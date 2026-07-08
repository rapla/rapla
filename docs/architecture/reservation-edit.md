# Reservation edit flow

The reservation-edit dance is the most complex client-side flow in
Rapla. It's also the part most likely to break in subtle ways
because four mechanisms intersect:

1. **Clone semantics.** The original Reservation in the cache stays
   read-only. A *mutable copy* is fetched from the server, edited,
   and dispatched back — only on save.
2. **Async-on-EDT.** Server I/O is async (`Promise<T>` over RxJava3),
   but the UI runs on the Swing EDT. Promise callbacks are scheduled
   back on the EDT.
3. **Pluggable validation.** A chain of `EventCheck` extension-point
   beans runs before save (the conflict checker is one of them).
4. **Undo / redo.** Each in-dialog mutation is recorded in a
   per-session `CommandHistory`.

This page traces one concrete scenario end to end and describes what
each layer is responsible for.

> **Where the code lives.** Reservation-edit UI:
> `rapla-client/src/main/java/org/rapla/client/swing/internal/edit/reservation/`.
> Orchestration: `rapla-client/.../client/internal/edit/`. Calendar
> view glue: `rapla-plugin/abstractcalendar/RaplaCalendarViewListener.java`.

## The cast

| Layer | Class | Role |
|---|---|---|
| Calendar view → "user wants to edit" | `RaplaCalendarViewListener` | Listens for double-click / drag, looks up permissions, hands off to `EditController`. |
| Orchestration | `EditController` | Publishes an `ApplicationEvent(EDIT_EVENTS_ID, …)` on the bus. |
| Lifecycle | `EditTaskPresenter` | Subscribes to the event, fetches mutable copy, builds the dialog, wires save / close / delete. |
| The dialog | `ReservationEditImpl` (`rapla-client/.../edit/reservation/ReservationEditImpl.java`) | Composite Swing dialog: classification fields + appointment list + allocatable selection. |
| Sub-editor: classification | `ReservationInfoEdit` | Fields driven by the DynamicType schema. |
| Sub-editor: appointments | `AppointmentListEdit` + `AppointmentController` | Per-appointment editor with repeating UI. |
| Sub-editor: allocatables | `AllocatableSelection` | Resource picker with conflict visualization. |
| Validation | `EventCheck` (extension point) | Chain of pre-save checks. The conflict checker is the canonical example. |
| Undo / redo | `CommandHistory` + `CommandUndo` | Per-session stack of reversible mutations. |
| Save | `SaveUndo<Reservation>` → `RaplaFacade.dispatch(...)` → `RemoteOperator` | The actual store. |
| Base class | `RaplaGUIComponent` | Provides `ClientFacade`, `RaplaFacade`, i18n, logger to every Swing dialog. |

## The wire model

Everything below is grounded in this contract. Jackson 3 with
field-based introspection (`rapla-core/.../rest/JacksonObjectMapperFactory.java`)
means the fields you see on `*Impl` classes are what travels on
the wire. All paths are under `server.servlet.context-path=/rapla`.

### Auth

```
POST /rapla/auth/login    { username, password }   → { accessToken, refreshToken }
```

Every path below requires `Authorization: Bearer <accessToken>`
unless listed `permitAll` in `SecurityConfig.filterChain`.

### Dispatch — the only write path

```
POST /rapla/storage/dispatch    body: UpdateEvent    → UpdateEvent (refreshed + conflicts)
```

There is **no** `POST /reservations`. Create / update / delete all
go through `/storage/dispatch` wrapped in an `UpdateEvent`
(`rapla-core/.../storage/UpdateEvent.java`).

### Adjacent endpoints

| Path | Use |
|---|---|
| `POST /rapla/storage/identifier` (body: `{ raplaType, count }`) | Allocate UUIDs **before** building the draft, so `restrictions` can reference appointment ids that don't yet exist server-side |
| `GET /rapla/storage/resources` | Hydrate the local cache: DynamicTypes, Allocatables, Periods, Categories |
| `POST /rapla/storage/queryAppointments` | Range query for the calendar viewport |
| `POST /rapla/storage/allocatable/bindings/all` | "Which allocatables are busy in this interval?" — drives the conflict overlay |
| `POST /rapla/storage/allocatable/date/next` | "Find next free slot" — backs the *Find next free* button |
| `POST /rapla/storage/refresh` | Long-poll for changes from other clients |

### Reservation JSON shape

```jsonc
{
  "id": "<uuid>",
  "classification": {
    "type": "event",          // DynamicType key — never null
    "data": {
      // Every attribute is List<String>, even single-value ones.
      // CATEGORY / ALLOCATABLE attributes hold UUID strings.
      "name":      ["Team Planning"],
      "category":  ["<category-uuid>"],
      "starttime": ["2026-05-11T10:00:00"]
    }
  },
  "appointments": [ /* see below */ ],
  "permissions":  [ /* row-level ACL; usually inherited from the type */ ],
  "restrictions": {
    // SPARSE map: a missing key means "allocated on every appointment".
    // Only emit a key when the user narrowed the allocation.
    "<allocatable-uuid>": ["<appointment-uuid>", ...]
  },
  "links": {
    "resources": ["<allocatable-uuid>", ...],
    "owner":     ["<user-uuid>"],
    "template":  ["<reservation-uuid>"]    // optional
  },
  "annotations": { "<key>": "<string>" },
  "createDate":  "2026-05-11T09:30:00",
  "lastChanged": "2026-05-11T09:30:00"
}
```

