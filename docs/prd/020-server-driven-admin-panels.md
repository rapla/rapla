# PRD 020: Server-Driven Admin / Preferences Panels

**Status:** in-progress — foundation done; 5/11 vanilla + 4 dhbw panels migrated (2026-05-10)
**Date:** 2026-05-10

## Goal

Replace per-plugin Swing `PluginOptionPanel` classes (and the small dhbw
server-rendered HTML admin pages we built ad-hoc) with a **single generic
renderer on the client driven by structured server-side panel definitions**.

The mechanic mirrors what we already have for the externaleventimport wizard
(PRD 012) and for the dynamic-type editor: the **server publishes structure
+ data + actions**, the **client renders** them with a fixed widget toolkit.
The client never needs to know which plugin or which deployment owns a panel —
it just walks the server-supplied tree of fields/buttons and renders them.

After this PRD: dhbw's three small admin needs (Morada URL display, Terminal
URL computation, LDAP role-mapping editor) are covered by the generic
mechanism; vanilla rapla can migrate its existing `PluginOptionPanel` impls
to the same path in a follow-up. Custom deployments (dhbwrapla, future
deployments) add admin panels by registering `@Service` beans on the server
side — no client jar, no custom Swing, no separate signing chain.

## Why this is needed now

1. **Backend HTML admin pages are dead-ends.** PRD 003 §I added the small
   `/dhbw/terminal/url` HTML page as the one admin page kept after deleting
   the Swing option panels. It works but has no good evolution: no consistent
   styling with rapla, no shared widget toolkit, every page is artisanal HTML.
2. **The mechanism we want already exists for two adjacent surfaces.** The
   dynamic-type GUI is exactly "server data → client widgets," and the
   externaleventimport wizard (PRD 012 §B1, B4, B2) is "server metadata →
   client UI." Generalising it to preferences/admin closes the gap and
   removes a one-off HTML rendering path.
3. **The 2026-05-07 direction change** says custom deployments don't ship
   Swing code. The remaining Swing `PluginOptionPanel` impls are the last
   place where a deployment that wants to add an admin knob has to either
   ship a custom client jar or fall back to yaml-only config. This PRD
   gives them a third option that fits the no-custom-Swing rule.
4. **dhbw needs LDAP role-mapping editing now.** The legacy
   `DhbwAuthPluginOptionPanel` was deleted in PRD 003 D2. Role mappings
   currently live in `application.yml` (config-driven) — fine for one-time
   setup but wrong for ops, since edits require a redeploy. PRD 020's
   first concrete deliverable is editable role mappings via a server-driven
   panel.

## Scope

### In scope

- A new wire contract `PreferencesAdminService` (REST, in rapla-core) for
  listing panels, fetching a panel's definition + current values, saving
  values, and invoking action buttons.
- A `PreferencesPanel` server-side SPI (interface) — Spring beans implement
  this; vanilla rapla auto-discovers them via `Set<PreferencesPanel>`
  injection.
- A generic Swing renderer in rapla-client — one panel-definition → one
  rendered tab/dialog. Reuses existing rapla widgets (Category picker,
  Allocatable picker, etc.) for typed fields.
- A new top-level menu entry on the client ("Plugin Settings" or
  "Server Configuration" — see Open Question 1) that lists all server-
  reported panels.
- Three concrete dhbw panels: Morada URL display, Terminal URL computation,
  LDAP role-mappings editor.

### Out of scope (this PRD)

- Migrating existing rapla `PluginOptionPanel` impls to the new mechanism.
  Done as a follow-up once the renderer is proven and the wire contract
  is stable. PRD 020 keeps the legacy `PluginOptionPanel` extension point
  alive and the new mechanism alongside; unification is a future PRD.
- Web/Angular renderer of the same panels (the JSON wire contract makes
  this possible later; this PRD only covers the Swing renderer).
- Generic schema-validated form-builder UX (e.g. JSON Schema integration).
  We pick a small fixed set of field types for now and grow it as needed.
- Server-side editing of `application.yml` values. yaml stays
  read-at-startup; values that need runtime edits live in rapla
  `Preferences` (the same store the legacy `PluginOptionPanel` wrote to).
  yaml remains the source of defaults when no Preferences entry exists.

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

