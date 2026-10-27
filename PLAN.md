# nva-uac: plan

This plan refines [required.md](docs/required.md). It is derived from
[cristin-upstream-usage.md](docs/cristin-upstream-usage.md) and from the source of
[nva-cristin-service](https://github.com/BIBSYSDEV/nva-cristin-service) and
[nva-identity-service](https://github.com/BIBSYSDEV/nva-identity-service), read at `main` as of
14 October 2026. Section numbers in brackets, e.g. [4.3], refer to the usage report.

## 1. What we are building, in one paragraph

A service that owns the research-information data NVA currently fetches from the Cristin REST
API: persons, their employments and affiliations, the organisation (unit) hierarchy, projects,
biobanks and the reference lists they depend on. It replaces the Cristin API as the data
provider behind `nva-cristin-service` and `nva-identity-service`, and after cut-over it is the
system of record; Cristin is closed. Only the functional surface those two services actually use
is in scope. The wire format of the old API is not a requirement; the behaviour the NVA services
need is. It is a Quarkus application over PostgreSQL, deployed first to AWS Lambda behind API
Gateway, later as the same image on EKS and on a private cloud.

## 2. Decisions

D1 to D9 and D15 are decided. D10 to D14 are assumed as written until someone overrides them;
every later section follows this table and nothing else.

| #   | Decision                                                                                                                                                                                                                                                                                                                                      | Status  | Consequence                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| D1  | New, clean API. The NVA clients are adapted.                                                                                                                                                                                                                                                                                                  | Decided | No byte compatibility with Cristin. A Cristin-shaped driving adapter is not planned; the hexagonal layout keeps it cheap if cut-over risk ever demands it.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| D2  | Data is migrated from Cristin's database with standard SQL tools. The migration is rehearsed repeatedly against a test target until the acceptance and fitness tests pass. The final run happens once, in a closed weekend window, after which nva-uac is the system of record and Cristin is closed.                                         | Decided | No delta sync, no dual running, no write-back. The migration is a tested deliverable with a runbook and a rollback window (section 9). Production switches for every slice at once in Phase 9; earlier phases switch dev and test only.                                                                                                                                                                                                                                                                                                                                                                                                                |
| D3  | User-level authorisation stays in the NVA services. nva-uac authorises machine clients by OAuth2 scope and takes "representing institution" as explicit input on writes.                                                                                                                                                                      | Decided | The role rules in [2.9, 4.6, 4.7] are not reimplemented. The scope-to-role mapping is documented in the OpenAPI description so both sides agree.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| D4  | Public identifiers stay as they are: Cristin numeric ids and dotted unit ids (`185.15.2.10`).                                                                                                                                                                                                                                                 | Decided | NVA URIs such as `/cristin/organization/20754.0.0.0` keep working. New records get ids from the same spaces.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| D5  | Search and facets in PostgreSQL (full-text search and `GROUP BY`) until measurement proves otherwise.                                                                                                                                                                                                                                         | Decided | One store, transactional, local-runnable. The access patterns of every consumer are not yet understood, and SQL keeps them all open where a key-value or search-index design would commit to the ones known today. OpenSearch is not in the plan.                                                                                                                                                                                                                                                                                                                                                                                                      |
| D6  | Persistence through jOOQ, or another immutable-by-default library, on the Quarkus datasource stack (Agroal, PostgreSQL JDBC, Flyway).                                                                                                                                                                                                         | Decided | No Hibernate, no Panache, no mutable entity classes. Queries are explicit SQL, which the hierarchy, facet and full-text paths need anyway.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| D7  | HTTP runtime is standard Quarkus: Quarkus REST, served on AWS through the Amazon Lambda HTTP extension behind an API Gateway HTTP API (v2) with a JWT authoriser, and through the plain Quarkus HTTP server on EKS and private cloud.                                                                                                         | Decided | One set of resources for all three targets. No Lambda Web Adapter, no Helidon, no Javalin. HTTP API v2 gives the JWT authoriser and drops the API Gateway cache, which the plan does not want.                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| D8  | Person pictures move to object storage. Only metadata is in PostgreSQL.                                                                                                                                                                                                                                                                       | Decided | S3 on AWS, an S3-compatible store elsewhere. The picture store is a driven port.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| D9  | Multilingual labels are rows `(entity, language, text)`, projected to `{lang: text}` maps at the edge.                                                                                                                                                                                                                                        | Decided | Labels are graph edges with a language tag. Per-language sorting and full-text indexes work on columns, not on JSON.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| D10 | Quarkus is the application framework. JVM with SnapStart on Lambda, a GraalVM native executable only if the cold-start budget is missed, JVM container on EKS and private cloud.                                                                                                                                                              | Assumed | `core` carries no Quarkus dependency. Constructor injection, records and `@ConfigMapping` interfaces; no field injection, no mutable beans. Quarkus fixes the test harness (`@QuarkusTest`, Dev Services), OIDC validation and the local developer loop.                                                                                                                                                                                                                                                                                                                                                                                               |
| D11 | RDF tooling: Jena writes the ontology and runs the round-trip tests, outside the runtime classpath. JSON-LD on the wire is produced by Titanium JSON-LD or by direct serialisation where the shape is fixed. RDF4J is not added unless a runtime SPARQL or SHACL need appears.                                                                | Assumed | No RDF library in the Lambda or EKS artefact, so native image stays open. Graph-first governs the model, not the serving path.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| D12 | Scopes: Phase 0 and 1 accept the existing NVA backend scope. Before Phase 2 exposes NIN, nva-uac's own scopes are registered as a resource server in the NVA user pool, which lives in the identity-service template.                                                                                                                         | Assumed | The scope names are defined in nva-uac's OpenAPI from day one and enforced by Quarkus OIDC. Registration is a pull request to nva-identity-service, agreed in Phase 0.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| D13 | One deployable. The five bounded contexts are modules inside it and share one database, but never each other's tables.                                                                                                                                                                                                                        | Assumed | required.md's "microservices" is read as nva-uac being one service in the NVA landscape. The login aggregate needs one transaction across person and organisation, which argues against splitting now. A later split follows module boundaries.                                                                                                                                                                                                                                                                                                                                                                                                        |
| D14 | Lambda runs in a VPC with RDS Proxy. Phase 0 establishes whether the NVA accounts export a shared VPC and subnets; if not, nva-uac owns a network stack with private subnets and VPC endpoints.                                                                                                                                               | Assumed | Neither of the two replaced services uses a VPC, but the team has run Neptune in one, so this is routine. Egress for the issuer's JWKS fetch is part of the same decision.                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| D15 | Aurora PostgreSQL Serverless v2 is the database baseline in every AWS environment, production included. Provisioned instances are introduced only if the fitness tests on Serverless v2 miss their budget. All automated tests run against a local community PostgreSQL of the same major version, started by Dev Services or Testcontainers. | Decided | The schema and queries use only features present in community PostgreSQL, so local tests are representative and the EKS and private-cloud targets need nothing special. Aurora-specific behaviour (capacity scaling, failover, proxy) is exercised by the fitness tests on the rehearsal cluster, not by unit or acceptance tests. Every AWS environment has the same shape, RDS Proxy included, so dev and test exercise the real deployment; no per-environment toggles. Behind RDS Proxy a Serverless v2 instance does not pause to zero, so each environment pays at least the minimum half ACU per instance, which is the price of that symmetry. |

## 3. Scope: capabilities by bounded context

Consolidated from the 34 upstream calls. Each row is a use case the core must expose as a
driving port. Writes are aggregate operations in a single transaction.

### 3.1 Organisation

| Capability                                                                             | Replaces                                               | Notes                                                                                                                                                                                                                                               |
| -------------------------------------------------------------------------------------- | ------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Get unit by id with `partOf` chain to top and subtree (`none`, `top`, `full`)          | U23, U24, U25 [5.3, 5.4]                               | One query (closure table or `ltree`). No 32-level, 2000-subunit or page-2 caps.                                                                                                                                                                     |
| Search units by name, paged, sorted (`country`, `name_{en,nb,nn}`), optional full tree | U24 [5.1, 5.2]                                         | Sorting per language works because labels are rows (D9).                                                                                                                                                                                            |
| Resolve institution for a unit (top-level unit, Cristin's `corresponding_unit`)        | I4 [10.5]                                              | Used in login; must not require an extra round trip.                                                                                                                                                                                                |
| Export all Norwegian units                                                             | `UpdateUnitsHandler` job and `units-norway.json` in S3 | Other NVA services read this file and discover it through the SSM parameter `/NVA/S3/CristinIntermediateStorage/Units`. Decide whether nva-uac keeps producing the file or offers a streaming endpoint, after the consumers are found (section 11). |

### 3.2 Person

| Capability                                                                                               | Replaces                            | Notes                                                                                                                                                              |
| -------------------------------------------------------------------------------------------------------- | ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Get person by Cristin id or ORCID, public view                                                           | U9 [4.2]                            | Reserved person returns id only.                                                                                                                                   |
| Get person, authorised view (NIN, employments, reserved flag, NVI, awards)                               | U9, I2                              | Scope-gated (D12).                                                                                                                                                 |
| Resolve person by national identity number                                                               | U7 (two calls) [4.3], I1 [10.3]     | Single call. Blind-index lookup, see section 6.                                                                                                                    |
| Login aggregate: person (by NIN or id) with active affiliations resolved to institution and units        | I1 + N×I4, I2 + N×I4 [10.6]         | One call replaces 1+N. A create-if-missing variant replaces the I3 call in the login flow [10.4], idempotent on NIN, and reuses the create-person aggregate below. |
| Search persons by name, institution, verified, sort, paged, with facets (sector, institution), full hits | U7, U8 and the N+1 enrichment [4.1] |                                                                                                                                                                    |
| List persons in a unit, paged                                                                            | U7 [4.4]                            |                                                                                                                                                                    |
| Create person with contact, keywords, countries, employments, NVI                                        | U10 [4.5]                           | Aggregate, one transaction. Duplicate NIN is a 409, not a text match on "already exists".                                                                          |
| Patch person                                                                                             | U11 [4.6]                           | Merge-patch semantics on the fields listed in 4.6.                                                                                                                 |
| Employments: list, create, patch, delete                                                                 | U12–U15 [4.7]                       | Representing institution is explicit input.                                                                                                                        |
| Institution-specific contact (email, phone): get, patch                                                  | U16, U17 [4.8]                      |                                                                                                                                                                    |
| Picture: get, put, delete                                                                                | U18, U19 [4.9]                      | Object storage (D8). Content type validated.                                                                                                                       |
| List persons with ORCID                                                                                  | U22 [4.10]                          | Fix the URI bug noted in [9].                                                                                                                                      |

### 3.3 Project

| Capability                                                                          | Replaces                             | Notes                                                                                                                                                          |
| ----------------------------------------------------------------------------------- | ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Get project by id, full                                                             | U1 [3.1, 3.6]                        | Includes `creator` so NVA can enforce "own project" rules.                                                                                                     |
| Search projects by the parameter matrix in 3.2, with facets, full hits, total count | U2, U3 and the N+1 enrichment        | Parameters: title/project code, unit with depth, status, dates, participant, category, funding, keyword, biobank, institution, manager, approval fields, sort. |
| Projects of a unit with depth                                                       | U2 via `/organization/{id}/projects` |                                                                                                                                                                |
| Create project                                                                      | U5 [3.4]                             | Aggregate with participants, funding, approvals, categories, keywords, related projects.                                                                       |
| Patch project                                                                       | U6 [3.5]                             | Merge-patch. Fix the null-list defect noted in [9].                                                                                                            |
| List project categories                                                             | U4 [3.3]                             | Reference data.                                                                                                                                                |

### 3.4 Reference data

Positions (code, name, enabled), countries (alpha-2, alpha-3, name), funding sources (code,
name), keywords (search, get, create), project categories. [3.3, 4.10, 6, 7]

### 3.5 Biobank

Get biobank by id [8]. The undocumented query parameters on this call are dropped unless a
consumer is found.

### 3.6 Explicitly not reimplemented

These are properties of the old transport, not of the capability. They go away:

- Bot-filter bypass header, Basic auth challenge and preemptive variants [2.2, 2.3].
- `lang=en,nb,nn` on every call; responses carry all labels in all languages.
- `link` header substring parsing and the "page out of scope" 400 [2.5]. Paging is defined in
  section 4 instead.
- Legacy organisation v1 recursion and the `version=` content negotiation [2.6, 5.4].
- Facets URI rebuild after decoding [2.8].
- Identifier leniency (`+5`, `007`) [2.7]. Identifiers are validated strictly.
- The 3600 s and 300 s API Gateway caches with incomplete keys [5.1, 8, 9]. HTTP API v2 has no
  cache (D7).
- Error-text sniffing ("already exists") [10.2].

## 4. Architecture

Ports and adapters, packaged by bounded context and then by role. Gradle multi-module, Java 25,
Quarkus (D10). One deployable (D13) built in two packagings (D7).

```
nva-uac/
  core/                          plain Java: domain model + ports; no Quarkus, no I/O, no serialisation
    organisation/                model, driving ports (use cases), driven ports (repositories)
    person/
    project/
    referencedata/
    biobank/
    shared/                      identifiers, language-tagged text, paging, errors
  adapter-persistence-postgres/  jOOQ repositories, UnitOfWork, Flyway migrations
  adapter-storage-s3/            picture store port on S3 or any S3-compatible store
  adapter-cipher-kms/            NIN cipher port on KMS envelope encryption + Secrets Manager (AWS)
  adapter-cipher-local/          NIN cipher port on a mounted key (local, EKS, private cloud)
  adapter-http/                  Quarkus REST resources, DTOs, OpenAPI, problem+json, OIDC scope checks
  app-lambda/                    Quarkus app: adapter-http + Amazon Lambda HTTP extension, SnapStart
  app-server/                    Quarkus app: adapter-http + HTTP server, Jib image, health, metrics
  migration/                     Cristin database -> nva-uac: SQL, reconciliation and fitness reports
  ontology/                      Turtle ontology and Jena round-trip tests; never on a runtime classpath
  acceptance-tests/              BDD through HTTP; Dev Services PostgreSQL locally, a base URL when deployed
  template.yaml                  SAM application: HTTP API v2, Lambda, the migration projects; the root, as the pipeline expects
  infrastructure/                the data tier: Aurora, RDS Proxy, security groups; network stack if D14 needs it
```

Rules:

- `core` depends on nothing outward, including Quarkus. DTOs live in `adapter-http`, table
  types in `adapter-persistence-postgres`. The domain model is the only thing both know.
- Driving ports are the use cases in section 3. Driven ports are repositories, the picture
  store, the clock and the NIN cipher. Adapters implement them; the app modules wire them with
  CDI producers and constructor injection. Every module Quarkus must scan carries a Jandex index.
- Every write use case is one transaction around one aggregate (person with employments,
  project with participants). The transaction boundary is a driven port (`UnitOfWork`), so the
  core stays free of JDBC.
- Idempotency: `PUT` and `DELETE` are naturally idempotent. Create-person is idempotent on NIN.
  `PATCH` uses `application/merge-patch+json` semantics (RFC 7396).
- Paging: `page` and `size` query parameters, RFC 8288 `Link` headers (`first`, `prev`, `next`,
  `last`) and a total count in the body. A page past the end is an empty page, not a 400.
- Errors are `application/problem+json` (RFC 9457).
- Configuration is read through `@ConfigMapping` interfaces. Nothing in the application looks
  up SSM or Secrets Manager directly; the deployment supplies values.
- Logging: one `LOGGER` per type. NIN is never logged; external input is validated or
  truncated before it appears in any message.

## 5. Data model

Model first as a graph, then project to tables.

### 5.1 Conceptual graph

- **Unit** is a node. `partOf` edges form a tree; the root of a tree is an **Institution**
  (Cristin's `corresponding_unit`). Properties: dotted id, acronym, country, labels per language.
- **Person** is a node. Properties: Cristin id, ORCID, names (given, family, preferred
  variants), contact, reserved flag, verified flag, background, place, collaboration, awards.
- **Employment** is an edge Person → Unit (or Institution) with position code, start, end,
  FTE. The old `affiliations` and `detailed_affiliations` are two projections of the same
  edge set [4.2].
- **Institution contact** is an edge Person → Institution with email and phone [4.8].
- **NVI verification** is an edge Person → (verifying Person, verifying Unit, date) [4.2].
- **Project** is a node. Edges: coordinating Unit, responsible Units, Participant edges with
  roles (`PRO_MANAGER` etc.), funding sources with project codes, keywords, categories,
  related projects, approvals, external sources.
- **Reference nodes**: Country, Position, FundingSource, Keyword, ProjectCategory,
  Sector, HealthProjectType, with labels per language.
- **Biobank** is a node with edges to coordinating Unit, coordinator Person, associated Project,
  materials, approvals.

The ontology is a Turtle file in the `ontology` module. Jena parses it there and in tests that
check the relational projection round-trips (D11). Serving paths do not go through RDF; JSON-LD
representations are produced by Titanium JSON-LD or by direct serialisation.

### 5.2 Relational projection

- One table per node type, one table per edge type with its own properties.
- Unit hierarchy as a closure table or PostgreSQL `ltree`, so subtree and ancestor queries are
  one index scan.
- Labels as `(owner_id, language, text)` tables with a composite primary key (D9).
- National identity number stored as ciphertext plus an HMAC blind index column with a
  unique index, so lookup by NIN is one indexed equality (section 6).
- Full-text search columns (`tsvector`) with GIN indexes for person name and project title.
- Facet counts from `GROUP BY` over the same filtered query as the result page.
- Flyway migrations, applied by a pipeline step before traffic is shifted, never by request-path
  code. Flyway's migrate-at-start is on only in dev and test mode.
- The schema relies on `ltree` and `tsvector`, both available in community PostgreSQL and in
  Aurora PostgreSQL. Nothing Aurora-specific is used (D15): no Aurora extensions, no Babelfish,
  no Limitless features. The EKS and private-cloud targets must offer the same major version;
  confirm before Phase 1 locks the schema.

## 6. Security

- **Authentication**: OAuth2 client credentials, validated in the application by Quarkus OIDC
  against a configurable issuer and JWKS (issuer, audience, expiry, signature). On AWS the
  issuer is the NVA Cognito user pool, and the API Gateway JWT authoriser is a second check in
  front. On EKS and private cloud the issuer is whatever that environment provides.
- **Scopes** (D12): `uac/organisation.read`, `uac/person.read`, `uac/person.sensitive.read`,
  `uac/person.write`, `uac/project.read`, `uac/project.write`, `uac/reference.read`, defined in
  the OpenAPI and mapped to use cases in `adapter-http`. Until the resource server exists,
  `https://api.nva.unit.no/scopes/backend` is accepted for read-only Phase 0 and 1 traffic.
- **Representing institution**: writes that the old API gated with
  `Cristin-Representing-Institution` take the institution as an explicit, validated input and
  record it in an audit column.
- **NIN at rest**: the NIN cipher is a driven port. On AWS it is envelope encryption with a KMS
  data key and a blind index as HMAC-SHA-256 with a separate key from Secrets Manager. On EKS
  and private cloud the same scheme runs on a mounted key. Key rotation re-indexes in a batch
  job. An in-memory implementation serves tests.
- **In transit**: TLS at API Gateway or the ingress, TLS to RDS Proxy or PostgreSQL. Storage
  encryption on.
- **Secrets**: Secrets Manager on AWS, mounted secrets on EKS. Nothing in environment variables
  except names and ARNs.
- **Logging**: NIN masked, request bodies never logged, structured JSON logs.
- **Personal data**: becoming system of record for persons brings retention and erasure duties
  that the proxy never had. The owner and the rules are an open question (section 11); the
  schema must not make erasure impossible.

## 7. Infrastructure

### 7.1 AWS (first target)

- API Gateway HTTP API (v2) with a JWT authoriser on the NVA Cognito issuer, attached to the
  shared custom domain from `/NVA/ApiDomain` through an API mapping. Confirm in Phase 0 that a
  v2 mapping attaches to that domain.
- One Lambda, Java 25, SnapStart, Amazon Lambda HTTP extension (D7), published alias. arm64 if
  SnapStart supports it in the region, otherwise x86_64; measured in Phase 0.
- Lambda in a VPC (D14) with RDS Proxy and IAM authentication to the proxy. The Agroal pool
  needs a credentials provider that refreshes the IAM token.
- Aurora PostgreSQL Serverless v2 in every environment (D15): one writer and one reader in
  two AZs. Dev and test run at the minimum half ACU; production starts at a minimum sized from
  the Phase 0 fitness run, with the maximum set well above it. Scale-to-zero is not available
  behind RDS Proxy, so no environment relies on it; environments are deliberately symmetric
  so that dev and test prove the production shape, including proxy failover behaviour. A move to provisioned instances is a
  template change, not an application change, and is taken only on fitness-test evidence.
- RDS Proxy is billed per ACU for Serverless v2 targets. IAM authentication to the proxy works
  the same way as for provisioned instances.
- S3 bucket for pictures; KMS keys for the bucket, the database and the NIN cipher.
- Flyway migrations run by a CodeBuild project inside the VPC that the application stack
  defines; a custom resource starts it during the deploy, before the function and its alias
  are updated (ADR 0005).
- CloudWatch alarms on error rate and p95 latency, sent to the Slack topic from
  `/NVA/Monitoring/SlackSnsArn`. Log retention set explicitly.
- Pipeline as the other NVA services: the per-account CodePipeline from `NVA-infrastructure`
  builds `main` with `buildspec.yaml` and deploys `packaged.yaml`, with manual approval where
  the account requires it; GitHub Actions run the shared BIBSYSDEV Java workflow on pull
  requests (ADR 0005).
- Branch stacks are deployed by hand in the dev account with `sam deploy` when a change needs
  one, sharing the dev Aurora cluster with one schema per branch; deleting the stack drops the
  schema.
- SSM parameters under `/NVA/...` supply domain, issuer and topic names, as in the existing
  templates. They are read by the template, not by the application.

### 7.2 EKS (second target)

`app-server` image built with Jib, Helm chart, health and metrics from the Quarkus extensions,
OpenTelemetry for tracing, the same PostgreSQL schema, secrets mounted, issuer configured. The
same acceptance suite runs against the ingress URL.

### 7.3 Private cloud (third target)

The `app-server` image with any PostgreSQL 15 or later that offers `ltree` and full-text search,
an S3-compatible object store and an OIDC issuer.

### 7.4 Local

`quarkus dev` with Dev Services: a community PostgreSQL of the same major version as Aurora and
a Keycloak issuer start automatically, so the scope logic runs locally with a real token and the
schema runs on a plain database (D15). No docker compose, no AWS account. A container runtime
is required. The same local database serves the acceptance and persistence tests in CI.

## 8. Testing and quality gates

- **Acceptance tests first.** Every use case starts with a failing BDD test written through the
  HTTP adapter with `@QuarkusTest` and RestAssured against Dev Services PostgreSQL. Unhappy
  paths first: unknown id, invalid identifier, missing scope, duplicate NIN, stale patch,
  reserved person for a public caller. The same suite runs against a base URL and a real token
  when pointed at a deployed stack.
- Given/When/Then as blank-line-separated blocks, `shouldXxx` names, JUnit 5 and AssertJ.
- Core use cases also have fast, plain JUnit tests through the driving ports with in-memory
  driven ports.
- Persistence adapter tests prove the SQL (hierarchy queries, facets, blind index, transactions)
  with Testcontainers.
- **Migration tests** (section 9): reconciliation of counts and sampled records per entity type,
  NIN lookup by blind index on samples, hierarchy and label round-trips. They run on every
  rehearsal and produce a report.
- **Fitness tests**: a budget per read use case (target p95 under 50 ms warm for lookups by id)
  measured against the rehearsal database on Aurora Serverless v2, not fixtures and not the local
  database. They are the only tests that see Aurora, and the only evidence that may move an
  environment to provisioned instances (D15). Cold start measured separately in Phase 0 and
  before cut-over.
- Contract: OpenAPI authored first, linted with Spectral `oas`, and a test that every route in
  the adapter is in the spec and vice versa.
- Coverage: JaCoCo through the Quarkus JaCoCo extension at 100 % method and class, enforced in
  `check`, with Quarkus-generated classes excluded. PMD and Error Prone with failures blocking,
  never downgraded to warnings. Spotless with google-java-format.
- Tooling comes from the shared `nva-gradle-plugins` and the nva-commons version catalogs, with
  the coverage thresholds set to 1.0. The Quarkus Gradle plugin must coexist with them; proven in
  Phase 0.

## 9. Migration and cut-over

The migration is a deliverable of its own (the `migration` module) and is on the critical path
from Phase 0, because its first rehearsal supplies the production-sized data every later phase
measures against.

1. **Access and mapping.** Obtain read access to Cristin's database and document the mapping
   from its schema to the model in section 5. The NIN ciphertext and blind index are produced
   during the load; pictures are copied to object storage; Cristin ids are preserved (D4).
2. **Rehearsal.** Run the full migration into a test target as often as needed. Every run
   produces a reconciliation report and a fitness report (section 8). The first rehearsal feeds
   Phase 0.
3. **Gate.** The final run is approved when the acceptance suite is green against migrated data,
   the fitness budgets hold, and two consecutive rehearsals reconcile clean.
4. **Cut-over weekend.** A runbook with a go/no-go point: freeze Cristin writes, run the
   migration, reconcile, switch the NVA stacks' configuration from Cristin to nva-uac, smoke test
   through the NVA APIs, unfreeze. During the freeze the NVA person and project write paths
   fail or queue; which one is agreed with the NVA teams beforehand.
5. **Rollback.** Pointing the NVA stacks back at Cristin. Cristin therefore stays reachable and
   frozen for an agreed period after the switch. When the period ends, Cristin is closed and its
   credentials are removed from the NVA stacks.
6. **Not done**: delta sync, dual running, write-back.

## 10. Delivery in vertical slices

Each slice ends with: acceptance tests green, 100 % coverage, OpenAPI updated and linted,
deployed to dev through SAM, and the consuming NVA client switched in dev and test against
migrated data, behind configuration. Production switches once, in Phase 9.

| Phase | Slice                                                                                                                                                                                                                                                                                                                                                           | Exit criterion                                                                                                                                                                       |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 0     | Walking skeleton: `GET /organisations/{id}` end to end. Gradle modules with Quarkus and the nva plugins, JaCoCo at 100 %, PMD and Error Prone, OpenAPI and Spectral, SAM deploy with HTTP API v2 and JWT authoriser, VPC, Aurora Serverless v2 and RDS Proxy (D14, D15), `quarkus dev` against local PostgreSQL, first rehearsal migration of units and persons | One request round-trips in dev on the shared domain. Cold start with SnapStart and warm p95 measured on rehearsal data; native image decided (D10). Scope registration agreed (D12). |
| 1     | Organisation: get with hierarchy, search, institution resolution, Norwegian units export                                                                                                                                                                                                                                                                        | nva-cristin-service organisation module switched in dev and test. Consumers of `units-norway.json` known.                                                                            |
| 2     | Person login aggregate: resolve by NIN, get by id, create-if-missing, affiliations resolved                                                                                                                                                                                                                                                                     | nva-identity-service `CristinPersonRegistry` replaced in dev and test. nva-uac scopes live in the user pool. Narrowest contract, highest value.                                      |
| 3     | Reference data: positions, countries, funding sources, keywords, project categories                                                                                                                                                                                                                                                                             | Reference modules switched in dev and test.                                                                                                                                          |
| 4     | Person read and search: public and authorised views, ORCID, search with facets, persons in unit, ORCID list                                                                                                                                                                                                                                                     | Person read paths switched in dev and test.                                                                                                                                          |
| 5     | Person writes: create, patch, employments, institution contact, picture                                                                                                                                                                                                                                                                                         | Person write paths switched in dev and test.                                                                                                                                         |
| 6     | Project read and search with facets, projects of unit                                                                                                                                                                                                                                                                                                           | Project read paths switched in dev and test.                                                                                                                                         |
| 7     | Project writes: create, patch                                                                                                                                                                                                                                                                                                                                   | Project write paths switched in dev and test.                                                                                                                                        |
| 8     | Biobank                                                                                                                                                                                                                                                                                                                                                         | Biobank switched in dev and test. Full migration passes the gate in section 9.                                                                                                       |
| 9     | Cut-over weekend (section 9), then the rollback window                                                                                                                                                                                                                                                                                                          | No NVA call reaches Cristin. After the window, Cristin closed and its credentials removed.                                                                                           |
| 10    | EKS: `app-server` image, Helm chart, same tests                                                                                                                                                                                                                                                                                                                 | Same acceptance suite green on EKS.                                                                                                                                                  |

Phase 2 before Phase 4 is deliberate: the identity-service contract is four request shapes
[10.3] and proves the auth, NIN encryption and aggregate design on the smallest surface.

## 11. Risks and open questions

| Risk or question                                                                                        | Mitigation or who decides                                                                                                                                                           |
| ------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Read access to Cristin's database, and its schema, are assumed by D2.                                   | Confirm access and engine before Phase 0 starts. The mapping in section 9 step 1 is the real work; the copy is not.                                                                 |
| Neither replaced service provides a VPC (D14).                                                          | Phase 0 reuses the network the team already runs for Neptune or builds a stack on the same pattern. Egress for the JWKS fetch is part of it.                                        |
| Per-branch stacks with a database.                                                                      | Shared dev cluster with a schema per branch (section 7.1). Revisit if provisioning time or isolation becomes a problem.                                                             |
| Quarkus with the shared nva Gradle plugins, Error Prone, Spotless and JaCoCo at 100 %.                  | Proven in Phase 0 before any slice is built. Generated classes excluded from coverage.                                                                                              |
| Java cold start on Lambda for "very quick" lookups.                                                     | SnapStart, measured in Phase 0. Native image is the escalation (D10); it needs reflection configuration for Jackson and jOOQ. Provisioned concurrency for the login path if needed. |
| IAM authentication to RDS Proxy from the Agroal pool.                                                   | Credentials provider that refreshes the token; proven in Phase 0.                                                                                                                   |
| HTTP API v2 mapping on the shared REST-era custom domain.                                               | Verify in Phase 0; fall back to a separate domain if it does not attach.                                                                                                            |
| Scope names and the Cognito resource server are owned by the identity team (D12).                       | Agree names in Phase 0; pull request merged before Phase 2.                                                                                                                         |
| Volume: persons, employments and projects in Cristin are large.                                         | Fitness tests run on rehearsal data from Phase 0 on.                                                                                                                                |
| Serverless v2 capacity scaling adds latency on load spikes, and the login path is latency-sensitive.    | Set the production minimum ACU from the fitness run so the working set stays in the buffer cache; raise it or move to provisioned instances only on measured evidence (D15).        |
| Local PostgreSQL and Aurora diverge in some behaviour (planner, extensions versions, failover).         | Keep the schema to community features; pin the same major version locally; the fitness tests on Aurora catch the rest.                                                              |
| Consumers of `units-norway.json` in other services.                                                     | Search the NVA repositories for the SSM parameter name before Phase 1 ends.                                                                                                         |
| Undocumented biobank query parameters [8, 9].                                                           | Look for callers; drop if none.                                                                                                                                                     |
| Cut-over freeze blocks NVA write paths for the weekend.                                                 | Agree fail-or-queue with the NVA teams; announce the window.                                                                                                                        |
| Rollback window length after cut-over.                                                                  | Agree with the Cristin owners before Phase 9.                                                                                                                                       |
| Personal data lifecycle (retention, erasure) once nva-uac is system of record.                          | Owner to be named; rules decided before Phase 5 adds person writes. Not in section 3 until then.                                                                                    |
| Who may create persons with NIN, and the "person is caller" rules [4.6], stay in the NVA services (D3). | Document the scope-to-role mapping in the OpenAPI description so both sides agree.                                                                                                  |
| PostgreSQL extensions on all three targets (section 5.2).                                               | Confirm `ltree` and full-text search are available on the EKS and private-cloud databases before Phase 1.                                                                           |

## 12. Definition of done for the whole project

- Every capability in section 3 has acceptance tests, including the negative paths.
- The migration has been rehearsed to a clean reconciliation, run once in the cut-over window,
  and the rollback window has passed. Cristin is closed.
- `nva-cristin-service` and `nva-identity-service` run against nva-uac in production with the
  Cristin credentials removed.
- 100 % method and class coverage, static analysis clean, OpenAPI lint clean.
- The same acceptance suite passes locally, on Lambda and on EKS.

## 13. Working method and pull-request plan

The project is high priority, and it is delivered so that everyone involved understands the
intention, the code and its deployment at every step. Speed comes from small, reviewable
increments, not from large drops.

### 13.1 Rules

- **Stacked pull requests of at most 250 changed lines** where possible. The count is
  hand-written lines added plus removed, tests included; generated code, lock files and
  vendored rulesets are excluded. Where a change cannot be split (a Flyway migration, a SAM
  template section), the pull request says so in its description and stays alone in its layer.
- **Every pull request carries its tests.** Test-first still holds inside a pull request: the
  failing acceptance or unit test and the code that satisfies it ship together, because the
  coverage gate would otherwise fail on the intermediate commit. Split by capability, never by
  separating tests from production code.
- **One pull request, one intent.** The description follows a fixed template: _Intent_ (the
  decision or section of this plan it serves), _Change_, _How to verify locally_, _Deployment
  impact_, _What the reviewer should look at first_. Stacked pull requests link to their base
  and to the next one in the stack.
- **Understanding is a deliverable.** Port interfaces carry JavaDoc describing their contract.
  Each module has a short README stating what it is for and what it may depend on. Decisions
  that change or refine section 2 are recorded as an ADR under `docs/adr` in the same pull
  request as the code that implements them.
- **Deployment is reviewed like code.** Template and pipeline changes come in their own pull
  requests, with the resulting resources listed in the description. Every pull request that
  touches `infrastructure` is deployed to a branch stack before review.
- **Phase boundaries are communication points.** Each phase opens with a short written intent
  and closes with a demo against the dev stack and a written result against the exit criterion
  in section 10. The Phase 0 result is an ADR, since it settles D10 and D14.
- **Merge order is the stack order.** A pull request is rebased on its base after review
  changes; the stack is merged bottom-up. Tooling for stacks (`gh`, or a stack helper) is the
  author's choice; the rules above are not.

### 13.2 Phase 0 as a stack

The stack as built, in merge order. Every branch builds green on its own with every gate on.
Where a planned row became several pull requests the budget decided it; where one exceeds the
budget the description says why. Sizes are hand-written lines added.

| #   | Branch                               | Contents                                                                                                                           | Size |
| --- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- | ---- |
| 1   | `phase0/01-bootstrap`                | README, pull-request template, CODEOWNERS, ADR 0001                                                                                | 116  |
| 2   | `phase0/02-gradle-foundation`        | shared nva plugins and catalogs, Gradle 9.8.1, empty `core`, gates at full strength; ADR 0002 for Java 25; a formatter-only commit | 158  |
| 3   | `phase0/04-unit-identifier`          | `UnitIdentifier`, `InvalidIdentifierException`                                                                                     | 208  |
| 4   | `phase0/05-language-tag`             | `LanguageTag`, `LanguageTaggedText`, `InvalidValueException`                                                                       | 181  |
| 5   | `phase0/06-labels`                   | `Labels`                                                                                                                           | 167  |
| 6   | `phase0/07-unit`                     | `Unit` with the hierarchy invariants                                                                                               | 192  |
| 7   | `phase0/08-pmd-ruleset`              | project PMD ruleset: ports are not functional interfaces                                                                           | 45   |
| 8   | `phase0/09-find-unit`                | `FindUnit`, `UnitRepository`, the use case, in-memory fixtures                                                                     | 181  |
| 9   | `phase0/10-http-adapter`             | Quarkus conventions, `GET /organisations/{identifier}`, acceptance tests                                                           | 257  |
| 10  | `phase0/11-problem-json`             | RFC 9457 problems for 400 and 404                                                                                                  | 166  |
| 11  | `phase0/12-openapi`                  | the authored contract, Spectral, the route-versus-contract test                                                                    | 196  |
| 12  | `phase0/13-oidc`                     | OIDC, scope-to-role mapping, 401 and 403, security in the contract                                                                 | 298  |
| 13  | `phase0/14-persistence-schema`       | Flyway `V1`, jOOQ generation from the migrations, 20 constraint tests                                                              | 331  |
| 14  | `phase0/15-unit-repository`          | `PostgresUnitRepository` with its tests                                                                                            | 223  |
| 15  | `phase0/16-library-config`           | library configuration location, test fixtures, `test-support`                                                                      | 83   |
| 16  | `phase0/17-assembly`                 | the wiring, Flyway, health, the end-to-end walking skeleton test                                                                   | 143  |
| 17  | `phase0/18-app-lambda`               | the Lambda packaging with SnapStart, tests through the event path                                                                  | 86   |
| 18  | `phase0/19-app-server`               | the server packaging with Jib, random test ports                                                                                   | 84   |
| 19  | `phase0/20-infrastructure-data-tier` | Aurora Serverless v2, RDS Proxy, KMS, security groups, SSM                                                                         | 268  |
| 20  | `phase0/21-infrastructure-api-tier`  | Lambda, HTTP API v2 with JWT authoriser, domain mapping, alarms                                                                    | 246  |
| 21  | `phase0/22-pipeline`                 | buildspecs, Flyway migration step in the VPC, GitHub workflows                                                                     | 213  |
| 22  | `phase0/23-fitness-harness`          | the measurement script and report                                                                                                  | 191  |
| 23  | `phase0/24-record-the-stack`         | this table and the README build section                                                                                            | docs |
| 24  | `phase0/25-migration-loader`         | staging tables and the validating unit loader                                                                                      | 354  |
| 25  | `phase0/26-migration-reconciliation` | the reconciliation report                                                                                                          | 257  |
| 26  | `phase0/27-migration-runner`         | the rehearsal step: load, reconcile, report; its buildspec                                                                         | 208  |
| 27  | `phase0/28-migration-consistency`    | both directions of the hierarchy staged and reconciled; ADR 0004                                                                   | 190  |
| 28  | `phase0/34-pipeline-codepipeline`    | delivery on the NVA CodePipeline, the migration projects in the stack, ADR 0005                                                    | 595  |

Still to do in Phase 0, each waiting on something outside the repository:

| Item                                                                                                                                            | Waits on                                                                                     |
| ----------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Deploy the data tier to dev, register the repository in the master pipelines of `NVA-infrastructure`, and let the pipeline deploy once          | an AWS account and the VPC answer (D14)                                                      |
| The extract of units from Cristin into the staging tables, both directions of the hierarchy included; the load and the reconciliation are built | read access to Cristin's database                                                            |
| Least-privilege database user and the IAM credentials provider for the proxy                                                                    | the first deployment                                                                         |
| The fitness run and the Phase 0 record (ADR) settling D10, D12 and D14                                                                          | the deployment and the scope agreement with the identity team                                |

### 13.3 Pattern for every later slice

Each slice in section 10 repeats the same shape, so reviewers always know where they are:

1. **Core** pull requests: model and ports with JavaDoc, use-case tests through the driving port
   with in-memory driven ports, unhappy paths first.
2. **Persistence** pull requests: migration alone, then repository with Testcontainers tests.
3. **HTTP** pull requests: one resource or one group of closely related routes per pull request,
   OpenAPI and acceptance tests included.
4. **Infrastructure and pipeline** pull requests, if the slice needs any, deployed to a branch
   stack before review.
5. **Migration** pull request for the slice's entity types, with its reconciliation tests.
6. **Client switch** pull request in `nva-cristin-service` or `nva-identity-service`, behind
   configuration, with its own tests, reviewed by that service's owners.
7. **Slice result** note against the exit criterion, and the demo.
