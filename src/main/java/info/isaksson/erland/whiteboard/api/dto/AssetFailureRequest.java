package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AssetFailureRequest", description = "Request payload used for asset failure or quarantine actions.")
public record AssetFailureRequest(
        @Schema(description = "Human-readable reason for the failure or quarantine action.", example = "malware scan failed")
        String failureReason
) {
}
