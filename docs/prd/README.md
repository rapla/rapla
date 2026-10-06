# PRD index

50 active PRDs in this directory, 73 done under `done/`, 7 under `wont-fix/`. AGENTS.md §2 and the `prd-management` skill cover the lifecycle (move to `done/` when complete; `git mv` back to reopen).

Each active PRD below carries a generated header (status, locked decisions, dependencies, governed code) plus a keyword line in German and English so that agents and search find it from either language. When a PRD's status or decisions change, update its header here in the same edit (wrap-up checklist).

> Numbering note: `031` exists twice (`031-api-namespace-redesign.md` active, `031-token-refresh-and-api-keys.md` done). Pick the next free number, never reuse.

## Active


### Auth / OAuth / IdP

### 030-server-side-view-rendering.md

In-progress PRD (Phases 1-6 landed 2026-05-12; Phase 7-9 migrate Swing table views) moving every read-side render decision — calendar layout, table-row projection, CSV export — to the server so clients become thin viewers receiving pre-projected records instead of full entity graphs. Introduces TableViewEngine + TableRow (rapla-core), /table/reservations, /table/appointments, /table/config REST endpoints, a shared BlockColors helper, and /export/csv, aiming to cut wire payload ~10x and eventually delete the rapla-server→rapla-client back-edge (PRD 005 D3). A 2026-07-07 correction notes the calendar-layout core (CalendarLayoutEngine, RenderedBlock, CalendarPage) was deleted 2026-05-27 as unused — only BlockColors survives, now consumed by PRD 095. Cross-references PRD 020 (admin panel pattern), PRD 024 (server-side edit services, calendar layout prior art), PRD 026 (Angular, primary consumer), PRD 028 (power search), PRD 009 (bulk storage REST). Governs org.rapla.plugin.tableview, TableViewController, CalendarViewController, ExportController, RaplaTableColumn, ReservationTableViewFactory in rapla-core/rapla-server/rapla-client.

*Keywords:* server-side rendering, TableViewEngine, PRD 030, CalendarLayoutEngine, RenderedBlock, BlockColors, table view, CSV export, PRD 005 D3, Tabelle, Kalenderansicht, TablePage, TableColumnConfig, back-edge, RaplaBuilder, Swing table, CellExtractor

### 043-api-keys-jwt-pat.md

PRD 043, status in-progress (server-side + docs complete, Angular UI shipped 2026-06-27, Swing deferred), designs GitHub-PAT-style API keys for rapla: server generates a fresh asymmetric keypair (RSA-2048 or Ed25519) per key, signs one JWT with it, stores only the public key/JWK in RaplaKeyStorage's multi-slot APIKEY prefs role, and discards the private key so nobody can mint another JWT for that key. Endpoints POST/GET/DELETE /api/auth/api-keys; ApiKeyJwtDecoder dispatches by JWT typ claim alongside the access/refresh decoder. Supersedes PRD 031's API-key section; the legacy TokenHandler/SignedToken HMAC path this coexisted with has since been fully deleted (update note). Angular UI (ApiKeysDialogComponent) adds scopes and grace-window rotation per PRD 076. Related to PRD 041 (RefreshSessionService pattern) and PRD 035/060 (MCP token consumers).

*Keywords:* API-Key, Personal Access Token, JWT, RaplaKeyStorage, ApiKeyController, ApiKeyJwtDecoder, Ed25519, RS256, kid, thumbprint, Bearer token, PRD 043, PRD 031, PRD 041, PRD 076, Widerruf, revoke, Schlüssel, Anmeldedaten, Rotation

### 076-scoped-api-keys-self-rotation.md

Reopened PRD describing rapla's API-key security model: scoped data permissions (read, write_events, write_resources, write_all) plus a self-rotation capability (rotate_self) so a key can replace itself without holding write power. Phases 1-3 (scope enforcement, self-rotation, grace-expiry) shipped 2026-06-21; Phase 4 (no-legacy default, config token-gate) landed; Phase 5 (api-keys confined to a GraphQL-only allow-list plus a field-level access_details directive) is planned but not built; Phase 6 (interactive SPA rotation, graceMinutes, chain capping, expired-entry compaction) is in progress with decisions D11-D15 locked. Builds on PRD 043 (API key JWT mechanism) and PRD 071. Governs ApiKeyController, ApiKeyJwtDecoder, ApiKeyScopeContext, LocalAbstractCachableOperator.check(), and docs/authentication.md.

*Keywords:* API key, Scope, rotate_self, Rotation, access_details, Berechtigung, JWT, PRD 043, PRD 071, ApiKeyJwtDecoder, ApiKeyScopeContext, self-rotation, graceMinutes, least privilege, Sicherheit, Schlüssel, read-only key, write_all

### 102-browser-credential-hardening.md

PRD 102 defines how rapla contains its browser session credential (access_token/refresh_token cookies) against untrusted same-origin content, split out of PRD 072. Split-out driven by PRD 097's semi-trusted document templates landing on the main origin. Status: in progress, Phase 1 (enforced non-script CSP on /app) landed 2026-07-10; Phases 2-6 (capability-mint endpoint, refresh-gap fix, untrusted render-path hardening, script-src XSS backstop) open. Core decisions: keep the HttpOnly access_token cookie rather than migrating to memory-only (D1, reverses an earlier plan); untrusted pages get an opaque origin via CSP sandbox + connect-src none (D2, the single load-bearing control); scoped {read} capability tokens reused from PRD 076's API-key scopes for untrusted pages needing data (D3); also specifies the later interactive-document tier (D4-D9): two CSP tiers by component usage, deployment script allowlist not nonce, precompiled custom elements via a component registry, native-form save with a sealed write-capability token, untrusted-author model, opt-in author-JS escape hatch. Governs SecurityConfig, RaplaCspHeaderWriter, ApiKeyScopeContext; implemented jointly with PRD 097 Phase 9.

*Keywords:* CSP, Sandbox, Cookie, Zugriffstoken, access_token, Sicherheit, Same-Origin, Capability-Token, Berechtigung, GraphiQL, Refresh-Token, PRD 072, PRD 076, PRD 097, XSS, Formular speichern, write-capability, opaque origin


### 128-picker-conflicts-requests-chip-model.md

