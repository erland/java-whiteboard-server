package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "UpdateBoardRequest", description = "Request body used to update editable board metadata.")
public record UpdateBoardRequest(
        @Schema(description = "Updated board name.", example = "Operations planning")
        String name,
        @Schema(description = "Updated board kind. The server normalizes supported values.", example = "whiteboard")
        String type,
        @Schema(description = "Updated board type/category.", example = "team-board")
        String boardType
) {
}
