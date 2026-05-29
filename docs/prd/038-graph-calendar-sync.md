# PRD 038: Microsoft Graph calendar sync (Exchange Online / M365) — write-only push

**Status:** draft
**Date:** 2026-05-15

## Goal

Make rapla able to **push** bookings into the **Microsoft 365 / Exchange Online** mailbox represented by each rapla allocatable, via the Microsoft Graph REST API — alongside (not replacing) the existing EWS-based on-prem path. The integration is **write-only**: rapla creates, updates, and deletes events in the target mailbox; it does **not** read events back from Exchange.

The read direction — "show me what's in this calendar so rapla can detect conflicts" — is handled by **PRD 039** (per-resource iCal subscriptions). The mailbox owner publishes their Exchange calendar at an iCal URL and configures it as a subscription on their rapla allocatable. The two PRDs are deliberately complementary: 038 is rapla→external (Graph write), 039 is external→rapla (iCal read).

After this PRD, a rapla deployment can sync allocatable bookings into:

- **Exchange Server (SE / 2019 / 2016)** on-prem — via the existing EWS connector
- **Exchange Online / M365** — via a new `GraphCalendarBackend`
- **Hybrid customers** with a mix of on-prem and cloud mailboxes — per-allocatable backend selection via a classification-attribute flag, routing each mailbox to the correct API

## How rapla's existing sync model works (read first)

This PRD slots into `org.rapla.plugin.exchangeconnector` without re-architecting it:

- **Target mailbox is intrinsic to the allocatable** — `SynchronisationManager.getMailbox(Allocatable)` extracts mailbox email from classification attributes (`exchangeMailbox` first, then `KEY_EMAIL`-annotated, then `email`).
- **Sync keyed per-allocatable per-mailbox** — `synchronizationBoxMap` tracks state; reverse `allocatablesPerMailbox` built at sync time.
- **Credentials in `RaplaKeyStorage` under `EXCHANGE_USER_STORAGE`**, keyed by rapla user. Small pool (1–10) of credential-carrier users (not end users); each carrier's Exchange account has delegate/`ApplicationImpersonation` access to shared mailboxes. Centralised — admins manage carriers, end users see no prompts.
- **Current sync loop** (`synchronizeMailboxes` ~138–242): for each carrier → connect to EWS → enumerate shared mailboxes → for each `(mailbox, allocatable)` pair, sync appointments via that connection.

This PRD adds a parallel Graph path against the same `(allocatable, mailbox)` set. Credential pool stays the same; new sibling key `GRAPH_USER_STORAGE`.

## Why now

