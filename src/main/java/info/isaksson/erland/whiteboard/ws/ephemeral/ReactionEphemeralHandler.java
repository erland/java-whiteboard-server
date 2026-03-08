package info.isaksson.erland.whiteboard.ws.ephemeral;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;

import info.isaksson.erland.whiteboard.ws.TokenBucketRateLimiter;
import info.isaksson.erland.whiteboard.ws.WsMessage;
import info.isaksson.erland.whiteboard.ws.WsMetrics;
import info.isaksson.erland.whiteboard.ws.WsOutboundSupport;
import info.isaksson.erland.whiteboard.ws.WsSessionProps;

@ApplicationScoped
public class ReactionEphemeralHandler implements EphemeralEventHandler {

    private final ReactionPayloadValidator reactionPayloadValidator;
    private final WsOutboundSupport outboundSupport;
    private final WsMetrics metrics;

    @Inject
    public ReactionEphemeralHandler(ReactionPayloadValidator reactionPayloadValidator,
                                    WsOutboundSupport outboundSupport,
                                    WsMetrics metrics) {
        this.reactionPayloadValidator = reactionPayloadValidator;
        this.outboundSupport = outboundSupport;
        this.metrics = metrics;
    }

    @Override
    public boolean supports(EphemeralEventType eventType) {
        return eventType == EphemeralEventType.REACTION;
    }

    @Override
    public void handle(EphemeralRequestContext context, EphemeralEventType eventType, JsonNode payload) {
        String validationError = reactionPayloadValidator.validate(payload);
        if (validationError != null) {
            outboundSupport.send(context.session(), new WsMessage.Error("VALIDATION_ERROR", validationError));
            return;
        }
        Object rlObj = context.session().getUserProperties().get(WsSessionProps.REACTION_RATE_LIMITER);
        if (rlObj instanceof TokenBucketRateLimiter rl && !rl.tryConsume()) {
            metrics.incRejected("reaction_rate_limited");
            outboundSupport.send(context.session(), new WsMessage.Error("RATE_LIMITED", "Too many reactions."));
            return;
        }
        outboundSupport.broadcastEphemeral(context.boardId(), new WsMessage.Ephemeral(
                context.boardId(),
                context.connectionId(),
                context.fromUserId(),
                EphemeralEventType.REACTION.wireName(),
                payload,
                false));
    }
}
