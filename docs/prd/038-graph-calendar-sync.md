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

This PRD slots into the existing `org.rapla.plugin.exchangeconnector` plugin without re-architecting it. The key code-level facts that shape the design:

- **The target mailbox is intrinsic to the allocatable.** `SynchronisationManager.getMailbox(Allocatable)` already extracts the mailbox email from classification attributes — `exchangeMailbox` first, then any attribute annotated `KEY_EMAIL`, then `email`. No new mailbox-pointer field is needed; existing rapla deployments already populate this on allocatables that should sync.
- **Sync is keyed per-allocatable, per-mailbox.** `synchronizationBoxMap : Map<ReferenceInfo<Allocatable>, SynchronizationBox>` tracks sync state per allocatable. The reverse `allocatablesPerMailbox : Map<String, Allocatable>` is built at sync time. No per-user backend state.
- **Credentials live in `RaplaKeyStorage`, keyed by rapla user.** A small pool (typically 1–10) of rapla users hold Exchange credentials in `RaplaKeyStorage` under `EXCHANGE_USER_STORAGE`. These are **credential-carrier users**, not end users. Each carrier's underlying Exchange account has been granted delegate access to a set of shared mailboxes via Exchange's `ApplicationImpersonation` role or shared-mailbox delegation. This pool model is more centralised than per-end-user OAuth — admins manage the few credential-carrier accounts; ordinary rapla users never see auth prompts.
- **The current sync loop** (`SynchronisationManager.synchronizeMailboxes`, lines ~138–242):

  ```
  for each rapla user with stored EWS credentials:
      connect to EWS as that user
      enumerate shared mailboxes the connection can reach
      for each (mailbox, allocatable) pair where allocatable.exchangeMailbox == mailbox:
          sync rapla appointments → that mailbox's calendar via this connection
  ```

This PRD adds a parallel Graph path that targets the same `(allocatable, mailbox)` set but writes via Graph instead of EWS. The credential-pool model stays the same — `RaplaKeyStorage` gains a sibling key (`GRAPH_USER_STORAGE`) for rapla users holding Graph credentials.

## Why now

Microsoft has set a hard deadline:

- **Oct 1 2026** — EWS becomes *disabled-by-default* in Exchange Online. Tenants must explicitly allow-list to keep it alive.
- **Apr 1 2027** — EWS in Exchange Online is fully shut off. No exceptions.

