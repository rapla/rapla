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

A `Map<String, Set<String>>` (DynamicType id → entity ids), maintained in `LocalCache` (or the
H2 read-model — see *Where it lives*) on entity put/remove. The `Set<String>` **is** the answer set
for "all of type X" queries.

**Scope — allocatables *and* reservations, kept in separate bucket spaces (D5).** Both `Allocatable`
and `Reservation` carry a `Classification`/`DynamicType`; the GraphQL read path filters *both*
(`getAllocatables(typeKeyEq:"Raum")` and `reservations(typeKeyEq:"Vorlesung")`). A DynamicType is
scoped to exactly one classification-type category (`resource` / `reservation` / `rapla:person` …), so
a single id-space is technically unambiguous — but the two query paths resolve different stores
(`cache.getAllocatables()` vs the reservation set) and have **different permission/scope rules**
(`canRead` on an allocatable ≠ readability of a reservation that references it). Keep two maps —
`allocatableTypeBucket` and `reservationTypeBucket` — so each consumer (`getAllocatables` /
`reservations`) reads only its own space and a bucket lookup never crosses the entity boundary.
Reservation-side maintenance lives at the same put/remove seam; the appointment index (PRD 086) is the
*time* lever, this is the *type* lever — orthogonal, both can narrow the same `reservations()` query.

Where it helps (and where not — table is allocatable-phrased; the reservation path is symmetric):

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

### Class 2 — on-the-fly classification-attribute indices (dynamic) — *later in the 082 plan (D7)*

Classification attributes (`capacity`, `building`, `year`, …) are admin-/deployment-defined — not
known at schema time, and mostly never filtered. Per the PRD 082 read-model: store classification as
a `JSON` column; the **first** time an attribute is filtered (`where:{Raum:{capacity_gt:30}}`),
create a typed generated column (CAST driven by the DynamicType's known attribute type) + index it
**lazily**. Pay-for-use; only filtered attributes cost memory/write-maintenance. An LRU/threshold
**budget** caps the number of on-the-fly indexes (each adds per-write maintenance). A proven-hot
attribute can be **promoted** to a permanent typed indexed column. The hot path (the appointment
block query, PRD 086) is pure Class 1 / structural and never touches this JSON machinery.

**Not every attribute type is index-safe — the explosion guard (D6).** rapla `AttributeType` is
`STRING | INT | BOOLEAN | DATE | CATEGORY | ALLOCATABLE`. The bounded types index cheaply:

| AttributeType | Index? | Generated column / note |
|---|:--:|---|
| `BOOLEAN`, `INT`, `DATE` | **yes** | direct CAST to `BOOLEAN`/`BIGINT`/timestamp; tiny key, low cardinality |
| `CATEGORY` | **yes** | index the category-id (a stable ref), not the localized label |
| `ALLOCATABLE` | **yes** | index the referenced id (equality/`in` only — no range) |
| `STRING` | **build deferred** | sampled on-the-fly under a global cap; falls back to `WhereEvaluator` until built (see below) |

`STRING` is the explosion risk: a free-text attribute (description, notes, comment) is high-cardinality
with large keys, so a B-tree functional index would bloat memory for little selectivity — and rapla's
attribute model has no per-attribute max-length to gate on. We **defer building** string indexes (a
string predicate falls back to the `WhereEvaluator` scan over the already-narrowed Class-1 bucket — the
type filter cuts the population first, so the residual scan is bounded, *not* full-population), but we
**keep the on-the-fly sampling design** for when a string predicate later proves hot:

- **Sample at first filter.** The first time a `STRING` attribute is filtered, sample its observed
  values against a single global key-length cap — **128 chars** (a read-model knob — rapla has no
  per-attribute max-length). Short codes/labels (`building:"A"`, `room:"V104"`) are well under 128 →
  eligible to build the functional index; mostly-long prose values → stay on the scan.
- **The cap is a memory heuristic, not a correctness constraint.** H2 happily stores a long key in a
  B-tree; an over-cap value never makes the index *wrong*, only *bigger*. So the cap exists purely to
  bound footprint, and that framing decides the eviction policy below.

#### What happens when a new over-cap value arrives later?

A built string index can be invalidated at runtime: a put writes a value longer than the cap into an
attribute we already indexed as "short." We don't put that long value in the B-tree, and we don't
discard the index on the first outlier. Instead:

- **Over-cap entries are tracked in a side "violators" list, keyed per entity id** (reservation or
  allocatable id). The functional index serves the under-cap population; the violators list holds the
  rest. A query against the attribute probes the index **and** scans the violators list — every entity
  is in exactly one of the two, so the result stays correct. The list is small by construction, so the
  extra scan is cheap.
- **Discard the whole index only when the violators list outgrows its worth** — threshold
  `max(10, 2% · index size)`. Below that, long values are absorbed into the side list (bounded cost).
  Once violators exceed `max(10, 2%)` of the whole index, the index no longer pays for itself → drop it
  entirely and fall back to the `WhereEvaluator` scan over the Class-1-narrowed set. The `max(10, …)`
  floor keeps small indexes from being killed by a handful of long values; the `2%` term scales the
  tolerance with index size.
- **Safe and reversible.** The index is a **pure, drop-and-rebuildable projection** (PRD 082 —
  `index == project(objects)`), so dropping costs only the acceleration, never correctness, and reclaims
  the index in full. If the attribute is filtered again later and samples back under the cap, it rebuilds
  from scratch (same drop-and-rebuild primitive used at boot).

So the budget has two limiters: the LRU **count** cap on how many on-the-fly indexes exist, and, for
strings, this **violators-list** policy — absorb over-cap values per id up to `max(10, 2%·size)`, then
discard the index.

#### H2 support — what's native vs. what we build

A fair amount of the machinery above is *ours*, not H2's. The split (verify exact behaviour against the
target H2 version at implementation — OQ6):

