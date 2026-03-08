package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "UpdateCommentRequest", description = "Request body used to update the content of an existing comment.")
public record UpdateCommentRequest(
        @Schema(description = "Updated comment content.", example = "Updated wording for the review comment.")
        String content
) {
}
