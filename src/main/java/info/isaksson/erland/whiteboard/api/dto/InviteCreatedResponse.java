package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

import info.isaksson.erland.whiteboard.domain.Invite;

public record InviteCreatedResponse(
        String id,
        String boardId,
        String permission,
        Instant expiresAt,
        Integer maxUses,
        int uses,
        Instant revokedAt,
        Instant createdAt,
        String token
) {
    public static InviteCreatedResponse from(Invite i, String token) {
        return new InviteCreatedResponse(i.id(), i.boardId(), i.permission(), i.expiresAt(), i.maxUses(), i.uses(), i.revokedAt(), i.createdAt(), token);
    }
}
