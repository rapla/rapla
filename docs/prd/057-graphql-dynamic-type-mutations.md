# PRD 057 — GraphQL DynamicType Schema Editor Mutations

**Status:** draft (placeholder — not slated for implementation)

**Parent:** PRD 035 §"Schema design — structural-static + classification-generated"
+ §"Rebuild on admin change". **Siblings:** PRD 055 (events read), PRD 056
(events mutations).

**Triggered by:** the eventual Angular SPA gaining a schema editor for
DynamicTypes (data-model administration). Server side is unblocked; SPA UI
is the work that prompts implementation.

## Goal

Land the **write-side mutation surface for DynamicTypes themselves** —
admin operations that change the deployment's data model (create/update/
delete DynamicTypes and their Attributes). Distinct from PRD 056 which
mutates *data within* DynamicTypes (reservations, allocatables) using the
existing schema as a fixed contract.

When an admin saves a DynamicType change:
1. Server's existing hot-swap fires (PRD 055 Cut C `GraphQlSchemaRebuilder`)
2. Schema regenerates within ~10s; generated `<TypeKey>Classification`
   types reflect the new attribute set
3. The admin's SPA can immediately query against the new shape

## Scope

In:
- `saveDynamicType(input: DynamicTypeInput!, expectedLastChanged: LocalDateTime): DynamicType!`
  — upsert (id null = create; id supplied = update)
- `deleteDynamicTypes(ids: [ID!]!): BulkResult!`
- **No `ChangeOp` extension** in v1. Admin's cross-type batches
  (e.g., create new DynamicType + create initial instance) are split
  into two roundtrips. Acceptable for the SPA editor; ChangeOp extension
  becomes additive if real demand emerges.
- `AttributeInput` shape for attribute create/update inline within a
  DynamicType edit
- §12 admin-only gate
- Hot-swap integration documented

Out:
- **Real-time notifications** for other connected SPAs that the schema
  changed — GraphQL subscriptions are out of scope. Other users see the
  new schema on their next descriptor refresh (PRD 035 §4 edit-open pattern)
  or on a SPA reload. Skew matches existing rapla operational model.
- **Migration tooling** for existing data when an attribute is removed
  or its valueType changes — rapla's storage layer handles drop / coerce
  per existing semantics; we don't add new policies.
- **Multi-locale name editing** in the first cut — single-locale string
  on `AttributeInput.name` / `DynamicTypeInput.name`. Multi-locale comes
  later when the SPA's i18n admin UI is in scope.
- **Per-attribute permissions / read-only-on-edit fields** (admin-only
  attributes, write-protected fields) — defer until a real consumer asks.
- **Annotation editing** beyond core annotations — limit v1 to the
  well-known set (`classification-type`, `name-format`, etc.); arbitrary
  admin annotations come later.

## Locked design intent (subject to revision when implemented)

### Surface (locked 2026-05-28 — minimal consolidated)

```graphql
type Mutation {
  # Upsert — id null = create; id present = update
  saveDynamicType(input: DynamicTypeInput!,
                  expectedLastChanged: LocalDateTime): DynamicType!
  deleteDynamicTypes(ids: [ID!]!): BulkResult!
}
```

Two mutations. `saveDynamicType` is an upsert (matches PRD 056 update
full-state style); `deleteDynamicTypes` is uniform bulk (matches PRD 056
delete consolidation lesson). **No `ChangeOp` extension in v1** — admin
doesn't need atomic cross-type schema + data workflows that often; if
needed later, ChangeOp gains additive variants.

### Inputs

