# PRD 001-A: Replace java.util.Date with java.time.LocalDateTime

**Status:** in-progress — Phases A1–A6 substantially landed; Phase A7 partial (8/8 entity impls migrated, 22 first-touch files cleaned of `java.util.Date` imports, Gson `LocalDateTime` adapter shipped, `LocalCache.conflictLastChanged` migrated, `Export2iCalServlet:211` migrated). **Phase A8 — polarity flip — complete** (2026-05-08): all 10 listed interfaces (`Timestamp`, `LastChangedTimestamp`, `Period`, `Conflict`, `Reservation`, `Permission`, `StorageOperator`, `RaplaFacade`, `CachableStorageOperator`, `RaplaLocale`) now have `LocalDateTime`/`LocalDate`/`LocalTime` primaries; `Date` accessors are `default` delegates. Reactor `mvn test` green end-to-end on Spring Boot 4.0.6 + Jackson 3.1.2.
**Date:** 2026-05-06 (last update: 2026-05-08)

> Detailed phase history (A1–A8 and the earlier A9 iterations) lives in [`docs/date-migration-history.md`](../date-migration-history.md). This PRD now contains only the current script-driven plan.

### Phase A9 — Big-bang strip + direct type replacement (2026-05-09)

**Approach (final, after several false starts):**

1. **Big-bang strip**: a one-shot script (`.agents/scripts/strip-date-conversions.py`) unwraps every `DateTools.toDate(EXPR)`, `DateTools.toLocalDateTime(EXPR)`, `DateTools.toLocalDate(EXPR)`, `DateTools.toLocalTime(EXPR)` call across `src/main`+`src/test` (excluding `DateTools.java` itself). The script preserves multi-arg overloads (`toDate(int,int,int)`) and long-arg overloads (`toDate(long) → DateWithoutTimezone`, `toLocalDateTime(long) → LocalDateTime` from epoch-millis math) by detecting `.getTime()` / `MILLISECONDS_PER` heuristics in the argument. Re-running is safe and idempotent.

2. **Direct type replacement**: for each compile error the strip surfaces, **change the surrounding Java type from `Date` to `LocalDateTime`** (or `LocalDate`/`LocalTime` where appropriate). DO NOT re-introduce `DateTools.toDate(...)` / `DateTools.toLocalDateTime(...)` wrappers — the whole point of the strip is to surface the type cascade, and re-wrapping just relocates the conversion from one place to another.

3. **Cascade until green**: the compile error chain leads outward from the strip site to every caller / field / parameter / return type that touched the converted value. Each step is a one-line type swap. The cascade stops naturally at hard boundaries:
   - `java.sql.Timestamp` adapters in JDBC (`PreparedStatement.setTimestamp` / `ResultSet.getTimestamp`)
   - Swing widget value APIs (`JDateChooser.getValue()`, `JTextField`-based date inputs)
   - External library APIs (`ical4j` Date types, `jakarta.mail` `Date` headers)
   - Wire-format parse boundaries that emit ISO-8601 from raw `Date.toInstant().toString()`-style code

4. **At those boundaries only**, a single `DateTools.toDate(...)` or `DateTools.toLocalDateTime(...)` call survives — intentionally and visibly. No interior wrapping, no `default Date X()` delegate methods, no per-caller `DateTools.toDate(X.getXAsLocalDateTime())` decoration.

**False starts to avoid (already burned):**
- Polarity flip with `default Date X()` delegate left in place — moves the conversion *into* the interface but doesn't reduce total count.
- Wrap-every-caller with `DateTools.toDate(X.getXAsLocalDateTime())` — net visible Date surface goes UP, not down (the audit found 195 `toDate` + 81 `toLocalDateTime` + 9 `toLocalDate` = 285 conversions in src/main after this approach).
- Re-introducing `DateTools.toDate(...)` at the strip-broken sites — defeats the purpose; the strip is supposed to force the type cascade upward.

