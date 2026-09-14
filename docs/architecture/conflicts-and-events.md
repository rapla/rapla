# Reservation as event container, conflicts

A "Reservation" in Rapla is **not** the same as one calendar event. It
is the container for an event series:

```
Reservation                "Calculus 101" (Spring 2026)
  ├── Appointment 1        every Mon 09:00–10:00 (weekly, 14 occurrences)
  ├── Appointment 2        Wed 14:00–17:00 (one-off lab)
  └── Appointment 3        Fri 16:00–18:00 (every other week, biweekly)
       Allocatables:       Room A101, Lab B202, Lecturer Dr Smith
       Restrictions:       Lab B202 → only Appointment 2
                           Lecturer → all appointments
```

What renders in a calendar view is **AppointmentBlocks** — the
materialized occurrences of each Appointment, with the repeating rule
expanded. A Reservation with three weekly Appointments running for a
14-week semester produces ~42 visible blocks.

This page covers:

- The Reservation → Appointment → AppointmentBlock chain
- How allocatable restrictions per appointment work
- How the calendar model queries blocks
- How conflicts are computed and exposed

For the **overlap algorithm itself** (closed-open intervals, the
gcd fast path for repeating-vs-repeating, MONTHLY semantics), see
[../conflict-detection.md](../conflict-detection.md). This page
covers the entities and the integration points; that page covers
the math.

---

## Reservation: the container

`rapla-core/src/main/java/org/rapla/entities/domain/Reservation.java`
`rapla-core/.../entities/domain/internal/ReservationImpl.java`

A Reservation owns:

- **Appointments** — `List<Appointment>` (composite; deleting a
  reservation deletes its appointments).
- **Allocatables** — `List<Allocatable>` (referenced; not owned).
- **Restrictions** — `Map<Allocatable, Appointment[]>`. By default,
  every allocatable applies to every appointment; a non-empty
  restriction array narrows that to the listed appointments only.
- **Classification** — the schema-driven attribute bag (title,
  course code, etc.). See [dynamic-types.md](dynamic-types.md).
- **Permissions** — per-reservation ACL controlling read-event
  visibility.
- **RequestStatus** — `Map<Allocatable, RequestStatus>` for the
  request-approve workflow (a non-admin user "requests" an
  allocation, an admin approves or rejects). See
  [permissions.md](permissions.md).

Useful derived methods:

- `getFirstDate()` / `getMaxEnd()` — bounds across all appointments,
  expanding repeats.
- `getSortedAppointments()` — chronological by start.
- `getAppointmentsFor(Allocatable)` — appointments that actually use
  that allocatable, after applying restrictions.

## Appointment: a time block, optionally repeating

`rapla-core/.../entities/domain/Appointment.java`
`rapla-core/.../entities/domain/internal/AppointmentImpl.java`

Fields:

- `start`, `end` — `LocalDateTime`. Always in **GMT** internally;
  any TZ presentation happens at the UI boundary.
- `repeating` — optional `Repeating` rule.
- `wholeDaysSet` — flag that this is a whole-day event (start/end
  snapped to midnight).

Key ops:

- `move(newStart)` — shifts both endpoints, preserves duration and
  repeat structure.
- `overlapsAppointment(Appointment other)` — symmetric overlap
  predicate; the entry point for conflict detection. See
  [../conflict-detection.md](../conflict-detection.md) for how
  it dispatches by repeating shape.
- `createBlocks(start, end, list)` — expands the repeating rule
  within the [start, end) window and appends `AppointmentBlock`s.

### Repeating

`rapla-core/.../entities/domain/Repeating.java`
`rapla-core/.../entities/domain/internal/RepeatingImpl.java`

| `RepeatingType` | Step rule | Notes |
|---|---|---|
| `DAILY` | `start + interval × N` days | Fixed-interval. |
| `WEEKLY` | `start + 7 × interval × N` days, optionally restricted to specific weekdays | Fixed-interval if 1 weekday; variable if multiple. |
| `MONTHLY` | **Nth weekday of the month** (e.g. "third Thursday"), NOT same day-of-month | Variable-interval. The semantic that surprises people. |
| `YEARLY` | Same date next year. **Feb 29 anchor: skips non-leap years entirely** (does NOT roll to Feb 28). | Variable-interval. |

Stored fields:

- `interval` — repeat step count.
- `number` (count) **or** `end` (date) — bounded by occurrences or by date.
- `weekdays` — `Set<Integer>` (1=Mon … 7=Sun, ISO).
- `exceptions` — `Set<LocalDateTime>` of skipped occurrences.

#### Worked examples

- `MONTHLY` anchor 2026-06-15 (3rd Monday): next occurrences are
  2026-07-20, 2026-08-17, 2026-09-21 — the 3rd Monday of each month.
  **Not** 2026-07-15, 2026-08-15.
