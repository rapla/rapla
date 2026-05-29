# PRD 012: Migrate dhbwrapla client-side plugin code to server pages / general rapla

**Status:** in-progress — rapla-side carve-out fully landed 2026-05-10 (`externaleventimport` wire contract + 12-file move + metadata-driven wizard refactor + `ExternalEventImportResources` bundle + property-gated activation). dhbwrapla server-side adapter (`DualisEventsLoaderImpl` → `ExternalEventImportService`) pending under PRD 003 D2/E.
**Date:** 2026-05-08

## 2026-05-10 Implementation snapshot

**Major design refinement: server-driven UI metadata.** Wizard is fully generic — column labels, hierarchy levels, source name, CSV-support flag all come from server's `getMetadata()` response at runtime. Vanilla rapla carries zero domain-specific terminology (no "Studiengang", "Fakultät", "Dualis"). Every dhbw user-visible string lives in dhbw's server-side `getMetadata()` Java return, NOT in client-side `.properties`/`.java`. `Locale`-aware metadata returns German strings to German clients. UI templates parameterize source-supplied nouns (e.g. `"Please choose at least one {0} from the list"` filled with `metadata.hierarchyLevels[leaf].label`).

**Wire contract additions** (beyond original §4.1):
- `getMetadata()` GET — returns `ExternalEventImportMetadata` (sourceName, hierarchyLevels, resultColumns, supportsCsvImport, csvFileFilterLabel, uiMessageOverrides, selectableAllocatableTypeKey, maxSelectableItems).
- `uploadCsv(MultipartFile)` POST — server-side CSV parse, returns same `ExternalEventImportResult` shape.
- `createReservations(CreateReservationsRequest)` POST — server-side reservation creation/sync. Wizard sends only picked source-item IDs; mapping stays server-side.

**Wire DTO simplification**: typed `ExternalExam`/`ExternalLecture`/`ExternalCourse` from original §4.1 dropped in favor of generic `ImportItem { sourceItemId, hierarchy: Map<String,String>, columns: Map<String,Object> }`. Wizard renders any column shape declared by `metadata.resultColumns`.

**Activation gate**: `@Conditional(ExternalEventImportEnabledCondition.class)` — custom condition (not `@ConditionalOnProperty` since rapla-client stays on plain spring-context with no spring-boot-autoconfigure). Checks `rapla.externalevents.enabled`. Resolves §4.3 in favor of (b).

**Files landed in rapla-core/`org.rapla.plugin.externaleventimport`**: `ExternalEventImportPlugin` (constants), `ExternalEventImportService` (`@HttpExchange`), `ExternalEventImportMetadata`, `ExternalEventImportResult`, `ImportItem`, `ImportCriteria`, `CreateReservationsRequest`, `HierarchyLevel`, `ResultColumn`. Pinned by `ExternalEventImportServiceContractTest` (9 assertions).

**Files landed in rapla-client/`org.rapla.plugin.externaleventimport.client[/swing]`** — replaces all 12 dhbwrapla `dualisimport/client/**`:
- Top-level: `ExternalEventImportController`, `ExternalEventImportDialog` (interface), `ExternalEventImportSubmitCallback`, `ExternalEventSyncTaskPresenter`, `ExternalEventImportEnabledCondition`, `ExternalEventImportResources` (15-key, EN+DE).
- Swing: `ExternalEventImportPanel` (metadata-driven JTable), `ExternalEventImportAllocatableSelectionDialog`, `ExternalEventImportDialogImpl`, `ExternalEventSyncButtonExtension`, `ExternalEventImportWizard`, `XTableColumnModel`.

**dhbwrapla deletions**: `org.rapla.plugin.dhbw.dualisimport.client/**` (12 files), `DualisEventsLoader.java` + `DualisEventsResult.java`, `DhbwResources.java` + 2 `.properties` (replaced by metadata).

**Phases 1+2+3 (auth/terminal admin pages, index links)** — ALL DROPPED per user direction 2026-05-10. Config moved to `application.yml` / `DhbwProperties` (no UI). One tiny admin page kept: `TerminalUrlController` for encrypted export-URL query (PRD 003 §I).

Pending: dhbwrapla server-side `DualisEventsLoaderImpl` rewrite as `ExternalEventImportService` impl with `Locale`-aware `getMetadata()` (PRD 003 Phase E).


## Goal

