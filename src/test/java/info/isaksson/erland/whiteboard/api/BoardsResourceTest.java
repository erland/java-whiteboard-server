package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

@QuarkusTest
public class BoardsResourceTest {

    @Inject
    BoardsRepository boardsRepository;

    @Test
    void list_requires_auth() {
        given()
          .when().get("/api/boards")
          .then()
             .statusCode(401)
             .body("code", is("UNAUTHORIZED"));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void create_list_get_update_archive_happy_path() {
        String id =
            given()
              .contentType("application/json")
              .body("{\"name\":\"My board\",\"type\":\"advanced\"}")
              .when().post("/api/boards")
              .then()
                 .statusCode(201)
                 .body("name", is("My board"))
                 .body("type", is("advanced"))
                 .body("ownerUserId", is("alice"))
                 .body("status", is("active"))
                 .extract().path("id");

        given()
          .when().get("/api/boards")
          .then()
             .statusCode(200)
             .body("size()", is(1))
             .body("[0].id", is(id));

        given()
          .when().get("/api/boards/" + id)
          .then()
             .statusCode(200)
             .body("id", is(id));

        given()
          .contentType("application/json")
          .body("{\"name\":\"Renamed\"}")
          .when().patch("/api/boards/" + id)
          .then()
             .statusCode(200)
             .body("name", is("Renamed"))
             .body("type", is("advanced"));

        given()
          .when().delete("/api/boards/" + id)
          .then()
             .statusCode(204);

        // Archived board is still retrievable by owner (status != deleted)
        given()
          .when().get("/api/boards/" + id)
          .then()
             .statusCode(200)
             .body("status", is("archived"));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void create_validates_required_fields() {
        given()
          .contentType("application/json")
          .body("{\"name\":\"\",\"type\":\"x\"}")
          .when().post("/api/boards")
          .then()
             .statusCode(400)
             .body("code", is("VALIDATION_ERROR"));

        given()
          .contentType("application/json")
          .body("{\"name\":\"x\",\"type\":\"\"}")
          .when().post("/api/boards")
          .then()
             .statusCode(400)
             .body("code", is("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void other_user_gets_404_for_board_they_dont_own() {
        String id = UUID.randomUUID().toString();
        boardsRepository.create(new Board(
                id,
                "Secret",
                "advanced",
                "alice",
                "active",
                null,
                null
        ));

        given()
          .when().get("/api/boards/" + id)
          .then()
             .statusCode(404)
             .body("code", is("NOT_FOUND"));
    }
}
