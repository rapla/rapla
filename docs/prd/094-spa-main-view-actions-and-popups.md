# PRD 094 — SPA main-view actions & popups (command pattern + post-action undo)

**Status:** draft — 2026-07-07 (updated 2026-07-09: calendar drag/resize **move** pulled in as a command producer — Phase 4 + D5; scope logic locked server-side in a new `moveAppointment` GraphQL mutation)
**Related:** [PRD 091](091-spa-reservation-edit-and-availability.md) (event sheet — D5 locks *in-sheet* undo as memento, pre-save
only, and moves everything past the save boundary HERE), PRD [077](077-calendar-model-graphql.md)/[078](078-spa-graphql-view-renderer.md) (view model +
renderer — the main view these actions live on; the calendar surface hosts the
Phase 4 drag/resize, but the move *command + scope dialog + mutation* are owned
here), [PRD 091](091-spa-reservation-edit-and-availability.md) (recurrence semantics the `moveAppointment` SINGLE-split reuses),
[PRD 093](093-loan-lifecycle.md) (loan lifecycle — its status actions are the first archetype-specific
command producers), [PRD 056](056-graphql-events-write-api.md) (mutation contract incl. §9 id-integrity/retry — the
create-inverse relies on it), [PRD 067](067-server-mutation-unification.md) (D7: GraphQL write surface adjustable)

## Abstract

Give the SPA main view (table lens today, calendar surfaces later) row/context
**actions and popups** — delete, duplicate, loan status changes, quick access to
the editor — and introduce the **command pattern** for them: every committed
action is a command object carrying a label and a *compensating GraphQL mutation*,
surfaced as a post-action **"Rückgängig" toast**. This is the SPA counterpart of
Swing's *global* `CommandHistory` (menu bar) — the architecture line [PRD 091](091-spa-reservation-edit-and-availability.md) D5
drew: memento inside the draft dialog, commands for committed actions on shared
state.

Grounding: `docs/architecture/reservation-edit.md` § "Command / undo catalog"
(the Swing global-history command inventory this PRD maps to the SPA) and the
market research in [PRD 091](091-spa-reservation-edit-and-availability.md) D5 (post-action single-shot undo toast is what Google
Calendar / Trello / Asana ship; deep client-side stacks for committed actions
exist nowhere in the calendar space).

## Why command pattern here (and not memento)

Committed actions mutate **shared, versioned, multi-pod server state**. A client
snapshot restore would be a silent lost update (it would overwrite concurrent
edits). A command's inverse is a *domain operation* — a normal mutation that runs
through `expectedLastChanged` version checks, permissions and validation, and
fails loudly ("Rückgängig nicht möglich — wurde inzwischen geändert") instead of
silently clobbering. Swing's `SaveUndo.undo()` does exactly this (refetch mutable
copies → dispatch the old version as a normal write). The command also carries
what a snapshot cannot: the **label** ("Ausleihe gelöscht") and the **scope
semantics** of the action (Swing's `AppointmentResize` encapsulates the
only-this-date/series/whole-event branching — the model for later calendar
commands).

## Swing global-history inventory → SPA mapping

From `docs/architecture/reservation-edit.md` (global commands table):

| Swing command | SPA equivalent | This PRD? |
|---|---|---|
| `SaveUndo` (save from edit dialog) | toast after sheet save; inverse = `updateReservation` with the captured pre-save state (or `deleteReservations` after a create) | yes (Phase 2) |
| `DeleteUndo` | delete action on a table row; inverse = `createReservation` re-creating the captured full state **with the same ids** (D3 id-first makes re-create id-stable — conflicts/links re-attach) | yes (Phase 2) |
| `AppointmentResize` (drag/resize, scope dialog) | calendar drag/resize **move** commands | **yes (Phase 4)** — the scope dialog + command/toast infra live here; the EVENT/SERIE/SINGLE cascade is a new server `moveAppointment` mutation (D5) |
| `AllocatableExchangeCommand` | drag between resource rows | no — [PRD 077](077-calendar-model-graphql.md) |
| `AppointmentPaste` / `ReservationPaste` | copy/duplicate action | duplicate-as-new: candidate (Phase 3); paste semantics deferred |
| `ConflictEnable` | conflict view action | no — future conflict surface |
| *(no Swing equivalent)* | loan status transitions (planned▸out▸returned, [PRD 093](093-loan-lifecycle.md)) | producer only — commands defined in 093, run on this infra |

