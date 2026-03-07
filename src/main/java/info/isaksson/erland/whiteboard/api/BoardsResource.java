package info.isaksson.erland.whiteboard.api;

import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
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

import info.isaksson.erland.whiteboard.api.dto.BoardResponse;
import info.isaksson.erland.whiteboard.api.dto.CreateBoardRequest;
import info.isaksson.erland.whiteboard.api.dto.UpdateBoardRequest;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.domain.BoardMetadataRules;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import io.quarkus.security.identity.SecurityIdentity;

@Tag(name = "Boards")
@SecurityRequirement(name = "bearerAuth")
@Path("/api/boards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BoardsResource {

    private final BoardsRepository boardsRepository;
    private final BoardAccessService boardAccess;
    private final BoardGuards boardGuards;
    private final BoardMetadataRules boardMetadataRules;
    private final SecurityIdentity identity;

    public BoardsResource(BoardsRepository boardsRepository,
                          BoardAccessService boardAccess,
                          BoardGuards boardGuards,
                          BoardMetadataRules boardMetadataRules,
                          SecurityIdentity identity) {
        this.boardsRepository = boardsRepository;
        this.boardAccess = boardAccess;
        this.boardGuards = boardGuards;
        this.boardMetadataRules = boardMetadataRules;
        this.identity = identity;
    }

    @GET
    @Operation(summary = "List boards", description = "Returns all boards the authenticated user can access, including owned and shared boards.")
    @APIResponse(responseCode = "200", description = "Accessible boards returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = BoardResponse.class)))
    @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public List<BoardResponse> list() {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        return boardAccess.listAccessibleBoards(userId).stream()
                .map(BoardResponse::from)
                .toList();
    }

    @POST
    @Operation(summary = "Create board", description = "Creates a new active board owned by the authenticated user.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Board created.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BoardResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid board request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response create(
            @RequestBody(required = true, description = "Board creation request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateBoardRequest.class)))
            CreateBoardRequest req) {
        Authz.requireUserOrAdmin(identity);

        BoardMetadataRules.NormalizedCreate normalized;
        try {
            normalized = boardMetadataRules.normalizeCreate(req);
        } catch (BoardMetadataRules.ValidationException e) {
            return validationError(e.getMessage());
        }

        String userId = Authz.userId(identity);
        String id = UUID.randomUUID().toString();

        Board created = boardsRepository.create(new Board(
                id,
                normalized.name(),
                normalized.type(),
                normalized.boardType(),
                userId,
                BoardMetadataRules.STATUS_ACTIVE,
                null,
                null
        ));

        return Response.status(Response.Status.CREATED)
                .entity(BoardResponse.from(created))
                .build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get board", description = "Returns board metadata for a specific board that the authenticated user can read.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Board returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BoardResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public BoardResponse get(@Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("id") String id) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        Board b = boardGuards.requireReadableAccess(id, userId).board();
        return BoardResponse.from(b);
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Update board metadata", description = "Updates editable board metadata for a board the authenticated user can write to.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Board updated.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BoardResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid board update request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "409", description = "Board is not active and cannot be updated.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response update(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("id") String id,
            @RequestBody(required = true, description = "Board metadata update request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UpdateBoardRequest.class)))
            UpdateBoardRequest req) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        Board existing = boardGuards.requireWritableAccess(id, userId).board();

        BoardMetadataRules.NormalizedUpdate normalized;
        try {
            normalized = boardMetadataRules.normalizeUpdate(req, existing);
        } catch (BoardMetadataRules.BoardNotActiveException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ApiError("BOARD_NOT_ACTIVE", e.getMessage()))
                    .build();
        }

        Board updated = boardsRepository.updateMetadata(
                id,
                userId,
                normalized.name(),
                normalized.type(),
                normalized.boardType());
        if (updated == null) {
            throw new NotFoundException();
        }

        return Response.ok(BoardResponse.from(updated)).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Archive board", description = "Archives a board owned by the authenticated user.")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Board archived."),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response archive(@Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("id") String id) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        boardGuards.requireOwner(id, userId);

        boolean ok = boardsRepository.archive(id, userId);
        if (!ok) {
            throw new NotFoundException();
        }
        return Response.noContent().build();
    }

    private static Response validationError(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError("VALIDATION_ERROR", message))
                .build();
    }
}
