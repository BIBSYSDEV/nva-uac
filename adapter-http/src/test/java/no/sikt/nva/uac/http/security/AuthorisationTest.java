package no.sikt.nva.uac.http.security;

import static io.restassured.RestAssured.given;
import static no.sikt.nva.uac.testing.MockIssuer.tokenFromAnotherIssuer;
import static no.sikt.nva.uac.testing.MockIssuer.tokenWithScopes;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import java.util.Map;
import no.sikt.nva.uac.http.problem.ForbiddenExceptionMapper;
import no.sikt.nva.uac.http.problem.UnauthorizedExceptionMapper;
import org.junit.jupiter.api.Test;

/**
 * Also registers the mock OIDC issuer, which Quarkus then starts for every test in this module; the
 * tokens come from {@code MockIssuer} in test-support.
 */
@QuarkusTest
@QuarkusTestResource(OidcWiremockTestResource.class)
class AuthorisationTest {

  private static final String ORGANISATION = "/organisations/{identifier}";
  private static final String UIO = "185.0.0.0";
  private static final String UNRELATED_SCOPE = "uac/project.read";
  private static final String WHOLE_BODY = "$";
  private static final int OK = 200;
  private static final int UNAUTHORIZED = 401;
  private static final int FORBIDDEN = 403;

  @Test
  void shouldRejectRequestWithoutToken() {
    var response = given().when().get(ORGANISATION, UIO);

    assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED);
  }

  @Test
  void shouldChallengeForBearerTokenWhenMissing() {
    var response = given().when().get(ORGANISATION, UIO);

    assertThat(response.header("WWW-Authenticate")).isEqualTo("Bearer");
  }

  @Test
  void shouldDescribeMissingTokenAsProblem() {
    var response = given().when().get(ORGANISATION, UIO);

    assertThat(response.jsonPath().getMap(WHOLE_BODY))
        .isEqualTo(
            Map.of(
                "type",
                "about:blank",
                "title",
                "Unauthorized",
                "status",
                UNAUTHORIZED,
                "detail",
                UnauthorizedExceptionMapper.TOKEN_REQUIRED));
  }

  @Test
  void shouldRejectTokenFromAnotherIssuer() {
    var token = tokenFromAnotherIssuer(Scopes.NVA_BACKEND);

    var response = given().auth().oauth2(token).when().get(ORGANISATION, UIO);

    assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED);
  }

  @Test
  void shouldRejectTokenWithoutRequiredScope() {
    var token = tokenWithScopes(UNRELATED_SCOPE);

    var response = given().auth().oauth2(token).when().get(ORGANISATION, UIO);

    assertThat(response.statusCode()).isEqualTo(FORBIDDEN);
  }

  @Test
  void shouldDescribeMissingScopeAsProblem() {
    var token = tokenWithScopes(UNRELATED_SCOPE);

    var response = given().auth().oauth2(token).when().get(ORGANISATION, UIO);

    assertThat(response.jsonPath().getMap(WHOLE_BODY))
        .isEqualTo(
            Map.of(
                "type",
                "about:blank",
                "title",
                "Forbidden",
                "status",
                FORBIDDEN,
                "detail",
                ForbiddenExceptionMapper.SCOPE_MISSING));
  }

  @Test
  void shouldAcceptTheNvaBackendScope() {
    var token = tokenWithScopes(Scopes.NVA_BACKEND);

    var response = given().auth().oauth2(token).when().get(ORGANISATION, UIO);

    assertThat(response.statusCode()).isEqualTo(OK);
  }

  @Test
  void shouldAcceptTheOrganisationReadScope() {
    var token = tokenWithScopes(UNRELATED_SCOPE, Scopes.ORGANISATION_READ);

    var response = given().auth().oauth2(token).when().get(ORGANISATION, UIO);

    assertThat(response.statusCode()).isEqualTo(OK);
  }
}
