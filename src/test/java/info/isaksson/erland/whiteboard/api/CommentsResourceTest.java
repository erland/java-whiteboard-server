package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.comments.Comment;
import info.isaksson.erland.whiteboard.comments.CommentService;
import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardPermissionsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryCommentsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryPublicationsRepository;
import info.isaksson.erland.whiteboard.publication.PublicationService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

@QuarkusTest
public class CommentsResourceTest {

    @Inject BoardsRepository boardsRepository;
    @Inject CommentService commentService;
    @Inject PublicationService publicationService;
    @Inject InMemoryBoardsRepository inMemoryBoardsRepository;
    @Inject InMemoryBoardPermissionsRepository inMemoryBoardPermissionsRepository;
    @Inject InMemoryCommentsRepository inMemoryCommentsRepository;
    @Inject InMemoryPublicationsRepository inMemoryPublicationsRepository;

    String boardId;

    @BeforeEach
    void setup() {
        if (inMemoryBoardsRepository != null) inMemoryBoardsRepository.clear();
        if (inMemoryBoardPermissionsRepository != null) inMemoryBoardPermissionsRepository.clear();
        if (inMemoryCommentsRepository != null) inMemoryCommentsRepository.clear();
        if (inMemoryPublicationsRepository != null) inMemoryPublicationsRepository.clear();
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Comments board", "whiteboard", "advanced", "alice", "active", Instant.now(), Instant.now()));
        if (inMemoryBoardPermissionsRepository != null) {
            inMemoryBoardPermissionsRepository.upsert(boardId, "bob", "viewer");
            inMemoryBoardPermissionsRepository.upsert(boardId, "carol", "editor");
        }
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void viewer_can_create_and_list_comments() {
        String commentId = given()
                .contentType("application/json")
                .body("{\"targetType\":\"object\",\"targetRef\":\"shape-1\",\"content\":\"Needs review\"}")
                .when().post("/api/boards/" + boardId + "/comments")
                .then()
                .statusCode(201)
                .body("boardId", equalTo(boardId))
                .body("targetType", equalTo("object"))
                .body("authorUserId", equalTo("bob"))
                .extract().path("id");

        given()
                .when().get("/api/boards/" + boardId + "/comments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(commentId));
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void author_can_reply_and_update_own_comment() {
        String parentId = given()
                .contentType("application/json")
                .body("{\"targetType\":\"board\",\"content\":\"Top level\"}")
                .when().post("/api/boards/" + boardId + "/comments")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType("application/json")
                .body("{\"targetType\":\"comment\",\"parentCommentId\":\"" + parentId + "\",\"content\":\"Reply\"}")
                .when().post("/api/boards/" + boardId + "/comments")
                .then()
                .statusCode(201)
                .body("parentCommentId", equalTo(parentId))
                .body("targetType", equalTo("comment"));

        given()
                .contentType("application/json")
                .body("{\"content\":\"Edited top level\"}")
                .when().patch("/api/boards/" + boardId + "/comments/" + parentId)
                .then()
                .statusCode(200)
                .body("content", equalTo("Edited top level"));
    }

    @Test
    @TestSecurity(user = "carol", roles = { "whiteboard-user" })
    void editor_can_resolve_reopen_and_delete_foreign_comment() {
        Comment comment = commentService.createBoardComment(boardId, "bob", "Needs review");

        given()
                .when().post("/api/boards/" + boardId + "/comments/" + comment.id() + "/resolve")
                .then()
                .statusCode(200)
                .body("state", equalTo("resolved"))
                .body("resolvedAt", notNullValue());

        given()
                .when().post("/api/boards/" + boardId + "/comments/" + comment.id() + "/reopen")
                .then()
                .statusCode(200)
                .body("state", equalTo("active"));

        given()
                .when().delete("/api/boards/" + boardId + "/comments/" + comment.id())
                .then()
                .statusCode(200)
                .body("state", equalTo("deleted"))
                .body("deletedAt", notNullValue());
    }

    @Test
    void anonymous_publication_reader_can_list_comments_when_allowed() {
        commentService.createBoardComment(boardId, "alice", "Public note");
        String token = publicationService.createBoardPublication(boardId, "alice", null, true).accessToken();

        given()
                .when().get("/api/boards/" + boardId + "/comments?publicationToken=" + token)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].content", equalTo("Public note"));
    }

    @Test
    void anonymous_publication_reader_cannot_list_comments_when_not_allowed() {
        commentService.createBoardComment(boardId, "alice", "Private note");
        String token = publicationService.createBoardPublication(boardId, "alice", null, false).accessToken();

        given()
                .when().get("/api/boards/" + boardId + "/comments?publicationToken=" + token)
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "carol", roles = { "whiteboard-user" })
    void non_author_cannot_update_comment_content() {
        Comment comment = commentService.createBoardComment(boardId, "bob", "Needs review");

        given()
                .contentType("application/json")
                .body("{\"content\":\"Editor rewrite\"}")
                .when().patch("/api/boards/" + boardId + "/comments/" + comment.id())
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void reply_requires_parent_comment_id() {
        given()
                .contentType("application/json")
                .body("{\"targetType\":\"comment\",\"content\":\"Reply\"}")
                .when().post("/api/boards/" + boardId + "/comments")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }
}
