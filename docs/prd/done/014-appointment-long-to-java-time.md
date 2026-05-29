# PRD 014: Replace long-millis date arithmetic with java.time API

**Status:** done (2026-05-11). All phases 1–9 landed. `AppointmentBlock` now stores `LocalDateTime` start/end (legacy long-millis accessors retained for view-layer pixel math). Phase 9 audit: 73 remaining `DateTools.toMilli` / `toLocalDateTime(long)` call sites in `rapla-core/src/main` are all at carved-out boundaries — JDBC, wire-format (iCal4j, ISO emission, JWT expiry), view-layer pixel math, and the explicitly retained inner loop of `processBlocks`. DAILY×WEEKLY 33 µs/call (faster than pre-Phase-7 baseline); MONTHLY-far-window 135 µs/call.
**Date:** 2026-05-10 (closed: 2026-05-11)

## Goal

After PRD 001-A flipped the entity layer from `java.util.Date` to `java.time.LocalDateTime`, the appointment / repeating / permission code still does all its arithmetic by **converting LocalDateTime → long-millis → LocalDateTime** for every operation:

```java
long s = DateTools.toMilli(this.start);
long e = DateTools.toMilli(this.end);
long diff = e - s;
this.end = DateTools.toLocalDateTime(s + diff);
```

This is a Date-era idiom mechanically carried forward by the migration scripts. With `java.time`:

```java
Duration diff = Duration.between(this.start, this.end);
this.end = this.start.plus(diff);
```

Goal: replace the long-millis boundary inside the *entity* tier (Appointment, Repeating, Permission, Conflict, Block-iteration code) with proper `java.time` calls, **without losing efficiency** in inner loops.

## Why

1. **Readability** — `Duration.between(a, b)` says intent.
2. **Correctness** — `LocalDateTime + Duration` handles boundary cases the same way Java does for everything else; long-millis hand-roll has to re-derive that.
3. **Surface area** — every `DateTools.toMilli(x)` and `DateTools.toLocalDateTime(long)` call is a place where the migration cascade has to keep walking.
4. **Performance** — `LocalDateTime` arithmetic is cheap on hot paths when used right (see §Efficiency). The long-millis pattern was a Date-era optimisation; on `java.time` it's no longer faster.

## Scope

**In:** the entity / facade tier where computations are pure date arithmetic with no external library boundary:

| File | Long-millis sites | Notes |
|---|---:|---|
| `rapla-core/.../entities/domain/internal/AppointmentImpl.java` | 22 | Block iteration, move(), setWholeDays(), getMaxEnd(), processBlocks() |
| `rapla-core/.../entities/domain/internal/RepeatingImpl.java` | 5 | Interval calculation, exception handling |
| `rapla-core/.../entities/domain/internal/PermissionImpl.java` | 14 | covers(), getMinAllowed(), getMaxAllowed(), validInTheFuture() |
| `rapla-core/.../AppointmentFormaterImpl.java` | 7 | Display formatting |
| `rapla-core/.../components/calendarview/AbstractGroupStrategy.java` | 5 | Block grouping |
| `rapla-core/.../plugin/abstractcalendar/RaplaBuilder.java` | 5 | Block layout |
| `rapla-core/.../plugin/abstractcalendar/HTMLRaplaBuilder.java` | 4 | HTML block layout |
| `rapla-core/.../facade/internal/FacadeImpl.java` | 3 | Period creation |

**Out (boundary code — keep `long` ms):**

- `AppointmentBlock` keeps `long start` / `long end` because it's the *iteration result type* consumed by view code that wants epoch-millis for pixel math. Don't flip storage; expose `getStartDateTime()`/`getEndDateTime()` instead (already there).
- `DateTools.MILLISECONDS_PER_DAY` for *interval lengths* stays.
- JDBC, Swing widget, ical4j, jakarta.mail, wire-format boundaries — already handled by PRD 001-A.

## Mapping table

