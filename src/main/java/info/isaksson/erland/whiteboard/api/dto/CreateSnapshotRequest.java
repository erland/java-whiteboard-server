package info.isaksson.erland.whiteboard.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Snapshot payload.
 * The server stores the snapshot as JSON (opaque to the backend in this phase).
 */
@Schema(name = "CreateSnapshotRequest", description = "Request body used to persist a new board snapshot.")
public record CreateSnapshotRequest(
        @Schema(description = "Opaque whiteboard snapshot JSON payload stored by the server.", type = SchemaType.OBJECT, example = "{\"elements\":[{\"id\":\"n1\",\"type\":\"sticky\"}],\"viewport\":{\"x\":0,\"y\":0,\"zoom\":1}}")
        JsonNode snapshot
) {
}
