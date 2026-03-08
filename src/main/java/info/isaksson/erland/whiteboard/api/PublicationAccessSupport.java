package info.isaksson.erland.whiteboard.api;

import java.util.function.Predicate;

import info.isaksson.erland.whiteboard.publication.Publication;
import info.isaksson.erland.whiteboard.publication.PublicationPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PublicationAccessSupport {

    private final PublicationPolicy publicationPolicy;

    @Inject
    public PublicationAccessSupport(PublicationPolicy publicationPolicy) {
        this.publicationPolicy = publicationPolicy;
    }

    public Publication resolveReadablePublication(String boardId, String publicationToken) {
        return resolveReadablePublication(boardId, publicationToken, publication -> true);
    }

    public Publication resolveReadablePublication(String boardId, String publicationToken, Predicate<Publication> publicationFilter) {
        PublicationPolicy.Decision decision = publicationPolicy.validateToken(publicationToken);
        if (!decision.valid() || decision.publication() == null) {
            return null;
        }
        Publication publication = decision.publication();
        if (!boardId.equals(publication.boardId()) || !publicationFilter.test(publication)) {
            return null;
        }
        return publication;
    }
}
