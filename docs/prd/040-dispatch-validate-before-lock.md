# PRD 040: Dispatch validates against a stale cache before locking

**Status:** draft
**Date:** 2026-05-15

## Goal

Close a multi-pod correctness window in the dispatch path: conflict
detection and permission checks run against the per-pod `LocalCache` —
which lags up to ~10 s behind other pods — **before** the cluster
`WRITE_LOCK` is acquired. Validation should run **under the lock,
against fresh store state**.

Surfaced during the PRD 035 design review (see PRD 035 OQ#10); it is an
independent rapla issue, unrelated to GraphQL/MCP.

## Problem

In `DBOperator.dispatch` (`rapla-server/.../storage/dbsql/DBOperator.java:681`):

```
preprocessEventStorage(evt)   line 687  — check(): conflict + permission validation
dbStore(...)                  line 698
  tryResolve(oldEntity)       line 761  — old entities resolved from LocalCache
  requestLocks(...)           line 777  — cluster WRITE_LOCK acquired HERE
  store / commit                        — the write
```

`preprocessEventStorage` → `check()` validates against
`EntityStore(cache)` — the in-memory `LocalCache`. Each pod reconciles
its cache from the update history only **every ~10 s**
(`docs/architecture/locking.md`). The cluster `WRITE_LOCK` is acquired
later, inside `dbStore`. So **validation runs pre-lock, against a cache
up to ~10 s stale**. Two consequences across pods:

1. **Silently missed conflict.** Pod A books Room 101 at 14:00; within
   10 s pod B books Room 101 at 14:00. B's conflict detection runs on a
   cache that has not yet seen A's reservation → reports no conflict →
   both commit. *Mitigating factor:* rapla conflicts are advisory and
   recomputed — once both reservations land in every cache (≤10 s), the
   conflict resurfaces to anyone viewing the resource. So this is a
   missed *at-save-time warning*, not permanent data loss.
2. **Stale permission honored.** A user's write permission to a
   resource is revoked on pod A. For up to ~10 s pod B still validates
   against the old permission and lets the write through. **Not
   advisory, never self-corrects.** This is the genuine defect.

What *is* already correct: the optimistic version check inside
`raplaSQLOutput.store` runs under the lock against the DB, so
lost-update of an *existing* entity is caught. Only the conflict and
permission checks are exposed.

`FileOperator` (XML backend) is single-pod by nature → unaffected.

## Severity

The double-booking window (#1) is a UX degradation — the saving user
misses an immediate warning, but the conflict is not lost. The
permission window (#2) is a real, non-self-correcting authorization
gap. "Permission changes are not instantaneous across a cluster" is a
common, often-accepted distributed-systems property (cf. JWT valid
until expiry) — so whether #2 crosses from *known weakness* to
*must-fix* is a deployment threat-model decision. This PRD documents it
and offers the fix; adoption is the maintainer's call.

## Scope

**In scope:** reorder `DBOperator.dispatch` so the cluster lock is
acquired *before* validation, with a fresh history read under the lock:

```
acquire cluster WRITE_LOCK   (resource ids + global lock as today)
fetch fresh state from history under the lock
preprocessEventStorage / check()   — now against fresh state
store / commit
release lock
```

The lock primitive already exists (`requestLocks` / the `WRITE_LOCK`
table) — this is a **reordering**, not new infrastructure. Lock
acquisition is fail-fast (`activateLocks` throws on contention), so the
reorder introduces **no deadlock risk** — worst case a lock-acquisition
failure that the caller retries.

**Out of scope:**

- `FileOperator` — single-pod, no window.
- A general distributed-cache redesign. The ~10 s history-poll model
  stays; this PRD only fixes the dispatch validation path.
- Conflict *severity* changes — conflicts stay advisory.

## Plan

1. Determine the lock-id set *before* `preprocessEventStorage`. Today
   `getLockIds` derives it from the event's stored entity ids, and
   `needsGlobalLock` from `containsDynamicType` — both already
   computable up front from the `UpdateEvent`. No new information
   needed.
2. Acquire the locks first; on contention, fail fast and let the caller
   retry the whole dispatch.
3. Refresh from the history under the lock (the machinery already runs
   post-write as `readRefreshInfoFromDb` + `refreshWithoutLock` — this
   moves/adds a refresh *before* validation).
4. Run `preprocessEventStorage` / `check()` against the refreshed
   state.
5. Store, commit, release — as today.
6. Measure lock hold-time impact (validation now inside the critical
   section — see Open Questions).

## Tests

- **Tier 2/3** — two operators over one shared DB (or a two-instance
  harness): revoke a user's write permission via operator A, then
  attempt a write as that user via operator B → must be rejected, not
  honored on the stale cache.
- **Tier 2/3** — operator A books Room 101 at 14:00; operator B books
  the same → B's dispatch surfaces the conflict at save time (not only
  on a later refresh).
- Regression: single-pod `FileOperator` dispatch behaviour unchanged.

## Open Questions

1. **Lock hold time.** Validation moves *inside* the critical section,
   so the cluster lock is held longer per dispatch → more contention,
   more fail-fast retries under load. For rapla's write volume
   (university scheduling) likely negligible — measure in step 6. If it
   bites, scope locks finer (per-allocatable) — adds lock-ordering
   complexity.
2. **What to lock for a *create*'s conflict check.** A new reservation's
   conflict check touches *other* reservations on the allocatables it
   books. Locking only the new reservation id is insufficient — two
   concurrent creates booking the same room lock disjoint ids. The
   conflict check needs the **allocatable ids** in the lock set too.
   Decide whether `getLockIds` should include referenced allocatables.
3. **Refresh granularity.** A full history refresh on every dispatch vs.
   a scoped refresh of only the touched entities + their allocatables +
   conflicting reservations in the window. Full is simpler; scoped is
   cheaper. Lean: full for v1 (the post-write refresh already does it),
   optimise if step 6 shows a problem.

## Cross-references

- [`docs/architecture/locking.md`](../architecture/locking.md) — the
  three lock layers and the validate-before-lock gotcha.
- [PRD 035](035-rapla-mcp-server.md) — OQ#10; this issue was surfaced
  during that design review.
- `docs/architecture/conflicts-and-events.md` — what `check()`
  validates.
- AGENTS.md — deployment topology (multi-pod).