```graphql
input DynamicTypeInput {
  id:                 ID                                    # null = create; supplied = update
  key:                String!                               # admin-supplied (e.g. "Raum") — unique across deployment
  name:               MultiLanguageStringInput!             # multi-locale; rapla DynamicType is MultiLanguageNamed
  classificationType: ClassificationType!                   # RESOURCE | PERSON | RESERVATION
  attributes:         [AttributeInput!]!                    # ordered; rapla preserves attribute order
  annotations:        [KeyValueInput!]                      # name-format etc.; open-ended
}

input AttributeInput {
  id:             ID                                        # client UUID for new attributes; existing id for updates
  key:            String!
  name:           MultiLanguageStringInput!                 # multi-locale; rapla Attribute is MultiLanguageNamed
  valueType:      AttributeValueType!                       # STRING | INT | BOOLEAN | DATE | CATEGORY | ALLOCATABLE
  multiplicity:   Multiplicity!                             # SINGLE | LIST | BELONGS_TO | PACKAGE — see Multiplicity expansion below
  required:       Boolean!
  rootCategoryId: ID                                        # CATEGORY only — root of allowed subtree
  expectedTypeId: ID                                        # ALLOCATABLE only — required target DynamicType id
  defaultValue:   DefaultValueInput                         # optional; null = no default. See @oneOf shape below.
  annotations:    [KeyValueInput!]
}

# Multi-locale name shape — applies to DynamicType.name + Attribute.name.
# Reusable wherever rapla's MultiLanguageNamed surface needs writing
# (Category names eventually).
input MultiLanguageStringInput {
  default:      String!                                     # default-locale value (required fallback)
  translations: [LocaleEntryInput!]                         # optional per-locale overrides
}

input LocaleEntryInput {
  locale: String!                                           # ISO code: "de", "en", "fr"
  value:  String!
}

# Typed primitive value for AttributeInput.defaultValue. The @oneOf
# directive enforces exactly-one-set at parse time.
input DefaultValueInput @oneOf {
  stringValue:    String
  intValue:       Int
  boolValue:      Boolean
  dateValue:      LocalDateTime
  categoryId:     ID
  allocatableId:  ID
  stringValues:   [String!]
  categoryIds:    [ID!]
  allocatableIds: [ID!]
}

# Generic key-value pairs — open-ended annotation keys per rapla's
# Annotatable surface. Reusable wherever map-style inputs appear.
input KeyValueInput {
  key:   String!
  value: String!
}
```

### Multiplicity expansion (locked 2026-05-28)

The rapla "Multiselect" admin dropdown actually exposes 4 values, all
mutually exclusive per the rapla constraint model. They consolidate into
a single `Multiplicity` enum:

```graphql
enum Multiplicity {
  SINGLE       # Nein — one value
  LIST         # Ja — multi-value list (independent items)
  BELONGS_TO   # gehört zu — multi-value, each "belongs to" target — ALLOCATABLE only
  PACKAGE      # gruppiert — multi-value, packaged as a group — ALLOCATABLE only
}
```

`BELONGS_TO` and `PACKAGE` are ALLOCATABLE-specific; server rejects them
with `INVALID_VALUE` if applied to other valueTypes.

| valueType | Allowed Multiplicity |
|---|---|
| STRING / INT / BOOLEAN / DATE | SINGLE |
| CATEGORY | SINGLE, LIST |
| ALLOCATABLE | SINGLE, LIST, BELONGS_TO, PACKAGE |

Read-side consequence: SDL generator emits all multi-valued attrs as
`[X!]` regardless of which multiplicity flavor; the BELONGS_TO/PACKAGE
distinction surfaces via a `@multiplicity(value: BELONGS_TO)` custom
directive on the generated field. SPA's renderer reads the directive
to pick the appropriate widget (single picker vs multi-list vs
belongs-to-tree).

### §12 invariants

1. **Admin only.** `caller.isAdmin == false` → all DynamicType mutations
   reject with `PERMISSION_DENIED`. No group-admin path — DynamicType is
   system-wide schema, not group-scoped.
2. **Referenced entities must exist:** `rootCategoryId` resolves to a
   real Category; `expectedTypeId` resolves to a real DynamicType.
3. **No leak.** Standard §12 — unknown id and unreadable id fail
   identically.

### Validation

| Check | Error code |
|---|---|
| Caller not admin | `PERMISSION_DENIED` |
| `key` not unique across DynamicTypes (create) | `KEY_COLLISION` |
| `key` is a rapla-internal name (`rapla:*`) | `INVALID_VALUE` |
| Attribute `key` not unique within DynamicType | `KEY_COLLISION` |
| Attribute `valueType` change incompatible with existing data | `INVALID_VALUE` (with detail) |
| `rootCategoryId` doesn't resolve | `REFERENCE_NOT_FOUND` |
| `expectedTypeId` doesn't resolve | `REFERENCE_NOT_FOUND` |
| `expectedLastChanged` mismatch on update | `CONCURRENT_MODIFICATION` |
| Delete with existing instances of this type | `REFERENCE_EXISTS` (with `referrers: [ID!]` capped) |
| `classificationType` change with existing instances | `REFERENCE_EXISTS` or `INVALID_VALUE` |

### Hot-swap visibility

The mutation response returns the updated `DynamicType` — guaranteed
consistent with the storage state at the moment of dispatch.

