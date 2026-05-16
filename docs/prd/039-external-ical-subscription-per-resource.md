# PRD 039: Per-resource external iCal subscriptions for conflict awareness

**Status:** draft
**Date:** 2026-05-15

## Goal

Let a rapla resource — most importantly a **Person** resource (Dozent, room manager, equipment custodian) — be configured with one or more **external iCal subscription URLs**. rapla polls those URLs periodically, parses the events, and surfaces them as **read-only constraint markers** in conflict detection — so that booking a resource against the planner's intent (either *while* they're busy or *outside* the times they've declared available) shows a conflict, *without* the external event becoming a rapla reservation and *without* leaking the event's title or details to other users.

The feed can express the resource's schedule in either direction; the subscription declares how to interpret it:

| Interpretation mode | iCal source pattern | Conflict semantics |
|---|---|---|
| **Busy-time publishing** (default) | `VEVENT` with `TRANSP:OPAQUE` — Google/Outlook/iCloud personal calendars | Conflict when proposed booking overlaps an external event |
| **Availability publishing** | `VAVAILABILITY` (RFC 7953) OR `VEVENT`s tagged with a marker the admin specifies — Calendly, Apple's availability, FastMail's published-hours, Dozent "office hours" feeds | Conflict when proposed booking falls **outside** every external available-window |
| **Mixed** | A feed using both `VEVENT` (busy) and `VAVAILABILITY` (when-available) — RFC 7953 designed exactly for this | Conflict if either constraint is violated |

Concretely, after this PRD a rapla deployment can:

- Configure 1..N subscription URLs per allocatable (typically `text/calendar` feeds from Google Calendar's secret ICS URL, Apple iCloud public calendar, Outlook published calendar, Calendly availability URL, FastMail, Nextcloud, etc.)
- Have rapla treat Dozenten's published *availability* (when they're willing to teach) as constraints during planning — so trying to schedule a course outside their declared availability triggers a soft conflict the planner sees and can resolve
- Have rapla treat personal-calendar *busy* times the same way — overlap with private appointments triggers a conflict
- Render external constraints visually in the calendar view of the resource
- Trust that another user looking at "when is Dr. Schmidt free?" sees `busy 14:00–16:00` but *not* `Therapy session` — privacy is preserved by default
- Recover gracefully when the external feed becomes unreachable (stale-cache window, then surface a warning)

## Motivation

Two real customer pains, one technical solution:

**Pain 1 — Personal-calendar conflicts (busy direction).** A rapla user (course coordinator, room scheduler) tries to book Dr. Schmidt for a 2-hour seminar Tuesday 14:00. Dr. Schmidt has a dentist appointment 15:00–16:00 in her personal Google Calendar. rapla doesn't know — she shows as free — the booking is created, then she has to manually catch the conflict and reschedule. This is the most common cause of "rapla shows free but I'm actually busy" complaints from staff at the German university customers who are the primary rapla audience.

**Pain 2 — The availability problem (availability direction).** Dozenten (lecturers) typically tell their department, semester by semester, *when* they're available to teach — "Monday and Wednesday mornings, never Friday." Today, rapla has no model for this. The planner either keeps the schedule in a spreadsheet next to rapla, or accidentally books people outside their declared availability and has to undo. Meanwhile every Dozent already publishes their availability *somewhere* — in Calendly, in Apple Calendar's "availability", in a Google Calendar called "office hours", in FastMail. The data exists; rapla just can't see it.

By treating both as the same problem — a periodic read-only subscription to an external iCal feed, with a per-subscription declaration of how to interpret the events — one feature covers both pains. The Dozent doesn't have to learn a new tool; they keep using whatever availability publishing they already use, and rapla follows along.

External conflicts behave like every other conflict in rapla: advisory by default. The planner sees them; the existing rapla conflict UI lets them ignore or suppress per the existing permission rules. This PRD does **not** introduce a separate severity ladder for external conflicts — they ride the same conflict model and the same UI affordances that internal conflicts use today.

The existing `org.rapla.plugin.ical.ICalImport` (`POST /api/ical/import`) is **a different feature**: it's a one-shot admin import that *creates rapla reservations* from an ICS file or URL. It's the right tool for "import last semester's course schedule from a legacy system"; it's the wrong tool for "watch my private calendar for conflicts". Mixing them would be wrong on two axes:
- One-shot import writes the event titles into rapla as `Reservation.name` — visible to everyone with read on the resource. Privacy disaster for personal feeds.
- One-shot import creates rapla entities the user can edit/delete, which then drift from the source. Subscription needs the external feed to remain authoritative.

So this is genuinely a new concern, not an extension of the existing import plugin.

## Scope

**In scope:**

