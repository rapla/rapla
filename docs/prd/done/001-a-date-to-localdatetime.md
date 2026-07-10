# PRD 001-A: Replace java.util.Date with java.time.LocalDateTime

**Status:** done (2026-05-11). All phases A1–A9 landed. `java.util.Date` is gone from `rapla-core` and `rapla-server` `src/main`; the three remaining imports in `rapla-client` are Swing widget boundaries (`DateField`, `TimeField`, `ExternalEventImportPanel`) and intentional. All 10 listed interfaces (`Timestamp`, `LastChangedTimestamp`, `Period`, `Conflict`, `Reservation`, `Permission`, `StorageOperator`, `RaplaFacade`, `CachableStorageOperator`, `RaplaLocale`) carry `LocalDateTime`/`LocalDate`/`LocalTime` primaries. Reactor `mvn test` green on Spring Boot 4.0.6 + Jackson 3.x. Follow-up work on long-millis arithmetic inside `AppointmentImpl` / `RepeatingImpl` tracked under [PRD 014](014-appointment-long-to-java-time.md) (also done).
**Date:** 2026-05-06 (closed: 2026-05-11)

> Detailed phase history (A1–A8 and earlier A9 iterations) lives in [`docs/date-migration-history.md`](../date-migration-history.md). This PRD now contains only the script-driven plan.

### Phase A9 — Big-bang strip + direct type replacement (2026-05-09)

**Approach (final, after several false starts):**

1. **Big-bang strip**: `.agents/scripts/strip-date-conversions.py` unwraps every `DateTools.toDate(EXPR)`, `DateTools.toLocalDateTime(EXPR)`, `DateTools.toLocalDate(EXPR)`, `DateTools.toLocalTime(EXPR)` call across `src/main`+`src/test` (excluding `DateTools.java`). Preserves multi-arg overloads (`toDate(int,int,int)`) and long-arg overloads (`toDate(long) → DateWithoutTimezone`, `toLocalDateTime(long) → LocalDateTime` from epoch-millis math) by detecting `.getTime()` / `MILLISECONDS_PER` heuristics. Idempotent.

2. **Direct type replacement**: for each compile error the strip surfaces, change the surrounding Java type from `Date` to `LocalDateTime` (or `LocalDate`/`LocalTime`). DO NOT re-introduce `DateTools.toDate(...)` / `DateTools.toLocalDateTime(...)` wrappers — the whole point of the strip is to surface the type cascade.

3. **Cascade until green**: each compile error chain leads outward to every caller / field / parameter / return type. Each step is a one-line type swap. The cascade stops naturally at hard boundaries:
   - `java.sql.Timestamp` in JDBC (`PreparedStatement.setTimestamp` / `ResultSet.getTimestamp`)
   - Swing widget APIs (`JDateChooser.getValue()`, `JTextField` date inputs)
   - External libraries (ical4j `Date`, `jakarta.mail` `Date` headers)
   - Wire-format parse boundaries emitting ISO-8601 from raw `Date.toInstant().toString()`-style code

4. **At those boundaries only**, a single `DateTools.toDate(...)` / `toLocalDateTime(...)` survives — intentionally and visibly. No interior wrapping, no `default Date X()` delegate methods, no per-caller `DateTools.toDate(X.getXAsLocalDateTime())` decoration.

**False starts to avoid (already burned):**
- Polarity flip with `default Date X()` delegate left in place — moves conversion into the interface but doesn't reduce total count.
- Wrap-every-caller with `DateTools.toDate(X.getXAsLocalDateTime())` — net visible Date surface goes UP (audit found 285 conversions in src/main).
- Re-introducing `DateTools.toDate(...)` at strip-broken sites — defeats the purpose.

**Pair-by-pair interface replacement:** for each interface with both `Date getX()` and `default LocalDateTime getXAsLocalDateTime()`, replace with `LocalDateTime getX()` (type changed; method name kept). Delete the `AsLocalDateTime` delegate. Every `@Override Date getX()` becomes `LocalDateTime getX()` — next cascade hop. Same for `setX(Date)` → `setX(LocalDateTime)` (companion `setXLocalDateTime` deleted).

**Caller migration** is mechanical: `Date result = obj.getX();` → `LocalDateTime result = obj.getX();`; `result.before(other)` → `result.isBefore(other)`; `result.getTime()` → `DateTools.toMilli(result)`; `new Date(result.getTime() + delta)` → `result.plus(delta, ChronoUnit.MILLIS)` or `result.plusSeconds(...)`.

#### Companion catalogs and scripts

A read-only checkout of `origin/master` lives at `/home/chris/git/rapla-master-checkout/` for diff-friendly inspection.

