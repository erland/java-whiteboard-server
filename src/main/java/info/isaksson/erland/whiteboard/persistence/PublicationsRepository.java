package info.isaksson.erland.whiteboard.persistence;

import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.publication.Publication;

public interface PublicationsRepository {

    Publication create(Publication publication);

    Optional<Publication> findById(String publicationId);

    Optional<Publication> findByTokenHash(String tokenHash);

    List<Publication> listForBoard(String boardId);

    Optional<Publication> revoke(String publicationId);

    Optional<Publication> rotateAccessToken(String publicationId, String newTokenHash);
}
