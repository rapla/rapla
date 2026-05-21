# PRD 049 — Controller / `@HttpExchange` interface deduplication

**Status:** draft
**Date:** 2026-05-21
**Related:** PRD 009 (server bulk storage REST), PRD 031 (API namespace), PRD 041 (OpenAPI runtime removal)

## Goal

Eliminate the dual declaration of REST endpoint metadata (path, HTTP verb,
parameter binding) currently spread between the client-side `@HttpExchange`
interfaces in `rapla-core/.../rest/`, `.../storage/`, `.../endpoints/`,
`.../plugin/**/` and the server-side Spring `@RestController` classes in
`rapla-server/.../server/spring/web/`. End state: **one annotation site per
endpoint**, owned by an interface in `rapla-core`, implemented by the Spring
controller in `rapla-server`. Spring 6 routes from the interface's
`@HttpExchange` / `@GetExchange` / `@PostExchange` annotations; the controller
contributes the method body only.

For the three JAX-RS-style `*RestPage` delegates (`RaplaResourcesRestPage`,
`RaplaEventsRestPage`, `RaplaDynamicTypesRestPage`), additionally collapse the
two-class arrangement into one: extract a fresh `@HttpExchange` interface in
`rapla-core`, move the business logic from the RestPage into the controller
body, delete the RestPage and the `@RequestScope` `@Bean` factory that wires
it.

## Why

- **24 endpoint groups currently declare path/verb/params twice** — once in
  the client-side `@HttpExchange` interface (so Spring's `HttpServiceProxyFactory`
  can build a Swing-side proxy and Angular's OpenAPI codegen can emit a
  client) and once in the Spring `@RestController` (so the server can route
  requests). Drift between the two is silent: a rename on the interface
  doesn't break the server, and vice versa. The Swing client and Angular SPA
  may speak slightly different dialects against the same backend before anyone
  notices.
- **The RestPage pattern is doubly redundant.** `RaplaResourcesController` is
  five lines of body delegating to `RaplaResourcesRestPage`, plus null-defaulting
  on Spring's nullable `@RequestParam`s. The JAX-RS `@Path`, `@GET`, `@POST`,
  `@PUT`, `@DELETE` annotations on the RestPage are dead metadata — Spring
  doesn't honour them; only the controller's `@GetMapping` etc. are wired into
  routing. Two declarations, neither single-sourced.
- **PRD 031 stabilised the `/api/` namespace and PRD 041 removed runtime
  springdoc**, so the URL shape is now frozen and the contract surfaces (Swing
  proxy, Angular OpenAPI, server router) are settled. This is the right
  moment to consolidate.
- **AGENTS.md §15 invariant** (every controller URL starts with `/api/`, every
  `@HttpExchange` matches) is currently enforced by `ApiPrefixArchitectureTest`
  reading both sides. After this PRD it's enforced by the type system: a
  controller that implements the interface inherits the path, end of story.

## Scope

### In scope

The 24 endpoint groups currently double-declared (see Appendix A for the full
table from the 2026-05-21 audit):

- **21 `@HttpExchange` interface + Spring controller pairs.** Most have no
  shared backing impl (controller calls `RaplaFacade` directly); a few delegate
  to an `*Impl` (`RemoteStorageImpl`, `RemoteLoggerImpl`, `MailToUserImpl`,
  `ReservationEditImpl`).
- **3 JAX-RS `*RestPage` + Spring controller pairs.** `RaplaResourcesController` /
  `RaplaResourcesRestPage`, `RaplaEventsController` / `RaplaEventsRestPage`,
  `RaplaDynamicTypesController` / `RaplaDynamicTypesRestPage`.

### Out of scope

- Standalone controllers without an interface twin: `OAuthConfigController`,
  `OAuthExchangeController`, `ApiKeyController`, `IndexPageController`,
  `LoginPageController`, `StatusPageController`, `RaplaJNLPController`,
  `Export2iCalController`, `CalendarPageController`. These already have a
  single declaration site — leave them alone.
- Changing any wire shape (URLs, verbs, params, response bodies). This is a
  pure source-layout refactor; HTTP traffic must be byte-identical
  before/after. PRD 012 leak-tests and the OpenAPI snapshot must continue to
  pass unchanged.
