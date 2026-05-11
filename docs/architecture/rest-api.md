# REST API reference

A catalog of every HTTP endpoint a browser client can call against
the Rapla Spring Boot server. Grouped by audience (SPA-likely,
admin panels, import/export, deprioritised) rather than by
controller, so an SPA team can read top-to-bottom and stop when
they hit "you probably don't need this."

Snapshot: 2026-05-11. Endpoints are stable but DTO field names
drift; check the source class when something doesn't deserialize.

> **The wire model for reservation save** (UpdateEvent envelope,
> Reservation / Appointment / Repeating JSON shapes) lives in
> [reservation-edit.md](reservation-edit.md). This page lists
> endpoints; that page describes payloads.

## Conventions

### Context path

All paths below are relative to
`server.servlet.context-path=/rapla`. A `POST /storage/dispatch`
hits `POST /rapla/storage/dispatch` over the wire.

### Auth header

Every endpoint requires `Authorization: Bearer <accessToken>`
unless it appears in the public list in
`rapla-server/.../spring/SecurityConfig.java:26-32`. The public
list is:

```
/auth/**            /                   /index              /server
/static/**          /*.html             /*.css              /Rapla/**
/images/**          /webclient/**       /jsclient/**        /logger/**
/ical/timezones/**  /calendar           /calendar.csv       /internal_calendar
/internal_calendar.csv  /ical           /internal_ical      /raplaclient
/raplaclient.jnlp   /dhbw/status
```

Everything else is JWT-gated (`oauth2ResourceServer.jwt`).

### JSON wire format

Jackson 3 with field-based introspection
(`rapla-core/.../rest/JacksonObjectMapperFactory.java`). The fields
on `*Impl` classes are what travels — no `@JsonProperty` rewriting.
`LocalDateTime` is ISO-8601 string, interpreted in the server's
local zone. See [reservation-edit.md §Wire model](reservation-edit.md)
for the timezone-naive caveat.

### Error envelope

`RaplaExceptionHandler` (`@RestControllerAdvice`) maps exceptions
to JSON:

```jsonc
{ "status": 401,
  "error":  "Unauthorized",
  "message": "Access only for admin users" }
```

Mappings:

| Exception | HTTP | When |
|---|---|---|
| `RaplaSecurityException` | 401 | Not logged in, expired JWT, or auth check rejected |
| `EntityNotFoundException` | 404 | Reference to an entity that doesn't exist |
| `IllegalArgumentException`, `AssertionError`, `MissingServletRequestParameterException` | 400 | Malformed request |
| `RaplaNewVersionException` | 500 (today; subject to change) | Optimistic-lock conflict — surface as "another user changed this; refresh and try again" |
| `RaplaException` (base) | 500 | Catch-all server error |

**Today** there's no dedicated 409 for version conflict —
`RaplaNewVersionException` falls through to 500. If the SPA needs
to distinguish, pattern-match on `message` (brittle) or push for
a status-code change in the advice.

### Conditional registration

Many controllers are `@ConditionalOnBean(RemoteSession.class)`,
meaning they're only registered when the server is configured for
remote access. In practice every dev environment qualifies; flag
this only if you build a stripped-down deployment.

---

## 1. Auth — `AuthController`

`@ConditionalOnProperty(rapla.file-datasources=raplafile)` — i.e.,
only when using the file backend; database backends pair with a
different controller, but the wire shape matches.

| Method | Path | Body | Returns | Auth |
|---|---|---|---|---|
| POST | `/auth/login` | `{ username, password }` | `{ accessToken, refreshToken }` | public |
| POST | `/auth/refresh` | `{ refreshToken }` | `{ accessToken, refreshToken }` | public |

The JWT contains the user id as `sub`. Tokens are HS256-signed
with the property `rapla.jwt.secret` (dev default in
`application.yml`; production must override).

The SPA pattern: on 401, attempt `/auth/refresh`; on a second 401
or a 401 from refresh itself, redirect to the login form.

---

