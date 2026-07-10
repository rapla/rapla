# PRD 023 — Presenter / Model carve-out from Swing components

**Status:** in-progress — Phases 1, 2, 3 (model+refactor+tier-1 tests), 5, 7, 8, 9 landed 2026-05-11. Phase 6 re-aimed; 6a + 6c landed (6b skipped); 6e-partial landed 2026-05-12. **Phase 10 (EventCheck carve-outs)** DONE 2026-05-12 — `ReservationWarning` shared DTO + `DefaultReservationWarnings` (4 rules) + `RequestAllocationWarnings` + `HolidayWarningModel` under `org.rapla.client.edit.check`. 31 new tier-1 tests. **Phase 11 (`ExceptionListMutator` carve-out)** DONE 2026-05-12 — extracted the 4 exception-list mutations (apply/revert × add/remove) from `AppointmentController.UndoExceptionChange`; the Swing undo command now delegates. 13 new tier-1 tests using a Proxy-stubbed `Repeating`. **Phase 12 (opportunistic singles)** DONE 2026-05-12 — three small helpers: `EventTimeStatus.classify(...)` (rapla-core, 9 tests) replacing the diff→colour decision in `EventTimeCalculatorStatusWidget.updateStatus`; `HolidayWarningModel.countAllPeriodConflicts(...)` extension (rapla-core, 6 tests) replacing the inline sum in `ConflictPeriodReservationButton.updateButton`; `WorktimeRange.isOvernight(...)` (rapla-core, 9 tests) replacing the inline arithmetic in `CalendarOption.dateChanged` AND fixing the pre-existing PRD-014-flagged bug (start-time now correctly reads from `worktimeStart`). 24 new tier-1 tests. **509+ tier-1 tests in rapla-core.** Phase 4 superseded. AppointmentController −45 then −34 (6e) then ~14 more (Phase 11); AllocatableSelection −64 then −60 (6a); ClassifiableFilterEdit −46; PasswordChangeAction −33; RaplaObjectActions −38; ClassificationEditUI −22 (6c); DefaultReservationCheck −34, HolidayExceptionCheck −22, RequestAllocationCheck −3 (Phase 10). Phase 9 added `ResourceSelectionState` + 27 tests. AllocatableSelection legacy field migration (Finish-A) DONE. Arch-test gate (`NoSwingInRaplaCoreClientEditTest`). `docs/architecture/mvp-pattern.md` published. Harness validated via `PasswordChangePolicyHarnessTest`.
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-11

## Goals

Two primary goals, both about the **decision logic** inside existing Swing components — **not** about replacing the Swing UI:

### Goal 1 — Make the Swing UI better testable

Edit-tier Swing classes (`AppointmentController` 1972 LOC, `AllocatableSelection` 2412 LOC, `ClassifiableFilterEdit` 938 LOC, `ReservationInfoEdit` 797 LOC, `ReservationEditImpl` 596 LOC, `EditTaskPresenter` 639 LOC) have ~5% test coverage; most decisions are verified by hand-clicking. Recurring regressions (Jackson-3 `final` fields hit 5×, date-migration script collateral, undo-stack drift) are the symptom. Carving pure-logic chunks into rapla-core unlocks tier-1 unit testing — Swing classes shrink slightly, the test-bench grows substantially.

### Goal 2 — Identify logic worth reusing in the Angular client

The Angular UI ([PRD 026](026-angular-frontend.md) / [PRD 028](028-angular-power-search.md)) **will look very different** from Swing (power-search shell, different edit-flow surfaces, different layout). This PRD does **not** produce presenter classes that Angular drives. It produces **pure-Java decision/computation classes** in rapla-core that Angular calls via REST ([PRD 024](024-server-side-edit-services.md)), and that the existing Swing classes also delegate to. Same `RepeatingRuleValidator.validate(...)` decides "is this recurrence rule valid" regardless of UI.

### Anti-goals

