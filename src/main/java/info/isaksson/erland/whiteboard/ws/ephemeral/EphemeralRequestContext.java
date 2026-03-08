package info.isaksson.erland.whiteboard.ws.ephemeral;

import jakarta.websocket.Session;

public record EphemeralRequestContext(
        Session session,
        String boardId,
        String fromUserId,
        String permission,
        String connectionId) {
}