The wire types deliberately mirror what worked for the
externaleventimport wizard (PRD 012 §B1): structured records, enum-typed
field kind, generic `Map<String, Object>` for values to keep the contract
stable across field-type extensions.

### Server SPI (rapla-server)

```java
public interface PreferencesPanel {
    String getId();
    PanelDefinition getDefinition(Locale locale, User user) throws RaplaException;
    PanelDefinition save(User user, Map<String, Object> values) throws RaplaException;
    ActionResult invokeAction(User user, String actionId, Map<String, Object> args) throws RaplaException;
}
```

Implementation in vanilla rapla:
- `PreferencesAdminController` (`@RestController`) injects
  `Set<PreferencesPanel>` and dispatches by `getId()`.
- Super-admin gate (`User.isAdmin()`) enforced at the controller layer
  per PanelSummary.adminOnly. Per-field permissions are not in scope;
  whole-panel admin gating is enough for v1.

### Client renderer (rapla-client)

- New top-level Swing menu entry, gated by `User.isAdmin()`. Opens an
  `AdminPanelsDialog` that:
  1. Calls `listPanels()`. If empty, shows "no admin panels available".
  2. Renders each panel as a tab in a `JTabbedPane`, OR as a tree-based
     navigation if there are many — open question.
  3. For each tab, calls `getPanel(id)` lazily on first focus, then
     renders the field list via a `FieldRenderer` switch on
     `FieldType`. Existing rapla widgets are reused for the typed
     pickers (Category, Allocatable, etc.).
  4. "Save" button per tab → `POST /admin/panels/{id}/save`. Server
     returns the refreshed `PanelDefinition`; client re-binds the
     widgets to the new values.
  5. Each `ActionButton` becomes a `JButton`. Click → confirm dialog
     if `confirmRequired` → `POST .../action/{actionId}`. Server
     returns `ActionResult`; client shows `message` as a toast/dialog.

### Activation gate

- Server-side `PreferencesAdminController` is `@ConditionalOnProperty`-gated
  on `rapla.adminpanels.enabled` (default true) so deployments that don't
  want it can disable.
- Client-side: the menu entry is always present when `User.isAdmin()`,
  but if the server reports zero panels it stays hidden (no point
  showing an empty dialog).

## Plan

### Phase 1 — Wire contract + generic types (rapla-core) — **DONE**

Implemented in `rapla-core/src/main/java/org/rapla/plugin/adminpanels/`:
`PanelScope`, `FieldType` (10 types: BOOL, TEXT, LONG_TEXT, PASSWORD, INT,
SELECT, RADIO_GROUP, DISPLAY_ONLY, ACTION_BUTTON, JSON_EDITOR), `PanelSummary`,
`Field`, `ActionButton`, `ActionResult`, `PanelDefinition`, and the
`PreferencesAdminService` `@HttpExchange("/admin/panels")` interface.
9-test `PreferencesAdminServiceContractTest` pins method shapes, paths, enum
stability. JSON_EDITOR is the universal escape hatch for arbitrary
nested/list data; the server stores in whatever native shape it likes and
translates to/from the wire `Map<String, Object>` per request.

### Phase 2 — Server SPI + dispatcher (rapla-server) — **DONE**

`org.rapla.server.adminpanels.PreferencesPanel` SPI (in rapla-server) plus
`org.rapla.server.spring.web.PreferencesAdminController`
(`@RestController @RequestMapping("/admin/panels")`) injecting
`Set<PreferencesPanel>`. Filters listings by scope; gates SYSTEM-scoped
operations on `User.isAdmin()` (non-admins get an empty list for SYSTEM
scope and `RaplaSecurityException` on direct gets).
`PreferencesAdminControllerIntegrationTest` (rapla-app, MockMvc, 7 tests)
covers non-admin gate, admin listing, get/save/action round-trip with two
stub panels (`stub.system` + `stub.peruser`).

### Phase 3 — Generic Swing renderer (rapla-client) — **DONE**

`org.rapla.client.swing.internal.adminpanels.*`:
- `FieldRenderer` interface + per-type implementations: `BoolFieldRenderer`,
  `TextFieldRenderer`, `LongTextFieldRenderer`, `PasswordFieldRenderer`,
  `IntFieldRenderer`, `SelectFieldRenderer`, `RadioGroupFieldRenderer`,
  `DisplayOnlyFieldRenderer`, `JsonEditorFieldRenderer`.