| Capability | H2 native? | Implication |
|---|:--:|---|
| `JSON` column + `JSON_VALUE`/`CAST` extraction | **yes** (H2 2.x) | store classification as JSON, extract per attribute |
| Generated column `GENERATED ALWAYS AS (CAST(JSON_VALUE(...)))` | **yes** | this **is** the "functional index" — H2 indexes a stored/computed column, not an arbitrary expression |
| B-tree index on that column; runtime `CREATE INDEX` / `DROP INDEX` | **yes** | the drop-and-rebuild primitive is native DDL |
| **Partial / filtered index** (`CREATE INDEX … WHERE`) | **no** | "index only under-cap values" can't be DDL → the **violators split is our Java logic** |
| First-filter detection, length **sampling**, cap decision | **no** | application policy in the projection-maintenance seam |
| Per-id **violators list** + dual-probe (index ∪ violators) | **no** | our data structure + our query rewrite |
| `max(10, 2%)` **discard** + promote/demote | **no** | our policy loop, evaluated at maintenance |

**Bottom line:** H2 gives the mechanical parts — a typed extracted column, a B-tree on it, and runtime
DDL to create/drop it. The entire **adaptive layer** (when to build, what to sample, the violators
tracking, the discard threshold, the fallback routing to `WhereEvaluator`) is bespoke Java we write in
the read-model layer. That's a non-trivial build — which is the concrete reason **string Class-2 is
deferred**: the bounded types get nearly all the value for a fraction of the code.

For **bounded types** (INT/BOOL/DATE/CATEGORY/ALLOCATABLE) the H2 story is simple and mostly native:
one generated column + one index per hot attribute, no sampling, no violators, no discard policy. The
only *our*-side logic is the lazy "create on first filter" trigger and the LRU count cap. So the
near-term Class-2 work — if it lands at all — is the cheap bounded-type half; the expensive adaptive
half is string-only and deferred.

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

**Class 1 (type index) is this PRD's near-term work; Class 2 (attribute indices) is sequenced later in
the overall PRD 082 program build order, not within 087 (D7).** The two are independent — Class 1
needs no JSON column, no lazy functional indexes, no budget machinery, and delivers the measured "all
of type X" / multi-type-union win on its own. Class 2 is speculative (OQ2: is multi-attribute
filtering even hot?) and carries the explosion risk (D6), so it lands in a later step of the 082 plan,
after the higher-value indices (appointment 086, permission 083), once traffic data justifies it.

*Near-term — Class 1 only:*

- **Phase 1** — type-bucket index (both `allocatableTypeBucket` and `reservationTypeBucket`) +
  maintenance on put/remove/putAll (type-change on re-put); accessor + unit test.
- **Phase 2** — consume it: `getAllocatables(filters)` builds candidates from the allocatable bucket(s)
  for typed filters (preserve internal-type exclusion + `maxPerType`); `reservations()` builds
  candidates from the reservation bucket(s) symmetrically; `buildStorageFilter` emits per-type
  `ClassificationFilter[]` for `typeKeyIn` and derives the implied type from a sole `where<Type>` (B′).

*Later in the 082 program (not within 087) — Class 2, once justified:*

- **Phase 3a (cheap, bounded types)** — generated column + index for INT/BOOL/DATE/CATEGORY/ALLOCATABLE
  on the H2 read-model, lazily on first filter, + LRU count cap. Mostly native H2 (see *H2 support*);
  little bespoke logic.
