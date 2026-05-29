# PRD 019: Spring Boot Lifecycle Migration — Replacing `ServerExtension`

**Status:** done — Phases 1–4 landed 2026-05-10; moved to `done/` 2026-05-11. `ServerExtension` interface deleted; all 6 implementors migrated to `@Scheduled` (5 of them) + `@EventListener(ApplicationReadyEvent.class)` (`JavascriptPatcher`). `operator.connect()` moved into the `cachableStorageOperator` `@Bean` factory. `@DependsOn("serverServiceContainer")` removed from `RaplaKeyStorage`. dhbw cron cadence (3×daily) restored via `@Scheduled(cron = "0 11 8,12,16 * * *", zone = "Europe/Berlin")` for Dualis (and 8:21/12:21/16:21 for Morada). 8/8 baseline tests still pass; both repos compile green.
**Date:** 2026-05-10 (closed: 2026-05-11)

## Goal

Move rapla off its bespoke `ServerExtension` start()/stop() lifecycle (driven by `ServerServiceImpl` iterating a `Map<String, ServerExtension>`) to Spring Boot's native bean lifecycle:

- `@Scheduled` for recurring tasks (replaces the removed `CommandScheduler.scheduleAtGivenTime`)
- `@EventListener(ApplicationReadyEvent.class)` for one-shot startup tasks needing loaded storage
- `@PostConstruct` / `@PreDestroy` for per-bean init/teardown
- `SmartLifecycle` only for rare cases needing symmetric phased start/stop across many beans

## Why this is needed now

1. **Cron API was removed without a replacement.** `CommandScheduler.scheduleAtGivenTime(action, hour, minute)` is gone (removed in the `4f50a558 big ai assisted refactoring` window). The two dhbwrapla callers (`DualisSyncJobStarter`, `MoradaSyncJobStarter`) used it for 3×daily cadence (8:11, 12:11, 16:11). The 2026-05-10 dhbw session collapsed both to once-daily as a workaround; this PRD restores the original cadence Spring-native.

2. **Storage-up ordering leaks an implementation detail.** `ServerServiceImpl`'s constructor calls `operator.connect()` *before* iterating `ServerExtension.start()` — and that's the guarantee extensions depend on. Same guarantee is exposed today as `@DependsOn("serverServiceContainer")` (used by `RaplaKeyStorage`) — consumers needing connected storage hard-code an unrelated infra bean name. In clean Spring Boot, `operator.connect()` belongs in `@PostConstruct` on the operator bean; Spring's dep graph then implicitly guarantees any injector of `RaplaFacade`/`CachableStorageOperator` gets a connected one.

3. **`ServerExtension` is a pre-Spring relic.** 6 implementors today; 5 use it for scheduling, 1 (`JavascriptPatcher`) for one-shot init/teardown. Spring Boot's `@Scheduled` + `@EventListener` cover both idiomatically.

## Scope

### In scope

- `org.rapla.scheduler.CommandScheduler` — keeps `delay`/`schedule`/`run`/`supply`/`scheduleSynchronized`. No `scheduleAtGivenTime` resurrection.
- `org.rapla.server.extensionpoints.ServerExtension` — deprecate, then delete.
- 6 `ServerExtension` implementors — migrated per case.
- `ServerServiceImpl` — remove `Map<String, ServerExtension>` iteration and `operator.connect()` from its constructor. Connection moves to operator bean's `@PostConstruct`.
- `RaplaServerAutoConfiguration` — add `@EnableScheduling`.
- `@DependsOn("serverServiceContainer")` callsites — drop after Phase 1.

### Out of scope

- Rapla's `Promise<T>` / `CommandScheduler` async API — only lifecycle (start-up scheduling) changes, not scheduler internals.
- In-Reservation appointment scheduling (`Repeating`, etc.) — data model, not lifecycle.
- Replacing rapla's `Action` with `Runnable` — possible follow-up.

## Plan

### Phase 1 — Move `operator.connect()` to `@PostConstruct` (rapla-server)

1. Add `@PostConstruct void connect()` to `LocalAbstractCachableOperator` or concrete subclasses (`FileOperator`, `DBOperator`). Since these aren't direct Spring beans (built by `ServerStorageSelector.get()`), either wire via `ServerStorageSelector` calling `connect()` before returning, **or** convert the operator into a proper `@Bean` via `ServerCoreConfig` and let Spring fire `@PostConstruct`.
2. Remove `operator.connect()` from `ServerServiceImpl`'s constructor.
3. Verify `RaplaSpringBootApplicationTest` still passes.
4. Drop `@DependsOn("serverServiceContainer")` on `RaplaKeyStorage`.

### Phase 2 — `@EnableScheduling` (rapla-server)

1. Add `@EnableScheduling` to `RaplaServerAutoConfiguration`. Every deployment pulling rapla-server gets Spring's `TaskScheduler` automatically.
2. Optional: configure pool size via `spring.task.scheduling.pool.size=N` (default 1).

### Phase 3 — Migrate the 6 `ServerExtension` impls

