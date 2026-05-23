    # PRD 045: End-User Deployment Model, Database Configuration & Drop-in Plugins

**Status:** in-progress — Phases 1 + 2 + 3 landed 2026-05-18; Phase 4 (PropertiesLauncher + bundled `loader.properties`) planned 2026-05-23; OQ5 resolved 2026-05-23 (no tarball, no launcher script — fat JAR + bundled `loader.properties` is the distribution). Plugin contract (Phases 5+6, merged from former PRD 046) planned 2026-05-23.
**Date:** 2026-05-18 (plugin merge: 2026-05-23)

## Goal

Define how an end user (a site operator, not a rapla developer) installs,
configures, runs, and **extends** the Spring Boot rapla server — and close the
gap that makes a database-backed deployment currently impossible via
configuration.

Four concerns, at different stages:

1. **Externalized configuration** — how the operator overrides `application.yml`
   without rebuilding the fat JAR. *Mostly works today; needs documentation.*
2. **Production-sane bundled config** — the `application.yml` baked into the JAR
   is currently a dev config. *Concrete fix, planned below.*
3. **Database configuration** — selecting MariaDB / PostgreSQL / HSQLDB instead
   of the XML file store. *Half-wired in code; design-first, no implementation
   in this PRD revision.*
4. **Drop-in plugin model** — operator drops `<vendor>.jar` into `./plugins/`,
   restart, plugin's `@AutoConfiguration` is discovered and adds REST endpoints,
   `AuthenticationStore`s, scheduled jobs, etc. *Contract designed; build
   wiring planned in Phase 4; example + docs planned in Phase 6.* (Merged from
   former PRD 046 on 2026-05-23.)

This PRD covers the vanilla end-user story **and** the forward extension story.
PRD 003 covers the legacy custom-deployable model (downstream
`@SpringBootApplication` + own fat JAR); dhbwrapla is its only instance and
is targeted for migration to the plugin model in a follow-up PRD. New
customizations should start with §4 (plugin contract) below, not PRD 003.

## Background — current state

`mvn package` on `rapla-app` produces one artifact: `rapla-2.1-SNAPSHOT.jar`,
a Spring Boot fat JAR (~45 MB; embedded Tomcat 11, all deps in `BOOT-INF/lib/`,
the Angular SPA under `static/app/`, signed JNLP webclient jars served from
`BOOT-INF/lib/`). It is self-contained and immutable — operators must never
edit inside it. The whole rapla is the unit of update; granular per-jar
replacement is **not** a supported workflow (see Phase 4 / Non-goals).

### 1. Externalized configuration — works

Spring Boot loads `application.yml` from several locations, later overriding
earlier: classpath root (the baked-in defaults) → `./config/application.yml`
→ `./application.yml` → env vars (`SERVER_PORT`, `RAPLA_OAUTH_PUBLIC_BASE_URL`,
…) → command-line `--key=value`. Profiles via `SPRING_PROFILES_ACTIVE`.

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

No code change needed — this is standard Spring Boot. The deliverable is
documentation plus a shipped `config/application.yml.sample`.

### 2. Bundled `application.yml` is a dev config — must change

The YAML currently inside the JAR carries dev-only values:

- `logging.level.org.springframework.security: DEBUG` — the comment itself
  marks it `TEMPORARY`.
- `rapla.oauth.public-base-url: http://localhost:8051` default.
- OAuth redirect URIs are all loopback / `:4200` (dev surfaces).

Shipping these as defaults means every deployment inherits dev-flavoured values
and must remember to override them.

**Not** a problem, despite earlier framing: `rapla.oauth.allow-wsl-bridge-redirects:
true` stays as-is. It is not a meaningful security risk — it only ever matches
a redirect URI whose host is in `172.16.0.0/12` (private, non-routable), the
path must still match a registered URI, and PKCE is mandatory, so an
intercepted authorization code is useless without the `code_verifier`. Outside
a WSL2 dev environment no legitimate redirect URI falls in that range, so the
flag is **inert** in a normal production deployment regardless of its value.
The only change here is to drop the alarmist "PRODUCTION DEPLOYMENTS MUST SET
THIS TO false" comment and reframe it as a developer convenience.

