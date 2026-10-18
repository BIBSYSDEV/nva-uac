package no.sikt.nva.uac.http.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.smallrye.jwt.build.Jwt;

/**
 * A mock OIDC issuer for every test in this module, served by WireMock, and the tokens it signs.
 * Registering the test resource here applies it to all Quarkus tests of the module.
 */
@QuarkusTestResource(OidcWiremockTestResource.class)
public final class MockIssuer {

  public static final String ISSUER = "https://server.example.com";
  public static final String ANOTHER_ISSUER = "https://another.example.com";
  private static final String SCOPE_CLAIM = "scope";
  private static final String SCOPE_SEPARATOR = " ";

  private MockIssuer() {
    // NO-OP
  }

  /** A token from the trusted issuer granting exactly the given scopes. */
  public static String tokenWithScopes(String... scopes) {
    return tokenFrom(ISSUER, scopes);
  }

  /** A token signed with the trusted key but naming another issuer. */
  public static String tokenFromAnotherIssuer(String... scopes) {
    return tokenFrom(ANOTHER_ISSUER, scopes);
  }

  private static String tokenFrom(String issuer, String... scopes) {
    return Jwt.issuer(issuer)
        .subject("nva-backend-client")
        .claim(SCOPE_CLAIM, String.join(SCOPE_SEPARATOR, scopes))
        .sign();
  }
}
