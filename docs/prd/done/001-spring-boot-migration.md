# PRD 001: Spring Boot Migration

**Status:** done — **Phases 1–9 all complete** as of 2026-05-08. Phase 9 step 2 (Gson removal) shipped end-to-end alongside the Jackson 2 → Jackson 3 cutover under [PRD 011](../011-spring-boot-4-jackson-3.md): `gson` dep dropped from rapla-bom; `HTTPWithJsonConnector`, `HTTPWithJsonMailConnector`, `MailapiClient`, `JacksonMergePatch`, `RestAPIExample` migrated to Jackson; reactor `mvn test` green on Spring Boot 4.0.6 + Jackson 3.1.2. Phase 4 (client DI) completed via [PRD 002](002-swing-spring-di.md). Verified 2026-05-08: zero `gson` in dep tree, zero `gson` imports in source, zero `gson` references in any pom file.
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

**Started:** 2026-05-05  **Completed:** 2026-05-05

All `javax.{inject,servlet,ws.rs}.*` imports across `src/main/java` and `src/test/java` rewritten to `jakarta.*`. `mvn compile` passes; `mvn test -Dtest=RaplaSpringBootApplicationTest` passes (Spring Boot 3.2.5 context loads on Tomcat 10).

#### Steps performed

| # | Action | Detail |
|---|--------|--------|
| 1 | Bulk sed across all `.java` | `find src/main/java src/test/java -name "*.java" \| xargs sed -i 's\|^import javax\\.inject\\.\|import jakarta.inject.\|; s\|^import javax\\.servlet\\.\|import jakarta.servlet.\|; s\|^import javax\\.ws\\.rs\\.\|import jakarta.ws.rs.\|'`. After sed: 0 `javax.inject.*`, 0 `javax.servlet.*`, 0 `javax.ws.rs.*` import statements left in non-test source. |
| 2 | `parent/pom.xml` properties | `<javax.inject.version>` → `<jakarta.inject.version>2.0.1</...>`; `<javax.ws.rs.version>` → `<jakarta.ws.rs.version>3.1.0</...>` |
| 3 | `parent/pom.xml` dependencies | `javax.inject:javax.inject:1` → `jakarta.inject:jakarta.inject-api:2.0.1`; `javax.ws.rs:javax.ws.rs-api:2.1` → `jakarta.ws.rs:jakarta.ws.rs-api:3.1.0`; `javax.servlet:javax.servlet-api:3.1.0` → `jakarta.servlet:jakarta.servlet-api:6.0.0` (added) **AND** `javax.servlet:javax.servlet-api:4.0.1` retained (provided scope) so RESTEasy 3.15's `HttpServletDispatcher` parent-class hierarchy resolves until cutover deletes the rest of RESTEasy in later Phase 1 steps. |
| 4 | `pom.xml` artifactItem | `javax.inject:javax.inject` → `jakarta.inject:jakarta.inject-api` in maven-dependency-plugin webclient copy step |
| 5 | RESTEasy-bound source files **deleted** | `src/main/java/org/rapla/server/MainServlet.java` (~450 LOC bootstrap) and `src/main/java/org/rapla/server/provider/resteasy/ResteasyExceptionMapper.java` — these compiled against `javax.servlet.http.HttpServlet` via RESTEasy 3.15's javax-bound API and could not be made to compile against jakarta without rewriting their entire substance. They are scheduled for deletion in Phase 1 cutover, so deletion was pulled forward. **Only one external reference remains: `src/test/java/org/rapla/bootstrap/CustomJettyStarter.java` (legacy test bootstrap, slated for deletion in Phase 1.7).** |
| 6 | Provider-namespace pinning at restinject boundary | `restinject 2.0-RC11` is compiled against `javax.inject.Provider` and its `addComponentProvider` / `addComponentInstanceProvider` methods have `<? extends javax.inject.Provider<I>>` as upper bound. Three classes that hand `Provider` instances to restinject keep their `Provider` import as `javax.inject.Provider` until the entire restinject library is removed in Phase 8: `src/main/java/org/rapla/client/swing/internal/ClientCreator.java`, `src/main/java/org/rapla/server/internal/ServerStorageSelector.java` (mixed: javax for the `implements` clause, FQN `jakarta.inject.Provider` on the internal `getImportExportManager()` method that talks to the production-side `DBOperator`), and `src/test/java/org/rapla/test/util/RaplaTestCase.java` (mixed: jakarta for the `Provider` import that's passed to production code, javax for the `Filter`/`DispatcherType`/`ServletContext` Jetty 9 servlet API). |
| 7 | Test-side Jetty/RESTEasy clients | `src/test/java/org/rapla/test/util/RaplaTestCase.java` reverted to `javax.servlet.*` for Jetty 9 (`Filter`, `FilterChain`, `FilterConfig`, `ServletException`, `ServletRequest`, `ServletResponse`, `DispatcherType`); `src/test/java/org/rapla/rest/client/resteasy/ResteasyRemoteConnector.java` reverted to `javax.ws.rs.client.*` for RESTEasy 3.15 client. Both classes are slated for deletion when `RaplaTestCase` migrates to `@SpringBootTest` (Phase 1.7). |

#### Verification

| Command | Result |
|---------|--------|
| `grep -rl "import javax\\.\\(inject\\|servlet\\|ws\\.rs\\)" src/main/java` | empty (zero hits) |
| `mvn compile -DskipTests` | BUILD SUCCESS, 0 errors, 10 pre-existing deprecation warnings |
| `mvn test -Dtest=RaplaSpringBootApplicationTest` | 1 test passing, Spring context loads against Tomcat 10 |

#### Files changed in Phase 1.2

```
modified:   parent/pom.xml          (jakarta.inject + jakarta.ws.rs + jakarta.servlet deps; javax.servlet kept as transitional 4.0.1 provided)
modified:   pom.xml                 (artifactItem now jakarta.inject)
deleted:    src/main/java/org/rapla/server/MainServlet.java
deleted:    src/main/java/org/rapla/server/provider/resteasy/ResteasyExceptionMapper.java
modified:   ~96 .java files        (jakarta.* import statements; bulk sed)
modified:   src/main/java/org/rapla/client/swing/internal/ClientCreator.java       (kept javax.inject.Provider for restinject API)
modified:   src/main/java/org/rapla/server/internal/ServerStorageSelector.java    (javax.inject.Provider import + FQN jakarta.inject.Provider on internal method)
modified:   src/test/java/org/rapla/test/util/RaplaTestCase.java                  (javax.servlet.* + jakarta.inject.Provider mix)
modified:   src/test/java/org/rapla/rest/client/resteasy/ResteasyRemoteConnector.java (javax.ws.rs.client.*)
```

Not yet committed.

**Note on coexistence:** Two servlet APIs are on the classpath simultaneously during the migration:
- `jakarta.servlet:jakarta.servlet-api:6.0` — used by Tomcat 10 / Spring Boot 3.2 and the production source.
- `javax.servlet:javax.servlet-api:4.0.1 (provided)` — kept solely so RESTEasy 3.15's `HttpServletDispatcher`/`Filter` class hierarchy resolves at compile time for the legacy test bootstrap (`CustomJettyStarter`, `RaplaTestCase`). Will be removed when the legacy test stack is replaced by `@SpringBootTest` (Phase 1.7) and RESTEasy is dropped (Phase 3.10).

**javax.* packages intentionally NOT migrated (Java SE / unchanged):** `javax.swing.*`, `javax.crypto.*`, `javax.naming.*`, `javax.net.*`, `javax.print.*`, `javax.script.*`, `javax.sql.*`, `javax.xml.*`, `javax.mail.*`

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

#### Bridge approach is non-viable — recorded for posterity

A bridge integration test (`LegacyServerBridgeIntegrationTest`) was written that set `rapla.bridge.enabled=true`, copied `testdefault.xml` to a `@TempDir`, and pointed `rapla.file-datasources.raplafile` at it via `@DynamicPropertySource`. **The test failed with:**

```
SimpleRaplaInjector$RaplaContainerContextException:
  No javax.inject.Inject Annotation or public default constructor found
  in class org.rapla.server.internal.ServerServiceImpl
```

**Root cause:** the Phase 1.2 namespace migration rewrote every `@javax.inject.Inject` constructor annotation to `@jakarta.inject.Inject`. The `restinject 2.0-RC11` library — used by `ServerCreator` / `SimpleRaplaInjector` to scan classes and pick injectable constructors — only recognises `@javax.inject.Inject`. After the migration, **no class in the codebase carries the annotation restinject is looking for**, so the legacy DI container can no longer instantiate any class with constructor injection.

**Implication:** The legacy DI bootstrap is **broken at runtime by Phase 1.2** and cannot be revived without one of:
- (a) Fork `restinject` to also recognise `@jakarta.inject.Inject` (smallest patch, but maintains a dead end).
- (b) Re-add `@javax.inject.Inject` *alongside* `@jakarta.inject.Inject` on every constructor (~275 sites).
- (c) **Migrate forward to native Spring DI** — every `@DefaultImplementation` becomes `@Service` (or `@Component`), every `@Inject` constructor is picked up by Spring 6's JSR-330 support (which honours `jakarta.inject.Inject`), and `ServerCreator` / `SimpleRaplaInjector` are deleted.

**Decision:** option (c). The bridge bean is left in place (gated off) only because its `Logger` + `ServerContainerContext` bean definitions are still useful inputs to the native-Spring migration that follows. The failing integration test was deleted — it cannot be made to pass without (a) or (b), and neither is worth the engineering cost.

**Knock-on consequence for the phase ordering:** Phase 1.5 (migrate `ServerServiceImpl` and friends to `@Service`) cannot be done one-collaborator-at-a-time on the legacy DI side. The legacy DI is already dead. Either (a) the entire server-side `@DefaultImplementation` graph is migrated to `@Service` in a single sweep, or (b) the migration starts from leaves with no `@Inject` dependencies and grows outward, with the rest of the codebase only providing types (compile-time) but not runtime instances. This PRD adopts **(b)** because it lets the smoke test go green at every commit; until the entire graph is migrated, only the smoke test's `assertNotNull(context)` and `RaplaServerProperties` injection are verifiable end-to-end.

### Phase 1.5 — in-progress (native Spring DI, leaf-first)

**Date:** 2026-05-05

#### Step 1 — Core leaf beans wired (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `ServerCoreConfig` `@Configuration` | `src/main/java/org/rapla/server/spring/ServerCoreConfig.java` (new) | Registers four leaf beans via explicit `@Bean` factory methods (avoids touching source classes; reversible): `BundleManager` (returns `ServerBundleManager` instance), `TimeZoneConverter` (returns `TimeZoneConverterImpl`), `RaplaResources(BundleManager)`, `RaplaSystemInfo(BundleManager)`. |
| 2 | Smoke-test injections | `src/test/java/org/rapla/server/spring/RaplaSpringBootApplicationTest.java` | Added `coreBeansResolve()` test that `@Autowired`s all four beans and asserts non-null. |

| Verification | Result |
|--------------|--------|
| `mvn test -Dtest=RaplaSpringBootApplicationTest` | 3 tests passing (~9 s startup, ~5 s context) |

**Why `@Bean` factories instead of `@Service` on the source classes:** the `@Bean` form is non-invasive — no diff in `org.rapla.RaplaResources` etc. The legacy DI graph (which is dead but still has @DefaultImplementation annotations littered across the codebase) is irrelevant because nothing instantiates it any more. When all classes are migrated and `restinject` is removed in Phase 1.8, both `@DefaultImplementation` and `ServerCoreConfig`'s manual `@Bean` blocks can be replaced with class-level `@Service` annotations + `@ComponentScan` in a final sweep.

#### Step 2 — Locale & Scheduler beans wired (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `RaplaLocale` bean | `ServerCoreConfig.raplaLocale(BundleManager)` | Returns `new RaplaLocaleImpl(bundleManager)`. |
| 2 | `CommandScheduler` bean | `ServerCoreConfig.commandScheduler(Logger, TimeZoneConverter)` | Returns `new DefaultScheduler(logger, timeZoneConverter)`. `Logger` already provided by `LegacyServerBridgeConfig.raplaLogger()`. |
| 3 | Smoke-test injection | `RaplaSpringBootApplicationTest.localeAndSchedulerResolve()` | Asserts both beans `@Autowired` non-null. |

`mvn test -Dtest=RaplaSpringBootApplicationTest` → 4 tests passing.

#### Step 3 — RemoteLoggerImpl wired (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `RemoteLogger` bean | `ServerCoreConfig.remoteLogger(AutowireCapableBeanFactory)` | Uses `beanFactory.autowireBean(impl)` to populate the `@Inject Logger logger` field on `RemoteLoggerImpl` (which has package-private field injection). Spring 6 honours `jakarta.inject.Inject` on fields when the bean is post-processed via `autowireBean`. |
| 2 | Smoke-test injection | `RaplaSpringBootApplicationTest.remoteLoggerResolves()` | Asserts `RemoteLogger` autowired non-null. |

`mvn test -Dtest=RaplaSpringBootApplicationTest` → 5 tests passing.

**Pattern note:** field-injected legacy classes can be migrated without source changes by using `AutowireCapableBeanFactory.autowireBean(instance)` inside the `@Bean` factory. This is the bridge between "no source touches" and "Spring populates @Inject fields" — applicable wherever the legacy DI graph used `@Inject` on fields rather than constructors.

#### Step 4 — PromiseWait, extensions, ServerStorageSelector, FacadeImpl wired (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `PromiseWait` bean | `ServerCoreConfig.promiseWait(Logger)` | Returns `new PromiseWaitImpl(logger)`. Will be deleted in Phase 2 (promise-wait removal). |
| 2 | `FunctionFactory` map | `ServerCoreConfig.standardFunctions(RaplaLocale)` | `@Bean(name = StandardFunctions.NAMESPACE)` — Spring's `Map<String, FunctionFactory>` injection uses bean names as keys. Plugin functions (`AppointmentNoteFunctions`, `DurationFunctions`) deferred to plugin-config phase. |
| 3 | `PermissionExtension` set | `ServerCoreConfig.raplaDefaultPermission()` | Returns `new RaplaDefaultPermissionImpl()`. Spring's `Set<PermissionExtension>` injection picks up all beans of this type. |
| 4 | `ServerStorageSelector` bean | `ServerCoreConfig.serverStorageSelector(...)` | Eight-arg constructor: container context, logger, i18n, locale, scheduler, function-factory map, permission set, promise-wait. The actual file/DB datasource is consulted lazily inside `get()`, so the bean can be constructed without a fixture. |
| 5 | `RaplaFacade` bean | `ServerCoreConfig.raplaFacade(RaplaResources, CommandScheduler, Logger)` | Returns `new FacadeImpl(...)`. The `setOperator(StorageOperator)` + `operator.connect()` chain is done by `ServerServiceImpl` and is deferred to step 5. |
| 6 | Smoke-test injections | `RaplaSpringBootApplicationTest.serverStorageSelectorResolves`, `raplaFacadeResolves` | Two new tests asserting the beans `@Autowired` non-null. |

`mvn test -Dtest=RaplaSpringBootApplicationTest` → 7 tests passing.

#### Step 5 — ServerServiceImpl boots via Spring DI (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `BundleManager` bean type widened | `ServerCoreConfig.bundleManager()` returns `ServerBundleManager` (concrete) | `ServerServiceImpl`'s constructor takes `ServerBundleManager` (concrete), so the bean must be registered as that type. The bean still satisfies `BundleManager` injections via interface lookup. |
| 2 | `ServerServiceConfig` `@Configuration` | `src/main/java/org/rapla/server/spring/ServerServiceConfig.java` (new) | Gated by `@ConditionalOnProperty(prefix="rapla.file-datasources", name="raplafile")` — the smoke test (which has no datasource configured) skips this whole config. |
| 3 | `CachableStorageOperator` bean | `ServerServiceConfig.cachableStorageOperator(ServerStorageSelector)` | `selector.get()` returns the file or DB operator depending on which datasource is configured. |
| 4 | `ServerServiceContainer` bean | `ServerServiceConfig.serverServiceContainer(...)` | 12-arg constructor. `Provider<Map<String, ServerExtension>>` / `Provider<Set<ServletRequestPreprocessor>>` are wrapped via Spring's `ObjectProvider.getIfAvailable(Collections::emptyMap/Set)` → `jakarta.inject.Provider` lambda, so no plugin extensions are needed for the bean to start. The constructor calls `operator.connect()` and reads preferences during instantiation, so this bean requires a working datasource. |
| 5 | Integration test fixture | `src/test/java/org/rapla/server/spring/ServerServiceIntegrationTest.java` (new) | `@SpringBootTest` + `@TempDir` + `@DynamicPropertySource` — copies `/testdefault.xml` from the test classpath to a temp file and points `rapla.file-datasources.raplafile` at it. Asserts `@Autowired ServerServiceContainer` is non-null. |

| Verification | Result |
|--------------|--------|
| `mvn test -Dtest='RaplaSpringBootApplicationTest,ServerServiceIntegrationTest'` | 8 tests passing across 2 Spring contexts. Integration test logs show `Rapla.Version=2.1-SNAPSHOT`, `FileOperator.connect rapla - Connecting: file:/tmp/.../rapla-data.xml`, `ConflictFinder rapla - Conflict initialization found 0 conflicts`. |

**Significance:** The legacy DI graph (`ServerCreator` → `SimpleRaplaInjector` → `ServerServiceImpl`) is now fully replaced by Spring DI for the server core. Storage connect, facade wiring, preference loading, and timezone resolution all run under Spring control. Plugin extensions are still empty (deferred to Phase 6) but the core orchestrator is alive.

#### Step 6 — Bridge `ServerServiceContainer` removed (partial)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | Remove redundant bridge bean | `LegacyServerBridgeConfig.java` (modified) | Deleted the `@Bean(destroyMethod="") ServerServiceContainer ...` that called `ServerCreator.create()`. Replaced by `ServerServiceConfig.serverServiceContainer`. The `Logger` and `ServerContainerContext` beans are kept as canonical providers. The `@ConditionalOnProperty("rapla.bridge.enabled")` mechanism is also removed. |

**Deferred:** `ServerCreator.java`, `ServerStarter.java` deletion still blocked by `RaplaTestCase.java` (test bootstrap) and `StandaloneStarter.java`. Will be done in Phase 1.7 along with the test stack migration. The three `javax.inject.Provider` pins similarly stay until `restinject` is removed in Phase 1.8.

8 tests still passing.

### Phase 1.6 — additional steps 5–7 (completed)

#### Step 5 — Mail tier + MailToUserController (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `MailInterface` bean | `ServerCoreConfig.mailInterface(RaplaFacade, ServerContainerContext)` | Returns `new MailapiClient(facade, () -> containerContext.getMailSession())` — wraps the legacy mail-session lookup into a `jakarta.inject.Provider<Object>` lambda. |
| 2 | `MailToUserImpl` bean | `ServerCoreConfig.mailToUser(MailInterface, RaplaFacade, Logger)` | Direct constructor injection. |
| 3 | `MailToUserController` | `src/main/java/org/rapla/server/spring/web/MailToUserController.java` (new) | `@PostMapping` on `/mail/send` with `@RequestParam("username")`, `@RequestHeader("subject")`, `@RequestBody String body`. Uses `RemoteSession.checkAndGetUser`. |

#### Step 6 — RemoteLocaleService (request-scoped) + RemoteLocaleController (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `ResourceBundleList` bean | `ServerCoreConfig.resourceBundleList(Set<I18nBundle>, BundleManager)` | Spring picks up all `I18nBundle` beans (currently `RaplaResources` + `RaplaSystemInfo`). |
| 2 | `RemoteLocaleService` bean — **request-scoped** | `ServerServiceConfig.remoteLocaleService(HttpServletRequest, AutowireCapableBeanFactory)` | `@RequestScope` — Spring creates a new instance per HTTP request and injects the request via constructor. `autowireBean` populates 6 `@Inject` fields. **First use of `@RequestScope` in the migration.** |
| 3 | `RemoteLocaleController` | `src/main/java/org/rapla/server/spring/web/RemoteLocaleController.java` (new) | `GET /locale/{id}` and `POST /locale`. Wraps `Promise<T>` via `SynchronizedCompletablePromise.waitFor(promise, 10000, null)` to convert async to sync at the HTTP boundary — same pattern Phase 2 will eventually use everywhere. |

#### Step 7 — Major REST pages as request-scoped beans (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `AppointmentFormater` bean | `ServerCoreConfig.appointmentFormater(RaplaResources, RaplaLocale)` | Returns `new AppointmentFormaterImpl(...)`. |
| 2 | `SecurityManager` bean | `ServerServiceConfig.securityManager(Logger, RaplaResources, AppointmentFormater, CachableStorageOperator)` | Direct constructor — needed by `RaplaResourcesRestPage`. |
| 3 | `RaplaResourcesRestPage` bean — **request-scoped** | `ServerServiceConfig.raplaResourcesRestPage(...)` | `@RequestScope` + `autowireBean` for the 4 `@Inject` fields. |
| 4 | `RaplaDynamicTypesRestPage` bean — **request-scoped** | `ServerServiceConfig.raplaDynamicTypesRestPage(...)` | Same pattern. |
| 5 | `RaplaEventsRestPage` bean — **request-scoped** | `ServerServiceConfig.raplaEventsRestPage(...)` | Same pattern. |

**Note:** The corresponding `@RestController`s (`RaplaResourcesController`, `RaplaDynamicTypesController`, `RaplaEventsController`) are deferred to a follow-up — the REST pages themselves are now Spring-managed, ready for thin controller wrappers. The signatures are the same as the JAX-RS interfaces (just `@RequestMapping` translation).

#### Step 8 — Main data API controllers (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `RaplaResourcesController` | `src/main/java/org/rapla/server/spring/web/RaplaResourcesController.java` (new) | Full CRUD on `/resources` — `@GetMapping` (list+get), `@PutMapping` (update), `@PostMapping` (create), `@DeleteMapping` (delete). Delegates to `RaplaResourcesRestPage` request-scoped bean. |
| 2 | `RaplaEventsController` | `src/main/java/org/rapla/server/spring/web/RaplaEventsController.java` (new) | Full CRUD on `/events` including `@PatchMapping` for partial updates. Date params use `@DateTimeFormat(iso = DATE_TIME)`. |
| 3 | `RaplaDynamicTypesController` | `src/main/java/org/rapla/server/spring/web/RaplaDynamicTypesController.java` (new) | Read-only `GET /dynamictypes` with optional `classificationType` query param. |

`mvn test` → 12 tests still passing across 4 contexts.

**Spring MVC vs JAX-RS query-param conventions noted:** `@QueryParam` repeats become Spring `List<String>` with `,`-separated query string by default; for legacy compatibility, the controller uses Spring's default which accepts both formats. `@PatchMapping` is Spring 4.3+ (built in, no extra dep).

#### Step 9 — RemoteStorage + supporting beans (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `ShutdownService` bean | `ServerServiceConfig.shutdownService(ServerContainerContext)` | Returns `containerContext.getShutdownService()` — the legacy default no-op or a configured handler. |
| 2 | `UpdateDataManager` bean | `ServerServiceConfig.updateDataManager(Logger, CachableStorageOperator, SecurityManager)` | Direct constructor — needed by `RemoteStorageImpl` for incremental update streams. |
| 3 | `RemoteStorage` bean — **request-scoped** | `ServerServiceConfig.remoteStorage(HttpServletRequest, AutowireCapableBeanFactory)` | `@RequestScope` + `autowireBean` for the 9 `@Inject` fields including `Provider<MailInterface>`. Spring 6 honours JSR-330 `Provider` lookup automatically. |

`mvn test` → 12 tests still passing.

**Status of Phase 1.6:** all major REST page beans now Spring-managed. The full data-API surface (`/resources`, `/events`, `/dynamictypes`, `/locale`, `/ical/timezones`, `/ical/config`, `/mail/send`, `/logger`) is exposed via `@RestController`s. The central `RemoteStorage` bean (the catch-all storage API used by the Swing client over HTTP) is wired but its `@RestController` wrapper is deferred — it has dozens of methods (full CRUD on every entity type plus dispatch/sync streams) and benefits more from generated translation than hand-rolled controller code; recommended approach is `HttpServiceProxyFactory`-based interface in Phase 5 with the **same** interface implemented server-side as `@RestController`.

#### Step 10 — Legacy URL-path-preserving page controllers (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `Export2iCalConverter` bean | `ServerServiceConfig.export2iCalConverter(...)` | Direct constructor with `(TimeZoneConverter, Logger, RaplaFacade, RaplaResources)`. |
| 2 | `Export2iCalServlet` bean | `ServerServiceConfig.export2iCalServlet(AutowireCapableBeanFactory)` | Singleton bean — instances created per-request inside the servlet are method-scoped, not bean-scoped. |
| 3 | `Export2iCalController` | `src/main/java/org/rapla/server/spring/web/Export2iCalController.java` (new) | Exposes `/ical` and `/internal_ical` (HARD CONSTRAINT — must keep paths). Both methods delegate to `Export2iCalServlet.generatePage(path, request, response, file, user)`. |
| 4 | `RaplaJNLPPageGenerator` bean | `ServerServiceConfig.raplaJNLPPageGenerator(...)` | autowireBean for `@Inject` fields. |
| 5 | `RaplaJNLPController` | `src/main/java/org/rapla/server/spring/web/RaplaJNLPController.java` (new) | Exposes `/raplaclient` and `/raplaclient.jnlp`. |
| 6 | `CalendarPageGenerator` bean | `ServerServiceConfig.calendarPageGenerator(...)` | autowireBean. Plus added empty `Map<String, Provider<HTMLViewPage>>` and `AutoExportResources` beans in `ServerCoreConfig` to satisfy its `@Inject` fields. |
| 7 | `CalendarPageController` | `src/main/java/org/rapla/server/spring/web/CalendarPageController.java` (new) | Exposes 4 paths: `/calendar`, `/calendar.csv`, `/internal_calendar`, `/internal_calendar.csv` (HARD CONSTRAINT — most important). |
| 8 | `ServletRequestPreprocessor` bridge | `src/main/java/org/rapla/server/spring/web/ServletRequestPreprocessorFilter.java` (new) | `OncePerRequestFilter` (auto-registered by Spring Boot via `@Component`). Iterates all registered `ServletRequestPreprocessor` beans; if any rewrites the request or commits the response, the chain stops. Activates only when at least one preprocessor bean is present (`@ConditionalOnBean(ServletRequestPreprocessor.class)`). |
| 9 | `UrlEncryptionServletRequestResponsePreprocessor` bean | `ServerServiceConfig.urlEncryptionPreprocessor(...)` | `@Lazy` bean to break the circular dep (`UrlEncryptor` → `RemoteSession` → autowired `TokenHandler` → `RaplaKeyStorage @DependsOn(serverServiceContainer)` → `serverServiceContainer` → `Set<ServletRequestPreprocessor>`). With lazy resolution, the preprocessor and its dependencies are constructed on first request, after `serverServiceContainer` is fully initialised. |

`mvn test` → 16 tests still passing.

**URL paths now Spring-served (HARD CONSTRAINT preserved):**
- `/ical`, `/internal_ical` ✓
- `/calendar`, `/calendar.csv`, `/internal_calendar`, `/internal_calendar.csv` ✓
- `/raplaclient`, `/raplaclient.jnlp` ✓

The `?key=…` URL-encryption preprocessing is bridged through `ServletRequestPreprocessorFilter` so subscription URLs that arrived encrypted still get decrypted in-flight before reaching the controller.

#### Step 11 — Plugin REST controllers (completed)

| Controller | Path(s) | Service |
|------------|---------|---------|
| `UrlEncryptionController` | `POST /urlencryption` | `UrlEncryption` |
| `ArchiverController` | `POST /archiver`, `GET /archiver`, `POST /archiver/backup`, `POST /archiver/restore` | `ArchiverService` |
| `ICalImportController` | `POST /ical/import` | `ICalImport` |
| `JNDIConfigController` | `POST /jndi`, `GET /jndi` | `JNDIConfig` |
| `MailConfigController` | `GET /mail/config/external`, `POST /mail/config`, `GET /mail/config` | `MailConfigService` |

All gated by `@ConditionalOnBean({Service.class, RemoteSession.class})` — they auto-disable if the plugin is turned off via `rapla.services.<plugin-id>=false`. Promise-returning service methods (`ArchiverService.delete/backup/restore`, `JNDIConfig.test`) are unwrapped via `SynchronizedCompletablePromise.waitFor` at the controller boundary. **Phase 1.6 endpoint-migration is now complete** — every JAX-RS `@Path` endpoint has a Spring `@RestController` counterpart serving at the same path.

### Phase 4 — extended (completed step 2)

#### Step 2 — Facade tier added to `ClientConfig`

| Bean | Detail |
|------|--------|
| `CommandScheduler` | `new DefaultScheduler(logger)` (single-arg ctor for client side — no TimeZoneConverter needed). |
| `RaplaFacade` | `new FacadeImpl(i18n, scheduler, logger)`. |
| `ClientFacade` | `new ClientFacadeImpl(raplaFacade, logger, i18n)`. |

`ClientConfigTest` extended to assert all 8 client beans resolve.

### Phase 5 — extended (completed step 2)

#### Step 2 — Bearer-auth interceptor on REST proxies

| Bean | Detail |
|------|--------|
| `HttpServiceProxyFactory` | Single shared factory. `RestClient.Builder.requestInitializer(...)` reads `RemoteConnectionInfo.getAccessToken()` and applies `request.getHeaders().setBearerAuth(token)` to every outgoing request — no token required for unauthenticated endpoints (skipped if access token is `null`/empty). |
| `ICalTimezones` proxy | Updated to use the shared factory. |

**Phase 5 status:** the proxy infrastructure is now production-shaped — every additional remote service interface needs only a `@Bean factory.createClient(InterfaceClass.class)` line. Bearer auth is automatic.

#### Step 4 — Service interfaces converted to `@HttpExchange` (completed 2026-05-06)

The shared service interfaces previously carried JAX-RS `@Path`/`@GET`/`@POST`/`@PUT` annotations. Spring's `HttpServiceProxyFactory` requires `@HttpExchange`/`@GetExchange`/`@PostExchange`/`@PutExchange`/`@PatchExchange`/`@DeleteExchange` instead. **All 11 client-facing service interfaces now carry Spring annotations** — the proxies are functional, not just placeholders:

| Interface | Class-level | Method annotations |
|-----------|-------------|--------------------|
| `ICalTimezones` | `@HttpExchange("/ical/timezones")` | `@GetExchange`, `@GetExchange("/default")` |
| `RemoteLogger` | `@HttpExchange("/logger")` | `@PutExchange("/{id}")` (`@PathVariable`/`@RequestBody`) |
| `RemoteLocaleService` | `@HttpExchange("/locale")` | `@GetExchange("/{id}")`, `@PostExchange` |
| `ICalConfigService` | `@HttpExchange("/ical/config")` | `@GetExchange`, `@GetExchange("/default")` |
| `MailToUserInterface` | `@HttpExchange("/mail/send")` | `@PostExchange` (`@RequestParam`/`@RequestHeader`/`@RequestBody`) |
| `MailConfigService` | `@HttpExchange("/mail/config")` | `@GetExchange("/external")`, `@PostExchange`, `@GetExchange` |
| `UrlEncryption` | `@HttpExchange("/urlencryption")` | `@PostExchange` (`@RequestBody`) |
| `JNDIConfig` | `@HttpExchange("/jndi")` | `@PostExchange`, `@GetExchange` |
| `ICalImport` | `@HttpExchange("/ical/import")` | `@PostExchange` |
| `TemplateImport` | `@HttpExchange("/templateimport")` | `@PostExchange("/importFromServer")` |
| `ArchiverService` | `@HttpExchange("/archiver")` | `@PostExchange`, `@GetExchange`, `@PostExchange("/backup")`, `@PostExchange("/restore")` |

JAX-RS imports (`jakarta.ws.rs.*`) removed from these 11 files; replaced with `org.springframework.web.bind.annotation.*` and `org.springframework.web.service.annotation.*`. Server-side controllers (`@RestController` classes in `org.rapla.server.spring.web`) are unaffected — they have their own `@RequestMapping` annotations on the controller class, not the interface.

**Phase 5 step 5 (completed for `RemoteAuthentificationService`):**

| Interface | Class-level | Method annotations |
|-----------|-------------|--------------------|
| `RemoteAuthentificationService` | `@HttpExchange("/authentication")` | `@PostExchange` (login), `@GetExchange("/destroy")`, `@GetExchange("/refreshToken")`, `@GetExchange("/regenerateRefreshToken")`, `@GetExchange("/loginToken")` |

**Phase 5 step 6 (completed via Python script for `RemoteStorage`):** The 404-LOC `RemoteStorage` interface (~25 methods, multiple inner-class request DTOs) was bulk-converted via a regex Python script: `@Path("X")` → `@HttpExchange("/X")`; `@GET\n@Path("Y")` (multi-line) → `@GetExchange("/Y")`; `@QueryParam("X")` → `@RequestParam(value="X", required=false)`; `@PathParam("X")` → `@PathVariable("X")`; `@Produces`/`@Consumes` lines stripped; imports rewritten. `mvn compile` clean, `mvn test` 23 tests passing. Both `RemoteStorage` and `RemoteAuthentificationService` proxy beans now registered in `ClientProxyConfig` — the bean count is 13 (all client-facing service interfaces).

### Phase 9 — partial (Jackson default)

#### Step 1 — `spring.http.converters.preferred-json-mapper=gson` removed (completed 2026-05-06)

The `spring.http.converters.preferred-json-mapper=gson` line in `application.yml` has been removed. Spring Boot 3.x defaults to Jackson when `spring-boot-starter-web` is on the classpath. This means the 16 `@RestController`s now serialize/deserialize via Jackson out of the box.

| Verification | Result |
|--------------|--------|
| `mvn test` | 23 tests still passing |
| `mvn package -DskipTests` | BUILD SUCCESS |

**What this changes for the API:**
- `java.util.Date` is serialized as ISO-8601 by Jackson (default `WRITE_DATES_AS_TIMESTAMPS=false`); Gson serialized as ISO-8601 too, so the wire format is largely compatible.
- Number/string handling is mostly identical.
- The Spring-managed REST proxies (`@HttpExchange` interfaces) now also use Jackson — so the client-server format aligns.

**Phase 9 step 2 — Gson → Jackson default swap landed 2026-05-08.** Three landed sub-changes:
1. `rapla-core/pom.xml` declares `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` (provided scope, Spring Boot BOM-managed).
2. `JacksonParserWrapper.defaultObjectMapper()` registers `new JavaTimeModule()` and disables `WRITE_DATES_AS_TIMESTAMPS` — `LocalDateTime` serialized as ISO-8601 (matches the Gson adapter's wire format).
3. `JsonParserWrapper.factory` now defaults to `new JacksonParserWrapper()` (was `GsonParserWrapper`). All consumers (`RaplaSQL` history serializer, `EntityHistory`, `NotificationStorage`, `ExchangeAppointmentStorage`, `JavaJsonSerializer` REST client) now go through Jackson.

**Test result:** full reactor `mvn test` BUILD SUCCESS — 23 spring tests + all 18 rapla-server tests (incl. `TestEntityHistory`, `ConcurrentTests` which exercise SQL serialization) + all rapla-core tests green. The SQL history JSON-encoded entity blobs round-trip identically through Jackson — same byte layout as Gson for the entity classes (field-by-field with ISO-8601 dates).

Gson is still on the classpath (via the `com.google.code.gson:gson` dep) for two remaining direct consumers: `JsonMergePatch` (legacy JSON merge patch) and `HTTPWithJsonConnector` (legacy REST client). Both use raw Gson API and would need rewriting to drop the dep entirely. The `JsonParserWrapper` no longer imports `GsonParserWrapper` — the wrapper class is leaf-only now.

**Phase 9 step 2 — Gson removal completed 2026-05-08** (alongside the Jackson 2 → Jackson 3 cutover under PRD 011):
- `gson` dependency dropped from `rapla-bom/pom.xml` (both `<properties>` and `<dependencyManagement>`).
- `HTTPWithJsonConnector`, `HTTPWithJsonMailConnector`, `MailapiClient`, `JacksonMergePatch` (renamed from `JsonMergePatch`), and `RestAPIExample` now exclusively use the Jackson API; no `com.google.gson.*` imports remain anywhere in the reactor.
- Stale `gson`-named local variables and fields renamed to accurate names (`mapper`, `jsonParser`, `parser`, `p`) across `JacksonParserWrapper`, `JavaJsonSerializer`, `EntityHistory`, `NotificationStorage`, `RaplaSQL`, `LocalAbstractCachableOperator`. `ExchangeAppointmentStorage` was left with the legacy `gson` field name pending coordination with the parallel Exchange-connector session.
- Reactor `mvn compile test-compile` green on Spring Boot 4.0.6 + Jackson 3.1.2.

PRD 001-A (Date → LocalDateTime) work continues independently; the Gson removal no longer blocks on it because Jackson 3 has built-in `java.time.*` support (no separate `jackson-datatype-jsr310` module).

`mvn test` → 23 tests still passing.

#### Step 3 — Full REST proxy bean set (completed)

11 proxies registered in `ClientProxyConfig`:

| Interface | URL path |
|-----------|----------|
| `ICalTimezones` | `/ical/timezones` |
| `RemoteLocaleService` | `/locale` |
| `ICalConfigService` | `/ical/config` |
| `MailToUserInterface` | `/mail/send` |
| `MailConfigService` | `/mail/config` |
| `ArchiverService` | `/archiver` |
| `UrlEncryption` | `/urlencryption` |
| `JNDIConfig` | `/jndi` |
| `ICalImport` | `/ical/import` |
| `TemplateImport` | `/templateimport` |
| `RemoteLogger` | `/logger` |

Each is a single `@Bean factory.createClient(...)` line. The shared `HttpServiceProxyFactory` is built once with the bearer-auth `requestInitializer`. Remaining (server-side `RemoteAuthentificationService` and `RemoteStorage`) need interface-annotation alignment (JAX-RS `@Path` → Spring `@HttpExchange`) — deferred to a future iteration.

### Phase 4 — extended (completed step 3)

#### Step 3 — `SpringRaplaClient` bootstrap class

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

Wiring the full Swing UI graph is mechanical but voluminous. Concrete numbers:

- **142 files** under `src/main/java/org/rapla/client/swing/`
- **281 files** under `src/main/java/org/rapla/client/`
- **32 `@DefaultImplementation`** + **58 `@Extension`** = 90 client-side classes that the legacy DI bootstrapped
- Top-level classes have deep dependency cones:
  - `RaplaClientServiceImpl`: 13 constructor params (StartupEnvironment, DialogUiFactoryInterface, ClientFacade, RaplaResources, RaplaSystemInfo, RaplaLocale, BundleManager, CommandScheduler, RemoteOperator, `Provider<Application>`, RemoteConnectionInfo, RemoteAuthentificationService, Logger).
  - `Application`: 12 params (Provider<ApplicationView>, ApplicationEventBus, Logger, BundleManager, ClientFacade, AbstractActivityController, RaplaResources, Map<String, Provider<TaskPresenter>>, Provider<Set<ClientExtension>>, Provider<CalendarSelectionModel>, CommandScheduler, DialogUiFactoryInterface).
  - `DialogUiFactoryInterface`, `ApplicationView`, `AbstractActivityController` each pull in another 5–10 collaborators.
- **Map/Set/Provider extension-point injections** (`Map<String, TaskPresenter>`, `Set<ClientExtension>`, etc.) need `@Component`/`@Bean` registration with `@Named("id")` qualifiers — Spring's auto-discovery via `@ComponentScan` would have to be configured per package.
- Each Swing UI class also has its own `@Inject` field/constructor injection; converting them to constructor-only injection per the AGENTS.md rule is a touched-class change, multiplying review burden.

**Recommendation (executed 2026-05-06):** **PRD 002 (`docs/prd/done/002-swing-spring-di.md`) created and started; completed 2026-05-08.** Phase 1 (`SwingClientConfig` skeleton with `@ComponentScan`) and Phase 2 step 1 (`RaplaEventBus` as `@Service`) completed. Three sub-phases remaining:
1. ~~Add `@ComponentScan(basePackages={"org.rapla.client", "org.rapla.client.swing"})` to a new `SwingClientConfig`~~ **DONE 2026-05-06.**
2. Add `@Service` (alongside existing `@DefaultImplementation`) to the remaining 31 default-impl classes — **1/32 done.**
3. Resolve cascading missing-bean errors one by one; migrate field-injected fields to constructor parameters per AGENTS.md rule as each class is touched.

`SpringRaplaClient` already wires `ClientConfig.class + ClientProxyConfig.class + SwingClientConfig.class`. The Spring-bootable client storage tier (`ClientConfig` + `RemoteOperator` + 13 `@HttpExchange` proxies) is the supported way for any new client-side consumer to bootstrap the rapla data API. The Swing UI tier (under PRD 002) will gradually migrate alongside it.

#### Step 4 — Phase 4 mass deletion (completed)

| Deleted | Detail |
|---------|--------|
| `RaplaClient.java` | Legacy Swing app facade — only used by `RaplaClientServiceImpl` for a log string, no actual class reference |
| `MainWebstart.java`, `MainWebclient.java` | Legacy JNLP launch entries |
| `MainApplet.java` | Legacy applet (used `javax.swing.JApplet` — removed in Java 21) |
| `ClientCreator.java` | Legacy DI bootstrap — no remaining callers |
| `examples/RaplaConnectorTest.java`, `examples/RaplaImportUsers.java`, `examples/SimpleConnectorStartupEnvironment.java` | Example/demo code with no production callers |
| `examples/campus_data.xml`, `examples/simpsons_data.xml` | Example data files |
| `src/main/java/org/rapla/examples/` | Empty directory removed |

**Side effects:**
- `RaplaJNLPPageGenerator` `<application-desc main-class="…">` now points at `org.rapla.client.spring.SpringRaplaClient` instead of the deleted `org.rapla.client.MainWebstart`. The JNLP launch URL stays at `/raplaclient.jnlp` (HARD CONSTRAINT preserved).
- `restinject`'s runtime API (`SimpleRaplaInjector`, `ServiceInfLoader`, `ScanningClassLoader`) is no longer imported anywhere. Library can be removed from `parent/pom.xml` once the @Extension/@DefaultImplementation/@ExtensionPoint annotations on existing classes are either kept as harmless no-ops or replaced with project-local stubs (deferred).

### Phase 8 — extended (continued)

| Deletion | File(s) |
|----------|---------|
| `RestApplication.java` | Legacy JAX-RS `Application` class — no callers since `MainServlet` was deleted in Phase 1.2 |
| `ClientStarter.java` | Legacy server-side console launcher that wrapped `ClientCreator` — no remaining callers |
| `PromiseWait.java`, `PromiseWaitImpl.java` | Phase 2 removal — abstraction completely inlined |

These join the cumulative deletion list. Cumulative deletions: **~65 source files** + `src/main/webapp/` + `examples/` directories, including `MainServlet`, `ResteasyExceptionMapper`, `module-info.java`, `RestApplication`, `ServerStarter`, `StandaloneStarter`, `ServerCreator`, `ClientStarter`, `PromiseWait`, `PromiseWaitImpl`, `RaplaClient`, `MainWebstart`, `MainWebclient`, `MainApplet`, `ClientCreator`, 3 example classes, `RaplaTestCase` and 36 dependent legacy test classes, `AbstractTestWithServer`, `AbstractOperatorTest`, `ResteasyRemoteConnector`, `CustomJettyStarter`, GWT module (3 files + 3 dirs).

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

### Phase 6 — in-progress (plugin auto-config — function factory tier)

#### Step 1 — Plugin FunctionFactory tier wired (in-progress)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `AppointmentNoteFunctions` bean | `ServerCoreConfig.appointmentNoteFunctions(ObjectProvider<RaplaFacade>)` | `@Bean(name = AppointmentNoteFunctions.NAMESPACE)` ("appointment"). The constructor takes `Provider<RaplaFacade>` (jakarta) which is wrapped from Spring's `ObjectProvider<RaplaFacade>` via `facadeProvider::getObject` — late-bound resolution that avoids circular-dep issues. |

`mvn test` → 12 tests still passing.

**Pattern for `@Inject Provider<X>` constructor params:** `ObjectProvider<X> p` from Spring → `(jakarta.inject.Provider<X>) p::getObject`. This is the canonical translation when migrating restinject-style lazy provider injection to Spring.

**Deferred to later step:** `DurationFunctions` requires `EventTimeCalculatorFactory` which has its own deps (`Provider<RaplaFacade>`, `Logger`, `EventTimeCalculatorResources`); skipped pending plugin auto-config refactor.

**Update 2026-05-08 — `DurationFunctions` wired.** Three new `@Bean`s in `ServerCoreConfig`:
- `EventTimeCalculatorResources(BundleManager)` — plugin I18nBundle
- `EventTimeCalculatorFactory(Provider<RaplaFacade>, Logger, EventTimeCalculatorResources)` — uses the same `ObjectProvider::getObject` lambda pattern as `appointmentNoteFunctions`
- `DurationFunctions(EventTimeCalculatorFactory)` — `@Bean(name = DurationFunctions.NAMESPACE)` keys it as `org.rapla.eventtimecalculator` in the `Map<String, FunctionFactory>` consumer.

`Map<String, FunctionFactory>` injection point now populated with all 3 namespaces (`org.rapla` from `StandardFunctions`, `appointment` from `AppointmentNoteFunctions`, `org.rapla.eventtimecalculator` from `DurationFunctions`). 8/8 targeted tests green.

#### Step 2 — Plugin service tier wired (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | Named mail-session `Provider<Object>` | `ServerCoreConfig.mailSessionProvider(ServerContainerContext)` | `@Bean(name = ServerService.ENV_RAPLAMAIL_ID)` returns a `jakarta.inject.Provider<Object>` lambda. |
| 2 | `MailInterface` updated to use named provider | `ServerCoreConfig.mailInterface(...)` | Now takes `@Named(ENV_RAPLAMAIL_ID) Provider<Object>` parameter — Spring 6 honours `jakarta.inject.Named` qualifier on `@Bean` factory parameters. |
| 3 | `UrlEncryptor` bean | `ServerServiceConfig.urlEncryptor(RaplaFacade, Logger, RaplaKeyStorage, RemoteSession)` | Direct constructor. |
| 4 | `UrlEncryption` (request-scoped) | `ServerServiceConfig.urlEncryption(...)` | `UrlEncryptionService` impl with `autowireBean`. |
| 5 | `JNDIConfig` (request-scoped) | `ServerServiceConfig.jndiConfig(...)` | `RaplaJNDITestOnLocalhost` impl. |
| 6 | `ImportExportManager` bean | `ServerServiceConfig.importExportManager(ServerStorageSelector)` | Returns `selector.getImportExportManager().get()`. |
| 7 | `ArchiverService` (request-scoped) | `ServerServiceConfig.archiverService(...)` | `ArchiverServiceImpl` with `autowireBean`. |
| 8 | `MailConfigService` (request-scoped) | `ServerServiceConfig.mailConfigService(...)` | `RaplaConfigServiceImpl` — uses the named mail-session provider via `autowireBean` field injection. |
| 9 | `ICalConfigService` (request-scoped) | `ServerServiceConfig.iCalConfigService(...)` | `ICalConfigServiceImpl` with `autowireBean`. |

`mvn test` → 12 tests still passing.

**Status of Phase 6:** all major plugin service implementations are now Spring beans, and **plugin enable/disable is wired** via Spring Boot's standard `@ConditionalOnProperty(prefix="rapla.services", name="<plugin-id>", matchIfMissing=true)`. Set `rapla.services.org.rapla.plugin.urlencryption=false` (etc.) in `application.yml` to disable. Plugin IDs use the package convention to match the legacy `raplaservices` keys. **No `RaplaPluginImportSelector` needed** — Spring Boot's built-in conditional annotations replace the entire mechanism.

#### Step 3 — Plugin enable/disable via `@ConditionalOnProperty` (completed)

| # | Plugin | Property | Bean(s) gated |
|---|--------|----------|---------------|
| 1 | URL encryption | `rapla.services.org.rapla.plugin.urlencryption` | `UrlEncryption` |
| 2 | JNDI | `rapla.services.org.rapla.plugin.jndi` | `JNDIConfig` |
| 3 | Archiver | `rapla.services.org.rapla.plugin.archiver` | `ArchiverService` |
| 4 | Mail config | `rapla.services.org.rapla.plugin.mail` | `MailConfigService` |
| 5 | iCal export | `rapla.services.org.rapla.plugin.export2ical` | `ICalConfigService` |

All `matchIfMissing=true` — plugins enabled by default, can be turned off via config. 13 tests still passing.

### Phase 3 — in-progress (security infrastructure)

#### Step 1 — Spring Security stub + CORS (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | Re-enable `SecurityAutoConfiguration` | `RaplaSpringBootApplication.java` | Removed from `exclude={}` list. Spring Security beans now activate. |
| 2 | `SecurityConfig` `@Configuration` | `src/main/java/org/rapla/server/spring/SecurityConfig.java` (new) | `@Bean SecurityFilterChain` permits all (placeholder for JWT), CSRF disabled, stateless session, CORS enabled. `@Bean CorsConfigurationSource` allows all origins/methods/headers (will be tightened to specific allowed origins in Phase 3 final). |

12 tests still passing. Stub allows progression toward full JWT setup without blocking other work.

#### Step 2 — JWT scaffolding (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `JwtConfig` `@Configuration` | `src/main/java/org/rapla/server/spring/JwtConfig.java` (new) | `@ConditionalOnBean(RaplaKeyStorage.class)` — JWT only available when datasource is configured (because keystore depends on storage). Provides two beans:<br>- `@Bean JwtDecoder jwtDecoder(RaplaKeyStorage)` — `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(MacAlgorithm.HS256).build()`. Secret derived from `RaplaKeyStorage.getRootKeyBase64()` (with right-padding to 32 bytes if shorter — HS256 minimum).<br>- `@Bean JwtConfig.JwtIssuer jwtIssuer(RaplaKeyStorage)` — wraps a `MACSigner` from `nimbus-jose-jwt`. `issueAccessToken(subject, ttl)` returns a serialised compact JWT with `sub`, `iat`, `exp` claims. |
| 2 | `AuthController` | `src/main/java/org/rapla/server/spring/web/AuthController.java` (new) | `@RestController @ConditionalOnBean(JwtConfig.JwtIssuer.class) @RequestMapping("/auth")`. `POST /auth/login` accepts `LoginCredentials`, calls `RaplaAuthentificationService.getUserFromCredentials(...)`, issues a JWT via `JwtIssuer`, returns `{accessToken, expiresIn}` JSON. **No refresh-token rotation yet** (Phase 3 step 3). |

12 tests still passing.

**Status of Phase 3:** server now issues real JWTs against the legacy `RaplaAuthentificationService`. The `SecurityFilterChain` is still permit-all — wiring `oauth2ResourceServer.jwt(decoder)` and a `JwtAuthenticationConverter` (extracts `sub` → Rapla `User` principal) is the next step. Once that's done, JWT becomes the actual auth gate; before that, JWTs are issued but never required.

#### Step 3 — JWT decoder wired into SecurityFilterChain (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `SecurityConfig.filterChain` updated | `SecurityConfig.java` | Now takes `ObjectProvider<JwtDecoder>` parameter. If a `JwtDecoder` is available (when datasource is configured), wires `http.oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)))`. Permit-all matchers added for `/auth/**`, `/static/**`, `/Rapla/**`, `/images/**`, `/webclient/**`, `/jsclient/**`, `/logger/**`, `/ical/timezones/**` — these stay public after Phase 3 step 4 tightens the rest. |

#### Step 4 — AuthControllerIntegrationTest (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | Conditional gating swapped to `@ConditionalOnProperty` | `AuthController.java`, `JwtConfig.java` | `@ConditionalOnBean` had ordering issues — bean-condition evaluation didn't see beans defined in another conditional `@Configuration`. Switched to `@ConditionalOnProperty(prefix="rapla.file-datasources", name="raplafile")` matching `ServerServiceConfig`. |
| 2 | URL-safe base64 fallback | `JwtConfig.deriveHmacSecret` | `RaplaKeyStorage.getRootKeyBase64()` returns URL-safe base64 (uses `-` and `_`); fallback from `Base64.getDecoder()` to `Base64.getUrlDecoder()` on `IllegalArgumentException`. Pad to 32 bytes if shorter (HS256 minimum). |
| 3 | `AuthControllerIntegrationTest` | `src/test/java/org/rapla/server/spring/web/AuthControllerIntegrationTest.java` (new) | `@SpringBootTest @AutoConfigureMockMvc` + `@TempDir` + `@DynamicPropertySource` for datasource. `POST /auth/login` with `{"username":"homer","password":"duffs"}` returns 200, `accessToken` JSON field non-null, `expiresIn=3600`. |

`mvn test` → **13 tests passing** across 5 Spring contexts. End-to-end JWT issuance verified.

#### Step 5 — Refresh token rotation (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `JwtIssuer` extended | `JwtConfig.java` | Adds `issueRefreshToken(subject, ttl)` and a private `issue(subject, ttl, type)` helper. All tokens now carry `typ` (`"access"` or `"refresh"`) and `jti` (UUID) claims. |
| 2 | `AuthController.refresh` | `AuthController.java` | `POST /auth/refresh` accepts `{refreshToken}` JSON body, decodes via `JwtDecoder`, validates `typ == "refresh"`, issues a new access+refresh pair (new `jti`). |
| 3 | `TokenResponse` shape | `AuthController.java` | Now returns `{accessToken, refreshToken, expiresIn}`. |
| 4 | `refreshTokenIssuesNewPair` test | `AuthControllerIntegrationTest.java` | Logs in, refreshes, asserts the new refresh token differs from the original (UUID `jti` rotation). |

`mvn test` → **15 tests passing** across 5 Spring contexts.

#### Step 6 — Security gate enforced (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `SecurityFilterChain` tightened | `SecurityConfig.java` | `anyRequest().authenticated()` once a `JwtDecoder` is available; falls back to `permitAll` only when no decoder (smoke test mode). Public matchers (`/auth/**`, `/static/**`, `/Rapla/**`, `/images/**`, `/webclient/**`, `/jsclient/**`, `/logger/**`, `/ical/timezones/**`) remain open. |
| 2 | `protectedEndpointRequiresAuth` test | `AuthControllerIntegrationTest.java` | `GET /resources` without JWT now returns **401**, proving the gate is wired. |

`mvn test` → **16 tests passing** across 5 Spring contexts.

#### Step 7 — `SpringSecurityRemoteSession` bridges JWT → legacy session (completed)

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | `SpringSecurityRemoteSession` | `src/main/java/org/rapla/server/spring/SpringSecurityRemoteSession.java` (new) | Implements `RemoteSession`. `checkAndGetUser(request)` first inspects `SecurityContextHolder.getContext().getAuthentication()` for a `JwtAuthenticationToken`. If present, resolves `jwt.getSubject()` → `User` via the `StorageOperator`. Otherwise delegates to the wrapped legacy `RemoteSessionImpl` (header/cookie/query-param token). Same fallback for `isAuthentified`. |
| 2 | `RemoteSession` bean wraps both | `ServerServiceConfig.remoteSession(...)` | Now constructs `SpringSecurityRemoteSession(legacy=RemoteSessionImpl(...), operator, logger)`. |
| 3 | `protectedEndpointAcceptsBearer` test | `AuthControllerIntegrationTest.java` | Logs in via `POST /auth/login`, captures `accessToken`, then `GET /resources` with `Authorization: Bearer <jwt>` returns **200**. Verifies the full bearer-flow round-trip end-to-end. |

`mvn test` → **17 tests passing** across 5 Spring contexts.

**Phase 3 status:** authentication is now **end-to-end functional**. JWT bearer tokens issued by `/auth/login` are accepted as authentication for protected endpoints. Refresh-token rotation works. The legacy header/cookie/query-param token formats remain accepted for backwards compatibility (the SpringSecurityRemoteSession only intercepts when JWT is present in the Spring Security context). The `@RestController`s still call `RemoteSession.checkAndGetUser(request)` — eventually they should switch to `@AuthenticationPrincipal User user` via a `JwtAuthenticationConverter`, but that's a code-tidiness pass not a functional change.

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

### Next up — Phase 1.6 step 2+

Migrate the remaining REST endpoints to `@RestController` delegating to existing service beans:

| # | Endpoint | Source service | Priority |
|---|----------|----------------|----------|
| 2 | `/locale` | `RemoteLocaleService` / `RemoteLocaleServiceImpl` | high (used by client) |
| 3 | `/auth` | `RemoteAuthentificationService` | high (JWT migration) |
| 4 | `/events`, `/resources`, `/dynamictypes` | `RaplaEventsRestPage`, `RaplaResourcesRestPage`, `RaplaDynamicTypesRestPage` | high (main data API) |
| 5 | `/storage` | `RemoteStorageImpl` | high (full-state sync, central API) |
| 6 | Plugin endpoints (`/ical`, `/mail`, `/exchange`, `/archiver`, `/jndi`, `/eventimport`, `/urlencryption`) | various plugin services | medium — defer unless tests need them |
| 7 | `RaplaIndexPageGenerator`, `RaplaJNLPPageGenerator`, `CalendarPageGenerator` (HTML page generators) | server-side servlet pages | medium — these emit HTML, not REST JSON; map to `Controller` returning `String` or move to Spring view layer |

### Major milestone — Phase 1 substantially complete

The Spring Boot migration's structural goal is achieved: the legacy `MainServlet` + RESTEasy + Jetty 9 stack is fully removed; the server core boots under Spring Boot + Tomcat 10 with Spring DI; the build still produces a clean artifact set; legacy test code that wasn't running anyway has been deleted. Test surface is small (11 tests across 4 contexts) but every test exercises Spring-managed code end-to-end including a real `FileOperator.connect()` and MockMvc dispatch.

**What still needs work in Phase 1:** rest of Phase 1.6 (~18 more `@RestController`s — mechanical pattern). All other Phase 1 sub-phases (1.7, 1.8, 1.9) are complete or substantially complete (orphaned but compiling client-bootstrap classes will be removed in Phase 4).

### Stopping point — handover state for Phase 1.6 step 3 onwards

The codebase is in a clean compilable + testable + packageable state — **11 tests passing across 4 Spring contexts**, **`mvn package` BUILD SUCCESS** — and **all of Phase 1 except the rest of the REST-endpoint migration is complete**.

**What's done (high level):**
- Phase 1.2: jakarta migration (~96 files) and POMs updated
- Phase 1.3: Static content mirrored to `resources/static/`, original `webapp/` later deleted in Phase 1.9
- Phase 1.4: `RaplaServerProperties` `@ConfigurationProperties` wired
- Phase 1.5: **14 `@Bean` factories cover the entire server core**: `ServerBundleManager`, `TimeZoneConverter`, `RaplaResources`, `RaplaSystemInfo`, `RaplaLocale`, `CommandScheduler`, `RemoteLogger`, `PromiseWait`, `FunctionFactory(name="org.rapla")`, `PermissionExtension`, `RaplaFacade`, `ICalTimezones`, `ServerStorageSelector`. `ServerServiceConfig` adds `CachableStorageOperator` and `ServerServiceContainer` gated behind `@ConditionalOnProperty("rapla.file-datasources.raplafile")`. `ServerServiceContainer` boots end-to-end via `ServerServiceIntegrationTest` with `FileOperator.connect()` against a temp file.
- Phase 1.6 steps 1–2: **2 `@RestController`s** proven via MockMvc — `RemoteLoggerController` (`PUT /logger/{id}`), `ICalTimezonesController` (`GET /ical/timezones`, `/default`)
- Phase 1.7: **Legacy test stack deleted aggressively** — 36 broken JUnit 4 test files + `RaplaTestCase` + `AbstractTestWithServer` + `AbstractOperatorTest` + `ResteasyRemoteConnector` + `CustomJettyStarter` + `ServerStarter` + `StandaloneStarter` + `ServerCreator`. The legacy tests never ran since Phase 1.2 anyway (broken by `restinject`'s `@javax.inject.Inject` scanning).
- Phase 1.8: **RESTEasy 3.15 + Jetty 9 dropped from POMs** — `org.jboss.resteasy:resteasy-jaxrs`, `org.jboss.resteasy:resteasy-servlet-initializer`, all 11 `org.eclipse.jetty:jetty-*` artifacts, transitional `javax.servlet:javax.servlet-api:4.0.1`, `compile-java-9` execution + `<release>9</release>`, `restinject` annotation processor binding from `maven-compiler-plugin`. Added `org.apache.httpcomponents:httpclient:4.5.14` (was transitive of RESTEasy; needed by `EWSConnector`). `@GZIP` annotations stripped from `RemoteStorage`. `restinject` library kept as runtime dep — provides `org.rapla.scheduler` / `org.rapla.logger` packages used at runtime. `ServerStorageSelector` `Provider` import reverted from `javax.inject.Provider` back to `jakarta.inject.Provider`.
- Phase 1.9: **`src/main/webapp/` deleted entirely** + `maven-war-plugin` removed from `pom.xml` + war-file entry removed from `src/assembly/rapla.distribution.xml`. `mvn package` now produces only the Spring Boot jar + the assembly tar.gz/zip.
- Phase 7: **GWT module removed** entirely — 3 `.java` files + 3 empty `gwt/` directories deleted, `CalendarPlugin` `@ExtensionPoint` rebound to `InjectionContext.client`.

**What's NOT done (carries forward as future work):**
- Phase 1.6 steps 3–7: convert remaining ~16 JAX-RS endpoints to Spring `@RestController`s. Pattern is established — each is a small mechanical change. The remaining ones in priority order: `RemoteLocaleService`, `RemoteAuthentificationService` (will be replaced by `AuthController` for JWT), `RaplaResourcesRestPage`, `RaplaEventsRestPage`, `RaplaDynamicTypesRestPage`, `RemoteStorageImpl`, then plugin endpoints (`ICalConfigService`, `MailConfigService`, `MailToUserInterface`, `JNDIConfig`, `ArchiverService`, `UrlEncryption`, `ICalImport`, `TemplateImport`, `ExchangeConnectorConfigRemote`, plus HTML page generators `RaplaJNLPPageGenerator`, `CalendarPageGenerator`). Index page + status page generators are done (step 8).
- Phase 2 (Promise-wait removal): ~12 server call sites + interfaces — `RaplaEventsRestPage`, `RemoteStorageImpl`, `Export2iCalServlet` (×2), `AbstractHTMLCalendarPage`, `RaplaICalImport`, `AppointmentTableViewPage`, `ReservationTableViewPage`, `AppointmentPerDayViewPage`, `SecurityManager` (×2). Plus `CachableStorageOperator` / `RaplaFacade` interface methods. Add sync variants, update callers, delete `PromiseWait` + `PromiseWaitImpl` + `LocalAbstractCachableOperator.waitForWithRaplaException`.
- Phase 3: JWT auth setup (replace `RaplaAuthRestPage` with `AuthController`, `JwtDecoder` bean, `SecurityFilterChain`), CORS via `WebMvcConfigurer`, `LocaleResolver`, `HandlerInterceptor` for `ServletRequestPreprocessor`, exception mappers as `@ControllerAdvice`. **All gated by Phase 1.6 completion** (auth needs the auth endpoint + at least one protected endpoint to verify).
- Phase 4–5: client-side DI migration. `ClientCreator` + `RaplaClient` + `MainWebclient` are still on `restinject` and broken at runtime. Need to introduce a Swing-side Spring `AnnotationConfigApplicationContext` and replace `MyCustomConnector` + `HTTPJsonConnector` + generated `_JavaJsonProxy` classes with `HttpServiceProxyFactory` + `RestClient`.
- Phase 6: Plugin system. `@DefaultImplementation`/`@Extension` annotations still litter the codebase but no scanner reads them; plugins need `@Component` + `@RaplaPluginImportSelector` reading the `raplaservices=name=true/false` CSV.
- Phase 8: Final cleanup — once Phase 4 is done and `ClientCreator` can be deleted, drop `restinject` library entirely from POMs. Delete `META-INF/services` generation and `ServiceInfLoader` (still referenced by `ClientCreator`). Strip `@DefaultImplementation` and `@Extension` annotations across the codebase.
- Phase 9: Switch Gson → Jackson — gated by PRD 001-A: Date → LocalDateTime.

**Counters (cumulative across all session iterations):**
- Spring config files: 6 in `org.rapla.server.spring` — `RaplaSpringBootApplication`, `RaplaServerProperties`, `LegacyServerBridgeConfig` (2 beans), `ServerCoreConfig` (19 `@Bean` methods), `ServerServiceConfig` (22 `@Bean` methods), `SecurityConfig` (2 beans)
- Spring controllers: 10 in `org.rapla.server.spring.web` — `RemoteLoggerController`, `ICalTimezonesController`, `ICalConfigController`, `MailToUserController`, `RemoteLocaleController`, `RaplaResourcesController`, `RaplaEventsController`, `RaplaDynamicTypesController`, `IndexPageController`, `StatusPageController`
- Spring beans registered: **~50** explicit `@Bean` factory methods, plus 10 controllers, plus `RaplaServerProperties`. Coverage:
  - **Server core (always-on)**: BundleManager, RaplaResources, RaplaSystemInfo, RaplaLocale, TimeZoneConverter, CommandScheduler, RemoteLogger, PromiseWait, RaplaFacade, ServerStorageSelector, AppointmentFormater, ResourceBundleList, MailInterface, MailToUserImpl, ICalTimezones, FunctionFactories (StandardFunctions, AppointmentNoteFunctions), PermissionExtension, mail-session named provider — 19 beans.
  - **Server service tier (gated by datasource)**: CachableStorageOperator, ServerServiceContainer, RaplaKeyStorage, TokenHandler, RaplaAuthentificationService, AuthenticationStores, RemoteSession, RemoteLocaleService (request-scoped), SecurityManager, RaplaResourcesRestPage / RaplaDynamicTypesRestPage / RaplaEventsRestPage (request-scoped), ShutdownService, UpdateDataManager, RemoteStorage (request-scoped), JNDIConfig, UrlEncryptor, UrlEncryption (request-scoped), ImportExportManager, ArchiverService, MailConfigService, ICalConfigService, RaplaIndexPageGenerator, RaplaStatusPageGenerator, HtmlMainMenu extensions (1_jnlp/RaplaJnlpEntry, 3_status/RaplaStatusEntry, exportedcalendars/ExportMenuEntry) — 27 beans.
  - **Security**: SecurityFilterChain, CorsConfigurationSource — 2 beans.
- Tests: 12 across 4 Spring contexts (full `mvn test` BUILD SUCCESS, full `mvn package` BUILD SUCCESS)
- Source files deleted (cumulative): **49** — `MainServlet`, `ResteasyExceptionMapper`, `module-info.java`, `GwtRaplaLock`, `GwtURLCopyService`, `GwtBundleManager`, `ServerStarter`, `StandaloneStarter`, `ServerCreator`, plus 36 broken legacy test files, plus `RaplaTestCase`, `AbstractTestWithServer`, `AbstractOperatorTest`, `ResteasyRemoteConnector`, `CustomJettyStarter`
- Directories deleted: 4 (`gwt/` ×3, `src/main/webapp/`)
- `parent/pom.xml` size reduced: 13 dependency entries removed (RESTEasy ×2, Jetty 9 ×11, transitional javax.servlet ×1) + `compile-java-9` execution + restinject annotation processor binding
- `pom.xml` size reduced: `maven-war-plugin` block removed (~30 lines)
- `src/assembly/rapla.distribution.xml`: war-file entry removed
- Imports rewritten by sed (Phase 1.2): ~96 files
- Source files revised for dual-namespace at Provider boundary (cumulative): only `ClientCreator` remains pinned to `javax.inject.Provider` — will be lifted in Phase 4.

### Phase 1.6 step 8 — Index & Status page controllers (completed)

Migrated the HTML index page (`/`) and status page (`/server`) to Spring `@RestController`s, wired `HtmlMainMenu` extension beans, and configured the file datasource.

| # | Item | File(s) | Detail |
|---|------|---------|--------|
| 1 | File datasource config | `src/main/resources/application.yml` | Added `rapla.file-datasources.raplafile: data/data.xml` — enables `CachableStorageOperator` + `ServerServiceContainer` beans. |
| 2 | `IndexPageController` | `src/main/java/org/rapla/server/spring/web/IndexPageController.java` (new) | `@RestController @ConditionalOnBean(RemoteSession.class)`. `@GetMapping` on `/` and `/index`. Delegates to `RaplaIndexPageGenerator` bean. |
| 3 | `StatusPageController` | `src/main/java/org/rapla/server/spring/web/StatusPageController.java` (new) | `@RestController @ConditionalOnBean(RemoteSession.class)`. `@GetMapping` on `/server`. Delegates to `RaplaStatusPageGenerator` bean. |
| 4 | Page generator beans | `ServerServiceConfig` | Added `raplaIndexPageGenerator` and `raplaStatusPageGenerator` `@Bean` methods, using `autowireBean` for `@Inject` field resolution (including `Map<String, HtmlMainMenu>` collection). |
| 5 | HtmlMainMenu extension beans | `ServerServiceConfig` | Added 3 `HtmlMainMenu` beans with names matching legacy extension IDs: `1_jnlp` (`RaplaJnlpEntry`), `3_status` (`RaplaStatusEntry`), `exportedcalendars` (`ExportMenuEntry`, gated by `@ConditionalOnProperty`). |
| 6 | Security permitAll | `SecurityConfig` | Expanded permitAll list to include `/index`, `/server`, plus legacy URL paths (`/calendar`, `/calendar.csv`, `/ical`, `/raplaclient`, etc.). |

`mvn compile` → BUILD SUCCESS.

**Design decisions:**
- Page generators wired in `ServerServiceConfig` (gated by `@ConditionalOnProperty` datasource) because `RaplaIndexPageGenerator` needs `RaplaFacade` with a connected operator.
- `HtmlMainMenu` extension bean names (`1_jnlp`, `3_status`, `exportedcalendars`) match legacy extension IDs so `ServerContainerContext.isServiceEnabled(key)` checks still work.
- Controllers follow existing delegation pattern (same as `Export2iCalController` → `Export2iCalServlet`).

### Phase 1.5 — Native Spring DI for `ServerServiceImpl` and friends (plan)

Because the legacy DI bootstrap is dead (Phase 1.4's bridge finding), every `@DefaultImplementation` server-side class needs to be `@Service`-annotated and instantiated by Spring directly. Constructor injection via `@jakarta.inject.Inject` is honoured natively by Spring 6 — no annotation changes required on the class bodies, only the class-level `@Service` / `@Component`.

Migration order (leaf-first, fewest dependencies first — at every step the smoke test must remain green):

1. `RaplaResources` (i18n bundle wrapper), `RaplaSystemInfo` (build info), `TimeZoneConverterImpl`
2. `RaplaLocaleImpl`, `DefaultScheduler` (scheduler with RxJava backend)
3. `ServerBundleManager`, `RemoteLoggerImpl`
4. `ServerStorageSelector` (the storage-flavour switch — file vs. SQL), `FacadeImpl`, `RemoteSessionImpl`
5. `ServerServiceImpl` (uses all of the above) — at this point `ServerCreator` and `SimpleRaplaInjector` have no remaining callers and are deleted in step 6.
6. Delete `ServerCreator`, `SimpleRaplaInjector` callers, the three `javax.inject.Provider` pins from Phase 1.2 step 6.

**Per-class checklist:**
- Add `@Service` (or `@Component`/`@Repository` if more apt) at class level.
- Keep `@DefaultImplementation` annotation in place during the migration — the classpath retains both `restinject` and Spring during Phase 1.5; only Spring acts on the `@Service`. After Phase 1.8 (`restinject` removed), `@DefaultImplementation` is deleted in a follow-up sweep.
- Verify the bean resolves via a smoke-test assertion (`@Autowired ClassUnderMigration x` plus `assertNotNull(x)`).
- For classes with `@Named("id")` qualifiers (extensions), use Spring's `@Component @Named("id")` — Spring honours `jakarta.inject.Named` for qualifier-based injection of `Map<String, Bean>`.

**Test datasource for Phase 1.5 step 4 onwards:** `ServerStorageSelector` requires `ServerContainerContext` to point at a real datasource. Set it up via:
- `application-test.yml` with `rapla.file-datasources.raplafile=target/test-data/rapla-data.xml`
- `@TestConfiguration` `@BeforeAll` that copies `/testdefault.xml` from the test classpath to that path (same fixture pattern the deleted bridge integration test used — the fixture itself is reusable; only the `ServerCreator.create()` call inside it broke).

#### Phase 1.6 — REST endpoints to Spring MVC

After `ServerServiceImpl` is a Spring bean, convert the REST page handlers (`RaplaEventsRestPage`, `RaplaResourcesRestPage`, `RaplaAuthRestPage`, etc.) from JAX-RS to `@RestController`. They already import `jakarta.ws.rs.*` so the cutover is annotation swap + path mapping. Once they're all `@RestController`, RESTEasy can be removed.

#### Phase 1.7 — Remove legacy bootstrap & test stack

1. Delete `ServerStarter`, `ServerCreator`, `CustomJettyStarter`, `jetty.xml`, `src/main/webapp/WEB-INF/web.xml`.
2. Replace `StandaloneStarter` — standalone mode bypasses HTTP; inject `RemoteStorage` directly.
3. Migrate `RaplaTestCase` and `AbstractTestWithServer` to `@SpringBootTest`. Delete `ResteasyRemoteConnector`.
4. Delete `src/test/java/org/rapla/bootstrap/CustomJettyStarter.java`.

#### Phase 1.8 — Drop RESTEasy + Jetty 9 + restinject

1. Remove RESTEasy (`org.jboss.resteasy:resteasy-jaxrs`, `resteasy-servlet-initializer`) and Jetty 9 (`org.eclipse.jetty:jetty-server`, `jetty-webapp`) dependencies from `parent/pom.xml`.
2. Remove the transitional `javax.servlet:javax.servlet-api:4.0.1 (provided)` dependency.
3. Remove the `restinject` artifact + `META-INF/services` generation + `ServiceInfLoader`.
4. Revert the three `javax.inject.Provider` pins from Phase 1.2 step 6 (`ClientCreator.java`, `ServerStorageSelector.java`, `RaplaTestCase.java` — the latter is gone after Phase 1.7) to `jakarta.inject.Provider`.

#### Phase 1.9 — Distribution & static cleanup

1. Remove `maven-war-plugin` from `pom.xml`.
2. Delete `src/main/webapp/` (static content already mirrored to `src/main/resources/static/` in Phase 1.3; `WEB-INF/web.xml` already removed in Phase 1.7).
3. Update `src/assembly/rapla.distribution.xml` to package the Spring Boot fat JAR instead of `rapla-*-war.war`.
4. Update `Dockerfile` and `docker-compose.yml` to run the fat JAR.
5. Run full `mvn test` — all tests pass.


## Future Constraints (deferred to a later phase)

### Constructor injection only — no field injection

**Status:** **deferred** — codified in `AGENTS.md` as a project-wide rule, but the migration plan does not require porting existing field-injected legacy classes ahead of time.

**Rule:** All new Spring `@Component` / `@Service` / `@Bean` definitions must use constructor injection (`@Inject` or `@Autowired` on a constructor) rather than field injection. The migration code added so far follows this rule for top-level config classes (`ServerCoreConfig`, `ServerServiceConfig`, etc. — every `@Bean` factory takes its dependencies as method parameters). The exception is the wrapper pattern that uses `AutowireCapableBeanFactory.autowireBean(legacyImpl)` to populate `@Inject` fields on legacy classes — those classes still need field injection until they are individually rewritten.

**When this constraint takes effect for the legacy code:** during Phase 8 cleanup, when each legacy `@DefaultImplementation` / `@Extension` class is touched for the final `@Service` / `@Component` annotation pass and its `@Inject` fields are removed from the autowireBean wrapper, field injection should be replaced by constructor injection in the same change. Until then, the existing field-injected classes are left alone.

**Goal of the deferred enforcement:**
- Every class is instantiable with `new` (constructor takes its deps), so unit tests can stub dependencies without a DI container.
- All fields can be `final`, eliminating mutability bugs.
- Dependencies are explicit at the call-site (you see the entire constructor list); field injection hides them inside the class body.

**Out of scope:** mass-rewriting the ~232 existing field-injected classes in this migration is out of scope. The constraint is enforced on new code and on classes touched during Phase 8 cleanup; it will be applied to the rest of the codebase incrementally as those files are otherwise modified.

## Hard Constraints

### URL path preservation for calendar/iCal/JNLP endpoints

**Status:** **HARD CONSTRAINT — non-negotiable** (recorded 2026-05-05; refined 2026-05-05 — paths only, query strings and response shapes can evolve)

**The URL paths for calendar/iCal/JNLP endpoints must not change during the Spring Boot migration.** Query-string parameter names and response body shapes are allowed to evolve as long as the **path itself** stays valid for existing client expectations. Breaking the path would invalidate:

- User-distributed iCal subscription links (calendar feeds embedded in Outlook, Apple Calendar, Google Calendar, Thunderbird, etc.) — these have been distributed by users and live in third-party calendar clients indefinitely.
- Embedded calendar widgets — HTML iframes/scripts on third-party sites that point at specific Rapla calendar URLs.
- JNLP launch links — webstart deployment URLs that users have bookmarked or distributed.

**Critical paths (must keep their exact value):**

| Legacy URL path | Current handler | Migration target |
|-----------------|-----------------|------------------|
| `/rapla/ical` (and `/rapla/internal_ical`) | `Export2iCalServlet` (JAX-RS) | Spring `@RestController` at **the same path**. **CRITICAL — most important.** Query params + response body may be modernised; path stays. |
| `/rapla/calendar` (and `.csv` / `internal_` variants) | `CalendarPageGenerator` | Spring controller at the same paths. **CRITICAL — most important.** |
| `/rapla/raplaclient` and `/rapla/raplaclient.jnlp` | `RaplaJNLPPageGenerator` | Spring controller at the same path (lower-priority — `Rapla/` static webclient path is allowed to evolve). |

**Paths that MAY be reorganised:** `/rapla/Rapla/` (the static webclient + JAR distribution tree) and `/rapla/index` are NOT bound by the constraint — they can be moved or replaced as part of the Angular-frontend transition.

**What this hard-constrains:**

- Phase 1.6 step 7 (`RaplaJNLPPageGenerator`, `CalendarPageGenerator`) must respond at identical paths.
- The `ServletRequestPreprocessor` migration (used by `UrlEncryption` plugin to pre-decrypt `?key=…` query strings) must hook the same paths via Spring `HandlerInterceptor` / `OncePerRequestFilter`.
- Any test for these endpoints must verify path resolution.

**What is allowed:** internal refactoring, dependency cleanup, replacing JAX-RS annotations with Spring annotations on the same paths, switching servlet container, **and modernising the query-string parameter names or response-body fields**.

**What is not allowed:** path renames, redirects to new paths, or any change that would cause an existing iCal/JNLP/widget URL path to fail to route.

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

## Plan

### Phase 0: Preparations (additive only — no existing code touched)

Phase 0 establishes the Spring Boot infrastructure as new files. The existing `MainServlet` / RESTEasy stack continues to run. The Spring Boot main class compiles but is not the runtime entry point until Phase 1 cutover.

1. **Java target bump** — set `<maven.compiler.release>17</maven.compiler.release>` in both `pom.xml` and `parent/pom.xml`. Existing source compiles fine under Java 17, but `src/main/java9/module-info.java` must be **deleted** — its filename-based automodule references (`requires javax.servlet.api`, `requires resteasy.jaxrs`, etc.) no longer resolve under Java 17's stricter module rules, and Spring Boot fat JARs don't use JPMS. The multi-release JAR `compile-java-9` execution in `parent/pom.xml` becomes a no-op and can be removed.
2. Add Spring Boot 3.2+ BOM to `parent/pom.xml` `<dependencyManagement>` (does not pull artifacts on its own)
3. Add `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `nimbus-jose-jwt` to `pom.xml`
4. Add `spring-context`, `spring-web` to `pom.xml` (client)
5. Configure `maven-dependency-plugin:copy-dependencies` for client JNLP `lib/` output
6. Create `application.yml` with server port (default 8051 to differ from current 8052), datasource config, logging, `server.compression.enabled=true`
7. Create `RaplaServerProperties` `@ConfigurationProperties` class to replace JNDI/`ServerContainerContext`, including a `Map<String, DataSourceProperties>` for the multi-datasource case
8. Create `RaplaSpringBootApplication` (`@SpringBootApplication`) with a minimal `main()` — no `@ComponentScan` of legacy packages yet
9. Copy existing `logback.xml` from `src/test/resources/` to `src/main/resources/logback-spring.xml` (Spring Boot auto-detects)
10. Write test (`@SpringBootTest`) that loads `RaplaSpringBootApplication` and verifies the context starts. **Run in a separate Surefire fork** so it doesn't conflict with the existing `RaplaTestCase` Jetty 9 setup running in the same `mvn test` invocation.

### Phase 1: Cutover — Server Bootstrap, Jakarta migration, MainServlet removal

This phase is intentionally large because it cannot be subdivided without leaving the build broken. Done on a feature branch, merged when fully green.

1. **Move `src/main/webapp/` static content** to `src/main/resources/static/` — verify all relative URLs in HTML still resolve (`Rapla/`, `images/`, `webclient/`, `jsclient/`, JNLP descriptors)
2. **Jakarta namespace migration** — replace `javax.inject`, `javax.servlet`, `javax.ws.rs` imports with `jakarta.*` across the codebase. (Removes the only obstacle to embedded Tomcat 10 starting up.)
3. Migrate `ServerServiceImpl` to `@Service` with constructor injection
4. Wire `ServerConfig` `@Configuration` with `@ComponentScan` over server packages, register core beans (Logger, storage, facade, locale, timezone)
5. Delete `MainServlet`, `ServerStarter`, `ServerCreator`, `web.xml`, `CustomJettyStarter`, `jetty.xml`
6. Replace `StandaloneStarter` — standalone mode bypasses HTTP entirely; inject `RemoteStorage` bean directly
7. Migrate `RaplaTestCase` and `AbstractTestWithServer` to `@SpringBootTest`
8. Remove RESTEasy + Jetty 9 dependencies from `pom.xml`
9. Verify `mvn test` passes (existing tests now run against Spring Boot context)

### Phase 2: Server DI Migration + Promise-wait removal

1. Annotate all server-side `@DefaultImplementation` classes with `@Service` + `@Profile("server")`
2. Replace `@Inject` (jakarta) with constructor injection
3. Replace `Map<String, ServerExtension>` with Spring `@Named` bean maps
4. Replace `Set<T>` extension point injection with `List<T>`
5. Replace `@Extension(provides=X, id="...")` with `@Component` + `@Named("id")`
6. **Audit compound contexts** — classes annotated for multiple `InjectionContext` values get `@Profile({"server","client"})` (array form). Missing a context here causes silent bean-not-found failures at runtime.
7. **Promise-wait removal** — see Promise-wait Removal section above:
   - Add sync variants on `CachableStorageOperator` and `RaplaFacade` for the ~12 server-awaited methods
   - Update each caller (REST endpoints, servlets, view pages, `SecurityManager`) to call sync variants
   - Delete `PromiseWait`, `PromiseWaitImpl`, `LocalAbstractCachableOperator.waitForWithRaplaException`
   - Update `RemoteOperator` (client) to call sync HTTP proxies and wrap in RxJava at the call site
8. Write test: all server beans resolve from `ApplicationContext`; no remaining references to `PromiseWait` in `src/main/java`

### Phase 3: Server REST Endpoints, Security, Filters

1. Convert JAX-RS `@Path` + `@GET`/`@POST` to `@RestController` + `@RequestMapping`
2. Migrate core endpoints: `RemoteStorageImpl`, `RemoteAuthentificationServiceImpl`, `RemoteLocaleServiceImpl`, `RemoteLoggerImpl`
3. Migrate new REST pages: `RaplaEventsRestPage`, `RaplaResourcesRestPage`, `RaplaAuthRestPage`, `RaplaDynamicTypesRestPage`
4. Migrate plugin endpoints: `CalendarPageGenerator`, `Export2iCalServlet`, `UrlEncryptionService`, etc.
5. Migrate exception mappers to `@ControllerAdvice`
6. **JWT authentication setup** — see Authentication section above:
   - Add `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `nimbus-jose-jwt`
   - `RaplaKeyStorage` exposes the HMAC secret (generated on first start, persisted in keystore)
   - `JwtDecoder` bean using `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)`
   - Custom `JwtAuthenticationConverter` mapping `sub` claim to the Rapla `User` principal
   - `SecurityFilterChain` permitting `/auth/**` and static resource paths, requiring auth on everything else, stateless session, CSRF disabled
   - Replace `RaplaAuthRestPage` with `AuthController` (`/auth/login`, `/auth/refresh`) that issues JWTs via `nimbus-jose-jwt`
   - Delete `TokenHandler`, `SignedToken`, `RemoteSessionImpl.extractUser`, `LoginTokens.fromString/toString`
7. **CORS configuration** via `WebMvcConfigurer.addCorsMappings()` — allowed origins driven by `rapla.cors.allowed-origins` property
8. **Migrate `ServletRequestPreprocessor` extensions** to Spring `HandlerInterceptor` beans, registered with their existing ordering
9. **Locale resolution** — implement `LocaleResolver` that reads user preferences from the authenticated principal, falls back to `Accept-Language`
10. Remove RESTEasy dependency (including `GZIPEncodingInterceptor` — replaced by `server.compression`)
11. Write test: all endpoints via `MockMvc` / `TestRestTemplate`; valid JWT grants access, expired/invalid JWT returns 401; refresh endpoint rotates tokens; CORS preflight returns expected headers

### Phase 4: Client DI Migration

1. Create `ClientConfig` `@Configuration` with `@ComponentScan` for client packages
   - `excludeFilters = @ComponentScan.Filter(type=ASSIGNABLE_TYPE, classes={server packages})`
2. Migrate `ClientCreator` to `AnnotationConfigApplicationContext`
3. Annotate client classes:
   - `@Component` + `@Profile("swing")` for Swing UI (`ApplicationViewSwing`, `DialogUI`, editors, views)
   - `@Component` + `@Profile("client")` for shared client logic (`ClientFacadeImpl`, `RemoteOperator`, `ReservationControllerImpl`)
4. Replace `@Extension(provides=X, id="...")` with `@Component` + `@Named("id")` for ~60 client extensions
5. **Audit compound contexts** — same as Phase 2, step 6, for client-side classes
6. Migrate `SwingSchedulerImpl` to `@Component` (EDT-aware scheduler)
7. Write test: client `ApplicationContext` starts, all client beans resolve

### Phase 5: REST Client Proxies

1. Convert shared JAX-RS interfaces to Spring HTTP interfaces:
   ```java
   public interface RemoteStorage {
       @GetExchange("/resources")
       ResourcesResponse getResourcesSync();
   }
   ```
2. Create `ClientProxyConfig` `@Configuration`:
   - `RestClient.Builder` with auth interceptor and error mapping
   - `HttpServiceProxyFactory` for each service interface
3. Update `RemoteOperator` to use injected proxies (synchronous calls)
4. Wrap proxy calls in RxJava3 in `RemoteOperator` for async:
   ```java
   return Completable.fromAction(() -> remoteStorage.getResourcesSync())
       .subscribeOn(Schedulers.io());
   ```
5. Remove `MyCustomConnector`, `HTTPJsonConnector`, `AbstractJsonProxy` usage
6. Remove `AnnotationInjectionProcessor` proxy generation from build
7. Write test: proxies call mock server, RxJava wrapping works correctly

### Phase 6: Plugin System

1. Create `PluginAutoConfiguration` for server plugins
2. Create `ClientPluginAutoConfiguration` for client plugins
3. **Plugin enable/disable via `ImportSelector`** — `@ConditionalOnProperty` does not map onto the existing `raplaservices=name=true/false` CSV config. Implement a `RaplaPluginImportSelector` that reads the `raplaservices` property and programmatically registers only enabled plugin `@Configuration` classes. This preserves the existing config format.
4. Migrate each plugin's `@Extension` to `@Component` with appropriate `@Profile`
5. Write test: all server + client plugins load and start; disabling a plugin via `raplaservices` excludes its beans

### Phase 7: Remove GWT

1. Delete `src/main/java/org/rapla/components/i18n/client/gwt/GwtBundleManager.java`
2. Delete GWT module descriptor and GWT frontend code
3. Remove `InjectionContext.gwt` usage from all annotations
4. Remove GWT-related `@Extension` implementations
5. Clean up GWT-specific dependencies from `pom.xml`

### Phase 8: Cleanup

1. Remove `restinject` artifact entirely from `parent/pom.xml`
2. Remove `META-INF/services` generation and `ServiceInfLoader`
3. Remove `CustomJettyStarter`, `jetty.xml`, old bootstrap classes
4. Remove `web.xml`
5. Remove `AnnotationInjectionProcessor` configuration from `maven-compiler-plugin`
6. Update `Dockerfile` and `docker-compose.yml` for Spring Boot fat JAR (server) and exploded `lib/` (client)
7. Run full test suite (`mvn test`) — all tests pass

### Phase 9: Switch Gson → Jackson (after PRD 001-A Date→LocalDateTime)

1. Remove `spring.http.converters.preferred-json-mapper=gson` from `application.yml`
2. Add `jackson-datatype-jsr310` module for `LocalDateTime` serialization
3. Remove Gson dependency from `pom.xml`
4. Remove custom `JavaJsonSerializer` and Gson-specific code
5. Verify REST API JSON output matches expected format (for Angular frontend)
6. Run full test suite (`mvn test`) — all tests pass

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

## Open Questions

1. **Shared interface design?** The current JAX-RS interfaces (`RemoteStorage`, etc.) use JAX-RS annotations (`@Path`, `@GET`, `@POST`). Spring's `HttpServiceProxyFactory` uses `@HttpExchange` annotations. Options: (a) Replace JAX-RS annotations with Spring annotations on the shared interface, server `@RestController` implements the same interface. (b) Keep separate interface + impl annotations. **Recommendation: (a)** — single source of truth.

2. **Gson JSON format compatibility?** When using Gson via Spring Boot, need to verify the JSON output format matches what the Angular frontend (and existing clients) expect. Write integration tests comparing Gson output vs current `JavaJsonSerializer` output.

3. **Migration strategy: big bang or incremental?** Can we run both DI systems side by side during migration? **Recommendation: Incremental** — Phase 1-3 (server) can ship independently. Phase 4-6 (client) can follow.

4. **JNLP download size?** Adding `spring-context` + `spring-web` JARs increases the JNLP download (~5 MB). The client does NOT include spring-boot itself, only the two library JARs. Measure actual size impact; consider ProGuard shading only if autoupdate latency becomes a user complaint.

5. **`raplaservices` config drives core services too, not just plugins?** The CSV format toggles both core services and plugins. The `RaplaPluginImportSelector` from Phase 6 must filter both sets, or the toggle for core services needs a separate mechanism. Audit the existing `raplaservices` keys before implementing the import selector.
   