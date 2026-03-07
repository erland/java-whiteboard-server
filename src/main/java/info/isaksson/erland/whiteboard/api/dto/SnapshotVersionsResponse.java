package info.isaksson.erland.whiteboard.api.dto;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "SnapshotVersionsResponse", description = "Available snapshot version numbers for a board.")
public record SnapshotVersionsResponse(
        @Schema(description = "Available snapshot version numbers in ascending order.", type = SchemaType.ARRAY, implementation = Long.class, example = "[1,2,3]")
        List<Long> versions
) {
}
