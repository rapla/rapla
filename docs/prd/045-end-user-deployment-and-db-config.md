    # PRD 045: End-User Deployment Model, Database Configuration & Drop-in Plugins

**Status:** in-progress — Phases 1 + 2 + 3 landed 2026-05-18; Phase 4 (PropertiesLauncher + bundled `loader.properties`) planned 2026-05-23; OQ5 resolved 2026-05-23 (no tarball, no launcher script — fat JAR + bundled `loader.properties` is the distribution). Plugin contract (Phases 5+6, merged from former PRD 046) planned 2026-05-23.
**Date:** 2026-05-18 (plugin merge: 2026-05-23)

## Goal

Define how an end user (a site operator, not a rapla developer) installs, configures, runs, and **extends** the Spring Boot rapla server — and close the gap that makes a database-backed deployment currently impossible via configuration.

Four concerns, at different stages:

1. **Externalized configuration** — operator overrides `application.yml` without rebuilding the fat JAR. *Mostly works today; needs docs.*
2. **Production-sane bundled config** — the baked `application.yml` is currently a dev config. *Concrete fix, planned below.*
3. **Database configuration** — selecting MariaDB/PostgreSQL/HSQLDB instead of the XML file store. *Half-wired in code; design-first.*
4. **Drop-in plugin model** — operator drops `<vendor>.jar` into `./plugins/`, restart, plugin's `@AutoConfiguration` is discovered and adds REST endpoints, `AuthenticationStore`s, scheduled jobs. *Contract designed; build wiring planned in Phase 4; example + docs in Phase 6.* (Merged from former PRD 046 on 2026-05-23.)

This PRD covers the vanilla end-user story **and** the forward extension story. [PRD 003](003-custom-deployments-after-spring-migration.md) covers the legacy custom-deployable model (downstream `@SpringBootApplication` + own fat JAR); dhbwrapla is its only instance and is targeted for migration to the plugin model in a follow-up PRD. New customizations should start with §4 (plugin contract) below, not [PRD 003](003-custom-deployments-after-spring-migration.md).

## Background — current state

`mvn package` on `rapla-app` produces one artifact: `rapla-2.1-SNAPSHOT.jar`, a Spring Boot fat JAR (~45 MB; embedded Tomcat 11, all deps in `BOOT-INF/lib/`, Angular SPA under `static/app/`, signed JNLP webclient jars served from `BOOT-INF/lib/`). It is self-contained and immutable — operators must never edit inside it. The whole rapla is the unit of update; granular per-jar replacement is **not** a supported workflow (see Phase 4 / Non-goals).

### 1. Externalized configuration — works

Spring Boot loads `application.yml` from several locations, later overriding earlier: classpath root (baked-in defaults) → `./config/application.yml` → `./application.yml` → env vars (`SERVER_PORT`, `RAPLA_OAUTH_PUBLIC_BASE_URL`, …) → command-line `--key=value`. Profiles via `SPRING_PROFILES_ACTIVE`.

Intended on-disk layout:

```
/opt/rapla/
  rapla-2.1-SNAPSHOT.jar      ← never touched
  config/
    application.yml           ← thin override: only the keys the operator changes
  data/
    data.xml                  ← XML store, path relative to CWD
```

Run with CWD = `/opt/rapla`: `java -jar rapla-2.1-SNAPSHOT.jar`.

No code change needed — standard Spring Boot. Deliverable: documentation plus a shipped `config/application.yml.sample`.

### 2. Bundled `application.yml` is a dev config — must change

The YAML currently inside the JAR carries dev-only values:

- `logging.level.org.springframework.security: DEBUG` — comment itself marks it `TEMPORARY`.
- `rapla.oauth.public-base-url: http://localhost:8051` default.
- OAuth redirect URIs are all loopback / `:4200` (dev surfaces).

Shipping these as defaults means every deployment inherits dev-flavoured values and must remember to override them.

**Not** a problem: `rapla.oauth.allow-wsl-bridge-redirects: true` stays as-is. It only matches a redirect URI whose host is in `172.16.0.0/12` (private, non-routable), the path must still match a registered URI, and PKCE is mandatory, so an intercepted authorization code is useless without the `code_verifier`. Outside WSL2 dev no legitimate redirect URI falls in that range, so the flag is **inert** in production regardless of its value. Only change: drop the alarmist "PRODUCTION DEPLOYMENTS MUST SET THIS TO false" comment and reframe as developer convenience.

