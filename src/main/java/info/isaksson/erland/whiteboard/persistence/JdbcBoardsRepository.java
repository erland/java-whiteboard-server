package info.isaksson.erland.whiteboard.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO boards (id, name, type, board_type, owner_user_id, status) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, board.id());
                ps.setString(2, board.name());
                ps.setString(3, board.type());
                ps.setString(4, board.boardType());
                ps.setString(5, board.ownerUserId());
                ps.setString(6, board.status());
                ps.executeUpdate();
            }
            return findById(board.id()).orElseThrow(() -> new IllegalStateException("Inserted board not found"));
        } catch (Exception e) {
            throw JdbcSupport.failure("create board", e);
        }
    }

    @Override
    public List<Board> listForOwner(String ownerUserId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, type, board_type, owner_user_id, status, created_at, updated_at " +
                     "FROM boards WHERE owner_user_id = ? AND status = 'active' ORDER BY updated_at DESC")) {
            ps.setString(1, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Board> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("list boards", e);
        }
    }

    @Override
    public Optional<Board> findById(String id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, type, board_type, owner_user_id, status, created_at, updated_at FROM boards WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("find board", e);
        }
    }

    @Override
    public Board updateMetadata(String id, String ownerUserId, String name, String type, String boardType) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE boards SET name = ?, type = ?, board_type = ?, updated_at = now() " +
                    "WHERE id = ? AND owner_user_id = ? AND status = 'active'")) {
                ps.setString(1, name);
                ps.setString(2, type);
                ps.setString(3, boardType);
                ps.setString(4, id);
                ps.setString(5, ownerUserId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    return null;
                }
            }
            return findById(id).orElse(null);
        } catch (Exception e) {
            throw JdbcSupport.failure("update board", e);
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
            throw JdbcSupport.failure("archive board", e);
        }
    }

    private static Board map(ResultSet rs) throws Exception {
        Instant createdAt = JdbcSupport.getInstant(rs, "created_at");
        Instant updatedAt = JdbcSupport.getInstant(rs, "updated_at");
        return new Board(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("board_type"),
                rs.getString("owner_user_id"),
                rs.getString("status"),
                createdAt,
                updatedAt
        );
    }

}
