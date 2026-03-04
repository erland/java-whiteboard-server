package info.isaksson.erland.whiteboard.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import info.isaksson.erland.whiteboard.domain.BoardSnapshot;
import io.quarkus.arc.profile.IfBuildProfile;

@ApplicationScoped
@IfBuildProfile("test")
@Priority(1)
public class InMemorySnapshotsRepository implements SnapshotsRepository {

    private final ConcurrentHashMap<String, ConcurrentSkipListMap<Long, BoardSnapshot>> store = new ConcurrentHashMap<>();

    @Override
    public BoardSnapshot create(String boardId, String createdBy, String snapshotJson) {
        ConcurrentSkipListMap<Long, BoardSnapshot> versions = store.computeIfAbsent(boardId, k -> new ConcurrentSkipListMap<>());
        long next = versions.isEmpty() ? 1L : (versions.lastKey() + 1L);
        BoardSnapshot s = new BoardSnapshot(boardId, next, snapshotJson, Instant.now(), createdBy);
        versions.put(next, s);
        return s;
    }

    @Override
    public Optional<BoardSnapshot> get(String boardId, long version) {
        ConcurrentSkipListMap<Long, BoardSnapshot> versions = store.get(boardId);
        if (versions == null) return Optional.empty();
        return Optional.ofNullable(versions.get(version));
    }

    @Override
    public Optional<BoardSnapshot> getLatest(String boardId) {
        ConcurrentSkipListMap<Long, BoardSnapshot> versions = store.get(boardId);
        if (versions == null || versions.isEmpty()) return Optional.empty();
        return Optional.ofNullable(versions.lastEntry().getValue());
    }

    @Override
    public List<Long> listVersions(String boardId) {
        ConcurrentSkipListMap<Long, BoardSnapshot> versions = store.get(boardId);
        if (versions == null) return List.of();
        return versions.keySet().stream().sorted(Comparator.reverseOrder()).toList();
    }
}