**Direction (decided 2026-05-18):** baked-in `application.yml` becomes production-sane — the `org.springframework.security: DEBUG` logging and dev `public-base-url` move into the existing `application-local.yml` (the `local` profile, already used for IdP secrets, gitignored). No new `application-dev.yml` — the established `local` profile is the dev-override file. Broader guidance for a secure rapla install: **use a proper external IdP** (Keycloak/Entra/Google — [PRD 036](036-external-idp-oauth-login.md)) rather than the embedded authorization server.

### 3. Database configuration — half-wired, the real gap

Storage layer already supports both backends: `ServerStorageSelector.get()` returns a `DBOperator` (JDBC) when `ServerContainerContext.isDbDatasource()` is true, otherwise a `FileOperator` (XML). `DBOperator` takes a `javax.sql.DataSource`. rapla historically supported HSQLDB and MariaDB/PostgreSQL via `DBOperator`.

But the Spring wiring never connects the dots:

- `RaplaServerProperties` binds `rapla.db-datasources.*` into a `Map<String, DataSourceProperties> dbDatasources` — and **nothing ever reads that map.**
- `LegacyServerBridgeConfig.serverContainerContext(...)` copies only `fileDatasources` into the `ServerContainerContext`. No code builds a `DataSource` from a `DataSourceProperties` entry or calls `context.addDbDatasource("jdbc/rapladb", ds)`.
- `ServerServiceConfig` is gated `@ConditionalOnProperty(prefix = "rapla.file-datasources", name = "raplafile")` — the whole server stack only boots when the **file** datasource is set. A DB-only deployment cannot start. (`JwtConfig` and `ApiKeyController` carry the same condition.)

Net: **switching rapla to a database via `application.yml` does not work today.** A code gap, not a config gap.

JDBC driver classpath strategy (decided 2026-05-18 — resolves OQ2):

- **HSQLDB** — bundled as a normal Maven `runtime` dependency, lands in `BOOT-INF/lib/`. The embedded, zero-external-server default backend.
- **PostgreSQL + MariaDB** — shipped in an external `./lib/` directory beside the deployable, *not* inside the JAR. Operators swap a driver by replacing the file. Picked up at launch via the bundled `loader.properties` (Phase 4 — `loader.path=lib/,plugins/`, no operator flag).
- **SQL Server drivers ship with dhbwrapla only.** Both Microsoft's `mssql-jdbc` and legacy jTDS are dhbwrapla concerns — its Dualis datasource targets SQL Server ([PRD 003](003-custom-deployments-after-spring-migration.md)). Vanilla rapla ships neither. Storage layer still *supports* SQL Server (`isSQLServer()` — see below), so a vanilla operator who needs it places a driver jar in `./lib/` themselves; the rapla project just doesn't provide one.
- **No `<scope>system</scope>`.** Shipped drivers staged into `./lib/` by the build (maven-dependency-plugin `copy`), not referenced via Maven system scope.

SQL-dialect handling for all four backends already exists — `AbstractTableStorage` has `isHsqldb()` / `isMysql()` (covers MariaDB) / `isPostgres()` / `isSQLServer()`, branching on `getDatabaseProductName()`. Crucially `isSQLServer()` matches the *product name* (`"Microsoft SQL Server"`), not the driver — so rapla's dialect code is **driver-agnostic**: jTDS and mssql-jdbc both connect to the same product and trigger the same SQL branch. Only the drivers are missing from the deployable; the operator code is ready.

**SQL Server note.** Operator places a driver jar in `./lib/` themselves — Microsoft `mssql-jdbc` (URL `jdbc:sqlserver://host:port;databaseName=db`) or legacy jTDS (`jdbc:jtds:sqlserver://host:port/db`). Spring Boot/HikariCP derives the driver class from the URL scheme, so no explicit `driver-class-name` is needed, and `isSQLServer()` is product-name based so the dialect works with either.

### 4. Drop-in plugin model — designed, build wiring pending

There is **no plugin loader today** (investigated 2026-05-18):

