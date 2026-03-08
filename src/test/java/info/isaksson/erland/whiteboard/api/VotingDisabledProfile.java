package info.isaksson.erland.whiteboard.api;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class VotingDisabledProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "whiteboard.features.voting.enabled", "false",
                "whiteboard.features.ws.voting-events.enabled", "false",
                "whiteboard.features.ws.reactions.enabled", "false",
                "whiteboard.features.timer.enabled", "false"
        );
    }
}
