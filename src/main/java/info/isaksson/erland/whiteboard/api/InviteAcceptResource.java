package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.dto.AcceptInviteRequest;
import info.isaksson.erland.whiteboard.api.dto.AcceptInviteResponse;
import info.isaksson.erland.whiteboard.domain.Invite;
import info.isaksson.erland.whiteboard.persistence.InvitesRepository;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.security.InviteTokens;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;


@Path("/api/invites/accept")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InviteAcceptResource {

    private final SecurityIdentity identity;
    private final InvitesRepository invitesRepository;
    private final BoardAccessService boardAccess;

    @Inject
    public InviteAcceptResource(SecurityIdentity identity,
                               InvitesRepository invitesRepository,
                               BoardAccessService boardAccess) {
        this.identity = identity;
        this.invitesRepository = invitesRepository;
        this.boardAccess = boardAccess;
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

        String tokenHash = InviteTokens.sha256Hex(token);
        Invite invite = invitesRepository.findByTokenHash(tokenHash).orElse(null);
        if (invite == null) {
            // do not leak
            return Response.status(404).build();
        }

        if (invite.revokedAt() != null) return Response.status(404).build();
        if (invite.expiresAt() != null && invite.expiresAt().isBefore(Instant.now())) return Response.status(404).build();
        if (invite.maxUses() != null && invite.uses() >= invite.maxUses()) return Response.status(404).build();

        // grant membership
        String role = switch (invite.permission()) {
            case "edit", "editor" -> BoardAccessService.ROLE_EDITOR;
            default -> BoardAccessService.ROLE_VIEWER;
        };
        boardAccess.grant(invite.boardId(), userId, role);
        invitesRepository.incrementUses(invite.id());

        return Response.ok(new AcceptInviteResponse(invite.boardId(), role)).build();
    }
}