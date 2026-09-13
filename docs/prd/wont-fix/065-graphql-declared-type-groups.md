# PRD 065 — GraphQL Declared Type Groups (cross-classification interfaces + filters)

**Status:** draft (opened 2026-05-29) — design

**Date:** 2026-05-29

**Parent:** [PRD 035 (done) — GraphQL foundations](done/035-graphql-foundations.md) §"Classification axis — cross-type queries" + §"Input vs output groups — both v1". [PRD 035](done/035-graphql-foundations.md) designed this as **the primary cross-type surface** but deferred implementation; this PRD picks up the work.

**Siblings:**
- [PRD 055 — Events Read API](055-graphql-events-read-api.md) — provides the structural `Reservation` type that the group interfaces sit above
- [PRD 057 (done) — DT mutations v1](done/057-graphql-dt-mutations-v1.md) + [PRD 061 — DT mutations v2](061-graphql-dt-mutations-v2.md) — annotation surface needed to admin-edit group membership; this PRD reads the annotation, [PRD 061](061-graphql-dt-mutations-v2.md) owns the writing
- [PRD 059 — Typed Where Predicates](done/059-graphql-typed-where-predicates.md) — per-type `<TypeKey>Where` predicate pattern this PRD reuses verbatim for the group filters (`<Group>Filter` is the union shape; `where<TypeKey>` from [PRD 059](done/059-graphql-typed-where-predicates.md) is per-type)
- [PRD 060 — MCP foundations](060-graphql-mcp-foundations.md) — declared group queries are the obvious MCP-friendly cross-type primitive (one schema-typed entry point vs ad-hoc cross-type filters)

## Goal

Land the **declared type group** GraphQL surface so consumers can query and filter across multiple DynamicTypes that share an admin-declared semantic (e.g. "all course-like events: Lecture + Test + Exam") with full schema typing — `... on Lecture { weeklyHours }` works alongside `semester { path }` on the group interface.

Today the only cross-classification surface is `allocatables(filter: { typeKeyIn: [...] })` ([PRD 059](done/059-graphql-typed-where-predicates.md)) — a flat union with NO shared-attribute typed access; consumers must inline-fragment per concrete classification. That's fine for "all bookable things on this floor" but breaks down for the dominant cross-type query pattern in rapla: "all course events this semester" (Lectures + Tests + Exams + Excursions all share `semester` + `lecturer` + `courseNumber` but the SPA wants typed access to those without per-fragment boilerplate).

## Scope

In v1 (this PRD):
- A new DynamicType annotation `graphqlGroups` (comma-separated list of group names) read by `ClassificationSdlGenerator`.
- For each declared group, the generator computes the **intersection** of attribute (key, valueType, multiplicity) across all member DTs and emits:
  - A GraphQL interface `<Group>` extending the structural interface (`ReservationClassification` or `AllocatableClassification`) with the shared attribute fields.
  - A typed input `<Group>Filter` reusing the [PRD 059](done/059-graphql-typed-where-predicates.md) `where<TypeKey>` pattern — predicates over the shared attributes + AND/OR/NOT combinators.
  - Each member DT's generated `<TypeKey>Classification` implements ALL declared groups (`implements ReservationClassification & CourseEvent & GradedActivity`).
