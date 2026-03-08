package info.isaksson.erland.whiteboard.publication;

import java.time.Instant;
import java.util.Optional;

import info.isaksson.erland.whiteboard.persistence.PublicationsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PublicationPolicy {

    public static final String REASON_OK = "OK";
    public static final String REASON_NOT_FOUND = "NOT_FOUND";
    public static final String REASON_INACTIVE = "INACTIVE";
    public static final String REASON_REVOKED = "REVOKED";
    public static final String REASON_EXPIRED = "EXPIRED";

    private final PublicationsRepository publicationsRepository;

    @Inject
    public PublicationPolicy(PublicationsRepository publicationsRepository) {
        this.publicationsRepository = publicationsRepository;
    }

    public record Decision(boolean valid, String reason, Publication publication) {
        public static Decision notFound() {
            return new Decision(false, REASON_NOT_FOUND, null);
        }

        public String boardId() {
            return publication == null ? null : publication.boardId();
        }
    }

    public Decision validateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Decision.notFound();
        }
        String tokenHash = PublicationAccessTokens.sha256Hex(rawToken.trim());
        Optional<Publication> publication = publicationsRepository.findByTokenHash(tokenHash);
        return evaluate(publication.orElse(null), Instant.now());
    }

    Decision evaluate(Publication publication, Instant now) {
        if (publication == null) {
            return Decision.notFound();
        }
        if (publication.isRevoked()) {
            return new Decision(false, REASON_REVOKED, publication);
        }
        if (publication.expiresAt() != null && publication.expiresAt().isBefore(now)) {
            return new Decision(false, REASON_EXPIRED, publication);
        }
        if (publication.state() != PublicationState.ACTIVE) {
            return new Decision(false, REASON_INACTIVE, publication);
        }
        return new Decision(true, REASON_OK, publication);
    }
}
