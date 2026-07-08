# Reservation-edit UI — functional inventory (Swing)

Companion to [reservation-edit.md](reservation-edit.md), which documents the *flow*
(clone semantics, EDT/Promise, EventCheck chain, AppointmentController auto-correction
rules, wire model). This page inventories the *user-facing capabilities* of the Swing
dialog — every pane, control and behaviour — as the checklist a new frontend (the SPA
event sheet, PRD 091) must consciously cover or drop.

Paths: `RES` = `rapla-client/src/main/java/org/rapla/client/swing/internal/edit/reservation/`.
i18n keys from `rapla-core/src/main/resources/org/rapla/RaplaResources.properties`.

## 1. Dialog layout

`ReservationEditImpl` (`RES/ReservationEditImpl.java`) is a single vertically-stacked
panel (no tabs) with a top toolbar and a bottom button bar:

1. **`ReservationInfoEdit`** — event type + classification attribute fields
   (+ permissions in the "additional" view) — row 0.
2. **`AppointmentListEdit`** — appointment list + per-appointment editor
   (`AppointmentController`) — row 1, titled "Appointments".
3. **`AllocatableSelection`** — the resource picker — row 2 (fills remaining space),
   titled "Allocations".

- Toolbar: Save, Delete, Undo, Redo, pluggable `ReservationToolbarExtension` buttons,
  status label (`ReservationEditImpl.java:192-253`). Bottom bar: Save + Abort.
- **Two view modes:** switching `ReservationInfoEdit` to its "Additional Information /
  Permissions" tab hides the appointment and allocation panes and expands the
  classification editor to full height (`ReservationEditImpl.java:549-570`).
- **Permission gating:** no `canModify` → appointment + info editors recursively
  disabled (read-only dialog); Delete only with `canDelete`
  (`ReservationEditImpl.java:402-407`).
- **Template mode:** status label shows "edit templates" for template reservations;
  calendar buttons and request-status logic are template-aware
  (`ReservationEditImpl.java:376`, `AllocatableSelection.java:464-467,778-796`).

## 2. Classification pane (`ReservationInfoEdit`)

- Event-type dropdown over the *creatable* reservation DynamicTypes; disabled when only
  one exists; type change re-maps attributes and is undoable
  (`ReservationInfoEdit.java:159-173,641`).
- Attribute fields laid out per DynamicType schema; `no-view`-annotated attributes
  hidden; "main" vs "additional" attributes split across the two view modes
  (`ReservationInfoEdit.java:411-512`).
- **Permissions editor** (`PermissionListField`, levels READ/EDIT/ADMIN) in the
  additional view, visible only with `canAdmin`
  (`ReservationInfoEdit.java:116-118,216-221`).
- Every field edit is an undoable command.

## 3. Appointment list (`AppointmentListEdit`) + editor (`AppointmentController`)

- List auto-sorted by start (no manual reorder, no copy button); each row: index
  number, formatted summary, repeating/single icon, repeating summary, exception
  summary (`AppointmentListEdit.java:94-120,220-239`).
- **New** clones the selected/last appointment as template (exceptions cleared), or
  creates a 1-hour appointment at worktime start when the list is empty; **Delete**
  removes selected appointments (restriction links preserved for undo). Both undoable
  (`AppointmentListEdit.java:249-259,335-449`).
- Selecting a row switches the detail editor (selection change itself is undoable).
- **Detail editor** (`AppointmentController`, dual-mode single/repeating —
  auto-correction rules documented in [reservation-edit.md](reservation-edit.md)):
  - Repeating-type radio: none / weekly / daily / monthly / yearly.
  - Single mode: start/end date+time, all-day toggle.
  - Repeating mode: interval, weekday checkboxes, day-in-month / weekday-in-month /
    month choosers, ending mode (until-date / n-times / forever), start/end time,
    all-day, day-span ("same day" / "next day" / "on day x").
  - **Exceptions dialog:** add date-*range* exceptions (expanded to single days),
    multi-select remove; count badge on the button
    (`AppointmentController.java:753-1519`).
  - **Convert to single events ("split"):** one repeating appointment → N single
    appointments, restrictions migrated; only for finite series
    (`AppointmentListEdit.java:462-545`).
  - Extension point `AppointmentEditExtensionFactory` adds extra fields
    (`AppointmentController.java:184-193`).
  - **Date/time picker ergonomics** (`org.rapla.components.calendar`, mechanics in
    [reservation-edit.md](reservation-edit.md)): the four begin/end widgets are
    interlocked (start drags end duration-preserving; end-before-start
    auto-corrects); block-wise keyboard editing in the text fields (←/→ block,
    ↑/↓ increment with rollover, PageUp/Down big steps, digit auto-advance);
    time dropdown with configurable granularity (default 15 min), the worktime
    window highlighted and a per-slot duration hint ("(2 h)"); weekday label
    rendered inside the date field.

### The "free appointment" search (next free slot)

- Button **"free appointment >>"** on the appointment-list toolbar; enabled only when
  exactly one appointment is selected (`AppointmentListEdit.java:110-114,289`).
- Calls `RaplaFacade.getNextAllocatableDate(allocatables, appointment,
  CalendarOptions)` → REST `POST /api/storage/allocatable/date/next`; on success
  *moves* the appointment to the found start (undoable); on failure shows
  "No free appointment found" (`AppointmentController.java:1810-1839`).
- Search parameters come from **global `CalendarOptions`** (worktime start/end,
  excluded weekdays, rows-per-hour granularity), *not* from the dialog; the search
  covers only the appointment's **already-allocated** resources and shifts a single
  appointment. Server side is a brute-force linear scan of up to a year
  (`LocalAbstractCachableOperator.getNextAllocatableDateSync`).