- **Not** building a production `ReservationEditPresenter` driving `ReservationEditImpl` via a `ReservationView` interface — that design assumed Angular would mirror the Swing edit flow; it won't. The dead-code sample (`org.rapla.client.edit.reservation.sample.ReservationPresenter`) was removed 2026-05-11.
- **Not** rewriting `ReservationEditImpl` or other dialog classes with Presenter↔View split — too risky for a UI tier that will be replaced wholesale by Angular. Dialog stays as is until Angular ships.
- **Not** chasing every small permission check or display formatter into rapla-core. Threshold: ≥ 20 LOC of decision logic AND clear Angular reuse value (REST endpoint or TS-port avoidance).

## Why this is needed now

1. **Testability gap.** Client-side coverage <5%. Recurring regressions (Jackson-3 `final` fields hit 5×, date-migration collateral, undo-stack drift) are missing-test symptoms.
2. **Angular rework on the horizon.** A web client needs the same conflict checks, recurrence validation, layout math and permission filtering — and won't have access to a Java `JPanel`. Either extract into REST endpoints ([PRD 024](024-server-side-edit-services.md)) or pure-Java classes the REST layer reuses. Doing it now gives Angular a working contract; not doing it means reimplementing four years of subtle scheduling behaviour from scratch.
3. **Pattern already works.** [PRD 020](020-server-driven-admin-panels.md)'s `FieldRenderer` / `PanelRenderer` produced 10 tier-1 tests. `CalendarPlacePresenter` / `ConflictSelectionPresenter` / `ResourceSelectionPresenter` already exhibit the inner-`Presenter`-in-View shape — the precedents this PRD piggy-backs on.
4. **Direction lock-in.** PRD 005 marked the `rapla-server` → `rapla-client` back-edge (for `RaplaBuilder` / `abstractcalendar`) as a known compromise. The classes the server depends on are where layout/rendering is tangled with Swing. Carving pure modules lets us re-evaluate that dependency later.

## Scope

### In scope

A focused first wave — each item is one Swing god-class plus one pure model carved out of it, plus tier-1/2 tests:

| # | Source (Swing-coupled) | Extracted (pure) | Why this one first |
|---|---|---|---|
| 1 | `AppointmentController` (1972 LOC) — repeating-rule branch | `RepeatingRuleModel` + `RepeatingRuleValidator` (rapla-core) | Single-largest hot spot of pure logic buried in UI. Recurrence rules drive most date bugs. |
| 2 | `AllocatableSelection` (2463 LOC) — `calcConflictingAppointments` + `isAllowed` + binding assembly | `AllocationConflictModel` (rapla-core) | Conflict math; first candidate to expose as a REST pre-check ([PRD 024](024-server-side-edit-services.md)). |
| 3 | `AbstractRaplaSwingCalendar` + `SwingRaplaBlock` (~900 LOC combined) — block geometry | `CalendarBlockLayout` (rapla-core) | Shared by weekview / monthview / dayresource / compactweek / timeslot. Single extraction unlocks five plugins. |
| 4 | `SwingRaplaBlock` — color decision | `ReservationBlockStyle` (rapla-core) | Permission/status → colour code. Trivially testable; reused by Angular. |
| 5 | `ClassifiableFilterEdit` (938 LOC) — filter assembly | `ClassificationFilterBuilder` (rapla-core) | Schema-driven filter construction. Heavy logic, no tests today. |

Each extraction includes: a new pure class in `rapla-core/src/main/java/...` (no Swing/AWT import — verified by an arch test or `grep` in CI); a tier-1 JUnit 5 test class targeting ≥ 80% branch coverage; the Swing class refactored to **delegate** (Swing keeps events/widgets/painting; pure class owns decisions).

### In scope — house-pattern alignment

- **Document the carve-out pattern** in `docs/architecture/mvp-pattern.md` (landed 2026-05-11) — package layout, carve-out recipe, harness usage, AGENTS.md §12 permission-leak rule with reference impls.

### Explicitly out of scope