- `FieldRendererFactory` switches on `FieldType` to instantiate.
- `PanelRenderer` lays out one `PanelDefinition` (title + description +
  GridBag-laid-out fields + action-button row) and exposes
  `collectValues()` for save.
- `ServerDrivenSettingsDialog` (`@Service`) — tree-on-left + form-on-right
  split (modeled on `PreferencesEditUI`); fetches `listPanels(scope)`,
  builds a tree from `PanelSummary.path()`, renders the selected panel
  via `getPanel(id)`, and round-trips through `savePanel` /
  `invokeAction`.
- REST proxy bean `preferencesAdminServiceProxy` added to
  `ClientProxyConfig`.
- `FieldRendererTest` (10 tests) covers each renderer's setValue/getValue
  round-trip. Wire-format invariant: INT widgets always emit `Long`;
  TEXT empty string → `null`; DISPLAY_ONLY never round-trips (server-
  computed only).
- Reuse of classification UI patterns: GridBag label-on-left, BoxLayout
  action row, monospace JTextArea+JScrollPane for JSON_EDITOR (as in
  classification value editors).

### Phase 4 — Legacy bridge + menu wiring (rapla-client) — **DONE**

`ServerDrivenSettingsDialog` now consumes both server panels and the
existing Swing extension points in one tree:
- PER_USER scope merges `Set<UserOptionPanel>` with server PER_USER panels
- SYSTEM scope merges `Set<SystemOptionPanel>` + `Map<String,
  Supplier<PluginOptionPanel>>` with server SYSTEM panels under "Admin"
  / "Plugins" subtrees

Save path branches: server panels save through `api.savePanel(...)`;
legacy panels mutate a shared editable `Preferences` (one
`facade.edit(facade.getPreferences(user))` per dialog session) and
persist with `facade.dispatch(...)` on save.

`RaplaMenuBar` now wires *both* "Edit > Options" and "Admin > Admin
Settings" menu items through `createSettingsAction(scope)` →
`ServerDrivenSettingsDialog.show(...)`. The prior `editController.edit(preferences)`
path is no longer used for these menu items (still in place for entity
edits elsewhere).

Confirmed: `SwingClientStartIntegrationTest` and
`HeadlessClientNameResolutionIntegrationTest` still green after the
bean-graph change.

### Phase 4 — dhbwrapla concrete panels

Three panels in dhbwrapla, each a `@Service implements PreferencesPanel`:

#### 4a. Morada URL panel (display-only)

- `id = "dhbw.morada"`
- One `DISPLAY_ONLY` field showing the configured `rapla.dhbw.morada.url`
- One `ActionButton` "Test connection" → server fetches the URL and
  reports HTTP status / a snippet of the response.

#### 4b. Terminal URL panel (replacement for `TerminalUrlController`)

- `id = "dhbw.terminal"`
- One `TEXT` field "Kurs typ"
- One `ActionButton` "Compute encrypted URL" → server runs
  `urlEncryption.encrypt(...)` and returns the URL as `ActionResult.message`.
- Replaces the HTML page at `/dhbw/terminal/url` (PRD 003 §I).
  `TerminalUrlController` deletes after this lands.

#### 4c. LDAP role-mappings editor

- `id = "dhbw.auth.roleMappings"`
- One `LONG_TEXT` field with the role mappings serialized as JSON or
  newline-delimited records (decide in Phase 4c). Edited live, persisted
  to rapla `Preferences`.
- `DhbwAuthPreferences` already has `RoleMapping` records; the editor
  reads from yaml as defaults and writes overrides into Preferences.
  `DhbwNtlmAuthStore` reads Preferences first, falls back to yaml.
  This restores the runtime-editing capability the deleted
  `DhbwAuthPluginOptionPanel` provided, without a Swing class.

### Phase 7 — dhbw concrete panels (dhbwrapla repo) — **DONE**

Four panels in `~/git/dhbwrapla` under the `DHBW/` tree node:

- `TerminalPreferencesPanel` (`org.rapla.plugin.dhbw.dhbwterminal.server`)
  — id `dhbw.terminal`. TEXT for kursTyp (one-shot, not persisted) +
  DISPLAY_ONLY for encrypted result + 7 DISPLAY_ONLY fields mirroring
  the rest of yaml-driven Terminal config (ueberschrift, keinekurse,
  cssurl, raumTyp, steleUser, eventTypes, resourceTypes) +
  ACTION_BUTTON "Compute encrypted URL" → `urlEncryption.encrypt(...)`.
  No save side-effect (yaml-authoritative for terminal config per
  PRD 003 §C4).
