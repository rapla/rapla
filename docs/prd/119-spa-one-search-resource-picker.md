# PRD 119 — SPA: one search field, resource picker with type chips and group tree

**Status:** draft — 2026-09-15. Direction decided by the user on 2026-09-15 after two rounds of clickable prototypes; data access revised the same day (user: load one lean resource list, not the full resource as Swing does; change notification later). Phases 1–3 implemented and committed on master in `d0395b700` (2026-09-15, rapla-impl); Phase 3b (D12 display caps, D5 row-order fix, two missing tests) committed in `2b14f6e6d`; Phase 4 waits for a group source.
**Related:** [PRD 081](081-graphql-omnibox-multisearch.md) (omnibox multisearch, "find / step / filter" split), [PRD 089](089-server-side-recents-favorites.md) (server-side recents + favorites), [PRD 099](099-spa-table-selection.md) (`TableSelection` click/Ctrl/Shift semantics), [PRD 104](104-spa-template-picker.md) (the "Neu" picker: flat list + recents instead of a menu tree), [PRD 116](done/116-graphql-allocatable-to-resource-rename.md) (`Resource` umbrella, `ResourceKind { RESOURCE, PERSON }`), [PRD 077](077-calendar-model-graphql.md) (groups as `ClassificationFilter[]`), [PRD 028](028-angular-power-search.md) (`searchText` / `matchKind`)

## Abstract

The SPA's resource picker opens empty: the left list only shows recents, favorites or a loaded group, and finding anything else means typing at least three characters into the omnibox at the top. On top of that there are two search fields (omnibox and the list's own filter) that both find resources, while event hits in the omnibox lead nowhere. This PRD replaces both with **one search field** and a picker that always shows content: type chips over a list, the resource hierarchy as an expandable tree (one level per categorization value, e.g. building ▸ rooms or programme ▸ courses; deeper levels later, D11), and events in the search dropdown that jump to their week and open the event sheet.

## Current state (read 2026-09-15)

- `shell/resource-selection.component.ts` — the left list. Tabs *Zuletzt / Favoriten / Gruppe* over `ResourceSelectionStore.activeList()`, plus its own input "in der Liste filtern…" that filters that list locally. A new user sees "— leer".
- `shell/omnibox.component.ts` + `search/search.service.ts` — the top search. Calls `search(query, limit: 20)` only from `MIN_QUERY_LENGTH = 3`. Resource hits carry "Belegung" (replace filter) and "+" (add), user hits add an owner scope chip, group hits "in Liste laden". **Event hits render but do nothing**: `navigate()` and `edit()` are `TODO` stubs.
- Server `SearchGraphQLController` delivers RESOURCE, EVENT and USER buckets. EVENT is a name scan over reservations the caller may **edit**. OCCURRENCE and GROUP are declared but not produced.
- `event/availability-search.service.ts` — the event sheet's add mode returns no hits for an empty `searchText`.
- `ClassificationGraphQLController.resources` — `limit` cuts the result **in storage order**; sorting only happens when `searchText` is set. "The first 20 alphabetically" is therefore not available today.
- The GraphQL schema exposes no resource hierarchy. For background (this PRD does not mirror it, D2), the Swing client builds one in `TreeFactoryImpl.addClassifiables`:
  - one level per **categorization attribute** (`AttributeAnnotations.KEY_CATEGORIZATION = "true"`; a multi-valued attribute puts the resource under each value),
  - nesting via the type's **belongsTo** attribute (a room under its building resource),
  - child entries via the **packages** attribute.
- `views/row-menu.ts` opens `EventSheetComponent` with `EventSheetDialogData { id }`; `ViewStateStore.setWindow({ from, to })` moves the visible date window.

## Decisions (user, 2026-09-15)

**D1 — Picker = chips over one list (prototype variant C combined with A).** Chips: *Alle*, *★ Favoriten*, *Zuletzt*, one chip per resource type (persons are resources, PRD 116 — lecturers get their type chip like rooms do). No *Benutzer* chip — users appear as hits while typing (D10). With *Alle* and no query the list shows favorites, then recents, then all other resources A–Z — the first 20 at once, the rest behind "Weitere n anzeigen". The user may still switch to another variant later (tree-only or grouped list).

