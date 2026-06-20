# PRD 075 — Expression-language standardization (investigation)

**Status:** draft (investigation — no implementation committed). **Scope broadened 2026-06-20:**
this PRD was originally framed as *naming/vocabulary only, engine fixed as rapla's own*. That
framing prematurely closed the engine question. The investigation is now reopened to **genuinely
evaluate alternative expression languages** (CEL, JSONata, JMESPath, JEXL, restricted SpEL, or a
formalized rapla DSL) as candidate engines across **all** of rapla's expression use cases — not
merely to align the names of the existing rapla Functions. The naming-alignment work below is
retained as a **sub-question** (it still applies *if* rapla Functions are kept).

## Goal

**Evaluate whether any alternative expression language fits rapla's expression use cases better
than rapla's own `Function`/`ParsedText` evaluator — for some or all use cases — under rapla's
hard constraints** (server-side safety / no-eval / no-RCE, embeddability on the JVM, and the
need to introspect the expression vocabulary for SDL/schema-boundary documentation).

Candidate engines in scope for evaluation: **CEL** (cel-java), **JSONata**, **JMESPath**, **JEXL**,
**restricted SpEL** (locked-down `SimpleEvaluationContext`), a **formalized rapla DSL** (rapla's
own grammar, cleaned up and specified), and **keeping rapla Functions as-is**. The earlier framing
that "the engine stays rapla's own (CEL evaluated and rejected — PRD 073/074)" is **no longer a
premise** — it is now one of the outcomes this PRD must justify or overturn. A different engine may
fit *some* surfaces (e.g. predicate/filter use cases) even if rapla Functions remain best for
*others* (e.g. text composition); a per-use-case verdict is acceptable.

If — and only if — the conclusion is that rapla Functions are kept (for all or some surfaces),
the **naming/vocabulary** sub-question applies: can we get part of the standard-EL benefit
(recognizability, less documentation, a name-compatible path to a *future* standard engine) by
**standardizing the function names**, and which surfaces should share one vocabulary? The
multi-agent naming findings (below) already answer that sub-question for the rapla-Functions case.

## Scope

**In scope — the central question: which engine(s).** For each candidate (CEL, JSONata, JMESPath,
JEXL, restricted SpEL, formalized rapla DSL, keep-rapla-Functions), evaluate fit against rapla's
expression **use cases** and the **evaluation criteria** below. Output: a per-use-case engine
recommendation (one engine, several engines, or rapla Functions retained), with the safety/JVM/
introspection constraints as gates.

**Expression use cases to cover** (the full surface inventory the engine choice must serve):

- **View compute cells** — derived per-row column values in GraphQL/SPA views (PRD 074).
- **nameformat / `displayName` / export / planning compositions** — the four named composition
  annotations (PRD 073).
- **`ClassificationFilter` predicates** and the generated `where<TypeKey>` typed filters (PRD 059).
- **SPA / query search expressions** — `searchText` / `matchKind` ranked matching (PRD 059).
- **Export / template formatting** — iCal (`Export2iCalController` / `CalendarPageController`),
  CSV, `AllocatableExporter`.
- **Attribute validation constraints** — expression-based field rules.
- **External-import field mapping** — Dualis import wizard source→attribute mapping (PRD 068).
- (Watching brief) notification / mail templates (`MailInterface`) — dynamic text; conditional
  permission / conflict-exemption rules if rapla ever expresses these as expressions.

**Evaluation criteria** (apply per candidate × use case):

1. **Safety** — non-Turing-complete / mutation-free / no-RCE for untrusted server-side eval
   (the gating criterion; SpEL/MVEL-class RCE liabilities are disqualifying unless provably locked).
2. **JVM embedding** — a maintained JVM implementation, embeddable bounded and sandboxed,
   acceptable licensing/maturity.
3. **Syntax fit** — match to each surface's shape (prefix-call composition vs predicate/operator
   tree vs ranked search vs source-field mapping).
