package info.isaksson.erland.whiteboard.ws.ephemeral;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.JsonNode;

@ApplicationScoped
public class TimerControlPayloadValidator {

    private static final Set<String> ALLOWED_ACTIONS = Set.of("start", "pause", "resume", "reset", "cancel", "complete");

    public String validate(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return "Field 'payload' must be an object.";
        }
        JsonNode action = payload.get("action");
        if (action == null || !action.isTextual() || action.asText().isBlank()) {
            return "Timer payload requires non-empty field 'action'.";
        }
        String normalizedAction = action.asText().trim().toLowerCase();
        if (!ALLOWED_ACTIONS.contains(normalizedAction)) {
            return "Unsupported timer action '" + action.asText() + "'.";
        }
        JsonNode timerId = payload.get("timerId");
        if (timerId != null && !timerId.isNull() && (!timerId.isTextual() || timerId.asText().isBlank() || timerId.asText().length() > 64)) {
            return "Field 'timerId' must be a non-empty string up to 64 characters when provided.";
        }
        JsonNode scope = payload.get("scope");
        if (scope != null && !scope.isNull() && !scope.isObject()) {
            return "Field 'scope' must be an object when provided.";
        }
        JsonNode label = payload.get("label");
        if (label != null && !label.isNull() && (!label.isTextual() || label.asText().length() > 80)) {
            return "Field 'label' must be a string up to 80 characters when provided.";
        }
        if ("start".equals(normalizedAction)) {
            return validateDuration(payload.get("durationMs"), true);
        }
        if ("reset".equals(normalizedAction)) {
            return validateDuration(payload.get("durationMs"), false);
        }
        return null;
    }

    private String validateDuration(JsonNode durationMs, boolean required) {
        if (durationMs == null || durationMs.isNull()) {
            return required ? "Field 'durationMs' is required for timer start." : null;
        }
        if (!durationMs.canConvertToLong()) {
            return "Field 'durationMs' must be an integer when provided.";
        }
        long value = durationMs.asLong();
        if (value < 1000 || value > 86_400_000L) {
            return "Field 'durationMs' must be between 1000 and 86400000.";
        }
        return null;
    }
}
