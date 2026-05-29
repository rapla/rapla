# PRD 039: Per-resource external iCal subscriptions for conflict awareness

**Status:** draft
**Date:** 2026-05-15

## Goal

Let a rapla resource — most importantly a **Person** resource (Dozent, room manager, equipment custodian) — be configured with one or more **external iCal subscription URLs**. rapla polls those URLs periodically, parses the events, and surfaces them as **read-only constraint markers** in conflict detection — so booking a resource against the planner's intent (either *while* they're busy or *outside* their declared availability) shows a conflict, *without* the external event becoming a rapla reservation and *without* leaking the event's title or details to other users.

The feed can express the resource's schedule in either direction; the subscription declares how to interpret it:

| Interpretation mode | iCal source pattern | Conflict semantics |
|---|---|---|
| **Busy-time publishing** (default) | `VEVENT` with `TRANSP:OPAQUE` — Google/Outlook/iCloud personal calendars | Conflict when proposed booking overlaps an external event |
| **Availability publishing** | `VAVAILABILITY` (RFC 7953) OR `VEVENT`s tagged with a marker the admin specifies — Calendly, Apple's availability, FastMail's published-hours, Dozent "office hours" feeds | Conflict when proposed booking falls **outside** every external available-window |
| **Mixed** | A feed using both `VEVENT` (busy) and `VAVAILABILITY` (when-available) — RFC 7953 designed exactly for this | Conflict if either constraint is violated |

After this PRD a rapla deployment can: configure 1..N subscription URLs per allocatable; treat Dozenten's published *availability* as constraints during planning; treat personal-calendar *busy* times the same way; render external constraints visually in the resource calendar; preserve privacy (other users see `busy 14:00–16:00` not `Therapy session`); recover gracefully when feeds become unreachable.

## Motivation

**Pain 1 — Personal-calendar conflicts (busy direction).** Planner books Dr. Schmidt at 14:00; she has a 15:00–16:00 dentist appointment in personal Google Calendar; rapla shows her free; conflict caught manually after. Most common "rapla shows free but I'm actually busy" complaint.

**Pain 2 — The availability problem (availability direction).** Dozenten declare semester availability ("Monday and Wednesday mornings, never Friday"). Rapla has no model. Data already exists in Calendly / Apple Calendar / Google "office hours" / FastMail — rapla just can't see it.

One periodic read-only subscription with per-subscription interpretation covers both. External conflicts inherit rapla's advisory-by-default model — no separate severity ladder.

The existing `org.rapla.plugin.ical.ICalImport` (`POST /api/ical/import`) is **a different feature**: one-shot admin import that *creates rapla reservations*. Mixing would be wrong: (1) one-shot writes event titles as `Reservation.name`, visible to everyone with read on the resource (privacy disaster for personal feeds); (2) one-shot creates rapla entities the user can edit/delete, drifting from source — subscriptions need the external feed authoritative.

## Scope

**In scope:**

