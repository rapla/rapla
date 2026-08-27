# PRD 104 — SPA "Neu" picker: unified types + templates dialog

**Status:** in-progress — 2026-07-24
**Related:** [PRD 107](107-reservation-prototype-prefill.md) (Phase 5 `newEventOptions` is the data source; D6 defines `reservationsFromTemplate`),
[PRD 089](089-server-side-recents-favorites.md) (recents mechanism the picker adopts later),
[PRD 094](094-spa-main-view-actions-and-popups.md) (main-view actions — the "Neu" entry point lives there),
[PRD 068](068-dualis-import-wizard-redesign.md) (external event import — the second template consumer, see § Templates as import blueprints)

## Abstract

Deployments carry up to ~1000 event templates (`rapla:template` Allocatables); Swing renders a
balanced multi-level menu tree (`BalancedHierarchicalMenu`, max 25/node, alphabetic range labels).
The SPA gets ONE unified "Neu" dialog (D1/D6, discussion 2026-07-24): **search + recents + one
flat scrollable list carrying event TYPES and TEMPLATES together**, each row marked by kind —
the Swing tree existed because menus can't scroll; a dialog scrollbar removes that constraint.
The old type menu disappears; the dialog is skipped only for exactly-1-type-and-0-templates.
The server still computes a grouping `path` per template (kept on the wire for later grouped
rendering, e.g. section headers); grouping stays a server concern so Swing can adopt it later
and §12 filtering happens before any label/count is computed.

## Implementation

- **Wire shape: flat list + path tag, NOT a recursive tree** (D2). `EventTemplate` gains
  `path: [String!]!` — the server-computed group path, root first, `[]` = ungrouped top-level.
  The SPA folds the (already name-sorted) list into a tree in one pass. Precedent:
  `DocumentApi.DocumentSummary.groups` is the same flat-hierarchy-tag shape.
- **Grouping algorithm** (D3): `TemplatePathBuilder` (rapla-app, `org.rapla.server.spring.graphql`)
  — token-prefix clustering (first name token = semantic bucket, e.g. course keys like `TINF23B4`;
  oversized buckets recurse into the next token), falling back to alphabetic range chunks
  (labels like `Raumplan10 – Raumplan21`) where tokens don't split — the
  `BalancedHierarchicalMenu` mechanic (rapla-core), max 25 per node. Separators as in Swing:
  whitespace `/ - _ | : , ·`.
- Paths are computed inside `newEventOptions` **after** the §12 `canRead` filter — labels, counts
  and range buckets reflect only the caller-visible list (a bucket computed over hidden siblings
  would leak existence). Range-bucket labels depend on siblings → `path` is a snapshot of THIS
  response, never client-cacheable across lists.
- SPA: `NewEventPickerComponent` (MatDialog, `src/app/event/`) — autofocused search (AND over
  whitespace-split terms) over ONE flat scrollable list (virtual scroll): the few TYPES pinned as
  a block on top, the name-sorted TEMPLATES below; **the filter applies to both kinds** (types
  stay pinned within the filtered result). Each row carries a kind marker (icon + "Typ"/
  "Vorlage" tag). Recents chips on top: MIXED kinds, 6 entries, dedupe by kind+id (v1
  localStorage `rapla.newEventRecents`, per-user namespaced per PRD 089 D2; later swap to
  server-side recents behind the same service). No browse tree (D1 revision) — `path` is not
  consumed by the v1 UI. Pure logic in `new-event-picker-model.ts` (tier-5).
- "Neu" entry points (D6/D7): toolbar button AND grid drag-create both open the picker whenever
  more than one option exists; exactly 1 type + 0 templates skips the dialog (today's
  direct-open); 0 options ⇒ button hidden / drag inert. Type pick → scoped draft (as today);
  template pick → instantiation below.
- Instantiation (D8): `reservationsFromTemplate(templateId: ID!): [Reservation!]!` — plain
  Reservation entities (SPA selects `{ id }` only), §12 unknown/unreadable ⇒ empty list. The
  sheet's EXISTING `reservation(id:)` load path then fetches the full state — correct because
  `PermissionController.canRead(templateReservation)` delegates to `canRead(template)`
  (`PermissionController.java:623`). Client builds a FRESH draft (`draftFromTemplate`): all ids
  re-keyed (allocation restrictions remapped), `persisted=false`, template-wide date shift.
  **Target cascade = Swing parity** (`RaplaComponent.getMarkedInterval`): dragged slot >
  current calendar window date > today. v1 instantiates the FIRST reservation; the list-form
  API is the multi-reservation provision (D9).
- **Swing shift semantics for parity** (`FacadeImpl.copy`, verified 2026-07-24): the template
  classification attribute `fixedtimeandduration` = Swing's `keepTime`. It governs ONLY the
  time-of-day handling and the relative placement to the FIRST reservation: `true` → per-
  appointment DAY offset to the first reservation kept, original times kept; `false` → one
  minute-exact Duration shift for all (times follow the destination). Series: exceptions and
  open repeating ends re-anchor by day distance. Identical for a single day-aligned
  reservation — so it only matters for >1-event templates and minute-exact drag placement:
  **provided for (attribute rides the wire later), NOT implemented in v1** (D9).
- Prototype (HTML/JS, algorithm identical): claude.ai artifact `e3ee84b5` (session-local; the
  checked-in truth is `TemplatePathBuilder` + its tests).

## Templates as import blueprints (external event import)

Second template consumer (2026-07-30): the **external event import**
([PRD 068](068-dualis-import-wizard-redesign.md) — generic contract
`org.rapla.plugin.externaleventimport.ExternalEventImportService`, Dualis impl in dhbwrapla).
Today the import copies the picked template per selected item but uses only the FIRST copied
reservation (`DualisEventsLoaderImpl.createReservations` — `copies.iterator().next()`); a
multi-reservation template is silently truncated. The target model:

- A multi-reservation template is a **blueprint SET** (e.g. a "Semestervorlage" holding one
  reservation per Veranstaltung with its weekly slots). Per imported Veranstaltung the import
  picks the **matching** template reservation, so each import inherits the right
  appointments/structure — this is D9's first real consumer.
