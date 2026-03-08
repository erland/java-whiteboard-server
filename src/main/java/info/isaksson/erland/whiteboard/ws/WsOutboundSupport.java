package info.isaksson.erland.whiteboard.ws;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import info.isaksson.erland.whiteboard.domain.BoardSnapshot;
import info.isaksson.erland.whiteboard.persistence.SnapshotsRepository;
import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralAccessPolicy;

@ApplicationScoped
public class WsOutboundSupport {

    private final ObjectMapper mapper;
    private final SnapshotsRepository snapshotsRepository;
    private final PresenceHub presenceHub;
    private final WsMessageSender messageSender;
    private final WsBroadcastService broadcastService;

    WsOutboundSupport(ObjectMapper mapper,
                      SnapshotsRepository snapshotsRepository,
                      PresenceHub presenceHub,
                      WsSessionRegistry sessionRegistry,
                      WsMetrics metrics) {
        this(mapper,
                snapshotsRepository,
                presenceHub,
                new WsMessageSender(mapper),
                new WsBroadcastService(sessionRegistry, metrics, new EphemeralAccessPolicy(), new WsMessageSender(mapper)));
    }

    @Inject
    WsOutboundSupport(ObjectMapper mapper,
                      SnapshotsRepository snapshotsRepository,
                      PresenceHub presenceHub,
                      WsMessageSender messageSender,
                      WsBroadcastService broadcastService) {
        this.mapper = mapper;
        this.snapshotsRepository = snapshotsRepository;
        this.presenceHub = presenceHub;
        this.messageSender = messageSender;
        this.broadcastService = broadcastService;
    }

    void sendJoined(Session session,
                    String boardId,
                    String effectiveUserId,
                    String wsSessionId,
                    String correlationId,
                    int protocolVersion,
                    java.util.List<String> capabilities) {
        Long latestVersion = null;
        JsonNode latestSnapshot = null;
        try {
            BoardSnapshot latest = snapshotsRepository.getLatest(boardId).orElse(null);
            if (latest != null) {
                latestVersion = latest.version();
                latestSnapshot = mapper.readTree(latest.snapshotJson());
            }
        } catch (Exception ignored) {
        }

        Map<String, PresenceHub.UserPresence> users = presenceHub.snapshot(boardId);
        send(session, new WsMessage.Joined(boardId, effectiveUserId, latestVersion, latestSnapshot, usersToJson(users), wsSessionId, correlationId, protocolVersion, capabilities));
    }

    void broadcastPresence(String boardId) {
        broadcastService.broadcastPresence(boardId, new WsMessage.Presence(boardId, usersToJson(presenceHub.snapshot(boardId))));
    }

    void broadcastOp(String boardId, WsMessage.Op opMsg) {
        broadcastService.broadcastOp(boardId, opMsg);
    }

    public void broadcastEphemeral(String boardId, WsMessage.Ephemeral message) {
        broadcastService.broadcastEphemeral(boardId, message);
    }

    public void send(Session session, Object payload) {
        messageSender.send(session, payload);
    }

    void close(Session session, CloseReason.CloseCode code, String reason) {
        messageSender.close(session, code, reason);
    }

    private JsonNode usersToJson(Map<String, PresenceHub.UserPresence> users) {
        ArrayNode arr = mapper.createArrayNode();
        for (PresenceHub.UserPresence up : users.values()) {
            ObjectNode o = mapper.createObjectNode();
            o.put("userId", up.userId());
            o.put("joinedAt", up.joinedAt().toString());
            arr.add(o);
        }
        return arr;
    }
}