4. **Use-case coverage** — does the language express what the surface needs without contortion.
5. **Migration cost** — converting stored expressions, aliasing/back-compat, dual-engine burden.
6. **SDL-introspection / doc story** — can the vocabulary be enumerated and documented at the
   GraphQL schema boundary (the catalog requirement from PRD 073/074).

**Sub-question (applies only if rapla Functions are kept, for all or some surfaces) — naming
alignment.** The catalog splits cleanly (verified against `StandardFunctions`):

- **General-purpose ops** — `not` `and` `or` `if` `concat` `substring` `format` `filter`
  `sort` `equals` `reverse` `index` `key` `intervall`. Mostly **already standard names**;
  candidates for alignment.
- **Domain accessors** — `isPerson` `isLocation` `resources` `persons` `events`
  `appointments` `appointmentBlocks` `times` `start` `end` `lastchanged` `name` `attribute`
  `type` `parent` `env` `date` `number`. **No standard equivalent** ("get the rooms of an
  event" is rapla domain vocabulary); these are documented, not standardized.

Three naming options under consideration:

1. **Common-intersection vocabulary** (SQL/spreadsheet: `concat`/`substring`/`if`/`and`/`or`/
   `not`/`format`/`coalesce`) — best fit for rapla's prefix-call style, maximal recognizability,
   **no commitment** to one external EL.
2. **CEL names** — adopt the vocabulary of CEL (`startsWith`/`contains`/`matches`/`size`,
   `==`/`!`/`&&`, conditional `?:`). Strategic, broad; names align even though CEL is
   method/operator-style vs rapla's prefix-call. Keeps a name-compatible path to a *future* CEL engine.
3. **Typos/obvious only** — `intervall`→`interval`, `equals`→`eq`; otherwise leave rapla names.
   Minimal hygiene, no external standard.

**Implementation constraint (naming sub-question, any option):** additive **aliases in
`FunctionFactory.createFunction`** (legacy *and* standard name resolve to the same `Function`)
— existing stored compositions never break; the catalog advertises the standard name.

**Shared-vocabulary sub-question.** If rapla Functions are kept, weigh whether a single
standardized vocabulary should serve more than view columns (composition family vs predicates vs
search). For each surface: does it already use `ParsedText`? would a shared vocabulary help or
over-couple? what is the cost of *not* sharing?

**Out of scope.** Changing GraphQL **query** syntax (the GraphQL transport itself). Any
view-design decision already locked in PRD 073/074 *except* the engine choice, which this PRD
reopens. (Engine replacement was previously listed out of scope; it is now the central question.)

## Plan

1. **Survey standard ELs** — CEL, JSONata, JMESPath, SpEL, common SQL/spreadsheet function
   vocabularies. For each: name overlap with rapla's general ops, syntax-style fit
   (prefix-call vs method/operator), licensing/maturity, whether anyone embeds it server-side
   bounded.
2. **Map rapla's general ops** to each surveyed vocabulary; quantify the alias delta per
   option (1/2/3).
3. **Audit the other surfaces** above — which already share `ParsedText`, which reinvent, what
   a shared vocabulary would cost/save.
4. **Recommend** a naming option + an alias plan + a position on shared-vocabulary scope.

## Tests

Investigation PRD — no production code. If a naming option is adopted later, its own PRD adds:
alias-resolution unit tests in `FunctionFactory` (legacy + standard name → same `Function`),
and a round-trip test that an existing stored composition still parses unchanged.

## Findings (multi-agent investigation, 2026-06-20)

### (a) EL survey verdict — align the general ops to the SQL/spreadsheet scalar vocabulary

**Anchor the boolean + conditional + string half on the SQL/spreadsheet scalar-function
vocabulary** (`NOT`/`AND`/`OR`/`IF`/`CONCAT`/`SUBSTRING`/`FORMAT`, plus a new `COALESCE`).
It is the only surveyed source that is **natively prefix-call** — matching rapla's
`substring(s,start,len)` / `if(c,t,e)` / `concat(a,b)` shape almost verbatim — **ISO-standardized**
(SQL + SQLite + Excel + Sheets + every BI tool), **dependency-free** (a naming convention, not a
library), and **maximally recognizable** to any analyst/report author without a lookup.

Fill the **list-transform names** (`filter`/`sort`/`reverse`) from **JSONata/JMESPath**, where they
are already prefix-call functions (`$sort`/`$reverse`/`$filter` in JSONata; `sort`/`reverse` in
JMESPath) and where rapla's current names already coincide — so zero churn.

The rejected/deferred sources:
- **CEL** — best-engineered **safety** model in the survey (non-Turing-complete, mutation-free,
  built for untrusted server-side eval) and the right template to keep in mind *if the engine is
  ever reconsidered* — but its `not`/`and`/`or`/`if`/`equals`/`filter`/`sort` are **operators and
  macros, not named functions**, so they yield almost no usable prefix-call aliases for rapla's
  current ops. Borrowing only string-op spellings buys little over SQL and costs SQL's
  recognizability. **Deferred, not adopted.**
- **SpEL, MVEL** — vocabulary-irrelevant and active **RCE liabilities** (SpEL
  `StandardEvaluationContext` CVE-2022-22963; MVEL == arbitrary Java). Cited only as what *not* to
  embed.
- **Liquid/Handlebars** — pipe/method-style filters, not prefix-call; useful only as a secondary
  sanity-check that rapla's `reverse`/`sort`/`concat`/`replace`/`format` names match common
  template naming.

Note the structural asymmetry the Scope already flags: rapla's **domain accessors** (`isPerson`,
`appointmentBlocks`, `lastchanged`, `key`, `intervall`, …) have **no analog in any surveyed
language** and stay rapla-native regardless. Standardization scope is the general-ops half only.