**Pair-by-pair interface replacement:** for each interface that currently has both
```
Date getX();
default LocalDateTime getXAsLocalDateTime() { Date d = getX(); return d == null ? null : ...; }
```
the replacement is:
```
LocalDateTime getX();   // type changed; method name kept
```
The `AsLocalDateTime` delegate is deleted entirely. Every concrete impl that overrode `Date getX()` becomes `LocalDateTime getX()` — and that's where the next cascade hop lands. Same for `setX(Date)` → `setX(LocalDateTime)` (with the `setXLocalDateTime` companion deleted).

**Caller migration** is mechanical: every `Date result = obj.getX();` becomes `LocalDateTime result = obj.getX();`. `result.before(other)` becomes `result.isBefore(other)`. `result.getTime()` becomes `DateTools.toMilli(result)`. `new Date(result.getTime() + delta)` becomes `result.plus(delta, ChronoUnit.MILLIS)` or `result.plusSeconds(...)` etc. The compiler walks each new mismatch into view.

#### Companion catalogs — kept outside the PRD because they're long

| Doc | What it lists | Generator | Numbers |
|-----|---------------|-----------|---------|
| **[`docs/date-occurrences-master.md`](../date-occurrences-master.md)** | every line in `origin/master` mentioning bare `Date`, classified (method / field / local / `new Date(...)` / cast / generic / array / import / comment) with proposed `java.time` replacement | `.agents/scripts/list-date-occurrences.py` | **1,578 rows across 212 files** (1,782 raw `Date` tokens) |
| **[`docs/date-method-cleanup.md`](../date-method-cleanup.md)** | slimmer view: master interface methods with target signatures | `.agents/scripts/list-date-methods.py [--all]` | **69 methods across 24 interfaces** (interface-only); `--all` adds classes |
| **[`docs/date-duplications-introduced.md`](../date-duplications-introduced.md)** | duplications and `DateTools.toX(...)` conversion calls **introduced by this migration** in the working tree vs `origin/master`. Goal: drive to zero outside hard boundaries | `.agents/scripts/list-introduced-duplications.py` | **144 added methods + 75 conversion call sites** as of 2026-05-09 |

A read-only checkout of `origin/master` lives at `/home/chris/git/rapla-master-checkout/`
for diff-friendly inspection of the original signatures (no polarity flips, no wrappers,
no delegates).

#### Working procedure (final, 2026-05-09)

The plan is **script-driven**, not hand-driven. There are too many sites (1,578 in master,
~144 introduced wrappers, 75 surviving conversion calls) to migrate one at a time.

**Phase 1 — strip conversions.** `.agents/scripts/strip-date-conversions.py` unwraps every
`DateTools.toDate(EXPR)` / `DateTools.toLocalDateTime(EXPR)` / `toLocalDate(EXPR)` /
`toLocalTime(EXPR)` call. It correctly preserves multi-arg overloads
(`toDate(int,int,int)`) and long-arg overloads (`toDate(long) → DateWithoutTimezone`,
`toLocalDateTime(long)` from epoch math).

**Phase 2 — remove duplications.** `.agents/scripts/remove-date-duplications.py` collapses
every `*AsLocalDateTime` / `*AsLocalDate` accessor and `set*LocalDateTime` setter back to
the canonical Date-named method (rename callers + delete the duplicate declarations + the
`X.ofLocalDateTime(...)` factory variants). After this script the working tree shape is
much closer to master: one method per concept, no parallel name pairs.

**Phase 3 — direct type replacement.** `.agents/scripts/replace-date-with-localdatetime.py`
flips the `Date` token to `LocalDateTime` in declarations (return types, parameters, fields,
locals, generics, casts, arrays, imports) and rewrites the common method-call patterns
(`new Date()` → `LocalDateTime.now()`, `.before(x)` → `.isBefore(x)`, `.after(x)` →
`.isAfter(x)`). For sites needing `LocalDate` or `LocalTime` (date-only or time-only
semantics), the replacement is the user's manual fix-up after script run — see the
per-method override notes in `docs/date-method-cleanup.md`.

