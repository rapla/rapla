# PRD 061 — GraphQL DynamicType Mutations v2 (deferred follow-ups)

**Status:** draft — opened 2026-05-29; picks up 5 items deferred from PRD 057 v1

**Date:** 2026-05-29

**Parent / v1:** [PRD 057 (done) — DynamicType Mutations v1](done/057-graphql-dt-mutations-v1.md).
v1 shipped 9 tier-3 tests covering create + replace + delete + admin gate +
key collision + multiplicity validation + REFERENCE_NOT_FOUND on unknown id.

**Triggered by:** the Angular schema editor UI (PRD 057 trigger) needs the
deferred behaviour before admins can safely run real type changes against
live data. Specifically Example 6 (valueType migration) is the
highest-risk gap — silent data loss footgun.

## Goal

Lock the v1 surface's behaviour on the five boundaries v1 left soft:
existing-data migration on valueType change, default-value coercion
semantics, annotation governance, hot-swap responsiveness, and richer
delete-blocker reporting. Brings the editor surface up to "safe for
admins to run against production datasets" instead of "safe for
happy-path schema-from-scratch fixtures".

## Scope

In:
1. valueType-change-with-data — Example 6 from PRD 057 — explicit policy
   (reject vs coerce vs silent-drop) + tier-3 tests + admin warning UX
2. DefaultValueInput coercion semantics — variant-vs-valueType matching
   rules + invalid-combo rejection
3. Annotation governance — strict allow-list or open-ended; lock the
   v1-known set + extension policy
4. Hot-swap UX — drop poll interval globally OR add post-mutation
   faster-poll trigger
5. `deleteDynamicTypes` referrer reporting — per-type breakdown, total
   counts, paginated drill-down

Out:
- Multi-locale name editing — OQ2 in PRD 057, separate concern, defer to
  a localization PRD
- Per-attribute permissions / read-only-on-edit fields — defer until a
  real consumer asks
- Soft-delete / "deprecation" semantics for DynamicTypes — defer
- DynamicType migration tooling for renaming/restructuring (beyond what
  PRD 057 v1 ships)
- Cross-deployment schema sync / template DynamicTypes

## Items

### 1. valueType-change-with-data migration

PRD 057 Example 6 is the worked case (STRING → INT on
`AnzahlPlaetzeInsgesamt`). v1 inherits rapla's `AttributeImpl.commitChange`
behaviour — silently drops unparseable values. Footgun: admin clicks
"save", 50 entities silently lose data.

**Decision matrix.** Per (old → new) valueType pair: `COERCE` (always
safe), `REJECT` (disallow), or `COERCE_OR_REJECT` (try-parse; reject the
whole mutation if any entity fails, surfacing counts so the admin can
clean data first).

| from \ to     | STRING            | INT               | BOOLEAN           | DATE              | CATEGORY  | ALLOCATABLE |
|---------------|-------------------|-------------------|-------------------|-------------------|-----------|-------------|
| STRING        | —                 | COERCE_OR_REJECT  | COERCE_OR_REJECT  | COERCE_OR_REJECT  | REJECT    | REJECT      |
| INT           | COERCE            | —                 | COERCE_OR_REJECT  | REJECT            | REJECT    | REJECT      |
| BOOLEAN       | COERCE            | COERCE            | —                 | REJECT            | REJECT    | REJECT      |
| DATE          | COERCE            | REJECT            | REJECT            | —                 | REJECT    | REJECT      |
| CATEGORY      | COERCE (render path) | REJECT         | REJECT            | REJECT            | REJECT (root change) | REJECT |
| ALLOCATABLE   | REJECT            | REJECT            | REJECT            | REJECT            | REJECT    | REJECT (type change) |

Rationale: lossless widenings to STRING always coerce; narrowings
try-parse and reject on first failure; ALLOCATABLE changes always reject
(references can't be coerced); CATEGORY root changes always reject (root
swap reinterprets stored values — admin must clear first).

**Error shape** for `COERCE_OR_REJECT` failures:

```graphql
errors: [{
  message: "Cannot change AnzahlPlaetzeInsgesamt from STRING to INT — 3 entities have unparseable values",
  extensions: {
    code: "INVALID_VALUE_TYPE_CHANGE",
    attributeKey: "AnzahlPlaetzeInsgesamt",
    fromValueType: "STRING",
    toValueType: "INT",
    unparseableCount: 3,
    affectedEntityCount: 3,
    sampleEntities: [
      { id: "alloc-1", displayName: "Hörsaal 1", currentValue: "approx. 50" },
      { id: "alloc-7", displayName: "Labor 3",   currentValue: "n/a" },
      { id: "alloc-9", displayName: "Aula",      currentValue: "see notes" }
    ]
  }
}]
```

