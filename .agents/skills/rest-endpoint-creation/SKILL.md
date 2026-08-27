---
name: rest-endpoint-creation
description: Use whenever you create, modify, or delete a rapla REST endpoint — that means any class with `@RestController`, any `@HttpExchange` interface in `rapla-core`, anywhere you'd add or change `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`/`@RequestMapping`, or any DTO/request/response shape on the wire. Carries the PRD 049 single-source-of-truth pattern (interface owns routing metadata, controller `implements` it), the PII-in-query-params guardrail with the worked `ChangeNamePost`/`ChangeEmailPost` example, the explicit-`@RequestParam("name")` rule that survives parameter-name erosion on `@Override`, the SpringDocGroupsConfig grouping, the OpenAPI spec-capture command, and the documented exceptions where class-level `@RequestMapping` is still acceptable (page generators, multi-impl SPIs, controllers with in-process impl callers, `ResponseEntity`-returning download endpoints). AGENTS.md §15 keeps just the invariant + the skill pointer; the implementation patterns live here.
---

# Creating a REST endpoint in rapla

Single source of truth for path, verb, parameter binding, request body shape, return type: the **`@HttpExchange` interface in `rapla-core`**. The controller `implements` it. PRD 049 (2026-05-21) made this the live pattern after auditing every Pattern A interface for drift between the old `@RequestMapping` controllers and the `@HttpExchange` proxies — the interface form is what the Swing-side `HttpServiceProxyFactory` and SpringDoc both read, so making it the routing source eliminates wire drift.

## The pattern

### 1. Interface in `rapla-core/src/main/java/org/rapla/rest/` (or a plugin's `rapla-core` package)

```java
package org.rapla.rest;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/yourthing")
public interface YourThingService {
    @GetExchange("/{id}")
    Foo get(@PathVariable("id") String id) throws RaplaException;

    @PostExchange
    Foo create(@RequestBody CreateFooRequest req) throws RaplaException;

    @GetExchange("/search")
    List<Foo> search(@RequestParam("q") String query,
                     @RequestParam(value = "limit", required = false) Integer limit) throws RaplaException;
}
```

Declare path, verb, AND every parameter binding (`@PathVariable`, `@RequestParam`, `@RequestBody`) here — these inherit to the controller and to the Swing-side proxy.

### 2. Controller in `rapla-server/.../web/`

```java
package org.rapla.server.spring.web;

@RestController
@ConditionalOnBean(RemoteSession.class)
public class YourThingController implements YourThingService {
    private final RaplaFacade facade;
    private final RemoteSession session;
    private final HttpServletRequest request;

    public YourThingController(RaplaFacade facade, RemoteSession session, HttpServletRequest request) {
        this.facade = facade;
        this.session = session;
        this.request = request;
    }

    @Override
    public Foo get(String id) throws RaplaException {
        User user = session.checkAndGetUser(request);
        // ... logic
    }
    // ...
}
```

Carries only logic + `@Override`. No class-level `@RequestMapping`, no method-level `@GetMapping`/`@PostMapping`, no parameter-level `@PathVariable`/`@RequestParam`/`@RequestBody` — all inherited.

Still carries: `@RestController`, `@ConditionalOnBean(RemoteSession.class)` or `@ConditionalOnProperty(...)` for plugin gating, constructor-injected deps including `HttpServletRequest` as a Spring request-bound proxy.

## Hard rules

### No PII in query params (PRD 049 §"Wire-contract hardening")

Query params land in server access logs, intermediary proxies, and browser history — they're the wrong channel for personal data. Audit each interface method for query params carrying:

- Names (display name, title, surname, lastname)
- Email addresses
- Passwords / tokens / API keys (these should already be in body; verify)
- Personal IDs that aren't opaque (e.g. employee number, citizen ID)

Move those to a `@RequestBody` DTO. Internal opaque IDs (rapla `userId`, allocatable id, reservation id) and timestamps stay in query params — they're not PII.

**Worked example from PRD 049 Phase 1** — three RemoteStorage methods had a query+body mix carrying name/email PII:

