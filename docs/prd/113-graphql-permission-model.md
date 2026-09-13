# PRD 113 — Permissions in the GraphQL model

**Status:** draft v1 — 2026-09-07, design mostly settled; 7 open questions remain (§ 4). Coordinator: rapla-62; drafted in the concept sessions. Decisions below marked *user ruling* were given in a concept session and await the coordinator's confirmation with the user.

**Decided:** the entity set and level matrix (§ 1a), the `DynamicType` two-list split (§ 1b), the `EventTemplate` entity (§ 1c), the merged create/update inputs (§ 1d — implemented under WP1, this PRD only consumes it), the three typed permission inputs and `@oneOf` principals (§ 2a), write-through-save-inputs (§ 2b), full principal expansion for `canAdmin` callers (§ 4 OQ 3), and that read and write ship in one delivery (§ 4 OQ 1).

**Open:** OQ 5 (reservations in v1 — also a security decision, see § 2c), OQ 6 (`effectiveAccess`), OQ 7 (windows in v1 input), OQ 10 (`READ_NO_ALLOCATION`), OQ 11 (group-membership editing), OQ 12/13 (template & period write surface, verb merge). Plus: explicit go for the `path` deletion (AGENTS.md § 11), confirmation that `DENIED` needs no handling, and routing for the § 2c gate gap. Not yet ruled: whether users/groups/categories stay read-only with membership editing in a sibling PRD (OQ 11), and `Group.parent`/`children` for the picker.

