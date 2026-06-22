# PRD 082 — Storage memory model: read-path indices, footprint & modernization

**Status:** draft — 2026-06-22 (broadened from the original "GraphQL read-path indices" scope)
**Related:** PRD 035 (GraphQL foundations — per-field perf hot-spots), PRD 066 (allocatable scope union on ReservationFilter), PRD 079/080 (grouped aggregates / typed-entity stats — the `appointmentBlockStats` fan-out), PRD 081 (omnibox multisearch — the reservation name full-scan this indexes), PRD 067 (server operator split)

**Scope note (changed 2026-06-22):** this PRD originally covered *only* in-memory read-path
indices in `LocalCache` and explicitly deferred "persistence-/storage-scheme level" indices to
a separate stream. That separation is dropped: the PRD now covers the **whole storage memory
model** — the in-memory read-path indices remain as **Workstream A** (concrete, partly shipped),
and the broader **re-evaluation of whether rapla's "load everything into RAM" model is still
contemporary** is captured as the **Bestandsaufnahme + Modernization evaluation** below.

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
(~48k today) are bounded and near-static. The memory hog is the history — and Role A is what
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

### Measured bottleneck (admin token, local dhbw backend, 2026-06-22)
47,893 allocatables (type `Raum` = 1,872). Latency is driven by **allocatable-set size**, not by
the time window or result cardinality:

| Scope | `appointmentBlockStats` (group by Raum, 1 yr) | Factor |
|---|---:|---:|
| no scope (all 47,893) | 901 ms | 1× |
| `typeKeyEq:"Raum"` (1,872) | 201 ms | 4.5× |
| one building (`idIn`) | **23 ms** | **39×** |

Window scaling is flat (a 1-day, 3-block window already pays ~670 ms). Catalog
`allocatables(filter:)` ids-only: untyped `{}` = 155 ms / 47,893 rows (dominated by
`canRead × 47,893`); `typeKeyEq:"Raum"` = 22 ms / 1,872 rows (storage-prefiltered, but still
`new HashSet<>(47,893)` + a 47,893-element scan inside `getAllocatables`).

---

## Modernization evaluation — is the storage model still zeitkonform?

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
   This is the real "not contemporary" smell: holding 48k+ objects resident to scan them linearly
   while an indexed database sits idle beside it.

### Target scale (decided 2026-06-22)
- **Allocatables × 2** (~96k) — modest growth, near-static dimension.
- **Reservations × 5** — the **unbounded, RAM-pinning** dimension grows the most.

This stays **below** the Kafka/grid/sharding threshold — so lever (2) (sharding/distributed grid)
is **out**. The asymmetry is the decisive planning input: the dimension that grows fastest (×5) is
exactly the one Role A pins in RAM (appointments for conflict detection) and the one that grows
without bound over time. **Reservation footprint, not allocatable scan, is the primary pressure at
the target.** → **Option 3 (window the resident reservation set) is the primary lever; the
read-path/catalog work (Workstream A / option 2) is real but secondary** because the allocatable
dimension only doubles.

### Today's footprint bound: the archiver (and why it is the wrong tool kept for the right reason)

