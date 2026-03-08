package info.isaksson.erland.whiteboard.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import info.isaksson.erland.whiteboard.comments.Comment;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.arc.profile.IfBuildProfile;

@ApplicationScoped
@IfBuildProfile("test")
@Priority(1)
public class InMemoryCommentsRepository implements CommentsRepository {

    private final ConcurrentHashMap<String, Comment> comments = new ConcurrentHashMap<>();

    @Override
    public Comment create(Comment comment) {
        Instant now = Instant.now();
        Comment created = new Comment(
                comment.id(),
                comment.boardId(),
                comment.parentCommentId(),
                comment.targetType(),
                comment.targetRef(),
                comment.authorUserId(),
                comment.content(),
                comment.state(),
                now,
                now,
                comment.resolvedAt(),
                comment.deletedAt()
        );
        comments.put(created.id(), created);
        return created;
    }

    @Override
    public Optional<Comment> findById(String commentId) {
        return Optional.ofNullable(comments.get(commentId));
    }

    @Override
    public List<Comment> listForBoard(String boardId) {
        return comments.values().stream()
                .filter(comment -> comment.boardId().equals(boardId))
                .sorted(Comparator.comparing(Comment::createdAt))
                .toList();
    }

    @Override
    public Optional<Comment> update(Comment comment) {
        Comment updated = comments.computeIfPresent(comment.id(), (id, existing) -> comment);
        return Optional.ofNullable(updated);
    }

    public void clear() {
        comments.clear();
    }
}
