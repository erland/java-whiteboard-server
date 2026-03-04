package info.isaksson.erland.whiteboard.api;

import java.time.Instant;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
public class HelloResource {

    @GET
    @Path("/healthz")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> healthz() {
        return Map.of(
                "status", "ok",
                "time", Instant.now().toString()
        );
    }
}
