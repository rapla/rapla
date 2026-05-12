# PRD 024 — Server-side edit services (Angular precursor)

**Status:** in-progress —
- **Phase 1 (`validateRecurrence`)** DONE: `ReservationEditService` + `ReservationEditController` + `RecurrenceRule` / `RecurrenceValidation` DTOs. Wraps `RepeatingRuleValidator` (rapla-core, PRD 023 Phase 2).
- **Phase 2 (`checkConflicts`)** DONE: `ConflictCheckRequest` / `ConflictReport` / `AllocationOutcomeDto` / `AppointmentSpec` DTOs. Wraps `AllocationConflictModel.compute(...)` and pulls the allocatable bindings via the existing `facade.getAllocatableBindings(...)` path — no separate `AllocationBindingsLoader` was needed; the facade already does the equivalent server-side. AGENTS.md §12 leak-probe (unknown / unreadable ids silently dropped) covered.
- **Phase 3 (`/calendar/view`)** DONE: engine + controller + 16 tier-1 + 7 MockMvc tests (landed 2026-05-11; colours added 2026-05-12 via PRD 030 Phase 4 `BlockColors`).
- **Bonus** beyond original PRD: `expand-blocks` endpoint (`AppointmentSpec` → `AppointmentBlockDto[]`) — server-side implementation of `Appointment.createBlocks(...)` for the Angular form to enumerate concrete occurrences without porting the weekday-flip / exception-skip logic. PRD 026 §B4 hook.
- **Test totals**: 12 contract tests (`ReservationEditServiceContractTest`) + 15 MockMvc tests (`ReservationEditControllerIntegrationTest`), all green 2026-05-12.

Scope adjusted 2026-05-11: allocatable search + format service dropped; calendar layout added — see Considered & Rejected.
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-11

## Goal

Expose the **business logic of reservation editing** — conflict pre-check,
permission filtering, allocatable suggestion, recurrence-rule validation,
formatter / display helpers, classification-filter evaluation — as
**REST endpoints on the server**, so a future Angular (or any non-JVM)
client can do reservation editing without re-implementing twelve years
of subtle scheduling rules.

This PRD is the **companion of PRD 023**. 023 carves pure-Java models
out from under the Swing layer; 024 takes those same models, wraps
them in `@HttpExchange` interfaces, and serves them. The Swing client
keeps calling the in-process model directly (no behaviour change); the
Angular client calls the REST endpoint and renders the result.

## Why this is needed now

1. **The Angular rework will land in 12–24 months.** Building it
   against a "thin client → REST" architecture from day one — instead
   of re-porting Java logic to TypeScript — is the only way that
   project finishes. Every month we delay the server-side services is
   a month the Angular team will spend duplicating Java.
2. **Two adjacent PRDs already proved the pattern.** PRD 020
   (server-driven admin panels) and PRD 012 (external event import
   wizard) both ship the **server-publishes-metadata + client-renders**
   pattern. PRD 024 generalises it to the reservation-edit surface,
   which is the largest UI area not yet covered.
3. **Server-side authority is correct anyway.** Permission filtering
   on the client is a cosmetic optimisation, not a security boundary;
   the server already re-checks on save. Moving the pre-check to the
   server eliminates the client / server skew (the Swing client and
   the server today re-implement the same `canAllocate` logic, and
   they have drifted before).
4. **Closes PRD 005's dependency direction.** rapla-server today
   depends on rapla-client for `abstractcalendar` / `RaplaBuilder`
   (PRD 005 D3). The layout / formatting logic exposed by 024 should
   live in `rapla-core` (where 023 puts it) so rapla-server can call
   it without depending on rapla-client. Net effect: the back-edge
   becomes deletable in a future PRD.

## Scope

### In scope — three REST surfaces

