# 026 — Angular frontend (reservation editing)

**Status:** in-progress (Phase 0 prototype landed 2026-05-12)

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

> §6 (framework) and §7 (hosting) decided 2026-05-12 — see
> [§Phase 0 — first prototype](#phase-0--first-prototype-decisions--plan).

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

   **OpenAPI exposure (follow-up):** the 409 mapping isn't yet
   visible in `/v3/api-docs` — the auto-derived spec only declares
   the 200 path. To surface it to the generated TypeScript client,
   add `@ApiResponse(responseCode = "409", description = "...")`
   on each endpoint that can throw `RaplaNewVersionException`. See
   §5 "Annotations on controllers" for the leaner-than-full-sweep
   recommendation.

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

   Follow-up — annotations on controllers. **Recommendation:**
   skip the full `@Tag` / `@Operation` / `@Parameter` sweep (mostly
   cosmetic — better Swagger UI grouping and prettier generated TS
   filenames, but the spec already works). Do the **leaner version**
   instead: just `@ApiResponse` for the documented error paths.

   The 200 path is fine from auto-discovery. The non-200 paths
   aren't — they're invisible to the SPA's generated client unless
   declared. Three concrete cases worth annotating today:

   ```java
   @PostExchange("/foo")
   @ApiResponse(responseCode = "200", description = "Success")
   @ApiResponse(responseCode = "401", description = "JWT missing or invalid")
   @ApiResponse(responseCode = "409",
                description = "Concurrent modification — another user changed the entity; refresh and retry")
   @ApiResponse(responseCode = "400",
                description = "Malformed request body or query")
   FooResult foo(@RequestBody FooReq req) throws RaplaException;
   ```

   The 409 row directly exposes the `RaplaNewVersionException` →
   HTTP 409 mapping from §B2 to the generated SPA client. Without
   it, the OpenAPI spec says "200 OK" only and the SPA author has
   to read the `RaplaExceptionHandler` source to learn about 409 /
   401 / 400. With it, the generated TypeScript client surfaces the
   error codes as discriminated union variants.

   **Cost:** one new compile-scope dep on rapla-server
   (`io.swagger.core.v3:swagger-annotations-jakarta`, ~50 KiB —
   today only on runtime classpath via springdoc). 3–5 annotations
   per endpoint that can return non-200 — verbose but stable.

   **What to annotate first:** the three new endpoints from PRDs
   024 + 026 §B2/§B4: `/edit/check-conflicts`, `/edit/validate-recurrence`,
   `/edit/expand-blocks`, `/calendar/view`. All have at least 401
   and 400 paths; the conflict-check + draft-save endpoints can also
   hit 409. **Skip the legacy endpoints** (`/storage/*`) — they're
   not part of the new Angular surface and annotating them adds
   noise without benefit.

   **Not recommended:** the broader `@Tag` / `@Operation` /
   `@Parameter` sweep. The auto-derived spec already gives Angular
   typed clients; the cost (boilerplate × every endpoint, drift
   risk between annotation text and behaviour) outweighs the
   payoff (slightly nicer file/method names in the generated TS).
   Revisit when the SPA team actually imports the generated client
   and the auto-derived names bite.

   Property `springdoc.api-docs.enabled=false` disables endpoints
   in prod (jars still ship). To strip from prod entirely, gate the
   dependency on a Maven profile.

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
    e.g. `/app/**` from rapla-app), no change needed. If
    cross-origin, lock down `setAllowedOriginPatterns` for prod.

11. **SSE / WebSocket for refresh.** Replace the 20–30 s long-poll
    with server push. Cuts visible latency on multi-user edits.
    v2 item.

12. **Tier-3 MockMvc coverage for `/storage/*`.** Per PRD 017, the
    storage controller is light on MockMvc tests. Add coverage
    before the SPA starts depending on these endpoints — gives a
    safety net for inevitable contract tweaks.

## Option-panel migration progress (2026-05-12)

