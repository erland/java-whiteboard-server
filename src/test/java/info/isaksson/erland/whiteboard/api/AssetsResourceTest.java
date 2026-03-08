package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.util.UUID;

import info.isaksson.erland.whiteboard.assets.Asset;
import info.isaksson.erland.whiteboard.assets.AssetService;
import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryAssetsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardPermissionsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryPublicationsRepository;
import info.isaksson.erland.whiteboard.publication.PublicationService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AssetsResourceTest {

    @Inject BoardsRepository boardsRepository;
    @Inject AssetService assetService;
    @Inject PublicationService publicationService;
    @Inject InMemoryBoardsRepository inMemoryBoardsRepository;
    @Inject InMemoryBoardPermissionsRepository inMemoryBoardPermissionsRepository;
    @Inject InMemoryAssetsRepository inMemoryAssetsRepository;
    @Inject InMemoryPublicationsRepository inMemoryPublicationsRepository;

    String boardId;

    @BeforeEach
    void setup() {
        if (inMemoryBoardsRepository != null) inMemoryBoardsRepository.clear();
        if (inMemoryBoardPermissionsRepository != null) inMemoryBoardPermissionsRepository.clear();
        if (inMemoryAssetsRepository != null) inMemoryAssetsRepository.clear();
        if (inMemoryPublicationsRepository != null) inMemoryPublicationsRepository.clear();
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Assets board", "whiteboard", "advanced", "alice", "active", Instant.now(), Instant.now()));
        if (inMemoryBoardPermissionsRepository != null) {
            inMemoryBoardPermissionsRepository.upsert(boardId, "bob", "viewer");
            inMemoryBoardPermissionsRepository.upsert(boardId, "carol", "editor");
        }
    }

    @Test
    @TestSecurity(user = "carol", roles = { "whiteboard-user" })
    void editor_can_create_activate_and_delete_asset() {
        String assetId = given()
                .contentType("application/json")
                .body("{\"logicalName\":\"diagram.png\",\"contentType\":\"image/png\",\"sizeBytes\":2048,\"integrityHash\":\"sha256:abc\",\"versionTag\":\"client-v1\"}")
                .when().post("/api/boards/" + boardId + "/assets")
                .then()
                .statusCode(201)
                .body("boardId", equalTo(boardId))
                .body("scopeType", equalTo("board"))
                .body("state", equalTo("pending"))
                .body("createdByUserId", equalTo("carol"))
                .extract().path("id");

        given()
                .contentType("application/json")
                .body("{\"versionTag\":\"stored-v2\"}")
                .when().post("/api/boards/" + boardId + "/assets/" + assetId + "/activate")
                .then()
                .statusCode(200)
                .body("id", equalTo(assetId))
                .body("state", equalTo("active"))
                .body("versionTag", equalTo("stored-v2"))
                .body("activatedAt", notNullValue());

        given()
                .when().delete("/api/boards/" + boardId + "/assets/" + assetId)
                .then()
                .statusCode(200)
                .body("state", equalTo("deleted"))
                .body("deletedAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "carol", roles = { "whiteboard-user" })
    void editor_can_fail_and_quarantine_asset_with_reason() {
        Asset asset = assetService.createBoardAssetMetadata(boardId, "diagram.png", "image/png", 2048L, "carol", null, null);

        given()
                .contentType("application/json")
                .body("{\"failureReason\":\"virus scan failed\"}")
                .when().post("/api/boards/" + boardId + "/assets/" + asset.id() + "/fail")
                .then()
                .statusCode(200)
                .body("state", equalTo("failed"))
                .body("failureReason", equalTo("virus scan failed"));

        given()
                .contentType("application/json")
                .body("{\"versionTag\":\"reprocessed-v1\"}")
                .when().post("/api/boards/" + boardId + "/assets/" + asset.id() + "/activate")
                .then()
                .statusCode(200)
                .body("state", equalTo("active"));

        given()
                .contentType("application/json")
                .body("{\"failureReason\":\"manual quarantine\"}")
                .when().post("/api/boards/" + boardId + "/assets/" + asset.id() + "/quarantine")
                .then()
                .statusCode(200)
                .body("state", equalTo("quarantined"))
                .body("failureReason", equalTo("manual quarantine"));
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void viewer_can_list_assets_but_cannot_create_them() {
        assetService.createBoardAssetMetadata(boardId, "diagram.png", "image/png", 2048L, "alice", null, null);

        given()
                .when().get("/api/boards/" + boardId + "/assets")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].logicalName", equalTo("diagram.png"));

        given()
                .contentType("application/json")
                .body("{\"logicalName\":\"blocked.png\",\"contentType\":\"image/png\",\"sizeBytes\":10}")
                .when().post("/api/boards/" + boardId + "/assets")
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void anonymous_publication_reader_can_list_assets() {
        assetService.createBoardAssetMetadata(boardId, "public.pdf", "application/pdf", 4096L, "alice", null, null);
        String token = publicationService.createBoardPublication(boardId, "alice", null, false).accessToken();

        given()
                .when().get("/api/boards/" + boardId + "/assets?publicationToken=" + token)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].logicalName", equalTo("public.pdf"));
    }



    @Test
    @TestSecurity(user = "carol", roles = { "whiteboard-user" })
    void asset_mutation_rejects_asset_from_another_board_path() {
        String otherBoardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(otherBoardId, "Other board", "whiteboard", "advanced", "alice", "active", Instant.now(), Instant.now()));
        if (inMemoryBoardPermissionsRepository != null) {
            inMemoryBoardPermissionsRepository.upsert(otherBoardId, "carol", "editor");
        }
        Asset asset = assetService.createBoardAssetMetadata(boardId, "diagram.png", "image/png", 2048L, "carol", null, null);

        given()
                .contentType("application/json")
                .body("{\"versionTag\":\"wrong-board\"}")
                .when().post("/api/boards/" + otherBoardId + "/assets/" + asset.id() + "/activate")
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void anonymous_publication_token_cannot_list_assets_on_another_board() {
        assetService.createBoardAssetMetadata(boardId, "public.pdf", "application/pdf", 4096L, "alice", null, null);
        String token = publicationService.createBoardPublication(boardId, "alice", null, false).accessToken();
        String otherBoardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(otherBoardId, "Other board", "whiteboard", "advanced", "alice", "active", Instant.now(), Instant.now()));

        given()
                .when().get("/api/boards/" + otherBoardId + "/assets?publicationToken=" + token)
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "carol", roles = { "whiteboard-user" })
    void fail_requires_reason() {
        Asset asset = assetService.createBoardAssetMetadata(boardId, "diagram.png", "image/png", 2048L, "carol", null, null);

        given()
                .contentType("application/json")
                .body("{}")
                .when().post("/api/boards/" + boardId + "/assets/" + asset.id() + "/fail")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }
}
