# migration

The rehearsed migration from Cristin (D2), in two halves.

1. **Extract**: Cristin's database into the `staging` schema of the target database, with
   standard SQL tools. Waits on read access to Cristin's database; the staging tables below
   define what the extract must produce.
2. **Load**: `staging` into the real tables. Every staged row is turned into a domain value
   first, so a load that completes has only inserted what the domain accepts, and a
   reconciliation report proves the counts and the hierarchy afterwards.

`src/main/resources/staging/units.sql` creates the staging tables; `UnitLoader` loads units and
their names in one transaction into an empty target, parents before children by the `partOf`
relation; nothing is read from the identifiers (ADR 0003). The tests run both against PostgreSQL in
Docker.

`./gradlew :migration:loadUnits`, with `MIGRATION_URL`, `MIGRATION_USER` and
`MIGRATION_PASSWORD` set, runs one rehearsal step: load, reconcile, print the report, and fail
unless it is clean. `buildspec.rehearse.yml` runs it from CodeBuild inside the VPC and keeps the
report as an artefact, so every rehearsal leaves a record.
