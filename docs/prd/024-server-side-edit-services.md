# PRD 024 — Server-side edit services (Angular precursor)

**Status:** draft
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

### In scope — five REST surfaces

| # | Surface | Wraps (after 023 lands) | Client use today | Angular use later |
|---|---|---|---|---|
| 1 | `/reservations/check-conflicts` | `AllocationConflictModel.compute(...)` | `AllocatableSelection.paintAllocation` | Conflict-warning sidebar |
| 2 | `/appointments/validate-recurrence` | `RepeatingRuleValidator.validate(...)` | `AppointmentController.RepeatingEditor.mapToAppointment` | Inline form validation |
| 3 | `/allocatables/search` | `RaplaFacade.getAllocatables(...)` + classification filter + permission filter | New (today client filters in-memory) | Allocatable picker autocomplete |
| 4 | `/calendar/layout` (opt-in) | `CalendarBlockLayout.compute(...)` + `ReservationBlockStyle.compute(...)` | Currently in-process; server endpoint is opt-in for clients that don't want to ship the algorithm | Angular week / month view |
| 5 | `/format/duration`, `/format/date-range`, `/format/appointment-time` | `RaplaLocale` formatters | New (today via `RaplaGUIComponent.getTimeRenderer().getDurationString()`) | Locale-aware display strings |

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
- **`/calendar/layout` may stay deferred to v2.** It's expensive
  (returns geometry for every block in a view) and the Angular client
  may prefer to compute layout client-side. Ship it only if the
  Angular team explicitly wants it.

## Architecture

### Wire contract sketches

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

    // 3. Allocatable search — server filters & permission-checks
    @GetExchange("/allocatables/search")
    List<AllocatableStub> searchAllocatables(
            @RequestParam("q") String query,
            @RequestParam(value = "type", required = false) String dynamicTypeKey,
            @RequestParam(value = "max", defaultValue = "50") int maxResults,
            @RequestParam(value = "atDate", required = false) String isoDate)
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

record AllocatableStub(
    ReferenceInfo<Allocatable> id,
    String name,
    String dynamicTypeKey,
    boolean canAllocate
) {}
```

```java
@HttpExchange("/format")
public interface FormatService {
    @PostExchange("/duration")
    String formatDuration(@RequestBody DurationFormatRequest req)
            throws RaplaException;

    @PostExchange("/date-range")
    String formatDateRange(@RequestBody DateRangeFormatRequest req)
            throws RaplaException;

