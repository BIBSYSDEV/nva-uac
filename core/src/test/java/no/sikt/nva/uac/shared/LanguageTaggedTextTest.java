package no.sikt.nva.uac.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageTaggedTextTest {

  private static final LanguageTag NORWEGIAN_BOKMAAL = new LanguageTag("nb");
  private static final String NAME = "Universitetet i Oslo";

  @Test
  void shouldRejectMissingLanguage() {
    LanguageTag missing = null;

    assertThatThrownBy(() -> new LanguageTaggedText(missing, NAME))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldRejectMissingText() {
    String missing = null;

    assertThatThrownBy(() -> new LanguageTaggedText(NORWEGIAN_BOKMAAL, missing))
        .isInstanceOf(InvalidValueException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "\n"})
  void shouldRejectBlankText(String blank) {
    assertThatThrownBy(() -> new LanguageTaggedText(NORWEGIAN_BOKMAAL, blank))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldKeepTextAsGiven() {
    var text = new LanguageTaggedText(NORWEGIAN_BOKMAAL, NAME);

    assertThat(text.text()).isEqualTo(NAME);
  }
}
