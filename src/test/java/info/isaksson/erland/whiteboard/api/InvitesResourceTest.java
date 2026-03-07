package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.domain.Invite;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InvitesRepository;
import info.isaksson.erland.whiteboard.security.InviteTokens;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

@QuarkusTest
public class InvitesResourceTest {

    @Inject
    BoardsRepository boardsRepository;

    @Inject
    InvitesRepository invitesRepository;

    @Test
    void create_requires_auth() {
        given()
          .contentType("application/json")
          .body("{\"permission\":\"viewer\"}")
          .when().post("/api/boards/any/invites")
          .then()
             .statusCode(401)
             .body("code", is("UNAUTHORIZED"));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void create_list_revoke_validate() {
        String boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "B", "whiteboard", "advanced", "alice", "active", null, null));

        String token =
            given()
              .contentType("application/json")
              .body("{\"permission\":\"editor\"}")
              .when().post("/api/boards/" + boardId + "/invites")
              .then()
                 .statusCode(201)
                 .body("permission", is("editor"))
                 .body("boardId", is(boardId))
                 .body("token", not(isEmptyOrNullString()))
                 .extract().path("token");

        String inviteId =
            given()
              .contentType("application/json")
              .body("{\"permission\":\"viewer\"}")
              .when().post("/api/boards/" + boardId + "/invites")
              .then()
                 .statusCode(201)
                 .extract().path("id");

        given()
          .when().get("/api/boards/" + boardId + "/invites")
          .then()
             .statusCode(200)
             .body("size()", is(2))
             .body("[0].id", notNullValue())
             .body("[0].boardId", is(boardId))
             .body("[0].permission", notNullValue())
             // ensure token is NOT in list response
             .body("[0].token", nullValue());

        // Validate token publicly
        given()
          .contentType("application/json")
          .body("{\"token\":\"" + token + "\"}")
          .when().post("/api/invites/validate")
          .then()
             .statusCode(200)
             .body("valid", is(true))
             .body("reason", is("OK"))
             .body("boardId", is(boardId));

        // Revoke inviteId
        given()
          .when().delete("/api/boards/" + boardId + "/invites/" + inviteId)
          .then()
             .statusCode(204);

        // Validate revoked token: create a deterministic token and revoke it for this check
        String tok2 = "tok-" + UUID.randomUUID();
        Invite inv2 = invitesRepository.create(new Invite(
                UUID.randomUUID().toString(),
                boardId,
                InviteTokens.sha256Hex(tok2),
                "viewer",
                null,
                null,
                0,
                null,
                null
        ));
        invitesRepository.revoke(inv2.id());

        given()
          .contentType("application/json")
          .body("{\"token\":\"" + tok2 + "\"}")
          .when().post("/api/invites/validate")
          .then()
             .statusCode(200)
             .body("valid", is(false))
             .body("reason", is("REVOKED"));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void validate_expired_and_max_uses() {
        String boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "B", "whiteboard", "advanced", "alice", "active", null, null));

        String tokExpired = "tok-exp-" + UUID.randomUUID();
        invitesRepository.create(new Invite(
                UUID.randomUUID().toString(),
                boardId,
                InviteTokens.sha256Hex(tokExpired),
                "viewer",
                Instant.now().minusSeconds(60),
                null,
                0,
                null,
                null
        ));

        given()
          .contentType("application/json")
          .body("{\"token\":\"" + tokExpired + "\"}")
          .when().post("/api/invites/validate")
          .then()
             .statusCode(200)
             .body("valid", is(false))
             .body("reason", is("EXPIRED"));

        String tokMax = "tok-max-" + UUID.randomUUID();
        Invite invMax = invitesRepository.create(new Invite(
                UUID.randomUUID().toString(),
                boardId,
                InviteTokens.sha256Hex(tokMax),
                "viewer",
                null,
                1,
                1,
                null,
                null
        ));

        given()
          .contentType("application/json")
          .body("{\"token\":\"" + tokMax + "\"}")
          .when().post("/api/invites/validate")
          .then()
             .statusCode(200)
             .body("valid", is(false))
             .body("reason", is("MAX_USES_REACHED"));
    }

    @Test
    void validate_returns_not_found_for_empty_or_unknown() {
        given()
          .contentType("application/json")
          .body("{\"token\":\"\"}")
          .when().post("/api/invites/validate")
          .then()
             .statusCode(200)
             .body("valid", is(false))
             .body("reason", is("NOT_FOUND"));

        given()
          .contentType("application/json")
          .body("{\"token\":\"does-not-exist\"}")
          .when().post("/api/invites/validate")
          .then()
             .statusCode(200)
             .body("valid", is(false))
             .body("reason", is("NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void other_user_gets_404_when_listing_invites_for_foreign_board() {
        String boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "B", "whiteboard", "advanced", "alice", "active", null, null));

        given()
          .when().get("/api/boards/" + boardId + "/invites")
          .then()
             .statusCode(404)
             .body("code", is("NOT_FOUND"));
    }
}
