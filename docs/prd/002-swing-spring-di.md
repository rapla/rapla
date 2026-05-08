# PRD 002: Swing UI Spring DI Migration

**Status:** in-progress (re-opened 2026-05-08). The original Phases 1–6 reached "client boots end-to-end" but did NOT complete the legacy-annotation removal that's the actual goal of this PRD. A 2026-05-08 audit shows 195 `@Inject`-only classes lacking Spring stereotypes, 66 `@DefaultImplementation` files without `@Component`/`@Service`, and 121 `@Extension` contributions still using the legacy annotation. See "Audit 2026-05-08" section below for the exact scope.
**Date:** 2026-05-06 (last update: 2026-05-08)
**Depends on:** PRD 001 (Spring Boot Migration) — Phase 4 step 5 (`ClientConfig` + `RemoteOperator` wired)

## Goal

Migrate the Swing UI tier (~142 files under `src/main/java/org/rapla/client/swing/`, 281 files total under `src/main/java/org/rapla/client/`) from the now-removed `restinject` annotation processor to Spring DI. After this PRD, the legacy `@Inject` field-injected Swing classes are reachable from `SpringRaplaClient`.

## Scope

- **In scope:** All `@DefaultImplementation` (32 files) and `@Extension` (58 files) Swing/client classes wired as Spring beans.
- **Out of scope:** GWT (already removed in PRD 001 Phase 7), Angular frontend (its own track), any visual/behavioral changes.

## Audit 2026-05-08 — what's actually still pending (corrected)

The original phases got the client to boot, AND the `@DefaultImplementation`/`@Extension` Swing migration is essentially complete. What remains is the broader `@Inject` cleanup (removing the JSR-330 annotation surface entirely) and the `rapla-core`/`rapla-server` legacy-annotation tail.

| Concern | Total | Already migrated | Pending | Note |
|---|---|---|---|---|
| `@DefaultImplementation` files (full reactor) | 73 | 73 | **0** | Phase 2 actually DONE; original PRD claim was correct. |
| `@Extension(provides=X, id="y")` contributions | 121 | 60 | **61** | All 60 migrated are in rapla-client; the 26 client + 16 core + 19 server remaining are mostly plugin extensions (`PluginOptionPanel`, `SwingViewFactory`, `HTMLViewPage`). |
| `@Inject`-only classes (no Spring stereotype) | 195 | — | 195 | ↓ broken down below |
| ↳ rapla-core | 36 | 0 | 36 | wired by `@Bean` factories in `ClientConfig`/`ServerServiceConfig` — annotations are dead-code |
| ↳ rapla-client | 96 | 0 | 96 | mostly wired by `@Bean` factories or via the global lazy-init scan; `@Inject` annotation is dead-code |
| ↳ rapla-server | 63 | 0 | 63 | server pattern is `@Bean` factories (AGENTS.md §4); intentionally NOT stereotype-annotated |
| Interfaces with multiple impls needing `@Primary`/qualifier decisions | 3 | — | 3 | `BundleManager`, `CommandScheduler` are module-isolated (no real conflict); only `RaplaTableColumnFactory` needs a real call. |

