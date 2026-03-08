package info.isaksson.erland.whiteboard.api;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
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
import info.isaksson.erland.whiteboard.security.Authz;
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
    InviteApplicationService inviteApplicationService;

    @Inject
    SecurityIdentity identity;

    @Inject
    ApiRequestSupport apiRequestSupport;

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
        try {
            InviteCreatedResponse created = inviteApplicationService.createInvite(boardId, Authz.userId(identity), req);
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (IllegalArgumentException e) {
            return apiRequestSupport.validationError(e.getMessage());
        }
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
        return inviteApplicationService.listInvites(boardId, Authz.userId(identity));
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
        inviteApplicationService.revokeInvite(boardId, inviteId, Authz.userId(identity));
        return Response.noContent().build();
    }
}