- No `META-INF/services`, no annotation processor, no `ServiceLoader`. The legacy `@Extension` / `@DefaultImplementation` / `@ExtensionPoint` system was removed in PRD 001 — see `docs/architecture/extension-points.md`.
- Server discovery is **explicit only**: `RaplaServerAutoConfiguration` scans exactly `@ComponentScan("org.rapla.server.spring.web")`; everything else is wired by hand-written `@Bean` factories in `ServerCoreConfig` / `ServerServiceConfig` with hardcoded class names. A class in a jar that wasn't part of the build is never discovered.
- No `./plugins/` directory in any build or assembly descriptor.

**The one mechanism that already works in our favour:** Spring Boot aggregates `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` from **every jar on the classpath**. rapla-server already ships such a file to deliver `RaplaServerAutoConfiguration` (verified by `AutoConfigImportTest`). A plugin jar carrying its own `AutoConfiguration.imports` is therefore discovered by the same path — **no new scanning machinery required.**

**Classpath gap closed by Phase 4.** Phase 4 below switches `spring-boot-maven-plugin` to `PropertiesLauncher` and bakes a `loader.properties` resource with `loader.path=lib/,plugins/` into the fat JAR. `./plugins/*.jar` therefore lands on the runtime classpath with **no operator flag and no launcher script** — just the existing `java -jar /opt/rapla/rapla-X.jar`. The plugin **contract** (what a plugin author writes) is the only thing left to define; that lives in Phase 5 + 6.

**Why drop-in, not [PRD 003](003-custom-deployments-after-spring-migration.md)'s custom deployable?** [PRD 003](003-custom-deployments-after-spring-migration.md) made downstream customizers rebuild a *whole* ~45 MB fat JAR just to add a few server beans. Every rapla patch forced a downstream rebuild. The drop-in model:

- Decoupled release cadence — rapla security patches land with no downstream build.
- Proportionate effort — one small autoconfig jar, not a full Spring Boot app.
- Composability — multiple independent vendor plugins coexist in `./plugins/`.
- Operator stays on the canonical artifact — same tests, CI signal, support story as stock rapla.

The known trade-off — **API drift caught at runtime, not compile time** — accepted 2026-05-23 (resolves former PRD 046 OQ3): rapla does not freeze a plugin-facing API; plugin authors recompile and re-test against each rapla release. Documented loudly in the plugin guide.

## Scope

In scope:
- End-user deployment model and documentation.
- Hardening bundled `application.yml` (concrete, planned).
- **Design** of database configuration — config shape, wiring points, driver-on-classpath strategy, boot-condition change. No DB implementation lands in this PRD revision.
- Launcher shape — switching `spring-boot-maven-plugin` to `PropertiesLauncher` and baking `loader.properties` so external `./lib/` and `./plugins/` directories are picked up without operator flags (Phase 4).
- Drop-in plugin **contract** — what a plugin author writes (`@AutoConfiguration` jar layout, package convention, `rapla.plugins.<id>.enabled` gating, plugin REST endpoint contract, autoconfig ordering after `RaplaServerAutoConfiguration`). Merged from former PRD 046. Phases 5+6.
- Minimal example plugin and `docs/plugins.md` author guide. Phase 6.

Out of scope (deferred or owned elsewhere):
- **Client-side (Swing) drop-in plugins.** Swing jars are JNLP-signed in a single pass by `rapla-app`; an unsigned operator-supplied Swing jar breaks the signing invariant. Custom Swing follows [PRD 003](003-custom-deployments-after-spring-migration.md).
- **Angular SPA frontend plugins** — owned by **[PRD 047](047-angular-frontend-plugin-model.md)** (Native Federation remotes). A [PRD 047](047-angular-frontend-plugin-model.md) frontend plugin is delivered as a jar of exactly this PRD's shape — an `@AutoConfiguration` jar — that *additionally* carries `static/plugins/<id>/` remote assets plus a `RaplaUiRemote` `@Bean`. Discovered via the same `./plugins/` wiring.
- Custom downstream deployments (legacy model) — [PRD 003](003-custom-deployments-after-spring-migration.md). Drop-in plugin model is the new path; dhbwrapla migration is a follow-up PRD.
- External IdP setup — [PRD 036](036-external-idp-oauth-login.md).
- Multi-pod / shared-store coordination — `docs/architecture/locking.md`, [PRD 040](040-dispatch-validate-before-lock.md).
- Granular per-rapla-jar replacement. **Non-goal** — fat JAR is the unit of rapla update; layered extraction considered and rejected (over-engineering for an unused workflow). See Phase 4 rationale.
- Hot reload / unload of plugins — discovered at boot only.
- A plugin marketplace, dependency resolution between plugins, or curated plugin registry.
- Signature verification of plugin jars — document-only trust model for v1; signature preflight is a follow-up.

