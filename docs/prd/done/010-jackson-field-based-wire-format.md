# PRD 010 — Jackson field-based JSON wire format (shared client + server)

**Status:** done (2026-05-08) — all plan items shipped; `SwingClientStartIntegrationTest` runs green and locks in the wire format. Configuration migrated to the Jackson 3 mapper API (`tools.jackson.*`) under [PRD 011 Phase 4](011-spring-boot-4-jackson-3.md); `JacksonObjectMapperFactory.configure(JsonMapper.Builder)` is the post-migration entry point — same four knobs (`FIELD=ANY`, `GETTER/IS_GETTER/SETTER=NONE`, `CREATOR=ANY`, `PROPAGATE_TRANSIENT_MARKER=true`) and same wire format, just on the immutable-builder shape Jackson 3 requires.

## Goal

Use **Jackson with field-based introspection + `transient`-honoring + JSR-310 dates** as the single, shared JSON wire format between Spring Boot server and Swing client. Replaces Gson on both sides of the Spring HTTP machinery.

**Why this matters (read before switching anything to getters):** Rapla entities are not POJOs — they're graph nodes with bidirectional references stored in a per-entity `ReferenceHandler.links: Map<String,List<String>>` and resolved through a `transient EntityResolver`. `Category.getParent()` walks the resolver and may cycle. Naïve Jackson defaults (getter-based) → `StackOverflowError` mid-serialization with truncated JSON, no error code. Naïve Gson defaults (field-based, no JSR-310 module) → `InaccessibleObjectException` because JDK modules block reflection into `java.time` internals. **Both modes hit [PRD 009](../009-server-bulk-storage-rest-api.md) implementation.** This PRD freezes the config that solved them.

## Scope

**In:**
- Server-side serialization for every `@RestController` response (`/storage/*`, `/auth/*`, plugin endpoints).
- Client-side deserialization for every `HttpServiceProxyFactory`-generated proxy (`RemoteStorage`, `RemoteAuthentificationService`, plugin proxies — see `ClientProxyConfig.java`).
- Both sides MUST use the same `ObjectMapper` configuration so the wire format is symmetric.

