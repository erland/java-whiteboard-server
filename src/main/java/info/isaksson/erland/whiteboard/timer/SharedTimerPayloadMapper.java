package info.isaksson.erland.whiteboard.timer;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SharedTimerPayloadMapper {

    private final ObjectMapper mapper;
    private final SharedTimerStateCalculator stateCalculator;

    @Inject
    public SharedTimerPayloadMapper(ObjectMapper mapper, SharedTimerStateCalculator stateCalculator) {
        this.mapper = mapper;
        this.stateCalculator = stateCalculator;
    }

    public JsonNode toPayload(SharedTimer timer) {
        SharedTimer effective = stateCalculator.refreshRunningStateIfNeeded(timer);
        ObjectNode payload = mapper.createObjectNode();
        payload.put("timerId", effective.id());
        payload.put("state", effective.state().storageValue());
        payload.put("durationMs", effective.durationMs());
        payload.put("remainingMs", stateCalculator.remainingMs(effective, Instant.now()));
        if (effective.startedAt() == null) {
            payload.putNull("startedAt");
        } else {
            payload.put("startedAt", effective.startedAt().toString());
        }
        if (effective.endsAt() == null) {
            payload.putNull("endsAt");
        } else {
            payload.put("endsAt", effective.endsAt().toString());
        }
        payload.put("updatedAt", effective.updatedAt().toString());
        payload.put("createdAt", effective.createdAt().toString());
        payload.put("controllerUserId", effective.controllerUserId());
        if (effective.label() == null) {
            payload.putNull("label");
        } else {
            payload.put("label", effective.label());
        }
        ObjectNode scope = payload.putObject("scope");
        scope.put("type", effective.scopeType().storageValue());
        if (effective.scopeRef() == null) {
            scope.putNull("ref");
        } else {
            scope.put("ref", effective.scopeRef());
        }
        return payload;
    }
}
