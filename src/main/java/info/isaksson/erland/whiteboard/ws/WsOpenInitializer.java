package info.isaksson.erland.whiteboard.ws;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

@ApplicationScoped
class WsOpenInitializer {

    private final WsAuthResolver authResolver;

    @Inject
    WsOpenInitializer(WsAuthResolver authResolver) {
        this.authResolver = authResolver;
    }

    WsConnectionContext initialize(Session session, String boardId) {
        String connectionId = prop(session, WsSessionProps.CONNECTION_ID);
        if (connectionId == null || connectionId.isBlank()) {
            connectionId = UUID.randomUUID().toString();
        }
        String wsSessionId = prop(session, WsSessionProps.WS_SESSION_ID);
        if (wsSessionId == null || wsSessionId.isBlank()) {
            wsSessionId = UUID.randomUUID().toString();
        }
        String correlationId = prop(session, WsHandshakeConfigurator.PROP_CORRELATION_ID);
        String inviteToken = authResolver.resolveInviteToken(session);

        session.getUserProperties().put(WsSessionProps.CONNECTION_ID, connectionId);
        session.getUserProperties().put(WsSessionProps.WS_SESSION_ID, wsSessionId);
        session.getUserProperties().put(WsSessionProps.BOARD_ID, boardId);

        return new WsConnectionContext(boardId, connectionId, wsSessionId, correlationId, null, null, inviteToken);
    }

    @SuppressWarnings("unchecked")
    private <T> T prop(Session session, String key) {
        return session == null ? null : (T) session.getUserProperties().get(key);
    }
}
