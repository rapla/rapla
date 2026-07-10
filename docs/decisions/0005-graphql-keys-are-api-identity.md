---
status: "accepted"
date: 2026-07-08
decision-makers: Christopher Kohlhaas
consulted: PRD 035 §11 (typeKey-only, 2026-05-29); PRD 059 phases 6+7 (2026-07-07/08); PRD 074 D5 (revalidate-and-mark)
informed: future contributors, AI coding agents
---

# DynamicType keys are the GraphQL API identity — name-based, schema-validated per kind; renames break loudly and stored views are revalidated-and-marked, never auto-migrated

## Context and Problem Statement

rapla's GraphQL schema is regenerated at runtime from the deployment's DynamicTypes
(`ClassificationSdlGenerator` + `HotSwappableGraphQlSource`). Type keys appear in the API in
several positions: generated type names (`RaumClassification`), generated filter fields
(`whereRaum`), inline fragments, and the type-selection filter. How should clients reference a
DynamicType — by stable UUID, by tolerated free string, or by schema-validated name? And what
happens to server-stored queries (custom views, saved variables) when an admin renames or
deletes a type?

Related: [PRD 035 (done) §11](../prd/done/035-graphql-foundations.md),
[PRD 059 (done)](../prd/done/059-graphql-typed-where-predicates.md),
[PRD 074 §"Storage & lifecycle"](../prd/074-graphql-declarative-views.md),
[docs/graphql.md](../graphql.md).

## Decision Drivers

- One identity currency across the whole API surface (fragments, filters, inputs, tooling) — no
  "which one do I use?" per consumer (the [PRD 035](../prd/done/035-graphql-foundations.md) §11 driver).
- Typos and stale references should fail **loudly** at validation, not silently return empty
  results (the silent-empty of the former `typeKeyIn: [String!]` cost real debugging time).
- GraphQL is name-based **by spec** — there is no standard stable-id binding for fields/types;
  inventing one (id-bound AST) is a proprietary format with its own migration burden.
- Runtime-generated schema already exists (rebuild on every DynamicType change, multi-pod via
  update-history polling) — validation currency is available "for free".

## Considered Options

1. **Name-based, schema-validated per kind** — keys verbatim as generated names; type selection
   via generated per-kind enums (`AllocatableTypeKey` = resource+person, `ReservationTypeKey` =
   reservation) on a single `typeIn` field; renames are breaking; stored views are
   **revalidated-and-marked** on every schema rebuild (kept, refused execution,
   `invalidReason` set) and fixed by the admin in GraphiQL.
2. **Free-string keys** (`typeKeyEq`/`typeKeyIn: [String!]`, the pre-2026-07-07 state) —
   tolerant of renames (old queries return empty) but silently swallows typos and stale keys.
3. **UUID-based identity** (`typeId`) — rename-stable, but nothing user-facing uses the UUID;
   fragments/generated names embed the key anyway, so renames still break the schema (rejected
   in [PRD 035](../prd/done/035-graphql-foundations.md) §11).
4. **Id-bound stored views** (`idref` AST) — rename-proof persistence, but a self-invented
   format against GraphQL's name-based spec (explicitly dropped in [PRD 074](../prd/074-graphql-declarative-views.md)).
5. **Auto-migration of stored views on rename** — rewrite stored query text/variables when a
   key changes.

## Decision Outcome

Chosen option: **1 — name-based, schema-validated per kind**, because it makes the key the
single identity currency, converts every stale/typo'd reference into an immediate validation
error, and keeps persistence on the GraphQL standard (persisted-query **text**).

Concretely:

- `Classification` exposes `typeKey: String!` only (no `typeId`); UUID escape hatch is
  `type { id }` ([PRD 035](../prd/done/035-graphql-foundations.md) §11).
- Type selection is the single generated field `typeIn: [<Kind>TypeKey!]` on
  `AllocatableFilter`/`ReservationFilter`; the redundant `typeKeyEq`/`typeKeyIn` String fields
  were **removed** ([PRD 059](../prd/done/059-graphql-typed-where-predicates.md) Phase 7, hard cut — nothing in production). Per-kind enums mean a
  wrong-kind key (an allocatable key on `ReservationFilter`) is also a validation error.
- Typed attribute predicates (`where<TypeKey>`) exist for **all** kinds — resource/person on
  `AllocatableFilter`, reservation on `ReservationFilter` — through one generator loop and one
  `WhereEvaluator` ([PRD 059](../prd/done/059-graphql-typed-where-predicates.md) Phase 6).
- **Rename/delete lifecycle:** schema rebuild → `ViewCatalogService.revalidateCustomViews()`
  marks every stored custom view `valid`/`invalidReason`. Invalid views keep their text, refuse
  execution, and the **admin fixes them in GraphiQL** ([PRD 074](../prd/074-graphql-declarative-views.md) D5/D8). **No auto-migrate, no
  silent prune.** Built-in views are code constants — keeping them schema-current is a
  developer/test responsibility.

### Consequences

- Good: typos, renamed keys, and wrong-kind keys fail at validation with a precise message —
  no silent empties anywhere on the type axis.
- Good: SPA/tooling discover valid keys via introspection of the enums (no discovery call).
- Bad (accepted): a type rename is a **breaking API change** — every stored view/variable and
  every external client referencing the old key breaks loudly until fixed by hand.
- Bad (accepted): multi-pod schema-rebuild skew (~10 s update-history polling) can briefly
  reject a just-added key's enum value on a stale pod.
- Constraint: DynamicType keys must be GraphQL-name-compliant — guaranteed by [PRD 058](../prd/058-graphql-key-spec-migration.md)'s
  startup `GraphqlKeyMigration`; `checkGraphQlCompliantName` throws if one slips through
  (GraphQL-reserved words get a trailing `_`).

### Confirmation

- `ReservationGraphQLControllerTest.typeInEnumsAreSplitPerKind` — wrong-kind keys rejected both
  directions.
- `ClassificationGraphQLControllerTest.allocatablesFilterTypeInUnknownKeyIsRejected` — unknown
  enum value is a validation error.
- `ClassificationGraphQLControllerTest.reservationDTGetsWhereInput` — reservation kinds carry
  where-inputs.
- `ViewCatalogService.revalidateCustomViews` + [PRD 074](../prd/074-graphql-declarative-views.md)'s revalidate tests — invalid views are
  marked, never pruned.
- Grep guard: `typeKeyEq`/`typeKeyIn` must not reappear in `schema.graphqls` or resolvers.

## Future possibilities

- A rename-assist (offer the admin a preview/one-click textual rewrite of invalidated views on
  key rename) — tooling **on top of** revalidate-and-mark, not silent auto-migration.
- Saved-view variables ([PRD 077](../prd/077-calendar-model-graphql.md)) could get the same revalidate-and-mark treatment if they ever
  store type keys outside the query text.

## More Information

Extracted from [PRD 035](../prd/done/035-graphql-foundations.md) §11 (`typeKey` vs `typeId`), [PRD 059](../prd/done/059-graphql-typed-where-predicates.md) phases 6+7 (D-locks in the done
PRD), and [PRD 074](../prd/074-graphql-declarative-views.md) D5 ("revalidate-and-mark") so the rationale stays visible now that [PRDs 035](../prd/done/035-graphql-foundations.md)
and 059 live in `done/`. Living contract: [docs/graphql.md](../graphql.md).
