package info.isaksson.erland.whiteboard.api;

import java.util.List;

import info.isaksson.erland.whiteboard.api.dto.CreateVoteRequest;
import info.isaksson.erland.whiteboard.api.dto.CreateVotingSessionRequest;
import info.isaksson.erland.whiteboard.api.dto.VoteRecordResponse;
import info.isaksson.erland.whiteboard.api.dto.VotingResultsResponse;
import info.isaksson.erland.whiteboard.api.dto.VotingSessionResponse;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.publication.Publication;
import info.isaksson.erland.whiteboard.publication.PublicationAccessTokens;
import info.isaksson.erland.whiteboard.publication.PublicationPolicy;
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

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "Voting")
@Path("/api/boards")
@Produces(MediaType.APPLICATION_JSON)
public class BoardVotingResource {

    @Inject VotingService votingService;
    @Inject BoardGuards boardGuards;
    @Inject SecurityIdentity identity;
    @Inject FeatureSupport featureSupport;
    @Inject VotingWsNotifier votingWsNotifier;
    @Inject PublicationPolicy publicationPolicy;

    @POST
    @Path("/{boardId}/voting-sessions")
    @Consumes(MediaType.APPLICATION_JSON)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create voting session", description = "Creates a draft voting session for a board. Requires authenticated facilitation access.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Voting session created.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VotingSessionResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid voting session request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response createSession(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @RequestBody(required = true, description = "Voting session creation request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateVotingSessionRequest.class)))
            CreateVotingSessionRequest req) {
        featureSupport.requireVotingEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireFacilitationAccess(boardId, userId);
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
    @Operation(summary = "List voting sessions", description = "Lists voting sessions for a board. Authenticated members with voting observation access may list sessions. Anonymous publication readers may list sessions when a valid publication token is supplied.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voting sessions returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = VotingSessionResponse.class))),
            @APIResponse(responseCode = "404", description = "Board not found, publication token invalid, or voting sessions are not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public List<VotingSessionResponse> listSessions(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Optional publication token used for anonymous publication voting visibility.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("publicationToken") String publicationToken) {
        featureSupport.requireVotingEnabled();
        BoardAccessService.Access access = requireVoteObservationAccess(boardId, publicationToken);
        return votingService.listSessionsForBoard(boardId).stream().map(VotingSessionResponse::from).toList();
    }

    @GET
    @Path("/{boardId}/voting-sessions/{sessionId}")
    @Operation(summary = "Get voting session", description = "Returns a voting session for a board. Authenticated members with voting observation access may read it. Anonymous publication readers may read it when a valid publication token is supplied.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voting session returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VotingSessionResponse.class))),
            @APIResponse(responseCode = "404", description = "Voting session not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public VotingSessionResponse getSession(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Voting session identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("sessionId") String sessionId,
            @Parameter(description = "Optional publication token used for anonymous publication voting visibility.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("publicationToken") String publicationToken) {
        featureSupport.requireVotingEnabled();
        requireVoteObservationAccess(boardId, publicationToken);
        return VotingSessionResponse.from(requireSession(boardId, sessionId));
    }

    @POST
    @Path("/{boardId}/voting-sessions/{sessionId}/open")
    @Consumes(MediaType.WILDCARD)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Open voting session", description = "Transitions a draft voting session to open. Requires authenticated facilitation access.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voting session opened.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VotingSessionResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid voting session transition.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Voting session not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response openSession(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Voting session identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("sessionId") String sessionId) {
        return transition(boardId, sessionId, "open");
    }

    @POST
    @Path("/{boardId}/voting-sessions/{sessionId}/close")
    @Consumes(MediaType.WILDCARD)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Close voting session", description = "Transitions an open voting session to closed. Requires authenticated facilitation access.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voting session closed.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VotingSessionResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid voting session transition.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Voting session not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response closeSession(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Voting session identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("sessionId") String sessionId) {
        return transition(boardId, sessionId, "close");
    }

    @POST
    @Path("/{boardId}/voting-sessions/{sessionId}/reveal")
    @Consumes(MediaType.WILDCARD)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Reveal voting results", description = "Transitions a closed voting session to revealed. Requires authenticated facilitation access.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voting session revealed.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VotingSessionResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid voting session transition.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Voting session not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response revealSession(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Voting session identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("sessionId") String sessionId) {
        return transition(boardId, sessionId, "reveal");
    }

    @POST
    @Path("/{boardId}/voting-sessions/{sessionId}/cancel")
    @Consumes(MediaType.WILDCARD)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cancel voting session", description = "Cancels a draft, open, or closed voting session. Requires authenticated facilitation access.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voting session cancelled.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VotingSessionResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid voting session transition.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Voting session not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response cancelSession(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Voting session identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("sessionId") String sessionId) {
        return transition(boardId, sessionId, "cancel");
    }

    @POST
    @Path("/{boardId}/voting-sessions/{sessionId}/votes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Cast vote", description = "Creates or updates a participant vote for a voting session. Authenticated members with vote participation access may vote. Anonymous publication readers may vote only when a valid publication token is supplied and the session rules allow published-reader participation.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Vote stored.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VoteRecordResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid vote request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required when no publication token is supplied.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Voting session not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response castVote(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Voting session identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("sessionId") String sessionId,
            @Parameter(description = "Optional publication token used for anonymous publication voting.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("publicationToken") String publicationToken,
            @Parameter(description = "Required participant token for anonymous publication voting. The server derives a stable synthetic participant id from the publication token and participant token.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("participantToken") String participantToken,
            @RequestBody(required = true, description = "Vote submission request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateVoteRequest.class)))
            CreateVoteRequest req) {
        featureSupport.requireVotingEnabled();
        VotingSession session = requireSession(boardId, sessionId);
        try {
            VoteActor actor = requireVoteParticipant(boardId, publicationToken, participantToken);
            VoteRecord vote = votingService.castVote(
                    session.id(),
                    actor.participantId(),
                    actor.access().role(),
                    actor.access().viaPublication(),
                    req == null ? null : req.targetRef(),
                    req == null || req.voteValue() == null ? 0 : req.voteValue());
            votingWsNotifier.votesUpdated(session, votingService.getPublicResults(session.id()), actor.auditUserId());
            return Response.status(Response.Status.CREATED).entity(VoteRecordResponse.from(vote)).build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{boardId}/voting-sessions/{sessionId}/votes")
    @Operation(summary = "Remove vote", description = "Removes a participant vote for a target within a voting session. Authenticated members with vote participation access may remove their own vote. Anonymous publication readers may remove their own vote only when a valid publication token is supplied and the session rules allow published-reader participation.")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Vote removed."),
            @APIResponse(responseCode = "400", description = "Invalid remove vote request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required when no publication token is supplied.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Vote, voting session, or board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response removeVote(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Voting session identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("sessionId") String sessionId,
            @Parameter(description = "Vote target identifier to remove.", required = true, schema = @Schema(type = SchemaType.STRING)) @QueryParam("targetRef") String targetRef,
            @Parameter(description = "Optional publication token used for anonymous publication voting.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("publicationToken") String publicationToken,
            @Parameter(description = "Required participant token for anonymous publication voting.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("participantToken") String participantToken) {
        featureSupport.requireVotingEnabled();
        VotingSession session = requireSession(boardId, sessionId);
        try {
            VoteActor actor = requireVoteParticipant(boardId, publicationToken, participantToken);
            boolean removed = votingService.removeVote(session.id(), actor.participantId(), targetRef, false);
            if (removed) {
                votingWsNotifier.votesUpdated(session, votingService.getPublicResults(session.id()), actor.auditUserId());
                return Response.noContent().build();
            }
            throw new NotFoundException();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @GET
    @Path("/{boardId}/voting-sessions/{sessionId}/results")
    @Operation(summary = "Get voting results", description = "Returns a visibility-filtered projection of voting results for a session. Authenticated members with voting observation access may read results. Anonymous publication readers may read results when a valid publication token is supplied.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voting results returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VotingResultsResponse.class))),
            @APIResponse(responseCode = "404", description = "Voting session not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public VotingResultsResponse getResults(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Voting session identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("sessionId") String sessionId,
            @Parameter(description = "Optional publication token used for anonymous publication voting visibility.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("publicationToken") String publicationToken) {
        featureSupport.requireVotingEnabled();
        BoardAccessService.Access access = requireVoteObservationAccess(boardId, publicationToken);
        requireSession(boardId, sessionId);
        VotingResults results = votingService.getResults(sessionId, access);
        return VotingResultsResponse.from(results);
    }

    private Response transition(String boardId, String sessionId, String action) {
        featureSupport.requireVotingEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireFacilitationAccess(boardId, userId);
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
                votingWsNotifier.sessionOpened(updated, votingService.getPublicResults(updated.id()));
            } else if ("close".equals(action)) {
                votingWsNotifier.sessionClosed(updated, votingService.getPublicResults(updated.id()));
            } else if ("reveal".equals(action)) {
                votingWsNotifier.resultsRevealed(updated, votingService.getPublicResults(updated.id()));
            }
            return Response.ok(VotingSessionResponse.from(updated)).build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    private BoardAccessService.Access requireVoteObservationAccess(String boardId, String publicationToken) {
        Publication publication = resolveReadablePublication(boardId, publicationToken);
        if (identity != null && !identity.isAnonymous()) {
            String userId = Authz.userId(identity);
            return boardGuards.requireVoteObservationAccess(boardId, userId, publication != null);
        }
        if (publication == null) {
            throw new NotFoundException();
        }
        return boardGuards.requirePublicationReadAccess(boardId, null, true);
    }

    private VoteActor requireVoteParticipant(String boardId, String publicationToken, String participantToken) {
        Publication publication = resolveReadablePublication(boardId, publicationToken);
        if (identity != null && !identity.isAnonymous()) {
            String userId = Authz.userId(identity);
            BoardAccessService.Access access = boardGuards.requireVoteParticipationAccess(boardId, userId, publication != null);
            return new VoteActor(access, userId, userId);
        }
        if (publication == null) {
            throw new NotFoundException();
        }
        BoardAccessService.Access access = boardGuards.requirePublicationReadAccess(boardId, null, true);
        String normalizedParticipantToken = normalizeParticipantToken(participantToken);
        String participantId = anonymousPublicationParticipantId(publicationToken, normalizedParticipantToken);
        return new VoteActor(access, participantId, "publication:" + publication.id());
    }

    private Publication resolveReadablePublication(String boardId, String publicationToken) {
        PublicationPolicy.Decision decision = publicationPolicy.validateToken(publicationToken);
        if (!decision.valid() || decision.publication() == null) {
            return null;
        }
        Publication publication = decision.publication();
        if (!boardId.equals(publication.boardId())) {
            return null;
        }
        return publication;
    }

    private static String normalizeParticipantToken(String participantToken) {
        if (participantToken == null || participantToken.isBlank()) {
            throw new IllegalArgumentException("Field 'participantToken' is required for anonymous publication voting.");
        }
        return participantToken.trim();
    }

    private static String anonymousPublicationParticipantId(String publicationToken, String participantToken) {
        return "publication-participant:" + PublicationAccessTokens.sha256Hex(publicationToken.trim() + ":" + participantToken);
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

    private record VoteActor(BoardAccessService.Access access, String participantId, String auditUserId) {
    }
}