The **generated `<TypeKey>Classification` GraphQL type** may not yet be
in the runtime schema at the moment the response is sent: the rebuild
runs on the next `GraphQlSchemaRebuilder` poll (within ~10s). Documented
behavior:
- Admin's SPA queries `__type(name: "RaumClassification")` to detect
  when the new shape is live, OR
- Admin's SPA waits a fixed delay (~15s safety margin) before
  attempting typed-narrow reads, OR
- Admin's SPA polls the read schema via introspection until the new
  fields appear

The PRD 055 Cut C poll interval may need to be tightened (e.g., 2s) for
the schema-editor UX — admin saving + waiting for typed types to
appear should feel responsive, not a 10s pause. Implementation note for
when this lands.

### Reordering attributes

`attributes: [AttributeInput!]!` is order-significant. Rapla preserves
attribute order; SPA's drag-reorder UI submits the new order; server
stores it. No special "reorder" verb — just submit with desired order.

### Deletion behavior

`deleteDynamicTypes(ids: [id])` rejects if any data instance uses the type:
- For reservation types: rejected if any Reservation has this type
- For resource/person types: rejected if any Allocatable has this type

`REFERENCE_EXISTS` error with first 50 referring ids in the extension.
Admin must delete instances (via bulk delete or reassign-type via
reshape) before removing the type.

Soft-delete / "deprecation" semantics — out of scope; consider in a
follow-up if real consumer demand emerges.

## CategoryKind cascade (from parallel-session work)

PRD 035 §5b's CategoryKind discrimination locked 2026-05-28 — Categories
are classified as `VALUE_LIST` (flat picklist), `ORGANIZATION` (hierarchical
tree), or `SYSTEM` (rapla-internal). Cascading consequence for this PRD:

When the schema editor creates a CATEGORY attribute, the **field type
in the generated `<TypeKey>Classification` depends on the referenced
Category's `kind`:**

| `rootCategory.kind` | Generated typed field |
|---|---|
| `VALUE_LIST` (e.g., `Raumtypen`) | Generated enum (e.g., `enum Raumtyp { Hoersaal Seminarraum Labor … }`) — typed-enum field |
| `ORGANIZATION` (e.g., `Standorte`) | `Category` — full typed pointer with `children` walkable |
| `SYSTEM` | Filtered out — rapla-internal subtrees aren't writable / referenceable via the schema editor |

Schema editor implications:
- Admin picking `rootCategoryId` for a CATEGORY attribute → server validates
  the Category exists AND its kind is not `SYSTEM` (else `INVALID_VALUE`).
- After save, the hot-swap regenerates the classification SDL — if the
  referenced root is VALUE_LIST and no enum-type-name collision happens, a new
  enum type appears in `__schema`.
- VALUE_LIST root with **no children** → generated enum has no values →
  GraphQL spec rejects empty enums → save rejected with `INVALID_VALUE`
  ("root category must have ≥1 child").
- Changing a Category's kind (via the future Category editor PRD —
  separate from this one) **cascades to every DynamicType referencing it**:
  attribute fields flip between enum and `Category` typed shape.

## Examples — worked admin scenarios

### Example 1 — Add an INT attribute to an existing DynamicType

Admin extends `Raum` with `floorNumber` (Int, optional):

```graphql
mutation AddFloorNumberToRaum {
  saveDynamicType(
    expectedLastChanged: "2026-05-28T10:00:00",
    input: {
      id: "dt-raum-uuid",
      key: "Raum",
      name: "Room",
      classificationType: RESOURCE
      attributes: [
        # ... all existing attributes echoed back (full-state per PRD 056 update style) ...
        { id: "attr-raumname-uuid",     key: "Raumname",         name: "Room name",          valueType: STRING,  multiplicity: SINGLE, required: false },
        { id: "attr-grundflaeche-uuid", key: "Grundflaeche",     name: "Floor area [m²]",    valueType: INT,     multiplicity: SINGLE, required: false },
        # ... etc ...
        # NEW:
        { id: null,                     key: "floorNumber",      name: "Floor number",       valueType: INT,     multiplicity: SINGLE, required: false }
      ]
    }
  ) {
    id key
    attributes { key valueType }
  }
}
```

After save (within ~10s, see OQ1):
- `RaumClassification.floorNumber: Int` appears in `__schema`
- Existing Raum allocatables have `floorNumber: null` (no migration needed for nullable add)
- SPA's old descriptor is stale; refetch picks up `floorNumber`

### Example 2 — Create a new DynamicType from scratch

