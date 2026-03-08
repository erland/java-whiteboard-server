package info.isaksson.erland.whiteboard.timer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.TimersRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SharedTimerCommandService {

    private final TimersRepository timersRepository;
    private final BoardsRepository boardsRepository;
    private final SharedTimerStateCalculator stateCalculator;

    @Inject
    public SharedTimerCommandService(TimersRepository timersRepository,
                                     BoardsRepository boardsRepository,
                                     SharedTimerStateCalculator stateCalculator) {
        this.timersRepository = timersRepository;
        this.boardsRepository = boardsRepository;
        this.stateCalculator = stateCalculator;
    }

    public SharedTimer applyControl(String boardId, String actorUserId, JsonNode payload) {
        requireActiveBoard(boardId);
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("Timer payload must be an object");
        }
        String action = payload.path("action").asText("").trim().toLowerCase();
        if (action.isBlank()) {
            throw new IllegalArgumentException("Timer action is required");
        }

        SharedTimer current = timersRepository.findActiveForBoard(boardId).orElse(null);
        String requestedTimerId = textOrNull(payload.get("timerId"));
        return switch (action) {
            case "start" -> start(boardId, actorUserId, payload, current);
            case "pause" -> pause(current, requestedTimerId, actorUserId);
            case "resume" -> resume(current, requestedTimerId, actorUserId);
            case "reset" -> reset(current, requestedTimerId, payload, actorUserId);
            case "cancel" -> finish(current, requestedTimerId, SharedTimerState.CANCELLED, actorUserId);
            case "complete" -> finish(current, requestedTimerId, SharedTimerState.COMPLETED, actorUserId);
            default -> throw new IllegalArgumentException("Unsupported timer action: " + action);
        };
    }

    public Optional<SharedTimer> current(String boardId) {
        return timersRepository.findActiveForBoard(boardId)
                .map(stateCalculator::refreshRunningStateIfNeeded);
    }

    private SharedTimer start(String boardId, String actorUserId, JsonNode payload, SharedTimer existing) {
        SharedTimerRules.requireStartAllowed(existing);
        long durationMs = payload.path("durationMs").asLong();
        SharedTimerRules.requireDuration(durationMs);
        Instant now = Instant.now();
        SharedTimerScopeType scopeType = extractScopeType(payload.get("scope"));
        String scopeRef = extractScopeRef(boardId, payload.get("scope"), scopeType);
        SharedTimer timer = new SharedTimer(
                normalizeTimerId(payload),
                boardId,
                scopeType,
                scopeRef,
                actorUserId,
                textOrNull(payload.get("label")),
                SharedTimerState.RUNNING,
                durationMs,
                durationMs,
                now,
                now.plusMillis(durationMs),
                now,
                now
        );
        return timersRepository.create(timer);
    }

    private SharedTimer pause(SharedTimer current, String requestedTimerId, String actorUserId) {
        SharedTimerRules.requireCanPause(current);
        SharedTimerRules.requireTimerIdMatches(current, requestedTimerId);
        Instant now = Instant.now();
        long remainingMs = stateCalculator.remainingMs(current, now);
        SharedTimer updated = new SharedTimer(
                current.id(), current.boardId(), current.scopeType(), current.scopeRef(), actorUserId, current.label(),
                remainingMs <= 0 ? SharedTimerState.COMPLETED : SharedTimerState.PAUSED,
                current.durationMs(), Math.max(0, remainingMs), current.startedAt(), null, current.createdAt(), now);
        return timersRepository.update(updated).orElseThrow(() -> new IllegalStateException("Updated timer not found"));
    }

    private SharedTimer resume(SharedTimer current, String requestedTimerId, String actorUserId) {
        SharedTimerRules.requireCanResume(current);
        SharedTimerRules.requireTimerIdMatches(current, requestedTimerId);
        Instant now = Instant.now();
        long remainingMs = Math.max(0, current.remainingMs());
        SharedTimer updated = new SharedTimer(
                current.id(), current.boardId(), current.scopeType(), current.scopeRef(), actorUserId, current.label(),
                remainingMs <= 0 ? SharedTimerState.COMPLETED : SharedTimerState.RUNNING,
                current.durationMs(), remainingMs, now, remainingMs <= 0 ? null : now.plusMillis(remainingMs), current.createdAt(), now);
        return timersRepository.update(updated).orElseThrow(() -> new IllegalStateException("Updated timer not found"));
    }

    private SharedTimer reset(SharedTimer current, String requestedTimerId, JsonNode payload, String actorUserId) {
        SharedTimerRules.requireCanReset(current);
        SharedTimerRules.requireTimerIdMatches(current, requestedTimerId);
        long durationMs = payload.hasNonNull("durationMs") ? payload.path("durationMs").asLong() : current.durationMs();
        SharedTimerRules.requireDuration(durationMs);
        Instant now = Instant.now();
        SharedTimerScopeType scopeType = payload.has("scope") ? extractScopeType(payload.get("scope")) : current.scopeType();
        String scopeRef = payload.has("scope") ? extractScopeRef(current.boardId(), payload.get("scope"), scopeType) : current.scopeRef();
        SharedTimer updated = new SharedTimer(
                current.id(), current.boardId(), scopeType, scopeRef, actorUserId,
                payload.has("label") ? textOrNull(payload.get("label")) : current.label(),
                SharedTimerState.PAUSED,
                durationMs,
                durationMs,
                null,
                null,
                current.createdAt(),
                now);
        return timersRepository.update(updated).orElseThrow(() -> new IllegalStateException("Updated timer not found"));
    }

    private SharedTimer finish(SharedTimer current, String requestedTimerId, SharedTimerState finalState, String actorUserId) {
        SharedTimerRules.requireCanFinish(current);
        SharedTimerRules.requireTimerIdMatches(current, requestedTimerId);
        Instant now = Instant.now();
        SharedTimer updated = new SharedTimer(
                current.id(), current.boardId(), current.scopeType(), current.scopeRef(), actorUserId, current.label(),
                finalState, current.durationMs(), 0, current.startedAt(), null, current.createdAt(), now);
        return timersRepository.update(updated).orElseThrow(() -> new IllegalStateException("Updated timer not found"));
    }

    private void requireActiveBoard(String boardId) {
        boardsRepository.findById(boardId)
                .filter(board -> "active".equals(board.status()))
                .orElseThrow(() -> new IllegalArgumentException("Board not found or not active"));
    }

    private String normalizeTimerId(JsonNode payload) {
        String timerId = textOrNull(payload.get("timerId"));
        return timerId == null ? UUID.randomUUID().toString() : timerId;
    }

    private SharedTimerScopeType extractScopeType(JsonNode scopeNode) {
        if (scopeNode == null || scopeNode.isNull()) {
            return SharedTimerScopeType.BOARD;
        }
        return SharedTimerScopeType.fromStorageValue(textOrNull(scopeNode.get("type")));
    }

    private String extractScopeRef(String boardId, JsonNode scopeNode, SharedTimerScopeType scopeType) {
        if (scopeType == SharedTimerScopeType.BOARD) {
            return boardId;
        }
        String ref = scopeNode == null ? null : textOrNull(scopeNode.get("ref"));
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("Timer scope reference is required for scope type " + scopeType.storageValue());
        }
        return ref;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
