# Rapla Use Cases

Collected use cases that drive the **new SPA design** (PRD [026](../prd/026-angular-frontend.md)/[074](../prd/074-graphql-declarative-views.md)/[077](../prd/077-calendar-model-graphql.md)/[078](../prd/078-spa-graphql-view-renderer.md) and the
GUI-redesign discussion, 2026-06-21). This is the *requirements ground truth* for
"what the application must let people do" — kept separate from the *how* (PRDs) and the
*data model* ([architecture/domain-model.md](../architecture/domain-model.md)).

> **Deployment-specific data lives elsewhere.** Concrete figures, type keys, naming
> conventions and per-user measurements for the production-shaped **university deployment**
> are kept in the **private** deployment repo (`dhbwrapla/docs/usecases-dhbw.md`), not here —
> so these use cases stay publishable without exposing real deployment data. This doc uses
> **generic deployment archetypes** only.

Two deployment archetypes anchor everything here:

- **A — University timetabling.** Long-horizon **term planning**, but the planning *unit*
  is **one cohort's week**: you anchor on a **cohort** (a student group) and lay out its
  Mon–Fri program. Central surface = the **week view** (anchored on the cohort); a flat
  **table** is the list lens beside it. *(Concrete deployment + measured data → private
  `dhbwrapla` repo.)*
- **B — Recurring weekly seminar program.** The bundled `data/wochenplan.xml` (a
  seminar-scheduling shape). **Weekly, recurring** planning. Central surface = the
  **`week_timeslot` grid** ("Wochenprogramm"), anchored on room/leader.

A third archetype — **C — Equipment lending desk** (multi-day loans of portable equipment
to external borrowers, staff-mediated) — is documented separately in
[equipment-planning.md](equipment-planning.md); it is the one archetype where the weekly
view is *not* central.

The headline finding: **the weekly view is central to *both* — what differs is the
*anchor* and the *render-mode flavor*.** Archetype A anchors on a **cohort** and uses the
standard week view; B anchors on room/leader and uses the timeslot grid. There is **no
single hardcoded central surface**: landing = "the configured/last saved view, in *its*
render-mode, on *its* anchor". The SPA fundament is a *render-mode-agnostic view host* over
`appointmentBlocks(filter:)` — not a hardcoded calendar or table.

> Personas/data are dummies per AGENTS.md §17 — generic placeholders only.

---

## Glossary

Canonical terms for the SPA selection/view model (agreed 2026-06-21). Use these
consistently; English for all definitions.

| Term | Meaning | Technical |
|---|---|---|
| **View Definition** | Level 1 — the *defined* structure (admin / rapla-default): columns, render mode, declared inputs | `@view` query ([PRD 074](../prd/074-graphql-declarative-views.md)) |
| **Saved View** | Level 2 — a *stored, named* instance: `viewRef` + concrete values (selection, date, name), per user | `CalendarModelConfiguration` / SavedView ([PRD 077](../prd/077-calendar-model-graphql.md)) |
| **Runtime State** | Level 3 — the ephemeral live state (changed, not yet stored as a Saved View) | GraphQL variables |
| **Group** | a *rule* over resources (type / derived / self-defined); live, §12-scoped | `ClassificationFilter[]` = `AllocatableFilter` |
| **Selection** | the *currently active* resources of the view (= the Chips) | `ReservationFilter.allocatableMatching` |
| **Chip** | one entry of the Selection — a *single* resource **or** a whole Group | — |
| **Stepping List** | the left list to click through (sources: Recent / Favorites / Group) | — |
| **Render Mode** | Week · Table · Month · Timeslot | — |
| **Omnibox** | the single find/search field | — |
| **Event** | a reservation (the logical course/event with many occurrences) | `Reservation` |
| **Occurrence** | a single materialized appointment of an Event | `AppointmentBlock` |
| **Actions** | **filter** (resource/group) · **navigate** (date / Event → first occurrence / Occurrence / Saved View) · **edit** (event sheet) | — |

