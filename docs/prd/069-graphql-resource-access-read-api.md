# PRD 069 — GraphQL Resource- & Event-Access Read API (admin-scoped)

**Status:** draft (opened 2026-06-17) — design

## Goal

Let an authenticated **admin** caller ask, over GraphQL, *"which resources
(`Allocatable`s) may this user / this group **read** or **edit**?"* — and, more
generally, at any chosen access level.

The two primary use cases:
- **read** — resources the target can see → `canRead` → effective level `READ`.
- **edit** — resources the target may modify/administer → `canModify`/`canAdmin`
  → effective level `ADMIN` (in rapla, editing an allocatable requires admin
  rights on it; there is no separate "edit but not admin" level). Owner and
  global-admin implicit rights count as edit.

Booking (`ALLOCATE`) is supported by the same mechanism but is a secondary
concern here.

The **same read/edit question applies to events (Veranstaltungen =
`Reservation`s)** — "which events may this user / group read or edit?" — but
events use a *different* permission model (see the Events section below), so
the semantics are not a literal copy of the resource case.

The query is **admin-scoped**: a caller may only target a user or group that
they are permitted to administer (`PermissionController.canAdminUser` /
`canAdminGroup` via `getGroupsToAdmin`). Targets outside the caller's admin
scope are rejected uniformly — the response must not reveal whether such a
user/group even exists (AGENTS.md §12).

Motivation: there is currently **no** way (REST, GraphQL, or Swing) to do this
reverse lookup. The data model stores permissions *on each resource* pointing
at a user or group; answering "for whom is this resource accessible" today
means iterating every allocatable and inspecting its permission list by hand.

## Scope

**In:**
- Extend the existing `allocatables(filter:)` query (PRD 035 / 059 / 066) with
  three optional, mutually-exclusive-by-target fields:
  - `accessibleByUser: ID` — restrict to allocatables the given user has rights on.
  - `accessibleByGroup: [ID!]` — restrict to allocatables that **any** of the
    given groups (each incl. its parent cascade) has rights on. **Union**
    semantics — mirrors how rapla already computes a user's effective access as
    the union over all their groups. Exactly one *target kind* may be set
    (`accessibleByUser` XOR `accessibleByGroup`); every group id must be within
    the caller's admin scope.
  - `accessLevel: AccessLevel = READ` — minimum access level the target must
    hold (e.g. `ALLOCATE` → only resources the target may book).
- A new GraphQL `enum AccessLevel` mirroring `Permission.AccessLevel`
  (DENIED, READ_TYPE, CREATE, READ_NO_ALLOCATION, READ, REQUEST, ALLOCATE,
  ALLOCATE_CONFLICTS, ADMIN).
- Server helper (rapla-core `PermissionController`) computing the effective
  access level a **user** *or* a **group** holds on a given allocatable,
  honouring group-parent cascade and owner/global-admin implicit rights.
- Admin-scope enforcement in the resolver; §12-safe intersection with the
  caller's own `canRead`.
- **Events**: extend `reservations(filter:)` (PRD 055) with the same
  `accessibleByUser` / `accessibleByGroup` + `accessLevel` fields, evaluated
  against the *reservation* permission model (owner / event-type rights /
  `read-events-from-others`) — see the Events section for the exact semantics.

**Out (deferred / explicitly not now):**
- Time-window evaluation of ALLOCATE permissions (`start/end/min/maxAdvance`).
  v1 ignores time constraints — "holds the permission" is date-independent.
  Note this limitation in the schema doc.
- A dedicated top-level query (`resourceAccess(...)`) — folded into
  `allocatables(filter:)` instead for resolver reuse. Revisit only if the
  filter semantics get awkward.
- REST equivalent, Swing UI, SPA wiring (separate follow-ups if wanted).
- Non-admin self-query ("which resources can *I* access") — already covered by
  the plain `allocatables` query (everything the caller can read).