### (b) Surface-sharing verdict — share within the composition family, keep predicates/search independent

**Do not force one vocabulary across all surfaces.** The audit splits the eight surfaces into two
groups with orthogonal semantics:

- **Composition family — already shares `ParsedText`; consolidating the vocabulary is a free win.**
  nameformat / `displayName` / export / planning compositions
  (`DynamicTypeAnnotations.java:17-20`; `ParsedText.java:56,140-175`), and all export surfaces —
  iCal/CSV/table view — already route through the same `KEY_NAME_FORMAT_EXPORT` annotation via
  `NameFormatUtil` (`Export2iCalConverter`, `DefaultRaplaTableColumn.java`). These already use
  `StandardFunctions`' 21+ functions (`StandardFunctions.java:27-77`); a richer shared catalog
  helps them uniformly. **Mail templates** (`MailInterface.java:5-15` — plain strings today;
  `MailConfigController.java:85-86` hardcoded) are a natural *future* `ParsedText` adopter, no
  reinvented language to displace.

- **Predicates + search — separate mini-languages by design; merging over-couples.** Strongest
  evidence: `ClassificationFilter` predicates are **pre-compiled operator trees**
  (`ClassificationFilterImpl.java:108-173`, `Object[][]` conditions) and `WhereEvaluator` is a
  **runtime map-based predicate** evaluator with AND/OR/NOT combinators and comparison operators
  (`WhereEvaluator.java:37-516`, combinators 81-113) — a query language, not text composition. SPA
  search is a **three-level ranked matcher** (PREFIX/SUBSTRING/FUZZY, Levenshtein ≤1)
  (`SearchMatcher.java:29,38-79`; `ClassificationGraphQLController.java:141-154`) — a finding
  algorithm, not composition. Folding these into `ParsedText` would force it to become a full query
  language (combinators, attribute comparisons, null-checks), breaking the clean composition/predicate
  separation. **Constraints** (`ConstraintIds.java:15-21`), **external-import mapping**
  (`ExternalEventImportService.java:17-51`), and **permission/conflict rules**
  (`PermissionImpl.java:33-127`; `ConflictFinder.java:51-100`) are declarative flags/enums with **no
  expression language to unify** — neutral.

