package info.isaksson.erland.whiteboard.api;

import java.util.List;

import info.isaksson.erland.whiteboard.api.dto.CreateVotingSessionRequest;
import info.isaksson.erland.whiteboard.voting.VotingService;
import info.isaksson.erland.whiteboard.voting.VotingSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VotingSessionApplicationService {

    private final VotingService votingService;
    private final VotingAccessResolver accessResolver;
    private final VotingRequestSupport requestSupport;
    private final VotingTransitionNotifier transitionNotifier;

    @Inject
    public VotingSessionApplicationService(VotingService votingService,
                                          VotingAccessResolver accessResolver,
                                          VotingRequestSupport requestSupport,
                                          VotingTransitionNotifier transitionNotifier) {
        this.votingService = votingService;
        this.accessResolver = accessResolver;
        this.requestSupport = requestSupport;
        this.transitionNotifier = transitionNotifier;
    }

    public VotingSession createDraftSession(String boardId, CreateVotingSessionRequest req) {
        String userId = accessResolver.requireFacilitatorUserId(boardId);
        return votingService.createDraftSession(
                boardId,
                requestSupport.parseScopeType(req == null ? null : req.scopeType()),
                req == null ? null : req.scopeRef(),
                userId,
                requestSupport.parseRules(req));
    }

    public List<VotingSession> listSessions(String boardId, String publicationToken) {
        accessResolver.requireVoteObservationAccess(boardId, publicationToken);
        return votingService.listSessionsForBoard(boardId);
    }

    public VotingSession getSession(String boardId, String sessionId, String publicationToken) {
        accessResolver.requireVoteObservationAccess(boardId, publicationToken);
        return accessResolver.requireSession(boardId, sessionId);
    }

    public VotingSession transition(String boardId, String sessionId, String action) {
        accessResolver.requireFacilitatorUserId(boardId);
        accessResolver.requireSession(boardId, sessionId);
        VotingSession updated = switch (action) {
            case "open" -> votingService.openSession(sessionId);
            case "close" -> votingService.closeSession(sessionId);
            case "reveal" -> votingService.revealSession(sessionId);
            case "cancel" -> votingService.cancelSession(sessionId);
            default -> throw new IllegalArgumentException("Unsupported transition");
        };
        transitionNotifier.notifyTransition(action, updated);
        return updated;
    }
}
