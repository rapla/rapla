# PRD 020: Server-Driven Admin / Preferences Panels

**Status:** in-progress — foundation done; 5/11 vanilla + 4 dhbw panels migrated (2026-05-10)
**Date:** 2026-05-10

## Goal

Replace per-plugin Swing `PluginOptionPanel` classes (and the small dhbw
server-rendered HTML admin pages) with a **single generic renderer on the
client driven by structured server-side panel definitions**. Same mechanic as
the externaleventimport wizard ([PRD 012](012-dhbwrapla-client-migration.md)) and dynamic-type editor: server
publishes structure + data + actions, client renders with a fixed widget
toolkit — no plugin/deployment knowledge needed.

After this PRD: dhbw's three small admin needs (Morada URL, Terminal URL,
LDAP role-mapping) are covered; vanilla rapla's `PluginOptionPanel` impls
can migrate in follow-up. Custom deployments add admin panels via `@Service`
beans — no client jar, no custom Swing, no signing chain.

## Why now

1. **Backend HTML admin pages dead-end** — [PRD 003](003-custom-deployments-after-spring-migration.md) §I's `/dhbw/terminal/url` works but has no evolution path (no shared styling/widgets, all artisanal HTML).
2. **The mechanism exists for adjacent surfaces** — dynamic-type GUI + externaleventimport wizard ([PRD 012](012-dhbwrapla-client-migration.md) §B1/B2/B4) are already "server data → client widgets". Closing the gap removes a one-off HTML path.
3. **2026-05-07 direction** — custom deployments don't ship Swing. Existing `PluginOptionPanel`s were the last place a deployment had to choose between custom-client-jar or yaml-only.
4. **dhbw needs LDAP role-mapping editing now** — `DhbwAuthPluginOptionPanel` was deleted in [PRD 003](003-custom-deployments-after-spring-migration.md) D2; role mappings in `application.yml` require redeploy. This PRD's first concrete deliverable.

## Scope

### In scope

- New REST wire contract `PreferencesAdminService` in rapla-core (list/get/save/invokeAction).
- Server SPI `PreferencesPanel` (Spring beans, vanilla auto-discovers via `Set<PreferencesPanel>`).
- Generic Swing renderer in rapla-client (one definition → one tab/dialog; reuses Category/Allocatable/etc. pickers).
- New top-level menu listing server-reported panels.
- Three dhbw panels: Morada URL display, Terminal URL computation, LDAP role-mappings editor.

### Out of scope

- Migrating existing rapla `PluginOptionPanel` impls (follow-up; legacy extension point kept alongside).
- Web/Angular renderer of the same panels (wire contract makes it possible later).
- Generic schema-validated form-builder UX (fixed field-type set for now).
- Server-side editing of `application.yml` — yaml stays read-at-startup; runtime edits live in rapla `Preferences` (yaml = defaults).

## Architecture

### Wire contract (rapla-core)

```java
@HttpExchange("/admin/panels")
public interface PreferencesAdminService {
    @GetExchange
    List<PanelSummary> listPanels() throws RaplaException;

    @GetExchange("/{id}")
    PanelDefinition getPanel(@PathVariable String id) throws RaplaException;

    @PostExchange("/{id}/save")
    PanelDefinition savePanel(@PathVariable String id,
                              @RequestBody Map<String, Object> values)
                              throws RaplaException;

    @PostExchange("/{id}/action/{actionId}")
    ActionResult invokeAction(@PathVariable String id,
                              @PathVariable String actionId,
                              @RequestBody Map<String, Object> args)
                              throws RaplaException;
}
```

DTOs (all in `org.rapla.plugin.adminpanels` in rapla-core):