- Adding/removing endpoints. PRD 009 is the proper home for that.
- Touching the legacy `restinject` references in docs — those are already
  removed from the runtime per PRD 011 / `docs/architecture/migration-from-master.md`.

## Approach

### Pattern A — interface + controller pairs (21 groups)

Have the controller implement the `@HttpExchange` interface and drop the
controller's own Spring routing annotations. Spring 6 picks up `@HttpExchange`
on an implemented interface as routing metadata equivalent to
`@RequestMapping`.

Before (`RemoteStorageController`):
```java
@RestController
@RequestMapping(value = "/api/storage", produces = APPLICATION_JSON_VALUE)
public class RemoteStorageController {
    private final RemoteStorageImpl delegate;

    @GetMapping("/change/canchangepassword")
    public boolean canChangePassword() throws RaplaException {
        return delegate.canChangePassword();
    }
    // ... 21 more methods, each re-declaring a path
}
```

After:
```java
@RestController
public class RemoteStorageController implements RemoteStorage {
    private final RemoteStorageImpl delegate;

    @Override
    public boolean canChangePassword() throws RaplaException {
        return delegate.canChangePassword();
    }
    // ... no path annotations, only @Override + body
}
```

The `produces = APPLICATION_JSON_VALUE` setting moves to the interface
level if it isn't already implicit from the return types.

### Pattern B — RestPage + controller pairs (3 groups)

Two-step collapse:

1. **Extract a new `@HttpExchange` interface** in
   `rapla-core/src/main/java/org/rapla/rest/` mirroring the current
   controller surface (path, verbs, params, return types). The Swing client
   doesn't need this interface yet (it doesn't proxy `/api/resources` etc.
   today — those are PRD 009's bulk surfaces), but creating it keeps the
   pattern uniform with Pattern A and gives Angular OpenAPI codegen a clean
   contract.
2. **Inline the RestPage logic into the controller body**, then delete the
   RestPage and its `@Bean` factory in
   `ServerServiceConfig` lines ~156–182. The `@Autowired` fields
   (`RaplaFacade`, `StorageOperator`, `RemoteSession`, `SecurityManager`,
   `HttpServletRequest`) become constructor parameters on the controller.

Result: one class per endpoint group, implementing one interface.

### Migration order

1. **Worked example: `RemoteStorage` / `RemoteStorageController`** (largest
   interface, cleanest shared impl, the canonical pair). One commit.
   Verifies the Spring-routing-from-interface mechanism works end-to-end
   against the existing tier-3 and tier-4 tests for the storage surface.
2. **Sweep the remaining 20 Pattern A groups.** Each is mechanically the same
   transformation; can be done in batches of 3–5 per commit, grouped by
   feature (mail, ical, calendar, etc.).
3. **Pattern B: `RaplaResourcesController` first** (smallest RestPage —
   5 methods). Inline logic, delete RestPage, delete `@Bean` factory. Then
   `RaplaEventsController`, then `RaplaDynamicTypesController`.
4. **Tighten `ApiPrefixArchitectureTest`**: replace its "controller path
   starts with /api/" check with "every controller either implements an
   `@HttpExchange` interface with an `/api/` prefix, or appears in the
   `ALLOWED_NON_API` list." Catches future regressions where someone adds
   `@RequestMapping` back to a controller.

## Plan

- [ ] Phase 0 — verify Spring 6 routing from interface annotations
  - [ ] Write a smoke test: create a 1-method `@HttpExchange` interface, an
    implementing `@RestController` with no class-level annotations, hit it via
    MockMvc. Confirm 200 + correct body. Document the exact annotation rules
    Spring honours from the interface (path, verb, `@RequestBody`,
    `@RequestParam`, `@PathVariable`, `produces`).
- [ ] Phase 1 — worked example
  - [ ] Convert `RemoteStorageController` to `implements RemoteStorage`.
    Drop all class-level and method-level Spring routing annotations.
  - [ ] Run the full `mvn -pl rapla-app -am test` (tier 3 + tier 4). All
    existing storage tests pass unchanged.
  - [ ] Capture the resulting OpenAPI snapshot; diff against the
    pre-conversion snapshot. **Must be byte-identical except for cosmetic
    field ordering.**
