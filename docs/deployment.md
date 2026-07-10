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
rapla-app/target/rapla-2.1-SNAPSHOT.jar
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
  rapla-2.1-SNAPSHOT.jar      ← the artifact, never modified
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
java -jar rapla-2.1-SNAPSHOT.jar
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
to the JAR and add `-Dloader.path=lib/` to the `java -jar` invocation (Spring
Boot's fat-JAR convention for external classpath entries).

### First boot

Pointing Rapla at an **empty database** works: on first connect it creates the
full schema and **imports the initial data from the XML file source** (the
default admin account, dynamic types, etc.). So a fresh database deployment
still needs a readable `data/data.xml` seed on first start — if absent, Rapla
falls back to a built-in default.

A **pre-1.8 Rapla database schema is not auto-migrated** — Rapla stops with an
explicit message. Migrate by exporting `data.xml` from a Rapla 1.8 instance and
importing it into the new database.

## Running as a service

### Linux — systemd

The service layout matches the install layout from §"Install layout" — the JAR
sits at the top of `/opt/rapla` and the operator's **state** (`config/`,
`data/`, `lib/`, `logs/`) lives beside it. An upgrade replaces the JAR
wholesale and never touches the rest:

```
/opt/rapla/
  rapla-2.1-SNAPSHOT.jar  ← the artifact, replaced on every upgrade
  config/application.yml  ← your overrides
  data/data.xml           ← datastore (XML mode) or first-boot seed (DB mode)
  lib/                    ← extra JDBC drivers (PostgreSQL / MariaDB / …)
  logs/
```

**1. Install.** As root, create the layout and drop the JAR in:

```sh
sudo mkdir -p /opt/rapla/{config,data,lib,logs}
sudo cp rapla-2.1-SNAPSHOT.jar /opt/rapla/
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
  -Dloader.path=/opt/rapla/lib/ \
  -jar /opt/rapla/rapla-2.1-SNAPSHOT.jar
Restart=on-failure
TimeoutStopSec=45
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

Notes on the unit:
- `WorkingDirectory=/opt/rapla` is the **state root** — Rapla resolves
  `config/`, `data/`, `lib/`, `logs/` relative to it.
- `-Dloader.path=/opt/rapla/lib/` is Spring Boot's fat-JAR convention for
  adding external classpath entries — that's where operator-supplied JDBC
  drivers (PostgreSQL, MariaDB) go; drop the jar in, restart, no rebuild.
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
sudo cp rapla-2.1-SNAPSHOT.jar /opt/rapla/
sudo chown root:rapla /opt/rapla/rapla-2.1-SNAPSHOT.jar
sudo systemctl start rapla
```

A database schema is migrated in place on the next start.
