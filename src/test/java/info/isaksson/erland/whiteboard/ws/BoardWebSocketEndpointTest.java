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
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralAccessPolicy;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralInboundMessageHandler;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralStateRegistry;
import info.isaksson.erland.whiteboard.ws.ephemeral.ReactionPayloadValidator;
import info.isaksson.erland.whiteboard.ws.ephemeral.TimerControlPayloadValidator;
import info.isaksson.erland.whiteboard.ws.ephemeral.TimerEphemeralStateRegistry;
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
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "owner")),
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
        TestWsSupport.TestSessionState alice = openSession(fixture, "board-1", "alice", "owner");
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
    void onOpen_joinedIncludesProtocolVersionAndCapabilities() throws Exception {
        EndpointFixture fixture = newFixture(new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")), new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState state = openSession(fixture, "board-1", "alice", "editor");
        JsonNode joined = mapper.readTree(state.sentTexts.get(0));
        assertEquals(1, joined.get("protocolVersion").asInt());
        assertTrue(joined.withArray("capabilities").toString().contains("ws-ephemeral"));
    }

    @Test
    void onOpen_incompatibleProtocolVersion_returnsErrorAndCloses() throws Exception {
        EndpointFixture fixture = newFixture(new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")), new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState state = TestWsSupport.newSession(URI.create("ws://localhost/ws/boards/board-1?protocolVersion=2"), Map.of("protocolVersion", List.of("2")), null);
        fixture.endpoint.onOpen(state.session, "board-1");
        JsonNode error = mapper.readTree(state.sentTexts.get(0));
        assertEquals("INCOMPATIBLE_PROTOCOL", error.get("code").asText());
        assertNotNull(state.closeReason);
        assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, state.closeReason.getCloseCode());
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
    void onMessage_whenEphemeralDisabled_returnsFeatureDisabled() throws Exception {
        EndpointFixture fixture = newFixture(new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")), new FixedSnapshotsRepository(null), false);
        TestWsSupport.TestSessionState state = openSession(fixture, "board-1", "alice", "editor", false);
        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"cursor\",\"payload\":{\"x\":1}}", state.session);
        JsonNode error = mapper.readTree(state.sentTexts.get(state.sentTexts.size() - 1));
        assertEquals("FEATURE_DISABLED", error.get("code").asText());
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


    @Test
    void onMessage_reactionBroadcastsForViewerSessions() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "viewer")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState alice = openSession(fixture, "board-1", "alice", "viewer");
        TestWsSupport.TestSessionState bob = openSession(fixture, "board-1", "bob", "viewer");

        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"reaction\",\"payload\":{\"reactionType\":\"thumbs-up\",\"durationMs\":1200}}", alice.session);

        JsonNode reaction = mapper.readTree(bob.sentTexts.get(bob.sentTexts.size() - 1));
        assertEquals("ephemeral", reaction.get("type").asText());
        assertEquals("reaction", reaction.get("eventType").asText());
        assertEquals("thumbs-up", reaction.get("payload").get("reactionType").asText());
    }

    @Test
    void onMessage_invalidReactionPayload_returnsValidationError() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "viewer")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState alice = openSession(fixture, "board-1", "alice", "viewer");

        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"reaction\",\"payload\":{\"reactionType\":\"\"}}", alice.session);

        JsonNode error = mapper.readTree(alice.sentTexts.get(alice.sentTexts.size() - 1));
        assertEquals("VALIDATION_ERROR", error.get("code").asText());
    }

    @Test
    void onMessage_reactionRateLimitUsesDedicatedLimiter() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "viewer")),
                new FixedSnapshotsRepository(null));
        fixture.limits.reactionBurst = 1;
        fixture.limits.reactionRatePerSecond = 1;
        TestWsSupport.TestSessionState alice = openSession(fixture, "board-1", "alice", "viewer");

        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"reaction\",\"payload\":{\"reactionType\":\"thumbs-up\"}}", alice.session);
        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"reaction\",\"payload\":{\"reactionType\":\"thumbs-up\"}}", alice.session);

        JsonNode error = mapper.readTree(alice.sentTexts.get(alice.sentTexts.size() - 1));
        assertEquals("RATE_LIMITED", error.get("code").asText());
    }

    @Test
    void onMessage_timerControlBroadcastsTimerState() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState alice = openSession(fixture, "board-1", "alice", "editor");
        TestWsSupport.TestSessionState bob = openSession(fixture, "board-1", "bob", "viewer");

        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"timer-control\",\"payload\":{\"action\":\"start\",\"durationMs\":30000,\"timerId\":\"retro-timer\"}}", alice.session);

        JsonNode timerState = mapper.readTree(bob.sentTexts.get(bob.sentTexts.size() - 1));
        assertEquals("ephemeral", timerState.get("type").asText());
        assertEquals("timer-state", timerState.get("eventType").asText());
        assertEquals("running", timerState.get("payload").get("state").asText());
        assertEquals("retro-timer", timerState.get("payload").get("timerId").asText());
    }

    @Test
    void onMessage_timerControlForbiddenForViewer() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "viewer")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState alice = openSession(fixture, "board-1", "alice", "viewer");

        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"timer-control\",\"payload\":{\"action\":\"start\",\"durationMs\":30000}}", alice.session);

        JsonNode error = mapper.readTree(alice.sentTexts.get(alice.sentTexts.size() - 1));
        assertEquals("FORBIDDEN", error.get("code").asText());
    }


    @Test
    void onMessage_startingSecondTimerReturnsValidationError() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState alice = openSession(fixture, "board-1", "alice", "editor");

        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"timer-control\",\"payload\":{\"action\":\"start\",\"durationMs\":30000,\"timerId\":\"retro-timer\"}}", alice.session);
        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"timer-control\",\"payload\":{\"action\":\"start\",\"durationMs\":15000,\"timerId\":\"retro-timer-2\"}}", alice.session);

        JsonNode error = mapper.readTree(alice.sentTexts.get(alice.sentTexts.size() - 1));
        assertEquals("VALIDATION_ERROR", error.get("code").asText());
        assertTrue(error.get("message").asText().contains("already active"));
    }

    @Test
    void onOpen_whenTimerStateExists_replaysCurrentTimerStateToNewSession() throws Exception {
        EndpointFixture fixture = newFixture(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", "alice", "editor")),
                new FixedSnapshotsRepository(null));
        TestWsSupport.TestSessionState alice = openSession(fixture, "board-1", "alice", "editor");
        fixture.endpoint.onMessage("{\"type\":\"ephemeral\",\"eventType\":\"timer-control\",\"payload\":{\"action\":\"start\",\"durationMs\":30000,\"timerId\":\"retro-timer\"}}", alice.session);

        TestWsSupport.TestSessionState bob = openSession(fixture, "board-1", "bob", "viewer");

        JsonNode joined = mapper.readTree(bob.sentTexts.get(0));
        JsonNode replay = mapper.readTree(bob.sentTexts.get(1));
        assertEquals("joined", joined.get("type").asText());
        assertEquals("timer-state", replay.get("eventType").asText());
        assertEquals("retro-timer", replay.get("payload").get("timerId").asText());
    }

    private TestWsSupport.TestSessionState openSession(EndpointFixture fixture, String boardId, String userId, String permission) {
        return openSession(fixture, boardId, userId, permission, true);
    }

    private TestWsSupport.TestSessionState openSession(EndpointFixture fixture, String boardId, String userId, String permission, boolean ephemeralEnabled) {
        fixture.endpoint.lifecycleService = new WsLifecycleService(
                new StaticBoardJoinAuthorizer(new BoardJoinAuthorizer.JoinDecision(true, "OK", userId, permission)),
                new WsAuthResolver(null),
                fixture.sessionRegistry,
                fixture.presenceHub,
                fixture.ephemeralStateRegistry,
                fixture.timerStateRegistry,
                contractSupport(ephemeralEnabled),
                fixture.limits,
                fixture.metrics,
                fixture.outboundSupport);
        TestWsSupport.TestSessionState state = TestWsSupport.newSession("ws://localhost/ws/boards/" + boardId);
        fixture.endpoint.onOpen(state.session, boardId);
        return state;
    }

    private EndpointFixture newFixture(BoardJoinAuthorizer authorizer, SnapshotsRepository snapshotsRepository) {
        return newFixture(authorizer, snapshotsRepository, true);
    }

    private EndpointFixture newFixture(BoardJoinAuthorizer authorizer, SnapshotsRepository snapshotsRepository, boolean ephemeralEnabled) {
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
        limits.reactionRatePerSecond = 8;
        limits.reactionBurst = 16;
        WsMetrics metrics = new WsMetrics(new SimpleMeterRegistry());
        WsOutboundSupport outboundSupport = new WsOutboundSupport(mapper, snapshotsRepository, presenceHub, sessionRegistry, metrics);
        EphemeralStateRegistry ephemeralStateRegistry = new EphemeralStateRegistry();
        TimerEphemeralStateRegistry timerStateRegistry = new TimerEphemeralStateRegistry(mapper);
        endpoint.lifecycleService = new WsLifecycleService(
                authorizer,
                new WsAuthResolver(null),
                sessionRegistry,
                presenceHub,
                ephemeralStateRegistry,
                timerStateRegistry,
                contractSupport(ephemeralEnabled),
                limits,
                metrics,
                outboundSupport);
        endpoint.inboundMessageHandler = new WsInboundMessageHandler(
                mapper,
                new BoardOpSequencer(),
                limits,
                metrics,
                outboundSupport,
                new EphemeralInboundMessageHandler(new EphemeralAccessPolicy(), ephemeralStateRegistry, timerStateRegistry, new ReactionPayloadValidator(), new TimerControlPayloadValidator(), outboundSupport, metrics),
                contractSupport(ephemeralEnabled));
        return new EndpointFixture(endpoint, presenceHub, sessionRegistry, ephemeralStateRegistry, timerStateRegistry, limits, metrics, outboundSupport);
    }

    private WsContractSupport contractSupport(boolean ephemeralEnabled) {
        ProtocolCompatibility compatibility = new ProtocolCompatibility();
        compatibility.apiVersion = 1;
        compatibility.wsProtocolVersion = 1;
        compatibility.requireClientWsVersion = false;
        FeatureToggles toggles = new FeatureToggles();
        toggles.publicationsEnabled = true;
        toggles.commentsEnabled = true;
        toggles.assetsEnabled = true;
        toggles.wsEphemeralEnabled = ephemeralEnabled;
        return new WsContractSupport(compatibility, toggles);
    }

    private record EndpointFixture(
            BoardWebSocketEndpoint endpoint,
            PresenceHub presenceHub,
            WsSessionRegistry sessionRegistry,
            EphemeralStateRegistry ephemeralStateRegistry,
            TimerEphemeralStateRegistry timerStateRegistry,
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
