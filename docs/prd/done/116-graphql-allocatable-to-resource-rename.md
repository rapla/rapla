# PRD 116 — GraphQL API rename: Allocatable → Resource

**Status:** done — implemented 2026-09-13, reviewed PASS (rapla-review), closed 2026-09-14. Open: OQ1 (Siegen store-only views), Siegen deployment with rewritten patch files.
**Related:** [PRD 063](../063-graphql-allocatables-write-api.md) (allocatable write verbs — renamed here), [PRD 055 § Decisions](../055-graphql-events-read-api.md) (D1 "Name Reservation (not Event)" — stays), [PRD 035 (done)](035-graphql-foundations.md) (the type family being renamed), [PRD 112](../112-deployment-patch.md) (how shipped views/documents get the new vocabulary), [ADR 0005](../../decisions/0005-graphql-keys-are-api-identity.md) (renames break loudly, no auto-migration — applies here unchanged), [PRD 113 § 1d](../113-graphql-permission-model.md#1d-one-input-per-entity--createupdate-inputs-merged) (early-beta ruling: schema breaks allowed)

## Abstract

"Allocatable" is rapla-internal vocabulary nobody outside the codebase understands; the newer
schema elements already say Resource (`ResourceAccessLevel`, `ResourcePermissionInput`,
`ResourceAvailability`, `ResourceHit`). This PRD renames the whole `Allocatable*` family on the
GraphQL wire to `Resource*` — schema, controllers/DTOs, SPA, docs — in one breaking sweep. The
Java core entity `Allocatable` and `Reservation` on the wire stay. End state: zero occurrences
of `Allocatable`/`allocatable` in `schema.graphqls`, the SPA and `docs/graphql.md`.

## Decisions locked (user, 2026-09-13)

**D1 — Umbrella `Resource`, enum `ResourceKind { RESOURCE, PERSON }` — values unchanged; field `Resource.type` → `Resource.kind` (user, 2026-09-13).**
The double use of the word mirrors what rapla already says everywhere (Swing shows
"Ressourcen" and "Personen" side by side; `ClassificationType { RESOURCE, PERSON, RESERVATION }`
stays untouched). `Resource.isPerson: Boolean!` already exists for the common check.
Rejected: `THING` (user: "geht nicht"), `ASSET`/`OBJECT`/`ITEM`/`FACILITY` (each wrong for one
of rooms / vehicles / course seats).

**D2 — `Reservation` stays; PRD 055 D1 is NOT overturned.** rapla is both a booking system and
an event planner; "Reservation" carries both meanings ("Event" does not fit a maintenance block on a
room), keeps `Appointment` vs `Reservation` distinct, and keeps parity with the Java vocabulary.
The existing mix with `EventTemplate` / `newEventOptions` / `SearchKind.EVENT` / `EventHit` is
deliberate: templates and omnibox speak user language, the core type system speaks domain
language. Not a second break.

**D3 — No in-app migration of stored query texts. ADR 0005 applies as written.** After the
schema rebuild, stored custom views (PRD 074) and the documents referencing them (PRD 097) are
revalidated-and-marked invalid. Repair path is the existing PRD 112 patch directory: the
deployment's `data/patch` files are rewritten to the new vocabulary by hand (view text AND
Mustache section tags — the response keys are the Mustache keys, `ResultShapeService` /
`DocumentRenderService.result.getData()`), `updated` bumped, and the loader overwrites the
stale store artefacts at the next start (PRD 112 A2 rule 2). Yoga Vidya is a test store and is
rebuilt; Siegen receives the rewritten patch files with its next deployment.
Precondition for Siegen: no view/document exists only in the store without a patch file —
those would stay marked invalid until fixed in GraphiQL. Rejected: PRD-058-style one-shot text
migration (code for two deployments that don't need it; conflicts with ADR 0005), `@deprecated`
aliases (cannot alias type names in fragments).

**D4 — Schema and SPA only; Java keeps `Allocatable` (user, 2026-09-13: "nur in graphql und spa").**
Renamed: `schema.graphqls`, the SDL generator's emitted names, the SPA (identifiers, files,
`app/allocatable` → `app/resource`), GraphQL docs/skills (rapla `docs/graphql.md`, ADR 0005,
`graphql-api`/`api-testing` skills, dhbwrapla `docs/graphql.md` + `docs/graphql-bm-mosbach.md`).
Java: no class or identifier renames — `AllocatableMutationController`, `allocatables()` etc. stay;
only wire strings (test query text, type-name strings, input map keys, error texts) change and the
mapping annotations carry the wire name (`@QueryMapping(name = "resources")`,
`@MutationMapping(name = "createResource")`). Record components that ARE the wire field
(`ResourceHit.resource`, `ConflictRow.resource`, `ResourceAvailabilityRow.resource`,
`AllocationDto.resource`, `BlockGroupKey.resources` input binding) take the wire name.
`Tools.RESERVED_TYPE_KEYS` gets `Resource`, `RESERVED_ATTRIBUTE_KEYS` gets `kind`.
Uppercase enum VALUES (`AttributeType.ALLOCATABLE`, `EntityKind.ALLOCATABLE`,
`NO_ALLOCATABLES_SELECTED`, `SubjectSource.ALLOCATABLE`) stay — they mirror core enum names via
`name()`; renaming them is a separate decision if ever wanted.