The dhbwrapla fork (`~/git/dhbwrapla`, separate repo, unrelated git history) carries 14 Swing-client classes across three plugins. Two (`DhbwAuthPluginOptionPanel`, `TerminalOption`) are admin-only config panels that don't belong on the client — they become server-rendered HTML pages, super-admin gated. The third (`dualisimport`, 12 classes) is a general-purpose external-event import wizard hard-wired to Dualis; it moves to main rapla, renamed `externaleventimport`, only shown when a corresponding server endpoint exists.

After this PRD: dhbwrapla's `src/main/java/org/rapla/plugin/dhbw/*/client/**` tree shrinks to zero. The DHBW deployment needs only a generic rapla client + a dhbw server-side jar.

## Scope

### In scope

| Class | Current location (dhbwrapla) | Target |
|---|---|---|
| `DhbwAuthPluginOptionPanel` | `org.rapla.plugin.dhbw.auth.client.swing` | New server page in `rapla-server` (or dhbw-server jar) — HTML form, super-admin gated |
| `TerminalOption` | `org.rapla.plugin.dhbw.dhbwterminal.client.swing` | Same: server-rendered HTML form, super-admin gated |
| 12 `dualisimport/client/**` classes | `org.rapla.plugin.dhbw.dualisimport.client[.swing]` | Move to general rapla, rename to `org.rapla.plugin.externaleventimport.client[.swing]` |
| Wire-contract types reachable from client (`DualisEventsLoader` REST interface, `DualisEventsResult` DTO, generic plugin id constant) | top of `dualisimport` package | Move to `rapla-core` under `org.rapla.plugin.externaleventimport.*` (renamed). They live in vanilla rapla so any deployment's server impl can compile against them. |
| Dualis-specific code (CSV parser `CSVImport`, CSV column-index constants, **all of `dualisimport/server/**`**) | top of `dualisimport` package + `dualisimport/server` | Stays in dhbwrapla. Server impl now `implements ExternalEventImportService`. |

### Out of scope

- The **server-side** dhbw plugin code (`*/server/**`: auth/`DhbwLdap*`, dhbwterminal/`SteleKursUebersichtPageGenerator*`, etc.).
- `pruefungen`, `dualisexport`, `merge`, `resources`, `rights`, `semesterplan`, `exchange`: zero client classes.
- The dhbwrapla repo's own build / packaging shape.
- Localization — both panels have hardcoded German labels (`"Ueberschrift der Uebersichtsseite"`, `"Benutzung: …"`). Keep verbatim during migration; i18n is separate cleanup.

## Why server pages, not Swing panels

Both panels mutate **system preferences** (`DhbwAuthPreferences.CONFIG`, `TerminalConstants.CONFIG`) — server-side state. Values are read by **server-side code** (LDAP authenticate, terminal HTML generators); round-tripping through the desktop client adds nothing. Single super-admin gate is naturally enforced server-side. Pattern already established in main rapla: `RaplaIndexPageGenerator`, `RaplaStatusPageGenerator`, `Export2iCalServlet`, `HTMLViewPage` impls — all do plain `PrintWriter` HTML.

## Decision dimensions

### A. Where do the new server pages live?

**Pick A2: in dhbwrapla server jar.** Main rapla server has standard plugin-scan path; dhbw jar contributes controllers + option page beans. Rejected A1 (`rapla-server/.../plugin/dhbw/`): pollutes generic codebase with tenant concern.

### B. URL shape

Existing admin-page pattern `/rapla/<page-name>`. Pick: `GET`/`POST /rapla/dhbw/auth/options` and `/rapla/dhbw/terminal/options`. Behind a single `/rapla/dhbw/**` Spring filter enforcing super-admin.

### C. Form rendering

Plain `PrintWriter` HTML, matching `RaplaIndexPageGenerator`. Don't introduce Thymeleaf for two pages. Panels render ≤8 form fields each; inlined HTML with small `<input>` row helper. CSRF via Spring Security default.

### D. Super-admin gating

Two layers: Spring Security URL rule (`requestMatchers("/rapla/dhbw/**").authenticated()`) + method-level `User user = remoteSession.checkAndGetUser(request); if (!user.isAdmin()) throw new RaplaSecurityException(...)`. Existing `RaplaExceptionHandler` (PRD 009 Phase 5) maps to 401. `isAdmin()` is the existing super-admin marker (single admin tier); finer-grained role is a separate PRD.

