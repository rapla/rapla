# PRD 112 — Deployment seed / patches: customer artefacts into the store without manual pasting

**Status:** Option A **implemented 2026-09-02** on the user's instruction ("setze das so um …
für morgen"): `org.rapla.server.spring.patch.{FrontMatter, ArtifactPatchLoader}` (rapla-app),
`RaplaServerProperties.patchDir`, system-scoped save overloads in `ArtifactCatalogService`
(`saveAsSystem`), `ViewCatalogService` (`saveViewAsSystem`), `DocumentCatalogService`
(`saveAsSystem`); tests `FrontMatterTest` (tier 1) + `ArtifactPatchLoaderTest` (tier 3); docs in
[deployment.md § Patch directory](../deployment.md#patch-directory--stored-views-and-documents-from-files-prd-112).
Option B (patch JAR) stays a later extension — § Compatibility A → B. Not done in v1: `/server`
listing of applied files, editor export with front matter (§ Open).
**Related:** [PRD 097](097-event-html-templates-mustache.md) (documents; § Phase 4 notes "no seeding mechanism, deliberately"), [PRD 098](done/098-server-artifact-store.md) (artifact store; builtins stay in the JAR), [PRD 111](111-document-ui-wiring.md) (the `documents` type annotation), [PRD 090](090-additive-permission-resolution.md) (one-shot migration protocol), [locking](../architecture/locking.md#multi-pod-note)

## The question

A deployment needs a stored view, a stored document and a type annotation that are specific to
it (an equipment-lending slip on its loan type). Today the install guide says "paste both via
the template editor, then set the annotation in the type editor" — four manual steps by an admin
after every fresh install, and a data file that is not reproducible from the package. Wanted: the
package carries the artefacts as files, the server puts them into the store.

Standing rule this must respect: **customer-specific content stays outside the JAR; an upgrade
is a JAR swap.** So the seed input lives next to the JAR (`data/` or `config/`), never inside it.

## Verified current state (2026-09-02)

- **Stored artifacts** (PRD 098): `StoredArtifact` entity, id = `kind:name`
  (`StoredArtifact.createId`), so a save is structurally an **upsert** and name uniqueness is
  per kind. `ArtifactCatalogService.save(kind, name, body, metadata, caller)` requires an admin
  `User` — `checkWrite` rejects `null` (system).
- **Save paths with validation:** `ViewCatalogService.saveView(name, queryText, isPublic, groups,
  defaultVariables, caller)` validates the name (= operation name, GraphQL identifier) and the
  query against the **live schema**; `DocumentCatalogService.save(name, viewName, template,
  isPublic, groups, defaultVariables, window, caller)` validates the template + window. Both
  refuse builtin names. Both return `List<String>` errors.
- **Builtins** (`ViewCatalogService.BUILTIN_VIEWS`, `BuiltinDocuments.ENTRIES`,
  `BuiltinPartials`) are in-memory JAR content, never stored. PRD 098 line 77 and PRD 097
  Phase 4 decided against seeding *builtins* into the store — that decision is about JAR
  content and upgrade conflicts, not about deployment content, and stays.
- **Startup write slots:**
  1. `ServerServiceConfig.cachableStorageOperator()` right after `operator.connect()` — where
     PRD 058/090 migrations run (`LocalAbstractCachableOperator:691-751`): marker in system
     preferences → `writeLockIfLoaded` → re-check marker inside the lock → write → unlock.
     Runs before any other bean, failure = fatal. **Too early for a view seed:** the GraphQL
     schema (needed to validate a view) is not built yet.
  2. `@EventListener(ApplicationReadyEvent)` — the `JavascriptPatcher` slot
     (`rapla.patch-script`, `rapla.services.org.rapla.plugin.javascriptpatch`). Everything is
     up, including the GraphQL source. **The patcher's body is dead code** (opens the file,
     logs "done", never runs a script; Nashorn is gone since JDK 15) — the hook shape is
     reusable, the mechanism is not.
- **Type annotation writes:** two idioms — `DynamicTypeMutationController` (clone + optimistic
  lock + `dispatch(UpdateEvent)`, hot-swaps the generated SDL) and the migration idiom
  (`editObject` + `storeAndRemove(…, null)`).
- **Multi-pod:** each pod has its own cache reconciled from the update history (~10 s); writes
  are serialized by the write lock; PRD 090's "re-check inside the lock" is the whole
  coordination story.
- **Not reusable:** the eventimport plugin (`TemplateImportController`) is a request-driven
  read of a deployment DB view; DB first-boot import (`DBOperator.upgradeDatabase`) imports a
  whole `data.xml`, not artifacts.

## Option A — declarative file seed (first draft)

A `data/patch/` directory next to the JAR (`rapla.patch-dir`, absent = no-op) containing the
artefact files themselves — **no manifest** (user, 2026-09-02: the metadata lives in the file).
Each file is self-describing via **front matter in a native comment** that the engine already
discards — a Mustache `{{! … }}` block for documents, `#` lines for views — holding a small YAML
block read with the YAML parser Spring Boot already ships:

```mustache
{{! rapla-document
view: Leihschein
public: true
updated: 2026-09-02T10:00
}}
<!doctype html> …
```
```graphql
# rapla-view
# public: true
# updated: 2026-09-02T10:00
query Leihschein @view(title: "Leihschein", listed: false) { … }
```

Names come from the file: the view's is its operation name (the catalog requires that anyway),
the document's is the file name without extension (`Leihschein.mustache`). Optional keys mirror
the editor's fields (`groups`, `window`, `defaultVariables`). A file without a recognised header
at the very top is ignored with a WARN. The header stays inside the stored template — visible in
the editor as provenance — and is also the natural **export format** ("download this document as
a file" writes exactly this head), which folds the former P6 into the same shape.

**Artifacts only** — no dynamic-type section (user, 2026-09-02: patching a type's annotation is
entity surgery, the next ask would be an attribute or a permission; that is Option B territory,
code with an applied list. The PRD 111 `documents` annotation stays the one manual type-editor
step of the install guide until then).

Runs at `ApplicationReadyEvent` on every start under the write lock (existence check repeated
inside it), rule A2 below per file. System author via a `saveAsSystem` path with full
validation. Invalid file → ERROR log + skip, boot continues. No Java build per deployment
change: edit file, bump `updated`, restart.

**Naming (user, 2026-09-02): "patch", not "seed"** — with timestamp updates it is no longer an
initial fill, and Option B later is "patches as code" under the same word. Names: directory
`data/patch/`, property `rapla.patch-dir`, plugin gate `org.rapla.plugin.patch`. The dead `rapla.patch-script` is superseded, not touched (§ Residue).

**A2 — Updates by timestamp (user, 2026-09-02).** Each file's front matter carries a declared
`updated: <ISO datetime>` (NOT the file mtime — unzip/copy/checkout rewrite mtimes and would
"update" everything). `StoredArtifact` implements `Timestamp`, so the rule per entry is:
1. artifact missing → create;
2. exists and the file's `updated` is after the artifact's `lastChanged` → overwrite (INFO);
3. otherwise → leave alone. This also protects an admin's later editor change (its
   `lastChanged` is younger than the seed) until a newer delivery bumps `updated` — then the
   update wins, which is the intended "Update überschreibt".
Not distinguished in (3): "admin edited" vs "seed already ran" — both are "stored is younger".
If admin edits must survive an update, add `seededAt` to the artifact metadata later; not v1.


### Editor interplay (user, 2026-09-02: "im Template-Editor und GraphQL-Editor aufpassen")

- **Stored metadata is authoritative** on the running server (`isPublic`, `viewName`, …, as
  today). The front matter is read ONLY by the patch loader; inside a stored template it is inert
  comment text. An admin flipping "public" in the editor leaves a stale header behind — harmless,
  nothing reads it; the export rewrites the header from stored metadata.
- **GraphiQL "Prettify" drops `#` comments** (`print(parse(...))`). A view pasted into
  `data/patch/` after prettifying has no header → the loader ignores the file with a WARN (visible,
  not silent). Nothing is lost server-side; the export regenerates the header.
- **Template editor:** a `{{! … }}` block survives validation, preview and save (it is text the
  renderer discards); in a full-HTML template it precedes the doctype and is gone after render.
  The editor must not trim or rewrite the header — that is the one rule it needs.

## Option B — patch JAR with an applied-patches record

**P1 — Delivery: a patch JAR in `lib/`, loaded by the existing `-Dloader.path=lib/`.** The fat
JAR already starts via Spring Boot's `PropertiesLauncher` (`layout ZIP`, PRD 045), and
`docs/deployment.md` already documents `lib/` + `loader.path` for operator-supplied JDBC
drivers. A deployment patch JAR (e.g. `rapla-patches-siegen-1.jar`) carries its own
`META-INF/spring/…AutoConfiguration.imports` and is picked up like a plugin: full DI, operator,
catalog services. Customer-specific stays outside the rapla JAR; a rapla upgrade is still a JAR
swap (the patch JAR may need a rebuild against the new API — named cost). The artefact files travel as resources inside the patch JAR, same front-matter format.

**P2 — Contract: `DataPatch` + a runner that knows what already ran.**
```java
public interface DataPatch {
    String id();                      // sortable, unique: "siegen-001-leihschein"
    void apply(PatchContext ctx) throws Exception;
}
```
Runner at `ApplicationReadyEvent` (the GraphQL schema must exist to validate a view; the
PRD 090 slot after `connect()` is too early): collect all `DataPatch` beans, sort by id, take the
write lock (`writeLockIfLoaded`), read the applied list **inside the lock** (multi-pod: two pods
starting together apply once), apply each patch not in the list, record it after success,
unlock. The applied list lives in the system preferences under one key
(`org.rapla.patches.applied`: id, timestamp, patch-JAR version) — the PRD 090/058 marker
protocol, one entry per patch instead of one key per migration. A failed patch is NOT recorded
(retried next start), logged ERROR with id + cause, and stops the run for later ids (Flyway
discipline: no out-of-order application). The server stays up either way.

**P3 — Helper API in `PatchContext`,** so the common patches are three lines, all with the same
validation as the editor and authored as system (`lastChangedBy = null`, no fake admin):
`seedView(resource, isPublic, groups)` (name = operation name), `seedDocument(name, viewName,
resource, isPublic, groups, window)`, `setTypeAnnotation(typeKey, key, value)` (PRD 111 D1 —
`documents=Leihschein` on the loan type), plus the raw `CachableStorageOperator` for
everything else. Needs a system-scoped save path in `ArtifactCatalogService`
(package-private `saveAsSystem`, same validation minus `checkWrite`) and the matching overloads
in the view/document catalogs.

**P4 — Semantics: Flyway, not seed.** A patch runs exactly once per store. Re-delivering a
changed template is a NEW patch id that overwrites (`seedDocument` upserts — the artifact id is
`kind:name`). An applied patch is never re-run even if its code changed; editing a shipped patch
is a bug, shipping a new one is the fix. A stored artifact the admin edited in between is
overwritten by a later patch that targets it — the patch author decides, the mechanism does not
guess (no hashes).

**P5 — Plugin gate:** `rapla.services.org.rapla.plugin.patches` (`matchIfMissing = true`) on
the runner, like every startup hook. No runtime preference. `/server` status page lists the
applied patches (cheap, and the first thing to look at when "the Leihschein is missing").

**P6 — Relation to builtins and the editor.** Builtins stay in the JAR (PRD 098). A patch may not
use a builtin name (the save path already refuses). The editor stays the authoring tool; a
patch is what the deployer packages afterwards — "download this document as patch resources"
is the natural later addition, not v1.

## Compatibility A → B (why A is not thrown away when patches come)

- **One save path.** A's system-scoped save with full validation (`saveAsSystem` in the
  artifact catalog + overloads in view/document catalogs, `setTypeAnnotation`) IS the helper API
  a later `PatchContext` exposes. Build it as a reusable piece, never inline in the seed loader.