## Goal

1. A reusable client command layer: `{label, execute(): mutation, invert():
   mutation}` + a single-slot post-action undo (toast with "Rückgängig", app-shell
   `MatSnackBar`), with the inverse running as a normal version-checked mutation.
2. Main-view row/context actions on the table lens: open editor (exists, 091
   2.8), **delete with undo toast**, "new" (exists); popup shell = `mat-menu`
   context menu per row.
3. Measurable: delete a loan from the table lens, click "Rückgängig" within the
   toast window → the reservation is back with identical ids; a concurrent edit
   between delete and undo yields the loud failure toast, never a silent
   overwrite.

## Scope

### In scope
- Command abstraction + single-slot undo holder (depth 1, replaced by the next
  action — the market norm; a deeper stack is speculative until a consumer asks)
- App-shell toast surface ("<label> — Rückgängig", auto-dismiss window)
- Table-lens row context menu (popup): Bearbeiten / Löschen (/ Duplizieren as
  candidate); toolbar actions stay as-is
- Delete + save-from-sheet as the first two command producers
- Own-actions-only semantics (never undo another user's change)

### Out of scope
- In-sheet (pre-save) undo — [PRD 091](091-spa-reservation-edit-and-availability.md) D5 (memento)
- Calendar drag/resize **move** is now Phase 4 (in scope). Still out: **paste**
  (needs a paste-target model — [PRD 077](077-calendar-model-graphql.md) calendar surface) and
  `AllocatableExchangeCommand` (drag between resource rows — [PRD 077](077-calendar-model-graphql.md))
- Loan-specific transitions — [PRD 093](093-loan-lifecycle.md) defines them, they only *run* here
- Multi-step global history / cross-session undo (server trash-can semantics
  would be its own design)
- Swing changes

## Plan

Design locked 2026-07-07 (D3); implementation deferred — no phase started.

### Phase 1 — Context menu shell + Bearbeiten/Anzeigen — DONE 2026-07-07
- [x] Menu shell on the view-host row (`view-host.component.ts`): ONE `mat-menu`
      fed by the ⋮ button (hover-visible, `.row-menu-btn`) + `contextmenu`
      (right-click, positioned `ctx-anchor` trigger). Long-press: Android fires
      `contextmenu` natively (covered); iOS Safari needs an explicit long-press
      handler — deferred until a touch consumer exists. Actions column
      `__actions` only appears when ≥1 row has menu items; subject-less rows
      (aggregates) get no button (D4).
- [x] `RowMenuProvider` registry (`views/row-menu.ts`, `ROW_MENU_PROVIDERS`
      multi-token, registered in `app.config.ts`); `RowContext` from
      `views/row-context.ts` (`extractRowContext` — subject aliases
      reservation/allocatable/user + `reservationId` scalar fallback +
      secondary allocatable refs from Allocatable-typed cells). 8 tier-5 specs.
- [x] `EventRowMenuProvider`: **Bearbeiten** (`canModify`) + **Anzeigen**
      (always; opens the sheet dialog with new `EventSheetDialogData.readOnly`
      — `canModify` forced off before and after load). 6 tier-6 specs
      (`view-host-menu.spec.ts`: gating, dialog data, right-click) + 1 readOnly
      spec. Server: `rapla_reservations` builtin gained
      `reservationId: id @hidden` + `canModify @hidden`
      (ViewCatalogControllerTest 12 green).
- [x] Row edit entry point closes the 091 2.8 gap (edit from the table lens).

### Phase 2 — Command + history + Löschen + Neu + header undo/redo — DONE 2026-07-07
- [x] `actions/command.ts` (pure TS): `SpaCommand` = `{label, execute(): Observable<MutationResult>, undo: (() => Observable<MutationResult>) | null}` — the inverse is a compensating domain mutation, not a client state restore.
- [x] `actions/undo-toast.service.ts` (app shell, `MatSnackBar`): runs a command, on `ok` shows the "Rückgängig" snackbar (15 s) + fires `mutated$`; undo click runs the inverse; `concurrent`/`denied`/`invalid` on either direction → loud error toast, `mutated$` NOT fired. Single slot. 4 tier-5/6 specs. **OQ1 resolved: 15 s** fixed window (Asana precedent). OQ2 (`z` shortcut) still open — button only for now.
- [x] **Delete-scope logic ported (full Swing parity, not the earlier v1 simplification).** `views/delete-scope.ts` (pure): `deleteScopeOptions` (the `showDialog` predicates → event / serie / single) + `applyDeleteScope` (data-model effects: whole-event delete, appointment removal with restriction-map cleanup + empty-restriction drop, day-truncated exception add, last-appointment cascade → whole-event). 14 tier-5 specs. Grounded in the new docs/architecture/reservation-edit.md § "Delete — the scope dialog and its cascades".
- [x] `views/delete-scope-dialog.component.ts` — the "Was möchtest du löschen?" chooser (radio list; degrades to a plain confirm at one option).
- [x] `actions/event-commands.ts` `buildDeleteCommand`: whole-event → `deleteReservations` ⇄ `createReservation` (captured full state, SAME ids — D3 id-stable); scoped → full-state `updateReservation` of the modified draft ⇄ update back to the captured original, with the inverse's `expectedLastChanged` captured by a reload right after the forward save (a third-party edit between delete and undo then fails loudly, never silent-overwrites).
- [x] **Löschen** in `EventRowMenuProvider` (`canModify` + not an exception block — Swing parity): load → scope dialog → command → toast. View re-queries on `UndoToastService.mutated$`. 8 tier-6 specs in `view-host-menu.spec.ts`.
- [x] Server: `appointmentId: ID!` on `AppointmentBlock` (`APPOINTMENT_BLOCK_APPOINTMENT_ID` fetcher) + builtin `rapla_appointments` view gained `appointmentId @hidden` + `isException @hidden`; `RowContext.block` carries `(appointmentId, start, isException)`. Server tests green (block-id + view catalog).
- [x] **Neu, type-aware (Swing wizard-submenu analog):** toolbar "Neu" becomes a `mat-menu` when >1 creatable RESERVATION type exists (one entry per type, pre-selects `typeKey` via `newDraft`), plain button at exactly one type. `app-toolbar.component.ts`; 2 tier-6 specs. Lending-lens "Neue Ausleihe" falls out as the one-type case. NOT in the row menu (object menu); "new from context" stays the quick-create entry point (Phase 3 / [PRD 077](077-calendar-model-graphql.md)).
- [x] **Scope pre-allocation (Swing parity):** the view's selected resource scope chips (`FilterStore`, kind `resource`) are pre-added as allocations of the new event (applies-to-all, null restriction) — as Swing pre-allocates the marked allocatables on a new reservation. `user` chips (owner filter) are not added. tier-6 spec.
- [x] **Header undo/redo (D2 revised — flat stack, not single-slot).**
      `UndoToastService` became the main-view command history: `past`/`future`
      `SpaCommand` stacks (cap 5, signals), `undo()`/`redo()` run one entry per
      click (undo = inverse, redo = re-execute forward), drop-on-stale (a failed
      direction shows the loud error + drops the entry, never corrupts the
      stack). Header ↶/↷ `matIconButton`s next to "Neu" (`.undo-btn`/`.redo-btn`,
      disabled/tooltip from canUndo/canRedo/labels). The toast stays as immediate
      feedback; its Rückgängig action calls `undo()`. 10 tier-5/6 service specs +
      2 toolbar specs. Survives paging within the session (in-memory);
      localStorage persistence across reload is a later increment.
- [ ] Sheet-save-as-command toast (inverse = `updateReservation` pre-save state) — deferred; delete is the shipped producer, sheet save still owns its own path.

### Phase 4 — Calendar drag/resize move (scope dialog + `moveAppointment` mutation)

> **⚠ Mutation design moved to [PRD 101](101-transpose-anchors-move-copy-paste.md)**
> (2026-07-09): the `moveAppointment(scope, dateShift)` shape referenced below was
> superseded by the anchor-based verb family (typed `Anchor` input, scope-split verbs).
> D5's principle (scope logic server-side) stands — recorded as [PRD 101](101-transpose-anchors-move-copy-paste.md) D1. This phase's
> task list gets rewritten once [PRD 101](101-transpose-anchors-move-copy-paste.md) Phase 1 locks the verbs.

Reuses the Phase 2 command/toast/history infra. Grounded in
`docs/architecture/reservation-edit.md` § "Drag / resize on the calendar" (Swing
`AppointmentResize.change()` — the EVENT/SERIE/SINGLE cascade this ports) and its
"SPA gap" subsection.

Current SPA state: the week grid enables drag only when `canModify &&
appointmentCount === 1 && repeating === null` (`week-grid.component.ts`
`isMovableRow`) and commits via `moveReservations(ids, dateShift)` — i.e. **only
the lone case where Swing skips the dialog** (EVENT is the sole safe action).
Multi-appointment and repeating blocks don't drag today.

- [ ] **Server: `moveAppointment` mutation — designed in [PRD 056](056-graphql-events-write-api.md)
      § "Verb-level semantic notes" (the `moveAppointment` verb + `AppointmentEditScope`
      enum + per-scope semantics live there, since [PRD 056](056-graphql-events-write-api.md) owns the reservation
      write surface).** Summary: `moveAppointment(reservationId, appointmentId,
      occurrenceStart, dateShift, scope: EVENT|SERIE|SINGLE, keepTime, newEnd,
      expectedLastChanged): Reservation!` carries the full
      `AppointmentResize.change()` cascade server-side (EVENT = shift all; SERIE =
      shift the whole repeating appointment; SINGLE = split off + `addException`,
      with the `isNotEmptyWithExceptions` empty cascade). Test-first (tier-3
      GraphQL): one case per scope + last-occurrence-becomes-empty + permission
      denied. **This is the D5 server work.**
- [ ] **Resize form.** The same `moveAppointment` verb with a `newEnd` argument
      (Swing runs move and resize through the one `showDialog(..., "move", ...)`
      path) — see [PRD 056](056-graphql-events-write-api.md). No separate `resizeAppointment` verb.
- [ ] **Client dialog:** reuse `views/delete-scope-dialog.component.ts` pattern
      for a move-scope chooser (EVENT/SERIE/SINGLE) with Swing's show-when
      predicates (skip the dialog when only one option qualifies — the current
      single-appointment drag path). Extend `views/delete-scope.ts` alongside it or
      add `views/move-scope.ts`.
- [ ] **Command:** `actions/event-commands.ts` `buildMoveCommand`: forward =
      `moveAppointment(scope)`, inverse = `moveAppointment` with the negated shift
      and the SAME scope (server re-derives; SINGLE's inverse needs care — likely a
      captured-state `updateReservation` back to the pre-split reservation, since a
      split is not self-inverting). Runs through `UndoToastService` (toast +
      header history, drop-on-stale).
- [ ] **Grid gate:** widen `isMovableRow` to allow multi-appointment/repeating
      blocks (still `canModify`); the scope dialog handles the branching.

### Phase 3 — Candidates (each needs its own go)
- [ ] Duplizieren (create-as-new from an existing event, fresh ids, opens sheet as draft)
- [ ] Loan transitions ([PRD 093](093-loan-lifecycle.md)) as the first external `MenuItemProvider`
- [ ] Request confirm/deny (UC-E9 / 091 OQ5) as a provider
- [ ] Quick-create window entry point (091 2b.2 has none yet — a "+" cell/slot action could open it; coordinate with [PRD 077](077-calendar-model-graphql.md) calendar-click)

## Tests

- Tier 5: command construction/inversion (delete→create round-trip on the input
  shape), single-slot replacement, own-action guard
- Tier 6: toast render + undo click wiring; context menu permission gating
- Live probe / tier-7 candidate: delete→undo→ids identical; concurrent-edit
  between delete and undo → failure toast

## Open Questions

- **OQ1** — Toast dismiss window: fixed (Asana ~15 s) vs. until-navigation
  (Google) vs. until-replaced. *Resolution:* pending.
- **OQ2** — `z` keyboard shortcut for the toast undo (Google/Trello precedent) —
  worth the global key handler? *Resolution:* pending.
- **OQ3** — Does the delete inverse restore *request status* and other
  server-managed fields the mutation input cannot express ([PRD 091](091-spa-reservation-edit-and-availability.md) 2.0a
  preserved them on update — create is a different path)? Needs a server-side
  answer; possibly a dedicated `restoreReservation` mutation instead of plain
  `createReservation`. *Resolution:* pending.
- **OQ4** — Where does the captured inverse state live if the user navigates
  within the SPA before the toast expires (service-held, survives route change —
  but a reload loses it; acceptable?). *Resolution:* pending — likely yes,
  matching all surveyed products.

## Decisions locked

**D1 — committed actions use the command pattern with compensating mutations;
client state restore is forbidden past the save boundary (2026-07-07).** Inherited
from [PRD 091](091-spa-reservation-edit-and-availability.md) D5's boundary analysis: shared/versioned/multi-pod state can only be
un-done by a domain inverse that passes version checks (`expectedLastChanged`),
permissions and validation — exactly Swing's global-history model (`SaveUndo`
dispatches the old version as a normal write). Alternatives rejected: memento
restore of committed state (silent lost update on concurrent edits); no undo at
all (the post-action toast is the one undo feature every surveyed product ships —
Google Calendar, Trello, Asana — and protects against the *expensive* mistakes,
the saved ones).

