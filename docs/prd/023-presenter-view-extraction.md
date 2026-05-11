# PRD 023 — Presenter / Model carve-out from Swing components

**Status:** in-progress — Phase 1 + Phase 2 + Phase 3 (model + refactor) landed 2026-05-11. 60 tier-1 tests. AppointmentController -45 LOC; AllocatableSelection -64 LOC. Phase 3 tier-1 tests deferred — see Phase 3 section.
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-11

## Goal

Extract the **pure business logic** that today lives inside Swing
components (`AppointmentController`, `AllocatableSelection`, the calendar
block layout, `ClassifiableFilterEdit`, …) into headless **presenter**
and **model** classes that:

1. Can be unit-tested in tier 1 / tier 2 (no Swing, no EDT, no `JDialog`).
2. Are reusable from a future Angular client — either directly (the pure
   model classes are POJOs) or via the REST surface that PRD 024 will
   add on top of them.
3. Leave the Swing controllers as thin **view adapters** that bind
   widgets to the presenter and translate Swing events into presenter
   calls.

This is **carve-out, not rewrite**. The goal is not to eliminate Swing
or replace `AppointmentController`'s outer shell; it is to move the
~20–30 % of code per file that is *pure logic* (validation, state
computation, conflict math, layout geometry, undo command assembly)
out from under the JPanel.

## Why this is needed now

1. **Testability gap.** Client-side coverage today is <5 %. Almost
   nothing tests `AppointmentController`'s repeating-rule logic, the
   conflict-detection inner loop in `AllocatableSelection`, the
   calendar block layout, or the classification-filter assembly. Every
   change in this area is verified by hand-clicking through the Swing
   dialog. We have repeatedly regressed the same patterns (Jackson-3
   `final` fields hit 5×, date-migration script collateral damage,
   undo-stack drift) because the regression test was missing.
2. **Angular rework on the horizon.** A web client will need exactly
   the same conflict checks, recurrence validation, layout math and
   permission filtering — and it will not have access to a Java Swing
   `JPanel`. Either we extract that logic into REST endpoints (PRD 024)
   or into pure-Java classes that the REST layer can reuse. Doing the
   carve-out now means the Angular client gets a working contract; not
   doing it means reimplementing four years of subtle scheduling
   behaviour from scratch.
3. **The skeleton already exists.** `RaplaWidget<T>`,
   `TaskPresenter`, `EditTaskPresenter`, the inner-`Presenter`-in-View
   pattern (`CalendarPlaceView.Presenter`, `MenuView.Presenter`),
   `CalendarPlacePresenter`, `ConflictSelectionPresenter`,
   `ResourceSelectionPresenter` and the sample
   `org.rapla.client.edit.reservation.sample.ReservationPresenter` /
   `ReservationView` already model the target shape. PRD 020's
   `FieldRenderer` / `PanelRenderer` extracted a similar split for
   admin panels and produced the only well-unit-tested client code in
   the repo (`FieldRendererTest`, 10 tests). The pattern is proven; we
   need to apply it to the reservation-edit tier.
4. **Direction lock-in.** PRD 005 (multi-module split) marked the
   coupling from `rapla-server` back to `rapla-client` (for
   `RaplaBuilder` / `abstractcalendar`) as a known compromise. The
   classes the server depends on are exactly the places where layout
   and rendering logic is tangled with Swing. Carving that logic into
   pure modules lets us re-evaluate the server↔client dependency in a
   later PRD.

## Scope

### In scope

A focused first wave. Each item is **one Swing god-class** plus the
**one pure model** carved out of it, plus a tier-1/2 test suite:

| # | Source (Swing-coupled) | Extracted (pure) | Why this one first |
|---|---|---|---|
| 1 | `AppointmentController` (1972 LOC) — repeating-rule branch | `RepeatingRuleModel` + `RepeatingRuleValidator` (rapla-core) | Single-largest hot spot of pure logic buried in UI. Recurrence rules drive most date bugs. |
| 2 | `AllocatableSelection` (2463 LOC) — `calcConflictingAppointments` + `isAllowed` + binding assembly | `AllocationConflictModel` (rapla-core) | Conflict math; first candidate to expose as a REST pre-check (PRD 024). |
| 3 | `AbstractRaplaSwingCalendar` + `SwingRaplaBlock` (~900 LOC combined) — block geometry | `CalendarBlockLayout` (rapla-core) | Shared by weekview / monthview / dayresource / compactweek / timeslot. Single extraction unlocks five plugins. |
| 4 | `SwingRaplaBlock` — color decision | `ReservationBlockStyle` (rapla-core) | Permission/status → colour code. Trivially testable; reused by Angular. |
| 5 | `ClassifiableFilterEdit` (938 LOC) — filter assembly | `ClassificationFilterBuilder` (rapla-core) | Schema-driven filter construction. Heavy logic, no tests today. |

