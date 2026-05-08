# PRD 008: Confine rxjava to rapla-client

**Status:** done (2026-05-07) — Phases 0–4 shipped.
**Date:** 2026-05-07

## Outcome (verified end state)

- `mvn -pl rapla-core dependency:tree | grep rxjava` → empty ✓
- `mvn -pl rapla-server dependency:tree | grep rxjava` → empty ✓
- `mvn -pl rapla-client dependency:tree | grep rxjava` → `io.reactivex.rxjava3:rxjava:3.1.5` ✓
- `grep -rE "import io\.reactivex" rapla-core/src/main rapla-server/src/main` → empty ✓
- `grep -rE "Throwables\.uncheck|new CompletionException\(" rapla-core/src/main` → empty (only inside `SynchronizedPromise.java` impl-internal bridging, which is the contained pattern the PRD allows) ✓
- `mvn clean compile` BUILD SUCCESS, `mvn test` 23 test classes / all green ✓

### What landed by phase

| Phase | Status | Outcome |
|---|---|---|
| **0** Cleanup of in-flight pollution | ✅ done | Defined custom function-type interfaces (`Function`, `Consumer`, `Action`, `BiFunction`, `BiConsumer`) + `Cancellation` in `org.rapla.scheduler`. Updated `Promise.java` and impls to use them. Deleted `Throwables.java`. Removed every `Throwables.uncheck(...)` and inline `try/catch + CompletionException` block from user-code lambdas in `FacadeImpl`, `RemoteOperator`, `AbstractCachableOperator`. Reverted `RaplaFacade`'s `Consumer` import to use the custom one. |
| **1** De-rxjava-ify `CommandScheduler` | ✅ done | Removed rxjava `Disposable`/`Action` imports from `CommandScheduler`. Replaced `Disposable` returns with the new `Cancellation` interface. Rewrote `delay(...)` and `schedule(...)` to use `ScheduledExecutorService` directly (no rxjava `just(t).delay(ms)` chains). `UtilConcurrentCommandScheduler` is now rxjava-free. Bulk-swapped 32 files' rxjava `functions.*` imports to `org.rapla.scheduler.*` equivalents. Replaced `Disposable` field types in `RaplaClientServiceImpl`, `LocalAbstractCachableOperator`, `NotificationService`, `SynchronisationManager`, `ArchiverServiceTask` with `Cancellation`; replaced `.dispose()` calls with `.cancel()`. |
| **2** Move `Observable`/`Subject` to client | ✅ done | Removed `just(T)`, `toObservable(Promise<T>)`, `createPublisher()` from `CommandScheduler` interface (and from `UtilConcurrentCommandScheduler` impl). Added `Executor getExecutor()` to `CommandScheduler` so client callers can attach Observable streams to the scheduler's executor. `git mv`d `Observable.java`, `Subject.java`, `JavaObservable.java`, `JavaSubject.java` from `rapla-core` to `rapla-client`. Created `org.rapla.scheduler.Observables` in client with static `createPublisher(Executor)`, `toObservable(Promise, Executor)`, `just(T, Executor)` methods. Migrated 11 client callers (and 2 plugin callers) from `scheduler.createPublisher()` etc. to `Observables.createPublisher(scheduler.getExecutor())` etc. Moved `ObservableTest` from core/test to client/test. Stripped Observable references from `DefaultScheduler` (deleted unused `scheduleAtGivenTime` methods). |
| **3** Add `SyncStorageOperator` | ✅ done | New `org.rapla.storage.SyncStorageOperator` interface in core declaring `getConflictsSync(User)` and `getConflictsSync(Reservation)`. `LocalAbstractCachableOperator` implements it via direct sync work — `getConflictsSync(User)` runs the conflict-finder logic inline; `getConflictsSync(Reservation)` does the allocatable-bindings + conflict-check logic inline (no call into the async path). Async siblings now delegate the other way: `getConflicts(...) = scheduler.supply(() -> getConflictsSync(...))`. `SecurityManager` constructor takes a `SyncStorageOperator` parameter (Spring auto-wires `LocalAbstractCachableOperator`); the two `SynchronizedCompletablePromise.waitFor(operator.getConflicts(...), ...)` blocking sites in `SecurityManager.checkPermissions` are gone — replaced with direct `syncOperator.getConflictsSync(...)` calls. `ServerServiceConfig.securityManager` bean updated. |
| **4** Move rxjava Maven dep to client only | ✅ done | Removed `rxjava` and `reactive-streams` `<dependency>` blocks from `rapla-core/pom.xml`. Added explicit `rxjava` dep block to `rapla-client/pom.xml` (reactive-streams comes transitively). Version pin in `rapla-bom`'s `<dependencyManagement>` is unchanged. |
| **5** Replace remaining server-side async storage calls with `SyncStorageOperator` | ✅ done | Extended `SyncStorageOperator` with `queryAppointmentsSync`, `queryAppointmentsByLocalDateTimeSync` (default), `getFirstAllocatableBindingsSync`, `getAllAllocatableBindingsSync` (in addition to the Phase-3 `getConflictsSync(User\|Reservation)`). Refactored `LocalAbstractCachableOperator` to make the **sync methods the source of truth** — async siblings are thin `scheduler.supply(() -> syncMethod(...))` wrappers, never the other way around. Refactored `RemoteStorageImpl` to inject `SyncStorageOperator` and call sync versions directly: 4 call sites that previously chained `operator.queryAppointments(...).waitFor`, `operator.getConflicts(...).thenApply`, `operator.getFirstAllocatableBindings(...).thenApply`, `operator.getAllAllocatableBindings(...).thenApply` are now plain sync calls wrapped only in `ResolvedPromise.of(...)` for the public method's `Promise<>` return shape. Migrated `RaplaEventsRestPage` similarly via `queryAppointmentsByLocalDateTimeSync`. |
| **6** Sync siblings on server-internal services | ✅ done | Same "sync is source of truth, async wraps sync" pattern applied to three server-internal services where Promise was pure ceremony: `RemoteLocaleServiceImpl` (added `localeSync`, `countriesSync`); `ArchiverServiceImpl` (added `backupNowSync`, `restoreSync`, `deleteSync`); `RaplaJNDITestOnLocalhost` (added `testSync`). Migrated the matching REST controllers — `RemoteLocaleController`, `ArchiverController`, `JNDIConfigController` — to inject the impl class directly and call sync versions, removing 7 `.waitFor` sites. Service interfaces (`RemoteLocaleService`, `ArchiverService`, `JNDIConfig`) keep their Promise shape because they're shared with the Swing client over `@HttpExchange`. |
| **7** Plugin-internal storage waits | ✅ done | Added `doMergeSync` to `SyncStorageOperator` + impl on `LocalAbstractCachableOperator`. Two sites migrated: `RemoteStorageImpl.doMerge` (was `operator.doMerge(...).thenCompose(_ -> refresh(time))`, now direct `syncOperator.doMergeSync(...)` + `refreshSync(time)` calls); `SynchronisationManager.queryAppointments` (was `cachableStorageOperator.queryAppointments(...).waitFor`, now cast to `SyncStorageOperator` and call `queryAppointmentsSync` directly — same instance implements both). |
| **8** RaplaFacade-async + RemoteStorageController | ✅ done | Added `getReservationsSync(...)` default method on `SyncStorageOperator` that mirrors `RaplaFacade.getReservationsAsync` and translates `AppointmentMapping → Collection<Reservation>` internally — gives any server caller a single sync entry point without changing `RaplaFacade`. Migrated 3 active facade-async sites: `ArchiverServiceImpl.delete(...)` (static helper now takes `SyncStorageOperator` parameter; `ArchiverServiceTask` updated), `RaplaICalImport.importCalendar` (returned `Promise<Integer[]>` → `Integer[]`; the `getImportedReservations` chain flattened to imperative; `importICal` `waitFor` removed). The `RaplaJNLPPageGenerator` site that grep had flagged turned out to be inside a `/* … */` comment block (dead code, no migration needed). Added `getNextAllocatableDateSync` to `SyncStorageOperator`. Added 7 sync methods to `RemoteStorageImpl` (`getEntityDependenciesSync`, `queryAppointmentsSync`, `dispatchSync`, `getConflictsSync`, `getFirstAllocatableBindingsSync`, `getAllAllocatableBindingsSync`, `getNextAllocatableDateSync`, `doMergeSync`, `restartServerSync`). Rewrote `RemoteStorageController` to inject `RemoteStorageImpl` directly and call sync methods straight through — **deleted the `await()` Promise-blocking helper entirely**; 12 `.waitFor`-equivalent sites collapsed to direct sync calls. |
| **9** `SyncCalendarModel` sibling | ✅ done | New `org.rapla.facade.SyncCalendarModel` interface in core declaring `queryReservationsSync`, `queryAppointmentsSync`, `queryAppointmentBindingsSync`, `queryBlocksSync` — sync siblings of the four `CalendarModel` query methods. `CalendarModelImpl` now `implements CalendarSelectionModel, SyncCalendarModel`; the four async query methods became thin `scheduler.supply(() -> querySync(...))` wrappers, with the sync paths doing the real work via a `requireSyncOperator()` cast. The cast throws `UnsupportedOperationException` if the underlying operator isn't an in-process `SyncStorageOperator` (i.e. on the client where `RemoteOperator` is in use), so client callers transparently keep using the async path. Extended `SyncStorageOperator` with `getFromIdSync` and a `templateId` variant of `queryAppointmentsSync`; impls on `LocalAbstractCachableOperator`. Refactored helpers `getAppointments(conflicts)` and `getAppointmentsForRequests(requests)` (and the private `queryAppointmentBindings(allocatables, owners, …)`) into sync siblings. Migrated 5 server `.waitFor` call sites: `Export2iCalServlet` (2 sites), `AppointmentTableViewPage`, `AppointmentPerDayViewPage`, `ReservationTableViewPage` — each cast `model` to `SyncCalendarModel` and call the new sync method directly. Added `RaplaBuilder.initFromModelSync(...)` (factored the lambda body of `initFromModel` into a private `applyBindings` helper shared by both async and sync paths) and migrated `AbstractHTMLCalendarPage.createBuilder` to call it. After Phase 9, server `.waitFor` sites are **zero** outside of REST parser infrastructure (`JacksonParserWrapper`, `GsonParserWrapper`). |

