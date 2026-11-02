package no.sikt.nva.uac.organisation;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import no.sikt.nva.uac.shared.InvalidValueException;
import no.sikt.nva.uac.shared.Labels;

/**
 * An organisational unit. Units form trees through {@link #partOf()}; a unit without a parent is a
 * root. Which roots are the top units of institutions is data, not something read from the
 * identifier (ADR 0003).
 *
 * <p>Invariants: every component is present; an acronym, when present, is not blank; a parent, when
 * present, is another unit.
 *
 * @param identifier the public identifier, never null
 * @param name the name in each language it is known in, never null, possibly empty
 * @param acronym the short form of the name, never null, empty when there is none
 * @param partOf the identifier of the parent unit, never null, empty for a root
 */
public record Unit(
    UnitIdentifier identifier,
    Labels name,
    Optional<String> acronym,
    Optional<UnitIdentifier> partOf) {

  public static final String REQUIRED_FORM =
      "A unit has an identifier, a name, an acronym that is not blank when present, and a parent"
          + " other than itself when it has one";

  /**
   * Validates the invariants.
   *
   * @throws InvalidValueException if any invariant is violated
   */
  public Unit {
    if (anyMissing(identifier, name, acronym, partOf)
        || acronym.filter(String::isBlank).isPresent()
        || partOf.filter(identifier::equals).isPresent()) {
      throw new InvalidValueException(REQUIRED_FORM);
    }
  }

  /** Returns true when this unit has no parent. */
  public boolean isRoot() {
    return partOf.isEmpty();
  }

  private static boolean anyMissing(Object... components) {
    return Stream.of(components).anyMatch(Objects::isNull);
  }
}
