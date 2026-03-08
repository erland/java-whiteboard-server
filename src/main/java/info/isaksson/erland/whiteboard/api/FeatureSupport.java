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
    public void requireVotingEnabled() { if (!featureToggles.votingEnabled()) throw new NotFoundException(); }
    public void requireTimerEnabled() { if (!featureToggles.timerEnabled()) throw new NotFoundException(); }
    public void requireWsReactionsEnabled() { if (!featureToggles.wsReactionsEnabled()) throw new NotFoundException(); }
    public void requireWsVotingEventsEnabled() { if (!featureToggles.wsVotingEventsEnabled()) throw new NotFoundException(); }
}
