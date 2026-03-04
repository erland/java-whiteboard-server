package info.isaksson.erland.whiteboard.ws;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PresenceHub {

    public record UserPresence(String userId, Instant joinedAt) {}

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, UserPresence>> boardUsers = new ConcurrentHashMap<>();

    public Map<String, UserPresence> join(String boardId, String connectionId, String userId) {
        var users = boardUsers.computeIfAbsent(boardId, k -> new ConcurrentHashMap<>());
        users.put(connectionId, new UserPresence(userId, Instant.now()));
        return Map.copyOf(users);
    }

    public Map<String, UserPresence> leave(String boardId, String connectionId) {
        var users = boardUsers.get(boardId);
        if (users == null) return Map.of();
        users.remove(connectionId);
        if (users.isEmpty()) boardUsers.remove(boardId, users);
        return users.isEmpty() ? Map.of() : Map.copyOf(users);
    }

    public Map<String, UserPresence> snapshot(String boardId) {
        var users = boardUsers.get(boardId);
        if (users == null || users.isEmpty()) return Map.of();
        return Map.copyOf(users);
    }
}
