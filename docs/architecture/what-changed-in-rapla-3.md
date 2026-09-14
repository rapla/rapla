# What changed in Rapla 3: the big rework

Rapla 3 is a structural rework of the historical Rapla 2 `master`
branch (developed on the `spring-boot` branch). This page records
**what changed and why** so a reader looking at the current codebase
has a frame of reference for older docs, blame trails, and decisions
that look arbitrary without the history.

This is not a step-by-step migration guide (operators: see
[`migration-rapla2-to-rapla3.md`](../../migration-rapla2-to-rapla3.md)).
This is **a snapshot of what was different**, useful when:

- You're reading a blame line that points at master-era code and
  wondering why the structure changed.
- You're tempted to "restore" a master-era pattern (`@DefaultImplementation`,
  JSON-RPC factory, single-module reactor) — this page tells you why
  those went away.
- You're sizing the project or briefing a new contributor on "how
  much of Rapla is current vs. legacy."

Cross-references below to the PRDs that drove each piece of the work.

## TL;DR

| Concern | Master | Rapla 3 |
|---|---|---|
| Module shape | 1 monolithic Maven project (`pom.xml` + `parent/pom.xml`) | 5-module reactor (`rapla-bom`, `rapla-core`, `rapla-client`, `rapla-server`, `rapla-app`) — [PRD 005](../prd/done/005-multi-module-split.md) |
| DI framework | Custom `restinject` (annotation-processor-driven; external Maven dep `artifactId restinject` at `2.0-RC11`) | Spring Boot 4 (client: `@ComponentScan` + `@Service`; server: explicit `@Bean` factories) — [PRD 001](../prd/done/001-spring-boot-migration.md) / [011](../prd/done/011-spring-boot-4-jackson-3.md) |
| REST wire | Hand-rolled JSON-RPC: `org/rapla/enpoints/`, `org/rapla/rest/`, `org/rapla/server/internal/rest/` (~2.3 k LOC) | Spring MVC + Jackson 3 + `@HttpExchange` interfaces (Swing client), GraphQL at `/api/graphql` (SPA) — PRD [009](../prd/009-server-bulk-storage-rest-api.md) / [010](../prd/done/010-jackson-field-based-wire-format.md) / [049](../prd/049-controller-interface-deduplication.md) |
| Server runtime | Embedded Jetty bootstrapped by custom code, webapp under `/rapla` | `RaplaSpringBootApplication`, embedded Tomcat 11, context root `/`, autoconfig (`META-INF/spring/AutoConfiguration.imports`) — [PRD 001](../prd/done/001-spring-boot-migration.md) |
| Wire format | Custom Jackson 2 mapper config | Jackson 3 field-based serialisation — [PRD 010](../prd/done/010-jackson-field-based-wire-format.md) |
| Date types in entities | `java.util.Date` everywhere (millis-since-epoch on the wire) | `java.time.LocalDateTime` / `LocalDate` — PRD [001-a](../prd/done/001-a-date-to-localdatetime.md) / [014](../prd/done/014-appointment-long-to-java-time.md) (Appointment) / [015](../prd/done/015-finish-date-migration-rapla-client.md) (rapla-client) |
| Web frontend | None (Swing JNLP only) | Angular SPA at `/app/`, data via GraphQL only — [PRD 026](../prd/026-angular-frontend.md) |
| Server-side rendered surfaces | None — clients fetched raw entities and laid out / projected locally | `/api/table/*` for the Swing table views ([PRD 030](../prd/030-server-side-view-rendering.md)); the SPA renders from GraphQL views instead |
| Authentication | Custom session token, server-side state | OAuth 2.0 via the bundled Spring Authorization Server: authorization code + PKCE (SPA, Swing), password grant, refresh tokens, API keys; RS256 JWTs — [`authentication.md`](../authentication.md) |
| Test infrastructure | Smattering of GUI tests + few entity tests (~9 % test/main ratio) | Seven-tier pyramid (four Java tiers + three Angular tiers, AGENTS.md §10; [PRD 017](../prd/017-test-coverage-strategy.md)); ~33 % Java test/main ratio (2026-09-14) |
| Documentation | One 21-line README | ~61 k lines of tracked Markdown across `docs/prd/`, `docs/architecture/`, `AGENTS.md` and more (2026-09-14) |

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
- Runtime DI via Spring: on the Swing client `@ComponentScan` +
  `@Service` / `@Component` annotations on the classes themselves
  ([PRD 002](../prd/done/002-swing-spring-di.md)); on the server explicit
  `@Bean` factory methods (AGENTS.md §3).
