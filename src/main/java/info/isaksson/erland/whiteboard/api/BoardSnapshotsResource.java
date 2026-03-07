package info.isaksson.erland.whiteboard.api;

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

import info.isaksson.erland.whiteboard.api.dto.CreateSnapshotRequest;
import info.isaksson.erland.whiteboard.api.dto.SnapshotResponse;
import info.isaksson.erland.whiteboard.api.dto.SnapshotVersionsResponse;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.domain.BoardSnapshot;
import info.isaksson.erland.whiteboard.persistence.SnapshotsRepository;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import info.isaksson.erland.whiteboard.security.Authz;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;

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
    public Response create(@PathParam("boardId") String boardId, CreateSnapshotRequest req) {
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
    public SnapshotResponse latest(@PathParam("boardId") String boardId) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        boardGuards.requireReadableAccess(boardId, userId);

        BoardSnapshot latest = snapshotsRepository.getLatest(boardId).orElseThrow(NotFoundException::new);
        return SnapshotResponse.from(latest, mapper);
    }

    @GET
    @Path("/{boardId}/snapshots/{version}")
    public SnapshotResponse get(@PathParam("boardId") String boardId, @PathParam("version") long version) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        boardGuards.requireReadableAccess(boardId, userId);

        BoardSnapshot s = snapshotsRepository.get(boardId, version).orElseThrow(NotFoundException::new);
        return SnapshotResponse.from(s, mapper);
    }

    @GET
    @Path("/{boardId}/snapshots")
    public SnapshotVersionsResponse versions(@PathParam("boardId") String boardId) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        boardGuards.requireReadableAccess(boardId, userId);

        List<Long> versions = snapshotsRepository.listVersions(boardId);
        return new SnapshotVersionsResponse(versions);
    }
}
