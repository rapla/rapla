# PRD 088 — Spec-as-source formalization, spec graph & AI-maintained docs

**Status:** draft — 2026-06-24 (condensed + D6/D7 added 2026-06-27)
**Related:** [PRD 049](049-controller-interface-deduplication.md) (`@HttpExchange` = REST contract), [PRD 035](done/035-graphql-foundations.md) (`schema.graphqls` = boot-enforced contract),
[PRD 022](022-architecture-documentation.md) (architecture reference docs), [PRD 067](067-server-mutation-unification.md), [PRD 082](082-storage-memory-model.md)–087.
ADRs: [0001](../decisions/0001-use-madr-for-architecture-decisions.md) (MADR),
[0002](../decisions/0002-no-sdd-framework-as-tool.md) (no SDD tool = D1),
[0004](../decisions/0004-ai-maintained-docs-conversational-diff.md) (AI-maintained docs = D7).

## Summary

rapla already practices spec-driven development — 90 PRDs, 15 architecture docs, the §2a "where knowledge
lives" routing, `D1..Dn` locks, `[since:]`/`[watch:]` tags — the artifact stack OpenSpec / Spec Kit / Kiro
impose after the fact. The gap is **not** missing specs. Two real gaps remain:

1. **Machine layer (spec graph).** 1,426 prose `PRD NNN` refs + `// PRD NNN` comments (~340 Java files) +
   `[[wikilinks]]` form an *implicit* graph nothing validates or makes navigable. Formalize it as a
   lightweight, git-versioned, CI-enforced layer **on top of** the PRDs (additive). Target:
   **spec-as-review-gate + durable contract**, where executable conformance (arch-tests, contract-tests,
   boot-enforced schemas) — not code generation — is the engine (D2).
2. **Docs (architecture / admin / developer).** PRDs are change-shaped; the current-state docs for these
   audiences drift (the README index ran ~40 PRDs stale). They are **AI-maintained by conversational targeted
   diff** (D7), not regenerated. End-user *usage* docs are excluded — not derivable from build-time PRDs.

No framework is adopted as a tool (D1, [ADR 0002](../decisions/0002-no-sdd-framework-as-tool.md)); OpenSpec's
*ideas* (delta-specs, day-one `validate`, status-enum, change↔capability split) are reimplemented as thin
scripts + skills. Full framework-fit evidence:
[research findings](../research/spec-driven-development-findings.md).

## Goal (measurable end state)

- CI spec-lint **fails red** on any dangling `PRD NNN` ref or typed edge — currently red against known defects
  (PRD 006/021/046 dangling, duplicate `031-*`, README claiming "46 PRDs" vs the real 90).
- `docs/prd/README.md` **generated** from PRD front-matter, not hand-maintained.
- A regenerable spec-graph export (Mermaid/Graphviz); an agent answers "which specs does a change to file X
  touch?" by traversing `implemented-by → PRD → depends-on/refines`.
- ≥1 subsystem (REST endpoint catalog) has a generated-not-hand-maintained capability spec backed by contract tests.
- Docs stay current via the D7 announce→targeted-edit workflow + an optional advisory drift-check.

## Plan

**Phase 0 — Linkability + CI dangling-check.** Define the `status` enum + YAML front-matter
(`id, status, related, depends-on, refines, supersedes, parent-of, implemented-by`); write the spec-lint
(dangling-ref + orphan + README-drift); regenerate the README. *Deliverable:* a CI gate on spec integrity;
the known defects become visible/fixed. Throws nothing away, changes no flow.

**Phase 1 — Doc-maintenance dogfood (D7).**
[ADR 0004](../decisions/0004-ai-maintained-docs-conversational-diff.md) is the first artifact authored via the
announce→ja, targeted-diff workflow. Then run the workflow on one landed PRD against its `architecture/*.md`.
*Deliverable:* a go/no-go feel for "announce → yes" before wiring a skill.

**Phase 2 — Materialize the spec graph.** Extraction script: prose refs + `// PRD NNN` comments → proposed
`related`/`implemented-by` edges (human-confirmed); `implemented-by` reverse index; Mermaid/Graphviz export.
*Deliverable:* a navigable graph + impact analysis.

**Phase 3 — Brownfield capability-spec pilot.** Generate `rest-api.md` from `@HttpExchange` interfaces +
per-endpoint contract tests; create the `capability` node. *Deliverable:* one subsystem with a
generated-not-hand-maintained spec + executable conformance — a blueprint for GraphQL/permissions next.

## Open Questions

- **OQ1** — front-matter format: YAML-in-`.md` vs sidecar `graph.toml`. *Lean YAML* (edge next to prose,
  survives `git mv`).
- **OQ2** — lint script language: Node vs Python. *Pending.*
- **OQ3** — lint home: Maven arch-test vs standalone CI step. *Lean arch-test* (parity with
  `ApiPrefixArchitectureTest`).
- **OQ4** — where, and under what name, do living `capability` + `change` specs live? *Widened 2026-06-27:*
  the maintainer is no longer attached to the `docs/prd/` + `done/` location or naming (D6), so the spec
  store's location and name are open, and the "no change to the git mv prd→done lifecycle" scope line is
  reopened. Decide before the Phase 3 pilot; keep capability specs out of `docs/prd/` if they prove durable
  (not change-shaped).

