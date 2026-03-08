package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.dto.CreateCommentRequest;
import info.isaksson.erland.whiteboard.api.dto.UpdateCommentRequest;
import info.isaksson.erland.whiteboard.comments.CommentTargetType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class CommentRequestSupport {

    @Inject
    ApiRequestSupport apiRequestSupport;

    public CommentTargetType parseTargetType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field 'targetType' is required.");
        }
        try {
            return CommentTargetType.fromStorageValue(value.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Field 'targetType' must be one of: board, object, region, comment.");
        }
    }

    public String requireContent(CreateCommentRequest req) {
        String value = req == null ? null : req.content();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field 'content' is required.");
        }
        return value;
    }

    public String requireUpdateContent(UpdateCommentRequest req) {
        String value = req == null ? null : req.content();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field 'content' is required.");
        }
        return value;
    }

    public String requireTargetRef(CreateCommentRequest req, String type) {
        String value = req == null ? null : req.targetRef();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field 'targetRef' is required for " + type + " comments.");
        }
        return value;
    }

    public String requireParentCommentId(CreateCommentRequest req) {
        String value = req == null ? null : req.parentCommentId();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field 'parentCommentId' is required for reply comments.");
        }
        return value;
    }

    public Response validationError(String message) {
        return apiRequestSupport.validationError(message);
    }
}
