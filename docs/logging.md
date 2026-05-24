# Logging

How rapla writes logs, where they land, and how to change levels or destinations
at deploy time. Operator-first — the recipes section covers everything a typical
deployment needs; the architecture section explains the *why* if you're
modifying the setup.

## TL;DR

| Surface | Default file | How to change |
|---|---|---|
| Server application log | `<install-dir>/logs/rapla.log` (rolling daily + 100 MB, gzipped, 30-day retention) | `LOGGING_LEVEL_ORG_RAPLA=DEBUG` env var |
| Server HTTP access log | `<install-dir>/logs/rapla_access.YYYY-MM-DD.log` (Apache Combined) | `SERVER_TOMCAT_ACCESSLOG_ENABLED=false` to disable |
| Server console | stdout (mirror of application log, color in TTY) | `SPRING_PROFILES_ACTIVE=cloud` keeps console only (no file) |
| Swing desktop client | stdout, plus whatever the JNLP launcher captures | `java -DRAPLA_LOG_LEVEL=DEBUG …` |

## Operator recipes

### Change a log level

Env-var name: take the property dotted path, uppercase, replace `.` with `_`:

```bash
# raise specific package
export LOGGING_LEVEL_ORG_RAPLA=DEBUG
export LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG
export LOGGING_LEVEL_NET_FORTUNA_ICAL4J=INFO       # un-silence iCal4j

# lower the whole world
export LOGGING_LEVEL_ROOT=WARN
```

Works for any package; takes effect at startup. Spring Boot's [Loggers
endpoint](https://docs.spring.io/spring-boot/api/rest/actuator/loggers.html)
can change levels at *runtime* without restart but the actuator endpoint is
disabled by default.

### Change the application-log location

```bash
export LOGGING_FILE_NAME=/var/log/rapla/rapla.log
# rolling policy is auto-applied (100 MB / 30 days / 2 GB cap)
```

Tune the rolling policy:

```bash
export LOGGING_LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE=200MB
export LOGGING_LOGBACK_ROLLINGPOLICY_MAX_HISTORY=14
export LOGGING_LOGBACK_ROLLINGPOLICY_TOTAL_SIZE_CAP=5GB
```

### Disable file logging (cloud / k8s / Docker — stdout only)

```bash
export SPRING_PROFILES_ACTIVE=cloud
```

Console-only output. Your container platform / log shipper captures stdout.

### Disable access logging

```bash
export SERVER_TOMCAT_ACCESSLOG_ENABLED=false
```

Useful when a reverse proxy / ingress (nginx, traefik, AWS ALB) already logs
requests at the edge.

### Move the access log

```bash
export SERVER_TOMCAT_ACCESSLOG_DIRECTORY=/var/log/rapla   # absolute path bypasses basedir
export SERVER_TOMCAT_ACCESSLOG_PATTERN=common             # NCSA Common, no referer/agent
export SERVER_TOMCAT_ACCESSLOG_MAX_DAYS=14
```

### Full-replace the logback config

For unusual needs (JSON for ELK, syslog, custom appenders):

```bash
export LOGGING_CONFIG=file:/etc/rapla/custom-logback.xml
```

Wins over everything. Drop in a file with whatever appenders/encoders you need;
Spring Boot will load it instead of the bundled `logback-spring.xml`. For JSON
output specifically, Spring Boot 4 ships
`org/springframework/boot/logging/logback/structured-{console,file}-appender.xml`
which can be `<include>`d in your overlay (ECS, Logstash, Graylog).

### Silence a specific noisy logger

```bash
export LOGGING_LEVEL_<DOTTED_PATH_UPPERCASE_UNDERSCORED>=ERROR
```

Pre-baked silencers in the bundled `application.yml` /
`logback-spring.xml`:
- `net.fortuna.ical4j.model.TimeZoneRegistryImpl` → ERROR
- `org.springframework.security.config.annotation.authentication.configuration.InitializeUserDetailsBeanManagerConfigurer` → ERROR (intentional both-beans setup)

## Where logs land — file layout

`${user.dir}` is the JVM's current working directory.

- **`java -jar rapla-*.jar`** — `user.dir` is wherever you launched from, typically the install directory:
  ```
  /opt/rapla/
  ├── rapla-2.1-SNAPSHOT.jar
  ├── data/
  └── logs/
      ├── rapla.log                          # application log (rolling, gzipped archives)
      └── rapla_access.2026-05-24.log        # HTTP access log (daily)
  ```

- **`mvn -pl rapla-app spring-boot:run`** — `user.dir` is `rapla-app/`:
  ```
  rapla-app/logs/
  ├── rapla.log
  └── rapla_access.2026-05-24.log
  ```

- **`SPRING_PROFILES_ACTIVE=cloud`** — no `logs/` dir; everything on stdout.

## Swing desktop client

The Swing client is **separate** from the server (different JVM, different
deployment model — launched via JNLP, no Spring Boot). Its config lives in the
bundled `rapla-client.jar` at `logback.xml` (not logback-spring.xml).

**Defaults**: console-only (whatever the launcher captures), `org.rapla` at
INFO, AWT/Swing/Spring JUL chatter silenced.

**Change levels**:

```bash
java -DRAPLA_LOG_LEVEL=DEBUG \
     -jar netx.jar /path/to/raplaclient.jnlp
```

Or via the JNLP file's `<j2se>` `java-vm-args`.

**Custom config**:

```bash
java -Dlogback.configurationFile=/path/to/custom-logback.xml \
     -jar netx.jar /path/to/raplaclient.jnlp
```

## Architecture (what's under the hood)

### Spring Boot's logging precedence chain

At startup, Spring Boot's `LoggingApplicationListener` looks for, in order:

1. The `logging.config` property (env var `LOGGING_CONFIG`, JVM
   `-Dlogging.config=...`, or `application.yml`). Wins over everything.
   Accepts `classpath:foo.xml`, `file:/etc/.../foo.xml`, or a URL.
2. `classpath:logback-spring.xml` — rapla's bundled config (this is what you
   normally get).
