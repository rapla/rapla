# Exchange Connector – Code Flow & Plugin Architecture

This document traces the full code flow of the Exchange Connector plugin, from startup through scheduled synchronisation to the DHBW-specific configuration layer.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Package Layout](#2-package-layout)
3. [Configuration](#3-configuration)
4. [Startup & Scheduling](#4-startup--scheduling)
5. [Mailbox Discovery (60-minute cycle)](#5-mailbox-discovery-60-minute-cycle)
6. [Sync Cycle (6-second cycle)](#6-sync-cycle-6-second-cycle)
7. [Task Model](#7-task-model)
8. [EWS Connection Layer](#8-ews-connection-layer)
9. [Appointment Synchronisation](#9-appointment-synchronisation)
10. [Persistent Task Storage](#10-persistent-task-storage)
11. [REST API](#11-rest-api)
12. [DHBW Extension](#12-dhbw-extension)
13. [Data Flow Diagram](#13-data-flow-diagram)
14. [Error Handling & Retries](#14-error-handling--retries)
15. [Key Constants & Tuning Parameters](#15-key-constants--tuning-parameters)

---

## 1. Overview

The Exchange Connector provides **bi-directional synchronisation** between Rapla calendar appointments and Microsoft Exchange calendars via the Exchange Web Services (EWS) API. A background scheduler continuously pushes Rapla changes into Exchange; changes originating in Exchange are detected during the mailbox-refresh cycle and pulled back.

The DHBW extension (`dhbwrapla`) layers on top by routing each user to the correct Exchange server based on their LDAP location.

---

## 2. Package Layout

```
org.rapla.plugin.exchangeconnector/
├── ExchangeConnectorPlugin.java          # Plugin constants & config keys
├── ExchangeConnectorConfig.java          # Config reader (inner ConfigReader class)
├── ExchangeConnectorRemote.java          # JAX-RS REST interface
├── ExchangeConnectorRemoteConfigFactory  # Remote config factory
├── ExchangeConnectorResources.java       # i18n bundle
├── ShowExchangeForUser.java              # Auth / visibility check
├── SynchronizationStatus.java            # Status DTO
├── SynchronizeResult.java                # Sync result DTO
├── SyncError.java                        # Error DTO
├── extensionpoints/
│   └── ExchangeConfigExtensionPoint.java # SPI for multi-tenant server routing
├── client/swing/
│   ├── ExchangeConnectorAdminOptions     # Admin preferences panel
│   ├── ExchangeConnectorUserOptions      # Per-user preferences panel
│   └── SyncResultDialog                  # Result display
└── server/
    ├── SynchronisationManager.java       # Scheduler & main orchestrator
    ├── ExchangeAppointmentStorage.java   # Persistent task store
    ├── ExchangeConnectorServerPlugin.java # Server plugin registration
    ├── SynchronizationTask.java          # Sync task model
    └── exchange/
        ├── EWSConnector.java             # EWS connection wrapper
        ├── AppointmentSynchronizer.java  # Per-appointment sync logic
        └── ExchangeAppointment.java      # Exchange appointment model

org.rapla.plugin.dhbw.exchange/
└── DhbwExchangeExtension.java            # DHBW location → Exchange URL mapping
```

---

## 3. Configuration

**Class:** `ExchangeConnectorConfig` / inner `ConfigReader`

Configuration is read from Rapla's system preferences (`RaplaConfiguration`) under the key `EXCHANGESERVER_CONFIG`.

| Config Key | Default | Description |
|---|---|---|
| `ews_fqdn` | — | Exchange server FQDN / URL |
| `exch-sync-past` | 30 | Days in the past to sync |
| `exchange.timezone` | `W. Europe Standard Time` | Windows timezone ID |
| `exchange.default.category` | — | Category label for imported appointments |
| `exchange_connector_enabled_by_admin` | false | Master on/off switch |

`ConfigReader.getExchangeServerURL()` delegates to any registered `ExchangeConfigExtensionPoint` implementations first (e.g. `DhbwExchangeExtension`), falling back to the global system preference if none match.

---

## 4. Startup & Scheduling

**Class:** `SynchronisationManager` (`@Extension(provides = ServerExtension.class)`)

`SynchronisationManager.start()` is called once on server startup. It schedules **two independent recurring actions** using RxJava3:

```
start()
 ├─ schedule(synchronizeMailboxesAction, every 60 minutes)
 │     → refreshMailbox() for all connected users
 └─ schedule(synchronizeAction, every 6 seconds)
       → processTasks() for all pending sync tasks
```

Both actions are wrapped with an **exclusive lock** (`EXCHANGE_LOCK_ID`, 2-second timeout) to prevent concurrent modifications to the task store.

---

## 5. Mailbox Discovery (60-minute cycle)

**Method:** `SynchronisationManager.refreshMailbox(User user)`

Goal: discover the shared Exchange mailboxes accessible for a given user and detect any appointments in Exchange that Rapla does not know about.

```
refreshMailbox(user)
 ├─ Get Exchange credentials for user from preferences
 ├─ Create EWSConnector(fqdn, username, password, mailbox)
 ├─ connector.loadMailboxes()
 │     → Returns UserConnect with map of folder names → FolderId
 ├─ Store UserConnect in connectMap (keyed by User)
 ├─ For each known Allocatable resource linked to this mailbox:
 │     updateTasksForMailbox(user, resource, userConnect)
 └─ Store SynchronizationBox in synchronizationBoxMap (keyed by Allocatable)
```

**`updateTasksForMailbox()`** performs a full resync for one mailbox:
1. Load all existing Rapla appointments for the resource.
2. Query Exchange for appointments marked with the Rapla extended property (`RAPLA_APPOINTMENT_MARKER`).
3. Diff the two sets; create `toUpdate` / `toDelete` tasks accordingly.

---

## 6. Sync Cycle (6-second cycle)

**Method:** `SynchronisationManager.synchronizeAction`

```
synchronizeAction (every 6 s)
 ├─ Acquire EXCHANGE_LOCK_ID (2 s timeout)
 ├─ storage.getAllTasks()          → pending SynchronizationTask list
 ├─ processTasks(tasks)
 │     ├─ Group tasks by User
 │     └─ For each user:
 │           ├─ Look up UserConnect from connectMap
 │           ├─ For each task:
 │           │     ├─ Resolve Rapla appointment from task.appointmentId
 │           │     ├─ Create AppointmentSynchronizer(connector, task, appointment, ...)
 │           │     └─ synchronizer.execute()
 │           └─ storage.storeAndRemove(completed, failed)
 └─ Release lock
```

Rapla data changes (reservations added/changed/deleted) are fed into this cycle through `SynchronisationManager.synchronize(UpdateResult)`, which is called by the Rapla storage listener. That method translates `UpdateResult` entries into new `SynchronizationTask` objects and adds them to the storage queue.

---

## 7. Task Model

**Class:** `SynchronizationTask`

A task represents a single unit of work: synchronise one Rapla appointment into one Exchange mailbox.

| Field | Type | Description |
|---|---|---|
| `appointmentId` | String | Rapla appointment ID |
| `mailboxName` | String | Exchange mailbox address |
| `userId` | String | Rapla user ID |
| `resourceId` | String | Rapla allocatable ID |
| `status` | `SyncStatus` | Current state |
| `retries` | int | Number of failed attempts |
| `lastError` | String | Last error message |
| `persistantId` | String | Exchange item ID (once synced) |

**Status lifecycle:**

```
[created] → toUpdate ──────→ synched
                  └─(error)→ toUpdate (retry)

[deleted] → toDelete ──────→ deleted
                  └─(error)→ toDelete (retry)
```

---

## 8. EWS Connection Layer

**Class:** `EWSConnector`

Wraps the `ews-java-api` library. One instance per user session (cached in `connectMap`).

```java
EWSConnector(fqdn, exchangeUsername, exchangePassword, logger, mailboxAddress)
```

Key methods:

| Method | Description |
|---|---|
| `test()` | Validates credentials by binding a test folder |
| `loadMailboxes()` | Returns `UserConnect` with all shared folder mappings |
| `getService()` | Builds and configures `ExchangeService` (timeout, SSL, credentials) |

**SSL handling:** The connector installs a custom `TrustStrategy` that accepts self-signed certificates — important for internal DHBW Exchange servers.

**Inner class `UserConnect`** holds:
- The `EWSConnector` instance
- `Map<String, FolderId>` — folder name → Exchange folder ID
- Any error messages from mailbox loading

---

## 9. Appointment Synchronisation

**Class:** `AppointmentSynchronizer`

Handles one task at a time. Entry point: `execute()`.

```
execute()
 ├─ status == toUpdate  → addOrUpdate()
 └─ status == toDelete  → delete()
```

**`addOrUpdate()`:**
```
addOrUpdate()
 ├─ getEquivalentExchangeAppointment()
 │     → Searches Exchange folder for item with RAPLA_APPOINTMENT_ID extended property
 │     → Returns existing Exchange Appointment or null
 ├─ if null: create new Appointment
 ├─ Map Rapla fields → Exchange fields:
 │     subject, start/end time, location, recurrence, body, category
 ├─ Set extended properties:
 │     RAPLA_APPOINTMENT_ID      → Rapla appointment ID
 │     RAPLA_APPOINTMENT_MARKER  → "true" (flags as Rapla-owned)
 │     RAPLA_LAST_UPDATED_TIME   → current timestamp
 ├─ saveToExchangeServer()
 │     → appointment.save() or update() with SendInvitationsMode.SendToNone
 └─ task.setPersistantId(exchangeItemId)
```

**`delete()`:**
```
delete()
 ├─ getEquivalentExchangeAppointment()
 ├─ if found: appointment.delete(DeleteMode.MoveToDeletedItems)
 └─ task.setStatus(deleted)
```

**Static utilities:**
- `getExchangeAppointments(service, folderId)` — queries all Rapla-marked items in a folder
- `remove(service, folderId)` — bulk-deletes all Rapla-owned appointments from a folder

**Timezone conversion** is handled by an injected `TimeZoneConverter` that maps Rapla `TimeZone` objects to Windows timezone IDs (e.g. `"W. Europe Standard Time"`).

---

## 10. Persistent Task Storage

**Class:** `ExchangeAppointmentStorage` (`@Singleton`)

Tasks are kept in memory (`Map<String, SynchronizationTask>`) and persisted to the Rapla database as `ExternalSyncEntity` records (one per task, JSON-serialised).

Key methods:

| Method | Description |
|---|---|
| `getAllTasks()` | Returns all tasks currently in memory |
| `getTasksForUser(userId)` | Filters by user |
| `getTask(appointmentId, mailbox)` | Look up a specific task |
| `storeAndRemove(toStore, toRemove)` | Batch persist/delete, updates `ExternalSyncEntity` in DB |
| `refresh()` | Reload from DB, validate task integrity against live Rapla data |

On server startup `refresh()` is called to rebuild the in-memory map from persisted state, discarding any tasks whose Rapla appointments no longer exist.

---

## 11. REST API

**Interface:** `ExchangeConnectorRemote` (JAX-RS)

Base path: `/rapla/rest/exchange/connect`

| Method | Path | Action |
|---|---|---|
| `GET` | `/` | `getSynchronizationStatus()` — current sync status for calling user |
| `POST` | `/synchronize` | Trigger manual full resync for a mailbox |
| `POST` | `/` | `changeUser()` — register/update Exchange credentials for a user |
| `POST` | `/remove` | `removeUser()` — disconnect user, delete all their Exchange appointments |
| `POST` | `/refreshMailboxes` | Force immediate mailbox discovery |

Authentication uses the standard Rapla session / bearer token. The UI panels (`ExchangeConnectorUserOptions`, `ExchangeConnectorAdminOptions`) call these endpoints via the Rapla remote proxy.

---

## 12. DHBW Extension

**Class:** `DhbwExchangeExtension`  
**Implements:** `ExchangeConfigExtensionPoint`  
**Registration:** `@Extension(id = "org.rapla.plugin.dhbw.exchange.config", provides = ExchangeConfigExtensionPoint.class)`

DHBW runs multiple campuses, each with its own Exchange server. This extension maps a user to the correct server URL by inspecting their LDAP location attribute.

```
isResponsibleFor(user)
 └─ match user.ldapLocation against location regex in DhbwAuthPreferences.RoleMapping

getExchangeUrl(user)
 └─ return configured exchange_server_url for the matching RoleMapping entry
```

**`RoleMapping` config format** (stored in admin preferences):

```
location_regex | category | email_domain | exchange_server_url
```

Example:
```
Stuttgart.*   | Rapla     | dhbw-stuttgart.de | https://mail.dhbw-stuttgart.de/EWS/Exchange.asmx
Mannheim.*    | Rapla     | dhbw-mannheim.de  | https://mail.dhbw-mannheim.de/EWS/Exchange.asmx
```

`ConfigReader.getExchangeServerURL(user)` iterates all registered `ExchangeConfigExtensionPoint` implementations and returns the URL from the first one that claims `isResponsibleFor(user)`.

---

## 13. Data Flow Diagram

```
Rapla Storage
    │  (UpdateResult events)
    ▼
SynchronisationManager.synchronize()
    │  creates SynchronizationTask(toUpdate / toDelete)
    ▼
ExchangeAppointmentStorage          ◄── persisted as ExternalSyncEntity (DB)
    │  (every 6 s)
    ▼
SynchronisationManager.processTasks()
    │  groups tasks by user
    │  looks up EWSConnector.UserConnect from connectMap
    ▼
AppointmentSynchronizer.execute()
    │
    ├── toUpdate ──► EWSConnector ──► Exchange Server (EWS API)
    │                   (add / update appointment)
    │
    └── toDelete ──► EWSConnector ──► Exchange Server (EWS API)
                        (delete appointment)

(every 60 min)
SynchronisationManager.refreshMailbox()
    └──► EWSConnector.loadMailboxes()
             └──► Exchange Server
                      (detect foreign changes, build toUpdate tasks)
```

---

## 14. Error Handling & Retries

Failed tasks are not discarded — `SynchronizationTask.increaseRetries()` increments the counter and stores the last error message. The task remains in the queue and is retried on the next 6-second cycle.

A hash of the error message is computed and exposed through `SynchronizationStatus` so the UI can show a persistent error indicator without flooding the user with repeated notifications.

Tasks that repeatedly fail (e.g. due to permanent Exchange-side errors) accumulate retry counts indefinitely. Manual intervention is possible via the REST `/synchronize` endpoint, which triggers `updateTasksForMailbox()` — a full resync that wipes and rebuilds the task set for the affected mailbox.

---

## 15. Key Constants & Tuning Parameters

| Constant | Value | Location |
|---|---|---|
| `SCHEDULE_PERIOD` | 6 seconds | `SynchronisationManager` |
| `SCHEDULE_PERIOD_REFRESH_MAILBOXES` | 60 minutes | `SynchronisationManager` |
| `EXCHANGE_LOCK_ID` | `"exchange"` | `SynchronisationManager` |
| Lock timeout | 2 seconds | `SynchronisationManager` |
| `SERVICE_DEFAULT_TIMEOUT` | 10 seconds | `EWSConnector` |
| Default sync past | 30 days | `ExchangeConnectorConfig` |
| Default timezone | `W. Europe Standard Time` | `ExchangeConnectorConfig` |
| EWS API version | 2.0 | `pom.xml` (`exchange.webservice.api`) |

---

*Generated: 2026-04-22 — based on source at `rapla/src/main/java/org/rapla/plugin/exchangeconnector` and `dhbwrapla/src/main/java/org/rapla/plugin/dhbw/exchange`.*