## 2. Storage core — `RemoteStorageController`

Base path `/storage`. This is the SPA's main API surface. The
controller deliberately bundles every read and write into a small
number of high-leverage endpoints — `dispatch` for all writes,
`refresh` for incremental sync.

Source: `rapla-server/.../web/RemoteStorageController.java`.

### Reads

| Method | Path | Query / Body | Returns | Purpose |
|---|---|---|---|---|
| GET | `/storage/resources` | — | `UpdateEvent` | **Initial hydrate.** DynamicTypes, Allocatables, Categories, Periods, the calling user. Call once at app boot |
| POST | `/storage/queryAppointments` | `QueryAppointments { start, end, resourceIds, ownerIds }` | `AppointmentMap` (id → Appointment + parent Reservation refs) | Calendar viewport query |
| GET | `/storage/conflicts` | — | `List<ConflictImpl>` | Current allocation conflicts (advisory list) |
| POST | `/storage/allocatable/bindings/first` | `AllocatableBindingsRequest { allocatableIds, appointmentIds, ignoreReservationId? }` | `BindingMap` (allocatable → first conflicting binding) | Cheap "is anything blocking?" check |
| POST | `/storage/allocatable/bindings/all` | same | `List<ReservationImpl>` | Full conflict overlay payload |
| POST | `/storage/allocatable/date/next` | `NextAllocatableDateRequest { allocatableId, after, duration, … }` | `LocalDateTime` | "Find next free slot for resource X" |
| GET | `/storage/user` | `?userId=<uuid>` | `String` (username) | Display name lookup |
| POST | `/storage/entity/recursiveSync` | `SerializableReferenceInfo[]` | `UpdateEvent` | Fetch an entity + all its dependencies |
| POST | `/storage/entity/dependent` | `SerializableReferenceInfo[]` | `UpdateEvent` | Fetch entities that depend on the given ids (for safe delete) |

### Long-poll refresh

| Method | Path | Query | Returns | Purpose |
|---|---|---|---|---|
| POST | `/storage/refresh` | `?lastValidated=<opaque>` | `UpdateEvent` (delta since `lastValidated`) | Heartbeat: pick up changes from other clients |
| POST | `/storage/refreshAllEvents` | `?lastValidated=<opaque>` | `UpdateEvent` | Same but events only — used by calendar-only views |

`lastValidated` is opaque server state — echo whatever the previous
response gave you. The Swing client polls every ~30 s; pick a
cadence that matches your UX (5–30 s is reasonable).

### Writes

| Method | Path | Body | Returns | Purpose |
|---|---|---|---|---|
| POST | `/storage/dispatch` | `UpdateEvent` | `UpdateEvent` (refreshed + new conflicts) | **The only write path** for Reservation / Allocatable / DynamicType. Bundled transaction. See [reservation-edit.md](reservation-edit.md) for the envelope. |
| POST | `/storage/identifier` | `?raplaType=<type>&count=<n>` | `List<String>` | Allocate UUIDs before building a draft |
| POST | `/storage/merge` | `MergeRequest` + `?targetId=` | `UpdateEvent` | Merge two entities (typically allocatables) — admin only |
| POST | `/storage/restart` | — | void | Admin: hot-restart the server |

### User self-service

| Method | Path | Body / Query | Returns | Purpose |
|---|---|---|---|---|
| GET | `/storage/change/canchangepassword` | — | `boolean` | Check if the user can self-update (false e.g. for LDAP-backed users) |
| POST | `/storage/change/password` | `PasswordPost { oldPassword, newPassword }` | void | Self-service password change |
| POST | `/storage/change/name` | String + params | void | Update display name |
| POST | `/storage/change/email` | String | void | Initiate email change (sends confirmation) |
| POST | `/storage/confirm/email` | String (token) | void | Complete email change |

---

## 3. Bulk REST (PRD 009) — `RaplaEventsController`, `RaplaResourcesController`

