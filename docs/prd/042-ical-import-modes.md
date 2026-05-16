# PRD 042: iCal-to-Reservation import — one-time + read-only sync

**Status:** draft
**Date:** 2026-05-15

## Goal

Replace the current broken iCal import (`org.rapla.plugin.ical.ICalImport` / `RaplaICalImport`) with a two-mode importer that turns iCal `VEVENT`s into rapla `Reservation`s:

1. **One-time import (modifiable)** — iCal events become rapla Reservations owned by the importing user. They are normal editable reservations after import; the import link is informational only (UID stored as `KEY_EXTERNALID` for re-import deduplication).
2. **Read-only periodic sync (managed)** — iCal events become rapla Reservations *managed* by a sync source. They appear everywhere normal Reservations do (calendar views, conflict detection, reports) but are **read-only** in the UI; the source feed is authoritative; rapla refetches periodically and applies changes; reservations disappear when they're removed from the source.

Both modes share parsing infrastructure: the `IcalFeedParser` service introduced in PRD 039 is the substrate. The differences live above the parse layer — what rapla does with the parsed events.

## Background — current state

`RaplaICalImport.java` has been **silently broken since the Date→LocalDateTime migration** (`670219d9f`, ~6 months ago). The entire VEVENT-parsing block (~200 lines) is commented out. The handler returns `[0, 0, 0, 0]` (parsed 0, imported 0, present 0, skipped 0) on every call, with no error. A user invoking import via the Swing `ImportFromICalMenu` sees "imported 0 events" and assumes their file was empty.

Auxiliary state:
- `POST /api/ical/import` endpoint exists and accepts requests.
- Angular OpenAPI codegen exists (`rapla-angular/src/app/api/api/i-cal-import-controller.service.ts`) but no SPA UI consumes it.
- Swing `ImportFromICalMenu` dialog still works as a user-facing dialog (file/URL picker, allocatable selector, event-type mapping) — but submits to the dead handler.
- `calcRepeating()` helper exists and works for COUNT-bounded RRULEs but throws `UnsupportedOperationException` on UNTIL-bounded RRULEs.
- Zero tests on this entire surface.

The user has accepted breaking the current wire shape, so the rewrite is freed from API-compat constraints.

## The two modes

| | **Mode 1 — one-time import** | **Mode 2 — read-only sync** |
|---|---|---|
| What rapla creates | Normal `Reservation` objects | `Reservation` objects flagged `externallyManaged=true` |
| Editability | Fully editable post-import | Read-only (UI hides edit/delete; only the sync can mutate) |
| Source authority | Rapla owns the data after import | External feed is authoritative |
| Re-fetch behaviour | None — one-shot | Scheduled refresh per source config |
| Disappearance handling | N/A — Reservation persists | Removed from rapla when removed from source |
| Manual mutation | Allowed by the owning user | Allowed only by admin with explicit "break the sync" action |
| UID re-import handling | Configurable: `SKIP_EXISTING` / `UPDATE_EXISTING` / `RECREATE_ALL` | N/A — sync handles it |
| Use case | "Bootstrap rapla from our legacy system one time"; "import last semester's schedule" | "Mirror the central university calendar into rapla as read-only schedule"; "keep our department's contractor schedule in sync from their system" |
| Sibling PRD relationship | Distinct from PRD 039 (which produces busy markers, not Reservations) | **Distinct from PRD 039**: PRD 039 events appear only in conflict detection (`ExternalAppointment`); Mode 2 events appear everywhere Reservations do. Different mental model. |

A given allocatable can have any combination: PRD 038 writes (rapla → Exchange), PRD 039 subscriptions (busy-marker awareness), PRD 042 Mode 2 syncs (managed Reservations), and ordinary user-created Reservations. They compose; each carries its own provenance.

## How Mode 2 differs from PRD 039 — concrete example

A Dozent's published Outlook calendar URL:

- **PRD 039 subscription, `interpretationMode=BUSY_TIMES`**: events show as anonymous "busy 14:00–16:00" blocks in conflict-detection only. Don't appear in the Dozent's row in the schedule grid as full entries.
- **PRD 042 Mode 2 sync**: events show as full Reservations in the schedule grid — with titles (if `visibility=FULL`), with the Dozent as an allocatable, participating in everything rapla shows. Visually distinct (badge/border indicating "externally managed") but functionally present everywhere.

Use Mode 2 when: the external feed *is* the schedule of record for those events, and rapla should expose them. Use PRD 039 when: the external feed represents constraints on someone's availability but the events themselves aren't rapla business.

## Scope

**In scope (both modes):**

