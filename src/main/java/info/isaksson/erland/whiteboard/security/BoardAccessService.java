package info.isaksson.erland.whiteboard.security;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.BoardPermissionsRepository;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class BoardAccessService {

    public static final String ROLE_VIEWER = "viewer";
    public static final String ROLE_EDITOR = "editor";

    private final BoardsRepository boardsRepository;
    private final BoardPermissionsRepository permissionsRepository;

    @Inject
    public BoardAccessService(BoardsRepository boardsRepository,
                             BoardPermissionsRepository permissionsRepository) {
        this.boardsRepository = boardsRepository;
        this.permissionsRepository = permissionsRepository;
    }

    public record Access(Board board, String role) {
        public boolean isOwner() {
            return "owner".equals(role);
        }

        public boolean canWrite() {
            return isOwner() || ROLE_EDITOR.equals(role);
        }

        public boolean canRead() {
            return isOwner() || ROLE_EDITOR.equals(role) || ROLE_VIEWER.equals(role);
        }
    }

    public Optional<Access> findAccess(String boardId, String userId) {
        Optional<Board> b = boardsRepository.findById(boardId);
        if (b.isEmpty()) return Optional.empty();
        Board board = b.get();
        if (board.ownerUserId().equals(userId)) {
            return Optional.of(new Access(board, "owner"));
        }
        return permissionsRepository.findRole(boardId, userId).map(r -> new Access(board, r));
    }

    public List<Board> listAccessibleBoards(String userId) {
        List<Board> owned = boardsRepository.listForOwner(userId);
        List<String> sharedIds = permissionsRepository.listBoardIdsForUser(userId);
        if (sharedIds.isEmpty()) return owned;

        Set<String> seen = new HashSet<>();
        List<Board> out = new ArrayList<>();
        for (Board b : owned) {
            out.add(b);
            seen.add(b.id());
        }
        for (String id : sharedIds) {
            if (seen.contains(id)) continue;
            boardsRepository.findById(id).ifPresent(out::add);
        }
        return out;
    }

    public void grant(String boardId, String userId, String role) {
        permissionsRepository.upsert(boardId, userId, role);
    }
}
