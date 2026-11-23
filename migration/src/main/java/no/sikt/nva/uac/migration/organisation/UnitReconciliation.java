package no.sikt.nva.uac.migration.organisation;

import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT;
import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT_LABEL;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import no.sikt.nva.uac.organisation.UnitIdentifier;
import org.jooq.DSLContext;

/**
 * Compares the staging tables with the real tables, and the two staged directions of the hierarchy
 * with each other. Run before a load to find what cannot be loaded, and after a load to prove that
 * everything was.
 *
 * <p>Malformed staged identifiers are reported with every character that is not a digit or a dot
 * masked, so the report never carries raw extract data.
 */
public final class UnitReconciliation {

  private static final Pattern NOT_DIGIT_OR_DOT = Pattern.compile("[^0-9.]");
  private static final String MASK = "?";

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
        malformedIdentifiers(),
        unitsNotLoaded(),
        parentsNotStaged(),
        unitsWithoutName(),
        namesWithoutUnit(),
        childrenNotListedByParent(),
        listedChildrenClaimingAnotherParent());
  }

  private Set<String> malformedIdentifiers() {
    return sql
        .select(StagedUnits.IDENTIFIER)
        .from(StagedUnits.UNIT)
        .fetch(StagedUnits.IDENTIFIER)
        .stream()
        .filter(UnitReconciliation::isMalformed)
        .map(UnitReconciliation::masked)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static boolean isMalformed(String identifier) {
    return !UnitIdentifier.isWellFormed(identifier);
  }

  private static String masked(String identifier) {
    return NOT_DIGIT_OR_DOT.matcher(identifier).replaceAll(MASK);
  }

  private Set<String> namesWithoutUnit() {
    return sql.selectDistinct(StagedUnits.IDENTIFIER)
        .from(StagedUnits.UNIT_NAME)
        .where(StagedUnits.IDENTIFIER.notIn(select(StagedUnits.IDENTIFIER).from(StagedUnits.UNIT)))
        .fetchSet(StagedUnits.IDENTIFIER);
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

  private Set<String> childrenNotListedByParent() {
    return sql.select(StagedUnits.IDENTIFIER)
        .from(StagedUnits.UNIT)
        .where(StagedUnits.PARENT_IDENTIFIER.isNotNull())
        .and(
            row(StagedUnits.PARENT_IDENTIFIER, StagedUnits.IDENTIFIER)
                .notIn(
                    select(StagedUnits.LISTED_PARENT, StagedUnits.LISTED_CHILD)
                        .from(StagedUnits.UNIT_PART)))
        .fetchSet(StagedUnits.IDENTIFIER);
  }

  private Set<String> listedChildrenClaimingAnotherParent() {
    return sql.select(StagedUnits.LISTED_PARENT, StagedUnits.LISTED_CHILD)
        .from(StagedUnits.UNIT_PART)
        .where(
            row(StagedUnits.LISTED_PARENT, StagedUnits.LISTED_CHILD)
                .notIn(
                    select(StagedUnits.PARENT_IDENTIFIER, StagedUnits.IDENTIFIER)
                        .from(StagedUnits.UNIT)
                        .where(StagedUnits.PARENT_IDENTIFIER.isNotNull())))
        .fetchSet(listing -> "%s -> %s".formatted(listing.value1(), listing.value2()));
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
