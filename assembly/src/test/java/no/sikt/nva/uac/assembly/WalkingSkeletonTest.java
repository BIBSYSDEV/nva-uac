package no.sikt.nva.uac.assembly;

import static io.restassured.RestAssured.given;
import static no.sikt.nva.uac.testing.MockIssuer.tokenWithScopes;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.arc.Arc;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import java.util.Map;
import no.sikt.nva.uac.http.security.Scopes;
import no.sikt.nva.uac.persistence.organisation.UnitRows;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The whole walking skeleton, locally: token, HTTP adapter, use case, migrated PostgreSQL. */
@QuarkusTest
@QuarkusTestResource(OidcWiremockTestResource.class)
class WalkingSkeletonTest {

  private static final String ORGANISATION = "/organisations/{identifier}";
  private static final String HEALTH = "/q/health";
  private static final String UIO = "185.0.0.0";
  private static final String FACULTY = "185.15.0.0";
  private static final String READER = tokenWithScopes(Scopes.ORGANISATION_READ);
  private static final String WHOLE_BODY = "$";
  private static final int OK = 200;

  @BeforeEach
  void seedUnits() {
    var rows = new UnitRows(Arc.container().instance(DSLContext.class).get());
    rows.deleteAll();
    rows.institution(UIO, "UiO");
    rows.label(UIO, "nb", "Universitetet i Oslo");
    rows.unit(FACULTY, UIO);
    rows.label(FACULTY, "en", "Faculty of Mathematics and Natural Sciences");
  }

  @Test
  void shouldReportHealthyWithTheDatabaseReachable() {
    var response = given().when().get(HEALTH);

    assertThat(response.statusCode()).isEqualTo(OK);
  }

  @Test
  void shouldServeAUnitReadFromPostgres() {
    var response = given().auth().oauth2(READER).when().get(ORGANISATION, FACULTY);

    assertThat(response.jsonPath().getMap(WHOLE_BODY))
        .isEqualTo(
            Map.of(
                "id", FACULTY,
                "name", Map.of("en", "Faculty of Mathematics and Natural Sciences"),
                "partOf", UIO));
  }

  @Test
  void shouldServeAnInstitutionWithItsAcronym() {
    var response = given().auth().oauth2(READER).when().get(ORGANISATION, UIO);

    assertThat(response.jsonPath().getMap(WHOLE_BODY))
        .isEqualTo(
            Map.of("id", UIO, "name", Map.of("nb", "Universitetet i Oslo"), "acronym", "UiO"));
  }
}
