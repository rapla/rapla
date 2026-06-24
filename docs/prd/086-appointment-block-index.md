# PRD 086 — appointment block index (flat in-memory block table, dual-API)

**Status:** draft — 2026-06-24
**Related:** PRD 082 (storage memory model — provides the H2 read-model + the drift-safe put/remove projection seam this builds on; the appointment-index design was split out of 082 into here), PRD 064 (GraphQL conflicts read API), PRD 079/080 (grouped aggregates / typed-entity stats — `appointmentBlockStats`), PRD 055 (GraphQL events read), `docs/architecture/locking.md`

**Split from PRD 082.** The appointment index is the largest and riskiest piece of the storage
read-path modernization, and — unlike the classification (PRD 087), permission (PRD 083), and search
(PRD 085) indices, which are GraphQL-only — it is **dual-API**: it sits *below* both the old
RemoteStorage protocol and the new GraphQL API (both call the same operator query/conflict path). It
therefore carries a heavier test obligation (the old-API record/replay + brute-force oracle below)
and gets its own PRD. The shared read-model foundation (H2 engine, projection seam, drift-safety,
boot rebuild) lives in **PRD 082** and is referenced here, not duplicated.

## Problem

rapla's hot path is **"appointment blocks for these allocatables in this time window"** — the
calendar render, the conflict check, `appointmentBlocks` / `appointmentBlockStats`. Today it is
served from `AppointmentMapClass` (`LocalAbstractCachableOperator`): per-allocatable
`SortedSet<Appointment>` ordered by **start only**.

The measured pathology (PRD 082): latency is a **fixed floor independent of the window** — a 1-day,
3-block query already pays ~670 ms; growing the window 365× (to a year, 100× the blocks) only moves
it to ~882 ms (1.3×). Cause: `headSet(start < winEnd)` applies only the **upper** time bound; the
**lower** bound (`maxEnd > winStart`) is checked per-appointment via `overlaps`. For a window late
in the dataset the headSet is ≈ *all appointments ever started*, so cost ∝ (allocatables ×
appointments-before-winEnd), not ∝ result. At reservations ×5 this scan tax grows with total data.

## Workload shape (measured — see PRD 082 for the full numbers)

Orders of magnitude across two production-scale stores (each ~10⁵ reservations, a few × 10⁵
appointments):

- **~98% of appointments are single** (non-repeating) — `start`/`end` *is* the block.
- **~2% repeating**, an absolute, roughly-constant few thousand that **does not grow** with the
  dataset; of these a large, store-dependent fraction (~¼ to ~½) is **open-ended** (no count, no
  end-date); where bounded, occurrence counts are tiny (median a handful, p95 ~6–12, ~none over 50).
- **Per-allocatable density is heavily skewed:** median single-digit, p95 ~10³, a worst-case
  **collector allocatable carries tens of thousands** of appointments.

This makes the index design single-appointment-first, with repeating as a small annotated subset.

## The index — a flat block table

In the in-memory engine (H2, per PRD 082). One row = one *block* (a concrete dated occurrence on one
allocatable), so the single-appointment majority maps 1:1 to a directly query/aggregate-able row.

```sql
appointment_block(
  block_id        VARCHAR,   -- PK; materialized: appointment_id#occurrenceIdx, rule row: appointment_id#rule
  appointment_id  VARCHAR,
  reservation_id  VARCHAR,
  allocatable_id  VARCHAR,   -- one row per (block × allocated allocatable)
  owner_id        VARCHAR,
  start_ts        BIGINT,    -- epoch millis
  end_ts          BIGINT,    -- exact block end; rule row: maxEnd (∞ → Long.MAX_VALUE)
  is_rule         BOOLEAN    -- false ⇒ exact block (single OR materialized occurrence);
                             -- true  ⇒ expand in Java via the RESIDENT AppointmentImpl
)
CREATE INDEX ix_block_alloc_time ON appointment_block(allocatable_id, start_ts);  -- the hot index
CREATE INDEX ix_block_owner_time ON appointment_block(owner_id, start_ts);        -- replaces appointmentUserMap
CREATE INDEX ix_block_reservation ON appointment_block(reservation_id);           -- maintenance + hydration grouping
```

**No recurrence rule is stored — only `is_rule`.** Under the all-in-memory scope (PRD 082) the
engine is an **index, not a data store**: the authoritative repeating rule (interval, end/count,
exceptions) lives in the resident `AppointmentImpl`. A rule row needs only `start_ts`/`end_ts` (the
envelope, so the range filter selects it) + `is_rule = true`; the Java step calls
`processBlocks(winStart, winEnd)` on the **real resident object**. The 1-bit flag lets the ~98%
exact-block fast path emit the row without dereferencing the object. (Storing the rule columns would
only be needed in the deferred DB-offload future, where expansion runs without a resident object.)

### Three row kinds (the representation decision)

| Data | Rows | `is_rule` |
|---|---|---|
| Single appointment (~98%) | 1 block row, `start/end` = the block | false |
| Bounded repeating ≤ cap | N **materialized** block rows | false |
| Open-ended / over-cap repeating | 1 **rule row**, `end_ts = maxEnd/∞` | true |

Rationale: singles are already blocks (materialization is scoped to the ~2% repeating set); bounded
repeatings are tiny so materializing them costs a handful of rows each; the open-ended fraction
forces a Java expansion path to exist regardless, so the choice is "materialize everything bounded,
keep the rule path for the irreducible open-ended tail."

**The cap.** Materialize iff total occurrences ≤ cap (start value **~50**); above it, or open-ended,
keep a rule row. A count cap maps to different spans by frequency (~50 weekly ≈ 1 yr, ~50 daily ≈ 2
mo), so a **time-horizon cap** (materialize within a resident horizon, rule-expand the tail) is the
likely refinement — final form set from the measured occurrence distribution (OQ1).

