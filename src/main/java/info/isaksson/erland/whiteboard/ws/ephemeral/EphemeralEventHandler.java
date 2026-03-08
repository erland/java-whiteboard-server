package info.isaksson.erland.whiteboard.ws.ephemeral;

import com.fasterxml.jackson.databind.JsonNode;

public interface EphemeralEventHandler {
    boolean supports(EphemeralEventType eventType);

    void handle(EphemeralRequestContext context, EphemeralEventType eventType, JsonNode payload);
}
