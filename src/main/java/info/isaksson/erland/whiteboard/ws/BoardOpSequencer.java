package info.isaksson.erland.whiteboard.ws;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * In-memory per-board monotonic sequence generator for ops.
 *
 * MVP: sequence resets on server restart.
 */
@ApplicationScoped
public class BoardOpSequencer {

    private final ConcurrentHashMap<String, AtomicLong> seqByBoard = new ConcurrentHashMap<>();

    public long next(String boardId) {
        return seqByBoard.computeIfAbsent(boardId, k -> new AtomicLong(0L)).incrementAndGet();
    }
}
