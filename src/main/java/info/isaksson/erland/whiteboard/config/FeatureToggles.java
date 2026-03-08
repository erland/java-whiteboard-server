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

    public boolean publicationsEnabled() { return publicationsEnabled; }
    public boolean commentsEnabled() { return commentsEnabled; }
    public boolean assetsEnabled() { return assetsEnabled; }
    public boolean wsEphemeralEnabled() { return wsEphemeralEnabled; }

    public List<String> enabledCapabilities() {
        List<String> out = new ArrayList<>();
        if (publicationsEnabled) out.add("publications");
        if (commentsEnabled) out.add("comments");
        if (assetsEnabled) out.add("assets");
        if (wsEphemeralEnabled) out.add("ws-ephemeral");
        return List.copyOf(out);
    }
}
