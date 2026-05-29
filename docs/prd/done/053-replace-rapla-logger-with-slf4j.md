# PRD 053 — Replace the custom rapla `Logger` with SLF4J

**Status:** done (shipped 2026-05-24)
**Date:** 2026-05-23 → 2026-05-24
**Related:** PRD 011 (Spring Boot 4 / Jackson 3), PRD 027 (mock-framework policy)

## Result (2026-05-24)

All four phases shipped. The `org.rapla.logger` package is deleted; every class in the reactor logs via `private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(<Class>.class);` (or one of 5 named subsystem loggers `rapla.webservice` / `rapla.exchangeupdate` / `rapla.ldap` / `rapla.ical` / `rapla.404`, plus the dynamic `rapla.<id>` from `RemoteLoggerController`). JUL is bridged to SLF4J at both entry points (`RaplaSpringBootApplication.main`, `SpringRaplaClient.main`).

**Reactor:** `mvn clean compile` green; `mvn test` green except 3 pre-existing rapla-app failures unrelated to logger (`ApiPrefixArchitectureTest.everyRestControllerBelongsToExactlyOneSpecGroup`, `OAuthConfigControllerTest.discoveryReportsSwingLegacyLoginDisabledByDefault`, `UrlPreservationTest.appShellRoutes`).

**Aggregate diff:**
- **310 files modified**, **10 files deleted** (full `org.rapla.logger` package + 1 obsolete test), 1 pom dependency added (`jul-to-slf4j` in `rapla-client/pom.xml`).
- **+1,946 / −3,144 lines = net −1,198 LOC.**

**Before → after (reactor-wide):**

| Pattern | Before | After |
|---|---:|---:|
| `org.rapla.logger.*` imports | ~289 | 0 |
| `java.util.logging.*` imports | 5 (incl. `DialogUI` JUL leak) | 0 |
| `Logger logger` constructor params | ~313 | 1 (commented body) |
| `Logger logger` instance fields | 131 | 1 (test fixture) |
| `public/protected Logger getLogger()` accessors | 11 | 1 (intentional `AbstractTableStorage` bridge) |
| `getChildLogger(...)` indirections | 23 | 4 (all in commented-out code) |
| `if (logger != null)` defensive guards | 23 | 0 |
| Static `final Logger LOGGER` (SLF4J idiom) | 0 | 122 |
| `System.out`/`System.err` in main code (live) | 9 | 0 (all converted to LOGGER) |
| Custom logger package files | 9 | 0 |

**Phase 4 — JUL bridge:**
- `RaplaSpringBootApplication.main` + `SpringRaplaClient.main` both call `SLF4JBridgeHandler.removeHandlersForRootLogger()` + `SLF4JBridgeHandler.install()` at startup.
- `rapla-client/pom.xml` got an explicit `jul-to-slf4j` dependency (compile scope, version `${slf4j.version}`).
- 4 `logback*.xml` configs swept: dead `rapla.*` category rules removed (`rapla.call`, `rapla.standalone.plugin`, `rapla.standalonefacade`, `rapla.remotefacade`, `rapla.serverfacade`, `rapla.facade.trigger.allocation`, `rapla.raplafile`, `rapla.remotestub`, `rapla.remotestore`, `rapla.rapladb`, `rapla.remote`, `rapla.server.csvaccesslog`); 5 surviving named loggers documented inline as commented templates. `LevelChangePropagator` kept (needed for JUL→Logback level sync).
- `DialogUI.java`'s `java.util.logging.Logger.getLogger("rapla.dialogui.anchor")` JUL leak migrated to SLF4J by the rapla-client Stage A agent.

**Phase 3 — opportunistic System.out/err sweep:**
- 8 `System.err`/`System.out` calls in main code converted to `LOGGER`: `Assert`, `HTMLView` (×2), `RaplaMapImpl` (×3), `SupplierAutoWrapperBeanFactoryPostProcessor`, `AppointmentImpl.print()`.
- 1 debug `System.out.println` deleted: `MailapiClient.java:218`.
- Kept: `SignWebclientJars` (interactive build tool — `System.out` is the user-facing YubiKey countdown contract), `JNDIAuthenticationStore` `main()` (manual test harness), `WeekdayMapper` Javadoc example, `DialogUI` pending diagnostic work (per AGENTS.md §7).

