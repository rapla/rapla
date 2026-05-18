# PRD 046: Drop-in Server Plugin / Extension Model

**Status:** draft — 2026-05-18
**Date:** 2026-05-18

## Goal

Let a site operator add **server-side** functionality — a REST endpoint, an
`AuthenticationStore`, a scheduled job, a permission extension, a custom bean —
to a **stock rapla deployable** by placing a jar in a `./plugins/` directory
beside the JAR. No rebuild of rapla, no custom `@SpringBootApplication`, no
touching the immutable fat JAR.

This complements, not replaces, **PRD 003**. PRD 003 is the full custom-
deployment model: dhbwrapla rebuilds its *own* Spring Boot deployable (its own
`@SpringBootApplication`, its own pom depending on `rapla-server`). That stays
the right answer for a substantial, version-coupled customization with its own
release cadence. PRD 046 covers the lighter case PRD 003 makes disproportionately
heavy: "I just need one extra endpoint / one custom auth store on an otherwise
stock server."

## Background — current state

There is **no plugin loader today.** Investigated 2026-05-18:

- No `META-INF/services`, no annotation processor, no `ServiceLoader`. The
  legacy `@Extension` / `@DefaultImplementation` / `@ExtensionPoint` system was
  removed in the Spring Boot migration (PRD 001) — see
  `docs/architecture/extension-points.md`.
- Server discovery is **explicit only**: `RaplaServerAutoConfiguration` scans
  exactly `@ComponentScan("org.rapla.server.spring.web")`; everything else is
  wired by hand-written `@Bean` factories in `ServerCoreConfig` /
  `ServerServiceConfig` with hardcoded class names. A class in a jar that
  wasn't part of the build is never discovered.
- No `loader.path`, no `PropertiesLauncher`, no `./plugins/` directory in any
  build or assembly descriptor.

**The one mechanism that already works in our favour:** Spring Boot aggregates
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
from **every jar on the classpath**. rapla-server already ships exactly such a
file to deliver `RaplaServerAutoConfiguration` to a foreign `@SpringBootApplication`
(PRD 003, verified by `AutoConfigImportTest`). A plugin jar carrying its own
`AutoConfiguration.imports` is therefore discovered by the same path the rapla
server stack already relies on — **no new scanning machinery required.**

**Classpath:** PRD 018 establishes that the production launch mode is
extract-and-run on a flat classpath (`java -cp BOOT-INF/classes:BOOT-INF/lib/*`)
because the Spring Boot 4 nested-jar classloader is defective. PRD 045 already
adds an external replaceable `./lib/` glob for JDBC drivers. A `./plugins/*`
glob is the same one-line classpath addition.

So the gap is small and mechanical: a documented plugin **contract** + the
`./plugins/` classpath wiring + boot-ordering + documentation.

## Drop-in plugin vs. custom deployable — trade-offs

The two models are not competitors — they are different points on a
coupling/effort curve. This section records *why* PRD 046 exists alongside
PRD 003 rather than replacing it, and the price the drop-in model pays.

To be precise about the baseline: there is no jar-drop mechanism today. The
status quo is the PRD 003 *custom deployable* — a downstream builds its own
Spring Boot fat JAR (its own `@SpringBootApplication`, its own pom depending on
`rapla-server`). dhbwrapla is the only instance. So the comparison is
*drop-in plugin* vs. *fork-the-deployable*.

**Where drop-in wins:**

- **Decoupled release cadence.** A custom deployable re-runs `mvn package`,
  re-signs the JNLP set, re-tests, and re-ships a whole fat JAR for every rapla
  patch. A drop-in operator updates `rapla-2.1-SNAPSHOT.jar` and the
  `./plugins/` jar is untouched — rapla security patches land with no
  downstream build.
- **Proportionate effort for small extensions.** The PRD 003 setup (a pom
  inheriting `rapla-bom`, the `${rapla.version}` interpolation trap — PRD 003
  line 10, an `@SpringBootApplication`, signing-config inheritance) is wildly
  disproportionate to "one extra `/api/` endpoint." A drop-in plugin is one
  small autoconfig jar.
