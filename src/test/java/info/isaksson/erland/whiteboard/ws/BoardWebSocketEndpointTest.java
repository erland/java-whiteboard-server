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
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralAccessPolicy;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralInboundMessageHandler;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralStateRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.websocket.CloseReason;

class BoardWebSocketEndpointTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void onOpen_withInviteJoin_sendsJoinedAndPresenceWithLatestSnapshot() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "invite:123", "viewer")),
                new FixedSnapshotsRepository(new BoardSnapshot("board-1", 7L, "{\"shapes\":[\"a\"]}", Instant.parse("2026-01-01T10:15:30Z"), "alice")));

        TestWsSupport.TestSessionState state = TestWsSupport.newSession(
                URI.create("ws://localhost/ws/boards/board-1?invite=my-token"),
                Map.of("invite", List.of("my-token")),
                null);
        state.session.getUserProperties().put(WsHandshakeConfigurator.PROP_CORRELATION_ID, "corr-1");

        fixture.endpoint.onOpen(state.session, "board-1");

        assertNull(state.closeReason);
        assertEquals(2, state.sentTexts.size());

        JsonNode joined = mapper.readTree(state.sentTexts.get(0));
        assertEquals("joined", joined.get("type").asText());
        assertEquals("board-1", joined.get("boardId").asText());
        assertEquals("invite:123", joined.get("yourUserId").asText());
        assertEquals(7L, joined.get("latestSnapshotVersion").asLong());
        assertEquals("a", joined.path("latestSnapshot").path("shapes").get(0).asText());
        assertEquals("corr-1", joined.get("correlationId").asText());
        assertTrue(joined.get("wsSessionId").asText().length() > 10);
        assertEquals(1, joined.withArray("users").size());
        assertEquals("invite:123", joined.withArray("users").get(0).get("userId").asText());

        JsonNode presence = mapper.readTree(state.sentTexts.get(1));
        assertEquals("presence", presence.get("type").asText());
        assertEquals("board-1", presence.get("boardId").asText());
        assertEquals(1, presence.withArray("users").size());
    }

    @Test
    void onOpen_rejectedJoin_closesSessionWithPolicyViolation() {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(false, "NOT_ALLOWED", null, null)),
                new FixedSnapshotsRepository(null));

        TestWsSupport.TestSessionState state = TestWsSupport.newSession("ws://localhost/ws/boards/board-1");

        fixture.endpoint.onOpen(state.session, "board-1");

        assertNotNull(state.closeReason);
        assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, state.closeReason.getCloseCode());
        assertEquals("Not allowed", state.closeReason.getReasonPhrase());
        assertTrue(state.sentTexts.isEmpty());
    }

    @Test
    void onOpen_whenBoardConnectionLimitReached_closesNewestSession() {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        fixture.limits.maxConnectionsPerBoard = 1;

        TestWsSupport.TestSessionState first = openSession(fixture, "board-1", "alice", "editor");
        TestWsSupport.TestSessionState second = TestWsSupport.newSession("ws://localhost/ws/boards/board-1");

        fixture.endpoint.onOpen(second.session, "board-1");

        assertNull(first.closeReason);
        assertNotNull(second.closeReason);
        assertEquals(CloseReason.CloseCodes.TRY_AGAIN_LATER, second.closeReason.getCloseCode());
        assertEquals("Board connection limit reached", second.closeReason.getReasonPhrase());
        assertTrue(second.sentTexts.isEmpty());
    }

    @Test
    void onClose_removesPresenceAndBroadcastsUpdatedPresence() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState alice = openSession(fixture, "board-1", "alice", "editor");
        TestWsSupport.TestSessionState bob = openSession(fixture, "board-1", "bob", "viewer");

        fixture.endpoint.onClose(alice.session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "bye"));

        JsonNode lastBobMessage = mapper.readTree(bob.sentTexts.get(bob.sentTexts.size() - 1));
        assertEquals("presence", lastBobMessage.get("type").asText());
        assertEquals(1, lastBobMessage.withArray("users").size());
        assertEquals("bob", lastBobMessage.withArray("users").get(0).get("userId").asText());
    }

    @Test
    void onError_closesSessionWithUnexpectedCondition() {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState state = openSession(fixture, "board-1", "alice", "editor");

        fixture.endpoint.onError(state.session, new IllegalStateException("boom"));

        assertNotNull(state.closeReason);
        assertEquals(CloseReason.CloseCodes.UNEXPECTED_CONDITION, state.closeReason.getCloseCode());
        assertEquals("Error", state.closeReason.getReasonPhrase());
    }

    @Test
    void onMessage_invalidJson_returnsBadRequestError() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState state = openSession(fixture, "board-1", "alice", "editor");

        fixture.endpoint.onMessage("{not-json", state.session);

        assertEquals(3, state.sentTexts.size());
        JsonNode error = mapper.readTree(state.sentTexts.get(2));
        assertEquals("error", error.get("type").asText());
        assertEquals("BAD_REQUEST", error.get("code").asText());
    }

    @Test
    void onMessage_rateLimited_returnsRateLimitedError() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        fixture.limits.ratePerSecond = 1;
        fixture.limits.burst = 1;
        TestWsSupport.TestSessionState state = openSession(fixture, "board-1", "alice", "editor");

        fixture.endpoint.onMessage("{\"type\":\"ping\"}", state.session);
        fixture.endpoint.onMessage("{\"type\":\"ping\"}", state.session);

        JsonNode error = mapper.readTree(state.sentTexts.get(state.sentTexts.size() - 1));
        assertEquals("error", error.get("type").asText());
        assertEquals("RATE_LIMITED", error.get("code").asText());
    }

    @Test
    void onMessage_viewerCannotPublishOp() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "viewer")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState state = openSession(fixture, "board-1", "alice", "viewer");

        fixture.endpoint.onMessage("{\"type\":\"op\",\"op\":{\"kind\":\"add\"}}", state.session);

        assertEquals(3, state.sentTexts.size());
        JsonNode error = mapper.readTree(state.sentTexts.get(2));
        assertEquals("FORBIDDEN", error.get("code").asText());
    }

    @Test
    void onMessage_editorPublishesBroadcastWithSequenceToAllSessions() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState sender = openSession(fixture, "board-1", "alice", "editor");
        TestWsSupport.TestSessionState receiver = openSession(fixture, "board-1", "bob", "viewer");

        fixture.endpoint.onMessage("{\"type\":\"op\",\"op\":{\"kind\":\"add\",\"id\":\"s1\"}}", sender.session);

        JsonNode senderOp = mapper.readTree(sender.sentTexts.get(sender.sentTexts.size() - 1));
        JsonNode receiverOp = mapper.readTree(receiver.sentTexts.get(receiver.sentTexts.size() - 1));

        assertEquals("op", senderOp.get("type").asText());
        assertEquals("board-1", senderOp.get("boardId").asText());
        assertEquals(1L, senderOp.get("seq").asLong());
        assertEquals("alice", senderOp.get("from").asText());
        assertEquals("s1", senderOp.path("op").path("id").asText());
        assertEquals(senderOp, receiverOp);
    }

    @Test
    void onMessage_viewerPublishesCursorEphemeralBroadcastToAllSessions() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "viewer")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState sender = openSession(fixture, "board-1", "alice", "viewer");
        TestWsSupport.TestSessionState receiver = openSession(fixture, "board-1", "bob", "viewer");

        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"cursor\",\"payload\":{\"x\":10,\"y\":20}}", sender.session);

        JsonNode senderEvent = mapper.readTree(sender.sentTexts.get(sender.sentTexts.size() - 1));
        JsonNode receiverEvent = mapper.readTree(receiver.sentTexts.get(receiver.sentTexts.size() - 1));

        assertEquals("ephemeral", senderEvent.get("type").asText());
        assertEquals("board-1", senderEvent.get("boardId").asText());
        assertEquals("alice", senderEvent.get("from").asText());
        assertEquals("cursor", senderEvent.get("eventType").asText());
        assertFalse(senderEvent.get("cleared").asBoolean());
        assertEquals(10, senderEvent.path("payload").path("x").asInt());
        assertEquals(senderEvent, receiverEvent);
    }

    @Test
    void onMessage_viewerCannotPublishFollowEphemeral() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "viewer")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState state = openSession(fixture, "board-1", "alice", "viewer");

        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"follow\",\"payload\":{\"targetUserId\":\"bob\"}}", state.session);

        JsonNode error = mapper.readTree(state.sentTexts.get(state.sentTexts.size() - 1));
        assertEquals("error", error.get("type").asText());
        assertEquals("FORBIDDEN", error.get("code").asText());
    }

    @Test
    void onMessage_invalidEphemeralPayload_returnsValidationError() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState state = openSession(fixture, "board-1", "alice", "editor");

        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"cursor\",\"payload\":123}", state.session);

        JsonNode error = mapper.readTree(state.sentTexts.get(state.sentTexts.size() - 1));
        assertEquals("error", error.get("type").asText());
        assertEquals("VALIDATION_ERROR", error.get("code").asText());
    }

    @Test
    void onClose_clearsEphemeralStateBeforePresenceBroadcast() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState alice = openSession(fixture, "board-1", "alice", "editor");
        TestWsSupport.TestSessionState bob = openSession(fixture, "board-1", "bob", "viewer");

        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"cursor\",\"payload\":{\"x\":1}}", alice.session);
        fixture.endpoint.onClose(alice.session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "bye"));

        JsonNode cleared = mapper.readTree(bob.sentTexts.get(bob.sentTexts.size() - 2));
        JsonNode presence = mapper.readTree(bob.sentTexts.get(bob.sentTexts.size() - 1));
        assertEquals("ephemeral", cleared.get("type").asText());
        assertTrue(cleared.get("cleared").asBoolean());
        assertEquals("cursor", cleared.get("eventType").asText());
        assertEquals("presence", presence.get("type").asText());
    }

    @Test
    void onMessage_ephemeralDoesNotAdvanceOperationSequence() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState state = openSession(fixture, "board-1", "alice", "editor");

        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"cursor\",\"payload\":{\"x\":1}}", state.session);
        fixture.endpoint.onMessage("{\"type\":\"op\",\"op\":{\"kind\":\"add\",\"id\":\"s1\"}}", state.session);

        JsonNode op = mapper.readTree(state.sentTexts.get(state.sentTexts.size() - 1));
        assertEquals("op", op.get("type").asText());
        assertEquals(1L, op.get("seq").asLong());
    }

    @Test
    void onMessage_tooLargeMessage_returnsErrorAndCloses() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        fixture.limits.maxMessageBytes = 8;
        TestWsSupport.TestSessionState state = openSession(fixture, "board-1", "alice", "editor");

        fixture.endpoint.onMessage("{\"type\":\"ping\",\"a\":1}", state.session);

        JsonNode error = mapper.readTree(state.sentTexts.get(state.sentTexts.size() - 1));
        assertEquals("MESSAGE_TOO_LARGE", error.get("code").asText());
        assertNotNull(state.closeReason);
        assertEquals(CloseReason.CloseCodes.TOO_BIG, state.closeReason.getCloseCode());
    }

    private TestWsSupport.TestSessionState openSession(EndpointFixture fixture, String boardId, String userId, String permission) {
        fixture.endpoint.lifecycleService = new WsLifecycleService(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", userId, permission)),
                new WsAuthResolver(null),
                fixture.sessionRegistry,
                fixture.presenceHub,
                fixture.ephemeralStateRegistry,
                fixture.limits,
                fixture.metrics,
                fixture.outboundSupport);
        TestWsSupport.TestSessionState state = TestWsSupport.newSession("ws://localhost/ws/boards/" + boardId);
        fixture.endpoint.onOpen(state.session, boardId);
        return state;
    }

    private EndpointFixture newFixture(BoardJoinAuthorizer authorizer, SnapshotsRepository snapshotsRepository) {
        BoardWebSocketEndpoint endpoint = new BoardWebSocketEndpoint();
        PresenceHub presenceHub = new PresenceHub();
        WsSessionRegistry sessionRegistry = new WsSessionRegistry();
        WsLimits limits = new WsLimits();
        limits.maxMessageBytes = 64 * 1024;
        limits.ratePerSecond = 20;
        limits.burst = 40;
        limits.maxConnectionsPerBoard = 64;
        limits.ephemeralRatePerSecond = 60;
        limits.ephemeralBurst = 120;
        WsMetrics metrics = new WsMetrics(new SimpleMeterRegistry());
        WsOutboundSupport outboundSupport = new WsOutboundSupport(mapper, snapshotsRepository, presenceHub, sessionRegistry, metrics);
        EphemeralStateRegistry ephemeralStateRegistry = new EphemeralStateRegistry();
        endpoint.lifecycleService = new WsLifecycleService(
                authorizer,
                new WsAuthResolver(null),
                sessionRegistry,
                presenceHub,
                ephemeralStateRegistry,
                limits,
                metrics,
                outboundSupport);
        endpoint.inboundMessageHandler = new WsInboundMessageHandler(
                mapper,
                new BoardOpSequencer(),
                limits,
                metrics,
                outboundSupport,
                new EphemeralInboundMessageHandler(new EphemeralAccessPolicy(), ephemeralStateRegistry, outboundSupport));
        return new EndpointFixture(endpoint, presenceHub, sessionRegistry, ephemeralStateRegistry, limits, metrics, outboundSupport);
    }

    private record EndpointFixture(
            BoardWebSocketEndpoint endpoint,
            PresenceHub presenceHub,
            WsSessionRegistry sessionRegistry,
            EphemeralStateRegistry ephemeralStateRegistry,
            WsLimits limits,
            WsMetrics metrics,
            WsOutboundSupport outboundSupport) {
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
