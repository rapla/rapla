# PRD 069 — GraphQL Resource- & Event-Access Read API (admin-scoped)

**Status:** in-progress — v1 landed 2026-06-17 (user + group targets, resources +
events). Schema `AccessLevel` enum + `accessibleByUsername`/`accessibleByUserId`/
`accessibleByGroup`/`accessLevel` on both filters; `PermissionController`
owner-aware user helper + group-effect helper (tier-2 `PermissionControllerAccessQueryTest`);
`AccessTargetFilter` admin-scope resolution + `ForbiddenException` (uniform §12
no-leak); wired into `ClassificationGraphQLController` + `ReservationGraphQLController`;
tier-3 leak test `ResourceAccessQueryGraphQLTest`. **Follow-up (2026-06-17):**
added `UserFilter.inGroup: [String!]` to `users(filter:)` — server-side "all
users of a group" using the same relative `user-groups` key-paths (resolves the
bare-key ambiguity of `User.groups`; `belongsTo` so sub-group members match;
unknown path → INVALID_VALUE); tier-3 `UsersInGroupFilterGraphQLTest`. Remaining:
confirm Open Questions (time-constraint, SPA wiring).

## Goal

Let an authenticated **admin** caller ask, over GraphQL, *"which resources
(`Allocatable`s) and events (`Reservation`s) may this user / these groups
**read** or **edit**?"* — and, more generally, at any chosen access level.

The two primary use cases:
- **read** — what the target can see → `canRead` → effective level `READ`.
- **edit** — what the target may modify → `canModify` → effective level `EDIT`
  (`ADMIN` is higher: administering the entity itself, e.g. changing its
  permissions/owner). Owner and global-admin implicit rights count as edit.

Booking (`ALLOCATE`) is supported by the same mechanism but is secondary here.

The query is **admin-scoped**: a caller may only target a user or group(s) they
are permitted to administer (`PermissionController.canAdminUser` /
`canAdminGroup` via `getGroupsToAdmin`). Targets outside the caller's admin
scope are rejected uniformly — the response must not reveal whether such a
user/group even exists (AGENTS.md §12).

Motivation: there is currently **no** way (REST, GraphQL, or Swing) to do this
reverse lookup. For resources the permission lives *on each resource* pointing
at a user/group; events derive access differently (see Events). Answering "for
whom is this accessible" today means hand-iterating entities.

## Scope

**In:**
- Extend `allocatables(filter:)` (PRD 035 / 059 / 066) and
  `reservations(filter:)` (PRD 055) with **access-by-target** fields:
  - `accessibleByUsername: String` — target user by **username** (single value,
    the human handle, like `user(username:)`).
  - `accessibleByUserId: ID` — target user by **opaque id** (single value, for
    callers that already hold it, e.g. the SPA). Two explicit user selectors
    instead of one ambiguous "id-or-name" field — no disambiguation guesswork.
  - `accessibleByGroup: [String!]` — target group(s). **List**, **union**
    semantics ("any of these groups", each incl. its parent cascade) — mirrors
    how rapla computes a user's effective access as the union over their groups.
    Groups are identified by **key path relative to the `user-groups` root** —
    the redundant `user-groups/` prefix is dropped (permission groups always
    live there): `"staff"` (direct child) or `"staff/leads"` (nested).
  - Exactly **one** of the three selectors may be set (`accessibleByUsername`
    XOR `accessibleByUserId` XOR `accessibleByGroup`).
  - `accessLevel: AccessLevel = READ` — minimum level the target must hold.
- A new GraphQL `enum AccessLevel` mirroring `Permission.AccessLevel`
  (DENIED, READ_TYPE, CREATE, READ_NO_ALLOCATION, READ, REQUEST, ALLOCATE,
  ALLOCATE_CONFLICTS, EDIT, ADMIN).
- Admin-scope enforcement + §12-safe intersection with the caller's own
  `canRead` in the resolver.

**Out (deferred / explicitly not now):**
- Time-window evaluation of ALLOCATE permissions (`start/end/min/maxAdvance`).
  v1 ignores time constraints — "holds the permission" is date-independent.
  Note the limitation in the schema doc.
