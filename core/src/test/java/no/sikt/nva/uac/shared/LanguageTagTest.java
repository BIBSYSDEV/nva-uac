package no.sikt.nva.uac.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageTagTest {

  @Test
  void shouldRejectNull() {
    String missing = null;

    assertThatThrownBy(() -> new LanguageTag(missing)).isInstanceOf(InvalidValueException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", " ", "e", "engl", "en_GB", "en-", "-en", "en--GB", "en GB", "en;q=0.9", "nb1"})
  void shouldRejectMalformedTag(String malformed) {
    assertThatThrownBy(() -> new LanguageTag(malformed)).isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldNotEchoInputInErrorMessage() {
    var hostile = "<b>";

    assertThatThrownBy(() -> new LanguageTag(hostile)).hasMessageNotContaining(hostile);
  }

  @ParameterizedTest
  @ValueSource(strings = {"en", "nb", "nn", "sme", "en-GB", "zh-Hant", "de-CH-1996"})
  void shouldAcceptWellFormedTag(String wellFormed) {
    var tag = new LanguageTag(wellFormed);

    assertThat(tag.value()).isEqualToIgnoringCase(wellFormed);
  }

  @Test
  void shouldRenderNormalisedTag() {
    var tag = new LanguageTag("EN-GB");

    assertThat(tag).hasToString("en-gb");
  }

  @Test
  void shouldTreatDifferentlyCasedTagsAsEqual() {
    var upper = new LanguageTag("EN-GB");

    assertThat(upper).isEqualTo(new LanguageTag("en-gb"));
  }
}