**Not done (opportunistic cleanup, separate PRs):**
- 319 `logger.info("foo " + bar)` string-concat sites → `LOGGER.info("foo {}", bar)` placeholder migration (no behaviour change; readability + perf for disabled levels)
- 65 `if (logger.isDebugEnabled())` guards mostly redundant once `{}` placeholders are in play — drop guards where the argument isn't itself expensive to compute
- 8 `printStackTrace()` calls in main code → `LOGGER.error("context", ex)` (`IOUtil` ×3, `RaplaXMLReader`, `JNDIAuthenticationStore`, `DialogUI`, `UserOption`, `CalendarPrintDialog`)

**Memory entries:**
- [[feedback_no_scripts_for_logger_migration]] — recorded the no-scripts/manual-Edits/20-class-cadence/no-new-tests preference for refactors of this shape (overrides AGENTS.md §6a for this case)


## Goal

Delete the `org.rapla.logger` package and use SLF4J (`org.slf4j.Logger` + `LoggerFactory`) directly across the reactor. Logback stays as the backend (already declared in `rapla-bom`, configured by 6 `logback*.xml` files). The two `@Bean Logger raplaLogger()` factories disappear, along with the ~313 constructor-injected `Logger` parameters and ~474 `getLogger()` accessor calls.

## Why

The custom `Logger` interface is a thin wrapper that pre-dates the project's commit to SLF4J/Logback. Today:

- `RaplaBootstrapLogger` reflectively probes SLF4J → Log4j → JUL; only SLF4J ever wins in production. The Log4j branch references a class that doesn't exist in the source tree (`Log4jAdapter`). The JUL/GWT branches (`JavaUtilLoggingAdapter`, `AbstractJavaUtilLogger`, `JavaUtilLoggerForGwt`, `NullLogger`, `ConsoleLogger`) are dead — GWT is gone, JUL is never selected. Six classes / ~250 LOC sit on the classpath unused.
- The `Logger` interface declares methods that return `Void` (boxed), an artefact of the GWT-RPC era. SLF4J returns `void`.
- Every class that wants to log carries a `Logger logger` constructor parameter — ~313 sites. New contributors expect `private static final Logger LOGGER = LoggerFactory.getLogger(MyClass.class);` and are surprised by the manual DI.
- 23 `getChildLogger("subname")` sites hand-curate sub-categories that SLF4J's class-name-based logger hierarchy gives for free.
- Per-class loggers let Logback's category configuration (`<logger name="org.rapla.plugin.exchangeconnector.server.SynchronisationManager">`) target individual classes without code changes — currently impossible unless someone added a `getChildLogger` for that category.

## Scope

In scope:

- `org.rapla.logger.*` — full package removal.
- `RaplaBootstrapLogger.createRaplaLogger()` — removed; replaced by per-class `LoggerFactory.getLogger(...)`.
- `@Bean Logger raplaLogger()` in `ServerCoreConfig` and `ClientConfig` — removed.
- `Logger` constructor params + fields across the reactor (~313 sites) — replaced with per-class static `LOGGER` fields.
- `getLogger()` accessors on `RaplaComponent` and ~10 other classes — removed; callers use their own static logger.
- `getChildLogger("name")` (23 sites) — replaced with named static fields (`LoggerFactory.getLogger("rapla.ldap")`) where the category name is meaningful, or dropped where the class-name logger suffices.
- `RemoteLoggerController` keeps its HTTP contract (`PUT /api/logger/{id}`); internally just calls `LoggerFactory.getLogger("rapla." + id).info(...)`.

Out of scope (separate cleanup, listed under "Opportunistic cleanup" below):

- Standardising the 6 `logback*.xml` files (consolidate appenders, rename legacy `rapla.*` categories to package-qualified `org.rapla.*`).
- The dhbwrapla custom deployment's own logger usages (coordinate separately).
- Switching backend away from Logback.

## Plan

### Phase 1 — Dead code removal (zero call-site changes) ✅ done 2026-05-24

Pure deletion, no behaviour change. Dropped −256 LOC.

