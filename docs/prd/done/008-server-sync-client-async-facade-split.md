# PRD 008: Confine rxjava to rapla-client

**Status:** done (2026-05-07) — Phases 0–4 shipped.
**Date:** 2026-05-07

## Outcome (verified end state)

- `mvn -pl rapla-core dependency:tree | grep rxjava` → empty ✓
- `mvn -pl rapla-server dependency:tree | grep rxjava` → empty ✓
- `mvn -pl rapla-client dependency:tree | grep rxjava` → `io.reactivex.rxjava3:rxjava:3.1.5` ✓
- `grep -rE "import io\.reactivex" rapla-core/src/main rapla-server/src/main` → empty ✓
- `grep -rE "Throwables\.uncheck|new CompletionException\(" rapla-core/src/main` → empty (only inside `SynchronizedPromise.java` impl-internal bridging, the contained pattern the PRD allows) ✓
- `mvn clean compile` BUILD SUCCESS, `mvn test` 23 test classes green ✓

### What landed by phase

| Phase | Outcome |
|---|---|
| **0** Cleanup of in-flight pollution | Custom function-type interfaces (`Function`, `Consumer`, `Action`, `BiFunction`, `BiConsumer`) + `Cancellation` in `org.rapla.scheduler`. `Promise.java` and impls updated. `Throwables.java` deleted. Removed every `Throwables.uncheck(...)` and inline `try/catch + CompletionException` from user-code lambdas in `FacadeImpl`, `RemoteOperator`, `AbstractCachableOperator`. |
| **1** De-rxjava-ify `CommandScheduler` | Removed rxjava `Disposable`/`Action` imports. Replaced `Disposable` returns with new `Cancellation` interface. Rewrote `delay(...)` and `schedule(...)` to use `ScheduledExecutorService` directly. `UtilConcurrentCommandScheduler` rxjava-free. Bulk-swapped 32 files' rxjava `functions.*` imports to `org.rapla.scheduler.*`. Replaced `Disposable` field types in 5 services with `Cancellation`; `.dispose()` → `.cancel()`. |
| **2** Move `Observable`/`Subject` to client | Removed `just(T)`, `toObservable(Promise<T>)`, `createPublisher()` from `CommandScheduler` interface. Added `Executor getExecutor()`. `git mv`d `Observable.java`, `Subject.java`, `JavaObservable.java`, `JavaSubject.java` from `rapla-core` to `rapla-client`. Created `org.rapla.scheduler.Observables` in client with static factories. Migrated 11 client + 2 plugin callers. Moved `ObservableTest` to client/test. Stripped Observable refs from `DefaultScheduler` (deleted unused `scheduleAtGivenTime` methods). |
| **3** Add `SyncStorageOperator` | New `org.rapla.storage.SyncStorageOperator` in core declaring `getConflictsSync(User)` and `getConflictsSync(Reservation)`. `LocalAbstractCachableOperator` implements directly (no async path). Async siblings now delegate to sync: `getConflicts(...) = scheduler.supply(() -> getConflictsSync(...))`. `SecurityManager` takes a `SyncStorageOperator` parameter; the two `waitFor(...)` blocking sites in `checkPermissions` are gone. `ServerServiceConfig.securityManager` updated. |
| **4** Move rxjava Maven dep to client only | Removed `rxjava` and `reactive-streams` from `rapla-core/pom.xml`. Added explicit `rxjava` to `rapla-client/pom.xml`. Version pin in `rapla-bom`'s `<dependencyManagement>` unchanged. |
| **5** Replace remaining server-side async storage calls | Extended `SyncStorageOperator` with `queryAppointmentsSync`, `queryAppointmentsByLocalDateTimeSync`, `getFirstAllocatableBindingsSync`, `getAllAllocatableBindingsSync`. **Sync methods are source of truth; async siblings wrap sync**, never the other way. Refactored `RemoteStorageImpl` to inject `SyncStorageOperator`: 4 call sites chaining `.waitFor` / `.thenApply` → plain sync calls wrapped only in `ResolvedPromise.of(...)` for the `Promise<>` return shape. `RaplaEventsRestPage` migrated via `queryAppointmentsByLocalDateTimeSync`. |
| **6** Sync siblings on server-internal services | Same pattern applied to three services where Promise was pure ceremony: `RemoteLocaleServiceImpl` (`localeSync`, `countriesSync`); `ArchiverServiceImpl` (`backupNowSync`, `restoreSync`, `deleteSync`); `RaplaJNDITestOnLocalhost` (`testSync`). Controllers migrated to inject impl class and call sync, removing 7 `.waitFor` sites. Service interfaces keep Promise (shared with Swing client over `@HttpExchange`). |
| **7** Plugin-internal storage waits | Added `doMergeSync` to `SyncStorageOperator`. Migrated `RemoteStorageImpl.doMerge` (was `operator.doMerge(...).thenCompose(_ -> refresh(time))`, now direct `doMergeSync(...)` + `refreshSync(time)`); `SynchronisationManager.queryAppointments` (cast to `SyncStorageOperator`, call `queryAppointmentsSync` directly). |
| **8** RaplaFacade-async + RemoteStorageController | Added `getReservationsSync(...)` default on `SyncStorageOperator` mirroring `RaplaFacade.getReservationsAsync` — gives any server caller a sync entry without changing `RaplaFacade`. Migrated 3 active facade-async sites: `ArchiverServiceImpl.delete(...)`, `RaplaICalImport.importCalendar` (`Promise<Integer[]>` → `Integer[]`). `RaplaJNLPPageGenerator` site was inside a comment block (dead code). Added `getNextAllocatableDateSync`. Added 7 sync methods to `RemoteStorageImpl`. Rewrote `RemoteStorageController` to call sync straight through — **deleted the `await()` Promise-blocking helper entirely**; 12 `.waitFor` sites collapsed. |
| **9** `SyncCalendarModel` sibling | New `org.rapla.facade.SyncCalendarModel` in core: `queryReservationsSync`, `queryAppointmentsSync`, `queryAppointmentBindingsSync`, `queryBlocksSync`. `CalendarModelImpl` now `implements CalendarSelectionModel, SyncCalendarModel`; four async query methods became thin `scheduler.supply(() -> querySync(...))` wrappers, sync paths doing real work via a `requireSyncOperator()` cast. Cast throws `UnsupportedOperationException` if the underlying operator isn't an in-process `SyncStorageOperator` (i.e. on the client where `RemoteOperator` is in use), so client callers transparently keep using async. Extended `SyncStorageOperator` with `getFromIdSync` and a `templateId` variant of `queryAppointmentsSync`. Migrated 5 server `.waitFor` sites: `Export2iCalServlet` (2), `AppointmentTableViewPage`, `AppointmentPerDayViewPage`, `ReservationTableViewPage`. Added `RaplaBuilder.initFromModelSync(...)`; migrated `AbstractHTMLCalendarPage.createBuilder`. After Phase 9, server `.waitFor` sites are **zero** outside REST parser infrastructure (`JacksonParserWrapper`, `GsonParserWrapper`). |

