# PRD 123 — SPA: one resource picker for the left rail and the event sheet

**Status:** implemented — 2026-09-30, uncommitted. Option "B mit A" chosen by the user on 2026-09-30 after a click-through analysis; Phases A and B built the same day (rapla-impl2), live-checked against the dev server; tier-7 e2e still open.
**Related:** [PRD 119](119-spa-one-search-resource-picker.md) (picker with chips, tree, lean list — D7 is overturned here), [PRD 091](091-spa-reservation-edit-and-availability.md) (event sheet add mode, `resourceAvailability`), [PRD 099](done/099-spa-table-selection.md) (selection gestures), [PRD 089](089-server-side-recents-favorites.md) (recents/favorites), [PRD 106](106-query-request-lifecycle.md) (query throttle), [PRD 122](122-spa-new-resource.md) (+ Neu in the picker — same files, lands first)

## Abstract

Finding a resource is fast in the left rail (one lean list, browser filtering) but the event sheet's "Auswählbar" list is a second, weaker path: server search capped at 50, no ranking by name, one click per resource, pins as a third concept and a "Fertig" that only closes. This PRD makes the rail's picker the one picker — the sheet renders it with availability pills and assign-toggle rows — and fixes the rail's own friction on the way (chip wall, keyboard path, lost state on reload, duplicate queries per click). End state: a user assigns three resources to an event from favorites without typing, and reaches the calendar of a searched resource with the keyboard alone.

## Findings (click-through 2026-09-30, customer data set, admin)

| # | Friction | Evidence |
|---|---|---|
| F1 | 17 type chips take ~300 px of the 250 px rail; names truncate | screenshot, `.stepper { width: 250px }` |
| F2 | Enter in the search field does nothing; 4 Tabs from search to the list; arrows only with focus on the list container | probe, `onListKeydown` |
| F3 | First row click = 7 requests: the PRD 097 D8 `{}` bootstrap query + re-query (by design, once per view load), the worklist twice (window seeded by the first result), plus `GET /api/externaleventimport/metadata` → 404 on every selection change | probe; `import-worklist.service.ts:182` |
| F4 | Reload keeps the filter chips (`FilterStore`, localStorage) but loses active chip, query and "▶ gezeigt" | probe; `ResourceSelectionStore` holds them in plain signals |
| F5 | Multi-select (Ctrl/Shift/"alle wählen") is undiscoverable — no checkbox affordance | template |
| F6 | Sheet add mode: empty search shows nothing; ≤ 50 hits ranked by match kind then **by id** ("k" → Kabel A, Kabel B, an item without k…); "→ Zuordnen" per row; "★ heftet oben an" pins; "Fertig" bottom-right only closes | `ClassificationGraphQLController.java:149-161`, `event-sheet.component.ts` add mode, user screenshots |

Not a problem: the lean list loads in one query (43 KB, 78 ms), typing sends no resource query.

## Decisions locked (user, 2026-09-30)

