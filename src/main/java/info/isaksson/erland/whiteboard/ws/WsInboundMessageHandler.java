package info.isaksson.erland.whiteboard.ws;

import java.nio.charset.StandardCharsets;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class WsInboundMessageHandler {

    private final ObjectMapper mapper;
    private final BoardOpSequencer opSequencer;
    private final WsLimits limits;
    private final WsMetrics metrics;
    private final WsOutboundSupport outboundSupport;

    WsInboundMessageHandler(ObjectMapper mapper,
                            BoardOpSequencer opSequencer,
                            WsLimits limits,
                            WsMetrics metrics,
                            WsOutboundSupport outboundSupport) {
        this.mapper = mapper;
        this.opSequencer = opSequencer;
        this.limits = limits;
        this.metrics = metrics;
        this.outboundSupport = outboundSupport;
    }

    void handle(String message, Session session) {
        if (message != null && message.getBytes(StandardCharsets.UTF_8).length > limits.maxMessageBytes()) {
            metrics.incRejected("message_too_large");
            outboundSupport.send(session, new WsMessage.Error("MESSAGE_TOO_LARGE", "Message exceeds max size."));
            outboundSupport.close(session, CloseReason.CloseCodes.TOO_BIG, "Message too large");
            return;
        }

        Object rlObj = session.getUserProperties().get(WsSessionProps.RATE_LIMITER);
        if (rlObj instanceof TokenBucketRateLimiter rl && !rl.tryConsume()) {
            metrics.incRejected("rate_limited");
            outboundSupport.send(session, new WsMessage.Error("RATE_LIMITED", "Too many messages."));
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
}