- [x] `RaplaBootstrapLogger` collapsed: Log4j and JUL branches gone, body is now `return new Slf4jAdapter().get();`.
- [x] Deleted `internal/JavaUtilLoggingAdapter`, `internal/AbstractJavaUtilLogger`, `internal/JavaUtilLoggerForGwt`.
- [ ] `NullLogger`, `ConsoleLogger`, `AbstractLogger` — kept; have live callers (1 prod + 5 test sites for NullLogger/ConsoleLogger; 1 test extends AbstractLogger). Will go in Phase 2 when their callers are migrated.
- [x] `mvn clean compile` + `mvn -pl rapla-core,rapla-server -am test` — green (135 + 512 tests).

### Phase 2 — Per-class static loggers (two-stage, parallel)

**Strategy shift (2026-05-24):** original "drop everything per class" hit cross-module cascade — every class drop forced its `@Bean` factories and inheritance chain to update simultaneously. Reworked as two stages:

**Stage A (parallel, low-risk):** each class gets a static `org.slf4j.Logger LOGGER` and switches its internal calls. Constructor `Logger logger` params and instance fields **stay** — signatures unchanged means no cross-module breakage. Three subagents work in parallel, one per module (rapla-core, rapla-client, rapla-server), each strictly bounded to its module's source tree.

**Stage B (single sweep, coordinated):** once all classes log via static `LOGGER`, drop the now-unused constructor params + fields, drop Logger args from `@Bean` factories, migrate the deferred base classes (`RaplaComponent`, `AbstractCachableOperator`, `FacadeImpl`, `ClientFacadeImpl`, `CalendarModelImpl`, `RemoteOperator`, `MyCustomConnector`, `CustomConnector` interface, `StartupEnvironment` interface), replace `getChildLogger("xxx")` with named static loggers, delete `@Bean Logger raplaLogger()` factories, delete the `org.rapla.logger` package.

