# PRD 113 — GraphQL permission model — coordination status

Coordinator: rapla-62. Sessions: concept, impl, review. Gate: only rapla-62 sends `APPROVED <WP>`, after the user confirms in the coordinator session.

## WP table

| WP | Owner | State |
|---|---|---|
| WP0 PRD 113 draft v0 | concept | done 2026-09-06 (docs/prd/113-graphql-permission-model.md), open questions pending |
| WP1 merge create/update inputs → ReservationInput / AllocatableInput | impl | APPROVED by coordinator 2026-09-07 (user go in coordinator session); narrowed then re-widened same day (validation + tests in); impl DONE 00:35, coordinator-verified (leftover grep clean, 53/53 tier-3 green); review PASS 00:40 (§12 clean, test-first observed); R1/R2 done 00:45 (coordinator spot-checked); WP1 COMPLETE, uncommitted — awaiting user commit call |
| DOC-1 permissions.md:165 NO_PERMISSION described as stored 'disabled row' — wrong (matcher return value only; coordinator-verified in PermissionContainer.getUserEffect) | concept 2 | done 01:05, coordinator spot-checked |
| WP0b PRD 113 concept continuation | concept 2 [081595] (was rapla-3a) | in progress since 21:58; taking OQ 1–7 to the user |

## Answers (user)

- 2026-09-07 (user, coordinator session): **make the merge now in impl; give concept sessions the new schema** → WP1 approved.
- 2026-09-07 (user, coordinator session): **WP1 = only the create/update schema merge, not the other specs** — no id/ownerId validation on update, no new tests for it; existing behaviour unchanged.
- 2026-09-07 (user, coordinator session): **validation and test rules back in WP1** ("you can add validation and test rules") — supersedes the narrowing.
- 2026-09-07 (user, coordinator session): **no separate create/update inputs.** Still early beta, schema breaks allowed. Merge CreateReservationInput/UpdateReservationInput and CreateAllocatableInput/UpdateAllocatableInput into one input per entity (DynamicTypeInput pattern); new EventTemplate/Period inputs are single from the start. Update the carrying PRDs (056, 063). Standing order for impl: do not differentiate create and update inputs.

### Reported by concept session 2026-09-06 21:05 — NOT yet confirmed to coordinator
- Permission-carrying API entities: Resource/Person (Allocatable), Event, EventTemplate, Period, DynamicType (Template + Period separate API entities though stored as allocatables).
- DynamicType permissions split in API: `typeAccess` (READ_TYPE, CREATE; no ADMIN) + `instanceDefaults` (rows copied to new instances, ADMIN lives here); one stored list, projection + per-list validation on write.
- OQ 9 (21:08): instanceDefaults keep full Swing parity — same levels and windows as a resource row, absolute windows included (yearly re-set use case). No API restriction.
- OQ 8 (21:10): add `id: ID!` to GraphQL `Period`; name stays display field; additive only, no period(id:) query, no period CRUD in this PRD.
- §1c (21:13): EventTemplate becomes an entity view (id, name, fixedTimeAndDuration, owner, createdAt, lastModifiedAt, canModify, canAdmin, permissions, allowedLevels); new queries eventTemplate(id:) / eventTemplates gated by canRead(template); newEventOptions keeps its gate; no template CRUD. canAdmin + allowedLevels on every permission-carrying entity.
- DENIED rows (22:00, via concept): need no handling in PRD 113 — impossible after PRD 090 migration; skip.
- OQ 1 (22:05, via concept): read and write in one delivery, not read-first. Group-administration sibling PRD does not gate 113.
- OQ 2 (22:30, via concept): write shape D — permissions as nullable inputs inside the existing save mutations (create/updateAllocatable, updateReservation, saveDynamicType with typeAccess/instanceDefaults); no separate setPermissions verbs. updateEventTemplate / updatePeriod ADDED (revises earlier 'no template/period CRUD' — update only).
- Typed permission inputs (2026-09-07, via concept): ResourcePermissionInput (resource levels + windows), SimplePermissionInput (READ/EDIT/ADMIN, no window), TypeAccessInput (READ_TYPE/CREATE, group|everyone only), shared PrincipalInput; allowedLevels output dropped; DynamicTypeInput gets resourceInstanceDefaults / eventInstanceDefaults. Open: EventTemplateInput/PeriodInput after the merge, template/period CREATE in scope?
- OQ 3 (00:55, via concept): entity admin (canAdmin) sees user names on ALL existing rows of that entity (Swing-equivalent); picker for new rows shows only users in the caller's existing read scope. PrincipalInput = @oneOf { userId | groupId | everyone: true }; read side three-way object {user, group, everyone}. RECORDED AS A DELIBERATE §12 WIDENING beyond Swing (Swing cannot render out-of-scope names); pinned by a test asserting the name IS present + boundary test that users(filter:) still hides that user. PrincipalInput @oneOf {userId, groupId, everyone(must be true)}; TypeAccessPrincipalInput @oneOf {groupId, everyone}; output `type PermissionPrincipal { user, group, everyone: Boolean! }`.
- OQ 4 (00:55, via concept): closed, not applicable — disabled row is not a stored state.
- §1c DELETION (needs explicit user go per AGENTS §11): EventTemplate.path removed. Coordinator check 2026-09-06: TemplatePathBuilder used only by ReservationGraphQLController + its test; SPA fetches `path` in new-event-options.service.ts (query selection + interface field) but no code reads it afterwards.