Implemented 2026-10-05 (c3ddea7a0/c72cdcff1, on dhbw-test): conflicts and resource requests as VIEWS in the view list (D7 — the view decides what is queried, the renderer how it is drawn; red badges with active counts; left pane switches to the conflict/request tree), GraphQL conflicts(filter:)/conflictStats/resourceRequests(filter:) with an open-request index, anonymous "nicht sichtbar" calendar blocks of unreadable reservations on readable resources like Swing (no clashes/slots), and the chip model behind them — chips contribute scope (what loads) and/or focus (what is drawn normally, the rest pale, like Swing's RaplaBuilder); conflict and request become own chip kinds; two selection contexts Planen (resources, users) and Prüfen (conflicts, requests). Needs user-scoped all-conflicts / all-requests queries (PRD 064 deferred allConflicts). Builds on PRD 127.

*Keywords:* Konflikte, conflicts, Ressourcenanfragen, resource requests, Chip, scope, focus, Fokus, blass, Planen, Prüfen, allConflicts, ConflictImpl.getMap, getRequestMap, RaplaBuilder, filter-store, PRD 064, PRD 078, PRD 127

### GraphQL API

### 060-graphql-mcp-foundations.md

PRD 060 extracts from PRD 035 the cross-type GraphQL discovery/compute surface and the MCP (Model Context Protocol) transport for AI assistants: new query roots (categories, users, periods, conflicts, templates, serverTime), a global search root, three compute primitives (findFreeSlots, checkConflicts, whoIsFree), and an MCP shape of one read-only graphql_query tool plus a graphql_schema tool plus curated, confirmation-gated mutation tools like book. Status: draft, design locked 2026-05-29, with the MCP transport itself gated on verifying Spring AI MCP starter's Spring Boot 4/Jackson 3 alignment (Phase 2 spike). Many §12 leak and window-boundary open questions (OQ-A through OQ-G) remain unresolved, including conflict symmetry/N-way shape and default/max search window sizing. Depends on PRD 035, PRD 055/056 (events surface), PRD 028 (power search), PRD 043 (API keys for MCP auth).

*Keywords:* MCP, Model Context Protocol, GraphQL Discovery, findFreeSlots, checkConflicts, whoIsFree, Suche, search, Konflikt, Verfügbarkeit, graphql_query, graphql_schema, book tool, API Key, Spring AI, PRD 035, PRD 028, freie Slots

### 061-graphql-dt-mutations-v2.md

PRD 061 is a draft follow-up (opened 2026-05-29) to PRD 057's v1 DynamicType GraphQL mutations, addressing five deferred hardening items needed before admins can safely edit schemas against live production data: (1) a valueType-change migration policy matrix (COERCE/REJECT/COERCE_OR_REJECT, e.g. STRING→INT) to prevent silent data loss, (2) DefaultValueInput @oneOf variant-vs-valueType/multiplicity validation, (3) a strict annotation allow-list (name-format, colors, editView, etc.) replacing v1's open-ended key-value input, (4) hot-swap latency tightening via a direct post-mutation schema rebuild trigger (SaveDynamicTypeResult wrapper, breaking change), and (5) richer deleteDynamicTypes referrer reporting with per-kind counts and a paginated referrersOf query. Triggered by the Angular schema editor UI. Governs DynamicTypeMutationController, GraphQlSchemaRebuilder. References PRD 057 (parent), PRD 055 (hot-swap mechanism, connection pagination), PRD 056 (error taxonomy), PRD 062 (shared robustness patterns).

*Keywords:* DynamicType Mutation, Schema Editor, valueType Änderung, Migration Policy, DefaultValueInput, Annotation, Hot-Swap, GraphQlSchemaRebuilder, deleteDynamicTypes, referrersOf, REFERENCE_EXISTS, saveDynamicType, Attribut, Typänderung, PRD 057, coercion, SaveDynamicTypeResult

### 062-graphql-api-robustness.md

PRD 062 (renumbered from 058 due to a collision with the key-spec migration PRD) is a draft placeholder/parking-lot cataloguing GraphQL API robustness patterns rapla deliberately defers given its low current traffic: in-flight idempotency locks for concurrent retries, idempotency-key TTL/cache eviction, rate limiting (including a 2026-08-12 per-user read-concurrency-limit design tied to PRD 106), query/mutation complexity limits, request timeout and read-cancellation-on-disconnect (also tied to PRD 106), and distributed tracing on mutations. Explicitly not slated for implementation; each pattern lists a concrete production-evidence "defer trigger" before promotion to its own PRD. Depends on PRD 035 (foundations), sibling to PRD 056/057/061 (mutation surfaces); the shipped lean idempotency baseline (client UUIDs, ID_COLLISION) lives in PRD 056 itself, not here.

*Keywords:* API Robustness, Idempotenz, Rate Limiting, Idempotency Lock, TTL Cache, Query Complexity, Timeout, Cancellation, Distributed Tracing, WebGraphQlInterceptor, PRD 106, concurrency limit, StoredViewInterceptor, DoS, Multi-Tenant, parking lot, graphql-java

### 073-graphql-function-equivalents.md

PRD 073 (in-progress) is the reference equivalence map between rapla's server-side expression-language Functions (`org.rapla.entities.extensionpoints.Function`, ~34 functions across `StandardFunctions`, `AppointmentNoteFunctions`, `DurationFunctions`) and the GraphQL read API, identifying which Functions are fully covered, partial, or gaps. Introduces a `FunctionDescriptor` SPI so plugins declare functions in rapla terms (source-arg type, return type), letting a central generator emit GraphQL fields/catalog/type-checks uniformly — shipped: `Query.computeFunctions` catalog, descriptor-driven fields on `AppointmentBlock`/`Appointment` (`note`, `number`, `date`, `lastchanged`). Converges with PRD 074 on keeping rapla's own `ParsedText` engine as the composition bridge (no CEL) exposing `displayName`/`name(variant:)`/`compute()` fields, and with PRD 075 on function-naming standardization. Establishes the 'focused input over shared filter' schema-design guideline after a real contract-lie defect on nested `Appointment.allocatables(filter:)`. Governs `ClassificationSdlGenerator`, `FunctionFieldGenerator`, `ComputeFunctionsController`, `GeneratedClassificationWiring`.

*Keywords:* Function, ParsedText, EvalContext, FunctionDescriptor, computeFunctions, nameformat, displayName, AppointmentBlock, duration, note, isLocation, isPerson, PRD 073, PRD 074, PRD 075, Ausdruckssprache, GraphQL Feld, Klassifikation, ClassificationSdlGenerator, compute

### 074-graphql-declarative-views.md

PRD 074 (draft, 2026-06-20) designs declarative, admin-authored GraphQL 'saved views' replacing legacy TableView (PRD 030, frozen/deprecated) with no client-side expression engine: composition columns (nameformats, duration, times) are server-evaluated via rapla's existing `ParsedText` engine and exposed as plain GraphQL fields; selection/filtering stays in GraphQL (PRD 059/066/069); presentation (column order, headers, joins, grouping) is convention-driven with optional directives (`@view`, `@column`, `@join`, `@group`, `@bucket`). Views are stored as persisted query text (no invented binding format, per ADR 0005) with revalidate-and-mark on schema change (no auto-migration). Three canonical views ship as immutable BUILTIN code constants (`Termine_events`, `Termine_appointments`, `Termine_perDay`); admins author/fork via GraphiQL with `saveView`/`listViews`/`deleteView` mutations. CEL and client transform pipelines were evaluated and dropped. Depends on PRD 073 (data-layer feeder, op-set catalog), PRD 059, PRD 066, PRD 069, PRD 028, PRD 077 (persistence mechanics), supersedes PRD 030.

Phase 5 (2026-10-06, committed 94b5b0099/8e0e43e51): `appointmentBlocks(filter, sort, after:)` cursor paging replaces offset — `extensions.view.page { limit, returned, hasMore, endCursor }`, the cursor carries its sort spec (+ language for NAME), limit default 1000 / max 2500, no total cap; `select()` no longer stops after `limit` reservations for blocks/stats; SPA "Weitere laden" appends pages and a header click on Datum/Zeit/Bis/Titel sorts server-side (START/END/NAME) over the full set, other columns client-sorted with a hint. Residues: prev/next, sort by joined columns, `rapla_reservations` paging, failed load-more replaces the table.

*Keywords:* saved view, GraphQL view, TableView, ParsedText, displayName, compute, saveView, listViews, GraphiQL, appointmentBlocks, Termine, Spalte, column, extensions.view, PRD 074, PRD 073, PRD 030, Vorlage, Kalenderansicht, Sortierung, Cursor, after, endCursor, hasMore, Weitere laden, load more, Paginierung, pagination, BlockSort, limit

### 075-expression-language-standardization.md

PRD 075 is an investigation (draft, no implementation) reopened 2026-06-20 to evaluate whether an alternative expression language (CEL, JSONata, JMESPath, JEXL, restricted SpEL, or a formalized rapla DSL) should replace rapla's own `Function`/`ParsedText` engine for any of its use cases (view compute cells, nameformat/displayName compositions, ClassificationFilter predicates, SPA search, export/template formatting, attribute validation, Dualis import field-mapping). Multi-agent findings (2026-06-20) conclude: keep rapla Functions for ALL composition/export/view surfaces (no engine beats ParsedText's prefix-call template shape); only two genuinely greenfield surfaces — attribute validation and Dualis import mapping — could justify a narrow second engine (CEL preferred, restricted SpEL as zero-dependency fallback). Also resolves the naming sub-question: adopt SQL/spreadsheet-vocabulary aliases (NOT/AND/OR/IF/CONCAT/SUBSTRING/EQ/COALESCE) additively in `FunctionFactory.createFunction`, scoped to the composition family only. Feeds into PRD 073 and PRD 074's engine-choice conclusions.

*Keywords:* Expression Language, ParsedText, Function, CEL, JSONata, JMESPath, JEXL, SpEL, FunctionFactory, Namenskonvention, alias, PRD 075, PRD 073, PRD 074, Ausdruckssprache, Validierung, Dualis Import, COALESCE, EQ

### 077-calendar-model-graphql.md

Draft/in-progress PRD replacing the legacy Swing CalendarModel/saved-calendars with a GraphQL-native model covering SavedView persistence, view switching/conversion, and week/month calendar render-modes; carved out of PRD 074 (which owns the table render layer) and reuses PRD 078's transport/renderer. Locks scope/domain and render-mode as orthogonal axes, defers input-control machinery, and resolves OQ7 (week grid render model, 2026-07-14) with a full-day fixed raster, worktime shading via a new CalendarOptions query, and a server-driven all-day/multi-day banner band coupled to PRD 097 Phase 5. Month grid shipped via PRD 095; week grid prototyped with WeekGridComponent/week-lanes.ts. Governs the Angular SPA calendar rendering and server view/window contract.

*Keywords:* CalendarModel, SavedView, Kalender, Wochenansicht, Monatsansicht, Termin, Serie, Banner, week grid, WeekGridComponent, week-lanes.ts, PRD 074, PRD 078, PRD 095, PRD 097, worktime, CalendarOptions, Ansicht wechseln, render-mode

### 081-graphql-omnibox-multisearch.md

PRD for the SPA omnibox's unified, typed, ranked, §12-scoped GraphQL search across resources/events/occurrences/groups/users. Phase 1 (RESOURCE+EVENT+USER kinds) landed 2026-06-21/24 via a new search(query,kinds,limit) resolver returning kind-bucketed SearchGroup/SearchHit types (ResourceHit/EventHit/GroupHit/UserHit); EVENT is edit-gated (canModify, stricter than read) and windowless (a true name index, not date-scoped); RESOURCE search is FUZZY-enabled, EVENT is SUBSTRING/PREFIX-only. GroupHit exposes an opaque memberFilter so deployment-specific hierarchy knowledge stays server-side. Mandatory §12 leak tests (data-leak-prevention skill) verify no existence leak. Phases 2 (groups) and 3 (occurrences/SAVED_VIEW) are planned. Consumed by PRD 078's SPA; depends on PRD 028's allocatable evaluator and PRD 077's group model.

*Keywords:* Omnibox, Suche, search resolver, SearchHit, ResourceHit, EventHit, GroupHit, UserHit, memberFilter, canModify, FUZZY, §12, PRD 028, PRD 077, PRD 078, Gruppe, Person suchen, scope chip, windowless search, existence leak

### REST API / server architecture

### 020-server-driven-admin-panels.md

In-progress PRD (foundation plus most phases done) replacing per-plugin Swing PluginOptionPanel classes and ad-hoc dhbw HTML admin pages with a generic server-driven renderer: server publishes PanelDefinition (fields, actions, current values) via a new PreferencesAdminService wire contract (@HttpExchange /admin/panels) and PreferencesPanel server SPI (Set<PreferencesPanel> Spring beans); client renders with a fixed Swing widget toolkit (ServerDrivenSettingsDialog, FieldRenderer per FieldType). Phases 1-5 done (contract, dispatcher, generic renderer, legacy-panel bridge, plugin-gate audit); Phase 6 migrated 5/11 vanilla panels (PlanningStatus, AppointmentNote, CSVExport, AutoExport, Timeslot) to @Service PreferencesPanel beans; Phase 7 shipped four concrete dhbw panels (Terminal, Morada, LDAP role-mappings, Dualis) in the dhbwrapla repo, also fixing seven missing @Service annotations in dhbwrapla's Spring bean graph. Phase 8 cleanup partial. Supersedes part of PRD 003 §I; mirrors PRD 012's metadata-driven pattern; depends on PRD 019 for startup ordering.

