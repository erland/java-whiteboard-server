package info.isaksson.erland.whiteboard.ws;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.domain.Invite;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardPermissionsRepository;
import info.isaksson.erland.whiteboard.persistence.InvitesRepository;
import info.isaksson.erland.whiteboard.security.BoardAccessService;
import info.isaksson.erland.whiteboard.security.BoardGuards;
import info.isaksson.erland.whiteboard.security.InviteTokens;

public class BoardJoinAuthorizerTest {

    @Test
    void owner_can_join() {
        var boards = new SimpleBoardsRepo();
        var invites = new SimpleInvitesRepo();
        String boardId = UUID.randomUUID().toString();
        boards.create(new Board(boardId, "B", "whiteboard", "advanced", "alice", "active", null, null));

        BoardJoinAuthorizer a = new BoardJoinAuthorizer();
        a.boardGuards = createBoardGuards(boards);
        a.invitePolicy = new info.isaksson.erland.whiteboard.security.InvitePolicy(invites);

        var ok = a.authorize(boardId, "alice", null);
        assertTrue(ok.allowed());
        assertEquals("alice", ok.effectiveUserId());
        assertEquals("owner", ok.permission());

        var no = a.authorize(boardId, "bob", null);
        assertFalse(no.allowed());
    }

    @Test
    void valid_invite_allows_join_when_no_userid() {
        var boards = new SimpleBoardsRepo();
        var invites = new SimpleInvitesRepo();
        String boardId = UUID.randomUUID().toString();
        boards.create(new Board(boardId, "B", "whiteboard", "advanced", "alice", "active", null, null));

        String token = "tok-" + UUID.randomUUID();
        invites.create(new Invite(
                UUID.randomUUID().toString(),
                boardId,
                InviteTokens.sha256Hex(token),
                "viewer",
                null,
                2,
                0,
                null,
                null
        ));

        BoardJoinAuthorizer a = new BoardJoinAuthorizer();
        a.boardGuards = createBoardGuards(boards);
        a.invitePolicy = new info.isaksson.erland.whiteboard.security.InvitePolicy(invites);

        var ok = a.authorize(boardId, null, token);
        assertTrue(ok.allowed());
        assertTrue(ok.effectiveUserId().startsWith("invite:"));
        assertEquals("viewer", ok.permission());
    }

    @Test
    void expired_invite_denied() {
        var boards = new SimpleBoardsRepo();
        var invites = new SimpleInvitesRepo();
        String boardId = UUID.randomUUID().toString();
        boards.create(new Board(boardId, "B", "whiteboard", "advanced", "alice", "active", null, null));

        String token = "tok-" + UUID.randomUUID();
        invites.create(new Invite(
                UUID.randomUUID().toString(),
                boardId,
                InviteTokens.sha256Hex(token),
                "viewer",
                Instant.now().minusSeconds(10),
                null,
                0,
                null,
                null
        ));

        BoardJoinAuthorizer a = new BoardJoinAuthorizer();
        a.boardGuards = createBoardGuards(boards);
        a.invitePolicy = new info.isaksson.erland.whiteboard.security.InvitePolicy(invites);

        var no = a.authorize(boardId, null, token);
        assertFalse(no.allowed());
    }


    private static BoardGuards createBoardGuards(BoardsRepository boards) {
        return new BoardGuards(boards, new BoardAccessService(boards, new InMemoryBoardPermissionsRepository()));
    }
    // Minimal repos for unit tests (no CDI)
    static class SimpleBoardsRepo implements BoardsRepository {
        private final java.util.concurrent.ConcurrentHashMap<String, Board> store = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public Board create(Board board) { store.put(board.id(), board); return board; }
        @Override public java.util.List<Board> listForOwner(String ownerUserId) { throw new UnsupportedOperationException(); }
        @Override public Optional<Board> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public Board updateMetadata(String id, String ownerUserId, String name, String type, String boardType) { throw new UnsupportedOperationException(); }
        @Override public boolean archive(String id, String ownerUserId) { throw new UnsupportedOperationException(); }
    }

    static class SimpleInvitesRepo implements InvitesRepository {
        private final java.util.concurrent.ConcurrentHashMap<String, Invite> store = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public Invite create(Invite invite) { store.put(invite.id(), invite); return invite; }
        @Override public java.util.List<Invite> listForBoard(String boardId) { throw new UnsupportedOperationException(); }
        @Override public Optional<Invite> findById(String inviteId) { return Optional.ofNullable(store.get(inviteId)); }
        @Override public Optional<Invite> findByTokenHash(String tokenHash) {
            return store.values().stream().filter(i -> i.tokenHash().equals(tokenHash)).findFirst();
        }
        @Override public boolean revoke(String inviteId) { throw new UnsupportedOperationException(); }
        @Override public Optional<Invite> incrementUses(String inviteId) {
            Invite updated = store.computeIfPresent(inviteId, (k, v) -> new Invite(
                    v.id(), v.boardId(), v.tokenHash(), v.permission(), v.expiresAt(), v.maxUses(), v.uses() + 1, v.revokedAt(), v.createdAt()
            ));
            return Optional.ofNullable(updated);
        }
    }
}
