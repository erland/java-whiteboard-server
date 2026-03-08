package info.isaksson.erland.whiteboard.comments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryCommentsRepository;

public class CommentServiceTest {

    private InMemoryCommentsRepository commentsRepository;
    private InMemoryBoardsRepository boardsRepository;
    private CommentService commentService;
    private String boardId;

    @BeforeEach
    void setup() {
        commentsRepository = new InMemoryCommentsRepository();
        boardsRepository = new InMemoryBoardsRepository();
        commentService = new CommentService(commentsRepository, boardsRepository);
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Board", "whiteboard", "advanced", "alice", "active", null, null));
    }

    @Test
    void creates_board_comment_with_server_managed_metadata() {
        Comment comment = commentService.createBoardComment(boardId, "alice", "Initial note");

        assertEquals(CommentTargetType.BOARD, comment.targetType());
        assertEquals(boardId, comment.targetRef());
        assertEquals(CommentState.ACTIVE, comment.state());
        assertNotNull(comment.createdAt());
        assertNotNull(comment.updatedAt());
    }

    @Test
    void creates_reply_comment_for_same_board_parent() {
        Comment parent = commentService.createObjectComment(boardId, "node-1", "alice", "Please review");

        Comment reply = commentService.replyToComment(boardId, parent.id(), "bob", "Will do");

        assertEquals(parent.id(), reply.parentCommentId());
        assertEquals(CommentTargetType.COMMENT, reply.targetType());
        assertEquals(parent.id(), reply.targetRef());
    }

    @Test
    void rejects_reply_when_parent_is_on_another_board() {
        String otherBoardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(otherBoardId, "Other", "whiteboard", "advanced", "alice", "active", null, null));
        Comment parent = commentService.createBoardComment(otherBoardId, "alice", "Elsewhere");

        assertThrows(IllegalArgumentException.class, () -> commentService.replyToComment(boardId, parent.id(), "bob", "Will do"));
    }

    @Test
    void author_can_edit_active_comment() {
        Comment created = commentService.createBoardComment(boardId, "alice", "Old");

        Comment updated = commentService.updateContent(created.id(), "alice", "New").orElseThrow();

        assertEquals("New", updated.content());
    }

    @Test
    void resolve_reopen_and_delete_follow_valid_lifecycle() {
        Comment created = commentService.createBoardComment(boardId, "alice", "Track this");

        Comment resolved = commentService.resolve(created.id()).orElseThrow();
        assertEquals(CommentState.RESOLVED, resolved.state());
        assertNotNull(resolved.resolvedAt());

        Comment reopened = commentService.reopen(created.id()).orElseThrow();
        assertEquals(CommentState.ACTIVE, reopened.state());
        assertNull(reopened.resolvedAt());

        Comment deleted = commentService.delete(created.id()).orElseThrow();
        assertEquals(CommentState.DELETED, deleted.state());
        assertNotNull(deleted.deletedAt());
        assertThrows(IllegalArgumentException.class, () -> commentService.reopen(created.id()));
    }

    @Test
    void lists_comments_for_board_in_creation_order() {
        commentService.createBoardComment(boardId, "alice", "One");
        commentService.createBoardComment(boardId, "alice", "Two");

        var comments = commentService.listForBoard(boardId);

        assertEquals(2, comments.size());
        assertEquals("One", comments.get(0).content());
        assertEquals("Two", comments.get(1).content());
        assertTrue(comments.stream().allMatch(comment -> boardId.equals(comment.boardId())));
    }
}
