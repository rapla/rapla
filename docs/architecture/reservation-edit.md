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

## See also

- [domain-model.md](domain-model.md) — Reservation / Appointment /
  Repeating
- [conflicts-and-events.md](conflicts-and-events.md) — what the
  conflict checker queries
- [permissions.md](permissions.md) — what the server enforces on
  dispatch
- [extension-points.md](extension-points.md) — adding an
  `EventCheck`
- [flows.md](flows.md) — store / dispatch / refresh internals
