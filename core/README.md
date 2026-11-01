# core

The domain model and the ports, as plain Java. Packaged by bounded context (organisation,
person, project, reference data, biobank) with a shared package for identifiers,
language-tagged text, paging and errors.

Depends on nothing outward: no Quarkus, no I/O, no serialisation. Adapters depend on this
module and implement its driven ports; the application modules wire them together.
