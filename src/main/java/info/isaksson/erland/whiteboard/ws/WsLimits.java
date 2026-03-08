package info.isaksson.erland.whiteboard.ws;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Configurable runtime limits for WebSocket MVP.
 */
@ApplicationScoped
public class WsLimits {

    @ConfigProperty(name = "whiteboard.limits.ws.max-message-bytes", defaultValue = "65536")
    int maxMessageBytes;

    @ConfigProperty(name = "whiteboard.limits.ws.rate.per-second", defaultValue = "20")
    int ratePerSecond;

    @ConfigProperty(name = "whiteboard.limits.ws.rate.burst", defaultValue = "40")
    int burst;

    @ConfigProperty(name = "whiteboard.limits.ws.ephemeral-rate.per-second", defaultValue = "60")
    int ephemeralRatePerSecond;

    @ConfigProperty(name = "whiteboard.limits.ws.ephemeral-rate.burst", defaultValue = "120")
    int ephemeralBurst;

    @ConfigProperty(name = "whiteboard.limits.ws.max-connections-per-board", defaultValue = "64")
    int maxConnectionsPerBoard;

    public int maxMessageBytes() { return maxMessageBytes; }

    public int ratePerSecond() { return ratePerSecond; }

    public int burst() { return burst; }

    public int ephemeralRatePerSecond() { return ephemeralRatePerSecond; }

    public int ephemeralBurst() { return ephemeralBurst; }

    public int maxConnectionsPerBoard() { return maxConnectionsPerBoard; }
}
