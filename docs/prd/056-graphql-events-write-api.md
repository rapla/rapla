# PRD 056 — GraphQL Events Write API (Reservation Mutations)

**Status:** in-progress — v1 controller + schema landed 2026-05-29 (`ReservationMutationController` covering all 7 mutations: `createReservation`, `updateReservation`, `changeReservationOwner`, `moveReservations`, `copyReservations`, `deleteReservations`, `applyChanges`; `@oneOf ChangeOp`; per-DynamicType typed classification inputs (β²); `MutationExceptionResolver`; 9 tier-3 tests including happy-path create-then-read-back + restriction round-trip). Design refinements deferred per PRD body's open questions; happy-path coverage closes the gap that previously caught the `typeId`/`typeKey` asymmetry + appointment-id-not-honored bug.

**Parent:** [PRD 035 (done) — Foundations](done/035-graphql-foundations.md) — supersedes the former §6 "Bulk mutations" per the 2026-05-28 design discussion. **Sibling:** [PRD 055 — Events Read API](055-graphql-events-read-api.md) (reopened 2026-05-29 for Tier-1 perf migration).

**Related cross-PRDs:**
- [PRD 094 — SPA main-view actions & popups](094-spa-main-view-actions-and-popups.md) — Phase 4 (calendar drag/resize move) is the consumer of the `moveAppointment` verb designed here; PRD 094 D5 locks that the EVENT/SERIE/SINGLE cascade stays server-side in this mutation, not in the client.
- [PRD 040 — dispatch validate before lock](040-dispatch-validate-before-lock.md) — lock-set requirement for bulk operations
- [PRD 057 (done) — DT Mutations v1](done/057-graphql-dt-mutations-v1.md) + [PRD 061 — DT Mutations v2](061-graphql-dt-mutations-v2.md) — schema-editor mutation surface
- Future PRD — allocatables write (extends `applyChanges`)
- Future PRD — users / permissions write (extends `applyChanges`)
- Future PRD — templates (defers `instantiateFromTemplate` bulk verb)

## Goal

Land the write-side **events / reservation** GraphQL surface — single-entity
editor flow + typed bulk transformations + a generic cross-type escape hatch
that preserves rapla's existing atomic dispatch model.

Design principles:
1. **Preserve rapla's atomic-across-types capability.** `operator.dispatch(UpdateEvent)`
   supports mixed entity types in one transaction today; the GraphQL surface must not
   regress that.
2. **Use named verbs for high-level admin intents.** "Change owner of 50 lectures"
   is one logical operation, not 50 separate updates.
3. **Type safety end-to-end.** Each operation has a typed input contract;
   codegen consumers get compile-time field access.
4. **One mental model.** All mutations ATOMIC by default in v1; PARTIAL is a
   future additive extension.

## Scope

In v1 (this PRD):
- Single-entity CRUD: `createReservation`, `updateReservation` (full state, with
  `expectedLastChanged` for update)
- Bulk transformations: `changeReservationOwner`, `moveReservations`,
  `copyReservations`, `deleteReservations`
- Generic atomic batch: `applyChanges(operations: [ChangeOp!]!)` with `ChangeOp`
  carrying typed create/update/delete inputs for reservations
- `ValidationError` shape + initial error code taxonomy
- Per-mutation §12 permission gates
- Same-batch entity-reference via client-generated UUIDs

Out (future PRDs):
- **PARTIAL mode** — deferred; v1 is ATOMIC-only. Additive enum extension when
  needed (`BulkMode`, `mode:` arg, `PARTIAL_SUCCESS` status)
- **`instantiateFromTemplate`** — defers to a templates PRD (current
  `rapla:template` filter from PRD 055 excludes templates from GraphQL)
- ~~**`reshapeClassification`** preview query + type-change save path —
  separate PRD~~ *(obsolete 2026-07-07: type change lands in
  `updateReservation` itself, preview is client-side — OQ1.c revision + PRD 096)*
- **Allocatable / User / Permission mutations** — extend `ChangeOp` in future
  PRDs (057, 058)
- **`dryRun` validation-only mode** — additive, deferred to first real consumer
  need
- **Conflict pre-flight (`checkConflicts`)** — separate query, separate PRD
- **DynamicType schema editor mutations** — when the Angular SPA gains a
  schema editor (create / update / delete DynamicTypes + their Attributes),
  it'll need `createDynamicType` / `updateDynamicType` / `deleteDynamicType`
  + an `AttributeInput` shape. The hot-swap mechanism built in PRD 055 Cut C
  fires on these too — admin's edits are visible within ~10s. Out of scope
  for events; needs its own PRD when the editor work starts.

## Locked decisions (2026-05-28)

### 1. API style — Approach 2 (named verbs)

Considered three approaches:
- **A**: typed-per-entity bulk mutations (`bulkCreateReservations`,
  `bulkCreateAllocatables`, …)
- **B**: generic untyped mutation (`mutate(operations: [GenericOp!]!)` with
  `data: JSON`)
- **C**: typed operations under a generic batch (input-as-oneof)

**Resolved: Approach 2 (named verbs) for high-level intents + Option C
(`applyChanges` with typed `ChangeOp`) for the escape hatch.**

Reasoning: A regresses rapla's atomic-across-types capability. B erases
input typing — codegen consumers lose half the value. C preserves rapla's
existing model while keeping typed inputs. Named verbs above C are the
high-level intent layer (changeOwner, move, copy).

### 2. Surface — 6 mutations

```graphql
type Mutation {
  # ===== editor flow (single-entity, full state) =====
  createReservation(input: CreateReservationInput!): Reservation!
  updateReservation(id: ID!, input: UpdateReservationInput!,
                    expectedLastChanged: LocalDateTime): Reservation!

  # ===== bulk transformations (typed intent verbs) =====
  changeReservationOwner(ids: [ID!]!, newOwnerId: ID!): BulkResult!
  # ⚠ SHIPPED SHAPE (PRD 101, 2026-07-09): dateShift:Duration was replaced by
  #   reference/target and the Duration scalar was deleted. See PRD 101 for the
  #   live signatures + the moveAppointment/splitOccurrence appointment verbs.
  moveReservations(ids: [ID!]!, reference: LocalDateTime, target: Target!): BulkResult!
  copyReservations(ids: [ID!]!, reference: LocalDateTime, target: Target!): BulkResult!
  deleteReservations(ids: [ID!]!): BulkResult!

  # ===== escape hatch — cross-type / compound / concurrency-checked delete =====
  applyChanges(operations: [ChangeOp!]!): BulkResult!
}
```

### 3. Update is full-state (not patch)

`UpdateReservationInput` mirrors the read-side `Reservation` shape — the
SPA submits what the reservation should look like after the edit, server
diffs against stored state to produce the storage operations.

Rationale:
- **Symmetry with read.** Same shape both directions; reduces mental load.
- **Matches rapla's internal model.** `operator.dispatch(UpdateEvent)` takes
  full `Entity` objects. Patch would be a new abstraction layer.
- **SPA editor flow is naturally full-state.** Reactive Forms / Apollo cache
  hold the full entity; clicking Save sends what's in the form.

