package no.sikt.nva.uac.http.organisation;

import static io.restassured.RestAssured.given;
import static no.sikt.nva.uac.http.organisation.OrganisationTestWiring.FACULTY_AT_UIO;
import static no.sikt.nva.uac.http.organisation.OrganisationTestWiring.UIO;
import static no.sikt.nva.uac.http.security.MockIssuer.tokenWithScopes;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import no.sikt.nva.uac.http.security.Scopes;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OrganisationResourceTest {

  private static final String ORGANISATION = "/organisations/{identifier}";
  private static final String UNKNOWN = "185.99.0.0";
  private static final int OK = 200;
  private static final int NOT_FOUND = 404;
  private static final String WHOLE_BODY = "$";
  private static final String READER = tokenWithScopes(Scopes.ORGANISATION_READ);

  @Test
  void shouldReturnNotFoundForUnknownUnit() {
    var response = given().auth().oauth2(READER).when().get(ORGANISATION, UNKNOWN);

    assertThat(response.statusCode()).isEqualTo(NOT_FOUND);
  }

  @Test
  void shouldReturnOkForKnownUnit() {
    var response =
        given()
            .auth()
            .oauth2(READER)
            .when()
            .get(ORGANISATION, FACULTY_AT_UIO.identifier().toString());

    assertThat(response.statusCode()).isEqualTo(OK);
  }

  @Test
  void shouldRepresentUnitWithItsNameAndParent() {
    var response =
        given()
            .auth()
            .oauth2(READER)
            .when()
            .get(ORGANISATION, FACULTY_AT_UIO.identifier().toString());

    assertThat(response.jsonPath().getMap(WHOLE_BODY))
        .isEqualTo(
            Map.of(
                "id", "185.15.0.0",
                "name", Map.of("nb", "Det matematisk-naturvitenskapelige fakultet"),
                "partOf", "185.0.0.0"));
  }

  @Test
  void shouldLeaveOutParentAndAcronymWhenAbsent() {
    var response =
        given().auth().oauth2(READER).when().get(ORGANISATION, UIO.identifier().toString());

    assertThat(response.jsonPath().getMap(WHOLE_BODY))
        .isEqualTo(Map.of("id", "185.0.0.0", "name", Map.of("nb", "Universitetet i Oslo")));
  }
}
