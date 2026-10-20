# assembly

Wires the adapters into the use cases: the jOOQ context over the Agroal data source, the
PostgreSQL repositories, and the use cases of `core` that the HTTP adapter injects. Adds the data
source, Flyway and health. Both application modules depend on this and add only their packaging.

Flyway runs at start only in dev and test mode; deployed environments migrate in the pipeline
before traffic is shifted. The test here is the walking skeleton end to end: a token, the HTTP
adapter, the use case, and a PostgreSQL started by Dev Services.
