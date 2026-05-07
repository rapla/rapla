# PRD 003: Custom Deployment Model After Spring Migration

**Status:** in-progress — major direction change 2026-05-07 (see "2026-05-07 Direction Change" below).
**Date:** 2026-05-06

## 2026-05-07 Direction Change — dhbwrapla becomes server-only

User decision (2026-05-07, while PRD 005 multi-module split landed): **all dhbw-specific Swing/client code moves INTO the rapla repo.** dhbwrapla retains only server-side artefacts (REST controllers, server-side beans, configuration, scheduled jobs, auth integration).

**Consequences — what this PRD's plan changes to:**

1. **Single-signing pass.** Only `rapla-app` signs `webclient/*.jar`. The dhbwrapla build does not produce a client JAR, does not stage a `webclient/` directory, and does not run `maven-jarsigner-plugin`. The entire "Custom deployments (dhbwrapla) continue to do their own signing pass" path in §"JNLP Client and Code Signing" is **deleted** — see "What gets dropped" below.
2. **No `rapla-client-api` extraction.** PRD 004 / PRD 005 left the door open to splitting `rapla-client` into `rapla-client-api` + `rapla-client-swing` if a customer ever needed a Swing-only Java distribution that pulls less than the full client. With dhbw-specific client code living in rapla, that scenario never arises — the customer's Swing extensions ship inside the canonical `rapla-client` JAR. **The split is now permanently off the table** (was: deferred). PRD 005 §"Known compromise (D3)" and PRD 004 §"Why not split rapla-client into two modules?" both stand as written: a single `rapla-client` is correct for the foreseeable future.
3. **dhbw-specific Swing code gets a home in rapla-client.** Suggested package: `org.rapla.plugin.dhbw.*` (matching the existing `plugin.<vendor>.*` shape). Existing dhbw plugins that already follow the `plugin.<name>.{client,server,extensionpoints}` triplet move into `rapla-client/src/main/java/org/rapla/plugin/dhbw/{client,swing}/` and `rapla-server/src/main/java/org/rapla/plugin/dhbw/server/`, with shared descriptors/interfaces in `rapla-core/src/main/java/org/rapla/plugin/dhbw/` (mirrors the post-PRD-005 layout for stock plugins).
4. **dhbw plugins ship by default.** A consequence of (3): the dhbw extensions are now part of every rapla build, not opted-in by a separate JAR. If certain dhbw plugins should remain optional (e.g. only loaded for the dhbw deployment), they need a runtime feature flag — `@ConditionalOnProperty(prefix="rapla.plugins", name="org.rapla.plugin.dhbw.<id>", matchIfMissing=false)` on each `@Component`/`@Configuration`. **Decide per-plugin during the move.**
5. **dhbwrapla's pom.xml** — depends only on `org.rapla:rapla-server` (and transitively on `rapla-core`). It does NOT depend on `rapla-client`. The dhbwrapla deployable is a Spring Boot fat JAR (its own `@SpringBootApplication`) that re-exports rapla-app's webclient/ resources via Spring Boot's `META-INF/resources` convention — so the rapla-app-signed JNLP set serves correctly from the dhbwrapla deployable too.
6. **Branding / configuration overrides** — dhbwrapla still owns its `application.yml` overrides, custom auth provider beans, scheduled jobs, custom REST endpoints. These all live in dhbwrapla as before. The shape of "how to wire a server-side custom bean" (§"DI Registration: @Extension → @Component" below) is unchanged.

**What gets dropped from the rest of this PRD:**
- §"Client-Side Customizations (Swing)" (line 269 below) — the whole pattern of "custom project's JAR is bundled in JNLP webclient/" goes away. Custom Swing code lives in rapla-client and ships in rapla-app's signed webclient/ set with no separate custom JAR.
- §"JNLP Client and Code Signing" — the dual-signing chain (rapla-core signs, custom re-signs) collapses to single-signing in rapla-app. The PKCS#11/YubiKey config lives in `rapla-app/pom.xml` only. dhbwrapla's pom.xml has zero signing plugin config. The "stripping META-INF/*.SF before re-signing with a different identity" caveat becomes moot.
- §"Custom Application Main Class" stays — dhbwrapla still has its own `@SpringBootApplication` for branding the boot banner / picking up its own `@ComponentScan` / serving its own REST endpoints.

**What remains from the original PRD 003:**
- The DI-registration pattern (§"DI Registration: @Extension → @Component") — still the right shape for server-side custom beans.
- Authentication customization, scheduled jobs, REST endpoints, configuration (JNDI → application.yml) — all unchanged.

The sections below (written 2026-05-06) describe the original "custom-project-with-its-own-client-JAR" plan. **Read with the direction change above in mind** — the Swing/JNLP halves are superseded; the server-side halves remain valid.

