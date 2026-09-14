# PRD 105 — Pre-save reservation checks shared by Swing and SPA

**Status:** in-progress — Phases 1–3 shipped 2026-08-12; Phase 4 (enforcement) open
**Related:** [PRD 023 § Phase 10](023-presenter-view-extraction.md) (the EventCheck carve-out that
made the rules tier-neutral), [PRD 091](091-spa-reservation-edit-and-availability.md) (the SPA event
sheet; `potentialConflicts` / `resourceAvailability`; OQ5 request status — a dependency of this
PRD), [PRD 094](094-spa-main-view-actions-and-popups.md) (main-view commands), [PRD 067](067-server-mutation-unification.md) (server mutation path)

## Abstract

The Swing client runs a chain of pre-save checks over an edited reservation (no name, no resources,
duplicate appointments, conflicts, holidays, request-only allocations, not-in-current-calendar) and
asks the user to confirm or abort. The Angular SPA runs **none** of them: it saves silently, so an
event with zero resources or a conflicting room is created without a word. The decision logic is
already tier-neutral in rapla-core ([PRD 023](023-presenter-view-extraction.md) Phase 10) — only its
*invocation* is Swing-shaped. This PRD gives the checks a server-side home both tiers can call, and
wires the SPA to it. End state: the same seven warnings, from one implementation, in both clients.

## Implementation

**What exists** (verified 2026-08-11, do not rebuild):

| Piece | Where | State |
|---|---|---|
| Warning model | `org.rapla.client.edit.check.ReservationWarning` (rapla-core) — 7 stable `Code`s + args | tier-neutral, done |
| Pure rules | `DefaultReservationWarnings.evaluate(...)`, `RequestAllocationWarnings` (rapla-core) | tier-neutral, done |
| Swing invocation | `EventCheck` SPI (rapla-client, `PopupContext` + `Promise<Boolean>`), 4 `@Service` impls: `DefaultReservationCheck`, `ConflictReservationCheck`, `HolidayExceptionCheck`, `RequestAllocationCheck`; folded in `ReservationControllerImpl` | Swing-only by construction |
| Draft conflicts (Swing) | `facade.getConflictsForReservation(draft)` → `AbstractCachableOperator.getConflicts` → server `getAllAllocatableBindings` (ids + intervals travel, assembly client-side) | done |
| Draft conflicts (GraphQL) | `potentialConflicts(input: PotentialConflictInput!)` — schema comment already says *"chip drill-down / **save preflight**"* | **exists, unused by the SPA** |
| Availability + request-only | `resourceAvailability(input:)` → `AllocationStatus { AVAILABLE PARTIAL CONFLICT REQUEST_ONLY FORBIDDEN }` | used only in the add-mode search |
| Per-warning off switches | `CalendarOptionsImpl.SHOW_CONFLICT_WARNING`, `SHOW_HOLIDAY_WARNING`, `SHOW_HOLIDAY_WARNING_SINGLE_APPOINTMENT`, `SHOW_NOT_IN_CALENDAR_WARNING`, `SHOW_ABORT_EDIT_WARNING`; Swing UI `WarningsOption` | done — reuse, don't invent config |

**What gets built:** a `ReservationChecker` SPI in rapla-core (pure: context in, warnings out), the
standard checkers as server beans, a `ReservationCheckService` that folds them in a deterministic
order, and one GraphQL field. The SPA calls it before `createReservation`/`updateReservation` and
renders the codes through the existing i18n keys ([PRD 103](103-i18n-language-coverage.md)).

**Where each check can run** — this is what shaped D1/D3:

| Code | Needs | Server has it |
|---|---|---|
| `NO_RESERVATION_NAME`, `NO_ALLOCATABLES_SELECTED`, `DUPLICATED_APPOINTMENTS` | the draft only | yes |
| `CONFLICT` | booking data | yes (natively — better than the client) |
| `REQUEST_PENDING` | `PermissionController.isRequestOnly` + the allocation's request status | yes, but see D5 |
| `HOLIDAY_ON_APPOINTMENT` | holiday configuration | yes |
| `NOT_IN_CALENDAR` | the caller's current calendar model | **no** — see D3 |

