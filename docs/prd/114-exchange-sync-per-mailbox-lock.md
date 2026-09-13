# PRD 114 — Exchange sync: per-mailbox locking, task queue as intents, sweep/poll separation

**Status:** in-progress — 2026-09-13 — the 2026-09 production hotfix line (Phase 1 + series/delete semantics, hunks 1–14) is closed in [PRD 115 (done)](done/115-exchange-sync-hotfix-2026-09.md); production runs v11, hunk 14 pending deploy. Open here: Phase 2 (per-mailbox lock), the Phase 2b rest, Phase 3 (executors), Phase 4 (connector lifecycle).
**Related:** [PRD 115](done/115-exchange-sync-hotfix-2026-09.md) (hotfix record + lessons), [PRD 070](070-restore-exchange-connector-wiring.md) (server wiring + scheduler split), [PRD 038](038-graph-calendar-sync.md) (Graph backend, additive), [locking](../architecture/locking.md), [exchange-sync](../architecture/exchange-sync.md) (canonical rules), operational record with site/mailbox specifics: `dhbwrapla/docs/exchange-sweep-analysis-2026-09.md` (gitignored, not in this repo)

## Abstract

A production analysis (2026-09-09, one location's Exchange, read-only) found three independent
reasons why appointments do not reach lecturers' Outlook calendars; two of them (mailbox
mapping, one access-denied mailbox aborting every task of the user) were closed by the hotfix
line in PRD 115. The third remains: the hourly full sweep shares the RxJava computation
workers with the 6-second change poll, so the poll is stalled ~40–60 % of the time, and the
only thing keeping poll and sweep from writing the same item twice is that accident. This PRD
replaces the single global `EXCHANGE` write lock with **per-mailbox locks**, makes the task
queue carry intents only, and separates sweep and poll — so that two writers (poll + sweep, or
two pods) can never create the same appointment twice, and one broken mailbox cannot block
the others.

## Findings that drive this PRD (2026-09-09, production, read-only)

- Only `synchronizeQueue()` (6 s poll) takes the `EXCHANGE` DB lock; `synchronizeMailboxes()`
  (hourly sweep, tens of minutes in prod before the hotfixes, ~90 s after) does **not**. Poll
  and sweep are serialised only by accident: `@Scheduled` ticks are distributed round-robin
  over the four RxJava computation workers and a tick queued behind the sweep's worker stalls
  the poll until the sweep ends (confirmed over ~60 sweep windows; jstack shows idle workers,
  no blocking). Fixing the scheduler without adding locking would re-introduce duplicate
  Exchange items.
- The `EXCHANGE` lock row doubles as the poll's **watermark**: `requestLock` returns the row's
  `LAST_CHANGED` ("changes since"), `releaseLock(id, updatedUntil)` advances it
  (`DBOperator.requestLock/releaseLock`, `RaplaSQL.removeLocks`). This role must stay global.
- `EWSConnector.getService()` builds a new `ExchangeService`/HttpClient per call and never
  closes it — hundreds of open sockets, many in CLOSE-WAIT, on the sync node. Hoisting it out
  of the paging loop (PRD 115 hunk 2) removed the per-page cost; the per-call lifecycle itself
  is Phase 4.
- The task persistence is dead code: `reloadSyncTasks()` is `if (true) return empty`,
  `storeAndRemove` calls are commented out. The `IMPORT_EXPORT` table is neither read nor
  written by the sync loop. Tasks live in a `HashSet` (random order).
- Duplicate Exchange items with the same `raplaId` are leftovers of former series (single
  days of an earlier weekly/daily version keep an old stamp; the master update does not
  remove old occurrences/exceptions) — PRD 115 § Findings. The write path
  (`getExchangeAppointmentByRaplaId`, `ItemView(1)`) always updates the same first item, so
  the stale ones are flagged again every sweep. Systematic fix: Phase 2b.

## Implementation

Mailbox = the fault and concurrency domain.

- **Lock roles.** Keep the `EXCHANGE` lock **only** as watermark/queue cursor: the poll takes
  it, reads the update history, enqueues tasks, releases immediately. No Exchange write under
  it. Every Exchange write happens under a **per-mailbox lock** (`requestLock(<person
  resource id>)`, the existing resource-lock path `LockStorage.getLocks(ids, …)`; rows are
  auto-created, expire via `VALID_UNTIL`, cleaned by `cleanupOldLocks`). A writer that cannot
  get a mailbox lock skips that mailbox in this tick and retries next tick.
- **Execution grouped by mailbox.** Tasks are grouped by `(mailbox)`; per mailbox: lock →
  all tasks of that mailbox → release. An access-denied mailbox fails as a unit and is
  reported as such (the per-run failure domain + `FailureBackoff` of PRD 115 hunks 3/9 are
  the interim form). Only an HTTP 401 on the sync account's own credentials aborts the user
  (existing password-mail path stays).