### Appointment + Repeating JSON

```jsonc
// Appointment
{ "id": "<uuid>",
  "start": "2026-05-11T10:00:00",
  "end":   "2026-05-11T11:00:00",
  "isWholeDaysSet": false,
  "repeating": null }              // or the object below

// Repeating
{ "repeatingType": "WEEKLY",       // DAILY | WEEKLY | MONTHLY | YEARLY
  "interval": 1,                   // every N units of the type
  "isFixedNumber": true,           // discriminator: count vs. end-date
  "number": 10,                    // used iff isFixedNumber; -1 = forever
  "end": null,                     // used iff !isFixedNumber
  "weekdays": [2, 4],              // WEEKLY only; 1=SUN .. 7=SAT
  "exceptions": ["2026-05-25T00:00:00", ...] }   // dates to skip — NOT appointment ids
```

Sources: `AppointmentImpl.java:43-46`, `RepeatingImpl.java:42-48`.

### UpdateEvent envelope

```jsonc
{
  "userId":          "<user-uuid>",
  "reservations":    [ /* full Reservation objects */ ],
  "resources":       [ /* changed Allocatables, if any */ ],
  "removeSet":       [ { "id": "...", "type": "..." } ],
  "preferencesPatches": [ /* per-user pref deltas */ ],
  "lastValidated":   "<opaque server cursor — echo back unchanged>",
  "timezoneOffset":  0
}
```

Source: `UpdateEvent.java:46-69`.

**Timestamps are timezone-naive `LocalDateTime`** — ISO-8601
strings interpreted in the server's local zone. Don't `new Date(...)`
them in JS; that's a browser-local conversion. Treat as opaque
server-time and convert to user-local only at render.

## The clone: `editListAsync`

The single most important call in this flow:

```java
raplaFacade.editListAsync(toEdit) : Promise<Map<T, T>>
```

Given a list of read-only entities from the cache, the server
returns a `Map<original, mutableClone>`. The client edits the clone;
the original stays untouched.

What this buys you:

- **Cancel is free.** Throw the clone away, the original is intact.
- **Concurrent edit detection.** When the clone is dispatched back,
  the server compares versions; if another client modified the
  original in the meantime, the dispatch fails with
  `RaplaNewVersionException` and the user sees a conflict-resolution
  dialog.
- **Permissions.** The server enforces edit permissions at clone
  time, not just save time, so the dialog opens "read-only" if the
  user isn't allowed to write.

The pair `(original, clone)` lives in the dialog's `editMap` for the
duration of the session and is what `SaveUndo` operates on.

## Stage 1 — open the dialog

```
[user double-clicks an AppointmentBlock in a calendar view]

SwingWeekView (or SwingMonthView)
  └── AbstractDaySlot.BlockListener.mouseClicked(MouseEvent)
        └── DraggingHandler.blockEdit(SwingBlock, Point)
              └── AbstractSwingCalendar.fireBlockEdit(...)
                    └── ViewListener.blockEdit(...)         // RaplaCalendarViewListener
                          ├── permissionController.canModify(reservation, user)?  → if no: bail
                          └── EditController.edit(appointmentBlock, popupContext)
                                └── eventBus.publish(ApplicationEvent(EDIT_EVENTS_ID, ...))

[event bus dispatches]

EditTaskPresenter.startActivity(event)
  ├── extract entities from event context
  ├── raplaFacade.editListAsync([originalReservation])  // server roundtrip → clone
  └── thenCompose(editMap → getEditWidget(...))
        ├── new ReservationEditImpl                    // Spring prototype bean
        └── editTaskView.editReservation(mutable, original, appointmentBlock)
              ├── reservationInfo.setReservation(mutable)
              ├── appointmentEdit.setReservation(mutable, currentlyEditedAppt)
              └── allocatableEdit.setReservation({mutable}, {original})

[dialog renders on EDT]
```

`EDIT_EVENTS_ID` is one application-event topic among several — you
also see `EDIT_RESOURCES_ID`, `EDIT_USERS_ID`, etc. The presenter
dispatches by the `id` field on the event.

## Stage 2 — mutate

The dialog is composite; each sub-editor reports state changes back
to `ReservationEditImpl`, which:

- Sets `hasChanged=true` (which enables the Save button).
- Records a `CommandUndo` in `CommandHistory` (Ctrl+Z / Ctrl+Y).
- Fires `ChangeEvent` to its listeners so other panes (e.g. the
  allocatable selection) can refresh.

### Adding an Appointment

```
RaplaListEdit.Listener.addButtonClicked()
  └── AppointmentController.newAppointment()
        └── ReservationEditImpl.addAppointment(start, end)
              └── facade.newAppointmentAsync(TimeInterval)   // server roundtrip
                    └── Promise<Appointment>
                          └── appointmentEdit.addAppointment(appointment)
                                ├── model.addElement(appointment)   // SortedListModel
                                └── fireAppointmentAdded(appointment)
                                      └── ReservationEditImpl.Listener.appointmentAdded(...)
                                            └── fireReservationChanged() → setHasChanged(true)
```

### Changing the appointment time / repeat

