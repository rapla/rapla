# PRD 052: Clean restart of the rapla Swing client without JVM exit

**Status:** draft
**Date:** 2026-05-21

## Goal

When a user logs out of the Swing client and a different (or the same) user logs in **within the same JVM**, the client must start from a baseline as close as practical to a freshly-launched JVM:

- No carry-over of cached entities (allocatables, reservations, classifications).
- No stale Swing/UI state — admin menus must not appear for a non-admin who logs in after an admin session, calendar selection must not retain prior filter, edit windows must close.
- No leaked RxJava subscriptions or undisposed `Subject` observer lists.
- No silent retention of `User` / entity references via captured lambdas, scheduler queues, or AWT/Swing internals.
- Heap on second login converges to first-login baseline (within JVM heap-policy noise).

## Why this is needed

1. **VM pressure is real for admin↔normal-user transitions.** An admin loads more (broader visibility, more allocatables/reservations cached) than a normal user. Switching should free that working set. Today, much persists because singleton Swing beans hold references `clearAll()` can't reach.
2. **Permission-scope correctness.** The 2026-05-21 bug — non-admin sees admin menu after admin logged out — surfaced the root cause: `RaplaMenuBar` is `@Service @Lazy` singleton, its ctor runs once with first session's `getUser()` baked in, and `logout()` + re-`start()` reuses the same Spring context, so menu items never rebuild. Repeats in reverse. Every other singleton with per-user state has the same defect waiting.
3. **No "javaws relaunch" escape hatch.** `javax.jnlp` ships no restart/reload API on `BasicService`; only `Runtime.exec("javaws <url>.jnlp")` + `System.exit(0)`, which gives double-JVM-startup UX and loses in-flight state. In-JVM cleanup is the only workable path.
4. **The data cache already mostly cleans up.** `RemoteOperator.disconnect()` line 348 already calls `cache.clearAll()` — heavyweight entity store *does* reset. What does **not**: `RaplaMenuBar`, `CalendarSelectionModel`, `Application` (main JFrame), open edit controllers, `RaplaEventBus`'s `PublishSubject` observer lists, `raplascheduler-*` pending work, every presenter subscribed without disposing. These hold per-user state across logout.

## Scope

### In scope

- `rapla-client/` Swing client lifecycle.
- `org.rapla.client.spring.SpringRaplaClient.main()` — top-level context ownership.
- `RaplaClientServiceImpl.logout()` / `restart()` / `start()` — current in-JVM re-login lives here.
- `RaplaEventBus` — confirmed leak source; only file in client tier with multi-subscriber `Subject`s.
- Per-session bean classification.
- `@PreDestroy` audit for AWT/Swing listener removal (UIManager, Toolkit, KeyboardFocusManager).
- JFrame disposal hook **before** any context close so AWT releases strong refs.

### Out of scope

- Server-side (`rapla-server/`, `rapla-app/`) — no per-user singleton state.
- Angular SPA — each browser session has its own JS runtime.
- Spring DevTools-style hot reload — different problem.

## Background investigation

### Pure-Spring vs Spring Boot restart

Swing client uses plain `AnnotationConfigApplicationContext`, **not** `SpringApplication.run()`. Simplifies restart:

- No `SpringApplication.exit(ctx, generators)` machinery.
- No `ApplicationArguments` / `SpringApplicationRunListener` lifecycle.
- The "non-daemon thread to keep JVM alive across `ctx.close()`" caveat from the Baeldung pattern doesn't apply — AWT EDT is already non-daemon, so `ctx.close()` from any thread (including EDT) does not exit JVM.
- No DevTools `RestartClassLoader` — classes don't change between logins.

### Web Start compatibility

Anything that works under `mvn exec:java` works under `javaws raplaclient.jnlp`. `@PreDestroy`, `Subject.onComplete()`, `AnnotationConfigApplicationContext.close()`, `Frame.dispose()` are plain Java; JNLP sandbox doesn't restrict.

### AWT/Swing static-listener surface

