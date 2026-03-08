package info.isaksson.erland.whiteboard.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import info.isaksson.erland.whiteboard.api.dto.CreateInviteRequest;
import info.isaksson.erland.whiteboard.api.dto.InviteCreatedResponse;
import info.isaksson.erland.whiteboard.api.dto.InviteResponse;
import info.isaksson.erland.whiteboard.domain.Invite;
import info.isaksson.erland.whiteboard.persistence.InvitesRepository;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import info.isaksson.erland.whiteboard.security.InviteTokens;

@ApplicationScoped
public class InviteApplicationService {

    private final InvitesRepository invitesRepository;
    private final BoardGuards boardGuards;
    private final InviteRequestSupport inviteRequestSupport;

    public InviteApplicationService(InvitesRepository invitesRepository,
                                    BoardGuards boardGuards,
                                    InviteRequestSupport inviteRequestSupport) {
        this.invitesRepository = invitesRepository;
        this.boardGuards = boardGuards;
        this.inviteRequestSupport = inviteRequestSupport;
    }

    public InviteCreatedResponse createInvite(String boardId, String userId, CreateInviteRequest req) {
        boardGuards.requireOwner(boardId, userId);

        String permission = inviteRequestSupport.requirePermission(req);
        Instant expiresAt = inviteRequestSupport.parseExpiresAt(req);
        Integer maxUses = inviteRequestSupport.parseMaxUses(req);

        String token = InviteTokens.generateToken();
        String tokenHash = InviteTokens.sha256Hex(token);

        Invite created = invitesRepository.create(new Invite(
                UUID.randomUUID().toString(),
                boardId,
                tokenHash,
                permission,
                expiresAt,
                maxUses,
                0,
                null,
                null
        ));
        return InviteCreatedResponse.from(created, token);
    }

    public List<InviteResponse> listInvites(String boardId, String userId) {
        boardGuards.requireOwner(boardId, userId);
        return invitesRepository.listForBoard(boardId).stream()
                .map(InviteResponse::from)
                .toList();
    }

    public void revokeInvite(String boardId, String inviteId, String userId) {
        boardGuards.requireOwner(boardId, userId);
        Invite invite = invitesRepository.findById(inviteId).orElseThrow(NotFoundException::new);
        if (!invite.boardId().equals(boardId)) {
            throw new NotFoundException();
        }
        invitesRepository.revoke(inviteId);
    }
}