```java
record PanelSummary(String id, String title, boolean adminOnly) {}

record PanelDefinition(
    String id,
    String title,
    String description,                 // free-form help text shown above
    List<Field> fields,
    List<ActionButton> actions,
    Map<String, Object> currentValues   // keyed by Field.key
) {}

record Field(
    String key,                         // form key
    String label,                       // user-visible label (locale-aware)
    FieldType type,
    String helpText,                    // optional; rendered next to / below field
    boolean readOnly,
    Map<String, Object> typeConfig      // type-specific config (see below)
) {}

enum FieldType {
    TEXT,           // single-line text
    LONG_TEXT,      // multi-line, scrollable
    INT,            // integer
    BOOL,           // checkbox
    SELECT,         // dropdown; typeConfig.options = List<{value, label}>
    MULTI_SELECT,   // list/multi-pick
    CATEGORY_PICK,  // rapla Category picker; typeConfig.rootCategory = id
    ALLOCATABLE_PICK, // rapla Allocatable picker; typeConfig.dynamicTypeKey
    USER_PICK,      // rapla User picker
    DYNAMIC_TYPE_PICK,
    DISPLAY_ONLY    // server-computed, no edit; value rendered as-is
}

record ActionButton(
    String key,
    String label,
    String description,
    boolean confirmRequired,            // show "Are you sure?" dialog first
    String confirmMessage
) {}

record ActionResult(
    boolean success,
    String message,                     // user-visible
    Map<String, Object> updatedValues   // optional: refresh panel values
) {}
```

Mirrors the externaleventimport wizard shape ([PRD 012](012-dhbwrapla-client-migration.md) §B1): structured records, enum-typed kinds, generic `Map<String, Object>` values for contract stability.

### Server SPI (rapla-server)

```java
public interface PreferencesPanel {
    String getId();
    PanelDefinition getDefinition(Locale locale, User user) throws RaplaException;
    PanelDefinition save(User user, Map<String, Object> values) throws RaplaException;
    ActionResult invokeAction(User user, String actionId, Map<String, Object> args) throws RaplaException;
}
```

`PreferencesAdminController` (`@RestController`) injects `Set<PreferencesPanel>`, dispatches by `getId()`. Super-admin gate (`User.isAdmin()`) at controller per `PanelSummary.adminOnly`. Whole-panel gating only for v1.

### Client renderer (rapla-client)

New top-level Swing menu entry gated by `User.isAdmin()`. Opens `AdminPanelsDialog`:
1. `listPanels()` → "no panels" message if empty.
2. Each panel as tab in `JTabbedPane` (or tree nav for many — open question).
3. Per tab: lazy `getPanel(id)` on focus, render via `FieldRenderer` switch on `FieldType`; reuse existing pickers.
4. "Save" → `POST /save` → re-bind widgets to returned definition.
5. `ActionButton` → `JButton` → optional confirm → `POST /action/{id}` → toast/dialog from `ActionResult.message`.

### Activation gate

Server `@ConditionalOnProperty("rapla.adminpanels.enabled", default true)`. Client menu hidden when admin but zero panels reported.

## Plan

### Phase 1 — Wire contract + generic types (rapla-core) — **DONE**

Implemented in `rapla-core/src/main/java/org/rapla/plugin/adminpanels/`: `PanelScope`, `FieldType` (10 types: BOOL, TEXT, LONG_TEXT, PASSWORD, INT, SELECT, RADIO_GROUP, DISPLAY_ONLY, ACTION_BUTTON, JSON_EDITOR), `PanelSummary`, `Field`, `ActionButton`, `ActionResult`, `PanelDefinition`, `PreferencesAdminService` `@HttpExchange("/admin/panels")`. 9-test `PreferencesAdminServiceContractTest` pins shapes/paths/enum-names. JSON_EDITOR is the escape hatch for nested/list data.

### Phase 2 — Server SPI + dispatcher (rapla-server) — **DONE**

`org.rapla.server.adminpanels.PreferencesPanel` SPI + `org.rapla.server.spring.web.PreferencesAdminController` (`@RestController @RequestMapping("/admin/panels")`) injecting `Set<PreferencesPanel>`. Filters by scope, gates SYSTEM on `User.isAdmin()` (non-admins: empty list + `RaplaSecurityException` on direct gets). `PreferencesAdminControllerIntegrationTest` (rapla-app, MockMvc, 7 tests): non-admin gate, admin list, get/save/action round-trip with stub panels.