- REST proxy generation via Spring 6's `@HttpExchange` /
  `HttpServiceProxyFactory` ([PRD 009](../prd/009-server-bulk-storage-rest-api.md)).
- JSON serialisation via Jackson 3 ([PRD 010](../prd/done/010-jackson-field-based-wire-format.md)).

Master's `parent/pom.xml:31` declared `<restinject.version>2.0-RC11</restinject.version>`.
That property no longer exists.

### Hand-rolled JSON-RPC infrastructure

Master had a hand-written REST/JSON-RPC layer:

| Master location | LOC | Purpose |
|---|---:|---|
| `org/rapla/enpoints/` *(sic, "enpoints" typo preserved)* | ~961 | REST endpoint interfaces with JAX-RS-style annotations (`@Path`, `@GET`, `@POST`) |
| `org/rapla/rest/` | partial | Wire-format helpers, JsonReader, signed-token logic |
| `org/rapla/server/internal/rest/` | ~689 | Server-side dispatch, request routing, response shaping |

Replaced by Spring MVC's `@RestController` + a small per-controller
class in `rapla-server/.../web/`, plus `@HttpExchange` interfaces in
rapla-core that the Swing client uses as a typed REST proxy. The
Angular SPA does not use them: it talks to `/api/graphql` and the auth
endpoints through plain `HttpClient`, with no generated client. No
bespoke routing code. (`org/rapla/rest/` still exists in rapla-core,
now holding the service interfaces and DTOs.)

### `parent/` module + `attic/` build artifacts

Master had a separate `parent/` directory holding the shared POM and
a `master/` directory holding the actual source root. The rework
collapsed this into one Maven reactor with five peer modules
([PRD 005](../prd/done/005-multi-module-split.md)). `attic/` retained for
old build scripts not deleted from history.

## What got added

### The 5-module reactor ([PRD 005](../prd/done/005-multi-module-split.md))

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
([PRD 030](../prd/030-server-side-view-rendering.md) Phase 6).

### Spring Boot 4 stack ([PRD 001](../prd/done/001-spring-boot-migration.md) / [011](../prd/done/011-spring-boot-4-jackson-3.md))

- `RaplaSpringBootApplication` is the single `@SpringBootApplication`.
- Autoconfig discovered via `META-INF/spring/AutoConfiguration.imports`.
- Embedded Tomcat 11 (Spring Boot 4.0.8). Context root `/`
  ([PRD 031](../prd/031-api-namespace-redesign.md)); `/rapla/` survives only
  as a literal prefix on the published calendar and iCal routes
  ([legacy-urls.md](legacy-urls.md)).
- Spring Authorization Server issues RS256 JWTs; Spring Security
  validates bearer tokens on the API ([`authentication.md`](../authentication.md)).
- Jackson 3 (`tools.jackson.*`) for wire serialisation, field-based
  per [PRD 010](../prd/done/010-jackson-field-based-wire-format.md) to avoid getter/setter scaffolding drift.

### Angular SPA ([PRD 026](../prd/026-angular-frontend.md))