```
[user edits date/time/repeat in AppointmentController]
  ChangeEvent fires
    ├── commandHistory.record(CommandUndo)                  // for Ctrl+Z
    └── Listener.stateChanged()
          └── fireReservationChanged() → setHasChanged(true)
```

### Changing resource allocation

```
[user toggles a checkbox in AllocatableSelection]
  ChangeEvent fires
    ├── mutableReservation.setRestriction(allocatable, ...)
    ├── commandHistory.record(SetRestrictionCommand)
    └── allocatableEdit refreshes the conflict-period overlay
          └── (queries getConflictingAppointments(...) for the picked allocatable)
```

### Drag / resize on the calendar

The same flow but skipping the dialog entirely. Drag and resize go
through `RaplaCalendarViewListener.moved(...)` /
`resized(...)`, which call
`reservationController.moveAppointment(...)` /
`reservationController.resizeAppointment(...)`. Each constructs a
`MoveAppointmentCommand` (a `CommandUndo`) and dispatches optimistically.
If the server rejects the move (version conflict, permission), the
user sees a dialog and the optimistic UI is rolled back.

### Delete — the scope dialog and its cascades

Deleting a block from the calendar view goes through
`ReservationControllerImpl.deleteAppointment()` (`:412`) /
`deleteBlocks()` (`:139`), with the scope chooser built in
`showDialog()` (`:513-561`). The dialog title is `delete`, the content
`delete_appointment.format` ("Was wollen Sie löschen?"), and the option
list is assembled from these exact predicates:

| Option (i18n key) | Shown when | Effect on the data model |
|---|---|---|
| `reservation` (whole event) | `reservation.getAppointments().length <= 1 \|\| includeEvent` | reservation added to the remove set → `facade.dispatch(update, remove)` deletes it entirely |
| `serie: <summary>` | `appointment.getRepeating() != null && appointments.length > 1` | the `Appointment` object is removed from the reservation; its restriction links are captured for undo and discarded |
| `single_appointment.format` ("Termin am {0}") | `(repeating != null && isNotEmptyWithExceptions(appointment, [date])) \|\| appointments.length > 1` | repeating: exception date added (see below); non-repeating in a multi-appointment event: the appointment is removed |

**The dialog is skipped when only one option applies**
(`optionList.size() <= 1`, `:540`) — a single non-repeating appointment
in a one-appointment event goes straight to a plain confirm
(`deleteDialog.showDeleteDialog`, `:548`).

Rules that fire regardless of which option was chosen:

- **Exception dates are day-truncated.** The SINGLE case adds
  `DateTools.cutDate(blockStart)` (midnight, `:428`) to
  `repeating.exceptions` — not the block's start time.
- **Empty-series cascade.** Before adding the exception, Swing checks
  `isNotEmptyWithExceptions(appointment, exceptions)` (`:429`): if the
  exception would leave the series with zero occurrences, the whole
  appointment is removed instead — an all-excepted appointment is never
  stored.
- **Last-appointment cascade.** If a delete removes the last remaining
  appointment(s) of a reservation, the whole reservation is deleted
  instead (`deleteBlocks` `:182-191`) — `checkReservation()` rejects an
  empty `appointments` array, so a zero-appointment event can never
  reach the store.
- **Exception blocks can't be deleted.** The block context menu omits
  the delete action when `block.isException()`
  (`MenuFactoryImpl:211-213`) — the occurrence is already skipped.
- **The table view bypasses all of this.** Delete from the table/tree
  goes through `RaplaObjectActions.delete` → `DeleteUndo` — plain
  confirm, always the whole reservation, no scope options and no
  exception writing.

`DeleteBlocksCommand` (`ReservationControllerImpl.java:268-410`)
implements the calendar-side delete as one command over a multi-block
selection: it partitions the blocks into reservations-to-remove /
appointments-to-remove / exceptions-to-add (applying both cascades
above), captures the restriction arrays of removed appointments, and
dispatches once. `undo()` restores in reverse: re-store deleted
reservations (via the parent `DeleteUndo`), re-add appointments with
`setRestrictionForAppointment(...)`, remove the added exceptions.

## Stage 3 — save with validation

User clicks **Save**:

```
ReservationEditImpl.saveButton.action
  └── saveCmd.accept(Collections.singleton(mutableReservation))     // closure from Stage 1

   (closure body:)
        busyIdleObservable.onNext("save")                           // UI shows spinner
        ↓
        reservationController.saveReservations(editMap, popupContext)
          ↓
          checkEvents(reservations, popupContext)                    // ★ validation chain
            for each EventCheck in eventCheckers (Set<EventCheck>):
              promise = promise.thenCompose(prevOk ->
                prevOk ? eventCheck.check(reservations, ctx) : ResolvedPromise(false)
              )
            // ConflictCheckImpl is one of these — it queries
            // getConflictingAppointments for each appointment
            // and shows a confirm-or-cancel dialog if any are found
          ↓
          if (allChecksPassed) {
            commandHistory.storeAndExecute(SaveUndo<Reservation>(facade, editMap))
              └── SaveUndo.execute()
                    └── facade.dispatch(modified, [])               // ★ the actual server write
                          └── RemoteOperator.dispatch(UpdateEvent)
                                └── HTTP POST → server processes →
                                    server fires ModificationEvent →
                                    server returns OK → Promise<Void>
          } else {
            // user cancelled in the conflict dialog or a check failed
            return CommandAbortedException
          }

   (after the closure resolves:)
        if (successful) {
          eventBus.publish(stopEvent)         // closes the dialog
          raplaFacade.refreshAsync()          // pulls the post-save state
        }
        busyIdleObservable.onNext("")         // hides spinner
```

