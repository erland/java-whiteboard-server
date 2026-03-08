package info.isaksson.erland.whiteboard.api.dto;

public record CreateVotingSessionRequest(
        String scopeType,
        String scopeRef,
        Boolean allowViewerParticipation,
        Boolean allowPublishedReaderParticipation,
        Integer maxVotesPerParticipant,
        Boolean anonymousVotes,
        Boolean showProgressDuringVoting,
        Boolean allowVoteUpdates,
        Long durationSeconds
) {
}
