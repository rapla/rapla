# PRD 098 — Server artifact store (views, templates, CSS)

**Status:** done — 2026-07-08 (entity + both backends + catalog + view switch-over, 25 tests green;
client-exclusion test landed). [PRD 097](../097-event-html-templates-mustache.md) consumes the store (kind=TEMPLATE/CSS/PARTIAL/IMAGE) — the
generic artifact CRUD/upload endpoint and the IMAGE mimeType guard land with that first consumer.
**Related:** [PRD 074](../074-graphql-declarative-views.md) (declarative GraphQL views — its `ViewCatalogService` preferences storage is what
this replaces), [PRD 097](../097-event-html-templates-mustache.md) (Mustache event templates — first new consumer; its `DocumentTemplate`
storage lands directly here instead of a second preferences key), [PRD 077](../077-calendar-model-graphql.md) (saved calendar views —
explicitly NOT moved, see out-of-scope), [PRDs 082](../082-storage-memory-model.md)–087 (storage modernization — sequencing note in D5)

## Abstract

Admin-authored server-side artifacts — stored GraphQL view queries ([PRD 074](../074-graphql-declarative-views.md)), Mustache templates
([PRD 097](../097-event-html-templates-mustache.md)), CSS/partials — currently live as one JSON array per kind inside the single system
`Preferences` entity. That gives whole-entity conflict granularity, ships template/CSS bodies to
every Swing client via preferences sync, has no per-artifact metadata, and breeds one copy-pasted
catalog service per kind. This PRD introduces a dedicated **server-only entity type**
(`StoredArtifact`, modeled on the existing `ExternalSyncEntity` wiring) inside the rapla store, so
artifacts keep multi-pod freshness (read-through, OQ1), both backends (file XML + DB), export/backup,
and locking — while gaining per-artifact conflict granularity, per-artifact timestamps/author, and
zero client-sync payload. Measurable end state: `ViewCatalogService` reads/writes `StoredArtifact`
entities, the `org.rapla.graphql.customViews` preferences key is retired, and no artifact body ever
appears in a client `UpdateEvent`.

## Why preferences is the wrong home (recorded 2026-07-08)

1. **Conflict granularity = the whole system-prefs entity.** Every artifact save rewrites the full
   JSON blob and bumps the one entity all system settings share — unrelated concurrent admin edits
   collide with `RaplaNewVersionException`.
2. **Client-sync bloat.** System preferences are pushed to every Swing client; Mustache HTML + CSS
   bodies are KB–tens-of-KB server-side render inputs no client needs.
3. **No per-artifact metadata.** No lastChanged, no author — "who broke the Leihschein template" is
   unanswerable.
4. **N parallel catalog services.** Views, then templates ([PRD 097](../097-event-html-templates-mustache.md)), then CSS/partials — each with
   its own `TypedComponentRole` key and hand-rolled visibility JSON.

What preferences quietly provides and any replacement must keep: multi-pod freshness (preferences
get it via the ~10 s update-history poll; the artifact store gets it via read-through instead —
OQ1), dual backend (FileOperator + DBOperator), inclusion in XML export/backup, transactional
store + locking. This is why "just add a Spring JDBC table" or files-on-disk are
rejected (D1 alternatives).

## Implementation

### Entity

`StoredArtifact` interface (`org.rapla.entities.storage`) + `StoredArtifactImpl extends
SimpleEntity` in rapla-core, mirroring `ExternalSyncEntity`/`ExternalSyncEntityImpl`:

```
StoredArtifact { kind (VIEW|TEMPLATE|CSS|PARTIAL|IMAGE — string, open set),
                 name (unique per (kind, owner)), body (query text / mustache / css / base64),
                 metadataJson (isPublic, groups, defaultVariables, mimeType, kind-specific extras),
                 owner (nullable Ownable ref — ALWAYS null for now, see D7),
                 createDate / lastChanged / lastChangedBy }
```

Visibility (`isPublic`/`groups`) stays application-level JSON interpreted by the catalog service —
NOT integrated into `PermissionController` entity permissions (D4).

### What belongs in the store — admission test (discussed 2026-07-08)

