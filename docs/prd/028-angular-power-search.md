# PRD 028 — Angular power search (single-calendar shell)

**Status:** draft (research / scoping only — no implementation)
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-11

## Goal

Define a **single-calendar shell** for the Angular frontend
(PRD 026) with a persistent **power-search box** above one main
calendar. The search box is the primary way to find allocatables,
reservations, and conflicts. Results are ranked by **how relevant
they are to what's currently on screen** — selected resources and
the visible time window — so finding something doesn't require
leaving the calendar.

This PRD is about the **search UX, ranking model, and interaction
verbs**. Wire format and backend endpoints land in a follow-up
server PRD once the UX is locked.

## Scope

In scope:

- Single-calendar shell layout: search-bar at top, calendar below.
- Power-search box: ranked result list across three entity types
  (allocatable, reservation, conflict).
- Ranking model (priority tiers — see below).
- Result-row verbs: select / unselect an allocatable, navigate to
  a reservation's first occurrence, "switch selection" affordance.
- Recency boost: recently searched / visited entities float up.
- Behaviour for reservations that **don't** touch any currently-selected
  allocatable (the "orphan" case).

Out of scope:

- The reservation-edit flow itself (PRD 026 Phase 2+).
- Admin views, plugin UIs, user / category administration.
- Server-side search endpoint design — separate PRD once UX is
  locked. Until then assume `/storage/queryAppointments` +
  `/storage/resources` + a future `/search` exist.
- Conflict detail UI; this PRD only ranks conflicts as a result
  type, clicking one defers to a future conflict-detail view.

## Ranking model

The result list is partitioned into **priority tiers**. Within each
tier, results are sorted by string-match quality (prefix > substring >
fuzzy) and then by recency. Tiers are visually separated (subheading
or thin divider); search-as-you-type re-ranks on every keystroke.

### Allocatables (resources)

| Tier | Definition | Visual |
|---|---|---|
| **A1 — Selected** | Currently in the calendar's selection (drawn as a row / colour band) | Highlighted; checkbox shown checked |
| **A2 — Recent** | In the user's "recently visited / searched" list, not currently selected | Mid-emphasis; checkbox unchecked |
| **A3 — Other** | Match the query, neither selected nor recent | Low-emphasis; checkbox unchecked |

### Reservations (events)

| Tier | Definition | Visual |
|---|---|---|
| **E1 — Visible** | At least one appointment block falls inside the **currently visible time window** AND touches at least one **currently selected allocatable** | Highlighted; "jump to block" is the default action |
| **E2 — Selected resource, off-screen** | Touches a selected allocatable but **no block is inside the visible window** | Mid-emphasis; default action is "navigate to first start" |
| **E3 — Recent** | Recently visited reservation, not in E1/E2 | Mid-emphasis; default action "navigate to first start" |
| **E4 — Orphan** | Matches the query but touches **none** of the selected allocatables | Low-emphasis with a badge ("not in current selection") — see *orphan handling* below |

### Conflicts

Conflicts are pairs of reservations. Rank as the *worst* of the two
component reservations (i.e. if one side is E4, the conflict row is
E4). Conflict rows always carry the conflict badge so users can
distinguish them from plain reservation matches.

### Recency boost

A bounded ring of "recently visited" IDs (allocatable, reservation)
per user, kept in browser storage (e.g. `localStorage`, ~50 entries
each, FIFO eviction). Updated on:

- explicit selection of an allocatable from the search,
- opening a reservation (edit, detail, or "jump to") from the search,
- direct click on a reservation block in the calendar.

Recency only **lifts a result into tier A2 / E3**; it does not
override the higher tiers. (A selected resource is always A1, even
if it was selected ten seconds ago.)

## Interaction verbs

### On an allocatable result row

- **Primary**: checkbox toggle (select / unselect). Re-renders the
  calendar inline; search results stay open and re-rank
  (the selection delta moves rows between A1 and A2/A3).
- **Switch selection** (modifier-click, e.g. shift-click, or a row
  side-button): replace the current selection with **just this
  one** allocatable. Avoids the "uncheck-everything-then-check"
  shuffle. Probably the highest-frequency power-user verb.
- **Open detail** (secondary): full allocatable view (out of scope
  for v1 — link to future PRD).

### On a reservation result row

- **E1 (visible)**: default click scrolls the calendar to the
  block and flashes it. The reservation is **already on screen** —
  this is a "show me where" verb.
- **E2 / E3 (off-screen, but touches a selected resource)**:
  default click moves the calendar viewport to the **first
  appointment's start date** and selects that occurrence. Selection
  is preserved.
- **E4 (orphan)**: see below.
- **Open edit** (secondary, modifier-click or row side-button):
  open the reservation-edit dialog (PRD 026 Phase 5).

### On a conflict result row

- Default click: same as the underlying reservation, but the calendar
  draws the conflict's overlap interval emphasized.
- Secondary: open conflict-detail (deferred PRD).

## Orphan reservations (E4)

A reservation that matches the query but doesn't touch **any**
currently selected allocatable poses a UX problem: jumping to it
would either (a) show an empty calendar at that date (because
nothing the reservation uses is selected), or (b) silently expand
the selection behind the user's back.

