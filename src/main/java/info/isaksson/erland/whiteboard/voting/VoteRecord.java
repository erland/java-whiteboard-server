package info.isaksson.erland.whiteboard.voting;

import java.time.Instant;

public record VoteRecord(
        String id,
        String sessionId,
        String participantId,
        String targetRef,
        int voteValue,
        Instant createdAt,
        Instant updatedAt
) {
}
