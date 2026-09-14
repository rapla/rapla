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
| [glossary.md](glossary.md) | What a term *means* — the disambiguation index for colliding words ("template", "view", "export", "calendar", "model", "block", …), each domain term with its German equivalent and a code pointer |
| [overview.md](overview.md) | What modules exist, how they're laid out, how they talk |
| [domain-model.md](domain-model.md) | What entities exist, how they relate (ER summary) |
| [dynamic-types.md](dynamic-types.md) | The schema-on-data system: DynamicType, Attribute, Classification, name templates |
| [conflicts-and-events.md](conflicts-and-events.md) | Reservation as event container; Appointment / AppointmentBlock; how `ConflictFinder` works |
| [reservation-edit.md](reservation-edit.md) | End-to-end edit flow with clone semantics, EventCheck, save; wire model; AppointmentController rules; edge case reference |
| [reservation-edit-ui-inventory.md](reservation-edit-ui-inventory.md) | Every user-facing capability of the Swing edit dialog (picker states, restriction popup, free-slot search) — the coverage checklist for the SPA event sheet ([PRD 091](../prd/091-spa-reservation-edit-and-availability.md)) |
| [rest-api.md](rest-api.md) | Full REST endpoint catalog grouped by audience (SPA, admin, import/export); auth header, error envelope, common-flow recipes |
| [permissions.md](permissions.md) | AccessLevel, PermissionImpl, resolution algorithm, server enforcement, JWT |
| [extension-points.md](extension-points.md) | Spring DI plugin wiring, extension-point catalog, plugin list |
| [flows.md](flows.md) | Cross-cutting flows: login, query, store/dispatch, refresh poll |
| [locking.md](locking.md) | The three lock layers (process / resource / global), the `WRITE_LOCK` table, multi-pod concurrency, the validate-before-lock ordering gotcha |
| [mvp-pattern.md](mvp-pattern.md) | Presenter / View / pure-model carve-out pattern; headless test harness; AGENTS.md §12 leak-probe pattern |
| [swing-platform-quirks.md](swing-platform-quirks.md) | Platform/compositor workarounds in the Swing client (WSLg popup input-region leak, FilterEditButton positioning, focus-dismiss handlers) |
| [what-changed-in-rapla-3.md](what-changed-in-rapla-3.md) | What changed from the historical `master` branch to the current `spring-boot` tree (restinject removed, Spring Boot 4 + Angular added, 5-module reactor, carve-out programme); size comparison + intent for new contributors |
| [tableview-and-graphql-views.md](tableview-and-graphql-views.md) | Legacy Swing TableView config (views, columns, `defaultValue` annotations, the dhbw-configured Termine table) as the capability benchmark for the GraphQL-native view system ([PRD 074](../prd/074-graphql-declarative-views.md)) — proves the new path reproduces the existing tables |
| [spa-selection-and-actions.md](spa-selection-and-actions.md) | Row/item selection in the SPA (Swing/Excel semantics, keyboard map, touch selection mode) and how a multi-row selection feeds the row-action/command system (bulk Löschen, subset-wins gating, best-effort undo) — PRDs [094](../prd/094-spa-main-view-actions-and-popups.md)/099 |
| [legacy-urls.md](legacy-urls.md) | The `/rapla/` prefix after the context-root move (server routes kept it literally, client URL generators must emit it themselves) + the `UrlEncryptor` salt-in-return-value wire format — read before touching published calendar/iCal URL generation |
| [calendar-rendering.md](calendar-rendering.md) | Block-based calendar rendering rules extracted from Swing + HTML export (shared rapla-core strategy machinery): color resolution + always-black text, the lane/slot pipeline (`GroupAllocatablesStrategy` over SELECTED resources, fixed vs compact), rows-per-hour scale/worktime, FLOW/BLOCK selection — ground truth for the SPA month/week grids (PRDs [095](../prd/095-month-grid-render-mode.md)/[077](../prd/077-calendar-model-graphql.md)/[100](../prd/100-spa-block-renderer-unification.md)) |
| [exchange-sync.md](exchange-sync.md) | The server-side Exchange/EWS connector plugin: poll/sweep cycles, watermark locking, rapla-marking via extended properties, mailbox-to-person mapping, own-vs-foreign item rules — derived from a 2026-09-09 production incident ([PRD 114](../prd/114-exchange-sync-per-mailbox-lock.md)/[070](../prd/070-restore-exchange-connector-wiring.md)) |