**D2 — Type chips show a simple group tree of its own (user ruling 2026-09-15, relayed by the coordinator: "Parität zum Swing-Baum overruled").**
- **Deliberate first cut:** the grouping will change again (user, 2026-09-15: "wir werden die Gruppierung eh nochmal ändern — ist jetzt die erste Stufe"). The API is shaped so that change needs no break (`groupPaths`, S1).
- **Rule:** under a type chip the list is an expandable tree with one level per categorization value (D11). Every node shows its member count and offers "alle wählen" (select all resources below it). Resources without a categorization value sit directly under the type.
- **Not in scope:** the Swing `TreeFactoryImpl` rules are deliberately **not** mirrored. There is no belongsTo nesting yet (later), no packages, and no parity test.
- **Who computes what:** the **server computes** the group values per resource (`groupPaths`, D8); the SPA only nests by that field and never interprets deployment-specific attributes (PRD 081 principle).

**D8 — One lean resource list, filtered in the browser (user, 2026-09-15).** On start the SPA loads all readable resources once, with only the fields the picker needs: `id`, `kind`, `name`, `classification { typeKey type { name } }` (the type name labels the chip), `groupPaths` (`belongsTo` later, D2). It never loads the full resource (classification values, permissions) like the Swing client. Typing, chip counts, "first 20 A–Z", favorites/recents ordering and the tree are then computed in the browser, **with no server call per keystroke**. How the list learns about added, changed or deleted resources is solved later (OQ 6); until then the list reloads when the SPA starts.

**D9 — Events are found by everyone who may read them (user, 2026-09-15).** The event bucket of the search checks `canRead` instead of `canModify`, so a student finds the exam in their course calendar. Opening an event the caller can't edit opens the sheet read-only (`EventSheetDialogData.readOnly` follows `canModify`). A §12 leak test covers the change (S3).

**D10 — No *Benutzer* chip (user, 2026-09-15).** Users (rapla accounts, owner scope) appear only as hits under *Alle* while typing; the pinned "meine" entry stays for the caller's own events.

**D11 — Flat tree levels first (user, 2026-09-15).** A categorization value is one tree level carrying its name. Nesting by a category's parent chain (faculty ▸ programme ▸ course, as in the prototype) is a later extension; each entry of `groupPaths` is a path from the start, so deeper levels only make the paths longer without breaking the API.

**D12 — Long lists stop at a cap with "Weitere n anzeigen" (user, 2026-09-15).** The picker never renders all rows at once:
- *Alle* without a query shows the first 20 (D1).
- Any other flat list, a search result, and the children of one tree node show the first 100, then a "Weitere n anzeigen" row that reveals the next 100.
- Tree nodes start collapsed except the path to a selected resource.

The list area scrolls (user, 2026-09-15: "und dann scrollbar"); the caps limit how many rows are rendered, not what can be reached. Filtering in the browser is unaffected (D8). No virtual scrolling for now; add it only if capped lists still feel slow.

**D3 — One search field, at the top.** The picker loses its own input. Typing in the top field immediately narrows the picker:
- no minimum length, applied within the active chip;
- tree branches with matches expand automatically, the rest disappears;
- a node whose own name matches shows all its members;
- under *Alle*, matching groups appear as expandable hits and matching users as rows;
- outside *Alle*, a link "n weitere Treffer in „Alle“" points to hits in other chips.

Resources stay part of the top search — the user rejected moving them out — but their hits live in the picker, not in the dropdown.

**D4 — The dropdown shows events only.**
- **Layout:** a first row "n Ressourcen und Gruppen in der Liste links"; clicking it focuses the picker (narrow screens: OQ 5). From three characters, the dropdown lists events.
- **Event hit:** sets the view window to the week of `firstOccurrenceStart` and opens the event sheet for that reservation (`EventSheetDialogData { id }`). The `navigate()` / `edit()` stubs go.
- **Event outside the selection:** if none of the event's resources is selected, the sheet shows a hint with "Ressourcen des Termins auswählen", which replaces the selection with the event's resources.

**D5 — Selection semantics stay.**
- **Gestures:** `TableSelection` semantics (click = only this, Ctrl/⌘ = toggle, Shift = range), and `FilterStore` stays the source of truth.
- **Other picker behaviour:** ★ toggles favorites, a selection pushes a recent (PRD 089), the pinned "meine" entry stays.
- **A click never moves rows (user, 2026-09-15, option A).** Pushing a recent must not reorder the list the user is working in. Reported bug: under *Alle* the clicked row jumped up into the recents block, because `rankAll` orders by the live recents list. *Alle* therefore ranks by a **snapshot** of the recents, taken when the picker loads, when the chip changes and when the search text is cleared; the pushed recent shows up in the order only at the next such point. The snapshot also refreshes on every server (re)load or clear of the recents list, never on a push (`RecentsFavoritesService.reloaded`, review S1 in `2b14f6e6d`), so recents answered after the resource list rank up without waiting for a chip change. Rejected: recording recents only for resources found by typing (B), and dropping recents from the *Alle* ordering (C).

