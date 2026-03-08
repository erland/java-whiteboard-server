package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.dto.ActivateAssetRequest;
import info.isaksson.erland.whiteboard.api.dto.AssetFailureRequest;
import info.isaksson.erland.whiteboard.api.dto.AssetResponse;
import info.isaksson.erland.whiteboard.api.dto.CreateAssetRequest;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.assets.Asset;
import info.isaksson.erland.whiteboard.assets.AssetService;
import info.isaksson.erland.whiteboard.publication.Publication;
import info.isaksson.erland.whiteboard.publication.PublicationPolicy;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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

import java.util.List;

@Tag(name = "Assets")
@Path("/api/boards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BoardAssetsResource {

    @Inject
    AssetService assetService;

    @Inject
    BoardGuards boardGuards;

    @Inject
    PublicationPolicy publicationPolicy;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/{boardId}/assets")
    @Operation(summary = "List board assets", description = "Lists durable asset metadata for a board. Authenticated members may list assets they can use. Anonymous publication readers may list assets when a valid publication token is supplied.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Assets returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = AssetResponse.class))),
            @APIResponse(responseCode = "404", description = "Board not found or assets are not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public List<AssetResponse> listAssets(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Optional publication token for anonymous publication access.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("publicationToken") String publicationToken) {
        Publication publication = resolveReadablePublication(boardId, publicationToken);
        if (!identity.isAnonymous()) {
            String userId = Authz.userId(identity);
            boardGuards.requireAssetUseAccess(boardId, userId, publication != null);
        } else if (publication == null) {
            throw new NotFoundException();
        }
        return assetService.listForBoard(boardId).stream()
                .map(AssetResponse::from)
                .toList();
    }

    @POST
    @Path("/{boardId}/assets")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create board asset metadata", description = "Creates durable metadata for a board-scoped asset. This step does not upload binary content; it only registers the asset metadata and initial pending state.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Asset metadata created.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AssetResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid asset request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response createAsset(@PathParam("boardId") String boardId,
                                @RequestBody(required = true, description = "Asset metadata creation request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateAssetRequest.class)))
                                CreateAssetRequest req) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireAssetManageAccess(boardId, userId);
        try {
            Asset created = assetService.createBoardAssetMetadata(
                    boardId,
                    req == null ? null : req.logicalName(),
                    req == null ? null : req.contentType(),
                    req == null || req.sizeBytes() == null ? -1L : req.sizeBytes(),
                    userId,
                    req == null ? null : req.integrityHash(),
                    req == null ? null : req.versionTag());
            return Response.status(Response.Status.CREATED)
                    .entity(AssetResponse.from(created))
                    .build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @POST
    @Path("/{boardId}/assets/{assetId}/activate")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Mark asset active", description = "Marks an existing asset active after its associated upload or validation workflow has completed.")
    public Response activateAsset(@PathParam("boardId") String boardId,
                                  @PathParam("assetId") String assetId,
                                  ActivateAssetRequest req) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireAssetManageAccess(boardId, userId);
        requireAssetForBoard(boardId, assetId);
        try {
            return assetService.markActive(assetId, req == null ? null : req.versionTag())
                    .map(AssetResponse::from)
                    .map(Response::ok)
                    .orElseThrow(NotFoundException::new)
                    .build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @POST
    @Path("/{boardId}/assets/{assetId}/fail")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Mark asset failed", description = "Marks an existing asset as failed and stores a required failure reason.")
    public Response markFailed(@PathParam("boardId") String boardId,
                               @PathParam("assetId") String assetId,
                               AssetFailureRequest req) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireAssetManageAccess(boardId, userId);
        requireAssetForBoard(boardId, assetId);
        try {
            return assetService.markFailed(assetId, req == null ? null : req.failureReason())
                    .map(AssetResponse::from)
                    .map(Response::ok)
                    .orElseThrow(NotFoundException::new)
                    .build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @POST
    @Path("/{boardId}/assets/{assetId}/quarantine")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Quarantine asset", description = "Marks an existing asset quarantined and stores a required quarantine reason.")
    public Response quarantine(@PathParam("boardId") String boardId,
                               @PathParam("assetId") String assetId,
                               AssetFailureRequest req) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireAssetManageAccess(boardId, userId);
        requireAssetForBoard(boardId, assetId);
        try {
            return assetService.quarantine(assetId, req == null ? null : req.failureReason())
                    .map(AssetResponse::from)
                    .map(Response::ok)
                    .orElseThrow(NotFoundException::new)
                    .build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{boardId}/assets/{assetId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete asset metadata", description = "Marks an existing board asset deleted. Binary cleanup remains the responsibility of the surrounding storage workflow.")
    public Response deleteAsset(@PathParam("boardId") String boardId,
                                @PathParam("assetId") String assetId) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireAssetManageAccess(boardId, userId);
        requireAssetForBoard(boardId, assetId);
        try {
            return assetService.delete(assetId)
                    .map(AssetResponse::from)
                    .map(Response::ok)
                    .orElseThrow(NotFoundException::new)
                    .build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    private Asset requireAssetForBoard(String boardId, String assetId) {
        Asset asset = assetService.findById(assetId).orElseThrow(NotFoundException::new);
        if (!boardId.equals(asset.boardId())) {
            throw new NotFoundException();
        }
        return asset;
    }

    private Publication resolveReadablePublication(String boardId, String publicationToken) {
        PublicationPolicy.Decision decision = publicationPolicy.validateToken(publicationToken);
        if (!decision.valid()) {
            return null;
        }
        Publication publication = decision.publication();
        if (publication == null || !boardId.equals(publication.boardId())) {
            return null;
        }
        return publication;
    }

    private static Response validationError(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError("VALIDATION_ERROR", message))
                .build();
    }
}