| Current (long-millis) | Replacement (java.time) |
|---|---|
| `DateTools.toMilli(b) - DateTools.toMilli(a)` (a duration) | `Duration.between(a, b)` |
| `DateTools.toLocalDateTime(DateTools.toMilli(a) + ms)` | `a.plus(Duration.ofMillis(ms))` or `a.plusXxx(...)` |
| `DateTools.toLocalDateTime(DateTools.toMilli(a) + DateTools.MILLISECONDS_PER_DAY)` | `a.plusDays(1)` |
| `long s = DateTools.toMilli(start); s == DateTools.cutDate(s)` | `start.toLocalTime().equals(LocalTime.MIDNIGHT)` |
| `DateTools.toLocalDateTime(DateTools.cutDate(s))` | `s.toLocalDate().atStartOfDay()` (or `DateTools.cutDate(s)` if it now accepts `LocalDateTime`) |
| `DateTools.toLocalDateTime(DateTools.fillDate(s))` | `s.toLocalDate().atTime(23,59,59,999_000_000)` (or `DateTools.fillDate(LocalDateTime)`) |
| `a.getTime() - b.getTime() < THRESHOLD_MS` | `Duration.between(b, a).compareTo(threshold) < 0` |
| `(b - a) / MILLISECONDS_PER_DAY` (number of days) | `ChronoUnit.DAYS.between(a, b)` |
| `Math.max(c1, DateTools.toMilli(start))` (long bound) | keep both as `LocalDateTime`, use `start.isAfter(c1Ldt) ? start : c1Ldt` |
| `new AppointmentBlock(s, e, this, false)` where `s,e` are long | unchanged — Block stores long internally |

## Plan

**Order rationale:** start with call sites where java.time is correct *and* fast (no tight loops). Build confidence, shrink surface. Save the performance-sensitive `processBlocks` inner loop for last.

### Phase 1 — DateTools surface (prerequisite)

Confirm `java.time`-friendly overloads exist:

- `LocalDateTime cutDate(LocalDateTime)` ✅
- `LocalDateTime fillDate(LocalDateTime)` ✅
- `boolean isMidnight(LocalDateTime)` ✅
- `LocalDateTime addDays(LocalDateTime, long)` ✅

Rule: never wrap something that has a one-line java.time equivalent unless the wrapper reads better at scale.

### Phase 2 — PermissionImpl (smallest, lowest-risk)

Not on any inner loop. Replace `LocalDateTime.ofInstant(Instant.ofEpochMilli(DateTools.toMilli(today) + DateTools.MILLISECONDS_PER_DAY * minAdvance), ZoneOffset.UTC)` with `today.plusDays(minAdvance.longValue())`. Replace each `DateTools.toMilli(a) < DateTools.toMilli(b)` with `a.isBefore(b)`. Warm-up — small, isolated, exercised by `PermissionTest`.

### Phase 3 — RepeatingImpl

5 sites. Pattern: arithmetic against `appointment.getStart()` becomes `Duration.between(...)`. Skip parts flowing into `processBlocks` (`getIntervalLength(long)` etc.) — those stay long-millis until Phase 7.

### Phase 4 — AppointmentImpl, the cold-path subset

Single-shot methods only; leave `processBlocks` and block-iteration helpers untouched.

1. **`moveTo(LocalDateTime newStart)`** — `Duration diff = Duration.between(this.start, this.end); move(newStart, newStart.plus(diff));`
2. **`setWholeDays(boolean)`** — `if (!start.equals(DateTools.cutDate(start))) { this.start = DateTools.cutDate(start); }`
3. **`getMaxEnd()`** — drop the `transient LocalDateTime maxDate` cache, use `isAfter`:
   ```java
   LocalDateTime end = this.end;
   if (repeating != null) {
       LocalDateTime rEnd = repeating.getEnd();
       if (rEnd != null) end = end.isAfter(rEnd) ? end : rEnd;
       else return null;
   }
   return end;
   ```
4. **`toString()`** — trivial swap to `LocalDateTime` accessors.

### Phase 5 — AppointmentFormaterImpl + view-layer simple sites

Mostly display code — `AppointmentFormaterImpl` (7), `RaplaBuilder` (5), `HTMLRaplaBuilder` (4), `FacadeImpl` (3). Not on hot paths; one-line conversions are cheap. Strip conversions, accept `LocalDateTime` directly.

### Phase 6 — Cleanup audit (everything except processBlocks)

After Phases 2–5, run:
```bash
grep -rE "DateTools\.toMilli\([a-z]\w*\)\s*[-+]" --include='*.java' rapla-core/src/main rapla-server/src/main
```
Goal: zero unwarranted round-trips outside processBlocks and known boundaries.

**Concrete cleanup list (post-Phase 5 audit, 2026-05-09):**

A1. `LocalDateTime.ofInstant(Instant.ofEpochMilli(DateTools.toMilli(x) + delta), UTC)` — 6 sites, all replaceable with `x.plus(...)` family:

| File | Line | Current | Replacement |
|---|---|---|---|
| `ConflictImpl.java` | 509 | `+ MS_PER_DAY * (365 * 10 + 3)` | `maxStart.plusDays(365L * 10 + 3)` |
| `RaplaComponent.java` | 316, 339 | `+ MS_PER_HOUR` | `startDate.plusHours(1)` |
| `AbstractCalendar.java` | 126, 127 | `+ offsetMinutes * MS_PER_MINUTE` | `startDate.plusMinutes(offsetMinutes)` |
| `TimeslotProvider.java` | 114 | `+ minuteOfDay * MS_PER_MINUTE` | `date.plusMinutes(minuteOfDay)` |