**D6 — Groups replace the "Gruppe" tab.** Hierarchy groups live in the tree (D2). Self-defined filter groups (PRD 077) appear in an "Eigene Gruppen" section under their type chip once a server source for them exists. This supersedes the interim coordinator ruling of the same day ("a loaded group becomes its own chip"), which the user replaced after the tree prototype.

**D7 — ~~The event sheet's add mode keeps its own field.~~ Overturned 2026-09-30 by [PRD 123](123-spa-unified-resource-picker.md) D1: the sheet renders the same picker in assign mode.**

### Behaviour inventory for removed code (AGENTS.md §11)

| Today | Afterwards |
|---|---|
| Omnibox resource hit "Belegung" | click on the row in the picker |
| Omnibox resource hit "+" | Ctrl/⌘-click in the picker |
| Omnibox action pushes a recent | a picker selection pushes a recent |
| Omnibox user hit → owner scope chip | user rows under *Alle* while typing (D10) |
| Group hit "in Liste laden" + *Gruppe* tab | tree nodes (D2), "Eigene Gruppen" (D6) |
| Picker input "in der Liste filtern" | the top field (D3) |
| Event hit `navigate` / `edit` (stubs) | jump to week + open event sheet (D4) |

## Implementation

### Server

The existing `resources(filter:)` query carries the list; GraphQL resolves only the selected fields. Two new fields on `Resource`:

- **S1 — `groupPaths: [[String!]!]!`.** One path per value of the type's attribute annotated `categorization=true` (`AttributeAnnotations.KEY_CATEGORIZATION`). Each path has one level today (the value's name, D11); a multi-valued attribute yields sibling paths, so the resource appears under each value. Empty when the type has no such attribute or the resource has no value. Deeper levels later only lengthen the paths — no API break (D2 first cut). **§12:** when the categorization attribute references another resource, a value the caller cannot read is left out — its name must not surface.
- **S2 — `belongsTo: ID` (later, D2 — not part of the first delivery).** The parent resource via the type's belongsTo attribute. **§12:** `null` when the caller cannot read the parent — neither the id nor the name of an unreadable parent may surface. The packages relation (a resource contains others) follows the same pattern if needed (`packageIds: [ID!]`, filtered to readable ids).
- **Cost.** The query is one pass over the type buckets with an O(1) readable-id check per resource (per-user cache, `rapla.readmodel.authoritative`, default on) and cached names (`ClassificationImpl.TextCache`). The payload is about 100 bytes per resource: roughly 200 KB for 2,000 resources, 2 MB for 20,000. These are estimates from the code, not measurements; no perf test for now (user, 2026-09-15).
- **S3 — event search gate (D9).** `SearchGraphQLController.searchEvents` checks `canRead` instead of `canModify`. The query stays `search(kinds: [EVENT])`: a windowless name scan over all reservations per request, with the permission check on name matches only. More callers now get hits, so more permission checks run per request.
- **Not needed any more:** a separate tree query, sort-before-`limit` in `resources`, a count query — all computed in the browser from the list.

### SPA

- `ResourceSelectionComponent`: tabs → chip row; list renders flat rows (*Alle*, *Favoriten*, *Zuletzt*) or the tree (type chips); keyboard support for the tree (Enter/Space toggle, arrows step).
- `ResourceSelectionStore`: loads the D8 list once and holds it as a signal. The chip key replaces the tab type. The query signal moves here so both the top field and the picker read it. Filtering, ranking and tree building are pure functions over the list (tier-5 testable). `group` / `loadGroup` are removed together with their callers.
- `OmniboxComponent` + `SearchService`: the input writes the shared query; the server search asks for `EVENT` only, from three characters, throttled like the view queries (`throttleTime`, PRD 106 pattern); the dropdown renders the resource-count row plus event hits with the D4 actions.
- `RecentsFavoritesService` stays the source for favorites/recents; entries are matched against the loaded list by id.
- `TableSelection` has to learn row sets that change when nodes expand. Range selection over tree levels follows Swing's discontiguous tree selection.

