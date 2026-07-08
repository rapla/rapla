# SPA selection & multi-select actions

**Status:** reference (behaviour + architecture). **Scope:** how row/item selection
works in the Angular SPA — the generic table view (`ViewHostComponent`), the left
resource rail (`ResourceSelectionComponent`) — and how a multi-row selection feeds
the row-action/command system. Design PRDs: [099](../prd/099-spa-table-selection.md)
(selection + bulk actions), [094](../prd/094-spa-main-view-actions-and-popups.md)
(row menu + command/undo infra). Swing analogs: `SwingTableView` (JTable
`MULTIPLE_INTERVAL_SELECTION`), `RaplaTree`/`ResourceSelectionViewSwing`
(`DISCONTIGUOUS_TREE_SELECTION`), `MenuFactoryImpl.addObjectMenu`.

## The one selection model

Both surfaces run the same headless engine —
`rapla-angular/src/app/views/table-selection.ts` (`TableSelection<K>`, pure TS,
no Angular DOM dependency). It holds `selected: Set<K>`, an `anchor` (range
origin), an `active` key (keyboard cursor), and a `selectionMode` flag (touch),
over a caller-supplied **rendered row order** (`setRows`). Deliberately NOT a
library: Angular Material has no table selection (CDK `SelectionModel` is a bare
Set), and a headless model survives the planned mobile re-render of tables into
card lists where a grid library's selection would not (PRD 099 D2).

### Pointer semantics (Swing/Excel rules — both surfaces)

| Gesture | Effect |
|---|---|
| Plain click | selection = only this row; anchor here |
| Strg/⌘ click | toggle this row in/out; anchor here |
| Shift click | selection = the contiguous range anchor → here (**replaces**); anchor keeps |
| Strg+Shift click | **adds** the range to the selection |
| Right-click on a selected row | keeps the selection (menu targets all of it) |
| Right-click on an unselected row | replaces the selection with that row (Explorer convention) |
| Double-click | opens the editor — only when exactly one row is selected |

Shift-click never selects page text (`mousedown.preventDefault` on shift).

### Keyboard map (table has `tabindex`, rail list too)

| Key | Effect |
|---|---|
| ↑ / ↓ | move the active row and select only it (in the rail this IS the step rhythm) |
| Shift+↑ / Shift+↓ | extend the range from the anchor |
| Home / End | jump to first / last row (Shift extends) |
| Strg+A | select all rows |
| Escape | clear the selection (and exit touch selection mode) |

A11y: the table uses the **active-descendant pattern** — `aria-multiselectable`
+ `aria-activedescendant` on the `<table>`, `aria-selected` + generated `id`s on
rows — instead of per-row roving tabindex.

### Touch (mobile roadmap — modeled now, gestures later)

`enterSelectionMode()` switches plain taps to TOGGLE (Gmail/Google-Photos
pattern — touch has no Strg/Shift). Exiting: Escape, `clear()`, or deselecting
the last item. **Not reachable on a phone yet:** nothing calls
`enterSelectionMode()` today — the long-press gesture that will call it lands
with the mobile surface. The mode's semantics are already tier-5-tested, so the
mobile work is wiring, not design.

## Surface 1 — the generic table view (`ViewHostComponent`)

- Rows are keyed by **object identity**; the rendered order is mirrored from
  `MatTableDataSource.connect()`, so shift-ranges and arrow keys follow the
  **current sort**, and group-header rows (week mode) never enter the key list.
- A re-query (window/view/filter change, `mutated$` after a mutation) mints new
  row objects → the selection **clears itself**; there is no persistence across
  reloads (PRD 099 OQ3 tracks re-selection-by-id as a later increment).
- Selected rows get `.selected` (theme secondary-container background); the
  keyboard-active row carries a left `.active-row` bar.

### Selection → row actions

`extractSelectionContext(rows, …)` (`views/row-context.ts`) builds the
`RowContext` the menu providers dispatch on: `rows` = all selected rows,
`subjects` = each row's typed primary subject, and `primary` **only when
exactly one row is selected** (the Swing `focused` rule — single-row items like
Bearbeiten/Anzeigen/scope-Löschen disappear on multi-select instead of hitting
an arbitrary row).

**Bulk Löschen** (the first multi-row action, `EventRowMenuProvider.multiItems`):

- Selected blocks of the SAME event dedupe to one delete.
- Mixed permissions = **subset wins**: the menu offers „Löschen (k von N)" on
  the deletable subset (Swing menu-layer parity); nothing is offered when
  nothing is deletable.
- Confirm dialog names the blast radius („N Veranstaltungen wirklich löschen?"),
  then ONE command runs: forward = one `deleteReservations([ids])` mutation;
  the toast/header-↶ inverse re-creates every captured event **with identical
  ids**. The inverse is best-effort: a mid-list failure (concurrent edit) does
  not stop the remaining restores — failures are reported loudly
  („k von N wiederhergestellt; ‚X' nicht wiederhergestellt (inzwischen
  geändert)") and the history entry is dropped (PRD 094 D2 drop-on-stale).
  `UndoToastService.undo()` fires `mutated$` even on a failed inverse, because
  a bulk inverse may have partially applied and the view must show reality.

## Surface 2 — the resource rail (`ResourceSelectionComponent`)

The rail's "selection" **is the filter**: the `FilterStore` chips stay the
single source of truth. The `TableSelection` engine only computes each
interaction — it re-syncs from the chips **before** every pointer/key event
(`syncSelected`), so a chip removed in the toolbar never resurrects through a
stale anchor.

- The rail follows the same table above — this replaced the earlier prototype
  behaviour where Shift *accumulated* ranges and Strg always added (PRD 099 D4:
  unify on Swing's `DISCONTIGUOUS_TREE_SELECTION`, don't preserve the prototype).
- Mirror rule: **exclusive** gestures (plain click, bare arrow — the
  step-through-resources rhythm) replace the WHOLE filter; **modifier** gestures
  (Strg/Shift) replace only the chips belonging to the visible list and keep
  foreign chips (search-added event chips, other tabs).
- Items whose chip is active are highlighted `.selected`; the „▶ gezeigt"
  `.active` marker (last-stepped item) is unchanged and independent.
- Keys typed into the list-filter input or on the tab/★/⋮ buttons are never
  interpreted as list interaction — ALL keys, including Escape: an Escape
  pressed inside the search box does NOT clear the selection (the handler
  returns before the selection model sees the event).

## Where to extend

- New multi-row actions: add a branch in a `RowMenuProvider` dispatching on
  `ctx.subjects` (kind + `canModify`), build one `SpaCommand`, run it through
  `UndoToastService` — see `buildBulkDeleteCommand` (`actions/event-commands.ts`)
  for the composite-inverse pattern.
- Calendar surfaces (PRD 077) reuse `TableSelection` for block multi-select when
  they arrive; the month grid's drag-create day-range selection is a different
  concept (PRD 095) and stays separate.
