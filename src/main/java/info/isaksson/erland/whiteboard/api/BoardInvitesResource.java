package info.isaksson.erland.whiteboard.api;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import info.isaksson.erland.whiteboard.api.dto.CreateInviteRequest;
import info.isaksson.erland.whiteboard.api.dto.InviteCreatedResponse;
import info.isaksson.erland.whiteboard.api.dto.InviteResponse;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.domain.Invite;
import info.isaksson.erland.whiteboard.persistence.InvitesRepository;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import info.isaksson.erland.whiteboard.security.InviteTokens;
import io.quarkus.security.identity.SecurityIdentity;

@Path("/api/boards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BoardInvitesResource {


    @Inject
    InvitesRepository invitesRepository;

    @Inject
    SecurityIdentity identity;

    @Inject
    BoardGuards boardGuards;

    @POST
    @Path("/{boardId}/invites")
    public Response createInvite(@PathParam("boardId") String boardId, CreateInviteRequest req) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        boardGuards.requireOwner(boardId, userId);

        String permission = req == null ? null : req.permission();
        if (permission == null || permission.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError("VALIDATION_ERROR", "Field 'permission' is required."))
                    .build();
        }
        permission = permission.trim();
        if (!permission.equals("viewer") && !permission.equals("editor")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError("VALIDATION_ERROR", "Field 'permission' must be 'viewer' or 'editor'."))
                    .build();
        }

        Instant expiresAt = null;
        if (req != null && req.expiresAt() != null && !req.expiresAt().isBlank()) {
            try {
                expiresAt = Instant.parse(req.expiresAt().trim());
            } catch (DateTimeParseException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiError("VALIDATION_ERROR", "Field 'expiresAt' must be an ISO-8601 instant (e.g. 2026-01-01T00:00:00Z)."))
                        .build();
            }
        }

        Integer maxUses = req == null ? null : req.maxUses();
        if (maxUses != null && maxUses <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError("VALIDATION_ERROR", "Field 'maxUses' must be a positive integer."))
                    .build();
        }

        String token = InviteTokens.generateToken();
        String tokenHash = InviteTokens.sha256Hex(token);

        Invite created = invitesRepository.create(new Invite(
                UUID.randomUUID().toString(),
                boardId,
                tokenHash,
                permission,
                expiresAt,
                maxUses,
                0,
                null,
                null
        ));

        return Response.status(Response.Status.CREATED)
                .entity(InviteCreatedResponse.from(created, token))
                .build();
    }

    @GET
    @Path("/{boardId}/invites")
    public List<InviteResponse> listInvites(@PathParam("boardId") String boardId) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        boardGuards.requireOwner(boardId, userId);

        return invitesRepository.listForBoard(boardId).stream()
                .map(InviteResponse::from)
                .toList();
    }

    @DELETE
    @Path("/{boardId}/invites/{inviteId}")
    public Response revokeInvite(@PathParam("boardId") String boardId, @PathParam("inviteId") String inviteId) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        boardGuards.requireOwner(boardId, userId);

        Invite invite = invitesRepository.findById(inviteId).orElseThrow(NotFoundException::new);
        if (!invite.boardId().equals(boardId)) {
            throw new NotFoundException();
        }

        invitesRepository.revoke(inviteId);
        return Response.noContent().build();
    }
}
