# adapter-http

The driving HTTP adapter: Quarkus REST resources, their representations, problem+json
errors and the OpenAPI contract in `src/main/resources/openapi`. The contract is authored
first, linted with Spectral by the build, and a test proves the registered routes match it. It
depends on `core` and on nothing else of ours.

It carries no production wiring. The application modules produce the use cases this adapter
injects; the tests here produce them over the in-memory fixtures of `core`.
