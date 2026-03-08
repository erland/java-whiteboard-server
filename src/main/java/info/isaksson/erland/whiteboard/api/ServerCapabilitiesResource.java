package info.isaksson.erland.whiteboard.api;

import java.util.List;

import info.isaksson.erland.whiteboard.config.FeatureToggles;
import info.isaksson.erland.whiteboard.config.ProtocolCompatibility;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "Identity")
@Path("/api/capabilities")
@Produces(MediaType.APPLICATION_JSON)
public class ServerCapabilitiesResource {

    @Inject ProtocolCompatibility protocolCompatibility;
    @Inject FeatureToggles featureToggles;

    @GET
    @Operation(summary = "Get server capabilities", description = "Returns the current API version, WebSocket protocol version, and enabled feature capabilities so clients can adapt contracts safely.")
    public CapabilitiesResponse get() {
        return new CapabilitiesResponse(Integer.toString(protocolCompatibility.apiVersion()), Integer.toString(protocolCompatibility.wsProtocolVersion()), featureToggles.enabledCapabilities());
    }

    public record CapabilitiesResponse(String apiVersion, String wsProtocolVersion, List<String> capabilities) {}
}
