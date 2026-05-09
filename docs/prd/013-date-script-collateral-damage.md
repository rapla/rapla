# PRD 013: Recover collateral damage from Date migration scripts

**Status:** draft
**Date:** 2026-05-09

## Goal

The Date → LocalDateTime migration scripts (PRD 001-A Phase 4 — `strip-date-conversions.py`,
`remove-date-duplications.py`, `replace-date-with-localdatetime.py`, `fix-phase4-errors.py`)
deleted **~1,068 lines across 148 files** that have nothing to do with the Date migration.
Most of these are real code: method calls, anonymous-class command definitions, JDBC helper
calls, listener-pattern boilerplate. They were dropped because the dedup-pass regex
incorrectly matched code-statement lines as method declarations and removed lines between
adjacent control-flow keywords.

This PRD catalogs the deletions and tracks recovery. The baseline is **`HEAD`** (the most
recent committed state — the multimodule shift), not `origin/master`. The Date migration
scripts ran *after* the multimodule shift was committed, so all the damage is in the
uncommitted working tree and visible via `git diff HEAD`.

## Reproduction

```bash
python3 .agents/scripts/find-script-collateral-vs-head.py 2>/dev/null > /tmp/collateral.txt
```

Output: per-file list of removed lines, filtered to exclude tokens that are clearly part
of the Date cleanup (`Date`, `LocalDateTime`, `toMilli`, `cutDate`, `before`/`after`
method calls, etc.), trivial whitespace, comments, and `import` lines.

## Scope

**148 files** have non-Date deletions. Top files by deletion count:

| Lines | File | Pattern |
|---:|---|---|
| 188 | `rapla-core/.../scheduler/sync/UtilConcurrentCommandScheduler.java` | Entire constructor body + ThreadFactory + pool setup gone |
| 64 | `rapla-client/.../client/internal/ReservationControllerImpl.java` | Anonymous `CommandUndo` impls (`execute()`, `undo()`, `getCommandoName()`) for ~10 commands |
| 63 | `rapla-client/.../swing/internal/edit/reservation/AppointmentListEdit.java` | Same pattern: `CommandUndo` inner classes for appointment add/remove/split |
| 49 | `rapla-core/.../components/util/DateTools.java` | Internals of `cutDate(Date)` body, `modifyDate(...)` helper — most of which I deleted in this session anyway during the gateway sweep, so most are intended |
| 37 | `rapla-server/.../storage/dbsql/RaplaSQL.java` | `setTimestampLocalDateTime`/`setDateLocalDateTime`/`getDateAsLocalDateTime` JDBC helper calls |
| 30 | `rapla-server/.../storage/impl/server/LocalAbstractCachableOperator.java` | Method calls dropped between control-flow lines (`removeOldConflicts()`, `removeConflictsFromCache()`, `storeAndRemove(...)`) |
| 24 | `rapla-client/.../swing/internal/edit/ClassifiableFilterEdit.java` | Listener-pattern boilerplate: `addChangeListener`, `fireFilterChanged`, `getChangeListeners` bodies |
| 24 | `rapla-client/.../swing/internal/edit/reservation/AllocatableSelection.java` | UI binding callbacks (`updateBindings`, `fireAllocationsChanged`, `updateButtons`) |
| 23 | `rapla-client/.../swing/internal/edit/reservation/AppointmentController.java` | Date-comparison branches and event handlers (`mapToAppointment`, `doChanges`, `resetWeekdays`) |
| ... | ~138 more files with 1–22 deletions each | mix |

## Damage patterns

### Pattern 1 — Anonymous-class `CommandUndo` blocks dropped

Most-affected pattern, found in `ReservationControllerImpl`, `AppointmentListEdit`,
`AppointmentController`, `AllocatableSelection`, etc. The dedup-pass regex matched the
opening `public Promise<Void> execute() {` of an anonymous-class method as a "duplicate
method declaration" and dropped lines until it found a matching `}`.

**Example** (`ReservationControllerImpl.java`, missing in current working tree vs `HEAD`):
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

All three method bodies inside the anonymous `CommandUndo` instance were stripped.
Without these, undo/redo for several user actions (exchange allocatables, move
appointment, copy reservation, save reservation) is broken.

### Pattern 2 — Method-call statements dropped between control-flow lines

The dedup-pass regex was anchored on patterns that match both method declarations and
method *calls* with identical-looking syntax. When two method calls appeared on
consecutive lines after a control-flow keyword, the regex dropped one as a "duplicate."

