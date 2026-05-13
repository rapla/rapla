# Migration from `master`: the big rework

Rapla's current `spring-boot` branch is a structural rework of the
historical `master` branch. This page records **what changed and why**
so a reader looking at the current codebase has a frame of reference
for older docs, blame trails, and decisions that look arbitrary
without the history.

This is not a step-by-step migration guide. The work is done. This is
**a snapshot of what was different**, useful when:

- You're reading a blame line that points at master-era code and
  wondering why the structure changed.
- You're tempted to "restore" a master-era pattern (`@DefaultImplementation`,
  JSON-RPC factory, single-module reactor) — this page tells you why
  those went away.
- You're sizing the project or briefing a new contributor on "how
  much of Rapla is current vs. legacy."

Cross-references below to the PRDs that drove each piece of the work.

## TL;DR

| Concern | Master | Spring-boot |
|---|---|---|
| Module shape | 1 monolithic Maven project (`pom.xml` + `parent/pom.xml`) | 5-module reactor (`rapla-bom`, `rapla-core`, `rapla-client`, `rapla-server`, `rapla-app`) — PRD 005 |
| DI framework | Custom `restinject` (annotation-processor-driven; external Maven dep `artifactId restinject` at `2.0-RC11`) | Spring Boot 4 (`@Service`, `@ComponentScan`, `@Bean` factories, `@Conditional`) — PRD 001 / 011 |
| REST wire | Hand-rolled JSON-RPC: `org/rapla/enpoints/`, `org/rapla/rest/`, `org/rapla/server/internal/rest/` (~2.3 k LOC) | Spring MVC + Jackson 3 + `@HttpExchange` interfaces — PRD 009 / 010 / 024 / 030 |
| Server runtime | Embedded Jetty bootstrapped by custom code | `RaplaSpringBootApplication`, embedded Tomcat, autoconfig (`META-INF/spring/AutoConfiguration.imports`) — PRD 001 |
| Wire format | Custom Jackson 2 mapper config | Jackson 3 field-based serialisation — PRD 010 |
| Date types in entities | `java.util.Date` everywhere (millis-since-epoch on the wire) | `java.time.LocalDateTime` / `LocalDate` — PRD 001 (Phase A) / 014 (Appointment) / 015 (Rapla-client) |
| Web frontend | None (Swing JNLP only) | Angular SPA at `/app/` — PRD 026 (in flight) |
| Server-side rendered surfaces (calendar tiles / table rows / CSV) | None — clients fetched raw entities and laid out / projected locally | `/calendar/view`, `/table/*`, `/export/csv` — PRD 024 Phase 3 + PRD 030 |
| Authentication | Custom session token, server-side state | OAuth 2.0 PKCE + JWT bearer (browser-redirect for Swing) — PRD 029 |
| Test infrastructure | Smattering of GUI tests + few entity tests (~9 % ratio) | Four-tier pyramid (PRD 017): tier-1 pure Java, tier-2 `FacadeTestSupport`, tier-3 MockMvc + Spring context, tier-4 `@SpringBootTest` end-to-end; ~12.4 % ratio |
| Documentation | One 21-line README | ~20 k LOC across `docs/prd/`, `docs/architecture/`, `AGENTS.md`, `CLAUDE.md` |

## What got removed

### `restinject` (external Maven dep)

Master depended on `org.rapla:restinject:2.0-RC11` — an annotation
processor + runtime that generated `*Factory.java` classes from
`@DefaultImplementation` / `@Extension` annotations on plain
interfaces. The generated classes did the DI wiring + the JSON-RPC
proxy generation for REST clients. Roughly **3–5 k LOC** of Java
across the external project, plus **1–2 k LOC** of generated factory
code in each build's `target/generated-sources/annotations/`.

Why removed:
- The library was Rapla-specific and **maintained alone** — every
  Spring-or-Jakarta upgrade needed a parallel restinject upgrade.
- The generated factories were opaque at debug time (stack traces
  through synthetic types).
- Wire format coupling: restinject was the JSON-RPC layer too, so
  rolling forward Jackson required forking the library.

Replaced by:
- Runtime DI via Spring Boot's `@ComponentScan` + `@Service` /
  `@Component` annotations on the classes themselves (PRD 002).
- REST proxy generation via Spring 6's `@HttpExchange` /
  `HttpServiceProxyFactory` (PRD 009).
- JSON serialisation via Jackson 3 (PRD 010).

Master's `parent/pom.xml:31` declared `<restinject.version>2.0-RC11</restinject.version>`.
That property no longer exists in spring-boot.

### Hand-rolled JSON-RPC infrastructure

Master had a hand-written REST/JSON-RPC layer:

| Master location | LOC | Purpose |
|---|---:|---|
| `org/rapla/enpoints/` *(sic, "enpoints" typo preserved)* | ~961 | REST endpoint interfaces with JAX-RS-style annotations (`@Path`, `@GET`, `@POST`) |
| `org/rapla/rest/` | partial | Wire-format helpers, JsonReader, signed-token logic |
| `org/rapla/server/internal/rest/` | ~689 | Server-side dispatch, request routing, response shaping |

