# PRD 023 — Presenter / Model carve-out from Swing components

**Status:** in-progress — Phases 1, 2, 3 (model+refactor+tier-1 tests), 5, 7, 8, 9 landed 2026-05-11. Phase 6 re-aimed; 6a + 6c landed (6b skipped); 6e-partial landed 2026-05-12. **Phase 10 (EventCheck carve-outs)** DONE 2026-05-12 — `ReservationWarning` shared DTO + `DefaultReservationWarnings` (4 rules) + `RequestAllocationWarnings` + `HolidayWarningModel` under `org.rapla.client.edit.check`. 31 new tier-1 tests. **380+ tier-1 tests in rapla-core.** Phase 4 superseded. AppointmentController −45 then −34 (6e); AllocatableSelection −64 then −60 (6a); ClassifiableFilterEdit −46; PasswordChangeAction −33; RaplaObjectActions −38; ClassificationEditUI −22 (6c); DefaultReservationCheck −34, HolidayExceptionCheck −22, RequestAllocationCheck −3 (Phase 10). Phase 9 added `ResourceSelectionState` + 27 tests. AllocatableSelection legacy field migration (Finish-A) DONE. Arch-test gate (`NoSwingInRaplaCoreClientEditTest`). `docs/architecture/mvp-pattern.md` published. Harness validated via `PasswordChangePolicyHarnessTest`.
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-11

## Goals

Two primary goals, both about the **decision logic** inside the
existing Swing components — **not** about replacing the Swing UI:

### Goal 1 — Make the Swing UI better testable

Today's edit-tier Swing classes (`AppointmentController` 1972 LOC,
`AllocatableSelection` 2412 LOC, `ClassifiableFilterEdit` 938 LOC,
`ReservationInfoEdit` 797 LOC, `ReservationEditImpl` 596 LOC,
`EditTaskPresenter` 639 LOC) have ~5 % test coverage. Most decisions
are verified by hand-clicking through dialogs. Recurring regressions
(Jackson-3 `final` fields hit 5×, date-migration script collateral,
undo-stack drift) are the symptom. Carving out pure-logic chunks into
rapla-core unlocks tier-1 unit testing of those decisions — the Swing
classes shrink slightly and the test-bench grows substantially.

### Goal 2 — Identify logic worth reusing in the Angular client

The Angular UI (PRD 026 / PRD 028) **will look very different** from
the Swing one — power-search shell, different edit-flow surfaces,
different overall layout. So this PRD is **not** producing presenter
classes that Angular will drive. It is producing **pure-Java
decision and computation classes** in rapla-core that Angular calls
via REST (PRD 024), and that the existing Swing classes also
delegate to. The same `RepeatingRuleValidator.validate(...)` decides
"is this recurrence rule valid" regardless of which UI asked the
question.

### Anti-goals

- **Not** building a production `ReservationEditPresenter` that
  drives `ReservationEditImpl` via a `ReservationView` interface.
  That design assumed Angular would mirror the Swing edit flow; it
  won't. The dead-code sample
  (`org.rapla.client.edit.reservation.sample.ReservationPresenter`)
  has been removed (2026-05-11) so it doesn't keep being read as a
  roadmap signal.
- **Not** rewriting `ReservationEditImpl` or other dialog classes
  with a Presenter↔View split — too risky for a UI tier that will be
  replaced wholesale by Angular, not refactored. The dialog stays as
  it is until Angular ships and the Swing tier is retired.
- **Not** chasing every small permission check or display formatter
  into rapla-core. Threshold for extraction: at least 20 LOC of
  decision logic AND clear Angular reuse value (REST endpoint or
  TS-port avoidance).

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
3. **The pattern already works elsewhere.** PRD 020's
   `FieldRenderer` / `PanelRenderer` extracted a similar split for
   admin panels and produced 10 tier-1 tests in `FieldRendererTest`.
   `CalendarPlacePresenter` / `ConflictSelectionPresenter` /
   `ResourceSelectionPresenter` already exhibit the
   inner-`Presenter`-in-View shape on the calendar tier — they're
   the precedents this PRD's tier-1 carve-outs piggy-back on.
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

