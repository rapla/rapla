# PRD 012: Migrate dhbwrapla client-side plugin code to server pages / general rapla

**Status:** draft
**Date:** 2026-05-08

## Goal

The dhbwrapla fork (`~/git/dhbwrapla`, separate repo, unrelated git history from `~/git/rapla`) carries 14 Swing-client classes across three plugins. Two of them (`DhbwAuthPluginOptionPanel`, `TerminalOption`) are admin-only configuration panels that don't belong on the client at all — they should be server-rendered HTML pages, gated to super-admin. The third (`dualisimport`, 12 classes) is a general-purpose external-event import wizard that's been hard-wired to one external system (Dualis); it should move into the main rapla project, be renamed to a system-agnostic `externaleventimport`, and only show up in the client when a corresponding server endpoint exists.

After this PRD: the dhbwrapla repo's `src/main/java/org/rapla/plugin/dhbw/*/client/**` tree shrinks to zero — every client artifact in dhbwrapla either moves upstream (dualisimport) or becomes a server-side HTML page (auth, terminal). The DHBW deployment then needs only a generic rapla client + a dhbw server-side jar.

## Scope

### In scope

| Class | Current location (dhbwrapla) | Target |
|---|---|---|
| `DhbwAuthPluginOptionPanel` | `org.rapla.plugin.dhbw.auth.client.swing` | New server page in `rapla-server` (or a dhbw-server jar) — HTML form, super-admin gated |
| `TerminalOption` | `org.rapla.plugin.dhbw.dhbwterminal.client.swing` | Same: server-rendered HTML form, super-admin gated |
| 12 `dualisimport/client/**` classes | `org.rapla.plugin.dhbw.dualisimport.client[.swing]` | Move to general rapla, rename package to `org.rapla.plugin.externaleventimport.client[.swing]` |
| Wire-contract types reachable from the client (`DualisEventsLoader` REST interface, `DualisEventsResult` DTO, the generic plugin id constant) | top of `dualisimport` package | Move to `rapla-core` under `org.rapla.plugin.externaleventimport.*` (renamed). They have to live in vanilla rapla so any deployment's server impl can compile against them. |
| Dualis-specific code (CSV parser `CSVImport`, CSV column-index constants, **all of `dualisimport/server/**`**) | top of `dualisimport` package + `dualisimport/server` | Stays in dhbwrapla. The server impl now `implements ExternalEventImportService` (the renamed interface that lives in vanilla rapla). |

### Out of scope

- The **server-side** dhbw plugin code (`*/server/**` packages: auth/`DhbwLdap*`, dhbwterminal/`SteleKursUebersichtPageGenerator*`, etc.). Those continue to live in dhbwrapla; only the *client* artifacts move or get rewritten as server pages.
- `pruefungen`, `dualisexport`, `merge`, `resources`, `rights`, `semesterplan`, `exchange`: zero client classes, no migration needed.
- The dhbwrapla repo's own build / packaging shape (it's still a separate fork).
- Localization — both panels currently have hardcoded German labels (`"Ueberschrift der Uebersichtsseite"`, `"Benutzung: …"`). Keep that text verbatim during the migration; i18n is a separate cleanup.

## Why server pages, not Swing panels

1. Both `DhbwAuthPluginOptionPanel` and `TerminalOption` mutate **system preferences** (`DhbwAuthPreferences.CONFIG`, `TerminalConstants.CONFIG`) — server-side state, not user state. Editing them from the desktop client requires the full Swing wiring chain just to write three text fields.
2. The values they edit are read by **server-side code** (LDAP authenticate flow, terminal HTML page generators). Round-tripping through the client adds nothing.
3. Audit / access control wants a single super-admin gate, which is naturally enforced server-side. Doing it client-side requires trusting a desktop user not to bypass.
4. Pattern is already established in main rapla: `RaplaIndexPageGenerator`, `RaplaStatusPageGenerator`, `Export2iCalServlet`, the new `HTMLViewPage` impls (`HTMLDayViewPage` etc.) — all do plain `PrintWriter` HTML on the server. Two more pages fit the same shape.

