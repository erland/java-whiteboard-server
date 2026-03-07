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
import info.isaksson.erland.whiteboard.security.BoardGuards;
import info.isaksson.erland.whiteboard.security.Authz;
import io.quarkus.security.identity.SecurityIdentity;

@Path("/api/boards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BoardsResource {

    private static final String DEFAULT_BOARD_KIND = "whiteboard";
    private static final String DEFAULT_BOARD_TYPE = "advanced";

    private final BoardsRepository boardsRepository;
    private final BoardAccessService boardAccess;
    private final BoardGuards boardGuards;
    private final SecurityIdentity identity;

    public BoardsResource(BoardsRepository boardsRepository, BoardAccessService boardAccess, BoardGuards boardGuards, SecurityIdentity identity) {
        this.boardsRepository = boardsRepository;
        this.boardAccess = boardAccess;
        this.boardGuards = boardGuards;
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
        String rawType = req == null ? null : req.type();
        String rawBoardType = req == null ? null : req.boardType();
        String type = normalizeBoardKind(rawType);
        String boardType = normalizeBoardType(rawBoardType, rawType);

        if (name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new info.isaksson.erland.whiteboard.api.errors.ApiError("VALIDATION_ERROR", "Field 'name' is required."))
                    .build();
        }
        if (rawType == null || rawType.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new info.isaksson.erland.whiteboard.api.errors.ApiError("VALIDATION_ERROR", "Field 'type' is required."))
                    .build();
        }
        if (boardType == null || boardType.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new info.isaksson.erland.whiteboard.api.errors.ApiError("VALIDATION_ERROR", "Field 'boardType' is required."))
                    .build();
        }

        String userId = Authz.userId(identity);
        String id = UUID.randomUUID().toString();

        Board created = boardsRepository.create(new Board(
                id,
                name.trim(),
                type,
                boardType,
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

        Board b = boardGuards.requireReadableAccess(id, userId).board();
        return BoardResponse.from(b);
    }

    @PATCH
    @Path("/{id}")
    public Response update(@PathParam("id") String id, UpdateBoardRequest req) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);

        Board existing = boardGuards.requireWritableAccess(id, userId).board();
        if (!"active".equals(existing.status())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new info.isaksson.erland.whiteboard.api.errors.ApiError("BOARD_NOT_ACTIVE", "Board is not active."))
                    .build();
        }

        String name = req == null ? null : req.name();
        String newName = (name == null || name.isBlank()) ? existing.name() : name.trim();
        String newType = normalizeBoardKind(req == null ? null : req.type());
        if (newType == null || newType.isBlank()) {
            newType = existing.type();
        }
        String newBoardType = normalizeBoardType(req == null ? null : req.boardType(), req == null ? null : req.type());
        if (newBoardType == null || newBoardType.isBlank()) {
            newBoardType = existing.boardType();
        }

        Board updated = boardsRepository.updateMetadata(id, userId, newName, newType, newBoardType);
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

    private static String normalizeBoardKind(String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return DEFAULT_BOARD_KIND;
        }
        return DEFAULT_BOARD_KIND;
    }

    private static String normalizeBoardType(String requestedBoardType, String requestedType) {
        if (requestedBoardType != null && !requestedBoardType.isBlank()) {
            return requestedBoardType.trim();
        }
        if (requestedType != null && !requestedType.isBlank()) {
            String trimmedType = requestedType.trim();
            if (!DEFAULT_BOARD_KIND.equals(trimmedType)) {
                return trimmedType;
            }
        }
        return DEFAULT_BOARD_TYPE;
    }

}