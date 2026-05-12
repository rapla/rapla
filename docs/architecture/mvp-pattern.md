# MVP / carve-out pattern

How Rapla separates pure-logic models from Swing components, how
presenter/view pairs are wired, and how to test them headlessly. This is
the pattern PRD 023 carved out and PRDs 024–025 build on. Read
[reservation-edit.md](reservation-edit.md) for the concrete dialog and
[overview.md](overview.md) for the module split before this page if you
need broader context.

## The three layers

```
+----------------------------+
|  pure model / validator    |    rapla-core (no Swing, no AWT, no facade)
|  RepeatingRuleModel        |    fully tier-1 testable
|  AllocationConflictModel   |    reusable from server REST + Angular
|  NameSearchMatcher         |
+----------------------------+
            ^
            |  POJOs only
            |
+----------------------------+
|  Presenter                 |    rapla-client (no Swing import)
|  CalendarPlacePresenter    |    depends on RaplaFacade + View interface
|  (ReservationEditPresenter)|    no JPanel / JTable / EDT
|     — PRD 023 P6, TBD      |    tier-2 testable via FacadeTestSupport
+----------------------------+
            ^  setPresenter / view callbacks
            |
+----------------------------+
|  View interface            |    rapla-client (no Swing import in interface)
|  CalendarPlaceView         |    inner Presenter interface declares the
|  ReservationView          |    events the view forwards to the presenter
+----------------------------+
            ^  implements
            |
+----------------------------+
|  Swing adapter             |    rapla-client/.../swing
|  ReservationEditImpl       |    holds JPanel / JTable / JButtons
|  CalendarPlaceViewSwing    |    forwards events to the presenter
+----------------------------+
```

**The invariant:** the top two layers must compile without `javax.swing.*`
or `java.awt.*` on the classpath. The model layer must additionally
compile without `org.rapla.facade.*` — only entity / value / util types
from rapla-core are allowed.

## What's where today

Pure-logic models in `rapla-core/src/main/java/`:

| Package | Class | What it owns |
|---|---|---|
| `org.rapla.client.edit.reservation` | `RepeatingRuleProjector` | UI-state projection for the repeating-appointment editor: panel visibility, ending mode, day-chooser bucket, weekday selection (initial & on-anchor-shift), exception-button label, `RepeatingChoice` enum + `choiceFor` / `repeatingTypeFor` for the type↔radio-button mapping |
| `org.rapla.client.edit.reservation` | `RepeatingRuleModel` + `RepeatingRuleWriter` + `RepeatingRuleValidator` | Recurrence rule + clamping rules + advisory validator |
| `org.rapla.client.edit.reservation` | `AllocationConflictModel` | Per-allocatable conflict + permission-filter compute |
| `org.rapla.client.edit.reservation` | `AllocatableRowStatusModel` | Picker row-status (AVAILABLE / NOT_ALWAYS_AVAILABLE / REQUEST / CONFLICT / FORBIDDEN) decision — carved out of `AllocatableSelection.getIcon` |
| `org.rapla.client.edit.reservation` | `ClassificationFieldVisibility` | `(visible, writable)` decision per classification attribute, multi-edit-conservative — carved out of `ClassificationEditUI.createEditField` |
| `org.rapla.client.edit.reservation` | `ReservationEditSelection` | Mutable + original reservations, flattened appointments, transient name-search term |
| `org.rapla.client.edit.filter` | `ClassificationFilterOperators` | `AttributeType` ↔ operator-string ↔ JComboBox index catalog |
| `org.rapla.client.edit.search` | `NameSearchMatcher` | Substring matcher with case folding, diacritic folding, ß→ss, multi-word AND |
| `org.rapla.client.menu` | `PasswordChangePolicy` | `canChangePassword` / `requiresOldPassword` / `validate` — carved out of `PasswordChangeAction` |
| `org.rapla.client.menu` | `RaplaObjectActionPolicy` | `isEnabled` decision for NEW/EDIT/DELETE/EDIT_SELECTION/DELETE_SELECTION — carved out of `RaplaObjectActions`. Takes lambda predicates so tier-1 stubbing doesn't need the full `PermissionController` |
| `org.rapla.plugin.calendarview` | `CalendarLayoutEngine` | Strategy + groupBy → `CalendarPage` |
| `org.rapla.plugin.reservationedit` | DTOs (`RecurrenceRule`, `ConflictCheckRequest`, …) | Wire shapes for PRD 024 endpoints |

