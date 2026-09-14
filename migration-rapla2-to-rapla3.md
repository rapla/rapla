# Migrating from Rapla 2 to Rapla 3 (alpha)

This guide is for **operators upgrading an existing Rapla 2 deployment** to the
Rapla 3 alpha — the Spring Boot rework of the server. It is written for the
person who runs the server, not for someone developing Rapla.

> Developing Rapla, not deploying it? See [`development.md`](docs/development.md).
> For the *internal* code rework (why the module shape, DI, and wire format
> changed) see [`architecture/what-changed-in-rapla-3.md`](docs/architecture/what-changed-in-rapla-3.md).
> Once you have migrated, [`deployment.md`](docs/deployment.md) is the ongoing
> install/configure/run reference.

Rapla 3 is still **alpha**. Do the migration against a **copy** of your Rapla 2
data first (a copy of the database or of `data.xml`), verify it, and keep your
Rapla 2 instance runnable until you have signed off on the new one. Rapla 3
runs from its own directory; it upgrades the store it is pointed at in place,
so never point it at the live Rapla 2 database while Rapla 2 is still running.

## What actually changes

Five things matter to an operator:

| Concern | Rapla 2 | Rapla 3 (alpha) | Section |
|---|---|---|---|
| Server runtime | Embedded **Jetty**, started by custom bootstrap code; deployed as a WAR / service-wrapper | **Spring Boot** fat JAR (embedded Tomcat 11), `java -jar`, Java 21 | [§1](#1-jetty--spring-boot) |
| Configuration | Jetty context file `contexts/rapla.xml` (JNDI entries) + `etc/jetty.xml` + system properties | `application.yml` (Spring Boot externalized config) | [§1](#1-jetty--spring-boot) |
| URLs and APIs | Webapp under a context path; JNLP with `codebase`; JAX-RS REST API | Context root `/`; JNLP resolves relative to its own URL; GraphQL API + OAuth2 | [§1](#urls-web-start-and-integrations) |
| Database | JDBC datasource declared in `contexts/rapla.xml` | `rapla.db-datasources.rapladb` key in `application.yml` | [§2](#2-database-configuration) |
| Permissions | `USER > GROUP > WORLD` **precedence** with soft-deny | **Purely additive** (`max` over all matching rows); soft-deny abolished | [§3](#3-the-new-permission-model-prd-090) |

**`DENIED` permissions no longer exist:** existing `DENIED` rows have no effect
from the first Rapla 3 start. The first-boot migration lists every resource
where this widens someone's access in the admin **Permission migration**
dialog; **Resolve** deletes that resource's `DENIED` rows and marks it as
reviewed (§3).

The Angular web frontend and the GraphQL API are new and need no migration
work, but anything that used the Rapla 2 REST API has to move to them (§1).

The migration path in one line: **make a backup of your Rapla 2 database or
`data.xml`, point Rapla 3 at the same store, start once, then verify.**

## 1. Jetty → Spring Boot

### What disappears

Rapla 2 ran an embedded Jetty brought up by Rapla's own bootstrap code, and was
typically deployed as a WAR in a servlet container or wrapped as an OS service.
Its configuration lived in the Jetty context file `contexts/rapla.xml` — JNDI
entries `raplafile`, `rapladatasource`, `raplaservices` and the resource
`jdbc/rapladb` — plus `etc/jetty.xml` and JVM system properties.

In Rapla 3 **all of that is gone**:

- No WAR. No external servlet container. No Jetty.
- The deliverable is a single self-contained Spring Boot fat JAR
  (`rapla-2.1-SNAPSHOT.jar`) with embedded Tomcat 11, all dependencies, the
  Angular SPA, and the signed Web Start webclient inside it. You never edit
  inside the JAR. (Rapla 2 builds carry the same version string
  `2.1-SNAPSHOT` — don't confuse the artifacts.)
- `contexts/rapla.xml`, `etc/jetty.xml` and the JNDI configuration no longer
  exist. All server configuration moves to `application.yml`.

### The new install layout

Rapla 3 needs **Java 21**. It resolves `config/`, `data/`, `lib/`, `plugins/`
and `logs/` **relative to the working directory**. Put the JAR at the top of a
dedicated directory and run it from there:

```
/opt/rapla/
  rapla-2.1-SNAPSHOT.jar      ← the artifact, never modified
  config/
    application.yml           ← your overrides (only the keys you change)
  data/
    data.xml                  ← datastore (XML mode) or first-boot seed (DB mode)
    patch/                    ← optional: views/documents applied at every start (PRD 112)
  lib/                        ← JDBC drivers for PostgreSQL / MariaDB / SQL Server
  plugins/                    ← optional drop-in plugin JARs
  logs/
```

Start it:

```sh
cd /opt/rapla
java -jar rapla-2.1-SNAPSHOT.jar
```

`lib/` and `plugins/` are on the classpath automatically — the JAR ships a
`loader.properties` with `loader.path=lib/,plugins/`. Don't add a
`-Dloader.path` flag.

Configuration is standard Spring Boot — you override the baked-in defaults from
`./config/application.yml` (later sources override earlier: baked-in defaults →
`./config/application.yml` → `./application.yml` → env vars → `--key=value`).
Your `config/application.yml` is a **thin override**: only the keys you change.

### Config key translation

| Rapla 2 | Rapla 3 (`application.yml`) |
|---|---|
| Jetty HTTP port (`etc/jetty.xml`) | `server.port` (default `8051` — the same as Rapla 2, so set another port if both run on one host) |
| JNDI `raplafile` | `rapla.file-datasources.raplafile` (default `data/data.xml`) |
| JNDI resource `jdbc/rapladb` | `rapla.db-datasources.rapladb.*` (see §2) |
| JNDI `rapladatasource` (selects file or DB) | no key: a configured `rapla.db-datasources.rapladb.url` selects the database |
| JNDI `raplaservices` (`org.rapla.plugin.notification=true,…`) | `rapla.services.<id>: true/false` — see below |
| Webapp context path (e.g. `/rapla`) | none — Rapla 3 runs at `/`; keep old published links alive with `rapla.legacy-context-path` |
| `logback.xml` | `logging.*` keys, `LOGGING_FILE_NAME`, or a full replacement via `LOGGING_CONFIG` ([`logging.md`](docs/logging.md)) — a custom Rapla 2 logback config is not picked up |
| Log level | `logging.level.org.rapla` (default `INFO`) |

**Services:** in Rapla 3 a service that is **not listed** under `rapla.services`
counts as **enabled**. Everything you switched off in Rapla 2's `raplaservices`
list must be set to `false` explicitly, e.g.

```yaml
rapla:
  services:
    org.rapla.plugin.notification: false
```

The plugin ids are listed in
[`architecture/extension-points.md`](docs/architecture/extension-points.md).

**Reverse proxy:** keep `server.forward-headers-strategy: native` (the shipped
default). Rapla then honours `X-Forwarded-*` only when the proxy connects from
loopback or a private address range (Tomcat's `RemoteIpValve` trust list). A
proxy with a public address is not trusted until you add it to that list. Do
**not** switch to `FRAMEWORK` — it trusts forwarded headers from any caller.
`rapla.oauth.public-base-url` is only needed for an external identity
provider; leave it empty behind a reverse proxy. Details:
[`deployment.md`](docs/deployment.md).

### URLs, Web Start and integrations

- **Context root is required.** Rapla 3 must run at `/` (no
  `server.servlet.context-path`). The Web Start JNLP no longer carries a
  `codebase` or `rapla.download.url`: the client resolves the server from the
  JNLP's own URL. Remove any old `codebase` / download-URL settings.
- **Web Start URL:** `/raplaclient.jnlp` (Rapla 2: `/rapla/raplaclient.jnlp`).
  The webclient JARs are signed; with a self-signed build the Java launcher
  shows an "unknown publisher" warning, so clients have to trust that
  certificate ([`signing.md`](docs/signing.md)).
- **Published calendar and iCal links** keep working: `/rapla/calendar` and
  `/rapla/ical` answer at the same paths. If Rapla 2 ran under a context path,
  set `rapla.legacy-context-path: /<old path>` so links with that prefix keep
  answering ([`legacy-urls.md`](docs/architecture/legacy-urls.md)).
- **The Rapla 2 REST API is gone** (`/rapla/events`, `/rapla/resources`, …, and
  query-parameter credentials). Integrations move to `/api/graphql` with an API
  key or a token from `POST /oauth2/token` (`grant_type=password`). The old
  `/api/auth/login` endpoint does not exist either. Mapping of the old calls:
  [`legacy-urls.md` § The Rapla 2.0 REST API is gone](docs/architecture/legacy-urls.md#the-rapla-20-rest-api-is-gone);
  authentication: [`authentication.md`](docs/authentication.md).

### Running as a service

The Rapla 2 WAR-in-a-container / service-wrapper model is replaced by a plain
`java -jar` under an OS service manager:

- **Linux**: a systemd unit with `WorkingDirectory=/opt/rapla` and
  `ExecStart=/usr/bin/java -jar /opt/rapla/rapla-2.1-SNAPSHOT.jar`. Full unit
  file (service account, permissions, graceful shutdown) in
  [`deployment.md` §Running as a service](docs/deployment.md#running-as-a-service).
- **Windows**: a wrapper such as WinSW (Spring Boot has no native
  Windows-service support).

## 2. Database configuration

Rapla 3 supports the same two backends as Rapla 2 — the **XML file store**
(default) and a **JDBC database** — but you select the database in
`application.yml` instead of `contexts/rapla.xml`.

### Staying on the XML file store

If your Rapla 2 instance used the XML store, you need to do nothing special:
make a backup of your `data.xml`, place it at `data/data.xml` (see §"Migrating
your data" below) and start. The default is:

```yaml
rapla:
  file-datasources:
    raplafile: data/data.xml
```

This is fine for small, single-instance deployments.

> If no file is found at that path, Rapla does **not** stop: it starts with a
> built-in default system (an `admin` account with an empty password) and only
> logs a warning. After the first start, check that your own data is loaded.

### Switching to / keeping a database

Configure a JDBC datasource under `rapla.db-datasources.rapladb`. When its `url`
is set it becomes the live store; the file datasource is then used only as the
**first-boot seed** of an empty database (see below).

```yaml
rapla:
  db-datasources:
    rapladb:
      url: jdbc:postgresql://db.example.com:5432/rapla
      username: rapla
      password: ${RAPLA_DB_PASSWORD}
```

The JDBC driver class is **auto-derived from the URL scheme** — no
`driver-class-name` needed.

| Database | Driver | Shipped? |
|---|---|---|
| HSQLDB | bundled in the JAR | yes — embedded, zero-setup |
| PostgreSQL | `org.postgresql:postgresql` | no — download it and place the jar in `./lib/` |
| MariaDB / MySQL | `org.mariadb.jdbc:mariadb-java-client` | no — download it and place the jar in `./lib/` |
| Microsoft SQL Server | `com.microsoft.sqlserver:mssql-jdbc` / jTDS | no — download it and place the jar in `./lib/` |

Neither the JAR nor the build ships these drivers; get them from Maven Central.
`./lib/` is picked up automatically (see §1). HSQLDB is bundled, so an embedded
database needs nothing extra:

```yaml
rapla:
  db-datasources:
    rapladb:
      url: jdbc:hsqldb:file:data/rapladb
      username: SA
      password: ""
```

### Migrating your data

The store format is compatible across the migration. No export or import is
needed. Rapla 3 upgrades your existing store in place:

1. **Stop Rapla 2 and make a backup** of the database (DB mode) or of
   `data.xml` (XML mode). Rapla 3 updates the store in place, so the backup is
   your way back to Rapla 2.
2. **Point Rapla 3 at the same store**: the same database in
   `rapla.db-datasources.rapladb`, or the same `data.xml` at `data/data.xml`.
   Rapla 2 must not write to that store at the same time.
3. **Start Rapla 3 once.** In DB mode the schema is updated in place on first
   start; in XML mode the file is read directly. On this first start Rapla also
   migrates keys (§2 *Generic keys*) and permissions (§3); if the key
   migration fails, the server does not start — check the log.

> **Export/import is only needed in special cases:** a pre-1.8 Rapla database
> schema (Rapla stops with an explicit message and asks for a `data.xml` export
> from Rapla 1.8), switching from the XML store to a database, or changing the
> database product. Then export `data.xml` from the old instance, place it at
> `data/data.xml`, and start Rapla 3 against an **empty** database: it creates
> the schema and imports the XML file on first start. After that the database is
> the source of truth and `data.xml` is no longer read.
>
> If only **some** tables of a database still have the old integer id columns,
> Rapla drops **all** tables and re-imports from `data/data.xml`. Another reason
> to start from a backup.

> **In-place upgrade of a 1.8+ schema: the `CHANGES` history needs no manual step.**
> Rapla 3 writes `CHANGES.CHANGED_AT` with the same wall-clock convention Rapla 2
> used (and the same one every other timestamp column uses), so the history rows
> your Rapla 2 instance left behind stay correctly ordered against Rapla 3's own
> writes. Builds before [PRD 108](docs/prd/108-changes-history-timestamp-convention.md)
> stored that one column shifted by the local UTC offset; on those, legacy rows
> from the last one to two hours before the copy were replayed as if they lay in
> the future and could revert freshly migrated entities in the cache. If you are
> on such a build the workaround was `TRUNCATE TABLE changes` before the first
> start — on current builds, don't.

### Generic keys (`c1`, `reservation10`, …)

Rapla 2 installations often carry auto-generated keys — categories `c1`, `c2`, …
and event types `reservation10`, `reservation12`, …. Rapla 3 exposes every key
verbatim in the GraphQL schema, so you will want speaking keys: an event type
`reservation12` becomes `reservation12Classification`, and a category becomes an
enum named after its path below the root, joined with `_` (`c5` under `c1`
becomes `c1_c5Enum`; categories under `user-groups` get no enum).

**Migrate first, rename afterwards in Rapla 3.** Two reasons:

1. **Key shape is migrated automatically.** On the first Rapla 3 boot,
   [PRD 058](docs/prd/058-graphql-key-spec-migration.md) renames every key that is
   not GraphQL-safe (`ü` → `ue`, `-` → `_`, reserved suffix → trailing `_`) and
   rewrites the references. No manual step.
2. **Renaming is safe in Rapla 3, unsafe in Rapla 2.** Type definitions and
   calendar preferences (including published export calendars) store categories
   and types *by key path* (see
   [dynamic-types § Keys are persisted as references](docs/architecture/dynamic-types.md#keys-are-persisted-as-references--renaming-has-blast-radius)).
   Rapla 3 re-stores every referencing definition and preference in the same
   transaction; Rapla 2 rewrites only the edited entity, and the stale paths
   surface on its next restart — category filters silently stop filtering, a
   renamed type key aborts the boot.

So: don't touch keys in the Rapla 2 admin client once you plan the migration;
after the switch, rename them in the Rapla 3 admin client at your leisure. If an
integration refers to keys, rename them after that integration has moved to the
GraphQL API (the Rapla 2 REST API is gone in any case, §1).

## 3. The new permission model ([PRD 090](docs/prd/090-additive-permission-resolution.md))

This is the one **behaviour** change in the migration that can affect who sees
what. Read it even if everything else in your deployment is unchanged.

> Reference: [PRD 090](docs/prd/090-additive-permission-resolution.md),
> [ADR 0003](docs/decisions/0003-permissions-are-grant-only.md),
> [`architecture/permissions.md`](docs/architecture/permissions.md).

### What changed

Rapla 2 resolved permissions by **precedence**: `USER > GROUP > WORLD`. A more
specific row overrode a less specific one — including **downward**. That made it
possible to express a *"soft deny"*: grant a group access, then add a
**lower** (or `DENIED`) row for a specific user to subtract it back. Precedence
honoured that subtraction.

Rapla 3 resolves permissions **purely additively**:

- A user's effective access is the **highest** level granted by **any** matching
  row — user, group, or world — unioned together (`max`).
- **No precedence. No subtraction.** The `DENIED` level is the floor and is
  inert under `max`. A user-row set *below* their group's level no longer caps
  anything — it is dominated and ignored.

In short: **you can only ever grant access, never take it away with a
lower/denied row.** Both forms of the old "soft deny" (an explicit `DENIED` row,
and a user-below-group row) stop having any effect.

### Why this is usually a small change

The change only matters where a store actually *relied* on a soft-deny to hold
something back. In practice that is rare, because the common cases (owner/admin
bypass, expired time-windows, a user already covered by another group) never
depended on it.

But it is **not guaranteed zero** for your store. Where a soft-deny *was*
load-bearing, the affected user **gains** their group's (or world's) level at
the flip — from the first start on, not only after you review. The migration is
built to surface exactly those cases.

### What the migration does

The flip is **immediate** — Rapla 3 resolves additively from first boot. To let
you review any real escalations, a **one-shot migration runs at first boot**:

1. It compares the old precedence calculation against the new additive one and
   finds the **true escalation set**: resources where a **user** now gets *more*
   than precedence would have given them (a deny on a group shows up as findings
   for the affected users). Owners, admins, expired time windows and users
   covered by another group are filtered out automatically. The server logs one
   INFO line with the count: `PRD 090 — additive permission flip: N resource(s) …`.
2. It freezes the affected **resource ids** into a worklist (a system
   preference). Nothing sensitive is stored — names, levels, and the
   explanation are recomputed live for display.
3. The **Permission migration** dialog lists exactly those resources. It is an
   entry in the SPA toolbar's user menu, shown only to **global admins** and only
   while the list is **not empty** — no menu entry means nothing to migrate.
   Each row shows the resource and the users who gain access:

   | Resource | Who gains access | Resolved |
   |---|---|---|
   | (room/resource) | user *X*: READ → REQUEST (was denied) | ☐ |
   | (room/resource) | user *Y*: — → READ; user *Z*: READ → EDIT | ☐ |

4. You **resolve** each resource in one of two ways:
   - **Resolve** it (check "Resolved") — you accept the escalation as fine, or
     you have restructured the groups. This **deletes all `DENIED` rows of that
     resource** and marks it as reviewed, which removes it from the list. User
     rows below their group's level are not deleted; they stay, without effect.
   - **Edit the permissions** to remove the dependence on the soft-deny — the
     list recomputes and the entry **drops off automatically** once it is clean.
     Permissions are edited in the **Swing client**; the SPA has no permission
     editor.

The same data is available to scripts for admins:
`GET /api/admin/permission-migration/findings` and
`POST /api/admin/permission-migration/{resourceId}/resolve`.

The dialog and endpoints are **admin-only and transient**: invisible to normal
users, and they will be removed in a later release once deployments have
migrated.

### What you should do

1. Do the first start **before users can reach the server** (e.g. on a copy or
   with the port closed), because escalations apply from the first start.
2. Check the `PRD 090 — additive permission flip` INFO line in the log.
3. Log in to the SPA as a global admin and open **Permission migration** from
   the user menu. No entry = nothing to migrate.
4. For each listed resource, look at "who gains access". If the escalation is
   acceptable, **resolve** it. If it is not, **edit the permissions** in the
   Swing client to remove the dependence on the old soft-deny, then let the
   entry recompute clean.
5. When the list is empty, the migration is complete. Then open the server to
   users.

> The Swing permission editor no longer offers `DENIED` on new rows and shows
> any existing `DENIED` as *"(deprecated)"*. A user-below-group row is still two
> valid rows but has no effect; the editor does not show the effective access
> yet.

## Migration checklist

- [ ] Install Java 21. Build / obtain the Rapla 3 fat JAR (`rapla-2.1-SNAPSHOT.jar`).
- [ ] Create the install layout (`config/`, `data/`, `lib/`, `plugins/`, `logs/`).
- [ ] Translate `contexts/rapla.xml` / `etc/jetty.xml` / sysprop settings into
      `config/application.yml` (§1 key table); set every service that was off
      in `raplaservices` to `false` under `rapla.services`.
- [ ] Choose a free `server.port` if Rapla 2 keeps running on the same host (both default to 8051).
- [ ] Run Rapla 3 at the context root; remove old `codebase` / download-URL
      settings; set `rapla.legacy-context-path` if Rapla 2 ran under a context path.
- [ ] Behind a reverse proxy with a public address: add it to the trusted proxies (§1).
- [ ] Don't rename keys in Rapla 2 from now on; speaking keys come after the switch (§2 *Generic keys*).
- [ ] Stop Rapla 2 and make a backup of the database or `data.xml`.
- [ ] XML store: place `data.xml` at `data/data.xml`. Database: configure
      `rapla.db-datasources.rapladb` for the same database and drop the
      downloaded driver in `./lib/`.
- [ ] Start once, with no users able to reach the server; confirm the schema
      update, the key migration and the permission-flip log line, and that your
      own data is loaded.
- [ ] Log in with your existing Rapla 2 admin account. Only a fresh install
      without data creates `admin` with an empty password; set it before the
      server is reachable. With an external identity provider, keep a working
      local admin account.
- [ ] Open **Permission migration** in the SPA user menu (if shown); review and
      resolve every listed resource (§3).
- [ ] Verify a few real users see exactly what they should (spot-check the most
      permission-sensitive resources).
- [ ] Check the Web Start client via `/raplaclient.jnlp` (certificate trust)
      and a few published calendar / iCal links.
- [ ] Move integrations from the Rapla 2 REST API to GraphQL with API keys or
      the password grant (§1).
- [ ] Transfer custom logging settings (§1 key table).
- [ ] Wire up the OS service (systemd / WinSW) per [`deployment.md`](docs/deployment.md).
- [ ] Keep the Rapla 2 instance and the backup available until you have signed off.

## See also

- [`deployment.md`](docs/deployment.md) — ongoing install/configure/run reference.
- [`authentication.md`](docs/authentication.md) — OAuth2, external IdPs, API keys.
- [`architecture/legacy-urls.md`](docs/architecture/legacy-urls.md) — URL changes, `rapla.legacy-context-path`, replacements for the Rapla 2 REST API.
- [`architecture/what-changed-in-rapla-3.md`](docs/architecture/what-changed-in-rapla-3.md)
  — the internal code rework (developer-facing).
- [PRD 045](docs/prd/045-end-user-deployment-and-db-config.md) — end-user deployment
  + database configuration model.
- [PRD 090](docs/prd/090-additive-permission-resolution.md) — additive permission
  resolution + soft-deny migration.
