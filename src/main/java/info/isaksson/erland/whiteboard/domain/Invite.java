package info.isaksson.erland.whiteboard.domain;

import java.time.Instant;

public record Invite(
        String id,
        String boardId,
        String tokenHash,
        String permission, // viewer | editor
        Instant expiresAt,
        Integer maxUses,
        int uses,
        Instant revokedAt,
        Instant createdAt
) {
    public boolean isRevoked() {
        return revokedAt != null;
    }
}
