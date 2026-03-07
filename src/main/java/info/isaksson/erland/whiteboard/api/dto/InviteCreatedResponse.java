package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import info.isaksson.erland.whiteboard.domain.Invite;

@Schema(name = "InviteCreatedResponse", description = "Invite metadata returned when a new invite has been created. Includes the plain-text token exactly once.")
public record InviteCreatedResponse(
        @Schema(description = "Invite identifier.", example = "invite-123")
        String id,
        @Schema(description = "Board identifier.", example = "board-123")
        String boardId,
        @Schema(description = "Permission granted by the invite.", example = "viewer")
        String permission,
        @Schema(description = "Invite expiration timestamp if present.", example = "2026-04-01T00:00:00Z", nullable = true)
        Instant expiresAt,
        @Schema(description = "Maximum allowed uses if present.", example = "5", nullable = true)
        Integer maxUses,
        @Schema(description = "Number of recorded uses.", example = "0")
        int uses,
        @Schema(description = "Timestamp when the invite was revoked, if revoked.", example = "2026-03-15T12:00:00Z", nullable = true)
        Instant revokedAt,
        @Schema(description = "Creation timestamp.", example = "2026-03-01T10:15:30Z")
        Instant createdAt,
        @Schema(description = "Plain-text invite token. This is only returned at creation time.", example = "b6d2f3f0e8c7495f9a1c3c7d6a4b2e1f")
        String token
) {
    public static InviteCreatedResponse from(Invite i, String token) {
        return new InviteCreatedResponse(i.id(), i.boardId(), i.permission(), i.expiresAt(), i.maxUses(), i.uses(), i.revokedAt(), i.createdAt(), token);
    }
}
