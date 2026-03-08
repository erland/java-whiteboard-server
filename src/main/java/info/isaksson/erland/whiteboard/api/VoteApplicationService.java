package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.dto.CreateVoteRequest;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.voting.VoteRecord;
import info.isaksson.erland.whiteboard.voting.VotingResults;
import info.isaksson.erland.whiteboard.voting.VotingService;
import info.isaksson.erland.whiteboard.voting.VotingSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class VoteApplicationService {

    private final VotingService votingService;
    private final VotingAccessResolver accessResolver;
    private final VotingTransitionNotifier transitionNotifier;

    @Inject
    public VoteApplicationService(VotingService votingService,
                                  VotingAccessResolver accessResolver,
                                  VotingTransitionNotifier transitionNotifier) {
        this.votingService = votingService;
        this.accessResolver = accessResolver;
        this.transitionNotifier = transitionNotifier;
    }

    public VoteRecord castVote(String boardId,
                               String sessionId,
                               String publicationToken,
                               String participantToken,
                               CreateVoteRequest req) {
        VotingSession session = accessResolver.requireSession(boardId, sessionId);
        VotingAccessResolver.VoteActor actor = accessResolver.requireVoteParticipant(boardId, publicationToken, participantToken);
        VoteRecord vote = votingService.castVote(
                session.id(),
                actor.participantId(),
                actor.access().role(),
                actor.access().viaPublication(),
                req == null ? null : req.targetRef(),
                req == null || req.voteValue() == null ? 1 : req.voteValue());
        transitionNotifier.notifyVotesUpdated(session, actor.auditUserId());
        return vote;
    }

    public void removeVote(String boardId,
                           String sessionId,
                           String targetRef,
                           String publicationToken,
                           String participantToken) {
        VotingSession session = accessResolver.requireSession(boardId, sessionId);
        VotingAccessResolver.VoteActor actor = accessResolver.requireVoteParticipant(boardId, publicationToken, participantToken);
        boolean removed = votingService.removeVote(session.id(), actor.participantId(), targetRef, false);
        if (!removed) {
            throw new NotFoundException();
        }
        transitionNotifier.notifyVotesUpdated(session, actor.auditUserId());
    }

    public VotingResults getResults(String boardId, String sessionId, String publicationToken) {
        BoardAccessService.Access access = accessResolver.requireVoteObservationAccess(boardId, publicationToken);
        accessResolver.requireSession(boardId, sessionId);
        return votingService.getResults(sessionId, access);
    }
}