A separate `rapla-angular/` directory holds the SPA (build via npm,
served at `/app/` by Spring Boot), ~27 k lines of TS/HTML/SCSS
(2026-09-14). There is no generated OpenAPI client: the SPA reads and
writes data through `/api/graphql` and uses `HttpClient` for the
cookie-based auth flow (AGENTS.md §14).

The SPA renders from server-declared GraphQL views
([PRD 074](../prd/074-graphql-declarative-views.md) /
[078](../prd/078-spa-graphql-view-renderer.md)), not from the
server-rendered surfaces originally planned in PRD
[024](../prd/wont-fix/024-server-side-edit-services.md) (wont-fix) /
[030](../prd/030-server-side-view-rendering.md). See
[overview.md](overview.md) for the current architecture.

### Pure-Java carve-outs from Swing ([PRD 023](../prd/023-presenter-view-extraction.md))

A large body of new Java code is **pure-logic classes carved out of
Swing god-classes** into `rapla-core`. The pattern:

1. Identify a chunk of decision logic embedded in a Swing class
   (typically 20+ LOC).
2. Extract to a class in `rapla-core` — under `org.rapla.client.*`
   (`edit`, `sidebar`, `menu`; the `client.` prefix is historical, the
   classes live in rapla-core, not rapla-client), `org.rapla.plugin.*`
   or `org.rapla.facade`.
3. Add a tier-1 test class that pins the rule with cheap fakes
   (Proxy stubs).
4. Make the Swing class delegate.

Carved out by 2026-05-13 and still present:
`RepeatingRuleValidator`, `AllocationConflictModel`,
`ResourceSelectionState`, `ReservationEditSelection`,
`NameSearchMatcher`, `PasswordChangePolicy`,
`RaplaObjectActionPolicy`, `DefaultReservationWarnings`,
`HolidayWarningModel`, `RequestAllocationWarnings`,
`ExceptionListMutator`, `EventTimeStatus`, `WorktimeRange`,
`BlockColors`, `TableViewEngine`, `CsvSerializer`.
(`CalendarLayoutEngine` and `RaplaBlockDecorator` were later deleted
together with the server-rendered calendar view.)

Each carve-out has its own tier-1 test class; rapla-core has ~570
test methods (2026-09-14) vs. master's negligible coverage of these
rules.

### Test-tier pyramid ([PRD 017](../prd/017-test-coverage-strategy.md))

| Tier | Where | Engine |
|---|---|---|
| 1 | `rapla-core/src/test/...` extending nothing | Plain JUnit, no Spring, no facade |
| 2 | `rapla-server/src/test/...` extending `FacadeTestSupport` | Plain JUnit + real `FacadeImpl` over `@TempDir testdefault.xml` |
| 3 | `rapla-app/src/test/...` with `@SpringBootTest` + `@AutoConfigureMockMvc` | Cached Spring context, JWT + MockMvc |
| 4 | `rapla-app/src/test/...` with `@SpringBootTest(webEnvironment=RANDOM_PORT)` | Spring + Tomcat |
| 5 | `rapla-angular/src/**/*.spec.ts` | Vitest, no `TestBed` |
| 6 | `rapla-angular/src/**/*.spec.ts` with `TestBed` | Vitest + Angular TestBed + jsdom |
| 7 | `rapla-angular/tests/**/*.spec.ts` | Playwright against a live server |

Details and costs per tier: AGENTS.md §10.

Mock-framework policy ([PRD 027](../prd/027-mock-framework-policy.md)): **no mocks of internal rapla types**
— use the real thing at the appropriate tier. The carve-outs make
tier-1 viable.

### Documentation regime

Master shipped with one README. Rapla 3 has (line counts of tracked
files, 2026-09-14):

- `docs/prd/` — Product-requirements docs that drove each structural
  change. Active + `done/` + `wont-fix/` subfolders. ~40 k lines.
- `docs/architecture/` — Reference docs for current internals
  (this directory). ~6.7 k lines.
- `AGENTS.md` — Build / test / lifecycle hard rules. ~390 lines.
  `CLAUDE.md` only points to it.