Each extraction includes:

- A new pure class in `rapla-core/src/main/java/...` (no Swing import,
  no AWT import — verified by an arch test or `grep` in CI).
- A tier-1 JUnit 5 test class in `rapla-core/src/test/java/...` covering
  the extracted behaviour. Target ≥ 80 % branch coverage per extracted
  class.
- The Swing class refactored to **delegate** to the pure class. Swing
  keeps event handling, widget layout, painting; pure class owns the
  decisions.

### In scope — house-pattern alignment

- **Promote the sample MVP shape to production** for the
  reservation-edit dialog. The
  `org.rapla.client.edit.reservation.sample.ReservationPresenter` is
  currently dead code; rename `.sample` → `.headless` and use it as the
  template for the production `ReservationEditPresenter`. The
  Swing impl (`ReservationEditImpl`, 596 LOC) becomes a `ReservationView`
  adapter.
- **Document the house pattern** in `docs/architecture/` (PRD 022 —
  add an `mvp-pattern.md` page when 022 lands, or inline into
  `extension-points.md`).

### Explicitly out of scope (this PRD)

- **No Swing → JavaFX / SwingX / FXML migration.** The view stays Swing.
- **No REST endpoints.** Server-deferring the extracted logic is
  PRD 024.
- **No rewrite of `RaplaGUIComponent`**, even though it's a 509-LOC
  god-class. It's a Swing base that 64 classes extend; touching it is
  a much larger surgery. The carve-outs in this PRD let new code
  *not* depend on it; deprecation is future work.
- **No new view interfaces for classes we're not changing.** Don't
  retroactively add `*View` interfaces to every Swing component just
  to call it "MVP". Add the interface when there's a presenter to
  pair it with.
- **No Eclipse-MVP / GWT-MVP framework adoption.** The current
  hand-rolled inner-`Presenter`-in-View pattern is fine; we just
  apply it more consistently.

## Architecture — house pattern

The target shape, copied from what already works in
`CalendarPlacePresenter` / `CalendarPlaceView` and the PRD 020
admin-panels renderer:

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

- **`*Presenter` lives in `rapla-client` but does not import `javax.swing.*`,
  `java.awt.*`, or `SwingUtilities`.** Enforced by an arch test
  (ArchUnit or a one-line `grep` in CI).
- **Pure models live in `rapla-core`.** They are POJOs/records, no
  facade access, no DI. The presenter passes them entities and gets
  back computed state.
- **The View interface lives next to the Presenter** in `rapla-client`,
  not in `swing/`. The Swing impl is in `swing/internal/...`.
- **The presenter is constructor-injected** (AGENTS.md §4), the view
  is one of its constructor parameters. The Swing impl is wired by
  the existing client `@ComponentScan`.

## Plan

### Phase 1 — Lift the smallest pure logic out of `AppointmentController` (≈3 days) — **DONE 2026-05-11**

Landed: `org.rapla.client.edit.reservation.RepeatingRuleProjector` (rapla-core, 214 LOC) with static functions `exceptionButtonState`, `endingMode`, `endingPanelVisibility`, `endDateBinding`, `repeatingPanelVisibility`, `dayChooserState`, `weekdaySelections`, plus enums `EndingMode` / `DayChooserMode` / `ExceptionCountStyle` and record outputs. `AppointmentController.RepeatingEditor.mapFromAppointment()` / `updateExceptionCount()` / `showEnding(int)` now delegate. 35 tier-1 tests in `RepeatingRuleProjectorTest` (~150 ms, 0 Swing). `AppointmentOverlapHardeningTest` still green — no entity-tier regression.

Original spec follows for the record:

Goal: shake out the pattern on a small extraction before touching the
big ones. Target the **exception-count formatter** and the
**ending-mode → panel-visibility decision** in
`AppointmentController.RepeatingEditor`:

1. Create `rapla-core/.../client/extract/RepeatingEditorState.java`
   (record): `endingMode` (UNTIL / N_TIMES / FOREVER),
   `weekdayPanelVisible`, `intervalPanelVisible`, `monthDayVisible`,
   `weekdayInMonthVisible`, etc.
