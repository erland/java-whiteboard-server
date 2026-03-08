package info.isaksson.erland.whiteboard.voting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.VoteRecordsRepository;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VotingResultsService {

    private final VoteRecordsRepository voteRecordsRepository;
    private final VotingSessionService votingSessionService;

    @Inject
    public VotingResultsService(VoteRecordsRepository voteRecordsRepository,
                                VotingSessionService votingSessionService) {
        this.voteRecordsRepository = voteRecordsRepository;
        this.votingSessionService = votingSessionService;
    }

    public VotingResults getResults(String sessionId) {
        return getPublicResults(sessionId);
    }

    public VotingResults getResults(String sessionId, BoardAccessService.Access access) {
        VotingSession session = votingSessionService.requireSession(sessionId);
        List<VoteRecord> votes = voteRecordsRepository.listForSession(sessionId);
        Map<String, Integer> totalsByTarget = new LinkedHashMap<>();
        for (VoteRecord vote : votes) {
            totalsByTarget.merge(vote.targetRef(), vote.voteValue(), Integer::sum);
        }
        boolean progressHidden = shouldHideProgress(session, access);
        boolean identitiesHidden = shouldHideIdentities(session, access);
        List<VoteRecord> visibleVotes = identitiesHidden || progressHidden ? List.of() : votes;
        return new VotingResults(session, progressHidden ? Map.of() : totalsByTarget, visibleVotes, identitiesHidden, progressHidden);
    }

    public VotingResults getPublicResults(String sessionId) {
        VotingSession session = votingSessionService.requireSession(sessionId);
        Board board = votingSessionService.requireActiveBoard(session.boardId());
        BoardAccessService.Access publicAudience = new BoardAccessService.Access(board, BoardAccessService.ROLE_VIEWER, false);
        return getResults(sessionId, publicAudience);
    }

    private boolean shouldHideProgress(VotingSession session, BoardAccessService.Access access) {
        if (session.state() != VotingSessionState.OPEN) {
            return false;
        }
        if (session.rules().showProgressDuringVoting()) {
            return false;
        }
        return access == null || !access.isFacilitator();
    }

    private boolean shouldHideIdentities(VotingSession session, BoardAccessService.Access access) {
        if (!session.rules().anonymousVotes()) {
            return false;
        }
        return access == null || !access.isFacilitator() || session.state() == VotingSessionState.OPEN;
    }
}
