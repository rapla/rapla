# PRD 080 — Typed-entity stats across entity families

**Status:** implemented (2026-06-21) — all 8 items built + tested (item 4 verify-live-only; see Status block). Extends [PRD 079](079-graphql-grouped-aggregates.md) (generic grouped
aggregates) with **typed, selectable group entities** and **per-family stats fields**. Carved out of a
long design discussion: the generic `keys/values` bucket couldn't carry entity fields (e.g. room size),
and aggregation existed only on `appointmentBlockStats`.

## Problem

`appointmentBlockStats` returns generic buckets — `BlockStatBucket { keys:[StatKey{key,value}], values:[StatValue], count }`.
1. You **can't select fields of the grouped entity** (room's `AnzahlPlaetzeInsgesamt`, building address, …) — only the display string. Forces a client-side join.
2. Aggregation is **only** on blocks; no `allocatableStats` / `reservationStats`.

## Decision: X (generic result + typed group entity), not Y (fixed typed reports)

Two ways to get typed/selectable grouping were weighed:
- **Y — fixed typed reports** (`roomUtilization { room: Allocatable, minutes, count }`): fully typed named
  fields, BUT the **grouping is baked into the schema** → no ad-hoc grouping, one field+type per analysis
  (combinatorial field proliferation), a server deploy per new view. **Rejected** as the primary model.
- **X — generic buckets + the group entity is a typed, selectable object** (chosen): keep ad-hoc
  `groupBy`/`aggregate`; the bucket key carries the **real entity** so any of its fields are selectable.
  Keeps query-time flexibility AND solves field-selection. Only the `keys/values` *wrapper* stays generic.

## The shape

```graphql
union StatEntity = Allocatable | Reservation | Category   # the meaningful group entities

type StatKey {
  key:    String!        # the groupBy "key" name
  value:  String!        # display string (always)
  entity: StatEntity     # NEW: the typed group entity — null for time/scalar dimensions
}
```
`StatKey.entity` is **per-dimension** (per key), populated only for entity-resolving dimensions; null for
`date`/`expr`-string dimensions. `StatValue`/`BlockStatBucket` (`keys`/`values`/`count`) stay shared + generic.

Example — Mosbach utilization **with room size, no join**:
```graphql
appointmentBlockStats(
  groupBy:   [ { key:"raum", allocatables:{ typeKeyIn:["Raum"], whereRaum:{ Gebaeude:{ where:{ Gebaeudename:{ startsWith:"MOS" } } } } } } ],
  aggregate: [ { key:"stunden", field:DURATION_MINUTES, fn:SUM } ]
) {
  keys { value entity { ... on Allocatable { classification { ... on RaumClassification { AnzahlPlaetzeInsgesamt } } } } }
  values { key number } count
}
```

## Why these three entities (and not Appointment)

| Group by | Entity | Why |
|---|---|---|
| Event | **Reservation** | has name/identity; "hours/sessions per course" |
| Resource (room/lecturer/building) | **Allocatable** | named; utilization per resource |
| Category attribute (Studiengang/Raumart/…) | **Category** | named/keyed |
| Time bucket / arbitrary scalar | — (`value` only) | no entity |
| ~~Appointment~~ | — | internal recurrence container, no fachliche identity → not a group entity |

## Scope (items)

1. **`StatKey.entity: StatEntity`** union + graphql union `TypeResolver`; resolver populates it.
2. **`appointmentBlockStats`**: the `allocatables` dimension fills `entity` (Allocatable).
3. **Reservation dimension** in `BlockGroupKey` (`reservation: true`) → group blocks by their Reservation; `entity` = Reservation. ("hours/sessions per Veranstaltung")
4. **Category dimension** (group by a category attribute → `Category`) — feasibility TBD.
5. **`expr → Entity`**: evaluate a group `expr` to an OBJECT (not `formatName` string); if Allocatable/Reservation/Category → populate `entity` (list ⇒ fan-out). Enables **navigated** grouping (rooms by their building). Needs a raw-object eval mode + EL navigation — verify.
6. **`allocatableStats`** — own `AllocatableGroupKey` (attribute/reference/type; no time) + `AllocatableMetricField`. ("Plätze pro Gebäude")
7. **`reservationStats`** — own `ReservationGroupKey` (reservation type/date/allocatable) + `ReservationMetricField`.
8. **Shared generic result** (`StatKey`/`StatValue`/bucket) across all three families.