Replaced by Spring MVC's `@RestController` + a small per-controller
class in `rapla-server/.../web/`, plus `@HttpExchange` interfaces in
rapla-core that the Swing client uses as a typed REST proxy and the
Angular client uses as the contract source. No bespoke routing code.

### `parent/` module + `attic/` build artifacts

Master had a separate `parent/` directory holding the shared POM and
a `master/` directory holding the actual source root. The
spring-boot rework collapsed this into one Maven reactor with five
peer modules (PRD 005). `attic/` retained for old build scripts not
deleted from history.

## What got added

### The 5-module reactor (PRD 005)

```
rapla-aggregator (root pom.xml, packaging=pom)
├── rapla-bom         — parent POM, dependency BOM, plugin versions
├── rapla-core        — entities, facade, REST DTOs, no Spring Boot, no Swing
├── rapla-client      — Swing presenters + views; uses spring-context only
├── rapla-server      — Spring autoconfig, JDBC storage, REST controllers
└── rapla-app         — runnable Spring Boot fat JAR (single @SpringBootApplication)
```

The split has a hard dependency direction:
`rapla-app → rapla-server → rapla-core ← rapla-client`. The
historical back-edge from `rapla-server → rapla-client` (PRD 005 D3)
is closed: pinned by `NoRaplaClientImportInServerTest` arch test
(PRD 030 Phase 6).

### Spring Boot 4 stack (PRD 001 / 011)

- `RaplaSpringBootApplication` is the single `@SpringBootApplication`.
- Autoconfig discovered via `META-INF/spring/AutoConfiguration.imports`.
- Embedded Tomcat 10. Servlet path `/rapla/` (preserved from master).
- Spring Security with JWT bearer for the REST surface.
- Jackson 3 (`tools.jackson.*`) for wire serialisation, field-based
  per PRD 010 to avoid getter/setter scaffolding drift.

### Angular SPA (PRD 026, in flight)

A separate `rapla-angular/` directory holds the SPA (build via npm,
served at `/app/` by Spring Boot). Phase 0 prototype just landed
(~11 k LOC of TS/HTML/SCSS). The OpenAPI client at
`rapla-angular/src/app/api/` is generated from the Spring controllers'
`@HttpExchange` interfaces.

The SPA never holds full entity graphs for view purposes — it
consumes server-rendered surfaces from PRD 024 / 030 (calendar tiles,
table rows, CSV export). See [overview.md](overview.md) §"Wire
contracts" for the catalog.

### Pure-Java carve-outs from Swing (PRD 023)

The largest body of new Java code in spring-boot is **pure-logic
classes carved out of Swing god-classes** into `rapla-core`. The
pattern:

1. Identify a chunk of decision logic embedded in a Swing class
   (typically 20+ LOC).
2. Extract to a static class in `rapla-core/.../client/edit/...`
   (the `client.` prefix is historical — the classes live in
   rapla-core, not rapla-client).
3. Add a tier-1 test class that pins the rule with cheap fakes
   (Proxy stubs).
4. Make the Swing class delegate.

Carved out by 2026-05-13:
`RepeatingRuleValidator`, `AllocationConflictModel`,
`ClassificationFilterBuilder`, `ResourceSelectionState`,
`ReservationEditSelection`, `NameSearchMatcher`,
`PasswordChangePolicy`, `RaplaObjectActionPolicy`,
`DefaultReservationWarnings`, `HolidayWarningModel`,
`RequestAllocationWarnings`, `ExceptionListMutator`,
`EventTimeStatus`, `WorktimeRange`, `BlockColors`,
`CalendarLayoutEngine`, `TableViewEngine`, `CsvSerializer`,
`RaplaBlockDecorator`.

Each carve-out has its own tier-1 test class. Total: **509+ tier-1
tests in rapla-core** vs. master's negligible coverage of these rules.

The carve-outs serve two ends: tier-1 testability today, and Angular
reuse later (the SPA either calls these classes via the REST
controllers, or — for stateless helpers — gets a TypeScript port).

### Test-tier pyramid (PRD 017)

| Tier | Where | Engine |
|---|---|---|
| 1 | `rapla-core/src/test/...` extending nothing | Plain JUnit, no Spring, no facade |
| 2 | `rapla-server/src/test/...` extending `FacadeTestSupport` | Plain JUnit + real `FacadeImpl` over `@TempDir testdefault.xml` |
| 3 | `rapla-app/src/test/...` with `@SpringBootTest` + `@AutoConfigureMockMvc` | Cached Spring context, JWT + MockMvc |
| 4 | `rapla-app/src/test/...` with `@SpringBootTest(webEnvironment=RANDOM_PORT)` | Spring + Tomcat |

Mock-framework policy (PRD 027): **no mocks of internal rapla types**
— use the real thing at the appropriate tier. The carve-outs make
tier-1 viable.

### Documentation regime

Master shipped with one README. Spring-boot has:

- `docs/prd/` — Product-requirements docs that drove each structural
  change. Active + done + wont-fix subfolders. ~12 k LOC.