- **Tasks are intents.** A task is `(user, appointmentId, mailbox, status)`; the executor
  re-resolves the appointment at execution time (already the case). Two gaps become
  `toDelete` instead of silent discard: appointment no longer resolvable, and mailbox no
  longer allocated on the appointment. One task per `(appointmentId, mailbox)` key (the
  existing set-merge stays).
- **Idempotent write.** Under the mailbox lock: `findItems(raplaId)`; if the item exists with
  the current `raplaLastUpdate` → no-op; else create/update. This makes poll+sweep duplicates
  and re-queued tasks harmless. The ownership / foreign-item / last-writer rules of PRD 115
  (canonical in [exchange-sync](../architecture/exchange-sync.md)) apply unchanged inside it.
- **Sweep = producer, poll = consumer (optional, recommended).** The sweep computes the diff
  and enqueues; only the poll executes. Sweep may then run on its own single-thread
  executor with any duration. Per-mailbox locks make this safe across pods as well.
- **`refreshMailbox()` `synchronized`** on the manager monitor goes; serialise per user if
  needed.
- **Connector lifecycle:** reuse one `ExchangeService` per user connect and close it when the
  connector is replaced; HttpClient connection-request + socket timeouts.

Deployment note: production today runs the **legacy Jetty WAR**; the classes above exist in
both trees with the same logic. Changes land in rapla (`rapla-server`) first; back-port to
the legacy tree is the customer deployment's decision.

## Backport to `master` (legacy production line)

Production runs the legacy WAR built from `master`; our work happens on `spring-boot`. The
three classes differ file-wide (Date→LocalDateTime, logger migration) but every spot this
PRD touches exists identically in both trees, so each fix is classified by how mechanically
it transfers. "Simple" = a few lines in one method, no new types, no scheduler/DI change,
`git diff` of the hunk applies with path adjustment. (The Phase-1 rows moved to PRD 115 —
all done.)

| Fix | spring-boot | master | Backport |
|---|---|---|---|
| Reuse one `ExchangeService` per user connect, close on replace; HttpClient timeouts (Phase 4) | `EWSConnector.getService`, `connectMap` replace | same | **medium** — touches every `getService()` caller, but no scheduler/DI |
| Per-mailbox lock + grouped execution + idempotent write (Phase 2) | `processTasks`/`executeTasks` | same methods | **medium** — logic only; `requestLock(id)` exists on `CachableStorageOperator` in both |
| Task gaps → `toDelete` (Phase 2) | `processTasks` | same | **simple** |
| Separate executors for sweep/poll (Phase 3) | `ExchangeSchedulerTrigger` (`@Scheduled`) | `CommandScheduler.schedule(…)` | **not transferable as-is** — different scheduling stacks; on master give the sweep its own single-thread executor, on spring-boot configure a multi-thread `TaskScheduler` or a dedicated executor |
| Producer/consumer (sweep enqueues only) | manager | same | **medium** — logic only, but depends on Phase 2 |

Scheduling difference worth knowing before Phase 3: master runs both jobs on the legacy
`CommandScheduler` = RxJava computation pool (4 workers, round-robin — the poll stall
measured in prod). `spring-boot` uses `@EnableScheduling`
(`RaplaServerAutoConfiguration`) with Spring Boot's **default single-thread**
`TaskScheduler` (`spring.task.scheduling.pool.size` is not configured) — there a long sweep
blocks the poll **and every other `@Scheduled` job** (notification, external syncs) for its
whole duration. Phase 3 is therefore more urgent on `spring-boot` than on master.

## Goal

- Poll ticks continue to be logged (`Update triggered`) during a running sweep.
- Two concurrent executors (poll + sweep, or two managers on one store) writing the same
  `(appointment, mailbox)` produce exactly one Exchange item (test against a fake
  `ExchangeService`).
- No task of user U is skipped because a *different* mailbox of U is not writable — already
  true per run since PRD 115 hunk 3; Phase 2 makes it structural (lock/execute/release per
  mailbox).

## Scope

### In scope
- `SynchronisationManager` (`synchronizeQueue`, `synchronizeMailboxes`, `processTasks`,
  `executeTasks`), `ExchangeSchedulerTrigger` executors, `EWSConnector.getService()`
  lifecycle.
- Lock usage via the existing `CachableStorageOperator.requestLock/releaseLock`.

### Out of scope
- Everything shipped in the 2026-09 hotfix line — [PRD 115](done/115-exchange-sync-hotfix-2026-09.md).
- Alias→primary SMTP resolution for the mapping (data fix via `exchangeMailbox`; a
  `resolveName`-based fallback is a separate small PRD if wanted — OQ3).
