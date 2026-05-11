# PRD 019: Spring Boot Lifecycle Migration — Replacing `ServerExtension`

**Status:** done — Phases 1–4 landed 2026-05-10; moved to `done/` 2026-05-11. `ServerExtension` interface deleted; all 6 implementors migrated to `@Scheduled` (5 of them) + `@EventListener(ApplicationReadyEvent.class)` (`JavascriptPatcher`). `operator.connect()` moved into the `cachableStorageOperator` `@Bean` factory. `@DependsOn("serverServiceContainer")` removed from `RaplaKeyStorage`. dhbw cron cadence (3×daily) restored via `@Scheduled(cron = "0 11 8,12,16 * * *", zone = "Europe/Berlin")` for Dualis (and 8:21/12:21/16:21 for Morada). 8/8 baseline tests still pass; both repos compile green.
**Date:** 2026-05-10 (closed: 2026-05-11)

## Goal

Move rapla off its bespoke `ServerExtension` start()/stop() lifecycle (driven by
`ServerServiceImpl` iterating a `Map<String, ServerExtension>`) to Spring Boot's
native bean lifecycle:

- `@Scheduled` for recurring tasks (replaces the removed
  `CommandScheduler.scheduleAtGivenTime`)
- `@EventListener(ApplicationReadyEvent.class)` for one-shot startup tasks that
  need the storage to be loaded
- `@PostConstruct` / `@PreDestroy` for per-bean init/teardown
- `SmartLifecycle` only for the rare cases that genuinely need symmetric
  phased start/stop across many beans

## Why this is needed now

1. **The cron API was removed without a replacement.**
   `CommandScheduler.scheduleAtGivenTime(action, hour, minute)` is gone (last
   appeared up to commit `5098019e`, removed in the `4f50a558 big ai assisted
   refactoring` window). The two known callers in dhbwrapla
   (`DualisSyncJobStarter`, `MoradaSyncJobStarter`) used it for a 3×daily
   cadence (8:11, 12:11, 16:11). The 2026-05-10 dhbw migration session
   collapsed both to once-daily as a temporary workaround. PRD 019 restores
   the original cadence on the Spring-native pathway.

2. **Storage-up ordering currently leaks an implementation detail.**
   `ServerServiceImpl`'s constructor calls `operator.connect()` *before*
   iterating `ServerExtension.start()`. That guarantee is what
   `ServerExtension`s depend on. The same guarantee is exposed today as
   `@DependsOn("serverServiceContainer")` (used by `RaplaKeyStorage`,
   `ServerServiceConfig.java:72`) — i.e. consumers that need the storage
   connected hard-code the bean name of an unrelated infra class.

   In a clean Spring Boot world, `operator.connect()` belongs in a
   `@PostConstruct` on the operator bean itself. Then Spring's bean
   dependency graph implicitly guarantees that any bean injecting
   `RaplaFacade`/`CachableStorageOperator` gets a connected one — no
   `@DependsOn` needed.

3. **`ServerExtension` is a relic of pre-Spring rapla.**
   6 implementors today, 5 of which use it for scheduling
   (`NotificationService`, `SynchronisationManager`, `ArchiverServiceTask`,
   plus the two dhbw sync starters). 1 (`JavascriptPatcher`) uses it for
   one-shot init/teardown. Spring Boot's `@Scheduled` + `@EventListener` cover
   both shapes idiomatically.

## Scope

### In scope

- `org.rapla.scheduler.CommandScheduler` interface — keeps
  `delay`/`schedule`/`run`/`supply`/`scheduleSynchronized`. No
  `scheduleAtGivenTime` resurrection.
- `org.rapla.server.extensionpoints.ServerExtension` — deprecate, then delete
  once all consumers move off.
- 6 `ServerExtension` implementors — each migrated per case (see Plan §3).
- `org.rapla.server.internal.ServerServiceImpl` — remove the
  `Map<String, ServerExtension>` iteration and the `operator.connect()` call
  from its constructor. Storage connection moves to `@PostConstruct` on the
  operator bean.
- `RaplaServerAutoConfiguration` — add `@EnableScheduling`.
- `@DependsOn("serverServiceContainer")` callsites — drop after Phase 1.

### Out of scope

- Rapla's `Promise<T>` / `CommandScheduler` async API — stays as is. This PRD
  only covers the *lifecycle* (start-up scheduling registration), not the
  scheduler internals.
