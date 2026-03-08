package info.isaksson.erland.whiteboard.ws;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.isaksson.erland.whiteboard.domain.BoardSnapshot;
import info.isaksson.erland.whiteboard.persistence.SnapshotsRepository;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralAccessPolicy;

@ApplicationScoped
public class WsOutboundSupport {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(WsOutboundSupport.class);

    private final ObjectMapper mapper;
    private final SnapshotsRepository snapshotsRepository;
    private final PresenceHub presenceHub;
    private final WsSessionRegistry sessionRegistry;
    private final WsMetrics metrics;
    private final EphemeralAccessPolicy ephemeralAccessPolicy;

    WsOutboundSupport(ObjectMapper mapper,
                      SnapshotsRepository snapshotsRepository,
                      PresenceHub presenceHub,
                      WsSessionRegistry sessionRegistry,
                      WsMetrics metrics) {
        this(mapper, snapshotsRepository, presenceHub, sessionRegistry, metrics, new EphemeralAccessPolicy());
    }

    @Inject
    WsOutboundSupport(ObjectMapper mapper,
                      SnapshotsRepository snapshotsRepository,
                      PresenceHub presenceHub,
                      WsSessionRegistry sessionRegistry,
                      WsMetrics metrics,
                      EphemeralAccessPolicy ephemeralAccessPolicy) {
        this.mapper = mapper;
        this.snapshotsRepository = snapshotsRepository;
        this.presenceHub = presenceHub;
        this.sessionRegistry = sessionRegistry;
        this.metrics = metrics;
        this.ephemeralAccessPolicy = ephemeralAccessPolicy;
    }

    void sendJoined(Session session,
                    String boardId,
                    String effectiveUserId,
                    String wsSessionId,
                    String correlationId,
                    int protocolVersion,
                    java.util.List<String> capabilities) {
        Long latestVersion = null;
        JsonNode latestSnapshot = null;
        try {
            BoardSnapshot latest = snapshotsRepository.getLatest(boardId).orElse(null);
            if (latest != null) {
                latestVersion = latest.version();
                latestSnapshot = mapper.readTree(latest.snapshotJson());
            }
        } catch (Exception ignored) {
        }

        Map<String, PresenceHub.UserPresence> users = presenceHub.snapshot(boardId);
        send(session, new WsMessage.Joined(boardId, effectiveUserId, latestVersion, latestSnapshot, usersToJson(users), wsSessionId, correlationId, protocolVersion, capabilities));
    }

    void broadcastPresence(String boardId) {
        Map<String, Session> sessions = sessionRegistry.sessions(boardId);
        if (sessions.isEmpty()) {
            return;
        }
        WsMessage.Presence msg = new WsMessage.Presence(boardId, usersToJson(presenceHub.snapshot(boardId)));
        for (Session session : sessions.values()) {
            send(session, msg);
            metrics.presenceBroadcast();
        }
    }

    void broadcastOp(String boardId, WsMessage.Op opMsg) {
        Map<String, Session> sessions = sessionRegistry.sessions(boardId);
        if (sessions.isEmpty()) {
            return;
        }
        for (Session session : sessions.values()) {
            send(session, opMsg);
            metrics.opBroadcast();
        }
    }

    public void broadcastEphemeral(String boardId, WsMessage.Ephemeral message) {
        Map<String, Session> sessions = sessionRegistry.sessions(boardId);
        if (sessions.isEmpty()) {
            return;
        }
        for (Session session : sessions.values()) {
            String permission = (String) session.getUserProperties().get(WsSessionProps.PERMISSION);
            if (!ephemeralAccessPolicy.canObserve(permission, message.eventType())) {
                continue;
            }
            send(session, message);
            metrics.ephemeralBroadcast();
        }
    }

    public void send(Session session, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            session.getAsyncRemote().sendText(json, new SendHandler() {
                @Override
                public void onResult(SendResult result) {
                    if (result == null) {
                        LOG.debug("WS send completed with null result");
                        return;
                    }
                    if (!result.isOK()) {
                        Throwable ex = result.getException();
                        if (ex == null) {
                            LOG.debug("WS send failed (no exception)");
                        } else {
                            LOG.debugf(ex, "WS send failed: %s", ex.getClass().getSimpleName());
                        }
                    }
                }
            });
        } catch (Exception e) {
            LOG.debugf(e, "WS send failed to serialize or dispatch");
        }
    }

    void close(Session session, CloseReason.CloseCode code, String reason) {
        try {
            LOG.debugf("WS closing code=%s reason=%s", code, reason);
            session.close(new CloseReason(code, reason));
        } catch (Exception ignored) {
        }
    }

    private JsonNode usersToJson(Map<String, PresenceHub.UserPresence> users) {
        ArrayNode arr = mapper.createArrayNode();
        for (PresenceHub.UserPresence up : users.values()) {
            ObjectNode o = mapper.createObjectNode();
            o.put("userId", up.userId());
            o.put("joinedAt", up.joinedAt().toString());
            arr.add(o);
        }
        return arr;
    }
}