## Goal

- A new user with no recents or favorites opens the SPA and sees at least 20 resources in the picker without typing.
- There is exactly one search input in the shell (tier-6 assertion on the shell template).
- Clicking an event hit changes the view window to that event's week and opens its sheet (tier-7 e2e).
- Typing in the search field sends no resource query to the server (tier-6/7: only the event search request appears, and only from three characters).
- For a type with a categorization attribute, every resource appears under a node named after each of its values, and resources without a value sit directly under the type (tier-5 tree-building test over `groupPaths`).
- A non-admin user whose readable resource has a categorization value referencing an unreadable resource gets `groupPaths` without the path for that value, and the list response is identical to one over the readable subset (tier-3 leak test).

## Scope

### In scope
- Picker chips, *Alle* list, group tree, one search field, event dropdown with working actions (D1–D6).
- The lean resource list (D8) with the new field S1 `groupPaths`, and the leak test.

### Later
- Keeping the loaded list current while the SPA is open (OQ 6).
- belongsTo nesting (S2) and deeper category levels (D11).

### Out of scope
- The event sheet's add mode (D7) — now [PRD 123](123-spa-unified-resource-picker.md).
- Event search beyond event names, and occurrence hits (PRD 081 later phases).
- A server source for self-defined groups (PRD 077); D6 only reserves the section.

## Plan

Phases 1–3 were committed on master in `d0395b700` (2026-09-15). Checkboxes below are ticked where the committed code shows the item (verified against `d0395b700` by the concept session); the two tests that were missing from `d0395b700` landed with Phase 3b in `2b14f6e6d`.

### Phase 1 — Lean list, chips and the *Alle* list
- [x] Store loads the D8 list once (plus visible users); browser-side filtering and ranking (`state/resource-selection-store.ts`, `state/resource-picker.ts`).
- [x] Chip row replaces the tabs; *Alle* = favorites, recents, A–Z first 20 + "Weitere n anzeigen"; user rows only while typing (`usersMatching`).
- [x] Type chips show the tree directly (Phase 3 landed in the same commit, so no flat interim list).

### Phase 2 — One search field
- [x] Shared query signal in the store; picker input removed; the top field narrows the picker without minimum length.
- [x] Dropdown: resource-count row plus event hits (≥ 3 characters, `kinds: [EVENT]`); event hit jumps to the week and opens the sheet; hint + "Ressourcen des Termins auswählen" (`event/search-hint.ts`).
- [x] Omnibox resource, user and group actions removed per the behaviour inventory.
- [x] S3: event search gate `canModify` → `canRead`; tier-3 test in `SearchGraphQLControllerTest`.
- [x] Sheet opens read-only for events the caller can't edit — code: `event-sheet.component.ts` sets `canModify` from the loaded `canModify && !readOnly`, edit entry points return early (search hits pass no `readOnly`, so the server decides). Test landed with Phase 3b (`2b14f6e6d`).

### Phase 3 — Group tree
- [x] S1 `groupPaths: [[String!]!]!` on `Resource` (`ResourceTreeRules`, `StructuralTypeFetchers`); tier-2 `ResourceTreeRulesTest`; tier-3 `ResourceTreeFieldsGraphQLTest`.
- [x] Tree building in the store from the list (`state/resource-tree.ts`); rendering under type chips: expand/collapse, counts, "alle wählen", auto-expand on query.
- [x] Group hits under *Alle* while typing (`resource-selection.component.ts` `tree` computed; tier-6 spec "under Alle, a group whose name matches the query appears as an expandable row").
- [x] `TableSelection` over expandable rows — code: the visible tree rows' distinct resources feed `selection.setRows`, so Shift ranges follow tree order. Test landed with Phase 3b (`2b14f6e6d`).

### Phase 3b — Display caps (D12) and the D5 row-order fix

