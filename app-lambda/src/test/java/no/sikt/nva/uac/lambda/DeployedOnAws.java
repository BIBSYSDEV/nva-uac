package no.sikt.nva.uac.lambda;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * The prod profile, which every AWS environment runs, with what the deployment supplies and fixed
 * fake AWS credentials. Signing an RDS authentication token is local, so no AWS is needed.
 */
public final class DeployedOnAws implements QuarkusTestProfile {

  static final String PROXY = "proxy.example.invalid";
  static final String DATABASE_USER = "nvauac_admin";

  @Override
  public String getConfigProfile() {
    return "prod";
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "UAC_DATABASE_HOST", PROXY,
        "quarkus.datasource.username", DATABASE_USER,
        "quarkus.rds.aws.region", "eu-west-1",
        "quarkus.rds.aws.credentials.type", "static",
        "quarkus.rds.aws.credentials.static-provider.access-key-id", "test-access-key",
        "quarkus.rds.aws.credentials.static-provider.secret-access-key", "test-secret-key",
        "quarkus.oidc.tenant-enabled", "false",
        "quarkus.lambda.mock-event-server.test-port", "0");
  }
}
