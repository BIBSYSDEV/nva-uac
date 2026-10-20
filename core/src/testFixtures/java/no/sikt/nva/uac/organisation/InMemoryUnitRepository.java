package no.sikt.nva.uac.organisation;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A {@link UnitRepository} over a fixed collection of units, for tests of the core. */
public final class InMemoryUnitRepository implements UnitRepository {

  private final Map<UnitIdentifier, Unit> byIdentifier;

  private InMemoryUnitRepository(Map<UnitIdentifier, Unit> byIdentifier) {
    this.byIdentifier = byIdentifier;
  }

  /**
   * Builds a repository holding exactly the given units.
   *
   * @throws IllegalStateException if two units share an identifier
   */
  public static InMemoryUnitRepository holding(Collection<Unit> units) {
    return new InMemoryUnitRepository(
        units.stream()
            .collect(Collectors.toUnmodifiableMap(Unit::identifier, Function.identity())));
  }

  @Override
  public Optional<Unit> find(UnitIdentifier identifier) {
    return Optional.ofNullable(byIdentifier.get(identifier));
  }
}
