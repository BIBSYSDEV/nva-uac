package no.sikt.nva.uac.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One disposable PostgreSQL for a test class, with every migration applied. */
public final class MigratedDatabase implements AutoCloseable {

  private static final String POSTGRES_IMAGE = "postgres:17-alpine";

  private final PostgreSQLContainer container = new PostgreSQLContainer(POSTGRES_IMAGE);

  private MigratedDatabase() {
    container.start();
    Flyway.configure().dataSource(dataSource()).load().migrate();
  }

  /** Starts PostgreSQL in Docker and applies the migrations of this module. */
  public static MigratedDatabase start() {
    return new MigratedDatabase();
  }

  /** A plain, unpooled data source for the migrated database. */
  public DataSource dataSource() {
    var dataSource = new PGSimpleDataSource();
    dataSource.setUrl(container.getJdbcUrl());
    dataSource.setUser(container.getUsername());
    dataSource.setPassword(container.getPassword());
    return dataSource;
  }

  @Override
  public void close() {
    container.stop();
  }
}
