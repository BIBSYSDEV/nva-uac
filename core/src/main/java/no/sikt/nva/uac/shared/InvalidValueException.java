package no.sikt.nva.uac.shared;

/**
 * Signals that a value offered to a value object does not satisfy that object's invariant.
 *
 * <p>The message describes the invariant and never repeats the offending input, so it is safe to
 * pass on to a caller or a log.
 */
public final class InvalidValueException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidValueException(String invariant) {
    super(invariant);
  }
}