## Plan

**Phase 1 — Document the deployment model. ✅ Done 2026-05-18.**
`docs/deployment.md` written: fat-JAR model, `config/` + `data/` + `lib/` layout, externalized-config precedence, profiles, must-set-for-production keys, full database section (file vs db backend, driver matrix, empty-DB bootstrap + pre-1.8 manual-migration caveat from OQ4), systemd / WinSW service recipes. Supersedes stale `INSTALL.txt` / `README-Server.txt`.

**Phase 2 — Production-sane bundled `application.yml`. ✅ Done 2026-05-18.**
Only two keys are genuinely dev-specific. What landed:

*Changes to committed `application.yml` (JAR defaults):*
- **Remove** `logging.level.org.springframework.security: DEBUG` — falls back to `root: INFO`.
- **Change** `rapla.oauth.public-base-url` default from `${RAPLA_OAUTH_PUBLIC_BASE_URL:http://localhost:8051}` to `${RAPLA_OAUTH_PUBLIC_BASE_URL:}` — empty default means request-derived origin (correct for production per existing YAML comment).
- **Reframe** the `allow-wsl-bridge-redirects` comment as a dev convenience; value stays `true` (inert in production — see §2).
- **Keep** `redirect-uris` unsplit. A profile YAML list *replaces* rather than appends; splitting is messy. Dev loopback/`:4200`/`:8051` entries are inert in production; `allow-same-origin-redirects: true` covers the prod SPA callback.

*Dev overrides → `application-local.yml` (gitignored, `local` profile):*
- `logging.level.org.springframework.security: DEBUG`
- `rapla.oauth.public-base-url: http://localhost:8051` (the `ng serve` proxy case)
- (existing `rapla.oauth.external.{microsoft,google}` IdP test config stays)

*No committed template.* Fresh checkout discovers dev-override content from a new section in `docs/development.md` (paste-ready snippet). `application-local.yml` not un-gitignored.

*AGENTS.md §8* — dev-server recipe gains `SPRING_PROFILES_ACTIVE=local` so dev server actually loads `application-local.yml`. Fresh checkout without the file runs with `INFO` logging and request-derived `public-base-url` — acceptable.

Test-first: tier-3 boot test (no profile) asserting `org.springframework.security` logging at `INFO`, not `DEBUG`.

**Phase 3 — DB configuration. ✅ Done 2026-05-18.** What landed:
- `LegacyServerBridgeConfig.serverContainerContext()` iterates `RaplaServerProperties.getDbDatasources()`, builds a `DataSource` per entry via `DataSourceProperties.initializeDataSourceBuilder().build()` (HikariCP, driver class auto-derived from JDBC URL) and registers each via `ServerContainerContext.addDbDatasource(key, ds)`.
- New `DatasourceConfiguredCondition` (Spring `AnyNestedCondition`) ORs `rapla.file-datasources.raplafile` and `rapla.db-datasources.rapladb.url`. `ServerServiceConfig`, `JwtConfig` and `ApiKeyController` swapped their `@ConditionalOnProperty(...raplafile)` for `@Conditional(DatasourceConfiguredCondition.class)` — server boots on file **or** db.
- Canonical key `rapladb` (OQ1): `ServerContainerContext.getMainDbDatasource()` now looks up `MAIN_DB_DATASOURCE = "rapladb"`, not `"jdbc/rapladb"`. YAML: `rapla.db-datasources.rapladb.{url,username,password}`.
- `FileOperator` stays wired as `ImportExportManager` source in DB mode (OQ3/OQ4) — `ServerStorageSelector` unchanged; empty DB seeded from file source on first boot.
- **HikariCP added as a `runtime` dependency of `rapla-server`.** `rapla-server` pulls `spring-boot-jdbc`, *not* the starter, so no connection pool was on the classpath and `DataSourceBuilder.build()` failed with "No supported DataSource type found". HikariCP (Spring Boot default) is now bundled.
- HSQLDB flipped from `provided` to `runtime` scope in `rapla-server` so it lands in fat JAR's `BOOT-INF/lib/` (§3/OQ2). PostgreSQL + MariaDB stay external `./lib/`; SQL Server operator-supplied — picked up via Phase 4 `loader.path` (no rebuild needed).
- Verified by `DbDatasourceBootIntegrationTest`.

