# Exchange sync (EWS connector) — how it works and what Exchange does to it

Server-side plugin `org.rapla.plugin.exchangeconnector` (`rapla-server`). It mirrors rapla
appointments into the Outlook calendars of persons, using one Exchange *sync account* per
location that has the persons' calendars shared to it. Everything below was established or
verified on a production system on 2026-09-09 — see
[PRD 115](../prd/done/115-exchange-sync-hotfix-2026-09.md) for the analysis and the hotfixes,
[PRD 114](../prd/114-exchange-sync-per-mailbox-lock.md) for the open phases, and [PRD 070](../prd/070-restore-exchange-connector-wiring.md) for the
Spring wiring.

## Moving parts

| Part | Class | Cadence | Does |
|---|---|---|---|
| **Poll** | `SynchronisationManager.synchronizeQueue()` | every 6 s | reads the rapla update history since a watermark, turns changed/removed reservations into tasks, executes them |
| **Sweep** | `SynchronisationManager.synchronizeMailboxes()` | hourly | for every sync account: refresh the shared-calendar list, then per calendar diff rapla appointments against the rapla-tagged Exchange items and enqueue create/update/delete tasks; executes them at the end |
| **Task** | `SynchronizationTask` | — | intent `(user, appointment, resource/mailbox, status)`; the executor re-reads the appointment, so a stale task cannot write stale content |
| **Writer** | `AppointmentSynchronizer` | per task | `findItems(raplaId)` → update the found item or create a new one; delete by rapla id |
| **EWS** | `EWSConnector` | per call | `ExchangeService` with the sync account's credentials; `loadMailboxes()` discovers the shared calendars from the account's Outlook "Common Views" links |

**Watermark.** The DB write lock `EXCHANGE` is not only a mutex: `requestLock` returns its
`LAST_CHANGED` ("changes since") and `releaseLock(id, updatedUntil)` advances it. Only the poll
takes it; the sweep does not — poll and sweep are serialised by nothing but the shared RxJava
computation workers (see [locking](locking.md) and PRD 114 Phase 2/3).

**Task persistence is switched off.** `reloadSyncTasks()` returns an empty set and
`storeAndRemove` is commented out; the `IMPORT_EXPORT` rows of external system `exchange` are
legacy and are neither read nor written. Consequences: retries do not survive a sweep, the
sweep recomputes the full diff every hour, and anything that relied on stored tasks (the poll's
delete path before hunk 8) silently does nothing.

## How rapla marks its items

Every item rapla writes carries three extended properties in `DefaultExtendedPropertySet.Appointment`:
`raplaId` (the rapla appointment id), `isRaplaMeeting` (marker used for the sweep's search) and
`raplaLastUpdate` (the reservation's `lastChanged`, the sweep's "is it stale" stamp). The sweep
finds items with `Exists(isRaplaMeeting)`, the writer with `IsEqualTo(raplaId)`.

Owner actions in Outlook never touch these properties — but an **Outlook copy of a rapla item
keeps them**, so a copy looks like a rapla item to every search.

## Mapping mailbox → person

`getMailbox(allocatable)` keys on the Person's `exchangeMailbox` attribute, else the attribute
annotated as e-mail, else `email`, lowercased. Exchange resolves a shared calendar to the
mailbox's **primary SMTP address**; if the person record holds an alias form (or an address fed
by an HR import that differs from the primary SMTP), the calendar is skipped with
`Resource for mailbox X not found  Skipping mailbox`. Fix: set `exchangeMailbox` to the primary
SMTP (imports may overwrite `email`, they do not touch `exchangeMailbox`).

## What Exchange does — facts that cost a day to learn

- **Share level.** The sync account needs at least "Author" (create + read, edit/delete own
  items), preferably "Editor". Read it from `FolderSchema.Permissions`;
  `FolderSchema.EffectiveRights` shows only `CreateContents, Read` for any delegate and is
  **not** informative. A calendar shared as "Reviewer" fails every write with
  `Access is denied … Cannot get ID from name`.
- **Private items.** An item the owner marked private (`PR_SENSITIVITY = 2`) cannot be
  updated or deleted by a delegate, regardless of share level; Exchange reports
  `ErrorItemNotFound` ("The specified object was not found in the store") for
  Soft/HardDelete and `ErrorCannotDeleteObject` for MoveToDeletedItems, although
  `FindItems`/`GetItem` still return it and `PR_ACCESS` claims Modify+Delete. Only the owner
  (or an admin) can remove it.
- **Owner edits are fine.** Moving, editing or sending a rapla item as meeting changes
  `PR_LAST_MODIFIER_NAME`/sender to the owner but leaves it writable for the delegate
  (verified live). `PR_SENDER_NAME`/organizer of a rapla-created item is the mailbox owner by
  design — not a sign of takeover.
- **Owner copies.** `PR_CREATOR_NAME` (MAPI 0x3FF8) is set once at creation and survives edits;
  a copy has the copying owner as creator. This is the only reliable discriminator without
  persistence.