---

## Goal

Define how custom Rapla deployments (e.g., `dhbwrapla`) integrate with the platform after the Spring Boot migration (PRD 001) removes `restinject`, `@Extension`/`@DefaultImplementation` annotations, `ServiceInfLoader`, `SimpleRaplaInjector`, and the WAR-overlay build mechanism. The new model must let downstream projects register custom beans, override defaults, hook into extension points, schedule background jobs, add REST endpoints, and contribute Swing UI — all using standard Spring DI and Spring Boot conventions.

**As of 2026-05-07:** "contribute Swing UI" means *contribute Swing code to rapla-client*, not *bundle a separate signed Swing JAR*. See top-of-PRD direction change.

## Current Custom Deployment Mechanism (Pre-Migration)

### Build

1. Custom project sets `<parent>` to `org.rapla:custom:2.1-SNAPSHOT` (relative path `../rapla/custom/pom.xml`)
2. `custom/pom.xml` is a `pom`-packaging parent that provides `rapla` core JAR + test JAR + WAR as `provided` dependencies
3. `parent-process` profile activates when `src/` exists:
   - `maven-war-plugin` overlays custom project onto base rapla WAR
   - Copies client JARs into `webclient/`
   - Signs JARs with PKCS#11/YubiKey
   - Generates `clientlibs.properties` via Ant
4. `moduleDescription` resource is Maven-filtered to `${project.groupId}.${project.artifactId}` (e.g., `org.rapla.dhbw`)

### DI Registration

1. Custom classes use `@Extension(provides = SomeExtensionPoint.class, id = "...")` or `@DefaultImplementation(of = SomeInterface.class, context = InjectionContext.server)`
2. `restinject`'s `AnnotationInjectionProcessor` runs at compile time, generating:
   - `META-INF/org.rapla.servicelist` (master list of service interface names)
   - `META-INF/services/<interface.name>` (implementing classes)
3. At runtime, `ServiceInfLoader` discovers services from all JARs on classpath, merges registrations from both rapla-core and custom module
4. `SimpleRaplaInjector` instantiates everything using `@javax.inject.Inject` constructors

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

**Option A (Recommended): Separate Maven module with dependency on rapla-core**

```
dhbwrapla/
├── pom.xml                    ← parent: rapla-parent, depends on org.rapla:rapla (jar)
├── src/main/java/             ← custom @Component classes
├── src/main/resources/
│   ├── application.yml        ← overrides/extends rapla defaults
│   └── static/                ← custom static content
└── src/test/java/
```

The custom project is a **standard Maven module** that depends on `org.rapla:rapla` (the Spring Boot JAR). It does NOT inherit from `custom/pom.xml`. Instead:

1. Set `<parent>` to `org.rapla:rapla-parent:2.1-SNAPSHOT` (or use rapla as a plain dependency)
2. Add `org.rapla:rapla` as a dependency (the Spring Boot fat JAR is NOT used — the project compiles against the library JAR and runs its own `@SpringBootApplication`)
3. Custom project provides its own `@SpringBootApplication` main class that `@ComponentScan`s both `org.rapla` (core) and `org.rapla.plugin.dhbw` (custom)
4. `spring-boot-maven-plugin` in the custom project produces a fat JAR that includes both rapla-core and custom classes

**Why this works:** Spring Boot's `@ComponentScan` replaces `ServiceInfLoader`. Both rapla-core beans and custom beans are discovered by the same Spring context. Custom `@Component` beans that implement the same interface as core beans are picked up in `List<T>` / `Map<String, T>` injections automatically.

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

**Current:** `DhbwNtlmAuthStore` implements `AuthenticationStore`, discovered via `@Extension`.

**After migration:** Two options:

**Option 1 (Recommended): `@Component` implementing `AuthenticationStore`**

```java
@Component
@Named(DhbwNtlmAuthStore.ID)   // "org.rapla.dhbw.server.auth"
@ConditionalOnProperty(prefix = "rapla.services", name = DhbwNtlmAuthStore.ID, matchIfMissing = false)
public class DhbwNtlmAuthStore implements AuthenticationStore {
    // constructor injection for DhbwAuthPreferences, Logger
    // same LDAP/NTLM logic — delete the existing isEnabled() method
}
```

Spring's `Set<AuthenticationStore>` injection in `ServerServiceConfig` picks it up automatically. Enable via `rapla.services.org.rapla.dhbw.server.auth=true` in `application.yml`. Use the existing `DhbwNtlmAuthStore.ID` constant — do **not** introduce a new short name like `dhbw-ntlm-auth`, since the same ID keys preferences, plugin gates, and `serverContainerContext.isServiceEnabled` lookups today.

