package info.isaksson.erland.whiteboard.api;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import info.isaksson.erland.whiteboard.api.dto.MeResponse;
import info.isaksson.erland.whiteboard.api.errors.ApiError;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "Identity")
@SecurityRequirement(name = "bearerAuth")
@Path("/api/me")
public class MeResource {

    private static final String ROLE_USER = "whiteboard-user";
    private static final String ROLE_ADMIN = "whiteboard-admin";

    private final SecurityIdentity identity;

    public MeResource(SecurityIdentity identity) {
        this.identity = identity;
    }

    /**
     * Returns basic information about the authenticated principal.
     *
     * Authentication:
     * - Requires an authenticated user.
     *
     * Authorization:
     * - Requires role whiteboard-user OR whiteboard-admin.
     *
     * Note:
     * We perform checks explicitly in order to return consistent JSON error bodies for 401/403.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get current user", description = "Returns the authenticated principal id and granted whiteboard roles.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Current user returned.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MeResponse.class))),
            @APIResponse(responseCode = "401", description = "Authentication required.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class))),
            @APIResponse(responseCode = "403", description = "Authenticated principal lacks required role.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    })
    public MeResponse me() {
        if (identity == null || identity.isAnonymous()) {
            // Triggers our NotAuthorizedExceptionMapper -> JSON 401
            throw new NotAuthorizedException("Bearer");
        }

        boolean allowed = identity.getRoles().contains(ROLE_USER) || identity.getRoles().contains(ROLE_ADMIN);
        if (!allowed) {
            // Triggers our ForbiddenExceptionMapper -> JSON 403
            throw new ForbiddenException();
        }

        List<String> roles = identity.getRoles().stream()
                .sorted()
                .collect(Collectors.toList());

        return new MeResponse(identity.getPrincipal().getName(), roles);
    }
}
