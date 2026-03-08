package info.isaksson.erland.whiteboard.comments;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.CommentsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CommentService {

    private final CommentsRepository commentsRepository;
    private final BoardsRepository boardsRepository;

    @Inject
    public CommentService(CommentsRepository commentsRepository,
                          BoardsRepository boardsRepository) {
        this.commentsRepository = commentsRepository;
        this.boardsRepository = boardsRepository;
    }

    public Comment createBoardComment(String boardId, String authorUserId, String content) {
        return createComment(boardId, null, CommentTargetType.BOARD, boardId, authorUserId, content);
    }

    public Comment createObjectComment(String boardId, String objectId, String authorUserId, String content) {
        return createComment(boardId, null, CommentTargetType.OBJECT, objectId, authorUserId, content);
    }

    public Comment createRegionComment(String boardId, String regionRef, String authorUserId, String content) {
        return createComment(boardId, null, CommentTargetType.REGION, regionRef, authorUserId, content);
    }

    public Comment replyToComment(String boardId, String parentCommentId, String authorUserId, String content) {
        requireActiveBoard(boardId);
        Comment parent = commentsRepository.findById(parentCommentId)
                .filter(existing -> existing.boardId().equals(boardId))
                .orElseThrow(() -> new IllegalArgumentException("Parent comment not found for board"));
        if (parent.isDeleted()) {
            throw new IllegalArgumentException("Cannot reply to deleted comment");
        }
        return createComment(boardId, parentCommentId, CommentTargetType.COMMENT, parentCommentId, authorUserId, content);
    }

    public Optional<Comment> updateContent(String commentId, String actorUserId, String content) {
        CommentRules.requireContent(content);
        return commentsRepository.findById(commentId)
                .map(existing -> {
                    CommentRules.requireCanEdit(existing, actorUserId);
                    return commentsRepository.update(new Comment(
                            existing.id(),
                            existing.boardId(),
                            existing.parentCommentId(),
                            existing.targetType(),
                            existing.targetRef(),
                            existing.authorUserId(),
                            content,
                            existing.state(),
                            existing.createdAt(),
                            Instant.now(),
                            existing.resolvedAt(),
                            existing.deletedAt()
                    )).orElseThrow(() -> new IllegalStateException("Updated comment not found"));
                });
    }

    public Optional<Comment> resolve(String commentId) {
        return transition(commentId, CommentState.RESOLVED);
    }

    public Optional<Comment> reopen(String commentId) {
        return transition(commentId, CommentState.ACTIVE);
    }

    public Optional<Comment> delete(String commentId) {
        return transition(commentId, CommentState.DELETED);
    }

    public Optional<Comment> findById(String commentId) {
        return commentsRepository.findById(commentId);
    }

    public List<Comment> listForBoard(String boardId) {
        return commentsRepository.listForBoard(boardId);
    }

    private Comment createComment(String boardId,
                                  String parentCommentId,
                                  CommentTargetType targetType,
                                  String targetRef,
                                  String authorUserId,
                                  String content) {
        requireActiveBoard(boardId);
        CommentRules.validateNewComment(targetType, targetRef, parentCommentId, authorUserId, content);
        return commentsRepository.create(new Comment(
                UUID.randomUUID().toString(),
                boardId,
                parentCommentId,
                targetType,
                targetRef,
                authorUserId,
                content,
                CommentState.ACTIVE,
                null,
                null,
                null,
                null
        ));
    }

    private Optional<Comment> transition(String commentId, CommentState nextState) {
        return commentsRepository.findById(commentId)
                .map(existing -> {
                    CommentRules.requireTransition(existing, nextState);
                    Instant now = Instant.now();
                    return commentsRepository.update(new Comment(
                            existing.id(),
                            existing.boardId(),
                            existing.parentCommentId(),
                            existing.targetType(),
                            existing.targetRef(),
                            existing.authorUserId(),
                            existing.content(),
                            nextState,
                            existing.createdAt(),
                            now,
                            nextState == CommentState.RESOLVED ? now : (nextState == CommentState.ACTIVE ? null : existing.resolvedAt()),
                            nextState == CommentState.DELETED ? now : existing.deletedAt()
                    )).orElseThrow(() -> new IllegalStateException("Updated comment not found"));
                });
    }

    private void requireActiveBoard(String boardId) {
        boardsRepository.findById(boardId)
                .filter(board -> "active".equals(board.status()))
                .orElseThrow(() -> new IllegalArgumentException("Board not found or not active"));
    }
}