The Swing option panels currently fetch their initial state from the
bulk `/storage/resources` preference cache. The Angular SPA can't use
that path, so each panel is being moved to a dedicated REST endpoint.
**Save paths are intentionally unchanged** — every panel still writes
to the editable preferences clone and the dialog framework saves via
`facade.store(clone)` → `/storage/dispatch`. Migration scope per panel
is read-only: cold-load values from REST so the panel is renderable
without the full preferences bundle.

### Migrated

| Panel | Scope | Endpoint | DTO | Notes |
|---|---|---|---|---|
| `RaplaStartOption` | admin (SystemOptionPanel) | `GET /settings/system` | `SystemSettings` (rapla-core) | title, timezone, locale, charsets, refresh-interval |
| `UserOption` | per-user (UserOptionPanel) | `GET /settings/me` | `UserSettings` (rapla-core) | language (re-uses the same DTO as `WarningsOption`) |
| `WarningsOption` | per-user (UserOptionPanel) | `GET /settings/me` | `UserSettings` (rapla-core) | language + 5 warning flags |
| `EventTimeCalculatorUserOption` | per-user (UserOptionPanel) | `GET /eventtimecalculator/{system,user}-config` | `DefaultConfiguration` | user-config returns null when no override (falls back to system-config) |
| `Export2iCalUserOption` | per-user (UserOptionPanel) | `GET /ical/config/{default,user}` + `GET /plugins/{id}` (`isEnabled`) | `DefaultConfiguration` + `UserICalSettings` (rapla-core) | system defaults via `/default` (any user, existing); user overrides via new `/user` endpoint returning nullable `(daysBefore, daysAfter, exportAttendees, participationStatus)` |
| `ExchangeConnectorUserOptions` | per-user (UserOptionPanel) | `GET /exchange/config/user` | `ExchangeUserSettings` (rapla-core) | single live key (`EXCHANGE_SEND_INVITATION_AND_CANCELATION`); nullable Boolean signals "no override → fall back to plugin default" |
| `IcalPublishExtensionFactory` | per-user (factory gate) | `GET /ical/system-config` | `DefaultConfiguration` | factory-side gate for publish menu visibility |

Plus six plugin-wizard / menu factories that gate visibility on
`PluginsService.list()` rather than the prefs cache: `CSVExportMenu`,
`AppointmentNoteEditFactory`, `PlanningStatusPublishExtensionFactory`,
`PlanningStatusMenuFactory`, `TemplateWizard`, `DefaultWizard`
(+ `ExternalEventImportWizard` constructor signature follow-up).

### Deferred — custom widgets, migrate as part of SPA Phase 4–5

| Panel | Scope | Reason for deferral |
|---|---|---|
| `TableviewOption` | admin plugin-config | Per-row column-config builder UI; the Angular table-view PRD will replace it wholesale rather than migrating in place |
| `CalendarOption` | admin + per-user | Heavyweight cell/grid configurator with timeslot strategy picker — too entangled with Swing model classes for a thin REST migration |
| `NotificationOption` | per-user | Custom resource-picker widget + per-resource notification-rule editor |

### Not migrated — no behaviour win

The save path already goes via `/storage/dispatch`; the prefs clone
is populated for free at dialog open. These read paths give the SPA
team nothing they wouldn't already have from `/storage/resources` /
`/plugins/{id}`.

`view-factory option panels`, `ImportTemplateMenu`.

### Pattern for new panel migrations

