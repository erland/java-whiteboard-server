package info.isaksson.erland.whiteboard.security;

import static info.isaksson.erland.whiteboard.security.BoardAccessService.ROLE_EDITOR;
import static info.isaksson.erland.whiteboard.security.BoardAccessService.ROLE_OWNER;
import static info.isaksson.erland.whiteboard.security.BoardAccessService.ROLE_PUBLICATION_READER;
import static info.isaksson.erland.whiteboard.security.BoardAccessService.ROLE_VIEWER;

final class BoardCapabilityPolicy {

    private BoardCapabilityPolicy() {
    }

    static boolean allows(String role, boolean viaPublication, BoardCapability capability) {
        if (capability == null) {
            return false;
        }
        return switch (capability) {
            case BOARD_READ -> isMembershipReader(role);
            case BOARD_WRITE -> isOwner(role) || isEditor(role);
            case BOARD_OWNER, FACILITATE_BOARD -> isOwner(role);
            case PUBLICATION_READ -> isMembershipReader(role) || viaPublication || isPublicationReader(role);
            case COMMENT_PARTICIPATE -> isOwner(role) || isEditor(role) || isViewer(role);
            case VOTE_PARTICIPATE -> isOwner(role) || isEditor(role) || isViewer(role);
            case VOTE_OBSERVE -> isOwner(role) || isEditor(role) || isViewer(role);
            case TIMER_CONTROL -> isOwner(role) || isEditor(role);
            case TIMER_OBSERVE -> isOwner(role) || isEditor(role) || isViewer(role);
            case REACTION_EMIT, REACTION_OBSERVE -> isOwner(role) || isEditor(role) || isViewer(role);
            case PRIVATE_MODE_CONTRIBUTE -> isOwner(role) || isEditor(role) || isViewer(role);
            case PRIVATE_MODE_REVEAL -> isOwner(role);
            case PRIVATE_MODE_VIEW -> isOwner(role) || isEditor(role) || isViewer(role);
            case ASSET_USE -> isMembershipReader(role) || viaPublication || isPublicationReader(role);
            case ASSET_MANAGE -> isOwner(role) || isEditor(role);
            case LIBRARY_READ -> isMembershipReader(role) || viaPublication || isPublicationReader(role);
            case LIBRARY_SHARE, LIBRARY_MANAGE -> isOwner(role) || isEditor(role);
        };
    }

    static boolean isOwner(String role) {
        return ROLE_OWNER.equals(role);
    }

    static boolean isEditor(String role) {
        return ROLE_EDITOR.equals(role);
    }

    static boolean isViewer(String role) {
        return ROLE_VIEWER.equals(role);
    }

    static boolean isPublicationReader(String role) {
        return ROLE_PUBLICATION_READER.equals(role);
    }

    static boolean isMembershipReader(String role) {
        return isOwner(role) || isEditor(role) || isViewer(role);
    }
}
