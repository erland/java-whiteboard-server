package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import info.isaksson.erland.whiteboard.domain.Invite;

@Schema(name = "InviteResponse", description = "Invite metadata returned when listing existing invites.")
public record InviteResponse(
        @Schema(description = "Invite identifier.", example = "invite-123")
        String id,
        @Schema(description = "Board identifier.", example = "board-123")
        String boardId,
        @Schema(description = "Permission granted by the invite.", example = "editor")
        String permission,
        @Schema(description = "Invite expiration timestamp if present.", example = "2026-04-01T00:00:00Z", nullable = true)
        Instant expiresAt,
        @Schema(description = "Maximum allowed uses if present.", example = "5", nullable = true)
        Integer maxUses,
        @Schema(description = "Number of recorded uses.", example = "1")
        int uses,
        @Schema(description = "Timestamp when the invite was revoked, if revoked.", example = "2026-03-15T12:00:00Z", nullable = true)
        Instant revokedAt,
        @Schema(description = "Creation timestamp.", example = "2026-03-01T10:15:30Z")
        Instant createdAt
) {
    public static InviteResponse from(Invite i) {
        return new InviteResponse(i.id(), i.boardId(), i.permission(), i.expiresAt(), i.maxUses(), i.uses(), i.revokedAt(), i.createdAt());
    }
}