**Note:** `DhbwNtlmAuthStore` currently has a field-injected `ServerContainerContext` and an `isEnabled()` method that calls `serverContainerContext.isServiceEnabled(ID)`. After migration, the `@ConditionalOnProperty` gate replaces `isEnabled()` — delete both the field and the method (see Risk 8).

**Option 2: Spring Security `AuthenticationProvider`**

For deeper integration with Spring Security's JWT flow, wrap the LDAP/NTLM logic in a custom `AuthenticationProvider`:

```java
@Component
public class DhbwNtlmAuthenticationProvider implements AuthenticationProvider {
    @Override
    public Authentication authenticate(Authentication auth) {
        // LDAP/NTLM validation using DhbwNtlmAuthStore logic
    }
}
```

This would be registered in `SecurityConfig` and the `AuthController`'s login flow would delegate to it. This is the cleaner long-term option but requires the auth flow to be fully Spring-native.

**Recommendation:** Start with Option 1 (mechanical port, minimal change). Plan Option 2 as a follow-up.

### Scheduled Background Jobs

**Current:** `DualisSyncJobStarter` and `MoradaSyncJobStarter` implement `ServerExtension`, started by `ServerServiceImpl` calling `serverExtension.start()` at startup. They use `CommandScheduler.scheduleAtGivenTime()` for cron-like scheduling.

**After migration:** Two options:

**Option 1 (Recommended): `@Component` implementing `ServerExtension`**

```java
@Component
@Named("org.rapla.dhbw.interface.dualis")
@ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.dhbw.interface.dualis", matchIfMissing = true)
public class DualisSyncJobStarter implements ServerExtension {
    private final CommandScheduler scheduler;     // interface, not DefaultScheduler
    private final DualisImportJob job;
    private final Logger logger;
    private final List<Disposable> schedules = new ArrayList<>();

    public DualisSyncJobStarter(CommandScheduler scheduler, DualisImportJob job, Logger logger) {
        this.scheduler = scheduler;
        this.job = job;
        this.logger = logger;
    }

    @Override
    public void start() {
        scheduler.run(job);
        schedules.add(scheduler.scheduleAtGivenTime(job, 8, 11));
        schedules.add(scheduler.scheduleAtGivenTime(job, 12, 11));
        schedules.add(scheduler.scheduleAtGivenTime(job, 16, 11));
    }
}
```

**Cleanup during the port:** the existing constructor takes `DefaultScheduler` (impl class). Replace the parameter type with the `CommandScheduler` interface so the bean wires to the Spring-managed `CommandScheduler` from `ServerCoreConfig` rather than depending on the concrete class.

`ServerExtension` remains as an interface. Spring collects all implementations into `Set<ServerExtension>` or `Map<String, ServerExtension>` and `ServerServiceImpl` iterates them at startup — same lifecycle, Spring-managed beans.

**Option 2: Spring `@Scheduled`**

Replace `CommandScheduler.scheduleAtGivenTime()` with `@Scheduled(cron = "...")` on the job method. This is simpler but loses the timezone-aware scheduling that `CommandScheduler` provides via `TimeZoneConverter`. Only adopt if `CommandScheduler` is removed in a future phase.

### REST Endpoints

**Current:** JAX-RS `@Path`/`@GET`/`@POST` classes discovered by RESTEasy.

**After migration:** Spring `@RestController` classes discovered by `@ComponentScan`.

