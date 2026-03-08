package info.isaksson.erland.whiteboard.api.dto;

import info.isaksson.erland.whiteboard.voting.VotingSession;

public record VotingSessionResponse(
        String id,
        String boardId,
        String scopeType,
        String scopeRef,
        String state,
        String createdByUserId,
        VotingRulesResponse rules,
        String createdAt,
        String updatedAt,
        String openedAt,
        String closedAt,
        String revealedAt
) {
    public static VotingSessionResponse from(VotingSession session) {
        return new VotingSessionResponse(
                session.id(),
                session.boardId(),
                session.scopeType().storageValue(),
                session.scopeRef(),
                session.state().storageValue(),
                session.createdByUserId(),
                VotingRulesResponse.from(session.rules()),
                session.createdAt() == null ? null : session.createdAt().toString(),
                session.updatedAt() == null ? null : session.updatedAt().toString(),
                session.openedAt() == null ? null : session.openedAt().toString(),
                session.closedAt() == null ? null : session.closedAt().toString(),
                session.revealedAt() == null ? null : session.revealedAt().toString());
    }
}