- **Phase 3b (expensive, strings)** — the adaptive string path: first-filter sampling vs the 128-char
  cap, per-id violators list + dual-probe, `max(10, 2%)` discard, promote/demote. Almost entirely
  bespoke Java (H2 has no partial index / no adaptive layer). Sequenced last, gated on OQ2 traffic data
  showing a string predicate is actually hot; until then `where:{...str...}` stays on the
  `WhereEvaluator` scan over the Class-1-narrowed set (correct, just not indexed).

Both 3a/3b sit after 086/083 in the 082 build order.

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
- **D5 — Class 1 covers allocatables *and* reservations, in separate bucket maps.** Both are
  classifiable and both GraphQL read paths filter by type. Keep `allocatableTypeBucket` /
  `reservationTypeBucket` distinct so each consumer reads its own entity space and the §12 permission
  boundary (allocatable `canRead` ≠ reservation readability) is never crossed by a bucket lookup.
- **D6 — Class 2 indexes bounded types directly; string indexes are build-deferred but sampled.**
  `BOOLEAN`/`INT`/`DATE`/`CATEGORY`/`ALLOCATABLE` index by their bounded value (category/ref by id, not
  label). `STRING` build is deferred — fall back to `WhereEvaluator` over the Class-1-narrowed set —
  but the **on-the-fly sampling design is kept**: first-filter sampling against a single global
  key-length cap (**128 chars**; rapla has no per-attribute max-length; the cap is a memory heuristic,
  not a correctness constraint). Later over-cap values are absorbed into a per-id **violators list** (probed
  alongside the index so reads stay correct), not put in the B-tree; the whole index is **discarded**
  only when violators exceed `max(10, 2% · index size)` → fall back to scan (the projection is
  drop-and-rebuildable, so correctness is never at risk; rebuild if it samples back under the cap).
  Free-text substring search is **PRD 085** (Lucene).
- **D8 — H2 supplies storage primitives only; the adaptive layer is bespoke Java.** Native: `JSON`
  column + `JSON_VALUE`/`CAST`, generated columns (the functional-index vehicle), B-tree index, runtime
  `CREATE`/`DROP INDEX`. **Not** native: partial/filtered indexes, on-the-fly creation, sampling,
  violators tracking, the discard policy. So bounded-type indexes (Phase 3a) are near-native and cheap;
  the string path (Phase 3b) is almost all our code → its cost justifies sequencing it last. Confirm
  exact H2 support at impl (OQ6).
- **D7 — Class 1 is 087's near-term scope; Class 2 is sequenced later in the 082 program.** The
  type-bucket index (Phases 1–2) ships independently and delivers the measured win with no
  JSON/lazy-index/budget machinery. Class 2 (Phase 3) is not deferred-maybe-never — it has a slot
  *later* in the 082 build order (after appointment 086 and permission 083), gated on OQ2 traffic data;
  the read path stays correct via `WhereEvaluator` until then. This keeps 087's near-term scope to a
  clean "type lever," matching the program build order (087 = the low-risk first H2 consumer).

## Open Questions

- **OQ1** — In-memory map (a) vs H2 read-model (b) — see *Where it lives*. Leaning (b) as the
  foundation-validation consumer; confirm against PRD 082 foundation timing.
- **OQ2** — Frequency of `typeKeyIn` vs `typeKeyEq` vs `idIn` in real SPA traffic (determines whether
  the multi-type union — the biggest win — is actually exercised).
- **OQ3** — Class-2 budget policy: LRU size / promotion threshold; H2's JSON indexing is weaker than
  Postgres `jsonb`/GIN — if multi-attribute predicates become hot, that is the Postgres-first
  argument (PRD 082 engine note).
- **OQ4 — resolved.** Global `STRING` cap = **128 chars**; discard threshold = `max(10, 2% · index
  size)` (violators-list size). Both locked; revisit only if real value-length distributions show 128
  is wrong. Until string indexing (Phase 3b) is built, strings stay on the `WhereEvaluator` fallback.
- **OQ6** — confirm the exact target H2 version's support for generated columns over `JSON_VALUE`
  extraction, and the absence of partial/filtered indexes (the assumption that forces the violators
  list into Java). Verify at Phase 3 implementation, not before — it doesn't gate the Class-1 work.
- **OQ5** — Does the reservation-side type bucket (D5) actually pay off, or is the `reservations()`
  query already scope-narrowed enough (D4) that a type bucket adds maintenance for little gain? Measure
  before building Phase 1's reservation half — may defer it behind the allocatable bucket.
