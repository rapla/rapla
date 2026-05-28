# PRD 055 — GraphQL Events API (Reservation + Appointment + Allocation)

**Status:** in-progress

**Parent:** PRD 035 §"Per-type shape" (line 467+) + §6 "Bulk mutations" (line 161+).
PRD 035 fixes the architecture; this PRD nails down the resolver-batch surface,
captures open design questions specifically about the allocation/restriction
model, and lands the read-side resolvers.

**Related work shipped:**
- Cut C allocatables/classifications (`AllocatableClassification`,
  `ReservationClassification` interfaces, generated `<TypeKey>Classification`
  types). The `ReservationClassification` interface already has its 6
  implementations from this deployment's reservation DynamicTypes
  (`Lehrveranstaltung`, `Pruefung`, `Exkursion`, etc.).
- `Allocatable` resolvers + `§12` patterns we extend here.

## Goal

Land the read-side **events / reservation** GraphQL surface — single +
list queries returning Reservations with their appointments, allocations,
classification, owner. The shape must:

- Capture rapla's restriction model fully (per-appointment allocation
  scoping) for editor round-trips.
- Be naturally ergonomic for the SPA's calendar / list views.
- Default to time-bounded queries to avoid the 14s admin-scale problem
  we hit on `allocatables`.

**This PRD explicitly considers improvements over the legacy rapla
restriction model.** We're designing a new API surface, not faithfully
mirroring storage internals. Where a cleaner API shape exists, take it
and adapt the storage-side mapping rather than the reverse.

## Scope

In:
- `reservation(id: ID!): Reservation` — single, for editor open
- `reservations(filter: ReservationFilter!): [Reservation!]!` — list,
  time-bounded
- Structural types: `Reservation`, `Appointment`, `RepeatingRule`,
  `AppointmentBlock` (no `allocatables` field — inherited via
  `block.appointment.allocatables`; restrictions are appointment-level),
  `Allocation`
- §12 filtering at the reservation boundary + nested allocatable boundary
- Generated typed-classification access for reservation types
  (`... on LehrveranstaltungClassification { ... }`)
- Mandatory window arg + default limit + window-size cap

Out (future PRDs):
- **Mutations (create/update/delete/bulk)** — covered by [PRD 056](056-graphql-events-mutations.md)
  (sibling). Supersedes PRD 035 §6 with named-verbs + `applyChanges` design.
  Symmetric β² (2026-05-28): write side gets typed per-DynamicType
  classification inputs mirroring this PRD's typed reads — same
  hot-swap, same one-schema-two-modes consumption pattern.
- Conflicts derivation (`conflicts: [Conflict!]!`)
- Templates (`rapla:template` is currently filtered out per PRD 055-Cut-C policy)
- `Reservation.canModify` — defer with mutations
- The `renderedBlocks` server-side projection (PRD 030)

## Locked decisions (confirmed 2026-05-27)

| Q | Decision | Rationale |
|---|---|---|
| 1 | Name `Reservation` (not `Event`) | Matches rapla internal naming + already locked in PRD 035 schema (`ReservationClassification`) |
| 2 | `AppointmentBlock` is id-less | Block ids aren't stable across recurring expansions; use parent ids + start for addressing |
| 3 | `Appointment.repeating` nullable (not `RepeatingType.NONE`) | Cleaner: "no recurrence" is its own state, not an enum value |
| 4 | `Reservation.allocations[]` (restriction-aware, editor-only) + `Appointment.allocatables[]` (pre-resolved, everyone else). Block has no `allocatables` field — traverses via `block.appointment.allocatables`. Block is a sub-resolution on Appointment (`Appointment.blocks(from:, to:)`), not a peer query root. | Use-case driven: editor needs lossless restriction structure for round-trip save; listviews / iCal / calendar need pre-resolved per-appointment lists with no client-side join. Restrictions are appointment-level in rapla's domain (see [domain-model.md §Reservation](../architecture/domain-model.md#reservation)), so blocks inherit allocatables from their parent appointment with no duplication. Architectural rationale lives in [PRD 035 §"Consumer-driven read surfaces"](035-rapla-mcp-server.md#consumer-driven-read-surfaces-locked-2026-05-27). |
| 5 | `RepeatingRule.exceptions: [LocalDate!]!` (not LocalDateTime) | Exceptions are calendar-date concept, not wall-time |

