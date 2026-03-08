package info.isaksson.erland.whiteboard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardPermissionsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;

public class BoardAccessServiceTest {

    private InMemoryBoardsRepository boardsRepository;
    private InMemoryBoardPermissionsRepository permissionsRepository;
    private BoardAccessService boardAccessService;
    private String boardId;

    @BeforeEach
    void setup() {
        boardsRepository = new InMemoryBoardsRepository();
        permissionsRepository = new InMemoryBoardPermissionsRepository();
        boardAccessService = new BoardAccessService(boardsRepository, permissionsRepository);
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Board", "whiteboard", "advanced", "alice", "active", null, null));
        permissionsRepository.upsert(boardId, "bob", BoardAccessService.ROLE_VIEWER);
        permissionsRepository.upsert(boardId, "carol", BoardAccessService.ROLE_EDITOR);
    }

    @Test
    void membership_access_exposes_capability_specific_decisions() {
        var owner = boardAccessService.findCapabilityAccess(boardId, "alice").orElseThrow();
        var viewer = boardAccessService.findCapabilityAccess(boardId, "bob").orElseThrow();
        var editor = boardAccessService.findCapabilityAccess(boardId, "carol").orElseThrow();

        assertEquals(BoardAccessService.ROLE_OWNER, owner.role());
        assertTrue(owner.allows(BoardCapability.BOARD_OWNER));
        assertTrue(owner.allows(BoardCapability.BOARD_WRITE));
        assertTrue(owner.allows(BoardCapability.COMMENT_PARTICIPATE));
        assertTrue(owner.allows(BoardCapability.FACILITATE_BOARD));
        assertTrue(owner.canObserveVotes());
        assertTrue(owner.allows(BoardCapability.LIBRARY_MANAGE));

        assertTrue(viewer.allows(BoardCapability.BOARD_READ));
        assertFalse(viewer.allows(BoardCapability.BOARD_WRITE));
        assertTrue(viewer.allows(BoardCapability.COMMENT_PARTICIPATE));
        assertTrue(viewer.isParticipant());
        assertTrue(viewer.canObserveVotes());
        assertTrue(viewer.allows(BoardCapability.ASSET_USE));
        assertFalse(viewer.allows(BoardCapability.LIBRARY_SHARE));

        assertTrue(editor.allows(BoardCapability.BOARD_WRITE));
        assertFalse(editor.isFacilitator());
        assertTrue(editor.isParticipant());
        assertTrue(editor.allows(BoardCapability.ASSET_MANAGE));
        assertTrue(editor.allows(BoardCapability.LIBRARY_SHARE));
    }

    @Test
    void publication_read_access_is_separate_from_membership_board_read() {
        var publicationReader = boardAccessService.findPublicationReadAccess(boardId, null, true).orElseThrow();

        assertTrue(publicationReader.isPublicationReader());
        assertTrue(publicationReader.allows(BoardCapability.PUBLICATION_READ));
        assertTrue(publicationReader.allows(BoardCapability.ASSET_USE));
        assertTrue(publicationReader.allows(BoardCapability.LIBRARY_READ));
        assertFalse(publicationReader.allows(BoardCapability.BOARD_READ));
        assertFalse(publicationReader.allows(BoardCapability.COMMENT_PARTICIPATE));
        assertFalse(publicationReader.allows(BoardCapability.VOTE_PARTICIPATE));
        assertFalse(publicationReader.allows(BoardCapability.VOTE_OBSERVE));
        assertFalse(publicationReader.allows(BoardCapability.BOARD_WRITE));
    }

    @Test
    void publication_flag_does_not_replace_membership_permissions() {
        var viewer = boardAccessService.findCapabilityAccess(boardId, "bob", true).orElseThrow();

        assertEquals(BoardAccessService.ROLE_VIEWER, viewer.role());
        assertFalse(viewer.isPublicationReader());
        assertTrue(viewer.allows(BoardCapability.BOARD_READ));
    }
}
