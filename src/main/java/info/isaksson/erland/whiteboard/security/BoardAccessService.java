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

    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_VIEWER = "viewer";
    public static final String ROLE_EDITOR = "editor";
    public static final String ROLE_PUBLICATION_READER = "publication_reader";

    private final BoardsRepository boardsRepository;
    private final BoardPermissionsRepository permissionsRepository;

    @Inject
    public BoardAccessService(BoardsRepository boardsRepository,
                             BoardPermissionsRepository permissionsRepository) {
        this.boardsRepository = boardsRepository;
        this.permissionsRepository = permissionsRepository;
    }

    public record Access(Board board, String role, boolean viaPublication) {
        public Access(Board board, String role) {
            this(board, role, false);
        }

        public boolean isOwner() {
            return BoardCapabilityPolicy.isOwner(role);
        }

        public boolean isEditor() {
            return BoardCapabilityPolicy.isEditor(role);
        }

        public boolean isViewer() {
            return BoardCapabilityPolicy.isViewer(role);
        }

        public boolean isPublicationReader() {
            return BoardCapabilityPolicy.isPublicationReader(role) || viaPublication;
        }

        public boolean canWrite() {
            return allows(BoardCapability.BOARD_WRITE);
        }

        public boolean canRead() {
            return allows(BoardCapability.BOARD_READ) || allows(BoardCapability.PUBLICATION_READ);
        }

        public boolean isFacilitator() {
            return allows(BoardCapability.FACILITATE_BOARD);
        }

        public boolean isParticipant() {
            return allows(BoardCapability.VOTE_PARTICIPATE);
        }

        public boolean canObserveVotes() {
            return allows(BoardCapability.VOTE_OBSERVE);
        }

        public boolean allows(BoardCapability capability) {
            return BoardCapabilityPolicy.allows(role, viaPublication, capability);
        }
    }

    public Optional<Access> findAccess(String boardId, String userId) {
        return findCapabilityAccess(boardId, userId, false);
    }

    public Optional<Access> findCapabilityAccess(String boardId, String userId) {
        return findCapabilityAccess(boardId, userId, false);
    }

    public Optional<Access> findCapabilityAccess(String boardId, String userId, boolean publicationReadable) {
        Optional<Board> b = boardsRepository.findById(boardId);
        if (b.isEmpty()) {
            return Optional.empty();
        }
        Board board = b.get();

        Optional<Access> membership = findMembershipAccess(board, userId);
        if (membership.isPresent()) {
            return membership;
        }
        if (publicationReadable) {
            return Optional.of(new Access(board, ROLE_PUBLICATION_READER, true));
        }
        return Optional.empty();
    }

    public Optional<Access> findPublicationReadAccess(String boardId, String userId, boolean publicationReadable) {
        return findCapabilityAccess(boardId, userId, publicationReadable)
                .filter(access -> access.allows(BoardCapability.PUBLICATION_READ));
    }

    public boolean hasCapability(String boardId, String userId, BoardCapability capability) {
        return hasCapability(boardId, userId, capability, false);
    }

    public boolean hasCapability(String boardId, String userId, BoardCapability capability, boolean publicationReadable) {
        return findCapabilityAccess(boardId, userId, publicationReadable)
                .filter(access -> access.allows(capability))
                .isPresent();
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
            boardsRepository.findById(id)
                    .filter(board -> "active".equals(board.status()))
                    .ifPresent(out::add);
        }
        return out;
    }

    public void grant(String boardId, String userId, String role) {
        permissionsRepository.upsert(boardId, userId, role);
    }

    private Optional<Access> findMembershipAccess(Board board, String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        if (board.ownerUserId().equals(userId)) {
            return Optional.of(new Access(board, ROLE_OWNER));
        }
        return permissionsRepository.findRole(board.id(), userId).map(r -> new Access(board, r, false));
    }
}
