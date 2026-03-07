package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreateBoardRequest", description = "Request body used to create a new board.")
public record CreateBoardRequest(
        @Schema(description = "Board name.", example = "Operations planning")
        String name,
        @Schema(description = "Board kind. The server normalizes supported values.", example = "whiteboard")
        String type,
        @Schema(description = "Optional board type/category.", example = "team-board")
        String boardType
) {
}
