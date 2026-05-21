# PRD 049 — One class per REST endpoint: collapse delegates into controllers

**Status:** in-progress (Phases 0-1, 3-6 landed 2026-05-21; Phase 2 partial — see notes)
**Date:** 2026-05-21
**Related:** PRD 009 (server bulk storage REST), PRD 031 (API namespace), PRD 041 (OpenAPI runtime removal)

## Goal

Every REST endpoint group in rapla should be **one class**: a Spring
`@RestController` that implements the corresponding `@HttpExchange` interface
from `rapla-core`. The interface owns path + verb + parameter binding; the
controller holds the implementation. No `*Impl`, no `*RestPage`, no
`*PageGenerator`, no `@Bean` factory wiring the delegate as a separate bean —
unless the delegate has a real in-process caller other than the controller.

Pre-existing `@HttpExchange` interfaces in `rapla-core` are already the
abstraction across the wire — the Swing client builds an HTTP proxy via
`HttpServiceProxyFactory`, the Angular SPA generates a client from the OpenAPI
doc. They are NOT in-process call sites; the server side has no reason to
keep a second class behind the controller just because the client speaks to
the interface.

## Why

The current arrangement has up to three classes per endpoint group:
1. `@HttpExchange` interface in `rapla-core` — client-side contract.
2. `@RestController` in `rapla-server/.../web/` — declares path + verb +
   params *again*, body forwards to a delegate.
3. `*Impl` / `*RestPage` / `*PageGenerator` in `rapla-server/.../internal/`
   or `.../endpoints/server/` or `.../plugin/**/server/` — holds the logic.

Path + verb + params are declared twice (interface + controller). Logic lives
one level removed from the routing surface. For every endpoint that doesn't
need polymorphism, this is pure historical baggage from the pre-Spring era
(JSON-RPC interface impls under Guice, RESTEasy `@Path` classes, servlet
page-generator framework). Concretely:

- **Drift**: a path renamed on the interface doesn't break the server, and
  vice versa. Swing and Angular can quietly speak different dialects against
  the same backend until one breaks in production.
- **Security distance**: tier-3 MockMvc leak tests (§12) sit at the
  controller. The actual permission checks (`session.checkAndGetUser`,
  `permissionController.canRead`, `securityManager.checkWritePermissions`)
  sit one delegate hop away. Bringing them into the same class as the test
  target tightens the audit surface.
- **Dead annotations**: the 3 `*RestPage` classes carry JAX-RS
  `@Path`/`@GET`/`@POST`/`@PathParam`/`@QueryParam` that Spring doesn't
  honour. The 5 `*PageGenerator` classes carry the same. The
  `@HttpExchange` interfaces' Spring-equivalent annotations are what actually
  routes traffic.

PRDs 031 (URL namespace), 041 (springdoc runtime removal), and 009 (bulk REST)
stabilised the URL space and the OpenAPI surface — this is the moment to
consolidate before more endpoints land.

## Scope

### In scope — collapse (delete delegate, controller is the implementation)

For each of the following, the delegate has **zero in-process callers** other
than its matching controller. The `@HttpExchange` interface stays; everything
else fuses into the controller.

**Pattern A — `*Impl` delegates with single caller (4 + 1-pending):**
| Controller | Delegate to delete |
|---|---|
| `RemoteStorageController` | `RemoteStorageImpl` (22 methods) |
| `RemoteLocaleController` | `RemoteLocaleServiceImpl` |
| `RemoteLoggerController` | `RemoteLoggerImpl` |
| `JNDIConfigController` | `RaplaJNDITestOnLocalhost` |
| `MailConfigController` | `RaplaConfigServiceImpl` ⚠️ verify single impl of `MailConfigService` interface first |

**Pattern A — interface-only delegates (3, no `*Impl` to delete, just declare `implements` on the controller):**
| Controller | Interface |
|---|---|
| `ICalImportController` | `ICalImport` (sole impl: `RaplaICalImport` — also collapse into controller) |
| `ICalTimezonesController` | `ICalTimezones` |
| `UrlEncryptionController` | `UrlEncryption` |

