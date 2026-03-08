package info.isaksson.erland.whiteboard.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.publication.Publication;
import info.isaksson.erland.whiteboard.publication.PublicationState;
import info.isaksson.erland.whiteboard.publication.PublicationTargetType;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@UnlessBuildProfile("test")
public class JdbcPublicationsRepository implements PublicationsRepository {

    @Inject
    AgroalDataSource dataSource;

    @Override
    public Publication create(Publication publication) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO publications (id, board_id, snapshot_version, target_type, state, access_token_hash, created_by_user_id, allow_comments, expires_at, revoked_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, publication.id());
                ps.setString(2, publication.boardId());
                JdbcSupport.setNullableLong(ps, 3, publication.snapshotVersion());
                ps.setString(4, publication.targetType().storageValue());
                ps.setString(5, publication.state().storageValue());
                ps.setString(6, publication.accessTokenHash());
                ps.setString(7, publication.createdByUserId());
                ps.setBoolean(8, publication.allowComments());
                JdbcSupport.setNullableTimestamp(ps, 9, publication.expiresAt());
                JdbcSupport.setNullableTimestamp(ps, 10, publication.revokedAt());
                ps.executeUpdate();
            }
            return findById(publication.id())
                    .orElseThrow(() -> new IllegalStateException("Inserted publication not found"));
        } catch (Exception e) {
            throw JdbcSupport.failure("create publication", e);
        }
    }

    @Override
    public Optional<Publication> findById(String publicationId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, snapshot_version, target_type, state, access_token_hash, created_by_user_id, allow_comments, created_at, updated_at, expires_at, revoked_at FROM publications WHERE id = ?")) {
            ps.setString(1, publicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("find publication", e);
        }
    }

    @Override
    public Optional<Publication> findByTokenHash(String tokenHash) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, snapshot_version, target_type, state, access_token_hash, created_by_user_id, allow_comments, created_at, updated_at, expires_at, revoked_at FROM publications WHERE access_token_hash = ?")) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("find publication by token hash", e);
        }
    }

    @Override
    public List<Publication> listForBoard(String boardId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, snapshot_version, target_type, state, access_token_hash, created_by_user_id, allow_comments, created_at, updated_at, expires_at, revoked_at FROM publications WHERE board_id = ? ORDER BY updated_at DESC, created_at DESC")) {
            ps.setString(1, boardId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Publication> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("list publications", e);
        }
    }

    @Override
    public Optional<Publication> revoke(String publicationId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE publications SET state = 'revoked', revoked_at = COALESCE(revoked_at, now()), updated_at = now() WHERE id = ? RETURNING id, board_id, snapshot_version, target_type, state, access_token_hash, created_by_user_id, allow_comments, created_at, updated_at, expires_at, revoked_at")) {
            ps.setString(1, publicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("revoke publication", e);
        }
    }

    @Override
    public Optional<Publication> rotateAccessToken(String publicationId, String newTokenHash) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE publications SET access_token_hash = ?, updated_at = now() WHERE id = ? RETURNING id, board_id, snapshot_version, target_type, state, access_token_hash, created_by_user_id, allow_comments, created_at, updated_at, expires_at, revoked_at")) {
            ps.setString(1, newTokenHash);
            ps.setString(2, publicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("rotate publication access token", e);
        }
    }

    private static Publication map(ResultSet rs) throws Exception {
        return new Publication(
                rs.getString("id"),
                rs.getString("board_id"),
                (Long) rs.getObject("snapshot_version"),
                PublicationTargetType.fromStorageValue(rs.getString("target_type")),
                PublicationState.fromStorageValue(rs.getString("state")),
                rs.getString("access_token_hash"),
                rs.getString("created_by_user_id"),
                rs.getBoolean("allow_comments"),
                JdbcSupport.getInstant(rs, "created_at"),
                JdbcSupport.getInstant(rs, "updated_at"),
                JdbcSupport.getInstant(rs, "expires_at"),
                JdbcSupport.getInstant(rs, "revoked_at")
        );
    }
}
