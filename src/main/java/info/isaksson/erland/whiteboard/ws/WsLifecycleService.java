package info.isaksson.erland.whiteboard.ws;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

@ApplicationScoped
class WsLifecycleService {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(WsLifecycleService.class);

    private final BoardJoinAuthorizer authorizer;
    private final WsAuthResolver authResolver;
    private final WsSessionRegistry sessionRegistry;
    private final PresenceHub presenceHub;
    private final WsLimits limits;
    private final WsMetrics metrics;
    private final WsOutboundSupport outboundSupport;

    @Inject
    WsLifecycleService(BoardJoinAuthorizer authorizer,
                       WsAuthResolver authResolver,
                       WsSessionRegistry sessionRegistry,
                       PresenceHub presenceHub,
                       WsLimits limits,
                       WsMetrics metrics,
                       WsOutboundSupport outboundSupport) {
        this.authorizer = authorizer;
        this.authResolver = authResolver;
        this.sessionRegistry = sessionRegistry;
        this.presenceHub = presenceHub;
        this.limits = limits;
        this.metrics = metrics;
        this.outboundSupport = outboundSupport;
    }

    void open(Session session, String boardId) {
        WsConnectionContext context = initializeSession(session, boardId);

        LOG.debugf("WS opening boardId=%s connectionId=%s wsSessionId=%s requestUri=%s",
                boardId, context.connectionId(), context.wsSessionId(), session == null ? null : session.getRequestURI());

        BoardJoinAuthorizer.JoinDecision decision = authorizer.authorize(boardId, authResolver.resolveUserId(session), context.inviteToken());
        if (!decision.allowed()) {
            metrics.incRejected("not_allowed");
            LOG.debugf("WS rejected boardId=%s connectionId=%s jwtUserId=%s invite=%s",
                    boardId,
                    context.connectionId(),
                    authResolver.resolveUserId(session),
                    context.inviteToken() == null ? null : "<present>");
            outboundSupport.close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Not allowed");
            return;
        }

        if (sessionRegistry.connectionCount(boardId) >= limits.maxConnectionsPerBoard()) {
            metrics.incRejected("board_connection_limit");
            LOG.debugf("WS rejected (board connection limit) boardId=%s connectionId=%s current=%d limit=%d",
                    boardId, context.connectionId(), sessionRegistry.connectionCount(boardId), limits.maxConnectionsPerBoard());
            outboundSupport.close(session, CloseReason.CloseCodes.TRY_AGAIN_LATER, "Board connection limit reached");
            return;
        }

        WsConnectionContext accepted = acceptDecision(session, context, decision);
        sessionRegistry.register(boardId, accepted.connectionId(), session);
        presenceHub.join(boardId, accepted.connectionId(), accepted.effectiveUserId());

        outboundSupport.sendJoined(session,
                boardId,
                accepted.effectiveUserId(),
                accepted.wsSessionId(),
                accepted.correlationId());
        outboundSupport.broadcastPresence(boardId);

        metrics.joinAccepted();
        metrics.connectionOpened(boardId);
        LOG.debugf("WS open boardId=%s connectionId=%s wsSessionId=%s userId=%s permission=%s invite=%s",
                boardId,
                accepted.connectionId(),
                accepted.wsSessionId(),
                accepted.effectiveUserId(),
                accepted.permission(),
                accepted.inviteToken() == null ? null : "<present>");
    }

    void close(Session session, CloseReason reason) {
        String boardId = prop(session, WsSessionProps.BOARD_ID);
        String connectionId = prop(session, WsSessionProps.CONNECTION_ID);
        if (boardId == null || connectionId == null) {
            return;
        }

        presenceHub.leave(boardId, connectionId);
        sessionRegistry.unregister(boardId, connectionId);
        outboundSupport.broadcastPresence(boardId);
        metrics.connectionClosed(boardId);

        LOG.debugf("WS close boardId=%s connectionId=%s code=%s reason=%s",
                boardId,
                connectionId,
                reason == null ? null : reason.getCloseCode(),
                reason == null ? null : reason.getReasonPhrase());
    }

    void error(Session session, Throwable throwable) {
        metrics.error();
        if (throwable == null) {
            LOG.debug("WS error: unknown");
        } else {
            LOG.debugf(throwable, "WS error: %s", throwable.getClass().getSimpleName());
        }
        try {
            outboundSupport.close(session, CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Error");
        } catch (Exception ignored) {
        }
    }

    private WsConnectionContext initializeSession(Session session, String boardId) {
        String connectionId = prop(session, WsSessionProps.CONNECTION_ID);
        if (connectionId == null || connectionId.isBlank()) {
            connectionId = UUID.randomUUID().toString();
        }
        String wsSessionId = prop(session, WsSessionProps.WS_SESSION_ID);
        if (wsSessionId == null || wsSessionId.isBlank()) {
            wsSessionId = UUID.randomUUID().toString();
        }
        String correlationId = prop(session, WsHandshakeConfigurator.PROP_CORRELATION_ID);
        String inviteToken = authResolver.resolveInviteToken(session);

        session.getUserProperties().put(WsSessionProps.CONNECTION_ID, connectionId);
        session.getUserProperties().put(WsSessionProps.WS_SESSION_ID, wsSessionId);
        session.getUserProperties().put(WsSessionProps.BOARD_ID, boardId);

        return new WsConnectionContext(boardId, connectionId, wsSessionId, correlationId, null, null, inviteToken);
    }

    private WsConnectionContext acceptDecision(Session session,
                                               WsConnectionContext context,
                                               BoardJoinAuthorizer.JoinDecision decision) {
        session.getUserProperties().put(WsSessionProps.USER_ID, decision.effectiveUserId());
        session.getUserProperties().put(WsSessionProps.PERMISSION, decision.permission());
        session.getUserProperties().put(WsSessionProps.RATE_LIMITER,
                new TokenBucketRateLimiter(limits.burst(), limits.ratePerSecond()));
        return new WsConnectionContext(
                context.boardId(),
                context.connectionId(),
                context.wsSessionId(),
                context.correlationId(),
                decision.effectiveUserId(),
                decision.permission(),
                context.inviteToken());
    }

    @SuppressWarnings("unchecked")
    private <T> T prop(Session session, String key) {
        return session == null ? null : (T) session.getUserProperties().get(key);
    }
}
