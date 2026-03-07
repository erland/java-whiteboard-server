package info.isaksson.erland.whiteboard.ws;

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

import org.slf4j.MDC;

@ServerEndpoint(value = "/ws/boards/{boardId}", configurator = WsHandshakeConfigurator.class)
@ApplicationScoped
public class BoardWebSocketEndpoint {

    @Inject
    WsLifecycleService lifecycleService;

    @Inject
    WsInboundMessageHandler inboundMessageHandler;

    @OnOpen
    public void onOpen(Session session, @PathParam("boardId") String boardId) {
        session.getUserProperties().putIfAbsent(WsSessionProps.CONNECTION_ID, UUID.randomUUID().toString());
        session.getUserProperties().putIfAbsent(WsSessionProps.WS_SESSION_ID, UUID.randomUUID().toString());
        session.getUserProperties().put(WsSessionProps.BOARD_ID, boardId);
        withWsMdc(session, () -> lifecycleService.open(session, boardId));
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        withWsMdc(session, () -> lifecycleService.close(session, reason));
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        withWsMdc(session, () -> lifecycleService.error(session, throwable));
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        withWsMdc(session, () -> inboundMessageHandler.handle(message, session));
    }

    private void withWsMdc(Session session, Runnable fn) {
        String connectionId = session == null ? null : (String) session.getUserProperties().get(WsSessionProps.CONNECTION_ID);
        String wsSessionId = session == null ? null : (String) session.getUserProperties().get(WsSessionProps.WS_SESSION_ID);
        String correlationId = session == null ? null : (String) session.getUserProperties().get(WsHandshakeConfigurator.PROP_CORRELATION_ID);
        try {
            if (correlationId != null) {
                MDC.put("correlationId", correlationId);
            }
            if (wsSessionId != null) {
                MDC.put("wsSessionId", wsSessionId);
            }
            if (connectionId != null) {
                MDC.put("wsConnectionId", connectionId);
            }
            fn.run();
        } finally {
            MDC.remove("correlationId");
            MDC.remove("wsSessionId");
            MDC.remove("wsConnectionId");
        }
    }
}
