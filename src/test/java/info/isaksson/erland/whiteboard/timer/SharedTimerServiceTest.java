package info.isaksson.erland.whiteboard.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryTimersRepository;

public class SharedTimerServiceTest {

    private InMemoryTimersRepository timersRepository;
    private InMemoryBoardsRepository boardsRepository;
    private SharedTimerService service;
    private String boardId;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        timersRepository = new InMemoryTimersRepository();
        boardsRepository = new InMemoryBoardsRepository();
        service = new SharedTimerService(timersRepository, boardsRepository, mapper);
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Board", "whiteboard", "standard", "alice", "active", null, null));
    }

    @Test
    void startPauseResumeResetAndCompleteTransitionsAreSupported() {
        SharedTimer started = service.applyControl(boardId, "alice", startPayload(30_000L, "retro-1"));
        assertEquals(SharedTimerState.RUNNING, started.state());
        assertEquals(SharedTimerScopeType.BOARD, started.scopeType());

        SharedTimer paused = service.applyControl(boardId, "alice", actionPayload("pause", "retro-1"));
        assertEquals(SharedTimerState.PAUSED, paused.state());
        assertTrue(paused.remainingMs() > 0 && paused.remainingMs() <= 30_000L);

        SharedTimer resumed = service.applyControl(boardId, "alice", actionPayload("resume", "retro-1"));
        assertEquals(SharedTimerState.RUNNING, resumed.state());
        assertNotNull(resumed.endsAt());

        ObjectNode reset = actionPayload("reset", "retro-1");
        reset.put("durationMs", 45_000L);
        SharedTimer resetTimer = service.applyControl(boardId, "alice", reset);
        assertEquals(SharedTimerState.PAUSED, resetTimer.state());
        assertEquals(45_000L, resetTimer.durationMs());
        assertEquals(45_000L, resetTimer.remainingMs());

        SharedTimer completed = service.applyControl(boardId, "alice", actionPayload("complete", "retro-1"));
        assertEquals(SharedTimerState.COMPLETED, completed.state());
        assertTrue(service.current(boardId).isEmpty());
    }

    @Test
    void rejectsConflictingSecondActiveTimer() {
        service.applyControl(boardId, "alice", startPayload(20_000L, "retro-1"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.applyControl(boardId, "alice", startPayload(10_000L, "retro-2")));

        assertTrue(error.getMessage().contains("already active"));
    }

    @Test
    void currentReturnsReconnectSafePayloadStateForSectionTimer() {
        ObjectNode start = startPayload(20_000L, "retro-1");
        ObjectNode scope = start.putObject("scope");
        scope.put("type", "section");
        scope.put("ref", "section-7");
        start.put("label", "Retro vote");

        SharedTimer started = service.applyControl(boardId, "facilitator-1", start);
        SharedTimer current = service.current(boardId).orElseThrow();

        assertEquals(started.id(), current.id());
        assertEquals(SharedTimerScopeType.SECTION, current.scopeType());
        assertEquals("section-7", current.scopeRef());
        assertEquals("Retro vote", current.label());
    }

    private ObjectNode startPayload(long durationMs, String timerId) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("action", "start");
        payload.put("durationMs", durationMs);
        payload.put("timerId", timerId);
        return payload;
    }

    private ObjectNode actionPayload(String action, String timerId) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("action", action);
        payload.put("timerId", timerId);
        return payload;
    }
}
