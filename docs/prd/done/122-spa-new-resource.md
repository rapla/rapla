# PRD 122 — SPA: create new resources from the resource picker

**Status:** done — 2026-10-06 (closed in the PRD triage). Previously: implemented — 2026-10-01 (commits 12fb1bfb4 server, 8a72d81cd SPA; reviewed, all findings fixed). All decisions made by the user (D1–D9); the type picker was dropped (ruling B1, D2/D3 revised). Since PRD 123 the rail's list lives in `ResourcePickerComponent`; the "+ Neu" button stays in the rail header.
**Related:** [PRD 063](063-graphql-allocatables-write-api.md) (`createResource`, server side done), [PRD 096](096-spa-classification-editor.md) (resource edit dialog, Phase 4), [PRD 104](../104-spa-template-picker.md) (event "Neu" picker, `newEventOptions`), [PRD 107](107-reservation-prototype-prefill.md) (`reservationPrototype`), [PRD 119](../119-spa-one-search-resource-picker.md) (picker with type chips), [PRD 120](120-resource-hierarchy-parents-children.md) (parents/children)

## Abstract

The SPA can edit resources but not create them. A "+ Neu" button in the resource picker opens the existing resource dialog in a create mode with a preselected type (D2/D3), prefilled with the type's attribute defaults. End state: a user with create permission on a resource or person type creates one from the SPA and sees it in the picker list.

## Current state (read 2026-09-30)

- Server: `createResource(input: ResourceInput!)` exists; client mints the id, owner = caller, `permissions: null` copies the type defaults. The classification starts from `dt.newClassification()` and sent attributes overwrite it (`ClassificationInputMapper.buildClassificationFromInput`).
- Server: `newEventOptions.eventTypes` = `FacadeImpl.getDynamicTypes(RESERVATION)` (skips internal `rapla:` types) filtered by `canCreate`, gated by the defaultwizard plugin; `reservationPrototype(typeKey)` returns `dt.newClassification()`, unknown / wrong-kind / non-creatable answer identically (`ReservationGraphQLController`). No resource counterparts.
- SPA: `ResourceEditDialogComponent` opens only by id; `ResourceDataService.save` calls only `updateResource`; `typeOptions` reads the unfiltered `types`.
- SPA: `NewEventPickerComponent` injects `NewEventOptionsService`; `new-event-picker-model.ts` is event-agnostic and already mixes `type` and `template` rows.
- SPA: `ClassificationSchemaService.prototype(typeKey)` loads and caches `reservationPrototype`, only for `kind === 'RESERVATION'`.
- SPA: active type chip = `type:<typeKey>` in `ResourceSelectionStore.activeChip()`; `typedId('a')` in `event/event-draft.ts` is file-private.
- Swing: "Neu → Ressource" in the resource tree context menu (`MenuFactoryImpl.addAllocatableMenuNew`), shown when `PermissionController.isRegisterer` (canCreate on any resource/person type) or admin; preselects the focused type node's or resource's type; one type creates directly. New objects start from the type's attribute defaults.

## Decisions (user, 2026-09-30)

**D1 — Entry point.** "+ Neu" at the right end of the "DURCHSTEPPEN" header in `resource-selection.component.ts`, above the chips; hidden when no type is creatable.

**D2 — ~~Type picker like the event "Neu".~~ Revised 2026-09-30 (ruling B1): no picker.** "+ Neu" opens the create dialog directly; the type is changed in the dialog's grouped select (D9). Swing form (`RaplaObjectActions.guessType`). A picker may return once a deployment has enough types to need one.

