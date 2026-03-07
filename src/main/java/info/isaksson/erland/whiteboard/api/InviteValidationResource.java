package info.isaksson.erland.whiteboard.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import info.isaksson.erland.whiteboard.api.dto.InviteValidationResponse;
import info.isaksson.erland.whiteboard.api.dto.ValidateInviteRequest;
import info.isaksson.erland.whiteboard.security.InvitePolicy;

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
    public InviteValidationResponse validateInvite(ValidateInviteRequest req) {
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