### `EventCheck` extension point

`rapla-client/src/main/java/org/rapla/client/extensionpoints/EventCheck.java`

```java
public interface EventCheck
{
    String ID = "eventcheck";
    Promise<Boolean> check(Collection<Reservation> reservations, PopupContext source);
}
```

Spring DI registers every `@Service`-annotated `EventCheck`, the
controller's `Set<EventCheck> eventCheckers` is auto-injected, and
each check runs in turn. A check returns:

- `true` — proceed to the next check (or to dispatch).
- `false` — abort the save.

Checks may show their own UI (e.g. the conflict checker shows a
confirm dialog with the list of conflicts; the user can override
or back out). They run **after** the user clicked Save and **before**
dispatch.

Custom plugins add a check by writing a class that implements
`EventCheck`, marking it `@Service`, and dropping it under
`org.rapla.client.*` or `org.rapla.plugin.*`. See
[extension-points.md](extension-points.md).

## Stage 4 — cancel / discard

```
ReservationEditImpl.closeButton.action
  └── closeCmd.run()

   (closure body:)
        EditTaskPresenter.processStop(event, view)
          ├── if (!view.hasChanged()) → close immediately
          └── else
                ├── show "Discard changes?" dialog
                └── if user confirms:
                      • mutable Reservation goes out of scope (GC)
                      • CommandHistory goes out of scope (GC)
                      • original in cache is untouched
                      • event.setStop(true) → eventBus closes the dialog
```

There is no rollback mechanism on the server because the clone never
reached the server. A discarded edit is a local-only operation.

## AppointmentController in detail

`rapla-client/.../edit/reservation/AppointmentController.java` is
the largest single piece of the dialog (~1800 lines). Stage 2 above
introduces it; this section enumerates every rule it enforces.
**Most rules are silent auto-corrections, not visible errors** —
the controller's job is to *converge* the form into a save-safe
shape, not to gate input.

### State model

It's a dual-mode editor for one Appointment at a time. The mode is
determined by `appointment.getRepeating() != null`. Inner classes
`SingleEditor` and `RepeatingEditor` implement the two modes.

| Field | Role |
|---|---|
| `appointment` | The currently-selected mutable Appointment |
| `repeating` | Cached pointer to `appointment.getRepeating()`; null in single mode |
| `savedRepeatingType` | Last committed `RepeatingType` so toggles between DAILY ↔ WEEKLY ↔ MONTHLY ↔ YEARLY know which fields to reset |
| `selectedEditDate` | The slot the user clicked; pre-fills the exception date picker |
| `lastModifiedExceptionDialogInterval` | Sticky across appointment switches in the same session |
| `listenerEnabled` | Re-entrancy guard around every UI handler — programmatic updates (undo/redo, parent reset) must suppress the change handlers |

Every mutation runs through `CommandHistory.storeAndExecute(cmd)`,
giving Ctrl+Z / Ctrl+Y.

### Single-appointment edits

| User action | Behaviour | Auto-correction / gotcha |
|---|---|---|
| Change start date | Shift end by same delta (preserve duration) | If new start > original end, end becomes start + previous duration |
| Change end date | Direct set | If end lands at/before start, the **start shifts backward** to preserve duration (see interlink mechanics below) |
| Change start time | Same as start date | If now off-midnight, `isWholeDaysSet` clears |
| Change end time | Direct set | If end < start, advance end-day by 1 |
| Toggle all-day **on** | Cut start/end to midnight | Internal end = next-day-00:00; UI displays end-date − 1 day |
| Toggle all-day **off** | Restore times from `CalendarOptions.worktime_start/end` | **Original times are not remembered** — toggling on discards them; toggling back off yields worktimes, not the previous times |

### The four-widget interlink — exact mechanics

`SingleEditor.processChange(source)` (`AppointmentController.java:468-539`)
is the single handler behind all four date/time widgets plus the all-day
checkbox. It runs under the `listenerEnabled` re-entrancy guard
(programmatic widget updates from undo/redo must not re-fire it), computes
the change relative to the **model** state (`appointment.getStart()/getEnd()`,
`duration = Duration.between(start, end)`), wraps it in an
`UndoSingleEditorChange` and executes it through the dialog
`CommandHistory`:

- **`startDate` or `startTime` changed** →
  `newStart = toDate(startDate, startTime)`; `newEnd = newStart.plus(duration)`.
  Begin drags end along; duration is invariant. No explicit midnight
  handling needed — `plus()` rolls dates.
- **`endTime` changed** → `newEnd = toDate(endDate, endTime)`; if
  `appStart.isAfter(newEnd)`, `newEnd = addDay(newEnd)` — an end time
  earlier than the start time means "into the next day", not an error.
  Start untouched (`newStart = null` in the command).
- **`endDate` changed** →
  `newEnd = toDate(endDate + (allDay ? 1 : 0), endTime)`. If the new end
  lands at/before the start, the **start shifts backward**: the sub-day
  remainder of the old duration is preserved
  (`newStart = newEnd − (duration mod 24h)`), or for exact multi-day
  durations `newStart = newEnd − 1 day`.
