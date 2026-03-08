package info.isaksson.erland.whiteboard.ws;

import java.nio.charset.StandardCharsets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralInboundMessageHandler;

@ApplicationScoped
class WsInboundMessageHandler {

    private final ObjectMapper mapper;
    private final BoardOpSequencer opSequencer;
    private final WsLimits limits;
    private final WsMetrics metrics;
    private final WsOutboundSupport outboundSupport;
    private final EphemeralInboundMessageHandler ephemeralInboundMessageHandler;

    @Inject
    WsInboundMessageHandler(ObjectMapper mapper,
                            BoardOpSequencer opSequencer,
                            WsLimits limits,
                            WsMetrics metrics,
                            WsOutboundSupport outboundSupport,
                            EphemeralInboundMessageHandler ephemeralInboundMessageHandler) {
        this.mapper = mapper;
        this.opSequencer = opSequencer;
        this.limits = limits;
        this.metrics = metrics;
        this.outboundSupport = outboundSupport;
        this.ephemeralInboundMessageHandler = ephemeralInboundMessageHandler;
    }

    void handle(String message, Session session) {
        if (message != null && message.getBytes(StandardCharsets.UTF_8).length > limits.maxMessageBytes()) {
            metrics.incRejected("message_too_large");
            outboundSupport.send(session, new WsMessage.Error("MESSAGE_TOO_LARGE", "Message exceeds max size."));
            outboundSupport.close(session, CloseReason.CloseCodes.TOO_BIG, "Message too large");
            return;
        }

        String boardId = (String) session.getUserProperties().get(WsSessionProps.BOARD_ID);
        String fromUserId = (String) session.getUserProperties().get(WsSessionProps.USER_ID);
        String permission = (String) session.getUserProperties().get(WsSessionProps.PERMISSION);
        String connectionId = (String) session.getUserProperties().get(WsSessionProps.CONNECTION_ID);
        if (boardId == null || fromUserId == null || connectionId == null || permission == null) {
            metrics.incRejected("not_allowed");
            outboundSupport.close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Not allowed");
            return;
        }

        JsonNode root;
        try {
            root = mapper.readTree(message);
        } catch (Exception e) {
            metrics.jsonError();
            outboundSupport.send(session, new WsMessage.Error("BAD_REQUEST", "Invalid JSON."));
            return;
        }

        String type = root.hasNonNull("type") ? root.get("type").asText() : "";
        if ("ephemeral".equals(type)) {
            if (!consumeRateLimit(session, WsSessionProps.EPHEMERAL_RATE_LIMITER, "ephemeral_rate_limited")) {
                return;
            }
            ephemeralInboundMessageHandler.handle(root, session, boardId, fromUserId, permission, connectionId);
            return;
        }

        if (!consumeRateLimit(session, WsSessionProps.RATE_LIMITER, "rate_limited")) {
            return;
        }

        if (!"op".equals(type)) {
            return;
        }

        JsonNode op = root.get("op");
        metrics.opReceived();
        if (op == null || op.isNull()) {
            outboundSupport.send(session, new WsMessage.Error("VALIDATION_ERROR", "Field 'op' is required."));
            return;
        }

        if ("viewer".equalsIgnoreCase(permission)) {
            metrics.incRejected("forbidden_op");
            outboundSupport.send(session, new WsMessage.Error("FORBIDDEN", "You do not have permission to publish operations."));
            return;
        }

        long seq = opSequencer.next(boardId);
        outboundSupport.broadcastOp(boardId, new WsMessage.Op(boardId, seq, fromUserId, op));
    }

    private boolean consumeRateLimit(Session session, String key, String rejectedReason) {
        Object rlObj = session.getUserProperties().get(key);
        if (rlObj instanceof TokenBucketRateLimiter rl && !rl.tryConsume()) {
            metrics.incRejected(rejectedReason);
            outboundSupport.send(session, new WsMessage.Error("RATE_LIMITED", "Too many messages."));
            return false;
        }
        return true;
    }
}
