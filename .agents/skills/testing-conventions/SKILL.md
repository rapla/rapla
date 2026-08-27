---
name: testing-conventions
description: Use when actually writing or modifying a test in this repo — covers the `FacadeTestSupport` base-class usage (tier-2 facade tests, `@TempDir` over `testdefault.xml`, when NOT to use it), the `@Tag("db")`/`@Tag("e2e")` tagging table and the `mvn -Dtest.excludedGroups=` recipes for running the full lane, plus the mock-policy Allowed/Not-allowed lists with specific class names and `@TestConfiguration` patterns. AGENTS.md §10 keeps the 4-tier pyramid (always-on, fires on every test) and §13 keeps the headline "no mocks of internal rapla types" rule; this skill has the details that only matter when you're at the keyboard writing the test.
---

# Testing conventions — the details

AGENTS.md §10 has the always-on pyramid table and the mock-policy headline. This skill has the body for the Java side (tiers 1–4). For Angular tiers 5 (TS unit) and 6 (component), load the `angular-frontend` skill — its "Writing tests" section covers the Vitest + `TestBed` patterns, mocking guidance, and jsdom limits.

## Using `FacadeTestSupport`

`org.rapla.test.util.FacadeTestSupport` (in `rapla-server/src/test/...`) gives each `@Test` a freshly-connected `RaplaFacade` over a temp-dir copy of `testdefault.xml`:

```java
class MyFacadeTest extends FacadeTestSupport {
    @Test
    void categoriesLoad() throws Exception {
        Category[] children = facade.getSuperCategory().getCategories();
        assertEquals(2, children.length);
    }
}
```

- JUnit 5 (`@TempDir`, `@BeforeEach`). Don't mix with JUnit 4 in the same class.
- Override `fixtureResource()` to point at a smaller hand-written XML when the 500-line default is overkill.
- Mirrors production wiring in `ServerCoreConfig.raplaFacade()` + `ServerStorageSelector.createFileOperator()`. **If you add a constructor argument to `FacadeImpl` or `FileOperator`, update this base class in the same change** — the rapla-app `@SpringBootTest` ring is the only thing that catches drift in CI.

If a facade setup feels awkward, the answer is usually "extend `FacadeTestSupport` with the helper you wanted" (as `waitFor(Promise)` was promoted in PRD 017 Phase 4) — not "reach for Mockito".

## When NOT to use `FacadeTestSupport`

- Pure entity/util logic: stay in tier 1 (rapla-core). Don't pull in `FileOperator` to test `DateTools` or appointment expansion.
- Controller behaviour, JSON shape, error mapping, JWT gate: tier 3 with `@AutoConfigureMockMvc`. MockMvc beats `RANDOM_PORT` ~5–10×.
- The very few "does the bean graph wire end-to-end" smoke tests: tier 4. One per major surface is enough.

## Isolate the dataset in full `@SpringBootTest` web tests

A full-context `@SpringBootTest` (tier 3/4) that does NOT override the storage
file boots with the production `application.yml` default
`rapla.file-datasources.raplafile: data/data.xml` — a **relative** path. It
resolves against the JVM working directory, which for `mvn … test` from the
reactor root **is the reactor root**. So the test silently loads
`<reactor-root>/data/data.xml`: a stray, gitignored dev data file left by a
dev-server run. Tests then depend on whatever stale (possibly corrupted) data
sits there — non-hermetic and surprising.

**Worked scar (2026-06-18):** four web-slice tests (`InterfaceRoutingSmokeTest`,
`ICalTimezonesControllerTest`, `RemoteLoggerControllerTest`,
`ExchangeConnectorControllerTest`) ERROR'd on context load with
`InvalidSchemaException: "rapla_anonymousEventClassificationInput" must define
one or more fields`. Root cause: the stray reactor-root `data/data.xml` had its
internal-type keys sanitized by an old GraphqlKeyMigration
(`rapla:anonymousEvent` → `rapla_anonymousEvent`); the key-based `isInternal()`
no longer recognised them, so they leaked into the generated GraphQL SDL as
empty input types and the schema build failed. The data file — not the test
code — was the variable. The same suite passed in a fresh worktree (no stray
file) and failed in the canonical checkout.