### Phase 3 — Generic Swing renderer (rapla-client) — **DONE**

`org.rapla.client.swing.internal.adminpanels.*`:
- `FieldRenderer` interface + per-type impls (Bool, Text, LongText, Password, Int, Select, RadioGroup, DisplayOnly, JsonEditor).
- `FieldRendererFactory` switches on `FieldType`.
- `PanelRenderer` lays out one `PanelDefinition` (title + description + GridBag fields + action row); exposes `collectValues()`.
- `ServerDrivenSettingsDialog` (`@Service`) — tree-on-left + form-on-right (modeled on `PreferencesEditUI`).
- REST proxy `preferencesAdminServiceProxy` in `ClientProxyConfig`.
- `FieldRendererTest` (10 tests): each renderer's setValue/getValue round-trip. Wire invariants: INT → `Long`; TEXT empty → `null`; DISPLAY_ONLY never round-trips.
- Reuses classification UI patterns (GridBag label-left, BoxLayout action row, monospace JTextArea+JScrollPane for JSON).

### Phase 4 — Legacy bridge + menu wiring (rapla-client) — **DONE**

`ServerDrivenSettingsDialog` consumes both server panels and existing Swing extension points in one tree: PER_USER merges `Set<UserOptionPanel>` + server PER_USER; SYSTEM merges `Set<SystemOptionPanel>` + `Map<String, Supplier<PluginOptionPanel>>` + server SYSTEM under "Admin"/"Plugins". Save branches: server via `api.savePanel(...)`; legacy mutates a shared editable `Preferences` (one `facade.edit(facade.getPreferences(user))` per session) and `facade.dispatch(...)` on save. `RaplaMenuBar` wires both "Edit > Options" and "Admin > Admin Settings" via `createSettingsAction(scope)`. `SwingClientStartIntegrationTest` + `HeadlessClientNameResolutionIntegrationTest` green.

### Phase 5 — γ-shape plugin enable gates — **DONE (no migration needed)**