**Cost of not sharing:** near zero — the composition family is already shared; the predicate/search
surfaces have no expression overlap to lose. **Cost of over-sharing:** high — coupling four
orthogonal problem domains onto one tree.

### (c) Recommendation — Option 1 + Option 3 hygiene, additive aliases, composition-family scope

Adopt **Option 1 (common-intersection / SQL-spreadsheet vocabulary)** as the primary naming source,
**folding in Option 3's two hygiene fixes** (they are objective defects, orthogonal to the
vocabulary choice). Implement entirely as **additive aliases in `FunctionFactory.createFunction`**
so legacy and standard names resolve to the same `Function` and every stored composition keeps
parsing.

Alias plan (canonical ← legacy/variant):
- `NOT`←`not`, `AND`←`and`, `OR`←`or`, `IF`←`if` (alias `IIF`), `CONCAT`←`concat`,
  `SUBSTRING`←`substring` (alias `SUBSTR`), `FORMAT`←`format` (alias `TEXT`).
- `EQ`←`equals` (SQL `=` is an operator with no scalar name; expose `EQ` canonical, `EXACT` as a
  secondary alias; **resolves the `equals`→`eq` hygiene fix too**).
- **New** `COALESCE` — rapla has no exact op; natural additive companion to `if`/`equals`.
- `filter`/`sort`/`reverse` — keep rapla names (already aligned with JSONata/JMESPath prefix funcs).
- Hygiene: `interval`←`intervall` (fix the double-`l` typo, alias both so stored compositions parse).
- Domain accessors unchanged (no external anchor exists).

**Shared-vocabulary scope:** the catalog applies to the **composition family only**
(nameformat/displayName/export/planning + iCal/CSV/table export, and mail templates when they grow
dynamic text). **Predicates (`ClassificationFilter`/`WhereEvaluator`) and SPA search keep their own
languages.**

**Engine unchanged** — rapla's `ParsedText`/`Function` tree stays; CEL remains rejected as an
engine (PRD 073/074). This is naming + scope only.

### (d) Implementation path for the renames (decided 2026-06-20)

1. **Catalog advertises canonical names only.** The generated `ComputeFunctions` catalog (PRD 073)
   lists the canonical/standard names (`EQ`, `interval`, `CONCAT`, …) and **omits legacy names
   entirely** — no `@deprecated`, no noise. "So much as needed, no more."
2. **Legacy names stay as silent input aliases** in `FunctionFactory.createFunction` (the dispatch
   is a `switch` — extra `case` labels fall through to the same constructor), so every stored
   composition keeps parsing. Indefinitely cheap; never advertised.
3. **Canonical re-emit (Option B — parameterize the name).** Give the function a constructor that
   takes the *typed* name instead of hardcoding `ID` (`super(NAMESPACE, name, args)`), and dispatch
   the canonical name through it. `getRepresentation` then emits the canonical form, so a stored
   `equals(...)` **normalizes to `EQ(...)` the next time it is saved** — legacy names self-migrate
   out of stored data; the catalog omission has no lasting downside. (Optional one-shot rewrite à la
   `GraphqlKeyMigration` to flush the rest — not required.)
4. **No cloning** for pure renames (copy-paste + drift risk). Clone a function only if the new name
   must also diverge in *behaviour* (different args/semantics).

This keeps the rename **additive and non-breaking** (legacy parses forever, canonical is shown +
emitted) and needs no deprecation lifecycle.

## Candidate EL evaluation (multi-agent, 2026-06-20)

This section re-tests the **engine** question (not just naming) across all candidates, including
the formalized-rapla-DSL baseline. Source-verified where claimed (e.g. `ParsedText.parseFunctions`,
`StandardFunctions.java:39-77`).

### Comparison matrix