| Impl | Where | Replacement |
|---|---|---|
| `NotificationService` | rapla-server / `plugin.notification` | Two `@Scheduled` methods (`sentUpdateMails` at `fixedRate=30000`, `retryMails` at `initialDelay=45000, fixedRate=...`). |
| `SynchronisationManager` | rapla-server / `plugin.exchangeconnector` | Two `@Scheduled` with existing periods. |
| `ArchiverServiceTask` | rapla-server / `plugin.archiver` | `@Scheduled` with existing cadence. |
| `JavascriptPatcher` | rapla-server / `plugin.javasciptpatch` | `@EventListener(ApplicationReadyEvent.class)` — one-shot. Pair with `@PreDestroy` if teardown needed. |
| `DualisSyncJobStarter` | dhbwrapla / `dhbw.sync.dualis.server` | `@Scheduled(cron = "0 11 8,12,16 * * *", zone = "Europe/Berlin")` calling `dualisImportJob.run()`. **Restores 3×daily.** |
| `MoradaSyncJobStarter` | dhbwrapla / `dhbw.sync.morada.server` | Same with 8:21/12:21/16:21. |

For each: drop `implements ServerExtension`, drop `start()`/`stop()`, drop `List<Cancellation> schedules`, move scheduling to `@Scheduled` on cleanly-named methods.

### Phase 4 — Delete `ServerExtension`

After all 6 impls are off:

1. Delete iteration in `ServerServiceImpl` constructor (lines 175–182) and in `stop()` (lines 236–...).
2. Drop the `Map<String, ServerExtension>` field + constructor arg.
3. Drop the `Supplier<Map<String, ServerExtension>>` arg from `ServerServiceConfig.serverServiceContainer(...)`.
4. Delete `org.rapla.server.extensionpoints.ServerExtension`.
5. Update PRD 003 §"Scheduled Background Jobs" — remove Option 1 (`@Component implements ServerExtension`); make `@Scheduled` canonical.
6. Update PRD 003 §"Extension Point Preservation Checklist" — remove `ServerExtension`.

## Storage-up ordering invariant (post-Phase 1)

Documented for downstream deployments:

> After Phase 1, any Spring bean injecting `RaplaFacade` or `CachableStorageOperator` is guaranteed to receive a **connected** operator before its own `@PostConstruct` runs. `@Scheduled` tasks fire after context refresh, which is after every `@PostConstruct`. So storage is always available in `@Scheduled`, `@EventListener(ApplicationReadyEvent.class)`, and any `@PostConstruct` depending on the facade — no `@DependsOn` required.

This is the contract dhbwrapla and future deployments depend on.

## Tests

| Phase | Test |
|---|---|
| 1 | `OperatorConnectInPostConstructTest` — `operator.isConnected()` true at downstream bean's `@PostConstruct`. |
| 1 | `RaplaSpringBootApplicationTest` — must still boot green. |
| 2 | `SchedulingEnabledTest` — a `@Scheduled` test bean's method runs at least once during a `@SpringBootTest`. |
| 3 | Per-impl smoke test confirming recurring cadence triggers (e.g. `MoradaSyncSchedulingTest` with 100ms `fixedRate` override). |
| 4 | Compile-only: `grep -r ServerExtension` returns zero across both repos. |

## Risks

1. **`@Scheduled` initial-delay race with storage connect.** Mitigated by Phase 1: every bean's `@PostConstruct` (including the bean carrying `@Scheduled`) sees connected operator. First `@Scheduled` only fires after `TaskScheduler` activates at end-of-context-refresh, after all `@PostConstruct`s.

2. **`scheduler.run(action)` vs `@Scheduled` thread pool.** `CommandScheduler` uses rapla's own executor; `@Scheduled` uses Spring's. If any path assumed shared pool / FIFO ordering between recurring and one-shot tasks, that's broken. Mitigation: review each migration for inter-task ordering; if any, move to `SmartLifecycle` instead.

3. **dhbwrapla cron timezone.** Legacy `scheduleAtGivenTime(action, h, m)` fired in JVM default zone. Spring's `@Scheduled(cron=...)` defaults to JVM default unless `zone="..."`. Set `zone = "Europe/Berlin"` explicitly to match DHBW.

4. **Spring `TaskScheduler` pool size.** Default 1. If two `@Scheduled` on different beans fire concurrently, second queues. For dhbw + notification + sync, that's fine. Bump via `spring.task.scheduling.pool.size` if heavy I/O scheduled.

## Open Questions

1. **Delete `ServerExtension` outright or `@Deprecated` for one release?** Delete — zero external consumers (server-internal plugin point), so deprecation buffers no one.
2. **dhbw cron cadence configurability.** Legacy 8:11/12:11/16:11 was hardcoded. Expose via `DhbwProperties` (e.g. `rapla.dhbw.dualis.cron`) so deployments can tune. Yes for production hygiene, but follow-up — initial migration uses hardcoded to match legacy.
3. **Does any consumer rely on `ServerExtension.start()` ordering?** If yes, move to `SmartLifecycle`'s `getPhase()`. Audit during Phase 3.

## Cross-references

| PRD | Relationship |
|---|---|
| **001** spring-boot-migration (done) | Establishes the `@SpringBootApplication` context this PRD leverages. |
| **003** custom-deployments-after-spring-migration | Currently recommends `ServerExtension`. **Amend** to point at PRD 019 once Phase 4 lands. |
| **012** dhbwrapla-client-migration | dhbw cron-cadence regression is the most concrete motivator. |
| **AGENTS.md §11** | "Never delete code to fix compile errors — unless removal is part of the plan." This PRD is that plan. |
