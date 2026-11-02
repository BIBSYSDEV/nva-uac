package no.sikt.nva.uac.organisation;

import java.util.Objects;
import java.util.Optional;

/** Implements {@link FindUnit} over a {@link UnitRepository}. */
public final class FindUnitUseCase implements FindUnit {

  private static final String IDENTIFIER_REQUIRED = "An identifier is required to find a unit";

  private final UnitRepository units;

  public FindUnitUseCase(UnitRepository units) {
    this.units = Objects.requireNonNull(units, "A unit repository is required");
  }

  @Override
  public Optional<Unit> byIdentifier(UnitIdentifier identifier) {
    return units.find(Objects.requireNonNull(identifier, IDENTIFIER_REQUIRED));
  }
}
