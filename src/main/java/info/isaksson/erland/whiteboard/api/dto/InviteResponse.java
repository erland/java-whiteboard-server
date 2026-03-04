package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

import info.isaksson.erland.whiteboard.domain.Invite;

public record InviteResponse(
        String id,
        String boardId,
        String permission,
        Instant expiresAt,
        Integer maxUses,
        int uses,
        Instant revokedAt,
        Instant createdAt
) {
    public static InviteResponse from(Invite i) {
        return new InviteResponse(i.id(), i.boardId(), i.permission(), i.expiresAt(), i.maxUses(), i.uses(), i.revokedAt(), i.createdAt());
    }
}
