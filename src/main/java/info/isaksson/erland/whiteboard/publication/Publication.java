package info.isaksson.erland.whiteboard.publication;

import java.time.Instant;

public record Publication(
        String id,
        String boardId,
        Long snapshotVersion,
        PublicationTargetType targetType,
        PublicationState state,
        String accessTokenHash,
        String createdByUserId,
        boolean allowComments,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        Instant revokedAt
) {
    public boolean targetsSnapshot() {
        return targetType == PublicationTargetType.SNAPSHOT;
    }

    public boolean isRevoked() {
        return revokedAt != null || state == PublicationState.REVOKED;
    }

    public boolean isActiveAt(Instant instant) {
        if (instant == null) {
            instant = Instant.now();
        }
        if (state != PublicationState.ACTIVE) {
            return false;
        }
        if (isRevoked()) {
            return false;
        }
        return expiresAt == null || !expiresAt.isBefore(instant);
    }
}
