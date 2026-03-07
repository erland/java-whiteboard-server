package info.isaksson.erland.whiteboard.api.dto;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "MeResponse", description = "Identity information for the authenticated caller.")
public record MeResponse(
        @Schema(description = "Authenticated user identifier.", example = "alice")
        String userId,
        @Schema(description = "Granted whiteboard roles.", type = SchemaType.ARRAY, implementation = String.class, example = "[\"whiteboard-admin\",\"whiteboard-user\"]")
        List<String> roles
) {
}
