package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.domain.Invite;
import info.isaksson.erland.whiteboard.persistence.BoardPermissionsRepository;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InvitesRepository;
import info.isaksson.erland.whiteboard.security.InviteTokens;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class InviteAcceptResourceTest {

    @Inject
    BoardsRepository boardsRepository;

    @Inject
    InvitesRepository invitesRepository;

    @Inject
    BoardPermissionsRepository permissionsRepository;

    String boardId;
    String token;

    @BeforeEach
    void setup() {
        // create a board owned by alice
        boardId = UUID.randomUUID().toString();
        // Board record fields: (id, name, type, ownerUserId, status, createdAt, updatedAt)
        Board board = new Board(boardId, "Test board", "whiteboard", "alice", "active", Instant.now(), Instant.now());
        boardsRepository.create(board);

        token = InviteTokens.newToken();
        String tokenHash = InviteTokens.sha256Hex(token);
        Invite invite = new Invite(
                UUID.randomUUID().toString(),
                boardId,
                tokenHash,
                "view",
                null,
                10,
                0,
                null,
                Instant.now()
        );
        invitesRepository.create(invite);
    }

    @Test
    @TestSecurity(user = "bob", roles = {"user"})
    void accept_invite_grants_read_access_to_board() {
        given()
                .contentType("application/json")
                .body("{\"token\":\"" + token + "\"}")
                .when()
                .post("/api/invites/accept")
                .then()
                .statusCode(200)
                .body("boardId", equalTo(boardId));

        // bob can now fetch the board
        given()
                .when()
                .get("/api/boards/" + boardId)
                .then()
                .statusCode(200)
                .body("id", equalTo(boardId))
                .body("ownerUserId", equalTo("alice"));

        // role stored in repository
        org.junit.jupiter.api.Assertions.assertTrue(permissionsRepository.findRole(boardId, "bob").isPresent());
    }
}