| Candidate (lib, license) | Safety | JVM / license | Syntax fit | Migration cost | SDL-introspection doc story | Use-case fit summary |
|---|---|---|---|---|---|---|
| **Formalized rapla DSL** (in-tree `org.rapla.entities.dynamictype.internal`, AGPL/Apache2) | **Strongest** — whitelist-by-construction, closed ~30-fn `switch`, no eval/reflection/RCE, non-Turing, depth-capped (>6). DoS-shaped residual only. | None — it *is* the engine. Zero dep/license/sandbox. Optional ANTLR4 (BSD-3) for a specified grammar. | **Perfect** for composition — its prefix-call shape IS the SQL/spreadsheet shape others regress from. | **Zero** — no conversion, no dual-engine, server-side only. | **Weakest point** — vocabulary is a hand-maintained `switch`, no machine-readable signatures; catalog must be hand-authored + CI-tested to avoid drift. | good: views, nameformat/export, validation-capable; weak: search (wrong category), filters (clumsy call-shape) |
| **CEL** (cel-expr/cel-java v0.13.1 *or* projectnessie/cel-java v0.6.1, Apache-2.0) | **Best-in-class** — non-Turing by construction + runtime cost budget (k8s admission model). No host-classpath escape. Safe-by-construction. | Clean license, Java 8+. **Integration cost: protobuf-centric type system** — binding rapla POJO graph needs custom Env types/overloads (the bulk of the work). | **Poor** for prefix-call — ops are operators/macros (`a && b`, `list.filter(x,p)`), **not aliasable**. | **High, not aliasable** — adoption = rewrite + 2nd parallel engine. | Mixed — grammar is public/pretrained (AI gets it free), but rapla Env bindings still must be projected into SDL. Typed Env gives auto-projectable signatures. | good: validation, predicates, view cells; poor: text composition (no template mode) |
| **JSONata** (com.dashjoin:jsonata v0.9.x, Apache-2.0) | **Disqualifying** — self-described **Turing-complete** (lambdas, recursion, historical `$eval`). DoS intrinsic. Fails rapla's hard non-Turing gate; safe only behind a hardened profile on an immature port (~17★, no built-in caps). | Apache-2.0 but **low maturity** single-vendor port; weak sandbox knobs vs JSONata-Go/JS. | Partial — path-query idiom; `&` concat, `?:` ternary — **rewrite, no alias**. | High, disruptive, no alias path. | Poor — vocabulary not SDL-expressible (opaque String arg). | good: **import field-mapping (Dualis)** only; loses elsewhere |
| **JMESPath** (io.burt:jmespath-core, BSD-3) | **Strong** — bounded, non-Turing, no arithmetic/concat/if, closed fn set, no host reach. | BSD-3 but **ARCHIVED/read-only since 2024-10**, no Jackson-3 adapter — hand-write a custom `Adapter`. | **Poor / model-mismatch** — JSON projection DSL; **no concat, no arithmetic, no if/else**. | High, mostly unrewarding — most nameformats can't even be expressed. | Weak — grammar not SDL-expressible; needs out-of-band ref + JSON shape. | partial: import extraction, filter overlap; structurally **cannot** do composition |
| **JEXL 3** (org.apache.commons:commons-jexl3 3.6.2, Apache-2.0) | **Not bounded** — Turing-complete (loops, assignment, method invocation). Hardenable (JexlPermissions+JexlSandbox) but **opt-in, config-fragile**; loop-DoS survives even a perfect method sandbox (needs external timeout). | Apache-2.0, very mature (~20y), but pulls commons-logging (unwanted vs SLF4J). | Poor-partial; infix/imperative, **not aliasable** onto prefix `if(c,t,e)`. **More readable** than nested prefix though. | High — rewrite + re-register all ~30 domain fns as namespaces. | Weak — property access aligns to SDL fields (plus), but fn/permitted-method list is external (minus). | good (readability): views, nameformat/export, validation; partial: filters/search/import |
| **Restricted SpEL** (org.springframework:spring-expression, Apache-2.0) | Bounded but **sharp edges** — default is RCE (`T(...).exec`); whole CVE history is SpEL injection. Safe **only** via `SimpleEvaluationContext.forReadOnlyDataBinding()` (config-fragile, not safe-by-construction). | **Zero new dep** (already on classpath via Spring Boot 4), Apache-2.0, maximal maturity. | Poor-partial for domain idiom; infix is fine but **no domain verbs** — must re-expose all ~38 fns. | High for incumbents; **low for greenfield** (no aliasing path). | Weak — no self-introspection; vocabulary lives in `SimpleEvaluationContext` wiring, invisible to SDL. | good: **attribute validation**, **Dualis import mapping** (greenfield); lateral-to-negative elsewhere |