```java
// BEFORE — username + title + surname in query, lastname in body
void changeName(@RequestParam("username") String username,
                @RequestParam("title") String newTitle,
                @RequestParam("surename") String newSurename,
                @RequestBody String newLastname);

// AFTER — single body DTO, no PII in URL
@PostExchange("/change/name")
void changeName(@RequestBody ChangeNamePost job) throws RaplaException;

class ChangeNamePost {
    private String username;
    private String newTitle;
    private String newSurename;
    private String newLastname;
    // constructor + getters
}
```

Same for `changeEmail`/`confirmEmail` → `ChangeEmailPost` body DTO.

### API-key scope coverage — automatic for `dispatch()` writes, manual for everything else

API-key scopes (`read`/`write_events`/`write_resources`/`write_all`, PRD 076) are enforced at **one** chokepoint: `LocalAbstractCachableOperator.guardApiKeyScopes()`, reached only through `operator.dispatch(UpdateEvent)` (and `storeAndRemove`/`facade.store*`/`facade.removeObjects` → dispatch). So:

- **Endpoints that persist by writing entities through the facade/operator are covered for free** — you cannot forget the scope check, because every `dispatch()` hits the guard, which inspects `evt.getStoreObjects()`/`getRemoveIds()` per entity type. This is true for both REST and GraphQL, and for plugins (dhbwrapla adds zero scope code and is fully covered).
- **Endpoints that mutate state WITHOUT emitting an `UpdateEvent` bypass the guard entirely** and must gate themselves by hand. These are: `ImportExportManager.saveData(...)` (bulk import/export/restore), raw JDBC, file/blob writes, or anything that mutates a different store. There is no compiler or architecture test forcing the check — it is genuinely easy to forget.