- A new model: `ExternalCalendarSubscription` linked to `Allocatable` (1:N). Fields: `url`, `displayName`, `enabled`, `refreshIntervalMinutes` (default 60), `visibility` (`BUSY_ONLY` | `FULL_DETAIL`), `interpretationMode` (`BUSY_TIMES` | `AVAILABILITY_TIMES` | `MIXED`), `loopbackTargetMailbox` (optional — the mailbox address whose iCal export this subscription points at; enables the loopback filter — see "Rapla-origin loopback filter" section), `lastFetched`, `lastError`, `eventCount`, `etag` / `lastModified` for conditional GETs.
- Background fetcher running on rapla's existing scheduler. Honours per-subscription `refreshIntervalMinutes`. Uses HTTP conditional GETs (`If-None-Match` / `If-Modified-Since`) to be polite to feed providers. Respects `Retry-After` on 429.
- iCal4j (already on rapla's classpath as 4.2.0) parses the feed. Recurring `VEVENT`s with exceptions are expanded into instances within the fetcher's lookahead window (configurable, default 365 days forward + 30 days back). `VAVAILABILITY` components (RFC 7953) are parsed and stored as `AvailabilityWindow` records — see "Interpretation modes" section.
- Parsed busy events stored as `ExternalAppointment` records — a new entity that is NOT a `Reservation`. Read-only, server-managed, never editable through any UI. Carries an `originStatus` field (`FOREIGN` | `RAPLA_ORIGIN_CONFIRMED` | `RAPLA_ORIGIN_PROBABLE_EXACT` | `RAPLA_ORIGIN_PROBABLE_FUZZY`) populated by the loopback filter — see "Rapla-origin loopback filter" section.
- Parsed availability windows (from `VAVAILABILITY` or `VEVENT`-as-availability) stored as `AvailabilityWindow` records — also read-only, server-managed.
- **Rapla-origin loopback filter**: at parse time, every `ExternalAppointment` is matched against the `RaplaExportedEvent` table from PRD 038 to identify rapla's own writes coming back through the subscription. Rapla-origin events are recorded with the appropriate `originStatus` and **excluded from conflict detection** so rapla doesn't conflict with itself. See the dedicated section below for the matching algorithm and edge cases.
- Calendar view: `ExternalAppointment` instances render as visually-distinct busy blocks (e.g. diagonal-hatched fill, no title for non-privileged users). Existing `RaplaBuilder` extension point handles the rendering tier.
- `AllocationConflictModel` (PRD 023) extended via a single **constraint projection**: at conflict-check time, both `ExternalAppointment` rows and the *complement* of `AvailabilityWindow` rows project into a unified "constrained time" set per allocatable. The existing conflict path treats those constraints like any other conflicting reservation — same severity model, same suppress/ignore affordances, same permission rules. **No new conflict-severity enum is introduced.**
- Admin UI: subscription management lives in the existing allocatable edit panel (`AllocatableEditUI` in Swing, equivalent in Angular). Add/remove/test subscriptions. "Test fetch now" button shows count + parse errors.
- User self-service: the resource owner (a Person whose user account is linked to the allocatable) can manage subscriptions for *their own* resource without needing admin permission. Other users cannot.
- **Owner-accessible subscription health.** A Dozent who configures a subscription for their own allocatable can also *see its health* for that allocatable — last fetch, last error, event count, refresh interval, `originStatus` breakdown for loopback-filter-enabled subscriptions — through the existing PRD 026 Angular allocatable detail page (and the Swing equivalent). Without this, the canonical owner can't diagnose "my conflicts aren't showing up" or "all my events are being filtered as rapla-origin" without an admin. Same data the admin status page (Phase 5) exposes, scoped to allocatables the viewer owns. Cross-allocatable / deployment-wide health stays admin-only.
- REST endpoints under `/api/external-calendars/*` (per AGENTS.md §15):
  - `GET /api/external-calendars?resource={id}` — list subscriptions for a resource (admin OR owner)
  - `POST /api/external-calendars` — create subscription (admin OR owner)
  - `PUT /api/external-calendars/{id}` — update
  - `DELETE /api/external-calendars/{id}` — delete
  - `POST /api/external-calendars/{id}/refresh` — trigger immediate fetch (admin OR owner)
  - `GET /api/external-calendars/{id}/health` — last fetch, last error, event count, `originStatus` breakdown (admin OR owner — see "Owner-accessible subscription health" above)
- iCal4j configured strictly: disable any iCal4j features that resolve external entities or follow nested URLs (defense in depth against malicious feeds). Hard cap on feed size (default 10 MB) and event count (default 5000 per fetch).
- Privacy invariants — see "Privacy & leak considerations" section below.
- **Shared infrastructure for the future iCal-import rewrite.** The iCal parsing surface — iCal4j 4.2.0 strict-mode configuration, size and event-count caps, `BUSYSTATUS` / `TRANSP` filtering, recurrence expansion within a lookahead window, timezone normalisation to UTC, parse-error reporting — is built as a standalone `IcalFeedParser` service in `rapla-core`, not buried in the `ExternalCalendarFetcher`. Rationale: the existing one-shot import plugin (`org.rapla.plugin.ical.ICalImport`) is broken in production since the Date→LocalDateTime migration; PRD 042 rewrites it (Mode 1 = one-time import, Mode 2 = read-only sync as managed Reservations) on top of `IcalFeedParser`. Building the parser as a service here keeps PRD 042 from reinventing iCal handling. The subscription-specific concerns (loopback filter, privacy projection, per-allocatable storage) stay in the fetcher; the parser is purely "iCal bytes → typed structured events."

**Out of scope:**

- Two-way sync (rapla → external). That's PRD 038's job; this PRD is **read-only ICS → rapla**.
- Authentication for external feeds (HTTP Basic, OAuth-protected feeds). v1 supports only public/secret-URL feeds (the URL itself is the secret — Google Calendar's "secret ICS URL", Apple's public iCloud URL). HTTP Basic via URL credentials is an easy follow-up; full OAuth-protected feeds (e.g. Microsoft 365 ICS endpoints behind tenant auth) belong in PRD 038's territory anyway.
- CalDAV. CalDAV gives you the same data plus write semantics; if read-only access via ICS subscription is enough for the conflict-awareness use case, CalDAV is overkill. Track as future work if customers ask.
- Promoting external events to rapla reservations. The existing `ICalImport` already does this for the one-shot case; we don't combine the two.
- Per-user (rapla user, not resource) subscriptions. A subscription belongs to a *resource*, not a user. A user with multiple personal calendars who also has 3 rapla allocatables associated with them registers 3+ subscriptions, one per allocatable, even if the underlying calendar URLs are duplicates. Rationale: the conflict model is per-resource, not per-user; deduping fetches at the URL level is a Phase-4 optimisation.
- "Importing" historic events as a one-time backfill (use `ICalImport` for that).

## Interpretation modes — busy vs availability

A subscription's `interpretationMode` field tells the conflict detector how to read the feed's events. Three modes:

### `BUSY_TIMES` (default)

Every `VEVENT` is treated as "the resource is busy for this window." Standard interpretation, what 99% of personal Google/Outlook/iCloud feeds contain. `TRANSP:OPAQUE` events count; `TRANSP:TRANSPARENT` events are skipped (per the iCal spec they explicitly do not block time).

Conflict rule: proposed rapla booking overlaps any busy block → conflict.

### `AVAILABILITY_TIMES`

The feed publishes *when the resource is available* (Dozent office hours, Calendly availability slots). Implementation has two sub-cases depending on what the feed uses:

1. **RFC 7953 `VAVAILABILITY` components** — the standards-correct form. Each `VAVAILABILITY` has nested `AVAILABLE` sub-components describing recurring available windows. Calendly, Apple Calendar (since macOS 13), FastMail, and Cyrus IMAP emit this.
2. **`VEVENT`-as-availability** — older convention where availability is published as ordinary events on a dedicated calendar. The admin marks the subscription `AVAILABILITY_TIMES` and rapla treats every `VEVENT` in the feed as an *available* window rather than a busy one.

Conflict rule: proposed rapla booking that is NOT fully contained in any `AvailabilityWindow` → conflict.

### `MIXED`

The feed contains both kinds (RFC 7953 was explicitly designed for this — a single calendar publishes `VAVAILABILITY` defining the normal availability *plus* individual `VEVENT` exceptions for busy times within those windows). Conflict rule: a proposed booking that is either outside availability OR overlaps a busy block triggers a conflict.

### Storage shape — two entities, one conflict projection

Two related entities sit alongside `ExternalCalendarSubscription`:

- `ExternalAppointment` — a busy block. Holds start/end + (privacy-projected) summary/description/location.
- `AvailabilityWindow` — an available window. Holds start/end + (optionally) the underlying `VAVAILABILITY` priority field (RFC 7953 supports overlapping windows with priority for tie-breaking; v1 ignores priority and treats all windows equally).

Both are server-managed, read-only to the world, indexed on `(allocatableId, start, end)`. Different parsing paths produce them but they are NOT polymorphic at the conflict layer — see "Phase 2" for the unified constraint projection.

### Conflict severity

External conflicts inherit rapla's existing severity model. rapla conflicts are advisory by default, suppressable manually, and may be elevated to non-ignorable via the existing permission system at the deployment level. This PRD does not add a per-subscription `conflictBehaviour` field — the same controls that govern internal conflicts govern external ones. Personal calendar feeds and department holiday calendars surface conflicts identically; the *response* to those conflicts is what differs, and that's a planner choice on a case-by-case basis, not a per-subscription policy.

## Rapla-origin loopback filter

When a customer configures both **PRD 038** (rapla writes bookings into a mailbox via Graph or EWS) AND a PRD 039 subscription pointing at that same mailbox's published iCal URL, rapla's own writes loop back and would naively appear as external constraints — phantom conflicts where rapla conflicts with itself. The loopback filter identifies these self-writes and excludes them from conflict detection.

The filter is opt-in per subscription via the `loopbackTargetMailbox` field: if set to a mailbox address `M` (e.g. `dr.schmidt@uni.de`), the fetcher treats events on this subscription as candidates for matching against PRD 038's `RaplaExportedEvent` rows where `targetMailbox = M`. If unset (default), the subscription is treated as fully foreign — no filtering applied. The owner sets this when they tell rapla "this iCal feed is the published view of mailbox M, which rapla is also writing to."

Why mailbox-keyed and not user-keyed: PRD 038's writes target a *mailbox* (the value `SynchronisationManager.getMailbox(Allocatable)` returns), not the rapla planner who triggered the write. The rapla user who configured the subscription is irrelevant to whether a given iCal event is one rapla wrote — what matters is whether rapla wrote *to this mailbox*.

### Three-pass row-consumption algorithm

The filter runs as three ordered passes over the parsed VEVENTs in a fetch, with **row consumption**: a `RaplaExportedEvent` row can match multiple inbound VEVENTs *only if they share its `iCalUId`* (which happens for recurring series — the master VEVENT and any exception VEVENTs all share the master's UID per RFC 5545). Once Pass 1 has matched a row via UID, the row is considered consumed for Pass 2/3 — preventing the false-positive case where a real personal event with a *different* UID at the same time would otherwise be wrongly fuzzy-matched. Higher-confidence passes have first claim; lower-confidence passes only consider rows not yet consumed.

For non-recurring events this collapses to a clean 1:1 invariant (one row, one VEVENT). For recurring series with exceptions, multiple same-UID VEVENTs all confirmed by the same row is correct — they're all rapla's own writes.

For a subscription with `loopbackTargetMailbox = M`:

1. **Pass 1 — UID match** (Tier 1, highest confidence). For every parsed VEVENT, look for a `RaplaExportedEvent` row where `row.targetMailbox == M AND row.iCalUId == VEVENT.UID`. (Note: this lookup does NOT require the row to be unconsumed — a recurring series's exception VEVENT shares the master's UID and validly matches the same row again.) If found: `originStatus = RAPLA_ORIGIN_CONFIRMED`, **mark the row consumed for Pass 2/3** (but it remains available for further Pass 1 same-UID matches), skip from conflict detection.

2. **Pass 2 — exact time match** (Tier 2). For every VEVENT not matched in Pass 1, look for an *unconsumed* `RaplaExportedEvent` row for `targetMailbox = M` where the underlying reservation's `(start, end)` exactly equals `(VEVENT.DTSTART, VEVENT.DTEND)` (normalized to UTC). If found: `originStatus = RAPLA_ORIGIN_PROBABLE_EXACT`, mark the row consumed, skip from conflict detection. Log at info.

3. **Pass 3 — fuzzy time match** (Tier 3, last resort). For each still-unmatched VEVENT, look for the closest *unconsumed* row for `targetMailbox = M` within ±5 minutes on both endpoints. Tie-breaker: smallest time-delta wins. If found: `originStatus = RAPLA_ORIGIN_PROBABLE_FUZZY`, mark the row consumed, skip from conflict detection. Log at warn. Surface in subscription health UI.

4. **Anything still unmatched** → `originStatus = FOREIGN`. Participates in conflict detection normally.

### Why row consumption matters

Without row consumption, both a rapla-owned VEVENT and a coincidentally-overlapping personal event would each match the same `RaplaExportedEvent` row by Pass 2's time check — the personal event would get filtered as a false positive, and the planner would lose visibility into a real double-booking. With row consumption, the rapla-owned VEVENT claims the row in Pass 1 (UID match) first; the personal event has no unconsumed row to match against; it falls through to FOREIGN and surfaces correctly as an external constraint.

Row consumption also makes Pass 3 (fuzzy) self-limiting. Pass 3 only fires when (a) Pass 1 failed for this VEVENT specifically (UID was stripped or never matched anything), (b) Pass 2 failed (no exact-time row), AND (c) there's still an unconsumed row within tolerance. For a subscription whose feed publishes with UIDs (Full/Limited Details on Exchange), all rapla-owned VEVENTs are claimed by Pass 1, every rapla-export row gets consumed, and Pass 3 has nothing left to draw from — no false positives at all. The fuzzy path effectively only fires in the genuinely-ambiguous "Availability Only with anonymized UIDs AND user drift" case it was designed for.

### Why three tiers, not just UID

PRD 038's Phase 1 spike will quantify it, but Microsoft's published-iCal pipeline *may* strip or synthesise the `UID:` line at lower publish-detail levels — and at "Availability only" (the privacy default most users will pick), the UID is plausibly anonymized per-fetch and useless for matching. The time-window tier rescues those cases: rapla knows it exported a reservation for `(U, allocatable)` at `[T0, T1]`, and the iCal feed surfaces a busy block at approximately that time — high confidence it's rapla's own write.

**Tier 2 is guaranteed to have data to match against** — independent of the spike's outcome — because:

- Exchange/Outlook always publishes events as `VEVENT` components, never `VFREEBUSY`. [Slipstick confirms](https://www.slipstick.com/outlook/calendar/publishing-outlook-free-busy/): "Outlook does not export VFREEBUSY components and ignores them on import." This holds at every detail level including "Availability only" — only the subject / description / location are stripped, not the structural shape.
- `DTSTART` and `DTEND` are RFC 5545-required for a valid `VEVENT` and are the entire reason the calendar is being published. They survive every privacy redaction.

So the worst plausible case at "Availability only" is "Tier 1 fails, Tier 2 still works" — never "no data to match against." If Phase 1's spike comes back showing UIDs survive at all three levels, Tier 1 is the primary path and Tier 2 is just a safety net; if UIDs are anonymized at the lower levels, Tier 2 carries those cases — same correct outcome either way.

### Edge cases

- **User manually moved a rapla-written event in Outlook** (e.g. dragged 14:00–16:00 → 14:30–16:30). Tier 1 fails (UID rewrite on move is possible). Tier 2 falls outside tolerance → `FOREIGN`. Planner sees a phantom conflict at 14:30–16:30. **Correct behaviour** — the user diverged their copy; rapla can't silently absorb that. Surface a "drifted exported event" status on the subscription so the user notices.

- **Reservation deleted in rapla, subscription cache hasn't caught up.** PRD 038 marks the `RaplaExportedEvent` row with `deletedAt = now()` but keeps it for a tombstone window (default `2 × max(refreshIntervalMinutes across the user's subscriptions)` or 2 hours, whichever is greater). The loopback filter still matches against tombstoned rows during the window, so the stale-cache phantom is suppressed. After the tombstone expires, hard-delete; if the iCal feed is still stuck, the event becomes `FOREIGN` and triggers a real conflict — at which point the staleness is bigger than the user's refresh interval and the conflict is informative ("your subscription has been stuck for ages").

- **Real personal event coincidentally at the same time as a rapla export to the same calendar.** Handled correctly by row consumption. The rapla-owned VEVENT claims its `RaplaExportedEvent` row in Pass 1 (UID match); the personal event has no unconsumed row to match against → falls through to `FOREIGN` → surfaces correctly as an external constraint. Planner sees both the internal rapla conflict and the external personal-event conflict at that time — no silent filtering of the double-booking.

- **No PRD 038 export history (`RaplaExportedEvent` empty for U)**: every event resolves to `FOREIGN`. The filter is effectively dormant — no false positives, no overhead.

- **Subscription points at a calendar rapla DOESN'T write to.** The user leaves `loopbackTargetMailbox` unset; the filter never runs; foreign events participate normally. Most subscriptions will be this case (personal Google calendar, public department holidays, etc.).

### Failure mode: filter degradation, not crash

If `RaplaExportedEvent` queries fail (DB hiccup, schema mismatch), the fetcher falls back to treating every event as `FOREIGN` — same behaviour as if the filter were disabled. Logged at error. **Never crash the fetch over a filter failure** — the subscription has to keep working even when loopback identification doesn't.

## Privacy & leak considerations

This is the highest-stakes part of the PRD. ICS feeds typically contain private appointment titles — "Therapy session", "Job interview at Acme", "Anniversary dinner". The default behaviour must protect the resource owner's privacy from other rapla users. AGENTS.md §12 applies — every endpoint, response shape, and rendering path goes through the data-leak review.

**Defaults:**

| Viewer | Visibility on `ExternalAppointment` (default `BUSY_ONLY`) |
|---|---|
| Resource owner (user linked to the allocatable) | Full detail (title, description, location, URL) |
| Admin | Full detail |
| Other rapla users with read permission on the resource | Time only (`busy 14:00–16:00`), no title, no description, no location |
| Users without read on the resource | Cannot see the resource → cannot see anything about its external events |

A subscription's `visibility` field can be flipped to `FULL_DETAIL` per subscription — appropriate for a department-wide holiday calendar or a public class schedule, never for a personal feed.

**Enforcement points:**

1. **API output filter**: `ExternalCalendarController` and any calendar-view controller returning `ExternalAppointment` runs a `BusyOnlyProjection` that strips title/description/location/URL when the requesting user is not the owner or admin and visibility is `BUSY_ONLY`. The full record never leaves the server boundary unsanitised.
2. **Existence check inheritance**: per AGENTS.md §12 rule "existence is information" — a non-readable resource's subscriptions and external events must be indistinguishable in response shape from "resource doesn't exist". The standard reflexive 404 / drop-silently pattern applies.
3. **Calendar rendering on the server (PRD 030 SSR)**: server-rendered calendar HTML/SVG for non-owner viewers must not include the external event title in DOM, alt-text, ARIA labels, hover tooltips, or print-mode output. The renderer takes the same `BusyOnlyProjection` projection — not the full entity.
4. **Audit logging**: log the URL of every subscription fetch and the count of events parsed, but **never the event titles or descriptions** even at DEBUG level. A grep-test (`AuditLogPrivacyTest`) enforces this — fails CI if any `ExternalAppointment.summary` field appears in any log path.
5. **Tier-3 leak test** (AGENTS.md §12 mandatory): non-admin user, mixed (visible / hidden / non-existent) resource ids in a calendar-view request, assert response body is byte-identical to the visible-only subset. Specifically asserts no event titles for `BUSY_ONLY` subscriptions of resources the user can read but doesn't own.

## Plan

### Phase 1 — Data model + fetcher

1. New entity `ExternalCalendarSubscription` in `rapla-core` (entity package, persists via the existing storage operators). Fields per "In scope" above including `interpretationMode` and `loopbackTargetMailbox`. Add to `RaplaXMLReader` / `RaplaXMLWriter` + JDBC DDL (Liquibase changelog, follow existing pattern).
2. New entity `ExternalAppointment` — read-only, server-managed busy block. Holds `subscriptionId`, `externalUid` (from VEVENT `UID`), `start`, `end`, `allDay`, `summary`, `description`, `location`, `recurrenceId`, `lastSeen`, `originStatus`. Indexed on `(allocatableId, start, end, originStatus)` for conflict-lookup performance (conflict check filters by `originStatus = FOREIGN`).
3. New entity `AvailabilityWindow` — read-only, derived from `VAVAILABILITY` parsing or VEVENT-as-availability. Holds `subscriptionId`, `start`, `end`, optional `priority`, `lastSeen`. Indexed on `(allocatableId, start, end)`. (No `originStatus` — availability windows are never rapla-origin since rapla doesn't export availability declarations, only bookings.)
4. New `IcalFeedParser` service in `rapla-core` (shared infrastructure — see "Shared infrastructure for the future iCal-import rewrite" in Scope). Wraps iCal4j 4.2.0 with strict-mode config, size-cap input streams (10 MB default), event-count cap (5000 default), `TRANSP:TRANSPARENT` and `X-MICROSOFT-CDO-BUSYSTATUS:FREE` filtering, recurrence expansion within a caller-specified time window, timezone normalisation to UTC. Returns a typed result type carrying parsed events + parse-error diagnostics. No subscription-specific logic (no DB writes, no loopback filter, no privacy projection — those stay in the fetcher).
5. `ExternalCalendarFetcher` service: scheduled, picks subscriptions due for refresh, performs conditional GET, delegates parsing to `IcalFeedParser`. Behaviour by `interpretationMode`:
   - `BUSY_TIMES`: expand `VEVENT` recurrences within lookahead window → `ExternalAppointment` rows. **Skip events where any of the following are set** (these communicate "this is on my calendar but doesn't block time"):
     - `TRANSP:TRANSPARENT` (RFC 5545 standard signal for non-blocking events)
     - `X-MICROSOFT-CDO-BUSYSTATUS:FREE` (Microsoft-specific extension; Exchange/Outlook emit this on every published VEVENT — `FREE` events should not trigger conflicts).
   - Other `X-MICROSOFT-CDO-BUSYSTATUS` values (`TENTATIVE`, `BUSY`, `OOF`, `WORKING_ELSEWHERE`) all count as busy. Tentative is the most edge-case — RFC treats it as bookable-but-uncertain — but treating it as busy is the safer default since the planner can override one conflict, but can't recover from silently overlooking a tentative commitment.
   - `AVAILABILITY_TIMES`: if feed contains `VAVAILABILITY` components, parse them into `AvailabilityWindow` rows. If feed contains only `VEVENT`s, treat each as an availability window (creates `AvailabilityWindow` rows directly from VEVENT start/end).
   - `MIXED`: parse both; `VAVAILABILITY` → `AvailabilityWindow`, `VEVENT` → `ExternalAppointment`.
   - Upserts are delete-and-insert per subscription per entity-type for v1 simplicity.
6. **Loopback filter step** (after parse, before persist): if `subscription.loopbackTargetMailbox != null`, run each parsed `ExternalAppointment` through the three-pass row-consumption match against `RaplaExportedEvent` (from PRD 038) where `targetMailbox = loopbackTargetMailbox`. Populate `originStatus`. Tier 1 (UID match) is a direct indexed lookup on `(targetMailbox, iCalUId)`; tiers 2/3 (time-window match) are small range scans on the same `targetMailbox` filtered by start/end + tolerance. Both bounded; no measurable fetcher overhead for typical mailbox volumes (~10k exports per mailbox over a 5-year history).
7. iCal4j strict-mode configuration (lives inside `IcalFeedParser` from step 4): `CompatibilityHints.KEY_RELAXED_PARSING = false`, no network resolution of external `METHOD` or `URL` properties, hard-cap input stream at 10 MB. Documented here for completeness; not duplicated in the fetcher.

### Phase 2 — Conflict integration

The two parsing paths (busy vs availability) collapse to **one constraint projection** at conflict-check time. The conflict model never sees two flavours of external constraint; it sees a single "constrained time" interval set per allocatable.

1. Extend `AllocationConflictModel` (PRD 023) with a `ExternalConstraintProvider` that, for a given allocatable and time window `[T0, T1]`, returns the union of:
   - All `ExternalAppointment` rows for the allocatable overlapping `[T0, T1]` **with `originStatus = FOREIGN`** (rapla-origin rows are skipped — that's the loopback filter at work). The indexed `(allocatableId, start, end, originStatus)` lookup makes this filter free.
   - For each subscription with `interpretationMode IN (AVAILABILITY_TIMES, MIXED)` and at least one `AvailabilityWindow` row touching `[T0, T1]`: the *complement* of that subscription's `AvailabilityWindow` rows within `[T0, T1]`. The inversion is a sub-second interval operation on a small set.
2. The conflict-check path treats the resulting intervals identically to a conflicting reservation — same severity, same suppress/ignore affordance, same permission flow. There is **no** new conflict-kind enum; the report payload tags external constraints with `originSubscriptionId` and a human-readable origin string (e.g. "External calendar: Office hours") purely for the drill-down view, not as a structural distinction at the model level.
3. The conflict detector respects the privacy invariant — the conflict message shown to a non-owner says "Dr. Schmidt has an external calendar constraint at 14:00–16:00" not "Dr. Schmidt has a therapy appointment" / not the title of the availability window.
4. **Empty-feed safety**: if a subscription with `AVAILABILITY_TIMES` mode has zero `AvailabilityWindow` rows in the conflict window (stale feed, parse error, or feed legitimately empty), the constraint projection for that subscription is **skipped** entirely — otherwise every booking would conflict. This is logged + surfaced as a subscription-health warning. Fail-open is the safe default per the existing Open Question on unreachable feeds.

### Phase 3 — Subscription management UI

1. Swing: extend `AllocatableEditUI` with a "External calendars" tab listing subscriptions, add/edit/delete actions, "Test fetch now" button surfacing parse errors. Visibility-defaults UI clearly labelled (default `BUSY_ONLY` is highlighted as recommended).
2. Angular (PRD 026): add a section to the allocatable detail page (`rapla-angular/src/app/allocatable/`) reusing the OpenAPI codegen against `/api/external-calendars/*`.
3. Owner-self-service: if the current user is linked to the resource via its `ownerUser` relation (existing rapla mechanism), permission check passes without admin role.
4. **Owner-accessible health panel** (same UI tab as subscription management, both Swing + Angular): for each subscription on this allocatable, show last fetch timestamp, last error (if any), parsed event count, refresh interval, and — when `loopbackTargetMailbox` is set — the `originStatus` breakdown (count of `FOREIGN` / `RAPLA_ORIGIN_CONFIRMED` / `RAPLA_ORIGIN_PROBABLE_EXACT` / `RAPLA_ORIGIN_PROBABLE_FUZZY` over the most recent fetch). "Test fetch now" button writes its result into the same panel. New REST endpoint `GET /api/external-calendars/{id}/health` returns this data; same auth rule as the rest of `/api/external-calendars/*` (admin OR owner of the resource the subscription is attached to).

### Phase 4 — Privacy hardening + leak tests

1. `BusyOnlyProjection` introduced as a server-side projection applied at every controller that emits `ExternalAppointment` in any form. Unit tests cover full-detail → busy-only round-trip.
2. `ExternalCalendarLeakTest` — the AGENTS.md §12 mandatory tier-3 MockMvc test. Non-admin user, mixed visible/hidden resources, mixed subscription visibilities. Asserts byte-identical responses for the visible-only subset and (separately) all-non-existent ids.
3. `AuditLogPrivacyTest` — grep-style test failing CI if any log statement in `org.rapla.plugin.externalical.*` references `ExternalAppointment.summary` or `.description`.
4. Calendar SSR (PRD 030) integration test: render a calendar view as a non-owner viewer with external events present, parse the HTML/SVG output, assert no event titles appear anywhere in the document tree.

### Phase 5 — Operations & docs

1. Admin status page: extend `/server` (StatusPageController) with a section showing per-subscription health — last fetch, last error, current event count, average fetch latency, **and `originStatus` breakdown** (count of `FOREIGN` / `RAPLA_ORIGIN_CONFIRMED` / `RAPLA_ORIGIN_PROBABLE_*` events). Useful for diagnosing both "why don't I see Dr. Schmidt's appointments?" and "why are so many events being flagged as rapla-origin? (probable misconfiguration)". A high `PROBABLE_FUZZY` count is the canonical "loopback configured but UID isn't surviving the publish pipeline" indicator — admin can switch the user's publish detail level to "Full details" to upgrade matches to `CONFIRMED`.
2. `docs/configuration.md` — new section "External calendar subscriptions" covering setup steps for the three most common providers (Google Calendar secret ICS URL, Apple iCloud, Outlook published calendar) with screenshots.
3. Throttling / abuse defaults: per-subscription minimum refresh interval enforced server-side at 15 minutes (no spamming Google), per-deployment global cap on parallel outbound fetches (default 4 concurrent), HTTP timeout 30 s, retry with exponential backoff up to 3 attempts on transient failures.

## Tests

Per AGENTS.md §10 pyramid.

**Tier 1 (pure unit, `rapla-core`):**

- `IcalParserMappingTest` — iCal `VEVENT` → `ExternalAppointment` mapping for: simple events, all-day events, recurring events with `RRULE`, recurring events with `EXDATE`, recurring events with `RECURRENCE-ID` overrides, events in non-UTC timezones, events crossing DST.
- `BusyStatusFilterTest` — fetcher's "non-blocking events" skip path. Assert that VEVENTs with `TRANSP:TRANSPARENT` are skipped (no `ExternalAppointment` row created). Assert that VEVENTs with `X-MICROSOFT-CDO-BUSYSTATUS:FREE` are skipped. Assert that VEVENTs with `X-MICROSOFT-CDO-BUSYSTATUS` of `BUSY`, `TENTATIVE`, `OOF`, `WORKING_ELSEWHERE`, or missing field all produce `ExternalAppointment` rows.
- `VAvailabilityParserTest` — RFC 7953 `VAVAILABILITY` → `AvailabilityWindow` mapping. Covers: a single available block with `DTSTART/DTEND`, nested `AVAILABLE` sub-components with recurring weekly windows ("Mon/Wed 09:00–17:00"), priority field on overlapping windows, `BUSYTYPE` field handling, RFC 7953 example fixtures from the spec.
- `VEventAsAvailabilityTest` — when `interpretationMode = AVAILABILITY_TIMES` and the feed contains only `VEVENT`s, each VEVENT becomes an `AvailabilityWindow`.
- `BusyOnlyProjectionTest` — full-detail entity → busy-only DTO round-trip; assert `summary` / `description` / `location` / `url` fields are unset post-projection.
- `RecurrenceExpansionWindowTest` — events with `RRULE COUNT=N` and `UNTIL` are expanded only within the lookahead window; events outside the window are skipped.

**Tier 2 (facade, no Spring):**

- `ExternalCalendarFetcherTest` — drives a `WireMock` HTTP server serving recorded ICS payloads (Google, Apple, Outlook real-world samples). Covers initial fetch, conditional GET hit (304 Not Modified), conditional GET miss (200 OK with new etag), 429 throttling with `Retry-After`, malformed ICS (rejected gracefully, error recorded), oversized response (truncated, error recorded), URL returns HTML instead of ICS (rejected).
- `AllocationConflictModelExternalTest` — exercises the constraint projection. All cases assert one conflict shape (`source=EXTERNAL` + `originSubscriptionId`), not two:
  - `BUSY_TIMES` subscription, proposed booking overlapping a busy block → conflict
  - `BUSY_TIMES` subscription, proposed booking NOT overlapping → no conflict
  - `AVAILABILITY_TIMES` subscription, proposed booking inside an availability window → no conflict
  - `AVAILABILITY_TIMES` subscription, proposed booking outside every availability window → conflict (constraint = complement of windows)
  - `AVAILABILITY_TIMES` subscription with zero windows (stale feed) → no conflict (fail-open, projection skipped, warning logged)
  - `MIXED` subscription with both VAVAILABILITY and a busy VEVENT inside an availability window → conflict on bookings overlapping the busy VEVENT
  - Two subscriptions on the same allocatable (one busy, one availability) → conflicts unioned correctly
  - Rapla-origin `ExternalAppointment` (originStatus != FOREIGN) is excluded from the projection — booking overlapping rapla-origin event → no conflict
- `LoopbackFilterTest` — exercises the three-pass row-consumption algorithm. Each case asserts the produced `originStatus` per VEVENT and (where relevant) that rows were consumed correctly:
  - Subscription has `loopbackTargetMailbox = null` → all VEVENTs `FOREIGN`, no DB lookup
  - Single VEVENT, UID matches `RaplaExportedEvent.iCalUId` for that mailbox → `RAPLA_ORIGIN_CONFIRMED`, row consumed
  - Single VEVENT, UID doesn't match but (start, end) exact-match a live row's reservation for that mailbox → `RAPLA_ORIGIN_PROBABLE_EXACT`, row consumed
  - Single VEVENT, (start, end) within ±5 min of a live row → `RAPLA_ORIGIN_PROBABLE_FUZZY`, row consumed
  - Single VEVENT, (start, end) >5 min off all rows → `FOREIGN` (drift case)
  - **Row-consumption case (the false-positive guard)**: two VEVENTs at the same time, A with matching UID, B with different UID. Assert: A=`CONFIRMED` and consumes the row; B=`FOREIGN` (would have wrongly fuzzy-matched without row consumption).
  - **Closest-fuzzy assignment**: three VEVENTs without UIDs at offsets +1min, +3min, +5min from one unconsumed row. Assert: +1min wins fuzzy match, other two = `FOREIGN`.
  - **Tombstone window**: VEVENT matches a tombstoned row (deleted reservation, within tombstone window) → still `RAPLA_ORIGIN_PROBABLE_EXACT`, row consumed.
  - **Tombstone expired**: VEVENT matches a tombstoned row past expiry → no match (row hard-deleted) → `FOREIGN`.
  - **Cross-timezone match**: rapla export stored as UTC; VEVENT with `DTSTART;TZID=America/Los_Angeles:` representing the same instant → normalized comparison succeeds → `RAPLA_ORIGIN_PROBABLE_EXACT`.
  - **Cross-mailbox isolation**: subscription with `loopbackTargetMailbox = a@x` only matches against `RaplaExportedEvent` rows where `targetMailbox = a@x`; rows for `b@x` are not candidates even if they coincide in time.
  - **Recurring series, no exceptions**: rapla exported a weekly meeting (one `RaplaExportedEvent` row, master's iCalUId). Inbound iCal has one master VEVENT(UID=master). After iCal4j expansion, 50 `ExternalAppointment` occurrence rows in PRD 039 storage. Assert: the master VEVENT matches Pass 1 → CONFIRMED; all 50 expanded occurrences inherit `originStatus = CONFIRMED` from the parent VEVENT.
  - **Recurring series with exception**: rapla exported a weekly meeting; planner moved one occurrence by 30 min. iCal feed has master VEVENT(UID=M) + exception VEVENT(UID=M, RECURRENCE-ID=...). One `RaplaExportedEvent` row (the master). Assert: both VEVENTs match the same row in Pass 1 → both CONFIRMED; row is consumed for Pass 2/3 but Pass 1 same-UID matches are not blocked.
  - **DB failure**: `RaplaExportedEvent` query throws → every VEVENT falls back to `FOREIGN`, no crash, error logged once per fetch.

**Tier 3 (Spring slice, `rapla-app`):**

- `ExternalCalendarControllerTest` — CRUD on `/api/external-calendars/*` with owner / admin / random-user perspectives. Verifies permission boundaries. Includes `GET /health` endpoint: admin and owner both see full data; random user gets the standard 404-or-403 reflexive shape per AGENTS.md §12 "existence is information" rule (the health endpoint never reveals that a resource exists if the user can't read it).
- `ExternalCalendarLeakTest` — mandatory AGENTS.md §12 test. Non-admin user, mixed visible/hidden resource ids in a calendar-view query, assert response is byte-identical to the visible-only subset, including for `BUSY_ONLY` subscriptions.

**Tier 4 (full E2E, `rapla-app`, `@Tag("e2e")`):**

- `EndToEndExternalCalendarTest` — `@SpringBootTest(webEnvironment=RANDOM_PORT)`. Stand up an embedded WireMock serving a fixed ICS feed, register a subscription via the API, wait for the scheduler tick, query the calendar view, assert external busy blocks appear with the correct privacy projection. Tagged `e2e` and excluded from default `mvn test` (AGENTS.md §10).

**Tier 6 (Angular component, `rapla-angular`):**

- `external-calendars-panel.component.spec.ts` — TestBed-driven test of the subscription management panel: add/remove/edit, error states (network down, invalid URL, parse failure), visibility selector with default `BUSY_ONLY` highlighted, **health view** (last fetch timestamp, error display, `originStatus` breakdown bar chart when loopback is enabled, "Test fetch now" button updates the panel in place).

## Open Questions

- **Storage of recurring-event expansion.** Two options: (a) materialise every occurrence within the lookahead window into the `ExternalAppointment` table (fast queries, large rows), (b) store only the underlying VEVENT with RRULE and expand on demand at query time (small rows, query-time CPU cost). (a) is simpler and matches how rapla stores its own recurring `Appointment` instances. *Resolution candidate: (a) for v1; reconsider if storage volume becomes a real concern.*

- **Fetch scheduling — per subscription or per resource?** A resource with 3 subscriptions could trigger 3 simultaneous outbound HTTPS calls. Default behaviour: each subscription independent on its own interval; global concurrency cap (4) prevents thundering-herd. Alternative: coalesce per resource so a resource's fetches are serialised. *Resolution candidate: independent + global cap, simpler and matches user mental model.*

- **What happens when a feed is unreachable for a long time?** Options: (a) treat external conflicts as "no data, no conflict" (fail-open — bookings go through), (b) treat as "data unknown, all-day conflict" (fail-closed — bookings blocked). (a) preserves usability when Google has a hiccup; (b) is safer for HARD conflicts. *Resolution candidate: fail-open with a prominent UI warning ("Dr. Schmidt's external calendar last synced 47 hours ago — conflicts may be incomplete"). The conflict model is advisory, not a hard guarantee, and a stuck feed shouldn't block the whole department.*

- **Linking external UID stability.** A VEVENT's `UID` is supposed to be stable across iCal serialisations of the same logical event, but in practice some providers (early Outlook versions) violate this. If we use `(subscription, externalUid)` as the primary key for `ExternalAppointment`, an unstable UID looks like delete+create on every fetch. *Resolution candidate: accept the churn; fetch is bounded and the deletes/inserts are local. If a customer hits a pathological case, add a `dtstart-based fingerprint` fallback identity.*

- **Should the resource owner be notified of fetch failures?** Email or in-app notification when a subscription has failed N consecutive fetches. Useful but introduces a notification surface rapla doesn't have today. *Resolution candidate: out of scope for v1; add a Phase-6 "subscription health notifications" follow-up if customers ask.*

- **Caching at the URL level when multiple resources share the same feed.** Two rapla allocatables could legitimately reference the same Google Calendar (e.g. a manager who is also a project lead). Deduping fetches at the URL level is an obvious optimisation; deferring to a Phase-4 task because the simple per-subscription model is easier to reason about first.

- **Real-world `VAVAILABILITY` emitter coverage.** RFC 7953 is supported by Apple Calendar, FastMail, Calendly (since 2024), and Cyrus IMAP, but Google Calendar and Microsoft Outlook do **not** emit it (as of May 2026). The realistic scenario for the Dozent use case is therefore likely the `VEVENT`-as-availability sub-case for now: a Dozent maintains a Google Calendar called "Office hours" containing ordinary VEVENTs at the times they're willing to teach, and the admin marks the rapla subscription `AVAILABILITY_TIMES` to flip the interpretation. *Resolution candidate: ship both code paths (RFC 7953 parser AND VEVENT-as-availability) in v1; emphasise the VEVENT path in user docs since it works with any iCal source.*

- **Surfacing the origin of an external constraint in the conflict UI.** The model carries `originSubscriptionId` + a human-readable origin string, but the existing conflict UI (PRD 023) wasn't designed with external origins in mind. v1 displays the origin as plain text in the conflict detail view (alongside the existing "conflicting reservation" affordances) — no new UI primitive. Future work could surface the underlying subscription's last-fetched timestamp inline ("synced 12 minutes ago"), which would help the planner decide whether to trust the constraint or override it.

- **Loopback fuzzy-match tolerance window.** Default ±5 minutes for `RAPLA_ORIGIN_PROBABLE_FUZZY`. Rationale: typical Outlook DST and timezone-conversion drift produces ≤5 min skew; user-initiated edits drift more. Row-consumption (Pass 3 only fires on unconsumed rows) bounds the false-positive blast radius regardless of tolerance, so the tolerance value is no longer a critical design tension — just a tradeoff between catching small drifts vs surfacing real conflicts. Open to adjustment after PRD 038's Phase 1 spike measures real drift on a test tenant.

- **What if a mailbox has had its PRD 038 backend switched from EWS to Graph during hybrid migration?** Possible cutover scenario. The loopback filter looks up `RaplaExportedEvent` by `targetMailbox` agnostic of `backendType` — so any rapla write (past EWS, current Graph) for that mailbox is a match candidate. Correct behaviour: the mailbox transitions cleanly without phantom conflicts during the cutover window. The PRD 038 `RaplaExportedEvent` rows from before the cutover keep working as match candidates until their underlying reservations are deleted and they age out via tombstone.

## References

- iCal4j (already on rapla's classpath): https://www.ical4j.org/ (version pinned to 4.2.0 in `rapla-bom/pom.xml`)
- RFC 5545 — iCalendar core spec
- RFC 7953 — VAVAILABILITY component for publishing availability
- Existing rapla code (different feature — one-shot import): `rapla-core/src/main/java/org/rapla/plugin/ical/{ICalImport,ImportFromICalPlugin}.java`
- Existing rapla code (sibling feature — export ICS): `rapla-core/src/main/java/org/rapla/plugin/export2ical/`
- Related PRDs: 023 (AllocationConflictModel), 024 (RepeatingRuleValidator), 030 (Server-side view rendering — privacy projection applies), 038 (Graph calendar sync — write-direction sibling; defines `RaplaExportedEvent` consumed by the loopback filter)
