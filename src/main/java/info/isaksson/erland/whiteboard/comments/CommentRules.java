package info.isaksson.erland.whiteboard.comments;

import java.util.Set;

public final class CommentRules {

    public static final int MAX_CONTENT_LENGTH = 4_000;
    private static final Set<CommentState> MUTABLE_CONTENT_STATES = Set.of(CommentState.ACTIVE);

    private CommentRules() {
    }

    public static void validateNewComment(CommentTargetType targetType,
                                          String targetRef,
                                          String parentCommentId,
                                          String authorUserId,
                                          String content) {
        requireTarget(targetType, targetRef, parentCommentId);
        requireAuthor(authorUserId);
        requireContent(content);
    }

    public static void requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Comment content is required");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Comment content exceeds max length");
        }
    }

    public static void requireAuthor(String authorUserId) {
        if (authorUserId == null || authorUserId.isBlank()) {
            throw new IllegalArgumentException("Comment author is required");
        }
    }

    public static void requireTarget(CommentTargetType targetType, String targetRef, String parentCommentId) {
        if (targetType == null) {
            throw new IllegalArgumentException("Comment target type is required");
        }
        if (targetRef == null || targetRef.isBlank()) {
            throw new IllegalArgumentException("Comment target reference is required");
        }
        if (targetType == CommentTargetType.COMMENT) {
            if (parentCommentId == null || parentCommentId.isBlank()) {
                throw new IllegalArgumentException("Reply comments require parent comment id");
            }
            if (!targetRef.equals(parentCommentId)) {
                throw new IllegalArgumentException("Reply target reference must equal parent comment id");
            }
        } else if (parentCommentId != null && !parentCommentId.isBlank()) {
            throw new IllegalArgumentException("Only reply comments may set parent comment id");
        }
    }

    public static void requireCanEdit(Comment comment, String actorUserId) {
        if (comment == null) {
            throw new IllegalArgumentException("Comment is required");
        }
        if (actorUserId == null || actorUserId.isBlank()) {
            throw new IllegalArgumentException("Actor user id is required");
        }
        if (!actorUserId.equals(comment.authorUserId())) {
            throw new IllegalArgumentException("Only the author may edit the comment");
        }
        if (!MUTABLE_CONTENT_STATES.contains(comment.state())) {
            throw new IllegalArgumentException("Comment content cannot be edited in current state");
        }
    }

    public static void requireTransition(Comment comment, CommentState nextState) {
        if (comment == null) {
            throw new IllegalArgumentException("Comment is required");
        }
        if (nextState == null) {
            throw new IllegalArgumentException("Target comment state is required");
        }
        if (comment.state() == nextState) {
            return;
        }
        boolean allowed = switch (comment.state()) {
            case ACTIVE -> nextState == CommentState.RESOLVED || nextState == CommentState.DELETED;
            case RESOLVED -> nextState == CommentState.ACTIVE || nextState == CommentState.DELETED;
            case DELETED -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException("Invalid comment state transition: " + comment.state() + " -> " + nextState);
        }
    }
}
