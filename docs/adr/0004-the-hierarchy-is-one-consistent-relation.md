# ADR 0004: The hierarchy is one consistent relation

Date: 2026-10-19

Status: Accepted

## Context

Cristin serves the hierarchy of units in two directions from two different fields: each unit
names its parent, and each parent lists its parts. The old NVA API builds `partOf` from the
first and `hasPart` from the second, and the two can disagree. `185.0.0.0` names `185.90.0.0`,
the top unit of the University of Oslo, as its parent, while the parts of `185.90.0.0` do not
include `185.0.0.0`. Served as it stands, the unit is a child looking up and an orphan looking
down.

## Decision

The hierarchy is one relation, `partOf`, stored once on the child. `hasPart` is its inverse and
nothing else, so the result is a forest: every unit has at most one parent, no cycles, and
every edge reads the same from both ends.

The extract stages both of Cristin's directions, and the rehearsal fails when they disagree: a
unit whose claimed parent does not list it, or a listed part that claims another parent or none.
A disagreement is a data error to be resolved with Cristin before the migration, never modelled,
never silently resolved by preferring one direction.

## Consequences

The staging schema gains `unit_part` for the parent-to-child direction, and the extract must fill
it. The reconciliation report lists both kinds of disagreement and is not clean while either
exists. The loader keeps its guards against unstaged parents and cycles. The first extract will
show how many units Cristin disagrees with itself about, and that count goes into the Phase 0
record.