Resolved naming:
- **View Definition** (the structure, shared) vs **Saved View** (the stored per-user instance) —
  no German *View/Sicht* ambiguity; matches [PRD 077](../prd/077-calendar-model-graphql.md)'s three storage levels.
- **Group** = the noun (a resource rule); **filter** = the verb; **Filter Editor** = where
  self-defined Groups are built. "Filter" is *not* used as a competing noun.
- **Event** = Reservation (the whole course), **Occurrence** = one AppointmentBlock — so
  "navigate to first occurrence" is unambiguous.

## Actors

| Actor | Who | Device | Mode |
|---|---|---|---|
| **Planner** | Central scheduler / programme lead / seminar lead | Desktop (primary) | read + heavy write |
| **Planner-on-the-go** | Same person, away from the desk | Phone (later) | targeted single-event write |
| **Consumer** | Lecturer / student / seminar attendee | Phone / mail | read-only (out of SPA scope — served by iCal/HTML export) |

The new SPA targets the **Planner**, desktop-first. Mobile (quick edit) is in scope but
*later*; on the phone the resource tree is simply gone.

---

## Use case overview

| # | Use case | Actor | Archetype | Central surface / render-mode | Frequency |
|---|---|---|---|---|---|
| UC-1 | **Plan a cohort's week** (→ term) | Planner | A | **week view**, anchored on a **cohort** | seasonal, intense |
| UC-2 | **Weekly program planning** | Planner | B | **`week_timeslot` grid** (Wochenprogramm) | weekly |
| UC-3 | **Find a free room** | Planner | both | ad-hoc availability check | daily, spontaneous |
| UC-4 | **Find a known item (power search)** | Planner | both | search box → result | constant |
| UC-5 | **Resolve a conflict** | Planner | both | conflict view + calendar | on every clash |
| UC-6 | **Quick edit on the go** | Planner-on-the-go | both | focused event sheet (mobile) | occasional, *later* |
| UC-7 | **Publish / export a calendar** | Planner | both | iCal / HTML / CSV (server-rendered) | set-and-forget |
| UC-8 | **Read my own schedule** | Consumer | both | — (out of SPA scope) | constant |

The *editing* side (what UC-1/2/5/6 do when they write) is decomposed separately in
[reservation-editing.md](reservation-editing.md) (UC-E1…E16) — requirements ground
truth for the SPA event sheet and the availability search ([PRD 091](../prd/091-spa-reservation-edit-and-availability.md)).

---

## UC-1 — Plan a cohort's week (archetype A; aggregates to term planning)

**Goal:** build the timetable of **one cohort** (student group) — anchor on the cohort,
view its **week**, and place each course event into its slots, assigning a **room** and one
or more **lecturers**. Term planning is the **aggregate** of doing this for every cohort.

- **Anchor:** a **cohort** — the selection that scopes the week.
- **Trigger:** start of a planning period (term); revisited per cohort.
- **Central surface:** the **week view** for the anchored cohort (Mon–Fri, that cohort's
  events). A flat **table** (*Name, Start, End, Cohort, Person, Room, Duration*) is the
  **list lens** beside it; month for the wider check.
- **Key entities:** course event / exam (Reservation) · room / sub-room · lecturer (person)
  · cohort / cohort-group / study-programme (Allocatable).
