package no.sikt.nva.uac.organisation;

import static no.sikt.nva.uac.organisation.InMemoryUnitRepository.holding;
import static no.sikt.nva.uac.organisation.Units.institution;
import static no.sikt.nva.uac.organisation.Units.unitWithin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class FindUnitUseCaseTest {

  private static final Unit UIO = institution("185.0.0.0", "Universitetet i Oslo");
  private static final Unit FACULTY_AT_UIO =
      unitWithin(UIO, "185.15.0.0", "Det matematisk-naturvitenskapelige fakultet");
  private static final UnitIdentifier UNKNOWN = UnitIdentifier.parse("185.99.0.0");

  private final FindUnit findUnit = new FindUnitUseCase(holding(List.of(UIO, FACULTY_AT_UIO)));

  @Test
  void shouldRejectMissingRepository() {
    UnitRepository missing = null;

    assertThatThrownBy(() -> new FindUnitUseCase(missing)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectMissingIdentifier() {
    UnitIdentifier missing = null;

    assertThatThrownBy(() -> findUnit.byIdentifier(missing))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldReturnEmptyWhenUnitIsUnknown() {
    var found = findUnit.byIdentifier(UNKNOWN);

    assertThat(found).isEmpty();
  }

  @Test
  void shouldReturnUnitWhenKnown() {
    var found = findUnit.byIdentifier(FACULTY_AT_UIO.identifier());

    assertThat(found).contains(FACULTY_AT_UIO);
  }
}