For a bypass write, call `ApiKeyScopeContext.requireWriteAllForBulk("<operation>")` at the access gate. A non-api-key caller (`current() == null`) passes; a scoped key must hold `write_all`, since bulk ops touch arbitrary entity types. **Reference impl:** `ArchiverServiceImpl.checkAccess()` — `backup`/`restore`/`delete` go through `saveData`, so they call `requireWriteAllForBulk` after the `isAdmin()` check (an admin's read-only key would otherwise trigger a full restore). Regression lock: `ArchiverServiceAccessTest`. When you add an endpoint, ask: *does this reach `operator.dispatch()`?* If no, you own the scope check.

### Always name `@RequestParam("name")` and `@PathVariable("name")` explicitly

Java parameter names are lost on `@Override`-inherited methods unless the implementing class is compiled with `-parameters`. SpringDoc emits the OpenAPI spec by reading the controller's `@Override` signature — without the explicit name on the interface annotation, parameters become `arg0`/`arg1`/`arg2`, breaking SPA codegen and Swing proxies. **Always name the binding.**

### required=true is the default; only mark false when you mean it

Drift caught by PRD 049: several interface methods had `@RequestParam(..., required = false)` while the old controller forced required-at-server with `@RequestParam("name")` (default required=true). The tier-3 tests asserted 400 for missing params. The wire truth is the controller behavior — fix the interface to match the test, not the other way around.

### `@RequestBody` is required when the param IS the body

Spring's `HttpServiceProxyFactory` has no documented contract for unannotated method parameters on `@HttpExchange` interfaces. If a `String`/object parameter should land in the request body, annotate it `@RequestBody` on the interface explicitly. The auto-inference behaviour observed in some Spring versions is not contract.

## SpringDocGroupsConfig grouping

Add the controller's path to exactly ONE group in `SpringDocGroupsConfig` (PRD 031 Phase 5):

| Group | What goes here |
|---|---|
| `auth` | Login / OAuth2 / `/api/auth/*` |
| `client` | SPA + admin UI surface, OpenAPI codegen target |
| `rest` | PRD 009 bulk REST (`/api/storage`, `/api/resources`, `/api/events`, `/api/dynamictypes`) |
| `exports` | File in/out + legacy iCal feeds |

`auth` is also a subset of `client` so SPA codegen has login covered — the only deliberate overlap. `ApiPrefixArchitectureTest` fails CI on controllers missing from every group or in two (non-`auth`) groups.

## Capture the OpenAPI spec

After adding or changing an endpoint:

```
mvn -pl rapla-app -am test -Dtest=OpenApiSpecCaptureTest -Dopenapi.update=true -Dtest.excludedGroups=
```

Commit the diff under `rapla-app/src/main/resources/openapi/`. CI runs the same test in compare mode (no `-Dopenapi.update`) and fails on drift. Verify the diff has no `arg0`/`arg1` param names — if it does, you forgot an explicit `@RequestParam("name")` on the interface.

## Documented exceptions where class-level `@RequestMapping` is still acceptable

The interface-implements pattern doesn't fit every case. Three exception categories are allowed and recognised by `ApiPrefixArchitectureTest`:

1. **Plugin SPI controllers that aggregate over a `Set<XxxImpl>` SPI** — e.g. `PreferencesAdminController` aggregates over `Set<PreferencesPanel>`. The controller `implements PreferencesAdminService` and IS the aggregator; no separate Impl bean.

2. **Controllers whose impl class has real in-process callers outside the controller** — examples:
   - `ArchiverController` / `ArchiverServiceImpl` — used by `ArchiverServiceTask` + `ArchiverPreferencesPanel`
   - `ExternalEventImportController` / `ExternalEventImportService` impls — plugin SPI with deployment-custom impls
   
   Making the controller `implements` would cause "multiple beans of type X" conflicts with the existing `@Bean` impl. Keep the impl as a separate `@Bean`; the controller stays as a thin delegator with explicit `@RequestMapping("/api/...")` + method-level `@GetMapping`/`@PostMapping`. Until in-process callers are migrated to depend on the interface (then the controller can be the sole impl), this is the only viable shape.

3. **HTML/iCal/JNLP-returning page controllers** — `IndexPageController`, `StatusPageController`, `RaplaJNLPController`, `CalendarPageController`, `Export2iCalController`. Not on the SPA-codegen / Swing-proxy surface and have no `@HttpExchange` interface; they use class-level `@RequestMapping` directly. `CalendarPageController` dispatches into the `HTMLViewPage` plugin SPI for view rendering.

`ResponseEntity`-returning download endpoints are NOT an exception (anymore): an `@HttpExchange` interface method can declare `ResponseEntity<byte[]>` directly — see `DocumentApi.csv` (`@HttpExchange("/api/documents")`) implemented by `DocumentController` with `Content-Disposition: attachment` headers.

## JAX-RS is gone

PRD 049 removed `jakarta.ws.rs-api` from rapla entirely. `ApiPrefixArchitectureTest.noJakartaWsRsImportsAnywhereInReactor` fails CI on any `import jakarta.ws.rs.*` reintroduction in `rapla-core`, `rapla-client`, `rapla-server`, or `rapla-app`. The Keycloak NTLM submodule (`tools/keycloak/ntlm-authenticator/`) is intentionally outside this scope — it uses Keycloak's own JAX-RS surface.

## Common pitfalls

- **Forgetting `@RequestBody` on a String/object param** → Spring's proxy is undefined-behavior. Test by curling the endpoint manually and inspecting the request body.
- **Forgetting the explicit name on `@RequestParam`/`@PathVariable`** → SpringDoc emits `arg0`/`arg1`, SPA codegen breaks.
- **Mismatched `required` between interface and old controller** → check tier-3 MockMvc test expectations; the tests are the wire-truth.
- **Drift on `@RequestBody`-vs-`@RequestParam`** → SpringDoc reflects the interface declaration. If the OpenAPI doc shows a body param where the old controller had a query, the interface is wrong.
- **Missing group entry in SpringDocGroupsConfig** → CI fails on `everyRestControllerBelongsToExactlyOneSpecGroup`.
- **Adding a `WebMvcConfigurer` to inject a path prefix programmatically** → don't. The literal-`/api/`-on-the-interface invariant is what the architecture test guards.