- **Inputs (the view's filter):** time window (`from`/`to`, one week, anchored) · the cohort
  selection (`allocatableMatching` on `typeKeyIn:[<cohort-type>]`) · event type (`typeKeyEq`)
  · per-type rules (the generated `where<Type>` predicate).
- **Heavy needs:** **anchor on a cohort and see its week** · **bulk selection over
  hierarchy** ("all cohorts of a programme", "all rooms in a building") · **stable
  overview** · **conflict** surfacing (a room/lecturer already taken in that slot).
- **rapla mechanism:** `Query.appointmentBlocks(filter: ReservationFilter)`, **render-mode
  = week**, anchored via the cohort in `allocatableMatching`.
- **Design notes:** the **cohort anchor** is the key navigation act — the planner picks
  *which cohort's week* they're building. The single search field serves this well *if* it
  also returns **group results** (pick a whole programme → its cohorts) for the bulk case.

## UC-2 — Weekly program planning (archetype B)

**Goal:** plan the recurring weekly program — which seminar runs in which **time band** on
which day, led by whom.

- **Trigger:** weekly (rolling). The program repeats with weekly variations.
- **Central surface:** **`week_timeslot` grid** ("Wochenprogramm") — rows = **named
  timeslots** (deployment-configured, e.g. 05:00, 08:00, 12:00, 19:00…), columns = weekdays,
  cells = seminars. A **dense, whole-week, scannable** overview.
- **Key entities:** seminar (Reservation) · leader (person, shown via the PLANNING-variant
  name with its marker chain) · room.
- **Inputs:** time window (one week, anchored) · selection · filter.
- **Heavy needs:** **whole-week-at-a-glance overview** (scan, not search) · fixed-band
  placement · planner-specific naming (PLANNING variant).
- **rapla mechanism:** same `appointmentBlocks(filter:)`, **render-mode = timeslot grid**;
  rows come from the deployment's configured `Timeslot` bands (Timeslot plugin).
- **Design notes:** the timeslot grid is a **first-class render mode**, not a niche — for
  this archetype it *is* the main view. Its rows are **configured**, not derived. Here search
  is secondary; the standing overview is the value.

## UC-3 — Find a free room

**Goal:** spontaneously find a room that is **free** now (or in a given slot) — e.g. a
lecturer needs a room for an ad-hoc session.

- **Trigger:** ad-hoc, "I need a free room for *this* time".
- **Surface:** a focused availability check — pick a time window (+ maybe capacity /
  equipment / building), get the list of rooms with **no booking** in that window.
- **Key entities:** room (Allocatable, with capacity / equipment / building attributes) ·
  existing `appointmentBlocks` in the window.
- **Inputs:** time window · room constraints (type/capacity/equipment/building).
- **rapla mechanism (gap):** **no direct "free room" query** in the schema today. Derived:
  rooms **minus** rooms with a block in the window — i.e. `allocatables` ∖
  (`appointmentBlocks(filter: {from,to})` → their rooms). Candidate for a dedicated
  availability resolver. *Flag this as a capability gap to design.*
- **Design notes:** the most **mobile-friendly, search-shaped** use case — small inputs, a
  ranked list out. **Its own user group** in practice (see "Data reality": room-only users),
  not merely a planner's side-task. Strong argument for treating it as a first-class flow.

## UC-4 — Find a known item (power search)

**Goal:** jump to a specific room / person / event by name, fast.

- **Surface:** the single search box ([PRD 028](../prd/028-angular-power-search.md)) — ranked across allocatables, reservations,
  conflicts, weighted by what's on screen (selection + visible window).
- **Verbs:** toggle into selection · "switch selection" (replace with just this one) ·
  jump-to-block. **Recency** floats recently-touched entities up.
- **rapla mechanism:** `searchText` + `matchKind {PREFIX,SUBSTRING,FUZZY}` on
  `AllocatableFilter` **and** `ReservationFilter` (shipped 2026-05-29) · `Reservation.hasConflicts`.
- **Design notes:** the everyday entry point; complements (does not replace) a structured
  filter ("all events of type X in period").

## UC-5 — Resolve a conflict

**Goal:** see and fix double-bookings (same room/person, overlapping times).

- **Trigger:** a clash appears (badge / conflict view) after a store.
- **Surface:** conflict list → jump to the calendar with the overlap emphasized; on mobile,
  a **textual** conflict summary (no grid): "collides with *<event>* in *<room>* on these dates".
- **rapla mechanism:** `Query.conflicts(reservationId:)` · `Reservation.hasConflicts` for
  badges · the conflict view is a *view with a conflict-sourced selection* ([PRD 077](../prd/077-calendar-model-graphql.md)), not a
  separate domain.

## UC-6 — Quick edit on the go (mobile, later)

**Goal:** a planner away from the desk makes a **single targeted change**.

- **In scope (mobile):** move an appointment · swap room/lecturer · cancel one occurrence
  (add exception) · add a note · set "planning done" status.
- **Out of scope (stays desktop):** create an event from scratch · resolve a multi-way
  conflict · bulk re-room · build a complex recurrence rule.
- **Surface:** find (search) → **event sheet** (focused, deep-linkable, responsive) → one
  edit → save with **server conflict check** shown as text.
- **rapla mechanism:** `Reservation.canModify` (server-derived, gates the edit button) ·
  GraphQL mutations (`UpdateReservationInput`/`ChangeOp`/`applyChanges`) · PRD [023](../prd/023-presenter-view-extraction.md)/[024](../prd/024-server-side-edit-services.md)
  pure-Java validation (recurrence) shared with desktop.
- **Status:** in scope for the product, **built later**. Design the event sheet mobile-first
  from day one so responsive isn't a retrofit.

## UC-7 — Publish / export a calendar

**Goal:** make a calendar consumable outside Rapla (subscriptions, embeds, spreadsheets).

- **Surface:** server-rendered iCal / HTML / CSV from the **same** GraphQL view ([PRD 074](../prd/074-graphql-declarative-views.md)
  server-side rendering).
- **Privacy invariant:** public/unencrypted exports strip person names via the server-set
  `internal_request=false` context flag (never a client variable) — §12.
- **Load-bearing URLs:** the six legacy iCal/calendar URLs external subscribers depend on
  (AGENTS.md §15 allow-list) must not move.

## UC-8 — Read my own schedule (consumer)

**Goal:** a lecturer/student sees "where is my next session?".

- **Status: out of SPA scope.** Served today by **iCal subscription / HTML export**
  (UC-7). Recorded here to mark the boundary: the new SPA is a *planner tool*, not a
  consumer app. Revisit only if a real consumer-app need appears.

---

## Data reality — per-user permission scoping (the load-bearing finding)

Measured on a live production-shaped university deployment (concrete figures in the private
`dhbwrapla/docs/usecases-dhbw.md`). The generic, publishable findings:

- **§12 permission scoping shrinks the working set by ~1–2 orders of magnitude.** A user
  sees only their location's resources + their programme's persons/cohorts — a small
  fraction of the full deployment (tens of thousands → a few hundred per user).
- **So a single typed search field is sufficient** for the normal-user majority: the
  per-user resource set is small and the names are short/prefix-coded (typeable). The
  resource tree is **comfort, not necessity**.
- **Three user profiles emerge from the scoping**, and they map onto the use cases:
  1. **Room-only** users (book rooms, see almost no persons) → **UC-3** is a *distinct user
     group*, not a side-task.
  2. **Cohort-planner** users (scoped to their programme) → **UC-1**.
  3. A rare **cross-programme coordinator** who sees most of the deployment → the **power-user
     exception** for which an "advanced mode" (filter / tree toggle) exists.
- **The weekly view payload stays small** (~hundreds of blocks) regardless of user size →
  the render host scales trivially.
- **Only the *person* set is large/variable per user** — and persons are the easiest search
  case (you know the name).

**Conclusion: the single search field works *because of* §12 scoping** — permission scoping
is a *precondition* of the lean SPA design, not just a security feature. The full-set power
user is the documented minority that needs the advanced mode.

---

## Selection model — one search field, tree optional

How the planner picks *what* a view shows. The legacy Swing client proved one shape
(`ResourceSelectionViewSwing`): a **name-search field over a resource tree** that **prunes
the tree in place** (`pruneByName` / `NameSearchMatcher`), a structured **ClassificationFilter**
button beside it, **sticky** selection, and a **hidden-selection indicator**
(`search.hidden_status` — "N selected items are hidden by the current search").

**SPA direction: one big search field — no persistent tree.** The new SPA's selection
control is a **single large search box**; typing shows a **result list** underneath, and
you pick to add the resource to the selection (chips). This *inverts* the legacy layout
(search *over* an always-present tree): in the SPA the **search is the surface**. The "Data
reality" above is why this is viable — §12 keeps the per-user set small and typeable.

- **You usually know part of the name** → type it → list of matching resources → pick.
- **The tree is legacy / optional, not the SPA default.** It may return later as an
  *on-demand* browse affordance (einblendbar) for "name unknown" / bulk-over-hierarchy.
- **Tree and search do not contradict** — in the legacy client they are literally the *same*
  widget (search narrows the tree). The SPA simply leads with the search.

Beside the search sits the **structured filter** (ClassificationFilter): "all events of type
X in this period" / per-type attribute rules — which the name search does *not* subsume.

| Control | What it is | feeds | best for |
|---|---|---|---|
| **One big search field** (SPA default) | type → result list → pick | selection (chips) | UC-1/2 anchor, UC-3 free room, UC-4 known item |
| **ClassificationFilter** (beside) | structured rule editor | the view's filter | "all type-X events in period" |
| **Tree** (legacy / optional, deferred) | browse hierarchy, bulk-select | selection (chips) | UC-1 bulk, browse when name unknown, the power-user (advanced mode) |

**What the single field must still cover (capabilities the legacy tree provided):**
- **Browse when the name is unknown** — the result list must work on an empty/short query
  (recents, or grouped suggestions), not require an exact name.
- **Bulk over hierarchy** — "all cohorts of a programme" / "all rooms in a building" must be
  reachable: either the search returns **group/parent results** you can pick (a whole
  programme / building), or it falls to the ClassificationFilter — *not lost*. (Cheap given
  §12: the per-user hierarchy is small and flat.)
- **Selection safety** — the legacy hidden-selection indicator's job is carried by the
  **selection chips**, which persist regardless of the current query.

**Open:** does picking from the list **add** to the selection (chips accumulate) or
**replace** it (single anchor)? Likely both — single pick = anchor + a "switch selection"
verb ([PRD 028](../prd/028-angular-power-search.md)) to replace; explicit add for multi. Decide when we design the picker.

---

## Resource selection — groups are filters; the stepping list; the omnibox

Worked out in the GUI discussion (2026-06-21). Mockups:
[`mockups/stepping-list.html`](mockups/stepping-list.html),
[`mockups/group-load.html`](mockups/group-load.html),
[`mockups/browse-all.html`](mockups/browse-all.html),
[`mockups/search-navigation-selection.html`](mockups/search-navigation-selection.html).

**On the resource level there is ONE concept: a rule** (`ClassificationFilter[]` / GraphQL
`AllocatableFilter`). "Filter", "group", and "a chip standing for a set" are the same thing
seen three ways.

**A group = a `ClassificationFilter[]` (a rule over allocatables).** Three flavours, same
type underneath:
- **Type groups** — "all rooms", "all lecturers", "all cohorts (n)": just a type predicate.
  Always available — this is how you browse a *whole* type (the tree-equivalent).
- **Derived groups** — "rooms in building X", "cohorts of programme Y": type + one predicate.
- **Self-defined groups** — an arbitrary `ClassificationFilter[]` built in the **filter editor**
  (AND/OR + attribute predicates, e.g. rooms with >50 seats and a projector). Saving + naming
  one = a self-defined group.

→ **The "structured filter beside the search" IS the group builder.** There is no separate
filter axis on the resource level — building a custom set = building a group (a rule).

**Groups are live; favourites/recents are static.** A group is a *rule*, re-evaluated on use
(and §12-scoped), so a newly added matching resource (e.g. a new lecturer) **appears
automatically**. Favourites (pinned ids) and recents (visited ids) are static id-lists.

**Two uses of a rule:**
- **Materialise → the stepping list** — its matches as a flat, in-list-filterable list you
  click through.
- **Apply → one chip** on the view — the whole rule as a single chip ("lecturers of programme
  Y") → the view shows all matches together. This is how *bulk over hierarchy* stays **one
  chip, not N**.

**The stepping list (left) — populate, step, remove:**
- Three sources / tabs: **Recents** (auto — every viewed resource), **Favourites** (pin via ★),
  **Group** (load a rule, including a whole type "all rooms (n)").
- **Single-click an entry = filter the view to it (replace)** → its occupancy; click the next =
  replace again. The dominant rhythm ("step from one room/cohort to the next"); arrow keys +
  live preview optional. `+` accumulates instead of replacing.
- **Removal:** Favourites — un-pin; Recents — × / FIFO; **Group — whole only** (`× clear` /
  unload). **No individual removal from a group, and no fork-to-static** — a group is an atomic
  rule; for a different set, use a different rule. Editing a self-defined group = editing its
  rule in the filter editor.

**Division of labour:**
- **Omnibox** *finds* — a single resource (filter/replace + `+`), a Veranstaltung (navigate to
  first appointment · `+` filter · ✏️ edit), a single appointment (navigate · edit), a saved
  view (load), or a **group** ("all X (n)" / a derived group → load into the list).
- **Filter editor** *builds* self-defined groups (`ClassificationFilter[]`).
- **Stepping list** *materialises* a rule → click through (the Swing-tree-stepping replacement).
- **Chip rail** *applies* rules (single resource or whole group) → the live view filter; the
  chips **persist regardless of the search query** — they are the "what's currently selected"
  display and carry the legacy hidden-selection safety.

**Status:** the omnibox's two-gesture navigate-vs-filter shape (the *hybrid*) is the current
leaning, **not finally decided**. The group=filter unification and the atomic-group / no-fork
rules above are settled within this discussion.

## Cross-cutting design implications

1. **The weekly view is central to both — anchor + render-mode flavor differ.** Archetype A →
   **week view anchored on a cohort**; B → **timeslot grid anchored on room/leader**. The
   table is a *list lens*, not the planning surface. Landing = "the configured/last saved
   view, in its render-mode, on its anchor". The fundament is a render-mode-agnostic view
   host, not a hardcoded screen.
2. **Render modes are a set, and bigger than table/week/month.** It includes the
   **`week_timeslot` grid** (configured rows) and `day_timeslot`. Treat as first-class.
3. **Search-first is not universal.** It wins for UC-3 (free room) and UC-4 (known item);
   **anchor-then-overview** wins for UC-1 (pick a cohort → see its week) and UC-2 (weekly
   program). The surface decides, not a global rule — the central planning act is often
   *"anchor on X, show its week"*, not *"type a query"*.
4. **One search field is viable because of §12** (see "Data reality") — per-user scoping
   keeps the set small/typeable. The tree is comfort; the advanced mode (filter/tree) serves
   the rare full-set power user.
5. **Same data root, many lenses.** All of UC-1/2/3/5 sit on `appointmentBlocks(filter:)`
   plus a render-mode and a variant (DISPLAY/EXPORT/PLANNING). The "view ebenen": granularity
   (events/blocks/per-day) × render-mode (table/day/week/month/timeslot) × variant.
6. **Capability gap:** UC-3 (free room) has **no direct query** today — needs a derived
   availability resolver. Surfaced for design.
7. **Saved-view navigation is its own axis** — not the search field's job. Real deployments
   accumulate many saved views; reaching them needs a dedicated picker/list, separate from
   resource search.
