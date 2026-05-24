# PRD 052: Clean restart of the rapla Swing client without JVM exit

**Status:** draft
**Date:** 2026-05-21

## Goal

When a user logs out of the Swing client and a different (or the same)
user logs in **within the same JVM**, the client must start from a
baseline as close as practically achievable to a freshly-launched JVM:

- No carry-over of cached entities (allocatables, reservations, classifications).
- No stale Swing/UI state — admin menus must not appear for a non-admin who
  logs in after an admin session, calendar selection must not retain the
  previous user's filter, edit windows must be closed.
- No leaked RxJava subscriptions or undisposed `Subject` observer lists.
- No silent retention of `User` / entity references via captured lambdas,
  scheduler queues, or AWT/Swing internals.
- Heap usage on the second login must converge to the same baseline as
  a first-login JVM (within JVM heap-policy noise).

## Why this is needed

1. **VM pressure is real for admin↔normal-user transitions.** An admin
   user loads more resources (broader visibility scope, more allocatables
   and reservations cached) than a normal user. Switching to a normal
   user should free that working set. Today, much of it persists because
   singleton Swing beans hold references the data-cache `clearAll()` can't
   reach.
2. **Permission-scope correctness.** The 2026-05-21 bug report —
   non-admin sees admin menu entries after an admin logged out — surfaced
   the root cause: `RaplaMenuBar` is a `@Service @Lazy` singleton, its
   constructor runs once with the first session's `getUser()` baked in,
   and `logout()` + re-`start()` reuses the same Spring context, so the
   menu items are never rebuilt. The bug repeats in reverse (admin login
   after non-admin login sees no admin entries). Beyond the menu, every
   other singleton with per-user state has the same defect waiting.
3. **No "javaws relaunch" escape hatch.** `javax.jnlp` ships no
   restart/reload API on `BasicService` or anywhere else; the only way
   to relaunch under Web Start is `Runtime.exec("javaws <url>.jnlp")` +
   `System.exit(0)`, which gives a double-JVM-startup UX and loses
   in-flight state. In-JVM cleanup is the only workable path.
4. **The data cache already mostly cleans up.** `RemoteOperator.disconnect()`
   line 348 already calls `cache.clearAll()` — so the heavyweight entity
   store *does* reset. What does **not** reset:
   `RaplaMenuBar`, `CalendarSelectionModel`, `Application` (main JFrame),
   open edit controllers, `RaplaEventBus`'s `PublishSubject` observer
   lists, `raplascheduler-*` pending work, and every presenter that
   subscribed to those subjects without disposing. These are what hold
   the per-user state across logout today.

## Scope

### In scope

- `rapla-client/` Swing client lifecycle.
- `org.rapla.client.spring.SpringRaplaClient.main()` — top-level context ownership.
- `RaplaClientServiceImpl.logout()` / `restart()` / `start()` — current
  in-JVM re-login path lives here.
- `RaplaEventBus` — confirmed leak source; only file in the client tier
  with multi-subscriber `Subject`s.
- Per-session bean classification — which `@Service`/`@Component` beans
  in the client tier are session-scoped vs infrastructure.
- `@PreDestroy` audit for AWT/Swing listener removal (UIManager,
  Toolkit, KeyboardFocusManager) on session-scope beans.
- JFrame disposal hook to run **before** any context close so AWT
  releases its strong refs.

### Out of scope

- Server-side (`rapla-server/`, `rapla-app/`) — server runs cleanly under
  Spring Boot; no per-user singleton state in scope.
- Angular SPA (`rapla-angular/`) — each browser session has its own JS
  runtime; no equivalent issue.
- Spring DevTools-style hot code reload — different problem (class
  changes between launches), not addressed here.

## Background investigation

### Pure-Spring vs Spring Boot restart

The Swing client uses plain `AnnotationConfigApplicationContext`, **not**
`SpringApplication.run()`. That simplifies restart:

- No `SpringApplication.exit(ctx, generators)` machinery to invoke.
- No `ApplicationArguments` / `SpringApplicationRunListener` lifecycle
  to coordinate.
- The "non-daemon thread to keep the JVM alive across `ctx.close()`"
  caveat from the Baeldung pattern doesn't apply — AWT's EDT is already
  non-daemon, so `ctx.close()` from any thread (including the EDT) does
  not exit the JVM.
- No DevTools `RestartClassLoader` — classes don't change between logins,
  so the two-classloader trick is unneeded.

