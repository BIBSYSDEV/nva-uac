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
REST APIs with Regional endpoints on the shared domain.

D7's JWT authoriser was a second check in front of Quarkus OIDC (section 6), which validates
every token and each operation's scopes anyway, and is the only check on EKS and private cloud.
REST APIs now offer a JWT authoriser too, but AWS SAM does not support it yet and neither does the
cfn-lint version CI runs. The Cognito user pool authoriser the other services use answers a
missing scope with 401, where the contract promises 403.

The alternatives considered:

- An HTTP API on a Regional domain of its own, the plan's fallback: nva-uac becomes the only NVA
  API outside the shared domain, and Phase 0's exit criterion asks for a request on that domain.
- Our own CloudFront in front of an HTTP API: the most to build and run.
- Turning the shared domain Regional: every NVA API changes for the sake of one.

## Decision

On AWS, nva-uac is a REST API with a Regional endpoint, mapped onto the shared domain under `uac`
by a base path mapping, as the other NVA services are. The gateway has no authoriser: Quarkus
OIDC validates every token, enforces each operation's scopes, and checks the calling client,
which the gateway did before. No API Gateway cache is enabled, and the Lambda packaging uses
Quarkus's Amazon Lambda REST extension.

This supersedes the API Gateway type, the JWT authoriser and the Lambda extension in D7, and the
second check in section 6. Quarkus REST, one set of resources for all targets, and the plain
Quarkus HTTP server on EKS, private cloud and locally stand.

## Consequences

- `template.yaml` gets an `AWS::Serverless::Api` without an authoriser and a base path mapping,
  and the alarms and the access log move to the REST API's metrics and variables.
- The OIDC configuration gains the check on the calling client.
- Requests without a valid token reach the function and are refused there, so they cost an
  invocation. A gateway authoriser can be added again once SAM supports one that fits.
- `app-lambda` swaps its Quarkus extension and sends REST events in its tests. `core`, `app-server`
  and the URLs in the contract are unchanged.
- `fitness/` reads the REST API's latency metrics, and the fitness run shows what the edge
  domain's CloudFront adds to the latency budget.
- A REST API costs more per request than an HTTP API.
- AWS WAF can be attached to the API should it ever need it.
