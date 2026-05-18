# PRD 018: Spring Boot 4.0.6 Fat-JAR Classloader Defect — Extract-and-Run Workaround

**Status:** draft
**Date:** 2026-05-10

## Goal

Make the deployable `rapla-app/target/rapla-2.1-SNAPSHOT.jar` runnable in production. As of Spring Boot 4.0.6, running it directly via `java -jar` collapses the HTTP layer minutes after start, on real-world request shapes (h2c upgrade probes, Tomcat error paths, JNLP launcher traffic). The dev path (`mvn spring-boot:run`) is unaffected.

## Background — what's broken

The fat JAR builds green, boots to `Started RaplaSpringBootApplication in ~9 s`, serves the first few requests fine, then begins logging:

```
ERROR o.a.coyote.http11.Http11NioProtocol - Error reading request, ignored
java.lang.NoClassDefFoundError: org.apache.tomcat.util.http.parser.TokenList
    at org.apache.coyote.http11.Http11Processor.isConnectionToken(Http11Processor.java:1098)
    at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:332)
    at o.a.t.u.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1801)
    ...
Caused by: java.lang.ClassNotFoundException: org.apache.tomcat.util.http.parser.TokenList
    at java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:574)
    at o.s.boot.loader.net.protocol.jar.JarUrlClassLoader.loadClass(JarUrlClassLoader.java:107)
    at o.s.boot.loader.launch.LaunchedClassLoader.loadClass(LaunchedClassLoader.java:91)
```

Each affected exec thread dies. After enough deaths, Tomcat's exec pool is exhausted, TCP connects are accepted but never serviced, and `curl` hangs. The class **is** in the fat JAR (`BOOT-INF/lib/tomcat-embed-core-11.0.21.jar/org/apache/tomcat/util/http/parser/TokenList.class`) and the jar **is** listed in `BOOT-INF/classpath.idx`. The defect is in the runtime classloader, not the packaging.

