# PRD 091 — SPA reservation editing & availability search

**Status:** draft — 2026-07-05 (updated 2026-07-06: equipment-lending archetype prioritized as first implementation target)
**Related:** PRD 024 (server-side edit services — the `/api/edit` REST trio), PRD 026 (Angular umbrella), PRD 056/057/063 (GraphQL mutations, shipped), PRD 060 (GraphQL MCP foundations — designed `whoIsFree`/`findFreeSlots`/`checkConflicts`, unbuilt), PRD 067 (mutation unification, D7: GraphQL write surface still adjustable — SPA is the first real consumer), PRD 077/078 (view model + renderer, the read side), PRD 086 (appointment block index — the availability substrate), **PRD 092 (free-slot search — the fixed-resources/variable-time axis, split from this PRD)**, **PRD 093 (loan lifecycle — status/blocking rules the availability query must honor)**

**Focus (clarified 2026-07-06): the sheet is the GENERAL event editor** — the
lending archetype (`docs/usecases/equipment-planning.md`, UC-C1/C2) is a *special
case* (one event type among many, plus a deployment table lens), not the design
driver. It drives only the *ordering*: UC-C2 ("free camera Mon–Fri") is
`resourceAvailability` with a fixed multi-day window — *not* PRD 092, which stays
parked; loans are single-appointment, so the finder (C) moves ahead of the matrix
(D) and the sheet ships its single-appointment slice before the recurrence editor.
**Every Swing dialog capability keeps a place in the design**
(`docs/architecture/reservation-edit-ui-inventory.md` is the checklist): the
when-axis is a *list* of appointments from day one (Phase 2 fills it with one),
each allocation shows its restriction ("gilt für …") from day one (editing comes
with the Phase 4 date picker), and the sheet structure must not preclude
recurrence, convert-to-single, holiday exceptions, permissions, or the request
workflow.

## Abstract

Bring reservation *editing* to the Angular SPA — the first write surface after the
read-only view renderer — and, as its foundation, give the server a real
**resource-availability API** (free/partially-free resources for a series, with the
per-occurrence conflict matrix). The Swing dialog's weakest points (finding free
resources, assigning resources to occurrences on long series) become the SPA's
strongest, built on capabilities the market doesn't have (per-occurrence assignment
via the sparse restriction map). The complementary axis — finding free *time* for a
fixed resource set — is a separate design problem (new gap-enumeration algorithm,
its own UX) and lives in **PRD 092**.

Requirements ground truth: `docs/usecases/reservation-editing.md` (UC-E1…E16).
Swing capability checklist: `docs/architecture/reservation-edit-ui-inventory.md`.

## Current state (research 2026-07-05)

- **GraphQL writes exist, unused:** `createReservation` / `updateReservation`
  (full-state + `expectedLastChanged`) / `deleteReservations` / `applyChanges`
  ship server-side; the SPA has **no** mutation method in `graphql.service.ts` and
  no edit component at all. PRD 067 D7: the mutation contract may still be reshaped
  for its first real consumer.
- **Availability primitives exist only as legacy RPC:** `getAllocatableBindings`
  (busy-map for candidate resources × proposed appointments,
  `LocalAbstractCachableOperator:4399`) and `getNextAllocatableDate` (brute-force
  linear scan up to a year, `:4451`) over `/api/storage/*` — not in GraphQL. PRD 024
  added `/api/edit/check-conflicts` (proposed-reservation dry-run, §12-filtered),
  `/validate-recurrence`, `/expand-blocks`.
- **No free-resource or multi-slot search anywhere.** "Which rooms are free in
  window W" requires client-side inversion of the busy map; "give me ranked
  candidate slots" doesn't exist (only first-hit next-free-date).
- **PRD 086 block index** (behind `rapla.readmodel.authoritative`) provides the
  in-memory interval index an efficient availability resolver should use.
- **Pure-Java edit models** (PRD 023/024: `RepeatingRuleValidator`,
  `AllocationConflictModel`, `AllocatableRowStatusModel`, `ExceptionListMutator`)
  encode the domain rules and stay the single source of truth.