Forward-compatibility caveat: a stale SPA submitting full state may omit
attributes the admin added to a DynamicType after the SPA build. **Mitigation:**
server treats "attribute not in input list" as unchanged (not as cleared);
descriptor query at edit-open keeps the SPA aligned with current attribute set
(PRD 035 §4).

### 4. Delete is uniform — only `deleteReservations(ids: [ID!]!)`

No `deleteReservation(id)` single mutation. Delete's input is uniform regardless
of cardinality (just ids; no state); single + bulk collapse cleanly. For N=1,
caller invokes `deleteReservations(ids: ["r1"])` and reads `result.results[0].deletedId`.

`expectedLastChanged` dropped from the top-level delete — per-id concurrency
checks are impractical for bulk; deletes are usually unconditional. Concurrency-
checked delete remains available via `applyChanges` where `ChangeOp.deleteReservation`
retains the `expectedLastChanged` field.

### 5. ATOMIC-only in v1; PARTIAL deferred

All mutations succeed-or-reject atomically. No `mode: BulkMode` parameter in v1.

Forward-compatibility: adding PARTIAL later is purely additive:
- Add `BulkMode` enum with `ATOMIC` + `PARTIAL`
- Add `mode: BulkMode = ATOMIC` arg (default preserves current behavior)
- Add `PARTIAL_SUCCESS` to `BulkStatus` enum (extension is non-breaking)

Existing v1 callers don't break.

### 6. `applyChanges` — typed ChangeOp with exactly-one invariant

```graphql
mutation { applyChanges(operations: [ChangeOp!]!): BulkResult! }

input ChangeOp {
  # Exactly one field set per ChangeOp. Validated at the mutation boundary
  # before any storage I/O. Grows as we ship more entity types (PRD 057/058).
  createReservation:  CreateReservationInput
  updateReservation:  UpdateReservationInput
  deleteReservation:  DeleteInput
}

input DeleteInput {
  id:                  ID!
  expectedLastChanged: LocalDateTime    # opt-in concurrency check
}
```

Maps 1:1 to rapla's `operator.dispatch(UpdateEvent)` — one storage transaction
covering all operations.

**Same-batch entity references** via client-generated UUIDs (the pattern rapla's
storage already uses internally). Operation N can reference entities created in
operations 0..N-1. Forward references rejected at validation time.

**Exactly-one-set validation:**

```java
for (int i = 0; i < operations.size(); i++) {
  ChangeOp op = operations.get(i);
  int setFields = countNonNull(op.createReservation(), op.updateReservation(),
                                op.deleteReservation() /* ... grow per PRD */);
  if (setFields != 1) {
    throw new ValidationException(
        "operations[" + i + "] must have exactly one operation set; got " + setFields);
  }
}
```

Fails fast with a clear error. Each new ChangeOp field added = one line in this
validator + a test case. Loud failure mode (rejection on first request that tests
the new shape).

### 7. `expectedLastChanged` on update only

Optimistic concurrency via `LocalDateTime` (rapla's storage already uses
lastChanged timestamps; no new version-counter mechanism).

Not on:
- **Create** — no prior state to check
- **Bulk verbs** (changeOwner, move, copy, deleteReservations) — per-id parallel
  arrays would be impractical
- **Delete via top-level** — accepted race
- **Delete via applyChanges** — retained as opt-in via `DeleteInput.expectedLastChanged`

On mismatch: server returns `ValidationError { code: "CONCURRENT_MODIFICATION", ... }`
with the current `lastChanged` value in the extension so SPA can offer
merge / discard UX.

### 8. `BulkResult` + `ChangeResult` shape

```graphql
type BulkResult {
  overallStatus: BulkStatus!
  results:       [ChangeResult!]!
}

enum BulkStatus {
  SUCCESS
  REJECTED
  # PARTIAL_SUCCESS — added when PARTIAL mode ships
}

type ChangeResult {
  index:        Int!
  reservation:  Reservation             # for create/update of Reservation
  allocatable:  Allocatable             # future (PRD 057)
  user:         User                    # future (users-write PRD, TBD)
  deletedKind:  EntityKind              # for delete
  deletedId:    ID
  errors:       [ValidationError!]!
}

type ValidationError {
  path:    String!     # GraphQL path: operations[3].createReservation.appointments[0].repeating.exceptions[2]
  code:    String!     # canonical taxonomy — see §"Error code taxonomy"
  message: String!
  # extensions populated per code — e.g. CONCURRENT_MODIFICATION carries currentLastChanged
}
```

Single result list indexed to `operations[]`; per-op result populated based on
operation kind. Heterogeneous "kitchen sink" type — paid as the cost for the
typed-batch model.

### 9. Client-supplied ids — contract rule + operator-side integrity guard (2026-07-06)

Locked in the PRD 091 design dialog (id-first vs. server-assigned; the SPA edit
surface is the first consumer that needs subset restrictions at create time).

**Contract rule (REVISED 2026-07-06 — client ids are MANDATORY):**
- `input.id` on reservations, appointments, and allocatables is **required**;
  id absent or blank → `ValidationError` code `REQUIRED` naming the path. The
  server-generate fallback is removed from `createReservation`,
  `applyChanges`-create, and `createAllocatable` (PRD 063).