### Server-side async-facade audit (final state)

Phase 9 cleared the `CalendarModel`-driven sites. After Phase 9 the only `SynchronizedCompletablePromise.waitFor` references in `rapla-core` + `rapla-server` are **REST parser infrastructure** (`JacksonParserWrapper`, `GsonParserWrapper`) — intentional sync entry points at the I/O boundary, not part of any "remove async ceremony" effort. `ArchiverServiceImpl` and `RaplaICalImport` chains were migrated in Phase 8 (the only remaining facade-async chain in earlier audit notes was a commented-out `RaplaJNLPPageGenerator:236`, dead code).

## Out of scope (deferred to a future PRD)

- **Sync `RaplaFacade` sibling.** The 10–15 server `.waitFor` sites that wait on facade-level methods (`raplaFacade.getReservationsAsync(...)`, `model.queryReservations(...)`) — distinct from storage-side waits — keep `.waitFor`. A future PRD can introduce `RaplaServerFacade` sync sibling for the specific methods these need.
- **More `SyncStorageOperator` methods.** Only the methods needed to remove `SecurityManager`'s waits are exposed today. Add more as sync call sites need them.
- **Promise → CompletionStage rename.** `Promise<T>` stays. Removing it creates a separate exception-cascade decision (custom checked-allowing function types vs JDK vs make `RaplaException` unchecked); none needed to confine rxjava.

