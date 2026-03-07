package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class OpenApiResourceTest {

    @Test
    void openapi_document_is_exposed() {
        given()
          .when().get("/q/openapi")
          .then()
             .statusCode(200)
             .body(anyOf(containsString("openapi: 3."), containsString("\"openapi\":\"3.")))
             .body(containsString("Whiteboard Server REST API"))
             .body(containsString("bearerAuth"))
             .body(containsString("Boards"))
             .body(containsString("Snapshots"))
             .body(containsString("Invites"))
             .body(containsString("Identity"))
             .body(containsString("List boards"))
             .body(containsString("Create board"))
             .body(containsString("Get latest snapshot"))
             .body(containsString("List snapshot versions"))
             .body(containsString("Create invite"))
             .body(containsString("List invites"))
             .body(containsString("Revoke invite"))
             .body(containsString("Validate invite"))
             .body(containsString("Accept invite"))
             .body(containsString("Get current user"))
             .body(containsString("Standard JSON error payload returned by the REST API"))
             .body(containsString("Stable machine-readable error code"))
             .body(containsString("Opaque whiteboard snapshot JSON payload"))
             .body(containsString("Plain-text invite token"))
             .body(containsString("Board metadata returned by the REST API"));
    }

    @Test
    void swagger_ui_is_exposed() {
        given()
          .when().get("/q/swagger-ui/")
          .then()
             .statusCode(200)
             .body(anyOf(
                 containsString("OpenAPI UI"),
                 containsString("id=\"swagger-ui\""),
                 containsString("SwaggerUIBundle")
             ));
    }
}
