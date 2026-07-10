# PRD 049 — One class per REST endpoint: collapse delegates into controllers

> **Update 2026-06-24 — `RemoteSessionImpl` removed.** Phase 5's note that the
> `RaplaAuthRestPage` `LOGIN_COOKIE` constant was inlined into `RemoteSessionImpl` is
> now historical: `RemoteSessionImpl` (and the whole legacy HMAC token path) was
> deleted — auth is JWT-only via `SpringSecurityRemoteSession` + `JwtUserResolver`.

**Status:** in-progress (Phases 0-1, 3-6 landed 2026-05-21; Phase 2 partial — see notes)
**Date:** 2026-05-21
**Related:** [PRD 009](009-server-bulk-storage-rest-api.md) (server bulk storage REST), PRD 031 (API namespace), [PRD 041](041-openapi-runtime-removal.md) (OpenAPI runtime removal)

## Goal

Every REST endpoint group in rapla should be **one class**: a Spring `@RestController` that implements the corresponding `@HttpExchange` interface from `rapla-core`. The interface owns path + verb + parameter binding; the controller holds the implementation. No `*Impl`, no `*RestPage`, no `*PageGenerator`, no `@Bean` factory wiring the delegate as a separate bean — unless the delegate has a real in-process caller other than the controller.

Pre-existing `@HttpExchange` interfaces in `rapla-core` are already the abstraction across the wire — Swing builds an HTTP proxy via `HttpServiceProxyFactory`, Angular generates a client from the OpenAPI doc. They are NOT in-process call sites; no reason to keep a second class behind the controller.

## Why

Current arrangement has up to three classes per endpoint group:
1. `@HttpExchange` interface in `rapla-core` — client contract.
2. `@RestController` in `rapla-server/.../web/` — declares path + verb + params *again*, forwards to delegate.
3. `*Impl` / `*RestPage` / `*PageGenerator` — holds the logic.

Path + verb + params declared twice (interface + controller). Logic one level removed from routing. Pure historical baggage from the pre-Spring era (JSON-RPC interface impls under Guice, RESTEasy `@Path` classes, servlet page-generator framework). Concretely:

- **Drift**: path renamed on interface doesn't break the server, and vice versa. Swing and Angular can quietly speak different dialects until something breaks in production.
- **Security distance**: tier-3 MockMvc leak tests (§12) sit at the controller. Permission checks (`session.checkAndGetUser`, `permissionController.canRead`, `securityManager.checkWritePermissions`) sit one delegate hop away. Same-class tightens the audit surface.
- **Dead annotations**: 3 `*RestPage` classes carry JAX-RS `@Path`/`@GET`/`@POST`/`@PathParam`/`@QueryParam` that Spring doesn't honour. 5 `*PageGenerator` classes carry same. `@HttpExchange` interfaces' Spring-equivalent annotations are what actually routes.

PRDs 031 (URL namespace), 041 (springdoc runtime removal), and 009 (bulk REST) stabilised URL space and OpenAPI surface — this is the moment to consolidate.

## Scope

### In scope — collapse (delete delegate, controller is the implementation)

For each below, the delegate has **zero in-process callers** other than its controller. The `@HttpExchange` interface stays; everything else fuses into the controller.

**Pattern A — `*Impl` delegates with single caller (4 + 1-pending):**
| Controller | Delegate to delete |
|---|---|
| `RemoteStorageController` | `RemoteStorageImpl` (22 methods) |
| `RemoteLocaleController` | `RemoteLocaleServiceImpl` |
| `RemoteLoggerController` | `RemoteLoggerImpl` |
| `JNDIConfigController` | `RaplaJNDITestOnLocalhost` |
| `MailConfigController` | `RaplaConfigServiceImpl` ⚠️ verify single impl of `MailConfigService` interface first |

**Pattern A — interface-only delegates (3, no `*Impl` to delete):**
| Controller | Interface |
|---|---|
| `ICalImportController` | `ICalImport` (sole impl: `RaplaICalImport` — also collapse) |
| `ICalTimezonesController` | `ICalTimezones` |
| `UrlEncryptionController` | `UrlEncryption` |

