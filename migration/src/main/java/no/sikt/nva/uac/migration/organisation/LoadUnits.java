package no.sikt.nva.uac.migration.organisation;

import java.io.PrintStream;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/**
 * One rehearsal step for units: reconcile and print what staging holds, load it into the real
 * tables, reconcile again, print the report, and fail unless the report is clean. The report before
 * the load is printed first so that a load that fails still leaves a record naming what stopped it.
 * Invoked by the Gradle task {@code loadUnits}, which passes the environment on.
 */
public final class LoadUnits {

  public static final String URL_VARIABLE = "MIGRATION_URL";
  public static final String USER_VARIABLE = "MIGRATION_USER";
  public static final String PASSWORD_VARIABLE = "MIGRATION_PASSWORD";
  public static final String MISSING_VARIABLE = "A connection variable is not set";
  public static final String NO_ARGUMENTS =
      "The step takes no arguments; the connection is read from the environment";
  public static final String NOT_CLEAN = "The rehearsal is not clean; see the report above";

  private LoadUnits() {
    // NO-OP
  }

  /**
   * Runs the step against the database the process environment names. Arguments are refused, so
   * that no connection detail can be passed on a command line.
   *
   * @throws IllegalArgumentException if any argument is given, or a connection variable is unset or
   *     blank
   * @throws IllegalStateException if the load fails or the reconciliation after it is not clean
   */
  public static void main(String[] arguments) {
    if (arguments.length > 0) {
      throw new IllegalArgumentException(NO_ARGUMENTS);
    }
    connectAndRun(System.getenv(), System.out);
  }

  /**
   * Connects as the given environment says and runs the step.
   *
   * @param environment holds {@link #URL_VARIABLE}, {@link #USER_VARIABLE} and {@link
   *     #PASSWORD_VARIABLE}
   * @return the report after the load
   * @throws IllegalArgumentException if a connection variable is unset or blank
   * @throws IllegalStateException if the load fails or the reconciliation after it is not clean
   */
  public static UnitReconciliationReport connectAndRun(
      Map<String, String> environment, PrintStream out) {
    try (var connection =
        DSL.using(
            required(environment, URL_VARIABLE),
            required(environment, USER_VARIABLE),
            required(environment, PASSWORD_VARIABLE))) {
      return run(DSL.using(connection.configuration().derive(SQLDialect.POSTGRES)), out);
    }
  }

  /**
   * Reconciles, loads, reconciles again and prints both reports.
   *
   * @return the report after the load
   * @throws IllegalStateException if the load fails or the report after it is not clean
   */
  public static UnitReconciliationReport run(DSLContext sql, PrintStream out) {
    var reconciliation = new UnitReconciliation(sql);
    out.println("# Rehearsal: units");
    out.println();
    out.println("## Before the load");
    out.println();
    out.print(reconciliation.report().asMarkdown());
    var loaded = new UnitLoader(sql).load();
    out.println();
    out.println("## After the load");
    out.println();
    out.println("Loaded " + loaded + " units.");
    out.println();
    var report = reconciliation.report();
    out.print(report.asMarkdown());
    if (!report.isClean()) {
      throw new IllegalStateException(NOT_CLEAN);
    }
    return report;
  }

  private static String required(Map<String, String> environment, String variable) {
    return Optional.ofNullable(environment.get(variable))
        .filter(Predicate.not(String::isBlank))
        .orElseThrow(() -> new IllegalArgumentException(MISSING_VARIABLE + ": " + variable));
  }
}
