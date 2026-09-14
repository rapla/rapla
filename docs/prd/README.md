# PRD index

79 active PRDs in this directory, 33 done under `done/`, 6 under `wont-fix/`. AGENTS.md §2 and the `prd-management` skill cover the lifecycle (move to `done/` when complete; `git mv` back to reopen).

Each active PRD below carries a generated header (status, locked decisions, dependencies, governed code) plus a keyword line in German and English so that agents and search find it from either language. When a PRD's status or decisions change, update its header here in the same edit (wrap-up checklist).

> Numbering note: `031` exists twice (`031-api-namespace-redesign.md` active, `031-token-refresh-and-api-keys.md` done). Pick the next free number, never reuse.

## Active


### Auth / OAuth / IdP

### 029-swing-oauth-login.md

Large multi-phase PRD (Phases 1-5 largely shipped through 2026-05-25) adding browser-based OAuth 2.0 PKCE login to the Swing client via SwingOAuthLoginFlow, alongside and eventually replacing the username/password LoginDialog. Covers auto-OAuth-on-launch, refresh-token bootstrapping via TokenStore (JNLP PersistenceService/dotfile/NoOp fallback chain), remember-me cookies, admin-selectable legacy login toggles (Phase 3), a Keycloak sign-in dropdown (Phase 4), and Phase 5's major credential-lifecycle cleanup slimming ConnectInfo to a token-only 4-tuple, converting LoginCredentials.password to char[], deleting the dead RemoteAuthentificationService interface, and fixing dual-slot impersonation across client-restart. Legacy HMAC TokenHandler/RemoteSessionImpl paths were deleted 2026-06-24 (JWT-only now). Depends on/supersedes PRD 031 (refresh mechanics, API namespace), PRD 072 (server-side login dialog, superseded several Phase 4/5 mechanisms), PRD 036 (external IdP), PRD 052 (client clean restart); setup doc is docs/authentication.md. Governs rapla-client SwingOAuthLoginFlow, LoginDialog, RaplaClientServiceImpl, ConnectInfo, MyCustomConnector, and rapla-server OAuthConfigController.

*Keywords:* Swing OAuth, PKCE, PRD 029, SwingOAuthLoginFlow, LoginDialog, ConnectInfo, TokenStore, RaplaClientServiceImpl, MyCustomConnector, Anmeldung, Login, remember-me, Keycloak, impersonation, refresh token, JWT, PRD 072, PRD 036, OAuthConfigController

### 036-external-idp-oauth-login.md

Large in-progress PRD adding external OIDC identity-provider login (Microsoft Entra ID, Google, later Keycloak) alongside rapla's embedded Spring Authorization Server, via a multi-issuer JwtDecoder (IssuerAwareJwtDecoder) and a single provider-agnostic ExternalUserResolver keyed on rapla username (upn/preferred_username/email lookup, then auto-provision) rather than a per-provider stable id — a 2026-05-21 pivot after DHBW pilot failures. Swing stays rapla-SAS-only (deprecated in favor of PRD 026's Angular SPA); Angular gets a configurable LoginPickerComponent driven by discovery's providers[]/picker fields. Phase 2 (shipped) adds Keycloak as a provider and documents Shibboleth-via-Keycloak SAML brokering (zero rapla code, contrasted with native SAML in PRD 037). Phase 3 (shipped 2026-06-24) generalizes external providers from three fixed config fields to a Map<String,ProviderDef> keyed by registrationId, fixing a bug where an arbitrary provider key was silently dropped. Depends on PRD 029 (Swing OAuth plumbing reused), PRD 031 (URL/refresh conventions), feeds PRD 037 (Shibboleth reverse-proxy alternative). Governs ExternalProvidersProperties, ExternalUserResolver, JwtConfig, OAuthConfigController, ProviderConfig, ProviderDef in rapla-server.

*Keywords:* external IdP, PRD 036, Microsoft Entra, Google OAuth, Keycloak, ExternalUserResolver, IssuerAwareJwtDecoder, ExternalProvidersProperties, ProviderDef, LoginPickerComponent, SSO, Anmeldung, Berechtigung, Shibboleth, auto-provision, registrationId, OIDC, picker.mode

### 043-api-keys-jwt-pat.md

PRD 043, status in-progress (server-side + docs complete, Angular UI shipped 2026-06-27, Swing deferred), designs GitHub-PAT-style API keys for rapla: server generates a fresh asymmetric keypair (RSA-2048 or Ed25519) per key, signs one JWT with it, stores only the public key/JWK in RaplaKeyStorage's multi-slot APIKEY prefs role, and discards the private key so nobody can mint another JWT for that key. Endpoints POST/GET/DELETE /api/auth/api-keys; ApiKeyJwtDecoder dispatches by JWT typ claim alongside the access/refresh decoder. Supersedes PRD 031's API-key section; the legacy TokenHandler/SignedToken HMAC path this coexisted with has since been fully deleted (update note). Angular UI (ApiKeysDialogComponent) adds scopes and grace-window rotation per PRD 076. Related to PRD 041 (RefreshSessionService pattern) and PRD 035/060 (MCP token consumers).

*Keywords:* API-Key, Personal Access Token, JWT, RaplaKeyStorage, ApiKeyController, ApiKeyJwtDecoder, Ed25519, RS256, kid, thumbprint, Bearer token, PRD 043, PRD 031, PRD 041, PRD 076, Widerruf, revoke, Schlüssel, Anmeldedaten, Rotation

### 050-external-auth-user-lifecycle.md

PRD 050 defines rapla's external-auth (Keycloak/LDAP/SAML) user lifecycle: a persisted authenticationSource marker on User blocks self/admin password, name, and email changes for externally-managed users, with a one-way admin-only disconnect. Status: in-progress, Phases 1-6 and re-opened Phases 7-8 landed 2026-05-28, extracting provisioning off the read path (AGENTS.md §16) into UserProvisioner/DefaultUserProvisioner at OAuth/JNDI login seams (ExternalUserResolver made pure). Related to PRD 036 (OAuth login), PRD 037 (SAML), PRD 049 (controller interface dedup), PRD 053 (cross-repo coordination pattern). Governs UserImpl, ExternalUserResolver, AuthenticationStore, RaplaAuthentificationService, RemoteStorage endpoints, Swing UserOption/UserEditUI, and the Angular EditAccountDialogComponent. The dispatch path enforces the same authenticationSource rule (security audit F6-1).

*Keywords:* externe Authentifizierung, Keycloak, LDAP, SAML, Passwort ändern, Benutzer, authenticationSource, ExternalUserResolver, UserProvisioner, disconnect, Provisioning, AGENTS.md §16, JNDI, DhbwUserProvisioner, IdentityClaims, User lifecycle, Namensänderung, E-Mail ändern

### 076-scoped-api-keys-self-rotation.md

Reopened PRD describing rapla's API-key security model: scoped data permissions (read, write_events, write_resources, write_all) plus a self-rotation capability (rotate_self) so a key can replace itself without holding write power. Phases 1-3 (scope enforcement, self-rotation, grace-expiry) shipped 2026-06-21; Phase 4 (no-legacy default, config token-gate) landed; Phase 5 (api-keys confined to a GraphQL-only allow-list plus a field-level access_details directive) is planned but not built; Phase 6 (interactive SPA rotation, graceMinutes, chain capping, expired-entry compaction) is in progress with decisions D11-D15 locked. Builds on PRD 043 (API key JWT mechanism) and PRD 071. Governs ApiKeyController, ApiKeyJwtDecoder, ApiKeyScopeContext, LocalAbstractCachableOperator.check(), and docs/authentication.md.

*Keywords:* API key, Scope, rotate_self, Rotation, access_details, Berechtigung, JWT, PRD 043, PRD 071, ApiKeyJwtDecoder, ApiKeyScopeContext, self-rotation, graceMinutes, least privilege, Sicherheit, Schlüssel, read-only key, write_all

### 102-browser-credential-hardening.md

PRD 102 defines how rapla contains its browser session credential (access_token/refresh_token cookies) against untrusted same-origin content, split out of PRD 072. Split-out driven by PRD 097's semi-trusted document templates landing on the main origin. Status: in progress, Phase 1 (enforced non-script CSP on /app) landed 2026-07-10; Phases 2-6 (capability-mint endpoint, refresh-gap fix, untrusted render-path hardening, script-src XSS backstop) open. Core decisions: keep the HttpOnly access_token cookie rather than migrating to memory-only (D1, reverses an earlier plan); untrusted pages get an opaque origin via CSP sandbox + connect-src none (D2, the single load-bearing control); scoped {read} capability tokens reused from PRD 076's API-key scopes for untrusted pages needing data (D3); also specifies the later interactive-document tier (D4-D9): two CSP tiers by component usage, deployment script allowlist not nonce, precompiled custom elements via a component registry, native-form save with a sealed write-capability token, untrusted-author model, opt-in author-JS escape hatch. Governs SecurityConfig, RaplaCspHeaderWriter, ApiKeyScopeContext; implemented jointly with PRD 097 Phase 9.

*Keywords:* CSP, Sandbox, Cookie, Zugriffstoken, access_token, Sicherheit, Same-Origin, Capability-Token, Berechtigung, GraphiQL, Refresh-Token, PRD 072, PRD 076, PRD 097, XSS, Formular speichern, write-capability, opaque origin


### GraphQL API

### 055-graphql-events-read-api.md

PRD 055 defines and ships the GraphQL read API for reservations/events (Reservation, Appointment, RepeatingRule, AppointmentBlock, Allocation), a child of PRD 035 foundations and sibling to PRD 056 (write API). Status: in-progress but core read resolvers shipped 2026-05-29 including a Tier-1 LightDataFetcher performance migration; the restriction-round-trip tier-3 test is deferred. Key locked decisions: Reservation named not Event; two-shape allocation exposure (Reservation.allocations[] restriction-aware for the editor vs Appointment.allocatables[] pre-resolved for everyone else, Option C); RepeatingRule.exceptions as LocalDate; eager canModify; firstDate/lastDate convenience fields; conflicts deferred. Governs ReservationGraphQLController, schema.graphqls Reservation/Appointment/Allocation types, StructuralTypeFetchers, and AGENTS.md §12 leak filtering; feeds PRD 056 (writes mirror this typed-classification surface, β²) and referenced by domain-model.md.

*Keywords:* GraphQL, Reservation, Termin, Appointment, Allocation, Restriktion, Buchung, Serie, RepeatingRule, AppointmentBlock, Sichtbarkeit, canModify, ReservationGraphQLController, PRD 035, PRD 056, classification, LightDataFetcher, Ressource

### 056-graphql-events-write-api.md

PRD 056 designs and largely ships the GraphQL write API for reservations: createReservation, updateReservation, changeReservationOwner, moveReservations, copyReservations, deleteReservations, and the generic applyChanges batch escape hatch, sibling to PRD 055 (reads). Status: in-progress; v1 controller/schema landed 2026-05-29 (ReservationMutationController, 9 tier-3 tests); revised 2026-09-07 per PRD 113 to merge Create/UpdateReservationInput into one ReservationInput. Locked decisions include named-verb API style, full-state (not patch) updates, ATOMIC-only bulk mode, mandatory client-supplied ids with checkIdIntegrity guards (ID_COLLISION), and typed per-DynamicType classification inputs (β² symmetry with reads). moveReservations/copyReservations/moveAppointment sections are superseded by PRD 101 (transpose anchors). Governs ReservationMutationController, LocalAbstractCachableOperator.checkIdIntegrity, UpdateEvent.createReferences; feeds into PRD 062 (robustness), PRD 101, PRD 096 (type-change editing), PRD 113.

*Keywords:* GraphQL Mutation, Reservation erstellen, Termin ändern, createReservation, updateReservation, applyChanges, ChangeOp, moveReservations, copyReservations, deleteReservations, ID_COLLISION, checkIdIntegrity, expectedLastChanged, Berechtigung, PRD 101, PRD 113, Klassifikation, ReservationMutationController, optimistic concurrency

### 058-graphql-key-spec-migration.md

