# PRD 086 — appointment block index (in-memory `IntervalIndex` over blocks, dual-API)

**Status:** draft — 2026-06-24 (**pivoted to in-memory** — the H2 design below was built, measured, and lost; see *The H2 detour*)
**Related:** [PRD 082](082-storage-memory-model.md) (storage memory model — the in-memory index catalog + shared `IntervalIndex`/`BucketIndex` kinds + the put/remove maintenance seam this builds on; the H2 pivot is recorded there), [PRD 064](064-graphql-conflicts-read-api.md) (GraphQL conflicts read API), PRD [079](079-graphql-grouped-aggregates.md)/[080](done/080-typed-entity-stats.md) (grouped aggregates / typed-entity stats — `appointmentBlockStats`), [PRD 055](055-graphql-events-read-api.md) (GraphQL events read), `docs/architecture/locking.md`

**Split from [PRD 082](082-storage-memory-model.md), then pivoted from H2 to in-memory.** The appointment index is the largest piece
of the storage read-path modernization, and — unlike the classification ([PRD 087](087-classification-type-indices.md)), permission (PRD
083), and search ([PRD 085](085-search-name-indexing.md)) indices, which are GraphQL-only — it is **dual-API**: it sits *below* both
the old RemoteStorage protocol and the new GraphQL API (both call the same operator query/conflict
path), so it carries the heavier test obligation (the old-API record/replay + brute-force oracle).
The original design put this in **H2 (in-process SQL)**; that was built, flipped behind a flag, and
**measured 1.1×–3.7× slower** than the legacy in-memory `appointmentMap` (~91% JDBC boundary cost — see
*The H2 detour* and [PRD 082](082-storage-memory-model.md)). The design is therefore an **in-memory `IntervalIndex` over materialized
blocks** — the shared kind defined in [PRD 082](082-storage-memory-model.md)'s index catalog, instantiated **twice** (one keyed by
allocatable, one by owner), built on rapla's existing `AppointmentMapClass` structures and lock model.

## Problem

rapla's hot path is **"appointment blocks for these allocatables in this time window"** — the
calendar render, the conflict check, `appointmentBlocks` / `appointmentBlockStats`. Today it is
served from `AppointmentMapClass` (`LocalAbstractCachableOperator`): per-allocatable
`SortedSet<Appointment>` ordered by **start only**.

The measured pathology ([PRD 082](082-storage-memory-model.md)): latency is a **fixed floor independent of the window** — a 1-day,
3-block query already pays ~670 ms; growing the window 365× (to a year, 100× the blocks) only moves
it to ~882 ms (1.3×). Cause: `headSet(start < winEnd)` applies only the **upper** time bound; the
**lower** bound (`maxEnd > winStart`) is checked per-appointment via `overlaps`. For a window late
in the dataset the headSet is ≈ *all appointments ever started*, so cost ∝ (allocatables ×
appointments-before-winEnd), not ∝ result. At reservations ×5 this scan tax grows with total data.

## Workload shape (measured — see [PRD 082](082-storage-memory-model.md) for the full numbers)

Orders of magnitude across two production-scale stores (each ~10⁵ reservations, a few × 10⁵
appointments):

- **~98% of appointments are single** (non-repeating) — `start`/`end` *is* the block.
- **~2% repeating**, an absolute, roughly-constant few thousand that **does not grow** with the
  dataset; of these a large, store-dependent fraction (~¼ to ~½) is **open-ended** (no count, no
  end-date); where bounded, occurrence counts are tiny (median a handful, p95 ~6–12, ~none over 50).
- **Per-allocatable density is heavily skewed:** median single-digit, p95 ~10³, a worst-case
  **collector allocatable carries tens of thousands** of appointments.

This makes the index design single-appointment-first, with repeating as a small annotated subset.

## The index — an in-memory `IntervalIndex` over blocks

