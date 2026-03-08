package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.dto.ActivateAssetRequest;
import info.isaksson.erland.whiteboard.api.dto.AssetFailureRequest;
import info.isaksson.erland.whiteboard.api.dto.AssetResponse;
import info.isaksson.erland.whiteboard.api.dto.CreateAssetRequest;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
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
    AssetApplicationService assetApplicationService;

    @Inject
    AssetRequestSupport requestSupport;

    @Inject
    FeatureSupport featureSupport;

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
        featureSupport.requireAssetsEnabled();
        return assetApplicationService.listAssets(boardId, publicationToken).stream()
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
    public Response createAsset(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @RequestBody(required = true, description = "Asset metadata creation request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateAssetRequest.class)))
            CreateAssetRequest req) {
        featureSupport.requireAssetsEnabled();
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(AssetResponse.from(assetApplicationService.createAsset(boardId, req)))
                    .build();
        } catch (IllegalArgumentException e) {
            return requestSupport.validationError(e.getMessage());
        }
    }

    @POST
    @Path("/{boardId}/assets/{assetId}/activate")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Mark asset active", description = "Marks an existing asset active after its associated upload or validation workflow has completed.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Asset marked active.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AssetResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid asset activation request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Asset not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response activateAsset(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Asset identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("assetId") String assetId,
            @RequestBody(required = true, description = "Asset activation request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActivateAssetRequest.class)))
            ActivateAssetRequest req) {
        featureSupport.requireAssetsEnabled();
        try {
            return Response.ok(AssetResponse.from(assetApplicationService.activateAsset(boardId, assetId, req))).build();
        } catch (IllegalArgumentException e) {
            return requestSupport.validationError(e.getMessage());
        }
    }

    @POST
    @Path("/{boardId}/assets/{assetId}/fail")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Mark asset failed", description = "Marks an existing asset as failed and stores a required failure reason.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Asset marked failed.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AssetResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid asset failure request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Asset not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response markFailed(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Asset identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("assetId") String assetId,
            @RequestBody(required = true, description = "Asset failure request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AssetFailureRequest.class)))
            AssetFailureRequest req) {
        featureSupport.requireAssetsEnabled();
        try {
            return Response.ok(AssetResponse.from(assetApplicationService.failAsset(boardId, assetId, req))).build();
        } catch (IllegalArgumentException e) {
            return requestSupport.validationError(e.getMessage());
        }
    }

    @POST
    @Path("/{boardId}/assets/{assetId}/quarantine")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Quarantine asset", description = "Marks an existing asset quarantined and stores a required quarantine reason.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Asset quarantined.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AssetResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid asset quarantine request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Asset not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response quarantine(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Asset identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("assetId") String assetId,
            @RequestBody(required = true, description = "Asset quarantine request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AssetFailureRequest.class)))
            AssetFailureRequest req) {
        featureSupport.requireAssetsEnabled();
        try {
            return Response.ok(AssetResponse.from(assetApplicationService.quarantineAsset(boardId, assetId, req))).build();
        } catch (IllegalArgumentException e) {
            return requestSupport.validationError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{boardId}/assets/{assetId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete asset metadata", description = "Marks an existing board asset deleted. Binary cleanup remains the responsibility of the surrounding storage workflow.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Asset metadata deleted.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AssetResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Asset not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response deleteAsset(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Asset identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("assetId") String assetId) {
        featureSupport.requireAssetsEnabled();
        try {
            return Response.ok(AssetResponse.from(assetApplicationService.deleteAsset(boardId, assetId))).build();
        } catch (IllegalArgumentException e) {
            return requestSupport.validationError(e.getMessage());
        }
    }
}
