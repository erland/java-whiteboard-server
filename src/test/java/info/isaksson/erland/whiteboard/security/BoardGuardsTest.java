package info.isaksson.erland.whiteboard.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardPermissionsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import jakarta.ws.rs.NotFoundException;

public class BoardGuardsTest {

    private InMemoryBoardsRepository boardsRepository;
    private InMemoryBoardPermissionsRepository permissionsRepository;
    private BoardGuards boardGuards;

    @BeforeEach
    void setup() {
        boardsRepository = new InMemoryBoardsRepository();
        permissionsRepository = new InMemoryBoardPermissionsRepository();
        boardGuards = new BoardGuards(boardsRepository, new BoardAccessService(boardsRepository, permissionsRepository));
    }

    @Test
    void owner_board_requires_owner_and_hides_deleted() {
        String activeBoardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(activeBoardId, "Active", "whiteboard", "advanced", "alice", "active", null, null));

        assertDoesNotThrow(() -> boardGuards.requireOwner(activeBoardId, "alice"));
        assertThrows(NotFoundException.class, () -> boardGuards.requireOwner(activeBoardId, "bob"));

        String deletedBoardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(deletedBoardId, "Deleted", "whiteboard", "advanced", "alice", "deleted", null, null));
        assertThrows(NotFoundException.class, () -> boardGuards.requireOwner(deletedBoardId, "alice"));
    }

    @Test
    void readable_and_writable_access_are_centralized() {
        String boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Shared", "whiteboard", "advanced", "alice", "active", null, null));
        permissionsRepository.upsert(boardId, "bob", BoardAccessService.ROLE_VIEWER);
        permissionsRepository.upsert(boardId, "carol", BoardAccessService.ROLE_EDITOR);

        assertDoesNotThrow(() -> boardGuards.requireBoardReadAccess(boardId, "alice"));
        assertDoesNotThrow(() -> boardGuards.requireBoardReadAccess(boardId, "bob"));
        assertDoesNotThrow(() -> boardGuards.requireBoardWriteAccess(boardId, "carol"));
        assertDoesNotThrow(() -> boardGuards.requireCommentParticipation(boardId, "bob", false));
        assertDoesNotThrow(() -> boardGuards.requireVoteParticipationAccess(boardId, "bob", false));
        assertDoesNotThrow(() -> boardGuards.requireVoteObservationAccess(boardId, "bob", false));
        assertDoesNotThrow(() -> boardGuards.requireAssetUseAccess(boardId, "bob", false));
        assertDoesNotThrow(() -> boardGuards.requireLibraryReadAccess(boardId, "bob", false));
        assertDoesNotThrow(() -> boardGuards.requireAssetManageAccess(boardId, "carol"));
        assertDoesNotThrow(() -> boardGuards.requireFacilitationAccess(boardId, "alice"));
        assertDoesNotThrow(() -> boardGuards.requireLibraryShareAccess(boardId, "carol"));
        assertDoesNotThrow(() -> boardGuards.requireLibraryManageAccess(boardId, "alice"));

        assertThrows(NotFoundException.class, () -> boardGuards.requireBoardWriteAccess(boardId, "bob"));
        assertThrows(NotFoundException.class, () -> boardGuards.requireLibraryShareAccess(boardId, "bob"));
        assertThrows(NotFoundException.class, () -> boardGuards.requireFacilitationAccess(boardId, "bob"));
        assertThrows(NotFoundException.class, () -> boardGuards.requireBoardReadAccess(boardId, "mallory"));
    }

    @Test
    void publication_read_access_is_separate_from_board_membership() {
        String boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Published", "whiteboard", "advanced", "alice", "active", null, null));

        assertDoesNotThrow(() -> boardGuards.requirePublicationReadAccess(boardId, null, true));
        assertThrows(NotFoundException.class, () -> boardGuards.requireBoardReadAccess(boardId, "mallory"));
        assertThrows(NotFoundException.class, () -> boardGuards.requireCommentParticipation(boardId, null, true));
        assertThrows(NotFoundException.class, () -> boardGuards.requireVoteObservationAccess(boardId, null, true));
    }
}
