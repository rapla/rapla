# PRD 045: End-User Deployment Model & Database Configuration

**Status:** in-progress — Phases 1 + 2 + 3 landed 2026-05-18; only OQ5 (distribution packaging) remains, deferred to a follow-up
**Date:** 2026-05-18

## Goal

Define how an end user (a site operator, not a rapla developer) installs,
configures, and runs the Spring Boot rapla server — and close the gap that
makes a database-backed deployment currently impossible via configuration.

Three concerns, deliberately separated because they are at different stages:

1. **Externalized configuration** — how the operator overrides `application.yml`
   without rebuilding the fat JAR. *Mostly works today; needs documentation.*
2. **Production-sane bundled config** — the `application.yml` baked into the JAR
   is currently a dev config. *Concrete fix, planned below.*
3. **Database configuration** — selecting MariaDB / PostgreSQL / HSQLDB instead
   of the XML file store. *Half-wired in code; design-first, no implementation
   in this PRD revision.*

This PRD is the vanilla end-user story. PRD 003 covers *custom* deployments
(dhbwrapla and similar downstream `@SpringBootApplication`s) and is out of scope
here except where the DB-datasource design must stay compatible with it.

## Background — current state

`mvn package` on `rapla-app` produces one artifact: `rapla-2.1-SNAPSHOT.jar`,
a Spring Boot fat JAR (embedded Tomcat 11, all deps in `BOOT-INF/lib/`, the
Angular SPA under `static/app/`, signed JNLP webclient jars served from
`BOOT-INF/lib/`). It is self-contained and immutable — operators must never
edit inside it.

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

There is also a classpath consequence, entangled with **PRD 018**. PRD 018
documents that the Spring Boot 4 nested-jar classloader is defective — running
the fat JAR directly with `java -jar` collapses the HTTP layer; the production
launch mode is **extract-and-run on a flat classpath**
(`java -cp BOOT-INF/classes:BOOT-INF/lib/*`). That means JDBC drivers are
handled as follows (decided 2026-05-18 — resolves OQ2):

- **HSQLDB** — bundled as a normal Maven `runtime` dependency, lands in
  `BOOT-INF/lib/`. It is the embedded, zero-external-server default backend.
- **PostgreSQL + MariaDB** — shipped in an external `./lib/` directory beside
  the deployable, *not* inside the JAR. Operators replace a driver (newer
  version, alternate build) by swapping the file.
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
- **Mechanism** is subordinate to PRD 018's launch decision. Under
  extract-and-run, `lib/*` is simply added to the flat `-cp` glob — replaceable,
  no rebuild, no launcher config. A `PropertiesLauncher` + `loader.path=lib/`
  setup would be the equivalent *only* if rapla ran as a real `java -jar` fat
  JAR; PRD 018 says it currently cannot, and switching to `PropertiesLauncher`
  would not fix PRD 018 (the app bulk stays in nested `BOOT-INF/lib/`). Either
  way the operator-visible outcome is identical: a replaceable `lib/` dir.

## Scope

In scope:
- The end-user deployment model and its documentation.
- Hardening the bundled `application.yml` (concrete, planned).
- The **design** of database configuration — config shape, wiring points,
  driver-on-classpath strategy, boot-condition change. No DB implementation
  lands in this PRD revision; it is split out once the open questions resolve.

Out of scope (deferred or owned elsewhere):
- Distribution packaging (the `.tar.gz`/`.zip`, service install). The current
  `rapla.distribution.xml`, `INSTALL.txt`, `README-Server.txt` and the `service/`
  Tanuki wrapper are all pre-Spring-Boot (Jetty / WAR / JNDI). A rewrite is
  needed but deferred — see Open Question 5.
- Custom downstream deployments — PRD 003.
- External IdP setup — PRD 036.
- Multi-pod / shared-store coordination — `docs/architecture/locking.md`,
  PRD 040.

## Plan

**Phase 1 — Document the deployment model. ✅ Done 2026-05-18.**
`docs/deployment.md` written: the fat-JAR model, the `config/` + `data/` + `lib/`
layout, the externalized-config precedence order, profiles, must-set-for-
production keys, the full database section (file vs db backend, driver matrix,
the empty-DB bootstrap + pre-1.8 manual-migration caveat from OQ4), the
extract-and-run launch caveat (PRD 018), and systemd / WinSW service recipes.
Supersedes the stale `INSTALL.txt` / `README-Server.txt`.

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
  external `./lib/`; SQL Server operator-supplied — those are PRD 018 / OQ5
  packaging work, not done here.
- Verified by `DbDatasourceBootIntegrationTest` (see Tests).

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
   MariaDB shipped in an external replaceable `./lib/` directory. SQL Server
   drivers (mssql-jdbc + jTDS) ship with dhbwrapla only (PRD 003); vanilla rapla
   ships neither — operator-supplied into `./lib/` if needed. The classpath
   mechanism follows PRD 018's launch decision — see §3 / Phase 3.
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
5. **Distribution packaging (deferred).** Replace the stale assembly with a
   slim `.tar.gz`/`.zip` (JAR + `config/application.yml.sample` + `data/` +
   `./lib/` JDBC drivers + start script + service install) — or ship the bare
   JAR + `docs/deployment.md` only? The external `./lib/` driver directory (OQ2)
   means *some* archive layout is now required regardless. Decide in a later PRD
   revision or a follow-up PRD.

   **Service install — Linux:** a plain systemd unit (`ExecStart=java -jar
   rapla-...jar`, `WorkingDirectory=` the install dir). No wrapper needed.

   **Service install — Windows:** Spring Boot has no native Windows-service
   support, and `sc.exe` cannot run `java -jar` directly (no SCM handshake — the
   service never reports "started"). A wrapper `.exe` is required. Recommended:
   **WinSW** (MIT, maintained, single `.exe` + short XML, any-bit Java, log
   rotation, graceful `<stoptimeout>`). Alternatives: NSSM (barely maintained),
   Apache Procrun (fiddly). The current rapla `service/` Tanuki Java Service
   Wrapper is **dropped** — its community edition is 32-bit-only (the source of
   the legacy "you may need 32-bit Java" advice). Pair WinSW's `<stoptimeout>`
   with `server.shutdown=graceful` so a service stop drains in-flight requests.
