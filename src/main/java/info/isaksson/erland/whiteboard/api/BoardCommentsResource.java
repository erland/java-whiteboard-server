package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.FeatureSupport;
import java.util.List;

import info.isaksson.erland.whiteboard.api.dto.CommentResponse;
import info.isaksson.erland.whiteboard.api.dto.CreateCommentRequest;
import info.isaksson.erland.whiteboard.api.dto.UpdateCommentRequest;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.comments.Comment;
import info.isaksson.erland.whiteboard.comments.CommentService;
import info.isaksson.erland.whiteboard.comments.CommentTargetType;
import info.isaksson.erland.whiteboard.publication.Publication;
import info.isaksson.erland.whiteboard.publication.PublicationPolicy;
import info.isaksson.erland.whiteboard.security.Authz;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "Comments")
@Path("/api/boards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BoardCommentsResource {

    @Inject
    CommentService commentService;

    @Inject
    BoardGuards boardGuards;

    @Inject
    PublicationPolicy publicationPolicy;

    @Inject
    SecurityIdentity identity;

    @Inject
    FeatureSupport featureSupport;

    @GET
    @Path("/{boardId}/comments")
    @Operation(summary = "List comments", description = "Lists durable comments for a board. Authenticated members may always list comments they can read. Publication readers may list comments only when a valid publication token is supplied and the publication explicitly allows comments.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Comments returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = CommentResponse.class))),
            @APIResponse(responseCode = "404", description = "Board not found or comments are not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public List<CommentResponse> listComments(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Optional publication token used for anonymous publication comment visibility when enabled.", required = false, schema = @Schema(type = SchemaType.STRING)) @QueryParam("publicationToken") String publicationToken) {
        featureSupport.requireCommentsEnabled();
        Publication publication = resolveReadablePublication(boardId, publicationToken);
        if (!identity.isAnonymous()) {
            String userId = Authz.userId(identity);
            boardGuards.requirePublicationReadAccess(boardId, userId, publication != null);
        } else if (publication == null) {
            throw new NotFoundException();
        }
        return commentService.listForBoard(boardId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @POST
    @Path("/{boardId}/comments")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create comment", description = "Creates a durable board comment, object comment, region comment, or reply comment. Requires authenticated board comment participation access.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Comment created.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CommentResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid comment request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Board not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response createComment(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @RequestBody(required = true, description = "Comment creation request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateCommentRequest.class)))
            CreateCommentRequest req) {
        featureSupport.requireCommentsEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireCommentParticipation(boardId, userId, false);

        CommentTargetType targetType;
        try {
            targetType = parseTargetType(req == null ? null : req.targetType());
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }

        try {
            Comment created = switch (targetType) {
                case BOARD -> commentService.createBoardComment(boardId, userId, requireContent(req));
                case OBJECT -> commentService.createObjectComment(boardId, requireTargetRef(req, "object"), userId, requireContent(req));
                case REGION -> commentService.createRegionComment(boardId, requireTargetRef(req, "region"), userId, requireContent(req));
                case COMMENT -> commentService.replyToComment(boardId, requireParentCommentId(req), userId, requireContent(req));
            };
            return Response.status(Response.Status.CREATED).entity(CommentResponse.from(created)).build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @PATCH
    @Path("/{boardId}/comments/{commentId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update comment", description = "Updates the content of an existing comment. Only the comment author may update content.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Comment updated.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CommentResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid comment update.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Comment not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response updateComment(
            @PathParam("boardId") String boardId,
            @PathParam("commentId") String commentId,
            UpdateCommentRequest req) {
        featureSupport.requireCommentsEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        boardGuards.requireCommentParticipation(boardId, userId, false);
        Comment existing = requireCommentForBoard(boardId, commentId);
        if (!userId.equals(existing.authorUserId())) {
            throw new NotFoundException();
        }
        try {
            return commentService.updateContent(commentId, userId, req == null ? null : req.content())
                    .map(CommentResponse::from)
                    .map(Response::ok)
                    .orElseThrow(NotFoundException::new)
                    .build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @POST
    @Path("/{boardId}/comments/{commentId}/resolve")
    @Consumes(MediaType.WILDCARD)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Resolve comment", description = "Resolves a comment. The comment author or a board writer may resolve the comment.")
    public Response resolveComment(@PathParam("boardId") String boardId, @PathParam("commentId") String commentId) {
        featureSupport.requireCommentsEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        ensureCanManageLifecycle(boardId, commentId, userId);
        try {
            return commentService.resolve(commentId)
                    .map(CommentResponse::from)
                    .map(Response::ok)
                    .orElseThrow(NotFoundException::new)
                    .build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @POST
    @Path("/{boardId}/comments/{commentId}/reopen")
    @Consumes(MediaType.WILDCARD)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Reopen comment", description = "Reopens a resolved comment. The comment author or a board writer may reopen the comment.")
    public Response reopenComment(@PathParam("boardId") String boardId, @PathParam("commentId") String commentId) {
        featureSupport.requireCommentsEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        ensureCanManageLifecycle(boardId, commentId, userId);
        try {
            return commentService.reopen(commentId)
                    .map(CommentResponse::from)
                    .map(Response::ok)
                    .orElseThrow(NotFoundException::new)
                    .build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{boardId}/comments/{commentId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete comment", description = "Marks a comment as deleted. The comment author or a board writer may delete the comment.")
    public Response deleteComment(@PathParam("boardId") String boardId, @PathParam("commentId") String commentId) {
        featureSupport.requireCommentsEnabled();
        Authz.requireUserOrAdmin(identity);
        String userId = Authz.userId(identity);
        ensureCanManageLifecycle(boardId, commentId, userId);
        try {
            return commentService.delete(commentId)
                    .map(CommentResponse::from)
                    .map(Response::ok)
                    .orElseThrow(NotFoundException::new)
                    .build();
        } catch (IllegalArgumentException e) {
            return validationError(e.getMessage());
        }
    }

    private Publication resolveReadablePublication(String boardId, String publicationToken) {
        var decision = publicationPolicy.validateToken(publicationToken);
        if (!decision.valid() || decision.publication() == null) {
            return null;
        }
        Publication publication = decision.publication();
        if (!boardId.equals(publication.boardId()) || !publication.allowComments()) {
            return null;
        }
        return publication;
    }

    private Comment requireCommentForBoard(String boardId, String commentId) {
        Comment comment = commentService.findById(commentId).orElseThrow(NotFoundException::new);
        if (!boardId.equals(comment.boardId())) {
            throw new NotFoundException();
        }
        return comment;
    }

    private void ensureCanManageLifecycle(String boardId, String commentId, String userId) {
        boardGuards.requireCommentParticipation(boardId, userId, false);
        Comment existing = requireCommentForBoard(boardId, commentId);
        if (!userId.equals(existing.authorUserId())) {
            boardGuards.requireBoardWriteAccess(boardId, userId);
        }
    }

    private static CommentTargetType parseTargetType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field 'targetType' is required.");
        }
        try {
            return CommentTargetType.fromStorageValue(value.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Field 'targetType' must be one of: board, object, region, comment.");
        }
    }

    private static String requireContent(CreateCommentRequest req) {
        String value = req == null ? null : req.content();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field 'content' is required.");
        }
        return value;
    }

    private static String requireTargetRef(CreateCommentRequest req, String type) {
        String value = req == null ? null : req.targetRef();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field 'targetRef' is required for " + type + " comments.");
        }
        return value;
    }

    private static String requireParentCommentId(CreateCommentRequest req) {
        String value = req == null ? null : req.parentCommentId();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field 'parentCommentId' is required for reply comments.");
        }
        return value;
    }

    private static Response validationError(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError("VALIDATION_ERROR", message))
                .build();
    }
}
