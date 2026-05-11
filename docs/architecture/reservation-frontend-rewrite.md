# Reservation editing for a new frontend (Angular / SPA guide)

A companion to [reservation-edit.md](reservation-edit.md), which
covers the Swing edit flow. This page is for the team writing a
fresh Angular (or any modern SPA) client that has to reproduce the
behaviour of `ReservationEditImpl` and `AppointmentController` over
the existing Spring Boot REST surface.

It captures **two things the Swing-flow doc deliberately doesn't**:

1. The exact wire model — endpoints, JSON shape, ID lifecycle.
2. Every silent edge case that `AppointmentController` handles in
   ~1800 lines of Swing glue. Most are auto-corrections (swap,
   snap, push) rather than visible errors — easy to miss, painful
   when missed.

Cross-references (read these first; don't duplicate):

- [domain-model.md](domain-model.md) — entity catalog and the
  `ReferenceInfo` / `EntityResolver` pattern
- [reservation-edit.md](reservation-edit.md) — the Swing edit dance
  (clone semantics, EventCheck, CommandHistory)
- [dynamic-types.md](dynamic-types.md) — the EAV classification
  system
- [conflicts-and-events.md](conflicts-and-events.md) — how
  ConflictFinder decides what overlaps
- [permissions.md](permissions.md) — what the server enforces on
  dispatch
- [flows.md](flows.md) — store / dispatch / refresh internals

---

## 1. The wire model

All paths are relative to `server.servlet.context-path=/rapla`
(production deployments may differ; check `application.yml`).

### 1.1 Authentication

```
POST /rapla/auth/login
Body : { "username": "...", "password": "..." }
Resp : { "accessToken": "<jwt>", "refreshToken": "<jwt>" }
```

All paths below require `Authorization: Bearer <accessToken>` unless
listed in `SecurityConfig.filterChain`
(`rapla-server/src/main/java/org/rapla/server/spring/SecurityConfig.java:25`).

### 1.2 The dispatch endpoint — the only write path

```
POST /rapla/storage/dispatch
Body : UpdateEvent (single transaction; ≥1 store and/or remove)
Resp : UpdateEvent (refreshed entities + any conflict deltas)
```

There is **no** `POST /reservations` create endpoint. Every
reservation create / update / delete goes through `/storage/dispatch`
wrapped in an `UpdateEvent` (`rapla-core/src/main/java/org/rapla/storage/UpdateEvent.java`).
Controller: `RemoteStorageController.dispatch`
(`rapla-server/.../web/RemoteStorageController.java:122`).

### 1.3 ID allocation (do this *before* dispatch)

```
POST /rapla/storage/identifier
Body : { "raplaType": "reservation", "count": 1 }
Resp : ["<uuid>", ...]
```

Allocate the Reservation's id (and, ideally, each Appointment's id)
up front so the draft JSON references everything by UUID and
`restrictions` can point to appointment ids that don't yet exist
server-side.

### 1.4 Edit (existing) — `editListAsync`

For modifying an existing reservation, the Swing client fetches a
**mutable clone** rather than editing the cache directly. This is
the cycle that makes "Cancel" cheap and that enables version-conflict
detection. See [reservation-edit.md §The clone](reservation-edit.md)
for the full rationale. The corresponding REST cycle is part of
`/storage/dispatch` (the response carries refreshed copies); an
SPA can simulate it by always re-fetching before opening the editor.

### 1.5 Adjacent endpoints you'll need

| Path | Use |
|---|---|
| `GET /rapla/storage/resources` | Initial cache hydrate: DynamicTypes, Allocatables, Periods, Categories |
| `POST /rapla/storage/queryAppointments` | Range query of appointments (calendar view) |
| `GET /rapla/storage/conflicts` | Existing conflict list |
| `POST /rapla/storage/allocatable/bindings/all` | "Which allocatables are busy in this interval?" — used by the conflict overlay |
| `POST /rapla/storage/allocatable/date/next` | "Find next free slot for resource X" — feeds the *Find next free* button |
| `POST /rapla/storage/refresh` | Long-poll for changes from other clients |

### 1.6 Reservation JSON shape

Serialization is Jackson 3 with field-based introspection (`rapla-core/src/main/java/org/rapla/rest/JacksonObjectMapperFactory.java`).
What you see in the field declarations of `*Impl` classes is what
travels on the wire. **Times are timezone-naive `LocalDateTime`**
(ISO-8601 strings, server-local interpretation — see §4 for the
timezone trap).

