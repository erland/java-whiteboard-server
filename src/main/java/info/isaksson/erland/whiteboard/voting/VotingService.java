package info.isaksson.erland.whiteboard.voting;

import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.VoteRecordsRepository;
import info.isaksson.erland.whiteboard.persistence.VotingSessionsRepository;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VotingService {

    private final VotingSessionService votingSessionService;
    private final VoteCommandService voteCommandService;
    private final VotingResultsService votingResultsService;

    @Inject
    public VotingService(VotingSessionService votingSessionService,
                         VoteCommandService voteCommandService,
                         VotingResultsService votingResultsService) {
        this.votingSessionService = votingSessionService;
        this.voteCommandService = voteCommandService;
        this.votingResultsService = votingResultsService;
    }

    public VotingService(VotingSessionsRepository votingSessionsRepository,
                         VoteRecordsRepository voteRecordsRepository,
                         BoardsRepository boardsRepository) {
        this.votingSessionService = new VotingSessionService(votingSessionsRepository, boardsRepository);
        this.voteCommandService = new VoteCommandService(voteRecordsRepository, votingSessionService);
        this.votingResultsService = new VotingResultsService(voteRecordsRepository, votingSessionService);
    }

    public VotingSession createDraftSession(String boardId,
                                            VotingScopeType scopeType,
                                            String scopeRef,
                                            String createdByUserId,
                                            VotingRules rules) {
        return votingSessionService.createDraftSession(boardId, scopeType, scopeRef, createdByUserId, rules);
    }

    public Optional<VotingSession> findSession(String sessionId) {
        return votingSessionService.findSession(sessionId);
    }

    public List<VotingSession> listSessionsForBoard(String boardId) {
        return votingSessionService.listSessionsForBoard(boardId);
    }

    public VotingSession openSession(String sessionId) {
        return votingSessionService.openSession(sessionId);
    }

    public VotingSession closeSession(String sessionId) {
        return votingSessionService.closeSession(sessionId);
    }

    public VotingSession revealSession(String sessionId) {
        return votingSessionService.revealSession(sessionId);
    }

    public VotingSession cancelSession(String sessionId) {
        return votingSessionService.cancelSession(sessionId);
    }

    public VoteRecord castVote(String sessionId,
                               String participantId,
                               String participantRole,
                               boolean viaPublication,
                               String targetRef,
                               int voteValue) {
        return voteCommandService.castVote(sessionId, participantId, participantRole, viaPublication, targetRef, voteValue);
    }

    public boolean removeVote(String sessionId,
                              String participantId,
                              String targetRef,
                              boolean updatesAllowedOverride) {
        return voteCommandService.removeVote(sessionId, participantId, targetRef, updatesAllowedOverride);
    }

    public List<VoteRecord> listVotes(String sessionId) {
        return voteCommandService.listVotes(sessionId);
    }

    public VotingResults getResults(String sessionId) {
        return votingResultsService.getResults(sessionId);
    }

    public VotingResults getResults(String sessionId, BoardAccessService.Access access) {
        return votingResultsService.getResults(sessionId, access);
    }

    public VotingResults getPublicResults(String sessionId) {
        return votingResultsService.getPublicResults(sessionId);
    }
}