Admin creates a `Stehtisch` (standing desk) resource type:

```graphql
mutation CreateStehtischType {
  saveDynamicType(input: {
    id: "00000000-1111-2222-3333-stehtisch01",
    key: "Stehtisch",
    name: { default: "Standing desk" },
    classificationType: RESOURCE,
    attributes: [
      { key: "name",       name: "Name",                valueType: STRING,  multiplicity: SINGLE, required: true },
      { key: "lager",      name: "Stored in",           valueType: ALLOCATABLE, multiplicity: SINGLE, required: false,
        constraints: { expectedTypeId: "dt-raum-uuid" } },
      { key: "ausstattung",name: "Equipment",           valueType: CATEGORY, multiplicity: LIST, required: false,
        constraints: { rootCategoryId: "cat-ausstattungen-uuid" } }
    ],
    annotations: [
      { key: "name-format", value: "{name}" }
    ]
  }) {
    id key
    attributes { key valueType }
  }
}
```

Hot-swap regenerates:
```graphql
# auto-appears in __schema after ~10s
type StehtischClassification implements Classification & AllocatableClassification {
  typeId: ID!
  type: DynamicType!
  name: String
  lager: Allocatable                    # ALLOCATABLE attribute → typed pointer
  ausstattung: [Ausstattung!]           # CATEGORY-LIST attr targeting VALUE_LIST root → generated enum
}
```

The `ausstattung` field type follows from `cat-ausstattungen-uuid`'s
`kind: VALUE_LIST` — the SDL generator emits enum-typed list instead of
`[Category!]`. **The admin doesn't choose the field type — the Category's
kind determines it.**

### Example 3 — Rename an attribute (`Raumname` → `roomName`)

Update with renamed attribute (same attribute id; only key + name change):

```graphql
mutation RenameRaumnameToRoomName {
  saveDynamicType(
    expectedLastChanged: "2026-05-28T11:00:00",
    input: {
      id: "dt-raum-uuid",
      key: "Raum",
      name: { default: "Room" },
      classificationType: RESOURCE,
      attributes: [
        { id: "attr-raumname-uuid", key: "roomName", name: "Room name",
          valueType: STRING, multiplicity: SINGLE, required: false },
        # ... rest unchanged ...
      ]
    }
  ) { id }
}
```

Server preserves the underlying attribute (id stable) and renames its key.
Existing Raum data: the stored value for the now-renamed attribute survives
(rapla's storage indexes attribute values by attribute id internally, not by
key — confirm via tier-3 test).

Hot-swap consequences:
- `RaumClassification.Raumname` deleted from schema
- `RaumClassification.roomName` added
- SPA queries with old `Raumname` get `FIELD_NOT_FOUND` → descriptor-refresh recovery
- Codegen consumers' static fragments break until regen

### Example 4 — Delete a DynamicType, rejected because instances exist

```graphql
mutation DeleteUnusedType {
  deleteDynamicTypes(ids: ["dt-unused-uuid"]) {
    overallStatus
    results { index deletedId errors { code message } }
  }
}
```

If 42 allocatables exist of this type, server rejects:
```json
{
  "errors": [{
    "message": "Cannot delete DynamicType — 42 entities still reference it",
    "extensions": {
      "code": "REFERENCE_EXISTS",
      "referrers": ["alloc-1", "alloc-2", ..., "alloc-50"]   # capped at 50
    }
  }]
}
```

Admin must reassign or delete the instances first (via reservation/allocatable
mutations from PRD 056 / 057-future).

### Example 5 — Create DynamicType then first instance (two roundtrips)

> **Note:** the originally-sketched `ChangeOp` extension that would have
> made this atomic was **dropped 2026-05-28** (see Scope). The use case
> is rare enough that two roundtrips are acceptable. If demand surfaces
> later, extending `ChangeOp` with `saveDynamicType` is additive.

Admin's workflow becomes:

1. Save the new DynamicType:
   ```graphql
   mutation { saveDynamicType(input: { id: "...", key: "Stehtisch", ... }) { id } }
   ```
2. Wait for the schema rebuild (poll `__type(name: "StehtischClassification")` until it appears, ~10s)
3. Create the first instance using the now-existing typed input:
   ```graphql
   mutation { createAllocatable(input: { typeId: "Stehtisch", classification: { stehtisch: { name: "HS-FB-1" } } }) { id } }
   ```

The original atomic-batch sketch (preserved for design-history context):