*Keywords:* PreferencesAdminService, PreferencesPanel, PRD 020, FieldRenderer, PanelDefinition, ServerDrivenSettingsDialog, admin panels, Einstellungen, Berechtigung admin, TerminalPreferencesPanel, MoradaPreferencesPanel, LdapRoleMappingsPreferencesPanel, DualisPreferencesPanel, PluginOptionPanel, AdminPanelsScanConfig, isAdmin, Vorlage server-driven

### 041-openapi-runtime-removal.md

PRD 041, status in-progress (Phase 1 done 2026-05-16, Phase 2 deferred), moves OpenAPI spec generation from runtime SpringDoc reflection to build-time capture (OpenApiSpecCaptureTest), removing Jackson 2 (SwaggerJacksonConfig) from rapla-app's production classpath; springdoc becomes test-scope only, served via StaticOpenApiController at runtime. Also documents substantial adjacent OAuth-refresh consolidation work (2026-05-16) that landed in the same session: RefreshSessionService as single source of truth for refresh tokens, custom Spring Authorization Server providers, never-rotate refresh policy, deletion of /api/auth/login and AuthController in favor of /oauth2/token and /oauth2/revoke. Spun off from PRD 035 and feeds into PRD 043 (API keys). Phase 2 (per-plugin endpoint tagging + runtime filter) is deferred pending a plugin-id tagging mechanism.

*Keywords:* OpenAPI, SpringDoc, Jackson 2, Jackson 3, RefreshSessionService, /oauth2/token, /oauth2/revoke, OAuth, Refresh Token, Scalar, Swagger UI, PRD 041, PRD 043, PRD 031, PRD 035, Anmeldung, Login, API-Dokumentation, build-time spec

### 067-server-mutation-unification.md

PRD 067 (draft, opened 2026-06-10, decisions D1-D11 locked) is a major architectural refactor splitting RaplaFacade into a stateless sync 'EntityLifecycle' in rapla-core (reached via `operator.getLifecycle(user[,templateId])`) and a thin Swing client facade holding working-user/session state. Goal: zero RaplaFacade calls in server code, enforced by moving RaplaFacade/FacadeImpl/ClientFacadeImpl to rapla-client entirely (D9), making server-side facade usage a compile error. Establishes that GraphQL is THE server API (D4), the operator is the persistence+lifecycle layer, and ownership only changes via an explicit `changeOwner` mutation (D10, never at creation). Fixes confirmed drift bugs where GraphQL mutation controllers hand-rolled create/clone logic missing `copyPermissions` and appointment re-id. Depends on/relates to PRD 056, PRD 063 (write APIs to migrate), PRD 048, PRD 019, PRD 005 (reactor split), PRD 068 (Dualis, independent). Governs StorageOperator, AbstractCachableOperator, EntityLifecycle, RaplaFacade/FacadeImpl, GraphQL mutation controllers across rapla-core/server/app/client.

*Keywords:* EntityLifecycle, RaplaFacade, FacadeImpl, StorageOperator, Berechtigung, copyPermissions, changeOwner, Eigentümer, GraphQL Mutation, workingUserId, PRD 067, Facade Split, operator.getLifecycle, UpdateEvent, dispatch, Termin erstellen, Reservierung klonen

### 083-user-change-subscription.md

In progress (state 2026-10-05). Part A: first cut built — `PermissionIndex` caches each user's readable allocatable set via the real PermissionController, live in every GraphQL request, invalidated at the `updateReadModel` seam (local + other pods); the inverted `access_grant` index is not built; AQ5 answered by PRD 129's group cache. Part B: `changesSince(since)` GraphQL poll over the existing EntityHistory/UpdateResult delta — not built; concept 2026-10-05: relevance filter runs PermissionController + PRD 129 cache directly on the small delta (no `access_grant` needed), group/hierarchy/type/preference change → `resync` (B-D1), `eventsChangedForOwners` for user chips (B-D2), wire names `resourceId` (B-D3), SPA poller reloads lean list / view / PRD 128 Prüfen counts and on tab visibility (B-D4); open SQ1/SQ3/SQ4/SQ5 (poll interval). Strict §12 no-existence-leak requirements throughout.

*Keywords:* access_grant, Berechtigung, canRead, canAllocate, changesSince, EntityHistory, UpdateResult, Änderungsbenachrichtigung, PermissionController, AccessLevel, PRD 082, PRD 086, PRD 087, §12, principal, Gruppe, Sichtbarkeit, resync, watermark poll

### 105-shared-reservation-checks.md

PRD 105 gives the Swing pre-save reservation warning checks (missing name, no resources, duplicate appointments, conflicts, holidays, request-only allocations, not-in-current-calendar) a server-side home so the Angular SPA — which today saves silently with no warnings — can show the same seven warnings via GraphQL. Status: in-progress; Phases 1-3 shipped 2026-08-12 (ReservationChecker SPI + StandardCheckers beans, reservationChecks GraphQL field, SPA confirm/abort dialog); Phase 4 (write-path enforcement, options UI) deferred. Ten decisions locked (D1-D10): dry-run query not warnings-on-save (D1), advisory not enforcing (D2), NOT_IN_CALENDAR only checks resource/owner intersection not classification filter (D3), order-deterministic checker beans with per-code CalendarOptionsImpl preference switches (D4), severity copied 1:1 from Swing (D7), per-occurrence checking (D9), one confirm dialog for every write path not an inline panel (D10). Governs rapla-core ReservationChecker/CheckContext, rapla-app StandardCheckers/ReservationCheckService/ReservationChecksController, and rapla-angular's reservation-checks.service.ts. Depends on PRD 023 Phase 10, PRD 091 (requestStatus dependency, shipped), PRD 094, PRD 067.

*Keywords:* Speicherprüfung, Warnung, Konflikt, Feiertag, Duplikat, Ressource fehlt, reservationChecks, ReservationChecker, EventCheck, Reservierung prüfen, Vorabprüfung, Bestätigungsdialog, PRD 091, REQUEST_PENDING, CalendarOptionsImpl, Swing-Parität

### Frontend / SPA

### 117-graphql-administration-entities.md

Draft 2026-09-14: GraphQL administration of users, groups, categories, periods and dynamic types (decisions E1–E16 open); the three security guards E2/E8/E9 and the admin-flag invariant are implemented and test-pinned, no GraphQL mutations yet. Sibling of PRD 113.

*Keywords:* administration, Verwaltung, users, groups, categories, periods, dynamic types, Benutzerverwaltung, GraphQL mutations, admin flag, E1–E16

### 119-spa-one-search-resource-picker.md

Draft 2026-09-15 (direction decided by the user after two clickable prototypes): the SPA resource picker opens empty and resources are searchable in two places, while omnibox event hits do nothing. Replaces both with one search field at the top that narrows the picker instantly, plus a dropdown with events only (jump to the week + open the event sheet). The picker becomes type chips (Alle, Favoriten, Zuletzt, one per resource type; users only as search hits) over a list; "Alle" shows favorites, recents and the first 20 A–Z; type chips show the resource hierarchy as a simple expandable tree, one level per categorization value (deliberate first cut, grouping will change again), computed server-side (no Swing tree parity — user ruling 2026-09-15; belongsTo nesting later), with "alle wählen" per node. The "Gruppe" tab and the omnibox resource/user/group actions go (behaviour inventory in the PRD). Data: the SPA loads one lean resource list once (id, kind, name, typeKey + new server field `groupPaths`, §12 leak test) and filters, ranks and builds the tree in the browser — no server call per keystroke; event search stays server-side and throttled, and finds every event the caller may read (was: may edit). Tree levels Swing-flat first. Amended 2026-10-05 (D13, supersedes D3): the picker gets its own field again (rail head row, left of "+ Neu"); the top field no longer narrows it, its count row copies the term into the picker field on click (mobile: switches to the resource screen). Phase 2b committed in `c72cdcff1`, on dhbw-test. Open: list size for large deployments (no perf test for now), narrow screens, keeping the list current (later), event search cost.

*Keywords:* resource picker, Ressourcenauswahl, ResourceSelection, omnibox, Suche, one search field, chips, Typ-Chips, Baum, resource tree, TreeFactoryImpl, categorization, belongsTo, packages, Gruppen, Gebäude, Studiengang, recents, favorites, event search, Terminsuche, PRD 119

### 121-rapla2-rapla3-shared-database.md

Draft 2026-09-30, number reserved: Rapla 2.1 and Rapla 3 on one shared database during the transition. The planning record (schema comparison, six legacy patches, BL1 BCrypt rehash, OQ1 keep DENIED, OQ3 bootstrap timezone offset, OQ6) lives in the private dhbwrapla docs; rapla carries only the stub plus the Rapla 3 side (bootstrap timezone offset 12fb1bfb4, DENIED rows kept on write cec7a8f54).

*Keywords:* Rapla 2, Rapla 2.1, legacy, shared database, gemeinsame Datenbank, Übergang, migration, timezoneOffset, DENIED, BCrypt, EntityHistory, PREFERENCE, dhbwrapla

### 124-spa-i18n.md