2. Create `rapla-core/.../client/extract/RepeatingRuleProjector.java`:
   pure functions
   - `RepeatingEditorState project(Repeating repeating, Appointment appointment)`
   - `String formatExceptionCountLabel(int count, I18nBundle bundle)`
   - `ExceptionCountStyle styleForExceptionCount(int count)` (returns
     enum: NORMAL / HIGHLIGHTED — no `Color` import)
3. Refactor `AppointmentController.RepeatingEditor.mapFromAppointment()`
   (L1258–1398) to call `project(...)` once, then apply the returned
   state to its Swing widgets. Same for `updateExceptionCount()`
   (L1216–1234) and `showEnding(int)` (L1236–1255).
4. **Test:** `RepeatingRuleProjectorTest` (tier 1). Cases: daily / weekly /
   monthly / yearly × { until / N-times / forever } × weekday-set
   variants. Exception-count edges (0, 1, 9, 10).

Expected delta: ~150 lines moved out of `AppointmentController` to a
pure class with full test coverage.

**Acceptance:** existing GUI smoke tests (`UndoTests`,
`CalendarEditorTest`) still green; new pure tests cover the projector;
`grep -n 'javax.swing' rapla-core/.../client/extract/` returns empty.

### Phase 2 — Carve out `RepeatingRuleModel` + validator (≈5 days) — **DONE 2026-05-11**

Landed: `RepeatingRuleModel` (record, 41 LOC), `RepeatingRuleWriter` (79 LOC, applies model to live `Repeating` with all clamping rules), `RepeatingRuleValidator` (88 LOC, advisory issues with codes `INTERVAL_LESS_THAN_ONE`, `WEEKLY_WITH_NO_WEEKDAYS`, `UNTIL_END_BEFORE_START`, `N_TIMES_COUNT_LESS_THAN_ONE`, `N_TIMES_COUNT_UNBOUNDED`). `AppointmentController.RepeatingEditor.mapToAppointment()` and the `updateWeekdays()` helper are gone; replaced by a single 35-LOC body that builds a model from widget reads and calls `RepeatingRuleWriter.writeTo(...)`. Net delta on `AppointmentController` after Phases 1+2: **-45 LOC** (1972 → 1927). 25 tier-1 tests in `RepeatingRuleWriterTest` + `RepeatingRuleValidatorTest` (~60 ms combined). Validator is wired through the model but NOT yet surfaced to the user in Swing — that's Phase 6 (presenter promotion) work.

**Bug found while refactoring:** the original `mapToAppointment()` had `if (number != null)` where it meant `if (numberValue != null)` (L1186 pre-refactor). Pre-existing latent NPE when the repeat-count widget returned null; the refactor incidentally fixes it.

Original spec follows for the record:

Goal: take the actual recurrence-rule logic — not just UI projection —
out of `AppointmentController`.

1. `RepeatingRuleModel` (rapla-core, record-style mutable):
   `type` (DAILY / WEEKLY / MONTHLY / YEARLY), `interval`,
   `weekdays: Set<Integer>`, `endingMode`, `endDate: LocalDate?`,
   `repeatCount: int?`, `exceptions: SortedSet<LocalDate>`.
2. `RepeatingRuleValidator`: pure functions
   - `ValidationResult validate(RepeatingRuleModel m)` — interval > 0,
     weekday set non-empty if WEEKLY, end-date after start, repeat-count
     ≥ 1, etc.
   - `RepeatingRuleModel applyTo(RepeatingRuleModel m, Appointment a)`
     (or `Repeating writeBack(RepeatingRuleModel m, Repeating target)`)
3. Refactor `AppointmentController.RepeatingEditor.mapToAppointment()`
   (L1150–1196) to read widget state → `RepeatingRuleModel` →
   `validator.validate(...)` → if OK, `writeBack(...)`. Validation
   errors surface via `view.showWarning(...)` (mirroring the sample
   `ReservationPresenter.changeAttribute(...)`).
4. **Test:** `RepeatingRuleValidatorTest` (tier 1) — 25+ cases.
   `RepeatingRuleModelTest` (tier 1) — round-trip with `Repeating`
   entity.

Expected delta: ~250 lines moved out, AppointmentController is now
~1500 LOC, with the recurrence math testable.

### Phase 3 — `AllocationConflictModel` carve-out (≈5 days) — **PARTIAL — model + refactor DONE 2026-05-11; tier-1 tests DEFERRED**