- No Swing → JavaFX / SwingX / FXML migration. The view stays Swing.
- No REST endpoints (that's [PRD 024](024-server-side-edit-services.md)).
- No rewrite of `RaplaGUIComponent` (509-LOC god-class, 64 subclasses; deprecation is future work).
- No retroactive `*View` interfaces added to every Swing component just to call it "MVP". Add when there's a presenter to pair with.
- No Eclipse-MVP / GWT-MVP framework adoption.

## Architecture — house pattern

Target shape, copied from working `CalendarPlacePresenter` / `CalendarPlaceView` + [PRD 020](020-server-driven-admin-panels.md) admin-panels renderer:

```
+----------------------------+
|  pure model / validator    |    rapla-core, no Swing
|  RepeatingRuleModel        |    fully unit-testable (tier 1)
|  RepeatingRuleValidator    |    reusable from server REST (PRD 024)
+----------------------------+
            ^
            |  POJOs only
            |
+----------------------------+
|  Presenter                 |    rapla-client, no Swing
|  ReservationEditPresenter  |    depends on RaplaFacade, View interface
|  - holds editable entity   |    no JPanel / JTable / EDT
|  - owns the model objects  |    callable from a JUnit test with a
|  - emits commands to View  |      mock View and FacadeTestSupport
+----------------------------+
            ^  setPresenter / view callbacks
            |
+----------------------------+
|  View interface            |    rapla-client, no Swing import
|  ReservationView           |    inner Presenter interface declares
|  - show(...)               |      the events the view raises
|  - updateAppointments(...) |      (button clicks, field edits)
|  - showWarning(...)        |
+----------------------------+
            ^  implements
            |
+----------------------------+
|  Swing adapter             |    rapla-client/.../swing
|  ReservationEditImpl       |    holds JPanel / JTable / JButtons
|  - converts events to      |    forwards everything to the Presenter
|    presenter.xxx() calls   |    headless test substitutes a fake view
|  - renders model state     |
+----------------------------+
```

Key rules:

- `*Presenter` lives in `rapla-client` but does **not** import `javax.swing.*`, `java.awt.*`, or `SwingUtilities`. Enforced by arch test (ArchUnit or one-line `grep` in CI).
- Pure models live in `rapla-core`. POJOs/records, no facade access, no DI. The presenter passes them entities and gets back computed state.
- The View interface lives next to the Presenter in `rapla-client`, not in `swing/`. The Swing impl is in `swing/internal/...`.
- The presenter is constructor-injected (AGENTS.md §4); the view is one constructor parameter. The Swing impl is wired by the existing client `@ComponentScan`.

## Plan

### Phase 1 — Lift the smallest pure logic out of `AppointmentController` (≈3 days) — **DONE 2026-05-11**

Landed: `org.rapla.client.edit.reservation.RepeatingRuleProjector` (rapla-core, 214 LOC) with static functions `exceptionButtonState`, `endingMode`, `endingPanelVisibility`, `endDateBinding`, `repeatingPanelVisibility`, `dayChooserState`, `weekdaySelections`, plus enums `EndingMode` / `DayChooserMode` / `ExceptionCountStyle` and record outputs. `AppointmentController.RepeatingEditor.mapFromAppointment()` / `updateExceptionCount()` / `showEnding(int)` now delegate. 35 tier-1 tests in `RepeatingRuleProjectorTest` (~150 ms, 0 Swing). `AppointmentOverlapHardeningTest` still green.

### Phase 2 — Carve out `RepeatingRuleModel` + validator (≈5 days) — **DONE 2026-05-11**

Landed: `RepeatingRuleModel` (record, 41 LOC), `RepeatingRuleWriter` (79 LOC, applies model to live `Repeating` with all clamping rules), `RepeatingRuleValidator` (88 LOC, advisory issues with codes `INTERVAL_LESS_THAN_ONE`, `WEEKLY_WITH_NO_WEEKDAYS`, `UNTIL_END_BEFORE_START`, `N_TIMES_COUNT_LESS_THAN_ONE`, `N_TIMES_COUNT_UNBOUNDED`). `AppointmentController.RepeatingEditor.mapToAppointment()` and `updateWeekdays()` replaced by a 35-LOC body that builds a model from widget reads and calls `RepeatingRuleWriter.writeTo(...)`. Net delta on `AppointmentController` after Phases 1+2: **-45 LOC** (1972 → 1927). 25 tier-1 tests in `RepeatingRuleWriterTest` + `RepeatingRuleValidatorTest`. Validator wired through the model but not yet surfaced to user in Swing — that's Phase 6 work.

**Bug found while refactoring:** original `mapToAppointment()` had `if (number != null)` where it meant `if (numberValue != null)` (L1186 pre-refactor) — pre-existing latent NPE when repeat-count widget returned null; refactor incidentally fixes it.

### Phase 3 — `AllocationConflictModel` carve-out (≈5 days) — **DONE 2026-05-11**

Landed: `org.rapla.client.edit.reservation.AllocationConflictModel` (rapla-core) with static `compute(allocatable, appointments, bindings, permissionController, user, today) → AllocationOutcome` and standalone `isAllowed(...)`. Output record: `conflictingAppointments[]`, `conflictCount`, `permissionConflictCount`, `aggregateRequestStatus`. `AllocatableSelection`'s 7-line `isAllowed`, 37-line `calcConflictingAppointments`, and the inner `AllocationRendering` class are all gone — replaced by a 5-line delegate. Six call sites updated to record-accessor syntax. Net delta on AllocatableSelection: **-64 LOC**.

Tier-1 tests in `AllocationConflictModelTest` (rapla-core, 9 tests, ~110 ms) — uses `java.lang.reflect.Proxy` to stub `Allocatable` / `Reservation` / `Appointment` (only the four methods `compute()` touches need to be answered); `PermissionController` subclassed with `canAllocate` overridden. Cases: empty appointments, all-permitted-no-bindings, binding-driven conflict, hold-back-conflicts annotation suppression, permission-denial flag + count, hold-back-suppresses-flag-but-not-permission-count, request-status aggregation (first non-null wins), null-request-status, mixed binding-and-permission accumulation. Plus `/edit/check-conflicts` MockMvc tests in `ReservationEditControllerIntegrationTest` cover the same model end-to-end via Spring.

### Phase 4 — `CalendarBlockLayout` + `ReservationBlockStyle` — **SUPERSEDED — already done in rapla-core**

Audited 2026-05-11. Both halves already separated:

- **Block colour decision** lives in `rapla-core/.../plugin/abstractcalendar/RaplaBlock.java:133` (`getColorsAsHex()`) plus `isException`, `isRequest`, `isMovable`, `isBlockSelected`. Consumed by `HTMLRaplaBlock` and `SwingRaplaBlock` symmetrically. No Swing imports. Swing paint code only does alpha blending and `g.setColor(...)` — true rendering, not a carve-out target.
- **Block layout** lives in `rapla-core/.../components/calendarview/{AbstractGroupStrategy, GroupStartTimesStrategy, BestFitStrategy}.java`. Geometry/sweep-line/overlap-detection is already a pure `BuildStrategy` impl.

PRD over-estimated this phase. **Skip.**

### Phase 5 — `ClassificationFilterBuilder` (≈3 days) — **PARTIAL — operator catalog DONE 2026-05-11**

Landed: `org.rapla.client.edit.filter.ClassificationFilterOperators` (rapla-core, 115 LOC) — centralised catalog mapping `AttributeType` ↔ valid operator strings ↔ JComboBox index. Replaces the two duplicated if/else ladders in `ClassifiableFilterEdit.RuleRow.{getOperatorValue, setOperatorValue}` (66 LOC → 20 LOC, **-46 LOC**). API: `operatorsFor(type)`, `defaultOperatorFor(type)`, `operatorAt(type, index)`, `indexOf(type, op)`, `hasOperatorChoice(type)`. Preserves legacy `"is"` → `=` alias on numeric types so existing serialized filters still load. 13 tier-1 tests in `ClassificationFilterOperatorsTest`.

The larger `ClassifiableFilterEdit.getFilter()` / `mapFrom(...)` flow is already mostly separated — it iterates rule rows and calls into rapla-core's `ClassificationFilter`. The pure-logic chunk worth carving was the operator catalog; the rest is widget binding.

### Phase 6 — Continued opportunistic carve-outs from edit-tier classes (re-aimed 2026-05-11)

**Original framing dropped.** PRD [026](026-angular-frontend.md) / [028](028-angular-power-search.md) confirm Angular UI will look very different from the Swing edit dialog (power-search shell, redesigned edit flow). No production `ReservationEditPresenter` to build — Angular will not drive the sample's `ReservationView` interface. Promoting the sample would produce a presenter shaped for a UI that's not going to exist.

Swing `ReservationEditImpl` and friends therefore **stay as they are** until the Swing tier is retired (anti-goal #2).

Phase 6 actually becomes: **keep extracting pure-logic chunks from edit-tier god-classes**, on the same terms as Phases 1–5 and 8 — Swing class delegates to a rapla-core class, tier-1 tests pin the behaviour, REST endpoint ([PRD 024](024-server-side-edit-services.md)) consumes the same class if Angular needs the decision. No `*View` interfaces added; no dialog rewrites.

#### Audit — remaining extractables (each independently shippable)

| # | Source | Chunk | Approx LOC | Angular reuse value | REST candidate |
|---|---|---:|---:|---|---|
| 6a | `AllocatableSelection` | **Row status decision** — extracted as `AllocatableRowStatusModel` (rapla-core). Pure `Inputs` → `Status` enum (AVAILABLE / NOT_ALWAYS_AVAILABLE / REQUEST / CONFLICT / FORBIDDEN). Plus `hasPermissionToAllocate(...)` carved out. Swing `getIcon(...)` is now 1-line delegate + 7-line enum→Icon switch. **DONE 2026-05-11** — 10 tier-1 tests in `AllocatableRowStatusModelTest`. |
| 6b | `ReservationEditImpl` | **Dirty-state detection.** **SKIPPED** — `EditTaskView.hasChanged()` returns hardcoded `true` in all impls. Carving would be NEW behaviour (real entity-diff), not refactor. Reopen if/when always-prompt UX changes. |
| 6c | ~~`ReservationInfoEdit`~~ → `ClassificationEditUI` | **Field visibility per attribute** — `(visible, writable)` decision per attribute across multi-edit object list, with conservative semantics. Extracted as `ClassificationFieldVisibility.Result resolve(...)` (rapla-core). Was inlined in `ClassificationEditUI.createEditField(...)` — found there, not in `ReservationInfoEdit` as audit guessed. **DONE 2026-05-11** — 7 tier-1 tests in `ClassificationFieldVisibilityTest`. |
| 6d | `EditTaskPresenter` | **Activity-type → entity-type dispatch** — which inner edit flow handles `editevent` / `editallocatable` / `edituser` / etc. | ~50 | Medium — Angular router has its own dispatch but the entity-type→handler map is the same. | No — client routing. |
| 6e | `AppointmentController` | **`RepeatingType` → radio-button mapping** + **weekday-set update on anchor-shift**. Extracted as `RepeatingRuleProjector.choiceFor` / `repeatingTypeFor` + `weekdaysOnAnchorShift(...)` (rapla-core). The mapping was duplicated in `setAppointment` (~20 LOC) and `setRepeatingType` (~16 LOC); replaced by 1-line `choiceFor(...)` + 7-line `switch` on the choice in the view. `resetWeekdays(int)` rewrote to one delegate call. **DONE 2026-05-12** — 9 new tests in `RepeatingRuleProjectorTest`. Undo-command construction (broader L120 item) deferred — entity-mutation half is already pure; command-class shells are thin glue. |
| 6f | `AllocatableSelection` | **`AllocationTextField` index-display logic** — which appointment indices light up which allocatable row. | ~50 | Medium — Angular table renders same indicators. | No — derived from already-fetched data. |

#### Priority

Ship 6a, 6b, 6c first (high Angular reuse, smallest LOC). 6d adds value when Angular's edit-flow router lands. 6e/6f are lower-leverage. Each carve-out follows the recipe in `docs/architecture/mvp-pattern.md` §"Adding a new carve-out". Each extraction is independent and sized for one focused session.

### Phase 7 — Name-search field next to the filter button (quick win, ≈0.5 day) — **DONE 2026-05-11**

Landed:
- `org.rapla.client.edit.search.NameSearchMatcher` (rapla-core) — substring, case-insensitive, diacritic-folded (NFD + Mn-strip), ß→ss for German user base, multi-word AND. 15 tier-1 tests in `NameSearchMatcherTest`.
- `ReservationEditSelection.nameSearchTerm` + `filterByNameSearch(Collection<Allocatable>, Locale)` — 6 new tier-1 tests in `ReservationEditSelectionTest`.
- `AllocatableSelection.nameSearchField` JTextField with placeholder + `DocumentListener` → `selection.setNameSearchTerm(...)` → `refreshCompleteTreeForNameSearch()` which re-binds `completeModel` with `selection.filterByNameSearch(getAllAllocatables(), getRaplaLocale().getLocale())`. No debounce (matcher is sub-millisecond at typical sizes).
- Sub-panel groups the search field + existing filter button at column 4 of leftPanel `TableLayout`.
- `ResourceSelectionViewSwing` (main calendar-place resource sidebar): `nameSearchField` JTextField mounted in `buttonsPanel.CENTER` next to existing filter button on `EAST`. `DocumentListener` records the term and re-runs `updateTree(...)`; `generateTree(...)` post-prunes the `AllocatableNodes` tree via local `pruneByName(...)` using `NameSearchMatcher.matchesPrepared` per leaf and drops empty type/categorization folders. Tooltip uses existing `search` i18n key. View-local transient state.
- New i18n keys `search` (`"Search"` / `"Suche"`) and `search.placeholder` (`"Search…"` / `"Suchen…"`).
- Classification filter button still works orthogonally — name search is an AND on top of the structured filter (search filters the already-classification-filtered list from `getAllAllocatables()`, or post-prunes the already-classified tree).

Also landed (`ResourceSelectionViewSwing` sidebar):
- `hiddenSelectionStatus` JLabel below `buttonsPanel`. Shows `"{N} selected hidden by search — click replaces selection, Ctrl-click adds"` whenever search term is non-empty AND ≥1 currently-selected allocatable doesn't match. Counts recomputed on every search-term change AND every `update(filter, model, selectedObjects)` call. New i18n key `search.hidden_status` (EN+DE).
- Selection semantics (Option 6 from design discussion):
  - Tree shows matches only.
  - Selection state stays untouched by search — `model.getSelectedObjects()` never written by search field.
  - Status line tells user how many selections are hidden.
  - Plain click on a visible result still triggers `JTree`'s default "replace selection" — status line is **informational, not protective**. Option (b) — sticky additive selection while search is active — discussed but deferred.

Not landed (deferred): `ClassifiableFilterEdit` doesn't have its own search field yet (dialog heavier, rule list typically small). **Event search in the calendar view (`MultiCalendarViewSwing`) explicitly dropped on Swing tier** — initial attempt added a transient `eventNameSearch` field to `CalendarSelectionModel` + `CalendarModelImpl` and filtered the appointment binding map at query time; reverted 2026-05-11 after design discussion — per-view search is a UX affordance, not a calendar-model concept. Event search will land in Angular as part of [PRD 028](028-angular-power-search.md)'s power-search. Sticky additive selection (Option b) not landed.

**Origin:** extracted from PRD 021 (`wont-fix`) when the stub-mode redesign was dropped in favor of Angular ([PRD 026](026-angular-frontend.md)).

### Phase 8 — Action-class policy carve-outs (opportunistic) — **DONE 2026-05-11**

Pattern: action/menu classes in `rapla-client/.../menu/...` often have a small `isEnabled()` or `validate(...)` method that's pure logic mixed with Swing action's `setEnabled` / dialog side effects. Carve the decision into rapla-core as a `*Policy` class; the Swing class delegates.

Landed:

- **`PasswordChangePolicy`** (rapla-core, 75 LOC) — carved from `PasswordChangeAction`. Three pure functions: `canChangePassword`, `requiresOldPassword`, `validate`. 13 tier-1 tests in `PasswordChangePolicyTest`. Plus `PasswordChangePolicyHarnessTest` (rapla-server, 4 tier-2 tests) — first production use of `HeadlessPresenterTestSupport` against real `testdefault.xml` users; **validates the harness scaffold from [PRD 025](025-headless-client-test-harness.md) works end-to-end**. `PasswordChangeAction` shrinks by 33 LOC.
- **`RaplaObjectActionPolicy`** (rapla-core, 110 LOC) — carved from `RaplaObjectActions.isEnabled()`. Branches per action type (NEW/EDIT/DELETE/EDIT_SELECTION/DELETE_SELECTION) × entity type (Allocatable/Category/other) × admin status. Two overloads: production (takes `PermissionController`) + test-friendly (takes lambda predicates), so tier-1 tests don't construct or subclass the full controller (whose `isRegisterer` is final). 15 tier-1 tests in `RaplaObjectActionPolicyTest`. `RaplaObjectActions.isEnabled()` shrinks from 52 LOC to 11 LOC.

This is **not the same shape as Phases 1–5**. Those carved business-logic from widget-binding code; Phase 8 carves decision logic from `javax.swing.Action` shells. Same goal, different starting point. Future similar carve-outs in `MenuFactoryImpl.java` (871 LOC, ~12 permission decisions) deliberately not pursued — each is 1–3 lines and the cost/benefit doesn't justify the touch.

### Architecture invariant — **DONE 2026-05-11**

`NoSwingInRaplaCoreClientEditTest` (rapla-core/src/test/java/.../client/edit/) walks the source tree of `rapla-core/src/main/java/org/rapla/{client/edit,plugin/calendarview,plugin/reservationedit}` and fails the test if any `.java` file imports `javax.swing.*`, `java.awt.*`, `org.rapla.client.swing.*`, `org.rapla.facade.client.*`, or `org.rapla.facade.server.*`. Sanity-tested both directions.

## Tests

### Tier discipline

Per AGENTS.md §10, default to tier 1 / tier 2:

| Phase | Tier | Where | Engine |
|---|---|---|---|
| 1 | 1 | `rapla-core/src/test/java/.../client/extract/` | plain JUnit, no Spring, no facade |
| 2 | 1 | same | same |
| 3 | 1 (stub `PermissionController`) or 2 (`FacadeTestSupport`) | `rapla-server/src/test/java/` if tier 2 | plain JUnit |
| 4 | 1 | `rapla-core` | plain JUnit |
| 5 | 1 or 2 | `rapla-core` / `rapla-server` | plain JUnit |
| 6 | 2 | `rapla-server/src/test/java/`, extends `FacadeTestSupport` | plain JUnit |

No tier-3/4 tests added; `SwingClientStartIntegrationTest` and `HeadlessClientNameResolutionIntegrationTest` guard end-to-end wiring.

### Architecture test

Single ArchUnit (or `grep`-based) test that fails CI if `rapla-core/src/main/java/org/rapla/client/extract/**` ever imports `javax.swing.*`, `java.awt.*`, or `org.rapla.client.swing.*`.

## Risks

1. **The Swing refactor breaks the dialog.** Mitigation: existing `UndoTests` and `CalendarEditorTest` exercise the appointment edit dialog end-to-end. Run after each phase. Tagged `e2e`; explicitly include in CI lane for this PRD.
2. **`AppointmentController` is currently modified (M) in `git status`.** Coordinate first phase with in-flight changes (AGENTS.md §7).
3. **PermissionController is heavy.** Phase 3 extraction may surface that `PermissionController` itself wants splitting into pure `PermissionRules` + facade-aware loader. Don't chase here; note as follow-up.
4. **Layout extraction (Phase 4) is the most algorithmic.** Subtle paint-order dependencies (overlapping blocks, all-day rows above timed rows, conflict-stripes painted on top). Test in two passes: first byte-identical, second moves overlap-detection into pure model. Don't refactor algorithm + structure in one commit.
5. **The "sample" presenter is incomplete.** Phase 6 promotion is effectively rewriting `ReservationEditImpl`. Scope can balloon. Time-box at 4 days; if it overruns, ship Phases 1–5 (mechanical extractions) and defer Phase 6 to a follow-up PRD.

## Phase 9 follow-up — duplicate-node selection + per-click rebuild skip (2026-06-11)

The sidebar tree shows the same `Allocatable` under multiple parents (TreeFactoryImpl creates one node per categorization value, e.g. a dhbw room under both Gebäude and Studiengang). Three fixes, all verified by `RaplaTreeDuplicateSelectionTest` (6 headless tier-1 tests, red-first):

- **`RaplaTree` selects per user object, not per node.** `select()` and the `exchangeTreeModel` restore loop previously stopped at the first DFS match, so the highlight snapped to the first occurrence regardless of which duplicate was clicked. Now every node carrying a selected object highlights. A new listener-level mirror treats user gestures as object-level: clicking one occurrence selects all twins, ctrl-click-deselecting one deselects all. `getSelectedElements()` dedupes.
- **No view jump.** Two mechanisms moved the tree away from the clicked node: (a) `scrollsOnExpand` auto-scroll — suppressed during all programmatic selection/expansion restore; (b) the bigger one — selecting the hidden twin *expanded* its collapsed parent (`expandsSelectedPaths` default + `select()`'s explicit `expandPath`), inserting rows that visually shift the tree. Fix: `expandsSelectedPaths` off, `select(collection, false)` no-expand variant for the click-refresh path (twins stay selected-but-hidden under collapsed parents). Plus a **click anchor**: `RaplaTree` remembers the clicked path + its viewport pixel offset and, after any programmatic selection re-apply or model exchange, scrolls so the clicked row sits at the same visual position again (path resolved by user-object chain across rebuilt models).
- **Expansion survives search prune + server refresh.** `RaplaTree` keeps a persistent expanded-user-objects set (TreeExpansionListener-maintained; user collapse removes, model swap keeps). `exchangeTreeModel` restores from it, so a branch a search pruned away comes back expanded when the term is cleared. Restore loop bound is now dynamic (`tree.getRowCount()` re-read), fixing the old fixed-`rowCount` cap that dropped nested expansion/selection. Server data refreshes are safe: entities equal by id (`SimpleEntity.equals`), categorization folders by name (`Categorization.equals`), so restore matches replaced instances.
- **Pure selection changes no longer rebuild the tree.** `ResourceSelectionViewSwing.refreshFromState` regenerated the whole model on every click (O(resource count) — slow clicks for admins on 10k-resource installations). It now rebuilds only when the search term changed (`appliedSearchTerm` tracking); selection-only refreshes reuse the model and just re-apply selection. Data/filter changes still rebuild via `view.update(...)`.

## Open Questions

1. **Package name.** `org.rapla.client.extract` is a placeholder. `org.rapla.client.presenter.model`? `org.rapla.client.headless`? Open.
2. **Where does the View interface live?** Today `RaplaWidget<T>` is in `org.rapla.client`. New `*View` interfaces belong next to their presenter (`org.rapla.client.edit.reservation.ReservationView`), not in a `view/` subpackage — open whether to enforce.
3. **Inner `Presenter` interface vs. separate class.** The sample uses `ReservationView.Presenter` (inner); some places (`TaskPresenter`) are top-level. Decide and document one style in `docs/architecture/mvp-pattern.md` ([PRD 022](022-architecture-documentation.md)).
4. **Should `RepeatingRuleModel` replace the existing `Repeating` entity?** No — `Repeating` is a domain entity persisted to XML/JDBC; the model is an editor-facing view. They translate via `writeBack(...)`. Note [PRD 014](done/014-appointment-long-to-java-time.md)'s `Appointment` long→java.time migration when designing field types — use `LocalDate` for end-dates, not `Date`.
5. **Headless presenter test base.** Add a `HeadlessPresenterTestSupport` companion to `FacadeTestSupport` giving `(facade, view: MockView, clock)`? Probably yes — but defer until first three phases have shown common shape. Add as Phase 7 follow-up.

## Cross-references

| PRD | Relationship |
|---|---|
| **020** server-driven admin panels | Established the metadata-driven generic-renderer pattern. PRD 023 applies the same separation (pure model ↔ Swing renderer) to the reservation-edit tier. |
| **022** architecture documentation | This PRD's "house pattern" diagram + rules should land in `docs/architecture/mvp-pattern.md` as part of 022. |
| **024** server-side edit services | The pure models extracted here are the natural reuse target for the REST endpoints in 024. Order: 023 extracts; 024 exposes. |
| **017** test coverage strategy | The tier-1 unit tests added here directly raise rapla-core / rapla-client coverage. Track per-phase deltas in 017's coverage table. |
| **005** multi-module split | The known compromise (server → client dep on `abstractcalendar` / `RaplaBuilder`) gets a relief valve once layout extraction (Phase 4) is done — the layout classes can move to rapla-core, and the back-edge can be re-examined. |
| **AGENTS.md §4** | New presenter classes are constructor-injected; the sample already shows the shape. |
| **AGENTS.md §10** | All new tests are tier 1 or tier 2 per the pyramid. |
