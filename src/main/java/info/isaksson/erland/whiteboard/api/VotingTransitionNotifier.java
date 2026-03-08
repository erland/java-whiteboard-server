package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.voting.VotingService;
import info.isaksson.erland.whiteboard.voting.VotingSession;
import info.isaksson.erland.whiteboard.voting.VotingWsNotifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VotingTransitionNotifier {

    private final VotingService votingService;
    private final VotingWsNotifier votingWsNotifier;

    @Inject
    public VotingTransitionNotifier(VotingService votingService, VotingWsNotifier votingWsNotifier) {
        this.votingService = votingService;
        this.votingWsNotifier = votingWsNotifier;
    }

    public void notifyTransition(String action, VotingSession updated) {
        if ("open".equals(action)) {
            votingWsNotifier.sessionOpened(updated, votingService.getPublicResults(updated.id()));
        } else if ("close".equals(action)) {
            votingWsNotifier.sessionClosed(updated, votingService.getPublicResults(updated.id()));
        } else if ("reveal".equals(action)) {
            votingWsNotifier.resultsRevealed(updated, votingService.getPublicResults(updated.id()));
        }
    }

    public void notifyVotesUpdated(VotingSession session, String actorUserId) {
        votingWsNotifier.votesUpdated(session, votingService.getPublicResults(session.id()), actorUserId);
    }
}
