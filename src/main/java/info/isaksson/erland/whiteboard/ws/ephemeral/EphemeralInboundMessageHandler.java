package info.isaksson.erland.whiteboard.ws.ephemeral;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import com.fasterxml.jackson.databind.JsonNode;

import info.isaksson.erland.whiteboard.ws.WsMessage;
import info.isaksson.erland.whiteboard.ws.WsOutboundSupport;

@ApplicationScoped
public class EphemeralInboundMessageHandler {

    private final EphemeralAccessPolicy accessPolicy;
    private final EphemeralStateRegistry stateRegistry;
    private final WsOutboundSupport outboundSupport;

    @Inject
    public EphemeralInboundMessageHandler(EphemeralAccessPolicy accessPolicy,
                                          EphemeralStateRegistry stateRegistry,
                                          WsOutboundSupport outboundSupport) {
        this.accessPolicy = accessPolicy;
        this.stateRegistry = stateRegistry;
        this.outboundSupport = outboundSupport;
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
