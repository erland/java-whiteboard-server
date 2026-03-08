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
        return requireBoardOwnerAccess(boardId, userId).board();
    }

    public BoardAccessService.Access requireReadableAccess(String boardId, String userId) {
        return requireBoardReadAccess(boardId, userId);
    }

    public BoardAccessService.Access requireWritableAccess(String boardId, String userId) {
        return requireBoardWriteAccess(boardId, userId);
    }

    public BoardAccessService.Access requireBoardOwnerAccess(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.BOARD_OWNER, false);
    }

    public BoardAccessService.Access requireBoardReadAccess(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.BOARD_READ, false);
    }

    public BoardAccessService.Access requireBoardWriteAccess(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.BOARD_WRITE, false);
    }

    public BoardAccessService.Access requirePublicationReadAccess(String boardId, String userId, boolean publicationReadable) {
        return requireCapability(boardId, userId, BoardCapability.PUBLICATION_READ, publicationReadable);
    }

    public BoardAccessService.Access requireCommentParticipation(String boardId, String userId, boolean publicationReadable) {
        return requireCapability(boardId, userId, BoardCapability.COMMENT_PARTICIPATE, publicationReadable);
    }

    public BoardAccessService.Access requireAssetUseAccess(String boardId, String userId, boolean publicationReadable) {
        return requireCapability(boardId, userId, BoardCapability.ASSET_USE, publicationReadable);
    }

    public BoardAccessService.Access requireAssetManageAccess(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.ASSET_MANAGE, false);
    }

    public BoardAccessService.Access requireFacilitationAccess(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.FACILITATE, false);
    }

    public BoardAccessService.Access requireVoteParticipation(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.VOTE_PARTICIPATE, false);
    }

    public BoardAccessService.Access requireVoteObservation(String boardId, String userId, boolean publicationReadable) {
        return requireCapability(boardId, userId, BoardCapability.VOTE_OBSERVE, publicationReadable);
    }

    public BoardAccessService.Access requireTimerControl(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.TIMER_CONTROL, false);
    }

    public BoardAccessService.Access requireTimerObservation(String boardId, String userId, boolean publicationReadable) {
        return requireCapability(boardId, userId, BoardCapability.TIMER_OBSERVE, publicationReadable);
    }

    public BoardAccessService.Access requireReactionEmit(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.REACTION_EMIT, false);
    }

    public BoardAccessService.Access requireReactionObservation(String boardId, String userId, boolean publicationReadable) {
        return requireCapability(boardId, userId, BoardCapability.REACTION_OBSERVE, publicationReadable);
    }

    public BoardAccessService.Access requirePrivateModeContribution(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.PRIVATE_MODE_CONTRIBUTE, false);
    }

    public BoardAccessService.Access requirePrivateModeReveal(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.PRIVATE_MODE_REVEAL, false);
    }

    public BoardAccessService.Access requirePrivateModeView(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.PRIVATE_MODE_VIEW, false);
    }

    public BoardAccessService.Access requireLibraryReadAccess(String boardId, String userId, boolean publicationReadable) {
        return requireCapability(boardId, userId, BoardCapability.LIBRARY_READ, publicationReadable);
    }

    public BoardAccessService.Access requireLibraryShareAccess(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.LIBRARY_SHARE, false);
    }

    public BoardAccessService.Access requireLibraryManageAccess(String boardId, String userId) {
        return requireCapability(boardId, userId, BoardCapability.LIBRARY_MANAGE, false);
    }

    private BoardAccessService.Access requireCapability(String boardId,
                                                        String userId,
                                                        BoardCapability capability,
                                                        boolean publicationReadable) {
        return boardAccess.findCapabilityAccess(boardId, userId, publicationReadable)
                .filter(access -> access.allows(capability))
                .filter(access -> !isDeleted(access.board()))
                .orElseThrow(NotFoundException::new);
    }

    private static boolean isDeleted(Board board) {
        return "deleted".equals(board.status());
    }
}
