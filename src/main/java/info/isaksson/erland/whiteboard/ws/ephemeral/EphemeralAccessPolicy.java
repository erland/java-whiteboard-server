package info.isaksson.erland.whiteboard.ws.ephemeral;

import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.security.BoardCapability;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EphemeralAccessPolicy {

    public boolean canEmit(String permission, EphemeralEventType eventType) {
        if (permission == null || eventType == null) {
            return false;
        }
        BoardCapability capability = switch (eventType) {
            case CURSOR, VIEWPORT, PRESENCE_META -> BoardCapability.BOARD_READ;
            case FOLLOW -> BoardCapability.BOARD_WRITE;
            case REACTION -> BoardCapability.REACTION_EMIT;
            case TIMER_CONTROL -> BoardCapability.TIMER_CONTROL;
            case TIMER_STATE -> null;
        };
        return allows(permission, capability);
    }

    public boolean canObserve(String permission, String eventType) {
        if (permission == null || eventType == null || eventType.isBlank()) {
            return false;
        }
        BoardCapability capability = switch (eventType.trim().toLowerCase()) {
            case "reaction" -> BoardCapability.REACTION_OBSERVE;
            case "timer-state" -> BoardCapability.TIMER_OBSERVE;
            case "voting-session-opened", "voting-votes-updated", "voting-session-closed", "voting-results-revealed" -> BoardCapability.VOTE_OBSERVE;
            default -> BoardCapability.BOARD_READ;
        };
        return allows(permission, capability);
    }

    private boolean allows(String permission, BoardCapability capability) {
        if (capability == null) {
            return false;
        }
        boolean viaPublication = BoardAccessService.ROLE_PUBLICATION_READER.equals(permission);
        BoardAccessService.Access access = new BoardAccessService.Access(null, permission, viaPublication);
        return access.allows(capability);
    }
}
