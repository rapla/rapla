# PRD 059 — GraphQL Typed Where Predicates on `allocatables(filter:)`

**Status:** done — all 5 phases landed 2026-05-29. Extracted from PRD 035 §5d on 2026-05-29; design locked.

**Date:** 2026-05-29

**Parent:** [PRD 035 (done) — GraphQL foundations](done/035-graphql-foundations.md) §"Filter & query language" for the broader filter-axis model this PRD extends.

**Siblings:**
- [PRD 055 — Events Read API](055-graphql-events-read-api.md) — establishes the typed-classification interface pattern this extends to filtering (reopened 2026-05-29 for Tier-1 perf migration)
- Future: same evaluator could apply to `reservations(filter:)` and to nested `AllocatableWhere` — both deferred (see Out of scope)

## Goal

Server-side per-attribute filtering on `allocatables(filter:)` queries. Today every "find rooms with >=100 seats and a projector" query falls back to `allocatables(filter: { typeKeyEq: "Raum" }) { ... }` plus client-side filtering — ~250 rows on the wire and ~250x the §12 permission check, per call. This PRD shrinks the wire payload by 10-100x for these common patterns and shifts CPU from client to server.

The static `*Where` inputs (`StringWhere`, `IntWhere`, `BooleanWhere`, `LocalDateTimeWhere`, `CategoryWhere`, `CategoryListWhere`, `AllocatableWhere`, `AllocatableListWhere`) already live in `schema.graphqls`. The dynamic per-type / per-enum `<TypeKey>Where` inputs are emitted by `ClassificationSdlGenerator` as of Phase 1 (2026-05-29). The remaining work wires the predicate evaluator and the `where<TypeKey>` fields on `AllocatableFilter`.

## Scope

In:
- Dynamic SDL generation of `<TypeKey>Where`, `<EnumName>Where`, `<EnumName>ListWhere` inputs per resource/person DynamicType and per VALUE_LIST root.
- `where<TypeKey>` fields on `AllocatableFilter` via SDL type extension, one per generated `AllocatableClassification` implementor.
- `WhereEvaluator` walking the where map and dispatching predicates against `Classification.getValue(attribute)`.
- Combinators `AND`, `OR`, `NOT` with depth cap 10.
- Full operator coverage (`eq`, `ne`, `contains`, `startsWith`, `endsWith`, `in`, `gt`/`gte`/`lt`/`lte`, `between`, `containsAll`, `containsAny`, `isEmpty`, `isNull`).

Out of scope (deferred):
- Nested where on `AllocatableWhere`. Today id-equality only. A recursive type structure (`AllocatableWhere.where: AllocatableWhere`) is a separate PRD if real consumer demand emerges.
- Where on Reservations (`reservations(filter:)`). Same evaluator could apply; separate phase / separate PRD.
- Sort by typed attribute.

## Locked design

The full SDL the generator emits (per dhbw schema for the `Raum` DT; the same shape generalises to any resource/person DT):

```graphqls
# === Schema additions per generated AllocatableClassification implementor ===
# RaumWhere shown — one is generated per DT that is RESOURCE or PERSON kind.
input RaumWhere {
  Raumname:                  StringWhere
  Raumnummer:                StringWhere
  Grundflaeche:              IntWhere
  RollstuhlgerechterZugang:  BooleanWhere
  Raumart:                   raumtypWhere
  AusstattungListe:          ausstattungenListWhere
  Gebaeude:                  AllocatableWhere
  AND: [RaumWhere!]
  OR:  [RaumWhere!]
  NOT: RaumWhere
}

# Per VALUE_LIST enum — generated alongside the existing <Enum> typed enum
input raumtypWhere    { eq: raumtyp  ne: raumtyp  in: [raumtyp!]  isNull: Boolean }
input ausstattungenListWhere {
  contains:    ausstattungen
  containsAny: [ausstattungen!]
  containsAll: [ausstattungen!]
  isEmpty:     Boolean
  isNull:      Boolean
}

# AllocatableFilter gains one where field per resource/person DT
input AllocatableFilter {
  # ... existing scalar fields ...
  whereRaum:     RaumWhere
  whereGebaeude: GebaeudeWhere
  wherePerson:   PersonWhere
}
```

## Predicate semantics

- Multiple non-null operators inside ONE `*Where` AND together (e.g. `Grundflaeche: { gte: 50, lte: 200 }`).
- Multiple typed `where<TypeKey>` fields at the top-level AND with the scalar fields (`typeKeyEq`, `nameContains`, `ownerEq`, `limit`).
- Combinators: `AND: [<TypeKey>Where!]`, `OR: [<TypeKey>Where!]`, `NOT: <TypeKey>Where`. Recursively nestable up to depth 10.
- `typeKeyEq` + `where<TypeKey>` combo: only one `where<TypeKey>` matches at runtime (allocatable belongs to one DT). A `where<TypeKey>` against a non-matching allocatable contributes no constraint; combined with `typeKeyIn:` this enables per-type predicates in a cross-type union.
- `isNull: true` matches the attribute being unset; every other predicate auto-implies "and not null".

## Java wiring

