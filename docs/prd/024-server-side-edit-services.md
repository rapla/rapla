# PRD 024 — Server-side edit services (Angular precursor)

**Status:** in-progress —
- **Phase 1 (`validateRecurrence`)** DONE: `ReservationEditService` + `ReservationEditController` + `RecurrenceRule` / `RecurrenceValidation` DTOs. Wraps `RepeatingRuleValidator` (rapla-core, [PRD 023](023-presenter-view-extraction.md) Phase 2).
- **Phase 2 (`checkConflicts`)** DONE: `ConflictCheckRequest` / `ConflictReport` / `AllocationOutcomeDto` / `AppointmentSpec` DTOs. Wraps `AllocationConflictModel.compute(...)` and pulls allocatable bindings via the existing `facade.getAllocatableBindings(...)` — no separate loader needed. AGENTS.md §12 leak-probe covered.
- **Phase 3 (`/calendar/view`)** DONE: engine + controller + 16 tier-1 + 7 MockMvc tests (landed 2026-05-11; colours added 2026-05-12 via [PRD 030](030-server-side-view-rendering.md) Phase 4 `BlockColors`).
- **Bonus:** `expand-blocks` endpoint (`AppointmentSpec` → `AppointmentBlockDto[]`) — server-side `Appointment.createBlocks(...)` so Angular doesn't port weekday-flip / exception-skip logic. [PRD 026](026-angular-frontend.md) §B4 hook.
- **Test totals**: 12 contract tests (`ReservationEditServiceContractTest`) + 15 MockMvc tests (`ReservationEditControllerIntegrationTest`), green 2026-05-12.

Scope adjusted 2026-05-11: allocatable search + format service dropped; calendar layout added — see Considered & Rejected.
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-11

## Goal

Expose the **business logic of reservation editing** — conflict pre-check, permission filtering, allocatable suggestion, recurrence-rule validation, formatter / display helpers, classification-filter evaluation — as **REST endpoints**, so a future Angular (or any non-JVM) client can do reservation editing without re-implementing twelve years of subtle scheduling rules.

Companion of [PRD 023](023-presenter-view-extraction.md): 023 carves pure-Java models out from under Swing; 024 wraps them in `@HttpExchange` interfaces and serves them. Swing keeps calling the in-process model (no behaviour change); Angular calls REST and renders the result.

## Why this is needed now

1. **The Angular rework will land in 12–24 months.** Building it against a "thin client → REST" architecture from day one is the only way that project finishes.
2. **[PRD 020](020-server-driven-admin-panels.md) (server-driven admin panels) and [PRD 012](012-dhbwrapla-client-migration.md) (external event import wizard) already ship the pattern.** 024 generalises it to the reservation-edit surface — the largest UI area not yet covered.
3. **Server-side authority is correct anyway.** Client permission filtering is cosmetic; the server already re-checks on save. Centralising eliminates client/server skew (Swing and server re-implement the same `canAllocate` logic and have drifted before).
4. **Closes PRD 005's dependency direction.** rapla-server today depends on rapla-client for `abstractcalendar` / `RaplaBuilder` (PRD 005 D3). Layout / formatting in `rapla-core` (per 023) lets rapla-server call it without the back-edge.

## Scope

### In scope — three REST surfaces

| # | Surface | Wraps | Client use today | Angular use later |
|---|---|---|---|---|
| 1 | `/edit/check-conflicts` | `AllocationConflictModel.compute(...)` | `AllocatableSelection.paintAllocation` | Conflict-warning sidebar |
| 2 | `/edit/validate-recurrence` | `RepeatingRuleValidator.validate(...)` | `AppointmentController.RepeatingEditor.mapToAppointment` | Inline form validation |
| 3 | `/calendar/view` | `RaplaBuilder` + `GroupStartTimesStrategy`/`BestFitStrategy` + `RaplaBlock`/`HTMLRaplaBlock` | New (today the client builds blocks locally) | Angular week / month / day view consumes pre-positioned tiles |

(1) and (2) wrap pure-Java models already in rapla-core ([PRD 023](023-presenter-view-extraction.md) Phases 2+3 landed 2026-05-11). (3) wraps the existing `RaplaBuilder` + layout strategies (already headless in rapla-core); the server runs them server-side and serializes the result instead of shipping raw entities.

### Why server-side calendar layout is the right call

The server queries the reservations for a date range either way. Today the client receives raw entities and runs `RaplaBuilder` + `GroupStartTimesStrategy` locally. With `/calendar/view`, the server runs the same pipeline and returns pre-positioned `RenderedBlock` tiles.

**Concrete benefits:**
- Less wire data. `RenderedBlock` is a flat record with one name + time string + colour list — not the full reservation graph.
- Angular gets **byte-for-byte identical** layout to Swing/HTML without porting the sweep-line / overlap / group-strategy code. Rapla's specific semantics (group-by-resource vs group-by-day, fixed slots, conflict resolution, period overlay) are non-trivial — a generic Angular calendar widget will not replicate them.
- One source of truth for layout.
- Closes the PRD 005 D3 back-edge (the calendar-builder coupling is the only reason for it today).

