# PRD 016: Pre-check-in audit of `git diff HEAD` after Date migration

**Status:** done (2026-05-11). The audit drove six follow-up commits (`4a6b6603` … `bcaceb88`) that systematically restored Pattern-2 collateral and finished the per-file verdict pass. Working-tree file count dropped from 242 at audit start to ~48 today (the remainder are intentional Date-migration edits plus the architecture docs from PRD 022). No outstanding "unsure" verdicts remain.
**Date:** 2026-05-09 (closed: 2026-05-11)

## Goal

Before the next commit on `spring-boot`, get a confident verdict —
*intentional / collateral / unsure* — for every line the working tree changes
vs `HEAD`. The Date-migration scripts (PRD 001-A Phase 4, catalogued in
PRD 013) intentionally rewrote thousands of `Date`-typed sites but also
dropped ~700–1,000 lines of unrelated code. We want every collateral line
restored before commit, every intentional change kept, and zero "unsure"
lines remaining.

## Scope

`git diff HEAD` on this branch: **242 Java files, +2,906/−4,177 (1,245 hunks).**

## Audit method — six passes

A deletion-only classifier turned out to miss whole classes of damage
(duplicated lines, semantic-divergence pairs, caller-side cascades). The
final method runs six complementary passes and combines the evidence per
file:

| pass | input | finds |
|---|---|---|
| A | every deleted line | shape category + verdict (intentional / follows-prev / review / review-collateral / collateral) |
| B | every pair of consecutive `+` lines | duplicated lines (state-machine wrote new shape, didn't delete old) |
| C | each hunk | a `-` line and a high-similarity `+` line with deletions in between → move-with-drops |
| D | each hunk with `n_del==0` | injected stubs (filter import-only adds) |
| E | each `-`/`+` pair | sim ∈ [0.3, 0.85] with operand divergence beyond a clean type rename |
| F | `mvn -pl rapla-app -am compile` | compile errors intersected with passes A–E findings: **confirmed-collateral / nearby-collateral / BLIND-SPOT** |

A line/hunk is considered safe only if **no pass flags it** AND every `-`/`+`
pair classifies as `mechanical` (imports/whitespace/braces/comments) or
`pure-rename` (clean type swap with operands intact after stripping
`Date`/`LocalDateTime`/`DateTools.toMilli`/`new Date(...)`/`.getTime()` noise).

### Verdict derivation per hunk

For each hunk:

1. **mechanical** — every `-`/`+` line is import / whitespace / brace /
   comment / annotation only.
2. **pure-rename** — `|dels| == |adds|`; pairing each `-` with its highest-
   token-jaccard `+` partner, every pair becomes textually equal after
   stripping rename noise. No operand reorder, no signature change.
3. **logic-bearing** — anything else: orphan deletes/adds, mismatched cardinality,
   operand divergence, signature changes.

Files with **only** mechanical/pure-rename hunks are auto-accept. Files with
any logic-bearing hunk need per-hunk review.

## Findings (2026-05-09 run)

| metric | value |
|---|---:|
| Java files changed | 242 |
| Auto-accept (only mechanical/pure-rename) | **67** |
| Logic-bearing candidates | **175** |
| Logic-bearing hunks across candidates | **497** |
| Pass B duplicate-line findings | 14 |
| Pass C move-with-drops findings | 252 (227 in `UtilConcurrentCommandScheduler` known to PRD 013) |
| Pass D non-import pure-adds | 4 |
| Pass E semantic-drift pairs (after filtering clean retypes) | 379 |
| Pass F compile errors at the time of the run | 22 unique (14 BLIND-SPOTs in `RaplaSQL.java`) |

### Categories that emerged

**Intentional (Date migration):** `Date`-typed declarations replaced by
`LocalDateTime`; `*AsLocalDateTime` overloads collapsed to canonical names;
`DateTools.toDate(...)` / `toMilli(...)` / `toLocalDateTime(...)` bridges
stripped; `import java.util.Date` removed; `cutDate(...)` removed when the
target field is now `LocalDate`; `SerializableDateTimeFormat` /
`ISODateTimeFormat` adapters removed; Spring 4 import path moves
(`boot.test.autoconfigure.web.servlet` → `boot.webmvc.test.autoconfigure`).

**Collateral patterns** (PRD 013 + new):

- Pattern 1 — anonymous-class `Promise<Void> execute()/undo()` /
  `getCommandoName` triplets dropped from `CommandUndo` instances.
- Pattern 2 — method-call statements dropped between control-flow lines
  (`removeOldConflicts(); setLastRefreshed(...);` etc.). The `FileOperator`
  case the user flagged is this pattern.
- Pattern 3 — listener-pattern boilerplate stripped
  (`addChangeListener` / `fireFilterChanged` / `getChangeListeners`).
- Pattern 4 — inline `if`/`while` body dropped between adjacent control-
  flow keywords.
- Pattern 5 (new, found by Pass B) — **duplicated lines.** State-machine
  pass wrote the migrated form before deleting the original; delete regex
  failed. Seed: `FileOperator.java:724`. Total found: 14 instances
  across 10 files (incl. `AbstractRaplaSwingCalendar`, `ReservationHelper`,
  `SimpleEntity`, `ArchiverServiceImpl`).

**Caller-side cascades** (Pass F BLIND-SPOTs at the time of the F run): every
`cannot find symbol` for `setDateLocalDateTime` / `setTimestampLocalDateTime`
in `RaplaSQL.java` referred to helpers that were removed/renamed in
`AbstractTableStorage` without caller updates. Invisible to passes A–E
because no `-` line exists at the call site.

## Plan

### Phase 1 — Auto-accept commit (DONE, commit `670219d9`)

67 mechanical/pure-rename files committed in one batch. Compile-clean before
commit. The remaining 175 candidates stay in the working tree.

### Phase 2 — Top-10 logic-bearing files (in progress)

The top 10 candidates by risk score account for ~28% of remaining logic-
bearing hunks (~140 of 497):

| rank | file | hunks |
|---:|---|---:|
| 1 | `rapla-server/.../storage/dbsql/RaplaSQL.java` | 27 |
| 2 | `rapla-core/.../components/util/DateTools.java` | 17 |
| 3 | `rapla-core/.../entities/domain/internal/AppointmentImpl.java` | 17 |
| 4 | `rapla-server/.../storage/impl/server/LocalAbstractCachableOperator.java` | 20 |
| 5 | `rapla-core/.../entities/tests/AppointmentTest.java` | 14 |
| 6 | `rapla-core/.../entities/domain/internal/RepeatingImpl.java` | 10 |
| 7 | `rapla-core/.../components/util/SerializableDateTimeFormat.java` | 6 |
| 8 | `rapla-core/.../entities/domain/internal/PermissionImpl.java` | 6 |
| 9 | `rapla-core/.../facade/internal/ConflictImpl.java` | 10 |
| 10 | `rapla-client/.../client/internal/ReservationControllerImpl.java` | 10 |

Walk hunks in order; verify the `-`/`+` pair preserves semantics. Restore
collateral; keep intentional. Compile per module after each batch.

### Phase 3 — Long-tail (165 files, ~360 hunks)

Files 11–175 ranked by risk score. Most have 1–5 logic-bearing hunks each
and concentrate on type-rename + operand-flip pairs. Spot-check, accept
clusters of pure-rename-shaped hunks unless adjacent to a flagged line.

### Phase 4 — Restore Pattern-5 duplicates (14 sites)

Mechanical: delete the duplicate `+` line. Each is a one-line edit.

### Phase 5 — Reactor green-build gate

`mvn test` across the reactor before final commit. Targeted regression
tests for restored Pattern-1 (undo/redo) and Pattern-3 (listener) sites.

### Phase 6 — Close

`git mv docs/prd/016-pre-checkin-deletion-audit.md docs/prd/done/` and flip
PRD 013 to `done` (the audit catalog is fully resolved once Phases 2–5
land).

## Tests
- `mvn -pl <module> -am compile` per batch of restores.
- Reactor-wide `mvn test` once before final commit.
- Per AGENTS.md §1 / §5: targeted tests during the session, full reactor
  green-build only at the end.

## Open questions

1. **Pass E noise floor.** 379 semantic-drift pairs is mostly clean
   `Date→LocalDateTime` operand rewrites that the filter doesn't catch.
   Walking these by hand is tedious; an AST-level diff would be better but
   expensive to build. **Acceptance: hand-eyeball.**
2. **`UtilConcurrentCommandScheduler.java` got auto-accepted** despite
   PRD 013 naming it the worst-damaged file (188 lines collateral). It now
   shows balanced +424/−423; every delete pairs with a same-token add.
   **Spot-check before final commit** — confirm the file was reset and
   re-applied (PRD 013 plan 2) rather than the matched pairs being
   coincidental.
3. **Pass F coverage gap.** Logic bugs that compile cleanly slip through
   (e.g. ternary flip in `FileOperator.getHistoryValidStart` silently
   drops `HISTORY_DURATION` from one branch). Only tests catch these.
   **Recommend: targeted tests during Phase 2 for any pair flagged by
   Pass E with sim < 0.6.**

## Related PRDs
- **PRD 001-A** (`docs/prd/001-a-date-to-localdatetime.md`) — parent
  migration.
- **PRD 013** (`docs/prd/013-date-script-collateral-damage.md`) — catalog
  of the collateral damage. This PRD is the audit-and-fix execution that
  013 scoped.
- **PRD 015** (`docs/prd/015-finish-date-migration-rapla-client.md`) —
  touches the same file set; coordinate restore order.