## Decisions locked

**D1 — No SDD framework as a tool; reimplement OpenSpec's ideas as thin scripts.** OpenSpec is the closest fit
(brownfield, Node-only, PRD↔proposal / `done/`↔archive) but its `changes/`+`archive` merge would break the
`git mv` lifecycle and the §2a four-store split. Full rationale + framework-fit table →
**[ADR 0002](../decisions/0002-no-sdd-framework-as-tool.md)**.

**D2 — Target = spec-as-review-gate + durable contract, NOT code generation from specs.** At ~189k LOC of
mature Java, generating code from prose is unrealistic (the "spec detailed enough to run = a program"
paradox). rapla's conformance engine is its arch-tests (`ApiPrefixArchitectureTest`); the spec is the durable
intent+contract layer over the tests; the code stays the artifact.

**D3 — Lightweight only: YAML front-matter + CI lint + Mermaid export.** Reject ReqIF/DOORS/Polarion and
Neo4j/Backstage at the start. The implicit graph already exists in prose; the job is to lift it into typed
edges, not build one from scratch. Matches the docs-as-code, git-versioned culture.

**D4 — Capability-spec extraction starts with REST/GraphQL/permissions; not storage or Swing.** First targets
already have a half-formalized, machine-checkable contract (`@HttpExchange` [PRD 049](049-controller-interface-deduplication.md), `schema.graphqls`
[PRD 035](done/035-graphql-foundations.md), `permissions.md` + §12 leak-tests). Avoid the storage engine (in flux — 082–087 draft) and the
Swing client (dying legacy, low ROI).

**D5 — Dogfooded:** PRD 088 is the first node authored under its own front-matter schema once Phase 0 lands.

**D6 — The objection to the OpenSpec CLI is its rigidity, not its binary; adopt self-authored,
format-*adjacent* skills (2026-06-27).** Verified empirically (`npx @fission-ai/openspec@1.4.1 init`): the
official Claude-Code skills it generates are **thin CLI wrappers** (frontmatter `compatibility: Requires
openspec CLI`; every body delegates to `openspec new change` / `status` / `instructions --json`) — they
cannot satisfy a no-CLI requirement, and the spec location *is* relocatable (`.openspec.yaml` planning-home,
`changesDir`), so neither the binary nor the location was the blocker. The real blocker: **the automatic
delta-merge on `archive` plus the fixed artifact schema rob flexibility and force a rigid structure** that
cannot hold rapla's free-prose treatises (668-line [PRD 082](082-storage-memory-model.md)), inline **code examples** (AI implementation aid),
or **PlantUML** (human architecture). Because the CLI's payoff (`validate`, graph, typed instructions)
*derives from* that rigidity, format-conformance is self-defeating and dissolves the earlier "lock-out" worry.
**Direction:** implement the lifecycle idea as rapla-owned, self-contained skills with an **agent-judgment
merge**; stay **format-adjacent** — reuse the `## ADDED / MODIFIED / REMOVED Requirements` markers and the
`Requirement:` + `#### Scenario:` vocabulary so a future export to canonical OpenSpec stays mechanical — but
not conformant. External readability evidence (Böckeler "I'd rather review code than all these markdown
files"; the MDD inflexibility paradox; intent-driven.dev "unreadable code dumps") is collected in
**[ADR 0004](../decisions/0004-ai-maintained-docs-conversational-diff.md)**.

**D7 — Docs are AI-maintained by conversational targeted diff, not regenerated (2026-06-27).**
The D6 flexibility logic applied to the documentation layer. The machine-generated reference
layer (OpenAPI/schema/endpoint catalog) is a vanishing fraction at rapla — mentioned inline, not a structured
pillar — so the model collapses to **one track**: AI-authored prose for the architecture/admin/developer
audiences, maintained by the agent **announcing the intended change** ("PRD Z makes §Y of
`architecture/foo.md` stale — I'd change it to …"), the human **discussing or saying yes**, and the edit
**targeted to the affected section, never a wholesale page rewrite**. Two guardrails — correctness, not
ceremony: **announce-before-apply** (no silent wholesale change → preserves human-curated context/warnings)
and **targeted-not-wholesale** (AI regeneration otherwise strips curation — the documented central failure
mode). An optional non-blocking **advisory drift-check** ("these sections may be stale") replaces the heavy
CI-checkbox-PR bot. Extracted to **[ADR 0004](../decisions/0004-ai-maintained-docs-conversational-diff.md)**,
whose own creation is the first dogfood.

## Tests

- **Phase 0:** the spec-lint as a CI step — red against the known defects (PRD 006/021/046, duplicate `031`),
  green after they are fixed. Treated like an arch-test (cf. `ApiPrefixArchitectureTest`).
- **Phase 3:** per-endpoint contract tests (tier-3 MockMvc) asserting the generated `rest-api.md` matches the
  live `@HttpExchange` routing; the OpenAPI spec-capture command as a diff gate.
