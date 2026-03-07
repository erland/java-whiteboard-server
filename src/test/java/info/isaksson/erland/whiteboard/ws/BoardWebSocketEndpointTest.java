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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.websocket.CloseReason;

class BoardWebSocketEndpointTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void onOpen_withInviteJoin_sendsJoinedAndPresenceWithLatestSnapshot() throws Exception {
        BoardWebSocketEndpoint endpoint = newEndpoint();
        endpoint.authorizer = new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "invite:123", "viewer"));
        endpoint.snapshotsRepository = new FixedSnapshotsRepository(
                new BoardSnapshot("board-1", 7L, "{\"shapes\":[\"a\"]}", Instant.parse("2026-01-01T10:15:30Z"), "alice"));

        TestWsSupport.TestSessionState state = TestWsSupport.newSession(
                URI.create("ws://localhost/ws/boards/board-1?invite=my-token"),
                Map.of("invite", List.of("my-token")),
                null);
        state.session.getUserProperties().put(WsHandshakeConfigurator.PROP_CORRELATION_ID, "corr-1");

        endpoint.onOpen(state.session, "board-1");

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
        BoardWebSocketEndpoint endpoint = newEndpoint();
        endpoint.authorizer = new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(false, "NOT_ALLOWED", null, null));

        TestWsSupport.TestSessionState state = TestWsSupport.newSession("ws://localhost/ws/boards/board-1");

        endpoint.onOpen(state.session, "board-1");

        assertNotNull(state.closeReason);
        assertEquals(jakarta.websocket.CloseReason.CloseCodes.VIOLATED_POLICY, state.closeReason.getCloseCode());
        assertEquals("Not allowed", state.closeReason.getReasonPhrase());
        assertTrue(state.sentTexts.isEmpty());
    }

    @Test
    void onOpen_whenBoardConnectionLimitReached_closesNewestSession() {
        BoardWebSocketEndpoint endpoint = newEndpoint();
        endpoint.limits.maxConnectionsPerBoard = 1;

        TestWsSupport.TestSessionState first = openSession(endpoint, "board-1", "alice", "editor");
        TestWsSupport.TestSessionState second = TestWsSupport.newSession("ws://localhost/ws/boards/board-1");

        endpoint.onOpen(second.session, "board-1");

        assertNull(first.closeReason);
        assertNotNull(second.closeReason);
        assertEquals(CloseReason.CloseCodes.TRY_AGAIN_LATER, second.closeReason.getCloseCode());
        assertEquals("Board connection limit reached", second.closeReason.getReasonPhrase());
        assertTrue(second.sentTexts.isEmpty());
    }

    @Test
    void onClose_removesPresenceAndBroadcastsUpdatedPresence() throws Exception {
        BoardWebSocketEndpoint endpoint = newEndpoint();
        TestWsSupport.TestSessionState alice = openSession(endpoint, "board-1", "alice", "editor");
        TestWsSupport.TestSessionState bob = openSession(endpoint, "board-1", "bob", "viewer");

        endpoint.onClose(alice.session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "bye"));

        JsonNode lastBobMessage = mapper.readTree(bob.sentTexts.get(bob.sentTexts.size() - 1));
        assertEquals("presence", lastBobMessage.get("type").asText());
        assertEquals(1, lastBobMessage.withArray("users").size());
        assertEquals("bob", lastBobMessage.withArray("users").get(0).get("userId").asText());
    }

    @Test
    void onError_closesSessionWithUnexpectedCondition() {
        BoardWebSocketEndpoint endpoint = newEndpoint();
        TestWsSupport.TestSessionState state = openSession(endpoint, "board-1", "alice", "editor");

        endpoint.onError(state.session, new IllegalStateException("boom"));

        assertNotNull(state.closeReason);
        assertEquals(CloseReason.CloseCodes.UNEXPECTED_CONDITION, state.closeReason.getCloseCode());
        assertEquals("Error", state.closeReason.getReasonPhrase());
    }

    @Test
    void onMessage_invalidJson_returnsBadRequestError() throws Exception {
        BoardWebSocketEndpoint endpoint = newEndpoint();
        TestWsSupport.TestSessionState state = openSession(endpoint, "board-1", "alice", "editor");

        endpoint.onMessage("{not-json", state.session);

        assertEquals(3, state.sentTexts.size());
        JsonNode error = mapper.readTree(state.sentTexts.get(2));
        assertEquals("error", error.get("type").asText());
        assertEquals("BAD_REQUEST", error.get("code").asText());
    }

    @Test
    void onMessage_rateLimited_returnsRateLimitedError() throws Exception {
        BoardWebSocketEndpoint endpoint = newEndpoint();
        endpoint.limits.ratePerSecond = 1;
        endpoint.limits.burst = 1;
        TestWsSupport.TestSessionState state = openSession(endpoint, "board-1", "alice", "editor");

        endpoint.onMessage("{\"type\":\"ping\"}", state.session);
        endpoint.onMessage("{\"type\":\"ping\"}", state.session);

        JsonNode error = mapper.readTree(state.sentTexts.get(state.sentTexts.size() - 1));
        assertEquals("error", error.get("type").asText());
        assertEquals("RATE_LIMITED", error.get("code").asText());
    }

    @Test
    void onMessage_viewerCannotPublishOp() throws Exception {
        BoardWebSocketEndpoint endpoint = newEndpoint();
        TestWsSupport.TestSessionState state = openSession(endpoint, "board-1", "alice", "viewer");

        endpoint.onMessage("{\"type\":\"op\",\"op\":{\"kind\":\"add\"}}", state.session);

        assertEquals(3, state.sentTexts.size());
        JsonNode error = mapper.readTree(state.sentTexts.get(2));
        assertEquals("FORBIDDEN", error.get("code").asText());
    }

    @Test
    void onMessage_editorPublishesBroadcastWithSequenceToAllSessions() throws Exception {
        BoardWebSocketEndpoint endpoint = newEndpoint();
        TestWsSupport.TestSessionState sender = openSession(endpoint, "board-1", "alice", "editor");
        TestWsSupport.TestSessionState receiver = openSession(endpoint, "board-1", "bob", "viewer");

        endpoint.onMessage("{\"type\":\"op\",\"op\":{\"kind\":\"add\",\"id\":\"s1\"}}", sender.session);

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
    void onMessage_tooLargeMessage_returnsErrorAndCloses() throws Exception {
        BoardWebSocketEndpoint endpoint = newEndpoint();
        endpoint.limits.maxMessageBytes = 8;
        TestWsSupport.TestSessionState state = openSession(endpoint, "board-1", "alice", "editor");

        endpoint.onMessage("{\"type\":\"ping\",\"a\":1}", state.session);

        JsonNode error = mapper.readTree(state.sentTexts.get(state.sentTexts.size() - 1));
        assertEquals("MESSAGE_TOO_LARGE", error.get("code").asText());
        assertNotNull(state.closeReason);
        assertEquals(jakarta.websocket.CloseReason.CloseCodes.TOO_BIG, state.closeReason.getCloseCode());
    }

    private TestWsSupport.TestSessionState openSession(BoardWebSocketEndpoint endpoint, String boardId, String userId, String permission) {
        endpoint.authorizer = new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", userId, permission));
        TestWsSupport.TestSessionState state = TestWsSupport.newSession("ws://localhost/ws/boards/" + boardId);
        endpoint.onOpen(state.session, boardId);
        return state;
    }

    private BoardWebSocketEndpoint newEndpoint() {
        BoardWebSocketEndpoint endpoint = new BoardWebSocketEndpoint();
        endpoint.mapper = mapper;
        endpoint.presenceHub = new PresenceHub();
        endpoint.snapshotsRepository = new FixedSnapshotsRepository(null);
        endpoint.opSequencer = new BoardOpSequencer();
        endpoint.limits = new WsLimits();
        endpoint.limits.maxMessageBytes = 64 * 1024;
        endpoint.limits.ratePerSecond = 20;
        endpoint.limits.burst = 40;
        endpoint.limits.maxConnectionsPerBoard = 64;
        endpoint.metrics = new WsMetrics(new SimpleMeterRegistry());
        endpoint.authorizer = new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "owner"));
        endpoint.jwtParser = null;
        return endpoint;
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
