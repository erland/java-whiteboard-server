package info.isaksson.erland.whiteboard.api;

import java.util.List;

import info.isaksson.erland.whiteboard.api.dto.CreateVoteRequest;
import info.isaksson.erland.whiteboard.api.dto.CreateVotingSessionRequest;
import info.isaksson.erland.whiteboard.api.dto.VoteRecordResponse;
import info.isaksson.erland.whiteboard.api.dto.VotingResultsResponse;
import info.isaksson.erland.whiteboard.api.dto.VotingSessionResponse;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import info.isaksson.erland.whiteboard.voting.VoteRecord;
import info.isaksson.erland.whiteboard.voting.VotingResults;
import info.isaksson.erland.whiteboard.voting.VotingRules;
import info.isaksson.erland.whiteboard.voting.VotingScopeType;
import info.isaksson.erland.whiteboard.voting.VotingService;
import info.isaksson.erland.whiteboard.voting.VotingSession;
import info.isaksson.erland.whiteboard.voting.VotingWsNotifier;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/boards")
@Produces(MediaType.APPLICATION_JSON)
public class BoardVotingResource {

    @Inject VotingService votingService;
    @Inject BoardGuards boardGuards;
    @Inject SecurityIdentity identity;
    @Inject FeatureSupport featureSupport;
    @Inject VotingWsNotifier votingWsNotifier;

    @POST
    @Path("/{boardId}/voting-sessions")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createSession(@PathParam("boardId") String boardId, CreateVotingSessionRequest req) {
        featureSupport.requireVotingEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireOwner(boardId, userId);
        try {
            VotingSession created = votingService.createDraftSession(
                    boardId,
                    parseScopeType(req == null ? null : req.scopeType()),
                    req == null ? null : req.scopeRef(),
                    userId,
                    parseRules(req));
            return Response.status(Response.Status.CREATED).entity(VotingSessionResponse.from(created)).build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @GET
    @Path("/{boardId}/voting-sessions")
    public List<VotingSessionResponse> listSessions(@PathParam("boardId") String boardId) {
        featureSupport.requireVotingEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireReadableAccess(boardId, userId);
        return votingService.listSessionsForBoard(boardId).stream().map(VotingSessionResponse::from).toList();
    }

    @GET
    @Path("/{boardId}/voting-sessions/{sessionId}")
    public VotingSessionResponse getSession(@PathParam("boardId") String boardId, @PathParam("sessionId") String sessionId) {
        featureSupport.requireVotingEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireReadableAccess(boardId, userId);
        return VotingSessionResponse.from(requireSession(boardId, sessionId));
    }

    @POST
    @Path("/{boardId}/voting-sessions/{sessionId}/open")
    public Response openSession(@PathParam("boardId") String boardId, @PathParam("sessionId") String sessionId) {
        return transition(boardId, sessionId, "open");
    }

    @POST
    @Path("/{boardId}/voting-sessions/{sessionId}/close")
    public Response closeSession(@PathParam("boardId") String boardId, @PathParam("sessionId") String sessionId) {
        return transition(boardId, sessionId, "close");
    }

    @POST
    @Path("/{boardId}/voting-sessions/{sessionId}/reveal")
    public Response revealSession(@PathParam("boardId") String boardId, @PathParam("sessionId") String sessionId) {
        return transition(boardId, sessionId, "reveal");
    }

    @POST
    @Path("/{boardId}/voting-sessions/{sessionId}/cancel")
    public Response cancelSession(@PathParam("boardId") String boardId, @PathParam("sessionId") String sessionId) {
        return transition(boardId, sessionId, "cancel");
    }

    @POST
    @Path("/{boardId}/voting-sessions/{sessionId}/votes")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response castVote(@PathParam("boardId") String boardId,
                             @PathParam("sessionId") String sessionId,
                             CreateVoteRequest req) {
        featureSupport.requireVotingEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        BoardAccessService.Access access = boardGuards.requireReadableAccess(boardId, userId);
        VotingSession session = requireSession(boardId, sessionId);
        try {
            VoteRecord vote = votingService.castVote(
                    session.id(),
                    userId,
                    access.role(),
                    access.viaPublication(),
                    req == null ? null : req.targetRef(),
                    req == null || req.voteValue() == null ? 0 : req.voteValue());
            votingWsNotifier.votesUpdated(session, votingService.getResults(session.id()), userId);
            return Response.status(Response.Status.CREATED).entity(VoteRecordResponse.from(vote)).build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{boardId}/voting-sessions/{sessionId}/votes")
    public Response removeVote(@PathParam("boardId") String boardId,
                               @PathParam("sessionId") String sessionId,
                               @QueryParam("targetRef") String targetRef) {
        featureSupport.requireVotingEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireReadableAccess(boardId, userId);
        VotingSession session = requireSession(boardId, sessionId);
        try {
            boolean removed = votingService.removeVote(session.id(), userId, targetRef, false);
            if (removed) {
                votingWsNotifier.votesUpdated(session, votingService.getResults(session.id()), userId);
                return Response.noContent().build();
            }
            throw new NotFoundException();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @GET
    @Path("/{boardId}/voting-sessions/{sessionId}/results")
    public VotingResultsResponse getResults(@PathParam("boardId") String boardId, @PathParam("sessionId") String sessionId) {
        featureSupport.requireVotingEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireReadableAccess(boardId, userId);
        requireSession(boardId, sessionId);
        VotingResults results = votingService.getResults(sessionId);
        return VotingResultsResponse.from(results);
    }

    private Response transition(String boardId, String sessionId, String action) {
        featureSupport.requireVotingEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireOwner(boardId, userId);
        requireSession(boardId, sessionId);
        try {
            VotingSession updated = switch (action) {
                case "open" -> votingService.openSession(sessionId);
                case "close" -> votingService.closeSession(sessionId);
                case "reveal" -> votingService.revealSession(sessionId);
                case "cancel" -> votingService.cancelSession(sessionId);
                default -> throw new IllegalArgumentException("Unsupported transition");
            };
            if ("open".equals(action)) {
                votingWsNotifier.sessionOpened(updated);
            } else if ("close".equals(action)) {
                votingWsNotifier.sessionClosed(updated, votingService.getResults(updated.id()));
            } else if ("reveal".equals(action)) {
                votingWsNotifier.resultsRevealed(updated, votingService.getResults(updated.id()));
            }
            return Response.ok(VotingSessionResponse.from(updated)).build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    private VotingSession requireSession(String boardId, String sessionId) {
        VotingSession session = votingService.findSession(sessionId).orElseThrow(NotFoundException::new);
        if (!boardId.equals(session.boardId())) {
            throw new NotFoundException();
        }
        return session;
    }

    private static VotingScopeType parseScopeType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return VotingScopeType.BOARD;
        }
        try {
            return VotingScopeType.fromStorageValue(rawValue.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Field 'scopeType' has an invalid value.");
        }
    }

    private static VotingRules parseRules(CreateVotingSessionRequest req) {
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

    private static Response validationError(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError("VALIDATION_ERROR", message))
                .build();
    }
}
