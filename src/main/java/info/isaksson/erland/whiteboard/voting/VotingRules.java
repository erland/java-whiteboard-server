package info.isaksson.erland.whiteboard.voting;

public record VotingRules(
        boolean allowViewerParticipation,
        boolean allowPublishedReaderParticipation,
        int maxVotesPerParticipant,
        boolean anonymousVotes,
        boolean showProgressDuringVoting,
        boolean allowVoteUpdates,
        Long durationSeconds
) {
    public VotingRules {
        if (maxVotesPerParticipant <= 0) {
            throw new IllegalArgumentException("maxVotesPerParticipant must be greater than zero");
        }
        if (durationSeconds != null && durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be greater than zero when provided");
        }
    }

    public static VotingRules defaults() {
        return new VotingRules(true, false, 1, true, false, false, null);
    }
}
