package no.sikt.nva.uac.migration.organisation;

import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
  private static final String UNSTAGED_PARENT = "185.99.0.0";
  private static final String NAME_OF_UIO = "Universitetet i Oslo";
  private static final PrintStream DISCARDED =
      new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);

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
  void shouldFailWhenAConnectionVariableIsMissing() {
    Map<String, String> environment = Map.of(LoadUnits.URL_VARIABLE, "jdbc:postgresql://x/y");

    assertThatThrownBy(() -> LoadUnits.connectAndRun(environment, DISCARDED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(LoadUnits.MISSING_VARIABLE + ": " + LoadUnits.USER_VARIABLE);
  }

  @Test
  void shouldFailWhenAConnectionVariableIsBlank() {
    var environment = connectionEnvironment();
    environment.put(LoadUnits.PASSWORD_VARIABLE, " ");

    assertThatThrownBy(() -> LoadUnits.connectAndRun(environment, DISCARDED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(LoadUnits.MISSING_VARIABLE + ": " + LoadUnits.PASSWORD_VARIABLE);
  }

  @Test
  void shouldRefuseArgumentsOnTheCommandLine() {
    var anArgument = new String[] {"jdbc:postgresql://x/y"};

    assertThatThrownBy(() -> LoadUnits.main(anArgument))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(LoadUnits.NO_ARGUMENTS);
  }

  @Test
  void shouldReadTheConnectionFromTheProcessEnvironment() {
    var noArguments = new String[0];

    assertThatThrownBy(() -> LoadUnits.main(noArguments))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldFailWhenTheRehearsalIsNotClean() {
    stageUnit(UIO, null);
    stageName(UIO, NAME_OF_UIO);
    stagePart(UIO, FACULTY);

    assertThatThrownBy(() -> LoadUnits.run(sql, DISCARDED))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(LoadUnits.NOT_CLEAN);
  }

  @Test
  void shouldPrintTheReportBeforeALoadThatFails() {
    stageUnit(FACULTY, UNSTAGED_PARENT);
    stageName(FACULTY, "Et fakultet");

    var output = outputOfFailedRun();

    assertThat(output).contains("Parents not staged: 1 (" + UNSTAGED_PARENT + ")");
  }

  @Test
  void shouldPrintTheReportBeforeAndAfterTheLoad() {
    stageUnit(UIO, null);
    stageName(UIO, NAME_OF_UIO);
    var output = new ByteArrayOutputStream();

    LoadUnits.run(sql, new PrintStream(output, true, StandardCharsets.UTF_8));

    assertThat(output.toString(StandardCharsets.UTF_8))
        .contains("## Before the load", "Units not loaded: 1 (" + UIO + ")")
        .contains("## After the load", "Loaded 1 units.", "Clean.");
  }

  @Test
  void shouldLoadThroughTheEnvironment() {
    stageUnit(UIO, null);
    stageName(UIO, NAME_OF_UIO);
    stageUnit(FACULTY, UIO);
    stageName(FACULTY, "Det matematisk-naturvitenskapelige fakultet");
    stagePart(UIO, FACULTY);

    LoadUnits.connectAndRun(connectionEnvironment(), DISCARDED);

    assertThat(sql.fetchCount(UNIT)).isEqualTo(2);
  }

  private String outputOfFailedRun() {
    var output = new ByteArrayOutputStream();
    try {
      LoadUnits.run(sql, new PrintStream(output, true, StandardCharsets.UTF_8));
    } catch (IllegalStateException expected) {
      return output.toString(StandardCharsets.UTF_8);
    }
    throw new AssertionError("The run should have failed");
  }

  private static Map<String, String> connectionEnvironment() {
    var arguments = database.connectionArguments();
    var environment = new HashMap<String, String>();
    environment.put(LoadUnits.URL_VARIABLE, arguments[0]);
    environment.put(LoadUnits.USER_VARIABLE, arguments[1]);
    environment.put(LoadUnits.PASSWORD_VARIABLE, arguments[2]);
    return environment;
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
