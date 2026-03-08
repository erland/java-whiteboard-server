package info.isaksson.erland.whiteboard.ws;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralSignal;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralStateRegistry;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralEventType;
import info.isaksson.erland.whiteboard.ws.ephemeral.TimerEphemeralStateRegistry;

@ApplicationScoped
class WsLifecycleService {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(WsLifecycleService.class);

    private final BoardJoinAuthorizer authorizer;
    private final WsAuthResolver authResolver;
    private final WsSessionRegistry sessionRegistry;
    private final PresenceHub presenceHub;
    private final EphemeralStateRegistry ephemeralStateRegistry;
    private final TimerEphemeralStateRegistry timerStateRegistry;
    private final WsContractSupport contractSupport;
    private final WsLimits limits;
    private final WsMetrics metrics;
    private final WsOutboundSupport outboundSupport;

    @Inject
    WsLifecycleService(BoardJoinAuthorizer authorizer,
                       WsAuthResolver authResolver,
                       WsSessionRegistry sessionRegistry,
                       PresenceHub presenceHub,
                       EphemeralStateRegistry ephemeralStateRegistry,
                       TimerEphemeralStateRegistry timerStateRegistry,
                       WsContractSupport contractSupport,
                       WsLimits limits,
                       WsMetrics metrics,
                       WsOutboundSupport outboundSupport) {
        this.authorizer = authorizer;
        this.authResolver = authResolver;
        this.sessionRegistry = sessionRegistry;
        this.presenceHub = presenceHub;
        this.ephemeralStateRegistry = ephemeralStateRegistry;
        this.timerStateRegistry = timerStateRegistry;
        this.contractSupport = contractSupport;
        this.limits = limits;
        this.metrics = metrics;
        this.outboundSupport = outboundSupport;
    }

    void open(Session session, String boardId) {
        WsConnectionContext context = initializeSession(session, boardId);

        LOG.debugf("WS opening boardId=%s connectionId=%s wsSessionId=%s requestUri=%s",
                boardId, context.connectionId(), context.wsSessionId(), session == null ? null : session.getRequestURI());

        var versionDecision = contractSupport.check(session);
        if (!versionDecision.allowed()) {
            metrics.incRejected("incompatible_protocol");
            outboundSupport.send(session, new WsMessage.Error(versionDecision.code(), versionDecision.message(), contractSupport.protocolVersion(), contractSupport.capabilities()));
            outboundSupport.close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Incompatible protocol");
            return;
        }

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
                accepted.correlationId(),
                contractSupport.protocolVersion(),
                contractSupport.capabilities());
        timerStateRegistry.current(boardId).ifPresent(state ->
                outboundSupport.send(session, new WsMessage.Ephemeral(
                        boardId,
                        state.path("connectionId").asText(accepted.connectionId()),
                        state.path("from").asText(accepted.effectiveUserId()),
                        EphemeralEventType.TIMER_STATE.wireName(),
                        state,
                        false)));
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

        for (EphemeralSignal cleared : ephemeralStateRegistry.clearConnection(boardId, connectionId)) {
            outboundSupport.broadcastEphemeral(boardId, new WsMessage.Ephemeral(
                    cleared.boardId(),
                    cleared.connectionId(),
                    cleared.fromUserId(),
                    cleared.eventType().wireName(),
                    cleared.payload(),
                    true));
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
        session.getUserProperties().put(WsSessionProps.EPHEMERAL_RATE_LIMITER,
                new TokenBucketRateLimiter(limits.ephemeralBurst(), limits.ephemeralRatePerSecond()));
        session.getUserProperties().put(WsSessionProps.REACTION_RATE_LIMITER,
                new TokenBucketRateLimiter(limits.reactionBurst(), limits.reactionRatePerSecond()));
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
