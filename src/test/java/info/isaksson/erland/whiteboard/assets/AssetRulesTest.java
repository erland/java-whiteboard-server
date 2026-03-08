package info.isaksson.erland.whiteboard.assets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class AssetRulesTest {

    @Test
    void accepts_valid_asset_metadata() {
        assertDoesNotThrow(() -> AssetRules.validateNewAsset(
                AssetScopeType.BOARD,
                "board-1",
                "diagram.png",
                "image/png",
                1024L,
                "alice",
                "sha256:abc",
                "v1"
        ));
    }

    @Test
    void rejects_blank_logical_name() {
        assertThrows(IllegalArgumentException.class, () -> AssetRules.validateNewAsset(
                AssetScopeType.BOARD,
                "board-1",
                " ",
                "image/png",
                10L,
                "alice",
                null,
                null
        ));
    }

    @Test
    void rejects_too_large_asset_size() {
        assertThrows(IllegalArgumentException.class, () -> AssetRules.validateNewAsset(
                AssetScopeType.BOARD,
                "board-1",
                "large.bin",
                "application/octet-stream",
                AssetRules.MAX_ASSET_SIZE_BYTES + 1,
                "alice",
                null,
                null
        ));
    }

    @Test
    void rejects_invalid_transition_from_deleted() {
        Asset deleted = new Asset(
                "asset-1",
                "board-1",
                AssetScopeType.BOARD,
                "board-1",
                "diagram.png",
                "image/png",
                100L,
                null,
                null,
                AssetState.DELETED,
                "alice",
                null,
                null,
                null,
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> AssetRules.requireTransition(deleted, AssetState.ACTIVE));
    }
}