Microsoft hard deadlines: **Oct 1 2026** — EWS disabled-by-default in Exchange Online (allow-list required); **Apr 1 2027** — EWS in Exchange Online fully shut off. [Microsoft retirement announcement](https://techcommunity.microsoft.com/blog/exchange/retirement-of-exchange-web-services-in-exchange-online/3924440).

Rapla's current Exchange connector already partially broken for cloud: `com.microsoft.ews-java-api:2.0` is archived, no modern OAuth2; basic auth removed from Exchange Online Oct 2022. Exchange Server SE keeps EWS indefinitely — so this is *adding* a second backend, not migrating.

## Scope

**In scope:**

- Extract write-only `CalendarBackend` SPI from `org.rapla.plugin.exchangeconnector.server.exchange.*` (`AppointmentSynchronizer`, `EWSConnector`, `ExchangeAppointment`). Existing EWS code becomes `EwsCalendarBackend`. No behaviour change.
- `GraphCalendarBackend` against Graph v1.0 — three endpoints:
  - `POST /users/{mailbox}/events` / `PATCH /users/{mailbox}/events/{id}` / `DELETE /users/{mailbox}/events/{id}`. `/users/{mailbox}/...` not `/me/...` (app-only principal).
- All POST/PATCH send `Prefer: IdType="ImmutableId"` ([docs](https://learn.microsoft.com/en-us/graph/outlook-immutable-id)) — `id` stable across calendar-move.
- Capture `id` (immutable) + `iCalUId` from Graph response into `RaplaExportedEvent`.
- **New entity `RaplaExportedEvent`** — one row per rapla `Appointment` regardless of recurrence (master identifiers only). Fields: `appointmentId`, `targetMailbox`, `backendType` (`EWS`|`GRAPH`), `credentialCarrierUserId`, `externalImmutableId`, `iCalUId`, `lastSyncedAt`, `deletedAt`. Indexed on `(targetMailbox, iCalUId)` and `(targetMailbox, externalImmutableId)` — PRD 039's loopback keys.
  - One-row-per-Appointment because the published iCal feed emits one master VEVENT per series with `RRULE`; iCal4j expands locally in PRD 039. Loopback Pass 1 matches at wire level. Graph's per-occurrence `iCalUId` quirk is API-side; POST response gives the master's, which survives into iCal.
  - Exception occurrences (`RECURRENCE-ID`) share master UID per RFC 5545 — match same row in Pass 1 (PRD 039 allows multi-VEVENT-per-row when sharing `iCalUId`).
  - Tombstone: on reservation delete, set `deletedAt`; keep for `2 × max(refreshIntervalMinutes)` or 2 h floor. Background sweep hard-deletes past window. Reason: PRD 039 sub cache may still serve deleted event; loopback would let it through as phantom constraint.
- **Per-allocatable backend** via `exchangeBackend` classification attribute (`EWS`/`GRAPH`/omitted → `rapla.exchange.default-backend`, defaults `EWS`). Set via existing classification editor or template default. No new UI panel.
- **Credential pool reuses `RaplaKeyStorage`** — EWS in `EXCHANGE_USER_STORAGE` (unchanged); Graph in new `GRAPH_USER_STORAGE` (`tenant-id`, `client-id`, secret-or-cert). Typical: 1–3 EWS + 1 Graph carrier (one Azure AD app with `Calendars.ReadWrite.All` covers a tenant). ≤10 total.
- Graph throttling: `Retry-After`, exponential backoff on 429, `transactionId` idempotency.
- `docs/configuration.md`: Azure AD registration (tenant, client, app perms, admin consent), per-allocatable attribute, "Full details" recommendation for loopback-filter robustness.
- Phase 1 empirical spike: write via Graph, publish at three detail levels, record whether `iCalUId` appears as `UID:` in iCal feed. Tells PRD 039 which detail levels Tier-1 supports.

**Out of scope:**

- **Reading external events back into rapla** — that's PRD 039 (per-resource iCal subscriptions). Avoids delta-sync state machines + free/busy probing.
- Replacing the on-prem EWS backend; modern-auth OAuth2 on EWS — separate follow-up.
- Replacing `ews-java-api:2.0` — archived but works for SOAP/NTLM on-prem.
- Official Microsoft Graph Java SDK (`com.microsoft.graph:6.64.0`, 87 MB with transitives — would nearly double rapla's 45 MB fat JAR). Hand-rolled `RestClient` is right for 3 endpoints.
- Per-end-user delegated OAuth (PKCE) — credential-pool scales fine. Future PRD 044 if customer needs per-user audit attribution.
- `AUTO` backend probing — viable but more moving parts than explicit `exchangeBackend` attribute.
- Google Calendar / CalDAV / JMAP write backends — SPI shaped to admit them; no impl this PRD.
- Microsoft Places API (room mirroring) — different problem; future PRD 043 if asked.
- Multi-tenant (PRD 002) — pool model already supports multiple carriers per tenant.

## Dependency weight — measured

Already on Spring Boot 4.0.6 classpath; **zero new MB for core**: `spring-web` (`RestClient`), `spring-security-oauth2-client` (client-credentials against Azure AD), `jackson-databind` 3.x.

Optional: `com.azure:azure-identity:1.13.3` (~3 MB) for `TokenCredential` polish if PRD 002 lands on Azure.

Avoided: official `microsoft-graph:6.64.0` SDK — 87 MB (59 MB SDK jar of Kiota-generated bindings for thousands of endpoints we don't use).

## Sequencing & dependencies

PRD 038 is **upstream definer** of `RaplaExportedEvent`. Downstream consumers PRD 039 + PRD 042 (Mode 2) both use it for loopback filtering; both degrade to "everything FOREIGN" / no-filter when absent, so PRD 038 can ship before or after them.

**Within PRD 038**: Phase 1 (SPI extraction + UID survival spike) is the gate — spike's output informs PRD 039's docs but doesn't block its code.

## Plan

### Phase 1 — `CalendarBackend` SPI extraction + UID survival spike

1. Define a write-only interface in `rapla-core` (`org.rapla.plugin.exchangeconnector.CalendarBackend`):

   ```java
   public interface CalendarBackend {
       ExportResult createEvent(String targetMailbox, Appointment apt) throws CalendarSyncException;
       ExportResult updateEvent(String targetMailbox, String externalImmutableId, Appointment apt) throws CalendarSyncException;
       void deleteEvent(String targetMailbox, String externalImmutableId) throws CalendarSyncException;
   }

   public record ExportResult(String externalImmutableId, String iCalUId) {}
   ```

   `targetMailbox` is what `SynchronisationManager.getMailbox(Allocatable)` returns. No `User` parameter — auth is the backend's responsibility (it picks a credential carrier from the pool that can write to this mailbox). No `initialSync`, no `deltaSync`, no `checkFreeBusy` — the read direction is PRD 039's job.

2. Move existing `AppointmentSynchronizer` logic behind an `EwsCalendarBackend` implementing the SPI. The new class delegates to the existing per-carrier-user loop in `SynchronisationManager` for credential selection. Capture EWS's `ItemId` as `externalImmutableId` and EWS's `iCalUid` as `iCalUId` into `ExportResult`. No behaviour change to the rest of the EWS path.

3. Tier-2 facade test confirms EWS path still produces identical wire calls against a recorded fixture (use `WireMock` against the existing EWS SOAP envelopes).

4. **UID survival empirical spike** (half a day, Microsoft developer test tenant): Graph POST with `Prefer: IdType="ImmutableId"`; record returned `id`/`iCalUId`. Publish at "Full details" / "Limited details" / "Availability only" → check `UID:` in published iCal vs `iCalUId`. Result tells PRD 039 which detail levels support Tier-1 (UID match).

   **Already settled** (no spike needed): Outlook publishes VEVENT (never VFREEBUSY) at every detail level; `DTSTART`/`DTEND` always present (RFC 5545). So PRD 039 Tier 2 (time-window match) works regardless of spike outcome — the spike only determines whether Tier 1 *also* works at lower detail levels.

### Phase 2 — `GraphCalendarBackend` skeleton

1. New `org.rapla.plugin.exchangeconnector.server.graph.GraphCalendarBackend`.
2. `GraphCredentialPool` reads from `GRAPH_USER_STORAGE` (`rapla-user`, `tenant-id`, `client-id`, secret-or-cert). App-only tokens via Spring Security `OAuth2AuthorizedClientManager` (client-credentials), cached per `(tenant-id, client-id)`, refresh ~5 min before expiry.
3. Routing v1: first matching carrier wins (multi-tenant `tenant-domain` mapping → Phase 6).
4. Hand-written Jackson POJOs for the `event` subset rapla uses (Graph OpenAPI is too large).
5. Tier-2 WireMock test with real-tenant recorded payloads: happy-path CRUD, 429 backoff, 412 conflict.

### Phase 3 — Write-side implementation + `RaplaExportedEvent`

1. New entity `RaplaExportedEvent` in `rapla-core`. New JDBC table + Liquibase changelog. Indices: `(targetMailbox, iCalUId)`, `(targetMailbox, externalImmutableId)`, `(deletedAt)`.
2. Implement create/update/delete:
   - All POST/PATCH send `Prefer: IdType="ImmutableId"`. Capture `id` + `iCalUId` (one row per rapla Appointment, master identifiers).
   - Recurring: one Graph event with `event.recurrence`; PATCH master on series changes. No per-occurrence PATCHes.
   - `transactionId` = stable hash(`appointmentId + epoch-of-write`) for idempotency.
   - Field mapping: `Appointment.start/end` → `event.start/end.dateTime`+tz; `Reservation.name` → `event.subject`; `Reservation.allocatableNames` → `event.location.displayName` (room) + `event.attendees[]`; `Appointment.repeating` → `event.recurrence.pattern/range`; `event.body` carries the "managed by rapla" marker (step 6).
3. Delete: Graph `DELETE` → mark row `deletedAt = now()`; tombstone sweep removes later.
4. Update (start/end change): Graph `PATCH` → update `lastSyncedAt`.
5. **Read-only contract — rapla wins on every conflict.** Outlook-side edits are overwritten on next sync. No merge logic / notification / divergence UI in v1.
   - **`412 Precondition Failed` on PATCH**: refetch for fresh etag, re-PATCH with rapla's view. One refetch, no further retries. User edits lost by design.
   - **`404 Not Found` on PATCH**: event-vanished. Re-POST (new `id`/`iCalUId`); update row. Event reappears in Outlook with body marker — clear signal that Outlook-delete isn't how to remove a rapla booking.
6. **Body-text marker** on every POST/PATCH:

   ```
   Managed by rapla. Edits in Outlook will be overwritten on the next rapla sync.
   See: <link to rapla deployment, optionally a deep link to the underlying reservation>
   ```

   Visible to the editing user. Body preserved at "Full Details" publish (leaks to feed subscribers) — kept minimal.
7. Throttling: read `Retry-After` on 429, exponential backoff up to 60s, surface `CalendarSyncException` on persistent failure.
8. Tombstone sweep: scheduled every 10 min, hard-deletes rows past the per-mailbox window.

### Phase 4 — Routing + carrier pool wiring

1. Per-allocatable `exchangeBackend` attribute (`EWS` / `GRAPH` / omitted). Default `rapla.exchange.default-backend` from `application.yml`.
2. `BackendRouter` in `rapla-server`: O(1) lookup, returns `EwsCalendarBackend` or `GraphCalendarBackend`. Misconfig → log warning + skip allocatable; don't crash sweep.
3. Modify `synchronizeMailboxes()` to dispatch through router. EWS-tagged: existing per-carrier loop. GRAPH-tagged: simpler path — enumerate `allocatablesPerMailbox`, write directly via Graph (`Calendars.ReadWrite.All` reaches every mailbox; no `getSharedMailboxes()` needed).
4. Document hybrid pattern (on-prem `EWS` / M365 `GRAPH`, attribute flip for migration).
5. Tier-3 MockMvc routing test (EWS/GRAPH/misconfig).

### Phase 5 — Azure AD setup + docs

1. `docs/configuration.md` Azure AD steps: register app in Entra (capture tenant + client id); secret or cert (cert preferred prod); **application** permission `Calendars.ReadWrite` (not delegated, tenant-level admin consent); optional Application Access Policy to scope mailboxes. In rapla: admin configures Graph credentials via the existing exchange-connector panel.
2. Per-allocatable: `exchangeBackend=GRAPH` on classification / template default; `getMailbox()` extracts email.
3. **Loopback note**: mailbox owners who want both PRD 038 write + PRD 039 subscription to the same calendar should publish at "Full details" so PRD 039's UID-based loopback filter works robustly. Lower detail levels fall back to time-window matching.
4. **Read-only contract in user docs**: edits in Outlook are overwritten on next sync; change bookings in rapla.
5. End-to-end smoke test against Microsoft test tenant.

## Tests

Per AGENTS.md §10 pyramid.

**Tier 1 (pure unit, `rapla-core`):**
- `GraphEventMapperTest` — `Appointment` ↔ `event` round-trip (all-day, recurring with exceptions, multi-day DST, non-default tz).
- `GraphRecurrenceConversionTest` — every `repeating` mode → `recurrence.pattern.type` + edge cases (MONTHLY same-weekday, custom yearly, count vs end-date).
- `RaplaExportedEventTombstoneTest` — tombstone expiry + sweep behaviour.

**Tier 2 (facade, WireMock):**
- `EwsCalendarBackendTest` — existing EWS path still produces identical SOAP wire calls (SPI extraction regression gate); captures `ItemId`+`iCalUid`.
- `GraphCalendarBackendTest` — happy-path create/update/delete with `Prefer: IdType="ImmutableId"` asserted on every request; **body marker** on every POST/PATCH; 429 backoff via `Retry-After`; idempotency via `transactionId` (no double-create on retry); **412**: exactly one refetch + one re-PATCH, rapla's view wins; **404**: re-POST with fresh `transactionId`, row updated.

**Tier 3 (Spring slice, `rapla-app`):**
- `BackendRouterTest` — `@SpringBootTest`; EWS/GRAPH/omitted/misconfig routing.
- `GraphCarrierAuthTest` — credentials from `GRAPH_USER_STORAGE`; non-admin can't read others' (§12).
- `RaplaExportedEventPersistenceTest` — JDBC round-trip; assert indices `(targetMailbox, iCalUId)` and `(targetMailbox, externalImmutableId)` exist.

**Tier 4 (opt-in):**
- `GraphLiveTenantTest` `@Tag("e2e-graph")` — real MS developer-tenant via env vars `RAPLA_GRAPH_TEST_TENANT`/`_CLIENT_ID`/`_USER_MAILBOX`. Runs the UID-survival spike in release-prep CI to catch silent MS-side changes.

**Manual smoke (Phase 5):** dev server + test tenant + Azure AD app, configure carrier, tag allocatable `GRAPH`+mailbox, book → confirm event in `outlook.office.com/calendar` and `iCalUId` matches `RaplaExportedEvent`.

## Open Questions

- **Tombstone window length** — default `2 × max(refreshIntervalMinutes across PRD 039 subs to this mailbox)` or 2 hours floor. Doubles the stale-cache risk window. Edge case: no PRD 039 subs → 2-hour floor.
- **UID-survival matrix** — Phase 1 spike answers; determines whether PRD 039 Tier-1 (UID match) works at "Limited" / "Availability only" detail levels. Tier-2 time-window match always works.
- **Calendar selection within mailbox** — v1 writes to primary calendar only. Per-allocatable named-calendar (`/users/{mailbox}/calendars/{id}/events`) → Phase 6 if asked.
- **Multi-tenant credential routing** — v1 first-matching-carrier. Future: `tenant-domain` mapping per carrier, route by mailbox domain.
- **Audit attribution** — app-only auth loses per-user trail in Microsoft's logs (writes attributed to service principal). rapla's own audit log preserves planner trail; cross-correlation via `reservationId` in body marker recovers the chain offline. Fine for typical customers. Compliance-sensitive deployments needing per-user attribution inside MS logs → future **PRD 044: Delegated-auth Graph writes** (planner consents via PKCE, tokens in PRD 030 store, writes use delegated token). v1 architecture admits this: `RaplaExportedEvent.credentialCarrierUserId` is per-row, SPI shape unchanged.
- **`exchangeBackend` attribute key naming** — confirm consistency with `exchangeMailbox` etc.
- **Allocatables as Exchange resource mailboxes** — separate "push rapla → Places API" concern; bidirectional non-trivial. Track as **PRD 043 (Microsoft Places mirror)** if asked.

## References

- [Microsoft Graph event resource (authoritative for `id` / `iCalUId` semantics)](https://learn.microsoft.com/en-us/graph/api/resources/event)
- [Get immutable identifiers for Outlook resources (`Prefer: IdType="ImmutableId"` header)](https://learn.microsoft.com/en-us/graph/outlook-immutable-id)
- [Microsoft Graph calendar events — iCalUId Update (M365 Developer Blog)](https://devblogs.microsoft.com/microsoft365dev/microsoft-graph-calendar-events-icaluid-update/)
- [Microsoft Graph throttling guidance](https://learn.microsoft.com/en-us/graph/throttling)
- [Application Access Policies for scoped Graph mailbox access](https://learn.microsoft.com/en-us/graph/auth-limit-mailbox-access)
- [EWS retirement timeline](https://techcommunity.microsoft.com/blog/exchange/retirement-of-exchange-web-services-in-exchange-online/3924440)
- Existing rapla code (sync entry point): `rapla-server/src/main/java/org/rapla/plugin/exchangeconnector/server/SynchronisationManager.java` (especially `getMailbox(Allocatable)` and `synchronizeMailboxes()`)
- Existing rapla code (EWS backend internals): `rapla-server/src/main/java/org/rapla/plugin/exchangeconnector/server/exchange/{AppointmentSynchronizer,EWSConnector,ExchangeAppointment}.java`
- Existing rapla code (per-rapla-user credential storage): `rapla-core/src/main/java/org/rapla/server/RaplaKeyStorage.java` + `EXCHANGE_USER_STORAGE` key
- Related PRDs: 030 (Server-side view rendering — token store pattern), 036 (External IdP OAuth login), 039 (Per-resource iCal subscriptions — the read-direction sibling; consumes `RaplaExportedEvent`)
