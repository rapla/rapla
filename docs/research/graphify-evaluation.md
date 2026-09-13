# graphify evaluation — code knowledge graph for agent navigation (2026-09-12/13)

**Status:** closed — not adopted. Artefacts removed 2026-09-13. Revisit only if a
graphify release fixes member-call name collisions (upstream issue 598) *and* pull
requests start arriving on rapla (hosted graph-aware review).

## Question

Can [graphify](https://github.com/Graphify-Labs/graphify) (Graphify Labs, OSS CLI
`graphifyy`, Apache-2.0) shrink the context an agent needs to navigate rapla, and is
it useful for the docs vault or the maintainer's private Obsidian vault?

## Setup

- graphify 0.9.60 in a private venv, pinned; `graphify install` deliberately **not**
  run (it overwrites `CLAUDE.md` = `@AGENTS.md` and writes a hook outside
  `.agents/settings.json`).
- `graphify extract . --code-only`: 40 s, 1,831 files → 21,521 nodes, 81,334 edges,
  431 communities, `graph.json` 55 MB. Communities named via
  `graphify label --backend=claude-cli` (92 s).
- Two A/B runs, each two fresh Sonnet subagents with identical ~72k-token start
  context; the graph agent additionally got the CLI, the bundled skill's query rules
  and "trust EXTRACTED, verify INFERRED". Token counts are whole-session usage.

## Results

| Run | Questions | grep baseline | graph agent |
|---|---|---|---|
| A/B 1 | 3 caller / implementation lookups (Java) | 85k tokens · 1 call · 42 s · all correct | 127k · 25 calls · 205 s · false positives, fell back to grep, still missed 3 of 8 callers |
| A/B 2 | SPA save → storage incl. permission checks; SPA command/undo map | 104k · 19 calls · 59 s · complete | 120k · 23 calls · 141 s · Q1 equal, Q2 missed the toolbar undo/redo trigger |

graphify's own `benchmark` on rapla reports 3.1× "reduction" — graph answer
(~457k tokens per question) vs. reading the whole 1.4M-token corpus. That is the
methodology behind the 49–71× blog numbers; nobody pays the naive baseline.

## Root causes

- **Java call resolution is name-based across files.** Bare calls to inherited
  methods (`storeAndRemove(...)` inside `FileOperator`) resolve to any same-named
  method in the repo → 52 % of Java call edges are INFERRED (12,377 of 23,763).
  TypeScript edges are import-based and clean (3 % INFERRED). Upstream issue 598,
  fix PR unmerged as of 0.9.60.
- **Interface → implementation and Spring wiring are invisible** to an AST graph:
  `@Bean` factories, constructor injection by interface type. Every trace stops at
  `StorageOperator`.
- **Angular templates are strings** to tree-sitter: `(click)="undo.undo()"` yields no
  call edge, so UI triggers are missing by construction.
- **`graphify query` returns node lists**, truncated at the budget; the agent still
  has to open the files. With verification required, the graph is an extra step
  before the same reads.
- **Our baseline already has a map**: AGENTS.md §2a routing, `docs/architecture/`,
  Explore subagents, MEMORY.md. The enthusiastic reports compare against an agent
  with none of that.

## Vaults

Not applicable. `docs/` has ~2,600 relative Markdown links (the graph is already in
the text); semantic extraction would push ~1M tokens through an LLM per rebuild, and
`docs/yogavidya` / `docs/leihschein` carry customer data. The private vault is too
small for a graph and contains PII. What both need is link hygiene (broken links,
orphans), which graphify does not provide.

## Enterprise / hosted

Hosted Free (25,000-node cap, 15 PR reviews/month), Pro, Teams; Enterprise adds SMT
"formal verification" (Python examples, Java undocumented), graph-aware PR review,
self-hosting, SSO. None needed: the graph itself is identical in OSS, rapla has one
maintainer, and reviews need pull requests we do not use. code-review-graph (MIT) is
the OSS stand-in for blast radius / test gaps, with the same parser limits.

## What we keep from this

The measurements pointed at gaps in **our own map**, which is where the token savings
actually are — see the follow-up plan: SPA store path missing from
[flows.md § 3](../architecture/flows.md#3-store--mutating-entities) (WriteGate
appears in no architecture doc), no interface → implementation → factory table, no
class → doc index, 72 unguarded `File.java:NN` anchors and 15 broken relative links,
and AGENTS.md as the largest always-on cost (~12.5k tokens per turn).
