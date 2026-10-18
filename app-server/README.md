# app-server

The application as a plain HTTP server, for EKS, a private cloud and local development: the
`assembly` behind Quarkus's HTTP server, with Jib to build the container image.

- `./gradlew :app-server:quarkusDev` runs it locally with Dev Services: PostgreSQL started and
  migrated, and a Keycloak started as the OIDC issuer. Docker is required.
- `./gradlew :app-server:build -Dquarkus.container-image.build=true` builds the image
  `no.sikt.nva/nva-uac` without a Dockerfile.
- Health is at `/q/health`, the registered OpenAPI document at `/q/openapi`.

Per-environment configuration, the OIDC issuer and the data source, comes from environment
variables or mounted configuration. The test here runs the walking skeleton over plain HTTP
against a PostgreSQL started by Dev Services.
