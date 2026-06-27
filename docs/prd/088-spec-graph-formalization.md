# PRD 088 — Spec-as-source formalization & spec graph

**Status:** draft — 2026-06-24
**Related:** PRD 049 (controller-interface dedup — `@HttpExchange` interfaces are already the REST contract), PRD 035 (GraphQL foundations — `schema.graphqls` is already a boot-enforced contract), PRD 067 (server mutation unification — example of a heavily cross-referenced PRD cluster), PRD 082–087 (storage modernization cluster — the 082→083/085/086/087 decomposition is the canonical example of an implicit parent-of/depends-on spec graph in prose)

## Abstract

rapla already practices spec-driven development — 90 PRDs (`docs/prd/` + `done/`), 15 architecture docs,
the AGENTS.md §2a "where knowledge lives" routing table, the D1..Dn decision-locks, and the
`[since:]`/`[watch:]` staleness tags are exactly the artifact stack that OpenSpec / Spec Kit / Kiro
impose after the fact. The gap is **not** missing specs — it is the missing **machine layer**: the
1.426 prose `PRD NNN` references, the cross-doc links, and the `[[wikilinks]]` form an *implicit* spec
graph that nothing validates, materializes, or makes navigable. This PRD formalizes that graph as a
lightweight, git-versioned, CI-enforced layer **on top of** the existing PRD system (additive, not a
replacement), and establishes "spec as source" for rapla at the realistic target: **spec-as-review-gate
+ durable contract layer**, where executable conformance (arch-tests, contract-tests, boot-enforced
schemas) — not code generation — is the enforcement engine. Measurable end state: a CI gate fails on
any dangling spec reference, the README index regenerates from front-matter, and a navigable spec graph
exists for impact analysis and GraphRAG retrieval.

## Implementation

Background evaluation (2026-06-24, multi-agent research workflow) compared OpenSpec, GitHub Spec Kit,
AWS Kiro, Tessl, and BMAD against rapla's existing discipline. Full cited evidence base:
[`docs/research/spec-driven-development-findings.md`](../research/spec-driven-development-findings.md). Conclusion: **no framework is adopted as
a tool**; OpenSpec's *ideas* (delta-specs, `validate` on day one, status-as-enum, the change↔capability
distinction) are reimplemented as thin scripts over the existing PRDs. Rationale and the full
framework-fit table live in D1–D3 below.

Key technical seams:
- **PRD front-matter.** Each `docs/prd/NNN-*.md` gains a YAML front-matter block carrying machine-readable
  edges. The 1.426 prose `PRD NNN` references and the `// PRD NNN` source-comment convention (≈340 Java
  files, 64 distinct PRD numbers) are the raw material — an extraction script *proposes* edges, a human
  confirms. Front-matter is the single source for the README index (today ~40 PRDs stale) and the graph.
- **Lint script (CI gate).** A Node/Python script in CI that validates every `PRD NNN` reference and every
  typed edge against existing files; reports orphan PRDs (no `implemented-by`), `superseded-by` targets
  still referenced as live, and diffs the README index against reality. Same shape as
  `ApiPrefixArchitectureTest` — a mechanical conformance check, not more prose. Generalizes the existing
  manual `[watch:]` convention to machine-enforced.
- **Conformance, not generation.** "Spec as source" here means the spec is the durable *intent + contract*
  layer; conformance is proven by arch-tests / contract-tests / boot-enforced schemas. `@HttpExchange`
  interfaces (PRD 049) and `schema.graphqls` (PRD 035) are the model: the interface/schema *is* the
  machine-readable contract, the test enforces it, the code stays the artifact. No code is generated from
  prose.
- **Capability specs (new node type).** rapla today has only *change* specs (PRDs). The brownfield work is
  to extract *capability* specs (durable "current contract" docs) for stable, well-bounded subsystems —
  starting where a half-formalized contract already exists (REST interfaces, GraphQL schema, permissions),
  explicitly **not** storage (in flux, 082–087 draft) or Swing (dying legacy, PRD 023/024).

## Goal

Measurable end state from the outside:
- `mvn`/CI runs a spec-lint that **fails red** on any dangling `PRD NNN` reference or typed edge, and is
  currently red against the known defects (PRD 006/021/046 dangling refs, the duplicate `031-*`, the
  stale README index claiming "46 PRDs" vs the real 90).
- `docs/prd/README.md` is **generated** from PRD front-matter, not hand-maintained.
- A spec-graph export (Mermaid/Graphviz) exists and is regenerable from front-matter; an agent can answer
  "which specs does a change to file X touch?" by traversing `implemented-by` → PRD → `depends-on`/`refines`.