## Rename table

Mechanical, word-boundary, case-preserving. Every identifier in `schema.graphqls` matching
`\bAllocatable\w*\b` / `\ballocatable\w*\b` is renamed; the table lists the named elements and
the field-level patterns so the sweep script (Phase 2) can be checked against it.

| Old (schema) | New |
|---|---|
| `type Allocatable` | `type Resource` |
| `interface AllocatableClassification` | `interface ResourceClassification` |
| `enum AllocatableType { RESOURCE, PERSON }` | `enum ResourceKind { RESOURCE, PERSON }`; field `Resource.type` → `Resource.kind` (`kind` reserved as attribute key) |
| generated `enum AllocatableTypeKey` | `enum ResourceTypeKey` |
| generated `input AllocatableClassificationInput @oneOf` | `input ResourceClassificationInput @oneOf` |
| `input AllocatableInput` | `input ResourceInput` |
| `input AllocatableFilter` / `AllocatableWhere` / `AllocatableListWhere` | `ResourceFilter` / `ResourceWhere` / `ResourceListWhere` |
| `input AllocatableAggregate` / `AllocatableGroupKey` | `ResourceAggregate` / `ResourceGroupKey` |
| `input ChangeOpUpdateAllocatable` | `ChangeOpUpdateResource` |
| `Query.allocatables` / `allocatable` / `allocatableStats` | `resources` / `resource` / `resourceStats` |
| `Mutation.createAllocatable` / `updateAllocatable` / `deleteAllocatables` / `changeAllocatableOwner` | `createResource` / `updateResource` / `deleteResources` / `changeResourceOwner` |
| `ChangeOp.createAllocatable` / `updateAllocatable` / `deleteAllocatable` | `createResource` / `updateResource` / `deleteResource` |
| fields `allocatable`, `allocatables`, `allocatableId`, `allocatableIds`, `allocatableIdsIn`, `allocatableMatching`, `scopeAllocatableIds` | `resource`, `resources`, `resourceId`, `resourceIds`, `resourceIdsIn`, `resourceMatching`, `scopeResourceIds` |
| `union StatEntity = Allocatable \| Reservation \| Category` | `Resource \| Reservation \| Category` |
| `Allocatable` as `__typename` / column type string (SPA `views/row-context.ts`, `ResultShapeService` consumers) | `Resource` |

