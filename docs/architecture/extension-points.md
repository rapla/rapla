# Extension points and plugins

Rapla is "extension-point heavy." Calendar views, export formats,
import wizards, menu entries, save-time validation, attribute
formula functions, authentication stores, and HTML page renderers
are all pluggable. Today the wiring is **Spring DI**; the legacy
custom-injection annotations (`@DefaultImplementation`, `@Extension`,
`@ExtensionPoint`) are no longer used at runtime — references in
older docs / commit messages refer to the historical scheme.

This page covers:

- How extension wiring works in Rapla today
- The catalog of extension-point interfaces
- The list of bundled plugins, what they do, and how they hook in
- A concrete trace of one plugin (the iCal export) end to end

For the runtime split between the two Spring contexts (client uses
component scan; server uses explicit `@Bean` factories), see
[overview.md](overview.md) and `AGENTS.md` §4.

---

## How wiring works (today)

### Client side

`rapla-client/src/main/java/org/rapla/client/spring/SwingClientConfig.java`
defines an `AnnotationConfigApplicationContext` with a component scan
of `org.rapla.client.*` and `org.rapla.plugin.*`. Anything in those
packages annotated with `@Service`, `@Component`, `@Repository`, or
`@Configuration` is picked up automatically.

For "give me every implementation of interface X" (the extension
mechanism), Spring auto-injects a `Set<X>` (or `Map<String, X>`) into
constructors that ask for one. Example:

```java
@Service
public class ReservationControllerImpl
{
    private final Set<EventCheck> eventCheckers;

    public ReservationControllerImpl(Set<EventCheck> eventCheckers, ...) {
        this.eventCheckers = eventCheckers;
    }
}
```

Drop a `@Service public class MyCheck implements EventCheck` under
the scan path and it's wired in. No registry. No `services` file.
Just the annotation.

For `Map<String, X>` consumers, the bean's name (default = decapitalized
class name, or whatever's in `@Service("name")`) becomes the map key.
This is the pattern for "named" extensions — table column factories,
view types, plugin option panels.

### Server side

`rapla-server` and `rapla-app` go via `@SpringBootApplication`
(in the `org.rapla.server.spring` package), which only scans **its own
package**. Server internals (`org.rapla.server.internal.*`,
`org.rapla.plugin.*.server.*`) are wired manually in `@Bean` factory
methods inside:

- `rapla-server/src/main/java/org/rapla/server/spring/ServerCoreConfig.java`
  — core server beans (storage operator, JWT, `HTMLViewPage`s).
- `rapla-server/src/main/java/org/rapla/server/spring/ServerServiceConfig.java`
  — plugin wiring, gated by `@ConditionalOnProperty(prefix="rapla.services", name="org.rapla.plugin.<id>", matchIfMissing=true|false)`.

A typical pattern:

```java
@Configuration
public class ServerServiceConfig {
    @Bean
    @ConditionalOnProperty(
        prefix = "rapla.services",
        name = "org.rapla.plugin.export2ical",
        matchIfMissing = true)
    public Export2iCalServlet export2iCalServlet(...) {
        return new Export2iCalServlet(...);
    }
}
```

`matchIfMissing=true` means "on by default; turn off with
`rapla.services.org.rapla.plugin.export2ical=false`."
`matchIfMissing=false` means "off by default; opt in."

> **Footgun.** Adding `@Service` to a class under `org.rapla.server.internal.*`
> does **nothing** — it's outside the Boot scan. You have to add a
> `@Bean` method in `ServerCoreConfig` or `ServerServiceConfig`. AGENTS.md §4
> spells out why and where the eventual server-side `@ComponentScan`
> migration would have to happen.

### Properties / preferences gating

Plugin enable / disable lives in two places:

1. **Server-side static gate** — `rapla.services.org.rapla.plugin.<id>`
   in `application.yml` or via env var. Controls whether the bean is
   created at all.
2. **Per-user preferences** — a `PluginOptionPanel` writes into the
   user's `Preferences` (entity-based config). These flags decide
   per-user whether a feature is visible / enabled in the UI. Beans
   still exist server-side regardless.

