# PRD 058 — GraphQL key-spec migration

**Status:** done — Phase 6 (group-key legacy validation) landed 2026-06-24; Phase 5 (dhbw deploy verification) still pending
**Owner:** Christopher Kohlhaas
**Created:** 2026-05-28
**Shipped:** 2026-05-28
**Tracks against:** [PRD 035](done/035-graphql-foundations.md) (GraphQL Cut C — exposes the same key surface)

## Goal

DynamicType, Attribute, and Category keys may currently contain characters that are
valid rapla identifiers (Java `Character.isLetter` accepts Unicode letters: `ü`, `ö`,
…) but **invalid GraphQL identifiers** (`[A-Za-z_][A-Za-z0-9_]*`). Today the GraphQL
SDL generator covers this with a band-aid (`ClassificationSdlGenerator.sanitizeTypeName`
with `umlautFold`) — that's wrong because:

- The fold is incomplete (only German umlauts + ß; no other Unicode letters covered).
- Sanitisation is invisible to writers (the dualis importer still creates `Prüfer_extern_` style keys); the GraphQL view is permanently aliased.
- Filter inputs that name attributes by key need to round-trip — clients can read `pruefer_extern` but writes still need the umlaut form, and we have to maintain both.

Fix: one-shot **startup migration** that renames every DynamicType / Attribute /
Category key with a non-spec character to a deterministic spec-compliant key, and
propagates the rename through every site that references the old key by name. After
the migration runs, the SDL generator's umlautFold becomes a defensive warn-and-fold
fallback that should never fire in steady state.

## Scope

In scope:
- Validate DynamicType / Attribute (per DynamicType) / Category (recursive) keys against `^[A-Za-z_][A-Za-z0-9_]*$`.
- Deterministic rename strategy: ASCII-fold (umlauts → `ae`/`oe`/`ue`/`ss`, plus `Normalizer.normalize(s, NFKD)` for other diacritics) → drop combining marks → invalid chars → `_` → leading non-letter prefixed with `_` → collision-resolve with `_2`/`_3`/… suffix per scope.
- Run **once, at initial deploy of the new rapla version**, after operator boot and **before** GraphQL SDL generation. Mandatory: there is no `--no-migrate` opt-out, no "degraded mode" fallback. If migration fails (DB error, lock contention timeout, validator rejection of a generated key), server startup **fails fatally** and the operator stays disconnected — better than serving a half-migrated schema. Guarded by a completion marker so subsequent boots skip-fast.
- Hold the same **global write lock** the entity store already uses for DynamicType changes (`RaplaLock.WriteLock` via `writeLockIfLoaded("graphql key spec migration")`) for the entire migration — defensive against any concurrent admin save somehow racing the boot sequence on the same pod. Single-pod deploy; multi-pod boot timing is **not a concern** (the migration runs once per cluster lifetime, the admin coordinates the deploy).
- Single batched update using `storeAndRemove(...)` to commit all renames + the marker atomically.
- Rewrite references in **all** known key-embedding sites (see Plan §3).
- Replace `ClassificationSdlGenerator.sanitizeTypeName`'s active fold path with a defensive warn-and-pass-through that logs at WARN if it ever sees a non-spec key post-migration.
- Update writers that synthesize keys (`org.rapla.components.util.Tools.makeValidKey`) so newly-created entities never reintroduce non-spec characters. **Includes** the dualis importer in `~/git/dhbwrapla` (DualisImportJob, DualisRaplaMapping).
- **Universal entity-side validators** — fire on every boundary where a non-spec key could enter the operator:
  - **Write path:** `DynamicTypeImpl.checkKey(...)` and the corresponding category-key validator reject non-spec keys at `facade.store(...)` time. No source is exempt — Swing admin, REST controller, plugin import, raw SQL load through `FileOperator`/`DBOperator`. Save fails with `error.invalid_key`.
  - **Load path:** after the migration runner completes (or skip-fasts on the marker), assert spec compliance for every DynamicType / Attribute / Category key in the loaded cache. Any failure is a **fatal startup error** — server refuses to come up. This catches stored data that bypassed validation (e.g. inserted by an old rapla version before phase 1, then loaded by a new server that somehow skipped the migration, or a DB write outside rapla).
  - No "validator only fires on writes" softness, no "read pass-through" — the GraphQL schema invariant requires every key in the live cache to be spec-compliant; if that ever fails, the server fails loudly rather than serving a broken schema.

