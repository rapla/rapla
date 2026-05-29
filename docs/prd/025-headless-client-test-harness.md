# PRD 025 — Headless client test harness

**Status:** draft
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-11

## Goal

Add a `HeadlessPresenterTestSupport` base class (peer of `FacadeTestSupport`) so that **presenter and pure-model classes carved out by PRD 023 can be unit-tested without spinning up Swing, EDT, or a Spring context**.

The harness:

1. Provides a real `RaplaFacade` over the existing `testdefault.xml` fixture (via `FacadeTestSupport` reuse).
2. Provides a `MockView` test double for any `*View` interface, with recorded method calls and a fluent assertion API.
3. Provides a deterministic `Clock` / `today` injection point.
4. Gives presenter tests a sub-second median runtime (tier 2 per AGENTS.md §10).

## Why this is needed now

1. **PRD 023 produces classes with no tier-2 home.** Carved-out presenters (e.g. `ReservationEditPresenter` in 023 Phase 6) need a real facade for `facade.edit(...)` / `facade.store(...)` and a fake view for `view.show(...)` / `view.showWarning(...)`. Without a shared harness, every test class hand-rolls the same fixture.
2. **Existing client-side tests are tier-4 (full Swing GUI)** — e.g. `UndoTests` boots a real `JDialog`. Slow (~5–10 s each) and fragile (need `$DISPLAY` or Xvfb). PRD 023 shouldn't inherit that.
3. **The MockView pattern is small but worth standardising.** Without a shared `RecordingView`, every test writes the same `boolean shown; void show(...) { shown = true; }` boilerplate.
4. **PRD 020 already proved the value at field-renderer scale** — `FieldRendererTest` (10 tests, ~150 ms total) is the only client code with proper tier-1 unit tests. Same for presenters.

## Scope

### In scope