PRD 058 is a done, shipped one-shot startup migration renaming every DynamicType/Attribute/Category key containing non-GraphQL-spec characters (umlauts, hyphens, etc.) to deterministic ASCII-safe keys, replacing the ad-hoc sanitizeTypeName band-aid in the SDL generator. Landed 2026-05-28 with universal write- and load-path validators (Tools.isSpecCompliant, checkGraphqlKeySpecCompliance, assertCacheSpecCompliant) enforcing spec compliance everywhere. Later updates: 2026-06-24 relaxed the user-groups Category subtree to a legacy key rule (Option A, isLegacyKey) since group keys never needed the strict spec; 2026-08-28 fixed a boot crash where user-groups roots still reached the SDL generator via CATEGORY VALUE_LIST attributes; 2026-08-29 added reserved-suffix-word rules (Classification/Where/Enum/Rapla) to prevent generated-type-name collisions. Governs GraphqlKeyMigration, DynamicTypeImpl.checkKey, ClassificationSdlGenerator, and dhbwrapla's dualis importer key generation. Tracks against PRD 035 (Cut C).

*Keywords:* GraphQL Key Migration, Schlüssel, Umlaute, DynamicType, Attribute, Category, Gruppe, user-groups, isSpecCompliant, toSpecKey, makeValidKey, ClassificationSdlGenerator, GraphqlKeyMigration, dualis import, Namenskollision, reserved suffix, checkGraphqlKeySpecCompliance, PRD 035

### 060-graphql-mcp-foundations.md

PRD 060 extracts from PRD 035 the cross-type GraphQL discovery/compute surface and the MCP (Model Context Protocol) transport for AI assistants: new query roots (categories, users, periods, conflicts, templates, serverTime), a global search root, three compute primitives (findFreeSlots, checkConflicts, whoIsFree), and an MCP shape of one read-only graphql_query tool plus a graphql_schema tool plus curated, confirmation-gated mutation tools like book. Status: draft, design locked 2026-05-29, with the MCP transport itself gated on verifying Spring AI MCP starter's Spring Boot 4/Jackson 3 alignment (Phase 2 spike). Many §12 leak and window-boundary open questions (OQ-A through OQ-G) remain unresolved, including conflict symmetry/N-way shape and default/max search window sizing. Depends on PRD 035, PRD 055/056 (events surface), PRD 028 (power search), PRD 043 (API keys for MCP auth).

*Keywords:* MCP, Model Context Protocol, GraphQL Discovery, findFreeSlots, checkConflicts, whoIsFree, Suche, search, Konflikt, Verfügbarkeit, graphql_query, graphql_schema, book tool, API Key, Spring AI, PRD 035, PRD 028, freie Slots

### 061-graphql-dt-mutations-v2.md

PRD 061 is a draft follow-up (opened 2026-05-29) to PRD 057's v1 DynamicType GraphQL mutations, addressing five deferred hardening items needed before admins can safely edit schemas against live production data: (1) a valueType-change migration policy matrix (COERCE/REJECT/COERCE_OR_REJECT, e.g. STRING→INT) to prevent silent data loss, (2) DefaultValueInput @oneOf variant-vs-valueType/multiplicity validation, (3) a strict annotation allow-list (name-format, colors, editView, etc.) replacing v1's open-ended key-value input, (4) hot-swap latency tightening via a direct post-mutation schema rebuild trigger (SaveDynamicTypeResult wrapper, breaking change), and (5) richer deleteDynamicTypes referrer reporting with per-kind counts and a paginated referrersOf query. Triggered by the Angular schema editor UI. Governs DynamicTypeMutationController, GraphQlSchemaRebuilder. References PRD 057 (parent), PRD 055 (hot-swap mechanism, connection pagination), PRD 056 (error taxonomy), PRD 062 (shared robustness patterns).

*Keywords:* DynamicType Mutation, Schema Editor, valueType Änderung, Migration Policy, DefaultValueInput, Annotation, Hot-Swap, GraphQlSchemaRebuilder, deleteDynamicTypes, referrersOf, REFERENCE_EXISTS, saveDynamicType, Attribut, Typänderung, PRD 057, coercion, SaveDynamicTypeResult

### 062-graphql-api-robustness.md

PRD 062 (renumbered from 058 due to a collision with the key-spec migration PRD) is a draft placeholder/parking-lot cataloguing GraphQL API robustness patterns rapla deliberately defers given its low current traffic: in-flight idempotency locks for concurrent retries, idempotency-key TTL/cache eviction, rate limiting (including a 2026-08-12 per-user read-concurrency-limit design tied to PRD 106), query/mutation complexity limits, request timeout and read-cancellation-on-disconnect (also tied to PRD 106), and distributed tracing on mutations. Explicitly not slated for implementation; each pattern lists a concrete production-evidence "defer trigger" before promotion to its own PRD. Depends on PRD 035 (foundations), sibling to PRD 056/057/061 (mutation surfaces); the shipped lean idempotency baseline (client UUIDs, ID_COLLISION) lives in PRD 056 itself, not here.

*Keywords:* API Robustness, Idempotenz, Rate Limiting, Idempotency Lock, TTL Cache, Query Complexity, Timeout, Cancellation, Distributed Tracing, WebGraphQlInterceptor, PRD 106, concurrency limit, StoredViewInterceptor, DoS, Multi-Tenant, parking lot, graphql-java

### 063-graphql-allocatables-write-api.md

PRD 063 designs and implements the GraphQL write API for Allocatables (resources/persons): createAllocatable, updateAllocatable, deleteAllocatables, plus ChangeOp batch variants, mirroring PRD 056's (events) named-verb pattern and reusing PRD 055/056's typed per-DynamicType classification inputs. Status: in-progress, opened 2026-05-29; owner-at-create override was later removed per PRD 067 D10 (audit-erasure concern — owner is always the caller, transfers go through an explicit changeOwner verb); type-change-on-update was later accepted per PRD 096 (client-side remap preview). Revision 2026-09-07 per PRD 113 merges Create/UpdateAllocatableInput into one AllocatableInput and folds this PRD's deferred OQ2 (permission editing) into PRD 113 as a nullable permissions field. Governs AllocatableMutationController, mirrors ReservationMutationController's exception/error-code machinery. Open/deferred: shared GraphQlMutationException extraction, allocatable bulk-owner-change verb.

*Keywords:* GraphQL Mutation, Ressource, Allocatable, createAllocatable, updateAllocatable, deleteAllocatables, Berechtigung, Eigentümer, owner, ChangeOp, PRD 056, PRD 067, PRD 113, AllocatableMutationController, Klassifikation, Person, Raum, typeKey

### 064-graphql-conflicts-read-api.md

PRD 064 defines the GraphQL read API for rapla's scheduling-conflict detection: a `Query.conflicts(reservationId)` field returning a `Conflict` type carrying both reservations, both appointments, and the shared allocatable. Status: in-progress, opened 2026-05-29, parent PRD 035 (done), sibling PRD 055 (Events Read API) and PRD 060 (MCP foundations, separate compute-op concern). Locks §12 no-leak invariants: anonymous/unknown/unreadable reservation returns empty list; if the OTHER reservation or the allocatable isn't caller-readable, the conflict is dropped rather than partially exposed. Defers the per-Reservation `conflicts` field, `allConflicts` admin overview, and `checkConflicts` dry-run. Governs `ConflictGraphQLController` in rapla-app's `org.rapla.server.spring.graphql` package, using `operator.getConflicts`.

*Keywords:* Konflikt, Conflict, GraphQL, conflicts query, Reservation, Appointment, Allocatable, PRD 064, PRD 055, PRD 060, canRead, Sichtbarkeit, Termin, Buchungskonflikt, ConflictGraphQLController, Berechtigung

### 066-graphql-reservation-allocatable-matching.md

PRD 066, in-progress with Phases 1+2 landed 2026-05-29, closes a two-roundtrip gap in the GraphQL calendar query pattern: it adds `idIn: [ID!]` to `AllocatableFilter` (explicit id-selection, unioned with type-bucket results) and `allocatableMatching: AllocatableFilter` to `ReservationFilter`, letting the SPA send a whole resource-tree selection (type checkboxes + per-type where rules + individually ticked resources) in one GraphQL round-trip instead of two. Locks semantic invariants: `idIn` always wins over filter narrowing but never bypasses §12 `canRead`. Depends on PRD 055 (done, Events Read API — the resolver this extends), PRD 059 (done, typed where predicates), and pairs with PRD 028 (Power Search). Phase 3 (doc migration to `dhbwrapla/docs/graphql.md`) partially done. Governs `AllocatableFilter`/`ReservationFilter` Java records and `ClassificationGraphQLController`/`ReservationGraphQLController` resolvers.

*Keywords:* Ressourcenbaum, AllocatableFilter, ReservationFilter, allocatableMatching, idIn, GraphQL, Termin, Ressource, Kalenderbaum, canRead, Berechtigung, PRD 066, PRD 055, PRD 059, typeKeyIn, whereRoom, one round-trip

### 069-graphql-resource-access-read-api.md

PRD 069 (in-progress, v1 landed 2026-06-17) adds an admin-scoped GraphQL reverse-lookup: 'which resources (Allocatables) and events (Reservations) may this user or group read/edit?' Extends `allocatables(filter:)` and `reservations(filter:)` with `accessibleByUsername`/`accessibleByUserId`/`accessibleByGroup` (exactly one XOR'd) plus `accessLevel: AccessLevel` (mirroring `Permission.AccessLevel`). Caller must be admin-scoped over the target (`canAdminUser`/`canAdminGroup`); out-of-scope or unknown targets get a uniform forbidden error (§12 no existence leak). Distinguishes user-target (owner-aware canRead/canModify, folds in ownership) from group-target (permission-list scan, no owner shortcut) resolution, and notes reservations derive access differently (owner, event-type permissions, `read-events-from-others`, admin) than resources (explicit per-entity permission list). Adds `UserFilter.inGroup` follow-up. Governs `PermissionController`, `AccessTargetFilter`, `ClassificationGraphQLController`/`ReservationGraphQLController`.

*Keywords:* Zugriffsrecht, AccessLevel, accessibleByUsername, accessibleByGroup, PermissionController, Gruppe, Berechtigung, admin-scoped, canAdminUser, canAdminGroup, PRD 069, GraphQL Filter, Ressourcenzugriff, Sichtbarkeit, Eigentümer, canRead, canModify

### 073-graphql-function-equivalents.md

PRD 073 (in-progress) is the reference equivalence map between rapla's server-side expression-language Functions (`org.rapla.entities.extensionpoints.Function`, ~34 functions across `StandardFunctions`, `AppointmentNoteFunctions`, `DurationFunctions`) and the GraphQL read API, identifying which Functions are fully covered, partial, or gaps. Introduces a `FunctionDescriptor` SPI so plugins declare functions in rapla terms (source-arg type, return type), letting a central generator emit GraphQL fields/catalog/type-checks uniformly — shipped: `Query.computeFunctions` catalog, descriptor-driven fields on `AppointmentBlock`/`Appointment` (`note`, `number`, `date`, `lastchanged`). Converges with PRD 074 on keeping rapla's own `ParsedText` engine as the composition bridge (no CEL) exposing `displayName`/`name(variant:)`/`compute()` fields, and with PRD 075 on function-naming standardization. Establishes the 'focused input over shared filter' schema-design guideline after a real contract-lie defect on nested `Appointment.allocatables(filter:)`. Governs `ClassificationSdlGenerator`, `FunctionFieldGenerator`, `ComputeFunctionsController`, `GeneratedClassificationWiring`.

*Keywords:* Function, ParsedText, EvalContext, FunctionDescriptor, computeFunctions, nameformat, displayName, AppointmentBlock, duration, note, isLocation, isPerson, PRD 073, PRD 074, PRD 075, Ausdruckssprache, GraphQL Feld, Klassifikation, ClassificationSdlGenerator, compute

### 074-graphql-declarative-views.md

PRD 074 (draft, 2026-06-20) designs declarative, admin-authored GraphQL 'saved views' replacing legacy TableView (PRD 030, frozen/deprecated) with no client-side expression engine: composition columns (nameformats, duration, times) are server-evaluated via rapla's existing `ParsedText` engine and exposed as plain GraphQL fields; selection/filtering stays in GraphQL (PRD 059/066/069); presentation (column order, headers, joins, grouping) is convention-driven with optional directives (`@view`, `@column`, `@join`, `@group`, `@bucket`). Views are stored as persisted query text (no invented binding format, per ADR 0005) with revalidate-and-mark on schema change (no auto-migration). Three canonical views ship as immutable BUILTIN code constants (`Termine_events`, `Termine_appointments`, `Termine_perDay`); admins author/fork via GraphiQL with `saveView`/`listViews`/`deleteView` mutations. CEL and client transform pipelines were evaluated and dropped. Depends on PRD 073 (data-layer feeder, op-set catalog), PRD 059, PRD 066, PRD 069, PRD 028, PRD 077 (persistence mechanics), supersedes PRD 030.

*Keywords:* saved view, GraphQL view, TableView, ParsedText, displayName, compute, saveView, listViews, GraphiQL, appointmentBlocks, Termine, Spalte, column, extensions.view, PRD 074, PRD 073, PRD 030, Vorlage, Kalenderansicht, Sortierung

### 075-expression-language-standardization.md

PRD 075 is an investigation (draft, no implementation) reopened 2026-06-20 to evaluate whether an alternative expression language (CEL, JSONata, JMESPath, JEXL, restricted SpEL, or a formalized rapla DSL) should replace rapla's own `Function`/`ParsedText` engine for any of its use cases (view compute cells, nameformat/displayName compositions, ClassificationFilter predicates, SPA search, export/template formatting, attribute validation, Dualis import field-mapping). Multi-agent findings (2026-06-20) conclude: keep rapla Functions for ALL composition/export/view surfaces (no engine beats ParsedText's prefix-call template shape); only two genuinely greenfield surfaces — attribute validation and Dualis import mapping — could justify a narrow second engine (CEL preferred, restricted SpEL as zero-dependency fallback). Also resolves the naming sub-question: adopt SQL/spreadsheet-vocabulary aliases (NOT/AND/OR/IF/CONCAT/SUBSTRING/EQ/COALESCE) additively in `FunctionFactory.createFunction`, scoped to the composition family only. Feeds into PRD 073 and PRD 074's engine-choice conclusions.

