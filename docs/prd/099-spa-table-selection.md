# PRD 099 — SPA table selection & multi-select actions

**Status:** done — all 4 phases landed 2026-07-08 (live probe of the bulk-delete
Goal §4 scenario still outstanding; OQ3–OQ5 remain open as later increments)
**Related:** PRD 094 (row actions + command/undo infra — D3 explicitly deferred
multi-select actions until "table selection exists"; this PRD supplies it), PRD 091
(event sheet — the edit target rows open into), PRD 077 (calendar surfaces — block
multi-select on calendar grids is NOT here, but must be able to reuse the selection
model), PRD 078 (view host — the mat-table this lands on)

## Abstract

Give the SPA table views Swing/Excel-like row selection: plain click selects,
Ctrl/⌘-click toggles, Shift-click range-selects, arrow keys navigate with
Shift-extension — and feed the selection into the PRD 094 row-action system so
multi-row commands (bulk Löschen first) work from the context menu. Swing gets all
of this free from `JTable` (`MULTIPLE_INTERVAL_SELECTION` default) +
`SelectionMenuContext`; the web gives none of it, so the SPA needs an explicit
selection model. The `RowContext.rows` array has been multi-select-ready since
PRD 094 D3/D4 ("v1 always carries exactly one row") — this PRD makes it carry N.

## Implementation

Behaviour reference (keyboard map, per-surface semantics, extension recipe):
[`docs/architecture/spa-selection-and-actions.md`](../architecture/spa-selection-and-actions.md).

| Piece | Where | Pattern |
|---|---|---|
| Selection engine | `views/table-selection.ts` (`TableSelection<K>`) | headless pure TS over a caller-fed RENDERED row order; signals for `selected`/`active`/`selectionMode`; `syncSelected()` for externally-owned state |
| Table wiring | `views/view-host.component.ts` | row-object keys; rendered order mirrored from `dataSource.connect()` (sort-aware, group headers dropped); active-descendant a11y; selection self-clears on re-query (new row objects) |
| Multi-row context | `views/row-context.ts` `extractSelectionContext` | `subjects` = per-row primaries; `primary` only at size 1 (D3) |
| Bulk delete menu | `views/row-menu.ts` `multiItems`/`bulkDeleteFlow` | dedupe by event id, subset-wins label (OQ1), one-option `DeleteScopeDialog` confirm |
| Bulk command | `actions/event-commands.ts` `buildBulkDeleteCommand` | one `deleteReservations([ids])` ⇄ best-effort id-stable re-creates; aggregate → `invalid` issues on any failure (OQ2) |
| Undo infra change | `actions/undo-toast.service.ts` | `undo()` fires `mutated$` even on a failed inverse (partial application must re-query) |
| Rail retrofit | `shell/resource-selection.component.ts` | engine computes, `FilterStore` chips stay source of truth (`syncSelected` before every interaction, `FilterStore.setAll` mirror); exclusive vs modifier gesture rule (D4) |

Specs: `table-selection.spec.ts` (32), `view-host-selection.spec.ts` (12),
`event-commands.spec.ts` (4), `undo-toast.service.spec.ts` (+1),
`resource-selection.component.spec.ts` (9 new).

## Swing reference (analysed 2026-07-08)

Primary reference per repo convention — interaction semantics to mirror:

- **Table** (`SwingTableView.java:214-216`): plain `JTable`, no explicit
  `setSelectionMode` → default `MULTIPLE_INTERVAL_SELECTION` (discontiguous
  multi-row via Ctrl/Shift). Selection read-back maps through the sorter
  (`sorter.modelIndex`, lines 404-413) — selection is over the *rendered* order.
- **Context building** (`createMenuContext`, lines 390-402): `SelectionMenuContext`
  carries `selectedObjects` = all selected rows; `focused` is set **only when
  exactly one** row is selected.
- **Multi-select actions** (`MenuFactoryImpl.addObjectMenu`, 488-614):
  `DELETE_SELECTION` (bulk delete → one `DeleteUndo`), `EDIT_SELECTION` (bulk edit
  dialog, type-homogeneous via `getObjectsWithSameType`, only
  Allocatable/User/Reservation per `isMultiEditSupported`), multi-block delete
  (`deleteBlocks`), multi-reservation copy. **Mixed-permission behaviour:** the
  menu-build layer pre-filters to the `canDelete`/`canModify` subset and offers
  the action on that subset (silently excluding the rest); the policy layer
  (`RaplaObjectActionPolicy:107-115`) alone would be all-or-nothing — the shipped
  Swing UX is *subset wins*.