- [ ] Phase 2 — sweep Pattern A (21 groups)
  - [ ] Batch 1: `RemoteLocale`, `RemoteLogger`, `Plugins`, `Settings`.
  - [ ] Batch 2: mail family (`MailConfig`, `MailToUser`).
  - [ ] Batch 3: ical family (`ICalConfig`, `ICalImport`, `ICalTimezones`,
    `ExchangeConnectorConfig`).
  - [ ] Batch 4: view family (`Export`, `TableView`, `CalendarView`,
    `ExternalEventImport`, `EventTimeCalculator`).
  - [ ] Batch 5: admin family (`JNDIConfig`, `PreferencesAdmin`,
    `ReservationEdit`, `UrlEncryption`, `Archiver`).
  - [ ] After each batch: `mvn -pl rapla-app -am test`, snapshot OpenAPI,
    diff against pre-PRD baseline.
- [ ] Phase 3 — Pattern B (3 groups)
  - [ ] Extract `RaplaResources` interface in `rapla-core/.../rest/`; inline
    `RaplaResourcesRestPage` into `RaplaResourcesController`; delete the
    RestPage and its `@Bean`.
  - [ ] Same for `RaplaEvents`.
  - [ ] Same for `RaplaDynamicTypes`.
- [ ] Phase 4 — tighten architecture test
  - [ ] Update `ApiPrefixArchitectureTest` to require every `@RestController`
    outside the `ALLOWED_NON_API` list to implement an interface annotated
    with `@HttpExchange("/api/...")`.
- [ ] Phase 5 — cleanup
  - [ ] Delete the `org.rapla.endpoints.server` package (now empty).
  - [ ] Delete the unused `jakarta.ws.rs.*` imports across the codebase
    (verify nothing else references JAX-RS — only the three RestPage classes
    did).
  - [ ] Update AGENTS.md §15: replace "every controller has a class-level
    `@RequestMapping(\"/api/...\")`" with "every controller implements an
    interface annotated with `@HttpExchange(\"/api/...\")`."
  - [ ] Move PRD to `docs/prd/done/`, status `done`.

## Tests

- **Existing tier-3 / tier-4 server tests must pass unchanged.** This is a
  refactor; observable behaviour is identical. No new test failures.
- **OpenAPI snapshot diff.** Capture before/after each phase; the diff must
  be limited to cosmetic ordering changes (no path, verb, param, schema
  drift).
- **One new tier-3 architecture test per pattern:**
  - `Pattern A test`: pick `RemoteStorageController`, assert via reflection
    that `RemoteStorageController.class.getInterfaces()` contains
    `RemoteStorage`. Catches future regressions where someone adds
    `@RequestMapping` back to the controller class instead of relying on the
    interface.
  - `Pattern B test`: after Phase 3, assert that the `org.rapla.endpoints.server`
    package contains no classes. Catches a future RestPage being added back.
- **Smoke test from Phase 0** stays as the canonical reference for "Spring
  6 routes from `@HttpExchange` on an implemented interface."
- **Per AGENTS.md §1**, every conversion in Phase 2/3 starts with a small
  red test that exercises the endpoint, switches to green when the
  conversion is correct, and stays in the suite afterwards. For most
  endpoints an existing tier-3 test is sufficient — only add a new one for
  endpoints that lack tier-3 coverage today.

## Open questions

1. **Does Spring 6 honour `@HttpExchange` on an implemented interface for
   server routing, or only for `HttpServiceProxyFactory` client building?**
   Phase 0's smoke test answers this. If the answer is "client only", the
   PRD falls back to: keep the controller's `@RequestMapping` annotations,
   but enforce via `ApiPrefixArchitectureTest` that the path strings on the
   interface and the controller match exactly. Less satisfying but still
   eliminates drift.
2. **Should the `*RestPage` interface live in `rapla-core/.../rest/`
   (consistent with PRD 026 / Angular codegen) or in
   `rapla-core/.../endpoints/` (matches the legacy package)?** Recommend
   `rapla-core/.../rest/` to converge on a single home for `@HttpExchange`
   interfaces. Open until Phase 3 starts.