| # | Surface | Wraps | Client use today | Angular use later |
|---|---|---|---|---|
| 1 | `/edit/check-conflicts` | `AllocationConflictModel.compute(...)` | `AllocatableSelection.paintAllocation` | Conflict-warning sidebar |
| 2 | `/edit/validate-recurrence` | `RepeatingRuleValidator.validate(...)` | `AppointmentController.RepeatingEditor.mapToAppointment` | Inline form validation |
| 3 | `/calendar/view` | `RaplaBuilder` + `GroupStartTimesStrategy`/`BestFitStrategy` + `RaplaBlock`/`HTMLRaplaBlock` | New (today the client builds blocks locally after fetching reservations) | Angular week / month / day view consumes pre-positioned tiles |

(1) and (2) wrap pure-Java models that already exist in rapla-core (PRD 023 Phases 2+3 landed 2026-05-11). (3) wraps the existing `RaplaBuilder` + layout strategies — they're already in rapla-core and headless; the server endpoint runs them server-side and serializes the result instead of shipping raw reservation entities for the client to re-layout.

### Why server-side calendar layout is the right call

The "no-op on round-trips" rationale: **the server queries the reservations for a date range either way**. Today's flow is:

```
client                           server
  │   GET /storage/reservations?from=…&to=…
  │ ────────────────────────────▶
  │                               (load reservations + permissions)
  │ ◀────────────────────────────
  │   (Reservation, Appointment, Allocatable entities + classifications)
  │
  │   RaplaBuilder.build(blocks)
  │   GroupStartTimesStrategy.layout()
  │   → positions, colors, overlap resolution
  │
  │   Swing/HTML renderer paints
```

The same query, returning **pre-positioned tiles** instead of raw entities:

```
client                                            server
  │   GET /calendar/view?from=…&to=…&strategy=group&…
  │ ────────────────────────────────────────────▶
  │                                                (load reservations)
  │                                                RaplaBuilder.build(...)
  │                                                strategy.layout(...)
  │ ◀────────────────────────────────────────────
  │   (RenderedBlock: id, slot, x/y/w/h, colors, time, name…)
  │
  │   renderer paints (Swing JComponent OR Angular tile component)
```

**Concrete benefits:**
- Less data over the wire. `RenderedBlock` is a flat record with one name + one time string + one colour list — *not* the full reservation graph (allocatables, classifications, permissions, dynamic types).
- The Angular team gets **byte-for-byte identical** layout to Swing/HTML without porting the sweep-line / overlap / group-strategy code. Rapla's specific semantics (group-by-resource vs group-by-day, fixed slots, conflict resolution, period overlay) are non-trivial — a generic Angular calendar widget will not replicate them.
- One source of truth for layout. When the strategy gets a fix (e.g. overlap edge case), both Swing and Angular get it the same week.
- Closes the PRD 005 D3 back-edge: the only reason `rapla-server` depends on `rapla-client` today is the calendar-builder coupling. Exposing layout via REST means that dependency is no longer load-bearing for the calendar.

The Swing client **keeps calling the builder in-process** — same in-process model as today, same render path. Migration to the REST endpoint is opt-in and not part of this PRD.

### Considered & rejected

- **`/allocatables/search`** — *not a win.* The Swing client already has the full allocatable set loaded via `RaplaFacade.getAllocatables(...)`; in-memory filtering is O(N) over a few hundred to a few thousand entries — sub-millisecond. A server round-trip per keystroke would be strictly slower. The future Angular client can either pre-fetch the same way (the set is small) or paginate via the existing `/storage/resources` endpoint (PRD 009). No reason for a dedicated edit-time search endpoint.
- **`/format/duration`, `/format/date-range`, `/format/appointment-time`** — *not a win.* Locale-aware formatting is not heavy work — `RaplaLocale.formatTime(...)` etc. are local string concatenation. The future Angular client should format with the browser's native `Intl.DateTimeFormat` / `Intl.NumberFormat`, which is well-supported and zero-round-trip. Server-side formatting would add latency per format call and force the server to ship strings for every locale variant. Skip.

### In scope — wire contract

All endpoints follow the patterns already established by PRD 020:

- `@HttpExchange` interfaces in `rapla-core/.../plugin/edit/`
  (re-using the Spring 6 HttpExchange surface, like
  `PreferencesAdminService`).