### Proposed by concept, NOT ruled (21:20)
- DynamicType write folded into saveDynamicType as nullable typeAccess/instanceDefaults inputs.
- users/groups/categories stay read-only; membership editing = sibling PRD; PRD 113 only adds parent/children on Group for the picker.

### Gap list from concept → concept 2 (review input, 22:00)
Missing Tests/Plan section (tier-3 §12 leak tests per field/verb, tier-2 matrix/window/DynamicType merge); READ_NO_ALLOCATION resources invisible in SPA catalog; PRD 083 PermissionIndex maintenance as verification step; user-picker scope for non-admin entity admins (OQ 3 input side). Outside 113 before an editor ships: SPA permission-editor PRD, owner-change verbs for allocatables/templates/periods, group-administration sibling PRD.

### SECURITY FINDING (concept 2, 22:40; coordinator-verified in SecurityManager.checkModifyPermissions)
The ACL-rewrite gate at rapla-server/.../SecurityManager.java:250 covers only `Allocatable`/`Category`; `Reservation` is not in the instanceof list and the reservation block above it (checkPermissions) covers allocation only. A non-owner with EDIT on a reservation can rewrite its permission list (self-grant ADMIN, grant third parties READ). Pre-existing, reachable from Swing today; shape D's updateReservation.permissions would expose it too. PRD 113 P4a = extend the gate to Reservation, failing test first. Decision needed: fix now as standalone security fix (bugfix session) or inside 113 P4a.

### concept 2 § 2a consequences (2026-09-07)
- Output stays one `Permission` with full AccessLevel; input splits into three typed inputs with their own level enums. allowedLevels dropped everywhere; SPA reads permitted sets via enum introspection. DynamicTypeInput: typeAccess + resourceInstanceDefaults + eventInstanceDefaults (server rejects the one not matching classificationType); output keeps one instanceDefaults.
- Test plan shrank: tier-2 level-matrix table deleted (unrepresentable levels fail at query validation → one tier-3 validation assertion each). Tier-2 keeps only server-enforced rules: window-level rule, absolute-XOR-relative, PrincipalInput exactly-one. § 2a carries a schema-enforced vs server-enforced table. → pass to impl/review with the 113 WPs.
- Plan P1 gains `Period.categories: [Category!]!` read field (needed so a save echoing categoryIds can be built from a read).

### concept 2 correction (2026-09-07 00:50)
PRD 113 § 1d now carries the landed WP1 schema verbatim. Earlier § 1d wording "id null = server generates" was stale (PRD 056 § 9 revised 2026-07-06: client id mandatory on create). Type stays nullable `ID` because update may omit it — requiredness on create is a resolver rule; do not change to `ID!`.

### concept 2 note for PRD 113 impl (2026-09-07)
`checkReservation` is read-only: once ReservationInput carries `permissions`, a draft's permission list must be ignored there, never applied. Recorded in PRD 113 § 1d; not a WP1 concern.

### concept 2 findings (22:10)
- EntityKind is {RESERVATION ALLOCATABLE USER PERMISSION} (schema.graphqls:1662, coordinator-verified) — Shape B cannot reuse it; would need PermissionTargetKind. concept 2 now leans A (typed verbs) over B. Not ruled.
- PermissionIndex maintenance: verified not a work item (invalidateAll on permission-affecting updates at the storage seam) → one verification test.
- User-picker scope merged into OQ 3.
- NEW OQ 10: READ_NO_ALLOCATION — SPA catalog gates on canRead(100), so a resource granted only READ_NO_ALLOCATION (50) is invisible in the SPA while Swing shows it bookings-hidden. Offer the level in the editor with a note (lean) or hide it?
- NEW OQ 11: group/user membership editing scope — under PRD 090 membership change is the ONLY way to reduce access; editor without it can only grant. Choice: nothing / membership-only verb / full user+group admin.

## Pending decisions for the user

