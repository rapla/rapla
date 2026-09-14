# PRD 117 — Administration over GraphQL: users, group membership, categories/groups, periods and templates

**Status:** draft 2026-09-14 — design only, no code. Sibling of [PRD 113](113-graphql-permission-model.md) (its [OQ 11](113-graphql-permission-model.md#4-open-questions-for-the-user) ruled "(a) nothing in 113; sibling PRD for a membership verb", and its P-2 ruling hands over the residue "group/user visibility scoping"). Requested by the PRD 113 coordinator session; all decisions in § 4 are open and wait for the user. Order relative to the PRD 113 commit: user decides.

**Guiding ruling (user, relayed by the coordinator 2026-09-14):** **entities go over GraphQL, procedures and configuration stay REST.**

## 1. Scope

**In**
1. **Users** — `createUser` / `updateUser` / `deleteUsers`.
2. **Group membership** — a verb of its own (the only downward lever under [PRD 090](done/090-additive-permission-resolution.md): access never subtracts, so narrowing a person means changing membership).
3. **Categories and groups** — create / update / delete.
4. **Periods and event templates** — create / delete (update exists since PRD 113 W2: `updatePeriod`, `updateEventTemplate`).
5. Residues handed over: group/user visibility scoping (113 P-2), direct-membership gate (S1 side finding), S1 review N1 (→ E2).

**Security note (AGENTS.md § 17):** E2, E8 and E9 name open gate questions. Their reasoning and paths are kept in the gitignored `docs/security/117-open-findings-2026-09.md`, not here. If they are fixed in the running security block first (proposed S8), this PRD lists them as closed.

**Out — stay REST (or elsewhere)**
Options and admin panels ([PRD 020](020-server-driven-admin-panels.md)), import/export, password set/reset (`RemoteStorage` `/change/password`), `disconnect-external-auth`, migrations (e.g. `/api/admin/permission-migration`), server status. Moving a category to a different parent (re-parenting) — see E10. The SPA admin UI itself.

**Pattern (as 056 / 063 / 113):** one merged input per entity, `create*` / `update*` verbs (113 OQ 13), bulk `delete*(ids)` → `BulkResult`; `WriteGate` → `SecurityManager.checkWritePermissions` / `checkDeletePermissions` stays the **only** gate; controllers only pre-check to choose the error code/path; PRD 056 error codes; § 12 tier-3 leak tests (unknown id == invisible id, byte-identical); ownership per [PRD 067 D10](067-server-mutation-unification.md) and 113 § 5f.

## 2. What exists today (read 2026-09-14, not run)

**Gate — `SecurityManager.checkModifyPermissions`** (`rapla-server/.../server/internal/SecurityManager.java`), global admin returns early:
- **User branch** (~l. 197–235): `canModify(entity) && canModify(original)`. `RaplaDefaultPermissionImpl` answers `canModify(User)` = `false` if the target *(stored or submitted)* `isAdmin`, else `canAdminUser(caller, target)`. So a group admin can neither edit an admin nor set the admin flag.
  - Authentication source change → global admin only (F6-1). Open point on this rule → E2 (security file).
  - Group diff: added and removed groups must each satisfy `canAdminGroup(getGroupsToAdmin(caller), g)` (group = scope root or descendant).
  - Final check `Collections.disjoint(newCompleteUserGroups, groupsToAdmin)` → reject. `groupsToAdmin` holds the **scope roots** only, so the user must be a **direct** member of a scope root; a user who is only in a sub-group of the scope is `canAdminUser`-visible (that uses `belongsTo`, parents included) but **not editable** (**S1 side finding**).
- **Category branch** (~l. 182–195): `canModify(Category)` = the category is a scope root or its descendant (`RaplaDefaultPermissionImpl`); non-group categories → global admin only. Any change of `can_admin_parent` → global admin only (PH1), which on create means a group admin can never set it (`before == null`).
- Changing a `Category` permission list needs `canAdmin` (~l. 276–291).
- Scope definition: `PermissionController.getGroupsToAdmin(user)` = the **parent** of every group of the user that carries `can_admin_parent=true`. Test fixture: `monty` ∈ `powerplant-admins` (`can_admin_parent`) → scope root `powerplant`.

**Store mechanics.**
- Categories are stored as part of their parent: Swing creates with `facade.newCategory()` + `edit(parent).addCategory(child)` (`RaplaObjectActions.createNewNodeAt`); on removal the operator edits the parent itself (`LocalAbstractCachableOperator` ~l. 3390). Key renames: the operator rewrites key-path referrers (`addCategoryKeyPathReferers`) — keys migrate in-app, as PRD 058 does.
- `FacadeImpl.newUser()`: adds `Permission.DEFAULT_USER_GROUPS` (`can-read-events-from-others`, `can-create-events`) and, for a group admin, the first scope root.
- `FacadeImpl.newPeriod(user)`: `rapla:period` allocatable, `newAllocatable` defaults. Swing `TemplateEdit.createTemplate`: `rapla:template` allocatable with **all permission rows removed** (owner-only); `removeTemplate` removes the template allocatable only.
- Delete: `DependencyException` (references from other entities; deleting yourself → `error.deletehimself`). GraphQL precedent: `deleteDynamicTypes` → `REFERENCE_EXISTS` @ `ids[i]`.
- Username uniqueness: no check found on the dispatch path (`grep`; only `cache.getUser(username)` lookups) — **verify at impl** (§ 6 A0).

**Reads.** `me`, `users(filter:)`, `user(username:)`: self + `canAdminUser` (`HelloGraphQLController`). `groups` / `group(id)` / `Group.parent|children`: every authenticated caller (`GroupGraphQLController`, "categories are global metadata"). `category(path)` / `categories(rootKey)` filter out the `user-groups` subtree. `periods`: `canReadInformation` (113 § 5e). `User.groups` visible where the user is. Swing client cache: all categories; users = self + admins + members of administered groups (`LocalCache.getVisibleEntities`).

**GraphQL writes today:** reservation, resource, dynamic-type verbs, `updatePeriod`, `updateEventTemplate`, `change*Owner`. No user, group or category write verb. REST: `UsersService` is GET-only (`/api/users`, `/me`).

## 3. Design

### 3a. Users

```graphql
input UserInput {
  username:   String!
  name:       String
  email:      String
  isAdmin:    Boolean          # null = false on create, unchanged on update; true/change → global admin only
  authSource: String           # global admin only, create AND update (F6-1 / N1)
}
createUser(input: UserInput!): User!
updateUser(id: ID!, input: UserInput!, expectedLastChanged: LocalDateTime): User!
deleteUsers(ids: [ID!]!): BulkResult!
```

- **No `groupIds` in `UserInput`** (E3): membership has exactly one write path, § 3b. Create seeds groups like `FacadeImpl.newUser`: global admin → `DEFAULT_USER_GROUPS`; group admin → first scope root **plus** those default groups that lie inside the scope (a default group outside the scope would make the store fail the group-diff check — verify in A0 how Swing behaves today; if Swing fails there too, parity = scope root only).
- Gates: `updateUser` / `deleteUsers` on an id that is unknown or outside `self ∪ canAdminUser` → `REFERENCE_NOT_FOUND` @ `id` / `ids[i]`, byte-identical. Self-edit: `canAdminUser(self, self)` is false for a non-admin, so a plain user cannot update their own record through this verb (account fields stay the SPA account dialog / REST) — E4.
- `isAdmin` / `authSource` from a non-global-admin → `PERMISSION_DENIED` @ `input.isAdmin` / `input.authSource`, on create and update alike. The store gate carries the same rule (E2), so Swing and raw dispatch match.
- Delete: self → `INVALID_VALUE` @ `ids[i]`; referenced (owner of reservations/resources, preferences, …) → `REFERENCE_EXISTS` @ `ids[i]` **without referrer ids** — the referrers may be entities the caller cannot read (§ 12). Whole batch rejects (`BulkStatus.REJECTED`), as `deleteReservations`.
- Password: not in the input. A new local user has no password until an admin sets one via the existing REST endpoint — documented, no new surface.
- Username taken → `INVALID_VALUE` @ `input.username`. This necessarily reveals that the username exists, including an out-of-scope one; Swing reveals the same. Documented as an accepted existence signal for callers with `canAdminUsers` only (E5).

### 3b. Group membership — one verb, both directions

```graphql
changeUserGroups(userIds: [ID!]!, addGroupIds: [ID!], removeGroupIds: [ID!]): BulkResult!
```

- **Delta, not replace** (E6). Replace would force the caller to echo groups outside their scope, which the group-diff check then rejects as "removed"; a delta names only what changes. One verb covers the user side (one user, n groups) and the group side (n users, one group) — this answers 113 OQ 11's sub-question without a second verb.
- Gate = the unchanged User branch: every added/removed group inside `getGroupsToAdmin(caller)`, user in `canAdminUser` scope. Unknown or invisible user → `REFERENCE_NOT_FOUND` @ `userIds[i]`; unknown group → `REFERENCE_NOT_FOUND` @ `addGroupIds[j]`; known group outside scope → `PERMISSION_DENIED` @ `addGroupIds[j]` (groups are globally visible, § 3d — no existence to hide). Add of an existing membership / remove of a missing one = no-op, not an error.
- **S1 side finding (E7):** replace the final `disjoint(newCompleteUserGroups, groupsToAdmin)` with "the user still `belongsTo` some scope root" — the same relation `canAdminUser` already uses. Effect: sub-group-only members of the scope become editable; removing a user's **last** membership inside the scope stays forbidden (a group admin cannot push a user out of their own reach) → `INVALID_VALUE` @ `removeGroupIds`. Not a widening of who is *visible*, only a consistency fix between the read gate and the write gate.
- Membership changes of groups carrying `can_admin_parent` → E8 (reasoning in the security file).

### 3c. Categories and groups

Two verb sets over one storage type, because the API already separates them (`Category` resolvers hide `user-groups`, `Group` is first class):

```graphql
input GroupInput    { key: String!  name: LocalizedTextInput!  adminOfParent: Boolean }   # adminOfParent = can_admin_parent → global admin only (PH1)
input CategoryInput { key: String!  name: LocalizedTextInput!  annotations: … }           # annotation set: E11
createGroup(parentId: ID, input: GroupInput!): Group!          # parentId null = directly under user-groups (global admin only)
updateGroup(id: ID!, input: GroupInput!, expectedLastChanged: LocalDateTime): Group!
deleteGroups(ids: [ID!]!): BulkResult!
createCategory(parentId: ID, input: CategoryInput!): Category!  # parentId null = top level
updateCategory(id: ID!, input: CategoryInput!, expectedLastChanged: LocalDateTime): Category!
deleteCategories(ids: [ID!]!): BulkResult!
```

- A group id sent to a category verb (or vice versa) → `REFERENCE_NOT_FOUND`, identical to unknown.
- **Categories: global admin only** — already what `RaplaDefaultPermissionImpl` answers; the verb pre-checks → `PERMISSION_DENIED`.
- **Groups:** group admin inside scope for create (parent in scope), rename/key change and delete. Store mechanics as Swing: parent clone + `addCategory` on create; delete as a remove id (the operator edits the parent).
- **Deleting scope roots and `can_admin_parent` groups** → E9 (reasoning in the security file). Proposed rule: a group admin deletes only strict descendants of a scope root that carry no `can_admin_parent`; enforced in the Category branch (core, Swing included).
- Delete with members / attribute values / period categories referencing it → `REFERENCE_EXISTS` (no referrers, § 12). Children: verify at A0 whether the operator removes the subtree or rejects; the verb follows whatever the operator does and the test pins it.
- Key change rewrites key-path referrers in-app (existing operator path). Well-known group keys (`can-create-events`, `registerer`, …) → E12.

### 3d. Periods and event templates

```graphql
createPeriod(input: PeriodInput!): Period!                       # PeriodInput from 113 W2
deletePeriods(ids: [ID!]!): BulkResult!
createEventTemplate(input: EventTemplateInput!): EventTemplate!   # EventTemplateInput from 113 W2
deleteEventTemplates(ids: [ID!]!): BulkResult!
```

- Revises 113 OQ 12(b) "update-only" — that ruling scoped 113, this PRD is where create/delete was sent.
- Gate: `canCreate` on the `rapla:period` / `rapla:template` type (the store gate's `Classifiable` create check), owner = caller; delete = `checkDeletePermissions` (`canAdmin` or owner). Wrong type / unknown / unreadable id → `REFERENCE_NOT_FOUND`, as `update*`.
- Permission rows on create: `permissions` null → Swing parity — period: `newAllocatable` defaults; template: **empty list** (owner-only, as `TemplateEdit`). Non-null → the list sent, `canAdmin`-free because the caller is the owner.
- Separate typed delete verbs rather than letting `deleteResources` take templates/periods (E13): symmetric with `update*`, and `deleteResources` keeps its resource-only contract.
- Template reservations (`getTemplateReservations`) on template delete: Swing leaves them. E14.

### 3e. Group/user visibility scoping (residue of 113 P-2)

Two separate surfaces:
1. **Users — reopens 113 P-2** (ruled 2026-09-13 ~23:30: *document, do not enforce*). Already scoped on read (self + `canAdminUser`). E1 asks whether the **input** side of permission rows gets the same scope (113 P-2 option (a); reasoning in the gitignored `docs/security/113-reviews-and-findings-2026-09.md`): an out-of-scope `userId` would answer `REFERENCE_NOT_FOUND` @ `…principal.userId`, byte-identical to unknown. With this PRD adding user administration, the "one place to solve it" is here.
2. **Groups.** Stay globally visible (E15). Reason: Swing's client cache holds every category, `everyone` rows need no principal at all, and a group row never grants more than the caller could already grant to `everyone`. Scoping groups would be a Swing deviation with no leak closed. Recorded in permissions.md as intended.

## 4. Decisions for the user (yes/no)

| # | Question | Lean |
|---|---|---|
| E1 | **Reopens 113 P-2** (ruled: document, not enforce). Enforce the principal scope on permission inputs after all: a `userId` outside `self ∪ canAdminUser` answers like an unknown id? | yes — needs a conscious reversal of P-2 |
| E2 | `authenticationSource` on user **creation** only by a global admin (all write paths)? Reasoning: security file. | yes |
| E3 | `UserInput` without `groupIds` — membership only via `changeUserGroups`? | yes |
| E4 | `updateUser` is an **admin** verb only (no self-edit through it; own account fields stay in the account dialog / REST)? | yes |
| E5 | Accept "username taken" as an existence signal for `canAdminUsers` callers (Swing parity)? | yes |
| E6 | Membership as a **delta** verb (`changeUserGroups` with add/remove lists), covering user side and group side in one verb? | yes |
| E7 | Fix the S1 side finding: sub-group-only members of a scope are editable (`belongsTo` instead of direct membership), last in-scope membership still not removable? | yes |
| E8 | Membership changes of `can_admin_parent` groups only by a global admin? Reasoning: security file. | yes |
| E9 | Deleting a scope root or a `can_admin_parent` group only by a global admin? Reasoning: security file. | yes |
| E10 | Re-parenting categories/groups stays out? | yes |
| E11 | `CategoryInput` carries annotations (e.g. `category-kind`, colour) — or key + name only in v1? | key + name only |
| E12 | Renaming the keys of well-known groups (`can-create-events`, `can-read-events-from-others`, `registerer`) forbidden (they are referenced by key in code)? | yes |
| E13 | Separate `deletePeriods` / `deleteEventTemplates` instead of widening `deleteResources`? | yes |
| E14 | Template delete also deletes its template reservations? (No = Swing parity, they stay.) | no — parity; verify they do not surface anywhere first |
| E15 | Groups stay globally visible (no read scoping), documented in permissions.md? (P-2 context; consistent with P-2's reasoning.) | yes |
| E16 | One PRD for all four blocks, work packages A1–A4 deliverable separately? | yes |

## 5. Plan (work packages)

| WP | Content | Depends |
|---|---|---|
| A0 | Core gate changes in `SecurityManager` / `RaplaDefaultPermissionImpl`: E2 (N1), E7 (S1), E8, E9; verifications: username uniqueness on dispatch, `newUser` default groups for a group admin, category subtree delete. Tier 2 first. | rulings |
| A1 | `createUser` / `updateUser` / `deleteUsers` | A0 |
| A2 | `changeUserGroups` | A0 |
| A3 | Group and category verbs | A0 |
| A4 | `createPeriod` / `deletePeriods` / `createEventTemplate` / `deleteEventTemplates` | — (113 committed) |
| A5 | E1 principal scope in `PermissionInputMapper` | — |
| A6 | Docs: permissions.md (§ 4 membership as the downward lever with its verb, E7/E9/E15), graphql.md, PRD README entry | all |

## 6. Tests

**Tier 2 — `SecurityManagerDispatchFieldGuardTest` / new `SecurityManagerUserAdminTest`** (`FacadeTestSupport`, fixture `monty` scope `powerplant`), each red first:
1. E2 guard — cases in the security file (or closed by S8).
2. group admin creates user with `isAdmin=true` → exception.
3. sub-group-only member of `powerplant`: group admin adds/removes an in-scope group → ok (E7; red against `disjoint`).
4. group admin removes the last in-scope membership → exception.
5. group admin adds a group outside scope / removes one outside scope → exception (existing rule, pinned).
6. E8 guard — cases in the security file (or closed by S8).
7. E9 guard — cases in the security file (or closed by S8).
8. group admin creates a group under `powerplant` → ok; under a non-scope parent → exception; with `can_admin_parent` → exception (PH1, pinned).
9. group admin creates / renames a non-group category → exception.
10. duplicate username on dispatch → rejected (or documented gap, per A0 verification).

**Tier 3 — leak tests** (pattern `UsersInGroupFilterGraphQLTest` / `PermissionWriteGraphQLTest`; responses compared byte-identically):
- `UserAdminLeakGraphQLTest`: `updateUser` / `deleteUsers` / `changeUserGroups` with an out-of-scope user id == unknown id; mixed batch visible + hidden + unknown → the same error at the same index as all-unknown; `deleteUsers` on a referenced in-scope user → `REFERENCE_EXISTS` with no referrer ids.
- `PermissionPrincipalScopeGraphQLTest` (E1): monty grants a row to a user he cannot administer == unknown `userId`.
- `GroupAdminGraphQLTest`: group verb on a category id == unknown; category verb on a group id == unknown; group outside scope on `changeUserGroups` → `PERMISSION_DENIED` (not hidden, groups are global).
- `PeriodTemplateLifecycleGraphQLTest`: create without `canCreate` on the type → `PERMISSION_DENIED`; template created with null permissions reads back an empty list, period with the `newAllocatable` defaults; delete of a resource id via `deletePeriods` == unknown id.
- Schema assertion: `UserInput` has no `groupIds`, no `password`.

**Verification:** `mvn -pl rapla-server -am test -Dtest='SecurityManager*Test'`, then `mvn -pl rapla-app -am test -Dtest='*Admin*GraphQLTest,PermissionPrincipalScopeGraphQLTest,PeriodTemplateLifecycleGraphQLTest' -Dsurefire.failIfNoSpecifiedTests=false`.
