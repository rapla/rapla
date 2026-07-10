# Glossary — Rapla terminology

Rapla's vocabulary is overloaded: the same word ("template", "view",
"export", "calendar") names three or four unrelated things depending on
which subsystem you are in. This page is the disambiguation reference.

**Audience:** a new contributor or an AI agent who hits a term in code,
a PRD, or a design discussion and needs to know *which* meaning is in
play — and where the code truth lives. It is a **reference index**, not
a tutorial: each entry is 1–3 sentences plus a `path/to/File.java`
pointer, and links to the deeper `docs/architecture/*.md` page rather
than duplicating it.

Rapla is a German-market product; the UI and users are German. Every
domain term therefore carries its **German equivalent (dt.)** — the word
you will hear in a design session — next to the English code identifier.

**How to read it:** if you are chasing a *collision*, start with
[⚠ Colliding terms](#-colliding-terms). Otherwise jump to the grouped
glossary and scan alphabetically within a group. "geplant (PRD NNN)"
marks a term that does **not exist in the code yet** — it is a planned
concept named in a PRD.

---

## ⚠ Colliding terms

The terms that have bitten us in design sessions. Each row is one
distinct meaning; disambiguate by the "How to tell apart" column.

### "Template" / "Vorlage"

Three unrelated concepts share this word. Two exist; one is planned.

| Term | Meaning | Where in code | How to tell apart |
|---|---|---|---|
| **Rapla template (event template)** — dt. *Vorlage* | A Reservation flagged as a reusable template, so users can create new events by copying it. Marked by the `template` annotation; a copy records its origin via `copyof`. Editing gated by the `edit-templates` user group. | `RaplaObjectAnnotations.KEY_TEMPLATE` (`="template"`) + `KEY_TEMPLATE_COPYOF` (`="copyof"`), `RaplaComponent.isTemplate(...)` (rapla-core `facade/RaplaComponent.java:262`), `RaplaComponent.isTemplateEditAllowed(...)` (`:120`, checks `Permission.GROUP_CAN_EDIT_TEMPLATES = "edit-templates"`). Plugin `plugin/tempatewizard` (sic — the copy wizard). | It is a **Reservation entity** carrying an annotation. Lives in the store like any event; filtered out of GraphQL surfaces (classification-type `rapla`). |
| **HTML/Mustache document template** — *geplant ([PRD 097](../prd/097-event-html-templates-mustache.md))* | A stored, logic-less HTML template filled with event data to produce a printable document (custom exports, loan slips). The document analogue of a stored GraphQL view. | Planned as a `StoredArtifact` with `kind=TEMPLATE` (`StoredArtifact.KIND_TEMPLATE`, rapla-core `entities/storage/StoredArtifact.java:15`) in the artifact store ([PRD 098](../prd/done/098-server-artifact-store.md)). See [PRD 097](../prd/097-event-html-templates-mustache.md). | Does **not exist yet** ([PRD 097](../prd/097-event-html-templates-mustache.md) is draft). It is stored *content* (an artifact body), not a Reservation and not a DynamicType annotation. |
| **Name template (nameformat)** — dt. *Namensvorlage* | The `{attr}`-style pattern that formats an entity's display name from its Classification attributes. Parsed and evaluated by `ParsedText`. | `DynamicTypeAnnotations.KEY_NAME_FORMAT` (+ `_PLANNING` / `_EXPORT` variants), `ParsedText` (rapla-core `entities/dynamictype/internal/ParsedText.java`). See [dynamic-types.md](dynamic-types.md#name-templates-parsedtext). | It is a **string annotation on a DynamicType**, not an entity and not a stored file. "Template" here means string-interpolation pattern. |

### "View" / "Sicht"

| Term | Meaning | Where in code | How to tell apart |
|---|---|---|---|
| **Stored GraphQL view** ([PRD 074](../prd/074-graphql-declarative-views.md)) | A saved, named, declarative GraphQL query that renders a table/grid. BUILTIN catalog + user CUSTOM views. | `ViewCatalogService` (rapla-app `server/spring/graphql/ViewCatalogService.java`), stored as `StoredArtifact` `kind=VIEW` (`StoredArtifact.KIND_VIEW`). See [tableview-and-graphql-views.md](tableview-and-graphql-views.md). | A persisted GraphQL document (artifact body). Advertises render modes via `@view(renderModes:)`. |
| **Render mode** (calendar view type) | *How* results are laid out: table / grouped / day / week / month / program. | `ViewRenderMode` enum (rapla-app `server/spring/graphql/ViewRenderMode.java`: `table, grouped, week, day, month, program`). | A layout enum, not stored data. "Week view" = a render mode. |
| **Swing TableView config** (legacy) | The legacy per-deployment table config (columns + views) in the Swing/HTML client. Being superseded by stored GraphQL views. | `org.rapla.plugin.tableview.internal.TableConfig` (rapla-core), preference `org.rapla.plugin.tableview.config`. See [tableview-and-graphql-views.md](tableview-and-graphql-views.md). | Legacy `RaplaConfiguration` in preferences + per-type `tablecolumn_*` annotations. |
| **MVP View** | The passive UI half of the Presenter/View pattern in the Swing client. | `*View` interfaces in `org.rapla.client.*`. See [mvp-pattern.md](mvp-pattern.md). | A UI-code interface. Nothing to do with saved queries or calendars. |
| **SPA calendar view** | An Angular component rendering blocks (week/month grid). | `rapla-angular/src/app/views/` (e.g. `week-grid.component.ts`). | Frontend TS component. Consumes a render mode + GraphQL data. |

### "Export"

| Term | Meaning | Where in code | How to tell apart |
|---|---|---|---|
| **Published calendar export** (autoexport) | A saved calendar view published at a stable public URL (HTML or iCal) for subscribers. | `autoexport` plugin (`plugin/autoexport/AutoExportPlugin.java`); stored as `RaplaMap<CalendarModelConfiguration>` in user preferences (`CalendarModelConfiguration.EXPORT_ENTRY`); routes `/rapla/calendar(.csv)?`, `/rapla/internal_calendar(.csv)?` (`CalendarPageController`), `/rapla/ical`, `/rapla/internal_ical` (`Export2iCalController`). | User publishes their **own calendar view**. URLs are load-bearing (AGENTS.md §15) — external subscribers depend on them. |
| **External sync (import/export)** | Two-way synchronisation with an external system (Exchange/EWS). Mapping rows link a rapla entity to its foreign id. | `ExternalSyncEntity` (rapla-core `entities/storage/ExternalSyncEntity.java`: `getExternalSystem` / `getRaplaId` / `getData` / `getContext`), `exchangeconnector` plugin. | It is a **mapping entity**, not a URL. "Export to Exchange" is a sync, not a calendar publish. |
| **XML export / backup** | The full data store serialised as `data.xml` (the FileOperator on-disk format, also used for backup/migration). | `rapla-core/.../storage/xml/*`, `FileOperator` (rapla-server). See [overview.md](overview.md). | Whole-store serialisation. "Export the data" in an admin/backup context. |
| **Custom document export** — *geplant ([PRD 097](../prd/097-event-html-templates-mustache.md))* | Rendering event data through a Mustache/HTML template to a printable document. | Planned — see the "HTML/Mustache document template" row above. | Does not exist yet. |

### "Calendar"

| Term | Meaning | Where in code | How to tell apart |
|---|---|---|---|
| **CalendarModelConfiguration** — dt. *(gespeicherte) Kalendersicht* | A **persisted snapshot** of a calendar view: dates, selected allocatables, filters, render type, title. Stored in preferences; shareable as a "saved view". | `CalendarModelConfiguration` (rapla-core `entities/configuration/`). A value object (RaplaType-registered, no id). See [domain-model.md](domain-model.md#preferences-raplaconfiguration-calendarmodelconfiguration). | **Immutable stored config.** This is what a saved calendar/export *is*. |
| **CalendarModel / CalendarSelectionModel** | The **mutable, in-memory** view state the client mutates and views listen to. Saving one produces a `CalendarModelConfiguration`. | `CalendarModel` / `CalendarSelectionModel` (rapla-core `facade/`), impl `CalendarSelectionModelImpl`. See [conflicts-and-events.md](conflicts-and-events.md#how-the-ui-gets-blocks-calendarmodel). | Live UI state, not stored. `...Configuration` is its persisted snapshot. |
| **SPA calendar view** | The Angular grid rendering appointment blocks. | `rapla-angular/src/app/views/`. | Frontend component (see "View" collision). |

### "Model"

| Term | Meaning | Where |
|---|---|---|
| **CalendarModel / CalendarSelectionModel** | Mutable in-memory calendar view state (see "Calendar"). | rapla-core `facade/` |
| **CalendarModelConfiguration** | Its persisted snapshot (see "Calendar"). | rapla-core `entities/configuration/` |
| **Domain model** | The entity graph (User, Reservation, …). | [domain-model.md](domain-model.md) |
| **MVP Model** | The pure, headless model half of the Presenter/View/Model carve-out (e.g. `AllocationConflictModel`). | rapla-core; see [mvp-pattern.md](mvp-pattern.md) |

### "Block"

| Term | Meaning | Where |
|---|---|---|
| **AppointmentBlock** — dt. *Termin(block)* | The materialised occurrence of one Appointment after expanding its repeating rule. Not persisted. | `AppointmentBlock` (rapla-core `entities/domain/`) |
| **RaplaBlock** | The render-layer wrapper around an AppointmentBlock carrying build context (colors, permissions), produced by `RaplaBuilder`. | `RaplaBlock` (rapla-core `plugin/abstractcalendar/`) |
| **SwingRaplaBlock** | The Swing-specific painted block (pixel geometry, always-black text). | rapla-client |
| **GraphQL `AppointmentBlock`** | The wire shape exposed to the SPA (`color`, `matchedBy`, `allocatables(filter:)`). | `schema.graphqls`; see [calendar-rendering.md](calendar-rendering.md) |

### "Filter"

| Term | Meaning | Where |
|---|---|---|
| **ClassificationFilter** | A query predicate over Classification attribute values (the legacy "neue Regel für" rules). | `ClassificationFilter` (rapla-core `entities/dynamictype/`) |
| **ReservationFilter / AllocatableFilter** (GraphQL) | The GraphQL input types carrying date range, type/where predicates, allocatable matching. | `schema.graphqls`; see [tableview-and-graphql-views.md](tableview-and-graphql-views.md) |
| **CalendarModel allocatable/reservation filter** | The selected-object + classification filters on the in-memory calendar model. | `CalendarModel.setReservationFilter` / `setAllocatableFilter` |

### "Store"

| Term | Meaning | Where |
|---|---|---|
| **`StorageOperator.store(...)`** (the verb) | Persist an entity through the operator/dispatch pipeline. | `StorageOperator` (rapla-core `storage/`) |
| **The store / backend** | The backing persistence (XML file via `FileOperator`, or JDBC via `DBOperator`). | rapla-server `storage/` |
| **LocalCache** | The in-memory entity index (a cache, *not* the durable store). | `LocalCache` (rapla-core `storage/`) |
| **Artifact store** | The `StoredArtifact` persistence layer (views, templates, images). | `StoredArtifact`; `ArtifactCatalogService` (rapla-app) |

### "Sync"

| Term | Meaning | Where |
|---|---|---|
| **`*Sync` operator methods** | Synchronous server-side operator calls (`queryAppointmentsSync`, `getConflictsSync`) — the server never wraps a Promise in a latch (AGENTS.md §4). | `SyncStorageOperator` (rapla-core `storage/`) |
| **External sync** | Exchange/EWS synchronisation (see "Export"). | `ExternalSyncEntity`, `exchangeconnector` |
| **Refresh poll** | The client's periodic incremental update pull (not "sync" in code, but colloquially). | `RemoteOperator.refreshAsync`; see [flows.md](flows.md) |

### "Type" / "Key"

| Term | Meaning | Where |
|---|---|---|
| **DynamicType** — dt. *Veranstaltungs-/Ressourcenart* | An admin-defined schema. | `DynamicType` (rapla-core `entities/dynamictype/`) |
| **DynamicType key** | The stable string id of a DynamicType (`"course"`), and — per ADR 0005 — the GraphQL API identity. | `DynamicType.getKey()` |
| **RaplaType** | The static registry mapping each Entity subtype to a serialization localname (`"reservation"`, `"resource"`). | `RaplaType` (rapla-core `entities/`) |
| **AttributeType** | The value-type enum of an Attribute (STRING/INT/DATE/BOOLEAN/CATEGORY/ALLOCATABLE). | `AttributeType` (rapla-core `entities/dynamictype/`) |
| **Attribute/Category key** | Stable string id of an Attribute or Category node. | `Attribute.getKey()`, `Category.getKey()` |
| **Annotation / i18n / TypedComponentRole key** | String keys for entity annotations, resource-bundle lookups, and typed preference slots respectively — all called "key", all unrelated. | `Annotatable`, `RaplaResources`, `TypedComponentRole` |

### "Owner" vs "lastChangedBy" — both are Users, don't conflate

| Term | Meaning | Where |
|---|---|---|
| **Owner** — dt. *Besitzer* | The User who *owns* an entity (drives the permission owner-shortcut). Set once, changed only by re-parenting (admin). | `Ownable.getOwner()` (rapla-core `entities/`) |
| **lastChangedBy** | The User who most recently *modified* the entity — audit metadata, no permission effect. | `Timestamp.getLastChangedBy()` (rapla-core `entities/`) |

### "Permission" vs "AccessLevel"

| Term | Meaning | Where |
|---|---|---|
| **Permission** — dt. *Berechtigung* | One ACL *row* (who + level + optional time window), embedded in a PermissionContainer. | `Permission` / `PermissionImpl` (rapla-core `entities/domain/`) |
| **AccessLevel** | The *level* granted by a Permission — the 9-value ordered enum `DENIED..ADMIN`. | `Permission.AccessLevel`; see [permissions.md](permissions.md) |
| **PermissionController** | The engine that resolves effective access (additive, max-wins). | `PermissionController` (rapla-core `storage/`) |

### "Session"

| Term | Meaning | Where |
|---|---|---|
| **RemoteSession** | The request-scoped server object holding the resolved current `User` for one request. | `RemoteSession`, `SpringSecurityRemoteSession` (rapla-server) |
| **Edit session** | The client-side lifetime of an open edit dialog (clone-edit-save). | see [reservation-edit.md](reservation-edit.md) |

---

## Domain & entities

- **Allocatable (dt. Ressource)** — A bookable thing: room, equipment, or person, distinguished by its DynamicType classification-type. `Allocatable` (rapla-core `entities/domain/Allocatable.java`). Not to be confused with the German UI term *Ressource* covering both rooms and persons. See [domain-model.md](domain-model.md#allocatable).
- **Appointment (dt. Termin)** — A single time block (start/end in GMT internally), optionally repeating, always owned by exactly one Reservation. `Appointment` (rapla-core `entities/domain/Appointment.java`). Distinct from AppointmentBlock (its materialised occurrence). See [conflicts-and-events.md](conflicts-and-events.md#appointment-a-time-block-optionally-repeating).
- **AppointmentBlock (dt. Termin/Block)** — The computed, non-persisted materialisation of one occurrence of an Appointment. Calendar views render blocks. `AppointmentBlock` (rapla-core `entities/domain/AppointmentBlock.java`). See the "Block" collision above.
- **Attribute** — One column in a DynamicType schema, with a key, an AttributeType, constraints, and a default. `Attribute` (rapla-core `entities/dynamictype/Attribute.java`). See [dynamic-types.md](dynamic-types.md).
- **Category (dt. Kategorie)** — A node in a hierarchical tree that plays three roles: user groups, permission targets, and CATEGORY-attribute value sets. `Category` (rapla-core `entities/Category.java`). See [domain-model.md](domain-model.md#category).
- **Classifiable** — Mixin for "I have a Classification": Allocatable, Reservation, and the User's person record. `Classifiable` (rapla-core `entities/dynamictype/`).
- **Classification** — One instance's attribute-value bag pointing at one DynamicType; stores everything as `Map<key, List<String>>`. `Classification` (rapla-core `entities/dynamictype/Classification.java`). See [dynamic-types.md](dynamic-types.md#the-instance-side-classification).
- **Conflict (dt. Konflikt)** — A computed (not persisted-as-content) record: one Allocatable double-booked by two overlapping (Reservation, Appointment) pairs. `Conflict` (rapla-core `facade/Conflict.java`), computed by `ConflictFinder` (rapla-server). See [conflicts-and-events.md](conflicts-and-events.md#conflicts).
- **DynamicType (dt. Veranstaltungs-/Ressourcenart)** — An admin-defined runtime schema (key + Attributes + annotations). The heart of the schema-on-data system. `DynamicType` (rapla-core `entities/dynamictype/DynamicType.java`). See [dynamic-types.md](dynamic-types.md).
- **Entity / SimpleEntity** — `Entity<T>` is the storable-with-id root interface; `SimpleEntity` the impl base. `RaplaObject` is the broader root (Permission and Classification are RaplaObjects but not Entities). rapla-core `entities/storage/`. See [domain-model.md](domain-model.md#type-hierarchy).
- **ExternalSyncEntity** — A mapping row linking a rapla entity to a foreign system's id (Exchange). `ExternalSyncEntity` (rapla-core `entities/storage/ExternalSyncEntity.java`). See the "Export"/"Sync" collisions.
- **Ownable / Owner (dt. Besitzer)** — Mixin for "has an owner User"; the owner gets the permission owner-shortcut. `Ownable` (rapla-core `entities/`). Distinct from `lastChangedBy` (audit only).
- **Period (dt. Zeitraum)** — A named time interval (academic term, e.g. "Summer 2026"), optionally category-tagged. `Period` (rapla-core `entities/domain/Period.java`). Its id wraps a `rapla:period` allocatable.
- **Preferences** — Per-user or system-wide config, indexed by `TypedComponentRole<T>`; id `preferences_<userId>` (system = `preferences_0`). `Preferences` (rapla-core `entities/configuration/`).
- **RaplaObject / RaplaType** — `RaplaObject` is the type-discriminated root; `RaplaType` the static registry mapping subtypes to serialization localnames. rapla-core `entities/`. See the "Type" collision.
- **ReferenceInfo / EntityResolver** — Entities reference each other by typed id (`ReferenceInfo<T>`), resolved on demand via `EntityResolver` (implemented by both `LocalCache` and `RemoteOperator`). rapla-core `entities/storage/`. See [domain-model.md](domain-model.md#identity-references-resolution).
- **Repeating (dt. Wiederholung)** — The recurrence rule on an Appointment (DAILY/WEEKLY/MONTHLY/YEARLY). MONTHLY = *Nth weekday of month*, not same day-of-month. `Repeating` (rapla-core `entities/domain/Repeating.java`). See [conflicts-and-events.md](conflicts-and-events.md#repeating).
- **RequestStatus** — Per-allocatable state of the request/approve workflow (`REQUEST`-level users request; admins confirm). `Reservation.getRequestStatus(Allocatable)`. See [permissions.md](permissions.md#requeststatus-workflow).
- **Reservation (dt. Veranstaltung)** — The central event entity: one logical event containing one or more Appointments that allocate one or more Allocatables. `Reservation` (rapla-core `entities/domain/Reservation.java`). **Not** one calendar event — it is the container for an event series. See [conflicts-and-events.md](conflicts-and-events.md).
- **Restriction** — Per-appointment narrowing of an allocatable (`setRestriction(Allocatable, Appointment[])`); a null/empty array means "all appointments". Appointment-level, not block-level. See [domain-model.md](domain-model.md#reservation).
- **StoredArtifact** — A generic server-stored content blob with a natural key `kind:name`; kinds `VIEW`, `TEMPLATE`, `PARTIAL`, `CSS`, `IMAGE`. `StoredArtifact` (rapla-core `entities/storage/StoredArtifact.java`, [PRD 098](../prd/done/098-server-artifact-store.md)). The backing store for GraphQL views (and planned Mustache templates).
- **Timestamp / ModifiableTimestamp** — Mixins for `createDate` / `lastChanged` / `lastChangedBy` audit metadata. rapla-core `entities/`.
- **User (dt. Benutzer)** — The auth principal; owns entities, belongs to Category groups, may be `isAdmin()`. `User` (rapla-core `entities/User.java`). Can have a *person* record (an Allocatable of type `rapla:person`). See [domain-model.md](domain-model.md#user).

## Schema-on-data

- **AttributeType** — Enum of an Attribute's value type: STRING, INT (stored as Long), BOOLEAN, DATE (LocalDateTime), CATEGORY (id ref), ALLOCATABLE (id ref). rapla-core `entities/dynamictype/AttributeType.java`.
- **classificationType** — DynamicType annotation partitioning types into `rapla` (built-in), `resource`, `person`, `reservation`. `DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE`. See [dynamic-types.md](dynamic-types.md#built-in-vs-admin-defined-types).
- **ConstraintIds** — Attribute constraint keys (`KEY_ROOT_CATEGORY`, `KEY_DYNAMIC_TYPE`, `KEY_MULTI_SELECT`, …). rapla-core `entities/dynamictype/ConstraintIds.java`.
- **nameformat** — See the "Template" collision (name template). `DynamicTypeAnnotations.KEY_NAME_FORMAT`.
- **ParsedText** — Parses/evaluates a nameformat template against a Classification; functions pluggable via `FunctionFactory`. rapla-core `entities/dynamictype/internal/ParsedText.java`. See [dynamic-types.md](dynamic-types.md#name-templates-parsedtext).

## Storage & persistence

- **CachableStorageOperator / StorageOperator** — The server-side persistence API (dispatch, query, lock). Server code depends on the *operator*, not `RaplaFacade` (AGENTS.md §4). rapla-core `storage/`.
- **DBOperator / FileOperator** — The two backend implementations: JDBC (`RaplaSQL`, multi-pod) and single-file XML. rapla-server `storage/dbsql/` and `storage/`. See [locking.md](locking.md), [overview.md](overview.md).
- **EntityHistory** — Server-side change-record history used for incremental updates. rapla-server `storage/`.
- **LocalCache** — In-memory entity index and `EntityResolver`; also runs the `getVisibleEntities` read filter. rapla-core `storage/LocalCache.java`. A cache, not the durable store (see "Store" collision).
- **RaplaLock / WRITE_LOCK** — The three lock layers: process (`ReentrantReadWriteLock`), resource, and global (DB `WRITE_LOCK` rows). See [locking.md](locking.md).
- **UpdateEvent** — The incremental change payload the server returns to a polling client (stores + removes). rapla-core `storage/UpdateEvent.java`. See [flows.md](flows.md).
- **UpdateResult** — The server-side outcome of a dispatch (what changed, including appeared/disappeared conflicts). rapla-core `storage/UpdateResult.java`.
- **ModificationEvent** — The facade-level "something changed" notification consumed by client views. rapla-core `facade/ModificationEvent.java`. Distinct from `UpdateEvent` (the wire payload).

## Permissions & auth

- **AccessLevel** — The 9-value ordered grant enum `DENIED(0)..ADMIN(400)`; `DENIED` is deprecated as selectable. See [permissions.md](permissions.md#accesslevel) and the "Permission vs AccessLevel" collision.
- **AuthenticationStore** — Pluggable credential verifier (DB default, LDAP/JNDI, urlencryption). See [permissions.md](permissions.md#authentication-jwt-rapla-user), [extension-points.md](extension-points.md).
- **canRead / canReadInformation** — The two resource-visibility gates: `canReadInformation` (≥ `READ_NO_ALLOCATION`, 50 — resource exists) vs `canRead` (≥ `READ`, 100 — its bookings). Getting these confused causes leaks. See [permissions.md](permissions.md#5-read_no_allocation--resource-visibility-vs-booking-visibility-verified-2026-06-24).
- **PermissionContainer** — Mixin for "owns a list of Permissions": Allocatable, Reservation, DynamicType, Category. rapla-core `entities/domain/PermissionContainer.java`.
- **PermissionController** — Resolves effective access (purely additive / max-wins per ADR 0003 / [PRD 090](../prd/090-additive-permission-resolution.md)). rapla-core `storage/PermissionController.java`. See [permissions.md](permissions.md#resolution-algorithm).
- **RemoteSession** — Request-scoped holder of the resolved current `User`. See the "Session" collision.

## Calendar & rendering

- **CalendarModel / CalendarSelectionModel** — Mutable in-memory view state (dates, selected allocatables, filters, render type). rapla-core `facade/`. See the "Calendar"/"Model" collisions.
- **CalendarModelConfiguration** — The persisted snapshot of a calendar view. rapla-core `entities/configuration/`. See the "Calendar" collision.
- **RaplaBlock / RaplaBuilder** — `RaplaBuilder` expands appointments into `RaplaBlock`s carrying color/permission build context; shared by Swing and server HTML export. rapla-core `plugin/abstractcalendar/`. See [calendar-rendering.md](calendar-rendering.md).
- **Render mode / ViewRenderMode** — The layout enum (table/grouped/day/week/month/program). rapla-app `server/spring/graphql/ViewRenderMode.java`. See the "View" collision.
- **Slot / lane** — A column within a day that holds non-overlapping blocks; assigned by a strategy pipeline (`GroupAllocatablesStrategy`, fixed vs compact). See [calendar-rendering.md](calendar-rendering.md#2-lane-slot-model--how-overlapping-blocks-get-columns).

## GraphQL & views

- **`@view` / render modes** — The directive a stored GraphQL view uses to advertise its render modes. [PRD 074](../prd/074-graphql-declarative-views.md). See [tableview-and-graphql-views.md](tableview-and-graphql-views.md).
- **ReservationFilter / AllocatableFilter** — GraphQL input types carrying date range, `typeKeyEq`, generated `whereXxx` predicates, `allocatableMatching`. `schema.graphqls`. See the "Filter" collision and [graphql.md](../graphql.md).
- **Stored view (kind=VIEW)** — A saved GraphQL query artifact; BUILTIN + CUSTOM catalog managed by `ViewCatalogService`. See the "View" collision.
- **`<TypeKey>Classification`** — The per-DynamicType narrowing type generated in the GraphQL schema; keys are API identity (ADR 0005). See [graphql.md](../graphql.md).

## Plugins & integration

- **abstractcalendar** — Shared block/lane/color machinery (`RaplaBuilder`) consumed by Swing views and server HTML export. rapla-core `plugin/abstractcalendar/`.
- **autoexport** — Publishes saved calendar views as HTML/iCal at stable URLs. See the "Export" collision.
- **exchangeconnector** — Two-way Exchange/EWS sync via `ExternalSyncEntity`. See the "Sync" collision.
- **export2ical / ical** — iCal generation and the `/rapla/ical` subscription routes (`Export2iCalController`). AGENTS.md §15 allow-list (external subscribers depend on the URLs).
- **tableview** — Legacy Swing/HTML table config (`TableConfig`). See the "View" collision and [tableview-and-graphql-views.md](tableview-and-graphql-views.md).
- **tempatewizard** — The copy-from-template wizard (note the misspelled package name). Consumes the `template`/`copyof` annotations. See the "Template" collision.
- **urlencryption** — Encrypted public calendar URLs (an `AuthenticationStore` variant). See [permissions.md](permissions.md), [extension-points.md](extension-points.md).

For the full plugin catalog and DI wiring see [extension-points.md](extension-points.md).

## Build & modules

- **rapla-bom / rapla-core / rapla-client / rapla-server / rapla-app** — The five Maven reactor modules. rapla-core is the shared spine (no Spring Boot, no Swing). See [overview.md](overview.md#the-five-module-reactor) and AGENTS.md.
- **rapla-angular** — The Angular 21 SPA, served at `/app/`; **not** in the Maven reactor. AGENTS.md §14.
- **RaplaFacade / RaplaComponent** — `RaplaFacade` is the Swing-client data API (a process-singleton on the server — server code avoids it). `RaplaComponent` is the client-side base with helper predicates (`isTemplate`, `isTemplateEditAllowed`). rapla-core `facade/`.

## See also

- [README.md](README.md) — the architecture doc index
- [domain-model.md](domain-model.md) — entity catalog and ER summary
- [dynamic-types.md](dynamic-types.md) — schema-on-data (DynamicType, Attribute, Classification, name templates)
- [permissions.md](permissions.md) — AccessLevel, resolution, enforcement
- [tableview-and-graphql-views.md](tableview-and-graphql-views.md) — stored views vs legacy TableView
- [calendar-rendering.md](calendar-rendering.md) — blocks, lanes, render modes
- AGENTS.md — build, module layout, hard rules
