# PRD 008: Server Cleanup — superseded

**Status:** superseded by [008-server-sync-client-async-facade-split.md](008-server-sync-client-async-facade-split.md) (done 2026-05-07)
**Date:** 2026-05-07

## Why this stub still exists

This file was created the same day as `008-server-sync-client-async-facade-split.md` as a placeholder for "server cleanup". The sibling PRD shipped first and ended up covering the same scope (remove rxjava from server, replace `Promise<T>` with sync siblings, drop reactive-streams dep). Rather than delete this file (numbers in `docs/prd/` should be stable so cross-references don't rot), it's kept as a redirect.

## Original speculative scope (now done in the sibling PRD)

- Remove reactive abstractions (`io.reactivex.rxjava3`, `org.reactivestreams`) from server code → done, see Phases 0–4 of the sibling PRD.
- Replace each `Promise<T>` / `CommandScheduler` / RxJava observable on the server side with sync siblings → done, see Phases 5–9 of the sibling PRD.
- Audit `CommandScheduler` callers for blocking patterns that become trivial under direct calls → done as part of Phases 5–9.

## What's still server-cleanup work but is NOT covered by the sibling PRD

These are the genuine remaining server-side todos. Each has its own home:

- **Wire orphan plugin extensions as Spring beans** (~25 server `@Extension` / `@DefaultImplementation` classes) — tracked under PRD 002's "Audit of remaining `@Inject` files" + the post-002 follow-ups noted in PRD 002's status section.
- **Drop Gson dep from BOM after `HTTPWithJsonMailConnector` + `HTTPWithJsonConnector` migrate to Jackson** — tracked under PRD 001 Phase 9.
- **Migrate `autowireBean()` workarounds in `ServerServiceConfig`/`ServerCoreConfig` (~19 occurrences) to constructor injection** — per AGENTS.md ctor-injection rule, on a touch-it-as-you-go basis. No dedicated PRD.
- **Phase 5 error-handling cleanup for the new REST surface** — done in [PRD 009](../009-server-bulk-storage-rest-api.md) Phase 5 (`@RestControllerAdvice`).

### Server orphan @DefaultImplementation survey (2026-05-08)

Six `@DefaultImplementation` classes in `rapla-server` are not registered as Spring beans, **not because of a wiring oversight, but because they are dead code in the post-Spring-Boot architecture**. Each has zero external consumers:

| Class | `@DefaultImplementation of` | Why it's dead |
|---|---|---|
| `RemoteAuthentificationServiceImpl` | `RemoteAuthentificationService` | Auth flow now goes through `AuthController` (REST) + `RaplaAuthentificationService` (server-side) + JWT. The legacy `RemoteAuthentificationService` interface has no consumers in the codebase. |
| `ExchangeConnectorRemoteObjectFactory` / `ExchangeConnectorRemoteConfigFactory` | `ExchangeConnectorRemote` / `ExchangeConnectorConfigRemote` | Exchange connector plugin server-side has not been reactivated under Spring. The interfaces are referenced only by sibling files within the same plugin's server package. |
| `ImportExportManagerContainerImpl` | `ImportExportManagerContainer` | Console-only entry point (`server.internal.console`), no current consumer. |
| `RaplaMailToUserOnLocalhost` | `MailToUserInterface` | Legacy localhost-test mail impl. Active mail flow uses `MailToUserImpl` (wired in `ServerCoreConfig.mailToUser` `@Bean`). |
| `ImportExportManagerDefaultImpl` | `ImportExportManager` | NOT orphan — wired via the `ServerStorageSelector` factory pattern (`ServerServiceConfig.importExportManager` `@Bean` calls `selector.getImportExportManager().get()`). The selector returns the right impl per backend. |

**Decision:** leave these untouched. If a future task needs any of them, that task should add the `@Bean` factory at the same time. A clean-up PR could remove the legacy classes entirely (they'd reduce the @Inject-no-stereotype count) but that's a separate scope.

### ServerExtension wiring (done 2026-05-08)

Three of the four `ServerExtension` impls were wired to `ServerServiceConfig` as `@Bean(name = pluginId)` factories so the consumer's `Map<String, ServerExtension>` injection populates correctly:
- `JavascriptPatcher` (id `org.rapla.plugin.javascriptpatch.server`) — also migrated to constructor injection in the same change (per AGENTS.md ctor-injection rule).
- `ArchiverServiceTask` (id `org.rapla.plugin.archiver.server`) — already ctor-injected.
- `NotificationService` (id `org.rapla.plugin.notification`) — required adding a `NotificationResources` `@Bean` first.

`SynchronisationManager` (id `org.rapla.ExchangeConnector`) was intentionally NOT wired — its constructor needs `ConfigReader`, `ShowExchangeForUser`, `ExchangeAppointmentStorage`, `Set<ExchangeConfigExtensionPoint>`, `MailToUserImpl`, `ExchangeConnectorResources`, all of which would cascade more wiring work in the not-yet-reactivated exchange plugin. Leave for a focused exchange-plugin reactivation PR.

If a fresh "server cleanup" scope appears, write a new PRD at the next available number rather than re-using this file.
