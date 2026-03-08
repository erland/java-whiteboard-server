package info.isaksson.erland.whiteboard.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
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

import info.isaksson.erland.whiteboard.api.dto.CreateSnapshotRequest;
import info.isaksson.erland.whiteboard.api.dto.SnapshotResponse;
import info.isaksson.erland.whiteboard.api.dto.SnapshotVersionsResponse;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.security.Authz;
import io.quarkus.security.identity.SecurityIdentity;

@Tag(name = "Snapshots")
@SecurityRequirement(name = "bearerAuth")
@Path("/api/boards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BoardSnapshotsResource {

    @ConfigProperty(name = "whiteboard.limits.snapshots.max-bytes", defaultValue = "1048576")
    long maxSnapshotBytes;

    @Inject
    SnapshotApplicationService snapshotApplicationService;

    @Inject
    SecurityIdentity identity;

    @POST
    @Path("/{boardId}/snapshots")
    @Operation(summary = "Create snapshot", description = "Creates a new versioned snapshot for a board the authenticated user can write to.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Snapshot created.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SnapshotResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid snapshot payload.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "413", description = "Snapshot exceeds configured size limit.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response create(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @RequestBody(required = true, description = "Snapshot payload stored as opaque JSON by the backend.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateSnapshotRequest.class)))
            CreateSnapshotRequest req) {
        Authz.requireUserOrAdmin(identity);
        try {
            SnapshotResponse created = snapshotApplicationService.create(boardId, Authz.userId(identity), req, maxSnapshotBytes);
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (SnapshotApplicationService.SnapshotValidationException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError("VALIDATION_ERROR", e.getMessage()))
                    .build();
        } catch (SnapshotApplicationService.SnapshotTooLargeException e) {
            return Response.status(413)
                    .entity(new ApiError("PAYLOAD_TOO_LARGE", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{boardId}/snapshots/latest")
    @Operation(summary = "Get latest snapshot", description = "Returns the latest snapshot version for a board the authenticated user can read.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Latest snapshot returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SnapshotResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found, not accessible, or no snapshot exists.")
    })
    public SnapshotResponse latest(@Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId) {
        Authz.requireUserOrAdmin(identity);
        return snapshotApplicationService.latest(boardId, Authz.userId(identity));
    }

    @GET
    @Path("/{boardId}/snapshots/{version}")
    @Operation(summary = "Get snapshot by version", description = "Returns a specific snapshot version for a board the authenticated user can read.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Snapshot returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SnapshotResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found, not accessible, or version does not exist.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public SnapshotResponse get(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Snapshot version number.", required = true, schema = @Schema(type = SchemaType.INTEGER, format = "int64")) @PathParam("version") long version) {
        Authz.requireUserOrAdmin(identity);
        return snapshotApplicationService.get(boardId, version, Authz.userId(identity));
    }

    @GET
    @Path("/{boardId}/snapshots")
    @Operation(summary = "List snapshot versions", description = "Returns the list of available snapshot version numbers for a board the authenticated user can read.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Snapshot versions returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SnapshotVersionsResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public SnapshotVersionsResponse versions(@Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId) {
        Authz.requireUserOrAdmin(identity);
        return snapshotApplicationService.versions(boardId, Authz.userId(identity));
    }
}