As of 2026-09-07 01:10 (PRD 113 § 2 rewritten as decided design; § 2c = reservation gate gap): OPEN OQ 5, 6, 7, 10, 11, 12, 13 + path deletion go + DENIED confirmation + gate-gap routing + WP1 commit call + user confirmation of concept-relayed rulings (OQ 1–4, 8, 9, §1a–c, typed inputs). Ruled/closed in the PRD: OQ 1, 2, 3, 4, 8, 9.

Authoritative wording: PRD 113 § 4 (concept 2, 22:20). Leans are concept 2's; coordinator verified the OQ 3 parity claim against permissions.md § read filter (users visible = self + admins + administered-group members).

1. Confirm the rulings reported by concept (§1a entities, §1b DynamicType split, §1c EventTemplate entity, OQ 1 one delivery, OQ 8 Period id, OQ 9 defaults keep windows, DENIED skip).
2. Explicit go for deleting EventTemplate.path (AGENTS §11).
3. OQ 2 write shape — reported ruled as shape D (inputs on existing save mutations); confirm.
4. OQ 3 — reported ruled (admin sees all rows; picker read-scoped); confirm.
5. OQ 4 — reported closed as n/a; confirm.
6. OQ 5 reservations in v1 — lean include.
7. OQ 6 effectiveAccess in this delivery — lean out.
8. OQ 7 windows in v1 input — lean in.
9. OQ 10 READ_NO_ALLOCATION in editor — lean offer with note.
10a. OQ 12 EventTemplateInput {name, fixedTimeAndDuration, permissions} / PeriodInput {name, start, end, permissions}; create/delete in scope? — lean update-only.
10b. SECURITY: reservation ACL gate gap — fix now standalone or as 113 P4a?
10c. OQ 13 (concept 2): merge only the inputs (WP1 as approved, verbs createX/updateX stay) or also collapse the verbs into one saveX like saveDynamicType? Lean inputs only. NOW COUPLED to OQ 12: the concept proposal gives templates/periods `save*` verbs with nullable id — that makes CREATE reachable by construction and leaves two verb conventions side by side. Rule OQ 13 first, then templates/periods follow.
10d. OQ 12 revised: (a) EventTemplateInput/PeriodInput field lists, (b) template/period CREATE in or out — decided by accident if save* with nullable id is chosen, (c) verb shape follows OQ 13.
10e. concept 2 suggestion, not ruled: @oneOf on PrincipalInput (wart: everyone:false counts as set — reject false or make it a valueless marker).
10. OQ 11 membership editing — (a) nothing / (b) membership-only verb / (c) full admin; concept 2 leans (b), coordinator leans (a).

PRD § 5 Plan: input merge = WP1 (unnumbered prerequisite for P4, not a 113 step) → P1 read types+fields+Period.id → P2 EventTemplate entity + path deletion → P3 DynamicType read projection → P4 write verbs → P5 DynamicType write → P6 effectiveAccess/Group.parent-children/membership verb. § 6 Tests written (spec only). WP cut waits on rulings.

## Residue

- WP1 files (uncommitted): schema.graphqls, ReservationMutationController.java, AllocatableMutationController.java, 3 test classes, rapla-angular event-data.service.ts + allocatable-data.service.ts, docs/architecture/legacy-urls.md:96.
- Pre-existing lint error in rapla-angular src/app/views/document-catalog.store.spec.ts (another session's file) — untouched.
- PRD 112 patch loader rejects gitignored data/patch/Leihschein.* during tests (unknown type personClassification) — pre-existing noise, Siegen-specific.

- PRE-EXISTING (review, coordinator-verified 2026-09-07): `ChangeOp.createAllocatable` / `updateAllocatable` are declared in schema.graphqls but applyChanges (ReservationMutationController ~line 712) handles only createReservation/updateReservation/deleteReservation — allocatable batch ops fall through to OP_INVARIANT. Not WP1; candidate bugfix WP or PRD 063 follow-up.
- PRE-EXISTING doc staleness: schema.graphqls createReservation doc says id optional; controllers throw REQUIRED (PRD 056 §9). Being fixed inside WP1 since impl rewrites that doc block anyway.

- CLOSED by WP1 validation — WP1 interim state (review, coordinator-verified 2026-09-07): after the merge, `ownerId` and `id` on an UPDATE request are accepted by schema validation and silently ignored by the server (update paths read only typeKey + classification; create keeps its REQUIRED id and admin-only ownerId checks). Previously GraphQL rejected them as unknown fields. The validation cut from WP1 by the user would close this; candidate follow-up WP, or lands with PRD 113 P4.

- bugfix session (unrelated to PRD 113, user-directed): remember-me tick on login page; files CookieAuthSupport.java, FormLoginSuccessHandler.java (rapla-server), LoginPageController.java + new RememberMeChoiceTest (rapla-app). Uncommitted; targeted tests green per session report (not verified by coordinator).

## Server — who has it

8051: nobody has claimed it. Restart only after announcing to rapla-62.
