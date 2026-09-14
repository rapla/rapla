# PRD 001: Spring Boot Migration

> **Update 2026-06-24 — legacy HMAC token path finally removed.** This PRD's design
> target ("`TokenHandler` + `SignedToken` — deleted; JWT-only") was only partly carried
> out during the migration: the `TokenHandler`/`SignedToken`/`ValidToken` classes and
> the `RemoteSessionImpl` fallback actually survived as a wired-but-vestigial HMAC path
> (the `@Bean TokenHandler`, the `remoteSession(...TokenHandler...)` wiring, and
> `SpringSecurityRemoteSession`'s delegate-to-legacy branch described below). They were
> deleted on 2026-06-24 once auditing confirmed nothing mints the `userId$signature`
> token. Auth is now genuinely JWT-only — the bean-wiring snapshots below are historical.

**Status:** done — **Phases 1–9 all complete** as of 2026-05-08. Phase 9 step 2 (Gson removal) shipped end-to-end alongside the Jackson 2 → Jackson 3 cutover under [PRD 011](011-spring-boot-4-jackson-3.md): `gson` dep dropped from rapla-bom; `HTTPWithJsonConnector`, `HTTPWithJsonMailConnector`, `MailapiClient`, `JacksonMergePatch`, `RestAPIExample` migrated to Jackson; reactor `mvn test` green on Spring Boot 4.0.6 + Jackson 3.1.2. Phase 4 (client DI) completed via [PRD 002](002-swing-spring-di.md). Verified 2026-05-08: zero `gson` in dep tree, zero `gson` imports in source, zero `gson` references in any pom file.
**Date:** 2026-05-06 (initial); 2026-05-08 (Phase 9 + Spring Boot 4 / Jackson 3 follow-on)

## Implementation Status

**Branch:** `spring-boot`
**Last verified:** 2026-05-06 — `mvn compile -DskipTests` BUILD SUCCESS; `mvn test-compile` BUILD SUCCESS; `mvn test` BUILD SUCCESS; `mvn package -DskipTests` BUILD SUCCESS — **23 tests across 7 Spring contexts**:
- `RaplaSpringBootApplicationTest` — 7 tests. Verifies `RaplaServerProperties` binding + 11 core beans.
- `ServerServiceIntegrationTest` — 2 tests. Boots `ServerServiceContainer` + auth chain (`RaplaKeyStorage`, `TokenHandler`, `RaplaAuthentificationService`, `RemoteSession`) with a `@TempDir` file datasource.
- `RemoteLoggerControllerTest` — 1 test. `PUT /logger/{id}` via MockMvc.
- `ICalTimezonesControllerTest` — 2 tests. `GET /ical/timezones` + `/default`.
- `AuthControllerIntegrationTest` — 4 tests. `POST /auth/login` JWT issuance, `POST /auth/refresh` rotation (new `jti`), protected `GET /resources` returns 401 without bearer / 200 with bearer.
- `UrlPreservationTest` — 5 tests. Routes for `/ical`, `/internal_ical`, `/calendar`, `/calendar.csv`, `/raplaclient.jnlp` all return non-404 (HARD CONSTRAINT — URL paths preserved).
- `ClientConfigTest` — 1 test. Standalone `AnnotationConfigApplicationContext(ClientConfig.class)` boots, all 8 client beans resolve.
- `SpringRaplaClientTest` — 1 test. Verifies `SpringRaplaClient` (Spring-based replacement for legacy `RaplaClient`) boots and exposes `ClientFacade` + REST proxies.

Stack: Spring Boot 3.2.5, Tomcat 10, Java 21 runtime, `release 17` source.

Legacy JUnit 4 tests (under `src/test/java/org/rapla/...` extending `RaplaTestCase`) are silently skipped by `surefire 3.0.0-M9` because its auto-detected provider is JUnit Platform 5; they are also broken at runtime since Phase 1.2 (`restinject`'s `@javax.inject.Inject` scanning is dead). Migration is Phase 1.7.

### Quick reference — what runs under Spring today

```
RaplaSpringBootApplication (@SpringBootApplication, scans org.rapla.server.spring.*)
├── @EnableConfigurationProperties(RaplaServerProperties.class)
├── SecurityConfig (Spring Security)
│   ├── @Bean SecurityFilterChain (permitAll stub, CSRF disabled, stateless, CORS enabled)
│   └── @Bean CorsConfigurationSource (all origins/methods/headers — placeholder)
├── LegacyServerBridgeConfig
│   ├── @Bean Logger raplaLogger()
│   └── @Bean ServerContainerContext serverContainerContext(RaplaServerProperties)
├── ServerCoreConfig (~18 beans)
│   ├── @Bean ServerBundleManager bundleManager()
│   ├── @Bean TimeZoneConverter timeZoneConverter()
│   ├── @Bean RaplaResources raplaResources(BundleManager)
│   ├── @Bean RaplaSystemInfo raplaSystemInfo(BundleManager)
│   ├── @Bean RaplaLocale raplaLocale(BundleManager)
│   ├── @Bean CommandScheduler commandScheduler(Logger, TimeZoneConverter)
│   ├── @Bean RemoteLogger remoteLogger(AutowireCapableBeanFactory)
│   ├── @Bean PromiseWait promiseWait(Logger)
│   ├── @Bean(name="org.rapla") FunctionFactory standardFunctions(RaplaLocale)
│   ├── @Bean(name="appointment") FunctionFactory appointmentNoteFunctions(ObjectProvider<RaplaFacade>)
│   ├── @Bean PermissionExtension raplaDefaultPermission()
│   ├── @Bean RaplaFacade raplaFacade(RaplaResources, CommandScheduler, Logger)
│   ├── @Bean ICalTimezones iCalTimezones(AutowireCapableBeanFactory)
│   ├── @Bean MailInterface mailInterface(RaplaFacade, ServerContainerContext)
│   ├── @Bean MailToUserImpl mailToUser(MailInterface, RaplaFacade, Logger)
│   ├── @Bean ResourceBundleList resourceBundleList(Set<I18nBundle>, BundleManager)
│   ├── @Bean AppointmentFormater appointmentFormater(RaplaResources, RaplaLocale)
│   └── @Bean ServerStorageSelector serverStorageSelector(...)
├── JwtConfig (@ConditionalOnProperty rapla.file-datasources.raplafile)
│   ├── @Bean JwtDecoder jwtDecoder(RaplaKeyStorage)
│   └── @Bean JwtIssuer jwtIssuer(RaplaKeyStorage)
├── ServerServiceConfig (@ConditionalOnProperty rapla.file-datasources.raplafile, ~22 beans)
│   ├── @Bean CachableStorageOperator cachableStorageOperator(ServerStorageSelector)
│   ├── @Bean ServerServiceContainer serverServiceContainer(... 12 deps ...)
│   ├── @Bean @DependsOn("serverServiceContainer") RaplaKeyStorage raplaKeyStorage(...)
│   ├── @Bean TokenHandler tokenHandler(RaplaKeyStorage, CachableStorageOperator)
│   ├── @Bean RaplaAuthentificationService raplaAuthentificationService(AutowireCapableBeanFactory)
│   ├── @Bean Set<AuthenticationStore> authenticationStores() (empty)
│   ├── @Bean RemoteSession remoteSession(Logger, TokenHandler, RaplaAuthentificationService)
│   ├── @Bean @RequestScope RemoteLocaleService remoteLocaleService(HttpServletRequest, AutowireCapableBeanFactory)
│   ├── @Bean SecurityManager securityManager(Logger, RaplaResources, AppointmentFormater, CachableStorageOperator)
│   ├── @Bean @RequestScope RaplaResourcesRestPage raplaResourcesRestPage(...)
│   ├── @Bean @RequestScope RaplaDynamicTypesRestPage raplaDynamicTypesRestPage(...)
│   └── @Bean @RequestScope RaplaEventsRestPage raplaEventsRestPage(...)
└── REST Controllers under org.rapla.server.spring.web
    ├── RemoteLoggerController        PUT /logger/{id}
    ├── ICalTimezonesController       GET /ical/timezones, GET /ical/timezones/default
    ├── ICalConfigController          GET /ical/config, GET /ical/config/default                (gated by @ConditionalOnBean(RemoteSession.class))
    ├── MailToUserController          POST /mail/send                                            (gated)
    ├── RemoteLocaleController        GET /locale/{id}, POST /locale                             (gated)
    ├── RaplaResourcesController      GET/PUT/POST/DELETE /resources, GET /resources/{id}       (gated)
    ├── RaplaEventsController         GET/PUT/POST/DELETE /events, GET/PATCH /events/{id}       (gated)
    ├── RaplaDynamicTypesController   GET /dynamictypes                                          (gated)
    ├── AuthController                POST /auth/login, /auth/refresh → JWT                      (gated by @ConditionalOnProperty rapla.file-datasources.raplafile)
    ├── Export2iCalController         GET /ical, GET /internal_ical                              (HARD CONSTRAINT — URL paths)
    ├── RaplaJNLPController           GET /raplaclient, GET /raplaclient.jnlp                    (URL constraint)
    ├── CalendarPageController        GET /calendar, /calendar.csv, /internal_calendar(.csv)     (HARD CONSTRAINT)
    ├── UrlEncryptionController       POST /urlencryption                                         (gated)
    ├── ArchiverController            POST /archiver, GET /archiver, POST /archiver/backup,/restore (gated)
    ├── ICalImportController          POST /ical/import                                          (gated)
    ├── JNDIConfigController          POST /jndi, GET /jndi                                      (gated)
    └── MailConfigController          GET /mail/config/external, POST /mail/config, GET /mail/config (gated)
```

### What's deleted from the original codebase

- `src/main/java/org/rapla/server/MainServlet.java` (~450 LOC servlet bootstrap)
- `src/main/java/org/rapla/server/provider/resteasy/ResteasyExceptionMapper.java` (RESTEasy 3.15 mapper)
- `src/main/java9/module-info.java` (incompatible under Java 17)
- `src/main/java/org/rapla/storage/dbrm/gwt/GwtRaplaLock.java` (Phase 7 — GWT removal)
- `src/main/java/org/rapla/plugin/copyurl/gwt/GwtURLCopyService.java` (Phase 7)
- `src/main/java/org/rapla/components/i18n/client/gwt/GwtBundleManager.java` (Phase 7)
- The three empty `gwt/` directories under `dbrm`, `copyurl`, `components/i18n/client` (Phase 7)
- `src/main/java/org/rapla/server/internal/ServerStarter.java` (Phase 1.7 partial — no callers after `MainServlet` deletion)
- `src/main/java/org/rapla/server/internal/console/StandaloneStarter.java` (Phase 1.7 partial — no callers)
- `src/main/java/org/rapla/server/ServerCreator.java` (Phase 1.7 — no callers after legacy test stack deletion)
- `src/test/java/org/rapla/test/util/RaplaTestCase.java` + `AbstractTestWithServer.java` (Phase 1.7 — broken legacy test base classes)
- 34 dependent legacy test files extending `RaplaTestCase` (Phase 1.7 — never ran since Phase 1.2 anyway)
- `src/test/java/org/rapla/rest/client/resteasy/ResteasyRemoteConnector.java` (Phase 1.7)
- `src/test/java/org/rapla/bootstrap/CustomJettyStarter.java` (Phase 1.7)
- `src/test/java/org/rapla/storage/tests/AbstractOperatorTest.java` (Phase 1.7 — depended on RaplaTestCase)
- POM dependencies removed (Phase 1.8): `org.jboss.resteasy:resteasy-jaxrs:3.15.6.Final`, `org.jboss.resteasy:resteasy-servlet-initializer:3.15.6.Final`, all 11 `org.eclipse.jetty:jetty-*:9.4.58.v20250814` artifacts, transitional `javax.servlet:javax.servlet-api:4.0.1`, `compile-java-9` execution + `<release>9</release>` config, `restinject` annotation processor binding from `maven-compiler-plugin`, the `<jetty.version>` and `<resteasy.version>` properties. Added `org.apache.httpcomponents:httpclient:4.5.14` (was transitive of RESTEasy; needed by `EWSConnector`)
- `src/main/webapp/` (Phase 1.9 — entire directory; static content already mirrored to `src/main/resources/static/` in Phase 1.3)
- `maven-war-plugin` execution from `pom.xml` (Phase 1.9)
- War packaging entry from `src/assembly/rapla.distribution.xml` (Phase 1.9)
- `@GZIP` annotations on `RemoteStorage` interface (Phase 1.8 — RESTEasy-specific; Spring Boot uses `server.compression.enabled=true`)

### What's still alive but dead at runtime

- ~~`restinject 2.0-RC11` library~~ **REMOVED 2026-05-06.** Sources for `org.rapla.scheduler.*`, `org.rapla.logger.*`, `org.rapla.inject.*` (annotations only), and `org.rapla.rest.*` (helpers used by `RaplaSQL` / `MyCustomConnector`) were copied directly from `restinject-2.0-RC11-sources.jar` into the project tree. All copied files migrated to `jakarta.inject.*` / `jakarta.ws.rs.*` namespaces. `<dependency>org.rapla:restinject</dependency>` and the `<restinject.version>` property have been deleted from `parent/pom.xml`. The `restinject` skeletons (`SimpleRaplaInjector`, `ServiceInfLoader`, `RestEasyLoadingFilter`, annotation processor) were **not** copied — they're dead code since Phase 1.5.
- `ClientCreator`, `RaplaClient`, `ClientStarter`, `MainWebclient` — orphaned Swing client bootstrap. Will be migrated to a Swing-side Spring `AnnotationConfigApplicationContext` in Phase 4.

### Phase 0 — completed (additive, no existing runtime touched)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | Java 17 toolchain | `parent/pom.xml` (lines 18, 39–41), `pom.xml` (lines 49–51) | `<java.version>17</java.version>` and `maven.compiler.source/target/release=17` |
| 2 | `module-info.java` deleted | `src/main/java9/module-info.java` (removed), `src/main/java9/` directory (removed) | Filename-based automodule names no longer resolve under Java 17. The `compile-java-9` execution in `parent/pom.xml` is now a no-op (source dir gone) — left in place; will be removed during Phase 1 cleanup. |
| 3 | Spring Boot BOM | `parent/pom.xml` (new `<dependencyManagement>` block) | `spring-boot-dependencies:3.2.5` imported with `<scope>import</scope>`. New property `<spring-boot.version>3.2.5</spring-boot.version>`. |
| 4 | Server-side Spring Boot starters | `parent/pom.xml` (end of `<dependencies>`) | `spring-boot-starter-web` (Tomcat 10 default), `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` (brings `nimbus-jose-jwt` transitively), `spring-boot-starter-test` (test scope) |
| 5 | Spring Boot main class | `src/main/java/org/rapla/server/spring/RaplaSpringBootApplication.java` | `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, SecurityAutoConfiguration.class})` — datasource/security auto-config disabled until they're properly configured in Phase 1+. **Compiles only — not the runtime entry point yet; `MainServlet` still drives production.** |
| 6 | `application.yml` | `src/main/resources/application.yml` | `server.port=8051` (8052 still owned by `RaplaTestCase`), `server.compression.enabled=true`, `server.servlet.context-path=/rapla`, `spring.http.converters.preferred-json-mapper=gson`, root + `org.rapla` log levels |
| 7 | Context-load test | `src/test/java/org/rapla/server/spring/RaplaSpringBootApplicationTest.java` | `@SpringBootTest(classes=RaplaSpringBootApplication.class)` with `assertNotNull(context)`. Verifies the Spring Boot 3.2.5 stack starts without conflicts against the existing javax codebase. |

### Phase 0 — deferred (with reason)

| Item | Reason |
|------|--------|
| `logback-spring.xml` in main resources | Existing `src/test/resources/logback.xml` uses `${jetty.home}` for log file paths — will be rewritten with Spring Boot's `${LOG_PATH}` during Phase 1 cutover. For now Spring Boot's default console logging suffices. |
| Surefire `forkCount=1 reuseForks=false` | Currently `forkCount=0` (parent/pom.xml line 76–79). Single-JVM mode is fine right now because (a) the new Spring Boot test is run in isolation via `-Dtest=`, (b) existing JUnit 4 tests aren't being picked up at all by surefire 3.0.0-M9 — its auto-detected provider is JUnit Platform 5. Forking + JUnit-vintage engine (or JUnit 5 migration of the existing tests) is part of Phase 1 cutover. |
| `maven-dependency-plugin:copy-dependencies` for client `lib/` | Client packaging belongs to Phase 4 (client DI migration); Phase 0 is server-focused. |
| `RaplaServerProperties` `@ConfigurationProperties` | Lives alongside `ServerContainerContext` removal — happens during Phase 1 cutover. |
| Static resource move (`webapp/` → `resources/static/`) | Touches existing code paths; deferred to Phase 1 to keep Phase 0 strictly additive. |

### Build verification

| Command | Result |
|---------|--------|
| `mvn compile -DskipTests` | BUILD SUCCESS — 339 source files compiled with `release 17` |
| `mvn test -Dtest=RaplaSpringBootApplicationTest` | 1 test passing (~6 s context startup, ~12 s end-to-end) |
| `mvn test` (full) | 1 test passing — existing JUnit 4 tests silently skipped by surefire (pre-existing issue, see deferred row above) |

### Files changed in this phase

```
modified:   parent/pom.xml          (+37 lines: Java 17, BOM, 4 starter deps, 1 property)
modified:   pom.xml                 (+3  lines: maven.compiler.release=17 added; source/target=17)
deleted:    src/main/java9/module-info.java
new:        src/main/java/org/rapla/server/spring/RaplaSpringBootApplication.java
new:        src/main/resources/application.yml
new:        src/test/java/org/rapla/server/spring/RaplaSpringBootApplicationTest.java
new:        docs/prd/001-spring-boot-migration.md  (this file)
```

Not yet committed.

### Phase 1.2 — completed (javax.* → jakarta.* namespace migration)

**Completed:** 2026-05-05

Bulk sed rewrote `javax.{inject,servlet,ws.rs}.*` → `jakarta.*` across ~96 files in `src/main/java` + `src/test/java`. POMs updated: `jakarta.inject:jakarta.inject-api:2.0.1`, `jakarta.ws.rs:jakarta.ws.rs-api:3.1.0`, `jakarta.servlet:jakarta.servlet-api:6.0.0` added; `javax.servlet:javax.servlet-api:4.0.1` retained as `provided` so RESTEasy 3.15's `HttpServletDispatcher` resolves until Phase 1.7/1.8 deletes the test stack. `MainServlet.java` (~450 LOC) + `ResteasyExceptionMapper.java` deleted (pulled forward — they compiled only against javax RESTEasy). Three `Provider`-pin classes (`ClientCreator`, `ServerStorageSelector`, `RaplaTestCase`) keep `javax.inject.Provider` at the restinject boundary until restinject is dropped in Phase 1.8.

`mvn compile` BUILD SUCCESS; `mvn test -Dtest=RaplaSpringBootApplicationTest` passes against Tomcat 10.

**Note on coexistence:** `jakarta.servlet:6.0` (Tomcat 10 / Spring Boot 3.2 + production source) and `javax.servlet:4.0.1` (provided, RESTEasy 3.15 compile-time bridge for legacy test stack) live side-by-side until Phase 1.7/1.8.

**javax.* intentionally NOT migrated (Java SE):** `javax.swing.*`, `javax.crypto.*`, `javax.naming.*`, `javax.net.*`, `javax.print.*`, `javax.script.*`, `javax.sql.*`, `javax.xml.*`, `javax.mail.*`

### Phase 1.3 — completed (static content move)

**Date:** 2026-05-05

`src/main/webapp/` content (HTML, CSS, JS libs, images, `webclient/`, `jsclient/`, JNLP descriptors under `Rapla/`) **copied** to `src/main/resources/static/` so Spring Boot serves it from the classpath. The original `src/main/webapp/` directory is retained for now — the `maven-war-plugin` in `pom.xml` still references it and will be removed in Phase 1.4 along with `web.xml` deletion.

| Verification | Result |
|--------------|--------|
| `ls src/main/resources/static/` | `Rapla apiTest.html bootstrap.min.css calendar.css default.css export.css images jsclient login.css rapla.css rapla.html redirect.html webclient` |
| `mvn compile -DskipTests` | BUILD SUCCESS, 911 resources copied (was 840 before — the new static/ adds ~71 files) |

### Phase 1.4 — partial (RaplaServerProperties wired; bridge bean stubbed but inactive)

**Date:** 2026-05-05

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `RaplaServerProperties` `@ConfigurationProperties` | `src/main/java/org/rapla/server/spring/RaplaServerProperties.java` (new) | `prefix="rapla"`. Holds `Map<String, DataSourceProperties> dbDatasources`, `Map<String, String> fileDatasources`, `Map<String, Boolean> services`, `String mailSession`, `String patchScript` — replaces `ServerContainerContext` field-by-field as Phase 1.5 migrates collaborators. |
| 2 | `@EnableConfigurationProperties` | `src/main/java/org/rapla/server/spring/RaplaSpringBootApplication.java` | Activates the properties class. |
| 3 | Test assertion | `src/test/java/org/rapla/server/spring/RaplaSpringBootApplicationTest.java` | New `serverPropertiesBound()` test verifies `RaplaServerProperties` is autowired and its maps are non-null after binding. |
| 4 | `LegacyServerBridgeConfig` `@Configuration` (stub, gated off) | `src/main/java/org/rapla/server/spring/LegacyServerBridgeConfig.java` (new) | Exposes `@Bean Logger`, `@Bean ServerContainerContext` (translated from `RaplaServerProperties`), and `@Bean ServerServiceContainer` (calls `ServerCreator.create(...)`). The `ServerServiceContainer` bean is gated by `@ConditionalOnProperty("rapla.bridge.enabled")` — **off by default and intentionally so**, see "Bridge approach is non-viable" below. The `Logger` and `ServerContainerContext` beans remain useful as foundation for native-Spring migration in Phase 1.5. |

| Verification | Result |
|--------------|--------|
| `mvn test -Dtest=RaplaSpringBootApplicationTest` | 2 tests passing (~5 s startup) |

#### Bridge approach non-viable — recorded for posterity

The bridge integration test (`LegacyServerBridgeIntegrationTest`) failed because Phase 1.2's jakarta migration rewrote every `@javax.inject.Inject` to `@jakarta.inject.Inject`, but `restinject 2.0-RC11`'s `SimpleRaplaInjector` only scans for `@javax.inject.Inject`. **The legacy DI container is dead at runtime after Phase 1.2** — no class carries the annotation it's looking for. Rejected alternatives: forking restinject for jakarta support (dead-end), or re-adding `@javax.inject.Inject` alongside on ~275 sites (pointless duplication). Decision: migrate forward to native Spring DI — Spring 6's JSR-330 honours `jakarta.inject.Inject` directly. The bridge bean is kept (gated off) only as a source of `Logger` + `ServerContainerContext` definitions for the native-Spring migration.

Knock-on: Phase 1.5 must migrate leaf-first (no `@Inject` deps) and grow outward, since the legacy graph can't bootstrap collaborators piecewise. This keeps the smoke test green at every commit.

### Phase 1.5 — completed (native Spring DI, leaf-first)

**Date:** 2026-05-05

Six steps landed leaf-first, each verified by a smoke-test bean assertion:

- **Step 1 — `ServerCoreConfig`** registers `BundleManager` (`ServerBundleManager`), `TimeZoneConverter`, `RaplaResources`, `RaplaSystemInfo` as `@Bean` factories.
- **Step 2** adds `RaplaLocale` (`RaplaLocaleImpl`) and `CommandScheduler` (`DefaultScheduler(logger, tz)`).
- **Step 3** adds `RemoteLogger` (`RemoteLoggerImpl`) via `AutowireCapableBeanFactory.autowireBean(impl)` to populate the legacy `@Inject Logger logger` field.
- **Step 4** adds `PromiseWait` (later deleted in Phase 2), `FunctionFactory(name="org.rapla")`, `PermissionExtension`, `ServerStorageSelector` (8-arg, datasource consulted lazily inside `get()`), `RaplaFacade` (`FacadeImpl`).
- **Step 5** adds `ServerServiceConfig` (gated `@ConditionalOnProperty(prefix="rapla.file-datasources", name="raplafile")`) with `CachableStorageOperator` and `ServerServiceContainer` (12-arg; `ObjectProvider::getIfAvailable` wraps `Provider<Map<...>>` / `Provider<Set<...>>` into jakarta-Provider lambdas). `ServerServiceIntegrationTest` boots the container end-to-end against a `@TempDir`-copied `testdefault.xml`.
- **Step 6** deletes the bridge `ServerServiceContainer` bean (kept the `Logger` + `ServerContainerContext` beans as canonical providers).

**Why `@Bean` factories not `@Service`:** non-invasive (no diff in `org.rapla.RaplaResources` etc.); legacy `@DefaultImplementation` annotations are dead-but-harmless after Phase 1.2. A final `@Service` + `@ComponentScan` sweep would be a follow-up after restinject removal.

**Pattern note:** field-injected legacy classes can be Spring-managed without source changes via `AutowireCapableBeanFactory.autowireBean(instance)` inside the `@Bean` factory — Spring 6 honours `jakarta.inject.Inject` on fields under this post-processor.

After step 5 the legacy DI graph (`ServerCreator` → `SimpleRaplaInjector` → `ServerServiceImpl`) is fully replaced by Spring for the server core: storage connect, facade wiring, preference loading, and timezone resolution all run under Spring control.

### Phase 1.6 — additional steps 5–11 (completed)

Each step adds a tier of Spring beans + `@RestController`s; full quick-reference is at the top of the file. Highlights and patterns worth keeping:

- **Step 5 — Mail tier.** `MailInterface` (`MailapiClient` wrapping `containerContext.getMailSession()` as `Provider<Object>`), `MailToUserImpl`, `MailToUserController` (`POST /mail/send`).
- **Step 6 — Locale (first `@RequestScope` use).** `ResourceBundleList`, `RemoteLocaleService` (request-scoped, `autowireBean` populates 6 `@Inject` fields), `RemoteLocaleController`. Wraps `Promise<T>` via `SynchronizedCompletablePromise.waitFor(promise, 10000, null)` at the HTTP boundary.
- **Step 7 — Data-API request-scoped beans.** `AppointmentFormater`, `SecurityManager`, plus `RaplaResourcesRestPage` / `RaplaDynamicTypesRestPage` / `RaplaEventsRestPage` all `@RequestScope` + `autowireBean`.
- **Step 8 — Data-API controllers.** `RaplaResourcesController` (CRUD on `/resources`), `RaplaEventsController` (CRUD + `@PatchMapping` on `/events`, date params use `@DateTimeFormat(iso=DATE_TIME)`), `RaplaDynamicTypesController` (read-only on `/dynamictypes`). Spring MVC's `@RequestParam List<String>` accepts both repeated and comma-separated formats — no `@QueryParam` translation hassle.
- **Step 9 — RemoteStorage tier.** `ShutdownService`, `UpdateDataManager`, `RemoteStorage` (request-scoped + autowireBean for 9 `@Inject` fields incl. `Provider<MailInterface>`; Spring 6 honours JSR-330 `Provider` lookup). The `@RestController` wrapper is deferred to Phase 5 — better generated from the same `@HttpExchange` interface than hand-rolled (dozens of CRUD + dispatch/sync methods).
- **Step 10 — Legacy URL-path controllers (HARD CONSTRAINT preserved).** `Export2iCalController` (`/ical`, `/internal_ical`), `RaplaJNLPController` (`/raplaclient`, `/raplaclient.jnlp`), `CalendarPageController` (`/calendar`, `/calendar.csv`, `/internal_calendar(.csv)`). `ServletRequestPreprocessorFilter` (a `OncePerRequestFilter`) bridges the legacy `ServletRequestPreprocessor` extension point — the `?key=…` URL-encryption decrypt is still applied in-flight. `urlEncryptionPreprocessor` bean is `@Lazy` to break a circular dep through `RemoteSession` → `TokenHandler` → `RaplaKeyStorage` → `serverServiceContainer` → `Set<ServletRequestPreprocessor>`.
- **Step 11 — Plugin REST controllers.** `UrlEncryptionController` (`/urlencryption`), `ArchiverController` (`/archiver` + `/backup` / `/restore`), `ICalImportController` (`/ical/import`), `JNDIConfigController` (`/jndi`), `MailConfigController` (`/mail/config(/external)?`). All gated by `@ConditionalOnBean({Service.class, RemoteSession.class})` so `rapla.services.<plugin-id>=false` removes them cleanly. `Promise`-returning service methods are unwrapped via `SynchronizedCompletablePromise.waitFor` at the controller boundary.

**Phase 1.6 endpoint-migration complete** — every JAX-RS `@Path` endpoint has a Spring `@RestController` counterpart at the same path.

### Phase 4 step 2 — Facade tier in `ClientConfig` (completed)

Three beans added: `CommandScheduler` (`new DefaultScheduler(logger)` — single-arg client ctor, no `TimeZoneConverter`), `RaplaFacade` (`new FacadeImpl(i18n, scheduler, logger)`), `ClientFacade` (`new ClientFacadeImpl(raplaFacade, logger, i18n)`).

### Phase 5 step 2 + 4–6 — Bearer-auth + `@HttpExchange` interfaces (completed 2026-05-06)

A single shared `HttpServiceProxyFactory` with a `RestClient.Builder.requestInitializer(...)` that reads `RemoteConnectionInfo.getAccessToken()` and applies `setBearerAuth(token)` (skipped if null/empty). Adding a remote service is one `@Bean factory.createClient(InterfaceClass.class)` line.

**All shared service interfaces converted from JAX-RS to Spring `@HttpExchange`** (11 in step 4, plus `RemoteAuthentificationService` and the 404-LOC `RemoteStorage` interface in steps 5–6). `RemoteStorage` was bulk-converted via a regex Python script (`@Path("X")` → `@HttpExchange("/X")`, multi-line `@GET\n@Path` → `@GetExchange`, `@QueryParam` → `@RequestParam(required=false)`, `@PathParam` → `@PathVariable`, `@Produces`/`@Consumes` stripped). 13 proxy beans registered in `ClientProxyConfig`. Server-side `@RestController`s are unaffected — they own their own `@RequestMapping` annotations.

### Phase 9 — completed (Jackson default + Gson removal)

**Step 1 (2026-05-06)** — `spring.http.converters.preferred-json-mapper=gson` removed from `application.yml`; Spring Boot 3.x defaults to Jackson with `spring-boot-starter-web`. Wire format is largely compatible (both serialize `java.util.Date` as ISO-8601 by default).

**Step 2 (2026-05-08)** — Jackson default swap + full Gson removal landed alongside the Jackson 2 → Jackson 3 cutover under [PRD 011](011-spring-boot-4-jackson-3.md):
- `rapla-core/pom.xml` declared `jackson-datatype-jsr310` (later dropped — Jackson 3 has built-in `java.time.*`); `JacksonParserWrapper.defaultObjectMapper()` registers `JavaTimeModule` + disables `WRITE_DATES_AS_TIMESTAMPS`. `JsonParserWrapper.factory` defaults to `JacksonParserWrapper` (was `GsonParserWrapper`).
- `gson` dropped from `rapla-bom/pom.xml`. Final Gson consumers (`HTTPWithJsonConnector`, `HTTPWithJsonMailConnector`, `MailapiClient`, `JacksonMergePatch` (renamed from `JsonMergePatch`), `RestAPIExample`) migrated to Jackson API.
- Stale `gson`-named locals/fields renamed (`mapper`, `parser`, etc.) across `JacksonParserWrapper`, `JavaJsonSerializer`, `EntityHistory`, `NotificationStorage`, `RaplaSQL`, `LocalAbstractCachableOperator`. `ExchangeAppointmentStorage` deferred pending parallel Exchange-connector session.
- Reactor `mvn test` green: 23 spring tests + 18 rapla-server tests (incl. `TestEntityHistory`, `ConcurrentTests` exercising SQL serialization) + all rapla-core tests. SQL history JSON entity blobs round-trip byte-identical between Gson and Jackson (field-by-field with ISO-8601 dates).

PRD 001-A (Date → LocalDateTime) is independent — Jackson 3's native `java.time.*` support unblocked the migration.

#### Step 3 — Full REST proxy bean set (completed)

11 proxies in `ClientProxyConfig`: `ICalTimezones` (`/ical/timezones`), `RemoteLocaleService` (`/locale`), `ICalConfigService` (`/ical/config`), `MailToUserInterface` (`/mail/send`), `MailConfigService` (`/mail/config`), `ArchiverService` (`/archiver`), `UrlEncryption` (`/urlencryption`), `JNDIConfig` (`/jndi`), `ICalImport` (`/ical/import`), `TemplateImport` (`/templateimport`), `RemoteLogger` (`/logger`). Each is one `@Bean factory.createClient(...)` line on the shared bearer-auth `HttpServiceProxyFactory`. `RemoteAuthentificationService` + `RemoteStorage` added later (step 6 above).

### Phase 4 step 3 — `SpringRaplaClient` bootstrap class

| File | Detail |
|------|--------|
| `src/main/java/org/rapla/client/spring/SpringRaplaClient.java` (new) | `AutoCloseable` bootstrap that wraps `AnnotationConfigApplicationContext(ClientConfig.class, ClientProxyConfig.class)` and exposes `ClientFacade`. Replaces `new RaplaClient(env)` for callers that don't need the legacy `restinject` graph. |
| `src/test/java/org/rapla/client/spring/SpringRaplaClientTest.java` (new) | Verifies the standalone Spring client boots, produces a working `ClientFacade`, and registers `ICalTimezones`/`RemoteLocaleService`/`MailToUserInterface` proxy beans. |

`mvn test` → **23 tests passing**.

**Phase 4 status:** the Spring-based client bootstrap is in place. The full Swing UI graph (`RaplaClientServiceImpl` and dozens of Swing components) is still field-injected via `@Inject` annotations, but has no runtime caller path — the legacy entries that bootstrapped it are all deleted (`RaplaClient`, `MainWebstart`, `MainWebclient`, `MainApplet`, `ClientCreator`, `examples/`). The Swing UI graph is dead code until Phase 4 step 4 wires `RaplaClientServiceImpl` and its UI tier into `ClientConfig`.

#### Step 5 — Client storage tier wired (completed 2026-05-06)

| Bean | Detail |
|------|--------|
| `Map<String, FunctionFactory> functionFactoryMap` | Empty map (client-side function factories are plugin-loaded, none required for the smoke test). |
| `Set<PermissionExtension> permissionExtensions` | Single-element set containing `RaplaDefaultPermissionImpl`. |
| `RaplaLock raplaLock(Logger)` | `new DefaultRaplaLock(logger)`. |
| `StartupEnvironment startupEnvironment(Logger)` | Anonymous impl returning `CONSOLE` startup mode. |
| `RemoteOperator remoteOperator(...)` | The client-side storage operator. 10 constructor params: `Logger, RaplaResources, RaplaLocale, CommandScheduler, Map<String, FunctionFactory>, RemoteAuthentificationService, RemoteStorage, RemoteConnectionInfo, Set<PermissionExtension>, RaplaLock`. The two service proxies (`RemoteAuthentificationService`, `RemoteStorage`) come from `ClientProxyConfig`'s `HttpServiceProxyFactory`-generated beans. |

`ClientConfigTest` now loads `ClientConfig.class + ClientProxyConfig.class` and verifies all the bean lookups. `mvn test` → 23 tests passing.

**The client-side Spring DI graph is now functionally bootable** — given a server URL + access token in `RemoteConnectionInfo`, `RemoteOperator` would call the right HTTP endpoints via Spring proxies. The remaining Swing UI graph (`RaplaClientServiceImpl` + dozens of Swing presenters/views) is decoupled from this — it's an entirely separate pyramid that adds visualisation on top.

##### Why the rest of Phase 4 is its own follow-up PRD

The Swing UI graph is voluminous: ~281 client files (142 under `swing/`), 32 `@DefaultImplementation` + 58 `@Extension` classes. Top-level `RaplaClientServiceImpl` has 13 ctor params, `Application` 12; `DialogUiFactoryInterface` / `ApplicationView` / `AbstractActivityController` each pull 5–10 more. `Map<String, T>` / `Set<T>` / `Provider<T>` extension-point injections need `@Named("id")` registration per bean.

**PRD 002 (`docs/prd/done/002-swing-spring-di.md`) handles the mechanical sweep — created 2026-05-06, completed 2026-05-08.** `SpringRaplaClient` wires `ClientConfig + ClientProxyConfig + SwingClientConfig`; the client storage tier (`ClientConfig` + `RemoteOperator` + 13 `@HttpExchange` proxies) is the supported bootstrap path for any new client consumer.

#### Step 4 — Phase 4 mass deletion (completed)

Deleted: `RaplaClient`, `MainWebstart`, `MainWebclient`, `MainApplet` (`javax.swing.JApplet` gone in Java 21), `ClientCreator`, 3 example classes (`RaplaConnectorTest`, `RaplaImportUsers`, `SimpleConnectorStartupEnvironment`) + 2 example data XMLs + empty `src/main/java/org/rapla/examples/`.

Side effects: `RaplaJNLPPageGenerator` `<application-desc main-class="…">` now points at `org.rapla.client.spring.SpringRaplaClient` (JNLP URL `/raplaclient.jnlp` preserved). `restinject`'s runtime API has no remaining importers.

### Phase 8 — extended deletions

Also deleted: `RestApplication` (legacy JAX-RS `Application`, no callers since Phase 1.2's `MainServlet` removal), `ClientStarter` (wrapped `ClientCreator`), `PromiseWait` + `PromiseWaitImpl` (Phase 2 — abstraction inlined).

**Cumulative deletions: ~65 source files** + `src/main/webapp/` + `examples/` directories. Full list in the "What's deleted" header section at the top of the file.

### Phase 2 — complete (Promise-wait removal)

| # | Item | Detail |
|---|------|--------|
| 1 | Call sites inlined | All 12 `xxx.waitForWithRaplaException(promise, timeoutMs)` callers rewritten to `org.rapla.scheduler.sync.SynchronizedCompletablePromise.waitFor(promise, timeoutMs, logger)`. Files: `RaplaEventsRestPage`, `RemoteStorageImpl`, `Export2iCalServlet` (×2 — incl. `getLastModified`), `AbstractHTMLCalendarPage`, `RaplaICalImport`, `AppointmentTableViewPage`, `ReservationTableViewPage`, `AppointmentPerDayViewPage`, `SecurityManager` (×2). |
| 2 | Interface method removed | `CachableStorageOperator.waitForWithRaplaException(...)` deleted. |
| 3 | Implementation removed | `LocalAbstractCachableOperator.waitForWithRaplaException(...)` + `PromiseWait` field + constructor parameter — gone. Cascades: `FileOperator(Logger, ...)` and `DBOperator(Logger, ...)` constructors lost the `PromiseWait` argument. `ServerStorageSelector` constructor + factory methods updated. |
| 4 | `@Inject PromiseWait` fields removed | From `RaplaEventsRestPage`, `Export2iCalServlet`, `AbstractHTMLCalendarPage`, `RaplaICalImport`. |
| 5 | Constructor parameters removed | From `ReservationTableViewPage`, `AppointmentPerDayViewPage`, `AppointmentTableViewPage` — they took `PromiseWait` as the first parameter. |
| 6 | Spring config simplified | `ServerCoreConfig` lost the `@Bean PromiseWait promiseWait(Logger)` method and the `serverStorageSelector` factory's `PromiseWait` parameter. |
| 7 | Files deleted | `org.rapla.server.PromiseWait` (interface), `org.rapla.server.internal.PromiseWaitImpl` (HMAC `SynchronizedCompletablePromise.waitFor` wrapper). |
| 8 | Exception adaptation | `SynchronizedCompletablePromise.waitFor` throws `Exception` (not `RaplaException`); callers wrap in `try { … } catch (RaplaException ex) { throw ex; } catch (Exception ex) { throw new RaplaException(ex); }` where the calling method only declares `throws RaplaException`. Where the caller already throws `Exception` (e.g., `RaplaEventsRestPage.list`) no wrapping is needed. |

`mvn test` → **23 tests still passing**.

**Significance:** the `Promise<T>`-on-server-but-immediately-awaited pattern (which existed only because the interface was shared with the async client `RemoteOperator`) is gone. The server side is now fully synchronous at the storage call boundary; async wrapping is the client's responsibility (RxJava in `RemoteOperator`).

### Phase 6 — completed (plugin auto-config)

**Step 1 — FunctionFactory tier.** `AppointmentNoteFunctions(@Bean(name="appointment"))` and `DurationFunctions(@Bean(name="org.rapla.eventtimecalculator"))` wired alongside existing `StandardFunctions` ("org.rapla"). `DurationFunctions` brings `EventTimeCalculatorResources(BundleManager)` and `EventTimeCalculatorFactory`. `Map<String, FunctionFactory>` injection point populated with all 3 namespaces.

**Pattern for `@Inject Provider<X>` ctor params:** Spring's `ObjectProvider<X> p` → `(jakarta.inject.Provider<X>) p::getObject` — late-bound, dodges circular-dep issues.

**Step 2 — Plugin service tier.** Named mail-session `Provider<Object>` (`@Bean(name=ServerService.ENV_RAPLAMAIL_ID)`) consumed by `MailInterface` via `@Named` qualifier; `UrlEncryptor`, `UrlEncryption`, `JNDIConfig`, `ImportExportManager`, `ArchiverService`, `MailConfigService`, `ICalConfigService` all registered (request-scoped where stateful, all using `autowireBean` for `@Inject` fields).

**Step 3 — Plugin enable/disable via Spring Boot's standard `@ConditionalOnProperty(prefix="rapla.services", name="<plugin-id>", matchIfMissing=true)`** — no `RaplaPluginImportSelector` needed. Plugin IDs match legacy `raplaservices` keys (package convention). Properties: `rapla.services.org.rapla.plugin.{urlencryption,jndi,archiver,mail,export2ical}=false` toggles `UrlEncryption`, `JNDIConfig`, `ArchiverService`, `MailConfigService`, `ICalConfigService` respectively.

### Phase 3 — completed (security infrastructure)

Seven steps land JWT bearer auth end-to-end:

- **Step 1 — Spring Security stub + CORS.** `SecurityAutoConfiguration` re-enabled. `SecurityConfig.filterChain` permits all (placeholder), CSRF disabled, stateless, CORS enabled with permissive `CorsConfigurationSource`.
- **Step 2 — JWT scaffolding.** `JwtConfig` provides `JwtDecoder` (`NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)`) and `JwtIssuer` (wraps `MACSigner` from `nimbus-jose-jwt`). Secret derived from `RaplaKeyStorage.getRootKeyBase64()` (right-padded to 32 bytes for HS256 minimum). `AuthController` exposes `POST /auth/login` returning `{accessToken, expiresIn}`.
- **Step 3 — `SecurityConfig` wires `oauth2ResourceServer.jwt(decoder)`** when `ObjectProvider<JwtDecoder>` is available. Permit-all matchers added for `/auth/**`, `/static/**`, `/Rapla/**`, `/images/**`, `/webclient/**`, `/jsclient/**`, `/logger/**`, `/ical/timezones/**`.
- **Step 4 — `AuthControllerIntegrationTest`** verifies `POST /auth/login` end-to-end. Conditional gating swapped from `@ConditionalOnBean` (ordering issues) to `@ConditionalOnProperty(prefix="rapla.file-datasources", name="raplafile")` matching `ServerServiceConfig`. **Worked-bug fix:** `RaplaKeyStorage.getRootKeyBase64()` returns URL-safe base64 (uses `-` and `_`), so `JwtConfig.deriveHmacSecret` falls back from `Base64.getDecoder()` to `Base64.getUrlDecoder()` on `IllegalArgumentException`.
- **Step 5 — Refresh-token rotation.** `JwtIssuer.issueRefreshToken(...)`; all tokens now carry `typ` (`"access"`/`"refresh"`) and `jti` (UUID) claims. `POST /auth/refresh` validates `typ == "refresh"`, issues a new pair (new `jti`).
- **Step 6 — `SecurityFilterChain` enforces `anyRequest().authenticated()`** once a `JwtDecoder` is available (falls back to permit-all in smoke-test mode). `GET /resources` without JWT now returns 401.
- **Step 7 — `SpringSecurityRemoteSession`** implements `RemoteSession`. `checkAndGetUser(request)` first inspects `SecurityContextHolder` for a `JwtAuthenticationToken` (resolves `jwt.getSubject()` → `User` via `StorageOperator`); otherwise delegates to wrapped legacy `RemoteSessionImpl` (header/cookie/query-param) for backwards compatibility. Bearer round-trip verified end-to-end (`POST /auth/login` → capture `accessToken` → `GET /resources` with `Authorization: Bearer <jwt>` → 200).

Eventual cleanup: `@RestController`s should switch from `RemoteSession.checkAndGetUser(request)` to `@AuthenticationPrincipal User user` via a `JwtAuthenticationConverter` — code tidiness, not functional.

### Phase 4 — partial (client-side Spring DI skeleton)

#### Step 1 — `ClientConfig` skeleton (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `ClientConfig` `@Configuration` | `src/main/java/org/rapla/client/spring/ClientConfig.java` (new) | Standalone Spring `@Configuration` for the Swing client side. Boots via `AnnotationConfigApplicationContext` (NO Spring Boot — the Swing client is not a webapp). Currently registers `Logger` (`RaplaBootstrapLogger`), `BundleManager` (`SwingBundleManager`), `RaplaResources`, `RaplaSystemInfo`, `RaplaLocale`. |
| 2 | `ClientConfigTest` | `src/test/java/org/rapla/client/spring/ClientConfigTest.java` (new) | Standalone test (NO `@SpringBootTest`) — `new AnnotationConfigApplicationContext(ClientConfig.class)`, asserts all 5 client beans resolve. **Demonstrates that the client-side Spring DI graph compiles and bootstraps independently of the server.** |

`mvn test` → 14 tests passing.

**Status of Phase 4:** the foundation for replacing `ClientCreator` + `SimpleRaplaInjector` is in place. Adding the rest of the client beans (Swing UI components, `ReservationControllerImpl`, `RemoteOperator`, etc.) is mechanical — each is a `@Bean` factory in `ClientConfig` (or its own `@Configuration` per Swing module). The biggest remaining task is replacing `MyCustomConnector` + generated `_JavaJsonProxy` classes with `HttpServiceProxyFactory` proxies (Phase 5).

**Update 2026-05-08 — Phase 4 substantially complete via PRD 002.** PRD 002 (Swing UI Spring DI Migration) is the implementation track for Phase 4's "rest of the client beans" item. As of 2026-05-08:
- `SpringRaplaClient` boots end-to-end against `AnnotationConfigApplicationContext(ClientConfig, ClientProxyConfig, SwingClientConfig, EditTaskPresenterConfig, PluginResourcesConfig)`.
- ~50 core Swing beans wired (`@Service` + `@Lazy` for boot-state classes, `@Scope("prototype")` for action/dialog classes).
- 43 plugin extensions wired (calendar view factories, plugin option panels, menu factories, function factories, publish extension factories, summary extensions, annotation editors). All `Set<T>` injection points populated; `Map<String, T>` qualifier-keyed maps populated for `TaskPresenter`, `EditComponent`, `PluginOptionPanel`, `FunctionFactory`.
- `RaplaClientServiceImpl` (`ClientService` impl) is `@Service @Lazy` and resolves end-to-end on first dereference.
- Phase 5 (REST proxies) substantially complete: 16 `@HttpExchange` REST proxies wired in `ClientProxyConfig` (was 11, added `ICalExport`, `ExchangeConnectorRemote`, `ExchangeConnectorConfigRemote` after JAX-RS → `@HttpExchange` interface conversion 2026-05-08).
- 2 unwired plugin extensions (`CalendarTableViewPresenter`, `CalendarWeekViewPresenter`) remain — both depend on `CalendarTableView`/`CalendarWeekView` interfaces with no implementation registered in the codebase; dead code unless someone implements those views.

### Phase 5 — partial (REST client proxies via `HttpServiceProxyFactory`)

#### Step 1 — `ClientProxyConfig` stub (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `ClientProxyConfig` `@Configuration` | `src/main/java/org/rapla/client/spring/ClientProxyConfig.java` (new) | Demonstrates the proxy pattern: `RestClient.builder().baseUrl(info.getServerURL()).build()` → `HttpServiceProxyFactory.builderFor(RestClientAdapter.create(...)).build().createClient(InterfaceClass.class)`. Exemplified for `ICalTimezones` (read-only, unauthenticated). |
| 2 | `RemoteConnectionInfo` bean | `ClientProxyConfig.remoteConnectionInfo()` | Existing class, registered as a bean — holds server URL + access token. Bearer-auth interceptor (TODO Phase 5 step 2) will read `info.getAccessToken()` and add `Authorization: Bearer <jwt>` to every outgoing request, plus a `ResponseErrorHandler` that retries via `/auth/refresh` on 401. |

`mvn test` → 14 tests passing (the proxy bean is registered but not yet invoked end-to-end against a running server; an end-to-end client-server round-trip test would spin up `@SpringBootTest(webEnvironment=RANDOM_PORT)` and have the proxy hit the real port — TODO).

**Status of Phase 5:** pattern established. Each remote service interface (`RemoteStorage`, `RemoteLocaleService`, `RemoteAuthentificationService`, plugin endpoints) follows the same recipe. The interfaces currently use JAX-RS `@Path`/`@GET` annotations; they need to be either (a) replaced with Spring's `@HttpExchange`/`@GetExchange`, or (b) mapped via Spring's `JaxrsHttpExchangeAdapter` (Spring 6.1+). Server side already implements the right routes (Phase 1.6 `@RestController`s), so the client just needs the interface annotations to align.

**Update 2026-05-08 — Phase 5 substantially complete.** Approach (a) chosen — JAX-RS `@Path` interfaces converted to `@HttpExchange` per-interface. 16 REST proxies now wired in `ClientProxyConfig`:

| Interface | URL path | Status |
|-----------|----------|--------|
| `ICalTimezones` | `/ical/timezones` | ✓ |
| `RemoteLocaleService` | `/locale` | ✓ |
| `ICalConfigService` | `/ical/config` | ✓ |
| `MailToUserInterface` | `/mail/send` | ✓ |
| `MailConfigService` | `/mail/config` | ✓ |
| `ArchiverService` | `/archiver` | ✓ |
| `UrlEncryption` | `/urlencryption` | ✓ |
| `JNDIConfig` | `/jndi/config` | ✓ |
| `ICalImport` | `/ical/import` | ✓ |
| `TemplateImport` | `/templateimport` | ✓ |
| `RemoteLogger` | `/logger` | ✓ |
| `RemoteAuthentificationService` | `/authentication` | ✓ |
| `RemoteStorage` | `/storage` | ✓ |
| `RestartServer` | `/restart` | ✓ |
| `ICalExport` | `/ical/export` | ✓ (2026-05-08, JAX-RS → `@HttpExchange` conversion) |
| `ExchangeConnectorRemote` | `/exchange/connect` | ✓ (2026-05-08) |
| `ExchangeConnectorConfigRemote` | `/exchange/config` | ✓ (2026-05-08) |

All major remote service interfaces are now Spring-proxy-wired. The corresponding `@RestController` server-side counterparts exist for the original 14 (Phase 1.6 work); the 3 newly-converted interfaces (`ICalExport`, `ExchangeConnectorRemote`, `ExchangeConnectorConfigRemote`) had no Spring `@RestController` (the prior JAX-RS annotations were vestigial — no `JerseyServlet` was registered) — server-side `@RestController`s for those are deferred work; the client proxies are wired for when the server side lands.

### Phase 1.6 — in-progress (REST endpoints to Spring MVC)

#### Step 4 — ICalConfigController (HttpServletRequest endpoint pattern) (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `ICalConfigController` | `src/main/java/org/rapla/server/spring/web/ICalConfigController.java` (new) | `@RestController @ConditionalOnBean(RemoteSession.class) @RequestMapping("/ical/config")`. Two `@GetMapping`s — `getConfig` (admin-only) and `getUserDefaultConfig` (any authenticated user). Takes `HttpServletRequest` as method parameter (Spring MVC supports this natively), passes it to `RemoteSession.checkAndGetUser(request)` for session check. |
| 2 | `@ConditionalOnBean(RemoteSession.class)` | controller annotation | Smoke test (no datasource) doesn't trigger `RemoteSession` bean creation, so the controller is gated off — no `@Autowired` failures. Integration test (with datasource) wires the controller. |

`mvn test` → 12 tests still passing.

**Pattern established for HttpServletRequest endpoints:** `@RestController` takes `RemoteSession` + `RaplaFacade` (or other beans) via constructor, `HttpServletRequest` as method parameter. Original `@DefaultImplementation` impl class becomes redundant once all of its callers go through the controller — can be deleted in Phase 8 cleanup.

#### Step 3 — Auth/session chain wired (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `RaplaKeyStorage` bean | `ServerServiceConfig.raplaKeyStorage(RaplaFacade, Logger)` | Returns `new RaplaKeyStorageImpl(facade, logger)`. |
| 2 | `TokenHandler` bean | `ServerServiceConfig.tokenHandler(RaplaKeyStorage, CachableStorageOperator)` | Direct constructor — `CachableStorageOperator` satisfies the `StorageOperator` parameter via interface inheritance. |
| 3 | `RaplaAuthentificationService` bean | `ServerServiceConfig.raplaAuthentificationService(AutowireCapableBeanFactory)` | Empty constructor + 5 `@Inject` fields populated via `autowireBean`. |
| 4 | `Set<AuthenticationStore>` bean | `ServerServiceConfig.authenticationStores()` | Returns `Collections.emptySet()` — no auth stores configured by default. |
| 5 | `RemoteSession` bean | `ServerServiceConfig.remoteSession(Logger, TokenHandler, RaplaAuthentificationService)` | Returns `new RemoteSessionImpl(...)` via the 3-arg constructor. |
| 6 | Integration assertion | `ServerServiceIntegrationTest.authChainResolves()` | Asserts all 4 auth/session beans `@Autowired` non-null. |

`mvn test` → 12 tests passing.

This unblocks Phase 1.6 step 4+ — endpoints that depend on `RemoteSession` (most `@Inject HttpServletRequest` JAX-RS endpoints can now be migrated to Spring `@RestController` with `RemoteSession` injected).

#### Step 2 — ICalTimezones controller (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `ICalTimezones` bean | `ServerCoreConfig.iCalTimezones(AutowireCapableBeanFactory)` | `RaplaICalTimezones` instance with `autowireBean` for the `@Inject TimeZoneConverter` field. |
| 2 | `ICalTimezonesController` | `src/main/java/org/rapla/server/spring/web/ICalTimezonesController.java` (new) | `@GetMapping` on `/ical/timezones` and `/ical/timezones/default`. Delegates to bean. |
| 3 | `ICalTimezonesControllerTest` | `src/test/java/org/rapla/server/spring/web/ICalTimezonesControllerTest.java` (new) | MockMvc verifies both endpoints — array response on root, string response on `/default`. |

`mvn test` → 11 tests passing across 4 contexts.

#### Step 1 — RemoteLogger via Spring `@RestController` (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `RemoteLoggerController` | `src/main/java/org/rapla/server/spring/web/RemoteLoggerController.java` (new) | `@RestController @RequestMapping("/logger")` with `@PutMapping("/{id}")`. Delegates to the `RemoteLogger` bean (which is `RemoteLoggerImpl` from `ServerCoreConfig`). |
| 2 | `RemoteLoggerControllerTest` | `src/test/java/org/rapla/server/spring/web/RemoteLoggerControllerTest.java` (new) | `@SpringBootTest @AutoConfigureMockMvc` — `mockMvc.perform(put("/logger/some-client-id"))` returns 200. Server log shows `RemoteLoggerImpl.info rapla.some-client-id - hello from client` proving end-to-end dispatch. |

| Verification | Result |
|--------------|--------|
| `mvn test -Dtest='RaplaSpringBootApplicationTest,ServerServiceIntegrationTest,RemoteLoggerControllerTest'` | 9 tests passing across 3 contexts. MockMvc verifies routing + dispatcher + bean wiring. |

**Endpoint migration pattern established:** create a Spring `@RestController` in `org.rapla.server.spring.web` that delegates to the existing service interface bean. The JAX-RS interface (`@Path`/`@PUT` etc.) stays untouched — eventually it'll be removed in the cleanup pass. This keeps RESTEasy 3.15 + Spring MVC coexisting on the same classpath; only Spring's `DispatcherServlet` is wired into the Spring Boot servlet container, so RESTEasy routes are dead at runtime even though they still compile.

### Major milestone — Phase 1 complete

Structural goal achieved: legacy `MainServlet` + RESTEasy + Jetty 9 stack fully removed; server core boots on Spring Boot + Tomcat 10 with Spring DI; build produces clean artifact set. Every test exercises Spring-managed code end-to-end including a real `FileOperator.connect()` and MockMvc dispatch.

### Phase 1.6 step 8 — Index & Status page controllers (completed)

`IndexPageController` (`/`, `/index`) and `StatusPageController` (`/server`) — both `@RestController @ConditionalOnBean(RemoteSession.class)`, delegating to `RaplaIndexPageGenerator` / `RaplaStatusPageGenerator` (`@Bean` factories with `autowireBean` for `@Inject` fields incl. `Map<String, HtmlMainMenu>`). Three `HtmlMainMenu` beans registered with names matching legacy extension IDs (`1_jnlp`, `3_status`, `exportedcalendars`) so `ServerContainerContext.isServiceEnabled(key)` checks still work. Datasource wired via `rapla.file-datasources.raplafile: data/data.xml`. `SecurityConfig` permit-all list expanded.

## Future Constraints (deferred to a later phase)

### Constructor injection only — no field injection

**Status: deferred** — codified in `AGENTS.md` as a project-wide rule. All new `@Component`/`@Service`/`@Bean` use constructor injection. Existing field-injected legacy classes wrapped via `AutowireCapableBeanFactory.autowireBean(legacyImpl)` keep field injection until individually rewritten; the rule applies when each class is otherwise touched (Phase 8 cleanup, ~232 legacy classes — not part of this PRD's scope).

Goal: every class instantiable with `new`, final fields, explicit dependencies at the call site.

## Hard Constraints

### URL path preservation for calendar/iCal/JNLP endpoints

**Status: HARD CONSTRAINT — non-negotiable** (recorded 2026-05-05; refined 2026-05-05 — paths only, query strings and response shapes can evolve)

The URL paths for calendar/iCal/JNLP endpoints must not change. Breaking them would invalidate user-distributed iCal subscription links (Outlook/Apple/Google/Thunderbird), embedded calendar widgets, and JNLP launch links that live in third-party calendar clients indefinitely.

**Critical paths (must keep their exact value):**

| Legacy URL path | Current handler | Migration target |
|-----------------|-----------------|------------------|
| `/rapla/ical` (and `/rapla/internal_ical`) | `Export2iCalServlet` (JAX-RS) | Spring `@RestController` at the same path. **CRITICAL — most important.** |
| `/rapla/calendar` (and `.csv` / `internal_` variants) | `CalendarPageGenerator` | Spring controller at the same paths. **CRITICAL — most important.** |
| `/rapla/raplaclient` and `/rapla/raplaclient.jnlp` | `RaplaJNLPPageGenerator` | Spring controller at the same path. |

**MAY be reorganised:** `/rapla/Rapla/` (static webclient/JAR distribution tree) and `/rapla/index` — not bound by the constraint.

Allowed: internal refactoring, JAX-RS → Spring annotation swap on same paths, servlet container switch, query-param and response-body modernisation. Not allowed: path renames or redirects. `ServletRequestPreprocessor` migration (used by `UrlEncryption` plugin to pre-decrypt `?key=…`) must hook the same paths via Spring `HandlerInterceptor` / `OncePerRequestFilter`.

## Decisions

| Decision | Choice |
|----------|--------|
| REST framework | **Spring MVC** (`@RestController`) — not Jersey |
| Embedded container | **Tomcat** (Spring Boot default) — no exclusions needed |
| Client DI | **Migrate alongside server** — Spring `ApplicationContext` on Swing client too |
| REST client proxies | **`HttpServiceProxyFactory`** with synchronous interfaces, RxJava3 wrapping on client |
| Async model | **Promises only on client** — server is fully synchronous |
| GWT frontend | **Drop** — future frontend is Angular, consumes same REST API |
| Database layer | **No change** — keep existing `CachableStorageOperator` / `FileOperator` |
| `restinject` removal | **Full removal** after both server and client are migrated |
| Serialization | **Gson during migration, Jackson after Date→LocalDateTime** |
| Date→LocalDateTime | **Prerequisite PRD 001-A** — must complete before switching to Jackson |
| Client packaging | **JNLP with exploded lib/ dir** — `maven-dependency-plugin:copy-dependencies` produces individual JARs; JNLP manifest references them. Client is NOT packaged as a Spring Boot fat JAR. |
| PATCH support | **Yes** — `@PatchMapping` (server) + `@PatchExchange` (client proxy) |
| Namespace migration | **`javax.*` → `jakarta.*`** required by Spring Boot 3.x — done as part of the Phase 1 cutover (cannot coexist with javax-based RESTEasy 3.15 / Jetty 9 at runtime) |
| Java target | **Java 17** — Spring Boot 3.x requires Java 17+. Bumped from current `1.8` source/target. |
| Migration shape | **Incremental until Phase 1, then cutover** — Phase 0 adds Spring Boot infrastructure files alongside the existing javax stack (compiles, doesn't run). Phase 1 is a single cutover that deletes `MainServlet` + RESTEasy + javax imports and switches the runtime to Spring Boot. The two stacks cannot run side by side in the same JVM. |
| Authentication | **JWT (RFC 7519) Bearer tokens via `spring-boot-starter-oauth2-resource-server`** — replaces custom `SignedToken`. Bearer header is the only accepted source for protected endpoints. |
| JWT signing | **HS256 with shared secret** stored in `RaplaKeyStorage` (existing keystore is repurposed). RS256 is an option if a separate auth service is introduced later. |
| Token presentation | **Bearer header only** — drop cookie / query-param / basic-auth fallback. The login endpoint returns `{accessToken, refreshToken, expiresIn}` JSON; clients are responsible for sending Bearer headers and refresh requests. |
| Server async | **Server is fully synchronous — `PromiseWait` removed.** Storage / facade interfaces gain sync variants used by server callers. Client `RemoteOperator` wraps sync HTTP proxies in RxJava at the call site. |
| CORS | **Add Spring `CorsConfigurationSource`** — required for Angular frontend on a different origin (today's UI is same-origin so CORS isn't configured) |
| Multiple DataSources | **`@ConfigurationProperties` over `Map<String, DataSource>`** — current `ServerContainerContext.getDbDatasource(key)` supports multiple named datasources |
| HTTP compression | **`server.compression.enabled=true`** — replaces RESTEasy `GZIPEncodingInterceptor` |
| Static resources | **Move `src/main/webapp/` content** (HTML, CSS, JS libs, images, `webclient/`, `jsclient/`) to `src/main/resources/static/`. JNLP descriptors stay accessible at the same URLs. |

## Goal

Migrate Rapla from embedded Jetty + custom DI + RESTEasy + compile-time-generated REST proxies to **Spring Boot** with:
- Spring Boot embedded Tomcat on server
- Spring DI (`@Component`, `@Service`, constructor injection) replacing `SimpleRaplaInjector` on both server and client
- `HttpServiceProxyFactory` for type-safe REST client interfaces replacing generated `_JavaJsonProxy` classes
- Spring profiles replacing `InjectionContext` (server/client/swing)
- RxJava3 retained on client only for async Swing UI operations — server is fully synchronous
- Remove GWT support entirely (Angular frontend replaces it)

This eliminates the `restinject` annotation processor, `META-INF/services` wiring, custom proxy generation, and the GWT module — reducing build complexity and aligning with mainstream Java ecosystem.

## Serialization Strategy

Rapla currently uses a custom `JavaJsonSerializer` (Gson-based) with special handling for `java.util.Date`. Switching to Jackson is desired but requires `java.util.Date` → `java.time.LocalDateTime` first.

### Phased approach

| Step | Serializer | Date type | When |
|------|-----------|-----------|------|
| 1. Spring Boot migration | **Gson** (Spring Boot supports it via `spring.http.converters.preferred-json-mapper=gson`) | `java.util.Date` | PRD 001 |
| 2. Date → LocalDateTime | Gson (no change) | `LocalDateTime` | PRD 001-A |
| 3. Switch to Jackson | **Jackson** (Spring Boot default) | `LocalDateTime` | PRD 001, Phase 9 |

This avoids writing throwaway Jackson custom serializers for `java.util.Date`. Gson is used as a bridge during the transition.

**PRD 001-A (Date → LocalDateTime) is a prerequisite for Phase 9 (Jackson switch).**
PRD 001 Phases 0-8 can proceed independently with Gson.

## Architecture Overview

### After Migration

```
SERVER (Spring Boot + Tomcat)                   CLIENT (Swing + Spring DI)
─────────────────────────────                   ────────────────────────────
@SpringBootApplication                          AnnotationConfigApplicationContext
  @RestController (sync, no promises)             @ComponentScan("org.rapla.client", "org.rapla.plugin.*.client")
    RemoteStorageController                       RemoteOperator
    RaplaEventsController                           ├─ RemoteStorageClient (HttpServiceProxyFactory)
    RaplaResourcesController                        │    └─ RestClient + auth interceptor
    ...                                             ├─ wraps calls in RxJava3 Completable/Observable
  @Service                                         │    └─ subscribeOn(Schedulers.io()) for Swing EDT safety
    ServerServiceImpl                             └─ RemoteConnectionInfo (server URL + access token)
    CachableStorageOperator
    FacadeImpl                                  SwingSchedulerImpl (EDT-aware)
  @Component                                     RaplaClientServiceImpl
    20+ plugin extensions                          Calendar views, editors, menus, option panels
  @ConfigurationProperties
    RaplaServerProperties

ANGULAR FRONTEND
─────────────────
Consumes same REST API via HTTP (no change to endpoints)
```

### REST Client Proxy Pattern

Server endpoints are synchronous `@RestController` methods returning plain objects.
Client uses `HttpServiceProxyFactory` to create synchronous interface proxies,
then wraps them in RxJava3 at the call site for async execution:

```java
// Shared interface (server + client classpath)
public interface RemoteStorage {
    ResourcesResponse getResourcesSync();
    void store(UpdateEvent event);
    // ...
}

// Server: @RestController implements the interface
@RestController
@RequestMapping("/storage")
public class RemoteStorageController implements RemoteStorage {
    @GetMapping("/resources")
    public ResourcesResponse getResourcesSync() { /* ... */ }
}

// Client: HttpServiceProxyFactory creates proxy from the same interface
@Configuration
public class ClientProxyConfig {
    @Bean
    RemoteStorage remoteStorageClient(RestClient.Builder builder, RemoteConnectionInfo info) {
        RestClient restClient = builder
            .baseUrl(info.getServerURL())
            .requestInterceptor((request, body, execution) -> {
                request.getHeaders().setBearerAuth(info.getAccessToken());
                return execution.execute(request, body);
            })
            .build();
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(RemoteStorage.class);
    }
}

// Client: RemoteOperator wraps in RxJava for async
public class RemoteOperator {
    private final RemoteStorage remoteStorage;

    public Completable connectAsync() {
        return Completable.fromAction(() -> {
            ResourcesResponse response = remoteStorage.getResourcesSync();
            processResponse(response);
        }).subscribeOn(Schedulers.io());
    }
}
```

## Authentication

The custom `SignedToken` (`userId$signature`, 4-source extraction) is **replaced by standard JWT**. This removes proprietary serialization, leverages Spring Security's resource-server filter chain, and aligns with what an Angular frontend expects.

### Token format

| Token | Algorithm | Expiry | Claims |
|-------|-----------|--------|--------|
| Access | HS256 | 1 hour | `sub` (userId), `iat`, `exp`, `roles` |
| Refresh | HS256 | 30 days | `sub`, `iat`, `exp`, `jti`, `typ: "refresh"` |

Secret is loaded via `RaplaKeyStorage` (existing keystore — repurposed to hold the HMAC secret). On first startup, if no secret exists, generate a 256-bit secret and persist it. Single-node deployment, so no coordination needed. This preserves the rotate-by-redeploy semantics already in place.

### Library choice

- **Validation:** `spring-boot-starter-oauth2-resource-server` (Spring Security's built-in JWT support, uses Nimbus JOSE under the hood)
- **Issuance:** `nimbus-jose-jwt` directly (Spring Security does not issue tokens — only validates them)

### Endpoints

| Endpoint | Purpose |
|----------|---------|
| `POST /auth/login` | Body: `{username, password}`. Returns `{accessToken, refreshToken, expiresIn}` |
| `POST /auth/refresh` | Body: `{refreshToken}`. Returns the same shape with new tokens. Old refresh token blacklisted via `jti`. |

### Filter chain

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder decoder) throws Exception {
    return http
        .authorizeHttpRequests(a -> a
            .requestMatchers("/auth/**", "/", "/static/**", "/*.html", "/*.css",
                              "/Rapla/**", "/images/**", "/webclient/**", "/jsclient/**").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)
                                                .jwtAuthenticationConverter(raplaJwtConverter())))
        .csrf(c -> c.disable())  // stateless API
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .cors(Customizer.withDefaults())
        .build();
}

@Bean
JwtDecoder jwtDecoder(RaplaKeyStorage keys) {
    SecretKeySpec key = new SecretKeySpec(keys.getJwtSecret(), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
}
```

The `JwtAuthenticationConverter` extracts `sub` → `User`, populating `Authentication` with the user principal that `@RestController` methods can receive via `@AuthenticationPrincipal`.

### Migration of existing artifacts

- `TokenHandler`, `SignedToken` — **deleted**. Validation is handled by Spring Security's `JwtDecoder`.
- `RemoteSessionImpl.extractUser` — **deleted**. The 4-source extraction goes away; only Bearer header is supported.
- `RaplaKeyStorage` — kept, but now stores a single HMAC secret, not a signing keypair.
- `RaplaAuthRestPage` — becomes `AuthController` with `/auth/login` and `/auth/refresh` issuing JWTs.
- `MyCustomConnector.reauth()` retry → `RestClient.ResponseErrorHandler` that calls `/auth/refresh` on 401 and retries once.
- `LoginTokens` DTO — kept (still the response shape), but its `fromString`/`toString` cookie-serialization methods are deleted.

### What this means for existing clients

The Swing client and the Angular frontend always use Bearer headers, so they are unaffected after migration. Confirmed: no production deployments rely on `?access_token=` query params or cookie-based iCal export URLs, so the cookie/query/basic-auth fallbacks can be removed without a deprecation window.

## Promise-wait Removal (server)

The current server has ~12 call sites that block on `PromiseWait.waitForWithRaplaException(promise, timeoutMillis)` inside REST handlers. The Promise return type exists in `CachableStorageOperator` and `RaplaFacade` because those interfaces are shared between server and client — historically the client (`RemoteOperator`) returned Promises because HTTP calls were async.

In the new architecture, the client uses synchronous `HttpServiceProxyFactory` proxies and wraps them in RxJava at the call site. There is no longer a reason for the underlying storage interfaces to be Promise-typed. **The Promise-wait pattern is removed entirely from the server.**

### Strategy

1. Add synchronous variants to `CachableStorageOperator` and `RaplaFacade` for the methods currently returning `Promise<T>` that are called from server-side code (queryAppointments, queryReservations, queryBlocks, getConflicts, initBuilder, ICAL imports, table view queries). Sync variants do the same work without Promise wrapping — most underlying implementations are synchronous already; the Promise was just ceremony.
2. Update server callers to use sync variants directly:
   - `RaplaEventsRestPage.queryAppointments` → direct call
   - `RemoteStorageImpl.mapFutureResult` → direct call
   - `Export2iCalServlet` (2 sites) → direct call
   - `AbstractHTMLCalendarPage.initBuilder` → direct call
   - `RaplaICalImport.count` → direct call
   - `AppointmentTableViewPage`, `ReservationTableViewPage`, `AppointmentPerDayViewPage` → direct calls
   - `SecurityManager` (2 sites) → direct calls
3. Update `RemoteOperator` (client) to call sync variants and wrap in `Completable.fromCallable(...).subscribeOn(Schedulers.io())` for any caller that wants async.
4. Delete `PromiseWait` interface and `PromiseWaitImpl`.
5. Delete `LocalAbstractCachableOperator.waitForWithRaplaException` delegation.

### Why this is safe

The underlying work in `LocalAbstractCachableOperator` (and subclasses `FileOperator`, `DBOperator`) executes synchronously in-process. The `Promise` wrapper only exists so the client variant (`RemoteOperator`) could be async. Once the client wraps at the call site instead of in the storage API, the Promise on the server side is pure overhead.

### Scheduling — what stays

`CommandScheduler.scheduleAtGivenTime(...)` and other genuinely-asynchronous scheduler APIs stay unchanged. The removal targets only the `Promise<T>`-returning data-query methods that are awaited synchronously on the server.

Implemented as a dedicated step in **Phase 2** (after server DI migration, before REST endpoint conversion in Phase 3) so REST controllers can be written against the new sync APIs from the start.

## Scope

### What changes

| Current | Replacement |
|---------|-------------|
| **Server** | |
| `SimpleRaplaInjector` + `ServerCreator` | Spring `ApplicationContext`, `@SpringBootApplication` |
| `@DefaultImplementation(of=X, context=server)` | `@Service` + `@Profile("server")` |
| `@Extension(provides=X, id="...", context=server)` | `@Component` + `@Profile("server")` + `@Named("id")` |
| `@ExtensionPoint` | Spring interface with `List<T>` or `Map<String, T>` injection |
| `InjectionContext` enum | `@Profile("server")`, `@Profile("client")`, `@Profile("swing")` |
| `AnnotationInjectionProcessor` + `META-INF/services` | `@ComponentScan` |
| `MainServlet` + `ServerStarter` | Spring Boot `main()` with `SpringApplication.run()` |
| `CustomJettyStarter` + `jetty.xml` | Spring Boot embedded Tomcat (auto-config) |
| `RestApplication` + RESTEasy `HttpServletDispatcher` | Spring MVC `@RestController` |
| `ResteasyMembersInjector` | Spring manages bean lifecycle natively |
| JAX-RS `@Path`/`@GET`/`@POST` annotations | Spring `@RequestMapping`/`@GetMapping`/`@PostMapping` |
| JNDI config lookups | `@ConfigurationProperties` + `application.yml` |
| `javax.*` imports (inject, servlet, ws.rs) | `jakarta.*` imports throughout |
| `RemoteSessionImpl.extractUser` (4-source token extraction) | **Deleted.** Bearer header only via `spring-boot-starter-oauth2-resource-server`. |
| `TokenHandler` + `SignedToken` (custom HMAC token format) | **Deleted.** Replaced by standard JWT; validation by Spring's `NimbusJwtDecoder`, issuance by `nimbus-jose-jwt` directly. |
| `RaplaAuthRestPage` (login + cookie-setting) | `AuthController` with `/auth/login` + `/auth/refresh` returning JSON `{accessToken, refreshToken, expiresIn}` |
| `LoginTokens.fromString` / `toString` cookie serialization | **Deleted.** DTO retained as JSON response shape only. |
| `MyCustomConnector.reauth()` retry on 401 | `RestClient.ResponseErrorHandler` that calls `/auth/refresh` and retries once |
| `PromiseWait`, `PromiseWaitImpl`, `waitForWithRaplaException` (~12 call sites) | **Deleted.** Server-side storage / facade APIs gain sync variants; callers invoke them directly. |
| `ServletRequestPreprocessor` extension point | Spring `HandlerInterceptor` beans registered by `WebMvcConfigurer` (preserves order, plugin discoverable) |
| RESTEasy `GZIPEncodingInterceptor` | `server.compression.enabled=true` in `application.yml` |
| `ServerContainerContext.getDbDatasource(Map<String,DataSource>)` | `@ConfigurationProperties("rapla.datasources")` binding to `Map<String, DataSourceProperties>` |
| JNDI `mail/Session` lookup | `spring-boot-starter-mail` with `spring.mail.*` properties |
| `RemoteLocaleServiceImpl` request-locale logic | Spring `LocaleResolver` reading user preferences from `RemoteSession` |
| `src/main/webapp/` static content (HTML, CSS, JS libs, images, `webclient/`, `jsclient/`) | `src/main/resources/static/` (Spring Boot serves automatically; JNLP descriptors stay at same URLs) |
| **Client** | |
| `SimpleRaplaInjector` + `ClientCreator` | Spring `AnnotationConfigApplicationContext` |
| `@DefaultImplementation(of=X, context=swing/client)` | `@Component` + `@Profile("swing")`/`@Profile("client")` |
| `@Extension(provides=X, id="...", context=swing)` | `@Component` + `@Named("id")` |
| `_JavaJsonProxy` generated clients | `HttpServiceProxyFactory` with `RestClient` |
| `AbstractJsonProxy` + `CustomConnector` | `RestClient` backed proxies |
| `MyCustomConnector` + `HTTPJsonConnector` | `RestClient.Builder` with interceptors (auth, error mapping) |
| `ServiceInfLoader` classpath scanning | `@ComponentScan` |
| Client-side package filtering (exclude `.server.`) | `@ComponentScan` with `excludeFilters` |
| Fat JAR / single-artifact client packaging | Exploded `lib/` directory via `maven-dependency-plugin` for JNLP |
| **Removed** | |
| GWT module (`GwtBundleManager`, GWT frontend) | Removed — Angular replaces |
| `InjectionContext.gwt` | Removed |
| `restinject` annotation processor | Removed entirely |
| `StandaloneStarter` (Jetty `LocalConnector`) | Replaced — standalone mode uses direct bean injection (no HTTP) |

### Compound InjectionContext → multi-value @Profile

Some classes are annotated with multiple `InjectionContext` values (e.g., active in both `server` and `client`). Spring `@Profile` supports this via array syntax:

```java
@Profile({"server", "client"})
```

Audit all `@DefaultImplementation` and `@Extension` annotations for compound contexts during Phase 2 and Phase 4.

### Key files/packages affected

**Server:**
- `src/main/java/org/rapla/server/` — entire server package (startup, DI, servlets)
- `src/main/java/org/rapla/enpoints/server/` — REST endpoint implementations
- `src/main/java/org/rapla/plugin/*/server/` — all server-side plugin implementations (~20 plugins)
- `src/main/java/org/rapla/storage/impl/server/` — server storage operators
- `src/main/java/org/rapla/facade/internal/FacadeImpl.java` — shared facade
- `src/main/java/org/rapla/framework/` — `DefaultScheduler`, `RaplaLocaleImpl`
- `src/main/webapp/WEB-INF/web.xml` — removed
- `parent/pom.xml` — dependency changes

**Client:**
- `src/main/java/org/rapla/client/swing/internal/ClientCreator.java` — replaced with Spring context
- `src/main/java/org/rapla/client/swing/internal/RaplaClientServiceImpl.java` — `@Component`
- `src/main/java/org/rapla/client/swing/` — all Swing UI components become `@Component`
- `src/main/java/org/rapla/client/internal/` — client logic becomes `@Component`
- `src/main/java/org/rapla/client/menu/` — menu factories
- `src/main/java/org/rapla/plugin/*/client/` — all client-side plugin implementations
- `src/main/java/org/rapla/storage/dbrm/RemoteOperator.java` — uses `HttpServiceProxyFactory` proxies
- `src/main/java/org/rapla/storage/dbrm/MyCustomConnector.java` — replaced by `RestClient` interceptors
- `src/main/java/org/rapla/facade/internal/ClientFacadeImpl.java` — `@Component`
- `src/main/java/org/rapla/components/i18n/client/swing/SwingBundleManager.java` — `@Component`

**Removed:**
- `src/main/java/org/rapla/components/i18n/client/gwt/` — GWT bundle manager
- GWT-related `@Extension`/`@DefaultImplementation` (context=gwt)
- `src/test/java/org/rapla/bootstrap/CustomJettyStarter.java` — replaced with `@SpringBootTest`

**Build:**
- `pom.xml` — add `spring-boot-maven-plugin` (server only), `maven-dependency-plugin` (client lib/), remove restinject processor
- `parent/pom.xml` — add Spring Boot BOM, starters; remove restinject dep

### What stays

- Entity model and storage layer (`CachableStorageOperator`, `LocalAbstractCachableOperator`, `FileOperator`, `SQLOperator`)
- Business logic in all `*ServiceImpl` classes
- Plugin extension point semantics (Spring `Map<String, T>` naturally supports this)
- RxJava3 on client for async operations
- Swing UI components (views, editors, dialogs) — only DI annotations change
- REST API paths (same URLs for backward compatibility with Angular frontend)
- `DefaultScheduler` / `CommandScheduler` — kept as a `@Service` bean. **Do not replace with Spring `@Scheduled`** — the `scheduleAtGivenTime(Action, hour, minute)` API is called by callers (timezone-aware via `TimeZoneConverter`) and uses RxJava `.repeat()`. Wrapping in Spring's `TaskScheduler` would break the API.
- `RaplaKeyStorage` — kept, but now stores a single HMAC secret for JWT signing instead of the legacy keypair
- Logback config (already used; rename to `logback-spring.xml` for Spring Boot auto-detection)

## Dependencies

### Server (`spring-boot-starter-web` — Tomcat default, no exclusions)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <!-- version managed by spring-boot BOM; used directly only for JWT issuance -->
</dependency>
```

### Client (Swing — no embedded container, not a Spring Boot app)

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
</dependency>
<!-- HttpServiceProxyFactory requires Spring 6.1+ (via spring-boot BOM 3.2+) -->
```

#### Client dependency breakdown

| JAR | Size | Purpose |
|-----|------|---------|
| `spring-context-6.2.x.jar` | 1.3 MB | DI container (`@Component`, `@Autowired`, `ApplicationContext`) |
| `spring-core-6.2.x.jar` | 1.9 MB | Core framework (beans, utils) |
| `spring-beans-6.2.x.jar` | 0.9 MB | Bean factory, property editing |
| `spring-aop-6.2.x.jar` | 0.4 MB | AOP proxies (required by DI) |
| `spring-expression-6.2.x.jar` | 0.3 MB | SpEL (used internally by DI) |
| `spring-jcl-6.2.x.jar` | 0.02 MB | Logging bridge |
| `spring-web-6.2.x.jar` | 2.0 MB | `RestClient`, `HttpServiceProxyFactory` |
| `micrometer-observation-1.14.x.jar` | 0.07 MB | Metrics (transitive of spring-web) |
| `micrometer-commons-1.14.x.jar` | 0.05 MB | Metrics (transitive of spring-web) |
| **Total** | **~6.8 MB** | **9 JARs, no overlap with current 51 JARs (15.1 MB)** |

**Client classpath:** 51 JARs (15.1 MB) → 60 JARs (21.9 MB) — **+6.8 MB (+45%)**. First-load penalty only; cached by OpenWebStart/JNLP after that.

#### JNLP packaging

Client distribution uses **OpenWebStart** (open-source Java Web Start for Java 11+) for autoupdate + JRE provisioning. JNLP `.jar` entries list each JAR individually. Spring JARs are added alongside existing JARs — no change to the update mechanism.

Client JNLP packaging uses `maven-dependency-plugin:copy-dependencies` to produce flat `lib/` JARs — not `spring-boot-maven-plugin`. The JNLP manifest lists each JAR individually as it does today, with Spring JARs added to the list.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <executions>
        <execution>
            <id>copy-client-deps</id>
            <phase>package</phase>
            <goals><goal>copy-dependencies</goal></goals>
            <configuration>
                <outputDirectory>${project.build.directory}/lib</outputDirectory>
                <includeScope>runtime</includeScope>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Removed
```
restinject (entire library)
RESTEasy (org.jboss.resteasy:resteasy-jaxrs)
javax.ws.rs-api (replaced by Spring MVC annotations)
javax.servlet-api (provided by Spring Boot / jakarta.servlet)
spring-boot-starter-jetty (not needed — Tomcat is default)
```

### Kept
```
RxJava3 (io.reactivex.rxjava3:rxjava) — client only
Gson — kept temporarily during migration (Jackson switch after PRD 001-A)
iCal4j, EWS Java API — unchanged
```

## Plan (original — historical reference; see Implementation Status above for what actually shipped)

### Phase 0: Preparations (additive — existing code untouched)
Java 17 target (delete `src/main/java9/module-info.java`); Spring Boot 3.2+ BOM in `parent/pom.xml`; add `spring-boot-starter-{web,security,oauth2-resource-server}` + `nimbus-jose-jwt` (server) and `spring-context`/`spring-web` (client); `maven-dependency-plugin:copy-dependencies` for JNLP `lib/`; `application.yml` (port 8051, compression); `RaplaServerProperties @ConfigurationProperties`; `RaplaSpringBootApplication`; `logback-spring.xml`; `@SpringBootTest` smoke test in a separate Surefire fork.

### Phase 1: Cutover — Bootstrap, Jakarta, MainServlet removal
Single-PR cutover: move `webapp/` → `resources/static/`; rewrite `javax.{inject,servlet,ws.rs}` → `jakarta.*`; `ServerServiceImpl` → `@Service`; `ServerConfig` with `@ComponentScan`; delete `MainServlet`, `ServerStarter`, `ServerCreator`, `web.xml`, `CustomJettyStarter`, `jetty.xml`; replace `StandaloneStarter` with direct `RemoteStorage` injection; migrate `RaplaTestCase`/`AbstractTestWithServer` to `@SpringBootTest`; drop RESTEasy + Jetty 9.

### Phase 2: Server DI Migration + Promise-wait removal
`@Service` + `@Profile("server")` on `@DefaultImplementation` classes; constructor `@Inject`; `Map<String, ServerExtension>` via `@Named`; `Set<T>` → `List<T>`; audit compound `InjectionContext` → `@Profile({"server","client"})` (array form — missing context = silent bean-not-found). Promise-wait: sync variants on `CachableStorageOperator`/`RaplaFacade` for the ~12 awaited methods; update callers; delete `PromiseWait`/`PromiseWaitImpl`/`LocalAbstractCachableOperator.waitForWithRaplaException`; client wraps in RxJava at call site.

### Phase 3: REST Endpoints + Security + Filters
JAX-RS → `@RestController`. Core: `RemoteStorageImpl`, `RemoteAuthentificationServiceImpl`, `RemoteLocaleServiceImpl`, `RemoteLoggerImpl`. REST pages: `RaplaEvents/Resources/Auth/DynamicTypesRestPage`. Plugins: `CalendarPageGenerator`, `Export2iCalServlet`, `UrlEncryptionService`, etc. Exception mappers → `@ControllerAdvice`. **JWT setup** (see Authentication §): `JwtDecoder` via `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)`; custom `JwtAuthenticationConverter`; `SecurityFilterChain` (permit `/auth/**` + statics, auth elsewhere, stateless, CSRF off); `AuthController` issues JWTs via `nimbus-jose-jwt`; delete `TokenHandler`/`SignedToken`/`RemoteSessionImpl.extractUser`/`LoginTokens.fromString-toString`. CORS via `WebMvcConfigurer.addCorsMappings()` driven by `rapla.cors.allowed-origins`. `ServletRequestPreprocessor` → Spring `HandlerInterceptor`. `LocaleResolver` from authenticated principal with `Accept-Language` fallback. Drop RESTEasy (and `GZIPEncodingInterceptor` — replaced by `server.compression`).

### Phase 4: Client DI Migration
`ClientConfig @Configuration` with `@ComponentScan` (excludeFilters for server packages); migrate `ClientCreator` to `AnnotationConfigApplicationContext`; `@Component` + `@Profile("swing"|"client")` on Swing UI / shared client logic; `@Named("id")` on ~60 extensions; audit compound contexts; `SwingSchedulerImpl` as `@Component`.

### Phase 5: REST Client Proxies
JAX-RS interfaces → `@HttpExchange`/`@GetExchange`; `ClientProxyConfig` with `RestClient.Builder` (auth interceptor + error mapping) + `HttpServiceProxyFactory`; `RemoteOperator` calls injected proxies, wraps in RxJava (`Completable.fromAction(...).subscribeOn(Schedulers.io())`); delete `MyCustomConnector`/`HTTPJsonConnector`/`AbstractJsonProxy` and the `AnnotationInjectionProcessor` proxy generation.

### Phase 6: Plugin System
`PluginAutoConfiguration` (server) + `ClientPluginAutoConfiguration`. **Actual implementation** used `@ConditionalOnProperty(prefix="rapla.services", name="<plugin-id>", matchIfMissing=true)` rather than the originally-planned `RaplaPluginImportSelector` — Spring Boot's standard conditional annotations covered the existing config format with no custom selector needed.

### Phase 7: Remove GWT
Delete `GwtBundleManager`, GWT module descriptor, GWT frontend code, `InjectionContext.gwt`, GWT-specific deps.

### Phase 8: Cleanup
Remove `restinject` artifact, `META-INF/services` generation, `ServiceInfLoader`, `CustomJettyStarter`, `jetty.xml`, `web.xml`, `AnnotationInjectionProcessor` config; update `Dockerfile`/`docker-compose.yml` for fat JAR + exploded `lib/`.

### Phase 9: Switch Gson → Jackson (after PRD 001-A Date → LocalDateTime)
Remove `spring.http.converters.preferred-json-mapper=gson`; add `jackson-datatype-jsr310` (later dropped — Jackson 3 has built-in `java.time.*`); drop Gson; remove custom `JavaJsonSerializer`.

## Tests

| Phase | Test | When |
|-------|------|------|
| 0 | Spring Boot app starts with `application.yml`; `mvn compile` passes with no `javax` references | Before any migration |
| 1 | `@SpringBootTest` starts, `ServerServiceContainer` bean available; `RaplaTestCase` migrated | After Phase 1 |
| 2 | All server beans resolve from `ApplicationContext`; compound-profile beans present in correct contexts | After Phase 2 |
| 3 | All REST endpoints respond via `MockMvc` / `TestRestTemplate` | After Phase 3 |
| 4 | Client `ApplicationContext` starts, all client beans resolve | After Phase 4 |
| 5 | Proxies call mock server, RxJava async wrapping works | After Phase 5 |
| 6 | All server + client plugins load and start; disabling plugin via config excludes its beans | After Phase 6 |
| 7 | GWT code removed, no compile errors | After Phase 7 |
| 8 | Full `mvn test` passes | After Phase 8 |
| 9 | Jackson serialization matches Gson output, Angular frontend works | After Phase 9 |

**Note on test continuity:** `RaplaTestCase.createServerContext()` directly instantiates `ServerCreator` and `SimpleRaplaInjector` and will break in Phase 1. Migrating the test base class is part of Phase 1, not a post-hoc cleanup.

## Dependencies on Other PRDs

| PRD | Relationship |
|-----|-------------|
| **001-A: Date → LocalDateTime** | **Prerequisite for Phase 9** (Jackson switch). Phases 0-8 proceed independently with Gson. |

## Open Questions (all resolved — historical record)

1. **Shared interface design?** **Chose (a)** — single source of truth. JAX-RS annotations on shared interfaces replaced with Spring `@HttpExchange`; server `@RestController` implements the same interface.
2. **Gson JSON format compatibility?** Verified compatible during transition; Phase 9 cutover to Jackson preserved wire format (both default to ISO-8601 dates).
3. **Big bang or incremental?** **Incremental** — server Phases 1–3 shipped before client Phases 4–6.
4. **JNLP download size?** Adding `spring-context` + `spring-web` JARs adds ~6.8 MB to the client classpath (51 → 60 JARs, 15.1 → 21.9 MB, +45%). First-load penalty only; OpenWebStart caches after.
5. **`raplaservices` config drives core services too?** Spring Boot's `@ConditionalOnProperty(prefix="rapla.services", name="<plugin-id>", matchIfMissing=true)` covered both core services and plugins — no custom `RaplaPluginImportSelector` needed.
   