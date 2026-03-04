package info.isaksson.erland.whiteboard.domain;

import java.time.Instant;

public record BoardSnapshot(
        String boardId,
        long version,
        String snapshotJson,
        Instant createdAt,
        String createdBy
) {
}