- **Document the carve-out pattern** in
  `docs/architecture/mvp-pattern.md` (landed 2026-05-11). The doc
  records package layout, the carve-out recipe, the harness usage,
  and the AGENTS.md §12 permission-leak rule with reference
  implementations. New contributors read this first.

*(The "promote sample to production" entry that was here previously
has been dropped — see the re-aimed Phase 6 below for the new
direction.)*

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

**Tier-1 tests landed 2026-05-11** in `AllocationConflictModelTest` (rapla-core, 9 tests, ~110 ms). Uses `java.lang.reflect.Proxy` to stub `Allocatable` / `Reservation` / `Appointment` — only the four methods `compute()` actually touches need to be answered, so the stubs are 6 lines each. `PermissionController` subclassed with `canAllocate` overridden. Cases: empty appointments, all-permitted-no-bindings, binding-driven conflict, hold-back-conflicts annotation suppression, permission-denial flag + count, hold-back-suppresses-flag-but-not-permission-count, request-status aggregation (first non-null wins), null-request-status, mixed binding-and-permission accumulation. Plus the `/edit/check-conflicts` MockMvc tests in `ReservationEditControllerIntegrationTest` cover the same model end-to-end via Spring.

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

### Phase 4 — `CalendarBlockLayout` + `ReservationBlockStyle` — **SUPERSEDED — already done in rapla-core**

Audited 2026-05-11. Both halves of this phase are already separated:

- **Block colour decision** lives in `rapla-core/.../plugin/abstractcalendar/RaplaBlock.java:133` (`getColorsAsHex()`) plus `isException`, `isRequest`, `isMovable`, `isBlockSelected`. Already consumed by `HTMLRaplaBlock` and `SwingRaplaBlock` symmetrically. No Swing imports in the decision layer. The Swing paint code only does alpha blending and `g.setColor(...)` — that's true rendering, not a carve-out target.
- **Block layout** lives in `rapla-core/.../components/calendarview/{AbstractGroupStrategy, GroupStartTimesStrategy, BestFitStrategy}.java`. The geometry / sweep-line / overlap-detection is already a pure `BuildStrategy` implementation; the Swing classes just paint the resulting `Block` placements.

The PRD over-estimated this phase. **Skip.** Remaining cleanup (alpha-blend Swing glue, hex→`Color` lookup cache) is true rendering, not pure logic.

Original spec follows for the record:

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

### Phase 5 — `ClassificationFilterBuilder` (≈3 days) — **PARTIAL — operator catalog DONE 2026-05-11**

Landed: `org.rapla.client.edit.filter.ClassificationFilterOperators` (rapla-core, 115 LOC) — centralised catalog mapping `AttributeType` ↔ valid operator strings ↔ JComboBox index. Replaces the two duplicated if/else ladders in `ClassifiableFilterEdit.RuleRow.{getOperatorValue, setOperatorValue}` (66 LOC → 20 LOC, **-46 LOC**). API: `operatorsFor(type)`, `defaultOperatorFor(type)`, `operatorAt(type, index)`, `indexOf(type, op)`, `hasOperatorChoice(type)`. Preserves the legacy `"is"` → `=` alias on numeric types so existing serialized filters still load. 13 tier-1 tests in `ClassificationFilterOperatorsTest` — pins the bidirectional round-trip so future renames can't desync the two directions.

The larger `ClassifiableFilterEdit.getFilter()` / `mapFrom(...)` flow is already mostly separated — it iterates rule rows and calls into rapla-core's `ClassificationFilter`. The pure-logic chunk worth carving was the operator catalog; the rest is widget binding.

Original spec follows for the record:

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

### Phase 6 — Continued opportunistic carve-outs from edit-tier classes (re-aimed 2026-05-11)

**Original framing dropped.** PRD 026 / PRD 028 confirm the Angular UI
will look very different from the Swing edit dialog (power-search
shell, redesigned edit flow). So there's no production
`ReservationEditPresenter` to build — Angular will not drive the
sample's `ReservationView` interface. Promoting the sample would
produce a presenter shaped for a UI that's not going to exist.

The Swing `ReservationEditImpl` and friends therefore **stay as they
are** until the Swing tier is retired. Rewriting the dialog with a
Presenter↔View split is anti-goal #2 of this PRD.

