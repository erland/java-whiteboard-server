package info.isaksson.erland.whiteboard.ws;

import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.MDC;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.smallrye.jwt.auth.principal.JWTParser;

import info.isaksson.erland.whiteboard.domain.BoardSnapshot;
import info.isaksson.erland.whiteboard.persistence.SnapshotsRepository;

@ServerEndpoint(value = "/ws/boards/{boardId}", configurator = WsHandshakeConfigurator.class)
@ApplicationScoped
public class BoardWebSocketEndpoint {

    
    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(BoardWebSocketEndpoint.class);

    private static final String PROP_CONNECTION_ID = "connectionId";
    private static final String PROP_BOARD_ID = "boardId";
    private static final String PROP_USER_ID = "userId";
    private static final String PROP_PERMISSION = "permission";
    private static final String PROP_RATE_LIMITER = "rateLimiter";
    private static final String PROP_WS_SESSION_ID = "wsSessionId";

@Inject
    ObjectMapper mapper;

    @Inject
    PresenceHub presenceHub;

    @Inject
    SnapshotsRepository snapshotsRepository;

    @Inject
    BoardOpSequencer opSequencer;

    
    @Inject
    WsLimits limits;

    @Inject
    WsMetrics metrics;
@Inject
    BoardJoinAuthorizer authorizer;