`sampleEntities` capped at 50 (same as §5 referrers).

**Tier-3 tests** — one per non-trivial matrix cell. Seed a DynamicType +
N entities with mixed parseable/unparseable values, attempt the change,
assert code + extension shape + (for `COERCE`) migrated values.

**SPA UX note.** Dry-run preview is desirable but out of scope; admin
discovers unparseable entities by attempting the mutation and reading
the extension.

### 2. DefaultValueInput coercion

`DefaultValueInput @oneOf` ships in v1. Open in v1: server doesn't
enforce that the `@oneOf` variant matches the attribute's `valueType` +
`multiplicity` — wrong shapes round-trip silently (e.g.
`dateValue: "2026-01-01"` on a STRING attribute becomes the string
`"2026-01-01"`).

**Lock the rules:**

| attribute shape                         | required variant      | rejected variants → `INVALID_DEFAULT_VALUE` |
|-----------------------------------------|-----------------------|---------------------------------------------|
| STRING + SINGLE                         | `stringValue`         | everything else                             |
| STRING + LIST                           | `stringValues`        | everything else                             |
| INT + SINGLE                            | `intValue`            | everything else                             |
| BOOLEAN + SINGLE                        | `boolValue`           | everything else                             |
| DATE + SINGLE                           | `dateValue`           | everything else                             |
| CATEGORY + SINGLE                       | `categoryId`          | everything else                             |
| CATEGORY + LIST                         | `categoryIds`         | everything else                             |
| ALLOCATABLE + SINGLE                    | `allocatableId`       | everything else                             |
| ALLOCATABLE + LIST/BELONGS_TO/PACKAGE   | `allocatableIds`      | everything else                             |

Additional validation:
- `categoryId` / `categoryIds` must resolve under the attribute's
  `rootCategoryId` subtree — else `INVALID_DEFAULT_VALUE` with extension
  `{reason: "not_under_root", offered: id, root: rootCategoryId}`.
- `allocatableId` / `allocatableIds` must resolve to allocatables of type
  `expectedTypeKey` — else `INVALID_DEFAULT_VALUE` with extension
  `{reason: "wrong_type", offered: id, expectedTypeKey: ...}`.
- `defaultValue: null` is always valid (no default).

**Tier-3 tests** per (valueType × multiplicity × variant) cell —
positive for the required variant + one negative per attribute shape
covering the likely admin mistake (`stringValue` on INT, etc.).

### 3. Annotation governance

PRD 057 OQ3: v1 accepts open-ended `[KeyValueInput!]`. v2 locks to a
strict allow-list. Known annotation keys (best-effort survey from
`DynamicTypeAnnotations` / `AttributeAnnotations` / `Annotatable` —
reconcile against actual constants at implementation time):

DynamicType-level:
- `name-format` — template string for `displayName` (e.g. `"{name}"`)
- `nameFormatPlanning` — alternate format for the planning view
- `classification-type` — `RESOURCE` / `PERSON` / `RESERVATION` — but
  this is already a first-class enum field on `DynamicTypeInput`;
  rejected as an annotation key in v2 to avoid two sources of truth
- `colors` — calendar block colour palette key
- `treatAsPerson` — boolean flag for resources that should sort/render
  as persons
- `conflicts` — conflict-detection mode (`always` / `ifAvailable` / `never`)

Attribute-level:
- `expectedType` — already a first-class field (`expectedTypeKey`) —
  rejected as annotation
- `category` (root category id) — already a first-class field — rejected
- `editView` — admin-only UI hint (`visible` / `hidden`)
- `sortOrder` — integer ordering hint for the admin editor
- `multiSelect` — already encoded in `Multiplicity` — rejected

**Strict mode.** Unknown keys reject with `UNKNOWN_ANNOTATION` and
extension `{key: "...", knownKeys: [...]}`. Catches typos
(`name_format` vs `name-format`); prevents the SPA from writing
annotations nothing consumes.

**Extension policy.** Plugins extend the allow-list by contributing
`AnnotationKeyDescriptor` beans via
`@Extension(provides = AnnotationKeyDescriptor.class)`; controller
assembles the runtime allow-list from rapla-core constants +
plugin descriptors at startup. No schema directive in v2; a
`@knownAnnotation` directive on `KeyValueInput.key` is the additive
path if introspection-surfaced docs are needed later.

**Tier-3 tests:**
- `saveDynamicType` with unknown annotation key → `UNKNOWN_ANNOTATION`
- `saveDynamicType` with `classification-type` annotation key →
  `UNKNOWN_ANNOTATION` (forbidden — first-class field)
