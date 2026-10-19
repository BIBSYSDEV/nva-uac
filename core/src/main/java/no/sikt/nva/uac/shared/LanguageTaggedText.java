package no.sikt.nva.uac.shared;

import java.util.Objects;

/**
 * A piece of text together with the language it is written in: one label of a unit, the name of a
 * position in one language, and so on.
 *
 * @param language the language of the text, never null
 * @param text the text, never null or blank
 */
public record LanguageTaggedText(LanguageTag language, String text) {

  public static final String REQUIRED_FORM =
      "A language-tagged text has a language and a text that is not blank";

  /**
   * Validates the components.
   *
   * @throws InvalidValueException if the language is null or the text is null or blank
   */
  public LanguageTaggedText {
    if (Objects.isNull(language) || Objects.isNull(text) || text.isBlank()) {
      throw new InvalidValueException(REQUIRED_FORM);
    }
  }
}
