package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import info.isaksson.erland.whiteboard.comments.Comment;

@Schema(name = "CommentResponse", description = "Durable comment metadata returned by comment endpoints.")
public record CommentResponse(
        @Schema(description = "Comment identifier.", example = "comment-123")
        String id,
        @Schema(description = "Board identifier.", example = "board-123")
        String boardId,
        @Schema(description = "Parent comment identifier for replies.", example = "comment-100", nullable = true)
        String parentCommentId,
        @Schema(description = "Comment target type.", example = "object")
        String targetType,
        @Schema(description = "Comment target reference.", example = "shape-17")
        String targetRef,
        @Schema(description = "Comment author user id.", example = "alice")
        String authorUserId,
        @Schema(description = "Comment content.", example = "Please align this box with the process lane.")
        String content,
        @Schema(description = "Comment lifecycle state.", example = "active")
        String state,
        @Schema(description = "Creation timestamp.", example = "2026-03-01T10:15:30Z")
        Instant createdAt,
        @Schema(description = "Last update timestamp.", example = "2026-03-02T08:45:00Z")
        Instant updatedAt,
        @Schema(description = "Resolution timestamp when resolved.", example = "2026-03-03T08:45:00Z", nullable = true)
        Instant resolvedAt,
        @Schema(description = "Deletion timestamp when deleted.", example = "2026-03-04T08:45:00Z", nullable = true)
        Instant deletedAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.id(),
                comment.boardId(),
                comment.parentCommentId(),
                comment.targetType().storageValue(),
                comment.targetRef(),
                comment.authorUserId(),
                comment.content(),
                comment.state().storageValue(),
                comment.createdAt(),
                comment.updatedAt(),
                comment.resolvedAt(),
                comment.deletedAt());
    }
}