Implemented by rapla-impl 2026-09-15 and in REVIEW 119-P3b (rapla-impl report: full `npm test` 621/621, lint and build clean; reverting the snapshot turns the row-order test red). Committed on master in `2b14f6e6d` (2026-09-15, after REVIEW 119-P3b PASS); boxes verified against that commit.
- [x] Every flat list other than *Alle*, every search result and the children of each tree node show 100 rows, then "Weitere n anzeigen" for the next 100.
- [x] Tree nodes start collapsed except the path to a selected resource.
- [ ] The list area scrolls; no virtual scrolling (not verified against the commit).
- [x] Tier-6 test for the caps and "Weitere n anzeigen" (`resource-selection.component.spec.ts`, the four "D12 —" cases).
- [x] Tier-6 test (`event-sheet-readonly.spec.ts` › "opened from a search hit (PRD 119 D9)"): an event opened from a search hit with loaded `canModify: false` renders read-only (Phase 2 item, code already in `d0395b700`).
- [x] Tier-6 test "click, Ctrl and Shift select over the resource rows of an expanded tree" — on an expanded tree: Shift range across a group boundary, Ctrl toggle, plain click replaces (Phase 3 item, code already in `d0395b700`).
- [x] D5 fix: *Alle* ranks by a recents snapshot (refreshed on load, chip change, cleared search, and every server reload of the recents — never on a push), so a click never moves the clicked row; tier-6 test: click a row under *Alle* → it keeps its index, and it appears among the recents after a chip change.

### Phase 4 — Own groups (after PRD 077 / 081 deliver a group source)
- [ ] "Eigene Gruppen" section under the type chip.

## Tests

- Tier 1/2: `groupPaths` derivation (no categorization attribute, single value, multi-valued attribute, category value, resource-reference value) against `FacadeTestSupport` data.
- Tier 3: leak test for `groupPaths` (AGENTS.md §12); event search returns an event the non-admin caller may read but not edit, and nothing for an unreadable event with a matching name — counts included.
- Tier 5: store logic — chip filtering, *Alle* ranking (favorites, recents, A–Z), tree building from `groupPaths`, tree filtering and auto-expand, query sharing.
- Tier 6: shell has one search input; lists, search results and tree node children stop at the D12 caps and "Weitere n anzeigen" reveals the next block; chip row and tree rendering; dropdown shows only events plus the count row.
- Tier 7: pick a room via chip + tree → week shows its events; search an event → week jumps and the sheet opens.

## Open questions

1. ~~Event search scope~~ — resolved 2026-09-15 (user): "may read" is enough → D9.
2. ~~Benutzer~~ — resolved 2026-09-15 (user): no chip, users only as hits while typing → D10.
3. ~~Deeper category nesting~~ — resolved 2026-09-15 (user): flat first, deeper levels later → D11.
Questions 4–7 carry a **default** (set 2026-09-15 by the concept session at the coordinator's request, not ruled by the user). Implementation follows the default; the user may overturn it.

4. **Large deployments:** D8 loads every readable resource once. Up to low tens of thousands this is a few MB. Beyond that the picker would load per type chip and search server-side (throttled). No perf test for now (user, 2026-09-15). **Default:** load the full list for every deployment; no per-chip loading until a large deployment shows slow loading. Rendering is capped by D12. Proposed, not ruled: a load cap (e.g. 5,000, request cap + 1 to detect truncation) with a "zu viele Ressourcen — bitte suchen oder einen Typ wählen" hint, and a server-search mode only once a deployment hits the cap. JSON responses are already gzip-compressed (`server.compression.enabled` in `application.yml`). `resourceAvailability` by `ids` is capped at 200 ids per request (`INVALID_VALUE` on `input.candidates.ids` above that, counted before any lookup; review L6, 2026-10-01).
5. **Narrow screens:** the shell's side panel is always open today (`app.html`: `mat-sidenav mode="side" opened`), with no collapse on narrow widths. **Default:** keep it that way in this PRD; the dropdown's count row only focuses the picker. A collapsible drawer is separate work.
6. **Keeping the list current (later, user 2026-09-15):** how does an open SPA learn that a resource was added, renamed or deleted? Candidates: reload when the picker gains focus after a pause, a lightweight "resources changed since" check against the update history the pods already poll, or a push channel. **Default:** the list loads on SPA start and after the SPA's own resource create/edit/delete (the resource edit dialog); nothing else.
7. **Event search cost:** the event bucket scans all reservation names per request. **Default:** throttle only (`throttleTime`, from three characters); no name index (PRD 085) until measured slow.

## Prototypes

Two clickable HTML prototypes (maintainer artifacts, 2026-09-15, U1 Hochschule sample data): the variant comparison (today / A instant list / B tree / C type chips / D grouped list) and the combined "one search" prototype this PRD describes.
