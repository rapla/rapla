# PRD 087 — classification & type indices (GraphQL read-path, server-side filter pushdown)

**Status:** draft — 2026-06-24 (split from PRD 082 Workstream A)
**Related:** PRD 082 (storage memory model — foundation: H2 read-model + put/remove projection seam; this is its first low-risk consumer), PRD 066 (allocatable scope union on ReservationFilter), PRD 059 (GraphQL typed where-predicates), PRD 028 (allocatable evaluator), PRD 085 (search/name — sibling GraphQL-only index), PRD 086 (appointment index — the dual-API sibling)

**Split from PRD 082 (Workstream A).** Unlike the appointment index (PRD 086, dual-API), these
indices are **GraphQL-only**: they accelerate server-side *classification/type filtering*, which
exists only on the GraphQL read path (`ClassificationGraphQLController`, `buildStorageFilter`). The
old RemoteStorage `getResources()` returns the caller's full readable set and the Swing client filters
client-side — no server-side type filter to accelerate. So this PRD needs only GraphQL tier-3 tests,
not the old-API behavioral harness. The shared read-model foundation (H2, projection seam,
drift-safety, boot rebuild, index classes) lives in **PRD 082** and is referenced here.

## Problem (measured — PRD 082)

Broad GraphQL read queries over a large store (tens of thousands of allocatables) pay a fixed
**O(all)** tax. `AbstractCachableOperator.getAllocatables(filters)` always does
`new HashSet<>(cache.getAllocatables())` (copy all) then iterates to filter, regardless of how narrow
the filter is. `ClassificationGraphQLController.buildStorageFilter` pushes **only `typeKeyEq`** to
storage; `typeKeyIn` (multi-type) and `where`-only return `null` → the controller loops
`matches`/`evaluateWhere`/`canRead` over the whole population (a ~155 ms-class untyped catalog floor;
typed `typeKeyEq` still pays the full copy + scan inside `getAllocatables`).

## Two index kinds

### Class 1 — the type-bucket index (structural)

A `Map<String, Set<String>>` (DynamicType id → allocatable ids), maintained in `LocalCache` (or the
H2 read-model — see *Where it lives*) on entity put/remove. The `Set<String>` **is** the answer set
for "all of type X" queries.

Where it helps (and where not):

| Query class | Today | Index? |
|---|---|:--:|
| "all of type X" (`typeKeyEq:"Raum"`) | full copy → filter | **yes** — direct bucket lookup |
| multi-type union (`typeKeyIn:[A,B,…]`) | `buildStorageFilter`→null → full + `canRead`×all | **yes, biggest** — bucket union; `canRead` only over the union |
| `where<Type>`-only (B′, implied type) | →null → full + `WhereEvaluator`×all | **yes** — narrow to the implied type's bucket first |
| type-scoped analytics (`blockStats` over all of a type) | full | **partial** — narrows allocatable resolution; residual appointment cost is PRD 086 |
| id-scoped (`idIn`) | direct | no (already direct) |
| untyped "all" / window-only | full + `canRead`×all | no (= union of all buckets = the full map; the `canRead` cost is PRD 083) |

"alle Räume"/"alle Kurse" (whole-type) is a common, legitimate pattern, so the index is not niche.
Its lever is `getAllocatables`/`buildStorageFilter`; it is **orthogonal** to the appointment cost
(PRD 086) and the `canRead` cost (PRD 083).

### Class 2 — on-the-fly classification-attribute indices (dynamic)