Out of scope:
- Generic Unicode → ASCII transliteration for non-Latin scripts (Cyrillic, Greek, CJK). For those, fall back to `_<hex-codepoint>_` substitution. Documented as a known limitation; rapla deployments are Latin-script today and we'll address transliteration if a non-Latin deployment appears.
- Schema evolution beyond what the migration touches. [PRD 035](done/035-graphql-foundations.md) §5 (Cut C / enum generation / Group type) stays the same — it just sees clean keys after this lands.
- UI tooling for admins to manually rename a key. Migration is fully automatic.

## Plan

### 1. Spec + rename strategy — `org.rapla.components.util.Tools`

The spec predicate + folder live in `rapla-core`'s `Tools` (the same util that
already owns `makeValidKey`) so the rapla-core tier-1 validators
(`DynamicTypeImpl.checkKey`) and the dualis importer in dhbwrapla can both
delegate without crossing module boundaries.

```java
boolean isSpecCompliant(String key);          // matches /^[A-Za-z_][A-Za-z0-9_]*$/
String  toSpecKey(String key, Set<String> taken);  // deterministic; collision-resolves
String  makeValidKey(String key);  // delegates to toSpecKey(.., emptySet())
boolean isKey(String key);         // @Deprecated alias for isSpecCompliant
```

Rename pipeline (`toSpecKey`):
1. Explicit German fold **first**: `ä→ae ö→oe ü→ue Ä→Ae Ö→Oe Ü→Ue ß→ss`. Must precede NFKD — otherwise NFKD decomposes `ü` into `u` + combining diaeresis, the combining mark gets dropped, and `Prüfer` becomes `Prufer` instead of `Pruefer`.
2. `Normalizer.normalize(folded, NFKD)` for other diacritics (`café` → `cafe` + combining acute).
3. Drop combining marks (`Character.NON_SPACING_MARK`).
4. Replace every remaining char failing `[A-Za-z0-9_]` with `_`.
5. If empty, return `"_"`. If first char is not `[A-Za-z_]`, prefix with `_`.
6. If `taken` is non-empty and the candidate collides, append `_2`, `_3`, … until unique.

Returns the new key. **Idempotent on already-compliant input.**

### 2. Migration runner

`org.rapla.storage.impl.server.GraphqlKeyMigration` — package-private helper in
the same package as `LocalAbstractCachableOperator` so it can use the operator's
public API. The orchestration entry point is
`LocalAbstractCachableOperator.migrateGraphqlKeysIfNeeded()`, called directly
from `ServerServiceConfig.cachableStorageOperator()` immediately after
`operator.connect()` returns — guarantees the migration completes before
`HotSwappableGraphQlSource` is constructed and reads the DT set.