- This **supersedes the earlier "B′" conditional rule** ("ids only required when
  `AllocationInput.appointmentIds` references a subset") — with ids always
  present the conditional collapses; one contract instead of two behavior
  classes.
- Ids are stored **verbatim** — no server-side normalization, no type-prefix
  rewrite (approach W, see `docs/architecture/domain-model.md` § "Id format and
  assignment"). The only gate is the minimal syntax rule
  (`Tools.isValidEntityId`: alphanumerics + hyphen, 8–64 chars); a plain
  `crypto.randomUUID()` always passes.
- Rationale (recorded in domain-model.md): per the revised OQ5 there is no
  content comparison, so retry-idempotency exists **only** via the client-minted
  id (collision on own id = "already applied"). An id-less create is
  structurally non-idempotent; every mitigation (idempotency-key store,
  tempId mapping, response correlation) re-invents a client-generated token
  with extra server infrastructure. Precedent: iCalendar/CalDAV require a
  client-generated `UID` (RFC 5545, RFC 7986) with `PUT If-None-Match: *` →
  `412`; Google Calendar's optional client id exists exactly for retry
  idempotency; MS Graph had to retrofit `transactionId` (a client-minted
  random token) to fix duplicate-on-retry. Rapla is semantically an iCal
  system — we follow the CalDAV model with `ID_COLLISION` instead of `412`.
- **Server-initiated creates keep server-generated ids**: `copyReservations`
  clones server-side and mints fresh reservation *and appointment* ids
  (`clone()` keeps appointment ids — the copy must re-id them or
  `checkIdIntegrity` #2 rejects the store; restrictions are rewritten against
  the new ids).

**Operator-side integrity guard `checkIdIntegrity`** — a named step in
`LocalAbstractCachableOperator.check()`, sibling of PRD 058's
`checkGraphqlKeySpecCompliance` (same belt-and-suspenders rationale: guards at the
dispatch choke point cover *every* write path — GraphQL, Swing dispatch, plugin
imports, future PRD 067 `EntityLifecycle` — and can't be silently bypassed):
1. A **new** entity whose id already resolves to a persistent entity → reject.
   (Today `checkVersions` only catches the *stale* direction — a fresh entity with
   a current timestamp sails through and create-with-existing-id becomes a silent
   overwrite.)
2. An **appointment id that already lives in a different reservation** → reject.
   (Today unchecked anywhere; two appointments sharing a `ReferenceInfo` corrupt
   the conflict engine, the appointment/block index and restrictions, and violate
   the composite-sub-entity invariant `checkConsistency` implicitly assumes.)

**Status 2026-07-06 (b).** Id **syntax validation** is implemented as part of
`checkIdIntegrity`: NEW Reservation / Appointment / Allocatable entities (id does
not resolve to a persistent entity) must satisfy `Tools.isValidEntityId` — ASCII
alphanumerics + hyphen, alphanumeric first char, length 8–64. Existing store ids
are grandfathered (e.g. legacy `period_1` allocatables keep saving). Deliberately
not a UUID-structure check (legacy `r…`/`u…` prefixes aren't valid UUID hex; the
guard is about store safety — no `;` because of conflict composite ids, no
whitespace/XML/URL-escaping issues, length fits composite ids in VARCHAR(255)).
Pinned by 4 tier-1 `ToolsTest` cases + 5 tier-2 `AppointmentIdIntegrityTest`
cases (red-green verified).

**Status 2026-07-06.** Check #2 (appointment ownership) is **implemented**:
`checkIdIntegrity(storeObjects)` is a named step in
`LocalAbstractCachableOperator.check()` (right after `checkGraphqlKeySpecCompliance`).
For every incoming reservation it resolves each appointment id against the
persistent cache (`findPersistent`) and rejects with a §12-uniform `RaplaException`
(message names only the client's own appointment id) if the id already belongs to a
*different* reservation. Pinned by `AppointmentIdIntegrityTest` (tier 2, red-green:
foreign-reservation appointment id rejected; own appointment id re-stored across an
update passes).

**Status 2026-07-06 (c).** Check #1 (new-entity id collision) is **implemented**
via the two-carrier create-intent design below. Guard: for every ref in
`evt.getCreateReferences()`, `cache.tryResolve(id, type) != null` →
`EntityIdCollisionException` (extends `RaplaException`; message names only the
client's own id). `MutationExceptionResolver` maps it to GraphQL code
`ID_COLLISION`. Pinned by `NewEntityIdCollisionTest` (tier 2, red-green: new
reservation/allocatable reusing a persistent id rejected; edit clone keeps
upsert semantics — fail-open; fresh new entity stores) and two tier-3
retry-contract tests (`createReservationWithExistingIdReturnsIdCollision`,
`createWithExistingIdReturnsIdCollision`: create → identical retry →
`ID_COLLISION`, no silent overwrite).

§12: both rejections must be **uniform** — identical error shape whether the
colliding entity is readable or unreadable; the create path must not become an
existence probe. Per the revised OQ5 (2026-07-06) there is **no content
comparison**: a create whose id already resolves → `ID_COLLISION`, and the client
(owning its id space) maps a collision on its own id to "already applied". So
`checkIdIntegrity` #1 is the single mechanism — no controller-side pre-dispatch
comparison — and it covers every write path uniformly.

**Create-intent design for check #1 (locked + implemented 2026-07-06).**
The operator can't distinguish create from update today: both deliver a
full-state entity with an id, and no intent-free signal exists in the wire
(`UpdateEvent` has no create/update flag; `checkVersions` only catches the stale
direction). We control *every* write path, so we thread create-intent structurally
through two carriers rather than heuristically:

- **`transient boolean isNew` on `SimpleEntity`** — the in-memory carrier, sibling
  of the existing `transient readOnly`. Set `true` in exactly one place,
  `FacadeImpl.setNew(...)` (the sole funnel for every `newReservation` /
  `newAllocatable` / `copy` / `clone`). Reset to `false` on the `editObject` clone
  path (`AbstractCachableOperator.editObject`) and defaults `false` for
  deserialized / storage-loaded entities. Because it's `transient` it does **not**
  cross the Swing remote-dispatch wire — that's fine, it only has to survive
  in-JVM until the event is built.
- **`Set<ReferenceInfo> createReferences` on `UpdateEvent`** — the serialized
  carrier. `AbstractCachableOperator.createUpdateEvent` (the single funnel where
  the client builds the event *before* `serv.dispatch`) reads `entity.isNew()`
  and records the ref here. From this point the set is the truth; it survives
  serialization to the server. The GraphQL controllers build their `UpdateEvent`
  in-JVM and populate `createReferences` directly on create verbs.

`checkIdIntegrity` check #1 then becomes trivial and §12-uniform: for each ref in
`evt.createReferences`, `findPersistent(ref) != null → reject` with `ID_COLLISION`.
Per the revised OQ5 (2026-07-06) there is **no content comparison** anywhere — not
in the operator and not controller-side; the client maps a collision on its own id
to "already applied".

Locked sub-decisions:
- **Two carriers, both kept** (transient `isNew` *and* `createReferences`) so the
  Swing caller stays dumb — it only calls `newReservation` — while intent still
  reaches the server serialization-robustly.
- **fail-open default:** an entity reaching dispatch *without* a create marker
  keeps today's upsert-by-id semantics (id absent → insert, id present → update).
  Only entities that explicitly declare create-intent are guarded. This is the
  only sane intent-free default; undeclared writes are unchanged.
- **scope:** the relevant target set is **Reservation + Allocatable** — the two
  data entities with id-first GraphQL create surfaces (PRD 056 / PRD 063), i.e. the
  only entities where a client can supply an id that could collide. The operator
  guard itself runs generically over `createReferences` (typ-agnostic), but is
  **inert** for server-id-assigned entities: Categories / DynamicTypes are
  identified by key (PRD 058) not client ids, and facade-created entities always
  carry a fresh `createIdentifier` UUID that can never resolve to a persistent one.
  Sub-entity (appointment) id integrity stays check #2's job.
- `SimpleEntity.clone()` must reset `isNew` the same way it resets `readOnly`, so
  an edit clone never leaks `isNew=true`.

## Verb-level semantic notes

### `createReservation(input)`

§12: caller must `canCreate(DynamicType, user)` for the reservation type, AND
`canRead(allocatable, user)` for every referenced allocatable. Unknown / unreadable
references fail identically (§12 existence rule).

`input.id` and every appointment's `id` are **required** (§9 revised contract
rule, 2026-07-06) — absent/blank → `REQUIRED` error naming the path. Supplied ids
must be unique against storage (collision = `ID_COLLISION`) and must pass the
minimal syntax rule; supplied appointment ids must not live in another
reservation — see §9 (`checkIdIntegrity` operator guard). **Status 2026-07-06:
id-required enforcement is implemented** in `ReservationMutationController`
(create verb + `applyChanges`-create + `buildAppointment`) and
`AllocatableMutationController`, pinned by tier-3 REQUIRED tests. The
*new-entity id collision* case (§9 check #1) remains spec'd-not-implemented
pending the create-intent carriers.

### `updateReservation(id, input, expectedLastChanged)`

§12: `canModify(reservation, user)`. Cross-reference reads: every newly-referenced
allocatable in input must satisfy `canRead(allocatable, user)`.

If `expectedLastChanged` mismatches stored value → `CONCURRENT_MODIFICATION` error
with current `lastChanged` in extension.

Returns the updated `Reservation` (post-state).

### `changeReservationOwner(ids, newOwnerId)`

§12: caller must `canModify(reservation, user)` for **every** id in the batch.
Failures cause whole-batch rejection per ATOMIC mode.

`newOwnerId` must resolve to a real, visible user. Unknown ownerId fails as
`REFERENCE_NOT_FOUND`.

### `moveReservations(ids, dateShift)` — ⚠ SUPERSEDED by PRD 101 (2026-07-09)

> The shipped verb is `moveReservations(ids, reference: LocalDateTime, target:
> Target!)` — the `dateShift: Duration` form and the `Duration` scalar were
> **removed** (couldn't express keep-time; the delta is now `target − reference`).
> Exceptions stay **absolute** on move (PRD 101 D2 — not shifted, contrary to the
> note below). Full semantics + the appointment-addressed verbs
> (`moveAppointment`, `splitOccurrence`) live in
> [PRD 101](101-transpose-anchors-move-copy-paste.md). Do not implement from this
> section; kept for the error-taxonomy history only.

Shifts the **start** of each reservation's appointments by `dateShift` (ISO-8601
Duration: `"P7D"`, `"PT-30M"`, etc.). Recurrence rules preserved structurally —
WEEKLY stays WEEKLY, just N days offset. Exceptions shift too.

§12: `canModify` per reservation. If a shift pushes the recurrence rule's `end`
date before its `start` → `INVALID_SHIFT` error per affected reservation;
ATOMIC rejects the batch.

### `moveAppointment(reservationId, appointmentId, occurrenceStart, dateShift, scope, …)` — added 2026-07-09

> **⚠ SUPERSEDED same day by [PRD 101](101-transpose-anchors-move-copy-paste.md).**
> The design dialog continued past this sketch: the scope enum, the `Duration` delta,
> `keepTime`, and `reservationId` were all revised (typed `Anchor` input, scope-split
> verbs, no-rebase exception doctrine). This section is kept only as the decision trail;
> the current design state lives in PRD 101, and the final verbs will be rewritten here
> in PRD 101 Phase 1. Do not implement from this section.

**Post-v1 additive verb.** The scope-aware, single-reservation counterpart of
`moveReservations`: it moves/resizes **one appointment** of a reservation with an
explicit EVENT/SERIE/SINGLE scope, keeping the recurrence cascade server-side. It
exists because the calendar drag/resize surface (PRD 094 Phase 4) must offer the
same EVENT / SERIE / SINGLE choice Swing's `AppointmentResize.change()` does, and
that split logic must **not** be reimplemented client-side (PRD 094 **D5**; grounded
in `docs/architecture/reservation-edit.md` § "Drag / resize on the calendar").

```graphql
enum AppointmentEditScope {
  EVENT    # move every appointment of the reservation (== moveReservations for one id)
  SERIE    # move the whole repeating appointment (all occurrences), structure preserved
  SINGLE   # move only the grabbed occurrence — split off + add an exception
}

extend type Mutation {
  moveAppointment(
    reservationId:       ID!
    appointmentId:       ID!
    occurrenceStart:     LocalDateTime!     # which occurrence was grabbed — identifies the block for SINGLE
    dateShift:           Duration!          # ISO-8601; applied to the start (and end unless keepTime)
    scope:               AppointmentEditScope!
    keepTime:            Boolean = false    # true = shift the day only, preserve time-of-day (Swing keepTime)
    newEnd:              LocalDateTime      # resize form — set the occurrence end instead of shifting start
    expectedLastChanged: LocalDateTime      # optimistic concurrency, as on updateReservation
  ): Reservation!
}
```

Per-scope semantics (1:1 with `AppointmentResize.change()`):
- **EVENT** — shift the start of *every* appointment of the reservation by
  `dateShift`. Equivalent to `moveReservations(ids: [reservationId], dateShift)`;
  offered here so the scope dialog has a single entry point.
- **SERIE** — shift the whole repeating appointment identified by `appointmentId`
  (every occurrence moves together; the repeating rule is preserved structurally,
  its exceptions shift with it). This is the case `moveReservations` cannot express
  — it can't target one appointment of a multi-appointment reservation.
- **SINGLE** — **split**: clone the grabbed occurrence as a *new non-repeating*
  appointment at `occurrenceStart + dateShift` (carrying that appointment's
  per-appointment allocation restrictions) and add a **day-truncated exception**
  for `occurrenceStart` to the original series. If the series becomes empty
  (`isNotEmptyWithExceptions` false) the whole-appointment / whole-event cascade
  from the Delete section applies.

`keepTime` and `newEnd` are mutually the move-vs-resize forms of the one Swing
`showDialog(block, "move", …)` path (resize passes a new end; move passes a shift);
supplying `newEnd` makes the mutation a resize of the addressed occurrence under the
same scope rules.

Returns the updated `Reservation` (post-state, fresh `lastChanged`) so the PRD 094
command layer can capture the inverse. §12: `canModify(reservation, user)`. Errors:
`INVALID_SHIFT` (recurrence end < start after the move), `CONCURRENT_MODIFICATION`
(`expectedLastChanged` mismatch), `PERMISSION_DENIED`. Note the SINGLE inverse is
**not** self-inverting (a split can't be undone by a negated split) — the PRD 094
command captures pre-split state and inverts via `updateReservation` (see PRD 094
Phase 4).

**Relationship to `moveReservations`:** `moveReservations` stays the bulk EVENT
verb (shift many reservations wholesale, no scope choice); `moveAppointment` is the
single-reservation, scope-aware verb the interactive calendar drag needs. Neither
subsumes the other.

#### Example calls

Running scenario: reservation `res-3f2a9c11` ("Lineare Algebra") has one **weekly
repeating** appointment `app-8b7d0e42`, Mondays 10:00–12:00, and a second one-off
appointment `app-1c4f77a0` (the exam). The user grabbed the occurrence on Monday
**2026-07-13** in the week grid.

**1 — EVENT** (drag the block, choose *"Ganze Veranstaltung"*): shift **every**
appointment of the reservation by +1 day. Both `app-8b7d0e42` (all its Mondays →
Tuesdays) and `app-1c4f77a0` (the exam) move.

```graphql
mutation MoveWholeEvent {
  moveAppointment(
    reservationId:   "res-3f2a9c11"
    appointmentId:   "app-8b7d0e42"
    occurrenceStart: "2026-07-13T10:00:00"
    dateShift:       "P1D"
    scope:           EVENT
  ) { id lastChanged }
}
```

**2 — SERIE** (choose *"Serie"*): shift only appointment `app-8b7d0e42` — the whole
weekly series moves an hour earlier; the exam `app-1c4f77a0` is untouched. The
repeating rule stays WEEKLY; existing exceptions shift with it.

```graphql
mutation MoveWholeSeries {
  moveAppointment(
    reservationId:   "res-3f2a9c11"
    appointmentId:   "app-8b7d0e42"
    occurrenceStart: "2026-07-13T10:00:00"
    dateShift:       "PT-1H"            # every Monday is now 09:00–11:00
    scope:           SERIE
  ) { id lastChanged }
}
```

**3 — SINGLE** (choose *"nur dieser Termin"*): **split** — the Monday 2026-07-13
occurrence moves to Tuesday; the series keeps all *other* Mondays and gains a
`2026-07-13` exception, and a new **non-repeating** appointment is created at the
new time (carrying that appointment's per-appointment restrictions).

```graphql
mutation MoveOneOccurrence {
  moveAppointment(
    reservationId:   "res-3f2a9c11"
    appointmentId:   "app-8b7d0e42"
    occurrenceStart: "2026-07-13T10:00:00"   # this Monday only
    dateShift:       "P1D"                    # -> Tuesday 2026-07-14 10:00–12:00
    scope:           SINGLE
  ) {
    id
    appointments {
      id start end
      repeating { type exceptions }          # original series now excludes 2026-07-13
    }
  }
}
```

**4 — day-only move, keep time** (drop onto a different day column; `keepTime`
preserves the time-of-day so a DST-crossing or day-granular drop never drifts the
clock): move the whole event two days forward, keeping 10:00–12:00.

```graphql
mutation MoveKeepingTime {
  moveAppointment(
    reservationId:   "res-3f2a9c11"
    appointmentId:   "app-8b7d0e42"
    occurrenceStart: "2026-07-13T10:00:00"
    dateShift:       "P2D"
    scope:           EVENT
    keepTime:        true
  ) { id }
}
```

**5 — resize** (drag the block's bottom edge): extend the grabbed occurrence's end
by 30 min. `dateShift` leaves the start put; `newEnd` sets the new end, and the
+30 min delta is applied per `scope` — here `SERIE`, so every Monday becomes
10:00–12:30.

```graphql
mutation ResizeSeriesEnd {
  moveAppointment(
    reservationId:   "res-3f2a9c11"
    appointmentId:   "app-8b7d0e42"
    occurrenceStart: "2026-07-13T10:00:00"
    dateShift:       "PT0S"                  # start unchanged
    newEnd:          "2026-07-13T12:30:00"
    scope:           SERIE
  ) { id }
}
```

**6 — concurrency-checked move** (the PRD 094 command inverse captures
`expectedLastChanged` so a foreign edit between the move and its undo fails loudly
instead of silently overwriting):

```graphql
mutation MoveWithVersionCheck {
  moveAppointment(
    reservationId:       "res-3f2a9c11"
    appointmentId:       "app-8b7d0e42"
    occurrenceStart:     "2026-07-13T10:00:00"
    dateShift:           "P1D"
    scope:               EVENT
    expectedLastChanged: "2026-07-09T14:22:31"
  ) { id lastChanged }
}
```

On a stale `expectedLastChanged` the server returns
`ValidationError { code: "CONCURRENT_MODIFICATION", extensions: { currentLastChanged } }`
(§7 / error taxonomy), never a silent overwrite.

**Undo note.** EVENT / SERIE / keepTime / resize moves invert by re-issuing
`moveAppointment` with the negated `dateShift` (or the prior `newEnd`) and the same
scope. A **SINGLE** move is *not* self-inverting (a split can't be undone by a
negated split) — the PRD 094 command captures the pre-split reservation state and
inverts via `updateReservation` (see PRD 094 Phase 4).

### `copyReservations(ids, dateShift)` — ⚠ SUPERSEDED by PRD 101 (2026-07-09)

> Shipped as `copyReservations(ids, reference: LocalDateTime, target: Target!)`
> (`dateShift`/`Duration` removed, as for `moveReservations`). Copy keeps
> exceptions **absolute** but re-bases a non-fixed `until` length-preserving
> (PRD 101 D2/D3). See [PRD 101](101-transpose-anchors-move-copy-paste.md); the
> id-minting/permission prose below is still accurate.

Duplicates with new server-generated UUIDs — reservation **and** appointments
(`clone()` keeps appointment ids, so the copy re-ids every appointment and
rewrites restrictions against the new ids; without this, `checkIdIntegrity` #2
rejects the store — fixed + pinned 2026-07-06,
`copyReservationsMintsFreshAppointmentIds`). This is the one create surface where
the *server* mints ids: the client sends no entity input, so there is nothing to
be idempotent against. `dateShift` is required — copying without shift creates
guaranteed conflicts at the original time. Copies preserve classification,
appointments, allocations. Permissions reset (caller becomes owner; admin grants
explicit access via subsequent `applyChanges`).

§12: caller must `canRead(reservation, user)` for sources (to copy them) AND
`canCreate(DynamicType, user)` for the type (to create the copies).

### `deleteReservations(ids)`

Simple bulk delete. No concurrency check. §12: `canModify` per reservation.

### `applyChanges(operations)`

Per the §6 design above. One UpdateEvent per call. §12 gates run per-operation
based on its kind.

## Error code taxonomy (initial draft — refine per consumer need)

| Code | Meaning | Extension fields |
|---|---|---|
| `REQUIRED` | required input field missing | — |
| `INVALID_VALUE` | input value out of range / wrong format | — |
| `INVALID_SHIFT` | dateShift produces invalid state (e.g. recurrence end < start) | — |
| `REFERENCE_NOT_FOUND` | referenced id (allocatable, user, etc.) doesn't exist / unreadable | — |
| `REFERENCE_EXISTS` | delete blocked by existing referrers | `referrers: [ID!]` (capped at 50) |
| `PERMISSION_DENIED` | caller lacks required permission | — |
| `CONCURRENT_MODIFICATION` | `expectedLastChanged` mismatch | `currentLastChanged: LocalDateTime` |
| `ID_COLLISION` | client-supplied UUID matches existing entity | — |
| `CONFLICT` | scheduling conflict detected | `conflictsWith: [ID!]` |
| `OP_INVARIANT` | ChangeOp violated exactly-one-set rule | — |
| `STORAGE_ERROR` | unrecoverable storage failure (retry-after) | — |

Codes are stable strings (not enums) so future additions don't break clients.

## §12 invariants on writes

1. **Caller must be authenticated.** Anonymous → all mutations reject with
   `PERMISSION_DENIED`.
2. **Create requires `canCreate(DynamicType, user)`** for the entity's type.
3. **Update / delete requires `canModify(entity, user)`** per entity.
4. **Every referenced entity must be readable.** Cross-reference reads from
   classification / allocations / owner — `canRead(referenced, user)` for every
   resolved reference. Unknown id and unreadable id fail identically.
5. **Existence not leaked.** A `REFERENCE_NOT_FOUND` from id="r999" is identical
   whether r999 doesn't exist or the caller can't read it.

## Plan

Once input shapes settle (next discussion), implementation steps:

1. **Schema additions** in `schema.graphqls`:
   - Static: `CreateReservationInput`, `UpdateReservationInput`,
     `DeleteInput`, `AppointmentInput`, `RepeatingRuleInput`, `AllocationInput`
   - Static: `ChangeOp @oneOf`, `BulkResult`, `ChangeResult`, `BulkStatus`,
     `EntityKind`, `ValidationError`
   - 6 mutation roots
2. **SDL generator extension** (per symmetric β² — OQ2 resolved):
   - Per-DynamicType: emit `<TypeKey>ClassificationInput` mirroring the
     existing `<TypeKey>Classification` output, with allocatable / category
     references typed as `ID`
   - Per classification-kind: emit `AllocatableClassificationInput @oneOf`
     and `ReservationClassificationInput @oneOf` with one variant per
     contributing DynamicType
   - Hot-swap regenerates these alongside the read-side types
3. **`ReservationMutationController`** in
   `rapla-app/src/main/java/org/rapla/server/spring/graphql/`:
   - `@MutationMapping` for each verb
   - Goes through `operator.dispatch(UpdateEvent)` for all writes
   - §12 inline gates per verb
   - Builds a single UpdateEvent per call
4. **`ApplyChangesController`** (or merge into ReservationMutationController):
   - ChangeOp exactly-one validation up-front
   - Same-batch reference resolution
   - Single UpdateEvent dispatch
5. **`ValidationError` mapper** — translates rapla's storage exceptions
   (RaplaException subtypes, dependency check failures) to the typed error
   codes
6. **`checkIdIntegrity` operator guard** (locked decision §9) in
   `LocalAbstractCachableOperator.check()` — foreign-reservation appointment id
   (check #2) ✅ **done** (`AppointmentIdIntegrityTest`); new-entity id collision
   (check #1) ⏳ pending on the create-intent design. Still to do: §12-uniform
   new-entity rejection, plus the controller-side
   subset-restrictions-require-appointment-ids validation and id normalization.
   Regression tests still to add: foreign reservation id (once check #1 lands).
7. **Tier-3 tests** — MockMvc + HttpGraphQlTester:
   - §12 leak tests (anonymous → rejected; non-admin → can only modify owned)
   - Permission gates per verb
   - Concurrency mismatch on update
   - ATOMIC rejection on batch with one failing op
   - Same-batch refs resolve correctly
   - Forward refs in applyChanges fail validation
   - ExactlyOneSet validation on ChangeOp
   - Error code stability (test by code, not by message)
   - Restriction round-trip: split-case (10 appointments, mixed lecturers)
     create + read returns same shape

## Tests (tier-3 spec)

Per AGENTS.md §10 / §12 / §13:
- `@SpringBootTest` + `@AutoConfigureMockMvc(addFilters = false)` + `@WithMockUser`
- Per AGENTS.md §13: no `mock(...)` of internal rapla types; use real
  `FacadeTestSupport` infrastructure
- Per AGENTS.md §12: every mutation MUST have a leak-test variant (non-admin
  caller, mixed-permission inputs, assert response identical to "only-visible-subset"
  case)

## Open questions

### OQ1 — Input shape detail: `CreateReservationInput` / `UpdateReservationInput`

**OQ1.a — `ownerId` on create AND update — RESOLVED 2026-05-28: removed
from both.** Server sets owner to caller on create; owner changes after
that go exclusively through `changeReservationOwner(ids, newOwnerId)`.

Rationale:
- Symmetric input shapes (create + update both lack ownerId)
- Eliminates the "null = unchanged vs cleared" semantic question
- Audit clarity: the user who creates the reservation IS the owner;
  subsequent transfers are explicit `changeReservationOwner` events in
  the storage log
- Removes the "create on behalf of X" impersonation path; if admin needs
  to create-then-transfer, that's two ops (or one applyChanges batch)
- Consistent with the typed-verb pattern: owner is a high-intent operation
  with its own gate, separate from content editing

**OQ1.d — empty `appointments[]` — RESOLVED 2026-05-28: reject.** Rapla
domain rule: reservations must have ≥1 appointment. Server validates
non-empty; rejects with `REQUIRED` at path `operations[i].createReservation.appointments`
(or .updateReservation). GraphQL doesn't support list-min-length at the
schema layer, so the check is server-side.

**OQ1.c — typeId change on update — REVISED 2026-07-07 (PRD 096): accept.**
Original 2026-05-28 resolution was reject-with-`INVALID_TYPE_CHANGE` plus a
future dedicated `reshapeReservation` mutation, because type changes need a
"which attributes get dropped" preview UX. That preview now lives client-side
in the PRD 096 classification editor (draft-based sheet: user switches the
type, sees the remapped attributes live, saves once — the type change stays
undoable in the draft, Swing parity). `updateReservation` therefore accepts
a `typeKey` differing from stored:
- The classification `@oneOf` variant must match the NEW typeKey
  (else `MISMATCHED_TYPE`)
- The caller passes the same create-gate as `createReservation` on the
  target type (`requireCanCreate` → `PERMISSION_DENIED`)
- Attribute remapping is the client's job — the supplied classification is
  stored as-is (no server-side `newClassificationFrom` remap)
- Applies to both the direct mutation and the `applyChanges` batch op

No separate `reshapeReservation` mutation. Tests:
`ReservationMutationControllerTest.updateReservationChangesType` /
`updateReservationTypeChangeWithMismatchedVariantRejected`.

### OQ2 — Classification input shape — RESOLVED 2026-05-28 (symmetric β²)

**Decision: typed per-DynamicType, mirroring the read side.** The generic
`ClassificationInput` + `AttributeValueInput` + `AttributeValuePayload`
envelope sketched earlier is **dropped**.

The SDL generator emits one input type per DynamicType (parallel to the
read-side `<TypeKey>Classification` output) and a `@oneOf` polymorphic
dispatch type per classification-kind:

```graphql
input AllocatableClassificationInput @oneOf {
  raum:           RaumClassificationInput
  gebaeude:       GebaeudeClassificationInput
  person:         PersonClassificationInput
  # ... grows per DynamicType — hot-swapped with reads
}

input ReservationClassificationInput @oneOf {
  lehrveranstaltung:     LehrveranstaltungClassificationInput
  pruefung:              PruefungClassificationInput
  # ...
}

input RaumClassificationInput {        # generated per DynamicType
  Raumname:                 String
  Grundflaeche:             Int
  AnzahlPlaetzeInsgesamt:   Int
  RollstuhlgerechterZugang: Boolean
  Gebaeude:                 ID                     # ALLOCATABLE → ID
  Raumart:                  Raumtyp                # CATEGORY VALUE_LIST → enum
  AusstattungListe:         [Ausstattung!]         # CATEGORY VALUE_LIST + LIST
}
```

`CreateReservationInput` / `CreateAllocatableInput` now carry the typed
classification dispatch directly:

```graphql
input CreateReservationInput {
  id:             ID
  typeId:         String!                                  # discriminator
  classification: ReservationClassificationInput!          # @oneOf — variant must match typeId
  appointments:   [AppointmentInput!]!
  allocations:    [AllocationInput!]!
  ownerId:        ID
}
```

Rationale:
- **One mental model.** Same one-schema-two-modes consumption as reads
  (PRD 035 §580): SPA via dynamic mutation construction; codegen consumers
  via typed access.
- **Smaller wire** — typed fields don't need the `{key, value}` envelope.
- **Parse-time validation** — graphql-java rejects wrong types at the
  boundary instead of in resolver code.
- **`@oneOf`** enforces exactly-one-variant; server cross-validates
  variant set matches `typeId` field.

Generator emission rules:
- `typeId` / `type` interface fields are read-only — not emitted on input
- ALLOCATABLE attrs typed as `ID` (writes use references; reads use stubs)
- CATEGORY ORGANIZATION attrs typed as `ID`
- CATEGORY VALUE_LIST attrs typed as the same generated enum used on read
- Multi-valued (LIST / BELONGS_TO / PACKAGE) emit as `[X!]`
- All fields nullable — required semantics enforced server-side per
  `AttributeInput.required` from PRD 057

Hot-swap surface doubles (read + write types regenerated per DynamicType
change). Same trigger / poll / atomic-swap mechanism; SDL generator just
emits more SDL per rebuild.

### OQ3 — `AllocationInput.appointmentIds` — null = "all" vs explicit list

Mirror the read-side `Allocation.appointmentIds`. **Lean: yes, mirror.**

### OQ4 — `AppointmentInput.repeating` shape

`RepeatingRuleInput` mirror of read-side `RepeatingRule`. **Lean: mirror.**

### OQ5 — Idempotency on retry with same UUID — RESOLVED 2026-05-28, REVISED 2026-07-06

**Revised rule (2026-07-06): no content comparison. A create whose id already
resolves to a persistent entity → `ID_COLLISION`, always.** The client owns its
id space (it mints a fresh UUID per logical create), so a collision on an id it
generated for *this* create can only be its own earlier attempt — the client maps
`ID_COLLISION` on a self-generated id to "already applied → success". This *is*
the idempotency protocol: the server enforces uniqueness, the client interprets
the conflict.

Why the content comparison was dropped: "same content" on a reservation
(appointments, repeatings, allocations, restrictions, classification values) is
fiddly and fragile — too strict (a differing `lastChanged`/timestamp) turns a
legitimate retry into a false `ID_COLLISION`; too loose waves a real conflict
through. It also forced deep-equals into the wrong layer (§9 keeps the operator
free of content comparison). The consumers are ours (SPA mints the id for
optimistic UI; MCP tool-wrappers own their id) and map collision→success trivially,
so the server-side "same content → silent no-op" ergonomics don't earn their cost.

**Consequence for the layering:** the controller no longer needs a pre-dispatch
`tryResolve` + comparison. `checkIdIntegrity` #1 (operator, create-intent ref with
`findPersistent != null → reject`) becomes the single mechanism, and covers every
write path uniformly. §12: the rejection is uniform (identical error shape
regardless of the colliding entity's readability) — the id the client already
holds is all the response reveals.

What we give up (accepted): the server can no longer *distinguish* an honest retry
from an accidental id-reuse-for-different-content bug — both surface as
`ID_COLLISION`. That's a gross client error either way, and a loud collision is an
acceptable answer to it.

**Not in scope for PRD 056** (parked in [PRD 062 — API Robustness](062-graphql-api-robustness.md), renumbered from 058):
in-flight lock for concurrent retries arriving while the original is
still committing; idempotency TTL + cache eviction; rate limiting;
query complexity caps; distributed tracing. Rapla's current usage
doesn't earn these costs (a few dozen creates/day per deployment,
single-pod single-thread atomicity via `operator.dispatch`). They get
revived when scale or multi-tenant deployment changes the calculus.

### OQ6 — Reference integrity on delete — RESOLVED 2026-05-28

**Reservation delete (in scope for PRD 056):** non-issue. Reservations have
no inbound references — appointments are composite (deleted with parent);
allocations are outbound only. `deleteReservations(ids)` is a clean delete
with no `REFERENCE_EXISTS` path needed.

**Future PRD scope (allocatable / user / permission delete — PRD 057+):**

- **Reject with `REFERENCE_EXISTS`** when delete is blocked by referring
  entities. Error extension carries first 50 referring ids + total count
  (so caller can render "this would affect N reservations" without
  needing the full list):

  ```json
  { "code": "REFERENCE_EXISTS",
    "extensions": {
      "deletedEntityId":   "<id>",
      "deletedEntityKind": "ALLOCATABLE",
      "referrers":         ["r17", "r18", ...],
      "referrerKind":      "RESERVATION",
      "referrerCount":     7
    }
  }
  ```

- **`applyChanges` dependency awareness:** the dispatcher collects all
  ids being deleted in the same batch. When checking referrers for an
  allocatable delete, it excludes reservations also being deleted in the
  same batch. Real ergonomic win for admin "remove this room and the
  bookings it had" workflow. ~20 LOC in the dispatcher.

  ```graphql
  applyChanges(operations: [
    { deleteReservation: { id: "r1" } },     # books R1
    { deleteReservation: { id: "r2" } },     # books R1
    { deleteAllocatable: { id: "R1" } }      # now allowed — r1+r2 in same batch
  ])
  ```

Cascade-delete is explicitly NOT the default — silent data loss is bad.
Force-release-first as a separate verb is also rejected — adds API
surface for what the batch-aware applyChanges already handles.

### OQ7 — Bulk verb defaults

ATOMIC locked. PARTIAL deferred. No more open questions on mode.

## Decision log

- **2026-07-09 (later) — the `moveAppointment` sketch below is superseded by PRD 101**
  (transpose & anchors): the design dialog moved to a typed `Anchor` input
  (day/dateTime `@oneOf` replacing `keepTime`/`Duration`), scope-split verbs
  (`moveAppointment`/`splitOccurrence`, EVENT via `moveReservations`), and the
  no-rebase exception doctrine. PRD 101 holds the research findings + decisions; its
  Phase 1 rewrites this PRD's verb notes to the final shape.
- **2026-07-09 — `moveAppointment` verb designed (post-v1 additive; not yet
  implemented).** A scope-aware single-reservation move/resize
  (`AppointmentEditScope = EVENT | SERIE | SINGLE`) that keeps the recurrence
  cascade — whole-event shift / whole-series shift / SINGLE-occurrence split +
  `addException` — server-side, mirroring Swing `AppointmentResize.change()`. Driven
  by PRD 094 Phase 4 (calendar drag/resize): the SPA owns only the scope dialog +
  command inverse, the split logic lives in Java (PRD 094 D5, maintainer directive
  "the actual logic should happen in the mutation graphql"). Distinct from
  `moveReservations` (bulk, EVENT-only, no scope). Schema + semantics in the
  "Verb-level semantic notes" section. Implementation is test-first tier-3 per PRD
  094 Phase 4.

- **2026-07-06 — `checkIdIntegrity` check #1 landed (create-intent two-carrier).**
  As locked: transient `SimpleEntity.isNew` (set in the `FacadeImpl.setNew`
  funnel, cleared in `setReadOnly`, not copied by `deepClone`) + serialized
  `UpdateEvent.createSet` (populated from `isNew` in
  `AbstractCachableOperator.createUpdateEvent` — shared by the Swing
  `RemoteOperator` path — and directly via `event.addCreate(...)` in the
  GraphQL create verbs incl. `copyReservations` and `applyChanges`-create).
  Guard throws `EntityIdCollisionException` → GraphQL `ID_COLLISION`.
  Fail-open for undeclared writes. Red-green pinned tier-2
  (`NewEntityIdCollisionTest`) + tier-3 (retry returns `ID_COLLISION`).
- **2026-07-06 — client ids MANDATORY on GraphQL creates (B′ retired).**
  `createReservation` (reservation + every appointment), `applyChanges`-create,
  and `createAllocatable` now require `input.id` — absent/blank → `REQUIRED`;
  the server-generate fallback is removed. Supersedes the B′ conditional rule
  and the "server normalizes supplied ids" clause (ids are stored verbatim —
  approach W). Rationale + framework survey (CalDAV/RFC 5545 UID, Google
  optional-id, MS Graph transactionId, JMAP creation ids) recorded in
  docs/architecture/domain-model.md "Id format and assignment". Same change:
  `copyReservations` now mints fresh **appointment** ids on the server-side
  clone (kept source ids tripped `checkIdIntegrity` #2) and rewrites
  restrictions. Pinned by tier-3 tests (red-green):
  `createReservationWithoutIdRejected`, `createReservationAppointmentWithoutIdRejected`,
  `copyReservationsMintsFreshAppointmentIds`, `createWithoutIdRejected`.
- **2026-07-06 — id syntax validation landed (minimal rule, new entities only).**
  `Tools.isValidEntityId` (`[A-Za-z0-9][A-Za-z0-9-]{7,63}`) enforced in
  `checkIdIntegrity` for NEW Reservation / Appointment / Allocatable ids;
  persistent ids grandfathered. No UUID-structure check (would reject legacy
  `r…`/`u…` ids; uniqueness comes from the collision guard, not the format).
  Same day: server-generated prefix letters for User/Allocatable switched to
  hex-valid `b`/`f` (`CreateIdPrefixTest`) — see
  docs/architecture/domain-model.md "Id format and assignment".
- **2026-07-06 — OQ5 revised: no content comparison.** A create whose id already
  resolves → `ID_COLLISION`, always. Dropped the "same content → no-op success /
  differing → collision" rule: deep-equals over reservation content is fiddly and
  fragile (timestamp noise → false collisions), and our consumers own their id
  space so they map a collision on a self-generated id to "already applied". Kills
  the controller-side pre-dispatch comparison; `checkIdIntegrity` #1 is the single
  mechanism. Trade-off accepted: honest retry and accidental id-reuse both surface
  as `ID_COLLISION`. See OQ5.
- **2026-07-06 — `checkIdIntegrity` check #2 landed.** Foreign-reservation
  appointment-id guard implemented in `LocalAbstractCachableOperator.check()`
  (`checkIdIntegrity`, sibling of `checkGraphqlKeySpecCompliance`), pinned by
  `AppointmentIdIntegrityTest` (tier 2). §12-uniform message (client's own
  appointment id only).
- **2026-07-06 — check #1 create-intent design locked (not yet implemented).**
  Thread create-intent through two carriers since we control all write paths:
  transient `SimpleEntity.isNew` (set in `FacadeImpl.setNew`, reset on
  `editObject` clone) + serialized `UpdateEvent.createReferences` (populated in
  `createUpdateEvent` from `isNew`, and directly by the GraphQL create verbs).
  Guard = `createReferences` ref with `findPersistent != null → reject` with
  `ID_COLLISION` (no content comparison — revised OQ5); fail-open for undeclared
  writes; target set = Reservation + Allocatable (the id-first create surfaces;
  guard runs generically but is inert for server-id-assigned entities). Full
  rationale in §9. See the "Create-intent design" block.
- **2026-07-06 — client-supplied ids + `checkIdIntegrity` (locked decision §9).**
  From the PRD 091 design dialog: ids stay optional (B′) *(superseded same day —
  ids are now mandatory, see the newer entry above)*; subset restrictions require
  appointment ids (loud validation replaces the silent trap); id normalization
  server-side *(also superseded — ids are stored verbatim, approach W)*; new
  operator-side guard `checkIdIntegrity` in `check()` (PRD 058
  pattern) rejecting new-entity id collisions and foreign-reservation appointment
  ids — discovered as an open gap of the shipped controller (client ids honored
  unchecked, no dispatch-side check). §12-uniform rejection required.
- **2026-05-28** — PRD opened. Locked picks captured above:
  - Approach 2 (named verbs) + Option C (`applyChanges` with typed ChangeOp)
  - 6-mutation surface
  - Update is full-state (not patch)
  - Delete consolidated to bulk-only
  - ATOMIC-only in v1; PARTIAL deferred
  - `expectedLastChanged` on update only
  - Same-batch refs via client UUIDs
  - ChangeOp exactly-one invariant via boundary validation
  - Error code taxonomy as stable strings
- **2026-05-28 — symmetric β² classification I/O** — locked. Drops
  generic `ClassificationInput` + `AttributeValueInput` +
  `AttributeValuePayload`. Adopts typed per-DynamicType
  `<TypeKey>ClassificationInput` (generated, hot-swapped) +
  `AllocatableClassificationInput @oneOf` /
  `ReservationClassificationInput @oneOf` polymorphic dispatch.
  Mirrors read-side typed classifications; one mental model both
  directions. OQ2 resolved (see §Open questions).
- **2026-05-28 — `@oneOf` directive adopted** for all polymorphic
  inputs. Replaces the manual exactly-one-set boundary validator on
  `ChangeOp` with spec-level engine enforcement; clients get
  discriminated unions in TypeScript codegen.
- **2026-05-28 — OQ1.a, OQ1.d, OQ5 resolved:**
  - `ownerId` removed from `CreateReservationInput` AND `UpdateReservationInput`
    (server sets owner to caller on create; subsequent changes via
    `changeReservationOwner`)
  - Empty `appointments[]` rejected with `REQUIRED`
  - Same-UUID retry semantics: matching content → no-op success; differing → `ID_COLLISION`
    *(revised 2026-07-06: no content comparison — any existing id → `ID_COLLISION`; see OQ5)*
- **2026-05-28 — OQ1.c, OQ6 resolved:**
  - `typeId` change on update → reject with `INVALID_TYPE_CHANGE`. Type
    changes land on a future `reshapeReservation` mutation per PRD 035 §7.
    `typeId` stays in `UpdateReservationInput` as defensive cross-validation.
    *(REVISED 2026-07-07 — see OQ1.c above: type change now accepted in-place,
    no reshapeReservation; preview is client-side in the PRD 096 editor.)*
  - Reservation delete has no inbound references → clean delete.
  - Future allocatable/user/permission delete reject with `REFERENCE_EXISTS`
    + referrer list. `applyChanges` is dependency-aware (excludes same-batch
    deletes from the referrer check) for atomic clean-up workflows.
- **2026-05-28 — PRD 062 (API Robustness, originally 058) opened** as a parking lot for
  heavy-infrastructure patterns (in-flight idempotency lock, TTL cache,
  rate limiting, query complexity caps, distributed tracing). Not in PRD
  056 scope; revived when rapla scale / multi-tenant deployment changes
  the calculus.
- **β read refactor completed 2026-05-28** — dropped `AttributeValue`
  output, `AttributeDescriptor`, `attributes` field from Classification
  interface and DynamicType; added `@displayName`, `@expectedType`,
  `@rootCategory`, `@multiplicity`, `@required` SDL directives. 51/51
  GraphQL tests passing.
- **Next:** PRD 056 implementation (track 2 — SDL generator extension
  for `<TypeKey>ClassificationInput`, mutation controllers, validation
  mapper, tier-3 tests).