- `docs/architecture/` — Reference docs for current internals
  (this directory). ~3.5 k LOC.
- `AGENTS.md` — Build / test / lifecycle hard rules. ~2 k LOC.
- `CLAUDE.md` + memory files — Per-session AI agent guidance.

PRD discipline (AGENTS.md §3) is the load-bearing constraint: every
structural change starts with a PRD describing the *what + why*, the
work happens against that PRD, and the PRD migrates to `done/` when
it ships.

## Size comparison

Counts as of 2026-05-13 (lines of code, excluding `target/`,
`node_modules/`, the customer XML data dumps in `data/`):

### Java

| | Master | Spring-boot | Δ |
|---|---:|---:|---:|
| Main (production) | 155 832 | 172 343 | +16 511 (+10.6 %) |
| Test | 13 909 | 21 436 | +7 527 (+54 %) |
| **Total** | **169 741** | **193 779** | **+24 038** (+14.2 %) |

The +16.5 k production growth is roughly:
- +10 k from PRD 023 carve-outs (each extraction adds the pure-Java
  class and a tier-1 test; the Swing class shrinks but stays
  in-tree). Net: additive.
- +4 k from new REST controllers + server-side render surfaces
  (PRD 024 / 030).
- +2 k from Spring Boot autoconfig + OAuth (PRD 029).

The +7.5 k test growth tracks the carve-outs directly — every
carved class gets a tier-1 test class.

### Non-Java

| | Master | Spring-boot | Δ |
|---|---:|---:|---:|
| Markdown documentation | 21 (README only) | 19 722 | +19 701 |
| Properties (i18n bundles) | 12 169 | 12 214 | +45 (stable) |
| XML config (excl. data fixtures) | 6 826 | 5 290 | −1 536 (BOM consolidation) |
| Angular TS/HTML/SCSS | 0 | 11 460 | +11 460 (Phase 0 prototype) |
| YAML | 0 | 220 | +220 (Spring `application.yml`, CI) |

### Not in either count

- The `restinject` library that master pulled at build time
  (external GitHub project, ~3–5 k LOC).
- The annotation-processor-generated factory classes in
  `target/generated-sources/annotations/` at master build time
  (~1–2 k LOC).
- The customer XML reservation dumps in `data/` (~4.5 M LOC of
  test fixture data, both branches).

Fold the eliminated-restinject lines back in for a fair total: master
was responsible for ~160–162 k Java LOC, spring-boot is at 193 k —
so the *net* hand-written-Java growth attributable to the rework
proper is **~+30 k LOC**, of which **~75 % is test code or
documentation-adjacent carve-out tests**.

## Why this is the right comparison

A common reaction to the LOC delta: "the spring-boot branch is much
bigger; the migration didn't simplify anything." The honest reading:

- **Production complexity dropped.** A whole external project
  (`restinject`) is gone. The hand-rolled REST infrastructure is
  gone. The custom DI annotations are gone. A custom session-token
  system is gone. Each of those represented load-bearing code that
  one person (the maintainer) had to keep working with every Java /
  framework upgrade. The replacements are framework-provided.
- **Testability went from ~5 % coverage of Swing-edge logic to
  tier-1 coverage of the equivalent decisions.** Every PRD 023
  carve-out is a regression test for a hand-clicked-only rule that
  master didn't pin.
- **The Angular SPA exists.** A user looking at this rework in
  six months will see a working browser frontend that master never
  had.

The bigger LOC number is mostly **test coverage + a second UI tier
+ documentation**. The actual production decision logic didn't grow
substantially — it got more places to live (pure helpers, REST
endpoints, tests pinning the rules) but the rules themselves stayed
roughly stable.

## See also

- [`../prd/done/005-multi-module-split.md`](../prd/done/005-multi-module-split.md)
  — the module reactor split.
- [`../prd/done/001-spring-boot-migration.md`](../prd/done/001-spring-boot-migration.md)
  — the Spring Boot bring-up.
- [`../prd/done/010-jackson-field-based-wire-format.md`](../prd/done/010-jackson-field-based-wire-format.md)
  — Jackson 3 + field-based serialisation.
- [`../prd/done/011-spring-boot-4-jackson-3.md`](../prd/done/011-spring-boot-4-jackson-3.md)
  — Spring Boot 4 upgrade, restinject elimination follow-up.
- [`../prd/023-presenter-view-extraction.md`](../prd/023-presenter-view-extraction.md)
  — pure-Java carve-out programme.
- [`../prd/024-server-side-edit-services.md`](../prd/024-server-side-edit-services.md)
  — server-side edit pre-checks (Angular precursor).
- [`../prd/026-angular-frontend.md`](../prd/026-angular-frontend.md)
  — Angular SPA.
- [`../prd/029-swing-oauth-login.md`](../prd/029-swing-oauth-login.md)
  — OAuth PKCE + JWT.
- [`../prd/030-server-side-view-rendering.md`](../prd/030-server-side-view-rendering.md)
  — server-rendered calendar / table / CSV surfaces.
- [`overview.md`](overview.md) — current architecture reference.
