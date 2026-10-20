# assembly

Wires the adapters into the use cases: the jOOQ context over the Agroal data source, the
PostgreSQL repositories, and the use cases of `core` that the HTTP adapter injects. Adds the data
source, Flyway and health. Both application modules depend on this and add only their packaging. A `META-INF/beans.xml`
makes this jar a bean archive, so Quarkus discovers its producers from a dependency; the HTTP
adapter carries one for the same reason.

Flyway runs at start only in dev and test mode; deployed environments migrate in the pipeline
before traffic is shifted. The test here is the walking skeleton end to end: a token, the HTTP
adapter, the use case, and a PostgreSQL started by Dev Services.
