package info.isaksson.erland.whiteboard.api.dto;

import info.isaksson.erland.whiteboard.voting.VoteRecord;

public record VoteRecordResponse(
        String id,
        String sessionId,
        String participantId,
        String targetRef,
        int voteValue,
        String createdAt,
        String updatedAt
) {
    public static VoteRecordResponse from(VoteRecord vote) {
        return new VoteRecordResponse(
                vote.id(),
                vote.sessionId(),
                vote.participantId(),
                vote.targetRef(),
                vote.voteValue(),
                vote.createdAt() == null ? null : vote.createdAt().toString(),
                vote.updatedAt() == null ? null : vote.updatedAt().toString());
    }
}
