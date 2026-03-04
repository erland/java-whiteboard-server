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

import org.postgresql.util.PGobject;

import info.isaksson.erland.whiteboard.domain.BoardSnapshot;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.UnlessBuildProfile;

@ApplicationScoped
@UnlessBuildProfile("test")
public class JdbcSnapshotsRepository implements SnapshotsRepository {

    @Inject
    AgroalDataSource dataSource;

    @Override
    public BoardSnapshot create(String boardId, String createdBy, String snapshotJson) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                long nextVersion;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE(MAX(version), 0) + 1 AS v FROM board_snapshots WHERE board_id = ?")) {
                    ps.setString(1, boardId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        nextVersion = rs.getLong("v");
                    }
                }

                PGobject jsonb = new PGobject();
                jsonb.setType("jsonb");
                jsonb.setValue(snapshotJson);

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO board_snapshots (board_id, version, snapshot, created_by) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, boardId);
                    ps.setLong(2, nextVersion);
                    ps.setObject(3, jsonb);
                    if (createdBy == null) ps.setNull(4, java.sql.Types.VARCHAR);
                    else ps.setString(4, createdBy);
                    ps.executeUpdate();
                }

                // Upsert latest pointer
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO board_snapshot_latest (board_id, latest_version) VALUES (?, ?) " +
                        "ON CONFLICT (board_id) DO UPDATE SET latest_version = EXCLUDED.latest_version, updated_at = now()")) {
                    ps.setString(1, boardId);
                    ps.setLong(2, nextVersion);
                    ps.executeUpdate();
                }

                c.commit();
                return get(boardId, nextVersion).orElseThrow(() -> new IllegalStateException("Inserted snapshot not found"));
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create snapshot", e);
        }
    }

    @Override
    public Optional<BoardSnapshot> get(String boardId, long version) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT board_id, version, snapshot::text AS snapshot_json, created_at, created_by " +
                     "FROM board_snapshots WHERE board_id = ? AND version = ?")) {
            ps.setString(1, boardId);
            ps.setLong(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get snapshot", e);
        }
    }

    @Override
    public Optional<BoardSnapshot> getLatest(String boardId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT s.board_id, s.version, s.snapshot::text AS snapshot_json, s.created_at, s.created_by " +
                     "FROM board_snapshot_latest l JOIN board_snapshots s ON s.board_id = l.board_id AND s.version = l.latest_version " +
                     "WHERE l.board_id = ?")) {
            ps.setString(1, boardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get latest snapshot", e);
        }
    }

    @Override
    public List<Long> listVersions(String boardId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT version FROM board_snapshots WHERE board_id = ? ORDER BY version DESC")) {
            ps.setString(1, boardId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> out = new ArrayList<>();
                while (rs.next()) out.add(rs.getLong("version"));
                return out;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list snapshot versions", e);
        }
    }

    private static BoardSnapshot map(ResultSet rs) throws Exception {
        return new BoardSnapshot(
                rs.getString("board_id"),
                rs.getLong("version"),
                rs.getString("snapshot_json"),
                toInstant(rs.getTimestamp("created_at")),
                rs.getString("created_by")
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
