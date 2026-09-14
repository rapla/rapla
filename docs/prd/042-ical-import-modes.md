# PRD 042: iCal-to-Reservation import — one-time + read-only sync

**Status:** draft
**Date:** 2026-05-15

## Goal

Replace the current broken iCal import (`org.rapla.plugin.ical.ICalImport` / `RaplaICalImport`) with a two-mode importer that turns iCal `VEVENT`s into rapla `Reservation`s:

1. **One-time import (modifiable)** — iCal events become rapla Reservations owned by the importing user. They are normal editable reservations after import; the import link is informational only (UID stored as `KEY_EXTERNALID` for re-import deduplication).
2. **Read-only periodic sync (managed)** — iCal events become rapla Reservations *managed* by a sync source. They appear everywhere normal Reservations do (calendar views, conflict detection, reports) but are **read-only** in the UI; the source feed is authoritative; rapla refetches periodically and applies changes; reservations disappear when they're removed from the source.

Both modes share parsing infrastructure: the `IcalFeedParser` service introduced in [PRD 039](039-external-ical-subscription-per-resource.md) is the substrate. The differences live above the parse layer — what rapla does with the parsed events.

## Background — current state

`RaplaICalImport.java` silently broken since Date→LocalDateTime migration (`670219d9f`, ~6 months ago). VEVENT-parsing block (~200 lines) commented out. Handler returns `[0,0,0,0]` on every call with no error — users see "imported 0 events" and assume empty file.

State: endpoint `POST /api/ical/import` exists; Angular OpenAPI codegen exists but no SPA UI; Swing `ImportFromICalMenu` still presents the dialog (file/URL, allocatable, event-type mapping) but submits to dead handler; `calcRepeating()` works for COUNT-bounded RRULEs but throws on UNTIL; zero tests on this surface. User accepts breaking the wire shape.

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
| Sibling PRD relationship | Distinct from [PRD 039](039-external-ical-subscription-per-resource.md) (which produces busy markers, not Reservations) | **Distinct from [PRD 039](039-external-ical-subscription-per-resource.md)**: [PRD 039](039-external-ical-subscription-per-resource.md) events appear only in conflict detection (`ExternalAppointment`); Mode 2 events appear everywhere Reservations do. Different mental model. |

A given allocatable can have any combination: [PRD 038](038-graph-calendar-sync.md) writes (rapla → Exchange), [PRD 039](039-external-ical-subscription-per-resource.md) subscriptions (busy-marker awareness), PRD 042 Mode 2 syncs (managed Reservations), and ordinary user-created Reservations. They compose; each carries its own provenance.

## Three-way comparison — Mode 1 vs Mode 2 vs [PRD 039](039-external-ical-subscription-per-resource.md)

[PRD 039](039-external-ical-subscription-per-resource.md) produces something *different* from a Reservation; the three together cover the spectrum:

| | **[PRD 039](039-external-ical-subscription-per-resource.md)** busy-marker | **Mode 1** one-time | **Mode 2** read-only sync |
|---|---|---|---|
| Creates | `ExternalAppointment` sidecar (+ `AvailabilityWindow`) | Normal `Reservation` | Managed `Reservation` (read-only flag) |
| Visible | Conflict detection only — gray busy block | Lists, grid, reports, search | Same as Mode 1 |
| Searchable | No | Yes | Yes |
| Title visible | Owner only (`BusyOnlyProjection`) | All with read | All with read |
| Editable | N/A | Yes | No (admin can break the sync) |
| Auto-refetch | Yes | No | Yes |
| Vanish on source delete | Yes | No | Yes |
| Privacy default | `BUSY_ONLY` (titles stripped) | Full detail | Full detail |
| Marker | `ExternalCalendarSubscription` → `ExternalAppointment` | `KEY_EXTERNALID = UID` | `KEY_EXTERNALID` + `KEY_EXTERNAL_SYNC_SOURCE` |

### The fundamental axis

**Sidecar vs Reservation.** Sidecar ([PRD 039](039-external-ical-subscription-per-resource.md)) for "availability matters but events aren't rapla business." Reservation (PRD 042) for events that ARE rapla business. Then one-shot (Mode 1, rapla owns) vs ongoing (Mode 2, source owns).

