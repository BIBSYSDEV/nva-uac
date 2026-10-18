package no.sikt.nva.uac.http.organisation;

import static no.sikt.nva.uac.organisation.InMemoryUnitRepository.holding;
import static no.sikt.nva.uac.organisation.Units.institution;
import static no.sikt.nva.uac.organisation.Units.unitWithin;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.List;
import no.sikt.nva.uac.organisation.FindUnit;
import no.sikt.nva.uac.organisation.FindUnitUseCase;
import no.sikt.nva.uac.organisation.Unit;

/** Wires the organisation use cases over in-memory fixtures for the adapter tests. */
public class OrganisationTestWiring {

  public static final Unit UIO = institution("185.0.0.0", "Universitetet i Oslo");
  public static final Unit FACULTY_AT_UIO =
      unitWithin(UIO, "185.15.0.0", "Det matematisk-naturvitenskapelige fakultet");

  @Produces
  @ApplicationScoped
  public FindUnit findUnit() {
    return new FindUnitUseCase(holding(List.of(UIO, FACULTY_AT_UIO)));
  }
}
