# PRD 059 — GraphQL Typed Where Predicates on `allocatables(filter:)`

**Status:** done — phases 1–5 (allocatables) 2026-05-29; Phase 6 (reservation where-predicates, same evaluator) + Phase 7 (single `typeIn` selector on per-kind enums `AllocatableTypeKey`/`ReservationTypeKey`, typeKeyEq/typeKeyIn removed) both landed 2026-07-07 (per-kind split 2026-07-08). Extracted from [PRD 035](035-graphql-foundations.md) §5d on 2026-05-29. Durable identity/rename decision: [ADR 0005](../../decisions/0005-graphql-keys-are-api-identity.md).

**Date:** 2026-05-29 (reopened 2026-07-07)

**Parent:** [PRD 035 (done) — GraphQL foundations](035-graphql-foundations.md) §"Filter & query language" for the broader filter-axis model this PRD extends.

**Siblings:**
- [PRD 055 — Events Read API](../055-graphql-events-read-api.md) — establishes the typed-classification interface pattern this extends to filtering (reopened 2026-05-29 for Tier-1 perf migration)
- Reservation where-predicates: now **Phase 6** of this PRD (added 2026-07-07). Nested `AllocatableWhere` stays deferred (see Out of scope)

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
- ~~Where on Reservations (`reservations(filter:)`).~~ Pulled into scope as **Phase 6** (2026-07-07) — consumer demand arrived (equipment-lending loan-status filtering / [PRD 093](../093-loan-lifecycle.md), SPA event tables).
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
6. **Phase 6 — `where<TypeKey>` for RESERVATION DynamicTypes on `ReservationFilter`.** [DONE 2026-07-07.] Zero duplication: `ClassificationSdlGenerator.appendWhereInputs` now emits `<eventTypeKey>Where` for RESERVATION DTs in the SAME loop (extension lines split per target: resource/person → `extend input AllocatableFilter`, reservation → `extend input ReservationFilter`; no RefWhere for reservation DTs — nothing references them). `WhereEvaluator.evaluate` generalized from `Allocatable` to `Classifiable` — the reservation path runs the IDENTICAL evaluator. `reservations`/`appointmentBlocks`/`appointmentBlockStats`/`reservationStats` switched from record binding to the raw-map pattern (`fromMap`, mirrors `allocatables`) so the generated `whereEvent` fields bind; the where check runs in the `reservations` visible-loop, so all block/stats roots inherit it. Tier-3 tests: `reservationsWhereEventFiltersByAttribute` (eq + OR combinator), `appointmentBlocksWhereEventFilters`, `reservationDTGetsWhereInput` (introspection; replaced the Phase-1 negative test). Original scope notes (kept for context): (added 2026-07-07 — was "Out of scope"). Today event classification attributes are not typed-filterable at all: `ReservationFilter` offers only `typeIn` type selection plus name search; attribute filtering exists only indirectly via `allocatableMatching.where<Type>` on the *resources*. Phases 1–5 excluded reservation DTs purely as scope-cutting (no consumer at the time), not for technical reasons — the evaluator is type-agnostic.
   - Extend the `ClassificationSdlGenerator.appendWhereInputs(...)` loop to also accept `VALUE_CLASSIFICATION_TYPE_RESERVATION` DTs → emit `<eventTypeKey>Where` inputs (same attribute→predicate mapping, same AND/OR/NOT combinators).
   - Emit `extend input ReservationFilter { where<EventTypeKey>: <eventTypeKey>Where ... }` analog to the existing `extend input AllocatableFilter`.
   - Wire the existing `WhereEvaluator` into the reservation query path (`appointmentBlocks(filter:)` / `reservations(filter:)`) — evaluate against `Reservation.getClassification()`. Same semantics: a `where<OtherType>` against a non-matching event DT contributes no constraint; combines with `typeIn`.
   - Consumer demand (why now): archetype C equipment lending ([usecases/equipment-planning.md](../../usecases/equipment-planning.md)) — loan-status filtering on the reservation ([PRD 093](../093-loan-lifecycle.md) loan lifecycle, UC-C4 "what's out / overdue" table) needs "reservations where attribute X = Y" server-side; also general SPA event-table filtering (PRD [074](../074-graphql-declarative-views.md)/[077](../077-calendar-model-graphql.md) views).
