package no.sikt.nva.uac.organisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import no.sikt.nva.uac.shared.InvalidIdentifierException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UnitIdentifierTest {

  private static final String TOP_UNIT_OF_UIO = "185.90.0.0";
  private static final String A_UNIT_WITHIN_UIO = "185.0.0.0";
  private static final String UNIT_AT_UIO = "185.15.2.10";
  private static final String HOSTILE_INPUT = "<script>alert(1)</script>";

  @Test
  void shouldRejectNull() {
    String missing = null;

    assertThatThrownBy(() -> UnitIdentifier.parse(missing))
        .isInstanceOf(InvalidIdentifierException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        " ",
        "185",
        "185.15.2",
        "185.15.2.10.1",
        "+185.15.2.10",
        "185.15.2.+10",
        "-185.15.2.10",
        "185.015.2.10",
        "007.0.0.0",
        " 185.15.2.10",
        "185.15.2.10 ",
        "185,15,2,10",
        "185.15.2.1O",
        "1234567890.0.0.0",
        "185.15.2.10\n"
      })
  void shouldRejectMalformedIdentifier(String malformed) {
    assertThatThrownBy(() -> UnitIdentifier.parse(malformed))
        .isInstanceOf(InvalidIdentifierException.class);
  }

  @Test
  void shouldNotEchoInputInErrorMessage() {
    assertThatThrownBy(() -> UnitIdentifier.parse(HOSTILE_INPUT))
        .hasMessageNotContaining(HOSTILE_INPUT);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        TOP_UNIT_OF_UIO,
        A_UNIT_WITHIN_UIO,
        UNIT_AT_UIO,
        "0.0.0.0",
        "999999999.999999999.0.1"
      })
  void shouldAcceptAnyWellFormedIdentifierWithoutReadingStructureIntoIt(String dotted) {
    var identifier = UnitIdentifier.parse(dotted);

    assertThat(identifier).hasToString(dotted);
  }

  @Test
  void shouldEqualAnotherIdentifierWithTheSameValue() {
    var identifier = UnitIdentifier.parse(UNIT_AT_UIO);

    assertThat(identifier).isEqualTo(new UnitIdentifier(UNIT_AT_UIO));
  }
}