### Web Start compatibility

Anything that works under `mvn exec:java` works identically under
`javaws raplaclient.jnlp`. `@PreDestroy`, `Subject.onComplete()`,
`AnnotationConfigApplicationContext.close()`, `Frame.dispose()` are all
plain Java with no privileged operations; the JNLP sandbox doesn't
restrict them.

### AWT/Swing static-listener surface

`origin/master` had **no AWT-static listener registrations** — only
`Thread.setDefaultUncaughtExceptionHandler` (which doesn't fire for the
EDT). The original rapla tolerated EDT exceptions falling to `System.err`
because EDT-side failures were rare cosmetic glitches.

Commit `a24c6443` "keycloak login" (2026-05-21, on this branch) added
one EDT-handler block in `RaplaClientServiceImpl.initialize()` lines
198-223:

```java
Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
    @Override protected void dispatchEvent(AWTEvent event) {
        try { super.dispatchEvent(event); }
        catch (Throwable t) { logger.error("Uncaught exception in AWT event dispatch", t); }
    }
});
```

Why it was added: the OAuth/Keycloak browser-callback flow runs the
post-callback work (dialog close + session start) inside
`SwingUtilities.invokeLater(...)`. If anything inside the runnable threw
(dialog already disposed, focus owner gone, race during browser tab
close), the failure went silently to `System.err` and the login looked
stuck. The `EventQueue.push` wrapper routes those EDT exceptions through
the rapla logger.

**Problem with this placement:** it's inside a Spring bean's
`initialize()` (constructor path) and the anonymous `EventQueue`
captures the bean's instance `logger` field → strong ref to the bean
instance. Under any "close+recreate context" model (option A) the push
runs again on every recreate, accumulating event queues on the AWT
stack with strong refs to dead `RaplaClientServiceImpl` instances.

