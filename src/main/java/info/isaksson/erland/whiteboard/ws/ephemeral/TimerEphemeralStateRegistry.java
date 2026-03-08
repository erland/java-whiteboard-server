package info.isaksson.erland.whiteboard.ws.ephemeral;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ApplicationScoped
public class TimerEphemeralStateRegistry {

    private final ConcurrentHashMap<String, ObjectNode> states = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    @Inject
    public TimerEphemeralStateRegistry(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public synchronized JsonNode applyControl(String boardId, String connectionId, String fromUserId, JsonNode payload) {
        if (boardId == null || payload == null || !payload.isObject()) {
            return null;
        }
        String action = payload.path("action").asText("").trim().toLowerCase();
        if (action.isBlank()) {
            return null;
        }
        ObjectNode current = states.get(boardId);
        Instant now = Instant.now();
        return switch (action) {
            case "start" -> start(boardId, connectionId, fromUserId, payload, now);
            case "pause" -> pause(boardId, connectionId, fromUserId, current, now);
            case "resume" -> resume(boardId, connectionId, fromUserId, current, now);
            case "reset" -> reset(boardId, connectionId, fromUserId, current, payload, now);
            case "cancel" -> finish(boardId, connectionId, fromUserId, current, now, "cancelled");
            case "complete" -> finish(boardId, connectionId, fromUserId, current, now, "completed");
            default -> null;
        };
    }

    public Optional<JsonNode> current(String boardId) {
        ObjectNode state = boardId == null ? null : states.get(boardId);
        return state == null ? Optional.empty() : Optional.of(state.deepCopy());
    }

    public void clearBoard(String boardId) {
        if (boardId != null) {
            states.remove(boardId);
        }
    }

    private JsonNode start(String boardId, String connectionId, String fromUserId, JsonNode payload, Instant now) {
        long durationMs = payload.path("durationMs").asLong();
        ObjectNode state = mapper.createObjectNode();
        state.put("timerId", payload.path("timerId").asText(java.util.UUID.randomUUID().toString()));
        state.put("state", "running");
        state.put("durationMs", durationMs);
        state.put("remainingMs", durationMs);
        state.put("startedAt", now.toString());
        state.put("endsAt", now.plusMillis(durationMs).toString());
        state.put("updatedAt", now.toString());
        state.put("from", fromUserId);
        state.put("connectionId", connectionId);
        copyOptionalText(payload, state, "label");
        copyOptionalNode(payload, state, "scope");
        states.put(boardId, state);
        return state.deepCopy();
    }

    private JsonNode pause(String boardId, String connectionId, String fromUserId, ObjectNode current, Instant now) {
        if (current == null || !"running".equals(current.path("state").asText())) {
            return null;
        }
        long remainingMs = remainingMs(current, now);
        current.put("state", remainingMs <= 0 ? "completed" : "paused");
        current.put("remainingMs", Math.max(0, remainingMs));
        current.putNull("endsAt");
        current.put("updatedAt", now.toString());
        current.put("from", fromUserId);
        current.put("connectionId", connectionId);
        if (remainingMs <= 0) {
            states.remove(boardId);
        }
        return current.deepCopy();
    }

    private JsonNode resume(String boardId, String connectionId, String fromUserId, ObjectNode current, Instant now) {
        if (current == null || !"paused".equals(current.path("state").asText())) {
            return null;
        }
        long remainingMs = Math.max(0, current.path("remainingMs").asLong());
        current.put("state", remainingMs <= 0 ? "completed" : "running");
        current.put("startedAt", now.toString());
        current.put("endsAt", now.plusMillis(remainingMs).toString());
        current.put("updatedAt", now.toString());
        current.put("from", fromUserId);
        current.put("connectionId", connectionId);
        if (remainingMs <= 0) {
            states.remove(boardId);
        }
        return current.deepCopy();
    }

    private JsonNode reset(String boardId, String connectionId, String fromUserId, ObjectNode current, JsonNode payload, Instant now) {
        if (current == null) {
            return null;
        }
        long durationMs = payload.hasNonNull("durationMs") ? payload.path("durationMs").asLong() : current.path("durationMs").asLong();
        current.put("state", "paused");
        current.put("durationMs", durationMs);
        current.put("remainingMs", durationMs);
        current.putNull("startedAt");
        current.putNull("endsAt");
        current.put("updatedAt", now.toString());
        current.put("from", fromUserId);
        current.put("connectionId", connectionId);
        copyOptionalText(payload, current, "label");
        copyOptionalNode(payload, current, "scope");
        states.put(boardId, current);
        return current.deepCopy();
    }

    private JsonNode finish(String boardId, String connectionId, String fromUserId, ObjectNode current, Instant now, String finalState) {
        if (current == null) {
            return null;
        }
        current.put("state", finalState);
        current.put("remainingMs", 0);
        current.putNull("endsAt");
        current.put("updatedAt", now.toString());
        current.put("from", fromUserId);
        current.put("connectionId", connectionId);
        states.remove(boardId);
        return current.deepCopy();
    }

    private long remainingMs(ObjectNode current, Instant now) {
        String endsAt = current.path("endsAt").asText(null);
        if (endsAt == null || endsAt.isBlank()) {
            return Math.max(0, current.path("remainingMs").asLong());
        }
        return Math.max(0, Instant.parse(endsAt).toEpochMilli() - now.toEpochMilli());
    }

    private void copyOptionalText(JsonNode from, ObjectNode to, String field) {
        if (from.hasNonNull(field) && from.get(field).isTextual()) {
            to.put(field, from.get(field).asText());
        }
    }

    private void copyOptionalNode(JsonNode from, ObjectNode to, String field) {
        if (from.has(field)) {
            JsonNode value = from.get(field);
            if (value == null || value.isNull()) {
                to.putNull(field);
            } else {
                to.set(field, value.deepCopy());
            }
        }
    }
}
