package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
public class MeResourceTest {

    @Test
    void me_requires_auth() {
        given()
          .when().get("/api/me")
          .then()
             .statusCode(401)
             .body("code", is("UNAUTHORIZED"));
    }

    @Test
    @TestSecurity(user = "alice-subject", roles = { "whiteboard-user" })
    void me_returns_identity() {
        given()
          .when().get("/api/me")
          .then()
             .statusCode(200)
             .body("userId", is("alice-subject"))
             .body("roles", hasItem("whiteboard-user"));
    }

    @Test
    @TestSecurity(user = "bob-subject", roles = { })
    void me_forbidden_without_required_role() {
        given()
          .when().get("/api/me")
          .then()
             .statusCode(403)
             .body("code", is("FORBIDDEN"));
    }
}