Classification attributes (`capacity`, `building`, `year`, …) are admin-/deployment-defined — not
known at schema time, and mostly never filtered. Per the PRD 082 read-model: store classification as
a `JSON` column; the **first** time an attribute is filtered (`where:{Raum:{capacity_gt:30}}`),
create a typed generated column (CAST driven by the DynamicType's known attribute type) + index it
**lazily**. Pay-for-use; only filtered attributes cost memory/write-maintenance. An LRU/threshold
**budget** caps the number of on-the-fly indexes (each adds per-write maintenance). A proven-hot
attribute can be **promoted** to a permanent typed indexed column. The hot path (the appointment
block query, PRD 086) is pure Class 1 / structural and never touches this JSON machinery.

## Where it lives — the foundation-validation choice (OQ1)

- **(a) In-memory `LocalCache` map** — the type-bucket as a `Map<String, Set<String>>` (partly shipped;
  see Decisions). Cheapest, ships value fast, but does **not** exercise the H2 read-model.
- **(b) On the H2 read-model** (PRD 082) — the type-bucket + Class-2 attribute indices as projections
  at the put/remove seam. More work, but makes this the **low-risk first H2 consumer** that validates
  the foundation end-to-end before the appointment index (PRD 086) commits to it.

Leaning **(b)** for the program: a safe load-test of the projection seam before the risky appointment
work. (a) remains the fallback if foundation timing slips.

## Maintenance & drift

At the put/remove seam (PRD 082): on an allocatable put, if its type changed vs the old entity, move
its id from the old type's bucket to the new. Class-2: idempotent upsert of the JSON column;
functional indexes materialize lazily. Pure projection — `index == project(objects)`, rebuilt at boot,
drop-and-rebuildable. **Narrow, never gate** (PRD 082 D5): the index yields candidate ids; ordering,
`canRead`, and final matching run after, on the narrowed set.

## Plan

- **Phase 1** — type-bucket index + maintenance on put/remove/putAll (type-change on re-put);
  accessor + unit test.
- **Phase 2** — consume it: `getAllocatables(filters)` builds candidates from bucket(s) for typed
  filters (preserve internal-type exclusion + `maxPerType`); `buildStorageFilter` emits per-type
  `ClassificationFilter[]` for `typeKeyIn` and derives the implied type from a sole `where<Type>` (B′).
- **Phase 3** — Class-2 on-the-fly attribute indices on the H2 read-model (JSON column + lazy
  functional index + budget + promotion).

## Tests (GraphQL tier-3, no old-API harness)

- Tier-2 (`LocalCache`/operator): bucket membership + maintenance (put → in bucket; re-type → moves;
  remove → gone); `getAllocatables(typeFilter)` returns the bucket without a full-population scan.
- Tier-3 (`ClassificationGraphQLControllerTest`): `typeKeyIn` multi-type union == union of per-type
  `typeKeyEq`; `typeKeyEq` unchanged; **existing §12 leak tests stay green** (the leak surface is the
  GraphQL controller — same as today).
- Live: type-scoped catalog latency drop; `idIn` unchanged.

## Decisions locked (carried from PRD 082 Workstream A)

- **D1 — Store ids, not entities (`Map<String, Set<String>>`).** The id set *is* the type-group
  answer; ids are stable across re-reads; order/limit applied after resolving at the output boundary.
- **D2 — Index + `buildStorageFilter` ship together.** The index is inert without the consumer.
- **D3 — Orthogonal to the appointment cost (PRD 086) and the `canRead` cost (PRD 083).** This index
  attacks allocatable *resolution*, not appointment iteration or permission filtering.
- **D4 — `reservations()` scoped-set direct-resolve (shipped 2026-06-22).**
  `ReservationGraphQLController.reservations()` resolves scoped allocatables directly via the catalog
  resolver instead of `getAllocatables(null)`, removing the fixed O(all) copy from scoped queries
  (verified behaviour-preserving; §12 leak tests green).

## Open Questions

- **OQ1** — In-memory map (a) vs H2 read-model (b) — see *Where it lives*. Leaning (b) as the
  foundation-validation consumer; confirm against PRD 082 foundation timing.
- **OQ2** — Frequency of `typeKeyIn` vs `typeKeyEq` vs `idIn` in real SPA traffic (determines whether
  the multi-type union — the biggest win — is actually exercised).
- **OQ3** — Class-2 budget policy: LRU size / promotion threshold; H2's JSON indexing is weaker than
  Postgres `jsonb`/GIN — if multi-attribute predicates become hot, that is the Postgres-first
  argument (PRD 082 engine note).
