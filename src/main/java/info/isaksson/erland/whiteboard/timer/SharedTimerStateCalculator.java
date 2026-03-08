package info.isaksson.erland.whiteboard.timer;

import java.time.Instant;

import info.isaksson.erland.whiteboard.persistence.TimersRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SharedTimerStateCalculator {

    private final TimersRepository timersRepository;

    @Inject
    public SharedTimerStateCalculator(TimersRepository timersRepository) {
        this.timersRepository = timersRepository;
    }

    public SharedTimer refreshRunningStateIfNeeded(SharedTimer timer) {
        if (timer == null || timer.state() != SharedTimerState.RUNNING || timer.endsAt() == null) {
            return timer;
        }
        long remainingMs = remainingMs(timer, Instant.now());
        if (remainingMs > 0) {
            if (remainingMs == timer.remainingMs()) {
                return timer;
            }
            SharedTimer refreshed = new SharedTimer(
                    timer.id(), timer.boardId(), timer.scopeType(), timer.scopeRef(), timer.controllerUserId(), timer.label(),
                    timer.state(), timer.durationMs(), remainingMs, timer.startedAt(), timer.endsAt(), timer.createdAt(), timer.updatedAt());
            return timersRepository.update(refreshed).orElse(refreshed);
        }
        SharedTimer completed = new SharedTimer(
                timer.id(), timer.boardId(), timer.scopeType(), timer.scopeRef(), timer.controllerUserId(), timer.label(),
                SharedTimerState.COMPLETED, timer.durationMs(), 0, timer.startedAt(), null, timer.createdAt(), Instant.now());
        return timersRepository.update(completed).orElse(completed);
    }

    public long remainingMs(SharedTimer timer, Instant now) {
        if (timer == null) {
            return 0;
        }
        if (timer.state() != SharedTimerState.RUNNING || timer.endsAt() == null) {
            return Math.max(0, timer.remainingMs());
        }
        return Math.max(0, timer.endsAt().toEpochMilli() - now.toEpochMilli());
    }
}
