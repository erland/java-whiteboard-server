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

import info.isaksson.erland.whiteboard.api.dto.BoardResponse;
import info.isaksson.erland.whiteboard.api.dto.CreateBoardRequest;
import info.isaksson.erland.whiteboard.api.dto.UpdateBoardRequest;
import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.domain.BoardMetadataRules;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import io.quarkus.security.identity.SecurityIdentity;

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
    public List<BoardResponse> list() {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        return boardAccess.listAccessibleBoards(userId).stream()
                .map(BoardResponse::from)
                .toList();
    }

    @POST
    public Response create(CreateBoardRequest req) {
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
    public BoardResponse get(@PathParam("id") String id) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        Board b = boardGuards.requireReadableAccess(id, userId).board();
        return BoardResponse.from(b);
    }

    @PATCH
    @Path("/{id}")
    public Response update(@PathParam("id") String id, UpdateBoardRequest req) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        Board existing = boardGuards.requireWritableAccess(id, userId).board();

        BoardMetadataRules.NormalizedUpdate normalized;
        try {
            normalized = boardMetadataRules.normalizeUpdate(req, existing);
        } catch (BoardMetadataRules.BoardNotActiveException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new info.isaksson.erland.whiteboard.api.errors.ApiError("BOARD_NOT_ACTIVE", e.getMessage()))
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
    public Response archive(@PathParam("id") String id) {
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
                .entity(new info.isaksson.erland.whiteboard.api.errors.ApiError("VALIDATION_ERROR", message))
                .build();
    }
}
