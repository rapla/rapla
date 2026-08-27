# PRD 082 — Storage memory model: foundation (read-model architecture, engine, scope)

**Status:** draft — 2026-06-22 (restructured 2026-06-24 into the foundation; index implementations split to PRDs [083](083-user-change-subscription.md)/[085](085-search-name-indexing.md)/[086](086-appointment-block-index.md)/[087](087-classification-type-indices.md))
**Related:** [PRD 035](done/035-graphql-foundations.md) (GraphQL foundations — per-field perf hot-spots), [PRD 066](066-graphql-reservation-allocatable-matching.md) (allocatable scope union on ReservationFilter), PRD [079](079-graphql-grouped-aggregates.md)/[080](done/080-typed-entity-stats.md) (grouped aggregates / typed-entity stats — the `appointmentBlockStats` fan-out), [PRD 081](081-graphql-omnibox-multisearch.md) (omnibox multisearch), [PRD 067](067-server-mutation-unification.md) (server operator split), [PRD 083](083-user-change-subscription.md) (user change-subscription — consumes Workstream B), [PRD 084](084-replace-hsqldb-with-h2.md) (H2 persistence engine), [PRD 085](085-search-name-indexing.md) (search & name indexing — the name-search split out of Workstream A)

**Scope note (restructured 2026-06-24): this PRD is now the FOUNDATION.** It holds the shared
substrate — the Bestandsaufnahme, the modernization thesis (CQRS in-memory SQL read-model), the
**H2 engine decision (locked)**, the **read-model architecture** (put/remove projection seam,
drift-safety, boot rebuild, index classes), the all-in-memory scope, and the workload-shape
measurement. The concrete index implementations were split out into their own PRDs and reference
this foundation:
- **[PRD 086](086-appointment-block-index.md)** — appointment block index (dual-API: old RemoteStorage + GraphQL).
- **[PRD 087](087-classification-type-indices.md)** — classification & type indices (GraphQL-only).
- **[PRD 083](083-user-change-subscription.md) Part A** — permission-scoped read index (GraphQL-only); Part B consumes it.
- **[PRD 085](085-search-name-indexing.md)** — search & name indexing (GraphQL-only).
- **[PRD 084](084-replace-hsqldb-with-h2.md)** — HSQLDB→H2 persistence backend (orthogonal; same engine).

**Primary objective: read performance (efficient GraphQL queries).** Footprint/memory is a
*secondary, potential* benefit — **not** the driver of this PRD. The measured pain is read latency
(901 ms-class scans, N+1 field resolution), not an out-of-memory condition. The footprint material
below (slim/heavy split, windowing, parking, governance) is kept as a related opportunity, but the
bar every option is judged against is **does it make GraphQL reads fast**, not "does it shrink the
heap".

**Scope decision (2026-06-24): for now, everything stays resident in RAM.** The footprint/tiering
levers are **deferred** — no parking, no windowing/eviction of the heavy payload, no lazy-loading or
serving reads from the persistence DB. The active scope is the **in-memory read-model only**: an
**in-process H2 index** (in-memory mode) plus the slim/heavy split, **both fully resident**, built
purely to make reads fast. The full object graph stays in RAM exactly as today; the H2 read-model is
an *additional* in-memory structure beside it, not a replacement that offloads data to disk.
Consequences: **option 3 (window/park the heavy payload) is deferred**; **option 2 is reframed** as
an *in-memory* indexed read path (the H2 read-model), **not** reads served from the backing disk DB;
the parking/governance design below is **future work** (the existing archiver's time-based deletion
is unaffected and stays as-is). Footprint remains a later lever, consistent with "read performance
primary, footprint secondary."

**Working thesis (2026-06-22):** rather than hand-knit one index after another into the bespoke
storage framework, modernize the **read/query layer** onto a real query engine — a **CQRS
in-memory SQL read-model** fed from the change stream, migrated query-by-query (strangler-fig),
leaving the write/conflict/domain core untouched. Rationale (the two-level GraphQL cost argument
and why hand-rolling loses for open-ended GraphQL access patterns) in *Modernization evaluation →
Strategic direction*. Workstream A's hand-rolled indices become moot under this thesis, not wrong.

---

## The H2 pivot → in-memory indices (2026-06-24, measured)

**The CQRS-on-H2 read-model lost to measurement and is abandoned for the read path.** Built and
flipped behind a flag, the H2 block index was **1.1×–3.7× SLOWER** than the legacy in-memory
`appointmentMap` across the whole scope×window matrix on the real store; a phase decomposition showed
**~91% of the block path was the JDBC/SQL boundary** (`executeQuery` round-trips + row rehydration),
which an *already-resident in-memory* structure never has to pay. Batching made it worse (a wide `IN`
defeats the index). **Conclusion: PRD 082's "use a real query engine instead of hand-knit indices"
thesis is falsified for in-memory read performance** — the JDBC boundary cost exceeds the algorithmic
saving, because the legacy per-allocatable scans are small and cache-hot. The H2 work was not wasted:
it validated the **maintenance seam**, the **differential/shadow methodology**, and the
**binding/dependent-expansion correctness** — all of which carry over. We keep those and **swap the
storage from H2 to bespoke in-memory Java structures.** (H2 may still earn a place later for genuine
ad-hoc/aggregate queries with no in-memory equivalent, or at Postgres scale — but not for the hot
point/window reads.)

### Two shared index kinds (build each framework once)