**Direction (decided 2026-05-18):** the baked-in `application.yml` becomes
production-sane — the `org.springframework.security: DEBUG` logging and the dev
`public-base-url` move into the existing `application-local.yml` (the `local`
profile, already used for IdP secrets, gitignored). No new `application-dev.yml`
— the established `local` profile is the dev-override file. The broader
guidance for a secure rapla installation is **use a proper external IdP**
(Keycloak / Entra / Google — see PRD 036) rather than the embedded
authorization server.

### 3. Database configuration — half-wired, the real gap

The storage layer already supports both backends:
`ServerStorageSelector.get()` returns a `DBOperator` (JDBC) when
`ServerContainerContext.isDbDatasource()` is true, otherwise a `FileOperator`
(XML). `DBOperator` takes a `javax.sql.DataSource`. rapla historically
supported HSQLDB and MariaDB/PostgreSQL via the `DBOperator`.

But the Spring wiring never connects the dots:

- `RaplaServerProperties` binds `rapla.db-datasources.*` into a
  `Map<String, DataSourceProperties> dbDatasources` — and **nothing ever reads
  that map.**
- `LegacyServerBridgeConfig.serverContainerContext(...)` copies only
  `fileDatasources` into the `ServerContainerContext`. No code builds a
  `DataSource` from a `DataSourceProperties` entry or calls
  `context.addDbDatasource("jdbc/rapladb", ds)`.
- `ServerServiceConfig` is gated `@ConditionalOnProperty(prefix =
  "rapla.file-datasources", name = "raplafile")` — the whole server stack only
  boots when the **file** datasource is set. A DB-only deployment cannot start.
  (`JwtConfig` and `ApiKeyController` carry the same condition.)

Net result: **switching rapla to a database via `application.yml` does not work
today.** It is a code gap, not a config gap.

There is also a classpath consequence for JDBC drivers (decided 2026-05-18 —
resolves OQ2):

- **HSQLDB** — bundled as a normal Maven `runtime` dependency, lands in
  `BOOT-INF/lib/`. It is the embedded, zero-external-server default backend.
- **PostgreSQL + MariaDB** — shipped in an external `./lib/` directory beside
  the deployable, *not* inside the JAR. Operators replace a driver (newer
  version, alternate build) by swapping the file. Picked up at launch via
  the bundled `loader.properties` (Phase 4 — `loader.path=lib/,plugins/`,
  no operator flag needed).
- **SQL Server drivers ship with dhbwrapla only.** Both Microsoft's
  `mssql-jdbc` and the legacy jTDS driver are dhbwrapla concerns — its Dualis
  datasource targets SQL Server (PRD 003). Vanilla rapla ships neither. The
  storage layer still *supports* SQL Server (`isSQLServer()` — see below), so a
  vanilla-rapla operator who needs it can place a driver jar in `./lib/`
  themselves; the rapla project just does not provide one.
- **No `<scope>system</scope>`.** Shipped drivers are staged into `./lib/` by
  the build (maven-dependency-plugin `copy`), not referenced via Maven system
  scope — the `systemPath` anti-pattern PRD 003 already moved dhbwrapla away from.

The SQL-dialect handling for all four backends already exists in the storage
layer — `AbstractTableStorage` has `isHsqldb()` / `isMysql()` (covers MariaDB)
/ `isPostgres()` / `isSQLServer()`, branching on `getDatabaseProductName()`.
Crucially `isSQLServer()` matches the *product name* (`"Microsoft SQL Server"`),
not the driver — so rapla's dialect code is **driver-agnostic**: jTDS and
mssql-jdbc both connect to the same product and trigger the same SQL branch.
Only the drivers are missing from the deployable; the operator code is ready.

**SQL Server note.** Vanilla rapla ships no SQL Server driver. An operator
needing SQL Server places a driver jar in `./lib/` themselves — Microsoft
`mssql-jdbc` (URL `jdbc:sqlserver://host:port;databaseName=db`) or the legacy
jTDS driver (`jdbc:jtds:sqlserver://host:port/db`). Spring Boot / HikariCP
derives the driver class from the URL scheme, so no explicit `driver-class-name`
is needed, and `isSQLServer()` is product-name based so the dialect code works
with either driver.