    @PostExchange("/appointment-time")
    String formatAppointmentTime(@RequestBody AppointmentTimeFormatRequest req)
            throws RaplaException;
}
```

`/format/*` is the smallest of the three and the easiest first
deliverable.

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

Each phase produces one ship-able REST surface plus tests. Phases
are independent and can be ordered to match Angular team priorities.

### Phase 1 — `FormatService` (≈3 days)

Smallest surface; flushes out the JWT-protected `@HttpExchange`
registration boilerplate without business risk.

1. `rapla-core`: `FormatService` interface, three DTO records, contract
   test.
2. `rapla-server`: `FormatController`, calls existing `RaplaLocale`
   formatters.
3. `rapla-app`: MockMvc test — non-admin allowed (per-user surface),
   round-trip for each formatter.

### Phase 2 — `ReservationEditService.validateRecurrence` (≈3 days)

Depends on PRD 023 Phase 2 (`RepeatingRuleValidator`). One endpoint,
no facade hit, easy.

1. Wire the `validateRecurrence` POST.
2. Contract test pins the DTO shape.
3. MockMvc test covers the same cases as
   `RepeatingRuleValidatorTest` (PRD 023 Phase 2).

### Phase 3 — `ReservationEditService.checkConflicts` (≈5 days)

Depends on PRD 023 Phase 3 (`AllocationConflictModel`). The
allocatable-bindings map (today built client-side in
`AllocatableSelection.setReservation(...)`) is built server-side from
the requested allocatables + the user's permission scope; this is the
biggest behavioural carve-out.

1. Server-side: `AllocationBindingsLoader` (rapla-server) — given a
   list of allocatables + an effective time range, queries the
   reservation cache and returns the bindings map. This becomes the
   server-authoritative replacement for the in-memory map maintained
   in `AllocatableSelection`.
2. `checkConflicts` POST wires the loader + the pure model.
3. MockMvc test covers: zero conflicts, all conflicts, permission-denied
   case, `hold-back-conflicts` annotation, request-status aggregation.

### Phase 4 — `ReservationEditService.searchAllocatables` (≈4 days)

1. Server-side: extend the existing `getAllocatables` facade with a
   search-and-paginate variant (or add a thin `AllocatableSearch`
   service if the facade is too coarse).
2. Apply permission filtering server-side; `canAllocate` against
   `atDate` if provided.
3. MockMvc test: classification filter, max-results enforced,
   permission filtering observable.

### Phase 5 — `/calendar/layout` (opt-in, ≈5 days, gated on Angular team ask)

Skip unless explicitly requested. Depends on PRD 023 Phase 4
(`CalendarBlockLayout` + `ReservationBlockStyle`).

1. `CalendarLayoutService` wraps the two pure models.
2. Endpoint: `POST /calendar/layout` with a time range + allocatable
   list + column width → returns `List<RenderedBlock>` with
   `(blockId, x, y, w, h, styleRgb, borderEnum)`.
3. MockMvc test exercises overlap layout, all-day rows.

## Tests

| Phase | Tier | Where |
|---|---|---|
| 1 | 1 (contract) + 3 (MockMvc) | `rapla-core` + `rapla-app` |
| 2 | 1 + 3 | `rapla-core` + `rapla-app` |
| 3 | 1 + 3 | `rapla-core` + `rapla-app` |
| 4 | 1 + 3 | `rapla-core` + `rapla-app` |
| 5 | 1 + 3 | `rapla-core` + `rapla-app` |

Reuse `PreferencesAdminControllerIntegrationTest` as a template for
each Phase's MockMvc class — same Spring context, same JWT setup.

A single per-phase contract test (`*ContractTest` in rapla-core) pins
the `@HttpExchange` paths and DTO field names, so an Angular team
reading the contract gets a stable surface.

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

## Open Questions

1. **Should `/format/*` be cached by an HTTP header?** Locale-dependent
   strings are stable for the user's locale. `Cache-Control: max-age`
   on the response is cheap. Punt; add when Angular hits the endpoint
   and benchmarks show it.
2. **`AppointmentSpec` vs. transient `Appointment` entity.** Today's
   `Appointment` is an entity with an ID. The endpoint accepts
   *unsaved* appointment specs. `AppointmentSpec` is a separate DTO
   to avoid pretending it's a persisted entity. Confirm naming.
3. **Allocatable search ranking.** `searchAllocatables` returns by
   name match; should we add a relevance score, by-type filter,
   by-recently-used heuristic? Out of scope for v1; one open question
   per follow-up.
4. **Bulk vs. per-allocatable conflict check.** `checkConflicts`
   takes a list of allocatables and returns a map. Could be split
   into per-allocatable calls; bulk is more efficient. Confirm with
   Angular team.
5. **Should `/calendar/layout` exist at all?** A modern Angular
   calendar widget will likely do its own layout. We keep this
   phase optional and gated on explicit Angular-team demand.
6. **Should we publish OpenAPI?** Yes eventually; the `@HttpExchange`
   interfaces are a partial spec. Adding springdoc-openapi gives a
   public schema. Out of scope here; track separately.

## Cross-references

| PRD | Relationship |
|---|---|
| **023** presenter / model carve-out | **Prerequisite.** 024 wraps the pure models 023 produces. Phases align: 023 P2 → 024 P2, 023 P3 → 024 P3, 023 P4 → 024 P5. |
| **020** server-driven admin panels | Pattern source. The `@HttpExchange` + record-DTO + `@ConditionalOnProperty` + MockMvc-contract-test pattern is copied verbatim. |
| **012** dhbwrapla client carve-out | Pattern source for the **wizard-flow → metadata** style. `/edit/...` endpoints in 024 echo `/external-event-import/...` in 012. |
| **009** server bulk storage REST API | Sibling REST surface. 009 is bulk read/write of entities; 024 is edit-time decisions. No overlap. |
| **010** Jackson field-based wire format | All new DTOs follow 010's serialization rules. |
| **005** multi-module split | Pure models in rapla-core (per 023) are server-callable, which relaxes the rapla-server → rapla-client back-edge over time. |
| **AGENTS.md §10** | Contract tests at tier 1, MockMvc at tier 3. No tier-4 added. |