7. **Phase 7 — consolidate type selection: generated per-kind type enums + single `typeIn` field.** [DONE 2026-07-07 — hard cut, no deprecation cycle (nothing in production).] The old selection was redundant and inconsistent: `AllocatableFilter` carried BOTH `typeKeyEq: String` and `typeKeyIn: [String!]` (with an eq-takes-precedence special rule); `ReservationFilter` carried only `typeKeyEq` (no list form at all). `typeKeyEq: "X"` ≡ `typeKeyIn: ["X"]` — one field suffices.
   - **Landed:** `typeKeyEq` + `typeKeyIn` REMOVED from both static inputs. `ClassificationSdlGenerator.appendTypeInEnum(...)` emits **per-kind enums** (2026-07-08 refinement — one shared enum would let an allocatable key validate on `ReservationFilter` and silently yield empty, the exact failure mode Phase 7 removes): `enum AllocatableTypeKey` (resource+person DTs) → `extend input AllocatableFilter { typeIn: [AllocatableTypeKey!] }`, and `enum ReservationTypeKey` (reservation DTs) → `extend input ReservationFilter { typeIn: [ReservationTypeKey!] }` (ReservationFilter thereby gains list/union type selection for the first time). Wrong-kind keys are validation errors (`typeInEnumsAreSplitPerKind` test).
   - Semantics: `typeIn` = union over listed types; combines with `where<TypeKey>` exactly like `typeKeyIn` did (explicit gate authoritative — `WhereEvaluator.hasExplicitTypeGate` now reads `typeIn`; where-blocks refine). The storage pre-filter (`buildStorageFilter`) now builds one `ClassificationFilter` per listed type — list selection pre-filters at the storage layer (previously only the Eq form did).
   - Enum value = `checkGraphQlCompliantName(key)` — identical to the raw key except GraphQL-reserved words (trailing `_`); resolvers match on the sanitized name.
   - Enums stay variable-friendly: `query($types: [AllocatableTypeKey!])`; unknown values are a loud VALIDATION error (replaces the old silent-empty for typos — the accepted trade-off). Multi-pod schema-rebuild skew (~10 s polling window) can briefly reject a just-added type's enum value on a stale pod.
   - Tests: `reservationsTypeInFiltersByEventType` (tier-3, new capability), `allocatablesFilterTypeInUnknownKeyIsRejected` (validation error), plus the migrated where-predicate suites (142 green in the graphql package, 2026-07-07). `docs/graphql.md` examples updated.

## Tests

Per-phase tier-3 tests:

- **Phase 1 (done):** Tier-1 SDL string assertion — for a synthesized 3-attribute DT (String, Int, VALUE_LIST), the generated SDL contains `<TypeKey>Where` with the three fields, plus `<EnumName>Where` and `<EnumName>ListWhere` for the enum root.
- **Phase 2:** Tier-3 MockMvc query against the dev dataset asserting that `allocatables(filter: { typeKeyEq: "Raum", whereRaum: {} })` returns the same row count as `allocatables(filter: { typeKeyEq: "Raum" })`. Confirms the resolver switch from record to raw map didn't drop existing scalar-filter semantics.
- **Phase 3:** One tier-3 test per predicate kind — String `eq`, String `contains`, String `startsWith`, Int `gte`, Int `lte`, Boolean `eq`, enum `eq`, CategoryWhere `eq`. Asserts row count + ids match the hand-computed expected subset.
- **Phase 4:** Tier-3 tests for `AND: [...]`, `OR: [...]`, `NOT: ...`, and one mixed-nesting case (e.g. `AND: [{ Grundflaeche: { gte: 100 } }, { OR: [{ RollstuhlgerechterZugang: { eq: true } }, { NOT: { Raumart: { eq: ... } } }] }]`). Plus a depth-11 query that must be rejected with a clear validation error.
- **Phase 5:** Coverage-matrix test parameterised over (predicate kind x operator), asserting evaluator behaviour against a fixed in-memory fixture. Plus `isNull: true` and the implicit `not null` semantics for every other operator.

- **Phase 6:** Tier-1 SDL assertion — a synthesized RESERVATION DT yields `<eventTypeKey>Where` + the `extend input ReservationFilter` block. Tier-3 tests mirroring Phase 3/4 against the reservation path: `appointmentBlocks(filter: { typeKeyEq: "event", whereEvent: { <attr>: { eq: ... } } })` filters to the expected subset; combinators; `where<OtherType>` no-constraint semantics. Plus the §12 leak test on reservations (hidden event matching the predicate must not surface).

**Permission-leak test (AGENTS.md §12).** Mandatory across phases 2-5: non-admin user filtering on a where predicate whose true match set contains both visible and hidden allocatables (e.g. a hidden room that matches `Grundflaeche: { gte: 200 }`). Assert the response is byte-identical to the visible-only subset and to the all-non-existent case — the where predicate must not leak existence via row count, error text, or latency. Extension of the existing `GraphQlLeakTest` pattern.

## Out of scope

- Nested where on `AllocatableWhere`. Today id-equality only. A recursive type structure (`AllocatableWhere.where: AllocatableWhere`) is a separate PRD if real consumer demand emerges.
- Sort by typed attribute.

## Open questions

- **OQ1** (Phase 7) — migration order. *Resolution:* obsolete 2026-07-07 — nothing in production; hard cut executed (no deprecation window, String fields removed outright).
- **OQ2** (Phase 7) — key-compliance enforcement. *Resolution:* already guaranteed — [PRD 058](../058-graphql-key-spec-migration.md)'s `GraphqlKeyMigration` renames non-spec keys at startup and `checkGraphQlCompliantName` throws if one slips through, so every enum value is emittable. Only GraphQL-reserved words differ (trailing `_`), handled in the resolvers.

(Phases 1–5: none — design locked.)