### Server-side async-facade audit (final state)

Phase 9 cleared the `CalendarModel`-driven sites. After Phase 9 the only `SynchronizedCompletablePromise.waitFor` references in `rapla-core` + `rapla-server` are **REST parser infrastructure** (`JacksonParserWrapper`, `GsonParserWrapper`) — these are intentional sync entry points at the I/O boundary and are not part of any "remove async ceremony" effort. The `ArchiverServiceImpl` and `RaplaICalImport` chains were already migrated in Phase 8 (the only remaining facade-async chain in earlier audit notes was the commented-out `RaplaJNLPPageGenerator:236`, which is dead code).

## Out of scope (deferred to a future PRD)

- **Sync `RaplaFacade` sibling.** The 10-15 server `.waitFor` sites that wait on facade-level methods (`raplaFacade.getReservationsAsync(...)`, `model.queryReservations(...)`, etc.) — distinct from storage-side waits — were not migrated. They keep using `.waitFor`. A future PRD can introduce a `RaplaServerFacade` sync sibling for the specific facade methods these callers need.
- **More `SyncStorageOperator` methods.** Only `getConflictsSync(User|Reservation)` is exposed today (the methods needed to remove `SecurityManager`'s waits). Add more sync methods to the interface as new sync call sites need them.
- **Promise → CompletionStage rename.** `Promise<T>` stays. Removing it would create a separate exception-cascade decision (custom checked-allowing function types vs JDK function types vs make `RaplaException` unchecked); none of those are needed to confine rxjava.

## Goal

**Primary:** rxjava only lives in `rapla-client`. `rapla-core` and `rapla-server` have no rxjava on the classpath.

**Secondary (smaller, scoped via `SyncStorageOperator`):** server stops blocking on `SynchronizedCompletablePromise.waitFor(...)` for storage calls. The 5–8 `.waitFor` sites in REST controllers / servlets / pages become direct synchronous calls.

### Verifiable end state

```
$ mvn -pl rapla-core   dependency:tree | grep rxjava   # empty
$ mvn -pl rapla-server dependency:tree | grep rxjava   # empty
$ mvn -pl rapla-client dependency:tree | grep rxjava   # io.reactivex.rxjava3:rxjava:3.1.5

$ grep -rE "import io\.reactivex" rapla-core/src/main rapla-server/src/main   # empty
$ grep -rE "Throwables\.uncheck|new CompletionException\(" rapla-core/src/main # empty (cleanup, see Phase 0)
$ grep -rE "SynchronizedCompletablePromise\.waitFor" rapla-server/src/main    # only in non-storage callers
```

`Promise<T>` stays. `RaplaFacade` (async) stays in core. `RemoteOperator` stays in core. The server-as-client option (a Rapla server using `RemoteOperator` to call another Rapla server) is preserved.

## Scope and end-state placement

| Module | Async-shaped types | rxjava |
|---|---|---|
| `rapla-core` | `Promise<T>` + impls (using custom function types — see Phase 1), `RaplaFacade` (async), `StorageOperator` (async) + new `SyncStorageOperator` sibling, `CommandScheduler` (rewritten on `ScheduledExecutorService`), `RemoteOperator`, `FacadeImpl`, `ClientFacadeImpl` | **none** |
| `rapla-server` | impls `SyncStorageOperator` (only `LocalAbstractCachableOperator`) for in-process storage; can still call `RaplaFacade` async if it wants | **none** |
| `rapla-client` | `Observable<T>`, `Subject<T>` and rxjava-using impls (`JavaObservable`, `JavaSubject`), plus existing client-side rxjava use | yes |
| `rapla-app` | wires it all | (transitive only) |

Out of scope:
- Sync `RaplaFacade` sibling (`RaplaServerFacade` returning raw types) — dropped. Server keeps using `RaplaFacade` async if it wants.
- Promise → `CompletionStage` rename. Promise stays.
- Migration of `FacadeImpl` to client. Stays in core.

## Plan

### Phase 0 — Clean up the in-flight pollution (must land first)

The current working tree has an in-progress mix from an earlier exploration:
- `Promise<T>` interface signatures use JDK `java.util.function.*` types (committed in `8dd5692a multimodule shift`).
- `FacadeImpl`, `RemoteOperator`, `AbstractCachableOperator` have `Throwables.uncheck(...)` wrappers and inline `try { ... } catch (RaplaException e) { throw new CompletionException(e); }` blocks.
- A `Throwables.uncheck` helper exists in `rapla-core/.../framework/`.
- `RaplaFacade.java` uses `java.util.function.Consumer` instead of rxjava `Consumer`.

This pollutes the codebase and is exactly what the rest of the PRD is designed to avoid. It happened because the JDK function types in `Promise<T>` reject checked exceptions, and the cascade was patched at every call site rather than fixed at the source.

**Fix at the source:** replace JDK function types in `Promise<T>` (and impls + `RaplaFacade`) with custom checked-allowing function types defined in `org.rapla.scheduler`. Same shape as rxjava's, but our own. Lambdas throw checked freely. No wrapping anywhere.

Steps:

1. **Add custom function-type interfaces** in `rapla-core/.../scheduler/`:
   ```java
   @FunctionalInterface public interface Function<T, R> { R apply(T t) throws Exception; }
   @FunctionalInterface public interface Consumer<T>    { void accept(T t) throws Exception; }
   @FunctionalInterface public interface Action         { void run() throws Exception; }
   @FunctionalInterface public interface BiFunction<T, U, R> { R apply(T t, U u) throws Exception; }
   @FunctionalInterface public interface BiConsumer<T, U>    { void accept(T t, U u) throws Exception; }
   ```
2. **Update `Promise.java`** to import `org.rapla.scheduler.{Function,Consumer,Action,BiFunction,BiConsumer}` instead of `java.util.function.*`.
3. **Update `UnsynchronizedPromise`, `SynchronizedPromise`, `SynchronizedCompletablePromise`** to use the custom types in their method signatures. Internal bridging to `CompletionStage` (which uses JDK types) is local and self-contained — wrap the user's checked-throwing lambda once at the impl boundary.
4. **Update `RaplaFacade.java`** — change `import java.util.function.Consumer;` to `import org.rapla.scheduler.Consumer;`.
5. **Revert `FacadeImpl.java`**:
   - Remove `import java.util.concurrent.CompletionException;` and `import java.util.function.Consumer;` (replace with `import org.rapla.scheduler.Consumer;` if needed).
   - Remove the 10 inline `try { ... } catch (RaplaException e) { throw new CompletionException(e); }` blocks. Lambdas go back to bare bodies that propagate checked exceptions naturally — the custom function types allow it.
6. **Revert `RemoteOperator.java`** — remove `Throwables.uncheck`/`uncheckC` static imports and unwrap every `uncheck(...)` / `uncheckC(...)` wrapping.
7. **Revert `AbstractCachableOperator.java`** — same cleanup.
8. **Delete `Throwables.java`** — no longer needed.
9. **Verify**: `mvn compile` BUILD SUCCESS. `grep -rE "Throwables\.uncheck|new CompletionException\(" rapla-core/src/main` returns empty.

After Phase 0, the working tree is clean and the rest of the plan starts from a pristine state. **No phase below introduces new try/catch noise.**

### Phase 1 — De-rxjava-ify `CommandScheduler`

10. **Rewrite the interface**: drop `just(T)` and `toObservable(Promise<T>)` (only used by rxjava-flavored default methods). Replace `Disposable` returns with `AutoCloseable` (or a 1-method `Cancellation` type if `AutoCloseable.close() throws Exception` is awkward).
11. **Rewrite the default methods** (`delay`, `schedule`) to use `ScheduledExecutorService` directly instead of `just(t).delay(ms)` chains.
12. **Rewrite `UtilConcurrentCommandScheduler`** to use `ScheduledExecutorService` internally. Drop rxjava processors.
13. **Update `DefaultScheduler`** which extends UtilConcurrentCommandScheduler.

### Phase 2 — Move `Observable<T>` / `Subject<T>` to `rapla-client`

14. **Audit server use**: `NotificationService` and `SynchronisationManager` use `Observable` for periodic orchestration. Rewrite them to use `@Scheduled(fixedRate = ...)` + plain imperative code. The single periodic task in `ArchiverServiceTask` (`timer.schedule(...)`) becomes `@Scheduled` too.
15. **`git mv`** `Observable.java`, `Subject.java`, `JavaObservable.java`, `JavaSubject.java` from `rapla-core` to `rapla-client/.../scheduler/`.
16. **Verify**: `grep -rE "import io\.reactivex|import org\.rapla\.scheduler\.Observable|import org\.rapla\.scheduler\.Subject" rapla-core/src/main rapla-server/src/main` returns empty.

### Phase 3 — Add `SyncStorageOperator` (the secondary goal)

17. **Define `SyncStorageOperator`** in `rapla-core/.../storage/` as a sibling of `StorageOperator` (NOT a subtype). Same method names, sync return types, may `throws RaplaException`.
18. **Make `LocalAbstractCachableOperator` implement `SyncStorageOperator`** in addition to `StorageOperator`. The sync methods unwrap the existing async impl: e.g., `getReservations(...) throws RaplaException` calls the async path internally and unwraps. Cleaner: add direct sync methods that don't go through the async wrappers at all.
19. **Migrate the 5–8 `.waitFor` sites in server controllers/servlets/pages**:
    - `Export2iCalServlet`, `RaplaEventsRestPage`, `AppointmentTableViewPage`, `ReservationTableViewPage`, `AppointmentPerDayViewPage`, `AbstractHTMLCalendarPage`, `RaplaICalImport`, `RemoteLocaleController`, `JNDIConfigController`, `ArchiverController`, `SecurityManager`
    - Each replaces `SynchronizedCompletablePromise.waitFor(facadeOrOperator.someAsync(), 10000, null)` with `syncOperator.someSync(...)` directly.
    - For sites that wait on `RaplaFacade.someAsync(...)` (not `StorageOperator.someAsync(...)`), they keep the `.waitFor` for now (out of scope; deferred).

### Phase 4 — Move the rxjava Maven dep

20. Move `<dependency>io.reactivex.rxjava3:rxjava</dependency>` (and the `:sources:provided` declaration) from `rapla-bom`'s default `<dependencies>` block into `<dependencyManagement>` only. Add an explicit `<dependency>` block for it in `rapla-client/pom.xml`.
21. **Verify**:
    - `mvn -pl rapla-core dependency:tree | grep rxjava` empty
    - `mvn -pl rapla-server dependency:tree | grep rxjava` empty
    - `mvn -pl rapla-client dependency:tree | grep rxjava` shows `io.reactivex.rxjava3:rxjava:3.1.5`
    - `mvn install` BUILD SUCCESS, `mvn test` 94/94 pass.

## Server-side async-facade audit

Beyond the storage-side `.waitFor` sites that Phase 3 fixes, **`RaplaFacade` (async) is also called from server code in nontrivial ways**. Those are out of this PRD's scope but listed here so they're not lost:

| Site | Call | Disposition |
|---|---|---|
| `ArchiverServiceImpl:132` | `raplaFacade.getReservationsAsync(...).thenAccept((events) -> raplaFacade.removeObjects(events))` | Background archiver. Phase 1 makes the periodic schedule Spring-native (`@Scheduled`); the lambda body could remain async or be rewritten as imperative. Defer. |
| `RaplaJNLPPageGenerator:236` | `reservations.thenAccept((events) -> ...)` for JNLP page rendering | Servlet request handler that needs the result before responding. Today blocks via implicit Promise resolution. Could go sync; deferred. |
| `RaplaResourcesRestPage`, `RaplaEventsRestPage`, `RemoteStorageImpl` | Various `.thenApply` / `.thenAccept` chains on async results before responding to REST | Same story — request handlers that ultimately need to block before responding. The cleanest fix is the dropped `RaplaServerFacade` sync sibling. Without it, callers continue to use `.waitFor`. |
| `SecurityManager:443,458` | `.waitFor(operator.getConflicts(...))` chained with `.thenAcceptBoth` | Storage-side; **fixed by Phase 3** (`SyncStorageOperator.getConflicts(...)`). |
| `NotificationStorage`, `JNDIServerPlugin`, `ExchangeAppointmentStorage`, `RaplaICalImport` | `.thenApply` chains on storage and facade calls | Mix of storage (fixed by Phase 3) and facade (deferred). |

**Total deferred async-facade sites on server:** ~10–15. Suggested follow-up PRD when/if the server-sync goal is revived: introduce a `RaplaServerFacade` sync sibling for the specific facade methods these callers need (a subset, not all 28). The decision is "is the server's facade-async ceremony worth a dedicated cleanup PRD?" — answer probably yes eventually, but not bundled with the rxjava goal.

## Tests

Existing 94 tests are the verification. No new tests required.

| Phase | Verification |
|---|---|
| 0 | `mvn compile` BUILD SUCCESS. `grep -rE "Throwables\.uncheck\|new CompletionException\(" rapla-core/src/main` empty. `Throwables.java` deleted. |
| 1 | `mvn install` BUILD SUCCESS, all tests pass. `CommandScheduler` no longer imports rxjava. |
| 2 | All tests pass. `Observable`/`Subject` imports in `rapla-core`/`rapla-server` empty. |
| 3 | All tests pass. `SynchronizedCompletablePromise.waitFor` in server only appears in deferred facade sites (not storage sites). |
| 4 | All tests pass. The three `dependency:tree` checks confirm rxjava placement. |

## Risks

1. **Custom function types collide with imports.** Files that import both `java.util.function.Function` and our `org.rapla.scheduler.Function` need fully-qualified references or alias imports. Mitigation: prefer the custom one for Promise-related code, JDK one for stream/util code; resolve case-by-case as Phase 0 progresses.
2. **`AutoCloseable.close()` declares `throws Exception`.** If callers want a no-throw cancellation, we may need our own `Cancellation` interface. Decide in Phase 1.
3. **`NotificationService` / `SynchronisationManager` rewrite may surface real periodic-orchestration bugs.** Today they orchestrate via rxjava Observable in ways that *might* depend on rx semantics (backpressure, scheduling). Rewriting as `@Scheduled` + imperative needs careful audit. Mitigation: rewrite each one in its own commit, run targeted tests after each.
4. **`SyncStorageOperator` impl path.** Two options: (a) `LocalAbstractCachableOperator` adds direct sync methods alongside the existing async ones; (b) the sync methods just call the async ones and `.waitFor` internally. (a) is cleaner; (b) is faster to write but defeats the goal. **Use (a).**

## Open Questions

1. **`AutoCloseable` vs custom `Cancellation` for `CommandScheduler.delay()` / `.schedule()` returns?** AutoCloseable is JDK-standard but `close() throws Exception` is awkward for "cancel a scheduled task." Recommendation: define a tiny `org.rapla.scheduler.Cancellation` (1 method, no throws).
2. **Phase 3 — direct sync methods vs unwrap-the-async?** Recommendation: direct sync methods (option (a) in Risk #4).
3. **Should the `Subject<T> extends Observable<T>, Subscriber<T>` declaration drop the `Subscriber` parent when moving to client?** `org.reactivestreams.Subscriber` is a transitive of rxjava. It's fine to keep — rxjava is in client now. No change needed.
4. **What about `NotificationService` / `SynchronisationManager`'s rxjava use that's *internal* (not via Observable)?** If they use `Schedulers.io()` or rxjava-specific APIs beyond Observable, those need rewriting too. Survey in Phase 2 step 14.

## Dependencies on other PRDs

| PRD | Relationship |
|---|---|
| **004: Multi-Module Architecture Analysis** | Frames why client/server separation matters. No direct dep. |
| **005: Multi-Module Split** | Hard prerequisite. Done. |
| **007: Build & Test Performance** | Independent. |
| **(future) Server-side facade-sync** | This PRD's "Server-side async-facade audit" section enumerates the ~10–15 deferred sites. A future PRD introduces a `RaplaServerFacade` sync sibling for them. Independent of rxjava confinement. |