**D1 — One picker component, two hosts.** `ResourceSelectionComponent`'s list (chips, Alle/Favoriten/Zuletzt/type tree, browser filter, D12 caps) is extracted into a host-agnostic picker; the rail and the event sheet both render it. PRD 119 **D7 is overturned**: the sheet's own search field, `AvailabilitySearchService.search` by `searchText`, and the pins go. Alternatives rejected: polish only (A alone — leaves the sheet's second path), search-as-command-palette (C — contradicts PRD 119 D3/D4, not needed yet).

**D2 — Row semantics per host (user, 2026-09-30).** Rail: PRD 119 D5 unchanged (click = show, Ctrl = toggle, Shift = range). Sheet: a plain click assigns the resource at once and closes the list; Ctrl/⌘-click assigns and keeps the list open for more; an already-assigned row is shown checked and a click on it unassigns. The "+ Ressource…" button stays in place and toggles the list open and closed (label flips to "▴ einklappen"); a plain assign collapses it — no "Fertig", no separate close button (user, 2026-09-30). Availability pills come from `resourceAvailability` by `ids` for the rendered rows only (the D12 block), re-fetched when the draft's appointments change (PRD 091 pattern kept).

**D3 — Chip wall folds.** Alle / ★ Favoriten / Zuletzt stay as chips; the type chips become one "Typ ▾" select (mat-select, A–Z, count per type). Rail width 290 px (matches the sidenav in `app.css`), names still ellipsised.

**D4 — Keyboard path.** In the search field: Enter shows the first picker row (rail) or toggles it (sheet), ↓ moves focus into the list, Esc clears the query. The list keeps `TableSelection` keys.

**D5 — Picker state survives reload.** Active chip, type, query and active id persist per user via `ScopedStorage` (like `FilterStore`), key `rapla.picker`.

**D6 — No retried 404 per click.** The extra view query with empty variables is the PRD 097 D8 signature bootstrap (once per view load, not per click — the probe misread it); it stays. What goes: `GET /api/externaleventimport/metadata` answered 404 (no import plugin) was retried on every selection change; a 404 is now final for the session; any other failure is retried on the next call.

**D7 — Server ranking tie-break by name.** `ClassificationGraphQLController` sorts equal-rank hits by name (collator) instead of id. One line; still used by other `resources(filter: { searchText })` callers.

## Implementation

- `shell/resource-selection.component.ts` → split: `resource/resource-picker.component.ts` (list, chips, type select, tree, D12 caps, ★, assign-mode arrow keys) with inputs `mode`, `chip`/`query` (two-way), `checked`, `activeId`, `availability`, outputs `pick` (item + ctrl/shift — the host decides), `selectGroup`, `menu`; public `shown()`. The rail keeps "meine", DURCHSTEPPEN header, + Neu, the `TableSelection` engine and the ⋮ mat-menu (opened at the row's button via a fixed anchor, the view-host pattern).
- `state/resource-selection-store.ts`: persistence (D5); unchanged list/ranking functions.
- `event/event-sheet.component.ts`: `addMode` renders `<app-resource-picker mode="assign">` with its own input; `candidates`/`pins`/`searchText`/`togglePin`/`hits` removed; `onPick` assigns/unassigns (D2), `toggleAddMode` on the button. `AvailabilitySearchService.statuses` keeps only the `ids` branch; an effect re-fetches when the picker's `shown()` rows change.
- `shell/omnibox.component.ts`: Enter/↓/Esc (D4) via the store (`pickerFocus`, `activateFirst`).
- `import/import-worklist.service.ts`: D6.
- `ClassificationGraphQLController`: D7.

### Behaviour inventory for removed code (AGENTS.md §11)

| Today | Afterwards |
|---|---|
| Sheet search field | the picker's shared query (top field is outside the dialog → the picker in assign mode shows its own input bound to the same store query) |
| "→ Zuordnen" | click assigns and closes, Ctrl-click assigns and stays (D2) |
| "★ heftet oben an" pins | ★ favorites, already in the list |
| "Fertig" bottom right | the "+ Ressource…" button toggles open/closed in place (D2) |
| Server hits ≤ 50 | full lean list, D12 caps |

## Goal

- Sheet: three favorites assigned with two Ctrl-clicks and one click, no typing, list closed afterwards (tier-6).
- Rail: search "sony", Enter → calendar shows the first hit; no resource query on the wire (tier-6/7).
- Reload keeps chip, query and active row (tier-5 store spec).
- A 404 on the import metadata endpoint is asked once per session (tier-5 `import-worklist.service.spec.ts`).
- `resources(filter: { searchText: "k" })` returns equal-rank hits A–Z (tier-3).

## Scope

### In scope
D1–D7, the behaviour inventory, tests below.

### Out of scope
- Command-palette search (option C).
- Virtual scrolling; per-type loading for large deployments (PRD 119 OQ4).
- The "gilt für" per-allocation picker and the reservation's assigned-rows list (unchanged).

## Plan

### Phase A — Rail polish (after PRD 122 is committed)
- [x] D7 name tie-break + tier-3 test (`searchTextEqualRankHitsSortByName`).
- [x] D6 metadata 404 once per session; tier-5 spec.
- [x] D5 persistence (`rapla.picker` via `ScopedStorage`); tier-5 spec.
- [x] D3 type select + 290 px; tier-6 specs (rail + picker).
- [x] D4 Enter/↓/Esc in the omnibox; Enter is a store request (`requestActivateFirst(ctrl)`) the rail serves with its own `step` on `picker.shown()[0]` — one row semantics (review M2); the field starts from the restored query (review M1); tier-6 specs in omnibox + rail.

### Phase B — Picker extraction and sheet integration
- [x] `resource/resource-picker.component.ts` (+spec); the rail delegates, all 41 rail specs green; `store.listFor(chip)` serves a second host.
- [x] Sheet add mode renders the picker in assign mode; `AvailabilitySearchService.statuses(appointments, ids, ignore)` for assigned + rendered rows; pins/search hits/Fertig removed per inventory.
- [x] Tier-6 `event-sheet-assign.spec.ts`: click assigns + collapses, Ctrl-click assigns + stays, click on an assigned row unassigns, the toggle button; availability by ids with pills.
- [ ] Tier-7: pick a room via chip + Enter → week shows its events; assign from the sheet → saved reservation has the allocation.

## Tests

Tier 3 `ClassificationGraphQLControllerTest#searchTextEqualRankHitsSortByName` (D7); tier 5 `resource-selection-store.spec.ts` (D5), `import-worklist.service.spec.ts` (D6), `availability-search.service.spec.ts`; tier 6 `resource-picker.component.spec.ts`, `resource-selection.component.spec.ts` (D3, M2), `omnibox.component.spec.ts` (D4, M1), `event-sheet-assign.spec.ts` (D2); tier 7 in `rapla-angular/tests/` (open).

### Review residue (rapla-review pass 2, noted only)
- L3: the picker state (D5) writes localStorage on every keystroke of the query.
- L5: the sheet re-fetches availability from an effect on `picker.shown()`; fine at D12 sizes.
- L6: the availability query carries the rendered ids without a cap (PRD 119 OQ4 territory).

## Open Questions

- **OQ1** — In assign mode the top search field sits outside the dialog. *Resolution (2026-09-30, implementation):* the sheet's picker keeps its own chip and query signals (`pickerChip`, `pickerQuery`) with its own input; sharing the rail's query would narrow the rail behind the dialog.
- **OQ2** — Should assigning from the sheet push a recent (PRD 089)? *Resolution (2026-09-30, default applied):* yes, `store.pushRecent` on assign.