**Resolution (Phase 1a in the plan below):** delete the global
`EventQueue.push` and replace with **per-site `SwingSafe.invokeLater(r,
logger)` wrappers** at the ~13 `SwingUtilities.invokeLater(...)` call
sites in the OAuth + logout/restart paths. Master-shape recovered;
the EDT handler stops being JVM-global state. Confirmed
([previous exchange](#)) that EDT exceptions matter only at
init/login/restart boundaries — the refresh-token rotation flow runs on
the REST proxy thread (off-EDT) and never trips the EDT handler.

After this change there are **zero** AWT-static listener registrations
in the client tier — A and B become equivalent on this listener
because the listener no longer exists.

### RxJava-specific leak surface

Confirmed in `rapla-client/`:

- `RaplaEventBus.java:14-15` holds two `Subject` fields
  (`applicationEventPublishSubject`, `calendarRefreshEventPublishSubject`).
  All presenters / edit controllers / wizards subscribe to these. The
  bus is a Spring `@Service` singleton.
- Of every `.subscribe(...)` call in the client tier, only one
  (`ApplicationViewSwing.java:321`) keeps the `Disposable` and calls
  `.dispose()`. The rest are fire-and-forget — permanent observer-list
  entries in the bus's subjects → retain captured `this` references →
  retain bean state → not GC'd even if the bean instance is "destroyed."

Mitigation: even calling `dispose()` is not enough — the `Disposable`
field itself retains the lambda; the field must be nulled out
([Zac Sweers — Disposables Can Cause Memory Leaks](https://www.zacsweers.dev/disposables-can-cause-memory-leaks/)).

## Options evaluated

Three structural options for "clean slate on logout":

### A. Full ApplicationContext recreate

`SpringRaplaClient.main()` owns the context in a loop; on logout,
dispose all JFrames, `ctx.close()`, build a new
`AnnotationConfigApplicationContext(SwingClientConfig.class)`, restart.

- **Pros:** Maximal cleanup — every bean is rebuilt, by definition no
  session state survives if `@PreDestroy` hooks are correct.
- **Cons:** Highest implementation risk in general — but the only known
  AWT-static registration (`EventQueue.push` from PRD 052 commit
  `a24c6443`) is removed in Phase 1a, leaving zero AWT-static
  registrations in the client tier. Residual audit set: scheduler-queued
  tasks, listeners on parent-like singletons (`ClientFacade`,
  `RemoteOperator`), per-bean RxJava disposal — same as B.
- **Memory leak risk vs goal:** After Phase 1a, comparable to B for
  the listener surface we've actually found.

### B. Parent + child Spring context split

Parent (infrastructure, built once, never closed): `RemoteOperator`,
`ClientFacade`, `ObjectMapper`, `RaplaResources`, `CommandScheduler`,
`RaplaClientServiceImpl`. Child (session, recreated per login):
`Application`, `RaplaMenuBar`, all presenters, `CalendarSelectionModel`,
edit controllers, `RaplaEventBus`.

- **Pros:** Same "clean slate" semantics as A in practice but the audit
  surface is smaller (child-context beans only — ~20). Infrastructure
  beans (HTTP pool, ObjectMapper) survive — faster login. Less risky
  because parent boundary is explicit and any latent parent-bean leak
  doesn't compound across logouts.
- **Cons:** Requires bean taxonomy decision per bean. One-time but
  non-trivial.
- **Memory leak risk vs goal:** Lowest of the three.

### C. Explicit `SessionLifecycle` contract (no context split)

Add `interface SessionScoped { onLogin(User); onLogout(); }`; relevant
beans implement it; a `SessionLifecycle` singleton fires `onLogout()`
on every registered bean from `ClientFacadeImpl.logout()`. Beans clear
their own state.

- **Pros:** No Spring lifecycle change. Incremental — pay only for beans
  we touch. Works inside the current single-context arrangement.
- **Cons:** Discovery-driven — we won't know we missed a bean until a
  heap dump or a bug surfaces. No "by construction" guarantee.
- **Memory leak risk vs goal:** Bounded only by how thorough the audit
  is — same shape as A, but spread across N beans we have to find.

### RxJava sub-strategies (independent of A/B/C)

The bus problem has its own three options:

- **R1.** Move `RaplaEventBus` into session-scope (requires A or B).
- **R2.** Keep bus as singleton; on logout, `subject.onComplete()` then
  recreate the subjects. Works in C, also as a transition step.
- **R3.** Per-bean `@PreDestroy` discipline that disposes every
  subscription and nulls the field; CI grep-check for fire-and-forget
  `.subscribe(...)`.

R1 is cleanest and "free" if we already do A or B. R2 is the compatible
intermediate step. R3 is hardening.

## Recommendation (selected)

**A (full ApplicationContext close + recreate), executed in four
phases.** Phase 1a's removal of the only known AWT-static registration
neutralises A's main historical leak risk; what remains in A's favour
is by-construction safety for the entire RxJava + listener surface
(bus, scheduler-queued work, session→parent listener registrations).
B+R1 was the earlier recommendation when the EDT handler still lived
as JVM-global state; that's no longer true after Phase 1a.

Trade-off accepted with A: ~0.5–2s slower re-login due to Spring
context rebuild. Acceptable for a Swing desktop app where OAuth
roundtrip dominates login time.

1. **Phase 0 — Baseline measurement.** Heap-dump comparison: fresh
   login as admin → switch to non-admin → switch back → snapshot dump
   at each step. Identify dominator-tree retainers. This validates that
   the cleanup work pays off and tells us which beans to prioritise.
2. **Phase 1a — Remove the JVM-global EDT exception handler.** Delete
   the `EventQueue.push(...)` block (lines 198-223) from
   `RaplaClientServiceImpl.initialize()`. Introduce
   `org.rapla.client.swing.SwingSafe.invokeLater(Runnable, Logger)`
   that wraps the runnable with try/catch and routes throwables to the
   rapla logger. Replace the ~13 `SwingUtilities.invokeLater(...)`
   sites in the OAuth + logout/restart paths
   (`RaplaClientServiceImpl`; the former `OAuthCallbackPasteDialog`
   has been removed — see PRD 029 status) with the
   helper. Master-shape `initialize()` recovered; **zero** AWT-static
   listener registrations remain in the client tier. Independent of
   A — can land first.
3. **Phase 1b — Minimal R2 step on RaplaEventBus.** Add `@PreDestroy`
   and explicit `reset()` to `RaplaEventBus`; wire
   `ClientFacadeImpl.logout()` to call `reset()`. Becomes redundant
   when Phase 2 lands (A makes the bus die with `ctx.close()` anyway —
   the `@PreDestroy` still fires for free). Lands first so the
   most-visible RxJava leak is patched even before Phase 2.
4. **Phase 2 — Context close+recreate loop in `SpringRaplaClient.main()`.**
   Refactor `main()` to own the `AnnotationConfigApplicationContext`
   lifecycle. `RaplaClientServiceImpl.logout()` signals the launcher
   (via `CountDownLatch` or `ApplicationEvent`) to close the context
   and rebuild a fresh one. Before `ctx.close()`, a `disposeAllFrames()`
   hook iterates `Frame.getFrames()` and calls `.dispose()` so AWT
   releases its strong refs. Add a CI grep rule that fails on any new
   `Toolkit.getDefaultToolkit().getSystemEventQueue().push`,
   `UIManager.addPropertyChangeListener`,
   `KeyboardFocusManager.addPropertyChangeListener`, or
   `Toolkit.addAWTEventListener` outside the allowed (zero) callsites.

Phases 1a and 1b are non-controversial and small; they can land
independently of the Phase 2 decision. Phase 2 is the architecture
change.

## Plan

### Phase 0 — Baseline measurement (½ day)

- Launch fresh client (`mvn exec:java`), capture heap-dump as admin
  via `jcmd <pid> GC.heap_dump baseline-admin.hprof` after first GC.
- Logout, login as non-admin, force GC, dump as `after-switch.hprof`.
- Login as admin again, dump as `back-to-admin.hprof`.
- Open both in Eclipse MAT; diff dominator trees; identify any User /
  Reservation / Allocatable instances that survived logout.
- Record findings in this PRD (Open Questions section).

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
- Replace `SwingUtilities.invokeLater(...)` with `SwingSafe.invokeLater(..., logger)`
  at the call sites in OAuth + logout/restart paths
  (`RaplaClientServiceImpl`; the `OAuthCallbackPasteDialog` callsite
  no longer exists — the paste fallback was removed because it wasn't
  practical, see PRD 029 status).
- Delete `RaplaClientServiceImpl.initialize()` lines 198-223
  (the `Toolkit.getDefaultToolkit().getSystemEventQueue().push(...)` block).
- `initialize()` should now match master's shape — only the
  `Thread.setDefaultUncaughtExceptionHandler` block remains.
- Test: a tier-2 unit test that invokes `SwingSafe.invokeLater(throwing-runnable, logger)`
  and asserts `logger.error(...)` was called.
- Manual test: trigger an OAuth login, force a callback-side exception
  (e.g. close the OAuth dialog before the callback arrives), verify the
  error appears in `logs/rapla-client.log` instead of `System.err`.

### Phase 1b — RaplaEventBus disposal (1–2 hours)

- `RaplaEventBus.java`: add `@PreDestroy` that calls `onComplete()` on
  both subjects. Add `reset()` that completes the current subjects and
  recreates them via `Observables.createPublisher(scheduler.getExecutor())`.
- `ClientFacadeImpl.logout()`: before `cache.clearAll()`, look up
  `RaplaEventBus` and call `reset()`. Inject via constructor.
- Test: a tier-2 unit test that subscribes to the bus, calls `reset()`,
  asserts the subscriber received `onComplete()`, asserts a new
  subscription after reset still receives events.
- Manual test: existing dev-client logout/login cycle, verify no
  regression in app behavior.

### Phase 2 — Context close+recreate loop in `SpringRaplaClient.main()` (1–2 days)

User decisions 2026-05-22:
- Signal mechanism: `BlockingQueue<NextSession>` (not `CountDownLatch`)
  because the signal must carry the next session's `ConnectInfo` to
  support switch-to-user.
- PRD 051 alignment: switch-to-user and switch-back also ride the same
  close+recreate path. One mechanism handles all three transitions;
  switch-to-user gets the same clean-slate guarantee as logout (admin's
  cached working set doesn't bleed into the impersonation view).

Implementation:

- New `org.rapla.client.NextSession` carrier (record-style):
  ```java
  public record NextSession(ConnectInfo info, boolean exit) {
      public static NextSession showLoginDialog() { return new NextSession(null, false); }
      public static NextSession exit()            { return new NextSession(null, true); }
      public static NextSession reconnectAs(ConnectInfo info) { return new NextSession(info, false); }
  }
  ```
- New `LogoutSignal` bean (one per context, single-slot blocking queue):
  ```java
  @Service
  public class LogoutSignal {
      private final BlockingQueue<NextSession> queue = new ArrayBlockingQueue<>(1);
      public void next(NextSession ns) { queue.offer(ns); }
      public NextSession take() throws InterruptedException { return queue.take(); }
  }
  ```
- `RaplaClientServiceImpl`:
  - `logout()` → `eventBus.reset()` + `logoutSignal.next(NextSession.showLoginDialog())`. Drop the `SwingSafe.invokeLater(this::start)` self-restart (line 1338) — `main()` drives the rebuild now.
  - `switchTo(User target)` (PRD 051) → `logoutSignal.next(NextSession.reconnectAs(impersonationConnectInfo))`. Replaces in-place token swap.
  - `switchBack()` → `logoutSignal.next(NextSession.reconnectAs(savedAdminConnectInfo))`.
  - "Exit Rapla" menu action → `logoutSignal.next(NextSession.exit())` to break out of `main()`'s loop cleanly.
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
- Add a CI grep rule (`bash` test in `rapla-app/src/test/...`) that
  fails when any of these appear outside an empty allowlist:
  - `Toolkit.getDefaultToolkit().getSystemEventQueue().push`
  - `UIManager.addPropertyChangeListener`
  - `KeyboardFocusManager.addPropertyChangeListener`
  - `Toolkit.addAWTEventListener`
- Verify `RemoteOperator.disconnect()`'s `cache.clearAll()` still runs
  before context close (the operator dies with the context anyway, but
  any persistent server-side state should be released cleanly).
- Heap verification (per Task #10, deferred to post-Phase-2): confirm
  no growth across admin↔non-admin↔switch-to-user cycles on the
  ~/git/dhbwrapla heavy-data deployment.

## Tests

- **Tier 2 unit test for SwingSafe** — invoke a throwing runnable via
  `SwingSafe.invokeLater(...)`; assert the logger received the error
  and the test still terminates. Lands with Phase 1a.
- **Tier 2 unit test for RaplaEventBus** — subscribe, reset, confirm
  completion + fresh-subject behaviour. Lands with Phase 1b.
- **Tier 2 menu gating test** — construct `RaplaMenuBar` against three
  user permission levels (full admin / group-only admin / plain
  non-admin) and assert which entries appear. Independent of restart
  work but locks in the gating rules the restart fix must preserve.
- **Manual repro of the original bug** — fresh client, login non-admin,
  logout, login admin → admin menu entries appear. Reverse direction
  too. Phase 2 must pass this manually.
- **Heap-retention regression** — after Phase 2, two full
  admin↔non-admin cycles must not grow the post-GC heap by more than X%
  (threshold to be set during Phase 0 baseline).
- **Login-speed regression** — measure
  `AnnotationConfigApplicationContext.refresh()` time on a cold and on
  a second iteration of the main() loop. Document baseline so we
  notice if it grows unexpectedly after future bean additions.
- **AWT-static grep CI rule** — fails on any introduction of
  `EventQueue.push` / `UIManager.addPropertyChangeListener` /
  `KeyboardFocusManager.addPropertyChangeListener` /
  `Toolkit.addAWTEventListener` outside an allowlist (currently empty).
  Lands with Phase 2.

## Open Questions

- **OQ1:** Phase 0 finding — is the heap actually retained across
  logout, and if so by which dominator? Resolves whether Phase 2 is
  worth the effort, or Phases 1a + 1b are already enough.
- **OQ2:** Does `CommandScheduler` need an explicit `cancelAll()` API
  before context close, or does Spring's `@PreDestroy` chain on the
  scheduler bean already shut down its executor cleanly? With option A
  the scheduler is destroyed with the context, so this is mostly about
  whether pending tasks throw or get silently dropped during teardown.
- **OQ3:** Login-speed measurement — confirm the ~0.5–2s rebuild
  estimate for `AnnotationConfigApplicationContext.refresh()` on the
  full `SwingClientConfig`. If it's much worse than estimated, B+R1
  becomes attractive again on UX grounds.
- **OQ4:** Should we also clear per-session state out of static fields
  in `RaplaImages`, `RaplaWidget`, etc.? Probably out of scope —
  static caches are not per-user — but worth verifying during Phase 0.
- **OQ5:** ~~Logout signal mechanism~~ **Resolved 2026-05-22:**
  `BlockingQueue<NextSession>` chosen over `CountDownLatch` because the
  signal must carry next-session `ConnectInfo` for switch-to-user.
- **OQ6:** ~~PRD 051 interaction~~ **Resolved 2026-05-22:**
  Switch-to-user and switch-back ride the same close+recreate channel
  as logout. PRD 051's previously-drafted in-place token swap is
  superseded — switching is a session change, not a token swap.
  PRD 051 needs an update to reflect the new design when its
  implementation starts.
