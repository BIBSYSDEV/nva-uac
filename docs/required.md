# Requirements

## Upfront

The target is

- initially, AWS Lambda
- secondly, some EKS-based runtime
- thirdly, probably some private cloud

The capabilities shall be replacement of the service that provides data to the NVA services listed below:

- nva-cristin-service
- nva-identity-service

The source of these services is on GitHub, read at `main` as of 14 October 2026:
[BIBSYSDEV/nva-cristin-service](https://github.com/BIBSYSDEV/nva-cristin-service) and
[BIBSYSDEV/nva-identity-service](https://github.com/BIBSYSDEV/nva-identity-service).

The file [cristin-upstream-usage.md](cristin-upstream-usage.md) defines what is actually used by the APIs defined in the git archives, and it is these that we we be re-implementing.

## Commandments

- The service being replaced is basically irrelevant, we are only interested in the exposed functional surface expressed in the two referenced git repositories.
- The backend is a relational database, but we implement a repository interface so the storage can be replaced
- The application behaviour shall be tested (and verified that coverage is 100%)
- The testing methodology shall be BDD-style functional/acceptance tests
- The system is microservices, and we start with simple deployment in AWS using SAM
- Secrets and information such as NINs will be encrypted at rest and in transport
- We modernize and simplify where we can
- If using RDS, we use RDS proxy
- It should be possible to spin the service up locally
- We should use OAuth2 for m2m authn
- The lookups should be very quick
- Where necessary, we employ transactions from aggregates rather than aggregates leading to n-HTTP calls
