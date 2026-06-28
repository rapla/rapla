# Permission model

Rapla's permission model is **grant-only, role-based, hierarchical, and
time-aware**. Every entity that anyone might want to control access
to (Allocatable, Reservation, DynamicType, Category) carries a list
of `Permission` rows. Users are members of `Category` groups
(transitively, via the category hierarchy). Permission resolution
walks both axes.

> **Grant-only is the core invariant.** Permission rows only ever *add*
> access — there are **no deny rows**. A user's effective access is the
> level of the single strongest-precedence row that matches them
> (`USER` > `GROUP` > `WORLD`); a user matched by no row gets nothing.
> Access never subtracts: you cannot stack a "deny" on top of a grant.
> The one override idiom is putting `DENIED` (0) on a *higher-precedence*
> row to cap an inherited lower-precedence grant (e.g. a `GROUP`-`DENIED`
> beating an all-users grant). `DENIED` is now **deprecated in the editing
> UI** (see [`AccessLevel`](#accesslevel) below). This is
> [ADR 0003](../decisions/0003-permissions-are-grant-only.md) — read it
> before reasoning about deny semantics.

This page covers:

- The `AccessLevel` enum and what each level allows
- The shape of `PermissionImpl` (who, what, when)
- The resolution algorithm
- Where permissions are enforced (server) vs hinted (client)
- How JWT auth becomes a Rapla `User`

For the entity model around Permission see
[domain-model.md](domain-model.md).

---

## Mental model

```
A Permission is one ACL row:

   ┌─────────────────────────────────────────────────────────┐
   │ user OR group        — who this rule applies to         │
   │ accessLevel          — DENIED..ADMIN, ordered            │
   │ pStart, pEnd         — absolute window (optional)        │
   │ minAdvance, maxAdvance — relative window in days (opt.)  │
   └─────────────────────────────────────────────────────────┘

A PermissionContainer (Allocatable, Reservation, DynamicType, Category)
holds a List<Permission>. The container's effective permission for
user U is the strongest level granted by any matching row.

Effective access matters at three points:
  1. Read filter   — does U get to see this entity at all?
  2. Allocate gate — can U book this resource at this time?
  3. Modify gate   — can U change this entity?

Server is authoritative; client mirrors the same rules for UX (greyed
buttons, blocked dialogs). The server re-validates everything.
```

## `AccessLevel`

`rapla-core/src/main/java/org/rapla/entities/domain/Permission.java`

Nine levels, totally ordered. Each level **includes** every lower
level (`AccessLevel.includes(other)` ≡ `this.compareTo(other) >= 0`,
modulo `DENIED`):

| Level | Numeric | Allows |
|---|---:|---|
| `DENIED` | 0 | Nothing — used to override an inherited permission. **Deprecated as a selectable level** (see below). |
| `READ_TYPE` | 20 | See that the DynamicType exists (admin views). |
| `CREATE` | 30 | Create new entities of this DynamicType. |
| `READ_NO_ALLOCATION` | 50 | See the resource but not its bookings. |
| `READ` | 100 | See the resource and the bookings on it. |
| `REQUEST` | 150 | Request an allocation, pending admin approval. |
| `ALLOCATE` | 200 | Book directly. |
| `ALLOCATE_CONFLICTS` | 300 | Book even if it creates a conflict. |
| `EDIT` | 350 | Modify the entity itself (title, attributes, …). |
| `ADMIN` | 400 | Full control, including permission changes. |

The bands matter:

- **Reading.** `READ_NO_ALLOCATION` lets a user see "Room A101 exists"
  in pickers and resource lists without seeing who else has booked it.
  `READ` reveals the bookings too.
- **Allocating.** `REQUEST` is the workflow band — the user creates a
  reservation but it lands in `RequestStatus.requested`. Admins
  approve via the request workflow. `ALLOCATE` skips the workflow.
  `ALLOCATE_CONFLICTS` allows double-booking (rare).
- **Editing.** `EDIT` lets a user change a reservation's title,
  classification, owner, etc. — not just its allocation. `ADMIN` is
  needed to change permissions on the entity itself.

### Which levels are valid on which target (intended matrix)

Not every level belongs on every entity. `READ_TYPE` / `CREATE` are
**type-scoped** (they gate `canReadType` / `canCreate` on a
`DynamicType`) and must **never** appear on an `Allocatable` —
`READ_TYPE` (20) `< READ` (100), so a `READ_TYPE` row on a resource
grants no readability at all; it is simply a misplaced grant. The
intended set per target is encoded today in the Swing editors'
`setPermissionLevels(...)` calls — this table is the canonical
source-of-truth extracted from them:

| Target entity | Allowed access levels | Source (Swing editor) |
|---|---|---|
| `DynamicType` | `READ_TYPE`, `CREATE`, `READ`, `EDIT`, `ADMIN` | `DynamicTypeEditUI` |
| `Allocatable` | `READ_NO_ALLOCATION`, `READ`, `REQUEST`, `ALLOCATE`, `ALLOCATE_CONFLICTS`, `EDIT`, `ADMIN` — **no** `READ_TYPE`/`CREATE` | `AllocatableEditUI` |
| `Reservation` | `READ`, `EDIT`, `ADMIN` | `ReservationEditUI` |
| `Category` (group) | not a normal permission list — group admin rights ride on the admin-group mechanism (`getGroupsToAdmin` / `CAN_ADMIN_PARENT`), effectively `ADMIN` only | — |

**Enforcement status:** today this matrix is only *advisory* (the Swing
UI offers the right levels; nothing rejects an out-of-range level stored
through another path). It is **not yet enforced server-side**, because no
GraphQL write path edits permission lists (`AllocatableMutationController`
v1 copies existing permissions through and inherits type defaults — it
never sets a level). When a permission-editing verb lands (PRD 063 OQ2 —
`setAllocatablePermissions`, and the analogues for `DynamicType` /
`Reservation`), the save path **must validate the level against this
matrix** and reject a mismatch (e.g. `READ_TYPE` on an `Allocatable`).

### `DENIED` is deprecated as a selectable level

[ADR 0003](../decisions/0003-permissions-are-grant-only.md) makes permissions
**grant-only**; `DENIED` (0) is the one subtraction idiom (a more-specific
`DENIED` row overriding an inherited grant). Because it confuses admins who
read it as an NTFS-style "deny", it is being **retired from the editing UI**:

- **New permission rows never get `DENIED`.** The Swing access-level dropdown
  (`PermissionField`) filters `DENIED` out, and a new row created via
  `PermissionListField` falls back to the first non-`DENIED` level — so it can
  no longer be *added*.
- **Existing `DENIED` rows are preserved, not rewritten.** A row that loads with
  `DENIED` selected keeps `DENIED` in its dropdown (rendered as
  *"Denied (deprecated)"*, italicised) so editing the row never silently changes
  its stored level. The filter keys off the value **at load time only** — once a
  row is shown with `DENIED`, switching away and back is allowed within that
  edit session.
- **Save is not blocked.** This is a UI deprecation, not a validation rule;
  `DENIED` remains a fully valid stored/resolved level (see the worked example
  below and `RaplaDefaultPermissionImpl.hasAccess`).

Implemented in `PermissionField.selectableLevels(...)` /
`firstSelectableLevel(...)` (pinned by `PermissionFieldDeniedDeprecationTest`).

## `PermissionImpl`

`rapla-core/src/main/java/org/rapla/entities/domain/internal/PermissionImpl.java`

Fields:

| Field | Type | Meaning |
|---|---|---|
| `user` | `ReferenceInfo<User>` (nullable) | If set, applies only to that user. Setting it nulls out `group`. |
| `group` | `ReferenceInfo<Category>` (nullable) | If set, applies to members of that category and its descendants. |
| `accessLevel` | `AccessLevel` | The granted level. |
| `pStart` | `LocalDateTime` (nullable) | Absolute earliest moment the rule covers. |
| `pEnd` | `LocalDateTime` (nullable) | Absolute latest moment. |
| `minAdvance` | `Integer` (nullable) | Days from "today" — request must be at least this far in the future. |
| `maxAdvance` | `Integer` (nullable) | Days from "today" — request can't be further out than this. |

Two special cases for `(user, group)` both null:

- `NO_PERMISSION` (`-2`) — disabled row.
- `ALL_USER_PERMISSION` (`-1`) — applies to everybody (the wildcard).

Setting one of `pStart`/`pEnd` (absolute) clears the corresponding
relative bound `minAdvance`/`maxAdvance` and vice versa — they're
mutually exclusive at the field level.

`covers(start, end, today)` returns true iff the requested interval
falls within both the absolute window and the relative window.

## Where permissions live

| Container | What permissions on it control |
|---|---|
| `Allocatable` | Who can read it (`READ_NO_ALLOCATION` / `READ`), allocate it (`ALLOCATE` / `REQUEST`), edit it (`EDIT`), or admin it (`ADMIN`). |
| `DynamicType` | Who can see it exists (`READ_TYPE`), create instances of it (`CREATE`), edit instances (`EDIT`), or admin it (`ADMIN`). |
| `Reservation` | Who can read this specific event (independent of read on the allocatables). |
| `Category` | Who can administer the category and (for groups) its members. The `CAN_ADMIN_PARENT` annotation marks a category whose admins can manage the user records under it. |

## Resolution algorithm

Pseudocode for "does user U have access at level `requested` on entity E?":

```
function hasAccess(U, E, requested, [start, end], today):

    if U.isAdmin():
        return true                                    # global override

    if E is Ownable and E.owner == U:
        return true                                    # owner shortcut (most ops)

    groups = U.getGroupsIncludingParents()             # transitive ancestors

    bestEffect = NO_PERMISSION
    bestLevel  = DENIED

    for p in E.getPermissionList():
        effect = p.userEffect(U, groups)               # USER > GROUP > ALL_USERS > NO
        if effect <= bestEffect:
            continue                                   # weaker match; ignore

        if not p.accessLevel.includes(requested):
            continue                                   # this row doesn't grant enough

        if [start, end] given and not p.covers(start, end, today):
            continue                                   # outside time window

        bestEffect = effect
        bestLevel  = p.accessLevel

    return bestLevel.includes(requested)
```

Two refinements that the code makes:

1. **Effect ordering.** A `USER` match outranks a `GROUP` match
   outranks an `ALL_USERS` (wildcard) match. So if a group says
   "DENIED" and a user-specific row says "ALLOCATE", the user-specific
   row wins. This is what makes "deny by group, allow individuals"
   work.
2. **Group hierarchy.** `getGroupsIncludingParents()` returns the
   user's direct groups plus all ancestors up the category tree. So
   a permission on the root group "all-users" covers every user;
   a permission on "students" covers everyone in "students" and
   its descendants.

The actual implementation lives in
`rapla-core/src/main/java/org/rapla/storage/PermissionController.java`,
with utility methods on
`rapla-core/src/main/java/org/rapla/entities/domain/PermissionContainer.java`
(see `PermissionContainer.Util.getUserEffect` and
`PermissionContainer.Util.getInterval`).

## Server enforcement

The server is **authoritative**. All three checks live in
`rapla-server/src/main/java/org/rapla/server/internal/SecurityManager.java`
and `rapla-core/.../storage/PermissionController.java`.

### 1. Read filter (`getVisibleEntities`)

When a user logs in or polls for changes, the server runs every
entity through a visibility filter before sending it to the client.
The filter is applied by
`rapla-core/src/main/java/org/rapla/storage/LocalCache.java` →
`getVisibleEntities(forUser)`:

- Categories — always visible (they are the structure).
- Users — self, plus admins, plus members of groups the user
  administers (via the `CAN_ADMIN_PARENT` annotation).
- Allocatables — those for which `canReadInformation(alloc, user)`.
- Preferences — system-wide and the user's own.
- Reservations — those the user can read (see #3 below).

### 2. Allocate gate (on store)

Before committing a Reservation that allocates `Allocatable A` over
`[s, e]`, the server calls
`permissionController.hasPermissionToAllocate(user, appointment, A, originalReservation, today)`.
This walks A's permissions looking for an `ALLOCATE` (or stronger)
that:

- Applies to the user (direct or via group).
- Covers `[s, e]` against both absolute and relative windows.

If no row qualifies, the server throws `RaplaSecurityException`.

If only a `REQUEST`-level row qualifies, the server allows the store
but flips the per-allocatable RequestStatus to `requested` instead of
committing the allocation outright.

### 3. Reservation read (`canRead`)

A Reservation has its own permission list independent of the
allocatables it touches. The default permission for a new
Reservation makes it visible to its owner only. To share, the owner
adds a permission row.

`PermissionController.canRead(reservation, user)` short-circuits to
true for the owner, then walks the reservation's permission list. The
extension point `PermissionExtension` allows plugins (e.g. a
template-sharing plugin) to grant additional read access.

### 4. Modify / delete

`SecurityManager.checkModifyPermissions(user, entity)` runs before any
store. For new entities, the user must be the owner. For modifications,
either the owner is unchanged (and the user has `EDIT`) or the user is
admin. Re-parenting (changing the owner) requires admin rights on both
the old and new owner records.

### 5. `READ_NO_ALLOCATION` — resource visibility vs. booking visibility (verified 2026-06-24)

The `READ_NO_ALLOCATION` (50) / `READ` (100) band is enforced by **two
different methods that gate two different things** — confirmed by reading the
call sites and the i18n label (`permission.read_no_allocation = lesen (keine
Belegungen)` — "read, no bookings"):

| Method | Threshold | Gates | Call sites |
|---|---:|---|---|
| `canReadInformation(alloc, user)` | ≥ `READ_NO_ALLOCATION` (50) | the **resource itself** — existence, name, attributes; whether it appears in pickers, the calendar resource column, the client store, and is an allowed `queryAppointments` target | `LocalCache.getVisibleEntities`, `UpdateDataManagerImpl` (client store filter), `SecurityManager.checkRead`, `RemoteStorageController.queryAppointments`, `RaplaBuilder.isVisible` (calendar column), `ParsedText` (name render) |
| `canRead(alloc/appointment/reservation, user)` | ≥ `READ` (100) | the **bookings on it** — appointment blocks render with reservation detail; below `READ` a block is anonymized/suppressed (`RaplaBuilder`: `!canRead(appointment,user)` ⇒ anonymous) | `RaplaBuilder` block build, the GraphQL `canReadAllocatable` gate |

So `READ_NO_ALLOCATION` literally means **"the resource can be seen and
expanded, but without its allocations"**: a user at level 50 sees "Room A101
exists" and can select it; the appointments on it stay hidden/anonymous until
the user reaches `READ` (100).

**Caveat for the PRD 082 #8 / PRD 083 `PermissionIndex`.**
`PermissionIndex.readableAllocatables(user)` delegates to **`canRead`**
(level 100), so its set is the *"can see bookings"* set, **not** the
`canReadInformation` (level 50) resource-visibility set. Consequences:

- It is a correct §12 drop-in only at the **GraphQL `canRead` gate sites**
  (`RequestCtx.canReadAllocatable` and friends), which already used `canRead`.
- It must **not** gate the `canReadInformation` paths (old RemoteStorage
  `queryAppointments` / `getVisibleEntities`) — that would hide
  `READ_NO_ALLOCATION` resources the legacy path shows (over-restrictive /
  fail-closed — not a leak). PRD 083 scopes the index to GraphQL for exactly
  this reason.
- Net effect: on a `READ_NO_ALLOCATION`-only resource the **Swing client**
  (RemoteStorage → `canReadInformation`) shows it with bookings hidden, while
  the **SPA** (GraphQL → `canRead`) omits it from the catalog. This is a
  pre-existing boundary difference, not introduced by the index.

## Client mirror

The client uses the same `PermissionController` (it's in `rapla-core`)
to:

- Grey out menu items the user can't trigger
- Disable form fields the user can't edit
- Pre-emptively block the conflict-create dialog
- Drive the calendar's "this slot is read-only" hatching

This is **purely UX**. Every action that mutates state goes through
the server, which re-runs all checks. Tampering with the client
gives you nothing.

## Authentication: JWT → Rapla `User`

The HTTP edge runs Spring Security with a JWT bearer-token gate
(`rapla-server/.../server/spring/JwtConfig.java` and
`SecurityConfig.java`). Login (POST `/auth/login`) verifies
credentials against `AuthenticationStore` (file-based,
LDAP/JNDI, or DB) and returns an access + refresh token pair.

For each subsequent request:

1. Spring Security validates the JWT.
2. `SpringSecurityRemoteSession`
   (`rapla-server/.../server/spring/SpringSecurityRemoteSession.java`)
   reads the JWT's `sub` claim.
3. Resolves it to a `User` entity via
   `operator.resolve(new ReferenceInfo<>(sub, User.class))`.
4. Stashes the User in the request-scoped `RemoteSession`.

All downstream permission checks see this User as the "current user."

`AuthenticationStore` is pluggable — JNDI / LDAP via the `jndi`
plugin, DB via the default, encryption-token URLs via the
`urlencryption` plugin. See [extension-points.md](extension-points.md).

## RequestStatus workflow

When a user has only `REQUEST` permission on an Allocatable, allocations
made by that user land in `RequestStatus.requested` rather than
`RequestStatus.confirmed`. The reservation exists, the allocation exists,
but it doesn't count for conflict purposes until an admin approves it.

`Reservation.getRequestStatus(Allocatable)` is the per-allocatable
state. Admins approve / reject through the admin UI (see the
`adminpanels` plugin) or via REST.

## Worked example

**Setup.**

- Alice is in group `students`. The category tree is
  `root / user-groups / students`.
- Allocatable `Room A101` has one permission:
  `(group=students, accessLevel=ALLOCATE, no time bounds)`.
- DynamicType `reservation` has a permission
  `(everybody, accessLevel=CREATE)`.

**Alice books Room A101 for 2026-06-01 14:00–15:00.**

1. **Login.** Alice POSTs `/auth/login`, gets a JWT.
   `SpringSecurityRemoteSession` resolves the `sub` to her User.
2. **Read filter.** When she fetches the resource list, the server
   includes A101 because `canReadInformation(A101, alice)` walks
   A101's permissions, sees `(students, ALLOCATE)`, alice is in
   group `students` (transitively also in `user-groups` and the
   root), and `ALLOCATE.includes(READ)` ⇒ true.
3. **Create reservation.** The dialog calls
   `canCreate(reservationDynamicType, alice)`, which finds the
   `CREATE` permission on the reservation type → enabled.
4. **Submit.** Client sends the new Reservation with allocation
   for A101 over [14:00, 15:00].
5. **Server check.** `SecurityManager.checkModifyPermissions` —
   no original (new entity), owner is Alice (matches current user),
   passes.
6. **Allocate gate.** `hasPermissionToAllocate(alice, appt, A101, null, today)`
   → finds `(students, ALLOCATE)`, ALLOCATE.includes(ALLOCATE),
   no time bounds → true.
7. **Commit.** Server stores, fires `ModificationEvent`. Other
   connected clients see the allocation on their next poll.

**Alice tries to edit it later, after a new permission was added
to A101: `(students, READ, no allocate after 18:00)` — her
modification moves the appointment to 20:00.**

1. **Allocate gate.** Now both permissions match her. `(students, ALLOCATE)`
   has no bounds and would still pass; `(students, READ, after-18:00)` would
   not grant ALLOCATE. The strongest matching row wins → ALLOCATE → true.
2. *To restrict her properly*, the admin should change the `(students, ALLOCATE)`
   row to have `pEnd=18:00` or replace it with a `(students, READ)` rule.
   Permissions don't subtract; you can't add a "deny" row on top — you
   change the existing one or use `DENIED` to override an inherited group.

**Admin sees Alice's request even outside Alice's group.**

Because admin (`isAdmin() == true`) bypasses every check at every
level. There's no fine-grained "see all" flag — admin is global.

## See also

- [domain-model.md](domain-model.md) — Permission entity, User /
  Category structure
- [extension-points.md](extension-points.md) — `AuthenticationStore`,
  `PermissionExtension`
- [reservation-edit.md](reservation-edit.md) — where the client uses
  `canModify` to decide whether to even open the dialog
- [conflicts-and-events.md](conflicts-and-events.md) — conflict
  visibility filtering by `canRead`
