package no.sikt.nva.uac.shared;

/**
 * Signals that a string offered as an identifier does not have the form the identifier type
 * requires.
 *
 * <p>The message describes the required form and never repeats the offending input, so it is safe
 * to pass on to a caller or a log.
 */
public final class InvalidIdentifierException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidIdentifierException(String requiredForm) {
    super(requiredForm);
  }
}
