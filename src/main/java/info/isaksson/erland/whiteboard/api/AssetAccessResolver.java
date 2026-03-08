package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.assets.Asset;
import info.isaksson.erland.whiteboard.assets.AssetService;
import info.isaksson.erland.whiteboard.publication.Publication;
import info.isaksson.erland.whiteboard.publication.PublicationPolicy;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class AssetAccessResolver {

    private final BoardGuards boardGuards;
    private final SecurityIdentity identity;
    private final PublicationPolicy publicationPolicy;
    private final AssetService assetService;

    @Inject
    public AssetAccessResolver(BoardGuards boardGuards,
                               SecurityIdentity identity,
                               PublicationPolicy publicationPolicy,
                               AssetService assetService) {
        this.boardGuards = boardGuards;
        this.identity = identity;
        this.publicationPolicy = publicationPolicy;
        this.assetService = assetService;
    }

    public void requireAssetListAccess(String boardId, String publicationToken) {
        Publication publication = resolveReadablePublication(boardId, publicationToken);
        if (identity != null && !identity.isAnonymous()) {
            String userId = Authz.userId(identity);
            boardGuards.requireAssetUseAccess(boardId, userId, publication != null);
            return;
        }
        if (publication == null) {
            throw new NotFoundException();
        }
    }

    public String requireAssetManagerUserId(String boardId) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireAssetManageAccess(boardId, userId);
        return userId;
    }

    public Asset requireAssetForBoard(String boardId, String assetId) {
        Asset asset = assetService.findById(assetId).orElseThrow(NotFoundException::new);
        if (!boardId.equals(asset.boardId())) {
            throw new NotFoundException();
        }
        return asset;
    }

    private Publication resolveReadablePublication(String boardId, String publicationToken) {
        PublicationPolicy.Decision decision = publicationPolicy.validateToken(publicationToken);
        if (!decision.valid()) {
            return null;
        }
        Publication publication = decision.publication();
        if (publication == null || !boardId.equals(publication.boardId())) {
            return null;
        }
        return publication;
    }
}