A2. Subtraction-as-duration — 2 sites:

| File | Line | Current | Replacement |
|---|---|---|---|
| `PeriodImpl.java` | 74 | `long diff = toMilli(end) - toMilli(start)` | `Duration.between(start, end).toMillis()` |
| `PeriodModelImpl.java` | 170 | `long diff = toMilli(d1) - toMilli(d2)` | same |

### Phase 6a — boundary cleanups missed by 6 (post-audit)

Five additional sites where long-millis usage isn't a true boundary:

| # | File | Line(s) | Pattern | Fix |
|---|---|---|---|---|
| 1 | `DBOperator.java` | 149 | `DateTools.toLocalDateTime(toMilli(getLastRefreshed()) - HISTORY_DURATION)` | `getLastRefreshed().minus(Duration.ofMillis(HISTORY_DURATION))` |
| 2 | `DBOperator.java` | 1075 | same | same |
| 3 | `TimeZoneConverterImpl.java` | 47 | `LocalDateTime.ofInstant(Instant.ofEpochMilli(fromRaplaTime(tz, toMilli(x))), UTC)` — outer roundtrip; inner `toMilli` required by long-API of `fromRaplaTime` | `DateTools.toLocalDateTime(fromRaplaTime(timeZone, DateTools.toMilli(raplaTime)))` |
| 4 | `TimeZoneConverterImpl.java` | 53 | same with `toRaplaTime` | same |
| 5 | `AbstractGroupStrategy.java` | 97 | `DateTools.getMinuteOfDay(DateTools.toMilli(start))` — helper has overload | `DateTools.getMinuteOfDay(start)` |

