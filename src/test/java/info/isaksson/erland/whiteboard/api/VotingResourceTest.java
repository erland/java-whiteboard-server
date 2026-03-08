package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

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
import info.isaksson.erland.whiteboard.persistence.InMemoryPublicationsRepository;
import info.isaksson.erland.whiteboard.publication.PublicationService;
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
    @Inject PublicationService publicationService;
    @Inject InMemoryPublicationsRepository inMemoryPublicationsRepository;

    String boardId;

    @BeforeEach
    void setup() {
        if (inMemoryBoardsRepository != null) inMemoryBoardsRepository.clear();
        if (inMemoryBoardPermissionsRepository != null) inMemoryBoardPermissionsRepository.clear();
        if (inMemoryVotingSessionsRepository != null) inMemoryVotingSessionsRepository.clear();
        if (inMemoryVoteRecordsRepository != null) inMemoryVoteRecordsRepository.clear();
        if (inMemoryPublicationsRepository != null) inMemoryPublicationsRepository.clear();
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
    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void facilitator_can_view_hidden_progress_and_anonymous_votes_after_close() {
        String sessionId = votingService.openSession(
                votingService.createDraftSession(
                        boardId,
                        VotingScopeType.BOARD,
                        boardId,
                        "alice",
                        new VotingRules(true, false, 2, true, false, true, null)).id())
                .id();
        votingService.castVote(sessionId, "bob", "viewer", false, "shape-1", 1);

        given()
                .when().get("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/results")
                .then()
                .statusCode(200)
                .body("progressHidden", equalTo(false))
                .body("totalsByTarget.shape-1", equalTo(1))
                .body("identitiesHidden", equalTo(true))
                .body("visibleVotes.size()", equalTo(0));

        votingService.closeSession(sessionId);

        given()
                .when().get("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/results")
                .then()
                .statusCode(200)
                .body("progressHidden", equalTo(false))
                .body("identitiesHidden", equalTo(false))
                .body("visibleVotes.size()", equalTo(1))
                .body("visibleVotes[0].participantId", equalTo("bob"));
    }



    @Test
    @TestSecurity(user = "alice", roles = { "whiteboard-user" })
    void invalid_transition_returns_validation_error() {
        String sessionId = votingService.createDraftSession(boardId, VotingScopeType.BOARD, boardId, "alice", VotingRules.defaults()).id();

        given()
                .when().post("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/reveal")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void anonymous_publication_reader_requires_participant_token_to_vote() {
        String sessionId = votingService.openSession(
                votingService.createDraftSession(
                        boardId,
                        VotingScopeType.BOARD,
                        boardId,
                        "alice",
                        new VotingRules(true, true, 2, true, false, true, null)).id())
                .id();
        String token = publicationService.createBoardPublication(boardId, "alice", null, false).accessToken();

        given()
                .contentType("application/json")
                .body("{" +
                        "\"targetRef\":\"shape-1\"," +
                        "\"voteValue\":1}")
                .when().post("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/votes?publicationToken=" + token)
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", equalTo("Field 'participantToken' is required for anonymous publication voting."));
    }

    @Test
    void anonymous_publication_reader_with_invalid_token_cannot_access_results_or_vote() {
        String sessionId = votingService.openSession(
                votingService.createDraftSession(
                        boardId,
                        VotingScopeType.BOARD,
                        boardId,
                        "alice",
                        new VotingRules(true, true, 2, true, false, true, null)).id())
                .id();

        given()
                .when().get("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/results?publicationToken=invalid-token")
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));

        given()
                .contentType("application/json")
                .body("{" +
                        "\"targetRef\":\"shape-1\"," +
                        "\"voteValue\":1}")
                .when().post("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/votes?publicationToken=invalid-token&participantToken=anon-1")
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void anonymous_publication_reader_participant_id_is_stable_for_same_token_pair() {
        String sessionId = votingService.openSession(
                votingService.createDraftSession(
                        boardId,
                        VotingScopeType.BOARD,
                        boardId,
                        "alice",
                        new VotingRules(true, true, 2, true, false, true, null)).id())
                .id();
        String token = publicationService.createBoardPublication(boardId, "alice", null, false).accessToken();

        String participantId1 = given()
                .contentType("application/json")
                .body("{" +
                        "\"targetRef\":\"shape-1\"," +
                        "\"voteValue\":1}")
                .when().post("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/votes?publicationToken=" + token + "&participantToken=anon-1")
                .then()
                .statusCode(201)
                .body("participantId", startsWith("publication-participant:"))
                .extract().path("participantId");

        String participantId2 = given()
                .contentType("application/json")
                .body("{" +
                        "\"targetRef\":\"shape-1\"," +
                        "\"voteValue\":2}")
                .when().post("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/votes?publicationToken=" + token + "&participantToken=anon-1")
                .then()
                .statusCode(201)
                .body("participantId", startsWith("publication-participant:"))
                .extract().path("participantId");

        org.junit.jupiter.api.Assertions.assertEquals(participantId1, participantId2);
    }


    @Test
    void anonymous_publication_reader_can_view_results_with_publication_token() {
        String sessionId = votingService.openSession(
                votingService.createDraftSession(
                        boardId,
                        VotingScopeType.BOARD,
                        boardId,
                        "alice",
                        new VotingRules(true, true, 2, true, false, true, null)).id())
                .id();
        votingService.castVote(sessionId, "bob", "viewer", false, "shape-1", 1);
        String token = publicationService.createBoardPublication(boardId, "alice", null, false).accessToken();

        given()
                .when().get("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/results?publicationToken=" + token)
                .then()
                .statusCode(200)
                .body("progressHidden", equalTo(true))
                .body("identitiesHidden", equalTo(true));
    }

    @Test
    void anonymous_publication_reader_can_vote_when_session_rules_allow_publication_participation() {
        String sessionId = votingService.openSession(
                votingService.createDraftSession(
                        boardId,
                        VotingScopeType.BOARD,
                        boardId,
                        "alice",
                        new VotingRules(true, true, 2, true, false, true, null)).id())
                .id();
        String token = publicationService.createBoardPublication(boardId, "alice", null, false).accessToken();

        given()
                .contentType("application/json")
                .body("{" +
                        "\"targetRef\":\"shape-1\"," +
                        "\"voteValue\":1}")
                .when().post("/api/boards/" + boardId + "/voting-sessions/" + sessionId + "/votes?publicationToken=" + token + "&participantToken=anon-1")
                .then()
                .statusCode(201)
                .body("sessionId", equalTo(sessionId));
    }

    @Test
    void anonymous_publication_token_cannot_read_results_for_session_on_another_board() {
        String otherBoardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(otherBoardId, "Other board", "whiteboard", "advanced", "alice", "active", Instant.now(), Instant.now()));
        String sessionId = votingService.openSession(
                votingService.createDraftSession(
                        otherBoardId,
                        VotingScopeType.BOARD,
                        otherBoardId,
                        "alice",
                        new VotingRules(true, true, 2, true, false, true, null)).id())
                .id();
        String token = publicationService.createBoardPublication(boardId, "alice", null, false).accessToken();

        given()
                .when().get("/api/boards/" + otherBoardId + "/voting-sessions/" + sessionId + "/results?publicationToken=" + token)
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }


}