**Pattern A — controllers already without a delegate (8):**
`PluginsController`, `SettingsController`, `ICalConfigController`, `ExportController`, `TableViewController`, `CalendarViewController`, `EventTimeCalculatorConfigController`, `ExchangeConnectorConfigController`, `ReservationEditController`. Need `implements` step + removal of redundant Spring routing annotations, but no delete.

**Pattern B — `*RestPage` delegates (3):**
| Controller | Delete | Notes |
|---|---|---|
| `RaplaResourcesController` | `RaplaResourcesRestPage` | Extract `getClassificationFilter(...)` first — `public static` helper, find callers |
| `RaplaEventsController` | `RaplaEventsRestPage` | |
| `RaplaDynamicTypesController` | `RaplaDynamicTypesRestPage` | |

For Pattern B the `@HttpExchange` interface doesn't exist; extract one into `rapla-core/.../rest/` as part of the collapse.

**Pattern C — `*PageGenerator` delegates (5):**
| Controller | Delete |
|---|---|
| `IndexPageController` | `RaplaIndexPageGenerator` |
| `StatusPageController` | `RaplaStatusPageGenerator` |
| `RaplaJNLPController` | `RaplaJNLPPageGenerator` (with `RaplaJNLPPageGeneratorTest` rewritten as tier-3 MockMvc) |
| `CalendarPageController` | `CalendarPageGenerator` (KEEP the `HTMLViewPage` plugin SPI dispatch — real extension point) |
| `Export2iCalController` | `Export2iCalServlet` |

Pattern C returns HTML / iCal / JNLP. These controllers don't have `@HttpExchange` interfaces (`CalendarPageController` and `Export2iCalController` serve external iCal subscribers per AGENTS.md §15) and don't need one. Just inline.

### Pattern D — cleanup deletions

