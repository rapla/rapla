# PRD 123 — SPA: one resource picker for the left rail and the event sheet

**Status:** implemented — 2026-09-30, committed 2026-10-01 (8a72d81cd + review residue b6b7ccc12); Phase C ("alle wählen" + Benutzer chip + owner OR, D8–D10) built 2026-10-01 (rapla-impl, uncommitted; live `ownerIn` probe + tier 7 open). Option "B mit A" chosen by the user on 2026-09-30 after a click-through analysis; Phases A and B built the same day (rapla-impl2), live-checked against the dev server; tier-7 e2e still open.
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

**D3 — Chip wall folds.** Alle / ★ Favoriten / Zuletzt stay as chips; the type chips become one "Typ ▾" select (mat-select, A–Z, count per type). Rail width 290 px (matches the sidenav in `app.css`), names still ellipsised. **Amended 2026-10-01 (user):** the select groups its options like the create dialog (PRD 122 D9) — `optgroup` "Ressourcen" first, then "Personen", A–Z with counts inside; flat when only one kind exists. The active chip is no longer bold (a 3 px width change wrapped the row at 290 px).

**D4 — Keyboard path.** In the search field: Enter shows the first picker row (rail) or toggles it (sheet), ↓ moves focus into the list, Esc clears the query. The list keeps `TableSelection` keys.

**D5 — Picker state survives reload.** Active chip, type, query and active id persist per user via `ScopedStorage` (like `FilterStore`), key `rapla.picker`.

**D6 — No retried 404 per click.** The extra view query with empty variables is the PRD 097 D8 signature bootstrap (once per view load, not per click — the probe misread it); it stays. What goes: `GET /api/externaleventimport/metadata` answered 404 (no import plugin) was retried on every selection change; a 404 is now final for the session; any other failure is retried on the next call.

**D7 — Server ranking tie-break by name.** `ClassificationGraphQLController` sorts equal-rank hits by name (collator) instead of id. One line; still used by other `resources(filter: { searchText })` callers.

**D8 — "alle wählen" for the current list (user, 2026-10-01; Swing root-node parity, customer request).** Rail only — not in the sheet's assign mode. The set is every hit of the active chip + query (not just the rendered page); under a type chip the members of the filtered tree; under *Benutzer* the accounts. A button in the list header next to the hit count; the group button (PRD 119 D2) keeps the same mechanics (`selectGroup` carries `ctrl`). Click replaces the selection, Ctrl adds; when everything is already selected the click clears it ("Auswahl aufheben"). The ▶ marker resets, no recent is pushed, and the tree does NOT auto-expand the groups of the new selection (a Raum-type select-all would otherwise open hundreds of rows; prototype 2026-10-01). The chip rail folds ≥ 10 resource chips into one "N Ressourcen ×" chip; user and event chips stay single. The query still carries the explicit ids. Prototype: `docs/prototypes/select-all-picker.html` (untracked).

**D9 — *Benutzer* chip; PRD 119 D10 overruled (user, 2026-10-01).** Chips: Alle · ★ Favoriten · Zuletzt · *Benutzer* (types stay in the "Typ ▾" select, D3). *Benutzer* lists every readable account A–Z (self + `canAdminUser`, the `users` part of the lean query), the caller's own account first; rows follow PRD 119 D5 (click replaces, Ctrl toggles, Shift range) and become `user` chips. When only the own account is readable the chip is labelled *meine*; it activates like any chip (type select and other chips deselected, list = the own account, 1 hit) AND replaces the selection with the own account (Ctrl adds); a second click deselects the account, the chip stays active. *(Revised 2026-10-01 after the first build: the user rejected a filter-only "meine" that left the type select active.)* The pinned "meine" card goes; user hits under *Alle* while typing stay. Reason (recorded this time): one concept instead of a card plus hidden hits, accounts get a place like Swing's *Benutzer* root (`TreeFactoryImpl.newUsersNode`), and a one-account user keeps exactly today's "meine". Admin "meine" = two clicks (chip, own row) — accepted.

