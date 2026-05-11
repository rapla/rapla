# PRD 022 — Architecture reference documentation

**Status:** in-progress
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-10

## Goal

Produce a stable **reference** for Rapla's architecture that:

1. Helps a new contributor (or returning contributor) build a correct mental
   model in an afternoon, without having to reverse-engineer the codebase from
   `git grep`.
2. Pins down the parts of the system that are **load-bearing but
   under-documented today** — the dynamic-type schema, the
   reservation-edit dance with clone semantics, the permission
   resolution algorithm, the Spring-DI plugin wiring.
3. Serves as durable **context for future AI agent sessions** so they don't
   have to rediscover the layering, package conventions, and key integration
   seams every time.

This is **reference**, not a tutorial — readers can pick a topic, jump in,
and come out with file:line pointers into the live code. PRD 005
(multi-module split), PRD 008 (server sync / client async), PRD 011
(Spring Boot 4 / Jackson 3) and `docs/conflict-detection.md` already
cover their narrow slices in depth; this set links to those rather
than duplicating them.

## Scope

A new directory `docs/architecture/` containing:

| File | Topic |
|---|---|
| `README.md` | Index, audience, update policy |
| `overview.md` | Five-module reactor, runtime topology, layering |
| `domain-model.md` | Entities (Reservation, Allocatable, …), ER summary |
| `dynamic-types.md` | DynamicType / Attribute / Classification schema-on-data |
| `conflicts-and-events.md` | Reservation as event container, AppointmentBlock, ConflictFinder |
| `reservation-edit.md` | End-to-end edit flow with clone semantics |
| `permissions.md` | Permission model, AccessLevel, resolution algorithm |
| `extension-points.md` | Spring-DI plugin wiring + extension-point catalog |
| `flows.md` | Cross-cutting flows: login, query, store, refresh poll |

Out of scope (covered elsewhere):

- Build/test discipline — `AGENTS.md` §5–§9
- Conflict-overlap algorithm — `docs/conflict-detection.md` (kept; referenced)
- Spring Boot migration history — PRD 001, 011, 018, 019
- WSL2 / OpenWebStart — `docs/development.md`

## Non-goals

- **Not** a user-facing administrator manual. UI screenshots, click-paths,
  and admin how-tos belong in a separate user manual (currently the
  `attic/` site).
- **Not** an API/Javadoc replacement. We give signposts — file:line
  pointers — and trust readers to follow them.
- **Not** ADRs. Architectural decisions that need preserving go into
  PRDs (`docs/prd/`); this set documents *what is*, not *why we chose it*.

## Audience

1. **A new contributor** picking up Rapla for the first time. They get a
   layered map: module → domain object → flow.
2. **A returning maintainer** who wrote some of this code 18 months ago
   and needs to remember where the seams are.
3. **An AI agent** invoked on a task that crosses a subsystem boundary
   (e.g. "add a column to the reservation list view"). The doc set
   primes the agent's context with the right packages, classes, and
   patterns.

## Plan

1. Write the eight topic docs + index in one pass, using the ASCII /
   Markdown conventions already established in `docs/conflict-detection.md`:
   - File:line references render as `path/to/File.java:NNN` (clickable in
     most IDEs and in GitHub).
   - Use tables for catalogs (entities, plugins, extension points).
   - Use fenced code blocks for stack traces and call chains.
   - No screenshots; no embedded images.
2. Cross-link liberally — each doc has a "See also" footer.
3. Land all files in one commit so the index is never broken.

## Tests / verification

Documentation has no automated tests, but each doc is reviewed for:

- Every `path:line` reference is checkable by Read tool / IDE jump.
- No claim about a class or method that has been deleted, renamed,
  or moved (cross-checked with `grep -n` while writing).
- No duplication of content already in `AGENTS.md`, `docs/conflict-detection.md`,
  or done PRDs — instead, link.

## Maintenance

Architecture docs **decay**. To slow the rot:

- When a PRD lands a structural change (module split, facade rewrite,
  permission rule change), the closing checklist includes "update
  affected `docs/architecture/*.md`".
- This PRD itself moves to `docs/prd/done/` once the eight docs are
  in tree. Updates to individual docs do not need a new PRD; they
  go in directly.

## Open questions

None blocking. Two items to revisit later:

- **Diagrams.** Pure-Markdown ASCII art works but is limited. If the
  team adopts a diagram tool (Mermaid is the obvious candidate; GitHub
  renders it natively), the overview and flow diagrams would benefit.
  Left out of v1 to keep the docs editor-agnostic.
- **Generated content.** The plugin catalog and extension-point table
  could be auto-generated from `@Service` / extension-point interfaces.
  Worth doing once the Spring DI migration is fully complete.
