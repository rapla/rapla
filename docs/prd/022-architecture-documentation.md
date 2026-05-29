# PRD 022 — Architecture reference documentation

**Status:** in-progress
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-10

## Goal

A stable **reference** for Rapla's architecture that (a) lets a new/returning contributor build a correct mental model in an afternoon without `git grep` archaeology, (b) pins down load-bearing-but-under-documented parts (dynamic-type schema, reservation-edit clone semantics, permission resolution, Spring-DI plugin wiring), and (c) primes future AI agent sessions so they don't rediscover layering each time.

**Reference, not tutorial** — pick a topic, jump in, leave with file:line pointers. PRDs 005 / 008 / 011 and `docs/conflict-detection.md` cover their narrow slices in depth; this set links rather than duplicates.

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

- **Not** a user-facing administrator manual (UI screenshots / how-tos belong in the `attic/` site).
- **Not** an API/Javadoc replacement — file:line signposts, follow them.
- **Not** ADRs. Architectural decisions go in PRDs; this set documents *what is*, not *why we chose it*.

## Audience

New contributors (layered map: module → domain object → flow); returning maintainers (where are the seams?); AI agents invoked on cross-subsystem tasks (primes context with the right packages/classes/patterns).

## Plan

1. Write eight topic docs + index in one pass, using `docs/conflict-detection.md` conventions: `path/to/File.java:NNN` refs, tables for catalogs, fenced blocks for traces, no images.
2. Cross-link liberally — each doc has a "See also" footer.
3. Land all files in one commit.

## Tests / verification

No automated tests. Per-doc review: every `path:line` checkable by IDE jump; no claim about deleted/renamed/moved classes (cross-check with `grep -n`); no duplication of `AGENTS.md` / `docs/conflict-detection.md` / done PRDs — link instead.

## Maintenance

Architecture docs decay. To slow the rot: when a PRD lands a structural change, its closing checklist includes "update affected `docs/architecture/*.md`". This PRD moves to `docs/prd/done/` once the eight docs land; per-doc updates do not need new PRDs.

## Open questions

None blocking. Revisit later:

- **Diagrams.** Mermaid (GitHub-native) would help overview/flow diagrams. Left out of v1 to stay editor-agnostic.
- **Generated content.** Plugin catalog + extension-point table could be auto-generated from `@Service`/extension-point interfaces once Spring DI migration is complete.
