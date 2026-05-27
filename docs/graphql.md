# GraphQL API — generic query catalog

PRD 035 Cut C exposes a read-only GraphQL endpoint at `POST /api/graphql`
covering allocatables (resources + persons), classifications, and dynamic
types. Live UIs: GraphiQL at `/graphiql/`, Scalar at `/scalar/`,
schema-as-data via introspection.

This doc is a deployment-agnostic tour. **Deployment-specific examples**
(real type keys like `Raum`/`Gebaeude`/`Lehrveranstaltung`, role
walkthroughs against actual users, capacity / building / equipment
queries) live alongside that deployment — see for example
`dhbwrapla/docs/graphql.md`.

For the schema design, the `Classification` interface split (§540 lock-in),
and the SPA-on-interface contract see
[PRD 035 §"2026-05-24 design refinement"](prd/035-rapla-mcp-server.md).

## Auth

GraphQL is a regular `/api/*` endpoint behind Spring Security. Get a JWT
via the password grant (rapla-client public OAuth client):

```bash
TOK=$(curl -s -X POST http://localhost:8051/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=rapla-client&grant_type=password" \
  -d "username=YOUR_USER" \
  -d "password=YOUR_PASS" \
  -d "scope=read+write" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
```

Then attach to every request:

```bash
gq() {
  curl -s -X POST http://localhost:8051/api/graphql \
    -H "Authorization: Bearer $TOK" \
    -H "Content-Type: application/json" \
    -d "$1" | python3 -m json.tool
}
```

GraphiQL / Scalar / Swagger UI all read the SPA's `localStorage.access_token`
— log in via the SPA at `/app/login` first; the explorer panels pick the
token up automatically.

## Schema topology

```
Query
├── me, users(filter:), user(username:)            # user surface
├── periods                                        # rapla period model
├── category(path:), categories(rootKey:)          # category tree
├── types, type(key:)                              # DynamicType descriptors
└── allocatables(filter:), allocatable(id:)        # resources + persons

Classification (interface, base — shape: typeId, type, attributes)
├── AllocatableClassification  (narrows resources + persons)
│   ├── <TypeKey>Classification  ← one per resource/person DynamicType, generated
└── ReservationClassification  (narrows reservations)
    └── <TypeKey>Classification  ← one per reservation DynamicType, generated

# Rapla-internal scaffolding types (annotation classification-type=rapla
# — period / template / defaultUser / anonymousEvent) are filtered out
# of every GraphQL surface: not in `types`, not in `type(key:)`, not in
# `allocatables*`, no generated Rapla*Classification. They remain
# accessible via the dedicated query roots (`periods`, …) and via the
# operator for the subsystems that need them.
```

The SPA must query the **interface** path only — never the typed
implementations. Typed implementations are for codegen consumers (plugin
authors, MCP integrators with a fixed deployment).

---

## Core query catalog

Substitute `"<TypeKey>"` with an actual key from `{ types { key } }`,
and `<TypeKey>Classification` with the corresponding PascalCased
generated type name (e.g. key `course-group` → type `CourseGroupClassification`).

### 1. who am I

```graphql
{ me { username name isAdmin authSource } }
```

### 2. list DynamicTypes (the deployment's schema-as-data)

```graphql
{ types { key name classificationType } }
```

Returns only user-visible classifications — internal rapla scaffolding
(`rapla:*`) is filtered server-side.

### 3. one DynamicType — full descriptor

```graphql
{
  type(key: "<TypeKey>") {
    key name classificationType
    attributes {
      key name valueType multiplicity required
      rootCategoryPath expectedTypeKey
    }
  }
}
```

`rootCategoryPath` is the allowed-root hint for category pickers;
`expectedTypeKey` is the DynamicType filter for allocatable pickers
(PRD 035 §5 widget config).

### 4. allocatables — SPA / descriptor-driven (interface path)

The right SPA shape: a single generic renderer iterates the descriptor and
the value list. Never mentions deployment-specific type names.

```graphql
{
  allocatables {
    id
    displayName
    type
    classification {
      typeId
      type { key }
      attributes {
        key
        stringValue
        intValue
        boolValue
        dateValue
        categoryValue { id path name }
        allocatableValue { id displayName }
        categoryValues { name }
        allocatableValues { id displayName }
      }
    }
  }
}
```

### 5. allocatables — typed narrowing (codegen consumer path)

DO NOT do this from the SPA — type names are deployment-coupled. Codegen
consumers with a fixed deployment use this for compile-time field access:

```graphql
{
  allocatables(filter: { typeKeyEq: "<TypeKey>" }) {
    displayName
    classification {
      ... on <TypeKey>Classification {
        # Replace with actual attribute keys from query 3.
        someStringAttribute
        someIntAttribute
      }
    }
  }
}
```