- **Composability.** Two independent plugins from different vendors (an SSO
  adapter + a reporting endpoint) coexist in `./plugins/`. Under PRD 003 they
  must be merged into one `@SpringBootApplication` — not independently
  developed or shipped.
- **No signing entanglement.** Only `rapla-app` signs the JNLP webclient set
  (PRD 003 §"What gets dropped"). A server-only drop-in plugin never touches
  it. This is exactly why client-side drop-in is out of scope (see Scope).
- **Operator stays on the canonical artifact** — stock rapla's tests, CI
  signal, and support story. A forked deployable is a different binary nobody
  else runs.

**Where the custom deployable wins:**

- **API drift is caught at compile time, not at the customer site.** rapla has
  no frozen public API (OQ3). A drop-in plugin compiled against 2.1's
  `RaplaFacade` can `NoSuchMethodError` at runtime against 2.2 — late, at the
  customer site, after an innocuous rapla upgrade. A custom deployable
  recompiles against the new rapla, so the same drift is a *build error in the
  downstream build* — caught early, by a developer, before shipping. This is
  the drop-in model's single sharpest drawback.
- **No CI guard on plugin code.** `ApiPrefixArchitectureTest`, the §12
  permission-leak tests, the SpringDoc-group check all run in the rapla-app
  build and cannot see plugin jars (Phase 3 manages only a runtime WARN). A
  drop-in plugin can ship an endpoint that leaks data past read scope (§12)
  with nothing in the rapla build stopping it. A custom deployable at least
  runs its own build.
- **Bean *replacement*, not just addition.** Overriding a stock default
  (a custom `AuthenticationStore` replacing the built-in one), reshaping
  `application.yml` defaults, adding a second `DataSource`, or pom-level
  dependency surgery (dhbwrapla's jcifs-ng / jTDS / LDAP deps — PRD 003
  line 10) genuinely needs a build. Drop-in is additive; it is weak at
  replacing.
- **Trust boundary.** A `./plugins/` jar is arbitrary code with full server
  privileges, added to the classpath by a launch-script glob (OQ2). A custom
  deployable at least implies a build and a human in the chain. Drop-in makes
  "untrusted jar runs as the rapla server" a one-step operation.
- **Version-coupling is sometimes correct.** dhbwrapla is deeply coupled to
  rapla internals (Date→LocalDateTime drift, scheduler-API removals — PRD 003
  lines 22-24). For something that intertwined, forced recompile-per-version
  *is* the right discipline; drop-in would only defer that pain to runtime.

**Summary:**

| | Drop-in plugin (046) | Custom deployable (003) |
|---|---|---|
| Best for | additive, shallow, independently-shipped extras | deep, version-coupled, vendor-owned deployments |
| rapla upgrade | jar moves untouched | downstream rebuild every time |
| API drift caught | runtime, at customer site ⚠️ | downstream compile ✅ |
| CI / test guard on the extension | none in rapla's build ⚠️ | downstream's own build ✅ |
| Effort to start | one small jar | full module + pom + app class |
| Bean *replacement* | weak (autoconfig ordering only) | full control ✅ |
| Trust model | arbitrary code, glob-loaded ⚠️ | a build + a human in the chain |

Every drop-in drawback (late API failures, no CI guard, trust) is a direct
consequence of "no rebuild" — the same property that is its whole benefit. The
design tension this PRD must resolve before leaving draft is therefore OQ3:
without an answer on API stability, the model's convenience is paid for in
customer-site `NoSuchMethodError`s.

## Scope

In scope:
- The discovery mechanism: a rapla server plugin **is a Spring Boot
  autoconfiguration jar**.
