package info.isaksson.erland.whiteboard.security;

final class BoardCapabilityPolicy {

    private BoardCapabilityPolicy() {
    }

    static boolean allows(BoardAccessService.Access access, BoardCapability capability) {
        if (access == null || capability == null) {
            return false;
        }
        return switch (capability) {
            case BOARD_READ -> access.canRead() && !access.isPublicationReader();
            case BOARD_WRITE -> access.canWrite();
            case BOARD_OWNER -> access.isOwner();
            case PUBLICATION_READ -> access.canRead();
            case COMMENT_PARTICIPATE -> access.isOwner() || access.isEditor() || access.isViewer();
            case ASSET_USE -> access.canRead();
            case ASSET_MANAGE -> access.canWrite();
            case LIBRARY_READ -> access.canRead();
            case LIBRARY_SHARE, LIBRARY_MANAGE -> access.canWrite();
            case FACILITATE,
                 TIMER_CONTROL,
                 PRIVATE_MODE_REVEAL,
                 PRIVATE_MODE_VIEW -> access.isFacilitator();
            case VOTE_PARTICIPATE,
                 TIMER_OBSERVE,
                 REACTION_EMIT,
                 REACTION_OBSERVE,
                 PRIVATE_MODE_CONTRIBUTE -> access.isParticipant();
            case VOTE_OBSERVE -> access.canObserveVotes();
        };
    }
}
