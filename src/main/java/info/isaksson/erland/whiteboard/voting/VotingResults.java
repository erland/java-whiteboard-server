package info.isaksson.erland.whiteboard.voting;

import java.util.List;
import java.util.Map;

public record VotingResults(
        VotingSession session,
        Map<String, Integer> totalsByTarget,
        List<VoteRecord> visibleVotes,
        boolean identitiesHidden,
        boolean progressHidden
) {
}