- At least one subsystem (REST endpoint catalog) has a generated-not-hand-maintained capability spec backed
  by per-endpoint contract tests.

## Scope

### In scope
- YAML front-matter schema for PRDs (`id`, `status` enum, `related`, `depends-on`, `refines`, `supersedes`,
  `parent-of`, `implemented-by`, `specified-by`).
- A `status` enum (`draft | in-progress | done | superseded`) normalized across all PRD headers (today ~40
  free-form variants).
- A CI spec-lint script (dangling-ref + orphan + README-drift checks).
- README index generated from front-matter.
- An extraction script that *proposes* edges from the 1.426 prose refs + `// PRD NNN` comments (human-confirmed).
- A Mermaid/Graphviz graph export; optional Obsidian graph-view via the existing `[[wikilinks]]`.
- One brownfield capability-spec pilot: the REST endpoint catalog (generate `rest-api.md` from `@HttpExchange`
  interfaces + per-endpoint contract tests).

### Out of scope
- Adopting OpenSpec / Spec Kit / Kiro / Tessl / BMAD as a tool (D1).
- Code generation from specs / spec-as-single-source-of-truth (D2) — unrealistic at ~189k LOC mature Java.
- Heavy traceability tooling: ReqIF / DOORS / Polarion, and Neo4j / Backstage at the start (D3).
- Capability-spec extraction for the storage engine (082–087 in flux) and the Swing client (dying legacy).
- Any change to the existing `git mv prd/ → done/` lifecycle or the §2a four-store knowledge split.

## Plan

### Phase 0 — Linkability + CI dangling-check (smallest step, immediate value)
- [ ] Define the `status` enum and normalize it across all PRD headers.
- [ ] Define the YAML front-matter schema for PRDs (`id`, `status`, `related`, `supersedes`, `depends-on`, `parent-of`).
- [ ] Write the spec-lint script: validate every `PRD NNN` reference + typed edge against existing files; fail CI on dangling.
- [ ] Regenerate `docs/prd/README.md` from front-matter (replaces the ~40-PRDs-stale hand index).
- **Deliverable:** a CI gate on spec integrity; PRD 006/021/046 and the `031` collision become visible/fixed; README self-updating. Throws nothing away, changes no flow.

### Phase 1 — Change-flow pilot on ONE new feature
- [ ] Run the next real new feature/refactor in the OpenSpec *style*: proposal → delta-spec → tasks checklist → implement → archive — but with rapla's own files (PRD + `git mv`), not a foreign tool.
- [ ] Test dual-verification: check each generation against *spec AND* the existing test suite (catches unwanted feature additions — the misaligned-derived-spec regression trap).
- **Deliverable:** a PRD that proves (or disproves) the formalized flow is lighter than today's — a go/no-go data point before further investment.

### Phase 2 — Materialize + visualize the spec graph
- [ ] Extraction script: 1.426 prose refs + `// PRD NNN` comments → proposed `related`/`implemented-by` edges; human-confirm.
- [ ] Populate the `implemented-by` reverse index per PRD.
- [ ] Mermaid/Graphviz export + (optional) Obsidian graph-view.
- **Deliverable:** a navigable spec graph; an agent can run impact analysis ("what does this change touch?").

### Phase 3 — Brownfield capability-spec extraction for ONE subsystem
- [ ] REST endpoint catalog: generate `rest-api.md` from `@HttpExchange` interfaces instead of maintaining it narratively; per-endpoint contract tests; create the `capability` node.
- **Deliverable:** one subsystem with a generated-not-hand-maintained spec + executable conformance — a blueprint for GraphQL/permissions next.

## Tests

- **Phase 0:** the spec-lint script itself, run as a CI step (and locally). Verify it goes red against the
  known defects (PRD 006/021/046, duplicate `031`) and green after they are fixed. Treat the script like
  an arch-test (cf. `ApiPrefixArchitectureTest`).
- **Phase 3:** per-endpoint contract tests in `rapla-app` (tier-3 MockMvc) that assert the generated
  `rest-api.md` matches the live `@HttpExchange` routing; the OpenAPI spec-capture command as a diff gate.

## Open Questions

- **OQ1** — front-matter format: pure YAML front-matter in each `.md`, or a sidecar `docs/prd/graph.toml`?
  *Resolution:* pending — lean YAML front-matter (keeps the edge next to the prose, survives `git mv`).