- `RaplaAuthRestPage` (`@Path("login")`) — `POST /api/auth/login` removed in [PRD 041](041-openapi-runtime-removal.md)→043 cleanup. Verify zero callers, delete.
- `MessageHTMLWriter`, `RestWebApplicationExceptionMapper` (RESTEasy `@Provider` SPI) — Spring Boot 4 has no JAX-RS runtime. Verify zero autoconfig, delete. `RaplaExceptionHandler` is the replacement.
- `org.rapla.rest.PATCH` — custom JAX-RS `@HttpMethod`. If no live refs, delete (use Spring's `@PatchMapping`).
- `@Context HttpServletRequest` → plain `HttpServletRequest` sweep across ~11 surviving classes still injecting via JAX-RS.
- Empty package deletions: `org.rapla.endpoints.server`, `org.rapla.server.provider.resteasy`.
- Three commented-out `servlet.*` lines in `RaplaRpcAndRestProcessor:82,88,92`.
- **`jakarta.ws.rs-api` dependency removal** — once zero `import jakarta.ws.rs.*` remain (audit grep), delete:
  - `<dependency>` in `rapla-core/pom.xml`
  - `jakarta.ws.rs.version` property + `dependencyManagement` entry in `rapla-bom/pom.xml`
  - `jakarta.ws.rs-api` from `includeArtifactIds` of JNLP `dependency:copy-dependencies` in `rapla-app/pom.xml`
  - Keycloak NTLM submodule (`tools/keycloak/ntlm-authenticator/`) uses Keycloak's own JAX-RS surface — independent of reactor — leave alone.

### Out of scope — keep the delegate (real in-process callers / SPI)

| Class | Reason |
|---|---|
| `ArchiverServiceImpl` | `ArchiverServiceTask:66,72` (background task), `ArchiverPreferencesPanel:59` (Swing config UI) call in-process |
| `MailToUserImpl` | `NotificationService:58`, `SynchronisationManager:98` call in-process for server-internal mail |
| `ExternalEventImportService` interface | Genuine plugin SPI — deployment-specific impls (DualisEventsLoaderImpl) |
| `PreferencesAdminController`'s `Set<PreferencesPanel>` | Multi-impl SPI; controller IS the aggregator |
| `HTMLViewPage` SPI + impls | Plugin extension point — `AbstractHTMLCalendarPage`, `AppointmentPerDayViewPage`, `AppointmentTableViewPage`, `ReservationTableViewPage`, `TableViewPage`. `CalendarPageController` (post-collapse) dispatches into it. |
| `RaplaMenuGenerator` SPI + `DefaultHTMLMenuEntry` | Index-page menu extension. `IndexPageController` (post-collapse) iterates it. |
| `ApiKeyJwtDecoder`, `DelegationHandler`, `ActionWrapper`, `JTreeTable` cell-editor | Real decoration/composition patterns unrelated to REST routing. |

### Out of scope — no twin to collapse

Single-declaration controllers without interface/delegate: `OAuthConfigController`, `OAuthExchangeController`, `ApiKeyController`, `LoginPageController`. Already the end state.

## Drift audit — the cost of promoting interfaces to the live contract

Phase 1 surfaced a meta-finding that reshapes the rest of the PRD: when the controller is collapsed into `implements XxxService`, **the interface becomes the live wire contract** (both server route AND client proxy read from it). Any divergence between the old controller's annotations and the interface becomes a real bug — the controller's annotations are no longer the safety net.

Categories of drift seen so far on `RemoteStorage`:

1. **Missing `@RequestBody` on trailing String params** (`changeName.newLastname`, `changeEmail.newEmail`, `confirmEmail.newEmail`). Interface had no annotation — Spring's HTTP service proxy on Swing doesn't have a documented contract for unannotated args, and the OpenAPI doc treats them as query params.
2. **`required=true` ↔ `required=false`** between interface and old controller. Wire truth = whatever tier-3 tests assert (mostly required=true; interface often had required=false).
3. **Parameter names lost on `@Override`** — without `-parameters` compile flag *or* explicit `@RequestParam("name")` on every interface param, SpringDoc reports them as `arg0`/`arg1`. Always add the explicit name.

**Mandatory before each Pattern A collapse:**
- Diff interface against old controller's `@RequestParam`/`@RequestBody`/`required` settings — interface drift = real bug after collapse.
- Run OpenApiSpecCaptureTest with `-Dopenapi.update=true` after each controller is collapsed; review diff before committing the new captured spec.

### Wire-contract hardening — no PII in query params

Promoting the interface to the live contract is the right moment to fix **PII-in-query-params** mistakes. Query params land in server access logs, intermediary proxies, browser history; wrong channel for personal data. Audit each interface for:

- Names (display name, title, surname, lastname)
- Email addresses
- Passwords / tokens / API keys (these should already be in body; verify)
- Personal IDs that aren't opaque (employee number, citizen ID)

Move those to `@RequestBody` DTO. Internal opaque IDs (rapla `userId`, allocatable id, reservation id) and timestamps stay in query params.

**RemoteStorage examples (applied in Phase 1):**
- `changeName(username, title, surname, lastname)` — all four were query/body mix → now one `ChangeNamePost` body with all four fields.
- `changeEmail(username, newEmail)` — username was query → now `ChangeEmailPost` body. `confirmEmail` reuses the same DTO.

Apply the same audit to every Pattern A interface in Phase 2.

## Approach

### Step 1 — verify Spring 6 routes from interface annotations

Spring 6 supports inheriting `@HttpExchange`/`@GetExchange`/`@PostExchange` from an implemented interface as routing metadata, but exact rules (`@RequestBody`, `@RequestParam`, `@PathVariable`, `produces`, exception mapping) need a smoke test.

Tier-3 MockMvc test against a 1-method `@HttpExchange` interface and an implementing `@RestController` with zero class-level Spring annotations. Document what's honoured. Fallback: keep controller's Spring routing annotations + `ApiPrefixArchitectureTest` enforces path-string equality between interface and controller.

### Step 2 — worked example: `RemoteStorageController` + `RemoteStorageImpl`

Largest collapse (22 methods). One commit. Validates the full pattern end-to-end against tier-3 + tier-4 storage tests (must pass unchanged) plus OpenAPI snapshot diff (byte-identical modulo cosmetic ordering).

### Step 3 — sweep Pattern A in batches

Group by feature family (storage, mail, ical, view, admin) for reviewable commits. After each: `mvn -pl rapla-app -am test`, OpenAPI snapshot diff.

### Step 4 — Pattern B (RestPages)

For each: extract `@HttpExchange` interface to `rapla-core/.../rest/`, inline RestPage body into controller, delete RestPage + its `@RequestScope` `@Bean` factory in `ServerServiceConfig:156–182`. Permission checks land next to MockMvc leak tests.

Pre-step for `RaplaResourcesRestPage`: `grep -rn getClassificationFilter` — if external callers, extract to util class in `rapla-server` first.

### Step 5 — Pattern C (page generators)

Same inline. For `CalendarPageController`, keep `HTMLViewPage` dispatch (plugin SPI entry). For `RaplaJNLPController`, rewrite four `RaplaJNLPPageGeneratorTest` cases as tier-3 MockMvc.

### Step 6 — tighten architecture test

`ApiPrefixArchitectureTest`: every `@RestController` outside `ALLOWED_NON_API` either (a) implements an interface annotated `@HttpExchange("/api/...")` and has no class-level `@RequestMapping`, or (b) explicitly carries `@RequestMapping("/api/...")` if Step 1 found Spring doesn't honour interface annotations server-side.

Also forbid `import jakarta.ws.rs.*` outside the Keycloak NTLM submodule.

### Step 7 — Pattern D cleanup deletions

`RaplaAuthRestPage`, `MessageHTMLWriter`, `RestWebApplicationExceptionMapper`, `PATCH.java`, `@Context` sweep, empty packages, dead commented lines in `RaplaRpcAndRestProcessor`.

## Plan

- [x] Phase 0 — Spring 6 interface-routing smoke test. **Done 2026-05-21.** Verified path + verb + `@PathVariable` + `@RequestParam` + `@RequestBody` all propagate from interface to impl. Reference test stays in suite: `rapla-app/.../InterfaceRoutingSmokeTest.java`.
- [x] Phase 1 — `RemoteStorageController` worked example. **Done 2026-05-21.** Controller now `implements RemoteStorage`, 705-line `RemoteStorageImpl` deleted, `@Bean` factory in `ServerServiceConfig` deleted. Two interface annotations corrected (drift between `required=false` on interface vs. required-at-server in old controller — tests asserted required-at-server, aligned interface). `UpdateDataManagerImpl.isTransferedToClient` + `removeServerOnlyPreferences` raised from package-private to public. `ApiPrefixArchitectureTest` extended to recognize interface-inherited `@HttpExchange` paths. 63/64 broader tests green; the 1 failure (`UrlPreservationTest.appShellRoutes`) is pre-existing unrelated branch issue per memory.
- [x] Phase 2 — Pattern A sweep, batched. **Done 2026-05-21 (partial).** Batches 1+2 landed: Settings, ExchangeConnector, ICalConfig, EventTimeCalculator, TableView, CalendarView, ReservationEdit, Plugins (Batch 1 — 8 controllers); RemoteLogger, RemoteLocale, JNDIConfig, UrlEncryption, ICalTimezones (Batch 2 — 5 controllers, with 5 *Impl deleted). Batch 3 (Archiver, MailToUser, ExternalEventImport, MailConfig, PreferencesAdmin) deferred — *Impls have real in-process callers; making controller `implements` AND keeping impl as separate @Bean creates "multiple beans of type X" conflict. Proper fix: migrate in-process callers to depend on the interface (inject the controller). ExportController + ICalImportController also deferred (csvDownload ResponseEntity-wrap incompatible with simple interface routing; ICalImport impl is 488 lines, not worth inlining vs keeping delegate). RemoteStorage interface drift fixed in Phase 1 (3× `@RequestBody` missing on trailing String params, 2× `required=true` vs interface's `required=false`; introduced `ChangeNamePost` + `ChangeEmailPost` DTOs to move PII out of query params).
- [x] Phase 3 — Pattern B: resources, events, dynamictypes. **Done 2026-05-21.** Three new `@HttpExchange` interfaces in `rapla-core/src/main/java/org/rapla/rest/` (RaplaResourcesService, RaplaEventsService, RaplaDynamicTypesService); the three RestPages and `@Bean` factories in ServerServiceConfig deleted (~500 lines net). Static helper `getClassificationFilter` extracted to `ClassificationFilterUtil` in rapla-server before inlining.
- [x] Phase 4 — Pattern C: index, status, JNLP, calendar, ical. **Done 2026-05-21 (full inline, after user pushback on the deferral).** All 5 page generators inlined into their controllers + deleted (~1170 lines): RaplaIndexPageGenerator → IndexPageController, RaplaStatusPageGenerator → StatusPageController, RaplaJNLPPageGenerator → RaplaJNLPController (+ RaplaJNLPPageGeneratorTest moved + renamed RaplaJNLPControllerTest, all 4 cases green), CalendarPageGenerator → CalendarPageController (keeps HTMLViewPage plugin SPI dispatch), Export2iCalServlet → Export2iCalController. Bonus: RaplaICalImport (488 lines, previously deferred) also inlined into ICalImportController + impl deleted. Bean factories in ServerServiceConfig removed for all 5 generators + iCal-import bean. **No "delegate" classes left for any HTTP endpoint in the rapla server.**
- [x] Phase 5+6 — architecture test + cleanup deletions. **Done 2026-05-21.** Deleted: `RaplaAuthRestPage` (LOGIN_COOKIE constant inlined into RemoteSessionImpl); RESTEasy `@Provider` pair (`MessageHTMLWriter`, `RestWebApplicationExceptionMapper`); `ExceptionResponseBuilder` (orphan dead code); `PATCH.java`. `@Context` stripped from remaining 5 jakarta.ws.rs holdouts (RaplaICalImport, ArchiverServiceImpl, RaplaConfigServiceImpl, RaplaMailToUserOnLocalhost, RaplaICalExport). Empty packages removed: `org.rapla.server.provider.resteasy`, `org.rapla.server.provider`. **`jakarta.ws.rs-api` dependency removed**: deleted from `rapla-core/pom.xml`, version property + dependencyManagement entry in `rapla-bom/pom.xml`, and `jakarta.ws.rs-api` from JNLP `includeArtifactIds` in `rapla-app/pom.xml`. **Zero `jakarta.ws.rs.*` imports remain in rapla main source** (verified). `ApiPrefixArchitectureTest` extended in Phase 1.
- [ ] Phase 7 — move PRD to `docs/prd/done/`, update AGENTS.md §15. **Deferred until Batch 3 + ExportController + ICalImport collapse landed (or scope formally narrowed).**

## Tests

- **All existing tier-3 + tier-4 server tests must pass unchanged.** Pure refactor; observable behaviour identical.
- **OpenAPI snapshot diff** captured before/after each phase. Cosmetic only — no path/verb/param/schema drift.
- **One reflection-based architecture test added in Phase 1**: assert every `@RestController` (outside allow-list) has at least one implemented interface carrying `@HttpExchange`. Catches future controllers re-introducing standalone `@RequestMapping`.
- **Pattern B**: assert `org.rapla.endpoints.server` is empty after Phase 3.
- **Pattern C → JNLP**: 4 existing `RaplaJNLPPageGeneratorTest` cases become tier-3 MockMvc on `RaplaJNLPController`.
- **Per AGENTS.md §1**, every collapse starts with a small red test for the endpoint (usually existing tier-3 covers it), greens when collapse is correct, stays in suite afterwards.

## Open questions

1. **Spring 6 interface-routing semantics** — answered by Phase 0. Falls back to "keep controller routing annotations, enforce equality via architecture test" if interface-routing isn't fully supported.
2. **`MailConfigService` — single impl or plugin SPI?** Quick `find … -name '*MailConfig*'` + check for multiple `@DefaultImplementation`/`@Service`. Single → fold in. Multi → treat like `ExternalEventImportService`.
3. **`RaplaResourcesRestPage.getClassificationFilter`** — `public static` helper. Find callers; if reused, extract to util in `rapla-server` first.
4. **`@ConditionalOnBean(RemoteSession.class)`** — currently on most controllers. Stays on the controller; not part of routing contract.
5. **`RaplaAuthRestPage` orphan check** — `grep` to confirm zero callers before deleting. Same for RESTEasy `@Provider` pair and `PATCH.java`.

## Appendix A — survival map (post-PRD)

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

Approximate net delete: ~26 classes + their `@Bean` factories in `ServerServiceConfig` + dead JAX-RS imports across the survivors.
