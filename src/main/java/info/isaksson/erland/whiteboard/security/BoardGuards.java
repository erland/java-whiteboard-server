package info.isaksson.erland.whiteboard.security;

import java.util.Optional;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class BoardGuards {

    private final BoardsRepository boardsRepository;
    private final BoardAccessService boardAccess;

    @Inject
    public BoardGuards(BoardsRepository boardsRepository, BoardAccessService boardAccess) {
        this.boardsRepository = boardsRepository;
        this.boardAccess = boardAccess;
    }

    public Optional<Board> findExistingNotDeleted(String boardId) {
        return boardsRepository.findById(boardId)
                .filter(board -> !isDeleted(board));
    }

    public Board requireOwner(String boardId, String userId) {
        Board board = findExistingNotDeleted(boardId).orElseThrow(NotFoundException::new);
        if (!board.ownerUserId().equals(userId)) {
            throw new NotFoundException();
        }
        return board;
    }

    public BoardAccessService.Access requireReadableAccess(String boardId, String userId) {
        return boardAccess.findAccess(boardId, userId)
                .filter(BoardAccessService.Access::canRead)
                .filter(access -> !isDeleted(access.board()))
                .orElseThrow(NotFoundException::new);
    }

    public BoardAccessService.Access requireWritableAccess(String boardId, String userId) {
        return boardAccess.findAccess(boardId, userId)
                .filter(BoardAccessService.Access::canWrite)
                .filter(access -> !isDeleted(access.board()))
                .orElseThrow(NotFoundException::new);
    }

    private static boolean isDeleted(Board board) {
        return "deleted".equals(board.status());
    }
}
