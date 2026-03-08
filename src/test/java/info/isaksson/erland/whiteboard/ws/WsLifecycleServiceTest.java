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

import info.isaksson.erland.whiteboard.config.FeatureToggles;
import info.isaksson.erland.whiteboard.config.ProtocolCompatibility;
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
        FixedSnapshotsRepository snapshots = new FixedSnapshotsRepository(new BoardSnapshot("board-1", 2L, "{\"shapes\":[\"x\"]}", Instant.parse("2026-01-01T10:15:30Z"), "alice"));
        WsMetrics metrics = new WsMetrics(new SimpleMeterRegistry());
        WsLifecycleService service = new WsLifecycleService(new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")), new WsAuthResolver(null), sessionRegistry, presenceHub, new EphemeralStateRegistry(), contractSupport(), limits(), metrics, new WsOutboundSupport(mapper, snapshots, presenceHub, sessionRegistry, metrics));

        TestWsSupport.TestSessionState state = TestWsSupport.newSession(URI.create("ws://localhost/ws/boards/board-1"), Map.of(), null);
        state.session.getUserProperties().put(WsHandshakeConfigurator.PROP_CORRELATION_ID, "corr-1");
        service.open(state.session, "board-1");
        JsonNode joined = mapper.readTree(state.sentTexts.get(0));
        assertEquals("joined", joined.get("type").asText());
        assertEquals("alice", joined.get("yourUserId").asText());
        assertEquals(1, joined.get("protocolVersion").asInt());
        assertEquals("corr-1", joined.get("correlationId").asText());
    }

    @Test
    void open_rejectsIncompatibleProtocolVersion() throws Exception {
        WsSessionRegistry sessionRegistry = new WsSessionRegistry();
        PresenceHub presenceHub = new PresenceHub();
        FixedSnapshotsRepository snapshots = new FixedSnapshotsRepository(null);
        WsMetrics metrics = new WsMetrics(new SimpleMeterRegistry());
        WsLifecycleService service = new WsLifecycleService(new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")), new WsAuthResolver(null), sessionRegistry, presenceHub, new EphemeralStateRegistry(), contractSupport(), limits(), metrics, new WsOutboundSupport(mapper, snapshots, presenceHub, sessionRegistry, metrics));

        TestWsSupport.TestSessionState state = TestWsSupport.newSession(URI.create("ws://localhost/ws/boards/board-1?protocolVersion=2"), Map.of("protocolVersion", List.of("2")), null);
        service.open(state.session, "board-1");
        assertNotNull(state.closeReason);
        assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, state.closeReason.getCloseCode());
        JsonNode error = mapper.readTree(state.sentTexts.get(0));
        assertEquals("INCOMPATIBLE_PROTOCOL", error.get("code").asText());
    }

    private WsContractSupport contractSupport() {
        ProtocolCompatibility compatibility = new ProtocolCompatibility();
        compatibility.apiVersion = 1;
        compatibility.wsProtocolVersion = 1;
        compatibility.requireClientWsVersion = false;
        FeatureToggles toggles = new FeatureToggles();
        toggles.publicationsEnabled = true;
        toggles.commentsEnabled = true;
        toggles.assetsEnabled = true;
        toggles.wsEphemeralEnabled = true;
        return new WsContractSupport(compatibility, toggles);
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
        private StaticBoardJoinAuthorizer(JoinDecision decision) { this.decision = decision; }
        @Override public JoinDecision authorize(String boardId, String userId, String inviteToken) { return decision; }
    }

    private static final class FixedSnapshotsRepository implements SnapshotsRepository {
        private final BoardSnapshot latest;
        private FixedSnapshotsRepository(BoardSnapshot latest) { this.latest = latest; }
        @Override public BoardSnapshot create(String boardId, String createdBy, String snapshotJson) { throw new UnsupportedOperationException(); }
        @Override public Optional<BoardSnapshot> get(String boardId, long version) { return Optional.ofNullable(latest).filter(s -> s.boardId().equals(boardId) && s.version() == version); }
        @Override public Optional<BoardSnapshot> getLatest(String boardId) { return Optional.ofNullable(latest).filter(s -> s.boardId().equals(boardId)); }
        @Override public List<Long> listVersions(String boardId) { return latest != null && latest.boardId().equals(boardId) ? List.of(latest.version()) : List.of(); }
    }
}