**Phase 4 — PropertiesLauncher + bundled `loader.properties`.** Planned 2026-05-23. Locks in launcher shape so external `./lib/` (JDBC drivers, Phase 3) and `./plugins/` (plugin jars — §4 + Phase 5+6) are discovered without operator flags or launcher scripts.

*Build change* — `rapla-app/pom.xml`:
```xml
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <configuration>
    <layout>ZIP</layout>
    <loaderImplementation>PROPERTIES</loaderImplementation>
  </configuration>
</plugin>
```

*New resource* — `rapla-app/src/main/resources/loader.properties`:
```
loader.path=lib/,plugins/
```

The `<layout>ZIP</layout>` switch changes the fat JAR's `Main-Class` from Spring Boot's default `JarLauncher` to `PropertiesLauncher`, which reads `loader.properties` from the classpath root at launch. `loader.path` is relative to the JAR's CWD — so `lib/` and `plugins/` resolve to `./lib/` and `./plugins/` next to wherever the operator runs `java -jar`.

*Operator launch* (unchanged shape):
```bash
java -jar /opt/rapla/rapla-2.1-SNAPSHOT.jar
```
No `-Dloader.path` flag — manifest plus `loader.properties` carry it. systemd `ExecStart=` stays a plain `java -jar` line; no launcher script.

*Upgrade workflows* — three independent paths, each "drop file + restart":

| Task | Operator does | Downtime |
|---|---|---|
| Upgrade rapla | `cp` new fat JAR over the old one, restart | ~15s |
| Add / upgrade plugin | `cp` jar into `./plugins/`, restart | ~15s |
| Add / upgrade JDBC driver | `cp` jar into `./lib/`, restart | ~15s |

*Multi-pod zero-downtime* — rolling restart across pods; shared-store update history (~10s polling) handles per-pod brief unavailability without coordination. Documented in `docs/deployment.md`.

*Why no layered extraction* — investigated and rejected 2026-05-23. Spring Boot's `extract --layers` was designed for Docker layer caching, not operational upgrades. Using it for "easy hot-swap of individual rapla jars" introduces a required extraction step at install, classpath-conflict risk on dep version bumps, and two parallel launch modes. The granular-replacement benefit is moot: rapla doesn't ship per-jar hotfixes, and ~45 MB bandwidth saving on full re-download is meaningless (~4s on 100 Mbps). `./plugins/` and external `./lib/` cover the granular upgrade paths that matter.

**Phase 5 — Plugin contract.** Planned 2026-05-23. (Folded in from former PRD 046.) A rapla server plugin jar:

