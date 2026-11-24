# ADR 0006: A REST API on the shared domain

Date: 2026-11-24

Status: Proposed

## Context

All NVA APIs share one host, `api.nva.unit.no` in production and
`api.<environment>.nva.aws.unit.no` elsewhere: an API Gateway custom domain defined in
`nva-common-resources`, onto which each service maps a path prefix such as `/cristin`. D7 planned
to map an HTTP API (v2) there under `/uac`, and section 11 asked Phase 0 to confirm that the
mapping attaches. It does not.

A custom domain is either edge-optimized, fronted by a CloudFront distribution that AWS manages,
or Regional, living in the region with any CloudFront in front being one's own. The shared domain
is edge-optimized. REST APIs (v1) can be mapped onto either kind, but API Gateway maps HTTP APIs
only onto Regional domains. HTTP APIs also lack AWS WAF support, which REST APIs have. The other
NVA services are REST APIs with Regional endpoints on the shared domain.

The alternatives considered:

- An HTTP API on a Regional domain of our own: nva-uac becomes the only NVA API outside the
  shared domain, with a certificate and a DNS record of its own.
- Our own CloudFront in front of an HTTP API: the most to build and run.
- Turning the shared domain Regional: every NVA API changes for the sake of one.

## Decision

On AWS, nva-uac is a REST API with a Regional endpoint, mapped onto the shared domain under `uac`
by a base path mapping, as the other NVA services are. API Gateway authorises with a Cognito user
pool authoriser requiring the scopes the contract lists, Quarkus OIDC still validates every token,
no API Gateway cache is enabled, and the Lambda packaging uses the Amazon Lambda REST extension.
This supersedes the API Gateway part of D7; the rest of D7 stands.

## Consequences

- `template.yaml` gets an `AWS::Serverless::Api`, a base path mapping and alarms on the REST
  metrics; `app-lambda` swaps its Quarkus extension and sends REST events in its tests.
- `core`, `adapter-http` and the URLs in the contract are unchanged.
- A REST API costs more per request, and calls pass through the edge domain's CloudFront; the
  fitness run shows what that adds to the latency budget.
- AWS WAF can be attached to the API should it ever need it.
