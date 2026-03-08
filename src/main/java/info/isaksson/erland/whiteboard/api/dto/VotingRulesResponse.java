package info.isaksson.erland.whiteboard.api.dto;

import info.isaksson.erland.whiteboard.voting.VotingRules;

public record VotingRulesResponse(
        boolean allowViewerParticipation,
        boolean allowPublishedReaderParticipation,
        int maxVotesPerParticipant,
        boolean anonymousVotes,
        boolean showProgressDuringVoting,
        boolean allowVoteUpdates,
        Long durationSeconds
) {
    public static VotingRulesResponse from(VotingRules rules) {
        return new VotingRulesResponse(
                rules.allowViewerParticipation(),
                rules.allowPublishedReaderParticipation(),
                rules.maxVotesPerParticipant(),
                rules.anonymousVotes(),
                rules.showProgressDuringVoting(),
                rules.allowVoteUpdates(),
                rules.durationSeconds());
    }
}