- Request / response DTOs as Java 21 `record`s; field-based Jackson 3
  serialization (per PRD 010).
- All endpoints `@RequestMapping`-gated by the existing JWT bearer
  filter; no separate auth.
- Errors via `RaplaException` propagation; same handler as today
  (`RaplaSpringBootApplication`'s exception advisor).
- Wire-format invariants pinned by a tier-1 contract test
  (`ReservationEditServiceContractTest`) listing paths, methods,
  request/response type names. Pattern from
  `PreferencesAdminServiceContractTest`.

### Explicitly out of scope

- **No save-path REST changes.** `RaplaFacade.dispatch(...)` already
  works end-to-end; the existing REST surface is fine. 024 adds
  **read-side and pre-check** endpoints, not new save paths.
- **No Swing client migration to the new endpoints.** The Swing
  client keeps calling the in-process pure models (from PRD 023).
  Migrating Swing → REST gains nothing and loses determinism. The
  REST endpoints are for the Angular client and for any external
  integration (PRD 009 bulk storage REST, PRD 012 external-event
  import).
- **No JSON Schema / OpenAPI generation.** The `@HttpExchange`
  interface is the contract; OpenAPI generation can come later if
  the Angular team wants it.
- **No new authorization model.** Whatever the JWT user can do via
  the Swing client today, they can do via the REST endpoints.
  Per-endpoint admin gating handled via `User.isAdmin()` as in
  `PreferencesAdminController`.
## Architecture

### Wire contract sketch

```java
@HttpExchange("/edit")
public interface ReservationEditService {

    // 1. Conflict pre-check — for one allocatable + one or more appointments
    @PostExchange("/check-conflicts")
    ConflictReport checkConflicts(@RequestBody ConflictCheckRequest req)
            throws RaplaException;

    // 2. Recurrence-rule validation — pure rule check, no facade hit
    @PostExchange("/validate-recurrence")
    RecurrenceValidation validateRecurrence(@RequestBody RecurrenceRule rule)
            throws RaplaException;
}

record ConflictCheckRequest(
    List<ReferenceInfo<Allocatable>> allocatables,
    List<AppointmentSpec> appointments,    // start, end, repeating
    LocalDate today
) {}

record AppointmentSpec(LocalDateTime start, LocalDateTime end, RecurrenceRule recurrence) {}

record ConflictReport(
    Map<ReferenceInfo<Allocatable>, AllocationOutcome> outcomes
) {}

record AllocationOutcome(
    boolean[] conflictingAppointments,   // matches request order
    int conflictCount,
    int permissionDeniedCount,
    RequestStatus aggregateRequestStatus
) {}

record RecurrenceRule(
    RepeatingType type,
    int interval,
    Set<Integer> weekdays,
    EndingMode endingMode,
    LocalDate endDate,
    Integer repeatCount,
    SortedSet<LocalDate> exceptions
) {}

record RecurrenceValidation(boolean valid, List<String> errors) {}
```

`AllocationOutcome` matches the existing record in
`rapla-core/.../client/edit/reservation/AllocationConflictModel.java`
(PRD 023 Phase 3 landed). `RecurrenceRule` corresponds to the existing
`RepeatingRuleModel`. The controllers map DTO → model input, call the
pure compute / validate, and map model output → DTO. Nothing new to
implement on the rapla-core side.

```java
@HttpExchange("/calendar")
public interface CalendarViewService {

    @GetExchange("/view")
    CalendarPage view(@RequestParam("from") String fromIso,
                      @RequestParam("to") String toIso,
                      @RequestParam("strategy") LayoutStrategyId strategy,
                      @RequestParam(value = "groupBy", required = false) GroupBy groupBy,
                      @RequestParam(value = "allocatables", required = false) List<String> allocatableIds,
                      @RequestParam(value = "classificationFilter", required = false) String filterJson)
            throws RaplaException;
}

enum LayoutStrategyId { GROUP_START_TIMES, BEST_FIT }
enum GroupBy { RESOURCE, DAY }

record CalendarPage(
    LocalDate from,
    LocalDate to,
    List<Column> columns,            // one per day or per resource depending on groupBy
    List<RenderedBlock> blocks
) {}

record Column(String id, String label, int index) {}

record RenderedBlock(
    String reservationId,
    String appointmentId,
    int columnIndex,                 // which Column this block belongs to
    LocalDateTime start,
    LocalDateTime end,
    int slotIndex,                   // overlap-resolved slot within the column
    int slotCount,                   // total slots in this column (for x/w computation)
    List<String> colorsHex,          // from RaplaBlock.getColorsAsHex()
    boolean isException,
    boolean isRequest,
    String name,                     // pre-formatted display name
    String tooltip
) {}
```

The endpoint deliberately does **not** ship pixel x/y/w/h. It ships
`(columnIndex, slotIndex, slotCount)` plus the time range — the client
multiplies by its own pixel-per-minute / column-width. This keeps the
contract resolution-independent: same JSON renders correctly in a
2000×1000 Swing window and a 360×640 mobile Angular view. The
overlap-resolution semantics (which sweep-line algorithm packs blocks
into slots) — the expensive bit — is server-authoritative.

### Server side

- One `@RestController` per service, in
  `rapla-server/.../spring/web/edit/`.
- Each controller injects the **pure model** from rapla-core (e.g.
  `AllocationConflictModel`, `RepeatingRuleValidator`) plus
  `RaplaFacade` for entity lookup.
- Controller bodies are thin: map DTO → model input, call
  `model.compute(...)`, map result → DTO. Same shape as PRD 020's
  `PreferencesAdminController`.

### Client side

- Spring `@HttpExchange` proxies registered in `ClientProxyConfig`
  (the existing place that creates the REST proxy beans).
- The Swing client **does not switch** to REST proxies. Per the
  out-of-scope note above. Existing in-process calls stay.
- The Angular client (future) is the consumer.

## Plan

Three phases, independent. Order to match Angular team priorities.

### Phase 1 — `validateRecurrence` (≈3 days)

Depends on PRD 023 Phase 2 (`RepeatingRuleValidator`, DONE 2026-05-11).
One endpoint, no facade hit. Lowest-risk first deliverable; flushes
out the JWT-protected `@HttpExchange` registration boilerplate.

1. `rapla-core`: `ReservationEditService` `@HttpExchange` interface +
   `RecurrenceRule` / `RecurrenceValidation` DTO records.
2. `rapla-server`: `ReservationEditController` mapping the DTO to a
   `RepeatingRuleModel` and back via `RepeatingRuleValidator.validate(...)`.
3. `rapla-app`: MockMvc test covering the same cases as
   `RepeatingRuleValidatorTest` end-to-end.
4. `rapla-core`: contract test (`ReservationEditServiceContractTest`)
   pinning paths, method shapes, DTO field names.

### Phase 2 — `checkConflicts` (≈5 days)

Depends on PRD 023 Phase 3 (`AllocationConflictModel`, DONE 2026-05-11).
The allocatable-bindings map (today built client-side in
`AllocatableSelection.setReservation(...)`) is built server-side from
the requested allocatables + the user's permission scope; this is the
biggest behavioural carve-out.

1. Server-side: `AllocationBindingsLoader` (rapla-server) — given a
   list of allocatables + an effective time range, queries the
   reservation cache and returns the bindings map. The
   server-authoritative replacement for the in-memory map maintained
   in `AllocatableSelection`.
2. `checkConflicts` POST wires the loader + `AllocationConflictModel.compute(...)`.
3. MockMvc test covers: zero conflicts, all conflicts, permission-denied
   case, `hold-back-conflicts` annotation, request-status aggregation.

### Phase 3 — `CalendarViewService.view` (≈8 days) — **CONTRACT LAYER DONE 2026-05-11**

Landed in `rapla-core/.../plugin/calendarview/`: `CalendarViewService` `@HttpExchange` interface, `CalendarPage`, `Column`, `RenderedBlock` records, `LayoutStrategyId` and `GroupBy` enums. `CalendarViewServiceContractTest` (8 tier-1 tests, ~170 ms) pins paths, method shapes, record-component declaration order, enum names. Angular team can start writing TS types from the contract immediately.

**Server-side `CalendarLayoutAssembler` + `CalendarViewController` + `BuildContext`-server-equivalent + MockMvc integration test: deferred to a focused session.** This is the multi-day part — wiring `RaplaBuilder` headlessly, building a non-Swing `BuildContext`, mapping reservation permissions per-block. The contract surface is stable enough that the server impl can be written without breaking the wire format.

Original plan follows for the record:

Largest of the three. The bones already exist — `RaplaBuilder`,
`AbstractGroupStrategy`, `GroupStartTimesStrategy`, `BestFitStrategy`,
`RaplaBlock`, `HTMLRaplaBlock` are all in rapla-core today and run
headlessly. The work is **wrapping** them in a controller + DTOs.

1. **Server `BuildContext` factory.** Today's `BuildContext` is built
   inside `RaplaBuilder` from the running calendar widget's state
   (visible time window, colour scheme, period overlay, time-visible
   flag). The server needs a no-Swing constructor that takes
   request params instead. Likely a thin `ServerBuildContext` that
   implements the same interface but pulls user preferences from
   `Preferences` instead of widget state.
2. **`CalendarLayoutAssembler` (rapla-server).** Given a date range,
   strategy id, group-by, optional allocatable filter and classification
   filter: queries reservations via `RaplaFacade`, builds `Block`s via
   `RaplaBuilder`, runs the chosen strategy, flattens the
   strategy's placement output into `RenderedBlock` records.
3. **`CalendarViewController` (rapla-server).** Thin: parse query
   params → call assembler → return `CalendarPage`. Permission-checks
   on a per-reservation basis (a user only sees blocks for
   reservations they can read).
4. **`CalendarViewServiceContractTest`** (rapla-core, tier 1): pin
   `LayoutStrategyId`, `GroupBy`, `RenderedBlock` field names.
5. **`CalendarViewControllerIntegrationTest`** (rapla-app, MockMvc,
   tier 3): for each strategy × groupBy combination, seed
   reservations and verify the returned page has the expected
   `(columnIndex, slotIndex, slotCount)` layout. Use
   `FacadeTestSupport`'s `testdefault.xml` so the fixture is real.
6. **Permission-filter integration test:** verify that a
   non-admin user gets only their own + readable reservations,
   not the full server view.

The Swing calendar widget is **not** migrated to this endpoint in this
phase — it keeps calling `RaplaBuilder` in-process. Net delta on the
Swing tier from this phase: zero. The deliverable is the contract +
the headless assembler, validated end-to-end by the MockMvc tests.
Swing migration is a follow-up PRD (and may never happen — Swing
performance is fine in-process).

## Tests

| Phase | Tier | Where |
|---|---|---|
| 1 | 1 (contract) + 3 (MockMvc) | `rapla-core` + `rapla-app` |
| 2 | 1 + 3 | `rapla-core` + `rapla-app` |
| 3 | 1 + 3 (per-strategy × groupBy combinations) | `rapla-core` + `rapla-app` |

Reuse `PreferencesAdminControllerIntegrationTest` as a template for
each Phase's MockMvc class — same Spring context, same JWT setup.

A single contract test (`ReservationEditServiceContractTest` in
rapla-core) pins the `@HttpExchange` paths and DTO field names, so an
Angular team reading the contract gets a stable surface.

## Risks

1. **Round-trip latency.** A naive Angular client calling
   `validateRecurrence` on every keystroke would be wasteful.
   Mitigation: the recurrence validator is also a *pure* model in
   rapla-core (PRD 023 Phase 2) and a TypeScript port is trivial.
   The REST endpoint is the **authority**; the Angular client may
   shadow it client-side for UX.
2. **`checkConflicts` is the highest-load endpoint.** Caching: this
   PRD does not add caching. The server-side
   `AllocationBindingsLoader` should reuse the existing per-request
   `LocalCache` (PRD 008) rather than re-querying. Measure with
   `mvn -Pcoverage` and revisit if it's a hotspot.
3. **DTO drift.** New fields on `AllocationOutcome` etc. need a Jackson
   field-based migration path (PRD 010). Use the same
   `@JsonProperty` discipline as PRD 020.
4. **Permission semantics differ subtly today.** The Swing client
   computes `canAllocate(allocatable, user, start, end, today)`;
   centralising on the server may surface that one of the date params
   is silently `null` in some call paths. Mitigation: 023 Phase 3
   already standardises the model; 024 simply re-exposes it.
5. **Time zones.** `LocalDateTime` over the wire is timezone-naive by
   design (PRD 014). The server interprets relative to its own zone.
   Document this in the DTO JavaDoc on `AppointmentSpec`.
6. **Discovery / versioning.** Spring 6 `@HttpExchange` interfaces
   don't ship a wire-version header. If we later need v2 endpoints,
   add `/v2/...` URLs alongside; don't break the v1 path.
7. **`BuildContext` carries widget state.** Today's `BuildContext` is
   coupled to whatever the open Swing calendar view has selected
   (colour scheme, period overlay, time-visible flag). Phase 3 needs
   a server-side equivalent that resolves these from `Preferences`
   instead of widget state. Risk: edge cases where widget-only flags
   matter and Preferences doesn't capture them. Mitigation: audit
   `BuildContext`'s ~10 flags against `Preferences` keys; for any
   widget-only flag, surface it as a request param on `/calendar/view`.
8. **Per-strategy contract stability.** `GroupStartTimesStrategy` vs
   `BestFitStrategy` produce different layouts for the same data.
   The DTO must not bake in the strategy's terminology
   (`slotIndex`/`slotCount` is generic enough). Avoid leaking
   strategy-specific concepts into the wire contract — the strategy
   choice is a request param, not a response shape.

## Open Questions

1. **`AppointmentSpec` vs. transient `Appointment` entity.** Today's
   `Appointment` is an entity with an ID. The endpoint accepts
   *unsaved* appointment specs. `AppointmentSpec` is a separate DTO
   to avoid pretending it's a persisted entity. Confirm naming.
2. **Bulk vs. per-allocatable conflict check.** `checkConflicts`
   takes a list of allocatables and returns a map. Could be split
   into per-allocatable calls; bulk is more efficient. Confirm with
   Angular team.
3. **Should we publish OpenAPI?** Yes eventually; the `@HttpExchange`
   interfaces are a partial spec. Adding springdoc-openapi gives a
   public schema. Out of scope here; track separately.

## Cross-references

| PRD | Relationship |
|---|---|
| **023** presenter / model carve-out | **Prerequisite, mostly DONE.** 024 wraps the pure models 023 produces. Phases align: 023 P2 → 024 P1, 023 P3 → 024 P2; 024 P3 wraps the pre-existing `RaplaBuilder` + strategies in rapla-core. |
| **005** multi-module split | 024 P3 (`/calendar/view`) is the path to retiring PRD 005 D3's `rapla-server → rapla-client` back-edge — that coupling exists today only for the calendar builder. |
| **020** server-driven admin panels | Pattern source. The `@HttpExchange` + record-DTO + `@ConditionalOnProperty` + MockMvc-contract-test pattern is copied verbatim. |
| **012** dhbwrapla client carve-out | Pattern source for the **wizard-flow → metadata** style. `/edit/...` endpoints in 024 echo `/external-event-import/...` in 012. |
| **009** server bulk storage REST API | Sibling REST surface. 009 is bulk read/write of entities; 024 is edit-time decisions. No overlap. |
| **010** Jackson field-based wire format | All new DTOs follow 010's serialization rules. |
| **005** multi-module split | Pure models in rapla-core (per 023) are server-callable, which relaxes the rapla-server → rapla-client back-edge over time. |
| **AGENTS.md §10** | Contract tests at tier 1, MockMvc at tier 3. No tier-4 added. |
