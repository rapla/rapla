# PRD 013: Recover collateral damage from Date migration scripts

**Status:** done (2026-05-11). The ~1,068 lines of collateral damage from the PRD 001-A Phase 4 scripts were recovered via the file-reset + re-script approach. [PRD 015](015-finish-date-migration-rapla-client.md) records the final state: "100 % of the diff is Date migration work. All script collateral has been recovered." Reactor compiles green and `mvn test` runs end-to-end.
**Date:** 2026-05-09 (closed: 2026-05-11)

## Goal

The Date → LocalDateTime migration scripts (PRD 001-A Phase 4 — `strip-date-conversions.py`, `remove-date-duplications.py`, `replace-date-with-localdatetime.py`, `fix-phase4-errors.py`) deleted **~1,068 lines across 148 files** that have nothing to do with the Date migration. Most are real code: method calls, anonymous-class command definitions, JDBC helper calls, listener-pattern boilerplate. They were dropped because the dedup-pass regex incorrectly matched code-statement lines as method declarations and removed lines between adjacent control-flow keywords.

This PRD catalogs deletions and tracks recovery. Baseline is **`HEAD`** (the multimodule shift), not `origin/master`. The scripts ran *after* the multimodule shift was committed, so all damage is in the uncommitted working tree and visible via `git diff HEAD`.

## Reproduction

```bash
python3 .agents/scripts/find-script-collateral-vs-head.py 2>/dev/null > /tmp/collateral.txt
```

Output: per-file removed lines, filtered to exclude tokens clearly part of Date cleanup (`Date`, `LocalDateTime`, `toMilli`, `cutDate`, `before`/`after`), whitespace, comments, `import` lines.

## Scope

**148 files** have non-Date deletions. Top files by deletion count:

| Lines | File | Pattern |
|---:|---|---|
| 188 | `rapla-core/.../scheduler/sync/UtilConcurrentCommandScheduler.java` | Entire constructor body + ThreadFactory + pool setup gone |
| 64 | `rapla-client/.../client/internal/ReservationControllerImpl.java` | Anonymous `CommandUndo` impls (`execute()`, `undo()`, `getCommandoName()`) for ~10 commands |
| 63 | `rapla-client/.../swing/internal/edit/reservation/AppointmentListEdit.java` | Same pattern: `CommandUndo` inner classes for appointment add/remove/split |
| 49 | `rapla-core/.../components/util/DateTools.java` | Internals of `cutDate(Date)`, `modifyDate(...)` — most intended (gateway sweep this session) |
| 37 | `rapla-server/.../storage/dbsql/RaplaSQL.java` | `setTimestampLocalDateTime`/`setDateLocalDateTime`/`getDateAsLocalDateTime` JDBC helper calls |
| 30 | `rapla-server/.../storage/impl/server/LocalAbstractCachableOperator.java` | Method calls dropped between control-flow lines (`removeOldConflicts()`, `removeConflictsFromCache()`, `storeAndRemove(...)`) |
| 24 | `rapla-client/.../swing/internal/edit/ClassifiableFilterEdit.java` | Listener-pattern boilerplate: `addChangeListener`, `fireFilterChanged`, `getChangeListeners` |
| 24 | `rapla-client/.../swing/internal/edit/reservation/AllocatableSelection.java` | UI binding callbacks (`updateBindings`, `fireAllocationsChanged`, `updateButtons`) |
| 23 | `rapla-client/.../swing/internal/edit/reservation/AppointmentController.java` | Date-comparison branches and event handlers (`mapToAppointment`, `doChanges`, `resetWeekdays`) |
| ... | ~138 more files with 1–22 deletions each | mix |

## Damage patterns

### Pattern 1 — Anonymous-class `CommandUndo` blocks dropped

Most-affected pattern, found in `ReservationControllerImpl`, `AppointmentListEdit`, `AppointmentController`, `AllocatableSelection`, etc. The dedup-pass regex matched the opening `public Promise<Void> execute() {` of an anonymous-class method as a "duplicate method declaration" and dropped lines until a matching `}`.

**Example** (`ReservationControllerImpl.java`, missing in working tree vs `HEAD`):
```java
public Promise<Void> execute() {
    return getModifiedReservationForExecute().thenCompose(mutableReservation->
            checkAndDispatch(Collections.singleton(mutableReservation), Collections.emptyList(),
                             firstTimeCall, sourceComponent)).thenRun(()->firstTimeCall = false);
}
public Promise<Void> undo() {
    return getModifiedReservationForUndo().thenCompose(mutableReservation->
                    checkAndDispatch(Collections.singleton(mutableReservation),
                                     Collections.emptyList(), false, sourceComponent));
}
public String getCommandoName() {
    return getI18n().getString("exchange_allocatables");
}
```

