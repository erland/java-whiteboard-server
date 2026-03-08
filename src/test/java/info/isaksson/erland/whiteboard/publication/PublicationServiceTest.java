package info.isaksson.erland.whiteboard.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryPublicationsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemorySnapshotsRepository;

public class PublicationServiceTest {

    private InMemoryPublicationsRepository publicationsRepository;
    private InMemoryBoardsRepository boardsRepository;
    private InMemorySnapshotsRepository snapshotsRepository;
    private PublicationService publicationService;
    private String boardId;

    @BeforeEach
    void setup() {
        publicationsRepository = new InMemoryPublicationsRepository();
        boardsRepository = new InMemoryBoardsRepository();
        snapshotsRepository = new InMemorySnapshotsRepository();
        publicationService = new PublicationService(publicationsRepository, boardsRepository, snapshotsRepository);
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Board", "whiteboard", "advanced", "alice", "active", null, null));
    }

    @Test
    void creates_board_publication_with_raw_token_and_hashed_storage() {
        PublicationService.CreatedPublication created = publicationService.createBoardPublication(
                boardId,
                "alice",
                Instant.now().plusSeconds(3600),
                false);

        assertNotNull(created.accessToken());
        assertEquals(PublicationState.ACTIVE, created.publication().state());
        assertEquals(PublicationTargetType.BOARD, created.publication().targetType());
        assertEquals(PublicationAccessTokens.sha256Hex(created.accessToken()), created.publication().accessTokenHash());
    }

    @Test
    void creates_snapshot_publication_for_existing_snapshot() {
        long version = snapshotsRepository.create(boardId, "alice", "{\"nodes\":[]}").version();

        PublicationService.CreatedPublication created = publicationService.createSnapshotPublication(
                boardId,
                version,
                "alice",
                null,
                true);

        assertEquals(PublicationTargetType.SNAPSHOT, created.publication().targetType());
        assertEquals(version, created.publication().snapshotVersion());
        assertTrue(created.publication().allowComments());
    }

    @Test
    void rejects_snapshot_publication_when_snapshot_does_not_exist() {
        assertThrows(IllegalArgumentException.class, () -> publicationService.createSnapshotPublication(
                boardId,
                99L,
                "alice",
                null,
                false));
    }

    @Test
    void rotate_access_token_updates_hash() {
        PublicationService.CreatedPublication created = publicationService.createBoardPublication(boardId, "alice", null, false);

        PublicationService.CreatedPublication rotated = publicationService.rotateAccessToken(created.publication().id()).orElseThrow();

        assertNotEquals(created.accessToken(), rotated.accessToken());
        assertEquals(PublicationAccessTokens.sha256Hex(rotated.accessToken()), rotated.publication().accessTokenHash());
    }

    @Test
    void revoke_marks_publication_revoked() {
        PublicationService.CreatedPublication created = publicationService.createBoardPublication(boardId, "alice", null, false);

        Publication revoked = publicationService.revoke(created.publication().id()).orElseThrow();

        assertEquals(PublicationState.REVOKED, revoked.state());
        assertNotNull(revoked.revokedAt());
    }
}