Landed: `org.rapla.client.edit.reservation.AllocationConflictModel` (rapla-core) with static `compute(allocatable, appointments, bindings, permissionController, user, today) → AllocationOutcome` and standalone `isAllowed(...)`. Output is a record (`conflictingAppointments[]`, `conflictCount`, `permissionConflictCount`, `aggregateRequestStatus`). `AllocatableSelection`'s 7-line `isAllowed`, 37-line `calcConflictingAppointments`, and the inner `AllocationRendering` class are all gone — replaced by a 5-line delegate to `AllocationConflictModel.compute(...)`. Six call sites updated to record-accessor syntax. Net delta on AllocatableSelection: **-64 LOC** (2463 → 2412 working-copy, after accounting for adjacent in-flight edits; carve-out itself is ~-70 lines).

**Tier-1 tests deferred.** `Allocatable` is an interface with ~30 methods inherited from `EntityPermissionContainer`/`Classifiable`/`Annotatable`/etc. A useful stub is brittle and high-cost. Two paths forward:
- Cover via PRD 025's `HeadlessPresenterTestSupport` once it lands (tier 2 with `FacadeTestSupport` + a real `Allocatable` from `testdefault.xml`).
- Cover via PRD 024's `/edit/check-conflicts` MockMvc test, which exercises the same model end-to-end through a Spring controller.

Existing GUI tests (`UndoTests`, `CalendarEditorTest`) and the live Swing client are the backstop until then. Confirmed compile-clean across the reactor; `AppointmentOverlapHardeningTest` (the conflict-arithmetic baseline) still green.

Original spec follows for the record:

Goal: lift the conflict-detection inner loop out of `AllocatableSelection`.

1. `AllocationConflictModel` (rapla-core):
   - Input: `Appointment[] appointments`, `Allocatable`,
     `Map<ReferenceInfo, Collection<Appointment>> allocatableBindings`,
     `PermissionController`, `User`, `LocalDate today`.
   - Output: `AllocationOutcome` record (per-appointment boolean array
     for conflict, conflict count, permission-denied count,
     request-status aggregate).
   - Function: `compute(...)` — pure, no Swing.
2. Refactor `AllocatableSelection.calcConflictingAppointments`
   (L1278–1314), `isAllowed` (L1261–1267), `getAllAppointmentsFor`
   (L1243–1258), `getRestriction` (L1230–1241) to delegate.
3. `paintAllocation` (L1316–1343) keeps its Graphics2D code but
   consumes `AllocationOutcome`.
4. **Test:** `AllocationConflictModelTest` (tier 1, stub
   `PermissionController` or use a `FacadeTestSupport` fixture in
   tier 2). Cases: zero appointments / all-conflict / partial conflict
   / `hold-back-conflicts` annotation / permission-denied appointments /
   mixed request statuses.

Expected delta: ~120 lines moved out; testable; ready for PRD 024 to
expose `compute(...)` over REST.

### Phase 4 — `CalendarBlockLayout` + `ReservationBlockStyle` (≈5 days)

Goal: extract block geometry and styling — shared by 5 plugins.

1. `CalendarBlockLayout` (rapla-core):
   - Input: list of `AppointmentBlock` for one column/day, column
     width, time range, minutes-per-pixel.
   - Output: list of `BlockBounds(x, y, width, height,
     overlapDepth, overlapIndex)`.
   - Algorithm: sweep-line overlap detection + column packing.
2. `ReservationBlockStyle` (rapla-core):
   - Input: `Appointment`, `Allocatable[]`, `User`,
     `PermissionController`, request status.
   - Output: `BlockStyle` record (color RGB int, border style enum,
     icon enum — no `java.awt.Color`).
3. Refactor `AbstractRaplaSwingCalendar` (~462 LOC) and
   `SwingRaplaBlock` (~473 LOC) to consume the model. JComponent
   `paint*` only translates `BlockStyle` → `Color`.
4. **Test:** `CalendarBlockLayoutTest` (tier 1) — overlap, edge cases
   (zero-width block, midnight wrap, all-day vs. timed mixing).
   `ReservationBlockStyleTest` (tier 1) — permission-coloring matrix.

Expected delta: ~400 lines moved out across two files; layout/style
logic reusable by the Angular client and by HTMLWeekViewPresenter
(which today re-implements its own layout).

### Phase 5 — `ClassificationFilterBuilder` (≈3 days)

Goal: lift `ClassifiableFilterEdit` (938 LOC) filter-assembly logic
into a headless builder.

1. `ClassificationFilterBuilder` (rapla-core): takes a list of
   `(Attribute, Operator, Value)` triples and assembles a
   `ClassificationFilter` for the facade.
2. Refactor the inner classes of `ClassifiableFilterEdit` to consume
   the builder. The JTable / JComboBox panel construction stays in
   Swing.
3. **Test:** `ClassificationFilterBuilderTest` (tier 2 if it needs a
   facade for attribute lookup; tier 1 otherwise). Cases per
   `AttributeType` × `Operator`.

