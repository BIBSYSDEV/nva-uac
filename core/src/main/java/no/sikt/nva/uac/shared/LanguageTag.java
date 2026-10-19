package no.sikt.nva.uac.shared;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A language tag in the form of RFC 5646 (BCP 47), such as {@code nb}, {@code en-GB} or {@code
 * zh-Hant}. Tags are case-insensitive by the standard; this type normalises them to lower case so
 * that equal tags are equal values.
 *
 * <p>Only the form is checked: a primary language subtag of two or three letters, optionally
 * followed by subtags of two to eight letters or digits, each introduced by a hyphen. Whether a tag
 * is registered is not checked.
 */
public record LanguageTag(String value) {

  public static final String REQUIRED_FORM =
      "A language tag is a two- or three-letter language code, optionally followed by"
          + " hyphen-separated subtags of two to eight letters or digits";

  private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*");

  /**
   * Normalises and validates the tag.
   *
   * @throws InvalidValueException if {@code value} is null or not well-formed
   */
  public LanguageTag {
    if (Objects.isNull(value) || !WELL_FORMED.matcher(value).matches()) {
      throw new InvalidValueException(REQUIRED_FORM);
    }
    value = value.toLowerCase(Locale.ROOT);
  }

  @Override
  public String toString() {
    return value;
  }
}
