# PRD 002: Swing UI Spring DI Migration

**Status:** in-progress
**Date:** 2026-05-06
**Depends on:** PRD 001 (Spring Boot Migration) — Phase 4 step 5 (`ClientConfig` + `RemoteOperator` wired)

## Goal

Migrate the Swing UI tier (~142 files under `src/main/java/org/rapla/client/swing/`, 281 files total under `src/main/java/org/rapla/client/`) from the now-removed `restinject` annotation processor to Spring DI. After this PRD, the legacy `@Inject` field-injected Swing classes are reachable from `SpringRaplaClient`.

## Scope

- **In scope:** All `@DefaultImplementation` (32 files) and `@Extension` (58 files) Swing/client classes wired as Spring beans.
- **Out of scope:** GWT (already removed in PRD 001 Phase 7), Angular frontend (its own track), any visual/behavioral changes.

## Plan

### Phase 1 — `SwingClientConfig` skeleton

1. Create `org.rapla.client.spring.SwingClientConfig` `@Configuration` with `@ComponentScan(basePackages={"org.rapla.client.swing", "org.rapla.client.menu", "org.rapla.client.dialog", "org.rapla.client.internal"})`.
2. `SpringRaplaClient` constructor extended to accept `SwingClientConfig` alongside `ClientConfig` + `ClientProxyConfig`.

### Phase 2 — `@Service` annotations

Add `@Service` (alongside `@DefaultImplementation`) on each of the 32 default-impl classes via a single mechanical sed pass. The `jakarta.inject.Inject` constructors are honoured by Spring 6's JSR-330 support — no further changes needed for constructor-injected classes.

### Phase 3 — Resolve cascading bean errors

Each Swing component pulls in collaborators. Spring will fail at startup if any are missing. Fix order:

1. Run `SpringRaplaClientTest` and capture the missing-bean error.
2. Either `@Bean`-register the missing class or `@Service`-annotate it.
3. Repeat until the test passes.

### Phase 4 — Extension maps + sets

`Map<String, TaskPresenter>`, `Set<ClientExtension>`, etc. need `@Component`/`@Bean` registration with `@Named("id")` qualifiers. Each `@Extension(provides=X.class, id="Y")` becomes `@Component @Named("Y")` on the impl, and Spring's `Map<String, X>` injection populates the right map.

### Phase 5 — Field → constructor injection migration

Per `AGENTS.md` rule, every Swing class touched in Phases 2–4 should also migrate `@Inject` fields to constructor parameters in the same change.

### Phase 6 — `RaplaClientServiceImpl` lifecycle wiring

Final `@Bean RaplaClientServiceImpl` factory method that takes all 13 deps. Then `SpringRaplaClient` exposes `ClientService` (the Swing app entry).

## Tests

Each phase ends with `SpringRaplaClientTest` extended to assert the new beans resolve. After Phase 6, the test should be able to instantiate `RaplaClientServiceImpl` from a Spring context — proving the Swing tier boots end-to-end without the deleted `ClientCreator`.

## Implementation Status

### Phase 1 — `SwingClientConfig` skeleton (completed 2026-05-06)

- `src/main/java/org/rapla/client/spring/SwingClientConfig.java` (new) — `@Configuration @ComponentScan(basePackages={org.rapla.client.swing, org.rapla.client.menu, org.rapla.client.dialog, org.rapla.client.internal, org.rapla.client.event}, excludeFilters=REGEX(.*\\.server\\..*))`. Currently scans nothing for `@Service` purposes (no Swing classes have `@Service` yet beyond the one below).
- `SpringRaplaClient` no-arg constructor extended to `new AnnotationConfigApplicationContext(ClientConfig.class, ClientProxyConfig.class, SwingClientConfig.class)`.

### Phase 2 — `@Service` annotations (in progress, 45/32 — exceeded original count because we wired non-`@DefaultImplementation` leaves, nested factories, action classes, and `@Bean` factories beyond the original 32)