### 4. Drop-in plugin model — designed, build wiring pending

There is **no plugin loader today** (investigated 2026-05-18):

- No `META-INF/services`, no annotation processor, no `ServiceLoader`. The
  legacy `@Extension` / `@DefaultImplementation` / `@ExtensionPoint` system was
  removed in the Spring Boot migration (PRD 001) — see
  `docs/architecture/extension-points.md`.
- Server discovery is **explicit only**: `RaplaServerAutoConfiguration` scans
  exactly `@ComponentScan("org.rapla.server.spring.web")`; everything else is
  wired by hand-written `@Bean` factories in `ServerCoreConfig` /
  `ServerServiceConfig` with hardcoded class names. A class in a jar that
  wasn't part of the build is never discovered.
- No `./plugins/` directory in any build or assembly descriptor.

**The one mechanism that already works in our favour:** Spring Boot aggregates
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
from **every jar on the classpath**. rapla-server already ships exactly such a
file to deliver `RaplaServerAutoConfiguration` (verified by
`AutoConfigImportTest`). A plugin jar carrying its own
`AutoConfiguration.imports` is therefore discovered by the same path the rapla
server stack already relies on — **no new scanning machinery required.**

**Classpath gap closed by Phase 4.** PRD 045 Phase 4 below switches
`spring-boot-maven-plugin` to `PropertiesLauncher` and bakes a
`loader.properties` resource with `loader.path=lib/,plugins/` into the fat JAR.
`./plugins/*.jar` therefore lands on the runtime classpath with **no operator
flag and no launcher script** — just the existing `java -jar
/opt/rapla/rapla-X.jar`. The plugin **contract** (what a plugin author writes)
is the only thing left to define; that lives in Phase 5 + 6 below.

**Why drop-in, not PRD 003's custom deployable?** PRD 003 made downstream
customizers rebuild a *whole* ~45 MB fat JAR (re-bundling rapla-core, rapla-
server, Spring Boot, Tomcat, Jackson, etc.) just to add a few server beans.
Every rapla patch forced a downstream rebuild. The drop-in model:

- Decoupled release cadence — rapla security patches land with no downstream
  build.
- Proportionate effort — one small autoconfig jar, not a full Spring Boot
  application.
- Composability — multiple independent vendor plugins coexist in `./plugins/`.
- Operator stays on the canonical artifact — same tests, CI signal, support
  story as stock rapla.

The known trade-off — **API drift caught at runtime, not compile time** — was
accepted 2026-05-23 (resolves former PRD 046 OQ3): rapla does not freeze a
plugin-facing API; plugin authors recompile and re-test against each rapla
release. Documented loudly in the plugin guide.

## Scope

In scope:
- The end-user deployment model and its documentation.
- Hardening the bundled `application.yml` (concrete, planned).
- The **design** of database configuration — config shape, wiring points,
  driver-on-classpath strategy, boot-condition change. No DB implementation
  lands in this PRD revision; it is split out once the open questions resolve.
- The launcher shape — switching `spring-boot-maven-plugin` to `PropertiesLauncher`
  and baking `loader.properties` into the JAR so external `./lib/` and `./plugins/`
  directories are picked up without operator flags (Phase 4).
- The drop-in plugin **contract** — what a plugin author writes
  (`@AutoConfiguration` jar layout, package convention, `rapla.plugins.<id>.enabled`
  gating, plugin REST endpoint contract, autoconfig ordering after
  `RaplaServerAutoConfiguration`). Merged from former PRD 046. Phases 5+6.
- A minimal example plugin and the `docs/plugins.md` author guide. Phase 6.

Out of scope (deferred or owned elsewhere):
- **Client-side (Swing) drop-in plugins.** Swing jars are JNLP-signed in a
  single pass by `rapla-app`. An unsigned operator-supplied Swing jar breaks
  the signing invariant — out of scope, possibly never. Custom Swing code
  follows PRD 003 (lives in `rapla-client`).
