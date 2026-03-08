package info.isaksson.erland.whiteboard.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

@QuarkusTest
public class ServerCapabilitiesResourceTest {

    @Test
    void capabilities_endpoint_returns_versions_and_enabled_capabilities() {
        given()
          .when().get("/api/capabilities")
          .then()
             .statusCode(200)
             .body("apiVersion", equalTo("1"))
             .body("wsProtocolVersion", equalTo("1"))
             .body("capabilities", hasItem("voting"))
             .body("capabilities", hasItem("ws-reactions"))
             .body("capabilities", hasItem("shared-timer"))
             .body("capabilities", hasItem("ws-voting-events"));
    }

    @Test
    void api_responses_include_contract_headers() {
        given()
          .when().get("/api/healthz")
          .then()
             .statusCode(200)
             .header(ApiCompatibilityFilter.API_VERSION_HEADER, equalTo("1"))
             .header(ApiCompatibilityFilter.WS_PROTOCOL_VERSION_HEADER, equalTo("1"))
             .header(ApiCompatibilityFilter.CAPABILITIES_HEADER, containsString("voting"));
    }

    @Test
    void incompatible_api_version_is_rejected_with_contract_headers() {
        given()
          .header(ApiCompatibilityFilter.API_VERSION_HEADER, "999")
          .when().get("/api/healthz")
          .then()
             .statusCode(412)
             .body("code", equalTo("INCOMPATIBLE_API_VERSION"))
             .header(ApiCompatibilityFilter.API_VERSION_HEADER, equalTo("1"))
             .header(ApiCompatibilityFilter.WS_PROTOCOL_VERSION_HEADER, equalTo("1"));
    }
}
