package info.isaksson.erland.whiteboard.ws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralSignal;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralStateRegistry;
import info.isaksson.erland.whiteboard.ws.ephemeral.TimerEphemeralStateRegistry;

@ApplicationScoped
class WsLifecycleService {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(WsLifecycleService.class);

    private final WsOpenInitializer openInitializer;
    private final WsOpenAuthorizer openAuthorizer;
    private final WsOpenAcceptor openAcceptor;
    private final WsBootstrapPublisher bootstrapPublisher;
    private final WsSessionRegistry sessionRegistry;
    private final PresenceHub presenceHub;
    private final EphemeralStateRegistry ephemeralStateRegistry;
    private final WsMetrics metrics;
    private final WsOutboundSupport outboundSupport;

    @Inject
    WsLifecycleService(WsOpenInitializer openInitializer,
                       WsOpenAuthorizer openAuthorizer,
                       WsOpenAcceptor openAcceptor,
                       WsBootstrapPublisher bootstrapPublisher,
                       WsSessionRegistry sessionRegistry,
                       PresenceHub presenceHub,
                       EphemeralStateRegistry ephemeralStateRegistry,
                       TimerEphemeralStateRegistry timerStateRegistry,
                       WsMetrics metrics,
                       WsOutboundSupport outboundSupport) {
        this.openInitializer = openInitializer;
        this.openAuthorizer = openAuthorizer;
        this.openAcceptor = openAcceptor;
        this.bootstrapPublisher = bootstrapPublisher;
        this.sessionRegistry = sessionRegistry;
        this.presenceHub = presenceHub;
        this.ephemeralStateRegistry = ephemeralStateRegistry;
        this.metrics = metrics;
        this.outboundSupport = outboundSupport;
    }

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
        this(new WsOpenInitializer(authResolver),
                new WsOpenAuthorizer(authorizer, authResolver, sessionRegistry, contractSupport, limits, metrics, outboundSupport),
                new WsOpenAcceptor(limits),
                new WsBootstrapPublisher(sessionRegistry, presenceHub, timerStateRegistry, contractSupport, metrics, outboundSupport),
                sessionRegistry,
                presenceHub,
                ephemeralStateRegistry,
                timerStateRegistry,
                metrics,
                outboundSupport);
    }

    void open(Session session, String boardId) {
        WsConnectionContext context = openInitializer.initialize(session, boardId);

        LOG.debugf("WS opening boardId=%s connectionId=%s wsSessionId=%s requestUri=%s",
                boardId, context.connectionId(), context.wsSessionId(), session == null ? null : session.getRequestURI());

        BoardJoinAuthorizer.JoinDecision decision = openAuthorizer.authorizeOrReject(session, context);
        if (decision == null) {
            return;
        }

        WsConnectionContext accepted = openAcceptor.accept(session, context, decision);
        bootstrapPublisher.publish(session, accepted);

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


    @SuppressWarnings("unchecked")
    private <T> T prop(Session session, String key) {
        return session == null ? null : (T) session.getUserProperties().get(key);
    }
}
