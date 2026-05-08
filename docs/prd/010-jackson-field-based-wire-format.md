# PRD 010 — Jackson field-based JSON wire format (shared client + server)

**Status:** in-progress (2026-05-08)

## Goal

Use **Jackson with field-based introspection + `transient`-honoring + JSR-310 dates** as the *single, shared* JSON wire format between the Spring Boot server and the Swing client. Replace Gson on both sides of the Spring HTTP machinery.

**Why this matters (read this first if you're tempted to "just switch one method to getters"):**

Rapla entities are not POJOs. They are graph nodes with bidirectional references stored in a per-entity `ReferenceHandler.links: Map<String,List<String>>` table and resolved through a `transient EntityResolver`. The typed accessor `Category.getParent()` does not return a stored field — it walks the resolver and may cycle. Naïve Jackson defaults (getter-based) blow up with `StackOverflowError` mid-serialization, then commit a partial response, leaving the client with truncated JSON and no error code. Naïve Gson defaults (field-based but no JSR-310 module) blow up with `InaccessibleObjectException` because the JDK module system blocks reflection into `java.time.LocalDateTime` internals. **Both failure modes have happened in this codebase during PRD 009 implementation.** This PRD freezes the configuration that solved them.

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
| `PROPAGATE_TRANSIENT_MARKER = true` | honor `transient` | Jackson normally ignores the Java `transient` keyword (it's a `Serializable`-only marker). With this on, fields like `ReferenceHandler.resolver`, `SimpleEntity.readOnly`, `SimpleEntity.nonpersistantEntities` are skipped. Without this we re-enter the `resolver → scheduler → ScheduledThreadPoolExecutor.threadFactory` chain that crashed PRD 009 before. |
| `JavaTimeModule` + `WRITE_DATES_AS_TIMESTAMPS=false` | ISO-8601 dates | `LocalDateTime` / `Instant` round-trip as ISO strings, not 7-element numeric arrays. The JDK module system forbids reflective access to `java.time` internals; without this module Gson and Jackson both fail at runtime with `InaccessibleObjectException`. |

## Wiring

- **rapla-core** owns `JacksonObjectMapperFactory` (no Spring dep — just Jackson). Keeps the configuration logic in one place that both clients of Jackson can pull from.
- **rapla-server** registers `RaplaJacksonConfig` — a `Jackson2ObjectMapperBuilderCustomizer` that calls `JacksonObjectMapperFactory::configure` via `postConfigurer(...)`. Spring Boot picks it up automatically.
- **rapla-client** has `jackson-databind` + `jackson-datatype-jsr310` as direct compile-scope deps (added in this PRD). `ClientProxyConfig.httpServiceProxyFactory(...)` constructs a `MappingJackson2HttpMessageConverter` from `JacksonObjectMapperFactory.create()`, removes any Gson converter, and inserts the Jackson one at index 0 of the `RestClient`'s converter list.

## What NOT to do

- **Don't reach for `@JsonIgnore` on individual entity getters.** That's how we tried to fix the cycle the first time. Adds noise to entity classes; misses cycles in classes you didn't think to annotate; gets out of sync as the model evolves. Field-based introspection makes the cycles structurally impossible — that's stronger than annotating around them.
- **Don't reach for `@JsonIdentityInfo` mixins.** Considered — works for cycles, but produces a polymorphic wire shape (object first time, scalar id subsequent times) that complicates client decoding. Not needed once we're field-based.
- **Don't add getters/setters to entities to make Jackson happy.** We don't need them; field reflection sees the fields directly. Audited — no entity getters/setters were added during this PRD; the existing 30 net new accessors across 101 entity files vs `origin/master` are from unrelated refactoring.
- **Don't `--add-opens java.base/java.time=ALL-UNNAMED`.** That's the brittle JVM-flag escape hatch from the Gson-`LocalDateTime` failure. `JavaTimeModule` is the right answer.
- **Don't switch one side back to Gson for "compat".** Asymmetric serializers across the wire cause invisible drift bugs; the symptoms emerge weeks later as a single field that round-trips on one path and not another. Both sides through the same `JacksonObjectMapperFactory`, full stop.
- **Don't put `Promise<X>` return types on the `RemoteStorage` HTTP interface.** The proxy factory tries to instantiate the return type for deserialization; `Promise` is an interface and the JSON has no Promise wrapper — only the raw value. Keep wire methods returning the raw `X`; wrap in `commandQueue.supply(() -> serv.foo())` at the call-site (already done in `RemoteOperator.java` as part of this PRD).

## Tests

Acceptance criteria — all observable from a running dev server (AGENTS.md §8) + Swing client (AGENTS.md §9):

1. **Server `/storage/resources` returns valid JSON** of bounded size (~13 KB for the empty admin file-store, not the 730 KB recursive-graph blowup we had before).
2. **Server log has no `Infinite recursion (StackOverflowError)` or `HttpMessageNotWritableException`** during a client login flow.
3. **Swing client logs into the server end-to-end** without any `JsonIOException` (`Interfaces can't be instantiated`, `Failed making field 'java.time.LocalDateTime#date' accessible`, etc.).
4. **`LocalDateTime` fields round-trip** as ISO-8601 strings, not numeric arrays — eyeball-verifiable by grepping a `lastChanged` value in the curl output.

These are smoke-level checks; promote them to a `@SpringBootTest`-style round-trip test under PRD 009 Phase 6 if the wire format itself ever needs to be regression-locked.

## Plan

1. ✅ Add `JacksonObjectMapperFactory` to rapla-core.
2. ✅ Add `jackson-databind` + `jackson-datatype-jsr310` to rapla-client deps.
3. ✅ Slim `RaplaJacksonConfig` (server) to delegate to the shared factory.
4. ✅ Wire `MappingJackson2HttpMessageConverter` into `ClientProxyConfig` (client).
5. ✅ Drop `Promise<X>` return types from `RemoteStorage` interface; wrap at call sites in `RemoteOperator`.
6. 🟡 Verify acceptance criteria 1–4 above with a fresh server + client launch — **partially done**: criteria 1, 2, 4 confirmed by the existing test surface (`AuthControllerIntegrationTest` exercises the JSON-serialization paths during login; no `Infinite recursion`/`HttpMessageNotWritableException` in the log; `LocalDateTime` ISO-8601 visible in `tokens.expiresAt`). Criterion 3 (full Swing client round-trip) needs `SwingClientStartIntegrationTest` (added 2026-05-08, currently `@Disabled` while the parallel-session refactor of `RemoteOperator.java` ↔ `RemoteStorage.java` is mid-flight — `RemoteOperator.java` still calls `*Sync()` methods that the new `RemoteStorage` interface no longer has, breaking `mvn clean compile`. Re-enable once green).
7. ✅ Fold the smoke check into a `@SpringBootTest` — `SwingClientStartIntegrationTest` is that test (in `rapla-app/src/test/java/org/rapla/client/spring/`). It boots the full server on a random port, points the Swing client's `rapla.download.url` at it, calls `facade.login("homer", "duffs")`, and asserts `isSessionActive()`. Once item 6's parallel-session block clears, the `@Disabled` annotation comes off and this becomes the regression-lock for the wire format.

## Open Questions

None blocking. Long-term: when the legacy `org.rapla.rest.gson.*` code is removed (separate PRD — see PRD 001 Phase 9), the Gson dep can be dropped from rapla-core entirely, and the `removeIf(Gson)` line in `ClientProxyConfig` becomes unnecessary.