## What lives where (quick lookup)

- **"Which meaning of this word?"** — [glossary.md](glossary.md),
  starting with its `⚠ Colliding terms` section.
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

[PRD 022](../prd/022-architecture-documentation.md) captures the rationale and scope of this doc set. It will
move to `docs/prd/done/` once the eight pages are stable.

## Page headers

Generated one-paragraph headers per page (invariants, decisions, PRD provenance, governed code) plus a German/English keyword line for search. Keep them in sync when a page changes.

### calendar-rendering.md

Documents rapla's block-based calendar rendering rules shared by rapla-core (RaplaBuilder, AbstractGroupStrategy, GroupAllocatablesStrategy in org.rapla.components.calendarview / org.rapla.plugin.abstractcalendar) and consumed identically by Swing views and server-side HTML export (HTMLWeekViewPage, HTMLMonthViewPage). Covers color resolution (reservation color wins, else first color-bearing allocatable, text always black per SwingRaplaBlock), the lane/slot assignment pipeline (group/resolveConflicts/mergeSlots), the query-layer "matchedBy" binding semantics for grouping by selected resource including belongsTo hierarchies, fixed-vs-compact column modes, time-scale (rowsPerHour, worktime, excludeDays via LinearRowScale/CalendarOptions), FLOW vs BLOCK selection strategies, and drag/resize gating (RaplaBlock.isMovable). Ground truth for the Angular SPA's week/month grids; derives from and feeds PRD 095, PRD 077, PRD 100.

*Keywords:* Kalenderansicht, Termin, Block, Lane, Slot, Farbe, RaplaBuilder, GroupAllocatablesStrategy, Ressource, Wochenansicht, Monatsansicht, PRD 095, PRD 100, PRD 077, Konflikt, Selektion, matchedBy, Drag

### conflicts-and-events.md

Explains the Reservation-as-event-container model: a Reservation owns multiple Appointments (optionally repeating via Repeating/RepeatingType DAILY/WEEKLY/MONTHLY/YEARLY, with MONTHLY meaning Nth-weekday-of-month not same-day), which expand into non-persisted AppointmentBlocks. Covers per-appointment allocatable Restrictions, CalendarModel/CalendarSelectionModel querying of blocks, and the Conflict entity (immutable, pairwise, server-computed by ConflictFinder in rapla-server via the sweepLine algorithm, permission-filtered via canRead) including conflict disabling. Points to the separate conflict-detection.md for the overlap algorithm math. References regression tests (AppointmentBlockExpansionHardeningTest, ConflictFinderSweepLineTest) and governs rapla-core entities/domain/{Reservation,Appointment,Repeating,AppointmentBlock} and rapla-server ConflictFinder.

*Keywords:* Veranstaltung, Termin, Wiederholung, Serie, Konflikt, Reservation, Appointment, Repeating, MONTHLY, AppointmentBlock, ConflictFinder, Restriction, Einschränkung, Ressource, Buchung, CalendarModel, sweepLine

### domain-model.md