- `MoradaPreferencesPanel` (`org.rapla.dhbw.sync.morada.server`) — id
  `dhbw.morada`. DISPLAY_ONLY URL + trust store + connection status +
  ACTION_BUTTON "Test connection" (HTTP HEAD, 2xx/3xx = OK, captures
  the response code into the connectionStatus field).
- `LdapRoleMappingsPreferencesPanel` (`org.rapla.plugin.dhbw.auth.server`)
  — id `dhbw.auth`. DISPLAY_ONLY for `ldapServer` (yaml) + JSON_EDITOR
  for role mappings as structured records
  `{locationRegex, category, email, exchangeServer}`. Persists via the
  legacy `DhbwAuthPreferences.RoleMapping` "=" / newline-delimited
  shape in `Preferences` so `DhbwNtlmAuthStore` keeps working without
  code change.
- `DualisPreferencesPanel` (`org.rapla.plugin.dhbw.dualisimport.server`)
  — id `dhbw.dualis`. DISPLAY_ONLY url/username/driver/password-status
  (password value never echoed) + ACTION_BUTTON "Test connection" that
  borrows a JDBC connection from the Hikari pool and calls
  `Connection.isValid(5)`.

Discovery uses `DhbwRaplaApplication`'s existing
`@SpringBootApplication(scanBasePackages = {"org.rapla.dhbw", "org.rapla.plugin.dhbw"})` —
no extra config needed.

**Tests** (`dhbwrapla/src/test/java/org/rapla/dhbw/adminpanels/DhbwPanelsIntegrationTest`,
5 tests): unit-level coverage of the action-button paths
(Morada test-connection cleanly reports unreachable host; Terminal
encryption action seeds the DISPLAY_ONLY field; blank input rejected)
+ panel-definition shape (yaml fields exposed correctly). LDAP
read/save round-trip is **not** unit-tested here — building a
sufficient `RaplaFacade` stub is impractical and the cleanest path is
a `@SpringBootTest` once the pre-existing dhbwrapla bean-graph issues
are sorted.

**Pre-existing dhbwrapla bean-graph fix (2026-05-11):** seven dhbwrapla
classes were annotated `@jakarta.inject.Singleton` + `@Inject` only —
Spring doesn't treat `@Singleton` as a stereotype, so the beans weren't
registered. The chain was `DualisSyncJobStarter` (`@Component` +
`@Scheduled`) → `DualisImportJob` → `Dualis` impl → `RaplaImportMailSender`
→ `MailToUserImpl` etc., with `MoradaImport` → `XmlConverter` on the
parallel side, and `DhbwNtlmAuthStore` → `DhbwAuthPreferences.AuthPreferencesReader`.
Fix: added `@Service` to all seven (`XmlConverter`, `JsonConverter`,
`RaplaImportMailSender`, `MoradaImportJob`, `MoradaLocationMapping`,
`DualisImportJob`, `AuthPreferencesReader`) plus
`@Service @Scope("prototype")` on the two `Provider<>`-injected mappings
(`DualisRaplaMapping`, `MoradaRaplaMapping`). Also added a test-scope
HSQLDB dep so Hikari can bind a driver for the secondary Dualis
DataSource in `@SpringBootTest`. Net result: dhbw deployment now boots
without `NoSuchBeanDefinitionException`, and the full Spring stack is
testable.

`DhbwPanelsSpringIntegrationTest` is the result — 3 tests covering
`listSystemIncludesAllDhbwPanels` / `ldapRoleMappingsRoundTripPersistsLegacyFormat`
/ `terminalEncryptionActionUpdatesDisplayField` end-to-end through the
live bean graph. Companion `DhbwPanelsIntegrationTest` (no Spring,
5 tests) keeps the action-path + yaml-mirror coverage that doesn't need
a context.

