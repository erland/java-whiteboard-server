package info.isaksson.erland.whiteboard.persistence;

import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@UnlessBuildProfile("test")
public class JdbcBoardPermissionsRepository implements BoardPermissionsRepository {

    private final DataSource dataSource;

    @Inject
    public JdbcBoardPermissionsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void upsert(String boardId, String userId, String role) {
        // board_permissions: (board_id, user_id, role, created_at, updated_at)
        final String sql = """
                INSERT INTO board_permissions (board_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                ON CONFLICT (board_id, user_id)
                DO UPDATE SET role = EXCLUDED.role, updated_at = now()
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, boardId);
            ps.setString(2, userId);
            ps.setString(3, role);
            ps.executeUpdate();
        } catch (Exception e) {
            Log.error("Failed to upsert board permission", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<String> findRole(String boardId, String userId) {
        final String sql = "SELECT role FROM board_permissions WHERE board_id = ? AND user_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, boardId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.ofNullable(rs.getString("role"));
            }
        } catch (Exception e) {
            Log.error("Failed to find board role", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> listBoardIdsForUser(String userId) {
        final String sql = "SELECT board_id FROM board_permissions WHERE user_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(rs.getString("board_id"));
                }
                return out;
            }
        } catch (Exception e) {
            Log.error("Failed to list board ids for user", e);
            throw new RuntimeException(e);
        }
    }
}
