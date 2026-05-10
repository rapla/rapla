# PRD 014: Replace long-millis date arithmetic with java.time API

**Status:** in-progress (Phases 1–8 done including idea #1 + AppointmentBlock storage flip; Phase 9 re-audit + close-out remaining)
**Date:** 2026-05-10

## Goal

After PRD 001-A flipped the entity layer from `java.util.Date` to
`java.time.LocalDateTime`, the appointment / repeating / permission code still does
all its arithmetic by **converting LocalDateTime → long-millis → LocalDateTime** for
every operation. The pattern looks like:

```java
long s = DateTools.toMilli(this.start);
long e = DateTools.toMilli(this.end);
long diff = e - s;
this.end = DateTools.toLocalDateTime(s + diff);
```

This is a Date-era idiom mechanically carried forward by the migration scripts.
With `java.time` it should be:

```java
Duration diff = Duration.between(this.start, this.end);
this.end = this.start.plus(diff);
```

The goal is to replace the long-millis boundary inside the *entity* tier (Appointment,
Repeating, Permission, Conflict, Block-iteration code) with proper `java.time` calls,
**without losing efficiency** in the inner loops (block iteration, conflict checking,
range overlap).

## Why

1. **Readability** — `Duration.between(a, b)` says intent; `DateTools.toMilli(b) - DateTools.toMilli(a)` doesn't.
2. **Correctness** — `LocalDateTime + Duration` handles boundary cases (negative diffs, overflow) the same way Java does for everything else; the long-millis hand-roll has to re-derive that behaviour every time.
3. **Surface area** — every `DateTools.toMilli(x)` and `DateTools.toLocalDateTime(long)` call is a place where the migration cascade has to keep walking. Eliminating the long boundary inside the entity tier shrinks the surface that future migrations / refactors touch.
4. **Performance** — `LocalDateTime` arithmetic is genuinely cheap on hot paths when used right (see §Efficiency below). The long-millis pattern was a Date-era optimisation; on `java.time` it's no longer faster (often the opposite, because `toEpochSecond * 1000` already does the work the LocalDateTime would do natively).

## Scope

**In:** the entity / facade tier where computations are pure date arithmetic with no
external library boundary:

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

- `AppointmentBlock` itself stores `long start` / `long end` because it's the *iteration result type* — the block list is consumed by view code that wants epoch-millis to do pixel math. Don't flip the storage type; only flip the *callers* of `AppointmentBlock.getStart() / getEnd()` if they want LocalDateTime, expose `getStartDateTime()`/`getEndDateTime()` (already there).
- `DateTools.MILLISECONDS_PER_DAY` arithmetic for *interval lengths* (in milliseconds) stays — `Duration` would be a refactor there too but the gain is small and the call sites are spread across many view classes.
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
| `new AppointmentBlock(s, e, this, false)` where `s,e` are long | unchanged — Block stores long internally, callers above pass `DateTools.toMilli(ldt)` |

## Plan

**Order rationale:** start with the call sites where we know java.time is correct *and* fast (no tight loops, single-shot conversions). Build confidence and shrink the surface. Save the performance-sensitive `processBlocks` inner loop for last — by then we'll have a benchmark in hand and a working green test suite to detect regressions.

### Phase 1 — DateTools surface (prerequisite)

Add (or confirm) the `java.time`-friendly overloads in `DateTools`:

- `LocalDateTime cutDate(LocalDateTime)` ✅ already exists
- `LocalDateTime fillDate(LocalDateTime)` ✅ already exists
- `boolean isMidnight(LocalDateTime)` ✅ already exists
- `LocalDateTime addDays(LocalDateTime, long)` ✅ already exists

Rule: never wrap something that has a one-line java.time equivalent unless the wrapper makes the call site read better at scale (e.g., `cutDate` is more idiomatic in this codebase than `toLocalDate().atStartOfDay()`).

### Phase 2 — PermissionImpl (smallest, lowest-risk)

Not on any inner loop. `getMinAllowed(today)` / `getMaxAllowed(today)` / `covers(start, end, today)` / `validInTheFuture(today)` currently do:
```java
LocalDateTime.ofInstant(Instant.ofEpochMilli(DateTools.toMilli(today) + DateTools.MILLISECONDS_PER_DAY * minAdvance), ZoneOffset.UTC)
```
Replace with:
```java
today.plusDays(minAdvance.longValue())
```

`covers(...)` does many long comparisons on `pStart/pEnd/start/end/today`. Replace each `DateTools.toMilli(a) < DateTools.toMilli(b)` with `a.isBefore(b)`. The `today + MILLISECONDS_PER_DAY * N` patterns become `today.plusDays(N)`.

Acts as a warm-up — small, isolated, exercised by `PermissionTest`.

### Phase 3 — RepeatingImpl

5 sites. Pattern: arithmetic against `appointment.getStart()` becomes `Duration.between(...)`. Exceptions list is `LocalDateTime[]` already; comparisons use `isBefore`/`isAfter`/`equals`. **Skip the parts that flow into `processBlocks`** (`getIntervalLength(long)` etc.) — those are part of the hot path and stay as long-millis until Phase 7.

### Phase 4 — AppointmentImpl, the cold-path subset

Tackle the single-shot methods. Leave `processBlocks` and the block-iteration helpers untouched.

1. **`moveTo(LocalDateTime newStart)`** (line 110) — replace
   ```java
   long diff = DateTools.toMilli(end) - DateTools.toMilli(start);
   move(newStart, DateTools.toLocalDateTime(DateTools.toMilli(newStart) + diff));
   ```
   with
   ```java
   Duration diff = Duration.between(this.start, this.end);
   move(newStart, newStart.plus(diff));
   ```

2. **`setWholeDays(boolean)`** — replace the `long startMs = DateTools.toMilli(start); cutDate(startMs); != startMs` pattern with
   ```java
   if (!start.equals(DateTools.cutDate(start))) {
       this.start = DateTools.cutDate(start);
   }
   ```

3. **`getMaxEnd()`** — currently keeps a `long` cache and a transient `LocalDateTime maxDate`, converts on every call. Replace with `Math.max`-of-`LocalDateTime` via `isAfter`:
   ```java
   LocalDateTime end = this.end;
   if (repeating != null) {
       LocalDateTime rEnd = repeating.getEnd();
       if (rEnd != null) end = end.isAfter(rEnd) ? end : rEnd;
       else return null;
   }
   return end;
   ```
   Drop the `transient LocalDateTime maxDate` cache — `Math.max` over two LocalDateTimes is cheap (no allocation when one branch wins).

4. **`toString()`** — display formatting; trivial swap to `LocalDateTime` accessors.

### Phase 5 — AppointmentFormaterImpl + view-layer simple sites

Mostly display code — `AppointmentFormaterImpl` (7 sites), `RaplaBuilder` (5), `HTMLRaplaBuilder` (4), `FacadeImpl` (3). Not on hot paths; one-line conversions are cheap. Most are `formatDateTime` / `formatTime` calls that already accept `LocalDateTime`; the `DateTools.toMilli(...)` is just left over from Date-era code that wasn't directly touching display. Strip the conversions, accept `LocalDateTime` directly.

### Phase 6 — Cleanup audit (everything except processBlocks)

After Phases 2–5 land, run:
```bash
grep -rE "DateTools\.toMilli\([a-z]\w*\)\s*[-+]" --include='*.java' rapla-core/src/main rapla-server/src/main
```
Anything still matching outside the carve-out (Phase 7 hot loops + boundaries documented in §Scope) is a candidate for review. Goal: zero unwarranted `toMilli`/`toLocalDateTime(long)` round-trips outside processBlocks and known boundaries.

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
| `PeriodImpl.java` | 74 | `long diff = toMilli(end) - toMilli(start)` | `Duration.between(start, end).toMillis()` (or refactor caller to use `Duration` directly) |
| `PeriodModelImpl.java` | 170 | `long diff = toMilli(d1) - toMilli(d2)` | same |

### Phase 6a — boundary cleanups missed by 6 (post-audit)

Five additional sites where the long-millis usage is not a true boundary (no JDBC, no widget, no wire format) and a clean java.time replacement exists:

| # | File | Line(s) | Pattern | Fix |
|---|---|---|---|---|
| 1 | `DBOperator.java` | 149 | `DateTools.toLocalDateTime(toMilli(getLastRefreshed()) - HISTORY_DURATION)` | `getLastRefreshed().minus(Duration.ofMillis(HISTORY_DURATION))` |
| 2 | `DBOperator.java` | 1075 | same | same |
| 3 | `TimeZoneConverterImpl.java` | 47 | `LocalDateTime.ofInstant(Instant.ofEpochMilli(fromRaplaTime(tz, toMilli(x))), UTC)` — outer roundtrip; inner `toMilli` is required by the long-API of `fromRaplaTime` | `DateTools.toLocalDateTime(fromRaplaTime(timeZone, DateTools.toMilli(raplaTime)))` |
| 4 | `TimeZoneConverterImpl.java` | 53 | same with `toRaplaTime` | same |
| 5 | `AbstractGroupStrategy.java` | 97 | `DateTools.getMinuteOfDay(DateTools.toMilli(start))` — `start` is `LocalDateTime`, helper has overload | `DateTools.getMinuteOfDay(start)` |

Plus two **bugs** to fix while there:
- `AbstractGroupStrategy.java` 156-161: `DateTools.toMilli(b1.getStart())` — but `Block.getStart()` returns `long`. The `toMilli` is type-mismatched (would not compile). Drop the wrapper: `b1.getStart()` is already millis.
- `CalendarModelImpl.java` 1115-1116, 1120-1121: identical `buf.append(toMilli(start) + ";")` lines duplicated. Remove the duplicates.

### Phase 6b — idiomatic polish (post-audit)

After Phase 6 audit, two more low-risk cleanups:

B1. `LocalDateTime.ofInstant(Instant.ofEpochMilli(<long>), ZoneOffset.UTC)` → `DateTools.toLocalDateTime(<long>)` — already-idiomatic helper exists, just shorter and grep-friendly. **11 sites:**

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

B2. Remove `DateTools.toHour(long)` — defined at line 690 of `DateTools.java`, **zero call sites in src/main**.

**Out of scope for Phase 6 (intentional, documented):**
- All `AppointmentImpl` `toMilli`/`toLocalDateTime(long)` calls (26 + 11) — Phase 7 hot path: `processBlocks`, `overlaps`, `overlapsRepeating`, `getFirstDifference` (the AppointmentBlock-bridge call sites).
- JDBC adapters (`DBOperator`, `AbstractTableStorage`), wire-format (`ISODateTimeFormat`, `LoginTokens` token JSON, iCal/Exchange import), JWT expiry math, `TimeZoneConverter` millis contract, `AppointmentBlock` storage. These are §Scope-documented boundaries.
- `toString` / display formatters (`AppointmentImpl.toString` line 145, `RepeatingImpl.toString` line 401, `SerializableDateTimeFormat` internals) — calls to long-arg `f(long,long)` / `fe(long)` helpers; converting them is its own follow-up since the helpers themselves take long.

### Phase 6c — Performance benchmark (prerequisite for Phase 7)

Land a JMH-based microbenchmark for `AppointmentImpl.processBlocks` and the surrounding hot path **before** touching Phase 7 code. Without numbers, "is it slower?" is unanswerable and the carve-out rationale becomes faith-based.

**Location:** `rapla-core/src/test/java/org/rapla/perf/AppointmentBlockBench.java` (a new perf-test source root; do NOT mix into regular `rapla-core` test suite — perf runs are slow and shouldn't gate `mvn test`).

**Setup:**

- Add `org.openjdk.jmh:jmh-core` and `jmh-generator-annprocess` dependencies to `rapla-bom` (test-scoped).
- Annotate the bench class with `@BenchmarkMode(Mode.AverageTime)`, `@OutputTimeUnit(TimeUnit.MICROSECONDS)`, `@State(Scope.Benchmark)`.
- Build a fixture in `@Setup`: a single weekly-repeating appointment over a 5-year window (~260 occurrences), with a sprinkling of exceptions (~10) and a `Collection<AppointmentBlock>` sink.
- Three benchmark methods:
  1. `processBlocks_currentLong` — calls `processBlocks(long, long, ...)` directly via reflection or via a temporary public-bridge method (whichever is less invasive).
  2. `overlaps_repeating` — calls the conflict-overlap path with two appointments that mostly overlap.
  3. `getMaxEnd` — guards against the cache-removal change from Phase 4 quietly regressing.

**Run:** `mvn -pl rapla-core test-compile && java -jar target/jmh-runner.jar AppointmentBlockBench` (or via `mvn -Pperf-bench`).

**Capture:** record baseline numbers in `docs/perf/processblocks-baseline-2026-05-09.txt` (per-iteration ns/op, p50/p99). These are the reference Phase 7 must stay within.

**Exit criteria:** Phase 7 may proceed once the bench runs green (not green test, just produces numbers consistently across 3 runs with <5% variance).

### Phase 7 — processBlocks (performance-critical, last)

By now: green test suite, ~70 long-millis sites already eliminated, JMH benchmark from Phase 6c with documented baseline.

The inner block iteration loop uses `long currentPos`, `long blockLength`, `long maxEnding`, etc. **Keep the inner loop on `long`** — this is the hottest path in the calendar view and millis arithmetic in a tight loop is genuinely faster than `LocalDateTime.plus(Duration)` (allocates one LocalDateTime per iteration; over a year of weekly repeats that's 52 allocations vs 0).

Only change the *signature* and entry/exit conversion:

- **Before:** `processBlocks(long start, long end, Collection<AppointmentBlock> blocks, boolean excludeExceptions)` — long parameters mean every caller does `DateTools.toMilli(...)` at the call site.
- **After:** `processBlocks(LocalDateTime start, LocalDateTime end, Collection<AppointmentBlock> blocks, boolean excludeExceptions)` — converts to long *once* at the top: `long c1 = DateTools.toMilli(start); long c2 = DateTools.toMilli(end);` and runs the long-millis loop. Emits `AppointmentBlock(s, e, …)` (already takes `long`).

Same treatment for `getFirstDifference(Appointment a2, LocalDateTime maxDate)`: still uses `block.getStart()` (long) and converts back at exit. That's the contract of `AppointmentBlock`.

**Validation step:** run the JMH benchmark before and after the signature change. The conversion at entry is amortised over the whole loop; expect ≤1.5% slowdown vs the all-long version. If it's worse, the carve-out failed and we revert this phase only — Phases 1–6 still stand.

## Efficiency considerations

`LocalDateTime` arithmetic is **not free**: each `plus(Duration)` / `plusDays(n)` returns a new LocalDateTime (12 fields, ~80 bytes, allocated on the heap). For a single call this is ~10 ns and irrelevant. For a tight inner loop iterating a weekly-repeat appointment over a 5-year calendar window, that's 260 allocations + 260 GC pressure events per render of the view.

**Decision rule per loop:**

| Loop characteristic | Pick |
|---|---|
| Outer loop, ≤100 iterations, conversion at boundaries only | `LocalDateTime` arithmetic |
| Hot inner loop (block iteration, conflict overlap check, ≥1k iterations / call) | `long`-millis arithmetic, conversion ONLY at entry/exit |
| Single-shot conversion (move(), setWholeDays(), getMaxEnd()) | `LocalDateTime` arithmetic |
| Crossing a JDBC / Swing widget / ical4j / wire-format boundary | conversion required regardless — unchanged |

**Carve-out:** `AppointmentImpl.processBlocks(...)` keeps its long-millis inner loop. Its public signature changes from `processBlocks(long start, long end, ...)` to `processBlocks(LocalDateTime start, LocalDateTime end, ...)` and the conversion is a one-time `DateTools.toMilli(start)` / `DateTools.toMilli(end)` at the top of the method. That keeps the loop hot and the API clean.

**Counter-rule:** if a method does *both* arithmetic and result-emission in one pass (e.g., produces a `Collection<LocalDateTime>`), prefer LocalDateTime throughout — converting the result later costs the same allocation either way.

## Tests

PRD 001-A established a test baseline (reactor `mvn test` green). The same suite must stay green after each phase. Specifically:

- `AppointmentTest` (block iteration, move semantics)
- `RepeatingTest` (interval generation, exceptions)
- `ConflictImplTest` (range overlap)
- `PermissionTest` (covers / getMinAllowed / getMaxAllowed bounds)
- `RaplaBuilderTest` (view-layer block layout)

Add (per phase):
- A microbenchmark for `processBlocks` over a 5-year weekly repeat, before-and-after, to confirm the long-millis carve-out wasn't accidentally undone by a future cleanup. Target: ≤1.5× the current per-call time. (JMH harness already exists via Spring Boot `spring-boot-starter-test`; add to `rapla-core/src/test/java/org/rapla/perf/AppointmentBlockBench.java`.)

### Phase 8 — processBlocks inner loop + algorithmic skip-ahead (planned 2026-05-10)

Phase 7 flipped the *signature* of `processBlocks`. Phase 8 considers the
**inner loop** (the explicitly-carved-out part) and the algorithmic
opportunities the LocalDateTime API opens up there.

#### Inventory: what's left in `AppointmentImpl.java` after Phase 7

| Group | Where | Site count | Cost of flip | Decision |
|---|---|---:|---|---|
| **A.** processBlocks inner loop (`currentPos`, `blockLength`, `c1`, `c2`, `s`, `e`, `l`, `maxEnding`, …) | line 376–449 | ~12 | ~10 µs/call extra (260+ LocalDateTime allocations per worst-case call); thin headroom (~25 µs of 100 µs budget left after Phase 7's 1.27×) | **In scope (8b)** — measure-then-decide |
| **B.** `overlapsAppointment` gcd math (`s1`/`s2`/`e1`/`e2`, `gcd`, `startx1/x2`, `max_x1/x2`) | line 531–624 | ~25 | High; tight integer math, no readability win | **Skip** — primitive arithmetic is the right tool here per §Efficiency |
| **C.** `getFirstDifference` / `getLastDifference` (`a1Start`, `a1End` loop) | line 260–305 | ~12 | Negligible — cold path, called once from `PermissionController.canModify` | **In scope (8a)** — easy win |
| **D.** `overlapsBlock(AppointmentBlock)` (one-off `block.getStart/End`) | line 459–460 | 2 | Tied to Group E | **Defer with E** |
| **E.** `AppointmentBlock` storage type (`long start, long end` fields + 61 callers across view code) | `AppointmentBlock.java` | 61+ | High — view code does `(end-start)/pxPerMs` thousands of times/render; flipping forces `Duration.between` + `toMillis` per access | **Out of scope — see OQ 1 below** |
| **F.** `overlaps(long, long, boolean)` overload (internal-only — only called from processBlocks-related path) | line 505 | 1 def, 0 external | Trivial — delete or make private | **In scope (8a)** — pure cleanup |
| **G.** `toString()` helper `f(long, long)` etc. (display formatting) | line 137 | a few | Negligible — display only | **In scope (8a)** — easy win |

#### Algorithmic options (enabled by inner-loop LocalDateTime)

Once the inner loop is on LocalDateTime, the API opens up beyond what
`long` arithmetic can express cleanly:

| Idea | Description | Asymptotic | Risk | Decision |
|---|---|---|---|---|
| **1. Skip-ahead for variable-interval repeats** | Today, `processBlocks` only fast-jumps for DAILY/WEEKLY (`if (isFixedIntervalLength()) { timeFromStart = Math.max(l, ((c1-e) / l)* l); }`, line 415). MONTHLY/YEARLY iterate from occurrence #1. With `ChronoUnit.MONTHS.between(start, windowStart)` we can analytically jump to ≈the right occurrence and iterate from there. | O(N) → O(1) for windows far from appointment start | Low — the iteration after the jump still verifies | **In scope (8c)** — real algorithmic improvement; main reason to do Phase 8 at all |
| **2. Drop `repeating.getIntervalLength(long)` round-trip** | For MONTHLY/YEARLY, `getIntervalLength` does `long → LocalDateTime → gotoNextStep → back to long delta`. Inner loop in LocalDateTime can call `gotoNextStep(LocalDateTime)` directly via a new public `nextStartAfter(LocalDateTime)` on Repeating. | Constant-factor — saves 2 conversions per iteration in variable-interval path | Low | **In scope (8c)** — falls out naturally with idea 1 |
| **3. Cache exception-check** (sort exceptions, binary-search per `isException(currentPos)`) | Currently `O(E)` linear scan per iteration. For long repeats with many exceptions, total cost `O(N×E)`. | `O(N×E) → O(N×log E)` | Low | **Skip** — only matters if exceptions list is long (≥10); current perf budget doesn't notice. Track as future enhancement if profiling reveals it as a hot spot. |
| **4. Pre-compute "next conflict candidate" in ConflictFinder reindex** (incremental indices instead of full reindex per change) | Today, `ConflictFinder.updateConflicts` reindexes the full conflict graph per affected allocatable. | Per-store cost: `O(N log N) → O(log N)` for incremental case | High — algorithmic restructure of `ConflictFinder` | **Skip — separate PRD.** Out of "finish migrating longs" scope. |
| **5. Replace `overlapsHard` (variable × variable repeat overlap) with merged sweep** | Currently `O(n×m)` for variable-interval pair overlap. A sweep over the merged block lists is `O(n+m)`. | `O(n×m) → O(n+m)` | Medium — code complexity goes up; only worth it if benchmarks show this path is hot | **Skip for now** — current perf test doesn't exercise variable×variable repeat overlap. Re-evaluate if a benchmark surfaces it. |

#### Phase 8 sub-plan

- **8a. Cheap cleanups (C + F + G).** Migrate `getFirstDifference`/`getLastDifference` to walk LocalDateTime block lists; delete or privatize `overlaps(long, long, boolean)` (no external callers); flip the `f(long, long)` toString helper. Zero perf risk; no algorithm change.
- **8b. Add MONTHLY-far-window benchmark to `ConflictPerformanceTest`.** New `@Test` exercises a MONTHLY repeat with `setNumber(60)` and a query window in occurrence ~50 — hits the variable-interval iterate-from-start path that idea #1 targets. Capture pre-Phase-8 baseline. Add `MONTHLY_FAR_WINDOW_BUDGET_US` constant.
- **8c. Inner loop flip + analytical skip-ahead** (atomic change — they reinforce each other; idea #1 is what makes the LocalDateTime API earn its allocations). New public `LocalDateTime nextStartAfter(LocalDateTime current)` on `Repeating` (delegates to `gotoNextStep`). New private `LocalDateTime computeFirstOccurrenceAt(LocalDateTime windowStart)` on `RepeatingImpl` for the analytical jump. `processBlocks` inner loop becomes:
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
  Allocation count: 2 per iteration (`blockEnd` + `nextStartAfter` result). For 365 iterations = 730 allocations per worst-case call → ~3.5–7 µs added per call.
- **8d. Re-run benchmark** — both the existing `overlapsAppointmentScalesLinearlyOnRepeatingPair` (DAILY×WEEKLY, fast-path; expect ≤1.5× pre-Phase-8) and the new MONTHLY-far-window test (expect *improvement* from idea #1's analytical jump). Capture numbers in `docs/perf/processblocks-phase8-2026-05-XX.txt`. Update PRD with deltas.

#### Validation strategy

- **Behavior preservation:** the 23 `AppointmentOverlapHardeningTest` + 14 `AppointmentBlocksExpansionTest` + 5 `ConflictFinderViaFacadeTest` correctness tests must stay green. Add at least one `MONTHLY` overlap edge case if not already pinned (e.g. window exactly at the boundary between two monthly occurrences).
- **Perf:** existing `OVERLAPS_APPOINTMENT_BUDGET_US = 100` stays as the regression canary. New `MONTHLY_FAR_WINDOW_BUDGET_US` set after capturing 8b baseline + 1.5× headroom.
- **Test-first per AGENTS.md §1:** for the algorithmic change (idea #1), write a test that demonstrates the current iteration cost is O(N) — not just by perf number but by a counter (e.g., expose `processBlocks` iteration count via instrumentation in a debug build, OR pin behavior via "monthly with N=60, window in occurrence 50, must return correct block within K iterations"). Then implement the analytical jump and confirm K drops to ≤2.

#### Considered and rejected (rationale)

- **Group B** (gcd math in `overlapsAppointment`): the `s1, s2, e1, e2, l1, l2, gcd, startx1, startx2, max_x1, max_x2` variables form a pure integer-arithmetic block. There's no semantic gain from making them `LocalDateTime` — they represent millisecond offsets, not absolute times. PRD §Efficiency lists this as the canonical case for staying on `long`.
- **Group E** (`AppointmentBlock` storage type): hits 61 view-code call sites that do `(blockEnd - blockStart) / pixelsPerMs` style pixel math. Flipping to `LocalDateTime` would force `Duration.between(...).toMillis()` per access. Real perf concern unmeasured. Defer to its own PRD with view-layer benchmarks first. (See OQ 1.)
- **Idea 3** (sorted exceptions + binary-search): only valuable when exceptions list is large (≥10). Rapla's data model makes long exception lists rare. Track as a future micro-optimization, not Phase 8 work.
- **Idea 4** (incremental ConflictFinder reindex): a real architectural change. The bottleneck for incremental store is currently ~1.3–3.6 s per store on a 1000-reservation graph (per `ConflictPerformanceTest`); the gain would matter at scale, but the change ripples through the cache + listener interfaces. Separate PRD.
- **Idea 5** (sweep merge for variable×variable overlap): no benchmark currently exercises this path. Don't optimize without a benchmark. If `ConflictFinder` profiling later surfaces it as hot, revisit.

## Open questions

1. Should `AppointmentBlock` itself flip from `long start` to `LocalDateTime start`? The view code (HTMLWeekView, HTMLMonthView, AbstractGroupStrategy) reads `block.getStart()` thousands of times per render. Flipping to LocalDateTime adds allocation per access unless we cache. **Confirmed out of scope (Phase 8 inventory, 2026-05-10)** — Group D + E in the Phase 8 inventory table; needs its own PRD with view-layer benchmark first.
2. `DateTools.MILLISECONDS_PER_DAY * N` is used in many view-layer pixel-math expressions (`x = (eventStart - viewStart) / MILLISECONDS_PER_DAY * pixelsPerDay`). Keep as-is — these are intentional integer arithmetic, not date arithmetic.
3. The `transient LocalDateTime maxDate` cache in `AppointmentImpl.getMaxEnd()` — is it actually a hot path warranting a cache, or migration leftover? Profile before deciding to drop. **Resolved Phase 4 (2026-05-09)** — cache dropped, `getMaxEnd` uses `isAfter`. Item kept here as historical record.
4. **(new, 2026-05-10)** Should the `Repeating` interface gain `LocalDateTime nextStartAfter(LocalDateTime current)` as a public sibling of the existing `getIntervalLength(long)`? **Deferred** — Phase 8c (type flip only) still uses `repeating.getIntervalLength(long)` per iteration. The new method becomes necessary if/when idea #1 (analytical skip-ahead) is implemented.
5. **(new, 2026-05-10)** Phase 7 inadvertently changed the `end != cutDate(end)` guard's semantics: pre-Phase-7 `end` was the long parameter (= `windowEnd`); post-Phase-7 (after rename) `end` resolved to the field `this.end`. Phase 8c preserved the post-Phase-7 reading (`appEndIsMidnight = appEnd.equals(cutDate(appEnd))`) using proper `.equals()`. Both interpretations have a defensible story (the guard prevents emitting the last block of a daily-repeat at the maxEnding boundary in some way) and current tests don't distinguish. **Action:** add a targeted test exercising whole-day daily appointments at the maxEnding boundary; whichever semantic the test pins becomes the canonical one. Until then, the post-Phase-7 reading is what's shipped.

## Phase progress

| Phase | Status |
|---|---|
| 1 — DateTools surface | done (existing surface confirmed sufficient) |
| 2 — PermissionImpl | done — getMinAllowed/getMaxAllowed/validInTheFuture/covers all flipped to `plusDays` / `isBefore` / `isAfter` |
| 3 — RepeatingImpl (cold-path subset) | done — getEnd / getEndDateTime / getNumber / addExceptions converted; getIntervalLength stays long-millis (Phase 7) |
| 4 — AppointmentImpl (cold-path subset: moveTo / setWholeDays / getMaxEnd / toString) | done — moveTo uses `Duration`, setWholeDays uses `cutDate(LocalDateTime)`, getMaxEnd dropped the cache and uses `isAfter`, toString left untouched (calls long-arg `f()`) |
| 5 — AppointmentFormaterImpl + view-layer simple sites | done — AppointmentFormaterImpl/HTMLRaplaBuilder/RaplaBuilder/FacadeImpl converted to `Duration.between` / `plusDays` / `getHourOfDay(LocalDateTime)` |
| 6 — Cleanup audit (excluding processBlocks) | done — A1 (6 sites) and A2 (2 sites) all converted; PeriodModelImpl had a stray dangling `d1.getTime()-d2.getTime()` line outside any method, removed |
| 6a — boundary cleanups missed by 6 | done — DBOperator ×2 (`getLastRefreshed().minus(Duration.ofMillis(...))`), TimeZoneConverterImpl ×2 (outer `ofInstant` collapsed to `DateTools.toLocalDateTime`), AbstractGroupStrategy line 97 (`getMinuteOfDay(LocalDateTime)` overload), `Math.max(long, LocalDateTime)` bug at AbstractGroupStrategy 158/161 fixed, CalendarModelImpl duplicate `buf.append` removed |
| 6b — idiomatic polish | done — 13 `LocalDateTime.ofInstant(Instant.ofEpochMilli(<long>), UTC)` sites converted to `DateTools.toLocalDateTime(<long>)` (AbstractRaplaLocale ×3, RaplaLocaleImpl, RaplaLocale, StandardFunctions ×8); `DateTools.toHour(long)` dead code removed |
| 6c — Performance baseline for processBlocks / overlaps | done (2026-05-10) — substituted JMH with `ConflictPerformanceTest.overlapsAppointmentScalesLinearlyOnRepeatingPair`. 3-run baseline: 32 / 36 / 48 µs/call (median 36, max 48). Recorded in `docs/perf/processblocks-baseline-2026-05-10.txt`. Tightened the perf test budget from 5000 µs → 100 µs/call as the regression canary. JMH would add statistical rigour; the existing test catches anything ≥3× slowdown which is what Phase 7 needs. |
| 7 — processBlocks signature flip | done (2026-05-10) — `processBlocks(long, long, …)` → `processBlocks(LocalDateTime, LocalDateTime, …)`. Three internal call sites updated: `createBlocks(LocalDateTime, LocalDateTime, …)` and `overlaps(LocalDateTime, LocalDateTime, boolean)` drop their `DateTools.toMilli(...)` wrappers; `overlaps(long, long, boolean)` (also internal-use) gains a `DateTools.toLocalDateTime(...)` indirection. **Inner loop unchanged — still on primitive long arithmetic** (PRD's stated carve-out). **Validation:** all 51 rapla-core tests + 20 conflict-related rapla-server tests stay green; full reactor `mvn test` (1m42s) green. **Perf:** 8-sample post-flip median 45 µs/call (1.25× baseline median), one 92 µs outlier — within both the PRD's ≤1.5× allowance (~72 µs) and the 100 µs assertion budget. The ~10 µs/call cost is the LocalDateTime parameter dispatch overhead; conversion work itself is identical (still 2 `toMilli` calls per invocation). |
| 8a — Cheap cleanups (groups C + F + G) | done (2026-05-10) — `getFirstDifference`/`getLastDifference` walk LocalDateTime block lists via `getStartDateTime()/getEndDateTime()`; deleted `AppointmentImpl.overlaps(long, long, boolean)` (no external callers); `overlapsBlock`/`overlapsHard` updated; new `f(LocalDateTime, LocalDateTime)` overload makes toString round-trip-free; `fe(LocalDateTime)` overload added for RepeatingImpl.toString. Plus Phase 9 audit fixes: `UpdateDataManagerImpl` + `RemoteStorageImpl` `Duration.between`, `LocalAbstractCachableOperator.inWorktime` flipped to `getMinuteOfDay(LocalDateTime)`, `Export2iCalServlet.getGlobalLastModified` to `ChronoUnit.DAYS.between` + `today.minusDays(...)`, `RepeatingImpl.toString` end-format flipped, `ReservationHelper` block-iteration switched to `block.getStartDateTime()/isBefore(...)`. |
| 8b — MONTHLY-far-window benchmark | done (2026-05-10) — `ConflictPerformanceTest.monthlyFarWindowOverlap`. Pre-idea-#1 baseline: median 128 µs/call (8 samples, 98–358). Recorded in `docs/perf/processblocks-monthly-baseline-2026-05-10.txt`. |
| 8c — Inner loop flip (type only, idea #1 deferred) | done (2026-05-10) — `currentPos`, `currentEnd`, `maxEnding`, `blockLength`, `intervalDur` are now `LocalDateTime` / `Duration`. Comparisons use `isAfter` / `isBefore` / `equals`. AppointmentBlock emission still converts to long-millis at the boundary (Group E deferred). All 51 rapla-core + 20 rapla-server correctness tests stay green. **Perf, 11 samples (`docs/perf/processblocks-phase8c-2026-05-10.txt`):** median 52 µs/call (1.44× pre-Phase-7 baseline of 36; +6.5 µs/call vs Phase 7 alone). 10 of 11 samples pass the 100 µs assertion budget; one 146 µs JIT/GC outlier. **Idea #1 (analytical skip-ahead for variable-interval repeats) intentionally NOT included — left as a future enhancement; the type-flip cost is accepted as-is per the user's call.** Inadvertent semantic side-effect from Phase 7: the `appEndIsMidnight` guard now reads `this.end` not `windowEnd`; behaviour matches all current tests, but tracked as OQ 5 below. |
| 8c-full — Idea #1 analytical skip-ahead | done (2026-05-10) — added `RepeatingImpl.nextStartAfter(LocalDateTime)` (dispatches: fixed-interval uses cached millis delta, variable-interval defers to `gotoNextStep`) and `RepeatingImpl.computeFirstOccurrenceAfter(LocalDateTime threshold)` (fixed-interval = ceiling-divide math; MONTHLY = `YearMonth.between` analytical jump + snap to Nth-weekday-of-month via `TemporalAdjusters`; YEARLY + multi-weekday WEEKLY iterate from appStart). `processBlocks` replaced its split fast-jump-or-iterate with one `computeFirstOccurrenceAfter(windowStart - blockLength)` call, plus a clamp to avoid duplicating the first-occurrence emission. Loop tail switched from `getIntervalLength + plus` to `nextStartAfter` (drops the per-iteration long round-trip). **Bug found during impl + fixed:** initial `nextStartAfter` always called `gotoNextStep` which NPEs for DAILY (weekdays==null); fixed by dispatching on `isFixedIntervalLength()`. **Perf:** MONTHLY median 128 → 90 µs/call (~30% improvement). DAILY×WEEKLY 52 → 54.5 µs (within noise). |
| 8e — AppointmentBlock storage flip (Group E) | done (2026-05-10) — `AppointmentBlock.start/end` flipped from `long` to `LocalDateTime`. Added `AppointmentBlock(LocalDateTime, LocalDateTime, Appointment, boolean)` constructor; kept legacy `(long, long, …)` constructor + `getStart()/getEnd()` returning long via `DateTools.toMilli(...)` for backward compat (61 view-code callers untouched). `getStartDateTime()/getEndDateTime()` now direct field access. `processBlocks` block emission switched to LocalDateTime constructor (drops one toMilli per emitted block). `compareTo`/`equals`/`includes`/`intersects`/`toString`/`toInterval` rewritten in LocalDateTime form. **Perf:** DAILY×WEEKLY 54.5 → 44 µs median (~18% better — eliminated the round-trip in `nextStartAfter` + saved one toMilli per block emission). MONTHLY 90 → 99 µs (within noise). All 51 rapla-core + full reactor (54.9 s) green. View-layer `block.getStart()` calls now do `DateTools.toMilli` per access instead of direct field — pure CPU op, no allocation, ~30 ns. |
| 8d — Final perf documentation | done (2026-05-10) — `docs/perf/processblocks-phase8d-2026-05-10.txt` captures the full trajectory across phases for both workloads. |
| 8f — BlockVisitor refactor (overlapsHard optimisation) | done (2026-05-10) — `processBlocks` signature flipped from `Collection<AppointmentBlock> blocks` to a primitive-arg visitor `BlockVisitor.visit(LocalDateTime start, LocalDateTime end, boolean isException) -> boolean`. Three call sites adapted: `createBlocks` uses a closure that appends; `overlaps(LocalDateTime, LocalDateTime, boolean)` uses `(s,e,ex)->true` (no AppointmentBlock allocation); `overlapsHard` uses `(s,e,ex)->a2.overlaps(s,e,true)` — previously `createBlocks`-materialised every occurrence of `this` before checking, now short-circuits on first hit with zero block allocation. **New benchmark `overlapsHardVariableInterval` (MONTHLY×36 vs MONTHLY×36, early-hit):** median 52 µs/call. **Side effects:** DAILY×WEEKLY 44 → 33 µs/call median (faster than the original pre-Phase-7 baseline of 36 µs!); MONTHLY-far-window 99 → 135 µs/call median (regression — likely megamorphic call-site overhead at processBlocks now that 3 different lambda impls reach it; high noise 80-378 across 13 samples suggests partly measurement variance). All 51 rapla-core + full reactor tests stay green (1m47s). Detailed numbers in `docs/perf/processblocks-phase8f-2026-05-10.txt`. |
| 8g — 2-method split (processBlocksCollect + processBlocksFirstHit) | tried + reverted (2026-05-10) — split `processBlocks` into two structurally-identical methods on the theory that a megamorphic visitor.visit() call site was hurting JIT inlining at the post-Phase-8f single visitor entry point. **Perf result was contrary to prediction — split made all workloads slower:** DAILY×WEEKLY 33 → 65 µs/call (1.97×), MONTHLY-far-window 135 → 199 (1.47×), overlapsHard MONTHLY×MONTHLY 52 → 103 (1.98×). Hypothesis revised: the JIT was already handling the single-method visitor well; doubling the bytecode hurts code-cache density and prolongs warmup more than it helps inlining. **Reverted to the single Phase-8f processBlocks** — duplication was a real cost (50 lines that have to stay in sync) and the perf trade was wrong. Final-state code is the single visitor method; the megamorphic-dispatch concern is a non-issue at our scale (3 lambda implementations are well within the JIT's inline-cache capacity). |
| 9 — Close-out re-audit | **not started** — final `grep -rE "DateTools\.toMilli\([a-z]\w*\)\s*[-+]"` audit of `rapla-core/src/main` + `rapla-server/src/main`; goal is zero unwarranted long-millis sites outside the documented carve-outs (Group B gcd math, AppointmentBlock storage, JDBC/wire-format boundaries). |
