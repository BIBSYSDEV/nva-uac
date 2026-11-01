package no.sikt.nva.uac.persistence.organisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.stream.Stream;
import no.sikt.nva.uac.organisation.Unit;
import no.sikt.nva.uac.organisation.UnitIdentifier;
import no.sikt.nva.uac.persistence.MigratedDatabase;
import no.sikt.nva.uac.shared.LanguageTag;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostgresUnitRepositoryTest {

  private static final String UIO = "185.0.0.0";
  private static final String FACULTY = "185.15.0.0";
  private static final String DEPARTMENT = "185.15.2.0";
  private static final String UNKNOWN = "185.99.0.0";
  private static final LanguageTag ENGLISH = new LanguageTag("en");
  private static final LanguageTag NORWEGIAN_BOKMAAL = new LanguageTag("nb");

  private static MigratedDatabase database;
  private PostgresUnitRepository repository;

  @BeforeAll
  static void startDatabase() {
    database = MigratedDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @BeforeEach
  void seedHierarchy() {
    DSLContext sql = DSL.using(database.dataSource(), SQLDialect.POSTGRES);
    var rows = new UnitRows(sql);
    repository = new PostgresUnitRepository(sql);
    rows.deleteAll();
    rows.institution(UIO, "UiO");
    rows.label(UIO, "nb", "Universitetet i Oslo");
    rows.label(UIO, "en", "University of Oslo");
    rows.unit(FACULTY, UIO);
    rows.label(FACULTY, "nb", "Det matematisk-naturvitenskapelige fakultet");
    rows.unit(DEPARTMENT, FACULTY);
  }

  @Test
  void shouldRejectMissingContext() {
    DSLContext missing = null;

    assertThatThrownBy(() -> new PostgresUnitRepository(missing))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldReturnEmptyWhenUnitIsUnknown() {
    var found = repository.find(UnitIdentifier.parse(UNKNOWN));

    assertThat(found).isEmpty();
  }

  @Test
  void shouldReturnInstitutionWithoutParent() {
    var found = repository.find(UnitIdentifier.parse(UIO));

    assertThat(found.flatMap(Unit::partOf)).isEmpty();
  }

  @Test
  void shouldReturnAcronymWhenPresent() {
    var found = repository.find(UnitIdentifier.parse(UIO));

    assertThat(found.flatMap(Unit::acronym)).contains("UiO");
  }

  @Test
  void shouldReturnLabelsInEveryLanguage() {
    var found = repository.find(UnitIdentifier.parse(UIO)).orElseThrow();

    assertThat(found.name().byLanguage())
        .containsOnlyKeys(ENGLISH, NORWEGIAN_BOKMAAL)
        .containsEntry(ENGLISH, "University of Oslo");
  }

  @Test
  void shouldReturnUnitWithItsParent() {
    var found = repository.find(UnitIdentifier.parse(FACULTY));

    assertThat(found.flatMap(Unit::partOf)).contains(UnitIdentifier.parse(UIO));
  }

  @Test
  void shouldReturnNoLabelsForUnitWithoutAny() {
    var found = repository.find(UnitIdentifier.parse(DEPARTMENT)).orElseThrow();

    assertThat(found.name().isEmpty()).isTrue();
  }

  @Test
  void shouldResolveTheParentChainUpToTheInstitution() {
    var department = repository.find(UnitIdentifier.parse(DEPARTMENT)).orElseThrow();

    var chain =
        Stream.iterate(Optional.of(department), Optional::isPresent, this::parentOf)
            .map(Optional::orElseThrow)
            .map(Unit::identifier)
            .map(UnitIdentifier::toString)
            .toList();

    assertThat(chain).containsExactly(DEPARTMENT, FACULTY, UIO);
  }

  private Optional<Unit> parentOf(Optional<Unit> unit) {
    return unit.flatMap(Unit::partOf).flatMap(repository::find);
  }
}
