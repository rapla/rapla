# 026 — Angular frontend (reservation editing)

**Status:** draft (research / scoping only — no implementation)

## Goal

Replace the Swing reservation-edit UI with an Angular (or
equivalent modern SPA) frontend, served by the existing Spring
Boot backend over its REST surface. Initial scope: the reservation
creation and edit flow. Calendar views, admin panels, and plugin
UIs are out of scope for v1.

## Why

The Swing client is the long-tail technology debt:

- WSL2 / display-server / JNLP launch is fragile and gates new
  contributors (see PRD `done/jnlp-signing-pitfalls` follow-ups).
- The reservation-edit UI is the part users touch most, and the
  Swing implementation has ~1800 lines of edge-case glue in
  `AppointmentController` alone.
- The REST surface has been hardened enough (PRDs 009, 020, 024,
  025) that a browser client is now realistic.

## Scope

In scope:

- Reservation creation and edit (the flow documented in
  [`architecture/reservation-edit.md`](../architecture/reservation-edit.md)).
- Allocatable / resource selection for a reservation.
- Conflict overlay during edit (advisory, non-blocking).
- Repeating-rule editor with exception dates.

Out of scope for v1:

- Calendar views (week / month / day).
- Plugin admin UIs (Exchange, iCal, mail, etc.).
- Resource and user administration.
- The Swing client itself — runs alongside the new SPA until
  feature parity is reached.

## Reference

Everything an Angular implementation needs to know about the
domain and the wire model is in the architecture docs:

- [`reservation-edit.md`](../architecture/reservation-edit.md) — the
  full edit flow including the wire model (JSON shapes,
  endpoints), AppointmentController's rules, and the edge-case
  reference. **Read this first.**
- [`domain-model.md`](../architecture/domain-model.md) — entity catalog.
- [`dynamic-types.md`](../architecture/dynamic-types.md) — the EAV
  classification system.
- [`conflicts-and-events.md`](../architecture/conflicts-and-events.md)
  — what ConflictFinder considers an overlap.
- [`permissions.md`](../architecture/permissions.md) — what the
  server enforces on dispatch.

## Swing-concern → Angular-equivalent mapping

| Swing concern | Suggested SPA equivalent |
|---|---|
| Pre-save validation: `Set<EventCheck>` via Spring DI | Injection-token array of `(reservation) => Observable<boolean>` validators |
| Busy / spinner: RxJava3 `Subject<String>` | RxJS `BehaviorSubject` or a loading slice |
| Undo / redo: `CommandHistory` per dialog | Per-form state stack, lost on close |
| Cache hydrate: lazy via `ReferenceInfo` | Eager `GET /storage/resources` at app boot, then incremental via `/storage/refresh` |
| Conflict overlay: `getConflictingAppointments` + coloured block | `/storage/allocatable/bindings/all` keyed by the editing reservation's appointments |
| Mutable-clone edit cycle: `editListAsync` | First iteration: last-write-wins, surface `RaplaNewVersionException` as a refresh-and-retry dialog. Later: server-side clone endpoint |
| Per-field permission gating | Drive from permissions returned alongside the entity, not from a role string |
| Listener re-entrancy guard: `listenerEnabled` flag | `emitEvent: false` on Reactive Forms, or a manual `suppressNext` flag in state |

## Open questions

1. **Editing model.** Reproduce the clone cycle (fetch fresh on
   open, dispatch back) or accept last-write-wins for v1? Clone
   gives concurrent-edit detection; without it, Save can silently
   overwrite. Trade-off: implementation cost vs. data integrity.

2. **Recurring-exception UX.** Swing uses an interval picker
   (start/end date) for adding exception ranges. A more modern
   pattern is "click an occurrence, choose 'skip just this one'".
   The storage model is dates (not occurrences), so the
   interaction maps cleanly to either UI. Pick one.

3. **Calendar pagination for infinite recurrence.** Compute blocks
   on demand per viewport, not for the whole series. Swing does
   this via `Appointment.createBlocks(window, blocks)`. The SPA
   needs a similar discipline.

4. **Per-field permission gating.** The server returns the
   computed access level alongside the entity. Decide a
   directive / pipe pattern for hiding or read-only-rendering
   fields based on access level.

5. **Coexistence.** During the transition, the Swing client and
   the SPA both write through `/storage/dispatch`. The Swing
   client's long-poll refresh (`/storage/refresh`) will pick up
   SPA writes. The SPA needs equivalent invalidation — likely
   the same long-poll, or Server-Sent Events if we add them.

6. **Framework choice.** "Angular" is the working assumption from
   the title of this PRD; React / Vue / Svelte aren't ruled out.
   Decide before v1 plan freezes — the wire model is
   framework-agnostic but the validation / state plumbing isn't.

7. **Hosting.** Serve the SPA from rapla-app (`/webclient/` is
   currently the JNLP bundle path) or from a separate static
   host? Same-origin avoids CORS work and matches the existing
   `SecurityConfig`.

## Plan

To be drafted once the open questions are decided. The likely
shape:

- **Phase 0** — pick framework, pick coexistence model, pick
  hosting. Output: a revised plan section in this PRD.
- **Phase 1** — read-only reservation listing for one DynamicType,
  served from `/storage/resources` + `/storage/queryAppointments`.
- **Phase 2** — create new reservation (single appointment,
  no repeat, no allocatable).
- **Phase 3** — repeating-rule editor with exception dates.
- **Phase 4** — allocatable selection + conflict overlay.
- **Phase 5** — edit existing reservation (clone-or-last-write
  decision from Phase 0 lands here).

## Tests

To be drafted per phase. The reservation-edit edge-case list in
[`reservation-edit.md`](../architecture/reservation-edit.md) is
the test charter — every bullet there is a behaviour the SPA must
reproduce, and each one warrants a regression test.