3. **`RaplaResourcesRestPage.getClassificationFilter(...)` is a `public static`
   helper called from at least one other class.** Before inlining into the
   controller, find callers and extract the helper to a util class so the
   controller body stays focused.
4. **What is the right home for `@ConditionalOnBean(RemoteSession.class)`?**
   Currently on the controller class. Moving it to the interface annotation
   doesn't make sense (interfaces are routing contracts, not bean conditions).
   Leave on the controller; document the convention.

## Appendix A — full pair inventory (2026-05-21)

### Pattern A — `@HttpExchange` interface + Spring controller (21 pairs)

| URL prefix | Interface (`rapla-core`) | Controller (`rapla-server/.../web`) | Shared backing impl |
|---|---|---|---|
| `/api/storage` | `storage.dbrm.RemoteStorage` | `RemoteStorageController` | `RemoteStorageImpl` ✓ |
| `/api/locale` | `storage.RemoteLocaleService` | `RemoteLocaleController` | — |
| `/api/logger` | `endpoints.RemoteLogger` | `RemoteLoggerController` | `RemoteLoggerImpl` ✓ |
| `/api/plugins` | `rest.PluginsService` | `PluginsController` | — |
| `/api/settings` | `rest.SettingsService` | `SettingsController` | — |
| `/api/archiver` | `plugin.archiver.ArchiverService` | `ArchiverController` | — |
| `/api/mail/config` | `plugin.mail.MailConfigService` | `MailConfigController` | — |
| `/api/mail/send` | `plugin.mail.MailToUserInterface` | `MailToUserController` | `MailToUserImpl` ✓ |
| `/api/ical/config` | `plugin.export2ical.ICalConfigService` | `ICalConfigController` | — |
| `/api/ical/import` | `plugin.ical.ICalImport` | `ICalImportController` | — |
| `/api/ical/timezones` | `plugin.export2ical.ICalTimezones` | `ICalTimezonesController` | — |
| `/api/export` | `plugin.tableview.ExportService` | `ExportController` | — |
| `/api/table` | `plugin.tableview.TableViewService` | `TableViewController` | — |
| `/api/calendar` | `plugin.calendarview.CalendarViewService` | `CalendarViewController` | — |
| `/api/externaleventimport` | `plugin.externaleventimport.ExternalEventImportService` | `ExternalEventImportController` | — |
| `/api/eventtimecalculator` | `plugin.eventtimecalculator.EventTimeCalculatorConfigService` | `EventTimeCalculatorConfigController` | — |
| `/api/exchange/config` | `plugin.exchangeconnector.ExchangeConnectorConfigRemote` | `ExchangeConnectorConfigController` | — |
| `/api/jndi` | `plugin.jndi.internal.JNDIConfig` | `JNDIConfigController` | — |
| `/api/admin/panels` | `plugin.adminpanels.PreferencesAdminService` | `PreferencesAdminController` | — |
| `/api/edit` | `plugin.reservationedit.ReservationEditService` | `ReservationEditController` | `ReservationEditImpl` ✓ |
| `/api/urlencryption` | `plugin.urlencryption.UrlEncryption` | `UrlEncryptionController` | — |

### Pattern B — JAX-RS `*RestPage` + Spring controller (3 pairs)

| URL prefix | RestPage (`rapla-server/.../endpoints/server`) | Controller (`rapla-server/.../web`) |
|---|---|---|
| `/api/resources` | `RaplaResourcesRestPage` (`@Path("resources")`) | `RaplaResourcesController` |
| `/api/events` | `RaplaEventsRestPage` (`@Path("events")`) | `RaplaEventsController` |
| `/api/dynamictypes` | `RaplaDynamicTypesRestPage` (`@Path("dynamictypes")`) | `RaplaDynamicTypesController` |

### Out of scope — single-declaration controllers (9)

`OAuthConfigController`, `OAuthExchangeController`, `ApiKeyController`,
`IndexPageController`, `LoginPageController`, `StatusPageController`,
`RaplaJNLPController`, `Export2iCalController`, `CalendarPageController`.