1. Add `@HttpExchange` interface in `rapla-core/.../rest/` (or the
   plugin's `rapla-core` package for plugin-scoped state).
2. Add `@RestController` in `rapla-server/.../web/` that
   `session.checkAndGetUser(request)` on every method.
3. Register the proxy bean in
   `rapla-client/.../spring/ClientProxyConfig.java`.
4. Inject the service interface via constructor on the panel.
5. Use REST as the primary read in `show()`; fall back to
   `preferences.getEntry(...)` on `RestClientException` so the
   dialog still opens if the endpoint is unreachable.
6. **Do not** touch `commit()` — the dialog framework's atomic-save
   contract via `facade.store(clone)` stays as the single write
   path.

Tier-3 MockMvc test for the new controller; smoke-test by starting
the Swing client and opening the corresponding options dialog.

### Session handoff TODOs (2026-05-12)

State at session end: **all read-only-migratable panels are done.**
Server controllers exist, client panels call them, smoke-tested via
clean build + `mvn spring-boot:run` + `mvn exec:java`. Each migrated
`show()` has a temporary INFO log line to verify the path fires.

**Verification still owed by the user (open in the running Swing client):**

Edit → Settings → click through each tab, then `grep "fetching" logs/rapla-client.log`. Expected matches:

- `RaplaStartOption.show(): fetching /settings/system via REST`
- `UserOption.show(): fetching /settings/me via REST`
- `Export2iCalUserOption.show(): fetching /ical/config/{default,user} via REST`
- `ExchangeConnectorUserOptions.setValuesToJComponents(): fetching /exchange/config/user via REST`

(`WarningsOption` + `EventTimeCalculatorUserOption` were migrated
without log lines — verify by inspecting `logs/rapla.log` for
`/settings/me` and `/eventtimecalculator/*-config` GETs instead.)

**Cleanup once verified:**

1. **Remove the four temporary INFO log lines** added during session:
   - `rapla-client/.../swing/internal/RaplaStartOption.java` in `show()`
   - `rapla-client/.../swing/internal/UserOption.java` in `show()`
   - `rapla-client/.../export2ical/client/swing/Export2iCalUserOption.java` in `show()`
   - `rapla-client/.../exchangeconnector/client/swing/ExchangeConnectorUserOptions.java` in `setValuesToJComponents()`

   They're INFO-level and only fire when the dialog opens — leaving
   them costs nothing but they are debug code, not load-bearing.

**Coverage gaps to close in a follow-up session:**

2. **Tier-3 MockMvc tests for the new endpoints.** Per PRD 017, every
   new controller method needs MockMvc coverage. This session shipped
   six endpoints with **zero new tests**:
   - `SettingsController.getSystem` / `getMe` (the `/me` PUT path was
     unchanged but the GET is new)
   - `EventTimeCalculatorConfigController.getSystemConfig` / `getUserConfig`
   - `ICalConfigController.getUserSettings` (new `/user` method)
   - `ExchangeConnectorConfigController.getUserSettings` (new `/user` method)

   Minimum per endpoint: 200-path JSON-shape assertion + 401 path (no JWT).
   `/settings/system` PUT also needs the admin-only 403 path.

3. **No `@Tag` / `@Operation` annotations on the new endpoints.** Per
   §High-value item 5, the recommendation is the leaner `@ApiResponse`
   sweep, not full annotation. Not done for any of the six new
   endpoints — fine for internal use, will matter when the SPA
   generates a TypeScript client off `/v3/api-docs`.

4. **Permission-leak audit per §Rule 12** — none of the new
   per-user endpoints were tested with a non-admin user requesting a
   different user's data. The endpoints all use
   `session.checkAndGetUser(request)` and read
   `facade.getPreferences(user)` for that same user, so the shape is
   correct — but the test that proves it is missing.

**Panels remaining (no work planned, listed for completeness):**

- `CalendarOption`, `NotificationOption`, `TableviewOption` —
  deferred to be replaced by Angular components rather than migrated.
- `view-factory option panels`, `ImportTemplateMenu` — not migrated,
  no behaviour win (save path already goes via REST).

**Next pre-migration item to pick up** (per §Pre-migration above):

After this session the option-panel slice is complete. The remaining
high-impact pre-migration work is:

- **Item 1 (blocking)** — PRD 024 phases 1+2 (edit-time business
  logic to REST). Largest piece of work; SPA Phase 1 is gated on it.
- **Item 3 (blocking)** — `POST /storage/draft` new-reservation
  endpoint.
- **Item 6** — per-entity computed permission flags
  (`canEdit`/`canDelete`/`canSeeAllocator`) on JSON responses.
- **Item 7** — DTO consolidation (`ExternalEventImportMetadata` etc.
  move into `rapla-core/.../rest/dto/`).
- **Item 8** — `/locale/{id}` completeness audit.

**Git state at session end:** new/modified files this session, not yet committed:

- New files:
  - `rapla-core/.../rest/dto/SystemSettings.java`
  - `rapla-core/.../plugin/export2ical/UserICalSettings.java`
  - `rapla-core/.../plugin/exchangeconnector/ExchangeUserSettings.java`
- Modified:
  - `rapla-core/.../rest/SettingsService.java` (added `getSystem`)
  - `rapla-core/.../plugin/export2ical/ICalConfigService.java` (added `getUserSettings`)
  - `rapla-core/.../plugin/exchangeconnector/ExchangeConnectorConfigRemote.java` (added `getUserSettings`)
  - `rapla-server/.../web/SettingsController.java` (moved `SystemSettings` record to rapla-core)
  - `rapla-server/.../web/EventTimeCalculatorConfigController.java` (`/user-config` null on no override)
  - `rapla-server/.../web/ICalConfigController.java` (new `/user` endpoint)
  - `rapla-server/.../web/ExchangeConnectorConfigController.java` (new `/user` endpoint)
  - `rapla-client/.../swing/internal/RaplaStartOption.java`
  - `rapla-client/.../swing/internal/UserOption.java`
  - `rapla-client/.../swing/internal/WarningsOption.java`
  - `rapla-client/.../export2ical/client/swing/Export2iCalUserOption.java`
  - `rapla-client/.../exchangeconnector/client/swing/ExchangeConnectorUserOptions.java`
  - `rapla-client/.../eventtimecalculator/client/swing/EventTimeCalculatorUserOption.java`
  - `rapla-client/.../client/spring/ClientProxyConfig.java` (3 new proxy beans this session sequence)
  - This PRD

User has **not** asked for a commit yet — branch is `spring-boot`.

## Phase 0 — first prototype (decisions + plan)

**Status:** ready to start (2026-05-12). Goal: one end-to-end thread
proving the toolchain — Angular dev workflow, JWT auth via REST,
typed client generated from `/v3/api-docs`, served same-origin from
rapla-app — for one user-visible feature (read-only reservation
listing).

### Decisions (closes Open questions §6, §7)

| Question | Decision | Rationale |
|---|---|---|
| Framework | **Angular** | PRD's working assumption; RxJS aligns with rapla's RxJava patterns; `openapi-generator` has mature `typescript-angular` templates. |
| Hosting | **Same-origin from rapla-app at `/app/**`** | No CORS work; matches `SecurityConfig` shape; SPA assets ship inside the existing fat JAR with no extra deploy step. |
| Prototype scope | **Login + read-only reservation list** | Smallest slice that exercises auth + REST + typed-client + render. Open questions §1, §2, §4 (edit-time concurrency / recurring-exception UX / per-field permissions) defer until edit work begins. |

### What needs to be installed on the dev machine

Once-off. Use **nvm** so the Node version is per-project (`.nvmrc`
ships with the scaffold), Node upgrades don't need sudo, and we
track Node LTS rather than whatever the OS package happens to be.

```bash
# 1. System library (nvm downloads dynamically-linked Node binaries
#    that need libatomic regardless of version)
sudo apt-get install -y libatomic1

# 2. nvm
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
exec bash -l        # reload PATH

# 3. Node LTS (22.x today; tracks the `.nvmrc` we'll commit later)
nvm install --lts
nvm use --lts
nvm alias default lts/*

# 4. Global CLIs (no sudo — land under ~/.nvm)
npm install -g @angular/cli @openapitools/openapi-generator-cli

# 5. Verify
node --version && npm --version && ng version && openapi-generator-cli version
```

Why LTS over Current: Node 25 ("Current") loses support ~April 2026;
Node 22 (active LTS) is maintained through 2027. The SPA outlives
either, so we follow LTS.

No new Maven deps for the prototype. SpringDoc is already wired
(see §Pre-migration item 5) — `/v3/api-docs` returns the full spec
today. `frontend-maven-plugin` integration is deferred until the
prototype graduates from manual `ng build`.

### Project layout

Top-level sibling directory `rapla-angular/`, **not** a Maven module —
parallel to the existing reactor modules but with its own toolchain.

```
rapla/
├── rapla-bom/                          # (Maven)
├── rapla-core/                         # (Maven)
├── rapla-client/                       # (Maven — Swing UI; "client" is overloaded but kept for history)
├── rapla-server/                       # (Maven)
├── rapla-app/                          # (Maven — Spring Boot fat JAR)
│   ├── src/main/java/...               # incl. DevSpaResourceConfig (@Profile("dev"))
│   └── src/main/resources/static/app/  # ONLY populated in distribution builds; gitignored
└── rapla-angular/                          # NEW — Angular source tree (gitignore node_modules + dist)
    ├── angular.json                    # default outputPath: dist/rapla-angular
    ├── package.json
    ├── .nvmrc
    ├── README.md
    └── src/app/
        ├── auth/                       # login + JWT interceptor
        ├── api/                        # generated TS client (gitignored)
        └── reservations/               # read-only list
```

Rationale: clean separation of toolchains; frontend contributors
can open just `rapla-angular/` in their IDE without Java tooling;
easier to extract later if the team splits. No cross-module
`outputPath` write — `ng build` stays inside `rapla-angular/`, and
Spring picks up the result by configuration (dev) or by Maven copy
(distribution).

### Dev-mode vs. distribution-mode wiring

Two code paths serve the same `/app/**` URL:

**Dev mode (`@Profile("dev")` active):**

```java
@Configuration
@Profile("dev")
public class DevSpaResourceConfig implements WebMvcConfigurer {
    @Value("${rapla.spa.dev-dir:./rapla-angular/dist/rapla-angular/browser/}")
    private String devDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/**")
            .addResourceLocations("file:" + devDir)
            .setCachePeriod(0);
    }
}
```

Developer workflow:

```bash
# Terminal 1 — Angular keeps rebuilding incrementally
cd rapla-angular && ng build --watch

# Terminal 2 — Spring serves /app/** from rapla-angular/dist/rapla-angular/browser/
mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false
```

Edit `.ts`/`.html` → Angular rebuilds (~200 ms) → browser refresh
shows the change. No server restart.

**Distribution mode (no dev profile; fat JAR):**

`mvn -Pspa package` triggers two plugins in `rapla-app/pom.xml`:

1. `frontend-maven-plugin` — downloads a pinned Node, runs
   `npm ci && npm run build -- --configuration=production` in
   `../rapla-angular/`, output lands in `rapla-angular/dist/rapla-angular/browser/`.
2. `maven-resources-plugin` — copies that directory into
   `target/classes/static/app/` during `process-resources`.

Result: the fat JAR contains the built SPA at `/static/app/`, and
Spring Boot's default static-resource handler serves it from the
classpath. `DevSpaResourceConfig` isn't created (no dev profile),
so there's no conflict.

Gating behind a Maven profile (`-Pspa`) keeps `mvn test` /
`mvn compile` free of any Node download or `npm install` —
the SPA build only fires when explicitly requested or in CI's
distribution-build lane.

### Server-side changes

1. **Permit `/app/**`** in `rapla-server/.../SecurityConfig.java:26`.
   Add `/app/**` to the existing `permitAll` list (alongside
   `/Rapla/**`, `/webclient/**`, `/jsclient/**`). REST calls from
   the SPA still hit `/auth/**`, `/storage/**`, `/edit/**` and
   respect the existing JWT gate.
2. **`index.html` fallback for Angular pushState routing.** Angular
   router uses URLs like `/app/reservations/123`; Spring Boot's
   default static handler returns 404 for unmatched paths. Add a
   `WebMvcConfigurer` that forwards `/app/**` non-asset requests to
   `/app/index.html`. (Alternative: hash-routing, uglier URLs, no
   server change. Recommendation: pushState + the forward.)
3. **No new auth beans.** `POST /auth/login` already returns
   `{accessToken, ...}` per AGENTS.md §8 — the SPA `fetch`es it and
   stores the token in `localStorage`. Refresh-token rotation is
   out of scope for the prototype; access-token expiry → re-login.

### Plan (ordered, ~1 day each)

1. **Scaffold.** `ng new rapla-angular --routing --style=css --strict --directory rapla-angular` from the repo root. Keep the default `outputPath: dist/rapla-angular`; set `baseHref: "/rapla/app/"`. Root `.gitignore`: `rapla-angular/node_modules/`, `rapla-angular/dist/`, `rapla-angular/src/app/api/`, `rapla-app/src/main/resources/static/app/`.
2. **Wire dev-mode serving.** Add `DevSpaResourceConfig` in `rapla-app` (per §Dev-mode vs. distribution-mode wiring above). Add `/app/**` to the `permitAll` list at `rapla-server/.../SecurityConfig.java:26`. Add the `index.html` fallback `WebMvcConfigurer` (also dev-profile only — distribution mode adds its own from classpath). Restart server with `dev` profile active, run `ng build --watch` in `rapla-angular/`, verify `http://localhost:8051/rapla/app/` serves the default Angular landing page.
3. **Generate typed client.** `openapi-generator-cli generate -i http://localhost:8051/rapla/v3/api-docs -g typescript-angular -o src/app/api/`. Wire as an Angular module. Add an npm script (`npm run gen:api`) for reproducibility.
4. **Login.** Minimal form (username/password) → `POST /auth/login` → store `accessToken` in `localStorage` → navigate to `/reservations`. HTTP interceptor adds `Authorization: Bearer …` to outgoing requests.
5. **Reservation list.** Call the reservation-query REST endpoint(s) the generated client exposes (resource hydrate via `/storage/resources`, then appointment query for a fixed 30-day window). Render in a plain HTML table — date, title, allocatables. No styling beyond default.
6. **Smoke (dev).** With `ng build --watch` running, open `http://localhost:8051/rapla/app/`, log in as `admin` (empty password — AGENTS.md §8), confirm the table populates.
7. **Smoke (distribution).** `mvn -Pspa -pl rapla-app -am package`, then `java -jar rapla-app/target/rapla-2.1-SNAPSHOT.jar` with the dev profile disabled. Same smoke check confirms the SPA is bundled and the classpath path works.

### Tests

- **Server:** one tier-3 MockMvc test asserting `GET /app/index.html` returns 200 with `Content-Type: text/html` (proves SecurityConfig + static handler wiring). Don't test SPA contents — that drifts.
- **Client:** `ng test` skeleton from `ng new` is fine; no real coverage for the prototype. Real test discipline starts when Phase 1 begins.

### Out of scope for the prototype

- Hot-reload `ng serve` proxy → 8051. Manual `ng build` + restart is good enough for one engineer; wire `frontend-maven-plugin` + dev proxy when more than one person edits the SPA.
- Refresh-token / silent re-auth.
- Permission-aware UI gating (§Pre-migration items 6 + 12).
- Edit / create flows (open questions §1, §2, §4).
- Production CSP / CSRF tightening on `/app/**` (item 10).
- Locale / i18n — runs in English only.

### Exit criteria

The prototype is "done" when, on a fresh checkout:

**Dev path:**
1. `apt-get install libatomic1` and `nvm use --lts`
2. `npm install -g @angular/cli @openapitools/openapi-generator-cli`
3. `cd rapla-angular && npm install && npm run gen:api`
4. Terminal A: `cd rapla-angular && ng build --watch`
5. Terminal B: `mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false` from repo root (dev profile active by default)
6. Open `http://localhost:8051/rapla/app/`, log in as `admin` (empty password — AGENTS.md §8), see the reservation table populate.

**Distribution path:**
1. `mvn -Pspa -pl rapla-app -am package` (downloads pinned Node, runs `npm ci && ng build`, copies output into the fat JAR)
2. `java -jar rapla-app/target/rapla-2.1-SNAPSHOT.jar`
3. Same smoke check confirms the SPA is bundled and served from the classpath.

A short `rapla-angular/README.md` documents both workflows. This PRD is
updated with what we learned (typed-client gotchas, openapi-generator
template quirks, etc.) before Phase 1 starts.

## Post-prototype URL-layout decisions (2026-05-12)

Discussed and documented here for traceability; **execution lives in
[PRD 031 — API namespace redesign](031-api-namespace-redesign.md)**.
**Phases 1+2+3+4 landed 2026-05-12.** URL layout is now:

| Mode | SPA | REST |
|---|---|---|
| Dev (`ng serve`) | `http://localhost:4200/app/` | `http://localhost:4200/api/...` (proxied to :8051) |
| Prod (Spring) | `http://localhost:8051/app/` | `http://localhost:8051/api/...` |
| Legacy (preserved) | — | `http://host:8051/rapla/{calendar,ical,…}` |

### Decisions

- **SPA mount renamed `/spa/` → `/app/`** — framework-agnostic name
  (survives any future framework swap), avoids leaking "SPA" as an
  architectural choice in the URL. Already applied to
  `SecurityConfig`, `SpaResourceConfig`, and `angular.json`
  `baseHref`. Current URL: `http://localhost:8051/rapla/app/`.
- **Target URL parity between dev and prod.** Once PRD 031 lands,
  both modes use the same paths:
  - SPA: `:4200/app/` (ng serve) ⇄ `:8051/app/` (Spring fat JAR)
  - REST: `:4200/api/auth/login` ⇄ `:8051/api/auth/login`
  - OAuth2: `:4200/oauth2/...` ⇄ `:8051/oauth2/...` (proxied in dev)
  - Legacy iCal/calendar: `:4200/rapla/ical` ⇄ `:8051/rapla/ical`
- **`ng serve` proxy mode** is the planned dev workflow once URL
  parity is achievable. Until PRD 031 Phases 1+2 land, the prototype
  stays on the `ng build --watch` + Spring static handler pattern
  documented above (works today; same-origin; lower iteration speed
  than HMR).
- **Static cleanup done as a prerequisite** (2026-05-12):
  - Deleted `static/Rapla/` (GWT artifacts), `static/jsclient/`,
    `static/rapla.html`, `static/apiTest.html`, four unused icons
    from `static/images/`.
  - `RaplaAuthRestPage:94` fallback URL updated from `apiTest.html`
    to `swagger-ui/index.html`.
  - `SecurityConfig` permit list: `/Rapla/**` and `/jsclient/**`
    removed.
  - Total: ~416 KB out of the fat JAR.

### What's blocked on PRD 031

| Phase 0 task | Blocked by | Notes |
|---|---|---|
| `proxy.conf.json` + `npm start` script | PRD 031 Phases 1+2 | Without context-path drop + `/api/` prefix, dev/prod URLs would diverge; not worth wiring twice |
| `ng serve --serve-path /app/ --base-href /app/` | PRD 031 Phase 1 | Need root-level `/app/` mount in prod for the dev URL to match |
| `frontend-maven-plugin` distribution build wiring | None | Independent — can land any time |
| Production CSP / CSRF tightening | PRD 031 | URL paths change with the redesign |

### OAuth2 / OIDC wired into the SPA (2026-05-12)

The Phase 0 prototype's raw `/api/auth/login` (username/password)
flow has been replaced with the proper OAuth2 Authorization Code +
PKCE flow. Implementation is **IdP-agnostic** — bundled Spring
Authorization Server today, ready to swap to Keycloak / Auth0 / etc.
by changing server-side properties only (no SPA rebuild needed).

**Client side:**

- **`angular-oauth2-oidc` ^20.0.2** added as runtime dependency.
- `AuthService` reduced to a thin wrapper around `OAuthService` —
  `signIn()` calls `initCodeFlow()`, `signOut()` calls `logOut()`,
  `token()` returns the access token.
- `app.config.ts` initializer **fetches the endpoint set from
  `/api/auth/oauth/config`** at boot, then configures the library
  with those URLs. No use of OIDC discovery (`/.well-known/...`)
  because angular-oauth2-oidc's `loadDiscoveryDocument` does an
  unconditional `doc.issuer === this.issuer` check that fails in
  the dev-proxy + non-matching-origin case.
- JWKS is fetched manually from `cfg.jwksUrl` and assigned to
  `oauthService.jwks` for ID-token signature verification.
- `skipIssuerCheck: true` bypasses ID-token `iss` claim comparison
  (the AS issues tokens with its own server hostname, which
  doesn't match `window.location.origin` in dev-proxy mode).
- New `/app/auth/callback` route → `CallbackComponent` (thin
  "Signing in…" view) navigates to `/reservations` once the
  library has exchanged the code for tokens.
- `LoginComponent` is a single "Sign in" button.
- `authInterceptor` reads `OAuthService.getAccessToken()`; on 401
  outside auth/OIDC paths, signs out and bounces to `/login`.

**Server side:**

- **`server.forward-headers-strategy: FRAMEWORK`** in
  `application.yml` — Spring honours `X-Forwarded-{Host,Port,Proto}`
  on incoming requests. Combined with `proxy.conf.js`'s `xfwd: true`
  this makes the dev server return `:4200` URLs in dev (proxy
  origin) and the real public origin in prod.
- **`OAuthConfigController`** at `GET /api/auth/oauth/config`
  returns a complete endpoint set: `issuer`, `authorizeUrl`,
  `tokenUrl`, `refreshUrl`, `logoutUrl`, `jwksUrl`, `userinfoUrl`,
  `endSessionUrl`, `clientId`, `scopes`. Each URL has a
  corresponding `rapla.oauth.*-url` property override — Keycloak
  swap is `rapla.oauth.authorize-url=...`, `rapla.oauth.token-url=...`,
  etc. in `application.yml` (or env vars in CI/prod), no code change.
- **Loopback `/app/auth/callback` redirect URIs** registered for
  `rapla-client`: `localhost`, `127.0.0.1`, `localhost:4200`,
  `127.0.0.1:4200` (the explicit `:4200` entries cover dev mode
  since spring-security-oauth2-authorization-server 7.0.5's default
  loopback any-port matching doesn't always apply on the issuer
  side).

**Dev proxy (`proxy.conf.js`):**

- Forwards `/api`, `/oauth2`, `/.well-known`, `/userinfo`, `/connect`,
  `/swagger-ui`, `/v3`, `/rapla`, `/raplaclient`, `/webclient`,
  `/login`, `/logout`, `/error`, `/server`, `/index` to `:8051`.
- `xfwd: true` sets `X-Forwarded-*` headers so Spring's
  `forward-headers-strategy` knows the proxy origin.
- `cookieDomainRewrite: 'localhost'` keeps Spring's `JSESSIONID`
  cookie scoped correctly.
- `onProxyRes` rewrites any absolute `http://localhost:8051` in
  `Location` / `Content-Security-Policy` response headers back to
  `http://localhost:4200` (belt-and-suspenders alongside the
  forward-headers strategy).

**Outcome:** all OAuth traffic from the SPA flows through the proxy
in dev (no CORS), no `localhost:8051` URLs in the browser's address
bar during sign-in, and the IdP is swappable via property overrides.

### What the prototype proved (and now sticks)

- `BASE_PATH` override in `app.config.ts` works correctly (currently
  `/rapla`, becomes `/api` after PRD 031 Phase 2).
- The `SpaResourceConfig` filesystem-first / classpath-fallback
  pattern is robust (verified across multiple restarts).
- `npm run gen:api` against `/v3/api-docs` produces a clean
  TypeScript client; the `BASE_PATH` injection point + Bearer
  interceptor pattern keep auth wiring framework-idiomatic.
- The full-stack login → reservation-list flow round-trips end-to-end.

## Plan (post-prototype)

After Phase 0 ships, draft Phases 1–5 against the decisions above.
Likely shape:

- **Phase 1** — production-grade reservation listing for one
  DynamicType, with filtering. **Gated on §Pre-migration items 1
  and 3 (PRD 024 phases 1+2 + draft endpoint).**
- **Phase 2** — create new reservation (single appointment,
  no repeat, no allocatable).
- **Phase 3** — repeating-rule editor with exception dates.
- **Phase 4** — allocatable selection + conflict overlay.
- **Phase 5** — edit existing reservation (clone-or-last-write
  decision from Open question §1 lands here).

## Tests

To be drafted per phase. The reservation-edit edge-case list in
[`reservation-edit.md`](../architecture/reservation-edit.md) is
the test charter — every bullet there is a behaviour the SPA must
reproduce, and each one warrants a regression test.