Three candidate behaviours; **pick one before Phase 1**:

1. **Auto-add to selection on click.** Clicking E4 adds the
   reservation's primary allocatable to the calendar selection,
   then jumps. Cheap; mutates user state.
2. **"Add & jump" / "Just jump" split-button.** Two affordances
   on the E4 row — explicit. Costs a click on every orphan.
3. **Preview overlay.** Click opens a transient pop-out showing
   *just* this reservation's block + its allocatables, without
   modifying the calendar selection. Closing it returns to the
   selection state from before. Best UX but largest implementation.

Recommendation pending user input — annotate decision here once
made.

## Open questions

1. **Tier visualization.** Section headings vs. inline dividers vs.
   no separators (just sort order). Headings communicate the model
   but eat vertical space; if the result list is short, they read
   as noise. Probably headings appear only when ≥ 2 tiers are
   non-empty.
2. **Tier limits.** Cap each tier at N results (say, 5) with a
   "show all" expander, or let the longest tier dominate? Power
   users with thousands of allocatables will hit this; needs a
   bounded query shape on the backend either way.
3. **Asymmetric scope per tier.** Should E1/E2 query the full
   reservation history (years back) or only a bounded window
   (say, ±1 year around the viewport)? Full history is honest but
   slow; bounded is fast but misleads when the only match is
   outside the window. Likely: bounded with a "search older →"
   row at the bottom of E4.
4. **Conflict tier interaction.** When the user clicks a conflict
   row that maps to two off-screen reservations, jump to which?
   Earliest start? Or open a transient "conflict pair" preview?
5. **Search-as-you-type debounce.** Local-only filtering is free,
   but reservations need a server call (the SPA doesn't hold the
   full reservation graph). 200 ms debounce + cancel-prior is the
   conventional answer; confirm.
6. **Recency scope.** Per-user-per-device (browser storage) vs.
   server-persisted recency (`/preferences` round-trip). Device
   is cheaper and survives no backend round-trip; server-side gives
   portability across devices. Pick one.
7. **Orphan handling.** Decide between the three candidate
   behaviours above.
8. **"Switch selection" gesture.** Shift-click? Cmd/Ctrl-click?
   Or a dedicated icon-button on each row? Modifier-clicks are
   invisible; explicit icons crowd the row.
9. **Filter editor coexistence.** Does the classification-filter
   editor (PRD 023's `ClassificationFilterBuilder`) disappear
   entirely in the SPA, hide behind a "filter" affordance on the
   search box, or live as a separate panel? Search subsumes most
   filter use cases but not "show me all reservations of type X
   in this period" — that's still a filter.
10. **Permission leakage.** §12 of AGENTS.md is the binding
    constraint — search must not echo back ids the user can't
    read. The `/storage/resources` payload is already filtered;
    confirm a future `/search` endpoint uses the same boundary.

## Plan

To be drafted once open questions are decided. Likely shape:

- **Phase 0** — pick orphan handling, switch-selection gesture,
  tier-limit policy, recency scope. Update this PRD with the
  decisions.
- **Phase 1** — search shell (input box, result list, tier
  rendering) wired to **client-side filtering** of the already-loaded
  allocatable cache. No server changes. Tier A1/A2/A3 work end-to-end;
  E-tiers stubbed as "search reservations →" CTA.
- **Phase 2** — add reservation search. Reuses
  `/storage/queryAppointments` for E1/E2 (viewport-bounded) plus a
  new `/search/reservations?q=` endpoint for E3/E4 (bounded
  window). Server PRD here.
- **Phase 3** — recency ring (read/write), tier ordering by
  recency.
- **Phase 4** — conflict result type; piggy-backs on
  `/storage/conflicts` + the search index.
- **Phase 5** — "switch selection" verb, orphan handling
  (per Phase 0 decision), filter-editor coexistence (per OQ 9).

## Tests

To be drafted per phase. Initial test charter:

- **Tier-1 (pure)**: ranking function — given (`query`, `selected
  set`, `viewport`, `recency ring`, candidate list), expected
  ordering. Pure Java if we share the ranker via PRD 024, pure
  TS otherwise. Multiple cases per tier boundary
  (just-in/just-out of viewport; resource just-deselected; etc.).
- **Tier-3 (MockMvc)**: the eventual `/search` endpoint —
  permission leak test per AGENTS §12; bounded-window honoured;
  empty-query empty-response.
- **E2E (Playwright or similar)**: type query → click result →
  calendar scrolls. One golden-path test per tier
  (A1 toggle, E1 jump, E2 navigate-to-first, E4 orphan path).
  Worth investing here because the tier model is what's hardest
  to keep coherent over future changes.

## See also

- [PRD 026 — Angular frontend (reservation editing)](026-angular-frontend.md)
- [PRD 023 — Presenter / Model carve-out](023-presenter-view-extraction.md)
- [PRD 024 — Server-side edit services](024-server-side-edit-services.md)
- [`architecture/rest-api.md`](../architecture/rest-api.md) — the
  endpoints this PRD assumes will be available.
- AGENTS.md §12 — permission-leak rule that constrains the
  eventual `/search` endpoint.
