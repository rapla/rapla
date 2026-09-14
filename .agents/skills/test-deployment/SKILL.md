---
name: test-deployment
description: Use when the user wants to test the deployable Spring Boot fat JAR (`mvn package` artifact) — verifying that `java -jar rapla-2.1-SNAPSHOT.jar` boots, that the JNLP webclient/ jars are present and signed correctly, and that the bundled distribution archive looks right. Skip for routine dev work; the `mvn spring-boot:run` dev path in AGENTS.md §8 is faster and doesn't need any of this.
---

# Testing the deployable rapla fat JAR

This skill covers what `java -jar rapla-app/target/rapla-2.1-SNAPSHOT.jar` actually ships — the production-shape artifact, not the dev `mvn spring-boot:run` path that AGENTS.md §8 documents. Reach for this when:

- A change touches `rapla-app/pom.xml` (assembly, signing, packaging plugins, `spring-boot-maven-plugin` config)
- A change touches `rapla-app/src/assembly/` or `rapla-app/src/main/distribution/`
- A change touches the JNLP webclient/ staging (`maven-dependency-plugin:copy-dependencies`, `maven-resources-plugin` bundling into `static/webclient/`, `clientlibs.properties` antrun)
- A change touches the signing profiles (`sign-jks`, `sign-pkcs11`)
- A new dependency is added that might end up in the JNLP launch set (rapla-core, rapla-client, or transitive pure-Java deps the Swing client needs)
- Pre-handoff verification before a tag

## Build the deployable

```bash
mvn -pl rapla-app -am package -DskipTests
```

~30 s. Produces:

- `rapla-app/target/rapla-2.1-SNAPSHOT.jar` — the Spring Boot fat JAR (~40 MB). `<finalName>` keeps the filename stable for deployer scripts (PRD 005 OQ2).
- `rapla-app/target/webclient/*.jar` — the staged client-side jars (populated by `maven-dependency-plugin:copy-dependencies`, then optionally signed by a signing profile, then bundled into the fat JAR's `static/webclient/`).
- `rapla-app/target/distribution/rapla-2.1-SNAPSHOT.{tar.gz,zip}` — the binary distribution archive (PRD 003).

## Quick sanity check the fat JAR's contents

```bash
# Spring Boot launcher + main class present
unzip -l rapla-app/target/rapla-2.1-SNAPSHOT.jar | grep -E 'JarLauncher|RaplaSpringBootApplication' | head

# Modules bundled under BOOT-INF/lib/
unzip -l rapla-app/target/rapla-2.1-SNAPSHOT.jar | grep -E 'BOOT-INF/lib/rapla-(core|client|server)-' | head

# JNLP webclient/ jars are inside the fat JAR
unzip -l rapla-app/target/rapla-2.1-SNAPSHOT.jar | grep 'static/webclient/' | head

# clientlibs.properties is at the root of classes
unzip -p rapla-app/target/rapla-2.1-SNAPSHOT.jar BOOT-INF/classes/clientlibs.properties
```

Expected: launcher present, all three rapla modules listed, `static/webclient/` populated, `clientlibs.properties` is a non-empty `;`-separated list of jar filenames.

## Run it

Same lifecycle pattern as AGENTS.md §8 (PID file, graceful stop, log inspection), but with `java -jar` instead of `mvn spring-boot:run`. AI agents should use `run_in_background=true` on the start command.

```bash
mkdir -p logs
java -jar rapla-app/target/rapla-2.1-SNAPSHOT.jar \
  > logs/rapla.log 2>&1 &
SERVER_PID=$!
echo $SERVER_PID > logs/rapla.pid
echo "Started, PID=$SERVER_PID"
```

Wait ~10 s, then status check:

```bash
[ -f logs/rapla.pid ] && kill -0 "$(cat logs/rapla.pid)" 2>/dev/null \
  && echo "RUNNING ($(cat logs/rapla.pid))" || echo "NOT RUNNING"
curl -sf -o /dev/null -w '%{http_code}\n' "http://localhost:8051/rapla/raplaclient.jnlp"
```

Stop with the same snippet from AGENTS.md §8 (graceful SIGTERM, 10 s wait, SIGKILL fallback).

## Verify JNLP serves correctly

```bash
# JNLP descriptor itself
curl -s http://localhost:8051/rapla/raplaclient.jnlp | head -30

# Check a webclient/ jar is reachable (use a name from clientlibs.properties)
JAR=$(unzip -p rapla-app/target/rapla-2.1-SNAPSHOT.jar BOOT-INF/classes/clientlibs.properties | tr ';' '\n' | head -1)
curl -sf -o /dev/null -w "$JAR -> %{http_code}\n" "http://localhost:8051/rapla/webclient/$JAR"
```

Expected: JNLP body with `<jar href="webclient/X.jar"/>` entries, each jar reachable with HTTP 200.

## Signing verification (with a signing profile active)

```bash
# Build with a signing profile
mvn -pl rapla-app -am package -DskipTests -Psign-jks
# or for production:
mvn -pl rapla-app -am package -DskipTests -Psign-pkcs11

# Verify each webclient/ jar is signed by the same identity
for jar in rapla-app/target/webclient/*.jar; do
  echo "=== $(basename $jar) ==="
  jarsigner -verify -verbose:summary "$jar" | grep -E 'jar verified|signature|certificate'
done
```

Expected: every jar reports `jar verified` and shows the same signer DN. **Mixed signers cause OpenWebStart to refuse the launch under `<all-permissions/>`** — see PRD 003 §JNLP/Code Signing for the full signing-chain rationale.

### `-Psign-pkcs11` (YubiKey) on WSL — attach from bash, no PowerShell needed

If the PKCS#11 signing step fails with `slotListIndex is 0 but token only has 0 slots`, the YubiKey isn't forwarded to WSL. Drive `usbipd` from the same bash session — `usbipd.exe` is on the Windows PATH and reachable through the WSL interop:

```bash
usbipd.exe list                                  # find the VID 1050:0407 BUSID — typically 2-2
usbipd.exe attach --wsl --busid 2-2              # idempotent; safe to re-run every session
lsusb | grep -i yubi                             # verify the device is now in WSL
opensc-tool --list-readers                       # verify pcscd sees the reader
```

Then resume the build — `-pl rapla-app -am` (not `-rf :rapla-app`, which won't pull in sibling reactor modules):

```bash
mvn -pl rapla-app -am package -DskipTests -Psign-pkcs11   # touch the YubiKey when the 8-second countdown fires
```

Common follow-ups when `attach` succeeds but signing still fails: `sudo systemctl restart pcscd.socket`; or detach + re-attach via `usbipd.exe detach --busid 2-2 && usbipd.exe attach --wsl --busid 2-2`. Full per-session checklist + polkit/opensc one-time setup lives in [`docs/signing.md`](../../../docs/signing.md).

## Distribution archive

```bash
# What's in the distribution tarball
tar -tzf rapla-app/target/distribution/rapla-2.1-SNAPSHOT.tar.gz | head -20

# Sanity: launcher script present and executable in archive
tar -tzvf rapla-app/target/distribution/rapla-2.1-SNAPSHOT.tar.gz | grep -E '\bbin/(rapla|raplaserver)' | head
```

## Hard rules

- **Don't run `mvn package` while `java -jar` is running** — `spring-boot:repackage` writes to the same `rapla-app/target/rapla-2.1-SNAPSHOT.jar` you'd be executing. Stop the server first.
- **Don't keep both `mvn spring-boot:run` (the dev path) and `java -jar` (this path) running at once** in the same checkout — they fight for port 8051. If you need both, use a worktree per §7.
- **Don't sign with a different identity than rapla-core's** without first stripping the existing signatures from each jar (`zip -d X.jar 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/*.EC'`). Mixed signers break JWS launches under `<all-permissions/>`. See PRD 003 §JNLP for the full chain.

## Related

- **AGENTS.md §8** — the dev server lifecycle (`mvn spring-boot:run`, faster, no package step).
- **PRD 003 §JNLP Client and Code Signing** — the architectural rationale for the signing chain and the `static/webclient/` bundling strategy.
- **PRD 005 cleanup E2** — the `maven-dependency-plugin` + `maven-resources-plugin` + `maven-antrun-plugin` wiring in `rapla-app/pom.xml` that produces the staged + bundled webclient/ set.
