# PRD 008: Sync server facade, async client facade, rxjava only in rapla-client

**Status:** draft
**Date:** 2026-05-07

## Goal

Two coupled changes that simplify the async story across the codebase:

1. **`rapla-server` becomes synchronous.** Storage and facade impls return raw values (`Collection<Reservation>`, `User`, etc.) instead of `Promise<T>`. Server callers stop chaining `.thenApply` and stop blocking with `SynchronizedCompletablePromise.waitFor(...)`. Spring's `TaskScheduler` / `@Scheduled` / `TaskExecutor` replace `CommandScheduler` for the server's actual scheduling needs (one periodic archiver task, a few worker-thread submissions, plain `synchronized` blocks).
2. **`rxjava` is confined to `rapla-client`.** The `rxjava` Maven dep moves from `rapla-bom`'s default `<dependencies>` to `rapla-client/pom.xml`. `rapla-core` becomes rxjava-free; the custom `Promise<T>` interface (a thin wrapper around `CompletionStage`) is deleted in favour of `CompletionStage<T>` in core's interface signatures. The rxjava-shaped abstractions that genuinely use rx operators (`Observable`, `Subject`, `CommandScheduler`) move to `rapla-client` along with their impls.

### Verifiable end state

```
$ mvn -pl rapla-core dependency:tree | grep rxjava       # empty
$ mvn -pl rapla-server dependency:tree | grep rxjava     # empty
$ mvn -pl rapla-client dependency:tree | grep rxjava     # io.reactivex.rxjava3:rxjava:3.1.5

$ grep -rE "^import io\.reactivex" rapla-core/src/main rapla-server/src/main   # empty
$ grep -rE "^import io\.reactivex" rapla-client/src/main | wc -l               # >0

$ grep -rE "\.thenApply|\.thenCompose|\.thenAccept" rapla-server/src/main      # empty
$ grep -rE "Promise<|ResolvedPromise|SynchronizedCompletablePromise" rapla-server/src/main rapla-core/src/main  # empty

$ ls rapla-core/src/main/java/org/rapla/scheduler/                              # directory does not exist
```

## Why

### Why drop `Promise<T>` in favour of `CompletionStage<T>`

`Promise<T>`'s own javadoc says *"same as `java.util.concurrent.CompletionStage` but usable in gwt"*. GWT was removed years ago. The production impl (`SynchronizedPromise`) is a literal `CompletionStage` wrapper:

```java
public class SynchronizedPromise<T> implements Promise<T> {
    final Executor promiseExecutor;
    final CompletionStage f;          // ← it just holds one
    ...
}
```

Every Promise method delegates to `CompletionStage`. The rxjava imports it carries (`Action`, `Consumer`, `Function`, `BiFunction`, `BiConsumer`) are function-type aliases, used because GWT couldn't compile JDK `java.util.function.*`. With GWT gone, the wrapper is dead weight.

The only thing `Promise` has that `CompletionStage` doesn't is `execOn(Executor)` — a *sticky executor* used by Swing presenters to keep subsequent `thenApply` continuations on the EDT. That capability is preserved as a tiny ~50-line client-side wrapper around `CompletionStage` (see Phase 1 #4).

### Why the server should be synchronous

Server methods return `Promise<T>` regardless of who calls them. But:

- A server request runs on a thread already dedicated to it. There is no UI thread to protect.
- Server code allocates a `Promise`, chains `.thenApply`, then `.get()`s back into a value — every method async-shaped despite no actual async work.
- The 9 `.thenApply` / `.thenCompose` chains in `rapla-server/src/main` are pure ceremony.
- The 5 `SynchronizedCompletablePromise.waitFor(promise, timeout, ...)` blocking calls in REST controllers / servlets disappear *for free* once the underlying methods return raw values.

PRD 005 split the modules and removed the `rapla-server → rapla-client` Maven edge. This PRD finishes the matching API shape: with separate modules in place, the *interfaces* should also be honest about who blocks and who doesn't.

### Why Spring covers the server's `CommandScheduler` use

The server's actual `CommandScheduler` needs are tiny:

| Use | Spring replacement |
|---|---|
| `ArchiverServiceTask` periodic timer (every hour) | `@Scheduled(fixedRate = 3600000)` on a `@Component` method |
| `scheduler.delay(task, ms)` (one-shot) | `TaskScheduler.schedule(task, Instant.now().plusMillis(ms))` |
| `scheduler.run(task)` (submit to worker pool) | `TaskExecutor.execute(task)` or `CompletableFuture.runAsync(task, executor)` |
| `scheduler.scheduleSynchronized(lock, task)` | plain `synchronized(lock) { task.run(); }` (after sync migration) |

Spring Boot autoconfigures a `TaskScheduler` and `TaskExecutor` once `@EnableScheduling` is on the `@SpringBootApplication`. No new framework, no new abstraction.

## Scope

### What ends up where

| Module | Contents (after refactor) |
|---|---|
| `rapla-core` | All entity / domain / framework / i18n / REST-contract code, **plus** four interfaces: `RaplaFacade` (async, returns `CompletionStage<T>`), `RaplaServerFacade` (sync, **new**), `StorageOperator` (async, returns `CompletionStage<T>`), `SyncStorageOperator` (sync, **new**). **No `org.rapla.scheduler.*` package.** Zero rxjava. |
| `rapla-server` | Sync impls: `ServerFacadeImpl implements RaplaServerFacade`, `LocalAbstractCachableOperator implements SyncStorageOperator`. Zero `Promise`/`CompletionStage` returns from production logic. Spring `TaskScheduler` / `@Scheduled` for periodic work. Zero rxjava. |
| `rapla-client` | Async wrapper around the server interfaces (used in tests / Swing) + the rxjava-shaped abstractions: `Observable`, `Subject`, `CommandScheduler` interfaces and their impls (`JavaObservable`, `JavaSubject`, `UtilConcurrentCommandScheduler`, `DefaultScheduler`). Plus the new ~50-line `Promise<T>` sticky-executor wrapper. **Only module with rxjava on the classpath.** |
| `rapla-app` | Unchanged. Spring Boot `@SpringBootApplication` entry, distribution assembly. |

### Files affected

