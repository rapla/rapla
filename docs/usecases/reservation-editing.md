# Use cases — reservation editing (the event sheet)

Drill-down of the editing side of [README.md](README.md) (UC-1/2/5/6 touch editing;
this doc decomposes *what editing actually consists of*). Grounded in the Swing
functional inventory
([../architecture/reservation-edit-ui-inventory.md](../architecture/reservation-edit-ui-inventory.md))
and the market/UX research collected for [PRD 091](../prd/091-spa-reservation-edit-and-availability.md). Design proposals → [PRD 091](../prd/091-spa-reservation-edit-and-availability.md)
(resource availability, event sheet, matrix) and [PRD 092](../prd/092-free-slot-search.md) (free-slot search); this
page is the requirements ground truth.

Terminology per the README glossary: **Event** = Reservation, **Occurrence** =
materialized AppointmentBlock, **appointment** = the (possibly repeating) series
object owning occurrences.

## The three axes of an edit

Every reservation edit touches one or more of:

1. **What** — classification (type + attributes), permissions.
2. **When** — appointments: single times, recurrence rules, exceptions.
3. **With what** — resource allocation, including the *restriction map* (which
   resource is allocated to which appointments) and per-occurrence conflicts.

The legacy Swing dialog stacks all three panes into one modal editor. The SPA can —
and should — decompose them: most real edits touch exactly one axis.

## Use cases

| # | Use case | Axis | Frequency | Swing today | SPA opportunity |
|---|---|---|---|---|---|
| UC-E1 | Create an event (type, name, first appointment, resources) | all | high | full dialog | guided sheet; sensible defaults from the view context (clicked slot → time, anchor → resource) |
| UC-E2 | Change a single event's time / move it | when | high | dialog or calendar drag | calendar drag + inline sheet; server conflict dry-run before save |
| UC-E3 | Build / change a recurrence rule (+ exceptions) | when | medium | RepeatingEditor | dedicated recurrence editor reusing `/api/edit/validate-recurrence` + `/expand-blocks` (occurrence preview!) |
| UC-E4 | Cancel one occurrence of a series | when | high | exception dialog | one click on the occurrence ("skip this date") — mobile-capable (UC-6) |
| UC-E5 | **Find a free resource** for the event (room, device) | with what | high | picker icons (available / partially / taken) | ranked tri-state finder: "free for 10/10", "8/10", blocked — with drill-down to the clashing dates |
| UC-E6 | **Find a free slot** for a fixed resource set ("when are room X + lecturer Y free?") | when | high | "free appointment >>" button (single appointment, global options, first hit only) | ranked slot list ("Tue 10–12: free in 14/15 weeks") + availability strip while dragging |
| UC-E7 | **Assign resources to occurrences** (the restriction map) on events with many appointments | with what | medium, painful | per-resource checkbox popup | occurrence×resource matrix with per-cell conflict state; column/row bulk ops |
| UC-E8 | Resolve per-occurrence conflicts during editing (incl. **split booking**: room A for 8 dates, room B for the 2 clashes) | with what | on every clash | manual: read icons, toggle checkboxes | matrix + "repair" suggestions (auto-propose alternative resource for the clashing occurrences) |
| UC-E9 | Request a resource the user cannot allocate (request-only workflow) | with what | deployment-specific | REQUESTED status auto-set in picker | explicit "request" affordance + status chip; keep §12 semantics |
| UC-E10 | Edit classification attributes / event type | what | medium | info pane | plain form (DynamicType-driven), main vs additional attributes |
| UC-E11 | Edit event permissions | what | rare, admin | permissions tab | separate admin panel inside the sheet; hidden without `canAdmin` |
| UC-E12 | Convert a finite series to single appointments | when | rare | "convert" button | keep (restrictions must migrate) |
| UC-E13 | Skip holiday periods (add configured holidays as exceptions) | when | seasonal | holiday button extension | recurrence editor integration ("exclude holidays" toggle) |
| UC-E14 | Undo/redo inside an edit session; abort without trace | cross | constant | CommandHistory, clone semantics | draft state client-side; GraphQL full-state update keeps abort free |
| UC-E15 | Edit under concurrency (someone else saved meanwhile) | cross | occasional | RaplaNewVersionException dialog | `expectedLastChanged` → CONCURRENT_MODIFICATION; show "reload & reapply" |
| UC-E16 | Templates / batch multi-event edit | cross | rare | supported | consciously deferred (out of SPA v1) |

## Load-bearing observations

- **UC-E5/E6/E7/E8 are where the Swing dialog is weakest** and where the SPA can beat
  it — the market research ([PRD 091](../prd/091-spa-reservation-edit-and-availability.md)) shows no mainstream product handles
  per-occurrence resource assignment on recurring series well; rapla's sparse
  restriction map is the native data model for exactly that.
- **"Partially free" is a first-class state**, not an error: Swing already computes
  it (`NOT_ALWAYS_AVAILABLE`) but only shows an icon. Surfacing the *fraction*
  ("free for 8/10") and the *clashing dates* turns it into an actionable signal.
- **The Swing free-slot search is underpowered by design:** single appointment, first
  hit only, parameters buried in global CalendarOptions, only already-allocated
  resources. UC-E6 wants: multiple ranked candidates, in-context parameters (window,
  duration, weekday pattern), arbitrary candidate resource sets.
- **Most edits are single-axis.** UC-E2/E4 (the mobile UC-6 cases) must not require
  loading the full three-pane editor. The sheet should open scoped to the axis the
  user came from (clicked an occurrence → "when"; clicked the room chip → "with
  what").
- **Validation split stays as-is:** client converges (non-blocking auto-corrections,
  as documented in reservation-edit.md), server decides (EventCheck equivalents +
  dispatch). Recurrence validation and block expansion already exist as server
  services (`/api/edit/*`, [PRD 024](../prd/024-server-side-edit-services.md)) so the SPA never re-implements the rules.
- **§12 applies to availability answers:** "room busy" must not leak *what* occupies
  it when the other event is unreadable — show occupied/free, reveal details only
  when readable.

## Mapping to the SPA surface

- The **event sheet** (README UC-6) is the container: deep-linkable, responsive,
  scoped sections for the three axes.
- **Availability is a service, not a pane:** UC-E5 (find resource) and the matrix
  (UC-E7/E8) consume the server-side `resourceAvailability` query ([PRD 091](../prd/091-spa-reservation-edit-and-availability.md));
  UC-E6 (find slot) consumes the `freeSlots` query ([PRD 092](../prd/092-free-slot-search.md)) — the SPA must not
  compute availability client-side.
- The **matrix** is the series-planner's power view; the **calendar drag + strip**
  is the single-occurrence view; the **finder** is the search-shaped entry (kin to
  README UC-3, which is the anchor-free variant of UC-E5).