**D2 — flat command history in the header (undo/redo, one per click,
drop-on-stale), plus the toast as immediate feedback (revised 2026-07-07,
maintainer discussion).** *Supersedes the original single-slot-only decision.*

The single-slot toast has a real UX gap the maintainer surfaced: the undo
affordance is transient (15 s / replaced), but a user often only notices the
mistake *after* paging or scrolling the view — by then the toast is gone. Fix:
a **persistent** undo affordance, not a deeper *guaranteed* history.

Crucially, the concurrency objection that justified single-slot is **wrong**:
"the reservation was moved in another window before I undo" applies **identically
at depth 1** — the inverse's `expectedLastChanged` goes stale and it fails loudly
either way. That foreign-edit failure mode does not get worse with depth. The
*only* genuinely new failure at depth ≥2 is **self-interference**: my own later
command advancing the token an earlier command's inverse assumed. And that is
avoidable by construction:

- **Undo/redo one entry per click** (never multi-undo) → each step is atomic,
  fails independently, no "partial failure mid-stack".
- **Drop-on-stale** → if an inverse fails (concurrent / denied / invalid — foreign
  OR self), show the loud error and *drop that entry*, don't corrupt the stack.
  This is the same handling depth 1 already has, applied per entry.
- Independent entries (delete A, delete B on different reservations — the common
  case) need zero extra logic.

