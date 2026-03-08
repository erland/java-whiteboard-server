package info.isaksson.erland.whiteboard.api.dto;

import info.isaksson.erland.whiteboard.assets.Asset;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AssetResponse", description = "Durable asset metadata returned by asset endpoints.")
public record AssetResponse(
        @Schema(description = "Asset identifier.", example = "a3ab7438-9e6b-4dd5-b1ee-59d8467a7a8f")
        String id,
        @Schema(description = "Owning board identifier when board scoped.", example = "board-123")
        String boardId,
        @Schema(description = "Asset scope type.", example = "board")
        String scopeType,
        @Schema(description = "Scope reference value.", example = "board-123")
        String scopeRef,
        @Schema(description = "Logical asset name.", example = "reference-diagram.png")
        String logicalName,
        @Schema(description = "Asset media type.", example = "image/png")
        String contentType,
        @Schema(description = "Declared asset size in bytes.", example = "2048")
        long sizeBytes,
        @Schema(description = "Optional integrity hash.", example = "sha256:abc123")
        String integrityHash,
        @Schema(description = "Optional asset version tag.", example = "v1")
        String versionTag,
        @Schema(description = "Lifecycle state.", example = "active")
        String state,
        @Schema(description = "User who created the asset metadata.", example = "alice")
        String createdByUserId,
        @Schema(description = "Creation timestamp in ISO-8601 UTC format.", example = "2026-03-08T12:00:00Z")
        String createdAt,
        @Schema(description = "Last update timestamp in ISO-8601 UTC format.", example = "2026-03-08T12:00:00Z")
        String updatedAt,
        @Schema(description = "Activation timestamp when active.", example = "2026-03-08T12:05:00Z")
        String activatedAt,
        @Schema(description = "Deletion timestamp when deleted.", example = "2026-03-08T12:10:00Z")
        String deletedAt,
        @Schema(description = "Failure or quarantine reason when relevant.", example = "virus scan failed")
        String failureReason
) {
    public static AssetResponse from(Asset asset) {
        return new AssetResponse(
                asset.id(),
                asset.boardId(),
                asset.scopeType().storageValue(),
                asset.scopeRef(),
                asset.logicalName(),
                asset.contentType(),
                asset.sizeBytes(),
                asset.integrityHash(),
                asset.versionTag(),
                asset.state().storageValue(),
                asset.createdByUserId(),
                asset.createdAt() == null ? null : asset.createdAt().toString(),
                asset.updatedAt() == null ? null : asset.updatedAt().toString(),
                asset.activatedAt() == null ? null : asset.activatedAt().toString(),
                asset.deletedAt() == null ? null : asset.deletedAt().toString(),
                asset.failureReason());
    }
}