An artifact is admitted when ALL hold: (1) **application-scoped content** — one set per deployment,
no per-user rows (D7 prepares the schema for a later user scope, but the store's identity is
application content); (2) **server-side input to rendering/querying** — never synced to clients
as-is; (3) **small and few** — text or logo-sized binary, dozens of rows, under the OQ2 cap;
(4) **runtime-editable content, not code** — no rebuild to change, no executable logic (the [PRD 097](../097-event-html-templates-mustache.md)
no-eval line). The store is a "content customization without code customization" mechanism —
long-term it reduces what `custom/`-overlay deployments ([PRD 003](../003-custom-deployments-after-spring-migration.md)) exist for.

Intended consumers: view queries ([PRD 074](../074-graphql-declarative-views.md)), document templates + CSS + partials + same-origin
images ([PRD 097](../097-event-html-templates-mustache.md)), later plausibly HTML mail/notification templates, SPA branding (different trust
surface — needs its own review), admin announcements, stored-views-as-MCP-tools ([PRD 060](../060-graphql-mcp-foundations.md)).
Explicitly NOT: user attachments (large, user-authored, per-entity — own PRD if ever), per-user UI
state (user preferences: [PRD 077](../077-calendar-model-graphql.md) saved views, [PRD 089](../089-server-side-recents-favorites.md) recents), plugin/system settings
(system preferences hold *settings*; this store holds *content*), executable content of any kind.
BUILTIN views stay hardcoded in `ViewCatalogService` — not seeded/overridable in the store (upgrade
conflicts; revisit only with a concrete need).

### Storage wiring — mirror the ExternalSyncEntity integration points

| Concern | Precedent to mirror |
|---|---|
| Type registration | `RaplaType.java:67` (`new RaplaType<>(ExternalSyncEntity.class, "importexport")`) |
| Change records | `UpdateEvent.java:309` type branch + list field |
| Keep off clients | `UpdateDataManagerImpl:484` (`raplaType == ExternalSyncEntity.class → false`) |
| DB table | `RaplaSQL.java:3084` `ImportExportStorage extends RaplaTypeStorage` → new `ARTIFACT` table |
| XML persistence | `ImportExportWriter` / `RaplaMainReader`+`RaplaMainWriter` sections |
| Operators | `FileOperator` / `DBOperator` handling of the sync-entity side path |

