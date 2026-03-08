package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ActivateAssetRequest", description = "Request payload used to mark an asset active after upload or validation has completed.")
public record ActivateAssetRequest(
        @Schema(description = "Optional new version tag stored when the asset becomes active.", example = "v2")
        String versionTag
) {
}