- **Angular SPA frontend plugins** — owned by **PRD 047** (Native Federation
  remotes). A PRD 047 frontend plugin is delivered as a jar of exactly this
  PRD's shape — an `@AutoConfiguration` jar — that *additionally* carries
  `static/plugins/<id>/` remote assets plus a `RaplaUiRemote` `@Bean`.
  Discovered via the same `./plugins/` classpath wiring.
- Custom downstream deployments (legacy model) — PRD 003. The drop-in plugin
  model defined here is the new path; dhbwrapla migration to plugin is a
  follow-up PRD.
- External IdP setup — PRD 036.
- Multi-pod / shared-store coordination — `docs/architecture/locking.md`,
  PRD 040.
- Granular per-rapla-jar replacement (hotfix one of rapla-core / -server /
  -client / -app jars without replacing the whole fat JAR). **Non-goal** —
  the fat JAR is the unit of rapla update; layered extraction was considered
  and rejected (over-engineering for a workflow that isn't used). See Phase 4
  rationale.
- Hot reload / unload of plugins — plugins discovered at boot only.
- A plugin marketplace, dependency resolution between plugins, or a curated
  plugin registry.
- Signature verification of plugin jars before adding to the classpath —
  document-only trust model for v1; signature preflight is a follow-up. (See
  former PRD 046 OQ2.)

## Plan

**Phase 1 — Document the deployment model. ✅ Done 2026-05-18.**
`docs/deployment.md` written: the fat-JAR model, the `config/` + `data/` + `lib/`
layout, the externalized-config precedence order, profiles, must-set-for-
production keys, the full database section (file vs db backend, driver matrix,
the empty-DB bootstrap + pre-1.8 manual-migration caveat from OQ4), and
systemd / WinSW service recipes. Supersedes the stale `INSTALL.txt` /
`README-Server.txt`.

**Phase 2 — Production-sane bundled `application.yml`. ✅ Done 2026-05-18.**
The split is small — only two keys are genuinely dev-specific. What landed:

*Changes to committed `application.yml` (the JAR defaults):*
- **Remove** `logging.level.org.springframework.security: DEBUG` — it falls
  back to `root: INFO`. (The line was already comment-marked `TEMPORARY`.)
- **Change** `rapla.oauth.public-base-url` default from
  `${RAPLA_OAUTH_PUBLIC_BASE_URL:http://localhost:8051}` to
  `${RAPLA_OAUTH_PUBLIC_BASE_URL:}` — an empty default means request-derived
  origin, which the YAML comment itself states is correct for production.
- **Reframe** the `allow-wsl-bridge-redirects` comment as a dev convenience;
  the value stays `true` (inert in production — see §2).
- **Keep** `redirect-uris` unsplit. A profile YAML list *replaces* rather than
  appends, so splitting is messy; the dev loopback/`:4200`/`:8051` entries are
  inert in production and `allow-same-origin-redirects: true` is what actually
  covers the prod SPA callback.

*Dev overrides → `application-local.yml` (stays gitignored, `local` profile):*
- `logging.level.org.springframework.security: DEBUG`
- `rapla.oauth.public-base-url: http://localhost:8051` (the `ng serve` proxy case)
- (the existing `rapla.oauth.external.{microsoft,google}` IdP test config stays)

*No committed template.* A fresh checkout discovers the dev-override content
from a new section in `docs/development.md` (paste-ready snippet) — decided
2026-05-18. No `application-local.yml.sample`, `application-local.yml` is not
un-gitignored.

*AGENTS.md §8* — the dev-server recipe gains `SPRING_PROFILES_ACTIVE=local` so
the dev server actually loads `application-local.yml` (today it activates no
profile, leaving that file dormant). A fresh checkout without the file simply
runs with `INFO` logging and request-derived `public-base-url` — acceptable.

Test-first per AGENTS.md §1: a tier-3 boot test (no profile) asserting the prod
default has `org.springframework.security` logging at `INFO`, not `DEBUG`.

**Phase 3 — DB configuration. ✅ Done 2026-05-18.** What landed:
- `LegacyServerBridgeConfig.serverContainerContext()` now iterates
  `RaplaServerProperties.getDbDatasources()`, builds a `DataSource` per entry via
  `DataSourceProperties.initializeDataSourceBuilder().build()` (HikariCP, driver
  class auto-derived from the JDBC URL) and registers each via
  `ServerContainerContext.addDbDatasource(key, ds)`.
- New `DatasourceConfiguredCondition` (a Spring `AnyNestedCondition`) ORs
  `rapla.file-datasources.raplafile` and `rapla.db-datasources.rapladb.url`.
  `ServerServiceConfig`, `JwtConfig` and `ApiKeyController` swapped their
  `@ConditionalOnProperty(...raplafile)` for `@Conditional(DatasourceConfigured-
  Condition.class)`, so the server boots on a file **or** a db datasource.
- Canonical key `rapladb` (OQ1): `ServerContainerContext.getMainDbDatasource()`
  now looks up the `MAIN_DB_DATASOURCE = "rapladb"` constant, not `"jdbc/rapladb"`.
  YAML shape: `rapla.db-datasources.rapladb.{url,username,password}`.
- The `FileOperator` stays wired as the `ImportExportManager` source in DB mode
  (OQ3 / OQ4) — `ServerStorageSelector` is unchanged; an empty DB is seeded from
  the file source on first boot.
- **HikariCP added as a `runtime` dependency of `rapla-server`.** Not in the
  original sketch: `rapla-server` pulls `spring-boot-jdbc`, *not* the starter, so
  no connection pool was on the classpath and `DataSourceBuilder.build()` failed
  with "No supported DataSource type found". HikariCP (Spring Boot's default
  pool) is now bundled.
- HSQLDB flipped from `provided` to `runtime` scope in `rapla-server` so it
  lands in the fat JAR's `BOOT-INF/lib/` (§3 / OQ2). PostgreSQL + MariaDB stay
  external `./lib/`; SQL Server operator-supplied — picked up via the
  Phase 4 `loader.path` (no rebuild needed).
- Verified by `DbDatasourceBootIntegrationTest` (see Tests).

**Phase 4 — PropertiesLauncher + bundled `loader.properties`.** Planned
2026-05-23. Locks in the launcher shape so external `./lib/` (JDBC drivers,
Phase 3) and `./plugins/` (plugin jars — §4 + Phase 5+6) are discovered
without operator flags or launcher scripts.

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

The `<layout>ZIP</layout>` switch changes the fat JAR's `Main-Class` from
Spring Boot's default `JarLauncher` to `PropertiesLauncher`, which reads
`loader.properties` from the classpath root at launch. `loader.path` is
relative to the JAR's CWD — so `lib/` and `plugins/` resolve to
`./lib/` and `./plugins/` next to wherever the operator runs `java -jar`.

*Operator launch* (unchanged shape):
```bash
java -jar /opt/rapla/rapla-2.1-SNAPSHOT.jar
```
No `-Dloader.path` flag — the manifest plus `loader.properties` carry it.
systemd `ExecStart=` stays a plain `java -jar` line; no launcher script.

*Upgrade workflows* — three independent paths, each "drop file + restart":

| Task | Operator does | Downtime |
|---|---|---|
| Upgrade rapla | `cp` new fat JAR over the old one, restart | ~15s |
| Add / upgrade plugin | `cp` jar into `./plugins/`, restart | ~15s |
| Add / upgrade JDBC driver | `cp` jar into `./lib/`, restart | ~15s |

*Multi-pod zero-downtime* — rolling restart across pods; the shared-store
update history (~10s polling, per the deployment topology) handles per-pod
brief unavailability without coordination. Documented in `docs/deployment.md`.

*Why no layered extraction* — investigated and rejected 2026-05-23. Spring
Boot's `extract --layers` was designed for Docker layer caching, not
operational upgrades. Using it for "easy hot-swap of individual rapla jars"
introduces:
- A required extraction step at install (manual command or launcher-script
  magic — operator-confusing either way).
- Classpath-conflict risk on dep version bumps (stale jars from the previous
  release linger unless the operator wipes the extracted dirs first).
- Two parallel launch modes (direct fat JAR vs extracted directories).

The granular-replacement benefit it would unlock is moot: rapla doesn't ship
per-jar hotfixes, and at ~45 MB the bandwidth saving on a full re-download
is meaningless (~4 s on a 100 Mbps connection). The `./plugins/` (Phase 5+6
below) and external `./lib/` mechanisms cover all the granular upgrade paths
that actually matter.

**Phase 5 — Plugin contract.** Planned 2026-05-23. (Folded in from former
PRD 046.) A rapla server plugin jar:

- Contains an `@AutoConfiguration(after = RaplaServerAutoConfiguration.class)`
  class that `@ComponentScan`s the plugin's own package (or declares `@Bean`s
  explicitly).