## Goal

**Primary:** rxjava only lives in `rapla-client`. `rapla-core` and `rapla-server` have no rxjava on the classpath.

**Secondary** (via `SyncStorageOperator`): server stops blocking on `SynchronizedCompletablePromise.waitFor(...)` for storage calls. The 5–8 `.waitFor` sites in REST controllers / servlets / pages become direct synchronous calls.

### Verifiable end state

```
$ mvn -pl rapla-core   dependency:tree | grep rxjava   # empty
$ mvn -pl rapla-server dependency:tree | grep rxjava   # empty
$ mvn -pl rapla-client dependency:tree | grep rxjava   # io.reactivex.rxjava3:rxjava:3.1.5

$ grep -rE "import io\.reactivex" rapla-core/src/main rapla-server/src/main   # empty
$ grep -rE "Throwables\.uncheck|new CompletionException\(" rapla-core/src/main # empty (see Phase 0)
$ grep -rE "SynchronizedCompletablePromise\.waitFor" rapla-server/src/main    # only non-storage callers
```

`Promise<T>` stays. `RaplaFacade` (async) stays in core. `RemoteOperator` stays in core. Server-as-client option (Rapla server using `RemoteOperator` to call another Rapla server) preserved.

## Scope and end-state placement

| Module | Async-shaped types | rxjava |
|---|---|---|
| `rapla-core` | `Promise<T>` + impls (custom function types — Phase 1), `RaplaFacade` (async), `StorageOperator` (async) + new `SyncStorageOperator` sibling, `CommandScheduler` (`ScheduledExecutorService`), `RemoteOperator`, `FacadeImpl`, `ClientFacadeImpl` | **none** |
| `rapla-server` | impls `SyncStorageOperator` (only `LocalAbstractCachableOperator`) for in-process storage; can still call `RaplaFacade` async if it wants | **none** |
| `rapla-client` | `Observable<T>`, `Subject<T>` and rxjava-using impls (`JavaObservable`, `JavaSubject`), existing client-side rxjava use | yes |
| `rapla-app` | wires it all | (transitive only) |

Out of scope: sync `RaplaFacade` sibling, Promise → `CompletionStage` rename, migration of `FacadeImpl` to client.

## Plan

### Phase 0 — Clean up the in-flight pollution (must land first)

The working tree had an in-progress mix from earlier exploration: `Promise<T>` signatures used JDK `java.util.function.*`; `FacadeImpl`/`RemoteOperator`/`AbstractCachableOperator` had `Throwables.uncheck(...)` wrappers and inline `try/catch + CompletionException` blocks; `Throwables` helper in `rapla-core`; `RaplaFacade.java` used `java.util.function.Consumer`.

This pollutes the codebase and is what the rest of the PRD avoids. It happened because JDK function types in `Promise<T>` reject checked exceptions, and the cascade was patched at every call site rather than fixed at the source.

**Fix at the source:** replace JDK function types in `Promise<T>` (+ impls + `RaplaFacade`) with custom checked-allowing function types in `org.rapla.scheduler`. Same shape as rxjava's, but our own. Lambdas throw checked freely. No wrapping anywhere.

Steps:

1. **Add custom function-type interfaces** in `rapla-core/.../scheduler/`:
   ```java
   @FunctionalInterface public interface Function<T, R> { R apply(T t) throws Exception; }
   @FunctionalInterface public interface Consumer<T>    { void accept(T t) throws Exception; }
   @FunctionalInterface public interface Action         { void run() throws Exception; }
   @FunctionalInterface public interface BiFunction<T, U, R> { R apply(T t, U u) throws Exception; }
   @FunctionalInterface public interface BiConsumer<T, U>    { void accept(T t, U u) throws Exception; }
   ```