Sources: [Microsoft retirement announcement](https://techcommunity.microsoft.com/blog/exchange/retirement-of-exchange-web-services-in-exchange-online/3924440), [Microsoft Learn deprecation page](https://learn.microsoft.com/en-us/exchange/clients-and-mobile-in-exchange-online/deprecation-of-ews-exchange-online).

Independent of the deadline, rapla's current Exchange connector is already partially broken for cloud customers: the underlying `com.microsoft.ews-java-api:2.0` library was archived by Microsoft, doesn't support modern OAuth2 well, and basic auth was removed from Exchange Online in October 2022. Cloud customers using rapla's Exchange plugin today either run a kludge or have already given up.

The on-prem side is **not** affected: Exchange Server SE keeps EWS indefinitely ([Microsoft Learn](https://learn.microsoft.com/en-us/exchange/client-developer/exchange-server-development)). So this is genuinely about *adding* a second backend, not migrating one.

## Scope

**In scope:**

- Extract a write-only `CalendarBackend` SPI from the existing `org.rapla.plugin.exchangeconnector.server.exchange.*` code (`AppointmentSynchronizer`, `EWSConnector`, `ExchangeAppointment`). The current EWS code becomes `EwsCalendarBackend`, an implementation behind the SPI. No behaviour change to the EWS path.
- Implement `GraphCalendarBackend` against the Graph v1.0 REST API. Three endpoints carry the entire write surface:
  - `POST /users/{mailbox}/events` — create
  - `PATCH /users/{mailbox}/events/{id}` — update
  - `DELETE /users/{mailbox}/events/{id}` — delete

  Note `/users/{mailbox}/...` not `/me/...` — rapla writes as an app-only principal acting on the target mailbox, not as the end user. `{mailbox}` is the same value `getMailbox(Allocatable)` returns today.
- All Graph POSTs/PATCHes send `Prefer: IdType="ImmutableId"` so the returned `id` is stable across calendar-move operations ([Microsoft docs](https://learn.microsoft.com/en-us/graph/outlook-immutable-id)).
- Capture both `id` (immutable form) and `iCalUId` from the Graph response into `RaplaExportedEvent`.
- A new entity `RaplaExportedEvent` recording every event rapla writes to an external calendar. **One row per rapla `Appointment`, regardless of recurrence** — recurring series get a single row storing the *master* event's identifiers, not per-occurrence rows. Fields: `appointmentId` (the rapla Appointment), `targetMailbox`, `backendType` (`EWS` | `GRAPH`), `credentialCarrierUserId` (informational — which carrier wrote it), `externalImmutableId` (master's immutable Graph `id` or EWS `ItemId`), `iCalUId` (master's iCalUId), `lastSyncedAt`, `deletedAt` (tombstone). **Indexed on `(targetMailbox, iCalUId)` and `(targetMailbox, externalImmutableId)`** — these are the keys PRD 039's loopback filter looks up by.
  - Why one row per Appointment, not per occurrence: the published iCal feed emits one master VEVENT per series with `RRULE` (RFC 5545-conformant); iCal4j expands occurrences locally in PRD 039's storage. PRD 039's loopback Pass 1 matches at the *wire* level (one master VEVENT ↔ one `RaplaExportedEvent` row); the expansion is downstream. Graph's per-occurrence `iCalUId` quirk is an API-side artifact rapla never has to deal with — at write time the POST response gives us the master's `iCalUId`, which is what survives into the iCal export.
  - Exception occurrences in iCal (VEVENTs with `RECURRENCE-ID`) share the master's UID per RFC 5545 — so they match the same `RaplaExportedEvent` row in Pass 1. PRD 039's row-consumption invariant allows multiple VEVENTs to match a single row when they share its `iCalUId`. Storage stays one-row-per-Appointment regardless.
  - Tombstone semantics: when the underlying rapla reservation is deleted, the row's `deletedAt` is set and the row is kept for a tombstone window — default `2 × max(refreshIntervalMinutes across subscriptions to this mailbox)` or 2 hours, whichever is greater. A background sweep hard-deletes rows past the tombstone. Reason: PRD 039's iCal subscription may still be serving the now-deleted event from its cache; without the tombstone the loopback filter would let it through as a phantom external constraint.
- **Per-allocatable backend selection** via a new classification attribute `exchangeBackend` (values `EWS` | `GRAPH` | omitted). When omitted, falls back to the deployment-level default (`rapla.exchange.default-backend` in `application.yml`, defaults to `EWS` for backwards compatibility). Admins set this per allocatable via the existing classification editor, or via classification-template defaults so all allocatables of a given type inherit the right choice. No new UI panel; this is data on the allocatable, not on the user.
- **Credential pool reuses `RaplaKeyStorage`.** EWS credential carriers stay in `EXCHANGE_USER_STORAGE` unchanged. Graph credential carriers store `(tenant-id, client-id, client-secret-or-cert-thumbprint)` under a new key `GRAPH_USER_STORAGE`. Same pool-of-rapla-users model, just two backends. Typical deployment: 1–3 EWS carriers + 1 Graph carrier (since one Azure AD app with `Calendars.ReadWrite.All` covers an entire tenant). ≤10 carriers total across both backends matches existing customer sizing.
- Graph throttling resilience: respect `Retry-After` headers; exponential backoff on 429; idempotency via Graph's `transactionId` field so retries don't double-create.
- A new section in `docs/configuration.md` covering Azure AD app registration steps (tenant id, client id, application permissions, admin consent) and the per-allocatable `exchangeBackend` attribute, plus the recommendation that mailbox owners publish their calendars at **"Full details"** if they intend to subscribe to those calendars in rapla (so PRD 039's UID-based loopback filter — see PRD 039 — works robustly).
- Empirical spike (Phase 1): write events via Graph, publish the same calendar at each of the three detail levels ("Full details" / "Limited details" / "Availability only"), confirm whether the returned `iCalUId` appears as `UID:` in the published iCal feed. Record the matrix in this PRD so PRD 039 knows which detail levels its UID-match path supports.

**Out of scope:**

- **Reading external events back into rapla.** Covered by PRD 039 (read-only iCal subscriptions per resource). The PRDs are deliberately split — Graph for write, iCal for read. Avoids delta-sync state machines, free/busy probing, and most of the complexity bidirectional Graph integration would have required.
- Replacing the on-prem EWS backend. The existing `EWSConnector` keeps working; modern-auth (OAuth2) on the EWS side is a separate concern (track in a follow-up if a hybrid customer needs it before Exchange SE customers get Graph hybrid coverage).
- Replacing `com.microsoft.ews-java-api:2.0` with a maintained alternative. The archived library still works for SOAP-over-basic-auth and NTLM, which is what on-prem customers use. Migration is independent of this PRD.
- The official Microsoft Graph Java SDK (`com.microsoft.graph:6.64.0`). Measured at **87 MB total with transitives** — would nearly double rapla's 45 MB fat JAR. Hand-rolled `RestClient` calls are the right shape for rapla's narrow needs (3 endpoints).
- Per-end-user OAuth flow (delegated auth-code with PKCE). The credential-pool model already used for EWS scales fine to Graph and avoids prompting every rapla user for tenant consent. If a future customer specifically wants delegated auth (e.g. so audit logs attribute writes to the actual user rather than the service principal), add it as a Phase-6 follow-up.
- `AUTO` backend auto-detection by probing. With no read endpoints to probe with, auto-detection would have to call something like `GET /users/{mailbox}` for membership — viable but more moving parts than the explicit `exchangeBackend` attribute. Track as future polish.
- Google Calendar, CalDAV, JMAP write backends. The SPI extracted here is shaped to admit them later but no implementation in this PRD. `RaplaExportedEvent` is shared infrastructure for any future write backend.
- Microsoft Places API (room mirroring — push rapla allocatables into Exchange as bookable resource mailboxes). Different problem (pull-from-rapla vs push-into-mailbox); track as future **PRD 043 (Microsoft Places mirror)** if a customer asks.
- Multi-tenant rapla (PRD 002). A multi-tenant deployment would need multiple Graph credential carriers, one per Azure tenant, but that's already supported by the pool model — no extra design work.

## Dependency weight — measured

Already on rapla's Spring Boot 4.0.6 classpath; **zero new MB for the core path**:

- `spring-web` (`RestClient`) — HTTP calls to `graph.microsoft.com`
- `spring-security-oauth2-client` — token acquisition (client-credentials flow against Azure AD)
- `jackson-databind` 3.x — Graph response DTOs

**Optional**: `com.azure:azure-identity:1.13.3` for polished `TokenCredential` abstractions (chained credential, managed identity if rapla runs on Azure). ~3 MB transitive. Worth adding if PRD 002 (multi-tenant) ever lands on Azure; not needed for v1.

**Avoided**: the official `com.microsoft.graph:microsoft-graph:6.64.0` SDK — 87 MB total (the SDK jar alone is 59 MB of Kiota-generated bindings for thousands of Graph endpoints we don't use).

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

4. **UID survival empirical spike** (half a day, against a Microsoft developer test tenant):
   - Create an event via Graph POST with `Prefer: IdType="ImmutableId"`; record the returned `id` and `iCalUId`.
   - Publish the containing calendar at "Full details" → fetch the published iCal URL → grep for `UID:` → does it match the returned `iCalUId`?
   - Repeat at "Limited details" and "Availability only".
   - Record the matrix in this PRD; the result tells PRD 039 which detail levels its UID-match path (Tier 1) supports. Microsoft's docs imply Full details preserves UID; the other two levels are unknown until tested.

   **What is *already* settled** (does not need spiking — known from public sources):
   - **Component type is always `VEVENT`**, never `VFREEBUSY`. [Slipstick documents this](https://www.slipstick.com/outlook/calendar/publishing-outlook-free-busy/): "Outlook does not export VFREEBUSY components and ignores them on import." Holds at every detail level including "Availability only" — events are published as VEVENTs with the privacy-stripped fields just empty or generic.
   - **`DTSTART` and `DTEND` are always present** — RFC 5545-required for any valid VEVENT, and they're the entire purpose of publishing busy time. The privacy redaction at "Availability only" strips subject/description/location, never times.
   - **Therefore** PRD 039's Tier 2 (time-window match) works at every Exchange publish detail level regardless of the spike outcome. The spike only determines *whether Tier 1 (UID match) is also available* at the lower detail levels — it does not affect whether the filter works at all. Worst case at "Availability only": filter operates in Tier-2-only mode, still correct.

### Phase 2 — `GraphCalendarBackend` skeleton

1. New class `org.rapla.plugin.exchangeconnector.server.graph.GraphCalendarBackend` in `rapla-server`.
2. `GraphCredentialPool` reads carrier credentials from `RaplaKeyStorage` under the new `GRAPH_USER_STORAGE` key. Each entry: `(rapla-user, tenant-id, client-id, client-secret-or-cert)`. Acquires app-only access tokens via Spring Security's `OAuth2AuthorizedClientManager` configured for the client-credentials flow. Caches tokens per `(tenant-id, client-id)`; refreshes ~5 min before expiry.
3. Routing: for a given `targetMailbox`, pick a credential carrier whose tenant covers that mailbox. v1 keeps it simple: first carrier wins (single-tenant deployments have one Graph carrier; multi-tenant deployments need an additional "which tenant for this mailbox?" rule — Phase 6 if/when needed).
4. Jackson POJOs for the Graph `event` resource subset rapla uses: `event`, `dateTimeTimeZone`, `recurrence/patternedRecurrence`, `location`, `attendee`. Hand-written (Graph's OpenAPI is too large; we want the ~6 properties we actually use).
5. Tier-2 test against `WireMock` stubbing the Graph endpoints (recorded payloads from a real test tenant): happy-path create/update/delete, 429 throttling backoff, 412 conflict handling.

### Phase 3 — Write-side implementation + `RaplaExportedEvent`

1. New entity `RaplaExportedEvent` in `rapla-core`. Storage: new JDBC table + Liquibase changelog, follow existing storage-operator pattern. Indices: `(targetMailbox, iCalUId)`, `(targetMailbox, externalImmutableId)`, `(deletedAt)` for the tombstone sweep.
2. Implement `createEvent` / `updateEvent` / `deleteEvent`:
   - All POST/PATCH calls send `Prefer: IdType="ImmutableId"`. Capture `id` + `iCalUId` from the response into `RaplaExportedEvent` (one row per rapla Appointment, storing the master's identifiers).
   - For recurring rapla appointments: write the whole series as one Graph event with `event.recurrence` set; PATCH the master's id when any part of the series changes. Graph re-derives occurrences internally. Don't issue per-occurrence Graph PATCHes — the single-row `RaplaExportedEvent` model assumes one external identity per rapla Appointment.
   - Use Graph's `transactionId` (set to a stable hash of `appointmentId + epoch-of-write`) as the idempotency key so retries on network failure don't double-create.
   - Reservation ↔ Graph `event` field mapping (Jackson POJOs in `rapla-core`):
     - `Appointment.start/end` → `event.start/end.dateTime` + timezone
     - `Reservation.name` → `event.subject`
     - `Reservation.allocatableNames` → `event.location.displayName` (room) + `event.attendees[]` (resources)
     - Recurrence: `Appointment.repeating` → `event.recurrence.pattern/range`
     - `event.body` is always written with a one-line "managed by rapla" header (see step 6 below).
3. On reservation delete: issue Graph `DELETE`, then mark the `RaplaExportedEvent` row with `deletedAt = now()`. The row survives until the tombstone sweep removes it.
4. On reservation update (start/end change): issue Graph `PATCH`, update the `RaplaExportedEvent` row's `lastSyncedAt`.
5. **Read-only contract — rapla wins on every conflict.** Events written by rapla are *informationally* read-only from the user's perspective; users may edit them in Outlook, but rapla overwrites those edits on the next sync. There is no merge logic, no notification queue, no UI for divergence in v1. Two failure paths fall out of this policy:
   - **`412 Precondition Failed` on PATCH** (user edited the event in Outlook between rapla's write and the next sync): refetch the event to get a fresh etag, re-PATCH with rapla's view. No retry beyond that single refetch. User's edits are lost — by design, per the contract.
   - **`404 Not Found` on PATCH** (user deleted the event in Outlook): treat as event-vanished. Re-create via Graph POST (new `id`, new `iCalUId`); update `RaplaExportedEvent` with the new identifiers. The event reappears in the user's Outlook with the "managed by rapla" body marker visible — clear signal that deleting from Outlook isn't how to remove a rapla booking.
6. **Body-text marker on every exported event.** Every POST/PATCH writes `event.body` with the localized equivalent of:

   ```
   Managed by rapla. Edits in Outlook will be overwritten on the next rapla sync.
   See: <link to rapla deployment, optionally a deep link to the underlying reservation>
   ```

   Users editing the event in Outlook see this header in their own view — the warning is in front of them when they make the choice. Body is preserved verbatim at "Full Details" publish (where it would leak to feed subscribers), so the body text is intentionally minimal — just the policy notice and a link.
7. Throttling: read `Retry-After` header on 429, exponential backoff up to 60 s, surface `CalendarSyncException` on persistent failure.
8. Tombstone sweep: scheduled job, runs every 10 minutes, hard-deletes `RaplaExportedEvent` rows where `deletedAt` is older than the per-mailbox tombstone window.

### Phase 4 — Routing + carrier pool wiring

1. Per-allocatable `exchangeBackend` classification attribute (values `EWS` / `GRAPH` / omitted). Read via the existing classification-attribute mechanism in `getMailbox()`-adjacent code; default to `rapla.exchange.default-backend` from `application.yml` when omitted.
2. `BackendRouter` in `rapla-server`: given an `Allocatable`, returns the right `CalendarBackend` instance — `EwsCalendarBackend` or `GraphCalendarBackend`. Lookup is O(1) by attribute. Misconfiguration (e.g. `GRAPH` set on an allocatable but no Graph credentials configured) → log warning + skip sync for that allocatable; don't crash the whole sweep.
3. Modify the existing `SynchronisationManager.synchronizeMailboxes()` loop to dispatch through `BackendRouter`. EWS-tagged allocatables follow the existing per-credential-carrier loop (shared mailbox enumeration). GRAPH-tagged allocatables take a simpler path: enumerate `allocatablesPerMailbox` for `exchangeBackend=GRAPH` entries, write each directly via Graph using the credential carrier's app-only token. No `getSharedMailboxes()` enumeration needed for Graph — `Calendars.ReadWrite.All` reaches every mailbox in the tenant by default.
4. Document the hybrid pattern: an admin tags allocatables whose mailboxes live on-prem with `exchangeBackend=EWS`, allocatables whose mailboxes live in M365 with `exchangeBackend=GRAPH`. Migration from EWS → Graph for an individual mailbox is just an attribute flip plus ensuring the Graph carrier has access.
5. Tier-3 MockMvc test exercises the routing: same allocatable with attribute `EWS` routes to `EwsCalendarBackend`; same allocatable with attribute `GRAPH` routes to `GraphCalendarBackend`; misconfigured allocatable logs warning + skips.

### Phase 5 — Azure AD setup + docs

1. Document Azure AD app-registration steps in `docs/configuration.md`:
   - Create app in Entra portal, capture tenant id + client id.
   - Generate a client secret or upload a certificate (cert preferred for production).
   - Grant **application** permission `Calendars.ReadWrite` (not delegated). Admin must consent at the tenant level.
   - Optional: scope the app's access via Application Access Policy if the deployment should only write to a subset of mailboxes.
   - In rapla: admin signs in as the chosen credential-carrier user, configures Graph credentials via the existing exchange-connector admin panel — same UI workflow as configuring EWS credentials today, just for the Graph backend.
2. Per-allocatable setup: document setting `exchangeBackend=GRAPH` on the classification (or template default), and how `getMailbox()` extracts the target email.
3. **Loopback-handling note in the user setup docs**: if a mailbox owner wants both PRD 038 (rapla writes to their Outlook calendar) AND a PRD 039 subscription to that same calendar (so rapla sees their other appointments), they should publish at **"Full details"** so PRD 039's UID-based loopback filter can identify rapla's own writes. At lower detail levels the filter falls back to time-window matching (still correct, less robust). Simpler alternative: don't subscribe to the same calendar rapla writes to.
4. **Read-only contract in user-facing docs.** The setup flow includes: "rapla writes scheduled bookings to your Outlook calendar. Treat these events as read-only — any edits you make in Outlook (title, time, location, attendees) will be overwritten the next time rapla syncs. If you need to change a booking, change it in rapla." This contract is also written into every exported event's body so users see it again at the point of editing.
5. End-to-end smoke test against a Microsoft test tenant (developer subscription, free).

## Tests

Per AGENTS.md §10 pyramid — push every test to the cheapest tier that exercises the path.

**Tier 1 (pure unit, `rapla-core`):**

- `GraphEventMapperTest` — rapla `Appointment` ↔ Graph `event` round-trip. All-day events, recurring events with exceptions, multi-day events crossing DST, events with timezones other than the deployment's default.
- `GraphRecurrenceConversionTest` — every supported `Appointment.repeating` mode → Graph `recurrence.pattern.type` + edge cases (MONTHLY same-weekday-of-month, custom yearly, terminating count vs end-date).
- `RaplaExportedEventTombstoneTest` — given a deleted reservation, the row is marked tombstoned with the correct expiry timestamp; sweep removes it only after expiry.

**Tier 2 (facade-level, no Spring, WireMock):**

- `EwsCalendarBackendTest` — confirms existing EWS path still produces identical SOAP wire calls against a fixture set (regression gate for the SPI extraction); captures `ItemId`+`iCalUid` into `ExportResult`.
- `GraphCalendarBackendTest` — drives a `WireMock` server stubbed with real Graph response payloads. Covers:
  - Create/update/delete happy path against `/users/{mailbox}/events`, with `Prefer: IdType="ImmutableId"` header asserted on every request, `id`+`iCalUId` captured into `ExportResult`.
  - **Body marker**: every POST/PATCH request body includes the "Managed by rapla" header line.
  - 429 throttling backoff with `Retry-After`.
  - Idempotency via `transactionId` (assert no double-create on retried POST).
  - **412 Precondition Failed on PATCH**: rapla refetches the event to get a fresh etag, re-PATCHes with rapla's view (overwriting any user edits). Assert exactly one refetch + one re-PATCH, no further retries; final state is rapla's view.
  - **404 Not Found on PATCH**: rapla re-creates via POST with a fresh `transactionId`; `RaplaExportedEvent` row is updated with the new `id` and `iCalUId`.

**Tier 3 (Spring slice, `rapla-app`):**

- `BackendRouterTest` — `@SpringBootTest`. Allocatables with `exchangeBackend=EWS`, `GRAPH`, and omitted (deployment default). Verifies router picks the right backend; misconfiguration (`GRAPH` set but no Graph carrier configured) logs warning and skips that allocatable without aborting the sweep.
- `GraphCarrierAuthTest` — credentials are loaded from `RaplaKeyStorage` under `GRAPH_USER_STORAGE`; the carrier-rapla-user concept survives round-trips; non-admin users cannot read another carrier's credentials (AGENTS.md §12 data-leak invariants).
- `RaplaExportedEventPersistenceTest` — round-trip the table through the JDBC operator; assert indices on `(targetMailbox, iCalUId)` and `(targetMailbox, externalImmutableId)` exist (the keys PRD 039's loopback filter looks up by).

**Tier 4 (full E2E, optional, opt-in only):**

- `GraphLiveTenantTest` — `@Tag("e2e-graph")`. Runs against a real Microsoft developer-tenant via env vars `RAPLA_GRAPH_TEST_TENANT`, `RAPLA_GRAPH_TEST_CLIENT_ID`, `RAPLA_GRAPH_TEST_USER_MAILBOX`. Excluded from default `mvn test`. **Also runs the UID-survival spike** from Phase 1 as part of CI release-prep so we catch silent Microsoft-side changes.

**Manual smoke (Phase 5 demo):**

- Fresh rapla dev server, fresh Microsoft test tenant, register Azure AD app per docs, configure a rapla user as Graph credential carrier, tag a test allocatable with `exchangeBackend=GRAPH` and a target mailbox in the tenant, book that allocatable, confirm the event appears in `https://outlook.office.com/calendar` at that mailbox within sync interval. Confirm the event's iCalUId in Graph matches the `iCalUId` in `RaplaExportedEvent`.

## Open Questions

- **Tombstone window length.** Default is `2 × max(refreshIntervalMinutes across PRD 039 subscriptions to this mailbox)` or 2 hours, whichever is greater. Rationale: a stale subscription cache can still serve a now-deleted event up to one refresh interval after the source feed update; doubling gives safety margin. *Edge case: a mailbox with no PRD 039 subscriptions has `max=0` → falls back to the 2-hour floor.*

- **Empirical UID-survival matrix.** Phase 1's spike answers this — whether `iCalUId` appears as `UID:` in the published iCal feed at each of the three detail levels. The result is what PRD 039's loopback filter relies on for its UID-match path. If "Limited details" turns out to preserve UID, PRD 039's UID match works for the typical Outlook personal-calendar publish too; if not, the time-window fallback carries that case.

- **Calendar selection within a target mailbox.** Default writes to the mailbox's primary calendar (`/users/{mailbox}/events` aliases `/users/{mailbox}/calendar/events`). A future need is "write to *this* named calendar inside the mailbox" — Graph supports `/users/{mailbox}/calendars/{calendarId}/events`. v1 writes only to primary; per-allocatable calendar id is a Phase-6 follow-up if customers ask.

- **Multi-tenant credential routing.** v1 picks the first Graph credential carrier whose tenant config matches. With multiple tenants and overlapping mailbox domains, this could route wrong. Phase-6: add a `tenant-domain` mapping on each carrier ("this carrier handles `*@contoso.com`") and route by mailbox domain.

- **Audit attribution (app-only loses per-user trail in Microsoft's logs).** App-only auth attributes all Graph writes to the service principal in Microsoft's Unified Audit Log and mailbox audit log — the rapla planner who triggered the write is not visible in Microsoft's logs. rapla's own audit log preserves the per-planner trail; cross-correlation via `reservationId` in the event body marker recovers the human chain offline. For most customers (typical universities, mid-size orgs) this is fine — rapla's logs are the system-of-record for who-triggered-what. For compliance-sensitive deployments (some EU public sector, regulated healthcare/financial) whose audit regime requires per-user attribution *inside Microsoft's logs*, a future delegated-auth flow is the right escape valve: each planner consents to `Calendars.ReadWrite` once via auth-code + PKCE; tokens stored per-rapla-user in the PRD 030 OAuth token store; writes use the planner's delegated token so Microsoft logs the planner as the auditable actor. Most of the OAuth plumbing already exists from PRDs 029 / 030 / 036; new work is the consent UI for the Graph-write scope (distinct from login scope) and a per-planner credential dispatch path in `GraphCalendarBackend`. Track as future **PRD 044: Delegated-auth Graph writes** when a real customer requests it. v1's architecture admits this cleanly: `RaplaExportedEvent.credentialCarrierUserId` is already a per-row field, and the `CalendarBackend` SPI's per-mailbox dispatch shape doesn't need to change to support per-planner credential selection — it's a backend-internal lookup.

- **`exchangeBackend` classification attribute key.** Conventional? Reuse an existing rapla classification attribute scheme? v1 introduces it as a new well-known attribute key. Worth confirming naming consistency with existing attribute keys (`exchangeMailbox`, etc.) before locking it in.

- **Allocatable as Exchange resource mailbox.** Out of scope here, but worth noting: if customer X wants their rapla allocatables to *also* be bookable Exchange resource mailboxes (so M365 users can book them from Outlook directly), that's a separate "push rapla → Places API" PRD. Microsoft Places gives us `room` / `roomList` CRUD on Graph; the integration would be one-way (rapla as source of truth) and bidirectional conflict resolution is non-trivial. Track as **PRD 043 (Microsoft Places mirror): Mirror rapla allocatables to Microsoft Places**.

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
