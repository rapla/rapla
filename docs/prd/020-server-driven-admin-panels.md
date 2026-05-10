# PRD 020: Server-Driven Admin / Preferences Panels

**Status:** draft
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

### Phase 1 — Wire contract + generic types (rapla-core)

1. Create `org.rapla.plugin.adminpanels` package: `PreferencesAdminService`,
   `PanelSummary`, `PanelDefinition`, `Field`, `FieldType`, `ActionButton`,
   `ActionResult`.
2. Contract test (`PreferencesAdminServiceContractTest`) pinning paths,
   methods, return types, and field-type enum stability.

### Phase 2 — Server SPI + dispatcher (rapla-server)

1. Create `org.rapla.server.adminpanels.PreferencesPanel` SPI.
2. `PreferencesAdminController` (`@RestController @RequestMapping("/admin/panels")`)
   injecting `Set<PreferencesPanel>`. Super-admin gate.
3. Test: a fixture `PreferencesPanel` registered as `@Bean` in the test
   context, MockMvc verifies list / get / save / action round-trips.

### Phase 3 — Generic Swing renderer (rapla-client)

1. `AdminPanelsDialog` — top-level dialog with tabs per panel.
2. `FieldRenderer` — switch on `FieldType`, returns Swing component +
   value extractor. Reuses existing pickers from the dynamic-type editor
   for typed fields (Category, Allocatable, User, DynamicType).
3. `AdminPanelsMenuEntry` — admin-gated menu entry; opens the dialog;
   `@ConditionalOnProperty("rapla.adminpanels.enabled")`.
4. Test (`AdminPanelsDialogTest`): a stub `PreferencesAdminService`
   returns a curated panel covering every `FieldType`; the dialog is
   rendered headless; values round-trip through save.

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

### Phase 5 — Cleanup

- Delete `TerminalUrlController` and its `/dhbw/terminal/url` HTML page.
  (Already deletable as soon as Phase 4b lands; same functionality moved
  upstream into the generic mechanism.)
- Update PRD 003 §I to point at PRD 020 ("Terminal URL admin page" is
  no longer a one-off; it's a panel under the generic admin tree.)

### Phase 6 (deferred) — Migrate vanilla rapla `PluginOptionPanel`s

Out of scope for this PRD. Once the renderer covers all needed field
types, we'd write a per-plugin port: each existing Swing
`PluginOptionPanel` becomes a server-side `PreferencesPanel`. The
legacy extension-point interface and its discovery wiring can then
delete.

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