- Reviving task persistence (`IMPORT_EXPORT`) — not needed while the sweep runs on the single
  sync deployment (PRD 070 gate).
- Graph backend (PRD 038).
- Subject/body via document templates — recorded as an idea in [PRD 097](097-event-html-templates-mustache.md) § "Idea — Exchange subject and body from document templates".

## Plan

### Phase 1 — stop the bleeding — DONE, see PRD 115
All six boxes (access-denied break, paging, per-run failure domain, sweep task filter,
`FailureBackoff`, sync-result mail listing unmapped calendars — `SynchronisationManager`
resync path) shipped 2026-09-09 in both trees.

### Phase 2 — per-mailbox lock + grouped execution
- [ ] Group tasks by mailbox; lock/execute/release per mailbox.
- [ ] Idempotent write (`findItems(raplaId)` + `raplaLastUpdate` compare) under the lock.
- [ ] Task gaps → `toDelete` (appointment gone / mailbox no longer allocated).

### Phase 2b — series & delete semantics, open rest (the settled parts are PRD 115 hunks 7/10/11/12/13/14)
- [ ] Dedupe on write: when `findItems(raplaId)` returns more than one item, keep the one with
  the current stamp, soft-delete the rest (symptom repair, independent of the rest).
- [ ] Series shrink/reshape: audit `AppointmentSynchronizer` for what happens to old
  occurrences/exceptions when a recurring appointment becomes shorter or single — today they
  survive as separate items with the old stamp (the duplicate class of PRD 115 § Findings).
- [ ] Delegate-safe delete: `MoveToDeletedItems` fails in a foreign mailbox ("Object cannot be
  deleted"); `SoftDelete` is in use for stale items (PRD 115 hunk 5); still open: inspect
  every `deleteItems` response (ews-java-api 2.0 collection can iterate empty — per-item
  `Item.delete` reports errors reliably), and the one series update that fails with "Das
  Objekt kann nicht gelöscht werden" in `removeRecurrenceExceptions` after re-creation.
- [ ] Series removed in rapla whose first occurrence lies before the sweep window is only
  removed by the poll (PRD 115 § Lessons) — decide whether the sweep needs an "orphan by
  raplaId" pass that ignores the window.

### Phase 3 — separate executors
- [ ] Sweep on its own single-thread executor; poll keeps `@Scheduled`.
- [ ] `EXCHANGE` lock reduced to watermark/cursor role.
- [ ] Sweep enqueues only (producer/consumer) — decide in OQ2.

### Phase 4 — connector lifecycle
- [ ] One `ExchangeService` per user connect, closed on replace; HttpClient connection
  request + socket timeouts.

## Tests

- Tier 2 (`rapla-server`, `FacadeTestSupport`) with a hand-rolled `ExchangeService` double
  (external type — allowed under §13): idempotent write, concurrent writers → one item,
  task-gap → delete, dedupe on write.
- Existing `ExchangeSchedulerTriggerConditionTest` and `SynchronisationManagerHotfixTest`
  (PRD 115) stay green.

## Open Questions

- **OQ1** — Lock validity for a mailbox batch: fixed (e.g. 60 s) with renewal every N tasks,
  or per-task lock? *Resolution:* pending.
- **OQ2** — Sweep enqueues only (poll executes) vs. sweep executes under mailbox locks itself.
  *Resolution:* pending; per-mailbox locks make both safe, producer/consumer is simpler to
  reason about.
- **OQ3** — Should unmapped shared calendars be auto-mapped via `resolveName(alias)` →
  primary SMTP at sweep time? *Resolution:* pending (separate PRD candidate). The result mail
  already lists them (PRD 115 hunk 6).

## Decisions locked

**D1 — Per-mailbox locks, not one global write lock.** The mailbox is the natural unit of
both failure and concurrency; a global lock forces serialising the sweep against the
6-second poll. Alternatives rejected: single-writer-only without locks (unsafe across
pods/restart overlap); global lock held per batch (still stalls the poll for the batch).

**D2 — `EXCHANGE` lock stays as the poll watermark.** Its `LAST_CHANGED` is the
"changes since" cursor; per-mailbox locks do not replace that role.

**D3 — Tasks carry intents only.** The executor re-reads the current appointment; stale
tasks cannot write stale content. Gaps become deletes rather than silent discards.

**D4 — Ownership, foreign items, last writer wins, sweep window, backoff granularity** are
settled in [PRD 115](done/115-exchange-sync-hotfix-2026-09.md) D1–D4 and documented in
[exchange-sync](../architecture/exchange-sync.md); this PRD builds on them and does not
reopen them.
