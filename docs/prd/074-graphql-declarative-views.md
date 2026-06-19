# PRD 074 — Declarative GraphQL View Definitions

**Status:** draft — design resolved 2026-06-19 after a prior-art deep-dive
(6-agent workflow + direct investigation). **Recommendation: Option A (assembled
bounded spec), pending sign-off.** Data-layer feeder: [PRD 073](073-graphql-function-equivalents.md).

## Goal

A **saved view** lets an admin define a data view by storing two artifacts — a
**GraphQL document** (inputs + data) and a **bounded transform spec** (the
displayable shape) — with **no client redeploy and no server-side column config**.
The Angular SPA is a generic renderer; the *same* transform also runs server-side
for CSV/HTML/iCal export. Replaces TableView's per-deployment column config
(`/table/*`, PRD 030 — deprecated, frozen, **no migration**; the new path is
greenfield).

## Architecture at a glance

GraphQL (data + mutations) is the spine. Each rendering surface uses the best tool
for *its* output; only **server-rendered table export** is constrained to a
dual-runtime engine (it needs a JVM evaluator), which is why we build our own
bounded spec there:

| Surface | Renders | Engine | Status |
|---|---|---|---|
| **Read tables** (SPA + CSV/HTML/iCal) | client **and server** | **A-CEL** transform; rendered by **`cdk-table`** + `ngComponentOutlet` registry | this PRD |
| **Charts** | client only | **Vega-Lite** (interpreter mode, CSP-safe) | companion |
| **Edit forms** | client only | **ngx-formly** → GraphQL mutations | companion / future PRD 075 |

The dual-runtime constraint bit **only** server-rendered tables. Charts and forms
are client-only, so they adopt the best client-native option directly. **Adaptive
Cards is dropped** (its only value was cross-host portability, which an Angular-only
SPA doesn't need — ngx-formly replaces it for forms; CEL + the flatten
parent-projection replace its templating role on the read side). **A′ (GraalJS) is
withdrawn** — CEL gives cross-runtime parity without a server-side JS engine.

## Locked decisions

1. **Separation of concerns** — GraphQL owns **prediction (filtering) + navigation
   (which data)**; the transform owns **formatting + grouping + aggregation**. The
   rapla nameformat DSL (`ParsedText`) is **not** used here (stays in deprecated
   Swing/HTML TableView).
2. **Maximize GraphQL; keep the transform thin.** Filtering/partitioning/computed
   scalars belong in the query. E.g. the room/course/lecturer split is **aliased,
   filtered sub-selections** in the query, so the transform only joins:
   ```graphql
   raum:   allocatables(typeKeyIn:["Raum","virtuellerRaum","Teilraum"]) { displayName }
   dozent: allocatables(isPersonEq:true)                                 { displayName }
   ```
   (Needs data-layer additions — see Dependencies.)
3. **Client + server, one definition** — server-rendered HTML/CSV requires the
   transform to evaluate on the JVM, so the engine must be dual-runtime.
4. **Engine = Option A, a bounded spec we *assemble* from vetted prior art** (not a
   Turing-complete language). See §Transform engine.

## Inputs = query variables (controls inferred by convention)

The client maps each variable to a control by **name + type**; pageability adds
navigation. No directive for the common cases:

| Variable(s) | Inferred control |
|---|---|
| `from` + `to` (date/time) | date-range picker |
| `name` / search string | search combobox |
| query is pageable (offset/cursor) | next / prev — *future* |

Controls are state over variables: the control mutates a variable, the query
re-runs. Non-conventional bindings use a `@control` client directive (override
only).

## Data = GraphQL (prediction + navigation only)

The query decides **what data is in the result**: prediction (`where` PRD 059 +
`searchText`/`matchKind` PRD 028 + access selectors PRD 069), navigation
(selection), and **server-computed scalars** the client can't derive
(`displayName`, `durationMinutes`, typed attributes — the PRD 073 gaps). It selects
raw nested data; it does **not** shape a table.