**D3 — Preselection.** An active type chip preselects its type; ~~Alle / Favoriten / Zuletzt preselect nothing~~ under Alle / Favoriten / Zuletzt the first creatable type is preselected (revised 2026-09-30, ruling B1 — the dialog's select is the type choice).

**D4 — One options query for both kinds.**

```graphql
newResourceOptions: NewResourceOptions!

type NewResourceOptions {
  resourceTypes: [DynamicType!]!
}
```

Server: `FacadeImpl.getDynamicTypes` for RESOURCE and PERSON, each filtered by `canCreate`; no plugin gate. The wrapper leaves room for a later `templates` field (D7).

**D5 — Prefill attribute defaults** (parity with events and Swing; deployments use attribute defaults on resource types).

```graphql
resourcePrototype(typeKey: String!): ResourcePrototype!

type ResourcePrototype {
  typeKey:        String!
  classification: Classification!
}
```

Same resolver as `reservationPrototype`, accepting RESOURCE and PERSON types; unknown / wrong-kind / non-creatable answer identically (§12). The SPA's `prototype()` picks the query by the type's kind; the create dialog fills its draft once a type is chosen.

**D6 — After save only reload the list** (`store.reload()`, as the edit path); no auto-selection.

**D7 — Resource templates later.** Out of scope now; `NewResourceOptions` and the picker's `template` rows are ready for them.

**D8 — Fix the stale dialog comment** in the create-mode change: `ResourceEditDialogComponent` says the server rejects type changes for resources, the schema allows them (PRD 096 Phase 4).

**D9 — Mixed type list, grouped (ruling A3, 2026-09-30).** The create dialog's type select lists every creatable type in two groups, "Ressourcen" first, then "Personen". This deliberately deviates from Swing, whose `ClassificationField` shows one kind only.

## Implementation

1. **Server:** `newResourceOptions` and `resourcePrototype` resolvers next to their event siblings + schema entries.
2. ~~**Picker reuse:** `NewEventPickerComponent` takes items via `MAT_DIALOG_DATA`~~ — dropped with the picker (ruling B1).
3. **Create mode:** `ResourceEditDialogData` gets `id?` + `typeKey?`; without `id` the dialog starts from the prototype and `save` calls `createResource` with an id from an exported `typedId('a')`. Title "Neue Ressource", no `expectedLastChanged`.
4. **Flow:** button → dialog (type preselected, D3) → on `'saved'` `store.reload()`.

## Scope

### In scope
- Button, create mode with type select, `newResourceOptions`, `resourcePrototype`, stale comment.

### Out of scope
- Resource templates (D7), parent from the tree node (PRD 120), permission editing on create, delete.

## Plan

### Phase 1 — Server
- [x] `newResourceOptions` (tier-3 test: see Debt).
- [x] `resourcePrototype` (tier-3 test: see Debt).

### Phase 2 — SPA
- ~~Picker takes items via dialog data; event "Neu" specs stay green~~ — dropped with the picker (ruling B1).
- [x] `prototype()` handles RESOURCE/PERSON kinds.
- [x] Dialog create mode + `createResource` in `ResourceDataService`; stale comment fixed.
- [x] "+ Neu" button with preselection.

### Debt — quick win shipped without tests/review (user ruling 2026-09-30)
- [x] Tier-3 leak tests — `NewResourceOptionsGraphQLTest` (10 tests, red-probe on the kind/internal gate confirmed) and `AllocatableMutationControllerTest.updateTypeChangeToInternalWrongOrOtherKindAnswersLikeUnknown`, plus `GraphQlSchemaOrderTest` for the SDL field order (2026-09-30).
- [x] Tier-6 specs: `resource-edit-dialog.component.spec.ts` "create mode (PRD 122)" (7 specs incl. M1/M2/M4 and the A3 grouping, 2026-09-30). Rail "+ Neu" specs in `resource-selection.component.spec.ts` (hidden without creatable types, preselection active chip / first creatable, create-dialog data, reload after save; rapla-impl2, 2026-09-30).
- [x] Code review of the quick win — rapla-review pass 1 (2026-09-30): H1 kind/internal gate on `createResource` and on the `updateResource` type change (same kind only, Swing form), M1/M2/M4 dialog fixes, L1/L3/L5/L6; all fixed by rapla-impl, tests green 23:55.
- [x] Type picker (D2) — dropped by ruling B1 (2026-09-30); the dialog's grouped select is the type choice.

## Tests

- Tier 3: both new queries as above; `createResource` on a non-creatable type rejected (reuse a PRD 063 test if one exists).
- Tier 5: preselection from `activeChip()`.
- Tier 6: create mode shows prototype values and sends `createResource` with typeKey and a fresh `a…` id.

## Open Questions

None. Resolved 2026-09-30: field shape → D4, prefill → D5, after save → D6, Swing parity → Current state, stale comment → D8.