```graphql
mutation CreateTypeAndFirstInstance {
  applyChanges(operations: [
    {
      createDynamicType: {
        id: "00000000-1111-2222-3333-stehtisch01",
        key: "Stehtisch",
        name: "Standing desk",
        classificationType: RESOURCE,
        attributes: [
          { key: "name", name: "Name", valueType: STRING, multiplicity: SINGLE, required: true }
        ]
      }
    },
    {
      createAllocatable: {                                # PRD 057-allocatables variant
        id: "00000000-1111-2222-3333-firstdesk01",
        typeId: "00000000-1111-2222-3333-stehtisch01",     # same-batch ref to the new DynamicType
        classification: {
          typeId: "Stehtisch",
          attributes: [
            { key: "name", value: { stringValue: "Stehtisch HS-FB-1" } }
          ]
        }
      }
    }
  ]) {
    overallStatus
    results {
      index
      reservation { id }
      allocatable  { id displayName }
      errors { code message }
    }
  }
}
```

Both succeed or both reject. The same-batch reference from operation #1 to
operation #0 uses the client-assigned UUID — exactly the pattern PRD 056
locked.

**Hot-swap caveat:** the schema rebuild that adds `StehtischClassification`
doesn't fire DURING the mutation — it fires on the next poll (~10s). So
between the mutation's response and the rebuild, the SPA can't query
`... on StehtischClassification { name }`. Workaround: SPA queries
`__type(name: "StehtischClassification")` after save, polls every 1-2s,
proceeds when it appears (or fall back to the generic interface fields).
See OQ1 — tightening the schema-rebuild poll rate.

### Example 6 — Change attribute valueType (STRING → INT)

The hard case: existing data has string values that may not parse as INT.

```graphql
mutation MigrateCapacityToInt {
  saveDynamicType(
    expectedLastChanged: "2026-05-28T12:00:00",
    input: {
      id: "dt-raum-uuid",
      # ... other fields unchanged ...
      attributes: [
        # AnzahlPlaetzeInsgesamt changes from STRING → INT:
        { id: "attr-platze-uuid", key: "AnzahlPlaetzeInsgesamt",
          name: "Total seats", valueType: INT, multiplicity: SINGLE,
          required: false }
      ]
    }
  ) { id }
}
```

