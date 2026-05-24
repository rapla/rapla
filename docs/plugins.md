# Writing a rapla server plugin

A rapla server plugin is a single jar dropped into `./plugins/` next to the
deployed `rapla-*.jar`. On restart, rapla discovers it via the classpath, runs
its Spring Boot `@AutoConfiguration`, and the plugin's REST endpoints, beans,
scheduled jobs, etc. become part of the running server.

No rebuild of rapla. No custom `@SpringBootApplication`. No touching the
operator's installed JAR.

This is **PRD 045 §4 + Phase 5+6**. Read [`prd/045-end-user-deployment-and-db-config.md`](prd/045-end-user-deployment-and-db-config.md)
for the design rationale and trade-offs.

## What an operator does

```bash
# install once
sudo cp rapla-plugin-acme-sso-1.0.jar /opt/rapla/plugins/
sudo systemctl restart rapla
```

That's it. The plugin is gated by `rapla.plugins.<id>.enabled` in
`config/application.yml` (defaults to `true` — drop it in, it runs):

```yaml
rapla:
  plugins:
    acme-sso:
      enabled: false   # leave the jar in place, but don't load it
```

## What you (the plugin author) write

### 1. A jar with three things

```
rapla-plugin-acme-sso-1.0.jar
├── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
├── com/acme/raplaplugin/AcmeSsoAutoConfiguration.class
├── com/acme/raplaplugin/AcmeAuthenticationStore.class
└── com/acme/raplaplugin/AcmeRestController.class
```

### 2. An `@AutoConfiguration` class in your own package

```java
package com.acme.raplaplugin;

import org.rapla.server.spring.RaplaServerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration(after = RaplaServerAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "rapla.plugins",
    name = "acme-sso.enabled",
    matchIfMissing = true)
@ComponentScan("com.acme.raplaplugin")
public class AcmeSsoAutoConfiguration { }
```

Three things to notice:

- **`after = RaplaServerAutoConfiguration.class`** guarantees stock rapla beans
  (`RaplaFacade`, `PermissionController`, the storage operator) exist before
  your plugin loads, so you can `@Autowired` them.
- **`@ConditionalOnProperty(matchIfMissing = true)`** is the on/off switch.
  Without it, an operator who wants to keep the jar but disable the plugin
  must delete the jar.
- **`@ComponentScan("com.acme.raplaplugin")`** picks up your `@RestController`,
  `@Service`, etc. **NEVER scan `org.rapla.*`** — that would double-register
  stock rapla beans and crash the context at boot.

### 3. The `AutoConfiguration.imports` marker

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.acme.raplaplugin.AcmeSsoAutoConfiguration
```

One line per autoconfig class. Spring Boot aggregates this file from every jar
on the classpath at boot — that's how your plugin is discovered.

### 4. Your `pom.xml`

```xml
<dependencies>
    <dependency>
        <groupId>org.rapla</groupId>
        <artifactId>rapla-server</artifactId>
        <version>2.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    <!-- transitively pulls rapla-core, plus the Spring Boot stack -->
</dependencies>
```

`<scope>provided</scope>` means the deployable provides `rapla-server` and
`rapla-core` at runtime — your jar must not bundle them again. The plugin jar
is just *your* code.

Bundle any third-party deps your plugin needs (LDAP libs, an OAuth client,
whatever) as `compile` scope — they ship in your jar.

## Package convention

| | Required |
|---|---|
| **Plugin package starts with anything except `org.rapla.`** | YES |
| Recommended: `<vendor>.raplaplugin.*` (e.g. `com.acme.raplaplugin`, `de.dhbw.raplaplugin`) | recommended |

Plugins inside `org.rapla.*` would be picked up by stock rapla's
`@ComponentScan`s (in `rapla-client` and various Spring configs) and clash
with stock beans. The boot will fail loudly, but better to avoid the trap.

## REST endpoints — the `/api/` contract

Plugin `@RestController`s must map under `/api/<your-prefix>/...`. Per
[AGENTS.md §15](../AGENTS.md#15-rest-endpoints-live-under-api--literal-prefix-on-the-httpexchange-interface)
every rapla REST endpoint is `/api/` namespaced.

```java
@RestController
@RequestMapping("/api/acme/sso")
public class AcmeRestController {
    @PostMapping("/login") AcmeSsoResponse login(@RequestBody AcmeSsoRequest req) { ... }
}
```

**At runtime, the server logs a WARN for any plugin handler mapped outside
`/api/`.** The handler still works, but it won't appear in the SpringDoc-
generated SPA TypeScript client and won't get the standard `/api/` Spring
Security configuration. The warning surfaces the drift at boot rather than
at first 401. See
[`PluginApiPathWarningListener`](../rapla-server/src/main/java/org/rapla/server/spring/plugin/PluginApiPathWarningListener.java).

**Why a runtime warning, not a hard rejection?** Plugin jars don't go through
`ApiPrefixArchitectureTest` (the build-time enforcer for stock rapla). Hard
rejection would let a plugin author break operators with no recourse. WARN +
docs is the chosen trade-off.

## Wiring rapla beans into your plugin

```java
@Service
public class AcmeAuthenticationStore implements AuthenticationStore {

    private final RaplaFacade facade;
    private final PermissionController permissions;

    public AcmeAuthenticationStore(RaplaFacade facade, PermissionController permissions) {
        this.facade = facade;
        this.permissions = permissions;
    }
    ...
}
```

Constructor injection per [AGENTS.md §4](../AGENTS.md#4-code-style). The
`after = RaplaServerAutoConfiguration.class` ordering guarantees these beans
exist when your plugin's constructor fires.

## Trust model

A jar in `./plugins/` runs with **full server privileges** — arbitrary code
execution by design. Only install plugins you trust.

There is no signature verification today. If your deployment needs it,
implement a launch-script preflight that runs `jarsigner -verify` over
`./plugins/*.jar` before starting the JVM.

## API stability

**There is no plugin API stability guarantee across rapla versions.** Rapla
does not freeze a plugin-facing surface; internal refactors can rename, move,
or remove the classes you compile against.

You must:
- **Pin your plugin's `<dependency>` version** to a specific rapla release.
- **Recompile and re-test your plugin against every rapla version** before
  upgrading the deployed rapla JAR.
- **Treat a rapla minor-version bump as a plugin rebuild trigger** — even if
  your code didn't change, the compiled bytecode may reference symbols that
  moved.

A plugin compiled against rapla 2.1 may `NoSuchMethodError` at runtime against
rapla 2.2. The failure is loud (boot stops with a stack trace) but late
(it's on the deployed server, not in your build). Catch it early by running
`mvn test` against each new rapla release before publishing your plugin
for that release.

This is a known trade-off and the conscious cost of the drop-in model — see
PRD 045 §4 ("Why drop-in, not PRD 003's custom deployable?") and the
"API stability guarantee" entry in PRD 045's Open Questions for the
rationale and the path to a frozen surface if it ever becomes worth the
maintenance commitment.

## Frontend (Angular) plugins

If your plugin needs an Angular view (not just REST), it can ship the SPA
remote in the **same jar** under `static/plugins/<id>/`. Spring Boot serves
`static/` from every classpath jar — so your jar becomes both a server plugin
and a Native Federation frontend remote in one artifact.

Owned by [PRD 047](prd/047-angular-frontend-plugin-model.md). Read it before
shipping a frontend-bearing plugin.

## Example

A complete minimal plugin lives at [`docs/examples/plugin-hello/`](examples/plugin-hello/).
Copy it, change the package + group, build, drop the jar into your rapla
`./plugins/`. Curl `http://localhost:8051/api/hello` to confirm it loaded.
