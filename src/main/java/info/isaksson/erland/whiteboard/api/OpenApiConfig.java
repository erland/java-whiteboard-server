package info.isaksson.erland.whiteboard.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@OpenAPIDefinition(
        info = @Info(
                title = "Whiteboard Server REST API",
                version = "1.0.0",
                description = "REST API for board management, snapshots, invites, publications, and identity lookups for the whiteboard server.",
                contact = @Contact(name = "java-whiteboard-server")
        ),
        tags = {
                @Tag(name = "Boards", description = "Board lifecycle and metadata endpoints."),
                @Tag(name = "Snapshots", description = "Board snapshot create, read, and version listing endpoints."),
                @Tag(name = "Invites", description = "Board invite creation, validation, acceptance, and revocation endpoints."),
                @Tag(name = "Publications", description = "Board publication management and publication resolution endpoints."),
                @Tag(name = "Comments", description = "Durable board comment endpoints."),
                @Tag(name = "Identity", description = "Authenticated user identity endpoints.")
        }
)
@SecurityScheme(
        securitySchemeName = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Bearer access token used for authenticated REST endpoints."
)
@ApplicationPath("/")
public class OpenApiConfig extends Application {
}