Swing keeps calling the builder in-process. Migration to REST is opt-in and not part of this PRD.

### Considered & rejected

- **`/allocatables/search`** — *rejected*: Swing already has the full set loaded; in-memory filtering on a few thousand entries is sub-ms. Angular can pre-fetch or paginate via `/storage/resources` ([PRD 009](009-server-bulk-storage-rest-api.md)).
- **`/format/duration`, `/format/date-range`, `/format/appointment-time`** — *rejected*: locale formatting is local string concatenation; Angular should use the browser's native `Intl.DateTimeFormat` / `Intl.NumberFormat`.

### In scope — wire contract

Endpoints follow the patterns established by [PRD 020](020-server-driven-admin-panels.md):

- `@HttpExchange` interfaces in `rapla-core/.../plugin/edit/` (re-using Spring 6 HttpExchange surface, like `PreferencesAdminService`).
- Request/response DTOs as Java 21 `record`s; field-based Jackson 3 serialization ([PRD 010](done/010-jackson-field-based-wire-format.md)).
- JWT bearer filter gates all endpoints; no separate auth.
- Errors via `RaplaException` propagation (same handler as today).
- Wire invariants pinned by `ReservationEditServiceContractTest` (tier 1), modelled on `PreferencesAdminServiceContractTest`.

### Explicitly out of scope

- **No save-path REST changes.** `RaplaFacade.dispatch(...)` works end-to-end; existing REST surface is fine. 024 adds read-side and pre-check endpoints only.
- **No Swing client migration.** Swing keeps the in-process pure models ([PRD 023](023-presenter-view-extraction.md)). REST endpoints are for Angular + external integrations.
- **No JSON Schema / OpenAPI generation.** The `@HttpExchange` interface is the contract; OpenAPI later if needed.
- **No new authorization model.** Same as Swing today; per-endpoint admin gating via `User.isAdmin()`.
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

`AllocationOutcome` matches the existing record in `AllocationConflictModel.java` ([PRD 023](023-presenter-view-extraction.md) Phase 3); `RecurrenceRule` corresponds to `RepeatingRuleModel`. Controllers map DTO ↔ model. Nothing new on the rapla-core side.

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

Endpoint ships `(columnIndex, slotIndex, slotCount)` plus time range — *not* pixels. Client multiplies by its own pixel-per-minute / column-width — resolution-independent. Server-authoritative overlap-resolution (the expensive bit).

### Server side

- One `@RestController` per service in `rapla-server/.../spring/web/edit/`.
- Each controller injects the pure model from rapla-core (`AllocationConflictModel`, `RepeatingRuleValidator`) + `RaplaFacade`.
- Thin bodies: DTO → model → DTO. Same shape as [PRD 020](020-server-driven-admin-panels.md)'s `PreferencesAdminController`.

### Client side

- Spring `@HttpExchange` proxies registered in `ClientProxyConfig`.
- Swing **does not switch** (per out-of-scope note); Angular is the consumer.

## Plan

Three phases, independent. Order to match Angular team priorities.

### Phase 1 — `validateRecurrence` (≈3 days)

Depends on [PRD 023](023-presenter-view-extraction.md) Phase 2 (`RepeatingRuleValidator`, DONE). One endpoint, no facade hit. Lowest-risk first deliverable; flushes out JWT-protected `@HttpExchange` registration boilerplate.

1. `rapla-core`: `ReservationEditService` interface + `RecurrenceRule` / `RecurrenceValidation` DTOs.
2. `rapla-server`: `ReservationEditController` mapping DTO ↔ `RepeatingRuleModel` via `RepeatingRuleValidator.validate(...)`.
3. `rapla-app`: MockMvc test mirroring `RepeatingRuleValidatorTest` end-to-end.
4. `rapla-core`: contract test pinning paths, method shapes, DTO field names.

### Phase 2 — `checkConflicts` (≈5 days)

Depends on [PRD 023](023-presenter-view-extraction.md) Phase 3 (`AllocationConflictModel`, DONE). Allocatable-bindings map (today built client-side in `AllocatableSelection.setReservation(...)`) is built server-side from requested allocatables + the user's permission scope — the biggest behavioural carve-out.

1. Server `AllocationBindingsLoader` — given allocatables + time range, queries reservation cache, returns bindings map.
2. `checkConflicts` POST wires loader + `AllocationConflictModel.compute(...)`.
3. MockMvc test: zero/all conflicts, permission-denied, `hold-back-conflicts`, request-status aggregation.

### Phase 3 — `CalendarViewService.view` (≈8 days) — **CONTRACT LAYER DONE 2026-05-11**

Landed in `rapla-core/.../plugin/calendarview/`: `CalendarViewService` interface, `CalendarPage`, `Column`, `RenderedBlock` records, `LayoutStrategyId` and `GroupBy` enums. `CalendarViewServiceContractTest` (8 tier-1 tests, ~170 ms) pins paths/methods/record-component declaration order/enum names. Angular team can write TS types from the contract immediately.