- New Query roots per group on the reservation side: `<groupName>s(filter: <Group>Filter, from: LocalDateTime!, to: LocalDateTime!): [<Group>!]!` (e.g. `courseEvents(filter:, from:, to:)`). Same time-window discipline as [PRD 055](055-graphql-events-read-api.md)'s `reservations(filter:)`.
- §12 — per-entity `canRead` at the output boundary, mixed visibility silently narrowed.
- Tier-3 tests: schema introspection (group interface + filter + query root all emitted), happy-path filter on a shared attribute, §12 leak test, member-DT divergence (an attribute that's NOT in every member is correctly absent from the interface).

Out of scope:
- Allocatable-side groups (e.g. "all bookable resources implementing `HasCapacity`"). Same design but separate slice — open as [PRD 066](066-graphql-reservation-allocatable-matching.md) if demand surfaces.
- Connection-style pagination (`edges { node } pageInfo`). v1 returns flat lists, matching the existing `reservations(filter:)` / `allocatables(filter:)` shape. Pagination is its own PRD once a real consumer needs cursor-based traversal.
- Group-aware mutations (`createCourseEvent(...)`). v1 is read-only; writes go through the typed-per-DT path from [PRD 056](056-graphql-events-write-api.md).
- Admin UI for editing group membership. Annotation is read here; editing belongs to [PRD 061](061-graphql-dt-mutations-v2.md)'s annotation-surface expansion.
- Group hierarchies / nested groups (a `GradedActivity` that's ALSO a `CourseEvent`). v1 lets a type declare multiple flat groups (`groups: "CourseEvent, GradedActivity"`); inter-group hierarchy is future.

## Locked design

### Annotation surface

```xml
<rapla:dynamictype key="Lecture">
  <rapla:annotation key="classification-type">reservation</rapla:annotation>
  <rapla:annotation key="graphqlGroups">CourseEvent, GradedActivity</rapla:annotation>
  <!-- attributes... -->
</rapla:dynamictype>
```

Annotation key: `graphqlGroups` — comma-separated list (whitespace-trimmed). Empty / missing = type belongs to no declared groups (only the structural interface). The list shape is verbatim per [PRD 058](058-graphql-key-spec-migration.md) key-spec migration (no silent transforms, no PascalCase auto-rewrite).

Validation at generator time:
- Group names must match `[A-Z][A-Za-z0-9_]*` (PascalCase, GraphQL-type-compliant). Invalid → WARN + skip group emission.
- Group name must not collide with a generated `<TypeKey>Classification` name or a structural type. Collision → WARN + skip group emission.

### SDL generation algorithm

```
groupMembers = Map<groupName, List<DynamicType>>
for dt in operator.getDynamicTypes():
    groups = parseList(dt.getAnnotation("graphqlGroups"))
    for g in groups:
        groupMembers[g].add(dt)

for (group, members) in groupMembers:
    if members.size() < 1: skip
    sharedAttrs = intersect(members.attributes_by_key_and_valueType_and_multiplicity)
    emit interface <group>:
        all sharedAttrs as typed fields (same shape as <TypeKey>Classification)
        plus the parent structural interface fields (typeKey, type)
    emit input <group>Filter:
        each sharedAttr as a <Where> predicate (per PRD 059)
        AND/OR/NOT combinators
    emit Query.<groupNameCamelCased>s(filter: <group>Filter, from: LocalDateTime!, to: LocalDateTime!): [<group>!]!

modify each <TypeKey>Classification SDL emission:
    "implements ReservationClassification" → "implements ReservationClassification & <group1> & <group2>"
    (concrete fields unchanged — shared attrs come for free because they're already typed-per-attribute)
```

**Intersection rule (locked from [PRD 035](done/035-graphql-foundations.md) §"Shared attributes — declared groups"):** an attribute belongs to the group interface iff every member DT has an attribute with:
- the same KEY
- the same valueType (STRING / INT / BOOLEAN / DATE / CATEGORY / ALLOCATABLE)
- the same multiplicity (SINGLE / LIST / BELONGS_TO / PACKAGE)
- for CATEGORY-typed attributes, the same root category constraint (so the generated `<CategoryEnum>` enum is consistent)
- for ALLOCATABLE-typed attributes, the same `expectedTypeKey` constraint

Mismatch → the attribute is silently absent from the interface. The concrete `<TypeKey>Classification` types still expose it (via the standard typed-per-attribute path); the divergence just means consumers querying via the group interface won't see it without an inline fragment to a concrete type.

**Caveat (preserved from [PRD 035](done/035-graphql-foundations.md) §"Semantic caveat"):** structural match ≠ semantic match. `Lecture.status` and `Test.status` may both be `String` yet carry different value domains. The interface emits the type-match-friendly intersection; the SPA's renderer keys off per-type descriptors (already shipped) for value-domain semantics.

### Query roots

Per group: `<groupName>s(filter: <Group>Filter, from: LocalDateTime!, to: LocalDateTime!): [<Group>!]!`

- `from` + `to` are mandatory (same as `Query.reservations(filter:)` from [PRD 055](055-graphql-events-read-api.md) — server-side scan otherwise).
- Server-side cap: same 365-day window + default 500 limit / hard cap 5000 as `Query.reservations(filter:)`.
- Naming: `courseEvents` for group `CourseEvent`, `gradedActivities` for group `GradedActivity`. Lowercase-first-letter + pluralize. For irregular plurals the admin can override via a future `pluralName` annotation; v1 uses naive `+ "s"`.

### Resolver — no engine rewrite

For each group query the resolver:
1. Resolves the caller from `RequestContextInstrumentation` (per [PRD 055](055-graphql-events-read-api.md) perf pattern).
2. Looks up the group's member DTs from the schema-build-time map (stored in a sibling of `StructuralTypeFetchers`).
3. Translates the typed `<Group>Filter` to a `ClassificationFilter[]` — one filter per member DT (each typed-per-attribute predicate maps to a rule on that DT's matching attribute).
4. Calls `operator.queryAppointmentsByLocalDateTime(caller, allocatables, owners, from, to, classificationFilters, null, false)` — same path [PRD 055](055-graphql-events-read-api.md)'s `reservations(filter:)` uses.
5. §12-filters the result, applies the 500 default / 5000 cap.

Reuses the existing `ClassificationFilter` execution engine — no new evaluator, no engine rewrite (consistent with [PRD 035](done/035-graphql-foundations.md) §"Execution — no engine rewrite").

### Member-DT changes during hot-swap

When admin saves a DT ([PRD 057](done/057-graphql-dt-mutations-v1.md) / [PRD 061](061-graphql-dt-mutations-v2.md)), `GraphQlSchemaRebuilder` already polls + rebuilds the schema within ~10s. The group's intersection is recomputed on every rebuild. If admin adds `graphqlGroups: "CourseEvent"` to a new DT, that DT joins the group on the next rebuild. If admin removes a group from a DT, the DT drops out — schema-breaking but the SPA's hot-swap discovery flow ([PRD 057](done/057-graphql-dt-mutations-v1.md) §"Hot-swap visibility") handles this same way it handles concrete `<TypeKey>Classification` regeneration.

### §12 invariants

Identical to [PRD 055](055-graphql-events-read-api.md)'s `Query.reservations(filter:)`:
1. Anonymous → empty.
2. Result entries `canRead`-filtered; mixed visibility silently narrowed.
3. Mandatory window + cap.

## Worked example

Annotation on three DTs:
```
Lecture:    classification-type=reservation, graphqlGroups="CourseEvent"
Test:       classification-type=reservation, graphqlGroups="CourseEvent, GradedActivity"
Excursion:  classification-type=reservation, graphqlGroups="CourseEvent"
Exam:       classification-type=reservation, graphqlGroups="GradedActivity"
```

Generated SDL:
```graphql
interface CourseEvent implements ReservationClassification {
  typeKey:    String!
  type:       DynamicType!
  semester:   Semester     # only emitted if all 3 DTs have a "semester" attribute (CATEGORY → enum Semester)
  lecturer:   Allocatable  # only if all have an ALLOCATABLE attr keyed "lecturer" with expectedTypeKey="lecturer"
  # ...attributes that are NOT in every member are absent
}

interface GradedActivity implements ReservationClassification {
  typeKey:    String!
  type:       DynamicType!
  grade:      String       # if Test + Exam both have a "grade" attribute
  # ...
}

type Lecture implements ReservationClassification & CourseEvent {
  typeKey:      String!
  type:         DynamicType!
  semester:     Semester
  lecturer:     Allocatable
  weeklyHours:  Int         # Lecture-only — present here, NOT on CourseEvent
}

type Test implements ReservationClassification & CourseEvent & GradedActivity { ... }
type Excursion implements ReservationClassification & CourseEvent { ... }
type Exam implements ReservationClassification & GradedActivity { ... }

input CourseEventFilter {
  semester: SemesterWhere   # generated enum-where (PRD 035 §5b)
  lecturer: AllocatableWhere
  AND: [CourseEventFilter!]
  OR:  [CourseEventFilter!]
  NOT: CourseEventFilter
}

type Query {
  courseEvents(filter: CourseEventFilter, from: LocalDateTime!, to: LocalDateTime!): [CourseEvent!]!
  gradedActivities(filter: GradedActivityFilter, from: LocalDateTime!, to: LocalDateTime!): [GradedActivity!]!
}
```

SPA query:
```graphql
{
  courseEvents(
    filter: { semester: { eq: WS25_26 } },
    from: "2025-09-01T00:00:00",
    to:   "2026-02-28T00:00:00"
  ) {
    typeKey
    semester { path }     # typed via group interface
    lecturer { displayName }
    ... on Lecture { weeklyHours }   # type-specific inline fragment for the Lecture extension
  }
}
```

## Plan

1. **Phase 1 — annotation reading.** Wire `graphqlGroups` annotation read in `ClassificationSdlGenerator`; build the `Map<groupName, List<DT>>` map at schema-build time. No SDL emission yet; just collect + validate naming. Tier-1 unit test on a synthesized fixture.
2. **Phase 2 — interface emission.** Emit `<Group>` interface SDL with the intersection of typed attribute fields. Each `<TypeKey>Classification` SDL updates to include the group(s) in its `implements` clause. Tier-3 introspection test confirms the interface exists with the right field set.
3. **Phase 3 — input filter emission.** Emit `<Group>Filter` SDL — same shape as [PRD 059](done/059-graphql-typed-where-predicates.md)'s `<TypeKey>Where`, restricted to the shared-attribute set. Tier-3 introspection test confirms `CourseEventFilter` has the expected predicates.
4. **Phase 4 — query roots.** Emit `Query.<groupName>s(filter:, from:, to:)` per group. Wire resolver translating the typed filter to `ClassificationFilter[]` per member DT and calling `operator.queryAppointmentsByLocalDateTime`. Tier-3 happy-path test asserts cross-type results.
5. **Phase 5 — §12 + caps.** Mandatory window enforcement, 365-day cap, default limit 500, hard cap 5000, §12 read gate. Tier-3 leak test.

Phases 1-5 are sequenced — each depends on the previous SDL shape. Cannot parallelize cleanly without rework.

## Tests

- **Phase 1:** Tier-1 unit on `ClassificationSdlGenerator.parseGroupAnnotation()` — handles empty / whitespace / invalid names / duplicate entries.
- **Phase 2:** Tier-3 introspection of `CourseEvent` interface field set; assert all-member intersection rule (an attr in 2 of 3 members must NOT appear).
- **Phase 3:** Tier-3 introspection of `CourseEventFilter` input fields; assert per-attribute predicate types match the source.
- **Phase 4:** Tier-3 happy-path — `courseEvents(filter: { semester: { eq: WS25_26 } })` returns a mix of Lecture + Test + Excursion entries; inline fragment to a concrete type pulls type-specific fields.
- **Phase 5:** Tier-3 §12 — non-admin caller misses admin-only reservations within the result; anonymous gets `[]`; >365d window rejected.

## Open questions

### OQ1 — Cross-side groups (Allocatable + Reservation in one interface)?

Some domains might want groups spanning both sides (e.g. `Schedulable` over Allocatables-that-can-be-booked + Reservations). Current design splits per side because the structural interfaces (`AllocatableClassification` vs `ReservationClassification`) differ.

**Lean: defer**, splitting is the right v1; reopen if real demand surfaces.

### OQ2 — Connection-style pagination

Already covered in Scope (out of scope for v1). Note here so future readers know it's deliberate, not forgotten.

### OQ3 — Group hierarchy (groups extending other groups)

Designed in [PRD 035](done/035-graphql-foundations.md) §"declared groups" (shown as a hierarchy `Reservation → CourseEvent → Lecture`) but practical hierarchies are admin-pain to maintain. Lean: defer; v1 supports flat multi-membership only (a DT belongs to N groups, but no group extends another).

### OQ4 — Should we ship the runtime-validated `classificationFilter` escape hatch in this PRD too?

[PRD 035](done/035-graphql-foundations.md) §"Query modes" identifies it as the ad-hoc fallback for cross-type queries that don't have a declared group. Useful for power-user / MCP-agent flows.

**Lean: defer to a separate PRD.** This PRD's scope is the typed/safe path; the runtime escape hatch has a different design surface (filter validation, error mapping for `INVALID_ATTRIBUTE`) and a separate consumer (admin tooling / MCP). Splitting keeps each scope tight.

### OQ5 — Plural-name irregularity

Naive `+ "s"` produces `excursions` (fine), `tests` (fine), `gradedActivitys` (wrong — should be `gradedActivities`). Quick rule (`y → ies`) covers most. A `graphqlGroupQueryName` annotation could let admins override.

**Lean: ship the naive rule + a regex for `-y → -ies`; defer the override annotation.**

## Decision log

- **2026-05-29** — PRD opened. Picks up [PRD 035](done/035-graphql-foundations.md)'s "declared groups (chosen)" pattern. Scope: read-only; reservation side; v1 deliberately avoids the runtime escape hatch + connection pagination + admin UI.
