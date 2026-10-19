package no.sikt.nva.uac.migration.organisation;

import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import no.sikt.nva.uac.persistence.MigratedDatabase;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoadUnitsTest {

  private static final String UIO = "185.90.0.0";
  private static final String FACULTY = "185.15.0.0";

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
  void emptyStagingAndTarget() {
    sql = DSL.using(database.dataSource(), SQLDialect.POSTGRES);
    StagedUnits.createIn(sql);
    sql.deleteFrom(StagedUnits.UNIT_NAME).execute();
    sql.deleteFrom(StagedUnits.UNIT_PART).execute();
    sql.deleteFrom(StagedUnits.UNIT).execute();
    sql.deleteFrom(UNIT).execute();
  }

  @Test
  void shouldRejectWrongNumberOfArguments() {
    var tooFew = new String[] {"jdbc:postgresql://localhost/nvauac"};

    assertThatThrownBy(() -> LoadUnits.main(tooFew)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldFailWhenTheRehearsalIsNotClean() {
    stageUnit(UIO, null);
    var discarded = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> LoadUnits.run(sql, discarded))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldPrintTheReport() {
    stageUnit(UIO, null);
    stageName(UIO, "Universitetet i Oslo");
    var output = new ByteArrayOutputStream();

    LoadUnits.run(sql, new PrintStream(output, true, StandardCharsets.UTF_8));

    assertThat(output.toString(StandardCharsets.UTF_8)).contains("Loaded 1 units.", "Clean.");
  }

  @Test
  void shouldLoadThroughTheCommandLineEntryPoint() {
    stageUnit(UIO, null);
    stageName(UIO, "Universitetet i Oslo");
    stageUnit(FACULTY, UIO);
    stageName(FACULTY, "Det matematisk-naturvitenskapelige fakultet");
    stagePart(UIO, FACULTY);

    LoadUnits.main(database.connectionArguments());

    assertThat(sql.fetchCount(UNIT)).isEqualTo(2);
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