- Lists that class in its own
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Lives in a package **other than `org.rapla.*`** (a plugin component-scanning
  `org.rapla.*` would double-register stock beans). Recommended convention:
  `<vendor>.raplaplugin.*`.
- Is gated on `@ConditionalOnProperty(prefix = "rapla.plugins", name =
  "<id>.enabled", matchIfMissing = true)` so an operator can disable a deployed
  plugin via config without removing the jar.
- Compiles against `rapla-server` + `rapla-core` as `provided` Maven deps
  (resolved from the deployable at runtime). No separate `rapla-plugin-api`
  artifact — consistent with PRD 003/005 declining the `rapla-client-api` split;
  rapla has no frozen API surface to carve out (resolved 2026-05-23, former
  PRD 046 OQ3).

*REST endpoints from plugins:* a plugin `@RestController` is picked up by the
plugin's own `@ComponentScan`. AGENTS.md §15 still applies — class-level
`@RequestMapping("/api/<name>")` — but `ApiPrefixArchitectureTest` runs in the
rapla-app build and **cannot see plugin jars**. The fix is a runtime
`ApplicationListener` in rapla-server that, after refresh, logs a WARN for any
mapped handler outside `/api/` not on the allow-list. SpringDoc group
assignment (§15) is rapla-internal config; plugin endpoints simply won't appear
in the generated SPA client, which is correct (the SPA is built against stock
rapla).

