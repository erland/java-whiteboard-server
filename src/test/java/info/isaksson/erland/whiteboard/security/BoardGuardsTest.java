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
        assertDoesNotThrow(() -> boardGuards.requireAssetUseAccess(boardId, "bob", false));
        assertDoesNotThrow(() -> boardGuards.requireLibraryReadAccess(boardId, "bob", false));
        assertDoesNotThrow(() -> boardGuards.requireAssetManageAccess(boardId, "carol"));
        assertDoesNotThrow(() -> boardGuards.requireLibraryShareAccess(boardId, "carol"));
        assertDoesNotThrow(() -> boardGuards.requireLibraryManageAccess(boardId, "alice"));

        assertThrows(NotFoundException.class, () -> boardGuards.requireBoardWriteAccess(boardId, "bob"));
        assertThrows(NotFoundException.class, () -> boardGuards.requireLibraryShareAccess(boardId, "bob"));
        assertThrows(NotFoundException.class, () -> boardGuards.requireBoardReadAccess(boardId, "mallory"));
    }

    @Test
    void publication_read_access_is_separate_from_board_membership() {
        String boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Published", "whiteboard", "advanced", "alice", "active", null, null));

        assertDoesNotThrow(() -> boardGuards.requirePublicationReadAccess(boardId, null, true));
        assertThrows(NotFoundException.class, () -> boardGuards.requireBoardReadAccess(boardId, "mallory"));
        assertThrows(NotFoundException.class, () -> boardGuards.requireCommentParticipation(boardId, null, true));
    }
    @Test
    void tier2_guard_methods_follow_default_facilitator_and_participant_policy() {
        String boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Tier2", "whiteboard", "advanced", "alice", "active", null, null));
        permissionsRepository.upsert(boardId, "bob", BoardAccessService.ROLE_VIEWER);
        permissionsRepository.upsert(boardId, "carol", BoardAccessService.ROLE_EDITOR);

        assertDoesNotThrow(() -> boardGuards.requireFacilitationAccess(boardId, "alice"));
        assertDoesNotThrow(() -> boardGuards.requireTimerControl(boardId, "alice"));
        assertDoesNotThrow(() -> boardGuards.requireVoteParticipation(boardId, "bob"));
        assertDoesNotThrow(() -> boardGuards.requireVoteParticipation(boardId, "carol"));
        assertDoesNotThrow(() -> boardGuards.requireVoteObservation(boardId, "bob", false));
        assertDoesNotThrow(() -> boardGuards.requireTimerObservation(boardId, "bob", false));
        assertDoesNotThrow(() -> boardGuards.requireReactionEmit(boardId, "bob"));
        assertDoesNotThrow(() -> boardGuards.requireReactionObservation(boardId, "carol", false));
        assertDoesNotThrow(() -> boardGuards.requirePrivateModeContribution(boardId, "bob"));
        assertDoesNotThrow(() -> boardGuards.requirePrivateModeReveal(boardId, "alice"));
        assertDoesNotThrow(() -> boardGuards.requirePrivateModeView(boardId, "alice"));

        assertThrows(NotFoundException.class, () -> boardGuards.requireFacilitationAccess(boardId, "carol"));
        assertThrows(NotFoundException.class, () -> boardGuards.requireTimerControl(boardId, "bob"));
        assertThrows(NotFoundException.class, () -> boardGuards.requireReactionEmit(boardId, "mallory"));
        assertThrows(NotFoundException.class, () -> boardGuards.requireVoteObservation(boardId, null, true));
        assertThrows(NotFoundException.class, () -> boardGuards.requireTimerObservation(boardId, null, true));
        assertThrows(NotFoundException.class, () -> boardGuards.requireReactionObservation(boardId, null, true));
        assertThrows(NotFoundException.class, () -> boardGuards.requirePrivateModeReveal(boardId, "bob"));
        assertThrows(NotFoundException.class, () -> boardGuards.requirePrivateModeView(boardId, "bob"));
    }

}