- `YEARLY` anchor 2024-02-29 with `number=5`, queried over
  2024..2032: produces **2** occurrences (2024-02-29, 2028-02-29).
  Non-leap years 2025/26/27/29/30/31 are skipped — the anchor does
  not roll to Feb 28. A user who wants "every February" should use
  day=28.

#### Pinned in tests

| Semantic | Test |
|---|---|
| MONTHLY = Nth-weekday-of-month (3rd Monday → 3rd Monday) | `AppointmentBlockExpansionHardeningTest.monthlyIsNthWeekdayOfMonth` |
| YEARLY anchor on Feb 29 skips non-leap years | `AppointmentBlockExpansionHardeningTest.yearlyLeapYearFeb29SkipsNonLeapYears` |
| DAILY with `number=N` produces exactly N occurrences | `AppointmentBlockExpansionHardeningTest.dailyFixedNumberYieldsExactCount` |
| DAILY "forever" is bounded by query window | `AppointmentBlockExpansionHardeningTest.dailyForeverIsBoundedByWindow` |
| WEEKLY with multiple weekdays produces one per picked day per week | `AppointmentBlockExpansionHardeningTest.weeklyMultipleWeekdaysProducesAllPickedDays` |
| Exceptions excluded by default, included when `excludeExceptions=false` | `AppointmentBlockExpansionHardeningTest.{excludeExceptions*}` |
| MONTHLY×MONTHLY overlap (slow path) | `AppointmentOverlapHardeningTest.monthlyRepeatUsesNthWeekdayOfMonthNotSameDayOfMonth` |
| `ConflictFinder.sweepLine` direct semantics (self-pair skip, dedup of repeating-vs-repeating to 1, "neither reservation allocates this resource" skip, three-way overlap → 3 pairs, touching-edge non-overlap) | `ConflictFinderSweepLineTest` (rapla-server, tier-2 — needs `FacadeTestSupport` because `ConflictImpl(...)` casts to `AllocatableImpl` for the resolver) |

### AppointmentBlock

`rapla-core/.../entities/domain/AppointmentBlock.java`

A materialized occurrence: `(start, end, appointment, isException)`.
Not persisted, generated on demand by `Appointment.createBlocks(...)`.
Calendar views render blocks; conflict checks operate on appointments;
the overlap predicate handles both shapes.

## How the UI gets blocks: CalendarModel

`rapla-core/src/main/java/org/rapla/facade/CalendarModel.java`
`rapla-core/.../facade/CalendarSelectionModel.java` (mutable, UI state)

Calendar views interact through `CalendarModel`:

- `setSelectedDate(...)`, `setStartDate(...)`, `setEndDate(...)` — view bounds.
- `setSelectedObjects(...)` — which allocatables to filter to.
- `setReservationFilter(...)` / `setAllocatableFilter(...)` —
  classification filters (e.g., "only courses in CS department").
- `queryBlocks(TimeInterval)` → `Promise<Collection<AppointmentBlock>>`
  — returns blocks visible in the window, post-filter, post-permission.

`CalendarSelectionModelImpl` (in `org.rapla.facade.internal`) is the
implementation. It loads reservations through `RaplaFacade`, expands
appointments to blocks within the requested window, applies filters,
and short-circuits on permission denial.

## Per-appointment restrictions

By default, an allocatable applies to **every** appointment in the
reservation. To attach an allocatable to only some, use
`setRestriction(Allocatable, Appointment[])`. Effects:

- `getAppointmentsFor(allocatable)` returns only the restricted
  appointments.
- The conflict detector only checks the restricted appointments
  against other reservations on that allocatable.
- Calendar views that filter by allocatable show only the
  restricted blocks for that resource.

Example: a course reservation with three appointments where the lab
session uses Lab B202 only on the second Wed of each month.

```
reservation.setRestriction(labB202, [appointment2])  // only the Wed appointment
reservation.setRestriction(roomA101, Appointment.EMPTY_ARRAY)  // empty array means "all appointments"
```

A null or zero-length restriction array means **all appointments**
(the default).

## Conflicts

A **conflict** is two reservations on the same allocatable whose
appointments overlap in time. The model is intentionally pairwise:
three reservations all overlapping pairwise produce three Conflict
records (R1↔R2, R1↔R3, R2↔R3), not one three-way record.

### `Conflict` (facade type)

`rapla-core/src/main/java/org/rapla/facade/Conflict.java`
`rapla-core/.../facade/internal/ConflictImpl.java`

A Conflict is **not persisted as content** — it's an immutable record
computed by the server's `ConflictFinder`:

```
Conflict {
  allocatableRef:    ReferenceInfo<Allocatable>
  reservation1Ref:   ReferenceInfo<Reservation>
  appointment1Ref:   ReferenceInfo<Appointment>
  reservation2Ref:   ReferenceInfo<Reservation>
  appointment2Ref:   ReferenceInfo<Appointment>
  startDate:         LocalDateTime    // first overlapping moment
}
```

