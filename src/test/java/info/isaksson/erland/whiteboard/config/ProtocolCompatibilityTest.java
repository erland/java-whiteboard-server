package info.isaksson.erland.whiteboard.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProtocolCompatibilityTest {

    @Test
    void isSupportedApiVersion_accepts_blank_or_exact_match() {
        ProtocolCompatibility compatibility = new ProtocolCompatibility();
        compatibility.apiVersion = 1;
        assertTrue(compatibility.isSupportedApiVersion(null));
        assertTrue(compatibility.isSupportedApiVersion("1"));
        assertFalse(compatibility.isSupportedApiVersion("2"));
    }

    @Test
    void evaluateWsVersion_rejects_non_numeric_version() {
        ProtocolCompatibility compatibility = new ProtocolCompatibility();
        compatibility.wsProtocolVersion = 1;
        ProtocolCompatibility.WsVersionDecision decision = compatibility.evaluateWsVersion("abc");
        assertFalse(decision.allowed());
        assertEquals("INCOMPATIBLE_PROTOCOL", decision.code());
    }

    @Test
    void evaluateWsVersion_allowsMissingVersionByDefault() {
        ProtocolCompatibility compatibility = new ProtocolCompatibility();
        compatibility.wsProtocolVersion = 1;
        compatibility.requireClientWsVersion = false;
        ProtocolCompatibility.WsVersionDecision decision = compatibility.evaluateWsVersion(null);
        assertTrue(decision.allowed());
    }

    @Test
    void evaluateWsVersion_rejectsUnsupportedVersion() {
        ProtocolCompatibility compatibility = new ProtocolCompatibility();
        compatibility.wsProtocolVersion = 1;
        ProtocolCompatibility.WsVersionDecision decision = compatibility.evaluateWsVersion("2");
        assertFalse(decision.allowed());
        assertEquals("INCOMPATIBLE_PROTOCOL", decision.code());
    }

    @Test
    void evaluateWsVersion_requiresVersionWhenConfigured() {
        ProtocolCompatibility compatibility = new ProtocolCompatibility();
        compatibility.wsProtocolVersion = 1;
        compatibility.requireClientWsVersion = true;
        ProtocolCompatibility.WsVersionDecision decision = compatibility.evaluateWsVersion(" ");
        assertFalse(decision.allowed());
        assertEquals("WS_PROTOCOL_VERSION_REQUIRED", decision.code());
    }
}
