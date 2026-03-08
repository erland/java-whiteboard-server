package info.isaksson.erland.whiteboard.assets;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import info.isaksson.erland.whiteboard.persistence.AssetsRepository;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AssetService {

    private final AssetsRepository assetsRepository;
    private final BoardsRepository boardsRepository;

    @Inject
    public AssetService(AssetsRepository assetsRepository,
                        BoardsRepository boardsRepository) {
        this.assetsRepository = assetsRepository;
        this.boardsRepository = boardsRepository;
    }

    public Asset createBoardAssetMetadata(String boardId,
                                          String logicalName,
                                          String contentType,
                                          long sizeBytes,
                                          String createdByUserId,
                                          String integrityHash,
                                          String versionTag) {
        requireActiveBoard(boardId);
        return createAsset(boardId, AssetScopeType.BOARD, boardId, logicalName, contentType, sizeBytes, createdByUserId, integrityHash, versionTag);
    }

    public Asset createUserPrivateAssetMetadata(String userId,
                                                String logicalName,
                                                String contentType,
                                                long sizeBytes,
                                                String createdByUserId,
                                                String integrityHash,
                                                String versionTag) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User scope reference is required");
        }
        return createAsset(null, AssetScopeType.USER_PRIVATE, userId, logicalName, contentType, sizeBytes, createdByUserId, integrityHash, versionTag);
    }

    public Asset createLibraryAssetMetadata(String libraryId,
                                            String logicalName,
                                            String contentType,
                                            long sizeBytes,
                                            String createdByUserId,
                                            String integrityHash,
                                            String versionTag) {
        if (libraryId == null || libraryId.isBlank()) {
            throw new IllegalArgumentException("Library scope reference is required");
        }
        return createAsset(null, AssetScopeType.LIBRARY, libraryId, logicalName, contentType, sizeBytes, createdByUserId, integrityHash, versionTag);
    }

    public Optional<Asset> markActive(String assetId, String versionTag) {
        return assetsRepository.findById(assetId)
                .map(existing -> transition(existing, AssetState.ACTIVE, null, versionTag));
    }

    public Optional<Asset> markFailed(String assetId, String failureReason) {
        AssetRules.requireFailureReason(failureReason);
        return assetsRepository.findById(assetId)
                .map(existing -> transition(existing, AssetState.FAILED, failureReason, existing.versionTag()));
    }

    public Optional<Asset> quarantine(String assetId, String failureReason) {
        AssetRules.requireFailureReason(failureReason);
        return assetsRepository.findById(assetId)
                .map(existing -> transition(existing, AssetState.QUARANTINED, failureReason, existing.versionTag()));
    }

    public Optional<Asset> delete(String assetId) {
        return assetsRepository.findById(assetId)
                .map(existing -> transition(existing, AssetState.DELETED, existing.failureReason(), existing.versionTag()));
    }

    public Optional<Asset> findById(String assetId) {
        return assetsRepository.findById(assetId);
    }

    public List<Asset> listForBoard(String boardId) {
        return assetsRepository.listForBoard(boardId);
    }

    private Asset createAsset(String boardId,
                              AssetScopeType scopeType,
                              String scopeRef,
                              String logicalName,
                              String contentType,
                              long sizeBytes,
                              String createdByUserId,
                              String integrityHash,
                              String versionTag) {
        AssetRules.validateNewAsset(scopeType, scopeRef, logicalName, contentType, sizeBytes, createdByUserId, integrityHash, versionTag);
        return assetsRepository.create(new Asset(
                UUID.randomUUID().toString(),
                boardId,
                scopeType,
                scopeRef,
                logicalName,
                contentType,
                sizeBytes,
                integrityHash,
                versionTag,
                AssetState.PENDING,
                createdByUserId,
                null,
                null,
                null,
                null,
                null
        ));
    }

    private Asset transition(Asset existing,
                             AssetState nextState,
                             String failureReason,
                             String versionTag) {
        AssetRules.requireTransition(existing, nextState);
        Instant now = Instant.now();
        return assetsRepository.update(new Asset(
                existing.id(),
                existing.boardId(),
                existing.scopeType(),
                existing.scopeRef(),
                existing.logicalName(),
                existing.contentType(),
                existing.sizeBytes(),
                existing.integrityHash(),
                versionTag,
                nextState,
                existing.createdByUserId(),
                existing.createdAt(),
                now,
                nextState == AssetState.ACTIVE ? now : existing.activatedAt(),
                nextState == AssetState.DELETED ? now : existing.deletedAt(),
                nextState == AssetState.FAILED || nextState == AssetState.QUARANTINED ? failureReason : null
        )).orElseThrow(() -> new IllegalStateException("Updated asset not found"));
    }

    private void requireActiveBoard(String boardId) {
        boardsRepository.findById(boardId)
                .filter(board -> "active".equals(board.status()))
                .orElseThrow(() -> new IllegalArgumentException("Board not found or not active"));
    }
}
