package info.isaksson.erland.whiteboard.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import info.isaksson.erland.whiteboard.domain.Board;
import io.quarkus.arc.profile.IfBuildProfile;

@ApplicationScoped
@IfBuildProfile("test")
@Priority(1)
public class InMemoryBoardsRepository implements BoardsRepository {

    private final ConcurrentHashMap<String, Board> boards = new ConcurrentHashMap<>();

    @Override
    public Board create(Board board) {
        Instant now = Instant.now();
        Board created = new Board(
                board.id(),
                board.name(),
                board.type(),
                board.ownerUserId(),
                board.status(),
                now,
                now
        );
        boards.put(created.id(), created);
        return created;
    }

    @Override
    public List<Board> listForOwner(String ownerUserId) {
        return boards.values().stream()
                .filter(b -> b.ownerUserId().equals(ownerUserId))
                .filter(b -> !"deleted".equals(b.status()))
                .sorted(Comparator.comparing(Board::updatedAt).reversed())
                .toList();
    }

    @Override
    public Optional<Board> findById(String id) {
        return Optional.ofNullable(boards.get(id));
    }

    @Override
    public Board updateMetadata(String id, String ownerUserId, String name, String type) {
        return boards.compute(id, (k, existing) -> {
            if (existing == null) return null;
            if (!existing.ownerUserId().equals(ownerUserId)) return existing;
            if (!"active".equals(existing.status())) return existing;
            Instant now = Instant.now();
            return new Board(existing.id(), name, type, existing.ownerUserId(), existing.status(), existing.createdAt(), now);
        });
    }

    @Override
    public boolean archive(String id, String ownerUserId) {
        return boards.computeIfPresent(id, (k, existing) -> {
            if (!existing.ownerUserId().equals(ownerUserId)) return existing;
            if ("deleted".equals(existing.status())) return existing;
            Instant now = Instant.now();
            return new Board(existing.id(), existing.name(), existing.type(), existing.ownerUserId(), "archived", existing.createdAt(), now);
        }) != null;
    }
}