Draft 2026-09-30, concept only: the SPA is hard-wired German (`LOCALE_ID de-DE`, ~285–350 literal strings in 47–66 files, hand-rolled weekday arrays, partly English strings). D1 locked: runtime catalogue from the server — the SPA loads all texts of the user's language in one `/api/locale` request (existing `RemoteLocaleController`, Swing properties keys, English fallback server-side) plus a new `SpaResources` bundle translated via the PRD 103 process; dates via `Intl`/`LOCALE_ID`. Rejected: build-time `@angular/localize` and runtime `$localize`. Open: language precedence without user preference, per-user GraphQL names, Angular locale data loading, the `org.rapla.language` vs `org.rapla.locale` system-preference mismatch in `ServerLocaleResolver`, key naming; caching of `/api/locale` moved to PRD 125. Estimate 4–6 days.

Implemented 2026-10-06 (committed ba3c0d3d7/adedeec5e/e0a69ea33, live on demo + the customer test instance): OQ3 variant A — the server delivers entity names, attribute labels (`DynamicType.attributeNames { key name values { key name } }`, SDL spellings), built-in view titles and search headings in the request language (`RequestLanguage`, ThreadLocal per execution); the SPA overlays attribute labels from that query over the SDL shape; OQ5 — one language chain for API and login page: cookie → user preference → configured server language (`org.rapla.locale`, else `org.rapla.language`) → browser `Accept-Language` → JVM; stock categories get names in every shipped language at `createDefaultSystem`. Group names follow the request language too (ba3c0d3d7). Open: sibling sites A/B, OQ2.

*Keywords:* i18n, Internationalisierung, Übersetzung, translation, Sprache, language, locale, LOCALE_ID, attributeNames, Accept-Language, browser language, RequestLanguage, ServerLocaleResolver.configured, ViewTexts, createDefaultSystem, Benutzergruppen, Intl, DatePipe, @angular/localize, $localize, RemoteLocaleService, RemoteLocaleController, LocalePackage, ResourceBundleList, SpaResources, RaplaResources, org.rapla.language, org.rapla.locale, ServerLocaleResolver, MatPaginatorIntl, Wochentage, Caching, Cache-Control, PRD 103, PRD 026, PRD 072, PRD 124

### 125-spa-caching-version-skew.md

Draft 2026-09-30, concept only: the SPA is never cached — `SpaResourceConfig` serves all of `/app/**` with `no-store` (probed locally and on demo.rapla.org), although the Angular build hashes file names. Plan: hashed bundles `max-age=1y, immutable`, `index.html` `no-cache`; one build id per `mvn package` known to server and SPA (`ng build --define`), sent as `X-Rapla-Build` on every `/api` request; on a mismatch (tab open across a deploy, rolling deploy) the server only adds a mismatch header and the SPA shows a cancellable reload dialog — D1: no request is ever rejected, unsaved changes stay savable. `/api/locale` (PRD 124) gets ETag = build id + language with `private, no-cache`. Open: build id source, sticky sessions, dialog text key. Estimate 2–2.5 days.

*Keywords:* Caching, Cache-Control, no-store, immutable, ETag, 304, If-None-Match, SpaResourceConfig, outputHashing, build id, Build-ID, X-Rapla-Build, version skew, Versionsabgleich, veralteter Client, reload, Neu laden, rolling deploy, Reload-Dialog, /api/locale, PRD 026, PRD 118, PRD 124, PRD 125

### 026-angular-frontend.md

In-progress PRD defining the Angular SPA replacement for Swing's reservation-edit UI, served from the existing Spring Boot backend. v1 scope is reservation create/edit, allocatable selection, advisory conflict overlay, and repeating-rule editing; calendar views, plugin admin UIs and full resource/user administration are out of scope. Phase 0 prototype (login + read-only reservation list, Angular chosen, same-origin /app/ hosting) shipped 2026-05-12 with OAuth2 PKCE wired via angular-oauth2-oidc. Documents extensive pre-migration server work (PRD 024 edit services, 409 mapping for RaplaNewVersionException, draft/occurrence-expansion endpoints, OpenAPI/SpringDoc) and an options-panel REST migration table. Depends on architecture docs (reservation-edit.md, domain-model.md, dynamic-types.md, permissions.md) and GraphQL PRDs 055/059/066 for the calendar read substrate; execution of URL layout decisions moved to PRD 031. Governs rapla-angular/ tree and rapla-app SPA-serving config (SpaResourceConfig).

*Keywords:* Angular, SPA, rapla-angular, Reservation edit, PRD 026, PRD 024, PRD 031, OAuth2 PKCE, angular-oauth2-oidc, Vorlage, Termin, Serie, Konflikt, Berechtigung, Ressource, GraphQL, openapi-generator, TypeScript client, SpaResourceConfig

### 047-angular-frontend-plugin-model.md

PRD 047, status draft (2026-05-18), designs the Angular-frontend counterpart to PRD 045's server plugin model: custom deployments like dhbwrapla ship their own Angular views as runtime-loaded micro-frontends via Native Federation (not Webpack Module Federation, incompatible with Angular 21's esbuild builder), rather than forking rapla-angular. Model B (chosen) keeps dhbw code entirely out of the stock bundle; a RaplaFeature contract plus a RAPLA_FEATURE injection-token registry in the rapla-angular host assembles routes/nav dynamically, driven by a server-side /api/ui-config endpoint returning remote descriptors (RaplaUiRemote beans, collected like List<ServerExtension>). The same artifact shape also works as a PRD 045 section 4 drop-in server-plugin jar carrying both server beans and static/plugins/<id>/ frontend assets, served same-origin with no signing (unlike Swing's JNLP signing model).

*Keywords:* Angular, Frontend Plugin, Native Federation, Micro-Frontend, RaplaFeature, RAPLA_FEATURE, ui-config, RaplaUiRemote, dhbwrapla, remoteEntry.json, PRD 047, PRD 045, PRD 003, Plugin-Modell, SPA, rapla-angular, feature-api

### 078-spa-graphql-view-renderer.md

In-progress PRD for the Angular SPA's GraphQL view renderer: a generic ViewHostComponent that renders any server-declared view (from PRD 074's extensions.view contract) without per-view client code, using plain HttpClient/cookie auth (no Apollo, no tokens). Covers the transport (graphql.service.ts), type-driven variable binding (buildVariablesByType), server-resolved view.window date-nav seeding, the scope gate (resource/user/group chips, no query fires without scope), and execution routing (server-merged executeView vs authoring query). Phase 1-2 done, Phase 3 (grouping/component registry/pagination) partial, Phase 4 (authoring UI) dropped in favor of GraphiQL, Phase 5 (query lifecycle throttling) done via PRD 106. Downstream of PRD 074, upstream dependency of PRD 077/079/081. Governs rapla-angular graphql.service.ts, ViewHostComponent, variable-binder.ts.

*Keywords:* ViewHostComponent, extensions.view, graphql.service.ts, Scope-Gate, buildVariablesByType, Omnibox, Sichtbarkeit, view.window, PRD 074, PRD 077, PRD 079, PRD 081, PRD 106, GraphiQL, SPA, Ansicht, Ressourcenauswahl, resource chip, cookie auth, monaco-graphql

### 091-spa-reservation-edit-and-availability.md