| File | Change |
|---|---|
| `rapla-core/src/main/java/org/rapla/scheduler/Promise.java` | **delete** (replaced by `CompletionStage<T>` at call sites) |
| `rapla-core/.../scheduler/CompletablePromise.java` | **delete** (replaced by `CompletableFuture<T>`) |
| `rapla-core/.../scheduler/ResolvedPromise.java` | **delete** (replaced by `CompletableFuture.completedFuture(x)`) |
| `rapla-core/.../scheduler/UnsynchronizedPromise.java` | **delete** |
| `rapla-core/.../scheduler/sync/SynchronizedPromise.java` | **delete** (it was already a `CompletionStage` wrapper) |
| `rapla-core/.../scheduler/sync/SynchronizedCompletablePromise.java` | **delete**; `.waitFor(stage, ms, log)` either becomes `stage.toCompletableFuture().get(ms, MS)` inline or a tiny core utility `Stages.waitFor(...)` if call sites benefit from compactness |
| `rapla-core/.../scheduler/Observable.java`, `Subject.java`, `CommandScheduler.java` | **move to `rapla-client/.../scheduler/`** (rxjava-shaped, only client uses operators like `delay`/`flatMap`) |
| `rapla-core/.../scheduler/sync/JavaObservable.java`, `JavaSubject.java`, `UtilConcurrentCommandScheduler.java` | **move to `rapla-client`** |
| `rapla-core/.../framework/internal/DefaultScheduler.java` | **move to `rapla-client`** (uses `CommandScheduler`/`Observable` operators) |
| `rapla-core/.../facade/RaplaFacade.java` | Stays. 28 method signatures change `Promise<T>` → `CompletionStage<T>`. Mechanical. |
| `rapla-core/.../facade/server/RaplaServerFacade.java` | Becomes a **sibling** (not subtype) of `RaplaFacade`. Same method names, sync return types, may `throws RaplaException`. Today extends `RaplaFacade` and adds one sync `getpersistent` method. |
| `rapla-core/.../facade/internal/FacadeImpl.java` | Today implements `RaplaFacade` and wraps sync work in `Promise`. **Move sync work to `rapla-server` (new `ServerFacadeImpl`); replace this file with a thin async wrapper that lives in `rapla-client`** (delegates to the server interface, schedules onto a worker executor). |
| `rapla-core/.../storage/StorageOperator.java` | Stays async. 15 `Promise<T>` returns become `CompletionStage<T>`. |
| (new) `rapla-core/.../storage/SyncStorageOperator.java` | New sync sibling. Same 15 method names, raw return types. |
| `rapla-server/.../storage/impl/server/LocalAbstractCachableOperator.java` | Switch from implementing `StorageOperator` to `SyncStorageOperator`. Drop every `return new ResolvedPromise<>(x);` → `return x;`. |
| `rapla-server/.../plugin/notification/server/NotificationService.java` | Drop `.thenApply` / `Observable` orchestration; convert to imperative loops. If genuine periodic scheduling is needed, use `@Scheduled` (it currently has none — appears to use `Observable`/`CommandScheduler` only as a fancier way to write sequential code). |
| `rapla-server/.../plugin/exchangeconnector/server/SynchronisationManager.java` | Same audit. The Exchange-API I/O is genuinely network-bound; that work runs in `CompletableFuture.runAsync(..., taskExecutor)` if needed. The reactive orchestration around it goes. |
| `rapla-server/.../plugin/archiver/server/ArchiverServiceTask.java` | `timer.schedule(...)` → `@Scheduled(fixedRate = MILLISECONDS_PER_HOUR)` on the method. |
| `rapla-server/src/main/java/.../*Servlet.java`, `*Controller.java`, `*Page.java` (the 5 `SynchronizedCompletablePromise.waitFor` sites) | Just call the sync method directly. Delete the `.waitFor` import + line. |
| `rapla-server/.../server/spring/ServerCoreConfig.java` | Drop `@Bean CommandScheduler` (Spring autowires `TaskScheduler` / `TaskExecutor`). Wire `ServerFacadeImpl`. |
| `rapla-server/pom.xml` | (Already has its own dep block.) Inherits no rxjava once it's pulled out of `rapla-bom`. |
| `rapla-client/pom.xml` | Add explicit `<dependency>` on `io.reactivex.rxjava3:rxjava` (since it's no longer in the `rapla-bom` default). |
| `rapla-bom/pom.xml` | Move `rxjava` and the `rxjava` `:sources:provided` deps from default `<dependencies>` to `<dependencyManagement>` only (so the version is still pinned for whoever opts in). |
| (new) `rapla-client/.../scheduler/Promise.java` | ~50-line wrapper around `CompletionStage<T>` carrying a sticky `Executor`. Delegates `thenApply` etc. to `stage.thenApplyAsync(fn, executor)`. Used by Swing presenters that today chain `.execOn(edt).thenApply(...)`. |

### Out of scope

- Replacing rxjava `Observable`/`Subject` with JDK `Flow.Publisher`. The rx operators (`delay`, `flatMap`, `map`, `Schedulers.io()`) used by `UtilConcurrentCommandScheduler` and client presenters have no JDK equivalent worth recreating. rxjava stays in the client.
- Removing `Promise` from the *client*. The 50-line wrapper preserves `execOn(executor)` sticky-executor ergonomics for Swing EDT chaining.
- Touching the REST wire format. Server-side controllers can return raw values (Spring serializes synchronously) or `CompletableFuture<T>` (Spring auto-async); either is fine. No change to clients consuming the API.

## Plan

Five phases. Each compiles and `mvn test` passes on its own. Server-side work goes file-by-file.

### Phase 1 — Promise → CompletionStage in rapla-core (~1.5 days)

1. Replace `Promise<T>` return types in `RaplaFacade.java` (28 methods) and `StorageOperator.java` (15 methods) with `CompletionStage<T>`. The rxjava `Function`/`Consumer`/`Action` aliases inside method bodies that callers pass become JDK `java.util.function.Function`/`Consumer` and `Runnable`. **Real risk:** rxjava `Function.apply` declares `throws Throwable`; JDK doesn't. Grep for catch-rethrow patterns and adjust.
2. Replace `ResolvedPromise.of(x)` call sites with `CompletableFuture.completedFuture(x)`. Replace `new UnsynchronizedPromise<>()` constructions similarly.
3. Delete `Promise.java`, `CompletablePromise.java`, `ResolvedPromise.java`, `UnsynchronizedPromise.java`, `sync/SynchronizedPromise.java`, `sync/SynchronizedCompletablePromise.java`.
4. Add `rapla-client/.../scheduler/Promise.java` — the ~50-line sticky-executor wrapper around `CompletionStage<T>` (or rename to `EdtPromise` / `ChainedStage` to avoid ambiguity with the deleted core type).
5. Migrate Swing client call sites that today use `.execOn(edt).thenApply(...)` to the new wrapper. Most are in `rapla-client/.../client/swing/**`.
6. **Verify:** `mvn install` BUILD SUCCESS, `mvn test` 94/94 pass. `grep -rE "import org\.rapla\.scheduler\.Promise" rapla-core` returns empty.

### Phase 2 — Sync sibling interfaces in rapla-core (~0.5 day)

7. Make `RaplaServerFacade` a **sibling** (not subtype) of `RaplaFacade`. Mirror the 28 methods, sync signatures, may `throws RaplaException`. Keep the existing `getpersistent`.
8. Create `SyncStorageOperator` as sibling of `StorageOperator`. Mirror the 15 methods, sync signatures.
9. Add an `archunit` test that asserts every method *name* in `RaplaFacade` has a matching name in `RaplaServerFacade`, modulo return-type wrapping. ~30 LOC. Catches future drift.
10. **Verify:** Both interfaces compile, no implementations yet. `mvn install` BUILD SUCCESS.

### Phase 3 — Implement sync server facade in rapla-server, async wrapper in rapla-client (~1.5 days)

11. Copy `FacadeImpl` to `rapla-server/.../facade/server/ServerFacadeImpl.java`. Strip every `CompletableFuture.completedFuture(x)` and `*Async` wrapping — this impl is now plain sync. Implements `RaplaServerFacade`.
12. Same for `LocalAbstractCachableOperator`: implements `SyncStorageOperator`, no more `CompletableFuture` wrappers.
13. Move `FacadeImpl` from `rapla-core` to `rapla-client`. It now holds a `RaplaServerFacade` (server-supplied via Spring on the server side; via REST proxy on the client) and wraps each call in `CompletableFuture.supplyAsync(() -> serverFacade.x(...), workerExecutor)`. **Critical:** the wrapper *must* dispatch onto a worker executor — running the supplier inline on the calling thread silently regresses EDT-blocking. Add a test that verifies the calling thread isn't the same as the completion thread.
14. Update `ServerCoreConfig` to wire `ServerFacadeImpl` for `RaplaServerFacade`. The async `RaplaFacade` impl is wired only on the client side.
15. **Verify:** `mvn install` BUILD SUCCESS, `mvn test` 94/94 pass.

### Phase 4 — Migrate server callers off async (~2-3 days)

One PR. Internally organised as the batches below — useful as commit boundaries within the PR for bisecting if something regresses, but not as separate review units. The migrations are mechanically similar enough that splitting them across reviews adds review overhead without adding signal.

16. **Batch a:** `rapla-server/.../server/spring/web/*Controller.java` and `rapla-server/.../*Servlet.java` and `rapla-server/.../endpoints/server/*Page.java`. Switch from `RaplaFacade` to `RaplaServerFacade`. Delete every `SynchronizedCompletablePromise.waitFor(promise, ...)` call — the underlying call is sync now.
17. **Batch b:** `rapla-server/.../plugin/exchangeconnector/server/*.java`. Replace `.thenApply` chains with imperative code. The Exchange-API I/O genuinely is network-bound — wrap *only the I/O calls* in `CompletableFuture.runAsync(...)` if true async is needed for parallelism, otherwise sync.
18. **Batch c:** `rapla-server/.../plugin/notification/server/*.java`. Convert to imperative loops over the now-sync facade.
19. **Batch d:** `rapla-server/.../plugin/archiver/server/ArchiverServiceTask.java`. Replace `timer.schedule(() -> doArchive(...), 0, MS_PER_HOUR)` with `@Scheduled(fixedRate = 3600000)` on a `@Component` method. Add `@EnableScheduling` to the `@SpringBootApplication` if not already there.
20. **Batch e:** Remaining `rapla-server/.../server/internal/*` and storage callers. Drop `CommandScheduler` injection — replace with `TaskExecutor` injection where async submit is needed, plain sync where not.
21. After all batches: `grep -rE "thenApply|thenCompose|thenAccept|SynchronizedCompletablePromise" rapla-server/src/main` returns 0 lines. `grep -rE "import io\.reactivex" rapla-server/src/main` returns 0 lines. `grep -rE "import org\.rapla\.scheduler" rapla-server/src/main` returns 0 lines.

### Phase 5 — Move rxjava abstractions to rapla-client; relocate Maven dep (~0.5 day)

22. `git mv` the rxjava-shaped files from `rapla-core/.../scheduler/` and `rapla-core/.../framework/internal/DefaultScheduler.java` to corresponding paths in `rapla-client`. Delete the now-empty `rapla-core/.../scheduler/` directory.
23. Move `<dependency>io.reactivex.rxjava3:rxjava</dependency>` (and the `:sources:provided` declaration) from `rapla-bom`'s default `<dependencies>` block into `<dependencyManagement>` only. Add an explicit `<dependency>` block for it in `rapla-client/pom.xml`.
24. **Verify:**
    - `mvn -pl rapla-core dependency:tree | grep rxjava` empty
    - `mvn -pl rapla-server dependency:tree | grep rxjava` empty
    - `mvn -pl rapla-client dependency:tree | grep rxjava` shows `io.reactivex.rxjava3:rxjava:3.1.5`
    - `mvn install` BUILD SUCCESS, `mvn test` 94/94 pass
25. Update PRD 005's "remaining one-way edges" table — the `rapla-core → rxjava` and `rapla-server → rxjava` edges are now both gone.

## Tests

This is a refactor, not a feature. Existing tests are the verification. Three places where new tests are appropriate:

| Phase | New test |
|---|---|
| 1 | None — Promise/CompletionStage substitution is mechanical, existing tests cover it. |
| 2 | `archunit` test asserting method-name parity between `RaplaFacade` and `RaplaServerFacade`. ~30 LOC. Catches future drift. |
| 3 | Test that the async `FacadeImpl` wrapper schedules onto a worker executor, not the calling thread. Captures the "supplier runs inline" bug at compile time. |
| 4 | None per batch — each batch is verified by passing the existing 94 tests. |
| 5 | Add a CI-level grep assertion (`grep -rE "import io\.reactivex" rapla-core/src/main rapla-server/src/main` must be empty) to prevent regression. |

## Risks

1. **rxjava `Function`/`Consumer`/`Action` allow checked exceptions; JDK equivalents don't.** Some callers throw checked exceptions inside `.thenApply` lambdas relying on rxjava's `throws Throwable`. **Mitigation:** Phase 1 grep for `throws RaplaException` inside lambda bodies. Wrap as `RaplaUncheckedException` (existing) at the boundary, unwrap inside `.exceptionally`. Worst case: a few mechanical rewrites.

2. **The async wrapper in Phase 3 must run on a worker executor.** A naive `CompletableFuture.completedFuture(serverFacade.foo())` evaluates `foo()` on the calling thread — exactly the EDT-blocking bug we're trying to avoid. **Mitigation:** the wrapper uses `CompletableFuture.supplyAsync(() -> serverFacade.foo(), workerExecutor)`. Test in Phase 3 asserts thread identity differs.

3. **`Observable`/`Subject` move to client breaks any server caller that uses them.** Today `NotificationService` and `SynchronisationManager` use them. **Mitigation:** Phase 4 batches b and c rewrite those call sites *before* Phase 5 moves the interfaces. If a residual server caller is missed, Phase 5's `mvn compile` catches it loudly.

4. **`RaplaFacade` is part of the plugin ABI.** Any custom plugin (dhbwrapla, others) that today injects `RaplaFacade` and calls `.thenApply` will see method signatures change `Promise<T>` → `CompletionStage<T>`. **Mitigation:** `CompletionStage<T>` is a strictly richer JDK interface; existing `.thenApply` call sites compile unchanged. No migration needed downstream beyond what their own code chooses to do.

5. **The Exchange / notification reactive code might be doing real async work.** If `SynchronisationManager`'s rxjava use is for parallel I/O fan-out, "make it sync" regresses throughput. **Mitigation:** Phase 4 batch b audits each rxjava use case; for genuinely parallel I/O, use `CompletableFuture.runAsync(..., taskExecutor)` with a server-side worker pool. Don't blindly serialize.

6. **`Promise.exceptionally(Consumer<Throwable>)` and `CompletionStage.exceptionally(Function<Throwable,T>)` differ in shape.** Promise's "log and forget" callers need to return a recovery value (or `null`) under `CompletionStage`. **Mitigation:** mechanical: every `promise.exceptionally(t -> log.error(t))` becomes `stage.exceptionally(t -> { log.error(t); return null; })`. Phase 1 enumerates all sites.

7. **Two parallel facade interfaces drift over time.** Adding a method to `RaplaFacade` and forgetting to add it to `RaplaServerFacade` silently halves the API. **Mitigation:** the archunit test in Phase 2 #9 asserts parity at every build.

8. **Spring `@EnableScheduling` may not be on the existing `@SpringBootApplication`.** The archiver replacement assumes it is. **Mitigation:** Phase 4 batch d adds it if missing.

## Open Questions

1. **Should `Promise<T>` (the new ~50-line client-side wrapper) keep the same name?** Pro: minimal call-site churn for Swing presenters. Con: confusable with the deleted core type. **Recommendation:** rename to `EdtPromise` or `ChainedStage` to make the intent obvious.
2. **`SynchronizedCompletablePromise.waitFor(stage, timeoutMs, logger)` is used in 5 server controllers. Inline as `stage.toCompletableFuture().get(timeoutMs, MS)` or keep as a tiny `Stages.waitFor(...)` utility?** The tiny utility logs on timeout, which the inline form loses. **Recommendation:** inline. After Phase 4 the server callers are sync — there's no `stage` to wait on at all. The .waitFor sites disappear, not migrate.
3. **Should the async wrapper in Phase 3 dispatch every `CompletionStage<T>`-returning call to a single worker executor, or use Spring's `TaskExecutor`?** Single executor is simpler; Spring's auto-tunes the pool. **Recommendation:** Spring's. The wrapper takes `TaskExecutor` via constructor injection.
4. **What's the migration path for downstream consumers (dhbwrapla)?** They depend on `org.rapla:rapla-core` and `:rapla-server`. Method signatures change `Promise<T>` → `CompletionStage<T>`. Their existing `.thenApply` call sites compile unchanged (CompletionStage has the same method names). `Promise.execOn(...)` callers — if any — break and need to migrate to either explicit `*Async(fn, executor)` or the client-side `Promise` wrapper. **Recommendation:** flag in the PR description; offer the wrapper as an importable class if downstream wants to keep their code shape.
5. **`Subject<T>` event streams in the client — do any of them survive the refactor?** They're used by Swing presenters for view event streams. Out of scope. They keep using rxjava in `rapla-client`.

## Dependencies on other PRDs

| PRD | Relationship |
|---|---|
| **004: Multi-Module Architecture Analysis** | Frames why client/server separation matters. No direct dep. |
| **005: Multi-Module Split** | Hard prerequisite. The interface split this PRD proposes only makes sense once modules are split — done. |
| **007: Build & Test Performance** | Independent. The Phase 1.2 `mvn test && mvn test` non-idempotence in PRD 007 is unrelated. |
| **(future) Angular client** | Independent. Angular consumes REST, not Java. The new sync `RaplaServerFacade` is exactly the shape REST controllers want. |
