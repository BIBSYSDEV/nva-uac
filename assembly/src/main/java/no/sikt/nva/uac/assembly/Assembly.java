package no.sikt.nva.uac.assembly;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import javax.sql.DataSource;
import no.sikt.nva.uac.organisation.FindUnit;
import no.sikt.nva.uac.organisation.FindUnitUseCase;
import no.sikt.nva.uac.organisation.UnitRepository;
import no.sikt.nva.uac.persistence.organisation.PostgresUnitRepository;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/**
 * Composes the driven adapters into the use cases the driving adapters inject. The only place that
 * knows which implementation stands behind each port.
 */
@ApplicationScoped
public class Assembly {

  @Produces
  @ApplicationScoped
  public DSLContext sql(DataSource dataSource) {
    return DSL.using(dataSource, SQLDialect.POSTGRES);
  }

  @Produces
  @ApplicationScoped
  public UnitRepository units(DSLContext sql) {
    return new PostgresUnitRepository(sql);
  }

  @Produces
  @ApplicationScoped
  public FindUnit findUnit(UnitRepository units) {
    return new FindUnitUseCase(units);
  }
}
