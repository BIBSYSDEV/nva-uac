package no.sikt.nva.uac.lambda;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * The function as the shared domain reaches it: API Gateway passes the path with the base path in
 * front, so the deployment sets the root path to the base path.
 */
public final class UnderTheBasePath implements QuarkusTestProfile {

  static final String BASE_PATH = "/uac";

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of("quarkus.http.root-path", BASE_PATH);
  }
}
