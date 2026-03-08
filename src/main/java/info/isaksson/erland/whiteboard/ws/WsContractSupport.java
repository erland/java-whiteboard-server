package info.isaksson.erland.whiteboard.ws;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import info.isaksson.erland.whiteboard.config.FeatureToggles;
import info.isaksson.erland.whiteboard.config.ProtocolCompatibility;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

@ApplicationScoped
class WsContractSupport {

    static final String PROTOCOL_VERSION_HEADER = "X-Whiteboard-Protocol-Version";
    static final String PROTOCOL_VERSION_PARAM = "protocolVersion";

    private final ProtocolCompatibility protocolCompatibility;
    private final FeatureToggles featureToggles;

    @Inject
    WsContractSupport(ProtocolCompatibility protocolCompatibility, FeatureToggles featureToggles) {
        this.protocolCompatibility = protocolCompatibility;
        this.featureToggles = featureToggles;
    }

    ProtocolCompatibility.WsVersionDecision check(Session session) {
        return protocolCompatibility.evaluateWsVersion(resolveRequestedProtocolVersion(session));
    }

    int protocolVersion() { return protocolCompatibility.wsProtocolVersion(); }
    List<String> capabilities() { return featureToggles.enabledCapabilities(); }
    boolean ephemeralEnabled() { return featureToggles.wsEphemeralEnabled(); }

    String resolveRequestedProtocolVersion(Session session) {
        Object raw = session == null ? null : session.getUserProperties().get(PROTOCOL_VERSION_HEADER);
        if (raw instanceof String value && !value.isBlank()) return value;
        try {
            Map<String, List<String>> params = session == null ? null : session.getRequestParameterMap();
            if (params != null && params.get(PROTOCOL_VERSION_PARAM) != null) {
                for (String value : params.get(PROTOCOL_VERSION_PARAM)) {
                    if (value != null && !value.isBlank()) return value;
                }
            }
            if (session == null || session.getRequestURI() == null || session.getRequestURI().getRawQuery() == null) return null;
            for (String part : session.getRequestURI().getRawQuery().split("&")) {
                int idx = part.indexOf('=');
                String k = idx >= 0 ? part.substring(0, idx) : part;
                String v = idx >= 0 ? part.substring(idx + 1) : "";
                if (PROTOCOL_VERSION_PARAM.equals(URLDecoder.decode(k, StandardCharsets.UTF_8))) {
                    String decoded = URLDecoder.decode(v, StandardCharsets.UTF_8);
                    if (!decoded.isBlank()) return decoded;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
