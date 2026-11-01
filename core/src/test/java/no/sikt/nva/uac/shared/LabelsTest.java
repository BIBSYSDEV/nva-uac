package no.sikt.nva.uac.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LabelsTest {

  private static final LanguageTag ENGLISH = new LanguageTag("en");
  private static final LanguageTag NORWEGIAN_BOKMAAL = new LanguageTag("nb");
  private static final LanguageTag NORWEGIAN_NYNORSK = new LanguageTag("nn");
  private static final LanguageTaggedText ENGLISH_NAME =
      new LanguageTaggedText(ENGLISH, "University of Oslo");
  private static final LanguageTaggedText BOKMAAL_NAME =
      new LanguageTaggedText(NORWEGIAN_BOKMAAL, "Universitetet i Oslo");

  @Test
  void shouldRejectMissingCollection() {
    List<LanguageTaggedText> missing = null;

    assertThatThrownBy(() -> Labels.from(missing)).isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldRejectMissingMap() {
    Map<LanguageTag, String> missing = null;

    assertThatThrownBy(() -> new Labels(missing)).isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldRejectMissingText() {
    var texts = Arrays.asList(ENGLISH_NAME, null);

    assertThatThrownBy(() -> Labels.from(texts)).isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldRejectTwoTextsInTheSameLanguage() {
    var texts = List.of(ENGLISH_NAME, new LanguageTaggedText(ENGLISH, "Oslo University"));

    assertThatThrownBy(() -> Labels.from(texts)).isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldReturnEmptyForLanguageWithoutText() {
    var labels = Labels.from(List.of(ENGLISH_NAME, BOKMAAL_NAME));

    assertThat(labels.inLanguage(NORWEGIAN_NYNORSK)).isEmpty();
  }

  @Test
  void shouldReturnTextInRequestedLanguage() {
    var labels = Labels.from(List.of(ENGLISH_NAME, BOKMAAL_NAME));

    assertThat(labels.inLanguage(NORWEGIAN_BOKMAAL)).contains(BOKMAAL_NAME.text());
  }

  @Test
  void shouldReadTextThroughStreamFunction() {
    var labels = Labels.from(List.of(ENGLISH_NAME, BOKMAAL_NAME));

    assertThat(Labels.textIn(ENGLISH).apply(labels)).contains(ENGLISH_NAME.text());
  }

  @Test
  void shouldBeEmptyWhenNoTextsGiven() {
    var labels = Labels.from(List.of());

    assertThat(labels.isEmpty()).isTrue();
  }

  @Test
  void shouldEqualNoneWhenEmpty() {
    var labels = Labels.from(List.of());

    assertThat(labels).isEqualTo(Labels.none());
  }

  @Test
  void shouldNotExposeModifiableMap() {
    var labels = Labels.from(List.of(ENGLISH_NAME));

    assertThatThrownBy(() -> labels.byLanguage().put(NORWEGIAN_NYNORSK, "Universitetet i Oslo"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
