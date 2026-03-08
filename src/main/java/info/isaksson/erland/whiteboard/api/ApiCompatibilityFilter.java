package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.errors.ApiError;
import info.isaksson.erland.whiteboard.config.FeatureToggles;
import info.isaksson.erland.whiteboard.config.ProtocolCompatibility;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class ApiCompatibilityFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String API_VERSION_HEADER = "X-Whiteboard-Api-Version";
    public static final String WS_PROTOCOL_VERSION_HEADER = "X-Whiteboard-Ws-Protocol-Version";
    public static final String CAPABILITIES_HEADER = "X-Whiteboard-Capabilities";

    @Inject ProtocolCompatibility protocolCompatibility;
    @Inject FeatureToggles featureToggles;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String requestedVersion = requestContext.getHeaderString(API_VERSION_HEADER);
        if (protocolCompatibility.isSupportedApiVersion(requestedVersion)) {
            return;
        }
        requestContext.abortWith(Response.status(Response.Status.PRECONDITION_FAILED)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError("INCOMPATIBLE_API_VERSION", "Unsupported API version '" + requestedVersion + "'. Supported version is '" + protocolCompatibility.apiVersion() + "'."))
                .header(API_VERSION_HEADER, Integer.toString(protocolCompatibility.apiVersion()))
                .header(WS_PROTOCOL_VERSION_HEADER, Integer.toString(protocolCompatibility.wsProtocolVersion()))
                .header(CAPABILITIES_HEADER, String.join(",", featureToggles.enabledCapabilities()))
                .build());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        responseContext.getHeaders().putSingle(API_VERSION_HEADER, Integer.toString(protocolCompatibility.apiVersion()));
        responseContext.getHeaders().putSingle(WS_PROTOCOL_VERSION_HEADER, Integer.toString(protocolCompatibility.wsProtocolVersion()));
        responseContext.getHeaders().putSingle(CAPABILITIES_HEADER, String.join(",", featureToggles.enabledCapabilities()));
    }
}
