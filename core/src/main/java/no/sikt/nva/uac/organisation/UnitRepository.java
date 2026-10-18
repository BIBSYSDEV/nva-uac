package no.sikt.nva.uac.organisation;

import java.util.Optional;

/**
 * Driven port: the store of organisational units.
 *
 * <p>Implementations are expected to be safe for concurrent use and to return consistent snapshots:
 * a unit is returned whole or not at all.
 */
public interface UnitRepository {

  /**
   * Reads the unit with the given identifier.
   *
   * @param identifier the identifier, never null
   * @return the unit, or empty when the store holds no unit with that identifier
   */
  Optional<Unit> find(UnitIdentifier identifier);
}
