package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ValidateInviteRequest", description = "Request body used to validate an invite token without accepting it.")
public record ValidateInviteRequest(
        @Schema(description = "Plain-text invite token received by the client.", example = "b6d2f3f0e8c7495f9a1c3c7d6a4b2e1f")
        String token
) {
}