- `saveDynamicType` with valid `name-format` → succeeds; reads back via
  v1 `dynamicType { annotations }`

### 4. Hot-swap UX tightening

PRD 057 OQ1. v1 inherits PRD 055 Cut C's 10s `GraphQlSchemaRebuilder`
poll. Schema editor wants ~2s for admin feedback.

Options:
- **Lower globally to 2s** — simple; small idle-CPU cost on every
  deployment (the poll is a single timestamp check, probably negligible
  but unverified).
- **Post-mutation direct trigger** — `saveDynamicType` /
  `deleteDynamicTypes` invoke `GraphQlSchemaRebuilder.rebuild()` before
  returning. Adds rebuild latency to the mutation response; eliminates
  the poll wait. Response carries `schemaRebuildComplete: Boolean!` for
  admin SPA confirmation.

**Lean: direct-trigger.** Simpler UX, no cost for non-admins, removes
the "wait 10s and hope" pattern from Examples 2 + 5. The latency hit
lands only on the admin (already in a "wait for save" state). Concurrent
edits across sessions resolve correctly — rebuilder is idempotent +
`expectedLastChanged` protects against lost-update.

**Schema addition** (additive — no v1 break):

```graphql
type Mutation {
  saveDynamicType(input: DynamicTypeInput!,
                  expectedLastChanged: LocalDateTime): SaveDynamicTypeResult!
  # was: returned DynamicType! directly in v1
}

type SaveDynamicTypeResult {
  dynamicType:           DynamicType!
  schemaRebuildComplete: Boolean!
  generatedTypeName:     String         # e.g. "RaumClassification" — null on delete-only paths
}
```

v1 callers selecting `saveDynamicType { id key }` migrate to
`saveDynamicType { dynamicType { id key } }`. Breaking schema change,
acceptable since v1 has ~0 production consumers and we control the SPA.

**Fallback.** Keep the 10s poll as safety net — if the direct trigger
fails, response returns with `schemaRebuildComplete: false` and the
next poll catches up within 10s.

**Tier-3 test.** After `saveDynamicType` returns, immediately query
`__type(name: "NewClassification")` — must resolve synchronously.

### 5. `deleteDynamicTypes` referrer reporting

v1's `REFERENCE_EXISTS` extension is a flat list of 50 referrer ids
(Example 4 in PRD 057). v2 enriches for the admin UI:

- **Per-kind breakdown** — referrers can be reservations (reservation
  type), allocatables (resource/person type), or attributes on OTHER
  DynamicTypes pointing here via `expectedTypeKey`. Break the count by
  kind.
- **Total counts** — `"1247 reservations, 23 allocatables"` — useful
  without paging through ids.
- **Paginated drill-down** — `referrersOf(dynamicTypeId, first, after)`
  with cursor pagination per PRD 055's connection pattern.

**Locked v2 error extension shape:**

```graphql
errors: [{
  message: "Cannot delete DynamicType — 1270 entities still reference it",
  extensions: {
    code: "REFERENCE_EXISTS",
    deletedEntityId: "dt-raum-uuid",
    deletedEntityKind: "DYNAMIC_TYPE",
    totalReferrerCount: 1270,
    countByKind: {
      RESERVATION:    1247,
      ALLOCATABLE:    23,
      DYNAMIC_TYPE:   0           # attributes on OTHER types pointing at this one via expectedTypeKey
    },
    sampleReferrers: [
      { kind: "RESERVATION", id: "res-1",   displayName: "Vorlesung Mo 8:00" },
      { kind: "RESERVATION", id: "res-2",   displayName: "Übung Di 10:00" },
      # ... capped at 50, distributed proportionally across kinds when possible ...
    ]
  }
}]
```

**Drill-down query** (read-side, admin-only per §12):

```graphql
extend type Query {
  referrersOf(
    dynamicTypeId: ID!,
    kind:          ReferrerKind,         # optional filter — null = all kinds
    first:         Int     = 50,
    after:         String                  # cursor from previous page
  ): ReferrerConnection!
}

enum ReferrerKind { RESERVATION ALLOCATABLE DYNAMIC_TYPE }

type ReferrerConnection {
  totalCount: Int!
  pageInfo:   PageInfo!
  edges:      [ReferrerEdge!]!
}

type ReferrerEdge {
  cursor: String!
  node:   Referrer!
}

type Referrer {
  kind:        ReferrerKind!
  id:          ID!
  displayName: String!
}
```

Admin only (`§12`). No leak — non-admin callers reject identically for
unknown id and forbidden id (`PERMISSION_DENIED` in both cases — admin
gate is uniform across the v2 admin surface).

