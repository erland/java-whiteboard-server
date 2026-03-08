package info.isaksson.erland.whiteboard.assets;

import java.time.Instant;

public record Asset(
        String id,
        String boardId,
        AssetScopeType scopeType,
        String scopeRef,
        String logicalName,
        String contentType,
        long sizeBytes,
        String integrityHash,
        String versionTag,
        AssetState state,
        String createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant deletedAt,
        String failureReason
) {
    public boolean isBoardScoped() {
        return scopeType == AssetScopeType.BOARD;
    }

    public boolean isActive() {
        return state == AssetState.ACTIVE;
    }

    public boolean isDeleted() {
        return state == AssetState.DELETED;
    }
}
