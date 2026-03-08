package info.isaksson.erland.whiteboard.ws.ephemeral;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryTimersRepository;
import info.isaksson.erland.whiteboard.timer.SharedTimer;
import info.isaksson.erland.whiteboard.timer.SharedTimerService;

@ApplicationScoped
public class TimerEphemeralStateRegistry {

    private final SharedTimerService sharedTimerService;

    @Inject
    public TimerEphemeralStateRegistry(SharedTimerService sharedTimerService) {
        this.sharedTimerService = sharedTimerService;
    }

    public TimerEphemeralStateRegistry(ObjectMapper mapper) {
        InMemoryBoardsRepository boardsRepository = new InMemoryBoardsRepository();
        boardsRepository.create(new Board("board-1", "Test Board", "whiteboard", "standard", "alice", "active", Instant.now(), Instant.now()));
        boardsRepository.create(new Board("board-2", "Test Board 2", "whiteboard", "standard", "alice", "active", Instant.now(), Instant.now()));
        this.sharedTimerService = new SharedTimerService(new InMemoryTimersRepository(), boardsRepository, mapper);
    }

    public synchronized JsonNode applyControl(String boardId, String connectionId, String fromUserId, JsonNode payload) {
        SharedTimer timer = sharedTimerService.applyControl(boardId, fromUserId, payload);
        return toPayload(timer, connectionId, fromUserId);
    }

    public Optional<JsonNode> current(String boardId) {
        return sharedTimerService.current(boardId)
                .map(timer -> toPayload(timer, null, timer.controllerUserId()));
    }

    private JsonNode toPayload(SharedTimer timer, String connectionId, String fromUserId) {
        com.fasterxml.jackson.databind.node.ObjectNode payload = (com.fasterxml.jackson.databind.node.ObjectNode) sharedTimerService.toPayload(timer);
        if (fromUserId == null) {
            payload.putNull("from");
        } else {
            payload.put("from", fromUserId);
        }
        if (connectionId == null) {
            payload.putNull("connectionId");
        } else {
            payload.put("connectionId", connectionId);
        }
        return payload;
    }
}
