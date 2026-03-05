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
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.security.Authz;
import io.quarkus.security.identity.SecurityIdentity;

@Path("/api/boards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BoardsResource {

    private final BoardsRepository boardsRepository;
    private final BoardAccessService boardAccess;
    private final SecurityIdentity identity;

    public BoardsResource(BoardsRepository boardsRepository, BoardAccessService boardAccess, SecurityIdentity identity) {
        this.boardsRepository = boardsRepository;
        this.boardAccess = boardAccess;
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

        String name = req == null ? null : req.name();
        String type = req == null ? null : req.type();

        if (name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new info.isaksson.erland.whiteboard.api.errors.ApiError("VALIDATION_ERROR", "Field 'name' is required."))
                    .build();
        }
        if (type == null || type.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new info.isaksson.erland.whiteboard.api.errors.ApiError("VALIDATION_ERROR", "Field 'type' is required."))
                    .build();
        }

        String userId = Authz.userId(identity);
        String id = UUID.randomUUID().toString();

        Board created = boardsRepository.create(new Board(
                id,
                name.trim(),
                type.trim(),
                userId,
                "active",
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

        BoardAccessService.Access access = boardAccess.findAccess(id, userId)
                .filter(BoardAccessService.Access::canRead)
                .orElseThrow(NotFoundException::new);

        // Archived boards should still be retrievable by the owner (and other readers)
        // to support UI flows (e.g. showing an archived status) and match API tests.
        // Only "deleted" boards are treated as not found.
        Board b = access.board();
        if ("deleted".equals(b.status())) {
            throw new NotFoundException();
        }
        return BoardResponse.from(b);
    }

    @PATCH
    @Path("/{id}")
    public Response update(@PathParam("id") String id, UpdateBoardRequest req) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        Board existing = boardsRepository.findById(id).orElseThrow(NotFoundException::new);
        if ("deleted".equals(existing.status())) {
            throw new NotFoundException();
        }

        // allow owner + editor
        if (!boardAccess.findAccess(id, userId).map(BoardAccessService.Access::canWrite).orElse(false)) {
            throw new NotFoundException();
        }
        if (!"active".equals(existing.status())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new info.isaksson.erland.whiteboard.api.errors.ApiError("BOARD_NOT_ACTIVE", "Board is not active."))
                    .build();
        }

        String name = req == null ? null : req.name();
        String type = req == null ? null : req.type();

        String newName = (name == null || name.isBlank()) ? existing.name() : name.trim();
        String newType = (type == null || type.isBlank()) ? existing.type() : type.trim();

        Board updated = boardsRepository.updateMetadata(id, userId, newName, newType);
        if (updated == null) {
            // treat as not found to avoid leaks
            throw new NotFoundException();
        }

        return Response.ok(BoardResponse.from(updated)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response archive(@PathParam("id") String id) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        Board existing = boardsRepository.findById(id).orElseThrow(NotFoundException::new);
        if (!existing.ownerUserId().equals(userId) || "deleted".equals(existing.status())) {
            throw new NotFoundException();
        }

        boolean ok = boardsRepository.archive(id, userId);
        if (!ok) {
            throw new NotFoundException();
        }
        return Response.noContent().build();
    }
}