Expected delta: ~200 lines moved out.

### Phase 6 — Promote sample to production reservation presenter (≈4 days)

Goal: move
`org.rapla.client.edit.reservation.sample.ReservationPresenter` /
`ReservationView` out of `.sample` and use them to back a refactored
`ReservationEditImpl`.

1. Rename `.sample` → `org.rapla.client.edit.reservation` (drop the
   sample package).
2. Expand `ReservationView` to cover what `ReservationEditImpl` does
   today: appointment list, allocatable section, info section, save
   / delete / cancel buttons, conflict-warning surface.
3. Wire `ReservationEditPresenter` (production) through
   `EditTaskPresenter` so that the existing edit-task workflow uses
   it.
4. **Test:** `ReservationEditPresenterTest` (tier 2 — needs a
   facade for `facade.edit(...)`, but no Swing). Cases: open new /
   open existing / save / cancel / change classification / add
   appointment / delete appointment / change time.
5. Existing GUI tests (`UndoTests`, `CalendarEditorTest`) still
   green.

Expected delta: `ReservationEditImpl` shrinks from 596 → ~200 LOC
of pure widget binding.

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

No tier-3 / tier-4 tests are added; existing `SwingClientStartIntegrationTest`
and `HeadlessClientNameResolutionIntegrationTest` guard end-to-end
wiring as before.

### Architecture test

Add a single ArchUnit (or `grep`-based) test that fails CI if
`rapla-core/src/main/java/org/rapla/client/extract/**` ever imports
`javax.swing.*`, `java.awt.*`, or `org.rapla.client.swing.*`. This
keeps the carved-out classes truly headless.

## Risks

1. **The Swing refactor breaks the dialog.** Mitigation: the existing
   `UndoTests` and `CalendarEditorTest` exercise the appointment edit
   dialog end-to-end. Run them after each phase. They are tagged
   `e2e`; explicitly include them in the CI lane for this PRD.
2. **`AppointmentController` is currently modified (M) in `git status`.**
   Coordinate the first phase with whatever change is in flight there.
   (AGENTS.md §7 — check `stat -c '%Y' <file>` against
   `git log -1 --format=%ct -- <file>` before editing.)
3. **PermissionController is heavy.** The Phase 3 extraction may
   surface that `PermissionController` itself wants splitting into a
   pure `PermissionRules` evaluator + a facade-aware loader. Don't
   chase that here; pin the current interface, note as follow-up.
4. **Layout extraction (Phase 4) is the most algorithmic phase.**
   The current code has subtle paint-order dependencies (overlapping
   blocks, all-day rows above timed rows, conflict-stripes painted on
   top). Test in two passes: first pass keeps Swing behaviour
   byte-identical; second pass moves overlap-detection into the pure
   model. Do not refactor algorithm and refactor structure in one
   commit.
5. **The "sample" presenter is incomplete.** The Phase 6 promotion is
   effectively rewriting `ReservationEditImpl`, not a mechanical
   refactor. Scope can balloon. Mitigation: time-box at 4 days; if
   it overruns, ship Phases 1–5 (mechanical extractions) and defer
   Phase 6 to a follow-up PRD.

## Open Questions

1. **Package name.** `org.rapla.client.extract` is a placeholder.
   `org.rapla.client.presenter.model`? `org.rapla.client.headless`?
   Open.
2. **Where does the View interface live?** Today `RaplaWidget<T>` is
   in `org.rapla.client`. New `*View` interfaces belong next to their
   presenter (`org.rapla.client.edit.reservation.ReservationView`),
   not in a `view/` subpackage — open whether to enforce this.
3. **Inner `Presenter` interface vs. separate class.** The sample uses
   `ReservationView.Presenter` (inner). Some places (`TaskPresenter`)
   are top-level. Decide and document one style in
   `docs/architecture/mvp-pattern.md` (PRD 022).
4. **Should `RepeatingRuleModel` replace the existing `Repeating`
   entity?** No — `Repeating` is a domain entity persisted to XML /
   JDBC; the model is an editor-facing view of it. They translate
   via `writeBack(...)`. But note PRD 014's `Appointment` long→
   java.time migration when designing field types — use
   `LocalDate` for end-dates, not `Date`.
5. **Headless presenter test base.** Should we add a
   `HeadlessPresenterTestSupport` companion to `FacadeTestSupport` that
   gives `(facade, view: MockView, clock)`? Yes, probably — but defer
   until the first three phases have shown what the common shape is.
   Add to PRD as Phase 7 follow-up.

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