**Trust precondition for "maximize GraphQL".** A view may push a filter into the
query and omit it from the transform layer **only if every advertised predicate of
that input actually runs in the resolver.** A filter input that advertises more
than it honours (the silent `advertised ≠ honored` contract lie — see the
nested-`allocatables` defect) would make the view drop a filter that never fires.
Hence views depend on the schema-hygiene guideline in
[PRD 073 § Schema-design guideline](073-graphql-function-equivalents.md): focused
filter inputs, shared matcher logic, one test per filter field.

## Transform engine — recommended: Option A (assembled bounded spec)

The decision rests on a residual analysis: after maximizing GraphQL, what the
transform must still do is **flatten tree→rows, filter, derive/format, group,
aggregate** — a small, **bounded, non-Turing-complete** set. No single existing
spec can be adopted wholesale (each fails a non-negotiable), so we **assemble** one
from vetted parts.

### Decision matrix

| Criterion | **A. Own bounded spec** | B. Adaptive Cards + AEL | C. Vega-Lite | D. JSONata | E. Raw JS |
|---|---|---|---|---|---|
| Standard / ecosystem | 🟡 assembled from standard parts | ✅ MS spec | ✅ viz grammar | 🟡 niche | ✅ ECMAScript |
| Bounded / total (not Turing-complete) | ✅ by construction | ✅ | ✅ | ❌ TC + `$eval` RCE + CVEs | ❌ |
| Capability-confined (no DOM/net/host) | ✅ | ✅ | ✅ | ❌ | ❌ |
| Dual-runtime (TS **and** Java) | 🟡 we write both (small) | ❌ no JVM engine | ❌ no JVM engine | 🟡 2nd impl drifts | ✅ but heavy/unsafe |
| Plugin-extensible (compiled, not author code) | ✅ rapla `FunctionFactory` | ✅ | 🟡 JS-only | 🟡 + `$eval` hole | ❌ |
| CSP: no `unsafe-eval` | ✅ AST tree-walk | 🟡 | 🟡 default code-gens | ❌ | ❌ |
| Authoring tooling | 🟡 Monaco + JSON Schema | ✅ designer | ✅ editor | 🟡 exerciser | 🟡 |
| Fit to the residual | ✅ sized exactly | 🟡 no table/aggregate | ✅ near-1:1 transforms | ✅ overshoots | ✅ does anything |

- **D JSONata — REMOVED from consideration (2026-06-19).** Turing-complete,
  `$eval` RCE, CVE stream, `unsafe-eval`. Out.
- **E raw JS — rejected** (no author/code boundary; forces `unsafe-eval`).
- **B Adaptive Cards / C Vega-Lite — adapt the model, reject the *full* runtime**
  (no first-party JVM engine; AC is card-shaped with no table/aggregate; Vega's
  default needs `unsafe-eval`). **But see Alternative A′** — if we adopt their
  *real* syntax we can reuse their real JS libs on the client and run them on the
  JVM via GraalJS.
- **A — build, assembled from vetted parts.** Weaknesses (no standard / we write
  both interpreters) are mitigated: the **residual is tiny**, every semantic choice
  **traces to a vetted source**, parity is enforced by a compliance corpus. Tiny,
  eval-free, no big dependency — the **security floor**.

### Alternative A′ — adopt real AC/Vega *syntax* and reuse the libraries

If the spec **is** real Adaptive Cards templating + real Vega transforms (not
"inspired by"), then the **client uses the actual `adaptivecards-templating` +
`vega`/`vega-lite` JS libs** (free ecosystem, tooling, designer/editor), and only
the **server table/CSV/HTML export** needs a JVM evaluator.

**Parity crux:** if the client runs the full real lib but the server runs a Java
*subset*, the client is more capable → an admin can author something that renders
in the SPA but **breaks server export**. So both sides must run the *same*
semantics. Options for the server:
1. **GraalJS — run the real JS libs on the JVM** (confirmed: GraalJS runs npm via
   the Context API). **Same code both sides → perfect parity, zero reimplementation,
   fully standard syntax.** This is "find an implementation" = the library itself.
   It runs **trusted library code over the author's declarative JSON (data), in
   interpreter/CSP-safe mode** — *not* author JS, so it is not the rejected
   eval-author-code risk. Cost: **GraalVM dependency** (heavy) + a server-side JS
   engine (larger surface than a bounded Java interpreter); bound to external spec
   churn; needs a feasibility spike (vega/AC may pull Node built-ins → bundle).