- **OQ2** — lint script language: Node (matches the OpenSpec/`lychee` ecosystem and rapla-angular tooling)
  or Python (matches existing bulk-refactor scripts)? *Resolution:* pending.
- **OQ3** — does the spec-lint run inside the Maven reactor (an arch-test in `rapla-app`) or as a standalone
  CI step on the docs tree? *Resolution:* pending — standalone is lighter, but an arch-test gets it into
  the existing `mvn test` signal. Lean arch-test for parity with `ApiPrefixArchitectureTest`.
- **OQ4** — do `capability` specs live under `docs/prd/` (new lifecycle state) or a new `docs/spec/` tree?
  *Resolution:* pending — decide during Phase 3; keep them out of `docs/prd/` if they prove to be durable
  (not change-shaped).

## Decisions locked

**D1 — Adopt no SDD framework as a tool; reimplement OpenSpec's ideas as thin scripts over the existing PRDs.**
The 2026-06-24 research workflow scored OpenSpec, Spec Kit, Kiro, Tessl, BMAD against rapla's discipline.
OpenSpec is the only one whose model maps onto rapla (brownfield focus, Node-only, PRD↔proposal /
`done/`↔archive) — but adopting its `changes/`+`archive` machinery would duplicate or break rapla's
`git mv prd/ → done/` lifecycle and the §2a four-store split (PRD = decision, arch-doc = domain truth,
MEMORY = volatile, AGENTS = rules). Spec Kit (rigid phase-gates, Python, greenfield), Kiro (commercial
IDE lock-in), Tessl (private beta, non-deterministic regeneration), and BMAD (persona theater for a solo
maintainer) are worse fits. We plunder OpenSpec's *ideas* (delta-specs, day-one `validate`, status-as-enum,
change↔capability separation), not its tooling. A big-bang framework import would trade a working,
rapla-tuned discipline for a generic template that cannot represent rapla's free-prose treatises (e.g. the
668-line PRD 082).

**D2 — Target state is spec-as-review-gate + durable contract layer, NOT spec-as-source-of-truth with code generation.**
On the spectrum spec-as-doc → review-gate → generator → single-source-of-truth, rapla targets the
middle-right. Generating code from prose at ~189k LOC of mature Java (Swing, RxJava, JDBC operators,
GraphQL fetchers) is unrealistic — drift and non-determinism are unsolved, and the "if your spec is
detailed enough to run without a human, you just wrote a program" paradox applies in full. rapla's real
conformance engine is its arch-tests (`ApiPrefixArchitectureTest` enforces the §15 `/api/` allow-list),
i.e. already "executable spec" done right. "Spec as source" for rapla means: the spec is the durable
*intent + contract* layer over the tests; the test is the conformance engine; the code stays the artifact.

**D3 — Lightweight path only: YAML front-matter + a CI lint script + a Mermaid export. No heavy tooling.**
Reject ReqIF/DOORS/Polarion (safety-critical heavyweight, GUI-centric, not git/agent-native) and Neo4j/
Backstage at the start (only worth it once the graph is stable and large enough for Cypher-GraphRAG). The
lightweight path matches rapla culture: docs-as-code, git-versioned, enforced by a lint script — the same
form as `ApiPrefixArchitectureTest`. The implicit graph already exists (1.426 `PRD NNN` refs, the
082→083/085/086/087 decomposition, the MEMORY.md `[[wikilinks]]`, the arch-doc "See also" footers); the
job is to lift existing prose into typed edges, not to build a graph from scratch.

**D4 — Brownfield capability-spec extraction starts with REST/GraphQL/permissions; explicitly not storage or Swing.**
First targets are subsystems that already have a half-formalized, machine-checkable contract: the
`@HttpExchange` REST interfaces (PRD 049, plus `rest-api.md`, the OpenAPI capture command, and
`ApiPrefixArchitectureTest`), the boot-enforced `schema.graphqls` (PRD 035), and permissions
(`permissions.md`, backed by §12 MockMvc leak-tests). Avoid the storage engine (the largest undocumented
core, but in flux — PRDs 082–087 all draft, `readmodel/` only a skeleton) and the Swing client (77k LOC,
least specified, but dying legacy being rolled back toward Angular via PRD 023/024 → low ROI).

**D5 — This PRD is dogfooded: it is the first node authored under its own front-matter schema once Phase 0 lands.**
PRD 088 becomes the worked example of a capability/change spec carrying machine-readable edges, validated by
its own lint script.