**Pattern A — controllers already without a delegate (8):**
`PluginsController`, `SettingsController`, `ICalConfigController`,
`ExportController`, `TableViewController`, `CalendarViewController`,
`EventTimeCalculatorConfigController`, `ExchangeConnectorConfigController`,
`ReservationEditController`. These need the interface `implements` step and
the removal of redundant Spring routing annotations, but no delete.

**Pattern B — `*RestPage` delegates (3):**
| Controller | Delete | Notes |
|---|---|---|
| `RaplaResourcesController` | `RaplaResourcesRestPage` | Extract `getClassificationFilter(...)` first — it's a `public static` helper, find callers |
| `RaplaEventsController` | `RaplaEventsRestPage` | |
| `RaplaDynamicTypesController` | `RaplaDynamicTypesRestPage` | |

For Pattern B the `@HttpExchange` interface doesn't exist yet — extract one
into `rapla-core/.../rest/` per endpoint as part of the collapse.

**Pattern C — `*PageGenerator` delegates (5):**
| Controller | Delete |
|---|---|
| `IndexPageController` | `RaplaIndexPageGenerator` |
| `StatusPageController` | `RaplaStatusPageGenerator` |
| `RaplaJNLPController` | `RaplaJNLPPageGenerator` (with its `RaplaJNLPPageGeneratorTest` rewritten as tier-3 MockMvc) |
| `CalendarPageController` | `CalendarPageGenerator` (KEEP the `HTMLViewPage` plugin SPI dispatch — that's a real extension point) |
| `Export2iCalController` | `Export2iCalServlet` |

Pattern C returns HTML / iCal / JNLP, not JSON. These controllers don't have
`@HttpExchange` interfaces (they're not on the SPA codegen / Swing proxy
surface — `CalendarPageController` and `Export2iCalController` serve external
iCal subscribers per AGENTS.md §15) and don't need one. Just inline.

### Pattern D — cleanup deletions

- `RaplaAuthRestPage` (`@Path("login")`) — `POST /api/auth/login` was removed
  in PRD 041→043 cleanup. Verify zero callers, delete.
- `MessageHTMLWriter`, `RestWebApplicationExceptionMapper` (RESTEasy
  `@Provider` SPI) — Spring Boot 4 has no JAX-RS runtime. Verify zero
  Spring autoconfig wiring, delete. `RaplaExceptionHandler` is the live
  replacement.
- `org.rapla.rest.PATCH` — custom JAX-RS `@HttpMethod` annotation. If no live
  references, delete (use Spring's `@PatchMapping`).
- `@Context HttpServletRequest` → plain `HttpServletRequest` sweep across
  the ~11 surviving classes that still inject it via JAX-RS.
- Empty package deletions: `org.rapla.endpoints.server`,
  `org.rapla.server.provider.resteasy`.
- Three commented-out `servlet.*` lines in `RaplaRpcAndRestProcessor:82,88,92`.
- **`jakarta.ws.rs-api` dependency removal** — once zero `import jakarta.ws.rs.*`
  remain in the reactor (audit grep confirms), delete:
  - `<dependency>` block in `rapla-core/pom.xml`
  - `jakarta.ws.rs.version` property + `dependencyManagement` entry in `rapla-bom/pom.xml`
  - `jakarta.ws.rs-api` from `includeArtifactIds` of the JNLP `dependency:copy-dependencies` step in `rapla-app/pom.xml`
  - The Keycloak NTLM submodule (`tools/keycloak/ntlm-authenticator/`) uses Keycloak's own JAX-RS surface and is independent of the reactor — leave alone.

### Out of scope — keep the delegate (real in-process callers / SPI)

| Class | Reason |
|---|---|
| `ArchiverServiceImpl` | `ArchiverServiceTask:66,72` (background task), `ArchiverPreferencesPanel:59` (Swing config UI) call it in-process |
| `MailToUserImpl` | `NotificationService:58`, `SynchronisationManager:98` call it in-process for server-internal mail |
| `ExternalEventImportService` interface | Genuine plugin SPI — deployment-specific impls (e.g. DualisEventsLoaderImpl) |
| `PreferencesAdminController`'s `Set<PreferencesPanel>` | Multi-impl SPI; the controller IS the aggregator |
| `HTMLViewPage` SPI + impls | Plugin extension point — `AbstractHTMLCalendarPage`, `AppointmentPerDayViewPage`, `AppointmentTableViewPage`, `ReservationTableViewPage`, `TableViewPage`. Stays. `CalendarPageController` (post-collapse) dispatches into it. |
| `RaplaMenuGenerator` SPI + `DefaultHTMLMenuEntry` | Index-page menu extension point. Stays. `IndexPageController` (post-collapse) iterates it. |
| `ApiKeyJwtDecoder`, `DelegationHandler`, `ActionWrapper`, `JTreeTable` cell-editor | Real decoration/composition patterns unrelated to REST routing. Untouched. |

### Out of scope — no twin to collapse

Single-declaration controllers without an interface or delegate:
`OAuthConfigController`, `OAuthExchangeController`, `ApiKeyController`,
`LoginPageController`. Leave alone — they're already the end state.

## Drift audit — the cost of promoting interfaces to the live contract

Phase 1 surfaced a meta-finding that reshapes the rest of the PRD: when the
controller is collapsed into `implements XxxService`, **the interface becomes
the live wire contract** (both the server route AND the client proxy read from
it). Any divergence between the old controller's annotations and the interface
becomes a real bug — the controller's annotations are no longer the safety net.

Categories of drift seen so far on `RemoteStorage`:

1. **Missing `@RequestBody` on trailing String params** (`changeName.newLastname`,
   `changeEmail.newEmail`, `confirmEmail.newEmail`). Interface had no annotation
   — Spring's HTTP service proxy on Swing doesn't have a documented contract for
   unannotated args, and the OpenAPI doc treats them as query params.
2. **`required=true` ↔ `required=false`** between interface and old controller.
   Wire truth = whatever tier-3 tests assert (mostly required=true; interface
   often had required=false).
3. **Parameter names lost on `@Override`** — without `-parameters` compile flag
   *or* explicit `@RequestParam("name")` on every interface param, SpringDoc
   reports them as `arg0`/`arg1`/etc. Always add the explicit name.

**Mandatory before each Pattern A collapse:**
- Diff the interface against the old controller's `@RequestParam`/`@RequestBody`/
  `required` settings — interface drift = real bug after collapse.
- Run the OpenApiSpecCaptureTest with `-Dopenapi.update=true` after each
  controller is collapsed; review the diff (paths, param shapes, required flags)
  before committing the new captured spec.

### Wire-contract hardening — no PII in query params

Promoting the interface to the live contract is the right moment to fix any
**PII-in-query-params** mistakes. Query params land in server access logs,
intermediary proxies, and browser history; they're the wrong channel for
personal data. Audit each interface for query params carrying:

- Names (display name, title, surname, lastname)
- Email addresses
- Passwords / tokens / API keys (these should already be in body; verify)
- Personal IDs that aren't opaque (e.g. employee number, citizen ID)

Move those to a `@RequestBody` DTO. Internal opaque IDs (rapla `userId`,
allocatable id, reservation id) and timestamps stay in query params — they're
not PII.

**RemoteStorage examples (applied in Phase 1):**
- `changeName(username, title, surname, lastname)` — all four were query/body
  mix; lastname was the body, the rest were query → now one `ChangeNamePost`
  body with all four fields.
- `changeEmail(username, newEmail)` — username was query → now `ChangeEmailPost`
  body with both fields. `confirmEmail` reuses the same DTO.

Apply the same audit to every Pattern A interface in Phase 2.

## Approach

### Step 1 — verify Spring 6 routes from interface annotations

Spring 6 supports inheriting `@HttpExchange`/`@GetExchange`/`@PostExchange`
from an implemented interface as routing metadata, but the exact rules
(`@RequestBody`, `@RequestParam`, `@PathVariable`, `produces`, exception
mapping) need a smoke test.

Tier-3 MockMvc test against a 1-method `@HttpExchange` interface and an
implementing `@RestController` with zero class-level Spring annotations.
Document what's honoured. If something isn't, the PRD's fallback is: keep
the controller's Spring routing annotations but make `ApiPrefixArchitectureTest`
enforce path-string equality between interface and controller.

### Step 2 — worked example: `RemoteStorageController` + `RemoteStorageImpl`

The largest collapse (22 methods). One commit. Validates the full pattern
end-to-end against tier-3 + tier-4 storage tests (which must pass unchanged)
plus the OpenAPI snapshot diff (must be byte-identical modulo cosmetic
ordering).

### Step 3 — sweep Pattern A in batches

Group by feature family (storage, mail, ical, view, admin) to keep commits
reviewable. After each batch: `mvn -pl rapla-app -am test`, OpenAPI snapshot
diff.

### Step 4 — Pattern B (RestPages)

For each: extract `@HttpExchange` interface to `rapla-core/.../rest/`, inline
the RestPage body into the controller, delete the RestPage + its
`@RequestScope` `@Bean` factory in `ServerServiceConfig:156–182`. Permission
checks land next to the MockMvc leak tests.

Pre-step for `RaplaResourcesRestPage`: `grep -rn getClassificationFilter` —
if it has external callers, extract it to a util class in `rapla-server` first.

### Step 5 — Pattern C (page generators)

Same inline. For `CalendarPageController`, keep the `HTMLViewPage` dispatch
(it's the plugin SPI entry). For `RaplaJNLPController`, rewrite the four
`RaplaJNLPPageGeneratorTest` cases as tier-3 MockMvc tests against the
controller.

### Step 6 — tighten architecture test

`ApiPrefixArchitectureTest`: every `@RestController` outside the
`ALLOWED_NON_API` list either (a) implements an interface annotated
`@HttpExchange("/api/...")` and has no class-level `@RequestMapping` of its
own, or (b) explicitly carries `@RequestMapping("/api/...")` if Step 1 found
Spring doesn't honour interface annotations server-side.

Also forbid `import jakarta.ws.rs.*` outside the Keycloak NTLM submodule
(`tools/keycloak/ntlm-authenticator/`).

### Step 7 — Pattern D cleanup deletions

`RaplaAuthRestPage`, `MessageHTMLWriter`, `RestWebApplicationExceptionMapper`,
`PATCH.java`, `@Context` sweep, empty packages, dead commented lines in
`RaplaRpcAndRestProcessor`.

## Plan

- [x] Phase 0 — Spring 6 interface-routing smoke test (Step 1). **Done 2026-05-21.** Verified path + verb + `@PathVariable` + `@RequestParam` + `@RequestBody` all propagate from interface to impl. Reference test stays in the suite: `rapla-app/.../InterfaceRoutingSmokeTest.java`.
- [x] Phase 1 — `RemoteStorageController` worked example (Step 2). **Done 2026-05-21.** Controller now `implements RemoteStorage`, 705-line `RemoteStorageImpl` deleted, `@Bean` factory in `ServerServiceConfig` deleted. Two interface annotations corrected (drift between `required=false` on interface vs. required-at-server in old controller — tests asserted required-at-server, so interface was wrong; aligned interface to wire reality). `UpdateDataManagerImpl.isTransferedToClient` + `removeServerOnlyPreferences` raised from package-private to public (no longer same-package as the controller). `ApiPrefixArchitectureTest` extended to recognize interface-inherited `@HttpExchange` paths in addition to class-level `@RequestMapping`. 63/64 broader tests green; the 1 failure (`UrlPreservationTest.appShellRoutes`) is a pre-existing unrelated branch issue per memory.
- [x] Phase 2 — Pattern A sweep, batched (Step 3). **Done 2026-05-21 (partial).** Batches 1+2 landed: Settings, ExchangeConnector, ICalConfig, EventTimeCalculator, TableView, CalendarView, ReservationEdit, Plugins (Batch 1 — 8 controllers); RemoteLogger, RemoteLocale, JNDIConfig, UrlEncryption, ICalTimezones (Batch 2 — 5 controllers, with 5 *Impl classes deleted). Batch 3 (Archiver, MailToUser, ExternalEventImport, MailConfig, PreferencesAdmin) deferred — the *Impls have real in-process callers, so making the controller `implements` AND keeping the impl as a separate @Bean creates a "multiple beans of type X" conflict. Proper fix: migrate in-process callers to depend on the interface (inject the controller). ExportController + ICalImportController also deferred (csvDownload ResponseEntity-wrap is incompatible with simple interface routing; ICalImport impl is 488 lines and not worth inlining vs. keeping the delegate). RemoteStorage interface drift fixed in Phase 1 (3× `@RequestBody` missing on trailing String params, 2× `required=true` vs interface's `required=false`; introduced `ChangeNamePost` + `ChangeEmailPost` DTOs to move PII out of query params per the new "no PII in query" rule).
- [x] Phase 3 — Pattern B: resources, events, dynamictypes (Step 4). **Done 2026-05-21.** Three new `@HttpExchange` interfaces in `rapla-core/src/main/java/org/rapla/rest/` (RaplaResourcesService, RaplaEventsService, RaplaDynamicTypesService); the three RestPages and their `@Bean` factories in ServerServiceConfig deleted (~500 lines net). Static helper `getClassificationFilter` extracted to `ClassificationFilterUtil` in rapla-server before inlining (shared by Resources + Events controllers).
- [x] Phase 4 — Pattern C: index, status, JNLP, calendar, ical (Step 5). **Done 2026-05-21 (full inline, after user pushback on the deferral).** All 5 page generators inlined into their controllers + deleted (~1170 lines total): RaplaIndexPageGenerator → IndexPageController, RaplaStatusPageGenerator → StatusPageController, RaplaJNLPPageGenerator → RaplaJNLPController (+ RaplaJNLPPageGeneratorTest moved + renamed RaplaJNLPControllerTest, all 4 cases green), CalendarPageGenerator → CalendarPageController (keeps the HTMLViewPage plugin SPI dispatch — that's a real extension point), Export2iCalServlet → Export2iCalController. Bonus: RaplaICalImport (488 lines, previously deferred too) also inlined into ICalImportController + impl class deleted. Bean factories in ServerServiceConfig removed for all 5 generators + the iCal-import bean. **No "delegate" classes left for any HTTP endpoint in the rapla server.**
- [x] Phase 5+6 — architecture test + cleanup deletions (Steps 6+7). **Done 2026-05-21.** Deleted: `RaplaAuthRestPage` (LOGIN_COOKIE constant inlined into RemoteSessionImpl); RESTEasy `@Provider` pair (`MessageHTMLWriter`, `RestWebApplicationExceptionMapper`); `ExceptionResponseBuilder` (orphan dead code); `PATCH.java`. `@Context` annotation stripped from the remaining 5 jakarta.ws.rs holdouts (RaplaICalImport, ArchiverServiceImpl, RaplaConfigServiceImpl, RaplaMailToUserOnLocalhost, RaplaICalExport). Empty packages removed: `org.rapla.server.provider.resteasy`, `org.rapla.server.provider`. **`jakarta.ws.rs-api` dependency removed**: deleted from `rapla-core/pom.xml`, the version property + dependencyManagement entry in `rapla-bom/pom.xml`, and `jakarta.ws.rs-api` from the JNLP `includeArtifactIds` in `rapla-app/pom.xml`. **Zero `jakarta.ws.rs.*` imports remain in rapla main source** (verified). `ApiPrefixArchitectureTest` extended in Phase 1 to recognize interface-inherited `@HttpExchange` paths.
- [ ] Phase 7 — move PRD to `docs/prd/done/`, update AGENTS.md §15. **Deferred until Batch 3 + ExportController + ICalImport collapse landed (or scope formally narrowed).**

## Tests

- **All existing tier-3 + tier-4 server tests must pass unchanged.** Pure
  refactor; observable behaviour identical. No new red.
- **OpenAPI snapshot diff** captured before/after each phase. Diff must be
  cosmetic only — no path/verb/param/schema drift.
- **One reflection-based architecture test added in Phase 1**: assert every
  `@RestController` (outside the allow-list) has at least one implemented
  interface carrying `@HttpExchange`. Catches a future controller that
  re-introduces standalone `@RequestMapping`.
- **Pattern B**: assert `org.rapla.endpoints.server` is empty after Phase 3.
- **Pattern C → JNLP**: the 4 existing `RaplaJNLPPageGeneratorTest` cases
  become tier-3 MockMvc tests on `RaplaJNLPController`, exercising the same
  manifest-generation paths.
- **Per AGENTS.md §1**, every collapse starts with a small red test for the
  endpoint (usually an existing tier-3 already covers it), greens when the
  collapse is correct, stays in the suite afterwards.

## Open questions

1. **Spring 6 interface-routing semantics** — answered by Phase 0 smoke test.
   Falls back to "keep controller routing annotations, enforce equality via
   architecture test" if interface-routing isn't fully supported.
2. **`MailConfigService` — single impl or plugin SPI?** Quick `find … -name
   '*MailConfig*'` + check for multiple `@DefaultImplementation`/`@Service`
   bindings. If single, fold into the inlinable list. If multi, treat like
   `ExternalEventImportService` and keep the interface + impls.
3. **`RaplaResourcesRestPage.getClassificationFilter`** — `public static`
   helper. Find callers; if reused, extract to a util class in `rapla-server`
   before inlining the rest of the RestPage.
4. **`@ConditionalOnBean(RemoteSession.class)`** — currently on most
   controller classes. Stays on the controller; not part of the routing
   contract.
5. **`RaplaAuthRestPage` orphan check** — `grep` to confirm zero callers
   before deleting. Same for the RESTEasy `@Provider` pair and `PATCH.java`.

## Appendix A — survival map (post-PRD)

After PRD 049 lands, the REST surface looks like:

```
rapla-core/
  src/main/java/org/rapla/
    storage/dbrm/RemoteStorage.java          ← @HttpExchange("/api/storage")
    rest/PluginsService.java                  ← @HttpExchange("/api/plugins")
    rest/SettingsService.java                 ← @HttpExchange("/api/settings")
    rest/RaplaResourcesService.java           ← NEW, extracted from RestPage
    rest/RaplaEventsService.java              ← NEW
    rest/RaplaDynamicTypesService.java        ← NEW
    plugin/**/                                ← @HttpExchange interfaces, unchanged

rapla-server/
  src/main/java/org/rapla/server/spring/web/
    RemoteStorageController.java implements RemoteStorage       ← holds the logic
    PluginsController.java        implements PluginsService     ← holds the logic
    …                                                            ← 21 controllers
    CalendarPageController.java                                  ← Pattern C, dispatches into HTMLViewPage SPI
    IndexPageController.java                                     ← Pattern C, iterates RaplaMenuGenerator SPI
    Export2iCalController.java                                   ← Pattern C, holds iCal4j serialisation
    OAuthConfigController.java, OAuthExchangeController.java,
    ApiKeyController.java, LoginPageController.java              ← standalone, no twin

  src/main/java/org/rapla/server/extensionpoints/
    HTMLViewPage.java                                            ← KEEP — plugin SPI
  src/main/java/org/rapla/server/servletpages/
    RaplaMenuGenerator.java, DefaultHTMLMenuEntry.java           ← KEEP — index menu SPI
    RaplaPageGenerator.java                                      ← VERIFY — keep only if HTMLViewPage SPI still needs it as a supertype, otherwise delete

  Surviving in-process delegates (3):
    plugin/archiver/server/ArchiverServiceImpl.java              ← used by ArchiverServiceTask + Panel
    plugin/mail/server/MailToUserImpl.java                       ← used by NotificationService + SynchronisationManager
    plugin/externaleventimport/.../*EventImport*Impl.java        ← plugin SPI multi-impl

  Deleted:
    endpoints/server/Rapla{Resources,Events,DynamicTypes,Auth}RestPage.java
    server/internal/{RemoteStorageImpl,RemoteLoggerImpl,RemoteLocaleServiceImpl}.java
    server/servletpages/Rapla{Index,Status,JNLP}PageGenerator.java
    plugin/autoexport/server/CalendarPageGenerator.java
    plugin/export2ical/server/Export2iCalServlet.java
    plugin/jndi/server/RaplaJNDITestOnLocalhost.java
    plugin/urlencryption/server/UrlEncryptionService.java       ← if no in-process callers
    plugin/ical/server/RaplaICalImport.java                     ← if no in-process callers
    plugin/export2ical/server/ICalTimezones* impl                ← if no in-process callers
    server/provider/resteasy/{MessageHTMLWriter,RestWebApplicationExceptionMapper}.java
    rest/PATCH.java                                             ← if no callers
```

Approximate net delete: ~26 classes + their `@Bean` factories in
`ServerServiceConfig` + dead JAX-RS imports across the survivors.