1. **`HeadlessPresenterTestSupport`** in `rapla-server/src/test/java/org/rapla/test/util/` (sibling of `FacadeTestSupport`):
   - JUnit 5 `@TempDir` + `@BeforeEach`.
   - Loads `testdefault.xml`, builds `RaplaFacade` (reusing `FacadeTestSupport`'s wiring).
   - Exposes `MutableClock` so tests can pin `today()`.
   - Exposes `RaplaResources` test stub returning `key` for any i18n lookup, so labels are deterministic.
   - Helper: `User defaultAdmin()` / `User newUser(String role)`.
2. **`RecordingView<P>`** generic test double, same package. Records every method call on the View interface (small reflective proxy) into a `List<ViewCall>`. Assertion helpers: `view.assertCalled("showWarning").withArgs(...)`, `view.assertNeverCalled("hide")`, `view.lastCall()`. Optional typed sub-class: `RecordingReservationView extends RecordingView<ReservationView.Presenter> implements ReservationView`.
3. **Documentation**: add a section to `docs/architecture/mvp-pattern.md` (per PRD 022) showing the standard test layout:

   ```java
   class ReservationEditPresenterTest extends HeadlessPresenterTestSupport {
       RecordingReservationView view;
       ReservationEditPresenter presenter;

       @BeforeEach
       void setUp() {
           view = new RecordingReservationView();
           presenter = new ReservationEditPresenter(facade, clientFacade,
               logger, locale, eventBus, view);
       }

       @Test
       void editingExistingReservationLoadsAppointments() throws Exception {
           Reservation r = facade.newReservation(...);
           facade.store(r);
           presenter.edit(r, /*isNew=*/false);
           view.assertCalled("show").withArg(r);
       }
   }
   ```
4. **One worked-example test** per PRD 023 phase, using the harness, to prove the pattern. Migrate as 023 lands.

### Out of scope

- **No GUI test framework adoption** (AssertJ Swing, Mockito GUI). The point is to **not** instantiate Swing in tier 2.
- **No async / reactive testing utility.** If a presenter uses `Promise<T>`, tests await directly via `promise.get(Duration.ofSeconds(1))`. No `awaitility` dep.
- **No mocking framework dependency.** `RecordingView` is a hand-rolled reflective proxy; keeps dep graph minimal.
- **No coverage of existing tier-4 GUI tests.** `UndoTests` etc. keep running as `@Tag("e2e")` per AGENTS.md §10.

## Plan

### Phase 1 — `HeadlessPresenterTestSupport` (≈2 days)

1. Subclass / sibling of `FacadeTestSupport`. Share the temp-dir fixture loading.
2. Add `MutableClock` and i18n stub.
3. One smoke test (`HeadlessPresenterTestSupportTest`, 3 cases): facade boots, clock is mutable, i18n stub returns the key.

### Phase 2 — `RecordingView<P>` (≈2 days)

1. Reflective proxy implementing an arbitrary View interface.
2. Records each call as `ViewCall(method, args, timestamp)`.
3. Fluent assertion API: `assertCalled`, `assertNeverCalled`, `lastCall`, `callsFor("methodName")`.
4. `RecordingViewTest` (8 cases) against a small sample interface (e.g. `MenuView`).

### Phase 3 — First production use: `ReservationEditPresenterTest` (≈1 day)

Lands with PRD 023 Phase 6. ~10 tests covering the production presenter end-to-end via facade + recording view.

### Phase 4 — Documentation (≈1 day)

1. `docs/architecture/mvp-pattern.md` (or `extension-points.md` if 022's structure is locked in) section on the test pattern.
2. Update PRD 017 tier-table with: "Headless presenter / pure-model — tier 2 — extends `HeadlessPresenterTestSupport` — 200–400 ms per test".

## Tests

- `HeadlessPresenterTestSupportTest` (tier 2): facade boots, clock mutable, temp-dir cleanup OK.
- `RecordingViewTest` (tier 1): proxy records, assertions fire.
- Worked examples (Phase 3 + every PRD 023 phase).

The harness itself is test infrastructure; CI catches drift via worked-example tests breaking.

## Risks

1. **Reflective proxy overhead.** A `java.lang.reflect.Proxy` per test adds ~1 ms; tests target sub-second; non-issue.
2. **Facade fixture mutation.** Tests sharing the temp-dir copy could mutate each other. `FacadeTestSupport` already creates a fresh facade per `@Test`; we don't change that.
3. **i18n stub vs real bundle.** A presenter building user-visible strings via i18n + business logic (rare) would be tested against keys, not text. If a test wants real text, instantiate `RaplaResources` properly; the stub is a default.
4. **JUnit 4 lingerers.** Some old client tests still use JUnit 4. Harness is JUnit 5 only. Don't mix in the same class. No migration of legacy tests here.

## Open Questions

1. **`RecordingView` in rapla-core or rapla-server `test/`?** If presenter tests need a base class, `rapla-server/src/test/java/` (where `FacadeTestSupport` lives) is the natural home — but rapla-client tests can't depend without making rapla-server a `test`-scope dep. Lean: copy `RecordingView` into both module test trees, **or** publish in a small `rapla-test-support` test-jar. Decide before Phase 2.
2. **MutableClock vs `RaplaClock`.** Rapla has its own date utilities (`DateTools`, `RaplaLocale`). Existing clock injection point? If yes, reuse; if no, add one.
3. **Include a `RecordingEventBus`?** Presenters that publish `ApplicationEvent`s would benefit. Lean: yes, add in Phase 2.

## Cross-references

| PRD | Relationship |
|---|---|
| **023** presenter / model carve-out | **Primary consumer.** Each phase of 023 lands one worked-example test. |
| **024** server-side edit services | Indirect consumer. Pure models in rapla-core are testable with plain JUnit; MockMvc tests for controllers don't use this harness. Harness is for **client-side presenter** tests. |
| **017** test coverage strategy | Adds new row to tier-pyramid table. |
| **022** architecture documentation | MVP / test-pattern page references this harness. |
| **AGENTS.md §10** | Tier-2 harness, consistent with `FacadeTestSupport`. |