Resource-style REST sugar on top of `/storage/dispatch`. Useful
for scripting and as a friendlier API for SPAs that prefer
fine-grained endpoints over the bundled `UpdateEvent`. Internally
these still drive the same operator dispatch — they're a wrapper,
not a separate code path.

### `/events`

| Method | Path | Body | Returns | Purpose |
|---|---|---|---|---|
| GET | `/events` | `?start=&end=&resources=&owners=&eventTypes=&attributeFilter=` | `List<ReservationImpl>` | Filtered query |
| GET | `/events/{id}` | — | `ReservationImpl` | Single fetch |
| POST | `/events` | `ReservationImpl` | `ReservationImpl` | Create |
| PUT | `/events` | `ReservationImpl` | `ReservationImpl` | Update (full body) |
| PATCH | `/events/{id}` | `ReservationImpl` (partial) | `ReservationImpl` | Update (sparse) |
| DELETE | `/events/{id}` | — | `boolean` | Delete |

### `/resources`

| Method | Path | Body | Returns | Purpose |
|---|---|---|---|---|
| GET | `/resources` | `?resourceTypes=&attributeFilter=` | `List<AllocatableImpl>` | Filtered query |
| GET | `/resources/{id}` | — | `AllocatableImpl` | Single fetch |
| POST | `/resources` | `AllocatableImpl` | `AllocatableImpl` | Create |
| PUT | `/resources` | `AllocatableImpl` | `AllocatableImpl` | Update |
| DELETE | `/resources/{id}` | — | void | Delete |

**Pick one style and stick with it.** Mixing `/storage/dispatch`
and `/events` in the same SPA leads to surprises around
optimistic locking (each endpoint marshals `lastChanged`
differently). The bundled-transaction model of `dispatch` is what
the Swing client uses and what's most thoroughly tested.

---

## 4. Schema & i18n

### Dynamic types — `/dynamictypes`

| Method | Path | Query | Returns |
|---|---|---|---|
| GET | `/dynamictypes` | `?classificationType=<event|resource|person>` | `List<DynamicTypeImpl>` |

The "type schema for forms" endpoint. Returns the attribute
definitions, constraints, default values, and permission templates
for each DynamicType. Cache the result; it changes only when an
admin edits a type.

See [dynamic-types.md](dynamic-types.md) for the attribute model.

### Locale — `/locale`

| Method | Path | Body / Path | Returns |
|---|---|---|---|
| GET | `/locale/{id}` | path: locale tag, `?locale=` | `LocalePackage` (message keys → translated strings) |
| POST | `/locale` | `Set<String>` (language codes) | `Map<String, Set<String>>` (lang → country list) |

The SPA fetches its message catalog from here. Server holds the
i18n properties bundles; client renders. For per-entity localized
fields (DynamicType labels, Category names), the value comes back
in the entity payload directly — `/locale` is just the static UI
strings.

### iCal timezones — `/ical/timezones` (public)

| Method | Path | Returns |
|---|---|---|
| GET | `/ical/timezones` | `List<String>` |
| GET | `/ical/timezones/default` | `String` |

Public — used by the Exchange Connector admin panel and any
iCal-aware editor.

---

