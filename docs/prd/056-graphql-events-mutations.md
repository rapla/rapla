# PRD 056 — GraphQL Events Mutation API

**Status:** in-progress (design)

**Parent:** PRD 035 §6 "Bulk mutations" (line 161+) — supersedes that section per the
2026-05-28 design discussion. **Sibling:** PRD 055 (events read — locked & implemented
for resolvers).

**Related cross-PRDs:**
- PRD 040 — lock-set requirement for bulk operations
- Future PRD 057 — allocatables write (extends `applyChanges`)
- Future PRD 058 — users / permissions write (extends `applyChanges`)
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
- **`reshapeClassification`** preview query + type-change save path —
  separate PRD
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
  moveReservations(ids: [ID!]!, dateShift: Duration!): BulkResult!
  copyReservations(ids: [ID!]!, dateShift: Duration!): BulkResult!
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
  user:         User                    # future (PRD 058)
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

## Verb-level semantic notes

### `createReservation(input)`

§12: caller must `canCreate(DynamicType, user)` for the reservation type, AND
`canRead(allocatable, user)` for every referenced allocatable. Unknown / unreadable
references fail identically (§12 existence rule).

If `input.id` is null, server generates a UUID. If supplied, must be unique against
storage (collision = `ValidationError` with code `ID_COLLISION`).

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

### `moveReservations(ids, dateShift)`

Shifts the **start** of each reservation's appointments by `dateShift` (ISO-8601
Duration: `"P7D"`, `"PT-30M"`, etc.). Recurrence rules preserved structurally —
WEEKLY stays WEEKLY, just N days offset. Exceptions shift too.

§12: `canModify` per reservation. If a shift pushes the recurrence rule's `end`
date before its `start` → `INVALID_SHIFT` error per affected reservation;
ATOMIC rejects the batch.

### `copyReservations(ids, dateShift)`

Duplicates with new UUIDs. `dateShift` is required — copying without shift creates
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
6. **Tier-3 tests** — MockMvc + HttpGraphQlTester:
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

**OQ1.c — typeId change on update — RESOLVED 2026-05-28: reject.**
`updateReservation` is for content edits only. If `input.typeId` differs
from the stored reservation's `typeId`, server rejects with
`INVALID_TYPE_CHANGE`. Type changes are a high-intent operation needing
their own UX (preview which attributes get dropped before commit) and
land on a future dedicated `reshapeReservation` mutation per PRD 035 §7.

`typeId` stays in `UpdateReservationInput` as defensive cross-validation:
- The `@oneOf` variant inside `classification: ReservationClassificationInput!`
  encodes the type via field name (`lehrveranstaltung: { ... }`)
- The outer `typeId` is the explicit discriminator
- Server validates: `input.typeId` ↔ classification's `@oneOf` variant ↔
  stored `.typeId` must all align; mismatch → `INVALID_TYPE_CHANGE` (or
  `MISMATCHED_TYPE` if it's just internal input inconsistency)

Same intent-verb pattern as OQ1.a (`ownerId` → `changeReservationOwner`).

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

### OQ5 — Idempotency on retry with same UUID — RESOLVED 2026-05-28

**Same UUID + matching content = no-op success. Same UUID + differing
content = `ID_COLLISION`.** Server compares the input's client-supplied
fields against the existing entity (canonicalized; server-managed fields
like timestamps excluded). Match → return existing entity, status SUCCESS.
Diff → reject with `ID_COLLISION` extension carrying the existing entity
id and the list of differing field paths so the caller can debug.

Implementation: on create, before dispatch, check the storage for the
supplied UUID. If present, build the "would-be-created" entity from the
input and compare to stored. Cost: one extra storage lookup on the rare
collision path; negligible on the happy path (UUID not present).

**Not in scope for PRD 056** (parked in [PRD 058 — API Robustness](058-graphql-api-robustness.md)):
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
- **2026-05-28 — OQ1.c, OQ6 resolved:**
  - `typeId` change on update → reject with `INVALID_TYPE_CHANGE`. Type
    changes land on a future `reshapeReservation` mutation per PRD 035 §7.
    `typeId` stays in `UpdateReservationInput` as defensive cross-validation.
  - Reservation delete has no inbound references → clean delete.
  - Future allocatable/user/permission delete reject with `REFERENCE_EXISTS`
    + referrer list. `applyChanges` is dependency-aware (excludes same-batch
    deletes from the referrer check) for atomic clean-up workflows.
- **2026-05-28 — PRD 058 (API Robustness) opened** as a parking lot for
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