- Migrating in-Reservation appointment scheduling (`Repeating`, etc.) — that's
  the data model, not lifecycle.
- Replacing rapla's `Action` interface with `Runnable` — possible follow-up
  but separate concern.

## Plan

### Phase 1 — Move `operator.connect()` to `@PostConstruct` (rapla-server)

1. Add `@PostConstruct void connect()` (or rename existing) to
   `LocalAbstractCachableOperator` or its concrete subclasses
   (`FileOperator`, `DBOperator`). Since these aren't Spring beans directly
   (they're built by `ServerStorageSelector.get()`), wire it via
   `ServerStorageSelector` calling `connect()` before returning. **Or**
   convert the operator into a proper `@Bean` via `ServerCoreConfig` and let
   Spring fire `@PostConstruct`.
2. Remove `operator.connect()` from `ServerServiceImpl`'s constructor.
3. Verify `RaplaSpringBootApplicationTest` still passes (the existing
   `@SpringBootTest` exercises a connected facade).
4. Drop `@DependsOn("serverServiceContainer")` on `RaplaKeyStorage`
   (`ServerServiceConfig.java:72`). Verify the key storage test still passes.

### Phase 2 — `@EnableScheduling` (rapla-server)

1. Add `@EnableScheduling` to `RaplaServerAutoConfiguration`. Every deployment
   that pulls in rapla-server (rapla-app, dhbwrapla, future deployments) gets
   Spring's `TaskScheduler` registered automatically.
2. Optional: configure pool size via
   `spring.task.scheduling.pool.size=N` in `application.yml`. Default is 1.

### Phase 3 — Migrate the 6 `ServerExtension` impls

| Impl | Where | Replacement |
|---|---|---|
| `NotificationService` | rapla-server / `plugin.notification` | Two `@Scheduled` methods (`sentUpdateMails` at `fixedRate=30000`, `retryMails` at `initialDelay=45000, fixedRate=...`). Cancellation handled by Spring on shutdown. |
| `SynchronisationManager` | rapla-server / `plugin.exchangeconnector` | Same — two `@Scheduled` methods with the existing periods (`SCHEDULE_PERIOD`, `SCHEDULE_PERIOD_REFRESH_MAILBOXES`). |
| `ArchiverServiceTask` | rapla-server / `plugin.archiver` | `@Scheduled` with the archive task's existing cadence. |
| `JavascriptPatcher` | rapla-server / `plugin.javasciptpatch` | `@EventListener(ApplicationReadyEvent.class)` — runs once at startup, no recurring. Pair with `@PreDestroy` if it has teardown work. |
| `DualisSyncJobStarter` | dhbwrapla / `dhbw.sync.dualis.server` | `@Scheduled(cron = "0 11 8,12,16 * * *", zone = "Europe/Berlin")` on a method that calls `dualisImportJob.run()`. **Restores the lost 3×daily cadence.** |
| `MoradaSyncJobStarter` | dhbwrapla / `dhbw.sync.morada.server` | Same with Morada's 8:21/12:21/16:21 schedule. |

For each migration:
- Drop `implements ServerExtension`.
- Drop the `start()`/`stop()` methods.
- Drop the `List<Cancellation> schedules` field.
- Move the scheduling logic to `@Scheduled` annotations on cleanly-named
  methods (e.g. `runHourly()`, `runDaily()`).

### Phase 4 — Delete `ServerExtension`

After all 6 impls are off it:

1. Delete the iteration in `ServerServiceImpl` constructor (lines 175–182).
2. Delete the iteration in `ServerServiceImpl.stop()` (lines 236–...).
3. Drop the `Map<String, ServerExtension>` field + constructor arg.
4. Drop the `Supplier<Map<String, ServerExtension>>` arg from
   `ServerServiceConfig.serverServiceContainer(...)`.
5. Delete `org.rapla.server.extensionpoints.ServerExtension`.
6. Update PRD 003 §"Scheduled Background Jobs" — remove Option 1
   (`@Component implements ServerExtension`); make `@Scheduled` the
   canonical answer.
7. Update PRD 003 §"Extension Point Preservation Checklist" — remove
   `ServerExtension` from the list.

## Storage-up ordering invariant (post-Phase 1)

Documented for downstream deployments:

> After Phase 1, any Spring bean that injects `RaplaFacade` or
> `CachableStorageOperator` is guaranteed to receive a **connected** operator
> before its own `@PostConstruct` runs. `@Scheduled` tasks fire after context
> refresh, which is after every `@PostConstruct`. So storage is always
> available in `@Scheduled` methods, in `@EventListener(ApplicationReadyEvent.class)`,
> and in any `@PostConstruct` that depends on the facade — no `@DependsOn`
> required.

This is the contract custom deployments (dhbwrapla, future deployments)
depend on for their own startup tasks.

## Tests

| Phase | Test |
|---|---|
| 1 | `OperatorConnectInPostConstructTest` — verify `operator.isConnected()` is true at the time a downstream bean's `@PostConstruct` runs. |
| 1 | `RaplaSpringBootApplicationTest` — must still boot green. |
| 2 | `SchedulingEnabledTest` — verify a `@Scheduled` test bean's method runs at least once during a `@SpringBootTest`. |
| 3 | Per-impl: a smoke test confirming the recurring cadence still triggers (e.g. `MoradaSyncSchedulingTest` with a 100ms `fixedRate` test override). |
| 4 | Compile-only check: `grep -r ServerExtension` returns zero hits across both repos. |

## Risks

1. **`@Scheduled` initial-delay race with storage connect.** Mitigated by
   Phase 1: with `operator.connect()` in `@PostConstruct`, every bean's
   `@PostConstruct` (including the bean carrying `@Scheduled` annotations)
   sees a connected operator. The first `@Scheduled` invocation only happens
   after Spring's `TaskScheduler` activates at end-of-context-refresh, which
   is after all `@PostConstruct`s. So storage is reliably ready.

2. **`scheduler.run(action)` vs `@Scheduled` thread pool.** `CommandScheduler`
   uses rapla's own executor; `@Scheduled` uses Spring's `TaskScheduler`. If
   any code paths were assuming "recurring task and one-shot supplied task
   share a thread pool / FIFO ordering" that's now broken. Mitigation: review
   each migration for inter-task ordering assumptions; if any, move to
   `SmartLifecycle` rather than `@Scheduled`.

3. **dhbwrapla cron timezone.** The legacy `scheduleAtGivenTime(action, h, m)`
   fired in the JVM default time zone. Spring's `@Scheduled(cron=...)` defaults
   to the JVM default zone unless `zone="..."` is specified. To preserve
   behaviour we set `zone = "Europe/Berlin"` explicitly — matching DHBW's
   operating zone. Worth verifying with deployment.

4. **Spring `TaskScheduler` pool size.** Default is 1. If two `@Scheduled`
   methods on different beans want to fire concurrently, the second one
   queues. For dhbw + notification + sync, that's fine (none are
   long-running). If we ever schedule heavy I/O work, bump the pool via
   `spring.task.scheduling.pool.size`.

