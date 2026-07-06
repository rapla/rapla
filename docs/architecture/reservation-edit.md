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
| Change end date | Direct set | If end < start, end auto-advances by 1 day |
| Change start time | Same as start date | If now off-midnight, `isWholeDaysSet` clears |
| Change end time | Direct set | If end < start, advance end-day by 1 |
| Toggle all-day **on** | Cut start/end to midnight | Internal end = next-day-00:00; UI displays end-date − 1 day |
| Toggle all-day **off** | Restore times from `CalendarOptions.worktime_start/end` | **Original times are lost** — the flag is destructive going off-→on, not just a presentation hint |

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
