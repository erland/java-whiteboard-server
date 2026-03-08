package info.isaksson.erland.whiteboard.api;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import info.isaksson.erland.whiteboard.api.dto.CreatePublicationRequest;
import info.isaksson.erland.whiteboard.api.dto.PublicationCreatedResponse;
import info.isaksson.erland.whiteboard.api.dto.PublicationResponse;
import info.isaksson.erland.whiteboard.publication.Publication;
import info.isaksson.erland.whiteboard.publication.PublicationService;
import info.isaksson.erland.whiteboard.publication.PublicationTargetType;
import info.isaksson.erland.whiteboard.security.BoardGuards;

@ApplicationScoped
public class PublicationApplicationService {

    private final PublicationService publicationService;
    private final BoardGuards boardGuards;
    private final PublicationRequestSupport publicationRequestSupport;

    public PublicationApplicationService(PublicationService publicationService,
                                         BoardGuards boardGuards,
                                         PublicationRequestSupport publicationRequestSupport) {
        this.publicationService = publicationService;
        this.boardGuards = boardGuards;
        this.publicationRequestSupport = publicationRequestSupport;
    }

    public PublicationCreatedResponse createPublication(String boardId, String userId, CreatePublicationRequest req) {
        boardGuards.requireOwner(boardId, userId);

        PublicationTargetType targetType = publicationRequestSupport.parseTargetType(req);
        Instant expiresAt = publicationRequestSupport.parseExpiresAt(req);
        boolean allowComments = publicationRequestSupport.allowComments(req);

        PublicationService.CreatedPublication created = switch (targetType) {
            case BOARD -> publicationService.createBoardPublication(boardId, userId, expiresAt, allowComments);
            case SNAPSHOT -> publicationService.createSnapshotPublication(
                    boardId,
                    publicationRequestSupport.requireSnapshotVersion(req),
                    userId,
                    expiresAt,
                    allowComments);
        };
        return PublicationCreatedResponse.from(created);
    }

    public List<PublicationResponse> listPublications(String boardId, String userId) {
        boardGuards.requireOwner(boardId, userId);
        return publicationService.listForBoard(boardId).stream()
                .map(PublicationResponse::from)
                .toList();
    }

    public void revokePublication(String boardId, String publicationId, String userId) {
        boardGuards.requireOwner(boardId, userId);
        Publication publication = requirePublicationForBoard(boardId, publicationId);
        publicationService.revoke(publication.id()).orElseThrow(NotFoundException::new);
    }

    public PublicationCreatedResponse rotatePublicationToken(String boardId, String publicationId, String userId) {
        boardGuards.requireOwner(boardId, userId);
        Publication publication = requirePublicationForBoard(boardId, publicationId);
        return publicationService.rotateAccessToken(publication.id())
                .map(PublicationCreatedResponse::from)
                .orElseThrow(NotFoundException::new);
    }

    private Publication requirePublicationForBoard(String boardId, String publicationId) {
        Publication publication = publicationService.findById(publicationId).orElseThrow(NotFoundException::new);
        if (!publication.boardId().equals(boardId)) {
            throw new NotFoundException();
        }
        return publication;
    }
}