- **All-day checkbox toggled** → the command records the pre-toggle widget
  state as "old" and the model state as "new"; the transformation itself is
  `setToWholeDays()` (`AppointmentController.java:375-393`): toggling
  **off** re-derives times from
  `CalendarOptions.getWorktimeStartMinutes()/getWorktimeEndMinutes()`
  (start = day + worktimeStart, end = (endDay − 1 day) + worktimeEnd,
  +1 day if that inverts the pair).

The command's `execute()`/`undo()` (`AppointmentController.java:593-681`)
update the widgets first (listeners suppressed), then `mapToAppointment()`
writes the widgets back to the model via `appointment.move(start, end)` —
with the all-day end mapped to next-day-00:00 — and
`fireAppointmentChanged()` notifies the list panel, which refreshes the
affected row and re-sorts (`AppointmentListEdit.Listener.stateChanged`,
`AppointmentListEdit.java:296-308`).

The widgets are intertwined, but the **model is the arbiter**: every
handler reads the current model start/end/duration, never the previous
widget values, so a sequence of edits cannot drift the four widgets out
of agreement.

### Repeating-appointment edits

| User action | Behaviour | Gotcha |
|---|---|---|
| Off → on | Default WEEKLY, interval=1, weekday = start.dayOfWeek | Via `ReservationHelper.makeRepeatingForPeriod` |
| On → off | Drop the Repeating object | **All** exceptions / weekdays / interval / end lost — no warning |
| Switch repeat type | Replace type, keep interval | Weekday / month / day-in-month reset to current start's values |
| Interval | Numeric spinner | Min 1; no client-side max |
| Weekday checkboxes (WEEKLY) | Set of ints 1..7 | UI **allows empty set** — server then yields no occurrences; validate client-side |
| Month chooser (YEARLY) | int 0..11 | Independent of weekdays |
| Day-in-month (MONTHLY/YEARLY by day) | 1..31 | If the month is shorter, server clamps to last day |
| Weekday-in-month (MONTHLY "nth weekday") | 1..5 | "5th" → "last" |
| Duration mode | "Same day" / "Next day" / "X days" | "Same day" with endTime < startTime silently sets endTime ← startTime |
| Ending mode | "Until <date>" / "<N> times" / "Forever" | Discriminator: `isFixedNumber`; the unused field is left in JSON, server ignores |
| Period chooser | Snap start/end to a defined Period | Only visible if `PeriodModel` has any periods |
| Exception **add** | Open dialog → pick interval → expand to dates | Bidirectional swap if start > end. Storage is a Set of *single days* — the interval picker is a "add every day from X to Y" convenience |
| Exception remove | Multi-select from list | No confirmation |
| Convert to singles ("split") | Repeating → N non-repeating Appointments | Only enabled when `repeating.getEnd() != null` (finite series) |
| Move (drag/resize on calendar) | `appointment.move(start, end)` | If moved to a different weekday, WEEKLY weekdays are **mutated** (new added, old removed iff no other occurrence remains) |

In `RepeatingEditor` the same start/end-time link applies
(`dateChanged()`, `AppointmentController.java:1124-1162`): a `startTime`
change shifts `endTime` by the model duration; an `endTime` earlier than
start is interpreted as next-day (`addDay`). The end *date* of one
occurrence is not a widget — it's derived: `getEnd()` = start date +
end time, shifted by the day-span chooser ("same day" / "next day" /
"X days", `AppointmentController.java:961-969`). Selecting "same day"
while end < start snaps end time up to start time.

### Validation philosophy

The controller is deliberately permissive. Almost every temporal
invariant is auto-corrected, not enforced via dialog. Why:

- Swing controls fire events as the user types; popping a modal on
  every transient invalid value is unusable.
- Save-time validation runs in the `EventCheck` chain and on the
  server. The controller converges the form; the chain decides
  whether to commit.

For a new frontend: prefer non-blocking corrections (swap, snap)
for temporal invariants, and reserve hard errors for schema-level
violations (missing required attribute, classification constraint).
Validate ≥1 weekday before allowing save in WEEKLY mode — the
server will reject otherwise.

## Inside the date/time widgets (`org.rapla.components.calendar`)

The four pickers are two widget types from
`rapla-client/src/main/java/org/rapla/components/calendar/`, both
extending `RaplaComboBox` (text editor + arrow button + lazily-created
popup):

| Widget | Editor | Popup |
|---|---|---|
| `RaplaCalendar` | `DateField` — block-structured text field (day/month/year) with a gray weekday label ("Mi") right-aligned inside the field (`DateField.java:333-363`) | `CalendarMenu` — month grid (`DaySelection`) with month/year nav buttons |
| `RaplaTime` | `TimeField` — block-structured `HH:mm` (plus am/pm block in 12-hour locales) | `TimeList` — scrollable list of time slots |

**Time-slot granularity.** `TimeList` renders `24 × rowsPerHour` entries;
`rowsPerHour` defaults to 4 (15-minute slots) and is set from
`CalendarOptions` when the widget is built via
`RaplaGUIComponent.createRaplaTime` (`RaplaTime.java:72,152-172`).
Selecting a slot for an off-grid time truncates (floor), not rounds.

**Worktime highlighting.** `RaplaGUIComponent.getTimeRenderer()`
(`RaplaGUIComponent.java:157-233`) closes over
`CalendarOptions.getWorktimeStartMinutes()/EndMinutes()` and paints slots
outside the worktime window with `NON_WORKTIME` gray (`0xcccccc`);
an overnight worktime window (start ≥ end) inverts the test. That's the
highlighted band visible in the time dropdown.

