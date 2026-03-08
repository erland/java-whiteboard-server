package info.isaksson.erland.whiteboard.ws;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.isaksson.erland.whiteboard.domain.BoardSnapshot;
import info.isaksson.erland.whiteboard.persistence.SnapshotsRepository;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralStateRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.websocket.CloseReason;

class WsLifecycleServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void open_registersPresenceAndSendsJoinedAndPresence() throws Exception {
        WsSessionRegistry sessionRegistry = new WsSessionRegistry();
        PresenceHub presenceHub = new PresenceHub();
        FixedSnapshotsRepository snapshots = new FixedSnapshotsRepository(
                new BoardSnapshot("board-1", 2L, "{\"shapes\":[\"x\"]}", Instant.parse("2026-01-01T10:15:30Z"), "alice"));
        WsMetrics metrics = new WsMetrics(new SimpleMeterRegistry());
        WsLifecycleService service = new WsLifecycleService(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new WsAuthResolver(null),
                sessionRegistry,
                presenceHub,
                new EphemeralStateRegistry(),
                limits(),
                metrics,
                new WsOutboundSupport(mapper, snapshots, presenceHub, sessionRegistry, metrics));

        TestWsSupport.TestSessionState state = TestWsSupport.newSession(URI.create("ws://localhost/ws/boards/board-1"), Map.of(), null);
        state.session.getUserProperties().put(WsHandshakeConfigurator.PROP_CORRELATION_ID, "corr-1");

        service.open(state.session, "board-1");

        assertEquals(1, sessionRegistry.connectionCount("board-1"));
        assertEquals(2, state.sentTexts.size());
        JsonNode joined = mapper.readTree(state.sentTexts.get(0));
        assertEquals("joined", joined.get("type").asText());
        assertEquals("alice", joined.get("yourUserId").asText());
        assertEquals(2L, joined.get("latestSnapshotVersion").asLong());
        assertEquals("corr-1", joined.get("correlationId").asText());
    }

    @Test
    void close_unregistersPresenceAndBroadcastsReducedPresence() throws Exception {
        WsSessionRegistry sessionRegistry = new WsSessionRegistry();
        PresenceHub presenceHub = new PresenceHub();
        FixedSnapshotsRepository snapshots = new FixedSnapshotsRepository(null);
        WsMetrics metrics = new WsMetrics(new SimpleMeterRegistry());
        WsOutboundSupport outboundSupport = new WsOutboundSupport(mapper, snapshots, presenceHub, sessionRegistry, metrics);
        WsLifecycleService aliceService = new WsLifecycleService(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new WsAuthResolver(null),
                sessionRegistry,
                presenceHub,
                new EphemeralStateRegistry(),
                limits(),
                metrics,
                outboundSupport);
        WsLifecycleService bobService = new WsLifecycleService(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "bob", "viewer")),
                new WsAuthResolver(null),
                sessionRegistry,
                presenceHub,
                new EphemeralStateRegistry(),
                limits(),
                metrics,
                outboundSupport);

        TestWsSupport.TestSessionState alice = TestWsSupport.newSession("ws://localhost/ws/boards/board-1");
        TestWsSupport.TestSessionState bob = TestWsSupport.newSession("ws://localhost/ws/boards/board-1");
        aliceService.open(alice.session, "board-1");
        bobService.open(bob.session, "board-1");

        bobService.close(alice.session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "bye"));

        assertEquals(1, sessionRegistry.connectionCount("board-1"));
        JsonNode lastBob = mapper.readTree(bob.sentTexts.get(bob.sentTexts.size() - 1));
        assertEquals("presence", lastBob.get("type").asText());
        assertEquals(1, lastBob.withArray("users").size());
        assertEquals("bob", lastBob.withArray("users").get(0).get("userId").asText());
    }

    private WsLimits limits() {
        WsLimits limits = new WsLimits();
        limits.maxMessageBytes = 64 * 1024;
        limits.ratePerSecond = 20;
        limits.burst = 40;
        limits.ephemeralRatePerSecond = 60;
        limits.ephemeralBurst = 120;
        limits.maxConnectionsPerBoard = 64;
        return limits;
    }

    private static final class StaticBoardJoinAuthorizer extends BoardJoinAuthorizer {
        private final JoinDecision decision;

        private StaticBoardJoinAuthorizer(JoinDecision decision) {
            this.decision = decision;
        }

        @Override
        public JoinDecision authorize(String boardId, String userId, String inviteToken) {
            return decision;
        }
    }

    private static final class FixedSnapshotsRepository implements SnapshotsRepository {
        private final BoardSnapshot latest;

        private FixedSnapshotsRepository(BoardSnapshot latest) {
            this.latest = latest;
        }

        @Override
        public BoardSnapshot create(String boardId, String createdBy, String snapshotJson) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<BoardSnapshot> get(String boardId, long version) {
            return Optional.ofNullable(latest).filter(s -> s.boardId().equals(boardId) && s.version() == version);
        }

        @Override
        public Optional<BoardSnapshot> getLatest(String boardId) {
            return Optional.ofNullable(latest).filter(s -> s.boardId().equals(boardId));
        }

        @Override
        public List<Long> listVersions(String boardId) {
            return latest != null && latest.boardId().equals(boardId) ? List.of(latest.version()) : List.of();
        }
    }
}
