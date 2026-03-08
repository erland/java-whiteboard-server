package info.isaksson.erland.whiteboard.ws.ephemeral;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import com.fasterxml.jackson.databind.JsonNode;

import info.isaksson.erland.whiteboard.config.FeatureToggles;
import info.isaksson.erland.whiteboard.ws.WsMessage;
import info.isaksson.erland.whiteboard.ws.WsMetrics;
import info.isaksson.erland.whiteboard.ws.WsOutboundSupport;

@ApplicationScoped
public class EphemeralInboundMessageHandler {

    private final EphemeralAccessPolicy accessPolicy;
    private final List<EphemeralEventHandler> handlers;
    private final WsOutboundSupport outboundSupport;
    private final WsMetrics metrics;
    private final FeatureToggles featureToggles;

    @Inject
    public EphemeralInboundMessageHandler(EphemeralAccessPolicy accessPolicy,
                                          Instance<EphemeralEventHandler> handlers,
                                          WsOutboundSupport outboundSupport,
                                          WsMetrics metrics,
                                          FeatureToggles featureToggles) {
        this.accessPolicy = accessPolicy;
        this.handlers = handlers.stream().toList();
        this.outboundSupport = outboundSupport;
        this.metrics = metrics;
        this.featureToggles = featureToggles;
    }

    public EphemeralInboundMessageHandler(EphemeralAccessPolicy accessPolicy,
                                          EphemeralStateRegistry stateRegistry,
                                          TimerEphemeralStateRegistry timerStateRegistry,
                                          ReactionPayloadValidator reactionPayloadValidator,
                                          TimerControlPayloadValidator timerControlPayloadValidator,
                                          WsOutboundSupport outboundSupport,
                                          WsMetrics metrics) {
        this.accessPolicy = accessPolicy;
        this.handlers = List.of(
                new ReactionEphemeralHandler(reactionPayloadValidator, outboundSupport, metrics),
                new TimerControlEphemeralHandler(timerControlPayloadValidator, timerStateRegistry, outboundSupport),
                new SessionSignalEphemeralHandler(stateRegistry, outboundSupport));
        this.outboundSupport = outboundSupport;
        this.metrics = metrics;
        this.featureToggles = enabledFeatureToggles();
    }

    private static FeatureToggles enabledFeatureToggles() {
        FeatureToggles toggles = new FeatureToggles();
        toggles.wsReactionsEnabled = true;
        toggles.timerEnabled = true;
        return toggles;
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

        if (!featureEnabled(eventType, session)) {
            return;
        }
        if (eventType == EphemeralEventType.TIMER_STATE) {
            outboundSupport.send(session, new WsMessage.Error("FORBIDDEN", "Timer state is server-managed and cannot be published by clients."));
            return;
        }
        if (!accessPolicy.canEmit(permission, eventType)) {
            outboundSupport.send(session, new WsMessage.Error("FORBIDDEN", "You do not have permission to publish this ephemeral event."));
            return;
        }

        EphemeralEventHandler handler = handlerFor(eventType);
        if (handler == null) {
            outboundSupport.send(session, new WsMessage.Error("VALIDATION_ERROR", "Unsupported ephemeral event type."));
            return;
        }
        handler.handle(new EphemeralRequestContext(session, boardId, fromUserId, permission, connectionId), eventType, payload);
    }

    private boolean featureEnabled(EphemeralEventType eventType, Session session) {
        if (eventType == EphemeralEventType.REACTION && featureToggles != null && !featureToggles.wsReactionsEnabled()) {
            metrics.incRejected("reaction_disabled");
            outboundSupport.send(session, new WsMessage.Error("FEATURE_DISABLED", "Reaction events are disabled on this server."));
            return false;
        }
        if ((eventType == EphemeralEventType.TIMER_CONTROL || eventType == EphemeralEventType.TIMER_STATE)
                && featureToggles != null
                && !featureToggles.timerEnabled()) {
            metrics.incRejected("timer_disabled");
            outboundSupport.send(session, new WsMessage.Error("FEATURE_DISABLED", "Shared timer events are disabled on this server."));
            return false;
        }
        return true;
    }

    private EphemeralEventHandler handlerFor(EphemeralEventType eventType) {
        for (EphemeralEventHandler handler : handlers) {
            if (handler.supports(eventType)) {
                return handler;
            }
        }
        return null;
    }
}
