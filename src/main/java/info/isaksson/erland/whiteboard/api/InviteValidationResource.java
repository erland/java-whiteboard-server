package info.isaksson.erland.whiteboard.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import info.isaksson.erland.whiteboard.api.dto.InviteValidationResponse;
import info.isaksson.erland.whiteboard.api.dto.ValidateInviteRequest;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.security.InvitePolicy;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "Invites")
@Path("/api/invites")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InviteValidationResource {

    @Inject
    InvitePolicy invitePolicy;

    /**
     * Public endpoint used by the client to validate an invite token before attempting to join.
     * Returns 200 with valid=false for invalid tokens (does not leak board existence).
     */
    @POST
    @Path("/validate")
    @Operation(summary = "Validate invite", description = "Validates an invite token without requiring authentication and without leaking whether a board exists.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Invite validation result returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = InviteValidationResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid validation request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public InviteValidationResponse validateInvite(
            @RequestBody(required = true, description = "Invite token validation request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ValidateInviteRequest.class)))
            ValidateInviteRequest req) {
        InvitePolicy.Decision decision = invitePolicy.validateToken(req == null ? null : req.token());
        if (decision.invite() == null) {
            return InviteValidationResponse.notFound();
        }
        return new InviteValidationResponse(
                decision.valid(),
                decision.reason(),
                decision.boardId(),
                decision.permission(),
                decision.invite().expiresAt()
        );
    }
}