## Decision dimensions

### A. Where do the new server pages live?

Two options:
- **A1**: in `rapla-server/src/main/java/org/rapla/plugin/dhbw/{auth,dhbwterminal}/server/web/`. Same module, picks up Spring autoconfig. **But** these are dhbw-specific — putting them in main rapla pollutes a generic codebase with a tenant-specific concern.
- **A2 (recommended)**: in **dhbwrapla**, as part of its server jar. The main rapla server has a standard servlet/Spring entry point and a plugin-scan path; the dhbw server jar contributes a controller + the option page bean. dhbwrapla already has server-only code (`DhbwLdapAuthenticate`, etc.); two more controllers fit there.

A2 is cleaner — the dhbw server jar gets self-contained admin pages, no main-rapla pollution. Pick A2.

### B. URL shape

Existing pattern for admin-style pages: `/rapla/<page-name>`. Pick:
- `GET  /rapla/dhbw/auth/options` — render form
- `POST /rapla/dhbw/auth/options` — submit form, redirect back with a flash message
- `GET  /rapla/dhbw/terminal/options` — render
- `POST /rapla/dhbw/terminal/options` — submit

Behind a single `/rapla/dhbw/**` Spring filter that enforces super-admin (see D).

### C. Form rendering

Plain `PrintWriter` HTML, matching `RaplaIndexPageGenerator`. Don't introduce Thymeleaf for two pages — the existing main-rapla pages don't use it either; introducing a templating engine for a two-page need is over-budget. The Swing panels render at most ~8 form fields each; an inlined HTML string with a small helper for `<input>` rows is fine. CSRF token via Spring Security's default mechanism.

### D. Super-admin gating

Two layers:
1. **Spring Security URL rule**: `http.authorizeHttpRequests(auth -> auth.requestMatchers("/rapla/dhbw/**").authenticated())` — anyone with a valid JWT can hit the URL.
2. **Method-level check inside the controller**: `User user = remoteSession.checkAndGetUser(request); if (!user.isAdmin()) throw new RaplaSecurityException("super admin required");` — the existing `RaplaExceptionHandler` (PRD 009 Phase 5) maps `RaplaSecurityException` → 401 already.

