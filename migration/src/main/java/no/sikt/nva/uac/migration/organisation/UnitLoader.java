package no.sikt.nva.uac.migration.organisation;

import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT;
import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT_LABEL;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.sikt.nva.uac.organisation.Unit;
import no.sikt.nva.uac.organisation.UnitIdentifier;
import no.sikt.nva.uac.shared.Labels;
import no.sikt.nva.uac.shared.LanguageTag;
import no.sikt.nva.uac.shared.LanguageTaggedText;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.impl.DSL;

/**
 * Loads staged units and their names into the real tables, in one transaction, into an empty
 * target. Every staged row becomes a {@link Unit} first, so the domain's invariants decide what is
 * loaded; the database constraints then agree by construction.
 *
 * <p>Parents are inserted before their children, in the order the {@code partOf} relation gives and
 * not in any order read from the identifiers (ADR 0003); a parent that is not staged, or a cycle of
 * parents, fails the load before anything is written, and the message names the identifier at which
 * the walk up the parents failed. Identifiers are safe to name because they have passed the form
 * check by then.
 */
public final class UnitLoader {

  public static final String TARGET_NOT_EMPTY =
      "The unit tables must be empty before a load; drop and migrate the schema first";
  public static final String PARENT_NOT_STAGED = "A staged unit names a parent that is not staged";
  public static final String PARENTS_FORM_A_CYCLE = "The staged parents form a cycle";
  public static final String UNIT_WITHOUT_NAME = "A staged unit has no name in any language";

  private final DSLContext sql;

  public UnitLoader(DSLContext sql) {
    this.sql = Objects.requireNonNull(sql, "A jOOQ context is required");
  }

  /**
   * Loads everything staged.
   *
   * @return the number of units loaded
   * @throws IllegalStateException if the target already holds units, a staged unit has no name, a
   *     staged parent is not staged itself, or the staged parents form a cycle
   * @throws no.sikt.nva.uac.shared.InvalidIdentifierException if a staged identifier is malformed
   * @throws no.sikt.nva.uac.shared.InvalidValueException if a staged unit or name breaks an
   *     invariant
   */
  public int load() {
    return sql.transactionResult(this::loadWithin);
  }

  private int loadWithin(Configuration transaction) {
    var inTransaction = DSL.using(transaction);
    if (inTransaction.fetchExists(UNIT)) {
      throw new IllegalStateException(TARGET_NOT_EMPTY);
    }
    var units = stagedUnits(inTransaction);
    units.forEach(unit -> insert(inTransaction, unit));
    return units.size();
  }

  private static List<Unit> stagedUnits(DSLContext sql) {
    var names = stagedNames(sql);
    var staged =
        sql
            .select(StagedUnits.IDENTIFIER, StagedUnits.PARENT_IDENTIFIER, StagedUnits.ACRONYM)
            .from(StagedUnits.UNIT)
            .fetch(row -> toUnit(row, names))
            .stream()
            .collect(Collectors.toMap(Unit::identifier, Function.identity()));
    return staged.values().stream()
        .map(unit -> new Placed(depth(unit, staged), unit))
        .sorted(Comparator.comparingInt(Placed::depth))
        .map(Placed::unit)
        .toList();
  }

  private record Placed(int depth, Unit unit) {}

  private static Map<String, Labels> stagedNames(DSLContext sql) {
    return sql
        .select(StagedUnits.IDENTIFIER, StagedUnits.LANGUAGE, StagedUnits.TEXT)
        .from(StagedUnits.UNIT_NAME)
        .fetchGroups(
            StagedUnits.IDENTIFIER,
            row -> new LanguageTaggedText(new LanguageTag(row.value2()), row.value3()))
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> Labels.from(entry.getValue())));
  }

  private static Unit toUnit(Record3<String, String, String> row, Map<String, Labels> names) {
    var identifier = UnitIdentifier.parse(row.value1());
    return new Unit(
        identifier,
        Optional.ofNullable(names.get(row.value1()))
            .orElseThrow(() -> failure(UNIT_WITHOUT_NAME, identifier)),
        Optional.ofNullable(row.value3()),
        Optional.ofNullable(row.value2()).map(UnitIdentifier::parse));
  }

  private static int depth(Unit unit, Map<UnitIdentifier, Unit> staged) {
    var ancestors = new HashSet<UnitIdentifier>();
    var current = unit;
    while (current.partOf().isPresent()) {
      var parent = current.partOf().orElseThrow();
      if (!ancestors.add(parent)) {
        throw failure(PARENTS_FORM_A_CYCLE, parent);
      }
      current = stagedParent(parent, staged);
    }
    return ancestors.size();
  }

  private static Unit stagedParent(UnitIdentifier parent, Map<UnitIdentifier, Unit> staged) {
    return Optional.ofNullable(staged.get(parent))
        .orElseThrow(() -> failure(PARENT_NOT_STAGED, parent));
  }

  private static IllegalStateException failure(String reason, UnitIdentifier identifier) {
    return new IllegalStateException("%s: %s".formatted(reason, identifier));
  }

  private static void insert(DSLContext sql, Unit unit) {
    sql.insertInto(UNIT, UNIT.IDENTIFIER, UNIT.ACRONYM, UNIT.PARENT_IDENTIFIER)
        .values(
            unit.identifier().toString(),
            unit.acronym().orElse(null),
            unit.partOf().map(UnitIdentifier::toString).orElse(null))
        .execute();
    unit.name()
        .byLanguage()
        .forEach(
            (language, text) ->
                sql.insertInto(
                        UNIT_LABEL,
                        UNIT_LABEL.UNIT_IDENTIFIER,
                        UNIT_LABEL.LANGUAGE,
                        UNIT_LABEL.TEXT)
                    .values(unit.identifier().toString(), language.value(), text)
                    .execute());
  }
}