Reference catalog of rapla's core entity types and their relationships: composite-parent pattern (ParentEntity), schema-on-data classification, and reference-by-id via ReferenceInfo/EntityResolver. Documents the RaplaObject/Entity type hierarchy and mixins (Ownable, Annotatable, Timestamp, PermissionContainer, Classifiable, DynamicTypeDependant), id generation and type-prefix letters (createId, hex-valid since 2026-07-06, legacy r/u prefixes), the mandatory-client-id rule for GraphQL creates (PRD 056 idempotency rationale), and per-entity detail sections for User, Category, Allocatable, Reservation, Appointment/Repeating/AppointmentBlock, DynamicType/Attribute/Classification, Period, Permission, Preferences/RaplaConfiguration/CalendarModelConfiguration, and Conflict, closing with an ER summary diagram. Governs rapla-core entities/* packages broadly.

*Keywords:* Domänenmodell, Entität, ReferenceInfo, EntityResolver, Allocatable, Ressource, Reservation, Veranstaltung, Appointment, Termin, DynamicType, Classification, Permission, Berechtigung, PRD 056, Id-Präfix, UUID, Category, Kategorie

### dynamic-types.md

Describes rapla's schema-on-data system: admin-defined DynamicType schemas composed of Attributes (typed columns: STRING/INT/BOOLEAN/DATE/CATEGORY/ALLOCATABLE), instantiated as Classification value bags (stored as Map<key,List<String>>) on Allocatable/Reservation/User-person entities. Covers classificationType annotations (rapla/resource/person/reservation), ParsedText name templates (nameformat), the DynamicTypeEditUI admin UI, XML and Jackson-3 JSON wire formats, and critically the migration/rewrite semantics when attributes are added (lazy) vs removed/renamed/retyped (eager, rewritten in the same dispatch via DynamicTypeDependant/commitChange) plus the key-as-reference blast-radius table for renames (PRD 110 safe-key-rename). Governs rapla-core entities/dynamictype/* and the DynamicTypeEditUI in rapla-client; derives from PRD 010, PRD 011, PRD 110.

*Keywords:* DynamicType, Attribute, Classification, Klassifikation, Vorlage, nameformat, Namensvorlage, ParsedText, Ressourcenart, Veranstaltungsart, Schema, Migration, PRD 110, PRD 010, PRD 011, Key-Rename, Kategorie, ALLOCATABLE

### event-templates.md

Explains rapla's event templates (Vorlagen) — reusable event blueprints distinct from the unrelated Mustache document-template system (PRD 097). Covers the storage model (a template is an Allocatable of DynamicType rapla:template, its content is 1..n Reservations linked via the KEY_TEMPLATE annotation), permission delegation from template-reservation to template (PermissionController ~line 608-630, §12 filtering), Swing instantiation semantics via EditTaskPresenter/RaplaFacade.copyReservations (the fixedtimeandduration flag governing day-offset vs minute-exact shift, target-interval cascade, series re-anchoring), and the SPA "Neu" picker flow (newEventOptions GraphQL query, NewEventPickerComponent, reservationsFromTemplate, draftFromTemplate) per PRD 104 and PRD 107 §D6.

*Keywords:* Vorlage, Template, Ereignisvorlage, Termin, Reservation, copyReservations, fixedtimeandduration, PermissionController, PRD 104, PRD 107, Neu-Picker, Berechtigung, keepTime, Serie, Instanziierung, dhbw Semestervorlage

### exchange-sync.md

Documents the server-side Exchange/EWS connector plugin (org.rapla.plugin.exchangeconnector) that mirrors rapla appointments into Outlook calendars via per-location sync accounts. Covers the poll (every 6s, SynchronisationManager.synchronizeQueue) and hourly sweep (synchronizeMailboxes) cycles, SynchronizationTask/AppointmentSynchronizer/EWSConnector components, watermark locking via the EXCHANGE DB write lock, disabled task persistence, rapla-marking via extended properties (raplaId/isRaplaMeeting/raplaLastUpdate), mailbox-to-person mapping via exchangeMailbox attribute, hard-won Exchange behavioral facts (share levels, private-item deletion failures, owner-edit/copy discrimination via PR_CREATOR_NAME, delete modes, paging quirks), the own-vs-foreign item decision rules (last-writer-wins), and sweep window/deletion-safety bounds. Derived from a 2026-09-09 production incident; governs rapla-server exchangeconnector classes; references PRD 114 and PRD 070.