The exception that gets logged in our heap can be any class loaded lazily (`TokenList` for Tomcat parser, `ThrowableProxy` for Logback's exception wrapper, etc.). Whichever class hits the path first is the one that fails first; the class histogram for a healthy run shows e.g. `[Lch.qos.logback.classic.spi.ThrowableProxy;` (the array type) loaded but `ThrowableProxy` itself absent — proving classes are still being loaded lazily on demand and that lazy load is what's broken.

## Reproduction (canonical)

After `mvn -pl rapla-app -am package -DskipTests` and `java -jar rapla-app/target/rapla-2.1-SNAPSHOT.jar &`:

```bash
( printf 'GET /rapla/raplaclient.jnlp HTTP/1.1\r\nHost: localhost\r\nUpgrade: h2c\r\nConnection: Upgrade, HTTP2-Settings\r\nHTTP2-Settings: AAMAAABkAARAAAAAAAIAAAAA\r\n\r\n'; sleep 0.05 ) | nc localhost 8051
grep NoClassDefFoundError logs/rapla.log
```

→ first NCDFE arrives within ~1 s. Pool exhaustion follows over the next minutes as more lazy loads hit the same broken loader.

The same flat-classpath run **does not exhibit the bug**:

```bash
unzip -q rapla-app/target/rapla-2.1-SNAPSHOT.jar -d /tmp/rapla-flat
cd /tmp/rapla-flat
CP="BOOT-INF/classes:$(printf '%s:' BOOT-INF/lib/*.jar | sed 's/:$//')"
java -cp "$CP" org.rapla.server.spring.RaplaSpringBootApplication
```

→ same h2c probe returns HTTP/1.1 200 cleanly; server stays healthy.

## Suspected cause — Spring Boot nested-jar loader (UNCONFIRMED)

The failure surfaces through Spring Boot's rewritten nested-jar loader
(`LaunchedClassLoader` + `JarUrlClassLoader` + `jar:nested:`, introduced in
**3.2.0**): a not-yet-loaded class fails to resolve through
`URLClassLoader.findClass` even though the bytes are in `BOOT-INF/lib/` and the
jar is in `BOOT-INF/classpath.idx`. **The root cause has not been confirmed** —
neither pinned by us nor by Spring.

Related upstream issues — note these are *similar-shape* failures, not confirmed
to be the same bug as ours:

- [spring-boot#49341](https://github.com/spring-projects/spring-boot/issues/49341) — a `ClassNotFoundException` out of `JarUrlClassLoader` on Spring Boot 4.0.3 + JDK 25. **Different stack** from ours (`ApplicationContext$DispatchData`, via Tomcat error-page handling) and a different JDK. Closed **for lack of a reproduction** ("Closing due to lack of requested feedback") — *not* won't-fix; the maintainer offered to reopen given a sample. The `extract` snippet in that issue is the **reporter's own workaround**, not a Spring statement.
- [spring-boot#40096](https://github.com/spring-projects/spring-boot/issues/40096) — `NoClassDefFoundError` from `LaunchedClassLoader` when threads are interrupted. **Fixed in 3.2.5** (commits `4203e1f2f`, `9b0593efe` — fall back to `RandomAccessFile` on `ClosedByInterruptException`).
- [spring-boot#38719](https://github.com/spring-projects/spring-boot/issues/38719) — same loader chain on 3.2 with virtual threads. Duplicate of #38611, **fixed in 3.2.1**.
- [spring-boot#31853](https://github.com/spring-projects/spring-boot/issues/31853) — 2.x-era CNFE in nested jars under GC pressure (different, older loader). **Fixed in 2.6.11**.

We hit our failure on Spring Boot 4.0.6 + JDK 21.0.11. **Every diagnosed bug in
this family above was fixed in a patch release, and those fixes are present in
4.0.6.** So our failure is either a *new/distinct trigger* the existing fixes
don't cover, or a *regression* — unconfirmed. It is **not** established that
this is #49341, nor that the JDK version is causal (the #49341 reporter happened
to be on JDK 25; we are on 21).

## Plan — extract-and-run

Extracting the fat JAR before launching is **observed to avoid the failure** in
our testing. It is an **empirical mitigation**, not a Spring-documented fix for
this symptom: Spring's docs recommend extraction for *startup performance* and
*container-image packaging*, and Spring's response to every *diagnosed* loader
bug (#38611, #40096, #31853) was a code fix in a patch release — not "extract".

```bash
java -Djarmode=tools -jar rapla-2.1-SNAPSHOT.jar extract --destination ./extracted --force
java -jar ./extracted/rapla-2.1-SNAPSHOT.jar
```

The inner JAR after extraction has a flat classpath via `Class-Path:` entries in its manifest, bypassing `LaunchedClassLoader` entirely. `spring-boot-jarmode-tools-4.0.6.jar` is already bundled in `BOOT-INF/lib/` of our fat JAR, so no plugin/version changes needed.

**Implementation steps:**

1. **`rapla-app/src/main/distribution/bin/rapla` launcher script** (the entry point in the binary distribution per PRD 003 §"Distribution archive"):
   - On first start, `mkdir -p $RAPLA_HOME/extracted`, run `java -Djarmode=tools -jar rapla-X.Y.jar extract --destination $RAPLA_HOME/extracted --force` if the extracted directory is empty or older than the fat JAR.
   - Invoke `java -jar $RAPLA_HOME/extracted/rapla-X.Y.jar "$@"` instead of the fat JAR directly.
   - Ship the same fat JAR in the distribution archive — no Maven build changes needed. Extraction is purely a launcher concern.

2. **Update the `test-deployment` skill** (`.claude/skills/test-deployment/SKILL.md`) to test the extracted layout, not raw `java -jar`. The §"Run it" snippet currently uses `java -jar rapla-app/target/rapla-2.1-SNAPSHOT.jar` and will collapse on the first OWS-style traffic; replace with extract-then-run.

3. **AGENTS.md §8** is unaffected — the dev server uses `mvn spring-boot:run`, which doesn't go through `LaunchedClassLoader`. No change needed.

4. **`docs/prd/003-custom-deployments-after-spring-migration.md`** — add a one-line cross-reference under §"Distribution archive" pointing here. The dhbwrapla deployable is a Spring Boot fat JAR too (PRD 003 §1.5 direction change) — same defect, same workaround.

## Tests

- **Regression script** in `rapla-app/src/test/scripts/` (or wherever the existing deployment-smoke checks live, if any): builds the fat JAR, extracts via jarmode tools, boots the extracted JAR, fires the h2c-upgrade reproduction probe, asserts that no `NoClassDefFoundError` lands in the log within 10 s. Tagged `e2e` per AGENTS.md §10 so it doesn't run in the default `mvn test` lane.
- Optional: a second regression that boots the **fat JAR directly** and asserts the failure still reproduces — so we'll know upstream when the Spring Boot defect is fixed (the reproduction will start failing) and can drop the extract step.

## Open Questions

1. **Should we file or comment on a Spring Boot upstream issue?** #49341 is closed-not-planned but the duplicate trail (#38719 → ?) suggests there may be a more general unfixed tracker. Worth a minimal repro and a fresh comment so the team has visibility into another reproducible trigger (h2c upgrade) — currently their evidence is mostly virtual-threads / error-page paths.
2. **Distribution shape — extract at install time vs. start time?** Start-time extraction lets the distribution archive stay JAR-only (matches what users expect). Install-time extraction (deploy a directory, not a JAR) is closer to a traditional Tomcat install. Decide before writing the launcher script.
3. **dhbwrapla coordination.** PRD 003 §1.5 says the dhbwrapla deployable is its own fat JAR re-exporting rapla-app's webclient/ via `META-INF/resources`. That deployable hits the same defect. Coordinate the launcher fix or push the workaround into a shared distribution layout. Out of scope for this PRD's first cut, but flag in PRD 003.
4. **OWS-side symptom (resolved diagnosis, separate concern):** during repro the user saw `java.io.IOException: Error fetching file from http://localhost:8051/rapla/raplaclient.jnlp` from OpenWebStart. Server log showed zero requests from OWS, suggesting OWS bailed before any HTTP exchange. Comparison against the working production JNLP at `https://rapla.org/rapla-demo/rapla/raplaclient.jnlp` confirmed the cause: **unsigned jars + `<all-permissions/>` is hard-blocked by OWS** (see karakun/OpenWebStart#181, IcedTea-Web#915, [OWS FAQ](https://openwebstart.com/docs/FAQ.html)). The "Error fetching file" wording is OWS's IOException wrapper when it abandons a launch; the underlying refusal is "Cannot grant permissions to unsigned jars." Production works because its jars are signed and served over HTTPS; ours are unsigned. Resolution is to sign with the PKCS#11 profile (`mvn -Psign-pkcs11 package`, `rapla-app/pom.xml:230`) — there is no developer-mode escape hatch in OWS. Unrelated to the classloader defect this PRD addresses.
