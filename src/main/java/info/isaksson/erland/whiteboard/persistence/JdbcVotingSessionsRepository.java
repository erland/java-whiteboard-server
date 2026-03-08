package info.isaksson.erland.whiteboard.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.voting.VotingRules;
import info.isaksson.erland.whiteboard.voting.VotingScopeType;
import info.isaksson.erland.whiteboard.voting.VotingSession;
import info.isaksson.erland.whiteboard.voting.VotingSessionState;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@UnlessBuildProfile("test")
public class JdbcVotingSessionsRepository implements VotingSessionsRepository {

    @Inject
    AgroalDataSource dataSource;

    @Override
    public VotingSession create(VotingSession session) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO voting_sessions (id, board_id, scope_type, scope_ref, state, created_by_user_id, allow_viewer_participation, allow_published_reader_participation, max_votes_per_participant, anonymous_votes, show_progress_during_voting, allow_vote_updates, duration_seconds, created_at, updated_at, opened_at, closed_at, revealed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, session.id());
            ps.setString(2, session.boardId());
            ps.setString(3, session.scopeType().storageValue());
            JdbcSupport.setNullableString(ps, 4, session.scopeRef());
            ps.setString(5, session.state().storageValue());
            ps.setString(6, session.createdByUserId());
            ps.setBoolean(7, session.rules().allowViewerParticipation());
            ps.setBoolean(8, session.rules().allowPublishedReaderParticipation());
            ps.setInt(9, session.rules().maxVotesPerParticipant());
            ps.setBoolean(10, session.rules().anonymousVotes());
            ps.setBoolean(11, session.rules().showProgressDuringVoting());
            ps.setBoolean(12, session.rules().allowVoteUpdates());
            JdbcSupport.setNullableLong(ps, 13, session.rules().durationSeconds());
            JdbcSupport.setNullableTimestamp(ps, 14, session.createdAt());
            JdbcSupport.setNullableTimestamp(ps, 15, session.updatedAt());
            JdbcSupport.setNullableTimestamp(ps, 16, session.openedAt());
            JdbcSupport.setNullableTimestamp(ps, 17, session.closedAt());
            JdbcSupport.setNullableTimestamp(ps, 18, session.revealedAt());
            ps.executeUpdate();
            return findById(session.id()).orElseThrow(() -> new IllegalStateException("Inserted voting session not found"));
        } catch (Exception e) {
            throw JdbcSupport.failure("create voting session", e);
        }
    }

    @Override
    public Optional<VotingSession> update(VotingSession session) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE voting_sessions SET scope_type = ?, scope_ref = ?, state = ?, allow_viewer_participation = ?, allow_published_reader_participation = ?, max_votes_per_participant = ?, anonymous_votes = ?, show_progress_during_voting = ?, allow_vote_updates = ?, duration_seconds = ?, updated_at = ?, opened_at = ?, closed_at = ?, revealed_at = ? WHERE id = ? RETURNING id, board_id, scope_type, scope_ref, state, created_by_user_id, allow_viewer_participation, allow_published_reader_participation, max_votes_per_participant, anonymous_votes, show_progress_during_voting, allow_vote_updates, duration_seconds, created_at, updated_at, opened_at, closed_at, revealed_at")) {
            ps.setString(1, session.scopeType().storageValue());
            JdbcSupport.setNullableString(ps, 2, session.scopeRef());
            ps.setString(3, session.state().storageValue());
            ps.setBoolean(4, session.rules().allowViewerParticipation());
            ps.setBoolean(5, session.rules().allowPublishedReaderParticipation());
            ps.setInt(6, session.rules().maxVotesPerParticipant());
            ps.setBoolean(7, session.rules().anonymousVotes());
            ps.setBoolean(8, session.rules().showProgressDuringVoting());
            ps.setBoolean(9, session.rules().allowVoteUpdates());
            JdbcSupport.setNullableLong(ps, 10, session.rules().durationSeconds());
            JdbcSupport.setNullableTimestamp(ps, 11, session.updatedAt());
            JdbcSupport.setNullableTimestamp(ps, 12, session.openedAt());
            JdbcSupport.setNullableTimestamp(ps, 13, session.closedAt());
            JdbcSupport.setNullableTimestamp(ps, 14, session.revealedAt());
            ps.setString(15, session.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("update voting session", e);
        }
    }

    @Override
    public Optional<VotingSession> findById(String sessionId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, scope_type, scope_ref, state, created_by_user_id, allow_viewer_participation, allow_published_reader_participation, max_votes_per_participant, anonymous_votes, show_progress_during_voting, allow_vote_updates, duration_seconds, created_at, updated_at, opened_at, closed_at, revealed_at FROM voting_sessions WHERE id = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("find voting session", e);
        }
    }

    @Override
    public List<VotingSession> listForBoard(String boardId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, scope_type, scope_ref, state, created_by_user_id, allow_viewer_participation, allow_published_reader_participation, max_votes_per_participant, anonymous_votes, show_progress_during_voting, allow_vote_updates, duration_seconds, created_at, updated_at, opened_at, closed_at, revealed_at FROM voting_sessions WHERE board_id = ? ORDER BY created_at ASC, id ASC")) {
            ps.setString(1, boardId);
            try (ResultSet rs = ps.executeQuery()) {
                List<VotingSession> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("list voting sessions", e);
        }
    }

    private static VotingSession map(ResultSet rs) throws Exception {
        return new VotingSession(
                rs.getString("id"),
                rs.getString("board_id"),
                VotingScopeType.fromStorageValue(rs.getString("scope_type")),
                rs.getString("scope_ref"),
                VotingSessionState.fromStorageValue(rs.getString("state")),
                rs.getString("created_by_user_id"),
                new VotingRules(
                        rs.getBoolean("allow_viewer_participation"),
                        rs.getBoolean("allow_published_reader_participation"),
                        rs.getInt("max_votes_per_participant"),
                        rs.getBoolean("anonymous_votes"),
                        rs.getBoolean("show_progress_during_voting"),
                        rs.getBoolean("allow_vote_updates"),
                        (Long) rs.getObject("duration_seconds")
                ),
                JdbcSupport.getInstant(rs, "created_at"),
                JdbcSupport.getInstant(rs, "updated_at"),
                JdbcSupport.getInstant(rs, "opened_at"),
                JdbcSupport.getInstant(rs, "closed_at"),
                JdbcSupport.getInstant(rs, "revealed_at")
        );
    }
}
