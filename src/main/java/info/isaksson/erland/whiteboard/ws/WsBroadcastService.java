package info.isaksson.erland.whiteboard.ws;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import info.isaksson.erland.whiteboard.ws.ephemeral.EphemeralAccessPolicy;

@ApplicationScoped
public class WsBroadcastService {

    private final WsSessionRegistry sessionRegistry;
    private final WsMetrics metrics;
    private final EphemeralAccessPolicy ephemeralAccessPolicy;
    private final WsMessageSender messageSender;

    @Inject
    public WsBroadcastService(WsSessionRegistry sessionRegistry,
                              WsMetrics metrics,
                              EphemeralAccessPolicy ephemeralAccessPolicy,
                              WsMessageSender messageSender) {
        this.sessionRegistry = sessionRegistry;
        this.metrics = metrics;
        this.ephemeralAccessPolicy = ephemeralAccessPolicy;
        this.messageSender = messageSender;
    }

    public void broadcastPresence(String boardId, WsMessage.Presence message) {
        Map<String, Session> sessions = sessionRegistry.sessions(boardId);
        if (sessions.isEmpty()) {
            return;
        }
        for (Session session : sessions.values()) {
            messageSender.send(session, message);
            metrics.presenceBroadcast();
        }
    }

    public void broadcastOp(String boardId, WsMessage.Op message) {
        Map<String, Session> sessions = sessionRegistry.sessions(boardId);
        if (sessions.isEmpty()) {
            return;
        }
        for (Session session : sessions.values()) {
            messageSender.send(session, message);
            metrics.opBroadcast();
        }
    }

    public void broadcastEphemeral(String boardId, WsMessage.Ephemeral message) {
        Map<String, Session> sessions = sessionRegistry.sessions(boardId);
        if (sessions.isEmpty()) {
            return;
        }
        for (Session session : sessions.values()) {
            String permission = (String) session.getUserProperties().get(WsSessionProps.PERMISSION);
            if (!ephemeralAccessPolicy.canObserve(permission, message.eventType())) {
                continue;
            }
            messageSender.send(session, message);
            metrics.ephemeralBroadcast();
        }
    }
}