| Current Class | Migration |
|---------------|-----------|
| `RaplaPruefungen` (`@Path("pruefungen")`) | `@RestController @RequestMapping("/pruefungen")` |
| `SteleExportPageGenerator` (`@Path("terminal-export")`) | `@RestController @RequestMapping("/terminal-export")` |
| `DualisEventsLoaderImpl` (`@Path("DualisEventLoader")` on the **interface**) | `@RestController @RequestMapping("/DualisEventLoader")` on the **impl class**. Spring does not pick up `@RequestMapping` from interfaces by default; either move the annotation to `DualisEventsLoaderImpl` or delete the `DualisEventsLoader` interface (it exists only to satisfy `restinject`'s remote-proxy generator, which is gone post-migration). |
| `ImportController` (ICS upload) | `@RestController @RequestMapping("/semesterplan")` |
| Course overview pages | `@RestController` returning HTML strings (or Thymeleaf templates) |

**JAX-RS-isms that don't translate 1:1:**

| JAX-RS | Spring MVC equivalent |
|--------|------------------------|
| `@Context HttpServletRequest req` | bare `HttpServletRequest req` parameter — no annotation needed |
| `@Consumes(MediaType.MULTIPART_FORM_DATA)` + a `@MultipartForm` POJO (`ICSFileUploadForm`) | `@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)` + `@RequestParam("file") MultipartFile`. The `ICSFileUploadForm` wrapper class disappears entirely. |
| `@Produces(MediaType.TEXT_PLAIN)` | `@GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)` or set `Content-Type` on the response |
| Field-injected `@Inject Logger logger;` (package-private fields, e.g. `RaplaPruefungen`, `ImportController`) | constructor-injected `private final Logger logger;` — Spring discourages field injection and `final` fields can't be field-injected |
| `RemoteSession.checkAndGetUser(req)` for auth | `Authentication` parameter from Spring Security context, or `@PreAuthorize` on the method |

**HTML-generating endpoints:** `RaplaPruefungen` and terminal display pages currently generate HTML strings inline. Post-migration options:
- Keep inline HTML generation (works, low effort)
- Move to Thymeleaf templates (cleaner, but adds a template engine dep)
- Migrate to a frontend-only solution (Angular page consuming JSON API)

**Recommendation:** Keep inline HTML for the initial migration. Plan Angular migration separately.

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

**Current:** `@Extension(provides = ReservationWizardExtension.class)` on `DualisImportWizard`, `PluginOptionPanel` implementations, etc. Discovered by `ServiceInfLoader` on the client classpath.

**After migration:** `@Component` implementations discovered by client-side `AnnotationConfigApplicationContext`:

```java
@Component
@Named(DualisImportPlugin.ID)   // existing constant, e.g. "org.rapla.plugin.dhbw.dualisimport"
public class DualisImportWizard implements ReservationWizardExtension {
    // constructor injection
}
```

The client-side `@ComponentScan` must include the custom project's packages (e.g., `org.rapla.plugin.dhbw`). The custom project's JAR is bundled in the JNLP `webclient/` directory (same path as today — see "JNLP Client and Code Signing" section below).

**Client packaging:** The custom project's classes are JARed and placed in the JNLP `webclient/` directory alongside rapla-core JARs. `maven-dependency-plugin:copy-dependencies` handles this. No WAR overlay needed. See "JNLP Client and Code Signing" below for the full picture (the staging directory, `clientlibs.properties`, signing chain, and Java Web Start runtime story).

### JNLP Client and Code Signing

The PRD 001 migration deletes the WAR but **keeps the JNLP launch URL**: `RaplaJNLPController` (`@GetMapping("/raplaclient.jnlp")`) is gated by `@ConditionalOnBean(RaplaJNLPPageGenerator.class)` and serves a JNLP descriptor referencing JARs under `/webclient/`. Custom deployments inherit this contract — their custom JAR (and any client-side runtime deps) must show up in `/webclient/` as **signed** JARs, with their filenames listed in `clientlibs.properties` on the classpath. Below is what changes for a custom deployment.

#### How it works today (pre-migration)

1. `rapla/pom.xml` runs `maven-dependency-plugin:copy-dependencies` to materialize client-side dependencies under `${project.build.directory}/${project.build.finalName}/webclient/` inside the WAR target. Ant tasks generate `clientlibs.properties` (a `;`-separated filename list) on the classpath.
2. `rapla/pom.xml` activates one of `sign-jks` (dev — uses `raplaselfsigned.ks`) or `sign-pkcs11` (production — YubiKey via `pkcs11.cfg`, alias `Certificate for PIV Authentication`) at the `prepare-package` phase, signing every `webclient/*.jar`.
3. `custom/pom.xml` (when building dhbwrapla via the WAR overlay) re-runs `maven-dependency-plugin:copy-dependencies` for any custom-project-only client deps, then re-runs `maven-jarsigner-plugin` over the same `webclient/` directory using `${project.basedir}/../rapla/pkcs11.cfg` — i.e. the **same hardware token / same signing identity as rapla-core**. Re-signing already-signed jars with the same identity is idempotent in practice; signing the new custom jar produces a `webclient/` set with one consistent signer.
4. `RaplaJNLPPageGenerator.getClientLibs()` reads `/clientlibs.properties` from the classpath, prepends `webclient/`, and emits a `<jar href="webclient/…"/>` entry per file. OpenWebStart downloads each signed jar from `/rapla/webclient/<file>.jar`.
5. `<security><all-permissions/></security>` in the JNLP requires **every** JAR in the launch set to be signed (unsigned jars abort the launch) and prefers a single consistent signer to avoid SecurityException at runtime.

#### What changes after Spring Boot migration

**Signed jars are still required.** JNLP `<all-permissions/>` will not accept unsigned content — the launcher aborts. Both vanilla rapla and custom deployments must continue to ship a signed `webclient/` set. The signing **profiles** (`sign-jks`, `sign-pkcs11`) carry over verbatim; only the **directory they sign** moves from the WAR target to a Spring Boot staging dir.

**Where the JARs live.** There is no WAR target directory anymore; the rapla-core fat JAR is built by `spring-boot-maven-plugin`. But `RaplaJNLPPageGenerator` still expects `webclient/<file>.jar` to be reachable as a static URL. Two viable strategies:

| Strategy | How it works | Trade-off |
|----------|--------------|-----------|
| **(A) Static dir under `resources/static/`** (recommended for first migration) | Build copies the signed jars into `src/main/resources/static/webclient/` *before* `spring-boot:repackage`, so they end up inside the fat JAR. Spring Boot's `WebMvcAutoConfiguration` serves `classpath:/static/**` automatically. `RaplaJNLPPageGenerator.getClientLibs()` keeps reading `/clientlibs.properties` from the classpath; URLs in the generated JNLP resolve to `/rapla/webclient/<file>.jar`. | Bundles signed jars *inside* the fat JAR. Increases fat-JAR size; re-signing (cert rotation) requires a full rebuild. |
| **(B) External `webclient/` directory + `spring.web.resources.static-locations`** | Signed jars live next to the running fat JAR (e.g. `./webclient/`). Configure `spring.web.resources.static-locations=file:./webclient/,classpath:/static/` so Spring serves them from disk. | Decouples client JARs from the fat JAR; cert rotation is a deployment-time `jarsigner` over `./webclient/` with no rebuild. Better for production. Requires the deploy target to provide the directory. |

**Recommendation:** ship (A) for the initial migration (no infra changes — same shape as the WAR), expose `spring.web.resources.static-locations` so operators can switch to (B) for production cert-rotation workflows without code changes.

`SecurityConfig` in PRD 001 already permits `/webclient/**` (`SecurityConfig.java:27`), so no Spring Security changes are needed.

**Signing chain — both projects keep signing.**

- **Rapla-core** keeps `sign-jks` and `sign-pkcs11` profiles (`rapla/pom.xml` lines 326–417). The only change is `<archiveDirectory>` retargets from `${project.build.directory}/${project.build.finalName}/webclient` (the dead WAR path) to `${project.build.directory}/webclient` (a Spring-Boot-friendly staging dir). A `maven-resources-plugin:copy-resources` step then moves the signed dir into `src/main/resources/static/webclient/` **before** `spring-boot:repackage` so the fat JAR ships them. Vanilla deployments work standalone with this set.
- **Custom deployments (dhbwrapla)** continue to do their own signing pass over `target/webclient/`, which is populated by (i) copying rapla-core's already-signed jars (either by depending on a `rapla-webclient` zip classifier or by unpacking from the rapla-core fat JAR), then (ii) `maven-dependency-plugin:copy-dependencies` for any custom-project-only deps, then (iii) building/copying the custom project's own JAR. The `maven-jarsigner-plugin` runs over the lot. Re-signing rapla-core's jars with the **same identity** (shared `pkcs11.cfg` / shared YubiKey) is idempotent — the `MANIFEST.MF` and `*.SF` entries don't change. If the custom deployment uses a **different** signing identity than rapla-core (different YubiKey, different self-signed JKS), the existing rapla signatures must be stripped first (`zip -d rapla-client.jar 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/*.EC'`) before re-signing — otherwise OpenWebStart sees mixed signers and refuses to launch under `<all-permissions/>`.

Concretely, the custom project's signing block:

```xml
<build>
  <plugins>
    <plugin>
      <artifactId>maven-dependency-plugin</artifactId>
      <executions>
        <execution>
          <id>stage-client-jars</id>
          <phase>prepare-package</phase>
          <goals><goal>copy-dependencies</goal></goals>
          <configuration>
            <outputDirectory>${project.build.directory}/webclient</outputDirectory>
            <includeScope>runtime</includeScope>
            <!-- Filter to client-runtime deps only — exclude server-only Spring jars,
                 Tomcat, Spring Security oauth2-resource-server, RESTeasy remnants. -->
          </configuration>
        </execution>
      </executions>
    </plugin>
    <plugin>
      <artifactId>maven-jarsigner-plugin</artifactId>
      <executions>
        <execution>
          <id>sign-webclient-pkcs11</id>
          <phase>package</phase>
          <goals><goal>sign</goal></goals>
        </execution>
      </executions>
      <configuration>
        <providerClass>sun.security.pkcs11.SunPKCS11</providerClass>
        <providerArg>${project.basedir}/pkcs11.cfg</providerArg>
        <archiveDirectory>${project.build.directory}/webclient</archiveDirectory>
        <includes><include>**/*.jar</include></includes>
      </configuration>
    </plugin>
    <plugin>
      <artifactId>maven-resources-plugin</artifactId>
      <!-- Copy the signed jars into src/main/resources/static/webclient/
           BEFORE spring-boot:repackage runs, so they end up in the fat JAR.
           Or skip this step for strategy (B). -->
    </plugin>
  </plugins>
</build>
```

The PKCS#11/YubiKey configuration carries over **unchanged** — same `pkcs11.cfg`, same provider class, same hardware token. The only differences:

1. **`archiveDirectory` source** — was `${project.build.directory}/${project.build.finalName}/webclient` (inside the WAR target). Becomes a `target/webclient/` staging dir, populated from a mix of rapla-core's pre-signed jars and the custom project's own jar.
2. **Signing happens in *both* projects, not one**. Rapla-core continues to sign the jars it ships (so vanilla deployments work without a custom build); the custom project signs the union (its own jar + any extras it bundles).
3. **One signing identity per JNLP** — JWS rejects mixed signers under `<all-permissions/>`. The current dhbwrapla setup achieves this by sharing the rapla-core PKCS#11 config (`${project.basedir}/../rapla/pkcs11.cfg`); the post-migration setup must preserve that pattern. If a custom deployment chooses a *different* identity, it must strip the rapla-core signatures from each jar before re-signing (see signing-chain note above).

**`clientlibs.properties` generation.** Today this is generated by Ant in the WAR overlay. Replace with a Maven step in the custom project that lists the contents of `target/webclient/`:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>build-helper-maven-plugin</artifactId>
  <!-- or use exec-maven-plugin / antrun -->
</plugin>
```

The generated file lands at `src/main/resources/clientlibs.properties` (or in the build output) so `RaplaJNLPPageGenerator.getClientLibs()` (`RaplaJNLPPageGenerator.java:95`) finds it on the classpath. **No change to `RaplaJNLPPageGenerator` is needed** — it already reads from the classpath and falls back to `IOUtil.getJarFiles(base, "webclient")` for legacy WAR layouts.

**Codebase URL.** `RaplaJNLPPageGenerator` builds the codebase from the request (`getCodebase(request)`) and emits `codebase + "/webclient/<file>.jar"`. Spring Boot's `server.servlet.context-path=/rapla` (already set in PRD 001 Phase 0) preserves the `/rapla/raplaclient.jnlp` and `/rapla/webclient/*.jar` URL shape — no change needed for OpenWebStart bookmarks pointing at the existing URL.

**JNLP signing requirements still apply.** The JNLP file itself can optionally be signed and embedded in the JAR (`JNLP-INF/APPLICATION.JNLP`) for "trusted JNLP" mode, but Rapla doesn't use that today — every dynamic property in the JNLP (`<title>`, `vmXmsSize`, codebase) is computed per-request, which is incompatible with embedded JNLP. Keep it as a server-rendered descriptor. **Every `<jar>` entry must be signed**, and the launcher will refuse the launch if any are unsigned or signed by a mix of identities under `<all-permissions/>`.

**Java Web Start runtime.** Java Web Start was removed from the JDK in Java 11. End users must install **OpenWebStart** (the IcedTea-Web fork that AdoptOpenJDK adopted) — not Oracle JRE. This is independent of the migration but worth restating in deployment docs. Long term, `jpackage` (since JDK 14) can produce native installers (.deb, .msi, .pkg) and skip JNLP entirely; that's a separate PRD (Open Question 7). For PRD 003's scope, the answer is "OpenWebStart, same signing chain, JARs served from Spring Boot static resources."

#### Summary of dhbwrapla-side changes for JNLP/signing

| Item | Pre-migration | Post-migration |
|------|---------------|----------------|
| Build target for client jars | `${webapp.dir}/webclient/` inside WAR | `target/webclient/` staging dir, then either `src/main/resources/static/webclient/` (strategy A) or external `./webclient/` (strategy B) |
| Who signs | Rapla-core signs its set; custom project re-signs the union | **Same** — both still sign. Rapla-core's signing is required so vanilla deployments work standalone; custom project's signing is required for the custom JAR + bundled extras. |
| PKCS#11/YubiKey provider | Unchanged | Unchanged |
| Shared signing identity | Custom project points at `../rapla/pkcs11.cfg` | **Keep this pattern.** Same hardware token / same cert across both poms means re-signing rapla-core's jars is idempotent. Switching identities requires stripping `META-INF/*.SF`/`*.RSA` first. |
| `clientlibs.properties` | Generated by Ant during WAR overlay | Generated by Maven step in custom project (or rapla-core, if vanilla deployment) |
| `RaplaJNLPPageGenerator` | Served by `MainServlet` JAX-RS dispatch | Served by `RaplaJNLPController` (`@GetMapping("/raplaclient.jnlp")`) — already done in PRD 001 |
| URL path | `/rapla/raplaclient.jnlp` | Unchanged (HARD CONSTRAINT — `UrlPreservationTest`) |
| JAR URL path | `/rapla/webclient/*.jar` | Unchanged |
| Spring Security gate | N/A (Tomcat servlet) | `permitAll` on `/webclient/**` — already configured in `SecurityConfig.java:27` |
| Runtime | Oracle JRE / IcedTea-Web | OpenWebStart (same as today on Java 11+) |
| Re-signing workflow (cert rotation) | Rebuild WAR | Strategy A: rebuild custom fat JAR. Strategy B: re-run `jarsigner` over `./webclient/` on the deploy host — no rebuild. |

### Custom Application Main Class

The custom project provides its own `@SpringBootApplication` that composes rapla-core + custom:

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "org.rapla.server.spring",   // rapla core server config
    "org.rapla.server",          // rapla core server components
    "org.rapla.plugin",          // rapla core plugins + custom plugins
    "org.rapla.dhbw"             // custom project components
})
@EnableConfigurationProperties({RaplaServerProperties.class, DhbwProperties.class})
public class DhbwRaplaApplication {
    public static void main(String[] args) {
        SpringApplication.run(DhbwRaplaApplication.class, args);
    }
}
```

This replaces `RaplaSpringBootApplication` as the entry point for the custom deployment. The core `RaplaSpringBootApplication` remains the default for vanilla deployments.

Alternatively, the custom project can import the core application:

```java
@SpringBootApplication
@Import(RaplaSpringBootApplication.class)
@ComponentScan(basePackages = {"org.rapla.dhbw", "org.rapla.plugin.dhbw"})
public class DhbwRaplaApplication { ... }
```

**Recommendation:** Use `@Import` for the initial migration — quickest to land. Plan to migrate to a Spring Boot **auto-configuration** (see Open Question 6) as the final shape: `@Import(@SpringBootApplication)` is a known foot-gun (duplicate scan roots, double-registered auto-configs). The cleaner long-term pattern is to ship rapla-core as a non-`@SpringBootApplication` `@AutoConfiguration` plus a `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` entry, so the custom project has exactly one `@SpringBootApplication` and pulls in core automatically.

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

1. **Extension point deletion during PRD 001 cleanup** — If PRD 001 Phase 8 aggressively deletes interfaces marked with `@ExtensionPoint`, custom deployments lose their hook points. **Mitigation:** Phase C1 audit + explicit preservation list in PRD 001 cleanup scope.

2. **Component scan ordering** — Custom beans that override core defaults (e.g., a custom `AuthenticationStore` replacing the default) must be registered with higher priority. Spring's `@Primary` annotation or `@Order` handles this, but needs careful testing.

3. **Multiple DataSource beans** — Rapla already excludes `DataSourceAutoConfiguration`. Custom projects adding a secondary DataSource (for Dualis) must define both explicitly. Spring Boot's `DataSourceBuilder` works but requires care to avoid ambiguity.

4. **JAXB + Jakarta namespace** — dhbwrapla uses JAXB for Morada XML parsing. The pom.xml includes `jakarta.xml.bind:jakarta.xml.bind-api:3.0.1` and `jaxb-impl:3.0.2`. After migration, these may conflict with Spring Boot's managed JAXB version. **Mitigation:** Let Spring Boot BOM manage JAXB versions; remove explicit version pins.

5. **jTDS driver compatibility** — jTDS (`net.sourceforge.jtds:jtds:1.3.3`) is a legacy SQL Server driver. It may not be compatible with HikariCP (Spring Boot's default pool). **Mitigation:** Test with HikariCP; if incompatible, configure `commons-dbcp2` as the pool for the Dualis datasource, or migrate to `mssql-jdbc` (Microsoft's modern driver).

6. **NTLM/LDAP library compatibility** — `jcifs:jcifs:1.3.17` (a 2011 release) and `com.unboundid:unboundid-ldapsdk:2.3.5` are stale. Spring Boot 3.2 runs on Java 21. **Mitigation:** Upgrade `unboundid-ldapsdk` to 6.x. `jcifs` 1.3 will not survive Java 21 cleanly — replace with `jcifs-ng`, which has a different API surface (`SmbAuthException`, `CIFSContext`, `BaseContext`). The existing `NtlmBindRequest` subclass needs a real port, not a recompile — cost this up front.

7. **Client classpath size** — Adding Spring JARs to the JNLP classpath increases download size. The custom project adds its own JAR on top. **Mitigation:** Measure; same concern as PRD 001 Open Question 4. See also "JNLP Client and Code Signing" section — Java Web Start was removed from the JDK in Java 11, so the runtime story is OpenWebStart-only regardless.

8. **Plugin enable/disable forked between two systems.** After C3, `@ConditionalOnProperty(prefix="rapla.services", name=ID)` decides whether a Spring bean exists, while `serverContainerContext.isServiceEnabled(ID)` (read from `Preferences`/xconf) is still consulted by code like `DhbwNtlmAuthStore.isEnabled()`. A service can compile-time exist but be silently disabled at runtime by stale preferences — or the inverse. **Mitigation:** Pick one source of truth before C3 starts. Either (a) delete every `isEnabled()` check that consults `serverContainerContext.isServiceEnabled` in custom code and rely solely on `@ConditionalOnProperty`, or (b) make `ServerContainerContext.isServiceEnabled` delegate to the same Spring `Environment` property. Option (a) is the cleaner direction.

9. **Date → LocalDateTime interaction (PRD 001-A).** Dualis and Morada sync mappings (`DualisRaplaMapping`, `MoradaRaplaMapping`, `DualisImportJob`, `MoradaImportJob`) operate on `Date`/`Calendar` for appointment times. If PRD 001-A lands before C3, those signatures change underneath the custom port. **Mitigation:** Sequence C3 after PRD 001-A's appointment-related conversions, or pin the dhbwrapla port to a pre-001-A core revision.

## Dependencies on Other PRDs

| PRD | Relationship |
|-----|-------------|
| **001: Spring Boot Migration** | **Prerequisite.** Phases C1-C7 assume PRD 001 Phases 0-6 are complete (server Spring DI, REST controllers, plugin system). Phase C7 assumes Phase 8 (restinject removal) is complete. |
| **001-A: Date → LocalDateTime** | Independent. Can proceed in parallel. |
| **002: Multi-Tenancy** | Independent but complementary. Custom deployments may or may not use multi-tenancy. The `TenantAwareFacade` delegates transparently — custom plugins that inject `RaplaFacade` work unchanged. |

## Open Questions

1. **Custom `moduleDescription` needed?** **Resolved — delete.** No remaining consumer after `ServiceInfLoader` removal. Captured in C7 step 2.

2. **Separate `custom/pom.xml` or archetype?** Should the rapla project continue to ship a `custom/pom.xml` parent, or should it provide a Maven archetype for new custom projects? **Recommendation:** Delete `custom/pom.xml`. Provide a `README.md` with the dependency/scan pattern instead. An archetype is over-engineering for likely 1-2 custom deployments.

3. **Custom static content merge?** How do custom projects override or extend static content (HTML, CSS) served from `resources/static/`? **Recommendation:** Custom project's `resources/static/` overlays rapla-core's via standard classpath ordering (custom JAR listed first). Spring Boot serves the first match.

4. **Plugin option panels storing to Preferences vs application.yml?** Currently `PluginOptionPanel` saves to Rapla's `Preferences` (per-user, stored in data XML). After migration, should these migrate to `application.yml`? **Recommendation:** Keep `Preferences` for user-level settings (per-user LDAP config, per-user Morada URL). Move server-level settings (Dualis DB connection, LDAP server URL, `DhbwAuthPreferences.RoleMapping` consumed by `DhbwNtlmAuthStore` and `DhbwExchangeExtension`) to `application.yml` / `DhbwProperties`. Note: `RoleMapping` is read by *two* extensions, so the migration must update both call sites in lockstep.

5. **Client-side `@ComponentScan` composition?** The rapla-core `ClientConfig` scans `org.rapla.client`, `org.rapla.plugin.*.client`. Custom projects need their packages scanned too. Options: (a) Custom project provides its own `ClientConfig` that `@Import`s the core one and adds packages. (b) Core `ClientConfig` uses a `@ComponentScan` base package that's broad enough to include custom projects (e.g., `org.rapla`). **Recommendation:** (a) — explicit composition. The custom client's main class does `@Import(ClientConfig.class) @ComponentScan("org.rapla.plugin.dhbw")`.

6. **Auto-configuration vs `@Import(@SpringBootApplication)`?** Should rapla-core ship as a Spring Boot **auto-configuration** so custom projects have a single `@SpringBootApplication`, or should the custom project keep its own `@SpringBootApplication` and `@Import(RaplaSpringBootApplication.class)`? **Recommendation:** Land the migration with `@Import` (quickest path), then refactor in C7: extract a non-`@SpringBootApplication` `@AutoConfiguration` class (e.g. `RaplaServerAutoConfiguration`) plus a `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` entry. Two `@SpringBootApplication` annotations in one boot is a known foot-gun (duplicate auto-config registration, scan-base ambiguity), so the auto-config form is the proper end state.

7. **JNLP / Java Web Start future?** Java Web Start was removed from the JDK in Java 11; OpenWebStart is the only practical runtime. The rapla-core PRD 001 preserves `/raplaclient.jnlp` as a hard URL constraint, but it's worth declaring whether custom deployments commit to OpenWebStart long-term, or plan to migrate the Swing client to a packaged installer (`jpackage`). See "JNLP Client and Code Signing" section. **Recommendation:** Pin to OpenWebStart for now; surface `jpackage` as a separate PRD when the team is ready.
