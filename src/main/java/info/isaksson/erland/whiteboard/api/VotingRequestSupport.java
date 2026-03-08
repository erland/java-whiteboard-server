package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.dto.CreateVotingSessionRequest;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.voting.VotingRules;
import info.isaksson.erland.whiteboard.voting.VotingScopeType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class VotingRequestSupport {

    public VotingScopeType parseScopeType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return VotingScopeType.BOARD;
        }
        try {
            return VotingScopeType.fromStorageValue(rawValue.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Field 'scopeType' has an invalid value.");
        }
    }

    public VotingRules parseRules(CreateVotingSessionRequest req) {
        VotingRules defaults = VotingRules.defaults();
        try {
            return new VotingRules(
                    req != null && req.allowViewerParticipation() != null ? req.allowViewerParticipation() : defaults.allowViewerParticipation(),
                    req != null && req.allowPublishedReaderParticipation() != null ? req.allowPublishedReaderParticipation() : defaults.allowPublishedReaderParticipation(),
                    req != null && req.maxVotesPerParticipant() != null ? req.maxVotesPerParticipant() : defaults.maxVotesPerParticipant(),
                    req != null && req.anonymousVotes() != null ? req.anonymousVotes() : defaults.anonymousVotes(),
                    req != null && req.showProgressDuringVoting() != null ? req.showProgressDuringVoting() : defaults.showProgressDuringVoting(),
                    req != null && req.allowVoteUpdates() != null ? req.allowVoteUpdates() : defaults.allowVoteUpdates(),
                    req == null ? defaults.durationSeconds() : req.durationSeconds());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public Response validationError(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError("VALIDATION_ERROR", message))
                .build();
    }
}