| Kind | Shape | Query | Why |
|---|---|---|---|
| **`IntervalIndex<K,V>`** | per-key `ConcurrentSkipListSet` of **materialized blocks** sorted by start + a `volatile long maxBlockDuration` capped at threshold `D` + a small open-ended/long-runner **side-set** | `overlapping(key, winStart, winEnd)` = `subSet(winStart−maxBlockDuration, winEnd) ∪ sideSet` → O(log n + k), keeps lock-free reads | hand-rolled ~150 LOC; no library clears the bar (lodborg unmaintained+not-thread-safe; Guava `RangeMap` can't list overlaps; JTS/htsjdk heavy) |
| **`BucketIndex<K,V>`** | `Map<key, Set<id>>` | `members(key)` / `membersUnion(keys…)` | trivial; avoids the O(all) copy+scan |

Both are fed by the **same `updateReservation`/put-remove maintenance seam** the H2 projections used.
Why **materialized blocks**, not appointment *envelopes*: a repeating event has a long *envelope*
(months) → it would fall into the always-scanned side-set, bloating it. Materializing the bounded
recurrence into short concrete blocks keeps the `subSet` tight and the side-set tiny (open-ended tail
only, ~0.5–1%). Blocks also **eliminate the `overlaps()` recurrence call** for the 98%+ exact blocks
(the range slice *is* the exact answer) — recurrence math survives only on the open-ended side-set.
Cost: a cheap blocks→appointments dedup on the way out. Open-ended/over-cap → a rule entry in the
side-set, expanded in Java (the `is_rule` distinction, now a Java object not a row).

### In-memory index catalog (the inventory)

| # | Index | Keyed by → value | Query it serves | Kind | Replaces / status | PRD |
|---|---|---|---|---|---|---|
| **1** | Appointment blocks — by allocatable | allocatableId → blocks `[start,end]` | `queryAppointments(allocatables,window)`, calendar render | `IntervalIndex` | improves `appointmentMap` (half-range fix + overlaps-elim) — **the experiment** | 086 |
| **2** | Appointment blocks — by owner | userId → blocks | `queryAppointments(owners,window)` / `getAppointmentsForUser` | `IntervalIndex` | improves `appointmentUserMap` | 086 |
| **3** | Conflict candidates | *(derived from #1)* | `getAllocatableBindings` / `getConflicts` narrowing | uses #1 | not a new structure | 086 |
| **4** | blockStats aggregation | *(derived from #1)* | `appointmentBlockStats` count/group | uses #1 (in-memory group/count) | new (GraphQL) | 079/080/086 |
| **5** | Type bucket — allocatables | typeId → allocatableIds | `getAllocatables(typeKeyEq/In)` | `BucketIndex` | avoids O(all) copy+scan | 087 |
| **6** | Type bucket — reservations | typeId → reservationIds | `reservations(typeKeyEq/In)` | `BucketIndex` | new (GraphQL) | 087 |
| **7** | Attribute indices (Class 2) | (attrKey,value) → entityIds | `where:{Raum:{capacity_gt:30}}` | `BucketIndex` (bounded types) | **deferred**; strings stay scanned | 087 |
| **8** | Permission / access index | userId → readable set (READ/ALLOCATE/ADMIN) | `canRead` filtering, `changesSince` | **bespoke** (caller-context, hierarchy) | new; attacks `canRead × ~50k` | 083 |
| **9** | Name / full-text search | computed-name terms → entityIds | omnibox / name search | **bespoke** (in-memory Lucene `RAMDirectory`) | new | 085 |

### Components built (2026-06-24) — wired; H2 deleted

Four standalone components in `rapla-server/.../storage/impl/server/readmodel/`:
- **`IntervalIndex<K,V>`** — `put(key,value,start,end,openEnded)` / `remove(...)` / `overlapping(key,winStart,winEnd)`; ConcurrentSkipListSet + capped `maxBlockDuration` + side-set; lock-free reads. Remove narrows to the same-start slice (O(log n), not O(N)) so the dense "collector" key stays cheap on the write path. (7 tests)
- **`BucketIndex<K,V>`** — `put`/`remove`/`move(old,new,member)`/`members(key)`/`membersUnion(keys)`/`clear()`. (9 tests)
- **`PermissionIndex`** — `readableAllocatables(User)`/`accessLevel(User,id)`/`invalidate(...)`; **§12 `canRead`-equivalence test green** (13 tests). **Built, not yet wired** (GraphQL `canRead` boundary — own §12-leak-tested pass).
- **`DependencyIndex`** — `put(Allocatable)`/`remove(ref)`/`expand(ids)`; equivalence-to-`getDependent` test (5 tests). **Built, not wired on the hot path** — the read paths expand via `cache.getDependentRef` (matches legacy exactly, already graph-fast), so the index would only duplicate it; kept as a catalog component.

**Wired into `LocalAbstractCachableOperator` (2026-06-24).** No wrapper layer (the H2 `ReadModel`/`Projection`
SPI bought a JDBC connection/schema/generic-engine lifecycle that in-memory doesn't need):
- **Two `IntervalIndex` (allocatable + owner) maintained inside `AppointmentMapClass.updateReservation`** — at the same add/remove sites as `appointmentMap`/`appointmentUserMap`, so boot (`initAppointmentBindings`) and incremental updates both populate them with no separate seam. Appointments materialize into blocks (≤52) or one envelope entry (open-ended / >52).
- **Two `BucketIndex` (allocatable + reservation type) on the operator**, maintained at the `updateReadModel`/`rebuildReadModel` seam (now in-memory; `move` on type change).
- **Read paths flip behind `rapla.readmodel.authoritative`**: `queryAppointments` (allocatable + owner window read), `getAllocatableBindings` (conflict candidates), `getAllocatables` (type bucket) — each serves a candidate **superset** from the index then passes it through the *unchanged* `AppointmentImpl.getAppointments`/`getConflictingAppointments`/`matches`, so equivalence holds by construction. Expansion uses `cache.getDependentRef` (legacy-identical → the earlier Finding-2 `getDependent` divergence is gone).
- **The whole H2 detour is deleted**: `ReadModel`, `Projection`, the three projections, `blockDerived*`, `shadowCompare*`, `queryAllocatableTypeBucket`(SQL), `readModelConnectionForTest`, the H2 dependency, and the seven H2-coupled tests.

**Validation:** `ReadModelReadFlipEquivalenceTest` (window reads incl. owner scope + conflict bindings, reservations stored *after* boot so incremental maintenance is exercised) + `ReadModelFlipDifferentialTest` (type bucket) prove flag-on == flag-off; the owner read path has **no fail-safe fallback**, so its passing proves the index is genuinely served (not masked by a catch). **Full rapla-server fast lane: 304 tests green.**

**Perf (real store, 2026-06-24):** `QueryAppointmentsFlipPerfTest` (dhbwrapla, on a copy of the real `data.xml`) — dense 50-allocatable scope (top binding count 2433), 1-week window at the late end of the timeline (where the legacy `headSet(start < winEnd)` half-range prefilter degenerates to ~the whole history): **legacy `appointmentMap` 14.55 ms/query → in-memory `IntervalIndex` 0.79 ms/query = 18.5× faster**, byte-identical results. This is the exact inverse of the H2 detour (1.1–3.7× *slower*) and confirms the pivot thesis: a two-sided in-memory `subSet` over materialized blocks beats both the one-sided in-memory prefilter and the JDBC boundary.

**Priority:** #1 is the experiment (settles whether the in-memory two-sided index + overlaps-elimination
wins); #2 falls out of the same framework. #5/#6 (type bucket) and #8 (permission) attack measured
costs without #1's "is the win even there" doubt — likely real. #3/#4 are derived; #7 deferred; #9 a
separate Lucene track. **Gate (from the H2 lesson): build #1 behind a flag, measure on the profile
matrix — candidate-set shrinkage AND `overlaps()`-calls eliminated AND tiny-allocatable
non-regression — before committing or deleting any legacy path.**

## Program execution plan & live status

The full program spans PRDs 082/[083](083-user-change-subscription.md)/[085](085-search-name-indexing.md)/[086](086-appointment-block-index.md)/[087](087-classification-type-indices.md) (084 orthogonal). Build order and gating decisions
(all locked 2026-06-24): test-harness backend = **FileOperator on `data.xml` copy** (086 OQ4); 087
Class 1 lives on the **H2 read-model** (087 OQ1); **H2 confirmed** as engine (MQ7); appointment
materialization **cap = 52** = one year weekly (086 OQ1).

**Full build order:** 0 harness → 0.5 H2 write-path benchmark → 1 foundation seam → 2 087 Class 1 →
3 086 Stage X → 4 086 Stage Y → 5 083 → 6 085 → 7 084 → 8 087 Phase 3a (bounded attr) → 9 087 Phase 3b
(string attr, last). Phases 0→4 are the serial spine; 5/6/7 independent after the foundation; 8/9
demand-gated.

**Current authorized run: Phases 0–2** (current checkout, subagents for understanding fan-out /
Phase-0 test battery / adversarial gate review), then **stop for review**.

| # | Phase | Status | Notes |
|---|---|---|---|
| A | Understanding fan-out (read-only seam map; H2-on-`rapla-core`-classpath check) | ✅ done | findings below (2026-06-24) |
| 0 | Brute-force + record/replay harness (plugin-deployment repo, FileOperator on `data.xml` copy) | ✅ done | see Phase 0 result below |
| 0.5 | H2 write-path benchmark | ✅ done | PASS — see result below; found the missing maintenance index |
| 1 | Foundation seam (this PRD) — projection seam, drift-safety, boot rebuild, no consumer | ✅ done | infra + operator seam wired & tested; see below |
| 2 | 087 Class 1 type-bucket on H2 (`allocatable`/`reservation` buckets) | ✅ done (Stage-X shadow) | shadow consumer; 138 §12 leak tests green; see below |
| 3 | 086 appointment block index — **Stage X** (reads beside `appointmentMap`, shadow) | ✅ done | `AppointmentBlockProjection` + sampled shadow; **zero drift on the real store**; see below |
| 4a | 086 Stage Y — conflict-completeness shadow (non-destructive) | ✅ done | block candidates ⊇ authoritative conflicts; sampled shadow; see below |
| 4b-i | Flip **flag** (`rapla.readmodel.authoritative`, default off) + **type-bucket flipped** behind it | ✅ done | reversible; `ReadModelFlipDifferentialTest` flag-on==flag-off |
| 4b-ii | `queryAppointments` + conflict flips built, then **measured** (H2) | ⚠️ superseded by pivot | H2 was 0.62× (SLOWER) + aggregator divergence → pivoted to in-memory |
| P1 | **In-memory `IntervalIndex` ×2** wired into `AppointmentMapClass` (alloc+owner); read+conflict flips served from it; expansion via `getDependentRef` | ✅ done (2026-06-24) | equivalence proven; owner path has no fallback; 304 fast-lane green |
| P2 | **In-memory `BucketIndex` ×2** on the operator; `getAllocatables` type-bucket served from it | ✅ done (2026-06-24) | `ReadModelFlipDifferentialTest` flag-on==flag-off |
| P3 | **Delete the H2 detour** (`ReadModel`/`Projection`/3 projections/`blockDerived*`/`shadowCompare*`/H2 dep/7 tests) | ✅ done (2026-06-24) | reactor compiles; no dangling refs |
| P4 | `PermissionIndex` → GraphQL `canRead` boundary (all list/per-request-amortized sites) | ✅ done (2026-06-24) | measured **26.6 ms→0.0003 ms warm** (real store, non-admin, 48k allocatables). Wired behind the flag: `ClassificationGraphQLController.allocatables` (inline gate) + `AttributeDataFetcher` / `ConflictGraphQLController` / `StructuralTypeFetchers` (×3) via a single per-request `RequestCtx.canReadAllocatable` gate (readable-id set resolved once per request in `RequestContextInstrumentation`). Invalidation at the operator seam. **Single-entity gates left on `canRead`** (`allocatable(id)`, `WhereEvaluator` ref-recursion) — building the 48k set to check one id would pessimize when cold. Validation: §12 flip-equivalence MockMvc test (non-admin) + **all 82 GraphQL §12 leak tests re-run with `-Drapla.readmodel.authoritative=true` green** (monty restricted-view / idIn / where-predicate / anonymous / [PRD 069](069-graphql-resource-access-read-api.md) access) + flag-off suites green (no regression). |
| P5 | Run in-memory perf matrix (dhbwrapla real store) | ✅ done (2026-06-24) | **18.5× faster**: legacy 14.55 ms/query → in-memory 0.79 ms/query (dense 50 allocatables, top=2433, 1-wk late window); results byte-identical. Inverse of the H2 detour (1.1–3.7× slower). |
| P6 | **Window-first global read** (full-admin unscoped) — global singleton-key `IntervalIndex` + `reservationsInWindowGlobal` | ✅ done (2026-06-24) | one O(log N+k) lookup replaces the ~48k-allocatable loop for `isAdmin()` unscoped; filter-free (admin short-circuits canRead). Tier-1 singleton-key + tier-3 differential (window-first == resource-first) green. Merged from draft into [PRD 086](086-appointment-block-index.md). |
| 4b-iii | Delete `appointmentMap` (irreversible end-state) | ⛔ blocked | gated on P5 win + field-clean run |

Status legend: ⬜ not started · 🔄 in progress · ✅ done · ⚠️ blocked/needs decision. This table is the
durable progress record — updated as each phase lands.

### Phase 0 result (2026-06-24) — harness done & verified

Built in **the plugin-deployment repo** under `src/test/java/org/rapla/test/brute/`:
- `BruteForceFacadeSupport` — in-process `FileOperator`+`FacadeImpl` over a writable temp copy; small
  classpath `/testdata.xml` by default, real store via `-Drapla.brutetest.data=<path>` (`assumeTrue`-
  guarded; §17 — reads real data at runtime, asserts only ids/counts, never names).
- `AppointmentQueryOracle` + `QueryAppointmentsDifferentialTest` — **windowing-invariance** differential
  (all-time bindings filtered by plain-Java `overlaps()` == windowed query). Chosen over a binding-
  re-derivation oracle because `getAllocatablesFor` ≠ the `appointmentMap` binding (template-alloc +
  restriction divergence — see [PRD 086](086-appointment-block-index.md) "Binding semantics").
- `MutationBatteryTest` — 6 tests: create-with-conflict, delete, move-appointment, move-repeating,
  reassign-allocatable, fresh-allocatable create/use/remove; each re-asserts the invariant + conflict
  expectations through the mutation.

**Verification:** small fixture **7/7 green**; a **large production-shaped store** (order ~10^5
reservations) differential **passed in tens of seconds** (large heap, capped allocatable scope).
Harness proven *sensitive* (the binding-based first cut correctly went red) and *correct*. Run the real
differential by pointing the operator at an external store via `-Drapla.brutetest.data=<store path>`
(+ a large `-Xmx`), `assumeTrue`-guarded.
Real API signatures learned: `Appointment.moveTo(start)` / `move(start,end)`; `facade.remove(obj)` is
void+throws (no Promise); `facade.newAllocatable(Classification,User)`; repeating via
`appt.setRepeatingEnabled(true)+getRepeating().setType/​setNumber`.

### Phase 1 progress (2026-06-24) — foundation infrastructure done (option C)

New package `rapla-server/.../storage/impl/server/readmodel/`:
- `Projection` — SPI: `createSchema` + `table()` + `keyColumn()` + **`rows(Entity) → Collection<Object[]>`**
  (pure entity→rows transform; concrete projections implement it, real-entity-tested at Phase 2).
- `ReadModel` — owns one in-memory H2; **two layers**: a generic entity-free core
  (`upsert`/`deleteByKey`/`clearAll` = drift-safe delete-by-key + insert), and thin entity glue
  (`put`/`remove`/`rebuild`). `index == project(objects)` by construction; per-call commit (the operator
  seam will later batch one `UpdateResult` per transaction).
- `ReadModelTest` — **5/5 green, 0.3 s, pure unit** (no fixture, no Spring, **no entity stub**): drives
  the generic core with plain rows; covers idempotency, replace-by-key, absent-delete no-op, and the
  reproject-from-scratch == incremental invariant.

**Design decision (option C, agreed with user):** the foundation is entity-agnostic at the row level;
the only place real entities appear is the concrete projections' `rows()` (Phase 2). This removed the
hand-rolled `Entity` stub entirely — no fake rapla types in tests or production (§13). The generic
"delete-by-entity-id + insert rows" core is the same drift-safe maintenance PRD [086](086-appointment-block-index.md)/[087](087-classification-type-indices.md) specify and
Phase 0.5 proved is the hot path.

### Phase 4b-ii (2026-06-24) — appointment + conflict flips built, then MEASURED — ⚠️ do not flip

The `queryAppointments` read flip (`blockDerivedWindowedAppointments`) and conflict-candidate flip
(`blockDerivedConflictCandidates`) were implemented behind the same flag, with equivalence tests green
on the fixture. **A before/after perf measurement on the real store then produced two findings that
say: keep these OFF.**

**Finding 1 — the read flip is ~1.6× SLOWER at current scale.** Real store, 50 densest allocatables
(top collector ~2.4k appts), a 428-appt late week: legacy `appointmentMap` **13.6 ms/query** vs flipped
block-index **22.0 ms/query** (0.62×). The legacy in-memory `TreeSet` headSet + `overlaps()` is
~0.3 ms/allocatable; the block path's per-allocatable H2 `executeQuery` round-trip + per-row
`tryResolve` + `getDependent` expansion has a constant factor that dominates until collectors are far
larger than ~2.4k. **The PRD's ~670 ms floor is a different regime** (the full ~50k-allocatable GraphQL
path / N+1 field resolution), not this operator-level window read — so the block index does **not** win
`queryAppointments` at this scale. The real wins to (separately) measure: the **type-bucket** (avoids
the O(all) `getAllocatables` copy), **aggregation** (`blockStats` COUNT/GROUP BY), conflict narrowing
**only for very large collectors**.

**Finding 2 — a correctness divergence for aggregator allocatables.** At a 100-densest scope the
flag-on vs flag-off `queryAppointments` sets **diverge** for an aggregator allocatable with
`getDependent` children — the read-time `getDependent` expansion the flip uses ≠ the `appointmentMap`'s
`getDependentRef` expansion that `getAppointments` does. The fixture equivalence test and the Phase-3
"zero drift" shadow (first-250 scope) never hit that aggregator, so this was missed. **Root-cause
`getDependent` vs `getDependentRef` before any appointment-path flip.**

**Decision:** the appointment + conflict flips remain implemented but **OFF and not to be flipped** until
(a) Finding 2 is root-caused and fixed and (b) a regime is found where the block path actually wins.
Tests landed: `ReadModelReadFlipEquivalenceTest` (fixture, green), `QueryAppointmentsFlipPerfTest`
(dhbwrapla, the measurement above). The type-bucket flip (4b-i) is unaffected by these findings (different
mechanism) but should also be perf-measured before trusting its win.

### Phase 4b-i (2026-06-24) — the flip flag (reversible), type-bucket flipped

`LocalAbstractCachableOperator.readModelAuthoritative` — a `volatile` flip flag, default `false`, from
`-Drapla.readmodel.authoritative` (+ a package-private test setter). It is a **flip, not a deletion**:
the legacy structures stay maintained, so flipping back is instant. `getAllocatables(filters)` honors
it — off → legacy scan + shadow; on → served from the bucket (`bucketDerivedAllocatables`: resolve the
filter-types' candidates + the same `matches` filter, skipping the full-population copy/scan = the perf
win), with **fail-safe fallback** to the legacy scan on any read-model error.
`ReadModelFlipDifferentialTest` asserts flag-on == flag-off per type (behaviour-preserving + reversible;
the flip is licensed only while this is green). The `queryAppointments` + conflict consumers still return
legacy regardless of the flag — their flips ride the *same* flag (4b-ii), deferred as higher-risk on the
hottest path. Storage+readmodel 22/22; 189 §12 leak green.

### Phase 4a (2026-06-24) — 086 Stage Y conflict shadow (non-destructive)

Conflict detection funnels through `getAllocatableBindings(allocatables, appointments, ignoreList, …)`
(`getAppointments(A)` + `AppointmentImpl.getConflictingAppointments`). Added
`shadowCompareConflictCandidates` right before its return — sampled (≤ once/10 s, own
`lastConflictShadowAt`), guarded, side-effect-only. It checks the **Stage-Y safety invariant**: every
authoritative conflicting appointment must appear in the block-table **range candidates** (the qa
*envelope* `start_ts < qaMaxEnd AND end_ts > qaStart` over `getDependent`-expanded allocatables — a
deliberate superset, so a non-empty miss is a true gap). Logs only counts (§17); returns the
authoritative `map` unchanged.

**Verification:** `ConflictCandidateShadowTest` (real far-future conflict on one allocatable; the block
candidate set contains the conflicting appointment → completeness holds) green; rapla-server
storage+readmodel 21/21; 188 §12 leak tests green; dhbwrapla brute 7/7. The flip (4b) — make the block
table authoritative for reads + conflict and delete `appointmentMap` — is **irreversible and gated** on
an explicit go plus observing zero shadow drift in the field; the perf win (O(log n + k) conflict via
the range index vs. the full `SortedSet` scan) is realized there.

### Phase 3 (2026-06-24) — 086 appointment block index, Stage X (zero drift on the real store)

`AppointmentBlockProjection` (rapla-server `…/readmodel/`) projects each reservation to flat
`appointment_block` rows — the three row kinds (single / materialized ≤52 / open-ended rule row),
`block_id = appointmentId@allocId#idx`, binding mirroring `AppointmentMapClass.updateReservation`
(`getIds("resources")` + template-alloc + `getRestrictionForAllocatableRef`). Registered in the
operator's `ReadModel`, so it's maintained at the same seam.

**Stage-X shadow** in `queryAppointmentsSync`: a **sampled** (≤ once/10 s), **guarded**, side-effect-only
`shadowCompareAppointmentBlocks` compares the block-table window result against the authoritative
`appointmentMap` raw result and logs drift *counts only* (§17) — but returns the authoritative result
unchanged (zero behavioural change). The hot path is not doubled: throttled calls cost only a few
skip-guards + a `long` compare. Read-time **dependent expansion** (`getDependent`) mirrors
`getAppointments`'s belongs-to expansion (see the *Two implementation findings* in [PRD 086](086-appointment-block-index.md)).

**Verification:** `AppointmentBlockProjectionTest` (equivalence: block window == `queryAppointments`,
red→green) + `AppointmentBlockSeamTest` (boot/store/remove populate the table) green; rapla-server
storage+readmodel suite 20/20; **188 §12 leak tests green**; dhbwrapla brute 7/7. **Decisive: the real
production-shaped store (~10⁵ reservations, a few ×10⁵ appointments) differential passed AND the live
shadow logged ZERO drift** — the block index reproduces the legacy `appointmentMap` exactly at scale.

**Next (Phase 4, the cutover — a review gate):** move conflict computation onto the block-table range
query (Stage Y), the write-path benchmark gate, then — once field drift stays zero — flip reads/conflict
to authoritative and retire `appointmentMap`.

### Phase 1 seam + Phase 2 consumer (2026-06-24) — wired, shadow, zero regression

**Operator seam (Phase 1, `LocalAbstractCachableOperator`):** `readModel` field (built with the two
type-bucket projections); boot rebuild via `rebuildReadModel(events)` right after
`initAppointmentBindings`; incremental maintenance via `updateReadModel(result)` at the end of
`updateIndizes` (Add/Change → `put` the resolved entity; Remove → `remove` by id); `readModel.close()`
in `dispose()`. **Every read-model call is guarded** — a failure logs ERROR and never propagates to the
authoritative write path (Stage-X principle: the read-model is a non-authoritative shadow). The seam is
the same `updateIndizes(UpdateResult)` chokepoint the `appointmentMap` uses, so parity is structural.

**Type-bucket consumer (Phase 2, 087 Class 1) — Stage-X shadow.** `getAllocatables(filters)` is
overridden to query the `allocatable_type_bucket` for the filters' DynamicTypes and **compare it for
completeness** against the authoritative scan (logs drift if the bucket is missing any scan result),
but **returns the authoritative scan unchanged**. So behaviour is identical to today; the only effect is
a drift signal proving the bucket is complete before a later one-line flip makes it authoritative (the
actual perf win). The two concrete projections (`AllocatableTypeBucketProjection`,
`ReservationTypeBucketProjection`) implement `Projection.rows()` via
`((Classifiable)e).getClassification().getType().getId()`.

**Verification:** `ReadModelSeamPopulationTest` (tier-2, real entities) — boot rebuild populates the
bucket for every allocatable; store adds; remove deletes (3/3 green). Whole `rapla-server` storage +
readmodel suites green (0 failures). **138 §12 GraphQL leak tests green** (`ClassificationGraphQLControllerTest`,
`ReservationGraphQLControllerTest`, `ResourceAccessQueryGraphQLTest`) — zero regression, since the read
path returns the authoritative result.

**Next (post-review):** the reservation-bucket consumer (in the GraphQL `reservations()` path), then —
once shadow drift is observed to be zero in the field — the flip to returning bucket candidates (the
perf win), then Phase 3 (086 appointment index, the big one).

### Phase 0.5 result (2026-06-24) — H2 write-path PASS

`rapla-server/src/test/java/org/rapla/storage/readmodel/H2WritePathBenchmarkTest` (tagged `perf`,
opt-in via `-Drapla.h2bench=true`). Builds the real `appointment_block` schema + indices, measures
bulk projection + per-mutation re-projection. H2 added to **rapla-bom** (`h2.version` 2.3.232) +
**rapla-server** (compile scope).

- **Bulk projection:** 200k blocks in **5.13 s** (~39k rows/s → ~2.5 s per 100k) — beats the
  "~100k-block rebuild < ~5 s" gate.
- **Per-mutation re-projection:** **0.097 ms** (commit-per-mutation, `DELETE WHERE appointment_id=?` +
  re-INSERT) — beats the "< ~1 ms" gate by ~10×.
- **Finding:** the benchmark first ran at **28.35 ms/mutation** because the delete-by-appointment had
  no index → full table scan. Added `ix_block_appointment ON appointment_block(appointment_id)` → 290×
  faster. **[PRD 086](086-appointment-block-index.md) schema corrected** to make that index mandatory (the maintenance key). Verdict: H2
  write-path is comfortably fast enough; engine choice confirmed by measurement, not just assertion.

### Stage A findings (seam map + module placement) — locked 2026-06-24

**Mutation funnel is single-entry (low drift risk).** All writes converge:
`storeAndRemove()` → `dispatch()` → `refresh()` → `AbstractCachableOperator.update()`
(`addToCache()`/`cache.remove()`) → `LocalAbstractCachableOperator.updateIndizes()` →
`updateAppointmentBindings()` (maintains `appointmentMap`) → `conflictFinder.updateConflicts()`.
Boot/rebuild path: `connect()` → `cache.clearAll()` → `loadData()` → `initIndizes()` →
`initAppointmentBindings()`. No existing listener/SPI — but we own the class, so we hook directly.

**Projection seam (Phase 1):** `updateIndizes(UpdateResult)` in `LocalAbstractCachableOperator`
(rapla-server). It already receives the structured add/change/remove diff — **the exact same input
the `appointmentMap` shadow consumes**, so projection-vs-shadow parity is structural, not coincidental.
Boot rebuild plugs into `initIndizes()`.

**Module placement — decided:** the H2 read-model + all projections live in **rapla-server**,
co-located with `LocalAbstractCachableOperator` / `AppointmentMapClass` / `ConflictFinder`.
**rapla-core stays H2-free** (consistent with "no Spring Boot in core" — now "no H2 in core"). The
layering split discovered: `getAllocatables(ClassificationFilter[])` lives in **rapla-core**
(`AbstractCachableOperator:434`, the `new HashSet<>(cache.getAllocatables())` + filter-scan), but the
read-model is in rapla-server. Resolution: `LocalAbstractCachableOperator` (rapla-server, the subclass)
**overrides `getAllocatables`** to consult the H2 type-bucket and falls back to `super`'s scan — core
untouched, server adds the indexed path. Same override pattern for any core read method an index
accelerates.

**H2 is absent from the reactor** (HSQLDB 2.7.1 is current, [PRD 084](084-replace-hsqldb-with-h2.md)). Phase 1 adds
`com.h2database:h2` to **rapla-bom** (version management) + **rapla-server** (compile scope) — a
flagged POM change, surfaced before it lands.

**Recurrence API for the rule-row path (086):** occurrence count = `repeating.getNumber()` (computes
from end-date when bounded; `-1` + `getEnd()==null` ⇒ open-ended; `getMaxEnd()==null` is the ∞
sentinel). Materialize via `createBlocks(start,end,blocks)`; window-expand a rule row via
`processBlocks(winStart,winEnd,visitor,excludeExceptions)`; correctness check via
`overlaps(start,end,excludeExceptions)`. Exceptions are midnight-cut dates honoured inside
`processBlocks`.

**Harness data:** the real differential run points at an **external, production-shaped store** (order
~10^5 reservations, a comparable scale of appointments/permissions/persons; confirms the
single-appointment-dominant workload). The store holds **real personal data** → §17: the harness reads
it at runtime (`assumeTrue`-guarded via `-Drapla.brutetest.data=<path>`, never checked in), asserting
only on structural/count diffs, never on names. Heap-heavy: the in-process facade load needs a bumped
surefire `-Xmx`. The harness lives in the plugin-deployment repo, which keeps a **local copy** of
`FacadeTestSupport` (drift-risk noted in its header).

---

## Bestandsaufnahme — the current storage model (2026-06-22)

**One operator holds the entire dataset in `LocalCache` (RAM); the database is persistence +
change-log, never a query target on the hot path.**

### Backends
All backends extend `LocalAbstractCachableOperator` (rapla-server, ~4,450 LOC):
- `FileOperator` — single XML file (`data.xml`); dev default. **No database at all.**
- `DBOperator` (~1,270 LOC) — HSQLDB embedded **or** MariaDB/PostgreSQL over JDBC.

### Boot = full load
`DBOperator.connect()` → `loadData()` takes a write-lock and pulls the **entire store** via
`raplaSQLInput.loadAll()`, then `cache.putAll(list)` into `LocalCache`. From that point on,
**reads are never served from the database** — everything resolves from RAM. This is why the
Workstream-A latency tax (below) is a *RAM-scan* tax, not a DB tax.

### What `LocalCache` actually holds
- `entities` — master map `id → Entity` (everything).
- four parallel type maps `resources` / `reservations` / `users` / `dynamicTypes` (a second
  reference to the same object — cheap, pointers only, not deep copies).
- `graph` — `Allocatable → GraphNode` dependency graph (conflict detection / allocation).
- conflict maps + passwords.
- **No type-bucket index, no name index, no time-window appointment index** — exactly the gap
  Workstream A fills.

### The two roles of the cache (the load-bearing reframe)

| Role | Genuinely needs "all in RAM"? | Today |
|---|---|---|
| **A) Conflict substrate** (`AllocationMap` → `SortedSet<Appointment>` per allocatable; `ConflictFinder` iterates *all* allocatables × their full appointment sets) | **Yes** — overlap detection needs the full, time-sorted appointment graph | resident, time-sorted (effectively already an index) |
| **B) Read serving** (catalog lists, name search, analytics) | **No** — uses the cache only because it is there | O(all) RAM scan, no index |

