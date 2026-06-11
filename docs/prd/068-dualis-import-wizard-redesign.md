# PRD 068 — External-event (Dualis) import: server maps to classification, client uses the generic template flow

**Status:** draft — opened 2026-06-10 (design resolved in a late-night session
against the old productive dialog + the existing generic template wizard)

## Goal

Make the external-event (Dualis) import a thin layer over rapla's **existing
generic template-creation flow**, with the server contributing **only the
classification mapping** of the source data. No dhbw-specific code in the rapla
client; no server-side reservation construction or persistence.

## Prime directive (why the refactoring happened)

The whole point of the PRD 003/012 refactoring was **no dhbw-specific code in
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

## Contract changes

- **`ImportItem`**: `sourceItemId` + `columns` + `imported` + **`sourceData:
  Map<String,Object>`** (raw source fields). (Drop the `Classification` /
  `classificationValues` iterations — `sourceData` replaces them.)
- **`createReservations`**: request carries the **selected `ImportItem`s** (with
  `sourceData`) + `templateAllocatableId` + interval; returns **`List<Reservation>`**
  (un-persisted) — changed from `List<String>`.
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

## Relationship to PRD 067

The server side here is pure mapping — it does **not** need `EntityLifecycle`
(no server-side reservation construction at all). The client side uses the
existing generic template flow, which (post-067) will route its create/copy
through the lifecycle client-side like everything else. So 068 is largely
independent of 067 and can land first; the Dualis import's only server
responsibility is the classification mapper.

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
4. **ImportStatus update semantics:** does selecting an already-imported row
   update the existing reservation, or is it just skipped/greyed?
