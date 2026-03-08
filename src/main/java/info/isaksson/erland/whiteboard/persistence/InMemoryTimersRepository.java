package info.isaksson.erland.whiteboard.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import info.isaksson.erland.whiteboard.timer.SharedTimer;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.arc.profile.IfBuildProfile;

@ApplicationScoped
@IfBuildProfile("test")
@Priority(1)
public class InMemoryTimersRepository implements TimersRepository {

    private final ConcurrentHashMap<String, SharedTimer> timers = new ConcurrentHashMap<>();

    @Override
    public SharedTimer create(SharedTimer timer) {
        Instant now = Instant.now();
        SharedTimer created = new SharedTimer(
                timer.id(), timer.boardId(), timer.scopeType(), timer.scopeRef(), timer.controllerUserId(), timer.label(), timer.state(),
                timer.durationMs(), timer.remainingMs(), timer.startedAt(), timer.endsAt(),
                timer.createdAt() == null ? now : timer.createdAt(),
                timer.updatedAt() == null ? now : timer.updatedAt());
        timers.put(created.id(), created);
        return created;
    }

    @Override
    public Optional<SharedTimer> update(SharedTimer timer) {
        SharedTimer updated = timers.computeIfPresent(timer.id(), (id, existing) -> timer);
        return Optional.ofNullable(updated);
    }

    @Override
    public Optional<SharedTimer> findById(String timerId) {
        return Optional.ofNullable(timers.get(timerId));
    }

    @Override
    public Optional<SharedTimer> findActiveForBoard(String boardId) {
        return timers.values().stream()
                .filter(timer -> timer.boardId().equals(boardId))
                .filter(SharedTimer::isActive)
                .max(Comparator.comparing(SharedTimer::updatedAt));
    }

    @Override
    public List<SharedTimer> listForBoard(String boardId) {
        return timers.values().stream()
                .filter(timer -> timer.boardId().equals(boardId))
                .sorted(Comparator.comparing(SharedTimer::createdAt).thenComparing(SharedTimer::id))
                .toList();
    }
}
