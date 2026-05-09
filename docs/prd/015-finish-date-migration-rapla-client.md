# PRD 015: Finish Date → LocalDateTime migration in rapla-client

**Status:** in-progress
**Date:** 2026-05-09

## Goal

Drive `rapla-client` to zero compile errors so the reactor builds cleanly. After the recovery work in this session, **rapla-core compiles**, **rapla-client compiles**, and only **rapla-server has remaining errors** (handled by another session per user direction).

## Update 2026-05-09 (later in session)

`rapla-client` now compiles green. Semantic flips applied:
- `DateField` → `LocalDate` (date-only widget API)
- `TimeField` → `LocalTime` (time-of-day widget API)
- `DateModel` (package-private) → `LocalDate` internally; `RaplaCalendar` keeps `LocalDateTime` public API and converts at boundary
- `TimeModel` (package-private) → `LocalTime` internally; `RaplaTime` keeps `LocalDateTime` public API and converts at boundary
- `DateChooserPanel`: `today()` is `LocalDate`, callers add `.atStartOfDay()` at the LDT boundary

Reasoning: the widgets' semantic types match `java.time` directly, but the `DateChangeEvent` event wire and the public `getDate()/getTime()` APIs of `RaplaCalendar` and `RaplaTime` stay LDT to avoid touching all 18 listener consumers in this scope. A future Phase 5 can flip the event chain (`DateChangeEvent`, `DateChangeListener`, fireTimeChanged) to `LocalDate`/`LocalTime` if desired.

## Current state vs HEAD

```
246 files changed, 2,959 insertions, 4,619 deletions
1,087 Date-related diff hunks (intentional migration)
    1 non-Date diff hunk (intentional DateTools.toHour deletion, 0 callers)
```

100% of the diff is Date migration work. **All script collateral has been recovered** via the file-restore + re-script approach (see PRD 013).

## Error breakdown — 200 errors, all in rapla-client

| Type | Count | Pattern |
|---|---:|---|
| `cannot find symbol: method getTime()` | **63** | `x.getTime()` where `x` is now `LocalDateTime` (no `getTime()` on LDT) |
| `cannot find symbol: variable DateTools` | 4 | Missing `import org.rapla.components.util.DateTools;` |
| `cannot find symbol` (other: `format`, `toRaplaLocalDate`) | 2 | Stale references to removed members |
| `incompatible types: Date ↔ LocalDateTime` | 58 | Method signatures still take/return `Date` while callers pass/expect LDT (or vice versa) |
| `reference to getYCoord is ambiguous` | 2 | Two getYCoord overloads, one Date one LDT — collapse |
| `no suitable method found` | 2 | Same root: missing LDT overload |
| Other | ~70 | Cascades from the above |

## Per-file (top 15)

| Errors | File | Notes |
|---:|---|---|
| 36 | `ReservationControllerImpl.java` | Restored + scripted; ~30 `getTime()` cascades remain |
| 14 | `SwingCompactCalendar.java` | Date in calendar grid logic |
| 12 | `RaplaTime.java` | Custom Date widget — public API still Date-typed |
| 10 each | `SwingCompactWeekCalendar`, `TimeModel`, `TimeField`, `DateModel` | Same — Date widget internals |
| 8 | `AppointmentController.java`, `CalendarOption.java` | UI controllers calling LDT entities |
| 6 each | `SwingCompactDayCalendar`, `RaplaCalendarViewListener`, `DateField`, `PeriodChooser`, `CalendarContextMenuPresenter` | UI |
| 4 each | `SwingWeekCalendar`, `SwingMonthCalendar`, `IRowScale`, `SwingMonthView`, `DateRendererAdapter`, `ComplexTreeCellRenderer`, `AppointmentListEdit`, `PeriodInfoUI` | Various |
| 2 each | `CopyPluginMenu`, `ImportTemplateMenu`, `VariableRowScale` | Tail |

## Scope

`rapla-client/src/main/java/**` only. `rapla-core` already compiles. `rapla-server` and `rapla-app` are being handled by another session per user direction.

## Plan

Three phases, each with a focused script + a manual cleanup.

### Phase 1 — Fix `x.getTime()` on LocalDateTime (~63 errors)

Mechanical: every site of `x.getTime()` where `x` is `LocalDateTime` → `DateTools.toMilli(x)`. The existing `.agents/scripts/fix-ldt-orphans.py` already does this using compiler error positions; it's been verified to work but missed a follow-up pass when new `getTime()` errors surfaced after other fixes.