2. **Update `Promise.java`** to import `org.rapla.scheduler.{Function,Consumer,Action,BiFunction,BiConsumer}`.
3. **Update `UnsynchronizedPromise`, `SynchronizedPromise`, `SynchronizedCompletablePromise`** to use custom types in method signatures. Internal bridging to `CompletionStage` (which uses JDK types) is local — wrap the user's checked-throwing lambda once at the impl boundary.
4. **Update `RaplaFacade.java`** — change `java.util.function.Consumer` to `org.rapla.scheduler.Consumer`.
5. **Revert `FacadeImpl.java`** — remove `CompletionException` import; remove 10 inline `try/catch` blocks. Lambdas return to bare bodies propagating checked exceptions naturally.
6. **Revert `RemoteOperator.java`** — remove `Throwables.uncheck`/`uncheckC` static imports and unwrap every wrapping.
7. **Revert `AbstractCachableOperator.java`** — same cleanup.
8. **Delete `Throwables.java`**.
9. **Verify**: `mvn compile` BUILD SUCCESS. Grep for `Throwables\.uncheck|new CompletionException\(` in `rapla-core/src/main` empty.

After Phase 0, working tree is clean. **No phase below introduces new try/catch noise.**

### Phase 1 — De-rxjava-ify `CommandScheduler`

10. **Rewrite the interface**: drop `just(T)` and `toObservable(Promise<T>)`. Replace `Disposable` returns with `AutoCloseable` (or `Cancellation` if `AutoCloseable.close() throws Exception` is awkward).
11. **Rewrite default methods** (`delay`, `schedule`) to use `ScheduledExecutorService` directly.
12. **Rewrite `UtilConcurrentCommandScheduler`** to use `ScheduledExecutorService` internally. Drop rxjava processors.
13. **Update `DefaultScheduler`**.

### Phase 2 — Move `Observable<T>` / `Subject<T>` to `rapla-client`

14. **Audit server use**: `NotificationService` and `SynchronisationManager` use `Observable` for periodic orchestration. Rewrite to `@Scheduled(fixedRate=...)` + imperative code. `ArchiverServiceTask`'s `timer.schedule(...)` becomes `@Scheduled` too.
15. **`git mv`** `Observable.java`, `Subject.java`, `JavaObservable.java`, `JavaSubject.java` from `rapla-core` to `rapla-client/.../scheduler/`.
16. **Verify**: grep `import io\.reactivex|import org\.rapla\.scheduler\.Observable|Subject` in `rapla-core/src/main rapla-server/src/main` returns empty.

### Phase 3 — Add `SyncStorageOperator` (the secondary goal)

17. **Define `SyncStorageOperator`** in `rapla-core/.../storage/` as a sibling of `StorageOperator` (NOT a subtype). Same method names, sync return types, may `throws RaplaException`.
18. **Make `LocalAbstractCachableOperator` implement `SyncStorageOperator`**. Add direct sync methods that don't go through the async wrappers at all.
19. **Migrate the 5–8 `.waitFor` sites in server controllers/servlets/pages** — `Export2iCalServlet`, `RaplaEventsRestPage`, `AppointmentTableViewPage`, `ReservationTableViewPage`, `AppointmentPerDayViewPage`, `AbstractHTMLCalendarPage`, `RaplaICalImport`, `RemoteLocaleController`, `JNDIConfigController`, `ArchiverController`, `SecurityManager`. Each replaces `SynchronizedCompletablePromise.waitFor(...)` with `syncOperator.someSync(...)` directly. Sites waiting on `RaplaFacade.someAsync(...)` (not storage) keep `.waitFor` (deferred).

### Phase 4 — Move the rxjava Maven dep

20. Move `<dependency>io.reactivex.rxjava3:rxjava</dependency>` from `rapla-bom`'s default `<dependencies>` to `<dependencyManagement>` only. Add explicit `<dependency>` block in `rapla-client/pom.xml`.
21. **Verify**: three `dependency:tree` checks pass; `mvn install` BUILD SUCCESS; `mvn test` 94/94 pass.

## Server-side async-facade audit

Beyond storage-side `.waitFor` that Phase 3 fixes, **`RaplaFacade` (async) is also called from server code in nontrivial ways**. Out of scope but listed:

