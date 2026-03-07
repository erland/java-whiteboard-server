package info.isaksson.erland.whiteboard.domain;

import java.time.Instant;

public record Board(
        String id,
        String name,
        String type,
        String boardType,
        String ownerUserId,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
