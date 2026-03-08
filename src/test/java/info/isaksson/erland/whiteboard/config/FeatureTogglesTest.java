package info.isaksson.erland.whiteboard.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FeatureTogglesTest {

    @Test
    void enabledCapabilities_listsEnabledFeaturesOnly() {
        FeatureToggles toggles = new FeatureToggles();
        toggles.publicationsEnabled = true;
        toggles.commentsEnabled = false;
        toggles.assetsEnabled = true;
        toggles.wsEphemeralEnabled = false;
        toggles.votingEnabled = true;
        assertEquals(java.util.List.of("publications", "assets", "voting"), toggles.enabledCapabilities());
    }
}
