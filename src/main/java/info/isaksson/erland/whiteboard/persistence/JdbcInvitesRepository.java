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

import info.isaksson.erland.whiteboard.domain.Invite;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.UnlessBuildProfile;

@ApplicationScoped
@UnlessBuildProfile("test")
public class JdbcInvitesRepository implements InvitesRepository {

    @Inject
    AgroalDataSource dataSource;

    @Override
    public Invite create(Invite invite) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO invites (id, token_hash, board_id, permission, expires_at, max_uses) " +
                    "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, invite.id());
                ps.setString(2, invite.tokenHash());
                ps.setString(3, invite.boardId());
                ps.setString(4, invite.permission());
                if (invite.expiresAt() == null) ps.setNull(5, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
                else ps.setTimestamp(5, Timestamp.from(invite.expiresAt()));
                if (invite.maxUses() == null) ps.setNull(6, java.sql.Types.INTEGER);
                else ps.setInt(6, invite.maxUses());
                ps.executeUpdate();
            }
            return findById(invite.id()).orElseThrow(() -> new IllegalStateException("Inserted invite not found"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create invite", e);
        }
    }

    @Override
    public List<Invite> listForBoard(String boardId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, token_hash, board_id, permission, expires_at, max_uses, uses, revoked_at, created_at " +
                     "FROM invites WHERE board_id = ? ORDER BY created_at DESC")) {
            ps.setString(1, boardId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Invite> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list invites", e);
        }
    }

    @Override
    public Optional<Invite> findById(String inviteId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, token_hash, board_id, permission, expires_at, max_uses, uses, revoked_at, created_at " +
                     "FROM invites WHERE id = ?")) {
            ps.setString(1, inviteId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find invite", e);
        }
    }

    @Override
    public Optional<Invite> findByTokenHash(String tokenHash) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, token_hash, board_id, permission, expires_at, max_uses, uses, revoked_at, created_at " +
                     "FROM invites WHERE token_hash = ?")) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find invite by token hash", e);
        }
    }

    @Override
    public boolean revoke(String inviteId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE invites SET revoked_at = now() WHERE id = ? AND revoked_at IS NULL")) {
            ps.setString(1, inviteId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("Failed to revoke invite", e);
        }
    }

    @Override
    public Optional<Invite> incrementUses(String inviteId) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE invites SET uses = uses + 1 WHERE id = ? RETURNING id, token_hash, board_id, permission, expires_at, max_uses, uses, revoked_at, created_at")) {
                ps.setString(1, inviteId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(map(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to increment invite uses", e);
        }
    }

    private static Invite map(ResultSet rs) throws Exception {
        return new Invite(
                rs.getString("id"),
                rs.getString("board_id"),
                rs.getString("token_hash"),
                rs.getString("permission"),
                toInstant(rs.getTimestamp("expires_at")),
                (Integer) rs.getObject("max_uses"),
                rs.getInt("uses"),
                toInstant(rs.getTimestamp("revoked_at")),
                toInstant(rs.getTimestamp("created_at"))
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
