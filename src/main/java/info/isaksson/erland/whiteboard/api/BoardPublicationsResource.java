package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.FeatureSupport;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

import info.isaksson.erland.whiteboard.api.dto.CreatePublicationRequest;
import info.isaksson.erland.whiteboard.api.dto.PublicationCreatedResponse;
import info.isaksson.erland.whiteboard.api.dto.PublicationResponse;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.publication.Publication;
import info.isaksson.erland.whiteboard.publication.PublicationService;
import info.isaksson.erland.whiteboard.publication.PublicationTargetType;
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

@Tag(name = "Publications")
@SecurityRequirement(name = "bearerAuth")
@Path("/api/boards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BoardPublicationsResource {

    @Inject
    PublicationService publicationService;

    @Inject
    BoardGuards boardGuards;

    @Inject
    SecurityIdentity identity;

    @Inject
    FeatureSupport featureSupport;

    @Inject
    ApiRequestSupport apiRequestSupport;

    @POST
    @Path("/{boardId}/publications")
    @Operation(summary = "Create publication", description = "Creates a publication for a board or a specific snapshot. Only the board owner may create publications.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Publication created.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PublicationCreatedResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid publication request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response createPublication(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @RequestBody(required = true, description = "Publication creation request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreatePublicationRequest.class)))
            CreatePublicationRequest req) {
        featureSupport.requirePublicationsEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireOwner(boardId, userId);

        PublicationTargetType targetType;
        try {
            targetType = parseTargetType(req == null ? null : req.targetType());
        } catch (IllegalArgumentException e) {
            return apiRequestSupport.validationError(e.getMessage());
        }

        Instant expiresAt;
        try {
            expiresAt = parseExpiresAt(req == null ? null : req.expiresAt());
        } catch (IllegalArgumentException e) {
            return apiRequestSupport.validationError(e.getMessage());
        }

        boolean allowComments = req != null && Boolean.TRUE.equals(req.allowComments());

        try {
            PublicationService.CreatedPublication created = switch (targetType) {
                case BOARD -> publicationService.createBoardPublication(boardId, userId, expiresAt, allowComments);
                case SNAPSHOT -> publicationService.createSnapshotPublication(
                        boardId,
                        requireSnapshotVersion(req),
                        userId,
                        expiresAt,
                        allowComments);
            };
            return Response.status(Response.Status.CREATED)
                    .entity(PublicationCreatedResponse.from(created))
                    .build();
        } catch (IllegalArgumentException e) {
            return apiRequestSupport.validationError(e.getMessage());
        }
    }

    @GET
    @Path("/{boardId}/publications")
    @Operation(summary = "List publications", description = "Lists publications for a board owned by the authenticated user.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Publications returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = PublicationResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public List<PublicationResponse> listPublications(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId) {
        featureSupport.requirePublicationsEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireOwner(boardId, userId);
        return publicationService.listForBoard(boardId).stream()
                .map(PublicationResponse::from)
                .toList();
    }

    @DELETE
    @Path("/{boardId}/publications/{publicationId}")
    @Operation(summary = "Revoke publication", description = "Revokes a publication belonging to a board owned by the authenticated user.")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Publication revoked."),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board or publication not found.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response revokePublication(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Publication identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("publicationId") String publicationId) {
        featureSupport.requirePublicationsEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireOwner(boardId, userId);
        Publication publication = publicationService.findById(publicationId).orElseThrow(NotFoundException::new);
        if (!publication.boardId().equals(boardId)) {
            throw new NotFoundException();
        }
        publicationService.revoke(publicationId).orElseThrow(NotFoundException::new);
        return Response.noContent().build();
    }

    @POST
    @Path("/{boardId}/publications/{publicationId}/rotate-token")
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Rotate publication access token", description = "Rotates the access token for an existing publication. Only the board owner may rotate publication access material.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Publication token rotated.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PublicationCreatedResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board or publication not found.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public PublicationCreatedResponse rotatePublicationToken(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Publication identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("publicationId") String publicationId) {
        featureSupport.requirePublicationsEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireOwner(boardId, userId);
        Publication publication = publicationService.findById(publicationId).orElseThrow(NotFoundException::new);
        if (!publication.boardId().equals(boardId)) {
            throw new NotFoundException();
        }
        return publicationService.rotateAccessToken(publicationId)
                .map(PublicationCreatedResponse::from)
                .orElseThrow(NotFoundException::new);
    }

    private static PublicationTargetType parseTargetType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return PublicationTargetType.BOARD;
        }
        try {
            return PublicationTargetType.fromStorageValue(rawValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Field 'targetType' must be 'board' or 'snapshot'.");
        }
    }

    private static long requireSnapshotVersion(CreatePublicationRequest req) {
        Long snapshotVersion = req == null ? null : req.snapshotVersion();
        if (snapshotVersion == null || snapshotVersion <= 0) {
            throw new IllegalArgumentException("Field 'snapshotVersion' must be a positive integer when targetType is 'snapshot'.");
        }
        return snapshotVersion;
    }

    private static Instant parseExpiresAt(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(rawValue.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Field 'expiresAt' must be an ISO-8601 instant (e.g. 2026-01-01T00:00:00Z).");
        }
    }

}