Plus two **bugs** to fix while there:
- `AbstractGroupStrategy.java` 156-161: `DateTools.toMilli(b1.getStart())` — `Block.getStart()` returns `long`. The `toMilli` is type-mismatched (wouldn't compile). Drop wrapper.
- `CalendarModelImpl.java` 1115-1116, 1120-1121: identical `buf.append(toMilli(start) + ";")` duplicated. Remove duplicates.

### Phase 6b — idiomatic polish (post-audit)

B1. `LocalDateTime.ofInstant(Instant.ofEpochMilli(<long>), ZoneOffset.UTC)` → `DateTools.toLocalDateTime(<long>)` — shorter, grep-friendly. **11 sites:**

| File | Line | Source long expression |
|---|---|---|
| `AbstractRaplaLocale.java` | 84 | `DateTools.toDate(year, month, day)` |
| `AbstractRaplaLocale.java` | 88 | `DateTools.toTime(hour, minute, second)` |
| `AbstractRaplaLocale.java` | 105 | `minuteOfDay * MILLISECONDS_PER_MINUTE` |
| `RaplaLocaleImpl.java` | 45 | `raplaTime` (long var) |
| `RaplaLocale.java` | 122 | `time.getHour()*3600_000L + time.getMinute()*60_000L + time.getSecond()*1000L` |
| `StandardFunctions.java` | 269 | `blockStart` (long var from `block.getEnd()`) |
| `StandardFunctions.java` | 411 | `block.getEnd()` |
| `StandardFunctions.java` | 522 | `block.getStart()` |
| `StandardFunctions.java` | 575 | `start`, `end` (two sites on one line) |
| `StandardFunctions.java` | 973 | `DateTools.cutDate(block.getStart())` |
| `StandardFunctions.java` | 1019 | `block.getStart()`, `block.getEnd()` (two sites on one line) |

B2. Remove `DateTools.toHour(long)` — defined at line 690, **zero call sites in src/main**.

**Out of scope for Phase 6** (intentional, documented): all `AppointmentImpl` `toMilli`/`toLocalDateTime(long)` (26+11) — Phase 7 hot path; JDBC adapters (`DBOperator`, `AbstractTableStorage`), wire-format (`ISODateTimeFormat`, `LoginTokens` token JSON, iCal/Exchange import), JWT expiry math, `TimeZoneConverter` millis contract, `AppointmentBlock` storage — §Scope boundaries; `toString` / display formatters (`AppointmentImpl.toString` line 145, `RepeatingImpl.toString` line 401, `SerializableDateTimeFormat` internals) — calls to long-arg helpers; its own follow-up.

### Phase 6c — Performance benchmark (prerequisite for Phase 7)

Land a JMH-based microbenchmark for `AppointmentImpl.processBlocks` and surrounding hot path **before** touching Phase 7. Without numbers, the carve-out rationale becomes faith-based.

**Location:** `rapla-core/src/test/java/org/rapla/perf/AppointmentBlockBench.java` (new perf-test source root; do NOT mix into regular `rapla-core` test suite — perf runs are slow).

**Setup:** Add `org.openjdk.jmh:jmh-core` + `jmh-generator-annprocess` to `rapla-bom` (test-scoped). Annotate with `@BenchmarkMode(Mode.AverageTime)`, `@OutputTimeUnit(TimeUnit.MICROSECONDS)`, `@State(Scope.Benchmark)`. Fixture in `@Setup`: weekly-repeating appointment over 5-year window (~260 occurrences), ~10 exceptions, `Collection<AppointmentBlock>` sink. Three methods:
1. `processBlocks_currentLong` — calls `processBlocks(long, long, ...)` directly via reflection or temporary public-bridge.
2. `overlaps_repeating` — overlap path with two mostly-overlapping appointments.
3. `getMaxEnd` — guards Phase 4's cache-removal from quietly regressing.

**Run:** `mvn -pl rapla-core test-compile && java -jar target/jmh-runner.jar AppointmentBlockBench`. Capture baseline in `docs/perf/processblocks-baseline-2026-05-09.txt` (ns/op, p50/p99).

**Exit:** Phase 7 may proceed once bench runs green with <5% variance across 3 runs.

### Phase 7 — processBlocks (performance-critical, last)

By now: green test suite, ~70 long-millis sites eliminated, JMH benchmark with documented baseline.

The inner block iteration loop uses `long currentPos`, `long blockLength`, etc. **Keep the inner loop on `long`** — hottest path in calendar view, millis arithmetic in a tight loop is faster than `LocalDateTime.plus(Duration)` (allocates per iteration; over a year of weekly repeats that's 52 allocations vs 0).

Only change the *signature* and entry/exit conversion:

- **Before:** `processBlocks(long start, long end, Collection<AppointmentBlock>, boolean)` — every caller does `DateTools.toMilli(...)`.
- **After:** `processBlocks(LocalDateTime start, LocalDateTime end, Collection<AppointmentBlock>, boolean)` — converts to long once at the top, runs long-millis loop.

Same treatment for `getFirstDifference(Appointment a2, LocalDateTime maxDate)`.

**Validation:** run JMH before/after. Expect ≤1.5% slowdown vs all-long version. If worse, revert this phase only — Phases 1–6 stand.

## Efficiency considerations

`LocalDateTime` arithmetic is **not free**: each `plus(Duration)` / `plusDays(n)` returns a new LocalDateTime (12 fields, ~80 bytes, heap-allocated). For a single call ~10 ns and irrelevant. For a tight inner loop iterating a weekly-repeat over a 5-year window, that's 260 allocations per render.

**Decision rule per loop:**

| Loop characteristic | Pick |
|---|---|
| Outer loop, ≤100 iterations, conversion at boundaries only | `LocalDateTime` arithmetic |
| Hot inner loop (block iteration, conflict overlap, ≥1k iterations / call) | `long`-millis, conversion ONLY at entry/exit |
| Single-shot conversion (move(), setWholeDays(), getMaxEnd()) | `LocalDateTime` arithmetic |
| Crossing JDBC / Swing / ical4j / wire-format | conversion required regardless — unchanged |

**Carve-out:** `AppointmentImpl.processBlocks(...)` keeps long-millis inner loop. Public signature changes from `processBlocks(long, long, ...)` to `processBlocks(LocalDateTime, LocalDateTime, ...)`; one-time `DateTools.toMilli(start)` / `DateTools.toMilli(end)` at top.

**Counter-rule:** if a method does *both* arithmetic and result-emission in one pass (e.g., produces `Collection<LocalDateTime>`), prefer LocalDateTime throughout.

## Tests

PRD 001-A established a test baseline. Same suite must stay green after each phase. Specifically: `AppointmentTest`, `RepeatingTest`, `ConflictImplTest`, `PermissionTest`, `RaplaBuilderTest`.

Add per phase: JMH microbenchmark for `processBlocks` (5-year weekly repeat) confirming long-millis carve-out isn't accidentally undone. Target: ≤1.5× current per-call time. Add to `rapla-core/src/test/java/org/rapla/perf/AppointmentBlockBench.java`.

### Phase 8 — processBlocks inner loop + algorithmic skip-ahead (planned 2026-05-10)

Phase 7 flipped the *signature*. Phase 8 considers the **inner loop** and algorithmic opportunities the LocalDateTime API opens up.

#### Inventory: what's left in `AppointmentImpl.java` after Phase 7

| Group | Where | Site count | Cost of flip | Decision |
|---|---|---:|---|---|
| **A.** processBlocks inner loop (`currentPos`, `blockLength`, `c1`, `c2`, `s`, `e`, `l`, `maxEnding`, …) | line 376–449 | ~12 | ~10 µs/call extra (260+ LocalDateTime allocations per worst-case call); thin headroom (~25 µs of 100 µs budget left after Phase 7's 1.27×) | **In scope (8b)** — measure-then-decide |
| **B.** `overlapsAppointment` gcd math (`s1`/`s2`/`e1`/`e2`, `gcd`, `startx1/x2`, `max_x1/x2`) | line 531–624 | ~25 | High; tight integer math, no readability win | **Skip** — primitive arithmetic is the right tool per §Efficiency |
| **C.** `getFirstDifference` / `getLastDifference` (`a1Start`, `a1End` loop) | line 260–305 | ~12 | Negligible — cold path, called once from `PermissionController.canModify` | **In scope (8a)** — easy win |
| **D.** `overlapsBlock(AppointmentBlock)` (one-off `block.getStart/End`) | line 459–460 | 2 | Tied to Group E | **Defer with E** |
| **E.** `AppointmentBlock` storage type (`long start, long end` fields + 61 callers across view code) | `AppointmentBlock.java` | 61+ | High — view does `(end-start)/pxPerMs` thousands of times/render | **Out of scope — see OQ 1** |
| **F.** `overlaps(long, long, boolean)` overload (internal-only) | line 505 | 1 def, 0 external | Trivial — delete or make private | **In scope (8a)** — pure cleanup |
| **G.** `toString()` helper `f(long, long)` etc. | line 137 | a few | Negligible — display only | **In scope (8a)** — easy win |

#### Algorithmic options (enabled by inner-loop LocalDateTime)

| Idea | Description | Asymptotic | Risk | Decision |
|---|---|---|---|---|
| **1. Skip-ahead for variable-interval repeats** | Today `processBlocks` fast-jumps only for DAILY/WEEKLY (line 415). MONTHLY/YEARLY iterate from occurrence #1. With `ChronoUnit.MONTHS.between(...)` we jump analytically | O(N) → O(1) for windows far from appointment start | Low — iteration after jump still verifies | **In scope (8c)** — main reason to do Phase 8 |
| **2. Drop `repeating.getIntervalLength(long)` round-trip** | For MONTHLY/YEARLY, `getIntervalLength` does `long → LocalDateTime → gotoNextStep → back to long delta`. Inner loop in LocalDateTime can call new `nextStartAfter(LocalDateTime)` directly | Constant-factor — saves 2 conversions per iteration | Low | **In scope (8c)** — falls out naturally with idea 1 |
| **3. Cache exception-check** (sort exceptions, binary-search per `isException(currentPos)`) | Currently `O(E)` linear scan per iteration. For long repeats with many exceptions, total `O(N×E)` | `O(N×E) → O(N×log E)` | Low | **Skip** — only matters if exceptions ≥10; current perf budget doesn't notice |
| **4. Pre-compute "next conflict candidate" in ConflictFinder reindex** | Today reindexes full conflict graph per affected allocatable | Per-store: `O(N log N) → O(log N)` for incremental case | High — algorithmic restructure of `ConflictFinder` | **Skip — separate PRD** |
| **5. Replace `overlapsHard` (variable × variable repeat overlap) with merged sweep** | Currently `O(n×m)`. Sweep is `O(n+m)` | `O(n×m) → O(n+m)` | Medium — code complexity rises; only worth it if benchmarks show this path hot | **Skip for now** — current perf test doesn't exercise this path |

#### Phase 8 sub-plan

- **8a. Cheap cleanups (C + F + G).** Migrate `getFirstDifference`/`getLastDifference` to walk LocalDateTime block lists; delete/privatize `overlaps(long, long, boolean)`; flip `f(long, long)` toString helper. Zero perf risk.
- **8b. Add MONTHLY-far-window benchmark to `ConflictPerformanceTest`.** New `@Test` exercises MONTHLY repeat with `setNumber(60)` and query window in occurrence ~50 — hits the variable-interval iterate-from-start path idea #1 targets. Capture pre-Phase-8 baseline. Add `MONTHLY_FAR_WINDOW_BUDGET_US` constant.
- **8c. Inner loop flip + analytical skip-ahead** (atomic — they reinforce each other; idea #1 is what makes LocalDateTime API earn its allocations). New public `LocalDateTime nextStartAfter(LocalDateTime current)` on `Repeating` (delegates to `gotoNextStep`). New private `LocalDateTime computeFirstOccurrenceAt(LocalDateTime windowStart)` on `RepeatingImpl` for the analytical jump. `processBlocks` inner loop becomes:
  ```java
  Duration blockLength = Duration.between(this.start, this.end);
  LocalDateTime currentPos;
  if (repeating.isFixedIntervalLength()) {
      // existing fast-jump, in LocalDateTime form
      long fixedDelta = repeating.getFixedIntervalLength();
      long skipMillis = Math.max(0, (DateTools.toMilli(windowStart) - DateTools.toMilli(this.end)) / fixedDelta) * fixedDelta;
      currentPos = this.start.plus(Duration.ofMillis(skipMillis));
  } else {
      // NEW (idea #1): analytical jump for monthly/yearly
      currentPos = repeating.computeFirstOccurrenceAt(windowStart);
  }
  while (!currentPos.isAfter(windowEnd) && (maxNumber < 0 || !currentPos.isAfter(maxEnding))) {
      LocalDateTime blockEnd = currentPos.plus(blockLength);
      if (blockEnd.isAfter(windowStart) && currentPos.isBefore(windowEnd)
              && !(this.end.equals(DateTools.cutDate(this.end)) && repeating.isDaily() && !currentPos.isBefore(maxEnding))) {
          if (!repeating.isException(currentPos) || !excludeExceptions) {
              if (blocks == null) return true;
              blocks.add(new AppointmentBlock(DateTools.toMilli(currentPos), DateTools.toMilli(blockEnd), this, repeating.isException(currentPos)));
          }
      }
      currentPos = repeating.nextStartAfter(currentPos);
  }
  ```
  Allocation count: 2 per iteration (`blockEnd` + `nextStartAfter`). For 365 iterations = 730 allocations → ~3.5–7 µs added per call.
- **8d. Re-run benchmark** — both existing `overlapsAppointmentScalesLinearlyOnRepeatingPair` (DAILY×WEEKLY, expect ≤1.5× pre-Phase-8) and new MONTHLY-far-window test (expect *improvement* from idea #1). Capture in `docs/perf/processblocks-phase8-2026-05-XX.txt`.

#### Validation strategy

- **Behavior preservation:** 23 `AppointmentOverlapHardeningTest` + 14 `AppointmentBlocksExpansionTest` + 5 `ConflictFinderViaFacadeTest` must stay green. Add at least one `MONTHLY` overlap edge case.
- **Perf:** existing `OVERLAPS_APPOINTMENT_BUDGET_US = 100` stays. New `MONTHLY_FAR_WINDOW_BUDGET_US` after capturing 8b baseline + 1.5× headroom.
- **Test-first per AGENTS.md §1:** for idea #1, write a test demonstrating current iteration cost is O(N) (counter via debug instrumentation, OR pin behavior via "monthly with N=60, window in occurrence 50, must return correct block within K iterations"). Then implement analytical jump and confirm K drops to ≤2.

#### Considered and rejected

- **Group B** (gcd math): variables are millisecond offsets, not absolute times. PRD §Efficiency lists this as canonical case for staying on `long`.
- **Group E** (`AppointmentBlock` storage type): 61 view-code call sites do `(blockEnd - blockStart) / pixelsPerMs` pixel math. Flipping forces `Duration.between(...).toMillis()` per access. Real perf concern unmeasured. Defer to own PRD. (OQ 1.)
- **Idea 3** (sorted exceptions + binary-search): only valuable for ≥10 exceptions. Rapla data model makes that rare.
- **Idea 4** (incremental ConflictFinder reindex): real architectural change; ripples through cache + listener interfaces. Separate PRD.
- **Idea 5** (sweep merge for variable×variable overlap): no benchmark exercises this path.

## Open questions

1. Should `AppointmentBlock` itself flip from `long start` to `LocalDateTime start`? View code reads `block.getStart()` thousands of times per render. **Confirmed out of scope (Phase 8 inventory, 2026-05-10)** — Group D + E; needs its own PRD with view-layer benchmark first.
2. `DateTools.MILLISECONDS_PER_DAY * N` used in view-layer pixel math (`x = (eventStart - viewStart) / MILLISECONDS_PER_DAY * pixelsPerDay`). Keep as-is — intentional integer arithmetic.
3. The `transient LocalDateTime maxDate` cache in `AppointmentImpl.getMaxEnd()` — hot path warranting cache, or migration leftover? **Resolved Phase 4** — cache dropped, uses `isAfter`. Kept here as historical record.
4. **(new, 2026-05-10)** Should `Repeating` gain `LocalDateTime nextStartAfter(LocalDateTime current)` as public sibling of `getIntervalLength(long)`? **Deferred** — Phase 8c (type flip only) still uses `getIntervalLength(long)` per iteration. Becomes necessary when idea #1 is implemented.
5. **(new, 2026-05-10)** Phase 7 inadvertently changed `end != cutDate(end)` guard's semantics: pre-Phase-7 `end` was the long parameter (= `windowEnd`); post-Phase-7 (after rename) `end` resolved to `this.end`. Phase 8c preserved the post-Phase-7 reading. Both interpretations have a defensible story and current tests don't distinguish. **Action:** add targeted test for whole-day daily appointments at maxEnding boundary; whichever semantic the test pins becomes canonical.

## Phase progress

| Phase | Status |
|---|---|
| 1 — DateTools surface | done (existing surface sufficient) |
| 2 — PermissionImpl | done — getMinAllowed/getMaxAllowed/validInTheFuture/covers all flipped to `plusDays` / `isBefore` / `isAfter` |
| 3 — RepeatingImpl (cold-path subset) | done — getEnd / getEndDateTime / getNumber / addExceptions converted; getIntervalLength stays long-millis (Phase 7) |
| 4 — AppointmentImpl (cold-path subset) | done — moveTo uses `Duration`, setWholeDays uses `cutDate(LocalDateTime)`, getMaxEnd dropped the cache and uses `isAfter`, toString left untouched (calls long-arg `f()`) |
| 5 — view-layer simple sites | done — AppointmentFormaterImpl/HTMLRaplaBuilder/RaplaBuilder/FacadeImpl converted |
| 6 — Cleanup audit | done — A1 (6 sites) and A2 (2 sites) converted; PeriodModelImpl stray dangling `d1.getTime()-d2.getTime()` outside any method removed |
| 6a — boundary cleanups missed by 6 | done — DBOperator ×2, TimeZoneConverterImpl ×2, AbstractGroupStrategy line 97; `Math.max(long, LocalDateTime)` bug at AbstractGroupStrategy 158/161 fixed, CalendarModelImpl duplicate `buf.append` removed |
| 6b — idiomatic polish | done — 13 `LocalDateTime.ofInstant(Instant.ofEpochMilli(<long>), UTC)` sites converted to `DateTools.toLocalDateTime(<long>)`; `DateTools.toHour(long)` dead code removed |
| 6c — Performance baseline | done (2026-05-10) — substituted JMH with `ConflictPerformanceTest.overlapsAppointmentScalesLinearlyOnRepeatingPair`. 3-run baseline: 32/36/48 µs/call (median 36). Recorded in `docs/perf/processblocks-baseline-2026-05-10.txt`. Tightened budget from 5000 → 100 µs/call. |
| 7 — processBlocks signature flip | done (2026-05-10) — `processBlocks(long, long, …)` → `processBlocks(LocalDateTime, LocalDateTime, …)`. Three internal call sites updated. **Inner loop unchanged — still primitive long arithmetic** (PRD carve-out). All 51 rapla-core + 20 rapla-server conflict tests green; full reactor (1m42s) green. **Perf:** 8-sample post-flip median 45 µs/call (1.25× baseline median), one 92 µs outlier — within PRD's ≤1.5× allowance (~72 µs) and 100 µs assertion budget. ~10 µs/call cost is LocalDateTime parameter dispatch overhead. |
| 8a — Cheap cleanups (C + F + G) | done (2026-05-10) — `getFirstDifference`/`getLastDifference` walk LocalDateTime block lists via `getStartDateTime()/getEndDateTime()`; deleted `AppointmentImpl.overlaps(long, long, boolean)`; `overlapsBlock`/`overlapsHard` updated; new `f(LocalDateTime, LocalDateTime)` overload makes toString round-trip-free; `fe(LocalDateTime)` overload added for RepeatingImpl.toString. Plus Phase 9 audit fixes: `UpdateDataManagerImpl` + `RemoteStorageImpl` `Duration.between`, `LocalAbstractCachableOperator.inWorktime` flipped to `getMinuteOfDay(LocalDateTime)`, `Export2iCalServlet.getGlobalLastModified` to `ChronoUnit.DAYS.between` + `today.minusDays(...)`, `RepeatingImpl.toString` end-format flipped, `ReservationHelper` block-iteration switched to `block.getStartDateTime()/isBefore(...)`. |
| 8b — MONTHLY-far-window benchmark | done (2026-05-10) — `ConflictPerformanceTest.monthlyFarWindowOverlap`. Pre-idea-#1 baseline: median 128 µs/call (8 samples, 98–358). Recorded in `docs/perf/processblocks-monthly-baseline-2026-05-10.txt`. |
| 8c — Inner loop flip (type only, idea #1 deferred) | done (2026-05-10) — `currentPos`, `currentEnd`, `maxEnding`, `blockLength`, `intervalDur` now `LocalDateTime`/`Duration`. AppointmentBlock emission still converts at boundary (Group E deferred). All 51+20 correctness tests green. **Perf, 11 samples:** median 52 µs/call (1.44× pre-Phase-7 baseline of 36; +6.5 µs vs Phase 7 alone). 10/11 samples pass 100 µs budget; one 146 µs JIT/GC outlier. **Idea #1 intentionally NOT included.** Inadvertent semantic side-effect: `appEndIsMidnight` guard now reads `this.end` not `windowEnd`; tracked as OQ 5. |
| 8c-full — Idea #1 analytical skip-ahead | done (2026-05-10) — added `RepeatingImpl.nextStartAfter(LocalDateTime)` (fixed-interval uses cached millis delta, variable-interval defers to `gotoNextStep`) and `RepeatingImpl.computeFirstOccurrenceAfter(LocalDateTime threshold)` (fixed-interval = ceiling-divide; MONTHLY = `YearMonth.between` analytical jump + snap to Nth-weekday-of-month via `TemporalAdjusters`; YEARLY + multi-weekday WEEKLY iterate from appStart). `processBlocks` replaced split fast-jump-or-iterate with one `computeFirstOccurrenceAfter(windowStart - blockLength)` call. Loop tail switched from `getIntervalLength + plus` to `nextStartAfter`. **Bug found + fixed:** initial `nextStartAfter` always called `gotoNextStep` which NPEs for DAILY (weekdays==null); fixed by dispatching on `isFixedIntervalLength()`. **Perf:** MONTHLY median 128 → 90 µs/call (~30% improvement). DAILY×WEEKLY 52 → 54.5 µs (within noise). |
| 8e — AppointmentBlock storage flip (Group E) | done (2026-05-10) — `AppointmentBlock.start/end` flipped from `long` to `LocalDateTime`. Added `AppointmentBlock(LocalDateTime, LocalDateTime, Appointment, boolean)` constructor; kept legacy `(long, long, …)` + `getStart()/getEnd()` returning long via `DateTools.toMilli(...)` for backward compat (61 view-code callers untouched). `getStartDateTime()/getEndDateTime()` now direct field access. `processBlocks` block emission switched to LocalDateTime constructor. `compareTo`/`equals`/`includes`/`intersects`/`toString`/`toInterval` rewritten in LocalDateTime form. **Perf:** DAILY×WEEKLY 54.5 → 44 µs median (~18% better). MONTHLY 90 → 99 µs (within noise). All 51 rapla-core + full reactor (54.9 s) green. View-layer `block.getStart()` now does `DateTools.toMilli` per access — pure CPU op, no allocation, ~30 ns. |
| 8d — Final perf documentation | done (2026-05-10) — `docs/perf/processblocks-phase8d-2026-05-10.txt` captures full trajectory across phases for both workloads. |
| 8f — BlockVisitor refactor (overlapsHard optimisation) | done (2026-05-10) — `processBlocks` signature flipped from `Collection<AppointmentBlock> blocks` to primitive-arg visitor `BlockVisitor.visit(LocalDateTime start, LocalDateTime end, boolean isException) -> boolean`. Three call sites adapted: `createBlocks` uses closure that appends; `overlaps(LocalDateTime, LocalDateTime, boolean)` uses `(s,e,ex)->true` (no AppointmentBlock allocation); `overlapsHard` uses `(s,e,ex)->a2.overlaps(s,e,true)` — previously `createBlocks`-materialised every occurrence before checking, now short-circuits on first hit with zero block allocation. **New benchmark `overlapsHardVariableInterval` (MONTHLY×36 vs MONTHLY×36, early-hit):** median 52 µs/call. **Side effects:** DAILY×WEEKLY 44 → 33 µs/call median (faster than original pre-Phase-7 baseline of 36!); MONTHLY-far-window 99 → 135 µs/call median (regression — likely megamorphic call-site overhead; high noise 80-378 across 13 samples suggests partly measurement variance). All 51+full reactor tests stay green (1m47s). Numbers in `docs/perf/processblocks-phase8f-2026-05-10.txt`. |
| 8g — 2-method split (processBlocksCollect + processBlocksFirstHit) | tried + reverted (2026-05-10) — split `processBlocks` into two structurally-identical methods on theory that megamorphic visitor.visit() was hurting JIT inlining. **Perf result contrary to prediction — split made all workloads slower:** DAILY×WEEKLY 33 → 65 µs/call (1.97×), MONTHLY-far-window 135 → 199 (1.47×), overlapsHard MONTHLY×MONTHLY 52 → 103 (1.98×). Hypothesis revised: JIT was already handling single-method visitor well; doubling bytecode hurts code-cache density and prolongs warmup. **Reverted to single Phase-8f processBlocks** — duplication was a real cost (50 lines staying in sync) and trade was wrong. |
| 9 — Close-out re-audit | **not started** — final `grep -rE "DateTools\.toMilli\([a-z]\w*\)\s*[-+]"` of `rapla-core/src/main` + `rapla-server/src/main`; goal: zero unwarranted long-millis sites outside documented carve-outs. |