1. **Check completion marker** (system preference `org.rapla.server.graphql-key-migration.applied`). If set → log "already applied" → run the load-side assertion (Plan §6) and return.
2. **Acquire the global write lock** — `writeLockIfLoaded("graphql key spec migration")`. Inside the try-finally:
   1. Walk every non-internal `DynamicType` (`isRaplaInternal` skip for `rapla:period`, `rapla:template`, etc.):
      - Check the type key; if invalid, plan a rename.
      - Per attribute: check key; if invalid, plan a rename.
   2. Walk Category tree from super-category. Skip super itself and the `user-groups` subtree (rapla's `org.rapla.entities.domain.Permission` constants hardcode keys like `modify-preferences`, `create-events`, `read-events-from-others`).
   3. For each DT with renames: edit, call `editable.setKey(newDtKey)` and `attribute.setKey(newAttrKey)` per attribute. **Rapla's existing rename mechanism handles annotation propagation** — `AttributeImpl.setKey` fires `parent.keyChanged` which calls `ParsedText.updateFormatString` on every annotation; the annotation's parsed `AttributeFunction` holds the attribute by immutable id and re-emits using the current key. No manual annotation rewrite needed.
   4. For each Category with a rename: edit, `setKey(newKey)`.
   5. Add a system-preferences edit with the marker timestamp (`Instant.now().toString()`) to the toStore set.
   6. `operator.storeAndRemove(toStore, [], null)` in one batch.
3. **Release lock** in `finally`.
4. **Load-side assertion** (Plan §6) runs after the lock is released.

**Auto-propagation to dependents.** The `storeAndRemove` dispatch path runs
`addChangedDynamicTypeDependant(evt, user, store, type, false)` for each
renamed DT (`LocalAbstractCachableOperator.java:2308`). That walks
`getReferencingEntities(type, store)` — pulls every Reservation, Allocatable,
and every Preferences row holding a `ClassificationFilter` — and calls
`dependant.commitChange(type)` on each. `ClassificationFilterImpl.commitChange`
resolves the attribute by id and calls `rule.commitChange(typeAttribute)` which
refreshes the rule's stored `attributeKey` + `attributeId`. So filter rules,
classifications on reservations/allocatables, and everything else implementing
`DynamicTypeDependant` follows the rename automatically. No manual rewrite of
preferences or filter-rule storage needed in the migration.

**Sibling fix landed in same change.** `ClassificationFilterRuleImpl.getAttribute()` (lines 137-148) had a long-standing bug: when both `attributeId` and `attributeKey` are set (the normal case), the second `if` overwrote the id-resolved attribute with a key lookup that returned null after the rename. Result: `commitChange` saw `attribute == null` and removed the rule — silent data loss. Fixed to id-first with key as genuine fallback, regression-pinned by `ClassificationFilterRuleRenameTest`.

### 3. Reference rewrite — handled by rapla's existing mechanism

The 2026-05-28 audit (preserved for the record) identified two reference sites:

1. **Four `ParsedText`-backed annotations on DynamicType**: `nameformat`, `nameformat_planning`, `nameformat_export`, `descriptionformat_export`. Syntax `{attrKey}` or `{func(attrKey, …)}`.
2. **`ClassificationFilterRule.attributeKey`** — the rule's denormalized key cache, mirrored by `attributeId`.

After implementation discovery, **neither needs a custom rewrite in PRD 058**:

- **Annotations** auto-update at `setKey` time. `AttributeImpl.setKey(newKey)` → `parent.keyChanged(this, key)` → for each annotation, `text.updateFormatString(parseContext)` → `getExternalRepresentation` walks parsed `Function` instances → `AttributeFunction.getRepresentation(ctx)` returns `attribute.getKey()` resolved by immutable id (`DynamicTypeImpl.java:854-862`). The annotation is re-emitted with the current key.
- **Filter rules in preferences** auto-update at `storeAndRemove` time via the dispatch-side `addChangedDynamicTypeDependant` → `commitChange(type)` chain described in Plan §2. The chain resolves rules by id and refreshes their `attributeKey`/`attributeId`. Requires the `ClassificationFilterRuleImpl.getAttribute()` id-first fix (sibling change in same PRD).

**Out of audit scope, no rewrite needed:** `KEY_COLORS` with value `"color"` resolves via `KEY_COLOR` annotation lookup (no key-string embedded). `KEY_LOCATION` references type keys, which self-heal through the same DT setKey mechanism.

### 4. Writer-side fix — `Tools.makeValidKey`

Today `Tools.makeValidKey("Prüfer (extern)")` returns `Prüfer_extern_` (Java treats
`ü` as a letter). Update it to apply the same fold pipeline as the migrator (steps
1–5 above). Add a tier-1 unit test covering: ASCII passes through, leading digit gets
`_` prefix, umlauts fold, NFKD diacritics fold, non-Latin letters become `_`,
collisions are **not** resolved here (callers that need uniqueness call into the
migrator's helper).

**dhbwrapla coordination:** the dualis importer in
`~/git/dhbwrapla/src/main/java/org/rapla/dhbw/sync/dualis/server/{DualisImportJob,rapla/DualisRaplaMapping}.java`
calls `Tools::makeValidKey` four times. With rapla-core's `makeValidKey` folding
correctly, the importer creates ASCII-clean category keys for new akteurtypen
and the rapla startup migration handles legacy data already in the DB. A new
`org.rapla.dhbw.sync.dualis.server.rapla.DualisAkteurtypNames` helper adds
belt-and-suspenders explicit name aliases (`Prüfer` → `Pruefer`, `prüfer` →
`pruefer`) before `makeValidKey` so the dualis import path is documented and
robust against any future regression in `makeValidKey`. The three call sites in
`DualisImportJob` and `DualisRaplaMapping` route through the helper. Coordinate
the rapla and dhbw deploys in the same window.

### 5. Remove sanitizeTypeName band-aid

`ClassificationSdlGenerator.sanitizeTypeName` previously did `umlautFold` +
drop-invalid. After migration runs, every key seen by the SDL generator is
spec-compliant. The fold is removed; non-spec input throws
`IllegalStateException` with a clear "PRD 058 invariant violated" message —
this can only fire if migration was somehow skipped or a bug introduced a new
non-spec write path, in which case loud failure beats silent broken SDL.

The matching `isRaplaInternal` skip was also added to
`GeneratedClassificationWiring.configure` (caught during testing — the wiring
walked all DTs and was calling the SDL helper on `rapla:template`).

### 6. Universal validator — three layers

The migration writes spec-compliant keys once; three independent validator
layers stop any client/code path from reintroducing a non-spec key:

#### a) Entity validators (write path)

The existing `DynamicTypeImpl.checkKey(i18n, key)` (line 708) was the natural
hook. Its `Tools.isKey(key)` check was tightened by aliasing `Tools.isKey` to
`Tools.isSpecCompliant` (deprecated alias preserved for callers in
`ParsedText.java:215` which legitimately need the same check). The validator
fires from `DynamicTypeImpl.validate(...)` for DT keys and every attribute key,
and from `LocalAbstractCachableOperator.checkConsitency` line 2999 for Category
keys.

#### b) Explicit dispatch validator (defense in depth)

`LocalAbstractCachableOperator.checkGraphqlKeySpecCompliance(storeObjects)` is
called early in `check(evt, store)` — explicitly walks DTs / Attributes /
Categories, applies `Tools.isSpecCompliant`, throws `RaplaException` with the
`error.invalid_key` i18n key on any violation. Rapla-internal DTs (`rapla:*`)
exempted. Belt-and-suspenders against any future refactor that might
accidentally relax the entity-side validators.

#### c) Load-side assertion (post-migration)

After `migrateGraphqlKeysIfNeeded()` completes (or marker-skips),
`assertCacheSpecCompliant` walks the loaded cache and throws if any non-internal
DT / Attribute / Category key fails the spec — server startup aborts. Catches:
- A stored DB row whose key was set outside rapla (raw SQL).
- A new code path that calls `setKey(...)` without going through the validated `store(...)` boundary.
- A migration bug where the rename pipeline produced a still-invalid key.

### 7. Migration completion marker

System-preference entry `org.rapla.server.graphql-key-migration.applied` =
ISO-8601 timestamp of the first successful migration. One-shot: presence
of marker → skip migration entirely on subsequent boots; load-side assertion
still runs.

If a future change requires a *new* migration pass (e.g. extending the spec or
covering a new key-reference site), introduce a separate marker key for that
new pass. This PRD's marker is permanent.

## Tests

Landed:
- **Tier 1** (`rapla-core/.../ToolsTest`) — 17 tests covering `isSpecCompliant` + `toSpecKey` + `makeValidKey`: ASCII pass-through, German fold including the `Prüfer (extern)` → `Pruefer__extern_` case, NFKD diacritics (`café` → `cafe`), leading digit prefix, non-Latin → `_`, collision-resolve, idempotence property.
- **Tier 2** (`rapla-server/.../DynamicTypeKeyValidatorTest`) — 9 round-trip tests: storing DTs / attributes / Categories with umlauts / leading digit / spaces / hyphens / dots through `facade.store(...)` fails with `RaplaException`; ASCII-clean keys round-trip.
- **Tier 2** (`rapla-server/.../GraphqlKeyMigrationTest`) — 6 tests on `testdefault.xml`: hyphen-keyed `channel-6` / `elementary-springfield` under department renamed to `_`-keyed equivalents; `user-groups` subtree (with `modify-preferences`, `create-events`, …) left intact; cache post-migration is spec-compliant; idempotent re-run no-ops via marker; marker is written; nameformat annotation auto-propagates through `setKey` → `keyChanged` chain via a round-trip on the `room` DT.
- **Tier 2** (`rapla-server/.../ClassificationFilterRuleRenameTest`) — pins the `ClassificationFilterRuleImpl.getAttribute()` id-first fix: builds a filter rule against `room.name`, renames the attribute, calls `commitChange` and asserts the rule survives with `name_renamed` bound. Red-green confirmed by reverting the one-line fix.

Landed (added 2026-05-28 after the initial draft):
- **Tier 1** (`dhbwrapla/.../DualisAkteurtypNamesTest`) — 4 tests confirming `toCategoryKey("Prüfer (extern)") == "Pruefer__extern_"`, the substring alias fires on `prüfer` lowercase too, non-Prüfer strings round-trip through `makeValidKey` unchanged, null returns null.

Not landed (future):
- **Tier 3** (`rapla-app`, MockMvc) — boot a `@SpringBootTest` against a fixture DB pre-seeded with non-spec keys, verify the GraphQL SDL builds clean and the SDL generator's `checkGraphQlCompliantName` defensive throw doesn't fire. Existing 30+ `HelloGraphQLControllerTest` / `ClassificationGraphQLControllerTest` tier-3 tests exercise the SDL boot path implicitly (and pass against the migrated testdefault.xml), but no test deliberately seeds a non-spec key. Add when convenient.
- **Phase 5** dhbw deploy verification — run the migration against the dhbw test DB, capture before/after key counts, verify dualis re-import lands on the renamed `Pruefer__extern_` category.

## Open Questions

- **OQ-1 — resolved** Migration is a one-time deploy event (single admin-coordinated cutover), not a steady-state operation across rotating multi-pod replicas. Multi-pod boot timing is not a concern: the admin runs the deploy once on a quiet cluster. The lock and marker remain useful as a defensive guard against repeat invocation within the same boot but are not designed to coordinate concurrent boot races.
- **OQ-2 — resolved** Dot keys (`type.with.dot`) are flagged by the spec and renamed by the migration.
- **OQ-3 — resolved by discovery** Preferences embedded `ClassificationFilter` rules were the only key-referencing concern in user prefs. The dispatch-side `addChangedDynamicTypeDependant` → `commitChange` chain walks all preferences via `getReferencingEntities` and updates them automatically. No standalone audit needed.

## Update 2026-06-24 — group keys: relax write-guard to legacy validation (Option A)

**Problem discovered.** PRD 058 left group keys (the `user-groups` Category
subtree — see [PRD 069](069-graphql-resource-access-read-api.md) for what "groups" are) in an **inconsistent state**:

- The **startup migration** (`GraphqlKeyMigration`) deliberately exempts the
  `user-groups` subtree from both renaming and the load-side assertion
  (`walkCategoriesForRename` line 288, `walkCategoriesForAssert` line 368:
  `"user-groups".equals(c.getKey()) && parent == superCategory` → skip). So
  existing non-spec group keys (Bindestriche/Umlaute) are **never migrated** and
  don't block boot.
- But **both write-path validators still enforce the strict GraphQL spec on
  every Category**, including the user-groups subtree:
  - `LocalAbstractCachableOperator.checkGraphqlKeySpecCompliance` (Plan §6b,
    line ~1372) — no user-groups exemption, walks all Categories with `isSpecCompliant`.
  - `checkConsitency` → `DynamicTypeImpl.checkKey` → `Tools.isKey` (now aliased
    to `isSpecCompliant`, Plan §6a).
- Group keys **never reach the SDL generator** — groups are exposed only via the
  hand-written static `type Group { key: String! }` (`schema.graphqls:508`), and
  [PRD 069](069-graphql-resource-access-read-api.md) group inputs (`accessibleByGroup`, `inGroup`) take slash-separated
  key-paths `[String!]`, not generated identifiers. So there is **no schema
  reason** to enforce the strict spec on group keys.

**Consequence (regression).** An existing group with a non-spec key (e.g.
`prüfer-extern`) boots fine but can no longer be saved — any edit (rename display
name, add child) carries its key through `check()` and is rejected with
`error.invalid_key`. Groups are effectively frozen. Hits dhbwrapla, whose group
keys carry hyphens/umlauts.

**Decision — Option A + legacy validation.** Relax write-path enforcement for the
`user-groups` subtree to the **pre-058 key rule** rather than dropping validation
entirely, so group keys are no worse off than before PRD 058:

- Pre-058 rule (recovered from commit `da0aec5f`, old `Tools.isKey`): leading
  char `_`, `-`, or any `Character.isLetter` (Unicode); subsequent chars also
  allow `Character.isDigit`; reject the literals `true`/`false`; non-empty; ≤50
  chars (length cap lives in `DynamicTypeImpl.checkKey`).
- Re-introduce that predicate in rapla-core `Tools` as `isLegacyKey(String)`
  (its body was deleted when `isKey` became the strict alias).
- Both write-path category validators apply `isLegacyKey` (not `isSpecCompliant`)
  **only when the Category is in the `user-groups` subtree**; the strict spec
  stays for every other category (they can still back VALUE_LIST enums). Needs an
  `isInUserGroupsSubtree(cat)` ancestry helper (walk `getParent()` to super; true
  if cat or an ancestor is the `user-groups` child of super).
- The migration exemption stays as-is — no group keys get rewritten.

**Tests (test-first, tier 2 in rapla-server):** storing a user-groups child with
key `prüfer-extern` round-trips; storing one with a space / slash / `true` still
fails legacy; a *non-group* category with an umlaut still fails strict (unchanged).
Pin red-green by reverting the relax.

## Update 2026-08-28 — group keys DO reach the SDL generator (fixed)

The 2026-06-24 note assumed *"Group keys never reach the SDL generator"*. A migrated
Rapla 2 dataset disproved it: an event DynamicType had a CATEGORY attribute with
`root-category = user-groups` (booker picks a user group). `CategoryKindClassifier`
classified that root as VALUE_LIST, `ClassificationSdlGenerator.enumNameFor` built the
enum name from the path and `checkGraphQlCompliantName("user-groups")` threw
`PRD 058 invariant violated` on every boot — the migration exemption and the generator's
invariant contradicted each other.

**Fix:** `enumNameFor` returns `""` for roots inside the `user-groups` subtree
(`ClassificationSdlGenerator.isInUserGroupsSubtree`, mirror of the operator helper), so
all four call sites fall back to the existing ORGANIZATION handling (`ID` /
`Category` / `CategoryWhere`, no enum). Test:
`rapla-app/.../ClassificationSdlGeneratorUserGroupsTest` (plain JUnit, in-memory
`EntityStore`). Group *values* of such an attribute stay reachable by id; a typed enum
for user groups is out of scope (group keys are legacy-validated by design, see above).

**Second finding, same dataset:** enum-value descriptions were emitted as block strings
(`"""…"""`) with `"` escaped as `\"` — GraphQL block strings have no such escape, so a
category display name containing a double quote produced `\""""` and the schema parser
failed on boot (`SchemaProblem … InvalidSyntaxError`). Now emitted as a regular string
literal via `escapeStringLiteral` (`escapeDescription` removed). Covered by the second
test in `ClassificationSdlGeneratorUserGroupsTest` (parses the SDL with graphql-java's
`SchemaParser`).

## Phasing — as shipped

All phases below landed in one session (2026-05-28) except Phase 5.

- ✅ **Phase 1** — `Tools.isSpecCompliant` + `toSpecKey` + `makeValidKey` delegation in `rapla-core`. `Tools.isKey` deprecated as alias → `DynamicTypeImpl.checkKey` tightens transitively. 17 tier-1 + 9 tier-2 validator tests.
- ✅ **Phase 2** — `GraphqlKeyMigration` helper + `LocalAbstractCachableOperator.migrateGraphqlKeysIfNeeded()` orchestration entry point, called from `ServerServiceConfig.cachableStorageOperator()`. Global write lock + marker. Walks DT + Attribute + Category; skips `rapla:*` internal types and the `user-groups` subtree. Auto-propagation via `setKey`/`storeAndRemove` replaces the manual annotation/filter rewrite originally planned.
- ✅ **Phase 3** — Category rename works via the same path. Sibling fix in `ClassificationFilterRuleImpl.getAttribute()` (id-first resolution) makes filter-rule auto-propagation reliable. Regression-pinned by `ClassificationFilterRuleRenameTest`.
- ✅ **Phase 4** — `sanitizeTypeName` band-aid removed; defensive throw on non-spec input. Matching `isRaplaInternal` skip added to `GeneratedClassificationWiring`. 4 SDL unit tests updated.
- ⏳ **Phase 5** — dhbw deploy verification. Pending: run migration against dhbw test DB, capture key-count delta, verify dualis re-import lands cleanly on the renamed `Pruefer__extern_` akteurtyp category.
- ✅ **Phase 6** (2026-06-24) — group-key legacy validation (Option A). Landed: `Tools.isLegacyKey` (rapla-core); `DynamicTypeImpl.checkKey(i18n, key, boolean legacy)` overload; `LocalAbstractCachableOperator.isInUserGroupsSubtree(cat)` ancestry helper; both write-path category validators (`checkGraphqlKeySpecCompliance` + `checkConsitency` → `checkKey`) now use legacy validation for the user-groups subtree, strict spec elsewhere. Migration exemption unchanged. Tier-2 `GroupKeyLegacyValidationTest` (5 tests: hyphen + umlaut group keys round-trip; space / slash group keys still rejected; non-group hyphen still rejected by strict spec). `DynamicTypeKeyValidatorTest` + `GraphqlKeyMigrationTest` stay green.

## Update 2026-08-29 — one type namespace: reserved suffix words

**Found:** a DynamicType `intern` and a root category renamed to `intern` both produced
`internWhere` → `SchemaProblem` at boot, crashloop (found on a migrated Rapla 2 dataset). Generated names from DT keys
(`KClassification`, `KWhere`, `KRefWhere`) and from root-category paths (`E`, `EWhere`,
`EListWhere`) shared the suffix `Where`; enum↔enum (`a/b` vs `a_b`) and generated↔core
collisions were unguarded too. A collision-free naming scheme is impossible on a shared
alphabet with free keys — so the namespace is shared by construction and controlled by rule.

**Decision (Option 1 of the 2026-08-29 discussion):**
- Enum-derived names get their own suffix family: `EEnum`, `EEnumWhere`, `EEnumListWhere`
  (values stay verbatim keys). DT-derived names unchanged.
- Reserved suffix words `Classification`, `Where`, `Enum`, `Rapla` (`Rapla` kept free for
  future generated families): no key may END with one — `Tools.isSpecCompliant` rejects,
  `Tools.toSpecKey` appends `_` (so `GraphqlKeyMigration` migrates existing offenders).
  Reserved DT keys (`String`, `Int`, `Boolean`, `LocalDateTime`, `Category`, `Allocatable`)
  and reserved attribute keys (`typeKey`, `type`, `AND`, `OR`, `NOT`) are refused on write.
- Core side: `GeneratedNameNamespaceArchitectureTest` — no hand-written type in
  `schema.graphqls` ends with a reserved suffix (fixed predicate types excepted), no
  `AllocatableFilter`/`ReservationFilter` field shaped `where<Key>`.
- Fail-safe: the generator never breaks the boot on a name collision. Ambiguous enum path
  → first root keeps the enum, later roots fall back to `Category`, WARN with both ids.

**Consumers:** SPA parser (`classification-schema.ts`) resolves enums by SDL type name —
unchanged. Docs `docs/graphql.md` updated (`RaumartEnum`). dhbwrapla docs mention
`<EnumName>Where` generically — still true.

Tests: `ClassificationSdlGeneratorNamespaceTest`, `ToolsTest` (suffix rules),
`GeneratedNameNamespaceArchitectureTest`, `GraphqlKeyMigrationTest` (suffix migration).