**Example** (`LocalAbstractCachableOperator.java`):
```java
removeOldConflicts();
removeConflictsFromDatabase(conflictsToDelete);
removeConflictsFromCache(conflictsToDelete);
```

One or more of these calls is now missing. Same pattern in
`UtilConcurrentCommandScheduler` where the constructor's pool-setup statements got eaten.

### Pattern 3 — Listener-pattern boilerplate stripped

`ClassifiableFilterEdit.java` lost the entire `addChangeListener`/`removeChangeListener`/
`getChangeListeners`/`fireFilterChanged` quartet. These are the standard javax.swing
ChangeListener pattern; they need to be restored to support UI updates that drive the
filter edit panel.

### Pattern 4 — Inline `if`/`while` body dropped

Per PRD 001-A's own report: "the dedup-script regex incorrectly matched some call
statements as method declarations, removing lines between adjacent control-flow
keywords." The PRD lists `ResourceBundleLoader`, `SerializableDateTimeFormat`,
`ISODateTimeFormat`, `UtilConcurrentCommandScheduler`, `LocalCache` as known affected
files. The current run finds **143 more** beyond that list.

### Pattern 5 — Date-related-but-not-Date-typed code dropped

A subset of "non-Date" deletions are actually adjacent to Date sites: the dedup pass
eating the line *after* a Date getter rename. E.g. `RaplaSQL.java` lost
`setTimestampLocalDateTime(stmt, 6, category.getLastChangedAsLocalDateTime())` —
the `getLastChangedAsLocalDateTime` was renamed to `getLastChanged` later, but the
caller line itself shouldn't have been dropped.

## Plan

The recovery is mechanical but volume-heavy. Three options, all baselined against `HEAD`
(the multimodule shift commit):

1. **Per-hunk `git checkout HEAD -- <file>` then cherry-pick the Date edits back in.**
   Granular but slow — 148 files. Best for files with localized damage where the
   surrounding context is intact.
2. **`git checkout HEAD -- <file>` for files where the Date-script damage exceeds the
   Date migration's actual surface.** Resets the file to its committed state, then
   re-runs the `replace-date-with-localdatetime.py` script on it (now with the
   bug-fixed state-machine pass). Best for `ReservationControllerImpl`,
   `AppointmentListEdit`, `UtilConcurrentCommandScheduler` where deletions are >50
   lines.
3. **Per-pattern recovery script** — write a script that finds each
   `Promise<Void> execute()` / `undo()` / `getCommandoName()` triplet missing in working
   tree but present at `HEAD`, and re-inserts them. Patterns are uniform enough this
   could work for the Command-pattern triplet (option 1's most common shape).

**Recommended order:**

1. Reset the top-3 high-damage files (option 2):
   - `UtilConcurrentCommandScheduler.java` (188 lines)
   - `ReservationControllerImpl.java` (64 lines)
   - `AppointmentListEdit.java` (63 lines)
   - `ClassifiableFilterEdit.java` (24 lines)
   - `AllocatableSelection.java` (24 lines)
   - `AppointmentController.java` (23 lines)
2. After reset, re-run `replace-date-with-localdatetime.py` (with the bug-fixed
   state-machine pass) — this re-applies the *intended* Date migration without the
   collateral damage from the dedup pass.
3. For the long tail (~140 files with 1–22 deletions each), use the per-file diff
   approach. Many will turn out to be already-handled or trivial dropped braces.

## Tests

After each recovery batch:
- `mvn -pl <module> -am compile` — confirm no new compile errors introduced
- For files in pattern-1 (CommandUndo recovery), run any existing undo/redo tests
- Run the full reactor `mvn test` once at the end as a regression gate

## Open questions

1. **Which dedup-pass regex caused this?** The PRD says
   `.agents/scripts/remove-date-duplications.py` — but that script is intentionally
   destructive (its purpose is to remove duplicate declarations introduced by earlier
   migration phases). The bug is that its regex matched too broadly. Worth post-mortem:
   add a test that proves the regex doesn't match `Promise<Void> execute()`-style
   anonymous-class method declarations.
2. **Are some deletions legitimate?** Yes — e.g., `DateTools.java` lost ~49 lines in
   this run, but most were the `Date`-typed method bodies that this session
   intentionally deleted. Need to subtract intentional deletions before reporting
   "1,068 collateral lines" — the true number is likely lower (~700–800).

## Related PRDs

- **PRD 001-A** (`docs/prd/001-a-date-to-localdatetime.md`) — the parent migration that
  ran the offending scripts. Its Phase 4 progress note already acknowledges 18 leftover
  errors from script over-reach in 5 named files; this PRD scopes the actual damage to
  148 files.
