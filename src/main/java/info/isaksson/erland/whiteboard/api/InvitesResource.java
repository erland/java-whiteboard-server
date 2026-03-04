package info.isaksson.erland.whiteboard.api;

/**
 * Compatibility stub.
 *
 * Earlier iterations used an {@code InvitesResource} JAX-RS resource which has since been split into:
 * - {@link BoardInvitesResource}
 * - {@link InviteValidationResource}
 *
 * This class intentionally has no JAX-RS annotations.
 * It exists to ensure any stale compiled classes from previous checkouts are overwritten without requiring `mvn clean`.
 */
@Deprecated
public final class InvitesResource {
    private InvitesResource() {
        // no-op
    }
}
