package info.isaksson.erland.whiteboard.security;

import java.time.Instant;
import java.util.Optional;

import info.isaksson.erland.whiteboard.domain.Invite;
import info.isaksson.erland.whiteboard.persistence.InvitesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InvitePolicy {

    public static final String REASON_OK = "OK";
    public static final String REASON_NOT_FOUND = "NOT_FOUND";
    public static final String REASON_REVOKED = "REVOKED";
    public static final String REASON_EXPIRED = "EXPIRED";
    public static final String REASON_MAX_USES_REACHED = "MAX_USES_REACHED";

    private final InvitesRepository invitesRepository;

    @Inject
    public InvitePolicy(InvitesRepository invitesRepository) {
        this.invitesRepository = invitesRepository;
    }

    public record Decision(boolean valid, String reason, Invite invite, String permission) {
        public static Decision notFound() {
            return new Decision(false, REASON_NOT_FOUND, null, null);
        }

        public boolean matchesBoard(String boardId) {
            return invite != null && invite.boardId().equals(boardId);
        }

        public String boardId() {
            return invite == null ? null : invite.boardId();
        }
    }

    public Decision validateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Decision.notFound();
        }
        String tokenHash = InviteTokens.sha256Hex(rawToken.trim());
        Optional<Invite> invite = invitesRepository.findByTokenHash(tokenHash);
        return evaluate(invite.orElse(null), Instant.now());
    }

    Decision evaluate(Invite invite, Instant now) {
        if (invite == null) {
            return Decision.notFound();
        }
        if (invite.revokedAt() != null) {
            return new Decision(false, REASON_REVOKED, invite, normalizePermission(invite.permission()));
        }
        if (invite.expiresAt() != null && invite.expiresAt().isBefore(now)) {
            return new Decision(false, REASON_EXPIRED, invite, normalizePermission(invite.permission()));
        }
        if (invite.maxUses() != null && invite.uses() >= invite.maxUses()) {
            return new Decision(false, REASON_MAX_USES_REACHED, invite, normalizePermission(invite.permission()));
        }
        return new Decision(true, REASON_OK, invite, normalizePermission(invite.permission()));
    }


    public void recordUse(Invite invite) {
        if (invite != null) {
            invitesRepository.incrementUses(invite.id());
        }
    }

    public String normalizePermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return BoardAccessService.ROLE_VIEWER;
        }
        return switch (permission.trim()) {
            case "edit", "editor" -> BoardAccessService.ROLE_EDITOR;
            default -> BoardAccessService.ROLE_VIEWER;
        };
    }
}
