package info.isaksson.erland.whiteboard.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import info.isaksson.erland.whiteboard.domain.BoardSnapshot;
import info.isaksson.erland.whiteboard.persistence.SnapshotsRepository;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardGuards;
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
    BoardGuards boardGuards;

    @Inject
    SnapshotsRepository snapshotsRepository;

    @Inject
    SecurityIdentity identity;

    @Inject
    ObjectMapper mapper;

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
        String userId = Authz.userId(identity);

        boardGuards.requireWritableAccess(boardId, userId);

        JsonNode snapshotNode = req == null ? null : req.snapshot();
        if (snapshotNode == null || snapshotNode.isNull()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError("VALIDATION_ERROR", "Field 'snapshot' is required."))
                    .build();
        }

        String snapshotJson;
        try {
            snapshotJson = mapper.writeValueAsString(snapshotNode);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError("VALIDATION_ERROR", "Field 'snapshot' must be valid JSON."))
                    .build();
        }

        int sizeBytes = snapshotJson.getBytes(StandardCharsets.UTF_8).length;
        if (sizeBytes > maxSnapshotBytes) {
            return Response.status(413)
                    .entity(new ApiError(
                            "PAYLOAD_TOO_LARGE",
                            "Snapshot exceeds max size of " + maxSnapshotBytes + " bytes."))
                    .build();
        }

        BoardSnapshot created = snapshotsRepository.create(boardId, userId, snapshotJson);
        return Response.status(Response.Status.CREATED)
                .entity(SnapshotResponse.from(created, mapper))
                .build();
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
        String userId = Authz.userId(identity);

        boardGuards.requireReadableAccess(boardId, userId);

        BoardSnapshot latest = snapshotsRepository.getLatest(boardId).orElseThrow(NotFoundException::new);
        return SnapshotResponse.from(latest, mapper);
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
        String userId = Authz.userId(identity);

        boardGuards.requireReadableAccess(boardId, userId);

        BoardSnapshot s = snapshotsRepository.get(boardId, version).orElseThrow(NotFoundException::new);
        return SnapshotResponse.from(s, mapper);
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
        String userId = Authz.userId(identity);

        boardGuards.requireReadableAccess(boardId, userId);

        List<Long> versions = snapshotsRepository.listVersions(boardId);
        return new SnapshotVersionsResponse(versions);
    }
}
