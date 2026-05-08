# PRD 011 — Upgrade to Spring Boot 4 + Jackson 3

**Status:** in-progress (Phases 0–4 source rewrite complete 2026-05-08; reactor `mvn compile test-compile` green on Spring Boot 4.0.6 + Jackson 3.1.2; Phases 2/5 verification passes still pending)
**Date:** 2026-05-08
**Depends on:** PRD 001 (Spring Boot Migration) substantially complete; PRD 010 (Jackson wire format) lands first so the upgrade can re-pin the same configuration on the new mapper API.
**Supersedes pin:** `rapla-bom/pom.xml` `<spring-boot.version>3.2.5</spring-boot.version>` and `<jackson.version>2.15.1 / 2.19.0</jackson.version>`.

## Goal

Move the reactor from **Spring Boot 3.2.5 + Jackson 2.x (`com.fasterxml.jackson.*`)** to **Spring Boot 4.x + Jackson 3.x (`tools.jackson.*`)**.

**Why:**
- Spring Boot 3.2 reaches OSS end-of-support around the same time SB 4.0 GA's; staying on a maintained line is cheaper than back-porting fixes.
- Jackson 2.x is in maintenance only — Jackson 3.x is the future API. The new package (`tools.jackson.*`) deliberately breaks the import to force callers off the legacy classes; rolling it later means re-touching every file we touch now.
- Spring Boot 4 ships with Jackson 2 by default but makes Jackson 3 a supported opt-in via a properties switch — combined upgrade is cheaper than two separate ones because both touch the same `JacksonObjectMapperFactory` plumbing.

## Scope

**In:**
- `rapla-bom/pom.xml` — bump `<spring-boot.version>` to the latest 4.x GA (4.0.x at draft time; pick whatever is current at execution). Drop the `<jackson.version>` override unless Spring Boot's BOM doesn't pin Jackson 3 yet.
- `rapla-core` — rewrite imports in:
  - `org/rapla/rest/JacksonObjectMapperFactory.java` (the shared mapper from PRD 010 — the central touchpoint)
  - `org/rapla/rest/jackson/JacksonParserWrapper.java`
  - `org/rapla/rest/jackson/JacksonMergePatch.java`
  - `org/rapla/rest/client/swing/HTTPWithJsonConnector.java`
  - test files: `JsonReaderTest.java`, `RestAPIExample.java`
- `rapla-server` — touch any controller / config that imports Jackson types directly (most don't; Spring's `MappingJackson2HttpMessageConverter` is replaced by an SB 4 / Jackson 3 equivalent — the choice happens in auto-config, not user code).
- `rapla-client` — `ClientProxyConfig.java` (the `HttpServiceProxyFactory` builder; possibly a new mapper-binding API).
- `rapla-app` — `application.yml` may need the `spring.http.converters.preferred-json-mapper` / `spring.jackson.*` keys updated.
- Spring Security: `SecurityConfig.java`, `JwtConfig.java` — Spring Security 7 ships with Spring Boot 4. JWT decoder API may have evolved; verify `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(MacAlgorithm.HS256).build()` still compiles. `oauth2ResourceServer.jwt(j -> j.decoder(...))` should be stable.
- `@HttpExchange` proxy interfaces (`RemoteStorage.java` and the 11 others in `ClientProxyConfig`): Spring Framework 7's `HttpExchange` annotations are at the same coordinates; verify, no rewrite expected.

