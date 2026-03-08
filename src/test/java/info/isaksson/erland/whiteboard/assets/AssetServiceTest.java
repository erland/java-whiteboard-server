package info.isaksson.erland.whiteboard.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.InMemoryAssetsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;

public class AssetServiceTest {

    private InMemoryAssetsRepository assetsRepository;
    private InMemoryBoardsRepository boardsRepository;
    private AssetService assetService;
    private String boardId;

    @BeforeEach
    void setup() {
        assetsRepository = new InMemoryAssetsRepository();
        boardsRepository = new InMemoryBoardsRepository();
        assetService = new AssetService(assetsRepository, boardsRepository);
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Board", "whiteboard", "advanced", "alice", "active", null, null));
    }

    @Test
    void creates_board_asset_metadata_with_pending_state() {
        Asset asset = assetService.createBoardAssetMetadata(boardId, "diagram.png", "image/png", 128L, "alice", "sha256:abc", "v1");

        assertEquals(AssetScopeType.BOARD, asset.scopeType());
        assertEquals(boardId, asset.scopeRef());
        assertEquals(AssetState.PENDING, asset.state());
        assertNotNull(asset.createdAt());
        assertNotNull(asset.updatedAt());
    }

    @Test
    void can_activate_and_then_delete_asset() {
        Asset created = assetService.createBoardAssetMetadata(boardId, "diagram.png", "image/png", 128L, "alice", null, null);

        Asset activated = assetService.markActive(created.id(), "v2").orElseThrow();
        assertEquals(AssetState.ACTIVE, activated.state());
        assertEquals("v2", activated.versionTag());
        assertNotNull(activated.activatedAt());

        Asset deleted = assetService.delete(created.id()).orElseThrow();
        assertEquals(AssetState.DELETED, deleted.state());
        assertNotNull(deleted.deletedAt());
    }

    @Test
    void failed_asset_requires_reason() {
        Asset created = assetService.createBoardAssetMetadata(boardId, "diagram.png", "image/png", 128L, "alice", null, null);

        assertThrows(IllegalArgumentException.class, () -> assetService.markFailed(created.id(), " "));
    }

    @Test
    void board_listing_returns_only_assets_for_board() {
        assetService.createBoardAssetMetadata(boardId, "a.png", "image/png", 10L, "alice", null, null);
        assetService.createUserPrivateAssetMetadata("alice", "private.txt", "text/plain", 10L, "alice", null, null);

        var assets = assetService.listForBoard(boardId);

        assertEquals(1, assets.size());
        assertTrue(assets.stream().allMatch(asset -> boardId.equals(asset.boardId())));
    }

    @Test
    void rejects_board_asset_for_missing_board() {
        assertThrows(IllegalArgumentException.class, () -> assetService.createBoardAssetMetadata("missing", "a.png", "image/png", 10L, "alice", null, null));
    }
}