Presenters in `rapla-client/src/main/java/`:

| Package | Class | View interface | Status |
|---|---|---|---|
| `org.rapla.client` | `CalendarPlacePresenter` | `CalendarPlaceView` | production, well-shaped |
| `org.rapla.client.internal` | `ResourceSelectionPresenter` | `ResourceSelectionView` | production |
| `org.rapla.client.internal` | `ConflictSelectionPresenter` | `ConflictSelectionView` | production |
| `org.rapla.client.internal` | `RequestSelectionPresenter` | `ResourceRequestSelectionView` | production |
| `org.rapla.client.internal.edit` | `EditTaskPresenter` | inner `EditTaskView` | production |
| ~~`org.rapla.client.edit.reservation.sample`~~ | — | — | **Removed 2026-05-11.** The sample was a dead-code MVP draft kept as a potential promotion target; PRD 023 Phase 6 was re-aimed because the Angular client (PRD 026/028) will not mirror the Swing edit flow. The sample presenter shape would not have been reused, so it's gone. Git history retains it. |

Swing view adapters live under `org.rapla.client.swing.*` and end in
`...Swing` or `...Impl` (e.g. `CalendarPlaceViewSwing`,
`ResourceSelectionViewSwing`, `ReservationEditImpl`).

## House conventions

### Pure models

- Live in `rapla-core`. No Swing, no AWT, no facade. Verified by an
  ArchUnit / `grep`-based CI test (per PRD 023 §"Architecture test").
- Records or plain classes with constructor injection. No DI annotations
  on the model itself — the consumer (Swing adapter, server controller)
  owns instantiation.
- Static utility methods (`AllocationConflictModel.compute(...)`,
  `RepeatingRuleProjector.exceptionButtonState(...)`) when the function
  is genuinely pure. Instance fields only when there's mutable state
  worth keeping coherent (`ReservationEditSelection`).
- All branches covered by tier-1 tests in
  `rapla-core/src/test/java/...`. Target ≥ 80 % branch coverage.

### Presenter ↔ view contract

- Inner `Presenter` interface nested in the view: see
  `CalendarPlaceView.Presenter`, `MenuView.Presenter`, the sample
  `ReservationView.Presenter`. View raises an event → calls
  `presenter.something(...)`.
- View constructor takes no arguments specific to the presenter; the
  presenter constructs the view (or has one injected) and calls
  `view.setPresenter(this)` after.
- Constructor injection only (AGENTS.md §4). No field `@Inject` /
  `@Autowired`.

### Swing adapter

- Single responsibility: build the widget tree, bind widgets to
  presenter calls, render model state. **Owns no business logic.**
- When the Swing class extends `RaplaGUIComponent` (the legacy base —
  ~64 classes still do), the new code in the adapter should NOT add
  more logic that depends on `RaplaGUIComponent` utilities; reach for
  the pure model first.
- The adapter is the **only** layer that may import `javax.swing.*`.

## Testing presenters and models

### Tier 1 — pure-logic models

Plain JUnit 5 in `rapla-core/src/test/java/`. No Spring, no facade.
Examples:

- `RepeatingRuleProjectorTest` (44 tests)
- `AllocationConflictModelTest` (10 tests) — uses `java.lang.reflect.Proxy`
  to stub `Allocatable`, `Reservation`, `Appointment` interfaces (only the
  4–6 methods the model actually calls). Subclasses `PermissionController`
  with `canAllocate` overridden.
