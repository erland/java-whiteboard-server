package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import info.isaksson.erland.whiteboard.domain.Board;

@Schema(name = "BoardResponse", description = "Board metadata returned by the REST API.")
public record BoardResponse(
        @Schema(description = "Board identifier.", example = "board-123")
        String id,
        @Schema(description = "Human-readable board name.", example = "Operations planning")
        String name,
        @Schema(description = "Normalized board kind.", example = "whiteboard")
        String type,
        @Schema(description = "Optional board type/category chosen by the client.", example = "team-board")
        String boardType,
        @Schema(description = "User id of the board owner.", example = "alice")
        String ownerUserId,
        @Schema(description = "Board lifecycle status.", example = "ACTIVE")
        String status,
        @Schema(description = "Creation timestamp.", example = "2026-03-01T10:15:30Z")
        Instant createdAt,
        @Schema(description = "Last metadata update timestamp.", example = "2026-03-02T08:45:00Z")
        Instant updatedAt
) {
    public static BoardResponse from(Board b) {
        return new BoardResponse(
                b.id(),
                b.name(),
                b.type(),
                b.boardType(),
                b.ownerUserId(),
                b.status(),
                b.createdAt(),
                b.updatedAt());
    }
}