## Plan

1. **Schema** — add `AccessLevel` enum + the three filter fields to
   `AllocatableFilter` in `schema.graphqls`, with doc comments (incl. the
   time-constraint caveat and the admin-scope rule).
2. **Permission helper** (rapla-core) — add to `PermissionController` a method
   to get the effective `AccessLevel` for a `User` on an allocatable, and one
   for a `Category` (group) on an allocatable (matches permission whose group
   == target or is an ancestor of target). Reuse `PermissionContainer.Util`.
3. **Resolver** (`ClassificationGraphQLController.allocatables`) —
   - Parse the new filter fields.
   - If `accessibleByUser`/`accessibleByGroup` set: resolve the target;
     enforce caller admin scope (`canAdminUser` / `canAdminGroup`). On failure
     throw a uniform "forbidden" GraphQL error — identical whether the id is
     unknown or merely out-of-scope.
   - Filter the (already §12-visible) allocatable set to those where the
     target's effective level `>= accessLevel`.
4. **Wire** through any generated-where / filter plumbing without disturbing
   existing predicates (additive only).

## Tests

- **tier-2** (rapla-server, `FacadeTestSupport`): permission-effect helper —
  fixture with a group permission on some allocatables and a user permission on
  others; assert the helper returns the correct set; group permission cascades
  to a child-group member; `accessLevel` threshold respected (READ-only
  resource excluded when ALLOCATE requested); owner/global-admin implicit
  rights surfaced.
