package info.isaksson.erland.whiteboard.ws;

import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ServerEndpoint("/ws/boards/{boardId}")
@ApplicationScoped
public class BoardWebSocketEndpoint {

    @Inject
    ObjectMapper mapper;

    @Inject
    PresenceHub presenceHub;

    @Inject
    BoardJoinAuthorizer authorizer;

    /**
     * Per-board connected sessions.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Session>> boardSessions = new java.util.concurrent.ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("boardId") String boardId) {
        String connectionId = UUID.randomUUID().toString();
        session.getUserProperties().put("connectionId", connectionId);
        session.getUserProperties().put("boardId", boardId);

        String userId = firstQueryParam(session, "userId");
        String inviteToken = firstQueryParam(session, "invite");

        var decision = authorizer.authorize(boardId, userId, inviteToken);
        if (!decision.allowed()) {
            close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Not allowed");
            return;
        }

        String effectiveUserId = decision.effectiveUserId();
        session.getUserProperties().put("userId", effectiveUserId);

        boardSessions.computeIfAbsent(boardId, k -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(connectionId, session);

        Map<String, PresenceHub.UserPresence> users = presenceHub.join(boardId, connectionId, effectiveUserId);

        // Send joined to this session
        send(session, new WsMessage.Joined(boardId, effectiveUserId, usersToJson(users)));

        // Broadcast presence to all sessions on this board
        broadcastPresence(boardId);
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        String boardId = (String) session.getUserProperties().get("boardId");
        String connectionId = (String) session.getUserProperties().get("connectionId");

        if (boardId == null || connectionId == null) return;

        presenceHub.leave(boardId, connectionId);

        var sessions = boardSessions.get(boardId);
        if (sessions != null) {
            sessions.remove(connectionId);
            if (sessions.isEmpty()) {
                boardSessions.remove(boardId, sessions);
            }
        }

        broadcastPresence(boardId);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        // Best effort: close. Errors are expected when clients disconnect abruptly.
        try {
            close(session, CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Error");
        } catch (Exception ignored) {
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String boardId = (String) session.getUserProperties().get("boardId");
        String fromUserId = (String) session.getUserProperties().get("userId");
        String connectionId = (String) session.getUserProperties().get("connectionId");
        if (boardId == null || fromUserId == null || connectionId == null) {
            close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Not allowed");
            return;
        }

        JsonNode root;
        try {
            root = mapper.readTree(message);
        } catch (Exception e) {
            send(session, new WsMessage.Error("BAD_REQUEST", "Invalid JSON."));
            return;
        }

        String type = root.hasNonNull("type") ? root.get("type").asText() : "";
        if ("op".equals(type)) {
            JsonNode op = root.get("op");
            if (op == null || op.isNull()) {
                send(session, new WsMessage.Error("VALIDATION_ERROR", "Field 'op' is required."));
                return;
            }
            broadcastOp(boardId, connectionId, new WsMessage.Op(boardId, fromUserId, op));
            return;
        }

        // Ignore unknown message types for MVP
    }

    private void broadcastOp(String boardId, String fromConnectionId, WsMessage.Op opMsg) {
        var sessions = boardSessions.get(boardId);
        if (sessions == null) return;

        for (var entry : sessions.entrySet()) {
            if (entry.getKey().equals(fromConnectionId)) continue; // don't echo
            send(entry.getValue(), opMsg);
        }
    }

    private void broadcastPresence(String boardId) {
        var sessions = boardSessions.get(boardId);
        if (sessions == null) return;

        Map<String, PresenceHub.UserPresence> users = presenceHub.snapshot(boardId);
        WsMessage.Presence msg = new WsMessage.Presence(boardId, usersToJson(users));

        for (var s : sessions.values()) {
            send(s, msg);
        }
    }

    private JsonNode usersToJson(Map<String, PresenceHub.UserPresence> users) {
        ArrayNode arr = mapper.createArrayNode();
        for (var up : users.values()) {
            ObjectNode o = mapper.createObjectNode();
            o.put("userId", up.userId());
            o.put("joinedAt", up.joinedAt().toString());
            arr.add(o);
        }
        return arr;
    }

    private void send(Session session, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            session.getAsyncRemote().sendText(json);
        } catch (Exception ignored) {
        }
    }

    private void close(Session session, CloseReason.CloseCode code, String reason) {
        try {
            session.close(new CloseReason(code, reason));
        } catch (Exception ignored) {
        }
    }

    private String firstQueryParam(Session session, String key) {
        try {
            var map = session.getRequestParameterMap();
            if (map == null) return null;
            var values = map.get(key);
            if (values == null || values.isEmpty()) return null;
            return values.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}
