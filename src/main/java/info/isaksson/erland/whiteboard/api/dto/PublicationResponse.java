package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import info.isaksson.erland.whiteboard.publication.Publication;

@Schema(name = "PublicationResponse", description = "Publication metadata returned by publication management and resolution endpoints.")
public record PublicationResponse(
        @Schema(description = "Publication identifier.", example = "publication-123")
        String id,
        @Schema(description = "Board identifier.", example = "board-123")
        String boardId,
        @Schema(description = "Snapshot version when the publication targets a snapshot.", example = "3", nullable = true)
        Long snapshotVersion,
        @Schema(description = "Publication target type.", example = "snapshot")
        String targetType,
        @Schema(description = "Publication lifecycle state.", example = "active")
        String state,
        @Schema(description = "User id that created the publication.", example = "alice")
        String createdByUserId,
        @Schema(description = "Whether publication policy allows comments when comment APIs are introduced.", example = "false")
        boolean allowComments,
        @Schema(description = "Creation timestamp.", example = "2026-03-01T10:15:30Z")
        Instant createdAt,
        @Schema(description = "Last update timestamp.", example = "2026-03-02T08:45:00Z")
        Instant updatedAt,
        @Schema(description = "Expiration timestamp if present.", example = "2026-04-01T00:00:00Z", nullable = true)
        Instant expiresAt,
        @Schema(description = "Revocation timestamp when revoked.", example = "2026-03-15T12:00:00Z", nullable = true)
        Instant revokedAt
) {
    public static PublicationResponse from(Publication publication) {
        return new PublicationResponse(
                publication.id(),
                publication.boardId(),
                publication.snapshotVersion(),
                publication.targetType().storageValue(),
                publication.state().storageValue(),
                publication.createdByUserId(),
                publication.allowComments(),
                publication.createdAt(),
                publication.updatedAt(),
                publication.expiresAt(),
                publication.revokedAt());
    }
}
