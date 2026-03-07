package info.isaksson.erland.whiteboard.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;

@ApplicationScoped
class WsSessionRegistry {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Session>> boardSessions = new ConcurrentHashMap<>();

    int connectionCount(String boardId) {
        var sessions = boardSessions.get(boardId);
        return sessions == null ? 0 : sessions.size();
    }

    void register(String boardId, String connectionId, Session session) {
        boardSessions.computeIfAbsent(boardId, ignored -> new ConcurrentHashMap<>()).put(connectionId, session);
    }

    void unregister(String boardId, String connectionId) {
        var sessions = boardSessions.get(boardId);
        if (sessions == null) {
            return;
        }
        sessions.remove(connectionId);
        if (sessions.isEmpty()) {
            boardSessions.remove(boardId, sessions);
        }
    }

    Map<String, Session> sessions(String boardId) {
        var sessions = boardSessions.get(boardId);
        return sessions == null ? Map.of() : Map.copyOf(sessions);
    }
}
