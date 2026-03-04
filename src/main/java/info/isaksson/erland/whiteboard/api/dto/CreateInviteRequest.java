package info.isaksson.erland.whiteboard.api.dto;

public record CreateInviteRequest(
        String permission,  // viewer | editor
        String expiresAt,   // ISO-8601 instant, optional
        Integer maxUses     // optional
) {
}
