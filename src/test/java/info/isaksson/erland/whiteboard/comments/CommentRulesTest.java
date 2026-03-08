package info.isaksson.erland.whiteboard.comments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class CommentRulesTest {

    @Test
    void accepts_valid_reply_target_shape() {
        assertDoesNotThrow(() -> CommentRules.validateNewComment(
                CommentTargetType.COMMENT,
                "parent-1",
                "parent-1",
                "alice",
                "Looks good"));
    }

    @Test
    void rejects_reply_when_target_and_parent_differ() {
        assertThrows(IllegalArgumentException.class, () -> CommentRules.validateNewComment(
                CommentTargetType.COMMENT,
                "parent-1",
                "parent-2",
                "alice",
                "Looks good"));
    }

    @Test
    void rejects_invalid_state_transition_from_deleted() {
        Comment deleted = new Comment(
                "c1",
                "b1",
                null,
                CommentTargetType.BOARD,
                "b1",
                "alice",
                "x",
                CommentState.DELETED,
                null,
                null,
                null,
                null);

        assertThrows(IllegalArgumentException.class, () -> CommentRules.requireTransition(deleted, CommentState.ACTIVE));
    }
}
