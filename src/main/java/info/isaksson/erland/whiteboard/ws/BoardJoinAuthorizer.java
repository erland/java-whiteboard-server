package info.isaksson.erland.whiteboard.ws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import info.isaksson.erland.whiteboard.security.InvitePolicy;

@ApplicationScoped
public class BoardJoinAuthorizer {

    @Inject
    BoardGuards boardGuards;

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
        Board board = boardGuards.findExistingNotDeleted(boardId).orElse(null);
        if (board == null) {
            return new JoinDecision(false, "NOT_ALLOWED", null, null);
        }

        if (userId != null && !userId.isBlank()) {
            if (board.ownerUserId().equals(userId)) {
                return new JoinDecision(true, "OK", userId, "owner");
            }

            // Shared access (viewer/editor)
            if (boardGuards != null) {
                try {
                    var access = boardGuards.requireReadableAccess(boardId, userId);
                    return new JoinDecision(true, "OK", userId, access.role());
                } catch (jakarta.ws.rs.NotFoundException ignored) {
                    // fall through to NOT_ALLOWED
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