`origin/master` had **no AWT-static listener registrations** — only `Thread.setDefaultUncaughtExceptionHandler` (doesn't fire for EDT). Original rapla tolerated EDT exceptions falling to `System.err` (rare cosmetic glitches).

Commit `a24c6443` "keycloak login" (2026-05-21, this branch) added one EDT-handler block in `RaplaClientServiceImpl.initialize()` lines 198-223:

```java
Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
    @Override protected void dispatchEvent(AWTEvent event) {
        try { super.dispatchEvent(event); }
        catch (Throwable t) { logger.error("Uncaught exception in AWT event dispatch", t); }
    }
});
```

Why added: OAuth/Keycloak browser-callback flow runs post-callback work (dialog close + session start) inside `SwingUtilities.invokeLater(...)`. If anything in the runnable threw (dialog disposed, focus owner gone, browser-tab-close race), failure went silently to `System.err` and login looked stuck. Wrapper routes EDT exceptions through rapla logger.

**Problem:** placement is inside a Spring bean's `initialize()` (ctor path) and the anonymous `EventQueue` captures the bean's instance `logger` field → strong ref to bean. Under any "close+recreate context" model (option A), the push runs on every recreate, accumulating event queues with strong refs to dead `RaplaClientServiceImpl` instances.

**Resolution (Phase 1a):** delete global `EventQueue.push`, replace with **per-site `SwingSafe.invokeLater(r, logger)` wrappers** at the ~13 `SwingUtilities.invokeLater(...)` sites in OAuth + logout/restart paths. Master shape recovered; EDT handler stops being JVM-global state. Confirmed EDT exceptions matter only at init/login/restart boundaries — refresh-token rotation runs on REST proxy thread (off-EDT) and never trips the EDT handler.

After this change there are **zero** AWT-static listener registrations in client tier — A and B become equivalent on this listener.

### RxJava-specific leak surface

Confirmed in `rapla-client/`:

- `RaplaEventBus.java:14-15` holds two `Subject` fields (`applicationEventPublishSubject`, `calendarRefreshEventPublishSubject`). All presenters/edit controllers/wizards subscribe. Bus is Spring `@Service` singleton.
- Of every `.subscribe(...)` call in client tier, only one (`ApplicationViewSwing.java:321`) keeps the `Disposable` and calls `.dispose()`. Rest are fire-and-forget — permanent observer-list entries → retain captured `this` → retain bean state → not GC'd even if bean "destroyed."

Even calling `dispose()` isn't enough — `Disposable` field itself retains the lambda; field must be nulled ([Zac Sweers — Disposables Can Cause Memory Leaks](https://www.zacsweers.dev/disposables-can-cause-memory-leaks/)).

## Options evaluated

### A. Full ApplicationContext recreate

`SpringRaplaClient.main()` owns context in a loop; on logout dispose all JFrames, `ctx.close()`, build new `AnnotationConfigApplicationContext(SwingClientConfig.class)`, restart.

Maximal cleanup — every bean rebuilt, no session state survives if `@PreDestroy` hooks correct. Historical highest implementation risk, but only known AWT-static registration (`EventQueue.push` from commit `a24c6443`) is removed in Phase 1a, leaving zero AWT-static registrations. Residual audit (scheduler queues, listeners on parent-like singletons, per-bean RxJava disposal) same as B. After Phase 1a, listener-surface leak risk comparable to B.

### B. Parent + child Spring context split

Parent (infrastructure, built once, never closed): `RemoteOperator`, `ClientFacade`, `ObjectMapper`, `RaplaResources`, `CommandScheduler`, `RaplaClientServiceImpl`. Child (session, recreated per login): `Application`, `RaplaMenuBar`, all presenters, `CalendarSelectionModel`, edit controllers, `RaplaEventBus`.

Same clean-slate semantics as A but smaller audit surface (~20 child beans). Infrastructure survives → faster login. Lowest leak risk. Requires bean taxonomy decision per bean (one-time but non-trivial).

### C. Explicit `SessionLifecycle` contract (no context split)

Add `interface SessionScoped { onLogin(User); onLogout(); }`; beans implement; `SessionLifecycle` singleton fires `onLogout()` from `ClientFacadeImpl.logout()`. Beans clear own state.

No Spring lifecycle change, incremental, works in current single-context arrangement. **Rejected** because discovery-driven — won't know we missed a bean until heap dump or bug; no by-construction guarantee.

### RxJava sub-strategies (independent of A/B/C)

- **R1.** Move `RaplaEventBus` to session-scope (requires A or B).
- **R2.** Keep bus singleton; on logout, `subject.onComplete()` + recreate subjects. Works in C, transition step.
- **R3.** Per-bean `@PreDestroy` discipline disposing + nulling field; CI grep for fire-and-forget `.subscribe(...)`.

R1 is cleanest and "free" with A or B. R2 is the compatible intermediate. R3 is hardening.

## Recommendation (selected)

**A (full ApplicationContext close + recreate), four phases.** Phase 1a's removal of the only known AWT-static registration neutralises A's main historical leak risk; what remains in A's favour is by-construction safety for the entire RxJava + listener surface. B+R1 was the earlier recommendation when the EDT handler still lived as JVM-global state; no longer true after Phase 1a.

Trade-off: ~0.5–2s slower re-login due to Spring context rebuild. Acceptable for a Swing desktop app where OAuth round-trip dominates login time.

1. **Phase 0 — Baseline measurement.** Heap-dump comparison: fresh login as admin → switch to non-admin → switch back → snapshot at each. Identify dominator-tree retainers.
2. **Phase 1a — Remove the JVM-global EDT exception handler.** Delete the `EventQueue.push(...)` block from `RaplaClientServiceImpl.initialize()` lines 198-223. Introduce `org.rapla.client.swing.SwingSafe.invokeLater(Runnable, Logger)` wrapper. Replace ~13 `SwingUtilities.invokeLater(...)` sites in OAuth + logout/restart paths (`RaplaClientServiceImpl`; former `OAuthCallbackPasteDialog` removed — see [PRD 029](029-swing-oauth-login.md)). Master-shape `initialize()` recovered; **zero** AWT-static listener registrations. Independent of A — lands first.
3. **Phase 1b — Minimal R2 step on RaplaEventBus.** Add `@PreDestroy` and explicit `reset()` to `RaplaEventBus`; wire `ClientFacadeImpl.logout()` to call `reset()`. Becomes redundant when Phase 2 lands (A makes bus die with `ctx.close()`; `@PreDestroy` still fires for free). Lands first so most-visible RxJava leak patched before Phase 2.
4. **Phase 2 — Context close+recreate loop in `SpringRaplaClient.main()`.** Refactor `main()` to own the `AnnotationConfigApplicationContext` lifecycle. `RaplaClientServiceImpl.logout()` signals launcher (via `CountDownLatch` or `ApplicationEvent`) to close and rebuild. Before `ctx.close()`, `disposeAllFrames()` hook iterates `Frame.getFrames()` and calls `.dispose()` so AWT releases strong refs. Add CI grep failing on any new `Toolkit.getDefaultToolkit().getSystemEventQueue().push`, `UIManager.addPropertyChangeListener`, `KeyboardFocusManager.addPropertyChangeListener`, `Toolkit.addAWTEventListener` outside the allowed (zero) callsites.

Phases 1a + 1b non-controversial and small; can land independently of Phase 2. Phase 2 is the architecture change.

## Plan

### Phase 0 — Baseline measurement (½ day)

- Launch fresh client (`mvn exec:java`), capture heap as admin via `jcmd <pid> GC.heap_dump baseline-admin.hprof` after first GC.
- Logout, login non-admin, force GC, dump `after-switch.hprof`.
- Login admin again, dump `back-to-admin.hprof`.
- Eclipse MAT both; diff dominator trees; identify User/Reservation/Allocatable surviving logout.
- Record findings in Open Questions.

### Phase 1a — Remove global EDT exception handler (1–2 hours)

- New class `rapla-client/.../client/swing/SwingSafe.java`:
  ```java
  public final class SwingSafe {
      private SwingSafe() {}
      public static void invokeLater(Runnable r, Logger logger) {
          SwingUtilities.invokeLater(() -> {
              try { r.run(); }
              catch (Throwable t) { logger.error("Uncaught exception in EDT runnable", t); }
          });
      }
  }
  ```
- Replace `SwingUtilities.invokeLater(...)` with `SwingSafe.invokeLater(..., logger)` at OAuth + logout/restart sites in `RaplaClientServiceImpl` (the `OAuthCallbackPasteDialog` callsite no longer exists — paste fallback removed, see [PRD 029](029-swing-oauth-login.md)).
- Delete `RaplaClientServiceImpl.initialize()` lines 198-223 (`Toolkit.getDefaultToolkit().getSystemEventQueue().push(...)` block).
- `initialize()` matches master's shape — only `Thread.setDefaultUncaughtExceptionHandler` remains.
- Test: tier-2 unit invoking `SwingSafe.invokeLater(throwing-runnable, logger)` and asserting `logger.error(...)` called.
- Manual: trigger OAuth login, force callback-side exception (close OAuth dialog before callback), verify error in `logs/rapla-client.log` not `System.err`.

### Phase 1b — RaplaEventBus disposal (1–2 hours)

- `RaplaEventBus.java`: add `@PreDestroy` calling `onComplete()` on both subjects. Add `reset()` completing current subjects and recreating via `Observables.createPublisher(scheduler.getExecutor())`.
- `ClientFacadeImpl.logout()`: before `cache.clearAll()`, look up `RaplaEventBus` + call `reset()`. Inject via ctor.
- Test: tier-2 unit subscribes, calls `reset()`, asserts subscriber received `onComplete()`, new subscription after reset still receives events.
- Manual: existing logout/login cycle, no behaviour regression.

### Phase 2 — Context close+recreate loop in `SpringRaplaClient.main()` (1–2 days)

User decisions 2026-05-22:
- Signal mechanism: `BlockingQueue<NextSession>` (not `CountDownLatch`) because signal must carry next session's `ConnectInfo` for switch-to-user.
- [PRD 051](done/051-switch-user-with-oauth.md) alignment: switch-to-user and switch-back ride the same close+recreate path. One mechanism handles all three transitions; switch-to-user gets the same clean-slate guarantee.

Implementation:

- New `org.rapla.client.NextSession` (record):
  ```java
  public record NextSession(ConnectInfo info, boolean exit) {
      public static NextSession showLoginDialog() { return new NextSession(null, false); }
      public static NextSession exit()            { return new NextSession(null, true); }
      public static NextSession reconnectAs(ConnectInfo info) { return new NextSession(info, false); }
  }
  ```
- New `LogoutSignal` bean (single-slot blocking queue):
  ```java
  @Service
  public class LogoutSignal {
      private final BlockingQueue<NextSession> queue = new ArrayBlockingQueue<>(1);
      public void next(NextSession ns) { queue.offer(ns); }
      public NextSession take() throws InterruptedException { return queue.take(); }
  }
  ```
- `RaplaClientServiceImpl`:
  - `logout()` → `eventBus.reset()` + `logoutSignal.next(NextSession.showLoginDialog())`. Drop the `SwingSafe.invokeLater(this::start)` self-restart (line 1338) — `main()` drives rebuild now.
  - `switchTo(User target)` ([PRD 051](done/051-switch-user-with-oauth.md)) → `logoutSignal.next(NextSession.switchTo(adminFullInfo, impersonationToken, targetUsername))`. Phase 5 ([PRD 029](029-swing-oauth-login.md) §7, 2026-05-25) updated the signature: admin's full 4-tuple is the primary session for the new context, impersonation token + target are passed separately and applied via `ClientService.setImpersonation()` post-start. Originally `reconnectAs(impersonationConnectInfo)` was used, which broke renewal because impersonation token went into the regular accessToken slot.
  - `switchBack()` → `logoutSignal.next(NextSession.switchBack())`. Launcher restores `savedAdminInfo` (4-tuple including provider routing) so post-restart Keycloak refresh works even when admin's access token expired during impersonation.
  - "Exit Rapla" → `logoutSignal.next(NextSession.exit())` to break `main()`'s loop.
- Refactor `SpringRaplaClient.main()`:
  ```java
  public static void main(String[] args) throws Exception {
      NextSession next = NextSession.reconnectAs(parseConnectInfo(args));
      while (!next.exit()) {
          try (AnnotationConfigApplicationContext ctx =
                   new AnnotationConfigApplicationContext()) {
              ctx.addBeanFactoryPostProcessor(globalLazyInitPostProcessor());
              ctx.addBeanFactoryPostProcessor(new SupplierAutoWrapperBeanFactoryPostProcessor());
              ctx.register(ClientConfig.class, ClientProxyConfig.class,
                           SwingClientConfig.class, EditTaskPresenterConfig.class,
                           PluginResourcesConfig.class);
              ctx.refresh();
              ctx.getBean(ClientService.class).start(next.info());
              next = ctx.getBean(LogoutSignal.class).take();   // blocks until logout/switch/exit
              disposeAllFrames();                              // BEFORE close so AWT releases refs
          }   // ctx.close()
      }
  }

  private static void disposeAllFrames() {
      for (Frame f : Frame.getFrames()) { try { f.dispose(); } catch (Throwable ignored) {} }
  }
  ```
- Add CI grep rule (`bash` test in `rapla-app/src/test/...`) failing when any of these appear outside empty allowlist:
  - `Toolkit.getDefaultToolkit().getSystemEventQueue().push`
  - `UIManager.addPropertyChangeListener`
  - `KeyboardFocusManager.addPropertyChangeListener`
  - `Toolkit.addAWTEventListener`
- Verify `RemoteOperator.disconnect()`'s `cache.clearAll()` still runs before context close.
- Heap verification (Task #10, post-Phase-2): confirm no growth across admin↔non-admin↔switch-to-user cycles on `~/git/dhbwrapla` heavy-data deployment.

## Tests

- **Tier 2 SwingSafe** — throwing runnable via `SwingSafe.invokeLater(...)`; logger received error, test terminates. Lands Phase 1a.
- **Tier 2 RaplaEventBus** — subscribe, reset, confirm completion + fresh-subject behaviour. Lands Phase 1b.
- **Tier 2 menu gating** — construct `RaplaMenuBar` against three permission levels (full admin / group-only admin / plain non-admin), assert entries. Independent but locks gating rules restart fix must preserve.
- **Manual repro of original bug** — fresh client, login non-admin, logout, login admin → admin entries appear. Reverse too. Phase 2 must pass manually.
- **Heap-retention regression** — after Phase 2, two full admin↔non-admin cycles must not grow post-GC heap by more than X% (threshold from Phase 0).
- **Login-speed regression** — measure `AnnotationConfigApplicationContext.refresh()` cold and on second iteration. Document baseline.
- **AWT-static grep CI rule** — fails on introduction of `EventQueue.push`/`UIManager.addPropertyChangeListener`/`KeyboardFocusManager.addPropertyChangeListener`/`Toolkit.addAWTEventListener` outside allowlist (currently empty). Lands Phase 2.

## Open Questions

- **OQ1:** Phase 0 finding — is heap actually retained across logout, by which dominator? Resolves whether Phase 2 worth effort or 1a+1b enough.
- **OQ2:** Does `CommandScheduler` need explicit `cancelAll()` before context close, or does Spring `@PreDestroy` chain shut down its executor cleanly? With A scheduler dies with context, so question is whether pending tasks throw or get silently dropped during teardown.
- **OQ3:** Login-speed — confirm ~0.5–2s rebuild for `AnnotationConfigApplicationContext.refresh()` on full `SwingClientConfig`. If much worse, B+R1 attractive on UX grounds.
- **OQ4:** Clear per-session state out of static fields in `RaplaImages`, `RaplaWidget` etc.? Probably out of scope (static caches aren't per-user) — verify during Phase 0.
- **OQ5:** ~~Logout signal mechanism~~ **Resolved 2026-05-22:** `BlockingQueue<NextSession>` chosen over `CountDownLatch` (signal must carry next-session `ConnectInfo` for switch-to-user).
- **OQ6:** ~~[PRD 051](done/051-switch-user-with-oauth.md) interaction~~ **Resolved 2026-05-22:** switch-to-user and switch-back ride same close+recreate channel as logout. [PRD 051](done/051-switch-user-with-oauth.md)'s drafted in-place token swap superseded — switching is a session change, not a token swap. [PRD 051](done/051-switch-user-with-oauth.md) needs update when implementation starts.
