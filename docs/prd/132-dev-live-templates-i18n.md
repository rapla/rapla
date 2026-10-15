# PRD 132 — Dev mode: login template and i18n texts change without a server restart

**Status:** draft — 2026-10-08 (concept, no implementation go)
**Related:** [PRD 131](131-logout-stay-signed-in.md) (D14 introduced `templates/login.mustache`),
[PRD 124](124-spa-i18n.md) (`SpaResources`), [PRD 112](done/112-deployment-patch.md) (deployment overrides at start)

## Abstract

Editing the `/login` template or a `RaplaResources*.properties` text today needs a rebuild of
`target/classes` and a server restart before the change is visible. This PRD adds an opt-in dev
mode in which both are re-read from the source tree on the next request. Production and the fat
JAR behave exactly as today: the switch defaults to off.

## As-built (verified 2026-10-08)

- **Login template:** `LoginPageController` compiles `templates/login.mustache` once into a
  `volatile Template template` field and never again (`LoginPageController.java:81, 87, 254-270`).
  The source is the classpath (`ClassPathResource`), i.e. `target/classes` in dev.
- **i18n bundles:** `AbstractBundleManager.loadLocale` caches every bundle forever in
  `packMap` (`AbstractBundleManager.java:27, 99-116`). Below it, `ResourceBundleLoader.loadBundle`
  reads the `.properties` file per call via `getResourceAsStream` — there is **no** second
  (`java.util.ResourceBundle`) cache (`ResourceBundleLoader.java:106-116`). The manager is shared
  with the Swing client.
- **Dev start:** `rapla-app`'s `spring-boot-maven-plugin` sets `mainClass` and a skip property,
  no `addResources`, no `directories` (`rapla-app/pom.xml:358ff`). `spring-boot-devtools` is not a
  dependency anywhere. An external plugin's dev start runs `spring-boot:run` on `rapla-app`
  through the plugin's aggregator with `fork=false` and the plugin repo as working directory
  (`server-lifecycle` skill).

## Ladder (AGENTS §0a0)

1. **Needed?** Yes, for template/text iteration (login page, translations); not for production.
2. **Already in the codebase?** No dev reload switch for either path.
3. **Stdlib / platform?** No.
4. **Installed dependency?** `spring-boot-devtools` is the natural candidate (rung 5), but it does
   **not** solve this on its own: (a) changes under `templates/` are excluded from its restart
   trigger by default, and our template is cached in our own field anyway; (b) a devtools restart
   is a full context restart (seconds), not "no restart"; (c) its restart classloader needs a
   forked run — the plugin dev start uses `fork=false` (to verify, OQ1). So devtools at most
   shortens the restart; it does not make the two caches refresh.
5. **Minimum that works:** one property, two cache bypasses, read from the source tree.

## Proposal

**P1 — one switch.** `rapla.dev.live-resources` (default `false`). Only server code reads it.
**P2 — source directories.** When on, resources are read from the source tree before the
classpath: `rapla-app/src/main/resources` for the template, `rapla-core/src/main/resources` for
the bundles. The roots are configurable (`rapla.dev.resource-roots`, list) so a plugin start
(working directory = the plugin repo) can point to `../rapla/...`. Alternative: the plugin's
`addResources` + `directories` (Spring Boot run goal) to put the source dirs on the classpath —
then no own root handling, but it only works for `spring-boot:run` and the order against
`target/classes` must be checked (OQ2).
**P3 — login template.** With the switch on, `template()` re-reads and re-compiles per request
(or when the file's mtime changed). Off: unchanged.
**P4 — i18n bundles.** With the switch on, `AbstractBundleManager.loadLocale` bypasses `packMap`
(or drops entries whose file mtime changed). Opt-in through a server-side setter/flag only — the
Swing client never sets it, so its behaviour stays unchanged.
**P5 — guard.** The switch logs one WARN at start ("dev live resources ON") and is ignored when
the application runs from a fat JAR (no source tree).

## Goal

With `rapla.dev.live-resources=true` on a `spring-boot:run` start (plain and via a plugin
aggregator): edit `login.mustache` or a `RaplaResources_de.properties` key → reload `/login` →
change visible, no restart, no `mvn` run. With the switch off or in the fat JAR: identical to
today (tests prove both).

## Scope

### In scope
- `/login` template (`LoginPageController`), `RaplaResources*.properties` via `AbstractBundleManager`.

### Out of scope
- Production hot reload of any kind; Swing client; Angular SPA (`ng serve` already reloads).
- Document templates (PRD 097/111) — they have their own store path.

## Plan

### Phase 1 — switch + template
- [ ] Property, WARN log, fat-JAR guard; `LoginPageController.template()` bypass. Tier-3 test:
      switch on → second request sees a changed file; off → cached.
### Phase 2 — bundles
- [ ] `AbstractBundleManager` bypass, server-only opt-in; tier-1 test for both modes.
### Phase 3 — dev start
- [ ] Document the flag in the server-lifecycle skill and the plugin run recipe.

## Tests

Tier 1 for the bundle bypass (temp dir as resource root); tier 3 MockMvc for `/login` with the
switch on/off; no test may depend on the real source tree.

## Open Questions

- **OQ1** — Does `spring-boot-devtools` work with the plugin dev start (`fork=false`)? If yes and the
  user wants auto-restart for Java changes too, it is an add-on, not a replacement for P3/P4.
  *Resolution:* pending (verify).
- **OQ2** — P2 roots via own property vs. plugin `addResources`/`directories`? *Proposal:* own
  property — works independent of how the server is started, explicit in logs. *Resolution:* pending.
- **OQ3** — `SpaResources` (PRD 124, SPA texts delivered by the server) in scope? It is built
  from the same `BundleManager` (`ServerCoreConfig.java:77`), so P4 covers it without extra code;
  whether the SPA's own text cache (PRD 125) then still needs a reload is open.
  *Proposal:* in scope via P4, SPA cache behaviour checked in Phase 2.
- **OQ4** — Re-read per request vs. mtime check? *Proposal:* mtime check (cheap, no surprise
  re-compiles). *Resolution:* pending.

## Decisions locked

None yet.
