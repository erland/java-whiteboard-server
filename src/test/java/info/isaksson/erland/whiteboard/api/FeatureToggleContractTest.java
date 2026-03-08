package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
@TestProfile(VotingDisabledProfile.class)
public class FeatureToggleContractTest {

    @Test
    void capabilities_endpoint_hides_disabled_capabilities() {
        given()
          .when().get("/api/capabilities")
          .then()
             .statusCode(200)
             .body("capabilities", not(hasItem("voting")))
             .body("capabilities", not(hasItem("ws-reactions")))
             .body("capabilities", not(hasItem("shared-timer")))
             .body("capabilities", not(hasItem("ws-voting-events")));
    }

    @Test
    @TestSecurity(user = "alice", roles = {"whiteboard-user"})
    void voting_rest_surface_returns_not_found_when_voting_disabled() {
        given()
          .when().get("/api/boards/board-1/voting-sessions")
          .then()
             .statusCode(404);
    }
}