### E. Conditional activation for the moved `externaleventimport`

Today `DualisImportWizard` registers unconditionally. After the move: wizard interface becomes generic `ExternalEventImportService` (REST); client-side `ExternalEventImportWizard` is `@ConditionalOnBean(ExternalEventImportService.class)`. When no server-side impl exists, proxy resolves to nothing and wizard self-disables. dhbwrapla's `DualisEventsLoaderImpl` continues in dhbw server jar but now **implements `ExternalEventImportService`**.

## Plan

### Phase 1 — `DhbwAuthOptionsController` (1 day, dhbwrapla)

1. Create `org.rapla.plugin.dhbw.auth.server.web.DhbwAuthOptionsController` (`@Controller`).
2. `@GetMapping("/dhbw/auth/options")`:
    - `User user = remoteSession.checkAndGetUser(request); if (!user.isAdmin()) throw new RaplaSecurityException(...)`.
    - `RaplaConfiguration cfg = facade.getSystemPreferences().getEntry(DhbwAuthPreferences.CONFIG, ...)`.
    - Render HTML form with `ldapServer` text input + `roleMapping` textarea.
3. `@PostMapping("/dhbw/auth/options")`:
    - Same admin check.
    - Read form params, build fresh `DhbwAuthPreferences`, write back via `Preferences edit = facade.edit(facade.getSystemPreferences()); edit.putEntry(DhbwAuthPreferences.CONFIG, ...); facade.store(edit)`.
    - Redirect 303 → `GET /dhbw/auth/options?saved=1` so refresh doesn't re-submit.
4. **Delete** `DhbwAuthPluginOptionPanel.java` from dhbwrapla once verified.

### Phase 2 — `DhbwTerminalOptionsController` (1–2 days, dhbwrapla)

Same shape, larger field set from `TerminalOption`:

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

Add two `HtmlMainMenu` `@Bean` factories in dhbw server jar's Spring config (sibling to `RaplaJnlpEntry` / `RaplaStatusEntry` in main rapla's `ServerServiceConfig`). They emit anchor links into `RaplaIndexPageGenerator` output, **conditional on `user.isAdmin()`** — non-admins don't see the link.

### Phase 4 — Move `dualisimport` to main rapla, rename to `externaleventimport` (3–4 days)

#### 4.0 Direction of dependencies (the rule the rest of Phase 4 follows)

Strict rule:

- **Vanilla rapla never depends on dhbwrapla.** Has the generic wizard + wire contract.
- **dhbwrapla depends on vanilla rapla.** Server jar provides `ExternalEventImportService` impl as Spring bean.
- **Any other deployment** can also provide its own impl without touching vanilla rapla.

This forces the wire contract (interface + result DTO + generic plugin-id constant) into vanilla rapla. Server impl + Dualis CSV parser + any dhbw-specific detail stays in dhbwrapla.

#### 4.1 What moves (vanilla rapla)

In `rapla-core/org.rapla.plugin.externaleventimport`:
- `ExternalEventImportService` — REST interface, renamed from `DualisEventsLoader`. Rewritten as Spring `@HttpExchange` (drops `jakarta.ws.rs` per PRD 010). Single method `ExternalEventImportResult loadEvents(ImportCriteria criteria)`.
- `ExternalEventImportResult` — renamed from `DualisEventsResult`. Nested types renamed `Pruefung`/`Veranstaltung`/`Course` → `ExternalExam`/`ExternalLecture`/`ExternalCourse`. The two German `DynamicType`-key constants (`VERANSTALTUNG_TYPE_KEY = "Lehrveranstaltung"`, `PRUEFUNG_TYPE_KEY = "Pruefung"`) keep their **string values** verbatim — they're rapla type keys; renaming would force every dhbwrapla deployment to re-key existing data.
- `ExternalEventImportPlugin` — constants holding `org.rapla.plugin.externaleventimport` + `ENABLE_BY_DEFAULT = false`.
- `ImportCriteria` — request DTO; small POJO so interface signature stays stable.

In `rapla-client/org.rapla.plugin.externaleventimport.client[.swing]`: move all 12 dhbwrapla `dualisimport/client/**` files, rename `Dhbw`/`Dualis*` → `ExternalEvent*` (e.g. `DualisImportPanel` → `ExternalEventImportPanel`, `DhbwSyncButtonExtension` → `ExternalEventSyncButtonExtension`; `XTableColumnModel` keeps its name). See "Files landed in rapla-client" in the 2026-05-10 snapshot above for the full target list.