rapla already bounds reservation growth — **by deleting**. The `archiver` plugin
(`ArchiverServiceTask`, hourly `@Scheduled`; `ArchiverServiceImpl`) reads `removeOlderThan` days
from system preferences (default `-20` = disabled; the dhbw deployment sets it to ~365 → "events
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

### Options (smallest → largest)

| # | Option | Faster | Smaller | More standard | Effort | At target (alloc ×2 / resv ×5) |
|---|---|:--:|:--:|:--:|---|---|
| **1** | In-memory indices only (Workstream A: type-bucket + name; + window-first appointment index) | ✅ | ❌ (adds a little) | ➖ | low | ship — perf floor; allocatable ×2 keeps the scan cost bounded |
| **2** | **DB-indexed read path for Role B** (Spring `JdbcTemplate`/Data + SQL indices) | ✅ | ✅ | ✅ | medium | all-axes win for reads; secondary (alloc only ×2) |
| **3** | **Window the resident conflict/reservation set** (evict old reservations; lazy historical reads) | ➖ | ✅✅ | ✅ | medium | **primary lever — resv ×5 is the footprint** |
| **4** | Transport poll → push (`LISTEN/NOTIFY`) | ➖ (consistency, not throughput) | ❌ | ✅ | low–med | nice, orthogonal |
| **5** | Per-entity footprint (string interning, shared classification structures) | ❌ | ➖ | ➖ | low | micro, orthogonal |
| — | Distributed grid / Kafka / sharding | — | — | — | high | **rejected at this scale** |

### Recommendation
1. **Phase 0 — measure first.** Heap composition on the dhbw store (object histogram), reservation
   + appointment counts, pod count, concurrent-user load. The asymmetric target (resv ×5) already
   points at option 3, but confirm reservations/appointments actually dominate the heap before
   committing the windowing work.
2. Then, ordered by the target asymmetry: **(3) windowed resident reservation set first** (the ×5
   footprint), then **(2) DB-indexed read path** (the all-axes read win), with **(4) push transport**
   as the cheap consistency upgrade. Standard Spring Boot idioms, no exotic infra.
3. **Workstream A (option 1)** ships independently as the in-memory perf floor and is largely
   self-contained (and partly shipped, see D4). With allocatables only doubling, it keeps the
   catalog/search scan bounded and is the cheapest immediate latency win — but it is *not* the
   footprint lever.

### Open questions (modernization)
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

---

# Workstream A — In-memory read-path indices (original PRD 082 scope)

## Abstract

Broad GraphQL read queries over the dhbw store (47,893 allocatables; every reservation
scanned for name search) pay a fixed **O(all)** tax that is invisible at small scale but
dominates real latency. This workstream adds two `LocalCache` indices: (a) a **`Map<String, Set<String>>`
type-bucket index** (DynamicType id → allocatable ids) for type-scoped allocatable queries, and
(b) a **reservation-name search index** that replaces the windowless full scan in the omnibox
EVENT bucket. Both are maintained on entity put/remove and consumed by the storage-filter /
search paths that today fall back to a full scan. Measurable end state: a `typeKeyEq`/`typeKeyIn`
allocatable query resolves from the bucket(s), and an omnibox event-name search resolves from the
name index — neither copies+scans the whole population.

## Background — measured bottlenecks (2026-06-22, admin token, local dhbw backend)

Dataset: **47,893 allocatables** (type `Raum` = 1,872). Probed via `/api/graphql`, median of 3.

**The decisive finding — latency is driven by allocatable-set size, NOT by the time window or result cardinality.** `appointmentBlockStats` (group by Raum, 1 year) by allocatable scope:

| Scope | Latency | Factor |
|---|---:|---:|
| no scope (all 47,893) | 901 ms | 1× |
| `typeKeyEq:"Raum"` (1,872) | 201 ms | 4.5× |
| one building (`idIn`) | **23 ms** | **39×** |

`reservations` (1 year) — same shape: no scope 876 ms → one building **20 ms** (44×).

**Window scaling is flat** (fixed floor, not window-driven): `appointmentBlockStats` group-by-Raum = 673 ms (1 day, 3 rows) / 676 ms (1 week) / 688 ms (1 month) / 882 ms (1 year, 319 rows). `appointmentBlocks` = 686/664/659/685 ms for 1d/1w/1m/3m. A 1-day window with 3 blocks already pays ~670 ms.

**Catalog query** `allocatables(filter:)` ids-only: `{}` untyped = **155 ms / 47,893 rows**; `typeKeyEq:"Raum"` = **22 ms / 1,872 rows**. The untyped case is dominated by `canRead × 47,893` in the controller loop; the typed case is storage-prefiltered to 1,872 but still pays `new HashSet<>(47,893)` + a 47,893-element scan inside `getAllocatables`.

### Why (code path)

- `AbstractCachableOperator.getAllocatables(filters)` (line ~442) always does `new HashSet<>(cache.getAllocatables())` (copy all 47,893) then iterates to filter — regardless of how narrow the filter is.
- `ClassificationGraphQLController.buildStorageFilter` (line ~231) pushes **only `typeKeyEq`** to storage. `typeKeyIn` (multi-type) and `where`-only (B′) return `null` → the controller loops `matches`/`evaluateWhere`/`canRead` over all 47,893.
- `ReservationGraphQLController.reservations()` previously called `getAllocatables(null)` unconditionally even for scoped queries (**fixed this session** — scoped queries now resolve their set directly via the catalog resolver; see Decisions D4).

## Where a type-bucket index helps (and where it does NOT)

| Query class | Today | Index? | Note |
|---|---|:--:|---|
| **1. "all of type X" catalog** (`allocatables(typeKeyEq:"Raum")`, "alle Räume"/"alle Kurse") | 48k copy → filter to bucket (22 ms) | **YES** | the `Set<String>` **is** the answer set — direct bucket lookup, skip the 48k copy |
| **2. multi-type union** (`typeKeyIn:[Raum,Gebäude,…]`) | `buildStorageFilter`→null → 48k + `canRead`×48k (155 ms-class) | **YES, biggest** | bucket union; `canRead` only over the union |
| **3. `where<Type>`-only / B′** (no explicit typeKeyIn) | →null → 48k + `WhereEvaluator`×48k | **YES, potential** | B′ already knows the implied type → narrow to its bucket first |
| **4. type-scoped analytics** (`blockStats` over all rooms, no building narrowing) | 167–201 ms | **PARTIAL** | index narrows allocatable resolution; residual `queryAppointmentsSync` over the bucket (e.g. 1,872 rooms) scales with bucket size (~tens of ms) — not removed by the index |
| **5. id-scoped** (`idIn`, building) | `tryResolve`, 20 ms | **NO** | already direct; the SPA's analytics scope path |
| **6. untyped "all" / window-only** (`allocatables({})`, unscoped reservations) | 48k + `canRead`×48k; `queryAppointmentsSync`×48k | **NO** | you want all types (= union of all buckets = the full map); cost is `canRead`×48k (admin-short-circuit) and `queryAppointmentsSync` (window-first index) — different levers |

**Strategic read:** "alle Räume"/"alle Kurse" (whole-type) is a common, legitimate pattern — both as a catalog list (class 1) and as a grouping basis. So the index is **not** niche; it directly serves the type-as-group queries. Its lever is `getAllocatables`/`buildStorageFilter`; it is **orthogonal** to the `queryAppointmentsSync`-over-N cost (class 4/6).

## Name search index (reservations + allocatables)

Distinct from the type-bucket index, a second read pattern is **name search**, which today scans the full population:

- **Omnibox EVENT bucket** (`SearchGraphQLController`, lines 154-190): a **windowless full scan over every reservation** (`CachableStorageOperator.getReservations()`) — `SearchMatcher.rank(r.getName(locale), needle, SUBSTRING)` + `canModify(r, caller)` per reservation. The controller's own Javadoc flags it: *"Phase 1 is the naive full scan; a name index is a measured follow-up."* This is exactly the reservation-name index needed.
- **Omnibox RESOURCE bucket** (lines 121-145): name search over allocatables via `classificationController.allocatables({searchText, matchKind:FUZZY})` then `SearchMatcher.rank` per candidate — same shape, allocatable side. A name index helps here too (pairs with the type-bucket index — search within a type's bucket).
- `ReservationFilter.searchText` on `reservations(...)` ranks the already-window+allocatable-scoped set, so it is **not** a full scan — lower priority. The omnibox EVENT scan is the one without any narrowing.

**Index design.** Maintain in `LocalCache` on reservation put/remove (and allocatable put/remove for the resource side). The structure depends on the match semantics (OQ4): exact/prefix → a normalized `Map<String, Set<String>>` (lowercased name/token → ids) suffices; **substring/FUZZY** (what the omnibox uses today) needs a token or n-gram index, or a sorted-name structure with a candidate-narrowing pass before `SearchMatcher.rank`. The index narrows the candidate set; `SearchMatcher.rank` + `canModify`/`canRead` still run on the narrowed set (never trust the index to gate §12). **id-first, order-later** holds: the index yields candidate ids, ranking + permission gating + top-N happen after.

## Implementation

**The index alone does nothing — it only pays off through a consumer.** Ship index + `buildStorageFilter` change as one package.

- `LocalCache`: add `Map<String, Set<String>>` `allocatableIdsByTypeId` (ConcurrentHashMap; value = synchronized/`CopyOnWrite`-friendly id set). Maintain in `put(Entity)` (line ~154, the `Allocatable.class` branch already calls `updateDependencies`) and `remove(...)` (line ~100). On put: if the allocatable's type changed vs the old entity, remove its id from the old type's bucket and add to the new — the "kurzer cache check bei jeder Änderung" the design calls for. Multi-pod safe: derived purely from the same put/remove stream as the existing `resources` map, no extra coordination.
- New accessor `Set<String> getAllocatableIdsForType(String typeId)` (or `Collection<Allocatable> getAllocatablesForType(DynamicType)`).
- `AbstractCachableOperator.getAllocatables(filters)`: when the filter set names concrete types (single or union), build the candidate set from the bucket(s) instead of `new HashSet<>(cache.getAllocatables())`. Keep internal-type exclusion + `maxPerType` semantics.
- `ClassificationGraphQLController.buildStorageFilter`: emit one `ClassificationFilter` per type for `typeKeyIn` (currently dropped), and derive the implied type from a sole `where<Type>` block (B′) when no explicit typeKey is set.
- **id-first, order-later** (per design): the index yields *ids*; resolve ids→entities and apply ordering/limit at the output boundary (a `Set` has no order). Multi-type union = union of buckets.

## Goal

- A `typeKeyIn:[A,B]` allocatable query visits `O(bucket(A)+bucket(B))` allocatables, not 47,893 — verifiable by a unit test asserting `getAllocatables` does not iterate the full population for a typed filter, and by a live latency drop on the class-2 query.
- `allocatables(typeKeyEq:"Raum")` resolves from the bucket (no 47,893-copy) — live median materially below today's 22 ms.
- No behaviour change: same id sets returned (existing PRD 066 + leak tests stay green).

## Scope

### In scope
- `LocalCache` type-bucket index + maintenance on put/remove.
- `getAllocatables(filters)` bucket path for typed filters.
- `buildStorageFilter` `typeKeyIn` + B′ implied-type push-down.
- `LocalCache` **reservation-name search index** (+ allocatable-name) for the omnibox; replace the EVENT-bucket full `getReservations()` scan.

### Out of scope (of Workstream A — see the modernization evaluation above)
- **`queryAppointmentsSync`-over-N** (class 4/6) — the per-allocatable appointment-binding iteration; needs a window-first appointment index (now part of option 1 / feeds option 3).
- **Admin `canRead` short-circuit** (the 155 ms untyped `canRead`×48k) — cheap independent fix; track here as a sibling lever but ship separately (see OQ2).
- Per-block fan-out micro-optimizations (EL re-parse, cartesian churn, DTO churn) — measured **not** latency-dominant at realistic block counts (≤~500 blocks/year-window); deferred.

## Plan

### Phase 1 — Index + maintenance
- [ ] Add `allocatableIdsByTypeId` to `LocalCache`; populate on `put`/`remove`/`putAll`; handle type-change on re-put.
- [ ] `getAllocatableIdsForType` / `getAllocatablesForType` accessor + unit test over `testdefault.xml` (Raum vs resource1/Teilraum counts; re-type moves the id).

### Phase 2 — Consume the type-bucket index
- [ ] `getAllocatables(filters)` builds candidates from bucket(s) for typed filters; preserve internal-type + `maxPerType`.
- [ ] `buildStorageFilter`: `typeKeyIn` → per-type `ClassificationFilter[]`; sole `where<Type>` → implied type.
- [ ] Tier-3: `allocatables(typeKeyIn:[…])` returns same set as today; live latency probe before/after.

### Phase 3 — Name search index (after OQ4)
- [ ] Decide index structure from match semantics (OQ4); add `LocalCache` name index for reservations (+ allocatables), maintained on put/remove.
- [ ] `SearchGraphQLController` EVENT bucket: narrow candidates via the index before `SearchMatcher.rank` + `canModify`; same result set, no full `getReservations()` scan.
- [ ] Tier-3 (`SearchGraphQLControllerTest`): omnibox event-name search returns identical hits/order as the full scan; §12 (`canModify`-only) preserved; live latency probe.

## Tests

- Tier-2 (`LocalCache`/operator): bucket membership + maintenance (put new room → in bucket; re-type → moves; remove → gone). Assert `getAllocatables(typeFilter)` returns the bucket without a full-population scan (e.g. via a counting wrapper or by correctness over the fixture).
- Tier-3 (`ClassificationGraphQLControllerTest`): `typeKeyIn` multi-type union equals the union of per-type `typeKeyEq` results; `typeKeyEq` unchanged; §12 leak tests stay green.
- Live: re-run the 2026-06-22 probes after the user restarts — class 1/2 latency drop; class 5 (`idIn`) unchanged.

## Open Questions

- **OQ1** — Frequency of multi-type `typeKeyIn` vs single `typeKeyEq` vs `idIn` in real SPA traffic? Determines whether class 2 (biggest index win) is actually exercised. *Resolution:* pending — ask before Phase 2 ordering.
- **OQ2** — Ship the **admin `canRead` short-circuit** (skip `canRead` when `caller.isAdmin()`) first? It's a 3-line fix addressing the 155 ms untyped `canRead`×48k floor (class 6), with a live measurement, cheaper than the index. *Resolution:* pending — likely yes, as a quick win before Phase 1.
- **OQ3** — Class 4 (type-scoped analytics) residual: is the `queryAppointmentsSync`-over-bucket cost (e.g. all 1,872 rooms ≈ tens of ms) acceptable, or does it also warrant the window-first index? *Resolution:* pending — measure class 4 with the index in place before deciding.
- **OQ4** — Name-index structure: the omnibox uses SUBSTRING/FUZZY today. Exact/prefix needs only a normalized `Map<String,Set<String>>`; substring/fuzzy needs an n-gram/token index (more memory + maintenance). Keep FUZZY and pay for an n-gram index, or relax the omnibox to prefix-on-index + fuzzy-rerank on the narrowed set? *Resolution:* pending — measure the current full-scan latency on the dhbw reservation count first to size the win.

## Decisions locked

**D1 — Store ids, not entities (`Map<String, Set<String>>`).** The `Set<String>` *is* the type-group answer for class-1 queries; ids are stable across re-reads, avoid stale-entity references, and order/limit is applied after resolving at the output boundary (the design's "ids first, order later"). Rejected `Map<DynamicType, List<Allocatable>>` — heavier, order baked in prematurely, stale-entity risk on cache swap.

**D2 — Index + `buildStorageFilter` ship together.** The index is inert without a consumer; `buildStorageFilter`'s `typeKeyIn`/B′ push-down is the consumer. Splitting them ships dead code.

**D3 — Orthogonal to `queryAppointmentsSync`.** The index attacks allocatable *resolution* (class 1/2/3), not the per-allocatable appointment-binding iteration (class 4/6). Those are separate levers (window-first index / admin short-circuit) and out of scope for Workstream A — do not conflate.

**D4 — `reservations()` scoped-set direct-resolve (shipped 2026-06-22).** `ReservationGraphQLController.reservations()` no longer calls `getAllocatables(null)` for scoped queries; it resolves the scoped allocatables directly via `classificationController.allocatables(...)` (idIn/matching arms, deduped by id = union). Removes the fixed O(47,893) copy+scan tax from every scoped query. This is the precondition that makes class-5 (`idIn`) queries cheap independent of the index; verified behaviour-preserving by the new genuine-union guard test + existing §12 leak tests (72/72 green).

**D5 — Name index narrows, never gates.** The name index yields candidate ids only; `SearchMatcher.rank` + `canModify`/`canRead` always run on the narrowed set (§12 — never trust the index for permission or final match). id-first, rank+gate+top-N after. Same posture as the type-bucket index.