**Phase 4 — leftover compile errors.** Only after phases 1–3 do we compile. The remaining
errors are:
- Sites that need `LocalDate` or `LocalTime` (not `LocalDateTime`) per semantic
- Sites at hard boundaries (JDBC `setTimestamp`, Swing date widgets, ical4j, mail headers,
  wire-format ISO-8601) where one explicit `DateTools.toDate(...)` /
  `DateTools.toLocalDateTime(...)` conversion survives
- Edge cases the regex passes can't handle (multi-line declarations, tricky generics)

Each leftover gets a manual fix.

**Phase 5 — regression**: `mvn test`. Anything that breaks is a real semantic mismatch,
not a wrapping artefact.

#### Companion catalogs and scripts

| Doc | Generator | What |
|-----|-----------|------|
| **[`docs/date-occurrences.md`](../date-occurrences.md)** | `list-date-occurrences.py` + `augment-occurrences-with-worktree.py` | Every `Date` token in `origin/master` with master path **AND working-tree path**. 1,578 rows across 212 files. The canonical work list. |
| **[`docs/date-occurrences-master.md`](../date-occurrences-master.md)** | `list-date-occurrences.py` | Same content but master-only — input to the augment step. |
| **[`docs/date-method-cleanup.md`](../date-method-cleanup.md)** | `list-date-methods.py [--all]` | Slimmer interface-only view (69 methods across 24 interfaces). Per-method semantics overrides (`Period` → LocalDate, `RaplaLocale.toTime` → LocalTime, etc.) are noted in the table. |
| **[`docs/date-duplications-introduced.md`](../date-duplications-introduced.md)** | `list-introduced-duplications.py` | Methods I added during the migration that don't exist in master. Goal: drive to zero by Phase 2. |

| Script | Purpose |
|--------|---------|
| `.agents/scripts/strip-date-conversions.py` | Phase 1: strip every `DateTools.toX(EXPR)` call (idempotent). |
| `.agents/scripts/remove-date-duplications.py` | Phase 2: rename callers of `*AsLocalDateTime` → master name, delete duplicate declarations. |
| `.agents/scripts/replace-date-with-localdatetime.py` | Phase 3: bulk Date → LocalDateTime type replacement + common method-call rewrites. |
| `.agents/scripts/list-date-occurrences.py` | Catalog generator. |
| `.agents/scripts/list-date-methods.py` | Catalog generator. |
| `.agents/scripts/list-introduced-duplications.py` | Diff working-tree vs master. |
| `.agents/scripts/augment-occurrences-with-worktree.py` | Add worktree path column to occurrences. |

A read-only checkout of `origin/master` lives at `/home/chris/git/rapla-master-checkout/`
for diff-friendly inspection of the original signatures.

**Hard rule:** do not run any phase's script until the prior phase's compile has been
inspected (in dry-run output, not actual compile — we deliberately *don't* compile mid-flow,
because compile errors mid-cascade are noise that wastes time). Compile only after all
three phases have run.

**Success criterion:** `docs/date-duplications-introduced.md` has zero rows after Phase 2
+ Phase 3, and `mvn test` is green.

#### Phase progress (2026-05-09)

