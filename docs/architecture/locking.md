# Locking & concurrency

Rapla has **three** lock layers. They are not interchangeable — each
covers a different scope, and a change that needs cross-pod safety
must use a DB-backed one, not the in-process lock.

## The three layers

| Layer | Scope | Backed by | Used for |
|---|---|---|---|
| **Process lock** | One JVM / one pod | `ReentrantReadWriteLock` | Serializing operations inside one operator instance |
| **Resource lock** | Whole cluster | A row in the DB `WRITE_LOCK` table | Per-entity write atomicity; named coordination leases |
| **Global lock** | Whole cluster | The reserved `WRITE_LOCK` row `GLOBAL_LOCK` | Schema (`DynamicType`) changes |

### 1. Process lock — `RaplaLock`

`rapla-core/src/main/java/org/rapla/storage/impl/DefaultRaplaLock.java`
wraps a `java.util.concurrent.locks.ReentrantReadWriteLock`. Read/write,
reentrant (tracked on a `Stack`), with timeouts — read 20 s, write 60 s
by default. Acquired via `writeLockIfLoaded(...)` /
`writeLockIfAvaliable(...)` on the operator
(`AbstractCachableOperator`).

It serializes operations **within a single operator instance** —
dispatch, refresh, disconnect, merge. It is **per-pod**: pod A and pod B
each have their own `RaplaLock`, so it provides **no cross-pod
coordination**. Never rely on it for multi-pod correctness.

### 2 & 3. Resource lock + Global lock — the `WRITE_LOCK` table

`LockStorage` in `rapla-server/src/main/java/org/rapla/storage/dbsql/RaplaSQL.java:575`
manages a DB table `WRITE_LOCK` (`LOCKID` PK, `VALID_UNTIL`, `ACTIVE`,
timestamps). Because it is a shared DB table, it **is cluster-wide** —
every pod contends on the same rows.

- **Lease-based.** A lock has `VALID_UNTIL`; `cleanupOldLocks()`
  deactivates expired rows (`ACTIVE 1→2`). A pod that crashes holding a
  lock self-heals — the lease expires.
- **Resource lock:** `LOCKID` = an entity id. `DBOperator.dispatch` →
  `dbStore` computes lock ids from the stored entity ids
  (`getLockIds`) and calls `requestLocks(...)`
  (`DBOperator.java:777`); released in a `finally` (`removeLocks`).
- **Global lock:** `LOCKID = "GLOBAL_LOCK"`. Taken instead of
  per-entity locks when the event stores a `DynamicType`
  (`needsGlobalLock = containsDynamicType(ids)`) — schema changes
  serialize cluster-wide.
- **Named coordination leases.** `CachableStorageOperator.requestLock(id,
  validMilliseconds)` exposes the same table for arbitrary string ids.
  Background jobs use it as a cluster singleton-job lease —
  `NotificationService` (`NOTIFICATION_LOCK_ID`),
  `SynchronisationManager` (`EXCHANGE_LOCK_ID`). The returned
  `LocalDateTime` doubles as "changes synced up to here."

`FileOperator` (XML backend) is single-pod by nature — two processes
can't share the file — so the process lock alone is sufficient there;
its `requestLock` is effectively degenerate.

## Ordering gotcha — validation runs *before* the lock

In `DBOperator.dispatch` the sequence is:

```
preprocessEventStorage(evt)   DBOperator.java:687  — check(): conflict + permission validation, against the LocalCache
dbStore(...)                  DBOperator.java:698
  requestLocks(...)           DBOperator.java:777  — cluster lock acquired HERE
  store / commit                                  — the write
```

The resource lock is acquired **inside `dbStore`, after**
`preprocessEventStorage`. So conflict detection and permission checks
run against the (possibly stale — see below) `LocalCache`, **not** under
the cluster lock. The lock covers the write and the optimistic
version-check inside `store`, not the check. Cross-pod this is a
time-of-check-to-time-of-use window — see [PRD 035](../prd/done/035-graphql-foundations.md)'s OQ#10 / the
multi-pod-validation discussion for the analysis and the
lock-then-fetch-then-validate fix.

## Gotcha — never fire listener events under a lock

`RemoteOperator` (the client-side operator) carries an intrinsic
`synchronized` monitor *in addition to* its `RaplaLock` — coarse mutual
exclusion on lifecycle / refresh methods (`connect`, `disconnect`,
`refresh`, `isRestartPossible`, the storage-update-listener list, …).

Storage-update events reach listeners via `fireStorageUpdated`. **That
callout must never run while the `RemoteOperator` monitor is held.**
Listeners (`ClientFacadeImpl` → the Swing `Application`) re-enter
arbitrary code — Spring lazy-bean resolution, GUI construction — which
calls back into `synchronized` `RemoteOperator` methods (e.g. a
`RaplaMenuBar` constructor calling `isRestartPossible`). Holding the
monitor across the callout is a lock-order inversion:

- the firing thread holds the `RemoteOperator` monitor and waits for the
  Spring singleton-bean lock;
- a concurrent GUI-bean constructor holds the Spring singleton lock and
  waits for the `RemoteOperator` monitor.

→ deadlock; the Swing client hangs on "loading data". It is a timing
race — most easily triggered by a login that also switches the UI
language, because that persists `org.rapla.language` to the user's
preferences (a client-side store whose `refresh` continuation fires the
update event right as `Application.start` builds the GUI).

**Fix pattern** ([PRD 029](../prd/029-swing-oauth-login.md) Phase 4, 2026-05-18): `refresh(UpdateEvent)` /
`refreshAll()` compute the `UpdateResult` under `synchronized (this)`,
release the monitor, then call `fireStorageUpdated`. A dedicated
`fireLock` keeps the events ordered without putting the `this` monitor
back on the callout path. The no-arg `refresh()` is no longer
`synchronized` — its compute is still serialised by
`refresh(UpdateEvent)`'s own block.

**Rule:** compute under the lock, fire outside it. Never invoke a
listener / observer callback while holding an internal rapla lock.

## Multi-pod note

Rapla can run as multiple pods against one shared store. Each pod keeps
its own in-memory `LocalCache` and reconciles it from the update
history roughly **every 10 s** — so two pods' cached views can diverge
by up to ~10 s. Writes are still serialized by the resource/global
locks; reads from a pod's cache are eventually consistent within that
window.

## See also

- [flows.md](flows.md) — the store/dispatch and refresh-poll flows.
- [conflicts-and-events.md](conflicts-and-events.md) — what conflict
  detection checks (the part that runs pre-lock).
- [permissions.md](permissions.md) — the permission checks in
  `preprocessEventStorage`.
- `AGENTS.md` — deployment topology one-liner.
