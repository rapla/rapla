# PRD 085 — search & name indexing (in-memory derived-name index over computed names)

**Status:** draft — 2026-06-24 (rewritten: Lucene/H2 dropped in favour of an in-memory derived-name index; see *Direction*)
**Related:** [PRD 082](082-storage-memory-model.md) (storage memory model — provides the put/remove seam `updateIndizes` this builds on, and the "index = disposable in-RAM projection" rule; name search was split out of Workstream A into here), [PRD 084](084-replace-hsqldb-with-h2.md) (H2 engine — was the candidate full-text store; now only a conditional fallback), [PRD 081](081-graphql-omnibox-multisearch.md) (omnibox multisearch — the consumer), [PRD 028](028-angular-power-search.md) (allocatable evaluator / `searchText`), [PRD 035](done/035-graphql-foundations.md) (GraphQL foundations), AGENTS.md §12 (data-leak prevention)

**Split from [PRD 082](082-storage-memory-model.md).** Workstream A originally bundled a hand-rolled name index next to the
structural type-bucket index. Name search is a *different* problem (the name is **computed, not
stored**) and gets its own PRD. [PRD 082](082-storage-memory-model.md) keeps the structural type-bucket index; this PRD owns all
name search.

## Scope & priority — this is a planner-only secondary feature

**Decided 2026-06-24:** name search must only be *approximately* performant and must *scale*, not be
super-performant. It serves **planners** (the omnibox), **not** the bulk calendar/Belegungs-queries
that are rapla's primary focus (those are [PRD 082](082-storage-memory-model.md)'s read-model territory). This bar is what justifies
the lightweight in-memory direction below over a real search engine.

**In-memory stays (decided 2026-06-24).** The server holds the dataset in `LocalCache` (RAM); name
search rides that, it does not introduce a second store. The earlier Lucene/H2-full-text direction
is dropped to a *conditional* fallback (see *Engine: deferred*).

## Problem

Two omnibox read patterns are **O(all) full scans** today (`SearchGraphQLController`):

- **EVENT bucket** (`searchEvents`, ~162-194): a **windowless scan over every cached reservation** —
  `r.getName(locale)` (a `ParsedText` eval, per keystroke) then `SearchMatcher.rank(..., SUBSTRING)`,
  then `canModify` per name-hit. This is the worst offender: the eval cost over the unbounded
  reservation dimension is the 082 "901 ms-class scan".
- **RESOURCE bucket** (`searchResources`, ~118-149): FUZZY name search over allocatables via the PRD
  028 evaluator then `SearchMatcher.rank` per candidate. Allocatables are **bounded** (~48k,
  near-static per 082) — lower priority.

`ReservationFilter.searchText` on `reservations(...)` ranks an *already* window+allocatable-scoped
set → out of scope here.

## Why this is hard — the name is **computed**, not a stored field

`ClassificationImpl.getName(locale)` evaluates the DynamicType `KEY_NAME_FORMAT` annotation — a
**`ParsedText`** template (e.g. `{roomName} ({building})`) — against the classification's attribute
values, per locale. The template is a parsed **tree of `Function` objects** (`ParsedText.variablesList`),
parsed once per type and shared across instances. Consequences:

1. **The indexed text must be *derived*** — project `getName(locale)`, never copy a field.
2. **`ParsedText` functions can reach beyond own attributes.** `StandardFunctions` has 32 functions;
   beyond plain attribute interpolation, name formats *can* use:
   - **cross-entity** functions (`AttributeFunction`, `NameFunction`/`KeyFunction` on a category,
     `ParentFunction`, `Events`/`ResourcesFunction`) → the name can depend on *referenced* entities;
   - **time/occurrence** functions (`AppointmentStart/End/Times/Block(s)`) → in the `getName` eval the
     first context object is the *Classification* (not the reservation+appointments), so these
     typically yield empty — but a deployment *could* author such a format, making the projection
     undefined rather than stable;
   - **context** functions (`Environment`, `LastChanged`).
   → **Precondition (guard):** a format is index-safe only if it uses the pure subset
     (attributes + referenced category/entity names + locale). Time/environment-bearing formats fall
     back to the scan (or aren't indexed); a guard test asserts this.
3. **Per-locale** — one entity has N display names; the index is keyed per locale.
4. **Functions make it non-invertible** — index serves *name-text search only*, never attribute
   filtering (that is [PRD 082](082-storage-memory-model.md)'s read-model path). Keep the two strictly separate.

## Measurements (2026-06-24, this machine, 300k synthetic event names)

Standalone benchmark replicating `SearchMatcher` 1:1:

| Operation | Time | Notes |
|---|---:|---|
| **SUBSTRING** over 300k *materialized* names (`contains`) | **~3 ms** | the normal case — materialization alone solves it |
| **FUZZY** over 300k docs (today's `firstFuzzyMatchPosition`, sliding-window Levenshtein per name) | **~2 400 ms** | the real bottleneck — algorithmic, not scan |
| **FUZZY over the token vocabulary** (9 021 distinct tokens) + `token→ids` postings | **~1 ms** | identical hit set; the Lucene-`FuzzyQuery` trick, done in-RAM |
| vocabulary build (300k → postings) | ~400 ms | one-time at boot, incremental on the seam |

**Conclusions:**
- The per-keystroke `getName()`/`ParsedText` eval is the cost, not the string compare. **Materialise
  the derived names once** → SUBSTRING is ~3 ms.
- FUZZY must run **over the distinct-token vocabulary, not over the documents** (a few thousand
  tokens, not 300k) → ~1 ms. The "hand-rolled `Map`" [PRD 082](082-storage-memory-model.md) dismissed is exactly the right tool for
  fuzzy *when fuzzy runs over the vocabulary* — no Lucene needed to get this in the RAM model.

## Direction — in-memory derived-name index on the [PRD 082](082-storage-memory-model.md) seam

A shared, in-memory, per-locale index, maintained at the existing chokepoint
`LocalAbstractCachableOperator.updateIndizes(UpdateResult)` (where the conflict index + appointment
bindings are already kept; the multi-pod ~10 s refresh poll flows through the same path, so each pod
stays consistent for free):

```
NameIndex (per active locale):
  byId      : Map<id, { kind, typeId, foldedName }>   -- materialised derived name, lower+folded
  postings  : Map<token, Set<id>>                     -- distinct-token vocabulary → ids, for FUZZY
```

- **SUBSTRING** → linear `contains` over `byId.foldedName` (~3 ms @ 300k).
- **FUZZY** (fallback when SUBSTRING yields too few) → bounded Levenshtein over `postings.keySet()`
  (the vocabulary) with a length-band prefilter, then union the postings → candidate ids (~1 ms).
- **German folding** (ä/ö/ü/ß → ASCII-fold) applied once at projection — a free UX win the current
  `toLowerCase(ROOT)`-only matcher lacks.

**Engine: deferred.** Lucene / H2 full-text (`FullTextLucene`) is **not** built now. It becomes
relevant only if (i) [PRD 082](082-storage-memory-model.md) moves reservations out of full RAM residency (CQRS-SQL read-model) — then
the name projection folds into *that* read-model, not a separate store — or (ii) the measured
in-memory scan proves insufficient. Documented as a conditional fallback, not a v1 dependency.

## Permission — post-search, before top-N (three stages)

Permission is **per-user** and cannot live in the shared index without a per-user-index explosion;
**never pre-filter** (that means a `canModify`/`canRead` walk over all 300k — more expensive than the
search itself). The search narrows 300k → a bounded candidate set; permission runs on that set:

```
match (index)  →  rank (SearchMatcher, permission-free)  →  permission filter  →  top-N
```

**Critical §12 rule:** never truncate to top-N before permission, or you under-return / leak via the
count. Because `rank` is permission-free, bound the permission-walk set with a **generous top-K**
(e.g. 500) before the walk, then final top-N (e.g. 20) — caps `canModify` walks while keeping order
correct (K≫N makes the displacement risk negligible for a planner omnibox).
- **EVENT** → `canModify(r, caller)` (omnibox events are editable-only — *stricter* than §12 read).
- **RESOURCE** → the [PRD 028](028-angular-power-search.md) `canRead`/evaluator gate.
The index is never trusted for permission or for the final score.

## When to recompute (drift triggers)

| Trigger | Scope | Cost | Strategy |
|---|---|---|---|
| **Reservation** add/change/remove | that one entry (+ its tokens) | O(1), one `getName` | surgical |
| **Allocatable** add/change/remove | that entry | O(1) | surgical |
| **DynamicType `KEY_NAME_FORMAT`** change | all instances of that type | rare admin edit | **dirty → lazy full rebuild** (~400 ms next search) |
| **Category** rename (interpolated by a format) | referencing instances | rare edit | **dirty → lazy full rebuild** |
| **Locale set** change | all | config-time, rare | full rebuild |
| **Boot / cache reload** | all | ~400 ms one-time | rebuild |

**Does NOT trigger recompute:** appointment/date changes (the name doesn't depend on them, given the
index-safe-format precondition); permission changes (index is permission-free); user changes (unless
a format interpolates a user — rare).

**Coarse-drift decision (2026-06-24):** because the bar is "approximately performant, planner-only",
only the *frequent* triggers (Reservation/Allocatable put/remove) are surgical; all *rare* structural
edits (format / category / locale) just set a dirty flag → lazy full rebuild. This means **v1 needs
no `ParsedText` dependency analysis** — that precise fan-out (walk the parsed function tree to derive
the exact affected set) is a later optimization, only if the occasional ~400 ms rebuilds ever annoy.

## Plan

- **Phase 1 — projection + boot rebuild.** `NameIndex` class (materialise `getName(locale)` folded;
  build `postings`); rebuild at boot from `cache.getReservations()`. No consumer yet — property test
  that `byId.foldedName` matches `fold(getName(locale))` for every reservation + locale.
- **Phase 2 — maintenance at the seam.** Hook `updateIndizes`: surgical put/remove for
  Reservation/Allocatable; dirty-flag + lazy rebuild for DynamicType/Category/locale. Idempotent.
- **Phase 3 — wire the omnibox.** `searchEvents` queries the index (SUBSTRING, FUZZY fallback over
  the vocabulary) for candidate ids, then the unchanged rank → top-K → `canModify` → top-N. Golden
  test: identical hits + order to the old scan; then delete the old scan. Live latency probe vs the
  2026-06-22 baseline. RESOURCE bucket left as-is for now (bounded set); optional vocab speedup later.

**Effort estimate:** ~1–2 days. Additive, single-seam, low risk — new in-memory index (~150–250 LOC)
+ ~40 LOC at the existing seam + boot rebuild (~10 LOC) + one rewired consumer (~30 LOC) + tests. The
old scan stays until the golden test is green, then is deletable.

## Tests

- **Projection correctness (property):** for every reservation + locale, index name ==
  `fold(ClassificationImpl.getName(locale))`. Covers function-bearing (index-safe) formats.
- **Index-safe guard:** a format using an appointment/environment function is detected → falls back /
  is flagged, not silently mis-indexed.
- **Drift:** Reservation edit updates one entry; a `KEY_NAME_FORMAT` edit / Category rename triggers a
  rebuild and every affected name updates; assert `index == project(objects)`.
- **Tier-3 (`SearchGraphQLControllerTest`):** EVENT name search returns identical hits + order to the
  current scan; §12 preserved (EVENT `canModify`-only — a non-editable hit is absent, byte-identical
  to the no-match case); permission runs post-search before top-N; live latency drop on the dhbw store.
- **Locale:** a name differing across locales is found under each locale's needle.
- **Fuzzy semantics:** vocabulary-fuzzy returns the same id set as today's per-document fuzzy
  (golden), at a fraction of the time.

## Open Questions

- **OQ1 (folding)** — confirm the German fold set (ä→ae/a? ö, ü, ß→ss?) and whether folding is
  fold-both-sides (needle + haystack). New UX behaviour the current matcher lacks; pick the rule
  deliberately.
- **OQ2 (per-locale cost)** — index all configured locales eagerly, or only the deployment's active
  locale(s)? Most deployments run one or two. Note the `ClassificationImpl` single-entry name cache
  thrashes across locales, so multi-locale projection pays a full eval per locale.
- **OQ3 (RESOURCE bucket)** — leave the bounded ~48k allocatable FUZZY as-is, or also move it to the
  vocabulary index? Measure its current latency first.
- **OQ4 (EVENT windowing)** — orthogonal product question: should the omnibox event search stay
  windowless, or default to a recent/upcoming window? A window would shrink the candidate set further
  and is independent of the index. Decide with the planner UX.
- **OQ5 (`canModify`-only)** — does the EVENT `canModify`-only rule stay (stricter than read), or does
  the omnibox want a read-level event surface too?
- **OQ6 (deferred engine trigger)** — make explicit the threshold at which the Lucene/H2 fallback
  becomes worth it (082 goes CQRS-SQL, or measured in-memory latency exceeds X on the dhbw store).
