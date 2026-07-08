# PRD 096 — SPA classification editor (reusable, events + allocatables)

**Status:** draft — 2026-07-07
**Related:** PRD 091 (event sheet — §2.4 defers exactly this), PRD 035 §5 (widget-mapping
table + descriptor-on-edit, done), PRD 055 (schema-as-data β refactor — SDL directives
are the descriptor), PRD 056 (typed `<TypeKey>ClassificationInput @oneOf`, events),
PRD 063 (allocatable mutations — server side already implemented,
`AllocatableMutationController`)

## Abstract

One reusable Angular component renders and edits the DynamicType-driven
classification attributes of BOTH Reservations and Allocatables. Today the SPA
edits only the event-type select + the fixed `name` attribute (PRD 091 §2.4
slice); every other attribute rides along untouched via the 2.0b pass-through.
End state: the event sheet renders a full attribute form, and a thin allocatable
editor reuses the identical component against the PRD 063 mutations.

## Implementation

Three building blocks, all under `rapla-angular/src/app/classification/`:

1. **`ClassificationSchemaService`** — the client-side consumer of the PRD 055 β
   schema-as-data decision. Fetches the SDL once from `GET /api/graphql/schema`
   (printer enabled, verified live), parses the generated
   `<TypeKey>Classification` type blocks into
   `AttributeDescriptor[] { key, label, valueType, multiplicity, required,
   expectedTypeKey, rootCategoryPath, enumValues? }`, cached per typeKey.
   The SDL directives carry what plain introspection cannot see:
   `@displayName`, `@required`, `@expectedType`, `@rootCategory`,
   `@multiplicity(BELONGS_TO|PACKAGE)`. The generated SDL is format-stable
   (one field per line) — a small hand parser, no `graphql` npm dependency.
   `EventDataService.introspectFragment` migrates onto this service (one
   descriptor source instead of two).
2. **`<app-classification-edit>`** — a **controlled** (stateless) component.
   Inputs: `typeKey`, `values: Record<string, unknown>`, `disabled`. Output:
   one patch event `{ key, value, label, coalesceKey }`. The HOST owns the
   draft and routes the patch through its own mutation funnel — the event
   sheet through `mutateDraft(fn, label, coalesceKey)` so PRD 091 D5 memento
   undo keeps working (label = `@displayName`, coalesceKey =
   `values:<key>` for text bursts); the allocatable editor does the same with
   its own draft. The component never holds a copy of the values.
3. **Type-change remapping helper** — pure function: on typeKey switch,
   attributes with the same key keep their values (Swing
   `ReservationInfoEdit` parity). The type select itself stays with the host.

Widget mapping follows the PRD 035 §5 table (valueType + constraints →
widget; server stays validation-authoritative, `ValidationError
{path, code, message}` maps back to fields). v1 concretely:

| valueType | v1 widget | later phase |
|---|---|---|
| STRING | `matInput` | textarea via expected-rows annotation (needs SDL emit, OQ2) |
| INT | number input | — |
| BOOLEAN | `mat-checkbox` | — |
| DATE | `mat-datepicker` (rapla stores DATE as LocalDateTime) | — |
| CATEGORY (VALUE_LIST enum) | `mat-select` from generated enum values | — |
| CATEGORY (tree, `@rootCategory`) | read-only display | tree picker |
| ALLOCATABLE (`@expectedType`) | read-only display | picker (reuse AvailabilitySearch search) |
| LIST multiplicity | read-only display | chips multi-select |

Anything v1 cannot edit stays visible + untouched in the pass-through — no
data loss, just no widget yet.

## Goal

- Event sheet renders every classification attribute of the event's type as a
  form field (v1 widget set), values round-trip through save.
- An allocatable can be created/edited from the SPA using the SAME component
  against `createAllocatable`/`updateAllocatable` (PRD 063).
- `EventDataService` no longer carries its own introspection-fragment builder.

## Scope

### In scope
- `ClassificationSchemaService` (SDL parse + cache), descriptor model.
- `<app-classification-edit>` with the v1 widget set.
- Event-sheet integration (closes the PRD 091 §2.4 deferral) incl. memento wiring.
- Minimal allocatable editor dialog (consumer 2) on PRD 063 mutations.
- Type-change value remapping helper.

### Out of scope
- Tree-category picker, allocatable-reference picker, LIST chips (later phases here).
- DynamicType administration (attribute schema editing) — PRD 057 territory.
- Permission tab of the Swing edit dialogs.
- Further server-side changes to the mutation surface (PRD 056/063 cover both
  kinds; the type-change prerequisite below already landed).

## Plan

### Phase 0 — Server prerequisite: in-place type change
- [x] 0.1 DONE (2026-07-07) — `updateReservation` (direct + `applyChanges`
      batch op) accepts a `typeKey` differing from stored: `@oneOf` variant
      must match the NEW typeKey (`MISMATCHED_TYPE` otherwise), caller passes
      the `createReservation` create-gate on the target type. Revises PRD 056
      OQ1.c (no `reshapeReservation` mutation — the drop-preview is this PRD's
      editor). Tests: `ReservationMutationControllerTest.updateReservationChangesType`
      + `...MismatchedVariantRejected` (fixture gets a second reservation type
      via `saveDynamicType` + poll-on-demand `HotSwappableGraphQlSource.rebuild()`).