*Boot ordering interaction with Phase 3:* Phase 3 relaxes the server-stack
boot condition (`@Conditional(DatasourceConfiguredCondition.class)`). Plugin
autoconfig is `after = RaplaServerAutoConfiguration` so it already orders
correctly; a plugin that injects rapla beans (`RaplaFacade`,
`PermissionController`) gets a fully-initialized context. Tier-3 boot test
verifies.

**Phase 6 — Example plugin + author guide.** Ship a minimal example plugin
(a single `/api/hello` endpoint) as a separate tiny Maven module *outside* the
reactor (like `rapla-angular/` is outside it) — or in a `docs/examples/`
sketch. Write `docs/plugins.md`: the Phase 5 contract, the build setup, the
trust model (document-only — "only install plugins you trust"), the
`rapla.plugins.<id>.enabled` switch, and the explicit "no API stability
guarantee across rapla minor versions — recompile and re-test against each
release" warning.

## Tests

- Phase 2: tier-3 `@SpringBootTest` (no profile) asserting the production
  defaults — `org.springframework.security` log level not `DEBUG`.
- Phase 3 ✅ — `DbDatasourceBootIntegrationTest` (`rapla-app`, `@Tag("e2e")` +
  `@Tag("db")`): `@SpringBootTest` configuring `rapla.db-datasources.rapladb`
  (embedded HSQLDB) plus a temp `testdefault.xml` file seed. Asserts the context
  boots, `ServerStorageSelector.get()` is the `DBOperator` (DB is the live
  store), and first boot creates the schema + imports the bootstrap admin from
  the file seed (OQ4 path). Revert-checked per AGENTS.md §1 — reverting the
  `rapladb` key turns `liveOperatorIsTheDbOperator` red.
- Per AGENTS.md §10 nevers: any new constructor arg on `FacadeImpl` /
  `FileOperator` updates `FacadeTestSupport` in the same change; DB-hitting
  tests get `@Tag("db")`.
- Phase 4: tier-3 / `test-deployment` skill — verify the packaged fat JAR's
  `META-INF/MANIFEST.MF` declares `Main-Class:
  org.springframework.boot.loader.launch.PropertiesLauncher` (not `JarLauncher`)
  and that `loader.properties` exists at the classpath root with
  `loader.path=lib/,plugins/`. Boot the packaged JAR with a stub jar in
  `./plugins/`; assert its `@Component` resolves in the running context (proves
  external `loader.path` discovery, end-to-end).