**Duration hints.** When begin and end are on the same day, the end-time
widget gets `setDurationStart(start)`; the dropdown then appends a
duration to each slot — "(30 min)", "(2 h)", "(1½ h)" — computed per slot
in `TimeList.setModel` (`RaplaTime.java:413-445`) and formatted by
`getDurationString` (only round half-hours render text; odd durations
show nothing).

**Keyboard model** — why the Swing fields are fast to operate:

| Key (in the text field) | Effect (`AbstractBlockField.java:134-211`) |
|---|---|
| ← / → | previous / next block (day → month → year; hour → minute → am/pm) |
| Home / End | first / last block |
| ↑ / ↓ | increment / decrement the selected block by 1 |
| PageUp / PageDown | big step: ±10 years, ±3 months, ±7 days; ±12 hours, ±10 minutes |
| digits | overwrite the block; auto-advance when the block is full or a separator is typed |

Increments delegate to `LocalDate.plusDays/plusMonths/plusYears` and
`LocalTime.plusMinutes/plusHours` (`DateField.java:258-278`,
`TimeField.java:265-284`), so everything rolls over correctly (minute
59 ↑ rolls the hour, month 12 rolls the year).

In the popups: `CalendarMenu` arrows move by day/week, PageUp/Down by
month, Enter/Space confirms, Esc closes (`CalendarMenu.java:310-341`);
`TimeList` arrows move a slot, Enter/Space confirms, Esc closes
(`RaplaTime.java:524-551`).

**Events.** Both widgets fire `DateChangeEvent` to registered
`DateChangeListener`s only when the value actually differs from the last
fired value — this, plus the controller's `listenerEnabled` guard, is
what keeps the four-widget feedback loop from oscillating.

## Command / undo catalog

The contract
(`rapla-core/src/main/java/org/rapla/components/util/undo/CommandUndo.java`):

```java
public interface CommandUndo<T extends Exception> {
    Promise<Void> execute();
    Promise<Void> undo();
    String getCommandoName();   // shown in undo/redo tooltips and menu labels
}
```

`CommandHistory` (same package) keeps a single list plus a `current`
pointer (max 100 entries, FIFO-trimmed). `storeAndExecute` executes,
then truncates the redo tail and appends; `undo()`/`redo()` move the
pointer; `CommandHistoryChangedListener.historyChanged()` fires after
every operation and drives the arrow buttons' enabled state and
tooltips (`getUndoText()`/`getRedoText()`).

**Two histories, not one:**

- the **global** history (`ClientFacadeImpl.getCommandHistory()`), bound
  to the main-window menu and Ctrl-Z (`RaplaMenuBar.java:206-221`) —
  holds store-level commands (save, delete, calendar drag/paste);
- a **per-dialog** history (`ReservationEditImpl.commandHistory`,
  cleared on every `setReservation`), bound to the dialog's own
  undo/redo arrows — holds every in-dialog mutation and dies with the
  dialog. Only on Save does a single `SaveUndo` land in the global
  history.

### Global-history commands

| Command | Defined in | Trigger | execute() | undo() |
|---|---|---|---|---|
| `SaveUndo<T>` | `client/internal/SaveUndo.java` | Save in any edit dialog; copy-paste; status menus | `facade.dispatch(new)` — redo refetches mutable copies via `editListAsync` | restore old versions via `editListAsyncForUndo`; remove entities that were created |
| `DeleteUndo<T>` | `client/internal/DeleteUndo.java` | delete menu action (`RaplaObjectActions.java:416`) | `facade.dispatchRemove` (child categories included) | re-store cloned entities, re-owned to the current user |
| `AppointmentResize` | `ReservationControllerImpl.java:1108-1268` | drag-move/resize on the calendar, after the "only this date / series / whole event" scope dialog | SINGLE: clone as single appointment + add exception to the series; SERIE/EVENT: move the appointments | reverse the move / remove clone + exception |
| `DeleteBlocksCommand` | `ReservationControllerImpl.java:268-410` | delete from the calendar view (single block or multi-block selection), after the delete scope dialog (see "Delete — the scope dialog") | partition into remove-reservation / remove-appointment / add-exception with empty-series + last-appointment cascades; one dispatch | re-store reservations, re-add appointments + restrictions, remove added exceptions |
| `AllocatableExchangeCommand` | `ReservationControllerImpl.java:946-1086` | drag between resource rows in the calendar | exchange allocatable, adjust restrictions/exceptions per scope | restore old allocations and restrictions |
| `AppointmentPaste` | `ReservationControllerImpl.java:1318-1413` | paste a copied appointment | add appointment to a reservation OR clone the whole reservation (`asNewReservation`) | remove it / delete the clone |
| `ReservationPaste` | `ReservationControllerImpl.java:1469-1512` | paste copied reservations | `facade.copyReservations` + dispatch clones with time offset | dispatch-remove the clones |
| `ConflictEnable` | `ConflictSelectionPresenter.java:160-202` | enable/disable-conflict context menu | toggle the conflict's enabled flag | toggle back |

### Dialog-history commands

