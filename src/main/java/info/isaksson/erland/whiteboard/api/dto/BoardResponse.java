package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

import info.isaksson.erland.whiteboard.domain.Board;

public record BoardResponse(
        String id,
        String name,
        String type,
        String boardType,
        String ownerUserId,
        String status,
        Instant createdAt,
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