**Tier-3 tests:**
- `deleteDynamicTypes` with 100 reservation referrers → extension
  reports `RESERVATION: 100`, `sampleReferrers` length = 50
- `deleteDynamicTypes` with mixed-kind referrers (50 reservations + 23
  allocatables) → `countByKind` correct, sample drawn from both kinds
- `referrersOf` non-admin → `PERMISSION_DENIED`
- `referrersOf` cursor pagination through 200 referrers → 4 pages of
  50, last page's `pageInfo.hasNextPage = false`
- `referrersOf` with `kind: ALLOCATABLE` filter → only allocatable
  referrers in the result

## Plan

1. **valueType migration policy** — highest risk; matrix encoded as a
   `ValueTypeMigrationPolicy` table + tier-3 tests per cell.
2. **DefaultValueInput coercion** — depends on (1) for the
   default-on-valueType-change case.
3. **Annotation allow-list** — survey `DynamicTypeAnnotations` /
   `AttributeAnnotations` constants, add strict-mode gate.
4. **Hot-swap direct trigger** — inline `rebuild()` +
   `SaveDynamicTypeResult` wrapper. Migrate callers.
5. **Referrer reporting** — extension shape + `referrersOf` query +
   `Referrer{,Edge,Connection}` types.

Slices independently mergeable; (4) is the only breaking schema change —
coordinate SPA editor's response-shape update in the same release.

## Tests

Tier-3 per item per matrix cell. Same pattern as v1:
`@SpringBootTest` + `@AutoConfigureMockMvc(addFilters = false)` +
`@WithMockUser(roles = "ADMIN")`; non-admin variants for §12 rejection.

- ~10 valueType migration matrix (per non-trivial cell + error shape)
- ~8 DefaultValueInput variant matching (per shape + wrong-variant)
- ~3 annotation allow-list (known, unknown, first-class-as-annotation)
- ~2 hot-swap direct trigger (synchronous visibility + rebuild fallback)
- ~5 referrer reporting (breakdown, kind filter, pagination, admin
  gate, sample distribution)

Target: ~28 new tier-3 tests on top of v1's 9.

## Open questions

**OQ1 — valueType migration default policy.** Matrix proposes
`COERCE_OR_REJECT` as the narrowing default. Alternative — silent-drop
matching v1 with a count in a non-error response field — is gentler but
preserves the footgun. Confirm before implementation; if silent-drop
wins, add a `dryRun: Boolean` parameter so the admin can preview the
drop count.

**OQ2 — annotation extension policy + source.** The
`@Extension(provides = AnnotationKeyDescriptor.class)` path is sketched
but unverified; confirm rapla's extension mechanism handles
`Set<AnnotationKeyDescriptor>` collection-injection. Lean: hybrid —
rapla-core constants + plugin-contributed descriptors unioned at
controller construction. Fallback: server-config YAML allow-list.

**OQ3 — hot-swap trigger latency budget.** Direct-trigger adds rebuild
cost to every `saveDynamicType`. Bench on a realistic deployment
(~50 DynamicTypes, ~200 attributes) — if rebuild is >1s the breaking
`SaveDynamicTypeResult` change buys little vs just lowering the poll
to 2s globally.

**OQ4 — `referrersOf` admin gate.** Admin-only per parity with the rest
of v2. If a future "show me what depends on this before I edit it"
non-admin use case emerges, relax per §12 (per-entity readability +
existence-leak rules) — out of scope for v2.

## Cross-references

- [PRD 057 (done) — DynamicType Mutations v1](done/057-graphql-dt-mutations-v1.md) — the v1 surface this PRD extends
- [PRD 035 (done) — GraphQL foundations](done/035-graphql-foundations.md) — §5a category-kind cascade affects valueType migration on CATEGORY attributes
- [PRD 055 — GraphQL Events Read API](055-graphql-events-read-api.md) — Cut C `GraphQlSchemaRebuilder` is the hot-swap mechanism v2 §4 directly invokes; connection / cursor pagination pattern reused by `referrersOf`
- [PRD 056 — GraphQL Events Write API](056-graphql-events-write-api.md) — error code taxonomy + `ValidationError` shape this PRD's error extensions follow
- [PRD 062 — GraphQL API Robustness](062-graphql-api-robustness.md) — error-mapping + transport invariants shared across the GraphQL mutation surface (renumbered from 058 on 2026-05-29)

## Decision log

- **2026-05-29** — PRD opened as draft, capturing the 5 deferred items
  from PRD 057 v1's status header. Triggered by the same Angular schema
  editor UI work that prompted PRD 057 itself — v1 unblocks the basic
  CRUD; v2 unblocks safe operation against live data.
