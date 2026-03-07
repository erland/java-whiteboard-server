package info.isaksson.erland.whiteboard.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.api.dto.CreateBoardRequest;
import info.isaksson.erland.whiteboard.api.dto.UpdateBoardRequest;

class BoardMetadataRulesTest {

    private final BoardMetadataRules rules = new BoardMetadataRules();

    @Test
    void normalizeCreate_requires_name() {
        BoardMetadataRules.ValidationException ex = assertThrows(
                BoardMetadataRules.ValidationException.class,
                () -> rules.normalizeCreate(new CreateBoardRequest(" ", "whiteboard", "advanced"))
        );
        assertEquals("Field 'name' is required.", ex.getMessage());
    }

    @Test
    void normalizeCreate_requires_type() {
        BoardMetadataRules.ValidationException ex = assertThrows(
                BoardMetadataRules.ValidationException.class,
                () -> rules.normalizeCreate(new CreateBoardRequest("Board", " ", "advanced"))
        );
        assertEquals("Field 'type' is required.", ex.getMessage());
    }

    @Test
    void normalizeCreate_maps_legacy_type_to_board_type_and_trims_name() {
        BoardMetadataRules.NormalizedCreate normalized = rules.normalizeCreate(
                new CreateBoardRequest("  Legacy board  ", "kanban-v2", null)
        );

        assertEquals("Legacy board", normalized.name());
        assertEquals("whiteboard", normalized.type());
        assertEquals("kanban-v2", normalized.boardType());
    }

    @Test
    void normalizeUpdate_requires_board_to_be_active() {
        Board archived = new Board("b1", "Board", "whiteboard", "advanced", "alice", "archived", null, null);

        BoardMetadataRules.BoardNotActiveException ex = assertThrows(
                BoardMetadataRules.BoardNotActiveException.class,
                () -> rules.normalizeUpdate(new UpdateBoardRequest("Renamed", null, null), archived)
        );

        assertEquals("Board is not active.", ex.getMessage());
    }

    @Test
    void normalizeUpdate_preserves_existing_values_for_blank_inputs() {
        Board existing = new Board("b1", "Board", "whiteboard", "advanced", "alice", "active", null, null);

        BoardMetadataRules.NormalizedUpdate normalized = rules.normalizeUpdate(
                new UpdateBoardRequest(" ", null, null),
                existing
        );

        assertEquals("Board", normalized.name());
        assertEquals("whiteboard", normalized.type());
        assertEquals("advanced", normalized.boardType());
    }

    @Test
    void normalizeUpdate_allows_changing_board_type_with_whiteboard_kind() {
        Board existing = new Board("b1", "Board", "whiteboard", "advanced", "alice", "active", null, null);

        BoardMetadataRules.NormalizedUpdate normalized = rules.normalizeUpdate(
                new UpdateBoardRequest(null, "whiteboard", "timeline-pro"),
                existing
        );

        assertEquals("Board", normalized.name());
        assertEquals("whiteboard", normalized.type());
        assertEquals("timeline-pro", normalized.boardType());
    }
}