*Keywords:* Exchange, EWS, Outlook, Sync, Synchronisation, exchangeconnector, SynchronisationManager, AppointmentSynchronizer, PRD 114, PRD 070, Mailbox, raplaId, Sweep, Poll, Termin, Kalender, PR_CREATOR_NAME, Berechtigung Freigabe

### extension-points.md

Catalogs rapla's Spring-DI-based plugin/extension architecture, explaining the client-side component-scan wiring (SwingClientConfig, @Service auto-injected into Set<X>/Map<String,X>) versus the server-side explicit @Bean factory pattern (ServerCoreConfig/ServerServiceConfig gated by @ConditionalOnProperty, since @SpringBootApplication only scans its own package) — including the footgun that @Service on org.rapla.server.internal.* classes is silently ignored. Lists client extension-point interfaces (RaplaMenuExtension family, EventCheck, SwingViewFactory, etc.), server extension points (HTMLViewPage, ServletRequestPreprocessor), and the full bundled plugin catalog (weekview, tableview, export2ical, exchangeconnector, archiver, urlencryption, jndi, etc.) with which are off-by-default. Walks through the export2ical plugin end-to-end as a worked wiring example and gives a how-to-add-a-new-extension recipe. Governs rapla-client/rapla-server plugin wiring broadly; references AGENTS.md §4 and PRD 020.

*Keywords:* Plugin, Extension Point, Spring DI, ServerServiceConfig, SwingClientConfig, EventCheck, ConditionalOnProperty, export2ical, exchangeconnector, tableview, HTMLViewPage, PRD 020, adminpanels, urlencryption, Erweiterung, Berechtigung Plugin

### flows.md

Cross-cutting sequence documentation for how data moves through rapla end to end: login/bootstrap (OAuth2 password grant, RemoteOperator.connect, initial snapshot), read queries (queryBlocks/queryAppointments through RemoteStorageImpl and LocalCache), the store/dispatch pipeline (EventCheck validation, UpdateEvent construction, SecurityManager permission checks, LocalAbstractCachableOperator.dispatch with locking/versioning/persistence/conflict-reindex), the 30s client refresh poll (RemoteOperator.refreshAsync), and server-side persistence backends (FileOperator XML-on-disk vs DbsqlOperator/RaplaSQL JDBC transactional per-entity storage classes). Documents the UpdateEvent and UpdateResult wire shapes and the optimistic-concurrency model (RaplaNewVersionException). Governs rapla-core facade/RaplaFacade, RemoteOperator, rapla-server RemoteStorageImpl/LocalAbstractCachableOperator/FileOperator/DbsqlOperator; references PRD 011, PRD 017, PRD 021, PRD 008.

*Keywords:* Dispatch, UpdateEvent, UpdateResult, RemoteOperator, LocalCache, FileOperator, DbsqlOperator, RaplaSQL, Login, Refresh Poll, Konfliktindex, Sperre, Lock, PRD 011, PRD 017, PRD 021, Store, Anmeldung, Synchronisation

### glossary.md

Disambiguation reference for rapla's overloaded vocabulary, each term paired with its German equivalent and a code pointer. The centerpiece is the 'Colliding terms' section resolving Template/Vorlage (event template vs planned Mustache document template PRD 097 vs nameformat), View/Sicht (stored GraphQL view PRD 074 vs render mode vs legacy TableView vs MVP View vs SPA component), Export, Calendar, Model, Block, Filter, Store, Sync, Type/Key, Owner-vs-lastChangedBy, and Permission-vs-AccessLevel. Followed by grouped glossary sections (Domain & entities, Schema-on-data, Storage & persistence, Permissions & auth, Calendar & rendering, GraphQL & views, Plugins & integration, Build & modules) each with 1-3 sentence definitions and file pointers. Governs terminology across the entire codebase; cross-links to every other docs/architecture/*.md page.