What we deliberately do NOT build: *guaranteed* multi-step undo (transactional
consistency across the chain) — that is the expensive part, and nobody needs it
here. A best-effort flat stack is close to the persistent-single-slot cost.

Shape: `past[]` / `future[]` of `SpaCommand`, cap ~5; header ↶/↷ buttons next to
"Neu" (bound to canUndo/canRedo, tooltip = the command label), surviving
navigation within the session (in-memory — a page reload clears them; localStorage
persistence is a later increment). The toast stays as the immediate "gelöscht —
Rückgängig" feedback; its Rückgängig action is just `undo()` on the top entry.
Redo re-executes the forward command (best-effort, same drop-on-stale). Own
actions only. Depth 5 is a soft cap, not a market-derived law — Google/Trello
ship shallower, but the maintainer's paging scenario justifies more than one.

**D3 — context menu design (2026-07-07, maintainer discussion).** Swing-analog
row/block context menu, adapted to the web:

- *Triggers:* ONE `mat-menu` fed by three paths — a ⋮ button in the row
  (discoverability anchor, visible on hover/active row per the 091
  active-section principle), the `contextmenu` event (right-click, power-user
  path — Swing habit), and long-press on touch. Right-click-only was rejected
  (undiscoverable in browsers, absent on touch); ⋮-only was rejected (breaks
  the Swing muscle memory this menu is the analog of).
