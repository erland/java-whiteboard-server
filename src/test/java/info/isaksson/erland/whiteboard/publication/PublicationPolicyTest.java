package info.isaksson.erland.whiteboard.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.persistence.InMemoryPublicationsRepository;

public class PublicationPolicyTest {

    private InMemoryPublicationsRepository publicationsRepository;
    private PublicationPolicy publicationPolicy;

    @BeforeEach
    void setup() {
        publicationsRepository = new InMemoryPublicationsRepository();
        publicationPolicy = new PublicationPolicy(publicationsRepository);
    }

    @Test
    void returns_not_found_for_blank_token() {
        PublicationPolicy.Decision decision = publicationPolicy.validateToken("  ");
        assertFalse(decision.valid());
        assertEquals(PublicationPolicy.REASON_NOT_FOUND, decision.reason());
    }

    @Test
    void returns_inactive_for_inactive_publication() {
        String token = storePublication(PublicationState.INACTIVE, null, null);

        PublicationPolicy.Decision decision = publicationPolicy.validateToken(token);
        assertFalse(decision.valid());
        assertEquals(PublicationPolicy.REASON_INACTIVE, decision.reason());
    }

    @Test
    void returns_revoked_for_revoked_publication() {
        String token = storePublication(PublicationState.ACTIVE, null, Instant.now());

        PublicationPolicy.Decision decision = publicationPolicy.validateToken(token);
        assertFalse(decision.valid());
        assertEquals(PublicationPolicy.REASON_REVOKED, decision.reason());
    }

    @Test
    void returns_expired_for_expired_publication() {
        String token = storePublication(PublicationState.ACTIVE, Instant.now().minusSeconds(60), null);

        PublicationPolicy.Decision decision = publicationPolicy.validateToken(token);
        assertFalse(decision.valid());
        assertEquals(PublicationPolicy.REASON_EXPIRED, decision.reason());
    }

    @Test
    void accepts_active_publication() {
        String token = storePublication(PublicationState.ACTIVE, Instant.now().plusSeconds(300), null);

        PublicationPolicy.Decision decision = publicationPolicy.validateToken(token);
        assertTrue(decision.valid());
        assertEquals(PublicationPolicy.REASON_OK, decision.reason());
        assertEquals("board-1", decision.boardId());
    }

    private String storePublication(PublicationState state, Instant expiresAt, Instant revokedAt) {
        String token = PublicationAccessTokens.generateToken();
        publicationsRepository.create(new Publication(
                UUID.randomUUID().toString(),
                "board-1",
                null,
                PublicationTargetType.BOARD,
                state,
                PublicationAccessTokens.sha256Hex(token),
                "alice",
                false,
                Instant.now(),
                Instant.now(),
                expiresAt,
                revokedAt
        ));
        return token;
    }
}