*Keywords:* Glossar, Begriffe, Vorlage, Template, Sicht, View, Kalender, Modell, Block, Filter, Speicher, Sync, AccessLevel, Berechtigung, Besitzer, lastChangedBy, PRD 097, PRD 074, PRD 090, Terminologie

### legacy-urls.md

Documents the URL-space consequences of the Spring Boot migration (PRD 031) that moved the context root from /rapla to /. Explains that externally-subscribed server routes (CalendarPageController, Export2iCalController) deliberately kept the literal /rapla/ prefix per AGENTS.md §15's allow-list, while client-side URL generators (Swing dialogs, iCal export, autoexport links) must emit that prefix themselves or produce silent 404s. Details the UrlEncryptor wire format (salt embedded in the returned string, must be appended raw into query strings, not URL-encoded or split), the rapla.legacy-context-path / LegacyPathFilter mechanism for Rapla 2.0 upgrade compatibility (PRD 109, request-wrapping not forwarding, to preserve the Spring Security chain), and the removed Rapla 2.0 JAX-RS REST API with its GraphQL replacements. Governs CalendarPageController, Export2iCalController, UrlEncryptor, LegacyPathFilter (org.rapla.server.spring.web).

*Keywords:* URL, Kontextpfad, context path, /rapla/, UrlEncryptor, LegacyPathFilter, PRD 109, PRD 031, iCal, Export2iCalController, CalendarPageController, Migration, Rapla 2, Verschlüsselung, salt, Abonnement, REST API

### locking.md

Reference doc on Rapla's concurrency and locking model in a multi-pod deployment. Describes the three lock layers — the per-JVM process lock (RaplaLock/DefaultRaplaLock wrapping ReentrantReadWriteLock), the cluster-wide resource lock keyed by entity id, and the global lock (GLOBAL_LOCK row) for DynamicType/schema changes — all backed by the DB WRITE_LOCK table with lease expiry for crash self-healing. Documents the ordering gotcha where DBOperator.dispatch validates/checks permissions before acquiring the cluster lock (a time-of-check-to-time-of-use window, tied to PRD 035 OQ#10), and the RemoteOperator lock-order-inversion deadlock fixed in PRD 029 Phase 4 (compute under lock, fire listener events outside it). Governs org.rapla.storage.impl.DefaultRaplaLock, RaplaSQL/LockStorage, DBOperator, RemoteOperator, CachableStorageOperator, NotificationService, SynchronisationManager.

*Keywords:* Locking, Concurrency, WRITE_LOCK, ReentrantReadWriteLock, DefaultRaplaLock, DBOperator, RemoteOperator, GLOBAL_LOCK, multi-pod, PRD 035, PRD 029, Deadlock, fireStorageUpdated, Sperre, Nebenläufigkeit, cluster-wide lock, resource lock, process lock

### what-changed-in-rapla-3.md

Historical architecture record contrasting the legacy master branch with the current spring-boot branch rework, explaining what changed structurally and why — the move from restinject-based custom DI and hand-rolled JSON-RPC (org.rapla.enpoints, org.rapla.rest) to Spring Boot 4 with @HttpExchange interfaces and Jackson 3; the split from one monolithic Maven project into the 5-module reactor (rapla-bom/core/client/server/app, PRD 005); Date to java.time migration; the new Angular SPA (PRD 026); OAuth 2.0 PKCE auth (PRD 029); and the pure-Java presenter carve-outs from Swing (PRD 023) enabling the 4-tier test pyramid (PRD 017). Includes LOC comparisons and a size/complexity rationale. References PRDs 001, 005, 009, 010, 011, 017, 023, 024, 026, 029, 030.

