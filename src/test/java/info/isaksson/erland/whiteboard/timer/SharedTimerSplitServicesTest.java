package info.isaksson.erland.whiteboard.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryTimersRepository;

public class SharedTimerSplitServicesTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private InMemoryTimersRepository timersRepository;
    private InMemoryBoardsRepository boardsRepository;
    private SharedTimerCommandService commandService;
    private SharedTimerStateCalculator stateCalculator;
    private SharedTimerPayloadMapper payloadMapper;
    private String boardId;

    @BeforeEach
    void setup() {
        timersRepository = new InMemoryTimersRepository();
        boardsRepository = new InMemoryBoardsRepository();
        stateCalculator = new SharedTimerStateCalculator(timersRepository);
        commandService = new SharedTimerCommandService(timersRepository, boardsRepository, stateCalculator);
        payloadMapper = new SharedTimerPayloadMapper(mapper, stateCalculator);
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Board", "whiteboard", "standard", "alice", "active", null, null));
    }

    @Test
    void commandServiceCreatesRunningTimerAndPayloadMapperProjectsScope() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("action", "start");
        payload.put("durationMs", 60_000L);
        payload.put("label", "Focus");
        ObjectNode scope = payload.putObject("scope");
        scope.put("type", "section");
        scope.put("ref", "section-1");

        SharedTimer timer = commandService.applyControl(boardId, "alice", payload);
        JsonNode projected = payloadMapper.toPayload(timer);

        assertEquals(SharedTimerState.RUNNING.storageValue(), projected.path("state").asText());
        assertEquals("Focus", projected.path("label").asText());
        assertEquals("section", projected.path("scope").path("type").asText());
        assertEquals("section-1", projected.path("scope").path("ref").asText());
    }

    @Test
    void stateCalculatorCompletesExpiredRunningTimer() {
        Instant now = Instant.now();
        SharedTimer timer = new SharedTimer(
                "timer-1",
                boardId,
                SharedTimerScopeType.BOARD,
                boardId,
                "alice",
                null,
                SharedTimerState.RUNNING,
                5_000L,
                5_000L,
                now.minusSeconds(10),
                now.minusSeconds(1),
                now.minusSeconds(10),
                now.minusSeconds(10));
        timersRepository.create(timer);

        SharedTimer refreshed = stateCalculator.refreshRunningStateIfNeeded(timer);

        assertEquals(SharedTimerState.COMPLETED, refreshed.state());
        assertEquals(0L, refreshed.remainingMs());
        assertNull(refreshed.endsAt());
    }
}
