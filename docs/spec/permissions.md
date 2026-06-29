---
kind: capability-spec
capability: permissions
status: living
derived-from:
  - rapla-core/src/main/java/org/rapla/entities/domain/Permission.java
  - rapla-core/src/main/java/org/rapla/entities/domain/internal/PermissionImpl.java
  - rapla-core/src/main/java/org/rapla/entities/domain/PermissionContainer.java
  - rapla-core/src/main/java/org/rapla/entities/domain/permission/impl/RaplaDefaultPermissionImpl.java
  - rapla-core/src/main/java/org/rapla/storage/PermissionController.java
  - rapla-server/src/main/java/org/rapla/server/internal/SecurityManager.java
verified-by:
  - rapla-server/src/test/java/org/rapla/storage/PermissionMatrixTest.java
  - rapla-server/src/test/java/org/rapla/storage/PermissionControllerAccessQueryTest.java
  - rapla-core/src/test/java/org/rapla/storage/LocalCacheAuditTest.java
decisions:
  - "[[0003-permissions-are-grant-only]]"
extracted: 2026-06-24
---

# Capability: Permissions (current contract)

This is the **living contract** — what the permission system does *now*. It carries no rationale
and no rejected alternatives; those live in the linked MADRs (`decisions:` above) and the
explanatory companion [`docs/architecture/permissions.md`](../architecture/permissions.md)
(Diátaxis: this file is *reference*, that one is *explanation*).

> **Core invariant: grant-only and purely additive** (ADR 0003 revised 2026-06-28 / PRD 090).
> Rows only ever *add* access; there are no deny rows and **no precedence**. Effective access is
> the **highest** level over *all* matching rows (`USER`, `GROUP`, `WORLD` — the `max`), and
> no-match means no access. `DENIED` (0) is the floor — **inert** under resolution (never
> subtracts) — and deprecated in the editing UI. The precedence model (`USER` > `GROUP` > `WORLD`
> with downward override) is **superseded**; see [[0003-permissions-are-grant-only]].

## How to read this spec — verification legend

Every claim is tagged with how strongly it is held to be *true now*:

- **✅ PINNED** — an executable test fails if this drifts. Named inline. Treat as truth.
- **🔧 GENERATABLE** — should be machine-generated from the cited source; not yet generated, so
  currently transcribed by hand (drift-prone until generation lands — PRD 088 Phase 3).
- **📝 DERIVED** — read out of the code on 2026-06-24, but no test pins it. Probably true; verify
  against the cited file before relying on it.

The spec approaches "truth" by raising the ✅ ratio over time, not by asserting it. The
`## Unverified surface` section at the end is the honest list of what is *not* yet pinned.

## AccessLevel ladder

🔧 GENERATABLE from `Permission.AccessLevel` (`Permission.java:54-65`). Ten levels, totally ordered
by an `int`; `level.includes(x)` ≡ `x.numeric <= this.numeric`, `excludes` is the complement
(`Permission.java:89-95`).

| Level | Numeric | Grants (cumulative — includes every lower level) |
|---|---:|---|
| `DENIED` | 0 | Nothing — the floor. Inert under additive resolution (never subtracts); deprecated. |
| `READ_TYPE` | 20 | See that a `DynamicType` exists. |
| `CREATE` | 30 | Create instances of a `DynamicType`. |
| `READ_NO_ALLOCATION` | 50 | See a resource exists, without its bookings. |
| `READ` | 100 | See the resource and the bookings on it. |
| `REQUEST` | 150 | Request an allocation (lands in `RequestStatus.requested`, pending approval). |
| `ALLOCATE` | 200 | Book directly. |
| `ALLOCATE_CONFLICTS` | 300 | Book even when it creates a conflict. |
| `EDIT` | 350 | Modify the entity itself (title, attributes, owner). |
| `ADMIN` | 400 | Full control, including changing permissions on the entity. |

> ⚠️ The companion `architecture/permissions.md` currently says "Nine levels" — that prose has
> drifted from the ten-value enum. A generated table (🔧) removes this whole class of bug.

✅ PINNED (ladder semantics): `PermissionMatrixTest.nonAdminCanReadDefaultGrantedAllocatables`
(ALLOCATE_CONFLICTS includes READ), `…nonAdminCanAllocateDefaultGrantedResources`
(ALLOCATE_CONFLICTS includes ALLOCATE).

## Permission row (the ACL unit)

📝 DERIVED from `PermissionImpl`. One permission row is:

| Field | Meaning |
|---|---|
| `user` *(nullable)* | If set, applies only to that user. Mutually exclusive with `group`. |
| `group` *(nullable)* | If set, applies to that `Category` and its descendants. |
| `accessLevel` | The granted level (table above). |
| `start` / `end` *(nullable)* | Absolute time window the row covers. |
| `minAdvance` / `maxAdvance` *(nullable)* | Relative window in days from "today". |

📝 DERIVED: absolute (`start`/`end`) and relative (`minAdvance`/`maxAdvance`) bounds are mutually
exclusive per field. Both `user` and `group` null is a wildcard: effect `ALL_USER_PERMISSION`
(`PermissionImpl`, effect constants `NO_PERMISSION=-2`, `ALL_USER_PERMISSION=-1`,
`GROUP_PERMISSION=5000`, `USER_PERMISSION=10000`).

Permission-bearing containers (`PermissionContainer`): `Allocatable`, `Reservation`, `DynamicType`,
`Category`.

