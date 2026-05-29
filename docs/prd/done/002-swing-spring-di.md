# PRD 002: Swing UI Spring DI Migration

**Status:** done (2026-05-08, this session). All phases A-F complete. Source state: 0 `@Inject` / `@Singleton` / `@Named` / `@DefaultImplementation` / `@Extension` / `@ExtensionPoint` annotations (only 5 stale comment refs); the entire `org.rapla.inject.*` package deleted; `jakarta.inject` BOM dep dropped from rapla-bom + rapla-core/server/client/app pom.xml files; `jakarta.inject` no longer appears in the dep tree. Migration mapping: `@Inject` → `@Autowired` (268 files); `@Singleton` → removed (Spring's default); `@Named` → `@Qualifier` (3 files); `Provider<T>` → `Supplier<T>` from java.util.function (60 files); legacy class-level annotations replaced by Spring stereotypes earlier this session. See "Audit 2026-05-08" section for the migration journey including the false-positive scares from buggy audit scripts and the Jackson 2→3 parallel-session blocker that broke a `mvn compile` mid-Phase-F.
**Date:** 2026-05-06 (last update: 2026-05-08)
**Depends on:** PRD 001 (Spring Boot Migration) — Phase 4 step 5 (`ClientConfig` + `RemoteOperator` wired)

## Goal

**Replace the legacy `restinject` annotation-processor DI with native Spring DI across the entire reactor**, so the codebase has zero dependency on `org.rapla.inject.*` annotations and zero dependency on `jakarta.inject` (JSR-330). Original 2026-05-06 framing was narrower (just "make the Swing tier reachable from `SpringRaplaClient`") — see Implementation History for how the scope grew once the wiring problem was understood.

After this PRD: every wired class uses Spring stereotypes (`@Service` / `@Component` / `@Configuration`) + `@Autowired` + `@Bean` factories; the `org.rapla.inject` package is deleted; `jakarta.inject-api` is off the dep tree.

## Scope

**In scope (final, as-built):**
- 73 `@DefaultImplementation` classes (initial estimate was 32 — undercounted)
- 121 `@Extension(provides=X, id="y")` contributions (initial estimate was 58)
- 39 `@ExtensionPoint(...)` interface declarations (not in original scope; added when whole-package removal became possible)
- 268 `@Inject` annotations (Phase D)
- 102 `@Singleton` annotations (Phase F)
- 60 `Provider<T>` field types (Phase F)
- 3 `@Named` annotations (Phase F)
- All 7 classes in `org.rapla.inject.*` package (deleted)

**Out of scope:**
- GWT (already removed in PRD 001 Phase 7)
- Angular frontend (its own track)
- Any visual/behavioral changes
- Server-side `@Bean`-factory wiring restructure (per AGENTS.md §4 the server intentionally uses `@Bean` factories instead of `@ComponentScan`; Phase D normalized `@Inject` → `@Autowired` on server classes but did NOT add `@Service` annotations there)
- `custom/` module (out of reactor; AGENTS.md note about future PRD)

## Cross-references

- **PRD 001** (Spring Boot migration) — Phase 7 deleted the restinject annotation processor; PRD 002 cleans up the orphaned `@Inject` / `@DefaultImplementation` / `@Extension` annotations the processor used to consume.
- **PRD 003** (custom-deployments) — references the now-removed `restinject` jar via `custom/pom.xml`. Patched with a TODO comment in this PRD; full rework deferred to PRD 003.
- **PRD 010** (Jackson field-based wire format) — happened concurrently in this session and broke `mvn compile` mid-Phase-F (Jackson 2→3 API change in `JacksonMergePatch.java`). Per AGENTS.md §7 not addressed here; PRD 010 owns it.
- **AGENTS.md §4** — sets the policy this PRD enforces: server uses `@Bean` factories, client uses `@ComponentScan`. Phase D respects the asymmetry.

## Verification (run any time to confirm migration hasn't regressed)

One-liner that should print **0** if the migration is intact:
```bash
grep -rE "@(Inject|Singleton|Named|DefaultImplementation|Extension|ExtensionPoint)\b" \
     rapla-{core,client,server}/src/main/java | grep -vE "//|/\*| \*" | wc -l
```

Other regression probes:
```bash
# No legacy annotation classes
[ ! -d rapla-core/src/main/java/org/rapla/inject ] && echo "OK: org.rapla.inject deleted"

# No jakarta.inject in any pom
grep -rln "jakarta\.inject" rapla-bom rapla-core rapla-client rapla-server rapla-app --include='pom.xml' | wc -l   # expect 0

# No jakarta.inject in dep tree
mvn -pl rapla-app dependency:tree 2>&1 | grep -c "jakarta\.inject"   # expect 0

# Spring stereotypes are doing the wiring
grep -rln "@Autowired\b" rapla-core rapla-client rapla-server | wc -l   # expect ~268
```

## Architectural decisions (and the rationale behind each)

**`@Inject` → `@Autowired` (not just removed).** Spring 4.3+ auto-wires single-constructor classes without any annotation, so the strictly-correct migration on most files would be "delete `@Inject` and don't add anything." Chose `@Autowired` instead because it (a) is one mechanical sed, (b) makes the wiring intent visible at the constructor without requiring readers to know Spring's auto-detection rules, (c) keeps multi-constructor classes safe (Spring would have ambiguity errors otherwise). Cleanup pass to delete redundant `@Autowired` from single-ctor classes is left as low-priority follow-up (cosmetic, no behavior change).

**`Provider<T>` → `Supplier<T>` (java.util.function), not `ObjectProvider<T>` (Spring).** Both Spring's `ObjectProvider<T>` and Java's `Supplier<T>` have a `.get()` method that satisfies the legacy `Provider<T>.get()` call sites. Picked `Supplier` because:
- It's a JDK-standard type; consumers don't need to know about Spring just to lazily fetch a bean.
- Drop-in replacement at every call site (the only API used was `.get()`).
- Lets the migration be a pure mechanical text substitution — `s/Provider</Supplier</g` + import swap.
- The `@Bean Map<String, Supplier<TaskPresenter>>` factory in `SwingClientConfig` builds the Supplier wrappers explicitly via lambda, so no Spring-magic is needed for the Map-of-Provider pattern.

If a future need arises for `.getIfAvailable()` / `.orderedStream()` etc. that `ObjectProvider` exposes, the affected consumer can switch its single field type. No global rework needed.

**Field-injection left alone (for now).** AGENTS.md §4 says "Existing field-injected code may be left alone until it's touched, but any class you edit should be migrated to constructor injection in the same change." Phase D respected this — `@Inject` on a field became `@Autowired` on the same field; the constructor-injection migration is a separate per-class refactor. The classes touched outside Phase D (e.g. `RemoteStorageImpl` got hand-edited) did get migrated to ctor-injection where possible.

**`@ExtensionPoint` deleted (it's dead documentation, Spring doesn't read it).** Initial Phase E plan kept `@ExtensionPoint` because it "still in use on extension-point interfaces." The follow-up audit clarified that Spring doesn't consult `@ExtensionPoint` at all — the interfaces it annotates are normal Java interfaces, plugins extend them with `@Service("id")`, and Spring populates `Map<String, T>` consumers from bean names directly. The annotation was just documenting the intent; deleting it is safe.

**`@Singleton` removal is purely cosmetic.** Spring's default scope IS singleton, so `@Singleton` is a no-op annotation. Removing it doesn't change runtime behavior; the value is dropping the `jakarta.inject` BOM dep.

## Audit 2026-05-08 — what's actually still pending (corrected)

Original phases got the client booting; `@DefaultImplementation`/`@Extension` Swing migration essentially complete. What remained at audit time was the broader `@Inject` cleanup and the `rapla-core`/`rapla-server` legacy-annotation tail.

| Concern | Total | Migrated | Pending | Note |
|---|---|---|---|---|
| `@DefaultImplementation` files | 73 | 73 | **0** | Phase 2 done. |
| `@Extension(provides=…, id=…)` contributions | 121 | 60 | 61 | Remaining mostly plugin extensions in core/server. |
| `@Inject`-only classes (rapla-core/client/server) | 195 | — | 195 | Wired via `@Bean` factories; legacy annotations dead-code. |
| Multi-impl interfaces needing `@Primary`/qualifier | 3 | — | 3 | Only `RaplaTableColumnFactory` is a real conflict. |

**Audit script gotchas:**

1. `xargs -I{} sh -c 'grep -L "@Service" "{}"'` produces false positives (lists every file). Use `while read f; do grep -qE '@(Service|Component|Repository|Configuration)\b' "$f" || echo "$f"; done`.
2. `@Service` literal substring doesn't match the FQN form `@org.springframework.stereotype.Service`. Use a combined regex: `(^|[^A-Za-z0-9._])(@Service|@Component|@Repository|@Configuration|@org\.springframework\.stereotype\.|@org\.springframework\.context\.annotation\.Configuration)\b` — or grep imports + short forms separately.
3. **Never** `perl -i -e 'my @lines = <>; ...'` for in-place edits — `-i` truncate-and-rewrites; any code path that skips `print` leaves the file empty. A 26-file zero-out happened this session. Use `Edit` tool or sed (which preserves the file on script error).

### Sequenced plan for the remaining migration

The wiring migration was functionally complete at audit time. What remained was removing the legacy annotations so we could drop `org.rapla.inject.*` + `jakarta.inject`.

1. ~~Phase A — `@DefaultImplementation` → `@Service`~~ — DONE.
2. ~~Phase B — Remove redundant `@DefaultImplementation`~~ — DONE 2026-05-08.
3. ~~Phase C — Remove redundant `@Extension(...)`~~ — DONE 2026-05-08. Sed `s/@Extension([^)]*)//g` worked for 119/121; 2 had non-standard formatting (space before paren, multi-line) — fixed manually.
4. ~~Phase D — `@Inject` → `@Autowired`~~ — DONE 2026-05-08. 268 files; `sed 's|@Inject\b|@Autowired|g'` + import swap. 0 `@Inject` remaining.
5. ~~Phase E — Delete `org.rapla.inject.*` classes~~ — DONE 2026-05-08. Removed 39 `@ExtensionPoint(...)` via sed + 37 `ExtensionPoint` imports + 66 `InjectionContext` imports. Entire package gone (7 classes). Safe because `@ExtensionPoint` is dead documentation — Spring doesn't read it; interfaces are normal Java, plugins extend with `@Service("id")`, Spring populates `Map<String, T>` from bean names.
6. ~~Phase F — Drop `jakarta.inject` from rapla-bom~~ — DONE 2026-05-08. 102 `@Singleton` removed (cosmetic — singleton is Spring default); 3 `@Named` → `@Qualifier`; 60 `Provider<T>` → `Supplier<T>` (java.util.function, drop-in `.get()` API). Dep dropped from BOM + 4 module poms; `mvn dependency:tree` confirms zero `jakarta.inject` artifact.

**Final source state (2026-05-08):**
- 0 `@Inject`/`@Singleton`/`@Named`/`@DefaultImplementation`/`@Extension`/`@ExtensionPoint` annotations (only 5 stale comment refs)
- 0 `org.rapla.inject.*` imports; package directory removed
- 0 `jakarta.inject.*` imports; artifact off classpath
- 268 `@Autowired`, 3 `@Qualifier`, 60 `Supplier<T>`, 104 Spring stereotypes

**Restinject leftover audit (2026-05-08):** 0 source/pom references. `custom/pom.xml`'s phantom `${restinject.version}` reference replaced with a TODO (custom/ is out of reactor; webclient assembly being rethought). 3 PRDs mention restinject in historical context — kept. No annotation processors configured.

**Smoke test:** server (PID 1048732) login + `/storage/resources` 200; client (PID 1146713) bootstraps cleanly through `Starting gui`.

**Phase B sed gotchas (record for reuse):** `sed -i '/^[[:space:]]*@Foo/d'` breaks on (a) same-line `@Foo public class X` (deletes class declaration too — use `sed 's/@Foo([^)]*) //'`), (b) multi-line annotations (continuations become orphans — pre-flatten with `tr` or post-grep for `arg = ` orphans), (c) chained `@Bar @Foo(...)` (start-of-line anchor misses). POSIX awk's `\b` is gawk-only — use `[^A-Za-z0-9_]`.

**Cautions:** even within a phase, work in small batches with smoke tests between (the 2026-05-06 mass-add of `@Service` to 24 classes broke the test suite via cascading missing-bean errors from non-stereotyped collaborators). Never use `perl -i` for in-place edits — see audit gotcha #3.

## Plan (historical — original 6-phase outline)

The 2026-05-06 plan; actual work split into A-F as documented above. Original phases:

- **Phase 1** — `SwingClientConfig` `@Configuration` with `@ComponentScan` over Swing packages; `SpringRaplaClient` ctor extended.
- **Phase 2** — Mechanical sed adds `@Service` alongside `@DefaultImplementation` on all 32 default-impl classes; JSR-330 `@Inject` ctors honored by Spring 6.
- **Phase 3** — Resolve cascading bean errors: run `SpringRaplaClientTest`, fix missing beans, repeat until green.
- **Phase 4** — Extension maps/sets: `@Extension(provides=X, id="Y")` → `@Component @Named("Y")`. Spring's `Map<String, T>` injection uses **bean name** as map key, so `@Service("id")` is the simple-case wiring; multi-id classes (e.g. `EditTaskPresenter` provides 5) use multiple `@Bean` factory methods.
- **Phase 5** — Field → ctor injection migration per AGENTS.md rule.
- **Phase 6** — `@Bean RaplaClientServiceImpl` factory (13 deps); `SpringRaplaClient` exposes `ClientService`.

## Tests

**Final test status (2026-05-08, end of session):**
- `SpringRaplaClientTest` ✅ passes (instantiates the full Swing client Spring context, asserts core beans resolve including `ClientService`, `Application`, `RaplaClientServiceImpl`, the 9-id `Map<String, Supplier<TaskPresenter>>`, and the 5-id `Map<String, Supplier<EditComponent>>`).
- `ClientConfigTest.clientContextLoads` ✅ passes (verifies `ClientConfig` + `ClientProxyConfig` standalone — without `SwingClientConfig` — still resolves; this is the test that caught the early `swingBundleManager`/`commandScheduler` `@Bean` factory removal attempt).
- `RaplaSpringBootApplicationTest` ✅ passes.
- Server-side integration tests (REST + JDBC) ✅ pass.
- **`mvn test` for `rapla-bom,rapla-client,rapla-server,rapla-app` modules: 45 tests pass.**
- **`rapla-core` tests: 1 failure (`JsonReaderTest.testJson`)** — pre-existing parallel-session work on PRD 010 (Jackson 2→3 migration); not addressed here per AGENTS.md §7.

**Smoke test (live server + client):**
- Server (PID at session end: 1048732): `POST /auth/login` → 200; `GET /storage/resources` → 200.
- Client (PID at session end: 1146713): `mvn -pl rapla-client exec:java` boots through `Starting gui` with zero `ERROR`/`Exception` lines.

**Regression-protection one-liner** (run any time; expect 0):
```bash
grep -rE "@(Inject|Singleton|Named|DefaultImplementation|Extension|ExtensionPoint)\b" \
     rapla-{core,client,server}/src/main/java | grep -vE "//|/\*| \*" | wc -l
```
Plus the other probes documented in the **Verification** section above.

## Implementation Status

> **Compressed 2026-05-29:** the per-class chronological wiring log (2026-05-06 → 2026-05-08, ~400 lines of tables) has been collapsed into the summary below. The detailed log existed only to track in-flight iteration; with the migration fully done, the table-by-table journey is superseded by the final state captured under "Final source state".

### Phase 1 — `SwingClientConfig` skeleton (completed 2026-05-06)

`SwingClientConfig` `@Configuration @ComponentScan` over `org.rapla.client.{swing,menu,dialog,internal,event}` + `org.rapla.plugin` (excludeFilters REGEX `.*\\.server\\..*`). `SpringRaplaClient` ctor wires `ClientConfig + ClientProxyConfig + SwingClientConfig` (later: `+ EditTaskPresenterConfig + PluginResourcesConfig`).

### Phase 2 — `@Service` annotations + cascade resolution (✅ DONE 2026-05-08)

All 73 `@DefaultImplementation` files now have a Spring stereotype across multiple iterative batches (2026-05-06 → 2026-05-08). The work landed in three broad waves:

**Wave 1 — leaf classes + cascade unblockers** (`@Service` on Swing-only classes whose ctor deps all resolved): event bus, tree factories, dialog factories, menu items, field factories (date/boolean/text/long/classification/permission/multi-language), filter button. `ComplexTreeCellRenderer` marked `@Primary` to disambiguate three `TreeCellRenderer` candidates. `@Bean`-factory wiring for classes that live in rapla-core: `IOInterface`, `AppointmentFormater`, `CalendarSelectionModel`.

**Wave 2 — boot-time-state classes** (`@Service @Lazy` on classes whose ctor reaches `clientFacade.getUser()` / `addModificationListener()` pre-login): `MultiCalendarPresenter`, `ConflictSelectionPresenter`, `RequestSelectionPresenter`, `ResourceCalendarTask`, `RaplaSwingClipboard`, `InfoFactoryImpl`, `DeleteDialogSwing`, `ReservationControllerImpl`, `MenuFactoryImpl`, `ResourceSelectionViewSwing`, `ApplicationViewSwing`, `RaplaClientServiceImpl`, `Application`, and most plugin extensions.

**Wave 3 — prototype-scoped actions + EditComponent map** (`@Service @Scope("prototype")` where legacy used `Provider<T>` for fresh-per-use semantics): `UserAction`, `AppointmentAction`, `PasswordChangeAction`, `RaplaObjectActions`, `CalendarPrintDialog`, `AllocatableMergeEditUI`, plus the five `EditComponent` impls keyed under entity FQNs (`Reservation`/`Allocatable`/`Category`/`User`/`Preferences`/`DynamicType`). `EditTaskPresenter`'s 5 ids wired via prototype-scoped `@Bean` factory methods in `EditTaskPresenterConfig`.

**Plugin extensions** (43 wired across 4 batches): `PluginResourcesConfig` registers the 10 plugin `*Resources` I18nBundles as `@Bean`s (rapla-core has no `spring-context` dep so they can't be `@Service`d directly). Calendar view factories (10), publish factories, plugin option panels (autoexport, csvexport, eventtimecalculator, export2ical, exchangeconnector, notification, appointmentnote, mail, archiver, tableview, jndi, timeslot, planningstatus), 5 menu extensions (CopyPlugin, ImportTemplate, ImportFromICal, CSVExport, Export2iCal), function factories (`StandardFunctions`/`AppointmentNoteFunctions`/`DurationFunctions` as `@Bean(name=NAMESPACE)`), event-check / annotation-edit extensions.

**Bean naming pitfall:** Spring's `@Service("id")` sets the bean *name* globally — two `@Service("appointmentcounter")` (different extension types) collide with `ConflictingBeanDefinitionException`. Rule: bare `@Service` for `Set<T>` consumers, `@Service("id")` only when consumer is `Map<String, T>` and id needs to be the map key.

**JAX-RS → @HttpExchange:** `ICalExport`, `ExchangeConnectorRemote`, `ExchangeConnectorConfigRemote` converted to Spring `@HttpExchange` interfaces so they can be proxied via `HttpServiceProxyFactory` (server-side dispatch was already vestigial JAX-RS).

**Final UI launch (2026-05-07):** client boots end-to-end via `mvn -pl rapla-client exec:java [-Dexec.args="user pass"]`. `rapla-client/pom.xml` adds slf4j-api + logback-classic at runtime scope (rapla-bom has them provided/test only) and pre-configures exec-maven-plugin with `mainClass=SpringRaplaClient classpathScope=runtime`. Why exec:java not spring-boot:run: rapla-client uses plain `AnnotationConfigApplicationContext`, not `@SpringBootApplication`.

**Critical fix during 5th batch:** `SpringRaplaClientTest` initially failed with `BeanCreationException: Dependency Cycle detected. Please use provider for operator` (`ConflictReservationCheck`'s ctor calls `facade.getRaplaFacade().getPermissionController()` during `preInstantiateSingletons()`, before the operator finishes wiring). PRD claimed a global lazy-init BFPP was in place, but the BFPP did not actually exist. Fixed by adding it for real — `SpringRaplaClient` constructs an empty `AnnotationConfigApplicationContext`, registers a BFPP that walks every `BeanDefinition` and calls `setLazyInit(true)`, then registers config classes and refreshes. Beans construct on first dereference, after operator + facade are fully wired. This eliminates the need for sprinkling `@Lazy` defensively.

**`@Bean` factories in `ClientConfig` are load-bearing, NOT redundant.** Initial cleanup removed `swingBundleManager`/`commandScheduler` factories on the basis that `SwingBundleManager`/`SwingSchedulerImpl` are `@Service @Primary`. Test failure (`ClientConfigTest.clientContextLoads` — "No qualifying bean of type 'BundleManager'") revealed `ClientConfig` is designed to be standalone-usable: the test loads `ClientConfig + ClientProxyConfig` without `SwingClientConfig`'s scan. Reverted with JavaDoc explaining the standalone-vs-Swing duality. `@Primary` overrides do their job when `SwingClientConfig` is also loaded.

**`@Bean` factory return-type cleanup in `ServerServiceConfig` (2026-05-07):** parallel-session controllers (`ArchiverController`, `RemoteLocaleController`, `JNDIConfigController`) were rewritten to inject the impl directly. `@Bean` factories were still typed with interface return — Spring couldn't satisfy. Fixed by changing return types to impl: `archiverService` → `ArchiverServiceImpl`, etc.

**Production-parity items all resolved:**
- `getDownloadURL()` reads `rapla.download.url` system property (default `http://localhost:8051/`).
- `RemoteConnectionInfo.serverURL` URL-stripping bug fixed (2026-05-07): `RestClient.Builder.baseUrl()` was freezing at `@Bean` factory time when `info.getServerURL()` was null; replaced with a custom `DynamicBaseUriBuilderFactory` that reads `info.getServerURL()` per request.
- Vestigial `@Inject` stripped from `CountryChooser`, `LanguageChooser`, `SimpleTreeCellRenderer` (all `new`'d manually at every callsite).
- Empty menu `Set<*MenuExtension>` (`Admin`, `Help`, `View`) — no implementing classes exist; not a wiring gap.
- `LoginDialog` is a static factory, not a Spring bean — no DI needed.

**Final `rapla-client` source state**: 169 files contain `@Inject`; 156 carry a Spring stereotype or are wired via `@Bean`. 13 are intentionally not annotated (wired indirectly: `DefaultIO`, `EditTaskPresenter`; base classes with wired subclasses; dead code: `CalendarTableViewPresenter`, `CalendarWeekViewPresenter`, `CalendarContextMenuPresenter`, `BelongsToAnnotationEdit`, `PlanningStatusAnnotationEdit`, sample `ReservationPresenter`).


### Pattern for prototype-scoped action classes

Action classes like `UserAction`, `AppointmentAction`, `PasswordChangeAction`, `RaplaObjectActions` are typically constructed *fresh per menu invocation* — they hold short-lived state (the selected object, the popup context). The legacy DI used `Provider<UserAction>` to mean "give me a new one each time".

In Spring, the equivalent is `@Service @Scope("prototype")`: each `.get()` from the injected `Supplier<T>` returns a freshly-constructed instance. Without `@Scope("prototype")`, Spring's default is singleton — `.get()` would return the same instance, which would conflate state across invocations.

**Decision rule:** if the consumer takes `Supplier<T>` (post-Phase-F; was `Provider<T>` pre-Phase-F), mark `T` as `@Service @Scope("prototype")`. If the consumer injects `T` directly, mark `T` as plain `@Service` (default singleton).

### Pattern for boot-time-state classes

Classes whose ctor (or super-ctor) calls `clientFacade.getUser()` / `facade.addModificationListener()` / similar runtime-state methods fail eager Spring instantiation in tests that haven't logged in. Three workable approaches:

1. **`@Lazy`** on the bean: definition registered, instantiation deferred until first dereference. Cleanest fix, no behavior change at runtime once login completes.
2. **`@Lazy` on the *injection point*** (the field/parameter that takes the problematic dep). More targeted, but requires changes at every consumer.
3. **`Supplier<T>`** at the consumer side (post-Phase-F; was `Provider<T>` pre-Phase-F): explicit lazy lookup via the supplier's `.get()`. Useful when the dep is genuinely optional, but here we want eager wiring once login exists.

Option 1 is the default for this PRD. Option 2 is reserved for cases where one consumer truly is eager but the dep needs to lag. **Note (Phase F):** the global `setLazyInit(true)` `BeanFactoryPostProcessor` in `SpringRaplaClient` now defers ALL bean instantiation to first-dereference, so most boot-time-state issues are mitigated by default — explicit `@Lazy` is now usually redundant but harmless.

`SpringRaplaClientTest` extended to assert `ApplicationEventBus` and `CalendarEventBus` resolve. `mvn test` → 35 tests passing across 8 Spring contexts (incl. PRD 001-A `DateToolsLocalDateTimeTest`).

**Cascade lesson learned 2026-05-06:** a Python-script mass-add of `@Service` to all 24 remaining `@DefaultImplementation` Swing classes broke the test. Each Swing class transitively depends on non-`@DefaultImplementation` `@Inject` collaborators (`ConflictTreeCellRenderer`, `DialogUiFactoryInterface` impl, `ResourceSelectionViewSwing` parts, `RaplaImages`, etc.) that aren't yet Spring-managed. When all 24 were activated at once, the cascade of missing-bean errors swamped the build. The mass-add was reverted; only the 7 leaf classes above remain `@Service`-annotated.

**Pattern going forward:** each new `@Service` requires the full transitive dep cone to be Spring-managed first. The order should be:
1. Find a `@DefaultImplementation` candidate where every constructor parameter resolves to an existing bean.
2. Add `@Service`.
3. Run `SpringRaplaClientTest` to verify.
4. If new beans are needed (`@Inject`-decorated leaf classes that are *not* `@DefaultImplementation`), add `@Service` to those first.

~~This is genuinely voluminous work — easily another 30+ iterations to wire all 25 remaining `@DefaultImplementation` classes plus their `@Inject`-only collaborators. PRD-defined sub-phases (3 = resolve cascades, 4 = `@Named` extension maps, 5 = constructor injection migration, 6 = `RaplaClientServiceImpl` wiring) all progress in parallel as new beans land.~~ — **the 30+ iterations were done across the 2026-05-07 + 2026-05-08 sessions; all phases A-F closed.**

## Open Questions

- ~~**`@Named("id")` extension key collisions**~~ — **resolved 2026-05-08**. Audited all `@Service("…")`/`@Component("…")`/`@Bean(name=…)` across `rapla-client`: 29 named beans (6 EditComponent FQNs, 4 task-presenter, 11 plugin option, 3 function-factory namespaces, 5 edit-task-presenter ids). Zero collisions. The legacy `PlanningStatusPluginOption` bug (claims `id=CSVExportPlugin.PLUGIN_ID`) isn't a Spring collision because the bean uses bare `@Service`.
- ~~**Per-Swing-component scope**~~ — **resolved**. Decision rule: legacy `Provider<T>` consumer ⇒ `@Service @Scope("prototype")`; direct `T` consumer ⇒ singleton (see "Pattern for prototype-scoped action classes").
- ~~**Component-scan filter for server packages**~~ — **resolved 2026-05-08**. `SwingClientConfig` excludes `.*\\.server\\..*` via REGEX filter; `rapla-client/pom.xml` doesn't depend on `rapla-server` anyway. Filter is belt-and-braces against future misplaced files.
