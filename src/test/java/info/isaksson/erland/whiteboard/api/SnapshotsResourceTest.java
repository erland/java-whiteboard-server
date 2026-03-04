package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

@QuarkusTest
public class SnapshotsResourceTest {

    @Inject
    BoardsRepository boardsRepository;

    @Test
    void create_requires_auth() {
        given()
          .contentType("application/json")
          .body("{\"snapshot\":{\"k\":1}}")
          .when().post("/api/boards/any/snapshots")
          .then()
             .statusCode(401)
             .body("code", is("UNAUTHORIZED"));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void create_latest_get_versions_flow() {
        String boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "B", "advanced", "alice", "active", null, null));

        given()
          .contentType("application/json")
          .body("{\"snapshot\":{\"k\":1}}")
          .when().post("/api/boards/" + boardId + "/snapshots")
          .then()
             .statusCode(201)
             .body("version", is(1))
             .body("snapshot.k", is(1));

        given()
          .contentType("application/json")
          .body("{\"snapshot\":{\"k\":2,\"nested\":{\"x\":true}}}")
          .when().post("/api/boards/" + boardId + "/snapshots")
          .then()
             .statusCode(201)
             .body("version", is(2))
             .body("snapshot.k", is(2))
             .body("snapshot.nested.x", is(true));

        given()
          .when().get("/api/boards/" + boardId + "/snapshots/latest")
          .then()
             .statusCode(200)
             .body("version", is(2))
             .body("snapshot.k", is(2));

        given()
          .when().get("/api/boards/" + boardId + "/snapshots/1")
          .then()
             .statusCode(200)
             .body("version", is(1))
             .body("snapshot.k", is(1));

        given()
          .when().get("/api/boards/" + boardId + "/snapshots")
          .then()
             .statusCode(200)
             .body("versions", contains(2,1));
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void other_user_gets_404_no_leak() {
        String boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "B", "advanced", "alice", "active", null, null));

        given()
          .when().get("/api/boards/" + boardId + "/snapshots")
          .then()
             .statusCode(404)
             .body("code", is("NOT_FOUND"));
    }
}
