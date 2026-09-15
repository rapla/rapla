# Running a public Rapla demo

A public demo is an ordinary Rapla installation with three additions: a hard memory cap, a daily reset to a seed file, and a reverse proxy with TLS in front. This recipe builds on [Deploying Rapla § Linux — systemd](../deployment.md#linux--systemd); read that first. The demo at https://demo.rapla.org is set up this way. Which data sets and demo settings exist is described in [PRD 118](../prd/118-rapla3-demo-usecases.md).

Placeholders: `<demo-root>` (state root, e.g. `/opt/rapla-demo`), `<demo-user>` (system user), `<port>` (loopback port), `<host>` (public hostname).

## The JAR

Build `rapla.jar` in the working copy, like any other release (`mvn -pl rapla-app -am clean package -DskipTests`), or take a published release. A working copy can hold untracked files that Maven packs into the JAR, so check both sides:

```sh
git ls-files --others -- 'rapla-*/src/main'          # before: untracked sources and resources get compiled in
unzip -l rapla-app/target/rapla.jar | grep 'application-.*\.yml'
zip -d rapla-app/target/rapla.jar 'BOOT-INF/classes/application-<local-profile>.yml'   # after: strip untracked profiles
sha256sum rapla-app/target/rapla.jar
```

`clean` removes `target/classes`, so a development server started from that checkout stops working until it is restarted.

## Layout

```
<demo-root>/
  rapla.jar
  config/application.yml   root:<demo-user> 640
  seed/<usecase>.xml        root:<demo-user> 640, read-only for the service
  seed/current.xml          symlink to the active seed
  patch/                    root-owned, empty
  data/data.xml             live store, written by the service
  logs/  work/              written by the service
```

If the host already runs another Rapla, pick a unit name, user and port that cannot collide with it.

## Configuration

`config/application.yml`:

```yaml
server:
  address: 127.0.0.1
  port: <port>
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
rapla:
  file-datasources:
    raplafile: <demo-root>/data/data.xml
  oauth:
    public-base-url: https://<host>
  fix-admin-password: true
  patch-dir: <demo-root>/patch
```

- `server.address: 127.0.0.1` — only the reverse proxy reaches Rapla.
- `rapla.fix-admin-password: true` locks the built-in `admin` account: its password cannot be changed and it cannot be deleted. Use it when the demo login is published.
- Set no mail, Exchange, LDAP or external identity-provider settings.
- `rapla.patch-dir` defaults to `data/patch` relative to the working directory. Point it at an empty directory owned by root (`<demo-root>/patch`, not writable by the service) so no stored views or documents are imported by accident.

## Service with a memory cap

`/etc/systemd/system/rapla-demo.service`:

```ini
[Unit]
Description=Rapla public demo
After=network.target

[Service]
Type=exec
User=<demo-user>
Group=<demo-user>
WorkingDirectory=<demo-root>
Environment=SPRING_PROFILES_ACTIVE=demo
ExecStart=/usr/bin/java -Xmx640m -XX:MaxMetaspaceSize=192m -Djava.awt.headless=true -jar <demo-root>/rapla.jar
Restart=on-failure
TimeoutStopSec=45
SuccessExitStatus=143
MemoryMax=1G
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
ReadWritePaths=<demo-root>/data <demo-root>/logs <demo-root>/work

[Install]
WantedBy=multi-user.target
```

`SPRING_PROFILES_ACTIVE=demo` activates the hardening profile shipped in the JAR (`application-demo.yml`): passwords locked, password grant, Swing/JNLP, Exchange, iCal import and mail plugins off (the iCal export URLs stay available), a fail-closed `/api` allowlist and a demo banner. "Switch to user" (impersonation) stays on (PRD 118 D8-13). `MemoryMax=1G` caps the whole process; heap (640m) plus metaspace (192m) leave room for thread stacks and the code cache. A demo with a small seed starts at roughly 250 MB.

## Daily reset

`/usr/local/bin/rapla-demo-reset`:

```sh
#!/bin/sh
set -eu
R=<demo-root>
SEED="$R/seed/current.xml"
if grep -q -E 'org\.rapla\.crypto|org\.rapla\.auth\.session|org\.rapla\.auth\.rememberMeTokens|org\.rapla\.server\.exchangeuser' "$SEED"; then
  echo "rapla-demo-reset: ABORT, seed $(readlink -f "$SEED") carries server secrets; demo left running unchanged" >&2
  exit 2
fi
systemctl stop rapla-demo
install -o <demo-user> -g <demo-user> -m 640 "$SEED" "$R/data/data.xml"
rm -f "$R/data/data.xml.bak"
systemctl start rapla-demo
for i in $(seq 90); do curl -fsS -o /dev/null http://127.0.0.1:<port>/app/ && exit 0; sleep 2; done
exit 1
```

`rapla-demo-reset.service` (`Type=oneshot`, `ExecStart=/usr/local/bin/rapla-demo-reset`) and `rapla-demo-reset.timer`:

```ini
[Timer]
OnCalendar=*-*-* 04:15:00 Europe/Berlin
Persistent=true

[Install]
WantedBy=timers.target
```

`systemctl enable --now rapla-demo rapla-demo-reset.timer`. Right after a start, `data/data.xml` no longer matches the seed byte for byte: Rapla saves the store on startup and leaves out its internal system types. That is expected.

## Apache reverse proxy

Port-80 vhost with only `ServerName <host>`, then `certbot --apache -d <host> --redirect`. Certbot creates the TLS vhost; add inside it:

```apache
ProxyPreserveHost On
RequestHeader set X-Forwarded-Proto "https"
ProxyPass        / http://127.0.0.1:<port>/
ProxyPassReverse / http://127.0.0.1:<port>/
```

Rapla sends HSTS itself; do not add a second `Strict-Transport-Security` header. Needs `proxy`, `proxy_http`, `headers`, `ssl`. Run `apache2ctl configtest` before every reload. Check whether the files in `sites-enabled/` are symlinks or copies before editing an existing vhost.

## Switching the data set

The demo data files are tracked in the repository as `docs/demo/<usecase>/demo-<usecase>.xml`.

A seed must not carry server secrets. A running Rapla writes its JWT signing key (`org.rapla.crypto.*`, including `org.rapla.crypto.server.refreshToken`), refresh sessions (`org.rapla.auth.session`) remember-me tokens (`org.rapla.auth.rememberMeTokens`) and Exchange credentials (`org.rapla.server.exchangeuser`) into the store, so any file saved by a server run can contain them. With the key anyone can forge tokens; remember-me tokens log in even without it. Check before every swap — all counts must be 0:

```sh
for k in org.rapla.crypto org.rapla.auth.session org.rapla.auth.rememberMeTokens org.rapla.server.exchangeuser; do
  printf '%s %s\n' "$k" "$(grep -c "$k" demo-<usecase>.xml)"
done
```

Without these entries Rapla generates a fresh key when it starts, and the daily reset gives the demo a new key every night. The reset script refuses a seed that still carries any of them and leaves the running demo untouched.

```sh
install -m 640 -o root -g <demo-user> docs/demo/<usecase>/demo-<usecase>.xml <demo-root>/seed/
ln -sfn demo-<usecase>.xml <demo-root>/seed/current.xml
systemctl start rapla-demo-reset.service
```

## Smoke test

Without the `demo` profile the OAuth password grant is open:

```sh
curl -s -o /dev/null -w '%{http_code}\n' https://<host>/app/                                   # 200
curl -s -o /dev/null -w '%{http_code}\n' -H 'Content-Type: application/json' \
  -d '{"query":"{__typename}"}' https://<host>/api/graphql                                    # 401
curl -s -X POST https://<host>/oauth2/token \
  -d 'grant_type=password&username=admin&password=&client_id=rapla-client'                   # token
```

With the `demo` profile the password grant and `/server` are closed. Log in through the form like a browser: the login page carries a `_csrf` field, the login sets an HttpOnly `access_token` cookie, and a cookie-authenticated `POST` needs the `XSRF-TOKEN` cookie echoed as `X-XSRF-TOKEN` header (without it: 403):

```sh
J=$(mktemp)
CSRF=$(curl -s -c "$J" -b "$J" https://<host>/login | sed -n 's/.*name="_csrf"[^>]*value="\([^"]*\)".*/\1/p')
curl -s -o /dev/null -w '%{http_code}\n' -c "$J" -b "$J" \
  --data-urlencode username=admin --data-urlencode password= --data-urlencode "_csrf=$CSRF" \
  https://<host>/login                                                                        # 302
curl -s -o /dev/null -w '%{http_code}\n' -c "$J" -b "$J" https://<host>/api/auth/me              # 200
XSRF=$(sed 's/^#HttpOnly_//' "$J" | awk '$6=="XSRF-TOKEN"{print $7}' | tail -1)
curl -s -b "$J" -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
  -d '{"query":"{__typename}"}' https://<host>/api/graphql                                    # {"data":{"__typename":"Query"}}
rm -f "$J"
```
