# 026 — Angular frontend (reservation editing)

**Status:** draft (research / scoping only — no implementation)

## Goal

Replace the Swing reservation-edit UI with an Angular (or
equivalent modern SPA) frontend, served by the existing Spring
Boot backend over its REST surface. Initial scope: the reservation
creation and edit flow. Calendar views, admin panels, and plugin
UIs are out of scope for v1.

## Why

The Swing client is the long-tail technology debt:

- WSL2 / display-server / JNLP launch is fragile and gates new
  contributors (see PRD `done/jnlp-signing-pitfalls` follow-ups).
- The reservation-edit UI is the part users touch most, and the
  Swing implementation has ~1800 lines of edge-case glue in
  `AppointmentController` alone.
- The REST surface has been hardened enough (PRDs 009, 020, 024,
  025) that a browser client is now realistic.

## Scope

In scope:

- Reservation creation and edit (the flow documented in
  [`architecture/reservation-edit.md`](../architecture/reservation-edit.md)).
- Allocatable / resource selection for a reservation.
- Conflict overlay during edit (advisory, non-blocking).
- Repeating-rule editor with exception dates.

Out of scope for v1:

- Calendar views (week / month / day).
- Plugin admin UIs (Exchange, iCal, mail, etc.).
- Resource and user administration.
- The Swing client itself — runs alongside the new SPA until
  feature parity is reached.

## Reference

Everything an Angular implementation needs to know about the
domain and the wire model is in the architecture docs:

- [`reservation-edit.md`](../architecture/reservation-edit.md) — the
  full edit flow including the wire model (JSON shapes,
  endpoints), AppointmentController's rules, and the edge-case
  reference. **Read this first.**
- [`domain-model.md`](../architecture/domain-model.md) — entity catalog.
- [`dynamic-types.md`](../architecture/dynamic-types.md) — the EAV
  classification system.
- [`conflicts-and-events.md`](../architecture/conflicts-and-events.md)
  — what ConflictFinder considers an overlap.
- [`permissions.md`](../architecture/permissions.md) — what the
  server enforces on dispatch.

## Swing-concern → Angular-equivalent mapping

| Swing concern | Suggested SPA equivalent |
|---|---|
| Pre-save validation: `Set<EventCheck>` via Spring DI | Injection-token array of `(reservation) => Observable<boolean>` validators |
| Busy / spinner: RxJava3 `Subject<String>` | RxJS `BehaviorSubject` or a loading slice |
| Undo / redo: `CommandHistory` per dialog | Per-form state stack, lost on close |
| Cache hydrate: lazy via `ReferenceInfo` | Eager `GET /storage/resources` at app boot, then incremental via `/storage/refresh` |
| Conflict overlay: `getConflictingAppointments` + coloured block | `/storage/allocatable/bindings/all` keyed by the editing reservation's appointments |
| Mutable-clone edit cycle: `editListAsync` | First iteration: last-write-wins, surface `RaplaNewVersionException` as a refresh-and-retry dialog. Later: server-side clone endpoint |
| Per-field permission gating | Drive from permissions returned alongside the entity, not from a role string |
| Listener re-entrancy guard: `listenerEnabled` flag | `emitEvent: false` on Reactive Forms, or a manual `suppressNext` flag in state |

## Open questions

1. **Editing model.** Reproduce the clone cycle (fetch fresh on
   open, dispatch back) or accept last-write-wins for v1? Clone
   gives concurrent-edit detection; without it, Save can silently
   overwrite. Trade-off: implementation cost vs. data integrity.

2. **Recurring-exception UX.** Swing uses an interval picker
   (start/end date) for adding exception ranges. A more modern
   pattern is "click an occurrence, choose 'skip just this one'".
   The storage model is dates (not occurrences), so the
   interaction maps cleanly to either UI. Pick one.

3. **Calendar pagination for infinite recurrence.** Compute blocks
   on demand per viewport, not for the whole series. Swing does
   this via `Appointment.createBlocks(window, blocks)`. The SPA
   needs a similar discipline.

4. **Per-field permission gating.** The server returns the
   computed access level alongside the entity. Decide a
   directive / pipe pattern for hiding or read-only-rendering
   fields based on access level.

5. **Coexistence.** During the transition, the Swing client and
   the SPA both write through `/storage/dispatch`. The Swing
   client's long-poll refresh (`/storage/refresh`) will pick up
   SPA writes. The SPA needs equivalent invalidation — likely
   the same long-poll, or Server-Sent Events if we add them.

6. **Framework choice.** "Angular" is the working assumption from
   the title of this PRD; React / Vue / Svelte aren't ruled out.
   Decide before v1 plan freezes — the wire model is
   framework-agnostic but the validation / state plumbing isn't.

7. **Hosting.** Serve the SPA from rapla-app (`/webclient/` is
   currently the JNLP bundle path) or from a separate static
   host? Same-origin avoids CORS work and matches the existing
   `SecurityConfig`.

