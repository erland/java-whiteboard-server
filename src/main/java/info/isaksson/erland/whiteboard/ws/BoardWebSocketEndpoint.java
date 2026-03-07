package info.isaksson.erland.whiteboard.ws;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.MDC;

import io.smallrye.jwt.auth.principal.JWTParser;

import info.isaksson.erland.whiteboard.persistence.SnapshotsRepository;

@ServerEndpoint(value = "/ws/boards/{boardId}", configurator = WsHandshakeConfigurator.class)
@ApplicationScoped
public class BoardWebSocketEndpoint {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(BoardWebSocketEndpoint.class);

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

    private final WsSessionRegistry sessionRegistry = new WsSessionRegistry();

    @OnOpen
    public void onOpen(Session session, @PathParam("boardId") String boardId) {
        String connectionId = UUID.randomUUID().toString();
        String wsSessionId = UUID.randomUUID().toString();
        session.getUserProperties().put(WsSessionProps.CONNECTION_ID, connectionId);
        session.getUserProperties().put(WsSessionProps.WS_SESSION_ID, wsSessionId);
        session.getUserProperties().put(WsSessionProps.BOARD_ID, boardId);

        withWsMdc(session, () -> {
            WsAuthResolver authResolver = new WsAuthResolver(jwtParser);
            WsOutboundSupport outboundSupport = outboundSupport();

            LOG.debugf("WS opening boardId=%s connectionId=%s wsSessionId=%s requestUri=%s",
                    boardId, connectionId, wsSessionId, session == null ? null : session.getRequestURI());

            String inviteToken = authResolver.resolveInviteToken(session);
            String jwtUserId = authResolver.resolveUserId(session);
            BoardJoinAuthorizer.JoinDecision decision = authorizer.authorize(boardId, jwtUserId, inviteToken);
            if (!decision.allowed()) {
                metrics.incRejected("not_allowed");
                LOG.debugf("WS rejected boardId=%s connectionId=%s jwtUserId=%s invite=%s",
                        boardId, connectionId, jwtUserId, inviteToken == null ? null : "<present>");
                outboundSupport.close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Not allowed");
                return;
            }

            if (sessionRegistry.connectionCount(boardId) >= limits.maxConnectionsPerBoard()) {
                metrics.incRejected("board_connection_limit");
                LOG.debugf("WS rejected (board connection limit) boardId=%s connectionId=%s current=%d limit=%d",
                        boardId, connectionId, sessionRegistry.connectionCount(boardId), limits.maxConnectionsPerBoard());
                outboundSupport.close(session, CloseReason.CloseCodes.TRY_AGAIN_LATER, "Board connection limit reached");
                return;
            }

            String effectiveUserId = decision.effectiveUserId();
            session.getUserProperties().put(WsSessionProps.USER_ID, effectiveUserId);
            session.getUserProperties().put(WsSessionProps.PERMISSION, decision.permission());
            session.getUserProperties().put(WsSessionProps.RATE_LIMITER,
                    new TokenBucketRateLimiter(limits.burst(), limits.ratePerSecond()));

            sessionRegistry.register(boardId, connectionId, session);
            presenceHub.join(boardId, connectionId, effectiveUserId);

            String correlationId = (String) session.getUserProperties().get(WsHandshakeConfigurator.PROP_CORRELATION_ID);
            outboundSupport.sendJoined(session, boardId, effectiveUserId, wsSessionId, correlationId);
            outboundSupport.broadcastPresence(boardId);

            metrics.joinAccepted();
            metrics.connectionOpened(boardId);
            LOG.debugf("WS open boardId=%s connectionId=%s wsSessionId=%s userId=%s permission=%s invite=%s",
                    boardId, connectionId, wsSessionId, effectiveUserId, decision.permission(),
                    inviteToken == null ? null : "<present>");
        });
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        withWsMdc(session, () -> {
            String boardId = (String) session.getUserProperties().get(WsSessionProps.BOARD_ID);
            String connectionId = (String) session.getUserProperties().get(WsSessionProps.CONNECTION_ID);
            if (boardId == null || connectionId == null) {
                return;
            }

            presenceHub.leave(boardId, connectionId);
            sessionRegistry.unregister(boardId, connectionId);
            outboundSupport().broadcastPresence(boardId);
            metrics.connectionClosed(boardId);

            LOG.debugf("WS close boardId=%s connectionId=%s code=%s reason=%s",
                    boardId,
                    connectionId,
                    reason == null ? null : reason.getCloseCode(),
                    reason == null ? null : reason.getReasonPhrase());
        });
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        withWsMdc(session, () -> {
            metrics.error();
            if (throwable == null) {
                LOG.debug("WS error: unknown");
            } else {
                LOG.debugf(throwable, "WS error: %s", throwable.getClass().getSimpleName());
            }
        });
        try {
            outboundSupport().close(session, CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Error");
        } catch (Exception ignored) {
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        withWsMdc(session, () -> inboundHandler().handle(message, session));
    }

    private WsOutboundSupport outboundSupport() {
        return new WsOutboundSupport(mapper, snapshotsRepository, presenceHub, sessionRegistry, metrics);
    }

    private WsInboundMessageHandler inboundHandler() {
        return new WsInboundMessageHandler(mapper, opSequencer, limits, metrics, outboundSupport());
    }

    private void withWsMdc(Session session, Runnable fn) {
        String connectionId = session == null ? null : (String) session.getUserProperties().get(WsSessionProps.CONNECTION_ID);
        String wsSessionId = session == null ? null : (String) session.getUserProperties().get(WsSessionProps.WS_SESSION_ID);
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
}
