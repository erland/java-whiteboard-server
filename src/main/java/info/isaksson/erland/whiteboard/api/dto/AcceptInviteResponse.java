package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "AcceptInviteResponse", description = "Result returned after a valid invite has been accepted.")
public record AcceptInviteResponse(
        @Schema(description = "Board identifier that the invite granted access to.", example = "board-123")
        String boardId,
        @Schema(description = "Granted board role.", example = "editor")
        String role
) {
}