### Phase 1 — Descriptor source
- [x] 1.1 DONE (2026-07-07) — `classification-schema.ts` (pure parser: type
      blocks, kinds via implements-list, directives incl. graphql-java printer
      spacing `value : "..."` + escaped quotes, VALUE_LIST enums with
      description labels) + `ClassificationSchemaService` (one fetch,
      shareReplay + sync `typeMap` signal). 9 tier-5 specs. Live-verified:
      parser matches all 21 generated types of the running dhbw SDL.
- [x] 1.2 DONE (2026-07-07) — `EventDataService.introspectFragment` deleted;
      value fragment now built from the parsed descriptors (`readsAsObject`
      → `{ id }` for tree-CATEGORY/ALLOCATABLE). Event specs stay green.

### Phase 2 — Component
- [x] 2.1 DONE (2026-07-07) — `<app-classification-edit>` (selector app-prefix
      per lint rule), controlled, v1 widgets are NATIVE inputs/selects matching
      the sheet's plain-form style (Material only where the sheet already uses
      it — deviation from the PRD table's mat-* wording); required markers,
      disabled mode, excludeKeys (host renders name itself). 3 tier-6 specs.
- [x] 2.2 DONE (2026-07-07) — `remapValues` (same key + same valueType/
      cardinality survives; server stores as-is → target-variant-only keys).
      Tier-5 specs.

### Phase 3 — Consumer 1: event sheet
- [x] 3.1 DONE (2026-07-07) — expanded header renders the component below the
      type+name row; `applyClassificationPatch` routes through `mutateDraft`
      (label = @displayName, coalesceKey = values:<key>); type select ENABLED
      on persisted events (Phase 0 server support) with remap in `setTypeKey`
      — undoable as one step (tier-6 spec: remap + undo restores dropped
      values). Playwright-smoked: dialog opens against live dhbw server, SDL
      fetch 200, zero console errors (dhbw `event` type has only `name`, so
      the component correctly renders nothing there).
- [ ] 3.2 Save round-trip live probe per widget type — BLOCKED on a dataset
      with a multi-attribute reservation type (dhbw dev data has only `event`
      with `name`; needs a second type via saveDynamicType or testdefault run).

### Phase 4 — Consumer 2: allocatable editor
- [x] 4.0 DONE (2026-07-07) — server: `Allocatable.canModify: Boolean!`
      (mirror of Reservation.canModify; `ALLOCATABLE_CAN_MODIFY` fetcher).
      Needed for the dialog's edit-vs-view gating without a second roundtrip;
      the §12 permission check stays server-side. 2 tier-3 tests in
      `ClassificationGraphQLControllerTest` (admin true; monty on Room A66
      false — allocate_conflicts ≠ modify).
- [x] 4.1 DONE (2026-07-07) — `src/app/allocatable/`:
      `AllocatableDataService` (shell + descriptor-driven value read via the
      shared `valueSelections`/`normalizeClassificationValues` helpers, which
      EventDataService now also uses; save = `updateAllocatable` +
      `expectedLastChanged`) and `AllocatableEditDialogComponent` (thin
      MatDialog around `<app-classification-edit>` with NO excludeKeys — name
      is an ordinary attribute for resources; readOnly flag + server
      canModify both force "nur ansehen"; validation issues inline;
      CONCURRENT_MODIFICATION → reload notice). NO type select (server still
      rejects allocatable type change), NO memento v1 (Abbrechen is the
      undo), create flow deferred. 4 tier-6 specs incl. wire-shape assert
      on the update mutation.
- [x] 4.2 DONE (2026-07-07) — entry point: hover ⋮ on `kind==='resource'`
      rows of the left ResourceSelection (all three tabs) with
      Bearbeiten/Anzeigen mat-menu (mirrors the PRD 094 row-menu labels);
      user rows get no menu; ⋮ never steps the filter. Spec added.
      Further entry points (PRD 094 view rows for allocatable subjects)
      can reuse the same dialog.
- [x] 4.3 DONE (2026-07-07, user request) — allocatable type change:
      `updateAllocatable` (server) now accepts a differing typeKey (same
      semantics as 0.1: @oneOf must match NEW key, `canCreate` gate on the
      target type; 2 tier-3 tests, red→green). Dialog got a type select
      (same-`classificationType` options via `types` query, only in edit
      mode with >1 option) with client-side `remapValues` — supersedes the
      "no type select" note in 4.1. Updates PRD 063's reject stance.