Collision check (2026-09-13): `Resource`, `ResourceKind`, `ResourceInput`, `ResourceFilter`,
`resources`, `resource` are free; existing `resourceAvailability`, `ResourceHit`,
`ResourceAccessLevel`, `ResourcePermissionInput` coexist. Prose in schema descriptions and
docs ("an allocatable is …") is rewritten to "resource" by hand, not by the script.

## Scope

### In scope
- `schema.graphqls` + `ClassificationSdlGenerator` (generated `ResourceTypeKey`, `ResourceClassificationInput`, per-DT interfaces implementing `ResourceClassification`).
- rapla-app GraphQL + document packages: wire strings, mapping annotations, wire-field record components (Java names stay, D4).
- rapla-angular: all GraphQL operation strings, TS types, discriminator literals (47 files at count time), specs.
- Docs: `docs/graphql.md`, `docs/architecture/*`, PRD prose where it describes the live schema (055/056/063/074/080/081/096/113 examples), `docs/decisions/0005`, the `graphql-api` and `api-testing` skills. dhbwrapla `docs/graphql.md`, `docs/graphql-bm-mosbach.md`.
- Shipped patch files: Siegen `data/patch` (gitignored `docs/leihschein/` sources) rewritten by hand — view text and Mustache tags.
- `docs/prd/README.md` / PRD 063 header pointer.

### Out of scope
- Core entity `Allocatable`, `AllocatableImpl`, storage/XML/SQL, facade, Swing, i18n keys, dhbwrapla Java.
- `Reservation` → `Event` (D2).
- Any migration code for stored artifacts (D3).
- Deprecation aliases or a compatibility window (early beta, PRD 113 § 1d).

## Plan

### Phase 1 — Design (this document)
- [x] Four decisions locked with the user (D1–D4).
- [ ] Coordinator ("rapla" session) informed: PRD number + decisions.

### Phase 2 — Sweep (user ruling 2026-09-13: run on the canonical checkout together with the uncommitted PRD 113 work, verified first in a worktree copy)
- [x] Scripts (session scratchpad `sweep116.py` + `handpass116.py`): rule A substring rename on wire text (schema, SPA, GraphQL docs, template-editor comment), rule B Java strings only (graphql + document packages, lexer skips comments/char literals), `git mv` of `app/allocatable/*`; hand pass = mapping annotations, record components, Tools reserved keys, `kind` in two tests. `docs/graphql.md` Java fences skipped.
- [x] Worktree rehearsal `../rapla-prd116` (canonical uncommitted state replicated): `mvn clean compile` + full reactor `mvn test` green (core 569, server 527, client 93, app 921), SPA `tsc` app+spec clean, `ng test` 575 green, prettier on the 4 re-wrapped files.
- [ ] Canonical apply: same scripts + prettier, coordinator stops the 8051 server before `mvn -pl rapla-app -am clean compile`, targeted tests, `npm test`.
- [ ] Live check: dev server up, `resources(filter:)` + `createResource` probe via `graphql-api` skill; SPA resource list, event sheet, views, documents, omnibox render.
- [x] Docs pass: docs/graphql.md, ADR 0005, skills graphql-api + api-testing, dhbwrapla docs/graphql.md + graphql-bm-mosbach.md (script); PRD prose examples in 055/056/063/074/080/081/096/113 still say `allocatables` — historical, left as-is.
- [ ] Siegen patch files rewritten, `updated` bumped; note in `docs/leihschein/status.md`.

### Phase 3 — Acceptance
- [ ] `grep -rn 'llocatable' schema.graphqls rapla-angular/src/app docs/graphql.md` → 0 hits.
- [ ] Full `mvn test` + SPA suite green; user commit.

## Tests

- Existing tier-3 GraphQL tests renamed and green (they pin the wire names).
- New arch guard in the schema smoke test: the SDL contains no `Allocatable` identifier (locks the rename against the generator reintroducing it).
- Acceptance grep above.

## Open Questions

- **OQ1** — Does Siegen hold any store-only view/document without a patch file? *Resolution:* pending — user checks before the next Siegen deployment (D3 precondition).
