# PRD 011 — Upgrade to Spring Boot 4 + Jackson 3

**Status:** done (2026-05-11). Phases 1–5 landed 2026-05-08; Phase 6 follow-up (six Jackson-3 final-field / default-behaviour bugs) verified fixed 2026-05-09–10. `rapla-bom` pins `spring-boot.version=4.0.6`; codebase has 33 `tools.jackson.*` imports and zero `com.fasterxml.jackson.{databind,core}` imports. All six listed final-field bugs are non-final on Jackson-deserialized impls (`ClassificationImpl.data`, `ReservationImpl.appointments`/`permissions`, `AppointmentImpl`, …). `JacksonObjectMapperFactory` re-enables `ALLOW_FINAL_FIELDS_AS_MUTATORS` + `PROPAGATE_TRANSIENT_MARKER` as belt-and-suspenders.
**Date:** 2026-05-08 (closed: 2026-05-11)
**Depends on:** PRD 001 (Spring Boot Migration) substantially complete; [PRD 010](010-jackson-field-based-wire-format.md) (Jackson wire format) lands first.
**Supersedes pin:** `rapla-bom/pom.xml` `<spring-boot.version>3.2.5</spring-boot.version>` and `<jackson.version>2.15.1 / 2.19.0</jackson.version>`.

## Goal

Move the reactor from **Spring Boot 3.2.5 + Jackson 2.x (`com.fasterxml.jackson.*`)** to **Spring Boot 4.x + Jackson 3.x (`tools.jackson.*`)**.

Spring Boot 3.2 reaches OSS EOS as SB 4.0 GA's; Jackson 2.x is maintenance-only and the 3.x package rename forces import churn anyway. SB 4 ships Jackson 2 by default but makes Jackson 3 an opt-in via property switch — combined upgrade is cheaper than two separate ones because both touch the same `JacksonObjectMapperFactory` plumbing.

## Scope

**In:**
- `rapla-bom/pom.xml` — bump `<spring-boot.version>` to latest 4.x GA. Drop `<jackson.version>` unless SB BOM doesn't pin Jackson 3.
- `rapla-core` — rewrite imports in: `org/rapla/rest/JacksonObjectMapperFactory.java`, `JacksonParserWrapper.java`, `JacksonMergePatch.java`, `HTTPWithJsonConnector.java`, tests `JsonReaderTest`, `RestAPIExample`.
- `rapla-server` — Spring auto-config switches the message converter; user code mostly untouched.
- `rapla-client` — `ClientProxyConfig.java`.
- `rapla-app` — `application.yml` may need Jackson keys updated.
- Spring Security: `SecurityConfig.java`, `JwtConfig.java` — verify SS7 JWT decoder API.
- `@HttpExchange` proxies (`RemoteStorage` + 11 others) — Spring 7 same coordinates; verify.

**Out:** Removing legacy Gson paths, reactive, Promise refactor, broader yaml cleanup.

## Plan

Each phase ends green on `mvn compile` + targeted test run. Don't full-`mvn test` between phases (AGENTS.md §5). Rollback = single git revert per phase commit.

### Phase 0 — version bump only (1 hour)

Direct edits on working branch; each phase commits self-contained set for isolated revert.

1. Edit `rapla-bom/pom.xml`: bump SB to current 4 GA. Leave Jackson alone.
2. `mvn -pl rapla-app -am compile`. Capture errors (renamed APIs in Spring Web/Security, jakarta servlet bumps, `RestTemplate` removal — we use `RestClient`, unaffected).
3. Commit the bump even if compile red — next phase fixes against pinned BOM.

### Phase 1 — Spring Boot 4 source compatibility (1–2 days)

Apply minimal fixes to keep semantics. Expected categories:

| Category | Likely fix |
|---|---|
| `RestTemplate` | None — moved to `RestClient` in PRD 001 Phase 5. Verify by grep. |
| `org.springframework.web.bind.annotation.*` removals | Minor signature tweaks. |
| Spring Security 7 deprecations (`http.cors().and()`) | Lambda DSL — already done in `SecurityConfig` per PRD 001 Phase 3. |
| `ObjectProvider` API | `getIfAvailable(...)` stable. |
| Tomcat 11 servlet jar (jakarta 6→7) | Verify `Export2iCalServlet`, `ServletRequestPreprocessorFilter`. |
| `application.yml` keys renamed | Run SB 4 migration analyzer or grep (`spring.http.converters.preferred-json-mapper` removed — Jackson is default). |
| `@SpringBootApplication(exclude={...})` | `DataSourceAutoConfiguration`, `SecurityAutoConfiguration` — verify FQN. |

