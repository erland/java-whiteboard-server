package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import info.isaksson.erland.whiteboard.domain.BoardSnapshot;

@Schema(name = "SnapshotResponse", description = "Stored snapshot version returned by the REST API.")
public record SnapshotResponse(
        @Schema(description = "Board identifier.", example = "board-123")
        String boardId,
        @Schema(description = "Monotonically increasing snapshot version number.", example = "3")
        long version,
        @Schema(description = "Opaque whiteboard snapshot JSON payload.", type = SchemaType.OBJECT, example = "{\"elements\":[{\"id\":\"n1\",\"type\":\"sticky\"}],\"viewport\":{\"x\":0,\"y\":0,\"zoom\":1}}")
        JsonNode snapshot,
        @Schema(description = "Snapshot creation timestamp.", example = "2026-03-01T10:15:30Z")
        Instant createdAt,
        @Schema(description = "User id of the caller that created the snapshot.", example = "alice")
        String createdBy
) {
    public static SnapshotResponse from(BoardSnapshot s, ObjectMapper mapper) {
        try {
            JsonNode node = mapper.readTree(s.snapshotJson());
            return new SnapshotResponse(s.boardId(), s.version(), node, s.createdAt(), s.createdBy());
        } catch (Exception e) {
            throw new IllegalStateException("Stored snapshot is not valid JSON", e);
        }
    }
}