    @Inject
    Instance<JWTParser> jwtParser;
/**
     * Per-board connected sessions.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Session>> boardSessions = new java.util.concurrent.ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("boardId") String boardId) {
        String connectionId = UUID.randomUUID().toString();
        String wsSessionId = UUID.randomUUID().toString();
        session.getUserProperties().put(PROP_CONNECTION_ID, connectionId);
        session.getUserProperties().put(PROP_WS_SESSION_ID, wsSessionId);
        session.getUserProperties().put(PROP_BOARD_ID, boardId);

        withWsMdc(session, () -> {
            String inviteToken = firstQueryParam(session, "invite");

            String bearer = bearerTokenFromHandshake(session);
            if (bearer == null || bearer.isBlank()) {
                // Optional fallback for clients that cannot set headers easily
                bearer = firstQueryParam(session, "access_token");
            }

            String jwtUserId = null;
            if (bearer != null && !bearer.isBlank()) {
                jwtUserId = userIdFromJwt(bearer);
            }

            var decision = authorizer.authorize(boardId, jwtUserId, inviteToken);
            if (!decision.allowed()) {
                metrics.incRejected("not_allowed");
                close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Not allowed");
                return;
            }

            String effectiveUserId = decision.effectiveUserId();
            session.getUserProperties().put(PROP_USER_ID, effectiveUserId);
            session.getUserProperties().put(PROP_PERMISSION, decision.permission());

            // Hard limit: cap concurrent connections per board.
            var current = boardSessions.get(boardId);
            if (current != null && current.size() >= limits.maxConnectionsPerBoard()) {
                metrics.incRejected("board_connection_limit");
                close(session, CloseReason.CloseCodes.TRY_AGAIN_LATER, "Board connection limit reached");
                return;
            }

            session.getUserProperties().put(PROP_RATE_LIMITER,
                    new TokenBucketRateLimiter(limits.burst(), limits.ratePerSecond()));

            boardSessions.computeIfAbsent(boardId, k -> new java.util.concurrent.ConcurrentHashMap<>())
                    .put(connectionId, session);

            Map<String, PresenceHub.UserPresence> users = presenceHub.join(boardId, connectionId, effectiveUserId);

            // Resolve latest snapshot pointer (and include snapshot JSON) for join payload
            Long latestVersion = null;
            com.fasterxml.jackson.databind.JsonNode latestSnapshot = null;
            try {
                BoardSnapshot latest = snapshotsRepository.getLatest(boardId).orElse(null);
                if (latest != null) {
                    latestVersion = latest.version();
                    latestSnapshot = mapper.readTree(latest.snapshotJson());
                }
            } catch (Exception ignored) {
                // If snapshots are unavailable/corrupt, we still allow join and just omit snapshot data
            }

            String correlationId = (String) session.getUserProperties().get(WsHandshakeConfigurator.PROP_CORRELATION_ID);

            // Send joined to this session (include ws session + correlation ids for client-side tracing)
            send(session, new WsMessage.Joined(boardId, effectiveUserId, latestVersion, latestSnapshot,
                    usersToJson(users), wsSessionId, correlationId));

            // Broadcast presence to all sessions on this board
            broadcastPresence(boardId);

            metrics.joinAccepted();
            metrics.connectionOpened(boardId);
            LOG.debugf("WS open boardId=%s connectionId=%s wsSessionId=%s userId=%s", boardId, connectionId, wsSessionId, effectiveUserId);
        });
}

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        withWsMdc(session, () -> {
            String boardId = (String) session.getUserProperties().get(PROP_BOARD_ID);
            String connectionId = (String) session.getUserProperties().get(PROP_CONNECTION_ID);

            if (boardId == null || connectionId == null) {
                return;
            }

            presenceHub.leave(boardId, connectionId);

            var sessions = boardSessions.get(boardId);
            if (sessions != null) {
                sessions.remove(connectionId);
                if (sessions.isEmpty()) {
                    boardSessions.remove(boardId, sessions);
                }
            }

            broadcastPresence(boardId);
            metrics.connectionClosed(boardId);
            LOG.debugf("WS close boardId=%s connectionId=%s", boardId, connectionId);
        });
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        withWsMdc(session, () -> {
            metrics.error();
            LOG.debugf("WS error: %s", throwable == null ? "unknown" : throwable.getClass().getSimpleName());
        });
        // Best effort: close. Errors are expected when clients disconnect abruptly.
        try {
            close(session, CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Error");
        } catch (Exception ignored) {
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        withWsMdc(session, () -> {
            // Hardening: message size limit
            if (message != null && message.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > limits.maxMessageBytes()) {
                metrics.incRejected("message_too_large");
                send(session, new WsMessage.Error("MESSAGE_TOO_LARGE", "Message exceeds max size."));
                close(session, CloseReason.CloseCodes.TOO_BIG, "Message too large");
                return;
            }

            // Hardening: rate limit per connection
            Object rlObj = session.getUserProperties().get(PROP_RATE_LIMITER);
            if (rlObj instanceof TokenBucketRateLimiter rl) {
                if (!rl.tryConsume()) {
                    metrics.incRejected("rate_limited");
                    send(session, new WsMessage.Error("RATE_LIMITED", "Too many messages."));
                    return;
                }
            }

            String boardId = (String) session.getUserProperties().get(PROP_BOARD_ID);
            String fromUserId = (String) session.getUserProperties().get(PROP_USER_ID);
            String permission = (String) session.getUserProperties().get(PROP_PERMISSION);
            String connectionId = (String) session.getUserProperties().get(PROP_CONNECTION_ID);
            if (boardId == null || fromUserId == null || connectionId == null || permission == null) {
                metrics.incRejected("not_allowed");
                close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Not allowed");
                return;
            }

            JsonNode root;
            try {
                root = mapper.readTree(message);
            } catch (Exception e) {
                metrics.jsonError();
                send(session, new WsMessage.Error("BAD_REQUEST", "Invalid JSON."));
                return;
            }

            String type = root.hasNonNull("type") ? root.get("type").asText() : "";
            if ("op".equals(type)) {
                JsonNode op = root.get("op");
                metrics.opReceived();
                if (op == null || op.isNull()) {
                    send(session, new WsMessage.Error("VALIDATION_ERROR", "Field 'op' is required."));
                    return;
                }

                if ("viewer".equalsIgnoreCase(permission)) {
                    metrics.incRejected("forbidden_op");
                    send(session, new WsMessage.Error("FORBIDDEN", "You do not have permission to publish operations."));
                    return;
                }

                long seq = opSequencer.next(boardId);
                broadcastOp(boardId, connectionId, new WsMessage.Op(boardId, seq, fromUserId, op));
                return;
            }

            // Ignore unknown message types for MVP
        });
    }

    private void broadcastOp(String boardId, String fromConnectionId, WsMessage.Op opMsg) {
        var sessions = boardSessions.get(boardId);
        if (sessions == null) return;

        for (var entry : sessions.entrySet()) {
            if (entry.getKey().equals(fromConnectionId)) continue; // don't echo
            send(entry.getValue(), opMsg);
            metrics.opBroadcast();
        }
    }

    private void broadcastPresence(String boardId) {
        var sessions = boardSessions.get(boardId);
        if (sessions == null) return;

        Map<String, PresenceHub.UserPresence> users = presenceHub.snapshot(boardId);
        WsMessage.Presence msg = new WsMessage.Presence(boardId, usersToJson(users));

        for (var s : sessions.values()) {
            send(s, msg);
            metrics.presenceBroadcast();
        }
    }

    private JsonNode usersToJson(Map<String, PresenceHub.UserPresence> users) {
        ArrayNode arr = mapper.createArrayNode();
        for (var up : users.values()) {
            ObjectNode o = mapper.createObjectNode();
            o.put("userId", up.userId());
            o.put("joinedAt", up.joinedAt().toString());
            arr.add(o);
        }
        return arr;
    }

    private void withWsMdc(Session session, Runnable fn) {
        String connectionId = session == null ? null : (String) session.getUserProperties().get(PROP_CONNECTION_ID);
        String wsSessionId = session == null ? null : (String) session.getUserProperties().get(PROP_WS_SESSION_ID);
        String correlationId = session == null ? null : (String) session.getUserProperties().get(WsHandshakeConfigurator.PROP_CORRELATION_ID);
        try {
            if (correlationId != null) {
                MDC.put("correlationId", correlationId);
            }
            if (wsSessionId != null) {
                MDC.put("wsSessionId", wsSessionId);
            }
            if (connectionId != null) {
                MDC.put("wsConnectionId", connectionId);
            }
            fn.run();
        } finally {
            MDC.remove("correlationId");
            MDC.remove("wsSessionId");
            MDC.remove("wsConnectionId");
        }
    }

    private void send(Session session, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            session.getAsyncRemote().sendText(json);
        } catch (Exception ignored) {
        }
    }

    private void close(Session session, CloseReason.CloseCode code, String reason) {
        try {
            session.close(new CloseReason(code, reason));
        } catch (Exception ignored) {
        }
    }


private String bearerTokenFromHandshake(Session session) {
    try {
        Object raw = session.getUserProperties().get(WsHandshakeConfigurator.AUTHORIZATION_HEADER);
        if (raw instanceof String s) {
            if (s.startsWith("Bearer ")) return s.substring("Bearer ".length()).trim();
        }
        return null;
    } catch (Exception e) {
        return null;
    }
}

private String userIdFromJwt(String token) {
    try {
        if (jwtParser == null || !jwtParser.isResolvable()) {
            return null;
        }
        JsonWebToken jwt = jwtParser.get().parse(token);
String preferred = jwt.getClaim("preferred_username");
        if (preferred != null && !preferred.isBlank()) return preferred;
        return jwt.getSubject();
    } catch (Exception e) {
        return null;
    }
}

    private String firstQueryParam(Session session, String key) {
        try {
            var map = session.getRequestParameterMap();
            if (map == null) return null;
            var values = map.get(key);
            if (values == null || values.isEmpty()) return null;
            return values.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}
