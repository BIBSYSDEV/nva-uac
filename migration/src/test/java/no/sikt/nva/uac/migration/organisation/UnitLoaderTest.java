package no.sikt.nva.uac.migration.organisation;

import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT;
import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT_LABEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import no.sikt.nva.uac.persistence.MigratedDatabase;
import no.sikt.nva.uac.shared.InvalidIdentifierException;
import no.sikt.nva.uac.shared.InvalidValueException;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnitLoaderTest {

  private static final String TOP_UNIT_OF_UIO = "185.90.0.0";
  private static final String A_UNIT_WITHIN_UIO = "185.0.0.0";
  private static final String FACULTY = "185.15.0.0";
  private static final String NOT_STAGED = "185.99.0.0";
  private static final String ACRONYM_OF_UIO = "UiO";
  private static final String NORWEGIAN_BOKMAAL = "nb";
  private static final String ENGLISH = "en";
  private static final String TAG_LONGER_THAN_THE_COLUMN = "nb-aaaaaaaa-bbbbbbbb-cccccccc-dddddddd";

  private static MigratedDatabase database;
  private DSLContext sql;
  private UnitLoader loader;

  @BeforeAll
  static void startDatabase() {
    database = MigratedDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @BeforeEach
  void emptyStagingAndTarget() {
    sql = DSL.using(database.dataSource(), SQLDialect.POSTGRES);
    StagedUnits.createIn(sql);
    sql.deleteFrom(StagedUnits.UNIT_NAME).execute();
    sql.deleteFrom(StagedUnits.UNIT).execute();
    sql.deleteFrom(UNIT).execute();
    loader = new UnitLoader(sql);
  }

  @Test
  void shouldRefuseToLoadIntoANonEmptyTarget() {
    sql.insertInto(UNIT, UNIT.IDENTIFIER).values(TOP_UNIT_OF_UIO).execute();

    assertThatThrownBy(loader::load).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectMalformedStagedIdentifier() {
    stageUnit("185.015.0.0", null, null);
    stageUnit(TOP_UNIT_OF_UIO, null, null);

    assertThatThrownBy(loader::load).isInstanceOf(InvalidIdentifierException.class);
  }

  @Test
  void shouldRejectStagedUnitThatIsItsOwnParent() {
    stageUnit(FACULTY, FACULTY, null);

    assertThatThrownBy(loader::load).isInstanceOf(InvalidValueException.class);
  }

  @Test
  void shouldRejectStagedParentThatIsNotStagedItself() {
    stageUnit(FACULTY, NOT_STAGED, null);

    assertThatThrownBy(loader::load)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(UnitLoader.PARENT_NOT_STAGED + ": " + NOT_STAGED);
  }

  @Test
  void shouldRejectACycleOfParents() {
    stageUnit(A_UNIT_WITHIN_UIO, FACULTY, null);
    stageUnit(FACULTY, A_UNIT_WITHIN_UIO, null);

    assertThatThrownBy(loader::load)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(UnitLoader.PARENTS_FORM_A_CYCLE + ": ")
        .hasMessageContaining(FACULTY);
  }

  @Test
  void shouldRejectACycleThatTheStartingUnitIsNotPartOf() {
    stageUnit(TOP_UNIT_OF_UIO, A_UNIT_WITHIN_UIO, null);
    stageUnit(A_UNIT_WITHIN_UIO, FACULTY, null);
    stageUnit(FACULTY, A_UNIT_WITHIN_UIO, null);

    assertThatThrownBy(loader::load)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(UnitLoader.PARENTS_FORM_A_CYCLE + ": ");
  }

  @Test
  void shouldWriteNothingWhenAnyStagedRowIsInvalid() {
    stageUnit(TOP_UNIT_OF_UIO, null, null);
    stageUnit(FACULTY, NOT_STAGED, null);

    var unitsAfterFailedLoad = unitCountAfterFailedLoad();

    assertThat(unitsAfterFailedLoad).isZero();
  }

  @Test
  void shouldFailWhenAStagedNameDoesNotFitItsColumn() {
    stageUnit(TOP_UNIT_OF_UIO, null, null);
    stageName(TOP_UNIT_OF_UIO, TAG_LONGER_THAN_THE_COLUMN, "Universitetet i Oslo");

    assertThatThrownBy(loader::load).isInstanceOf(DataAccessException.class);
  }

  @Test
  void shouldWriteNothingWhenAnInsertFailsAfterOthersSucceeded() {
    stageUnit(TOP_UNIT_OF_UIO, null, null);
    stageUnit(FACULTY, TOP_UNIT_OF_UIO, null);
    stageName(FACULTY, TAG_LONGER_THAN_THE_COLUMN, "Et fakultet");

    var unitsAfterFailedLoad = unitCountAfterFailedLoad();

    assertThat(unitsAfterFailedLoad).isZero();
  }

  @Test
  void shouldLoadParentsBeforeChildrenWhateverTheOrderOrTheIdentifiers() {
    stageUnit(FACULTY, A_UNIT_WITHIN_UIO, null);
    stageUnit(A_UNIT_WITHIN_UIO, TOP_UNIT_OF_UIO, null);
    stageUnit(TOP_UNIT_OF_UIO, null, ACRONYM_OF_UIO);

    loader.load();

    assertThat(loadedParents())
        .hasSize(3)
        .containsEntry(FACULTY, A_UNIT_WITHIN_UIO)
        .containsEntry(A_UNIT_WITHIN_UIO, TOP_UNIT_OF_UIO)
        .containsEntry(TOP_UNIT_OF_UIO, null);
  }

  @Test
  void shouldLoadTheAcronymWhereThereIsOne() {
    stageUnit(TOP_UNIT_OF_UIO, null, ACRONYM_OF_UIO);
    stageUnit(FACULTY, TOP_UNIT_OF_UIO, null);

    loader.load();

    assertThat(loadedAcronyms())
        .containsEntry(TOP_UNIT_OF_UIO, ACRONYM_OF_UIO)
        .containsEntry(FACULTY, null);
  }

  @Test
  void shouldLoadEveryStagedNameInItsLanguage() {
    stageUnit(TOP_UNIT_OF_UIO, null, ACRONYM_OF_UIO);
    stageName(TOP_UNIT_OF_UIO, NORWEGIAN_BOKMAAL, "Universitetet i Oslo");
    stageName(TOP_UNIT_OF_UIO, ENGLISH, "University of Oslo");

    loader.load();

    assertThat(loadedNamesOf(TOP_UNIT_OF_UIO))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(NORWEGIAN_BOKMAAL, "Universitetet i Oslo", ENGLISH, "University of Oslo"));
  }

  private int unitCountAfterFailedLoad() {
    try {
      loader.load();
    } catch (IllegalStateException | DataAccessException expected) {
      return sql.fetchCount(UNIT);
    }
    throw new AssertionError("The load should have failed");
  }

  private Map<String, String> loadedParents() {
    return sql.select(UNIT.IDENTIFIER, UNIT.PARENT_IDENTIFIER)
        .from(UNIT)
        .fetchMap(UNIT.IDENTIFIER, UNIT.PARENT_IDENTIFIER);
  }

  private Map<String, String> loadedAcronyms() {
    return sql.select(UNIT.IDENTIFIER, UNIT.ACRONYM)
        .from(UNIT)
        .fetchMap(UNIT.IDENTIFIER, UNIT.ACRONYM);
  }

  private Map<String, String> loadedNamesOf(String identifier) {
    return sql.select(UNIT_LABEL.LANGUAGE, UNIT_LABEL.TEXT)
        .from(UNIT_LABEL)
        .where(UNIT_LABEL.UNIT_IDENTIFIER.eq(identifier))
        .fetchMap(UNIT_LABEL.LANGUAGE, UNIT_LABEL.TEXT);
  }

  private void stageUnit(String identifier, String parent, String acronym) {
    sql.insertInto(
            StagedUnits.UNIT,
            StagedUnits.IDENTIFIER,
            StagedUnits.PARENT_IDENTIFIER,
            StagedUnits.ACRONYM)
        .values(identifier, parent, acronym)
        .execute();
  }

  private void stageName(String identifier, String language, String text) {
    sql.insertInto(
            StagedUnits.UNIT_NAME, StagedUnits.IDENTIFIER, StagedUnits.LANGUAGE, StagedUnits.TEXT)
        .values(identifier, language, text)
        .execute();
  }
}
