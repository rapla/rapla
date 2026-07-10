# PRD 064 — GraphQL Conflicts Read API

**Status:** in-progress (design + implementation, opened 2026-05-29)

**Date:** 2026-05-29

**Parent:** [PRD 035 (done) — GraphQL foundations](done/035-graphql-foundations.md). Picks up the "conflicts surface" deferred from [PRD 055 — Events Read API](055-graphql-events-read-api.md) §"Deferred / next PRDs".

**Siblings:**
- [PRD 055 — Events Read API](055-graphql-events-read-api.md) — provides the Reservation / Appointment / Allocation types this PRD references
- [PRD 060 — MCP foundations](060-graphql-mcp-foundations.md) — `checkConflicts` dry-run is a separate concern (compute op, not data read); when it lands, it sits next to this PRD's data-read surface

## Goal

Expose rapla's existing conflict detection through GraphQL so the SPA can show conflict badges on reservations without a separate REST endpoint, and so MCP agents can ask "what's conflicting with this booking?" cleanly.

## Scope

In v1 (this PRD):
- `Query.conflicts(reservationId: ID!): [Conflict!]!` — list conflicts touching a specific reservation.
- `type Conflict` — single output type carrying both sides of the conflict, the allocatable, and the involved appointments.
- §12: Conflict involves two reservations. The caller must be able to read **both** to see the conflict; otherwise the conflict is dropped from the result. The allocatable must also be readable.

Out of scope (deferred to follow-ups):
- `Reservation.conflicts: [Conflict!]!` field (the full list inline on each reservation) — still deferred per the original per-row dispatch cost reasoning. The cheaper boolean variant `Reservation.hasConflicts: Boolean!` SHIPPED 2026-05-29 via [PRD 028 Phase 1 §"GraphQL substrate augmentations"](028-angular-power-search.md#phase-1--name-only-search-no-group--classification-dependencies--shipped-2026-05-29) — power-search badge UX justified the per-row cost; calendar view consumers should consume `hasConflicts` for badging and use `Query.conflicts(reservationId:)` for the editor's full list. The full-list inline field stays deferred until a concrete consumer needs it.
- `Query.allConflicts(filter: ConflictFilter!)` — admin overview across all reservations. Useful but the filter shape needs design (time window + allocatable + user).
- `checkConflicts(input)` dry-run — compute operation, lives in [PRD 060](060-graphql-mcp-foundations.md).

## Locked design

### Surface

```graphql
type Query {
  """
  Conflicts touching the specified reservation. Returns empty list for
  reservations the caller can't read OR with no conflicts.
  §12: conflicts where the OTHER reservation is not caller-readable are
  silently dropped (mixed visible/hidden = visible-only subset).
  """
  conflicts(reservationId: ID!): [Conflict!]!
}

"""
A scheduling conflict — two appointments allocating the same Allocatable at
overlapping times. The two-reservation symmetry is preserved in the wire
shape; consumers showing "the OTHER side" should compare against the
reservation they queried with.
"""
type Conflict {
  id:            ID!
  allocatable:   Allocatable!
  reservation1:  Reservation!
  reservation2:  Reservation!
  appointment1:  Appointment!
  appointment2:  Appointment!
  startDate:     LocalDateTime!
}
```

### §12 invariants

1. **Anonymous.** Returns empty list — existence not leaked.
2. **Unknown reservationId.** Returns empty list — existence not leaked (per the existing `Query.reservation(id)` pattern).
3. **Caller can't read the queried reservation.** Returns empty list (§12 — don't reveal "this exists but you can't see it").
4. **The OTHER reservation in the conflict isn't caller-readable.** Drop the conflict from the result — never return a `Conflict` exposing a hidden reservation's id/name.
5. **The allocatable isn't caller-readable.** Drop the conflict (the allocatable's existence is the conflict's evidence).

### Java wiring

- `ConflictGraphQLController` in `rapla-app/src/main/java/org/rapla/server/spring/graphql/`.
- `@QueryMapping conflicts(reservationId)` — resolves caller from `RequestContextInstrumentation`, fetches the reservation via `operator.tryResolve`, calls `operator.getConflicts(reservation)` (existing API, returns `Promise<Collection<Conflict>>`), filters by §12 at the output boundary.
- Conflict's nested references (reservation1, reservation2, appointment1, appointment2) resolve via the existing Reservation + Appointment graphs — `@SchemaMapping` on the controller for the two ref-fields, eagerly resolved (cheap — already in cache).
- `Conflict.id` derived from rapla's `Conflict.getId()` (Conflict is an Entity).

## Tests

Tier-3 MockMvc spec:
- Schema introspection: `Query.conflicts` + `type Conflict` shape.
- §12 anonymous: returns empty.
- §12 unknown id: returns empty.
- §12 happy-path admin: returns conflicts (requires fixture with conflicting reservations).
- Mixed-visibility (deferred to a future spec once we have a multi-reservation conflict fixture; the §12 logic is exercised at the existing canRead boundary already).

## Open questions

### OQ1 — Conflict id stability

Rapla's `Conflict.getId()` is a derived hash; if reservation ids change (impossible today but theoretical), conflict ids change too. Consumers should treat conflict ids as session-scoped, not persistent.

**Lean: document, no schema change.** Add a doc note on `Conflict.id` indicating it's derived from the involved reservation/appointment ids and may change if either side is modified.

### OQ2 — Should disabled/editable booleans be exposed?

`Conflict.checkEnabled()` + `isAppointment1Enabled()` / `isAppointment2Enabled()` carry rapla-side "is this conflict still real / actionable" semantics. Useful for the SPA badge UX.

**Lean: defer.** Real consumer demand from the SPA hasn't surfaced yet; the basic id + reservation + appointment surface covers the "show me the conflict" use case. Add fields when needed; additive change.

## Decision log

- **2026-05-29** — PRD opened. Scope deliberately minimal: one query, one type, §12 enforcement. Defers the per-Reservation field, the admin overview, and the dry-run compute op.