**D10 — Users OR resources, like Swing (user, 2026-10-01).** `ReservationFilter.ownerEq` is REPLACED by `ownerIn: [ID!]` (beta, no external API users — no alias). The server resolves the ids to readable users (unknown/hidden dropped silently, §12) and passes them as `owners` to `queryAppointmentsSync`, whose result is the UNION of the resources' and the owners' appointments (`LocalAbstractCachableOperator.queryAppointmentsSync`, Swing's path); the AND post-filter in `matches()` goes. The SPA binds every `user` chip into `ownerIn` (no first-chip workaround). **No user lanes:** owner-admitted blocks have empty `matchedBy` and fall back to their own room's lane (`week-lanes.ts`, `groupKeyRef`); a `matchedByOwner`/user lane is not built. Consequence accepted: the "owner X in building A" drill-down (AND) no longer exists; Swing has none either. `ResourceFilter.ownerEq` (owner of a resource) is a separate field and also becomes `ownerIn`, AND semantics unchanged. Shape (user, 2026-10-01, "Variante 1"): `ownerIn` stays a flat field next to `resourceIdsIn`/`resourceMatching` — the three are the union sources, stated in each field description ("UNIONed with …"); no union sub-input (`anyOf` was a working name; GraphQL has no spec-level OR — generated APIs like Prisma/Hasura use `or: [Filter!]` combinators, hand-written ones document a flat filter; rejected: would move `resourceIdsIn`/`resourceMatching` for the SPA binder, the import worklist, the stored views' `@param(into: "filter.resourceIdsIn")` and document deep links, for no second union source yet).

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

### Phase C — "alle wählen", Benutzer chip, owner OR (D8–D10; built 2026-10-01, uncommitted)
Order: server first (schema change), then SPA.
- [x] Server: `ownerEq` → `ownerIn` on `ReservationFilter` and `ResourceFilter` (`schema.graphqls`, both controllers' records + parsers, `docs/graphql.md`); reservations: resolve readable users → `owners` of `queryAppointmentsSync`, drop the `matches()` owner clause. Tier 3: a monty reservation in the fixture; cases resource only / owner only / both = union / hidden owner ignored / two owners.
- [x] SPA binding: all `user` chips → `ownerIn` (`variable-binder.ts`), remove the first-chip workaround (`view-host.component.ts`), specs.
- [x] *Benutzer* chip + *meine* degradation, own account first, "meine" card removed (D9); PRD 119 D10 marked overruled; tier 5/6 specs.
- [x] "alle wählen" header button + `selectGroup { items, ctrl }` + toggle + no auto-expand (D8); chip-rail fold ≥ 10; texts en + de; tier 6 specs.
- [ ] Tier 7: Benutzer chip → own events in the week; room + user → union.
- Live probe 2026-10-01 (8051, Siegen data, admin): resource only 1, owner only 10, resource + owner = union 10 (AND would be 1), hidden/unknown owner → `[]` without error, `ownerIn: []` = no filter, `ownerEq` rejected by validation. Pre-existing, decided 2026-10-01 (user, review S6): an unscoped `reservations` query (wire only — the SPA fires no query without chips, `view-host.component.ts`) resolves to "all readable resources" and therefore never returns reservations WITHOUT any resource; they are reachable through `ownerIn` only, as in Swing's user branch. Left as is; documented in `docs/graphql.md`.
- Review 2026-10-01 (rapla-review): PASS, 0 MUST, 6 SHOULD — S1 (direct `pick.emit` in `activateMine`), S2 (`usersChip` null until the list is loaded), S4 (`@Tag("e2e")` + javadoc on the three `@SpringBootTest` GraphQL input tests), S5 (`ResourceFilter.ownerIn` applies the same visibility rule as `ownerIn` on reservations, `readableUser`) folded in; S3 (reset the no-auto-expand flag on chip change) rejected by design — returning to the type chip would reopen the hundreds of rows D8 avoids, a single pick already resets it; S6 see above.
- Identity switch (user, 2026-10-01): impersonation switch/end now reloads the page (`auth.service.ts`), guarded by the unsaved-changes check — closes PRD 119 residue R-22 (stale lean list) and the sibling per-session caches (new-event options, import worklist, classification SDL) at once; the store additionally drops its lean list on an identity change.
- Reading applied (coordinator, 2026-10-01): under *Alle* with a query the appended user hits count as hits and are selected as `user` chips (`ownerIn`, OR); group rows matching by name only do not. Tests: `ReservationOwnerInGraphQLTest` (tier 3, 8 cases), `variable-binder.spec`, `resource-picker`/`resource-selection`/`chip-rail` specs (76 green, 7 red first).

## Tests

Tier 3 `ClassificationGraphQLControllerTest#searchTextEqualRankHitsSortByName` (D7); tier 5 `resource-selection-store.spec.ts` (D5), `import-worklist.service.spec.ts` (D6), `availability-search.service.spec.ts`; tier 6 `resource-picker.component.spec.ts`, `resource-selection.component.spec.ts` (D3, M2), `omnibox.component.spec.ts` (D4, M1), `event-sheet-assign.spec.ts` (D2); tier 7 in `rapla-angular/tests/` (open).

### Review residue (rapla-review pass 2, noted only)
- L3: the picker state (D5) writes localStorage on every keystroke of the query. **Won't fix (2026-10-01):** a debounce races the per-user rehydrate (`bindPerUser` re-reads `saved()` on identity change) — a user switch inside the debounce window would store the old user's query under the new user's key; ~60 bytes per keystroke is not worth that logic.
- L5: the sheet re-fetches availability from an effect on `picker.shown()`; fine at D12 sizes.
- L6: ~~the availability query carries the rendered ids without a cap~~ — fixed 2026-10-01: `AvailabilitySearchService.statuses` asks in blocks of `MAX_CANDIDATE_IDS` (200, the server cap in `AvailabilityGraphQLController`) and merges; spec "splits more than 200 ids into blocks of 200". Load volume itself stays PRD 119 OQ4 territory.

## Open Questions

- **OQ1** — In assign mode the top search field sits outside the dialog. *Resolution (2026-09-30, implementation):* the sheet's picker keeps its own chip and query signals (`pickerChip`, `pickerQuery`) with its own input; sharing the rail's query would narrow the rail behind the dialog.
- **OQ2** — Should assigning from the sheet push a recent (PRD 089)? *Resolution (2026-09-30, default applied):* yes, `store.pushRecent` on assign.
