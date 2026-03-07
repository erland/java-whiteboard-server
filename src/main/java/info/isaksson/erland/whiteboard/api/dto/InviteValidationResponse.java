package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "InviteValidationResponse", description = "Result returned when validating an invite token.")
public record InviteValidationResponse(
        @Schema(description = "Whether the invite token is currently valid.", example = "true")
        boolean valid,
        @Schema(description = "Validation reason code.", example = "OK", enumeration = {"OK", "NOT_FOUND", "EXPIRED", "REVOKED", "MAX_USES_REACHED"})
        String reason,
        @Schema(description = "Board identifier when the invite could be resolved.", example = "board-123", nullable = true)
        String boardId,
        @Schema(description = "Permission granted by the invite when the invite could be resolved.", example = "viewer", nullable = true)
        String permission,
        @Schema(description = "Invite expiration timestamp if present.", example = "2026-04-01T00:00:00Z", nullable = true)
        Instant expiresAt
) {
    public static InviteValidationResponse notFound() {
        return new InviteValidationResponse(false, "NOT_FOUND", null, null, null);
    }
}
