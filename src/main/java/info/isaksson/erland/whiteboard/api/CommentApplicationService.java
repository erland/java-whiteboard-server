package info.isaksson.erland.whiteboard.api;

import java.util.List;

import info.isaksson.erland.whiteboard.api.dto.CreateCommentRequest;
import info.isaksson.erland.whiteboard.api.dto.UpdateCommentRequest;
import info.isaksson.erland.whiteboard.comments.Comment;
import info.isaksson.erland.whiteboard.comments.CommentService;
import info.isaksson.erland.whiteboard.comments.CommentTargetType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class CommentApplicationService {

    private final CommentService commentService;
    private final CommentAccessResolver accessResolver;
    private final CommentRequestSupport requestSupport;

    @Inject
    public CommentApplicationService(CommentService commentService,
                                     CommentAccessResolver accessResolver,
                                     CommentRequestSupport requestSupport) {
        this.commentService = commentService;
        this.accessResolver = accessResolver;
        this.requestSupport = requestSupport;
    }

    public List<Comment> listComments(String boardId, String publicationToken) {
        accessResolver.requireCommentReadAccess(boardId, publicationToken);
        return commentService.listForBoard(boardId);
    }

    public Comment createComment(String boardId, CreateCommentRequest req) {
        String userId = accessResolver.requireCommentParticipantUserId(boardId);
        CommentTargetType targetType = requestSupport.parseTargetType(req == null ? null : req.targetType());
        return switch (targetType) {
            case BOARD -> commentService.createBoardComment(boardId, userId, requestSupport.requireContent(req));
            case OBJECT -> commentService.createObjectComment(boardId, requestSupport.requireTargetRef(req, "object"), userId, requestSupport.requireContent(req));
            case REGION -> commentService.createRegionComment(boardId, requestSupport.requireTargetRef(req, "region"), userId, requestSupport.requireContent(req));
            case COMMENT -> commentService.replyToComment(boardId, requestSupport.requireParentCommentId(req), userId, requestSupport.requireContent(req));
        };
    }

    public Comment updateComment(String boardId, String commentId, UpdateCommentRequest req) {
        String userId = accessResolver.requireCommentParticipantUserId(boardId);
        accessResolver.requireOwnedComment(boardId, commentId, userId);
        return commentService.updateContent(commentId, userId, requestSupport.requireUpdateContent(req))
                .orElseThrow(NotFoundException::new);
    }

    public Comment resolveComment(String boardId, String commentId) {
        String userId = accessResolver.requireCommentParticipantUserId(boardId);
        accessResolver.requireLifecycleManagement(boardId, commentId, userId);
        return commentService.resolve(commentId).orElseThrow(NotFoundException::new);
    }

    public Comment reopenComment(String boardId, String commentId) {
        String userId = accessResolver.requireCommentParticipantUserId(boardId);
        accessResolver.requireLifecycleManagement(boardId, commentId, userId);
        return commentService.reopen(commentId).orElseThrow(NotFoundException::new);
    }

    public Comment deleteComment(String boardId, String commentId) {
        String userId = accessResolver.requireCommentParticipantUserId(boardId);
        accessResolver.requireLifecycleManagement(boardId, commentId, userId);
        return commentService.delete(commentId).orElseThrow(NotFoundException::new);
    }
}
