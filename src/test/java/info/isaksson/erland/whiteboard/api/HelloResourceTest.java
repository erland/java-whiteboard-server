package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class HelloResourceTest {

    @Test
    void healthz_returns_ok() {
        given()
          .when().get("/api/healthz")
          .then()
             .statusCode(200)
             .body("status", is("ok"));
    }
}
