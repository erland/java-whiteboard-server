package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.errors.ApiError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class ApiRequestSupport {

    public Response validationError(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError("VALIDATION_ERROR", message))
                .build();
    }
}