- New model: `ExternalCalendarSubscription` linked to `Allocatable` (1:N). Fields: `url`, `displayName`, `enabled`, `refreshIntervalMinutes` (default 60), `visibility` (`BUSY_ONLY` | `FULL_DETAIL`), `interpretationMode` (`BUSY_TIMES` | `AVAILABILITY_TIMES` | `MIXED`), `loopbackTargetMailbox` (optional), `lastFetched`, `lastError`, `eventCount`, `etag`/`lastModified` for conditional GETs.
- Background fetcher on rapla's scheduler. Per-subscription interval. HTTP conditional GETs (`If-None-Match`/`If-Modified-Since`). Respects `Retry-After` on 429.
- iCal4j 4.2.0 parses; recurring `VEVENT`s expanded within lookahead window (default 365 days forward + 30 days back); `VAVAILABILITY` (RFC 7953) parsed as `AvailabilityWindow` records.
- Parsed busy events stored as `ExternalAppointment` — read-only, server-managed. Carries `originStatus` (`FOREIGN` | `RAPLA_ORIGIN_CONFIRMED` | `RAPLA_ORIGIN_PROBABLE_EXACT` | `RAPLA_ORIGIN_PROBABLE_FUZZY`) from loopback filter.
- Parsed availability windows stored as `AvailabilityWindow` — also read-only.
- **Rapla-origin loopback filter**: at parse time every `ExternalAppointment` matched against PRD 038's `RaplaExportedEvent` to identify rapla's own writes. Rapla-origin events are **excluded from conflict detection**. See dedicated section below.
- Calendar view: `ExternalAppointment` renders as visually-distinct busy blocks (e.g. diagonal-hatched, no title for non-privileged). Existing `RaplaBuilder` extension handles rendering.
- `AllocationConflictModel` (PRD 023) extended via a single **constraint projection**: `ExternalAppointment` rows + *complement* of `AvailabilityWindow` rows project into unified "constrained time" set per allocatable. Existing conflict path treats them like any other conflicting reservation. **No new severity enum.**
- Admin UI: subscription management in existing allocatable edit panel (Swing `AllocatableEditUI`, equivalent Angular). Add/remove/test.
- User self-service: resource owner (Person linked to the allocatable via `ownerUser`) manages subscriptions for their *own* resource without admin permission.
- **Owner-accessible subscription health.** Owner sees last fetch, last error, event count, refresh interval, `originStatus` breakdown — through PRD 026 allocatable detail page (and Swing equivalent). Same data as admin status page (Phase 5), scoped to owned allocatables. Cross-allocatable/deployment-wide health stays admin-only.
- REST endpoints under `/api/external-calendars/*` (per AGENTS.md §15): `GET ?resource={id}`, `POST`, `PUT /{id}`, `DELETE /{id}`, `POST /{id}/refresh`, `GET /{id}/health`. All admin OR owner.
- iCal4j strict: no external entity resolution, no nested URLs. Hard cap feed size (10 MB) and event count (5000 per fetch).
- Privacy invariants — see dedicated section below.
- **Shared infrastructure for future iCal-import rewrite.** The iCal parsing surface — iCal4j strict-mode, size/event caps, `BUSYSTATUS`/`TRANSP` filtering, recurrence expansion, UTC normalisation, parse-error reporting — built as standalone `IcalFeedParser` in `rapla-core`. Consumed by PRD 042 (Mode 1 + 2). Subscription-specific concerns (loopback filter, privacy projection, per-allocatable storage) stay in the fetcher.
- **Relationship to PRD 042's two modes** (full comparison in PRD 042 §"Three-way comparison"): this PRD produces **sidecar entities** visible only in conflict detection; PRD 042 produces **Reservations** appearing everywhere. PRD 039 is right for *availability constraints on a person* (personal calendars); PRD 042 is right for *the schedule itself* (administrative calendars). Axis: "is this event rapla business, or background information that affects my planning?"