| Command | Defined in | Trigger | execute() / undo() |
|---|---|---|---|
| `UndoReservationTypeChange` | `ReservationInfoEdit.java:641` | event-type dropdown | swap classification + DynamicType, re-render the form / swap back |
| `UndoClassificationChange` | `ReservationInfoEdit.java:552` | attribute field edit | set new attribute value / set old value |
| `UndoPermissionChange` | `ReservationInfoEdit.java` (after 700) | permission list edit | replace the permission set / restore the old set |
| `UndoSingleEditorChange` | `AppointmentController.java:593-681` | any of the 4 date/time widgets or the all-day box (single mode) | set widgets + `appointment.move` / restore old start/end/flag |
| `UndoDataChange` | `AppointmentController.java:1841-1887` | repeating-editor field changes; "free appointment" move | copy new appointment state (`AppointmentImpl.copy`) / copy old state back |
| `UndoRepeatingTypeChange` | `AppointmentController.java:1758-1807` | repeating-type radio buttons | switch repeating type + swap card panel / switch back |
| `UndoExceptionChange` | `AppointmentController.java:1632-1686` | exception dialog add/remove | apply exception additions/removals / revert them |
| `NewAppointment` | `AppointmentListEdit.java:425-449` | "New" button | add appointment to reservation + list / remove it |
| `RemoveAppointments` | `AppointmentListEdit.java:335-384` | "Delete" button | remove selected appointments (restrictions captured first) / re-add + restore restrictions |
| `AppointmentSplit` | `AppointmentListEdit.java:462-545` | "convert to single events" | expand finite series into N single appointments, migrate restrictions / remove them, restore the series |
| `AppointmentSelectionChange` | `AppointmentListEdit.java:557-584` | selecting a list row | select target / previous appointment — selection is **on the undo stack**, so undoing an edit also returns to the row it happened on |
| `RestrictionChange` | `AllocatableSelection.java:2314-2346` | "Selected on" restriction popup | set new restriction array on the allocatable / restore the old array |
| `AllocatableChange` | `AllocatableSelection.java:2250-2302` | add/remove resource buttons, double-click | add or remove allocatables on all edited reservations / inverse |
| `UndoNoteChange` | plugin `appointmentnote/.../AppointmentNoteEditFactory.java:159-205` | appointment-note text field | set the note annotation / restore the old note |

### Design notes

- Commands capture old/new state **at construction**; execute()/undo()
  never re-read the live UI.
- Several global commands carry a `firstTimeCall` flag: the first
  execution differs from redo (`SaveUndo` dispatches the already-edited
  clone on first call, but a redo must refetch fresh mutable copies).
- `execute()`/`undo()` return `Promise<Void>`, so server-backed commands
  (save, paste) chain async; `CommandHistory` only appends after the
  promise resolves — a failed execute never lands on the stack.

## Async on the EDT — the Promise contract

`rapla-core/src/main/java/org/rapla/scheduler/Promise.java` plus
`CommandScheduler` give the client an EDT-safe Promise model:

- `thenAccept`, `thenApply`, `thenCompose` callbacks fire on the
  scheduler's executor.
- The Swing client's executor is the EDT, so callbacks always run on
  the right thread.
- Server I/O happens off-EDT inside `RemoteOperator` (HTTP via
  Apache HttpClient).

The pattern you see throughout the edit code:

```java
busyIdleObservable.onNext("doing thing");
return server.thingAsync(args)               // off-EDT
    .thenApply(result -> processOnEdt(result))   // back on EDT
    .whenComplete((r, e) -> busyIdleObservable.onNext(""));
```

`busyIdleObservable` is an RxJava3 `Subject<String>` that the title
bar / status panel subscribes to for the spinner.

`handleException(promise, popupContext)` is the wrapper that takes
any unhandled exception in the Promise chain and shows it as an
error dialog via `DialogUiFactoryInterface.showException(...)`.

## Worked example: add an appointment, change a room, save

**Setup.** A user is editing the existing reservation
*"Team Planning, Mon 10:00–11:00 in Room A"*. The original is in
cache, read-only.

```
1. Double-click the block.
   RaplaCalendarViewListener.blockEdit fires.
   permissionController.canModify(reservation, user) returns true.
   EditController.edit publishes ApplicationEvent.

2. EditTaskPresenter calls editListAsync([R789]).
   Server returns {R789 → R789-clone}.

3. ReservationEditImpl is constructed (Spring prototype).
   editReservation(R789-clone, R789, block).
   Dialog renders with Mon 10:00–11:00 + Room A.

4. User clicks "+Add". A new Appointment for Tue 10:00–11:00 is
   fetched via newAppointmentAsync, added to AppointmentListEdit.
   appointmentAdded → fireReservationChanged → hasChanged=true.

5. User selects the new appointment, ticks Room B in
   AllocatableSelection.
   R789-clone.setRestriction(RoomB, [appt2]) is recorded.
   Conflict overlay reloads — "no conflicts on Room B Tue 10–11".

6. User clicks Save.
   saveCmd.accept({R789-clone})
     → checkEvents({R789-clone}, ctx)
         conflictCheck.check(...)  → no conflicts → true
     → commandHistory.storeAndExecute(SaveUndo)
         → facade.dispatch([R789-clone], [])
             → RemoteOperator HTTP POST
                 server validates permissions, version, conflicts
                 server commits to DB
                 server fires ModificationEvent to all clients
             → Promise<Void> resolves OK
     → eventBus.publish(stopEvent)   // closes dialog
     → facade.refreshAsync()         // re-syncs cache
   Calendar repaints with two blocks: Mon (Room A) and Tue (Room B).

7. Side effects on other connected clients:
   Within ~30 s (poll interval), each client's RemoteOperator gets
   an UpdateEvent containing the modified R789. Their LocalCache
   updates, ModificationEvent fires, calendar repaints.
```

