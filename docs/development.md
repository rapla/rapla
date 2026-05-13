# Development guide — WSL2 + Windows

This guide covers what's specific to developing rapla on **WSL2 with the OpenWebStart client running on Windows**. For everything else (Maven reactor, dev server, parallel work, signing profiles) start with [AGENTS.md](../AGENTS.md). For the deployable runtime classloader bug see [PRD 018](prd/018-fat-jar-classloader-defect.md).

## Quick map

| Layer | Where you'll find it |
|---|---|
| Reactor build, server lifecycle | [AGENTS.md](../AGENTS.md) §5, §8 |
| Swing client launched directly (no JNLP) | [AGENTS.md](../AGENTS.md) §9 |
| Fat-JAR test (no signing, no OWS) | `.agents/skills/test-deployment/SKILL.md` |
| **Full JNLP + OWS launch with self-signing** (this guide) | `.agents/skills/test-jnlp-launch/SKILL.md` |
| Spring Boot 4 LaunchedClassLoader workaround (extract-and-run) | [PRD 018](prd/018-fat-jar-classloader-defect.md) |
| Six known JNLP build/code defects | [memory: project_jnlp_signing_pitfalls](#known-jnlp-defects) (also in agent memory) |

## Bootstrap a fresh WSL2 Ubuntu environment

Captured from a real WSL2 Ubuntu install — install order matters (SDKMAN/nvm install scripts both need `curl`; SDKMAN also needs `zip`/`unzip`).

```bash
# 1. Base apt prerequisites
sudo apt-get update
sudo apt-get install -y curl zip unzip jq

# 2. SDKMAN → Java 21 + Maven (rapla targets Java 17 source, runs on Java 21)
curl -s "https://get.sdkman.io" | bash
source ~/.sdkman/bin/sdkman-init.sh   # or restart shell
sdk install java 21.0.11-sem          # Eclipse Temurin via Semeru build
sdk install maven                     # SDKMAN picks the current stable

# 3. nvm → Node 24 (Angular 21 needs >= 20.19; pinned to 24.15.0 in this repo)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/master/install.sh | bash
source ~/.bashrc
nvm install v24.15.0
nvm use v24.15.0

# 4. User-local npm prefix — avoids `sudo npm install -g`
mkdir -p ~/.npm-global
npm config set prefix ~/.npm-global
echo 'export PATH=~/.npm-global/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# 5. Claude Code
npm install -g @anthropic-ai/claude-code

# 6. wsl-screenshot-cli — pastes Windows screenshots into Claude Code as image attachments.
#    The daemon watches the Windows clipboard; when an image lands there, it
#    saves a PNG under /tmp/.wsl-screenshot-cli/ and rewrites the clipboard so
#    the WSL path is also available as text. Start the daemon once per boot.
curl -fsSL https://nailu.dev/wscli/install.sh | bash
wsl-screenshot-cli start --daemon --quiet
```

**Pasting a screenshot into Claude Code:** Win+Shift+S → snip an area → in the Claude Code prompt press **Ctrl+Shift+V** (terminal paste; plain Ctrl+V is copy in terminals). The path lands as text and Claude Code auto-attaches the image. PNGs accumulate in `/tmp/.wsl-screenshot-cli/` (tmpfs — cleared on reboot). `wsl-screenshot-cli status` shows daemon uptime + screenshot count.

### 8. jdwp-mcp — step-debug a live rapla JVM from Claude Code

Lets the agent attach to a running JVM (Spring Boot dev server most commonly, sometimes the Swing client), set breakpoints, inspect locals/fields, evaluate expressions in scope. See the `java-debugger` skill for the workflow and when to reach for it.

```bash
# Pre-built binary into ~/.local/bin (already on PATH from step 4)
TAG=$(curl -fsSL https://api.github.com/repos/dronsv/jdwp-mcp/releases/latest \
        | grep '"tag_name"' | sed 's/.*"tag_name": *"\([^"]*\)".*/\1/')
curl -fsSL "https://github.com/dronsv/jdwp-mcp/releases/download/${TAG}/jdwp-mcp-linux-x86_64.tar.gz" \
  -o /tmp/jdwp-mcp.tgz
tar -xzf /tmp/jdwp-mcp.tgz -C ~/.local/bin/
chmod +x ~/.local/bin/jdwp-mcp

# Register with Claude Code (project scope)
claude mcp add jdwp jdwp-mcp
claude mcp list | grep jdwp        # should show: jdwp: jdwp-mcp  - ✓ Connected
```

After register, the agent has `mcp__jdwp__attach`, `mcp__jdwp__set_breakpoint`, `mcp__jdwp__evaluate`, etc. (13 tools).

**Enabling JDWP on the target JVM.** The dev server needs `-agentlib:jdwp=...` added to its JVM args. From the AGENTS.md §8 start recipe, append:

```bash
-Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

`suspend=n` boots normally and waits for the agent's attach. Use `suspend=y` if you need to break before `main()` returns (early-init bugs). Port 5005 by rapla convention; the Swing client uses 5006 if debugged in parallel. Never start a production rapla with `-agentlib:jdwp` — it's a meaningful attack surface.

Upstream is [dronsv/jdwp-mcp](https://github.com/dronsv/jdwp-mcp) (a fork of [navicore/jdwp-mcp](https://git.navicore.tech/navicore/jdwp-mcp), the originally-published version which is now in maintenance mode).

### 9. GitHub CLI (`gh`) — PRs, issues, reviews, releases

Claude Code's system prompt routes all GitHub work through `gh` via the Bash tool (`gh pr create`, `gh pr view`, `gh issue list`, `gh run watch`, etc.). No GitHub MCP server needed for our scale — `gh` and the MCP wrap the same REST API; the MCP only pays off for line-anchored review-comment loops we don't run.

Install (official keyring path — gets the up-to-date release, not Ubuntu's older universe build):

```bash
sudo apt-get install -y wget
sudo mkdir -p -m 755 /etc/apt/keyrings
wget -nv -O- https://cli.github.com/packages/githubcli-archive-keyring.gpg \
  | sudo tee /etc/apt/keyrings/githubcli-archive-keyring.gpg > /dev/null
sudo chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg

# Use short variables to keep the `deb` line below the terminal wrap point —
# pasting a single ~140-char line tends to inject literal newlines mid-line,
# which produces "Malformed entry 1 in list file" on apt update.
KEY=/etc/apt/keyrings/githubcli-archive-keyring.gpg
URL=https://cli.github.com/packages
echo "deb [arch=amd64 signed-by=$KEY] $URL stable main" \
  | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null

sudo apt update
sudo apt install -y gh

gh auth login           # HTTPS + browser is fine
gh auth status          # confirm
```

If the source-list file ever ends up split across two lines (apt complains "Malformed entry 1"), collapse it with `sudo sed -i ':a;N;$!ba;s/\n */ /g' /etc/apt/sources.list.d/github-cli.list` and re-run `sudo apt update`.

WSL note: `gh auth login` defaults to a browser flow. If `xdg-open` can't launch the Windows browser, pick "Login with a web browser" anyway — `gh` prints a one-time code, and you open the URL manually in Windows.

After step 5 you can `claude` to launch the agent. For an agent session that doesn't pause on every tool call, use `claude --dangerously-skip-permissions` (or `claude -c --dangerously-skip-permissions` to continue the last session). Only run this on a checkout you trust — it disables the per-tool prompt that lets you veto destructive commands. For Playwright MCP (browser-driven SPA debugging) see the dedicated section below — it builds on this base.

## WSL2 — running the dev server

Most days `mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false` from the repo root is all you need ([AGENTS.md §8](../AGENTS.md#8-server-lifecycle---start-stop-restart-inspect)). The Maven reactor walks classpath in-tree; Spring Boot binds `*:8051`.

**Don't** `mvn install` and **don't** run jars from `~/.m2/repository/` — they shadow in-reactor `target/classes`. AGENTS.md §5 has the full hard rules.

## Windows-side OpenWebStart — networking caveats

The default WSL2 NAT mode has a quirk: **IPv4 localhost forwarding from Windows to WSL is unreliable, IPv6 (`::1`) forwarding works**. Most tools never notice because they implement [Happy Eyeballs (RFC 8305)](https://datatracker.ietf.org/doc/html/rfc8305) and try both stacks in parallel — Chrome, curl, modern Java all succeed via IPv6.

**OpenWebStart's bundled launcher JRE is Temurin 1.8.0_472 (Java 8)**, whose socket impl picks one stack and sticks with it — IPv4 first, no fallback. So OWS attempts `127.0.0.1:8051`, gets `Connection refused`, and reports the misleading top-level `java.io.IOException: Error fetching file from <jnlp url>`. Easy to misdiagnose as "OWS is broken" when the network is the actual culprit.

### Workarounds (pick one)

**A — launch via the WSL VM IP** (simplest, no Windows changes):

```cmd
wsl hostname -I
:: prints e.g. 172.18.115.21

"C:\Users\<you>\AppData\Local\Programs\OpenWebStart\javaws.exe" http://172.18.115.21:8051/rapla/raplaclient.jnlp
```

The JNLP `codebase` is auto-derived from `request.getServerName()`, so accessing via the IP makes the entire JNLP self-consistent. The IP changes after `wsl --shutdown` or Windows reboot — re-run `wsl hostname -I`.

**B — switch WSL2 to mirrored networking** (sticky, requires Windows 11 22H2+):

`%USERPROFILE%\.wslconfig`:

```ini
[wsl2]
networkingMode=mirrored
```

Then `wsl --shutdown` and reopen WSL. Windows-side `localhost:8051` thereafter forwards into WSL cleanly. Cost: every WSL distro restarts.

**C — Windows port-forward** (admin shell, fragile because WSL IP changes):

```cmd
netsh interface portproxy add v4tov4 listenport=8051 listenaddress=127.0.0.1 connectport=8051 connectaddress=<WSL IP>
```

## Self-signed build — what's automated vs. manual

`mvn -Psign-jks package` now produces a launchable webclient JNLP without hand-patching. Six gotchas the build (or generator) handles, and one that's still on you:

1. **Signs the bundled jars** — `archiveDirectory` in both signing profiles points at `target/classes/static/webclient/`, the path Spring Boot's repackage actually embeds.
2. **Injects `Permissions: all-permissions`** (plus `Codebase: *`, `Application-Name: Rapla`) into every webclient jar's `META-INF/MANIFEST.MF` — `maven-antrun-plugin` execution `add-jnlp-manifest-attributes`. Required by Java Web Start since 7u51 for `<all-permissions/>`.
3. **Includes `slf4j-api` in the JNLP set** — declared with `<scope>runtime</scope>` in `rapla-app/pom.xml` to override the upstream `provided` scope, so `maven-dependency-plugin:copy-dependencies` stages it. Without this, logback dies on `NoClassDefFoundError: org.slf4j.LoggerFactory`.
4. **Strips `META-INF/versions/` from `slf4j-api`** — `maven-antrun-plugin` execution `strip-mr-from-slf4j-api`. icedtea-netx 1.x's signing verifier rejects the MR `module-info.class` entry as unsigned, failing `<all-permissions/>` with "Cannot grant permissions to unsigned jars" (11 other multi-release jars in the set are unaffected — only slf4j-api triggers it). OWS 1.13+ handles MR correctly so this strip is a parity gesture for legacy launchers.
5. **Injects `rapla.download.url`** as a `<property>` in the JNLP — `RaplaJNLPPageGenerator` emits the server root URL (host:port, **without** the `/rapla/` context path — the REST proxy adds that). `ClientConfig.java:131` reads it; fixes the localhost-fallback that otherwise made REST calls miss the server when launched from a remote codebase. Emitting the full codebase doubles the path → `/rapla/rapla/auth/login` → 401.
6. **Icon URL has a single slash** — `RaplaJNLPPageGenerator.java:177-178` strips the redundant leading `/` so it doesn't 401 against Spring Security's `/webclient/**` whitelist. Tier-1 regression tests cover it.

**Still manual: the LaunchedClassLoader workaround** ([PRD 018](prd/018-fat-jar-classloader-defect.md)). Spring Boot 4.0.6's nested-jar classloader fails on lazy class loads under the fat JAR's launcher. Mitigation: `unzip` the fat JAR and run it with a flat classpath (or `java -Djarmode=tools -jar X.jar extract` followed by running the inner JAR). Until upstream fixes this, every deployment needs an extract step.

## Self-sign + launch (one-shot)

```bash
# 1. Generate dev keystore (one time)
keytool -genkeypair -alias rapla -keystore /tmp/rapla-selfsigned.jks \
  -storetype JKS -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 365 \
  -storepass changeit -keypass changeit \
  -dname "CN=Rapla Dev (self-signed), O=Rapla, C=DE"

# 2. Build with signing — the pom.xml does the manifest patching, slf4j-api inclusion,
# and slf4j MR-strip automatically.
mvn -pl rapla-app -am package -DskipTests -Psign-jks \
  -Dkeystore.file=/tmp/rapla-selfsigned.jks \
  -Dkeystore.alias=rapla -Dkeystore.password=changeit -Dkeystore.keypass=changeit

# 3. Extract for the LaunchedClassLoader workaround (PRD 018) — still needed.
rm -rf /tmp/rapla-flat && mkdir /tmp/rapla-flat && cd /tmp/rapla-flat
unzip -q /home/chris/git/rapla/rapla-app/target/rapla-2.1-SNAPSHOT.jar

# 4. Run with flat classpath
mkdir -p /tmp/rapla-flat/logs
CP="BOOT-INF/classes:$(printf '%s:' BOOT-INF/lib/*.jar | sed 's/:$//')"
nohup java -cp "$CP" \
  -Dserver.tomcat.accesslog.enabled=true \
  -Dserver.tomcat.accesslog.directory=/tmp/rapla-flat/logs \
  -Dserver.tomcat.accesslog.prefix=access -Dserver.tomcat.accesslog.suffix=.log \
  -Dserver.tomcat.accesslog.pattern='%h %t "%r" %s %b "%{User-Agent}i"' \
  -Dserver.tomcat.accesslog.buffered=false \
  org.rapla.server.spring.RaplaSpringBootApplication \
  > /home/chris/git/rapla/logs/rapla.log 2>&1 < /dev/null &
disown
```

## Launching from Windows OWS

```cmd
wsl hostname -I
:: e.g. 172.18.115.21

rmdir /s /q "%USERPROFILE%\.cache\icedtea-web\cache"
"%LOCALAPPDATA%\Programs\OpenWebStart\javaws.exe" http://172.18.115.21:8051/rapla/raplaclient.jnlp
```

First launch downloads ~200 MB of Temurin 21 (one-time, cached at `%USERPROFILE%\.cache\icedtea-web\jvm-cache\`). Then OWS prompts to trust the self-signed publisher → click "Always trust" → cert is stored at `%USERPROFILE%\.config\icedtea-web\security\trusted.certs`. Subsequent launches skip the prompt.

If OWS reports any error and you can't tell what failed, the **real** log is at:

```
%USERPROFILE%\.config\icedtea-web\log\
```

`itw-Cjavaws-N.log` files (numbered per launch) hold the full JNLP-parse / fetch / signing / classloader trace. The `Caused by:` chain there is hugely more useful than the top-level "Error fetching file" wrapper that surfaces in the GUI.

## Watching the WSL server live during a Windows OWS test

```bash
tail -f /tmp/rapla-flat/logs/access.2026-05-10.log
```

Three diagnostic shapes:

| What you see | Meaning |
|---|---|
| Nothing during retry | OWS isn't reaching the server — networking issue (see "Windows-side OpenWebStart" above) |
| One `GET .../raplaclient.jnlp` then nothing | OWS got the JNLP, then bailed on parse / signing / cert-trust |
| ~120 entries (HEAD+GET pairs for 63 jars) followed by `POST /rapla/auth/login 200` | Full launch succeeded |

A `POST /rapla/rapla/auth/login 401` (note doubled `/rapla/`) is the path-doubling bug from item 5 above — make sure your build has the JNLP generator that emits the property without `/rapla/`.

## Playwright MCP — install (browser-driven SPA debugging)

For *how* an agent uses Playwright MCP to debug/prototype the SPA, see the `angular-frontend` skill. This section covers the one-off install on WSL2 Ubuntu.

```bash
# 1. System libs Playwright's bundled Chromium needs. Not present by default on WSL2.
npx playwright install-deps   # apt-installs via sudo

# 2. Browser binaries (~150 MB Chromium + optional Firefox / WebKit). Cached in ~/.cache/ms-playwright.
npx playwright install

# 3. Register the MCP server with Claude Code, scoped to this repo.
cd /home/chris/git/rapla
claude mcp add playwright npx '@playwright/mcp@latest' -- --browser chromium

# 4. Restart Claude Code (or use /mcp in-session) so the new tools surface as mcp__playwright__browser_*.
```

WSL2 specifics:

- **Headless mode works anywhere.** Add `-- --headless --isolated` to the `claude mcp add` line for the unattended default. Drop `--isolated` if you want auth cookies to survive restarts.
- **Headed mode needs WSLg** (default on Windows 11). On Windows 10, set up VcXsrv + `DISPLAY=:0` or stay headless.
- **Networking:** WSL2 `localhost` is host loopback for the Playwright subprocess. `http://localhost:4200` (ng serve) and `http://localhost:8051` (Spring Boot) resolve correctly.

Once installed, `claude mcp list` should show `playwright: … ✓ Connected`. Per-session artefacts (screenshots, YAML snapshots, console logs) land in `.playwright-mcp/` and are gitignored.

## Known JNLP defects

| # | Defect | Status |
|---|---|---|
| 1 | `archiveDirectory` in signing profiles pointed at staging dir | Fixed (`rapla-app/pom.xml`) |
| 2 | `Permissions: all-permissions` not added to jars | Fixed (antrun `add-jnlp-manifest-attributes`) |
| 3 | `slf4j-api` excluded from JNLP set (`provided` scope) | Fixed (explicit `runtime` scope in `rapla-app/pom.xml`) |
| 4 | `slf4j-api` MR `module-info.class` rejected by icedtea-web 1.x signing verifier | Fixed (antrun `strip-mr-from-slf4j-api`) |
| 5 | `rapla.download.url` system property not injected; client falls back to `localhost` | Fixed (`RaplaJNLPPageGenerator.java`) |
| 6 | Doubled-slash icon URL → 401 from Spring Security | Fixed (with regression test) |
| 7 | `<jar main="true">` matcher doesn't match versioned `rapla-client-2.1-SNAPSHOT.jar` | Fixed (regex matcher + tests) |
| 8 | JNLP `Content-Type: charset=ISO-8859-1` vs body declares UTF-8 | Open (cosmetic; tolerated by all parsers we tested) |

The two hardcoded localhost defaults in `org.rapla.client.spring` (`ClientConfig.java:131`, `ClientProxyConfig.java:54`) are migration-era regressions worth a follow-up: ideal flow is `BasicService.getCodeBase()` (the standard JNLP "where was I launched from?" API) before falling back to localhost. Item 5 above papers over this via `<property>` injection — works, but a `BasicService` fallback would survive an absent or hand-edited JNLP.
