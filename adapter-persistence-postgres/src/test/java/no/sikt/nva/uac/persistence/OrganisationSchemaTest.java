package no.sikt.nva.uac.persistence;

import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT;
import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT_LABEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The schema must reject what the domain rejects, so the database never holds an invalid unit. */
class OrganisationSchemaTest {

  private static final String TOP_UNIT_OF_UIO = "185.90.0.0";
  private static final String A_UNIT_WITHIN_UIO = "185.0.0.0";
  private static final String FACULTY_AT_UIO = "185.15.0.0";
  private static final String NORWEGIAN_BOKMAAL = "nb";
  private static final String NAME = "Universitetet i Oslo";

  private static MigratedDatabase database;
  private DSLContext sql;

  @BeforeAll
  static void startDatabase() {
    database = MigratedDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @BeforeEach
  void emptyTables() {
    sql = DSL.using(database.dataSource(), SQLDialect.POSTGRES);
    sql.deleteFrom(UNIT).execute();
  }

  @ParameterizedTest
  @ValueSource(strings = {"185", "+185.0.0.0", "007.0.0.0", "185.0.0.0 ", "1234567890.0.0.0"})
  void shouldRejectMalformedIdentifier(String malformed) {
    assertThatThrownBy(() -> insertRoot(malformed)).isInstanceOf(DataAccessException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "\n", " \t\r\n "})
  void shouldRejectBlankAcronym(String blank) {
    assertThatThrownBy(
            () ->
                sql.insertInto(UNIT, UNIT.IDENTIFIER, UNIT.ACRONYM)
                    .values(TOP_UNIT_OF_UIO, blank)
                    .execute())
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void shouldRejectUnitAsItsOwnParent() {
    assertThatThrownBy(() -> insertUnit(FACULTY_AT_UIO, FACULTY_AT_UIO))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void shouldRejectUnknownParent() {
    assertThatThrownBy(() -> insertUnit(FACULTY_AT_UIO, TOP_UNIT_OF_UIO))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void shouldAcceptAnyParentThatExistsWhateverTheIdentifiersLookLike() {
    insertRoot(TOP_UNIT_OF_UIO);
    insertUnit(A_UNIT_WITHIN_UIO, TOP_UNIT_OF_UIO);

    assertThat(sql.fetchCount(UNIT)).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "NB", "en_GB", "nb1", "engl"})
  void shouldRejectMalformedLanguageTag(String malformed) {
    insertRoot(TOP_UNIT_OF_UIO);

    assertThatThrownBy(() -> insertLabel(TOP_UNIT_OF_UIO, malformed, NAME))
        .isInstanceOf(DataAccessException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "\n", " \t\r\n "})
  void shouldRejectBlankLabel(String blank) {
    insertRoot(TOP_UNIT_OF_UIO);

    assertThatThrownBy(() -> insertLabel(TOP_UNIT_OF_UIO, NORWEGIAN_BOKMAAL, blank))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void shouldRejectTwoLabelsInTheSameLanguage() {
    insertRoot(TOP_UNIT_OF_UIO);
    insertLabel(TOP_UNIT_OF_UIO, NORWEGIAN_BOKMAAL, NAME);

    assertThatThrownBy(() -> insertLabel(TOP_UNIT_OF_UIO, NORWEGIAN_BOKMAAL, "Oslo"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void shouldAcceptRootWithUnitAndLabels() {
    insertRoot(TOP_UNIT_OF_UIO);
    insertUnit(FACULTY_AT_UIO, TOP_UNIT_OF_UIO);
    insertLabel(TOP_UNIT_OF_UIO, NORWEGIAN_BOKMAAL, NAME);
    insertLabel(FACULTY_AT_UIO, NORWEGIAN_BOKMAAL, "Det matematisk-naturvitenskapelige fakultet");

    assertThat(sql.fetchCount(UNIT_LABEL)).isEqualTo(2);
  }

  @Test
  void shouldDeleteLabelsWithTheirUnit() {
    insertRoot(TOP_UNIT_OF_UIO);
    insertLabel(TOP_UNIT_OF_UIO, NORWEGIAN_BOKMAAL, NAME);

    sql.deleteFrom(UNIT).where(UNIT.IDENTIFIER.eq(TOP_UNIT_OF_UIO)).execute();

    assertThat(sql.fetchCount(UNIT_LABEL)).isZero();
  }

  private void insertRoot(String identifier) {
    sql.insertInto(UNIT, UNIT.IDENTIFIER).values(identifier).execute();
  }

  private void insertUnit(String identifier, String parent) {
    sql.insertInto(UNIT, UNIT.IDENTIFIER, UNIT.PARENT_IDENTIFIER)
        .values(identifier, parent)
        .execute();
  }

  private void insertLabel(String unit, String language, String text) {
    sql.insertInto(UNIT_LABEL, UNIT_LABEL.UNIT_IDENTIFIER, UNIT_LABEL.LANGUAGE, UNIT_LABEL.TEXT)
        .values(unit, language, text)
        .execute();
  }
}
