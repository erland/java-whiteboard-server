package info.isaksson.erland.whiteboard.config;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ProtocolCompatibility {

    @ConfigProperty(name = "whiteboard.contracts.api.version", defaultValue = "1")
    public int apiVersion;

    @ConfigProperty(name = "whiteboard.contracts.ws.protocol.version", defaultValue = "1")
    public int wsProtocolVersion;

    @ConfigProperty(name = "whiteboard.contracts.ws.protocol.require-client-version", defaultValue = "false")
    public boolean requireClientWsVersion;

    public int apiVersion() { return apiVersion; }
    public int wsProtocolVersion() { return wsProtocolVersion; }
    public boolean requireClientWsVersion() { return requireClientWsVersion; }

    public boolean isSupportedApiVersion(String raw) {
        return raw == null || raw.isBlank() || Integer.toString(apiVersion).equals(raw.trim());
    }

    public WsVersionDecision evaluateWsVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            if (requireClientWsVersion) {
                return new WsVersionDecision(false, "WS_PROTOCOL_VERSION_REQUIRED", "Client must provide protocolVersion=" + wsProtocolVersion + ".");
            }
            return new WsVersionDecision(true, null, null);
        }
        try {
            int requested = Integer.parseInt(raw.trim());
            if (requested == wsProtocolVersion) {
                return new WsVersionDecision(true, null, null);
            }
            return new WsVersionDecision(false, "INCOMPATIBLE_PROTOCOL", "Unsupported protocolVersion '" + requested + "'. Supported version is '" + wsProtocolVersion + "'.");
        } catch (NumberFormatException e) {
            return new WsVersionDecision(false, "INCOMPATIBLE_PROTOCOL", "Field 'protocolVersion' must be an integer. Supported version is '" + wsProtocolVersion + "'.");
        }
    }

    public record WsVersionDecision(boolean allowed, String code, String message) {}
}
