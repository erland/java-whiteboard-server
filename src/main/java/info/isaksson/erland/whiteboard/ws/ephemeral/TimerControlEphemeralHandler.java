package info.isaksson.erland.whiteboard.ws.ephemeral;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;

import info.isaksson.erland.whiteboard.ws.WsMessage;
import info.isaksson.erland.whiteboard.ws.WsOutboundSupport;

@ApplicationScoped
public class TimerControlEphemeralHandler implements EphemeralEventHandler {

    private final TimerControlPayloadValidator timerControlPayloadValidator;
    private final TimerEphemeralStateRegistry timerStateRegistry;
    private final WsOutboundSupport outboundSupport;

    @Inject
    public TimerControlEphemeralHandler(TimerControlPayloadValidator timerControlPayloadValidator,
                                        TimerEphemeralStateRegistry timerStateRegistry,
                                        WsOutboundSupport outboundSupport) {
        this.timerControlPayloadValidator = timerControlPayloadValidator;
        this.timerStateRegistry = timerStateRegistry;
        this.outboundSupport = outboundSupport;
    }

    @Override
    public boolean supports(EphemeralEventType eventType) {
        return eventType == EphemeralEventType.TIMER_CONTROL;
    }

    @Override
    public void handle(EphemeralRequestContext context, EphemeralEventType eventType, JsonNode payload) {
        String validationError = timerControlPayloadValidator.validate(payload);
        if (validationError != null) {
            outboundSupport.send(context.session(), new WsMessage.Error("VALIDATION_ERROR", validationError));
            return;
        }
        JsonNode statePayload;
        try {
            statePayload = timerStateRegistry.applyControl(context.boardId(), context.connectionId(), context.fromUserId(), payload);
        } catch (IllegalArgumentException | IllegalStateException e) {
            outboundSupport.send(context.session(), new WsMessage.Error("VALIDATION_ERROR", e.getMessage()));
            return;
        }
        outboundSupport.broadcastEphemeral(context.boardId(), new WsMessage.Ephemeral(
                context.boardId(),
                context.connectionId(),
                context.fromUserId(),
                EphemeralEventType.TIMER_STATE.wireName(),
                statePayload,
                false));
    }
}