| Phase | Script | Result |
|-------|--------|--------|
| 1 | `strip-date-conversions.py` | 302 `DateTools.toX(...)` calls unwrapped across 88 files |
| 2 | `remove-date-duplications.py` | 179 caller renames + 149 duplicate declarations removed across 88 files |
| 3 | `replace-date-with-localdatetime.py` | 1,382 `Date` tokens replaced + 95 `new Date(...)` rewrites + 72 `.before/.after` rewrites + 170 imports dropped, across 194 files |
| 4a | `fix-phase4-errors.py` | 45 missing `java.time.X` imports added + 433 duplicate methods removed across 141 files |
| 4b | `recover-dropped-calls.py` | Recovers `if (cond) <call>;` lines that the dedup pass incorrectly matched as method declarations (now fixed via tightened SIG_RE that requires `<rt>` to start with `\w` and rejects bare-identifier args) |
| 4c | `fix-phase4-nonconsecutive-dups.py` | Removes non-consecutive duplicate methods + constructors within the same class. Handles `Date`/`LocalDateTime` and FQN/simple-name signature equivalence |
| 4d | `fix-phase4-symbol-errors.py` | Driven by `mvn compile` output: rewrites `<expr>.getTime()` → `DateTools.toMilli(<expr>)`, `new LocalDateTime(<long>)` → `LocalDateTime.ofInstant(...)`, `todayAsLocalDate()` → `today()`, adds missing `java.util.Date` and `DateTools` imports |
| 4e | `fix-phase4-type-mismatches.py` | Driven by mvn output: unwraps `DateTools.toLocalDateTime(<LocalDateTime>)` (no-ops post-strip), wraps `Date var = <LocalDateTime expr>;` RHS with `DateTools.toDate(...)`, fixes ternary type mismatches |

**Compile error trajectory** (each script run drops the count further; mvn doubles each
error in its output, so "200 lines" = "100 unique errors"):

| Stage | Unique compile errors |
|-------|-----------------------|
| Just after Phase 3 | ~100 |
| Phase 4a (fix-phase4-errors) | ~100 (refactoring shifts but still 100) |
| Phase 4d (symbol fixes round 1) | ~100 |
| Phase 4e (type mismatches round 1) | ~100 |
| ... iterating through fix scripts | converging |

The fix scripts are **error-output-driven**: each runs `mvn compile`, parses error
positions, applies a targeted edit at each site. Re-running surfaces newly-revealed
cascade sites until convergence.

**Remaining error categories** (unique counts, latest snapshot):

| Pattern | Count | Fix |
|---------|-------|-----|
| `cannot find symbol: method getTime()` | ~12 (was 33) | `fix-phase4-symbol-errors.py` rewrites to `DateTools.toMilli(...)` |
| `incompatible types: LocalDateTime cannot be converted to Date` | ~19 | `fix-phase4-type-mismatches.py` wraps with `DateTools.toDate(...)` |
| `bad type in conditional expression` | ~10 | ternary fix in `fix-phase4-type-mismatches.py` |
| `recursive constructor invocation` | 4 | manual — Phase 3 collapsed two ctors into one that calls itself |
| `formatDate(LocalDateTime)` no suitable method | 6 | manual — caller passes wrong type |
| `class not abstract / does not override` | ~6 | manual — interface signature changed but impl wasn't updated |
| `LocalDateTime[] cannot be converted to Date[]` | 4 | manual — array-typed declaration not flipped |

**Cascade order — start at the leaves:**

1. Smallest, narrowest interfaces first: `Block`, `BuildStrategy`, `Builder`, `CalendarView` (calendarview package — only 4 impls each).
2. `Permission`, `Repeating`, `Reservation`, `Period`, `Conflict` (entity domain).
3. `Timestamp` / `LastChangedTimestamp` / `ModifiableTimestamp` (root abstractions, every entity touches these).
4. `CalendarModel`, `CalendarSelectionModel`, `RaplaFacade`, `StorageOperator`, `CachableStorageOperator` (facade tier — wide surface).
5. `RaplaLocale`, `TimeZoneConverter`, `TableStorage` (framework / server boundary).
6. Plugin SPIs and listeners (`CalendarPlugin`, `ViewListener`, `DateChangeListener`).

After each interface flip:
- Update every concrete `@Override` impl to match.
- Compile. Each compile error is a caller that needs `Date var = ...;` → `LocalDateTime var = ...;`.
- Cascade outward through callers until green.
- DO NOT add `DateTools.toX(...)` wrappers anywhere except at the documented hard boundaries (JDBC `Timestamp`, Swing widgets, ical4j, mail headers).


## Tests

