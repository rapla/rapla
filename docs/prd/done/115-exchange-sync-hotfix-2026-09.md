# PRD 115 — Exchange sync hotfix 2026-09: paging, failure domain, series & delete semantics

**Status:** done — 2026-09-13 — split out of [PRD 114](../114-exchange-sync-per-mailbox-lock.md) (which keeps only the open locking/executor work). Production (legacy WAR) runs hotfix **v11 = hunks 1–13**; **hunk 14** (weekly series with several weekdays) is in both trees incl. test but **not yet deployed** (v12 waits for the next build). All hunks are ported to `spring-boot` with `SynchronisationManagerHotfixTest`.
**Related:** [PRD 114](../114-exchange-sync-per-mailbox-lock.md) (open: per-mailbox locks, executor split, connector lifecycle), [PRD 070](../070-restore-exchange-connector-wiring.md) (server wiring + scheduler split), [PRD 038](../038-graph-calendar-sync.md) (Graph backend, additive), [exchange-sync](../../architecture/exchange-sync.md) (the resulting rules, canonical), operational record with site/mailbox specifics and per-step counts: `dhbwrapla/docs/exchange-sweep-analysis-2026-09.md` (gitignored, not in this repo).

## Abstract

A production analysis (2026-09-09, one location's Exchange, read-only) found three independent
reasons why appointments do not reach lecturers' Outlook calendars: (1) mailbox→Person mapping
misses when the Person's `email` is an alias and `exchangeMailbox` is empty, (2) one
"access denied" mailbox aborts **all** remaining tasks of that user per run
(`processTasks` `break`), (3) the hourly full sweep shares the RxJava computation workers
with the 6-second change poll, so the poll is stalled ~40–60 % of the time. The same day a
series of fourteen small hunks went into the legacy WAR and into `spring-boot`: paging and
service reuse, the per-mailbox failure domain, and — after the first sweeps exposed them —
the rules for items the mailbox owner copied, marked private or edited, plus the sweep window.
Cause (3) and the structural fix (per-mailbox locks, executor split) stay in PRD 114.

## Findings (2026-09-09, production, read-only)

- `SynchronisationManager.getMailbox()` keys on `exchangeMailbox` → `email` (lowercased).
  Exchange resolves shared calendars (`EWSConnector.loadMailboxes`, `resolveName(legacyDN)`)
  to the mailbox's **primary SMTP** (`firstname.lastname@…`). At the affected location the
  HR feed delivers alias-form emails (`lastname@…`) for the large majority of persons → those
  calendars are skipped (`Resource for mailbox X not found  Skipping mailbox`) on every sweep unless
  `exchangeMailbox` is set by hand. Data fix in progress with the customer; not code.
- `processTasks`: on any message containing "Access is denied"/"Zugriff"/"verweigert" it set
  `accessError = true` and **`break`d the task loop for the whole user**. One shared calendar
  without write permission for the sync account therefore dropped every task ordered after
  it, every run — observed as diffs that never converge for other mailboxes.
- `EWSConnector.getService()` builds a new `ExchangeService`/HttpClient per call and never
  closes it — hundreds of open sockets, many in CLOSE-WAIT, on the sync node.
  `getExchangeAppointments` called it **inside the paging loop**, i.e. one new HttpClient +
  TLS handshake per page. Measured with a read-only probe that reuses one `ExchangeService`:
  ~30 ms per page; production spent 5–10 s per mailbox on the same calls and its read
  timeouts clustered on the calendars with the most pages.
- The "same appointments every sweep" were a genuine backlog, not a comparison artefact:
  reservation series changed in rapla weeks ago carried Exchange `raplaLastUpdate` stamps
  from July/August; some items were never written at all. Tasks live in a `HashSet` (random
  order) and every run aborted at the first access-denied mailbox, so whatever was ordered
  behind it was dropped — "never converges, writes occasionally" is exactly that.
- `getExchangeAppointments` paged with `new ItemView(5, offset)` but advanced `offset++`
  instead of `offset += 5` — the EWS offset is an *item* offset, so a calendar with n items
  cost n−4 round-trips instead of n/5 and every item landed ~5× in the result list. Side
  effect: `appointmentsToDelete` held the same item several times → the recurring "Deleted
  appointment … not found in the store" warnings. There was no per-mailbox time budget and
  no "skip mailbox that failed last sweep" backoff.
- The task persistence is dead code: `reloadSyncTasks()` is `if (true) return empty`,
  `storeAndRemove` calls are commented out. The `IMPORT_EXPORT` table is neither read nor
  written by the sync loop. Consequence (hunk 8): the poll's delete path looked up persisted
  tasks and therefore never deleted anything.
- **Duplicate items** (verified with the read-only probe against two locations): calendars
  holding several Exchange items with the same `raplaId`. The sweep diff reads all of them,
  the write path (`getExchangeAppointmentByRaplaId`, `ItemView(1)`) always updates the same
  first item, the stale ones keep an old `raplaLastUpdate` → flagged again every hour. Origin
  is not the poll/sweep race but **leftovers of former series**: single days of an earlier
  weekly/daily version of the appointment; the update of the recurring master does not remove
  the old occurrences/exceptions. `MoveToDeletedItems` fails for the delegate account in a
  foreign mailbox ("Object cannot be deleted"); `SoftDelete` works. Cleaned by hand; the
  systematic fix (dedupe on write, series shrink audit) is PRD 114 Phase 2b.
- **Undeletable orphans, root cause reproduced:** the items carry `PR_SENSITIVITY = 2`
  (**Private**) — the mailbox owner marked the rapla item private in Outlook. Exchange refuses
  delegate writes on private items even with Editor rights and answers `ErrorItemNotFound`
  (Soft/HardDelete) / `ErrorCannotDeleteObject` (MoveToDeletedItems). Reproduced live: a fresh
  rapla item in the user's own calendar, marked private → SoftDelete fails with the same "not
  found"; an identical non-private item was deletable minutes earlier. Folder rights were
  Editor everywhere (`FolderSchema.Permissions`), `PR_ACCESS`=7 on the items — rights were
  never the problem; `FolderSchema.EffectiveRights` is not informative for delegates. Owners
  must un-private or delete these items.

## The hunks (legacy WAR + `spring-boot`, 2026-09-09 … 2026-09-12)

Decided 2026-09-09: only mechanical hunks go to production; everything structural
(per-mailbox lock, task queue, executor split) stays branch work — PRD 114.

1. `processTasks`: `break` only on HTTP 401 of the sync account; an access-denied on a
   single shared calendar marks only that task/mailbox as failed.
2. `getExchangeAppointments`: `offset += pageSize`, page size 100, `getService()` hoisted
   out of the paging loop (test `pagingAdvancesByPageSize`). Dedupe by item id turned out
   unnecessary once paging is correct.
3. (after sweep #1 showed hundreds of repeated failing writes on two unwritable calendars)
   `processTasks`: remember the resource id of a task that failed in this run and skip the
   user's remaining tasks for that resource — one failed attempt per broken mailbox per
   sweep instead of hundreds. Minimal form of PRD 114's "mailbox = failure domain".
4. (after sweep #2 showed hundreds of repeated writes that were all co-participant fan-out)
   `synchronizeMailboxes`: filter the tasks from `updateOrCreateTasks(appointment)` to the
   allocatable whose calendar was just diffed (test `sweepKeepsOnlyTasksOfTheDiffedResource`).
   `updateOrCreateTasks` is the poll-path helper ("appointment changed → update every
   participant's calendar"); the per-mailbox sweep reused it unfiltered, so one mailbox with
   a permanently pending diff re-wrote the shared appointment into every co-participant's
   calendar every sweep. Net Exchange state is unchanged by the filter — every mapped mailbox
   is diffed on its own anyway.
5. `AppointmentSynchronizer.delete()`/`remove()`: `SoftDelete` instead of `HardDelete`, and the
   swallowed `ServiceResponseException` is rethrown so a failed delete reaches the result
   mail (`open++`) instead of being logged as done. (5b "re-bind before delete" was tried
   and removed — no effect.) Effect: the silent delete failures became visible in the `open`
   count but still failed — the private-item cause above.
6. Result mail lists every shared calendar as "synchronisiert" or
   "KEINE RAPLA-PERSON ZUGEORDNET (exchangeMailbox setzen)" (`SynchronisationManager`
   resync path); task log lines and mail error lines carry the mailbox
   (`SynchronizationTask [mailbox=…`).
7. Owner copies are invisible to rapla: `getExchangeAppointments` (sweep read) and
   `getExchangeAppointmentByRaplaId` (update/delete lookup) read MAPI `PR_CREATOR_NAME`
   (0x3FF8) and skip every item the sync account did not create (`ownedBySyncAccount`,
   compared by the login's local part; test `onlyNormalItemsCreatedBySyncAccountAreRaplasOwn`).
   An Outlook copy keeps the rapla marker, so without this rapla would update/delete the
   owner's copy (and fail) instead of its own item. With an Editor share rapla *can* edit
   foreign items (verified live), so this is a deliberate guard, not a workaround for missing
   rights.
8. Poll delete path: on a removed reservation `updateTasksSetDelete()` looked up the
   *persisted* tasks — empty since persistence was disabled — so the poll never deleted an
   Exchange item; deletes only happened via the hourly sweep. Verified live (reservation
   removed, poll logged "Removing", no delete). Fix: build the delete tasks from the removed
   reservation via `updateOrCreateTasks(app)` + status `toDelete` (2 lines). This shortens
   the window between a rapla delete and the Exchange delete from up to an hour to seconds.
9. Per-mailbox backoff (`FailureBackoff`): after n failed tasks a mailbox is skipped for the
   next n sweeps (capped at 24 = one day of hourly sweeps); a successful task resets it
   (test `failingMailboxIsSkippedForAsManySweepsAsItFailed`). Bounds the cost of permanently
   broken calendars (permission cases, undeletable items) without hiding them — they still
   appear in the result mail when retried. Keyed per mailbox on purpose: a calendar without
   write rights fails on the first attempt anyway, and `failedResources` (hunk 3) already
   stops the remaining tasks of that mailbox within a run.
10. Private items are not rapla's: `ownedBySyncAccount` also returns false for
   `Sensitivity != Normal`, so the sweep neither updates, deletes nor counts an item the owner
   marked private — if the rapla appointment still exists, rapla creates its own (normal)
   item next to the owner's private one, consistent with the copy rule (hunk 7).
11. Foreign items are read, not ignored: `getExchangeAppointments` flags owner copies / private
   items as `foreign` instead of dropping them. Decision rule (same code in the sweep diff and in
   the write path `addOrUpdate`, i.e. poll and sweep behave identically): an own, non-private item
   always wins and is updated; if there is none, the foreign items are compared by **start, end
   and subject** with what rapla would write (`foreignItemUpToDate`, one match is enough; test
   `foreignItemIsUpToDateOnlyWhenStartEndSubjectMatch`) → nothing is written and the case is
   reported; otherwise rapla creates its own item next to the owner's. Foreign items are never
   deleted. The sweep logs one INFO line per mailbox for the up-to-date cases and a WARN per
   appointment when it creates its own item next to an outdated copy (v7 damping); the resync
   result mail lists them per mailbox ("kopiert oder als privat markiert").
12. Last writer wins for rapla's own items (replaces "rapla is always master"): if the owner
   modified the item after rapla's last change (`LastModifiedName` ≠ sync account and
   `LastModifiedTime` > `lastChanged`) **and** start/end/subject differ from rapla's intent,
   `addOrUpdate` writes nothing, logs INFO and reports it in the resync mail ("vom
   Postfach-Inhaber am … bearbeitet"); a later rapla change wins again. Tasks of an explicit
   resync (Swing dialog) are `forced` and always write (test
   `ownerEditAfterRaplaChangeWinsUnlessForced`). When rapla writes and the item's recurrence
   shape differs from the appointment (owner turned a series into a single block or vice
   versa), the own item is soft-deleted and re-created instead of updated (Exchange rejects
   such updates). Sync-account name matching is normalised (`isSyncAccountName`: display-name
   form with spaces ~ login local part with dots).
13. Sweep window: `exch-sync-past` (default 30 days) is now applied — it existed as config
   but `isInSyncInterval` was short-circuited and the sweep queried without dates. The sweep
   queries rapla from `today − exch-sync-past`; on the Exchange side the compare skips items
   ending before the window **except recurring masters**, and the delete loop skips every
   item whose first end lies before the window, masters included (`endsBeforeSweepWindow`).
   Master detection is `AppointmentType == RecurringMaster` (`isRecurringMaster`), never
   `IsRecurring`. A safety cap `MAX_DELETES_PER_MAILBOX_PER_SWEEP = 50` drops a mailbox's
   deletes with a WARN (test `sweepWindowNeverProducesDeletesForOldItems`). The poll is
   unchanged (rapla changes to old appointments still propagate). How this hunk was
   arrived at: § Lessons below.
14. (2026-09-12) Weekly series with several weekdays: `getExchangeRecurrence` wrote a weekly
   repeating as `WeeklyPattern(start, interval, <weekday of start>)`, dropping the other
   selected weekdays of `Repeating.getWeekdays()` (e.g. We/Th/Fr → only Wednesday in
   Outlook). Now `WeeklyPattern(start, interval, weeklyDays(getWeekdays()))` (SUNDAY=1..
   SATURDAY=7 maps to `DayOfTheWeek.values()[w-1]`; test `weeklyRepeatingExportsEveryWeekday`).
   Existing series in Exchange keep their stamp, so they are only rewritten after a resync or
   an edit of the event. **Not yet deployed** (v12).

Effect in production (per-step counts: dhbwrapla analysis file): sweep read phase from tens
of minutes to under two minutes with zero read timeouts; the affected location's whole
backlog written in one run; after hunks 3/4 one failed attempt per broken mailbox and zero
co-participant rewrites; after v7 a steady state where the sweep log holds only the
permission cases, unmapped calendars and known bind failures. Poll ticks during the sweep's
execute phase are still sparse — PRD 114 Phase 3.

## Lessons (incident v8–v11, the sweep-window night)

Hunk 13's first cut windowed the rapla query and the compare side but **not the delete
loop**, so every own item older than the window became "only in Exchange" and was
soft-deleted within minutes across many calendars (past appointments only, no mails —
`SendToNone`, all `sendInvitationAndCancelation` prefs false). Rolled back within minutes; the
windowless sweep re-created all missing items. The independent review by the parallel session
had flagged exactly this (HIGH-1) minutes after the deploy — the deploy went out before the
review result. The second cut guarded recurring masters via `IsRecurring`, which is *false*
on a master (`PidLidIsRecurring` flags occurrences; `PidLidRecurring` is the master flag), so
the guard did nothing and hunk 12's shape check saw "not recurring ≠ repeating" for every
tasked series → soft-delete + re-create of every tasked series. With masters correctly
inside the window (v10), every series whose LAST occurrence lies before the window was
absent on the windowed rapla side and deleted as "only in Exchange". v11 = the delete side
skips everything whose first end lies before the window, masters included.

Rules recorded (canonical wording in [exchange-sync](../../architecture/exchange-sync.md)):
- **Compare/delete asymmetry.** A window or filter applied to the rapla side of a diff must
  be applied to the delete side as well, otherwise everything filtered out on the rapla side
  becomes "only in Exchange". An item whose end lies inside the window always overlaps it,
  so its rapla appointment — if it still exists on this resource — is in the windowed set;
  deletes therefore hit only true orphans.
- **Recurring masters** are detected by `AppointmentType == RecurringMaster`, never by
  `IsRecurring`; a master's `End` is its first occurrence's end, so window checks on masters
  use "ends before the window" semantics, not "outside the window".
- **Delete cap.** `MAX_DELETES_PER_MAILBOX_PER_SWEEP = 50` stays a constant; a larger
  legitimate cleanup goes through a forced resync or the deployment's dupclean probe.
- Known limitation: a series removed in rapla whose first occurrence is old is only removed
  by the poll (hunk 8). Repair after the incident: `exch-sync-past` temporarily raised far
  beyond the default for one sweep, then lowered again; the value is read once at
  construction — a change needs a restart.
- **Deploy after the review, not before.** Both bad cuts were caught by the parallel review
  within minutes; each went live before the finding arrived.
- Open: one series update fails with "Das Objekt kann nicht gelöscht werden" in
  `removeRecurrenceExceptions` after re-creation (1 mailbox) — PRD 114 Phase 2b.

## Backport to `master` (legacy production line)

Production runs the legacy WAR built from `master`; our work happens on `spring-boot`. The
three classes differ file-wide (Date→LocalDateTime, logger migration) but every spot these
hunks touch exists identically in both trees. All fourteen hunks are in both trees;
`SynchronisationManagerHotfixTest` pins them on `spring-boot`.

| Hunk | spring-boot | master | Backport |
|---|---|---|---|
| 2 paging `offset += pageSize`, page size 5→100 | `AppointmentSynchronizer.getExchangeAppointments` | same | **simple** (3 lines) — done |
| 1/3 drop the foreign-mailbox `accessError` `break`, per-run failure domain | `SynchronisationManager.processTasks` | same | **simple** — done |
| 9 `FailureBackoff` | `synchronizeMailboxes` loop | same | **simple** (one map) — done |
| 6 sync-result mail lists unmapped shared calendars | `synchronize()` resync path | same | **simple** — done |
| 7/10/11/12 ownership, private, foreign compare, last writer wins | `AppointmentSynchronizer` + `SynchronisationManager` | same | **simple/medium** — done |
| 13 sweep window + master detection + delete cap | `SynchronisationManager` | same | **medium** — done |
| 14 weekly weekdays | `AppointmentSynchronizer.getExchangeRecurrence` | same | **simple** — in both trees, deploy pending |

Deploy path (legacy): swap `WEB-INF/lib/rapla-2.1-SNAPSHOT.jar` inside the deployed WAR on
the sync node, backup per the existing convention, service restart. Web node untouched (no
sync there). The jar must be built from the same checkout as the currently deployed one
(`origin/master` is older than that build).

## Tests

`rapla-server/src/test/java/org/rapla/plugin/exchangeconnector/server/SynchronisationManagerHotfixTest`:
`pagingAdvancesByPageSize`, `sweepKeepsOnlyTasksOfTheDiffedResource`,
`onlyNormalItemsCreatedBySyncAccountAreRaplasOwn`, `foreignItemIsUpToDateOnlyWhenStartEndSubjectMatch`,
`ownerEditAfterRaplaChangeWinsUnlessForced`, `sweepWindowNeverProducesDeletesForOldItems`,
`failingMailboxIsSkippedForAsManySweepsAsItFailed`, `weeklyRepeatingExportsEveryWeekday`.
Live acceptance (production, 2026-09-09): one unwritable calendar stays mapped on purpose;
after deploy one sweep lists its failure in the result mail and still writes the location's
remaining backlog; a rapla appointment created → written by the poll within a minute; owner
move in Outlook → rapla-side change → poll update overwrote the move; rapla delete → next
sweep soft-deleted the owner-modified item.

## Decisions locked

**D1 — rapla's calendar view is defined by rapla's own items.** An item is rapla's iff the
sync account created it and it is not private. If only an owner copy exists, rapla creates
its own item again ("duplicate" by design); owner edits of rapla-created items do not change
ownership. Foreign items are read for comparison and reporting, never written or deleted.

**D2 — Last writer wins on own items.** Replaces "rapla is always master". An owner edit
newer than rapla's change that differs in start/end/subject is kept and reported; a later
rapla change or an explicit resync wins again.

**D3 — Sweep window on both sides, masters by `AppointmentType`.** See § Lessons.

**D4 — Mailbox = failure domain within a run, backoff keyed per mailbox.** One failed attempt
per broken mailbox per sweep; n failures → skipped for n sweeps (max 24).

**D5 — Structural work stays out of the hotfix line.** Per-mailbox locks, task queue as
intents, executor split and connector lifecycle are [PRD 114](../114-exchange-sync-per-mailbox-lock.md).