**Shipped 2026-08-12 — what the build actually produced** (files, so the next session does not search):
`ReservationChecker` + `CheckContext` (rapla-core, next to the pure rules) · `StandardCheckers`
(rapla-app `…graphql.checks`, four `@Bean`s with `@Order` 10/20/30/40) · `ReservationCheckService`
(folds them, filters by the caller's `CalendarOptionsImpl` preferences) · `ReservationChecksController`
(`reservationChecks`) · SPA `reservation-checks.service.ts` + `reservation-warnings.ts` + the sheet's
warn box. Severity lives on `ReservationWarning.Code` (`ReservationWarningSeverityTest` pins it).

Three findings the build turned up, each fixed here:
1. **A transient draft has no resolver.** `getAllocatables()` threw `IllegalStateException: Resolver
   not set` — the write path gets the resolver during `dispatch`, a check runs before that.
   `buildTransientForCheck` attaches the operator (which IS the `EntityResolver`).
2. **§12 leak in the shared mutation mapper** (pre-existing, not introduced here): an allocatable the
   caller may not read answered `PERMISSION_DENIED` while an unknown id answered
   `REFERENCE_NOT_FOUND` — an existence oracle on `createReservation`/`updateReservation`, the exact
   pattern `MutationExistenceLeakTest` pins for entity ids. Both now answer `REFERENCE_NOT_FOUND`.
3. **Empty args are missing args.** The server sends `""` for a nameless draft, so `?? 'fallback'`
   printed `„" erscheint nicht …`. The SPA text layer treats `""` as absent.

Live-verified 2026-08-12 against the dhbw dev dataset: nameless draft → `NO_RESERVATION_NAME`
BLOCKING + `NO_ALLOCATABLES_SELECTED` CONFIRMABLE; a draft on an occupied room → `CONFLICT`; the same
draft in a free slot → nothing; a draft outside the stated scope → `NOT_IN_CALENDAR`, and inside it →
nothing. In the browser the blocking box refuses the save and offers no "Trotzdem speichern".

**Sequencing lesson (cost a peer session a debugging round):** a SPA query may only name a new server
field AFTER the server carrying it is restarted — `ng serve` rebuilds in seconds, so the SPA is
otherwise ahead of the API and every load fails validation.

## Goal

- `POST /api/graphql` with `reservationChecks(input:)` over a draft that has no allocatables returns
  `[{ code: NO_ALLOCATABLES_SELECTED }]`; over a draft on an already-booked room it also returns
  `CONFLICT`.
- Saving such a draft in the SPA shows a confirm dialog naming the same warnings the Swing dialog
  names for the same event — verified side by side.
- A warning switched off in the Swing options (`WarningsOption`) is absent from the GraphQL result
  for the same user.
- No warning rule exists twice: `grep -r "NO_ALLOCATABLES\|DUPLICATED_APPOINTMENTS" rapla-angular/src`
  finds only rendering (i18n keys), never a re-implementation of the rule.

## Scope

### In scope
- `ReservationChecker` SPI + the four standard checkers as server beans, deterministic order.
- `reservationChecks(input:)` GraphQL field over a draft (same input shape as the save mutations).
- Per-code enablement read from the existing `CalendarOptionsImpl` preferences.
- SPA: save preflight, confirm/abort dialog, i18n rendering.
- §12 leak test for the new field (a draft naming ids the caller may not read must not confirm they exist).

### Out of scope
- Enforcement on the write path (server refusing to persist) — see D2 and the "later" phase.
- Migrating Swing's four `EventCheck` impls to the service — D6 keeps them; they already call the
  same pure functions.
- The request-only *workflow* (approve/deny). This PRD only needs the status to exist — see D5.
- New warning kinds. Seven codes exist; this PRD moves them, it does not extend them.

## Plan

### Phase 1 — Server: the checker chain ✅ 2026-08-12
- [x] `ReservationChecker` (rapla-core): `List<ReservationWarning> check(CheckContext ctx)`; `CheckContext`
      carries the transient reservation, the caller, the locale, the scope ids (D3), the enabled codes,
      and the expanded occurrences per appointment (D9 — via the existing `expandOccurrences` engine).
- [x] Each warning carries its severity (D7: `NO_RESERVATION_NAME` = blocking, rest = confirmable),
      read off the Swing impls once and pinned by a test.
- [x] Four standard checkers wrapping the existing pure functions + the conflict and holiday sources.
- [x] `ReservationCheckService`: injects `List<ReservationChecker>`, `@Order`-deterministic,
      filters disabled codes from the caller's preferences.
- [x] Wiring via explicit `@Bean` factories (AGENTS.md §3 — the server does not `@ComponentScan` internals).

### Phase 2 — GraphQL surface ✅ 2026-08-12
- [x] `reservationChecks(input: ReservationCheckInput!): [ReservationWarning!]!`; the transient reservation
      is built with the SAME mapper `createReservation`/`updateReservation` use.
- [x] Tier-3 test + the mandatory §12 leak test (`ReservationChecksGraphQLTest`, 5/5).

### Phase 3 — SPA preflight ✅ 2026-08-12
- [x] **Dialog, not an inline panel** (2026-08-12, user-corrected — see D10) — one
      `ReservationWarningsDialogComponent` for every write path, `ReservationChecksService.confirm(...)`
      as the single funnel.
- [x] CONFLICT findings carry their clashing bookings, fetched from the existing `potentialConflicts`.
- [x] Drag/resize preflight: `moveChecks(input:)` — the server computes the state the move verb would
      store (`prospectiveAppointmentMove` / `prospectiveReservationsMove`, extracted from the verbs) and
      checks that; the SPA never rebuilds a move payload (PRD 101). `moveCheckInput(...)` sits next to
      `buildMoveScopeCommand` so both halves of a gesture stay together.
- [x] `event-sheet` save → `reservationChecks` → confirm dialog (blocking vs confirmable per code) → save.
- [x] Render codes via `reservation-warnings.ts` (texts + gate only, no rule logic); no rule logic in TypeScript.

### Phase 4 — later, not now
- [ ] **SINGLE-on-repeating drag has no preflight.** That gesture goes through `splitOccurrence`,
      whose prospective state mints an id and can delete the emptied reservation — no dry-run yet, so
      the drag proceeds unchecked. Everything else (SERIE, EVENT, resize) is covered.
- [ ] Enforcement (`acknowledge: [Code!]` on the mutations) so non-SPA API callers get the same gate.
- [ ] An SPA options page over the same `CalendarOptionsImpl` preference keys.

## Tests

- rapla-core: the pure rules already have `DefaultReservationWarningsTest` / `RequestAllocationWarningsTest` — extend, don't duplicate.
- rapla-app tier-3: `reservationChecks` over drafts (no name / no allocatables / duplicate appointments / conflicting room), plus a preference-disabled code disappearing from the result.
- rapla-app tier-3 leak test: a draft citing an allocatable id the caller cannot read yields the same result as one citing a nonexistent id.
- SPA tier-5: the dialog's blocking-vs-confirmable mapping per code (pure function, no TestBed).
- Manual parity probe: same event, Swing dialog vs SPA dialog, same warning set.

## Open Questions

None open. The one external dependency — **[PRD 091 OQ5](091-spa-reservation-edit-and-availability.md#open-questions)**
(`requestStatus` in the schema) — **shipped 2026-08-12**, so `REQUEST_PENDING` has real state to
report in both tiers.

## Decisions locked

**D1 — Dry-run field, not warnings-on-save** (2026-08-11, user decision). The SPA asks
`reservationChecks(input:)` before it saves — the same shape Swing has, where the check chain runs
before `dispatch`. The save contract stays untouched, and Swing (which writes via `dispatch`, not
GraphQL) is unaffected. Rejected: warnings returned by `createReservation`/`updateReservation` with
an `acknowledge` list — one roundtrip cheaper, but it changes the write contract for every caller and
protects only GraphQL clients; kept as the Phase-4 hardening.

**D2 — Advisory, not enforcing** (2026-08-11, user decision). The server returns codes; the client
decides. This mirrors Swing, where every warning except the missing name is confirmable, and it keeps
the two tiers symmetric. Enforcement would need the same chain on `dispatch` too, or Swing and SPA
would obey different rules.

**D3 — `NOT_IN_CALENDAR`: scope chips travel, classification filter does not** (2026-08-11).
Swing's `CalendarModelImpl.isMatchingSelectionAndFilter` intersects the reservation's
allocatables **∪ its DynamicType ∪ its owner** with the model's selected objects, then requires the
model's `ClassificationFilter[]` to match. The SPA has chips (`resource | user | event`) but its
filter lives inside the stored view query, which cannot be evaluated against an unpersisted draft.
So the SPA sends its chips and the server evaluates the **intersection half only** (resources +
owner) — it catches the everyday case ("you are scoped to room X and this event has no room X").
The classification-filter half stays Swing-only and is a documented gap, not an oversight.
Rejected: full parity (would require running the view query over a transient reservation) and
Swing-only (leaves the SPA with no signal at all).

**D4 — `ReservationChecker` beans, order-deterministic, per-code preference switch** (2026-08-11,
user decision). Swing's model is an extension point (`EventCheck`) whose impls are `@Service` classes
folded from an injected `Set`. The server mirrors it with a `ReservationChecker` interface and
explicit `@Bean` factories (AGENTS.md §3 forbids `@ComponentScan` for server internals); a plugin
autoconfiguration contributing a bean of that type is picked up automatically, so dhbwrapla can add
checks without a rapla change. Two deliberate differences from Swing: `@Order` (the result is a list,
not a first-false abort, so the order must be stable) and **per-code enablement read from the
existing `CalendarOptionsImpl` preferences** — the same keys the Swing `WarningsOption` page writes,
so a warning switched off there is off in the SPA too. No new config mechanism.

**D5 — `requestStatus` is a dependency, resolved in PRD 091, not here** (2026-08-11, user decision;
**dependency SHIPPED 2026-08-12** — `Allocation.requestStatus` reads, the server derives it on write
via `isRequestOnly`, see [PRD 091 OQ5](091-spa-reservation-edit-and-availability.md#open-questions).
`REQUEST_PENDING` is therefore no longer inert; the paragraph below records why it was).
`REQUEST_PENDING` fires on allocations in `REQUESTED` state; `requestStatus` does not exist anywhere
in the GraphQL schema, so the SPA can neither read nor set it — and Swing sets it on assignment
(`RaplaComponent.addAllocatables`: `isRequestOnly` → `RequestStatus.REQUESTED`). Consequence today
(verified 2026-08-11 in `SecurityManager`, ~line 478): the server REJECTS the save — a user with
request-only permission gets `warning.no_reserve_permission` and has no way to resolve it, because
the status the server looks for cannot be expressed over GraphQL. Not a silent booking: a dead end.
That is a PRD 091
concern (its `OQ5` asks exactly this and is still pending, and its `AllocationStatus.REQUEST_ONLY`
already models the read side). PRD 105 consumes the field once 091 lands it; until then
`REQUEST_PENDING` is inert in the SPA.

**D10 — One dialog for every write path, no inline panel** (2026-08-12, user decision after a
first attempt shipped a panel inside the event sheet). The panel was chosen because the pattern
already existed there, not because it was weighed — and it failed on three counts the user named:
it goes stale after an edit (it describes a past save attempt), re-checking still requires pressing
save, and **drag/resize run the same checks with no sheet to render into**. Swing settles it too:
`ReservationControllerImpl.checkEvents` is called from every write path and always opens a dialog.
So: `ReservationWarningsDialogComponent` + `ReservationChecksService.confirm(...)`/`confirmMove(...)`
as the funnel every path uses. A CONFLICT additionally lists its clashing bookings — a bare
"erzeugt Konflikte" is what made the first attempt useless, and `potentialConflicts` already had
the data.

**D6 — Swing keeps its own invocation** (2026-08-11). The four `EventCheck` impls stay; they already
call the same rapla-core functions, their dialogs are interactive in a way the service's flat list is
not, and rewriting a working path is risk without benefit. The service is the SECOND caller of one
shared rule set, not a replacement. Revisit only if a check appears in one tier and is missed in the
other.

**D7 — Severity is copied from Swing, 1:1** (2026-08-11, user decision). Swing aborts the save on
`NO_RESERVATION_NAME` and offers *continue/cancel* on the other six. The SPA dialog adopts exactly
that split — read off the four `EventCheck` impls (their `false` return = abort), never re-decided
per client. A warning that stops one tier and shrugs in the other is worse than no warning: the same
event would be creatable or not depending on which client the user happens to hold. The severity
therefore travels with the code, as a property of the check, not of the dialog.

**D8 — Per-user preferences only; no admin-forced checks in v1** (2026-08-11, user decision). The
five `CalendarOptionsImpl` keys stay what they are: each user switches their own warnings off, and
the server check service honours the *caller's* preference. A system-wide override that a user
preference cannot defeat is one line (system preference wins) but a policy question nobody has
asked yet — deferred until a concrete case appears (a candidate would be "event without resources"
in a deployment that must not allow silent skipping).

**D9 — Repeating drafts are checked per OCCURRENCE, like Swing** (2026-08-11, user decision).
`HolidayExceptionCheck` builds a `Map<Appointment, Set<Period>>` — which holiday periods each
appointment's occurrences actually hit — and filters it through the tier-neutral
`HolidayWarningModel.filterByPreference(periodConflicts, showWarning, showWarningSingleAppointment)`;
the second preference is what distinguishes "warn for series" from "warn for single appointments too".
Conflicts are equally per-occurrence (the bindings request expands the repeating rule). The server
side already has `expandOccurrences` for exactly this expansion — reuse it, do not re-expand rules.

*Explicitly NOT copied:* Swing's holiday dialog can write the offending days back into the repeating
rule as **exceptions**. That is an action, not a warning, and a flat warning list cannot express it.
The SPA shows the holiday hits; adding exceptions from there is a separate feature
([PRD 091](091-spa-reservation-edit-and-availability.md) recurrence editor territory), not part of
this check chain.
