package info.isaksson.erland.whiteboard.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.assets.Asset;
import info.isaksson.erland.whiteboard.assets.AssetScopeType;
import info.isaksson.erland.whiteboard.assets.AssetState;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@UnlessBuildProfile("test")
public class JdbcAssetsRepository implements AssetsRepository {

    @Inject
    AgroalDataSource dataSource;

    @Override
    public Asset create(Asset asset) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO assets (id, board_id, scope_type, scope_ref, logical_name, content_type, size_bytes, integrity_hash, version_tag, state, created_by_user_id, activated_at, deleted_at, failure_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, asset.id());
                JdbcSupport.setNullableString(ps, 2, asset.boardId());
                ps.setString(3, asset.scopeType().storageValue());
                ps.setString(4, asset.scopeRef());
                ps.setString(5, asset.logicalName());
                ps.setString(6, asset.contentType());
                ps.setLong(7, asset.sizeBytes());
                JdbcSupport.setNullableString(ps, 8, asset.integrityHash());
                JdbcSupport.setNullableString(ps, 9, asset.versionTag());
                ps.setString(10, asset.state().storageValue());
                ps.setString(11, asset.createdByUserId());
                JdbcSupport.setNullableTimestamp(ps, 12, asset.activatedAt());
                JdbcSupport.setNullableTimestamp(ps, 13, asset.deletedAt());
                JdbcSupport.setNullableString(ps, 14, asset.failureReason());
                ps.executeUpdate();
            }
            return findById(asset.id()).orElseThrow(() -> new IllegalStateException("Inserted asset not found"));
        } catch (Exception e) {
            throw JdbcSupport.failure("create asset", e);
        }
    }

    @Override
    public Optional<Asset> findById(String assetId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, scope_type, scope_ref, logical_name, content_type, size_bytes, integrity_hash, version_tag, state, created_by_user_id, created_at, updated_at, activated_at, deleted_at, failure_reason FROM assets WHERE id = ?")) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("find asset", e);
        }
    }

    @Override
    public List<Asset> listForBoard(String boardId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, scope_type, scope_ref, logical_name, content_type, size_bytes, integrity_hash, version_tag, state, created_by_user_id, created_at, updated_at, activated_at, deleted_at, failure_reason FROM assets WHERE board_id = ? ORDER BY created_at ASC, id ASC")) {
            ps.setString(1, boardId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Asset> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("list assets", e);
        }
    }

    @Override
    public Optional<Asset> update(Asset asset) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE assets SET board_id = ?, scope_type = ?, scope_ref = ?, logical_name = ?, content_type = ?, size_bytes = ?, integrity_hash = ?, version_tag = ?, state = ?, updated_at = ?, activated_at = ?, deleted_at = ?, failure_reason = ? WHERE id = ? RETURNING id, board_id, scope_type, scope_ref, logical_name, content_type, size_bytes, integrity_hash, version_tag, state, created_by_user_id, created_at, updated_at, activated_at, deleted_at, failure_reason")) {
            JdbcSupport.setNullableString(ps, 1, asset.boardId());
            ps.setString(2, asset.scopeType().storageValue());
            ps.setString(3, asset.scopeRef());
            ps.setString(4, asset.logicalName());
            ps.setString(5, asset.contentType());
            ps.setLong(6, asset.sizeBytes());
            JdbcSupport.setNullableString(ps, 7, asset.integrityHash());
            JdbcSupport.setNullableString(ps, 8, asset.versionTag());
            ps.setString(9, asset.state().storageValue());
            JdbcSupport.setNullableTimestamp(ps, 10, asset.updatedAt());
            JdbcSupport.setNullableTimestamp(ps, 11, asset.activatedAt());
            JdbcSupport.setNullableTimestamp(ps, 12, asset.deletedAt());
            JdbcSupport.setNullableString(ps, 13, asset.failureReason());
            ps.setString(14, asset.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("update asset", e);
        }
    }

    private static Asset map(ResultSet rs) throws Exception {
        return new Asset(
                rs.getString("id"),
                rs.getString("board_id"),
                AssetScopeType.fromStorageValue(rs.getString("scope_type")),
                rs.getString("scope_ref"),
                rs.getString("logical_name"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("integrity_hash"),
                rs.getString("version_tag"),
                AssetState.fromStorageValue(rs.getString("state")),
                rs.getString("created_by_user_id"),
                JdbcSupport.getInstant(rs, "created_at"),
                JdbcSupport.getInstant(rs, "updated_at"),
                JdbcSupport.getInstant(rs, "activated_at"),
                JdbcSupport.getInstant(rs, "deleted_at"),
                rs.getString("failure_reason")
        );
    }
}