Server behavior (matches rapla's existing `AttributeImpl.commitChange`
semantics, which we'd test against in tier-3):
- For each existing Raum: try to parse the stored string as Int
- Parseable → coerced to Int
- Unparseable ("approx. 50") → value silently dropped (or rejected — see OQ?)

If rapla's storage rejects on unparseable, the mutation fails with
`INVALID_VALUE` including the count of affected entities. Admin must clean
data first. The behavior must be **documented loudly** and tested at tier 3
since "silently drops data on type change" is a footgun.

### Example 7 — Delete an attribute (forces data drop)

```graphql
mutation RemoveDeprecatedField {
  saveDynamicType(
    expectedLastChanged: "...",
    input: {
      id: "dt-raum-uuid",
      # ... other fields unchanged ...
      # AttributeInput list omits "OeffnungszeitenBemerkung" — that means delete it
      attributes: [
        # ... all attributes EXCEPT the one being deleted ...
      ]
    }
  ) { id }
}
```

Full-state semantics from PRD 056 update style: an attribute absent from
the input list is **deleted**. All existing classifications drop their value
for that attribute (matches rapla's `commitChange` semantics).

The "absence = deletion" rule is dangerous if the SPA accidentally submits
an incomplete list (stale form state). Mitigations:
- SPA always builds the attributes list from the current descriptor at
  save time (defensive)
- Server logs warnings on attribute deletion with affected-entity counts
- Future: dry-run mode could preview the data-loss before committing

## Plan

When this lands (TBD — after the Angular schema editor UI work
prompts it):

1. **Schema additions** — input types listed above
2. **DynamicTypeMutationController** in `rapla-app` —
   `@MutationMapping` for the three roots; goes through `operator.dispatch`
3. **Extend ChangeOp + applyChanges** — additive; existing `@oneOf` rule
   keeps validation guarantees
4. **Validation logic** — referential integrity, key collision, classification-
   type change rules, valueType-change-vs-existing-data
5. **`AttributeImpl.commitChange(DynamicType)` integration** — rapla's
   existing storage-layer migration of classifications when an attribute's
   type changes; tested via tier-3
6. **Hot-swap poll-rate tuning** — consider dropping from 10s to ~2s when
   schema-editor mutations land, to make UX responsive
7. **Tier-3 tests** — admin-only gate; create/update/delete round-trip;
   delete-with-instances rejection; valueType change with existing data;
   hot-swap visibility window

## Tests (tier-3 spec)

Same patterns as PRD 056 — `@SpringBootTest` + `@AutoConfigureMockMvc(addFilters
= false)` + `@WithMockUser(roles = "ADMIN")` for happy path; non-admin
variants for §12 rejection tests.

Add:
- `saveDynamicType_create_nonAdmin_rejected`
- `saveDynamicType_create_keyCollision_rejected`
- `saveDynamicType_update_attributesValueTypeChange_propagatesToExistingData`
- `saveDynamicType_update_concurrent_rejected` (expectedLastChanged mismatch)
- `saveDynamicType_create_belongsToOnStringAttribute_rejected` (Multiplicity expansion validation)
- `saveDynamicType_create_categoryRootIsSystemKind_rejected`
- `deleteDynamicTypes_withInstances_rejected`
- `deleteDynamicTypes_empty_succeeds`
- Hot-swap round-trip: save → poll until typed input type appears → use it to create instance

## Open questions

### OQ1 — Hot-swap poll-rate adjustment

PRD 055 Cut C ships with 10s polling. Schema-editor UX wants ~2s for
admin feedback responsiveness. Open: lower it globally, or add a
faster post-mutation poll trigger?

### OQ2 — Multi-locale name editing

Single-locale `name: String!` in v1. When multi-locale becomes a real
need, switch to `name: LocalizedNameInput!`. Backward-incompat —
deserves its own design pass.

### OQ3 — Annotation surface

Limit v1 to well-known annotations (`name-format`, `classification-type`,
…) or accept arbitrary `[AnnotationInput!]`? Permissive accepts anything;
strict validates against a known list. **Lean: strict v1** — known set
only; expand as new admin features need them.

### OQ4 — Constraint shape for ALLOCATABLE attributes — `expectedTypeId` or
`expectedTypeKey`?

Read side uses `expectedTypeKey` (the human-meaningful key). Write side
could mirror this. But `expectedTypeId` (the UUID) is stable through
renames. **Lean: `expectedTypeId`** — referential stability beats key
mutability.

### OQ5 — Should schema-editor mutations be in `applyChanges`? — RESOLVED 2026-05-28

**No `ChangeOp` extension in v1.** Standalone `saveDynamicType` /
`deleteDynamicTypes` only. The atomic cross-type workflow (create
DynamicType + first instance) is uncommon enough that two roundtrips +
~10s hot-swap wait between them are acceptable. If demand emerges,
extending `ChangeOp` later is additive.

## Decision log

- **2026-05-28** — PRD opened as draft / placeholder. Triggered by the
  PRD 056 discussion of "how does the read/write API behave when admin
  edits a DynamicType?" Surface sketched; implementation deferred until
  Angular schema editor work begins.
- **2026-05-28** — Surface consolidated to 2 mutations:
  `saveDynamicType` (upsert) + `deleteDynamicTypes` (bulk). Earlier
  `createDynamicType` / `updateDynamicType` / `deleteDynamicType`
  triplet dropped — single roundtrip per operation; cleaner contract.
  `ChangeOp` extension dropped — cross-type atomic workflows split into
  two roundtrips. Additive if demand emerges.
- **2026-05-28** — Input shape locked:
  - `DynamicTypeInput.name` + `AttributeInput.name` use
    `MultiLanguageStringInput!` (rapla's MultiLanguageNamed surface)
  - `AttributeInput.defaultValue: DefaultValueInput @oneOf` (renamed
    from `AttributeValuePayload` for clarity — the type's only purpose
    in this PRD is to express attribute defaults)
  - Annotations as `[KeyValueInput!]` (reusable, generic)
- **2026-05-28** — `Multiplicity` enum **expanded** to
  `SINGLE | LIST | BELONGS_TO | PACKAGE` (was `SINGLE | LIST`). Folds
  rapla's `KEY_BELONGS_TO` and `KEY_PACKAGE` constraints into the
  multiplicity discriminator, matching the rapla admin UI's "Multiselect"
  dropdown. BELONGS_TO/PACKAGE are ALLOCATABLE-only; server validates.
- **OQ2, OQ3, OQ5 resolved** — generic `extraConstraints` not needed
  (Multiplicity expansion covers belongsTo/package); `expectedTypeId`
  (not key) for ALLOCATABLE constraint stability; no ChangeOp extension
  in v1.