What Phase 6 actually becomes: **keep extracting pure-logic chunks
from the edit-tier god-classes**, on the same terms as Phases 1–5
and 8 — Swing class delegates to a rapla-core class, tier-1 tests
pin the behaviour, REST endpoint (PRD 024) consumes the same class
if the Angular client needs the decision. No `*View` interfaces
added; no dialog rewrites.

#### Audit — remaining extractables (each independently shippable)

| # | Source | Chunk | Approx LOC | Angular reuse value | REST candidate |
|---|---|---:|---:|---|---|
| 6a | `AllocatableSelection` | **Row status decision** — extracted as `AllocatableRowStatusModel` (rapla-core). Pure function `Inputs` → `Status` enum (AVAILABLE / NOT_ALWAYS_AVAILABLE / REQUEST / CONFLICT / FORBIDDEN). Plus `hasPermissionToAllocate(...)` carved out alongside. Swing `getIcon(...)` is now a 1-line delegate + a 7-line enum→Icon switch. **DONE 2026-05-11** — 10 tier-1 tests in `AllocatableRowStatusModelTest`. |
| 6b | `ReservationEditImpl` | **Dirty-state detection.** **SKIPPED** — audit found `EditTaskView.hasChanged()` currently returns hardcoded `true` in all impls. Carving would be NEW behaviour (real entity-diff), not a refactor. Reopen if/when the always-prompt UX changes. |
| 6c | ~~`ReservationInfoEdit`~~ → `ClassificationEditUI` | **Field visibility per attribute** — `(visible, writable)` decision per attribute across the multi-edit object list, with conservative semantics. Extracted as `ClassificationFieldVisibility.Result resolve(...)` (rapla-core). Was inlined in `ClassificationEditUI.createEditField(...)` — found there, not in `ReservationInfoEdit` as the audit guessed. **DONE 2026-05-11** — 7 tier-1 tests in `ClassificationFieldVisibilityTest`. |
| 6d | `EditTaskPresenter` | **Activity-type → entity-type dispatch** — which inner edit flow handles `editevent` / `editallocatable` / `edituser` / etc. | ~50 | Medium — Angular router has its own dispatch but the entity-type→handler map is the same. | No — client routing. |
| 6e | `AppointmentController` | **`RepeatingType` → radio-button mapping** + **weekday-set update on anchor-shift**. Extracted as `RepeatingRuleProjector.choiceFor` / `repeatingTypeFor` + `weekdaysOnAnchorShift(...)` (rapla-core). The `RepeatingType ↔ radio button` mapping was duplicated in `setAppointment` (~20 LOC) and `setRepeatingType` (~16 LOC); replaced by a one-line `choiceFor(...)` + a 7-line `switch` on the choice in the view. `resetWeekdays(int)` rewrote from imperative weekday-iteration to one delegate call. **DONE 2026-05-12** — 9 new tests in `RepeatingRuleProjectorTest`. Undo-command CONSTRUCTION itself (the broader L120 LOC item) was deferred — the entity-mutation half is already pure (`Repeating.addExceptions` / `setEnd` / etc.); the command-class shells are thin glue not worth carving. |
| 6f | `AllocatableSelection` | **`AllocationTextField` index-display logic** — which appointment indices light up which allocatable row. | ~50 | Medium — Angular table renders the same indicators. | No — derived from already-fetched data. |

#### Priority

Ship 6a, 6b, 6c first (high Angular reuse, smallest LOC each). 6d
adds value when Angular's edit-flow router lands. 6e/6f are nice-to-have
testability wins with lower Angular leverage.

Each carve-out follows the same recipe documented in
`docs/architecture/mvp-pattern.md` §"Adding a new carve-out":
extract to rapla-core, tier-1 tests, Swing class delegates,
existing GUI tests stay green.

Note: this section deliberately doesn't lay out a single big plan —
each extraction is independent, sized for one focused session.
Pick one when the time is right.

### Phase 7 — Name-search field next to the filter button (quick win, ≈0.5 day) — **DONE 2026-05-11**

