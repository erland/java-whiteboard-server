package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreateCommentRequest", description = "Request body used to create a board comment, object comment, region comment, or reply comment.")
public record CreateCommentRequest(
        @Schema(description = "Comment target type.", example = "object", enumeration = {"board", "object", "region", "comment"})
        String targetType,
        @Schema(description = "Target reference. For board comments this may be omitted and the board id will be used automatically.", example = "shape-17", nullable = true)
        String targetRef,
        @Schema(description = "Parent comment id when creating a reply comment.", example = "comment-123", nullable = true)
        String parentCommentId,
        @Schema(description = "Comment content.", example = "Please align this box with the process lane.")
        String content
) {
}
