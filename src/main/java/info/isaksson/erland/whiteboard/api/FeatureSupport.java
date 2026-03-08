package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.config.FeatureToggles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class FeatureSupport {

    @Inject FeatureToggles featureToggles;

    public void requirePublicationsEnabled() { if (!featureToggles.publicationsEnabled()) throw new NotFoundException(); }
    public void requireCommentsEnabled() { if (!featureToggles.commentsEnabled()) throw new NotFoundException(); }
    public void requireAssetsEnabled() { if (!featureToggles.assetsEnabled()) throw new NotFoundException(); }
}