- The dynamic `where<TypeKey>` fields can't fit a Java record. Drop the record, switch `allocatables` to a programmatic `LightDataFetcher` (same pattern as the per-attribute fetchers in `StructuralTypeFetchers`), read the raw argument map, dispatch to a `WhereEvaluator`.
- `WhereEvaluator` walks the where map, looks up the per-attribute predicate operator, applies it to `Classification.getValue(attribute)`. Operators are dispatched by predicate-input type name.
- `ClassificationSdlGenerator` gains `emitWhereInputs(...)` — generates `<TypeKey>Where` per allocatable DT, `<EnumName>Where` / `<EnumName>ListWhere` per VALUE_LIST root, plus the `where<TypeKey>` fields on `AllocatableFilter` via SDL type extension. **Phase 1 landed 2026-05-29.**

## Plan — 5 phases

1. **Phase 1 — SDL generation only.** [DONE 2026-05-29.] Emit `<TypeKey>Where`, `<EnumName>Where`, `<EnumName>ListWhere` inputs in `ClassificationSdlGenerator`. Schema validates; no runtime behaviour change (the `where<TypeKey>` fields aren't wired on `AllocatableFilter` yet). Tier-3 introspection test confirms `roomWhere` shape + per-enum `departmentWhere`/`departmentListWhere`.
2. **Phase 2 — `where<TypeKey>` fields on `AllocatableFilter`.** [DONE 2026-05-29.] SDL emits `extend input AllocatableFilter { whereRoom: roomWhere ... }` per resource/person DT. Resolver switched from record-based `@Argument("filter") AllocatableFilter` to `@Argument("filter") Map<String, Object>` so unknown-to-record `whereXxx` fields don't fail the binder. `evaluateWhere()` is a no-op stub. Tier-3 tests confirm `whereRoom: {...}` is accepted and doesn't change result set.
3. **Phase 3 — Predicate evaluator for one operator per kind.** [DONE 2026-05-29.] `WhereEvaluator.evaluate(...)` walks the `where<TypeKey>` block matching the allocatable's DT key, dispatching per-attribute predicates: StringWhere `eq`/`contains` (case-insensitive)/`startsWith`; IntWhere `gte`/`lte`; BooleanWhere `eq`; CATEGORY-typed (both VALUE_LIST `<Enum>Where.eq` and ORGANIZATION `CategoryWhere.eq` — id-then-key match handles both shapes). `where<OtherType>` against a non-matching DT contributes no constraint. Tier-3 tests confirm each operator filters down to the expected row.
4. **Phase 4 — Combinators AND/OR/NOT.** [DONE 2026-05-29.] Recursive evaluator; depth cap 10. AND vacuously true on empty list; OR vacuously false on empty list; NOT inverts. Tier-3 tests for each + nested + vacuous edge cases.
5. **Phase 5 — Remaining predicates** (`endsWith`, `ne`, `in`, `between`, `gt`/`lt`, `containsAll`/`containsAny`, `isEmpty`, `isNull`) + implicit "and not null" semantics + tier-3 permission-leak test for non-admin filter against a hidden allocatable. [DONE 2026-05-29.] Covers single-valued attrs and multi-select via `*ListWhere` (CATEGORY/ALLOCATABLE).

## Tests

Per-phase tier-3 tests:

- **Phase 1 (done):** Tier-1 SDL string assertion — for a synthesized 3-attribute DT (String, Int, VALUE_LIST), the generated SDL contains `<TypeKey>Where` with the three fields, plus `<EnumName>Where` and `<EnumName>ListWhere` for the enum root.
- **Phase 2:** Tier-3 MockMvc query against the dev dataset asserting that `allocatables(filter: { typeKeyEq: "Raum", whereRaum: {} })` returns the same row count as `allocatables(filter: { typeKeyEq: "Raum" })`. Confirms the resolver switch from record to raw map didn't drop existing scalar-filter semantics.
- **Phase 3:** One tier-3 test per predicate kind — String `eq`, String `contains`, String `startsWith`, Int `gte`, Int `lte`, Boolean `eq`, enum `eq`, CategoryWhere `eq`. Asserts row count + ids match the hand-computed expected subset.
- **Phase 4:** Tier-3 tests for `AND: [...]`, `OR: [...]`, `NOT: ...`, and one mixed-nesting case (e.g. `AND: [{ Grundflaeche: { gte: 100 } }, { OR: [{ RollstuhlgerechterZugang: { eq: true } }, { NOT: { Raumart: { eq: ... } } }] }]`). Plus a depth-11 query that must be rejected with a clear validation error.
- **Phase 5:** Coverage-matrix test parameterised over (predicate kind x operator), asserting evaluator behaviour against a fixed in-memory fixture. Plus `isNull: true` and the implicit `not null` semantics for every other operator.

**Permission-leak test (AGENTS.md §12).** Mandatory across phases 2-5: non-admin user filtering on a where predicate whose true match set contains both visible and hidden allocatables (e.g. a hidden room that matches `Grundflaeche: { gte: 200 }`). Assert the response is byte-identical to the visible-only subset and to the all-non-existent case — the where predicate must not leak existence via row count, error text, or latency. Extension of the existing `GraphQlLeakTest` pattern.

## Out of scope

- Nested where on `AllocatableWhere`. Today id-equality only. A recursive type structure (`AllocatableWhere.where: AllocatableWhere`) is a separate PRD if real consumer demand emerges.
- Where on Reservations (`reservations(filter:)`). Same evaluator could apply; separate phase / separate PRD.
- Sort by typed attribute.

## Open questions

None — design locked.