End: `mvn -pl rapla-app -am compile` + `RaplaSpringBootApplicationTest` green.

### Phase 2 — full reactor test pass (Jackson still 2.x)

Reactor `mvn test`. Investigate every regression. **No Jackson 3 work** — verify SB 4 alone is solid.

### Phase 3 — Jackson 3 cutover (no opt-in, no parallel classpath)

**Surprise (2026-05-08):** Spring Boot 4 ships **Jackson 3 by default**. There is no `spring.jackson.use-jackson3` property; bumping to 4.0.6 already brings Jackson 3 and *removes* Jackson 2 from the managed set. So:

- First `mvn compile` after BOM bump fails on every `import com.fasterxml.jackson.*` not in the annotations module (annotations stay at legacy package — see migration map).
- No "Jackson 2 still works for now" intermediate state. Single atomic source rewrite.
- Carrying both APIs side-by-side is technically possible but pointless — doubles classpath, creates duplicate `ObjectMapper`s Spring won't auto-wire.

### Phase 4 — Jackson 3 source migration (1–2 days)

#### Migration map (canonical recipes — confirmed against jackson-databind master, 2026-05-08)

| Concern | Jackson 2 | Jackson 3 |
|---|---|---|
| **Group ID / package** | `com.fasterxml.jackson.{core,databind,datatype}` | `tools.jackson.{core,databind,datatype}` |
| **Annotations** (`@JsonIgnore`, `@JsonProperty`, `@JsonAutoDetect.Visibility`, etc.) | `com.fasterxml.jackson.annotation.*` | **stays at `com.fasterxml.jackson.annotation.*`** — annotations module deliberately not renamed so existing `@Json…` import lines need no edit |
| **Construction** | `new ObjectMapper()` (mutable, configure-after) | `JsonMapper.builder().…build()` (immutable, configure-during) — generic mutable `ObjectMapper` is removed; format-specific `JsonMapper` is the standard JSON entry point |
| **`JavaTimeModule`** | `mapper.registerModule(new JavaTimeModule())` from `jackson-datatype-jsr310` | **No-op — built into `jackson-databind`.** `java.time` types serialize correctly out of the box. Drop the import + the `registerModule` call. |
| **`WRITE_DATES_AS_TIMESTAMPS`** | `disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)` | **Default is now `false` (ISO-8601).** Calling `disable(...)` is redundant but harmless. |
| **`PROPAGATE_TRANSIENT_MARKER`** | `enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)` | **Same name and semantics** at `tools.jackson.databind.MapperFeature.PROPAGATE_TRANSIENT_MARKER`. Confirmed present in `jackson-databind` master. |
| **Visibility config** ([PRD 010](010-jackson-field-based-wire-format.md) cornerstone) | `mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker().withFieldVisibility(Visibility.ANY).withGetterVisibility(Visibility.NONE)…)` | `JsonMapper.builder().changeDefaultVisibility(vc -> vc.withFieldVisibility(Visibility.ANY).withGetterVisibility(Visibility.NONE).withIsGetterVisibility(Visibility.NONE).withSetterVisibility(Visibility.NONE).withCreatorVisibility(Visibility.ANY)).build();` — lambda transforms the immutable visibility checker; `Visibility` enum stays at `com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility`. |
| **Tree model nodes** (`JsonNode`, `ObjectNode`, `ArrayNode`) | `com.fasterxml.jackson.databind.{JsonNode,node.ObjectNode,node.ArrayNode}` | `tools.jackson.databind.{JsonNode,node.ObjectNode,node.ArrayNode}` — straight rename |
| **Spring HTTP message converter** | `MappingJackson2HttpMessageConverter` | `JacksonJsonHttpMessageConverter` — Spring 7 deprecates the Jackson-2-specific converter; new one implements `SmartHttpMessageConverter` and obsoletes `MappingJacksonValue` for serialization hints |
| **Spring Boot mapper customizer** | `Jackson2ObjectMapperBuilderCustomizer` | `JsonMapperBuilderCustomizer` (`builder.changeDefaultPropertyInclusion(...)` etc.) |
| **`application.yml` keys** | `spring.jackson.read.*` / `spring.jackson.write.*` | `spring.jackson.json.read.*` / `spring.jackson.json.write.*` (ours doesn't set them) |

#### Files to rewrite — status as of 2026-05-08

Done = imports migrated, no leftover `com.fasterxml.jackson.databind|core` except deliberately-preserved `JsonAutoDetect`.

| File | Status | Notes |
|---|---|---|
| `JacksonObjectMapperFactory` | done | `JsonMapper.builder()` + `changeDefaultVisibility(...)`. `JsonAutoDetect.Visibility` retained per D1. |
| `JacksonParserWrapper` | done | `JsonReadFeature` → `tools.jackson.core.json` (D7). `Promise` serializer via `ValueSerializer` + `SerializationContext` (D9). `WRITE_DATES_AS_TIMESTAMPS` from `tools.jackson.databind.cfg.DateTimeFeature` (D3). |
| `JacksonMergePatch` | done | Group rename + `node.fields()` → `node.properties()` (D2). `isContainerNode()` → `isObject() \|\| isArray()` (D2). |
| `HTTPWithJsonConnector` | done | Group rename. `parseJson(...)` drops `JsonProcessingException` try/catch (D5). |
| `RestAPIExample` | done | Group rename. `new ObjectMapper()` → `JsonMapper.builder().build()` (D4). `classification.fields()` → `((ObjectNode) classification).properties()` (D2). |
| `HTTPWithJsonMailConnector` | done | Group rename. `parseJson(...)` collapsed (D5). `JsonWriteFeature` repackage (D6). |
| `MailapiClient` | done | Group rename. `new ObjectMapper()` → `JsonMapper.builder().build()` (D4). |
| `TableConfigTest` | done | Group rename. `new ObjectMapper()` → `JsonMapper.builder().enable(...).build()` (D4). `JsonProcessingException` throws removed (D5). |
| `AuthControllerIntegrationTest` | done | Group rename + `AutoConfigureMockMvc` import → `org.springframework.boot.webmvc.test.autoconfigure.*` (D8). |
| `RemoteStorageErrorMappingIntegrationTest` | done | Same as `AuthControllerIntegrationTest`. |
| `RaplaJacksonConfig` | not started | SB 4 `JsonMapperBuilderCustomizer` rewrite — existing config works because SB 4 ships Jackson 3 and our factory is already builder-shaped. Verify + tighten in Phase 5. |
| `ClientProxyConfig` | not started | `MappingJackson2HttpMessageConverter` → `JacksonJsonHttpMessageConverter`. Defer to Phase 5 unless tests complain. |

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
2. Manual smoke: server up → Swing client connects → resource tree populates → save a reservation → re-fetch (exercises `RemoteStorage` wire surface [PRD 010](010-jackson-field-based-wire-format.md) stress-tested).
3. Drop legacy `<jackson.version>` from `rapla-bom/pom.xml` once `mvn dependency:tree | grep com.fasterxml` clean.
4. Update PRD 001 status: "Phase 9 step 2 (Gson removal) now possible end-to-end."
5. Update [PRD 010](010-jackson-field-based-wire-format.md): "configuration applies on Jackson 3 mapper API."

## Tests

| Phase | Test artifact |
|---|---|
| 0 | `mvn compile` failures captured to scratch file. |
| 1 | `RaplaSpringBootApplicationTest` (7 tests) + reactor compile. |
| 2 | Full reactor `mvn test`. Baseline before Jackson 3. |
| 3 | `RaplaSpringBootApplicationTest` + `AuthControllerIntegrationTest` against Jackson 3 default mapper. |
| 4 | New `JacksonObjectMapperFactoryJackson3Test` mirroring [PRD 010](010-jackson-field-based-wire-format.md)'s 2.x side: round-trip `Reservation`, `UpdateEvent`, `Category` (the `@JsonIgnore`-on-`getResolver` case from [PRD 009](../009-server-bulk-storage-rest-api.md) risk #1). Field-for-field match with 2.x baseline. |
| 5 | Reactor `mvn test` + manual end-to-end smoke. |

## Risks

1. **Spring Security 7 JWT API shift.** SS6 pinned nimbus-jose-jwt 9.x; SS7 may bump to 10.x with breaking signer/decoder API. Watch `JwtConfig.deriveHmacSecret` (PRD 001 Phase 3 step 4) and `MACSigner`/`MACVerifier` ctors. If broken, fall back to nimbus low-level API (already done — minimal exposure).
2. **`HttpServiceProxyFactory` builder API drift.** Spring 7 may have moved builder/bind-init APIs. 13 `@HttpExchange` proxies + per-request `Authorization: Bearer <jwt>` initializer need to keep working. If `RestClient.Builder.requestInitializer(...)` moves, rewrite via `requestInterceptor` / `ClientHttpRequestInitializer` bean.
3. **Jackson 3 module ecosystem incomplete.** Verify `tools.jackson.datatype.JavaTimeModule` published at execution. If not, blocks until module ships.
4. **Field-introspection knobs may have moved.** [PRD 010](010-jackson-field-based-wire-format.md)'s wire format relies on `Visibility.NONE` for every accessor (so `Category.getParent()` resolver-walking getters never fire), `PROPAGATE_TRANSIENT_MARKER = true` (skips `ReferenceHandler.resolver`, `SimpleEntity.{readOnly,nonpersistantEntities}` — without this we re-enter the resolver→scheduler→ScheduledThreadPoolExecutor.threadFactory chain that crashed [PRD 009](../009-server-bulk-storage-rest-api.md)), and `JavaTimeModule`. Jackson 3 reorganized config around immutable builders; verify each knob has an exact equivalent. **Test first thing in Phase 4** with a `Category` round-trip (cycle case) + `Reservation` round-trip (transient-skipping case) — `StackOverflowError` or serialized resolver state means config didn't transfer cleanly.
5. **Tomcat 11 servlet API tightening.** Stricter spec compliance (session ID uniqueness, header parsing). Subtle behaviour changes in `Export2iCalServlet` or static-resource handler. Smoke test in Phase 5 catches.
6. **Reactor-wide compile cascade.** Bare `mvn -pl rapla-bom install` after bump may cascade-rebuild every module. Don't be surprised by slow first compile.

## Open Questions

1. **Wait for SB 4.1?** SB 4.0 just-GA'd may carry rough edges. Decision: pick latest GA at execution. If 4.1 out, prefer it.
2. **Java 21 baseline?** SB 4 requires Java 17 min. We `release 17` source / Java 21 runtime. SB 4.1 may bump baseline. Defer source-level bump to separate PRD.
3. **Gson removal inside this PRD or follow-up?** Recommendation: defer — keeps this PRD small and reversible.
4. **`HTTPWithJsonConnector` deletion?** If truly dead at runtime (PRD 005 follow-up), delete instead of migrating. Verify with runtime trace.
5. **`spring.jackson.use-jackson3` exact property name.** Verify against actual SB 4 docs at execution. If class-path based instead, plan adjusts trivially.

## Discoveries during execution (2026-05-08)

Things the plan did not predict — kept so a future re-read trusts the documented migration map over pre-execution guesses.

### D1. `JsonAutoDetect` annotation import — confirmed unchanged

`JsonAutoDetect.Visibility` lives at `com.fasterxml.jackson.annotation.*` in Jackson 3.1.2 while every other type moved to `tools.jackson.*`. In `JacksonObjectMapperFactory` and `JacksonParserWrapper` you'll see one `com.fasterxml.jackson.annotation.JsonAutoDetect` import alongside `tools.jackson.*` — that mix is correct.

### D2. `JsonNode.fields()` and `isContainerNode()` removed — replacements confirmed

Not on the original map. Discovered when `JacksonMergePatch` + `JacksonParserWrapper` failed to compile:

| Jackson 2 | Jackson 3 |
|---|---|
| `JsonNode.fields()` returning `Iterator<Map.Entry<String, JsonNode>>` | `ObjectNode.properties()` returning `Set<Map.Entry<String, JsonNode>>` — only on `ObjectNode`, so `JsonNode` callers need cast |
| `JsonNode.isContainerNode()` | `node.isObject() \|\| node.isArray()` |

Touched: `JacksonMergePatch.java`, `JacksonParserWrapper.java`, `RestAPIExample.java`.

### D3. `WRITE_DATES_AS_TIMESTAMPS` moved to `DateTimeFeature`, not removed

Map said "redundant but harmless." Wrong on second half — `tools.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` does not exist; relocated to `tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS`. Calling on `SerializationFeature` is a hard compile error. Drop entirely (Jackson 3 default is ISO-8601) or call on `DateTimeFeature`.

### D4. `ObjectMapper` no public no-arg ctor

`new ObjectMapper()` gone. Replacement: `JsonMapper.builder().build()`. Affected: `MailapiClient`, `TableConfigTest`, `AuthControllerIntegrationTest`, `RemoteStorageErrorMappingIntegrationTest`, `RestAPIExample`.

### D5. `JsonProcessingException` no longer thrown by serialization

`writeValueAsString(...)` throws unchecked `tools.jackson.core.JacksonException`. Try/catch and `throws JsonProcessingException` clauses delete. Affected: `HTTPWithJsonConnector.parseJson`, `HTTPWithJsonMailConnector.parseJson`, `TableConfigTest.serializationDesirialization`.

### D6. `JsonWriteFeature` package moved

`com.fasterxml.jackson.core.json.JsonWriteFeature.ESCAPE_NON_ASCII` → `tools.jackson.core.json.JsonWriteFeature.ESCAPE_NON_ASCII`. Same enum, new package — `core.json` moves like `databind`.

### D7. `JsonReadFeature` package moved

`com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES` → `tools.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES`. Two changes: not nested under `JsonParser` now, and in `tools.jackson.core.json`. Affected: `JacksonParserWrapper.defaultObjectMapper()`.

### D8. SB 4 split MockMvc out of `spring-boot-test-autoconfigure`

In SB 3.x, `@AutoConfigureMockMvc`/`@WebMvcTest` were at `org.springframework.boot.test.autoconfigure.web.servlet.*` inside `spring-boot-test-autoconfigure`. SB 4 created **`spring-boot-webmvc-test`** with classes at `org.springframework.boot.webmvc.test.autoconfigure.*`. Class names unchanged.

Two-step fix:
1. Add `spring-boot-webmvc-test` (test scope) to `rapla-bom`'s inherited `<dependencies>` — not transitive from `spring-boot-starter-test`.
2. Rewrite import in every test using `@AutoConfigureMockMvc`/`@WebMvcTest`.

Affected: `AuthControllerIntegrationTest`, `RemoteStorageErrorMappingIntegrationTest`.

### D9. Jackson 3's `SerializationContext` replaces `SerializerProvider`; `ValueSerializer` replaces `JsonSerializer`

For the custom `Promise` serializer in `JacksonParserWrapper`:

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

Two-arg `findValueSerializer(class, beanProperty)` gone — `BeanProperty` no longer needed at lookup time. No `throws IOException` either.

### D10. `module.addSerializer(...)` returns `void` in Jackson 3 (was `SimpleModule` for chaining)

Side effect of immutable-builder redesign. Restructure `new SimpleModule().addSerializer(...).addDeserializer(...)` chains into separate statements on a held reference.

### Net delta vs the plan

- Files actually touched in Phase 4: 14 (plan estimated 7). All mechanical — no behavioural changes beyond migration map.
- Reactor compile + test-compile: green from repo root.
- Reactor full `mvn test`: green at end of Phase 5 (35 rapla-app + all upstream module tests).

### Phase 5 actual work (additions to the plan)

Beyond verification-only steps, Phase 5 fixed three classes of problem surfaced only by `mvn clean test` (incremental compile masked them):

**D11. `DataSourceProperties` moved.** SB 4 split DataSource autoconfig into dedicated `spring-boot-jdbc` module. Class is at `org.springframework.boot.jdbc.autoconfigure.DataSourceProperties`. Two-step: add `spring-boot-jdbc` (compile) to `rapla-server/pom.xml`, rewrite import in `RaplaServerProperties.java`.

**D12. `jakarta.inject.Provider` / `@Named` that should already have been migrated.** Five constructs in `ServerCoreConfig` + `ServerServiceConfig` still wrapped `ObjectProvider<T>` as `jakarta.inject.Provider<T>` for legacy code paths. SB 4 dropped jakarta.inject from default starter classpath, so failed to compile — but adding dep would paper over the real issue: downstream constructors already took `java.util.function.Supplier<T>`. Real fix: delete `Provider<T>` wrappers, pass `objectProvider::getObject` (a `Supplier`) directly. Same for `@jakarta.inject.Named(...)` → `@org.springframework.beans.factory.annotation.Qualifier(...)`. Also `PluginResourcesConfig` (rapla-client): two `Supplier<RaplaFacade>` params re-shaped as `ObjectProvider<RaplaFacade> facadeProvider` + `facadeProvider::getObject` (Spring only auto-wraps top-level injection points as `Supplier`, not nested generics).

**D13. `Supplier<Application>` bridge bean.** `RaplaClientServiceImpl` has `@Autowired` ctor param `Supplier<Application> applicationProvider` to break a cycle with `Application` `@Service` bean. Spring won't auto-wrap a single bean as `Supplier<X>` for nested-generic positions, so register explicit `@Bean Supplier<Application> applicationProvider(ObjectProvider<Application>)` in `ClientConfig`. Same shape as `mailInterfaceSupplier` in `ServerCoreConfig` — generalised: any class wanting `Supplier<X>` injection where `X` is a normal singleton needs an `ObjectProvider<X> → Supplier<X>` bridge.

**D14. Stale per-module `target/test-classes`.** Three test files (`ICalTimezonesControllerTest`, `RemoteLoggerControllerTest`, `UrlPreservationTest`) still imported old `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`. Appeared to compile because `target/test-classes/` had stale `.class` files referencing old class — but classes weren't on SB 4 classpath, so surefire failed at autoconfigure time. `*.class` files needed deleting (or `mvn clean`); fix was same import rewrite as D8.

**D15. `application.yml` SB 4 binding rule change.** Not encountered yet — `rapla.dbDatasources` binds `Map<String, DataSourceProperties>`. With `DataSourceProperties` moved (D11), if binding stops working, introduce record-shaped `RaplaDataSourceConfig` POJO and bind to that.

**RaplaJacksonConfig + ClientProxyConfig — left unchanged.** Plan called for explicit SB 4 customizers (`JsonMapperBuilderCustomizer`, `JacksonJsonHttpMessageConverter`). Existing wiring works because (a) `JacksonObjectMapperFactory.create()` already returns Jackson 3 `JsonMapper`, and (b) SB 4 auto-discovers via existing customizer SPI without needing new type. Reactor tests pass; revisit if `RestClient` ever stops honoring visibility config.

## Effort estimate

~5 working days for a single agent, dominated by Phase 4 (Jackson 3 import rewrite + custom mapper config equivalence). Phase 0–2: ~2 days; Phase 3: ~0.5 day; Phase 5: ~0.5 day. Worst case if SS7 surprises: +2 days for JWT pipeline rework.

## Dependencies on other PRDs

| PRD | Relationship |
|---|---|
| **001** Spring Boot Migration | Hard prerequisite — without SB 3.2 baseline this'd be migrating from Jetty 9. Done. |
| **001-A** Date → LocalDateTime | Soft prerequisite — Jackson 3 JSR-310 is natural target; cleaner with 001-A done first. Not a hard block. |
| **009** RemoteStorage REST controllers | Hard prerequisite — without bulk-storage endpoints, Phase 5 smoke has nothing to exercise. |
| **010** Jackson field-based wire format | Hard prerequisite — locks the configuration we re-pin against. |
| **012** (future) Gson removal | This PRD makes 012 trivially small. |

## Phase 6 — Jackson 3 behaviour-default follow-up (2026-05-09)

Phase 4's "test Reservation/Category round-trip" probe (Risk #4) only covered serialization, not the full setResolver→init chain the Swing client exercises after deserialize. End-to-end smoke on 2026-05-09 surfaced **six concrete bugs**, all sharing one root cause:

> **Jackson 3.0 flipped `MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS` default from `true` (Jackson 2) to `false`** ([jackson-databind#4552](https://github.com/FasterXML/jackson-databind/pull/4552)). Field-based deserialization no longer writes into `private final` fields. Existing entities relied on Jackson 2's behaviour, so any `private final Map/List/Set` collection silently stayed at its initializer value (empty) after deserialize.

User-visible: every resource name and reservation rendered blank in the Swing client.

### Discoveries (D4)

#### D4. `MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS` default flipped to false

Risk #4 asked to verify wire-format knobs transferred. `Visibility.NONE`, `PROPAGATE_TRANSIENT_MARKER`, `JavaTimeModule` came across cleanly — but `ALLOW_FINAL_FIELDS_AS_MUTATORS` (J2 default `true`) became J3 default `false`. [PRD 010](010-jackson-field-based-wire-format.md)'s wire format used `private final Collection<…> = new ArrayList<>();` on five entity collection fields, all silently failed.

Diagnostic chain:
1. Curl `/storage/resources` — JSON `classification.data: {"name":["test"]}` correct. Server-side serialization fine.
2. End-to-end probe `HeadlessClientNameResolutionIntegrationTest` (added 2026-05-09): full server + headless `SpringRaplaClient`, login, `facade.getAllocatables()`, assert each `getName(locale)` non-empty. Failed: empty names on all 6 allocatables.
3. Dump: `ParsedText.formatString="{name}"`, `variablesList=[name]`; `type.getAttribute("name")` returned right attribute; **but** `classification.data={}` instead of `{"name":["test"]}`.
4. `ClassificationImpl.java:49` — `private final Map<String,List<String>> data = new LinkedHashMap<>();`. Hypothesis test (`Jackson3TransientInitializerTest#jackson3SilentlySkipsFinalFieldsWithInitializers`) confirmed.

### Fixes applied

**1. Drop `final` from every wire-format collection field Jackson deserializes:**

| File | Field | Symptom |
|---|---|---|
| `ClassificationImpl` | `data` | resource/reservation names empty (the visible bug) |
| `DynamicTypeImpl` | `permissions` | type permissions ignored |
| `ReservationImpl` | `appointments`, `permissions` | reservations had no appointments → calendar grid empty |
| `AllocatableImpl` | `permissions` | resource permissions ignored |
| `AppointmentMap` (wire DTO for `/storage/queryAppointments`) | `entityIdToAppointmentIds` | calendar couldn't link returned reservations to selected resource |

**2. Belt-and-suspenders: re-enable in `JacksonObjectMapperFactory.configure(…)`:**

```java
.enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
```

Protects future `private final` collection additions from silent regression. (Jackson docs note "may not work with future JVMs" — strategy is "drop final where touched + flag as defensive net".)

**3. Route `JacksonParserWrapper` through `JacksonObjectMapperFactory.configure(…)`:** SQL-history-blob serializer (used by `RaplaSQL`/`EntityHistory` per PRD 001 §492) had own `JsonMapper.builder()` not pinning `PROPAGATE_TRANSIENT_MARKER` or new `ALLOW_FINAL_FIELDS_AS_MUTATORS`. Now routes through shared factory then overlays SQL-history knobs (UTC tz, custom date format, single-quote tolerance, Promise async-resolver serializer, creator visibility back to `NONE`).

### Tests added

All in `rapla-core/src/test/java/org/rapla/rest/Jackson3TransientInitializerTest.java`:

| # | Test | Pins |
|---|---|---|
| 1 | `transientFieldInitializerSurvivesRoundTrip` | Jackson 3 calls no-arg ctor (transient initializers run). |
| 2 | `parsedTextFirstFieldHasInitializedValueAfterRoundTrip` | `ParsedText.first` defaults `""` after deserialize. |
| 3 | `dynamicTypeParseContextIsNonNullAfterRoundTrip` | `DynamicTypeImpl.parseContext` initializer runs. |
| 4 | `dynamicTypeNameformatAnnotationSurvivesRoundTrip` | Annotations Map round-trips with `formatString`. |
| 5 | `plainTextFormatNameSurvivesDeserializeAndInit` | End-to-end `init` + `formatName`. |
| 6 | `appointmentMapEntityIdToAppointmentIdsRoundTripsAfterFinalDropped` | `AppointmentMap.entityIdToAppointmentIds` round-trips. |
| 7 | `factoryReEnablesFinalFieldMutationForBeltAndSuspenders` | Factory enables `ALLOW_FINAL_FIELDS_AS_MUTATORS`. |
| 8 | `jacksonParserWrapperRoundTripsFinalFieldsToo` | SQL-history wrapper inherits same behaviour. |

Plus `rapla-app/src/test/java/org/rapla/client/spring/HeadlessClientNameResolutionIntegrationTest` — full server + headless client, asserts every allocatable's + reservation's `getName(locale)` resolves through deserialize → setResolver → init → formatName.

### Audit completeness

Every other `private final (Map|List|Set|Collection)` in `rapla-core/src/main/java/org/rapla/entities/**` + `…/storage/**` inspected:
- Wire-format types: clean after the 5 entity + 1 DTO fixes.
- `EvalContext`, `StandardFunctions.*` inner classes, `ParsedText` inner classes — runtime-only `Function` instances, never serialized.
- `ModificationEventImpl`, `UpdateResult`, `AppointmentMapping` — facade-internal; wire types (`UpdateEvent`, `AppointmentMap`) are different classes, already non-final where needed.
- `RaplaXMLReader/Writer` — legacy XML, not Jackson.
- `AuthController.TokenResponse` — write-only; client deserializes into non-final `LoginTokens`.
- `AttributeType.type`, `Permission.AccessLevel.level`, `RepeatingType.type` — `enum`s by name.
- `RepeatingEnding.type` — singleton, not held by any wire-format entity.

### Lessons learned

- **End-to-end matters.** Phase 4 Risk #4 was serialize/deserialize byte-equality on `Category` + `Reservation`. Confirmed *wire format unchanged* but didn't catch that **deserialized objects were broken** for downstream `setResolver`/init. Jackson 3 introduces "silently produces half-populated object" bugs — only end-to-end exercises catch them.
- **Belt-and-suspenders config flags are cheap.** Re-enabling `ALLOW_FINAL_FIELDS_AS_MUTATORS` is one line; doesn't fully restore Jackson 2 semantics (may stop working on future JDKs) but protects accidental `private final` adds.
- **Mapper-factory harmonisation overdue.** Five places construct own `JsonMapper.builder()`; only one routed through `JacksonObjectMapperFactory`. SQL-history wrapper now does too. Remaining (`JacksonMergePatch`, `HTTPWithJsonConnector`, mail connectors) don't serialize entities, but "every Jackson user goes through the factory" is long-term shape — defer to future PRD.

## Phase 7 — REST proxy follow-ups (2026-05-09, after Phase 6 client smoke)

Two more production bugs in Swing-client smoke. Neither is a Jackson 3 issue per se, but both come from the REST-proxy migration wave PRD 011 enabled.

### D5. `RemoteLocaleService` returned `Promise<X>` — Spring `HttpServiceProxyFactory` has no Promise adapter

Spring's `HttpServiceProxyFactory` adapts `Mono<X>`, `Flux<X>`, `CompletableFuture<X>` but not `org.rapla.scheduler.Promise<X>`. With `Promise<X>` return type, Spring tells Jackson to deserialize body INTO a `Promise` directly — interface → `InvalidDefinitionException`. Symptom: opening Edit Preferences (any path constructing `CountryChooser`) failed instantly.

**Fix:** `RemoteLocaleService.{locale,countries}` return synchronous types (`LocalePackage`, `Map<String, Set<String>>`). `RemoteLocaleServiceImpl` matches. `CountryChooser` wraps call in `commandScheduler.supply(() -> ...)` so EDT isn't blocked. `RaplaStartOption` injects new `CommandScheduler` ctor arg.

**Test:** `HeadlessClientNameResolutionIntegrationTest` now resolves proxy and asserts `localeService.countries(["en"])` returns non-null map containing "en". Failed with exact production exception before fix.

### D6. `Map<String, Supplier<EditComponent>>` injection point had no matching beans

`EditTaskViewSwing` injects `Map<String, Supplier<EditComponent>>`, keyed by entity-type class name. Each editor registered with `@Service("<typeClass.getName()>")` — but those are `EditComponent` beans, not `Supplier<EditComponent>` beans. Spring auto-Map injection only populates from beans of value type — map injected empty, clicking "edit" threw `RuntimeException("Can't edit objects of type …")`.

**Fix:** `SwingClientConfig` now has explicit `@Bean Map<String, Supplier<EditComponent>> editUiProvider(...)` mirroring `activityPresenters` pattern: walks `getBeanNamesForType(EditComponent.class)` and wraps each in `Supplier`. Linter also added parallel bean for `Map<String, Supplier<PluginOptionPanel>>` (used inside `PreferencesEditUI`). Same problem class.

**Test:** Integration test now asserts the editUiProvider map contains `Preferences` + `DynamicType` keys.

### D7. JWT refresh-on-401 (2026-05-09 — user-driven follow-up)

Access tokens have 1-hour TTL; client had no renew path, so longer sessions started failing every REST call with 401 until re-login. Server's `/auth/refresh` had been wired since PRD 001 Phase 3 and `LoginTokens` included refresh token, but client dropped it at login and never used it.

**Fixes:**
- `RemoteConnectionInfo` gained `refreshToken` field.
- `RaplaClientServiceImpl.login` + `RemoteOperator.connect` now capture `loginToken.getRefreshToken()` after login.
- `ClientProxyConfig.RefreshOn401Interceptor` (replaces old `requestInitializer`): adds bearer header, on 401 calls `POST /auth/refresh` with stored refresh token, updates `connectionInfo.{accessToken,refreshToken}`, retries original request once. Single-flight guard for concurrent in-flight refresh. Skip-recursion guard for `/auth/...` so failed refresh propagates as 401.

**Test:** Integration corrupts access token mid-session, calls `/storage/conflicts`, asserts success AND `RefreshOn401Interceptor.refreshAttempts` incremented. Verified red without interceptor (`401 : [no body]`), green with it.

### Lessons learned (Phase 7)

- **Spring auto-Map injection gotcha.** `Map<String, T>` populates from beans of type `T`. `Map<String, Supplier<T>>` populates from beans of type `Supplier<T>` — NOT from `T` beans wrapped in Suppliers. The `SwingClientConfig.activityPresenters` `@Bean` (PRD 002 era) already knew this; nothing reminded us when `EditTaskViewSwing` started consuming `Map<String, Supplier<EditComponent>>`. **Default rule: every `Map<String, Supplier<X>>` injection point needs matching `@Bean` factory in `SwingClientConfig`** — TODO grep audit + add missing in one sweep.
- **Promise-typed REST proxies are a footgun.** Spring's HTTP service proxy quietly deserializes whatever return type is. `Promise<X>` and custom async wrappers aren't async — Jackson treats as "weird response shape". `RemoteLocaleService` was the only such case in wire surface; flagged for new contributors.
- **The api-testing skill paid off.** Curling failing endpoint to confirm server returns expected JSON (`/storage/conflicts`, `/auth/refresh`) bisected each bug between server- and client-side in seconds. Used three times this session.
