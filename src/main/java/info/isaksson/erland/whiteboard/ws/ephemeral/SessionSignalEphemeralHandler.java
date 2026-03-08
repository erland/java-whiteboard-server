package info.isaksson.erland.whiteboard.ws.ephemeral;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;

import info.isaksson.erland.whiteboard.ws.WsMessage;
import info.isaksson.erland.whiteboard.ws.WsOutboundSupport;

@ApplicationScoped
public class SessionSignalEphemeralHandler implements EphemeralEventHandler {

    private final EphemeralStateRegistry stateRegistry;
    private final WsOutboundSupport outboundSupport;

    @Inject
    public SessionSignalEphemeralHandler(EphemeralStateRegistry stateRegistry,
                                         WsOutboundSupport outboundSupport) {
        this.stateRegistry = stateRegistry;
        this.outboundSupport = outboundSupport;
    }

    @Override
    public boolean supports(EphemeralEventType eventType) {
        return eventType != EphemeralEventType.REACTION
                && eventType != EphemeralEventType.TIMER_CONTROL
                && eventType != EphemeralEventType.TIMER_STATE;
    }

    @Override
    public void handle(EphemeralRequestContext context, EphemeralEventType eventType, JsonNode payload) {
        EphemeralSignal signal = new EphemeralSignal(context.boardId(), context.connectionId(), context.fromUserId(), eventType, payload, false);
        stateRegistry.update(signal);
        outboundSupport.broadcastEphemeral(context.boardId(), new WsMessage.Ephemeral(
                context.boardId(),
                context.connectionId(),
                context.fromUserId(),
                eventType.wireName(),
                payload,
                false));
    }
}
