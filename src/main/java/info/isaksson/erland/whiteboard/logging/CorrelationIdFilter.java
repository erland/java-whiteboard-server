package info.isaksson.erland.whiteboard.logging;

import java.io.IOException;
import java.util.UUID;

import org.jboss.logging.MDC;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Adds/propagates a correlation id for HTTP requests.
 *
 * - Reads incoming header X-Correlation-Id if present
 * - Otherwise generates a UUID
 * - Adds it to MDC as "correlationId" and echoes it back in the response
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String cid = requestContext.getHeaderString(HEADER);
        if (cid == null || cid.isBlank()) {
            cid = UUID.randomUUID().toString();
        }
        requestContext.setProperty(MDC_KEY, cid);
        MDC.put(MDC_KEY, cid);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Object cid = requestContext.getProperty(MDC_KEY);
        if (cid != null) {
            responseContext.getHeaders().putSingle(HEADER, cid.toString());
        }
        MDC.remove(MDC_KEY);
    }
}