- Rewrite `RaplaICalImport` on top of PRD 039's `IcalFeedParser`. Parser handles iCal4j strict mode, recurrence expansion, timezone normalisation, size caps, `TRANSP:TRANSPARENT` / `X-MICROSOFT-CDO-BUSYSTATUS:FREE` filtering.
- Fix the `UNTIL`-bounded RRULE handling that currently throws.
- New REST endpoints under `/api/ical-import/*` per AGENTS.md §15 (the existing `/api/ical/import` URL gets retired; Angular codegen regenerates):
  - `POST /api/ical-import/preview` — dry-run: parse the source, return what *would* be imported (event count, per-event details, parse errors, conflict-detection preview). No DB writes. Used by the UI to show admins what they're about to commit.
  - `POST /api/ical-import/commit` — actually create Reservations from a previously-previewed source. Requires an idempotency token from the preview response.
  - `POST /api/ical-import/sync-sources` — CRUD for Mode 2 sync sources.
  - `GET /api/ical-import/sync-sources/{id}/health` — last fetch, last error, managed-reservation count.
- **Admin-only** for both modes. Importing reservations is privileged; non-admin users get 403.
- Auth, request handling, permission filtering all follow rapla's existing patterns (AGENTS.md §12 leak invariants on every endpoint).

**In scope (Mode 1 specifics):**

- Request body: file content (multipart upload) OR URL + classification mapping config + target allocatable list + update strategy (`SKIP_EXISTING` default / `UPDATE_EXISTING` / `RECREATE_ALL`).
- Classification mapping: which rapla event type the imported Reservations get, plus a per-attribute mapping (`SUMMARY → name`, optionally `DESCRIPTION → description-attribute`, `LOCATION → location-attribute`).
- UID dedup via existing `RaplaObjectAnnotations.KEY_EXTERNALID` annotation. Strategy controls collision behaviour.
- Reservations created with `owner = the importing admin user`.

**In scope (Mode 2 specifics):**

- New entity `IcalSyncSource`: `url`, `displayName`, `enabled`, `refreshIntervalMinutes` (default 60), classification mapping, allocatable mapping, `lastFetched`, `lastError`, `managedReservationCount`, `etag` / `lastModified` for conditional GETs. Persisted via the existing storage operators (XML reader/writer + JDBC DDL Liquibase changelog).
- New annotation `RaplaObjectAnnotations.KEY_EXTERNAL_SYNC_SOURCE` on Reservations created by Mode 2 sync — value is the `IcalSyncSource.id`. Combined with the existing `KEY_EXTERNALID` (UID), this uniquely identifies an external-managed reservation.
- Read-only enforcement: every controller that mutates Reservations (`POST/PUT/DELETE /api/reservations` etc.) checks for `KEY_EXTERNAL_SYNC_SOURCE`; if present, non-admin users get 403 with explanatory message ("This reservation is externally managed by sync source X and cannot be edited directly. Edit it at the source.").
- Admin "break the sync" action: admin can disable the sync source's coverage of a specific Reservation, which strips the `KEY_EXTERNAL_SYNC_SOURCE` annotation; the reservation becomes ordinary and editable. Audit-logged. Reverse not supported (an ordinary reservation can't be retroactively "claimed" by a sync source).
- Scheduled fetcher (same scheduler pattern as PRD 039): fetches enabled sync sources at their refresh interval. Parses via `IcalFeedParser`. Diffs against existing managed Reservations for this source: create new, update changed (start/end/name/etc.), delete vanished. Logs counts per fetch.
- Conflict handling on sync: if a managed Reservation's update would create a conflict with another Reservation (managed or otherwise), the update **proceeds anyway** — the sync source is authoritative and conflicts should surface in rapla's conflict UI for the planner to investigate. Don't silently reject the update; rapla's conflict model is advisory, not blocking.
- Body-marker symmetry with PRD 038: managed Reservations get a description attribute (or annotation) noting "Externally managed by sync from `<URL>`. Edits will be overwritten on next sync." Visible to anyone who can see the reservation.

**Out of scope:**

- Modifying the legacy `POST /api/ical/import` URL or `Import` record format. The endpoint goes away; the new endpoints are at `/api/ical-import/*`. Angular codegen regenerates against the new shape. The Swing menu (`ImportFromICalMenu`) is rewritten to call the new endpoints.
- Angular UI for either mode. The new endpoints are codegen-ready; an SPA-side UI is a follow-up (track as PRD 043 if/when a customer asks).
- Two-way sync (rapla → external). That's PRD 038's territory. Mode 2 is strictly external→rapla.
- Mode-conversion ("turn a Mode 1 import into a Mode 2 managed sync after the fact"). One-time imports are one-time; if a user wants ongoing sync, they create a Mode 2 source and accept that the initial state may diverge from their previous Mode 1 result.
- Conflict-of-managed-Reservations resolution UI. If two sync sources both try to create reservations for the same allocatable at the same time, both are created and rapla's normal conflict detection surfaces them. Multi-sync coordination is a Phase-6 polish.
- Authentication for sync source feeds (HTTP Basic, OAuth). v1 supports public/secret-URL feeds only, same as PRD 039.

