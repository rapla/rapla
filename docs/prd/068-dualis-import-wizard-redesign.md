# PRD 068 — External-event (Dualis) import: server maps to classification, client uses the generic template flow

**Status:** in-progress — opened 2026-06-10 (design resolved in a late-night session
against the old productive dialog + the existing generic template wizard).
Sync flow **implemented + verified live 2026-06-12** (Swing client against the
dhbw dev server + campusnet tunnel: load, single-row sync, in-place classification
update, undo/redo, edit-window-scoped busy glasspane all confirmed working): `syncClassification` contract + DTOs +
controller endpoint + generic client apply (rapla), `mapSyncClassification` +
`createReservations → List<ReservationImpl>` (dhbwrapla), Jackson abstract-type
mapping deleted. Tests: `ExternalEventImportServiceContractTest`,
`SyncClassificationWireTest`, `ExternalEventImportSyncApplyTest`,
`DualisSyncClassificationTest` — all green. Create-flow polish (filters,
ImportStatus column UX) still open.

## Goal

Make the external-event (Dualis) import a thin layer over rapla's **existing
generic template-creation flow**, with the server contributing **only the
classification mapping** of the source data. No dhbw-specific code in the rapla
client; no server-side reservation construction or persistence.

## Prime directive (why the refactoring happened)

The whole point of the PRD [003](003-custom-deployments-after-spring-migration.md)/[012](012-dhbwrapla-client-migration.md) refactoring was **no dhbw-specific code in
the rapla client** — so there is no dhbw rapla client to build/ship. Master
violated this with a full dhbw Swing wizard in the client
(`DhbwImportDialog`, `DualisImportWizard`, `DualisReservationCreator`,
`DualisServerLectureData.map()`, …). Any fix must keep the client vanilla.

## The resolved design (final, 2026-06-11)

Master's flow exactly — ship the raw event data in a table, select, map on
demand, multi-edit, save — with the **one** change the refactoring needed: the
mapping moved **server-side** (so zero dhbw client code). Two server calls, one
Dualis query, entity-shipping only for the selected rows.

```
CLIENT: user picks a template
   │ loadEvents(courses)
   ▼
SERVER loadEvents:  ONE Dualis query
   per event → ImportItem {
       sourceItemId
       columns      (display: CampusnetId, Kurs, …, ImportStatus)
       imported     (bool — DUALIS_ID dedup, server-computed)
       sourceData   (Map<String,Object>: the RAW Dualis fields — the dhbw
                     Veranstaltung/Pruefung DTO round-tripped via Jackson
                     convertValue; opaque to the generic client)
   }
   returns List<ImportItem>     ← flat data, all events, NO allocatable lookups
   ▼
CLIENT table: columns + ImportStatus + filters; disable create if only imported
   selected; user selects the rows to import
   │ createReservations({ selectedItems(with sourceData), templateId, interval })
   ▼                              ↑ only selected rows go back; raw data rides along
SERVER createReservations:  NO second Dualis call
   • resolve template → getTemplateReservations(template)
   • per selected item:
       re-hydrate the DTO from sourceData (Jackson convertValue back)
       copy = copyReservations(templateRes, begin, keepTime, user)   [waitFor — see OQ]
       map DTO → classification (newClassificationFrom(template) seed + set values)
       resolve allocatables (kursIds/personenIds → rapla ids)  ← only for selected
       copy.setClassification(cls); add allocatables
   • returns List<Reservation>   ← UN-PERSISTED, only the selected few
   ▼
CLIENT: RESOLVE the reservations (setResolver vs client operator) BEFORE accepting
   → editController.edit(reservations)  → user edits  → SAVE (dispatch; processor
   stamps the external id for next-time ImportStatus)
```

**Key properties (all confirmed):**
- **One Dualis call** — `loadEvents` queries once; `createReservations` works off
  the relayed `sourceData`. No second campusnet round-trip.
- **No dhbw client code** — `sourceData` is opaque `Map<String,Object>`; the client
  only relays it back and (generic) resolves+edits the returned reservations.
