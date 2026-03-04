package info.isaksson.erland.whiteboard.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.security.identity.SecurityIdentity;

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
    public Map<String, Object> me() {
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

        return Map.of(
                "userId", identity.getPrincipal().getName(),
                "roles", roles
        );
    }
}