### Decision tree

```
Real rapla entries (lists/reports/titles/allocatables)?
  No  → PRD 039 subscription (BUSY/AVAIL/MIXED)
  Yes → Sync with source?
          No  → Mode 1 (one-shot)
          Yes → Mode 2 (ongoing read-only)
```

### Same source, three different choices

Dr. Schmidt's Outlook iCal:
- **[PRD 039](039-external-ical-subscription-per-resource.md) (BUSY_TIMES)** — gray busy blocks on her row; no titles for others; blocks rapla bookings on top.
- **Mode 1** — Outlook events become editable Reservations with her as allocatable. Outlook changes tomorrow → rapla doesn't notice.
- **Mode 2** — events appear as read-only managed Reservations; badge "Managed by sync from …"; Outlook changes propagate; Outlook delete removes from rapla.

### Privacy distinction

[PRD 039](039-external-ical-subscription-per-resource.md) is privacy-aware by default (BUSY_ONLY) — right for personal calendars. PRD 042 has no built-in privacy stripping — titles visible to anyone with read. So personal calendars → [PRD 039](039-external-ical-subscription-per-resource.md); administrative/organisational calendars (dean's office, central room schedule, official course calendar) → PRD 042.

### Coexistence

OK with care. Mode 2 of admin schedule + [PRD 039](039-external-ical-subscription-per-resource.md) of personal Outlook on the same allocatable composes well. **Don't wire both to the same source** — duplicate conflict signals.

### Why "true dual sync" isn't a v1 goal

Decomposes into three one-way flows:
- rapla bookings in Outlook → [PRD 038](038-graph-calendar-sync.md)
- Personal calendar blocks rapla → [PRD 039](039-external-ical-subscription-per-resource.md)
- External schedule as rapla bookings → PRD 042 Mode 2

Three needs, three one-way solutions. Combining yields bidirectional behaviour without rapla solving bidirectional-sync conflict resolution.

## Scope

**In scope (both modes):**

- Rewrite `RaplaICalImport` on top of [PRD 039](039-external-ical-subscription-per-resource.md)'s `IcalFeedParser`. Parser handles iCal4j strict mode, recurrence expansion, timezone normalisation, size caps, `TRANSP:TRANSPARENT` / `X-MICROSOFT-CDO-BUSYSTATUS:FREE` filtering.
- Fix the `UNTIL`-bounded RRULE handling that currently throws.
- New REST endpoints under `/api/ical-import/*` per AGENTS.md §15 (the existing `/api/ical/import` URL gets retired; Angular codegen regenerates):
  - `POST /api/ical-import/preview` — dry-run: parse the source, return what *would* be imported (event count, per-event details, parse errors, conflict-detection preview). No DB writes. Used by the UI to show admins what they're about to commit.
  - `POST /api/ical-import/commit` — actually create Reservations from a previously-previewed source. Requires an idempotency token from the preview response.
  - `POST /api/ical-import/sync-sources` — CRUD for Mode 2 sync sources.
  - `GET /api/ical-import/sync-sources/{id}/health` — last fetch, last error, managed-reservation count.
- **Admin-only** for both modes. Importing reservations is privileged; non-admin users get 403.
- Auth, request handling, permission filtering all follow rapla's existing patterns (AGENTS.md §12 leak invariants on every endpoint).

**In scope (Mode 1 specifics):**

- Request: file (multipart) or URL + classification mapping + target allocatables + update strategy (`SKIP_EXISTING` default / `UPDATE_EXISTING` / `RECREATE_ALL`).
- Mapping: event type + per-attribute (`SUMMARY → name`, optionally `DESCRIPTION/LOCATION` → attribute).
- UID dedup via `KEY_EXTERNALID`. Reservations owned by importing admin.

**In scope (Mode 2 specifics):**

- New entity `IcalSyncSource`: `url`, `displayName`, `enabled`, `refreshIntervalMinutes` (default 60), classification + allocatable mapping, `lastFetched`, `lastError`, `managedReservationCount`, `etag`/`lastModified`. Persisted via XML reader/writer + Liquibase JDBC DDL.
- New `KEY_EXTERNAL_SYNC_SOURCE` annotation = `IcalSyncSource.id`. Combined with `KEY_EXTERNALID` uniquely identifies external-managed.
- Read-only enforcement: reservation mutation controllers check the annotation; non-admin → 403 with explanatory message naming the source.
- Admin "break the sync": strips the annotation; reservation becomes editable. Audit-logged. One-way (no re-attach).
- Scheduled fetcher ([PRD 039](039-external-ical-subscription-per-resource.md) pattern): fetch enabled sources, parse via `IcalFeedParser`, diff against managed Reservations (create/update/delete-vanished), log counts.
- Sync conflict handling: update proceeds even when it creates conflicts (source is authoritative; rapla conflict UI is advisory).
- Body-marker symmetry with [PRD 038](038-graph-calendar-sync.md): description note "Externally managed by sync from `<URL>`. Edits will be overwritten."

**Out of scope:**

- Legacy `POST /api/ical/import` shape — endpoint removed; new at `/api/ical-import/*`. Codegen + Swing menu regenerate.
- Angular UI — codegen-ready, but UI is follow-up ([PRD 043](043-api-keys-jwt-pat.md) if asked).
- Two-way sync ([PRD 038](038-graph-calendar-sync.md) territory).
- Mode-conversion (Mode 1 → Mode 2 after the fact).
- Multi-sync coordination — two sources for same allocatable both create; conflict UI surfaces it.
- Auth on feed URLs (Basic/OAuth) — public/secret-URL only, same as [PRD 039](039-external-ical-subscription-per-resource.md).

## Sequencing & dependencies

PRD 042 is **furthest downstream** in the iCal/Exchange family:

- **Hard dep on [PRD 039](039-external-ical-subscription-per-resource.md)** — `IcalFeedParser` for all parsing (strict-mode, size caps, recurrence expansion, `BUSYSTATUS`/`TRANSP` filtering, tz normalisation). Phase 1 step 1 gates on it.
- **Soft dep on [PRD 038](038-graph-calendar-sync.md)** — Mode 2's loopback uses `RaplaExportedEvent`-based logic; degrades to no-filter when absent (admin wiring both 038 + Mode 2 against same target before 038 lands sees duplicates).

[PRD 039](039-external-ical-subscription-per-resource.md) must land first (or at least `IcalFeedParser` merged). [PRD 038](038-graph-calendar-sync.md) can land before or after.

## Plan

### Phase 1 — Cleanup + shared infrastructure

1. Confirm [PRD 039](039-external-ical-subscription-per-resource.md)'s `IcalFeedParser` service is in place and exposes the API this PRD needs (parse + recurrence expansion + filtering). Add any missing primitives.
2. Delete the dead-code commented-out block in `RaplaICalImport.java`. Stub the endpoint to return a clear error ("iCal import is being rewritten; use `/api/ical-import/*` endpoints") so existing callers (Swing menu, Angular codegen) get a real signal until they're updated.
3. Liquibase changelog for new `ical_sync_source` table + new annotation columns where needed.

### Phase 2 — Mode 1 (one-time import)

1. `POST /api/ical-import/preview` — file/URL + mapping + allocatable ids. Calls `IcalFeedParser`, applies mapping in-memory. Returns per-event preview, aggregate counts (parsed/would-import/-update/-skip/errors), conflict preview, idempotency token (UUID, 15 min cache).
2. `POST /api/ical-import/commit` — token + strategy. Re-parses cached preview, writes Reservations.
3. Rewrite Swing `ImportFromICalMenu` for preview→commit flow.
4. Tier-1/2 tests for mapping + recurrence.

### Phase 3 — Mode 2 (read-only sync)

1. New `IcalSyncSource` entity in `rapla-core` (XML + Liquibase JDBC DDL).
2. Admin UI for sync sources (Swing first, Angular later) + "Test fetch now" + health view.
3. Add `KEY_EXTERNAL_SYNC_SOURCE` to `RaplaObjectAnnotations`.
4. Read-only enforcement on reservation mutation controllers; non-admin → 403. Admin proceeds with audit-log warning.
5. `IcalSyncFetcher` `@Scheduled` (same pattern as `SynchronisationManager`): fetch → parse → diff → write.
6. `POST /api/reservations/{id}/break-sync` — admin strips annotation. Audit-logged.

### Phase 4 — Tests + privacy review

1. Per §10 pyramid: tier 1 mapping/recurrence; tier 2 preview→commit idempotency + strategies + fetcher (initial/delta/delete-vanished) WireMock; tier 3 admin-only gates + §12 leak test on `KEY_EXTERNAL_SYNC_SOURCE` (non-admin sees but can't mutate; URL never leaks).
2. Privacy: sync URLs may carry secrets (Google "secret ICS URL"). Never appear in API responses to non-admin, error messages to non-admin, or audit logs (log `displayName`/`id`).

### Phase 5 — Docs

1. `docs/configuration.md` — Mode 2 setup, mapping, allocatable mapping, "what externally managed means."
2. User-facing read-only notice when opening managed Reservation (mirror [PRD 038](038-graph-calendar-sync.md) body marker).
3. Migration: `/api/ical/import` removed; callers → `/api/ical-import/preview`+`commit`.

## Tests

Per §10 pyramid (details in Phase 4 above). Key invariants:

- **Tier 1** — parsing/mapping/recurrence (empty SUMMARY, all-day, RRULE UNTIL/COUNT, EXDATE, RECURRENCE-ID, cross-DST).
- **Tier 2** — fetcher (WireMock): preview→commit idempotency, three strategies, vanish-on-delete, conflict-on-update doesn't abort, malformed-ICS graceful.
- **Tier 3** — admin-only gates, §12 leak test for URL secrecy + `KEY_EXTERNAL_SYNC_SOURCE` "existence is information".
- No tier-4 unless real-customer integration surfaces an unhappy path.

## Open Questions

- **Re-import update strategy default** — candidate `SKIP_EXISTING` for Mode 1 (canonical "one-time bootstrap"); irrelevant for Mode 2 (sync upserts).
- **Allocatable mapping in Mode 2** — candidate (a) flat admin-configured list for v1; (b) per-event derivation from `LOCATION`/`ORGANIZER` as Phase 6.
- **Recurring events in Mode 2** — candidate: modify when same RRULE type (preserves id used by exchange-connector exports); recreate on fundamental pattern change (DAILY → WEEKLY).
- **Sync failure handling** — candidate (b): "sync stale, last fetch X hours ago" badge, keep visible, never auto-delete on unreachability.
- **Per-Reservation drift detection** — "break the sync" is one-way door; v1 doesn't support unbreak.
- **Two sync sources, same target** — both create; conflict UI surfaces it. Known limit, intentional.
- **Secret-URL leak in errors** — scrub URLs from logs + non-admin responses; explicit Tier-2 test on error paths.
- **Concurrent imports/syncs** — serialise via existing storage-operator `requestLock` (same mechanism `SynchronisationManager` uses); concurrent calls queue.

## References

- iCal4j 4.2.0 (already on rapla's classpath): https://www.ical4j.org/
- RFC 5545 — iCalendar core spec
- Existing rapla code (broken): `rapla-server/src/main/java/org/rapla/plugin/ical/server/RaplaICalImport.java`
- Existing rapla code (Swing UI, still functional): `rapla-client/src/main/java/org/rapla/plugin/ical/client/swing/ImportFromICalMenu.java`
- The break commit: `670219d9f Date->LocalDateTime: pure-rename + mechanical changes (67 files)`
- Related PRDs: 023 (AllocationConflictModel — managed Reservations participate in conflict detection normally), 026 (Angular SPA — future UI surface), 038 (Graph calendar sync — opposite-direction sibling, rapla→external; shares body-marker / read-only-contract pattern), 039 (Per-resource iCal subscriptions — shares `IcalFeedParser`; busy-marker mode, distinct from Mode 2's full-Reservation mode)
