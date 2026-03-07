package info.isaksson.erland.whiteboard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Invite;
import info.isaksson.erland.whiteboard.persistence.InMemoryInvitesRepository;

public class InvitePolicyTest {

    private InMemoryInvitesRepository invitesRepository;
    private InvitePolicy invitePolicy;

    @BeforeEach
    void setup() {
        invitesRepository = new InMemoryInvitesRepository();
        invitePolicy = new InvitePolicy(invitesRepository);
    }

    @Test
    void returns_not_found_for_blank_token() {
        InvitePolicy.Decision decision = invitePolicy.validateToken("  ");
        assertFalse(decision.valid());
        assertEquals(InvitePolicy.REASON_NOT_FOUND, decision.reason());
    }

    @Test
    void returns_revoked_for_revoked_invite() {
        String token = storeInvite("viewer", null, 5, 0, Instant.now());
        Invite invite = invitesRepository.findByTokenHash(InviteTokens.sha256Hex(token)).orElseThrow();
        invitesRepository.revoke(invite.id());

        InvitePolicy.Decision decision = invitePolicy.validateToken(token);
        assertFalse(decision.valid());
        assertEquals(InvitePolicy.REASON_REVOKED, decision.reason());
        assertEquals("viewer", decision.permission());
    }

    @Test
    void returns_expired_for_expired_invite() {
        String token = storeInvite("editor", Instant.now().minusSeconds(60), 5, 0, null);

        InvitePolicy.Decision decision = invitePolicy.validateToken(token);
        assertFalse(decision.valid());
        assertEquals(InvitePolicy.REASON_EXPIRED, decision.reason());
        assertEquals("editor", decision.permission());
    }

    @Test
    void returns_max_uses_reached_for_exhausted_invite() {
        String token = storeInvite("view", null, 1, 1, null);

        InvitePolicy.Decision decision = invitePolicy.validateToken(token);
        assertFalse(decision.valid());
        assertEquals(InvitePolicy.REASON_MAX_USES_REACHED, decision.reason());
        assertEquals("viewer", decision.permission());
    }

    @Test
    void normalizes_legacy_permissions_for_valid_invites() {
        String token = storeInvite("edit", null, 5, 0, null);

        InvitePolicy.Decision decision = invitePolicy.validateToken(token);
        assertTrue(decision.valid());
        assertEquals(InvitePolicy.REASON_OK, decision.reason());
        assertEquals("editor", decision.permission());
        assertEquals(decision.invite().boardId(), decision.boardId());
    }

    private String storeInvite(String permission, Instant expiresAt, Integer maxUses, int uses, Instant revokedAt) {
        String token = InviteTokens.generateToken();
        invitesRepository.create(new Invite(
                UUID.randomUUID().toString(),
                "board-1",
                InviteTokens.sha256Hex(token),
                permission,
                expiresAt,
                maxUses,
                uses,
                revokedAt,
                Instant.now()
        ));
        return token;
    }
}
