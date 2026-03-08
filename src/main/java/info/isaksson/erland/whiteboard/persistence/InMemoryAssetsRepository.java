package info.isaksson.erland.whiteboard.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import info.isaksson.erland.whiteboard.assets.Asset;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.arc.profile.IfBuildProfile;

@ApplicationScoped
@IfBuildProfile("test")
@Priority(1)
public class InMemoryAssetsRepository implements AssetsRepository {

    private final ConcurrentHashMap<String, Asset> assets = new ConcurrentHashMap<>();

    @Override
    public Asset create(Asset asset) {
        Instant now = Instant.now();
        Asset created = new Asset(
                asset.id(),
                asset.boardId(),
                asset.scopeType(),
                asset.scopeRef(),
                asset.logicalName(),
                asset.contentType(),
                asset.sizeBytes(),
                asset.integrityHash(),
                asset.versionTag(),
                asset.state(),
                asset.createdByUserId(),
                now,
                now,
                asset.activatedAt(),
                asset.deletedAt(),
                asset.failureReason()
        );
        assets.put(created.id(), created);
        return created;
    }

    @Override
    public Optional<Asset> findById(String assetId) {
        return Optional.ofNullable(assets.get(assetId));
    }

    @Override
    public List<Asset> listForBoard(String boardId) {
        return assets.values().stream()
                .filter(asset -> boardId.equals(asset.boardId()))
                .sorted(Comparator.comparing(Asset::createdAt).thenComparing(Asset::id))
                .toList();
    }

    @Override
    public Optional<Asset> update(Asset asset) {
        Asset updated = assets.computeIfPresent(asset.id(), (id, existing) -> asset);
        return Optional.ofNullable(updated);
    }

    public void clear() {
        assets.clear();
    }
}
