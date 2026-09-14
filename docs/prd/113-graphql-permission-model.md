# PRD 113 — Permissions in the GraphQL model

**Status:** **implemented 2026-09-13, committed 2026-09-14** (master: squash 051e41fbb + security follow-ups 7a483562f/75dc7799d/094c71d09; PRD 116 rename shipped alongside) — P4a, R1 (+R1a/R1b), W1 (+W1a), W2, P6 all landed and reviewed (P6 review in progress); pending user review; **no open questions** (P-1 ruled "seed" → WP P1s § 5g, P-2 ruled "document" → residue for the OQ 11 sibling PRD, both 2026-09-13 ~23:30); WP O1 (§ 5f, owner change for group admins, user ruling P-4) implemented and reviewed (PASS) — also closed a pre-existing owner-change fall-through in `SecurityManager` (§ 5f); O1b (explicit allocation skip) and P1s pending impl. Design settled 2026-09-07, all OQs ruled 2026-09-13. Coordinator: rapla-62; drafted in the concept sessions. WP1 (merged inputs) committed 2e713ff33. Work packages P4a (§ 5a), R1 (§ 5b), W1 (§ 5c), W2 (§ 5d), P6 (§ 5e), O1 (§ 5f) — the sections stay as the record of what was built. *(Head of this file reconstructed 2026-09-13 ~21:30 after an accidental truncation; content = session-start version + the day's recorded edits.)*

**Decided:** the entity set and level matrix (§ 1a), the `DynamicType` two-list split (§ 1b), the `EventTemplate` entity (§ 1c), the merged create/update inputs (§ 1d — implemented under WP1, committed 2e713ff33, this PRD only consumes it), reservations in v1 (§ 4 OQ 5, user ruling 2026-09-13), the three typed permission inputs and `@oneOf` principals (§ 2a), write-through-save-inputs (§ 2b), full principal expansion for `canAdmin` callers (§ 4 OQ 3), and that read and write ship in one delivery (§ 4 OQ 1).

**Open:** nothing. P-1 ruled 2026-09-13 ~23:30 (seed Swing defaults when all three lists are null → WP P1s, § 5g); P-2 ruled 2026-09-13 ~23:30 (document, not enforce — residue "group/user visibility scoping" for the OQ 11 sibling PRD). Everything else is ruled 2026-09-13 (user, concept session): OQ 5 in, OQ 6 out, OQ 7 in (windows on REQUEST/ALLOCATE/ALLOCATE_CONFLICTS/EDIT), OQ 10 offer with note, OQ 11 (a), OQ 12 update-only, OQ 13 inputs only, OQ 14 `ownerId` dropped, `path` deletion go, P4a standalone (done), DENIED no handling, the 2026-09-06/07 concept-relayed rulings (§ 1a–c, OQ 1–4, 8, 9) confirmed item by item, P-4 owner change for group admins (§ 5f).

**Related:** [permissions.md](../architecture/permissions.md) (model + per-target level matrix), [ADR 0003](../decisions/0003-permissions-are-grant-only.md), [PRD 090](090-additive-permission-resolution.md) (max-wins, landed — see § GraphQL implications), [PRD 069](069-graphql-resource-access-read-api.md) (`accessibleBy*` / `AccessLevel` enum, landed), [PRD 063 OQ2](063-graphql-allocatables-write-api.md#oq2--permission-editing-on-allocatables) (deferred `setAllocatablePermissions`), [PRD 083](083-user-change-subscription.md) (`access_grant` read index), [PRD 035 §11](done/035-graphql-foundations.md) (typeKey-only), [PRD 061](061-graphql-dt-mutations-v2.md) / [063](063-graphql-allocatables-write-api.md) (mutation conventions), AGENTS.md §12.

## 1. What "permissions in the GraphQL model" means

Today GraphQL has **resolution results only**: the `accessibleBy*` filters (PRD 069, admin-scoped reverse lookup) and the implicit "you only see what you can read" gate. The **rows themselves** (`Permission`: principal, level, window) are neither readable nor writable — the Swing permission tab is the only editor. Both halves are needed and ship together (OQ 1); the order below is the order of the work, not of two releases:

- **Read exposure (Phase 1):** a `permissions` list on each permission-carrying API entity plus an `effectiveAccess` read. Without it the SPA cannot render a permission editor at all.
- **Write mutations (Phase 2):** replace the rows. Server gate = the one already enforced for every path: a changed permission list requires `canAdmin(entity, caller)` (`SecurityManager.checkPermissions`, audit fix 2026-08); global admin bypasses. Editing a `DynamicType` itself is global-admin only (`RaplaDefaultPermissionImpl.hasAccess` hardcodes it).

Categories/groups stay out (their "permissions" are the admin-group mechanism, PRD 069 handles group targets). Attribute-level permissions (`PermissionController.canRead/canWrite(Classification, Attribute, User)`) exist in core but stay out until a consumer asks (PRD 061 deferral). Owner is not a permission row; it changes only through `changeReservationOwner` / `changeAllocatableOwner`, gated per § 5f (WP O1: global admin, or group admin within scope).

### 1a. API entities that carry permissions

Internally `Template` and `Period` are allocatables of the rapla-internal types; **in the API they are separate entities** (`EventTemplate` and `Period` already exist as types — `Period` needs an `id` before it can be a permission target, OQ 8). Level sets are the Swing editors' `setPermissionLevels(...)` calls; the reduced set is keyed on the type being *internal*, not on person vs resource (verified 2026-09-06 in `AllocatableEditUI.mapFromObjects`):

| API entity | Storage | Levels (Swing = spec) | Time windows |
|---|---|---|---|
| Resource / Person (`Allocatable`) | Allocatable, non-internal type | READ_NO_ALLOCATION, READ, REQUEST, ALLOCATE, ALLOCATE_CONFLICTS, EDIT, ADMIN | on REQUEST, ALLOCATE, ALLOCATE_CONFLICTS, EDIT rows (OQ 7 ruling 2026-09-13; Swing offers the panel on the last three only) |
| Event (`Reservation`) | Reservation | READ, EDIT, ADMIN | none |
| `EventTemplate` | Allocatable `rapla:template` | READ, EDIT, ADMIN (`TemplateEdit`); READ = may see its reservations + instantiate, EDIT delegates `canModify` of template reservations; new template starts owner-only | none |
| `Period` | Allocatable `rapla:period` | READ, EDIT, ADMIN (internal-type branch) | none |
| `DynamicType` | DynamicType | split, see 1b | none |

**Time-based rules — the complete list.** The window fields (`start`/`end` absolute, `minAdvance`/`maxAdvance` days) live on every row but are evaluated only when *all* hold (`RaplaDefaultPermissionImpl.hasAccess`): the row has limits; the *requested* level is REQUEST or higher (`AccessLevel.includes` is a rank test, so ALLOCATE_CONFLICTS, EDIT and ADMIN requests check windows too — verified 2026-09-13); the caller passed `today` — only `canAllocate`, `canRequest`, `isRequestOnly`, `hasPermissionToAllocate` and `getInterval` do, `canRead`/`canReadInformation`/`canModify`/`canAdmin`/`canCreateConflicts` pass null; the row's own level is not ADMIN. `hasPermissionToAllocate` adds: for an existing appointment the window only has to cover the *changed* part. Swing shows the window panel only for rows whose level includes ALLOCATE and excludes ADMIN (ALLOCATE, ALLOCATE_CONFLICTS, EDIT — not REQUEST, although the server would honour a windowed REQUEST row). Consequence: only Resource/Person rows are ever windowed; a window on Event/Template/Period/DynamicType rows is dead data (Swing still shows the panel on an EDIT row there). **API rule (OQ 7, user ruling 2026-09-13):** window fields accepted on Resource/Person rows at **REQUEST, ALLOCATE, ALLOCATE_CONFLICTS and EDIT** — every level the server actually evaluates a window for; rejected (`INVALID_VALUE`) at READ_NO_ALLOCATION, READ and ADMIN, and on every Event/Template/Period/DynamicType row (`SimplePermissionInput` has no window fields). REQUEST is wider than the Swing panel and narrower than what the store accepts: the rule follows the *evaluation* path, not the Swing UI.

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
- Write merges both inputs into the one stored list; validation is per list (READ_TYPE/CREATE only in `typeAccess`, instance set only in `instanceDefaults`, no user principals in `typeAccess`). `instanceDefaults` rows follow the same window rule as the instances they are copied to (Resource/Person: windows on REQUEST, ALLOCATE, ALLOCATE_CONFLICTS, EDIT rows, absolute or relative — user rulings OQ 9 and OQ 7).
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
- **`updateEventTemplate(id: ID!, input: EventTemplateInput!, expectedLastChanged: LocalDateTime)` is IN** (*user ruling 2026-09-06, reported by the concept session*), and the same for `updatePeriod`. This revises the earlier "no template create/update/delete": **update** is in; **create/delete are out** (OQ 12 ruled 2026-09-13). Inputs: `EventTemplateInput { id, name: String!, fixedTimeAndDuration: Boolean!, permissions: [SimplePermissionInput!] }`, `PeriodInput { id, name: String!, start: LocalDateTime!, end: LocalDateTime!, categoryIds: [ID!]!, permissions: [SimplePermissionInput!] }`; `id` redundant-but-tolerated on update as in § 1d.
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
  # ownerId removed — OQ 14, user ruling 2026-09-13 (owner = caller; reassignment via the owner-change verb)
  permissions:    [ResourcePermissionInput!] # ← PRD 113 (§ 2a). NOT part of WP1.
}
```

`ReservationCheckInput.draft` is now typed `ReservationInput!` (landed with WP1) — noted because `checkReservation` is a read-only validation path: a `permissions` list arriving inside a draft must be **ignored**, never applied, since nothing is written there.

Replaces `CreateReservationInput` + `UpdateReservationInput` and `CreateAllocatableInput` + `UpdateAllocatableInput`. `EventTemplateInput` / `PeriodInput` (§ 1c) are single from birth. `ChangeOp`'s `createReservation` / `updateReservation` / `createAllocatable` / `updateAllocatable` variants all take the merged input; the variant, not the input, keeps saying which operation it is.

**Fields invalid for the operation are rejected, never silently ignored** — a silently-dropped `ownerId` looks like a successful owner change to the caller:

| Field | On create | On update |
|---|---|---|
| `id` | **required** — absent or blank → `REQUIRED`. Client-minted UUID; the server-generate fallback was removed by [PRD 056 § 9](056-graphql-events-write-api.md#9-client-supplied-ids--contract-rule--operator-side-integrity-guard-2026-07-06) (revised 2026-07-06), because an id-less create is structurally non-idempotent — retry-safety exists only via the client id. The GraphQL type stays nullable `ID` because *update* may omit it; requiredness on create is a resolver rule, not a type rule | absent, or equal to the mutation's `id:` argument. **Present and different → `INVALID_VALUE` at `input.id`** — reject rather than ignore, so a client sending the wrong id learns about it. Same rule on the batch path, error path prefixed `operations[i].updateReservation` |
| `ownerId` | **field removed** (OQ 14, user ruling 2026-09-13) — owner is always the caller; a different owner = create + `changeReservationOwner` / `changeAllocatableOwner` | same — the field no longer exists, so a client sending it fails schema validation |
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
"Exactly one populated (a type, not a union — `everyone` has no entity to point at). Dedicated leaf types — NOT `User`/`Group`: a permission row must not be a path to the full user record (review finding F1, coordinator decision 2026-09-13)."
type PermissionPrincipal { user: PermissionPrincipalUser  group: PermissionPrincipalGroup  everyone: Boolean! }
type PermissionPrincipalUser  { id: ID!  username: String!  name: String }
type PermissionPrincipalGroup { id: ID!  name: String! }

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
| Window only on `REQUEST` / `ALLOCATE` / `ALLOCATE_CONFLICTS` / `EDIT` (§ 1a) | **server** — level and window are sibling fields; types cannot couple them |
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

### 2c. ACL gate on `Reservation` — CLOSED 2026-09-13 (P4a, § 5a)

`SecurityManager.checkModifyPermissions` ends with the ACL block that demands `canAdmin(original, caller)` whenever a permission list changed. Until 2026-09-13 that block listed `Allocatable` and `Category` only; **since P4a it also lists `Reservation`**, so a non-owner with `EDIT` on a reservation can edit its data but not its permission list — the same rule as for resources. `EventTemplate` and `Period` are stored as allocatables and were covered all along; `DynamicType` is excluded deliberately (fully admin-gated upstream). Regression tests: `SecurityManagerPermissionChangeTest` (tier 2) and the § 6 write-gate case. Shape D (§ 2b) therefore rides on a gate that covers every container it writes to.

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
| `DENIED` only shown on rows that loaded with it | output enum keeps `DENIED`; input enum has none; no other handling — every stored `DENIED` row is migrated away by PRD 090 (user ruling 2026-09-13) |
| Absolute vs relative window radio (mutually exclusive) | both pairs set → `INVALID_VALUE`; entity setter semantics preserved |
| Tab shown inside the edit dialog; save goes through `checkPermissions` → `canAdmin` | same server gate, no client-side shortcut |
| Call paths that also carry permission lists: copy resource / copy event (permissions copied), templates → reservation (`reservationsFromTemplate` copies), new entity defaults (`addDefaultResourcePermissions`, type default rows) | unchanged — the new verbs only replace an existing container's list; parity check: every path above must keep working without calling the new verb |

Parity is server-side by construction: the one `PermissionController` / `SecurityManager` gate covers Swing, GraphQL, iCal export. What is *not* automatic is the level matrix — Swing enforces it in the UI only; GraphQL must enforce it in the resolver (permissions.md flags this).

## 4. Open questions for the user

1. ~~**Scope of v1**~~ — *user ruling 2026-09-06 (reported by the concept session):* **read and write ship in one delivery**, not read-only first. The Phase 1 / Phase 2 labels in § 1 stay as an ordering of the work inside that delivery, not as two releases.
2. ~~**Write shape**~~ — *user ruling 2026-09-06 (reported by the concept session):* **shape D** — permissions ride inside the existing save mutations as nullable inputs; no `setPermissions` verb family. A/B/C withdrawn (§ 2). Follow-on recorded there: the ACL gate did not cover `Reservation` at the time (closed 2026-09-13 by P4a, § 2c / § 5a), which made OQ 5 a security decision as well as a scope one.
3. ~~**Principal visibility on rows**~~ — *user ruling 2026-09-06 (reported by the concept session):* a caller with `canAdmin(entity)` may expand **every** row of that entity to user id + name, including users outside the caller's `canAdminUser` scope. Adding a *new* user row uses the SPA picker, which offers only users the caller can already see through the existing `users(filter:)` read scope — no new server surface. Server rule: `permissions` is non-null iff `canAdmin(entity)`, and user resolution inside a row is **not** re-filtered.
    **Consequences, recorded deliberately.** Whole-list replace round-trips completely and the hidden-row problem disappears — the `HiddenPrincipal` sketch is withdrawn along with option (a). In exchange this is a **conscious widening of read scope relative to Swing**: per [permissions.md § Server enforcement](../architecture/permissions.md#server-enforcement) the Swing client store holds only self + admins + administered-group members, so Swing *cannot* render those names today and shows an unresolvable reference. It is therefore not "Swing parity" but an intentional decision that entity-admin scope is sufficient to see who holds a grant on that entity. Recorded here as an accepted, documented deviation from the AGENTS.md § 12 default rather than an oversight, and pinned by a test (§ 6) so a later reader does not "fix" it back.
4. ~~**Disabled rows (`NO_PERMISSION`)**~~ — *closed 2026-09-06 as not applicable (user, reported by the concept session; verified in code 2026-09-07).* `NO_PERMISSION` is a **match result**, never a stored state: `PermissionContainer.Util.getUserEffect` returns `ALL_USER_PERMISSION` when both `user` and `group` are null, and returns `NO_PERMISSION` only as the "this row does not match this user" fallthrough. So both-null unambiguously means *everyone*; there is no disabled row to drop on read or reject on write.
    **Docs bug found while verifying — [permissions.md](../architecture/permissions.md) is wrong here** and should be corrected in a separate pass (not touched by this PRD): its `PermissionImpl` section lists *"Two special cases for `(user, group)` both null: `NO_PERMISSION` (-2) — disabled row; `ALL_USER_PERMISSION` (-1) — applies to everybody"*, which reads as two stored states for one storage shape. Only the second exists.
5. ~~**Reservations:**~~ — *user ruling 2026-09-13 (given directly to the concept session, to be confirmed by the coordinator): **reservations are in v1.** Consequence: P4a is a prerequisite of P4, and R1 (§ 5b) carries `Reservation.permissions` / `canAdmin`.* Original question: include from v1, or resources + templates + periods + dynamic types only (Reservation's own permission list is rarely edited outside sharing)? **Carried a prerequisite (closed by P4a):** the § 2c gate meant including reservations required first extending `SecurityManager.checkModifyPermissions` to `Reservation`. *Lean: include, and fix the gate — the gap is real whether or not this PRD ships, and leaving `updateReservation.permissions` ungated would be a new §12-class hole rather than an inherited one.*
6. ~~**`effectiveAccess`:**~~ *— user ruling 2026-09-13 (concept session, per recommendation; coordinator to confirm):* **out** — deferred to the SPA-editor PRD. Original: include in this delivery, or defer to the SPA-editor PRD? (OQ 1 removed the read-only-first option; the question is now in-or-out, not early-or-late.) Its admin-scoping is a second §12 surface (PRD 069 rules: target outside `canAdminUser`/`canAdminGroup` → uniform `PERMISSION_DENIED`; self-query always allowed).
7. ~~**Time window on windowed grants:**~~ *— user ruling 2026-09-13 (concept session; coordinator to confirm):* **in from v1, accepted on EDIT and every allocate/request level (REQUEST, ALLOCATE, ALLOCATE_CONFLICTS, EDIT).** Reason recorded: replace-the-whole-list semantics (§ 2b) would otherwise drop Swing-set windows on every read-modify-write. Original: input accepts `start/end/minAdvance/maxAdvance` from v1 (Resource/Person only, § 1a), or level + principal only first (PRD 069 v1 also ignored windows)?
8. ~~**`Period` id**~~ — *user ruling 2026-09-06:* add `id: ID!` to `Period`; name stays the display field. Additive only — no `period(id:)` query, no period CRUD in this PRD.
9. ~~**`instanceDefaults` windows**~~ — *user ruling 2026-09-06:* keep parity with Swing. Defaults accept the same levels and windows a resource row accepts, absolute windows included (use case: a one-year absolute default that the admin re-sets every year). No API-side restriction; a staleness hint is an SPA-editor concern.
10. ~~**`READ_NO_ALLOCATION` in the editor:**~~ *— user ruling 2026-09-13 (concept session, per recommendation; coordinator to confirm):* **offered, with a documented note.** Original: the level is offered by Swing on Resource/Person rows, but the SPA cannot render its effect — `permissions.md` § 5 (verified 2026-06-24) records that the GraphQL catalog gates on `canRead` (100) while `READ_NO_ALLOCATION` is 50, so a resource granted only that level is **omitted from the SPA entirely** (Swing shows it with bookings hidden). An editor offering the level therefore produces resources its own client cannot display. Offer it anyway with a documented note (Swing parity — the boundary difference is pre-existing, PRD 083 scoped the index to GraphQL for exactly this reason), or drop it from `ResourceAccessLevel` (§ 2a — the level set is now the input enum, so dropping it removes the level from the API entirely, not just from an advisory list)? *Lean: offer with a note — hiding the level does not close the catalog gap, it only makes the editor misrepresent the model.*
11. ~~**Group membership editing — in or out of PRD 113?**~~ *— user ruling 2026-09-13 (concept session, per recommendation; coordinator to confirm):* **(a) nothing in 113; sibling PRD for a membership verb.** `Group.parent`/`children` stay in P6. Original: `permissions.md` § Resolution (PRD 090 / ADR 0003) states that access never subtracts and *"to narrow one person below their group, change group membership"* — membership is therefore the **only** downward lever in the model. GraphQL has no user/group/category write verbs today (verified 2026-09-06: the `Mutation` type carries reservation, allocatable and dynamic-type verbs only), so a permission editor built on this PRD alone is **structurally grant-only**: an admin can hand out access in the SPA and must return to Swing to take any of it back. This does not *gate* implementation — read-only pickers suffice — but it decides what v1 actually is. Three scopes: (a) nothing, 113 adds only `Group.parent`/`children` for the picker and the editor stays grant-only; (b) membership only — one verb (`setUserGroups(userId, groupIds)` and/or the group-side inverse), gated by the existing group-admin mechanism (`canAdminUsers` / `CAN_ADMIN_PARENT`, the same scope that already decides which users the caller can see at all), everything else read-only; (c) full user + group administration (user CRUD, account fields for other users, creating/renaming/re-parenting groups = categories under `user-groups`) — a separate PRD in its own right. *Lean: (b).* Sub-question if (b): does membership editing appear on the group side (pick members for a group) as well as the user side? Swing edits it from the user record only.
12. ~~**`EventTemplate` / `Period` write surface.**~~ *— user ruling 2026-09-13 (concept session, per recommendation; coordinator to confirm):* **(a) field lists as proposed below, (b) update-only — no create/delete, (c) `updateEventTemplate` / `updatePeriod` (follows OQ 13).** Original: Revised 2026-09-07 by a proposal from the concept session (*not ruled*): follow the § 1d merge with single inputs and `save*` verbs rather than `update*` —
    `EventTemplateInput { id, name: String!, fixedTimeAndDuration: Boolean!, permissions: [SimplePermissionInput!] }`
    `PeriodInput { id, name, start, end, categoryIds: [ID!]!, permissions: [SimplePermissionInput!] }`
    Three things to rule: (a) the field lists above; (b) whether **create** is thereby in scope for templates and periods — a nullable `id` on a `save*` verb makes create reachable by construction, so choosing this shape decides OQ 12(b) implicitly unless create is explicitly rejected; (c) `save*` vs `update*` naming, which is **coupled to OQ 13** — templates and periods are new verbs and can be born merged, but if reservations and allocatables keep `create*`/`update*` pairs the API ends up with both conventions side by side. *Lean: rule OQ 13 first, then make these follow whatever it says.*
    **Read-side consequence, independent of the above:** `Period` needs `categories: [Category!]!` for round-trip — the stored multi-select category attribute used by the holiday-period model. Without it a `save` that echoes `categoryIds` back cannot be built from a prior read, and a client would silently clear the field. Added to Plan P1.
13. ~~**Verb merge as well as input merge (§ 1d)?**~~ *— user ruling 2026-09-13 (concept session, per recommendation; coordinator to confirm):* **inputs only; verbs stay `create*`/`update*`.** Original: The OQ 2 / § 1d ruling merged the *inputs* following the `saveDynamicType` precedent. That precedent also has a single *verb* (`saveDynamicType`, create-vs-update discriminated by `input.id`) rather than a `create`/`update` pair. § 1d as written keeps the verbs separate and merges only the inputs — which leaves `input.id` redundant on the update path. Confirm that reading, or also collapse to `saveReservation` / `saveAllocatable`? *Lean: inputs only, as specced — collapsing the verbs additionally changes every SPA call site and the `ChangeOp` variant set, for a symmetry gain rather than a capability one.*
14. ~~**Drop `ownerId` from `createAllocatable`, as PRD 063 says?**~~ — *user ruling 2026-09-13 (given directly to the concept session, to be confirmed by the coordinator): **yes, drop it — there is a change-owner verb.** `ownerId` leaves `AllocatableInput` and `ReservationInput`; § 1d row updated; the admin-only block in `AllocatableMutationController` goes with the field (lands in P4, together with the `permissions` input). **Consequence spotted 2026-09-13:** only `changeReservationOwner` exists in the schema; `changeAllocatableOwner` is still the PRD 063 deferral. Without it, an admin can no longer assign a resource to another owner via GraphQL at all (Swing still can). P4 therefore adds `changeAllocatableOwner(ids: [ID!]!, newOwnerId: ID!)`, same gate as the reservation verb (`isAdmin`) — one verb, not a gap.* Original question (coordinator finding 2026-09-13, PRD audit): [PRD 063](063-graphql-allocatables-write-api.md) (amended 2026-06-10) drops the admin-only `ownerId` create-override — owner is always the caller, reassignment goes through an explicit owner-change verb. § 1d and the implemented `AllocatableMutationController` block keep it. One API question, yes/no. *Lean: yes — drop it from `AllocatableInput` (and `ReservationInput`, same rule); § 1d's row becomes "absent; present → `INVALID_VALUE`" on both operations, and an owner other than the caller is set by create + `changeXOwner`. No = keep § 1d as written and amend PRD 063 back.*
P-1. ~~**GraphQL `saveDynamicType` create without default rows — seed like Swing, or document?**~~ — *user ruling 2026-09-13 ~23:30 (coordinator session): **seed when all three lists are null** → WP P1s, § 5g.* Original: *Review observation O1 (2026-09-13, W2 review), pre-existing since PRD 057.* Swing's `FacadeImpl.newDynamicType` seeds a new type: resource/person types get `everyone: READ_TYPE` + `everyone: ALLOCATE_CONFLICTS` + `registerer`-group rows; event types get `everyone: READ_TYPE`, `can-read-events-from-others`: READ, `can-create-events`: CREATE. The GraphQL create builds the type with an **empty** list, so a type created from the SPA is invisible to every non-admin until someone edits its lists. Options: (a) **seed** — the same rows on the GraphQL create when all three W2 lists are null (extract the two `addDefault*Permissions` bodies to a shared helper on `PermissionContainer.Util` or the operator, one implementation); (b) **document** — keep empty, the SPA type editor must always send `typeAccess`. *Lean: (a), seed — "wie Swing" across both create paths, and an admin who forgets the lists should not produce an invisible type. Sub-rule for (a): seeding happens only when every list input is null; any non-null list means the caller took ownership of that subset.* Not implemented — waits for the user.
P-2. ~~**Input-side principal scope — may a non-admin grant a row to ANY user id?**~~ — *user ruling 2026-09-13 ~23:30 (coordinator session): **document, do not enforce.** Reason: there are no visibility scopes for groups and users yet — a caller sees every group and may give every group and `everyone` a row, so a user row is a subset of what is already allowed; not always desirable, but a general visibility topic, to be solved once. Residue **"group/user visibility scoping"** goes to the OQ 11 sibling PRD (group membership / user-group administration). No code change in 113.* The original question and the review note behind it are kept in the gitignored `docs/security/113-reviews-and-findings-2026-09.md` (§ Security review, 2026-09-13 20:30). Reopened as a question by [PRD 117 E1](117-graphql-administration-entities.md#4-decisions-for-the-user-yesno).
P-4. ~~**Who may change an entity's owner?**~~ — *user ruling 2026-09-13 ~22:00 (coordinator session), not open:* **site/group admins may change owners within their user scope.** Rule, verification and work package: § 5f (WP O1, approved to impl the same evening). This is the "real group-delegation case" PRD 067 D10 reserved ("widen to admin-or-group-admin per `canAdminUsers` when a real case materializes"). Rejected variants: (i) only the *new* owner must be in scope — lets a group admin appropriate entities owned by users outside their scope; (ii) resource ADMIN as the owner-change right — mixes resource rights with user administration. Precisions from the concept session's code check, all adopted by the coordinator 2026-09-13: owner-only change, `canReadInformation` for allocatables, pure owner change skips the allocation check.

## 5. Plan

Read and write ship in one delivery (OQ 1). The order below is the work order; each step is independently compilable and testable.

| Step | Content | Depends on |
|---|---|---|
| — | § 1d input merge — **owned by WP1 / impl, not by this PRD**; PRD 113 depends on it and adds `permissions` to the merged inputs in P4 | prerequisite for P4 |
| ~~P1~~ ✔ R1 done 2026-09-13 | `Permission` / `PermissionPrincipal` output types; the three input types + three level enums (§ 2a); `canAdmin` + `permissions` fields on Allocatable, Reservation, EventTemplate, Period; `Period.id` (OQ 8) and `Period.categories` (OQ 12). **P1–P3 read side = work package R1, § 5b** | — |
| ~~P2~~ ✔ R1 done 2026-09-13 (path deleted) | `EventTemplate` promoted to an entity: `eventTemplate(id:)` / `eventTemplates` gated on `canRead`, the § 1c field set; `path` deleted with its residue (`TemplatePathBuilder` + tests, the SPA selection in `new-event-options.service.ts`) | P1; **deletion go given 2026-09-13** (AGENTS.md § 11) — R1 may include it |
| ~~P3~~ ✔ R1 done 2026-09-13 | `DynamicType.typeAccess` / `instanceDefaults` read projection over the one stored list (§ 1b) | P1 |
| ~~P4~~ ✔ W1 done 2026-09-13 | `permissions` on the merged `AllocatableInput` + `ReservationInput` (OQ 5 in); `ownerId` removed from both + the controller block (OQ 14); new `changeAllocatableOwner` verb (OQ 14 consequence); level-matrix validation and the § 1a window rule; null-vs-`[]` semantics. Concurrency is inherited — `expectedLastChanged` already exists on these verbs | P1–P3, OQ 3/4/5/7 |
| P4a | ~~extend `SecurityManager.checkModifyPermissions`'s ACL check to `Reservation`~~ **done 2026-09-13** (§ 5a) | — |
| ~~P5~~ ✔ W2 done 2026-09-13 | `saveDynamicType` gains nullable `typeAccess` / `instanceDefaults` (§ 1b merge + per-list validation); `updateEventTemplate` / `updatePeriod` per § 1c and OQ 12 | P3, P4 |
| ~~P6~~ ✔ done 2026-09-13 (+ periods gate, § 5e Part 2) | `Group.parent`/`children` for the picker. (`effectiveAccess` out — OQ 6; membership verb out — OQ 11 (a), sibling PRD) | P1 |

Out of scope, named so they are not assumed: the SPA permission editor itself (needs its own PRD — PRDs 090 and 096 both defer to one that does not exist), owner-change verbs for templates / periods (`changeAllocatableOwner` moved INTO P4 by the OQ 14 ruling — templates and periods are allocatables, so it covers them by id as well), attribute-level permissions (PRD 061 deferral), and user/group administration beyond OQ 11's ruling.

### 5a. WP P4a — reservation ACL gate (security fix, implementation-ready 2026-09-13)

**Implemented 2026-09-13** (impl session; `SecurityManager.java:250` now lists `Reservation`, regression tests in `SecurityManagerPermissionChangeTest`; permissions.md § 4 updated). Spec kept for the record.

Independent of every other 113 step; can ship alone as a bugfix. Tier 2, no schema change.

- **Affected method:** `rapla-server/src/main/java/org/rapla/server/internal/SecurityManager.java`, `checkModifyPermissions(User, Entity, boolean)`, the ACL block at the end (~line 250): `if ((entity instanceof Allocatable || entity instanceof Category) && original instanceof PermissionContainer)`. `Reservation` is missing from the `instanceof` list; the reservation branch above it (`checkPermissions(user, reservation, originalReservation, all)`) checks allocation rights only, never the permission list.
- **Failing test first** — tier 2, `rapla-server/src/test/java/org/rapla/server/internal/SecurityManagerPermissionChangeTest.java` (exists, same fixture: `monty` non-admin, `my-group`, admin `homer`). Add the reservation twin of `nonAdminCannotChangePermissionListOfResourceTheyOnlyEdit`:
  1. admin creates a reservation (`facade.newReservation(eventType.newClassification(), admin)`, one appointment, no allocatables so `checkPermissions` has nothing to reject), adds a row `group=my-group, level=EDIT`, stores it;
  2. sanity: `canModify(stored, monty) == true`, `canAdmin(stored, monty) == false`;
  3. `monty` edits it and adds a row `group=my-group, level=ADMIN` (or `user=<third user>, level=READ`);
  4. `assertThrows(RaplaSecurityException.class, () -> security.checkWritePermissions(monty, edit))` — was red-first before P4a (the call returned normally), green since;
  5. companion green test: same setup, monty changes only the classification name → `assertDoesNotThrow`;
  6. companion: owner (or admin) rewriting the list → `assertDoesNotThrow` — pins that the fix does not over-block.
- **Expected code change, one sentence:** add `|| entity instanceof Reservation` to that `instanceof` condition; nothing else (the `differs` + `canAdmin(original, user)` body already handles reservations, `Reservation` is a `PermissionContainer`).
- **Verification:** `mvn -pl rapla-server -am test -Dtest=SecurityManagerPermissionChangeTest` red → fix → green; AGENTS.md § 1 revert check (drop the clause, test red again); then `mvn -pl rapla-app -am test -Dtest='ReservationMutationControllerTest,MutationExistenceLeakTest' -Dsurefire.failIfNoSpecifiedTests=false` green (no existing GraphQL path rewrites reservation permissions, so nothing may change). Swing parity: the Swing permission tab on a reservation now gets `error.admin_not_allowed` for an EDIT-only user — that is the intended fix, not a regression (permissions.md § 4 Modify/delete gets one sentence).
- **Sibling audit (AGENTS.md § 1):** the only other `PermissionContainer` implementors are `DynamicType` (admin-gated upstream, excluded on purpose) and `Category` (already listed). No further sites.

### 5b. WP R1 — read phase (implementation-ready 2026-09-13)

P1–P3 read side: `permissions` + `canAdmin` on Allocatable, Reservation (OQ 5 ruled in), EventTemplate, Period; DynamicType `typeAccess` / `instanceDefaults` projection. **Excluded:** `effectiveAccess` (OQ 6), every input type and write path (P4/P5), `Group.parent`/`children` (P6), the `path` deletion (P2 — waits for the § 11 go; R1 *adds* fields to `EventTemplate` and leaves `path` in place). Additive schema only, no SPA change.

**Schema (additive):**

```graphql
type Permission {
  principal: PermissionPrincipal!
  level:     AccessLevel!                      # full enum incl. DENIED on read
  start: LocalDateTime  end: LocalDateTime
  minAdvance: Int       maxAdvance: Int
}
"Exactly one populated; a stored row with neither user nor group reads as everyone: true. Leaf types, not User/Group (F1)."
type PermissionPrincipal { user: PermissionPrincipalUser  group: PermissionPrincipalGroup  everyone: Boolean! }
type PermissionPrincipalUser  { id: ID!  username: String!  name: String }
type PermissionPrincipalGroup { id: ID!  name: String! }

extend type Allocatable   { canAdmin: Boolean!  permissions: [Permission!] }
extend type Reservation   { canAdmin: Boolean!  permissions: [Permission!] }
extend type EventTemplate { canAdmin: Boolean!  permissions: [Permission!]
                            owner: User  createdAt: DateTime  lastModifiedAt: DateTime
                            fixedTimeAndDuration: Boolean!  canModify: Boolean! }
extend type Period        { id: ID!  categories: [Category!]!
                            canAdmin: Boolean!  permissions: [Permission!] }
extend type DynamicType   { typeAccess: [Permission!]  instanceDefaults: [Permission!] }
extend type Query         { eventTemplate(id: ID!): EventTemplate   eventTemplates: [EventTemplate!]! }
```

`permissions` (and both DynamicType lists) are **nullable: `null` iff `!canAdmin(entity, caller)`** — never `[]` for a non-admin (§ 6). DynamicType: `canAdmin` = global admin (`user.isAdmin()`); `typeAccess` = rows with level READ_TYPE | CREATE, `instanceDefaults` = every other row, same order as stored.

**Resolver locations** (follow the existing wiring; no new controller class):

| Field | Where | Pattern to copy |
|---|---|---|
| `Allocatable.canAdmin` / `.permissions`, `Reservation.canAdmin` / `.permissions` | `StructuralTypeFetchers.wire(...)` — new `LightDataFetcher`s next to `ALLOCATABLE_CAN_MODIFY` / `RESERVATION_CAN_MODIFY` | those two fetchers (caller from the request context, `operator.getPermissionController()`) |
| `DynamicType.typeAccess` / `.instanceDefaults` | same file, the `b.type("DynamicType", …)` block | `DYNAMIC_TYPE_NAME` |
| `Permission.*`, `PermissionPrincipal.*` | one new `b.type("Permission", …)` / `b.type("PermissionPrincipal", …)` in the same file; `user` / `group` projected into the leaf types via `operator.tryResolve` (id, username, name only — no `User`/`Group` object on the wire; a group outside `user-groups` cannot occur in a stored row) | `allocatableOwner(operator)` |
| `EventTemplate.*` new fields, `eventTemplate(id:)`, `eventTemplates` | `ReservationGraphQLController` — the `EventTemplate` record (line ~135) grows the new fields; `newEventOptions` keeps its gate; the two new `@QueryMapping`s reuse its template lookup + `canRead(template, caller)` filter | `newEventOptions` |
| `Period.id` / `.categories` / `.canAdmin` / `.permissions` | `HelloGraphQLController.periods()` + `PeriodDto` record (line ~354) — becomes a record carrying the allocatable id and the underlying `Allocatable` for the field fetchers | `periods()` |

One shared helper for the projection, `Permission` row → output map (principal, level, window), used by all five containers — it exists nowhere yet (ladder step 2 checked: `AccessTargetFilter` filters, it does not project rows).

**§ 12 rules that R1 must satisfy:**

1. Containers are reached only through resolvers that already filter by `canRead` (`allocatables`, `reservations`, `newEventOptions.templates`); the two new template queries apply the same `canRead(template, caller)` filter and answer unknown id and unreadable id identically (`null` / omitted). **Exception, decided 2026-09-13 (coordinator, R1):** `periods` is *not* read-filtered (`HelloGraphQLController.periods()` → `operator.getPeriodModelFor(null)`, pre-existing TODO) and stays so in R1; only its `permissions` field is `canAdmin`-gated. Swing-parity check: Swing *does* filter — the client cache holds only allocatables with `canReadInformation` (`LocalCache.getVisibleEntities`, line ~353) and periods are `rapla:period` allocatables; but the period type ships an `everyone: READ` default row (`LocalAbstractCachableOperator` period-type bootstrap) that `copyPermissions` puts on every period, so in practice every user sees every period unless an admin removes that row. Consequence: a read gate is needed for parity the moment PRD 113 lets an admin edit period rows — **added to P6 (§ 5e)**, not R1.
2. `permissions` non-null **only** for `canAdmin(entity, caller)`; global admin passes everywhere. Field-level, so a list query with mixed admin / non-admin rows returns per-row `null`s.
3. Principal expansion for a `canAdmin` caller returns `id` + `name` of every row's user, **including users outside the caller's `canAdminUser` scope** — OQ 3 ruling, a documented § 12 widening, pinned by test. It does not open `users(filter:)` or `search` (unchanged gates in `HelloGraphQLController` / `SearchGraphQLController`).
4. A principal is a **dedicated leaf** — `PermissionPrincipalUser { id, username, name }` / `PermissionPrincipalGroup { id, name }` — never the `User` / `Group` types, so `groups`, `isAdmin`, `email`, `authSource` and every future `User` field are unreachable from a permission row (coordinator decision 2026-09-13 17:50, review finding F1; supersedes the earlier "same `User` type as `Allocatable.owner`" wording). PRD 076 Phase 5 `@requiresAccessDetails` therefore has nothing to tag here.
5. A non-admin caller always gets `permissions: null`, whether the stored list is empty or not — no count, error text or timing may reveal which. `[]` is reachable only by an admin caller (an entity with no rows).

**Tier-3 MockMvc leak tests** — `rapla-app/src/test/java/org/rapla/server/spring/graphql/PermissionReadLeakGraphQLTest.java`, pattern `ResourceAccessQueryGraphQLTest` (copied `testdefault.xml`, `HttpGraphQlTester`, `@WithMockUser`); fixture: `homer` admin, `monty` non-admin; seed in `@BeforeEach` via the operator: resource A (monty EDIT via my-group), resource B (monty ADMIN via a user row), resource C (monty no row, unreadable), one reservation each in the same three shapes, one template readable by monty and one not, the two default periods.

1. `nonAdminPermissionsNullWhereNotAdmin` — monty queries `allocatables { id canAdmin permissions { level } }`: A → `canAdmin:false, permissions:null`; B → `canAdmin:true, permissions:[…]`; C absent. Same for `reservations`.
2. `nonAdminNeverGetsEmptyListInsteadOfNull` — A's list has rows; assert the JSON literal is `null`, not `[]`, not an error entry.
3. `mixedIdsByteIdentical` (§ 12 recipe) — `allocatables(filter:{idIn:[A,B,C,<unknown>]})` as monty: response body byte-identical to `idIn:[A,B]`; `idIn:[C,<unknown>]` byte-identical to `idIn:[<unknown>]` (`[]`). Same for `eventTemplate(id:)` with readable / unreadable / unknown → identical `null` for the latter two; `eventTemplates` omits the unreadable one.
4. `adminSeesEveryPrincipalNameOnAdministeredEntity` (OQ 3 pin) — resource B carries a row for a user monty cannot administer; monty reads `permissions { principal { user { id username } } }` and gets the name. Boundary: `users(filter:…)` / `search` for that user as monty still returns nothing.
5. `dynamicTypeListsAdminOnly` — monty: `dynamicTypes { typeAccess instanceDefaults }` both `null`; homer: the fixture room type's stored list split correctly (READ_TYPE/CREATE rows in `typeAccess`, the rest in `instanceDefaults`, an ADMIN row in `instanceDefaults`).
6. `everyoneRowReadsAsEveryoneTrue` — a stored row with neither user nor group → `principal { everyone: true, user: null, group: null }`.
7. `periodIdAndCategoriesRoundTrip` — `periods { id name categories { id } }` non-null ids, categories match the stored attribute.

Tier 2 (FacadeTestSupport): the projection helper — split rule for DynamicType, principal mapping (user / group / both-null), window fields passthrough. `PermissionMatrixTest` stays untouched.

**Verification:** `mvn -pl rapla-app -am test -Dtest='PermissionReadLeakGraphQLTest,ResourceAccessQueryGraphQLTest,MutationExistenceLeakTest' -Dsurefire.failIfNoSpecifiedTests=false` green; schema snapshot / GraphiQL introspection shows the new fields; SPA untouched (`ng` build not required).

### 5c. WP W1 — P4: permission inputs on Allocatable + Reservation, `ownerId` removal, `changeAllocatableOwner` (implementation-ready 2026-09-13)

Prerequisites: P4a merged (reservation ACL gate), R1 merged (output types + projection helper). Independent of PRD 067: the controllers keep their current creation paths and only gain the permission mapping.

**Schema (breaking where noted — early beta):**

```graphql
input PrincipalInput @oneOf { userId: ID  groupId: ID  everyone: Boolean }      # @oneOf already in use (CandidateInput), graphql-java 25
enum ResourceAccessLevel { READ_NO_ALLOCATION READ REQUEST ALLOCATE ALLOCATE_CONFLICTS EDIT ADMIN }
enum SimpleAccessLevel   { READ EDIT ADMIN }
input ResourcePermissionInput { principal: PrincipalInput!  level: ResourceAccessLevel!
                                start: LocalDateTime  end: LocalDateTime  minAdvance: Int  maxAdvance: Int }
input SimplePermissionInput   { principal: PrincipalInput!  level: SimpleAccessLevel! }

extend input AllocatableInput { permissions: [ResourcePermissionInput!] }   # ownerId REMOVED (OQ 14, breaking)
extend input ReservationInput { permissions: [SimplePermissionInput!] }     # ownerId REMOVED
extend type Mutation {
  "Owner reassignment for resources, templates and periods (all stored as allocatables). Gate: isAdmin — Swing SetOwnerMenuFactory parity."
  changeAllocatableOwner(ids: [ID!]!, newOwnerId: ID!): BulkResult!
}
```

**One mapper, `PermissionInputMapper`** (`rapla-app/…/graphql/`, package-private, next to `ClassificationInputMapper` — same shape, pure, no Spring). Ladder: `PermissionContainer.Util.replace(container, rows)` is the existing write primitive, `container.newPermission()` mints rows; nothing else to reuse. Shared by W1 and W2.

```java
enum Kind { RESOURCE, SIMPLE, TYPE_ACCESS }
static List<Permission> toRows(PermissionContainer container, List<Map<String,Object>> input, Kind kind, String path, EntityResolver resolver)
static void apply(PermissionContainer container, List<Map<String,Object>> input, Kind kind, String path, EntityResolver resolver)  // toRows + Util.replace
// EntityResolver (rapla-core) — StorageOperator implements it; controllers pass the operator, the tier-1 test an in-memory resolver
```

Server rules (schema does the rest — § 2a table). Error codes follow the PRD 056 taxonomy (`ReservationMutationException(code, path, message)`):

| # | Rule | Code @ path |
|---|---|---|
| 1 | `principal.everyone == false` | `INVALID_VALUE` @ `<path>[i].principal.everyone` |
| 2 | `userId` unresolvable | `REFERENCE_NOT_FOUND` @ `<path>[i].principal.userId` |
| 3 | `groupId` unresolvable **or a category outside the `user-groups` subtree** — same response (§ 12, no existence signal) | `REFERENCE_NOT_FOUND` @ `<path>[i].principal.groupId` |
| 4 | RESOURCE only: any window field on a level other than REQUEST / ALLOCATE / ALLOCATE_CONFLICTS / EDIT (OQ 7) | `INVALID_VALUE` @ `<path>[i].level` |
| 5 | RESOURCE only: absolute (`start`/`end`) and relative (`minAdvance`/`maxAdvance`) both set | `INVALID_VALUE` @ `<path>[i].start` |
| 6 | RESOURCE only: `start > end`, `minAdvance > maxAdvance`, negative advance | `INVALID_VALUE` @ the offending field |
| 7 | `userId` → `setUser`, `groupId` → `setGroup`, `everyone: true` → neither (`PermissionImpl` exclusive setters); windows via `setStart/setEnd` or `setMinAdvance/setMaxAdvance` | — |
| 8 | Duplicate rows are stored as sent (Swing stores duplicates too) | — |

**Gate — one place, unchanged:** `WriteGate.check` → `SecurityManager.checkModifyPermissions` → `differs(old, new) && !canAdmin(original, caller)` → `PERMISSION_DENIED` @ `input`. Global admin passes `canAdmin`. The controllers add **no** second list check; they decide only *whether* to touch the list: `permissions == null` → mapper not called (create: type defaults stay; update: stored rows stay). `[]` → mapper called with an empty list → `replace` empties it → the gate decides.

**Per verb (all in the two existing controllers):**

| Verb | Change |
|---|---|
| `createAllocatable` | delete the `ownerId` block (`AllocatableMutationController` ~101–115) and its javadoc line; after `copyPermissions(dt, a)`: `if (permissions != null) PermissionInputMapper.apply(a, permissions, RESOURCE, "input.permissions", operator)`. |
| `updateAllocatable` | delete the `ownerId` rejection (~167); after clone + classification update: same `apply` on the draft. `expectedLastChanged` unchanged. |
| `createReservation` / `updateReservation` / the `createReservation` + `updateReservation` variants in `applyChanges` | same with `SIMPLE`, path `input.permissions` / `operations[i].updateReservation.permissions`; `ownerId` handling deleted; `checkReservation` ignores `permissions` on the draft (§ 1d). |
| `changeReservationOwner` | gate swap: `requireCanModify` → `caller.isAdmin()` else `PERMISSION_DENIED` @ `caller` (see finding below); rest unchanged. |
| `changeAllocatableOwner` | new in `AllocatableMutationController`, copy of `changeReservationOwner` (`ReservationMutationController` ~277–308) with the same admin gate; `newOwnerId` unresolvable → `REFERENCE_NOT_FOUND` @ `newOwnerId`; each id → allocatable (any type incl. `rapla:template` / `rapla:period`) else `REFERENCE_NOT_FOUND` @ `ids[i]`; edit → `setOwner` → one `UpdateEvent` → `dispatchChecked`; `BulkResult`. |

**Sibling finding (2026-09-13, verified) → decided:** `changeReservationOwner` gated on `requireCanModify` — any EDIT user could hand a reservation to anyone; Swing's `SetOwnerMenuFactory:70` is `isAdmin`-only. *Coordinator decision 2026-09-13 17:05 (autonomous, logged in 113-status.md): tightened INSIDE W1.* **Both owner verbs share one gate** — extract a private check used by `changeReservationOwner` (replacing `requireCanModify(r, caller)` in `ReservationMutationController` ~300) and by `changeAllocatableOwner`. *Verified 22:30 (coordinator confirmed):* the 17:05 reasoning was right — `checkModifyPermissions` refused an owner change at ~121 but then re-permitted it via the generic `canModify(original, user)` fall-through (~137–145, the `PermissionContainer` branch), i.e. the wire was wider than Swing's `isAdmin` menu. **Pre-existing, CLOSED by O1 (§ 5f, implemented + reviewed 2026-09-13).** W1 shipped `isAdmin` on the verbs; O1 replaces it with the scoped rule and closes the wire path. PRD 056 § `changeReservationOwner` follows O1.

**Tests.** `PermissionInputMapperTest` — **tier 1** (coordinator ruling 2026-09-13; `FacadeTestSupport` is not reachable from rapla-app): plain JUnit in the rapla-app test tree, the mapper's `EntityResolver` parameter is an in-memory resolver over real `UserImpl` / `CategoryImpl` instances (no Spring, no store, no mocks of rapla types — AGENTS.md § 13 allows hand-rolled doubles of an *interface* fed with real entities); a `user-groups` root with one child group and one category outside it; rules 1–8 one case each; a valid list round-trips through the R1 projection helper as identity. Tier 3, `PermissionWriteGraphQLTest` (pattern `AllocatableMutationControllerTest`; fixture as R1: homer admin, monty non-admin; resource A monty EDIT via my-group, resource B monty ADMIN via user row, reservations in the same shapes):
1. `editOnlyNullLeavesListUntouched` — monty `updateAllocatable(A, permissions: null)` succeeds, stored rows unchanged.
2. `editOnlyEmptyListDenied` — `permissions: []` → `PERMISSION_DENIED`, rows unchanged.
3. `editOnlySelfGrantAdminDenied` — `[<my-group ADMIN>]` → `PERMISSION_DENIED`; the same on reservation A′ (P4a end-to-end through GraphQL).
4. `entityAdminReplacesWholeList` — monty on B sends two rows → stored list is exactly those two; `permissions` read back equals the input.
5. `globalAdminBypass` — homer replaces the list on A (no row for homer) → success.
6. `createWithPermissionsOverridesTypeDefaults` / `createWithNullKeepsTypeDefaults`.
7. `windowOnReadRowRejected`, `windowOnRequestRowAccepted`, `absoluteAndRelativeRejected`, `startAfterEndRejected`.
8. `everyoneFalseRejected`; `groupOutsideUserGroupsAnswersLikeUnknown` — error byte-identical to a random id.
9. `unrepresentableLevelFailsValidation` — `READ_TYPE` in `ResourcePermissionInput`, `REQUEST` in `SimplePermissionInput`: validation error, no resolver call.
10. `ownerIdNoLongerAField` — `createAllocatable(input: { ownerId })` fails validation; same for `ReservationInput`. Delete the existing admin-override test in `AllocatableMutationControllerTest`.
11. `changeAllocatableOwner` — monty → `PERMISSION_DENIED`, owners unchanged; homer → owner changed on a resource, a template and a period in one call; unknown id → `REFERENCE_NOT_FOUND` @ `ids[i]`; unknown owner → `REFERENCE_NOT_FOUND` @ `newOwnerId`.
11a. `changeReservationOwnerAdminOnly` — monty (EDIT on reservation A′ via my-group, `canModify` true) → `PERMISSION_DENIED`, owner unchanged; homer → owner changed. **Red-first:** against today's `requireCanModify` gate the monty half succeeds. Adjust any existing `ReservationMutationControllerTest` case that changes owner as a non-admin.
12. `applyChangesCarriesPermissions` — batch `updateReservation` with a list, error path prefixed `operations[0].updateReservation.permissions[0]…`.
13. `concurrentModification` — stale `expectedLastChanged` with a permission list → `CONCURRENT_MODIFICATION`, nothing written.
14. `permissionIndexFresh` — after homer grants monty READ on C, `allocatables` as monty contains C on the next query (§ 6 verification).

**SPA impact:** none — `grep -rn ownerId rapla-angular/src` hits only a spec fixture and a comment, no mutation sends it.

**Verification:** `mvn -pl rapla-app -am test -Dtest='PermissionInputMapperTest,PermissionWriteGraphQLTest,AllocatableMutationControllerTest,ReservationMutationControllerTest,MutationExistenceLeakTest' -Dsurefire.failIfNoSpecifiedTests=false` green; § 3 parity paths (copy resource / copy event / `reservationsFromTemplate` / type-default copy) via the existing suites, re-run not rewritten.

### 5d. WP W2 — P5: `saveDynamicType` lists, `updateEventTemplate`, `updatePeriod` (implementation-ready 2026-09-13)

Prerequisite: W1 (the mapper + `SimplePermissionInput`). Gates: DynamicType = global admin only (existing `isAdmin` in `DynamicTypeMutationController` ~288); template/period = `canModify(allocatable, caller)` for the data fields, and the W1 gate (`canAdmin` via `SecurityManager`, global-admin bypass) for the list.

**Schema:**

```graphql
input TypeAccessPrincipalInput @oneOf { groupId: ID  everyone: Boolean }
enum TypeAccessLevel { READ_TYPE CREATE }
input TypeAccessInput { principal: TypeAccessPrincipalInput!  level: TypeAccessLevel! }
extend input DynamicTypeInput { typeAccess:               [TypeAccessInput!]
                                resourceInstanceDefaults: [ResourcePermissionInput!]
                                eventInstanceDefaults:    [SimplePermissionInput!] }
input EventTemplateInput { id: ID  name: String!  fixedTimeAndDuration: Boolean!  permissions: [SimplePermissionInput!] }
input PeriodInput        { id: ID  name: String!  start: LocalDateTime!  end: LocalDateTime!
                           categoryIds: [ID!]!  permissions: [SimplePermissionInput!] }
extend type Mutation {
  updateEventTemplate(id: ID!, input: EventTemplateInput!, expectedLastChanged: LocalDateTime): EventTemplate!
  updatePeriod(id: ID!, input: PeriodInput!, expectedLastChanged: LocalDateTime): Period!
}
```

**`saveDynamicType`** (`DynamicTypeMutationController.saveDynamicType`, after `applyAnnotations`):
1. Reject the instance list that does not match `classificationType`: `resourceInstanceDefaults` on an event type → `INVALID_VALUE` @ `input.resourceInstanceDefaults`; `eventInstanceDefaults` on a resource/person type → `INVALID_VALUE` @ `input.eventInstanceDefaults`. (`ADMIN` in `typeAccess` and user principals there are unrepresentable — schema.)
2. Split the stored list once: `typeRows` = level READ_TYPE | CREATE, `instanceRows` = the rest (same rule as the R1 projection — reuse that helper).
3. `typeAccess != null` → `typeRows = toRows(draft, typeAccess, TYPE_ACCESS, "input.typeAccess")`; the matching instance list non-null → `instanceRows = toRows(draft, list, RESOURCE|SIMPLE, path)`; a null list keeps its stored subset (**partial replace** — the two API lists are independent, § 1b).
4. `Util.replace(draft, typeRows ++ instanceRows)` — one stored list, order: type rows first.
5. On **create** (`input.id == null`): ~~today the facade defaults are added~~ — **wrong on the GraphQL path** (review O1, 2026-09-13): `saveDynamicType` builds `new DynamicTypeImpl(now, now)` with **no** permission rows (pre-existing, PRD 057); only Swing's `FacadeImpl.newDynamicType` seeds defaults. So a GraphQL-created type starts with an empty list (nobody but global admins sees it) unless the caller sends `typeAccess` / instance defaults. Ruled 2026-09-13 (P-1 → WP P1s, § 5g), **create and update differ:**
   - **Create** (`input.id == null`): if **all three** lists are null → the Swing default rows are seeded (§ 5g). If **any** list is non-null → the caller owns the **whole** stored list: no seeding at all, the stored list is exactly the union of the lists sent, a list left null contributes nothing (review P1s N1 — there is no "seed then partially replace").
   - **Update** (`input.id` set): partial replace per API list as in points 2–4 — a null list keeps its stored subset, a non-null list replaces exactly that subset.

**`updateEventTemplate` / `updatePeriod`** — new `@MutationMapping`s in `AllocatableMutationController`: resolve `id` → allocatable whose type key is `rapla:template` / `rapla:period`, else `REFERENCE_NOT_FOUND` @ `id` (unreadable answers identically — § 12); `canModify` else `PERMISSION_DENIED` @ `id`; `input.id` absent or equal to `id` else `INVALID_VALUE` @ `input.id` (§ 1d rule); clone; set classification attributes (template: `name`, `fixedTimeAndDuration`; period: `name`, `start`, `end`, `categories` — each id resolved, unknown → `REFERENCE_NOT_FOUND` @ `input.categoryIds[i]`; `start >= end` → `INVALID_VALUE` @ `input.end`); `apply(SIMPLE, "input.permissions")` when non-null; `expectedLastChanged` → `CONCURRENT_MODIFICATION`; `dispatchChecked`; return the R1 projection (`EventTemplate` record / `PeriodDto`).

**Tests.** Tier 2, `PermissionInputMapperTest` gains: `typeAccess` rows minted with group/everyone only; DynamicType merge cases — null `typeAccess` + `resourceInstanceDefaults: []` keeps READ_TYPE/CREATE rows and empties the rest, and vice versa; a stored ADMIN row lands in `instanceRows`. Tier 3, `DynamicTypePermissionWriteGraphQLTest` (pattern `DynamicTypeMutationControllerTest`) + additions to `PermissionWriteGraphQLTest`:
1. `nonAdminSaveDynamicTypeDenied` — monty with only `typeAccess` → `PERMISSION_DENIED` (existing gate), even holding ADMIN rows on instances.
2. `wrongInstanceListRejected` — `resourceInstanceDefaults` on the event type / `eventInstanceDefaults` on the room type → `INVALID_VALUE` at the named path.
3. `partialReplaceKeepsOtherSubset` — homer sends only `typeAccess` → instance rows unchanged; only `resourceInstanceDefaults` → type rows unchanged; both → both replaced; read back via `typeAccess` / `instanceDefaults` equals input.
4. `adminInTypeAccessFailsValidation`, `userPrincipalInTypeAccessFailsValidation` — validation errors, no resolver call.
5. `instanceDefaultsWindowRules` — same window matrix as W1 item 7 on `resourceInstanceDefaults` (OQ 9).
6. `newInstanceInheritsEditedDefaults` — after changing the room type's `resourceInstanceDefaults`, `createAllocatable` without `permissions` carries exactly those rows.
7. `updateEventTemplate` — homer changes name + permissions, reads back; monty with READ on the template → `PERMISSION_DENIED`; unknown and unreadable id → identical `REFERENCE_NOT_FOUND`; `input.id` ≠ `id` → `INVALID_VALUE`.
8. `updatePeriod` — round-trip name/start/end/categories/permissions; unknown category → `REFERENCE_NOT_FOUND` @ `input.categoryIds[i]`; `start >= end` → `INVALID_VALUE`; stale `expectedLastChanged` → `CONCURRENT_MODIFICATION`.
9. `schemaRebuildAfterPermissionOnlySave` — a `saveDynamicType` touching only lists still returns and the generated schema is unchanged (no attribute change → no rebuild needed; pins that the rebuilder is not triggered spuriously if PRD 061 § 4 lands).

**Verification:** `mvn -pl rapla-app -am test -Dtest='PermissionInputMapperTest,PermissionWriteGraphQLTest,DynamicTypePermissionWriteGraphQLTest,DynamicTypeMutationControllerTest' -Dsurefire.failIfNoSpecifiedTests=false` green.

### 5e. WP P6 — `Group.parent` / `Group.children` for the picker + `periods` read gate (implementation-ready 2026-09-13)

Read-only, additive. `effectiveAccess` (OQ 6) and membership editing (OQ 11) stay out.

**Part 2 — `periods`: all readable periods, category-independent + read gate** (R1 finding § 5b rule 1; coordinator decision 2026-09-13 18:35). Two defects in `HelloGraphQLController.periods()` today: (a) no read gate; (b) it goes through `operator.getPeriodModelFor(null)`, and `PeriodModelImpl.machtesKey` with an empty key set matches **only periods with zero categories** — a period given a category via `updatePeriod` (W2) silently disappears from the list. Fix: `periods()` enumerates the `rapla:period` allocatables directly (the same source `PeriodModelImpl.update` reads, minus the category match), keeps `canReadInformation(alloc, caller)` rows (global admin bypass — the Swing client-cache rule), sorts by start, and projects to `PeriodDto` with the allocatable attached; anonymous → `[]` (as before). **Implemented 2026-09-13:** category filter removed, no argument. `getPeriodModelFor("holiday")` in `StandardCheckers` is a different, keyed model and unaffected.

*Consumer check (2026-09-13):* no consumer of the GraphQL `periods` query depends on the category filter — the SPA does not query `periods` at all (`grep -rn periods rapla-angular/src` → no non-spec hit), no server-side Java/Mustache/HTML page calls it, and the Swing HTML pages use `PeriodModel` directly, not GraphQL. **Therefore no `categoryId` argument** — YAGNI; add one when a consumer asks.

Tests, `PeriodReadGateGraphQLTest` (pattern `ResourceAccessQueryGraphQLTest`): `categorisedPeriodStaysListed` — after `updatePeriod(categoryIds: [<holiday>])` the period is still in `periods` (was red-first against the category-filtered implementation); `defaultPeriodsVisibleToNonAdmin` (everyone READ row); `unreadablePeriodAbsent` — a period whose READ row an admin replaced by a `my-other-group` row is absent for monty, present for homer, response byte-identical to the fixture without that period; `sortedByStart`.

```graphql
extend type Group { parent: Group  children: [Group!]! }   # parent null at the user-groups root's direct children
```

**Resolver:** `GroupGraphQLController` — two `@SchemaMapping(typeName = "Group")` methods over `GroupDto`, resolving via `operator.tryResolve(id, Category.class)`; `parent` returns null when the parent is the `user-groups` root itself (the root is not a group). Cost note in the class javadoc applies: per-row `@SchemaMapping` is acceptable here (picker-sized lists).

**Leak rule:** today `groups()` / `group(id)` show every permission group to any authenticated caller (class javadoc: categories are global metadata, no per-user read permission). `parent` / `children` inherit exactly that gate — **not wider**: they never leave the `user-groups` subtree (a `Category` outside it is not a `Group`, so `parent` of a top-level group is null and `children` never surfaces non-group categories), and anonymous callers get exactly what `groups()` gives — `UNAUTHENTICATED` (`UnauthenticatedException.require`). No member expansion: `children` are groups, not users — user membership stays behind `User.groups` and its `canAdminUser` gate.

**Tests.** Tier 3, `GroupHierarchyGraphQLTest` (pattern `UsersInGroupFilterGraphQLTest`): `parentOfTopLevelGroupIsNull`; `childrenRoundTrip` on a nested fixture group; `anonymousAnswersLikeGroups` (`groups()` throws `UNAUTHENTICATED`; `Group.parent`/`children` behave identically — corrected 2026-09-13 at impl); `nonGroupCategoryNeverAppears` — a category outside `user-groups` with the same id shape queried via `group(id:)` → null, and no `children` entry anywhere resolves to it; `noUserExpansionThroughChildren` — the selection `children { id key name }` is the whole surface (schema assertion: `Group` has no `members` field).

**Verification:** `mvn -pl rapla-app -am test -Dtest='GroupHierarchyGraphQLTest,UsersInGroupFilterGraphQLTest' -Dsurefire.failIfNoSpecifiedTests=false` green.

### 5f. WP O1 — owner change for group admins within scope (user ruling P-4, 2026-09-13; approved to impl; final 22:45)

**Rule.** A caller may set the owner of an entity from `alt` to `neu` iff
`caller.isAdmin()` **or** (`canAdminUsers(caller)` ∧ `canAdminUser(caller, alt)` ∧ `canAdminUser(caller, neu)` ∧ *visible*(entity, caller)),
where *visible* = `canReadInformation` for allocatables (READ_NO_ALLOCATION already shows a resource in Swing's client cache) and `canRead` for reservations.
Ownerless entities and `DynamicType` stay global-admin only. Both old and new owner must be in scope — otherwise a group admin could appropriate (only-new-in-scope) or dump (only-old-in-scope) entities across the scope boundary. **The rule applies to a pure owner change only** (coordinator ruling on impl Q1): mixed changes (owner + anything else) take the normal path — otherwise a READ-only group admin could edit content through a crafted wire client. **A pure owner change skips the downstream allocatable `checkPermissions`** (coordinator ruling on impl Q2, overriding the concept lean): allocation is unchanged, like the exchange path, and there is no Swing precedent for group-admin owner changes to keep parity with; the main use case (group admin re-owns a reservation on resources they cannot book) would otherwise fail.

**Pre-existing issue, CLOSED by O1 (implemented + reviewed 2026-09-13):** `SecurityManager.checkModifyPermissions` used to refuse an owner change (~line 121) and then re-permit it through the generic `canModify(original, user)` fall-through (~137–145), which was wider than Swing's `isAdmin` menu. O1 **replaced** that fall-through for owner changes with the rule above; pinned by tier-2 case (d).

**Code check (concept session 2026-09-13, read not run):**
1. *Nested groups:* `canAdminUser(admin, u)` — scope = parent of `admin`'s `CAN_ADMIN_PARENT` group (`getGroupsToAdmin(…, addParent=true)`), `u.belongsTo(scope)` walks `getGroupsIncludingParents(u)` → sub-groups are in scope. Inherited edges: `canAdminUser` is false when `u.isAdmin()` (a group admin can never move ownership from or to a global admin) and when `admin` has no admin group. Accepted.
2. *Swing parity:* menu `isAdmin`-only (`SetOwnerMenuFactory:70`); the wire was wider (closed issue above). No group-admin owner path existed before O1.
3. *Gaps:* `Appointment.getOwnerRef` branch untouched (appointments never dispatched standalone; rule is `Ownable`-only). Preferences: own gate `checkWritePermissions(PreferencePatch)` (self/admin), untouched. Templates/periods: `Ownable` allocatables → covered; ownerless → global admin. Users, categories, `DynamicType`: not `Ownable` → outside by construction.
4. *Visibility condition:* `canReadInformation` / `canRead` as above (adopted).
5. *Notification to the new owner* (067 D10): nothing exists (the notification plugin reacts to reservation changes for the owner, not to transfers) → **residue**, notification-plugin backlog.

**Enforcement exactly once.** `PermissionController.canChangeOwner(User caller, Ownable entity, ReferenceInfo<User> alt, ReferenceInfo<User> neu)` — **instance method** (needs the resolver for `alt`/`neu` and the entity's visibility check). `SecurityManager.checkModifyPermissions`, owner-changed case: `if (isOwnerOnlyChange(entity, original) && permissionController.canChangeOwner(user, entity, alt, neu)) → permitted, skip the reservation `checkPermissions` block; else → the existing evaluation **without** the ~137 `canModify` re-permit for an owner change** (that fall-through survives only for owner-unchanged drafts). `isOwnerOnlyChange(entity, original)`: clone `entity`, `setOwner(original owner)`, serialise both with the `UpdateEvent` JSON mapper (canonical; the same JSON the change records carry) and compare — owner changes are rare, cost irrelevant; if no canonical serialisation is reachable there, fall back to a type-specific comparison after the `canExchange` pattern.

**GraphQL:** `changeReservationOwner` and `changeAllocatableOwner` replace the W1 `isAdmin` check with `permissionController.canChangeOwner(caller, entity, entity.getOwnerRef(), newOwnerRef)` per id → `PERMISSION_DENIED` @ `ids[i]`; the dispatch gate stays as the backstop. `newOwnerId` outside the caller's scope → `REFERENCE_NOT_FOUND` @ `newOwnerId`, byte-identical to an unknown user (§ 12).

**Swing:** `SetOwnerMenuFactory:70` shows the menu when `canAdminUsers(user)`; the picker lists the users the caller `canAdminUser`s (the client cache already holds exactly those); entities whose current owner is outside scope keep the menu hidden. AGENTS.md § 0a #1 parity: Swing menu, both GraphQL verbs and raw dispatch hit the one predicate.

**Tests.** Tier 2, `SecurityManagerPermissionChangeTest` owner block (fixture: a user with `CAN_ADMIN_PARENT` on a child of my-group): (a) group admin moves a reservation between two my-group members → allowed; (b) old owner outside → `RaplaSecurityException`; (c) new owner outside → exception; (d) non-group-admin EDIT user changes owner → exception (was red-first against the pre-O1 fall-through); (e) global admin → allowed; (f) in scope but entity not visible → exception; (g) ownerless allocatable / `DynamicType` by group admin → exception; (h) owner change **plus** a classification edit by a READ-only group admin → exception (owner-only rule); (i) old or new owner is a global admin → exception; (j) pure owner change of a reservation booked on a resource on which the group admin holds **no row at all** (not even READ) → allowed (Q2 skip, sharpened 23:30 — see O1b below); (k) the same with an appointment moved in the same draft → exception (mixed change, normal path). Tier 3, `PermissionWriteGraphQLTest` 11/11a updated: group admin succeeds within scope on both verbs; out-of-scope new owner → `REFERENCE_NOT_FOUND` byte-identical to unknown id; out-of-scope old owner → `PERMISSION_DENIED` @ `ids[i]`.

**Verification:** `mvn -pl rapla-server -am test -Dtest=SecurityManagerPermissionChangeTest`, then `mvn -pl rapla-app -am test -Dtest='PermissionWriteGraphQLTest,ReservationMutationControllerTest,AllocatableMutationControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`; permissions.md § 4 "Re-parenting requires admin" → the O1 rule (done 2026-09-13).

**Post-review rulings (user, 2026-09-13 ~23:25 / ~23:30), O1 review PASS:**
- **N1 — no self-transfer, by design.** An owner may **not** hand their own entity to someone else; the rule stays exactly "global admin, or group admin with both owners in scope". Review note N1 recorded as intended behaviour, not a gap.
- **WP O1b — the allocation skip is explicit.** A pure owner change skips the allocatable `checkPermissions` block ("an owner change is not an allocation"). Reason, verified in the review: `hasPermissionToAllocate` reaches its "unchanged appointment → ok" branch only *inside a matching row with ≥ READ*; without the skip a site admin fails on every resource of another building that carries no row for them at all. Test (j) sharpened to exactly that fixture. Q2 is final: skip, explicit, in code — not an emergent property of the exchange path.

### 5g. WP P1s — seed Swing default rows on GraphQL type create (user ruling P-1, 2026-09-13 ~23:30)

`saveDynamicType` with `input.id == null` and **all three** list inputs (`typeAccess`, `resourceInstanceDefaults`, `eventInstanceDefaults`) null seeds the same rows Swing's `FacadeImpl.newDynamicType` seeds — resource/person: `everyone: READ_TYPE`, `everyone: ALLOCATE_CONFLICTS`, `registerer` group row; event: `everyone: READ_TYPE`, `can-read-events-from-others`: READ, `can-create-events`: CREATE. Any non-null list means the caller took ownership of the whole list: no seeding, the W2 partial-replace rules apply to the empty type.

**One implementation:** a static helper in rapla-core (`PermissionContainer.Util.addDefaultTypePermissions(DynamicTypeImpl, Category userGroups)` or next to `copyPermissions`), built from the two `FacadeImpl.addDefault*Permissions` bodies moved verbatim; `FacadeImpl` delegates (ladder step 2 — the third copy is the one we do not write). Missing groups (`registerer`, `can-read-events-from-others`, `can-create-events` absent under `user-groups`) are skipped exactly as `FacadeImpl` skips them today.

**Tests.** Tier 2 golden master (`FacadeTestSupport`, rapla-server): `facade.newDynamicType(RESOURCE)` vs a bare `DynamicTypeImpl` + helper → identical permission lists (principal, level); same for RESERVATION. Tier 3 (`DynamicTypePermissionWriteGraphQLTest`): create a resource type with no lists → `typeAccess`/`instanceDefaults` read back show the seeded rows and monty can see the type; create an event type with no lists → the event rows; create with `typeAccess: []` (or any non-null list) → nothing seeded, the sent list only; create with only `resourceInstanceDefaults` → no `typeAccess` seeded either (all-null rule).

**Verification:** `mvn -pl rapla-server -am test -Dtest=DynamicTypeDefaultPermissionsGoldenMasterTest`, then the tier-3 class above.

## 6. Tests

Tier per AGENTS.md § 10; the § 12 leak tests are mandatory, not optional (§ 12: *"never merge a new id-list / filter endpoint without a tier-3 MockMvc leak test"*).

**Tier 2** — `rapla-server/src/test/...`, `FacadeTestSupport`, no Spring:

- Level matrix — **mostly gone as a resolver test after § 2a.** `READ_TYPE`/`CREATE` on an Allocatable, `REQUEST` on a Reservation and `ADMIN` in `typeAccess` are now unrepresentable, so they are *schema* assertions (the query fails validation before any resolver runs), not `INVALID_VALUE` round-trips. Keep one tier-3 test per case asserting the request is rejected at validation, and delete the tier-2 matrix table — testing what the type system already guarantees is dead weight. What survives at tier 2 is only the rules § 2a leaves to the server: the window-level rule, absolute-XOR-relative, and `PrincipalInput` exactly-one.
- Window rule (§ 1a): window fields accepted on `ResourcePermissionInput` rows at REQUEST / ALLOCATE / ALLOCATE_CONFLICTS / EDIT; rejected `INVALID_VALUE` at READ_NO_ALLOCATION / READ / ADMIN; absolute and relative both set → `INVALID_VALUE`. (Event / EventTemplate / Period rows need no test — `SimplePermissionInput` has no window fields at all.)
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
- **§ 2c pin (closed by P4a, 2026-09-13):** a non-owner with `EDIT` on a reservation attempts `updateReservation(permissions: [<self as ADMIN>])` → `PERMISSION_DENIED`, stored list unchanged. Was written red-first against the pre-P4a `SecurityManager`; stays as the regression pin.
- `expectedLastChanged` mismatch → `CONCURRENT_MODIFICATION`, no partial write.
- Once OQ 6 is ruled in: `effectiveAccess` for a target outside `canAdminUser` / `canAdminGroup` returns uniform `PERMISSION_DENIED` with no existence signal; self-query without argument always succeeds.

**Parity check (§ 3), no new test tier:** the paths that copy permission lists — copy resource, copy event, `reservationsFromTemplate`, `addDefaultResourcePermissions`, type-default copy at create — must keep working without calling any new verb. Existing coverage re-run, not rewritten.