```jsonc
{
  "id": "<uuid>",
  "classification": {
    "type": "event",           // DynamicType key — never null
    "data": {
      // Every attribute is a List<String>, even single-value ones.
      // Categories / allocatables store the referenced entity UUID
      // as a string. Render via the entity cache.
      "name": ["Team Planning"],
      "category": ["<category-uuid>"],
      "starttime": ["2026-05-11T10:00:00"]
    }
  },
  "appointments": [ /* see §1.7 */ ],
  "permissions": [ /* row-level ACL — usually inherited from the type */ ],
  "restrictions": {
    // SPARSE map: missing key = "allocatable is allocated on every
    // appointment". Only include keys where the user narrowed.
    "<allocatable-uuid>": ["<appointment-uuid>", ...]
  },
  "links": {
    // ReferenceHandler-managed outgoing refs.
    "resources": ["<allocatable-uuid>", ...],
    "owner":     ["<user-uuid>"],
    "template":  ["<reservation-uuid>"]   // optional
  },
  "annotations": { "<key>": "<string>" },
  "createDate":  "2026-05-11T09:30:00",
  "lastChanged": "2026-05-11T09:30:00"
}
```

### 1.7 Appointment + Repeating JSON

```jsonc
{
  "id": "<uuid>",
  "start": "2026-05-11T10:00:00",
  "end":   "2026-05-11T11:00:00",
  "isWholeDaysSet": false,
  "repeating": null   // OR the object below
}
```

```jsonc
{
  "repeatingType": "WEEKLY",      // DAILY | WEEKLY | MONTHLY | YEARLY
  "interval": 1,                  // every N units of the type
  "isFixedNumber": true,          // discriminator: count-based vs. end-date-based
  "number": 10,                   // used iff isFixedNumber; -1 = forever
  "end": null,                    // used iff !isFixedNumber; LocalDateTime
  "weekdays": [2, 4],             // WEEKLY only; 1=SUN .. 7=SAT (Calendar.SUNDAY..SATURDAY)
  "exceptions": ["2026-05-25T00:00:00", ...]   // dates to skip (not Appointment IDs!)
}
```

Field source: `AppointmentImpl.java:43-46`, `RepeatingImpl.java:42-48`.

### 1.8 The UpdateEvent envelope

```jsonc
{
  "userId": "<user-uuid>",
  "reservations": [ /* full reservation objects */ ],
  "resources":    [ /* changed allocatables */ ],
  "removeSet":    [ { "id": "...", "type": "..." } ],
  "preferencesPatches": [ /* per-user pref deltas */ ],
  "invalidateInterval": null,
  "lastValidated":      "<server-side cursor — echo back unchanged>",
  "timezoneOffset":     0
}
```