2. **Reimplement the needed subset in Java** (transform + binding eval only) — but
   chasing external full-spec semantics = drift risk + must lint the client to the
   subset (partly defeats reuse).
3. **Node sidecar** — real libs in a Node process the JVM calls; ops-heavy.

**A vs A′ is the project's core tension:** *minimal-dependency + control + security*
(A) vs *maximally-standard + reuse + same-code parity* (A′ via GraalJS).

**Deciding step — a 1–2 day GraalJS feasibility spike**: do `vega` +
`adaptivecards-templating` load and evaluate a transform/binding under a GraalJS
`Context` (no Node built-ins, interpreter mode, CSP-safe)? **If yes → A′** (standard
syntax, free client, same-code parity). **If messy → A** (bounded, eval-free, no big
dep) is the floor. A′ is the upside bet; A is the safe fallback.

### Amendment — CEL as the expression sublanguage (→ A-CEL). RECOMMENDED.

A background candidate sweep (2026-06-19) found one decisive win: **CEL (Common
Expression Language)** for A's expression slots (per-cell `calculate` + `filter`
predicate).
- **Non-Turing-complete by design** (the headline property, not a watchdog),
  capability-confined, **CSP-clean with zero relaxation** (tree-walk, no `eval`, no
  `wasm-unsafe-eval`).
- **Mature, conformance-tested runtimes on both sides:** `dev.cel:cel` (Google,
  JVM) + `@marcbachmann/cel-js` (TS, tree-walking). One spec drives both → real
  parity. Vetted at Kubernetes/Envoy scale.
- It is an *expression* language, **not** a pipeline: it fills `calculate`/`filter`;
  **flatten/group/aggregate/format stay the bespoke pipeline shell** — no candidate
  supplies a bounded, dual-runtime, CSP-clean *pipeline* language, which is itself
  the argument the bespoke shell is right.

**Effect: A → A-CEL** — the bespoke bounded pipeline (Vega-Lite transform vocab + AC
templating + RFC 9535 path) with **CEL in the expression slots**, and
`FunctionFactory` plugin ops exposed to CEL as host functions. This **deletes A's
highest-risk component** (two hand-written expression interpreters kept bit-parity
by golden corpus); the corpus then only proves *pipeline-stage* parity.

**This deprioritizes A′.** A′'s whole pitch was GraalJS for parity — but **CEL gives
cross-runtime parity without shipping a JS engine into the server**, the exact heavy
eval-capable surface A′ adds. **A-CEL is preferred over A′.**

**Evidence — CEL × GraphQL is rare-but-validated, and we couple looser than the
precedent (web check 2026-06-19):**
- **CEL itself is high-standard**, but in the API/authz boundary, not in GraphQL:
  Kubernetes (CRD validation, admission policies), Envoy, Confluent Cloud mTLS
  filters, Firebase rules.
- **CEL embedded *in* GraphQL has one clear production precedent — Twisp**, which
  exposes CEL as a first-class GraphQL `Expression` scalar (wherever the schema's
  `Expression` type appears, a CEL string is accepted and evaluated). So even the
  *tight* fusion ships in production.