A plain Java structure ([PRD 082](082-storage-memory-model.md)'s shared `IntervalIndex` kind), **no database**. One *block* = one
concrete dated occurrence; the single-appointment majority is 1 block. Per key:

```
class IntervalIndex<K> {                       // K = allocatableId  OR  userId (two instances)
  Map<K, NavigableSet<Block>> byStart;         // blocks sorted by start (ConcurrentSkipListSet — lock-free reads)
  Map<K, Set<Block>>          openEnded;        // the side-set: open-ended / over-cap rule entries
  volatile long maxBlockDuration;               // capped at threshold D (a few days); high-water-mark
}
// Block = (appointment ref, start, end)   — exact concrete interval; no rule data stored
```

**Query** `overlapping(key, winStart, winEnd)`:
```
candidates = byStart.get(key).subSet(winStart − maxBlockDuration, true, winEnd, false)   // tight two-sided slice
           ∪ openEnded.get(key)                                                            // always-scanned tail
→ for exact blocks: the slice IS the answer (no overlaps() call)
→ for open-ended entries only: appointment.overlaps(winStart,winEnd) recurrence check
→ dedup blocks → appointments
```

This fixes the half-range pathology (the lower bound is now indexed via the `subSet` lower key
`winStart − maxBlockDuration`, not a per-item scan) **and eliminates the `overlaps()` recurrence call
for the ~98%+ exact blocks** — recurrence math survives only on the small open-ended side-set. Both
wins come from materializing bounded recurrences into short concrete blocks (a long *envelope* would
fall into the side-set; a short *block* stays in the tight slice). Cost: a cheap blocks→appointments
dedup on the way out (a weekly event can have several blocks in a wide window → one appointment).

**Two instances, shared class (locked 2026-06-24):** `IntervalIndex<allocatableId>` replaces
`appointmentMap`, `IntervalIndex<userId>` replaces `appointmentUserMap`. Blocks are the *same objects*
referenced from both (reference-shared, not copied), so two instances cost no extra memory; the
separation keeps the keys type-clear, the maintenance explicit, and lets the user index hold
owned-but-unbooked appointments the allocatable index never has.

**`maxBlockDuration` + the side-set (the tightness guarantee).** Pick a cutoff `D` (≈ 7–14 days, generous
vs the hours-to-a-day norm). Any block with `end − start > D`, or any open-ended appointment
(`maxEnd == null`), goes to the **side-set** and is *excluded* from `maxBlockDuration` — so
`maxBlockDuration ≤ D` by construction and the `subSet` lower bound stays tight regardless of a few long
bookings. Track it as a **high-water mark capped at `D`** (never recomputed on remove — correct because
the bound may be looser by ≤ `D` but never narrower than the true max).

**No recurrence rule is stored** — the index holds concrete block intervals + appointment references;
the authoritative `Repeating` lives in the resident `AppointmentImpl`, used only for the side-set's
`overlaps()`/`processBlocks` expansion. (Materializing into Java blocks is cheap — `createBlocks` — and
was never the slow part; the slow part was the H2 round-trip, now gone.)

### Three block kinds (the representation decision)

| Data | In the index |
|---|---|
| Single appointment (~98%) | 1 block in `byStart`, `start/end` = the block |
| Bounded repeating ≤ 52 | N **materialized** blocks in `byStart` |
| Open-ended / > 52 repeating | 1 entry in the **side-set** (`maxEnd`, ∞ → always a candidate) |

Rationale: singles are already blocks (materialization is scoped to the ~2% repeating set); bounded
repeatings are tiny so materializing costs a handful of blocks each and keeps them in the tight slice;
the open-ended tail forces a Java expansion path regardless → the side-set. Single-appointment-first,
repeating as a small annotated subset.

**The cap = 52 (locked).** Materialize iff total occurrences ≤ **52**; above it, or open-ended, keep a
rule row. 52 is chosen as **one year of a weekly repeating** — the dominant recurrence frequency — so
a normal weekly event materializes its whole visible year and only multi-year / open-ended series fall
to the rule-row + Java path. A count cap maps to different spans by frequency (52 weekly ≈ 1 yr, 52
daily ≈ 7 wk); a time-horizon cap was considered but the count cap is simpler and 52-weekly-as-a-year
is the natural unit. Revisit only if the measured occurrence distribution shows a different dominant
frequency.

## Why materialize the bounded ones — the index-exact slot filter

Both hot paths are the same in-memory slice: `byStart.subSet(winStart − maxBlockDuration, winEnd)`
filtered to overlap — for reads (what's booked in the window) and for conflict (existing blocks
overlapping a new block's slot on the same allocatable).

On a *materialized* block the slice is selective on the **real occurrence**: a reservation whose
blocks fall in other slots is below the `subSet` lower key and never touched — candidate cardinality ≈
*what is actually booked in the window*, independent of dataset size or how many long-running series
exist. A *rule entry*'s envelope `[start, maxEnd]` overlaps every future window, so envelope-only would
re-scan and Java-expand every long/open-ended series on every query (cost ∝ resident long-running
series — the same missing-lower-bound tax the current start-only `SortedSet` pays).

- **Tight two-sided slice.** Materialized block durations are bounded (≤ `D`), so a block overlaps
  `[winStart, winEnd]` only if its start is in `(winStart − maxBlockDuration, winEnd)` — exactly the
  `subSet` bounds. Only the few open-ended entries break this, and they live in the always-scanned
  side-set.
- **Slice-exact even on the collector.** A window query on the tens-of-thousands collector touches
  only that window's blocks (small k), not the whole collector — the lower bound the start-only
  `SortedSet` lacks. (A genuinely *wide* window over a collector is large by nature → that's the
  aggregation case, served by counting over the same index — index #4 in [PRD 082](082-storage-memory-model.md)'s catalog.)

## Dual-API: the same operator path serves both surfaces

The index is at the **operator** level (`queryAppointmentsSync`, conflict detection), below both APIs:

| Surface | Read entry | Conflict entry | Maps to |
|---|---|---|---|
| **Old RemoteStorage** (`/api/storage`, Swing/`RemoteOperator`) | `queryAppointments` | `getConflicts`, `getFirst/AllAllocatableBindings`, `getNextAllocatableDate` | `queryAppointmentsSync` / `getConflictsSync` |
| **GraphQL** (SPA) | `appointmentBlocks` / `appointmentBlockStats` | conflicts read API ([PRD 064](064-graphql-conflicts-read-api.md)) | same operator methods |

Because both surfaces funnel through the same operator methods, changing the index **necessarily
changes both** — which is why this PRD owns the old-API behavioral guarantee (the record/replay test
below), not just GraphQL tier-3 tests.

## Conflict detection

Conflict = "do two appointments on the same allocatable overlap in time?". Today the `ConflictFinder`
iterates an allocatable's full `SortedSet` with pairwise recurrence-aware overlap. With the index:

- **Candidate narrowing → the range query** (`start_ts`/`end_ts` slot lookup): per-write check is
  **O(log n + k)**, k = real slot-overlappers. **k does not grow with reservations ×5** — only n,
  carried logarithmically. Because ~98% are concrete single days, the surviving candidate set is
  almost always single-vs-single (trivial overlap); the recurrence-vs-recurrence path
  (`overlapsAppointment` / period-LCM math — the most bug-prone code) is reached only when a series
  hits the same narrow slot.
- For materialized blocks, conflict reduces to a `subSet` slice over discrete blocks. For open-ended
  side-set entries (and pairs involving them), the existing Java `processBlocks`/`overlapsAppointment`
  precise-overlap runs on the narrowed candidates. The boundary: **the slice narrows + serves the
  exact-block majority; Java computes precise overlap only on the open-ended tail** ([PRD 082](082-storage-memory-model.md) MQ8).

## Window-first global read (full-admin unscoped) — LOCKED 2026-06-24, shipped

The per-allocatable `IntervalIndex` accelerates **scoped** window reads (building-`idIn` 1y: 23 → 12 ms).
But it is queried **per key (allocatable)**; the **unscoped** path (`reservations()` else-branch) hands
all ~48k allocatables to `queryAppointmentsSync`, so the loop runs ~48k times. Measured (admin, unscoped):
~900–1040 ms with **flat window-scaling** (1d ≈ 1y) — the cost is the allocatable iteration, not the
window. `overlapping(key, win)` needs a key; there was no global "all appointments in the window" query.

**Routing v1 (LOCKED):**

| Caller | Scope | Plan |
|---|---|---|
| any | explicit (resource/group) | **resource-first** (per-allocatable index) |
| **Full admin** (`isAdmin()`) | none | **window-first** (global index) |
| Non-admin (incl. group-admin) | none | **resource-first** over the readable set |

- **Window-first hits only full admins.** Because `canRead` short-circuits to "everything" for a full
  admin, the global read is **filter-free** — no §12 post-filter, no leak risk, no filter cost. The gate
  is exactly the `canRead` short-circuit condition (`isAdmin()`), **not** group-admin (`canAdminUsers`) —
  a group-admin does not see everything → resource-first.
- **v2 (cost-based planner: `min(|scope|, |window-hits|)`)** stays **deferred** — only needed if
  broadly-visible non-admins run unscoped/large-group queries that measure slow.

**Global index — singleton key, NO change to `IntervalIndex`.** `overlapping(K key, …)` is key-generic ⇒
the global read is a second instance under one `GLOBAL` key (`IntervalIndex<Object,Appointment>`), so
`overlapping(GLOBAL, win)` is the global window query, **O(log N + k)**, no allocatable iteration.

**Maintenance — one derivation, one extra entry per appointment.** The global index is maintained in the
same `AppointmentMapClass.updateReservation` seam as the per-allocatable/owner indices. It holds each
appointment **bound to ≥1 allocatable exactly once** (a per-reservation `globalFiledByReservation` record
drives precise remove-old/add-new) — which **matches the legacy unscoped read** (it iterates allocatables
and so never returns an allocatable-less appointment). Footprint: +1 global entry per bound appointment.

**Side-set is uncritical** (dhbw data): ~5% of appointments repeat but are **expanded** (≤52) into
bounded-short skip-list entries (fast path), not the side-set. Side-set = only open-ended (<20% of the 5%)
≈ ~1% — a sub-ms scan per global query. Correctness: the ~99% expanded/single occurrences are **exact**;
only the ~1% open-ended are coarse candidates (forward-infinite), re-checked precisely by the same
`AppointmentImpl.getAppointments` window filter the resource-first path uses. Index narrows, never gates (D5).

**Controller branch** (`ReservationGraphQLController.reservations()`, unscoped else-branch): full-admin +
flip → `operator.reservationsInWindowGlobal(from, to)` (one global lookup, no `getAllocatables(null)` +
`canRead`×48k + per-allocatable loop); else resource-first over the readable set.

**Status (2026-06-24):** shipped behind `rapla.readmodel.authoritative`. Tier-1 `IntervalIndexTest`
singleton-key test green; tier-3 `ReservationWindowFirstFlipTest` proves admin-unscoped window-first ==
legacy resource-first (id-set identical) on the real operator; 305 rapla-server fast-lane green.
**Open verification:** can a non-admin trigger an unscoped query from the UI? If no (UI always scopes),
resource-first over the explicit scope is complete and the permission-index path stays optional for it.

## Binding semantics (Phase-0 finding) — the index ≠ `getAllocatablesFor`

A non-obvious domain fact surfaced building the Phase-0 oracle: the `appointmentMap` binding
(`AppointmentMapClass.updateReservation`, which `queryAppointmentsSync` exposes) is **not** reproduced
by `Reservation.getAllocatablesFor(appointment)`. The map binds via `getIds("resources")` **plus the
template-alloc annotation** (`KEY_TEMPLATE` → an allocatable id, bound to *all* the reservation's
appointments) and uses `getRestrictionForAllocatableRef`; `getAllocatablesFor` uses
`getRestrictionPrivate` and omits the template alloc. They legitimately disagree on template/restriction
edge cases. **Consequence for 086:** the new range index must preserve the *appointmentMap* binding
relation, not the `getAllocatablesFor` one — so the Phase-0 differential oracle is built as a
**windowing-invariance** check (all-time index bindings, filtered by plain-Java `overlaps()`), which
isolates the range logic the index actually changes and leaves the (settled, quirky) binding relation
to record/replay + the shadow map.

## Two implementation findings (Phase 3 build, 2026-06-24)

- **`block_id` is namespaced by allocatable, not just occurrence.** One appointment binds *multiple*
  allocatables → one block row per (occurrence × allocatable). The PK is therefore
  `appointmentId@allocatableId#occurrenceIdx` (rule row: `…@allocatableId#rule`), not the
  `appointmentId#idx` the schema sketch implied — otherwise the PK collides across allocatables.
- **The read path expands belongs-to/dependent references; the projection stays raw.** `getAppointments(Allocatable)`
  (what `queryAppointments` consumes) expands the raw `appointmentMap` binding through belongs-to /
  allocatable-attribute references (`cache.getDependentRef`): an appointment booked on resource X also
  surfaces under resources that *depend on* X (packages, belongs-to parents, allocatable-typed
  attributes pointing at X). The block projection stores the **raw** `updateReservation` binding (one
  row per directly-allocated allocatable); the **read/shadow path must expand the query scope via
  `getDependent(...)`** before hitting the block table, exactly as `getAppointments` does. Keeping the
  expansion at read-time (not baked into the stored rows) mirrors the legacy structure and avoids
  fan-out in the table. This is why the Phase-0 oracle and the Phase-3 equivalence test both expand
  scope through `getDependent` before comparing.

## Open-ended rule-row handling

A rule row (`is_rule=true`) stores **no rule data** — the flag is purely a pointer that says "the index
can't answer this alone; defer to the live object." No new machinery is needed; it reuses what already
exists:

1. **Resolve `appointment_id` → the resident `AppointmentImpl`.** The block table deliberately omits
   `repeating_kind`/`interval`/`exceptions`; the in-`LocalCache` `AppointmentImpl` already holds the
   `Repeating` (type, interval, weekdays, number/end-date, **exceptions**). The rule row just looks it
   up — that is why we dropped the rule columns (§ row kinds).
2. **Window-expand only the rule-row candidates, only within the query window.** Reuse the existing
   `AppointmentImpl.processBlocks(winStart, winEnd, collector)` / `getAppointments` — *not* a
   reimplementation. Singles + materialized blocks are **index-exact** (answered directly); rule rows
   are **candidates** that get expanded per-occurrence inside the window and tested with `overlaps()`.
3. **An open-ended row (`end_ts = ∞`) is a candidate for *every* forward window.** The lower-bound
   filter `end_ts > winStart` is always true for ∞, so every open-ended series returns as a candidate
   for any future query and must be Java-checked. This is correct (an open-ended weekly series genuinely
   could be active in any future week) but means the per-query Java-correction cost scales with the
   **count of open-ended series**, not total appointments — the metric to watch (the open-ended fraction
   varies materially between stores). A bounded > 52 row caps at `maxEnd`, so it stops being a candidate
   past its last occurrence; only the truly open-ended (∞) rows are perpetual candidates.
4. **Exceptions handled for free.** Because the Java path reads the live `Repeating`,
   `getExceptions()` is honoured — an excepted occurrence is simply not emitted. The index alone could
   never do this for an open-ended series; the rule row sidesteps it by always deferring to Java.
   (OQ3's exception question applies only to *materialized* blocks, never to rule rows.)

Maintenance: on a put, re-evaluate the kind — if a series crosses the 52 boundary or flips
open-ended↔bounded, remove the appointment's old blocks and re-insert (materialize ≤ 52 into `byStart`,
or one entry in the side-set). The side-set's Java-overlap path is the part most likely to drift, so it
is exactly what the shadow `appointmentMap` validates during Stage X/Y.

## Aggregation

`appointmentBlockStats` counts are exact in-memory `count`/`group` over the materialized blocks (the
non-side-set entries — ~98%+); a small Java correction expands only the open-ended side-set tail.

## Maintenance (drift-safe, at the [PRD 082](082-storage-memory-model.md) seam)

At the put/remove/refresh seam (the same `updateReservation` chokepoint that maintains the existing
cache structures, for local *and* cross-pod changes), a changed appointment's blocks are regenerated
idempotently: **remove the appointment's old blocks from both `IntervalIndex` instances (allocatable +
owner), then re-insert** — materialize ≤ cap occurrences into `byStart`, or place one entry in the
side-set. Pure projection: rebuilt at boot from the loaded objects; drop-and-rebuildable. The invariant
`index == project(objects)` ([PRD 082](082-storage-memory-model.md)) covers it. (Removal locates the appointment's blocks by
regenerating them via `createBlocks` or by an appointment→blocks back-reference — an in-memory detail,
no `DELETE WHERE` / SQL maintenance-key concern.)

## The H2 detour (built, measured, lost, **deleted** — 2026-06-24)

> **Deleted from the codebase 2026-06-24.** The in-memory `IntervalIndex` (two instances, allocatable +
> owner) is now wired into `AppointmentMapClass.updateReservation` and the `queryAppointments`/conflict
> read paths flip behind `rapla.readmodel.authoritative`; the H2 `ReadModel`/`Projection`/projections/
> `blockDerived*`/`shadowCompare*` and the H2 dependency are gone. Equivalence is proven by
> `ReadModelReadFlipEquivalenceTest` (window + owner + conflict, reservations stored post-boot) and
> `ReadModelFlipDifferentialTest`; 304 rapla-server fast-lane tests green. See [PRD 082](082-storage-memory-model.md) *Components built → wired*.
> The H2 narrative below is kept for the rationale record.

This index was first built in **H2 (in-process SQL)** exactly per the Migration/Shadow/Test plan below,
flipped behind the `rapla.readmodel.authoritative` flag. It **lost**: 1.1×–3.7× slower than the legacy
in-memory `appointmentMap` across the whole scope×window matrix on the real store, ~91% of the path
being the JDBC boundary (`executeQuery` + row rehydration); batching made it worse. **Why it can't win:**
the `appointmentMap` is an *already-resident in-memory* structure — crossing a SQL boundary on every
read is strictly more work than a better in-memory data structure. So the design pivoted to the
in-memory `IntervalIndex` above. **What carries over verbatim:** the maintenance seam, the
differential/shadow methodology, the brute-force oracle + record/replay tests, and the
binding/dependent-expansion correctness — all reusable for the in-memory version. Full numbers + the
shadow/flip findings: **[PRD 082](082-storage-memory-model.md) Phases 3, 4a, 4b-ii**. The sections below are that plan; read
"H2 block table" → "in-memory `IntervalIndex`" and "SQL range query" → "`subSet` slice", everything
else (Stage X/Y, shadow-compare, the test layers) applies unchanged to the in-memory index.

## Migration — Stage X → Stage Y

- **Stage X (reads):** project `appointment_block` **beside** the untouched `appointmentMap`. Redirect
  `queryAppointmentsSync` to the H2 range query (+ Java expand for `is_rule`). Conflict core
  untouched. Read-perf win lands at low risk. Slim block data duplicated (cheap: ids + time).
- **Stage Y (conflict):** fold conflict detection onto `appointment_block`. The retained
  `appointmentMap` becomes the **differential shadow-oracle** — old vs engine-backed conflict sets
  byte-identical over a large corpus, then `appointmentMap` removed. Touchable because covered by
  enough tests.

**Write-path benchmark (the X→Y gate).** Stage Y puts the engine in the write-critical path (sync
insert + indexed conflict check). Correctness is the differential oracle; the open question is
write latency. Hypothesis: a sync in-process engine-backed index is ≤ ~1.2× p99 vs today's TreeSet
at realistic density (and better at high density — indexed candidate query beats the manual sweep).
The decisive axis is per-allocatable density (the workload shape places it in the "index wins"
regime: ~98% single, no high-fan-out recurrence collector — the collector's load is single dated
appointments, which a range index serves cleanly). JMH per-op + a sustained run for GC/alloc/
footprint. Decision: Y viable iff B write p99 ≤ ~1.2× A at realistic density AND conflict-check ≤ A
at high density AND alloc/GC ≤ A; otherwise hold at Stage X.

## Shadow mode (runtime differential during the migration)

Throughout Stages X and Y the `appointmentMap` is **kept maintained in parallel**, and **both reads
and conflict computations run both paths and compare** — so we see in the field whether the index
agrees before trusting it. Removed only after everything is proven.

- **Three flag states.** `shadow` (appointmentMap **authoritative**, index computed + compared) →
  `flipped` (index authoritative, appointmentMap compared) → `off` (index only — after removal). So
  decommissioning is first a config flip, then a code deletion — no big bang.
- **The comparison never blocks.** In `shadow` the appointmentMap serves the real result (query
  answer, booking decision); an index bug must not break production. Divergence is logged, not
  thrown.
- **Reproducible ERROR log.** Not "drift detected" but: allocatable-id, window `[start,end]`, and the
  **difference set** of appointment-/conflict-ids (what the index has extra/missing) — enough to
  reproduce.
- **Rate-limit / dedupe** by divergence signature (allocatable + window-class), so a systematic drift
  logs once + a counter, not once per call.
- **Set-normalized compare** for both surfaces — order-independent: the appointment-id set for reads,
  the sorted appointment-pair set for conflicts.
- **Cost is accepted and temporary.** Double compute + both structures resident (so no footprint win
  yet) only during the shadow period; the footprint payoff lands at `off`. If the double latency
  measurably hurts, compute the index path async and defer only the comparison — the authoritative
  old path stays synchronous.

## Test strategy

Two complementary layers, both **in the plugin-deployment repo** (where the real local store lives), in-process via
the facade/operator — **no GraphQL, no HTTP, no server lifecycle** (the old RemoteStorage protocol
delegates to the same operator methods, so testing the operator in-process tests that surface too):

### Layer 1 — brute-force differential oracle (correctness, single run)

An **oracle** = an independent source of truth. For "which appointments overlap allocatable X in
window W", the oracle is a dumb full scan over *all* reservations asking each appointment
`overlaps(W)` — no index, no shortcut, so it cannot share the index's bug class. Assert
`operator.queryAppointmentsSync(X, W) == bruteForce(X, W)` over many allocatables × windows (day /
week / year, the collector, empty future windows). Implementation-independent (public domain API
only) → validates today's `SortedSet` *and* tomorrow's H2 index against the same truth, same test;
green today proves the harness, catches any divergence after the index lands. Brute-force lives
**only in test code**, never in the backend.

For **conflicts**, prefer **controlled-mutation invariants** over brute-force-vs-`ConflictFinder`
equality (the finder has business rules — templates, disabled conflicts, exceptions — a naive
pairwise scan doesn't model): create a known overlapping appointment → assert detected → move it
apart → assert gone.

### Layer 2 — record/replay (behavioral regression across the migration)

Freeze one baseline store snapshot. Run a mutation battery via the facade (which builds the
`dispatch`/`UpdateEvent`s itself — no hand-rolled payloads), recording every operator-API query
result (block sets, conflict sets, bindings, next-date) as goldens; export the post-battery store
(engine-neutral XML). After the index migration: restore the same baseline, re-run, **diff** the
query goldens *and* the data export → must be byte-identical. This is the broad net (catches
conflict-rule/ordering/persisted-data changes the brute-force oracle doesn't model).

### The mutation battery (both layers exercise the put/remove maintenance seam)

create event with conflict · move appointment (single) · move repeating appointment · change an
appointment's allocatable assignment · toggle single↔repeating · delete event · delete allocatable ·
window-query equivalence on sampled real allocatables incl. the collector · single/bounded/open-ended
repeating through several windows.

Backend of the harness: `FileOperator` on a copy of the local `data.xml` (simpler; the index lives
in the shared `LocalAbstractCachableOperator`, so it exercises the same code the DBOperator path
runs). `Assumptions.assumeTrue(dataExists)` so it skips cleanly where the local store is absent —
safe to commit. (A `DBOperator`-on-hsqldb-copy variant — the exact server backend — is an optional
`@Tag("db")` follow-up.)

## Plan

- **Phase 0** — build the Layer-1 brute-force harness in the plugin-deployment repo; green against today's operator
  (proves the harness). Capture the Layer-2 baseline snapshot + golden recorder.
- **Phase 0.5** — H2 validation experiment (throwaway JMH: range query + sync write-through vs
  TreeSet at real density) → confirms the engine bet before the foundation build ([PRD 082](082-storage-memory-model.md)).
- **Phase 1 (Stage X)** — project `appointment_block` at the [PRD 082](082-storage-memory-model.md) seam; redirect
  `queryAppointmentsSync` to H2 (+ `is_rule` Java expand). Conflict core untouched. Gate: brute-force
  oracle green + record/replay identical + live latency win.
- **Phase 2 (Stage Y)** — conflict detection onto the engine; `appointmentMap` as shadow oracle;
  write-path benchmark gate; then remove `appointmentMap`.
- **Phase 3** — fold `appointmentBlockStats` aggregation into SQL (exact for `is_rule=false`, Java
  correction for the tail).

## Tests

- Layer 1 brute-force oracle (Phase 0, the plugin-deployment repo) — query equivalence on real data.
- Layer 2 record/replay (Phase 0/1) — behavioral + persisted-data parity across the migration.
- Stage-Y differential oracle — engine conflict sets == `appointmentMap` conflict sets over a
  generated corpus.
- Existing GraphQL tier-3 (PRD [064](064-graphql-conflicts-read-api.md)/[079](079-graphql-grouped-aggregates.md)/[080](done/080-typed-entity-stats.md)) + §12 leak tests stay green (the GraphQL surface).
- Write-path JMH benchmark (Phase 2 gate).

## Open Questions

- **OQ1 — resolved.** Cap = **52** (count), = one year of a weekly repeating (the dominant frequency).
  Revisit only if the measured occurrence distribution shows a different dominant frequency.
- **OQ2** — `maxBlockDuration` for the tight two-sided lower bound: a fixed conservative constant vs
  the real max block length per store (a degenerate multi-day block would widen it).
- **OQ3** — Materialized-block representation for exceptions: omit excepted occurrences vs emit with
  an exception flag (affects whether the index alone can answer "is this slot free incl. exceptions").
- **OQ4 — resolved.** Test-harness backend = **`FileOperator` on a `data.xml` copy** only (simpler
  snapshot/restore; same `LocalAbstractCachableOperator` code the DBOperator path uses). A
  `DBOperator`(hsqldb) `@Tag("db")` variant remains optional if a backend-specific divergence ever
  appears.
- **OQ5** — Stage X as a standalone release point (ship, observe in the field) before committing to
  Stage Y, vs straight through once the benchmark passes.