The unbounded dimension is **reservations/appointments** (every booking, forever). Allocatables
(~~50k today) are bounded and near-static. The memory hog is the history — and Role A is what
pins it in RAM.

### Multi-pod = event-log replication
- The **update history is an append-only event log** (JSON change records, `EntityHistory`;
  supported types: Allocatable / DynamicType / Reservation / User / Category / Conflict /
  Preferences).
- The **`LocalCache` is a projection / read-model** of that log.
- Each pod **polls** the log every ~10 s from `lastUpdated`, rebuilds the affected entities from
  the history entries, and `refresh()`es its local cache (`refreshWithoutLock`). No distributed
  cache; **every pod holds a full RAM copy**. Writes coordinated via process/resource/global
  locks (`docs/architecture/locking.md`); reads are lock-free against the cache.

### Measured bottleneck (admin token, local production backend, 2026-06-22)
~50,000 allocatables (type `Raum` = ~2,000). Latency is driven by **allocatable-set size**, not by
the time window or result cardinality:

| Scope | `appointmentBlockStats` (group by Raum, 1 yr) | Factor |
|---|---:|---:|
| no scope (all ~50,000) | 901 ms | 1× |
| `typeKeyEq:"Raum"` (~2,000) | 201 ms | 4.5× |
| one building (`idIn`) | **23 ms** | **39×** |

