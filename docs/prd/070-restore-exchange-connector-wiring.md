# PRD 070: Restore Exchange-connector server wiring (multi-pod scheduler split)

**Status:** in-progress

## Goal

Make the EWS Exchange-connector work again end-to-end on the Spring Boot server. The
Swing "Exchange Connector" user dialog currently dies with `Could not load: 404
Whitelabel`, and the scheduled Exchange sync never runs. Both are casualties of an
**incomplete migration**, not a deliberate removal — restore them, and split the
runtime so the GUI/config path works on **all** deployments while the `@Scheduled`
sync engine runs on **one** deployment only.

## Background — what broke and why

On `master` the connector worked via the legacy rapla DI container:

- `ExchangeConnectorRemoteObjectFactory` (`@DefaultImplementation(of=ExchangeConnectorRemote.class)`)
  served the `/api/exchange/connect` family — it injected `SynchronisationManager`,
  resolved the user via `session.checkAndGetUser(request)`, and delegated all five
  methods to the manager.
- `SynchronisationManager` was instantiated as a container component
  (`addContainerProvidedComponent(SynchronisationManager.class, …)`) and registered
  for scheduling via a server extension.

The Spring Boot / reactor-split migrations dropped this without a full replacement:

| Remote / engine | master | spring-boot (today) |
|---|---|---|
| `ExchangeConnectorConfigRemote` | `ExchangeConnectorRemoteConfigFactory` | ✅ ported → `ExchangeConnectorConfigController` |
| `ExchangeConnectorRemote` | `ExchangeConnectorRemoteObjectFactory` | ❌ dropped, no controller → **404** |
| `SynchronisationManager` | container component + `@Scheduled`-equivalent | ❌ never registered as a bean → `@Scheduled` never fires |

Cross-references: the `@Scheduled` annotations were added in **PRD 019** (which listed
`SynchronisationManager` as migrated, but the bean wiring was never completed); the
controller miss happened in **PRD 049** (its inventory only had the config remote);
the "intentionally NOT wired" deferral comment in `ServerServiceConfig` dates from the
**PRD 005** reactor split. **PRD 048** even assumes `SynchronisationManager` is a live
`@Scheduled` bean ("must NOT restart") — that assumption is false until this PRD lands.
**PRD 038** (Graph backend) is *additive* and explicitly does not replace this EWS path.

## Scope

- **In:** wire `SynchronisationManager` + its dependency graph as Spring beans on all
  deployments; add the `ExchangeConnectorController`; split scheduling into a separate
  `@ConditionalOnProperty` trigger bean gated on the existing `rapla.exchange.enabled`
  flag (already set true only on the dhbw sync deployment — was a dead property, now bound).
- **Out:** the Microsoft Graph backend (PRD 038); any new UI; changing the EWS
  protocol code (`EWSConnector`, `AppointmentSynchronizer`); the SPA — the Exchange
  dialog stays Swing-only.

## Design decisions (locked)

1. **`SynchronisationManager` is a normal `@Bean` (service) on all deployments.** Full
   dependency graph wired: `ShowExchangeForUser`, `ExchangeAppointmentStorage`,
   `ConfigReader`, `ExchangeConnectorResources`, `AppointmentFormater` (`MailToUserImpl`
   is already a bean). It is available everywhere so the GUI methods work everywhere;
   it is simply **not scheduled** unless this deployment enables sync.

2. **`ExchangeConnectorController implements ExchangeConnectorRemote`** — the Spring port
   of master's `ExchangeConnectorRemoteObjectFactory`. Same shape as
   `ExchangeConnectorConfigController` (`@RestController`, `RemoteSession` +
   `HttpServletRequest`, `session.checkAndGetUser(request)`), delegates 1:1 to the
   manager. All five methods restored, incl. `refreshMailboxes`.

3. **Scheduling split.** The two `@Scheduled` methods (`synchronizeQueue`,
   `synchronizeMailboxes`) move OFF the manager onto a tiny `ExchangeSchedulerTrigger`
   bean, `@ConditionalOnProperty(name = "rapla.exchange.enabled", havingValue
   = "true")`, that delegates to the manager. → the trigger bean (and thus all
   scheduling) exists only on the deployment where the property is true. No idle ticks
   elsewhere.