- `RaplaObjectActionPolicyTest` (15 tests) — uses the lambda-predicate
  overload (`BiPredicate<Entity, User>` for `canModify`, `Predicate<User>`
  for `isRegisterer`) so tests don't need to construct or subclass the
  full `PermissionController` (whose `isRegisterer` is final). The
  production overload bridges to the real controller.
- `PasswordChangePolicyTest` (13 tests) — stubs `User` via Proxy.
- `NameSearchMatcherTest`, `ClassificationFilterOperatorsTest`,
  `RepeatingRuleWriterTest`, `RepeatingRuleValidatorTest`,
  `CalendarLayoutEngineTest` — same shape.

### Test-only `PermissionController` stubbing

The real `PermissionController` is a final-ish class with a heavy
constructor. For tier-1 tests there are three patterns, in order of
preference:

1. **Lambda-predicate overload** — when designing a new policy, accept
   small `BiPredicate` / `Predicate` parameters instead of the full
   controller, and provide a bridge overload that takes the real
   controller. Pattern: `RaplaObjectActionPolicy.isEnabled(...)`.
2. **Subclass with `canModify` override** — when the policy only
   touches non-final methods of `PermissionController`, an anonymous
   subclass with `new PermissionController(Set.of(), stubOperator())`
   works. Pattern: `AllocationConflictModelTest.permitAll()`.
3. **Tier-2 with `FacadeTestSupport`** — fall back to this when the
   policy needs many controller methods or a real entity graph.
   Pattern: `PasswordChangePolicyHarnessTest`.

### Tier 2 — presenters

Extend `HeadlessPresenterTestSupport` (in `rapla-server/src/test/java/org/rapla/test/util/`).
This gives you:

| Field | What it is |
|---|---|
| `facade` | Real `RaplaFacade` over a temp-dir copy of `testdefault.xml`. Each `@Test` gets a fresh facade. |
| `clock` | `MutableClock` — pin `today()` per test for date-sensitive logic. |
| `logger`, `operator` | Inherited from `FacadeTestSupport`. |

Plus `RecordingView<V>` — a reflective proxy that implements any view
interface and records every method call. Use:

```java
class ReservationEditPresenterTest extends HeadlessPresenterTestSupport {
    RecordingView<ReservationView> view;
    ReservationEditPresenter presenter;

    @BeforeEach
    void wirePresenter() {
        view = RecordingView.of(ReservationView.class);
        presenter = new ReservationEditPresenter(facade, view.proxy(), clock);
    }

    @Test
    void editingExistingReservationCallsShow() throws Exception {
        Reservation r = facade.newReservation(...);
        facade.store(r);
        presenter.edit(r, /*isNew=*/ false);
        view.assertCalled("show");
        view.lastCall("show").arg(0);   // → the Reservation passed
    }
}
```

Assertion helpers on `RecordingView`:

- `assertCalled(method)` / `assertNeverCalled(method)`
- `callsFor(method)` → `List<Call>`
- `lastCall(method)`, `firstCall(method)`, `callCount(method)`
- `stub(method, value)` to return a canned value for the view's
  non-void methods (default is `null` / `0` / `false`)
- `clearCalls()` to reset between scenarios within one test

See `RecordingViewTest` (12 cases) for the recorder semantics.

### Tier 3 — REST controllers

`@SpringBootTest` + `@AutoConfigureMockMvc` + the same `testdefault.xml`
fixture. Examples:

- `CalendarViewControllerIntegrationTest` (8 cases)
- `ReservationEditControllerIntegrationTest` (11 cases)
- `PreferencesAdminControllerIntegrationTest` (11 cases — PRD 020)

Every new id- or filter-taking endpoint **must** include a leak-probe
test per AGENTS.md §12 (see "Permission discipline" below).

## Permission discipline (AGENTS.md §12)