`User.isAdmin()` is the existing super-admin marker (rapla has a single admin tier; there's no separate "super-admin" role). If a finer-grained role is needed later, that's a separate PRD — don't invent it here.

### E. Conditional activation for the moved `externaleventimport`

Today, `DualisImportWizard` registers itself as a `ReservationWizardExtension` unconditionally — the wizard appears in the menu whether Dualis is available or not. After the move:

- The wizard interface in main rapla becomes generic: `ExternalEventImportService` (REST), with operations `listAvailableImports()` / `importEvents(criteria)`.
- The client-side `ExternalEventImportWizard` is `@ConditionalOnBean(ExternalEventImportService.class)` (or equivalent — Spring auto-wires the REST proxy as a bean iff the server publishes the endpoint). When no server-side impl exists, the proxy resolves to nothing and the wizard's `@Service` self-disables (or fails at lazy-resolve, with global lazy-init from PRD 002 making the bean only construct on first dereference, which never happens if no menu invokes it).
- Concretely: the test for "is the endpoint present?" is "does the auth REST round-trip return a 404 vs a real DTO when called". The proxy can probe on first menu render; cached for the session.
- dhbwrapla's `DualisEventsLoaderImpl` continues to live in the dhbw server jar but now **implements `ExternalEventImportService`** (renamed from `Dualis`).

## Plan

### Phase 1 — `DhbwAuthOptionsController` (1 day, dhbwrapla)

1. Create `org.rapla.plugin.dhbw.auth.server.web.DhbwAuthOptionsController` (`@Controller`).
2. `@GetMapping("/dhbw/auth/options")`:
    - `User user = remoteSession.checkAndGetUser(request); if (!user.isAdmin()) throw new RaplaSecurityException(...)`.
    - `RaplaConfiguration cfg = facade.getSystemPreferences().getEntry(DhbwAuthPreferences.CONFIG, ...)`.
    - Render an HTML form with `ldapServer` text input + `roleMapping` textarea.
3. `@PostMapping("/dhbw/auth/options")`:
    - Same admin check.
    - Read form params, build a fresh `DhbwAuthPreferences`, write back via `Preferences edit = facade.edit(facade.getSystemPreferences()); edit.putEntry(DhbwAuthPreferences.CONFIG, ...); facade.store(edit)`.
    - Redirect 303 → `GET /dhbw/auth/options?saved=1` so refresh doesn't re-submit.
4. **Delete** `DhbwAuthPluginOptionPanel.java` from dhbwrapla once the server page is verified.

### Phase 2 — `DhbwTerminalOptionsController` (1–2 days, dhbwrapla)

Same shape as Phase 1, but with the larger field set from `TerminalOption`:

| Field | HTML control | Source |
|---|---|---|
| `ueberschrift` | `<input>` | `TerminalConstants.KURS_UEBERSCHRIFT` default |
| `keinekurse` | `<input>` | `TerminalConstants.NO_COURSES` default |
| `cssurl` | `<input>` | empty default |
| `eventTypes` | `<select multiple>` listing `DynamicType[]` of reservation types | `EVENT_TYPES_KEY` |
| `resourceTypes` | same, resource+person types | `RESOURCE_TYPES_KEY` |
| `externalPersonTypes` | same, person types only | `EXTERNAL_PERSON_TYPES_KEY` |
| `kursTyp` | `<select>` single | `KURS_KEY` |
| `raumTyp` | `<select>` single | `ROOM_KEY` |
| `steleUser` | `<select>` single, lists `User[]` | `USER_KEY` |
| Export-URL display | read-only `<input>`, computed via `urlEncryption.encrypt(...)` server-side | derived from `env.getDownloadURL()` + plugin id |

Persisted under `TerminalConstants.CONFIG`. Same admin check + redirect-after-post.

**Delete** `TerminalOption.java` from dhbwrapla.

### Phase 3 — Link both pages from the index (0.5 day)

Add two `HtmlMainMenu` `@Bean` factories in the dhbw server jar's Spring config (sibling pattern to `RaplaJnlpEntry` / `RaplaStatusEntry` in main rapla's `ServerServiceConfig`). They emit two anchor links into the `RaplaIndexPageGenerator` output, **conditional on `user.isAdmin()`** — non-admins don't see the link at all.

### Phase 4 — Move `dualisimport` to main rapla, rename to `externaleventimport` (3–4 days)

#### 4.0 Direction of dependencies (the rule the rest of Phase 4 follows)

Strict rule, per design intent:

- **Vanilla rapla never depends on dhbwrapla.** It has the generic wizard + the wire contract.
- **dhbwrapla depends on vanilla rapla.** Its server jar provides the `ExternalEventImportService` impl, registered as a Spring bean.
- **Any other deployment** can also provide its own `ExternalEventImportService` impl with their own backend (CSV upload, REST polling, etc.) without touching vanilla rapla.

This forces the wire contract (interface + result DTO + generic plugin-id constant) to live in vanilla rapla. The server impl + the Dualis CSV parser + any other dhbw-specific implementation detail stays in dhbwrapla.

#### 4.1 What moves (vanilla rapla)