- Contains an `@AutoConfiguration(after = RaplaServerAutoConfiguration.class)` class that `@ComponentScan`s the plugin's own package (or declares `@Bean`s explicitly).
- Lists that class in its own `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Lives in a package **other than `org.rapla.*`** (a plugin component-scanning `org.rapla.*` would double-register stock beans). Recommended convention: `<vendor>.raplaplugin.*`.
- Is gated on `@ConditionalOnProperty(prefix = "rapla.plugins", name = "<id>.enabled", matchIfMissing = true)` so an operator can disable a deployed plugin via config without removing the jar.
- Compiles against `rapla-server` + `rapla-core` as `provided` Maven deps (resolved from the deployable at runtime). No separate `rapla-plugin-api` artifact — consistent with PRD [003](003-custom-deployments-after-spring-migration.md)/005 declining the `rapla-client-api` split.

*REST endpoints from plugins:* a plugin `@RestController` is picked up by the plugin's own `@ComponentScan`. AGENTS.md §15 still applies — class-level `@RequestMapping("/api/<name>")` — but `ApiPrefixArchitectureTest` runs in the rapla-app build and **cannot see plugin jars**. The fix is a runtime `ApplicationListener` in rapla-server that, after refresh, logs a WARN for any mapped handler outside `/api/` not on the allow-list. SpringDoc group assignment (§15) is rapla-internal config; plugin endpoints simply won't appear in the generated SPA client, which is correct (the SPA is built against stock rapla).

*Boot ordering interaction with Phase 3:* Phase 3 relaxes the server-stack boot condition (`@Conditional(DatasourceConfiguredCondition.class)`). Plugin autoconfig is `after = RaplaServerAutoConfiguration` so it orders correctly; a plugin that injects rapla beans (`RaplaFacade`, `PermissionController`) gets a fully-initialized context. Tier-3 boot test verifies.

**Phase 6 — Example plugin + author guide.** Ship a minimal example plugin (`/api/hello` endpoint) as a separate tiny Maven module *outside* the reactor (like `rapla-angular/` is outside it) — or in a `docs/examples/` sketch. Write `docs/plugins.md`: Phase 5 contract, build setup, trust model (document-only — "only install plugins you trust"), `rapla.plugins.<id>.enabled` switch, and explicit "no API stability guarantee across rapla minor versions — recompile and re-test against each release" warning.

## Tests

- Phase 2: tier-3 `@SpringBootTest` (no profile) asserting prod defaults — `org.springframework.security` log level not `DEBUG`.
- Phase 3 ✅ — `DbDatasourceBootIntegrationTest` (`rapla-app`, `@Tag("e2e")` + `@Tag("db")`): `@SpringBootTest` configuring `rapla.db-datasources.rapladb` (embedded HSQLDB) plus a temp `testdefault.xml` file seed. Asserts the context boots, `ServerStorageSelector.get()` is the `DBOperator` (DB is the live store), and first boot creates the schema + imports the bootstrap admin from the file seed (OQ4 path). Revert-checked per AGENTS.md §1.
- Per AGENTS.md §10 nevers: any new constructor arg on `FacadeImpl` / `FileOperator` updates `FacadeTestSupport` in the same change; DB-hitting tests get `@Tag("db")`.
- Phase 4: tier-3 / `test-deployment` skill — verify packaged fat JAR's `META-INF/MANIFEST.MF` declares `Main-Class: org.springframework.boot.loader.launch.PropertiesLauncher` (not `JarLauncher`) and that `loader.properties` exists at the classpath root with `loader.path=lib/,plugins/`. Boot the packaged JAR with a stub jar in `./plugins/`; assert its `@Component` resolves in the running context (proves external `loader.path` discovery, end-to-end).
- Phase 5: tier-3 `@SpringBootTest` — a fixture plugin jar (built as a test resource) on the test classpath; assert its `@Bean` is present in the context and its `@RestController` answers via MockMvc. Mirrors `AutoConfigImportTest`.
- Phase 5: assert `rapla.plugins.<id>.enabled=false` removes the plugin's beans.
- Phase 5: tier-3 test that a plugin controller mapped outside `/api/` triggers the startup WARN (capture the log).
- Phase 5: tier-3 boot test — plugin autoconfig that `@Autowired`s `RaplaFacade` boots cleanly, proving ordering after `RaplaServerAutoConfiguration`.
- Per AGENTS.md §13: no mocks of rapla types — the example plugin's tests use the real Spring context.

## Open Questions

1. ~~Canonical DB datasource key.~~ **Resolved 2026-05-18.** `rapladb` — symmetric with file side's `raplafile` and free of YAML bracket-quoting a `/` forces. `getMainDbDatasource()` changed from `"jdbc/rapladb"` to `"rapladb"`. No back-compat cost.
2. ~~JDBC driver on the closed fat-JAR classpath.~~ **Resolved 2026-05-18.** HSQLDB bundled in `BOOT-INF/lib/`; PostgreSQL + MariaDB shipped in external replaceable `./lib/`, picked up via Spring Boot's `-Dloader.path=lib/` convention. SQL Server drivers ship with dhbwrapla only ([PRD 003](003-custom-deployments-after-spring-migration.md)); vanilla rapla operator-supplied into `./lib/`.
3. ~~File + DB both configured.~~ **Resolved 2026-05-18.** Not an ambiguity — it's the designed model. `ServerStorageSelector` *always* constructs `FileOperator` (defaulting to `data/data.xml`) as the `ImportExportManager` **source** and `DBOperator` as destination. When a DB datasource is configured, `ServerStorageSelector.get()` returns the DB operator as the live store; file operator is used only as empty-DB bootstrap import seed (OQ4). No fail-fast. The startup log line `Using datasource <product>: <url>` makes the live store explicit.
4. ~~Schema creation / migration.~~ **Resolved 2026-05-18 — confirmed in code.** `DBOperator.loadData()` → `upgradeDatabase()`:
   - **Empty database** (no `DYNAMIC_TYPE` table): `RaplaSQL.createOrUpdateIfNecessary()` creates the full schema, then data is **bootstrap-imported from XML source** (`data/data.xml`, via `ImportExportManager`). Fresh DB deployment still needs a readable `data.xml` seed — `FileOperator` falls back to `data/data.xml` and auto-creates a default bootstrap file (with default admin) if absent.
   - **Current schema:** `createOrUpdateIfNecessary` migrates an out-of-date schema in place.
   - **Pre-1.8 schema** (integer id columns): NOT auto-migrated — `DBOperator` throws with explicit "export data.xml with 1.8 rapla version and import" message. `docs/deployment.md` (Phase 1) must document this.
5. ~~Distribution packaging.~~ **Resolved 2026-05-23.** Ship **just the fat JAR**. No tarball, no assembly descriptor, no launcher script — bundled `loader.properties` (Phase 4) makes `./lib/` and `./plugins/` discoverable without operator flags. `docs/deployment.md` is the install guide.

   `./lib/` is just an empty directory the operator creates (`mkdir -p /opt/rapla/{config,data,lib,plugins,logs}`). No pre-populated content needs to ship. The stale `rapla.distribution.xml`, `INSTALL.txt`, `README-Server.txt`, and Tanuki `service/` wrapper are deleted in Phase 4.

   **Service install — Linux:** plain systemd unit (`ExecStart=/usr/bin/java -jar /opt/rapla/rapla-X.jar`, `WorkingDirectory=/opt/rapla`). No wrapper. Documented in `docs/deployment.md`.

   **Service install — Windows:** Spring Boot has no native Windows-service support, and `sc.exe` cannot run `java -jar` directly (no SCM handshake). Wrapper `.exe` required. Recommended: **WinSW** (MIT, maintained, single `.exe` + short XML, any-bit Java, log rotation, graceful `<stoptimeout>`). Alternatives: NSSM (barely maintained), Apache Procrun (fiddly). Current rapla `service/` Tanuki Java Service Wrapper is **dropped** — its community edition is 32-bit-only (source of the legacy "you may need 32-bit Java" advice). Pair WinSW's `<stoptimeout>` with `server.shutdown=graceful` so a service stop drains in-flight requests.
6. ~~`./plugins/` separate from `./lib/`, or one directory?~~ **Resolved 2026-05-23 (former PRD 046 OQ1).** Keep separate. Split is by *role* — JDBC drivers are infrastructure, plugins are application code — and reads clearer to an operator scanning the install dir. `loader.path=lib/,plugins/` covers both.
7. ~~Trust model for `./plugins/` jars.~~ **Resolved 2026-05-23 (former PRD 046 OQ2) — document-only for v1.** A `./plugins/` jar runs with full server privileges; arbitrary code execution by design. `docs/plugins.md` (Phase 6) documents "only install plugins you trust." Signature verification (reusing JNLP signing) would need a launch-script preflight step (JVM can't verify once class is on `-cp` glob) — out of scope.
8. ~~API stability guarantee for plugins.~~ **Resolved 2026-05-23 (former PRD 046 OQ3) — accept drift, document loudly.** rapla does not freeze a plugin-facing API. Plugin authors recompile and re-test against each rapla release. Matches PRD [003](003-custom-deployments-after-spring-migration.md)/005 declining `rapla-client-api` split. Revisit if third-party plugin authors emerge.
9. ~~`@ComponentScan` collision guard.~~ **Resolved 2026-05-23 (former PRD 046 OQ4) — document, don't enforce.** A plugin that scans `org.rapla.*` would double-register stock beans; failure is loud and immediate at boot. `docs/plugins.md` documents the `<vendor>.raplaplugin.*` convention. Active rejection out of scope for v1.
10. ~~Relationship to dhbwrapla.~~ **Resolved 2026-05-23 (former PRD 046 OQ5) — dhbwrapla migrates to plugin.** The 2026-05-23 deployment-strategy discussion accepted that dhbwrapla becomes a drop-in plugin jar (`rapla-plugin-dhbw-2.1.jar`, ~3 MB) replacing the [PRD 003](003-custom-deployments-after-spring-migration.md) full custom deployable. Migration is a separate follow-up PRD — covers [PRD 003](003-custom-deployments-after-spring-migration.md) D2 pending work (Date→LocalDateTime drift, jcifs-ng port, scheduler API migration) plus the structural shift. [PRD 003](003-custom-deployments-after-spring-migration.md) stays in `docs/prd/` as legacy reference until that lands.
