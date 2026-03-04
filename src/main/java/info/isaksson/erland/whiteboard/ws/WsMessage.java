package info.isaksson.erland.whiteboard.ws;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface WsMessage permits WsMessage.Joined, WsMessage.Presence, WsMessage.Op, WsMessage.Error {

    /**
     * Sent to the joining client after successful authorization.
     *
     * Includes:
     * - presence users list
     * - latest snapshot pointer (and optionally the snapshot itself)
     */
    record Joined(
            String type,
            String boardId,
            String yourUserId,
            Long latestSnapshotVersion,
            JsonNode latestSnapshot,
            JsonNode users
    ) implements WsMessage {
        public Joined(String boardId, String yourUserId, Long latestSnapshotVersion, JsonNode latestSnapshot, JsonNode users) {
            this("joined", boardId, yourUserId, latestSnapshotVersion, latestSnapshot, users);
        }
    }

    record Presence(String type, String boardId, JsonNode users) implements WsMessage {
        public Presence(String boardId, JsonNode users) {
            this("presence", boardId, users);
        }
    }

    record Op(String type, String boardId, String from, JsonNode op) implements WsMessage {
        public Op(String boardId, String from, JsonNode op) {
            this("op", boardId, from, op);
        }
    }

    record Error(String type, String code, String message) implements WsMessage {
        public Error(String code, String message) {
            this("error", code, message);
        }
    }
}