The `maven-compiler-plugin.testExcludes` block in `dhbwrapla/pom.xml`
still excludes stale legacy tests (`RestDHBWAPIExample`, `DhbwNtlmTest`,
etc.) that referenced Jackson 2 / `javax.inject` / removed
`org.rapla.test.util` — those are unrelated to the bean-graph issue
and out of scope here; can be revisited in a focused test-cleanup pass.

### Phase 8 — Cleanup — **PARTIAL**

- ✅ Deleted `dhbwterminal/server/web/TerminalUrlController.java` and
  the now-empty `web/` directory. `TerminalPreferencesPanel` covers
  the same use-case in the unified admin tree.
- ⏸ `JNDIConfigController` / `MailConfigController` /
  `ArchiverController` (rapla-server) are NOT deleted: they're JSON
  REST endpoints consumed by the legacy Swing
  `JNDIOption`/`MailOption`/`ArchiverOption` panels which still ship via
  the legacy bridge (Phase 4) until those plugins migrate. Delete them
  in the follow-up that ports those three plugins.

### Cleanup follow-ups

- Update PRD 003 §I to point at PRD 020 ("Terminal URL admin page" is
  no longer a one-off; it's a panel under the generic admin tree.)
- After the remaining 6 vanilla panel migrations land
  (Export2iCal/ExchangeConnector/Mail/Archiver/EventTimeCalc/JNDI), the
  legacy `OptionPanel` extension points and `PreferencesEditUI` can be
  removed — at that point `ServerDrivenSettingsDialog`'s legacy bridge
  (Phase 4 LegacyEntry handling) can also be deleted.

### Phase 5 — γ-shape plugin enable gates — **DONE (no migration needed)**

Audit found no `@ConditionalOnProperty`-style runtime-toggle gates to
migrate: vanilla rapla already drives plugin enables through
`Preferences.getEntry(RaplaComponent.PLUGIN_CONFIG)` /
`TypedComponentRole<Boolean>` keys (e.g. `PlanningStatusPlugin.ENABLED`),
which is the γ-shape we wanted. Two boot-time `@Conditional`-style
gates exist (`ExternalEventImportEnabledCondition` + dhbw's
`@ConditionalOnProperty(ExternalEventImportPlugin.ENABLE_PROPERTY)` on
`DualisEventsLoaderImpl`); both are appropriate boot-time gates (avoid
HTTP / scheduler registration when feature off), not user-toggle
settings — leave in place.

### Phase 6 — Migrate vanilla `PluginOptionPanel`s — **5/11 DONE**

Migrated to server-side `@Service` `PreferencesPanel` impls under
`org/rapla/plugin/<name>/server/`:
- `PlanningStatusPreferencesPanel` (BOOL)
- `AppointmentNotePreferencesPanel` (BOOL)
- `CSVExportPreferencesPanel` (BOOL)
- `AutoExportPreferencesPanel` (2× BOOL)
- `TimeslotPreferencesPanel` (JSON_EDITOR over the legacy
  `RaplaConfiguration` "timeslot" tree)

Common base class `org.rapla.server.adminpanels.AbstractPluginPreferencesPanel`
handles SYSTEM scope + the read/clone-edit/dispatch lifecycle on
system preferences. Legacy Swing classes deleted for the five
migrated plugins (their server counterparts replace them in the
unified tree; no duplicates).

Spring discovery: new config class `AdminPanelsScanConfig` (imported
from `RaplaServerAutoConfiguration`) does a targeted
`@ComponentScan` over `org.rapla.server.adminpanels` +
`org.rapla.plugin.*.server` panel packages — keeps the rest of the
server tier on the explicit-`@Bean`-factory pattern (AGENTS.md §4)
while letting these new panels register via `@Service`.

`VanillaPluginPanelsIntegrationTest` (rapla-app, MockMvc) verifies
list-includes-all-five and round-trips PlanningStatus + Timeslot
through the live Spring stack including persistence side-effects.

**Deferred to follow-up sessions** (heavyweight panels, kept on the
legacy bridge for now): `Export2iCalAdminOption`,
`ExchangeConnectorAdminOptions`, `MailOption`, `ArchiverOption`,
`EventTimeCalculatorAdminOption`, `JNDIOption`. `TableviewOption`
remains permanently excluded per direction — separate root tree if/when
needed (ref. dialog with user 2026-05-10).

## Tests

| Phase | Test |
|---|---|
| 1 | `PreferencesAdminServiceContractTest` (rapla-core): @HttpExchange paths, method shapes, FieldType enum names stable. |
| 2 | `PreferencesAdminControllerTest` (rapla-app, MockMvc): non-admin → 401; admin GET / list/get/save/action with a stub PreferencesPanel registered as @Bean. |
| 3 | `AdminPanelsDialogTest` (rapla-client headless): every FieldType renders without throwing; round-trip a save through a stub service. |
| 4 | dhbwrapla MockMvc tests for each of the three concrete panels. The role-mappings panel needs a "save then re-read" round-trip to confirm Preferences persistence. |

## Risks

1. **Field-type expressiveness.** The fixed `FieldType` enum will eventually
   not cover some legacy `PluginOptionPanel` UI — calendar previews,
   inline tables, custom widgets. Mitigation: design `FieldType` as
   extensible; deployments that need a one-off custom widget should
   surface the requirement and we add a new enum value rather than
   shipping an "embedded Swing" escape hatch (which would reintroduce
   the custom-Swing-jar problem).
2. **yaml vs Preferences value sources.** We say "yaml is defaults,
   Preferences are runtime overrides." Need a clean read-fallback path
   on the server side so consumers (`DhbwNtlmAuthStore` etc.) read the
   union without each one knowing the policy. Suggest a thin
   `PreferenceWithDefault` helper that takes both sources.
3. **Discovery / ordering.** With many panels, a flat tab list gets
   unwieldy. We can punt with a simple sort-by-id for v1 and add
   grouping later. The wire contract leaves room — `PanelSummary`
   can grow a `category` field without breaking clients that ignore it.
4. **i18n.** Same pattern as the externaleventimport wizard: server
   builds labels in the user's locale (via `LocaleContextHolder`),
   client renders verbatim. No client-side bundle for panel content.
5. **Versioning.** If a server adds a new `FieldType` enum value but
   the client is older, the client must fail gracefully (skip the
   field with a "(unsupported field type X)" placeholder rather than
   crashing the whole panel). Test for this in Phase 3.

## Open Questions

1. **Menu entry name and location.** "Plugin Settings" (matches the
   legacy `PluginOptionPanel` naming), "Server Configuration", or
   "Admin Panels"? Currently leaning "Plugin Settings" for continuity.
2. **Tabs vs tree navigation.** With 1–5 panels, tabs are fine; with
   20+, a tree on the left is nicer. Punt: ship tabs, switch to tree
   if/when count grows.
3. **`SELECT` typeConfig shape.** `List<{value, label}>` is obvious for
   static enums. For dynamic options (e.g. "list all DynamicType keys")
   we either inline the list at definition time or add a server
   "fetch options" endpoint. Inline is simpler; server cost is low for
   typical option lists. Lean inline.
4. **Action results that update fields.** When an action's
   `updatedValues` is non-null, the client rebinds the visible widgets
   to the new values. Should this be the default mechanism for
   "compute X from current values" actions, or is a separate
   "computeOnly" field type cleaner? Open.
5. **Concurrent edits.** If two admins edit the same panel
   simultaneously, last-write-wins. Need a `lastModifiedTimestamp` on
   the panel definition to detect mid-air collisions? Probably out of
   scope for v1.
6. **Action authorization.** Some actions might require non-admin (e.g.
   "test my own LDAP login"). Add a per-action `adminOnly` flag like
   on `PanelSummary`? Punt for now; admin-only at panel level.

## Cross-references

| PRD | Relationship |
|---|---|
| **003** custom deployments | This PRD's mechanism is what §I should have been. PRD 003 §I gets superseded by PRD 020 Phase 4b. |
| **012** dhbwrapla client carve-out | The wire-contract pattern (server metadata → generic client renderer) directly mirrors PRD 012 §B1 (`ExternalEventImportMetadata`). Where 012 made the *import wizard* generic, 020 makes the *admin/preferences UI* generic. |
| **019** Spring Boot lifecycle | Server-side `PreferencesPanel` beans benefit from PRD 019's storage-up ordering: any panel injecting `RaplaFacade` gets a connected one before its `@PostConstruct`, so panel definitions can read live Preferences without `@DependsOn`. |
| **AGENTS.md §11** | This PRD's deletion of `TerminalUrlController` (Phase 5) is a planned, not-to-fix-compile deletion — the function moves upstream to the generic mechanism. |
