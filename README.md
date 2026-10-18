# nva-uac

The research-information data behind NVA: persons, employments, the organisation hierarchy,
projects, biobanks and their reference lists. nva-uac replaces the Cristin API as the data
provider for `nva-cristin-service` and `nva-identity-service`, and after cut-over it is the
system of record.

## Intent

- [Requirements](docs/required.md): what was asked for, and the commandments that bound it.
- [Upstream usage](docs/cristin-upstream-usage.md): which Cristin calls the two NVA services
  actually make, and how. Everything in scope traces back to a section of this document.
- [Plan](PLAN.md): decisions (section 2), scope (3), architecture (4), data model (5),
  security (6), infrastructure (7), quality gates (8), migration and cut-over (9), delivery
  phases (10), risks (11), definition of done (12) and working method (13).
- [Decision records](docs/adr): changes to the decisions in the plan, one record each.

## In one paragraph

A Quarkus application over PostgreSQL, built as ports and adapters with a framework-free core,
deployed first to AWS Lambda behind an API Gateway HTTP API, later as the same image on EKS and
on a private cloud. Data is migrated from Cristin's database in rehearsals until the acceptance
and fitness tests pass, then once for real in a closed weekend window.

## Status

Phase 0, the walking skeleton. See the plan, section 10, for the phases and section 13.2 for
the pull requests that make up this phase.

## How work is delivered

Stacked pull requests of at most 250 hand-written lines where possible, each carrying its own
tests, each describing its intent, its verification and its deployment impact. The rules are in
the plan, section 13.1, and the pull-request template enforces the description.

## Building and running

Not yet. The Gradle build arrives in the next pull request of the Phase 0 stack.
