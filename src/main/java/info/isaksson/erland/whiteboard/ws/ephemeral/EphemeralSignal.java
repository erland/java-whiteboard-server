package info.isaksson.erland.whiteboard.ws.ephemeral;

import com.fasterxml.jackson.databind.JsonNode;

public record EphemeralSignal(
        String boardId,
        String connectionId,
        String fromUserId,
        EphemeralEventType eventType,
        JsonNode payload,
        boolean cleared
) {
}
