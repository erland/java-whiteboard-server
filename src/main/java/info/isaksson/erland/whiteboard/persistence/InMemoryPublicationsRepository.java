package info.isaksson.erland.whiteboard.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import info.isaksson.erland.whiteboard.publication.Publication;
import info.isaksson.erland.whiteboard.publication.PublicationState;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.arc.profile.IfBuildProfile;

@ApplicationScoped
@IfBuildProfile("test")
@Priority(1)
public class InMemoryPublicationsRepository implements PublicationsRepository {

    private final ConcurrentHashMap<String, Publication> publications = new ConcurrentHashMap<>();

    @Override
    public Publication create(Publication publication) {
        Instant now = Instant.now();
        Publication created = new Publication(
                publication.id(),
                publication.boardId(),
                publication.snapshotVersion(),
                publication.targetType(),
                publication.state(),
                publication.accessTokenHash(),
                publication.createdByUserId(),
                publication.allowComments(),
                now,
                now,
                publication.expiresAt(),
                publication.revokedAt()
        );
        publications.put(created.id(), created);
        return created;
    }

    @Override
    public Optional<Publication> findById(String publicationId) {
        return Optional.ofNullable(publications.get(publicationId));
    }

    @Override
    public Optional<Publication> findByTokenHash(String tokenHash) {
        return publications.values().stream()
                .filter(publication -> publication.accessTokenHash().equals(tokenHash))
                .findFirst();
    }

    @Override
    public List<Publication> listForBoard(String boardId) {
        return publications.values().stream()
                .filter(publication -> publication.boardId().equals(boardId))
                .sorted(Comparator.comparing(Publication::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<Publication> revoke(String publicationId) {
        Publication updated = publications.computeIfPresent(publicationId, (k, existing) -> {
            Instant now = Instant.now();
            return new Publication(
                    existing.id(),
                    existing.boardId(),
                    existing.snapshotVersion(),
                    existing.targetType(),
                    PublicationState.REVOKED,
                    existing.accessTokenHash(),
                    existing.createdByUserId(),
                    existing.allowComments(),
                    existing.createdAt(),
                    now,
                    existing.expiresAt(),
                    existing.revokedAt() == null ? now : existing.revokedAt()
            );
        });
        return Optional.ofNullable(updated);
    }

    @Override
    public Optional<Publication> rotateAccessToken(String publicationId, String newTokenHash) {
        Publication updated = publications.computeIfPresent(publicationId, (k, existing) -> new Publication(
                existing.id(),
                existing.boardId(),
                existing.snapshotVersion(),
                existing.targetType(),
                existing.state(),
                newTokenHash,
                existing.createdByUserId(),
                existing.allowComments(),
                existing.createdAt(),
                Instant.now(),
                existing.expiresAt(),
                existing.revokedAt()
        ));
        return Optional.ofNullable(updated);
    }

    public void clear() {
        publications.clear();
    }
}
