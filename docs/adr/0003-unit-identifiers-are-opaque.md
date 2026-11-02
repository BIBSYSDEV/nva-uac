# ADR 0003: Unit identifiers are opaque

Date: 2026-10-19

Status: Accepted

## Context

Cristin unit identifiers look structured: `185.15.2.10` reads as institution 185, faculty 15,
department 2, group 10, and an identifier ending in `.0.0.0` reads as the institution itself.
The first version of the unit model read it that way: it derived the institution from the first
number, treated `x.0.0.0` as a root without a parent, and required every other unit to have a
parent sharing its first number. The schema and the migration loader enforced the same.

The data says otherwise. The top unit of the University of Oslo is `185.90.0.0`, with 43 parts,
and `185.0.0.0` is an ordinary unit that names `185.90.0.0` as its parent (though `185.90.0.0`
does not list it back; ADR 0004 is about that). Cristin ties a unit to its
institution by data (`institution.cristin_institution_id`) and the institution to its top unit
by data (`corresponding_unit`). The old NVA code validates only the form, four dot-separated
numbers, and reads nothing else from it.

## Decision

A unit identifier is an opaque value with a strict form and no structure. The hierarchy of
units is the `partOf` relation and nothing else. A unit without a parent is a root; which root
is an institution's top unit is data, to be stored when institution resolution is built. No
code, constraint or query derives a parent, an institution or a level from the segments.

## Consequences

`UnitIdentifier` holds the dotted string and validates its form only. `Unit` requires a parent
other than itself when it has one, and nothing about where it sits. The schema checks the form
and that a unit is not its own parent. The migration loader orders inserts by the parent
relation, not by the segments. The OpenAPI descriptions say the same. Institution resolution for
the login path, in Phase 1, stores each unit's institution as a column filled from the data.