- **One slot, one lock.** Seed and patch runner both run at `ApplicationReadyEvent` under
  `writeLockIfLoaded`; the runner queues after the seed. The seed is its own callable step.
- **No bookkeeping conflict.** The seed compares timestamps against `lastChanged`; patches keep
  their applied list. A patch that rewrites an artifact bumps its `lastChanged`, so the seed
  leaves it alone afterwards. Discipline (one line, no mechanism): an artefact maintained by
  patches does not get its seed `updated` bumped.
- **The seed becomes a patch type.** Once the runner exists, "apply the patch directory" is a built-in
  patch without Java; A is then the default shape of a patch, not a second mechanism.

## Open (v1 shipped without; decide when needed)

1. P2: applied-list key shape — one JSON entry vs one key per patch id (PRD 090 style).
2. P2: on failure stop the run (proposed) vs continue with later ids.
3. P1: patch JAR location `lib/` (proposed, exists) vs a dedicated `patches/` dir (second
   `loader.path` entry).
4. P5: expose applied patches on `/server` (proposed) — yes/no.

## Rejected / not now

- **Seeding builtins into the store** — PRD 098 decision stands.
- **`rapla.patch-script` as the mechanism** — dead body, would need a JS engine dependency; the
  patch JAR is the same idea in the language the server already speaks.
- **Content-hash / versioned upgrade of seeded artefacts** — replaced by patch ids (P4).
- **Migration slot after `operator.connect()`** — too early (no schema); the marker protocol
  from there is reused, the slot is not.

## Residue

- `JavascriptPatcher` (`org.rapla.plugin.javasciptpatch`, typo'd package) is a no-op with dead
  script code; the `rapla.patch-script` property has no consumer. Candidate for removal in its
  own change.
- PRD 097 Phase 4 sentence "there is no artifact-store seeding mechanism, deliberately" needs a
  pointer here once this PRD is ruled.