- **GraphQL has no standard for an inline filter/expression language** — the spec
  issue [graphql/graphql-spec#271](https://github.com/graphql/graphql-spec/issues/271)
  has debated exactly this for years, unresolved. The dominant GraphQL pattern for
  logic is `@auth`-style **directives backed by host-language (JS/Java) resolvers** —
  i.e. developer code, not an embedded expression sublanguage.
- **A-CEL couples *looser* than Twisp:** CEL is **not** inside our GraphQL document.
  GraphQL does prediction/navigation → returns JSON → the bespoke pipeline shapes it
  → CEL fills the per-cell formula slots. The two meet only at the data boundary
  (query result → transform input). We invent **no** CEL-in-GraphQL fusion; each
  tool does only what it is individually standard for. The missing GraphQL inline
  standard (#271) is itself the reason a separate transform layer is needed —
  confirming the layering, not contradicting it.

### DuckDB-SQL — strongest transform option, behind a constraint waiver

`duckdb-wasm` (browser) + `duckdb_jdbc` (JVM) is the *same engine + same SQL* both
sides — best-in-class fit-to-residual (`UNNEST` flatten, `WHERE`, `GROUP BY`/`ROLLUP`
+ aggregates + window, `strftime`/`printf`); SQL is the most standard transform
language. **But it breaks two hard constraints:** duckdb-wasm needs `wasm-unsafe-eval`
in CSP, and `duckdb_jdbc` is JNI-native (not bytecode); capability-confinement is
opt-in hardening (extensions off, no `httpfs`/`ATTACH`, statement allow-list).
**Recorded as the strongest transform alternative *pending a written CSP/native-dep
waiver* — not folded in by default.**

### Whole-engine rivals — all rejected (no candidate beats A/A-CEL/A′)

FINOS Perspective (**no JVM binding**; ExprTK TC; `wasm-unsafe-eval`), JSLT
(Java-only), Pkl (single native runtime), json-rules-engine (JS-only), jq
(Turing-complete + WASM CSP), AG-Grid (group/agg is paid Enterprise + no JVM render),
TanStack/SDUI-DivKit (no server renderer), Cube/Grafana/Superset/Hasura (separate
products), Apollo/Relay directives (arbitrary JS resolvers). None is a bounded,
dual-runtime, CSP-clean *whole engine* — confirming the assembled bespoke pipeline.

### What we assemble (each part = "what it solves → what we borrow")

| Prior art | Solves (mostly not ours) | We borrow |
|---|---|---|
| **Vega-Lite** | interactive charts | the **transform pipeline** vocabulary: `flatten`/`filter`/`calculate`/`aggregate` (+`window` v2), `groupby`/`ops`/`fields`/`as` |
| **Adaptive Cards** | cross-host UI / forms | the **templating sublayer**: `$data`/`$root`/`$index`/`$when`, `${}` binding, data/layout split, `registerFunction` extensibility seam |
| **JSONPath (RFC 9535)** | JSON selection (IETF standard) | **downward path** access (hardened, no script-eval) |
| **rapla `FunctionFactory`** | nameformat functions | the **plugin op** registry (`namespace:name(args)`), already server-side; mirrored in TS |
| **JMESPath** | JSON query (formal grammar) | the **compliance-corpus** parity mechanism (`{given, cases:[…]}`) |

### The spec shape (two layers)

1. **`transform: [ {op, …}, … ]`** — ordered pipeline (Vega shape).
2. **`columns: [ {header, value, when?}, … ]`** — bindings (AC `${}` + `$when`).

Both share one bounded expression sublanguage (RFC 9535 path + Vega's operator
allow-list: literals/arithmetic/comparison/logical/ternary/`if`/member-access;
**forbidden**: assignment, `new`, loops, user functions, prototype reach) and one
pure-function library via the `FunctionFactory` registry. **Parent access** uses
AC's `$root` (no path family has a parent operator) — `flatten` projects named
parent fields down onto each row.

| Op | Semantics | Rapla use |
|---|---|---|
| `flatten` | array field → one row per element; **project parent fields onto each row** | reservation → one row per appointment block, carrying event/course/lecturer down |
| `filter` | drop rows by bounded predicate | residual filters not pushed to GraphQL |
| `calculate` | derive a field via bounded expr → `as` | formatted time range, room label |
| `aggregate` | `groupby` + `ops`(`sum`/`count`/`min`/`max`/`mean`/`distinct`) + `fields` + `as` | "pro Tag": group by day, `sum(durationMinutes)` |
| `window` *(v2)* | ordered running calcs (`row_number`, running sum) | deferred |

### Worked examples (dhbw)

**`Termine`** (rows = appointment blocks):
```jsonc
{
  "transform": [
    { "op": "flatten", "field": "$.reservations", "into": "appointments",
      "project": { "eventName": "$root.name",
                   "courseName": "$root.classification.course",
                   "lecturerName": "$root.classification.lecturer" } },
    { "op": "calculate", "as": "timeRange",
      "expr": "concat(formatTime($data.start),'–',formatTime($data.end))" },
    { "op": "calculate", "as": "room",
      "expr": "coalesce($data.allocatables[0].room.number,'—')" }
  ],
  "columns": [
    { "header": "Termin", "value": "$data.eventName" },
    { "header": "Zeit",   "value": "$data.timeRange" },
    { "header": "Raum",   "value": "$data.room" },
    { "header": "Kurs",   "value": "$data.courseName" },
    { "header": "Dozent", "value": "$data.lecturerName",
      "when": "$root.showLecturer == true" }
  ]
}
```

**`Termine pro Tag`** (group + aggregate):
```jsonc
{
  "transform": [
    { "op": "flatten", "field": "$.reservations", "into": "appointments",
      "project": { "eventName": "$root.name" } },
    { "op": "calculate", "as": "day",
      "expr": "formatDate($data.start,'yyyy-MM-dd')" },
    { "op": "calculate", "as": "durationMinutes",
      "expr": "org.rapla.eventtimecalculator:durationMinutes($data.start,$data.end)" },
    { "op": "aggregate", "groupby": ["day"], "ops": ["count","sum"],
      "fields": ["*","durationMinutes"], "as": ["blockCount","totalMinutes"] }
  ],
  "columns": [
    { "header": "Tag",        "value": "$data.day" },
    { "header": "Termine",    "value": "$data.blockCount" },
    { "header": "Dauer (min)","value": "$data.totalMinutes" }
  ]
}
```
`durationMinutes` is supplied by the existing `DurationFunctions` plugin
(`org.rapla.eventtimecalculator`) — a compiled op referenced by name; authors never
inject code.

## XSS / injection hardening (load-bearing)

The saved view is **admin-authored, shared, transferred to every client**, and all
entity data is untrusted. **XSS is conditional on execution** — a string is inert
until parsed as HTML or run as code. The job is to guarantee non-execution:

- **Markup execution → output escaping everywhere.** SPA: Angular text
  interpolation (auto-escaped); **never** `[innerHTML]`/`bypassSecurityTrustHtml`;
  component registry is a safe allowlist (no raw-HTML; `link` = `<a>` text +
  validated `https` href). Server HTML: auto-escaping engine, entity-encode every
  cell. CSV: OWASP formula-injection guard (prefix `= + - @`, tab, CR; quote).
- **Code execution → none by construction.** No `eval`/`new Function`; the
  expression language is total + side-effect-free (Vega deny-list at parse time);
  CSP keeps `script-src` without `unsafe-eval`.
- **Plugin ops are the only Turing-complete surface, and developer-only.** Authors
  reference ops by name; ops are compiled in-reactor code. **Never** register an op
  that itself evaluates author strings (the Vega `scale`-CVE lesson); reserve the
  `org.rapla` namespace so a plugin can't shadow core ops.
- **Validate on save + load**: schema-validate GraphQL + transform; enforce AST
  depth/node caps at parse time; reject unknown ops/functions.
- **Permission boundary stays in GraphQL** — the transform runs on already
  `canRead`-filtered data and cannot widen scope (AGENTS.md §12).

### Prior art — why a bounded language, not editable JS

The view is **admin-authored at runtime** — the same risk class as BI / low-code /
dashboard configuration, **not** the GraphQL `@auth`-directive pattern (those
resolvers are *developer*-written, compiled, reviewed — not runtime-editable, so
they're not this risk). Runtime-authored logic splits into two camps, and the
historical record is one-sided:

| Approach | Real-world examples | Outcome |
|---|---|---|
| Runtime-editable **general** scripting | Elasticsearch Groovy scripts; MongoDB `$where`/`mapReduce` JS; Retool `{{ JS }}` transformers; AG-Grid `valueGetter` expression strings (`new Function()`) | RCE CVE / stored-XSS / perpetual sandbox-hardening |
| Runtime-editable **bounded** expression language | Elasticsearch **Painless**; Kubernetes / Envoy **CEL**; spreadsheet formulas | no `eval`, not Turing-complete, no RCE path |

- **Elasticsearch Groovy → Painless is the textbook precedent.** `CVE-2015-1427`:
  user-supplied Groovy in queries bypassed the sandbox via Java reflection → shell
  execution as the ES process. Elastic's fix was **not** a better sandbox but a new
  **bounded language (Painless)** — general scripting was removed. **A-CEL chooses
  that endpoint up front instead of arriving via a CVE.**
- **Retool shows sandboxing alone doesn't close XSS.** Admin `{{ JS }}` transformers
  run in a sandboxed iframe, yet a transformer that builds an HTML string from
  user-supplied data and renders it is internal stored-XSS to the next operator —
  on a SOC-2/HIPAA-mature platform. The residual risk is the *admin-authored JS
  itself*.
- **AG-Grid `valueGetter` expression strings compile via `new Function()`** — which
  is exactly why AG-Grid is rejected for the render layer (cdk-table instead).

So for admin-authored views there are only two honest options: a bounded language
(CEL — left-to-right of the table's safe column) or sandbox-plus-perpetual-hardening
(Retool, which still ships XSS). CEL is the former by construction. A
GraphQL-directive comparison is a category error: directives are developer code.

## Parity + security strategy (TS ≡ Java)

1. **Single golden corpus** (JMESPath/JSONPath-CTS schema: `{given, cases:[{expr,
   result|results|invalid}]}`), generated into one `corpus.json`, loaded by **both**
   Vitest (rapla-angular, tier 5) and JUnit (rapla-core, tier 1); CI fails on any
   divergence. `results`-array convention for spec-permitted ordering
   non-determinism.
2. **Pin ONE numeric + ONE collation model** — the #1 silent-drift source (JS
   IEEE-754 vs Java BigDecimal/long; JS locale sort vs Java `Collator`, incl. German
   `ä/ö/ü`). Integer-minute durations sidestep most numeric drift. **Needs a written
   decision, not a default.**
3. **Differential fuzzing** — grammar-based generator; both runtimes are each
   other's oracle; promote failing seeds into the corpus.
4. **Consider ABNF code-gen** (APG) of both parsers from one grammar to remove
   parser drift by construction; only the semantic walk stays hand-written + corpus-
   guarded. (Open question — the grammar is tiny.)
5. **Bounds enforced identically** (AST depth, node count at parse, eval step/output
   caps) — an admin spec is still untrusted from a DoS standpoint.

## Authoring (Monaco) — recovers Option A's main weakness

The spec is **JSON**, so **Monaco's built-in JSON-Schema language service gives
autocomplete + validation for free** (publish a JSON Schema). Add **`monaco-graphql`**
for the query pane (GraphiQL is moving to Monaco) and a **small custom completion
provider** for the expression strings (function library + fields derived from the
GraphQL result schema — the query's shape powers the transform's autocomplete).
This closes the "we lose AC's designer" gap cheaply.

## Companion use cases (client-only; share the GraphQL spine)

- **Charts → Vega-Lite.** If a view wants a chart, render it client-side with
  Vega-Lite in **interpreter mode** (`vega-interpreter`, AST-walk, no `unsafe-eval`).
  The **same bounded transform produces the rows**; a table renders them as a grid,
  a chart feeds them into an *encoding-only* Vega-Lite spec (one transform, two
  renderers). No JVM needed (charts are client-only). Server-rendered charts (HTML/
  PDF export) would need a Node sidecar — out of scope unless required.
- **Edit forms → Adaptive Cards → mutations.** AC's actual strength (inputs +
  `Action.Submit`) fits the *write* side: an admin-defined form binds existing data
  (GraphQL query + AC templating, client-side) and submits to a **GraphQL mutation**
  (PRDs 056/057/061/063). No JVM needed (forms are client-rendered; the server only
  runs the mutation, which enforces `canModify`/`canAdmin` per §12/§16). Candidate
  **PRD 075**. Complex types (repeating appointments, allocatable refs) may exceed a
  flat form.

## Dependencies

- **Data-layer gaps — verified against live `rapla-test.dhbw.de` (2026-06-19).** To
  push filtering/computed-values into GraphQL: `Appointment.allocatables` takes **no
  arguments** (so aliased filtered sub-selections aren't expressible); **no
  `durationMinutes`**; **no `typeGroup`** / declared type-groups (PRD 065). Present:
  `AllocatableFilter` (`typeKeyEq`/`typeKeyIn`/`isPersonEq` + per-type `where*`,
  PRD 059) on `Query.allocatables` only. Closing these (PRD 073 + 065) keeps the
  transform thin.
- **Pagination + prev/next + server-side `aggregate`** are future. Client-side
  aggregation covers non-paginated admin tables; once paginated, full-set totals
  move to a server `aggregate` field (Hasura-style).

## Scope

**In:** the (GraphQL document + bounded transform spec) read-table view model;
variable→control inference; the Option A transform engine (TS + Java interpreters,
golden-corpus parity, plugin ops via FunctionFactory); XSS hardening; Monaco
authoring; per-user §12 execution + save-time validation.

**Out:** the rapla DSL / Swing-HTML TableView (deprecated, not migrated); GraphQL
mutations / edit forms (companion / PRD 075); charts beyond the client-only Vega-Lite
note; pagination + server `aggregate` (future); `window` op (v2).

## Plan — phased

1. **Phase 1 — Spec + dual interpreter + renderer.** JSON Schema for the spec;
   TS + Java tree-walk interpreters (`flatten`/`calculate`/`columns`); golden
   corpus in Vitest + JUnit; SPA renders `Termine`. Saved-view config entity (CRUD,
   admin-scoped). §12 via existing resolvers.
2. **Phase 2 — Grouping/aggregation + plugin ops + server export.** `aggregate`
   (`Termine pro Tag`); `FunctionFactory` plugin-op SPI (dual-runtime); Java
   interpreter wired to CSV/HTML export.
3. **Phase 3 — Authoring (Monaco) + presentation directives** (`$when`, component
   registry, JSON-Schema autocomplete + monaco-graphql + expression provider).
4. **Phase 4 — Authoring scope + shared views** (global vs group-admin; personal vs
   shared).
5. **Future — pagination/prev-next + server `aggregate`; `window` op; companion
   charts/forms PRDs.**

## Tests

- **Tier 1/5 — golden corpus** run in JUnit (rapla-core) *and* Vitest
  (rapla-angular): per-op semantics + cross-language parity; `results`-array for
  ordering; named error categories.
- **Differential fuzzing** in CI (both runtimes equal).
- **Tier 3 (MockMvc) §12 leak test** — two users run the same saved view; each sees
  only their readable rows (byte-identical to visible-only subset); the transform
  can't widen scope.
- **Server/client parity** — same spec + result → identical rows in Java and TS
  (locks SPA/CSV/HTML equivalence).
- **Save-time validation** — invalid GraphQL / unknown op / over-deep AST rejected.

## Open questions

1. **Parser strategy** — hand-written recursive-descent pair vs ABNF code-gen (APG).
   Tiny grammar; decide before writing either.
2. **Number + collation model** — decimal/rounding for `sum`/`mean`; sort order
   (byte vs locale; German `ä/ö/ü`). Highest drift risk; written decision required.
3. **Flatten parent-projection ergonomics** — explicit `project:{}` (leak-safe) vs
   auto-carry parent scalars under `$parent.`. Lean explicit.
4. **`window` op** — defer to v2 (the two dhbw tables don't need it).
5. **Plugin-op parity SPI** — per-op shared descriptor (name/namespace/arity/return)
   + conformance gate so the Java and TS ops can't diverge or go missing.
6. **`specVersion` + migration** — stored shared specs need a version + compat policy
   from day one.
7. **Server consumers** — confirm every server-rendered consumer (CSV, HTML, iCal)
   so parity covers the paths actually evaluated on the JVM.
8. **Live-preview tooling** — v1 (validator + SPA preview) vs v2.
9. **Engine: A vs A′ — gated on a GraalJS spike.** JSONata + raw JS are out. The
   open call is **A** (own bounded spec — security floor) vs **A′** (real AC/Vega
   syntax + libs on the client, GraalJS on the server — standard, same-code parity).
   Run the 1–2 day GraalJS feasibility spike (does `vega` + `adaptivecards-templating`
   evaluate under a GraalJS Context, no Node built-ins, interpreter mode) before
   committing. A′ if it's clean; A is the fallback.
