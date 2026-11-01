# adapter-persistence-postgres

The driven persistence adapter: Flyway migrations under `src/main/resources/db/migration`,
jOOQ classes generated from those migrations at build time, and the repositories that implement
the ports of `core` over PostgreSQL.

The schema enforces the same invariants as the value objects in `core`, so a row that the
database accepts is a value the domain accepts. Migrations are applied by a pipeline step
before traffic is shifted, and by the tests here against a disposable PostgreSQL in Docker.
Nothing in this module depends on Quarkus; the application modules supply the data source.
