package no.sikt.nva.uac.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class MockIssuerTest {

  private static final String READ = "uac/organisation.read";
  private static final String WRITE = "uac/person.write";
  private static final int PAYLOAD = 1;

  @Test
  void shouldNameTheTrustedIssuer() {
    var token = MockIssuer.tokenWithScopes(READ);

    assertThat(payloadOf(token)).contains("\"iss\":\"" + MockIssuer.ISSUER + "\"");
  }

  @Test
  void shouldNameAnotherIssuerOnRequest() {
    var token = MockIssuer.tokenFromAnotherIssuer(READ);

    assertThat(payloadOf(token)).contains("\"iss\":\"" + MockIssuer.ANOTHER_ISSUER + "\"");
  }

  @Test
  void shouldJoinScopesWithSpaces() {
    var token = MockIssuer.tokenWithScopes(READ, WRITE);

    assertThat(payloadOf(token)).contains("\"scope\":\"" + READ + " " + WRITE + "\"");
  }

  private static String payloadOf(String token) {
    var encodedPayload = token.split("\\.")[PAYLOAD];
    return new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
  }
}