- **Keyboard:** JTable defaults (arrows, Shift+arrows, Ctrl+A) + custom Ctrl+C/
  Ctrl+X only. No Delete key, no Enter-to-edit; double-click edits at size 1 only.
- **Resource tree** (`ResourceSelectionViewSwing:163`):
  `DISCONTIGUOUS_TREE_SELECTION`, multi-selection flows into the same
  `addObjectMenu` → bulk actions on resources from the left rail.
- **NOT this PRD:** `SelectionHandler` (calendarview) selects contiguous *time
  ranges* — the SPA analog is the month-grid drag-create (PRD 095, shipped).

## SPA state today

- `views/view-host.component.ts` mat-table: **no selection at all** — rows bind
  only `(dblclick)` → edit and `(contextmenu)` → menu.
- `shell/resource-selection.component.ts` `step()` (lines 325-341): proven
  plain/Ctrl/Shift modifier-click logic with `anchorIndex` — but mouse-only (no
  keyboard) and it selects *filter scope chips*, not action subjects.
- `views/row-context.ts:37`: `RowContext.rows` declared multi-select-ready.
- No CDK `SelectionModel`/`ListKeyManager` anywhere in the SPA.

## Goal

1. A reusable, tier-5-tested selection model (`TableSelection`, pure TS):
   plain/Ctrl/Shift pointer semantics + keyboard navigation (↑/↓ move active row,
   Shift+↑/↓ extend, Home/End, Ctrl+A, Escape clears), anchor/active tracking
   against the rendered row order.
2. The view-host mat-table is selectable: visible `.selected` highlight, roving
   tabindex + `aria-selected`, selection feeds the ⋮/right-click menu — the
   `RowContext` carries all selected rows, `primary` only at size 1 (Swing's
   `focused` rule).
3. Multi-row **Löschen** from the context menu runs as ONE undoable entry in the
   PRD 094 command history.
4. Measurable: select 3 events via Shift-click, right-click → "Löschen (3)",
   confirm → all 3 gone, ONE toast, one ↶ click restores all 3 with identical
   ids; a keyboard-only user can reach and extend the same selection with
   arrows + Shift.

## Scope

### In scope
- `TableSelection` helper (pure TS, tier-5): pointer + keyboard semantics,
  anchor/active/range state, operating on the rendered (sorted/grouped) row list
- Touch-ready semantics in the MODEL from day one (maintainer constraint
  2026-07-08: a smartphone version is on the roadmap): an explicit
  *selection mode* — entered via long-press, in which plain tap = toggle
  (Gmail/Google Photos pattern, since touch has no Ctrl/Shift) — as model
  state; the long-press gesture wiring itself may land with the mobile surface
- View-host mat-table wiring: click/keydown handlers, highlight, a11y
  (`aria-multiselectable`, `aria-selected`, roving tabindex), selection reset on
  view change / re-query
- Context menu + ⋮ act on the whole selection: `RowContext.rows` = N rows,
  provider items may aggregate (label "Löschen (N)")