*Keywords:* Migration, master branch, spring-boot branch, restinject, 5-module reactor, PRD 005, PRD 001, PRD 011, Jackson 3, Spring Boot 4, Angular SPA, PRD 026, OAuth, PRD 029, presenter carve-out, PRD 023, Test pyramid, PRD 017, JSON-RPC, Refactoring

### mvp-pattern.md

Architecture reference for Rapla's Model-View-Presenter carve-out pattern (PRD 023, built on by PRD 024/025) separating pure-logic models (rapla-core, no Swing/AWT/facade) from presenters (rapla-client, view interfaces) from Swing adapters. Catalogs existing pure models (RepeatingRuleProjector, AllocationConflictModel, AllocatableRowStatusModel, ClassificationFieldVisibility, ReservationEditSelection, NameSearchMatcher, PasswordChangePolicy, RaplaObjectActionPolicy, CalendarLayoutEngine, etc.) and presenters (CalendarPlacePresenter, ResourceSelectionPresenter, ConflictSelectionPresenter). Documents testing conventions per tier (tier-1 pure JUnit with reflect.Proxy stubs, tier-2 HeadlessPresenterTestSupport + RecordingView, tier-3 MockMvc), the permission-discipline pattern for id-filtering endpoints (AGENTS.md §12, CalendarViewController.resolveResourceFilter), and the step-by-step recipe for adding a new carve-out.

*Keywords:* MVP pattern, Presenter, Carve-out, PRD 023, PRD 024, PRD 025, RepeatingRuleValidator, AllocationConflictModel, AllocatableRowStatusModel, HeadlessPresenterTestSupport, RecordingView, PermissionController, Swing adapter, View interface, rapla-core, rapla-client, Tier-1 test, data leak prevention, Berechtigung

### overview.md

