package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreateAssetRequest", description = "Request payload used to create durable asset metadata for a board-scoped asset.")
public record CreateAssetRequest(
        @Schema(description = "Logical asset name as displayed to users.", example = "reference-diagram.png")
        String logicalName,
        @Schema(description = "Declared media type for the asset.", example = "image/png")
        String contentType,
        @Schema(description = "Expected binary size in bytes.", example = "2048")
        Long sizeBytes,
        @Schema(description = "Optional integrity hash or digest for the uploaded content.", example = "sha256:abc123")
        String integrityHash,
        @Schema(description = "Optional client supplied version tag used to correlate subsequent upload/activation flows.", example = "v1")
        String versionTag
) {
}
