package info.isaksson.erland.whiteboard.comments;

import java.time.Instant;

public record Comment(
        String id,
        String boardId,
        String parentCommentId,
        CommentTargetType targetType,
        String targetRef,
        String authorUserId,
        String content,
        CommentState state,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant deletedAt
) {
    public boolean isActive() {
        return state == CommentState.ACTIVE;
    }

    public boolean isResolved() {
        return state == CommentState.RESOLVED;
    }

    public boolean isDeleted() {
        return state == CommentState.DELETED;
    }
}
