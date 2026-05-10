# Conflict detection and appointment overlap

How Rapla decides whether two reservations clash, where the algorithm
lives, what edge cases it handles, and how to measure its performance.

## What "conflict" means

A **conflict** in Rapla is two reservations on the **same allocatable**
whose appointment time-windows **overlap**. It's surfaced to the UI via
`facade.getConflicts()` (global list) and `facade.getConflictsForReservation(r)`
(neighbours of a single reservation). Internally a `Conflict` is an immutable
record `(allocatableRef, reservation1Ref, appointment1Ref, reservation2Ref,
appointment2Ref, startDate)` — see
[`org.rapla.facade.internal.ConflictImpl`](../rapla-core/src/main/java/org/rapla/facade/internal/ConflictImpl.java).

The detection has two layers:

| Layer | What it does | Where |
|---|---|---|
| Pairwise overlap | "Do two appointments share any time on the timeline?" | `AppointmentImpl.overlapsAppointment(a2)` — [rapla-core](../rapla-core/src/main/java/org/rapla/entities/domain/internal/AppointmentImpl.java#L520) |
| Conflict graph | "For each allocatable, which reservation pairs overlap?" | `ConflictFinder` — [rapla-server](../rapla-server/src/main/java/org/rapla/storage/impl/server/ConflictFinder.java) |

Layer 1 is pure logic; Layer 2 maintains the per-allocatable index that
`getConflicts()` reads from, and reindexes on every store/remove via
`updateConflicts(...)`.

## Time semantics

- **Closed-open intervals.** An appointment `[09:00, 10:00)` and another
  `[10:00, 11:00)` **do not overlap** — the touching edge belongs to the
  later one. The check is the standard `!(e2 ≤ s1 || e1 ≤ s2)`.
- **Wall-clock millis.** Comparisons use `DateTools.toMilli(LocalDateTime)`.
  All timestamps are GMT internally; DST is a presentation concern, not a
  scheduling concern. A daily 09:00 appointment occurs every calendar day,
  including the DST transition days.
- **Zero-duration appointments** (`start == end`) are degenerate but
  symmetric — whatever the implementation decides, `a.overlaps(b)` always
  equals `b.overlaps(a)`. The hardening test pins symmetry, not a specific
  truth value.

## RepeatingType semantics

Rapla's `RepeatingType` does not always mean what calendar-app users assume:

| Type | Step rule | Source |
|---|---|---|
| `DAILY` | `start + interval×N` days | fixed-interval |
| `WEEKLY` | `start + 7×interval×N` days, optionally restricted to specific weekdays | fixed-interval if 1 weekday; variable if multiple weekdays |
| `MONTHLY` | **Nth weekday of the month** (e.g. "third Thursday"), NOT same day-of-month | variable-interval |
| `YEARLY` | Same date next year; for Feb 29 starts, advances to next leap year | variable-interval |

The MONTHLY semantic is the one that surprises people — a monthly repeat
starting on Thu 2026-01-15 lands on Thu 2026-02-19, Thu 2026-03-19, Thu
2026-04-16. **Not** the 15th of every month. `gotoNextMonth(...)` in
[`RepeatingImpl`](../rapla-core/src/main/java/org/rapla/entities/domain/internal/RepeatingImpl.java)
matches by `getDayOfWeekInMonth`. (This is how Outlook and Google Calendar
do their default "monthly" repeat too.)

This is pinned by `AppointmentOverlapHardeningTest.monthlyRepeatUsesNthWeekdayOfMonthNotSameDayOfMonth`.

## Pairwise overlap algorithm

`AppointmentImpl.overlapsAppointment(Appointment a2)` is the symmetric
predicate at the heart of every conflict check. It dispatches by the
repeating shape of both appointments:

```
                    a2 single        a2 repeating
       a1 single    closed-open      a2.overlaps(a1.start, a1.end)
                    bounds check     → expands a2's blocks
       a1 repeating a1.overlaps(...)  see "both repeating" below
                    → expands a1's blocks
```

### Both single

The fast path: two intervals on the timeline. `!(e2 ≤ s1 || e1 ≤ s2)`.
O(1). [AppointmentImpl.java:535](../rapla-core/src/main/java/org/rapla/entities/domain/internal/AppointmentImpl.java#L535).

### One single, one repeating

Delegate to `repeating.overlaps(single.start, single.end, excludeExceptions=true)`,
which calls `processBlocks(...)`. `processBlocks` walks the repeating's
occurrences in order, returning `true` as soon as any block overlaps the
target window. Exceptions (added via `repeating.addException(date)`) are
skipped. Worst-case O(N) in the number of occurrences, but it short-circuits.

### Both repeating

This is the interesting case. There's a fast path for *fixed-interval*
pairs (DAILY, WEEKLY-single-weekday) and a slow path for *variable-interval*
pairs (MONTHLY, YEARLY, multi-weekday WEEKLY).

**Fast path — `gcd` arithmetic.** [AppointmentImpl.java:572-624](../rapla-core/src/main/java/org/rapla/entities/domain/internal/AppointmentImpl.java#L572).
Both repeats have a fixed interval length `l1`, `l2` in milliseconds. Their
occurrence sets overlap iff there exist non-negative integers `x1`, `x2`
such that:

```
| (s1 + x1·l1) - (s2 + x2·l2) | < blockLength
```

The implementation steps through `x1, x2` bounded by the **gcd-derived
period** `l2/gcd(l1,l2)` for `x1` and `l1/gcd(l1,l2)` for `x2`. If neither
fixed end-date is hit and the search exhausts without finding a match,
**no overlap**. Otherwise overlap. O(`l/gcd`) iterations, typically
single-digit for compatible periods (DAILY-vs-WEEKLY → gcd = day, period
= 7).

**Slow path — `overlapsHard`.** [AppointmentImpl.java:629](../rapla-core/src/main/java/org/rapla/entities/domain/internal/AppointmentImpl.java#L629).
For variable-interval repeats, expand both into block lists (truncated by
their respective end-dates) and do an O(n·m) sweep. Bounded by the
`number` cap on each repeat — MONTHLY×12 + YEARLY×4 is at most 48
comparisons.

### Why the fast path matters

The whole point of the gcd machinery is that **two daily appointments for
a year (365 occurrences each) do not need 365² overlap checks**. The
gcd trick reduces this to constant time per pair. The hardening test
`twoOverlappingDailyRepeatsOverlap` and the perf test's
`overlapsAppointmentScalesLinearlyOnRepeatingPair` both pin this — the
latter measures **~30–60 µs per call** for daily-365 × weekly-52.

## Conflict graph (`ConflictFinder`)

The graph layer aggregates pairwise overlaps into a per-allocatable
conflict index. State:

```
conflictMap : Map<ReferenceInfo<Allocatable>, Map<ReferenceInfo<Conflict>, Conflict>>
```

For each allocatable, every reservation pair whose appointments overlap
contributes one `Conflict`. `getConflicts(user)` walks the whole map and
filters by `permissionController.canModify(conflict, user)`.

### Reindexing on store / remove

`updateConflicts(bindingsResult, currentUpdateResult, today)` is called
from the operator's dispatch path after every store or remove. It:

1. Computes the set of allocatables whose binding changed (added or
   removed reservations).
2. For each such allocatable, runs `sweepLine(allocatable, today,
   intervals)` over the new set of `AppointmentBlock`s — a standard
   sweep-line that reports overlapping pairs in `O(n log n)`.
3. Diffs the new conflict set against the old, emitting
   `ConflictChangeOperation`s for the cache + listeners.

Old conflicts that have ended (both appointments fully in the past) are
purged via `removeOldConflicts(today)`.

### Cost model

Per single store on a graph of N reservations across K allocatables:

- One sweep per affected allocatable. With repeating appointments, "blocks"
  are expanded to a bounded number per appointment (capped by `number`).
- Bulk store is an N×1 dispatch: one reindex, all changes batched.
- The `incremental store + ConflictFinder reindex` measurement in the
  perf test (~1.3–3.6 s for one new reservation on a 1000-reservation
  graph) is dominated by the full sweep on the affected allocatable plus
  serialization of the resulting XML.

## Performance test

Class: [`ConflictPerformanceTest`](../rapla-server/src/test/java/org/rapla/storage/impl/server/ConflictPerformanceTest.java),
tagged `@Tag("perf")`. **Excluded from the default test lane** (per
PRD 017 BOM config: `<test.excludedGroups>db,e2e,perf</test.excludedGroups>`).
Run on demand only.

### Running it

```bash
# include all tagged tests (db + e2e + perf)
mvn -pl rapla-server -am test -Dtest=ConflictPerformanceTest -Dtest.excludedGroups=

# include perf, keep db + e2e excluded
mvn -pl rapla-server -am test -Dtest=ConflictPerformanceTest \
    -Dtest.excludedGroups=db,e2e

# stream surefire output live (otherwise [perf] lines are buffered)
mvn -pl rapla-server -am test -Dtest=ConflictPerformanceTest \
    -Dtest.excludedGroups=db,e2e -Dsurefire.useFile=false
```

The test prints `[perf]` phase markers to stderr at start and end of each
phase plus a build-progress line every 200 reservations:

```
[perf] === ConflictPerformanceTest starting (~10–20 s expected) ===
[perf] phase 1/6: build 1000 reservations in memory — start
  ... built 200 / 1000
  ... built 400 / 1000
  ... built 600 / 1000
  ... built 800 / 1000
[perf] phase 1/6: build 1000 reservations in memory — done in 207 ms
[perf] phase 2/6: bulk storeObjects 1000 — start
[perf] phase 2/6: bulk storeObjects 1000 — done in 13029 ms
[perf]   post-store facade.getReservations count = 1000
[perf] phase 3/6: facade.getConflicts() — start
[perf] phase 3/6: facade.getConflicts() — done in 698 ms
[perf]   conflict count = 16259
[perf] phase 4/6: getConflictsForReservation(pivot) — start
[perf] phase 4/6: getConflictsForReservation(pivot) — done in 15 ms
[perf] phase 5/6: getReservationsForAllocatable (3-month window) — start
[perf] phase 5/6: getReservationsForAllocatable (3-month window) — done in 6 ms
[perf] phase 6/6: incremental store + ConflictFinder reindex — start
[perf] phase 6/6: incremental store + ConflictFinder reindex — done in 3610 ms
[perf] === ConflictPerformanceTest finished ===
```

### Fixture shape

1000 reservations distributed across the 6 allocatables in `testdefault.xml`
(small pool → forces real conflicts), spread across a 6-month window in
2030. Mix:

| Pattern | Share | Number of occurrences |
|---|---:|---|
| Single (no repeat) | ~30 % | 1 |
| DAILY | ~30 % | 10–30 |
| WEEKLY | ~20 % | 4–12 |
| MONTHLY | ~15 % | 3–6 |
| DAILY with exceptions | ~5 % | 60–120 occurrences, 2–5 exceptions |

Random seed is fixed (`20260510L`) so the conflict count is deterministic
across runs. Expected: **16,259 conflicts** with this fixture.

### Reference numbers (Linux WSL2, May 2026)

| Phase | Wall | Notes |
|---|---:|---|
| Build 1000 reservations in memory | ~130–210 ms | Pure object construction |
| Bulk `storeObjects(Reservation[])` | ~7–13 s | Persists to XML, full conflict reindex on the affected allocatables |
| `facade.getConflicts()` (16K conflicts) | ~280–700 ms | Walks `conflictMap`, applies permission filter |
| `facade.getConflictsForReservation(pivot)` | ~10–15 ms | Pulls one entry from the index |
| `getReservationsForAllocatable` (3-month window) | ~5–10 ms | LocalCache lookup + window filter |
| Incremental `store(one new reservation)` | ~1.3–3.6 s | One full sweep on the affected allocatable + serialize |
| `overlapsAppointment` × 1000 (DAILY×365 vs WEEKLY×52) | ~30–60 µs/call | gcd fast-path |

### Sanity-budget assertions

The perf test asserts each operation completes within a generous
order-of-magnitude ceiling — not a tight regression bound. Current
budgets (in `ConflictPerformanceTest`):

| Phase | Budget | Headroom over current |
|---|---:|---:|
| Bulk store | 60 s | ~5× |
| `getConflicts` | 15 s | ~20× |
| Per-reservation query | 5 s | ~300× |
| Incremental store | 10 s | ~3× |
| `overlapsAppointment` | 5 ms/call | ~100× |

The goal is to flag a 5–10× regression loudly, not to fail noisily on
normal noise.

## Hardening test

Class: [`AppointmentOverlapHardeningTest`](../rapla-core/src/test/java/org/rapla/entities/tests/AppointmentOverlapHardeningTest.java),
tier-1, runs in the default lane. 23 tests, ~110 ms total. Pins:

- Identity / containment / partial / touching-edge / 1 ms gap
- Zero-duration symmetry
- Single × repeating: in-occurrence, in-gap, on-exception, day-before-exception, past-end
- Repeating × repeating: same period, disjoint times, separate ranges, daily+weekly intersection
- MONTHLY = Nth-weekday-of-month (NOT same-day-of-month) — both negative (Apr 15) and positive (Apr 16) cases
- YEARLY across leap year (Feb 29 origin)
- DST spring-forward and fall-back days
- **Symmetry check on every test** — `a.overlaps(b)` must equal `b.overlaps(a)`. This catches the largest class of conflict-detection bugs (asymmetric predicates that miss one direction).

## storeAndRemoveAsync (fixed 2026-05-10)

`LocalAbstractCachableOperator.storeAndRemoveAsync(...)` was an empty stub
that returned an OK promise without persisting anything. `facade.dispatch(...)`
routes through it, so the async dispatch path silently dropped writes on
file-backed operators.

**Surfaced by:** `ConflictPerformanceTest` — first run reported 1000
reservations "dispatched" in 54 ms with 0 actually persisted.

**Fix:** delegate to the sync `storeAndRemove` inside `scheduler.run(...)` —
matches the pattern PRD 008 established (server uses sync as source of truth,
async wraps it).

```java
public Promise<Void> storeAndRemoveAsync(...) {
    return scheduler.run(() -> storeAndRemove(storeObjects, removeObjects, user, forceRessourceDelete));
}
```

**Pinned by:** `FacadeMutationTest.asyncDispatchActuallyPersists` — calls
`facade.dispatch(...)` and asserts the entity is resolvable afterward.
