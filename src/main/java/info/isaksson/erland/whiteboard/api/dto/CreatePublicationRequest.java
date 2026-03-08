package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreatePublicationRequest", description = "Request body used to create a board or snapshot publication.")
public record CreatePublicationRequest(
        @Schema(description = "Publication target type.", example = "board", enumeration = {"board", "snapshot"})
        String targetType,
        @Schema(description = "Snapshot version when creating a snapshot publication.", example = "3", nullable = true)
        Long snapshotVersion,
        @Schema(description = "Whether publication readers are allowed to participate in future comment capabilities.", example = "false")
        Boolean allowComments,
        @Schema(description = "Optional ISO-8601 expiration timestamp.", example = "2026-04-01T00:00:00Z", nullable = true)
        String expiresAt
) {
}