- Bulk Löschen as one composite `SpaCommand` (delete N ⇄ restore N)
- Retrofit `ResourceSelectionComponent` onto the shared helper (gains keyboard
  nav; behaviour parity with today's `step()`)

### Out of scope
- Cell-level selection (Excel cells) — row-granular only, per maintainer choice
  2026-07-08
- Bulk EDIT dialog (Swing `EDIT_SELECTION`) — no SPA bulk-edit surface exists;
  revisit when a consumer asks
- Calendar-surface block multi-select (month grid chips, week grid) — PRD 077
  territory; must reuse `TableSelection` when it arrives
- Copy/cut/paste of selected rows as *entities* (paste targets are a calendar
  concern, PRD 077); Ctrl+C-as-text is OQ4
- Drag-select (rubber-band) over table rows — Shift-click covers range selection
- Swing changes

## Plan

### Phase 1 — `TableSelection` model (pure TS) — DONE 2026-07-08
- [x] `views/table-selection.ts`: state = `selected: Set<key>`, `anchor`,
      `active` (indices against a caller-supplied rendered row list with stable
      row keys). API: `pointer(index, {shift, ctrl})` (plain=replace,
      ctrl=toggle+anchor, shift=range-from-anchor — semantics lifted from
      `ResourceSelectionComponent.step()`), `key(event)` (↑/↓ set active+select,
      Shift+↑/↓ extend from anchor, Home/End, Ctrl+A, Escape clear),
      `clear()`, signals for the component to consume.
- [x] Group-header rows never enter the key list (the caller feeds data rows only).
- [x] Selection-mode state (touch) modeled + tested (`enterSelectionMode`,
      tap-toggle, last-deselect/Escape exit).
- [x] 30 tier-5 specs (`table-selection.spec.ts`): full modifier/keyboard matrix,
      range over a re-sorted list, direction flip, Ctrl+A, Escape, setRows
      reconciliation.

### Phase 2 — view-host wiring — DONE 2026-07-08
- [x] mat-row: `(click)` → `pointer(...)`, `[class.selected]` +
      `.active-row` marker, table `tabindex="0"` + `(keydown)` → `key(...)`,
      `aria-multiselectable` / `aria-selected` / `aria-activedescendant`
      (active-descendant pattern instead of per-row roving tabindex),
      shift-click text selection suppressed via `mousedown.preventDefault`.
      Row key = the row OBJECT (rendered order fed from
      `dataSource.connect()` so ranges follow the current sort; a re-query
      mints new objects → the selection clears itself; group headers filtered
      out before `setRows`).
- [x] Right-click on a selected row keeps the selection; on an unselected row
      replaces it (Explorer convention, `selectionItems()`); the ⋮ button
      absorbs its row the same way. Dblclick edits only at size 1.
- [x] `extractSelectionContext` in `row-context.ts`: `rows` = all selected
      rows, new `subjects` field (per-row primaries), `primary` only at size 1.
- [x] 8 tier-6 specs (`view-host-selection.spec.ts`) + `scrollIntoView` guarded
      for jsdom.

### Phase 3 — multi-row Löschen (first bulk command) — DONE 2026-07-08
- [x] `EventRowMenuProvider.multiItems`: N reservation-subject rows →
      "Löschen (N)" / "Löschen (k von N)" (OQ1 subset wins); blocks of the same
      event DEDUPE to one delete; whole-event only (per-block scope dialog stays
      single-row). Confirm via `DeleteScopeDialogComponent`'s one-option degrade
      ("N Veranstaltungen wirklich löschen?").
- [x] `buildBulkDeleteCommand` (`actions/event-commands.ts`): ONE `SpaCommand` —
      execute = one `deleteReservations([ids])`; inverse = best-effort re-create
      of every captured state with identical ids (per-event `catchError`,
      aggregate → `invalid` with "k von N wiederhergestellt" + per-failure
      messages on any failure → 094 drop-on-stale drops the entry).
- [x] `UndoToastService.undo()` now fires `mutated$` on a FAILED inverse too —
      a bulk inverse may have partially applied; the view must re-query (OQ2).
- [x] 4 tier-5 specs (`event-commands.spec.ts`) + 4 tier-6 multi-menu specs +
      1 undo-toast spec. Live probe still open (Tests section).

### Phase 4 — resource rail retrofit — DONE 2026-07-08
- [x] Unified on **Swing tree semantics** (D4): the old rail deviated
      (shift ADDED the range to the filter; ctrl added without toggle) —
      Swing's `DISCONTIGUOUS_TREE_SELECTION` is plain=replace / ctrl=TOGGLE /
      shift=range-REPLACE, and the old SPA behaviour was prototype-only, so it
      was changed, not preserved.
- [x] `step()` delegates to a `TableSelection<string>` (item ids over
      `visible()`); keyboard nav on the rail (`tabindex` + keydown on
      `.stepper`, arrows STEP — the arrow IS the step rhythm — Shift+arrows
      extend, Ctrl+A, Escape clears; search input/buttons keep their keys).
- [x] Drift-proofing: `FilterStore` chips stay the source of truth — new
      `TableSelection.syncSelected()` re-syncs the model from the chips before
      every interaction (an externally removed chip never resurrects);
      `FilterStore.setAll()` mirrors the result back. Exclusive gestures
      (plain click / bare arrow) replace the WHOLE filter (step rhythm);
      modifier gestures keep chips not belonging to the visible list.
- [x] Selected items highlighted in the rail (`.item.selected`,
      chip-membership-driven).
- [x] 2 tier-5 sync specs + 9 tier-6 rail specs.

## Tests

- Tier 5: `table-selection.spec.ts` — full modifier/keyboard matrix
- Tier 6: view-host selection rendering + menu context (extends
  `view-host-menu.spec.ts`), resource-rail parity
- Live probe: the Goal §4 scenario (bulk delete + single undo, keyboard-only
  selection)

## Open Questions

- **OQ1** — Mixed-permission selection. *Resolution (2026-07-08, maintainer):*
  **subset wins** (Swing menu-layer parity) — "Löschen (k von N)" on the
  deletable subset; the label names the partial scope.
- **OQ2** — Composite-undo partial failure. *Resolution (2026-07-08,
  maintainer):* **best-effort + loud report** — restore everything restorable,
  aggregate failures into a loud `invalid` toast, drop the history entry (094
  D2 drop-on-stale); `mutated$` fires even on a failed inverse so the view
  re-queries the partial reality.
- **OQ3** — Selection survival across re-query: v1 clears; preserving by subject
  id after `mutated$` refetch would keep context after an action on *other*
  rows. Later increment? *Resolution:* pending.
- **OQ4** — Ctrl+C copies selected rows as text/TSV (the Excel expectation;
  Swing binds Ctrl+C to entity-copy instead)? Cheap and useful, but new scope.
  *Resolution:* pending.
- **OQ5** — Does selection span group sections in week mode (Shift-range across
  day blocks)? Leaning yes — sections are visual grouping, not selection
  boundaries. *Resolution:* pending.

## Decisions locked

**D1 — row-granular selection, not cell-level (2026-07-08, maintainer choice).**
Excel-like *cell* selection was explicitly not requested; the row is the action
subject (RowContext). Behaviours in scope: Shift-range, Ctrl/⌘-toggle, keyboard
navigation.

**D2 — hand-rolled headless `TableSelection` (pure TS); no table/grid library
(2026-07-08, reaffirmed under the mobile constraint).** Survey result:

- *Angular Material has NO built-in table selection.* The documented pattern is
  CDK `SelectionModel` (a bare `Set` wrapper) + hand-written click handlers /
  checkbox column — no shift-range, no keyboard grid navigation, no touch mode.
  `@angular/cdk-experimental` selection directives add checkbox-toggle +
  select-all only. PRD 032 already flagged this ("CDK Table is a toolkit, not a
  finished grid") and locked **no second general component library** — PrimeNG
  (`p-table selectionMode="multiple"` with ctrl/shift built in) and AG Grid
  Community (row multi-select + keyboard) do ship selection, but adopting either
  for this feature means a second design system, second theming/a11y surface,
  and losing the existing mat-table integration (MatSort accessor, grouped
  `when`-predicate rows, declarative view meta).
- *The mobile constraint strengthens headless:* on phones the wide table will
  likely re-render as a card/list layout — a desktop grid library's selection
  dies with the table markup, while a headless model (Set + anchor + mode) works
  unchanged under any row rendering, and touch multi-select (long-press →
  selection mode → tap-toggle) is a semantics question, not a widget question.
- CDK's `ListKeyManager` needs `FocusableOption` plumbing that fits neither
  `mat-row` nor the custom rail list; the plain/Ctrl/Shift logic already exists
  proven in `ResourceSelectionComponent.step()`. Angular's new `@angular/aria`
  grid/listbox patterns (developer preview) are worth a re-look once stable —
  as an internal upgrade of the same headless model, not a reason to wait.

A pure-TS model is tier-5-testable and shared across mat-table, the rail list,
future calendar surfaces (PRD 077), and the future mobile layout.

**D4 — the rail unifies on Swing tree selection semantics; FilterStore stays
the source of truth (2026-07-08, maintainer).** The old rail's shift-click
*accumulated* the range into the filter — a deviation from Swing
(`DISCONTIGUOUS_TREE_SELECTION`: shift = range-replace, ctrl = toggle) that
existed only because the SPA rail was a prototype. Unify, don't preserve:
same `TableSelection` engine as the table, mirrored into `FilterStore`
(`setAll`), model re-synced from the chips before every interaction
(`syncSelected`) so toolbar chip removal can't drift the anchor state.
Alternatives rejected: keyboard-nav-only (keeps two divergent selection
grammars alive); skip (leaves the rail keyboard-dead).

**D3 — Swing `focused` rule for the row context (2026-07-08).** `RowContext.primary`
is set only when exactly one row is selected (multi-select ⇒ `primary = null`,
providers dispatch on the homogeneous kind of `rows`) — mirrors
`SelectionMenuContext` (`focused` only at size 1) so single-row items (Bearbeiten,
Anzeigen, scope-dialog Löschen) naturally disappear on multi-select instead of
acting on an arbitrary row.