## Plan

### Phase 1 — Cleanup + shared infrastructure

1. Confirm PRD 039's `IcalFeedParser` service is in place and exposes the API this PRD needs (parse + recurrence expansion + filtering). Add any missing primitives.
2. Delete the dead-code commented-out block in `RaplaICalImport.java`. Stub the endpoint to return a clear error ("iCal import is being rewritten; use `/api/ical-import/*` endpoints") so existing callers (Swing menu, Angular codegen) get a real signal until they're updated.
3. Liquibase changelog for new `ical_sync_source` table + new annotation columns where needed.

### Phase 2 — Mode 1 (one-time import)

1. New endpoint `POST /api/ical-import/preview`. Accepts file (multipart) or URL + classification mapping + target allocatable ids. Calls `IcalFeedParser`, applies the classification mapping in memory (no DB writes), returns:
   - Per-event preview (parsed start/end/name/recurrence summary, mapping result, would-be-skipped reason if any)
   - Aggregate counts (parsed, would-import, would-update, would-skip, parse errors)
   - Conflict-detection preview against existing rapla Reservations
   - Idempotency token (UUID, valid 15 min, stored in a server-side preview cache)
2. New endpoint `POST /api/ical-import/commit`. Accepts the idempotency token from preview + update strategy (`SKIP_EXISTING` | `UPDATE_EXISTING` | `RECREATE_ALL`). Re-parses the cached preview and writes Reservations. Returns the same shape as preview but with actual counts.
3. Rewrite Swing `ImportFromICalMenu` to use the preview→commit flow: dialog shows preview results before committing.
4. Tier-1/2 tests for the mapping and recurrence handling.

### Phase 3 — Mode 2 (read-only sync)

1. New entity `IcalSyncSource` in `rapla-core`. Persistence: XML reader/writer + Liquibase JDBC DDL.
2. Admin UI for sync sources (Swing first; Angular later). CRUD operations + "Test fetch now" + per-source health view.
3. New annotation `KEY_EXTERNAL_SYNC_SOURCE` on `Reservation`. Add to `RaplaObjectAnnotations`.
4. Read-only enforcement: extend the existing reservation mutation controllers with a check on this annotation. Non-admin → 403 with explanatory message. Admin gets a warning but can proceed (with audit-log entry).
5. Scheduled `IcalSyncFetcher`: fetches enabled sources, parses via `IcalFeedParser`, diffs and writes. Uses the same `@Scheduled` pattern as `SynchronisationManager`.
6. "Break the sync" admin action — endpoint `POST /api/reservations/{id}/break-sync` strips the `KEY_EXTERNAL_SYNC_SOURCE` annotation. Audit-logged.

### Phase 4 — Tests + privacy review