- **tier-3** (rapla-app, `@SpringBootTest` + MockMvc → `HttpGraphQlTester`):
  - admin caller querying a user/group **within** their admin scope → exactly
    the expected resources.
  - **§12 leak test**: non-admin caller, or admin caller targeting an
    out-of-scope user/group, → forbidden; response **byte-identical** for
    "target id does not exist" vs "exists but out of scope".
  - result never includes a resource the **caller** cannot `canRead` (target
    can access it but caller can't → dropped).

## Events (Veranstaltungen) — the permission model differs

Resources carry an explicit per-entity permission list (user/group → level), so
the reverse lookup is "scan each allocatable's permission list". **Reservations
do not.** A reservation's read/edit access derives from:

- **owner** — the owner may read & modify their own events;
- **event type** — the reservation's `DynamicType` permission list grants
  READ / CREATE / EDIT to users/groups (this is where a *group* gets event
  rights);
- **`read-events-from-others`** — the global group permission that lets a user
  see events they don't own;
- **admin** — global admins read & edit everything.

`PermissionController.canRead(Reservation, user)` and
`canModify(Reservation, user)` already encode all of this — the resolver reuses
them rather than reimplementing.

**Target = user** (clean): a reservation is included when
`canRead(r, targetUser)` (for `READ`) / `canModify(r, targetUser)` (for `ADMIN`/
edit) is true. In practice for a non-admin target this is mostly "events they
own" plus any events their groups' type-level permissions grant.

**Target = group** (ambiguous — needs a decision, see Open Questions): two
plausible meanings —
1. **type-level**: events whose `DynamicType` grants the group READ/EDIT
   (mirrors the resource semantics — the group literally holds the right); vs.
2. **membership**: events owned by *any member* of the group.

Proposed v1: **type-level** (1), to stay consistent with the resource case and
because (2) is composable client-side via `users(filter: inGroup)` →
`reservations(ownerEq:)`. Flag (2) as a future option.

Note: the `reservations(filter:)` window (`from`/`to`) stays mandatory — the
access predicate narrows *within* the requested window, it does not replace it.

## Example queries

How the extended `allocatables(filter:)` looks in practice:

```graphql
# Which resources may user u-123 EDIT (modify/administer)?
query {
  allocatables(filter: { accessibleByUser: "u-123", accessLevel: ADMIN }) {
    id
    displayName
    type { key }
  }
}

# Which resources may the groups g-tutors / g-staff READ (union)?
query {
  allocatables(filter: { accessibleByGroup: ["g-tutors", "g-staff"], accessLevel: READ }) {
    id
    displayName
  }
}

# Narrow with the existing predicates — which ROOMS may group g-fac edit?
query {
  allocatables(filter: {
    accessibleByGroup: ["g-fac"]
    accessLevel:       ADMIN
    typeKeyEq:         "room"
  }) {
    id
    displayName
  }
}
```

```graphql
# Which events in March may user u-123 EDIT? (mostly: events they own)
query {
  reservations(filter: {
    from: "2026-03-01T00:00:00"
    to:   "2026-04-01T00:00:00"
    accessibleByUser: "u-123"
    accessLevel:      ADMIN
  }) {
    id
    name
  }
}

# Which events may group g-fac READ (via the event type's group permission)?
query {
  reservations(filter: {
    from: "2026-03-01T00:00:00"
    to:   "2026-04-01T00:00:00"
    accessibleByGroup: ["g-fac"]
    # accessLevel omitted → defaults to READ
  }) {
    id
    name
  }
}
```

Reuse of the existing `AllocatableFilter` means every current predicate
(`typeKeyIn`, `searchText` + `matchKind`, the generated `whereXxx` typed
attribute filters, `limit`, …) composes with the new access fields for free —
e.g. *"rooms with > 30 seats that group g-fac may edit"*.

## Future query space

Once the access-by-target mechanism exists, these queries become possible
(some need follow-up work, noted):

- **Read/visibility audit** — "what can this (leaving) user still see?" for
  offboarding / data-protection reviews. *(this PRD)*
- **Edit/delegation overview** — "which resources does group G administer?" to
  review who owns what before a reorg. *(this PRD)*
- **Booking-rights overview** — `accessLevel: ALLOCATE` → "what could this group
  book?" (capacity planning). *(this PRD, modulo the time-constraint caveat)*
- **Typed/filtered access** — combine with `whereXxx` / `searchText`:
  "lecture-halls > 100 seats that group g-fac may edit". *(this PRD)*
- **Effective-level-per-resource** — return the *level* the target holds on each
  result (read vs edit vs admin) in one query, instead of one query per level.
  *(future: add an `effectiveAccess(forUser/forGroup): AccessLevel` field on
  `Allocatable`.)*
- **Reverse direction** — given one resource, "which users/groups in my admin
  scope can edit it?" (inverse lookup). *(future: separate field/query.)*
- **Cross-target diffs** — "resources user A can edit but user B cannot" for
  permission-migration sanity checks. *(future: client-side composition of the
  above, no new server work.)*

## Open Questions

- Schema shape: extend `AllocatableFilter` (planned) vs. a dedicated
  `resourceAccess` top-level query? Going with the filter extension for
  resolver reuse; revisit if semantics get muddy.
- Should `accessibleByUser` also become a list (`[ID!]`, union) for symmetry,
  or stay single? Currently single (one concrete person); group is a list.
- Time-constrained ALLOCATE permissions — out for v1; confirm that's
  acceptable for the intended use (likely "who *could* book X" overview).
- Does the SPA need this now, or is it a server/MCP-facing capability first?
- **Events + group target semantics** — type-level (group holds the event-type
  right) vs. membership (events owned by group members)? Proposed v1:
  type-level; membership deferred (composable client-side).

## Decisions

- **`accessLevel` default = `READ`** (confirmed 2026-06-17). Omitting the field
  answers the "what can they see" question; `ADMIN` answers "what can they
  edit".
- **`accessibleByGroup` is a list `[ID!]` with union semantics** (confirmed
  2026-06-17) — "any of these groups". Consistent with rapla computing a user's
  effective access as the union over their groups. Intersection ("all groups")
  is out of scope. `accessibleByUser` XOR `accessibleByGroup` (one target kind).
