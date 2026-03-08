package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.domain.BoardSnapshot;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryPublicationsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemorySnapshotsRepository;
import info.isaksson.erland.whiteboard.persistence.SnapshotsRepository;
import info.isaksson.erland.whiteboard.publication.PublicationService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

@QuarkusTest
public class PublicationsResourceTest {

    @Inject
    BoardsRepository boardsRepository;

    @Inject
    SnapshotsRepository snapshotsRepository;

    @Inject
    PublicationService publicationService;

    @Inject
    InMemoryBoardsRepository inMemoryBoardsRepository;

    @Inject
    InMemorySnapshotsRepository inMemorySnapshotsRepository;

    @Inject
    InMemoryPublicationsRepository inMemoryPublicationsRepository;

    String boardId;

    @BeforeEach
    void setup() {
        if (inMemoryBoardsRepository != null) {
            inMemoryBoardsRepository.clear();
        }
        if (inMemorySnapshotsRepository != null) {
            inMemorySnapshotsRepository.clear();
        }
        if (inMemoryPublicationsRepository != null) {
            inMemoryPublicationsRepository.clear();
        }
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Published board", "whiteboard", "advanced", "alice", "active", Instant.now(), Instant.now()));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void owner_can_create_and_list_board_publication() {
        String publicationId = given()
                .contentType("application/json")
                .body("{\"targetType\":\"board\",\"allowComments\":true}")
                .when().post("/api/boards/" + boardId + "/publications")
                .then()
                .statusCode(201)
                .body("publication.boardId", equalTo(boardId))
                .body("publication.targetType", equalTo("board"))
                .body("publication.allowComments", equalTo(true))
                .body("token", notNullValue())
                .extract().path("publication.id");

        given()
                .when().get("/api/boards/" + boardId + "/publications")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(publicationId));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void owner_can_create_snapshot_publication() {
        BoardSnapshot snapshot = snapshotsRepository.create(boardId, "alice", "{\"elements\":[]}");

        given()
                .contentType("application/json")
                .body("{\"targetType\":\"snapshot\",\"snapshotVersion\":" + snapshot.version() + "}")
                .when().post("/api/boards/" + boardId + "/publications")
                .then()
                .statusCode(201)
                .body("publication.targetType", equalTo("snapshot"))
                .body("publication.snapshotVersion", equalTo((int) snapshot.version()));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void create_snapshot_publication_requires_snapshot_version() {
        given()
                .contentType("application/json")
                .body("{\"targetType\":\"snapshot\"}")
                .when().post("/api/boards/" + boardId + "/publications")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void non_owner_cannot_manage_publications() {
        given()
                .contentType("application/json")
                .body("{\"targetType\":\"board\"}")
                .when().post("/api/boards/" + boardId + "/publications")
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void owner_can_rotate_and_revoke_publication() {
        String publicationId = given()
                .contentType("application/json")
                .body("{\"targetType\":\"board\"}")
                .when().post("/api/boards/" + boardId + "/publications")
                .then()
                .statusCode(201)
                .extract().path("publication.id");

        given()
                .when().post("/api/boards/" + boardId + "/publications/" + publicationId + "/rotate-token")
                .then()
                .statusCode(200)
                .body("publication.id", equalTo(publicationId))
                .body("token", notNullValue());

        given()
                .when().delete("/api/boards/" + boardId + "/publications/" + publicationId)
                .then()
                .statusCode(204);
    }

    @Test
    void resolve_publication_returns_metadata_for_valid_token() {
        String token = publicationService.createBoardPublication(boardId, "alice", null, false).accessToken();

        given()
                .contentType("application/json")
                .body("{\"token\":\"" + token + "\"}")
                .when().post("/api/publications/resolve")
                .then()
                .statusCode(200)
                .body("boardId", equalTo(boardId))
                .body("targetType", equalTo("board"));
    }

    @Test
    void resolve_publication_rejects_revoked_token() {
        var created = publicationService.createBoardPublication(boardId, "alice", null, false);
        publicationService.revoke(created.publication().id());

        given()
                .contentType("application/json")
                .body("{\"token\":\"" + created.accessToken() + "\"}")
                .when().post("/api/publications/resolve")
                .then()
                .statusCode(404)
                .body("code", equalTo("PUBLICATION_REVOKED"));
    }
}