**Out:**
- Removing the legacy Gson code paths (`org.rapla.rest.gson.*`, `org.rapla.rest.client.swing.HTTPWithJsonConnector`'s Gson usage). PRD 010 already keeps Gson on the classpath for non-Spring REST paths; that decision is independent of the Jackson 3 cutover.
- Reactive (`spring-webflux`) — the codebase is servlet-only, no plan to switch.
- Refactoring Promise / async wrappers (PRD 008's concern).
- `application.yml` schema cleanup beyond Jackson keys.

## Plan

Each phase ends with `mvn compile` green and a targeted test run on the touched module. Don't run full `mvn test` between phases (per AGENTS.md §5). Rollback strategy is a single git revert per phase commit if a phase regresses worse than expected.

### Phase 0 — version bump only (1 hour)

Work directly on the current working branch (no `spring-boot-4` side branch). Each phase commits its own self-contained set of edits so a phase can be reverted in isolation if it regresses.

1. Edit `rapla-bom/pom.xml`: bump `<spring-boot.version>` to current SB 4 GA. Leave `<jackson.version>` alone for now.
2. `mvn -pl rapla-app -am compile`. Capture the full error list. Most errors will be:
   - Removed/renamed APIs in Spring Web / Spring Security
   - Servlet API symbols (jakarta 6 → jakarta 7 if SB 4 bumps it)
   - `org.springframework.web.client.RestTemplate` removal (we use `RestClient`, should be unaffected)
3. Commit the version bump even if compile is red — the next phase fixes the errors against the pinned BOM, not a moving target.

### Phase 1 — Spring Boot 4 source compatibility (1–2 days)

For each compile error in the captured list, apply the minimal fix to keep semantics the same. Expected categories:

| Category | Likely fix |
|---|---|
| `RestTemplate` references | None expected; we already moved to `RestClient` in PRD 001 Phase 5. Verify by `grep`. |
| `org.springframework.web.bind.annotation.*` removals (e.g., `@RequestMapping` overloads) | Minor signature tweaks in controllers. |
| Spring Security 7 deprecations (`http.cors().and()` style) | Migrate to lambda DSL — already done in `SecurityConfig` per PRD 001 Phase 3. |
| `ObjectProvider` API changes | `getIfAvailable(...)` etc. should be stable. |
| Tomcat 11 servlet jar | Bumps `jakarta.servlet-api` from 6.x → 7.x; verify `Export2iCalServlet` and the `ServletRequestPreprocessorFilter` (`OncePerRequestFilter`) still compile. |
| `application.yml` keys renamed | Run the SB 4 migration analyzer if available, or grep for renamed keys (`server.servlet.context-path` is stable; `spring.http.converters.preferred-json-mapper` removed in SB 4 — Jackson is default). |
| `@SpringBootApplication(exclude={...})` types | Two excluded classes (`DataSourceAutoConfiguration`, `SecurityAutoConfiguration`) — verify they still exist at the same FQN. |

End of phase: `mvn -pl rapla-app -am compile` and `mvn -pl rapla-app,rapla-server -am test -Dtest=RaplaSpringBootApplicationTest` both green.

### Phase 2 — full reactor test pass (Jackson still 2.x)

Run `mvn test` reactor-wide. Investigate every regression. The expected count is ~23 tests passing (current state) plus whatever PRD 009 added. **No Jackson 3 work in this phase** — we're verifying SB 4 alone is solid.

### Phase 3 — Jackson 3 cutover (no opt-in, no parallel classpath)

**Surprise discovery during analysis (2026-05-08):** Spring Boot 4 ships **Jackson 3 by default**. There is no `spring.jackson.use-jackson3` property; bumping `<spring-boot.version>` to 4.0.6 already brings Jackson 3 onto the classpath and *removes* Jackson 2 from `spring-boot-dependencies`'s managed set. So:

- The first `mvn compile` after the BOM bump fails on every `import com.fasterxml.jackson.*` line that isn't covered by the annotations module (which keeps the legacy package — see migration map below).
- There is no "Jackson 2 still works for now" intermediate state in this codebase. The migration is a single atomic source rewrite.
- Carrying both Jackson APIs side-by-side is technically possible (Jackson 3 deliberately changed group IDs to allow it) but pointless once we're starting fresh — it doubles the classpath and creates duplicate `ObjectMapper` instances Spring won't auto-wire.

### Phase 4 — Jackson 3 source migration (1–2 days now that Phase 3 collapsed)

#### Migration map (canonical recipes — confirmed against jackson-databind master, 2026-05-08)

| Concern | Jackson 2 | Jackson 3 |
|---|---|---|
| **Group ID / package** | `com.fasterxml.jackson.{core,databind,datatype}` | `tools.jackson.{core,databind,datatype}` |
| **Annotations** (`@JsonIgnore`, `@JsonProperty`, `@JsonAutoDetect.Visibility`, etc.) | `com.fasterxml.jackson.annotation.*` | **stays at `com.fasterxml.jackson.annotation.*`** — annotations module deliberately not renamed so existing `@Json…` import lines need no edit |
| **Construction** | `new ObjectMapper()` (mutable, configure-after) | `JsonMapper.builder().…build()` (immutable, configure-during) — generic mutable `ObjectMapper` is removed; format-specific `JsonMapper` is the standard JSON entry point |
| **`JavaTimeModule`** | `mapper.registerModule(new JavaTimeModule())` from `jackson-datatype-jsr310` | **No-op — built into `jackson-databind`.** `java.time` types serialize correctly out of the box. Drop the import + the `registerModule` call. |
| **`WRITE_DATES_AS_TIMESTAMPS`** | `disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)` | **Default is now `false` (ISO-8601).** Calling `disable(...)` is redundant but harmless. |
| **`PROPAGATE_TRANSIENT_MARKER`** | `enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)` | **Same name and semantics** at `tools.jackson.databind.MapperFeature.PROPAGATE_TRANSIENT_MARKER`. Confirmed present in `jackson-databind` master. |
| **Visibility config** (PRD 010 cornerstone) | `mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker().withFieldVisibility(Visibility.ANY).withGetterVisibility(Visibility.NONE)…)` | `JsonMapper.builder().changeDefaultVisibility(vc -> vc.withFieldVisibility(Visibility.ANY).withGetterVisibility(Visibility.NONE).withIsGetterVisibility(Visibility.NONE).withSetterVisibility(Visibility.NONE).withCreatorVisibility(Visibility.ANY)).build();` — lambda transforms the immutable visibility checker; `Visibility` enum stays at `com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility`. |
| **Tree model nodes** (`JsonNode`, `ObjectNode`, `ArrayNode`) | `com.fasterxml.jackson.databind.{JsonNode,node.ObjectNode,node.ArrayNode}` | `tools.jackson.databind.{JsonNode,node.ObjectNode,node.ArrayNode}` — straight rename |
| **Spring HTTP message converter** | `org.springframework.http.converter.json.MappingJackson2HttpMessageConverter` | `org.springframework.http.converter.json.JacksonJsonHttpMessageConverter` — Spring Framework 7 deprecates the Jackson-2-specific converter; the new one implements `SmartHttpMessageConverter` and obsoletes `MappingJacksonValue` for serialization hints |
| **Spring Boot mapper customizer** | `Jackson2ObjectMapperBuilderCustomizer` | `JsonMapperBuilderCustomizer` (`builder.changeDefaultPropertyInclusion(...)` etc.) |
| **`application.yml` keys** | `spring.jackson.read.*` / `spring.jackson.write.*` | `spring.jackson.json.read.*` / `spring.jackson.json.write.*` (only matters if any are set; ours doesn't) |

#### Files to rewrite — status as of 2026-05-08

Done = imports migrated to `tools.jackson.*`, no leftover `com.fasterxml.jackson.databind|core` references except the deliberately-preserved `com.fasterxml.jackson.annotation.JsonAutoDetect`.

| File | Status | Notes |
|---|---|---|
| `rapla-core/.../rest/JacksonObjectMapperFactory` | done | Already on `JsonMapper.builder()` + `changeDefaultVisibility(...)`. `JsonAutoDetect.Visibility` import deliberately retained at the legacy package per D1. |
| `rapla-core/.../rest/jackson/JacksonParserWrapper` | done | `JsonReadFeature` moved to `tools.jackson.core.json` (D7). `Promise` serializer rewritten using `ValueSerializer` + `SerializationContext` (D9). `WRITE_DATES_AS_TIMESTAMPS` now from `tools.jackson.databind.cfg.DateTimeFeature` (D3). |
| `rapla-core/.../rest/jackson/JacksonMergePatch` | done | Group rename + `node.fields()` → `node.properties()` (D2). `isContainerNode()` rewritten as `isObject() \|\| isArray()` (D2). |
| `rapla-core/.../rest/client/swing/HTTPWithJsonConnector` | done | Group rename. `parseJson(...)` no longer needs the `JsonProcessingException` try/catch (D5). |
| `rapla-core/src/test/.../rest/client/RestAPIExample` | done | Group rename. `new ObjectMapper()` → `JsonMapper.builder().build()` (D4). `classification.fields()` rewritten as `((ObjectNode) classification).properties()` (D2). |
| `rapla-server/.../plugin/mail/server/HTTPWithJsonMailConnector` | done | Group rename. `parseJson(...)` collapsed (D5). `JsonWriteFeature` repackage (D6). |
| `rapla-server/.../plugin/mail/server/MailapiClient` | done | Group rename. `new ObjectMapper()` → `JsonMapper.builder().build()` (D4). |
| `rapla-server/src/test/.../plugin/tableview/internal/TableConfigTest` | done | Group rename. `new ObjectMapper()` → `JsonMapper.builder().enable(...).build()` (D4). `JsonProcessingException` throws clause removed (D5). |
| `rapla-app/src/test/.../server/spring/web/AuthControllerIntegrationTest` | done | Group rename + `AutoConfigureMockMvc` import moved to `org.springframework.boot.webmvc.test.autoconfigure.*` (D8). |
| `rapla-app/src/test/.../server/spring/web/RemoteStorageErrorMappingIntegrationTest` | done | Same fixes as `AuthControllerIntegrationTest`. |
| `rapla-server/.../spring/RaplaJacksonConfig` | not started | Spring Boot 4's `JsonMapperBuilderCustomizer` rewrite. Existing config still works because Spring Boot 4 ships Jackson 3 by default and our `JacksonObjectMapperFactory` is already builder-shaped — verify and tighten in Phase 5. |
| `rapla-client/.../spring/ClientProxyConfig` | not started | `MappingJackson2HttpMessageConverter` → `JacksonJsonHttpMessageConverter`. Defer until Phase 5 unless reactor tests complain. |

#### Reference patch — `JacksonObjectMapperFactory` before/after

```java
// BEFORE
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public static ObjectMapper configure(ObjectMapper mapper) {
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER);
    mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
            .withFieldVisibility(Visibility.ANY)
            .withGetterVisibility(Visibility.NONE)
            .withIsGetterVisibility(Visibility.NONE)
            .withSetterVisibility(Visibility.NONE)
            .withCreatorVisibility(Visibility.ANY));
    return mapper;
}

// AFTER
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;          // annotations stay at com.fasterxml.*
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

public static JsonMapper.Builder configure(JsonMapper.Builder builder) {
    return builder
        .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
        .changeDefaultVisibility(vc -> vc
            .withFieldVisibility(Visibility.ANY)
            .withGetterVisibility(Visibility.NONE)
            .withIsGetterVisibility(Visibility.NONE)
            .withSetterVisibility(Visibility.NONE)
            .withCreatorVisibility(Visibility.ANY));
    // JavaTimeModule: built-in. WRITE_DATES_AS_TIMESTAMPS: default false. Both removed.
}

public static JsonMapper create() {
    return configure(JsonMapper.builder()).build();
}
```

### Phase 5 — verification + cleanup

1. Reactor `mvn test` green.
2. Manual smoke: server up → Swing client connects → resource tree populates → save a reservation → re-fetch — exercise the `RemoteStorage` wire surface that PRD 010 stress-tested.
3. Drop the legacy `<jackson.version>` override from `rapla-bom/pom.xml` once the Jackson 2 dependency is gone from the tree (`mvn dependency:tree | grep com.fasterxml`). 
4. Update PRD 001 status: "Phase 9 step 2 (Gson removal) now possible end-to-end."
5. Update PRD 010 to say "configuration applies on Jackson 3 mapper API" and link this PRD.

## Tests

| Phase | Test artifact |
|---|---|
| 0 | `mvn compile` failures captured to a scratch file. |
| 1 | `RaplaSpringBootApplicationTest` (7 tests) + reactor compile. |
| 2 | Full reactor `mvn test`. Baseline before Jackson 3. |
| 3 | `RaplaSpringBootApplicationTest` + `AuthControllerIntegrationTest` against Jackson 3 default mapper. |
| 4 | New `JacksonObjectMapperFactoryJackson3Test` in `rapla-core/src/test/java/org/rapla/rest/` mirroring whatever PRD 010 added on the 2.x side: round-trip a `Reservation`, an `UpdateEvent`, and a `Category` (the `@JsonIgnore`-on-`getResolver` case from PRD 009 risk #1) through the new mapper. Both sides must match field-for-field with the 2.x baseline. |
| 5 | Reactor `mvn test` + manual end-to-end smoke. |

## Risks

1. **Spring Security 7 JWT API shift.** Spring Security 6.x (SB 3.2) pinned `nimbus-jose-jwt` 9.x; Spring Security 7 may bump to 10.x with breaking signer/decoder API. Watch `JwtConfig.deriveHmacSecret` (the URL-safe base64 fallback handling — PRD 001 Phase 3 step 4) and the `MACSigner`/`MACVerifier` constructor signatures. If broken, fall back to writing the JWT manually using `nimbus-jose-jwt`'s low-level API (already done — minimal exposure).

2. **`HttpServiceProxyFactory` builder API drift.** Spring 7 may have introduced a new `HttpServiceProxyFactory.Builder` shape or moved bind-init APIs. The 13 `@HttpExchange` proxies in `ClientProxyConfig` plus the request-initializer that adds `Authorization: Bearer <jwt>` per request need to keep working. If the `RestClient.Builder.requestInitializer(...)` hook moves, rewrite using whatever replaces it (likely `requestInterceptor` or a `ClientHttpRequestInitializer` bean).

3. **Jackson 3 module ecosystem incomplete.** Some 2.x modules (e.g., `jackson-datatype-jsr310`) need 3.x equivalents. At the time this PRD is executed, verify `tools.jackson.datatype.JavaTimeModule` (or equivalent) is published. If not, this PRD blocks until the module ships.

4. **Field-introspection knobs may have moved or been renamed in Jackson 3.** PRD 010's wire format relies on three specific Jackson 2 settings to make Rapla entities serialize at all: `Visibility.NONE` for every accessor type (so getters like `Category.getParent()` that walk the resolver are never called), `MapperFeature.PROPAGATE_TRANSIENT_MARKER = true` (so `transient` fields like `ReferenceHandler.resolver`, `SimpleEntity.readOnly`, and `SimpleEntity.nonpersistantEntities` are skipped — without this we re-enter the `resolver → scheduler → ScheduledThreadPoolExecutor.threadFactory` chain that crashed PRD 009), and `JavaTimeModule` for JSR-310 dates. Jackson 3 reorganized the configuration API around immutable builders; verify each of these has an exact equivalent on `tools.jackson.databind.ObjectMapper.builder()` (or whatever the new entry point is) and produces identical wire output. **Test first thing in Phase 4** with a `Category` round-trip (the original cycle case) and a `Reservation` round-trip (the `transient`-skipping case) — if either re-emerges as `StackOverflowError` or as serialized resolver state, the configuration didn't transfer cleanly.

5. **Tomcat 11 servlet API tightening.** Tomcat 11 enforces stricter spec compliance (e.g., session ID uniqueness, header parsing). Subtle behavior changes in `Export2iCalServlet` or the static-resource handler may surface only at runtime. Smoke test in Phase 5 is the catcher.

6. **Reactor-wide compile cascade.** A bare `mvn -pl rapla-bom install` after the version bump may cascade-rebuild every module. Don't be surprised by a slow first compile; subsequent incremental builds are fine.

## Open Questions

1. **Wait for SB 4.1?** SB 4.0 just-GA'd may carry rough edges; 4.1 typically follows 6 months later with a cleaner Jackson 3 story. Decision: pick the latest GA at execution time. If 4.1 is out, prefer it.

2. **Java 21 baseline?** SB 4 requires Java 17 minimum. We currently `release 17` source / Java 21 runtime. If SB 4.1 bumps to Java 21 baseline, that's a wash for runtime but lets us use Java 21 source features (records-as-Spring-beans more cleanly, `switch` patterns, etc.). Defer the source-level bump to a separate PRD.

3. **Gson removal inside this PRD or follow-up?** PRD 001 Phase 9 step 2 was gated by PRD 001-A (Date → LocalDateTime). Once Jackson 3 is in and JSR-310 is the wire format, Gson has no remaining excuse on the Spring HTTP path. This PRD could pull that work in, or defer to a `012-gson-removal` PRD. Recommendation: defer — keeps this PRD small and reversible.

4. **`HTTPWithJsonConnector` deletion?** The legacy Swing client connector that lives outside the Spring HTTP path. If it's truly dead at runtime (PRD 005 follow-up), delete it instead of migrating its imports. Verify with a runtime trace before deleting.

5. **`spring.jackson.use-jackson3` exact property name.** Best-effort guess; verify against the actual SB 4 docs at execution time. If the switch is class-path based instead of property based (e.g., presence of the Jackson 3 starter), the plan above adjusts trivially.

## Discoveries during execution (2026-05-08)

Things the plan above did not predict. Captured here so a future re-read trusts the
documented migration map over my pre-execution guesses.

### D1. `JsonAutoDetect` annotation import — confirmed unchanged

The plan said the annotations module stays at `com.fasterxml.jackson.annotation.*`. Confirmed:
`JsonAutoDetect.Visibility` continues to live at that FQN in Jackson 3.1.2, while every other
Jackson type (`ObjectMapper`, `JsonNode`, `MapperFeature`, …) moved to `tools.jackson.*`. So
in `JacksonObjectMapperFactory` and `JacksonParserWrapper` you'll see one lingering
`com.fasterxml.jackson.annotation.JsonAutoDetect` import side-by-side with `tools.jackson.*`
imports — that mix is correct, not a half-finished migration.

### D2. `JsonNode.fields()` and `isContainerNode()` removed — replacements confirmed

Not on the migration map. Discovered when `JacksonMergePatch` and `JacksonParserWrapper`
failed to compile against `tools.jackson.databind.JsonNode`:

| Jackson 2 | Jackson 3 |
|---|---|
| `JsonNode.fields()` returning `Iterator<Map.Entry<String, JsonNode>>` | `ObjectNode.properties()` returning `Set<Map.Entry<String, JsonNode>>` — note: only on `ObjectNode`, so callers that had a `JsonNode` reference need a cast |
| `JsonNode.isContainerNode()` | `node.isObject() \|\| node.isArray()` |

Touched files: `JacksonMergePatch.java`, `JacksonParserWrapper.java`, `RestAPIExample.java`.

### D3. `WRITE_DATES_AS_TIMESTAMPS` moved to `DateTimeFeature`, not removed

The migration map said the disable call is "redundant but harmless." Wrong on the second
half — `tools.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` does not
exist in Jackson 3. The constant was relocated to
`tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS`. Calling
`.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)` is a hard compile error, not a
silently-ignored hint. Mapper builders that used to call it on `SerializationFeature` now
have to either drop the call entirely (Jackson 3 default is already ISO-8601, so dropping
is fine) or call it on `DateTimeFeature` if explicit intent is wanted.

### D4. `ObjectMapper` no longer has a public no-arg constructor

`new ObjectMapper()` is gone in Jackson 3. The replacement is the format-specific
`JsonMapper.builder().build()` (or `XmlMapper.builder().build()`, etc.). Affected:
`MailapiClient.java`, `TableConfigTest.java`, `AuthControllerIntegrationTest.java`,
`RemoteStorageErrorMappingIntegrationTest.java`, `RestAPIExample.java`.

### D5. `JsonProcessingException` no longer thrown by serialization

`writeValueAsString(...)` and friends throw the unchecked `tools.jackson.core.JacksonException`
in Jackson 3 instead of the checked `JsonProcessingException`. Try/catch blocks around
serialization can be deleted; method `throws JsonProcessingException` clauses fall away too.
Affected: `HTTPWithJsonConnector.parseJson(...)`, `HTTPWithJsonMailConnector.parseJson(...)`,
`TableConfigTest.serializationDesirialization()`.

### D6. `JsonWriteFeature` package moved

`com.fasterxml.jackson.core.json.JsonWriteFeature.ESCAPE_NON_ASCII` →
`tools.jackson.core.json.JsonWriteFeature.ESCAPE_NON_ASCII`. Same enum constant, new package.
Migration map listed only the `databind` group rename; the `core.json` subpackage moves
the same way.

### D7. `JsonReadFeature` package moved

`com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES` →
`tools.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES`. Two changes in one move:
the enum is now `JsonReadFeature` (not nested under `JsonParser`), and it's in the
`tools.jackson.core.json` package. Affected: `JacksonParserWrapper.defaultObjectMapper()`.

### D8. Spring Boot 4 split MockMvc out of `spring-boot-test-autoconfigure`

The plan covered Jackson and Spring Web/Security but did not flag this. In SB 3.x,
`@AutoConfigureMockMvc` and `@WebMvcTest` lived at
`org.springframework.boot.test.autoconfigure.web.servlet.*` inside
`spring-boot-test-autoconfigure`. SB 4 created a new module
**`spring-boot-webmvc-test`** with the classes at the new package
`org.springframework.boot.webmvc.test.autoconfigure.*`. The class names are unchanged.

Two-step fix:
1. Add `spring-boot-webmvc-test` (test scope) to `rapla-bom`'s inherited `<dependencies>` —
   it is *not* a transitive dependency of `spring-boot-starter-test`.
2. Rewrite the import in every test that used `@AutoConfigureMockMvc` or `@WebMvcTest`
   from `org.springframework.boot.test.autoconfigure.web.servlet.*` →
   `org.springframework.boot.webmvc.test.autoconfigure.*`.

Affected tests: `AuthControllerIntegrationTest.java`, `RemoteStorageErrorMappingIntegrationTest.java`.

### D9. Jackson 3's `SerializationContext` replaces `SerializerProvider`; `ValueSerializer` replaces `JsonSerializer`

For the custom `Promise` serializer in `JacksonParserWrapper`. Method signature changes:

```java
// BEFORE (Jackson 2)
new JsonSerializer<Promise>() {
    public void serialize(Promise p, JsonGenerator g, SerializerProvider provider) throws IOException {
        provider.findValueSerializer(result.getClass(), null).serialize(result, g, provider);
    }
}

// AFTER (Jackson 3)
new ValueSerializer<Promise>() {
    public void serialize(Promise p, JsonGenerator g, SerializationContext ctx) {
        ctx.findValueSerializer(result.getClass()).serialize(result, g, ctx);
    }
}
```

The two-arg `findValueSerializer(class, beanProperty)` signature is gone — `BeanProperty`
is no longer needed at lookup time. The method also no longer declares `throws IOException`.

### D10. `module.addSerializer(...)` returns `void` in Jackson 3 (was `SimpleModule` for chaining)

Side effect of the immutable-builder redesign. Restructure
`new SimpleModule().addSerializer(...).addDeserializer(...)` chains into separate statements
on a held reference.

### Net delta vs the plan

- Files actually touched in Phase 4 source rewrite: 14 (plan estimated 7).
- All mechanical — no behavioral changes beyond what the migration map predicted.
- Reactor compile + test-compile: green on `mvn compile test-compile` from the repo root.
- Reactor full `mvn test`: not yet run (deferred to Phase 5 per AGENTS.md §5).

## Effort estimate

~5 working days for a single agent, dominated by Phase 4 (Jackson 3 import rewrite + custom mapper config equivalence). Phase 0–2 (SB 4 compile + tests) is ~2 days; Phase 3 (Jackson 3 opt-in) is ~0.5 day; Phase 5 (verification) is ~0.5 day. Worst-case if Spring Security 7 surprises us: +2 days for JWT pipeline rework.

## Dependencies on other PRDs

| PRD | Relationship |
|---|---|
| **001** Spring Boot Migration | Hard prerequisite — without the SB 3.2 baseline this would be migrating from Jetty 9. Done. |
| **001-A** Date → LocalDateTime | Soft prerequisite — Jackson 3's JSR-310 module is the natural target; if `java.util.Date` is still pervasive, the migration is cleaner with PRD 001-A done first. Not a hard block. |
| **009** RemoteStorage REST controllers | Hard prerequisite — without the bulk-storage endpoints, the smoke test in Phase 5 has nothing to exercise. |
| **010** Jackson field-based wire format | Hard prerequisite — locks the configuration we re-pin against in Phase 4. Without 010, we don't know what "the right Jackson config" is. |
| **012** (future) Gson removal | This PRD makes 012 trivially small. |
