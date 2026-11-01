package no.sikt.nva.uac.persistence.organisation;

import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT;
import static no.sikt.nva.uac.persistence.jooq.Tables.UNIT_LABEL;

import org.jooq.DSLContext;

/** Writes well-formed rows for the repository tests; writing is not yet a use case. */
public final class UnitRows {

  private final DSLContext sql;

  public UnitRows(DSLContext sql) {
    this.sql = sql;
  }

  public void institution(String identifier, String acronym) {
    sql.insertInto(UNIT, UNIT.IDENTIFIER, UNIT.ACRONYM).values(identifier, acronym).execute();
  }

  public void unit(String identifier, String parent) {
    sql.insertInto(UNIT, UNIT.IDENTIFIER, UNIT.PARENT_IDENTIFIER)
        .values(identifier, parent)
        .execute();
  }

  public void label(String unit, String language, String text) {
    sql.insertInto(UNIT_LABEL, UNIT_LABEL.UNIT_IDENTIFIER, UNIT_LABEL.LANGUAGE, UNIT_LABEL.TEXT)
        .values(unit, language, text)
        .execute();
  }

  public void deleteAll() {
    sql.deleteFrom(UNIT).execute();
  }
}