**Execution rules** (per user direction, supersedes AGENTS.md §6a's script-first guidance for this PRD — see [[feedback_no_scripts_for_logger_migration]]):

- **Manual per-class edits only.** No Python/sed/jdt scripts. Each file is read and Edit-ed individually so the agent sees the local context.
- **Maximize parallelism** by issuing many independent Read/Edit tool calls within a single turn (one turn per batch).
- **Batch size: 20 classes.** After every batch: `mvn -f pom.xml clean compile` then `mvn -f pom.xml test` (full reactor). Fix any breakage before starting the next batch.
- **No new tests written** for the migration itself — the existing suite + compile is the signal. (`mvn test` regressions are the catch-all.)
- **Don't delete the `org.rapla.logger` package** until every importer is gone; the compile is what proves that.

**Rule — only add `LOGGER` to classes that actually log.** If a class takes a `Logger` constructor param only to forward it to `super(logger, ...)` and never calls any of `logger.info / warn / error / debug / trace` on it directly, do NOT add a static `LOGGER` to that class. The inherited logger field is for the parent class's use; the subclass doesn't need its own. Forwarders stay untouched in Stage A and lose the constructor param in Stage B (when the parent's signature drops it too).

For each class in a batch:

1. Add `private static final Logger LOGGER = LoggerFactory.getLogger(<ClassName>.class);` **only if the class body contains at least one direct log call** (`logger.info(...)`, `getLogger().warn(...)`, etc.).
2. Drop the constructor `Logger logger` parameter and the instance field.
3. Update every call site of that constructor (often in `@Bean` factory methods in `ClientConfig` / `ServerCoreConfig` / `SwingClientConfig`, or `new XImpl(...)` in `ServerServiceConfig` per AGENTS.md §4 server wiring pattern) — drop the `logger` argument.
4. Replace `this.logger` / `logger` references with `LOGGER`.
5. Replace `getLogger()` calls with `LOGGER` in the same class.
6. For `getChildLogger("name")` call sites, add a named static field: `private static final Logger LDAP_LOGGER = LoggerFactory.getLogger("rapla.ldap");`
7. Swap imports: `org.rapla.logger.Logger` → `org.slf4j.Logger`; add `org.slf4j.LoggerFactory`.

**Suggested batch ordering** (do leaves first — classes with no rapla consumers of their constructor — to avoid `@Bean`/`new` call-site fan-out across batches):

1. Pure-util / entity classes in `rapla-core` that never had Spring wiring (LockOrderingAudit, RaplaErrorHandler, JnlpTokenStore, etc.).
2. `rapla-client` Swing internal classes (most have `@Service` / `@Component` since SwingClientConfig component-scans them).
3. `rapla-server` explicit `@Bean` factory consumers — coordinate with updates to the `@Bean` factories in the same batch.
4. `rapla-app` test fixtures + `FacadeTestSupport`, `DbOperator*Test`, `ReloadServiceTest`, `ConcurrentTests`, `LockOrderingAuditTest`, `DefaultRaplaLockAuditTest`, `WeekdayMapperTest`, `DialogUIPositionTest`, `ErrorDialogTest`, `PermissionEditTest`.
5. Last batch — the `@Bean Logger raplaLogger()` factories in `ServerCoreConfig` and `ClientConfig`, `RemoteLoggerController`, `RaplaComponent#getLogger()`, then delete the `org.rapla.logger` package.

**Child logger decision (locked 2026-05-24).** 23 live `getChildLogger` call sites resolve as follows:

**Keep as named subsystem logger** (6 distinct names, 14 sites):

| Logger name | Sites | Rationale |
|---|---|---|
| `rapla.webservice` | `SynchronisationManager:839`, `AppointmentSynchronizer:132` | EWS wire log — ops silence wire chatter independently of higher-level sync errors |
| `rapla.exchangeupdate` | `AppointmentSynchronizer:300, 335` | Exchange sync-event audit — distinct from `webservice` (wire) |
| `rapla.ldap` | `JNDIAuthenticationStore:240, 280` | LDAP subsystem — operator-facing term, tuned independently |
| `rapla.ical` | `Export2iCalController:81` | iCal export subsystem |
| `rapla.404` | `CalendarPageController:156, 392`, `Export2iCalController:148, 160, 171, 224` | HTTP missing-resource warnings — bot-scanner noise in prod, route to separate appender. **Unify** `html.404` and `404` to the single name `rapla.404` |
| `rapla.<id>` (dynamic) | `RemoteLoggerController:27` | REST endpoint that forwards client-supplied category ids — purpose is dynamic naming. Becomes `LoggerFactory.getLogger("rapla." + id)` |

**Drop — collapse to the calling class's class-named `LOGGER`** (9 sites):

| Category | Site | Why drop |
|---|---|---|
| `notification` | `NotificationService:73` | Redundant with class name |
| `mail` | `MailToUserImpl:48` | Redundant with class name |
| `connector` | `MyCustomConnector:39` | Redundant with class name |
| `calendarmodel` | `CalendarModelImpl:142` | Redundant with class name |
| `remote` | `RemoteOperator:110` | Class IS the remote operator |
| `importexport` | `ImportExportManagerImpl:40` | Redundant with class name |
| `appointmentcheck` | `LocalAbstractCachableOperator:2015` | Message text already says what it is |
| `trigger.allocation` | `ClientFacadeImpl:302` | Inside a commented-out method block (dead code). Drop the logback rule `<logger name="rapla.facade.trigger.allocation">` too |
| `login` | `RaplaAuthentificationService:84` | Marginal — no logback rule references it; promote to named only if audit-routing becomes a real ask |

**Logback config sweep** (same Stage B): each of the 4-6 `logback*.xml` files gets pruned. Keep rules for the 6 surviving names; drop dead rules (`rapla.facade.trigger.allocation`, `rapla.standalonefacade`, `rapla.remotefacade`, `rapla.remotestub`, `rapla.remotestore`, `rapla.rapladb`, `rapla.serverfacade`, `rapla.call`, `rapla.standalone.plugin`, `rapla.raplafile` — audit each). Optionally add package-path rules (`<logger name="org.rapla.plugin.exchangeconnector.server">`) for finer per-class control.

**Progress log:**

- **Stage A continued — main-agent batch (2026-05-24):**
  - **Child logger drop list applied:** `NotificationService` full Stage A migration + dropped `notification` child; `MailToUserImpl` dropped `mail` child; `MyCustomConnector` dropped `connector` child; `CalendarModelImpl` dropped `calendarmodel` child; `RemoteOperator` dropped `remote` child; `ImportExportManagerImpl` dropped `importexport` child + removed Logger constructor param + getLogger() accessor; `LocalAbstractCachableOperator:2015` dropped `appointmentcheck` child; `RaplaAuthentificationService:84` dropped `login` child; `AppointmentSynchronizer` removed dead `ewsLogger` declaration.
  - **Child logger keep list applied (named statics):** `AppointmentSynchronizer` got `EXCHANGE_UPDATE_LOG` static (replaced 2 local-var `exchangeupdate` getChildLogger sites); `Export2iCalController` got `ICAL_LOG` + `NOT_FOUND_LOG` statics (replaced 4 `404` + the `ical` field); `CalendarPageController` got `NOT_FOUND_LOG` static (replaced 2 `html.404` sites, dropped Logger constructor param); `JNDIAuthenticationStore` LOGGER renamed from class-named to `LoggerFactory.getLogger("rapla.ldap")`; `RemoteLoggerController` fully migrated to dynamic `LoggerFactory.getLogger("rapla." + id)`, Logger constructor param dropped.
  - **@Bean factory updates:** `ServerServiceConfig.notificationService` dropped Logger param; `ServerStorageSelector:78` updated; `DbOperatorBootTest:107` + `DbOperatorRoundTripTest:107` updated.
  - Reactor core+server `mvn compile` green.

- **Stage A — parallel subagents (2026-05-24):** Three subagents dispatched in parallel, one per module. Each strictly bounded to its own module's source tree to prevent file-level conflicts.
  - **rapla-core**: 11 files migrated, 512/512 tests green
  - **rapla-server**: 43 files migrated, 135/135 tests green (1 new failure flagged in `ImpersonationControllerTest`, agent reverted that one call to use the param logger to preserve the test's `ListAppender` contract)
  - **rapla-client**: 88/176 files in first agent pass + follow-up agent dispatched for remaining ~80 files (~50% complete)
  - **Side effect:** rapla-client agent fixed `rapla-client/pom.xml` slf4j-api scope from `runtime` to compile (needed for `import org.slf4j.Logger` to resolve in source).
  - **Test-contract caveat surfaced:** tests that attach a `ListAppender` to a class's logger by category name (e.g. `SwingSafeTest` uses `CapturingLogger` via constructor param, `ImpersonationControllerTest` captures category `"rapla"`) break if their target class's internal `logger.x()` calls are switched to class-named `LOGGER.x()`. Mitigation: keep the offending call on the parameter `logger`, add a `// kept for test-contract` comment.

- **Pilot batch (2026-05-24, 5 main classes + 7 callers + 3 tests):** `RaplaErrorHandler`, `JnlpTokenStore`, `FileTokenStore`, `TokenStores`, `DefaultRaplaLock`. Callers updated: `RaplaInput` (×2), `FileOperator`, `ConfigTools`, `ClientConfig` (×2), `LocalAbstractCachableOperator` (×2). Tests updated: `FileTokenStoreTest` (×9), `LockOrderingAuditTest` (×3), `DefaultRaplaLockAuditTest` (×5). `mvn clean compile` green; `mvn test` green except for 3 pre-existing rapla-app failures (`ApiPrefixArchitectureTest.everyRestControllerBelongsToExactlyOneSpecGroup`, `OAuthConfigControllerTest.discoveryReportsSwingLegacyLoginDisabledByDefault`, `UrlPreservationTest.appShellRoutes` — all confirmed pre-existing via `git stash` + retest on bare HEAD). The "reading" subcategory used by `FileOperator` for SAX errors is now the class-named logger `RaplaErrorHandler` — no behavioural change (no `rapla.reading` rule exists in any logback config).

### Phase 3 — Opportunistic cleanup (separate commits, low risk)

Once SLF4J is the API, these patterns are worth fixing. Each is independent and can land in its own PR.

- **319 string-concat log calls → `{}` placeholders.** Today zero call-sites use the SLF4J `{}` parameter form. Sample:
  ```java
  // before
  logger.warn("Could not send mail: " + t.getMessage(), t);
  // after
  LOGGER.warn("Could not send mail: {}", t.getMessage(), t);
  ```
  Reduces string-building cost when the level is disabled, makes log-aggregator parameter extraction work.
- **65 `isDebugEnabled()` / `isTraceEnabled()` guards** become mostly redundant once `{}` placeholders are in play — only keep them when the argument computation itself is expensive (e.g. serialising a collection). Sample candidates to drop the guard: `EWSConnector:89`, `JNDIAuthenticationStore` (8 sites), `NotificationService:304`.
- **8 `printStackTrace()` calls in main code** — convert to `LOGGER.error("context", ex)`. Files: `JNDIAuthenticationStore.java:1147`, `IOUtil.java:57,130,136`, `RaplaXMLReader.java:367`, `DialogUI.java:303`, `UserOption.java:474`, `CalendarPrintDialog.java:146`.
- **87 `System.out`/`System.err` in main code** — most are commented-out debug trash; delete those. Live ones to convert per [[feedback_no_system_err_out]]: `MailapiClient.java:218`, `JNDIAuthenticationStore.java:741,1142,1144`, `RaplaMapImpl.java:148,684,700`.
- **23 `if (logger != null)` null-guards** — defensive nonsense; static loggers are never null. Drop. Sample: `JnlpTokenStore.java`, `RaplaErrorHandler.java`.

### Phase 4 — `java.util.logging` inventory and bridge

Full JUL footprint in the reactor (excluding `target/`):

| File | What it is | Action |
|---|---|---|
| `rapla-core/.../logger/RaplaBootstrapLogger.java:64` | String literal `"Logging via java.util.logging API."` in the JUL-fallback branch; no JUL import. | Removed in Phase 1 along with the JUL branch. |
| `rapla-core/.../logger/internal/AbstractJavaUtilLogger.java` | Dead JUL adapter base class. | Deleted in Phase 1. |
| `rapla-core/.../logger/internal/JavaUtilLoggingAdapter.java` | Dead JUL adapter used only by `RaplaBootstrapLogger`'s fallback chain. | Deleted in Phase 1. |
| `rapla-core/.../logger/internal/JavaUtilLoggerForGwt.java` | Dead GWT-era JUL adapter. | Deleted in Phase 1. |
| **`rapla-client/.../client/dialog/swing/DialogUI.java:336`** | **Live production JUL call** — `java.util.logging.Logger.getLogger("rapla.dialogui.anchor")` for dialog-anchor diagnostics. Bypasses the rapla logger entirely. | **Bug-class — see below.** |
| `tools/keycloak/ntlm-authenticator/NtlmAuthenticator.java:42` | `org.jboss.logging.Logger` (not JUL) — Keycloak SPI module, not part of the rapla reactor. | Out of scope. |

**The `DialogUI.java:336` problem.** Only live JUL call in rapla's main source. The configured logging stack:

- All 4 `logback*.xml` configs install `ch.qos.logback.classic.jul.LevelChangePropagator`, which **synchronises levels** between Logback and JUL — but does *not* redirect JUL output to Logback appenders.
- That redirect requires `org.slf4j.bridge.SLF4JBridgeHandler.install()`. Grep shows it's installed in **`rapla-app/src/test/etc/jetty.xml`** (test harness only) and **commented out** in `RaplaLoader.java:158`. Neither the dev server (`RaplaSpringBootApplication`) nor the Swing client installs it in production.
- The `jul-to-slf4j` jar is bundled into the fat JAR (`rapla-app/pom.xml:106`), so the bridge code is on the classpath — just never activated.

Consequence: the diagnostic log line in `DialogUI.start()` writes to JUL's default console handler in the Swing client, not to `logs/rapla-client.log`, and is invisible in any Logback appender. Effectively a hidden log channel.

**Actions:**
- [ ] Rewrite `DialogUI.java:336` to use the per-class `LOGGER` (its category `rapla.dialogui.anchor` becomes `LoggerFactory.getLogger("rapla.dialogui.anchor")` as a named static — keep the category, lose the JUL hop). Convert `_diag.info(...)` and `_diag.warning(...)` to `LOGGER.info` / `LOGGER.warn`. Replace string-concat with `{}` placeholders while there.
- [ ] **Decide on the JUL→SLF4J bridge:** install `SLF4JBridgeHandler` once at startup in `RaplaSpringBootApplication` (server) and in the Swing client bootstrap. Even with no rapla JUL callers, third-party libs occasionally log via JUL (JAX-RS impls, JDK HTTP client, JNDI), and we already pay the jar cost. Two lines per entry point:
      ```java
      SLF4JBridgeHandler.removeHandlersForRootLogger();
      SLF4JBridgeHandler.install();
      ```
- [ ] Once the bridge is installed everywhere, drop the `<contextListener class="...LevelChangePropagator">` blocks from the 4 `logback*.xml` files — only useful when JUL is *not* bridged. With the bridge installed, JUL has no handlers of its own and the propagator is a no-op.
- [ ] Remove the commented `RaplaLoader.java:158` line.

After Phase 4, the only JUL reference left in the reactor is the `jul-to-slf4j` dependency itself, doing its bridging job at runtime.

## Tests

Logger removal itself is verified by `mvn compile` + the existing suite — if 313 constructor parameters disappear and nothing breaks, the wiring is right. No new tests for the migration itself.

### How to assert log output in tests post-migration

Constructor-injected loggers were never used to *verify* log calls — a codebase grep finds zero `verify(logger)…` or log-content assertions today. Per-class static loggers don't lose this capability; they enable the three standard SLF4J/Logback test patterns:

**Pattern 1 — Logback `ListAppender` (canonical, no extra dependency).** Attach an in-memory appender to the class's logger; assert on captured events.

```java
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

class MyServiceTest {
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(MyService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() { logger.detachAppender(appender); }

    @Test
    void logsWarningOnUnknownUser() {
        new MyService().handle("ghost");
        assertThat(appender.list)
            .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("unknown user"));
    }
}
```

**Pattern 2 — `LogCaptor` (`io.github.hakky54:logcaptor`).** Same idea, one-liner setup. Adds a test-scope dependency; useful if multiple tests need log assertions.

```java
LogCaptor captor = LogCaptor.forClass(MyService.class);
// ... exercise ...
assertThat(captor.getWarnLogs()).anyMatch(s -> s.contains("unknown user"));
```

**Pattern 3 — `OutputCaptureExtension` (Spring Boot, free at tiers 3/4).** Reads whatever Logback writes to stdout/stderr. Coarser than `ListAppender`; also catches third-party log output.

```java
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class FooIT {
    @Test
    void itLogs(CapturedOutput output) {
        runTheThing();
        assertThat(output).contains("unknown user");
    }
}
```

| Tier (AGENTS.md §10) | Recommended pattern |
|---|---|
| 1 (pure unit) | `ListAppender` |
| 2 (`FacadeTestSupport`) | `ListAppender` or `LogCaptor` |
| 3/4 (`@SpringBootTest`) | `OutputCaptureExtension` or `ListAppender` |

Per AGENTS.md §13 (mock-framework policy): **never mock the SLF4J `Logger`**. Use `ListAppender` against the real logger; mocks defeat Logback's formatting and level filtering and silently mask bugs.

## Risks

- **Bulk refactor across 289 files** — the territory where the `bulk-refactor-scripts` skill was created. Run on a `/branch`, compile after every script, don't trust regexes blindly.
- **`RaplaComponent#getLogger()` is `protected`** — likely subclassed by dhbwrapla and external plugins. Removing it is a binary-incompatible change. Mitigation: keep `getLogger()` as a thin wrapper that returns the per-instance class's SLF4J logger via `LoggerFactory.getLogger(getClass())`, document as deprecated, remove in a follow-up release.
- **dhbwrapla** imports `org.rapla.logger.Logger` in its own sources. Coordinate: either ship rapla phase 1 first (no API break), then dhbwrapla, then phase 2; or merge both branches together.
- **Logback configs reference `rapla.*` category names** (e.g. `<logger name="rapla.remotefacade">`). Once loggers are class-named, those rules stop matching. Either rename to `org.rapla.*` in the same change, or keep the named-logger pattern for those few categories (currently ~10 distinct names in `logback-test.xml`).

## Open questions

- Keep `RaplaComponent#getLogger()` as a deprecated wrapper, or remove outright and force the dhbwrapla port? (Recommend: keep one release for the deprecation cycle.)
- Rename `logback*.xml` category names from `rapla.*` to `org.rapla.*` in the same PR, or as a follow-up? (Recommend: same PR — the configs are small and the mismatch would be confusing.)
- Add a test-scope dependency on `logcaptor`, or rely on hand-rolled `ListAppender` setup? (Recommend: defer — add only when a real test needs it, to keep the migration scope tight.)
