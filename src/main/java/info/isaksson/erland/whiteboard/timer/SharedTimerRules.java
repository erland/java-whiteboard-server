package info.isaksson.erland.whiteboard.timer;

public final class SharedTimerRules {

    private SharedTimerRules() {
    }

    public static void requireStartAllowed(SharedTimer existing) {
        if (existing != null && existing.isActive()) {
            throw new IllegalStateException("A shared timer is already active for this board");
        }
    }

    public static void requireCanPause(SharedTimer timer) {
        requireState(timer, SharedTimerState.RUNNING, "Timer can only be paused when running");
    }

    public static void requireCanResume(SharedTimer timer) {
        requireState(timer, SharedTimerState.PAUSED, "Timer can only be resumed when paused");
    }

    public static void requireCanReset(SharedTimer timer) {
        if (timer == null || !timer.isActive()) {
            throw new IllegalStateException("Timer can only be reset when an active timer exists");
        }
    }

    public static void requireCanFinish(SharedTimer timer) {
        if (timer == null || !timer.isActive()) {
            throw new IllegalStateException("Timer can only be finished when an active timer exists");
        }
    }

    public static void requireTimerIdMatches(SharedTimer timer, String requestedTimerId) {
        if (requestedTimerId == null || requestedTimerId.isBlank() || timer == null) {
            return;
        }
        if (!timer.id().equals(requestedTimerId)) {
            throw new IllegalStateException("Requested timer does not match the active timer");
        }
    }

    public static void requireDuration(long durationMs) {
        if (durationMs < 1000 || durationMs > 86_400_000L) {
            throw new IllegalArgumentException("Timer duration must be between 1000 and 86400000 milliseconds");
        }
    }

    private static void requireState(SharedTimer timer, SharedTimerState expected, String message) {
        if (timer == null || timer.state() != expected) {
            throw new IllegalStateException(message);
        }
    }
}
