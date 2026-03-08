package info.isaksson.erland.whiteboard.persistence;

import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.assets.Asset;

public interface AssetsRepository {

    Asset create(Asset asset);

    Optional<Asset> findById(String assetId);

    List<Asset> listForBoard(String boardId);

    Optional<Asset> update(Asset asset);
}
