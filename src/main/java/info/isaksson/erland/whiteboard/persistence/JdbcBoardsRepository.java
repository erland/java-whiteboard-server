package info.isaksson.erland.whiteboard.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import info.isaksson.erland.whiteboard.domain.Board;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.UnlessBuildProfile;

@ApplicationScoped
@UnlessBuildProfile("test")
public class JdbcBoardsRepository implements BoardsRepository {

    @Inject
    AgroalDataSource dataSource;

    @Override
    public Board create(Board board) {
        // status/created_at/updated_at are set by DB defaults
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO boards (id, name, type, owner_user_id, status) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, board.id());
                ps.setString(2, board.name());
                ps.setString(3, board.type());
                ps.setString(4, board.ownerUserId());
                ps.setString(5, board.status());
                ps.executeUpdate();
            }
            return findById(board.id()).orElseThrow(() -> new IllegalStateException("Inserted board not found"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create board", e);
        }
    }

    @Override
    public List<Board> listForOwner(String ownerUserId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, type, owner_user_id, status, created_at, updated_at " +
                     "FROM boards WHERE owner_user_id = ? AND status <> 'deleted' ORDER BY updated_at DESC")) {
            ps.setString(1, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Board> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list boards", e);
        }
    }

    @Override
    public Optional<Board> findById(String id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, type, owner_user_id, status, created_at, updated_at FROM boards WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find board", e);
        }
    }

    @Override
    public Board updateMetadata(String id, String ownerUserId, String name, String type) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE boards SET name = ?, type = ?, updated_at = now() " +
                    "WHERE id = ? AND owner_user_id = ? AND status = 'active'")) {
                ps.setString(1, name);
                ps.setString(2, type);
                ps.setString(3, id);
                ps.setString(4, ownerUserId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    // Could be missing OR not owned OR not active; caller decides how to handle.
                    return null;
                }
            }
            return findById(id).orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update board", e);
        }
    }

    @Override
    public boolean archive(String id, String ownerUserId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE boards SET status = 'archived', updated_at = now() " +
                     "WHERE id = ? AND owner_user_id = ? AND status <> 'deleted'")) {
            ps.setString(1, id);
            ps.setString(2, ownerUserId);
            int updated = ps.executeUpdate();
            return updated > 0;
        } catch (Exception e) {
            throw new RuntimeException("Failed to archive board", e);
        }
    }

    private static Board map(ResultSet rs) throws Exception {
        Instant createdAt = toInstant(rs.getTimestamp("created_at"));
        Instant updatedAt = toInstant(rs.getTimestamp("updated_at"));
        return new Board(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("owner_user_id"),
                rs.getString("status"),
                createdAt,
                updatedAt
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