## 5. Edit-time pre-checks — `ReservationEditController`

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/edit/validate-recurrence` | `RecurrenceRule` | `RecurrenceValidation` |
| POST | `/edit/check-conflicts` | `ConflictCheckRequest` | `ConflictReport` |

The SPA's `EventCheck` chain equivalent. Call these before
dispatch to surface a confirm dialog rather than letting the
server reject. Both are **advisory** — `dispatch` will succeed
with conflicts; this just lets you ask first.

See PRD 024 for the server-side edit-services rationale.

---

## 6. Calendar layout view — `CalendarViewController`

| Method | Path | Query | Returns |
|---|---|---|---|
| GET | `/calendar/view` | `?from=&to=&strategy=&groupBy=&allocatables=` | `CalendarPage` |

Server-side layout: takes a date range + strategy and returns
pre-grouped, pre-sorted blocks ready to render. Optional — the SPA
can also expand client-side using `Appointment.createBlocks`
semantics (see [reservation-edit.md §Edge cases](reservation-edit.md))
— but for complex group-by views the server-side path is much
less code on the client.

Query params:

- `from`, `to` — `LocalDate` ISO strings.
- `strategy` — layout strategy id (matches `org.rapla.components.calendarview.*Strategy`).
- `groupBy` — `GROUP_NONE`, `BY_RESOURCE`, `BY_OWNER`, `BY_EVENT_TYPE`.
- `allocatables` — comma-separated UUIDs (optional filter).

---

## 7. Client → server logging — `/logger` (public)

| Method | Path | Body | Returns | Auth |
|---|---|---|---|---|
| PUT | `/logger/{level}` | message string | void | public |

`{level}` = `error` | `warn` | `info` | `debug`. The SPA can ship
its uncaught-exception logs to the server for ops visibility.
Public so an unauthenticated client (e.g. one with an expired
session) can still report the failure.

---

## 8. Admin / config panels

These back the in-app admin UI. Most are gated on the user being
an admin (server-side check inside the handler; not enforced by
`SecurityConfig`). Treat 401 from them as "not admin" rather than
"not logged in."

### Server-driven panels (PRD 020) — `/admin/panels`

| Method | Path | Body / Query | Returns |
|---|---|---|---|
| GET | `/admin/panels` | `?scope=SYSTEM|USER` | `List<PanelSummary>` |
| GET | `/admin/panels/{id}` | — | `PanelDefinition` |
| POST | `/admin/panels/{id}/save` | `Map<String, Object>` | `PanelDefinition` |
| POST | `/admin/panels/{id}/action/{actionId}` | `Map<String, Object>` | `ActionResult` |

The server declares its admin panels declaratively; the SPA renders
the panel from the returned `PanelDefinition`. See PRD 020 for the
schema and the "field types" enum.

### Plugin config endpoints

Pattern: each plugin exposes a `/x/config` endpoint that returns
its current configuration as a `DefaultConfiguration` /
`RaplaConfiguration` blob. Admin-gated inside the handler.

| Plugin | Endpoint | Returns |
|---|---|---|
| iCal | `GET /ical/config` (admin), `GET /ical/config/default` (user) | `DefaultConfiguration` |
| Exchange | `GET /exchange/config/default`, `GET /exchange/config/timezones` | `RaplaConfiguration`, `List<String>` |
| Mail | `GET /mail/config/external`, `GET /mail/config`, `POST /mail/config?test=...` | `boolean`, `DefaultConfiguration`, void |
| JNDI | `GET /jndi`, `POST /jndi` (test) | `DefaultConfiguration`, `boolean` |

### Other admin

| Method | Path | Purpose |
|---|---|---|
| POST | `/mail/send` | Send an arbitrary email to a user (admin) |
| POST | `/archiver`, GET `/archiver`, `/archiver/backup`, `/archiver/restore` | Data retention + backup. Gated on `ArchiverService` bean. |
| POST | `/urlencryption` | Obfuscate a URL token for external sharing |

---

## 9. Import / export

### iCal export — public subscription URLs

| Method | Path | Query | Returns | Auth |
|---|---|---|---|---|
| GET, HEAD | `/ical` | `?file=&user=` | iCal file | public |
| GET, HEAD | `/internal_ical` | `?file=&user=` | iCal file | public |

These are the subscription URLs an external calendar (Google,
Outlook, Apple) hits. Public; the `file` parameter is a
server-stored named export.

### iCal import

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/ical/import` | `ICalImport.Import { content, dynamicTypeKey, ... }` | `Integer[]` (created event ids) |

### External-event import (DHBW-style CSV ingest)

