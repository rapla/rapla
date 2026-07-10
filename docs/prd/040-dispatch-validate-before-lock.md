# PRD 040: Dispatch validates against a stale cache before locking

**Status:** draft
**Date:** 2026-05-15

## Goal

Close a multi-pod correctness window in the dispatch path: conflict detection and permission checks run against the per-pod `LocalCache` — which lags up to ~10 s behind other pods — **before** the cluster `WRITE_LOCK` is acquired. Validation should run **under the lock, against fresh store state**.

Surfaced during the [PRD 035](done/035-graphql-foundations.md) design review (OQ#10); independent rapla issue, unrelated to GraphQL/MCP.

## Problem

In `DBOperator.dispatch` (`rapla-server/.../storage/dbsql/DBOperator.java:681`):

```
preprocessEventStorage(evt)   line 687  — check(): conflict + permission validation
dbStore(...)                  line 698
  tryResolve(oldEntity)       line 761  — old entities resolved from LocalCache
  requestLocks(...)           line 777  — cluster WRITE_LOCK acquired HERE
  store / commit                        — the write
```

`preprocessEventStorage` → `check()` validates against `EntityStore(cache)` — the in-memory `LocalCache`. Each pod reconciles its cache from the update history only **every ~10 s** (`docs/architecture/locking.md`). The cluster `WRITE_LOCK` is acquired later, inside `dbStore`. So **validation runs pre-lock, against a cache up to ~10 s stale**. Two consequences across pods:

1. **Silently missed conflict.** Pod A books Room 101 at 14:00; within 10 s pod B books Room 101 at 14:00. B's conflict detection runs on a cache that hasn't seen A's reservation → reports no conflict → both commit. *Mitigating factor:* rapla conflicts are advisory and recomputed — once both reservations land in every cache (≤10 s), the conflict resurfaces to anyone viewing the resource. Missed *at-save-time warning*, not permanent data loss.
2. **Stale permission honored.** A user's write permission to a resource is revoked on pod A. For up to ~10 s pod B still validates against the old permission and lets the write through. **Not advisory, never self-corrects.** The genuine defect.

What *is* already correct: the optimistic version check inside `raplaSQLOutput.store` runs under the lock against the DB, so lost-update of an *existing* entity is caught. Only conflict and permission checks are exposed.

`FileOperator` (XML backend) is single-pod → unaffected.

## Severity

Double-booking window (#1) is a UX degradation — saving user misses an immediate warning, conflict not lost. Permission window (#2) is a real, non-self-correcting authorization gap. "Permission changes are not instantaneous across a cluster" is a common, often-accepted distributed-systems property (cf. JWT valid until expiry) — whether #2 crosses from *known weakness* to *must-fix* is a deployment threat-model decision. This PRD documents it and offers the fix; adoption is the maintainer's call.

## Scope

**In scope:** reorder `DBOperator.dispatch` so the cluster lock is acquired *before* validation, with a fresh history read under the lock:

```
acquire cluster WRITE_LOCK   (resource ids + global lock as today)
fetch fresh state from history under the lock
preprocessEventStorage / check()   — now against fresh state
store / commit
release lock
```

The lock primitive already exists (`requestLocks` / the `WRITE_LOCK` table) — this is a **reordering**, not new infrastructure. Lock acquisition is fail-fast (`activateLocks` throws on contention), so the reorder introduces **no deadlock risk** — worst case a lock-acquisition failure that the caller retries.

**Out of scope:**

- `FileOperator` — single-pod, no window.
- A general distributed-cache redesign. The ~10 s history-poll model stays; this PRD only fixes the dispatch validation path.
- Conflict *severity* changes — conflicts stay advisory.

## Plan

1. Determine lock-id set *before* `preprocessEventStorage`. Today `getLockIds` derives from event's stored entity ids; `needsGlobalLock` from `containsDynamicType` — both computable up front from `UpdateEvent`. No new information needed.
2. Acquire locks first; on contention, fail fast and let caller retry the whole dispatch.
3. Refresh from history under the lock (machinery already runs post-write as `readRefreshInfoFromDb` + `refreshWithoutLock` — this moves/adds a refresh *before* validation).
4. Run `preprocessEventStorage` / `check()` against refreshed state.
5. Store, commit, release as today.
6. Measure lock hold-time impact (validation now inside critical section — see Open Questions).

## Tests

- **Tier 2/3** — two operators over one shared DB (or two-instance harness): revoke a user's write permission via operator A, then attempt a write as that user via B → must be rejected, not honored on stale cache.
- **Tier 2/3** — operator A books Room 101 at 14:00; B books same → B's dispatch surfaces conflict at save time (not only on later refresh).
- Regression: single-pod `FileOperator` dispatch unchanged.

## Open Questions

1. **Lock hold time.** Validation moves *inside* the critical section, so cluster lock held longer per dispatch → more contention, more fail-fast retries under load. For rapla's write volume (university scheduling) likely negligible — measure in step 6. If it bites, scope locks finer (per-allocatable) — adds lock-ordering complexity.
2. **What to lock for a *create*'s conflict check.** A new reservation's conflict check touches *other* reservations on the allocatables it books. Locking only the new reservation id is insufficient — two concurrent creates booking the same room lock disjoint ids. Conflict check needs **allocatable ids** in the lock set too. Decide whether `getLockIds` should include referenced allocatables.
3. **Refresh granularity.** Full history refresh on every dispatch vs scoped refresh of only touched entities + their allocatables + conflicting reservations in the window. Full is simpler; scoped is cheaper. Lean: full for v1 (post-write refresh already does it), optimise if step 6 shows a problem.

## Cross-references

- [`docs/architecture/locking.md`](../architecture/locking.md) — three lock layers and validate-before-lock gotcha.
- [PRD 035 (done) — GraphQL foundations](done/035-graphql-foundations.md) — OQ#10; issue surfaced during that design review.
- `docs/architecture/conflicts-and-events.md` — what `check()` validates.
- AGENTS.md — deployment topology (multi-pod).