## Market/UX research summary (2026-07-05, web research)

- Outlook Scheduling Assistant / Room Finder: canonical free-busy grid + filtered
  room list, but **blind to recurrence** — a room conflicting on one date declines
  the whole series; the only mitigation is server policy
  (`ConflictPercentageAllowed`). Google "Find a time": ranked slot suggestions,
  single-occurrence only.
- Add-on "Resource Central" is the closest to our "partially free" need: tri-state
  per room (free / *bookable for some of the series* / blocked) with book-only-the-
  free-dates.
- Untis/UniTime (timetabling) treat per-occurrence room overrides as the *normal
  case*; UniTime's Suggestions page annotates each alternative with the conflicts it
  would create.
- **Nobody ships an occurrence×resource assignment matrix** — rapla's sparse
  restriction map is the native model for it; genuine differentiator. Likewise
  automated **split-booking suggestions** ("room A for 8 dates, room B for the 2
  clashes") exceed the state of the art and are cheap on our model (one
  reservation, restriction-map patch — no series split).

## Goal

1. A GraphQL resource-availability query: given a proposed/existing event, the SPA
   can get ranked candidate resources with per-occurrence free/busy — enough to
   drive the finder, the occurrence×resource matrix and split suggestions — all
   §12-scoped, all side-effect-free (§16).
2. An SPA event sheet that creates and edits reservations end-to-end via the shipped
   GraphQL mutations, covering the UC-E checklist (minus the consciously deferred
   items), verified by tier-3 leak tests + tier-5/6 component tests + one tier-7
   happy path.
3. Measurable: a planner can find a room free for ≥8/10 occurrences and assign
   it to exactly those dates in ≤4 interactions.

## Proposals

Ordered; A+B are the foundation, C/D/F are UI surfaces consuming them (E moved to
PRD 092). Each UI proposal is independently shippable.

### A — GraphQL availability queries (server foundation)

