package no.sikt.nva.uac.migration.organisation;

import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import no.sikt.nva.uac.persistence.MigratedDatabase;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnitReconciliationTest {

  private static final String UIO = "185.0.0.0";
  private static final String FACULTY = "185.15.0.0";
  private static final String UNSTAGED_PARENT = "185.99.0.0";

  private static MigratedDatabase database;
  private DSLContext sql;
  private UnitReconciliation reconciliation;

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
    reconciliation = new UnitReconciliation(sql);
  }

  @Test
  void shouldRejectMissingContext() {
    DSLContext missing = null;

    assertThatThrownBy(() -> new UnitReconciliation(missing))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldBeCleanWhenNothingIsStagedOrLoaded() {
    var report = reconciliation.report();

    assertThat(report.isClean()).isTrue();
  }

  @Test
  void shouldListStagedUnitsThatWereNotLoaded() {
    stageUnit(UIO, null);
    stageName(UIO, "Universitetet i Oslo");

    var report = reconciliation.report();

    assertThat(report.unitsNotLoaded()).containsExactly(UIO);
  }

  @Test
  void shouldListParentsThatAreNotStaged() {
    stageUnit(FACULTY, UNSTAGED_PARENT);
    stageName(FACULTY, "Et fakultet");

    var report = reconciliation.report();

    assertThat(report.parentsNotStaged()).containsExactly(UNSTAGED_PARENT);
  }

  @Test
  void shouldListUnitsWithoutAnyName() {
    stageUnit(UIO, null);

    var report = reconciliation.report();

    assertThat(report.unitsWithoutName()).containsExactly(UIO);
  }

  @Test
  void shouldBeCleanAfterACompleteLoad() {
    stageUnit(UIO, null);
    stageName(UIO, "Universitetet i Oslo");
    stageUnit(FACULTY, UIO);
    stageName(FACULTY, "Det matematisk-naturvitenskapelige fakultet");
    new UnitLoader(sql).load();

    var report = reconciliation.report();

    assertThat(report.isClean()).isTrue();
  }

  @Test
  void shouldRenderCountsAndFindingsAsMarkdown() {
    stageUnit(FACULTY, UNSTAGED_PARENT);

    var markdown = reconciliation.report().asMarkdown();

    assertThat(markdown)
        .contains("| Units | 1 | 0 |")
        .contains("Parents not staged: 1 (" + UNSTAGED_PARENT + ")")
        .contains("Not clean.");
  }

  private void stageUnit(String identifier, String parent) {
    sql.insertInto(StagedUnits.UNIT, StagedUnits.IDENTIFIER, StagedUnits.PARENT_IDENTIFIER)
        .values(identifier, parent)
        .execute();
  }

  private void stageName(String identifier, String text) {
    sql.insertInto(
            StagedUnits.UNIT_NAME, StagedUnits.IDENTIFIER, StagedUnits.LANGUAGE, StagedUnits.TEXT)
        .values(identifier, "nb", text)
        .execute();
  }
}
