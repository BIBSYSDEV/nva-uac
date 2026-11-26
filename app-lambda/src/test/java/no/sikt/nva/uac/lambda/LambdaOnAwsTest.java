package no.sikt.nva.uac.lambda;

import static io.restassured.RestAssured.given;
import static no.sikt.nva.uac.testing.MockIssuer.tokenForAnotherClient;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import no.sikt.nva.uac.http.security.Scopes;
import org.junit.jupiter.api.Test;

/**
 * Who gets in to the function as every AWS environment runs it, where the gateway has no authoriser
 * (ADR 0006): nobody without a token from the backend client, on any path.
 */
@QuarkusTest
@TestProfile(DeployedOnAws.class)
class LambdaOnAwsTest {

  private static final int UNAUTHORIZED = 401;

  @Test
  void shouldRefuseTheHealthCheckWithoutToken() {
    var response = given().when().get("/q/health");

    assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED);
  }

  @Test
  void shouldRefuseTheOpenApiDocumentWithoutToken() {
    var response = given().when().get("/q/openapi");

    assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED);
  }

  @Test
  void shouldRefuseATokenIssuedToAnotherClient() {
    var response =
        given()
            .auth()
            .oauth2(tokenForAnotherClient(Scopes.ORGANISATION_READ))
            .when()
            .get("/organisations/185.0.0.0");

    assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED);
  }
}