In `rapla-core`, create `org.rapla.plugin.externaleventimport` (top of package):
- `ExternalEventImportService` — REST interface, renamed from `DualisEventsLoader`. Rewritten as Spring `@HttpExchange` (drops the `jakarta.ws.rs` annotations to match PRD 010's interface style). Single method `ExternalEventImportResult loadEvents(ImportCriteria criteria)`.
- `ExternalEventImportResult` — renamed from `DualisEventsResult`. Nested `Pruefung`/`Veranstaltung`/`Course` types renamed to `ExternalExam`/`ExternalLecture`/`ExternalCourse` (they're public API). The two German `DynamicType`-key constants (`VERANSTALTUNG_TYPE_KEY = "Lehrveranstaltung"`, `PRUEFUNG_TYPE_KEY = "Pruefung"`) keep their **string values** verbatim — they're rapla type keys, not API names; renaming the strings would force every dhbwrapla deployment to re-key its existing data.
- `ExternalEventImportPlugin` — constants interface holding the generic plugin id `org.rapla.plugin.externaleventimport` + `ENABLE_BY_DEFAULT = false`. Replaces the generic half of `DualisImportPlugin`.
- `ImportCriteria` — request DTO. Currently nameless (the dhbw `loadEvents` may take individual params); design as a small POJO so the interface signature is stable.

In `rapla-client`, create `org.rapla.plugin.externaleventimport.client[.swing]` and move all 12 client files (renamed):

| dhbwrapla path | new rapla-client path |
|---|---|
| `dualisimport/client/DhbwReservationImportController.java` | `externaleventimport/client/ExternalEventImportController.java` |
| `dualisimport/client/DualisReservationCreator.java` | `externaleventimport/client/ExternalEventReservationCreator.java` |
| `dualisimport/client/DualisServerLectureData.java` | `externaleventimport/client/ExternalServerLectureData.java` |
| `dualisimport/client/DualisLectureData.java` | `externaleventimport/client/ExternalLectureData.java` |
| `dualisimport/client/DhbwImportDialog.java` | `externaleventimport/client/ExternalEventImportDialog.java` |
| `dualisimport/client/DualisSyncTaskPresenter.java` | `externaleventimport/client/ExternalEventSyncTaskPresenter.java` |
| `dualisimport/client/swing/DualisImportCourseSelectionDialog.java` | `externaleventimport/client/swing/ExternalEventImportCourseSelectionDialog.java` |
| `dualisimport/client/swing/DualisImportPanel.java` | `externaleventimport/client/swing/ExternalEventImportPanel.java` |
| `dualisimport/client/swing/DhbwImportDialogImpl.java` | `externaleventimport/client/swing/ExternalEventImportDialogImpl.java` |
| `dualisimport/client/swing/DhbwSyncButtonExtension.java` | `externaleventimport/client/swing/ExternalEventSyncButtonExtension.java` |
| `dualisimport/client/swing/DualisImportWizard.java` | `externaleventimport/client/swing/ExternalEventImportWizard.java` |
| `dualisimport/client/swing/XTableColumnModel.java` | `externaleventimport/client/swing/XTableColumnModel.java` (no rename — Swing helper, reuse as-is) |

**Total moved: 16 files** (12 client + 3 wire-contract + 1 plugin-constants split-out).

#### 4.2 What stays in dhbwrapla

- `dualisimport/server/DualisEventsLoaderImpl.java` — server impl. Updates its `implements DualisEventsLoader` clause to `implements org.rapla.plugin.externaleventimport.ExternalEventImportService`. Method bodies unchanged.
- `dualisimport/server/DualisImportEventsPrePostDispatchProcessor.java` — server-only; updates its imports to the renamed types in vanilla rapla.
- `CSVImport.java` (was at `dualisimport/CSVImport.java`) — Dualis-CSV-format parser. Repositioned next to the server impl as `org.rapla.dhbw.sync.dualis.csv.CSVImport`. Updates `implements DualisLectureData` → `implements org.rapla.plugin.externaleventimport.client.ExternalLectureData` (the interface lives in vanilla rapla now).
- The CSV column-index constants (formerly in `DualisImportPlugin`) — moved to a new dhbwrapla-side `DualisCsvFormat` class so the Dualis-format-specific values stay together with the parser that uses them.

#### 4.2 Move client classes from dhbwrapla → rapla-client

Move + rename (12 files):

| dhbwrapla path | new rapla-client path |
|---|---|
| `dualisimport/client/DhbwReservationImportController.java` | `externaleventimport/client/ExternalEventImportController.java` |
| `dualisimport/client/DualisReservationCreator.java` | `externaleventimport/client/ExternalEventReservationCreator.java` |
| `dualisimport/client/DualisServerLectureData.java` | `externaleventimport/client/ExternalServerLectureData.java` |
| `dualisimport/client/DualisLectureData.java` | `externaleventimport/client/ExternalLectureData.java` (or move to core if shared) |
| `dualisimport/client/DhbwImportDialog.java` | `externaleventimport/client/ExternalEventImportDialog.java` |
| `dualisimport/client/DualisSyncTaskPresenter.java` | `externaleventimport/client/ExternalEventSyncTaskPresenter.java` |
| `dualisimport/client/swing/DualisImportCourseSelectionDialog.java` | `externaleventimport/client/swing/ExternalEventImportCourseSelectionDialog.java` |
| `dualisimport/client/swing/DualisImportPanel.java` | `externaleventimport/client/swing/ExternalEventImportPanel.java` |
| `dualisimport/client/swing/DhbwImportDialogImpl.java` | `externaleventimport/client/swing/ExternalEventImportDialogImpl.java` |
| `dualisimport/client/swing/DhbwSyncButtonExtension.java` | `externaleventimport/client/swing/ExternalEventSyncButtonExtension.java` |
| `dualisimport/client/swing/DualisImportWizard.java` | `externaleventimport/client/swing/ExternalEventImportWizard.java` |
| `dualisimport/client/swing/XTableColumnModel.java` | `externaleventimport/client/swing/XTableColumnModel.java` (no rename — Swing helper, reuse as-is) |

#### 4.3 Conditional client rendering — the critical detail

The vanilla rapla client must only show the import wizard when a server impl of `ExternalEventImportService` is present. There's no central registry — each deployment ships its own server jar that may or may not provide the impl. Two approaches; pick one explicitly:

**(a) Probe at first render.** `ExternalEventImportWizard` is `@Service @Lazy`. On menu render, the wizard calls a cheap heartbeat method on the proxy (e.g. `service.loadEvents(empty-criteria)` with a dry-run flag, or a dedicated `service.ping()`); if the call returns 404 / connection-refused / `NoSuchMethodError`, the wizard caches a "no" and self-disables for the rest of the session. Pro: deployments don't need to set anything, the wizard discovers reality. Con: first-click latency, wizard logic has to handle three states (unknown / available / unavailable).

**(b) Property-gated.** `@Bean ExternalEventImportService externalEventImportProxy(RestClient.Builder builder, RemoteConnectionInfo info)` is `@ConditionalOnProperty("rapla.externalevents.enabled")`. Deployments that have a server impl set `-Drapla.externalevents.enabled=true`. Without the property, no proxy bean → wizard `@Service @ConditionalOnBean(ExternalEventImportService.class)` doesn't construct → no menu entry. Pro: simple, deterministic, no runtime probe. Con: deployments must remember to set the property; mismatch (server has impl, client misses property) silently hides the feature.

**Recommendation: (b).** It matches the pattern already used in `ServerServiceConfig` for plugin features (`@ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.X")`). Symmetric: server impl is `@ConditionalOnProperty` for the same key on the server side. Both gates flip together via deployment config (Helm chart, systemd unit, etc.). Document the property in the dhbwrapla deployment guide.

(a) can be added later as a smarter UX if the property pattern proves annoying — they're not mutually exclusive.

#### 4.4 Adapt + register on the dhbwrapla server side

In dhbwrapla:
- `DualisEventsLoaderImpl` updates its `implements DualisEventsLoader` clause to `implements org.rapla.plugin.externaleventimport.ExternalEventImportService`. Method bodies unchanged. Add a `@Service` annotation gated by `@ConditionalOnProperty("rapla.externalevents.enabled")` (matching the client-side gate in 4.3) so the bean only registers when the deployment opts in.
- `DualisImportEventsPrePostDispatchProcessor` updates its imports to the renamed types; logic unchanged.
- `CSVImport` is moved next to the server impl as `org.rapla.dhbw.sync.dualis.csv.CSVImport`; its `implements` clause updates to point at the renamed `ExternalLectureData` interface in vanilla rapla.
- The old `Dualis` interface in `org.rapla.dhbw.sync.dualis.server.service` is sanity-checked for non-loader consumers and removed if redundant.

#### 4.5 Delete the moved-upstream files from dhbwrapla

After 4.1–4.4 land and a coordinated deploy of both repos, delete from dhbwrapla:
- `org.rapla.plugin.dhbw.dualisimport.client.**` (12 files — moved upstream)
- `org.rapla.plugin.dhbw.dualisimport.{DualisEventsLoader,DualisEventsResult}` (the 2 wire-contract files — moved upstream)
- The generic-id half of `DualisImportPlugin` (the constants moved upstream as `ExternalEventImportPlugin`)

What's left in dhbwrapla after Phase 4:
- `org.rapla.plugin.dhbw.dualisimport.server.{DualisEventsLoaderImpl,DualisImportEventsPrePostDispatchProcessor}` — the dhbw-specific server side
- `org.rapla.dhbw.sync.dualis.csv.{CSVImport,DualisCsvFormat}` — the dhbw-specific CSV parsing tier (relocated)

The empty `org.rapla.plugin.dhbw.dualisimport.client[.swing]` packages disappear. The shared-tier `dualisimport/` package no longer holds anything except the surviving server-tier sub-package, which can stay as-is or be renamed in a follow-up cleanup.

## Tests

| Phase | Test artifact |
|---|---|
| 1 | `DhbwAuthOptionsControllerTest` (`@SpringBootTest` MockMvc): GET as non-admin → 401; GET as admin → 200 + form fields prefilled from preferences; POST as admin → 303 + preferences updated. |
| 2 | `DhbwTerminalOptionsControllerTest`: same shape, plus assertions on the multi-select round-trip for `eventTypes` etc. |
| 3 | `IndexPageMenuTest` (extension to existing index test): non-admin user gets index page without dhbw links; admin user gets two new `<a>` tags. |
| 4 | `ExternalEventImportServiceContractTest` in rapla-core (interface contract pinned down). `ExternalEventImportWizardConditionalTest` in rapla-app: with `rapla.externalevents.enabled=false`, no `ExternalEventImportWizard` bean; with `=true` and a stub `ExternalEventImportService` impl, the wizard wires. |

## Risks

1. **dhbwrapla and rapla histories are unrelated.** `git merge-base` returns empty. The move in Phase 4 must happen as plain file `cp` + commit on each side, not as a `git mv` across repos. Authorship history for the moved files is lost on the rapla-side commit; mitigate by referencing the dhbwrapla commit hash in the rapla commit message.
2. **Renaming public types is a wire-format break.** `Dualis` → `ExternalEventImportService` changes JSON-RPC method paths and DTO class names that go into Jackson's serialization. Any pre-rename client + post-rename server (or vice-versa) will fail. Coordinate the rename across both repos in one deploy window. The existing dhbwrapla deployment has zero non-DHBW consumers, so the blast radius is small.
3. **Server-side preferences write needs the operator to be admin too.** `facade.edit(facade.getSystemPreferences())` works only if the calling user is admin; the controller's super-admin check is the same gate, so no surprise — but verify with a non-admin manually changing the JWT subject claim.
4. **The Swing panels currently have un-localized German labels.** Server-side rendering preserves this verbatim. If localization comes later, both pages need the same i18n surface that `RaplaResources` provides.
5. **`addCopyPaste(JComponent, …)` and other Swing helpers in `TerminalOption`** disappear when the page becomes HTML — that's fine (browsers handle copy/paste natively). The `getAddress()` method's `urlEncryption.encrypt(...)` round-trip stays the same; the URL is computed once on the server during render.

## Open Questions

1. **Is `User.isAdmin()` the right gate, or do we want a separate "super admin" tier?** Rapla today only has admin/non-admin. If a future PRD adds a super-admin tier, the gate string in this PRD becomes the upgrade point.
2. **Should the dhbw server jar publish its own Spring autoconfig (`META-INF/spring/AutoConfiguration.imports`)** so the controllers pick up automatically when the jar is on the classpath, or stay with explicit `@Import` from the main rapla app? Existing dhbw plugins seem to assume the latter; revisit if the autoconfig path fits better.
3. **`ExternalLectureData` — core or client?** Today `DualisLectureData` is a client-only DTO; if the import service returns `List<ExternalLectureData>`, it has to live in core (shared with both server and client). Decide before Phase 4.1.
4. **What happens to existing dhbwrapla deployments mid-migration?** Phase 1 + 2 require a coordinated dhbwrapla server upgrade (super-admin re-installs). Phase 4 requires both repos to upgrade in the same window. Provide a rollback plan: keep the old Swing panels in dhbwrapla as `@Deprecated` for one release before deletion.
5. **Should `ExternalEventImportService` expose a `listCategories()` method?** Today `DualisEventsLoader` has a single `loadEvents` operation — the wizard hardcodes the source label "Dualis Import". For a generic `externaleventimport`, listing what import sources are available makes the UI honest, but the only consumer today is dhbw with one source. Default: skip `listCategories()` for now, hardcode source label as a constant in the plugin config; revisit if a second source ever shows up.
6. **Drop the old `Dualis` interface in `org.rapla.dhbw.sync.dualis.server.service`?** Phase 4.4 sanity-check item. May still have non-loader consumers; verify before deleting.

## Dependencies on other PRDs

| PRD | Relationship |
|---|---|
| **001** Spring Boot Migration | Hard prerequisite — the controllers need the Spring MVC + Security setup that PRD 001 produced. |
| **002** Swing UI Spring DI Migration | Soft — the `ExternalEventImportWizard` will be `@Service`-annotated using the patterns established there. |
| **009** Server Bulk Storage REST | Soft — the `RaplaExceptionHandler` (PRD 009 Phase 5) handles the `RaplaSecurityException` → 401 mapping the new controllers rely on. |
| **003** Custom Deployments after Spring Migration | This PRD is one of the dhbw-specific items that PRD 003 anticipated. |

## Effort estimate

- Phase 1 (auth options page): 1 day
- Phase 2 (terminal options page): 1–2 days (more form fields)
- Phase 3 (index links): 0.5 day
- Phase 4 (dualisimport → externaleventimport move): 4–5 days
   - 4.0 shared-tier split + DTO rename: ~1 day (the `DualisEventsResult` rename touches every dhbwrapla call site that references the German type-key constants)
   - 4.1 interface carve-out in rapla-core: ~0.5 day
   - 4.2 client file moves: ~1 day (12 files, mechanical with rename)
   - 4.3 conditional wiring: ~0.5 day
   - 4.4 dhbwrapla server-side adapt: ~0.5 day
   - 4.5 deletion + cleanup: ~0.5 day
   - contract + conditional test: ~0.5–1 day
- **Total: ~7–9 days** of focused work for a single agent.

## Out-of-scope follow-ups

- Localization sweep on both new pages (German strings → `RaplaResources` keys).
- Replacing the `<select multiple>` UX with a search-and-multi-pick widget for the terminal page's resource-type lists (the Swing version uses a renderer; the HTML version starts as plain multi-select).
- Generalizing the gate beyond `isAdmin()` if super-admin tiering arrives.
- Additional `dhbw/*` server pages for the other plugins (resources/rights/exchange) — none have client artifacts today, so no migration is needed; but admin-config pages might be added later for parity.
