# Deploying Rapla

How to install, configure, and run the Rapla server. This replaces the
pre-Spring-Boot `INSTALL.txt` / `README-Server.txt` (Jetty / WAR / service
wrapper), which no longer apply.

> Developing Rapla, not deploying it? See [`development.md`](development.md) —
> the dev server runs straight from the reactor with `mvn spring-boot:run` and
> needs none of this.

## What you deploy

`mvn package` (module `rapla-app`) produces a single artifact:

```
rapla-app/target/rapla.jar
```

A self-contained Spring Boot fat JAR — embedded Tomcat, all dependencies, the
Angular SPA, and the signed Java Web Start webclient. It is **immutable**:
never edit anything inside it. Everything site-specific lives *outside* the JAR
(see Configuration).

**Requirements:** Java 21 (the JAR targets Java 17 bytecode but runs on 21).
A JDBC database is optional — Rapla ships with an embedded store.

## Building the deployable JAR

Always `clean` before `package` (stale `target/` shadows the assembly inputs
and ships a broken artifact), and skip tests on packaging builds:

```sh
mvn -pl rapla-app -am clean package -DskipTests -Psign-pkcs11
```

The Web Start client requires rapla at the context root (no
`server.servlet.context-path`); the JNLP carries no `codebase` and no
`rapla.download.url` — it is resolved relative to its own URL (audit fix S3,
2026-09-14).

The bundled Java Web Start webclient jars **must be signed** or the JNLP client
won't launch. Two signing profiles:

| Profile | Signs with | For |
|---|---|---|
| `-Psign-pkcs11` | a hardware token (YubiKey, PKCS#11) | the maintainer's release builds |
| `-Psign-jks`    | a self-signed keystore (`raplaselfsigned.ks`) | other developers / CI test builds |

Full signing setup (keystore config, PKCS#11 cfg, troubleshooting) is in
[`signing.md`](signing.md).

> **WSL2 + YubiKey gotcha.** The `sign-pkcs11` build fails at
> `sign-webclient-pkcs11` with `ProviderException: slotListIndex is 0 but token
> only has 0 slots` when the YubiKey isn't forwarded into WSL — the attach does
> not survive a `wsl --shutdown`. Fix from inside WSL (`usbipd.exe` is on the
> PATH): `usbipd.exe list` to find the Yubico busid, then
> `usbipd.exe attach --wsl --busid <busid>`; confirm with
> `pkcs11-tool --list-slots` before rebuilding. The batch signer prints an
> 8-second countdown, then signs all webclient jars on a single key touch.

## Install layout

Rapla resolves `data/`, `config/`, and `lib/` **relative to the working
directory**, so put the JAR at the top of a dedicated directory and start it
from there:

```
/opt/rapla/
  rapla.jar      ← the artifact, never modified
  config/
    application.yml           ← your overrides (only the keys you change)
  data/
    data.xml                  ← XML datastore (default backend)
  lib/                        ← extra JDBC drivers, if using PostgreSQL/MariaDB
  logs/
```

## Quick start

```sh
cd /opt/rapla
java -jar rapla.jar
```

Browse to `http://localhost:8051`. The bundled store starts with one admin
account — username `admin`, empty password. **Set a password immediately.**

## Configuration

Rapla is a standard Spring Boot application — you **override `application.yml`
without touching the JAR**. Spring Boot merges configuration from several
locations, *later overrides earlier*:

1. `application.yml` baked into the JAR — the production-sane defaults
2. `./config/application.yml` — next to the JAR ← **put your overrides here**
3. `./application.yml`
4. Environment variables (`SERVER_PORT`, `RAPLA_OAUTH_PUBLIC_BASE_URL`, …)
5. Command-line args (`--server.port=9000`)

Your `config/application.yml` is a **thin override** — only the keys you
change. Profiles work too: `config/application-prod.yml` activated with
`SPRING_PROFILES_ACTIVE=prod`.

### Keys to review for production

| Key | Default | Notes |
|---|---|---|
| `server.port` | `8051` | HTTP port |
| `rapla.oauth.public-base-url` | empty (request-derived) | Leave empty behind a reverse proxy that sets `X-Forwarded-*`; set explicitly only for an external IdP. |
| `rapla.file-datasources.raplafile` | `data/data.xml` | XML store path, or use a database (below) |
| `logging.level.org.rapla` | `INFO` | See [`logging.md`](logging.md) for the full picture (file locations, access log, profile-based stdout-only, full-replace, JUL bridge). |

Behind a reverse proxy / load balancer, keep `server.forward-headers-strategy:
native` (the shipped default) so Rapla derives correct absolute URLs from
`X-Forwarded-{Proto,Host,Port}` **only** when they come from an RFC1918-internal
proxy (Tomcat's `RemoteIpValve` trust list). Do **not** switch to `FRAMEWORK`:
that trusts `X-Forwarded-*` from any caller, so a client reaching the server
directly can forge `X-Forwarded-Host` to hijack the OAuth redirect-URI
same-origin check. With `native` in place the OAuth SPA callback is
accepted automatically for your real hostname (`rapla.oauth.allow-same-origin-
redirects` is on by default) — no per-deployment redirect URI to register.
If your proxy connects from an address outside loopback and the private ranges,
list it in `server.tomcat.remoteip.internal-proxies` (a regular expression of
trusted proxy IPs); the value replaces the built-in default, so keep the ranges
you still need in it.

Authentication (OAuth2, external IdPs, API keys) is covered in
[`authentication.md`](authentication.md).

## Database

Rapla has two storage backends. By default it uses the **XML file store**
(`rapla.file-datasources.raplafile`, `data/data.xml`) — fine for small,
single-instance deployments.

For a **database**, configure a datasource under the key `rapladb`:

```yaml
rapla:
  db-datasources:
    rapladb:
      url: jdbc:postgresql://db.example.com:5432/rapla
      username: rapla
      password: ${RAPLA_DB_PASSWORD}
```

When `rapla.db-datasources.rapladb` is set it becomes the live store; the file
datasource is then used only as the seed (see "First boot" below). The JDBC
driver class is auto-derived from the URL scheme — no `driver-class-name`
needed.

### Drivers

| Database | Driver | Shipped? |
|---|---|---|
| HSQLDB | bundled in the JAR | yes — embedded, zero-setup |
| PostgreSQL | `org.postgresql:postgresql` | place the jar in `./lib/` |
| MariaDB / MySQL | `org.mariadb.jdbc:mariadb-java-client` | place the jar in `./lib/` |
| Microsoft SQL Server | `com.microsoft.sqlserver:mssql-jdbc` or jTDS | operator-supplied in `./lib/` |

HSQLDB is bundled, so an embedded database needs nothing extra:

```yaml
rapla:
  db-datasources:
    rapladb:
      url: jdbc:hsqldb:file:data/rapladb
      username: SA
      password: ""
```

For PostgreSQL / MariaDB / SQL Server, drop the driver jar into `./lib/` next
to the JAR. The bundled `loader.properties` (`loader.path=lib/,plugins/`) puts
`lib/` and `plugins/` on the classpath, so no extra `java -jar` flag is needed.

### First boot

Pointing Rapla at an **empty database** works: on first connect it creates the
full schema and **imports the initial data from the XML file source** (the
default admin account, dynamic types, etc.). So a fresh database deployment
still needs a readable `data/data.xml` seed on first start — if absent, Rapla
falls back to a built-in default.

A **pre-1.8 Rapla database schema is not auto-migrated** — Rapla stops with an
explicit message. Migrate by exporting `data.xml` from a Rapla 1.8 instance and
importing it into the new database.

### Listing only some builtin views

`rapla.views.builtin-listed` is an allowlist of builtin view keys (`rapla_appointments`,
`rapla_reservations`, `rapla_kalender`, `rapla_wochenprogramm`) for the SPA view switcher.
Absent: each builtin's own `@view(listed:)` decides, as before. Set (also to an empty list): a
builtin is listed only if named — so a deployment that wants nothing but its own custom views
is not surprised by a builtin added in a later release. Unlisted builtins stay resolvable by
name and visible in the GraphiQL load dialog.

```yaml
rapla:
  views:
    builtin-listed: []            # custom views only
    # builtin-listed: [rapla_kalender]
```

### Patch directory — stored views and documents from files (PRD 112)

`data/patch/` (property `rapla.patch-dir`, relative to the working directory) holds
self-describing artefact files that Rapla applies to its artifact store at **every start**:

- `<Name>.mustache` with a `{{! rapla-document … }}` head → stored document `<Name>`
  (head keys: `view` (required), `public`, `updated`, optional `groups`, `window`,
  `defaultVariables`);
- `<Name>.graphql` with leading `# rapla-view` / `# key: value` lines → stored view named
  after the query's operation name (head keys: `public`, `updated`, optional `groups`,
  `defaultVariables`).

Rule per file: create when the artefact is missing; overwrite when the head's `updated`
(ISO-8601, bare local time read as UTC, offset/`Z` honoured) is newer than the stored
artefact's last change; otherwise leave it alone — so an admin's later edit in the template
editor survives until a re-delivery bumps `updated`. Views are applied before documents. A
file without a recognised head is ignored (WARN), a file that fails the editor's validation is
skipped (ERROR); the server starts either way. The files may be deleted after the first start —
the store keeps the artefacts. Disable with `rapla.services.org.rapla.plugin.patch: false`.
Dynamic-type annotations (e.g. `documents=` on an event type) are **not** patched — set them in
the type editor.

## Docker

The repo root carries a `Dockerfile` that wraps the fat JAR in
`eclipse-temurin:21-jre`, with the install layout from above under
`/opt/rapla` and a non-root `rapla` user. Build the JAR first (see
§"Building the deployable JAR"), then the image:

```sh
mvn -pl rapla-app -am clean package -DskipTests
docker compose up -d --build
```

Browse to `http://localhost:8051` — the XML file store in the `rapla-data`
volume starts with the default admin account (username `admin`, empty
password; **set a password immediately**, see §"Quick start").

| Container path | Compose default | Purpose |
|---|---|---|
| `/opt/rapla/data` | volume `rapla-data` | datastore / first-boot seed |
| `/opt/rapla/logs` | volume `rapla-logs` | application + access log |
| `/opt/rapla/config/application.yml` | not mounted | your overrides — bind-mount a file |
| `/opt/rapla/lib` | not mounted | extra JDBC drivers |
| `/opt/rapla/plugins` | not mounted | drop-in plugin jars |

`compose.yaml` sets the project name `rapla`, so the volumes are called
`rapla_rapla-data`, `rapla_rapla-logs` (and `rapla_rapla-db` for MariaDB)
whatever the checkout directory is named.

Configuration works as in §"Configuration": mount `config/application.yml`
or set environment variables (`SERVER_PORT`, `RAPLA_OAUTH_PUBLIC_BASE_URL`,
…). JVM flags go into `JAVA_TOOL_OPTIONS` (e.g. `-Xmx2g`).

### Storage modes

Copy `.env.example` to `.env` next to `compose.yaml` and uncomment the
block for your mode — Compose reads `.env` automatically, then
`docker compose up -d --build`.

| Mode | Set in `.env` |
|---|---|
| File store (default) | nothing |
| HSQLDB in the `rapla-data` volume | `RAPLA_DBDATASOURCES_RAPLADB_URL=jdbc:hsqldb:file:/opt/rapla/data/rapladb`<br>`RAPLA_DBDATASOURCES_RAPLADB_USERNAME=SA` |
| HSQLDB + seed | the two HSQLDB lines, plus<br>`COMPOSE_FILE=compose.yaml:docker/compose.seed.yaml`<br>`RAPLA_SEED_XML=<path to data.xml>` |
| MariaDB | `COMPOSE_FILE=compose.yaml:docker/compose.mariadb.yaml`<br>`RAPLA_DB_PASSWORD=<choose one>`<br>driver jar `mariadb-java-client-*.jar` in `./lib/` (or `RAPLA_LIB_DIR`) |

- Keep unused lines **commented**, not empty: an empty
  `RAPLA_DBDATASOURCES_RAPLADB_URL=` stops Rapla at startup.
- The variable names are Spring Boot's environment form of
  `rapla.db-datasources.rapladb.*`, so the same names work outside Docker.
- On Windows separate the `COMPOSE_FILE` entries with `;`, or set
  `COMPOSE_PATH_SEPARATOR=:`.
- With a database, the first start creates the schema and imports the seed
  (§"First boot"); the log shows `Using datasource HSQL Database Engine` or
  `Using datasource MariaDB`.

**Seed.** `docker/compose.seed.yaml` mounts `RAPLA_SEED_XML` read-only at
`/opt/rapla/seed/data.xml` and points `rapla.file-datasources.raplafile` at it.
An empty database imports it on first start; later starts ignore it. Use it
with the **database modes only** — the file store saves by renaming its
`data.xml` to `data.xml.bak` and writing a new one, which a read-only mount
cannot take. For MariaDB list both overlays:
`COMPOSE_FILE=compose.yaml:docker/compose.mariadb.yaml:docker/compose.seed.yaml`.

### Nightly image

Every nightly CI run on `master` pushes `ghcr.io/rapla/rapla:nightly` — the
current `master` state with the self-signed dev certificate for the Web Start
client. **Test builds only, not for production**; the tag moves every night.

```sh
docker pull ghcr.io/rapla/rapla:nightly
docker run -d --name rapla -p 8051:8051 \
  -v rapla-data:/opt/rapla/data -v rapla-logs:/opt/rapla/logs \
  ghcr.io/rapla/rapla:nightly
```

Settings go in with `-e` or from the same `.env` file as above:

```sh
docker run -d --name rapla -p 8051:8051 \
  -e RAPLA_DBDATASOURCES_RAPLADB_URL=jdbc:hsqldb:file:/opt/rapla/data/rapladb \
  -e RAPLA_DBDATASOURCES_RAPLADB_USERNAME=SA \
  -v rapla-data:/opt/rapla/data -v rapla-logs:/opt/rapla/logs \
  ghcr.io/rapla/rapla:nightly

docker run -d --name rapla -p 8051:8051 --env-file .env \
  -v rapla-data:/opt/rapla/data -v rapla-logs:/opt/rapla/logs \
  ghcr.io/rapla/rapla:nightly
```

`--env-file` hands every `KEY=value` line to the container; the
`COMPOSE_FILE` / `RAPLA_SEED_XML` / `RAPLA_LIB_DIR` entries are Compose
settings and have no effect there.

With Compose, `compose.yaml` in the repo **builds the image locally**
(`build: .`). To use the nightly image instead, replace that line with the
registry image and run `docker compose pull && docker compose up -d`:

```yaml
services:
  rapla:
    image: ghcr.io/rapla/rapla:nightly
```

The overlays in `docker/` and `.env` work unchanged.

The GitHub package is created **private** by the first push. Two one-time
steps for an owner of the `rapla` organization, in the package settings — no
workflow can do either:

- *Change visibility* → public. Until then `docker pull` needs
  `docker login ghcr.io` with a GitHub token.
- *Manage Actions access* → `rapla/rapla` → **Admin**. Deleting old untagged
  versions with the workflow's `GITHUB_TOKEN` needs the Admin role; without it
  the cleanup step gets HTTP 403 and the `docker` job turns red right after a
  successful push.

### Backup and restore

**File store and HSQLDB** — archive the data volume with a throwaway
container. Stop Rapla first; for HSQLDB this is required so the database
files are consistent:

```sh
docker compose stop rapla
docker run --rm -v rapla_rapla-data:/data -v "$PWD":/backup busybox \
  tar czf /backup/rapla-data.tgz -C /data .
docker compose start rapla
```

Restore into a fresh volume — create it without starting Rapla, unpack, start:

```sh
docker compose up --no-start
docker run --rm -v rapla_rapla-data:/data -v "$PWD":/backup busybox \
  tar xzf /backup/rapla-data.tgz -C /data
docker compose up -d
```

**MariaDB** — dump and restore through the database container; the password
is read from the container's own environment:

```sh
docker compose exec -T mariadb sh -c 'mariadb-dump -u rapla -p"$MARIADB_PASSWORD" rapla' > rapla.sql
docker compose exec -T mariadb sh -c 'mariadb -u rapla -p"$MARIADB_PASSWORD" rapla' < rapla.sql
```

**Host folder instead of a volume** — if the data should sit in a directory
your host backup already covers, replace `rapla-data:/opt/rapla/data` in
`compose.yaml` with `./data:/opt/rapla/data`. The container runs as the system
user `rapla` with a fixed uid/gid 999 (pinned in the `Dockerfile`, check with
`docker compose exec rapla id`), so the directory must be writable for it:
`mkdir data && sudo chown 999:999 data`.

## Running as a service

### Linux — systemd

The service layout matches the install layout from §"Install layout" — the JAR
sits at the top of `/opt/rapla` and the operator's **state** (`config/`,
`data/`, `lib/`, `logs/`) lives beside it. An upgrade replaces the JAR
wholesale and never touches the rest:

```
/opt/rapla/
  rapla.jar  ← the artifact, replaced on every upgrade
  config/application.yml  ← your overrides
  data/data.xml           ← datastore (XML mode) or first-boot seed (DB mode)
  lib/                    ← extra JDBC drivers (PostgreSQL / MariaDB / …)
  logs/
```

**1. Install.** As root, create the layout and drop the JAR in:

```sh
sudo mkdir -p /opt/rapla/{config,data,lib,logs}
sudo cp rapla.jar /opt/rapla/
```

**2. Service account.** A dedicated, login-less system user owns the runtime:

```sh
sudo useradd --system --user-group --shell /usr/sbin/nologin \
  --home-dir /opt/rapla rapla
```

**3. Permissions.** The service user only needs to *write* `data/` and `logs/`;
everything else is read-only to it (owned `root:rapla`):

```sh
sudo chown -R root:rapla /opt/rapla
sudo chown -R rapla:rapla /opt/rapla/data /opt/rapla/logs
sudo chmod -R 750 /opt/rapla
sudo chmod -R 770 /opt/rapla/data /opt/rapla/logs
```

**4. Unit file** — `/etc/systemd/system/rapla.service`:

```ini
[Unit]
Description=Rapla Server
After=network.target

[Service]
Type=exec
User=rapla
Group=rapla
WorkingDirectory=/opt/rapla
ExecStart=/usr/bin/java -Xmx2048m -Djava.awt.headless=true \
  -jar /opt/rapla/rapla.jar
Restart=on-failure
TimeoutStopSec=45
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

Notes on the unit:
- `WorkingDirectory=/opt/rapla` is the **state root** — Rapla resolves
  `config/`, `data/`, `lib/`, `logs/` relative to it.
- `lib/` (operator-supplied JDBC drivers such as PostgreSQL or MariaDB) and
  `plugins/` are on the classpath through the bundled `loader.properties`;
  drop the jar in, restart, no rebuild.
- `-Xmx2048m` is a starting point; tune to the host.
- `SuccessExitStatus=143` — the JVM exits `143` (128 + SIGTERM) on a clean
  `systemctl stop`; without this systemd would log the stop as failed.

**5. Enable and start:**

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now rapla
sudo systemctl status rapla        # check it came up
```

`stop` / `restart` / `status` work as usual. Logs go to `/opt/rapla/logs/`
(and `journalctl -u rapla` for the stdout/stderr).

**Graceful shutdown.** By default a `systemctl stop` terminates the JVM
immediately, dropping any in-flight requests. To drain them first, set in
`config/application.yml`:

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

`TimeoutStopSec=45` in the unit must stay larger than that drain window so
systemd waits for the graceful stop instead of killing the process.

> The in-app "restart" button (admin menu) is **not** a service restart — since
> [PRD 048](prd/048-eliminate-server-container-context.md) it performs a logical
> *reload* (re-reads the store, rebuilds caches) without bouncing the JVM. To
> restart the OS service, use `systemctl restart rapla`.

### Windows — service wrapper

Spring Boot has no native Windows-service support, and `sc.exe` cannot run
`java` directly (no Service Control Manager handshake). Use a wrapper —
**WinSW** is recommended (MIT-licensed, a single `.exe` + a short XML).
Configure `<executable>java</executable>`, the classpath/main-class arguments,
`<workingdirectory>` = the install dir, and a `<stoptimeout>` paired with
`server.shutdown=graceful` so a service stop drains in-flight requests.

## Upgrading

Replace the JAR only — leave `config/`, `data/`, and `lib/` untouched:

```sh
sudo systemctl stop rapla
sudo cp rapla.jar /opt/rapla/
sudo chown root:rapla /opt/rapla/rapla.jar
sudo systemctl start rapla
```

A database schema is migrated in place on the next start.