### Per-use-case recommendation

| Use case | Best fit | Beats rapla Functions? |
|---|---|---|
| **View compute cells (PRD 074)** | **rapla Functions** (already locked: server-side ParsedText) | **No.** Single-value prefix composition is exactly ParsedText's shape; CEL/JEXL only add infix ergonomics + a dual runtime. |
| **nameformat / displayName / export / planning (PRD 073)** | **rapla Functions** | **No — and decisively.** All candidates lack a template/interpolation mode; CEL/JMESPath fight non-string values; ParsedText's literal-text-with-holes model is the right tool. |
| **ClassificationFilter + where<TypeKey> (PRD 059, done)** | **Structured GraphQL where-inputs (already shipped)** | **No.** CEL is the *predicate-shaped* candidate that could theoretically win, but PRD 059 already ships a typed, SDL-introspectable, permission-aware, depth-capped evaluator; a free-text EL is a regression on introspection + leak-safety. Keep structured filters; CEL only if a free-text predicate surface is ever needed. |
| **SPA / query search** | **Neither — it's a ranked fuzzy-matcher** (`SearchMatcher`, Levenshtein) | **No EL applies.** Scoring/ranking algorithm, not boolean/scalar eval. Wrong tool category for every candidate. |
| **Export / template formatting (iCal, CSV, AllocatableExporter)** | **rapla Functions** | **No.** Already routes through `KEY_NAME_FORMAT_EXPORT`; same text-composition weakness sinks CEL/JMESPath; SpEL/JEXL are lateral. |
| **Attribute validation constraints** | **CEL** (capability) / **SpEL** (zero-dep pragmatics) | **Yes — narrowly, greenfield.** No incumbent rapla expression facility exists. CEL is the best-fit tool (non-Turing + cost budget = the k8s CRD-validation idiom); SpEL `forReadOnlyDataBinding` is the zero-new-dep pragmatic pick. rapla Functions *could* back booleans but the surface doesn't exist yet. |
| **External-import field mapping (Dualis, PRD 068)** | **JSONata / SpEL / JMESPath** (per-field transform) | **Partial.** JSONata's home turf (JSON→target reshaping) but needs a hardened profile; SpEL greenfield per-field infix is cleanest given zero new dep. Even here a small bounded mapping DSL may beat pulling in a dependency. Pure-expression core only — orchestration stays outside any EL. |

### Overall recommendation — **keep rapla Functions for all composition/export/view surfaces; reconsider a bounded EL only for the two greenfield predicate/transform surfaces**

**Keep the formalized rapla DSL as the engine for views (074), nameformat/displayName/export/planning
(073), and iCal/CSV/table export.** It wins outright on safety (whitelist-by-construction —
Painless/CEL-class, for free), JVM embedding (zero dep/license/sandbox), migration (zero), and
syntax fit (its prefix-call shape *is* the SQL/spreadsheet shape every alternative regresses from).
No surveyed engine beats it on any composition surface, and all of CEL/JSONata/JMESPath lack the
text-templating mode these surfaces fundamentally need. Its one real liability — the vocabulary is a
hand-maintained `switch` with no machine-readable signatures — is an argument **for** the
"formalize as a named grammar" investment (a reflected, CI-tested signature registry projectable
into the SDL), **not** for swapping engines: rapla's domain accessors (`isPerson`,
`appointmentBlocks`, `attribute(obj,key)`) have **no analog in any candidate**, so the catalog is
unavoidable in every language.

