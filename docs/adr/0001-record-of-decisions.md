# ADR 0001: The plan's decision table is the record of decisions

Date: 2026-10-18

Status: Accepted

## Context

nva-uac is delivered in small, stacked pull requests by a team that must understand the
intention behind every change. The decisions that shape the system were taken while writing
[PLAN.md](../../PLAN.md) and live in its section 2 as a single table, D1 to D15. Decisions
marked _Decided_ were taken by the product owner; decisions marked _Assumed_ hold until someone
overrides them.

## Decision

Section 2 of the plan is the authoritative register of decisions. This directory records the
history of that register:

- Any pull request that changes, adds or retires a decision in section 2 includes a new record
  here, numbered sequentially, stating the context, the decision and its consequences.
- A record never rewrites an earlier record. It supersedes it and says so.
- The end of each phase in section 10 of the plan produces a record with the measured results
  against the phase's exit criterion. The Phase 0 record settles D10, D12 and D14.

The decisions as of this record:

| #   | Decision                                                                                                                      | Status  |
| --- | ----------------------------------------------------------------------------------------------------------------------------- | ------- |
| D1  | New, clean API; the NVA clients are adapted                                                                                   | Decided |
| D2  | Migrate from Cristin's database with SQL tools, rehearsed until tests pass, cut over once in a closed weekend; Cristin closed | Decided |
| D3  | User-level authorisation stays in the NVA services                                                                            | Decided |
| D4  | Cristin identifiers remain the public identifiers                                                                             | Decided |
| D5  | Search and facets in PostgreSQL until measurement proves otherwise                                                            | Decided |
| D6  | jOOQ, or another immutable-by-default library, on the Quarkus datasource stack                                                | Decided |
| D7  | Standard Quarkus HTTP: Lambda HTTP extension behind HTTP API v2 on AWS, plain server elsewhere                                | Decided |
| D8  | Person pictures in object storage                                                                                             | Decided |
| D9  | Multilingual labels as rows                                                                                                   | Decided |
| D10 | Quarkus; JVM with SnapStart on Lambda, native only if the budget is missed                                                    | Assumed |
| D11 | Jena for ontology and tests only; Titanium or direct serialisation for JSON-LD                                                | Assumed |
| D12 | Existing backend scope first; own scopes registered before Phase 2                                                            | Assumed |
| D13 | One deployable with five bounded-context modules                                                                              | Assumed |
| D14 | Lambda in a VPC with RDS Proxy; Phase 0 settles the network                                                                   | Assumed |
| D15 | Aurora Serverless v2 everywhere on AWS, symmetric environments; local PostgreSQL for all automated tests                      | Decided |

## Consequences

Readers find the current decisions in one place and their history in another. A decision
change is visible in the pull request that makes it, alongside the code it affects.
