package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.comments.Comment;
import info.isaksson.erland.whiteboard.comments.CommentService;
import info.isaksson.erland.whiteboard.publication.Publication;
import info.isaksson.erland.whiteboard.publication.PublicationPolicy;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class CommentAccessResolver {

    private final BoardGuards boardGuards;
    private final SecurityIdentity identity;
    private final PublicationPolicy publicationPolicy;
    private final CommentService commentService;

    @Inject
    public CommentAccessResolver(BoardGuards boardGuards,
                                 SecurityIdentity identity,
                                 PublicationPolicy publicationPolicy,
                                 CommentService commentService) {
        this.boardGuards = boardGuards;
        this.identity = identity;
        this.publicationPolicy = publicationPolicy;
        this.commentService = commentService;
    }

    public void requireCommentReadAccess(String boardId, String publicationToken) {
        Publication publication = resolveReadablePublication(boardId, publicationToken);
        if (identity != null && !identity.isAnonymous()) {
            String userId = Authz.userId(identity);
            boardGuards.requirePublicationReadAccess(boardId, userId, publication != null);
            return;
        }
        if (publication == null) {
            throw new NotFoundException();
        }
    }

    public String requireCommentParticipantUserId(String boardId) {
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireCommentParticipation(boardId, userId, false);
        return userId;
    }

    public Comment requireCommentForBoard(String boardId, String commentId) {
        Comment comment = commentService.findById(commentId).orElseThrow(NotFoundException::new);
        if (!boardId.equals(comment.boardId())) {
            throw new NotFoundException();
        }
        return comment;
    }

    public Comment requireOwnedComment(String boardId, String commentId, String userId) {
        Comment comment = requireCommentForBoard(boardId, commentId);
        if (!userId.equals(comment.authorUserId())) {
            throw new NotFoundException();
        }
        return comment;
    }

    public Comment requireLifecycleManagement(String boardId, String commentId, String userId) {
        boardGuards.requireCommentParticipation(boardId, userId, false);
        Comment existing = requireCommentForBoard(boardId, commentId);
        if (!userId.equals(existing.authorUserId())) {
            boardGuards.requireBoardWriteAccess(boardId, userId);
        }
        return existing;
    }

    private Publication resolveReadablePublication(String boardId, String publicationToken) {
        PublicationPolicy.Decision decision = publicationPolicy.validateToken(publicationToken);
        if (!decision.valid() || decision.publication() == null) {
            return null;
        }
        Publication publication = decision.publication();
        if (!boardId.equals(publication.boardId()) || !publication.allowComments()) {
            return null;
        }
        return publication;
    }
}
