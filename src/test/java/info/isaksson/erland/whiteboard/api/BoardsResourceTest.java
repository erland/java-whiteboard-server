package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

@QuarkusTest
public class BoardsResourceTest {

    @Inject
    BoardsRepository boardsRepository;

    @Inject
    InMemoryBoardsRepository inMemoryBoardsRepository;

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    void clearBoards() throws Exception {
        if (inMemoryBoardsRepository != null) {
            inMemoryBoardsRepository.clear();
            return;
        }

        try (var c = dataSource.getConnection();
             var ps = c.prepareStatement("DELETE FROM boards")) {
            ps.executeUpdate();
        }
    }

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
              .body("{\"name\":\"My board\",\"type\":\"whiteboard\",\"boardType\":\"advanced\"}")
              .when().post("/api/boards")
              .then()
                 .statusCode(201)
                 .body("name", is("My board"))
                 .body("type", is("whiteboard"))
                 .body("boardType", is("advanced"))
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
             .body("type", is("whiteboard"))
             .body("boardType", is("advanced"));

        given()
          .when().delete("/api/boards/" + id)
          .then()
             .statusCode(204);

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
          .body("{\"name\":\"\",\"type\":\"whiteboard\",\"boardType\":\"advanced\"}")
          .when().post("/api/boards")
          .then()
             .statusCode(400)
             .body("code", is("VALIDATION_ERROR"));

        given()
          .contentType("application/json")
          .body("{\"name\":\"x\",\"type\":\"\",\"boardType\":\"advanced\"}")
          .when().post("/api/boards")
          .then()
             .statusCode(400)
             .body("code", is("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void create_accepts_legacy_type_only_payload_and_maps_it_to_boardType() {
        given()
          .contentType("application/json")
          .body("{\"name\":\"Legacy board\",\"type\":\"freehand\"}")
          .when().post("/api/boards")
          .then()
             .statusCode(201)
             .body("type", is("whiteboard"))
             .body("boardType", is("freehand"));
    }


    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void create_accepts_arbitrary_boardType_strings_without_server_whitelist_changes() {
        given()
          .contentType("application/json")
          .body("{\"name\":\"Kanban board\",\"type\":\"whiteboard\",\"boardType\":\"kanban-v2\"}")
          .when().post("/api/boards")
          .then()
             .statusCode(201)
             .body("type", is("whiteboard"))
             .body("boardType", is("kanban-v2"));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void create_accepts_arbitrary_legacy_type_only_payload_and_maps_it_to_boardType() {
        given()
          .contentType("application/json")
          .body("{\"name\":\"Legacy kanban board\",\"type\":\"kanban-v2\"}")
          .when().post("/api/boards")
          .then()
             .statusCode(201)
             .body("type", is("whiteboard"))
             .body("boardType", is("kanban-v2"));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void update_accepts_arbitrary_boardType_strings_without_server_whitelist_changes() {
        String id =
            given()
              .contentType("application/json")
              .body("{\"name\":\"My board\",\"type\":\"whiteboard\",\"boardType\":\"advanced\"}")
              .when().post("/api/boards")
              .then()
                 .statusCode(201)
                 .extract().path("id");

        given()
          .contentType("application/json")
          .body("{\"type\":\"whiteboard\",\"boardType\":\"timeline-pro\"}")
          .when().patch("/api/boards/" + id)
          .then()
             .statusCode(200)
             .body("type", is("whiteboard"))
             .body("boardType", is("timeline-pro"));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void update_returns_conflict_for_archived_board() {
        String id = UUID.randomUUID().toString();
        boardsRepository.create(new Board(
                id,
                "Archived",
                "whiteboard",
                "advanced",
                "alice",
                "archived",
                null,
                null
        ));

        given()
          .contentType("application/json")
          .body("{\"name\":\"Renamed\"}")
          .when().patch("/api/boards/" + id)
          .then()
             .statusCode(409)
             .body("code", is("BOARD_NOT_ACTIVE"));
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void other_user_gets_404_for_board_they_dont_own() {
        String id = UUID.randomUUID().toString();
        boardsRepository.create(new Board(
                id,
                "Secret",
                "whiteboard",
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