The top-level architecture map for Rapla, a resource-scheduling and event-planning system. Explains the five-module Maven reactor (rapla-bom, rapla-core, rapla-client, rapla-server, rapla-app) with their key packages and dependency edges (rapla-server/rapla-client → rapla-core → rapla-bom), the runtime topology (Swing client with RemoteOperator/LocalCache/RaplaFacade talking HTTP/JSON+JWT to rapla-app's Spring Boot/Tomcat server with FileOperator/DbsqlOperator/ConflictFinder over rapla.xml or JDBC), the polling-based client-server sync model, and the internal package layering of rapla-core (entities, facade, storage, framework, rest, scheduler, plugin). Documents the recurring Spring DI footgun: rapla-client uses @ComponentScan + @Service, while rapla-server/rapla-app wires server-internal classes via explicit @Bean factories in ServerCoreConfig/ServerServiceConfig — adding @Service on the server side is silently dead code. Entry point for navigating to domain-model.md, dynamic-types.md, conflicts-and-events.md, reservation-edit.md, permissions.md, extension-points.md, flows.md.

*Keywords:* Architecture overview, 5-module reactor, rapla-core, rapla-client, rapla-server, rapla-app, RemoteOperator, RaplaFacade, Spring DI, ComponentScan, Bean factory, ServerCoreConfig, Tomcat, JWT, FileOperator, DbsqlOperator, Modulstruktur, Übersicht, polling, AGENTS.md

### permissions.md

Canonical reference for Rapla's grant-only, additive, role-based, hierarchical, time-aware permission model (ADR 0003 revised, PRD 090). Explains the nine-level AccessLevel enum (DENIED..ADMIN) and its bands (read/allocate/edit), the PermissionImpl row shape (user/group, accessLevel, absolute pStart/pEnd and relative minAdvance/maxAdvance windows), the max-wins additive resolution algorithm (no precedence between USER/GROUP/WORLD, DENIED is inert and deprecated in the UI), the target-entity access-level matrix (DynamicType/Allocatable/Reservation/Category), and server enforcement points in SecurityManager and PermissionController (read filter getVisibleEntities, allocate gate hasPermissionToAllocate, canRead on Reservation, checkModifyPermissions). Distinguishes canReadInformation (READ_NO_ALLOCATION, resource visibility) from canRead (READ, booking visibility), notes the PermissionIndex caveat from PRD 082/083, covers JWT-to-User resolution, RequestStatus workflow, and gives a worked Alice/Room-A101 example.

*Keywords:* Permissions, Berechtigung, AccessLevel, PermissionController, PermissionImpl, grant-only, additive, ADR 0003, PRD 090, DENIED, READ_NO_ALLOCATION, ALLOCATE, canReadInformation, canRead, SecurityManager, RequestStatus, JWT, Sichtbarkeit, Gruppe, PermissionIndex, PRD 083

### reservation-edit-ui-inventory.md

A functional inventory of every pane, control, and behavior in the Swing reservation-edit dialog (ReservationEditImpl and its sub-editors under rapla-client's client/swing/internal/edit/reservation), companion to reservation-edit.md's flow description. Documents the classification pane (type dropdown, attribute fields, permissions editor), the appointment list/editor (AppointmentController's repeating-type modes, exceptions dialog, split-to-singles, next-free-slot search), and the resource picker (AllocatableSelection's dual tree-tables, per-resource availability status via AllocatableRowStatusModel, sparse restriction editing via popup checkboxes, name/classification filtering). Ends with a capability checklist intended as the coverage/deviation checklist for the SPA event sheet (PRD 091) — used to decide what a new frontend must replicate, replace, or consciously drop.

*Keywords:* Reservation edit UI, ReservationEditImpl, AppointmentController, AllocatableSelection, AllocatableRowStatusModel, PRD 091, Termin, Serie, Ausnahme, Ressourcenauswahl, Restriction, Undo, Swing dialog, capability checklist, SPA event sheet, Verfügbarkeit, Berechtigung

### reservation-edit.md

The most detailed flow trace in the architecture docs: the end-to-end reservation-edit dance across clone semantics (editListAsync), async-on-EDT Promise handling, the pluggable EventCheck validation chain, and per-dialog/global CommandHistory undo. Documents the REST wire model (UpdateEvent envelope, Reservation/Appointment/Repeating JSON shapes, dispatch as the sole write path), the classes involved (RaplaCalendarViewListener, EditController, EditTaskPresenter, ReservationEditImpl, AppointmentController), the delete/move/resize scope-dialog cascades (EVENT/SERIE/SINGLE, PRD 101's moveReservations/moveAppointment/splitOccurrence GraphQL verbs replacing PRD 056's sketch), the four-widget date/time interlock mechanics, the full command/undo catalog, and a long list of domain edge cases (sparse restrictions, all-day boundary handling, weekday-flip on move, classification EAV shape). Closes with the SPA recurrence editor's deliberate deviations from Swing (PRD 091 Phase 4).

*Keywords:* Reservation edit flow, editListAsync, EventCheck, CommandHistory, UpdateEvent, AppointmentController, PRD 101, moveAppointment, splitOccurrence, moveReservations, PRD 056, Serie, Ausnahme, Restriction, Termin verschieben, Undo, SPA recurrence editor, PRD 091, Klassifikation, RaplaNewVersionException

### rest-api.md

A catalog of every HTTP REST endpoint the Rapla Spring Boot server exposes to browser clients, organized by audience rather than controller. Covers URL conventions (the /api/ prefix, six excluded legacy controllers), OpenAPI SpringDoc grouping (auth/client/rest/exports), JWT bearer auth and the public endpoint allow-list, Jackson 3 field-based wire format rationale, the historical Jackson-2-vs-3 OpenAPI spec mismatch (resolved by PRD 041's build-time spec capture), and the error envelope. Enumerates RemoteStorageController (/api/storage — resources, queryAppointments, dispatch as the sole write path, refresh long-poll), the bulk REST sugar (/api/events, /api/resources, PRD 009), dynamic-types/locale endpoints, edit-time pre-checks (ReservationEditController), the server-side CalendarViewController, admin panels (PRD 020), import/export, and common SPA recipe flows (boot, create/edit reservation, logout). Cross-references reservation-edit.md for payload shapes and permissions.md for 401/403 semantics.

*Keywords:* REST API, RemoteStorageController, /api/storage/dispatch, queryAppointments, PRD 009, PRD 041, PRD 020, OpenAPI, SpringDoc, JWT, Jackson 3, SecurityConfig, CalendarViewController, dynamictypes, admin panels, ReservationEditController, Endpunkte, REST-Schnittstelle, oauth2 token

### spa-selection-and-actions.md

Reference doc for row/item selection and multi-select bulk actions in the Angular SPA, covering the generic table view (ViewHostComponent) and the resource rail (ResourceSelectionComponent), both driven by the shared headless TableSelection<K> engine (views/table-selection.ts). Documents Swing/Excel-parity pointer semantics (click/Strg-click/Shift-click/right-click), keyboard map, accessibility (active-descendant pattern), the planned touch/mobile selection mode, how selection feeds RowContext for menu providers (single-row 'primary' rule, bulk-delete subset-wins with best-effort composite-undo), and the resource rail's chip-as-filter model where FilterStore stays the source of truth. Derives from PRD 099 (selection + bulk actions) and PRD 094 (row menu/command/undo infra); Swing analogs are SwingTableView and RaplaTree/MenuFactoryImpl.

*Keywords:* Selection, TableSelection, ViewHostComponent, ResourceSelectionComponent, PRD 099, PRD 094, RowContext, bulk delete, Mehrfachauswahl, FilterStore, UndoToastService, Angular SPA, Auswahl, keyboard navigation, row menu, SpaCommand, PRD 077, PRD 095

### swing-platform-quirks.md

A record of platform/compositor bugs that the Swing client works around in code, kept so the workarounds aren't relitigated. Documents in detail the WSLg/XWayland filter-popup positioning bug: a Weston compositor z-order race stacking transient windows below their owner, and an XWayland oversized input-region hit-test leak that swallows clicks near the Filter button (linked to wslg#594/#801/#914 and JDK-8055834). Describes the FilterEditButton.java workaround (toFront() restack, popup positioned to the right of the button instead of below, dynamic button height instead of a hardcoded offset, deferred focus-loss dismissal, Escape key binding) and states the condition under which the workaround should be reverted once upstream fixes land.

*Keywords:* Swing, WSLg, XWayland, FilterEditButton, Filter popup, JDK-8055834, input region, z-order, Weston compositor, WSL2, platform quirk, workaround, Kompositor-Bug, click-through, toFront

### tableview-and-graphql-views.md

A capability-benchmark reference comparing the legacy Swing/HTML TableView plugin config (org.rapla.plugin.tableview.internal.TableConfig — column function compositions via ParsedText, view definitions, sort strings) against the new GraphQL-native declarative view system (PRD 074, function-equivalents in PRD 073). Uses the dhbw deployment's real 'Termine'/appointments view (columns Name/Beginn/Ende/Kurs/Person/Raum/Dauer, with dummy Prof. X data per AGENTS.md §17) as ground truth, then shows the equivalent GraphQL query rooted at appointmentBlocks with inline Appointment.resources(filter:) for per-type columns (Kurs/Raum/Person split via typeKeyIn/isPersonEq) and CalendarModel-derived ReservationFilter for saved view state. Concludes all three legacy views (events, appointments, appointments_per_day) are reproducible with zero or minimal (@group/@hidden) directives, referencing PRD 059 (typed where predicates), PRD 066 (allocatableMatching), and PRD 065 (typeGroup).

*Keywords:* TableView, TableConfig, GraphQL views, PRD 074, PRD 073, appointmentBlocks, ReservationFilter, typeKeyIn, allocatableMatching, PRD 066, PRD 059, PRD 065, ParsedText, Termine, Tabellenansicht, column definition, dhbw, Kurs, Raum, Person