PRD 091 is a large, actively evolving draft (updated through 2026-07-08) bringing reservation editing to the Angular SPA plus a new GraphQL resource-availability API, with the equipment-lending archetype as the first target. Phase 1 (resourceAvailability/potentialConflicts GraphQL queries) and most of Phase 2 (event-sheet skeleton, mutations, undo/redo D5, recurrence editor Phase 4.0-4.5) are done; Phase 3 (finder) and parts of Phase 4 (convert-to-single) remain open. Key locked decisions: D1 GraphQL-only availability API, D2 free-time search split to PRD 092, D3 client-generated ids (id-first drafts), D4 unified Conflict type, D5 in-sheet memento-based undo (pre-save only, contrasted with PRD 094's command-pattern post-save undo), D6 permanently deferred block-level/occurrence-granular availability detail. Depends on/relates to PRD 024, 026, 056/057/063, 060, 067, 077/078, 086, and spun off PRD 092 (free-slot search), 093 (loan lifecycle), 094 (main-view actions/undo), 096 (classification editor, closes §2.4 deferral). Governs AvailabilityGraphQLController, ReservationMutationController, event-draft.ts, event-sheet.component.ts, draft-history.ts, repeating-edit.ts in rapla-angular and rapla-app.

*Keywords:* Reservierung, Termin bearbeiten, Verfügbarkeit, resourceAvailability, potentialConflicts, Ressourcenfindung, Serie, Wiederholung, Konflikt, Buchung, Ausleihe, Belegung, event sheet, Undo, AvailabilityGraphQLController, GraphQL Mutation, PRD 092, PRD 093, PRD 094, PRD 096, gilt für, Restriction Map

### 092-free-slot-search.md

PRD 092 is a draft (2026-07-05), split out from PRD 091, designing a GraphQL freeSlots query that answers 'when are these resources free' via gap enumeration over busy intervals (using the PRD 086 appointment block index) rather than the legacy brute-force per-slot getNextAllocatableDate RPC scan. It defines two candidate modes (concrete slots vs. weekly-pattern search with quota ranking) but leaves the mode shape undecided (OQ1, the load-bearing open question), along with snapping policy (OQ2), Timeslot-band integration (OQ3), and index-dependency fallback (OQ4). Locked: D1 GraphQL transport (same rationale as PRD 091 D1), D2 gap-enumeration algorithm replacing grid scan. No implementation has started (all plan phases unchecked). Relates to PRD 091 (shared schema vocabulary, TimeWindow), PRD 060 (MCP), PRD 086, PRD 077 (future availability strip/heatmap), PRD 024. Will govern a new freeSlots resolver and an SPA slot-finder UI in the event sheet's when-section.

*Keywords:* freeSlots, freie Termine, Verfügbarkeitssuche, gap enumeration, getNextAllocatableDate, Lückensuche, Wochenmuster, pattern search, Heatmap, availability strip, PRD 086, block index, TimeWindow, GraphQL, Ressourcensuche, Raumsuche, Termin finden, Quote, worktime

### 094-spa-main-view-actions-and-popups.md

PRD 094 is a draft (updated 2026-07-09) giving the SPA main view (table lens, later calendar) row/context actions and a client-side command pattern with compensating GraphQL mutations for post-save undo (a 'Rückgängig' toast plus a header undo/redo stack, cap 5, D2 revised from single-slot to flat drop-on-stale history). It contrasts with PRD 091 D5's pre-save in-sheet memento undo — this PRD owns everything past the save boundary (D1). Phases 1 and 2 (context menu shell, Bearbeiten/Anzeigen/Löschen, delete-scope dialog, header undo/redo, type-aware 'Neu') are DONE 2026-07-07; Phase 4 (calendar drag/resize move via a new moveAppointment mutation, D5) was superseded/moved into PRD 101; Phase 3 (Duplizieren, loan transitions, request confirm/deny) are unstarted candidates. D3 defines the mat-menu context-menu design with an extensible MenuItemProvider registry; D4 defines typed row-subject extraction via hidden well-known aliases (RowContext). Depends on PRD 091, 077/078, 093, 056, 067; superseded partly by PRD 101 (move mutation design) and PRD 100 (block renderer).

*Keywords:* Kontextmenü, row menu, Rückgängig, undo, redo, Command Pattern, SpaCommand, UndoToastService, Löschen, delete scope, RowContext, MenuItemProvider, moveAppointment, Duplizieren, Neu, Toast, PRD 091, PRD 101, PRD 093, Tabellenansicht

### 100-spa-block-renderer-unification.md

PRD 100 unifies the SPA's month-grid and week-grid block renderers to match Swing's shared block-rendering model (SwingRaplaBlock/RaplaBuilder/BlockColors), fixing chip text-color duplication and week-lane grouping divergence. Status: in progress, Phases 1-2 shipped (shared block-style.ts module, black chip text, week-lane parity with fixed/compact modes and 5-min collision floor via week-lanes.ts), Phase 5 (server-computed lane matching via matchedBy field) mostly landed, Phase 3 (zoom/worktime options) and Phase 4 polish (rich chip content, auto-scroll) partly open. Key decisions D1-D8 cover always-black chip text, shared block-style module, Swing-parity week lanes, rows-per-hour as zoom, immediate selection-to-creation (deliberate Swing divergence), double-click-to-edit, matchedBy provenance field with no argument, and view-columns-driven chip content (D8, linking PRD 097). Governs rapla-angular/src/app/views/ (block-style.ts, week-lanes.ts, MonthGridComponent, WeekGridComponent). Depends on/relates to PRD 077, 095, 032, 094.

*Keywords:* Wochenansicht, Monatsansicht, Kalenderblock, Chip, Farbe, Termin, Ressource, Spurzuweisung, week-lanes, block-style, matchedBy, SwingRaplaBlock, GroupAllocatablesStrategy, compact vs fixed, PRD 095, PRD 077, Sichtbarkeit, Kollisionserkennung

### 101-transpose-anchors-move-copy-paste.md

PRD 101 designs and partly implements the reservation move/copy/paste/template-instantiation mutation family for the SPA (drag-move, resize, copy/paste, template instantiation), replacing the client-only Swing transpose logic (FacadeImpl.copyReservations) with server-side GraphQL verbs. Status: in-progress, Phases 0-5 done (research, solution draft, Swing copy-SINGLE bugfix D9, server move family moveReservations/moveAppointment/splitOccurrence/copyReservations with the old Duration scalar deleted, SPA week-grid move+resize with EVENT/SERIE/SINGLE scope dialog); Phase 6+ (copy verbs, SPA copy/paste, instantiateTemplate, exchangeAllocatable) deferred. Nine decisions locked (D1-D9): transpose logic lives server-side (D1), exceptions stay absolute never re-based (D2, diverges from Swing on copy), until is asymmetric absolute-on-move/length-preserving-on-copy (D3), one server transpose implementation (D4), naming keeps copy/no cut verb/splitOccurrence (D6), exchangeAllocatable ported literally (D7), MONTHLY rank drift accepted (D8), Swing midnight bug fixed (D9). Governs rapla-core FacadeImpl/AppointmentImpl/RepeatingImpl and rapla-app ReservationMutationController; supersedes an interim sketch in PRD 056.

*Keywords:* Verschieben, Kopieren, Einfügen, Serie, Termin verschieben, Ausnahme, Wiederholung, moveReservations, splitOccurrence, exchangeAllocatable, keepTime, Anker, Drag and Drop, Resize, PRD 056, PRD 094, Zwischenablage, EVENT SERIE SINGLE

### 104-spa-template-picker.md

PRD 104 builds the SPA's unified 'Neu' (New) dialog for picking event types and event templates (rapla:template Allocatables, many hundreds in a deployment), replacing Swing's multi-level BalancedHierarchicalMenu with one flat searchable/scrollable list plus recents (D1/D6). It also carries the generic external-event reconciliation worklist ("Halde" staging store, PRD 068 sibling) design of record — worklist-not-wizard, Verknüpfen/Aus-Vorlage/Ignorieren resolution paths, channel-decoupled staging (D12), booking-rights scope, blueprint time-source rules — after a 2026-09-13 cleanup moved every deployment-specific detail (Dualis-Abgleich UI history, org-hierarchy scoping, corpus-derived conventions) out to dhbwrapla PRD 004 per AGENTS.md §17. Status: in-progress; Phases 1-4 (server path/grouping, SPA picker dialog, instantiation via reservationsFromTemplate) done 2026-07-24; Phase 5 (grouping config) and Phase 6 (multi-reservation import matching) deferred/in design. Locked decisions D1-D10 cover flat-list-plus-path (no tree), server-side grouping via TemplatePathBuilder, localStorage recents, unified type+template dialog, drag-create picker, reservationsFromTemplate reuse, deferred multi-reservation instantiation, and import matchKey-based matching. Governs rapla-angular src/app/event/ (NewEventPickerComponent) and src/app/import/ (worklist), plus rapla-app TemplatePathBuilder; deployment-specific implementation lives in dhbwrapla PRD 004.

*Keywords:* Neu-Dialog, Vorlage, Ereignisvorlage, Termin anlegen, Dualis-Sync, Import, Abgleich, Parkstreifen, Halde, Verknüpfen, TemplatePathBuilder, newEventOptions, reservationsFromTemplate, Semestervorlage, Kurs, Unitcode, PRD 068, PRD 107, externalEventWorklist

### Documents / templates / event content

### 093-loan-lifecycle.md

PRD 093 is a draft (2026-07-06) defining a minimal loan-status lifecycle (planned -> out -> returned) for lending-desk deployments (archetype C), replacing the current 'ZURÜCK' pseudo-resource-type workaround. Overdue is derived (status==out AND end<now), never stored (D1); there is no persistent 'lent-out' flag on the resource (D2) — instead a computed Allocatable.currentLoan/isOut GraphQL field, §12-scoped. An out loan blocks its resource beyond its planned end until returned (D3, load-bearing rule for availability). Status is stored as a designated classification attribute plus annotation (D4, working key 'loan-status'), and the whole feature ships as an opt-in plugin org.rapla.plugin.loanstatus with double opt-in (plugin enabled AND annotated attribute present, D5). No phases are checked yet (all plan items open); OQ3 (mutation verb shape) and OQ4 (block-index interaction) unresolved. Depends on/relates to PRD 091 (resourceAvailability must honor open loans) and PRD 092 (not required). Will govern a new loanstatus plugin, GraphQL loanStatus/currentLoan fields, and an SPA active-loans table.

*Keywords:* Ausleihe, Verleih, loan status, checkOut, return, überfällig, overdue, ZURÜCK, Rückgabe, currentLoan, isOut, Klassifikationsattribut, annotation, Plugin, loanstatus, Equipment, Verfügbarkeit, resourceAvailability, Gerät, archetype C

### 097-event-html-templates-mustache.md

PRD 097 designs server-side HTML document templates: a stored Mustache template pairs with a stored GraphQL view (PRD 074) to render permission-safe HTML (Leihschein/loan slips, calendar exports) that browsers print to PDF via window.print(); no server PDF library. Status: in progress, Phases 1-6 mostly landed (storage, render pipeline, grouped views, template-authoring editor, 2D time-grid via strips/segments/bars), Phase 6 (replacing legacy AbstractHTMLCalendarPage) blocked on OQ10, Phases 7-9 (SPA read-only rendering, publish/capability URLs, interactive components+native save) open/deferred. Key decisions: JMustache logic-less engine (no SSTI, D2), sanitize render output not template (D6/D6d), CSP sandbox opaque origin (D6a, shared with PRD 102), page shell dissolved so templates own the whole page (D7a), @param/@window parameter contract (OQ9). Governs rapla-app's org.rapla.server.spring.document package (DocumentApi, DocumentController, DocumentCatalogService, DocumentRenderService, DocumentSanitizer, DocumentShell, BuiltinDocuments/BuiltinPartials) and static/template-editor/. Depends on PRD 074, 077, 098, 102; use-case catalog in docs/usecases/htmltemplates.md.

*Keywords:* Dokumentvorlage, Mustache, HTML-Template, Leihschein, Kalenderexport, GraphQL-View, PDF-Druck, Sandbox, CSP, Wochenprogramm, Monatsansicht, DocumentTemplate, DocumentController, TemplatePathBuilder, Wochenplan, PRD 074, PRD 098, PRD 102, CalendarLayoutEngine, Vorlage vs Dokument

### 111-document-ui-wiring.md

PRD 111 designs and implements wiring stored documents (PRD 097) into the SPA UI, e.g. a loan slip on an event context menu. Status: decisions D1-D6 ruled and v1 implemented same day (2026-09-02), shipped in the Siegen deployment; later phases (event sheet, resource tree, picker editor) remain open. Locked decisions: D1 placement is a documents annotation on the DynamicType (no selectors); D2 binding is derived from the view's by-id param, not declared; D3 DynamicType exposes a resolved documents field, SPA builds menus from memory (no round-trip); D4 the documents feature owns its own RowMenuProvider insertion; D6 it ships as a plugin, enabled by default, gated both statically and at runtime. Depends on PRD 097 (document templates), PRD 074 (param directives), PRD 094 (main-view actions), PRD 099 (table selection), PRD 089 (recents). Governs DynamicTypeAnnotations, DynamicType.documents resolver, EntityRef.typeKey, Swing DocumentsAnnotationEdit, SPA row-context.ts and row-menu.ts.

*Keywords:* PRD 111, Dokument, document, Vorlage, Leihschein, DynamicType, annotation, RowMenuProvider, RowContext, typeKey, Kontextmenue, context menu, GraphQL, PRD 097, PRD 074, SPA, Swing editor, Termin, Ressource


### Storage / indices / data model

### 040-dispatch-validate-before-lock.md

PRD 040, status draft (2026-05-15), documents and proposes fixing a multi-pod correctness gap in DBOperator.dispatch: conflict and permission validation run against the per-pod LocalCache (up to ~10s stale) before the cluster WRITE_LOCK is acquired. The stale-permission case means a permission change reaches other pods only after their next refresh; the stale-conflict case is only a missed at-save-time warning since conflicts are advisory and self-heal. Proposes reordering dispatch to acquire the lock first, refresh from history under the lock, then validate. No new locking infrastructure; a reordering only, with no added deadlock risk (fail-fast locks). FileOperator (single-pod/XML) is unaffected. Surfaced during PRD 035's design review (OQ#10). References docs/architecture/locking.md for the three lock layers. Adoption is a deployment threat-model decision, not yet mandated.

*Keywords:* DBOperator, dispatch, WRITE_LOCK, Multi-Pod, LocalCache, Konflikterkennung, Berechtigung, Permission, stale cache, locking.md, PRD 035, PRD 040, Sperre, requestLocks, preprocessEventStorage, race condition, Nebenläufigkeit

### 082-storage-memory-model.md

Foundation PRD for modernizing rapla's read-side storage/query layer to fix measured GraphQL latency (901ms-class full scans over ~50k allocatables). Originally proposed a CQRS in-memory-H2 read-model, but that was built, measured, and abandoned (2026-06-24): H2 was 1.1-3.7x SLOWER than the legacy in-memory appointmentMap due to JDBC-boundary cost. Pivoted to bespoke in-memory IntervalIndex/BucketIndex structures fed at the existing updateIndizes put/remove seam, achieving an 18.5x measured speedup on the real dhbwrapla store. Documents the Bestandsaufnahme (LocalCache/AbstractCachableOperator architecture, multi-pod event-log replication), deferred footprint/parking/archiver work, and the concrete index catalog split into sibling PRDs 083 (permission index), 085 (name search), 086 (appointment block index, dual-API), and 087 (classification/type buckets); PRD 084 (HSQLDB->H2 persistence) is orthogonal. Tracks a detailed phased execution/status table through Phase P6. Governs rapla-server LocalAbstractCachableOperator, AppointmentMapClass, and the readmodel package.

*Keywords:* Speichermodell, IntervalIndex, BucketIndex, CQRS, read-model, appointmentMap, H2, LocalCache, PRD 083, PRD 084, PRD 085, PRD 086, PRD 087, Performance, canRead, Auslastung, Belegung, storage/impl/server/readmodel, readModelAuthoritative flag, PermissionIndex

### 085-search-name-indexing.md

Draft PRD (rewritten 2026-06-24, split from PRD 082) for an in-memory derived-name search index serving the omnibox's EVENT and RESOURCE name search — a planner-only, approximately-performant secondary feature, explicitly not super-optimized. Because rapla names are computed via ParsedText templates (not stored fields), the design materializes derived names once per locale at the PRD 082 updateIndizes seam (NameIndex: byId foldedName map + a token-vocabulary postings map for fast FUZZY matching, ~1ms vs ~2400ms naive). Lucene/H2 full-text is dropped to a deferred conditional fallback. Permission filtering happens post-search (never pre-filter, never truncate before permission per §12) with a generous top-K before the final top-N. Consumed by PRD 081's omnibox; depends on PRD 082's seam and PRD 028's evaluator. Governs SearchGraphQLController.searchEvents/searchResources.

*Keywords:* NameIndex, Namenssuche, ParsedText, getName, FUZZY, SUBSTRING, Omnibox, SearchGraphQLController, canModify, PRD 081, PRD 082, PRD 028, Token-Vokabular, KEY_NAME_FORMAT, postings, German folding, planner search, §12

### 086-appointment-block-index.md

Draft PRD (split from PRD 082, later pivoted from H2 to in-memory) for the largest storage-modernization piece: an in-memory IntervalIndex over materialized appointment blocks, replacing the legacy start-only-sorted appointmentMap/appointmentUserMap to fix a fixed-latency-floor pathology in window queries and conflict detection. Dual-API (serves both old RemoteStorage/Swing and GraphQL, since both funnel through the same operator methods), unlike sibling GraphQL-only PRDs 083/085/087. Documents the H2 detour (built, measured 1.1-3.7x slower, deleted), the final design (two IntervalIndex instances keyed by allocatable/owner, blocks capped at 52 materialized occurrences else a side-set rule row, maxBlockDuration high-water mark), a locked window-first global-read optimization for full-admin unscoped queries, binding-semantics findings (appointmentMap binding != getAllocatablesFor), and a two-layer test strategy (brute-force oracle + record/replay). Governs AppointmentMapClass, LocalAbstractCachableOperator conflict/query paths.

*Keywords:* IntervalIndex, appointmentMap, appointmentUserMap, Konfliktprüfung, Belegung, Termin, Serie, block_id, maxBlockDuration, PRD 082, PRD 064, PRD 079, readModelAuthoritative, window-first, getDependentRef, getAllocatablesFor, brute-force oracle, record/replay, AppointmentMapClass

### 087-classification-type-indices.md

PRD 087 is a draft (2026-06-24) split from PRD 082's Workstream A, defining GraphQL-only server-side read-path indices to accelerate classification/type filtering (typeKeyEq, typeKeyIn, where-only filters) over large allocatable/reservation populations. It proposes two index classes on the H2 read-model: Class 1 (structural type-bucket maps, near-term scope, D1-D5) and Class 2 (lazy attribute indices for bounded types, string indexing deferred, D6-D8), sequenced later in PRD 082's build order. It depends on PRD 082 (storage memory model foundation), relates to PRD 066, 059, 028, 085, 086, and governs ClassificationGraphQLController, AbstractCachableOperator.getAllocatables, and LocalCache in rapla-server/rapla-core. Open questions cover H2 version support (OQ6) and traffic justification for Class 2 (OQ2).

*Keywords:* Klassifikation, Typ-Index, GraphQL, ClassificationGraphQLController, typeKeyEq, typeKeyIn, H2, LocalCache, getAllocatables, Ressource, Reservierung, Berechtigung, canRead, PRD 082, PRD 086, PRD 083, Attribut-Index, WhereEvaluator, AttributeType, Suche

### 090-additive-permission-resolution.md

PRD 090, done except cleanup (Phases 1-4 landed 2026-06-28, Phase 4 closed 2026-09-30; Phase 5 parked while Rapla 2 shares databases, PRD 121), replaces rapla's USER>GROUP>WORLD precedence permission resolution with a purely additive max-over-all-matching-rows model, abolishing DENIED and soft-deny (user-row-below-group) semantics (D1, D2, revises ADR 0003). A one-shot migration freezes a tiny (production-audit-measured, a handful of entities) worklist of true-escalation allocatables into a system preference; an admin REST endpoint plus SPA dialog let admins acknowledge/resolve findings (D4: prune DENIED, accept soft-deny). Option A (flip-then-migrate, D3) was chosen over a gated flip. Governs RaplaDefaultPermissionImpl, PermissionController, PermissionIndex, PermissionContainer.Util, the new PermissionMigrationService/Controller and SPA PermissionMigrationDialogComponent. The SPA migration dialog is the effective-access display. Since 2026-10-01 GraphQL never exposes DENIED (output enum without it, PermissionDto skips the rows) and every permission write keeps stored DENIED rows (PermissionInputMapper). Related: ADR 0003, docs/architecture/permissions.md, PRD 063 OQ2, PRD 069.

*Keywords:* Berechtigung, Permission, additiv, soft deny, DENIED, PermissionController, RaplaDefaultPermissionImpl, PermissionIndex, Migration, worklist, ADR 0003, Zugriffsrecht, Gruppe, Benutzer, precedence, GraphQL write, SettableAccessLevel, effectiveAccess, admin, Sichtbarkeit


### External integrations / sync

### 038-graph-calendar-sync.md

PRD 038, status draft (2026-05-15), designs a write-only Microsoft Graph calendar sync backend for Exchange Online/M365, alongside the existing EWS connector, in org.rapla.plugin.exchangeconnector. Motivated by Microsoft's EWS retirement deadlines (Oct 2026 / Apr 2027). Defines a new CalendarBackend SPI extracted in rapla-core, EwsCalendarBackend and GraphCalendarBackend implementations, per-allocatable backend routing via an exchangeBackend classification attribute, and a new entity RaplaExportedEvent (with tombstone sweep) that PRD 039's loopback filter and PRD 042 Mode 2 both consume. Rapla always wins conflicts; Outlook edits are overwritten. Depends on RaplaKeyStorage credential pooling; complementary sibling of PRD 039 (read direction) and upstream of PRD 042. Governs SynchronisationManager, AppointmentSynchronizer, EWSConnector, ExchangeAppointment.

*Keywords:* Exchange, Microsoft Graph, EWS, Kalender-Sync, Termin, Ressource, Allocatable, RaplaExportedEvent, CalendarBackend, Mailbox, iCalUId, PRD 038, PRD 039, PRD 042, SynchronisationManager, Graph API, OAuth2 client-credentials, Buchung

### 039-external-ical-subscription-per-resource.md

PRD 039, status draft (2026-05-15), lets a rapla resource (especially a Person/Dozent) carry external iCal subscription URLs that rapla polls to surface read-only busy/availability constraints in conflict detection, without creating reservations or leaking event titles. Defines ExternalCalendarSubscription, ExternalAppointment, and AvailabilityWindow entities, BUSY_TIMES/AVAILABILITY_TIMES/MIXED interpretation modes, and a three-pass row-consumption loopback filter against PRD 038's RaplaExportedEvent to exclude rapla's own writes from conflict detection. Extends AllocationConflictModel (PRD 023) via a single constraint projection. Heavy privacy invariants per AGENTS.md section 12 (BusyOnlyProjection, leak tests). Produces shared IcalFeedParser infrastructure consumed by PRD 042. Distinct from the existing one-shot org.rapla.plugin.ical.ICalImport.

*Keywords:* iCal Abonnement, Sichtbarkeit, Berechtigung, Verfügbarkeit, Konflikt, Dozent, Ressource, Person, ExternalAppointment, AvailabilityWindow, VAVAILABILITY, RFC 7953, loopback filter, RaplaExportedEvent, PRD 039, IcalFeedParser, Datenschutz, Termin

### 042-ical-import-modes.md

PRD 042, status draft (2026-05-15), rewrites the currently broken org.rapla.plugin.ical.ICalImport into a two-mode importer that turns iCal VEVENTs into rapla Reservations: Mode 1 one-time import (editable, admin-owned, UID dedup) and Mode 2 read-only periodic sync (managed Reservations, source-authoritative, disappear when removed upstream). Both built on PRD 039's shared IcalFeedParser. Distinguishes itself from PRD 039 (sidecar ExternalAppointment busy-markers, not real Reservations) via a decision tree: sidecar for availability-only awareness, Reservation for events that are rapla business. New REST endpoints under /api/ical-import/* (preview/commit/sync-sources), admin-only, with a new IcalSyncSource entity and KEY_EXTERNAL_SYNC_SOURCE annotation for Mode 2. Hard dependency on PRD 039's IcalFeedParser; soft dependency on PRD 038's RaplaExportedEvent for loopback dedup.

*Keywords:* iCal Import, Reservation, Termin-Import, Synchronisation, IcalSyncSource, IcalFeedParser, Mode 1, Mode 2, KEY_EXTERNALID, KEY_EXTERNAL_SYNC_SOURCE, PRD 038, PRD 039, PRD 042, RaplaICalImport, read-only sync, Import, Buchung, Kalender

### 114-exchange-sync-per-mailbox-lock.md

PRD 114 redesigns rapla's Exchange calendar sync to replace the single global EXCHANGE write lock with per-mailbox locks, turn the task queue into re-resolved intents, and separate the 6-second poll from the hourly full sweep so one broken mailbox or a long sweep cannot block or corrupt other mailboxes. Status: in-progress, Phase 1 hotfix (v1-v11 plus hunk14) live in production since 2026-09-09, Phases 2/2b/3/4 open. Triggered by a production incident analysis finding mailbox-mapping misses, an accessError break aborting all remaining tasks for a user, and RxJava worker contention stalling the poll behind the sweep. Locked decisions: D1 per-mailbox locks (not one global lock), D2 the EXCHANGE lock stays only as the poll's watermark/cursor, D3 tasks carry intents only so gaps become deletes not silent discards. Documents a long incident/hotfix history (private-item deletion bugs, recurring-master detection, weekly-pattern export bug, duplicate items from old series occurrences) and a backport table to the legacy master/WAR branch. Depends on PRD 070 (Exchange wiring/scheduler), PRD 038 (Graph backend), docs/architecture/locking.md and exchange-sync.md; site-specific data lives in gitignored dhbwrapla docs. Governs SynchronisationManager, AppointmentSynchronizer, EWSConnector, ExchangeSchedulerTrigger.

*Keywords:* PRD 114, Exchange, Sync, Synchronisation, Termin, Kalender, Outlook, Mailbox, Postfach, Lock, Sperre, SynchronisationManager, AppointmentSynchronizer, EWSConnector, Sweep, Poll, Serie, recurring, PRD 070, PRD 038, dhbwrapla


### Deployments / packaging / clients

### 118-rapla3-demo-usecases.md

Draft 2026-09-14: a public Rapla 3 demo built from four locked use cases — U1 Hochschule/Schule (timetable), U2 Ausleihe (equipment loans), U3 Seminarhaus (course centre), U4 Einsatzplan (shift roster) — plus U5, a cross-cutting feature-page screenshot set (dynamic types, permissions, templates, GraphQL views, API keys). Each use case gets its own hand-written `data/demo-<usecase>.xml` (dummy data only, fixed demo window), a scripted Playwright screenshot list into `docs/demo/<usecase>/`, Swing key scenes next to the SPA ones, a page in `rapla/site`, and finally a demo instance from the `rapla/rapla-releases` image with nightly reset. One session per use case in its own worktree; the coordinator session owns catalogue, merge and demo instance. Open: hosting, guest account shape, U4 station screen.

*Keywords:* Demo, Beispieldaten, demo data, data.xml, Screenshots, Playwright, Webseite, rapla/site, Hochschule, Stundenplan, Ausleihe, Leihschein, Seminarhaus, Einsatzplan, Schichtplan, Features-Seite, dynamische Typen, Demo-Instanz, Docker, PRD 118

### 052-client-clean-restart.md

PRD 052 designs a clean in-JVM restart of the Swing desktop client on logout/login so no cached entities, UI state, or RxJava subscriptions leak between users (e.g. admin menus surviving into a non-admin session). Status: draft, 2026-05-21, no shipped phases recorded here. Selected approach: full ApplicationContext close+recreate (Option A) over a parent/child context split (B) or an explicit SessionScoped contract (C, rejected), driven by a BlockingQueue<NextSession> signal from RaplaClientServiceImpl to a loop in SpringRaplaClient.main(). Phase 1a removes a JVM-global AWT EventQueue exception handler in favor of per-site SwingSafe.invokeLater; Phase 1b adds RaplaEventBus disposal. Aligns with PRD 051 (switch-user-with-oauth) and PRD 029 (Swing OAuth login). Governs rapla-client Swing lifecycle: SpringRaplaClient, RaplaClientServiceImpl, RaplaMenuBar, CalendarSelectionModel, RaplaEventBus.

*Keywords:* Swing Client, Neustart, Logout, Login, Speicherleck, RxJava, RaplaEventBus, ApplicationContext, SpringRaplaClient, RaplaClientServiceImpl, SwingSafe, AWT EventQueue, Benutzerwechsel, switch user, PRD 051, clean restart, heap leak, JVM

### Testing / quality / process

### 007-build-and-test-performance.md

In-progress PRD cutting Maven build/test wall-clock time and fixing two correctness bugs: 19 silently-skipped JUnit 4 test classes (missing junit-vintage-engine) and mvn test non-idempotence (RaplaSpringBootApplicationTest corrupting shared data/data.xml). Phase 0 (shipped 2026-05-07) disabled incremental-compile over-conservatism, removed a duplicate resources binding, upgraded maven-resources-plugin, and added junit-vintage-engine (test count 39->94), cutting cold compile 47s->21s and no-op compile 33s->6.5s. Phase 1 fixed the data.xml mutation via @TempDir+@DynamicPropertySource. Phase 2 (post PRD 005 module split, pending) plans surefire forking, shared Spring test contexts, and parallel reactor builds. Also documents dev-loop wait-for-condition/tail-F monitoring patterns later codified in AGENTS.md §8. Depends on PRD 004/005 (multi-module split) for Phase 2; interacts with PRD 001 (Spring Boot) via context-cache costs.

*Keywords:* Build Performance, mvn compile, mvn test, junit-vintage-engine, PRD 007, incremental compilation, surefire forkCount, Spring context cache, RaplaSpringBootApplicationTest, data.xml idempotency, maven-resources-plugin, Testperformance, reactor build, mvn -T 1C, MailTest, Testabdeckung

### 017-test-coverage-strategy.md

PRD defining rapla's layered test pyramid (later codified in AGENTS.md §10) and driving a coverage-backfill campaign. Phases 1-4 done: FacadeTestSupport tier-2 base class (no Spring, ~150ms boot vs ~7s @SpringBootTest), @Tag(db)/@Tag(e2e) gating with db/e2e excluded from default mvn test, JaCoCo coverage reporting behind a coverage profile, and backfill tests (AppointmentBlocksExpansionTest, PermissionMatrixTest, XmlRoundTripTest, FacadeMutationTest, ConflictFinderViaFacadeTest, ClassificationAndNameformatTest, ConflictPerformanceTest) raising aggregate coverage 12%->15% instruction. Phase 5 (DB-backed tests, DbOperatorBootTest/DbOperatorRoundTripTest) in progress, pushing storage.dbsql coverage 10%->72%. Surfaced 3 real bugs: Jackson 3 final-field roundtrip bugs, LocalAbstractCachableOperator.storeAndRemoveAsync no-op stub, and the MONTHLY-repeat Nth-weekday-of-month semantic. Depends on PRD 007 (build perf) and PRD 005 (module split); complements PRD 011/016.

*Keywords:* Test Coverage, PRD 017, FacadeTestSupport, JaCoCo, Tag db e2e, testdefault.xml, AppointmentBlocksExpansionTest, PermissionMatrixTest, XmlRoundTripTest, ConflictFinderViaFacadeTest, Testpyramide, storeAndRemoveAsync, MONTHLY repeat, Serie, Konflikt, Berechtigung, DbOperatorBootTest, coverage profile

### 023-presenter-view-extraction.md

Large in-progress PRD carving pure-Java decision/computation logic out of Swing god-classes (AppointmentController, AllocatableSelection, ClassifiableFilterEdit, ReservationInfoEdit) into rapla-core, both to make the logic unit-testable (tier-1, no Swing) and to give the future Angular client (PRD 026/028) reusable business rules via REST (PRD 024) instead of reimplementing scheduling semantics. Explicitly not building a production Presenter/View MVP split for the reservation-edit dialog (dropped 2026-05-11 once Angular's UI was confirmed to differ substantially from Swing). Phases 1-3, 5, 7-12 landed: RepeatingRuleProjector/Model/Validator/Writer, AllocationConflictModel, ClassificationFilterOperators, name-search (NameSearchMatcher), action-policy carve-outs (PasswordChangePolicy, RaplaObjectActionPolicy), EventCheck carve-outs (ReservationWarning DTOs), ExceptionListMutator, and opportunistic singles (EventTimeStatus, HolidayWarningModel, WorktimeRange fixing a PRD-014 bug). Phase 4 (calendar block layout) superseded - already pure. Phase 6 re-aimed to ongoing opportunistic carve-outs (6a/6c/6e done, 6b skipped, 6d/6f open). Established the house pattern documented in docs/architecture/mvp-pattern.md and the NoSwingInRaplaCoreClientEditTest architecture gate. Cross-references PRD 020 (pattern precedent), 024 (REST consumer), 017 (coverage), 025 (headless test harness), 026/028 (Angular).

*Keywords:* Presenter, MVP pattern, PRD 023, RepeatingRuleValidator, RepeatingRuleModel, AllocationConflictModel, AppointmentController, AllocatableSelection, ClassificationFilterOperators, NoSwingInRaplaCoreClientEditTest, mvp-pattern.md, PasswordChangePolicy, RaplaObjectActionPolicy, NameSearchMatcher, Serie, Konflikt, Berechtigung, HeadlessPresenterTestSupport, AllocatableRowStatusModel

## Done

- [001-a-date-to-localdatetime.md](done/001-a-date-to-localdatetime.md)
- [001-spring-boot-migration.md](done/001-spring-boot-migration.md)
- [002-swing-spring-di.md](done/002-swing-spring-di.md)
- [003-custom-deployments-after-spring-migration.md](done/003-custom-deployments-after-spring-migration.md)
- [004-multi-module-architecture-analysis.md](done/004-multi-module-architecture-analysis.md)
- [005-baseline.md](done/005-baseline.md)
- [005-cycle-audit.md](done/005-cycle-audit.md)
- [005-multi-module-split.md](done/005-multi-module-split.md)
- [008-server-sync-client-async-facade-split.md](done/008-server-sync-client-async-facade-split.md)
- [008-server.md](done/008-server.md)
- [009-server-bulk-storage-rest-api.md](done/009-server-bulk-storage-rest-api.md)
- [010-jackson-field-based-wire-format.md](done/010-jackson-field-based-wire-format.md)
- [011-spring-boot-4-jackson-3.md](done/011-spring-boot-4-jackson-3.md)
- [012-dhbwrapla-client-migration.md](done/012-dhbwrapla-client-migration.md)
- [013-date-script-collateral-damage.md](done/013-date-script-collateral-damage.md)
- [014-appointment-long-to-java-time.md](done/014-appointment-long-to-java-time.md)
- [015-finish-date-migration-rapla-client.md](done/015-finish-date-migration-rapla-client.md)
- [016-pre-checkin-deletion-audit.md](done/016-pre-checkin-deletion-audit.md)
- [019-spring-boot-lifecycle-migration.md](done/019-spring-boot-lifecycle-migration.md)
- [022-architecture-documentation.md](done/022-architecture-documentation.md)
- [025-headless-client-test-harness.md](done/025-headless-client-test-harness.md)
- [027-mock-framework-policy.md](done/027-mock-framework-policy.md)
- [028-angular-power-search.md](done/028-angular-power-search.md)
- [029-swing-oauth-login.md](done/029-swing-oauth-login.md)
- [031-api-namespace-redesign.md](done/031-api-namespace-redesign.md)
- [031-token-refresh-and-api-keys.md](done/031-token-refresh-and-api-keys.md)
- [032-angular-ui-library-evaluation.md](done/032-angular-ui-library-evaluation.md)
- [033-playwright-mcp-browser-testing.md](done/033-playwright-mcp-browser-testing.md)
- [034-ci-baseline-workflow.md](done/034-ci-baseline-workflow.md)
- [035-graphql-foundations.md](done/035-graphql-foundations.md)
- [036-external-idp-oauth-login.md](done/036-external-idp-oauth-login.md)
- [044-playwright-agents.md](done/044-playwright-agents.md)
- [045-end-user-deployment-and-db-config.md](done/045-end-user-deployment-and-db-config.md)
- [048-eliminate-server-container-context.md](done/048-eliminate-server-container-context.md)
- [049-controller-interface-deduplication.md](done/049-controller-interface-deduplication.md)
- [050-external-auth-user-lifecycle.md](done/050-external-auth-user-lifecycle.md)
- [051-switch-user-with-oauth.md](done/051-switch-user-with-oauth.md)
- [053-replace-rapla-logger-with-slf4j.md](done/053-replace-rapla-logger-with-slf4j.md)
- [055-graphql-events-read-api.md](done/055-graphql-events-read-api.md)
- [056-graphql-events-write-api.md](done/056-graphql-events-write-api.md)
- [057-graphql-dt-mutations-v1.md](done/057-graphql-dt-mutations-v1.md)
- [058-graphql-key-spec-migration.md](done/058-graphql-key-spec-migration.md)
- [059-graphql-typed-where-predicates.md](done/059-graphql-typed-where-predicates.md)
- [063-graphql-allocatables-write-api.md](done/063-graphql-allocatables-write-api.md)
- [064-graphql-conflicts-read-api.md](done/064-graphql-conflicts-read-api.md)
- [066-graphql-reservation-allocatable-matching.md](done/066-graphql-reservation-allocatable-matching.md)
- [068-dualis-import-wizard-redesign.md](done/068-dualis-import-wizard-redesign.md)
- [069-graphql-resource-access-read-api.md](done/069-graphql-resource-access-read-api.md)
- [070-restore-exchange-connector-wiring.md](done/070-restore-exchange-connector-wiring.md)
- [071-web-security-hardening.md](done/071-web-security-hardening.md)
- [072-server-side-login-dialog.md](done/072-server-side-login-dialog.md)
- [079-graphql-grouped-aggregates.md](done/079-graphql-grouped-aggregates.md)
- [080-typed-entity-stats.md](done/080-typed-entity-stats.md)
- [088-spec-graph-formalization.md](done/088-spec-graph-formalization.md)
- [089-server-side-recents-favorites.md](done/089-server-side-recents-favorites.md)
- [095-month-grid-render-mode.md](done/095-month-grid-render-mode.md)
- [096-spa-classification-editor.md](done/096-spa-classification-editor.md)
- [098-server-artifact-store.md](done/098-server-artifact-store.md)
- [099-spa-table-selection.md](done/099-spa-table-selection.md)
- [103-i18n-language-coverage.md](done/103-i18n-language-coverage.md)
- [106-query-request-lifecycle.md](done/106-query-request-lifecycle.md)
- [107-reservation-prototype-prefill.md](done/107-reservation-prototype-prefill.md)
- [108-changes-history-timestamp-convention.md](done/108-changes-history-timestamp-convention.md)
- [112-deployment-patch.md](done/112-deployment-patch.md)
- [113-graphql-permission-model.md](done/113-graphql-permission-model.md)
- [115-exchange-sync-hotfix-2026-09.md](done/115-exchange-sync-hotfix-2026-09.md)
- [116-graphql-allocatable-to-resource-rename.md](done/116-graphql-allocatable-to-resource-rename.md)
- [120-resource-hierarchy-parents-children.md](done/120-resource-hierarchy-parents-children.md)
- [122-spa-new-resource.md](done/122-spa-new-resource.md)
- [123-spa-unified-resource-picker.md](done/123-spa-unified-resource-picker.md)
- [126-swing-sso-auto-login.md](done/126-swing-sso-auto-login.md)
- [127-picker-accordion-type-order.md](done/127-picker-accordion-type-order.md)
- [129-cached-permission-groups.md](done/129-cached-permission-groups.md)

## Won't fix

- [002-multi-tenancy.md](wont-fix/002-multi-tenancy.md)
- [021-client-resource-stubs.md](wont-fix/021-client-resource-stubs.md)
- [024-server-side-edit-services.md](wont-fix/024-server-side-edit-services.md)
- [037-native-saml-shibboleth.md](wont-fix/037-native-saml-shibboleth.md)
- [054-standalone-windows-installer.md](wont-fix/054-standalone-windows-installer.md)
- [065-graphql-declared-type-groups.md](wont-fix/065-graphql-declared-type-groups.md)
- [084-replace-hsqldb-with-h2.md](wont-fix/084-replace-hsqldb-with-h2.md)


## How to add a PRD

Pick the next free number, write the PRD, then add a header block for it in the matching group above: a `### <file>` heading, one paragraph (topic, status, locked decisions, dependencies, governed code) and a `*Keywords:*` line mixing German and English terms.
