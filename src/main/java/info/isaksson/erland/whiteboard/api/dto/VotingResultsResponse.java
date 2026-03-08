package info.isaksson.erland.whiteboard.api.dto;

import java.util.List;
import java.util.Map;

import info.isaksson.erland.whiteboard.voting.VotingResults;

public record VotingResultsResponse(
        VotingSessionResponse session,
        Map<String, Integer> totalsByTarget,
        List<VoteRecordResponse> visibleVotes,
        boolean identitiesHidden,
        boolean progressHidden
) {
    public static VotingResultsResponse from(VotingResults results) {
        return new VotingResultsResponse(
                VotingSessionResponse.from(results.session()),
                results.totalsByTarget(),
                results.visibleVotes().stream().map(VoteRecordResponse::from).toList(),
                results.identitiesHidden(),
                results.progressHidden());
    }
}
