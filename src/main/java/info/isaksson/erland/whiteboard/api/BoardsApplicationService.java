package info.isaksson.erland.whiteboard.api;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import info.isaksson.erland.whiteboard.api.dto.BoardResponse;
import info.isaksson.erland.whiteboard.api.dto.CreateBoardRequest;
import info.isaksson.erland.whiteboard.api.dto.UpdateBoardRequest;
import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.domain.BoardMetadataRules;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.security.BoardGuards;

@ApplicationScoped
public class BoardsApplicationService {

    private final BoardsRepository boardsRepository;
    private final BoardAccessService boardAccess;
    private final BoardGuards boardGuards;
    private final BoardMetadataRules boardMetadataRules;

    public BoardsApplicationService(BoardsRepository boardsRepository,
                                    BoardAccessService boardAccess,
                                    BoardGuards boardGuards,
                                    BoardMetadataRules boardMetadataRules) {
        this.boardsRepository = boardsRepository;
        this.boardAccess = boardAccess;
        this.boardGuards = boardGuards;
        this.boardMetadataRules = boardMetadataRules;
    }

    public List<BoardResponse> list(String userId) {
        return boardAccess.listAccessibleBoards(userId).stream()
                .map(BoardResponse::from)
                .toList();
    }

    public BoardResponse create(String userId, CreateBoardRequest req) {
        BoardMetadataRules.NormalizedCreate normalized = boardMetadataRules.normalizeCreate(req);
        Board created = boardsRepository.create(new Board(
                UUID.randomUUID().toString(),
                normalized.name(),
                normalized.type(),
                normalized.boardType(),
                userId,
                BoardMetadataRules.STATUS_ACTIVE,
                null,
                null
        ));
        return BoardResponse.from(created);
    }

    public BoardResponse get(String boardId, String userId) {
        return BoardResponse.from(boardGuards.requireReadableAccess(boardId, userId).board());
    }

    public BoardResponse update(String boardId, String userId, UpdateBoardRequest req) {
        Board existing = boardGuards.requireWritableAccess(boardId, userId).board();
        BoardMetadataRules.NormalizedUpdate normalized = boardMetadataRules.normalizeUpdate(req, existing);
        Board updated = boardsRepository.updateMetadata(
                boardId,
                userId,
                normalized.name(),
                normalized.type(),
                normalized.boardType());
        if (updated == null) {
            throw new NotFoundException();
        }
        return BoardResponse.from(updated);
    }

    public void archive(String boardId, String userId) {
        boardGuards.requireOwner(boardId, userId);
        boolean ok = boardsRepository.archive(boardId, userId);
        if (!ok) {
            throw new NotFoundException();
        }
    }
}
