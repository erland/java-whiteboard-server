package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.FeatureSupport;
import info.isaksson.erland.whiteboard.api.dto.PublicationResponse;
import info.isaksson.erland.whiteboard.api.dto.ResolvePublicationRequest;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.publication.PublicationPolicy;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "Publications")
@Path("/api/publications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublicationAccessResource {

    @Inject
    PublicationPolicy publicationPolicy;

    @Inject
    FeatureSupport featureSupport;

    @POST
    @Path("/resolve")
    @Operation(summary = "Resolve publication", description = "Resolves publication access material into publication metadata without requiring authentication.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Publication resolved.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PublicationResponse.class))),
            @APIResponse(responseCode = "400", description = "Invalid publication resolve request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "404", description = "Publication not found or no longer valid.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public Response resolvePublication(
            @RequestBody(required = true, description = "Publication resolve request.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ResolvePublicationRequest.class)))
            ResolvePublicationRequest req) {
        featureSupport.requirePublicationsEnabled();
        String token = req == null ? null : req.token();
        if (token == null || token.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError("VALIDATION_ERROR", "Field 'token' is required."))
                    .build();
        }

        PublicationPolicy.Decision decision = publicationPolicy.validateToken(token);
        if (!decision.valid() || decision.publication() == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(toApiError(decision.reason()))
                    .build();
        }
        return Response.ok(PublicationResponse.from(decision.publication())).build();
    }

    private static ApiError toApiError(String reason) {
        if (PublicationPolicy.REASON_REVOKED.equals(reason)) {
            return new ApiError("PUBLICATION_REVOKED", "Publication not found or no longer valid.");
        }
        if (PublicationPolicy.REASON_EXPIRED.equals(reason)) {
            return new ApiError("PUBLICATION_EXPIRED", "Publication not found or no longer valid.");
        }
        if (PublicationPolicy.REASON_INACTIVE.equals(reason)) {
            return new ApiError("PUBLICATION_INACTIVE", "Publication not found or no longer valid.");
        }
        return new ApiError("PUBLICATION_NOT_FOUND", "Publication not found or no longer valid.");
    }
}