```bash
# Iterate until stable:
python3 .agents/scripts/fix-ldt-orphans.py    # parses mvn output, fixes pinpoint
mvn compile -q | grep -c "method getTime()"   # repeat until 0
```

Expected drop: 200 → ~140 errors.

### Phase 2 — Flip Swing date-widget APIs (Custom calendar components)

The custom Swing widgets `RaplaTime`, `RaplaCalendar`, `TimeField`, `DateField`, `TimeModel`, `DateModel`, `DateRendererAdapter` still expose `Date getValue()` / `setValue(Date)` APIs. Callers now pass `LocalDateTime`, causing the 58 "incompatible types" errors.

Each widget needs:
- `Date getDate()` → `LocalDateTime getDate()`
- `setDate(Date)` → `setDate(LocalDateTime)`
- `Date getValue()` / `setValue(Date)` → flip
- `DateChangeEvent.getDate()` → returns `LocalDateTime`
- Internal `java.util.Calendar` calls — preserve, but convert to LDT at the API boundary

Likely script-flippable for the bulk (signature changes), then ~30 min manual cleanup for internal Calendar bridges.

```bash
# After files are flipped, callers should resolve:
# - TimeField.setValue(LDT)  ← AppointmentController.startTimeField.setValue(start)
# - DateModel.getDate() returns LDT  ← SwingCompactCalendar uses returned value as LDT
```

Expected drop: 140 → ~30 errors.

### Phase 3 — Manual surgical fixes for residual

After phases 1 & 2, the remaining ~30 errors are hand-edits:
- Missing `import DateTools;` (4 sites)
- Two `getYCoord` overloads → keep one
- Stale references in commented or restored code paths
- Cross-module conversion sites (interface → impl boundary)

Estimated 20-30 min.

### Phase 4 — Verify

```bash
mvn compile           # full reactor compile, expect 0 errors
mvn test -pl rapla-core -Dtest=DateToolsLocalDateTimeTest  # smoke test
```

## Tests

| Phase | Test | When |
|---|---|---|
| 1 | After every iteration: `mvn -pl rapla-client -am compile -q` and count `getTime()` errors | Until 0 |
| 2 | Compile + manual smoke check on a calendar widget instantiation in `RaplaTimeTest` if it exists | After widget flip |
| 4 | Full reactor `mvn compile` clean | End of phase 3 |

## Recovery toolkit (already in place)

- `.agents/scripts/fix-ldt-orphans.py` — fixes `new LocalDateTime(long)` and `x.getTime()` orphans (uses compiler errors to locate)
- `.agents/scripts/replace-date-with-localdatetime.py` — bulk Date → LocalDateTime token replacement (state-machine-corrected for safe comment/string handling)
- `.agents/scripts/inspect-non-date-hunks.py` — verifies no non-Date collateral has been re-introduced
- `.agents/scripts/strip-localdatetime-bridges.py` — deletes the legacy `*AsLocalDateTime` bridge methods (already run)
- `.agents/scripts/recover-script-collateral.py` — re-inserts deleted lines (already run)

## Open Questions

1. **Should `DateChangeEvent.getDate()` flip to `LocalDate`/`LocalTime`?** — Currently kept as `LocalDateTime` to avoid touching the 18 listener consumers; the `RaplaCalendar`/`RaplaTime` public `getDate()`/`getTime()` APIs convert at the boundary. Phase 5 can flip the event chain end-to-end if a coordinated refactor is desired.
2. **`Permission.start/end` semantically date-only** — Permission entity API still typed as `LocalDateTime`; PermissionField uses `today.atStartOfDay()` boundary conversion. Phase 5 candidate: flip Permission.getStart/setStart to `LocalDate` (touches storage XML reader/writer too).
3. **Custom widget `Calendar` internals** — `RaplaTime`/`RaplaCalendar` etc. still use `java.util.Calendar` for date arithmetic. Keep as-is (UI rendering concern); a future PRD can replace with `java.time` arithmetic.

## Related PRDs

- **PRD 001-A** (`docs/prd/001-a-date-to-localdatetime.md`) — parent migration spec
- **PRD 013** (`docs/prd/013-date-script-collateral-damage.md`) — collateral damage recovery (now mostly resolved per "1 non-Date hunk" status)

## Definition of Done

- `mvn compile` succeeds across all modules with zero errors
- `mvn test -pl rapla-core` passes (sanity check on the converted entity layer)
- `git diff HEAD --shortstat` shows the migration delta with 0 non-Date hunks
- Update PRD 001-A status from `in-progress` to `done` and `git mv` to `docs/prd/done/`
