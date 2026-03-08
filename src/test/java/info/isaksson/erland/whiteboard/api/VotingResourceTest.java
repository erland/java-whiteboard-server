package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardPermissionsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryVoteRecordsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryVotingSessionsRepository;
import info.isaksson.erland.whiteboard.voting.VotingRules;
import info.isaksson.erland.whiteboard.voting.VotingScopeType;
import info.isaksson.erland.whiteboard.voting.VotingService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

@QuarkusTest
public class VotingResourceTest {

    @Inject BoardsRepository boardsRepository;
    @Inject VotingService votingService;
    @Inject InMemoryBoardsRepository inMemoryBoardsRepository;
    @Inject InMemoryBoardPermissionsRepository inMemoryBoardPermissionsRepository;
    @Inject InMemoryVotingSessionsRepository inMemoryVotingSessionsRepository;
    @Inject InMemoryVoteRecordsRepository inMemoryVoteRecordsRepository;

    String boardId;

    @BeforeEach
    void setup() {
        if (inMemoryBoardsRepository != null) inMemoryBoardsRepository.clear();
        if (inMemoryBoardPermissionsRepository != null) inMemoryBoardPermissionsRepository.clear();
        if (inMemoryVotingSessionsRepository != null) inMemoryVotingSessionsRepository.clear();
        if (inMemoryVoteRecordsRepository != null) inMemoryVoteRecordsRepository.clear();
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Voting board", "whiteboard", "advanced", "alice", "active", Instant.now(), Instant.now()));
        if (inMemoryBoardPermissionsRepository != null) {
            inMemoryBoardPermissionsRepository.upsert(boardId, "bob", "viewer");
            inMemoryBoardPermissionsRepository.upsert(boardId, "carol", "editor");
        }
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void owner_can_create_open_close_reveal_and_list_voting_sessions() {
        String sessionId = given()
                .contentType("application/json")
                .body("""
                        {"scopeType":"section","scopeRef":"section-1","maxVotesPerParticipant":3,
                         "allowVoteUpdates":true,"anonymousVotes":false,"showProgressDuringVoting":true}
                        """)
                .when().post("/api/boards/" + boardId + "/voting-sessions")
                .then()
                .statusCode(201)
                .body("boardId", equalTo(boardId))
                .body("scopeType", equalTo("section"))
                .body("scopeRef", equalTo("section-1"))
                .body("rules.maxVotesPerParticipant", equalTo(3))
                .extract().path("id");

        given()
                .when().post("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/open")
                .then()
                .statusCode(200)
                .body("state", equalTo("open"))
                .body("openedAt", notNullValue());

        given()
                .when().get("/api/boards/" + boardId + "/voting-sessions")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(sessionId));

        given()
                .when().post("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/close")
                .then()
                .statusCode(200)
                .body("state", equalTo("closed"));

        given()
                .when().post("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/reveal")
                .then()
                .statusCode(200)
                .body("state", equalTo("revealed"))
                .body("revealedAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void viewer_can_vote_when_session_rules_allow_viewer_participation() {
        String sessionId = votingService.openSession(
                votingService.createDraftSession(boardId, VotingScopeType.BOARD, boardId, "alice", VotingRules.defaults()).id())
                .id();

        given()
                .contentType("application/json")
                .body("{" +
                        "\"targetRef\":\"shape-1\"," +
                        "\"voteValue\":1}")
                .when().post("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/votes")
                .then()
                .statusCode(201)
                .body("sessionId", equalTo(sessionId))
                .body("participantId", equalTo("bob"))
                .body("targetRef", equalTo("shape-1"));

        given()
                .when().get("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/results")
                .then()
                .statusCode(200)
                .body("progressHidden", equalTo(true))
                .body("totalsByTarget.size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void viewer_vote_is_rejected_when_session_disallows_viewer_participation() {
        String sessionId = votingService.openSession(
                votingService.createDraftSession(
                        boardId,
                        VotingScopeType.BOARD,
                        boardId,
                        "alice",
                        new VotingRules(false, false, 1, true, false, false, null)).id())
                .id();

        given()
                .contentType("application/json")
                .body("{" +
                        "\"targetRef\":\"shape-1\"," +
                        "\"voteValue\":1}")
                .when().post("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/votes")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = "carol", roles = { "whiteboard-user" })
    void non_owner_cannot_manage_voting_sessions() {
        given()
                .contentType("application/json")
                .body("{\"scopeType\":\"board\"}")
                .when().post("/api/boards/" + boardId + "/voting-sessions")
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void results_expose_visible_progress_for_non_anonymous_sessions() {
        String sessionId = votingService.openSession(
                votingService.createDraftSession(
                        boardId,
                        VotingScopeType.BOARD,
                        boardId,
                        "alice",
                        new VotingRules(true, false, 3, false, true, true, null)).id())
                .id();
        votingService.castVote(sessionId, "bob", "viewer", false, "shape-1", 2);

        given()
                .when().get("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/results")
                .then()
                .statusCode(200)
                .body("progressHidden", equalTo(false))
                .body("identitiesHidden", equalTo(false))
                .body("totalsByTarget.shape-1", equalTo(2))
                .body("visibleVotes.size()", equalTo(1))
                .body("visibleVotes[0].participantId", equalTo("bob"));
    }

    @Test
    @TestSecurity(user = "bob", roles = { "whiteboard-user" })
    void participant_can_remove_vote_when_updates_allowed() {
        String sessionId = votingService.openSession(
                votingService.createDraftSession(
                        boardId,
                        VotingScopeType.BOARD,
                        boardId,
                        "alice",
                        new VotingRules(true, false, 1, true, false, true, null)).id())
                .id();
        votingService.castVote(sessionId, "bob", "viewer", false, "shape-1", 1);

        given()
                .when().delete("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/votes?targetRef=shape-1")
                .then()
                .statusCode(204);
    }
}
