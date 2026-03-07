package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.dto.AcceptInviteRequest;
import info.isaksson.erland.whiteboard.api.dto.AcceptInviteResponse;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.security.InvitePolicy;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "Invites")
@SecurityRequirement(name = "bearerAuth")
@Path("/api/invites/accept")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InviteAcceptResource {

    private final SecurityIdentity identity;
    private final BoardAccessService boardAccess;
    private final InvitePolicy invitePolicy;

    @Inject
    public InviteAcceptResource(SecurityIdentity identity,
                               BoardAccessService boardAccess,
                               InvitePolicy invitePolicy) {
        this.identity = identity;
        this.boardAccess = boardAccess;
        this.invitePolicy = invitePolicy;
    }

    @POST
    @Transactional
    @Operation(summary = "Accept invite", description = "Accepts a valid invite token for the authenticated user and grants the corresponding board permission.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Invite accepted.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AcceptInviteResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid invite acceptance request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Invite not found or no longer valid.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response accept(
            @RequestBody(required = true, description = "Invite acceptance request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AcceptInviteRequest.class)))
            AcceptInviteRequest req) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        String token = req == null ? null : req.token();
        if (token == null || token.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError("VALIDATION_ERROR", "Field 'token' is required."))
                    .build();
        }

        InvitePolicy.Decision decision = invitePolicy.validateToken(token);
        if (!decision.valid() || decision.invite() == null) {
            // do not leak
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiError("INVITE_NOT_FOUND", "Invite not found or no longer valid."))
                    .build();
        }

        boardAccess.grant(decision.boardId(), userId, decision.permission());
        invitePolicy.recordUse(decision.invite());

        return Response.ok(new AcceptInviteResponse(decision.boardId(), decision.permission())).build();
    }
}