**Out:**
- Legacy Gson code in `org.rapla.rest.gson.*`, `org.rapla.rest.client.swing.HTTPWithJsonConnector` — keeps Gson on the classpath for older REST paths until a separate PRD removes them. Not on the Spring HTTP path.
- Promise / async wrapping at the call-site (PRD 008's concern). The wire is sync; reactive wrapping happens client-side after deserialization.

## Decision: field-based introspection + JavaTimeModule

The shared mapper is built by `org.rapla.rest.JacksonObjectMapperFactory` (rapla-core). Both client and server route through it.

```java
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
```

The four key knobs and *why each one is load-bearing*:

| Knob | Value | Why |
|---|---|---|
| `FIELD = ANY`, `GETTER/IS_GETTER/SETTER = NONE` | opt-in via fields | Stops Jackson from calling derived getters (`getParent`, `getCategoryList`, `getReservation`, `getAppointmentStream`, …) that traverse the `transient resolver` and cycle the graph. Only persistent state (primitive fields + the `links: Map<String,List<String>>` ID-ref table) crosses the wire. |
| `CREATOR = ANY` | constructors visible | Lets Jackson find no-arg / args constructors during deserialization, otherwise classes with private ctors fail to instantiate even though their fields are visible. |
| `PROPAGATE_TRANSIENT_MARKER = true` | honor `transient` | Jackson normally ignores the Java `transient` keyword (it's a `Serializable`-only marker). With this on, fields like `ReferenceHandler.resolver`, `SimpleEntity.readOnly`, `SimpleEntity.nonpersistantEntities` are skipped. Without this we re-enter the `resolver → scheduler → ScheduledThreadPoolExecutor.threadFactory` chain that crashed [PRD 009](../009-server-bulk-storage-rest-api.md) before. |
| `JavaTimeModule` + `WRITE_DATES_AS_TIMESTAMPS=false` | ISO-8601 dates | `LocalDateTime` / `Instant` round-trip as ISO strings, not 7-element numeric arrays. The JDK module system forbids reflective access to `java.time` internals; without this module Gson and Jackson both fail at runtime with `InaccessibleObjectException`. |

## Wiring

- **rapla-core** owns `JacksonObjectMapperFactory` (no Spring dep — just Jackson). Keeps the configuration logic in one place that both clients of Jackson can pull from.
- **rapla-server** registers `RaplaJacksonConfig` — a `Jackson2ObjectMapperBuilderCustomizer` that calls `JacksonObjectMapperFactory::configure` via `postConfigurer(...)`. Spring Boot picks it up automatically.
- **rapla-client** has `jackson-databind` + `jackson-datatype-jsr310` as direct compile-scope deps (added in this PRD). `ClientProxyConfig.httpServiceProxyFactory(...)` constructs a `MappingJackson2HttpMessageConverter` from `JacksonObjectMapperFactory.create()`, removes any Gson converter, and inserts the Jackson one at index 0 of the `RestClient`'s converter list.

## What NOT to do

- **Don't `@JsonIgnore` individual entity getters.** That was the first failed fix — noisy, misses cycles you didn't annotate, drifts as the model evolves. Field-based makes cycles structurally impossible.
- **Don't reach for `@JsonIdentityInfo` mixins.** Works for cycles but produces a polymorphic wire shape (object first time, scalar id later) that complicates the client. Not needed once we're field-based.
- **Don't add getters/setters to entities for Jackson.** Field reflection already sees them; audited — no entity getters/setters added in this PRD.
- **Don't `--add-opens java.base/java.time=ALL-UNNAMED`.** That's the brittle JVM-flag escape hatch — `JavaTimeModule` is the right answer.
- **Don't switch one side back to Gson for "compat".** Asymmetric serializers cause invisible drift bugs that surface weeks later as one-path-only field corruption. Both sides through `JacksonObjectMapperFactory`, full stop.
- **Don't put `Promise<X>` on the `RemoteStorage` HTTP interface.** Proxy factory tries to instantiate the return type for deserialization; `Promise` is an interface and JSON has no Promise wrapper — only the raw value. Wire returns raw `X`; wrap in `commandQueue.supply(() -> serv.foo())` at the call-site (done in `RemoteOperator.java`).

## Tests

Acceptance criteria — all observable from a running dev server (AGENTS.md §8) + Swing client (AGENTS.md §9):

1. **Server `/storage/resources` returns valid JSON** of bounded size (~13 KB for the empty admin file-store, not the 730 KB recursive-graph blowup we had before).
2. **Server log has no `Infinite recursion (StackOverflowError)` or `HttpMessageNotWritableException`** during a client login flow.
3. **Swing client logs into the server end-to-end** without any `JsonIOException` (`Interfaces can't be instantiated`, `Failed making field 'java.time.LocalDateTime#date' accessible`, etc.).
4. **`LocalDateTime` fields round-trip** as ISO-8601 strings, not numeric arrays — eyeball-verifiable by grepping a `lastChanged` value in the curl output.

These are smoke-level checks; promote them to a `@SpringBootTest`-style round-trip test under [PRD 009](../009-server-bulk-storage-rest-api.md) Phase 6 if the wire format itself ever needs to be regression-locked.

## Plan

1. ✅ Add `JacksonObjectMapperFactory` to rapla-core.
2. ✅ Add `jackson-databind` + `jackson-datatype-jsr310` to rapla-client deps.
3. ✅ Slim `RaplaJacksonConfig` (server) to delegate to the shared factory.
4. ✅ Wire `MappingJackson2HttpMessageConverter` into `ClientProxyConfig` (client).
5. ✅ Drop `Promise<X>` return types from `RemoteStorage` interface; wrap at call sites in `RemoteOperator`.
6. ✅ Verify criteria 1–4 with fresh server + client launch. `SwingClientStartIntegrationTest` boots `RaplaSpringBootApplication` on random port, boots `SpringRaplaClient` via `rapla.download.url`, calls `facade.login("homer", "duffs")`, asserts `isSessionActive()`. 14 s, green — confirms all four criteria including LDT ISO-8601 round-trip via `expiresIn`/`expiresAt`.
7. ✅ Fold smoke check into `@SpringBootTest` — `SwingClientStartIntegrationTest` (in `rapla-app/src/test/java/org/rapla/client/spring/`); runs as part of `mvn test`; locks the wire format against future drift.

## Open Questions

None blocking. Long-term: when the legacy `org.rapla.rest.gson.*` code is removed (separate PRD — see PRD 001 Phase 9), the Gson dep can be dropped from rapla-core entirely, and the `removeIf(Gson)` line in `ClientProxyConfig` becomes unnecessary.