### Bugfix ride-along (2026-07-07)
- [x] False "zwischenzeitlich geändert" on every save of a persisted entity:
      the SPA's `toLocalDateTime` stripped the FRACTION along with the offset,
      but rapla timestamps carry milliseconds and the server compares
      `LocalDateTime.equals()` → every `expectedLastChanged` mismatched.
      Fix: shared `src/app/graphql/local-date-time.ts` strips ONLY the offset
      (tier-5 red→green); tier-3 contract test
      `updateWithOffsetStrippedLastModifiedAtPassesConcurrencyCheck` pins the
      wire round-trip (serialize lastModifiedAt → strip offset → echo passes).

## Tests

- Tier 5: SDL parser fixtures; remapping helper.
- Tier 6: component render/emit contract; event-sheet header integration.
- Live probe: edit an attribute of each v1 valueType on the dev server, save,
  re-load, verify persisted (api-testing / Playwright).

## Open Questions

- **OQ1** — `no-view` + main-vs-additional attribute split (Swing hides
  `no-view`, splits main/additional views) are NOT in the SDL today. Add two
  small directives in the SDL generator (`@noView`, `@additional`) or render
  all attributes flat in v1? *Resolution:* 2026-07-08 — subsumed by the D5
  revision: one `@editView` directive carries `title`/`additional`/`no-view`;
  the component hides `no-view` and renders `additional` like main for now.
- **OQ2** — expected-rows/columns annotations (textarea sizing) — emit in SDL
  or ignore? *Resolution:* pending.
- **OQ3** — allocatable editor entry point placement (PRD 094 command layer
  vs. plain button in resource views). *Resolution:* pending.

## Decisions locked

**D1 — Descriptor = parsed SDL from `/api/graphql/schema`.** Implements the
PRD 055 β decision client-side; plain introspection lacks the directives.
Rejected: a new `dynamicType(key){attributes{…}}` server query — would reopen
the PRD 055 β rejection of a parallel descriptor surface; hand parsing the
format-stable generated SDL is cheap.

**D2 — Controlled component; host owns the draft.** Precondition for PRD 091
D5 memento undo (single `mutateDraft` funnel) and for reuse across entity
kinds with different draft models. Rejected: component-internal form state
with ngModel two-way binding — would fork the undo funnel.

**D5 — Title fields are nameformat-driven via @editView (revised 2026-07-08;
originally a separate @title directive / option C, 2026-07-07).** One
placement directive `@editView(value: "title" | "additional" | "no-view")`
covers the whole `title > main > additional > no-view` scale — it replaced
`@title` after the user pointed at the existing `edit-view` attribute
annotation ("wo erscheint es") as the natural home:

- **`title`** (stage 1: computed, not stored): EVERY direct attribute
  reference `{key}` in the DISPLAY nameformat becomes a title attribute —
  composites (`{surname} {forename}`) yield several, rendered as prominent
  header fields in attribute order (re-sort attributes in the type editor to
  reorder the header). Function expressions/literals in the format are
  ignored (`{name} concat(...)` still yields `name`); dangling references
  and list-valued attributes drop out; an explicit `edit-view=no-view`
  annotation wins over the derivation. Multiple titles are deliberately
  allowed — the earlier "exactly one" rule was an artifact of option C's
  single-field header, not a domain constraint; dropping it removes any need
  for uniqueness validation.
- **`additional` / `no-view`**: mirror the stored `edit-view` attribute
  annotation; `main` (default) is omitted on the wire. The SPA component now
  hides `no-view` (Swing parity — before this we rendered admin-hidden
  attributes) and renders `additional` like main for now (collapsible
  details = later polish; Swing's Reservation edit also shows both together).
- **Stage 2 (future, with the SPA type editor):** allow `title` as an
  explicit stored `edit-view` value; explicit wins over the derivation.
  Needs the two Swing visibility one-liners (`ClassificationEditUI.isVisible`,
  `ClassificationInfoUI` must treat title like main) + the type-editor
  dropdown option.

Fixes the bug where a type without a `name` attribute still showed a Name
input whose value the @oneOf input rejected on save. Rejected: option A
(descriptor-gated but still keyed to `"name"`), option B (no prominent title
line at all), exposing the raw nameformat string to the client (would need
the ParsedText grammar client-side just for one bit), and a separate
`DynamicType.titleAttributeKey` query field (splits descriptor knowledge
across two channels — everything else already rides the printed SDL). The
dormant quick-event-dialog still hardcodes `name` — it must adopt
`titleAttrs` when it gets its calendar-click entry point.

**D4 — Enum selects offer NO pickable "nothing" (2026-07-07, user decision).**
The unset state renders as a blank `disabled hidden` placeholder; once a value
is chosen it cannot be cleared back to null from the select. This is a
DELIBERATE deviation from Swing (`CategoryListField` passes
`includeNothingSelected=true` unconditionally): status-like value lists (e.g.
the loan lifecycle category) must always hold a value. If a deployment needs
clearable optional enums later, gate the placeholder's `disabled` on
`!required` — don't silently re-add it for all.

**D3 — Widget spec = PRD 035 §5, not reinvented.** The table there is the
locked mapping; this PRD only phases it (v1 subset, pickers later). The
descriptor *transport* in PRD 035 §4 (`AttributeDescriptor` query) is
superseded by PRD 055 β — D1 here is the replacement.