Persistence details (worked out 2026-07-08): SQL = `ArtifactStorage extends RaplaTypeStorage` in
`RaplaSQL.java` (cf. `ImportExportStorage`, line ~3084), table **`ARTIFACT`** with columns
`ID VARCHAR(255) NOT NULL PRIMARY KEY` — the **natural key** `kind + ":" + name` (later
`kind:owner:name`), no store-generated id (OQ4; same caller-composed-id shape as
`ExternalSyncEntity`'s FOREIGN_ID). Uniqueness is structural via the PK. Further columns:
`KIND VARCHAR(50) KEY` (bare `KEY` = single-column secondary index, `AbstractTableStorage`
line ~183/265 — for catalog list-by-kind), `NAME VARCHAR(255), OWNER_ID VARCHAR(255) (nullable),
BODY TEXT, METADATA TEXT, CREATED_AT TIMESTAMP, CHANGED_AT TIMESTAMP KEY, CHANGED_BY VARCHAR(255)`
(as-built; `CHANGED_BY` not `LAST_CHANGED_BY` — see Implementation findings).
Rename is NOT a catalog operation (OQ4): save-under-new-name + delete-old, today's view semantics.
`TEXT` is dialect-mapped by `AbstractTableStorage.getDatabaseProductType` (MariaDB/MySQL→`LONGTEXT`
4 GB, H2→`CLOB`, HSQLDB→`VARCHAR(16M)`, Postgres `TEXT` unbounded) — no manual MEDIUMTEXT needed.
**Why character type, not BLOB** (asked 2026-07-08): the file backend forces base64 in `data.xml`
anyway, so BLOB would only avoid encoding on the DB half at the cost of an asymmetric `byte[]`-vs-
String body across backends and the migration channel; ~4/5 of the content is text (queries,
templates, CSS, JSON); `TEXT` reuses the existing dialect mapping + `setText` helpers
(`ExternalSyncEntity.DATA` precedent); and a text column stays inspectable in SQL clients/exports.
The +33 % base64 overhead only matters if images become large/numerous — at which point the answer
is the [PRD 097](../097-event-html-templates-mustache.md) multipart upload endpoint (binary never rides a GraphQL string), not a column retype. XML = `ArtifactWriter`/`ArtifactReader` (cf. `ImportExportWriter`):
section `<rapla:artifacts>` written by a `RaplaMainWriter.printArtifacts()` (cf.
`printImportExport()`), read via `localnameTable.put("artifacts", ...)` in `RaplaMainReader` +
`IOContext` registration; small fields as attributes, `body`/`metadata` as `printEncode`d child
elements.

Whether artifacts ride the normal `LocalCache` path or the `ExternalSyncEntity` side channel
(`saveData(LocalCache, Collection<ExternalSyncEntity>, version)`) is OQ1 — sync entities bypass the
cache because Exchange mappings number in the thousands; artifacts number in the dozens, so the
plain cached-entity path may be simpler *provided* the client-exclusion at `UpdateDataManagerImpl`
still holds.

### Caching stance — none for the store; content-hash memos only for derived values

Prior art (recorded 2026-07-08): Grafana serves dashboards as JSON blobs straight from SQL per
request (no cache); WordPress memoizes options per-request only; Keycloak's Infinispan
cache-with-cluster-invalidation exists because realm config is read per token operation — a read
rate artifacts never approach; Jira/Confluence's Ehcache + invalidation events are a recurring
stale-node bug source. Conclusion: at dozens of rows and one indexed query per catalog read/render,
read-through SQL is fast enough (single-digit ms vs the GraphQL execution it feeds) — **no cluster
invalidation machinery** (consistent with OQ1).

Sanctioned cache shape (decided 2026-07-08, **redesigned pull-based 2026-07-09** — the original
whole-catalog eager snapshot did `SELECT *` every refresh, so large bodies travelled over JDBC only
to be discarded; the cache was two-tier but the SQL read behind it was not): reads are
**demand-driven**, process-local, 10 s TTL, invalidated locally on every write.
- **`find(kind, name)`** — the hot path (view execution, template render) — is a **point read on
  the natural-key PK** (`WHERE ID = ?`), cached per entry when the body is ≤ 1 MiB; larger bodies
  are deliberately uncached and read-through per use ([PRD 097](../097-event-html-templates-mustache.md)'s serving endpoint adds JDBC
  streaming + ETag/304 for those). 500 users requesting the same template = one point query per
  10 s per pod.
- **`list(kind)`** — the rare path (catalog/admin listings) — is **metadata-only**: the SQL
  projection selects every column except BODY (`CAST(NULL AS CHAR(1))` in its place), so listing
  can never transfer bodies regardless of table content; demand-loaded snapshot, same TTL.
- Consumers needing listed bodies (the view catalog: `listViewsForCaller` needs every query text)
  do list + per-entry `find` — cold that is 1 + N point queries (N = dozens, once per pod per TTL),
  warm it is zero SQL. Deletions need no propagation: nothing long-lived holds the catalog.

Semantics unchanged: same-pod read-your-own-writes (invalidate-on-write), other pods ≤10 s stale —
the same staleness bound the update-history poll gives every other rapla entity, so no new
consistency class. Net effect: a large body **never leaves the database unless an actual use
requests it**, which makes OQ2's "the cap can be generous because the cache is immune" true at the
SQL layer, not just in cache memory. No cross-pod invalidation messages, ever (the Keycloak/Ehcache
trap). Also permitted: memoize *derived* values — compiled JMustache template, parsed+validated
view document — keyed by body content hash (self-invalidating, §16-safe pure derivation).

### Catalog service

One generic `ArtifactCatalogService` (CRUD + visibility + per-kind validation hook) replaces the
preferences `loadStored()`/`persist()` in `ViewCatalogService`; view-specific logic (GraphQL parse +
schema validation, `@view` title extraction, BUILTIN list) stays in `ViewCatalogService`, now
delegating storage. [PRD 097](../097-event-html-templates-mustache.md)'s `DocumentCatalogService` becomes a thin kind=TEMPLATE consumer.

### No migration

Existing custom views in the `org.rapla.graphql.customViews` preferences key are **cut off
completely — no migration, no compatibility, no legacy tests** (user decision 2026-07-08 — the
feature is not in production; dev-stage data, hand re-created). The preferences path is deleted
outright in Phase 2; a leftover
entry is ignored.

## Goal

- `ViewCatalogService` CRUD round-trips through `StoredArtifact` entities on both backends
  (tier-2 file test + `@Tag("db")` SQL test).
- Two concurrent saves of *different* artifacts do not conflict (per-entity granularity).
- Arch/slice test: a client `UpdateEvent` after an artifact save contains no `StoredArtifact`
  (mirror of the `ExternalSyncEntity` exclusion).
- `grep org.rapla.graphql.customViews` → only in the retirement note, not in live code.

## Scope

### In scope
- `StoredArtifact` entity + full storage wiring (XML, SQL, update history, client exclusion).
- Generic `ArtifactCatalogService`; `ViewCatalogService` switched onto it.
- Kind registry open for [PRD 097](../097-event-html-templates-mustache.md) (TEMPLATE, CSS, PARTIAL).

### Out of scope
- Migration of existing preferences-stored views (D2).
- [PRD 077](../077-calendar-model-graphql.md) saved calendar views (`CalendarModelConfiguration` in **user** preferences) — per-user
  UI state, correctly placed; not an admin-authored server artifact.
- Artifact version history / audit trail beyond lastChanged+lastChangedBy.
- SPA management UI changes beyond re-pointing the existing view CRUD (no new editor surfaces).
- GraphQL schema for artifact CRUD beyond what [PRD 074](../074-graphql-declarative-views.md) already exposes for views.

## Plan

### Phase 1 — Entity + storage plumbing (DONE 2026-07-08)
- [x] `StoredArtifact` + `StoredArtifactImpl`, `RaplaType` registration ("artifact"), `UpdateEvent` branch.
- [x] XML reader/writer sections; `ARTIFACT` SQL storage class; FileOperator/DBOperator wiring.
- [x] Client-exclusion in `UpdateDataManagerImpl` (+ cache-exclusion test).
- [x] Tier-2 round-trip tests (file: `StoredArtifactFileRoundTripTest`, 5 tests),
      `@Tag("db")` round-trip (`StoredArtifactDbRoundTripTest`, 3 tests).

### Phase 2 — Catalog service + view switch-over (DONE 2026-07-08)
- [x] `ArtifactCatalogService` (CRUD, admin-only `checkWrite` seam, two-tier 10 s TTL snapshot +
      invalidate-on-write, size cap via `RaplaArtifactProperties`; `ArtifactCatalogServiceTest`, 5 tests).
- [x] `ViewCatalogService` delegates storage to it; preferences path (`VIEWS_KEY`,
      `loadStored()`/`persist()`) DELETED; all 12 `ViewCatalogControllerTest` tests green unchanged.
- [x] Per-artifact save granularity test (two artifacts, no conflict).

### Implementation findings (Phase 1+2, 2026-07-08)
- **`TEXT` column type is already dialect-mapped** by `AbstractTableStorage.getDatabaseProductType`
  (MySQL/MariaDB→`LONGTEXT`, HSQLDB→`VARCHAR(16M)`, H2→`CLOB`) — the MEDIUMTEXT concern was moot.
- **Column is `CHANGED_BY`, not `LAST_CHANGED_BY`:** any column starting with `LAST_CHANGED` flips
  `EntityStorage` into timestamp-guarded deletes against a `LAST_CHANGED` column that doesn't exist.
- **`StoredArtifactImpl` deliberately does NOT implement `ModifiableTimestamp`** — that interface
  routes dispatch-stored entities into the EntityHistory/update-history replay (FileOperator
  `updateHistory`), which artifacts must stay out of (OQ1). The catalog sets timestamps at save.
- **Migration channel extended:** `CachableStorageOperator.saveData` +
  `CachableStorageOperatorCommand.execute` + `runWithReadLock` now carry a
  `Collection<StoredArtifact>` alongside sync entities, so XML↔DB conversion transports artifacts.
- **Read accessor:** `CachableStorageOperator.getStoredArtifacts()` (DB: fresh `RaplaSQL` query per
  call; File: side map under read lock — same shape as `getImportExportEntities`).
- **PRD054 drift-check exclusion:** artifacts never appear in the history replay, so
  `DBOperator.dispatch`'s post-save drift warning skips `StoredArtifact` ids.
- **`mimeType` guard for kind=IMAGE is NOT yet implemented** — no image consumer exists yet; add it
  in the same change that introduces the first IMAGE writer/serving endpoint ([PRD 097](../097-event-html-templates-mustache.md) — whichever
  phase first lets a template reference an uploaded image; don't cite a phase number, 097's plan was
  re-cut 2026-07-09).

### Implementation findings (pull-based read redesign, 2026-07-09)
- Operator API grew two read methods (defaults on `CachableStorageOperator` delegate to the full
  load; efficient overrides in both backends): `getStoredArtifact(id)` (DB: PK point query via
  `ArtifactStorage.loadById`; file: side-map lookup) and `getStoredArtifactsMetadata()` (DB:
  projection without BODY; file: full instances — the catalog strips a **clone**, never the live
  map instance).
- `ArtifactCatalogService`: whole-catalog `Snapshot`+`strippedIds` replaced by a
  `ConcurrentHashMap` per-entry cache (`find`) + a demand-loaded metadata snapshot (`list`);
  `list()` now contractually returns body-null entries.
- `ViewCatalogService`: `findView` is a direct point read (no catalog scan); `loadStored` =
  metadata list + per-entry `find`.
- `CAST(NULL AS CHAR(1))` as the BODY placeholder keeps one row-parsing path across full/metadata
  selects and is dialect-safe (HSQLDB-verified; CHAR works where VARCHAR casts differ).
- Tests: `ArtifactCatalogServiceTest` 7 (was 5 — + metadata-only list, + large-body read-through),
  `StoredArtifactDbRoundTripTest` 4 (+ point read & projection on HSQLDB), file round-trip /
  client exclusion / `ViewCatalogControllerTest` unchanged green.

### Phase 3 — Hand-off to [PRD 097](../097-event-html-templates-mustache.md)
- [ ] [PRD 097](../097-event-html-templates-mustache.md) Phase 1 re-pointed: `DocumentTemplate` = kind=TEMPLATE artifact, no new
      preferences key. (Work tracked there.)

## Tests

- Tier-2 `FacadeTestSupport`-style store round-trip (XML), `@Tag("db")` SQL round-trip.
- Multi-pod freshness test (save via operator A → immediately visible to a read through operator B
  on the same store — read-through, no history poll involved).
- Client `UpdateEvent` exclusion test.
- `ViewCatalogService` CRUD/visibility tests adapted to the new store (no behaviour-preservation
  mandate — pre-production, D2; no legacy-preferences or migration tests of any kind).

## Open Questions

- **OQ1 — LocalCache path vs ExternalSyncEntity side channel.** *Resolution:* **resolved 2026-07-08
  — side channel, read-through, no update-history dependency.** Mirror the `ExternalSyncEntity`
  read path exactly: on `DBOperator` every catalog read queries the `ARTIFACT` table directly
  (cf. `DBOperator.getImportExportEntities` → `RaplaSQL`, no `LocalCache`), on `FileOperator` reads
  serve from the in-memory XML data under a read lock (cf. `FileOperator.java:778`). No cache → no
  invalidation problem → update history is not needed for artifact freshness (multi-pod sees a save
  on the next read, fresher than the ~10 s history poll). Update history's other role (client push)
  is moot — artifacts are server-only (D3). Reads are per view-list/render on dozens of small rows;
  a process-local 10 s TTL snapshot with invalidate-on-local-write is the sanctioned cache shape
  (see "Caching stance"), still without history. Writes go through the
  operator's write locking; concurrent edits of the same artifact are last-write-wins, different
  artifacts never conflict. A plain Spring-managed JDBC table was reconsidered and stays rejected
  (D1): it would drop file-backend deployments (the dev default), XML export/backup, and lock
  integration.
- **OQ2 — size guard.** *Resolution:* **resolved 2026-07-08 — save-time checks: configurable size
  cap (`rapla.artifact.max-body-size`, Spring `DataSize` binding, default 10 MB) + per-kind
  mimeType validation.** The DB column is CLOB/BLOB with no schema-level restriction, so the limit
  is purely application-level — changing it is config, never a migration. Division of labor:
  **category guarding is done by mimeType per kind** (kind=IMAGE accepts `image/*` only — a video
  is rejected for what it *is*, not its size; text kinds are text by construction), while the size
  cap only bounds resource use. The cap can be generous because the cache is immune (two-tier
  snapshot — bodies ≤ 1 MiB cached, larger metadata-only, see Caching stance). Remaining accepted
  cost: the **file backend** holds all artifact bytes in memory and rewrites them into data.xml on
  every save of anything — with admin-only writes (D6) + mime guarding, realistic content stays
  small; noted, no dual path built. File-backed deployments that grow large artifacts should lower
  the property or move to the DB backend.
- **OQ4 — identity: natural key vs surrogate id.** How is a `StoredArtifact` identified — is the
  primary key the *name itself* or a store-generated id? Prior art splits into two camps
  (researched 2026-07-08), separated by ONE criterion: *are there long-lived inbound references
  that must survive renames?*
  - **Pole A — natural key, rename = recreate.** `ID = kind + ":" + name` (later
    `kind:owner:name`), PRIMARY KEY on it, no id generation; uniqueness is structural instead of a
    catalog-level check. Precedents: **Kubernetes ConfigMaps/Secrets** (identity is purely
    `(namespace, name)`; renaming deletes+recreates and name-references from pod specs break —
    accepted, config is re-applied declaratively), **Jenkins** (jobs by name). This is also
    rapla's shape *today*: `ViewCatalogService.saveView` is upsert-by-name, a rename operation
    does not exist, and the `ExternalSyncEntity` precedent we mirror uses caller-composed
    natural-key ids too (`NotificationStorage.setId(raplaId)`, Exchange's derived string —
    FOREIGN_ID is not store-generated). Fits because the side channel has NO inbound entity
    references and [PRD 097](../097-event-html-templates-mustache.md)'s `viewName` reference is admin-maintained config, warnable at rename.
    Costs: rename resets identity/audit (`CREATED_AT`, change history); any future by-id
    addressing sticks to the name.
  - **Pole B — surrogate id + business key beside it.** Store-generated `ID` PRIMARY KEY,
    `(kind, owner, name)` enforced by the catalog service. Precedents: **MediaWiki** (pages feel
    name-identified, but `page_id` carries revisions/history; moves keep the id and leave a
    redirect — machinery that exists *because* name-references break), **Grafana** (dashboards
    were slug-addressed; renames broke URLs, so the stable **UID** was introduced and slugs
    deprecated), WordPress/Confluence/Keycloak (all internal id + slug). Also rapla's own
    entity-table convention (`DynamicType`/`Category`: `ID … PRIMARY KEY` + unique business key).
    Costs: id generation + save-time uniqueness check as extra moving parts insuring against a
    rename scenario the catalog UX doesn't currently have.
  - Either way, the **Grafana lesson** defines the revisit trigger: the moment artifacts become
    externally addressable (stable document URLs that must survive renames, an MCP tool registry
    bookmarked by foreign clients), a stable UID is added — as an additional column, not an
    identity migration. *Resolution:* **resolved 2026-07-08 — Pole A (natural key), and rename is
    EXCLUDED by design as a catalog operation.** "Renaming" is save-under-new-name + delete-old —
    exactly today's view semantics — so Pole B's rename-survival advantage insures against an
    operation that deliberately doesn't exist. `ID = kind + ":" + name` (later `kind:owner:name`),
    PRIMARY KEY on it, no id generation, uniqueness structural. The bargain, stated explicitly:
    the **name is THE address everywhere** — template `viewName` references, render URLs
    (`/api/documents/{name}`), future MCP tool names, catalog CRUD — and in exchange the **admin
    maintains references manually** when re-naming (save-new + delete-old + update referrers).
    Mitigation, reusing the existing pattern: dangling name references are surfaced by
    **read-time validation** exactly like invalid views today (`ViewEntry.invalidReason` — marked
    invalid with a reason, never silently broken, never auto-deleted); a document whose `viewName`
    resolves to nothing lists as invalid in the catalog. The UID-column revisit trigger
    above stays as the documented step-up path.
- **OQ3 — name uniqueness scope.** *Resolution:* **resolved 2026-07-08 — unique per
  `(kind, owner)`.** With owner always null (D7) this is per-kind uniqueness today (a view and a
  template may share a name; [PRD 097](../097-event-html-templates-mustache.md)'s `DocumentTemplate.viewName` always means kind=VIEW), and a
  later user scope needs no key migration.

## Decisions locked

**D1 — New dedicated server-only entity type inside the rapla store, modeled on
`ExternalSyncEntity`.** Keeps update history (multi-pod), both backends, export/backup, and locking;
gains per-artifact granularity, timestamps, and client exclusion via the proven
`UpdateDataManagerImpl` pattern.
- *Rejected — stay on system Preferences:* the four problems above; acceptable for two small view
  queries, wrong for a growing template/CSS library.
- *Rejected — reuse `ExternalSyncEntity` rows (externalSystem="org.rapla.artifact"):* zero schema
  work, but semantic misuse of the Exchange-sync table, no typed fields (kind/name squeezed into
  context/raplaId), awkward `getImportExportEntities(systemId, direction)` API, and exchange-side
  maintenance jobs iterate that table.
- *Rejected — plain Spring JDBC side table:* DB-backend-only (breaks FileOperator deployments), no
  update-history propagation (other pods stale), missing from XML export/backup.
- *Rejected — files on disk:* same multi-pod/backup problems; contradicts runtime admin editing
  via the SPA.

**D2 — Complete cut-off of the old view preferences: no migration, no compatibility, no legacy
tests** (user decision 2026-07-08). The feature is not in production — existing preferences-stored
views are dev-stage data, re-created by hand. The preferences path (`VIEWS_KEY`,
`loadStored()`/`persist()`) is deleted outright in Phase 2; no code reads the old key, leftover
entries are inert; no migration code and no tests covering legacy data or behaviour preservation.

**D3 — Server-only: `StoredArtifact` never reaches clients.** Same exclusion mechanism as
`ExternalSyncEntity` (`UpdateDataManagerImpl:484`). Clients consume artifacts only through their
rendered products (view execution results, rendered documents).

**D4 — Visibility stays application-level metadata JSON, not entity permissions.** `isPublic`/
`groups` interpreted by `ArtifactCatalogService` exactly as `ViewCatalogService.isVisible` does
today — no `PermissionController` integration, which would drag artifacts into the client-facing
permission model D3 keeps them out of.

**D6 — Authoring: admin-only writes; artifacts are collectively owned by all admins (decided
2026-07-08).** Any admin may edit/delete any application artifact — creating one grants no special
relationship; `lastChangedBy` is audit only, never an authorization input. Group scoping
(`isPublic`/`groups`) filters *visibility/use*, not authorship. The write check lives in ONE seam
(`ArtifactCatalogService.checkWrite(kind, user)`) so later delegation is a one-method change.
Pre-designed extension (NOT built): per-kind delegation — views are structurally low-risk to
delegate (they execute as the *caller*, §12-scoped at the resolvers; worst case is an expensive
query, bounded by DEPTH_CAP), while templates/CSS/images render into other users' browsers
(stored-XSS/CSS-exfiltration surface), so presentation kinds would stay admin-only even then.
Corollary for [PRD 097](../097-event-html-templates-mustache.md): the D6 stripping + CSP + `nosniff` are **unconditional from day one**, never
keyed on author trust — so loosening the write rule never changes the security posture (resolves
097 OQ6 to "no admin raw-JS exception").
- *Rejected — group-admin (`canAdminUsers`) writes across all kinds:* would silently promote
  group-admins into "may run HTML in every group member's browser" — a bigger grant than
  `canAdminUsers` implies anywhere else.

**D7 — Schema prepared for a later user scope; behavior is application-scoped only (decided
2026-07-08).** Nullable `owner` reference (rapla `Ownable`, like reservations/allocatables);
Phase 1 writes only null (= application-scoped). Uniqueness `(kind, owner, name)` (OQ3); catalog
reads filter `owner == null` explicitly so future owned rows can't leak into the application
catalog. Being a real referenced entity also means user deletion runs through standard store
dependency handling — no orphaned-JSON problem (cf. [PRD 089](../089-server-side-recents-favorites.md)'s no-propagation scar). NOT built now:
any user-scope read path, owned-artifact visibility semantics, quotas, or UI.

**D5 — Sequencing: lands before [PRD 097](../097-event-html-templates-mustache.md) Phase 1, coordinated with storage modernization
([PRDs 082](../082-storage-memory-model.md)–087).** [PRD 097](../097-event-html-templates-mustache.md) templates should not ship on a storage mechanism scheduled for
replacement. The entity wiring mirrors today's `ExternalSyncEntity` shape; if [PRD 082](../082-storage-memory-model.md)+ reshapes the
storage foundation first, the artifact store follows that shape instead — check 082–087 status
before starting Phase 1.