- A dedicated top-level query (`resourceAccess(...)`) — folded into the existing
  filters for resolver reuse. Revisit only if filter semantics get awkward.
- REST equivalent, Swing UI, SPA wiring (separate follow-ups if wanted).
- Non-admin self-query ("what can *I* access") — already covered by the plain
  `allocatables` / `reservations` queries (everything the caller can read).
- `accessibleByUsername` as a list, intersection ("ALL these groups"),
  reverse direction ("who can edit X") — see Future query space.

## Plan

1. **Schema** — add `enum AccessLevel` + the four fields to `AllocatableFilter`
   and `ReservationFilter` in `schema.graphqls`, with doc comments (time-
   constraint caveat, the XOR rule, the relative-group-path rule, admin scope).
2. **Permission helper** (rapla-core `PermissionController`) — two paths,
   because **ownership lives outside the permission list**:
   - **User target**: reuse the owner-aware `canRead`/`canModify`/`canAdmin(entity, user)`.
     They already fold in **owner**, **global-admin**, the entity's permission
     list, and (for reservations) event-type rights + `read-events-from-others`.
     Do NOT hand-roll a permission-list scan for the user case — it would miss
     entities the user *owns* but has no explicit permission on.
   - **Group target**: a group is never an owner → no owner shortcut. A helper
     scans the permission list for a permission whose group == any target group
     or is an ancestor of it (reuse `PermissionContainer.Util` /
     `canAdminGroup`'s ancestor logic), at the requested level; for reservations
     also honour event-type group rights.
3. **Resolver** (`ClassificationGraphQLController` + the reservations resolver) —
   - Parse the filter fields; validate exactly one selector is set.
   - Resolve username→`User` / id→`User` / relative-path(s)→`Category`(s).
   - Enforce admin scope (`canAdminUser` for the user; `canAdminGroup` for every
     group). On failure throw a uniform "forbidden" GraphQL error — identical
     whether the handle is unknown or merely out-of-scope (no existence leak).
   - Filter the (already §12-visible) set to entities where the target's
     effective level `>= accessLevel`.
4. **Wire** through the existing filter plumbing additively (no change to
   current predicates).

## Tests

- **tier-2** (rapla-server, `FacadeTestSupport`): access-evaluation helper —
  - **User**: fixture where user X has an explicit permission on some entities
    and **owns** others (no explicit permission). The user-target set is
    correct; `accessLevel` threshold respected (READ-only entity excluded when
    EDIT requested). **Ownership must-have**: an owned-but-no-permission entity
    still appears for READ and EDIT (resolver uses owner-aware
    `canRead`/`canModify`, not a permission scan).
  - **Group**: a group permission cascades to a descendant group; union over
    multiple groups; a group whose only tie is that a *member* owns the entity
    does **not** appear (no owner shortcut for groups).
- **tier-3** (rapla-app, `@SpringBootTest` + MockMvc → `HttpGraphQlTester`):
  - admin caller targeting a user (by username and by id) / group(s) **within**
    their admin scope → exactly the expected resources/events.
  - **§12 leak test**: non-admin caller, or admin caller targeting an
    out-of-scope user/group, → forbidden; response **byte-identical** for
    "unknown handle" vs "exists but out of scope".
  - result never includes an entity the **caller** cannot `canRead` (target can
    access it but caller can't → dropped).

## Events (Veranstaltungen) — the permission model differs

Resources carry an explicit per-entity permission list (user/group → level), so
the reverse lookup scans each allocatable's permission list. **Reservations do
not.** A reservation's read/edit access derives from:

- **owner** — the owner may read & modify their own events;
- **event type** — the reservation's `DynamicType` permission list grants
  READ / CREATE / EDIT to users/groups (this is where a *group* gets event
  rights);
- **`read-events-from-others`** — the global group permission that lets a user
  see events they don't own;
- **admin** — global admins read & edit everything.

`canRead(Reservation, user)` / `canModify(Reservation, user)` already encode all
of this — the resolver reuses them rather than reimplementing.

- **Target = user**: a reservation is included when `canRead(r, targetUser)`
  (READ) / `canModify(r, targetUser)` (EDIT). For a non-admin target this is
  mostly "events they own" plus events their groups' type-level permissions
  grant.
- **Target = group**: use **type-level** semantics — events whose `DynamicType`
  grants the group READ/EDIT (the group literally holds the right), consistent
  with the resource case. The alternative "events owned by any member of the
  group" is deferred (composable client-side via `users(filter: inGroup)` →
  `reservations(ownerEq:)`).

Note: the `reservations(filter:)` window (`from`/`to`) stays mandatory — the
access predicate narrows *within* the requested window.

## Example queries

```graphql
# Which resources may user jdoe EDIT (by username)?
query {
  allocatables(filter: { accessibleByUsername: "jdoe", accessLevel: EDIT }) {
    id displayName type { key }
  }
}

# Same, but the caller already holds the id (e.g. the SPA):
query {
  allocatables(filter: { accessibleByUserId: "u-abc-123", accessLevel: READ }) {
    id displayName
  }
}

# Which resources may any of these groups READ (union; relative paths)?
query {
  allocatables(filter: { accessibleByGroup: ["tutors", "staff"], accessLevel: READ }) {
    id displayName
  }
}

# Compose with existing predicates — which ROOMS may group staff/leads edit?
query {
  allocatables(filter: {
    accessibleByGroup: ["staff/leads"]
    accessLevel:       EDIT
    typeKeyEq:         "room"
  }) { id displayName }
}

# Which events in March may user jdoe EDIT?
query {
  reservations(filter: {
    from: "2026-03-01T00:00:00"
    to:   "2026-04-01T00:00:00"
    accessibleByUsername: "jdoe"
    accessLevel:          EDIT
  }) { id name }
}
```

Reuse of the existing filters means every current predicate (`typeKeyIn`,
`searchText` + `matchKind`, the generated `whereXxx` typed attribute filters,
`limit`, …) composes with the access fields for free — e.g. *"rooms with > 30
seats that group staff may edit"*.

## Future query space

- **Read/visibility audit** — "what can this (leaving) user still see?" for
  offboarding / data-protection reviews. *(this PRD)*
- **Edit/delegation overview** — "which resources do these groups administer?"
  before a reorg. *(this PRD)*
- **Booking-rights overview** — `accessLevel: ALLOCATE`. *(this PRD, modulo the
  time-constraint caveat)*
- **Typed/filtered access** — combine with `whereXxx` / `searchText`. *(this PRD)*
- **Effective-level-per-result** — return the *level* the target holds on each
  result in one query. *(future: `effectiveAccess(forUser/forGroup): AccessLevel`
  field on `Allocatable`/`Reservation`.)*
- **Reverse direction** — given one entity, "which users/groups in my admin
  scope can edit it?". *(future: separate field/query.)*
- **Cross-target diffs / intersection** — "resources A can edit but B cannot",
  "resources ALL these groups share", username-list union. *(future; mostly
  client-side composition.)*

## Open Questions

- Time-constrained ALLOCATE permissions — out for v1; confirm acceptable for the
  intended use (likely a "who *could* book X" overview).
- Does the SPA need this now, or is it a server/MCP-facing capability first?

## Decisions

- **`accessLevel` default = `READ`** (2026-06-17). Omitting → "what can they
  see"; `EDIT` → "what can they edit" (`ADMIN` = administer the entity itself).
- **Three selectors, exactly one set** (2026-06-17): `accessibleByUsername:
  String` (single) XOR `accessibleByUserId: ID` (single) XOR
  `accessibleByGroup: [String!]`. Two explicit user selectors (username + id)
  rather than a single ambiguous "id-or-name" field or a custom scalar —
  self-documenting, no disambiguation guesswork.
- **`accessibleByUsername` is single, not a list** (2026-06-17). A username-list
  (union) is deferred to Future query space.
- **`accessibleByGroup` is a list with union semantics** (2026-06-17) — "any of
  these groups". Intersection is out of scope.
- **Group key paths are relative to the `user-groups` root** (2026-06-17) — no
  `user-groups/` prefix; `"staff"` / `"staff/leads"`.
- **Events + group target = type-level** (2026-06-17) — the group holds the
  event-type right; "events of group members" deferred.