*Keywords:* Expression Language, ParsedText, Function, CEL, JSONata, JMESPath, JEXL, SpEL, FunctionFactory, Namenskonvention, alias, PRD 075, PRD 073, PRD 074, Ausdruckssprache, Validierung, Dualis Import, COALESCE, EQ

### 077-calendar-model-graphql.md

Draft/in-progress PRD replacing the legacy Swing CalendarModel/saved-calendars with a GraphQL-native model covering SavedView persistence, view switching/conversion, and week/month calendar render-modes; carved out of PRD 074 (which owns the table render layer) and reuses PRD 078's transport/renderer. Locks scope/domain and render-mode as orthogonal axes, defers input-control machinery, and resolves OQ7 (week grid render model, 2026-07-14) with a full-day fixed raster, worktime shading via a new CalendarOptions query, and a server-driven all-day/multi-day banner band coupled to PRD 097 Phase 5. Month grid shipped via PRD 095; week grid prototyped with WeekGridComponent/week-lanes.ts. Governs the Angular SPA calendar rendering and server view/window contract.

*Keywords:* CalendarModel, SavedView, Kalender, Wochenansicht, Monatsansicht, Termin, Serie, Banner, week grid, WeekGridComponent, week-lanes.ts, PRD 074, PRD 078, PRD 095, PRD 097, worktime, CalendarOptions, Ansicht wechseln, render-mode

### 079-graphql-grouped-aggregates.md

PRD for GraphQL grouped/bucketed utilization analytics (Auslastung), carved out of PRD 074's table-view work. Status: Shape A (a dedicated typed query field, not directive-based) chosen and v1 implemented 2026-06-21. Adds Query.appointmentBlockStats(filter, groupBy, aggregate, limit) grouping by date/allocatables/custom expr and aggregating duration metrics (SUM/COUNT/MEAN/MIN/MAX), §12-safe and cost-guarded (window + 5000-bucket cap). Deferred: in-expression arithmetic needing PRD 073's number-model. Complements PRD 074's global totals (already shipped) and depends on the compute-expr engine (PRD 073) and allocatables(filter) from PRD 074. Governs ReservationGraphQLController's stats resolver and StructuralTypeFetchers.computeBlockExpr.

*Keywords:* appointmentBlockStats, Auslastung, groupBy, BlockAggregate, BlockStatBucket, Aggregation, Gruppierung, PRD 074, PRD 073, PRD 080, ISO_WEEK, Raum-Auslastung, durationMinutes, expr, Stunden, Statistik, computeBlockExpr

### 081-graphql-omnibox-multisearch.md

PRD for the SPA omnibox's unified, typed, ranked, §12-scoped GraphQL search across resources/events/occurrences/groups/users. Phase 1 (RESOURCE+EVENT+USER kinds) landed 2026-06-21/24 via a new search(query,kinds,limit) resolver returning kind-bucketed SearchGroup/SearchHit types (ResourceHit/EventHit/GroupHit/UserHit); EVENT is edit-gated (canModify, stricter than read) and windowless (a true name index, not date-scoped); RESOURCE search is FUZZY-enabled, EVENT is SUBSTRING/PREFIX-only. GroupHit exposes an opaque memberFilter so deployment-specific hierarchy knowledge stays server-side. Mandatory §12 leak tests (data-leak-prevention skill) verify no existence leak. Phases 2 (groups) and 3 (occurrences/SAVED_VIEW) are planned. Consumed by PRD 078's SPA; depends on PRD 028's allocatable evaluator and PRD 077's group model.

*Keywords:* Omnibox, Suche, search resolver, SearchHit, ResourceHit, EventHit, GroupHit, UserHit, memberFilter, canModify, FUZZY, §12, PRD 028, PRD 077, PRD 078, Gruppe, Person suchen, scope chip, windowless search, existence leak

### 113-graphql-permission-model.md

PRD 113 designs exposing rapla's permission model (Permission rows: principal, level, window) as readable and writable GraphQL fields, since today only resolution results (accessibleBy*) exist and Swing is the only editor. Status: draft v1 2026-09-07, design mostly settled, 7 open questions remain. Locked decisions include the entity/level matrix (Resource, Reservation, EventTemplate, Period, DynamicType), the DynamicType two-list split (typeAccess vs instanceDefaults), promoting EventTemplate to a full API entity, merging create/update inputs (landed under WP1), three typed permission inputs with oneOf principals, writing permissions inside existing save mutations (shape D, null=untouched vs empty list=replace), and full principal expansion for canAdmin callers. Open questions cover reservations-in-v1 (tied to a real security gate gap in SecurityManager.checkModifyPermissions not covering Reservation), effectiveAccess, time windows, READ_NO_ALLOCATION, and group-membership editing. Depends on permissions.md, ADR 0003, PRD 090, PRD 069, PRD 063, PRD 083, PRD 035, PRD 061. Governs GraphQL schema Permission and PermissionPrincipal types, SecurityManager, PermissionController.

*Keywords:* PRD 113, Berechtigung, permission, GraphQL, canAdmin, canRead, PermissionController, SecurityManager, DynamicType, typeAccess, instanceDefaults, EventTemplate, Period, AccessLevel, principal, Gruppe, group, Sichtbarkeit, Zugriffsrecht, Reservation


### 116-graphql-allocatable-to-resource-rename.md

