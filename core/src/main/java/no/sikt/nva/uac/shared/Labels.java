package no.sikt.nva.uac.shared;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The labels of one thing in several languages: at most one text per language. Immutable.
 *
 * @param byLanguage the texts keyed by language; copied on construction, never null
 */
public record Labels(Map<LanguageTag, String> byLanguage) {

  public static final String REQUIRED_FORM =
      "Labels are language-tagged texts with at most one text per language";

  private static final Labels NONE = new Labels(Map.of());

  /**
   * Copies the texts into an unmodifiable map.
   *
   * @throws InvalidValueException if the map is null
   */
  public Labels {
    if (Objects.isNull(byLanguage)) {
      throw new InvalidValueException(REQUIRED_FORM);
    }
    byLanguage = Map.copyOf(byLanguage);
  }

  /**
   * Collects language-tagged texts into labels.
   *
   * @param texts the texts, in any order; may be empty
   * @return the labels
   * @throws InvalidValueException if {@code texts} is null, contains null, or tags the same
   *     language more than once
   */
  public static Labels from(Collection<LanguageTaggedText> texts) {
    if (Objects.isNull(texts)
        || texts.stream().anyMatch(Objects::isNull)
        || hasDuplicateLanguage(texts)) {
      throw new InvalidValueException(REQUIRED_FORM);
    }
    return new Labels(
        texts.stream()
            .collect(Collectors.toMap(LanguageTaggedText::language, LanguageTaggedText::text)));
  }

  /** Returns labels in no language at all. */
  public static Labels none() {
    return NONE;
  }

  /** Returns the text in the given language, or empty when there is none. */
  public Optional<String> inLanguage(LanguageTag language) {
    return Optional.ofNullable(byLanguage.get(language));
  }

  /** Returns true when there is no text in any language. */
  public boolean isEmpty() {
    return byLanguage.isEmpty();
  }

  private static boolean hasDuplicateLanguage(Collection<LanguageTaggedText> texts) {
    return texts.stream().map(LanguageTaggedText::language).distinct().count() != texts.size();
  }

  /** Returns a function, for use in streams, that reads the text in the given language. */
  public static Function<Labels, Optional<String>> textIn(LanguageTag language) {
    return labels -> labels.inLanguage(language);
  }
}