Audit found no `@ConditionalOnProperty`-style toggles to migrate: vanilla already drives enables via `Preferences.getEntry(RaplaComponent.PLUGIN_CONFIG)` / `TypedComponentRole<Boolean>` (e.g. `PlanningStatusPlugin.ENABLED`). Two boot-time `@Conditional` gates (`ExternalEventImportEnabledCondition`, dhbw's `DualisEventsLoaderImpl`) stay — appropriate boot-time gates, not user toggles.

### Phase 6 — Migrate vanilla `PluginOptionPanel`s — **5/11 DONE**

Migrated to server-side `@Service` `PreferencesPanel` under `org/rapla/plugin/<name>/server/`:
`PlanningStatusPreferencesPanel` (BOOL), `AppointmentNotePreferencesPanel` (BOOL), `CSVExportPreferencesPanel` (BOOL), `AutoExportPreferencesPanel` (2× BOOL), `TimeslotPreferencesPanel` (JSON_EDITOR over legacy `RaplaConfiguration` "timeslot").

Common base `org.rapla.server.adminpanels.AbstractPluginPreferencesPanel` handles SYSTEM scope + read/clone-edit/dispatch on system prefs. Legacy Swing classes deleted for the five migrated plugins.

Spring discovery: new `AdminPanelsScanConfig` (imported from `RaplaServerAutoConfiguration`) does targeted `@ComponentScan` over `org.rapla.server.adminpanels` + `org.rapla.plugin.*.server` — keeps rest of server tier on explicit-`@Bean` pattern (AGENTS.md §4).

`VanillaPluginPanelsIntegrationTest` (rapla-app, MockMvc): list-includes-all-five + round-trips PlanningStatus + Timeslot.

**Deferred** (heavyweight, on legacy bridge): `Export2iCalAdminOption`, `ExchangeConnectorAdminOptions`, `MailOption`, `ArchiverOption`, `EventTimeCalculatorAdminOption`, `JNDIOption`. `TableviewOption` permanently excluded — separate root tree if needed.

### Phase 7 — dhbw concrete panels (dhbwrapla repo) — **DONE**

Four panels in `~/git/dhbwrapla` under `DHBW/` tree node:

- `TerminalPreferencesPanel` (`org.rapla.plugin.dhbw.dhbwterminal.server`) id `dhbw.terminal`: TEXT kursTyp (one-shot, not persisted) + DISPLAY_ONLY encrypted result + 7 DISPLAY_ONLY mirroring yaml Terminal config (ueberschrift, keinekurse, cssurl, raumTyp, steleUser, eventTypes, resourceTypes) + ACTION_BUTTON "Compute encrypted URL" → `urlEncryption.encrypt(...)`. No save (yaml-authoritative per [PRD 003](003-custom-deployments-after-spring-migration.md) §C4).
- `MoradaPreferencesPanel` (`org.rapla.dhbw.sync.morada.server`) id `dhbw.morada`: DISPLAY_ONLY URL + trust store + connection status + ACTION_BUTTON "Test connection" (HTTP HEAD).
- `LdapRoleMappingsPreferencesPanel` (`org.rapla.plugin.dhbw.auth.server`) id `dhbw.auth`: DISPLAY_ONLY `ldapServer` (yaml) + JSON_EDITOR for `{locationRegex, category, email, exchangeServer}`. Persists via legacy `DhbwAuthPreferences.RoleMapping` "=" / newline shape so `DhbwNtlmAuthStore` unchanged.
- `DualisPreferencesPanel` (`org.rapla.plugin.dhbw.dualisimport.server`) id `dhbw.dualis`: DISPLAY_ONLY url/username/driver/password-status (password never echoed) + ACTION_BUTTON "Test connection" (borrows Hikari conn + `isValid(5)`).

Discovery via `DhbwRaplaApplication`'s `@SpringBootApplication(scanBasePackages = {"org.rapla.dhbw", "org.rapla.plugin.dhbw"})`.

**Tests** (`DhbwPanelsIntegrationTest`, 5 tests): action paths (Morada unreachable host, Terminal encryption seeds field, blank input rejected) + panel-definition shape. LDAP read/save round-trip not unit-tested — cleanest via `@SpringBootTest` once dhbwrapla bean-graph issues sorted.

**Pre-existing dhbwrapla bean-graph fix (2026-05-11):** seven classes were `@jakarta.inject.Singleton` + `@Inject` only — Spring doesn't treat `@Singleton` as stereotype. Chain: `DualisSyncJobStarter` → `DualisImportJob` → `Dualis` impl → `RaplaImportMailSender` → `MailToUserImpl`; parallel: `MoradaImport` → `XmlConverter`; `DhbwNtlmAuthStore` → `DhbwAuthPreferences.AuthPreferencesReader`. Fix: `@Service` on all seven (`XmlConverter`, `JsonConverter`, `RaplaImportMailSender`, `MoradaImportJob`, `MoradaLocationMapping`, `DualisImportJob`, `AuthPreferencesReader`) + `@Service @Scope("prototype")` on the two `Provider<>`-injected mappings (`DualisRaplaMapping`, `MoradaRaplaMapping`). Added test-scope HSQLDB so Hikari binds a driver for the secondary Dualis DataSource. Dhbw deployment now boots without `NoSuchBeanDefinitionException`.

`DhbwPanelsSpringIntegrationTest` (3 tests, end-to-end via live bean graph): `listSystemIncludesAllDhbwPanels` / `ldapRoleMappingsRoundTripPersistsLegacyFormat` / `terminalEncryptionActionUpdatesDisplayField`. Companion `DhbwPanelsIntegrationTest` (no Spring, 5 tests) keeps action-path + yaml-mirror coverage.

`maven-compiler-plugin.testExcludes` in `dhbwrapla/pom.xml` still excludes stale legacy tests (`RestDHBWAPIExample`, `DhbwNtlmTest`, etc.) referencing Jackson 2 / `javax.inject` / removed `org.rapla.test.util` — unrelated, out of scope.

### Phase 8 — Cleanup — **PARTIAL**

- ✅ Deleted `dhbwterminal/server/web/TerminalUrlController.java` + empty `web/` dir. `TerminalPreferencesPanel` covers same use-case.
- ⏸ `JNDIConfigController` / `MailConfigController` / `ArchiverController` (rapla-server) NOT deleted: JSON REST consumed by legacy Swing `JNDIOption`/`MailOption`/`ArchiverOption` panels via legacy bridge. Delete in follow-up that ports those three plugins.

### Cleanup follow-ups

- Update [PRD 003](003-custom-deployments-after-spring-migration.md) §I to point at PRD 020.
- After remaining 6 vanilla migrations (Export2iCal/ExchangeConnector/Mail/Archiver/EventTimeCalc/JNDI), legacy `OptionPanel` extension points + `PreferencesEditUI` can be removed; `ServerDrivenSettingsDialog`'s LegacyEntry bridge also deletes.

## Tests

| Phase | Test |
|---|---|
| 1 | `PreferencesAdminServiceContractTest` (rapla-core): @HttpExchange paths, method shapes, FieldType enum names stable. |
| 2 | `PreferencesAdminControllerTest` (rapla-app, MockMvc): non-admin → 401; admin GET / list/get/save/action with stub `@Bean`. |
| 3 | `AdminPanelsDialogTest` (rapla-client headless): every FieldType renders without throwing; round-trip a save through stub service. |
| 4 | dhbwrapla MockMvc tests for each of the three concrete panels. Role-mappings needs "save then re-read" round-trip. |

## Risks

1. **Field-type expressiveness.** Fixed `FieldType` enum won't cover some legacy panels (calendar previews, inline tables). Design as extensible; add enum values rather than embedded-Swing escape hatch (would reintroduce custom-Swing-jar problem).
2. **yaml vs Preferences sources.** "yaml = defaults, Preferences = runtime overrides" needs a clean read-fallback path so consumers (`DhbwNtlmAuthStore` etc.) read the union without each one knowing the policy. Suggest `PreferenceWithDefault` helper.
3. **Discovery / ordering.** Flat tab list gets unwieldy with many panels. Punt with sort-by-id v1; `PanelSummary` can grow `category` without breaking clients.
4. **i18n.** Same pattern as externaleventimport wizard: server builds labels via `LocaleContextHolder`, client renders verbatim.
5. **Versioning.** Newer server `FieldType` + older client → must fail gracefully (skip field with "(unsupported)" placeholder, not crash). Test in Phase 3.

## Open Questions

1. **Menu entry name** — "Plugin Settings" / "Server Configuration" / "Admin Panels"? Leaning "Plugin Settings" for continuity.
2. **Tabs vs tree.** Tabs ok for 1–5; tree for 20+. Punt: ship tabs.
3. **`SELECT` typeConfig shape.** Inline `List<{value, label}>` is simple; server cost low. Lean inline.
4. **Action results that update fields.** Default mechanism for "compute X" actions, or separate "computeOnly" field type? Open.
5. **Concurrent edits** — last-write-wins for v1; `lastModifiedTimestamp` for mid-air collisions probably out of scope.
6. **Action authorization** — per-action `adminOnly` flag? Punt; panel-level only.

## Cross-references

| PRD | Relationship |
|---|---|
| **003** custom deployments | This PRD's mechanism is what §I should have been. [PRD 003](003-custom-deployments-after-spring-migration.md) §I superseded by PRD 020 Phase 4b. |
| **012** dhbwrapla client carve-out | Wire-contract pattern mirrors [PRD 012](012-dhbwrapla-client-migration.md) §B1 (`ExternalEventImportMetadata`). 012 made *import wizard* generic; 020 makes *admin/preferences UI* generic. |
| **019** Spring Boot lifecycle | [PRD 019](done/019-spring-boot-lifecycle-migration.md)'s storage-up ordering means `PreferencesPanel` beans injecting `RaplaFacade` get a connected one before `@PostConstruct` — no `@DependsOn` needed. |
| **AGENTS.md §11** | Phase 5 `TerminalUrlController` deletion is planned (function moves upstream), not a not-to-fix-compile deletion. |