**Total moved: 16 files** (12 client + 3 wire-contract + 1 plugin-constants).

#### 4.2 What stays in dhbwrapla

- `dualisimport/server/DualisEventsLoaderImpl.java` — updates `implements DualisEventsLoader` → `implements org.rapla.plugin.externaleventimport.ExternalEventImportService`. Method bodies unchanged.
- `dualisimport/server/DualisImportEventsPrePostDispatchProcessor.java` — server-only; imports updated.
- `CSVImport.java` — Dualis-CSV parser. Repositioned as `org.rapla.dhbw.sync.dualis.csv.CSVImport`. Updates `implements DualisLectureData` → `implements org.rapla.plugin.externaleventimport.client.ExternalLectureData`.
- CSV column-index constants (formerly in `DualisImportPlugin`) — moved to new dhbwrapla-side `DualisCsvFormat` class.

#### 4.3 Conditional client rendering — the critical detail

Vanilla client must only show the wizard when a server impl of `ExternalEventImportService` is present. Two approaches:

- **(a) Probe at first render** — `@Service @Lazy` wizard calls a cheap heartbeat on first menu render; cache result per session. Pro: zero config. Con: first-click latency, three-state logic.
- **(b) Property-gated** — `@Bean ExternalEventImportService externalEventImportProxy(...)` is `@ConditionalOnProperty("rapla.externalevents.enabled")`. Deployments with the impl set `-Drapla.externalevents.enabled=true`. Pro: simple, deterministic. Con: deployments must remember; mismatch silently hides feature.

**Pick (b)** — matches the existing `ServerServiceConfig` pattern (`@ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.X")`). Symmetric: server-side impl uses the same key. (a) can be added later as smarter UX.

#### 4.4 Adapt + register on the dhbwrapla server side

In dhbwrapla:
- `DualisEventsLoaderImpl` updates `implements` clause; add `@Service` gated by `@ConditionalOnProperty("rapla.externalevents.enabled")` matching client-side gate.
- `DualisImportEventsPrePostDispatchProcessor` updates imports.
- `CSVImport` moved next to server impl as `org.rapla.dhbw.sync.dualis.csv.CSVImport`; `implements` updates to renamed `ExternalLectureData`.
- Old `Dualis` interface in `org.rapla.dhbw.sync.dualis.server.service` sanity-checked for non-loader consumers and removed if redundant.

#### 4.5 Delete the moved-upstream files from dhbwrapla

After 4.1–4.4 and coordinated deploy of both repos, delete from dhbwrapla:
- `org.rapla.plugin.dhbw.dualisimport.client.**` (12 files — moved upstream)
- `org.rapla.plugin.dhbw.dualisimport.{DualisEventsLoader,DualisEventsResult}` (2 wire-contract files)
- Generic-id half of `DualisImportPlugin` (constants moved upstream as `ExternalEventImportPlugin`)

What's left in dhbwrapla after Phase 4:
- `org.rapla.plugin.dhbw.dualisimport.server.{DualisEventsLoaderImpl,DualisImportEventsPrePostDispatchProcessor}` — dhbw-specific server side
- `org.rapla.dhbw.sync.dualis.csv.{CSVImport,DualisCsvFormat}` — dhbw-specific CSV parsing tier (relocated)

Empty `org.rapla.plugin.dhbw.dualisimport.client[.swing]` packages disappear.

## Tests

| Phase | Test artifact |
|---|---|
| 1 | `DhbwAuthOptionsControllerTest` (`@SpringBootTest` MockMvc): GET as non-admin → 401; GET as admin → 200 + form fields prefilled; POST as admin → 303 + preferences updated. |
| 2 | `DhbwTerminalOptionsControllerTest`: same shape + multi-select round-trip for `eventTypes` etc. |
| 3 | `IndexPageMenuTest`: non-admin user gets index without dhbw links; admin gets two new `<a>` tags. |
| 4 | `ExternalEventImportServiceContractTest` in rapla-core. `ExternalEventImportWizardConditionalTest` in rapla-app: with `rapla.externalevents.enabled=false`, no wizard bean; with `=true` + stub impl, wizard wires. |

## Risks