- The `./plugins/` directory and its classpath wiring (subordinate to PRD 018's
  extract-and-run decision, like PRD 045's `./lib/`).
- The plugin contract: package convention, the `rapla.plugins.<id>.enabled`
  config namespace, autoconfig ordering after `RaplaServerAutoConfiguration`.
- What a plugin author compiles against.
- Documentation: a `docs/plugins.md` author guide + a minimal example plugin.

Out of scope (deferred or owned elsewhere):
- **Client-side (Swing) drop-in plugins.** Swing jars are JNLP-signed in a
  single pass by `rapla-app` (PRD 003 §"What gets dropped"). An unsigned
  operator-supplied Swing jar breaks the signing invariant — out of scope,
  possibly never. Custom Swing code follows PRD 003 (lives in `rapla-client`).
- **Angular SPA extensions** — owned by **PRD 047** (Angular frontend plugin
  model). Note the convergence: a PRD 047 frontend plugin is delivered as a
  jar of exactly this PRD's shape — an `@AutoConfiguration` jar — that
  *additionally* carries `static/plugins/<id>/` Native Federation remote assets
  (Spring Boot serves `static/` from any classpath jar) plus a `RaplaUiRemote`
  `@Bean`. Dropped into `./plugins/`, it adds both a server endpoint and an
  Angular view to a stock deployable. PRD 047 owns that frontend contract; this
  PRD's `./plugins/` classpath wiring and discovery mechanism carry it.
- **JDBC drivers** — that is PRD 045's `./lib/`, not an application plugin.
- **Hot reload / unload** — plugins are discovered at boot only.
- A plugin marketplace, dependency resolution between plugins, or a curated
  plugin registry.

## Plan

**Phase 1 — Plugin contract (design + a marker).** A rapla server plugin jar:
- Contains a `@AutoConfiguration(after = RaplaServerAutoConfiguration.class)`
  class that `@ComponentScan`s the plugin's own package (or declares `@Bean`s
  explicitly).
- Lists that class in its own
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Lives in a package **other than `org.rapla.*`** (a plugin component-scanning
  `org.rapla.*` would double-register stock beans). Recommended convention:
  `<vendor>.raplaplugin.*`.
- Is gated on `@ConditionalOnProperty(prefix = "rapla.plugins", name =
  "<id>.enabled", matchIfMissing = true)` so an operator can disable a deployed
  plugin via config without removing the jar.
- Compiles against `rapla-server` + `rapla-core` as `provided` Maven deps
  (resolved from the deployable at runtime). No separate `rapla-plugin-api`
  artifact — consistent with PRD 003/005 declining the `rapla-client-api` split;
  rapla has no frozen API surface to carve out (see OQ3).

**Phase 2 — `./plugins/` classpath wiring.** Add `plugins/*` to the
extract-and-run `-cp` glob (the launch script / `docs/deployment.md` from PRD
045 Phase 1). On-disk layout extends PRD 045's:

```
/opt/rapla/
  rapla-2.1-SNAPSHOT.jar
  lib/                  ← JDBC drivers (PRD 045)
  plugins/              ← operator-supplied server plugin jars (this PRD)
    acme-sso-plugin.jar
  config/application.yml
  data/data.xml
```

The `./lib/` vs `./plugins/` split is by *role* — a JDBC driver is
infrastructure, a plugin is application code — but mechanically both are just
classpath globs. OQ1 decides whether to keep them separate or collapse to one.

**Phase 3 — REST endpoints from plugins.** A plugin `@RestController` is picked
up by the plugin's own `@ComponentScan`. AGENTS.md §15 still applies — class-level
`@RequestMapping("/api/<name>")` — but `ApiPrefixArchitectureTest` runs in the
rapla-app build and **cannot see plugin jars**. Options: (a) document the §15
contract in `docs/plugins.md` and trust the plugin author; (b) add a runtime
`ApplicationListener` in rapla-server that, after refresh, logs a WARN for any
mapped handler outside `/api/` not on the allow-list. Phase 3 picks (b) — a
runtime warning is cheap and catches the common mistake. SpringDoc group
assignment (§15) is rapla-internal config; plugin endpoints simply won't appear
in the generated SPA client, which is correct (the SPA is built against stock
rapla).

**Phase 4 — Example plugin + author guide.** Ship a minimal example plugin
(a single `/api/hello` endpoint) as a separate tiny Maven module *outside* the
reactor (like `rapla-angular/` is outside it) — or in a `docs/examples/`
sketch. Write `docs/plugins.md`: the contract above, the build setup, the
trust model (OQ2), the `rapla.plugins.<id>.enabled` switch, and the explicit
"no API stability guarantee across rapla minor versions" warning (OQ3).

