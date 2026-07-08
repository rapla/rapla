# Rapla architecture reference

Reference documentation for Rapla's internals. Use this as a map
when you need to make a non-trivial change and want to know which
pieces you're walking into.

This is **reference**, not a tutorial. Each page assumes you can
read Java and have a working dev environment (`AGENTS.md` covers
setup). Pages link liberally to the source via `path/to/File.java`
references — pasting one into your IDE jumps to the right place.

## Audience

- A new contributor on day 1 who needs a mental map.
- A returning maintainer who wrote some of this 18 months ago.
- An AI agent invoked on a task that crosses subsystem boundaries.

## Layout

| Page | Read first if you want to know… |
|---|---|
| [overview.md](overview.md) | What modules exist, how they're laid out, how they talk |
| [domain-model.md](domain-model.md) | What entities exist, how they relate (ER summary) |
| [dynamic-types.md](dynamic-types.md) | The schema-on-data system: DynamicType, Attribute, Classification, name templates |
| [conflicts-and-events.md](conflicts-and-events.md) | Reservation as event container; Appointment / AppointmentBlock; how `ConflictFinder` works |
| [reservation-edit.md](reservation-edit.md) | End-to-end edit flow with clone semantics, EventCheck, save; wire model; AppointmentController rules; edge case reference |
| [reservation-edit-ui-inventory.md](reservation-edit-ui-inventory.md) | Every user-facing capability of the Swing edit dialog (picker states, restriction popup, free-slot search) — the coverage checklist for the SPA event sheet (PRD 091) |
| [rest-api.md](rest-api.md) | Full REST endpoint catalog grouped by audience (SPA, admin, import/export); auth header, error envelope, common-flow recipes |
| [permissions.md](permissions.md) | AccessLevel, PermissionImpl, resolution algorithm, server enforcement, JWT |
| [extension-points.md](extension-points.md) | Spring DI plugin wiring, extension-point catalog, plugin list |
| [flows.md](flows.md) | Cross-cutting flows: login, query, store/dispatch, refresh poll |
| [locking.md](locking.md) | The three lock layers (process / resource / global), the `WRITE_LOCK` table, multi-pod concurrency, the validate-before-lock ordering gotcha |
| [mvp-pattern.md](mvp-pattern.md) | Presenter / View / pure-model carve-out pattern; headless test harness; AGENTS.md §12 leak-probe pattern |
| [swing-platform-quirks.md](swing-platform-quirks.md) | Platform/compositor workarounds in the Swing client (WSLg popup input-region leak, FilterEditButton positioning, focus-dismiss handlers) |
| [migration-from-master.md](migration-from-master.md) | What changed from the historical `master` branch to the current `spring-boot` tree (restinject removed, Spring Boot 4 + Angular added, 5-module reactor, carve-out programme); size comparison + intent for new contributors |
| [tableview-and-graphql-views.md](tableview-and-graphql-views.md) | Legacy Swing TableView config (views, columns, `defaultValue` annotations, the dhbw-configured Termine table) as the capability benchmark for the GraphQL-native view system (PRD 074) — proves the new path reproduces the existing tables |
| [spa-selection-and-actions.md](spa-selection-and-actions.md) | Row/item selection in the SPA (Swing/Excel semantics, keyboard map, touch selection mode) and how a multi-row selection feeds the row-action/command system (bulk Löschen, subset-wins gating, best-effort undo) — PRDs 094/099 |
| [calendar-rendering.md](calendar-rendering.md) | Block-based calendar rendering rules extracted from Swing + HTML export (shared rapla-core strategy machinery): color resolution + always-black text, the lane/slot pipeline (`GroupAllocatablesStrategy` over SELECTED resources, fixed vs compact), rows-per-hour scale/worktime, FLOW/BLOCK selection — ground truth for the SPA month/week grids (PRDs 095/077/100) |

## What lives where (quick lookup)

- **Build, test, server / client lifecycle, hard rules** —
  [`../../AGENTS.md`](../../AGENTS.md).
- **Conflict overlap algorithm in detail** —
  [`../conflict-detection.md`](../conflict-detection.md). This page's
  `conflicts-and-events.md` covers the entities and integration; the
  algorithm itself stays in the standalone doc.
- **WSL2 / Windows / OpenWebStart** —
  [`../development.md`](../development.md).
- **Architectural decisions and ongoing migrations** — `docs/prd/`
  and `docs/prd/done/`.

## Update policy

These docs **decay**. To slow it down:

- When a PRD lands a structural change (module split, facade rewrite,
  permission rule change, plugin migration), the closing checklist
  includes "update affected `docs/architecture/*.md`."
- File:line references should be checkable via Read tool / IDE jump.
  When you see one that's stale, fix it inline; don't open a ticket.
- Each page has a "See also" footer; keep cross-links current.

When in doubt about what to keep updated, prefer the principles over
the file:line citations: the precise line numbers will drift with
every refactor, but the shape of the system changes much more
slowly. A stale line number at the bottom of a still-correct
explanation is worth more than a deleted page.

## Drafted

PRD 022 captures the rationale and scope of this doc set. It will
move to `docs/prd/done/` once the eight pages are stable.
