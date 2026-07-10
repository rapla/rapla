# PRD 003: Custom Deployment Model After Spring Migration

**Status:** in-progress — major direction change 2026-05-07; rapla-side autoconfig (Phase A), pom rewrite (D1), config (D3) and `DhbwRaplaApplication` (D3) landed 2026-05-10. Annotation/Date/jcifs migration of dhbwrapla server source still pending under D2.
**Date:** 2026-05-06

## 2026-05-10 Implementation snapshot

Landed this date:
- **Rapla-server auto-configuration** (`RaplaServerAutoConfiguration` + `META-INF/spring/AutoConfiguration.imports`). `RaplaSpringBootApplication` is now a thin `@SpringBootApplication`. Verified by `AutoConfigImportTest` — a third-party `@SpringBootApplication` in a foreign package boots the full server stack via classpath alone. Resolves OQ6.
- **dhbwrapla pom.xml rewrite** — parent=`rapla-bom` (with explicit `${rapla.version}` overrides on each rapla-* dep to defeat the inherited `${project.version}` interpolation), depends on `rapla-server` + `rapla-app`. Replaces system-scope JARs with maven deps (jcifs-ng, unboundid-ldapsdk 6.x, jtds, gson, jakarta.inject-api, jetbrains annotations). Uses Spring Boot's HikariCP for the secondary Dualis DataSource.
- **dhbwrapla obsolete-files purge** — deleted `dhbwrapla-container/`, `lib/`, `src/main/java9/`, `src/main/webapp/`. Deleted the four obsolete Swing option panels (`DhbwAuthPluginOptionPanel`, `TerminalOption`, `MoradaPluginOptionPanel`, `DhbwMergeChecker` + test) and `DhbwResources` (replaced respectively by yaml config, server-rendered admin pages, and metadata-driven labels in [PRD 012](012-dhbwrapla-client-migration.md)'s wizard).
- **`DhbwRaplaApplication`** thin `@SpringBootApplication(scanBasePackages={"org.rapla.dhbw","org.rapla.plugin.dhbw"})`. No `@Import` shim — autoconfig delivers rapla-server.
- **`DhbwProperties`** absorbing all server-level dhbw config (auth, dualis datasource, morada, terminal). Replaces what the deleted Swing option panels used to write into rapla `Preferences`.
- **`DhbwDatasourceConfig`** secondary `@Bean DataSource` for Dualis, qualified.
- **`application.yml`** — pre-wired with `rapla.merge.blocked-sync-attributes=morada_id,dualis_id` (Phase G server-side merge gate), `rapla.externalevents.enabled=true` ([PRD 012](012-dhbwrapla-client-migration.md) external-event-import wizard activation), and `rapla.dhbw.*` placeholders.
- **`TerminalUrlController`** — single small server-rendered HTML admin page (super-admin gated) replacing the URL-display field of the legacy `TerminalOption` Swing panel (the only HTML admin UI we keep; rest is yaml).
- **`PromiseWait` shim** restored at `org.rapla.dhbw.server.PromiseWait` (the original `org.rapla.server.PromiseWait` was deleted from rapla-core during the Spring migration). Delegates to `SynchronizedCompletablePromise.waitFor`.
- **`custom/pom.xml` deleted** from the rapla repo.
- **Annotation migration partly mechanical-sed'd**: `javax.*` → `jakarta.*` imports flipped across all dhbw .java files; `@Extension(...)` → `@Component @Named(...)`; `@DefaultImplementation(...)` → `@Service`; `org.rapla.inject` imports stripped.

Pending — the deeper API-drift work that mechanical sed cannot do (sized at the bottom):
- Date → LocalDateTime / LocalDate migration impact across ~15 dhbw files (`AllocatableExporter`, `CourseExporter`/`2`/`3`, `DualisAPIImpl`, `MoradaRaplaMapping`, etc.) — rapla-core has migrated time signatures (`fillDate`, `cutDate`, `formatTime`, `addDay`, `toRaplaTime`) to LocalDateTime/LocalDate; dhbw call sites still pass legacy `java.util.Date`.
- jcifs → jcifs-ng API change in `NtlmBindRequest` (`Type1Message`/`Type2Message` constructors changed).
- Scheduler API: `CommandScheduler.scheduleAtGivenTime` was removed (PRD 002); `DualisSyncJobStarter` and `MoradaSyncJobStarter` use it. `DualisImportJob` and `MoradaImportJob` no longer satisfy the `Action` interface contract.
- `DualisEventsLoaderImpl` rewrite as `ExternalEventImportService` (Phase E).
- Remaining ~20 server-side `@Service`/`@Component` annotation cleanup (constructor injection, qualifier names).


## 2026-05-07 Direction Change — dhbwrapla becomes server-only

User decision (with PRD 005 multi-module split landing): **all dhbw-specific Swing/client code moves INTO the rapla repo.** dhbwrapla retains only server-side artefacts (REST controllers, server-side beans, configuration, scheduled jobs, auth integration).

**Consequences:**

1. **Single-signing pass.** Only `rapla-app` signs `webclient/*.jar`. dhbwrapla build produces no client JAR, stages no `webclient/`, runs no jarsigner.
2. **No `rapla-client-api` extraction.** Customer Swing extensions ship inside the canonical `rapla-client` JAR — the split (deferred per PRD [004](done/004-multi-module-architecture-analysis.md)/005) is permanently off the table.
3. **dhbw Swing code → `org.rapla.plugin.dhbw.*`** in rapla-client / rapla-server / rapla-core (mirrors stock-plugin layout post-PRD-005).
4. **dhbw plugins ship by default.** Use `@ConditionalOnProperty(prefix="rapla.plugins", name="org.rapla.plugin.dhbw.<id>", matchIfMissing=false)` per-plugin for opt-in.
5. **dhbwrapla pom** depends only on `rapla-server` (transitively `rapla-core`); not `rapla-client`. dhbwrapla is its own `@SpringBootApplication` fat JAR that re-exports rapla-app's `webclient/` via Spring Boot `META-INF/resources`.
6. **Branding / config overrides** — dhbwrapla still owns its `application.yml`, custom auth beans, scheduled jobs, REST endpoints. DI-registration shape unchanged.

**Dropped:** §"Client-Side Customizations (Swing)" and §"JNLP Client and Code Signing" dual-signing chain — single-signing in `rapla-app/pom.xml`; dhbwrapla pom has zero signing config. **Kept:** §"Custom Application Main Class" — dhbwrapla has its own `@SpringBootApplication` for branding/scan/endpoints.

The original 2026-05-06 sections below describe the now-superseded "custom-project-with-its-own-client-JAR" plan — Swing/JNLP halves obsolete; server-side halves remain valid.

---

## Goal

Define how custom Rapla deployments (e.g., `dhbwrapla`) integrate with the platform after the Spring Boot migration (PRD 001) removes `restinject`, `@Extension`/`@DefaultImplementation` annotations, `ServiceInfLoader`, `SimpleRaplaInjector`, and the WAR-overlay build mechanism. The new model must let downstream projects register custom beans, override defaults, hook into extension points, schedule background jobs, add REST endpoints, and contribute Swing UI — all using standard Spring DI and Spring Boot conventions.

**As of 2026-05-07:** "contribute Swing UI" means *contribute Swing code to rapla-client*, not *bundle a separate signed Swing JAR*. See top-of-PRD direction change.

## Current Custom Deployment Mechanism (Pre-Migration)

**Build:** Custom project parents from `org.rapla:custom:2.1-SNAPSHOT` (`../rapla/custom/pom.xml`) — a `pom`-packaging parent that provides rapla core JAR + test JAR + WAR as `provided`. The `parent-process` profile (active when `src/` exists) runs `maven-war-plugin` overlay, copies client JARs into `webclient/`, signs with PKCS#11/YubiKey, generates `clientlibs.properties` via Ant. The `moduleDescription` resource is Maven-filtered to `${project.groupId}.${project.artifactId}`.

**DI Registration:** Custom classes use `@Extension(provides=…, id="…")` or `@DefaultImplementation(of=…, context=InjectionContext.server)`. `restinject`'s `AnnotationInjectionProcessor` generates `META-INF/org.rapla.servicelist` + `META-INF/services/<iface>` at compile time; `ServiceInfLoader` merges registrations at runtime; `SimpleRaplaInjector` instantiates via `@javax.inject.Inject` constructors.

### Extension Points Used by dhbwrapla

| Extension Point | Custom Implementation | Context |
|-----------------|----------------------|---------|
| `AuthenticationStore` | `DhbwNtlmAuthStore` | server |
| `PermissionExtension` | `DhbwRaplaRightsPlugin` | server |
| `MergeCheckExtension` | `DhbwMergeChecker` | client |
| `ServerExtension` | `DualisSyncJobStarter`, `MoradaSyncJobStarter` | server |
| `ExchangeConfigExtensionPoint` | `DhbwExchangeExtension` | server |
| `PluginOptionPanel` | `DhbwAuthPluginOptionPanel`, `MoradaPluginOptionPanel`, `TerminalOption` | client |
| `ReservationWizardExtension` | `DualisImportWizard` | client |
| `ReservationToolbarExtension` | `DhbwSyncButtonExtension` | client |
| `I18nBundle` | `DhbwResources` | both |
| JAX-RS `@Path` endpoints | `RaplaPruefungen`, `SteleExportPageGenerator`, `DualisEventsLoaderImpl`, `ImportController`, etc. | server |
| `PrePostDispatchProcessor` | `DualisImportEventsPrePostDispatchProcessor` | server |

### Configuration

- LDAP/Morada settings stored as Rapla `Preferences` (per-user or system-level) via `PluginOptionPanel` UI
- Dualis DB connection via JNDI `java:comp/env/jdbc/dualisdb`
- Morada HTTPS URL stored in preferences
- DHBW-specific dynamic types (Room, Building, Person, Course, Exam, etc.) with `MoradaId`/`DualisId` marker attributes

## Proposed Architecture (Post-Migration)

### Build Model: Spring Boot Overlay → Maven Module + Component Scan

Custom project is a standard Maven module depending on `org.rapla:rapla` (the JAR, not the fat JAR): parent `rapla-parent:2.1-SNAPSHOT` (or plain dep), `src/main/java/` for custom `@Component`s, `application.yml` overrides, own `@SpringBootApplication` main class that `@ComponentScan`s both `org.rapla` and `org.rapla.plugin.dhbw`, `spring-boot-maven-plugin` produces the fat JAR.

Spring Boot's `@ComponentScan` replaces `ServiceInfLoader`: rapla-core + custom beans share one Spring context; custom `@Component`s with the same interface are picked up in `List<T>` / `Map<String, T>` injections automatically.

### DI Registration: @Extension → @Component

| Before | After |
|--------|-------|
| `@Extension(provides = AuthenticationStore.class)` | `@Component` implementing `AuthenticationStore` — Spring auto-discovers via `@ComponentScan` |
| `@Extension(provides = ServerExtension.class, id = "org.rapla.dhbw.interface.dualis")` | `@Component @Named("org.rapla.dhbw.interface.dualis")` implementing `ServerExtension` |
| `@Extension(provides = PluginOptionPanel.class, id = "dhbw-auth")` | `@Component @Named("dhbw-auth")` implementing `PluginOptionPanel` |
| `@DefaultImplementation(of = SomeInterface.class, context = server)` | `@Service @Profile("server")` implementing `SomeInterface` |
| `@Extension(provides = I18nBundle.class)` | `@Component` implementing `I18nBundle` |
| `META-INF/org.rapla.servicelist` generation | Deleted — Spring `@ComponentScan` replaces |
| `META-INF/services/*` generation | Deleted — Spring `@ComponentScan` replaces |

**Named extensions in collections:** Spring naturally collects all beans implementing an interface into `List<T>` (ordered) and `Map<String, T>` (keyed by bean name). Extension points that currently use `@Named("id")` continue to work — Spring 6 honours `jakarta.inject.Named` as a qualifier. Beans registered with `@Component @Named("org.rapla.dhbw.interface.dualis")` appear in `Map<String, ServerExtension>` under that key.

**Plugin enable/disable:** `@ConditionalOnProperty(prefix="rapla.services", name="org.rapla.dhbw.interface.dualis", matchIfMissing=true)` on each custom `@Component` — same mechanism already used by rapla-core plugins (PRD 001 Phase 6 step 3).

### Authentication Customization

**Recommendation: `@Component` implementing `AuthenticationStore`** (Option 1 — mechanical port):

```java
@Component
@Named(DhbwNtlmAuthStore.ID)   // "org.rapla.dhbw.server.auth"
@ConditionalOnProperty(prefix = "rapla.services", name = DhbwNtlmAuthStore.ID, matchIfMissing = false)
public class DhbwNtlmAuthStore implements AuthenticationStore {
    // constructor injection for DhbwAuthPreferences, Logger
    // same LDAP/NTLM logic — delete the existing isEnabled() method
}
```

`Set<AuthenticationStore>` injection in `ServerServiceConfig` picks it up. Enable via `rapla.services.org.rapla.dhbw.server.auth=true`. Use the existing `DhbwNtlmAuthStore.ID` constant (also keys preferences, plugin gates). `@ConditionalOnProperty` replaces the existing `isEnabled()` method (which calls `serverContainerContext.isServiceEnabled(ID)`) — delete both the field-injected `ServerContainerContext` and the method (see Risk 8).

**Option 2 (follow-up):** wrap LDAP/NTLM in a Spring Security `AuthenticationProvider` registered in `SecurityConfig` for full Spring-native auth flow.

### Scheduled Background Jobs

> **Superseded 2026-05-10** — `CommandScheduler.scheduleAtGivenTime` removed during Spring Boot migration with no Spring-native replacement. **[PRD 019](done/019-spring-boot-lifecycle-migration.md) (Spring Boot Lifecycle Migration) is canonical** — migrate to `@Scheduled` (cron) + `@EventListener(ApplicationReadyEvent.class)` (one-shot startup) + delete `ServerExtension`.

(Pre-supersede plan was a `@Component` `ServerExtension` impl wrapping `CommandScheduler` calls; kept here for historical context only.)

### REST Endpoints

JAX-RS `@Path`/`@GET`/`@POST` → Spring `@RestController` via `@ComponentScan`.

| Current Class | Migration |
|---|---|
| `RaplaPruefungen` (`@Path("pruefungen")`) | `@RestController @RequestMapping("/pruefungen")` |
| `SteleExportPageGenerator` | `@RestController @RequestMapping("/terminal-export")` |
| `DualisEventsLoaderImpl` (`@Path` on interface) | Move `@RequestMapping("/DualisEventLoader")` to impl class (Spring doesn't pick up interface mappings). Or delete the interface — restinject-only. |
| `ImportController` (ICS upload) | `@RestController @RequestMapping("/semesterplan")` |
| Course overview pages | `@RestController` returning HTML strings |

**JAX-RS-isms that don't translate 1:1:**

| JAX-RS | Spring MVC |
|---|---|
| `@Context HttpServletRequest req` | bare `HttpServletRequest req` |
| `@Consumes(MULTIPART_FORM_DATA)` + `@MultipartForm` POJO | `@PostMapping(consumes=…)` + `@RequestParam("file") MultipartFile`. Wrapper POJO disappears. |
| `@Produces(TEXT_PLAIN)` | `@GetMapping(produces=TEXT_PLAIN_VALUE)` |
| Field-injected `@Inject Logger logger;` | constructor-injected `private final Logger logger;` |
| `RemoteSession.checkAndGetUser(req)` | `Authentication` parameter, or `@PreAuthorize` |

HTML-generating endpoints: keep inline for initial migration; plan Angular migration separately.

### Configuration (JNDI → application.yml)

**Current:**
- Dualis DB: JNDI `java:comp/env/jdbc/dualisdb` → `DataSource`
- LDAP server: Rapla `Preferences` (stored in data XML)
- Morada URL: Rapla `Preferences`

**After migration:**

```yaml
rapla:
  dhbw:
    dualis:
      datasource:
        url: jdbc:jtds:sqlserver://dualis-server:1433/dualisdb
        username: rapla
        password: secret
        driver-class-name: net.sourceforge.jtds.jdbc.Driver
    ldap:
      server-url: ldaps://ad.dhbw.de:636
      role-mappings:
        - pattern: ".*\\\\(.+)"
          location: "$1"
          email-domain: "dhbw.de"
    morada:
      url: https://morada.example.com/export
      ssl-truststore: classpath:ssl2.cert
```

**DataSource:** For Dualis, define a secondary `DataSource` bean in the custom project's `@Configuration`:

```java
@Configuration
public class DhbwDatasourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "rapla.dhbw.dualis.datasource")
    public DataSource dualisDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public Dualis dualisService(DataSource dualisDataSource, Logger logger) {
        return new DualisViewLoader(dualisDataSource, logger);
    }
}
```

If the custom project needs multiple `DataSource` beans, Spring Boot's `DataSource` auto-configuration must be excluded (as rapla-core already does) and each datasource defined explicitly.

**Local dev / file datastore.** dhbwrapla currently ships a `data/` directory with `data.xml` (HSQLDB) used by the local Jetty run. After migration, point the rapla file-datasource at it:

```yaml
rapla:
  file-datasources:
    raplafile: ${user.dir}/data/data.xml
```

Same `rapla.file-datasources.raplafile` property already gates `JwtConfig` and `ServerServiceConfig` in PRD 001. Worth a `application-dev.yml` profile so production defaults to a real DB.

### Client-Side Customizations (Swing)

> **Superseded 2026-05-07** — custom Swing code now lives in rapla-client directly; no separate JAR bundled in JNLP.

Pre-direction-change: `@Extension(...) ReservationWizardExtension` → `@Component @Named(DualisImportPlugin.ID)`. Client-side `@ComponentScan` includes `org.rapla.plugin.dhbw`. Custom JAR bundled in JNLP `webclient/` via `maven-dependency-plugin:copy-dependencies`.

### JNLP Client and Code Signing

> **Superseded 2026-05-07** by the direction change (dhbwrapla is server-only). Single-signing in `rapla-app/pom.xml`; dhbwrapla has zero signing config. Section retained below for historical context only.

PRD 001 deletes the WAR but keeps the JNLP launch URL — `RaplaJNLPController` (`@GetMapping("/raplaclient.jnlp")`) serves a descriptor referencing JARs under `/webclient/`. All JARs must be signed (JNLP `<all-permissions/>` aborts on unsigned content) and signed by one consistent identity (mixed signers → SecurityException).

**JAR location strategies:**

| Strategy | How | Trade-off |
|---|---|---|
| **(A) `resources/static/webclient/`** | Copy signed jars into the fat JAR before `spring-boot:repackage`. | Bundles inside fat JAR; cert rotation = rebuild. |
| **(B) External `./webclient/`** | `spring.web.resources.static-locations=file:./webclient/,classpath:/static/`. | Cert rotation = re-run `jarsigner` on deploy host, no rebuild. |

Ship (A) initially; expose `spring.web.resources.static-locations` so operators can switch to (B). `SecurityConfig` already permits `/webclient/**`.

**Pre-direction-change signing chain (both projects sign):** rapla-core's `sign-jks`/`sign-pkcs11` profiles retarget `<archiveDirectory>` from the WAR path to `target/webclient/`, then a `maven-resources-plugin:copy-resources` step moves the signed dir to `src/main/resources/static/webclient/` before repackage. Custom projects re-sign the union (rapla-core jars + custom jar) using the **same** PKCS#11/YubiKey identity (shared `pkcs11.cfg` keeps re-signing idempotent). Switching identities requires stripping `META-INF/*.{SF,RSA,DSA,EC}` first.

`clientlibs.properties` was Ant-generated; replace with a Maven step listing `target/webclient/` contents into `src/main/resources/clientlibs.properties`. No change to `RaplaJNLPPageGenerator` — it already reads from classpath. URLs (`/rapla/raplaclient.jnlp`, `/rapla/webclient/*.jar`) preserved via `server.servlet.context-path=/rapla` (HARD CONSTRAINT — `UrlPreservationTest`).

JNLP file stays server-rendered (embedded `JNLP-INF/APPLICATION.JNLP` mode incompatible with per-request dynamic properties). Runtime: OpenWebStart (Java Web Start removed in JDK 11). `jpackage` native installer migration → Open Question 7.

### Custom Application Main Class

Final shape (per OQ6, landed 2026-05-10): rapla-server ships as `@AutoConfiguration` via `META-INF/spring/AutoConfiguration.imports`. Custom project has exactly one `@SpringBootApplication` and pulls in core automatically:

```java
@SpringBootApplication(scanBasePackages = {"org.rapla.dhbw", "org.rapla.plugin.dhbw"})
@EnableConfigurationProperties({RaplaServerProperties.class, DhbwProperties.class})
public class DhbwRaplaApplication {
    public static void main(String[] args) { SpringApplication.run(DhbwRaplaApplication.class, args); }
}
```

No `@Import(RaplaSpringBootApplication.class)` shim — `@Import(@SpringBootApplication)` is a foot-gun (duplicate scan roots, double-registered auto-configs).

## Scope

### What changes in rapla-core (to support custom deployments)

| Item | File(s) | Detail |
|------|---------|--------|
| Keep `ServerExtension` interface | `org.rapla.server.extensionpoints.ServerExtension` | Retain as a Spring-collectable interface. Do not delete during PRD 001 cleanup. |
| Keep `AuthenticationStore` interface | `org.rapla.server.extensionpoints` | Retain for custom auth implementations. |
| Keep all client extension point interfaces | `org.rapla.client.extensionpoints.*` | `ReservationWizardExtension`, `PluginOptionPanel`, `ReservationToolbarExtension`, etc. — all retained as interfaces collected by Spring. |
| Keep `PermissionExtension`, `MergeCheckExtension` | `org.rapla.entities.extensionpoints` | Retained for custom permission/merge logic. |
| Keep `FunctionFactory` interface | `org.rapla.entities.extensionpoints` | Retained for custom formula functions. |
| Keep `I18nBundle` interface | `org.rapla.components.i18n` | Retained for custom i18n bundles. |
| Make `RaplaSpringBootApplication` importable | `RaplaSpringBootApplication.java` | Ensure `@Import` works from a custom `@SpringBootApplication` — may need to remove or conditionally apply `@SpringBootApplication`'s built-in `@ComponentScan`. Long term, refactor into a `@AutoConfiguration` per Open Question 6. |
| Document `spring.web.resources.static-locations` extension point | `application.yml` | So custom deployments can switch between bundled (strategy A) and external (strategy B) `webclient/` layouts without code changes. See "JNLP Client and Code Signing". |
| Preserve `sign-jks` and `sign-pkcs11` profiles | `rapla/pom.xml` (lines 326–417) | Signed jars are required by JNLP `<all-permissions/>` — vanilla deployments depend on rapla-core signing. Profiles stay; only the `archiveDirectory` retargets from `${project.build.finalName}/webclient` (WAR) to `target/webclient/` (staging dir copied into `static/webclient/` before `spring-boot:repackage`, or published as a separate `rapla-webclient` zip artifact for custom projects). |
| Publish a `rapla-webclient` classifier artifact | `rapla/pom.xml` `maven-assembly-plugin` (or `attach-artifact`) | Zip of the signed `webclient/*.jar` set, attached as `<classifier>webclient</classifier>` so custom projects can declare a Maven dependency on it instead of unpacking the rapla-core fat JAR to find them. Optional but cleaner than extracting from the fat JAR. |
| `custom/pom.xml` cleanup | `custom/pom.xml` | Rewrite as a parent POM for Spring Boot projects: depends on `org.rapla:rapla` JAR (not WAR), removes WAR overlay profile, removes `restinject` annotation processor, adds `spring-boot-maven-plugin`. Or delete entirely if the "separate module with `@Import`" pattern is sufficient. |
| `moduleDescription` mechanism | `src/main/resources-filtered/moduleDescription` | No longer needed after `ServiceInfLoader` removal. Custom projects don't need it. Keep for backward compat during migration, delete in final cleanup. |

### What changes in dhbwrapla (example migration)

| Item | Detail |
|------|--------|
| `pom.xml` | Change parent from `custom/pom.xml` to `rapla-parent`; add `org.rapla:rapla` as dependency; add `spring-boot-maven-plugin`; remove WAR packaging |
| All `@Extension` annotations | Replace with `@Component` (optionally `@Named("id")`) |
| All `@DefaultImplementation` annotations | Replace with `@Service` |
| JAX-RS `@Path`/`@GET`/`@POST` | Replace with Spring `@RestController`/`@GetMapping`/`@PostMapping` |
| JNDI `DataSource` lookup | Replace with Spring `@Bean DataSource` from `application.yml` properties |
| `DhbwRaplaApplication` (new) | Custom `@SpringBootApplication` with `@Import(RaplaSpringBootApplication.class)` |
| `application.yml` (new) | DHBW-specific config: dualis datasource, LDAP URL, Morada URL, plugin toggles |
| `DhbwProperties` (new) | `@ConfigurationProperties` for DHBW-specific settings |
| Client `@Component` classes | Register via `@ComponentScan("org.rapla.plugin.dhbw")` on client side |
| `moduleDescription` resource | Delete (no longer needed) |
| `lib/jtds-1.3.3.jar` | Move to Maven dependency (jTDS is available on Maven Central) |
| `lib/commons-dbcp-1.4.jar`, `lib/commons-pool-1.6.jar` | Move to Maven dependencies or use HikariCP (Spring Boot default) |
| `src/main/webapp/` | Delete entire directory. `web.xml` references the deleted `MainServlet` and JNDI `jdbc/{rapladb,dualisdb}` — there is no servlet bootstrap to preserve. Static content moves to `src/main/resources/static/`. |
| `src/main/java9/module-info.java` | Delete. Currently `requires org.rapla.restinject; requires resteasy.jaxrs; requires javax.inject;` — none of which exist post-migration. Same change rapla-core made in PRD 001 Phase 0. |
| `dhbwrapla-container/pom.xml` | Delete or rewrite. Current aggregator lists `../../rapla/parent`, `../../rapla/custom`, `../../rapla` (WAR), `../../dhbwrapla` — none of those modules exist in the post-migration shape. |
| `org.rapla.parentModules` Maven property | Delete. Used by the WAR-overlay parent; orphaned after `custom/pom.xml` removal. |
| `mariadb-java-client` test dep | Verify still needed; if used only for storage tests, leave as-is. |
| `javax.{inject,servlet,ws.rs,annotation}` imports | Replace with `jakarta.*` throughout. Spring Boot 3.x is jakarta-only. |
| Custom-defined interfaces with `@DefaultImplementation` (`DhbwImportDialog` / `DhbwImportDialogImpl`, `Dualis` / `DualisViewLoader`) | These are *project-internal* interfaces, not rapla-core extension points. Replace `@DefaultImplementation(of = …)` with `@Service` on the impl. If multiple impls exist, mark the default `@Primary`. |
| `@Singleton`-without-`@Extension` classes (`MoradaImport`, `MoradaImportJob`, `RaplaImportMailSender`, `JsonConverter`, `XmlConverter`, `MoradaLocationMapping`, `DhbwAuthPreferences.AuthPreferencesReader`, etc.) | Add `@Component`/`@Service`. The migration is not limited to `@Extension` classes — every Singleton currently picked up by `SimpleRaplaInjector` needs a Spring stereotype. |

## Plan

### Phase C1: Rapla-core extension point preservation (prerequisite)

1. Audit all `@ExtensionPoint` interfaces in rapla-core — ensure none are deleted during PRD 001 Phase 8 cleanup
2. Ensure `ServerExtension`, `AuthenticationStore`, `PermissionExtension`, `MergeCheckExtension`, `FunctionFactory`, `I18nBundle`, and all client extension point interfaces survive the migration
3. Ensure `Set<T>` / `Map<String, T>` injection of these interfaces works in Spring config classes (already done for some — verify completeness)
4. Make `RaplaSpringBootApplication` composable via `@Import` from a custom `@SpringBootApplication` (test this)
5. Write test: a test `@SpringBootApplication` in a test package that `@Import`s `RaplaSpringBootApplication` and contributes a custom `@Component` implementing `ServerExtension`; verify the custom bean appears in the context

### Phase C2: Custom POM and build model

1. Delete the obsolete build artifacts in dhbwrapla:
   - `src/main/java9/module-info.java`
   - `src/main/webapp/` (entire directory, incl. `web.xml`)
   - `dhbwrapla-container/pom.xml` (aggregator no longer makes sense)
   - `lib/` directory (after step 3 moves jars to Maven)
2. Rewrite `custom/pom.xml` or replace it with a documented dependency pattern (see Open Question 2):
   - Parent: `rapla-parent` (or none)
   - Dependency: `org.rapla:rapla` (JAR, not WAR)
   - `spring-boot-maven-plugin` for fat JAR packaging
   - Remove WAR overlay profile, `maven-war-plugin`, `restinject` processor
   - **Keep PKCS#11/YubiKey signing config** but rescope it from `webclient/*.jar` (WAR overlay output) to the custom JAR plus its client-side runtime deps in the staging directory (see "JNLP Client and Code Signing" section)
   - Add `maven-dependency-plugin:copy-dependencies` for client JNLP `webclient/` output
3. Move `lib/jtds-1.3.3.jar`, `lib/commons-dbcp-1.4.jar`, `lib/commons-pool-1.6.jar` to declared Maven dependencies (or HikariCP)
4. Delete the `org.rapla.parentModules` Maven property
5. Write an example `pom.xml` for dhbwrapla using the new model
6. Verify `mvn compile` succeeds for the custom project

### Phase C3: Migrate dhbwrapla annotations

1. Replace all `@Extension(provides = X.class, id = "...")` with `@Component @Named("...")` — preserve the existing string IDs (`org.rapla.dhbw.server.auth`, `org.rapla.dhbw.interface.dualis`, etc.)
2. Replace all `@DefaultImplementation(of = X.class)` with `@Service` (for project-internal interfaces, optionally `@Primary`)
3. Add `@Component`/`@Service` to every `@Singleton`-without-`@Extension` class — the migration is not limited to `@Extension`-annotated classes (see Scope table)
4. Replace `javax.{inject,servlet,ws.rs,annotation}` imports with `jakarta.*`
5. Replace JAX-RS annotations with Spring MVC annotations on all REST endpoints (see JAX-RS-isms table)
6. Add constructor injection to all classes (replace field injection — including the package-private `@Inject` fields in `RaplaPruefungen`, `ImportController`, `DhbwNtlmAuthStore`)
7. Replace `DefaultScheduler` constructor parameters with the `CommandScheduler` interface (`DualisSyncJobStarter`)
8. Add `@ConditionalOnProperty` gates for optional plugins; delete obsolete `isEnabled()` methods that gate via `serverContainerContext.isServiceEnabled(ID)` (see Risk 8)
9. Verify `mvn compile` succeeds

### Phase C4: Migrate dhbwrapla configuration

1. Create `DhbwProperties` `@ConfigurationProperties` class
2. Create `application.yml` with all DHBW-specific config (dualis datasource, LDAP, Morada, dev-mode `rapla.file-datasources.raplafile` for the local `data/data.xml`)
3. Create `DhbwDatasourceConfig` `@Configuration` with secondary `DataSource` bean for Dualis
4. Migrate **server-level** settings from `Preferences` to `DhbwProperties`:
   - `DhbwAuthPreferences.authServer` (LDAP server URL)
   - `DhbwAuthPreferences.RoleMapping` (also consumed by `DhbwExchangeExtension` — both classes need updating in lockstep)
   - Morada server URL
5. Keep **per-user** settings (per-user LDAP overrides) in `Preferences` — see Open Question 4
6. Replace JNDI lookups with Spring-injected beans
7. Move `lib/` JARs to proper Maven dependencies (already covered in C2 step 3)

### Phase C5: Create custom main class and test

1. Create `DhbwRaplaApplication` `@SpringBootApplication` with `@Import(RaplaSpringBootApplication.class)`
2. Create `DhbwRaplaApplicationTest` `@SpringBootTest` verifying all custom beans resolve
3. Create integration tests for custom REST endpoints
4. Create integration test for LDAP auth (against a test LDAP or mock)
5. Verify full `mvn test` passes for both rapla-core and dhbwrapla

### Phase C6: Client-side migration

1. Ensure client-side `@ComponentScan` includes `org.rapla.plugin.dhbw` packages
2. Migrate client UI extensions (`DualisImportWizard`, `DhbwSyncButtonExtension`, `PluginOptionPanel` implementations) to `@Component`
3. Verify Swing client boots with custom components visible
4. Update JNLP packaging to include custom project JAR

### Phase C7: Cleanup

1. Delete `custom/pom.xml` (the rewrite from C2 either replaced it with a documented dependency pattern or made it a thin parent — either way, this is the final removal pass; resolves the C2/C7 redundancy)
2. Delete `moduleDescription` mechanism from rapla-core (resolves Open Question 1)
3. Delete `ServiceInfLoader` references (if any remain)
4. Remove `lib/` directory from dhbwrapla (verified done by C2 step 3)
5. Migrate from the C5 `@Import(RaplaSpringBootApplication)` shim to a proper Spring Boot auto-configuration in rapla-core (Open Question 6)
6. Update documentation for custom deployment projects

## Tests

| Phase | Test | When |
|-------|------|------|
| C1 | `RaplaSpringBootApplication` composable via `@Import` + custom `@Component` appears in context | After C1 |
| C1 | `Set<ServerExtension>` injection includes both core and custom beans | After C1 |
| C1 | `Set<AuthenticationStore>` injection includes custom auth store when enabled | After C1 |
| C2 | Custom project `mvn compile` succeeds with new POM structure | After C2 |
| C2 | Verify `dhbwrapla-container/`, `webapp/`, `java9/module-info.java`, `lib/` all gone | After C2 |
| C3 | All custom `@Component` classes compile and resolve in a test Spring context | After C3 |
| C3 | No remaining `javax.{inject,servlet,ws.rs,annotation}` imports in dhbwrapla — `grep -r 'import javax\\.' src/main/java` returns empty | After C3 |
| C4 | Dualis `DataSource` bean created from `application.yml` properties | After C4 |
| C4 | LDAP config loaded from `DhbwProperties` | After C4 |
| C5 | `DhbwRaplaApplication` boots with all custom + core beans | After C5 |
| C5 | Custom REST endpoints respond via MockMvc | After C5 |
| C5 | Auth flow with custom `AuthenticationStore` issues JWT | After C5 |
| C6 | Swing client context includes custom wizard/toolbar/option panel | After C6 |
| C6 | Signed `webclient/` directory verifies with `jarsigner -verify` for every JAR | After C6 |
| C6 | Generated `/raplaclient.jnlp` lists every signed jar with the correct codebase | After C6 |
| C7 | Full `mvn test` passes for both projects | After C7 |

**Forward reference into PRD 001:** the C1 tests (`RaplaSpringBootApplication` composable via `@Import`, `Set<ServerExtension>` includes external beans) belong in *rapla-core's* test suite, not just dhbwrapla's. Add them to PRD 001's test inventory before Phase 8 cleanup so the prerequisite isn't lost when extension-point pruning starts.

## Extension Point Preservation Checklist

These interfaces must survive the PRD 001 migration and remain Spring-collectable:

### Server Extension Points
- [ ] `ServerExtension` — background services started at server boot
- [ ] `AuthenticationStore` — custom authentication backends
- [ ] `ServletRequestPreprocessor` — HTTP request interceptors
- [ ] `PermissionExtension` — custom permission logic
- [ ] `HTMLViewPage` — custom HTML page generators
- [ ] `FunctionFactory` — custom formula functions
- [ ] `PrePostDispatchProcessor` — dispatch event interception (server-side; used by `DualisImportEventsPrePostDispatchProcessor`)

### Client Extension Points
- [ ] `ReservationWizardExtension` — reservation creation wizards
- [ ] `ReservationToolbarExtension` — toolbar buttons in reservation editor
- [ ] `PluginOptionPanel` — plugin configuration UI
- [ ] `ObjectMenuFactory` — context menus
- [ ] `CalendarPlugin` — calendar view plugins
- [ ] `SwingViewFactory` — calendar view factories
- [ ] `EditComponent` — entity edit dialogs
- [ ] `EventCheck` — event validation
- [ ] `MergeCheckExtension` — merge validation
- [ ] `TaskPresenter` — background-task UI presenters (used by `DualisSyncTaskPresenter`)

### Shared Extension Points
- [ ] `I18nBundle` — internationalization bundles
- [ ] `ExchangeConfigExtensionPoint` — Exchange server integration

## Risks

1. **Extension point deletion in PRD 001 cleanup.** Phase C1 audit + preservation list scoped into PRD 001.
2. **Component scan ordering.** Custom beans overriding core defaults need `@Primary` / `@Order`.
3. **Multiple DataSource beans.** `DataSourceAutoConfiguration` already excluded; explicit `@Bean` for each.
4. **JAXB + Jakarta namespace.** Let Spring Boot BOM manage JAXB versions; remove explicit pins.
5. **jTDS + HikariCP.** May need `commons-dbcp2` for Dualis, or migrate to `mssql-jdbc`.
6. **NTLM/LDAP libs stale.** Upgrade `unboundid-ldapsdk` 2.3 → 6.x. `jcifs` 1.3 won't survive Java 21 — replace with `jcifs-ng` (different API surface: `SmbAuthException`, `CIFSContext`, `BaseContext`); existing `NtlmBindRequest` subclass needs real port.
7. **Client classpath size.** Spring JARs in JNLP grow download; OpenWebStart-only runtime regardless (JWS removed in Java 11).
8. **Plugin enable/disable forked.** After C3, `@ConditionalOnProperty` decides bean existence, but `serverContainerContext.isServiceEnabled` still consulted by `DhbwNtlmAuthStore.isEnabled()`. Mitigation: delete the `isEnabled()` checks, rely solely on `@ConditionalOnProperty`.
9. **Date → LocalDateTime (PRD 001-A).** Sync mappings operate on `Date`/`Calendar`; sequence C3 after 001-A or pin to pre-001-A core.

## Dependencies on Other PRDs

| PRD | Relationship |
|-----|-------------|
| **001: Spring Boot Migration** | **Prerequisite.** Phases C1-C7 assume PRD 001 Phases 0-6 are complete (server Spring DI, REST controllers, plugin system). Phase C7 assumes Phase 8 (restinject removal) is complete. |
| **001-A: Date → LocalDateTime** | Independent. Can proceed in parallel. |
| **002: Multi-Tenancy** | Independent but complementary. Custom deployments may or may not use multi-tenancy. The `TenantAwareFacade` delegates transparently — custom plugins that inject `RaplaFacade` work unchanged. |

## Open Questions

1. **Custom `moduleDescription` needed?** Resolved — delete (no consumer post-`ServiceInfLoader`). C7 step 2.
2. **Separate `custom/pom.xml` or archetype?** Delete `custom/pom.xml`; ship `README.md` with the pattern. Archetype is over-engineering for ~1-2 custom deployments.
3. **Custom static content merge?** Standard classpath ordering — custom `resources/static/` overlays rapla-core's; Spring serves first match.
4. **Plugin option panels: Preferences vs application.yml?** Keep `Preferences` for user-level (per-user LDAP, Morada URL); move server-level (Dualis DB, LDAP server URL, `DhbwAuthPreferences.RoleMapping`) to `application.yml`/`DhbwProperties`. `RoleMapping` read by two extensions — update both call sites in lockstep.
5. **Client-side `@ComponentScan` composition?** Explicit: custom client main does `@Import(ClientConfig.class) @ComponentScan("org.rapla.plugin.dhbw")`.
6. **Auto-configuration vs `@Import(@SpringBootApplication)`?** **Resolved 2026-05-10** — rapla-server ships as `@AutoConfiguration` via `META-INF/spring/AutoConfiguration.imports`; custom project has exactly one `@SpringBootApplication`.
7. **JNLP / JWS future?** Pin to OpenWebStart for now (JWS removed in Java 11); `jpackage` migration is a separate PRD.