- **Matching (D10): interactive assignment, prefilled by unit + fuzzy match.** The import
  table gains an assignment column (import item ↔ template reservation), prefilled by:
  1. deterministic key — the generic `ImportItem` carries an opaque `matchKey`; a template
     reservation whose classification carries the same key wins. dhbw fills it with the
     **`Unitcode`** attribute (present on both Lehrveranstaltung and Pruefung types and mapped
     from both Dualis DTOs). NOT `dualisId` — that identifies a concrete instantiation of a
     unit, not the unit itself, so it can never pre-exist on a template.
  2. fuzzy name match — normalized token match between the import item's display name and the
     template reservation names, as suggestion only.
  0. (precondition for both) **type filter**: items match only blueprints of the SAME
     DynamicType (Lehrveranstaltung ↔ Lehrveranstaltung, Prüfung ↔ Prüfung) — mandatory
     because a unit's Vorlesung and its Prüfung share ONE Unitcode; without the filter the
     exam would deterministically mis-match the lecture blueprint. A template without
     Prüfungs-blueprints leaves exam items unmatched (manual gesture). The Studiengang needs
     no own rule — it is implicit in the per-group template RANKING (units and names are
     Studiengang-specific, so coverage selects the right template).
  The user confirms/overrides before create; matching logic lives in the GENERIC
  externaleventimport layer (the impl only supplies `matchKey` + display name).
- Instantiation semantics (shift, keepTime/`fixedtimeandduration`, re-key) stay exactly the
  D8/D9 + `FacadeImpl.copyReservations` semantics — one implementation, two consumers.

Both matching edge cases are decided (2026-07-30): unmatched items trigger a warn dialog with
"Back" / "Create anyway" (OQ3); n:1 assignment is allowed — several import items (distinct
dualisIds of the same unit) may share one blueprint, each producing its own copy (OQ4).

### Dualis-Abgleich workflow + Halde (design direction 2026-07-30)

> Implementation home since 2026-08-05: **dhbwrapla PRD 004** (`docs/prd/004-dualis-halde.md`
> in the dhbwrapla repo) — carries the H1–H5 plan, the fill-job query design with live-verified
> view semantics (per-Kurs row explosion, volumes/timings, Orga scoping), and the open
> questions. This section stays the UI/consumer-side design of record.