### 6. filters

```graphql
# only one type
{ allocatables(filter: { typeKeyEq: "<TypeKey>" }) { displayName } }

# only persons
{ allocatables(filter: { isPersonEq: true }) { displayName } }

# substring name search (case-insensitive)
{ allocatables(filter: { nameContains: "foo" }) { displayName } }

# combined AND
{ allocatables(filter: { typeKeyEq: "<TypeKey>", nameContains: "foo" }) { displayName } }
```

### 7. follow a reference attribute

When an attribute has `valueType: ALLOCATABLE` and `expectedTypeKey: "<OtherKey>"`,
chain through to the referenced entity's typed classification:

```graphql
{
  allocatables(filter: { typeKeyEq: "<TypeKey>" }) {
    displayName
    classification {
      ... on <TypeKey>Classification {
        someStringAttribute
        someReferenceAttribute {     # this is an Allocatable
          displayName
          classification {
            ... on <OtherKey>Classification {
              someStringAttributeOnTarget
            }
          }
        }
      }
    }
  }
}
```

§12 is enforced at every step — an unreadable target yields a null
reference, never a partial object.

### 8. multi-query batch

One round-trip for a workbench:

```graphql
query Workbench {
  me { username isAdmin }
  types: types { key }
  things: allocatables(filter: { typeKeyEq: "<TypeKey>" }) {
    id displayName
  }
  serverNow: serverTime
}
```

### 9. introspection — see the interface split

```graphql
# only resources + persons
{ __type(name: "AllocatableClassification") { possibleTypes { name } } }

# only reservations
{ __type(name: "ReservationClassification") { possibleTypes { name } } }

# detailed shape of one generated type
{
  __type(name: "<TypeKey>Classification") {
    interfaces { name }
    fields { name type { name kind ofType { name } } }
  }
}
```

### 10. interface mismatch (validation error, by design)

A resource-typed `Allocatable.classification` rejects an event-only
narrowing at validation time — SPA mistakes never produce silent nulls:

```graphql
{
  allocatables(filter: { typeKeyEq: "<ResourceKey>" }) {
    classification { ... on <ReservationKey>Classification { typeId } }
  }
}
```

Yields:
```
Fragment cannot be spread here as objects of type 'AllocatableClassification'
can never be of type '<ReservationKey>Classification'
```

---

## Hot-swap probe

When an admin saves a DynamicType change (e.g. adds an attribute):

1. `GraphQlSchemaRebuilder` polls `operator.getDynamicTypes()` every 10 s.
2. SHA-256 of the generated SDL is compared against the last run —
   identical SDL short-circuits the rebuild.
3. Otherwise the schema is rebuilt and atomically swapped; in-flight
   queries finish on their captured `GraphQL` instance.

Verify:

```graphql
# Before admin save:
{ __type(name: "<TypeKey>Classification") { fields { name } } }
# Admin adds an attribute "NewFlag" on <TypeKey>.
# Wait ~10s, then re-query:
{ __type(name: "<TypeKey>Classification") { fields { name } } }
# 'NewFlag' should now appear in the fields list.
```

If introspection doesn't update but `type(key:)` does, the rebuilder is
the issue, not the operator — check server logs for
`GraphQL schema rebuilt — N DynamicType classification(s) regenerated`.

---

## §12 expectations (permission boundary)

Every read goes through `PermissionController.canRead` at the output
boundary — see AGENTS.md §12 and PRD 035 line 491-500. The contract:

- **Anonymous callers** see `me: null`, empty `users`, empty `allocatables`.
  Trivial probes (`hello`, `serverTime`, `version`) and the schema /
  introspection remain open (admin/debug aid).
- **Self-visibility**: every authenticated user always sees themselves in
  `users` (and via `user(username: <self>)`), regardless of admin status.
- **Other users**: only those the caller `canAdminUser` are visible.
- **Allocatables / attribute references**: filtered by `canRead`;
  unreadable entries are silently dropped. Existence not leaked.
- **Mixed-id requests**: a query like `allocatable(id: X)` returns the
  same `null` for "doesn't exist" and "exists but you can't see it".

---

## Limitations (Cut C)

- **No reservations yet.** `Reservation.classification:
  ReservationClassification!` is in the schema but the resolver lands
  next batch. Today you can only see reservation types via `types` and
  introspect their generated implementations.
- **No mutations.** Read-only. PRD 035 §6 bulk-mutation design is locked
  but not implemented.
- **No subscriptions.** Polling only (10 s) for schema changes; queries
  themselves are request/response.
- **Rapla-internal types fully hidden.** Templates, periods, default-user,
  and anonymous-event don't appear in any GraphQL surface. Use the
  dedicated query roots (`periods`, …) for those.