4. **Two gate layers, kept distinct:**
   - `rapla.exchange.enabled` (yaml, per-deployment) — decides *where* the scheduler
     runs. **Reuses the existing flag** (previously dead — only referenced in the panel
     docstring); already `true` in `application-sync.yml`, `false` in web/test/local.
     Binding it here makes that docstring's "per-deployment kill switch" claim true.
   - `ENABLED_BY_ADMIN` (`exchange_connector_enabled_by_admin`, data.xml / shared store)
     — the existing `if(!enabled) return` runtime kill switch, kept so admins can
     disable sync without a redeploy. Also still gates dialog visibility via
     `ShowExchangeForUser`.

5. **GUI methods stay synchronous (master / "Option 1").** `changeUser` /
   `refreshMailboxes` keep their on-demand EWS read (`connector.test()` +
   `loadMailboxes()`) so the dialog validates credentials and shows the shared-mailbox
   list immediately — on whichever pod serves the request. They *additionally* write
   the `REFRESH_MAILBOXES` / `RESYNC_USER` preference flag that the sync pod's scheduler
   consumes (and resets) to perform the heavy box-map rebuild + appointment push. Read
   for immediate feedback + DB-write to trigger the sync pod = both, as on master.
   Reachability to `ex.dhbw.de` on every pod is a deployment/network fact, not designed
   around.

## Plan

Test-first per AGENTS.md §1: the failing tier-3 test (`ExchangeConnectorControllerTest`,
already written) asserts `GET /api/exchange/connect` → 200. It currently can't run
because the `@SpringBootTest` context is blocked branch-wide by unrelated in-flight
PRD 069 GraphQL schema edits — resolve that (or use a clean worktree off HEAD) before
relying on the red→green signal.

### Phase 1 — wire `SynchronisationManager` + deps as beans (all deployments)

Bean dependency order (each later bean consumes earlier ones):

- [x] `ShowExchangeForUser` ← `CachableStorageOperator`
- [x] `ExchangeAppointmentStorage` ← `RaplaFacade`, `CachableStorageOperator`, `ShowExchangeForUser`
- [x] `ConfigReader` ← `CachableStorageOperator` (reads system prefs at construction — relies on
      the PRD 019 storage-up invariant: operator is connected at `cachableStorageOperator` `@Bean` time)
- [x] `ExchangeConnectorResources` ← `BundleManager`
- [x] `AppointmentFormater` — already a bean (`ServerCoreConfig:158`), injected as a param
- [x] `SynchronisationManager` ← all the above + `RaplaResources`, `TimeZoneConverter`,
      `RaplaKeyStorage`, `MailToUserImpl`, `Set<ExchangeConfigExtensionPoint>` (Spring
      collects; empty set on non-dhbw is fine)
- [x] Removed the "intentionally NOT wired" comment in `ServerServiceConfig`.

Done: factories added to `ServerServiceConfig` (next to `notificationService`).

### Phase 2 — split scheduling off the manager

- [x] Removed the two `@Scheduled` annotations from `SynchronisationManager.synchronizeQueue()`
      / `synchronizeMailboxes()`; methods stay public.
- [x] New `ExchangeSchedulerTrigger` (registered as a `@Bean` with
      `@ConditionalOnProperty(prefix = "rapla.exchange", name = "enabled", havingValue = "true")`)
      with two `@Scheduled` methods delegating to the manager (same `fixedRate` periods).
- [x] Kept the `if(!enabled) return` (`ENABLED_BY_ADMIN`) guard inside the manager methods.

### Phase 3 — restore the controller

- [x] `ExchangeConnectorController implements ExchangeConnectorRemote` in
      `org.rapla.server.spring.web` — `@RestController @ConditionalOnBean(RemoteSession.class)`,
      ctor `(SynchronisationManager, RemoteSession, HttpServletRequest)`, each method resolves
      `session.checkAndGetUser(request)` and delegates (Spring port of master's
      `ExchangeConnectorRemoteObjectFactory`).
