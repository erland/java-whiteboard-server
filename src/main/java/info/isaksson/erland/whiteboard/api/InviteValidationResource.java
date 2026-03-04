package info.isaksson.erland.whiteboard.api;

import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import info.isaksson.erland.whiteboard.api.dto.InviteValidationResponse;
import info.isaksson.erland.whiteboard.api.dto.ValidateInviteRequest;
import info.isaksson.erland.whiteboard.domain.Invite;
import info.isaksson.erland.whiteboard.persistence.InvitesRepository;
import info.isaksson.erland.whiteboard.security.InviteTokens;

@Path("/api/invites")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InviteValidationResource {

    @Inject
    InvitesRepository invitesRepository;

    /**
     * Public endpoint used by the client to validate an invite token before attempting to join.
     * Returns 200 with valid=false for invalid tokens (does not leak board existence).
     */
    @POST
    @Path("/validate")
    public InviteValidationResponse validateInvite(ValidateInviteRequest req) {
        String token = req == null ? null : req.token();
        if (token == null || token.isBlank()) {
            return InviteValidationResponse.notFound();
        }

        String tokenHash = InviteTokens.sha256Hex(token.trim());
        Invite invite = invitesRepository.findByTokenHash(tokenHash).orElse(null);
        if (invite == null) {
            return InviteValidationResponse.notFound();
        }

        if (invite.revokedAt() != null) {
            return new InviteValidationResponse(false, "REVOKED", invite.boardId(), invite.permission(), invite.expiresAt());
        }

        if (invite.expiresAt() != null && invite.expiresAt().isBefore(Instant.now())) {
            return new InviteValidationResponse(false, "EXPIRED", invite.boardId(), invite.permission(), invite.expiresAt());
        }

        if (invite.maxUses() != null && invite.uses() >= invite.maxUses()) {
            return new InviteValidationResponse(false, "MAX_USES_REACHED", invite.boardId(), invite.permission(), invite.expiresAt());
        }

        return new InviteValidationResponse(true, "OK", invite.boardId(), invite.permission(), invite.expiresAt());
    }
}