- Phase 5: tier-3 `@SpringBootTest` — a fixture plugin jar (built as a test
  resource) on the test classpath; assert its `@Bean` is present in the context
  and its `@RestController` answers via MockMvc. Mirrors `AutoConfigImportTest`.
- Phase 5: assert `rapla.plugins.<id>.enabled=false` removes the plugin's beans.
- Phase 5: tier-3 test that a plugin controller mapped outside `/api/` triggers
  the startup WARN (capture the log).
- Phase 5: tier-3 boot test — plugin autoconfig that `@Autowired`s `RaplaFacade`
  boots cleanly, proving ordering after `RaplaServerAutoConfiguration`.
- Per AGENTS.md §13: no mocks of rapla types — the example plugin's tests use
  the real Spring context.

## Open Questions

1. ~~Canonical DB datasource key.~~ **Resolved 2026-05-18.** The canonical key
   is `rapladb` — symmetric with the file side's `raplafile`
   (`rapla.file-datasources.raplafile` ↔ `rapla.db-datasources.rapladb`) and
   free of the YAML bracket-quoting a `/` forces. `getMainDbDatasource()`
   (currently hard-coded to the JNDI-era `"jdbc/rapladb"`) is changed to look up
   `"rapladb"`. No back-compat cost: the `rapla.db-datasources.*` map was never
   wired, so no existing config uses the old key; dhbwrapla's Dualis datasource
   is a separately-wired secondary, not this map.
2. ~~JDBC driver on the closed fat-JAR classpath.~~ **Resolved 2026-05-18.**
   HSQLDB bundled in `BOOT-INF/lib/` (embedded default backend); PostgreSQL +
   MariaDB shipped in an external replaceable `./lib/` directory, picked up via
   Spring Boot's `-Dloader.path=lib/` fat-JAR convention. SQL Server drivers
   (mssql-jdbc + jTDS) ship with dhbwrapla only (PRD 003); vanilla rapla ships
   neither — operator-supplied into `./lib/` if needed.
3. ~~File + DB both configured.~~ **Resolved 2026-05-18.** Not an ambiguity to
   fail on — it is the designed model. `ServerStorageSelector` *always*
   constructs the `FileOperator` (defaulting to `data/data.xml`) as the
   `ImportExportManager` **source** and the `DBOperator` as the destination.
   When a DB datasource is configured, `ServerStorageSelector.get()` returns
   the DB operator as the live store; the file operator is used only as the
   empty-DB bootstrap import seed (see OQ4). Decision: keep "DB is the live
   store when configured", no fail-fast. The existing startup log line
   `Using datasource <product>: <url>` already makes the live store explicit.
4. ~~Schema creation / migration.~~ **Resolved 2026-05-18 — confirmed in
   code.** `DBOperator.loadData()` → `upgradeDatabase()`:
   - **Empty database** (no `DYNAMIC_TYPE` table): `RaplaSQL.createOrUpdate-
     IfNecessary()` creates the full schema, then data is **bootstrap-imported
     from the XML source** (`data/data.xml`, via `ImportExportManager`). A fresh
     DB deployment therefore still needs a readable `data.xml` seed — the
     `FileOperator` falls back to `data/data.xml` and auto-creates a default
     bootstrap file (with the default admin) if absent.
   - **Current schema:** `createOrUpdateIfNecessary` migrates an out-of-date
     schema in place.
   - **Pre-1.8 schema** (integer id columns): NOT auto-migrated — `DBOperator`
     throws with an explicit "export data.xml with 1.8 rapla version and
     import" message. This is the one manual-migration path; `docs/deployment.md`
     (Phase 1) must document it.