Source: `UpdateEvent.java:46-69`. The reactor groups entities by
type; for reservation save, only `reservations` (and optionally
`resources` if you're creating a new room inline) are populated.

---

## 2. Creating a reservation — end to end

The Swing client does this; an Angular client must do the same.
The flow assumes you've already hydrated the type / category /
period caches from `/storage/resources`.

1. **Pick a DynamicType** (event template) — usually a "New event"
   dropdown listing types where the user has `CREATE` permission.
2. **Allocate ID** via `/storage/identifier` for the Reservation
   *and* (recommended) for each Appointment.
3. **Build the draft locally**:
   - Empty `Classification` keyed by the chosen type.
   - Apply user-chosen defaults, then template values (resolved
     from `links.template` if set — see `RaplaObjectAnnotations.KEY_TEMPLATE`).
   - Copy the type's permission template into `permissions`.
   - Add **at least one** Appointment (the server rejects empty
     reservations at `ReservationImpl.checkReservation()`).
   - Default times come from the user's `CalendarOptions`
     preferences: `worktime_start`, `worktime_end` (minutes
     since midnight).
4. **Attach Allocatables** (rooms, persons): push into
   `links.resources`. By default they're allocated on every
   Appointment; narrow with `restrictions` only if the user
   chooses sparse allocation.
5. **Run pre-save checks** (analog of Swing's `EventCheck` chain):
   - Conflict pre-check via `/storage/allocatable/bindings/all`.
     The server **doesn't reject on conflict** — the check is
     advisory; you show a confirm dialog with the conflicting
     bindings and let the user override or back out.
   - Plugin-supplied checks (out of scope for v1).
6. **Submit**: `POST /storage/dispatch` with `UpdateEvent { reservations: [draft] }`.
7. **Server validation** (see [permissions.md](permissions.md) and
   [flows.md](flows.md) for detail):
   - `security.checkWritePermissions(user, reservation)` —
     `CREATE` on the type, `EDIT` on any pre-existing entities.
   - `LocalAbstractCachableOperator.preprocessEventStorage` —
     classification constraints, required attributes, type
     compatibility.
   - `checkVersions()` — optimistic lock; on a brand-new
     reservation there's no prior version so this passes
     trivially. On *update*, a stale `lastChanged` throws
     `RaplaNewVersionException`.
   - `ConflictFinder` recomputes — fresh conflicts are returned
     in the response, not rejected.
8. **Apply the response**: update local cache, re-render
   calendar, refresh the conflict list.

---

## 3. AppointmentController — every rule, every edge case

Source: `rapla-client/src/main/java/org/rapla/client/swing/internal/edit/reservation/AppointmentController.java`.

The controller is a dual-mode editor for a single Appointment. The
mode is determined by `appointment.getRepeating() != null`. Two
inner classes implement the modes: `SingleEditor` and
`RepeatingEditor`.

### 3.1 State model

- `appointment` — the mutable Appointment currently selected in
  the list.
- `repeating` — cached pointer to `appointment.getRepeating()`;
  null in single mode.
- `savedRepeatingType` — last committed `RepeatingType` so toggles
  between DAILY ↔ WEEKLY ↔ MONTHLY ↔ YEARLY know which fields to
  reset.
- `selectedEditDate` — the calendar slot the user clicked to open
  this edit session. Used to pre-fill the exception date picker.
- `lastModifiedExceptionDialogInterval` — sticky across appointment
  switches within the same session (so the user doesn't re-type the
  same exception interval when bouncing between appointments).
- `listenerEnabled` — re-entrancy guard around every UI handler.
  Programmatic updates (undo / redo, parent-driven reset) must
  suppress the change handlers. Angular equivalent: `emitEvent: false`
  on Reactive Forms, or a manual `suppressNext` flag in NgRx.

### 3.2 Single-appointment edits

| User action | Behaviour | Auto-correction / gotcha |
|---|---|---|
| Change start date | Shift end by same delta (preserve duration) | If new start > original end, end becomes start + previous duration |
| Change end date | Direct set | If end < start, end auto-advances by 1 day |
| Change start time | Same as date | If now off-midnight, `isWholeDaysSet` clears |
| Change end time | Direct set | If end < start, advance end-day by 1 |
| Toggle all-day **on** | Cut start/end to midnight | Internal end = next-day-00:00; UI shows end-date − 1 day |
| Toggle all-day **off** | Restore times from `CalendarOptions.worktime_start/end` | **Original times are lost.** The flag is destructive going off-→on, not just a presentation hint |

### 3.3 Repeating-appointment edits

| User action | Behaviour | Gotcha |
|---|---|---|
| Off → on | Default WEEKLY, interval=1, weekday = start.dayOfWeek | Via `ReservationHelper.makeRepeatingForPeriod` |
| On → off | Drop the Repeating object | **All** exceptions/weekdays/interval/end lost — no warning |
| Switch repeat type | Replace type, keep interval | Weekday / month / day-in-month reset to current start's values |
| Interval | Numeric spinner | Min 1; no client-side max — server tolerates large values |
| Weekday checkboxes (WEEKLY only) | Set of ints 1..7 | UI **allows empty set** — server rejects with no occurrences; validate client-side |
| Month chooser (YEARLY only) | int 0..11 | Independent of weekdays |
| Day-in-month (MONTHLY / YEARLY by day) | 1..31 | If the month has fewer days, server clamps to the last day |
| Weekday-in-month (MONTHLY "nth weekday") | 1..5 | "5th" is interpreted as "last" |
| Duration mode | "Same day" / "Next day" / "X days" | If "Same day" with endTime < startTime, endTime ← startTime silently |
| Ending mode | "Until <date>" / "<N> times" / "Forever" | Discriminated by `isFixedNumber`; unused field left as-is, server ignores |
| Period chooser | Snap start/end to a defined Period | Only visible if `PeriodModel` has any periods |
| **Exception add** | Open dialog → pick interval → expand to dates | Bidirectional swap if start > end. Storage is a Set of dates (single days), **not** intervals — the UI is a convenience for "add every day from X to Y" |
| Exception remove | Multi-select from list | No confirmation |
| Convert to singles ("split") | Repeating → N non-repeating Appointments | Only enabled when `repeating.getEnd() != null` (finite series) |
| Move (drag/resize on calendar) | `appointment.move(start, end)` | If moved to a different weekday, the WEEKLY weekday set is **mutated** — non-obvious, see §4 |

### 3.4 Validation philosophy

`AppointmentController` is deliberately permissive — almost every
invariant is auto-corrected, not enforced via error dialog. Why:

- The Swing controls feed events as the user types; popping a
  modal on every transient invalid value would be unusable.
- Save-time validation runs in the `EventCheck` chain and on the
  server. The controller's job is to *converge* the form into a
  shape that won't reject, not to gate input.

For an Angular implementation: prefer non-blocking corrections
(swap, snap) for temporal invariants, and reserve hard errors for
schema-level violations (missing required attribute, classification
constraint failures). Validate that there's ≥1 weekday before
allowing save in WEEKLY mode — the server will reject otherwise.

### 3.5 Undo / redo

Every mutation goes through `CommandHistory.storeAndExecute(cmd)`.
Commands record (oldState, newState) by cloning Appointments. The
controller exposes Ctrl+Z / Ctrl+Y on the parent dialog. For
Angular: undo is per-form-instance and lost on close — don't
persist it across sessions.

---

## 4. Edge cases an SPA frontend must reproduce

The list below collects the surprises — behaviours that aren't
obvious from the entity declarations or the REST schema, but that
real users depend on.

- **Restrictions are sparse.** A missing key in `restrictions`
  means "allocated on every appointment of this reservation," not
  "not allocated." Don't emit explicit empty arrays for
  unrestricted allocatables.
- **Exception dates are dates, not Appointments.** "Delete this
  one occurrence" adds the date to `repeating.exceptions`. There
  is no separate Appointment for the skipped instance; nothing to
  delete from the appointments array.
- **Repeating-forever is real.** `number == -1 && end == null`
  means infinite recurrence. The UI must page or window the
  occurrence list — never materialise the full array. The calendar
  view does this by only computing blocks for the current viewport.
- **All-day boundary.** Internally, an all-day event from 2026-05-11
  ends at `2026-05-12T00:00:00`. The UI displays end-date as
  `internalEnd − 1 day`. Both client and server expect this
  convention; serializing "end = 2026-05-11T23:59:59" will look
  almost right and behave subtly wrong.
- **Whole-day flag drift.** `isWholeDaysSet` is a hint, not a
  derived field. If a user toggles it on, then nudges start by
  +0 seconds (still at midnight), the flag *can* end up
  inconsistent. At save time, recompute from the actual times
  rather than trusting the flag verbatim. See `AppointmentImpl.move`
  for how the Swing client handles it.
- **Weekday flip on calendar move.** Dragging a weekly appointment
  from Tue to Wed mutates `repeating.weekdays`: the new weekday
  is added; the old is removed iff no other occurrence of that
  weekday remains in the series. This is in `AppointmentImpl.move`
  — reproducing it in the SPA requires the same logic, not just
  a date update.
- **Classification is EAV.** Values are `Map<String, List<String>>`
  keyed by attribute key. Multi-value lists are the norm; even
  single-value attributes serialise as length-1 lists. Don't
  collapse to a flat `Map<String, String>` when re-emitting JSON
  — round-trip will be lossy.
- **Categories as references.** A `CATEGORY` attribute stores the
  category UUID, not its localized label. Render via the category
  cache. Same for `ALLOCATABLE` attributes.
- **Stale attribute keys.** If a DynamicType drops an attribute,
  existing classifications keep the orphan key/value. Tolerate
  unknown keys when reading — don't reject the whole reservation.
- **Conflicts are non-blocking.** A save that produces conflicts
  succeeds. Conflicts come back in the response for the UI to
  surface. The user can choose to accept-and-save, accept-and-disable,
  or back out (locally only). Don't reject client-side on conflict.
- **Timestamps are timezone-naive.** All `LocalDateTime` values
  travel as ISO-8601 strings interpreted in the server's local
  timezone. Don't `new Date(...)` them in JS — that's a
  browser-local conversion. Use a library like `date-fns` with
  `parseISO` and treat the result as opaque server-time; do
  timezone conversion only at render time.
- **`UpdateEvent.lastValidated` is opaque.** Echo whatever the
  server sent back; don't compute or interpret it. It's the
  server's refresh cursor.
- **Version conflict (`RaplaNewVersionException`).** If two clients
  edit the same reservation concurrently, the second dispatch fails
  with HTTP 409 (or whatever the error mapping is — verify in
  `RaplaExceptionAdvice`). Surface "another user changed this;
  refresh and try again." Don't auto-merge.
- **Permission gradient.** The 8 `AccessLevel` values (DENIED..ADMIN)
  shape both visibility and available actions. `READ_NO_ALLOCATION`
  shows the calendar block but hides who allocated the resource.
  `ALLOCATE` lets the user *book* but not edit other people's
  reservations. Drive per-field gating from the permissions
  returned alongside the entity, not from the user's role alone.
- **Time-windowed permissions.** A user with `ALLOCATE` on a room
  may still be blocked at save if the appointment falls outside
  the permission's `start`/`end` or violates `minAdvance`/`maxAdvance`
  (relative booking window in days). See [permissions.md](permissions.md).
- **Cancellation is local.** Throw the draft away client-side;
  no server roundtrip. There is no "release lock" or "discard
  pending" endpoint — the server never knew about your edit.
- **At least one appointment.** A reservation with `appointments: []`
  is rejected at `ReservationImpl.checkReservation()`. Validate
  client-side; don't rely on the server's error.
- **Mutable clone semantics (existing reservations).** When
  editing, never bind the form directly to the cached entity.
  Either deep-clone client-side or re-fetch fresh. Mutating the
  cache directly produces ghost updates that disappear on the
  next refresh poll.
- **Min-1 appointment in repeating "X days" duration mode.** The
  Swing UI defaults `days` to 2 when "X days" is selected; the
  semantics are "appointment ends N days later at the same time."
  N=1 is functionally identical to "Same day, end-time = start-time
  next day" — keep N≥2 to match the Swing behaviour.

---

## 5. Differences from the Swing reference implementation

| Concern | Swing | Suggested Angular equivalent |
|---|---|---|
| Pre-save validation | `Set<EventCheck>` via Spring DI | Injection-token array of `(reservation) => Observable<boolean>` validators |
| Busy / spinner | RxJava3 `Subject<String>` | RxJS `BehaviorSubject` or an NgRx loading slice |
| Undo / redo | `CommandHistory` per dialog | Per-form state stack, lost on close |
| Cache hydrate | `RemoteOperator` lazy load via `ReferenceInfo` | Eager `GET /storage/resources` at app boot, then incremental from `/storage/refresh` |
| Conflict overlay | `getConflictingAppointments` + colour-coded calendar block | `/storage/allocatable/bindings/all` keyed by editing reservation's appointments |
| Optimistic vs. pessimistic | Pessimistic via `editListAsync` clone | First iteration can be last-write-wins; surface `RaplaNewVersionException` as a refresh-and-retry dialog |

---

## 6. Open questions for the rewrite

- **Editing model.** Reproduce the clone cycle (fetch fresh on
  open, dispatch back) or accept last-write-wins for v1? The Swing
  flow's clone is what gives you concurrent-edit detection; without
  it, "Save" can silently overwrite.
- **Exception UX.** Swing uses an interval picker. A more modern
  interaction is "click an occurrence, choose 'skip just this
  one'" — but the storage model is dates, not occurrences, so the
  interaction maps cleanly.
- **Per-field permission gating.** Drive from the permissions
  returned with the entity, not from a role string. The server
  already returns the access level it computed for the requesting
  user.
- **Calendar pagination for infinite recurrence.** Compute blocks
  on demand per viewport, not for the whole series. Swing does
  this via `Appointment.createBlocks(window, blocks)`.

---

## See also

- [reservation-edit.md](reservation-edit.md) — the Swing flow this
  page replaces
- [domain-model.md](domain-model.md) — the underlying entity model
- [conflicts-and-events.md](conflicts-and-events.md) — what
  ConflictFinder considers an overlap
- [permissions.md](permissions.md) — what the server enforces on
  dispatch
- [flows.md](flows.md) — store / dispatch / refresh in detail
