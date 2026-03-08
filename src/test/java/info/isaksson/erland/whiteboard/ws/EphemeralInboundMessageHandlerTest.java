package info.isaksson.erland.whiteboard.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.isaksson.erland.whiteboard.persistence.InMemorySnapshotsRepository;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralAccessPolicy;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralInboundMessageHandler;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralStateRegistry;
import info.isaksson.erland.whiteboard.ws.ephemeral.ReactionPayloadValidator;
import info.isaksson.erland.whiteboard.ws.ephemeral.TimerControlPayloadValidator;
import info.isaksson.erland.whiteboard.ws.ephemeral.TimerEphemeralStateRegistry;

class EphemeralInboundMessageHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void handle_cursorDispatchesThroughSessionSignalHandler() throws Exception {
        WsMetrics metrics = new WsMetrics(new SimpleMeterRegistry());
        PresenceHub presenceHub = new PresenceHub();
        WsSessionRegistry sessionRegistry = new WsSessionRegistry();
        WsOutboundSupport outboundSupport = new WsOutboundSupport(mapper, new InMemorySnapshotsRepository(), presenceHub, sessionRegistry, metrics);
        EphemeralStateRegistry stateRegistry = new EphemeralStateRegistry();
        TimerEphemeralStateRegistry timerStateRegistry = new TimerEphemeralStateRegistry(mapper);
        EphemeralInboundMessageHandler handler = new EphemeralInboundMessageHandler(
                new EphemeralAccessPolicy(),
                stateRegistry,
                timerStateRegistry,
                new ReactionPayloadValidator(),
                new TimerControlPayloadValidator(),
                outboundSupport,
                metrics);

        TestWsSupport.TestSessionState sender = TestWsSupport.newSession("ws://localhost/ws/boards/board-1");
        sender.userProperties.put(WsSessionProps.PERMISSION, "editor");
        sender.userProperties.put(WsSessionProps.REACTION_RATE_LIMITER, new TokenBucketRateLimiter(5, 5));
        sender.userProperties.put(WsSessionProps.CONNECTION_ID, "conn-a");
        sessionRegistry.register("board-1", "conn-a", sender.session);

        TestWsSupport.TestSessionState viewer = TestWsSupport.newSession("ws://localhost/ws/boards/board-1");
        viewer.userProperties.put(WsSessionProps.PERMISSION, "viewer");
        sessionRegistry.register("board-1", "conn-b", viewer.session);

        JsonNode root = mapper.readTree("{\"eventType\":\"cursor\",\"payload\":{\"x\":10,\"y\":20}}");
        handler.handle(root, sender.session, "board-1", "alice", "editor", "conn-a");

        assertTrue(viewer.sentTexts.stream().anyMatch(text -> text.contains("\"type\":\"ephemeral\"") && text.contains("\"eventType\":\"cursor\"")));
    }

    @Test
    void handle_timerStateCannotBePublishedByClient() throws Exception {
        WsMetrics metrics = new WsMetrics(new SimpleMeterRegistry());
        WsOutboundSupport outboundSupport = new WsOutboundSupport(mapper, new InMemorySnapshotsRepository(), new PresenceHub(), new WsSessionRegistry(), metrics);
        EphemeralInboundMessageHandler handler = new EphemeralInboundMessageHandler(
                new EphemeralAccessPolicy(),
                new EphemeralStateRegistry(),
                new TimerEphemeralStateRegistry(mapper),
                new ReactionPayloadValidator(),
                new TimerControlPayloadValidator(),
                outboundSupport,
                metrics);

        TestWsSupport.TestSessionState sender = TestWsSupport.newSession("ws://localhost/ws/boards/board-1");
        JsonNode root = mapper.readTree("{\"eventType\":\"timer-state\",\"payload\":{\"timerId\":\"retro\"}}");
        handler.handle(root, sender.session, "board-1", "alice", "editor", "conn-a");

        assertEquals(1, sender.sentTexts.size());
        assertTrue(sender.sentTexts.get(0).contains("Timer state is server-managed"));
    }
}