| Doc | Generator | What |
|-----|-----------|------|
| **[`docs/date-occurrences.md`](../date-occurrences.md)** | `list-date-occurrences.py` + `augment-occurrences-with-worktree.py` | Every `Date` token in `origin/master` with master path AND working-tree path. 1,578 rows across 212 files. Canonical work list. |
| **[`docs/date-occurrences-master.md`](../date-occurrences-master.md)** | `list-date-occurrences.py` | Same content, master-only — input to augment step. |
| **[`docs/date-method-cleanup.md`](../date-method-cleanup.md)** | `list-date-methods.py [--all]` | Slimmer interface-only (69 methods across 24 interfaces). Per-method semantics overrides (`Period` → LocalDate, `RaplaLocale.toTime` → LocalTime). |
| **[`docs/date-duplications-introduced.md`](../date-duplications-introduced.md)** | `list-introduced-duplications.py` | Methods added during migration that don't exist in master. Goal: drive to zero by Phase 2. |

| Script | Purpose |
|--------|---------|
| `strip-date-conversions.py` | Phase 1: strip every `DateTools.toX(EXPR)` call (idempotent). |
| `remove-date-duplications.py` | Phase 2: rename callers of `*AsLocalDateTime` → master name, delete duplicate declarations. |
| `replace-date-with-localdatetime.py` | Phase 3: bulk Date → LocalDateTime type replacement + common method-call rewrites. |
| `list-date-occurrences.py`, `list-date-methods.py`, `list-introduced-duplications.py`, `augment-occurrences-with-worktree.py` | Catalog generators. |

#### Working procedure

Script-driven, not hand-driven — too many sites (1,578 in master, ~144 introduced wrappers, 75 surviving conversion calls).

**Phase 1** — `strip-date-conversions.py` unwraps `DateTools.toX(EXPR)` calls, preserving multi-arg and long-arg overloads.

**Phase 2** — `remove-date-duplications.py` collapses `*AsLocalDateTime` / `*AsLocalDate` accessors and `set*LocalDateTime` setters back to the canonical Date-named method (rename callers + delete duplicate declarations + `X.ofLocalDateTime(...)` factory variants). After this, working tree is much closer to master: one method per concept.

**Phase 3** — `replace-date-with-localdatetime.py` flips `Date` token to `LocalDateTime` in declarations (return types, params, fields, locals, generics, casts, arrays, imports) and rewrites common patterns (`new Date()` → `LocalDateTime.now()`, `.before(x)` → `.isBefore(x)`, `.after(x)` → `.isAfter(x)`). Sites needing `LocalDate`/`LocalTime` get manual fix-up — see `docs/date-method-cleanup.md`.

**Phase 4** — leftover compile errors. Only compile after phases 1–3. Remaining errors are sites needing `LocalDate`/`LocalTime`, hard boundaries (JDBC `setTimestamp`, Swing widgets, ical4j, mail, wire-format ISO-8601) where one explicit conversion survives, and edge cases regex can't handle (multi-line declarations, tricky generics).

**Phase 5** — regression: `mvn test`. Anything broken is a real semantic mismatch.

**Hard rule:** do not run any phase's script until the prior phase's dry-run output has been inspected. Compile only after all three phases. Mid-flow compile errors are noise that wastes time.

**Success criterion:** `docs/date-duplications-introduced.md` has zero rows after Phase 2 + Phase 3, and `mvn test` green.

#### Phase progress (2026-05-09)

| Phase | Script | Result |
|-------|--------|--------|
| 1 | `strip-date-conversions.py` | 302 `DateTools.toX(...)` calls unwrapped across 88 files |
| 2 | `remove-date-duplications.py` | 179 caller renames + 149 duplicate declarations removed across 88 files |
| 3 | `replace-date-with-localdatetime.py` | 1,382 `Date` tokens replaced + 95 `new Date(...)` rewrites + 72 `.before/.after` rewrites + 170 imports dropped, across 194 files |
| 4a | `fix-phase4-errors.py` | 45 missing `java.time.X` imports added + 433 duplicate methods removed across 141 files |
| 4b | `recover-dropped-calls.py` | Recovers `if (cond) <call>;` lines that the dedup pass incorrectly matched as method declarations (fixed via tightened SIG_RE) |
| 4c | `fix-phase4-nonconsecutive-dups.py` | Removes non-consecutive duplicate methods + constructors. Handles Date/LocalDateTime and FQN/simple-name signature equivalence |
| 4d | `fix-phase4-symbol-errors.py` | Driven by `mvn compile`: `<expr>.getTime()` → `DateTools.toMilli(<expr>)`, `new LocalDateTime(<long>)` → `LocalDateTime.ofInstant(...)`, `todayAsLocalDate()` → `today()`, missing imports |
| 4e | `fix-phase4-type-mismatches.py` | Driven by mvn: unwraps `DateTools.toLocalDateTime(<LocalDateTime>)` (no-ops post-strip), wraps `Date var = <LocalDateTime expr>;` RHS, fixes ternary type mismatches |

