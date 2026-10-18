package no.sikt.nva.uac.testing;

import io.smallrye.jwt.build.Jwt;

/**
 * Tokens signed with the key of the mock OIDC issuer that Quarkus's {@code
 * OidcWiremockTestResource} serves. A module that uses them registers that test resource once, on
 * any class of its test source set.
 */
public final class MockIssuer {

  public static final String ISSUER = "https://server.example.com";
  public static final String ANOTHER_ISSUER = "https://another.example.com";
  private static final String SCOPE_CLAIM = "scope";
  private static final String SCOPE_SEPARATOR = " ";
  private static final String SIGNING_KEY = "privateKey.jwk";

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
        .sign(SIGNING_KEY);
  }
}
