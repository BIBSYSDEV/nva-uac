package no.sikt.nva.uac.migration.organisation;

import java.io.PrintStream;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/**
 * One rehearsal step for units: load staging into the real tables, reconcile, print the report, and
 * fail unless the report is clean. Invoked by the Gradle task {@code loadUnits}, which takes the
 * connection from the environment.
 */
public final class LoadUnits {

  public static final String USAGE = "Arguments: <jdbc-url> <user> <password>";
  public static final String NOT_CLEAN = "The rehearsal is not clean; see the report above";
  private static final int URL = 0;
  private static final int USER = 1;
  private static final int PASSWORD = 2;
  private static final int ARGUMENTS = 3;

  private LoadUnits() {
    // NO-OP
  }

  /**
   * Runs the step against the database the arguments name.
   *
   * @throws IllegalArgumentException if the arguments are not url, user and password
   * @throws IllegalStateException if the reconciliation after the load is not clean
   */
  public static void main(String[] arguments) {
    if (arguments.length != ARGUMENTS) {
      throw new IllegalArgumentException(USAGE);
    }
    try (var connection = DSL.using(arguments[URL], arguments[USER], arguments[PASSWORD])) {
      run(DSL.using(connection.configuration().derive(SQLDialect.POSTGRES)), System.out);
    }
  }

  /**
   * Loads, reconciles and prints the report.
   *
   * @return the report
   * @throws IllegalStateException if the report is not clean
   */
  public static UnitReconciliationReport run(DSLContext sql, PrintStream out) {
    var loaded = new UnitLoader(sql).load();
    var report = new UnitReconciliation(sql).report();
    out.println("# Rehearsal: units");
    out.println();
    out.println("Loaded " + loaded + " units.");
    out.println();
    out.print(report.asMarkdown());
    if (!report.isClean()) {
      throw new IllegalStateException(NOT_CLEAN);
    }
    return report;
  }
}
