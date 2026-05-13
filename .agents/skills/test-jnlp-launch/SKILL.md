---
name: test-jnlp-launch
description: |
  Use when you want to verify the Spring Boot 4 deployable launches end-to-end via a real JNLP launcher (icedtea-netx in WSL, or OpenWebStart on Windows) — including self-signing for testing and the WSL2/Windows networking caveats. Skip when the user just wants to test the fat JAR boots and serves HTTP — that's `test-deployment`. Skip when the user is doing client-side work without JNLP — that's `mvn exec:java`, covered by the `swing-client-launch` skill.
---

# Verifying the JNLP / OWS launch end-to-end

The vanilla `mvn -pl rapla-app -am package -DskipTests` produces a fat JAR that **does not launch** in any modern Java Web Start client, even with `-Psign-jks`. This skill walks the workarounds — both the build-side gaps (until they're codified) and the WSL2↔Windows network gap.

The full background is in [`docs/development.md`](../../../docs/development.md). This skill is the action checklist.

## When to reach for it

- Verifying that a code change to the JNLP generator (`RaplaJNLPPageGenerator`) or signing profiles still launches under OWS
- Reproducing a Windows OWS bug report on WSL
- Pre-handoff smoke test before tagging a deployable

Do NOT use for plain "does the fat JAR boot" — `test-deployment` skill is faster and doesn't need keystore/sign/extract.

## Prerequisites

- `keytool`, `jarsigner`, `jar` (from any Java 21 JDK — the SDKMAN one is fine)
- `unzip`, `zip`
- Optional: `apt install icedtea-netx` for `javaws` in WSL — beware its 1.x signing verifier rejects multi-release `module-info.class` entries (item 4 below). For a more representative test, install OpenWebStart Linux 1.13+ via the .deb from the [karakun releases page](https://github.com/karakun/OpenWebStart/releases) — provides `/opt/OpenWebStart/javaws`.

## One-shot self-sign + launch script

```bash
# 1. Dev keystore (once per machine)
keytool -genkeypair -alias rapla -keystore /tmp/rapla-selfsigned.jks \
  -storetype JKS -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 365 \
  -storepass changeit -keypass changeit \
  -dname "CN=Rapla Dev (self-signed), O=Rapla, C=DE"

# 2. Build with signing — pom.xml does the manifest patching, slf4j-api inclusion,
# and slf4j MR-strip automatically. ~2 minutes; ~63 archives signed.
cd /home/chris/git/rapla
mvn -pl rapla-app -am package -DskipTests -Psign-jks \
  -Dkeystore.file=/tmp/rapla-selfsigned.jks \
  -Dkeystore.alias=rapla -Dkeystore.password=changeit -Dkeystore.keypass=changeit

# 3. Extract for the LaunchedClassLoader workaround (PRD 018) — still needed,
# Spring Boot 4 nested-jar loader is broken for lazy class loads. No upstream fix.
rm -rf /tmp/rapla-flat && mkdir /tmp/rapla-flat && cd /tmp/rapla-flat
unzip -q /home/chris/git/rapla/rapla-app/target/rapla-2.1-SNAPSHOT.jar
mkdir -p logs

# 4. Run with flat classpath
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

## Verify before launching the JNLP launcher

```bash
# server alive
[ -f /home/chris/git/rapla/logs/rapla.pid ] || sleep 10  # let it start
curl -sf -o /dev/null -w 'JNLP via WSL IP -> %{http_code}\n' "http://$(hostname -I | awk '{print $1}'):8051/rapla/raplaclient.jnlp"

# JNLP advertises rapla.download.url WITHOUT /rapla/ context-path
curl -s "http://$(hostname -I | awk '{print $1}'):8051/rapla/raplaclient.jnlp" \
  | grep 'rapla.download.url'
# expect: <property name="rapla.download.url" value="http://<IP>:8051/"/>
# wrong:  ...value="http://<IP>:8051/rapla/"... → POST goes to /rapla/rapla/auth/login → 401

# every webclient jar signed by single identity, with Permissions: attribute
for j in /tmp/rapla-flat/BOOT-INF/classes/static/webclient/*.jar; do
  jarsigner -verify "$j" 2>/dev/null | grep -q 'jar verified' || echo "UNSIGNED: $(basename $j)"
  unzip -p "$j" META-INF/MANIFEST.MF 2>/dev/null | grep -q '^Permissions: all-permissions' \
    || echo "MISSING Permissions: $(basename $j)"
done
```

## Launch via icedtea-netx (WSL, fastest local check)

```bash
# Trust the self-signed cert so we don't deal with the cert-warning dialog
mkdir -p /home/chris/.config/icedtea-web/security
keytool -exportcert -keystore /tmp/rapla-selfsigned.jks -storepass changeit -alias rapla \
  -file /tmp/rapla-selfsigned.cer -rfc
keytool -importcert -keystore /home/chris/.config/icedtea-web/security/trusted.certs \
  -storepass changeit -alias rapla-self -file /tmp/rapla-selfsigned.cer -noprompt 2>&1 \
  | grep -v 'proprietary format' || true

# Launch — strict mode (no -nosecurity)
javaws "http://$(hostname -I | awk '{print $1}'):8051/rapla/raplaclient.jnlp"
```

Watch `tail -f /tmp/rapla-flat/logs/access.2026-05-10.log` (substitute the current date). Healthy launch shape: `GET .../raplaclient.jnlp 200` once, then ~120 `HEAD/GET .../webclient/*.jar 200` entries, then `POST .../auth/login 200`.

## Launch via OpenWebStart (closer to production, supports MR jars natively)

```bash
# If you skipped 4c (MR strip), OWS Linux still works because icedtea-web 2.x handles MR correctly.
# But step 4c stays in the script for icedtea-netx parity.
/opt/OpenWebStart/javaws "http://$(hostname -I | awk '{print $1}'):8051/rapla/raplaclient.jnlp"
```

OWS downloads its own Temurin runtime on first launch (~200 MB, cached at `~/.cache/icedtea-web/jvm-cache/`). Subsequent launches reuse it.

## Windows OWS — caveats

When the JNLP launcher is on Windows and the server is in WSL:

1. **Use the WSL IP, not `localhost`.** OWS bundles Java 8 whose socket impl uses IPv4 first with no Happy Eyeballs fallback. WSL2's IPv4 localhost-forwarding is broken (IPv6 works, but Java 8 doesn't try IPv6). `wsl hostname -I` gives the IP; pass it to `javaws.exe`.

2. **Clear the OWS cache between attempts** if you've changed anything in the JNLP/jars:

   ```cmd
   rmdir /s /q "%USERPROFILE%\.cache\icedtea-web\cache"
   ```

3. **Real OWS log** lives at `%USERPROFILE%\.config\icedtea-web\log\itw-Cjavaws-N.log`, NOT in stdout/stderr (install4j launcher forks). Read the most recent file when diagnosing — the GUI's "Error fetching file" wrapper hides the actual `Caused by:` chain.

4. **Common Windows OWS failure shapes**:

   | Symptom in itw-Cjavaws-*.log | Cause | Fix |
   |---|---|---|
   | `java.net.ConnectException: Connection refused` from `DualStackPlainSocketImpl` | WSL2 IPv4 localhost gap | Launch via WSL IP (item 1) |
   | `Cannot grant permissions to unsigned jars` | A jar in the set isn't signed, OR icedtea-netx-1.x's MR-handling for slf4j-api | Verify all jars `jarsigner -verify`, ensure step 4c ran |
   | `NoClassDefFoundError: org.slf4j.LoggerFactory` after main() | slf4j-api missing from JNLP | Step 4b/d ran? curl JNLP, grep slf4j-api |
   | `POST /rapla/rapla/auth/login 401` in our access log | `rapla.download.url` JNLP property has `/rapla/` context-path appended | RaplaJNLPPageGenerator emits root URL, not codebase |

## Hard rules

- **Never sign with a different identity than the existing one** without first stripping the prior signatures (`zip -d X.jar 'META-INF/*.SF' 'META-INF/*.RSA' ...`). Mixed signers cause OWS to refuse the launch under `<all-permissions/>`. The script above strips signatures before re-signing for exactly this reason.
- **Don't run `mvn package` while a `java -jar` instance of the previous artifact is running** — `spring-boot:repackage` writes to the same JAR you'd be reading.
- **The `Permissions: all-permissions` step (4a) wipes the `META-INF/INDEX.LIST` if any jar had one** (jar tool rebuilds the index). Tolerated by every JNLP launcher we tested.

## Cleanup

```bash
# stop server
[ -f /home/chris/git/rapla/logs/rapla.pid ] && kill "$(cat /home/chris/git/rapla/logs/rapla.pid)"
# remove extracted layout
rm -rf /tmp/rapla-flat
# keystore + cert may stay for next test
```

## Related

- [`AGENTS.md` §8](../../../AGENTS.md) — dev server lifecycle (the path you use most days)
- [`swing-client-launch` skill](../swing-client-launch/SKILL.md) — Swing client without JNLP (`mvn exec:java` — fastest end-to-end client check)
- [`docs/development.md`](../../../docs/development.md) — long-form companion to this skill, including the WSL2↔Windows networking deep-dive
- [PRD 018](../../../docs/prd/018-fat-jar-classloader-defect.md) — Spring Boot 4.0.6 LaunchedClassLoader defect that forces the extract-and-run shape used in step 3
- [`test-deployment` skill](../test-deployment/SKILL.md) — fat-JAR smoke test without signing or OWS