## Invariants

- **§12:** the group entity is always `canRead`-gated before exposure (it comes from the §12 resolver / `filterAllocatables`); recursive reference resolution (PRD 074 b) already gates.
- **Genericity preserved:** ad-hoc `groupBy`/`aggregate` stays; no fixed-report deploy per analysis.
- **One generic result** over all families — only the per-family `…GroupKey` + `…MetricField` differ.

## Status (2026-06-21)

- ✅ **1 — `StatKey.entity: StatEntity`** union + `TypeResolver` (Allocatable/Reservation/Category) — wired in `GeneratedClassificationWiring`.
- ✅ **2 — `appointmentBlockStats` allocatables → entity** (the room object is selectable; tested: `entity.displayName == key.value`, `__typename == Allocatable`).
- ✅ **3 — reservation dimension** (`BlockGroupKey.reservation: true` → entity = Reservation; tested via `__typename`).
- ✅ **8 — shared generic result** (`StatKey`/`StatValue`/`BlockStatBucket`) — reused as-is. 55 GraphQL tests green; legacy EL green.
- ✅ **6 — `allocatableStats`** — new `@QueryMapping` over the §12-visible allocatable population
  (reuses `Query.allocatables` gating + full `AllocatableFilter`); `AllocatableGroupKey {type|expr|self}`
  + `AllocatableAggregate {expr, fn}`. `self` carries the Allocatable as `StatKey.entity`. COUNT needs
  no expr; SUM/MEAN/MIN/MAX coerce a numeric `expr` (e.g. capacity). Shares `BlockStatBucket`. 2 tests.
- ✅ **7 — `reservationStats`** — new `@QueryMapping` over the §12-visible reservation set in the
  window (reuses `reservations()`); `ReservationGroupKey {type|expr|self}` + `ReservationAggregate`.
  `self` carries the Reservation as `entity`. Shares `BlockStatBucket`. 2 tests. **59 GraphQL tests green.**
- ✅ **expr generalized** — `StructuralTypeFetchers.computeEntityExpr(entity, expr, user)` now drives
  blocks/allocatables/reservations (via `ParsedText.guessClassification`); `computeBlockExpr` is a shim.
- ✅ **5 — `expr → Entity`** — `ParsedText.evalToObject(ctx)` (new public eval-to-object) +
  `StructuralTypeFetchers.computeEntityExprObject`; the controller's `exprDimVals` detects
  Allocatable/Reservation/Category results (and Collections → fan-out), carries them as
  `StatKey.entity`, and §12-canRead-gates each (hidden entity dropped, **no name leak** — no string
  fallback on the entity path). Non-entity exprs keep the legacy `formatName` string key. Wired into
  all three families (block/allocatable/reservation expr dimensions). Test: `expr:"resources(item)"`
  → Allocatable entities. **61 GraphQL tests green.**
- ✅ **4 — category dimension** — achieved via item 5 (no dedicated field, per the generic-X decision):
  `expr:"attribute(item,\"<categoryAttr>\")"` returns a `CategoryProxy` (implements `Category`) →
  `StatKey.entity` resolves to the `Category` GraphQL type, selectable (`... on Category { name key }`).
  Multi-select category attrs fan out. (Not unit-tested: `testdefault.xml` has no category attribute;
  verify live against a deployment that does.)

## Build order

1 → 2 → 3 (block: entity + reservation dim) → 6 → 7 (new families) → 4 / 5 (category, expr→entity). Verify each (live against dhbw for the entity/§12 paths; fixture lacks reference/entity attributes).

## Tests / debt

- Union `TypeResolver` resolves Allocatable/Reservation/Category correctly.
- §12: a hidden group entity ⇒ bucket dropped / entity not exposed.
- Carries over from PRD 074 b: a fixture with a reference attribute for the reference-recursion + §12-leak regression test (unit fixture currently has none).

## Out of scope

Stufe c (EL number-model / in-expr arithmetic, PRD 073); the ComputeFunctions catalog (PRD 073);
fixed typed reports (Y).