**Do NOT adopt any alternative as the universal engine.** CEL is the only candidate with a
safety/introspection story competitive with rapla's own, but it is **not aliasable** onto the
prefix-call vocabulary (its ops are operators/macros), so adoption is a rewrite + a permanent
second engine + the protobuf↔POJO binding cost — and it still loses the text-composition surfaces.
JSONata (Turing-complete, immature port) and JEXL (Turing-complete, config-fragile) fail or strain
the hard non-Turing safety gate. SpEL is the most CVE-laden EL on the JVM and is safe only by
fragile configuration.

**The defensible exception — a deliberate, narrow second engine for two greenfield surfaces:**
(1) **attribute validation constraints** and (2) **Dualis import field-mapping (PRD 068)**. Both
have **no incumbent rapla-Function usage** and **no SDL-introspection requirement**, so adopting a
bounded EL there costs near-zero migration and introduces no parity burden on the stored-expression
corpus. For validation, **CEL** is the best-fit tool (bounded + cost budget); for both, **restricted
SpEL** (`SimpleEvaluationContext.forReadOnlyDataBinding`, already on the classpath, zero new dep) is
the pragmatic pick if a single new engine is preferred over CEL's protobuf-binding cost. Before
committing to either, weigh a small bounded mapping/validation DSL on the existing `Function` tree —
it may suffice and keeps the engine count at one.

This **confirms the PRD 073/074/075 conclusion on evidence, not by rubber-stamp**: the engine stays
rapla Functions everywhere it is incumbent. The genuine update is that the door PRD 075 left
open — "an alternative may fit *some* surfaces" — resolves to exactly two greenfield surfaces
(validation, import mapping), and CEL (not the SQL-naming layer) is the right design template if
either is ever built.

## Open questions

1. ~~Which naming option (1 / 2 / 3)~~ **Resolved:** Option 1 (SQL/spreadsheet vocabulary) as
   primary, plus Option 3's two hygiene fixes (`intervall`→`interval`, `equals`→`eq`). List-transform
   names sourced from JSONata/JMESPath. Option 2 (CEL names) deferred — yields almost no usable
   prefix-call aliases today.
2. ~~One shared vocabulary across surfaces, or per-surface freedom?~~ **Resolved:** share within the
   **composition family** (already on `ParsedText`; free win); keep **predicates and search**
   independent (orthogonal semantics — `ClassificationFilterImpl`/`WhereEvaluator`/`SearchMatcher`).
3. If a future standard *engine* is ever reconsidered, does name-compatibility (option 2) actually
   lower that cost, or is the engine swap the dominant cost regardless? **Resolved — engine swap
   dominates; name-compatibility buys almost nothing.** The 2026-06-20 candidate evaluation confirms
   it on evidence: no alternative engine is **aliasable** onto rapla's prefix-call vocabulary. CEL,
   JSONata, JEXL, and SpEL all express the general ops (`and`/`or`/`if`/`equals`/concat) as
   **operators/macros/infix**, not named functions, so a call-shape rewrite is unavoidable and
   Option-2 naming would not survive it. The dominant swap costs are (i) rewriting every stored
   composition (no automatic translation exists), (ii) running a permanent second engine alongside
   ParsedText (dual mental model + dual catalog), and (iii) for CEL specifically, binding rapla's
   POJO entity graph into its protobuf-centric type system. **Conclusion:** keep rapla Functions as
   the engine for all composition/export/view surfaces; the *only* place a bounded EL (CEL preferred
   for capability, restricted SpEL for zero-dep pragmatics) is worth adopting is the **two greenfield
   surfaces** (attribute validation, Dualis import mapping) where there is no stored corpus to
   migrate and no parity burden — see "Candidate EL evaluation" above.
4. Where does the catalog/naming doc live — PRD 073 (catalog) vs here? **Still open** — defer to the
   implementation PRD that lands the aliases; the catalog (073) is the likely home since it already
   documents the curated function set at the schema boundary.