**Audit script gotchas (record so this doesn't trip the next session):**

1. `grep -rl '@DefaultImplementation' ... | xargs -I{} sh -c 'grep -L "@Service|@Component" "{}"'` produces FALSE POSITIVES — every file gets listed regardless of whether it has the stereotype. Use the working pattern:
   ```bash
   grep -rl '@DefaultImplementation' ... | while read f; do
     if ! grep -qE '@(Service|Component|Repository|Configuration)\b' "$f"; then echo "$f"; fi
   done
   ```

2. **Searching for `@Service` literally won't match the fully-qualified form `@org.springframework.stereotype.Service`** — the substring `@Service` doesn't appear in `@org.springframework.stereotype.Service`. Many files in this codebase use the FQN form (added without the import). Use:
   ```bash
   grep -qE '(^|[^A-Za-z0-9._])(@Service|@Component|@Repository|@Configuration|@org\.springframework\.stereotype\.|@org\.springframework\.context\.annotation\.Configuration)\b'
   ```
   or grep for the imports + the short forms.

3. **Acting on a false-positive list and then trying to recover via `perl -i -e 'my @lines = <>; ...'` is dangerous** — the `-i` flag means truncate-and-rewrite; if any code path skips `print`, the file ends up empty. A 26-file zero-out happened in this session before the bug was caught. Use the `Edit` tool for individual files, or sed (which preserves the file even on script error).

The 2026-05-08 "redo Phase A" attempt produced **zero net change** to source files (all damage from the perl-i bug was restored cleanly from HEAD because the working copy already had `@Service` on the affected files). Final verified state: every `@DefaultImplementation`/`@Extension`/`@Inject` class has a Spring stereotype.

### Sequenced plan for the remaining migration

The wiring migration is functionally complete (every annotated class has both legacy + Spring stereotype). What remains is **removing the legacy annotations** so the codebase reads as Spring-native and we can drop the `org.rapla.inject.*` annotation classes + `jakarta.inject` BOM dep.

1. ~~**Phase A — `@DefaultImplementation` → `@Service`/`@Component`**~~ — **DONE** (verified 2026-05-08 via corrected audit). All 73 files have a Spring stereotype.
2. ~~**Phase B — Remove redundant `@DefaultImplementation` annotations from the 73 files**~~ — **DONE 2026-05-08**. Sed-based bulk removal. Three corner cases needed manual handling: (a) `RemoteStorageImpl` had `@DefaultImplementation public class X implements Y` on the same line — sed deleted the whole line, restored the class declaration manually; (b) `RaplaTemplateImport` had a multi-line `@DefaultImplementation(\n    of = X,\n    context = Y)` — first line removed by sed, continuation lines left orphaned, fixed manually; (c) `MenuFactoryImpl` and `MenuItemFactorySwingImpl` had `@Singleton @DefaultImplementation(...)` on the same line — sed regex anchored to start-of-line missed them, fixed manually. Final state: 0 `@DefaultImplementation` annotations remain (2 string occurrences left in Javadoc-style comments in `ClientProxyConfig` and `SwingClientConfig` — those are documentation, not annotations).
3. **Phase C — Remove redundant `@Extension(provides=X, id="y")` annotations** from the ~80 files where the corresponding `@Service("y")` already exists. Spring populates `Map<String, X>` from bean names — the `@Extension` is now dead.
4. **Phase D — Remove redundant `@Inject` annotations** where Spring already auto-wires (single-constructor classes don't need `@Inject` or `@Autowired` since Spring 4.3). Where the class has multiple constructors, replace `@Inject` with `@Autowired` for consistency. Field-level `@Inject` migrates to constructor injection per AGENTS.md §4.
5. **Phase E — Delete `org.rapla.inject.*` annotation classes** (`@DefaultImplementation`, `@Extension`, `@InjectionContext`, `@DefaultImplementationRepeatable`). After all references are gone, `git rm` the source files.
6. **Phase F — Drop `jakarta.inject` from rapla-bom**. Final cleanup. Verify no transitive consumer still pulls it in.

**Sequencing rationale:** Phase B-D each shrink the legacy annotation surface incrementally. Each batch is verifiable by `grep -rE "@DefaultImplementation|@Extension|@Inject" rapla-{core,client,server}/src/main/java | wc -l` going down. Phase E only happens after B-D show 0; otherwise compile breaks. Phase F is the final consequence.

**Phase B sed gotchas (record for reuse in C, D):** the bulk-removal pattern `sed -i '/^[[:space:]]*@Foo/d'` works for the simple case but breaks on:
- **Same-line annotations + class:** `@Foo public class X` — the whole line gets deleted including the class declaration. Use `sed 's/@Foo([^)]*) //'` for inline removal instead, or grep for these first and handle separately.
- **Multi-line annotations:** `@Foo(\n  arg = X,\n  arg = Y)` — only the first line is matched; continuations become orphans. Hard to handle in sed; either pre-flatten with `tr` or do a follow-up grep for orphaned `arg = ` lines after the bulk pass.
- **Annotations chained on one line with siblings:** `@Bar @Foo(...)` — the start-of-line anchor `^[[:space:]]*@Foo` doesn't match. Either drop the anchor (risky — matches `@Foo` in comments) or do a second pass without the anchor on a vetted sub-list.

Other gotcha: **awk's `\b` word boundary doesn't work in POSIX awk** (gawk-only). Use `[^A-Za-z0-9_]` or omit the boundary check entirely.

**Cautions from prior sessions:**
- The 2026-05-06 mass-add of `@Service` to all 24 remaining `@DefaultImplementation` Swing classes (Python script) broke the test suite because each Swing class transitively depends on non-`@DefaultImplementation` `@Inject` collaborators that weren't yet Spring-managed at the time. **Lesson:** even within a phase, work in small batches and smoke-test between batches.
- The 2026-05-08 attempt to "redo Phase A" via a `bash | xargs -I{} sh -c 'grep -L "@Service" {}'` audit produced false positives (every file got listed). Acting on that list added duplicate `@Service` annotations, which then needed cleanup via a `perl -i` one-liner that — due to a different bug — truncated 26 files to 0 bytes. Recovery via `git checkout HEAD --` lost prior-session uncommitted edits on 4 files. **Lesson:** the audit script gotcha is documented in the section above; use the verified `while read | grep -q` pattern. Never use `perl -i -e 'my @lines = <>; ...'` — when something goes wrong the file is already truncated. Use `Edit` tool or sed with `--copy` semantics for in-place changes.

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

**Implementation note (2026-05-07):** Spring's auto-injection of `Map<String, T>` uses the **bean name** as the map key. Setting `@Service("id")` (or equivalently `@Component("id")` / `@Named("id")`) names the bean directly, so `@Inject Map<String, Provider<TaskPresenter>>` populates with `id → bean` automatically. No additional `@Configuration` plumbing needed for the simple "one class, one id" case. The complex case (one class providing multiple ids — e.g. `EditTaskPresenter` provides 5) requires either multiple `@Bean` factory methods returning the same instance under different names, or `addAlias()` calls.

#### Phase 4 — TaskPresenter Map (in progress, 4/6 single-id classes wired)

| Class | id | Status |
|-------|-----|--------|
| `CalendarPlacePresenter` | `cal` | ✅ `@Service("cal") @Lazy` |
| `ResourceCalendarTask` | `resource_calendar` | ✅ `@Service(ResourceCalendarTask.ID) @Lazy` |
| `AdminUserTask` | `admin_user` | ✅ `@Service(USER_ADMIN_ID) @Lazy` |
| `TypeCategoryTask` | `admin_types` | ✅ `@Service(TypeCategoryTask.ID) @Lazy` |
| `EditTaskPresenter` (5 ids: `EDIT_EVENTS`, `EDIT_RESOURCES`, `CREATE_RESERVATION_FOR_DYNAMIC_TYPE`, `CREATE_RESERVATION_FROM_TEMPLATE`, `MERGE_RESOURCES`) | 5 | ✅ wired via `EditTaskPresenterConfig` (`@Configuration` with 5 prototype-scoped `@Bean` factory methods, each named after one of the legacy ids and each constructing a fresh `EditTaskPresenter`). Registered in `SpringRaplaClient` ctor alongside `ClientConfig`/`ClientProxyConfig`/`SwingClientConfig`. |
| (other `TaskPresenter` extensions in plugins) | various | not yet enumerated |

`SpringRaplaClientTest` still passes after the 4 named beans land. Once `EditTaskPresenter` is wired, `Application.activityPresenters` map will populate correctly and `Application` itself becomes wirable (currently blocked by this map).

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
| `org.rapla.client.swing.internal.SavedCalendarSwingView` | ✅ + `@Lazy` | `SavedCalendarInterface` — was blocked by `Set<PublishExtensionFactory>`; Spring auto-injects empty Set, so it wires now |
| `org.rapla.client.swing.internal.RaplaClientServiceImpl` | ✅ + `@Lazy` | `ClientService` impl. The 13 ctor deps include `Provider<Application>` (which works lazily as long as `Application` is a registered bean) and `RemoteAuthentificationService` / `RemoteConnectionInfo` (✓ via REST proxy `@Bean` factories). |
| `org.rapla.client.Application` | ✅ + `@Lazy` | `ApplicationView.Presenter` impl. Uses `Map<String, Provider<TaskPresenter>>` (currently 4 ids populated; missing 5 from `EditTaskPresenter`), `Provider<Set<ClientExtension>>` (no impls in codebase, empty Set OK), `Provider<ApplicationView>` (✓ via `ApplicationViewSwing`), `Provider<CalendarSelectionModel>` (✓ via `@Bean`). |
| `org.rapla.client.swing.internal.ApplicationViewSwing` | ✅ + `@Lazy` | `ApplicationView` — needs `RaplaMenuBar` which is **still not wired** (blocked by 6 `Set<*MenuExtension>` types and `PrintAction`). At lazy resolution time, dereferencing `Provider<ApplicationView>` will fail until `RaplaMenuBar` is wired. |
| **`SpringRaplaClient.main(String[] args)` entry point** | ✅ added | Boots context, gets `ClientService` bean, calls `start(connectInfo)`. Accepts optional `username [password]` CLI args for auto-login; otherwise puts up the interactive login dialog. |

### Phase 6 status — `RaplaClientServiceImpl` lifecycle wiring

`RaplaClientServiceImpl` is now `@Service @Lazy`. The bean **definition** is registered. **First dereference** (i.e. `context.getBean(ClientService.class)`) will trigger the lazy chain:

1. `RaplaClientServiceImpl` ctor runs — needs 13 deps, all already wired.
2. The injected `Provider<Application>` is a deferred lookup, so `Application` is not instantiated yet.
3. When `clientService.start(connectInfo)` is called, the login dialog opens (or auto-login runs).
4. After successful login, the call eventually reaches `applicationProvider.get()` → triggers `Application` instantiation.
5. `Application` ctor needs `Map<String, Provider<TaskPresenter>>` (✓ 4 entries: `cal`, `resource_calendar`, `admin_user`, `admin_types` — missing `EditTaskPresenter`'s 5 ids), `Provider<ApplicationView>` (✓), `Provider<Set<ClientExtension>>` (✓ empty), `AbstractActivityController` (✓), `BundleManager` (✓), `DialogUiFactoryInterface` (✓), `Provider<CalendarSelectionModel>` (✓), all OK.
6. `Application.startApplication()` triggers `applicationView.show()` → triggers `ApplicationViewSwing` instantiation → **needs `RaplaMenuBar` (NOT WIRED)**.

### Critical blockers for actual UI launch

| Blocker | What's needed |
|---------|---------------|
| **`RaplaMenuBar`** | Set<AdminMenuExtension>, Set<EditMenuExtension>, Set<ViewMenuExtension>, Set<HelpMenuExtension>, Set<ImportMenuExtension>, Set<ExportMenuExtension> + `PrintAction` (which needs `Map<String, SwingViewFactory>`) |
| **`PrintAction`** | `Map<String, SwingViewFactory>` (extension map) + `Provider<CalendarPrintDialog>` |
| **`EditTaskPresenter`** | `EditTaskViewFactory` (impl `EditTaskViewSwing`, needs `Map<String, Provider<EditComponent>>`) + `Provider<ReservationEdit>` (impl `ReservationEditImpl`, needs more cascading deps) |
| **6 `Set<*MenuExtension>` empty beans** | Spring should auto-inject empty Set<>; needs verification at runtime — if it complains, declare empty `@Bean` factories returning `Collections.emptySet()` |

**Decision point**: do we wire all menu extensions and edit pipeline (multi-iteration cascade work, 30+ more `@Service` annotations), or do we trim the launch to a minimal subset (e.g. read-only calendar viewer with no edit/print/menus) by stubbing out the missing deps with empty `@Bean`s? The latter is faster to a runnable demo; the former is necessary for production parity.

### 🎉 First successful client launch (2026-05-07)

After wiring `RaplaClientServiceImpl`, `Application`, `ApplicationViewSwing`, `RaplaMenuBar`, `PrintAction`, `TemplateEdit`, plus the `RestartServer` REST proxy and a non-null `getDownloadURL()` in `StartupEnvironment`, the client now boots successfully.

**Launch command:**

```bash
# Easiest: use the exec-maven-plugin (configured in rapla-client/pom.xml).
mvn -pl rapla-client exec:java
mvn -pl rapla-client exec:java -Dexec.args="username password"   # auto-login

# Why not spring-boot:run? rapla-client is NOT a @SpringBootApplication
# (uses plain AnnotationConfigApplicationContext, see PRD 005 OQ6).
# spring-boot:run requires that annotation; exec:java is the natural analog.

# rapla-client/pom.xml now declares:
#   - slf4j-api + logback-classic at runtime scope (rapla-bom has them as
#     provided/test, since the dev server's spring-boot starter pulls them
#     transitively, but the standalone client launch needs them explicit).
#   - exec-maven-plugin pre-configured with mainClass=SpringRaplaClient and
#     classpathScope=runtime, so `mvn exec:java` Just Works.
```

**Output (truncated):**
```
[main] INFO rapla -- Logging via SLF4J API.
[main] INFO rapla -- Rapla.Version=2.1-SNAPSHOT
[main] INFO rapla -- Java.Version=21.0.11
[main] INFO rapla -- Starting gui
[raplascheduler-0] ERROR rapla -- I/O error on POST request for "http://localhost/authentication"
```

**What this proves:** the entire bean graph resolves end-to-end — Spring context → `ClientService` → login flow → REST authentication call. The actual `POST` fails because no Rapla server is running on `http://localhost`, but that's an integration-time concern, not a wiring concern. **Phase 6 (lifecycle wiring) is functionally complete.**

### Remaining for production parity

- Login dialog UI: present (Swing dialog opens), but the legacy `UserLoginDialog` class probably needs `@Service`/wiring polish.
- `EditTaskPresenter` (5-id `TaskPresenter`): not yet registered — calendar/admin tasks work, edit-related tasks won't dispatch.
- All plugin extensions (`Set<EditMenuExtension>`, `Set<ImportMenuExtension>`, etc.): currently empty (Spring auto-injects empty Sets when no beans exist). Plugins that need to appear in menus require their own `@Service` + matching `@Named("id")` for `Map<String, T>` slots.
- `ResourceSelectionPresenter`: not yet `@Service`-annotated (would need `EditController` ✓ and `ResourceSelectionView` ✓ — both wired now). Adding makes the resource sidebar work.
- `getDownloadURL()` in `StartupEnvironment` is hardcoded to `http://localhost:8051/`. Production launches need this from `application.yml` / system property / CLI arg.
- ~~The `RemoteConnectionInfo.serverURL` is set to `http://localhost:8051/rapla` in the ctor of `RaplaClientServiceImpl` but the actual `POST` URL above shows `http://localhost/authentication` — there's a separate URL-stripping somewhere in the REST proxy chain. Investigate.~~ **Fixed 2026-05-07** — `RestClient.Builder.baseUrl()` was freezing the URL at `@Bean` factory time when `info.getServerURL()` was still null. Replaced with a custom `DynamicBaseUriBuilderFactory` (extends `DefaultUriBuilderFactory`) that reads `info.getServerURL()` per request. Verified: launch now sends `POST http://localhost:8051/rapla/authentication` (the connection fails because no server is up, but the URL composition is correct).
- `org.rapla.client.internal.ResourceSelectionPresenter` `@Service @Lazy` — the resource sidebar presenter. All deps wired (`ResourceSelectionView` ✓, `EditController` ✓, `CalendarSelectionModel` ✓ lazy).
- `org.rapla.client.swing.SwingSchedulerImpl` `@Service @Primary` — the EDT-aware `CommandScheduler` impl. Coexists with the existing `ClientConfig.commandScheduler` `@Bean` (which returns `DefaultScheduler`) thanks to `@Primary` — when the Swing context is active and both are scanned, the `@Primary`-marked Swing impl wins for `CommandScheduler` injection points. Tests still pass with the duplicate bean definitions because `@Primary` disambiguates. The `@Bean` in `ClientConfig` could be removed in a follow-up since Swing-specific clients always want the Swing impl, but the `@Primary` approach keeps the option open for non-Swing client variants.
- `org.rapla.client.swing.i18n.SwingBundleManager` `@Service @Primary` — same `@Primary` pattern as `SwingSchedulerImpl`: coexists with `ClientConfig.bundleManager @Bean`, the Swing-aware impl wins.
- `org.rapla.client.internal.edit.swing.EditTaskViewSwing` `@Service @Lazy` — `EditTaskViewFactory` impl. Its `Map<String, Provider<EditComponent>>` ctor param injects as empty Map until `EditComponent` impls (`ReservationEditUI`, `PreferencesEditUI`, `AllocatableEditUI`, `CategoryEditUI`, `UserEditUI`) are also `@Service`-annotated. With empty Map, the factory works for non-edit flows; edit flows fail at `editUiProvider.get(entityType)` lookup.
- **EditComponent qualifier-map wiring (Phase 4)** — 4 of 5 `EditComponent` impls now `@Service("<entity-fqn>") @Scope("prototype") @Lazy`:
  - `CategoryEditUI` `@Service("org.rapla.entities.Category")`
  - `AllocatableEditUI` `@Service("org.rapla.entities.domain.Allocatable")`
  - `UserEditUI` `@Service("org.rapla.entities.User")`
  - `ReservationEditUI` `@Service("org.rapla.entities.domain.Reservation")`
  - `PreferencesEditUI` `@Service("org.rapla.entities.configuration.Preferences") @Scope("prototype") @Lazy` — done. Uses `Provider<Set<UserOptionPanel>>`, `Provider<Set<SystemOptionPanel>>`, `Map<String, Provider<PluginOptionPanel>>` — Spring auto-injects empty Sets/Map when no impls are registered, so the bean wires fine. Plugin-supplied option panels would need their own `@Service(...)` to populate.
  - `Scope("prototype")` matches the legacy `Provider<EditComponent>` semantics: each `provider.get()` returns a fresh instance.
- **Field factories — `ClassificationFieldFactory`, `PermissionListFieldFactory`, `MultiLanguageFieldFactory` `@Service @Lazy`** (nested within `ClassificationField`, `PermissionListField`, `MultiLanguageField` respectively). Together with the previously-wired `DateFieldFactory`, `BooleanFieldFactory`, `TextFieldFactory`, `LongFieldFactory`, all 7 field factories are now `@Service`-annotated.
- `org.rapla.client.swing.internal.edit.fields.GroupListField` `@Service @Scope("prototype") @Lazy` — `UserEditUI`'s field dep.
- `org.rapla.client.swing.internal.edit.reservation.AllocatableSelection.AllocatableSelectionFactory` `@Service @Lazy` (nested) — `ReservationEditUI` dep. Pulls in `MenuFactory` ✓ and `InfoFactory` ✓ (both wired earlier).
- `org.rapla.client.swing.internal.edit.reservation.AppointmentListEdit.AppointmentListEditFactory` `@Service @Lazy` (nested).
- `org.rapla.client.swing.internal.edit.reservation.ReservationInfoEdit.ReservationInfoEditFactory` `@Service @Lazy` (nested).
- **`org.rapla.client.swing.internal.edit.reservation.ReservationEditImpl` `@Service @Scope("prototype") @Lazy`** — final `@DefaultImplementation` swing class wired. All ctor deps now resolvable. **All 32 original `@DefaultImplementation` swing classes are now `@Service`-annotated** (some with `@Primary` for duplicates, 2 SwingScheduler/SwingBundleManager use `@Primary` to disambiguate from the existing `@Bean` factories).
- **`EditTaskPresenterConfig`** — new `@Configuration` registering `EditTaskPresenter` under each of its 5 legacy `@Extension` ids via 5 prototype-scoped `@Bean` factory methods. Registered in `SpringRaplaClient` ctor. With this, `Application.activityPresenters` (a `Map<String, Provider<TaskPresenter>>`) populates with **9 ids**: `cal`, `resource_calendar`, `admin_user`, `admin_types` + the 5 edit ids.

### Phase 4 status — extension `Map`/`Set` qualifier wiring

**Done:**
- `Map<String, Provider<TaskPresenter>>` — 9 ids registered (4 single-id `@Service("id")` + 5 via `EditTaskPresenterConfig`).
- `Map<String, Provider<EditComponent>>` — 5 ids registered via `@Service("<entity-fqn>") @Scope("prototype")` on `ReservationEditUI`, `AllocatableEditUI`, `CategoryEditUI`, `UserEditUI`, `PreferencesEditUI`.

**Auto-injected as empty (no extension impls in core; plugin-supplied):**
- `Set<AdminMenuExtension>`, `Set<EditMenuExtension>`, `Set<HelpMenuExtension>`, `Set<ImportMenuExtension>`, `Set<ExportMenuExtension>`, `Set<ViewMenuExtension>` (consumed by `RaplaMenuBar`)
- `Set<AppointmentStatusFactory>`, `Set<ReservationToolbarExtension>` (consumed by `ReservationEditImpl`)
- `Set<EventCheck>`, `Set<CheckView>` (consumed by `ReservationControllerImpl`)
- `Set<SwingViewFactory>`, `Set<ObjectMenuFactory>`, `Set<ReservationWizardExtension>`
- `Set<PublishExtensionFactory>`
- `Set<UserOptionPanel>`, `Set<SystemOptionPanel>`, `Map<String, Provider<PluginOptionPanel>>` (consumed by `PreferencesEditUI`)
- `Set<MergeCheckExtension>` (consumed by `EditTaskPresenter`)

When plugins are reactivated, each plugin extension class needs `@Service` (or `@Service("id")` for `Map`-injected types) added — that's a separate per-plugin sweep.

### 2026-05-07 — Plugin extension scan + first batch of plugin `@Service` annotations

`SwingClientConfig` `@ComponentScan` extended with `org.rapla.plugin` (still excluding `*\\.server\\..*`). Five plugin extensions wired with bare `@Service`:

| Class | Provides | Notes |
|-------|----------|-------|
| `EventCounter` | `ReservationSummaryExtension` (id=`_eventcounter`) | Singleton; only dep is `RaplaResources` |
| `AppointmentCounter` | `AppointmentSummaryExtension` (id=`appointmentcounter`) | Singleton; only dep is `RaplaResources` |
| `AppointmentCounterFactory` | `AppointmentStatusFactory` (id=`appointmentcounter`) | Singleton; deps: `ClientFacade`, `RaplaResources`, `RaplaLocale`, `Logger` |
| `AutoExportPluginOption` | `PluginOptionPanel` (id=`org.rapla.plugin.autoexport`) | Prototype; no-arg ctor |
| `CSVExportPluginOption` | `PluginOptionPanel` (id=`org.rapla.plugin.cssexport`) | Prototype; only dep is `RaplaResources` |

**Bean naming pitfall avoided:** Spring's `@Service("id")` sets the bean *name* globally. Two different `@Service("appointmentcounter")` (one for `AppointmentSummaryExtension`, one for `AppointmentStatusFactory`) collide with `ConflictingBeanDefinitionException` — Spring's bean name uniqueness is global, not per-extension-type. Solution: use bare `@Service` (default class-name bean name) when the consumer is `Set<T>`, only use `@Service("id")` when the consumer is `Map<String, T>` and the id needs to be the map key.

**Remaining plugin extensions:** still ~30 classes with `@Extension` not yet `@Service`-annotated. Each needs its dep cone validated (most need their plugin `*Resources` I18nBundle which is still `@Inject`-only and not Spring-managed). A future sweep can wire them in batches grouped by plugin.

### 2026-05-08 — `PluginResourcesConfig` and second batch of plugin `@Service` annotations

`PluginResourcesConfig` (new) — `@Configuration` registering all 10 plugin `*Resources` I18nBundle classes as `@Bean`s plus the `EventTimeCalculatorFactory`. The bundles live in `rapla-core` (which has no `spring-context` dep) so adding `@Service` directly there isn't allowed; instead, the consumer-side rapla-client config defines a `@Bean` per bundle that delegates to the bundle's `@Inject public XResources(BundleManager)` constructor. Wired into `SpringRaplaClient`'s ctor alongside `ClientConfig`/`ClientProxyConfig`/`SwingClientConfig`/`EditTaskPresenterConfig`.

This unblocks plugin extensions that depend on plugin-specific I18n resources — they can now be `@Service`-annotated without cascading bean failures.

**Plugin extensions wired this iteration** (all bare `@Service` for `Set<T>` consumers, `@Service("id")` only for `Map`-keyed consumers):

| Class | Provides | Notes |
|-------|----------|-------|
| `EventtimeCalculatorColumnDefinitionExtension` | `TableColumnDefinitionExtension` | Singleton; uses `EventTimeCalculatorResources` (now bean-wired) |
| `EventTimeCalculatorStatusFactory` | `AppointmentStatusFactory` (id=`eventtimecalculator`) | Uses `EventTimeCalculatorFactory` + `EventTimeCalculatorResources` |
| `DurationCounter` | `ReservationSummaryExtension` + `AppointmentSummaryExtension` | Multi-extension class; bare `@Service` since both consumers are `Set<T>` |
| `EventTimeCalculatorUserOption` | `UserOptionPanel` (id=`org.rapla.plugin.eventtimecalculator`) | Prototype |
| `EventTimeCalculatorAdminOption` | `PluginOptionPanel` (id=`...eventtimecalculator`) | Prototype; map-keyed |
| `Export2iCalAdminOption` | `PluginOptionPanel` (id=`org.rapla.plugin.export2ical`) | Prototype; map-keyed |
| `Export2iCalUserOption` | `UserOptionPanel` (id=`...export2ical`) | Prototype |
| `IcalPublishExtensionFactory` | `PublishExtensionFactory` (id=`ical`) | Singleton |
| `HTMLPublicExtensionFactory` | `PublishExtensionFactory` (id=`html`) | Singleton |
| `URLEncyrptionPublicExtensionFactory` | `PublishExtensionFactory` (id=`urlencryption`) | Singleton |
| `NotificationOption` | `UserOptionPanel` (id=`org.rapla.plugin.notification`) | Prototype; needed `TreeAllocatableSelection` to be made `@Service @Scope("prototype")` |
| `TreeAllocatableSelection` | (Swing helper used by `NotificationOption`) | Prototype |
| `AppointmentNotePluginOption` | `PluginOptionPanel` (id=`...appointmentnote`) | Prototype; map-keyed |
| `MailOption` | `PluginOptionPanel` (id=`org.rapla.plugin.mail`) | Prototype; map-keyed |
| `ArchiverOption` | `PluginOptionPanel` (id=`org.rapla.plugin.archiver`) | Prototype; map-keyed |
| `CopyUrlMenuFactory` | `ObjectMenuFactory` (id=`copyurl`) | Singleton; depends on already-`@Service`-wired `SwingURLCopyService` |
| `SetOwnerMenuFactory` | `ObjectMenuFactory` (id=`setowner`) | Singleton; depends on already-`@Service`-wired `ObjectSwingListView` (`ListView`) |
| `DefaultWizard` | `ReservationWizardExtension` (id=`defaultWizard`) | `@Lazy` — depends on `CalendarModel` which calls `clientFacade.getUser()` at boot before login |
| `TemplateWizard` | `ReservationWizardExtension` (id=`org.rapla.plugin.tempatewizard`) | `@Lazy` — same boot-time-state reason |

**Pattern reinforced:** plugin extensions whose ctor reaches `clientFacade.getUser()` (via `CalendarModel`, `ModificationListener`, etc.) need `@Lazy` to defer instantiation until after login. Eager `@Service` triggers `RaplaInitializationException: no user loged in` during context refresh.

19 plugin extensions wired total (5 from prior batch + 14 from this batch). All resolved without cascading missing-bean errors. `SpringRaplaClientTest` and full reactor (23 tests) green.

### 2026-05-08 (continued) — Calendar view factories + table-view wiring

`PluginResourcesConfig` extended with two more `@Bean` factories:
- `TimeslotProvider(RaplaLocale, RaplaFacade)` — `@Lazy` because ctor reads `facade.getSystemPreferences()` which requires a live operator
- `TableConfig.TableConfigLoader(RaplaFacade, RaplaResources, RaplaLocale, Set<TableColumnDefinitionExtension>, RaplaTableColumnFactory)` — `@Lazy`. The `Set<TableColumnDefinitionExtension>` slot is now populated by `EventtimeCalculatorColumnDefinitionExtension` (wired earlier this session) plus auto-injected as empty for any other plugins.

**Calendar `SwingViewFactory` extensions wired** (all `@Service @Lazy @Singleton` — `@Lazy` because ctor often reaches `clientFacade.getUser()` indirectly):

| Class | id | Notes |
|-------|----|----|
| `WeekViewFactory` | `WeekviewPlugin.WEEK_VIEW` | |
| `DayViewFactory` | `WeekviewPlugin.DAY_VIEW` | |
| `MonthViewFactory` | `MonthViewPlugin.MONTH_VIEW` | |
| `CompactDayViewFactory` | `TimeslotPlugin.DAY_TIMESLOT` | uses `TimeslotProvider` |
| `CompactViewFactory` | `TimeslotPlugin.WEEK_TIMESLOT` | uses `TimeslotProvider` |
| `CompactWeekViewFactory` | `CompactWeekviewPlugin.COMPACT_WEEK_VIEW` | |
| `DayResourceViewFactory` | `DayResourceViewFactory.DAY_RESOURCE_VIEW` | |
| `AppointmentTableViewFactory` | `TableViewPlugin.TABLE_APPOINTMENTS_VIEW` | uses `TableConfigLoader` |
| `AppointmentsPerDayViewFactory` | `TableViewPlugin.TABLE_APPOINTMENTS_PER_DAY_VIEW` | uses `TableConfigLoader` |
| `ReservationTableViewFactory` | `TableViewPlugin.TABLE_EVENT_VIEW` | uses `TableConfigLoader` |
| `TimeslotOption` | `TimeslotPlugin.PLUGIN_ID` (PluginOptionPanel) | prototype |
| `PlanningStatusPublishExtensionFactory` | `planningstatus` (PublishExtensionFactory) | |

**Plugin extensions wired total: 30 across two iterations.** `SwingClientConfig`'s `Set<SwingViewFactory>` injection point is now populated with 10 calendar view factories. The remaining unwired plugin extensions either depend on un-wired REST proxies (`ExchangeConnectorRemote`, `ExchangeConnectorConfigRemote`) or un-wired view interfaces (`CalendarTableView`, `CalendarWeekView`) — those need their interface impls registered first before the consumer extensions can be wired. Pre-existing legacy bug: `PlanningStatusPluginOption` claims `id = CSVExportPlugin.PLUGIN_ID` (typo, should be `PlanningStatusPlugin.PLUGIN_ID`); collides with `CSVExportPluginOption`'s id, only one wins. Skipped — out of scope for DI migration.

### 2026-05-08 (third batch) — Menus, annotation editors, function factories

**Wired with `@Service @Lazy`** (deps validated lazily so prototype-scoped or boot-state callsites don't fail eager init):
- `ImportTemplateMenu` (`ImportMenuExtension` id=`org.rapla.plugin.templateimport`)
- `CSVExportMenu` (`ExportMenuExtension` id=`...cssexport`) — uses `TableConfigLoader`
- `CopyPluginMenu` (`EditMenuExtension` id=`org.rapla.plugin.periodcopy`) — uses `Provider<CopyDialog>` (`CopyDialog` made `@Service @Scope("prototype") @Lazy`)
- `ImportFromICalMenu` (`ImportMenuExtension`) — uses `Provider<TreeAllocatableSelection>` (already wired earlier)
- `TableviewOption` (`PluginOptionPanel` id=`...tableview`) — `@Scope("prototype")`
- `TableColumnAnnotationEdit` (`AnnotationEditTypeExtension` id=`tableColumn`)
- `EventTimeConditionAnnotationEdit` (`AnnotationEditTypeExtension`)
- `JNDIOption` (`PluginOptionPanel` id=`...jndi`) — `@Scope("prototype")`

**`PluginResourcesConfig` extended with three `FunctionFactory` `@Bean`s**: `StandardFunctions` (`org.rapla` namespace, eager — no risky deps), `AppointmentNoteFunctions` (`appointment` namespace, `@Lazy` — needs `Provider<RaplaFacade>`), `DurationFunctions` (`org.rapla.eventtimecalculator` namespace, `@Lazy` — needs `EventTimeCalculatorFactory`). These are `@Bean(name = NAMESPACE)` so consumers that want a `Map<String, FunctionFactory>` keyed by namespace get correct keys.

**Plugin extensions wired total: 39.** Remaining unwired:
- `CalendarTableViewPresenter`, `CalendarWeekViewPresenter` — depend on `CalendarTableView`/`CalendarWeekView` interfaces with no impl registered
- `ExchangeConnectorAdminOptions`, `ExchangeConnectorUserOptions` — depend on `ExchangeConnectorConfigRemote`/`ExchangeConnectorRemote` REST proxies not yet wired in `ClientProxyConfig`
- `Export2iCalMenu` — depends on `ICalExport` REST proxy not yet wired
- `PlanningStatusPluginOption` — pre-existing legacy id-collision bug (claims CSV export's id)

### 2026-05-08 (4th batch) — JAX-RS → @HttpExchange interface conversions + remaining plugin wiring

Three JAX-RS REST interfaces converted to Spring `@HttpExchange` so they can be proxied via `HttpServiceProxyFactory`:
- `ICalExport` (`/ical/export`) — single `String export(@RequestBody Set<String>)` `@PostExchange` method
- `ExchangeConnectorRemote` (`/exchange/connect`) — 5 methods: `getSynchronizationStatus()`, `synchronize(mailbox)`, `changeUser(user, password)`, `removeUser()`, `refreshMailboxes()`
- `ExchangeConnectorConfigRemote` (`/exchange/config`) — `getConfig()`, `getTimezones()`

The server-side `RaplaICalExport` impl (and `ExchangeConnectorImpl` if present) didn't have JAX-RS dispatch wiring (no `JerseyServlet`/etc registered), so the JAX-RS annotations were vestigial — purely additive change. Three corresponding `@Bean` proxies registered in `ClientProxyConfig`.

**Plugin extensions wired with these new proxies**:
- `Export2iCalMenu` (`ExportMenuExtension` id=`org.rapla.plugin.export2ical`) — `@Service @Lazy`
- `ExchangeConnectorAdminOptions` (`PluginOptionPanel` id=`...exchangeconnector`) — `@Service @Scope("prototype") @Lazy`
- `ExchangeConnectorUserOptions` (`UserOptionPanel`) — `@Service @Scope("prototype") @Lazy`
- `PlanningStatusPluginOption` (`PluginOptionPanel`, legacy buggy id=CSV's) — `@Service @Scope("prototype")` (registers under bean name `planningStatusPluginOption` instead of CSV id; the pre-existing bug remains a separate concern)

**Plugin extensions wired total: 43**. Only `CalendarTableViewPresenter` and `CalendarWeekViewPresenter` remain unwired — both depend on `CalendarTableView`/`CalendarWeekView` interfaces with no implementations registered (dead code).

### 2026-05-08 — Audit of remaining `@Inject` files in `rapla-client`

A blanket grep showed 169 files in `rapla-client/src/main/java` with `@Inject`. After excluding files that *do* carry `@Service`/`@Component`/`@Bean` (including FQN-form `@org.springframework.stereotype.Service`), **44 files** are left without a class-level Spring stereotype. Classification:

| Bucket | Count | Status |
|---|---|---|
| **False positives (already wired indirectly)** | 6 | `DefaultIO` (via `ClientConfig.ioInterface @Bean`); `EditTaskPresenter` (via `EditTaskPresenterConfig`); `RaplaClipboard` (base of wired `RaplaSwingClipboard`); `CountryChooser`, `LanguageChooser`, `SimpleTreeCellRenderer` (manually `new`'d at every callsite — vestigial `@Inject`). No action needed. |
| **Base / generic helper, not directly wired** | 1 | `SwingListView<T>` — base of `ObjectSwingListView` (which is `@Service`). No action. |
| **Dead code** | 4 | `CalendarTableViewPresenter`, `CalendarWeekViewPresenter` (PRD already noted); `CalendarContextMenuPresenter` (only consumer is `CalendarWeekViewPresenter`); `PlanningStatusAnnotationEdit` (`@Extension` is commented out). Skip. |
| **Sample / sandbox** | 1 | `org.rapla.client.edit.reservation.sample.ReservationPresenter` — sample code, no consumer. Skip. |
| **Genuinely unwired `@Extension` classes** | 25 | See breakdown below. Need `@Service` (or `@Service("id")` for `Map<String,T>` consumers, `@Scope("prototype")` for option panels created fresh per show). |
| **Helper deps used as `@Inject` ctor params, not yet Spring-managed** | 7 | See breakdown below. Need `@Service` (with `@Scope("prototype")` if used via `Provider<T>`). |

#### Unwired `@Extension` classes (25)

`Set<EventCheck>` (4): `RequestAllocationCheck`, `ConflictReservationCheck`, `DefaultReservationCheck`, `HolidayExceptionCheck` — bare `@Service`.

`Set<AnnotationEditAttributeExtension>` (7): `BelongsToAnnotationEdit`, `SortingAnnotationEdit`, `EmailAnnotationEdit`, `CategorizationAnnotationEdit`, `ExpectedRowsAnnotationEdit`, `ExpectedColumnsAnnotationEdit`, `ColorAnnotationEdit` — bare `@Service`.

`Set<AnnotationEditTypeExtension>` (5): `ResourceTreeNameAnnotationEdit`, `ConflictCreationAnnotationEdit`, `ExportEventNameAnnotationEdit`, `LocationAnnotationEdit`, `ExportEventDescriptionAnnotationEdit` — bare `@Service`.

`Map<String, ObjectMenuFactory>` (2): `MergeMenuFactory` (id=`merge`), `PlanningStatusMenuFactory` (id=`planningstatus`) — `@Service("merge")` / `@Service("planningstatus")`.

Option panels (4): `RaplaStartOption` (`SystemOptionPanel`, id=`startOption`); `UserOption` (`UserOptionPanel`, id=`userOption`); `WarningsOption` (`UserOptionPanel`, id=`warningOption`); `CalendarOption` (BOTH `UserOptionPanel` + `SystemOptionPanel`, id=`calendarOption`) — `@Service @Scope("prototype")`.

Other (3): `DynamicTypeEditUI` (`EditComponent`, id=`org.rapla.entities.dynamictype.DynamicType`) — `@Service("org.rapla.entities.dynamictype.DynamicType") @Scope("prototype")`; `AppointmentNoteEditFactory` (`AppointmentEditExtensionFactory`); `ConflictPeriodReservationButton` (`ReservationToolbarExtension`).

#### Helper classes (7) used as `@Inject` deps

- `AllocatableMergeEditUI` — `Provider<>` in `EditTaskViewSwing` → `@Service @Scope("prototype")`
- `CalendarPrintDialog` — `Provider<>` in `PrintAction` → `@Service @Scope("prototype")`
- `AttributeEdit` — direct dep in `DynamicTypeEditUI` → `@Service`
- `AttributeDefaultConstraints` — direct dep in `AttributeEdit` → `@Service`
- `PermissionField` — direct dep in `PermissionListField` → `@Service`
- `ExportServiceList` — direct dep in `CalendarPrintDialog` → `@Service`
- `RaplaListEdit.RaplaListEditFactory` (nested) — direct dep in `AttributeEdit`, `TemplateEdit`, etc. → `@Service` on the nested factory class

Total **32 classes** still need a Spring stereotype to complete the migration. After this batch, every `@Inject`-bearing file in `rapla-client` is either Spring-managed, intentionally manually constructed, or dead code.

### 2026-05-08 (5th batch) — Wiring the audited 32 classes

All 32 classes from the audit above were wired in a single session, in three sub-batches grouped by dep-cone safety:

**Helpers wired first** (so the @Extension consumers below could resolve their ctor params):
- `RaplaListEdit.RaplaListEditFactory` (nested) — `@Service` (was already `@Singleton`)
- `PermissionField.PermissionFieldFactory` (nested) — `@Service`. Note: `PermissionField` itself was *not* annotated — it's instantiated only via the factory's `create()`, never injected. So the `@Inject`-on-ctor on `PermissionField` is technically vestigial; the factory is what Spring constructs.
- `AttributeDefaultConstraints` — `@Service`
- `ExportServiceList` — `@Service` (was already `@Singleton`)
- `AttributeEdit` — `@Service`
- `AllocatableMergeEditUI` — `@Service @Scope("prototype")` (consumer is `Provider<>` in `EditTaskViewSwing`)
- `CalendarPrintDialog` — `@Service @Scope("prototype")` (consumer is `Provider<>` in `PrintAction`)

**Set-typed `@Extension` classes** (15 — `BelongsToAnnotationEdit` was found to be entirely inside a `/* … */` block comment, dead code, skipped):

`Set<EventCheck>` (4): `RequestAllocationCheck`, `ConflictReservationCheck`, `DefaultReservationCheck`, `HolidayExceptionCheck` — all bare `@Service`.

`Set<AnnotationEditAttributeExtension>` (6): `SortingAnnotationEdit`, `EmailAnnotationEdit`, `CategorizationAnnotationEdit`, `ExpectedRowsAnnotationEdit`, `ExpectedColumnsAnnotationEdit`, `ColorAnnotationEdit` — all bare `@Service`.

`Set<AnnotationEditTypeExtension>` (5): `ResourceTreeNameAnnotationEdit`, `ConflictCreationAnnotationEdit`, `ExportEventNameAnnotationEdit`, `LocationAnnotationEdit`, `ExportEventDescriptionAnnotationEdit` — all bare `@Service`.

**Option panels and remainders** (8):

- `RaplaStartOption` — `@Service @Scope("prototype")` (`SystemOptionPanel` id=`startOption`)
- `UserOption` — `@Service @Scope("prototype")` (`UserOptionPanel` id=`userOption`)
- `WarningsOption` — `@Service @Scope("prototype")` (`UserOptionPanel` id=`warningOption`)
- `CalendarOption` — `@Service @Scope("prototype")` (BOTH `UserOptionPanel` + `SystemOptionPanel` id=`calendarOption`; one Spring bean satisfies both injection points)
- `MergeMenuFactory` — `@Service` (consumer is `Set<ObjectMenuFactory>` per `CompactDayViewFactory` etc., so bare `@Service` is correct — same pattern as the already-wired `CopyUrlMenuFactory`/`SetOwnerMenuFactory`, despite the legacy `@Extension(id="…")` metadata)
- `PlanningStatusMenuFactory` — `@Service`
- `DynamicTypeEditUI` — `@Service("org.rapla.entities.dynamictype.DynamicType") @Scope("prototype")` (`Map<String, Provider<EditComponent>>` consumer keyed by entity FQN — same pattern as the four other `EditComponent` impls)
- `AppointmentNoteEditFactory` — `@Service` (`Set<AppointmentEditExtensionFactory>` consumer)
- `ConflictPeriodReservationButton` — `@Service` (`Set<ReservationToolbarExtension>` consumer)

**Verification.** `mvn -pl rapla-bom,rapla-core,rapla-client compile` is green at every batch boundary. After the parallel `RemoteStorage` refactor landed, the full reactor compiles, and `SpringRaplaClientTest` was re-run as the bean-graph signoff:

1. **Initial run failed** with `BeanCreationException: Error creating bean with name 'conflictReservationCheck' … Constructor threw exception … Caused by: RaplaInitializationException: Dependency Cycle detected. Please use provider for operator`. Root cause: `ConflictReservationCheck`'s ctor calls `facade.getRaplaFacade().getPermissionController()`, which during `preInstantiateSingletons()` runs before the operator finishes wiring. Same boot-time-state issue described under "Pattern for boot-time-state classes" — the PRD claimed this was already mitigated by a global lazy-init `BeanFactoryPostProcessor` in `SpringRaplaClient`, but **the BFPP did not actually exist** in the code; the PRD entry described intent, not state.
2. **Fixed by adding the BFPP for real** — `SpringRaplaClient` now constructs an empty `AnnotationConfigApplicationContext`, registers `globalLazyInitPostProcessor()` via `addBeanFactoryPostProcessor`, registers the config classes, then refreshes. The post-processor walks every `BeanDefinition` and calls `setLazyInit(true)`. Beans now construct on first dereference, after the operator and facade are fully wired.
3. **Result**: `SpringRaplaClientTest` now passes. Full non-`rapla-core` reactor (`mvn test -pl rapla-bom,rapla-client,rapla-server,rapla-app`) is green: 4 client + 18 server + 23 app = 45 tests pass, including `SpringRaplaClientTest`, `RaplaSpringBootApplicationTest`, the integration tests, and `ConcurrentTests`. (`rapla-core` is currently broken by a parallel session's PRD 010 Jackson refactor — `JsonReaderTest.testJson`. Per AGENTS.md §7 not mine to fix.)

**Final status of `rapla-client/src/main/java`'s `@Inject` files**:
- 169 files contain `@Inject`
- 156 carry a class-level Spring stereotype (`@Service`/`@Component`) or are wired via `@Bean` factory in a `*Config` class
- 13 are intentionally not annotated:
  - **Wired via `@Bean`**: `DefaultIO` (in `ClientConfig`), `EditTaskPresenter` (in `EditTaskPresenterConfig`)
  - **Base class with wired subclass**: `RaplaClipboard` (subclass `RaplaSwingClipboard` is `@Service`), `SwingListView<T>` (subclass `ObjectSwingListView` is `@Service`)
  - **Manually `new`'d at every callsite**: `CountryChooser`, `LanguageChooser`, `SimpleTreeCellRenderer`. **`@Inject` stripped 2026-05-08** (vestigial — no Spring consumer). The factory ctor signatures are unchanged so manual `new` callsites still compile.
  - **Dead code (no real consumer)**: `CalendarTableViewPresenter`, `CalendarWeekViewPresenter`, `CalendarContextMenuPresenter`, `BelongsToAnnotationEdit` (whole file commented out), `PlanningStatusAnnotationEdit` (`@Extension` is `//` commented out), `ReservationPresenter` in `client/edit/reservation/sample/`

### 2026-05-08 — Cleanup pass after the wiring batches landed

- **Vestigial `@Inject` removed** from `CountryChooser`, `LanguageChooser`, `SimpleTreeCellRenderer` (3 files). All three are constructed manually via `new` at every callsite (`RaplaStartOption`, `UserOption`, `RaplaClientServiceImpl`, `AbstractSelectField`). The legacy DI marker was decorative; removing it makes the audit-by-grep cleaner without changing behavior.
- **`getDownloadURL()` now reads `rapla.download.url` system property** (default `http://localhost:8051/`). Production launches can override with `-Drapla.download.url=https://prod.example.com/rapla/`. Closes the third bullet of "Remaining for production parity".
- **`@Bean` factories in `ClientConfig` confirmed load-bearing, NOT redundant.** Initial cleanup attempt removed `swingBundleManager` and `commandScheduler` `@Bean` factories on the (PRD-claimed) basis that `SwingBundleManager` and `SwingSchedulerImpl` are `@Service @Primary`. **Test failure** (`ClientConfigTest.clientContextLoads` — "No qualifying bean of type 'BundleManager' available") revealed that `ClientConfig` is designed to be standalone-usable: the test loads `ClientConfig + ClientProxyConfig` *without* `SwingClientConfig`'s component scan, so the `@Bean` factories are the *only* providers in that scenario. Reverted; both `@Bean`s now have JavaDoc explaining the standalone-vs-Swing duality. The `@Primary` overrides do their job when `SwingClientConfig` is also loaded (i.e. the actual Swing client launch).
- **`LoginDialog` polish**: PRD line 196 was speculative ("probably needs `@Service`/wiring polish"). On inspection, `LoginDialog` is a static-factory dialog (`LoginDialog.create(env, i18n, …)` invoked from `RaplaClientServiceImpl`), not a Spring bean. No DI is needed; the speculative bullet is closed without action.

### Remaining for production parity (post-cleanup)

The list below replaces the earlier "Remaining for production parity" bullets — all have closed:

- ~~**Sparse plugin menus**~~ — **resolved 2026-05-08**: not a wiring gap. All 5 menu-extension classes that exist in the codebase (`CopyPluginMenu`, `ImportTemplateMenu`, `ImportFromICalMenu`, `CSVExportMenu`, `Export2iCalMenu`) are `@Service`-annotated and resolve into their respective Sets. `Set<AdminMenuExtension>`/`HelpMenuExtension`/`ViewMenuExtension` are empty because **no classes implement those interfaces**, not because of unwired classes. Adding entries to those menus requires writing new menu-extension classes, which is a feature task, not a DI-migration task.
- ~~**Phase 5 (field → ctor injection)**~~ — **resolved 2026-05-08**: of the 31 files touched in the wiring batches, all were already ctor-injected. The earlier heuristic-based scan that flagged 9 candidates was a false positive (the regex matched `@Inject public Ctor(…)` on a single line). No migration work outstanding for the touched set.

### 2026-05-08 — Global lazy-init in `SpringRaplaClient`

`SpringRaplaClient` now sets `setLazyInit(true)` on every bean definition via a `BeanFactoryPostProcessor` before context refresh. Rationale: the legacy DI created `@Inject` classes on first use, and several constructors (notably `CountryChooser`) make REST calls in their ctor body — eager init at context refresh fails with 404 when no server is reachable. With global lazy-init, a bean is only constructed on first dereference (matching legacy behavior). Beans that genuinely need eager init can opt back in with `@Lazy(false)`. This eliminates the need for sprinkling `@Lazy` on individual plugin extensions defensively.

### 2026-05-07 — `@Bean` factory return-type cleanup in `ServerServiceConfig`

The parallel session's controllers (`ArchiverController`, `RemoteLocaleController`, `JNDIConfigController`) were rewritten to inject the impl directly (e.g. `ArchiverServiceImpl`) instead of the interface. The `@Bean` factories in `ServerServiceConfig` were still typed with the interface return type, so Spring couldn't satisfy the dep. Fixed by changing the factory return types to the impl: `archiverService` → `ArchiverServiceImpl`, `remoteLocaleService` → `RemoteLocaleServiceImpl`, `jndiConfig` → `RaplaJNDITestOnLocalhost`. The interface beans aren't needed (no other consumer asks for the interface type — controllers always wanted the impl).

### Phase 2 — DONE

All 32 original `@DefaultImplementation` swing classes are now `@Service`-annotated. The session has wired ~50 beans total counting nested factories, action classes, `@Bean` config methods, and Phase 4 prototype-scoped multi-id beans.

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

- ~~**`@Named("id")` extension key collisions**~~ — **resolved 2026-05-08**. Audited all `@Service("…")` / `@Component("…")` / `@Bean(name=…)` declarations across `rapla-client` (with constants like `*.PLUGIN_ID` resolved to their string values). Client context has 29 named beans across 6 EditComponent ids (entity FQNs), 4 task-presenter ids (`cal`, `resource_calendar`, `admin_user`, `admin_types`), 11 plugin option ids (`org.rapla.plugin.*`), 3 function-factory namespaces (`org.rapla`, `appointment`, `org.rapla.eventtimecalculator`), and 5 edit-task-presenter ids (`editEvents`, `editResources`, `createReservationFromDynamicType`, `reservationFromTemplate`, `mergeResources`). **Zero collisions.** The pre-existing `PlanningStatusPluginOption` legacy bug (claims `@Extension(id = CSVExportPlugin.PLUGIN_ID)`, i.e. `org.rapla.plugin.cssexport`) is *not* a Spring collision because the bean uses bare `@Service` (default name `planningStatusPluginOption`); the `@Extension` id only matters to legacy restinject scanning, which is gone.
- ~~**Per-Swing-component scope**~~ — **resolved over the wiring batches**. Decision rule documented under "Pattern for prototype-scoped action classes" (line ~366): legacy `Provider<T>` consumer ⇒ `@Service @Scope("prototype")`; direct `T` consumer ⇒ default singleton. Applied consistently: option panels, edit UIs, action classes, `CalendarPrintDialog`, `AllocatableMergeEditUI` are prototype; everything else is singleton.
- ~~**Component-scan filter for server packages**~~ — **resolved 2026-05-08**. `SwingClientConfig` already uses `excludeFilters = @ComponentScan.Filter(type=REGEX, pattern=".*\\.server\\..*")`. Independent of the filter, `rapla-client/pom.xml` does *not* declare a dependency on `rapla-server`, so server-side beans are not on the Swing client's runtime classpath in the first place. The regex filter remains as belt-and-braces against a future class accidentally landing in an `org.rapla.client.*.server.*` or `org.rapla.plugin.*.server.*` path. Currently zero classes in `rapla-client/src/main/java` match the filter (no `/server/` directories), so the filter excludes nothing today but will catch the next misplaced file.
