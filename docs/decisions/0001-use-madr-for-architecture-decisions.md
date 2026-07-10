---
status: "accepted"
date: 2026-06-24
decision-makers: Christopher Kohlhaas
consulted: spec-driven-development research workflow (2026-06-24)
informed: future contributors, AI coding agents
---

# Use MADR for durable architecture decisions, separate from PRDs

## Context and Problem Statement

rapla records decisions inline in PRDs as `D1..Dn` "locked" blocks. When a PRD ships it is
`git mv`'d to `docs/prd/done/`, and the decision rationale — including the rejected alternatives —
goes with it. In practice nobody reads `done/` PRDs when planning new work, so that rationale
becomes *invisible to future proposals* and trade-offs get re-litigated. The PRD is a **change
spec** (a delta: "build X"); it is the wrong home for a **durable decision record** ("why is it
this way, what did we reject, what could come later"). How do we keep weighed alternatives and
future ideas readable forever without polluting either the PRD or the living architecture docs?

Related: [PRD 088 — spec-graph formalization](../prd/088-spec-graph-formalization.md) (the
three-artifact model), AGENTS.md §2a ("where knowledge lives").

## Decision Drivers

- Decision rationale + **rejected alternatives** must stay readable after the change ships.
- Must not fight the existing PRD lifecycle or the §2a four-store split — additive only.
- docs-as-code, git-versioned, agent-readable; no heavy tooling, no external service.
- A decision should bind to an *enforceable check*, not just be prose.
- A sanctioned place for **future ideas** that carries no commitment.

## Considered Options

- Keep `D1..Dn` locks inside PRDs only (status quo)
- MADR (Markdown Any Decision Records) in a separate `docs/decisions/` tree
- Nygard-style ADRs (Context / Decision / Consequences only)
- Heavy traceability tooling (ReqIF / DOORS / a decision database)

## Decision Outcome

Chosen option: **MADR in `docs/decisions/`**, because it is the richest lightweight format for
*weighed* alternatives (per-option Pros and Cons), it adds a `Confirmation` section that binds each
decision to an enforceable check — matching rapla's arch-test culture — and it lives as plain
git-versioned Markdown that agents already read. PRDs keep their change-scope and their `D1..Dn`
locks for in-flight work; once a decision is durable and cross-cutting, it is **extracted into a
MADR** and the PRD links to it. MADRs are append-only: a decision is never rewritten, only
superseded by a new MADR (status flips to `superseded by ADR-NNNN`, with reciprocal links).

This is the durable "why" layer that OpenSpec's `design.md` loses on archive (see More Information);
MADR complements the change→capability spec flow rather than competing with it.

### Consequences

- Good, because rejected alternatives + rationale survive the PRD's move to `done/`.
- Good, because the `Future possibilities` section gives future ideas a home outside the contract.
- Good, because it dovetails with [PRD 088](../prd/088-spec-graph-formalization.md)'s spec graph — MADRs are `decision` nodes with
  `supersedes`/`superseded-by` and `refines` edges.
- Bad, because there are now two decision homes during the transition (PRD `D-locks` + MADRs)
  until extraction catches up — mitigated by extracting only durable, cross-cutting decisions.

### Confirmation

A spec-lint check ([PRD 088](../prd/088-spec-graph-formalization.md) Phase 0) validates every MADR: required sections present
(`Context and Problem Statement`, `Considered Options`, `Decision Outcome`), a valid `status`,
and no dangling `ADR-NNNN` / PRD cross-references. Runs in CI alongside the PRD dangling-link check.

## Pros and Cons of the Options

### Keep `D1..Dn` locks inside PRDs only (status quo)

- Good, because zero new structure; decisions sit next to the change that made them.
- Bad, because the rationale is archived to `done/` and becomes invisible to future proposals
  (the exact failure mode this MADR exists to fix).
- Bad, because a decision spanning several PRDs has no single durable home.

### MADR in `docs/decisions/`

- Good, because `Considered Options` + per-option `Pros and Cons` make rejected alternatives durable.
- Good, because `Confirmation` binds the decision to an enforceable check.
- Good, because immutable + superseded-by links give a navigable decision chain.
- Neutral, because it introduces a new tree, but `docs/decisions/` is the MADR-standard location.
- Bad, because it needs discipline to extract decisions out of PRDs rather than leaving them inline.

### Nygard-style ADRs (Context / Decision / Consequences)

- Good, because even lighter than MADR.
- Bad, because there is **no dedicated section for weighed alternatives** — the rationale lives
  diffusely in Context/Consequences, which is precisely what we need to be first-class here.

### Heavy traceability tooling (ReqIF / DOORS / decision DB)

- Good, because formal linking and queries.
- Bad, because GUI-centric, not git/agent-native, massive overhead for a solo maintainer — rejected
  in [PRD 088](../prd/088-spec-graph-formalization.md) D3 for the same reasons.

## Future possibilities

- A `log4brains`-style static site over `docs/decisions/` for backlinks + a decision timeline/graph.
- Auto-extract `D1..Dn` blocks from existing PRDs into seed MADRs (semi-automatic, human-confirmed).
- A `Supersedes`/`Superseded-by` lint that flags a MADR still referenced as live after supersession.

## More Information

- MADR template + spec: https://adr.github.io/madr/ — copied to [`adr-template.md`](adr-template.md).
- The OpenSpec gap this addresses: in OpenSpec, `design.md` is archived with the change, so its
  rationale "becomes invisible to future proposals" — the community fix is to keep ADRs as a separate
  durable tree. https://intent-driven.dev/blog/2026/04/29/spec-driven-development-with-adr/
- Forward-looking sections modelled on the Rust RFC template (`Rationale and alternatives` vs
  `Unresolved questions` vs `Future possibilities`): https://github.com/rust-lang/rfcs/blob/master/0000-template.md
- Extracted from / supersedes the inline reasoning that motivated [PRD 088](../prd/088-spec-graph-formalization.md).
