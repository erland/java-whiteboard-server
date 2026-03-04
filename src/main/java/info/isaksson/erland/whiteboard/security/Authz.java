package info.isaksson.erland.whiteboard.security;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;

import io.quarkus.security.identity.SecurityIdentity;

public final class Authz {

    public static final String ROLE_USER = "whiteboard-user";
    public static final String ROLE_ADMIN = "whiteboard-admin";

    private Authz() {}

    public static void requireAuthenticated(SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous()) {
            throw new NotAuthorizedException("Bearer");
        }
    }

    public static void requireUserOrAdmin(SecurityIdentity identity) {
        requireAuthenticated(identity);
        boolean allowed = identity.getRoles().contains(ROLE_USER) || identity.getRoles().contains(ROLE_ADMIN);
        if (!allowed) {
            throw new ForbiddenException();
        }
    }

    public static String userId(SecurityIdentity identity) {
        requireAuthenticated(identity);
        return identity.getPrincipal().getName();
    }
}
