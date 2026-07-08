# Architecture Decision Records (MADR)

Durable, append-only records of **why** rapla is built the way it is — including the alternatives we
**rejected** and ideas **parked for later**. Format: [MADR](https://adr.github.io/madr/) (Markdown
Any Decision Records). This tree is the immutable "why" layer; it complements — does not replace —
the other knowledge stores (AGENTS.md §2a):

| Store | Holds | Lifecycle |
|---|---|---|
| `docs/decisions/` (here) | **why** + rejected alternatives + future ideas | append-only, superseded never rewritten |
| `docs/prd/` | a **change** ("build X"): plan, scope, in-flight `D1..Dn` | `git mv` to `done/` when shipped |
| `docs/architecture/`, `docs/*.md` | the **living contract** ("what the system does now") | edited in place, always current |
| `memory/` | volatile project state | tagged `[since:]`/`[watch:]` |

A cross-cutting, durable decision is **extracted from its PRD's `D-lock` into a MADR here**, and the
PRD links to it. The PRD's rationale would otherwise become invisible once moved to `done/`.

## Conventions

- **File name:** `NNNN-short-title-with-dashes.md` (4-digit sequential, never reused).
- **Immutable:** once `accepted`, the body is never rewritten. To change a decision, write a **new**
  MADR and flip the old one's `status` to `superseded by ADR-NNNN` (reciprocal links on both).
- **Status:** `proposed | rejected | accepted | deprecated | superseded by ADR-NNNN`.
- **Confirmation required:** every MADR names an enforceable check (arch-test / lint / contract test).
- **New MADR:** copy [`adr-template.md`](adr-template.md) to the next free number.

## Index

| # | Title | Status | From |
|---|---|---|---|
| [0001](0001-use-madr-for-architecture-decisions.md) | Use MADR for durable architecture decisions, separate from PRDs | accepted | PRD 088 |
| [0002](0002-no-sdd-framework-as-tool.md) | Adopt no SDD framework as a tool; plunder OpenSpec's ideas | accepted | PRD 088 D1 |
| [0003](0003-permissions-are-grant-only.md) | Permissions are grant-only; strongest matching row wins | accepted | code archaeology |
| [0004](0004-ai-maintained-docs-conversational-diff.md) | Docs are AI-maintained by conversational targeted diff, not regenerated | accepted | PRD 088 D7 |
| [0005](0005-graphql-keys-are-api-identity.md) | DynamicType keys are the GraphQL API identity — per-kind enum validation, loud breaking renames, revalidate-and-mark for stored views | accepted | PRD 035 §11 / PRD 059 / PRD 074 D5 |

> Once PRD 088 Phase 0 lands, this index is **generated** from MADR front-matter, not hand-maintained.