> **UI redesign 2026-08-05 (v2, user decision: "the SPA must not be more complicated than
> Swing")** — the dialog-based worklist below is SUPERSEDED. The calendar becomes the
> assignment surface: (1) the overview page shrinks to a **cockpit** (one row per Kurs:
> progress + open count → click opens that Kurs's week view; footer = Verwaist/Geändert
> counters; semester filter + Kurs search stay); (2) the week view gains a **Dualis tray**
> (open items of the Kurs as draggable chips) and, when a template is selected (ONE selector,
> unit-overlap-preselected, once per Kurs), the template reservations render as **ghost
> blocks** labeled with their auto-matched Dualis event. Gestures replace every dialog:
> ghost click = create that copy · "Alle übernehmen" = bulk · chip → ghost = manual
> assignment · chip → free slot = create there (details in the sheet) · chip → existing
> reservation = verknüpfen (classification merge + id stamp). Unmatched ghosts stay dashed,
> unmatched chips stay in the tray — the m×n matching is SEEN, not answered.
> **The NORMAL case is template-IMPLICIT** (refined 2026-08-05 against the real dhbw corpus):
> the bulk of the ~1500 templates are single-reservation **structure defaults named by
> convention** — `<TypeName> <Standort>/<Fakultät>/<Studiengang>` (e.g. "Lehrveranstaltung
> MGH/T/INF", "Pruefung MA/…"; 281 hits for "lehrver" alone). A chip-drop silently
> instantiates the matching structure default — picked DETERMINISTICALLY by item type +
> the Kurs's orga path, no scoring — as the base (structure/duration/rooms from the
> template, classification from Dualis, slot from the drop, `PRUEFUNGSDAUER` for exams,
> details in the sheet); name-match fallback cascade: exact path → parent path → generic
> type template → bare create. The tray shows it as a footnote ("Basis: …"), never as a
> question. Chip-onto-existing = verknüpfen, unchanged. **Semestervorlagen (multi-
> reservation) are the ONLY family the ghost/ranking machinery considers** — structure
> defaults are excluded from the ranking (they would only be noise).
> Ghosts appear ONLY when the template ranking is unambiguous (clear unit-overlap/memory
> winner above a threshold) or the user picks a template deliberately — templates are an
> accelerator for the semester-start bulk, never a required step; gestures always win over
> ghosts. The create
> dialog (template search + blueprint radios + time-source radio + mini week grid), checkbox
> bulk selection, and the per-row action buttons are all deleted. The section below is kept
> for the concepts that survive (three resolution paths, states/buckets, scope rules,
> matching cascade) — their INTERACTION is now the v2 gesture model.
>
> **Genericity (locked 2026-08-05, user decision):** the worklist is GENERIC, PRD 068 style —
> the read contract (`externalEventWorklist`: sourceName, stand, booking-rights-filtered
> groups + items) belongs to rapla's externaleventimport plugin family; the deployment
> (dhbwrapla PRD 004 Halde) provides the implementation. The SPA carries ZERO
> source-specific strings — every visible label derives from the server's `sourceName`
> ("Dualis-Import" at dhbw, hidden entirely where no impl answers = the plugin gate).
> GUI landed + LIVE-VERIFIED 2026-08-05 (Playwright against the dhbw stack, generic
> `externalEventWorklist` API + staging engine from the parallel session, 45k staged items):
> `src/app/import/` (models tier-5-tested; worklist service = the ONE wire-shape adapter;
> cockpit route `/app/import` with semester filter defaulting to the current semester,
> Kurs search, virtual-scrolled group rows), sidenav entry with compact open badge
> ("33k"), deep-link `?importGroup=<allocatableId>` (preserved through the landing
> redirect) opening the virtual-scrolled import tray beside the view — draggable chips
> with payload type `application/x-rapla-import-item`. STILL MISSING: the drop layer +
> ghosts (blocked on the generic bind/create/ignore mutations), Erledigt/Verwaist/Geändert
> lists, auto-tray from calendar selection.

The import UI is a **reconciliation worklist** ("Dualis-Abgleich", own SPA route, plugin-gated),
not a wizard: it permanently shows every staged external event without a rapla counterpart,
grouped by hierarchy node (Kurs; CAS events group under their Studiengang), with per-group
progress and a **quick search over the Kurs/group names** (a planner may have many bookable
Kurse): a query matching a group name shows that whole group expanded; otherwise it filters
by event name/unitcode. Three resolution paths per entry: **Verknüpfen** (bind to an EXISTING reservation of
the group without an external id — suggested via I1/D10 matching, executed over the existing
`syncClassification` path; the candidate set is ALL not-yet-bound reservations of the group in
the semester window, searchable, ranked suggestions pinned on top — same scaling pattern as
the template selector, never a short hard-coded suggestion menu), **Aus Vorlage erstellen**
(single → auto-match + prefilled sheet; bulk selection → the D10 assignment matrix → batch
create; additionally a per-GROUP action "Kurs abgleichen…" takes ALL open entries of a group
into the matrix at once — the template is PRE-SELECTED per group via the **unit-overlap
score**: the template whose blueprint unitcodes cover the most open items wins, shown as
"deckt X von Y ab". Templates carry NO Kurs, and the Kurs↔template association changes every
semester (a Kurs advances through the curriculum; a first-semester Kurs is brand new) — so
Kurs is the WRONG key for template selection and for the assignment memory. Unit is the
stable key: I1 memory is keyed `unit → (template-family, blueprint)`, which survives semester
rollover and covers new Kurse automatically (unit overlap needs no history at all). When NO
units are present in the templates, the fallback is a **fuzzy name RANKING**: score per
template = aggregated best name similarity of its blueprint names vs the group's open items
("Namensdeckung X %"), presented as a ranked top list (chips under the template search) —
never a silent single pick, since many templates may qualify; `fixedtimeandduration=true` and
multi-reservation (plan-like) templates get a score BOOST, not a hard filter. Optional
later: template metadata (Studiengang + Fachsemester attributes, Phase 5 family) for
deterministic preselection; Dualis `JAHRGANG` on the Kurs could derive the current
curriculum semester), **Ignorieren** (persisted, undoable — without it the list never
reaches zero). Tabs: Offen / **Erledigt** (already-reconciled entries with their bound
reservation, actions "öffnen" + "Verknüpfung aufheben" — unbinding removes the external id
and returns the entry to Offen, the reservation itself stays; entries whose source data
changed after binding carry a **"Geändert" badge** with "Änderungen übernehmen" =
`syncClassification` re-merge, undoable, or "Verwerfen") / Ignoriert. The full
staging lifecycle table (insert/update/delete rules) lives in dhbwrapla PRD 004.
~~The Verwaist bucket (imported but gone from the source) falls out of the staging diff.~~
**Verwaist REMOVED entirely 2026-08-11 (user decision)** — see the decision note below.
I12 (push notification) later deep-links into this view.

**D12 — Halde: channel-decoupled staging store (locked 2026-07-30, user decision).** Staged
external events live in the shared store as `ExternalSyncEntity` rows (`externalSystem=DUALIS`,
own staging context, `data` = sourceData JSON, `raplaId` = bound reservation or empty = OPEN —
the worklist is `raplaId IS NULL`; binding later refined to DERIVED via
`tryResolveExternalId`, see dhbwrapla PRD 004). The Abgleich UI reads ONLY the Halde and
never knows the fill channel. Fill side (revised 2026-08-05, dhbwrapla PRD 004 § Fill side):
**v1 = periodic full reconciliation every ~20 min** (the only path allowed to infer
"gone"); **later** a Dualis change notification adds live latency, treated as
invalidation-only (targeted re-read, never a pushed payload). The formerly planned
admin-triggered manual refresh is **dropped** — obsolete at that cadence. The reconciliation diff yields new/changed/gone → the buckets;
multi-pod-safe and SPA-visible via the update history; the view opens without a campusnet
round trip and shows "Stand: vor X min" from the job's meta row.

**Scope = booking rights (locked 2026-07-30, user decision):** the worklist shows ONLY events
of Kurse/Studiengänge the caller may BOOK (allocate right on the group's allocatable), not
merely read — stricter than the §12 read floor.

**Time/day determination (2026-07-30, refined after prototype review):** a list view cannot
place events, so the create dialog carries an explicit **time source**: (a) **weekday + time
of day** of the matched blueprint, re-anchored into the CURRENT semester — never the
blueprint's stored dates (templates may stem from last year); this is exactly the
`fixedtimeandduration`/keepTime semantics of D8/D9 (series length may derive from
`SOLLSTUNDEN_SEMESTER`); or (b) **im Kalender platzieren** — hand over to the group's
calendar / a slot picker, mandatory when no blueprint matches. Multi-appointment blueprint
reservations ride WHOLE: all series are copied, relative day/time offsets kept; manual
placement anchors the FIRST appointment, the rest keep their distance. The template match
must be VISIBLE in the dialog (template + blueprint row + all slots + hours plausibility),
never a silent auto-pick — and the template selector must scale to admin visibility
(hundreds of templates → searchable, same pattern as the "Neu" picker, not a dropdown).

Open: reverse re-bind of an already-bound reservation (correction case), Halde
retention/cleanup per semester.

### Import UX v3 — "Dualis-Sync: Ein Dialog + Parkstreifen" (design CONFIRMED 2026-08-11)

> Status: design direction confirmed by the user 2026-08-11 ("sieht sehr gut aus") after
> prototype iteration (type-split templates, per-row verknüpfen/neu proposals, checkbox =
> participation semantics). STILL no teardown of the built v2 surface (tray, chips, badge
> button, cockpit, `?importGroup`) — user decision 2026-08-10: "baue noch nichts zurück";
> the teardown decision falls separately once v3 is usable end-to-end.
>
> **Implementation 2026-08-11 (live-verified):** v2 teardown EXECUTED (user go): tray
> component + chip drag payload + `?importGroup` deep link deleted; cockpit shrunk to the
> status page (Stand + semester + summary line + Erledigt/Geändert/Verwaist lists). Built:
> `ImportSyncDialogComponent` — the "Dualis-Sync ⑬" button in week/day views (scope ≡
> selection, semester ≡ scope service) opens the dialog with tabs Offen (template search
> over `newEventOptions` + type-grouped checkbox list, real worklist data) / Verknüpft /
> Auffällig. "Übernehmen" is honestly disabled pending the server building block
> (`createFromStagedItems`); bind proposals, plan option, Parkstreifen and the Kurs
> counters in the resource list follow with the deployment API (parked semantics + semester
> heuristic in dhbwrapla, both to be concretized there). Prototype: artifact `0ce323e9`
> (v3 label). Driver: the v2 surface grew to seven UI concepts with zero create capability —
> objectively more complex FOR THE USER than Swing's one menu + one dialog.

**Design principle:** *create real reservations as early as possible; everything after that
is the normal calendar.* Swing never had a matching UI — template pick, checkbox table,
create, refine in the editor. v3 keeps that shape and upgrades only the refine step:
visual placement instead of editor typing. No new gestures, no new object kinds — after
"Anlegen" everything IS a reservation and the existing move/resize/sheet machinery applies.

**Complete user-visible surface (the whole feature, by design):**

1. **Offen-Zähler am Kurs** in the left resource list ("STG-TINF23B ⑬") — bookable Kurse
   only, scoped to the semester of the visible week (so "13", never "33k"). This replaces
   the cockpit as the working overview; no separate page on the create path.
2. **Ein Button** — **"Dualis-Sync ⑬" in the TOOLBAR next to "Neu"** (corrected 2026-08-11
   after first real use; originally placed inside the week pane, which violated the shell's
   layout grammar — global creator actions live in the toolbar — and scrolled/vanished with
   the pane). Visible whenever the current selection contains bookable groups with open
   items, in any render mode. Context = selection, semester = scope; no own search, no
   semester dropdown.
   **Post-create flow (corrected same day — the deferred Parkstreifen had silently amputated
   the after-the-click half of the design):** after Übernehmen the app switches to WEEK mode
   and refreshes — the created events appear stacked at the default slot, immediately
   movable (the v1 stand-in for the Parkstreifen until the parked API lands). And the
   create REGISTERS UNDO in the standard toast (PRD 094 rule: every mutating action):
   inverse = `deleteReservations(createdIds)` — deleting removes the external-id stamp with
   the reservation, so the derived binding automatically reopens the staged items.
   Design lesson recorded: prototypes validated interaction but never EMBEDDING (toolbar
   grammar, post-action flow, undo system) — integration properties need a pass against the
   real shell, and deferring a design piece requires re-checking the whole flow.
3. **Ein Dialog**, Swing-shaped:
   - *Vorlage*: the EXISTING Neu-picker mechanics (search + recents), preselected by the
     name convention (type + orga path, e.g. "Lehrveranstaltung STG/T/INF"); Semestervorlagen
     appear in the same list, annotated "Semesterplan · matcht 8 von 13".
   - *Veranstaltungen*: the open staged items as a checkbox list (all preselected,
     master toggle) — Swing's table, slimmed. **Grouped by type** ("Veranstaltungen (10)" /
     "Prüfungen (3)"); the visible template selector applies only to ITS type, the other
     type resolves its own name-convention default automatically, shown as an overridable
     footnote on that section header ("Vorlage: Pruefung STG/T (automatisch)") — the D10
     type filter as UI rule: one selector visible, one template EFFECTIVE per type, never a
     lecture template on an exam. A Semestervorlage may carry both types; the type filter
     keeps exam items matching only exam blueprints despite the shared Unitcode.
   - *Option (only when a Semestervorlage is selected)*: "An Vorlagen-Zeiten platzieren
     (Auto-Match per Unit/Name)" — matched items are created AT the plan times, the rest
     goes to the Parkstreifen. The whole assignment matrix collapses into
     create-then-correct-in-the-calendar.
   - *Per-row ACTION proposal* (2026-08-10 — handles weeks already built by hand): each
     open item is matched against the Kurs's EXISTING unlinked reservations in the
     semester window (D10 cascade: type filter → Unitcode if the manual event carries one →
     fuzzy name). Match → row tag "→ verknüpft mit ‚<event · slot>'" (bind: classification
     merge + id stamp, NO duplicate); no match → tag "→ neu" (create per template/plan/
     Parkstreifen). Tag click overrides (pick another candidate / force new). Resolves
     OQ-v3-2: bind-to-existing IS in scope, but as a per-row proposal inside the ONE
     dialog — needs `bindStagedEvent` from the mutations contract (create-only can ship
     first).
   - Footer states the split honestly: "13 übernehmen · 5 verknüpfen · 8 neu".
   - *Overview tabs* (2026-08-11 — brings the original cockpit concepts back per Kurs):
     the dialog carries three tabs — **Offen** (the sync content above), **Verknüpft**
     (already-reconciled entries with their bound slot + actions öffnen/aufheben),
     **Auffällig** (Geändert — ~~+ Verwaist~~ removed 2026-08-11, with übernehmen/öffnen). One entry point
     answers every per-Kurs Dualis question. The GLOBAL overview across Kurse stays the
     (already built) cockpit status lists; placed events additionally carry their ✓ marker
     in the calendar blocks.
4. **Parkstreifen** ("Noch nicht platziert") above the week grid: the created-but-unplaced
   reservations as compact blocks; dragging one into the grid is the NORMAL move gesture.
   Strip disappears when empty.

Nothing else. ~~The cockpit shrinks to an admin status page~~ **REMOVED 2026-08-11 (user
decision, relayed via halde session): the global overview panel (`/app/import`, the
cockpit) is torn down entirely** — "auf Dualis-Sync-Ebene bleiben". Rationale: the panel
was a number, not a task list (no trigger, work happens at the Kurs); anyone wanting such
a display builds it per template editor over GraphQL (PRD 074). In the same sweep the
worklist read went **Kurs-scoped**: `externalEventWorklist(allocatableIds: [ID!]!,
scopeKey: String)` — no "everything I may book" mode anymore; the SPA passes the selected
resource-chip ids and reloads on chip change. ~~Verwaist is parked as a future topic~~
**Verwaist REMOVED from the whole Halde concept (2026-08-11, user decision):** rows leave
the Dualis export routinely when they are no longer current (export window covers only the
running semesters) — "gone from the source" is the normal end of life, not a drift signal,
so a Verwaist state is noise and the removal simplifies staging. SPA side done same day
(no vanished bucket/tab labels, `vanishedSince`/`vanishedCount` dropped, ORPHANED wire
rows filtered out until the server drops the state); server/staging removal (ORPHANED
state, `counts.orphaned`, `vanishedSince` in schema + assembler, dhbwrapla staging) is
with the halde session. Consequence: bindings whose source vanished are simply no longer
listed — deliberate, there is no reliable "deleted in Dualis" signal to distinguish from
"outside the export window".

**Verknüpft redesign (2026-08-11, same day, user go):** the Halde is now by definition
volatile ("not in the snapshot ⇒ row deleted"), so it is the wrong source for anything
durable. New split — **Halde = what Dualis currently says** (Offen + geändert markers),
**reservations = what is durably bound** (the `externalid` annotation):
- New generic GraphQL field `Reservation.externalId` (rapla-app StructuralTypeFetchers,
  reads the `externalid` annotation; §12-safe: hangs off the already canRead-filtered
  `reservations(filter:)` query; tier-3 test `reservationExternalIdExposesTheAnnotation`).
- SPA Verknüpft tab, 🔗 chip markers and the badge count come from a
  `reservations(filter: {from,to,allocatableIdsIn})` query filtered to `externalId != null`
  (`ImportWorklistService.linked`). Semester display derives from the reservation's
  `firstDate` — fixes the wrong-semester display the Halde `scopeKey` produced.
- **"nicht mehr im Export"** is a client-side JOIN, not a state: stamped reservation with
  no Halde row carrying its `boundReservationId`. Join safety: the worklist loads
  **unscoped** (no `scopeKey` — both axes must cover the same set); the Offen tab narrows
  to the visible semester client-side (`openItemsOfGroups(…, semester)`).
- **Auffällig tab removed** — after the Verwaist removal it only carried "geändert",
  which the Verknüpft tab now shows inline (warning icon + "geändert seit").
  The bulk **"n ‚geändert' verwerfen" button was removed too (user, 2026-08-12)**:
  its count mixed two semester notions (Halde `scopeKey` vs reservation `firstDate`)
  and the marker has no attached action yet ("Änderungen übernehmen" doesn't exist) —
  dismissing was bookkeeping without benefit. Server mutation `dismissStagedChanges`
  stays (halde's); re-wire per-row when the übernehmen flow lands.
- **Sync button always visible when a source is deployed** (user: "ansonsten 0/0
  anzeigen"): visibility = metadata `sourceName` non-empty; empty selection/semester
  shows `0/0`; a failed load shows `!` (never masquerades as 0/0 — lesson from the
  silent stale-bundle incident the same evening). Vanilla deployments (no source) keep
  no button.
- **Binding discriminator sharpened (2026-08-12, holidays bug):** the bare `externalid`
  annotation is NOT proof of a source binding — the iCal import stamps UIDs (holidays)
  without sync entities, and the operator's `tryResolveExternalId` index is annotation-fed,
  so it can't discriminate either. The truth is the `ExternalSyncEntity` row of the
  deployment's system id: new SPI default `ExternalEventImportService.getExternalSystemId()`
  (dhbw → "DUALIS") + generic query `externalEventLinkedReservationIds(reservationIds)`
  (§12 silent-drop, tier-3-tested incl. the annotation-only negative case); the SPA chains
  its linked candidates through it.
- SPA-dead code removed in the same sweep: `createFromStagedEvents` service method (the
  editor-flow replaced bulk create; the SERVER mutation still exists — removal to be
  decided), `available` signal, `WorklistStand`/`Worklist.sourceName`, `itemsOfBucket`/
  `StatusBucket` (→ `changedItems`), `ALL_SCOPES`.

**Data model "geparkt" — SUPERSEDED 2026-08-11 (user decisions, same day):** first the
marker event was rejected in favor of a deployment "parked API"; then the user relaxed the
requirements further — parked state may be **invisible to other users** and should NOT
persist across reloads (a persistent store would leak stale state into later sync runs).
Result: **client-only, in-memory** — `ParkedEventsService` (signal, session-lifetime)
collects the created ids; the week grid renders those rows in a Parkstreifen strip above
the grid (IMPLEMENTED 2026-08-11: strip chips drive the NORMAL armMove machinery, drop =
scoped move, first placement or undo un-parks; F5 simply drops the strip and the events
sit movable at Mo 08:00). No deployment parked API needed — removed from the contract.

**Parken/Platzieren — FINAL model (user rules 2026-08-11, third iteration):**
1. **Parken speichert NICHTS.** Übernehmen im Sync-Dialog legt keine Reservierungen an —
   es füllt nur den client-seitigen Parkstreifen (`ParkedEventsService`: sourceId + Name +
   aufgelöste Template-Ids). Kein Undo-Eintrag (es gibt nichts zurückzunehmen); Reload
   oder der Abbrechen-Knopf im Streifen verwerfen folgenlos, die Halde-Items bleiben OPEN.
2. **STANDING RULE — ein Drop speichert NIE direkt, und der Review-Schritt ist der
   VOLLE Reservierungseditor** (zweimal verletzt + einmal eigenmächtig durch einen
   Minimal-Dialog ersetzt am 2026-08-11 — alle drei vom User zurückgewiesen; ein
   Ersatz-Design braucht IMMER Rückfrage). Konkret: Drop auf freien Slot → die
   Vorlage wird CLIENTSEITIG instanziiert (`TemplateInstantiationService`, gleiche
   Mechanik wie der „Neu"-Picker; Zeiten vom Slot via `PlacementTarget`, Kurs-
   Allocations via `draftWithGroups`) → der Event-Editor öffnet mit dem
   UNGESPEICHERTEN Entwurf → Speichern legt normal an (`createReservation`,
   D3 id-first: die Draft-Id ist die Reservierungs-Id) → danach ruft die SPA
   `bindStagedEvent(sourceItemId, draftId)` — Stempel + Dualis-Felder kommen aufs
   frisch gespeicherte Event. Abbrechen im Editor lässt den Chip geparkt. Ohne
   aufgelöste Vorlage: Hinweis-Snackbar, Item bleibt geparkt. Einschränkung: Dualis-
   Personen-Zuordnungen kommen erst mit dem späteren Attribut-Sync, nicht beim Bind.
3. Drop auf einen BESTEHENDEN Kalender-Chip → Verknüpfen-Bestätigung (`bindStagedEvent`,
   Felder-Überschreib-Warnung); kein Duplikat zu löschen, da nichts vorab angelegt war.
4. Kalender-Chips verknüpfter Events tragen einen 🔗-Marker (`linkedIds` aus
   `boundReservationId`); der manuelle Pick-Modus („verknüpfen…" an der Offen-Zeile →
   Banner → Chip-Klick) und der Server-Vorschlag (`bindCandidates`, nie Auto-Match)
   ergänzen die Drag-Geste.

**Server needs (single new building block):** batch create from staged items —
`createFromStagedItems(sourceItemIds, templateId, placement)` returning the created
reservation ids (server does mapping + template copy + external-id stamp in ONE
transaction; placement = PLAN_TIMES | PARKED). Everything else (worklist read, states,
reconciliation) exists. The v2 gesture mutations (bind/ignore/…) stay in the contract for
the status lists, but are NOT needed for the v3 create path.

**Matching in v3:** only the plan option uses it (unit exact > fuzzy name, type-filtered —
D10 cascade unchanged); wrong matches are corrected by MOVING the created event, not by a
matrix. I1 memory/I2 write-back plug in behind the option later without UI change.

**Decision criteria v2 vs v3 (to be evaluated with real use):** number of UI concepts on
the create path (v3: 4 vs v2: 7+), taps from "Kurs offen" to "alle angelegt" (v3: 3),
does placement-by-drag beat chip-drag in practice, does the Parkstreifen model (a/b) hold
up against conflicts/exports.

**Open questions:** ~~OQ-v3-1 parked data model~~ RESOLVED 2026-08-11 → deployment API,
no marker event (see above); ~~OQ-v3-2 bind-to-existing~~ RESOLVED → per-row proposal in
the one dialog; OQ-v3-3 where exactly the Kurs counter renders in the resource list rows.

### Staging mutations — consumer contract (GUI requirements, 2026-08-10)

What the v2 gestures need from the generic staging engine (rapla GraphQL, same genericity
rule as the read API; every operation authorizes via BOOKING RIGHT on the item's group
allocatable, unknown ≡ forbidden):

| Operation | Semantics | GUI gesture it serves |
|---|---|---|
| `bindStagedEvent(sourceItemId, reservationId)` | Merge the staged item's mapped classification into the EXISTING reservation (the PRD 068 `syncClassification` merge — editor-undoable) and stamp its external id. Fails when the reservation already carries a different external id of this system. → state LINKED | chip → existing block ("verknüpfen") |
| `unbindStagedEvent(sourceItemId)` | Remove the external-id stamp from the bound reservation (reservation itself stays untouched). → state OPEN | Erledigt list "Verknüpfung aufheben" (correction case) |
| `applyStagedChanges(sourceItemId)` | Re-run the classification merge into the bound reservation, clear `changedSince`. → CHANGED → LINKED | "Änderungen übernehmen" badge action |
| `ignoreStagedEvent(sourceItemId)` / `restoreStagedEvent(sourceItemId)` | Set/clear `ignoredSince`. → IGNORED ↔ OPEN | tray/list "Ignorieren" + Ignoriert list |

**Deliberately NOT a mutation — the create path:** chip → free slot builds a CLIENT draft in
the event sheet (v2 model); saving goes through the normal reservation save, and the
deployment's existing dispatch processor stamps the external id when the saved
classification carries the source id (dhbw: `DualisImportEventsPrePostDispatchProcessor`).
What the GUI needs for that is READ data, not a mutation:

- `stagedEventDraftData(sourceItemId)`: the deployment-mapped classification (typeKey +
  values, the same mapping `createReservations` does server-side today), the resolved
  Kurs/Person allocatable ids, and (later, matching phase) the suggested basis template /
  blueprint. Without it the chip drop can only prefill the display name.
- Read-API addition: `ExternalEventItem.boundReservationId` — needed for "Veranstaltung
  öffnen" in the Erledigt/Verwaist lists and as the bind-target sanity check.
- Read-API addition (2026-08-11, user idea): **template-matching RULES live deployment-side**
  — DONE 2026-08-11: SPI `ExternalEventTemplateResolver` (rapla-core) + GraphQL query
  `externalEventDefaultTemplates(groupIds)` (rapla-app, null when no deployment resolver) +
  dhbwrapla `DualisDefaultTemplateResolver` (§12 canRead-filtered); the SPA interim heuristic
  is DELETED — the dialog only displays the server answer. Rules derived from the real
  template corpus (1474 templates, 281 LV / 255 Prüfung): dominant `<Typ> <Standort>/<Fak>/<Fach>`
  with multi-letter faculties at CAS (`CAS/TM/INF`) → exact cascade tries EVERY split of the
  Kurs code (corpus decides the segmentation), separator variants (`FN-T-INF`) normalized,
  then LCS fuzzy with type-prefix bonus. Known unresolved corpus shapes (need stored
  per-deployment mapping rules, the planned extension): bare-code templates (`Lehrveranstaltung AG`),
  color variants (`blau MOS/W/HD`), multi-word sites (`DHBW virtuell/T/INFO`), `Extern (DAA)`.
  Rules can then also feed the Semesterplan ranking (I1 memory's natural home).
- Read-API addition (2026-08-11, user decision): **semester/scope resolution is a
  DEPLOYMENT heuristic, not a client rule** — semester boundaries differ per Studiengang.
  Signature (locked 2026-08-11): **input = the selected date + the selected resource
  (allocatable) ids; output = ONE semester (scopeKey)** — e.g.
  `resolveScope(date, allocatableIds): String` on the deployment SPI in dhbwrapla,
  initially a simple heuristic, exchangeable later (per-Studiengang semester calendars)
  without any SPA change. The SPA calls it with the visible week's date + the current
  selection and feeds the returned semester into the worklist query. The SPA's own
  `currentSemester()` date rule is an INTERIM until this lands; the explicit scopeKey
  argument stays for the cockpit's manual semester choice.

Undo model: bind/apply are editor-undoable via the classification-merge command;
unbind/ignore/restore are their own inverses — the GUI offers snackbar-undo by calling the
inverse operation, no server-side undo stack required.

### Import ideas backlog (brainstorm 2026-07-30, user-rated)

**Promising (user-confirmed):**
- **I1 — Zuordnungs-Gedächtnis:** persist confirmed `unit → blueprint` assignments server-side;
  next semester's prefill is history, not heuristic. Keyed by UNIT, never by Kurs — the
  Kurs↔template association rotates every semester and new (first-semester) Kurse have no
  history; units recur across years and Kurse.
- **I2 — Unitcode-Rückschreibung:** on manual assignment, offer to stamp the Unitcode onto the
  template reservation (needs `canModify` on the template) — the template maintains itself.
- **I11 — Reconciliation buckets, incl. AFTER-the-fact matching:** unassigned Dualis events must
  be matchable later against EXISTING reservations (bind + dualisId stamp via the
  `syncClassification` path), not only at create time. ~~Plus the today-missing orphan bucket
  (imported but gone from Dualis → cancellation candidate)~~ — orphan/Verwaist rejected
  2026-08-11 (export-window noise, see decision above).
- **I12 — Push (scheduled diff → notification with deep link):** promising, but requires a
  process that STARTS from events, not from Kurs selection — see the Abgleich workflow below.

**Recorded, unrated:** fuzzy abbreviation dictionary (+learned synonyms), Dozent as secondary
match signal, Prüfung↔Vorlesung coupling (same unit, `PRUEFUNGSDAUER` as duration),
series length derived from `SOLLSTUNDEN_SEMESTER` (slot pattern × weeks → suggested series
end), per-unit hours budget display, calendar ghost dry-run preview before create,
conflict pre-check in the assignment step, multi-Kurs bulk (one unit row × Kurs checkboxes),
"wie letztes Semester" (previous semester's real reservations as blueprint set).

**Data facts (verified live over the campusnet tunnel + IntelliJ schema cache, 2026-07-30):**
the 11 `v_bee_RAPLA_*` views visible to the service login carry ZERO date/time columns —
times come exclusively from templates/planner. Time-adjacent columns: Veranstaltung
`SOLLSTUNDEN_SEMESTER` (float, target hours), Prüfung `PRUEFUNGSDAUER` (minutes) + `RUNDE`.
CAS events attach to Studiengang, not Kurs → hierarchy scope must be selectable at any level.

## Goal

From the "Neu" menu a user finds one template among 1000 in ≤ 3 interactions: typing two search
tokens, OR scrolling the alphabetic list, OR one recents chip. GraphQL probe:
`{ newEventOptions { templates { id name path } } }` returns §12-filtered templates with
server-computed paths; no group label/count reflects an invisible template.

## Scope

### In scope
- `EventTemplate.path` + `TemplatePathBuilder` (token clustering + range fallback).
- SPA "Neu" menu on `newEventOptions`; template picker dialog (search + flat scroll list);
  localStorage recents.
- `reservationsFromTemplate` + sheet prefill (single-reservation v1).

### Out of scope
- Grouping steering config (separator convention, `maxPerNode`/`minGroupSize` knobs, strategy
  switch, category attribute on `rapla:template`) — the expansion stage, see OQ1.
- Multi-reservation template instantiation from the PICKER (batch create — PRD 107 D6 keeps it
  deferred); the import-side multi-reservation consumer is in scope via
  [§ Templates as import blueprints](#templates-as-import-blueprints-external-event-import) / Phase 6.
- Swing adoption of the server-computed paths.
- Server-side recents for templates (PRD 089 extension; picker v1 uses localStorage).
- Template management UI in the SPA (create/edit/delete templates stays Swing).

## Plan

### Phase 1 — Server: `path` on EventTemplate — DONE 2026-07-24
- [x] `TemplatePathBuilder` (pure, tier-1-testable): token-prefix clustering, recursion into next
      token for oversized buckets (redundant single-token levels are skipped), alphabetic range
      chunks where tokens don't split, max 25/node.
- [x] Schema: `EventTemplate.path: [String!]!`; computed in `newEventOptions` after §12 filter.
- [x] Tests: `TemplatePathBuilderTest` (5 — grouping semantics, no-template-lost invariant),
      `NewEventOptionsGraphQLTest` extended (path on the wire).

### Phase 2 — SPA: "Neu" menu on newEventOptions — DONE 2026-07-24 (types half)
- [x] `NewEventOptionsService` (session-shared `ensureLoaded()`, `shareReplay`, error-retry;
      tier-5 spec) feeds BOTH create entry points: the toolbar "Neu" menu (0 types now hides the
      button — previously fell back to a hardcoded 'event') and the view-host drag-create
      (`openWithEventType`, previously picked the first RESERVATION type without canCreate).
- [ ] "Aus Vorlage…" menu entry — lands with Phase 3 (needs the picker dialog to open).

### Phase 3 — SPA: unified "Neu" picker dialog — DONE 2026-07-24
- [x] `NewEventPickerComponent` (MatDialog): types pinned + templates, kind markers (icon +
      "Typ"/"Vorlage" tag), search filters both, mixed recents (6, `rapla.newEventRecents`,
      healed against the live list), Enter = first hit, cdk-virtual-scroll.
- [x] Toolbar "Neu" opens the dialog (skip only for 1 type + 0 templates); type menu removed.
- [x] Drag-create (month range + week time-range) opens the dialog when >1 option (D7); the
      dragged slot becomes the placement target; tier-5 specs for model + placement math
      (`new-event-picker-model.spec.ts`, 8 green).
- [x] Browser-verified against the dhbw dataset (1474 templates, 0 types): picker, search,
      recents chip, virtual scroll.

### Phase 4 — Instantiation — DONE 2026-07-24
- [x] Server: `reservationsFromTemplate(templateId: ID!): [Reservation!]!` (D8; §12
      unknown/unreadable/non-template ⇒ empty list) — `ReservationsFromTemplateGraphQLTest`
      (3 green: readable, hidden-vs-unknown identical, admin).
- [x] SPA: `TemplateInstantiationService` — template pick → first reservation id → existing
      `EventDataService.load` → `draftFromTemplate` (re-key, shift per target cascade) → event
      sheet prefilled (isNew). GraphQL errors are distinguished from empty templates (separate
      snackbars). User-verified live 2026-07-24 ("funktioniert sehr gut").

### Phase 4b — Documentation — DONE 2026-07-24
- [x] [`docs/architecture/event-templates.md`](../architecture/event-templates.md) — the
      `rapla:template` model (template allocatable + annotated reservations, permission
      delegation `canRead(reservation)→canRead(template)`), the Swing instantiation semantics
      (`copyReservations`, `fixedtimeandduration`/keepTime, target cascade), the SPA picker
      behavior (D6-D9) and the "Vorlagen vs Mustache document templates" naming trap
      (cross-linked with `docs/templates.md`).

### Phase 5 — Expansion stage: grouping steering (deferred)
- [ ] Config blob (`org.rapla.template.picker`): `maxPerNode`, `minGroupSize`, separators,
      hierarchy-separator convention (explicit name paths like `TINF23 / Mathe`), strategy switch.
      Category attribute on `rapla:template` stays a recorded option (schema change + migration).

### Phase 6 — Import matching: multi-reservation templates in the external event import (design 2026-07-30)
- [ ] Generic contract: `ImportItem.matchKey` (opaque, impl-supplied); matching + prefill logic
      in the generic externaleventimport layer (deterministic matchKey > fuzzy name suggestion).
- [ ] Dualis impl: fill `matchKey` from `Unitcode`; template reservations matched via their
      `Unitcode` classification attribute.
- [ ] Import UI: assignment column (item ↔ template reservation), prefilled, user-overridable;
      `createReservations` copies the ASSIGNED template reservation per item (replaces the
      first-copy truncation).
- [ ] Unmatched-items warn dialog ("Back" / "Create anyway", OQ3); n:1 assignment with per-item
      copies + count badge (OQ4).

## Tests

Tier 1: `TemplatePathBuilderTest`. Tier 3: `NewEventOptionsGraphQLTest` (§12 + path).
Tier 5/6: picker fold/filter/recents specs. Live probe: dev server with seeded templates,
`{ newEventOptions { templates { name path } } }`, then picker flow to a prefilled sheet.

## Open Questions

- **OQ1 — expansion-stage trigger.** Which deployment signal promotes Phase 5 from deferred to
  active — dhbw template naming breaking the token heuristic? *Resolution:* pending; revisit when
  a real template corpus is available.
- **OQ3 — unmatched import items.** What happens to an import item with no assigned template
  reservation? *Resolution:* RESOLVED 2026-07-30 (user decision) — create shows a **warn
  dialog** listing the unmatched items, with "Back" (return to the assignment table) and
  "Create anyway". Open sliver: what "create anyway" produces for the unmatched items
  (proposed: today's no-template branch — bare reservation with a default appointment).
- **OQ4 — one blueprint, many items.** May one template reservation be assigned to several
  import items (same unit, several Kurse)? *Resolution:* RESOLVED 2026-07-30 (user decision) —
  **allowed, one separate copy per item.** Forced by the id model: each created Veranstaltung
  carries exactly ONE `dualisId`, and the n items are n distinct dualisIds of the same unit —
  a single merged reservation is impossible, so per-item copies are the only correct shape.
  The assignment UI should make the n:1 assignment visible (e.g. a count badge on the
  blueprint row).
- **OQ2 — drag-create × templates.** Should week/month drag-create ever offer templates (chooser
  after drag)? *Resolution:* RESOLVED 2026-07-24 → D7 (picker opens whenever >1 option exists;
  template pick lands on the dragged slot).

## Decisions locked

**D1 — Picker UX = search + recents + ONE flat scrollable list; no browse tree** (revised
2026-07-24, user decision; supersedes the earlier "prototype variant C with browse tree" pick).
Rationale: the Swing multi-level tree only existed because menus can't scroll — a dialog
scrollbar + search + recents covers both "I know the name" and "I don't" without drill-down
clicks. The server-computed `path` stays on the wire (D2-D4 unchanged) for later grouped
rendering (e.g. sticky section headers), but the v1 UI does not consume it. Rejected: tree as
default (interaction tax), dropping `path` from the wire (already shipped + tested, and the
grouped rendering option should stay open).

**D2 — Flat list + `path` tag instead of a recursive GraphQL tree.** GraphQL cannot select
unbounded recursion — a `templateGroups { groups { … } }` shape forces a depth cap and silent
flattening. A per-template path serves search AND browse from one payload, folds to a tree in one
client pass, and matches the existing `DocumentSummary.groups` shape. Cost: range-bucket labels
are sibling-dependent → path is response-snapshot-only, never cross-list cacheable.

**D3 — v1 groups with the known algorithm, config comes later.** Token-prefix clustering + the
Swing `BalancedHierarchicalMenu` range fallback ships hard-wired (Swing's constants: 25/node,
same separator set). All steering (knobs, separator convention, category attribute) is Phase 5 —
zero admin action required for a useful v1.

**D4 — Grouping is computed server-side.** One implementation (locale collator, §12-filtered
input), reusable by Swing later; the client never re-derives groups. Rejected: client-side
grouping (duplicate logic, §12 count leaks harder to reason about).

**D5 — Recents v1 = localStorage** (per-user namespaced, PRD 089 D2 pattern), swap to
server-side recents later behind the same service interface. Rejected for v1: extending the
PRD 089 server surface now (blocks the picker on an unrelated API change). **6 entries**,
mixed kinds (types + templates), dedupe by kind+id (locked 2026-07-24).

**D6 — ONE unified "Neu" dialog for types + templates** (2026-07-24, user decision): the type
menu disappears; the dialog lists TYPES pinned as a block on top and TEMPLATES below, each row
kind-marked, and the search filters BOTH kinds (types stay pinned within the filtered result).
Skip the dialog ONLY for exactly 1 type + 0 templates (direct-open); 0 options hides the
entry point.

**D7 — Drag-create opens the picker whenever >1 option exists** (2026-07-24, user decision;
resolves OQ2). Swing parity: Swing pops the wizard menu after a drag. The single-option case
stays click-free (type → seeded draft directly). A template picked after a drag instantiates
onto the dragged slot.

**D8 — Instantiation reuses the Reservation read path, no prototype wrapper** (deviation from
PRD 107 D6's letter, same spirit): `reservationsFromTemplate` returns plain `Reservation`s;
the client builds the fresh draft (re-key, shift, `persisted=false`). Sanitization via
selection-set + client draft construction — no server-side strip step, because the caller may
read these reservations anyway (§12 via template canRead). Target cascade per Swing:
dragged slot > current window date > today.

**D9 — Multi-reservation templates: provided for, not built** (2026-07-24, user decision).
The list-form API and the recorded `fixedtimeandduration`/keepTime semantics are the
provision; v1 instantiates the first reservation only. The eventual multi instantiation is
"just" a batch create + a special editor view over n drafts — designed when needed.

**D10 — Import matching = interactive assignment, prefilled by unit key + fuzzy name**
(2026-07-30, user decision — see § Templates as import blueprints). Deterministic prefill via
the generic `ImportItem.matchKey` (dhbw: the `Unitcode` classification attribute; explicitly
NOT `dualisId`, which names a concrete instantiation of a unit and can never pre-exist on a
template), fuzzy name match as secondary suggestion, user confirms/overrides in the import
table before create. Matching lives in the generic externaleventimport layer. Rejected:
silent heuristic-only matching (invisible mis-assignments), key-only matching (maintenance
burden, fails on templates without the attribute).
