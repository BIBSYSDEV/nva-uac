package no.sikt.nva.uac.migration.organisation;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;

/** The staging tables the extract from Cristin fills, as the load reads them. */
public final class StagedUnits {

  public static final Table<?> UNIT = table(name("staging", "unit"));
  public static final Table<?> UNIT_NAME = table(name("staging", "unit_name"));
  public static final Field<String> IDENTIFIER = field(name("identifier"), String.class);
  public static final Field<String> PARENT_IDENTIFIER =
      field(name("parent_identifier"), String.class);
  public static final Field<String> ACRONYM = field(name("acronym"), String.class);
  public static final Field<String> LANGUAGE = field(name("language"), String.class);
  public static final Field<String> TEXT = field(name("text"), String.class);

  private static final String DEFINITION_RESOURCE = "/staging/units.sql";

  private StagedUnits() {
    // NO-OP
  }

  /** Creates the staging schema and tables when they do not exist yet. */
  public static void createIn(DSLContext sql) {
    sql.execute(definition());
  }

  private static String definition() {
    try (var script = StagedUnits.class.getResourceAsStream(DEFINITION_RESOURCE)) {
      return new String(script.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