Extend the schema (aligning with PRD 060's sketch so MCP gets it for free).
**v1 (2026-07-06) is appointment-granular — exactly the Swing semantics** of
`getAllAllocatableBindings`: per candidate, *which appointments* clash. No block
enumeration, no evaluation window — series overlap is computed analytically on the
repeating rules (works for endless series), just like the Swing picker.

Two queries with two call profiles, mirroring Swing's split between the cheap
picker display (has-conflict yes/no, first-conflict early exit) and the exact
conflict list computed at save time:

```graphql
type Query {
  # CHEAP — the finder/picker display path: many candidates, yes/no per
  # (candidate × appointment), first-conflict early exit.
  resourceAvailability(input: AvailabilityInput!): [ResourceAvailability!]!

  # EXPENSIVE — the drill-down / save-preflight path: full conflict list for
  # concrete resources (typically one chip-click or the allocated set).
  # Same result type as the realized `conflicts(reservationId:)` query.
  potentialConflicts(input: PotentialConflictInput!): [Conflict!]!
}

input AvailabilityInput {
  reservationId: ID              # existing event…
  appointments: [AppointmentInput!]  # …or proposed (same input as mutations;
                                     # id REQUIRED here — see D3)
  candidates: CandidateInput!    # @oneOf: filter (finder) | ids (allocated set)
  ignoreReservationIds: [ID!]    # self-ignore while editing
}
input CandidateInput @oneOf {
  filter: AllocatableFilter      # typed/attribute rule, §12-scoped (proposal C)
  ids: [ID!]                     # the allocated set
}
type ResourceAvailability {
  allocatable: Allocatable!
  status: AllocationStatus!          # AVAILABLE | PARTIAL | CONFLICT | REQUEST_ONLY | FORBIDDEN
  conflictingAppointmentIds: [ID!]!  # own appointments, yes/no (early exit)
}

input PotentialConflictInput {
  reservationId: ID              # existing event being edited (resolves reservation1)
  appointments: [AppointmentInput!]  # the draft (id REQUIRED — D3)
  allocatableIds: [ID!]!         # concrete resources, typically one
  ignoreReservationIds: [ID!]
}
```

**One `Conflict` type for realized AND potential conflicts (2026-07-06).** A
potential conflict is a `Conflict` whose side 1 is not persisted (yet) — same
symmetric pair semantics, different realization state. The shipped PRD 064 type is
reshaped into an id-based superset (SPA is the sole consumer — cheap now, expensive
later, PRD 067 D7 spirit):

```graphql
type Conflict {
  allocatable: Allocatable!
  reservation1Id: ID!   appointment1Id: ID!
  reservation2Id: ID    appointment2Id: ID   # null only when masked (§12, potential)
  appointment1: Appointment!                 # realized: from store; potential:
                                             # materialized from the input (PRD 024
                                             # transient-appointment pattern) — shows
                                             # the DRAFT state, never null
  reservation1: Reservation                  # null only for a brand-new draft (input
                                             # carries appointments, no reservation)
  reservation2: Reservation                  # ┐ null when unreadable (§12)
  appointment2: Appointment                  # ┘
  description: String!                       # server-built, §12-scoped display text
  startDate: LocalDateTime!                  # first clash date — both cases, via
                                             # ConflictImpl.getFirstConflictDate
}
```

- **Symmetry is load-bearing for realized conflicts** (overview queries — "all
  conflicts visible to me" — have no natural "my side"); the perspectival queries
  (`conflicts(reservationId:)`, `potentialConflicts`) use the convention side 1 =
  queried reservation / draft. Every remaining nullability is semantic, not
  technical: `reservation1` null = brand-new draft; `reservation2`/`appointment2`
  null = §12-masked.
- **§12: drop vs. mask.** `conflicts(reservationId:)` keeps dropping unreadable
  conflicts entirely (PRD 064 contract). `potentialConflicts` must NOT drop — the
  resource IS busy — it masks: side 2 fields null, `description` generic ("belegt").
  Readable counterparty → name + time in `description` (server-localized).
- **Continuity across save:** appointment ids are stable (D3), so the potential
  conflict `(a1, a7)` becomes, after save, the realized `Conflict` carrying the same
  id pair — the UI can correlate pre-save warnings with post-save conflicts 1:1.
- **Granularity:** one entry per (own appointment × foreign appointment); several
  clashes on the same own appointment → several entries; UI groups by
  `appointment1Id`.
- **No `bookable` flag (2026-07-06):** whether a CONFLICT can be booked through
  (`allocate_conflicts`) is not availability data — save-time validation rejects
  hard when the permission is missing; the UI treats CONFLICT as "warn but allow".
- **Permission-window violations** (ALLOCATE outside permitted window,
  minAdvance/maxAdvance) are folded into `ResourceAvailability.status`
  (FORBIDDEN/REQUEST_ONLY) — they are NOT conflicts and never appear in
  `potentialConflicts` (no counterparty exists). Consequence: every
  `potentialConflicts` entry has a real side 2 (possibly masked).
- **Why no `window` / no blocks in v1:** the window existed only to cap *occurrence
  enumeration* (fraction display "8/10", matrix cells) — features of the deprioritized
  matrix phase. Swing never needed either; the equipment slice (single appointments)
  makes them pure ceremony. Dropped per Simplicity First.
- **Evolution path (reserved, additive):** when the matrix (Phase 5) or the fraction
  display needs per-occurrence detail, add a sub-field
  `ResourceAvailability.occurrences(window: TimeWindow!)` — the window moves to where
  enumeration actually happens and is mandatory only there. Block identity stays the
  reserved convention `(appointmentId, blockStart)` (index-based identity remains
  rejected: draft edits between two calls shift indexes and misattribute conflicts).

Implementation: both resolvers compose `getAllAllocatableBindingsSync` +
`AllocationConflictModel` — exactly the service path `/api/edit/check-conflicts`
uses today; **no parallel conflict logic**. `resourceAvailability` stops at the
first conflict per (candidate × appointment); `potentialConflicts` enumerates fully
and computes `startDate` per pair. Statuses reuse `AllocatableRowStatusModel`
semantics incl. hold-back-conflicts annotation. §12: candidates come pre-filtered
like the Swing picker (canAllocate ∪ requestOnly). Mandatory tier-3 leak tests per
the data-leak-prevention rules. Reshaping the `Conflict` type touches
`ConflictGraphQLController` (@SchemaMapping fields) + the SPA's existing `conflicts`
consumers — audit them in the same change.

**Shared schema vocabulary (PRD 092 must reuse, not redefine):** `TimeWindow`
(defined with the first consumer — PRD 092 or the future `occurrences` field,
whichever lands first), `AppointmentInput` (already shared with mutations), the
block-identity convention, `AllocationStatus`, and — when PRD 092 introduces worktime constraints —
`WorktimeInput` is defined there and referenced here if ever needed.

### B — SPA mutation layer + event sheet skeleton

`graphql.service.ts` gets `mutate<T>(document, variables)`; a deep-linkable event
sheet route (`/app/event/:id` + `/app/event/new`) with the three axis sections
(what / when / with-what), full-state load → edit draft → `updateReservation` with
`expectedLastChanged`; CONCURRENT_MODIFICATION → reload-and-reapply dialog
(UC-E15). Recurrence editing reuses `/api/edit/validate-recurrence` +
`/expand-blocks` (occurrence preview list). Read-only mode from `canModify`.

**Add mode = the Swing two-pane, with pins (mockup round 2026-07-06, maintainer
direction):** clicking "+ Ressource…" expands an add mode that mirrors the Swing
dialog's Belegungen area spatially — **the appointment list stays visible on top**
(editable during the mode; a change re-checks Auswählbar + pins immediately),
and **exactly ONE Zugeordnet list** — the section's own, keeping its full
"gilt für" control (= Swing's "ausgewählt an"); the mode expands only a
full-width "Auswählbar" list below it (tree/search; every entry carries its live
tri-state status from `resourceAvailability` with the draft appointments =
Swing's "auswählbar an" column; internal scroll area keeps the Zugeordnet list in
view). "→ Zuordnen" moves the hit up into the list (brief highlight). A separate
right-hand Zugeordnet pane was rejected (maintainer 2026-07-06): it duplicated
the assigned list and would have to cram the gilt-für/date-picker controls into a
narrow column — the Swing left/right metaphor becomes below-choose/above-chosen.
**Pins are part of the search, not a separate box** (maintainer 2026-07-06 — the
two-part split was awkward): ★ sticks a row to
the top of the Auswählbar list where it survives search/filter changes — pin two
cameras, then search rooms while the cameras stay visible. **Pins (☆)** are the
comparison intermediate: candidates of interest across types (rooms AND cameras at
once), re-checked on every draft change (`candidates.ids` = pins, debounced),
promotable to an allocation with one click. Closing the mode leaves only the
Zugeordnet list (the plain sheet). Pins are draft-local comparison state, never
persisted. **The add mode does NOT change the route/URL** (2026-07-06) — it is
pure UI state of the draft, not a navigation step; same for the expandable header
("▾ weitere Attribute" unfolds the remaining classification fields in place).
**Collapse rules (locked 2026-07-06):** the add mode closes only explicitly
("Fertig" button / Esc) plus implicitly on a successful save; it never
auto-collapses after a single "→ Zuordnen" (multiple assignments in a row are the
normal flow). The expanded header ("weitere Attribute") toggles only explicitly
and stays as the user set it for the lifetime of the draft — no accordion
coupling between header and add mode (implicit collapsing that removes input
context reads as a bug). Both states are draft-local: reopening the sheet starts
collapsed.
Open: auto-recheck vs. on-demand; draft-local vs. per-user-remembered
pins (PRD 089 favorites adjacency).

**Entry points (locked 2026-07-06):** (a) row edit in the table view — a row
carrying an event id opens `/app/event/:id` directly; a row carrying only an
appointment id resolves its owning reservation first (client-side from the row's
data where present, else one lookup) and opens that event's sheet; (b) a "new"
action (`/app/event/new`) — for the lending desk a toolbar "Neue Ausleihe" button
on the table lens. No calendar-drag entry in this phase (PRD 077's render mode
owns that).

### C — Tri-state resource finder (UC-E5; Swing picker successor)

Panel inside the with-what section: attribute/type filter (the README "group" rule)
+ ranked result list, each row a gauge — "✔ 10/10 free", "◐ 8/10", "✖ blocked",
"🔒 request-only" — sorted by free fraction, expandable to the clashing dates.
Selecting a partial resource offers **"assign to the 8 free dates only"** (writes
the restriction map). Consumes `resourceAvailability`.

### D — Assignment editing: per-resource date picker (workhorse) + matrix (special case)

**Reframed 2026-07-06.** Two insights shrank this proposal:

1. **Edit granularity is the appointment, not the occurrence** — the restriction map
   assigns resource → appointments; a single occurrence can only deviate by splitting
   the series (exception + new appointment). And since in practice recurring series
   are mostly broken down into single appointments anyway (UC-E12 is the timetabling
   norm — individual dates get moved), appointment granularity *is* date granularity,
   and the fraction display ("free on 8/10 dates") falls out of v1's
   `conflictingAppointmentIds` with no block enumeration.
2. **The standard edit flow is per-resource, not matrix-wide:** click the clashing
   resource's chip → date list with conflict marks → toggle checkboxes.

**The workhorse (sheet allocation section, Phase 4):** per-resource **date picker** —
"applies to: all / selected appointments", each entry showing its conflict state from
the same `resourceAvailability` call. The modernized Swing checkbox popup; covers
UC-E7 and the manual half of UC-E8.

**The matrix (stretch):** appointments × resources overview with toggle cells, column
all/none, row selection → "find alternative for these dates" (jumps to C). A *special
case* surface — progressive disclosure, offered only for events with ≥2 appointments
and non-uniform assignment (complex multi-resource split bookings, read-only "who is
in when" overview of big series). Not a core edit surface; loans (1 appointment) and
the default empty restriction map (all-on-all) get nothing from it.

### E — Ranked free-slot finder → moved to PRD 092

The fixed-resources/variable-time axis (UC-E6, the "free appointment >>" successor:
`freeSlots` query + slot-finder UI + heatmap/availability-strip increments) is a
separate design problem — new gap-enumeration algorithm over the block index, its
own mode question (concrete slots vs. weekly-pattern search) — and lives in
**PRD 092**. No dependency in either direction: C/D/F consume only
`resourceAvailability`; PRD 092's UI can land before or after this PRD's phases 3+.

### F — Split-booking repair suggestions (UC-E8 stretch)

On a partial assignment, a one-click chip: "Room 042 for 8 dates + Room 043 for the
2 remaining — apply?" Server ranks alternatives only for the conflicting blocks
(attribute-similar, same type) and returns a restriction-map patch the client
applies to the draft (still one save). Reuses A's query scoped to conflict blocks +
a similarity ranking. Beyond current market state; explicitly a later phase.

### Lightweight variants considered (not primary)

- **Room-lane day timeline** for single-occurrence repair (drag block to another
  room lane) — powerful but needs the calendar surface; defer until PRD 077 ships.
- Availability strip while dragging + weekday×hour heatmap → moved to PRD 092
  (they consume `freeSlots`-side data).

## Scope

### In scope
- GraphQL `resourceAvailability` (+ tier-3 leak tests)
- SPA mutation layer, event sheet (three axes), recurrence editor with occurrence
  preview, exceptions (incl. cancel-one-occurrence UC-E4)
- Resource finder (C), per-resource date picker (D workhorse); matrix only as
  stretch (D special case)
- Request-only workflow status surfacing (UC-E9)

### Out of scope
- **Free-time search (UC-E6): `freeSlots` query, slot finder, heatmap,
  availability strip → PRD 092**
- Templates + multi-event batch edit (UC-E16) — Swing keeps them
- Split-booking suggestions (F) — stretch/follow-up phase
- Calendar drag-editing — belongs to PRD 077's render mode
- Swing changes of any kind; permissions *editor* parity beyond read/gate (UC-E11
  minimal: show, edit only for `canAdmin`)
- Undo/redo stack parity — draft-local editing + abort covers UC-E14's core

## Plan

Reordered 2026-07-06 for the equipment-lending target: finder before matrix,
single-appointment sheet before recurrence. UC-C2 (candidates = `typeKeyIn`, one
multi-day appointment) is the acceptance case for Phases 1+3.

### Phase 1 — Availability API (A) — DONE 2026-07-06
- [x] Reshape `Conflict` type to the id-based superset (PRD 064 follow-up). SPA audit:
      zero Angular consumers existed — no migration needed. `@SchemaMapping` field
      resolvers replaced by the materialized `ConflictRow` record (both queries share it);
      realized rows are side-normalized (side 1 = queried reservation).
- [x] `resourceAvailability` resolver (`AvailabilityGraphQLController`) composing
      `getAllAllocatableBindingsSync` + `AllocationConflictModel`; candidates via
      `CandidateInput @oneOf` (filter delegates to the §12-scoped `allocatables(filter:)`
      resolver; ids resolved with hidden ≡ nonexistent drop). Appointment ids REQUIRED
      (loud REQUIRED error); `repeating` rejected loudly (UNSUPPORTED — the mutation path
      doesn't materialize recurrence yet either, PRD 056 v1).
- [x] `potentialConflicts` resolver (full enumeration; `startDate` via
      `ConflictImpl.getFirstConflictDate`; side-1 appointments materialized from input;
      §12 masking: unreadable counterparty → side-2 fields null + `not_visible` i18n text)
- [x] §12 leak tests: anonymous → UNAUTHENTICATED; mixed visible/nonexistent candidate
      ids → byte-identical to visible-only (hidden ≡ nonexistent). **Deferred:**
      masked-counterparty tier-3 test — testdefault.xml gives the event type
      `read=everyone` and mutations can't set restrictive permissions, so an
      unreadable-to-monty reservation needs a fixture extension (restricted-read event
      type) first; PRD 064 precedent.
- [x] Integration test: UC-C2 shape — `typeKeyIn` filter, single multi-day appointment
      (`AvailabilityGraphQLControllerTest.ucC2FinderSingleMultiDayAppointment`) +
      drill-down counterparty test. GraphQL package suite green (244 tests).

### Phase 2 — SPA write foundation (B), single-appointment slice

Detailed plan locked 2026-07-06 (full-state per OQ1; entry points per proposal B).
Order matters — each step is independently verifiable before the next starts.

- [ ] **2.1 `mutate<T>(document, variables)`** in `graphql.service.ts` — same
      transport as `query()`, plus mapping of the typed error codes
      (`VALIDATION_ERROR` extensions, `PERMISSION_DENIED`,
      `CONCURRENT_MODIFICATION`) into a discriminated result the sheet can act
      on. Tier-5 tests (pure TS, no TestBed): success path, each error code,
      network failure.
- [ ] **2.2 Draft model + id generation** — `EventDraft` (pure TS): classification
      values, an appointment LIST (Phase 2 UI creates one entry, the model carries
      n — Swing parity), allocation list incl. read-only restriction info;
      client-generated typed UUIDs at draft creation (D3: `crypto.randomUUID()`,
      'e'/'a' prefix convention); mapping draft → `createReservation` /
      `updateReservation` input (full-state) and GraphQL reservation → draft
      (load). Tier-5 tests: new-draft ids present, round-trip mapping, dirty
      tracking for abort-without-trace (UC-E14).
- [ ] **2.3 Event sheet route + skeleton** — `/app/event/new` + `/app/event/:id`,
      deep-linkable. UI labels (locked 2026-07-06, mockup round): a header area
      (name + event type, NO section label) plus two sections **"Termine"** and
      **"Ressourcen"** — the what/when/with-what axis vocabulary stays
      analysis-level, never on screen. The header starts as a compact
      display-only line (name · type); clicking into it springs open the edit
      fields incl. "weitere Attribute" (locked 2026-07-06).
      **Active-section principle (locked 2026-07-06):** the sheet renders as
      quiet summary sections; only the section being worked in (click /
      `:focus-within`) shows its edit affordances (✎, "+ Termin", "gilt für"
      dropdowns, ✕, "→ Zuordnen") and gets the primary-color border. Two
      guardrails: (1) information is never hidden — status pills, "gilt für"
      values, conflict markers stay visible in quiet sections; only ACTIONS
      appear on activation; (2) no layout shift (actions fade in with reserved
      space) and touch/keyboard work without hover (section tap activates;
      focus-within activates; each quiet section keeps a subtle ✎ in its title
      as the discoverability anchor). Orthogonal to the collapse rules: "active"
      governs affordance visibility, not expansion. Read-only mode from
      `canModify` = no section can become active. Load = full-state query; save = full-state mutation;
      cancel = drop draft, no server contact. Tier-6 tests only for template
      bindings (axis sections render, read-only disables inputs).
- [ ] **2.4 What-axis form** — DynamicType-driven fields for the event type
      (name attribute suffices for the loan type); reuse the existing
      classification rendering conventions from the read views.
- [ ] **2.5 When-axis (appointment list, single-entry slice)** — rendered as a
      LIST (Swing parity: the dialog's Termine list) with "+ Termin" and a
      disabled recurrence affordance; Phase 2 supports exactly one entry
      (from/to date-time, multi-day capable). Full multi-entry editing +
      recurrence: Phase 4.
- [ ] **2.6 With-what minimal** — add/remove allocations via the existing
      `search` query (person + items for UC-C1); chips list; no finder yet
      (Phase 3 replaces the picker interior, the section shell stays).
- [ ] **2.7 Concurrency** — `updateReservation` with `expectedLastChanged`;
      CONCURRENT_MODIFICATION → reload-and-reapply dialog (UC-E15). Tier-5 test
      for the reapply merge (fresh lastChanged + kept draft edits).
- [ ] **2.8 Table-lens entry points** — row edit action (event id direct;
      appointment-id rows resolve the owning event) + "new" toolbar action on
      the table lens (UC-C1 "Neue Ausleihe").

Out of this slice (explicit): recurrence editor (Phase 4), finder interior
(Phase 3), availability strip, permissions tab beyond read-only gating,
loan-status chip (PRD 093 Phase 3 — the sheet must merely not preclude it).

### Phase 3 — Resource finder (C)
- [ ] Tri-state finder over `resourceAvailability` (UC-E5 / UC-C2)

### Phase 4 — Multi-appointment editing (when-axis completion + assignment)
- [ ] recurrence + exceptions in the sheet (validate-recurrence / expand-blocks preview)
- [ ] per-resource date picker in the allocation section ("applies to: all / selected
      appointments", conflict marks per entry — the UC-E7 workhorse, see D)

### Phase 5 (stretch) — Matrix (D, special-case overview) + split suggestions (F)
- [ ] matrix only as progressive disclosure for ≥2-appointment events with
      non-uniform assignment

## Tests

- Tier 1/2: ranking + status functions over `AllocatableRowStatusModel` fixtures
- Tier 3: MockMvc/GraphQL leak tests per data-leak-prevention (mandatory for A)
- Tier 5/6: matrix cell logic, finder ranking rendering, draft/concurrency flows
- Tier 7: one Playwright path — create event → find partial room → assign to free
  dates → save → conflict badge count

## Open Questions

- **OQ1** — Does `updateReservation` (full-state) suffice for the sheet, or does the
  matrix want a fine-grained `updateAllocations` patch mutation (PRD 067 D7 allows
  reshaping)? *Resolution 2026-07-06:* **full-state** for the Phase 2 slice — the
  sheet holds the complete draft anyway, full-state keeps abort free (UC-E14) and
  `expectedLastChanged` gives clean CONCURRENT_MODIFICATION semantics. A patch
  mutation is reconsidered only when the date picker / matrix (Phase 4/5) shows a
  real need.
- **OQ2** — Finder ranking: free-fraction only, or attribute fit (capacity vs.
  enrolled) too? UniTime-style consequence annotations ("free, but capacity 20 <
  35")? *Resolution:* pending.
- **OQ3** — Should `resourceAvailability` require the PRD 086 flag
  (`rapla.readmodel.authoritative`) or fall back to the legacy per-allocatable scan?
  *Resolution:* pending — likely fallback with the index as fast path.
- **OQ4** — Where does UC-E4 (cancel one occurrence) live on mobile: sheet or a
  block context action in the view host? *Resolution:* pending.
- **OQ5** — Request-only (UC-E9): does the SPA v1 surface REQUESTED as read-only
  status or full request workflow? *Resolution:* pending.

## Decisions locked

**D1 — Availability API is GraphQL, not REST (2026-07-05).** The SPA is
GraphQL-only for data (AGENTS.md §14), PRD 067 D3/D4 make GraphQL the server API,
and MCP clients (PRD 060) get the query for free. Rejected: extending
`/api/edit/*` (would reopen a second data transport in the SPA and be rebuilt on
GraphQL later anyway); exposing the legacy `/api/storage` RPC (Swing wire protocol,
raw `ReservationImpl` payloads — §12 leak-by-design for a browser client).
Constraint: the resolver is a thin layer over the PRD 024 service path
(`getAllAllocatableBindingsSync` + `AllocationConflictModel`) — no parallel
conflict logic.

**D2 — Free-time search split out to PRD 092 (2026-07-05).** `freeSlots` shares no
implementation with `resourceAvailability` (new gap-enumeration algorithm vs.
composition of existing services), has no dependency link to the finder/matrix, and
carries its own unresolved mode question (concrete slots vs. weekly-pattern
search). Splitting keeps 091 shippable on the core pain (UC-E5/E7/E8). Shared
schema vocabulary stays defined in 091 (see proposal A).

**D3 — SPA drafts are id-first (2026-07-06).** The SPA generates entity ids
client-side at draft creation (`crypto.randomUUID()`; the server normalizes the
rapla type-prefix) and always submits them — for appointments *always*, even
though the mutation contract only requires them when subset restrictions are
present. Why: (a) the restriction map joins on appointment ids — subset
allocations at create time are inexpressible without them (the matrix's normal
case); (b) stable matrix/availability identity across draft edits (index-based
identity is fragile); (c) create becomes retry-idempotent (PRD 056 OQ5).
Swing's server-roundtrip id allocation (`/api/storage/identifier`) is NOT
copied — rapla ids are stateless typed UUIDs, so client generation is safe.
The *contract* rule (ids optional for third parties, subset restrictions force
appointment ids) and the `checkIdIntegrity` operator guard (collision +
foreign-reservation appointment ids, §12-uniform rejection) are owned by
**PRD 056 locked decision §9** — this PRD only consumes them.
Alternatives rejected: server pre-allocation mutation (roundtrip per appointment
add, no gain — server ids are random UUIDs too, uniqueness comes from the
save-time guard either way); strictly required ids for all consumers (hurts
casual/MCP ergonomics for a feature they don't use).

**D4 — one `Conflict` type for realized and potential conflicts; detail lives in
`potentialConflicts`, not on `ResourceAvailability` (2026-07-06).** A potential
conflict is a `Conflict` whose side 1 is not persisted (yet) — same symmetric pair,
different realization state; the Java `Conflict` interface is already id-based
(`ReferenceInfo` pairs), so the GraphQL type is reshaped to the id superset with
semantic-only nullability (side 1 unresolved = brand-new draft; side 2 null =
§12-masked). `ResourceAvailability` shrinks to the cheap display path
(`status` + `conflictingAppointmentIds`, first-conflict early exit); the expensive
full enumeration is its own query `potentialConflicts`, symmetric in role to
`conflicts(reservationId:)`. Why: schema tells the realized/potential relationship;
call profiles (finder vs. drill-down/preflight) become explicit instead of hidden in
field selection; save-preflight needs no availability ceremony. Alternatives
rejected: separate `AppointmentConflict`/`PotentialConflict` type (duplicates the
pair semantics; hides the continuity of id pairs across save); asymmetric unified
type (breaks the overview queries, where no side is "mine"); lazy `conflicts` field
on `ResourceAvailability` (implicit cost model, misuse-prone).