## 4. Resource picker (`AllocatableSelection`, ~2400 lines)

Two side-by-side tree-tables in a split pane:

- **Left ("Selectable"):** all allocatables the user `canAllocate` **or** may
  *request* (`isRequestOnly`), grouped into the DynamicType classification tree
  (`AllocatableSelection.java:545-558,1118`).
- **Right ("Selected"):** the resources allocated to the reservation, flat, with the
  per-appointment restriction column "Selected on".

Controls: add/remove buttons (also double-click), calendar buttons (open a calendar
view for the selected resources at the first appointment start), right-click context
menu (object menu via `MenuFactory`). Add/remove are undoable.

### Availability status per resource

Row icons computed by the pure model `AllocatableRowStatusModel` (rapla-core, no
Swing), fed by the server's `getAllocatableBindings` map via
`AllocationConflictModel.compute`:

| State | Meaning |
|---|---|
| `AVAILABLE` | no appointment conflicts |
| `NOT_ALWAYS_AVAILABLE` | some (not all) appointments conflict — **the "partially free" state** |
| `REQUEST` | user cannot allocate directly but may request (`RequestStatus.REQUESTED` auto-set on add) |
| `CONFLICT` | all appointments conflict |
| `FORBIDDEN` | no allocate permission |

Conflict computation honors the resource annotation
`KEY_CONFLICT_CREATION = ignore` (hold-back conflicts): such resources never mark
conflicts (`AllocationConflictModel.java:71-113`).

### Restriction editing (the resource↔appointment assignment)

The "Selected on" cell paints **one colored chip per non-conflicting appointment**
(color = appointment index, matching the appointment list), or green "Every
appointment" when unrestricted (`AllocatableSelection.java:1313-1378`). Clicking the
cell opens a **stay-open checkbox popup menu**:

- Radio "Every appointment" (= no restriction, the sparse-map default) vs
  "Selected on".
- One checkbox per appointment, labeled `<n>: <summary>`, background-colored by
  appointment index; conflicting appointments flagged with the conflict icon.
  Choosing "Selected on" pre-selects all *non-conflicting* appointments
  (`AllocatableSelection.java:1502-1713`).

So the Swing "matrix" is a **per-resource popup of appointment checkboxes** — there is
no grid overview showing all resources × all appointments at once. Multi-reservation
editing renders partially-allocated resources italic, fully-allocated bold.

### Picker search + filter

- Live name-search field over the available tree (case/diacritic-insensitive
  substring, filter-as-you-type; transient) (`AllocatableSelection.java:314-336`).
- `ClassificationFilter` edit button for structured attribute filtering
  (`AllocatableSelection.java:841-860`).

## 5. Cross-cutting

- **Undo/redo** (Ctrl-Z/Y) is a per-editor `CommandHistory` spanning all three panes;
  tooltips name the next undo/redo command.
- **Pluggable extension points** a new frontend can consciously drop or redesign:
  appointment status widgets below the list (counter, event-time calculator),
  `ReservationToolbarExtension` buttons, `AppointmentEditExtensionFactory` fields,
  `ConflictPeriodReservationButton` (holiday-period detection → add holiday dates as
  exceptions with one click).
- Live refresh: external data changes (`dataChanged`) rebind the picker while the
  dialog is open.

## 6. Capability checklist for a new frontend

Every line is a Swing capability; a new edit surface must cover it, replace it with
something better, or consciously drop it (decisions → PRD 091).

- [ ] Choose event type (creatable types only); type change re-maps attributes
- [ ] Edit classification fields; main vs additional attribute views; `no-view` hidden
- [ ] Edit reservation permissions (admin-only)
- [ ] Add / delete appointments (clone-as-template semantics on add)
- [ ] Edit single appointment: start/end date+time, all-day
- [ ] Begin/end interlock: start change shifts end (duration preserved);
      end-before-start auto-corrected (end +1 day, or start shifted back)
- [ ] Keyboard-first date/time entry (block navigation, arrow increments
      with rollover, digit auto-advance)
- [ ] Time picker: worktime window visualized, per-slot duration hint,
      configurable slot granularity
- [ ] Edit recurrence: type, interval, weekdays, month rules, ending mode, day-span
- [ ] Exceptions: add ranges, remove, count badge
- [ ] Convert finite series to single appointments (restrictions migrated)
- [ ] Next-free-slot search for one appointment (worktime/excluded-days aware)
- [ ] Resource picker: browse tree + live name search + classification filter
- [ ] Per-resource availability status incl. "partially free" + request-only + forbidden
- [ ] Hold-back-conflicts annotation respected in status computation
- [ ] Per-appointment restriction editing (sparse map; "every appointment" default)
- [ ] Restriction visualization (which resource on which occurrences, with conflicts)
- [ ] Request workflow (REQUESTED status for request-only resources)
- [ ] Open calendar for candidate resources from the picker
- [ ] Holiday-period conflicts → add as exceptions
- [ ] Undo/redo across the whole edit session
- [ ] Read-only mode (no modify permission), delete gating
- [ ] Template editing mode
- [ ] Multi-reservation batch edit (bold/italic partial-allocation rendering)
- [ ] Live rebind on concurrent data change

## See also

- [reservation-edit.md](reservation-edit.md) — flow, wire model, validation rules
- `docs/usecases/reservation-editing.md` — the use-case view + SPA reorganization
- PRD 091 — SPA reservation editing & availability (proposals)
- PRD 023/024 — the pure-Java edit models (`RepeatingRuleValidator`,
  `AllocationConflictModel`, `AllocatableRowStatusModel`, …) and the `/api/edit`
  services that already serve non-Swing clients