5. ~~Distribution packaging.~~ **Resolved 2026-05-23.** Ship **just the fat
   JAR**. No tarball, no assembly descriptor, no launcher script — the
   bundled `loader.properties` (Phase 4) makes `./lib/` and `./plugins/`
   discoverable without operator flags. `docs/deployment.md` is the install
   guide.

   The earlier framing assumed an archive was needed "because of the external
   `./lib/` directory" — but `./lib/` is just an empty directory the operator
   creates (`mkdir -p /opt/rapla/{config,data,lib,plugins,logs}`). No
   pre-populated content needs to ship. The stale `rapla.distribution.xml`,
   `INSTALL.txt`, `README-Server.txt`, and Tanuki `service/` wrapper are
   deleted in Phase 4. `docs/deployment.md` (Phase 1) is the single source of
   truth for install.

   **Service install — Linux:** a plain systemd unit (`ExecStart=/usr/bin/java
   -jar /opt/rapla/rapla-X.jar`, `WorkingDirectory=/opt/rapla`). No wrapper
   needed. Documented in `docs/deployment.md`.

   **Service install — Windows:** Spring Boot has no native Windows-service
   support, and `sc.exe` cannot run `java -jar` directly (no SCM handshake — the
   service never reports "started"). A wrapper `.exe` is required. Recommended:
   **WinSW** (MIT, maintained, single `.exe` + short XML, any-bit Java, log
   rotation, graceful `<stoptimeout>`). Alternatives: NSSM (barely maintained),
   Apache Procrun (fiddly). The current rapla `service/` Tanuki Java Service
   Wrapper is **dropped** — its community edition is 32-bit-only (the source of
   the legacy "you may need 32-bit Java" advice). Pair WinSW's `<stoptimeout>`
   with `server.shutdown=graceful` so a service stop drains in-flight requests.
6. ~~`./plugins/` separate from `./lib/`, or one directory?~~ **Resolved
   2026-05-23 (former PRD 046 OQ1).** Keep separate. The split is by *role* —
   JDBC drivers are infrastructure, plugins are application code — and reads
   clearer to an operator scanning the install dir. Mechanically both are just
   classpath globs, but the visual separation is worth it. `loader.path=lib/,plugins/`
   covers both.
7. ~~Trust model for `./plugins/` jars.~~ **Resolved 2026-05-23 (former PRD 046
   OQ2) — document-only for v1.** A `./plugins/` jar runs with full server
   privileges; arbitrary code execution by design. `docs/plugins.md` (Phase 6)
   documents "only install plugins you trust." Signature verification (reusing
   the JNLP signing infrastructure / a configured trust anchor) would need a
   launch-script preflight step (the JVM can't verify once the class is on the
   `-cp` glob) — out of scope, follow-up if a real need surfaces.
8. ~~API stability guarantee for plugins.~~ **Resolved 2026-05-23 (former
   PRD 046 OQ3) — accept drift, document loudly.** rapla does not freeze a
   plugin-facing API. Plugin authors recompile and re-test against each rapla
   release; `docs/plugins.md` (Phase 6) carries the explicit warning. Matches
   PRD 003/005 declining the `rapla-client-api` split. The freezing-a-narrow-
   surface alternative (with `@since` annotations + architecture test) is a
   real maintenance commitment with no compelling case today; revisit if
   third-party plugin authors emerge.
9. ~~`@ComponentScan` collision guard.~~ **Resolved 2026-05-23 (former PRD 046
   OQ4) — document, don't enforce.** A plugin that scans `org.rapla.*` would
   double-register stock beans; failure is loud and immediate at boot.
   `docs/plugins.md` (Phase 6) documents the `<vendor>.raplaplugin.*` package
   convention. Active rejection (failing the boot if a plugin's scan
   base-packages intersect `org.rapla`) is out of scope for v1.
10. ~~Relationship to dhbwrapla.~~ **Resolved 2026-05-23 (former PRD 046 OQ5)
    — dhbwrapla migrates to plugin.** The 2026-05-23 deployment-strategy
    discussion accepted that dhbwrapla becomes a drop-in plugin jar
    (`rapla-plugin-dhbw-2.1.jar`, ~3 MB) replacing the PRD 003 full custom
    deployable. The migration is tracked as a separate follow-up PRD —
    covers the PRD 003 D2 pending work (Date→LocalDateTime drift, jcifs-ng
    port, scheduler API migration) plus the structural shift to a plugin jar.
    PRD 003 stays in `docs/prd/` as legacy reference until that migration
    lands.
