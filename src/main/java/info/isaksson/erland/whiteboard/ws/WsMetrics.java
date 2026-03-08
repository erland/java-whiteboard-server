package info.isaksson.erland.whiteboard.ws;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small, focused metrics helper for the websocket layer.
 *
 * The metrics are intentionally simple (counters + a per-board connection gauge)
 * to keep the implementation light while still giving useful operational signals.
 */
@ApplicationScoped
public class WsMetrics {

    private final MeterRegistry registry;

    private final Counter connectionsOpened;
    private final Counter connectionsClosed;
    private final Counter opsReceived;
    private final Counter opsBroadcast;
    private final Counter ephemeralBroadcast;
    private final Counter presenceBroadcast;
    private final Counter joinsAccepted;
    private final Counter jsonErrors;
    private final Counter errors;

    // Gauge state
    private final ConcurrentHashMap<String, AtomicInteger> boardConnections = new ConcurrentHashMap<>();

    @Inject
    public WsMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.connectionsOpened = registry.counter("whiteboard_ws_connections_open_total");
        this.connectionsClosed = registry.counter("whiteboard_ws_connections_closed_total");
        this.opsReceived = registry.counter("whiteboard_ws_ops_in_total");
        this.opsBroadcast = registry.counter("whiteboard_ws_ops_out_total");
        this.ephemeralBroadcast = registry.counter("whiteboard_ws_ephemeral_out_total");
        this.presenceBroadcast = registry.counter("whiteboard_ws_presence_out_total");
        this.joinsAccepted = registry.counter("whiteboard_ws_joins_accepted_total");
        this.jsonErrors = registry.counter("whiteboard_ws_json_errors_total");
        this.errors = registry.counter("whiteboard_ws_errors_total");
    }

    public void connectionOpened() {
        connectionsOpened.increment();
    }

    public void connectionClosed() {
        connectionsClosed.increment();
    }

    public void connectionOpened(String boardId) {
        connectionOpened();
        if (boardId == null) {
            return;
        }
        AtomicInteger ai = boardConnections.computeIfAbsent(boardId, id -> {
            AtomicInteger x = new AtomicInteger(0);
            Gauge.builder("whiteboard_ws_board_connections", x, AtomicInteger::get)
                    .description("Active websocket connections per board")
                    .tag("boardId", id)
                    .register(registry);
            return x;
        });
        ai.incrementAndGet();
    }

    public void connectionClosed(String boardId) {
        connectionClosed();
        if (boardId == null) {
            return;
        }
        AtomicInteger ai = boardConnections.get(boardId);
        if (ai != null) {
            ai.decrementAndGet();
        }
    }

    public void joinAccepted() {
        joinsAccepted.increment();
    }

    public void opReceived() {
        opsReceived.increment();
    }

    public void opBroadcast() {
        opsBroadcast.increment();
    }

    public void ephemeralBroadcast() {
        ephemeralBroadcast.increment();
    }

    public void presenceBroadcast() {
        presenceBroadcast.increment();
    }

    public void jsonError() {
        jsonErrors.increment();
    }

    public void error() {
        errors.increment();
    }

    public void incRejected(String reason) {
        Counter.builder("whiteboard_ws_rejected_total")
                .tag("reason", reason == null ? "unknown" : reason)
                .register(registry)
                .increment();
    }
}
