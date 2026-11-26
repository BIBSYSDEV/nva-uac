package no.sikt.nva.uac.lambda;

import static no.sikt.nva.uac.lambda.DeployedOnAws.DATABASE_USER;
import static no.sikt.nva.uac.lambda.DeployedOnAws.PROXY;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.arc.Arc;
import io.quarkus.credentials.CredentialsProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.literal.NamedLiteral;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The login to RDS Proxy as the deployed function makes it, from the prod configuration. */
@QuarkusTest
@TestProfile(DeployedOnAws.class)
class RdsIamLoginTest {

  private static final String PROVIDER_BEAN = "rds-credentials-provider";
  private static final String PROVIDER_NAME = "proxy";

  @Test
  void shouldLogInAsTheDatabaseUser() {
    var login = loginToTheProxy();

    assertThat(login).containsEntry(CredentialsProvider.USER_PROPERTY_NAME, DATABASE_USER);
  }

  @Test
  void shouldLogInWithATokenSignedForTheProxy() {
    var login = loginToTheProxy();

    assertThat(login.get(CredentialsProvider.PASSWORD_PROPERTY_NAME))
        .startsWith(PROXY + ":5432/?")
        .contains("Action=connect", "DBUser=" + DATABASE_USER, "X-Amz-Signature=");
  }

  private static Map<String, String> loginToTheProxy() {
    return Arc.container()
        .instance(CredentialsProvider.class, NamedLiteral.of(PROVIDER_BEAN))
        .get()
        .getCredentials(PROVIDER_NAME);
  }
}
