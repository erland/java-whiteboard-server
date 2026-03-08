package info.isaksson.erland.whiteboard.ws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralEventType;
import info.isaksson.erland.whiteboard.ws.ephemeral.TimerEphemeralStateRegistry;

@ApplicationScoped
class WsBootstrapPublisher {

    private final WsSessionRegistry sessionRegistry;
    private final PresenceHub presenceHub;
    private final TimerEphemeralStateRegistry timerStateRegistry;
    private final WsContractSupport contractSupport;
    private final WsMetrics metrics;
    private final WsOutboundSupport outboundSupport;

    @Inject
    WsBootstrapPublisher(WsSessionRegistry sessionRegistry,
                         PresenceHub presenceHub,
                         TimerEphemeralStateRegistry timerStateRegistry,
                         WsContractSupport contractSupport,
                         WsMetrics metrics,
                         WsOutboundSupport outboundSupport) {
        this.sessionRegistry = sessionRegistry;
        this.presenceHub = presenceHub;
        this.timerStateRegistry = timerStateRegistry;
        this.contractSupport = contractSupport;
        this.metrics = metrics;
        this.outboundSupport = outboundSupport;
    }

    void publish(Session session, WsConnectionContext accepted) {
        sessionRegistry.register(accepted.boardId(), accepted.connectionId(), session);
        presenceHub.join(accepted.boardId(), accepted.connectionId(), accepted.effectiveUserId());

        outboundSupport.sendJoined(session,
                accepted.boardId(),
                accepted.effectiveUserId(),
                accepted.wsSessionId(),
                accepted.correlationId(),
                contractSupport.protocolVersion(),
                contractSupport.capabilities());
        timerStateRegistry.current(accepted.boardId()).ifPresent(state ->
                outboundSupport.send(session, new WsMessage.Ephemeral(
                        accepted.boardId(),
                        state.path("connectionId").asText(accepted.connectionId()),
                        state.path("from").asText(accepted.effectiveUserId()),
                        EphemeralEventType.TIMER_STATE.wireName(),
                        state,
                        false)));
        outboundSupport.broadcastPresence(accepted.boardId());

        metrics.joinAccepted();
        metrics.connectionOpened(accepted.boardId());
    }
}