## Open Questions

1. **Should we delete `ServerExtension` entirely or `@Deprecated` it for one
   release?** Recommendation: delete after Phase 4 — there are zero external
   consumers (it's a server-internal plugin point), so deprecation provides
   no migration buffer to anyone.
2. **dhbw cron cadence configurability.** The legacy 8:11/12:11/16:11 schedule
   was hardcoded. Should we expose it via `DhbwProperties` (e.g.
   `rapla.dhbw.dualis.cron`) so deployments can tune without recompile?
   Recommendation: yes for production hygiene, but it's a follow-up — the
   initial migration uses the hardcoded cadence to match legacy behaviour.
3. **Does any consumer rely on `ServerExtension.start()` ordering (the Map
   iteration order in `ServerServiceImpl`)?** If yes, those move to
   `SmartLifecycle`'s `getPhase()` for explicit ordering. Audit during Phase 3.

## Cross-references

| PRD | Relationship |
|---|---|
| **001** spring-boot-migration (done) | Establishes the `@SpringBootApplication` context that this PRD finishes leveraging. |
| **003** custom-deployments-after-spring-migration | Currently has §"Scheduled Background Jobs" recommending `ServerExtension`. **Amend** to point at PRD 019 once Phase 4 lands. |
| **012** dhbwrapla-client-migration | The dhbw cron-cadence regression noted in the 2026-05-10 implementation snapshot is the most concrete example of why this PRD is needed. |
| **AGENTS.md §11** | "Never delete code to fix compile errors — unless removal is part of the plan." This PRD is the plan that authorises the `ServerExtension` deletion in Phase 4. |