## Allocation / restriction model — locked 2026-05-27 (see Q4)

**Resolution:** two-shape exposure. `Reservation.allocations[]`
carries the restriction-aware structure (editor's lossless source
of truth). `Appointment.allocatables[]` carries the pre-resolved
per-appointment list (every other consumer). Blocks have no
`allocatables` field — they inherit via traversal
`block.appointment.allocatables`. See
[PRD 035 §"Consumer-driven read surfaces"](035-rapla-mcp-server.md#consumer-driven-read-surfaces-locked-2026-05-27)
for the per-consumer rationale; [PRD 035 §"Per-type shape"](035-rapla-mcp-server.md#per-type-shape)
for the locked SDL fragments.

The Options A–E discussion below is preserved as decision-log
context; resolution maps to **Option C with refinement** — pre-resolved
allocatables live on `Appointment` (not on Block, because rapla
restrictions are appointment-level — see
[domain-model.md §Reservation](../architecture/domain-model.md#reservation)).

### The rapla legacy model

A Reservation has multiple appointments and multiple allocatables. By default,
every allocatable is bound to every appointment (the "reservation-wide"
case — 80% of real reservations). Optionally, an allocatable can be
**restricted** to specific appointments — the "split" case:

> Example (from the DHBW workflow):
> A repeating lecture gets split into 10 standalone appointments. Each
> gets a different room. The lecturer is the same for 9 of them; one week
> has a guest lecturer.

The storage shape: `Allocatable[] allocatables` + `void setRestriction(Allocatable, Appointment[])`. The default is "no restriction = bound to all"; restriction list = "bound only to these."

> **The principle** (user 2026-05-27): "restrictions is rapla's compromise of
> not allocating a resource on every individual appointment but keeping
> the flexibility to do so."

### Option A — faithful mirror

```graphql
type Reservation {
  allocations: [Allocation!]!
}
type Allocation {
  allocatable:    Allocatable!
  appointmentIds: [ID!]   # null = bound to all; non-null = restricted to these
}
```

Pros: lossless, smallest wire shape, source-of-truth for editor saves.
Cons: client-side join for "what's booked at appointment X" (~5 lines of
fiddly logic with the null sentinel handling).

### Option B — appointment-centric

```graphql
type Reservation {
  # No top-level allocations; everything per-appointment
}
type Appointment {
  allocatables: [Allocatable!]!   # always populated, no restriction concept exposed
}
```

Pros: most natural for renderers; no client-side join; no null-sentinel
weirdness.
Cons: editor save must reconstruct restrictions server-side from the
delta — risk of round-trip data loss if the user toggles "bind X to all"
vs "bind X to every individual currently". Wire shape inflates the
common case (~14× duplication).

### Option C — both (current leaning, 2026-05-27)

```graphql
type Reservation {
  # Canonical structured form. Source of truth. Restriction-aware.
  # Used by editor reads + mutation round-trips.
  allocations:    [Allocation!]!
}
type Appointment {
  # Server-derived view: which allocatables are bound to THIS appointment,
  # resolved from parent Reservation.allocations (where appointmentIds is
  # null OR contains this.id). Convenience for read-only / calendar.
  allocatables:   [Allocatable!]!
}
```

Pros: editor gets canonical form; renderer gets natural per-slot view;
single resolver, no drift; gzip + Apollo normalization absorb the wire
overhead for the common case.
Cons: two ways to express the same data; documentation cost (which to use
when).

### Option D — tag-based binding

```graphql
type Reservation {
  allocations: [Allocation!]!
}
type Allocation {
  allocatable: Allocatable!
  scope:       AllocationScope!
}
union AllocationScope = ReservationWide | AppointmentRestricted
type ReservationWide { _: Boolean }
type AppointmentRestricted { appointmentIds: [ID!]! }
```

Pros: eliminates the null-sentinel ambiguity; restriction is explicit
in the type system; readable in introspection.
Cons: more verbose; unions are awkward for some clients; doesn't address
the client-side join question.

### Option E — Allocation as first-class entity

```graphql
type Allocation {
  id:           ID!
  allocatable:  Allocatable!
  reservation:  Reservation!
  appointments: [Appointment!]!  # which slots — empty = reservation-wide
}
type Query {
  allocations(filter: ...): [Allocation!]!  # queryable directly
}
```

Pros: lets you ask "which reservations book Room X?" without joining
through Reservation. Becomes a real graph edge.
Cons: heavier (new entity, new id space, new query root); not what most
consumers need; closer to a CRM model than a calendar model.

### Improvement opportunities the new API enables

We're not bound to legacy semantics. Worth considering:

- **Explicit "Reservation-wide" sentinel in the type system** rather than
  null-means-all (Option D's union, or an enum). Loses a footgun.
- **First-class "allocation history"** — track restriction changes as
  audit events. Currently lost in rapla's flat model.
- **Bind-to-pattern** — instead of restricting to literal appointment ids,
  restrict by recurrence pattern ("every other Monday"). Higher fidelity
  but requires a new server primitive.
- **Per-appointment notes / colour on Allocation** — annotations specific
  to one slot ("Room A used as overflow only").

## Open questions

### OQ1 — Allocation model (A/B/C/D/E or hybrid?) — RESOLVED 2026-05-27

Resolution above (§"Allocation / restriction model"). Two-shape exposure:
`Reservation.allocations[]` for editor; `Appointment.allocatables[]`
for everyone else. Block has no `allocatables` field. The "two ways
to express" cost is paid deliberately because the two shapes serve
distinct consumers (editor round-trip vs. listview read).

### OQ2 — Recurrence exceptions: split into Reservation or attached to Appointment? — RESOLVED 2026-05-27

**Stay on `Appointment.repeating.exceptions: [LocalDate!]!`.** No
metadata-per-exception use case has surfaced; introducing separate
entities loses the simple LocalDate-list shape that matches rapla's
`Repeating.exceptions: Set<LocalDateTime>` storage. Revisit if/when
"why was this date excluded" becomes a real need.

### OQ3 — `Reservation.canModify` — eager or query-on-demand? — RESOLVED 2026-05-27

**Ship eager** as `canModify: Boolean!` on Reservation. Cheap
(`PermissionController.canModify` is a permission-graph walk in
microseconds for the typical case), saves a SPA roundtrip per
edit-button gating decision.

### OQ4 — `firstDate` / `lastDate` convenience fields — RESOLVED 2026-05-27

**Ship both.** `Reservation.firstDate = min(appointment.start)`,
`Reservation.lastDate = max(appointment.maxEnd)`. Rapla already exposes
`getFirstDate()` and `getMaxEnd()` server-side; deriving client-side
across appointments + recurrence is fiddly. Used heavily in list
rendering.

### OQ5 — How to expose conflicts in this PRD vs a follow-up — RESOLVED 2026-05-27

**Defer.** No conflict field on Reservation in v1. Conflict computation
has real perf implications (cross-allocatable schedule scan); modelling
it as a separate top-level `checkConflicts(...)` query later — per
PRD 035 §"Consumer-driven read surfaces" line 457 "Scheduling pre-flight"
row — gives us room to design the appointment-pair aggregation properly
(a weekly clash = **one** conflict spanning N dates, not N conflicts).

## Example queries

The ten consumer shapes that drive the schema. Generic — substitute
`<ResTypeKey>` with a deployment's reservation DynamicType key
(e.g. `Lehrveranstaltung`/`Pruefung`/`Exkursion` in dhbw, `event` in
testdefault.xml).

### 1. Editor open — single reservation, restriction structure intact

The only consumer that selects `allocations` (lossless restriction
shape, needed to round-trip on save).

Post-β (locked 2026-05-28, see decision log): the editor reads
attribute *values* via the typed `... on <ResTypeKey>Classification`
fragment and discovers attribute *metadata* (expected type,
multiplicity, required, enum domain) via a one-shot introspection
query at SPA start (`__type(name: "<ResTypeKey>Classification")
{ fields { name type { ... } } }` plus the `@expectedType` /
`@multiplicity` / `@required` / `@enumDomain` directives). The
schema is the one source of truth — no parallel `attributes:
[AttributeDescriptor!]!` field on `DynamicType`.

```graphql
query EditorOpen($id: ID!) {
  reservation(id: $id) {
    id
    canModify
    classification {
      typeId
      type { key name }                              # metadata via introspection, not via .attributes
      ... on <ResTypeKey>Classification {           # typed value access
        # deployment-specific typed fields, built dynamically
        # from introspection + directives at SPA build time
      }
    }
    appointments {
      id start end allDay
      repeating { type interval end count weekdays exceptions }
    }
    allocations {                                    # editor-only
      allocatable { id displayName type }
      appointmentIds                                 # null = bound to all
    }
    owner { username name }
    createdAt
    lastModifiedAt
  }
}
```

### 2. Listview — paged, with per-appointment allocations resolved

The workhorse SPA query. Uses `Appointment.allocatables` (pre-resolved
view), never `Reservation.allocations`.

```graphql
query ListView($from: LocalDateTime!, $to: LocalDateTime!) {
  reservations(filter: { from: $from, to: $to, limit: 100 }) {
    id
    firstDate lastDate
    classification { type { key name } }
    appointments {
      start end
      allocatables { displayName }                   # per-appointment view
    }
  }
}
```

### 3. My reservations (owner-scoped)

```graphql
query MyReservations($me: ID!, $from: LocalDateTime!, $to: LocalDateTime!) {
  reservations(filter: { ownerEq: $me, from: $from, to: $to }) {
    firstDate lastDate
    appointments { start end allocatables { displayName } }
  }
}
```

### 4. Bookings of a specific resource

"Show me every reservation that books Room-A in the next 4 weeks."

```graphql
query BookingsForResource($room: ID!, $from: LocalDateTime!, $to: LocalDateTime!) {
  reservations(filter: { allocatableIdsIn: [$room], from: $from, to: $to }) {
    firstDate lastDate
    classification { type { key } }
    appointments {
      start end
      allocatables { id displayName }                # other resources sharing the slot
    }
    owner { name }
  }
}
```

### 5. iCal-shaped export — recurring rules intact, no expansion

For CalDAV / iCal-style consumers that handle RRULE / EXDATE themselves
client-side. **No `blocks(...)`** — the recurrence rule is the wire format.

```graphql
query ICalExport($from: LocalDateTime!, $to: LocalDateTime!) {
  reservations(filter: { from: $from, to: $to, limit: 5000 }) {
    id
    classification { type { key } }
    appointments {
      id start end
      repeating { type interval end count weekdays exceptions }
      allocatables { displayName }                   # for SUMMARY/LOCATION
    }
  }
}
```

### 6. Calendar grid — blocks materialized server-side

Week / month view. Server expands the recurrence via
`Appointment.createBlocks(...)` so the SPA doesn't reimplement rapla's
recurrence semantics (MONTHLY = Nth-weekday, YEARLY = leap-year skip,
etc. — see [domain-model.md §Appointment](../architecture/domain-model.md#appointment--repeating--appointmentblock)).

```graphql
query CalendarWeek($from: LocalDateTime!, $to: LocalDateTime!) {
  reservations(filter: { from: $from, to: $to }) {
    id
    classification { type { key } }
    appointments {
      blocks(from: $from, to: $to) {                 # materialized expansion
        start end isException
      }
      allocatables { displayName }                   # block.appointment.allocatables
    }
  }
}
```

### 7. Search — name + type + window

```graphql
query SearchByName {
  reservations(filter: {
    from: "2026-01-01T00:00:00",
    to:   "2026-12-31T23:59:59",
    typeKeyEq:    "<ResTypeKey>",
    nameContains: "intro",
    limit: 100
  }) {
    firstDate
    classification { type { key } }
    appointments { start end }
  }
}
```

### 8. Edit-button gating — just `canModify`

The smallest query — what the SPA fires to decide whether to render
the "edit" pencil. Cheap; one resolver hit.

```graphql
{ reservation(id: "...") { canModify } }
```

### 9. Typed-classification narrowing (codegen consumer)

Plugin author / MCP integrator with a known deployment uses typed
access. Same restriction: deployment-coupled — SPA must not.

```graphql
{
  reservations(filter: {
    typeKeyEq: "<ResTypeKey>",
    from: "...", to: "..."
  }) {
    classification {
      ... on <ResTypeKey>Classification {
        # specific attribute fields generated per DynamicType
      }
    }
  }
}
```

### 10. Restriction round-trip — split-case visibility

The split scenario (10 standalone appointments, 10 different rooms,
one guest lecturer week). Demonstrates both shapes side-by-side —
listview reads `appointments[].allocatables`, editor saves from
`allocations[]`.

```graphql
{
  reservation(id: "<split-lecture-id>") {
    # listview / calendar shape
    appointments {
      id start
      allocatables { displayName }                   # what's booked at THIS appointment
    }
    # editor shape — restriction structure
    allocations {
      allocatable { displayName }
      appointmentIds                                 # null = all; non-null = restricted
    }
  }
}
```

Expected response shape for the split case:

```json
{
  "appointments": [
    { "id": "appt1",  "start": "...", "allocatables": [{"displayName": "Room A"}, {"displayName": "Smith"}] },
    { "id": "appt2",  "start": "...", "allocatables": [{"displayName": "Room B"}, {"displayName": "Smith"}] },
    { "id": "appt7",  "start": "...", "allocatables": [{"displayName": "Room G"}, {"displayName": "Jones"}] },  // guest week
    ...
  ],
  "allocations": [
    { "allocatable": {"displayName": "Room A"}, "appointmentIds": ["appt1"] },
    { "allocatable": {"displayName": "Room B"}, "appointmentIds": ["appt2"] },
    ...
    { "allocatable": {"displayName": "Smith"},  "appointmentIds": ["appt1","appt2",...,"appt10"]  /* minus appt7 */ },
    { "allocatable": {"displayName": "Jones"},  "appointmentIds": ["appt7"] }
  ]
}
```

The `appointments[].allocatables` is server-side derived from
`allocations` per the rule in PRD 035 §"Consumer-driven read surfaces":
`appointmentIds == null || appointmentIds.contains(this.id)`.

## Plan

All design questions resolved (see Locked decisions + Open questions
sections above).

**Cross-PRD dependency:** the **β read simplification** (drop
`Classification.attributes` + `DynamicType.attributes`, introspection
+ directives in place of descriptor data) is owned by
[PRD 035 Phase 2](035-rapla-mcp-server.md#phase-2--classification-schema-generation)
items 5-7. It is not gated on the 055 Reservation surface and can ship
independently — but the example queries in this PRD (specifically
query 1, editor open) reflect the post-β shape, so a pre-β
implementation must use the pre-β `classification.type.attributes`
selection if shipping before β lands. After β lands, the typed
`<ResTypeKey>Classification` fragment + introspection are the only
attribute-shape consumer paths.

Implementation steps:

1. **Schema additions** in `schema.graphqls` — `Reservation`, `Appointment`,
   `RepeatingRule`, `AppointmentBlock`, `Allocation` per the Q4 lock:
   `Reservation.allocations[]` (restriction-aware), `Appointment.allocatables[]`
   (pre-resolved per-appointment view — workhorse), `Appointment.blocks(from:, to:)`
   sub-resolver, `AppointmentBlock` without `allocatables` field.
2. **ReservationFilter input** with mandatory window, default + hard-cap
   limits (lessons from allocatables perf round — PRD 055-Cut-C).
3. **`ReservationGraphQLController`** in `rapla-app/src/main/java/org/rapla/server/spring/graphql/`:
   - `@QueryMapping` for `reservation(id:)` and `reservations(filter:)`
   - Goes through `StorageOperator.queryAppointments(...)` per the existing
     TableViewController pattern (line 116-127 there)
   - §12 inline filter: `PermissionController.canRead(reservation, user)` at
     output boundary; per-allocation `canRead(allocatable, user)` filter (drop,
     not stub — AGENTS.md §12 rule 4)
   - `@SchemaMapping` resolvers for simple derived fields (`displayName`,
     `firstDate`, `lastDate`, `canModify`)
4. **`Appointment.allocatables` resolver** — the per-appointment view, server-side
   restriction resolution. For each appointment, walk parent
   `Reservation.allocations` and return allocatables where
   `appointmentIds == null || appointmentIds.contains(this.id)`. Per-allocatable
   `canRead` filter applied here too (§12 rule 4 — drop unreadable). This is the
   workhorse resolver for listview / iCal / calendar queries; the only
   `Reservation.allocations` consumer is the editor.
5. **Recurrence resolver** — `Appointment.blocks(from:, to:)` materializes
   block list via existing `Appointment.createBlocks(...)` (TableViewController
   line 159-163). `AppointmentBlock` carries `{ appointment, start, end, isException }`
   only — no `allocatables` (consumers traverse `block.appointment.allocatables`).
6. **Tier-3 tests** — MockMvc + `HttpGraphQlTester`, fixtures via testdefault.xml:
   - §12 leak: non-admin can't see admin-only reservations
   - Window enforcement: query without `from`/`to` → error
   - Window cap: query with span >365 days → error
   - Default limit applied when none specified
   - Restriction model round-trip: split case (10 appointments, mixed lecturers)
     surfaces correctly through both `Reservation.allocations` (editor) and
     `Appointment.allocatables` (listview)
   - Generated typed-classification: `... on LehrveranstaltungClassification { ... }`
     pulls real DHBW lecture attributes

## Tests (tier-3 MockMvc spec)

Per AGENTS.md §10, this surface is tier-3 (`@SpringBootTest` +
`@AutoConfigureMockMvc(addFilters=false)` + `@WithMockUser`).

**Restriction resolution (the workhorse assertion):** assert
`Appointment.allocatables` equals the join of `Reservation.allocations`
where `appointmentIds == null || appointmentIds.contains(this.id)`.
Specifically:

1. Reservation-wide allocation (`appointmentIds: null`) appears in
   every appointment's `allocatables` list.
2. Restricted allocation (`appointmentIds: ["appt3"]`) appears ONLY in
   appointment "appt3"'s list.
3. Per-allocatable §12: an allocatable the user can't read is dropped
   from `Appointment.allocatables` AND from `Reservation.allocations`
   (both output boundaries).

The TableViewController tier-3 tests at
`rapla-app/src/test/java/.../TableViewControllerTest.java` (if any) are the
closest analogue.

## Deferred / next PRDs

- **Reservation mutations** (`createReservation`, `updateReservation`,
  `bulkCreateReservations`, etc.) — PRD 035 §6 has the design; needs its
  own PRD for the implementation cuts.
- **Conflicts** — `conflicts(reservationId:)` + `Reservation.conflicts`
  field. Derived data; non-trivial perf considerations.
- **Templates** — `rapla:template` is currently filtered out by the
  rapla-internal SDL filter. Adding templates means special-casing them
  back in or designing a separate `templates: [Template!]!` query root
  per PRD 035 §"New query roots" (line 254).
- **`renderedBlocks`** — server-side calendar/table projection per PRD 030.
  Different surface (HTML/JSON projections), not raw GraphQL.

## Decision log

- 2026-05-27 — PRD opened. Q1-Q3, Q5 locked per session discussion. Q4
  open (Allocation model A vs B vs C vs D vs E). Discussion to continue
  in this PRD.
- 2026-05-27 — Q4 (allocation model) **resolved**: two-shape exposure
  — `Reservation.allocations[]` (restriction-aware, editor-only) +
  `Appointment.allocatables[]` (pre-resolved, listview/iCal/calendar).
  Block has no `allocatables` field. Driven by use-case framing:
  editor needs lossless restriction round-trip; listview ("all
  reservations for user X / for resource Y, with per-appointment
  allocation visibility") needs server-side restriction resolution.
  Architectural rationale committed to PRD 035 + domain-model.md
  same session.
- 2026-05-27 — OQ2-OQ5 **resolved**: exceptions stay on
  `RepeatingRule.exceptions: [LocalDate!]!`; `Reservation.canModify`
  ships eager; `firstDate`/`lastDate` ship as convenience fields;
  conflicts deferred to a separate `checkConflicts(...)` top-level
  query (its own PRD).
- **2026-05-28 — β read simplification (further than Q4)**: drop
  `attributes: [AttributeValue!]!` from `Classification` interface and
  `AttributeDescriptor` from `DynamicType.attributes`. SPA uses dynamic
  query construction via introspection + the typed
  `<TypeKey>Classification` types; descriptor data lives in the schema
  itself (field types) + custom directives (`@expectedType`,
  `@multiplicity`, etc.). One source of truth. Implementation refactor
  pending — design locked.
- **2026-05-28 — symmetric β² (writes mirror reads)**: PRD 056 adopts
  typed per-DynamicType classification inputs; same hot-swap surface,
  same one-schema-two-modes consumption pattern in both directions.
