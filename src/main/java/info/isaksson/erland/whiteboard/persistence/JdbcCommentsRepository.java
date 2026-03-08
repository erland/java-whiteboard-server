package info.isaksson.erland.whiteboard.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.comments.Comment;
import info.isaksson.erland.whiteboard.comments.CommentState;
import info.isaksson.erland.whiteboard.comments.CommentTargetType;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@UnlessBuildProfile("test")
public class JdbcCommentsRepository implements CommentsRepository {

    @Inject
    AgroalDataSource dataSource;

    @Override
    public Comment create(Comment comment) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO comments (id, board_id, parent_comment_id, target_type, target_ref, author_user_id, content, state, resolved_at, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, comment.id());
                ps.setString(2, comment.boardId());
                JdbcSupport.setNullableString(ps, 3, comment.parentCommentId());
                ps.setString(4, comment.targetType().storageValue());
                ps.setString(5, comment.targetRef());
                ps.setString(6, comment.authorUserId());
                ps.setString(7, comment.content());
                ps.setString(8, comment.state().storageValue());
                JdbcSupport.setNullableTimestamp(ps, 9, comment.resolvedAt());
                JdbcSupport.setNullableTimestamp(ps, 10, comment.deletedAt());
                ps.executeUpdate();
            }
            return findById(comment.id())
                    .orElseThrow(() -> new IllegalStateException("Inserted comment not found"));
        } catch (Exception e) {
            throw JdbcSupport.failure("create comment", e);
        }
    }

    @Override
    public Optional<Comment> findById(String commentId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, parent_comment_id, target_type, target_ref, author_user_id, content, state, created_at, updated_at, resolved_at, deleted_at FROM comments WHERE id = ?")) {
            ps.setString(1, commentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("find comment", e);
        }
    }

    @Override
    public List<Comment> listForBoard(String boardId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, board_id, parent_comment_id, target_type, target_ref, author_user_id, content, state, created_at, updated_at, resolved_at, deleted_at FROM comments WHERE board_id = ? ORDER BY created_at ASC, id ASC")) {
            ps.setString(1, boardId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Comment> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("list comments", e);
        }
    }

    @Override
    public Optional<Comment> update(Comment comment) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE comments SET parent_comment_id = ?, target_type = ?, target_ref = ?, author_user_id = ?, content = ?, state = ?, updated_at = ?, resolved_at = ?, deleted_at = ? WHERE id = ? RETURNING id, board_id, parent_comment_id, target_type, target_ref, author_user_id, content, state, created_at, updated_at, resolved_at, deleted_at")) {
            JdbcSupport.setNullableString(ps, 1, comment.parentCommentId());
            ps.setString(2, comment.targetType().storageValue());
            ps.setString(3, comment.targetRef());
            ps.setString(4, comment.authorUserId());
            ps.setString(5, comment.content());
            ps.setString(6, comment.state().storageValue());
            JdbcSupport.setNullableTimestamp(ps, 7, comment.updatedAt());
            JdbcSupport.setNullableTimestamp(ps, 8, comment.resolvedAt());
            JdbcSupport.setNullableTimestamp(ps, 9, comment.deletedAt());
            ps.setString(10, comment.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (Exception e) {
            throw JdbcSupport.failure("update comment", e);
        }
    }

    private static Comment map(ResultSet rs) throws Exception {
        return new Comment(
                rs.getString("id"),
                rs.getString("board_id"),
                rs.getString("parent_comment_id"),
                CommentTargetType.fromStorageValue(rs.getString("target_type")),
                rs.getString("target_ref"),
                rs.getString("author_user_id"),
                rs.getString("content"),
                CommentState.fromStorageValue(rs.getString("state")),
                JdbcSupport.getInstant(rs, "created_at"),
                JdbcSupport.getInstant(rs, "updated_at"),
                JdbcSupport.getInstant(rs, "resolved_at"),
                JdbcSupport.getInstant(rs, "deleted_at")
        );
    }
}
