package no.sikt.nva.uac.lambda;

import static io.restassured.RestAssured.given;
import static no.sikt.nva.uac.lambda.UnderTheBasePath.BASE_PATH;
import static no.sikt.nva.uac.testing.MockIssuer.tokenWithScopes;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.arc.Arc;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import no.sikt.nva.uac.http.security.Scopes;
import no.sikt.nva.uac.persistence.organisation.UnitRows;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A request through the shared domain, whose REST API events keep the base path in the path. */
@QuarkusTest
@TestProfile(UnderTheBasePath.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
class LambdaBasePathTest {

  private static final String ORGANISATION = BASE_PATH + "/organisations/{identifier}";
  private static final String UIO = "185.0.0.0";
  private static final String READER = tokenWithScopes(Scopes.ORGANISATION_READ);
  private static final int OK = 200;

  @BeforeEach
  void seedUnit() {
    var rows = new UnitRows(Arc.container().instance(DSLContext.class).get());
    rows.deleteAll();
    rows.institution(UIO, "UiO");
    rows.label(UIO, "nb", "Universitetet i Oslo");
  }

  @Test
  void shouldServeAUnitWhenThePathStartsWithTheBasePath() {
    var response = given().basePath("").auth().oauth2(READER).when().get(ORGANISATION, UIO);

    assertThat(response.statusCode()).isEqualTo(OK);
  }
}
