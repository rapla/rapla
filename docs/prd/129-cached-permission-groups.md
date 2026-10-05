# PRD 129 — Cached permission groups

**Status:** implemented — 2026-10-05 (reviewed by rapla-review, review round + F1/F2/delete-test built by rapla-impl2, all lanes green, full `mvn test` green 2026-10-05 01:12, committed 0d3467cf3, on dhbw-test since 2026-10-05 02:22)
**Related:** [PRD 090](090-additive-permission-resolution.md) (additive `hasAccess`), [PRD 082](082-storage-memory-model.md) / [PRD 083](083-user-change-subscription.md) (`PermissionIndex` — cached `canRead` on allocatables, same invalidation seam), [PRD 128](128-picker-conflicts-requests-chip-model.md) (needs fast `canModify` for conflicts and requests), [permissions.md § 5](../architecture/permissions.md#5-read_no_allocation--resource-visibility-vs-booking-visibility-verified-2026-06-24)

## Abstract

Every permission check recomputed the caller's groups including their parent chain (`UserImpl.getGroupsIncludingParents`), at least twice per check (`canReadType` → `matchesAccessLevel`, then `RaplaDefaultPermissionImpl.hasAccess`). For a location admin with 30–45 groups on a production-sized store this is **91 % of the pure permission time**. The server operator now caches the groups per resident user, invalidated per user on a user change and completely on a category change; query and write paths (GraphQL, REST, Swing via the server, `SecurityManager`) use the same cache. On the same data the pure permission time drops **8–13×**. A cache of resource × user rights adds little on top and is not proposed.

## Measurement (2026-10-04)

Method: a standalone harness (not in the repo) loads a copy of a production-sized store (tens of thousands of resources and reservations) into a `FileOperator` in its own JVM and times only `PermissionController` calls for three location admins (non-super-admins with group-admin rights, 32–43 groups each). Warm JIT, median of 7 runs. async-profiler (itimer) attributes the time.

Profile before (shared controller): 91 % in `getGroupsIncludingParents` — of that 46 % `CategoryImpl.getParent`, 19 % `UserImpl.getGroupList`, 11 % `HashSet.add`. The permission-row walk itself (`getUserEffect`) is 4 %. Making the computation itself cheaper does not help: calling `getParent` once per step instead of twice saves 0–8 %; one computation costs 2.7–3.8 µs, of which the parent walk is about 80 %.

| Workload (per user, one "request") | Before | Operator cache | Factor |
|---|---:|---:|---:|
| Resource list: `canRead` + `canModify` + `canAdmin` over all resources | 440–580 ms | 34–53 ms | 11–13× |
| Month view (~3 500–20 000 checks): `canRead` + `canModify` per reservation | 20–95 ms | 1.5–10.6 ms | 9–13× |
| Week view: same per reservation | 10–46 ms | 0.9–6.1 ms | 7–11× |
| Conflicts: `canRead` both reservations + resource | 8–49 ms | 0.5–4.3 ms | 11–15× |
| Open requests (store had only 15 reservations with a request) | ~0.2 ms | ~0.03 ms | ~7× |

Per check: 3.7–5.7 µs → 0.38–0.66 µs. Not measured: the share of permission time in a whole GraphQL response; `getConflictsSync` itself (131–138 ms) contains further checks that were not broken down.

## Implementation

- `StorageOperator.getGroupsIncludingParents(User)` — default method, computes as before (Swing client, other operators unchanged).
- `LocalAbstractCachableOperator` overrides it with `readmodel.UserGroupsCache`: entries keyed by user id and served only for the **exact resident instance** they were computed for (`tryResolve(user.getReference()) == user`). A changed user is a new resident instance, an unstored draft is never resident — both are computed fresh, never served stale. Values are immutable sets.
- Invalidation at the existing read-model seam `updateReadModel` (local writes and other pods' changes from the update history both pass through `refresh` → `updateIndizes` → `updateReadModel`): a **user** change or removal drops only that user's entry; a **category** add/change/removal drops all entries and bumps a generation, so a computation that raced with the change is not stored. Resource and type changes leave the cache alone.
- `PermissionController.groupsOf(user)` asks the operator; both private `hasAccess` overloads pass the groups to the new default overload `PermissionExtension.hasAccess(…, Collection<String> groups)`. `RaplaDefaultPermissionImpl` uses them; other extensions (e.g. a deployment rights plugin) inherit the default, which ignores them and computes as before.
- AGENTS §16: opaque internal cache, no observable state change.

## Relation to the PermissionIndex (PRD 082 #8 / PRD 083 Part A)

`readmodel.PermissionIndex` caches, per user id, the ids of the allocatables the user can read (`canRead`) and can only see (`canReadInformation && !canRead`). It is built by iterating all allocatables through the real `PermissionController` and is used by every GraphQL request (`RequestContextInstrumentation` → `RequestCtx.readableAllocatableIds`) when `rapla.readmodel.authoritative` is on (default).

- **Invalidation — the same seam, `updateReadModel`** (local writes and other pods' changes via `refresh` → `updateIndizes`):

  | Change | PermissionIndex | UserGroupsCache |
  |---|---|---|
  | User added / changed / removed (group membership, admin flag) | that user's entry | that user's entry |
  | Category added / changed / removed (group hierarchy) | all | all |
  | Allocatable added / changed / removed (its permissions, owner, type) | all | — (groups do not depend on it) |
  | DynamicType added / changed / removed (type-level read) | all | — |

  Within one update the group cache is cleared before the index, so an index entry rebuilt afterwards already sees the new groups. The index cannot hold an answer computed from stale groups that the group cache has already dropped: both are dropped by the same change, groups first.
- **Races — both safe, by different means.** `UserGroupsCache` keeps a computation that started before an invalidation out of the cache (generation). `PermissionIndex` builds inside `ConcurrentHashMap.computeIfAbsent`, which holds the bin lock (a reservation node for an empty bin) for the whole build; `clear()` and `remove()` take that lock (`synchronized (f)`, JDK 21 `ConcurrentHashMap.clear`, read 2026-10-05), wait for the running build and then drop its result. A build that starts after the clear began already sees the swapped entities, since `updateReadModel` runs after them.
- **The index build profits.** Measured on the same store, per location admin (`canRead` + `canReadInformation` over all resources): **300–680 ms without the group cache → 40–63 ms with it**, identical result. Every allocatable, type or category change drops all index entries, so each user pays this build again on the next request — the group cache shortens exactly that.
- **One description.** The group cache is the "caller-context cache" PRD 083 Part A sketches (per user, user change → that user, hierarchy change → flush; it answers AQ5 for the cache, not the JWT). Both caches are described together in [permissions.md § 5](../architecture/permissions.md#5-read_no_allocation--resource-visibility-vs-booking-visibility-verified-2026-06-24).

## Decisions

- **D1 — One cache in the operator for query and write paths (user ruling 2026-10-04).** First built per GraphQL request (`PermissionController.forRequest()`); replaced the same day by the operator cache so REST, Swing via the server and `SecurityManager` profit equally. Same measured factor.
- **D2 — A user change drops only that user's entry (user ruling 2026-10-04);** only category changes drop everything (they move the parent chains of many users).
- **D3 — Groups only, no resource × user memo.** After D1 under 10 % of the time is left, and `canRead` on allocatables is already cached by the `PermissionIndex`.
- **D4 — No reverse index "grantee → resources with EDIT".** Nothing needs the full set.
- **D5 — No micro-optimisation of the computation.** Measured: 0–8 %.

## Open Questions

None.

## Plan

- [x] Phase 0 — measure on a production-sized store, profile (2026-10-04).
- [x] Phase 1 — operator cache + `PermissionController` / `PermissionExtension` wiring; tier-2 `UserGroupsCacheTest` (equivalence for every user, reuse, user change drops only that user, category move drops all, draft never cached, resource change keeps the cache) red → green; the category invalidation and the eviction of a changed user's entry revert-checked (2026-10-05); harness rerun 8–13× (2026-10-04, uncommitted).

- [x] Phase 2 — review round (rapla-review 2026-10-05, no blocker; built by rapla-impl2): S1 `initIndizes` clears `UserGroupsCache` (`reloadDropsEveryEntry`, red→green; the `PermissionIndex` is still not cleared there — pre-existing residue, not touched); H1 the per-user invalidation carries no correctness (a changed user is a new instance) — kept as hygiene, `userChangeDropsOnlyThatUsersEntry` now asserts eviction of the old instance's entry via `UserGroupsCache.cachedUser(id)` (red with the change-branch reverted). Step 3 (rapla-impl2, 2026-10-05, all red→green): F1 race closed — after `put` the generation is re-checked and a stale entry removed (`UserGroupsCache.groupsOf`, test seam `protected currentGeneration()`, class no longer final; test `anInvalidationRightAfterTheGenerationCheckKeepsTheEntryOut` via an anonymous subclass of the class under test — a seam, not a §13 mock); F2 `hasPermissionToAllocate` rejects a null user again (`Objects.requireNonNull`; the only caller `canUserAllocateSomething` never passes null; test in `PermissionControllerAccessQueryTest`); `userRemovalDropsThatUsersEntry` covers the remove branch (revert-checked). Lanes after step 3: rapla-server default 587 + 620, leak/GraphQL 4 + 375 + 18, `UserGroupsCacheTest` 9/9, 0 failures. H2 (`groupsOf` per extension iteration — cache hit) and H3 (uncached remaining callers `PermissionContainer`, `SoftDenyAnalyzer`, `ViewCatalogService`, `DocumentCatalogService` — not hot) left as is.

## Acceptance criteria

- Identical permission answers (equivalence test; §12 leak tests unchanged green).
- Harness rerun shows the factor above.
