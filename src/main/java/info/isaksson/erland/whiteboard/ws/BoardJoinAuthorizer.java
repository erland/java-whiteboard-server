package info.isaksson.erland.whiteboard.ws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.security.InvitePolicy;

@ApplicationScoped
public class BoardJoinAuthorizer {

    @Inject
    BoardsRepository boardsRepository;

    @Inject
    BoardAccessService boardAccess;

    @Inject
    InvitePolicy invitePolicy;

    public record JoinDecision(boolean allowed, String reason, String effectiveUserId, String permission) {}

    /**
     * Authorize a join.
     *
     * Rules (MVP):
     * - Board must exist and not be deleted.
     * - If userId is present: must be the board owner.
     * - Else: if inviteToken is present: must map to a valid invite (not revoked/expired/max uses reached).
     * - If allowed via invite token, effectiveUserId becomes "invite:<inviteId>" (client can treat as guest).
     *
     * This intentionally avoids leaking board existence via different error messages.
     */
    public JoinDecision authorize(String boardId, String userId, String inviteToken) {
        Board board = boardsRepository.findById(boardId).orElse(null);
        if (board == null || "deleted".equals(board.status())) {
            return new JoinDecision(false, "NOT_ALLOWED", null, null);
        }

        if (userId != null && !userId.isBlank()) {
            if (board.ownerUserId().equals(userId)) {
                return new JoinDecision(true, "OK", userId, "owner");
            }

            // Shared access (viewer/editor)
            if (boardAccess != null) {
                var access = boardAccess.findAccess(boardId, userId).orElse(null);
                if (access != null && access.canRead()) {
                    return new JoinDecision(true, "OK", userId, access.role());
                }
            }

            return new JoinDecision(false, "NOT_ALLOWED", null, null);
        }

        if (inviteToken != null && !inviteToken.isBlank()) {
            InvitePolicy.Decision decision = invitePolicy.validateToken(inviteToken);
            if (!decision.valid() || decision.invite() == null || !decision.matchesBoard(boardId)) {
                return new JoinDecision(false, "NOT_ALLOWED", null, null);
            }
            invitePolicy.recordUse(decision.invite());
            return new JoinDecision(true, "OK", "invite:" + decision.invite().id(), decision.permission());
        }

        return new JoinDecision(false, "NOT_ALLOWED", null, null);
    }
}
