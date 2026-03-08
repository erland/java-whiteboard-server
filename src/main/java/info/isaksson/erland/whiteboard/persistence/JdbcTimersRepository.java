package info.isaksson.erland.whiteboard.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.timer.SharedTimer;
import info.isaksson.erland.whiteboard.timer.SharedTimerScopeType;
import info.isaksson.erland.whiteboard.timer.SharedTimerState;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@UnlessBuildProfile("test")
public class JdbcTimersRepository implements TimersRepository {

    @Inject
    AgroalDataSource dataSource;

    @Override
    public SharedTimer create(SharedTimer timer) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO shared_timers (id, board_id, scope_type, scope_ref, controller_user_id, label, state, duration_ms, remaining_ms, started_at, ends_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, timer.id());
            ps.setString(2, timer.boardId());
            ps.setString(3, timer.scopeType().storageValue());
            JdbcSupport.setNullableString(ps, 4, timer.scopeRef());
            ps.setString(5, timer.controllerUserId());
            JdbcSupport.setNullableString(ps, 6, timer.label());
            ps.setString(7, timer.state().storageValue());
            ps.setLong(8, timer.durationMs());
            ps.setLong(9, timer.remainingMs());
            JdbcSupport.setNullableTimestamp(ps, 10, timer.startedAt());
            JdbcSupport.setNullableTimestamp(ps, 11, timer.endsAt());
            JdbcSupport.setNullableTimestamp(ps, 12, timer.createdAt());
            JdbcSupport.setNullableTimestamp(ps, 13, timer.updatedAt());
            ps.executeUpdate();
            return findById(timer.id()).orElseThrow(() -> new IllegalStateException("Inserted timer not found"));
        } catch (Exception e) {
            throw JdbcSupport.failure("create shared timer", e);
        }
    }

    @Override
    public Optional<SharedTimer> update(SharedTimer timer) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE shared_timers SET scope_type = ?, scope_ref = ?, controller_user_id = ?, label = ?, state = ?, duration_ms = ?, remaining_ms = ?, started_at = ?, ends_at = ?, updated_at = ? WHERE id = ? RETURNING id, board_id, scope_type, scope_ref, controller_user_id, label, state, duration_ms, remaining_ms, started_at, ends_at, created_at, updated_at")) {
            ps.setString(1, timer.scopeType().storageValue());
            JdbcSupport.setNullableString(ps, 2, timer.scopeRef());
            ps.setString(3, timer.controllerUserId());
            JdbcSupport.setNullableString(ps, 4, timer.label());
            ps.setString(5, timer.state().storageValue());
            ps.setLong(6, timer.durationMs());
            ps.setLong(7, timer.remainingMs());
            JdbcSupport.setNullableTimestamp(ps, 8, timer.startedAt());
            JdbcSupport.setNullableTimestamp(ps, 9, timer.endsAt());
            JdbcSupport.setNullableTimestamp(ps, 10, timer.updatedAt());
            ps.setString(11, timer.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("update shared timer", e);
        }
    }

    @Override
    public Optional<SharedTimer> findById(String timerId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, scope_type, scope_ref, controller_user_id, label, state, duration_ms, remaining_ms, started_at, ends_at, created_at, updated_at FROM shared_timers WHERE id = ?")) {
            ps.setString(1, timerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("find shared timer", e);
        }
    }

    @Override
    public Optional<SharedTimer> findActiveForBoard(String boardId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, scope_type, scope_ref, controller_user_id, label, state, duration_ms, remaining_ms, started_at, ends_at, created_at, updated_at FROM shared_timers WHERE board_id = ? AND state IN ('running', 'paused') ORDER BY updated_at DESC, created_at DESC LIMIT 1")) {
            ps.setString(1, boardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("find active shared timer", e);
        }
    }

    @Override
    public List<SharedTimer> listForBoard(String boardId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, scope_type, scope_ref, controller_user_id, label, state, duration_ms, remaining_ms, started_at, ends_at, created_at, updated_at FROM shared_timers WHERE board_id = ? ORDER BY created_at ASC, id ASC")) {
            ps.setString(1, boardId);
            try (ResultSet rs = ps.executeQuery()) {
                List<SharedTimer> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("list shared timers", e);
        }
    }

    private static SharedTimer map(ResultSet rs) throws Exception {
        return new SharedTimer(
                rs.getString("id"),
                rs.getString("board_id"),
                SharedTimerScopeType.fromStorageValue(rs.getString("scope_type")),
                rs.getString("scope_ref"),
                rs.getString("controller_user_id"),
                rs.getString("label"),
                SharedTimerState.fromStorageValue(rs.getString("state")),
                rs.getLong("duration_ms"),
                rs.getLong("remaining_ms"),
                JdbcSupport.getInstant(rs, "started_at"),
                JdbcSupport.getInstant(rs, "ends_at"),
                JdbcSupport.getInstant(rs, "created_at"),
                JdbcSupport.getInstant(rs, "updated_at")
        );
    }
}
