package info.isaksson.erland.whiteboard.ws;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.domain.Invite;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InvitesRepository;
import info.isaksson.erland.whiteboard.security.InviteTokens;

@ApplicationScoped
public class BoardJoinAuthorizer {

    @Inject
    BoardsRepository boardsRepository;

    @Inject
    InvitesRepository invitesRepository;

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
            return new JoinDecision(false, "NOT_ALLOWED", null, null);
        }

        if (inviteToken != null && !inviteToken.isBlank()) {
            String tokenHash = InviteTokens.sha256Hex(inviteToken.trim());
            Invite invite = invitesRepository.findByTokenHash(tokenHash).orElse(null);
            if (invite == null) return new JoinDecision(false, "NOT_ALLOWED", null, null);
            if (!invite.boardId().equals(boardId)) return new JoinDecision(false, "NOT_ALLOWED", null, null);
            if (invite.revokedAt() != null) return new JoinDecision(false, "NOT_ALLOWED", null, null);
            if (invite.expiresAt() != null && invite.expiresAt().isBefore(Instant.now())) return new JoinDecision(false, "NOT_ALLOWED", null, null);
            if (invite.maxUses() != null && invite.uses() >= invite.maxUses()) return new JoinDecision(false, "NOT_ALLOWED", null, null);

            // Best-effort: increment uses (not strictly required for MVP)
            invitesRepository.incrementUses(invite.id());

            return new JoinDecision(true, "OK", "invite:" + invite.id(), invite.permission());
}

        return new JoinDecision(false, "NOT_ALLOWED", null, null);
    }
}
