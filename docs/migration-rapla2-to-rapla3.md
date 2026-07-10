# Migrating from Rapla 2 to Rapla 3 (alpha)

This guide is for **operators upgrading an existing Rapla 2 deployment** to the
Rapla 3 alpha — the Spring Boot rework of the server. It is written for the
person who runs the server, not for someone developing Rapla.

> Developing Rapla, not deploying it? See [`development.md`](development.md).
> For the *internal* code rework (why the module shape, DI, and wire format
> changed) see [`architecture/migration-from-master.md`](architecture/migration-from-master.md).
> Once you have migrated, [`deployment.md`](deployment.md) is the ongoing
> install/configure/run reference.

Rapla 3 is still **alpha**. Do the migration against a **copy** of your Rapla 2
data first, verify it, and keep your Rapla 2 instance runnable until you have
signed off on the new one. Nothing here deletes your Rapla 2 install — Rapla 3
runs from its own directory against its own (migrated) datastore.

## What actually changes

Three things matter to an operator. Everything else (Angular SPA, GraphQL,
OAuth) is additive and does not require migration work — it is just there.

| Concern | Rapla 2 | Rapla 3 (alpha) | Section |
|---|---|---|---|
| Server runtime | Embedded **Jetty**, started by custom bootstrap code; deployed as a WAR / service-wrapper | **Spring Boot** fat JAR (embedded Tomcat 11), `java -jar` | [§1](#1-jetty--spring-boot) |
| Configuration | `raplaserver.xml` / container XML + system properties | `application.yml` (Spring Boot externalized config) | [§1](#1-jetty--spring-boot) |
| Database | JDBC datasource declared in the container XML | `rapla.db-datasources.rapladb` key in `application.yml` | [§2](#2-database-configuration) |
| Permissions | `USER > GROUP > WORLD` **precedence** with soft-deny | **Purely additive** (`max` over all matching rows); soft-deny abolished | [§3](#3-the-new-permission-model-prd-090) |

The migration path in one line: **export your Rapla 2 store to `data.xml`, point
a fresh Rapla 3 install at it, start once to import, then verify.**

## 1. Jetty → Spring Boot

### What disappears

Rapla 2 ran an embedded Jetty brought up by Rapla's own bootstrap code, and was
typically deployed as a WAR in a servlet container or wrapped as an OS service.
Its configuration lived in container XML (`raplaserver.xml` and friends) plus
JVM system properties.

In Rapla 3 **all of that is gone**:

- No WAR. No external servlet container. No Jetty.
- The deliverable is a single self-contained Spring Boot fat JAR
  (`rapla-2.1-SNAPSHOT.jar`) with embedded Tomcat 11, all dependencies, the
  Angular SPA, and the signed Web Start webclient inside it. You never edit
  inside the JAR.
- `raplaserver.xml` and container-XML configuration no longer exist. All
  server configuration moves to `application.yml`.

### The new install layout

Rapla 3 resolves `config/`, `data/`, `lib/`, and `logs/` **relative to the
working directory**. Put the JAR at the top of a dedicated directory and run it
from there:

```
/opt/rapla/
  rapla-2.1-SNAPSHOT.jar      ← the artifact, never modified
  config/
    application.yml           ← your overrides (only the keys you change)
  data/
    data.xml                  ← datastore (XML mode) or first-boot seed (DB mode)
  lib/                        ← extra JDBC drivers, if using PostgreSQL/MariaDB
  logs/
```

Start it:

```sh
cd /opt/rapla
java -jar rapla-2.1-SNAPSHOT.jar
```

Configuration is standard Spring Boot — you override the baked-in defaults from
`./config/application.yml` (later sources override earlier: baked-in defaults →
`./config/application.yml` → `./application.yml` → env vars → `--key=value`).
Your `config/application.yml` is a **thin override**: only the keys you change.

### Config key translation

The settings you used to set in container XML / system properties now live in
`application.yml`. The common ones:

| Rapla 2 (XML / sysprop) | Rapla 3 (`application.yml`) |
|---|---|
| Jetty HTTP port | `server.port` (default `8051`) |
| XML store path | `rapla.file-datasources.raplafile` (default `data/data.xml`) |
| JDBC datasource block | `rapla.db-datasources.rapladb.*` (see §2) |
| Log level | `logging.level.org.rapla` (default `INFO`) |
| Public/external URL | `rapla.oauth.public-base-url` (leave empty behind a reverse proxy) |

Behind a reverse proxy keep `server.forward-headers-strategy: FRAMEWORK` (the
default) so Rapla derives correct absolute URLs from `X-Forwarded-*`. The full
key reference and the production checklist are in
[`deployment.md`](deployment.md); authentication (OAuth2, external IdPs, API
keys) is in [`authentication.md`](authentication.md).

### Running as a service

The Rapla 2 WAR-in-a-container / service-wrapper model is replaced by a plain
`java -jar` under an OS service manager:

- **Linux**: a systemd unit with `WorkingDirectory=/opt/rapla` and
  `ExecStart=/usr/bin/java -jar /opt/rapla/rapla-2.1-SNAPSHOT.jar`. Full unit
  file (service account, permissions, graceful shutdown) in
  [`deployment.md` §Running as a service](deployment.md#running-as-a-service).
- **Windows**: a wrapper such as WinSW (Spring Boot has no native
  Windows-service support).

## 2. Database configuration

Rapla 3 supports the same two backends as Rapla 2 — the **XML file store**
(default) and a **JDBC database** — but you select the database in
`application.yml` instead of the container XML.

### Staying on the XML file store

If your Rapla 2 instance used the XML store, you need to do nothing special:
drop your migrated `data/data.xml` in place (see §"Migrating your data" below)
and start. The default is:

```yaml
rapla:
  file-datasources:
    raplafile: data/data.xml
```

This is fine for small, single-instance deployments.

### Switching to / keeping a database

Configure a JDBC datasource under `rapla.db-datasources.rapladb`. When that key
is present it becomes the live store; the file datasource is then used only as
the **first-boot seed** (see below).

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
| PostgreSQL | `org.postgresql:postgresql` | place the jar in `./lib/` |
| MariaDB / MySQL | `org.mariadb.jdbc:mariadb-java-client` | place the jar in `./lib/` |
| Microsoft SQL Server | `com.microsoft.sqlserver:mssql-jdbc` / jTDS | place the jar in `./lib/` |

For PostgreSQL / MariaDB / SQL Server, drop the driver jar into `./lib/` and add
`-Dloader.path=lib/` to the `java -jar` invocation (Spring Boot's fat-JAR
convention for external classpath entries). HSQLDB is bundled, so an embedded
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

The store format is compatible across the migration; the import path is the same
one Rapla has always used:

1. **Export from Rapla 2** to `data.xml` (admin export, or the on-disk
   `data.xml` if you ran the XML store).
2. **Drop it at `data/data.xml`** in your Rapla 3 install directory.
3. **First boot against an empty database** (DB mode): on first connect Rapla 3
   creates the full schema and **imports the initial data from the XML file
   source**. So a fresh database deployment still needs a readable
   `data/data.xml` seed on first start.

> A **pre-1.8 Rapla database schema is not auto-migrated** — Rapla stops with an
> explicit message. Migrate by exporting `data.xml` from the old instance and
> importing it (steps above). A 1.8+ schema is migrated in place on the next
> start.

After the first successful boot in DB mode, the database is the source of truth;
the `data.xml` seed is no longer read.

## 3. The new permission model ([PRD 090](prd/090-additive-permission-resolution.md))

This is the one **behaviour** change in the migration that can affect who sees
what. Read it even if everything else in your deployment is unchanged.

> Reference: [PRD 090](prd/090-additive-permission-resolution.md),
> [ADR 0003](decisions/0003-permissions-are-grant-only.md),
> [`architecture/permissions.md`](architecture/permissions.md).

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

### Why this is (almost certainly) a non-event for you

The change only matters where a store actually *relied* on a soft-deny to hold
something back. An audit of the dhbw production store — a large, real
deployment — found the real impact was **2 entities (1 active, 0 events)**.
Soft-deny-as-subtraction was vanishingly rare in practice, because the common
cases (owner/admin bypass, expired time-windows, a user already covered by
another group) never depended on it.

But it is **not guaranteed zero** for your store. Where a soft-deny *was*
load-bearing, the affected user/group **gains** their group's (or world's) level
at the flip. The migration is built to surface exactly those cases so you can
review them.

### What the migration does

The flip is **immediate** — Rapla 3 resolves additively from first boot. To let
you review any real escalations, a **one-shot migration runs at first boot**:

1. It compares the old precedence calculation against the new additive one and
   finds the **true escalation set** — `(allocatable, principal)` pairs where a
   user/group now gets *more* than precedence would have given them. The
   owner/admin/expired/multi-group cases are filtered out automatically (in the
   dhbw audit this took ~2,778 structural soft-denies down to **1** real
   escalation).
2. It freezes the affected **allocatable ids** into a worklist (a system
   preference). Nothing sensitive is stored — names, levels, and the
   explanation are recomputed live for display.
3. An **admin migration page in the SPA** lists exactly those allocatables, one
   row each, with a plain-language description of who gained access:

   | Allocatable | Who gains access | Resolved |
   |---|---|---|
   | (room/resource) | user *X*: READ → REQUEST | ☐ |
   | (room/resource) | group *Y*: — → READ; user *Z*: READ → EDIT | ☐ |

4. You **resolve** each allocatable in one of two ways:
   - **Acknowledge** it (check "Resolved") — you accept the escalation as fine,
     or you have restructured the groups. This just clears it from the list.
   - **Edit the permissions** to remove the now-inert soft-deny rows — the page
     recomputes and the entry **drops off automatically** once it is clean.

The migration page is **admin-only and transient**: it is invisible to normal
API/SPA users and will be removed in a later release once deployments have
migrated.

### What you should do

1. After first boot of Rapla 3, log in as an admin and open the **permissions
   migration page**.
2. For each listed allocatable, look at "who gains access". If the escalation is
   acceptable, **acknowledge** it. If it is not, **edit the permissions** to
   remove the dependence on the old soft-deny (the lower/`DENIED` row), then let
   the entry recompute clean.
3. When the list is empty, the migration is complete.

> The permission editor no longer offers `DENIED` on new rows (it renders any
> existing `DENIED` as *"(deprecated)"*). A user-below-group row is still two
> valid rows but is inert. There is no hard block — the editor instead shows the
> *effective* additive access so a no-op soft-deny is visible as a no-op.

## Migration checklist

- [ ] Build / obtain the Rapla 3 fat JAR (`rapla-2.1-SNAPSHOT.jar`).
- [ ] Create the install layout (`config/`, `data/`, `lib/`, `logs/`).
- [ ] Translate your `raplaserver.xml` / sysprop settings into
      `config/application.yml` (§1 key table).
- [ ] Export Rapla 2 data to `data.xml`; place at `data/data.xml`.
- [ ] If using a database: configure `rapla.db-datasources.rapladb`, drop the
      driver in `./lib/`, point at an **empty** database for first boot.
- [ ] Start once; confirm the schema/data import and that you can log in.
- [ ] Set the `admin` password (the seed ships with an empty one).
- [ ] Open the **permissions migration page**; review and resolve every
      escalated allocatable (§3).
- [ ] Verify a few real users see exactly what they should (spot-check the most
      permission-sensitive resources).
- [ ] Wire up the OS service (systemd / WinSW) per [`deployment.md`](deployment.md).
- [ ] Keep the Rapla 2 instance available until you have signed off.

## See also

- [`deployment.md`](deployment.md) — ongoing install/configure/run reference.
- [`authentication.md`](authentication.md) — OAuth2, external IdPs, API keys.
- [`architecture/migration-from-master.md`](architecture/migration-from-master.md)
  — the internal code rework (developer-facing).
- [PRD 045](prd/045-end-user-deployment-and-db-config.md) — end-user deployment
  + database configuration model.
- [PRD 090](prd/090-additive-permission-resolution.md) — additive permission
  resolution + soft-deny migration.
</content>
</invoke>
