package info.isaksson.erland.whiteboard.voting;

import java.time.Instant;

public record VotingSession(
        String id,
        String boardId,
        VotingScopeType scopeType,
        String scopeRef,
        VotingSessionState state,
        String createdByUserId,
        VotingRules rules,
        Instant createdAt,
        Instant updatedAt,
        Instant openedAt,
        Instant closedAt,
        Instant revealedAt
) {
}