Its id is deterministic (a hash of allocatable + appointment ids),
so the same conflict survives across server restarts and is
addressable by clients.

### `ConflictFinder` (server-side index)

`rapla-server/src/main/java/org/rapla/storage/impl/server/ConflictFinder.java`

Maintained on the server (not the client). Indexed structures:

- `AllocationMap` — `Allocatable → Appointment[]` lookup.
- `conflictMap` — `Allocatable → Map<ConflictId, Conflict>`.

Lifecycle:

- **Init.** On server startup, walks every Allocatable and computes
  initial conflicts via `Appointment.overlapsAppointment(...)` for
  each pair.
- **Update.** Every dispatch (store/remove) reaches
  `LocalAbstractCachableOperator.dispatch(UpdateEvent)`, which
  reindexes the affected allocatables. Conflicts that
  appeared / disappeared show up in the resulting `UpdateResult`.

Two clients see the same conflicts because the conflict index lives on
the server. Clients learn about them through the modification stream
(see [flows.md](flows.md)).

The core algorithm is the `static sweepLine(Allocatable, today, Collection<AppointmentBlock>)`
method — priority-queue sweep that pairs overlapping blocks per
allocatable, deduplicates by `ConflictImpl.createId(...)`, and gates by
"is this allocatable actually allocated by one of the involved
reservations?" Two test classes cover it:
- `ConflictFinderViaFacadeTest` — facade-level (round-trip through
  `getConflictsForReservation`).
- `ConflictFinderSweepLineTest` — invokes `sweepLine(...)` directly
  with hand-built block collections; probes algorithmic edges
  (self-pair, dedup, three-way, edge-touching, cross-allocatable filter).

### Querying conflicts

From the facade:

- `facade.getConflicts(...)` — global list, filtered by what the user
  is allowed to see.
- `facade.getConflictsForReservation(r)` — neighbours of a single
  reservation. Used by the edit dialog to render conflict warnings
  in the AllocatableSelection panel.

Permission filtering: a user only sees a conflict if they can read
both reservations involved (via `PermissionController.canRead`). If
the user can read only one side, the conflict is hidden — leaking
its existence would leak the existence of the unreadable reservation.

### Disabling a conflict

`Conflict.isEnabledAppointment1` / `isEnabledAppointment2` allows an
admin to acknowledge a conflict without resolving it (a "this is fine,
they're sharing the room"). The server tracks disabled state in
`LocalCache.disabledConflictApp1` / `disabledConflictApp2`. Disabled
conflicts are still visible but rendered differently in the UI and
don't block saves.

### Time semantics, repeats, edge cases

For the overlap algorithm — closed-open intervals, the symmetric
fast-path for `single × single`, the gcd arithmetic for fixed-interval
repeating × repeating, the slow path for `MONTHLY × MONTHLY` —
see [../conflict-detection.md](../conflict-detection.md).

The most common surprises:

- `[09:00, 10:00)` and `[10:00, 11:00)` **do not overlap.**
- A daily repeat starting Mon and an interval-2 daily repeat starting
  Tue **never overlap** even though both are daily.
- A MONTHLY repeat starting Thu 2026-01-15 lands on Thu 2026-02-19,
  not on the 15th of every month. The "Nth weekday of the month"
  semantic.

---

## Worked example: opening a calendar week

```
1. SwingWeekView asks calendarModel.queryBlocks([Mon 00:00, Sun 24:00])
2. CalendarModelImpl resolves selected allocatables [A101, B202]
3. Calls facade.queryAppointments(user, allocatables=[A101,B202], interval=[Mon..Sun], filters)
4. The server side:
     a. For each allocatable in {A101, B202}:
          look up reservations that allocate it (via AllocationMap)
     b. For each (reservation, appointment) pair where appointment
          either restricts to this allocatable or has no restriction:
          appointment.createBlocks(Mon, Sun, list)
     c. Apply permission filter (canRead per reservation)
     d. Apply ClassificationFilter rules
5. Server returns Collection<AppointmentBlock>
6. CalendarModelImpl groups blocks by day-and-allocatable
7. SwingWeekView renders each block as a SwingRaplaBlock at its
   computed pixel position
8. ConflictFinder is *not* queried here — conflicts are a separate
   surface (ConflictsView) and show up via getConflicts()
```

For mutation (drag, resize, double-click → edit) see
[reservation-edit.md](reservation-edit.md).

## See also

- [../conflict-detection.md](../conflict-detection.md) — the overlap
  algorithm and its edge cases
- [domain-model.md](domain-model.md) — Reservation / Appointment /
  Repeating / AppointmentBlock entries
- [reservation-edit.md](reservation-edit.md) — what happens when a
  user mutates an event
- [flows.md](flows.md) — how dispatch propagates conflict updates
  back to clients
