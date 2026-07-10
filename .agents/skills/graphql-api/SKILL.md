---
name: graphql-api
description: Use when the user wants to probe or query the running rapla server's GraphQL API (PRD 035) — fetching allocatables/resources with their typed classification attributes, reading the schema, or confirming what a deployment-specific attribute (e.g. a room's number, or a course's year) actually holds on the wire. Covers the schema endpoint, Bearer-token auth, the generated per-DynamicType `<TypeKey>Classification` narrowing, and the `allocatables(filter:)` query. Sibling of the `api-testing` skill (REST) and `storage-inspection` skill (raw store). Assumes the dev server is running per AGENTS.md §8.
---

# Querying the rapla GraphQL API

The rapla server exposes a GraphQL API (PRD 035) at **`POST /api/graphql`**. Use it
to read allocatables/reservations with their *typed* classification attributes —
the cleanest way to see what a deployment-specific attribute (a room's
`Raumnummer`, a course's `COURSE_YEAR`, …) actually holds, resolved through
categories/allocatable references, without parsing raw store XML.

Server must be running (AGENTS.md §8). All paths are relative to the dev base
URL `http://localhost:8051`.

## Local-dev URLs at a glance

| What | URL | Auth |
|---|---|---|
| GraphQL endpoint | `POST /api/graphql` | Bearer token (§2) |
| **Raw SDL schema** (incl. generated `<TypeKey>Classification` types) | `GET /api/graphql/schema` | none |
| **Interactive GraphiQL UI** (custom, OAuth2-PKCE login button — no manual token minting) | `GET /graphiql/` | browser login (form / SPA-OAuth session) |

For interactive exploration prefer the **GraphiQL UI at
`http://localhost:8051/graphiql/`** — it ships a built-in OAuth2 login button
(Spring's bundled CDN launcher is disabled; ours owns the path), so you write
queries and get schema autocomplete without curl or hand-minting a Bearer token.
Use the curl paths below for scripted probes and for reading the SDL into a file.

## 1. Read the schema — no auth needed

The full SDL is served at **`GET /api/graphql/schema`** (plain text, unauthenticated):

```bash
curl -s http://localhost:8051/api/graphql/schema > /tmp/gql-schema.graphqls
```

Key facts about the schema (PRD 035):
- `type Query { allocatables(filter: AllocatableFilter): [Allocatable!]! ... }` is the
  entry point for resources/persons.
- Every `Allocatable` has `id`, `displayName` (locale-resolved, nameformat-derived),
  and `classification: AllocatableClassification!`.
- **Per-DynamicType typed classifications are GENERATED at startup**, one
  `<TypeKey>Classification` type per DynamicType (e.g. `RaumClassification`,
  `GebaeudeClassification`). They expose typed per-attribute fields
  (`Raumnummer: String`, `Gebaeude: Allocatable @expectedType(key:"Gebaeude")`, …).
  Because they're generated, they live in the **served** schema
  (`/api/graphql/schema`), not statically in
  `rapla-app/src/main/resources/graphql/schema.graphqls`. Always read the served
  schema to discover the actual field names for a deployment.
- The DynamicType is identified by `typeKey` (the human key, e.g. `"Raum"`), not a
  UUID. For the UUID use `type { id }`. See memory `typekey_only_classifications`.

**Hand-editing `schema.graphqls`:** multi-line SDL descriptions MUST use triple quotes
(`"""…"""`) — a single-quoted `"…"` description spanning lines is invalid SDL that parses
fine to the eye but fails at server start (scar 2026-06-21: broke a parallel session's
boot at "Syntaxfehler bei Zeile 1440"). After any hand-edit, validate the schema parses
(server start, or a parser check) before handing over.

## 2. Authenticate — Bearer token

`POST /api/graphql` requires a Bearer access token (`GET /schema` does not).
Mint one with the password grant (dev DB ships `admin` / empty password) — the
full recipe is in the `api-testing` skill. If your deployment issues long-lived
refresh tokens, exchange one for an access token instead:

```bash
RT='<your-refresh-token>'
TOK=$(curl -s -X POST http://localhost:8051/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=rapla-client&grant_type=refresh_token" \
  --data-urlencode "refresh_token=$RT" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
```

Access tokens expire after `expires_in` (3600 s) — re-mint on 401.

## 3. Query allocatables with typed narrowing

`POST /api/graphql`, body `{"query":"..."}`. Filter with `typeKeyIn:["<Key>"]`
(the DynamicType **key**, case-sensitive — `"Raum"`, not `"raum"`), and narrow to
the generated type with an inline fragment `... on <TypeKey>Classification`:

```bash
TOK=$(cat /tmp/rapla_token.txt)
Q='{"query":"{ allocatables(filter:{typeKeyIn:[\"Raum\"]}) { displayName classification { typeKey ... on RaumClassification { Raumnummer SekundaereRaumnummer Raumname Gebaeude { displayName } } } } }"}'
curl -s -X POST http://localhost:8051/api/graphql \
  -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
  -d "$Q" | python3 -m json.tool | head -40
```

Notes:
- A wrong `typeKeyIn` casing returns `{"data":{"allocatables":[]}}` (empty, no error)
  — if you get zero rows, check the key against the served schema first.
- ALLOCATABLE-typed attributes (like `Gebaeude`) resolve to a nested `Allocatable`;
  select `{ displayName }` or `{ classification { ... } }` on them.
- GraphQL errors come back as `{"errors":[...]}` with `data` partial/null — always
  check for an `errors` key before trusting `data`.

## 4. Worked example — which attribute backs `displayName`

Typed narrowing is the quickest way to confirm *which* raw attribute a resolved
`displayName` actually comes from. Selecting both candidate fields alongside
`displayName` makes the mapping obvious:

| displayName | `Raumnummer` (primary) | `SekundaereRaumnummer` (secondary) |
|---|---|---|
| 101 Lecture Hall | `101` | `1_02` |
| 204 Lab | `204` | `2_05` |

`displayName` follows the DynamicType's `nameformat` expression, which may select
a different attribute per row (e.g. the primary field for some buildings, the
secondary for others). When an export or calendar view shows an unexpected value,
this probe pins down whether the stored data is wrong or the `nameformat` /
exporter is picking the other field.

## When NOT to use this

- "What does the REST controller return?" → `api-testing` skill.
- "What's in the raw store (XML/HSQLDB/SQL)?" → `storage-inspection` skill. Note
  ALLOCATABLE/CATEGORY attributes are stored as `idref`s there; GraphQL resolves
  them — prefer GraphQL when you need resolved names.
- Server-side resolver code: use `SyncStorageOperator.*Sync`, never Promise+latch
  (memory `no_promise_latch_on_server`).