All three method bodies inside the anonymous `CommandUndo` were stripped. Without these, undo/redo for several user actions (exchange allocatables, move appointment, copy reservation, save reservation) is broken.

### Pattern 2 — Method-call statements dropped between control-flow lines

The dedup-pass regex was anchored on patterns matching both method declarations and method *calls* with identical-looking syntax. When two method calls appeared on consecutive lines after a control-flow keyword, the regex dropped one as "duplicate."

**Example** (`LocalAbstractCachableOperator.java`):
```java
removeOldConflicts();
removeConflictsFromDatabase(conflictsToDelete);
removeConflictsFromCache(conflictsToDelete);
```

One or more of these calls is now missing. Same pattern in `UtilConcurrentCommandScheduler` where the constructor's pool-setup statements got eaten.

### Pattern 3 — Listener-pattern boilerplate stripped

`ClassifiableFilterEdit.java` lost the entire `addChangeListener`/`removeChangeListener`/`getChangeListeners`/`fireFilterChanged` quartet. These are the standard javax.swing ChangeListener pattern; need to be restored to support UI updates driving the filter edit panel.

### Pattern 4 — Inline `if`/`while` body dropped

Per PRD 001-A: "the dedup-script regex incorrectly matched some call statements as method declarations, removing lines between adjacent control-flow keywords." Known affected: `ResourceBundleLoader`, `SerializableDateTimeFormat`, `ISODateTimeFormat`, `UtilConcurrentCommandScheduler`, `LocalCache`. Current run finds **143 more**.

### Pattern 5 — Date-related-but-not-Date-typed code dropped

A subset of "non-Date" deletions are adjacent to Date sites: dedup ate the line *after* a Date getter rename. E.g. `RaplaSQL.java` lost `setTimestampLocalDateTime(stmt, 6, category.getLastChangedAsLocalDateTime())` — the getter was renamed to `getLastChanged` later, but the caller line itself shouldn't have been dropped.

## Plan

Recovery is mechanical but volume-heavy. Three options, all baselined against `HEAD`:

1. **Per-hunk `git checkout HEAD -- <file>` then cherry-pick Date edits back in.** Granular but slow — 148 files. Best for localized damage with intact surrounding context.
2. **`git checkout HEAD -- <file>` for files where script damage exceeds the Date migration's actual surface.** Resets file to committed state, re-runs `replace-date-with-localdatetime.py` (with bug-fixed state-machine). Best for `ReservationControllerImpl`, `AppointmentListEdit`, `UtilConcurrentCommandScheduler` (>50 lines damage).
3. **Per-pattern recovery script** — find `Promise<Void> execute()`/`undo()`/`getCommandoName()` triplets missing in working tree but present at `HEAD`, re-insert. Patterns are uniform enough for the Command-pattern triplet.

**Recommended order:**

1. Reset top-3 high-damage files (option 2):
   - `UtilConcurrentCommandScheduler.java` (188 lines)
   - `ReservationControllerImpl.java` (64)
   - `AppointmentListEdit.java` (63)
   - `ClassifiableFilterEdit.java` (24)
   - `AllocatableSelection.java` (24)
   - `AppointmentController.java` (23)
2. Re-run `replace-date-with-localdatetime.py` (bug-fixed state-machine) — re-applies intended Date migration without dedup collateral.
3. For the long tail (~140 files, 1–22 deletions each), use per-file diff. Many will be already-handled or trivial dropped braces.

## Tests

After each recovery batch:
- `mvn -pl <module> -am compile` — confirm no new compile errors
- For Pattern-1 files (CommandUndo recovery), run existing undo/redo tests
- Full reactor `mvn test` once at the end as regression gate

## Open questions

1. **Which dedup-pass regex caused this?** `.agents/scripts/remove-date-duplications.py` is intentionally destructive (purpose: remove duplicate declarations from earlier phases). The bug is that its regex matched too broadly. Worth post-mortem: add a test proving the regex doesn't match `Promise<Void> execute()`-style anonymous-class declarations.
2. **Are some deletions legitimate?** Yes — `DateTools.java` lost ~49 lines, but most were `Date`-typed method bodies this session intentionally deleted. Subtract intentional before reporting — true number ~700–800.

## Related PRDs

- **PRD 001-A** — parent migration that ran the offending scripts. Its Phase 4 acknowledges 18 leftover errors in 5 named files; this PRD scopes the actual damage to 148 files.