**Server-side `CalendarLayoutAssembler` + `CalendarViewController` + non-Swing `BuildContext` + MockMvc integration test: deferred to a focused session.** Multi-day work — wiring `RaplaBuilder` headlessly, mapping per-block permissions. Wire format is stable.

Original plan for the record:

1. **Server `BuildContext` factory** — non-Swing constructor pulling state from `Preferences` instead of widget.
2. **`CalendarLayoutAssembler`** — queries reservations, builds blocks via `RaplaBuilder`, runs strategy, flattens to `RenderedBlock` records.
3. **`CalendarViewController`** — thin: params → assembler → return. Per-reservation permission filter.
4. **`CalendarViewServiceContractTest`** (tier 1).
5. **`CalendarViewControllerIntegrationTest`** (tier 3, MockMvc): strategy × groupBy combinations against `FacadeTestSupport`'s `testdefault.xml`.
6. **Permission-filter integration test**: non-admin user gets only their own + readable reservations.

Swing widget unchanged; deliverable is the contract + headless assembler, validated by MockMvc. Swing migration is a follow-up PRD (and may never happen).

## Tests

| Phase | Tier | Where |
|---|---|---|
| 1 | 1 (contract) + 3 (MockMvc) | `rapla-core` + `rapla-app` |
| 2 | 1 + 3 | `rapla-core` + `rapla-app` |
| 3 | 1 + 3 (per-strategy × groupBy combinations) | `rapla-core` + `rapla-app` |

Reuse `PreferencesAdminControllerIntegrationTest` as MockMvc template. `ReservationEditServiceContractTest` pins paths + DTO field names.

## Risks

1. **Round-trip latency.** Naive `validateRecurrence` per-keystroke is wasteful. Mitigation: validator is a pure model in rapla-core ([PRD 023](023-presenter-view-extraction.md) P2); TS port is trivial. REST is **authority**; Angular may shadow client-side for UX.
2. **`checkConflicts` highest-load.** No caching v1; server-side `AllocationBindingsLoader` should reuse per-request `LocalCache` (PRD 008). Measure with `mvn -Pcoverage`.
3. **DTO drift.** New fields need Jackson field-based migration ([PRD 010](done/010-jackson-field-based-wire-format.md)); use `@JsonProperty` discipline per [PRD 020](020-server-driven-admin-panels.md).
4. **Permission semantics differ subtly.** Swing computes `canAllocate(allocatable, user, start, end, today)`; centralising on server may surface that one date param is silently `null` in some paths. Mitigation: 023 P3 standardises; 024 re-exposes.
5. **Time zones.** `LocalDateTime` is timezone-naive ([PRD 014](done/014-appointment-long-to-java-time.md)); server interprets in own zone. Document in `AppointmentSpec` JavaDoc.
6. **Discovery / versioning.** No wire-version header. For v2 endpoints, add `/v2/...` URLs alongside.
7. **`BuildContext` carries widget state** (colour scheme, period overlay, time-visible flag). Phase 3 needs a server equivalent resolving from `Preferences`. Audit `BuildContext`'s ~10 flags; widget-only flags become request params.
8. **Per-strategy contract stability.** `GroupStartTimesStrategy` vs `BestFitStrategy` produce different layouts. DTO must not bake in strategy terminology — `slotIndex`/`slotCount` is generic enough; strategy choice is a request param, not response shape.

## Open Questions

1. **`AppointmentSpec` vs. transient `Appointment` entity.** Endpoint accepts *unsaved* specs; DTO avoids pretending it's a persisted entity. Confirm naming.
2. **Bulk vs. per-allocatable conflict check.** Bulk is more efficient; confirm with Angular team.
3. **Publish OpenAPI?** Yes eventually; track separately.

## Cross-references

| PRD | Relationship |
|---|---|
| **023** presenter / model carve-out | **Prerequisite, mostly DONE.** 024 wraps the pure models. 023 P2 → 024 P1, 023 P3 → 024 P2; 024 P3 wraps pre-existing `RaplaBuilder` + strategies. |
| **005** multi-module split | 024 P3 (`/calendar/view`) retires PRD 005 D3's `rapla-server → rapla-client` back-edge (calendar-builder coupling). |
| **020** server-driven admin panels | Pattern source — `@HttpExchange` + record-DTO + `@ConditionalOnProperty` + MockMvc-contract-test copied verbatim. |
| **012** dhbwrapla client carve-out | Pattern source for wizard-flow → metadata. `/edit/...` echoes `/external-event-import/...`. |
| **009** server bulk storage REST API | Sibling; bulk read/write vs edit-time decisions. No overlap. |
| **010** Jackson field-based wire format | All new DTOs follow 010's serialization rules. |
| **AGENTS.md §10** | Contract tests tier 1, MockMvc tier 3. No tier-4 added. |
