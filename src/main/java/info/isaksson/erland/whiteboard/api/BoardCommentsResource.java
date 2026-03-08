package info.isaksson.erland.whiteboard.api;

import java.util.List;

import info.isaksson.erland.whiteboard.api.dto.CommentResponse;
import info.isaksson.erland.whiteboard.api.dto.CreateCommentRequest;
import info.isaksson.erland.whiteboard.api.dto.UpdateCommentRequest;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.comments.Comment;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
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
    CommentApplicationService commentApplicationService;

    @Inject
    CommentRequestSupport commentRequestSupport;

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
        return commentApplicationService.listComments(boardId, publicationToken).stream()
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
        try {
            Comment created = commentApplicationService.createComment(boardId, req);
            return Response.status(Response.Status.CREATED).entity(CommentResponse.from(created)).build();
        } catch (IllegalArgumentException e) {
            return commentRequestSupport.validationError(e.getMessage());
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
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Comment identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("commentId") String commentId,
            @RequestBody(required = true, description = "Comment update request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UpdateCommentRequest.class)))
            UpdateCommentRequest req) {
        featureSupport.requireCommentsEnabled();
        try {
            return Response.ok(CommentResponse.from(commentApplicationService.updateComment(boardId, commentId, req))).build();
        } catch (IllegalArgumentException e) {
            return commentRequestSupport.validationError(e.getMessage());
        }
    }

    @POST
    @Path("/{boardId}/comments/{commentId}/resolve")
    @Consumes(MediaType.WILDCARD)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Resolve comment", description = "Resolves a comment. The comment author or a board writer may resolve the comment.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Comment resolved.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CommentResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Comment not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response resolveComment(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Comment identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("commentId") String commentId) {
        featureSupport.requireCommentsEnabled();
        try {
            return Response.ok(CommentResponse.from(commentApplicationService.resolveComment(boardId, commentId))).build();
        } catch (IllegalArgumentException e) {
            return commentRequestSupport.validationError(e.getMessage());
        }
    }

    @POST
    @Path("/{boardId}/comments/{commentId}/reopen")
    @Consumes(MediaType.WILDCARD)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Reopen comment", description = "Reopens a resolved comment. The comment author or a board writer may reopen the comment.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Comment reopened.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CommentResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Comment not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response reopenComment(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Comment identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("commentId") String commentId) {
        featureSupport.requireCommentsEnabled();
        try {
            return Response.ok(CommentResponse.from(commentApplicationService.reopenComment(boardId, commentId))).build();
        } catch (IllegalArgumentException e) {
            return commentRequestSupport.validationError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{boardId}/comments/{commentId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete comment", description = "Marks a comment as deleted. The comment author or a board writer may delete the comment.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Comment deleted.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CommentResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Comment not found or not accessible.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response deleteComment(
            @Parameter(description = "Board identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("boardId") String boardId,
            @Parameter(description = "Comment identifier.", required = true, schema = @Schema(type = SchemaType.STRING)) @PathParam("commentId") String commentId) {
        featureSupport.requireCommentsEnabled();
        try {
            return Response.ok(CommentResponse.from(commentApplicationService.deleteComment(boardId, commentId))).build();
        } catch (IllegalArgumentException e) {
            return commentRequestSupport.validationError(e.getMessage());
        }
    }
}