**Phase 5 — Boot condition interaction.** PRD 045 Phase 3 relaxes the
server-stack boot condition (`@ConditionalOnProperty` on `rapla.file-datasources`).
Plugin autoconfig is `after = RaplaServerAutoConfiguration` so it already
orders correctly; verify a plugin that injects rapla beans (`RaplaFacade`,
`PermissionController`) gets a fully-initialized context. Tier-3 boot test.

## Tests

- Phase 1/2: tier-3 `@SpringBootTest` — a fixture plugin jar (built as a test
  resource) on the test classpath; assert its `@Bean` is present in the context
  and its `@RestController` answers via MockMvc. Mirrors `AutoConfigImportTest`.
- Phase 1: assert `rapla.plugins.<id>.enabled=false` removes the plugin's beans.
- Phase 3: tier-3 test that a plugin controller mapped outside `/api/` triggers
  the startup WARN (capture the log).
- Phase 5: tier-3 boot test — plugin autoconfig that `@Autowired`s `RaplaFacade`
  boots cleanly, proving ordering after `RaplaServerAutoConfiguration`.
- Per AGENTS.md §13: no mocks of rapla types — the example plugin's tests use
  the real Spring context.

## Open Questions

1. **`./plugins/` separate from PRD 045's `./lib/`, or one directory?** Separate
   reads clearer (driver vs app code) and lets an operator reason about trust
   differently. Collapsing to one `./lib/` is one fewer glob. Lean: keep
   separate — decide with PRD 045's distribution-packaging OQ5.
2. **Trust model.** A `./plugins/` jar runs with full server privileges —
   arbitrary code execution by design. Is documenting "only install plugins you
   trust" enough, or does rapla verify a signature (reusing the JNLP signing
   infrastructure / a configured trust anchor) before adding the jar to the
   classpath? Signature verification can't happen *in* the JVM after the class
   is already on the `-cp` glob — it would need a launch-script preflight step.
   Lean: document-only for v1; signature preflight is a follow-up.
3. **API-stability guarantee — the load-bearing open question.** rapla has no
   frozen public API; a plugin compiled against 2.1 `RaplaFacade` may not link
   against 2.2, and (unlike a custom deployable — see the trade-offs section)
   that failure surfaces at runtime on the customer's server, not in a build.
   Two coherent resolutions:
   - **(a) Accept it, document loudly.** `docs/plugins.md` states "recompile
     and re-test your plugin against each rapla version; no cross-version
     guarantee." Matches PRD 003/005 declining the `rapla-client-api` split.
     Cheap, honest — but it shrinks the drop-in model's real benefit to "skip
     the `@SpringBootApplication` boilerplate," since the operator still owns a
     per-version rebuild. Drop-in stays best-effort, suitable for plugins whose
     author tracks rapla releases.
   - **(b) Freeze a narrow plugin-facing surface.** Define an explicit, small,
     `@since`-annotated API (a curated subset of `RaplaFacade` /
     `PermissionController` / the extension hooks) and commit to keeping *that*
     stable across minor versions, with an architecture test that fails the
     rapla build if the surface changes incompatibly. Real work, and a
     standing maintenance constraint — but it is the only option that makes
     drop-in genuinely safe across upgrades and so worth the model.
   - (Extracting a separate `rapla-plugin-api` *artifact* is an implementation
     detail of (b), not a third path — the hard part is the freeze, not the
     jar boundary.)
   Lean: **(a) for v1** to ship the mechanism, with **(b) explicitly flagged as
   the prerequisite for promoting drop-in from "best-effort" to "supported."**
   Decide before leaving draft — this choice sets what the model is *for*.
4. **`@ComponentScan` collision guard.** A misbehaving plugin that scans
   `org.rapla.*` would double-register stock beans and likely fail the context.
   Document the package convention (Phase 1), or actively reject at startup a
   plugin autoconfig whose scan base-packages intersect `org.rapla`? Lean:
   document for v1; the failure is loud and immediate.
5. **Relationship to dhbwrapla.** dhbwrapla stays a full PRD 003 custom
   deployment (server-only, its own `@SpringBootApplication`). Is there value
   in eventually re-shaping the smaller dhbw server plugins as drop-in PRD 046
   plugins? Out of scope here — note for a future PRD 003 / 046 reconciliation.
