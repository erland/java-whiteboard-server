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

@Tag(name = "Invites")
@SecurityRequirement(name = "bearerAuth")
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
    @Operation(summary = "Create invite", description = "Creates a viewer or editor invite for a board owned by the authenticated user.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Invite created.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = InviteCreatedResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid invite request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response createInvite(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @RequestBody(required = true, description = "Invite creation request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateInviteRequest.class)))
            CreateInviteRequest req) {
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
    @Operation(summary = "List invites", description = "Lists active and historical invites for a board owned by the authenticated user.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Board invites returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = InviteResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public List<InviteResponse> listInvites(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        boardGuards.requireOwner(boardId, userId);

        return invitesRepository.listForBoard(boardId).stream()
                .map(InviteResponse::from)
                .toList();
    }

    @DELETE
    @Path("/{boardId}/invites/{inviteId}")
    @Operation(summary = "Revoke invite", description = "Revokes an invite belonging to a board owned by the authenticated user.")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Invite revoked."),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board or invite not found.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response revokeInvite(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Invite identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("inviteId") String inviteId) {
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