3. `classpath:logback.xml` — vanilla Logback. `<springProperty>` and
   `<springProfile>` do NOT work in this file (it's loaded before Spring
   property sources).
4. None of the above → Spring Boot's defaults (console-only, `INFO` root).

### What's in rapla's bundled `logback-spring.xml`

- **Includes** Spring Boot's `defaults.xml` (`%clr{}` conversion rules,
  `CONSOLE_LOG_PATTERN`, `FILE_LOG_PATTERN`, embedded-Tomcat noise
  silencing) and `console-appender.xml`.
- **Rolling file appender** at `${user.dir}/logs/rapla.log` with
  `SizeAndTimeBasedRollingPolicy` (daily + 100 MB, gzipped, 30-day max history,
  2 GB total cap). All tunable via `logging.logback.rollingpolicy.*`.
- **`LevelChangePropagator`** keeps JUL per-logger levels in sync with
  Logback's. Spring Boot installs `SLF4JBridgeHandler` at startup, but JUL
  filters events at the source unless its level matches — without the
  propagator, raising `org.rapla` to DEBUG wouldn't catch JUL-emitting code.
- **Subsystem silencers** — currently just `net.fortuna.ical4j` (WARN) +
  `TimeZoneRegistryImpl` (ERROR).
- **Profiles**: `cloud` removes the file appender (stdout-only).

### Why a custom logback-spring.xml at all (vs. Spring Boot defaults)

Rapla's deploy model is "drop a JAR, run it" — many installations are
self-hosted, not k8s. File logging out of the box matters for those operators
who want to grep yesterday's startup error without setting up a log shipper.
The cloud-native case is covered by the `cloud` profile.

### Why Tomcat's `AccessLogValve` over `logback-access`

`AccessLogValve` is built into the embedded Tomcat we already ship; zero new
dependencies, configured via the same `application.yml` namespace as other
Tomcat settings (`server.tomcat.*`). `logback-access` would mean an extra
dependency, an extra XML file, and the same operational outcome.

### `server.tomcat.basedir`

Pinned to `${user.dir}` so the access log lands in `${user.dir}/logs/` next
to the application log. Without this, Tomcat defaults `basedir` to a
`/tmp/tomcat.<port>.<random>/` directory and the access log gets lost.

### Profiles

| Profile | What it does |
|---|---|
| `local` | Activates `application-local.yml` (dev IdP configs, public-base-url override). Logging defaults unchanged. |
| `cloud` | Removes the file appender from the root logger — stdout only. Use for Docker / k8s. |

Combine via `SPRING_PROFILES_ACTIVE=local,cloud` etc.

## Dev quirk: two `logs/` directories

When running `mvn -pl rapla-app spring-boot:run` from the repo root with my
agent-friendly shell redirection (`> /home/chris/git/rapla/logs/build.log
2>&1`):

```
<repo-root>/logs/build.log        ← mvn stdout (build messages + Spring Boot console)
<repo-root>/rapla-app/logs/
├── rapla.log                     ← logback FILE appender (no Maven noise) — canonical app log
└── rapla_access.YYYY-MM-DD.log   ← Tomcat AccessLogValve
```

There's no `<repo-root>/logs/rapla.log` in active use; if you see one, it's
stale from a previous session that used the older redirect target. Safe to
delete.

In production this collapses to one `logs/` directory (the install dir).

## Migration notes (from master)

Master used Logback in `src/test/resources/logback.xml` (a confusingly-named
production config copied into the Jetty deployment by the build), with three
appenders (`STDOUT` filtered INFO, `STDERR` filtered WARN+, `FILE` rolling)
and 14 named loggers using the old `rapla.*` flat namespace from the
pre-PRD-053 `org.rapla.logger` abstraction.

The spring-boot branch:

- Dropped the `rapla.*` named loggers — post-PRD-053 every class uses
  `LoggerFactory.getLogger(ThisClass.class)`, so logger names are FQCNs.
  Master's `rapla.serverfacade=WARN` etc. would be silently no-ops here.
- Replaced the WAR-on-Jetty deployment with the Spring Boot fat JAR.
  `${jetty.home}` placeholders are gone; relative paths now resolve to
  `${user.dir}`.
- Replaced Jetty's `logback-access` HTTP request log with Tomcat's built-in
  `AccessLogValve` (no XML; controlled by YAML).
- Kept the LevelChangePropagator (JUL bridge) — still relevant since some
  third-party libraries (iCal4j, JDK internals) log via JUL.
