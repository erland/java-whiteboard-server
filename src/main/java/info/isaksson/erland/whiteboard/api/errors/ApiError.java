package info.isaksson.erland.whiteboard.api.errors;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ApiError", description = "Standard JSON error payload returned by the REST API.")
public record ApiError(
        @Schema(description = "Stable machine-readable error code.", example = "VALIDATION_ERROR", enumeration = {
                "UNAUTHORIZED",
                "FORBIDDEN",
                "NOT_FOUND",
                "VALIDATION_ERROR",
                "BOARD_NOT_ACTIVE",
                "INVITE_NOT_FOUND",
                "PUBLICATION_NOT_FOUND",
                "PUBLICATION_REVOKED",
                "PUBLICATION_EXPIRED",
                "PUBLICATION_INACTIVE",
                "COMMENT_NOT_FOUND",
                "COMMENT_STATE_INVALID",
                "PAYLOAD_TOO_LARGE"
        })
        String code,
        @Schema(description = "Human-readable explanation of the error.", example = "Field 'token' is required.")
        String message
) {
}