`@ConditionalOnProperty(rapla.externalevents.enabled)`. Not
enabled by default; relevant only if your deployment uses the
external-event integration.

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/externaleventimport/metadata` | — | `ExternalEventImportMetadata` |
| POST | `/externaleventimport/loadEvents` | `ImportCriteria` | `ExternalEventImportResult` |
| POST | `/externaleventimport/uploadCsv` | `multipart/form-data` | `ExternalEventImportResult` |
| POST | `/externaleventimport/createReservations` | `CreateReservationsRequest` | `List<String>` |

---

## 10. Not needed by an SPA

Page-rendering controllers that serve HTML / JNLP / CSV directly.
An SPA will replace the HTML pages with client-side routes and
won't need the JNLP at all.

| Controller | Paths | Note |
|---|---|---|
| `IndexPageController` | `/`, `/index` | Landing page; SPA replaces |
| `CalendarPageController` | `/calendar`, `/calendar.csv`, `/internal_calendar`, `/internal_calendar.csv` | Legacy export widget URLs — keep working for backward compat |
| `StatusPageController` | `/server` | Server status HTML page |
| `RaplaJNLPController` | `/raplaclient`, `/raplaclient.jnlp`, `/webclient/*.jar` | Java Web Start manifests + signed jars — Swing-only |

---

## Recipes — common SPA flows in one place

### App boot

```
1. POST /auth/login                              → tokens
2. GET  /storage/resources                       → cache: types, allocatables, periods, categories, self
3. GET  /dynamictypes                            → form schemas
4. GET  /locale/<userLocale>                     → message catalog
5. setInterval(20s) → POST /storage/refresh      → incremental updates
```

### Open the calendar viewport

```
6. POST /storage/queryAppointments { start, end, resourceIds }
                                                 → AppointmentMap
   (or GET /calendar/view if using server-side layout)
```

### Create a reservation

```
7. POST /storage/identifier { raplaType: "reservation", count: 1 }
                                                 → [uuid]
8. POST /storage/identifier { raplaType: "appointment", count: N }
                                                 → [uuid, ...]
9. (optional) POST /storage/allocatable/bindings/all { ... }
                                                 → advisory conflict list
10. POST /storage/dispatch { reservations: [draft] }
                                                 → refreshed entity + conflicts
```

### Edit an existing reservation

```
11. POST /storage/refresh                        → fresh copy in cache
12. clone client-side                            → mutable draft
13. POST /storage/dispatch { reservations: [edited] }
                                                 → 200, or RaplaNewVersionException → refresh-and-retry dialog
```

### Logout

```
14. drop tokens locally; no server endpoint needed (JWT is stateless)
```

---

## Known gaps (May 2026)

- **No 409 for version conflict.** `RaplaNewVersionException`
  currently maps to 500. Distinguishing it requires inspecting the
  error message until the advice is updated.
- **No server push.** The refresh model is poll-only. Server-Sent
  Events / WebSocket would reduce latency but isn't on any active
  PRD.
- **No per-endpoint OpenAPI / Swagger.** This doc is the closest
  thing. If the SPA wants a generated TS client, it has to read
  the controller signatures directly.
- **DTO drift.** Some bulk-REST DTOs (e.g.
  `ExternalEventImportMetadata`) live in plugin packages and aren't
  exported via `rapla-core`. Verify the actual JSON shape with a
  one-off curl before assuming.

---

## See also

- [reservation-edit.md](reservation-edit.md) — wire model details
  (UpdateEvent / Reservation / Appointment JSON) and the edge-case
  reference
- [permissions.md](permissions.md) — what 401 vs 403 means here
- [flows.md](flows.md) — store / dispatch / refresh internals
- [dynamic-types.md](dynamic-types.md) — DynamicType / Attribute /
  Classification model
- [`../prd/026-angular-frontend.md`](../prd/026-angular-frontend.md)
  — SPA-migration scoping and open questions
- [`../prd/009-server-bulk-storage-rest-api.md`](../prd/009-server-bulk-storage-rest-api.md)
  — rationale for the `/events` and `/resources` resource-style API
- [`../prd/020-server-driven-admin-panels.md`](../prd/020-server-driven-admin-panels.md)
  — rationale for `/admin/panels`
