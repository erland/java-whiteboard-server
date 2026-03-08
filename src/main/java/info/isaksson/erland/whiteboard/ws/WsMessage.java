package info.isaksson.erland.whiteboard.ws;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface WsMessage permits WsMessage.Joined, WsMessage.Presence, WsMessage.Op, WsMessage.Ephemeral, WsMessage.Error {

    record Joined(String type, String boardId, String yourUserId, Long latestSnapshotVersion, JsonNode latestSnapshot, JsonNode users, String wsSessionId, String correlationId, Integer protocolVersion, List<String> capabilities) implements WsMessage {
        public Joined(String boardId, String yourUserId, Long latestSnapshotVersion, JsonNode latestSnapshot, JsonNode users, String wsSessionId, String correlationId, Integer protocolVersion, List<String> capabilities) {
            this("joined", boardId, yourUserId, latestSnapshotVersion, latestSnapshot, users, wsSessionId, correlationId, protocolVersion, capabilities == null ? List.of() : List.copyOf(capabilities));
        }
    }

    record Presence(String type, String boardId, JsonNode users) implements WsMessage {
        public Presence(String boardId, JsonNode users) { this("presence", boardId, users); }
    }

    record Op(String type, String boardId, long seq, String from, JsonNode op) implements WsMessage {
        public Op(String boardId, long seq, String from, JsonNode op) { this("op", boardId, seq, from, op); }
    }

    record Ephemeral(String type, String boardId, String connectionId, String from, String eventType, JsonNode payload, boolean cleared) implements WsMessage {
        public Ephemeral(String boardId, String connectionId, String from, String eventType, JsonNode payload, boolean cleared) { this("ephemeral", boardId, connectionId, from, eventType, payload, cleared); }
    }

    record Error(String type, String code, String message, Integer protocolVersion, List<String> capabilities) implements WsMessage {
        public Error(String code, String message) { this("error", code, message, null, List.of()); }
        public Error(String code, String message, Integer protocolVersion, List<String> capabilities) { this("error", code, message, protocolVersion, capabilities == null ? List.of() : List.copyOf(capabilities)); }
    }
}