- *Extension point from day one:* `MenuItemProvider` multi-provider
  (`(context: RowContext) => MenuItem[]`), the ObjectMenuFactory analog —
  [PRD 093](093-loan-lifecycle.md)'s loan transitions and the UC-E9 request confirm/deny arrive this
  way; a plugin system is NOT built, it's just an injected list. The menu
  context carries a list of rows (multi-select-ready) even though v1 always
  passes one — Swing's edit_multi/delete_selection stay possible without a
  break. Rows are TYPED via D4 — providers dispatch on the row subject's
  kind, exactly like Swing's per-type ObjectMenuFactory checks.
- *First actions:* **Bearbeiten** + **Anzeigen** (read-only sheet; keeps the
  menu non-empty for `canRead`-only users), before any command infrastructure
  exists. **Löschen** joins with the Phase 2 command layer.
- *Delete scope:* v1 deletes the whole event; the confirm dialog names the
  blast radius for series/multi-appointment events. The Swing scope choice
  ("nur dieser Termin" = exception write, "Serie") is deferred until 091
  Phase 4 makes exceptions editable in the sheet — building the exception
  write path menu-first would bypass the sheet.
- *Not ported:* copy/cut/paste (clipboard semantics need paste *targets* — the
  calendar surface, [PRD 077](077-calendar-model-graphql.md); Duplizieren covers the common case), the
  empty-slot menu (calendar-surface concern, [PRD 077](077-calendar-model-graphql.md)), multi-select actions
  (no table selection exists yet — PRD 099 supplies the selection model and
  the multi-row command producers).