**Out of scope:** two-way sync (PRD 038); auth for external feeds (v1 supports only public/secret-URL feeds; full OAuth belongs in PRD 038's territory); CalDAV; promoting external events to rapla reservations (`ICalImport` does this for one-shot); per-user subscriptions (subscriptions belong to *resources*); URL-level fetch dedup (Phase 4); historic backfill (use `ICalImport`).

## Interpretation modes — busy vs availability

### `BUSY_TIMES` (default)

Every `VEVENT` is "resource is busy for this window." 99% of personal Google/Outlook/iCloud feeds. `TRANSP:OPAQUE` events count; `TRANSP:TRANSPARENT` events skipped (don't block time per spec).

Conflict rule: proposed booking overlaps any busy block → conflict.

### `AVAILABILITY_TIMES`

Feed publishes *when* the resource is available. Two sub-cases:

1. **RFC 7953 `VAVAILABILITY`** — standards-correct form. Calendly, Apple Calendar (since macOS 13), FastMail, Cyrus IMAP emit this.
2. **`VEVENT`-as-availability** — older convention; availability published as ordinary events on a dedicated calendar. Admin marks subscription `AVAILABILITY_TIMES` and rapla treats every `VEVENT` as available rather than busy.

Conflict rule: proposed booking NOT fully contained in any `AvailabilityWindow` → conflict.

### `MIXED`

Feed contains both kinds (RFC 7953 explicitly designed for this — `VAVAILABILITY` defining normal availability *plus* `VEVENT` exceptions for busy times within those windows). Conflict rule: outside availability OR overlaps a busy block → conflict.

### Storage shape — two entities, one conflict projection

- `ExternalAppointment` — busy block. Start/end + (privacy-projected) summary/description/location.
- `AvailabilityWindow` — available window. Start/end + optional `VAVAILABILITY` priority (v1 ignores priority).

Both server-managed, read-only, indexed on `(allocatableId, start, end)`. NOT polymorphic at the conflict layer — see Phase 2.

### Conflict severity

External conflicts inherit rapla's existing severity model: advisory by default, suppressable manually, may be elevated to non-ignorable via existing permission system. **No per-subscription `conflictBehaviour` field** — personal calendar feeds and department holiday calendars surface conflicts identically; *response* differs case-by-case, not per-subscription policy.

## Rapla-origin loopback filter

When customer configures both **PRD 038** (rapla writes bookings into a mailbox via Graph/EWS) AND a PRD 039 subscription on that mailbox's published iCal URL, rapla's own writes loop back and would naively appear as external constraints — phantom conflicts where rapla conflicts with itself. The filter identifies these and excludes them.

Opt-in per subscription via `loopbackTargetMailbox`: if set to a mailbox address `M`, fetcher matches events against PRD 038's `RaplaExportedEvent` rows where `targetMailbox = M`. If unset (default), subscription is treated as fully foreign.

Mailbox-keyed (not user-keyed) because PRD 038's writes target a *mailbox* (the value `SynchronisationManager.getMailbox(Allocatable)` returns), not the rapla planner who triggered the write.

### Three-pass row-consumption algorithm

The filter runs three ordered passes over parsed VEVENTs with **row consumption**: a `RaplaExportedEvent` row can match multiple inbound VEVENTs *only if they share its `iCalUId`* (recurring series — master + exception VEVENTs all share the master's UID per RFC 5545). Once Pass 1 matches a row via UID, the row is consumed for Pass 2/3 — preventing false-positives where a real personal event with a *different* UID at the same time would be wrongly fuzzy-matched. Higher-confidence passes have first claim.

Non-recurring: collapses to 1:1 (one row, one VEVENT). Recurring with exceptions: multiple same-UID VEVENTs all confirmed by the same row is correct.

For a subscription with `loopbackTargetMailbox = M`:

1. **Pass 1 — UID match** (Tier 1, highest confidence). For every parsed VEVENT, look for a row where `row.targetMailbox == M AND row.iCalUId == VEVENT.UID`. (Lookup does NOT require unconsumed — a recurring exception VEVENT shares master UID and validly matches the same row again.) If found: `originStatus = RAPLA_ORIGIN_CONFIRMED`, **mark consumed for Pass 2/3** (still available for further Pass 1 same-UID matches), skip from conflict detection.

2. **Pass 2 — exact time match** (Tier 2). For VEVENTs unmatched by Pass 1, look for an *unconsumed* row for `targetMailbox = M` where reservation's `(start, end)` exactly equals `(VEVENT.DTSTART, VEVENT.DTEND)` (UTC). If found: `originStatus = RAPLA_ORIGIN_PROBABLE_EXACT`, consume, skip. Log at info.

3. **Pass 3 — fuzzy time match** (Tier 3, last resort). For still-unmatched VEVENTs, closest *unconsumed* row for `targetMailbox = M` within ±5 minutes on both endpoints. Tie-breaker: smallest delta. If found: `originStatus = RAPLA_ORIGIN_PROBABLE_FUZZY`, consume, skip. Log at warn. Surface in subscription health UI.

4. **Anything unmatched** → `originStatus = FOREIGN`. Participates in conflict detection normally.

### Why row consumption matters

Without consumption, both a rapla-owned VEVENT and a coincidentally-overlapping personal event would each match the same row by Pass 2's time check — the personal event would get filtered as a false positive, and the planner would lose visibility into a real double-booking. With consumption, the rapla-owned VEVENT claims the row in Pass 1 (UID); the personal event has no unconsumed row; falls through to FOREIGN and surfaces correctly.

Consumption also makes Pass 3 self-limiting. Pass 3 only fires when (a) Pass 1 failed for this specific VEVENT (UID stripped or never matched), (b) Pass 2 failed (no exact-time row), AND (c) there's still an unconsumed row within tolerance. For subscriptions that publish UIDs (Full/Limited Details on Exchange), all rapla-owned VEVENTs claim rows in Pass 1, every export row gets consumed, and Pass 3 has nothing left — no false positives at all. Pass 3 effectively only fires in the genuinely-ambiguous "Availability Only with anonymized UIDs AND user drift" case.

### Why three tiers, not just UID

PRD 038's Phase 1 spike will quantify it, but Microsoft's published-iCal pipeline *may* strip/synthesise the `UID:` at lower publish-detail levels — and at "Availability only" (the privacy default), UID is plausibly anonymized per-fetch. The time-window tier rescues those cases.

**Tier 2 is guaranteed to have data to match against** — independent of spike outcome — because:

- Exchange/Outlook always publishes events as `VEVENT`, never `VFREEBUSY`. [Slipstick confirms](https://www.slipstick.com/outlook/calendar/publishing-outlook-free-busy/): "Outlook does not export VFREEBUSY components and ignores them on import." Holds at every detail level including "Availability only" — only subject/description/location stripped, not structural shape.
- `DTSTART`/`DTEND` are RFC 5545-required for a valid `VEVENT`. Survive every privacy redaction.

Worst case at "Availability only" is "Tier 1 fails, Tier 2 still works" — never "no data."

### Edge cases

- **User manually moved a rapla-written event in Outlook** (e.g. 14:00–16:00 → 14:30–16:30). Tier 1 fails (UID rewrite possible). Tier 2 falls outside tolerance → `FOREIGN`. Planner sees phantom conflict at 14:30–16:30. **Correct** — user diverged their copy; rapla can't silently absorb. Surface "drifted exported event" status so user notices.

- **Reservation deleted in rapla, subscription cache hasn't caught up.** PRD 038 marks `RaplaExportedEvent` row with `deletedAt = now()` but keeps it for tombstone window (default `2 × max(refreshIntervalMinutes across the user's subscriptions)` or 2 hours, whichever is greater). Filter still matches tombstoned rows during the window. After expiry, hard-delete; if iCal feed is still stuck, the event becomes `FOREIGN` and triggers a real conflict — staleness is bigger than refresh interval and the conflict is informative.

- **Real personal event coincidentally at the same time as a rapla export to the same calendar.** Handled by row consumption — rapla-owned VEVENT claims its row in Pass 1; personal event has no unconsumed row → `FOREIGN` → surfaces correctly. Planner sees both conflicts at that time.

- **No PRD 038 export history (`RaplaExportedEvent` empty for U)**: every event resolves to `FOREIGN`. Filter dormant.

- **Subscription points at a calendar rapla DOESN'T write to.** User leaves `loopbackTargetMailbox` unset; filter never runs.

### Failure mode: filter degradation, not crash

If `RaplaExportedEvent` queries fail (DB hiccup, schema mismatch), fetcher falls back to treating every event as `FOREIGN` — same as if filter were disabled. Logged at error. **Never crash the fetch over a filter failure.**

## Privacy & leak considerations

Highest-stakes part of the PRD. ICS feeds typically contain private titles — "Therapy session", "Job interview at Acme". Default must protect resource owner's privacy. AGENTS.md §12 applies.

**Defaults:**

| Viewer | Visibility on `ExternalAppointment` (default `BUSY_ONLY`) |
|---|---|
| Resource owner (user linked to allocatable) | Full detail (title, description, location, URL) |
| Admin | Full detail |
| Other rapla users with read on the resource | Time only (`busy 14:00–16:00`) |
| Users without read on the resource | Cannot see anything |

`visibility = FULL_DETAIL` is appropriate for department-wide holiday calendars, never personal feeds.

**Enforcement points:**

1. **API output filter**: `ExternalCalendarController` runs a `BusyOnlyProjection` stripping title/description/location/URL when requester is not owner/admin and visibility is `BUSY_ONLY`. Full record never leaves the server unsanitised.
2. **Existence check inheritance** (AGENTS.md §12): non-readable resource's subscriptions and events must be indistinguishable from "resource doesn't exist". Standard reflexive 404 pattern.
3. **Calendar rendering on the server (PRD 030 SSR)**: HTML/SVG for non-owner viewers must not include external event title in DOM, alt-text, ARIA, tooltips, or print output. Renderer takes the `BusyOnlyProjection`.
4. **Audit logging**: log subscription fetch URL + event count, but **never event titles or descriptions** at any level. `AuditLogPrivacyTest` grep-tests this.
5. **Tier-3 leak test** (AGENTS.md §12 mandatory): non-admin user, mixed visible/hidden/non-existent resource ids, assert response body byte-identical to visible-only subset.

## Sequencing & dependencies

PRD 039 is **both consumer and producer** within the iCal/Exchange family:

- **Consumes** `RaplaExportedEvent` from **PRD 038** for the loopback filter. Soft dependency: filter degrades cleanly to "everything is `FOREIGN`" when missing. PRD 039 can ship before PRD 038.
- **Produces** `IcalFeedParser` as shared infrastructure. Consumed by **PRD 042** (both modes) for all iCal parsing concerns. PRD 042 cannot reasonably ship without it; deliver alongside Phase 1.

**Recommended order**: PRD 039 first (delivers `IcalFeedParser` + full subscription functionality), then PRD 038 (upgrades loopback filter from "all FOREIGN" to full three-pass), then PRD 042. PRD 038 can also slot before PRD 039 — both directions work.

## Plan

### Phase 1 — Data model + fetcher

1. New entity `ExternalCalendarSubscription` in `rapla-core` (persists via existing storage operators). Fields per "In scope" including `interpretationMode` and `loopbackTargetMailbox`. Add to `RaplaXMLReader`/`RaplaXMLWriter` + JDBC DDL (Liquibase changelog).
2. New entity `ExternalAppointment` — read-only busy block. Holds `subscriptionId`, `externalUid` (VEVENT `UID`), `start`, `end`, `allDay`, `summary`, `description`, `location`, `recurrenceId`, `lastSeen`, `originStatus`. Indexed on `(allocatableId, start, end, originStatus)` (conflict-check filters by `originStatus = FOREIGN`).
3. New entity `AvailabilityWindow` — read-only, derived from `VAVAILABILITY` or VEVENT-as-availability. Holds `subscriptionId`, `start`, `end`, optional `priority`, `lastSeen`. Indexed on `(allocatableId, start, end)`. (No `originStatus` — availability windows never rapla-origin.)
4. New `IcalFeedParser` service in `rapla-core` (shared infrastructure). Wraps iCal4j 4.2.0 with strict-mode, size-cap input streams (10 MB), event-count cap (5000), `TRANSP:TRANSPARENT` and `X-MICROSOFT-CDO-BUSYSTATUS:FREE` filtering, recurrence expansion within caller-specified window, UTC normalisation. Returns typed result with parsed events + diagnostics. No subscription-specific logic.
5. `ExternalCalendarFetcher` service: scheduled, picks subscriptions due, conditional GET, delegates parsing to `IcalFeedParser`. Behaviour by `interpretationMode`:
   - `BUSY_TIMES`: expand recurrences → `ExternalAppointment` rows. **Skip events** with:
     - `TRANSP:TRANSPARENT` (RFC 5545 non-blocking signal)
     - `X-MICROSOFT-CDO-BUSYSTATUS:FREE` (Microsoft extension; Exchange/Outlook emit on every published VEVENT)
   - Other `X-MICROSOFT-CDO-BUSYSTATUS` values (`TENTATIVE`, `BUSY`, `OOF`, `WORKING_ELSEWHERE`) all count as busy. Tentative is edge-case but safer-as-busy (planner can override one conflict, can't recover from overlooking a tentative commitment).
   - `AVAILABILITY_TIMES`: `VAVAILABILITY` → `AvailabilityWindow`; or `VEVENT`s as availability windows.
   - `MIXED`: parse both.
   - Upserts are delete-and-insert per subscription per entity-type for v1.
6. **Loopback filter step** (after parse, before persist): if `subscription.loopbackTargetMailbox != null`, run each `ExternalAppointment` through three-pass row-consumption match against `RaplaExportedEvent` where `targetMailbox = loopbackTargetMailbox`. Populate `originStatus`. Tier 1 is direct indexed lookup on `(targetMailbox, iCalUId)`; tiers 2/3 are bounded range scans. No measurable fetcher overhead for typical mailbox volumes (~10k exports per mailbox over 5-year history).
7. iCal4j strict-mode (inside `IcalFeedParser`): `CompatibilityHints.KEY_RELAXED_PARSING = false`, no network resolution of external `METHOD`/`URL`, hard-cap input stream at 10 MB.

### Phase 2 — Conflict integration

Two parsing paths collapse to **one constraint projection**. Conflict model never sees two flavours — it sees a single "constrained time" interval set per allocatable.

1. Extend `AllocationConflictModel` (PRD 023) with `ExternalConstraintProvider` that, for allocatable + time window `[T0, T1]`, returns union of:
   - All `ExternalAppointment` rows overlapping `[T0, T1]` **with `originStatus = FOREIGN`** (rapla-origin skipped — loopback filter at work). Indexed `(allocatableId, start, end, originStatus)` makes the filter free.
   - For each subscription with `interpretationMode IN (AVAILABILITY_TIMES, MIXED)` and at least one `AvailabilityWindow` row touching `[T0, T1]`: the *complement* of that subscription's `AvailabilityWindow` rows within `[T0, T1]`. Sub-second interval operation on small set.
2. Conflict-check path treats resulting intervals identically to a conflicting reservation — same severity, same suppress/ignore, same permission. **No** new conflict-kind enum; report tags external constraints with `originSubscriptionId` + human-readable origin string ("External calendar: Office hours") for drill-down only.
3. Conflict detector respects privacy invariant — message to non-owner says "Dr. Schmidt has an external calendar constraint at 14:00–16:00" not the event title.
4. **Empty-feed safety**: if `AVAILABILITY_TIMES` subscription has zero windows in conflict window (stale feed, parse error, legitimately empty), constraint projection for that subscription is **skipped** — otherwise every booking would conflict. Logged + surfaced as health warning. Fail-open is safe default.

### Phase 3 — Subscription management UI

1. Swing: extend `AllocatableEditUI` with "External calendars" tab — subscriptions, add/edit/delete, "Test fetch now" surfacing parse errors. Default `BUSY_ONLY` highlighted as recommended.
2. Angular (PRD 026): add section to allocatable detail page (`rapla-angular/src/app/allocatable/`) reusing OpenAPI codegen against `/api/external-calendars/*`.
3. Owner-self-service: current user linked to resource via `ownerUser` → permission passes without admin role.
4. **Owner-accessible health panel** (same UI tab, both Swing + Angular): per subscription, show last fetch, last error, parsed event count, refresh interval, and — when `loopbackTargetMailbox` is set — `originStatus` breakdown (count of `FOREIGN` / `RAPLA_ORIGIN_*` over latest fetch). "Test fetch now" writes into same panel. New endpoint `GET /api/external-calendars/{id}/health` returns this data; same auth rule (admin OR owner).

### Phase 4 — Privacy hardening + leak tests

1. `BusyOnlyProjection` as server-side projection at every controller emitting `ExternalAppointment`. Unit tests cover full-detail → busy-only round-trip.
2. `ExternalCalendarLeakTest` — AGENTS.md §12 mandatory tier-3 MockMvc. Non-admin user, mixed visible/hidden resources, mixed visibilities. Asserts byte-identical responses for visible-only subset and (separately) all-non-existent ids.
3. `AuditLogPrivacyTest` — grep-style failing CI if any log statement in `org.rapla.plugin.externalical.*` references `ExternalAppointment.summary` or `.description`.
4. Calendar SSR (PRD 030) integration test: render calendar view as non-owner with external events, parse HTML/SVG, assert no event titles anywhere.

### Phase 5 — Operations & docs

1. Admin status page: extend `/server` (StatusPageController) with per-subscription health — last fetch, last error, current event count, avg fetch latency, **and `originStatus` breakdown**. High `PROBABLE_FUZZY` count is canonical "loopback configured but UID isn't surviving the publish pipeline" indicator — admin can switch user's publish detail level to "Full details" to upgrade matches to `CONFIRMED`.
2. `docs/configuration.md` — new section "External calendar subscriptions" covering Google secret ICS, Apple iCloud, Outlook published calendar with screenshots.
3. Throttling/abuse defaults: per-subscription min interval enforced server-side at 15 min, per-deployment cap on parallel outbound fetches (default 4 concurrent), HTTP timeout 30 s, exponential backoff up to 3 attempts on transient failure.

## Tests

Per AGENTS.md §10 pyramid.

**Tier 1 (pure unit, `rapla-core`):**

- `IcalParserMappingTest` — `VEVENT` → `ExternalAppointment` mapping: simple events, all-day, recurring with `RRULE`, with `EXDATE`, with `RECURRENCE-ID` overrides, non-UTC timezones, DST-crossing.
- `BusyStatusFilterTest` — fetcher's skip path. Assert `TRANSP:TRANSPARENT` skipped; `X-MICROSOFT-CDO-BUSYSTATUS:FREE` skipped; `BUSY`/`TENTATIVE`/`OOF`/`WORKING_ELSEWHERE`/missing all produce rows.
- `VAvailabilityParserTest` — RFC 7953 `VAVAILABILITY` → `AvailabilityWindow`: single block, nested `AVAILABLE` with recurring weekly windows, priority on overlapping windows, `BUSYTYPE`, RFC 7953 spec fixtures.
- `VEventAsAvailabilityTest` — `AVAILABILITY_TIMES` + only `VEVENT`s → each becomes `AvailabilityWindow`.
- `BusyOnlyProjectionTest` — full-detail → busy-only DTO round-trip; assert summary/description/location/url unset.
- `RecurrenceExpansionWindowTest` — `RRULE COUNT=N` and `UNTIL` expanded only within lookahead; outside skipped.

**Tier 2 (facade, no Spring):**

- `ExternalCalendarFetcherTest` — drives `WireMock` HTTP server serving recorded ICS payloads (Google, Apple, Outlook real-world samples). Initial fetch, conditional GET hit (304), miss (200 + etag), 429 with `Retry-After`, malformed ICS (rejected, error recorded), oversized (truncated), HTML-not-ICS (rejected).
- `AllocationConflictModelExternalTest` — exercises constraint projection. All cases assert one conflict shape (`source=EXTERNAL` + `originSubscriptionId`):
  - `BUSY_TIMES` overlapping busy → conflict
  - `BUSY_TIMES` not overlapping → no conflict
  - `AVAILABILITY_TIMES` inside window → no conflict
  - `AVAILABILITY_TIMES` outside every window → conflict (complement)
  - `AVAILABILITY_TIMES` with zero windows (stale) → no conflict (fail-open, projection skipped, warn logged)
  - `MIXED` with VAVAILABILITY + busy VEVENT inside window → conflict on bookings overlapping busy VEVENT
  - Two subscriptions on same allocatable (busy + availability) → conflicts unioned
  - Rapla-origin (originStatus != FOREIGN) excluded from projection → booking overlapping → no conflict
- `LoopbackFilterTest` — three-pass row-consumption. Each case asserts produced `originStatus` and row-consumption:
  - `loopbackTargetMailbox = null` → all `FOREIGN`, no DB lookup
  - UID matches → `RAPLA_ORIGIN_CONFIRMED`, row consumed
  - UID misses, (start,end) exact-match → `RAPLA_ORIGIN_PROBABLE_EXACT`, consumed
  - (start,end) within ±5 min → `RAPLA_ORIGIN_PROBABLE_FUZZY`, consumed
  - (start,end) >5 min off all rows → `FOREIGN` (drift case)
  - **Row-consumption (false-positive guard)**: two VEVENTs at same time, A with matching UID, B with different. Assert A=`CONFIRMED` + consumes; B=`FOREIGN` (would have wrongly fuzzy-matched without consumption).
  - **Closest-fuzzy assignment**: three VEVENTs without UIDs at +1/+3/+5min from one unconsumed row. +1min wins; other two = `FOREIGN`.
  - **Tombstone window**: VEVENT matches tombstoned row within window → still `RAPLA_ORIGIN_PROBABLE_EXACT`, consumed.
  - **Tombstone expired**: past expiry → no match (hard-deleted) → `FOREIGN`.
  - **Cross-timezone**: UTC-stored export; VEVENT with `DTSTART;TZID=America/Los_Angeles:` same instant → normalized → `RAPLA_ORIGIN_PROBABLE_EXACT`.
  - **Cross-mailbox isolation**: subscription with `loopbackTargetMailbox = a@x` only matches rows where `targetMailbox = a@x`; `b@x` rows not candidates.
  - **Recurring series, no exceptions**: rapla exported weekly meeting (one row, master UID). Inbound has one master VEVENT. After expansion, 50 occurrence rows. Master VEVENT matches Pass 1 → CONFIRMED; all 50 occurrences inherit.
  - **Recurring series with exception**: weekly meeting; planner moved one occurrence by 30 min. Feed has master VEVENT(UID=M) + exception VEVENT(UID=M, RECURRENCE-ID=...). One row. Both VEVENTs match same row in Pass 1 → both CONFIRMED; row consumed for Pass 2/3 but same-UID matches not blocked.
  - **DB failure**: `RaplaExportedEvent` query throws → all `FOREIGN`, no crash, error logged once per fetch.

**Tier 3 (Spring slice, `rapla-app`):**

- `ExternalCalendarControllerTest` — CRUD on `/api/external-calendars/*` with owner/admin/random perspectives. Permission boundaries. Includes `GET /health`: admin and owner see full data; random user gets reflexive 404-or-403 per AGENTS.md §12.
- `ExternalCalendarLeakTest` — mandatory AGENTS.md §12. Non-admin, mixed visible/hidden ids, response byte-identical to visible-only subset, including for `BUSY_ONLY`.

**Tier 4 (full E2E, `rapla-app`, `@Tag("e2e")`):**

- `EndToEndExternalCalendarTest` — `@SpringBootTest(webEnvironment=RANDOM_PORT)`. Embedded WireMock serving fixed ICS, register via API, wait for scheduler tick, query calendar view, assert external busy blocks with correct privacy projection.

**Tier 6 (Angular component, `rapla-angular`):**

- `external-calendars-panel.component.spec.ts` — TestBed: add/remove/edit, error states (network down, invalid URL, parse failure), visibility selector with `BUSY_ONLY` highlighted, **health view** (last fetch timestamp, error display, `originStatus` breakdown bar chart when loopback enabled, "Test fetch now" updates panel in place).

## Open Questions

- **Storage of recurring-event expansion.** *Resolution: materialise every occurrence in the lookahead window for v1 (matches how rapla stores its own recurring `Appointment` instances); reconsider if storage volume matters.*
- **Fetch scheduling — per subscription or per resource?** *Resolution: each subscription independent on its own interval + global concurrency cap (4).*
- **Feed unreachable long-term — fail-open or fail-closed?** *Resolution: fail-open (bookings go through) with prominent UI warning. Conflict model is advisory.*
- **Linking external UID stability.** Some providers (early Outlook) violate stable-UID convention; using `(subscription, externalUid)` as PK makes unstable UIDs look like delete+create on every fetch. *Resolution: accept the churn; add `dtstart-based fingerprint` fallback if a customer hits a pathological case.*
- **Notify resource owner of fetch failures?** *Resolution: out of scope for v1.*
- **URL-level fetch caching when multiple resources share a feed.** Deferred to Phase 4.
- **Real-world `VAVAILABILITY` emitter coverage.** RFC 7953 supported by Apple Calendar / FastMail / Calendly (since 2024) / Cyrus IMAP; Google and Outlook do **not** emit it (as of May 2026). Realistic Dozent scenario is the `VEVENT`-as-availability sub-case. *Resolution: ship both code paths in v1; emphasise VEVENT path in docs.*
- **Surfacing origin of external constraint in conflict UI.** v1 displays origin as plain text in conflict detail; future could surface last-fetched timestamp inline.
- **Loopback fuzzy-match tolerance window.** Default ±5 minutes. Row-consumption bounds false-positive blast radius regardless. Open to adjustment after PRD 038's spike measures real drift.
- **Mailbox switched from EWS to Graph during hybrid migration?** Loopback looks up `RaplaExportedEvent` by `targetMailbox` agnostic of `backendType` — pre-cutover rows keep working until underlying reservations deleted and age out via tombstone.

## References

- iCal4j (pinned 4.2.0 in `rapla-bom/pom.xml`): https://www.ical4j.org/
- RFC 5545 — iCalendar core
- RFC 7953 — VAVAILABILITY for publishing availability
- Existing rapla (different feature — one-shot import): `rapla-core/src/main/java/org/rapla/plugin/ical/{ICalImport,ImportFromICalPlugin}.java`
- Existing rapla (sibling feature — export ICS): `rapla-core/src/main/java/org/rapla/plugin/export2ical/`
- Related PRDs: 023 (AllocationConflictModel), 024 (RepeatingRuleValidator), 030 (SSR — privacy projection applies), 038 (Graph calendar sync — defines `RaplaExportedEvent` consumed by loopback filter)
