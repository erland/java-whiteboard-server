package info.isaksson.erland.whiteboard.api;

import java.util.List;

import info.isaksson.erland.whiteboard.api.dto.ActivateAssetRequest;
import info.isaksson.erland.whiteboard.api.dto.AssetFailureRequest;
import info.isaksson.erland.whiteboard.api.dto.CreateAssetRequest;
import info.isaksson.erland.whiteboard.assets.Asset;
import info.isaksson.erland.whiteboard.assets.AssetService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class AssetApplicationService {

    private final AssetService assetService;
    private final AssetAccessResolver accessResolver;
    private final AssetRequestSupport requestSupport;

    @Inject
    public AssetApplicationService(AssetService assetService,
                                   AssetAccessResolver accessResolver,
                                   AssetRequestSupport requestSupport) {
        this.assetService = assetService;
        this.accessResolver = accessResolver;
        this.requestSupport = requestSupport;
    }

    public List<Asset> listAssets(String boardId, String publicationToken) {
        accessResolver.requireAssetListAccess(boardId, publicationToken);
        return assetService.listForBoard(boardId);
    }

    public Asset createAsset(String boardId, CreateAssetRequest req) {
        String userId = accessResolver.requireAssetManagerUserId(boardId);
        return assetService.createBoardAssetMetadata(
                boardId,
                requestSupport.logicalName(req),
                requestSupport.contentType(req),
                requestSupport.sizeBytes(req),
                userId,
                requestSupport.integrityHash(req),
                requestSupport.versionTag(req));
    }

    public Asset activateAsset(String boardId, String assetId, ActivateAssetRequest req) {
        accessResolver.requireAssetManagerUserId(boardId);
        accessResolver.requireAssetForBoard(boardId, assetId);
        return assetService.markActive(assetId, requestSupport.activatedVersionTag(req))
                .orElseThrow(NotFoundException::new);
    }

    public Asset failAsset(String boardId, String assetId, AssetFailureRequest req) {
        accessResolver.requireAssetManagerUserId(boardId);
        accessResolver.requireAssetForBoard(boardId, assetId);
        return assetService.markFailed(assetId, requestSupport.requireFailureReason(req))
                .orElseThrow(NotFoundException::new);
    }

    public Asset quarantineAsset(String boardId, String assetId, AssetFailureRequest req) {
        accessResolver.requireAssetManagerUserId(boardId);
        accessResolver.requireAssetForBoard(boardId, assetId);
        return assetService.quarantine(assetId, requestSupport.requireFailureReason(req))
                .orElseThrow(NotFoundException::new);
    }

    public Asset deleteAsset(String boardId, String assetId) {
        accessResolver.requireAssetManagerUserId(boardId);
        accessResolver.requireAssetForBoard(boardId, assetId);
        return assetService.delete(assetId).orElseThrow(NotFoundException::new);
    }
}