Landed:
- `org.rapla.client.edit.search.NameSearchMatcher` (rapla-core) — substring, case-insensitive, diacritic-folded (NFD + Mn-strip), ß→ss for German user base, multi-word AND. 15 tier-1 tests in `NameSearchMatcherTest`.
- `ReservationEditSelection.nameSearchTerm` + `filterByNameSearch(Collection<Allocatable>, Locale)` — 6 new tier-1 tests in `ReservationEditSelectionTest`.
- `AllocatableSelection.nameSearchField` JTextField with placeholder + `DocumentListener` → `selection.setNameSearchTerm(...)` → `refreshCompleteTreeForNameSearch()` which re-binds `completeModel` with `selection.filterByNameSearch(getAllAllocatables(), getRaplaLocale().getLocale())`. No debounce (matcher is sub-millisecond at typical sizes).
- Sub-panel groups the search field + the existing filter button at column 4 of the leftPanel `TableLayout`. No layout changes elsewhere.
- `ResourceSelectionViewSwing` (main calendar-place resource sidebar): `nameSearchField` JTextField mounted in `buttonsPanel.CENTER` next to the existing filter button on `EAST`. `DocumentListener` records the term and re-runs `updateTree(...)`; `generateTree(...)` post-prunes the `AllocatableNodes` tree via a local `pruneByName(...)` that uses `NameSearchMatcher.matchesPrepared` per leaf and drops empty type/categorization folders. Tooltip uses the existing `search` i18n key. No new presenter state — view-local transient.
- New i18n keys `search` (`"Search"` / `"Suche"`) and `search.placeholder` (`"Search…"` / `"Suchen…"`) in `RaplaResources` / `_de`.
- The classification filter button still works orthogonally — the name search is an AND on top of whatever the structured filter produced (the search filters the already-classification-filtered list from `getAllAllocatables()` in `AllocatableSelection`, or post-prunes the already-classified tree in `ResourceSelectionViewSwing`).

Also landed (`ResourceSelectionViewSwing` sidebar):
- `hiddenSelectionStatus` JLabel below the `buttonsPanel`. Shows
  `"{N} selected hidden by search — click replaces selection, Ctrl-click adds"`
  whenever the search term is non-empty AND at least one currently-selected
  allocatable doesn't match the term. Counts are recomputed on every
  search-term change AND every `update(filter, model, selectedObjects)` call.
  New i18n key `search.hidden_status` (EN+DE).
- Selection semantics (Option 6 from the design discussion):
  - Tree shows matches only.
  - Selection state stays untouched by search — `model.getSelectedObjects()`
    is never written by the search field.
  - The status line tells the user how many selections are currently hidden.
  - Plain click on a visible result still triggers `JTree`'s default
    "replace selection" behaviour — the status line is **informational, not
    protective**. Option (b) — sticky additive selection while search is
    active — was discussed but deferred; the status line covers the common
    case (user reads it before clicking).

Not landed (deferred):
- `ClassifiableFilterEdit` doesn't have its own search field yet — the dialog is heavier and the rule list there is typically small enough that name-search adds less value.
- **Event search in the calendar view (`MultiCalendarViewSwing`) — explicitly dropped on the Swing tier.** Initial attempt added a transient `eventNameSearch` field to `CalendarSelectionModel` + `CalendarModelImpl` and filtered the appointment binding map at query time. Reverted 2026-05-11 after a design discussion: the per-view search is a UX affordance, not a calendar-model concept, and putting it on the model mixed transient view state into persistent calendar state. Event search will land in the Angular client as part of PRD 028's power-search (single ranked dialog across allocatables + reservations + conflicts).
- Sticky additive selection while search is active (Option b from the same design discussion) — not landed. Status line informs; click semantics unchanged.

Original spec follows for the record:

**Origin:** extracted from PRD 021 (`wont-fix`) when the stub-mode redesign
was dropped in favor of the Angular frontend (PRD 026). The search box
itself was a small UX win independent of the stub backend; it works
fine against the existing full-resource client cache, so it ships here
on the Swing tier as-is.

Goal: add a one-line text field next to the existing filter button in
**every `AllocatableSelection`** instance (resource picker on a
reservation, calendar configuration, exchange/iCal export, etc.) and on
the **reservation filter** (`ClassifiableFilterEdit`). Typing in the
field narrows the visible tree to allocatables whose
`getName(locale)` contains the substring (case-insensitive,
diacritic-folded). Empty field → unfiltered. The classification filter
button keeps working orthogonally — the name search is an AND on top of
whatever the structured filter already produced.

