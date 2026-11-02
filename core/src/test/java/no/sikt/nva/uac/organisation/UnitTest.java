package no.sikt.nva.uac.organisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import no.sikt.nva.uac.shared.InvalidValueException;
import no.sikt.nva.uac.shared.Labels;
import no.sikt.nva.uac.shared.LanguageTag;
import no.sikt.nva.uac.shared.LanguageTaggedText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UnitTest {

  private static final UnitIdentifier TOP_UNIT_OF_UIO = UnitIdentifier.parse("185.90.0.0");
  private static final UnitIdentifier A_UNIT_WITHIN_UIO = UnitIdentifier.parse("185.0.0.0");
  private static final UnitIdentifier FACULTY_AT_UIO = UnitIdentifier.parse("185.15.0.0");
  private static final Labels NAME =
      Labels.from(List.of(new LanguageTaggedText(new LanguageTag("nb"), "Universitetet i Oslo")));
  private static final Optional<String> ACRONYM = Optional.of("UiO");
  private static final Optional<String> NO_ACRONYM = Optional.empty();
  private static final Optional<UnitIdentifier> NO_PARENT = Optional.empty();

  @Test
  void shouldRejectMissingIdentifier() {
    UnitIdentifier missing = null;

    assertThatThrownBy(() -> new Unit(missing, NAME, ACRONYM, NO_PARENT))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldRejectMissingName() {
    Labels missing = null;

    assertThatThrownBy(() -> new Unit(TOP_UNIT_OF_UIO, missing, ACRONYM, NO_PARENT))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldRejectMissingAcronymContainer() {
    Optional<String> missing = null;

    assertThatThrownBy(() -> new Unit(TOP_UNIT_OF_UIO, NAME, missing, NO_PARENT))
        .isInstanceOf(InvalidValueException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void shouldRejectBlankAcronym(String blank) {
    assertThatThrownBy(() -> new Unit(TOP_UNIT_OF_UIO, NAME, Optional.of(blank), NO_PARENT))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldRejectMissingParentContainer() {
    Optional<UnitIdentifier> missing = null;

    assertThatThrownBy(() -> new Unit(TOP_UNIT_OF_UIO, NAME, ACRONYM, missing))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldRejectUnitAsItsOwnParent() {
    var itself = Optional.of(FACULTY_AT_UIO);

    assertThatThrownBy(() -> new Unit(FACULTY_AT_UIO, NAME, NO_ACRONYM, itself))
        .isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldAcceptRootWhateverItsIdentifierLooksLike() {
    var root = new Unit(TOP_UNIT_OF_UIO, NAME, ACRONYM, NO_PARENT);

    assertThat(root.isRoot()).isTrue();
  }

  @Test
  void shouldAcceptParentWhateverTheIdentifiersLookLike() {
    var unit = new Unit(A_UNIT_WITHIN_UIO, NAME, ACRONYM, Optional.of(TOP_UNIT_OF_UIO));

    assertThat(unit.isRoot()).isFalse();
  }

  @Test
  void shouldAcceptUnitWithEmptyName() {
    var faculty = new Unit(FACULTY_AT_UIO, Labels.none(), NO_ACRONYM, Optional.of(TOP_UNIT_OF_UIO));

    assertThat(faculty.name().isEmpty()).isTrue();
  }
}
