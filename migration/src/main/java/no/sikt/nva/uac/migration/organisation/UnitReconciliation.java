package no.sikt.nva.uac.migration.organisation;

import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT;
import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT_LABEL;
import static org.jooq.impl.DSL.select;

import java.util.Objects;
import java.util.Set;
import org.jooq.DSLContext;

/**
 * Compares the staging tables with the real tables. Run before a load to find what cannot be
 * loaded, and after a load to prove that everything was.
 */
public final class UnitReconciliation {

  private final DSLContext sql;

  public UnitReconciliation(DSLContext sql) {
    this.sql = Objects.requireNonNull(sql, "A jOOQ context is required");
  }

  public UnitReconciliationReport report() {
    return new UnitReconciliationReport(
        sql.fetchCount(StagedUnits.UNIT),
        sql.fetchCount(UNIT),
        sql.fetchCount(StagedUnits.UNIT_NAME),
        sql.fetchCount(UNIT_LABEL),
        unitsNotLoaded(),
        parentsNotStaged(),
        unitsWithoutName());
  }

  private Set<String> unitsNotLoaded() {
    return sql.select(StagedUnits.IDENTIFIER)
        .from(StagedUnits.UNIT)
        .where(StagedUnits.IDENTIFIER.notIn(select(UNIT.IDENTIFIER).from(UNIT)))
        .fetchSet(StagedUnits.IDENTIFIER);
  }

  private Set<String> parentsNotStaged() {
    return sql.selectDistinct(StagedUnits.PARENT_IDENTIFIER)
        .from(StagedUnits.UNIT)
        .where(StagedUnits.PARENT_IDENTIFIER.isNotNull())
        .and(
            StagedUnits.PARENT_IDENTIFIER.notIn(
                select(StagedUnits.IDENTIFIER).from(StagedUnits.UNIT)))
        .fetchSet(StagedUnits.PARENT_IDENTIFIER);
  }

  private Set<String> unitsWithoutName() {
    return sql.select(StagedUnits.IDENTIFIER)
        .from(StagedUnits.UNIT)
        .where(
            StagedUnits.IDENTIFIER.notIn(
                select(StagedUnits.IDENTIFIER).from(StagedUnits.UNIT_NAME)))
        .fetchSet(StagedUnits.IDENTIFIER);
  }
}