| Class | `@Service` added | Implements |
|-------|------------------|------------|
| `org.rapla.client.event.RaplaEventBus` | ✅ | `ApplicationEventBus`, `CalendarEventBus` |
| `org.rapla.client.swing.internal.view.TreeItemFactorySwing` | ✅ | `TreeItemFactory` |
| `org.rapla.client.swing.internal.RaplaDateRenderer` | ✅ | (DateRenderer) |
| `org.rapla.client.swing.internal.CalendarPlaceViewSwing` | ✅ | `CalendarPlaceView` |
| `org.rapla.client.swing.SwingActivityController` | ✅ | `AbstractActivityController` |
| `org.rapla.client.menu.swing.PasswordChangeSwingView` | ✅ | `PasswordChangeView` |
| `org.rapla.client.swing.internal.view.ComplexTreeCellRenderer` | ✅ | (TreeCellRenderer) |
| `org.rapla.client.swing.internal.view.ConflictTreeCellRenderer` | ✅ | (`@Inject` leaf, no `@DefaultImplementation`) |
| `org.rapla.client.dialog.swing.DialogUI.DialogUiFactory` | ✅ (nested) | `DialogUiFactoryInterface` |
| `org.rapla.client.internal.TreeFactoryImpl` | ✅ | `TreeFactory` |
| `org.rapla.client.swing.ConflictSelectionViewSwing` | ✅ | `ConflictSelectionView` |
| `org.rapla.client.swing.internal.RaplaMenuBarContainer` | ✅ | (`@Inject` leaf, `@Singleton`) |
| `IOInterface` (via `ClientConfig.ioInterface` `@Bean`) | ✅ (manual factory) | `DefaultIO` instance |
| `org.rapla.entities.domain.AppointmentFormater` (via `ClientConfig.appointmentFormater` `@Bean`) | ✅ (manual factory) | `AppointmentFormaterImpl(i18n, raplaLocale)` |
| `org.rapla.client.menu.swing.MenuItemFactorySwingImpl` | ✅ | `MenuItemFactory` (only needs `DialogUiFactoryInterface`) |
| `org.rapla.plugin.copyurl.swing.SwingURLCopyService` | ✅ | `URLCopyService` (only needs `IOInterface`) |
| `org.rapla.plugin.tableview.client.swing.SwingTableColumnFactory` | ✅ | `RaplaTableColumnFactory` (only needs `ClientFacade`) |
| `org.rapla.client.internal.check.swing.HolidayCheckDialogViewSwing` | ✅ | `HolidayCheckDialogView` (only needs `RaplaResources`) |
| `org.rapla.client.swing.internal.view.RequestTreeCellRenderer` | ✅ | (`@Inject` leaf — needs only `RaplaResources` + `RaplaLocale`) |
| `org.rapla.client.swing.ResourceRequestSelectionViewSwing` | ✅ | `ResourceRequestSelectionView` (now possible because `RequestTreeCellRenderer` is wired) |
| `org.rapla.client.internal.check.swing.ConflictDialogViewSwing` | ✅ | `ConflictDialogView` (deps `TreeFactory` + `ConflictTreeCellRenderer` both wired) |
| `org.rapla.client.swing.internal.view.ComplexTreeCellRenderer` | ✅ + `@Primary` | `TreeCellRenderer` (3 candidates: complex/conflict/request — `@Primary` resolves the default for `TreeCellRenderer` injection points) |
| `org.rapla.client.dialog.swing.ObjectSwingListView` | ✅ | `ListView` (needs `TreeCellRenderer` ✓ via `@Primary`, `CommandScheduler` ✓) |
| `org.rapla.client.internal.check.swing.DefaultCheckViewSwing` | ✅ | `CheckView` (no constructor deps) |
| `InfoFactoryImpl` (attempted) | ❌ reverted | constructor throws at bean instantiation — `RaplaGUIComponent` super-constructor accesses runtime state not available during eager bean wiring (same pattern as `RaplaSwingClipboard`) |
| `org.rapla.client.swing.toolkit.RaplaFrame` | ✅ | (`@Inject` leaf no-arg ctor `JFrame` — unblocks `ApplicationViewSwing` and similar containers that take a `RaplaFrame` directly) |
| `org.rapla.client.swing.toolkit.ErrorDialog` | ✅ | (`@Inject` leaf — needs `Logger`, `RaplaResources`, `DialogUiFactoryInterface` — all wired) |
| `org.rapla.client.swing.internal.view.LicenseUI` | ✅ | (`@Inject` no-arg leaf, `RaplaWidget` impl) |
| `org.rapla.client.swing.internal.view.LicenseInfoUI` | ✅ | (`@Inject` leaf — needs `RaplaResources`, `RaplaSystemInfo`, `DialogUiFactoryInterface`, `Provider<LicenseUI>` — all wired now that LicenseUI is a bean. Spring 6's JSR-330 support makes `jakarta.inject.Provider<T>` work natively when the underlying bean exists.) |
| `org.rapla.facade.CalendarSelectionModel` (via `ClientConfig.calendarSelectionModel` `@Bean`) | ✅ (manual factory) | `CalendarModelImpl(ClientFacade, RaplaLocale)` — the impl lives in `rapla-core` (`org.rapla.facade.internal`) which is outside the SwingClientConfig scan, so a `@Bean` factory is the cleanest registration. Unblocks `MultiCalendarPresenter` (whose remaining dep `MultiCalendarView` still needs wiring) and any `Set<CalendarModel>` injection point. |
| `org.rapla.client.swing.internal.edit.fields.DateField.DateFieldFactory` | ✅ (nested `@Service`) | inner factory — needs `ClientFacade`, `RaplaResources`, `RaplaLocale`, `Logger`, `DateRenderer` (✓ via `RaplaDateRenderer`), `IOInterface` (✓ via `@Bean`). Spring `@ComponentScan` picks up `@Service` on nested static classes. |
| `org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory` | ✅ (nested `@Service`) | only `ClientFacade`, `RaplaResources`, `RaplaLocale`, `Logger` |
| `org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory` | ✅ (nested `@Service`) | + `IOInterface` |
| `org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory` | ✅ (nested `@Service`) | + `IOInterface` |
| `org.rapla.client.swing.internal.FilterEditButton.FilterEditButtonFactory` | ✅ (nested `@Service`) | needs the 4 field factories above + `TreeFactory` (✓), `DialogUiFactoryInterface` (✓) — all dependencies now present, factory wires cleanly. **Major cascade unblocker.** |
| `org.rapla.client.swing.MultiCalendarViewSwing` | ✅ | `MultiCalendarView` (only dep is `FilterEditButtonFactory`, now wired) |
| `org.rapla.client.swing.internal.MultiCalendarPresenter` | ✅ + `@Lazy` | `CalendarContainer` — needs `Set<SwingViewFactory>` (Spring auto-injects empty set when no beans of that type exist), `MultiCalendarView` (✓), `CalendarSelectionModel` (✓ via `@Bean`), `DialogUiFactoryInterface` (✓). **`@Lazy` required:** the indirect ctor of `CalendarSelectionModel` (`CalendarModelImpl`) calls `clientFacade.getUser()` which throws "no user logged in" before login. With `@Lazy`, the bean is only instantiated on first dereference — tests pass without a logged-in user. |
| `org.rapla.client.internal.ConflictSelectionPresenter` | ✅ + `@Lazy` | `Presenter` — depends on `CalendarSelectionModel` (transitively triggers same login issue) so `@Lazy` for the same reason. |
| `org.rapla.client.internal.RequestSelectionPresenter` | ✅ + `@Lazy` | `ResourceRequestSelectionView.Presenter` — same `CalendarSelectionModel` dependency, same `@Lazy` rationale. |
| `org.rapla.client.internal.ResourceCalendarTask` | ✅ + `@Lazy` | `@Extension(provides=TaskPresenter)` — needs `Provider<CalendarContainer>` (✓ via `MultiCalendarPresenter`), `DialogUiFactoryInterface` (✓), `RaplaResources` (✓), `CommandScheduler` (✓). `@Lazy` because it eventually dereferences `CalendarContainer` which transitively wants the logged-in user. |
| `ClientConfig.calendarSelectionModel` `@Bean` (revisited) | ✅ + `@Lazy` | Marked `@Lazy` so the `CalendarModelImpl` ctor (which fails before login with `"no user logged in"`) is deferred until actually needed. |
| **`SpringRaplaClientTest` re-enabled** | ✅ | The `@Disabled("Bean wiring requires logged-in user")` was the marker left by the PRD 005 split. With the four `@Lazy` annotations above, the test now passes without a logged-in user — bean *definitions* land in the context but `CalendarModelImpl` is never instantiated. |
| `org.rapla.client.EditController` | ✅ | concrete `@Singleton` class — single dep `ApplicationEventBus` (✓). Pre-requisite for `RaplaMenuBar`, `ResourceSelectionPresenter`, and any class that wants the central edit dispatcher. |
| `org.rapla.client.swing.internal.common.RaplaSwingClipboard` | ✅ + `@Lazy` | `RaplaClipboard` impl. Earlier attempt was reverted because the parent ctor calls `facade.addModificationListener(this)` which requires `RemoteOperator` available. With `@Lazy`, the bean is constructed only on first dereference (post-login), avoiding the boot-time failure. |
| `org.rapla.client.swing.internal.view.InfoFactoryImpl` | ✅ + `@Lazy` | `InfoFactory`. Earlier reverted (RaplaGUIComponent super-ctor accesses runtime state). `@Lazy` retrofit defers instantiation. |
| `org.rapla.client.swing.internal.DeleteDialogSwing` | ✅ + `@Lazy` | `DeleteDialogInterface`. Was blocked by `InfoFactory` — now that `InfoFactoryImpl` is wired, this slots in (also `@Lazy` because it extends `RaplaGUIComponent`). |
| `org.rapla.client.internal.ReservationControllerImpl` | ✅ + `@Lazy` | `ReservationController` (deps `Provider<Set<EventCheck>>` = empty Set, `RaplaClipboard` ✓, `DeleteDialogInterface` ✓, `AppointmentFormater` ✓). |
| `org.rapla.client.menu.PasswordChangeAction` | ✅ + `@Scope("prototype")` | Action — created fresh per menu invocation. `Provider<PasswordChangeView>` resolves to the wired `PasswordChangeSwingView` bean. **Uses `@Scope("prototype")`**: each `Provider.get()` returns a fresh instance, matching legacy DI semantics. |
| `org.rapla.client.menu.UserAction` | ✅ + `@Scope("prototype")` | same pattern; needs `UserClientService` (REST proxy ✓), `EditController` ✓, `Provider<PasswordChangeAction>` ✓ |
| `org.rapla.client.menu.impl.AppointmentAction` | ✅ + `@Scope("prototype") @Lazy` | extends `RaplaComponent` (boot-time state) so `@Lazy`; needs `ReservationController` ✓, `InfoFactory` ✓ |
| `org.rapla.client.menu.impl.RaplaObjectActions` | ✅ + `@Scope("prototype")` | needs `EditController` ✓, `InfoFactory` ✓, `DeleteDialogInterface` ✓, `MenuItemFactory` ✓ |
| `org.rapla.client.menu.MenuFactoryImpl` | ✅ + `@Lazy` | `MenuFactory` — needs all 4 actions (✓ as Provider<>), `RaplaClipboard` ✓, `MenuItemFactory` ✓, `Set<ReservationWizardExtension>`/`Set<ObjectMenuFactory>` (auto-injected as empty sets), `CalendarSelectionModel` ✓ (lazy). |
| `org.rapla.client.swing.ResourceSelectionViewSwing` | ✅ + `@Lazy` | `ResourceSelectionView` — `MenuFactory`, `InfoFactory`, `FilterEditButtonFactory` all wired now |
| `org.rapla.client.internal.admin.client.swing.SwingTypeCategoryView` | ✅ + `@Lazy` | `TypeCategoryView` — needs `MenuFactory` ✓, `TreeFactory` ✓, `TreeCellRenderer` ✓ |
| `org.rapla.client.internal.admin.client.swing.SwingUserGroupsView` | ✅ + `@Lazy` | `AdminUserUserGroupsView` — same deps as above |

### Pattern for prototype-scoped action classes

Action classes like `UserAction`, `AppointmentAction`, `PasswordChangeAction`, `RaplaObjectActions` are typically constructed *fresh per menu invocation* — they hold short-lived state (the selected object, the popup context). The legacy DI used `Provider<UserAction>` to mean "give me a new one each time".

In Spring, the equivalent is `@Service @Scope("prototype")`: each `provider.get()` from `jakarta.inject.Provider<T>` returns a freshly-constructed instance. Without `@Scope("prototype")`, Spring's default is singleton — `Provider.get()` would return the same instance, which would conflate state across invocations.

**Decision rule:** if the legacy code uses `@Inject Provider<T>` to obtain `T`, mark `T` as `@Service @Scope("prototype")`. If the legacy code injects `T` directly, mark `T` as plain `@Service` (default singleton).

### Pattern for boot-time-state classes

Classes whose ctor (or super-ctor) calls `clientFacade.getUser()` / `facade.addModificationListener()` / similar runtime-state methods fail eager Spring instantiation in tests that haven't logged in. Three workable approaches:

1. **`@Lazy`** on the bean: definition registered, instantiation deferred until first dereference. Cleanest fix, no behavior change at runtime once login completes.
2. **`@Lazy` on the *injection point*** (the field/parameter that takes the problematic dep). More targeted, but requires changes at every consumer.
3. **`ObjectProvider<T>`/`Provider<T>`** at the consumer side: explicit lazy lookup. Useful when the dep is genuinely optional, but here we want eager wiring once login exists.

Option 1 is the default for this PRD. Option 2 is reserved for cases where one consumer truly is eager but the dep needs to lag.

`SpringRaplaClientTest` extended to assert `ApplicationEventBus` and `CalendarEventBus` resolve. `mvn test` → 35 tests passing across 8 Spring contexts (incl. PRD 001-A `DateToolsLocalDateTimeTest`).

**Cascade lesson learned 2026-05-06:** a Python-script mass-add of `@Service` to all 24 remaining `@DefaultImplementation` Swing classes broke the test. Each Swing class transitively depends on non-`@DefaultImplementation` `@Inject` collaborators (`ConflictTreeCellRenderer`, `DialogUiFactoryInterface` impl, `ResourceSelectionViewSwing` parts, `RaplaImages`, etc.) that aren't yet Spring-managed. When all 24 were activated at once, the cascade of missing-bean errors swamped the build. The mass-add was reverted; only the 7 leaf classes above remain `@Service`-annotated.

**Pattern going forward:** each new `@Service` requires the full transitive dep cone to be Spring-managed first. The order should be:
1. Find a `@DefaultImplementation` candidate where every constructor parameter resolves to an existing bean.
2. Add `@Service`.
3. Run `SpringRaplaClientTest` to verify.
4. If new beans are needed (`@Inject`-decorated leaf classes that are *not* `@DefaultImplementation`), add `@Service` to those first.

This is genuinely voluminous work — easily another 30+ iterations to wire all 25 remaining `@DefaultImplementation` classes plus their `@Inject`-only collaborators. PRD-defined sub-phases (3 = resolve cascades, 4 = `@Named` extension maps, 5 = constructor injection migration, 6 = `RaplaClientServiceImpl` wiring) all progress in parallel as new beans land.

## Open Questions

- **`@Named("id")` extension key collisions** — restinject used compile-time generation to ensure unique IDs across plugins. Spring won't catch a duplicate at scan time. Audit needed.
- **Per-Swing-component scope** — most legacy classes were `@Singleton`. Some dialog/dialog-presenter classes need to be `@Scope("prototype")` since each opened dialog gets a fresh instance. Decide per class.
- **Component-scan filter for server packages** — Swing client must not pick up server-only beans even if they share parent packages. Use `@ComponentScan.Filter(type=ASSIGNABLE_TYPE, classes={...})` exclusions or restrict `basePackages`.
