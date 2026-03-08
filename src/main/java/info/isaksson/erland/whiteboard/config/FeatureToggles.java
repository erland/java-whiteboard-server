package info.isaksson.erland.whiteboard.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class FeatureToggles {

    @ConfigProperty(name = "whiteboard.features.publications.enabled", defaultValue = "true")
    public boolean publicationsEnabled;

    @ConfigProperty(name = "whiteboard.features.comments.enabled", defaultValue = "true")
    public boolean commentsEnabled;

    @ConfigProperty(name = "whiteboard.features.assets.enabled", defaultValue = "true")
    public boolean assetsEnabled;

    @ConfigProperty(name = "whiteboard.features.ws.ephemeral.enabled", defaultValue = "true")
    public boolean wsEphemeralEnabled;

    @ConfigProperty(name = "whiteboard.features.voting.enabled", defaultValue = "true")
    public boolean votingEnabled;

    @ConfigProperty(name = "whiteboard.features.ws.reactions.enabled", defaultValue = "true")
    public boolean wsReactionsEnabled;

    @ConfigProperty(name = "whiteboard.features.timer.enabled", defaultValue = "true")
    public boolean timerEnabled;

    @ConfigProperty(name = "whiteboard.features.ws.voting-events.enabled", defaultValue = "true")
    public boolean wsVotingEventsEnabled;

    public boolean publicationsEnabled() { return publicationsEnabled; }
    public boolean commentsEnabled() { return commentsEnabled; }
    public boolean assetsEnabled() { return assetsEnabled; }
    public boolean wsEphemeralEnabled() { return wsEphemeralEnabled; }
    public boolean votingEnabled() { return votingEnabled; }
    public boolean wsReactionsEnabled() { return wsReactionsEnabled; }
    public boolean timerEnabled() { return timerEnabled; }
    public boolean wsVotingEventsEnabled() { return wsVotingEventsEnabled; }

    public List<String> enabledCapabilities() {
        List<String> out = new ArrayList<>();
        if (publicationsEnabled) out.add("publications");
        if (commentsEnabled) out.add("comments");
        if (assetsEnabled) out.add("assets");
        if (wsEphemeralEnabled) out.add("ws-ephemeral");
        if (votingEnabled) out.add("voting");
        if (wsReactionsEnabled) out.add("ws-reactions");
        if (timerEnabled) out.add("shared-timer");
        if (wsVotingEventsEnabled) out.add("ws-voting-events");
        return List.copyOf(out);
    }
}