## Pre-migration refactorings

Server-side work that should land **before** SPA Phase 1 starts.
Each item is independently shippable; ordering below is by
expected impact on the SPA team, not by dependency.

### Blocking (must have before Phase 1)

1. **Finish PRD 024 phases 1+2** — the bulk of edit-time business
   logic (conflict pre-check, recurrence validation, permission
   filtering, allocatable suggestion) needs to live as REST so
   the SPA doesn't re-port it to TypeScript. Phase 3 contract
   layer landed 2026-05-11; phases 1+2 still draft. **This is
   the single largest piece of pre-migration work.**

2. **Map `RaplaNewVersionException` → HTTP 409.** **DONE 2026-05-11.**
   `@ExceptionHandler(RaplaNewVersionException.class)` added in
   `RaplaExceptionHandler` mapping to `HttpStatus.CONFLICT` with the
   standard body shape ({"status":409,"error":"Conflict","message":...}).
   Tier-1 case in `RaplaExceptionHandlerTest` + tier-3 round-trip in
   `NewVersionExceptionMappingIntegrationTest` (test controller throws
   the exception; MockMvc verifies the full Spring exception-resolution
   pipeline returns 409 with the expected body to a JWT-authenticated
   client). 409 chosen over 412 because there's no precondition header
   on the request — this is server-side optimistic-concurrency
   detection, not client-supplied If-Match.

3. **New-reservation draft endpoint.** Today `FacadeImpl.newReservation`
   (rapla-core) handles EAV default-value population, permission-template
   copy, and template lookup — all client-side. Expose as
   `POST /storage/draft?type=<dynamicType>&templateId=<uuid?>` returning
   a pre-populated `Reservation`. Without it, the SPA reimplements that
   logic; with it, the SPA's "New Event" button is one REST call.

4. **Occurrence expansion endpoint.** **DONE 2026-05-11.** Landed as
   `POST /edit/expand-blocks` (under the `ReservationEditService`
   wire contract — same JWT gate, same `AppointmentSpec` DTO as
   `/edit/check-conflicts`). Request: `{ appointment: AppointmentSpec,
   windowStart, windowEnd, excludeExceptions }`. Response:
   `List<AppointmentBlockDto>` where each DTO is `{ start, end }` in
   timezone-naive UTC (PRD 014 convention). Server-side wraps the
   transient `AppointmentImpl.createBlocks(...)` — the SPA gets a
   flat list without re-porting the weekday-flip-on-move logic to
   TypeScript. Tier-1 contract test + 4 tier-3 MockMvc cases covering
   single-occurrence, daily recurrence, JWT gate, and malformed
   request → 400. URL chosen as `/edit/expand-blocks` rather than
   `/storage/expand` because the operation is edit-time pre-check
   shaped (sibling of `/edit/check-conflicts` and
   `/edit/validate-recurrence`); the AppointmentSpec is unsaved, not
   a stored entity lookup.

### High value, not strictly blocking

5. **OpenAPI / SpringDoc** — **DONE 2026-05-11.** `springdoc-openapi-starter-webmvc-ui:3.0.0`
   added to `rapla-app` (runtime scope) with `spring-boot-validation`
   excluded; `/v3/api-docs` and `/swagger-ui/index.html` permitted
   in `SecurityConfig`. Auto-discovers all `@RestController` classes —
   **65 paths, 63 schemas** on first boot, no controller annotations
   required. The SPA team can run `openapi-generator-cli` against
   the live server to get a typed TS client.

   **Fat-JAR size impact: +4.73 MiB (+12.0%)** — 14 new BOOT-INF/lib
   jars (measured against a true no-SpringDoc clean rebuild; baseline
   39.55 MiB → 44.29 MiB):

   | Bytes | Jar | Why |
   |---:|---|---|
   | 1,702,030 | jackson-databind-2.21.2 | Jackson 2.x — swagger-core hasn't migrated to Jackson 3 |
   | 1,157,800 | swagger-ui-5.30.1 | Interactive `/swagger-ui/index.html` explorer |
   | 595,659 | jackson-core-2.21.2 | Jackson 2.x core |
   | 552,032 | springdoc-openapi-starter-common-3.0.0 | SpringDoc core (`OperationCustomizer`, model converters) |
   | 251,736 | swagger-core-jakarta-2.2.38 | OpenAPI model builders |
   | 140,065 | swagger-models-jakarta-2.2.38 | `OpenAPI`, `Schema`, `PathItem` POJOs |
   | 136,578 | jackson-datatype-jsr310-2.21.2 | Jackson 2.x time module |
   | 131,188 | jakarta.xml.bind-api-4.0.4 | JAXB API (swagger transitive) |
   | 106,136 | jakarta.validation-api-3.1.1 | Annotation classes (kept for schema reflection) |
   | 60,604 | jackson-dataformat-yaml-2.21.2 | Jackson 2.x YAML support |
   | 50,578 | swagger-annotations-jakarta-2.2.38 | `@Schema`, `@Operation`, etc. |
   | 44,622 | springdoc-openapi-starter-webmvc-api-3.0.0 | SpringDoc MVC binding |
   | 23,608 | springdoc-openapi-starter-webmvc-ui-3.0.0 | UI binding |
   | 8,676 | webjars-locator-lite-1.1.3 | Webjar URL resolver for swagger-ui |
   | **4,961,312** | **TOTAL** (4.73 MiB) | |

   **Bean-validation engine excluded** (`<exclusion>` on
   `org.springframework.boot:spring-boot-validation`): drops
   hibernate-validator (1.30 MiB), classmate (67 KiB), jboss-logging
   (61 KiB), spring-boot-validation (15 KiB). Saves ~2.54 MiB vs.
   the default SpringDoc dep cone. Safe because Rapla has zero
   `@Valid`/`@Validated` usage; SpringDoc only reflects on the
   annotation classes in `jakarta.validation-api`, which stay via
   `swagger-core-jakarta`'s independent pull. Re-add the engine
   (remove the exclusion) if/when bean validation is adopted.

   **Where the cost concentrates** — half of the cost is Jackson 2.x
   (~2.50 MiB), which is upstream-locked: Smartbear's swagger-core
   hasn't migrated to Jackson 3 yet, so SpringDoc 3.0.x for Spring
   Boot 4 still pulls Jackson 2.x for its OpenAPI model serialization.
   No swagger-core 3.x on Maven Central. The other big single item
   is swagger-ui (1.10 MiB) — droppable by switching artifact to
   `springdoc-openapi-starter-webmvc-api` if the interactive
   explorer isn't wanted; `/v3/api-docs` JSON still works without it.

   Follow-up: add `@Tag` / `@Operation` annotations on controllers
   as we touch them; configure an `OpenAPI` bean with title /
   version / contact info. Property `springdoc.api-docs.enabled=false`
   disables endpoints in prod (jars still ship). To strip from
   prod entirely, gate the dependency on a Maven profile.