| Site | Call | Disposition |
|---|---|---|
| `ArchiverServiceImpl:132` | `raplaFacade.getReservationsAsync(...).thenAccept(...)` | Background archiver. Phase 1 makes the periodic schedule Spring-native; lambda body could remain async or imperative. Defer. |
| `RaplaJNLPPageGenerator:236` | `reservations.thenAccept(...)` for JNLP page | Servlet handler needs result before responding. Could go sync; deferred. |
| `RaplaResourcesRestPage`, `RaplaEventsRestPage`, `RemoteStorageImpl` | `.thenApply`/`.thenAccept` chains | Request handlers that ultimately need to block. Cleanest fix is dropped `RaplaServerFacade` sync sibling. |
| `SecurityManager:443,458` | `.waitFor(operator.getConflicts(...))` | Storage-side; **fixed by Phase 3**. |
| `NotificationStorage`, `JNDIServerPlugin`, `ExchangeAppointmentStorage`, `RaplaICalImport` | `.thenApply` chains | Mix of storage (Phase 3) and facade (deferred). |

**Total deferred async-facade sites:** ~10–15. Suggested follow-up PRD: introduce `RaplaServerFacade` sync sibling for the specific methods these need (a subset).

## Tests

Existing 94 tests are the verification. No new tests required.

| Phase | Verification |
|---|---|
| 0 | `mvn compile` BUILD SUCCESS. Grep `Throwables\.uncheck|new CompletionException\(` in `rapla-core/src/main` empty. `Throwables.java` deleted. |
| 1 | `mvn install` BUILD SUCCESS. `CommandScheduler` no longer imports rxjava. |
| 2 | All tests pass. `Observable`/`Subject` imports in `rapla-core`/`rapla-server` empty. |
| 3 | All tests pass. `.waitFor` in server only in deferred facade sites. |
| 4 | All tests pass. Three `dependency:tree` checks confirm rxjava placement. |

## Risks

1. **Custom function types collide with imports.** Files importing both `java.util.function.Function` and our `org.rapla.scheduler.Function` need fully-qualified refs. Mitigation: prefer custom for Promise code, JDK for stream/util code; resolve case-by-case.
2. **`AutoCloseable.close()` declares `throws Exception`.** If callers want no-throw cancellation, we may need our own `Cancellation` interface. Decide in Phase 1.
3. **`NotificationService` / `SynchronisationManager` rewrite may surface real periodic-orchestration bugs.** They orchestrate via rxjava Observable in ways that *might* depend on rx semantics (backpressure, scheduling). Rewrite each in its own commit, run targeted tests after each.
4. **`SyncStorageOperator` impl path.** Two options: (a) `LocalAbstractCachableOperator` adds direct sync methods alongside async; (b) sync methods call async and `.waitFor` internally. (a) is cleaner; (b) defeats the goal. **Use (a).**

## Open Questions

1. **`AutoCloseable` vs custom `Cancellation` for `CommandScheduler.delay()` / `.schedule()` returns?** AutoCloseable is JDK-standard but `close() throws Exception` is awkward for "cancel a scheduled task." Define a tiny `org.rapla.scheduler.Cancellation` (1 method, no throws).
2. **Phase 3 — direct sync methods vs unwrap-the-async?** Direct sync methods (option (a) in Risk #4).
3. **`Subject<T> extends Observable<T>, Subscriber<T>` drop `Subscriber` parent when moving to client?** `org.reactivestreams.Subscriber` is a transitive of rxjava. Keep — rxjava is in client now.
4. **`NotificationService` / `SynchronisationManager`'s rxjava use that's internal (not via Observable)?** If they use `Schedulers.io()` or rxjava-specific APIs beyond Observable, those need rewriting too. Survey in Phase 2 step 14.

## Dependencies on other PRDs

| PRD | Relationship |
|---|---|
| **004: Multi-Module Architecture Analysis** | Frames why client/server separation matters. |
| **005: Multi-Module Split** | Hard prerequisite. Done. |
| **007: Build & Test Performance** | Independent. |
| **(future) Server-side facade-sync** | This PRD's audit enumerates the ~10–15 deferred sites. A future PRD introduces `RaplaServerFacade` sync sibling. |
