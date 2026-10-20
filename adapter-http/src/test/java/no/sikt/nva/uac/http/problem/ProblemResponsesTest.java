package no.sikt.nva.uac.http.problem;

import static io.restassured.RestAssured.given;
import static no.sikt.nva.uac.http.security.MockIssuer.tokenWithScopes;
import static no.sikt.nva.uac.organisation.UnitIdentifier.REQUIRED_FORM;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import no.sikt.nva.uac.http.security.Scopes;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProblemResponsesTest {

  private static final String ORGANISATION = "/organisations/{identifier}";
  private static final String MALFORMED = "185.15";
  private static final String HOSTILE = "<script>alert(1)</script>";
  private static final String UNKNOWN = "185.99.0.0";
  private static final String UNKNOWN_PATH = "/no-such-resource";
  private static final String WHOLE_BODY = "$";
  private static final String READER = tokenWithScopes(Scopes.ORGANISATION_READ);
  private static final int BAD_REQUEST = 400;
  private static final int NOT_FOUND = 404;

  @Test
  void shouldRejectMalformedIdentifier() {
    var response = given().auth().oauth2(READER).when().get(ORGANISATION, MALFORMED);

    assertThat(response.statusCode()).isEqualTo(BAD_REQUEST);
  }

  @Test
  void shouldAnswerWithProblemMediaType() {
    var response = given().auth().oauth2(READER).when().get(ORGANISATION, MALFORMED);

    assertThat(response.contentType()).isEqualTo(Problem.MEDIA_TYPE);
  }

  @Test
  void shouldDescribeMalformedIdentifierAsProblem() {
    var response = given().auth().oauth2(READER).when().get(ORGANISATION, MALFORMED);

    assertThat(response.jsonPath().getMap(WHOLE_BODY))
        .isEqualTo(
            Map.of(
                "type",
                "about:blank",
                "title",
                "Bad Request",
                "status",
                BAD_REQUEST,
                "detail",
                REQUIRED_FORM));
  }

  @Test
  void shouldNeverEchoTheRequestInAProblem() {
    var response = given().auth().oauth2(READER).when().get(ORGANISATION, HOSTILE);

    assertThat(response.body().asString()).doesNotContain(HOSTILE);
  }

  @Test
  void shouldDescribeUnknownUnitAsProblem() {
    var response = given().auth().oauth2(READER).when().get(ORGANISATION, UNKNOWN);

    assertThat(response.jsonPath().getMap(WHOLE_BODY))
        .isEqualTo(
            Map.of(
                "type",
                "about:blank",
                "title",
                "Not Found",
                "status",
                NOT_FOUND,
                "detail",
                NotFoundExceptionMapper.NOTHING_AT_PATH));
  }

  @Test
  void shouldDescribeUnknownPathAsProblem() {
    var response = given().auth().oauth2(READER).when().get(UNKNOWN_PATH);

    assertThat(response.statusCode()).isEqualTo(NOT_FOUND);
  }
}
