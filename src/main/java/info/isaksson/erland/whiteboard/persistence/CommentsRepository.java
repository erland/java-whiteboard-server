package info.isaksson.erland.whiteboard.persistence;

import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.comments.Comment;

public interface CommentsRepository {

    Comment create(Comment comment);

    Optional<Comment> findById(String commentId);

    List<Comment> listForBoard(String boardId);

    Optional<Comment> update(Comment comment);
}