**Rule:** a full `@SpringBootTest` must **own its dataset**. Either:
- copy `testdefault.xml` into a `@TempDir` and point `raplafile` at it (when you
  need the homer/monty fixture — see `ClassificationGraphQLControllerTest`), or
- point `raplafile` at a **non-existent** file in a `@TempDir` so the
  `FileOperator` boots a clean default system (ships `admin`/empty-password) when
  you need no specific data — extend
  `org.rapla.server.spring.web.IsolatedDefaultDatasetTest`:

```java
@DynamicPropertySource
static void isolateRaplaDataset(DynamicPropertyRegistry r) {
    r.add("rapla.file-datasources.raplafile",
          () -> raplaDataDir.resolve("rapla-data.xml").toAbsolutePath().toString());
}
```

Never rely on the relative-default `data/data.xml`. If a full-context test fails
only in one checkout, suspect a stray `<reactor-root>/data/`.

## Tagging — fast lane vs. full lane

Slow / environment-dependent tests carry a JUnit 5 `@Tag`:

| Tag | Meaning | Currently tagged |
|---|---|---|
| `db` | Hits a JDBC target (HSQLDB embedded today; still seconds) | `ConcurrentTests` |
| `e2e` | Full `@SpringBootTest` acceptance — cold context, often `RANDOM_PORT` | `RaplaSpringBootApplicationTest`, `ServerServiceIntegrationTest`, `HeadlessClientNameResolutionIntegrationTest`, `SwingClientStartIntegrationTest` |

Default `mvn test` excludes both via surefire `<excludedGroups>${test.excludedGroups}</excludedGroups>` (default value `db,e2e` set in `rapla-bom/pom.xml`). To include them:

```bash
mvn test -Dtest.excludedGroups=         # run everything
mvn test -Dtest.excludedGroups=db       # full + e2e, skip db
```

When you add a test that fits one of these categories, tag it. New tags need a row above and a corresponding entry in PRD 017's plan.

## Mock-policy details

Full rationale in PRD 027. AGENTS.md §13 has the one-line headline; the lists below are the precise call.

**Allowed:**

- Mockito for Servlet-API types you don't own (`HttpServletRequest`/`Response`/`ServletContext`) — see `RaplaJNLPControllerTest` for the canonical shape.
- Hand-rolled test doubles for external integrations behind a rapla-owned interface — see `MockMailer` (`MailInterface`), `RecordingPanel` (`PreferencesPanel`). Prefer these over Mockito when the interface has < ~6 methods.

**Not allowed:**

- `mock(RaplaFacade.class)` / `mock(LocalCache.class)` / `mock(PermissionController.class)` / any rapla entity, facade, storage, or permission type. Construct the real one via `FacadeTestSupport`.
- `@MockBean` / `@SpyBean` in `@SpringBootTest` — they bust the Spring context cache (fights PRD 007 Phase 2.7). Use a `@TestConfiguration` static class with hand-rolled stub beans instead — pattern in `PreferencesAdminControllerIntegrationTest`'s `StubPanelsConfig`.
- Mocks of pure-Java models from PRDs 023 / 024 (`AllocationConflictModel`, `RepeatingRuleValidator`, `RaplaBuilder`, layout strategies). They have no I/O — just construct them with the inputs the test needs.

Why the rules: mocks save < 100 ms per test and silently bypass the class of bugs rapla actually ships — Jackson final-field round-trip, operator stub no-ops, permission leaks, MONTHLY semantic, constructor drift. Booting a real `FacadeImpl` over `testdefault.xml` costs ~150 ms.

Audit grep at PR-review time:

```bash
grep -rE "mock\(.*(Facade|Operator|Cache|Permission|Conflict)\.class\)" rapla-*/src/test
```

Expected: zero matches outside `RaplaJNLPControllerTest` (its `mock(RaplaFacade.class)` is a grandfathered exception).