**Related:** [permissions.md](../architecture/permissions.md) (model + per-target level matrix), [ADR 0003](../decisions/0003-permissions-are-grant-only.md), [PRD 090](090-additive-permission-resolution.md) (max-wins, landed — see § GraphQL implications), [PRD 069](069-graphql-resource-access-read-api.md) (`accessibleBy*` / `AccessLevel` enum, landed), [PRD 063 OQ2](063-graphql-allocatables-write-api.md#oq2--permission-editing-on-allocatables) (deferred `setAllocatablePermissions`), [PRD 083](083-user-change-subscription.md) (`access_grant` read index), [PRD 035 §11](done/035-graphql-foundations.md) (typeKey-only), [PRD 061](061-graphql-dt-mutations-v2.md) / [063](063-graphql-allocatables-write-api.md) (mutation conventions), AGENTS.md §12.

## 1. What "permissions in the GraphQL model" means

Today GraphQL has **resolution results only**: the `accessibleBy*` filters (PRD 069, admin-scoped reverse lookup) and the implicit "you only see what you can read" gate. The **rows themselves** (`Permission`: principal, level, window) are neither readable nor writable — the Swing permission tab is the only editor. Both halves are needed and ship together (OQ 1); the order below is the order of the work, not of two releases:

- **Read exposure (Phase 1):** a `permissions` list on each permission-carrying API entity plus an `effectiveAccess` read. Without it the SPA cannot render a permission editor at all.
- **Write mutations (Phase 2):** replace the rows. Server gate = the one already enforced for every path: a changed permission list requires `canAdmin(entity, caller)` (`SecurityManager.checkPermissions`, audit fix 2026-08); global admin bypasses. Editing a `DynamicType` itself is global-admin only (`RaplaDefaultPermissionImpl.hasAccess` hardcodes it).

Categories/groups stay out (their "permissions" are the admin-group mechanism, PRD 069 handles group targets). Attribute-level permissions (`PermissionController.canRead/canWrite(Classification, Attribute, User)`) exist in core but stay out until a consumer asks (PRD 061 deferral). Owner is not a permission row and stays on the existing `changeXOwner` verbs.

### 1a. API entities that carry permissions

Internally `Template` and `Period` are allocatables of the rapla-internal types; **in the API they are separate entities** (`EventTemplate` and `Period` already exist as types — `Period` needs an `id` before it can be a permission target, OQ 8). Level sets are the Swing editors' `setPermissionLevels(...)` calls; the reduced set is keyed on the type being *internal*, not on person vs resource (verified 2026-09-06 in `AllocatableEditUI.mapFromObjects`):

| API entity | Storage | Levels (Swing = spec) | Time windows |
|---|---|---|---|
| Resource / Person (`Allocatable`) | Allocatable, non-internal type | READ_NO_ALLOCATION, READ, REQUEST, ALLOCATE, ALLOCATE_CONFLICTS, EDIT, ADMIN | on ALLOCATE, ALLOCATE_CONFLICTS, EDIT rows only |
| Event (`Reservation`) | Reservation | READ, EDIT, ADMIN | none |
| `EventTemplate` | Allocatable `rapla:template` | READ, EDIT, ADMIN (`TemplateEdit`); READ = may see its reservations + instantiate, EDIT delegates `canModify` of template reservations; new template starts owner-only | none |
| `Period` | Allocatable `rapla:period` | READ, EDIT, ADMIN (internal-type branch) | none |
| `DynamicType` | DynamicType | split, see 1b | none |

**Time-based rules — the complete list.** The window fields (`start`/`end` absolute, `minAdvance`/`maxAdvance` days) live on every row but are evaluated only when *all* hold (`RaplaDefaultPermissionImpl.hasAccess`): the row has limits; the requested level includes ALLOCATE or REQUEST; the caller passed `today` — only `canAllocate`, `canRequest`, `isRequestOnly`, `hasPermissionToAllocate` and `getInterval` do, `canRead`/`canReadInformation`/`canModify`/`canAdmin`/`canCreateConflicts` pass null; the row's own level is not ADMIN. `hasPermissionToAllocate` adds: for an existing appointment the window only has to cover the *changed* part. Swing shows the window panel only for rows whose level includes ALLOCATE and excludes ADMIN (ALLOCATE, ALLOCATE_CONFLICTS, EDIT — not REQUEST, although the server would honour a windowed REQUEST row). Consequence: only Resource/Person rows are ever windowed; a window on Event/Template/Period/DynamicType rows is dead data (Swing still shows the panel on an EDIT row there). **API rule:** window fields accepted only on Resource/Person rows at those three levels; rejected (`INVALID_VALUE`) elsewhere — a deliberate, stricter-than-Swing rule because no call path evaluates them.

### 1b. `DynamicType` — one stored list, two API lists

The stored `DynamicType` permission list serves two purposes and the server reads it two ways:

- **Type-scoped rows, evaluated live against the type:** `canRead(type)` = exact match on READ_TYPE | CREATE | ADMIN; `canCreate(type)` = exact match on CREATE | ADMIN (`PermissionController.matchesAccessLevel`). A READ or EDIT row on the type does *not* make the type visible. Instance reads require `canRead(type)` first (`canReadPrivate` → `canReadType`).
- **Instance-default rows, copied at create time** (`PermissionContainer.Util.copyPermissions`: every row except READ_TYPE and CREATE is cloned onto a new allocatable/reservation). Never consulted on the type again — editing the type later changes nothing on existing instances. One non-enforcement exception: the PRD 069 group reverse-lookup also scans the reservation type's list live.

API projection (storage untouched, Swing keeps working on the one list):

```graphql
type DynamicType {
  typeAccess:       [Permission!]   # READ_TYPE, CREATE only; principal group | everyone (Swing hides the user selector)
  instanceDefaults: [Permission!]   # the copied rows: resource set for resource/person types, READ/EDIT/ADMIN for event types
}
# Write rides on the existing verb (shape D, OQ 2) — both nullable, null = untouched:
# saveDynamicType(input: { ..., typeAccess: [TypeAccessInput!],
#                          resourceInstanceDefaults: [ResourcePermissionInput!],
#                          eventInstanceDefaults:    [SimplePermissionInput!] }, expectedLastChanged)
# Output keeps ONE instanceDefaults list; only the INPUT splits by classification type (§ 2a).
# Server rejects whichever input list does not match the type's classificationType.
```

- **ADMIN is not a `typeAccess` level** — editing a type is global-admin only, so a type-level ADMIN grant has no meaning. An ADMIN row belongs to `instanceDefaults` (instances get an ADMIN row). Server quirk kept and documented: the stored ADMIN row still satisfies the exact-match `canRead(type)`/`canCreate(type)` — harmless (an instance admin needs to see the type) and untouched here.
- Write merges both inputs into the one stored list; validation is per list (READ_TYPE/CREATE only in `typeAccess`, instance set only in `instanceDefaults`, no user principals in `typeAccess`). `instanceDefaults` rows follow the same window rule as the instances they are copied to (Resource/Person: windows on ALLOCATE, ALLOCATE_CONFLICTS, EDIT rows, absolute or relative — user ruling, OQ 9).
- Swing today offers resource/person types the full enum (no `setPermissionLevels` call → default list minus DENIED), event types READ_TYPE, CREATE, READ, EDIT, ADMIN. The `permissions.md` matrix lists only the event-type set for `DynamicType` — to be corrected with this PRD.

### 1c. `EventTemplate` target schema

Today `EventTemplate` is a picker record (`id`, `name`, `path`) reachable only through `newEventOptions`, which is gated on "may create reservations" + the templatewizard flag — a template admin who cannot create events has no path to their template. Target:

```graphql
type Query {
  newEventOptions: NewEventOptions!                    # unchanged gate
  reservationsFromTemplate(templateId: ID!): [Reservation!]!   # unchanged
  # NEW — entity reads gated by canRead(template) only; unknown/unreadable → null / absent (§12)
  eventTemplate(id: ID!): EventTemplate
  eventTemplates: [EventTemplate!]!
}
type NewEventOptions { eventTypes: [DynamicType!]!  templates: [EventTemplate!]! }

"An event template (rapla:template). Stored as an allocatable; separate entity in the API."
type EventTemplate {
  id:                   ID!
  name:                 String!          # locale-resolved
  "Swing keepTime — day-offset vs minute-exact shift on instantiation (event-templates.md)."
  fixedTimeAndDuration: Boolean!
  owner:                User
  createdAt:            DateTime
  lastModifiedAt:       DateTime
  canModify:            Boolean!         # mirror of Allocatable.canModify
  canAdmin:             Boolean!         # may edit the permission list
  permissions:          [Permission!]    # null unless canAdmin (§12)
}
```

- **`path` is deleted** (user ruling): PRD 104 D1 revision replaced the Swing tree with a flat searchable list and states `path` is not consumed by the v1 UI. Removal residue: `EventTemplate.path` in the schema, `TemplatePathBuilder` + its tests, the `path` selection in `new-event-options.service.ts`. Grouping, if ever needed, is a client-side sort concern.
- No `classification` field — the template type has exactly two attributes and internal types are excluded from the generated classification interfaces on purpose.
- **`updateEventTemplate(id: ID!, input: EventTemplateInput!, expectedLastChanged: LocalDateTime)` is IN** (*user ruling 2026-09-06, reported by the concept session*), and the same for `updatePeriod`. This revises the earlier "no template create/update/delete": **update** is in; **create/delete** were not ruled on and stay out pending OQ 12. Field lists, `save*`-vs-`update*` naming, and whether create comes along are all open — see OQ 12, revised 2026-09-07; the lists sketched here earlier are superseded by the proposal recorded there.
- `canAdmin` goes on every permission-carrying entity (Allocatable, Reservation, Period, DynamicType) for the same reason. **`allowedLevels` is dropped** (*user ruling 2026-09-06, § 2a*): with three typed input enums the SPA reads the permitted set from schema introspection (`ResourceAccessLevel` / `SimpleAccessLevel` / `TypeAccessLevel`) instead of from a server-served list, so the field would be a second source of truth for something the schema already states.

### 1d. One input per entity — create/update inputs merged

*User ruling 2026-09-07, confirmed directly to the coordinator.* Scope addition, ruled while designing shape D and landing **before** the `permissions` field so that field is added once per entity rather than to two inputs each. Follows the `saveDynamicType` / `DynamicTypeInput` precedent. **Early beta — breaking schema changes are allowed.**

The merge is mechanical: today's update inputs are exact subsets of the create inputs (verified 2026-09-07 against `schema.graphqls`), differing only by `id` and `ownerId`.

**Delivered by WP1, not by this PRD** — landed 2026-09-07 (coordinator-verified, uncommitted; owner: impl). PRD 113 consumes the result and adds exactly one field to each input. Below is the landed schema verbatim, with the PRD 113 addition marked:

```graphql
input ReservationInput {
  "Client-minted UUID for retry safety + same-batch refs (PRD 056 §9). Required on create; on update must be absent or equal to the id argument (INVALID_VALUE otherwise)."
  id:             ID
  "Discriminator — must match the @oneOf variant in classification (the DynamicType key, e.g. \"event\")."
  typeKey:        String!
  classification: ReservationClassificationInput!
  "Must be non-empty (PRD 056 OQ1.d). Order-significant."
  appointments:   [AppointmentInput!]!
  "Restriction-aware allocations. Empty list valid (reservation with no resources is legal but unusual)."
  allocations:    [AllocationInput!]!
  permissions:    [SimplePermissionInput!]   # ← PRD 113 (§ 2 shape D / § 2a), null = untouched. NOT part of WP1.
}

input AllocatableInput {
  "Client-minted UUID for retry safety + same-batch refs (PRD 056 §9). Required on create; on update must be absent or equal to the id argument (INVALID_VALUE otherwise)."
  id:             ID
  "Discriminator — must match the @oneOf variant in classification (the DynamicType key, e.g. \"room\")."
  typeKey:        String!
  classification: AllocatableClassificationInput!
  "Create-only, admin-only. Null = caller becomes owner. Non-null on update is rejected."
  ownerId:        ID
  permissions:    [ResourcePermissionInput!] # ← PRD 113 (§ 2a). NOT part of WP1.
}
```

`ReservationCheckInput.draft` is now typed `ReservationInput!` (landed with WP1) — noted because `checkReservation` is a read-only validation path: a `permissions` list arriving inside a draft must be **ignored**, never applied, since nothing is written there.

Replaces `CreateReservationInput` + `UpdateReservationInput` and `CreateAllocatableInput` + `UpdateAllocatableInput`. `EventTemplateInput` / `PeriodInput` (§ 1c) are single from birth. `ChangeOp`'s `createReservation` / `updateReservation` / `createAllocatable` / `updateAllocatable` variants all take the merged input; the variant, not the input, keeps saying which operation it is.

**Fields invalid for the operation are rejected, never silently ignored** — a silently-dropped `ownerId` looks like a successful owner change to the caller:

| Field | On create | On update |
|---|---|---|
| `id` | **required** — absent or blank → `REQUIRED`. Client-minted UUID; the server-generate fallback was removed by [PRD 056 § 9](056-graphql-events-write-api.md#9-client-supplied-ids--contract-rule--operator-side-integrity-guard-2026-07-06) (revised 2026-07-06), because an id-less create is structurally non-idempotent — retry-safety exists only via the client id. The GraphQL type stays nullable `ID` because *update* may omit it; requiredness on create is a resolver rule, not a type rule | absent, or equal to the mutation's `id:` argument. **Present and different → `INVALID_VALUE` at `input.id`** — reject rather than ignore, so a client sending the wrong id learns about it. Same rule on the batch path, error path prefixed `operations[i].updateReservation` |
| `ownerId` | null = caller; non-null = admin-only | non-null → **`INVALID_VALUE` at `input.ownerId`** — owner changes go through `changeReservationOwner` / the deferred `changeAllocatableOwner` (PRD 063) |
| `typeKey` | discriminator, must match the `@oneOf` classification variant | defensive cross-check, must equal the stored type |
| `permissions` | null = type defaults copied as today; non-null = the initial list | null = untouched; non-null = replace (§ 2) |

**Verbs are unchanged** (confirmed landed) — `createReservation` / `updateReservation(id:, ...)` and the allocatable pair stay separate; only the input types merge. Full `saveDynamicType` parity would also collapse each pair into one `saveX(input, expectedLastChanged)` verb discriminated by `input.id`, which the ruling did not ask for; that ambiguity is OQ 13. Keeping the verbs separate is why the update path still needs an `id:` argument and why `input.id` is redundant-but-tolerated there.

**Residue for the same work package** (SPA sends these input names in its mutation documents; verified 2026-09-07):
`rapla-angular/src/app/event/event-data.service.ts:121` (`UpdateReservationInput`), `:130` (`CreateReservationInput`), and `rapla-angular/src/app/allocatable/allocatable-data.service.ts:127` (`UpdateAllocatableInput`). Renaming the inputs without these breaks the SPA's writes at query-validation time — per AGENTS.md § 7a the server change lands first, the SPA follows.

## 2. The design

Permissions are **read** as a `permissions` list on each permission-carrying entity and **written** by passing that list back inside the entity's existing save mutation. No `setPermissions` verb family, no new `ChangeOp` variants, no second concurrency story.

### 2a. Input types

Output is **one** `Permission` type carrying the full `AccessLevel` enum. Input is **three** types, each with its own level enum, following the PRD 055/056 typed-input precedent: the level matrix of § 1a / § 1b is *unrepresentable* rather than server-validated.

```graphql
type Permission {                       # output — unchanged, full enum
  principal: PermissionPrincipal!
  level:     AccessLevel!
  start: LocalDateTime  end: LocalDateTime
  minAdvance: Int       maxAdvance: Int
}
"Exactly one populated (a type, not a union — `everyone` has no entity to point at)."
type PermissionPrincipal { user: User  group: Group  everyone: Boolean! }

input PrincipalInput          @oneOf { userId: ID  groupId: ID  everyone: Boolean }
input TypeAccessPrincipalInput @oneOf {            groupId: ID  everyone: Boolean }

enum ResourceAccessLevel { READ_NO_ALLOCATION READ REQUEST ALLOCATE ALLOCATE_CONFLICTS EDIT ADMIN }
enum SimpleAccessLevel   { READ EDIT ADMIN }
enum TypeAccessLevel     { READ_TYPE CREATE }

"Resource/Person rows, and instanceDefaults of resource/person types."
input ResourcePermissionInput {
  principal: PrincipalInput!  level: ResourceAccessLevel!
  start: LocalDateTime  end: LocalDateTime  minAdvance: Int  maxAdvance: Int
}
"Event, EventTemplate and Period rows, and instanceDefaults of event types."
input SimplePermissionInput { principal: PrincipalInput!  level: SimpleAccessLevel! }
"DynamicType.typeAccess — no user principal (Swing hides the user selector)."
input TypeAccessInput { principal: TypeAccessPrincipalInput!  level: TypeAccessLevel! }
```

**What the schema enforces, and what the server must.** Stated so neither is built twice nor forgotten:

| Rule | Enforced by |
|---|---|
| `READ_TYPE`/`CREATE` on an Allocatable; `REQUEST` on an Event/Template/Period; `ADMIN` in `typeAccess` | **schema** — the level enums make it unrepresentable |
| No user principal in `typeAccess` | **schema** — `TypeAccessInput` has no `userId` |
| `DENIED` as an input level | **schema** — absent from all three enums |
| Window only on `ALLOCATE` / `ALLOCATE_CONFLICTS` / `EDIT` (§ 1a) | **server** — level and window are sibling fields; types cannot couple them |
| Absolute XOR relative window | **server** |
| Exactly one of `userId` / `groupId` / `everyone` | **schema** — `@oneOf` on `PrincipalInput` / `TypeAccessPrincipalInput` |
| `everyone: false`; `groupId` outside the `user-groups` subtree | **server** — `INVALID_VALUE` / unknown-id response respectively |

**Principal shape.** `@oneOf` makes exactly-one structural. **`everyone` must be `true`; `false` → `INVALID_VALUE`.** A `groupId` outside the `user-groups` subtree answers exactly like an unknown id (§ 12 — no existence signal for categories that are not permission groups).

Storage mapping, mirroring `PermissionImpl`'s mutually-exclusive setters:

| Input | Stored |
|---|---|
| `userId` | sets `user`, clears `group` |
| `groupId` | sets `group`, clears `user` |
| `everyone: true` | clears both |

and on the way back, **both-null reads as `everyone: true`** — see OQ 4, which this makes unambiguous.

### 2b. Write — permissions ride inside the existing save inputs

Every container already has a save mutation; the permission list is one more nullable field on its input.

```graphql
input AllocatableInput   { ...  permissions: [ResourcePermissionInput!] }   # merged input, § 1d
input ReservationInput   { ...  permissions: [SimplePermissionInput!] }     # merged input, § 1d
input EventTemplateInput { ...  permissions: [SimplePermissionInput!] }     # § 1c
input PeriodInput        { ...  permissions: [SimplePermissionInput!] }     # § 1c
input DynamicTypeInput   { ...  typeAccess:               [TypeAccessInput!]
                                resourceInstanceDefaults: [ResourcePermissionInput!]
                                eventInstanceDefaults:    [SimplePermissionInput!] }
```

- **`null` = untouched** (on create: type defaults apply as today, `PermissionContainer.Util.copyPermissions`); **non-null = replace the whole list**. The distinction is load-bearing and mirrors the read side, where `permissions` is `null` for a caller without ADMIN — a read-modify-write client that fetched `null` must send `null` back. `[]` means "remove every row", needs `canAdmin`, and fails closed with `PERMISSION_DENIED` for a caller who never saw the list. `[]` and `null` are different requests.
- **Batch writes come for free.** `ChangeOp` embeds the same inputs, so `applyChanges` carries permission edits with no new variant.
- **Gate:** the existing one. `SecurityManager.checkModifyPermissions` compares old vs new via `PermissionContainer.Util.differs` and demands `canAdmin(original, caller)` only when the list actually changed, so an EDIT-only caller sending an unchanged list still saves. One gap — § 2c.
- **Read gate:** the `permissions` field is non-null iff `canAdmin(entity, caller)`, else `null`. User resolution inside a row is not re-filtered (§ 4 OQ 3).

### 2c. Gate gap on `Reservation` — open security item

`SecurityManager.checkModifyPermissions` (`rapla-server/.../SecurityManager.java`, the ACL block after the reservation checks) reads:

```java
if ((entity instanceof Allocatable || entity instanceof Category)
        && original instanceof PermissionContainer) { ...canAdmin... }
```

`Reservation` **is not in that list**, and nothing else re-checks a reservation's ACL. So today a non-owner with `EDIT` on a reservation passes `canModify`, reaches the store, and can rewrite that reservation's permission list — including granting themselves `ADMIN` on it or granting a third party `READ`. This is **pre-existing and reachable from Swing** (`ReservationInfoEdit` carries the permission tab), not introduced by shape D — but shape D would expose it through `updateReservation` as well. `EventTemplate` and `Period` are stored as allocatables and are therefore already covered; `DynamicType` is excluded deliberately (fully admin-gated upstream).

Consequence for this PRD: if OQ 5 puts reservations in v1, extending that `instanceof` to `Reservation` is a **prerequisite**, not a nice-to-have — with a failing test first per AGENTS.md § 1. If OQ 5 leaves reservations out, the gap still exists and should be raised as its own security fix rather than left unrecorded.

### 2d. Read helper — `effectiveAccess`

`effectiveAccess(userId: ID, groupKey: String): AccessLevel` on each container — the PRD 090 "reveal the no-op" field; admin-scoped like PRD 069 (target outside `canAdminUser`/`canAdminGroup` → uniform `PERMISSION_DENIED`, no existence leak). Self-query without argument is always allowed. **In or out of this delivery is OQ 6.**

### 2e. Appendix — shapes considered and rejected

Recorded so they are not re-proposed. All three were superseded by § 2b.

- **Shape A — a typed `setXPermissions` verb per entity.** Rejected: four near-identical verbs plus `ChangeOp` variants, duplicating a save path and a concurrency argument that already exist.
- **Shape B — one generic `setPermissions(target: EntityRef!)`.** Rejected: it cannot reuse `EntityKind` (`schema.graphqls:1662` — that enum is only `ChangeResult.deletedKind`, has a meaningless `PERMISSION` member and no TEMPLATE/PERIOD/DYNAMIC_TYPE), so it needs a new enum plus a five-way gate switch and a fragment spread at every call site.
- **Shape C — row-level `grantAccess` / `revokeAccess`.** Rejected for v1: it does not map onto the Swing table ("edit this row's end date" becomes revoke + grant, two change records). Layerable later if the SPA editor is ever designed as a grant list rather than a table.

## 3. Mapping onto the Swing permission editor ("wie Swing")

| Swing (`PermissionListField` / `PermissionField`) | GraphQL |
|---|---|
| Row principal: user, group, "all users" (`ALL_USER_PERMISSION`) | `PrincipalInput` exactly-one rule (§ 2a); `NO_PERMISSION` (disabled row) has no input form — dropped on read? (OQ 4) |
| Level dropdown = `setPermissionLevels(...)` per dialog (`AllocatableEditUI` internal vs non-internal branch, `TemplateEdit`, `ReservationInfoEdit`, `DynamicTypeEditUI`) | server validation against the table in § 1a / 1b per API entity, `INVALID_VALUE` on mismatch |
| `DENIED` only shown on rows that loaded with it | output enum keeps `DENIED`; input enum has none; save refused while a `DENIED` row exists |
| Absolute vs relative window radio (mutually exclusive) | both pairs set → `INVALID_VALUE`; entity setter semantics preserved |
| Tab shown inside the edit dialog; save goes through `checkPermissions` → `canAdmin` | same server gate, no client-side shortcut |
| Call paths that also carry permission lists: copy resource / copy event (permissions copied), templates → reservation (`reservationsFromTemplate` copies), new entity defaults (`addDefaultResourcePermissions`, type default rows) | unchanged — the new verbs only replace an existing container's list; parity check: every path above must keep working without calling the new verb |

Parity is server-side by construction: the one `PermissionController` / `SecurityManager` gate covers Swing, GraphQL, iCal export. What is *not* automatic is the level matrix — Swing enforces it in the UI only; GraphQL must enforce it in the resolver (permissions.md flags this).

## 4. Open questions for the user

1. ~~**Scope of v1**~~ — *user ruling 2026-09-06 (reported by the concept session):* **read and write ship in one delivery**, not read-only first. The Phase 1 / Phase 2 labels in § 1 stay as an ordering of the work inside that delivery, not as two releases.
2. ~~**Write shape**~~ — *user ruling 2026-09-06 (reported by the concept session):* **shape D** — permissions ride inside the existing save mutations as nullable inputs; no `setPermissions` verb family. A/B/C withdrawn (§ 2). Follow-on recorded there: the existing ACL gate does not cover `Reservation`, which makes OQ 5 a security decision as well as a scope one.
3. ~~**Principal visibility on rows**~~ — *user ruling 2026-09-06 (reported by the concept session):* a caller with `canAdmin(entity)` may expand **every** row of that entity to user id + name, including users outside the caller's `canAdminUser` scope. Adding a *new* user row uses the SPA picker, which offers only users the caller can already see through the existing `users(filter:)` read scope — no new server surface. Server rule: `permissions` is non-null iff `canAdmin(entity)`, and user resolution inside a row is **not** re-filtered.
    **Consequences, recorded deliberately.** Whole-list replace round-trips completely and the hidden-row problem disappears — the `HiddenPrincipal` sketch is withdrawn along with option (a). In exchange this is a **conscious widening of read scope relative to Swing**: per [permissions.md § Server enforcement](../architecture/permissions.md#server-enforcement) the Swing client store holds only self + admins + administered-group members, so Swing *cannot* render those names today and shows an unresolvable reference. It is therefore not "Swing parity" but an intentional decision that entity-admin scope is sufficient to see who holds a grant on that entity. Recorded here as an accepted, documented deviation from the AGENTS.md § 12 default rather than an oversight, and pinned by a test (§ 6) so a later reader does not "fix" it back.
4. ~~**Disabled rows (`NO_PERMISSION`)**~~ — *closed 2026-09-06 as not applicable (user, reported by the concept session; verified in code 2026-09-07).* `NO_PERMISSION` is a **match result**, never a stored state: `PermissionContainer.Util.getUserEffect` returns `ALL_USER_PERMISSION` when both `user` and `group` are null, and returns `NO_PERMISSION` only as the "this row does not match this user" fallthrough. So both-null unambiguously means *everyone*; there is no disabled row to drop on read or reject on write.
    **Docs bug found while verifying — [permissions.md](../architecture/permissions.md) is wrong here** and should be corrected in a separate pass (not touched by this PRD): its `PermissionImpl` section lists *"Two special cases for `(user, group)` both null: `NO_PERMISSION` (-2) — disabled row; `ALL_USER_PERMISSION` (-1) — applies to everybody"*, which reads as two stored states for one storage shape. Only the second exists.
5. **Reservations:** include from v1, or resources + templates + periods + dynamic types only (Reservation's own permission list is rarely edited outside sharing)? **Now carries a prerequisite:** § 2's gate gap means including reservations requires first extending `SecurityManager.checkModifyPermissions` to `Reservation`. *Lean: include, and fix the gate — the gap is real whether or not this PRD ships, and leaving `updateReservation.permissions` ungated would be a new §12-class hole rather than an inherited one.*
6. **`effectiveAccess`:** include in this delivery, or defer to the SPA-editor PRD? (OQ 1 removed the read-only-first option; the question is now in-or-out, not early-or-late.) Its admin-scoping is a second §12 surface (PRD 069 rules: target outside `canAdminUser`/`canAdminGroup` → uniform `PERMISSION_DENIED`; self-query always allowed).
7. **Time window on windowed grants:** input accepts `start/end/minAdvance/maxAdvance` from v1 (Resource/Person only, § 1a), or level + principal only first (PRD 069 v1 also ignored windows)?
8. ~~**`Period` id**~~ — *user ruling 2026-09-06:* add `id: ID!` to `Period`; name stays the display field. Additive only — no `period(id:)` query, no period CRUD in this PRD.
9. ~~**`instanceDefaults` windows**~~ — *user ruling 2026-09-06:* keep parity with Swing. Defaults accept the same levels and windows a resource row accepts, absolute windows included (use case: a one-year absolute default that the admin re-sets every year). No API-side restriction; a staleness hint is an SPA-editor concern.
10. **`READ_NO_ALLOCATION` in the editor:** the level is offered by Swing on Resource/Person rows, but the SPA cannot render its effect — `permissions.md` § 5 (verified 2026-06-24) records that the GraphQL catalog gates on `canRead` (100) while `READ_NO_ALLOCATION` is 50, so a resource granted only that level is **omitted from the SPA entirely** (Swing shows it with bookings hidden). An editor offering the level therefore produces resources its own client cannot display. Offer it anyway with a documented note (Swing parity — the boundary difference is pre-existing, PRD 083 scoped the index to GraphQL for exactly this reason), or drop it from `ResourceAccessLevel` (§ 2a — the level set is now the input enum, so dropping it removes the level from the API entirely, not just from an advisory list)? *Lean: offer with a note — hiding the level does not close the catalog gap, it only makes the editor misrepresent the model.*
11. **Group membership editing — in or out of PRD 113?** `permissions.md` § Resolution (PRD 090 / ADR 0003) states that access never subtracts and *"to narrow one person below their group, change group membership"* — membership is therefore the **only** downward lever in the model. GraphQL has no user/group/category write verbs today (verified 2026-09-06: the `Mutation` type carries reservation, allocatable and dynamic-type verbs only), so a permission editor built on this PRD alone is **structurally grant-only**: an admin can hand out access in the SPA and must return to Swing to take any of it back. This does not *gate* implementation — read-only pickers suffice — but it decides what v1 actually is. Three scopes: (a) nothing, 113 adds only `Group.parent`/`children` for the picker and the editor stays grant-only; (b) membership only — one verb (`setUserGroups(userId, groupIds)` and/or the group-side inverse), gated by the existing group-admin mechanism (`canAdminUsers` / `CAN_ADMIN_PARENT`, the same scope that already decides which users the caller can see at all), everything else read-only; (c) full user + group administration (user CRUD, account fields for other users, creating/renaming/re-parenting groups = categories under `user-groups`) — a separate PRD in its own right. *Lean: (b).* Sub-question if (b): does membership editing appear on the group side (pick members for a group) as well as the user side? Swing edits it from the user record only.

## 5. Plan

Read and write ship in one delivery (OQ 1). The order below is the work order; each step is independently compilable and testable.

| Step | Content | Depends on |
|---|---|---|
| — | § 1d input merge — **owned by WP1 / impl, not by this PRD**; PRD 113 depends on it and adds `permissions` to the merged inputs in P4 | prerequisite for P4 |
| P1 | `Permission` / `PermissionPrincipal` output types; the three input types + three level enums (§ 2a); `canAdmin` + `permissions` fields on Allocatable, Reservation, EventTemplate, Period; `Period.id` (OQ 8) and `Period.categories` (OQ 12) | — |
| P2 | `EventTemplate` promoted to an entity: `eventTemplate(id:)` / `eventTemplates` gated on `canRead`, the § 1c field set; `path` deleted with its residue (`TemplatePathBuilder` + tests, the SPA selection in `new-event-options.service.ts`) | P1; **deletion needs the user's explicit go per AGENTS.md § 11** |
| P3 | `DynamicType.typeAccess` / `instanceDefaults` read projection over the one stored list (§ 1b) | P1 |
| P4 | `permissions` added to `CreateAllocatableInput` / `UpdateAllocatableInput` (+ `UpdateReservationInput` if OQ 5 in); level-matrix validation and the § 1a window rule; null-vs-`[]` semantics. Concurrency is inherited — `expectedLastChanged` already exists on these verbs | P1–P3, OQ 3/4/5/7 |
| P4a | **Prerequisite if OQ 5 = in:** extend `SecurityManager.checkModifyPermissions`'s ACL check to `Reservation` (§ 2 gate gap), failing test first | before P4 |
| P5 | `saveDynamicType` gains nullable `typeAccess` / `instanceDefaults` (§ 1b merge + per-list validation); `updateEventTemplate` / `updatePeriod` per § 1c and OQ 12 | P3, P4 |
| P6 | `effectiveAccess` if OQ 6 says in; `Group.parent`/`children` for the picker; membership verb if OQ 11 says (b) | P1, OQ 6 / OQ 11 |

Out of scope, named so they are not assumed: the SPA permission editor itself (needs its own PRD — PRDs 090 and 096 both defer to one that does not exist), owner-change verbs for allocatables / templates / periods (PRD 063 deferred `changeAllocatableOwner`), attribute-level permissions (PRD 061 deferral), and user/group administration beyond OQ 11's ruling.

## 6. Tests

Tier per AGENTS.md § 10; the § 12 leak tests are mandatory, not optional (§ 12: *"never merge a new id-list / filter endpoint without a tier-3 MockMvc leak test"*).

**Tier 2** — `rapla-server/src/test/...`, `FacadeTestSupport`, no Spring:

- Level matrix — **mostly gone as a resolver test after § 2a.** `READ_TYPE`/`CREATE` on an Allocatable, `REQUEST` on a Reservation and `ADMIN` in `typeAccess` are now unrepresentable, so they are *schema* assertions (the query fails validation before any resolver runs), not `INVALID_VALUE` round-trips. Keep one tier-3 test per case asserting the request is rejected at validation, and delete the tier-2 matrix table — testing what the type system already guarantees is dead weight. What survives at tier 2 is only the rules § 2a leaves to the server: the window-level rule, absolute-XOR-relative, and `PrincipalInput` exactly-one.
- Window rule (§ 1a): window fields accepted on `ResourcePermissionInput` rows at ALLOCATE / ALLOCATE_CONFLICTS / EDIT; rejected `INVALID_VALUE` at READ_NO_ALLOCATION / READ / REQUEST / ADMIN; absolute and relative both set → `INVALID_VALUE`. (Event / EventTemplate / Period rows need no test — `SimplePermissionInput` has no window fields at all.)
- DynamicType round-trip: read splits one stored list into the two API lists (`typeAccess` + one `instanceDefaults`); write takes three input lists (§ 2a) and merges them back; the input list that does not match the type's `classificationType` is rejected — a `resourceInstanceDefaults` on an event type and vice versa; a stored ADMIN row surfaces in `instanceDefaults` and still satisfies `canRead(type)` afterwards (the documented § 1b quirk); a row absent from both inputs is gone after the write.
- Principal storage mapping (§ 2a): `userId` sets user and clears group, `groupId` the reverse, `everyone: true` clears both; a stored row with both null reads back as `everyone: true`. Round-trip a list through read → write → read and assert it is unchanged.
- `PermissionIndex` freshness after a permission write — *verification, not new machinery*: invalidation already hangs off `LocalAbstractCachableOperator.updateReadModel(UpdateResult)` with `permissionAffecting() = Allocatable | DynamicType | Category → invalidateAll()` (read 2026-09-06), which fires at the storage seam for every API. The test pins that a GraphQL permission write changes `readableAllocatables(user)` on the next read.

**Tier 3** — `rapla-app/src/test/...`, `@SpringBootTest` + MockMvc against `/api/graphql`:

- Leak test per new read field, § 12 recipe: non-admin caller, mixed readable / unreadable / non-existent ids in one request; response byte-identical to the visible-only subset, and identical again for the all-non-existent case. Covers `eventTemplate(id:)`, `eventTemplates`, `Period`, and the `permissions` field on each container.
- `permissions` returns `null` (not `[]`, not an error) for a caller with EDIT but not ADMIN on the entity — `[]` would assert "no rows exist", which is itself a leak.
- **OQ 3 pin (guards a deliberate § 12 deviation, do not "fix" it away):** a non-admin caller with `canAdmin` on one resource reads that resource's `permissions` and gets **id + name for every principal**, including a user outside their `canAdminUser` scope. Assert the name is present. The same caller must still NOT be able to reach that user through `users(filter:)` — the widening is scoped to rows of an entity they administer, not to the user directory.
- `@oneOf` principal cases: zero or two of `userId`/`groupId`/`everyone` set → rejected at query validation (engine-enforced, no resolver); `everyone: false` → `INVALID_VALUE`; a `groupId` naming a category outside the `user-groups` subtree → the unknown-id response, byte-identical to a `groupId` that does not exist at all.
- Write gate: a caller without `canAdmin(entity)` gets `PERMISSION_DENIED` and the stored list is unchanged; DynamicType write refused for a non-global-admin even when the caller has ADMIN on instances.
- Shape-D `null` vs `[]`: an EDIT-only caller who saves with `permissions: null` succeeds and the stored list is untouched; the same caller sending `permissions: []` gets `PERMISSION_DENIED` (it is a real change). An ADMIN caller sending `[]` empties the list.
- **§ 2 gate gap, if OQ 5 = in — write this one first and watch it fail:** a non-owner with `EDIT` on a reservation attempts `updateReservation(permissions: [<self as ADMIN>])`. Expected `PERMISSION_DENIED`; against today's `SecurityManager` it **succeeds**, which is the regression this PRD must close before shipping reservation permissions.
- `expectedLastChanged` mismatch → `CONCURRENT_MODIFICATION`, no partial write.
- Once OQ 6 is ruled in: `effectiveAccess` for a target outside `canAdminUser` / `canAdminGroup` returns uniform `PERMISSION_DENIED` with no existence signal; self-query without argument always succeeds.

**Parity check (§ 3), no new test tier:** the paths that copy permission lists — copy resource, copy event, `reservationsFromTemplate`, `addDefaultResourcePermissions`, type-default copy at create — must keep working without calling any new verb. Existing coverage re-run, not rewritten.
12. **`EventTemplate` / `Period` write surface.** Revised 2026-09-07 by a proposal from the concept session (*not ruled*): follow the § 1d merge with single inputs and `save*` verbs rather than `update*` —
    `EventTemplateInput { id, name: String!, fixedTimeAndDuration: Boolean!, permissions: [SimplePermissionInput!] }`
    `PeriodInput { id, name, start, end, categoryIds: [ID!]!, permissions: [SimplePermissionInput!] }`
    Three things to rule: (a) the field lists above; (b) whether **create** is thereby in scope for templates and periods — a nullable `id` on a `save*` verb makes create reachable by construction, so choosing this shape decides OQ 12(b) implicitly unless create is explicitly rejected; (c) `save*` vs `update*` naming, which is **coupled to OQ 13** — templates and periods are new verbs and can be born merged, but if reservations and allocatables keep `create*`/`update*` pairs the API ends up with both conventions side by side. *Lean: rule OQ 13 first, then make these follow whatever it says.*
    **Read-side consequence, independent of the above:** `Period` needs `categories: [Category!]!` for round-trip — the stored multi-select category attribute used by the holiday-period model. Without it a `save` that echoes `categoryIds` back cannot be built from a prior read, and a client would silently clear the field. Added to Plan P1.

13. **Verb merge as well as input merge (§ 1d)?** The OQ 2 / § 1d ruling merged the *inputs* following the `saveDynamicType` precedent. That precedent also has a single *verb* (`saveDynamicType`, create-vs-update discriminated by `input.id`) rather than a `create`/`update` pair. § 1d as written keeps the verbs separate and merges only the inputs — which leaves `input.id` redundant on the update path. Confirm that reading, or also collapse to `saveReservation` / `saveAllocatable`? *Lean: inputs only, as specced — collapsing the verbs additionally changes every SPA call site and the `ChangeOp` variant set, for a symmetry gain rather than a capability one.*