## Why materialize the bounded ones — the index-exact slot filter

Both hot paths are the same range query:

```sql
-- "what is booked" / week render:
WHERE allocatable_id IN (:scope) AND start_ts < :winEnd AND end_ts > :winStart
-- conflict for one new block:
WHERE allocatable_id IN (:newAllocs) AND start_ts < :blockEnd AND end_ts > :blockStart AND reservation_id <> :self
```

On a *materialized* block the range index is selective on the **real occurrence**: a reservation
whose blocks fall in other slots is excluded by the b-tree and never touched — result cardinality ≈
*what is actually booked in the window*, independent of dataset size or how many long-running series
exist. A *rule row*'s envelope `[start, maxEnd]` overlaps every window, so rule-only would
re-select and Java-expand every long/open-ended series on every query (cost ∝ resident long-running
series — the same missing-lower-bound tax the current `SortedSet` pays).

- **Tight two-sided start range.** Materialized block durations are bounded (a session), so a block
  overlaps `[winStart, winEnd]` only if `start_ts > winStart − maxBlockDuration` → a tight
  `start_ts BETWEEN winStart−maxDur AND winEnd`. Only the few rule rows break this.
- **Index-exact even on the collector.** A window query on the tens-of-thousands collector returns
  only that window's blocks (small k), not the whole collector — the lower bound the `SortedSet`
  lacks. (Caveat: a *wide* window over the collector is genuinely large → push aggregation into SQL.)

## Dual-API: the same operator path serves both surfaces

The index is at the **operator** level (`queryAppointmentsSync`, conflict detection), below both APIs:

| Surface | Read entry | Conflict entry | Maps to |
|---|---|---|---|
| **Old RemoteStorage** (`/api/storage`, Swing/`RemoteOperator`) | `queryAppointments` | `getConflicts`, `getFirst/AllAllocatableBindings`, `getNextAllocatableDate` | `queryAppointmentsSync` / `getConflictsSync` |
| **GraphQL** (SPA) | `appointmentBlocks` / `appointmentBlockStats` | conflicts read API (PRD 064) | same operator methods |

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
- For materialized blocks, conflict reduces to a SQL range lookup over discrete blocks. For
  `is_rule` rows (and pairs involving them), the existing Java `processBlocks` precise-overlap runs
  on the narrowed candidates. The boundary: **engine narrows + serves the exact-block majority; Java
  computes precise overlap only on the rule tail** (PRD 082 MQ8).

## Aggregation

`appointmentBlockStats` block counts are exact `COUNT`/`GROUP BY` for every `is_rule=false` row
(singles + materialized) — ~98%+ of blocks; a small Java correction expands only the rule tail.

## Maintenance (drift-safe, per the PRD 082 foundation)

At the put/remove/refresh seam (the same chokepoint that maintains the existing cache structures, for
local *and* cross-pod changes), a changed appointment's rows are regenerated idempotently
(`DELETE WHERE appointment_id=?` + re-`project`, re-materializing ≤ cap occurrences or writing one
rule row). Pure projection: rebuilt at boot from the loaded objects; drop-and-rebuildable. The
property invariant `index == project(objects)` (PRD 082) covers it.

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

Two complementary layers, both **in dhbwrapla** (where the real local store lives), in-process via
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

- **Phase 0** — build the Layer-1 brute-force harness in dhbwrapla; green against today's operator
  (proves the harness). Capture the Layer-2 baseline snapshot + golden recorder.
- **Phase 0.5** — H2 validation experiment (throwaway JMH: range query + sync write-through vs
  TreeSet at real density) → confirms the engine bet before the foundation build (PRD 082).
- **Phase 1 (Stage X)** — project `appointment_block` at the PRD 082 seam; redirect
  `queryAppointmentsSync` to H2 (+ `is_rule` Java expand). Conflict core untouched. Gate: brute-force
  oracle green + record/replay identical + live latency win.
- **Phase 2 (Stage Y)** — conflict detection onto the engine; `appointmentMap` as shadow oracle;
  write-path benchmark gate; then remove `appointmentMap`.
- **Phase 3** — fold `appointmentBlockStats` aggregation into SQL (exact for `is_rule=false`, Java
  correction for the tail).

## Tests

- Layer 1 brute-force oracle (Phase 0, dhbwrapla) — query equivalence on real data.
- Layer 2 record/replay (Phase 0/1) — behavioral + persisted-data parity across the migration.
- Stage-Y differential oracle — engine conflict sets == `appointmentMap` conflict sets over a
  generated corpus.
- Existing GraphQL tier-3 (PRD 064/079/080) + §12 leak tests stay green (the GraphQL surface).
- Write-path JMH benchmark (Phase 2 gate).

## Open Questions

- **OQ1** — Cap form & value: count (~50) vs time-horizon; final value from the measured occurrence
  distribution. Time-horizon likely (decouples from frequency).
- **OQ2** — `maxBlockDuration` for the tight two-sided lower bound: a fixed conservative constant vs
  the real max block length per store (a degenerate multi-day block would widen it).
- **OQ3** — Materialized-block representation for exceptions: omit excepted occurrences vs emit with
  an exception flag (affects whether the index alone can answer "is this slot free incl. exceptions").
- **OQ4** — Backend for the test harness: `FileOperator`(data.xml copy) only, or also a
  `DBOperator`(hsqldb copy) `@Tag("db")` variant matching the live server exactly.
- **OQ5** — Stage X as a standalone release point (ship, observe in the field) before committing to
  Stage Y, vs straight through once the benchmark passes.
