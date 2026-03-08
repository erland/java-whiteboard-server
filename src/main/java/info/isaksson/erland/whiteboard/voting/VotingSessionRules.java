package info.isaksson.erland.whiteboard.voting;

public final class VotingSessionRules {

    private VotingSessionRules() {
    }

    public static void requireValidScope(VotingScopeType scopeType, String scopeRef) {
        if (scopeType == null) {
            throw new IllegalArgumentException("Voting scope type is required");
        }
        if (scopeType == VotingScopeType.BOARD) {
            return;
        }
        if (scopeRef == null || scopeRef.isBlank()) {
            throw new IllegalArgumentException("Voting scope ref is required for scope type " + scopeType.storageValue());
        }
    }

    public static void requireCanOpen(VotingSession session) {
        requireSession(session);
        if (session.state() != VotingSessionState.DRAFT) {
            throw new IllegalArgumentException("Only draft voting sessions can be opened");
        }
    }

    public static void requireCanClose(VotingSession session) {
        requireSession(session);
        if (session.state() != VotingSessionState.OPEN) {
            throw new IllegalArgumentException("Only open voting sessions can be closed");
        }
    }

    public static void requireCanReveal(VotingSession session) {
        requireSession(session);
        if (session.state() != VotingSessionState.CLOSED) {
            throw new IllegalArgumentException("Only closed voting sessions can be revealed");
        }
    }

    public static void requireCanCancel(VotingSession session) {
        requireSession(session);
        if (session.state() != VotingSessionState.DRAFT && session.state() != VotingSessionState.OPEN) {
            throw new IllegalArgumentException("Only draft or open voting sessions can be cancelled");
        }
    }

    public static void requireAcceptsVotes(VotingSession session) {
        requireSession(session);
        if (!session.state().acceptsVotes()) {
            throw new IllegalArgumentException("Voting session is not open for voting");
        }
    }

    public static boolean canParticipantVote(String role, VotingRules rules, boolean viaPublication) {
        if (role == null || role.isBlank()) {
            return false;
        }
        if (viaPublication) {
            return rules.allowPublishedReaderParticipation();
        }
        return switch (role) {
            case "owner", "editor" -> true;
            case "viewer" -> rules.allowViewerParticipation();
            default -> false;
        };
    }

    public static boolean canViewProgress(VotingSession session) {
        return session != null && (session.state() != VotingSessionState.OPEN || session.rules().showProgressDuringVoting());
    }

    public static boolean shouldHideIndividualVotes(VotingSession session) {
        return session != null && session.rules().anonymousVotes();
    }

    private static void requireSession(VotingSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Voting session is required");
        }
    }
}