PRD 116 renames the whole `Allocatable*` family on the GraphQL wire to `Resource*` (schema, SDL generator output, controllers' wire names, DTO record components, SPA identifiers and folder `app/allocatable` → `app/resource`, GraphQL docs and skills) in one breaking sweep; Java core keeps `Allocatable` and its class names (D4, "nur in graphql und spa"). Status: implemented 2026-09-13, uncommitted, review PASS; open OQ1 (Siegen store-only views) and the Siegen deployment with rewritten patch files. Locked: D1 umbrella `Resource` with `ResourceKind { RESOURCE, PERSON }` and `Resource.type` → `Resource.kind`; D2 `Reservation` stays (PRD 055 D1 not overturned); D3 no in-app migration of stored query texts, repair via PRD 112 patch directory per ADR 0005; D4 schema + SPA only. Related: PRD 063 (renamed write verbs), 055, done/035, 112, 074, 097.

*Keywords:* Resource, Ressource, Allocatable, rename, Umbenennung, GraphQL schema, ResourceKind, kind, resources(filter:), createResource, PRD 116, PRD 063, PRD 112 patch, ADR 0005, SPA app/resource, Person, breaking change

### REST API / server architecture

### 009-server-bulk-storage-rest-api.md

In-progress PRD porting all 23 legacy RemoteStorage methods (change password/name/email, resource/event sync, dispatch, conflicts, allocatable bindings, merge, restart, identifier reservation) to a single new RemoteStorageController under /storage/*, so the Swing client's initial sync and save path stop 404ing post-Spring-Boot-migration. Decision: keep the /storage/* URL shape unchanged (mirrors client's @HttpExchange interface) rather than splitting per-concern; reuse legacy inner-class DTOs (PasswordPost, MergeRequest, etc.). Phases 0-4 implemented; Phase 5 (centralized @RestControllerAdvice exception->HTTP-status mapping, DTO renaming) pending, with a 5-item endpoint-sweep findings table (missing-arg->400 vs missing-resource->404 conflation). Key risk already partially hit: UpdateEvent Jackson roundtrip needed @JsonIgnore on ReferenceHandler.getResolver(). Hard prerequisite: PRD 001; coordinates with PRD 008 (sync operator methods) and feeds PRD 003 (dhbw needs every RemoteStorage op).

*Keywords:* RemoteStorage, RemoteStorageController, PRD 009, dispatch, getResources, refreshSync, createIdentifier, getConflicts, allocatable bindings, doMerge, changePassword, changeEmail, Buchung, Sync, Swing client sync, SwingClientStartIntegrationTest, RaplaExceptionHandler, Jackson roundtrip, UpdateEvent

### 020-server-driven-admin-panels.md

In-progress PRD (foundation plus most phases done) replacing per-plugin Swing PluginOptionPanel classes and ad-hoc dhbw HTML admin pages with a generic server-driven renderer: server publishes PanelDefinition (fields, actions, current values) via a new PreferencesAdminService wire contract (@HttpExchange /admin/panels) and PreferencesPanel server SPI (Set<PreferencesPanel> Spring beans); client renders with a fixed Swing widget toolkit (ServerDrivenSettingsDialog, FieldRenderer per FieldType). Phases 1-5 done (contract, dispatcher, generic renderer, legacy-panel bridge, plugin-gate audit); Phase 6 migrated 5/11 vanilla panels (PlanningStatus, AppointmentNote, CSVExport, AutoExport, Timeslot) to @Service PreferencesPanel beans; Phase 7 shipped four concrete dhbw panels (Terminal, Morada, LDAP role-mappings, Dualis) in the dhbwrapla repo, also fixing seven missing @Service annotations in dhbwrapla's Spring bean graph. Phase 8 cleanup partial. Supersedes part of PRD 003 §I; mirrors PRD 012's metadata-driven pattern; depends on PRD 019 for startup ordering.

*Keywords:* PreferencesAdminService, PreferencesPanel, PRD 020, FieldRenderer, PanelDefinition, ServerDrivenSettingsDialog, admin panels, Einstellungen, Berechtigung admin, TerminalPreferencesPanel, MoradaPreferencesPanel, LdapRoleMappingsPreferencesPanel, DualisPreferencesPanel, PluginOptionPanel, AdminPanelsScanConfig, isAdmin, Vorlage server-driven

### 030-server-side-view-rendering.md

In-progress PRD (Phases 1-6 landed 2026-05-12; Phase 7-9 migrate Swing table views) moving every read-side render decision — calendar layout, table-row projection, CSV export — to the server so clients become thin viewers receiving pre-projected records instead of full entity graphs. Introduces TableViewEngine + TableRow (rapla-core), /table/reservations, /table/appointments, /table/config REST endpoints, a shared BlockColors helper, and /export/csv, aiming to cut wire payload ~10x and eventually delete the rapla-server→rapla-client back-edge (PRD 005 D3). A 2026-07-07 correction notes the calendar-layout core (CalendarLayoutEngine, RenderedBlock, CalendarPage) was deleted 2026-05-27 as unused — only BlockColors survives, now consumed by PRD 095. Cross-references PRD 020 (admin panel pattern), PRD 024 (server-side edit services, calendar layout prior art), PRD 026 (Angular, primary consumer), PRD 028 (power search), PRD 009 (bulk storage REST). Governs org.rapla.plugin.tableview, TableViewController, CalendarViewController, ExportController, RaplaTableColumn, ReservationTableViewFactory in rapla-core/rapla-server/rapla-client.

*Keywords:* server-side rendering, TableViewEngine, PRD 030, CalendarLayoutEngine, RenderedBlock, BlockColors, table view, CSV export, PRD 005 D3, Tabelle, Kalenderansicht, TablePage, TableColumnConfig, back-edge, RaplaBuilder, Swing table, CellExtractor

### 031-api-namespace-redesign.md

In-progress PRD (Phases 1-5 done 2026-05-12/15) restructuring rapla's URL layout: dropping the legacy /rapla/ servlet context-path, mounting the Angular SPA at /app/, moving all REST under a literal /api/ prefix (enforced per-controller and checked by ApiPrefixArchitectureTest, AGENTS.md §15), and preserving six legacy iCal/calendar URLs under /rapla/ for external subscribers. Phase 5 splits the OpenAPI spec into auth/client/rest/exports groups via SpringDocGroupsConfig for scoped codegen. Outstanding: SecurityConfig's rememberMe() re-enable is owed to PRD 029. Depends on/supersedes PRD 001 (introduced the old context-path) and feeds PRD 026 (Angular frontend, needs clean URL layout); PRD 027 is cited for the MockMvc test-rename fallout. Governs SecurityConfig, every @RestController's class-level @RequestMapping, matching @HttpExchange interfaces in rapla-core, IndexPageController/RaplaSpaEntry, and SpringDocGroupsConfig in rapla-app.

*Keywords:* API namespace, PRD 031, /api/ prefix, context-path, SecurityConfig, ApiPrefixArchitectureTest, AGENTS.md §15, SpringDocGroupsConfig, OpenAPI groups, URL redesign, Angular /app/, iCal, rememberMe, RaplaSpaEntry, IndexPageController, PRD 029, PRD 026

### 041-openapi-runtime-removal.md

PRD 041, status in-progress (Phase 1 done 2026-05-16, Phase 2 deferred), moves OpenAPI spec generation from runtime SpringDoc reflection to build-time capture (OpenApiSpecCaptureTest), removing Jackson 2 (SwaggerJacksonConfig) from rapla-app's production classpath; springdoc becomes test-scope only, served via StaticOpenApiController at runtime. Also documents substantial adjacent OAuth-refresh consolidation work (2026-05-16) that landed in the same session: RefreshSessionService as single source of truth for refresh tokens, custom Spring Authorization Server providers, never-rotate refresh policy, deletion of /api/auth/login and AuthController in favor of /oauth2/token and /oauth2/revoke. Spun off from PRD 035 and feeds into PRD 043 (API keys). Phase 2 (per-plugin endpoint tagging + runtime filter) is deferred pending a plugin-id tagging mechanism.

*Keywords:* OpenAPI, SpringDoc, Jackson 2, Jackson 3, RefreshSessionService, /oauth2/token, /oauth2/revoke, OAuth, Refresh Token, Scalar, Swagger UI, PRD 041, PRD 043, PRD 031, PRD 035, Anmeldung, Login, API-Dokumentation, build-time spec

### 048-eliminate-server-container-context.md

PRD 048, status in-progress (Phases 1-2 done and verified in rapla 2026-05-18; Phase 3 dhbwrapla migration done source-side, pending cross-repo build verification), removes the pre-Spring ServerContainerContext bag and its LegacyServerBridgeConfig bridge, replacing its facets (DataSource map, dead shutdownService stub, dead mailSession, services/patchScript) with proper Spring beans and direct RaplaServerProperties reads. Implements a working ReloadService that performs a logical reload (operator disconnect()+connect(), clearing LocalCache and re-arming schedulers) instead of a JVM restart, fixing the previously non-functional Swing 'restart server' menu action. Requires coordinated lockstep changes in dhbwrapla (DualisViewLoader, DhbwNtlmAuthStore, RaplaPruefungen). Also folds in an adjacent cleanup replacing commons-collections4 (DualHashBidiMap/DualTreeBidiMap) with in-tree TwoWayMap/IndexedSortedMap helpers in LocalAbstractCachableOperator.

*Keywords:* ServerContainerContext, LegacyServerBridgeConfig, ReloadService, ShutdownService, reload, restart, LocalCache, CachableStorageOperator, dhbwrapla, DualisViewLoader, commons-collections4, TwoWayMap, IndexedSortedMap, PRD 048, PRD 045, Neustart, Cache leeren, Scheduler

### 049-controller-interface-deduplication.md

PRD 049, status in-progress (Phases 0-1 and 3-6 landed 2026-05-21, Phase 2 partially landed), collapses rapla's REST layer so every endpoint group is one class: a Spring @RestController implementing the corresponding @HttpExchange interface from rapla-core, eliminating separate *Impl/*RestPage/*PageGenerator delegate classes that duplicated routing metadata and added a permission-check hop. Covers Pattern A (*Impl delegates, e.g. RemoteStorageController/RemoteStorageImpl worked example), Pattern B (*RestPage for resources/events/dynamictypes), Pattern C (*PageGenerator for index/status/JNLP/calendar/iCal export), and Pattern D cleanup (deletes jakarta.ws.rs-api entirely from the reactor, RESTEasy provider classes). Surfaced a drift audit requiring PII to move out of query params into request bodies (ChangeNamePost/ChangeEmailPost DTOs) and extends ApiPrefixArchitectureTest to enforce the interface-implements pattern. Related to PRD 009, 031, 041.

*Keywords:* REST-Controller, HttpExchange, RemoteStorageController, RemoteStorageImpl, RestPage, PageGenerator, ApiPrefixArchitectureTest, jakarta.ws.rs, ChangeNamePost, PII in query params, PRD 049, PRD 009, PRD 031, PRD 041, Endpoint, Delegation, Architekturtest

### 067-server-mutation-unification.md

PRD 067 (draft, opened 2026-06-10, decisions D1-D11 locked) is a major architectural refactor splitting RaplaFacade into a stateless sync 'EntityLifecycle' in rapla-core (reached via `operator.getLifecycle(user[,templateId])`) and a thin Swing client facade holding working-user/session state. Goal: zero RaplaFacade calls in server code, enforced by moving RaplaFacade/FacadeImpl/ClientFacadeImpl to rapla-client entirely (D9), making server-side facade usage a compile error. Establishes that GraphQL is THE server API (D4), the operator is the persistence+lifecycle layer, and ownership only changes via an explicit `changeOwner` mutation (D10, never at creation). Fixes confirmed drift bugs where GraphQL mutation controllers hand-rolled create/clone logic missing `copyPermissions` and appointment re-id. Depends on/relates to PRD 056, PRD 063 (write APIs to migrate), PRD 048, PRD 019, PRD 005 (reactor split), PRD 068 (Dualis, independent). Governs StorageOperator, AbstractCachableOperator, EntityLifecycle, RaplaFacade/FacadeImpl, GraphQL mutation controllers across rapla-core/server/app/client.

*Keywords:* EntityLifecycle, RaplaFacade, FacadeImpl, StorageOperator, Berechtigung, copyPermissions, changeOwner, Eigentümer, GraphQL Mutation, workingUserId, PRD 067, Facade Split, operator.getLifecycle, UpdateEvent, dispatch, Termin erstellen, Reservierung klonen

### 083-user-change-subscription.md

Draft PRD absorbing PRD 082's Workstream B: Part A builds an access_grant permission-scoped read index (inverted, level-aware READ/ALLOCATE/ADMIN grants keyed by un-expanded principal, expanded on the caller side via a per-user-id stateless cache) to answer canRead/canAllocate without an O(all) scan; Part B builds a changesSince GraphQL query consuming Part A to give the SPA/Swing client a cheap, permission-filtered change-relevance signal (resourcesChanged/eventsChangedOnResources) built on the existing EntityHistory/UpdateResult watermark-poll mechanism, including a resync flag when history is pruned. GraphQL-only (old RemoteStorage path unaffected). Depends on PRD 082's foundation seam; related to PRD 086/087. Strict §12 no-existence-leak requirements throughout. Governs a new access_grant table/index and a changesSince resolver.

*Keywords:* access_grant, Berechtigung, canRead, canAllocate, changesSince, EntityHistory, UpdateResult, Änderungsbenachrichtigung, PermissionController, AccessLevel, PRD 082, PRD 086, PRD 087, §12, principal, Gruppe, Sichtbarkeit, resync, watermark poll

### 105-shared-reservation-checks.md

PRD 105 gives the Swing pre-save reservation warning checks (missing name, no resources, duplicate appointments, conflicts, holidays, request-only allocations, not-in-current-calendar) a server-side home so the Angular SPA — which today saves silently with no warnings — can show the same seven warnings via GraphQL. Status: in-progress; Phases 1-3 shipped 2026-08-12 (ReservationChecker SPI + StandardCheckers beans, reservationChecks GraphQL field, SPA confirm/abort dialog); Phase 4 (write-path enforcement, options UI) deferred. Ten decisions locked (D1-D10): dry-run query not warnings-on-save (D1), advisory not enforcing (D2), NOT_IN_CALENDAR only checks resource/owner intersection not classification filter (D3), order-deterministic checker beans with per-code CalendarOptionsImpl preference switches (D4), severity copied 1:1 from Swing (D7), per-occurrence checking (D9), one confirm dialog for every write path not an inline panel (D10). Governs rapla-core ReservationChecker/CheckContext, rapla-app StandardCheckers/ReservationCheckService/ReservationChecksController, and rapla-angular's reservation-checks.service.ts. Depends on PRD 023 Phase 10, PRD 091 (requestStatus dependency, shipped), PRD 094, PRD 067.

*Keywords:* Speicherprüfung, Warnung, Konflikt, Feiertag, Duplikat, Ressource fehlt, reservationChecks, ReservationChecker, EventCheck, Reservierung prüfen, Vorabprüfung, Bestätigungsdialog, PRD 091, REQUEST_PENDING, CalendarOptionsImpl, Swing-Parität

### 106-query-request-lifecycle.md

PRD 106 is an umbrella/collector PRD addressing overload from rapid SPA date-navigation (e.g. clicking next-week repeatedly fires many uncancelled GraphQL queries), collecting three mitigations: (A) client-side switchMap+throttle on the view-query trigger (built here, part of PRD 078 Phase 5), (B) server-side graphql-java execution cancellation on disconnect (deferred design, recorded in PRD 062 §5), (C) server-side per-user read-concurrency limiting (deferred design, PRD 062 §3). Status: in progress; the client slice (A) is implemented and tested, reducing ten rapid clicks to two actual HTTP requests. Decisions locked: D1 scopes all three mitigations to queries only, never mutations; D2 keeps switchMap local to each call site rather than a central cancellation registry; D3 picks a 250ms leading+trailing throttle over debounce; D4 rejects result caching/prefetching for now. Governs rapla-angular ViewHostComponent's query effect (deletes the old reqToken staleness-check pattern). One open question (OQ1) about whether Spring GraphQL's MVC handler runs async blocks promoting mitigation B.

*Keywords:* Anfrage-Lebenszyklus, Überlastschutz, throttle, switchMap, reqToken, Abbruch, GraphQL-Anfrage, Fensternavigation, Ratenbegrenzung, rate limiting, ViewHostComponent, PRD 078, PRD 062, Query Cancellation, Klick-Burst

### 108-changes-history-timestamp-convention.md

PRD 108 fixes a legacy in-place-migration bug where Rapla 3 wrote the CHANGES.CHANGED_AT history-table timestamp as a UTC wall-clock value instead of true local wall-clock time (unlike every other timestamp column and unlike Rapla 2), causing recently-migrated Rapla 2 MariaDB history rows to appear to be from the future and get replayed over newer Rapla 3 writes on every cache refresh — discovered when it broke the PRD 058 key-migration's cache-consistency assertion. Status: implemented (Option A) 2026-08-28. The root cause traced to an unintentional Date-to-LocalDateTime mechanical substitution in a 2026-05-09 refactor commit (OQ1), not a deliberate PRD 054 choice. Decision D1 locked: fix the convention at the root — bind CHANGED_AT writes/reads/cleanup through the same AbstractTableStorage.setTimestamp/getTimestamp helper every other column uses, restoring parity with Rapla 2's original behavior; a null-connection-timestamp NPE found during redeploy (Phase 2a) was also fixed in the shared helper. Governs rapla-server RaplaSQL.HistoryStorage and AbstractTableStorage. Depends on PRD 058 (which surfaced the bug) and references PRD 054.

*Keywords:* CHANGES-Tabelle, Zeitstempel, Migration, Rapla 2 zu Rapla 3, In-Place-Migration, MariaDB, CHANGED_AT, Historie, HistoryStorage, setTimestamp, UTC, Zeitzone, PRD 058, PRD 054, cache replay, GraphqlKeyMigration


### Frontend / SPA

### 026-angular-frontend.md

In-progress PRD defining the Angular SPA replacement for Swing's reservation-edit UI, served from the existing Spring Boot backend. v1 scope is reservation create/edit, allocatable selection, advisory conflict overlay, and repeating-rule editing; calendar views, plugin admin UIs and full resource/user administration are out of scope. Phase 0 prototype (login + read-only reservation list, Angular chosen, same-origin /app/ hosting) shipped 2026-05-12 with OAuth2 PKCE wired via angular-oauth2-oidc. Documents extensive pre-migration server work (PRD 024 edit services, 409 mapping for RaplaNewVersionException, draft/occurrence-expansion endpoints, OpenAPI/SpringDoc) and an options-panel REST migration table. Depends on architecture docs (reservation-edit.md, domain-model.md, dynamic-types.md, permissions.md) and GraphQL PRDs 055/059/066 for the calendar read substrate; execution of URL layout decisions moved to PRD 031. Governs rapla-angular/ tree and rapla-app SPA-serving config (SpaResourceConfig).

*Keywords:* Angular, SPA, rapla-angular, Reservation edit, PRD 026, PRD 024, PRD 031, OAuth2 PKCE, angular-oauth2-oidc, Vorlage, Termin, Serie, Konflikt, Berechtigung, Ressource, GraphQL, openapi-generator, TypeScript client, SpaResourceConfig

### 047-angular-frontend-plugin-model.md

PRD 047, status draft (2026-05-18), designs the Angular-frontend counterpart to PRD 045's server plugin model: custom deployments like dhbwrapla ship their own Angular views as runtime-loaded micro-frontends via Native Federation (not Webpack Module Federation, incompatible with Angular 21's esbuild builder), rather than forking rapla-angular. Model B (chosen) keeps dhbw code entirely out of the stock bundle; a RaplaFeature contract plus a RAPLA_FEATURE injection-token registry in the rapla-angular host assembles routes/nav dynamically, driven by a server-side /api/ui-config endpoint returning remote descriptors (RaplaUiRemote beans, collected like List<ServerExtension>). The same artifact shape also works as a PRD 045 section 4 drop-in server-plugin jar carrying both server beans and static/plugins/<id>/ frontend assets, served same-origin with no signing (unlike Swing's JNLP signing model).

*Keywords:* Angular, Frontend Plugin, Native Federation, Micro-Frontend, RaplaFeature, RAPLA_FEATURE, ui-config, RaplaUiRemote, dhbwrapla, remoteEntry.json, PRD 047, PRD 045, PRD 003, Plugin-Modell, SPA, rapla-angular, feature-api

### 078-spa-graphql-view-renderer.md

In-progress PRD for the Angular SPA's GraphQL view renderer: a generic ViewHostComponent that renders any server-declared view (from PRD 074's extensions.view contract) without per-view client code, using plain HttpClient/cookie auth (no Apollo, no tokens). Covers the transport (graphql.service.ts), type-driven variable binding (buildVariablesByType), server-resolved view.window date-nav seeding, the scope gate (resource/user/group chips, no query fires without scope), and execution routing (server-merged executeView vs authoring query). Phase 1-2 done, Phase 3 (grouping/component registry/pagination) partial, Phase 4 (authoring UI) dropped in favor of GraphiQL, Phase 5 (query lifecycle throttling) done via PRD 106. Downstream of PRD 074, upstream dependency of PRD 077/079/081. Governs rapla-angular graphql.service.ts, ViewHostComponent, variable-binder.ts.

*Keywords:* ViewHostComponent, extensions.view, graphql.service.ts, Scope-Gate, buildVariablesByType, Omnibox, Sichtbarkeit, view.window, PRD 074, PRD 077, PRD 079, PRD 081, PRD 106, GraphiQL, SPA, Ansicht, Ressourcenauswahl, resource chip, cookie auth, monaco-graphql

### 089-server-side-recents-favorites.md

PRD 089, in progress (Phases 1, 3, 4 landed 2026-06-27; Phase 2 deliberately deferred), moves the SPA's 'recents' and 'favorites' resource/user lists from cross-account-leaking localStorage into per-user server Preferences storage, adds REST endpoints (/api/recents, /api/favorites), and boosts search ranking for favorited/recent hits in SearchGraphQLController. Locked decisions D1-D6 cover storing opaque {id,kind,ts} JSON (not entity references, so deletion never blocks), live-resolve-and-skip on read (D3, reusing the calendar-model 'selected' pattern), in-bucket re-rank without a new bucket (D4), two separate Preferences keys (D5), and write-time compaction instead of a delete-path sweep (D6). Depends on PRD 081 (omnibox), 078, 074/077, 067, 049, 072. Governs UserListsService, RecentsController/FavoritesController, RefreshSessionService-style JSON prefs storage, and the Angular RecentsFavoritesService/ScopedStorage.

*Keywords:* Favoriten, Zuletzt verwendet, recents, favorites, Preferences, UserListsService, localStorage, Suche, Ranking, SearchGraphQLController, §12, Sichtbarkeit, RefreshSessionService, per-user storage, Omnibox, PRD 081, Löschung, auto-cleanup, ScopedStorage

### 091-spa-reservation-edit-and-availability.md

PRD 091 is a large, actively evolving draft (updated through 2026-07-08) bringing reservation editing to the Angular SPA plus a new GraphQL resource-availability API, with the equipment-lending archetype as the first target. Phase 1 (resourceAvailability/potentialConflicts GraphQL queries) and most of Phase 2 (event-sheet skeleton, mutations, undo/redo D5, recurrence editor Phase 4.0-4.5) are done; Phase 3 (finder) and parts of Phase 4 (convert-to-single) remain open. Key locked decisions: D1 GraphQL-only availability API, D2 free-time search split to PRD 092, D3 client-generated ids (id-first drafts), D4 unified Conflict type, D5 in-sheet memento-based undo (pre-save only, contrasted with PRD 094's command-pattern post-save undo), D6 permanently deferred block-level/occurrence-granular availability detail. Depends on/relates to PRD 024, 026, 056/057/063, 060, 067, 077/078, 086, and spun off PRD 092 (free-slot search), 093 (loan lifecycle), 094 (main-view actions/undo), 096 (classification editor, closes §2.4 deferral). Governs AvailabilityGraphQLController, ReservationMutationController, event-draft.ts, event-sheet.component.ts, draft-history.ts, repeating-edit.ts in rapla-angular and rapla-app.

*Keywords:* Reservierung, Termin bearbeiten, Verfügbarkeit, resourceAvailability, potentialConflicts, Ressourcenfindung, Serie, Wiederholung, Konflikt, Buchung, Ausleihe, Belegung, event sheet, Undo, AvailabilityGraphQLController, GraphQL Mutation, PRD 092, PRD 093, PRD 094, PRD 096, gilt für, Restriction Map

### 092-free-slot-search.md

PRD 092 is a draft (2026-07-05), split out from PRD 091, designing a GraphQL freeSlots query that answers 'when are these resources free' via gap enumeration over busy intervals (using the PRD 086 appointment block index) rather than the legacy brute-force per-slot getNextAllocatableDate RPC scan. It defines two candidate modes (concrete slots vs. weekly-pattern search with quota ranking) but leaves the mode shape undecided (OQ1, the load-bearing open question), along with snapping policy (OQ2), Timeslot-band integration (OQ3), and index-dependency fallback (OQ4). Locked: D1 GraphQL transport (same rationale as PRD 091 D1), D2 gap-enumeration algorithm replacing grid scan. No implementation has started (all plan phases unchecked). Relates to PRD 091 (shared schema vocabulary, TimeWindow), PRD 060 (MCP), PRD 086, PRD 077 (future availability strip/heatmap), PRD 024. Will govern a new freeSlots resolver and an SPA slot-finder UI in the event sheet's when-section.

*Keywords:* freeSlots, freie Termine, Verfügbarkeitssuche, gap enumeration, getNextAllocatableDate, Lückensuche, Wochenmuster, pattern search, Heatmap, availability strip, PRD 086, block index, TimeWindow, GraphQL, Ressourcensuche, Raumsuche, Termin finden, Quote, worktime

### 094-spa-main-view-actions-and-popups.md

PRD 094 is a draft (updated 2026-07-09) giving the SPA main view (table lens, later calendar) row/context actions and a client-side command pattern with compensating GraphQL mutations for post-save undo (a 'Rückgängig' toast plus a header undo/redo stack, cap 5, D2 revised from single-slot to flat drop-on-stale history). It contrasts with PRD 091 D5's pre-save in-sheet memento undo — this PRD owns everything past the save boundary (D1). Phases 1 and 2 (context menu shell, Bearbeiten/Anzeigen/Löschen, delete-scope dialog, header undo/redo, type-aware 'Neu') are DONE 2026-07-07; Phase 4 (calendar drag/resize move via a new moveAppointment mutation, D5) was superseded/moved into PRD 101; Phase 3 (Duplizieren, loan transitions, request confirm/deny) are unstarted candidates. D3 defines the mat-menu context-menu design with an extensible MenuItemProvider registry; D4 defines typed row-subject extraction via hidden well-known aliases (RowContext). Depends on PRD 091, 077/078, 093, 056, 067; superseded partly by PRD 101 (move mutation design) and PRD 100 (block renderer).

*Keywords:* Kontextmenü, row menu, Rückgängig, undo, redo, Command Pattern, SpaCommand, UndoToastService, Löschen, delete scope, RowContext, MenuItemProvider, moveAppointment, Duplizieren, Neu, Toast, PRD 091, PRD 101, PRD 093, Tabellenansicht

### 095-month-grid-render-mode.md

PRD 095, in progress (Phases 1+2 shipped, Phase 3a drag-create shipped, Phase 3b drag-move done 2026-07-08), adds a real month calendar grid (6x7, Monday-first) as a client render mode of the existing builtin 'Termine' view, with a new §12-gated AppointmentBlock.color field (delegating to the existing BlockColors helper, D2/D3: null the color rather than drop the block on unreadable contributors) as the only server addition. It uses spanning bars (EventCalendar-inspired, MIT-attributed) rather than per-day chips (OQ1 resolved). Locked decisions D1-D6 cover no-new-view (reusing rapla_appointments/ReservationFilter), ephemeral per-render-mode window derivation (D4), and an in-house implementation referencing Swing DraggingHandler/HTMLMonthViewPage/BlockColors plus EventCalendar for pointer mechanics only (D5, per PRD 032). Drag-move (D6) was superseded by PRD 101's transpose/anchor move verbs. Depends on PRD 077, 078, 074, 094, 032, 100, 101. Governs MonthGridComponent, ViewCatalogService.BUILTIN_VIEWS, AppointmentBlock GraphQL resolver in rapla-app/rapla-angular.

*Keywords:* Monatsansicht, month grid, Kalenderraster, AppointmentBlock, color, BlockColors, §12, Termine view, spanning bars, drag-create, drag-move, moveReservations, ViewHostComponent, PRD 077, PRD 101, PRD 094, EventCalendar, Chip, Fensterberechnung

### 096-spa-classification-editor.md

PRD 096 is a draft with Phases 1-4 landed (2026-07-07/08) building one reusable Angular component, <app-classification-edit>, that renders and edits DynamicType-driven classification attributes for both Reservations and Allocatables, replacing PRD 091 §2.4's type+name-only slice. It introduces ClassificationSchemaService (D1: parses SDL from /api/graphql/schema per the PRD 055 β schema-as-data decision, no new descriptor query), a controlled/stateless component (D2, precondition for PRD 091 D5 memento undo), the v1 widget-mapping table from PRD 035 §5 (D3), an @editView(title|additional|no-view) directive deriving prominent 'title' fields from the nameformat (D5, revised from an earlier @title directive), and non-clearable enum selects (D4). Server prerequisite (Phase 0, in-place type change for updateReservation/updateAllocatable) landed. Consumers: the SPA event sheet (Phase 3) and a new allocatable editor dialog (Phase 4). Includes ride-along bugfixes for server-locale name resolution (ServerLocaleResolver) and a false CONCURRENT_MODIFICATION from timestamp-fraction stripping. Depends on PRD 091, 035 §5, 055, 056, 063.

*Keywords:* Klassifikation, classification editor, Attribut, DynamicType, ClassificationSchemaService, app-classification-edit, SDL, @editView, Titel, nameformat, Allocatable Editor, AllocatableEditDialogComponent, ServerLocaleResolver, Widget-Mapping, PRD 091, PRD 055, PRD 035, type change, remapValues, expectedLastChanged

### 100-spa-block-renderer-unification.md

PRD 100 unifies the SPA's month-grid and week-grid block renderers to match Swing's shared block-rendering model (SwingRaplaBlock/RaplaBuilder/BlockColors), fixing chip text-color duplication and week-lane grouping divergence. Status: in progress, Phases 1-2 shipped (shared block-style.ts module, black chip text, week-lane parity with fixed/compact modes and 5-min collision floor via week-lanes.ts), Phase 5 (server-computed lane matching via matchedBy field) mostly landed, Phase 3 (zoom/worktime options) and Phase 4 polish (rich chip content, auto-scroll) partly open. Key decisions D1-D8 cover always-black chip text, shared block-style module, Swing-parity week lanes, rows-per-hour as zoom, immediate selection-to-creation (deliberate Swing divergence), double-click-to-edit, matchedBy provenance field with no argument, and view-columns-driven chip content (D8, linking PRD 097). Governs rapla-angular/src/app/views/ (block-style.ts, week-lanes.ts, MonthGridComponent, WeekGridComponent). Depends on/relates to PRD 077, 095, 032, 094.

*Keywords:* Wochenansicht, Monatsansicht, Kalenderblock, Chip, Farbe, Termin, Ressource, Spurzuweisung, week-lanes, block-style, matchedBy, SwingRaplaBlock, GroupAllocatablesStrategy, compact vs fixed, PRD 095, PRD 077, Sichtbarkeit, Kollisionserkennung

### 101-transpose-anchors-move-copy-paste.md

PRD 101 designs and partly implements the reservation move/copy/paste/template-instantiation mutation family for the SPA (drag-move, resize, copy/paste, template instantiation), replacing the client-only Swing transpose logic (FacadeImpl.copyReservations) with server-side GraphQL verbs. Status: in-progress, Phases 0-5 done (research, solution draft, Swing copy-SINGLE bugfix D9, server move family moveReservations/moveAppointment/splitOccurrence/copyReservations with the old Duration scalar deleted, SPA week-grid move+resize with EVENT/SERIE/SINGLE scope dialog); Phase 6+ (copy verbs, SPA copy/paste, instantiateTemplate, exchangeAllocatable) deferred. Nine decisions locked (D1-D9): transpose logic lives server-side (D1), exceptions stay absolute never re-based (D2, diverges from Swing on copy), until is asymmetric absolute-on-move/length-preserving-on-copy (D3), one server transpose implementation (D4), naming keeps copy/no cut verb/splitOccurrence (D6), exchangeAllocatable ported literally (D7), MONTHLY rank drift accepted (D8), Swing midnight bug fixed (D9). Governs rapla-core FacadeImpl/AppointmentImpl/RepeatingImpl and rapla-app ReservationMutationController; supersedes an interim sketch in PRD 056.

*Keywords:* Verschieben, Kopieren, Einfügen, Serie, Termin verschieben, Ausnahme, Wiederholung, moveReservations, splitOccurrence, exchangeAllocatable, keepTime, Anker, Drag and Drop, Resize, PRD 056, PRD 094, Zwischenablage, EVENT SERIE SINGLE

### 103-i18n-language-coverage.md

PRD 103 measures and decides which of rapla's nine UI languages (de, cs, es, fi, fr, nl, pl, pt plus English base) to bring to full translation coverage, triggered by an i18n pass on the combined login page. Status: in-progress; Phase 1 (filling gaps via LLM-assisted translation) done 2026-07-21 for all seven non-German/English languages, raising coverage to 99.5-100%; native-speaker review of the machine translations is still outstanding, as is deciding a keep-or-drop policy for the stray one-key NotificationResources_da.properties (Danish, not a real supported language). No decisions are yet formally locked (document still says none). Includes a regeneration script (Python, key-diff against the English base) for verifying coverage percentages. Governs rapla-core/rapla-client/plugin *.properties resource bundles (RaplaResources, plugin bundles like ExchangeConnectorResources, NotificationResources, etc.); does not cover the Angular SPA's separate i18n mechanism.

*Keywords:* Sprache, Übersetzung, i18n, Lokalisierung, Properties-Datei, RaplaResources, Sprachabdeckung, Deutsch, Login-Seite, Plugin-Bundle, LLM-Übersetzung, coverage, Bundle, PRD 072, Fallback

### 104-spa-template-picker.md

PRD 104 builds the SPA's unified 'Neu' (New) dialog for picking event types and event templates (rapla:template Allocatables, many hundreds in a deployment), replacing Swing's multi-level BalancedHierarchicalMenu with one flat searchable/scrollable list plus recents (D1/D6). It also carries the generic external-event reconciliation worklist ("Halde" staging store, PRD 068 sibling) design of record — worklist-not-wizard, Verknüpfen/Aus-Vorlage/Ignorieren resolution paths, channel-decoupled staging (D12), booking-rights scope, blueprint time-source rules — after a 2026-09-13 cleanup moved every deployment-specific detail (Dualis-Abgleich UI history, org-hierarchy scoping, corpus-derived conventions) out to dhbwrapla PRD 004 per AGENTS.md §17. Status: in-progress; Phases 1-4 (server path/grouping, SPA picker dialog, instantiation via reservationsFromTemplate) done 2026-07-24; Phase 5 (grouping config) and Phase 6 (multi-reservation import matching) deferred/in design. Locked decisions D1-D10 cover flat-list-plus-path (no tree), server-side grouping via TemplatePathBuilder, localStorage recents, unified type+template dialog, drag-create picker, reservationsFromTemplate reuse, deferred multi-reservation instantiation, and import matchKey-based matching. Governs rapla-angular src/app/event/ (NewEventPickerComponent) and src/app/import/ (worklist), plus rapla-app TemplatePathBuilder; deployment-specific implementation lives in dhbwrapla PRD 004.

*Keywords:* Neu-Dialog, Vorlage, Ereignisvorlage, Termin anlegen, Dualis-Sync, Import, Abgleich, Parkstreifen, Halde, Verknüpfen, TemplatePathBuilder, newEventOptions, reservationsFromTemplate, Semestervorlage, Kurs, Unitcode, PRD 068, PRD 107, externalEventWorklist

### 107-reservation-prototype-prefill.md

PRD 107 introduces a side-effect-free reservationPrototype(typeKey) GraphQL query returning the server-computed default classification for a new event type, so the SPA can prefill new-event drafts with the same defaults Swing/the server silently applies at create time (DynamicTypeImpl.newClassification()). Status: implemented, Phases 1-4 done 2026-07-08, Phase 5 (newEventOptions query, first slice of the later template-picker work) done 2026-07-22; template instantiation itself deferred to PRD 104. Ride-along fixes: added Swing-missing copyPermissions on GraphQL reservation create (Phase 1), and null-vs-omitted input semantics so explicit null clears a defaulted attribute while omission keeps the default (Phase 2). Nine decisions locked (D1-D6 plus template design constraints D6): prototype is a query not a persisted draft or mutation (D1), ids stay client-minted (D2), no @defaultValue SDL directive (D3), permissions are a create-seed not a live type binding (D4), type change stays save-time-only (D5), template instantiation design constraints recorded but not built (D6, later realized in PRD 104). Governs rapla-app ReservationGraphQLController/ClassificationInputMapper and rapla-angular ClassificationSchemaService. Depends on/feeds PRD 096, 056, 090, 104.

*Keywords:* Standardwerte, Vorschau, reservationPrototype, newClassification, Klassifikation, Attribut-Vorbelegung, Berechtigungen kopieren, copyPermissions, newEventOptions, Neuer Termin, Typwechsel, null-Semantik, PRD 104, PRD 096, PRD 056, VALUE_LIST enum


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

### 088-spec-graph-formalization.md

PRD 088 is a draft (2026-06-24, condensed with D6/D7 added 2026-06-27) proposing a lightweight, git-versioned spec graph and CI-enforced linkability layer on top of rapla's existing PRD/architecture-doc corpus, plus an AI-maintained-by-conversational-diff approach for architecture/admin/developer docs. It explicitly rejects adopting any SDD framework (OpenSpec, Spec Kit, Kiro) as a tool (D1), targeting spec-as-review-gate plus executable conformance rather than code generation (D2). Locked decisions D1-D7 cover YAML front-matter, dangling-ref CI lint, capability-spec extraction starting with REST/GraphQL/permissions, and dogfooding via ADR 0004. Depends on/references PRD 049, 035, 022, 067, 082-087, and ADRs 0001/0002/0004. Plan has four phases (linkability, doc dogfood, spec graph materialization, capability-spec pilot), none yet executed beyond design.

*Keywords:* Spec-Driven Development, PRD-Graph, spec graph, OpenSpec, ADR, CI Lint, dangling reference, capability spec, AI-maintained docs, conversational diff, docs/prd, architecture docs, PRD 049, PRD 035, MADR, Mermaid, REST-Endpoint-Katalog, Dokumentation, Wissensgraph

### 090-additive-permission-resolution.md

PRD 090, in-progress (Phases 1-4 landed 2026-06-28), replaces rapla's USER>GROUP>WORLD precedence permission resolution with a purely additive max-over-all-matching-rows model, abolishing DENIED and soft-deny (user-row-below-group) semantics (D1, D2, revises ADR 0003). A one-shot migration freezes a tiny (production-audit-measured, a handful of entities) worklist of true-escalation allocatables into a system preference; an admin REST endpoint plus SPA dialog let admins acknowledge/resolve findings (D4: prune DENIED, accept soft-deny). Option A (flip-then-migrate, D3) was chosen over a gated flip. Governs RaplaDefaultPermissionImpl, PermissionController, PermissionIndex, PermissionContainer.Util, the new PermissionMigrationService/Controller and SPA PermissionMigrationDialogComponent. Editor effective-access transparency is deferred to a future SPA permission editor; Phase 5 (deleting precedence code) is future cleanup. Related: ADR 0003, docs/architecture/permissions.md, PRD 063 OQ2, PRD 069.

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

### 068-dualis-import-wizard-redesign.md

PRD 068 (in-progress, sync flow implemented and live-verified 2026-06-12) redesigns dhbw's Dualis external-event import so the rapla Swing client stays generic/vanilla (no dhbw-specific code), with the server contributing only classification mapping of the raw Dualis data. Client flow: loadEvents fetches raw ImportItems with opaque `sourceData`; user selects rows; createReservations returns un-persisted `List<ReservationImpl>` built from a template, mapped server-side to classification; client resolves and edits them before save. A separate `syncClassification` contract (new, dedicated) binds an existing reservation to a Dualis event via classification-only merge, applied through an undoable `changeClassificationUndoable` command — deliberately not applying allocatable ids client-side. Relates to PRD 003/012 (original no-dhbw-client-code mandate), PRD 067 (independent — no EntityLifecycle needed here), PRD 104 (multi-reservation template matching). Governs `ExternalEventImportService`, `ImportItem`, dhbwrapla's `mapSyncClassification`/`createReservations`.

*Keywords:* Dualis, Import, Veranstaltung, Pruefung, Klassifikation, Vorlage, Template, syncClassification, ImportItem, ExternalEventImportService, PRD 068, sourceData, createReservations, Synchronisieren, dhbwrapla, ImportStatus, Campusnet

### 070-restore-exchange-connector-wiring.md

PRD 070 (in-progress) restores the Exchange (EWS) connector's server wiring, broken by an incomplete Spring Boot migration: the Swing 'Exchange Connector' dialog returned 404 and the scheduled sync never ran because `SynchronisationManager` was never registered as a Spring bean and its controller was dropped. Fixes: wires `SynchronisationManager` and its full dependency graph as an always-present `@Bean` on every deployment (so the GUI works everywhere), restores `ExchangeConnectorController implements ExchangeConnectorRemote`, and splits scheduling onto a new `ExchangeSchedulerTrigger` bean gated by `@ConditionalOnProperty(rapla.exchange.enabled)` so the `@Scheduled` sync only runs on the dedicated sync deployment — distinct from the runtime `ENABLED_BY_ADMIN` kill switch. References PRD 019 (lifecycle migration), PRD 049 (controller dedup), PRD 048 (server container elimination), PRD 038 (Graph backend, additive, not a replacement), PRD 069 (blocking unrelated GraphQL schema WIP). Governs `ServerServiceConfig`, `SynchronisationManager`, `ExchangeSchedulerTrigger`, `ExchangeConnectorController`.

*Keywords:* Exchange Connector, EWS, SynchronisationManager, rapla.exchange.enabled, ExchangeSchedulerTrigger, ExchangeConnectorController, Multi-Pod, Scheduler, PRD 070, Spring Boot Migration, 404 Whitelabel, ENABLED_BY_ADMIN, Mailbox, dhbw sync, @Scheduled, @ConditionalOnProperty

### 114-exchange-sync-per-mailbox-lock.md

PRD 114 redesigns rapla's Exchange calendar sync to replace the single global EXCHANGE write lock with per-mailbox locks, turn the task queue into re-resolved intents, and separate the 6-second poll from the hourly full sweep so one broken mailbox or a long sweep cannot block or corrupt other mailboxes. Status: in-progress, Phase 1 hotfix (v1-v11 plus hunk14) live in production since 2026-09-09, Phases 2/2b/3/4 open. Triggered by a production incident analysis finding mailbox-mapping misses, an accessError break aborting all remaining tasks for a user, and RxJava worker contention stalling the poll behind the sweep. Locked decisions: D1 per-mailbox locks (not one global lock), D2 the EXCHANGE lock stays only as the poll's watermark/cursor, D3 tasks carry intents only so gaps become deletes not silent discards. Documents a long incident/hotfix history (private-item deletion bugs, recurring-master detection, weekly-pattern export bug, duplicate items from old series occurrences) and a backport table to the legacy master/WAR branch. Depends on PRD 070 (Exchange wiring/scheduler), PRD 038 (Graph backend), docs/architecture/locking.md and exchange-sync.md; site-specific data lives in gitignored dhbwrapla docs. Governs SynchronisationManager, AppointmentSynchronizer, EWSConnector, ExchangeSchedulerTrigger.

*Keywords:* PRD 114, Exchange, Sync, Synchronisation, Termin, Kalender, Outlook, Mailbox, Postfach, Lock, Sperre, SynchronisationManager, AppointmentSynchronizer, EWSConnector, Sweep, Poll, Serie, recurring, PRD 070, PRD 038, dhbwrapla


### Deployments / packaging / clients

### 118-rapla3-demo-usecases.md

Draft 2026-09-14: a public Rapla 3 demo built from four locked use cases — U1 Hochschule/Schule (timetable), U2 Ausleihe (equipment loans), U3 Seminarhaus (course centre), U4 Einsatzplan (shift roster) — plus U5, a cross-cutting feature-page screenshot set (dynamic types, permissions, templates, GraphQL views, API keys). Each use case gets its own hand-written `data/demo-<usecase>.xml` (dummy data only, fixed demo window), a scripted Playwright screenshot list into `docs/demo/<usecase>/`, Swing key scenes next to the SPA ones, a page in `rapla/site`, and finally a demo instance from the `rapla/rapla-releases` image with nightly reset. One session per use case in its own worktree; the coordinator session owns catalogue, merge and demo instance. Open: hosting, guest account shape, U4 station screen.

*Keywords:* Demo, Beispieldaten, demo data, data.xml, Screenshots, Playwright, Webseite, rapla/site, Hochschule, Stundenplan, Ausleihe, Leihschein, Seminarhaus, Einsatzplan, Schichtplan, Features-Seite, dynamische Typen, Demo-Instanz, Docker, PRD 118

### 012-dhbwrapla-client-migration.md

In-progress PRD migrating dhbwrapla's 14 Swing-client classes out of the fork into vanilla rapla or server-rendered pages. Two small admin-config panels (DhbwAuthPluginOptionPanel, TerminalOption) become super-admin-gated server-rendered HTML pages in the dhbwrapla server jar (superseded later by PRD 020's generic admin-panel mechanism). The 12-class dualisimport wizard is generalized and moved to rapla-core/rapla-client as org.rapla.plugin.externaleventimport, with a metadata-driven wire contract (getMetadata/uploadCsv/createReservations, ImportItem/HierarchyLevel/ResultColumn DTOs) so vanilla rapla carries zero DHBW-specific terminology; activation gated by rapla.externalevents.enabled. Rapla-side carve-out fully landed 2026-05-10 (12-file move, ExternalEventImportResources, ExternalEventImportServiceContractTest); dhbwrapla server-side adapter (DualisEventsLoaderImpl -> ExternalEventImportService) still pending under PRD 003 D2/E. Strict rule: vanilla rapla never depends on dhbwrapla. Depends on PRD 001, 003, 009.

*Keywords:* dhbwrapla, externaleventimport, dualisimport, PRD 012, ExternalEventImportService, ExternalEventImportWizard, DualisEventsLoaderImpl, TerminalOption, DhbwAuthPluginOptionPanel, Vorlage, Termin-Import, Dualis, Morada, wire contract metadata, ConditionalOnProperty, super-admin gate, Studiengang, Fakultät

### 045-end-user-deployment-and-db-config.md

PRD 045, status in-progress (Phases 1-3 landed 2026-05-18, Phase 4 planned, plugin contract Phases 5-6 merged from former PRD 046), defines how an operator installs, configures, and extends the Spring Boot rapla fat JAR: externalized application.yml config, a production-sane bundled config (dev DEBUG logging and localhost URLs moved to application-local.yml), database configuration (wiring RaplaServerProperties db-datasources map to real DataSource beans via DatasourceConfiguredCondition, canonical key 'rapladb', HSQLDB bundled/PostgreSQL+MariaDB external in ./lib/), and a drop-in server plugin model using Spring Boot's AutoConfiguration.imports aggregation plus PropertiesLauncher with loader.path=lib/,plugins/ (no operator flags needed). Distinguishes itself from PRD 003's legacy full-custom-deployable model (dhbwrapla migration is a follow-up). Frontend plugin counterpart is PRD 047.

*Keywords:* Deployment, application.yml, Datenbank, DataSource, rapladb, HSQLDB, MariaDB, PostgreSQL, PropertiesLauncher, loader.properties, Plugin, drop-in, fat JAR, PRD 045, PRD 003, PRD 047, Konfiguration, Installation, systemd, WinSW

### 052-client-clean-restart.md

PRD 052 designs a clean in-JVM restart of the Swing desktop client on logout/login so no cached entities, UI state, or RxJava subscriptions leak between users (e.g. admin menus surviving into a non-admin session). Status: draft, 2026-05-21, no shipped phases recorded here. Selected approach: full ApplicationContext close+recreate (Option A) over a parent/child context split (B) or an explicit SessionScoped contract (C, rejected), driven by a BlockingQueue<NextSession> signal from RaplaClientServiceImpl to a loop in SpringRaplaClient.main(). Phase 1a removes a JVM-global AWT EventQueue exception handler in favor of per-site SwingSafe.invokeLater; Phase 1b adds RaplaEventBus disposal. Aligns with PRD 051 (switch-user-with-oauth) and PRD 029 (Swing OAuth login). Governs rapla-client Swing lifecycle: SpringRaplaClient, RaplaClientServiceImpl, RaplaMenuBar, CalendarSelectionModel, RaplaEventBus.

*Keywords:* Swing Client, Neustart, Logout, Login, Speicherleck, RxJava, RaplaEventBus, ApplicationContext, SpringRaplaClient, RaplaClientServiceImpl, SwingSafe, AWT EventQueue, Benutzerwechsel, switch user, PRD 051, clean restart, heap leak, JVM

### 054-standalone-windows-installer.md

PRD 054 specifies a standalone, single-user, no-auth Windows 11 MSI trial installer for rapla (a Tauri Rust shell hosting a jlinked, stripped Spring Boot fat JAR, rendering the existing Angular SPA via WebView2), explicitly for evaluation only (not dhbwrapla or multi-user). Status: in-progress; Phase 1 (the -Pstandalone Maven profile, application-standalone.yml, StandaloneBootTest) landed 2026-05-25; Phases 2-5 (jlink, Tauri build, signing, MSI, e2e verification) are scaffolded but run/verified on the maintainer's Windows machine. Storage reuses the same XML FileOperator format as a file-backed server so a trial user can graduate without migration. Governs rapla-app's standalone profile, RaplaAuthentificationService.passwordCheckDisabled, and the new sibling tree rapla-standalone/ (outside the Maven reactor, like rapla-angular). Open question: whether to bundle a starter rapla-data.xml.

*Keywords:* Standalone Installer, Windows MSI, Tauri, jlink, Trial, Testinstallation, passwordCheckDisabled, rapla-standalone, Spring Boot fat JAR, WebView2, Signierung, signtool, YubiKey, Auto-Update, Einzelbenutzer, FileOperator, rapla-data.xml

### 112-deployment-patch.md

PRD 112 covers seeding deployment-specific stored views, documents and type annotations from files next to the JAR instead of manual template-editor pasting, so customer-specific content stays outside the JAR. Option A (declarative front-matter file patches in data/patch/, using YAML headers inside Mustache/GraphQL comments, timestamp-based update rule A2) was implemented on user instruction 2026-09-02: FrontMatter, ArtifactPatchLoader, RaplaServerProperties.patchDir, system-scoped save overloads. Option B (a patch JAR with a DataPatch interface, applied-list bookkeeping, Flyway-style once-only semantics) is a designed but not-yet-built later extension, compatible with and building on Option A's save path. Depends on PRD 098 (artifact store, builtins stay in JAR), PRD 097 (documents), PRD 111 (documents annotation), PRD 090 (migration marker protocol), docs/architecture/locking.md. Governs org.rapla.server.spring.patch package, ArtifactCatalogService, ViewCatalogService, DocumentCatalogService.

*Keywords:* PRD 112, Patch, Seed, deployment, Vorlage, ArtifactPatchLoader, FrontMatter, data/patch, DataPatch, ArtifactCatalogService, ViewCatalogService, DocumentCatalogService, Auslieferung, PRD 097, PRD 098, PRD 111, multi-pod, writeLockIfLoaded, Leihschein


### Testing / quality / process

### 007-build-and-test-performance.md

In-progress PRD cutting Maven build/test wall-clock time and fixing two correctness bugs: 19 silently-skipped JUnit 4 test classes (missing junit-vintage-engine) and mvn test non-idempotence (RaplaSpringBootApplicationTest corrupting shared data/data.xml). Phase 0 (shipped 2026-05-07) disabled incremental-compile over-conservatism, removed a duplicate resources binding, upgraded maven-resources-plugin, and added junit-vintage-engine (test count 39->94), cutting cold compile 47s->21s and no-op compile 33s->6.5s. Phase 1 fixed the data.xml mutation via @TempDir+@DynamicPropertySource. Phase 2 (post PRD 005 module split, pending) plans surefire forking, shared Spring test contexts, and parallel reactor builds. Also documents dev-loop wait-for-condition/tail-F monitoring patterns later codified in AGENTS.md §8. Depends on PRD 004/005 (multi-module split) for Phase 2; interacts with PRD 001 (Spring Boot) via context-cache costs.

*Keywords:* Build Performance, mvn compile, mvn test, junit-vintage-engine, PRD 007, incremental compilation, surefire forkCount, Spring context cache, RaplaSpringBootApplicationTest, data.xml idempotency, maven-resources-plugin, Testperformance, reactor build, mvn -T 1C, MailTest, Testabdeckung

### 017-test-coverage-strategy.md

PRD defining rapla's layered test pyramid (later codified in AGENTS.md §10) and driving a coverage-backfill campaign. Phases 1-4 done: FacadeTestSupport tier-2 base class (no Spring, ~150ms boot vs ~7s @SpringBootTest), @Tag(db)/@Tag(e2e) gating with db/e2e excluded from default mvn test, JaCoCo coverage reporting behind a coverage profile, and backfill tests (AppointmentBlocksExpansionTest, PermissionMatrixTest, XmlRoundTripTest, FacadeMutationTest, ConflictFinderViaFacadeTest, ClassificationAndNameformatTest, ConflictPerformanceTest) raising aggregate coverage 12%->15% instruction. Phase 5 (DB-backed tests, DbOperatorBootTest/DbOperatorRoundTripTest) in progress, pushing storage.dbsql coverage 10%->72%. Surfaced 3 real bugs: Jackson 3 final-field roundtrip bugs, LocalAbstractCachableOperator.storeAndRemoveAsync no-op stub, and the MONTHLY-repeat Nth-weekday-of-month semantic. Depends on PRD 007 (build perf) and PRD 005 (module split); complements PRD 011/016.

*Keywords:* Test Coverage, PRD 017, FacadeTestSupport, JaCoCo, Tag db e2e, testdefault.xml, AppointmentBlocksExpansionTest, PermissionMatrixTest, XmlRoundTripTest, ConflictFinderViaFacadeTest, Testpyramide, storeAndRemoveAsync, MONTHLY repeat, Serie, Konflikt, Berechtigung, DbOperatorBootTest, coverage profile

### 022-architecture-documentation.md

In-progress short PRD establishing a new docs/architecture/ reference directory (README, overview, domain-model, dynamic-types, conflicts-and-events, reservation-edit, permissions, extension-points, flows) so new contributors and AI agents can build a correct mental model without code archaeology. Explicitly a reference not a tutorial, not a user manual, and not ADRs (decisions stay in PRDs). Uses docs/conflict-detection.md conventions (path:line refs, tables, no images); plans to land all eight docs in one commit and move this PRD to docs/prd/done/ once shipped, with per-doc updates thereafter not needing new PRDs. Maintenance rule: PRDs landing structural changes should update affected docs/architecture/*.md as part of their closing checklist. Open questions (non-blocking): Mermaid diagrams and auto-generated plugin/extension-point catalogs, deferred. Referenced by PRD 023 (its 'house pattern' diagram belongs in docs/architecture/mvp-pattern.md per this PRD).

*Keywords:* architecture documentation, PRD 022, docs/architecture, domain-model, dynamic-types, permissions.md, extension-points.md, flows.md, conflict-detection.md, reference documentation, mvp-pattern.md, Dokumentation, Architektur, AGENTS.md, PRD-driven development

### 023-presenter-view-extraction.md

Large in-progress PRD carving pure-Java decision/computation logic out of Swing god-classes (AppointmentController, AllocatableSelection, ClassifiableFilterEdit, ReservationInfoEdit) into rapla-core, both to make the logic unit-testable (tier-1, no Swing) and to give the future Angular client (PRD 026/028) reusable business rules via REST (PRD 024) instead of reimplementing scheduling semantics. Explicitly not building a production Presenter/View MVP split for the reservation-edit dialog (dropped 2026-05-11 once Angular's UI was confirmed to differ substantially from Swing). Phases 1-3, 5, 7-12 landed: RepeatingRuleProjector/Model/Validator/Writer, AllocationConflictModel, ClassificationFilterOperators, name-search (NameSearchMatcher), action-policy carve-outs (PasswordChangePolicy, RaplaObjectActionPolicy), EventCheck carve-outs (ReservationWarning DTOs), ExceptionListMutator, and opportunistic singles (EventTimeStatus, HolidayWarningModel, WorktimeRange fixing a PRD-014 bug). Phase 4 (calendar block layout) superseded - already pure. Phase 6 re-aimed to ongoing opportunistic carve-outs (6a/6c/6e done, 6b skipped, 6d/6f open). Established the house pattern documented in docs/architecture/mvp-pattern.md and the NoSwingInRaplaCoreClientEditTest architecture gate. Cross-references PRD 020 (pattern precedent), 024 (REST consumer), 017 (coverage), 025 (headless test harness), 026/028 (Angular).

*Keywords:* Presenter, MVP pattern, PRD 023, RepeatingRuleValidator, RepeatingRuleModel, AllocationConflictModel, AppointmentController, AllocatableSelection, ClassificationFilterOperators, NoSwingInRaplaCoreClientEditTest, mvp-pattern.md, PasswordChangePolicy, RaplaObjectActionPolicy, NameSearchMatcher, Serie, Konflikt, Berechtigung, HeadlessPresenterTestSupport, AllocatableRowStatusModel

### 025-headless-client-test-harness.md

Draft PRD proposing a HeadlessPresenterTestSupport test harness (peer of FacadeTestSupport) so presenter and pure-model classes carved out by PRD 023 can be unit-tested without booting Swing, the EDT, or a Spring context. Scope: a tier-2 base class reusing FacadeTestSupport's fixture wiring plus a MutableClock and i18n stub, and a reflective RecordingView<P> test double replacing hand-rolled boolean-flag view mocks, with fluent assertCalled/assertNeverCalled assertions. Explicitly excludes GUI test frameworks, async testing utilities, and mocking libraries. Depends on and is consumed by PRD 023 (presenter/view extraction, primary consumer), references PRD 020's FieldRendererTest precedent, PRD 017 (test coverage strategy pyramid), and PRD 022 (architecture docs for the pattern). Governs rapla-server/src/test/java/org/rapla/test/util/ test infrastructure for rapla-client presenter classes.

*Keywords:* HeadlessPresenterTestSupport, FacadeTestSupport, RecordingView, PRD 025, PRD 023, Presenter, MVP pattern, Swing, tier-2 test, MutableClock, test harness, presenter test, RaplaFacade, Testabdeckung, Mock, AGENTS.md §10

### 027-mock-framework-policy.md

Adopted decision PRD (2026-05-11) establishing rapla's mock-framework policy for tests: default is no mocking of internal rapla types (RaplaFacade, LocalCache, PermissionController, ConflictFinder, entities) — use real FacadeTestSupport at tier 2 and real Spring context + MockMvc at tier 3, since rapla's harnesses make 'real' cheap and mocks have historically hidden shipped bugs (Jackson 3 final-field bugs, silent no-op stubs, MONTHLY semantics, permission leaks). Narrow allowed exceptions: Servlet-API types (HttpServletRequest/Response) and external integrations behind one rapla-owned interface (MailInterface, EWS client). Grandfathers RaplaJNLPPageGeneratorTest as the canonical Mockito example. This policy became AGENTS.md §13 and constrains test-writing in PRDs 024 (server-side edit services) and 025 (headless client test harness); cites PRD 007 (build/test performance, context-cache) and PRD 017 (test coverage strategy).

*Keywords:* Mockito, Mock, mock-framework policy, PRD 027, AGENTS.md §13, FacadeTestSupport, MockMvc, RaplaFacade, PermissionController, LocalCache, ConflictFinder, Testrichtlinie, no mocks, SpringBootTest, MockBean, RecordingView

### 034-ci-baseline-workflow.md

Draft PRD (2026-05-13) proposing a minimal GitHub Actions CI workflow (.github/workflows/ci.yml) running on every PR to master and every push to spring-boot/master, since spring-boot (the working trunk, 64+ untested commits) currently has no CI coverage beyond Dependabot. Phase 1 scope: a Java job (JDK 21 Temurin, mvn -B verify covering test-pyramid tiers 1-2 excluding @Tag(db)/@Tag(e2e)) and an Angular job (npm ci && npm run build). Phases 2-4 (deferred): tier-3 MockMvc web-slice tests, tier-4 full e2e gated by a PR label, and Playwright browser e2e. Cross-references PRD 017 (test coverage strategy/pyramid this workflow exercises), PRD 007 (build/test performance), PRD 033 (Playwright MCP browser testing, deferred Phase 4), and AGENTS.md §5 build discipline. Governs the .github/workflows/ci.yml file only — no application code.

*Keywords:* CI, GitHub Actions, PRD 034, ci.yml, mvn verify, test pyramid, PRD 017, tier 1, tier 2, Angular build, npm ci, Dependabot, spring-boot branch, e2e, Playwright, MockMvc, surefire



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
- [010-jackson-field-based-wire-format.md](done/010-jackson-field-based-wire-format.md)
- [011-spring-boot-4-jackson-3.md](done/011-spring-boot-4-jackson-3.md)
- [013-date-script-collateral-damage.md](done/013-date-script-collateral-damage.md)
- [014-appointment-long-to-java-time.md](done/014-appointment-long-to-java-time.md)
- [015-finish-date-migration-rapla-client.md](done/015-finish-date-migration-rapla-client.md)
- [016-pre-checkin-deletion-audit.md](done/016-pre-checkin-deletion-audit.md)
- [019-spring-boot-lifecycle-migration.md](done/019-spring-boot-lifecycle-migration.md)
- [028-angular-power-search.md](done/028-angular-power-search.md)
- [031-token-refresh-and-api-keys.md](done/031-token-refresh-and-api-keys.md)
- [032-angular-ui-library-evaluation.md](done/032-angular-ui-library-evaluation.md)
- [033-playwright-mcp-browser-testing.md](done/033-playwright-mcp-browser-testing.md)
- [035-graphql-foundations.md](done/035-graphql-foundations.md)
- [044-playwright-agents.md](done/044-playwright-agents.md)
- [051-switch-user-with-oauth.md](done/051-switch-user-with-oauth.md)
- [053-replace-rapla-logger-with-slf4j.md](done/053-replace-rapla-logger-with-slf4j.md)
- [057-graphql-dt-mutations-v1.md](done/057-graphql-dt-mutations-v1.md)
- [059-graphql-typed-where-predicates.md](done/059-graphql-typed-where-predicates.md)
- [071-web-security-hardening.md](done/071-web-security-hardening.md)
- [072-server-side-login-dialog.md](done/072-server-side-login-dialog.md)
- [080-typed-entity-stats.md](done/080-typed-entity-stats.md)
- [098-server-artifact-store.md](done/098-server-artifact-store.md)
- [099-spa-table-selection.md](done/099-spa-table-selection.md)
- [115-exchange-sync-hotfix-2026-09.md](done/115-exchange-sync-hotfix-2026-09.md)


## Won't fix

- [002-multi-tenancy.md](wont-fix/002-multi-tenancy.md)
- [021-client-resource-stubs.md](wont-fix/021-client-resource-stubs.md)
- [024-server-side-edit-services.md](wont-fix/024-server-side-edit-services.md)
- [037-native-saml-shibboleth.md](wont-fix/037-native-saml-shibboleth.md)
- [065-graphql-declared-type-groups.md](wont-fix/065-graphql-declared-type-groups.md)
- [084-replace-hsqldb-with-h2.md](wont-fix/084-replace-hsqldb-with-h2.md)


## How to add a PRD

Pick the next free number, write the PRD, then add a header block for it in the matching group above: a `### <file>` heading, one paragraph (topic, status, locked decisions, dependencies, governed code) and a `*Keywords:*` line mixing German and English terms.
