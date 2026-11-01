package no.sikt.nva.uac.persistence.organisation;

import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT;
import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT_LABEL;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;

import java.util.Objects;
import java.util.Optional;
import no.sikt.nva.uac.organisation.Unit;
import no.sikt.nva.uac.organisation.UnitIdentifier;
import no.sikt.nva.uac.organisation.UnitRepository;
import no.sikt.nva.uac.shared.Labels;
import no.sikt.nva.uac.shared.LanguageTag;
import no.sikt.nva.uac.shared.LanguageTaggedText;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.Record4;
import org.jooq.Result;

/**
 * {@link UnitRepository} over the {@code unit} and {@code unit_label} tables. One query per lookup:
 * the unit row with its labels as a nested result.
 */
public final class PostgresUnitRepository implements UnitRepository {

  private final DSLContext sql;

  public PostgresUnitRepository(DSLContext sql) {
    this.sql = Objects.requireNonNull(sql, "A jOOQ context is required");
  }

  @Override
  public Optional<Unit> find(UnitIdentifier identifier) {
    return sql.select(
            UNIT.IDENTIFIER,
            UNIT.ACRONYM,
            UNIT.PARENT_IDENTIFIER,
            multiset(
                select(UNIT_LABEL.LANGUAGE, UNIT_LABEL.TEXT)
                    .from(UNIT_LABEL)
                    .where(UNIT_LABEL.UNIT_IDENTIFIER.eq(UNIT.IDENTIFIER))))
        .from(UNIT)
        .where(UNIT.IDENTIFIER.eq(identifier.toString()))
        .fetchOptional(PostgresUnitRepository::toUnit);
  }

  private static Unit toUnit(Record4<String, String, String, Result<Record2<String, String>>> row) {
    return new Unit(
        UnitIdentifier.parse(row.value1()),
        toLabels(row.value4()),
        Optional.ofNullable(row.value2()),
        Optional.ofNullable(row.value3()).map(UnitIdentifier::parse));
  }

  private static Labels toLabels(Result<Record2<String, String>> labels) {
    return Labels.from(
        labels.map(
            label -> new LanguageTaggedText(new LanguageTag(label.value1()), label.value2())));
  }
}