## The interface surface

Extension-point interfaces are plain Java interfaces in two
well-known packages. They typically declare a `String ID` constant
(used as the bean name when registered) and a single method.

### Client extension points (`org.rapla.client.extensionpoints`)

`rapla-client/src/main/java/org/rapla/client/extensionpoints/`:

| Interface | Purpose |
|---|---|
| `RaplaMenuExtension` | Base for menu entries (`getId()`, `getMenuId()`). |
| `EditMenuExtension` | Adds entries to the **Edit** menu. |
| `ViewMenuExtension` | Adds entries to **View**. |
| `ExportMenuExtension` | Adds entries to **File → Export**. |
| `ImportMenuExtension` | Adds entries to **File → Import**. |
| `AdminMenuExtension` | Adds entries to **Admin**. |
| `HelpMenuExtension` | Adds entries to **Extra / Help**. |
| `ObjectMenuFactory` | Adds entries to right-click context menus on a `RaplaObject`. |
| `ReservationWizardExtension` | Adds steps to the new-reservation wizard. |
| `AppointmentStatusFactory` | Adds the status-line footer in the appointment editor. |
| `AppointmentEditExtensionFactory` | Adds custom fields to the appointment editor. |
| `EventCheck` | Validation step before a reservation save. |
| `PluginOptionPanel` | Per-plugin preferences UI. |
| `SystemOptionPanel` | System-wide preferences UI. |
| `UserOptionPanel` | Per-user preferences UI. |
| `AnnotationEdit`, `AnnotationEditAttributeExtension`, `AnnotationEditTypeExtension`, `AnnotationEditCategoryExtension` | Custom UIs for editing annotations on attributes / dynamic types / categories. |
| `PublishExtensionFactory` | Custom publish / share targets in the publish dialog. |
| `ClientExtension` | Hook called once after the client logs in (use sparingly — it's a "startup hook" sink). |

Swing-specific (interfaces that take Swing components):

`rapla-client/src/main/java/org/rapla/client/swing/extensionpoints/`:

| Interface | Purpose |
|---|---|
| `SwingViewFactory` | Adds a calendar view type (week / month / table / custom). Returns a `JComponent` and a "view id" used in `CalendarSelectionModel`. |

### Server extension points (`org.rapla.server.extensionpoints`)

`rapla-server/src/main/java/org/rapla/server/extensionpoints/`:

| Interface | Purpose |
|---|---|
| `HTMLViewPage` | Renders a calendar view as HTML for the read-only web preview. One bean per view type (day, week, month, week_compact, day_resource, day_timeslot, week_timeslot, table, …). |
| `HtmlMainMenu` | Adds entries to the index page menu (the public landing for the HTML preview). |
| `ServletRequestPreprocessor` | Intercepts incoming HTTP requests before the controller runs. Used by `urlencryption` to decrypt encrypted-token URLs. |

### Plugin-internal extension points

A few plugins declare their own extension surface for cross-plugin
participation:

- `org.rapla.plugin.tableview.extensionpoints.TableColumnDefinitionExtension`
  — let other plugins add columns to the table view.
- `org.rapla.plugin.tableview.client.swing.extensionpoints.SummaryExtension`
  — let other plugins add summary-row rendering.
- `org.rapla.plugin.exchangeconnector.extensionpoints.ExchangeConfigExtensionPoint`
  — Exchange URL / user mapping customisation.

### Cross-cutting extension points (`org.rapla.entities.extensionpoints`)

`rapla-core/src/main/java/org/rapla/entities/extensionpoints/`:

| Interface | Purpose |
|---|---|
| `FunctionFactory` | Provide custom functions for `ParsedText` formula evaluation (used by `nameformat`, conditional annotations, etc.). |

## The bundled plugin catalog

All plugins live under `rapla-core/src/main/java/org/rapla/plugin/`
(interface side), with implementations in `rapla-client/.../plugin/<id>/...`
and `rapla-server/.../plugin/<id>/...`.

| Plugin | Side | What it adds | Hooks |
|---|---|---|---|
| `abstractcalendar` | shared | Base classes for calendar rendering (`RaplaBuilder`, `RaplaBlock`). Not a plugin in the user-facing sense. | — |
| `weekview` | both | Week / day calendar views. | `SwingViewFactory` (client), `HTMLViewPage` `day`, `week` (server) |
| `monthview` | both | Month grid view. | `SwingViewFactory` + `HTMLViewPage` `month` |
| `compactweekview` | both | Compact week view. | `HTMLViewPage` `week_compact` |
| `dayresource` | both | Day grouped by resource. | `HTMLViewPage` `day_resource` |
| `timeslot` | both | Day / week views aligned to admin-defined timeslots. | `HTMLViewPage` `day_timeslot`, `week_timeslot` |
| `tableview` | both | Tabular reservation / appointment views. Defines its own extension points (`TableColumnDefinitionExtension`, `SummaryExtension`). | `SwingViewFactory`, `HTMLViewPage`, `PluginOptionPanel` |
| `autoexport` | server | Pre-renders HTML calendars for public exposure. | `HtmlMainMenu` |
| `csvexport` | client | CSV export of events. | `ExportMenuExtension` |
| `export2ical` | both | iCal (RFC 5545) export. | `ExportMenuExtension` (client), REST `/ical` + `/internal_ical` (server), `UserOptionPanel`, `SystemOptionPanel` |
| `ical` | both | iCal **import**. | `ImportMenuExtension` (client), REST endpoint (server) |
| `externaleventimport` | both | Pull events from external feeds (CalDAV, Google, …). | Wizard + service |
| `eventimport` | both | Bulk import via templates ([details](event-template-import.md)). | `ReservationWizardExtension`, server `RaplaTemplateImport` |
| `tempatewizard` | client | "Create from template" wizard. | `ReservationWizardExtension` |
| `periodcopy` | client | Copy a series of events into a new period. | `ReservationWizardExtension` |
| `setowner` | client | Right-click "Change owner". | `ObjectMenuFactory` |
| `copyurl` | client | Right-click "Copy calendar URL". | `ObjectMenuFactory` |
| `appointmentnote` | both | Appointment notes via dynamic-type annotations. | `FunctionFactory` |
| `eventtimecalculator` | both | Working-time / duration formulas. | `FunctionFactory`, `TableColumnDefinitionExtension`, `AppointmentStatusFactory` |
| `planningstatus` | both | Marks events as planned / unplanned (annotation-driven). | annotation conditions |
| `notification` | server | Scheduled email on allocation changes. | `@Scheduled` task |
| `mail` | server | SMTP plumbing other plugins use. | `MailInterface` bean |
| `archiver` | server | Periodic archival of historical events. | `@Scheduled` task, REST `/archiver` |
| `exchangeconnector` | server | Bidirectional Microsoft Exchange sync. | `SynchronisationManager` (manual wiring), `ExchangeConfigExtensionPoint` |
| `urlencryption` | server | Encrypts iCal subscription URLs (so the URL itself is the secret). | `ServletRequestPreprocessor` |
| `jndi` | server | LDAP/JNDI-based authentication. | `AuthenticationStore` |
| `adminpanels` | both | Server-driven admin panels framework. See [PRD 020](../prd/020-server-driven-admin-panels.md). | — |

Many plugins are **off by default** (`matchIfMissing=false`):
notification, mail, archiver, exchangeconnector, urlencryption, jndi,
ical-import, externaleventimport, eventtimecalculator, planningstatus.
Turn them on by setting the corresponding
`rapla.services.org.rapla.plugin.<id>=true` property.

## Worked example: how the iCal export plugin hooks in

**Goal.** A user clicks **File → Export → Export to iCal**, picks a
filename, gets an `.ics` file containing the visible appointments.

**1. Client menu entry registers itself.**

`rapla-client/src/main/java/org/rapla/plugin/export2ical/client/swing/Export2iCalMenu.java`:

```java
@Service
@Lazy
public class Export2iCalMenu extends RaplaGUIComponent
        implements ExportMenuExtension {

    public Export2iCalMenu(/* facade, i18n, calendarModel, ioInterface, dialogUiFactory */) {
        super(...);
    }

    public String getId()     { return "export_file_text"; }
    public JMenuItem getMenuItem() { ... }      // builds the JMenuItem
    public void actionPerformed(ActionEvent e) {
        // queries calendarModel for visible appointments
        // calls server-side iCal export
        // saves the .ics file via ioInterface.saveFile(...)
    }
}
```

`SwingClientConfig`'s component scan finds this class, the menu
builder asks Spring for `Set<ExportMenuExtension>`, `Export2iCalMenu`
is in the set, the menu shows up.

**2. Server bean registers itself (conditionally).**

`ServerServiceConfig`:

```java
@Bean
@ConditionalOnProperty(
    prefix = "rapla.services",
    name = "org.rapla.plugin.export2ical",
    matchIfMissing = true)
public Export2iCalServlet export2iCalServlet(/* deps */) {
    return new Export2iCalServlet(...);
}
```

**3. REST controller exposes the endpoint.**

`rapla-server/src/main/java/org/rapla/server/spring/web/Export2iCalController.java`:

```java
@RestController
@RequestMapping("/")
@ConditionalOnBean(Export2iCalServlet.class)     // only mount if plugin enabled
public class Export2iCalController {
    @GetMapping({"/ical", "/internal_ical"})
    public void export(...) {
        servlet.generatePage(...);
    }
}
```

**4. Click-to-file flow.**

```
User clicks File → Export → Export to iCal
   ↓
Export2iCalMenu.actionPerformed
   ├── fetches selected appointments from CalendarSelectionModel
   ├── HTTP GET /ical?ids=...&start=...&end=... (with JWT)
   │     └── Spring Security validates token → resolves User
   │           └── Export2iCalController calls Export2iCalServlet
   │                 └── Export2iCalConverter renders RFC 5545 .ics
   │     ← Response: text/calendar
   └── ioInterface.saveFile(parent, dir, ["ics","ical"], suggestedName, bytes)
         └── OS file picker → file written
```

**Configurability.**

- Server-side off switch: `rapla.services.org.rapla.plugin.export2ical=false`.
- Per-user options (e.g. include exceptions?): a `UserOptionPanel`
  bean writes into `Preferences`.
- System-wide options (e.g. base URL for subscription URLs): a
  `SystemOptionPanel` bean.

## How to add a new extension

The general recipe:

1. Pick (or create) the right interface in
   `org.rapla.client.extensionpoints` or
   `org.rapla.server.extensionpoints`.
2. Implement it as a plain class.
3. Annotate `@Service` (client side) or add a `@Bean` method to
   `ServerServiceConfig` (server side).
4. If the consumer wires `Map<String, T>`, give the bean a name
   matching the expected key (`@Service("my-id")`) or define the
   `String ID` constant on the interface and use it.
5. If it should be feature-flagged, add a `@ConditionalOnProperty`.
6. Restart the server or relaunch the client — there is no hot-reload.

For an `EventCheck` (the conflict-checker pattern), three lines are
enough:

```java
@Service
public class MyEventCheck implements EventCheck {
    public Promise<Boolean> check(Collection<Reservation> rs, PopupContext c) {
        // validate; return ResolvedPromise.value(true) to proceed
    }
}
```

Spring DI will pick it up; `ReservationControllerImpl` will run it
in turn.

## See also

- [overview.md](overview.md) — the client / server Spring split
- [reservation-edit.md](reservation-edit.md) — `EventCheck` in
  context
- [dynamic-types.md](dynamic-types.md) — `FunctionFactory` for
  ParsedText formulas
- [permissions.md](permissions.md) — `AuthenticationStore` slot
- [PRD 020](../prd/020-server-driven-admin-panels.md) — server-driven admin panels (the `adminpanels` plugin)
- AGENTS.md §4 — Spring DI patterns, the client/server scan footgun
