package info.isaksson.erland.whiteboard.ws;

import java.util.List;
import java.util.Map;

import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Captures selected handshake data (headers) into the user properties map so the endpoint can read it.
 */
public class WsHandshakeConfigurator extends ServerEndpointConfig.Configurator {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        Map<String, List<String>> headers = request.getHeaders();
        if (headers != null) {
            List<String> auth = headers.get(AUTHORIZATION_HEADER);
            if (auth != null && !auth.isEmpty()) {
                sec.getUserProperties().put(AUTHORIZATION_HEADER, auth.get(0));
            }
        }
        super.modifyHandshake(sec, request, response);
    }
}
