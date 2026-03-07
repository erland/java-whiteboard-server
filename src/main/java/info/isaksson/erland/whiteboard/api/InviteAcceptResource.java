package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.dto.AcceptInviteRequest;
import info.isaksson.erland.whiteboard.api.dto.AcceptInviteResponse;
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
    public Response accept(AcceptInviteRequest req) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        String token = req == null ? null : req.token();
        if (token == null || token.isBlank()) {
            return Response.status(400).build();
        }

        InvitePolicy.Decision decision = invitePolicy.validateToken(token);
        if (!decision.valid() || decision.invite() == null) {
            // do not leak
            return Response.status(404).build();
        }

        boardAccess.grant(decision.boardId(), userId, decision.permission());
        invitePolicy.recordUse(decision.invite());

        return Response.ok(new AcceptInviteResponse(decision.boardId(), decision.permission())).build();
    }
}