PRD discipline (AGENTS.md §2) is the load-bearing constraint: every
structural change starts with a PRD describing the *what + why*, the
work happens against that PRD, and the PRD moves to `done/` when it
ships (or to `wont-fix/` when the direction is dropped).

## Size comparison

Lines of code in tracked files, excluding `target/` and `node_modules/`.
Master figures were measured on 2026-05-13; Rapla 3 figures on
2026-09-14 where marked, otherwise 2026-05-13.

### Java

| | Master | Rapla 3 (2026-09-14) | Δ |
|---|---:|---:|---:|
| Main (production) | 155 832 | 203 743 | +47 911 (+31 %) |
| Test | 13 909 | 66 431 | +52 522 (+378 %) |
| **Total** | **169 741** | **270 174** | **+100 433** (+59 %) |

In May 2026 the production growth (then +16.5 k) came mainly from the
[PRD 023](../prd/023-presenter-view-extraction.md) carve-outs, new REST
controllers and Spring Boot autoconfig + OAuth. Growth since then
(GraphQL API, SPA features) has not been broken down by area.

### Non-Java

| | Master | Rapla 3 | Δ |
|---|---:|---:|---:|
| Markdown documentation | 21 (README only) | ~60 800 (2026-09-14) | +~60 800 |
| Angular TS/HTML/SCSS | 0 | ~26 950 (2026-09-14) | +~26 950 |
| Properties (i18n bundles) | 12 169 | 12 214 (2026-05-13) | +45 |
| XML config (excl. data fixtures) | 6 826 | 5 290 (2026-05-13) | −1 536 (BOM consolidation) |
| YAML | 0 | 220 (2026-05-13) | +220 (Spring `application.yml`, CI) |

### Not in either count

- The `restinject` library that master pulled at build time
  (external GitHub project, ~3–5 k LOC).
- The annotation-processor-generated factory classes in
  `target/generated-sources/annotations/` at master build time
  (~1–2 k LOC).

## Why this is the right comparison

A common reaction to the LOC delta: "Rapla 3 is much bigger; the
migration didn't simplify anything." The honest reading:

- **Production complexity dropped.** A whole external project
  (`restinject`) is gone. The hand-rolled REST infrastructure is
  gone. The custom DI annotations are gone. A custom session-token
  system is gone. Each of those represented load-bearing code that
  one person (the maintainer) had to keep working with every Java /
  framework upgrade. The replacements are framework-provided.
- **Testability went from ~5 % coverage of Swing-edge logic to
  tier-1 coverage of the equivalent decisions.** Every [PRD 023](../prd/023-presenter-view-extraction.md)
  carve-out is a regression test for a hand-clicked-only rule that
  master didn't pin.
- **The Angular SPA and the GraphQL API exist** — a browser frontend
  and a public data API that master never had.

Much of the bigger LOC number is **test code + a second UI tier +
documentation**: more than half of the Java growth is test code.

## See also

- [`../../migration-rapla2-to-rapla3.md`](../../migration-rapla2-to-rapla3.md)
  — operator guide for upgrading a Rapla 2 deployment.
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
- [`../prd/wont-fix/024-server-side-edit-services.md`](../prd/wont-fix/024-server-side-edit-services.md)
  — server-side edit services (wont-fix; superseded by the GraphQL API).
- [`../prd/026-angular-frontend.md`](../prd/026-angular-frontend.md)
  — Angular SPA.
- [`../prd/029-swing-oauth-login.md`](../prd/029-swing-oauth-login.md)
  — Swing OAuth login (PKCE).
- [`../prd/030-server-side-view-rendering.md`](../prd/030-server-side-view-rendering.md)
  — server-rendered table surfaces (`/api/table/*`).
- [`../authentication.md`](../authentication.md) — current authentication model.
- [`overview.md`](overview.md) — current architecture reference.
