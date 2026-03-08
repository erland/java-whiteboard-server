package info.isaksson.erland.whiteboard.ws.ephemeral;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import com.fasterxml.jackson.databind.JsonNode;

import info.isaksson.erland.whiteboard.ws.TokenBucketRateLimiter;
import info.isaksson.erland.whiteboard.ws.WsMessage;
import info.isaksson.erland.whiteboard.ws.WsMetrics;
import info.isaksson.erland.whiteboard.ws.WsOutboundSupport;
import info.isaksson.erland.whiteboard.ws.WsSessionProps;

@ApplicationScoped
public class EphemeralInboundMessageHandler {

    private final EphemeralAccessPolicy accessPolicy;
    private final EphemeralStateRegistry stateRegistry;
    private final TimerEphemeralStateRegistry timerStateRegistry;
    private final ReactionPayloadValidator reactionPayloadValidator;
    private final TimerControlPayloadValidator timerControlPayloadValidator;
    private final WsOutboundSupport outboundSupport;
    private final WsMetrics metrics;

    @Inject
    public EphemeralInboundMessageHandler(EphemeralAccessPolicy accessPolicy,
                                          EphemeralStateRegistry stateRegistry,
                                          TimerEphemeralStateRegistry timerStateRegistry,
                                          ReactionPayloadValidator reactionPayloadValidator,
                                          TimerControlPayloadValidator timerControlPayloadValidator,
                                          WsOutboundSupport outboundSupport,
                                          WsMetrics metrics) {
        this.accessPolicy = accessPolicy;
        this.stateRegistry = stateRegistry;
        this.timerStateRegistry = timerStateRegistry;
        this.reactionPayloadValidator = reactionPayloadValidator;
        this.timerControlPayloadValidator = timerControlPayloadValidator;
        this.outboundSupport = outboundSupport;
        this.metrics = metrics;
    }

    public void handle(JsonNode root,
                       Session session,
                       String boardId,
                       String fromUserId,
                       String permission,
                       String connectionId) {
        JsonNode eventTypeNode = root.get("eventType");
        if (eventTypeNode == null || eventTypeNode.isNull() || !eventTypeNode.isTextual()) {
            outboundSupport.send(session, new WsMessage.Error("VALIDATION_ERROR", "Field 'eventType' is required."));
            return;
        }

        EphemeralEventType eventType = EphemeralEventType.fromWireName(eventTypeNode.asText()).orElse(null);
        if (eventType == null) {
            outboundSupport.send(session, new WsMessage.Error("VALIDATION_ERROR", "Unsupported ephemeral event type."));
            return;
        }

        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull() || !payload.isObject()) {
            outboundSupport.send(session, new WsMessage.Error("VALIDATION_ERROR", "Field 'payload' must be an object."));
            return;
        }

        if (!accessPolicy.canEmit(permission, eventType)) {
            outboundSupport.send(session, new WsMessage.Error("FORBIDDEN", "You do not have permission to publish this ephemeral event."));
            return;
        }

        switch (eventType) {
            case REACTION -> handleReaction(session, boardId, fromUserId, connectionId, payload);
            case TIMER_CONTROL -> handleTimerControl(session, boardId, fromUserId, connectionId, payload);
            case TIMER_STATE -> outboundSupport.send(session, new WsMessage.Error("FORBIDDEN", "Timer state is server-managed and cannot be published by clients."));
            default -> handleSessionScopedSignal(boardId, fromUserId, connectionId, eventType, payload);
        }
    }

    private void handleReaction(Session session, String boardId, String fromUserId, String connectionId, JsonNode payload) {
        String validationError = reactionPayloadValidator.validate(payload);
        if (validationError != null) {
            outboundSupport.send(session, new WsMessage.Error("VALIDATION_ERROR", validationError));
            return;
        }
        Object rlObj = session.getUserProperties().get(WsSessionProps.REACTION_RATE_LIMITER);
        if (rlObj instanceof TokenBucketRateLimiter rl && !rl.tryConsume()) {
            metrics.incRejected("reaction_rate_limited");
            outboundSupport.send(session, new WsMessage.Error("RATE_LIMITED", "Too many reactions."));
            return;
        }
        outboundSupport.broadcastEphemeral(boardId, new WsMessage.Ephemeral(
                boardId,
                connectionId,
                fromUserId,
                EphemeralEventType.REACTION.wireName(),
                payload,
                false));
    }

    private void handleTimerControl(Session session, String boardId, String fromUserId, String connectionId, JsonNode payload) {
        String validationError = timerControlPayloadValidator.validate(payload);
        if (validationError != null) {
            outboundSupport.send(session, new WsMessage.Error("VALIDATION_ERROR", validationError));
            return;
        }
        JsonNode statePayload = timerStateRegistry.applyControl(boardId, connectionId, fromUserId, payload);
        if (statePayload == null) {
            outboundSupport.send(session, new WsMessage.Error("VALIDATION_ERROR", "Timer action is not valid in the current timer state."));
            return;
        }
        outboundSupport.broadcastEphemeral(boardId, new WsMessage.Ephemeral(
                boardId,
                connectionId,
                fromUserId,
                EphemeralEventType.TIMER_STATE.wireName(),
                statePayload,
                false));
    }

    private void handleSessionScopedSignal(String boardId, String fromUserId, String connectionId, EphemeralEventType eventType, JsonNode payload) {
        EphemeralSignal signal = new EphemeralSignal(boardId, connectionId, fromUserId, eventType, payload, false);
        stateRegistry.update(signal);
        outboundSupport.broadcastEphemeral(boardId, new WsMessage.Ephemeral(
                boardId,
                connectionId,
                fromUserId,
                eventType.wireName(),
                payload,
                false));
    }
}
