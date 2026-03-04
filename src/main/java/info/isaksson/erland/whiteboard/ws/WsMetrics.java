package info.isaksson.erland.whiteboard.ws;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Basic metrics for realtime MVP.
 */
@ApplicationScoped
public class WsMetrics {

    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final Counter opsReceived;
    private final Counter errors;

    @Inject
    public WsMetrics(MeterRegistry registry) {
        Gauge.builder("whiteboard.ws.connections.active", activeConnections, AtomicInteger::get)
                .description("Active websocket connections")
                .register(registry);

        this.opsReceived = Counter.builder("whiteboard.ws.ops.received")
                .description("Number of op messages received")
                .register(registry);

        this.errors = Counter.builder("whiteboard.ws.errors")
                .description("Number of websocket processing errors")
                .register(registry);
    }

    public void connectionOpened() { activeConnections.incrementAndGet(); }

    public void connectionClosed() { activeConnections.decrementAndGet(); }

    public void opReceived() { opsReceived.increment(); }

    public void error() { errors.increment(); }
}
