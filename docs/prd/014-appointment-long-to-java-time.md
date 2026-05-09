# PRD 014: Replace long-millis date arithmetic with java.time API

**Status:** draft
**Date:** 2026-05-09

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

## Open questions

1. Should `AppointmentBlock` itself flip from `long start` to `LocalDateTime start`? The view code (HTMLWeekView, HTMLMonthView, AbstractGroupStrategy) reads `block.getStart()` thousands of times per render. Flipping to LocalDateTime adds allocation per access unless we cache. Probably **out of scope for this PRD**; would be a follow-up after we have a benchmark to validate.
2. `DateTools.MILLISECONDS_PER_DAY * N` is used in many view-layer pixel-math expressions (`x = (eventStart - viewStart) / MILLISECONDS_PER_DAY * pixelsPerDay`). Keep as-is — these are intentional integer arithmetic, not date arithmetic.
3. The `transient LocalDateTime maxDate` cache in `AppointmentImpl.getMaxEnd()` — is it actually a hot path warranting a cache, or migration leftover? Profile before deciding to drop.

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
| 6c — JMH performance benchmark for processBlocks/overlaps/getMaxEnd | not started — prerequisite for Phase 7 |
| 7 — processBlocks signature flip + benchmark validation | not started — **after Phase 6c lands the baseline** |