1. Per AGENTS.md §10 pyramid:
   - Tier 1 (pure unit): per-event mapping, recurrence handling, classification application.
   - Tier 2 (facade): preview→commit idempotency, dedup behaviour per update strategy, sync fetcher (initial + delta + delete-on-vanish), `WireMock` against recorded ICS payloads.
   - Tier 3 (Spring slice MockMvc): admin-only gate on every endpoint (non-admin → 403), read-only enforcement on managed Reservations (the AGENTS.md §12 leak test specifically for `KEY_EXTERNAL_SYNC_SOURCE` annotations — non-admin GET on a managed Reservation can still see it but can't mutate; PUT returns 403; existence of the sync-source URL itself never leaks to non-admin).
2. Privacy: sync source URLs can contain secrets (Google Calendar's "secret ICS URL"). The URL must never appear in API responses to non-admin users, in error messages to non-admin users, or in audit logs (log the source's `displayName` or `id`, never the URL).

### Phase 5 — Docs

1. `docs/configuration.md` — new section on Mode 2 sync sources: setup, mapping config, allocatable mapping, "what 'externally managed' means for end users."
2. User-facing notice: when a user opens a managed Reservation, the read-only state is surfaced ("Managed by sync source 'University Master Schedule'; edits will be overwritten") — same pattern as PRD 038's body marker for the opposite direction.
3. Migration note: the legacy `POST /api/ical/import` endpoint is removed; callers must move to `/api/ical-import/preview` + `/api/ical-import/commit`. Document any external integrators (likely none — the endpoint was broken).

## Tests

Per AGENTS.md §10 pyramid (details in Plan Phase 4 above). Key invariants:

- Tier 1 covers the parsing/mapping/recurrence math (lots of edge cases — empty SUMMARY, all-day events, RRULE with UNTIL, RRULE with COUNT, EXDATE, RECURRENCE-ID overrides, cross-DST timestamps).
- Tier 2 covers fetcher behaviour (WireMock-backed): preview→commit idempotency, three update strategies, vanish-on-delete, conflict-on-update doesn't abort, malformed-ICS rejected gracefully.
- Tier 3 covers permission boundaries: admin-only on all endpoints, AGENTS.md §12 leak test for sync-source URL secrecy, read-only enforcement on managed Reservations including the "existence is information" rule on `KEY_EXTERNAL_SYNC_SOURCE`.
- No tier-4 unless real customer integration testing surfaces an unhappy path.

## Open Questions

- **Re-import update strategy default.** Currently the broken code "skips" existing UIDs. Should the rewrite default to `SKIP_EXISTING` (preserves existing reservations) or `UPDATE_EXISTING` (refreshes from the source)? *Resolution candidate: `SKIP_EXISTING` for Mode 1 (the canonical use case is "one-time bootstrap, then leave alone"); irrelevant for Mode 2 (sync always upserts).*

- **Allocatable mapping in Mode 2.** Each managed Reservation needs to be attached to one or more rapla allocatables. Two options: (a) flat — every Reservation from this sync source gets attached to the same admin-configured list of allocatables; (b) per-event — derive the allocatable from VEVENT properties (e.g. `LOCATION` → match a room allocatable by name, or `ORGANIZER` → match a Person allocatable by email). (b) is more flexible but adds matching rules to admin config; (a) is simpler. *Resolution candidate: (a) for v1; per-event derivation as Phase 6.*

- **Recurring events in Mode 2.** A managed Reservation with `RRULE` expands locally on rapla's side. When the source feed changes the RRULE (e.g. extends the series), how does rapla represent that — modify the existing reservation's repeating definition, or delete + recreate? Modifying preserves the rapla id (used by external references like exchange-connector exports); recreate is cleaner but loses identity. *Resolution candidate: modify when possible (same RRULE type), recreate when the recurrence pattern fundamentally changes (e.g., DAILY → WEEKLY).*

- **Mode 2 sync failure handling.** If the source feed becomes unreachable for a long time, what happens to existing managed Reservations? Options: (a) keep them as-is until the feed recovers (stale data; user trusts last-known); (b) mark them all "stale" with a UI badge but keep them visible; (c) eventually delete them after some grace period (drastic; risks deleting reservations the source still considers valid). *Resolution candidate: (b) — keep visible with a "sync stale, last successful fetch X hours ago" badge; never auto-delete based on source unreachability.*

- **Per-Reservation drift detection.** If an admin uses "break the sync" to make a managed Reservation editable, then edits it, then later wants to re-attach to the sync — currently out of scope. v1 doesn't support "unbreak". This means a one-way door; admins should be sure before using it.

- **Conflict between two sync sources for the same target.** If two `IcalSyncSource`s both try to create Reservations for the same allocatables at overlapping times, both create their own Reservations. No multi-sync coordination. This is intentional for v1 — rapla's conflict UI surfaces it, admin investigates. Worth flagging as a known limit.

- **Sync-source secret URL leak in error messages.** Per the Privacy section, sync source URLs may contain secrets (Google "secret ICS URL" pattern). The fetcher's error paths must scrub URLs from logs and from API responses to non-admin users. This needs an explicit Tier-2 test: assert that no log line contains the URL when the fetch fails with a parse error or network error.

- **Concurrent imports / syncs.** What happens if two admins call `/api/ical-import/commit` simultaneously with overlapping content, or if a Mode 2 sync fires while a Mode 1 import is mid-write for overlapping UIDs? Rapla's existing storage operator handles concurrent writes via the existing lock mechanism (`requestLock` used by `SynchronisationManager`). v1: serialise via the same lock; concurrent calls queue.

## References

- iCal4j 4.2.0 (already on rapla's classpath): https://www.ical4j.org/
- RFC 5545 — iCalendar core spec
- Existing rapla code (broken): `rapla-server/src/main/java/org/rapla/plugin/ical/server/RaplaICalImport.java`
- Existing rapla code (Swing UI, still functional): `rapla-client/src/main/java/org/rapla/plugin/ical/client/swing/ImportFromICalMenu.java`
- The break commit: `670219d9f Date->LocalDateTime: pure-rename + mechanical changes (67 files)`
- Related PRDs: 023 (AllocationConflictModel — managed Reservations participate in conflict detection normally), 026 (Angular SPA — future UI surface), 038 (Graph calendar sync — opposite-direction sibling, rapla→external; shares body-marker / read-only-contract pattern), 039 (Per-resource iCal subscriptions — shares `IcalFeedParser`; busy-marker mode, distinct from Mode 2's full-Reservation mode)