## Common pitfalls

### Working in this codebase

- **Don't store the mutable copy outside the dialog scope.** It's
  a clone; mutations on it after the dialog closes are lost on the
  next refresh.
- **Don't bypass `editListAsync` to "save a roundtrip."** Skipping
  it means you lose concurrent-edit detection and may corrupt the
  cache when dispatch returns updated entities.
- **Don't add a check by subclassing the conflict checker.** Add an
  `EventCheck` and let Spring DI wire it in. The checker chain is
  designed to be extended.
- **Don't suppress exceptions from the Promise chain.** Wrap with
  `handleException(...)` so the user sees the dialog instead of a
  silent log line.

### Edge cases of the domain (any client must reproduce)

This list collects the non-obvious behaviours — things not visible
from the entity declarations or the REST schema but that real users
depend on. Relevant when porting the client, building a new
frontend, or writing integration tests.

- **Restrictions are sparse.** A missing key in `restrictions`
  means "allocated on every appointment of this reservation," not
  "not allocated." Don't emit explicit empty arrays for
  unrestricted allocatables.
- **Exception dates are dates, not Appointments.** "Delete this
  one occurrence" adds the date to `repeating.exceptions`. There
  is no separate Appointment for the skipped instance; nothing to
  delete from `appointments`.
- **Repeating-forever is real.** `number == -1 && end == null`
  is infinite recurrence. Never materialise the full occurrence
  array — page or window. The calendar view computes blocks per
  viewport via `Appointment.createBlocks(window, ...)`.
- **All-day boundary.** Internally, an all-day event from
  2026-05-11 ends at `2026-05-12T00:00:00`. The UI displays
  end-date as `internalEnd − 1 day`. Serializing
  `end = 2026-05-11T23:59:59` looks almost right and behaves
  subtly wrong.
- **Whole-day flag drift.** `isWholeDaysSet` is a hint, not a
  derived field. After toggles it can desync from the actual
  times. At save time, recompute from the times rather than
  trusting the flag.
- **Weekday flip on calendar move.** Dragging a weekly appointment
  from Tue to Wed mutates `repeating.weekdays`: the new weekday is
  added; the old is removed iff no other occurrence of that
  weekday remains. In `AppointmentImpl.move` — reproducing
  requires the same logic, not just a date update.
- **Classification is EAV.** Values are `Map<String, List<String>>`.
  Multi-value lists are the norm; even single-value attributes
  serialise as length-1 lists. Don't collapse to a flat
  `Map<String, String>` — round-trip will be lossy.
- **Categories as references.** `CATEGORY` and `ALLOCATABLE`
  attributes hold the referenced entity's UUID, not its localized
  label. Render via the entity cache.
- **Stale attribute keys.** If a DynamicType drops an attribute,
  existing classifications keep the orphan key/value. Tolerate
  unknown keys when reading.
- **Conflicts are non-blocking.** A save with conflicts succeeds;
  conflicts come back in the response for the UI to surface. The
  user can accept-and-save, accept-and-disable, or back out
  locally. Don't reject client-side on conflict.
- **`UpdateEvent.lastValidated` is opaque.** Echo whatever the
  server sent; don't compute or interpret it.
- **Version conflict (`RaplaNewVersionException`).** Concurrent
  edit on the same reservation fails the second dispatch. Surface
  "another user changed this; refresh and try again" — don't
  auto-merge.
- **Permission gradient drives visibility.** `READ_NO_ALLOCATION`
  shows the calendar block but hides allocator detail; `ALLOCATE`
  lets the user book but not edit others' reservations. Drive
  per-field gating from the permissions returned alongside the
  entity, not from a role string. See [permissions.md](permissions.md).
- **Time-windowed permissions.** A user with `ALLOCATE` may still
  be blocked at save if the appointment falls outside the
  permission's start/end or violates minAdvance/maxAdvance
  (relative booking window in days).
- **Cancellation is local.** Throw the draft away client-side; no
  "release lock" or "discard pending" endpoint exists. The server
  never knew about your edit.
- **At least one appointment.** `ReservationImpl.checkReservation()`
  rejects an empty `appointments` array at save time. Validate
  client-side too.
- **"X days" duration N ≥ 2.** The Swing UI defaults `days` to 2
  when "X days" is selected. N=1 is functionally identical to
  "Same day, end-time = start-time next day" — keep N≥2 to match
  Swing behaviour.

## See also

- [reservation-edit-ui-inventory.md](reservation-edit-ui-inventory.md) —
  functional inventory of the Swing dialog (every user-facing
  capability; the checklist for a new frontend)
- [domain-model.md](domain-model.md) — Reservation / Appointment /
  Repeating
- [conflicts-and-events.md](conflicts-and-events.md) — what the
  conflict checker queries
- [permissions.md](permissions.md) — what the server enforces on
  dispatch
- [extension-points.md](extension-points.md) — adding an
  `EventCheck`
- [flows.md](flows.md) — store / dispatch / refresh internals