6. **Per-entity computed permission flags.** Today
   `PermissionController.canModify`/`canDelete`/`canAllocateSlot`
   is client-side Java. Cheaper than re-porting: attach
   `computed: { canEdit, canDelete, canSeeAllocator }` to each
   entity in the JSON response, computed for the calling user.
   Avoids reimplementing the 8-level access-gradient logic in TS.

7. **DTO consolidation in rapla-core.** REST API reference notes
   some wire DTOs (e.g. `ExternalEventImportMetadata`) live in
   plugin packages, not `rapla-core/.../rest/dto/`. Hard to discover,
   hard to share. Move them all into one package; the OpenAPI spec
   then has a clean schema set.

8. **Locale-package completeness audit.** `/locale/{id}` returns
   `LocalePackage` keyed by message keys. The Swing client also
   pulls weekday/month names, attribute-type display labels, and
   several error templates directly from in-JVM `RaplaResources`
   bundles. Verify everything an SPA needs is reachable via
   `/locale`; add what's missing.

### Lower priority — fine to defer past v1

9. **Pick one write path.** `/storage/dispatch` (transactional
   bundle, Swing's choice) vs. `/events`+`/resources` (resource-style
   REST, PRD 009). Mixing them in the SPA risks optimistic-lock
   surprises. Either deprecate the resource-style endpoints or
   thin the dispatch path.

10. **CORS for prod.** `SecurityConfig.corsConfigurationSource`
    allows `*` origins. If SPA is hosted same-origin (recommended —
    e.g. `/spa/**` from rapla-app), no change needed. If
    cross-origin, lock down `setAllowedOriginPatterns` for prod.

11. **SSE / WebSocket for refresh.** Replace the 20–30 s long-poll
    with server push. Cuts visible latency on multi-user edits.
    v2 item.

12. **Tier-3 MockMvc coverage for `/storage/*`.** Per PRD 017, the
    storage controller is light on MockMvc tests. Add coverage
    before the SPA starts depending on these endpoints — gives a
    safety net for inevitable contract tweaks.

## Plan

To be drafted once the open questions are decided. The likely
shape:

- **Phase 0** — pick framework, pick coexistence model, pick
  hosting. Output: a revised plan section in this PRD.
- **Phase 1** — read-only reservation listing for one DynamicType,
  served from `/storage/resources` + `/storage/queryAppointments`.
  **Gated on pre-migration items 1–4 above.**
- **Phase 2** — create new reservation (single appointment,
  no repeat, no allocatable).
- **Phase 3** — repeating-rule editor with exception dates.
- **Phase 4** — allocatable selection + conflict overlay.
- **Phase 5** — edit existing reservation (clone-or-last-write
  decision from Phase 0 lands here).

## Tests

To be drafted per phase. The reservation-edit edge-case list in
[`reservation-edit.md`](../architecture/reservation-edit.md) is
the test charter — every bullet there is a behaviour the SPA must
reproduce, and each one warrants a regression test.