Fix scripts are error-output-driven: each parses `mvn compile` error positions and applies targeted edits. Re-running surfaces newly-revealed cascade sites until convergence.

**Remaining error categories** (unique counts, latest snapshot):

| Pattern | Count | Fix |
|---------|-------|-----|
| `cannot find symbol: method getTime()` | ~12 (was 33) | `fix-phase4-symbol-errors.py` → `DateTools.toMilli(...)` |
| `incompatible types: LocalDateTime cannot be converted to Date` | ~19 | `fix-phase4-type-mismatches.py` wraps `DateTools.toDate(...)` |
| `bad type in conditional expression` | ~10 | ternary fix in same script |
| `recursive constructor invocation` | 4 | manual — Phase 3 collapsed two ctors into one calling itself |
| `formatDate(LocalDateTime)` no suitable method | 6 | manual — caller passes wrong type |
| `class not abstract / does not override` | ~6 | manual — interface changed, impl wasn't |
| `LocalDateTime[] cannot be converted to Date[]` | 4 | manual — array-typed declaration not flipped |

**Cascade order — start at the leaves:**

1. Smallest interfaces first: `Block`, `BuildStrategy`, `Builder`, `CalendarView` (calendarview, ~4 impls each).
2. `Permission`, `Repeating`, `Reservation`, `Period`, `Conflict` (entity domain).
3. `Timestamp` / `LastChangedTimestamp` / `ModifiableTimestamp` (root abstractions).
4. `CalendarModel`, `CalendarSelectionModel`, `RaplaFacade`, `StorageOperator`, `CachableStorageOperator` (facade tier).
5. `RaplaLocale`, `TimeZoneConverter`, `TableStorage` (framework / server boundary).
6. Plugin SPIs and listeners (`CalendarPlugin`, `ViewListener`, `DateChangeListener`).

After each interface flip: update every `@Override`, compile, cascade through callers until green. DO NOT add `DateTools.toX(...)` wrappers except at documented hard boundaries.

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

1. **Gson serialization of `LocalDateTime`** — Gson doesn't handle `LocalDateTime` natively. Register a `TypeAdapter<LocalDateTime>` / `TypeAdapterFactory`. Use `SerializableDateTimeFormat` for wire compatibility.
2. **SQL `DATETIME` column precision** — both `java.sql.Timestamp` and `LocalDateTime` have nanosecond precision. No data loss. Verify with existing test data.
3. **`long` ↔ `LocalDateTime` conversion** — Rapla stores epoch millis. Conversion through `Instant.ofEpochMilli(long)` at UTC. `DateTools.toLocalDateTime(long)` exists — verify UTC.
4. **iCal4j compatibility — NOT a risk in 4.x.** Verified 2026-05-06 against `ical4j.version = 4.2.0`. iCal4j 4.x speaks `java.time` natively (`DtStart(ZonedDateTime)`, `DtStart(Instant)`, `DtStart(LocalDate)`, `ExDate<ZonedDateTime>`, `DateList<ZonedDateTime>`). `Export2iCalConverter.java` already imports `java.time.*` and uses them; the only `java.util.Date` it touches comes from Rapla's domain and is funnelled through `DateTools.toLocalDateTime(date)`. **Implication:** once Rapla returns `LocalDateTime`/`Instant` directly, conversion glue inside `Export2iCalConverter` collapses. **No adapter layer needed** — Phase A6 is call-site refactoring, not type-bridging. Same for `RaplaICalImport.java`.
5. **Exchange Web Services** — EWS API uses `java.util.Date`. Adapter needed in `AppointmentSynchronizer`, `EWSConnector`. Small scope (2–3 files).

## Dependencies on Other PRDs

| PRD | Relationship |
|-----|-------------|
| **001: Spring Boot Migration** | **Prerequisite for Phase 9** (Gson → Jackson switch). Phases 0–8 proceed independently. |

## Open Questions

1. **`LocalDate` vs `LocalDateTime` for periods?** Periods are date-only. Using `LocalDate` is cleaner but means `Appointment.getStart()` is `LocalDateTime` while `Period.getStart()` is `LocalDate` — handle comparison carefully. **Use `LocalDate` for periods** with conversion helpers in `DateTools`.
2. **Gradual or big-bang?** 186 files. **Big-bang for interfaces (Phase A2)** — cascades naturally. Package-by-package for implementations (A3–A6).
3. **Gson `TypeAdapter` registration?** **In `ClientProxyConfig` (after PRD 001) or shared `GsonConfig` class.** Until PRD 001 is done, add to existing `JavaJsonSerializer`.