1. **Model addition.** Add a `nameSearchTerm: String` (or empty) field
   to `AllocatableSelectionModel` (Phase 1) and the equivalent on
   `ClassifiableFilterModel` (Phase 5). Re-evaluate
   `visibleAllocatables` whenever the term or the structured filter
   changes. Matching helper lives in `rapla-core` and is tier-1 unit
   tested (diacritic folding, multi-word, case, empty string).
2. **View binding.** `AllocatableSelection` view adds a `JTextField`
   above (or to the left of) the filter button with a placeholder
   "Suchen…" / "Search…". `ClassifiableFilterEdit` view does the same.
   A `DocumentListener` calls `presenter.setNameSearchTerm(text)` on
   every keystroke (no debounce needed at typical tree sizes; revisit
   if it noticeably lags above ~5 000 resources).
3. **Persistence.** The search term is **transient** — it does NOT
   persist into `CalendarSelectionModel` or any saved view. It's a
   live picker affordance, not a stored filter rule. (The structured
   filter rules still persist as today.)
4. **i18n.** New key `search.placeholder` in
   `RaplaResources` / `RaplaResources_de.properties`.
5. **Tests:**
   - Tier 1: `AllocatableNameSearchTest` — substring match,
     case-insensitive, diacritic folding ("Müller" matches "muller"),
     multi-word AND, empty string returns all.
   - Tier 1: `AllocatableSelectionModelTest` (existing) — extend with
     "search term + structured filter compose as AND" case.

Expected delta: ~50 LOC of new Swing widget binding +
~30 LOC of tested matcher logic in rapla-core. Net feature, no LOC
reduction.

### Phase 8 — Action-class policy carve-outs (opportunistic) — **DONE 2026-05-11**

Pattern: action / menu classes in `rapla-client/.../menu/...` often have a small `isEnabled()` or `validate(...)` method that's pure logic mixed with a Swing action's `setEnabled` / dialog-popping side effects. Carve the decision into rapla-core as a `*Policy` class; the Swing class delegates.

Landed:

- **`PasswordChangePolicy`** (rapla-core, 75 LOC) — carved out of `PasswordChangeAction`. Three pure functions: `canChangePassword`, `requiresOldPassword`, `validate`. 13 tier-1 tests in `PasswordChangePolicyTest`. Plus `PasswordChangePolicyHarnessTest` (rapla-server, 4 tier-2 tests) — first production use of `HeadlessPresenterTestSupport` against real `testdefault.xml` users; **validates the harness scaffold from PRD 025 works end-to-end**. `PasswordChangeAction` shrinks by 33 LOC.
- **`RaplaObjectActionPolicy`** (rapla-core, 110 LOC) — carved out of `RaplaObjectActions.isEnabled()`. Branches per action type (NEW/EDIT/DELETE/EDIT_SELECTION/DELETE_SELECTION) × entity type (Allocatable/Category/other) × admin status. Two overloads: production (takes `PermissionController`) + test-friendly (takes lambda predicates), so tier-1 tests don't have to construct or subclass the full controller (whose `isRegisterer` is final). 15 tier-1 tests in `RaplaObjectActionPolicyTest`. `RaplaObjectActions.isEnabled()` shrinks from 52 LOC to 11 LOC.

This is **not the same shape as Phases 1–5**. Those carved business-logic out of widget-binding code; Phase 8 carves decision logic out of `javax.swing.Action` shells. Same goal (tier-1 testability + Angular reuse), different starting point. Future similar carve-outs in `MenuFactoryImpl.java` (871 LOC, ~12 permission decisions) are deliberately not pursued here — each is 1–3 lines and the cost/benefit doesn't justify the touch. If we ever produce an Angular menu, those return to the table.

### Architecture invariant — **DONE 2026-05-11**

`NoSwingInRaplaCoreClientEditTest` (rapla-core/src/test/java/.../client/edit/) walks the source tree of `rapla-core/src/main/java/org/rapla/{client/edit,plugin/calendarview,plugin/reservationedit}` and fails the test if any `.java` file imports `javax.swing.*`, `java.awt.*`, `org.rapla.client.swing.*`, `org.rapla.facade.client.*`, or `org.rapla.facade.server.*`. Sanity-tested in both directions (plant a deliberate violation → fails with exact file:line; remove → green).

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
