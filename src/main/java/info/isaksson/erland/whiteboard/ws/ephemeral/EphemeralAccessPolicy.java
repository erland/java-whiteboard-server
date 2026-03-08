package info.isaksson.erland.whiteboard.ws.ephemeral;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EphemeralAccessPolicy {

    public boolean canEmit(String permission, EphemeralEventType eventType) {
        if (permission == null || eventType == null) {
            return false;
        }
        String normalized = permission.trim().toLowerCase();
        return switch (eventType) {
            case CURSOR, VIEWPORT, PRESENCE_META -> "owner".equals(normalized)
                    || "editor".equals(normalized)
                    || "viewer".equals(normalized);
            case FOLLOW -> "owner".equals(normalized) || "editor".equals(normalized);
        };
    }
}
