package no.sikt.nva.uac.lambda;

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

/** The walking skeleton through the Lambda event path: API Gateway event in, response out. */
@QuarkusTest
@QuarkusTestResource(OidcWiremockTestResource.class)
class LambdaWalkingSkeletonTest {

  private static final String ORGANISATION = "/organisations/{identifier}";
  private static final String UIO = "185.0.0.0";
  private static final String READER = tokenWithScopes(Scopes.ORGANISATION_READ);
  private static final String WHOLE_BODY = "$";
  private static final int UNAUTHORIZED = 401;

  @BeforeEach
  void seedUnits() {
    var rows = new UnitRows(Arc.container().instance(DSLContext.class).get());
    rows.deleteAll();
    rows.institution(UIO, "UiO");
    rows.label(UIO, "nb", "Universitetet i Oslo");
  }

  @Test
  void shouldServeAUnitFromAnApiGatewayEvent() {
    var response = given().auth().oauth2(READER).when().get(ORGANISATION, UIO);

    assertThat(response.jsonPath().getMap(WHOLE_BODY))
        .isEqualTo(
            Map.of("id", UIO, "name", Map.of("nb", "Universitetet i Oslo"), "acronym", "UiO"));
  }

  @Test
  void shouldRejectAnEventWithoutToken() {
    var response = given().when().get(ORGANISATION, UIO);

    assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED);
  }
}
