package no.sikt.nva.uac.organisation;

import java.util.Optional;

/**
 * Driving port: look up one organisational unit by its public identifier.
 *
 * <p>Parsing and validating the textual identifier is the caller's responsibility; see {@link
 * UnitIdentifier#parse(String)}.
 */
public interface FindUnit {

  /**
   * Finds the unit with the given identifier.
   *
   * @param identifier the identifier, never null
   * @return the unit, or empty when no unit has that identifier
   * @throws NullPointerException if {@code identifier} is null
   */
  Optional<Unit> byIdentifier(UnitIdentifier identifier);
}