Window scaling is flat (a 1-day, 3-block window already pays ~670 ms). Catalog
`allocatables(filter:)` ids-only: untyped `{}` = 155 ms / ~50,000 rows (dominated by
`canRead × ~50,000`); `typeKeyEq:"Raum"` = 22 ms / ~2,000 rows (storage-prefiltered, but still
`new HashSet<>(~50,000)` + a ~50,000-element scan inside `getAllocatables`).

---

## Modernization evaluation — is the storage model still zeitkonform?

### Strategic direction (working thesis, 2026-06-22): a CQRS read-model on real query-engine technology — not hand-rolled indices

The trigger for this PRD was "add a type-bucket index, then a name index, then a window-first
appointment index" — i.e. **hand-knit one index after another into the bespoke storage framework**.
The thesis that emerged: that is the wrong treadmill, and this is the right moment to bring the
self-built read/query layer onto real technology instead.

**Why hand-rolling loses structurally for GraphQL specifically.** A GraphQL query costs on two
levels, and a hand-rolled index only addresses one:

| Level | rapla today | Hand-rolled index helps? |
|---|---|---|
| **Root resolution** (`@QueryMapping allocatables(filter:)`) | full scan (901 ms) | **yes** — this is Workstream A |
| **Field resolution** (`@SchemaMapping` per derived field) | N+1 fan-out — **28 `@QueryMapping` + 18 `@SchemaMapping`, zero `@BatchMapping`** (no DataLoader/batching anywhere) | **no** — an index does nothing here (this is [PRD 035](done/035-graphql-foundations.md)'s per-field hot-spots) |

So even after hand-rolling every index, the `@SchemaMapping`/N+1 level stays slow → next you
hand-roll DataLoaders, then projection, then the next index. The treadmill never ends **because
with GraphQL the client composes the access pattern (filter × selection set), not the server** —
the patterns are open-ended. "One hand-optimization per access pattern" cannot keep up with an
open-ended pattern space. **A query engine is precisely the tool for access patterns not fixed in
advance — which is the definition of GraphQL.** The self-knitted framework and GraphQL are a
structural mismatch, not a tuning problem.

A real engine unifies both levels in one mechanism: filter args → indexed `WHERE` (root);
selection set → `SELECT` projection + one batched fetch (fields) instead of an N+1 object-graph
walk.

**Scope is everything — modernize the read-model, never the write/conflict/domain core.** The
bespoke framework does two jobs; only one is GraphQL-relevant:

- **Write / domain / conflict** (conflict detection, repeating-rule expansion, multi-pod
  replication) — valuable, risky, proven. **Do not touch.**
- **Read / query** (Role B — what GraphQL serves) — the slow, hand-indexed treadmill.

The direction is therefore **CQRS via a strangler-fig**, not a rewrite:

> The bespoke store stays the write/domain source of truth (incl. conflict detection). A real
> query engine — an **embedded in-memory SQL read-model**, fed from the same put/remove change
> stream — becomes the read side. GraphQL resolvers are redirected onto the engine **query by
> query**; root-filter indexing *and* field batching fall out together per migrated query. Nothing
> breaks: any not-yet-migrated query runs exactly as today.

This reframes Workstream A: its hand-rolled indices are not *wrong*, they become **moot** — you
don't build 3 indices, you build the read-model **once**, after which every index and projection
is an engine feature (`CREATE INDEX`, a one-liner, correctly maintained by the engine rather than
by hand on put/remove). It also fixes the level (field N+1) that no hand-rolled index ever could.

**Decided forks for this direction** (engine choice + where the domain logic lives):
- **Engine: H2 in-memory — LOCKED (MQ7 resolved 2026-06-24).** In-process, SQL, MVStore/MVCC
  (clean fit for the Stage-Y sync write-through), JSON column for Class-2 attributes, and a
  built-in full-text index. It is also the engine [PRD 084](084-replace-hsqldb-with-h2.md) consolidates the *persistence* backend
  onto, so the whole stack runs one embedded engine. DuckDB (columnar analytics fit) and Calcite
  (SQL-over-objects, no data movement) are noted as future levers if the analytics path
  (`appointmentBlockStats`) ever needs a columnar engine, but the default and the prototyping
  target is H2.
- The hard boundary: does conflict detection / repeating expansion (`AppointmentImpl.processBlocks`)
  stay imperative Java over the object graph (engine serves Role B only), or can interval-overlap
  move into the engine (e.g. Postgres `tstzrange`+GiST if the backend goes Postgres-first)? → MQ8.
- Read-your-writes: the read-model is fed from the change stream, so it inherits an eventual-
  consistency lag vs the write store on the same pod — acceptable for Role B reads, but confirm no
  write-after-read GraphQL flow depends on immediate consistency. → MQ9.

### Verdict on the *pattern*
Stripped of labels, rapla implements **event-sourcing + CQRS with an in-memory read-model**: the
update history is the event log, `LocalCache` is the projection, multi-pod is log replication.
That is **not** a dated concept — it is what one would deliberately build today. The foundation
is sound. What has aged is **three mechanical choices**, not the concept:

1. **Polling instead of push (the 10 s).** Each pod polls the log every ~10 s. The modern
   replacement is push — Postgres `LISTEN/NOTIFY`, Redis pub/sub, or a broker. With Spring Boot 4,
   `LISTEN/NOTIFY` is nearly free; the consistency window drops from 10 s to ms **without touching
   the model.** High value, small change. (Does not apply to `FileOperator`.)
2. **100% replication per pod.** Memory ∝ `dataset × pods` — you can only scale *up* (every pod
   carries everything), never *out*. Modern grids shard or use near-cache + backing store. **But**
   this collides with the domain: global conflict detection *wants* a complete single-node view of
   the appointment graph; sharding tears cross-shard conflicts apart. Here "not contemporary"
   actually means "deliberately matched to the workload" — so this is the lever we do **not** pull
   at the target scale (below).
3. **DB used as a dumb change-log, not a query engine.** rapla re-implements in RAM (type buckets,
   name search, time windows — Workstream A) what Postgres does better with B-tree/GIN indices.
   This is the real "not contemporary" smell: holding ~50k+ objects resident to scan them linearly
   while an indexed database sits idle beside it.

### Target scale (decided 2026-06-22)
- **Allocatables × 2** (~96k) — modest growth, near-static dimension.
- **Reservations × 5** — the **unbounded, RAM-pinning** dimension grows the most.

This stays **below** the Kafka/grid/sharding threshold — so lever (2) (sharding/distributed grid)
is **out**. The asymmetry matters for **read performance at scale**: reservations ×5 means ~5×
more appointments to scan/expand per windowed query, so the read path degrades fastest exactly on
the growing dimension — reinforcing that an indexed/engine-served read-model (the primary
objective) is the lever, not a footprint fix. Footprint is the *secondary* read: the same ×5
growth would eventually pressure RAM too, which is why the slim/heavy split and parking are kept
as related opportunities — but they are judged by whether they also help read latency, not on
footprint alone.

### Today's footprint bound: the archiver (and why it is the wrong tool kept for the right reason)

> **DEFERRED (2026-06-24 scope: all-in-memory).** Everything below in this footprint/archiver/
> parking/governance block is **future work** — the current scope keeps the full dataset resident in
> RAM and pursues only the in-memory read-model for read performance. The existing archiver
> (time-based deletion) is **left exactly as-is**; the "park, don't delete" evolution and the
> governance decoupling are revisited when footprint becomes the driver. Retained here as the
> analysis to resume from, not as active work.

rapla already bounds reservation growth — **by deleting**. The `archiver` plugin
(`ArchiverServiceTask`, hourly `@Scheduled`; `ArchiverServiceImpl`) reads `removeOlderThan` days
from system preferences (default `-20` = disabled; a production deployment sets it to ~365 → "events
older than 1 year are deleted") and, each hour, **hard-deletes** every reservation whose
appointments are all older than the cutoff (`raplaFacade.removeObjects`), optionally preceded by a
*full* DB export (`importExportManager.doExport`, DBOperator-only) as a backup.

Two observations that reshape this PRD:

1. **The deletion predicate is exactly the windowing predicate option 3 needs.**
   `ArchiverServiceImpl.isOlderThan(event, cutoff)` returns true **only if every appointment's
   `start` *and* `maxEnd` precedes the cutoff** — so an infinite/unbounded repeating appointment
   (`maxEnd` far in the future) is never "old". This is precisely the "keep resident iff any block
   reaches ≥ window-start" rule MQ3 was worried about deriving from scratch — it already exists and
   is battle-tested in production. **MQ3's correctness core is solved.**

2. **Today rapla trades *data* for memory; the modernization is to trade *cache residency* for
   memory instead.** Deleting >1yr events is a destructive memory-management strategy: the history
   is gone (unless a prior full export survives). Option 3 is the **non-destructive evolution of the
   archiver** — same predicate, but **evict from the `LocalCache` instead of deleting from the
   store**. The DB keeps everything; the cache holds the window; old reservations are lazy-loaded
   on demand for historical reads. Strictly better: identical RAM benefit, **zero data loss**,
   history stays queryable. This is textbook hot/cold tiering — and it is *more* standard-conform
   than today, not less.

So the archiver is not a competitor to this PRD; it is the **existing, crude, destructive
prototype** of option 3. The work is to keep its windowing logic, drop the deletion, and move the
boundary from the store to the cache.

3. **"Park, don't delete" — with explicit on-demand restore for long-range analytics.** The
   eviction need not be fully transparent. The user-facing concept is **parking**: old
   reservations leave the hot working set (cache + conflict detection) but remain in a queryable
   parked tier (the DB itself, or an exported parked store), and can be **deliberately restored**
   when a specific evaluation needs them — e.g. a multi-year room-utilization report that genuinely
   wants history beyond the window. This gives three states instead of today's two (live /
   deleted):
   - **Hot** — within the window; resident; in conflict detection; instant.
   - **Parked** — beyond the window; not resident; not in conflict detection; reachable via an
     explicit restore for analytics.
   - (deletion becomes opt-in and rare, not the default memory strategy.)

   Design tension to settle (→ MQ5): is restore **transparent** (a historical query silently
   lazy-loads the parked range, then re-evicts) or **explicit** (an admin/report action
   "un-parks" a date range into a query scope for the duration of the evaluation)? Transparent is
   friendlier but risks a single broad historical query re-inflating the heap it was meant to
   bound; explicit keeps the footprint guarantee but needs UI/affordance. Likely answer:
   **explicit, analytics-scoped restore** — a parked range is loaded into a *temporary, separate
   query scope* (not back into the shared conflict-detection cache), used for the report, then
   dropped. That preserves the hot-set footprint guarantee while still serving the rare
   "I need 5 years of data" case.

### Data governance: parking is NOT a substitute for deletion (the two must be decoupled)

Today the archiver conflates two concerns that are actually independent — and a naïve
"park, don't delete" would silently drop the one that is legally mandatory:

- **Memory tiering (parking)** — reversible, footprint-driven, our concern here. Old data leaves
  RAM but stays recoverable.
- **Retention / erasure (deletion)** — irreversible, **governance-driven**, legally required.
  Some data *must* be deleted: statutory retention limits, GDPR Art. 17 right-to-erasure (on
  request, **not** time-based), per-deployment data-protection policy. Parking such data instead
  of deleting it is a compliance violation, not a feature.

So the design must keep a **separate, deliberate deletion/erasure path** alongside parking — and
that path must reach the parked tier too (you cannot erase what you have only evicted from cache:
the DB still holds it). Concretely:

- Splitting parking from deletion is the *right* refactor of the archiver: today its time-based
  delete happens to satisfy retention by accident; making it explicit means retention becomes a
  governance-configured policy, not a memory hack.
- Erasure may be **anonymization rather than full deletion** for some categories (drop the
  person-linked classification attributes, keep the anonymous booking for statistics) — that is a
  governance decision per data category, not a storage default.
- Right-to-erasure is **request-triggered and exact** (delete *this* person's data now), orthogonal
  to both the window and the retention schedule — it must hit hot, parked, *and* any exported
  parked store.

**This requires clarification with whoever owns data governance for the deployment** (→ MQ6)
before the parking design is finalized: what must be hard-deleted vs anonymized vs retained, on
what schedule, and how right-to-erasure requests are serviced across the tiers. The PRD should not
assume "keep everything forever" any more than it should assume "delete after a year".

### Is there a move that is faster AND smaller AND more standard-conform — all at once?
**Yes — not a single switch, but the separation of the two cache roles:**

- **Push read-serving (Role B) to the DB with real indices.** Wins on **all three axes
  simultaneously**: *faster* (indexed SQL beats the O(all) RAM scan for catalog/search/analytics),
  *smaller* (you no longer hold the bulk resident merely to scan it linearly), *more
  standard-conform* ("the database does queries" is the textbook pattern; replacing a hand-rolled
  in-memory index with a DB index is as standard as it gets).
- **Keep Role A in RAM, but time-windowed.** Only reservations with appointments in
  `[now − margin, ∞)` stay resident for conflict detection; historical reads come lazily from the
  DB. Bounded working set = standard caching. **At ×5 reservations this is where the memory is
  actually won** — the historical tail is the bulk of the 5× growth and rarely participates in new
  conflicts.

The earlier worry that "indices cost memory" (perf vs footprint pulling against each other)
**dissolves once reads move to the DB** — for the first time perf and footprint pull the same
direction.

The one catch keeping this from being a clean single-step win on *every* backend: `FileOperator`
(XML) has no DB to push reads to or lazy-load from, and the conflict substrate's appointment graph
stays resident regardless. So the all-axes win is real but scoped to **Role B on DB backends**;
`FileOperator` keeps the full-load model (acceptable — it is the dev/small-install backend).

### The central query is the appointment block — moved to [PRD 086](086-appointment-block-index.md)

The slim/heavy split (the hot query needs only ids+time, not classifications) and the decoupling of the slim appointment projection from the heavy payload are the heart of the **appointment block index** — see **[PRD 086](086-appointment-block-index.md)**. The *Workload shape* measurement below stays here as the program-wide fact base it informs.

### Workload shape: the store is overwhelmingly single-appointment (measured)

Counting appointment types across **two real production-scale stores** (each on the order of a few
hundred thousand appointments, ~10⁵ reservations) gives a decisive, consistent picture:

| Property | Order of magnitude |
|---|---|
| Total appointments per store | hundreds of thousands (~2–3 × 10⁵) |
| **Single (non-repeating) appointments** | **~98%** |
| Repeating appointments | low-single-digit percent (~2%) |
| Absolute repeating count | a few thousand — **roughly constant across stores regardless of total size** |
| Appointments per reservation | near 1 (≈1.5–3) |
| Time concentration | ~95% of appointments fall within the current + next year |

The dominant real pattern is **semester/term scheduling that materializes every session as its own
dated single appointment** — not as a repeating series. So repeating is the **~2% exception, and it
does not grow with the dataset** (the absolute repeating count was nearly identical between a
small-fan-out and a large-fan-out store); the dimension that scales is the single dated appointment.

**This reshapes the appointment-index design — repeating expansion is *not* the hot path:**

1. **For ~98% of appointments `start`/`end` IS the block.** The coarse range predicate
   `start < winEnd AND end > winStart` is then **exact, not a candidate filter** — no Java
   `processBlocks` post-expansion. The MQ8 "engine narrows, Java expands" mechanism applies only to
   the ~2% repeating subset.
2. **The aggregation-accuracy tension largely dissolves.** Block count ≈ row count for the single
   majority → `appointmentBlockStats` aggregates ~98% of the data **exactly in SQL** (`COUNT`,
   `GROUP BY`); a small Java correction expands only the repeating rows. (This is why the index can
   promise more for the stats paths than a recurrence-centric view assumed.)
3. **The row stays uniform:** `(alloc, resv, appt, start, end, is_rule)` — the common single case and
   materialized occurrences are exact blocks (`is_rule=false`); only the open-ended tail is an
   `is_rule=true` row whose rule lives in the resident object. The "materialized blocks" variant is
   cheap because only the small, roughly-constant repeating subset is involved; the singles are
   already blocks.
4. **No recurrence-collector hot-spot.** Per-allocatable density comes from many *single* dated
   appointments (which a range index serves cleanly), not from a few high-fan-out repeating series —
   so the Stage-Y write-path benchmark sits in the "index wins" regime, not the borderline
   high-recurrence-density regime.

Net: **rapla is at its core a single-appointment store with a small recurrence annotation** — the
index should be optimized for the dated single as the overwhelming common case, with repeating as a
small annotated (or materialized) subset.

### Appointment index — moved to [PRD 086](086-appointment-block-index.md)

The flat `appointment_block` table, the `is_rule` representation decision, the index-exact slot filter, dual-API (RemoteStorage + GraphQL), conflict detection, and the migration/test strategy now live in **[PRD 086](086-appointment-block-index.md) — appointment block index**.

### Options (smallest → largest)

> **Program map (2026-06-24):** these options are now realized across PRDs — option 0/the appointment
> lever = **[PRD 086](086-appointment-block-index.md)**; option 1 (type-bucket) = **[PRD 087](087-classification-type-indices.md)** + name search = **[PRD 085](085-search-name-indexing.md)**; the permission
> index = **[PRD 083](083-user-change-subscription.md) Part A**; option 3 (parking) = **deferred** (all-in-memory scope). The table stays
> as the conceptual overview the split was derived from.

| # | Option | Faster | Smaller | More standard | Effort | At target (alloc ×2 / resv ×5) |
|---|---|:--:|:--:|:--:|---|---|
| **0** | **Slim appointment-block projection, decoupled from heavy payload** (the pivot above) | ✅✅ | ✅✅ | ✅ | medium–high | **the central lever — serves the hot query + conflict detection on slim data; heavy payload becomes independently evictable/parkable** |
| **1** | In-memory indices only (Workstream A: type-bucket + name) | ✅ | ❌ (adds a little) | ➖ | low | ship — perf floor; allocatable ×2 keeps the scan cost bounded |
| **2** | **In-memory indexed read path for Role B** (the in-process **H2 read-model**, SQL indices) — *not* reads from the disk DB | ✅ | ➖ (resident) | ✅ | medium | the indexed read path, fully in RAM; the "smaller" axis is deferred (everything stays resident) |
| **3** | **Window / park the heavy payload** (evict old reservations' classification; lazy/parked historical) | ➖ | ✅✅ | ✅ | medium | **DEFERRED (2026-06-24 scope: all-in-memory)** — footprint + governance lever; revisit when footprint becomes the driver |
| **4** | Transport poll → push (`LISTEN/NOTIFY`) | ➖ (consistency, not throughput) | ❌ | ✅ | low–med | nice, orthogonal |
| **5** | Per-entity footprint (string interning, shared classification structures) | ❌ | ➖ | ➖ | low | micro, orthogonal |
| — | Distributed grid / Kafka / sharding | — | — | — | high | **rejected at this scale** |

Note option 0 reshapes 1/3: the "window-first appointment index" folds into option 0's slim
projection, and option 3's windowing now targets the *heavy payload* (not the appointment data,
which stays resident cheaply in slim form).

### Recommendation
1. **Phase 0 — measure first.** Heap composition on a production store (object histogram), reservation
   + appointment counts, pod count, concurrent-user load. Crucially, **measure the heavy/slim
   split**: how much of the heap is appointment time-data vs classification/attribute payload? That
   ratio decides how much option 0 alone buys.
2. **Option 0 is the central lever** — decouple the slim appointment-block projection from the
   heavy payload. It directly attacks the hot query (faster). It *also* unpins the footprint hog from
   the conflict index, but under the all-in-memory scope that footprint payoff is **latent** — both
   slim and heavy stay resident; the win we bank now is purely the compact, time-indexed hot-query
   structure. This is the structural heart of the PRD.
3. Then **(2) the in-memory H2 indexed read path** (materialize only result rows from the resident
   heavy payload), with **(4) push transport** as the cheap consistency upgrade if needed. Standard
   Spring Boot idioms, no exotic infra. **(3) window/park the heavy payload is deferred** (scope:
   all-in-memory) — revisit with the governance-decoupled retention policy (MQ6) when footprint
   becomes the driver.
4. **Workstream A (option 1)** ships independently as the in-memory perf floor and is largely
   self-contained (and partly shipped, see D4) — cheapest immediate latency win for the
   catalog/search path, now partly subsumed by option 0.

### Migration approach & write-path validation — moved to [PRD 086](086-appointment-block-index.md)

Stage X→Y (read-model beside the conflict core, then conflict onto the engine with the `appointmentMap` shadow oracle), the sync-local feed model, and the write-path benchmark are in **[PRD 086](086-appointment-block-index.md)**. The cross-pod poll→push deprioritization stays a foundation note (see MQ-list / transport below).

### Open questions (modernization)

> **Deferred under the all-in-memory scope (2026-06-24): MQ3, MQ4, MQ5, MQ6** all concern
> windowing / parking / DB-offload / governance, which are not in the current scope. They stay
> recorded for when footprint becomes the driver; the active questions are MQ1–MQ2 (sizing) and
> MQ7–MQ9 (the in-memory read-model engine/boundary/consistency).

- **MQ1** — Phase 0 numbers: confirm reservations/appointments dominate the heap (expected, given
  resv ×5) — quantify the historical-tail fraction to size the option-3 win.
- **MQ2** — Is a DB-indexed read path acceptable to maintain alongside the cache (two read paths,
  consistency story), or does the team prefer in-memory indices only and accept the footprint?
- **MQ3** — Conflict-window margin: how far back must the resident appointment graph reach for
  correct conflict detection (recurring appointments, long-running reservations)? The *predicate*
  is already solved (`ArchiverServiceImpl.isOlderThan` — all blocks before cutoff, infinite
  repeats never old); the open part is only the **margin value** (how far past the cutoff to keep
  resident as a safety buffer) and whether template reservations are exempt (the archiver exempts
  them).
- **MQ4** — `FileOperator` divergence: confirm it stays full-load (no DB to push to / lazy-load
  from); the DB-backed options bifurcate the two backends — is that acceptable?
- **MQ5** — Restore semantics for parked reservations: **transparent** lazy-load (a historical
  query silently un-parks its range, then re-evicts — friendly, but a broad query can re-inflate
  the heap) vs **explicit analytics-scoped restore** (a report un-parks a date range into a
  temporary separate query scope, never back into the conflict-detection cache — preserves the
  footprint guarantee, needs an affordance). Leaning explicit; confirm against the real analytics
  use cases (which reports actually need beyond-window history?).
- **MQ6** (governance — blocking) — Decouple parking from deletion: clarify with data governance
  what data **must** be hard-deleted vs anonymized vs retained, on what retention schedule, and how
  GDPR Art. 17 right-to-erasure requests are serviced across hot / parked / exported tiers.
  Parking must not become a backdoor that keeps data the deployment is legally required to delete.
  *Resolution:* pending — open with the deployment's data-protection owner before finalizing the
  parking design.
- **MQ7** (strategic direction) — Engine choice for the CQRS read-model. *Resolution:* **H2
  in-memory — LOCKED 2026-06-24.** In-process SQL, MVStore/MVCC (fits Stage-Y sync write-through),
  JSON column (Class-2 attributes), built-in full-text; and the same engine [PRD 084](084-replace-hsqldb-with-h2.md) consolidates
  the persistence backend onto. DuckDB (columnar `appointmentBlockStats`) and Calcite (SQL over the
  live object graph, no data movement) retained only as future levers if the analytics path needs a
  columnar engine — not the default.
- **MQ8** (strategic direction) — Domain-logic boundary: candidate-narrowing vs precise overlap.
  *Resolution:* **largely settled by the measured workload shape.** The engine serves the
  **candidate range query** (`start < winEnd AND end > winStart` on `(allocatable_id, start, end)`);
  Java keeps the **precise overlap** (`processBlocks`, exception/midnight-daily rules). Because ~98%
  of appointments are single, the coarse predicate is **exact** for them (the row IS the block, no
  expansion) and Java `processBlocks` runs only on the ~2% repeating subset. So the boundary is:
  *engine narrows + serves singles directly; Java expands only the small repeating annotation.*
  Moving interval-overlap fully into the engine (Postgres `tstzrange`+GiST) stays a future
  Postgres-first lever, not needed for the H2 default. Residual: decide single-row vs
  materialized-block representation for the repeating subset (cheap either way at ~2%).
- **MQ9** (strategic direction) — Read-your-writes: the read-model is fed from the change stream,
  so it lags the write store on the same pod. Acceptable for Role B reads, but confirm no
  write-after-read GraphQL flow depends on immediate consistency (e.g. mutate then re-query in the
  same SPA interaction). *Resolution:* pending — audit the SPA's mutate→refetch flows.

---

# Workstream A (type-bucket index) — moved to [PRD 087](087-classification-type-indices.md)

The in-memory type-bucket index, the `buildStorageFilter` `typeKeyIn`/B′ pushdown, and the on-the-fly Class-2 classification-attribute indices are now **[PRD 087](087-classification-type-indices.md) — classification & type indices** (GraphQL-only). The name/full-text search index is **[PRD 085](085-search-name-indexing.md)**.

# Read-model architecture (technical foundation)

The technical substrate the strategic direction (CQRS in-memory SQL read-model) and both workstreams sit on. Engine: **H2 in-memory** (in-process, SQL, JSON, MVCC, full-text) — **LOCKED, MQ7 resolved 2026-06-24** (same engine [PRD 084](084-replace-hsqldb-with-h2.md) consolidates persistence onto).

## Data flow & roles

Three roles, one maintenance seam:

- **DB** = durable source of truth (persistence, survives restart, shared across pods).
- **LocalCache (object graph)** = canonical in-memory working copy for the **write/domain** side (conflict detection, business logic, hydration source). Per pod.
- **H2 read-model** = the indexed **query surface** for reads. A *derived projection*, per pod, volatile.

The read-model is **not** a pure async stream projection — it is fed exactly like the existing cache:

| Trigger | Flow |
|---|---|
| **Boot** | DB full-read → hydrate LocalCache → project into H2 (from the loaded objects) |
| **Local write** | mutation → **DB persist + LocalCache + H2**, all synchronous in the same write lock |
| **Remote write** (other pod) | history **delta** via poll → LocalCache + H2 (not a full re-read) |
| **Read (GraphQL)** | H2 (filter/range/aggregate → **ids**) → LocalCache (hydrate heavy fields by id, only if the selection asks, gated by `canRead`) |

Key invariant: **H2 is projected *from the entity* at the `put`/`remove` seam — never a parallel DB read.** One source (the entity stream through put/remove), two consumers (the cache's derived structures + H2). This is what keeps it drift-safe.

## Index classes — structural (static) vs dynamic-attribute (on-the-fly)

| | **Class 1 — structural** | **Class 2 — classification attributes** |
|---|---|---|
| Fields | `allocatable_id`, `start_ts`, `end_ts`, `event_id`, `type_id`, name | per-DynamicType attrs (`capacity`, `building`, `year`, …) |
| Known at schema time? | yes — every deployment has them | no — admin/deployment-defined |
| Always hot? | yes (block query, type filter, omnibox) | mostly cold — most never filtered |
| How | **real relational columns + static indexes** (btree / fulltext) | **JSON column + lazy functional indexes**, pay-for-use, with a budget |

- **The hot path (the block query) is pure Class 1** — `(allocatable_id, start_ts, end_ts, event_id, repetition_*)`, plain columns, static composite index. No JSON, no on-the-fly. The dynamic/JSON machinery touches only the secondary **attribute-filter** dimension (`where:{Raum:{capacity_gt:30}}`) and can never slow the hot path.
- **On-the-fly (Class 2):** store classification as a `JSON` column; the *first* time an attribute is filtered, create a typed generated column (`CAST` driven by the **DynamicType's known attribute type** — correct int/date/string ranges) + index it lazily. Pay-for-use; only used attributes cost memory/write-maintenance.
- **Promotion (the bridge):** a Class-2 attribute that proves hot in a deployment can be promoted to a permanent typed indexed column (usage- or schema-driven). Cold attributes never get indexed.
- **Budget:** adaptive indexing needs an LRU/threshold cap on on-the-fly indexes — each adds per-write maintenance (couples to the Y write-path measurement); unbounded auto-indexing over-indexes.
- Caveat: H2's JSON indexing is weaker than Postgres `jsonb`/GIN; if multi-attribute predicates become hot, that is the Postgres-first argument (MQ8/engine).

## Drift safety — the index is a pure projection, never independently mutated

Drift = two things mutated independently that are expected to agree. The read-model is engineered so "drift" reduces to "a bug in the pure projection function `project(entity)`" — testable, and recoverable by rebuild.

1. **One seam** — maintain the index *only* from the `put`/`remove`/`putAll`/`refresh` chokepoint (the same funnel that already maintains `entities` + type maps + `graph`, for local *and* cross-pod changes). Never from scattered controllers → no path can be missed. Inherits the proven correctness of the existing derived structures.
2. **Atomic + ordered** — index update inside the same write lock as the object update; no race, no partial window. H2 MVCC bounds a concurrent reader to the pre-commit snapshot (same staleness class as today's cache; reads are id-first so a slightly-stale id set is harmless).
3. **Idempotent upsert** — per put: `DELETE WHERE id=? ; INSERT project(entity)`. Kills the type-change/re-put drift class *by construction* (no incremental-delta edge cases — the PRD-082 hand-rolled trap).
4. **Rebuildable** — the index is a pure projection; boot rebuilds it; worst-case recovery is drop-and-rebuild from the object graph. The source (objects/DB) is always truth; the index is disposable.
5. **Verified** — property test asserts the invariant `index == project(objects)` after random put/remove/refresh sequences; optional low-frequency runtime reconciliation diffs index vs re-projection and alarms on mismatch.

## Boot rebuild

- **Class 1 (structural): rebuilt every boot, every pod** (in-memory H2 is volatile) — consistent with "index = disposable projection". Cost is an **in-memory transform of the already-loaded objects** (not a second DB read). Optimize with **bulk-insert then `CREATE INDEX` once** (faster than per-row index maintenance); optionally build in the background and serve from the object graph until ready.
- **Class 2 (attributes): NOT built at boot** — only the JSON column (data) is populated; functional indexes materialize lazily on first filter use.
- **Do not persist H2** to skip the rebuild: a persistent index can drift from the store while a pod is down / across pods, reintroduces a boot-time reconciliation problem, and complicates multi-pod. Rebuild-at-boot preserves the "fresh = never drifted" guarantee.
- **Future (with lazy-load, frame-breaker C):** populate H2 directly from the source DB's `APPOINTMENT` table via bulk copy, skipping object hydration. For the first increment, build from the loaded objects.

---

# Workstream B (permission-scoped read index) — moved to [PRD 083](083-user-change-subscription.md)

The inverted `access_grant` index (carrying the access *level*), caller-side hierarchy expansion, the stateless caller-context cache, and the §12 correctness bar are now part of **[PRD 083](083-user-change-subscription.md) — permission-scoped read index + user change-subscription** (GraphQL-only; the old RemoteStorage path keeps its amortized per-session permission filtering). [PRD 083](083-user-change-subscription.md) both *owns* this read index and *consumes* it for the `changesSince` subscription.
