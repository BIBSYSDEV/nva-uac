package no.sikt.nva.uac.organisation;

import java.util.Objects;
import java.util.regex.Pattern;
import no.sikt.nva.uac.shared.InvalidIdentifierException;

/**
 * The public identifier of an organisational unit, inherited from Cristin: four dot-separated
 * numbers such as {@code 185.15.2.10}.
 *
 * <p>The segments carry no meaning. The hierarchy of units is data, the {@code partOf} relation,
 * and the institution a unit belongs to is data too. In Cristin the top unit of the University of
 * Oslo is {@code 185.90.0.0}, and {@code 185.0.0.0} is an ordinary unit within it, so neither "ends
 * in zeros" nor "shares the first number" may be read as structure.
 *
 * <p>Instances are immutable value objects. The form is strict: four numbers without signs, spaces
 * or leading zeros.
 *
 * @param value the identifier in its dotted form
 */
public record UnitIdentifier(String value) {

  public static final String REQUIRED_FORM =
      "A unit identifier is four dot-separated numbers without signs, spaces or leading zeros";

  private static final String NUMBER = "(0|[1-9]\\d{0,8})";
  private static final String SEPARATOR = "\\.";
  private static final Pattern DOTTED_FORM =
      Pattern.compile(NUMBER + SEPARATOR + NUMBER + SEPARATOR + NUMBER + SEPARATOR + NUMBER);

  /**
   * Validates the form.
   *
   * @throws InvalidIdentifierException if {@code value} is null or not in the required form
   */
  public UnitIdentifier {
    if (isMalformed(value)) {
      throw new InvalidIdentifierException(REQUIRED_FORM);
    }
  }

  /**
   * Tells whether a value has the required form, without constructing an identifier.
   *
   * @param value the candidate, which may be null
   * @return true if {@link #parse(String)} would accept the value
   */
  public static boolean isWellFormed(String value) {
    return Objects.nonNull(value) && DOTTED_FORM.matcher(value).matches();
  }

  private static boolean isMalformed(String value) {
    return !isWellFormed(value);
  }

  /**
   * Parses the strict dotted form.
   *
   * @param value the candidate identifier, which may be null
   * @return the identifier
   * @throws InvalidIdentifierException if {@code value} is null or not in the required form
   */
  public static UnitIdentifier parse(String value) {
    return new UnitIdentifier(value);
  }

  /** Returns the dotted form, which {@link #parse(String)} accepts. */
  @Override
  public String toString() {
    return value;
  }
}
