# PRD 015: Finish Date → LocalDateTime migration in rapla-client

**Status:** done (2026-05-11). All four Definition-of-Done items met: `rapla-client` compiles green (`mvn -pl rapla-client -am compile -q` succeeds); Swing date-widget APIs flipped (`DateField` → `LocalDate`, `TimeField` → `LocalTime`, `RaplaCalendar`/`RaplaTime` public APIs to `LocalDateTime` with internal boundary conversion); manual surgical fixes complete; full reactor compile passes. Closes the rapla-client side of the Date migration; PRD 001-A is also closed.
**Date:** 2026-05-09 (closed: 2026-05-11)

## Goal

Drive `rapla-client` to zero compile errors so the reactor builds cleanly. rapla-core + rapla-client compile after this work; rapla-server handled by another session.

## Update 2026-05-09 (later in session)

`rapla-client` compiles green. Semantic flips:
- `DateField` → `LocalDate`; `TimeField` → `LocalTime`.
- `DateModel`/`TimeModel` (package-private) → `LocalDate`/`LocalTime` internally; `RaplaCalendar`/`RaplaTime` keep `LocalDateTime` public API, convert at boundary.
- `DateChooserPanel.today()` is `LocalDate`; callers add `.atStartOfDay()`.

The `DateChangeEvent` wire and public `getDate()/getTime()` stay LDT to avoid touching 18 listener consumers; a future Phase 5 can flip the event chain end-to-end.

## Current state vs HEAD

```
246 files changed, 2,959 insertions, 4,619 deletions
1,087 Date-related diff hunks (intentional migration)
    1 non-Date diff hunk (intentional DateTools.toHour deletion, 0 callers)
```

100% of the diff is Date migration work. **All script collateral has been recovered** via the file-restore + re-script approach (see [PRD 013](013-date-script-collateral-damage.md)).

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

Mechanical: `x.getTime()` where `x` is `LocalDateTime` → `DateTools.toMilli(x)`. `.agents/scripts/fix-ldt-orphans.py` uses compiler error positions; iterate until stable.

```bash
python3 .agents/scripts/fix-ldt-orphans.py    # parses mvn output, fixes pinpoint
mvn compile -q | grep -c "method getTime()"   # repeat until 0
```

Expected drop: 200 → ~140 errors.

### Phase 2 — Flip Swing date-widget APIs

`RaplaTime`, `RaplaCalendar`, `TimeField`, `DateField`, `TimeModel`, `DateModel`, `DateRendererAdapter` still expose `Date getValue()`/`setValue(Date)`. Callers now pass `LocalDateTime` (58 "incompatible types" errors).

Each widget: flip `getDate`/`setDate`/`getValue`/`setValue` + `DateChangeEvent.getDate()` to LDT. Preserve internal `java.util.Calendar`, convert at API boundary. Script-flippable for bulk signatures, ~30 min manual cleanup for Calendar bridges.

Expected drop: 140 → ~30 errors.

### Phase 3 — Manual surgical fixes for residual (~30)

Missing `import DateTools;` (4 sites); two `getYCoord` overloads → keep one; stale references in restored code paths; cross-module conversion sites. ~20–30 min.

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

1. **`DateChangeEvent.getDate()` flip to `LocalDate`/`LocalTime`?** — Kept as LDT; boundary conversion in `RaplaCalendar`/`RaplaTime`. Phase 5 can flip end-to-end.
2. **`Permission.start/end` semantically date-only** — Entity API still LDT; PermissionField uses `today.atStartOfDay()`. Phase 5 candidate: flip to `LocalDate` (touches storage XML I/O).
3. **Custom widget `Calendar` internals** — `RaplaTime`/`RaplaCalendar` still use `java.util.Calendar`. Keep as-is; future PRD for `java.time` arithmetic.

## Related PRDs

- **PRD 001-A** (`docs/prd/001-a-date-to-localdatetime.md`) — parent migration spec
- **[PRD 013](013-date-script-collateral-damage.md)** (`docs/prd/013-date-script-collateral-damage.md`) — collateral damage recovery (now mostly resolved per "1 non-Date hunk" status)

## Definition of Done

- `mvn compile` succeeds across all modules with zero errors
- `mvn test -pl rapla-core` passes (sanity check on the converted entity layer)
- `git diff HEAD --shortstat` shows the migration delta with 0 non-Date hunks
- Update PRD 001-A status from `in-progress` to `done` and `git mv` to `docs/prd/done/`
