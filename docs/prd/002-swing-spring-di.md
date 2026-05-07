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

### Phase 2 — `@Service` annotations (in progress, 23/32)

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
