# PRD 063 — GraphQL Allocatables Write API

**Status:** in-progress (design + implementation, opened 2026-05-29)

**Date:** 2026-05-29

**Parent:** [PRD 035 (done) — GraphQL foundations](done/035-graphql-foundations.md). Continues the per-entity write surface PRD 056 (events) and PRD 057 (DynamicTypes) opened.

**Siblings:**
- [PRD 056 — Events Write API](056-graphql-events-write-api.md) — the verb naming + typed-classification input + ChangeOp dispatch patterns this PRD mirrors. β² typed classification inputs (already shipped — `AllocatableClassificationInput @oneOf` + per-DT `<TypeKey>ClassificationInput`) are reused as-is.
- [PRD 057 (done) — DT mutations v1](done/057-graphql-dt-mutations-v1.md) — the admin-gate / `requireCanCreate` patterns reused for type-level permissions.

## Goal

Land the write-side **allocatables** GraphQL surface so admins + group-admins can create, update, and delete Resources / Persons from the SPA or MCP without falling back to the legacy `/api/storage/*` thick client.

Combined with PRD 056 (events writes) + PRD 057 (DynamicType mutations), this closes "admin can do everything via GraphQL" for the scheduling domain.

## Scope

In v1 (this PRD):
- `createAllocatable(input: CreateAllocatableInput!): Allocatable!` — owner = caller by default; admins may override.
- `updateAllocatable(id: ID!, input: UpdateAllocatableInput!, expectedLastChanged: LocalDateTime): Allocatable!` — full-state replace with optimistic concurrency. Owner immutable here (future `changeAllocatableOwner` verb if demand).
- `deleteAllocatables(ids: [ID!]!): BulkResult!` — bulk, no per-id concurrency check (use `applyChanges` for concurrency-checked delete).
- `ChangeOp` extension — additive `createAllocatable` / `updateAllocatable` / `deleteAllocatable` variants for atomic cross-type batches.
- Tier-3 tests: schema smoke, §12 gate (anonymous + non-admin), happy-path create + read-back, restriction on type, key collision (n/a — allocatables don't have keys), delete-with-instances rejection (n/a — events block deletion of allocatables they reference, not the other way).

Out of scope:
- Permission editing on allocatables. Existing permissions are preserved on update; new allocatables start with type-default permissions (mirror facade's `addDefaultResourcePermissions`).
- ALLOCATABLE-typed attribute references between allocatables (e.g. a Room's `Building` allocatable attribute). The reads handle this; writes deferred until we have a documented use case beyond static lecturer/room data.
- Owner change verb. `createReservation`-style "owner = caller, server sets" applies; admin override on create; immutable on update; future `changeAllocatableOwner(ids, newOwnerId)` if real demand.
- Conflict checking on save (e.g. reservation references through this allocatable). Same model as PRD 057's DT delete — if conflicts exist, the storage layer rejects with `STORAGE_ERROR`; admin handles dependencies first.

## Locked design

### Surface

```graphql
type Mutation {
  createAllocatable(input: CreateAllocatableInput!): Allocatable!
  updateAllocatable(id: ID!, input: UpdateAllocatableInput!,
                    expectedLastChanged: LocalDateTime): Allocatable!
  deleteAllocatables(ids: [ID!]!): BulkResult!
}

input CreateAllocatableInput {
  "Client-supplied UUID for retry safety + same-batch refs. Null = server generates."
  id:             ID
  "Discriminator — must match the @oneOf variant in classification (the DynamicType key, e.g. \"room\")."
  typeKey:        String!
  "Typed classification per PRD 055/056 β² pattern — @oneOf variant must match typeKey."
  classification: AllocatableClassificationInput!
  "Optional. Null = caller becomes owner. Admin-only when non-null."
  ownerId:        ID
}

input UpdateAllocatableInput {
  "Defensive cross-check — must equal the stored allocatable's typeKey."
  typeKey:        String!
  classification: AllocatableClassificationInput!
}
```

### ChangeOp extension

```graphql
input ChangeOp @oneOf {
  # ... existing reservation variants ...
  createAllocatable: CreateAllocatableInput
  updateAllocatable: ChangeOpUpdateAllocatable
  deleteAllocatable: DeleteInput
}

input ChangeOpUpdateAllocatable {
  id:                  ID!
  input:               UpdateAllocatableInput!
  expectedLastChanged: LocalDateTime
}
```

### §12 invariants

1. **Permission gate.** `caller.isAdmin == false` → check `PermissionController.canCreate(DynamicType, caller)` (RESOURCE-type) / `canModify(Allocatable, caller)` (UPDATE) / `canAdmin(Allocatable, caller)` (DELETE). Same gates the Swing admin panel applies.
2. **Owner ID override.** `CreateAllocatableInput.ownerId` non-null + non-admin caller → `PERMISSION_DENIED`. Admins may set arbitrary owner.
3. **Type change on update.** `UpdateAllocatableInput.typeKey` ≠ stored → `INVALID_VALUE` (analogous to PRD 056 OQ1.c — in-place type change rejected; future reshape verb if needed).
4. **No leak on delete.** Unknown id and admin-readable-but-not-deletable id produce identical error shape (`REFERENCE_NOT_FOUND` vs `PERMISSION_DENIED` — keep distinct codes since the admin needs to know the difference; but `deleteAllocatables` is admin-only at the resolver entry, so the `canRead` check happens before the response shape diverges).

### Error code taxonomy

- `REQUIRED` — missing typeKey / classification
- `INVALID_VALUE` — unknown typeKey, type change on update, malformed classification @oneOf
- `REFERENCE_NOT_FOUND` — unknown id (update / delete)
- `MISMATCHED_TYPE` — @oneOf classification variant ≠ typeKey
- `PERMISSION_DENIED` — caller not authorized
- `CONCURRENT_MODIFICATION` — expectedLastChanged mismatch
- `STORAGE_ERROR` — dispatch failure (e.g. allocatable in use by reservations on delete)

## Plan

1. Schema additions in `schema.graphqls` — input types + Mutation roots + ChangeOp variants.
2. `AllocatableMutationController` in `rapla-app/src/main/java/org/rapla/server/spring/graphql/` — mirrors `ReservationMutationController` shape. Reuses `ReservationMutationController.ReservationMutationException` (rename to a shared `GraphQlMutationException`? — deferred to keep PR small).
3. Reuses `buildClassificationFromInput` helper logic — extract to a shared utility if it's a clean cut, otherwise duplicate the body.
4. Reuses `MutationExceptionResolver` — no change needed; already handles `RaplaException` + `IllegalArgumentException` + the rapla mutation exception type.
5. Tier-3 tests: schema smoke, anonymous reject, non-admin reject, admin create-then-read-back, unknown-typeKey reject, classification-variant mismatch, update with stale `expectedLastChanged` rejected.

## Open questions

### OQ1 — Shared exception type

`ReservationMutationException` is currently nested in `ReservationMutationController`. The DT mutation controller (PRD 057) already cross-references it; this PRD will be the third user. Should we extract a top-level `GraphQlMutationException` shared by all three controllers?

**Lean: yes**, but as a follow-up. The current cross-package import (`ReservationMutationController.ReservationMutationException`) works; rename is a mechanical refactor that can land independently.

### OQ2 — Permission editing on allocatables

Allocatables carry per-instance permission lists (read / allocate / allocate_conflicts / modify). Current write API preserves existing permissions on update; new allocatables start with type defaults. A future `setAllocatablePermissions(id, permissions)` verb could expose this.

**Lean: defer** until the SPA admin panel surfaces permission editing as a real UX need.

### OQ3 — Allocatable bulk transformations

PRD 056 ships `moveReservations` / `copyReservations` / `changeReservationOwner` because reservations have natural bulk verbs (calendar shifts, owner reassignment after a staff change). Allocatables have analogues — `changeAllocatableOwner(ids, newOwnerId)` for staff-change-style reassignment. Worth opening as a future PRD if the admin UX wants it.

**Lean: defer** until the SPA admin views surface a bulk-owner workflow.

## Decision log

- **2026-05-29** — PRD opened. Surface mirrors PRD 056 (named verbs + ChangeOp additive variants). β² typed-classification inputs reused as-is from PRD 055 read side.
