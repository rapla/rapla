---
status: "accepted"
date: 2026-06-24
decision-makers: Christopher Kohlhaas
consulted: spec-driven-development research workflow (2026-06-24)
informed: future contributors, AI coding agents
---

# Adopt no SDD framework as a tool; plunder OpenSpec's ideas as thin scripts

## Context and Problem Statement

rapla already practices spec-driven development through 90 PRDs, 15 architecture docs, the §2a
"where knowledge lives" routing, `D1..Dn` decision locks, and `[since:]`/`[watch:]` staleness tags.
The question is whether to adopt an off-the-shelf spec-driven-development (SDD) framework —
OpenSpec, GitHub Spec Kit, AWS Kiro, Tessl, BMAD — to formalize this, or to build a thin machine
layer on top of the existing discipline.

Related: [PRD 088 — spec-graph formalization](../prd/088-spec-graph-formalization.md) (this MADR is
the extracted, durable form of that PRD's decision **D1**).

## Decision Drivers

- Don't trade a working, rapla-tuned discipline for a generic template.
- Must respect the §2a four-store split (PRD = decision, arch-doc = domain truth, MEMORY = volatile,
  AGENTS = rules) and the `git mv prd/ → done/` lifecycle.
- Solo maintainer + AI agents; CLI/Claude-Code-centric; low ceremony.
- Brownfield, ~189k LOC mature Java — not greenfield.

## Considered Options

- **OpenSpec** — Node-only, brownfield SDD: `specs/` (durable) + `changes/` (deltas) + `archive`
- **GitHub Spec Kit** — 5-phase gate (constitution→specify→plan→tasks→implement), Python CLI
- **AWS Kiro** — agentic IDE, EARS requirements, steering files
- **Tessl** — spec-as-single-source-of-truth, code regenerated from spec
- **BMAD** — agent personas (analyst/PM/architect/dev), PRD→story shards
- **No framework** — plunder the best ideas as thin scripts over the existing PRDs

## Decision Outcome

Chosen option: **No framework as a tool; reimplement OpenSpec's ideas as thin scripts.** OpenSpec is
the closest fit (brownfield, Node-only, PRD↔proposal / `done/`↔archive mapping), but adopting its
`changes/`+`archive` machinery would duplicate or break rapla's `git mv` lifecycle and the §2a split.
We take its *ideas* — delta specs, day-one `validate`, status-as-enum, the change↔capability
separation — as our own lint scripts and conventions. A big-bang framework import would trade a
working discipline for a generic template that cannot represent rapla's free-prose treatises (e.g.
the 668-line [PRD 082](../prd/082-storage-memory-model.md)).

### Consequences

- Good, because the existing PRD discipline, §2a split, and `git mv` lifecycle are preserved.
- Good, because we adopt no external runtime dependency or tool lock-in.
- Bad, because we must build (and maintain) our own lint/graph scripts rather than inherit them —
  accepted, because they are small and match the `ApiPrefixArchitectureTest` pattern.

### Confirmation

No `openspec/`, `.specify/`, `.kiro/`, or equivalent framework scaffolding appears in the repo;
[PRD 088](../prd/088-spec-graph-formalization.md)'s spec-lint (Phase 0) is the home-grown machine layer instead. Reviewed at [PRD 088](../prd/088-spec-graph-formalization.md) close.

## Pros and Cons of the Options

### OpenSpec

- Good, because brownfield-focused, Node-only, maps almost 1:1 onto PRD↔proposal / `done/`↔archive.
- Good, because `validate` gives structural checks on day one.
- Bad, because its `changes/`+`archive` merge would duplicate/break the `git mv` lifecycle.
- Bad, because `### Requirement: SHALL` schema cannot hold rapla's free-prose treatises.
- Bad, because it archives `design.md` with the change, losing rationale (see ADR-0001).

### GitHub Spec Kit

- Good, because `/speckit.analyze` + `converge` (cross-artifact / code-vs-spec drift) target a real gap.
- Bad, because rigid phase-gates and branch-per-spec are too ceremonious for a solo maintainer.
- Bad, because greenfield-oriented; Python dependency; duplicates AGENTS.md + Plan-Mode.

### AWS Kiro

- Good, because steering files ≈ AGENTS.md; clean requirements→design→tasks staging.
- Bad, because commercial IDE lock-in, AWS-oriented; ignores rapla's CLI/skills ecosystem.

### Tessl

- Good, because the purest "spec-as-source" vision.
- Bad, because private beta, non-deterministic regeneration, 1:1 spec↔file impossible at 189k LOC.

### BMAD

- Good, because role discipline + story sharding for context efficiency.
- Bad, because it simulates a team rapla isn't; persona theater is anti-value for one maintainer.

### No framework (chosen)

- Good, because preserves the working discipline and adds only what a CI check can enforce.
- Bad, because requires building our own thin scripts.

## Future possibilities

- Revisit Spec Kit's `converge`/`analyze` *as an idea* for a code-vs-spec drift check, home-grown.
- If the spec graph ([PRD 088](../prd/088-spec-graph-formalization.md) Phase 2) outgrows Markdown + lint, reconsider a graph store then — not now.

## More Information

- Full framework-fit table + rationale: [PRD 088](../prd/088-spec-graph-formalization.md) §2 and D1.
- Background: 2026-06-24 multi-agent research workflow comparing the five frameworks.
- Related: [ADR-0001](0001-use-madr-for-architecture-decisions.md) (the durable-why layer this implies).
