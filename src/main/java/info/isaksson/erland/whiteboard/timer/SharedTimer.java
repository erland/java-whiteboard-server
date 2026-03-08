package info.isaksson.erland.whiteboard.timer;

import java.time.Instant;

public record SharedTimer(
        String id,
        String boardId,
        SharedTimerScopeType scopeType,
        String scopeRef,
        String controllerUserId,
        String label,
        SharedTimerState state,
        long durationMs,
        long remainingMs,
        Instant startedAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isActive() {
        return state != null && state.isActive();
    }
}
