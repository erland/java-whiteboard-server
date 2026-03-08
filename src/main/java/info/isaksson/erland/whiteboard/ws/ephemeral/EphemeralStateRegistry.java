package info.isaksson.erland.whiteboard.ws.ephemeral;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EphemeralStateRegistry {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<EphemeralEventType, EphemeralSignal>>> boardStates = new ConcurrentHashMap<>();

    public void update(EphemeralSignal signal) {
        if (signal == null || signal.boardId() == null || signal.connectionId() == null || signal.eventType() == null) {
            return;
        }
        boardStates
                .computeIfAbsent(signal.boardId(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(signal.connectionId(), ignored -> new ConcurrentHashMap<>())
                .put(signal.eventType(), signal);
    }

    public List<EphemeralSignal> clearConnection(String boardId, String connectionId) {
        if (boardId == null || connectionId == null) {
            return List.of();
        }
        ConcurrentHashMap<String, ConcurrentHashMap<EphemeralEventType, EphemeralSignal>> board = boardStates.get(boardId);
        if (board == null) {
            return List.of();
        }
        Map<EphemeralEventType, EphemeralSignal> removed = board.remove(connectionId);
        if (board.isEmpty()) {
            boardStates.remove(boardId, board);
        }
        if (removed == null || removed.isEmpty()) {
            return List.of();
        }
        List<EphemeralSignal> cleared = new ArrayList<>();
        for (EphemeralSignal signal : removed.values()) {
            cleared.add(new EphemeralSignal(
                    signal.boardId(),
                    signal.connectionId(),
                    signal.fromUserId(),
                    signal.eventType(),
                    signal.payload(),
                    true));
        }
        return List.copyOf(cleared);
    }
}