- **Delete modes.** `MoveToDeletedItems` needs the *owner's* Deleted Items folder → fails for
  delegates; `HardDelete` was the legacy default; `SoftDelete` (recoverable via "Recover
  deleted items") works and is what rapla uses now.
- **`ErrorItemNotFound` is also a permission answer** — Microsoft documents it as "you do not
  have access to the item"; never read it as "item is gone".
- **Paging.** `ItemView(pageSize, offset)` offsets count items; a full page advances by the page
  size. A page size of 5 with `offset++` costs n−4 round-trips for n items and returns every
  item ~5 times.
- **Shape changes by the owner.** If the owner turns rapla's recurring item into a single
  multi-day block, an `update()` that re-applies the recurrence is rejected ("duration between
  startTime and endTime of the recurrence is greater than the minimum duration between two
  occurrences") — the item must be deleted and re-created, not updated (PRD 114 Phase 2b).
- **`FindItems` result objects** carry `FirstClassProperties` only; extended properties must be
  requested in the `PropertySet`, and `deleteItems` responses of ews-java-api 2.0 can iterate
  empty — per-item `Item.delete` reports errors reliably.

## Decision rules in the writer and the sweep (since hotfix v6/v7)

An Exchange item is **rapla's own** iff the sync account created it (`PR_CREATOR_NAME`
matches the login's local part, or no creator property = legacy item) **and** it is not private.
Everything else is `foreign` — read, never written, never deleted, never counted as present.

For one rapla appointment in one calendar:
1. own item exists → **last writer wins**: if the owner modified it after rapla's last change,
   rapla leaves it alone and reports it (an explicit resync forces the write); otherwise rapla
   updates it — and re-creates it when the recurrence shape differs (Exchange rejects a
   series update on an item the owner turned into a single block);
2. no own item, a foreign item with the same **start, end and subject** → nothing to do
   (the owner's private/copied version is up to date);
3. otherwise → create rapla's own item (next to the owner's foreign one if there is one).

Rule 2/3 is the same code in the poll (`addOrUpdate`) and the sweep. The sweep logs one INFO line
per mailbox for case 2 and a WARN per appointment for case 3; the resync result mail lists both
plus every shared calendar with its mapping state.

Sweep window: `exch-sync-past` (default 30 days, read once at start — a change needs a restart)
bounds the sweep on both sides. The rapla side is queried from `today − exch-sync-past` with
overlap semantics (a series counts while any occurrence lies in the window). The Exchange side
uses the item's `End`, which on a **recurring master is the end of the first occurrence** — and
`IsRecurring` is *false* on a master (it flags occurrences), so a master is recognised by
`AppointmentType == RecurringMaster`. Two asymmetric rules follow:
- **compare side:** an item ending before the window is not compared — except a recurring
  master, which is always compared (its series may still run; otherwise every long-running
  series would be re-tasked each sweep);
- **delete side:** an item whose (first) end lies before the window is never deleted, master
  or not. An item that ends inside the window always overlaps it, so its rapla appointment —
  if it still exists on this person — is in the windowed rapla set; deletes therefore hit only
  true orphans. Everything older is left alone (a series removed in rapla whose first occurrence
  is old is only removed by the poll).
- A safety cap (`MAX_DELETES_PER_MAILBOX_PER_SWEEP`, 50) drops a calendar's deletes with a WARN.
Getting one of these wrong deletes history: a window applied only to the compare side, or a
master judged by its first end, both turned past items into "only in Exchange" (PRD 114).
The poll is not windowed.

Failure handling: an access-denied on one calendar marks only that calendar's tasks failed in
the run (`processTasks` skips the rest of that resource; only HTTP 401 of the sync account aborts
the user), a `FailureBackoff` skips a calendar for as many sweeps as it has failed (max 24), a
delete failure is rethrown so it reaches the result mail instead of being logged as done.

## Diagnosing without writing

Read-only probes (EWS `findItems`/`bind` with the sync account + `SELECT`s against the store)
reproduce the sweep's diff per calendar and dump the MAPI properties of single items; the
deployment repository carries them as a skill. When a calendar "does not sync", check in this
order: mapping (`Skipping mailbox`), share level (`Permissions`), then the item itself
(`PR_SENSITIVITY`, `PR_CREATOR_NAME`).

## Smaller rules from the 2026-09 hotfix line (PRD 115)

- **EWS paging:** `ItemView(pageSize, offset)` takes an *item* offset — advance by
  `pageSize`, never by one; page size 100. One `ExchangeService` per calendar read, hoisted
  out of the paging loop (per-page reconnects were the read-timeout source).
- **Poll delete path:** a removed reservation yields its delete tasks from the reservation
  itself (`updateOrCreateTasks` + `toDelete`) — never from persisted tasks, which are
  disabled. Without this the poll never deletes and only the hourly sweep does.
- **Weekly series with several weekdays:** `WeeklyPattern` gets all of
  `Repeating.getWeekdays()` (SUNDAY=1..SATURDAY=7 → `DayOfTheWeek.values()[w-1]`), not just
  the start's weekday. Series already in Exchange keep their stamp and are rewritten only
  after a resync or an edit.
- **Sync-account identity:** the creator/modifier name Exchange reports is the display-name
  form (spaces); the login is the local part (dots) — compare normalised
  (`isSyncAccountName`).
- **Sweep task scope:** the per-mailbox sweep filters `updateOrCreateTasks(appointment)` to
  the allocatable whose calendar was just diffed; the unfiltered helper is the poll's
  "update every participant" path and, reused in the sweep, rewrites shared appointments
  into every co-participant's calendar every hour.