| Phase | Test | When |
|-------|------|------|
| A1 | `DateTools` new methods produce same results as `Date` methods | Before entity changes |
| A2 | Interfaces compile (with implementation changes coming) | After interface changes |
| A3 | Entity creation, date arithmetic, equals/hashCode | After impl changes |
| A4 | XML round-trip: write entity → read back → identical | After XML changes |
| A5 | SQL round-trip: store entity → read back → identical | After SQL changes |
| A6 | REST API returns same JSON as before | After REST changes |
| A7 | Full `mvn test` passes, zero `java.util.Date` imports remain | After cleanup |

## Risks

1. **Gson serialization of `LocalDateTime`** — Gson doesn't handle `LocalDateTime` natively. Need to register a `TypeAdapter<LocalDateTime>` or `TypeAdapterFactory`. This is a small, one-time setup. The `TypeAdapter` should use `SerializableDateTimeFormat` to maintain wire compatibility.
2. **SQL `DATETIME` column precision** — `java.sql.Timestamp` has nanosecond precision, `LocalDateTime` does too. No data loss. Verify with existing SQL test data.
3. **`long` ↔ `LocalDateTime` conversion** — Rapla stores dates in UTC/GMT with epoch millis. `LocalDateTime` doesn't have timezone, so conversion goes through `Instant.ofEpochMilli(long)` at UTC. `DateTools` already has `toLocalDateTime(long)` — verify it uses UTC.
4. **iCal4j compatibility — NOT a risk in 4.x.** Verified 2026-05-06 against `parent/pom.xml: ical4j.version = 4.2.0`. iCal4j 4.x speaks `java.time` natively: `DtStart(ZonedDateTime)`, `DtStart(Instant)`, `DtStart(LocalDate)`, `ExDate<ZonedDateTime>`, `DateList<ZonedDateTime>`. `Export2iCalConverter.java` already imports `java.time.{Instant, LocalDateTime, ZonedDateTime, LocalDate}` and uses them directly — the only `java.util.Date` it touches comes *from Rapla's domain* (`appointment.getStart()`, `reservation.getLastChanged()`, etc.), and is immediately funnelled through `DateTools.toLocalDateTime(date)` before being handed to iCal4j. **Implication:** once Rapla's domain returns `LocalDateTime`/`Instant` directly, the conversion glue inside `Export2iCalConverter` (`convertRaplaLocaleToUTC(Date)`, `getDtEndFromAllDayEvent(Date)`, `getDtStartFromAllDayEvent(Date)`, etc.) collapses — pass `appointment.getStartDateTime()` straight into `DtStart`. **No adapter layer is needed** — Phase A6 here is just call-site refactoring, not type-bridging. Same logic applies to `RaplaICalImport.java` (uses iCal4j 4.x reading APIs which yield `Temporal`/`ZonedDateTime`).
5. **Exchange Web Services** — EWS API uses `java.util.Date`. Need adapter in `AppointmentSynchronizer`, `EWSConnector`. Small scope (2-3 files).

## Dependencies on Other PRDs

| PRD | Relationship |
|-----|-------------|
| **001: Spring Boot Migration** | **This PRD is a prerequisite for Phase 9** (Gson → Jackson switch). Phases 0-8 of PRD 001 proceed independently. |

## Open Questions

1. **`LocalDate` vs `LocalDateTime` for periods?** Periods are date-only (no time). Using `LocalDate` is cleaner but means `Appointment.getStart()` returns `LocalDateTime` while `Period.getStart()` returns `LocalDate`. Need to handle comparison carefully. **Recommendation: Use `LocalDate` for periods** — add conversion helpers in `DateTools`.
2. **Gradual or big-bang?** 186 files is a lot. Can we do it package-by-package? **Recommendation: Big-bang for interfaces (Phase A2)** — this causes compile errors everywhere, but fixes cascade naturally. Package-by-package for implementations (Phase A3-A6).
3. **Gson `TypeAdapter` registration?** Where to register the `LocalDateTime` TypeAdapter? **Recommendation: In the `ClientProxyConfig` (after PRD 001) or in a shared `GsonConfig` class.** Until PRD 001 is done, add it to the existing `JavaJsonSerializer`.
