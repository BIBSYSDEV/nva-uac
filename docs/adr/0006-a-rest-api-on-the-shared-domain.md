# ADR 0006: A REST API on the shared domain

Date: 2026-11-24

Status: Proposed

## Context

All NVA APIs share one host, `api.nva.unit.no` in production and
`api.<environment>.nva.aws.unit.no` elsewhere: an API Gateway custom domain defined in
`nva-common-resources`, onto which each service maps a path prefix such as `/cristin`. D7 planned
to map an HTTP API (v2) there under `/uac`, and section 11 asked Phase 0 to confirm that the
mapping attaches, with a separate domain as the fallback.

A custom domain is either edge-optimized, fronted by a CloudFront distribution that AWS manages,
or Regional, living in the region with any CloudFront in front being one's own. The shared domain
is edge-optimized. REST APIs (v1) can be mapped onto either kind, but the API Gateway
documentation allows HTTP APIs only on Regional domains, so the mapping D7 planned is not
possible. HTTP APIs also lack AWS WAF support, which REST APIs have. The other NVA services are
REST APIs with Regional endpoints on the shared domain, and REST APIs now offer a JWT authoriser
like the one D7 chose.

The alternatives considered:

- An HTTP API on a Regional domain of its own, the plan's fallback: nva-uac becomes the only NVA
  API outside the shared domain, and Phase 0's exit criterion asks for a request on that domain.
- Our own CloudFront in front of an HTTP API: the most to build and run.
- Turning the shared domain Regional: every NVA API changes for the sake of one.
- A REST API with the Cognito user pool authoriser the other services use: it answers a missing
  scope with 401, where the contract promises 403, and checks less than D7's authoriser does.

## Decision

On AWS, nva-uac is a REST API with a Regional endpoint, mapped onto the shared domain under `uac`
by a base path mapping, as the other NVA services are. It keeps D7's way of authorising, now with
the REST API's JWT authoriser on the NVA user pool and the backend client. The gateway's single
proxy route accepts any of the contract's scopes; Quarkus OIDC validates every token and enforces
each operation's own. No API Gateway cache is enabled, and the Lambda packaging uses Quarkus's
Amazon Lambda REST extension.

This supersedes the API Gateway type and the Lambda extension in D7. Quarkus REST, one set of
resources for all targets, and the plain Quarkus HTTP server on EKS, private cloud and locally
stand.

## Consequences

- `template.yaml` gets an `AWS::Serverless::Api` with a JWT authoriser and a base path mapping,
  and the alarms and the access log move to the REST API's metrics and variables. The JWT
  authoriser for REST APIs is new, so whether SAM and cfn-lint support it is checked first.
- `app-lambda` swaps its Quarkus extension and sends REST events in its tests. `core`,
  `adapter-http`, `app-server` and the URLs in the contract are unchanged.
- `fitness/` reads the REST API's latency metrics, and the fitness run shows what the edge
  domain's CloudFront adds to the latency budget.
- A REST API costs more per request than an HTTP API.
- AWS WAF can be attached to the API should it ever need it.
