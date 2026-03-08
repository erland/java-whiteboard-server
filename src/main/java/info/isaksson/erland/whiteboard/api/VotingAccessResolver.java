package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.publication.Publication;
import info.isaksson.erland.whiteboard.publication.PublicationAccessTokens;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import info.isaksson.erland.whiteboard.voting.VotingService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class VotingAccessResolver {

    private final BoardGuards boardGuards;
    private final SecurityIdentity identity;
    private final PublicationAccessSupport publicationAccessSupport;
    private final VotingService votingService;

    @Inject
    public VotingAccessResolver(BoardGuards boardGuards,
                                SecurityIdentity identity,
                                PublicationAccessSupport publicationAccessSupport,
                                VotingService votingService) {
        this.boardGuards = boardGuards;
        this.identity = identity;
        this.publicationAccessSupport = publicationAccessSupport;
        this.votingService = votingService;
    }

    public String requireFacilitatorUserId(String boardId) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireFacilitationAccess(boardId, userId);
        return userId;
    }

    public BoardAccessService.Access requireVoteObservationAccess(String boardId, String publicationToken) {
        Publication publication = publicationAccessSupport.resolveReadablePublication(boardId, publicationToken);
        if (identity != null && !identity.isAnonymous()) {
            String userId = Authz.userId(identity);
            return boardGuards.requireVoteObservationAccess(boardId, userId, publication != null);
        }
        if (publication == null) {
            throw new NotFoundException();
        }
        return boardGuards.requirePublicationReadAccess(boardId, null, true);
    }

    public VoteActor requireVoteParticipant(String boardId, String publicationToken, String participantToken) {
        Publication publication = publicationAccessSupport.resolveReadablePublication(boardId, publicationToken);
        if (identity != null && !identity.isAnonymous()) {
            String userId = Authz.userId(identity);
            BoardAccessService.Access access = boardGuards.requireVoteParticipationAccess(boardId, userId, publication != null);
            return new VoteActor(access, userId, userId);
        }
        if (publication == null) {
            throw new NotFoundException();
        }
        BoardAccessService.Access access = boardGuards.requirePublicationReadAccess(boardId, null, true);
        String normalizedParticipantToken = normalizeParticipantToken(participantToken);
        String participantId = anonymousPublicationParticipantId(publicationToken, normalizedParticipantToken);
        return new VoteActor(access, participantId, "publication:" + publication.id());
    }

    public info.isaksson.erland.whiteboard.voting.VotingSession requireSession(String boardId, String sessionId) {
        info.isaksson.erland.whiteboard.voting.VotingSession session = votingService.findSession(sessionId).orElseThrow(NotFoundException::new);
        if (!boardId.equals(session.boardId())) {
            throw new NotFoundException();
        }
        return session;
    }


    private static String normalizeParticipantToken(String participantToken) {
        if (participantToken == null || participantToken.isBlank()) {
            throw new IllegalArgumentException("Field 'participantToken' is required for anonymous publication voting.");
        }
        return participantToken.trim();
    }

    private static String anonymousPublicationParticipantId(String publicationToken, String participantToken) {
        return "publication-participant:" + PublicationAccessTokens.sha256Hex(publicationToken.trim() + ":" + participantToken);
    }

    public record VoteActor(BoardAccessService.Access access, String participantId, String auditUserId) {
    }
}