- **Mapping format-agnostic** — raw Dualis fields ride the wire ("anything
  mappable, as master"); the server maps to classification at create.
- **Entity-shipping only for selected** — un-persisted reservations cross the wire
  only for the handful the user picked. ids are UUIDs (free; no sequence/gaps).

**De-risk spikes (both PASS, 2026-06-11):**
- *Editor accepts un-persisted reservations* — `AbstractCachableOperator.editObject`:
  `persistent==null` → `clone = newObj.clone()`, treated as new. The generic
  `CREATE_RESERVATION_FROM_TEMPLATE` flow already feeds `copyReservations`
  (un-persisted) into `editListAsync`.
- *Client resolution exists* — `RemoteOperator.setResolver(...)` wires deserialized
  entities to the client cache (done for every query/store today). The REST
  deserialization does **not** auto-resolve, so the externaleventimport client
  controller must call it explicitly: **resolve before accept** (hard requirement).

## Why this dissolves every symptom seen 2026-06-10

| Symptom | Resolved because |
|---|---|
| Editor never opens (cache race) | server returns the reservation **objects**; client resolves + edits them directly — never re-resolves an id from a stale cache |
| Event invisible (no allocations) | server adds course/lecturer allocations + copies the template's appointments |
| Re-import duplicates | ImportStatus (server `DUALIS_ID` dedup) in the table; only-imported selection disables create |
| Missing columns / filters | generic, server-declared metadata |
| dhbw code in the client | **gone** — server maps; client relays `sourceData` + resolves/edits reservations (generic) |
| Server persists prematurely | server returns **un-persisted**; save is the client's |
| second Dualis call | **gone** — `sourceData` relayed back; no re-query |

## Sync flow (resolved 2026-06-11 — closes OQ 4)

The "Synchronisieren" toolbar button in the reservation editor
(`ExternalEventSyncButtonExtension` → `ExternalEventImportController.syncReservation`)
binds an existing, not-yet-imported reservation to a Dualis event: same mapping as
create, but folded into the reservation already open in the editor. Master did this
client-side (`DualisServerLectureData.map` mutated the live editor object); the
redesign keeps the mapping server-side and ships **only the classification** across
the wire — never a reservation carrier, so nothing can be mistaken for storable
state and the open editor's object identity is never disturbed.

```
CLIENT sync button → loadEvents(reservation's Kurs ids)          (unchanged)
   ▼
CLIENT dialog (sync mode): exactly ONE row, NOT yet imported     (master semantic)
   │ syncClassification({ selectedItem (with sourceData),
   │                      classification: editor working copy's ClassificationImpl })
   ▼
SERVER (dhbw impl) — NO store, NO read of persisted state:
   • shipped.setResolver(operator)            (ClassificationImpl is an EntityReferencer)
   • dt = getDynamicType(sourceData.typeKey)
   • merged = dt.newClassificationFrom(shipped)   ← same seeding call as the template branch
   • applyValues(merged, sourceData.values)
   • resolve kursIds/personenIds via DUALIS_ID filter → rapla allocatable IDS (not entities)
   • return { classification: merged, allocatableIds }
   ▼
CLIENT generic apply — classification ONLY, undoable (decided 2026-06-12):
   • merged.setResolver(operator)
   • reservationEdit.changeClassificationUndoable(merged)
     → ReservationInfoEdit builds the SAME UndoReservationTypeChange command the
       type-selector dropdown uses and storeAndExecute's it on the editor's own
       undo history: one history entry, Ctrl-Z restores the pre-sync classification,
       the command repaints the classification panel itself, and the editor's
       change listener marks the window dirty. No setHasChanged, no refresh calls,
       no editor re-init (NEVER reservationEdit.setReservation(...) — it rebinds
       every panel mid-flight and clears the undo history; master did it, we don't).
   • allocatableIds are deliberately NOT applied by the Swing client. Master's sync
     added Kurs/Person allocations (same map() as create) — dropped on purpose:
     allocations are a create-flow concern; the planner curates them manually.
     The field STAYS on the wire so future UIs (SPA) can decide to use it.
   ▼
USER reviews in editor → SAVE (normal dispatch; processor stamps the external id)
```

**Why classification-only (decision trail):**
- Merging server-side against the *shipped working copy* (not the persisted version)
  preserves edits the user made before hitting sync.
- A whole-reservation carrier in the response would need "ignore appointments/id/owner"
  by convention and risks someone later persisting/opening it; a `Classification`
  isn't storable on its own.
- A fresh `dt.newClassification()` + client-side overlay was rejected: defaults filled
  by `newClassification()` are indistinguishable from mapped values on the wire and
  would clobber user-edited fields. `newClassificationFrom(shipped)` on the server
  gets the merge for free.

**Wire types are concrete impls** (the `UpdateEvent` convention — it ships
`List<ReservationImpl>` etc. so Jackson never hits an abstract type, and springdoc
can generate a real model):

```
SyncClassificationRequest  { ImportItem selectedItem; ClassificationImpl classification; }
SyncClassificationResult   { ClassificationImpl classification; List<String> allocatableIds; }
```

New method on `ExternalEventImportService` (own `@PostExchange`); the
`updateExistingReservation` / `existingReservationId` fields on
`CreateReservationsRequest` are **deleted** — `createReservations` no longer doubles
as the sync path. The current sync wiring is a silent no-op (client sends only
`sourceItemIds`, server ignores the flags and early-returns on empty `selectedItems`,
client marks the editor dirty regardless) — all three defects disappear with the
dedicated contract.

**Implementation-time spike (single):** round-trip `ClassificationImpl` as a DTO
field through the `@HttpExchange` proxy + Spring MVC (serialize client-side,
`setResolver` server-side, and back).

## Contract changes

- **`ImportItem`**: `sourceItemId` + `columns` + `imported` + **`sourceData:
  Map<String,Object>`** (raw source fields). (Drop the `Classification` /
  `classificationValues` iterations — `sourceData` replaces them.)
- **`createReservations`**: request carries the **selected `ImportItem`s** (with
  `sourceData`) + `templateAllocatableId` + interval; returns **`List<ReservationImpl>`**
  (un-persisted) — changed from `List<String>`. The wire type is the **concrete impl**,
  not the `Reservation` interface: the Jackson `addAbstractTypeMapping(Reservation,
  ReservationImpl)` workaround in `JacksonObjectMapperFactory` fixed deserialization,
  but springdoc still emits a broken abstract schema for the interface — typing the
  signature `ReservationImpl` fixes the OpenAPI model AND lets the mapper workaround
  be deleted.
- **`syncClassification`**: new method — see the Sync flow section above;
  `updateExistingReservation`/`existingReservationId` deleted from
  `CreateReservationsRequest`.
- **`getMetadata()` gains generic filter descriptors** (Studiengang / Semester /
  Kurs) for the table filters.
- **ImportStatus** = server-computed column + `imported` flag (`DUALIS_ID` lookup).
- **Delete** the stubbed `uploadCsv` (CSV was never in the productive dialog).

## Scope

**In:** server-side `Veranstaltung`/`Pruefung` → `Classification` mapper
(recover the classification logic from master's `DualisServerLectureData`);
`ImportItem` classification payload; generic metadata filters; ImportStatus
dedup; the generic client "apply classification on template create" step;
multi-reservation edit (already exists).

**Out:** any dhbw client code; server-side reservation creation/persistence; CSV
import; the resource (allocatable) sync — that stays the separate scheduled
`DualisImportJob`/`DualisRaplaMapping` path.

## Relationship to [PRD 067](067-server-mutation-unification.md)

The server side here is pure mapping — it does **not** need `EntityLifecycle`
(no server-side reservation construction at all). The client side uses the
existing generic template flow, which (post-067) will route its create/copy
through the lifecycle client-side like everything else. So 068 is largely
independent of 067 and can land first; the Dualis import's only server
responsibility is the classification mapper.

## Multi-reservation templates — matching (2026-07-30)

`createReservations` currently truncates a multi-reservation template to its FIRST copied
reservation. The design for matching m import items against the n reservations of a blueprint
template (interactive assignment, prefilled by the generic `ImportItem.matchKey` — dhbw:
`Unitcode` — plus fuzzy name suggestion) lives in
[PRD 104 § Templates as import blueprints](104-spa-template-picker.md#templates-as-import-blueprints-external-event-import)
(D10, Phase 6, OQ3/OQ4).

## Open questions

1. **Appointments/times:** confirmed generic — from template + user-selected
   interval, not from Dualis. (Verify Dualis events don't carry authoritative
   times that must override the user interval.)
2. **Resource refs (course/lecturer):** carried as classification attribute
   values (Person/Allocatable-typed) so they ride inside the Classification, vs.
   resolved client-side into real allocations. Master did real `addAllocatable`;
   decide whether the dhbw reservation type models them as attributes.
3. **Template selection:** is there a single canonical Dualis template, or does
   the user pick (the generic wizard already supports pick-from-templates)?
4. ~~**ImportStatus update semantics:** does selecting an already-imported row
   update the existing reservation, or is it just skipped/greyed?~~ **Resolved
   2026-06-11** — see the Sync flow section: sync is only valid for exactly one
   **not-yet-imported** row (it *binds* an unbound reservation to a Dualis event);
   already-imported rows are not syncable, matching master's `isValidSelection`.
