package info.isaksson.erland.whiteboard.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.voting.VoteRecord;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@UnlessBuildProfile("test")
public class JdbcVoteRecordsRepository implements VoteRecordsRepository {

    @Inject
    AgroalDataSource dataSource;

    @Override
    public VoteRecord create(VoteRecord voteRecord) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO vote_records (id, voting_session_id, participant_id, target_ref, vote_value, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, voteRecord.id());
            ps.setString(2, voteRecord.sessionId());
            ps.setString(3, voteRecord.participantId());
            ps.setString(4, voteRecord.targetRef());
            ps.setInt(5, voteRecord.voteValue());
            JdbcSupport.setNullableTimestamp(ps, 6, voteRecord.createdAt());
            JdbcSupport.setNullableTimestamp(ps, 7, voteRecord.updatedAt());
            ps.executeUpdate();
            return listForSessionAndParticipant(voteRecord.sessionId(), voteRecord.participantId()).stream()
                    .filter(vote -> vote.id().equals(voteRecord.id()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Inserted vote record not found"));
        } catch (Exception e) {
            throw JdbcSupport.failure("create vote record", e);
        }
    }

    @Override
    public Optional<VoteRecord> update(VoteRecord voteRecord) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE vote_records SET vote_value = ?, updated_at = ? WHERE id = ? RETURNING id, voting_session_id, participant_id, target_ref, vote_value, created_at, updated_at")) {
            ps.setInt(1, voteRecord.voteValue());
            JdbcSupport.setNullableTimestamp(ps, 2, voteRecord.updatedAt());
            ps.setString(3, voteRecord.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("update vote record", e);
        }
    }

    @Override
    public List<VoteRecord> listForSession(String sessionId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, voting_session_id, participant_id, target_ref, vote_value, created_at, updated_at FROM vote_records WHERE voting_session_id = ? ORDER BY created_at ASC, id ASC")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<VoteRecord> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("list vote records", e);
        }
    }

    @Override
    public List<VoteRecord> listForSessionAndParticipant(String sessionId, String participantId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, voting_session_id, participant_id, target_ref, vote_value, created_at, updated_at FROM vote_records WHERE voting_session_id = ? AND participant_id = ? ORDER BY created_at ASC, id ASC")) {
            ps.setString(1, sessionId);
            ps.setString(2, participantId);
            try (ResultSet rs = ps.executeQuery()) {
                List<VoteRecord> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("list participant vote records", e);
        }
    }

    @Override
    public boolean deleteForSessionParticipantAndTarget(String sessionId, String participantId, String targetRef) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM vote_records WHERE voting_session_id = ? AND participant_id = ? AND target_ref = ?")) {
            ps.setString(1, sessionId);
            ps.setString(2, participantId);
            ps.setString(3, targetRef);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw JdbcSupport.failure("delete vote record", e);
        }
    }

    private static VoteRecord map(ResultSet rs) throws Exception {
        return new VoteRecord(
                rs.getString("id"),
                rs.getString("voting_session_id"),
                rs.getString("participant_id"),
                rs.getString("target_ref"),
                rs.getInt("vote_value"),
                JdbcSupport.getInstant(rs, "created_at"),
                JdbcSupport.getInstant(rs, "updated_at")
        );
    }
}
