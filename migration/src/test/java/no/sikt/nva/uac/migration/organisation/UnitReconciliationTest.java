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

  private static final String UIO = "185.90.0.0";
  private static final String DISOWNED_UNIT = "185.0.0.0";
  private static final String FACULTY = "185.15.0.0";
  private static final String UNSTAGED_PARENT = "185.99.0.0";
  private static final String NAME_OF_UIO = "Universitetet i Oslo";
  private static final String LEADING_ZERO = "185.015.0.0";
  private static final String WITH_LETTERS = "185.0.0.0x";
  private static final String MASKED = "185.0.0.0?";

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
    sql.deleteFrom(StagedUnits.UNIT_PART).execute();
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
    stageName(UIO, NAME_OF_UIO);

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
  void shouldListNamesWithoutAUnit() {
    stageName(UIO, NAME_OF_UIO);

    var report = reconciliation.report();

    assertThat(report.namesWithoutUnit()).containsExactly(UIO);
  }

  @Test
  void shouldListMalformedIdentifiers() {
    stageUnit(LEADING_ZERO, null);
    stageUnit(UIO, null);

    var report = reconciliation.report();

    assertThat(report.malformedIdentifiers()).containsExactly(LEADING_ZERO);
  }

  @Test
  void shouldMaskWhatIsNotADigitOrADotInAMalformedIdentifier() {
    stageUnit(WITH_LETTERS, null);

    var report = reconciliation.report();

    assertThat(report.malformedIdentifiers()).containsExactly(MASKED);
  }

  @Test
  void shouldListChildrenWhoseParentDoesNotListThem() {
    stageUnit(UIO, null);
    stageUnit(DISOWNED_UNIT, UIO);
    stageUnit(FACULTY, UIO);
    stagePart(UIO, FACULTY);

    var report = reconciliation.report();

    assertThat(report.childrenNotListedByParent()).containsExactly(DISOWNED_UNIT);
  }

  @Test
  void shouldListListedChildrenThatClaimAnotherParentOrNone() {
    stageUnit(UIO, null);
    stageUnit(FACULTY, UNSTAGED_PARENT);
    stageUnit(DISOWNED_UNIT, null);
    stagePart(UIO, FACULTY);
    stagePart(UIO, DISOWNED_UNIT);

    var report = reconciliation.report();

    assertThat(report.listedChildrenClaimingAnotherParent())
        .containsExactlyInAnyOrder(UIO + " -> " + FACULTY, UIO + " -> " + DISOWNED_UNIT);
  }

  @Test
  void shouldNotBeCleanWhenAChildIsNotListedByItsParent() {
    stageUnit(UIO, null);
    stageName(UIO, NAME_OF_UIO);
    stageUnit(FACULTY, UIO);
    stageName(FACULTY, "Det matematisk-naturvitenskapelige fakultet");
    stagePart(UIO, FACULTY);
    stageUnit(DISOWNED_UNIT, UIO);
    stageName(DISOWNED_UNIT, "En enhet");
    new UnitLoader(sql).load();

    var report = reconciliation.report();

    assertThat(report.isClean()).isFalse();
  }

  @Test
  void shouldNotBeCleanWhenAListingIsNotClaimedByTheChild() {
    stageUnit(UIO, null);
    stageName(UIO, NAME_OF_UIO);
    stageUnit(FACULTY, UIO);
    stageName(FACULTY, "Det matematisk-naturvitenskapelige fakultet");
    stagePart(UIO, FACULTY);
    stagePart(UIO, DISOWNED_UNIT);
    new UnitLoader(sql).load();

    var report = reconciliation.report();

    assertThat(report.isClean()).isFalse();
  }

  @Test
  void shouldBeCleanAfterACompleteLoad() {
    stageUnit(UIO, null);
    stageName(UIO, NAME_OF_UIO);
    stageUnit(FACULTY, UIO);
    stageName(FACULTY, "Det matematisk-naturvitenskapelige fakultet");
    stagePart(UIO, FACULTY);
    new UnitLoader(sql).load();

    var report = reconciliation.report();

    assertThat(report.isClean()).isTrue();
  }

  @Test
  void shouldRenderCountsAndFindingsAsMarkdown() {
    stageUnit(FACULTY, UNSTAGED_PARENT);
    stagePart(UIO, DISOWNED_UNIT);

    var markdown = reconciliation.report().asMarkdown();

    assertThat(markdown)
        .contains("| Units | 1 | 0 |")
        .contains("Parents not staged: 1 (" + UNSTAGED_PARENT + ")")
        .contains("Children their parent does not list: 1 (" + FACULTY + ")")
        .contains(
            "Listed children claiming another parent: 1 (" + UIO + " -> " + DISOWNED_UNIT + ")")
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

  private void stagePart(String parent, String child) {
    sql.insertInto(StagedUnits.UNIT_PART, StagedUnits.LISTED_PARENT, StagedUnits.LISTED_CHILD)
        .values(parent, child)
        .execute();
  }
}
