package info.isaksson.erland.whiteboard.ws;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Captures selected handshake data (headers) into the user properties map so the endpoint can read it.
 */
public class WsHandshakeConfigurator extends ServerEndpointConfig.Configurator {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String PROP_CORRELATION_ID = "correlationId";

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        Map<String, List<String>> headers = request.getHeaders();
        if (headers != null) {
            List<String> auth = headers.get(AUTHORIZATION_HEADER);
            if (auth != null && !auth.isEmpty()) {
                sec.getUserProperties().put(AUTHORIZATION_HEADER, auth.get(0));
            }

            String correlationId = firstHeaderIgnoreCase(headers, CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            sec.getUserProperties().put(PROP_CORRELATION_ID, correlationId);
        }
        super.modifyHandshake(sec, request, response);
    }

    private static String firstHeaderIgnoreCase(Map<String, List<String>> headers, String headerName) {
        for (var e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(headerName)) {
                var v = e.getValue();
                if (v != null && !v.isEmpty()) {
                    return v.get(0);
                }
            }
        }
        return null;
    }
}