## Resolution: does user U have access at level `R` on entity E?

📝 DERIVED from `RaplaDefaultPermissionImpl.hasAccess` (`:29-122`). Order is load-bearing:

1. **Admin short-circuit** — `U == null || U.isAdmin()` ⇒ **granted**. ✅ PINNED:
   `PermissionMatrixTest.adminCanModifyEveryAllocatable`, `…adminCanAdminEveryAllocatable`.
2. **Owner short-circuit** — `PermissionController.isOwner(E, U)` ⇒ **granted** (read/modify/admin).
   ✅ PINNED: `PermissionControllerAccessQueryTest.ownerHasReadAndEditWithoutAnyExplicitPermission`,
   `PermissionMatrixTest.ownerCheckMatchesOwnerRefId`.
3. **Permission scan** — over `E.getPermissionList()`, compute the effective level as the
   **highest** `accessLevel` over *all* matching rows (`USER`, `GROUP`, or `WORLD`), with **no
   precedence**. Grant iff that `max` `includes(R)`. `DENIED` (0) is the floor — inert.
   - ✅ PINNED (additive max-wins, deny-inert, cascade + union):
     `AdditivePermissionResolutionTest` (rapla-server) — `userReadNoLongerCapsBelowGroupAllocate`,
     `userDeniedNoLongerOverridesGroupGrant`, `groupDeniedNoLongerOverridesAllUsersGrant`;
     `PermissionControllerAccessQueryTest.groupPermissionGrantsAtLevelButNotAbove`,
     `…parentGroupPermissionCascadesToChildGroup`, `…unionAcrossGroups`.
   - 📝 DERIVED: a lower-level `USER` row can **no longer** cap below a stronger `GROUP` row — the
     `max` takes the group level. "Deny the group, allow individuals" is no longer expressible; use
     group membership to exclude.

📝 DERIVED (group expansion): `U`'s groups are expanded to all ancestors via
`UserImpl.getGroupsIncludingParents` — a grant on a parent category covers all descendants.

## Time-windowed allocation

📝 DERIVED (`RaplaDefaultPermissionImpl:88-105`): time bounds are checked **only** for `ALLOCATE`/
`REQUEST` requests, and **never** for `ADMIN` rows. A bounded row qualifies iff `covers(start, end,
today)` (full-window check) or, when `checkOnlyToday`, `validInTheFuture(today)`.
`PermissionContainer.Util.getInterval(...)` returns the union of windows where U holds a given level.

📝 DERIVED: a `REQUEST`-only qualifying row does not block the store; it flips the per-allocatable
`Reservation.getRequestStatus(Allocatable)` to `requested` instead of committing the allocation.

## Admin scopes — global vs. group

📝 DERIVED from `PermissionController` (`getGroupsToAdmin`, `getAdminGroups`, `canAdminUser*`):

- **Global admin** — `U.isAdmin()`. Bypasses every check. There is **no** fine-grained "see all"
  flag; admin is all-or-nothing.
- **Group admin** — a `Category` carrying the `CAN_ADMIN_PARENT="true"` annotation confers admin
  over the users/groups under its parent. `canAdminUsers(U)` ≡ `isAdmin() || getAdminGroups(U)
  non-empty`; `canAdminUser(admin, target)` ≡ admin's annotated group is an ancestor-or-equal of a
  target group.

## Enforcement boundaries

The server is authoritative; the client mirrors the same `PermissionController` for UX only
(greyed buttons), and every mutation is re-checked server-side.

📝 DERIVED — gates (see AGENTS.md §12, data-leak-prevention):

| Boundary | Check | Site |
|---|---|---|
| Read filter (login/poll) | `canReadInformation` / `canRead` | `LocalCache.getVisibleEntities`, `SecurityManager` |
| GraphQL read (output boundary) | `canRead` per entity, `.filter(canRead)` per list | `ReservationGraphQLController:100,184,205` |
| Allocate gate (on store) | `hasPermissionToAllocate(user, appt, A, original, today)` | `SecurityManager` |
| Modify / delete | `SecurityManager.checkModifyPermissions` (owner-unchanged+EDIT, or admin) | `SecurityManager` |
| REST | `canReadInformation` / `canRead` / `canAdminUser` | `RemoteStorageController:225,312,584` |

📝 DERIVED (cache): `readmodel/PermissionIndex` pre-computes readable allocatable ids per user and
the effective level; it **delegates to the real `PermissionController` for correctness** and only
caches, invalidated on permission or group-membership change. ✅ PINNED (invalidation):
`LocalCacheAuditTest`.

## Unverified surface (honest gaps — raise these to ✅ over time)

These claims are 📝 DERIVED only — no test pins them today. Each is a candidate for a pinning test
(PRD 088 Phase 3 confirmation work):

- Absolute vs. relative time bounds are mutually exclusive per field.
- `ADMIN` rows ignore time windows.
- `REQUEST`-only ⇒ `RequestStatus.requested` rather than a rejected store.
- The full enforcement-boundary table (only the read filter + cache invalidation are pinned).
- The `READ_ONLY_INFORMATION` → `READ_NO_ALLOCATION` legacy string alias (`Permission.java:99`).

## See also

- Rationale + rejected alternatives: [[0003-permissions-are-grant-only]] (`docs/decisions/`).
- Explanation + worked examples + auth flow: [`architecture/permissions.md`](../architecture/permissions.md).
- Entity model: [`architecture/domain-model.md`](../architecture/domain-model.md).
