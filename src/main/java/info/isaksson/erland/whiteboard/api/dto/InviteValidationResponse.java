package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

public record InviteValidationResponse(
        boolean valid,
        String reason,      // OK | NOT_FOUND | EXPIRED | REVOKED | MAX_USES_REACHED
        String boardId,
        String permission,
        Instant expiresAt
) {
    public static InviteValidationResponse notFound() {
        return new InviteValidationResponse(false, "NOT_FOUND", null, null, null);
    }
}