- [x] `ApiPrefixArchitectureTest` green (4/4). Required adding `/api/exchange/connect`
      (+`/**`) to the `client` group in `SpringDocGroupsConfig` (next to its
      `/api/exchange/config` sibling) — `everyRestControllerBelongsToExactlyOneSpecGroup`
      enforces every `@RestController` belongs to exactly one OpenAPI group.

### Phase 4 — deployment config

- [x] No config change needed: `rapla.exchange.enabled` is **already** `true` in
      `application-sync.yml` and `false` in `application-web.yml` / `application-test.yml` /
      dev `local/` — exactly the desired split. Binding it (Phase 2) activates it.
- [x] Resolved by binding: the `ExchangeConnectorPreferencesPanel` docstring already says
      `rapla.exchange.enabled` is the "per-deployment kill switch" — that's now true (the
      property is bound), so the docstring is no longer a dead reference. No edit needed.

### Phase 5 — verify

- [x] `ExchangeConnectorControllerTest` green (`GET /api/exchange/connect` → 200). Red-check
      confirmed: with the controller removed (clean rebuild) the test fails
      `expected:<200> but was:<404>` — the exact dialog error. Verified in a throwaway
      worktree off HEAD, because the main tree's `@SpringBootTest` context is blocked by
      unrelated in-flight PRD 069 GraphQL schema edits. Full context booted with all new
      `@Bean` factories → wiring validated end-to-end.
- [x] `ApiPrefixArchitectureTest` green (4/4) after the `SpringDocGroupsConfig` grouping add.
- [x] Trigger-bean presence/absence test (`ExchangeSchedulerTriggerConditionTest`): with
      `rapla.exchange.enabled=true` the `ExchangeSchedulerTrigger` bean is present; with
      `=false` it's absent — and `SynchronisationManager` is present in both. Green.
- [ ] Live dhbw dev-server smoke: dialog loads (no 404); with `rapla.exchange.enabled=true`
      the sweeps log `synchronizeMailboxes`/`Update triggered`. Pending GraphQL-WIP resolution
      (the dev server is also blocked from booting by the same schema edits).

## Files changed

- `rapla-server/.../ServerServiceConfig.java` — `@Bean` factories + removed the
  "intentionally NOT wired" comment.
- `rapla-server/.../exchangeconnector/server/SynchronisationManager.java` — removed the two
  `@Scheduled` annotations (methods stay public).
- `rapla-server/.../exchangeconnector/server/ExchangeSchedulerTrigger.java` — new `@Scheduled`
  trigger gated by `@ConditionalOnProperty(rapla.exchange.enabled)`.
- `rapla-server/.../server/spring/web/ExchangeConnectorController.java` — new `@RestController`.
- `rapla-app/.../server/spring/SpringDocGroupsConfig.java` (test) — `/api/exchange/connect`
  added to the `client` group.
- `rapla-app/.../server/spring/web/ExchangeConnectorControllerTest.java` — new tier-3 test.
- `rapla-app/.../server/spring/web/ExchangeSchedulerTriggerConditionTest.java` — new tier-3
  test for the deployment split (trigger present/absent per `rapla.exchange.enabled`).

## Tests

- **Tier 3 (MockMvc):** `GET /api/exchange/connect` returns 200 (not 404) for an
  authenticated user with no Exchange connection → status = disconnected.
  (`ExchangeConnectorControllerTest`.)
- **Tier 3:** scheduler trigger bean is present when `rapla.exchange.enabled=true`
  and absent when false/missing.
- Leak/permission: the connect endpoints are per-user (own credentials/status only);
  no cross-user data.

## Open Questions

- None blocking. (Resolved: full manager as service everywhere; new
  `rapla.exchange.enabled` property; keep `ENABLED_BY_ADMIN` runtime guard;
  GUI methods synchronous.)
- Note: tier-3 verification depends on the `@SpringBootTest` context booting — currently
  blocked branch-wide by unrelated in-flight PRD 069 GraphQL schema edits (two empty
  input types). Verify once that lands or in a clean worktree off HEAD.
