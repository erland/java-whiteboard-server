package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreateInviteRequest", description = "Request body used to create a board invite.")
public record CreateInviteRequest(
        @Schema(description = "Permission granted by the invite.", example = "viewer", enumeration = {"viewer", "editor"})
        String permission,
        @Schema(description = "Optional ISO-8601 expiration timestamp.", example = "2026-04-01T00:00:00Z", nullable = true)
        String expiresAt,
        @Schema(description = "Optional maximum number of times the invite can be used.", example = "5", nullable = true)
        Integer maxUses
) {
}
