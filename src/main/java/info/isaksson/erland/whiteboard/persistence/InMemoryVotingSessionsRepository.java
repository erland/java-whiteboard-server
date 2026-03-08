package info.isaksson.erland.whiteboard.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import info.isaksson.erland.whiteboard.voting.VotingSession;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@IfBuildProfile("test")
@Priority(1)
public class InMemoryVotingSessionsRepository implements VotingSessionsRepository {

    private final ConcurrentHashMap<String, VotingSession> sessions = new ConcurrentHashMap<>();

    public void clear() {
        sessions.clear();
    }

    @Override
    public VotingSession create(VotingSession session) {
        Instant now = Instant.now();
        VotingSession created = new VotingSession(
                session.id(), session.boardId(), session.scopeType(), session.scopeRef(), session.state(), session.createdByUserId(), session.rules(),
                session.createdAt() == null ? now : session.createdAt(),
                session.updatedAt() == null ? now : session.updatedAt(),
                session.openedAt(), session.closedAt(), session.revealedAt());
        sessions.put(created.id(), created);
        return created;
    }

    @Override
    public Optional<VotingSession> update(VotingSession session) {
        return Optional.ofNullable(sessions.computeIfPresent(session.id(), (id, existing) -> session));
    }

    @Override
    public Optional<VotingSession> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public List<VotingSession> listForBoard(String boardId) {
        return sessions.values().stream()
                .filter(session -> session.boardId().equals(boardId))
                .sorted(Comparator.comparing(VotingSession::createdAt).thenComparing(VotingSession::id))
                .toList();
    }
}