When the Swing in-process path moves to a REST endpoint, the server
must only return what the user could already see in the Swing client.
The pattern:

```java
@Override
@GetMapping("/...")
public Page list(@RequestParam(...) List<String> ids) throws RaplaException {
    User user = session.checkAndGetUser(request);
    PermissionController pc = facade.getPermissionController();
    List<Entity> resolved = new ArrayList<>();
    for (String id : ids) {
        Entity e = facade.tryResolve(new ReferenceInfo<>(id, Entity.class));
        if (e == null) continue;            // unknown id — silently dropped
        if (!pc.canRead(e, user)) continue; // unreadable — silently dropped
        resolved.add(e);
    }
    // …
}
```

Both "unknown" and "can't read" branches must produce indistinguishable
responses — no error message, no length differentiator beyond "the known
ones are there", no HTTP status differentiation. A user must not be
able to **probe for existence** by watching which ids echo back.

Reference implementations:

- `CalendarViewController.resolveResourceFilter`
  (rapla-server/.../web/) — the canonical example. The class JavaDoc
  spells out the probe risk.
- `ReservationEditController.checkConflicts` — same pattern applied to
  the conflict-check endpoint.

Reference tests:

- `CalendarViewControllerIntegrationTest` —
  `unknownAllocatableIdIsSilentlyDroppedFromResourceColumns`,
  `unknownAllocatableIdAlsoDroppedForNonAdmin`,
  `onlyUnknownAllocatablesYieldEmptyColumnsNotAnError`.
- `ReservationEditControllerIntegrationTest` —
  `checkConflictsUnknownAllocatableSilentlyDropped`,
  `checkConflictsOnlyUnknownIdsReturnsEmptyOutcomesNotError`.

## Adding a new carve-out

1. **Identify the pure logic.** A method or a small cluster of methods
   inside a Swing class that doesn't touch `JComponent` / `JTable` /
   `EDT`. If it depends on `RaplaGUIComponent` utilities (i18n,
   formatters), pass those as parameters; don't inherit them.
2. **Move it to `rapla-core/src/main/java/org/rapla/client/edit/<topic>/`.**
   Use a record output if the function returns multiple values.
3. **Write tier-1 tests** in the mirror package under
   `rapla-core/src/test/java/`. Stub entity interfaces via
   `java.lang.reflect.Proxy` when a real `*Impl` would pull in
   DynamicType wiring.
4. **Make the Swing class delegate.** The shape:

   ```java
   private SomeOutcome compute(SomeInput x) {
       return SomeModel.compute(x, dependencyA, dependencyB);
   }
   ```

5. **Run the existing GUI tests** (`UndoTests`, `CalendarEditorTest`)
   to catch regressions.

## What this pattern is NOT

- **Not** a framework adoption (no Spring MVC, no JavaFX MVC, no
  GWT-MVP runtime). The pattern is hand-rolled and stays that way.
- **Not** a justification to add `*View` interfaces retroactively to
  every Swing component. Add the View interface when there's a real
  presenter to pair it with; otherwise the interface is dead weight.
- **Not** a Swing→Web migration. The Swing renderers stay. The pattern
  exists so a future web renderer can reuse the model layer **and** so
  the existing Swing logic is testable today.

## See also

- [reservation-edit.md](reservation-edit.md) — the dialog this pattern
  was carved out of.
- [extension-points.md](extension-points.md) — `@Service` / `@Component`
  wiring on the client side (how presenters get instantiated).
- [permissions.md](permissions.md) — what `PermissionController`
  actually checks; the source of truth for §12.
- [PRD 023](../prd/023-presenter-view-extraction.md) — the original
  carve-out plan and what's still pending (Phase 6).
- [PRD 024](../prd/024-server-side-edit-services.md) — REST endpoints
  built on the carved-out models.
- [PRD 025](../prd/025-headless-client-test-harness.md) — the
  `HeadlessPresenterTestSupport` + `RecordingView` design.
