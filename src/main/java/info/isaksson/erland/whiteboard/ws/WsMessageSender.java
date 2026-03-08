package info.isaksson.erland.whiteboard.ws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;

import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class WsMessageSender {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(WsMessageSender.class);

    private final ObjectMapper mapper;

    @Inject
    public WsMessageSender(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void send(Session session, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            session.getAsyncRemote().sendText(json, new SendHandler() {
                @Override
                public void onResult(SendResult result) {
                    if (result == null) {
                        LOG.debug("WS send completed with null result");
                        return;
                    }
                    if (!result.isOK()) {
                        Throwable ex = result.getException();
                        if (ex == null) {
                            LOG.debug("WS send failed (no exception)");
                        } else {
                            LOG.debugf(ex, "WS send failed: %s", ex.getClass().getSimpleName());
                        }
                    }
                }
            });
        } catch (Exception e) {
            LOG.debugf(e, "WS send failed to serialize or dispatch");
        }
    }

    public void close(Session session, CloseReason.CloseCode code, String reason) {
        try {
            LOG.debugf("WS closing code=%s reason=%s", code, reason);
            session.close(new CloseReason(code, reason));
        } catch (Exception ignored) {
        }
    }
}
