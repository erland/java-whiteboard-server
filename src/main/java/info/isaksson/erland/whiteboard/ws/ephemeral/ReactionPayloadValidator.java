package info.isaksson.erland.whiteboard.ws.ephemeral;

import java.util.Set;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.JsonNode;

@ApplicationScoped
public class ReactionPayloadValidator {

    private static final Pattern REACTION_TYPE_PATTERN = Pattern.compile("^[a-zA-Z0-9._:-]{1,32}$");
    private static final Set<String> ALLOWED_SCOPE_KEYS = Set.of("kind", "pageId", "sectionId", "objectId", "objectIds", "targetId");

    public String validate(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return "Field 'payload' must be an object.";
        }
        JsonNode reactionType = payload.get("reactionType");
        if (reactionType == null || !reactionType.isTextual() || reactionType.asText().isBlank()) {
            return "Reaction payload requires non-empty field 'reactionType'.";
        }
        if (!REACTION_TYPE_PATTERN.matcher(reactionType.asText()).matches()) {
            return "Field 'reactionType' contains unsupported characters or length.";
        }
        JsonNode durationMs = payload.get("durationMs");
        if (durationMs != null && !durationMs.isNull()) {
            if (!durationMs.canConvertToLong()) {
                return "Field 'durationMs' must be an integer when provided.";
            }
            long value = durationMs.asLong();
            if (value < 100 || value > 10000) {
                return "Field 'durationMs' must be between 100 and 10000.";
            }
        }
        JsonNode scope = payload.get("scope");
        if (scope != null && !scope.isNull()) {
            if (!scope.isObject()) {
                return "Field 'scope' must be an object when provided.";
            }
            var fields = scope.fieldNames();
            while (fields.hasNext()) {
                String name = fields.next();
                if (!ALLOWED_SCOPE_KEYS.contains(name)) {
                    return "Field 'scope' contains unsupported key '" + name + "'.";
                }
            }
        }
        return null;
    }
}
