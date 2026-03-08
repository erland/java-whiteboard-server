package info.isaksson.erland.whiteboard.api;

import java.util.List;

import info.isaksson.erland.whiteboard.api.dto.CreateVoteRequest;
import info.isaksson.erland.whiteboard.api.dto.CreateVotingSessionRequest;
import info.isaksson.erland.whiteboard.api.dto.VoteRecordResponse;
import info.isaksson.erland.whiteboard.api.dto.VotingResultsResponse;
import info.isaksson.erland.whiteboard.api.dto.VotingSessionResponse;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.voting.VoteRecord;
import info.isaksson.erland.whiteboard.voting.VotingResults;
import info.isaksson.erland.whiteboard.voting.VotingSession;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
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

    @Inject VotingSessionApplicationService votingSessionApplicationService;
    @Inject VoteApplicationService voteApplicationService;
    @Inject FeatureSupport featureSupport;
    @Inject VotingRequestSupport votingRequestSupport;

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
        try {
            VotingSession created = votingSessionApplicationService.createDraftSession(boardId, req);
            return Response.status(Response.Status.CREATED).entity(VotingSessionResponse.from(created)).build();
        } catch (IllegalArgumentException e) {
            return votingRequestSupport.validationError(e.getMessage());
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
        return votingSessionApplicationService.listSessions(boardId, publicationToken).stream().map(VotingSessionResponse::from).toList();
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
        return VotingSessionResponse.from(votingSessionApplicationService.getSession(boardId, sessionId, publicationToken));
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
    @Operation(summary = "Close voting session", description = "Closes an open voting session. Requires authenticated facilitation access.")
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
    @Operation(summary = "Reveal voting results", description = "Reveals results for a closed voting session. Requires authenticated facilitation access.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Voting results revealed.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VotingSessionResponse.class))),
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
    @Operation(summary = "Cancel voting session", description = "Cancels a draft or open voting session. Requires authenticated facilitation access.")
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
    @Operation(summary = "Cast or update vote", description = "Casts or updates a vote for the current participant. Authenticated members may vote according to board capabilities and session rules. Anonymous publication readers may vote when the session allows published reader participation and both publicationToken and participantToken are supplied.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Vote recorded.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VoteRecordResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid vote request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Voting session not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response castVote(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Voting session identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("sessionId") String sessionId,
            @Parameter(description = "Optional publication token used for anonymous publication voting.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("publicationToken") String publicationToken,
            @Parameter(description = "Required participant token for anonymous publication voting.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("participantToken") String participantToken,
            @RequestBody(required = true, description = "Vote payload.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateVoteRequest.class)))
            CreateVoteRequest req) {
        featureSupport.requireVotingEnabled();
        try {
            VoteRecord vote = voteApplicationService.castVote(boardId, sessionId, publicationToken, participantToken, req);
            return Response.status(Response.Status.CREATED).entity(VoteRecordResponse.from(vote)).build();
        } catch (IllegalArgumentException e) {
            return votingRequestSupport.validationError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{boardId}/voting-sessions/{sessionId}/votes")
    @Operation(summary = "Remove vote", description = "Removes a previously recorded vote for the current participant and target. Authenticated members may remove their own vote when updates are allowed. Anonymous publication readers may remove their own vote when both publicationToken and participantToken are supplied.")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Vote removed."),
            @APIResponse(responseCode = "400", description = "Invalid vote removal request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Vote or voting session not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response removeVote(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Voting session identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("sessionId") String sessionId,
            @Parameter(description = "Vote target identifier to remove.", required = true, schema = @Schema(type = SchemaType.STRING)) @QueryParam("targetRef") String targetRef,
            @Parameter(description = "Optional publication token used for anonymous publication voting.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("publicationToken") String publicationToken,
            @Parameter(description = "Required participant token for anonymous publication voting.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("participantToken") String participantToken) {
        featureSupport.requireVotingEnabled();
        try {
            voteApplicationService.removeVote(boardId, sessionId, targetRef, publicationToken, participantToken);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return votingRequestSupport.validationError(e.getMessage());
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
        VotingResults results = voteApplicationService.getResults(boardId, sessionId, publicationToken);
        return VotingResultsResponse.from(results);
    }

    private Response transition(String boardId, String sessionId, String action) {
        featureSupport.requireVotingEnabled();
        try {
            VotingSession updated = votingSessionApplicationService.transition(boardId, sessionId, action);
            return Response.ok(VotingSessionResponse.from(updated)).build();
        } catch (IllegalArgumentException e) {
            return votingRequestSupport.validationError(e.getMessage());
        }
    }
}