**D5 — calendar move/resize scope logic lives in a server `moveAppointment`
GraphQL mutation, not in the client (2026-07-09, maintainer directive: "the actual
logic should happen in the mutation graphql").** The EVENT/SERIE/SINGLE cascade —
whole-reservation shift, whole-repeating-appointment shift, and the SINGLE
occurrence *split* (clone-as-non-repeating at the new time + `addException` on the
series, with the `isNotEmptyWithExceptions` empty-series cascade) — is exactly what
Swing's `AppointmentResize.change()` encapsulates and exactly the kind of
recurrence invariant that must not be reimplemented in TypeScript. The SPA
therefore does **not** build `updateReservation` payloads for SERIE/SINGLE (which
would force the SPA event model to carry multi-appointment sets + exceptions +
per-appointment restrictions and duplicate the cascade). Instead a new
`moveAppointment` mutation — **designed in [PRD 056](056-graphql-events-write-api.md)** (the reservation write-surface
PRD), § "Verb-level semantic notes" — keeps the cascade in Java as the single
source of truth, the same principle `moveReservations`/`deleteAppointment`/the
Phase 2 delete-scope path already follow. The client owns only the **scope dialog**
(present the chooser with Swing's show-when predicates) and the **command wrapper**
(forward + compensating inverse through the Phase 2 undo infra). *Alternatives rejected:* client-side
`updateReservation` rebuild (duplicates recurrence logic, needs the full [PRD 091](091-spa-reservation-edit-and-availability.md)
recurrence model on the read side just to move a block — the maintainer explicitly
ruled this out); silently widening `moveReservations` to non-EVENT scopes (loses
the scope choice — a repeating drag would ambiguously move either one occurrence or
all).

**D4 — typed row subject via hidden well-known aliases (2026-07-07).** View rows
are generic (`Record<string, unknown>` from arbitrary stored views) — a row can
be an appointment block, a resource, a user, or a menu-less aggregate row. The
Swing analog is `SelectionMenuContext` carrying a typed `RaplaObject` with
per-type `ObjectMenuFactory` dispatch. SPA model:

- *Declaration convention:* a view that wants row actions selects a hidden
  subject field under a well-known alias — `reservation @hidden { id canModify }`
  (already in the builtin `rapla_appointments`), `allocatable @hidden { id
  canModify }`, `user @hidden { id }`. No new server feature — `@hidden` +
  column meta exist; this is view authorship. A row without a subject field
  simply has no menu (aggregate/stat rows stay menu-free by construction —
  fail-quiet, never mistyped). Id-prefix inference was rejected (implicit,
  fragile); a server-side `@rowSubject` directive is deferred until custom-view
  authors actually stumble over the alias convention.
- *Client model (pure TS, tier-5):* `extractRowContext(row, viewName)` →
  `RowContext { primary: EntityRef | null, entities: EntityRef[], rows,
  viewName }` with `EntityRef { kind: 'reservation'|'allocatable'|'user', id,
  canModify? }`. `primary` = first subject in priority order reservation →
  allocatable → user. `entities` additionally collects secondary refs the row
  already carries (the builtin view's `persons`/`resources` cells select `id`) —
  extracted from day one, consumed later (per-allocatable request confirm/deny,
  "open resource" submenu).
- *Consequence for providers:* no subject or no items from any provider → no ⋮,
  no menu on that row (never an empty menu). The Phase 1 Bearbeiten/Anzeigen
  provider serves `kind === 'reservation'` only; resource/user providers arrive
  with their surfaces.
- *Builtin views:* `rapla_reservations` gets the same hidden subject
  (`id`/`canModify`) — today its rows carry no id at all.