1. **dhbwrapla and rapla histories are unrelated.** `git merge-base` returns empty. Phase 4 move must happen as plain file `cp` + commit on each side, not `git mv` across repos. Authorship history lost on rapla-side commit; mitigate by referencing dhbwrapla commit hash in rapla commit message.
2. **Renaming public types is a wire-format break.** `Dualis` → `ExternalEventImportService` changes JSON-RPC method paths + DTO class names. Pre-rename client + post-rename server (or vice-versa) will fail. Coordinate across both repos in one deploy window. Existing dhbwrapla deployment has zero non-DHBW consumers, so blast radius is small.
3. **Server-side preferences write needs operator to be admin too.** `facade.edit(facade.getSystemPreferences())` works only if calling user is admin; controller's super-admin check is same gate — verify with non-admin manually changing JWT subject claim.
4. **Swing panels currently have un-localized German labels.** Server-side rendering preserves verbatim. If localization comes later, both pages need same i18n surface as `RaplaResources`.
5. **`addCopyPaste(JComponent, …)` and other Swing helpers in `TerminalOption`** disappear when page becomes HTML — fine (browsers handle copy/paste natively). `getAddress()` method's `urlEncryption.encrypt(...)` stays; URL computed once on server during render.

## Open Questions

1. **Is `User.isAdmin()` the right gate, or a separate "super admin" tier?** Rapla today only has admin/non-admin. Future super-admin tier would be the upgrade point.
2. **Should dhbw server jar publish its own Spring autoconfig (`META-INF/spring/AutoConfiguration.imports`)** so controllers pick up automatically when jar is on classpath, or stay with explicit `@Import` from main rapla app? Existing dhbw plugins assume the latter; revisit if autoconfig path fits better.
3. **`ExternalLectureData` — core or client?** Today `DualisLectureData` is client-only DTO; if import service returns `List<ExternalLectureData>`, it lives in core. Decide before Phase 4.1.
4. **What happens to existing dhbwrapla deployments mid-migration?** Phase 1 + 2 need coordinated dhbwrapla server upgrade. Phase 4 needs both repos upgraded in same window. Provide rollback: keep old Swing panels in dhbwrapla as `@Deprecated` for one release before deletion.
5. **Should `ExternalEventImportService` expose `listCategories()`?** Today single `loadEvents`; wizard hardcodes "Dualis Import". For generic `externaleventimport`, listing import sources makes UI honest, but only consumer today is dhbw with one source. Default: skip for now; revisit if second source appears.
6. **Drop old `Dualis` interface in `org.rapla.dhbw.sync.dualis.server.service`?** Phase 4.4 sanity-check item.

## Dependencies on other PRDs

| PRD | Relationship |
|---|---|
| **001** Spring Boot Migration | Hard prerequisite — controllers need Spring MVC + Security setup. |
| **002** Swing UI Spring DI Migration | Soft — `ExternalEventImportWizard` will be `@Service`-annotated using those patterns. |
| **009** Server Bulk Storage REST | Soft — `RaplaExceptionHandler` (PRD 009 Phase 5) handles `RaplaSecurityException` → 401 the new controllers rely on. |
| **003** Custom Deployments after Spring Migration | This PRD is one of the dhbw-specific items PRD 003 anticipated. |

## Effort estimate

- Phase 1 (auth options page): 1 day
- Phase 2 (terminal options page): 1–2 days
- Phase 3 (index links): 0.5 day
- Phase 4 (dualisimport → externaleventimport move): 4–5 days
   - 4.0 shared-tier split + DTO rename: ~1 day
   - 4.1 interface carve-out in rapla-core: ~0.5 day
   - 4.2 client file moves: ~1 day (12 files, mechanical with rename)
   - 4.3 conditional wiring: ~0.5 day
   - 4.4 dhbwrapla server-side adapt: ~0.5 day
   - 4.5 deletion + cleanup: ~0.5 day
   - contract + conditional test: ~0.5–1 day
- **Total: ~7–9 days** focused work for a single agent.

## Out-of-scope follow-ups

- Localization sweep on both new pages (German strings → `RaplaResources` keys).
- Replacing `<select multiple>` UX with search-and-multi-pick widget for terminal page's resource-type lists.
- Generalizing the gate beyond `isAdmin()` if super-admin tiering arrives.
- Additional `dhbw/*` server pages for other plugins — none have client artifacts today; admin-config pages might be added later for parity.
