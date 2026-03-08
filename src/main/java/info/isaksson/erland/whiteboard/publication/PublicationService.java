package info.isaksson.erland.whiteboard.publication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.PublicationsRepository;
import info.isaksson.erland.whiteboard.persistence.SnapshotsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PublicationService {

    private final PublicationsRepository publicationsRepository;
    private final BoardsRepository boardsRepository;
    private final SnapshotsRepository snapshotsRepository;

    @Inject
    public PublicationService(PublicationsRepository publicationsRepository,
                              BoardsRepository boardsRepository,
                              SnapshotsRepository snapshotsRepository) {
        this.publicationsRepository = publicationsRepository;
        this.boardsRepository = boardsRepository;
        this.snapshotsRepository = snapshotsRepository;
    }

    public record CreatedPublication(Publication publication, String accessToken) {
    }

    public CreatedPublication createBoardPublication(String boardId,
                                                     String createdByUserId,
                                                     Instant expiresAt,
                                                     boolean allowComments) {
        requireActiveBoard(boardId);
        return createPublication(boardId, null, PublicationTargetType.BOARD, createdByUserId, expiresAt, allowComments);
    }

    public CreatedPublication createSnapshotPublication(String boardId,
                                                        long snapshotVersion,
                                                        String createdByUserId,
                                                        Instant expiresAt,
                                                        boolean allowComments) {
        requireActiveBoard(boardId);
        snapshotsRepository.get(boardId, snapshotVersion)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found for board publication"));
        return createPublication(boardId, snapshotVersion, PublicationTargetType.SNAPSHOT, createdByUserId, expiresAt, allowComments);
    }

    public Optional<Publication> revoke(String publicationId) {
        return publicationsRepository.revoke(publicationId);
    }

    public Optional<CreatedPublication> rotateAccessToken(String publicationId) {
        String token = PublicationAccessTokens.generateToken();
        String tokenHash = PublicationAccessTokens.sha256Hex(token);
        return publicationsRepository.rotateAccessToken(publicationId, tokenHash)
                .map(publication -> new CreatedPublication(publication, token));
    }

    public Optional<Publication> findById(String publicationId) {
        return publicationsRepository.findById(publicationId);
    }

    public List<Publication> listForBoard(String boardId) {
        return publicationsRepository.listForBoard(boardId);
    }

    private CreatedPublication createPublication(String boardId,
                                                 Long snapshotVersion,
                                                 PublicationTargetType targetType,
                                                 String createdByUserId,
                                                 Instant expiresAt,
                                                 boolean allowComments) {
        String token = PublicationAccessTokens.generateToken();
        Publication created = publicationsRepository.create(new Publication(
                UUID.randomUUID().toString(),
                boardId,
                snapshotVersion,
                targetType,
                PublicationState.ACTIVE,
                PublicationAccessTokens.sha256Hex(token),
                createdByUserId,
                allowComments,
                null,
                null,
                expiresAt,
                null
        ));
        return new CreatedPublication(created, token);
    }

    private void requireActiveBoard(String boardId) {
        boardsRepository.findById(boardId)
                .filter(board -> "active".equals(board.status()))
                .orElseThrow(() -> new IllegalArgumentException("Board not found or not active"));
    }
}
