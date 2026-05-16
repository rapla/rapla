# PRD 041: OpenAPI — build-time generation + runtime plugin filtering

**Status:** in-progress (Phase 1 done 2026-05-16; runtime plugin filter deferred). Spun off PRD 031's refresh consolidation + PRD 043 (API keys) when testing Scalar/Swagger UI surfaced cracks in the existing /oauth2/token refresh path.
**Date:** 2026-05-15

> **2026-05-16 — adjacent work landed in this session.** Verifying OAuth on the
> served Scalar / Swagger UI explorers exposed that **embedded Spring AS
> doesn't issue refresh tokens to public clients by default**. To make refresh
> work end-to-end (and not paper over the issue), the rest of the session
> consolidated rapla's refresh-token mechanism onto Spring AS's `/oauth2/*`
> endpoints, sharing storage with the existing `/api/auth/refresh` path.
>
> Lands as PRD 031 follow-up:
> - Extracted `RefreshSessionService` (single-source-of-truth: JWT format with
>   `typ=refresh`, full token in user prefs, never-rotate, multi-tab share).
> - `/oauth2/token` now handles `authorization_code` + `refresh_token` +
>   `password` grants, all going through `RefreshSessionService`. Custom Spring
>   AS providers (`PublicClientRefreshTokenAuthenticationConverter` + 3 custom
>   `AuthenticationProvider`s) wire it together.
> - `/oauth2/revoke` clears the user-prefs SESSION entry on revoke (single-
>   token-per-user revocation).
> - Swing client migrated to `/oauth2/token` (form-encoded refresh) +
>   `/oauth2/revoke` (logout).
> - Discovery's `refreshUrl` re-pointed to `/oauth2/token`. SPA needs no
>   client-side change (`angular-oauth2-oidc` already uses `tokenEndpoint` for
>   refresh).
> - `AuthController` kept as `@Deprecated` thin wrapper for tests + scripts;
>   delete in a follow-up PRD that migrates the dozen `loginAs` helpers to
>   `/oauth2/token grant_type=password`.
>
> See PRD 031 (now mostly shipped) for the design discussion + PRD 043 for the
> spun-off API-key work.

## Goal

Take OpenAPI spec *generation* out of the runtime. Today SpringDoc generates
the OpenAPI document at runtime by reflecting over `@RestController`s + DTOs —
which drags **Jackson 2 into the production classpath** (`SwaggerJacksonConfig`
is the Jackson-2 bridge island; the app itself is Jackson 3) and adds
reflective-generation overhead and fragility (field-discovery guessing).

Instead: generate the spec **once at build time** as a static artifact; at
runtime serve a *filtered copy* with disabled-plugin endpoints removed.
SpringDoc and its Jackson 2 dependency become **build/test-scope only** — they
leave the production classpath.

This is a separable REST-infrastructure cleanup, spun out of the PRD 035
design discussion so it can be carried out in its own session.

## Why now

- The Jackson-2 island (`SwaggerJacksonConfig`) and reflective generation are
  standing overhead — see AGENTS.md's Jackson 3 note.
- PRD 035 makes **GraphQL the external API**, which narrows the REST surface
  over time — runtime SpringDoc machinery is increasingly disproportionate.
- But REST is **not** going away: `/api/storage/*` (RemoteStorage, the Swing
  client's interface — PRD 009) is long-lived, plus `/api/auth/*` and the
  export feeds. They still need accurate docs + the SPA's codegen. So the
  answer is not "drop OpenAPI" — it is "move generation to build time."
- The REST OpenAPI is **product-level** (controllers/DTOs are fixed at build,
  not per-deployment), so a build-time spec is byte-identical to what runtime
  generation produced — *except* for which plugins are enabled. That single
  per-deployment variable is handled by a cheap runtime filter (below).

## Scope

**In scope:**

- **Build-time generation.** Produce the superset OpenAPI spec(s) — the four
  PRD 031 groups (`auth`, `client`, `rest`, `exports`) — as static build
  artifacts (`springdoc-openapi-maven-plugin`, or capture in an integration
  test — see Open Questions).
- **Plugin endpoint tagging.** Each plugin-contributed `@RestController`
  carries an OpenAPI **tag = plugin id**, so the runtime filter can identify
  its paths.
- **Runtime filter.** A small component loads the static spec, removes the
  `paths` JSON nodes tagged with plugins not enabled in this deployment
  (plain Jackson-3 `JsonNode` / `ObjectNode.remove()` tree editing — *not*
  SpringDoc, *not* Jackson 2), caches the result at startup, serves it at
  `/api/v3/api-docs` (+ grouped variants). A few dozen lines, one cached
  string, zero reflection.
- **Dependency move.** `springdoc-openapi-starter-*` + the transitive
  `com.fasterxml.jackson:jackson-databind` 2.x move to build/test scope;
  removed from runtime/compile scope of `rapla-app`.
- **Explorer.** Serve a static OpenAPI renderer — **Scalar** recommended, or
  static Swagger UI — **dev / non-prod only**.
- **SPA codegen.** `rapla-angular` `gen:api` retargets to the static spec
  file — no live server needed for codegen.

**Out of scope:**

- The GraphQL schema / transport — PRD 035.
- Retiring OpenAPI entirely — REST stays (auth, exports, RemoteStorage).
- Hand-maintaining the spec — explicitly rejected: RemoteStorage alone is
  ~25+ operations (PRD 009); auto-generation is the point.

## Plan

### Phase 1 — done 2026-05-16

1. ✅ **Build-time generation.** `OpenApiSpecCaptureTest` (rapla-app/src/test/...)
   captures the four group specs from a live `@SpringBootTest` context (springdoc
   on test classpath) and writes them to `rapla-app/src/main/resources/openapi/`.
   Default mode asserts byte-equality against the committed file (catches drift
   from controller / DTO changes); `-Dopenapi.update=true` overwrites.
4. ✅ **SpringDoc + Jackson 2 to test scope.** `springdoc-openapi-starter-webmvc-ui`
   moved from compile to test scope in `rapla-app/pom.xml`. `swagger-annotations-jakarta`
   stays at provided scope (RaplaSpringBootApplication's `@OpenAPIDefinition` etc.
   need to compile). `mvn dependency:tree` confirms `com.fasterxml.jackson.core:jackson-databind:2.x`
   is gone from `rapla-app`'s runtime/compile scope.
   `SpringDocGroupsConfig` + `SwaggerJacksonConfig` + `OpenApiFieldDiscoveryFixture`
   moved to `rapla-app/src/test/java/...` so they're only loaded during the
   capture run.
   `StaticOpenApiController` (rapla-app/src/main/...) serves the captured specs
   at runtime — `@ConditionalOnMissingClass("org.springdoc.webmvc.api.OpenApiWebMvcResource")`
   so it's active in production (springdoc gone) and dormant in tests (springdoc
   on classpath, owns the URLs for the capture run).
5. ✅ **Static explorers.** Both Scalar and Swagger UI served from
   `rapla-app/src/main/resources/static/{scalar,swagger-ui}/index.html`,
   CDN-loaded from jsdelivr — zero bytes added to the fat JAR. Scalar drops the
   modern UI; Swagger UI is the battle-tested fallback. See OQ5 for the explorer
   alternatives evaluated and why CDN beat both webjar + Java integration.
6. ✅ **Retarget SPA `gen:api`.** `rapla-angular/package.json` `gen:api` now
   reads `../rapla-app/src/main/resources/openapi/client.json` directly — no
   live server needed for codegen, deterministic across machines.

### Phase 2 — deferred

2. ⏸ **Tag plugin endpoints** with plugin id. Today's plugin-contributed
   `@RestController`s (Mail, ICal, Exchange, JNDI, Archiver, UrlEncryption,
   EventTimeCalculator, ExternalEventImport, etc.) live in
   `org.rapla.server.spring.web.*` rather than under `org.rapla.plugin.*.server.*`,
   so a tag-by-plugin pass needs a manual mapping step. Not blocking — the
   served spec includes paths for all plugins regardless of deployment-level
   enablement, which matches today's runtime behavior (plugin enablement is
   checked inside the request handler, not at controller registration).
3. ⏸ **Runtime filter component.** Per (2), no plugin enablement metadata
   exists at controller level today. When (2) lands, add a small Jackson-3
   `JsonNode` filter that prunes `paths` for disabled plugins at startup.

## Tests

- **Regression** — build-time-generated spec is byte-identical to today's
  runtime-generated spec (capture current, diff).
- **Runtime filter** — deployment with plugin X disabled → served spec has no
  X paths; X enabled → has them.
- **Classpath** — assertion / `dependency:tree` check that
  `com.fasterxml.jackson:jackson-databind` (Jackson 2) is not in
  `rapla-app`'s runtime/compile scope.
- `ApiPrefixArchitectureTest` still passes (the group machinery runs during
  build-time generation).

## Open Questions

1. ✅ **Generation mechanism** — RESOLVED: capture-in-test
   (`OpenApiSpecCaptureTest`). Reuses the existing `@SpringBootTest` context.
   No new Maven plugin, no extra app boot.
2. ⏸ **Orphan `components/schemas`** — left as-is for v1 (no filter yet).
   Revisit when Phase 2 lands.
3. ⏸ **Plugin enablement timing** — N/A, plugin filter not implemented (Phase 2).
4. ✅ **Static spec location** — RESOLVED: bundled in jar at
   `rapla-app/src/main/resources/openapi/{auth,client,rest,exports}.json`.
   Committed; CI test catches drift via byte-equality assertion.
5. ✅ **`SwaggerJacksonConfig`** — RESOLVED: kept and moved to test sources
   alongside `SpringDocGroupsConfig`. Required during the build-time capture
   to mirror rapla's field-based Jackson 3 visibility onto swagger-core's
   Jackson 2 ModelResolver. Inert at runtime — gone with the rest of the
   springdoc test-scope deps.

### Resolved during implementation

6. ✅ **Runtime API-doc explorer** — both Scalar and Swagger UI, CDN-loaded
   from jsdelivr. Considered + rejected:
   - `com.scalar.maven:scalar-webmvc` — drags Jackson 2 via scalar-core.
   - `org.webjars.npm:scalar__api-reference` — unresolvable nanoid
     version conflicts in the npm dep tree.
   - `org.webjars:swagger-ui` — viable clean webjar (1.2 MiB), zero
     Jackson 2, but bundle-cost vs CDN's zero. Use this if the CDN
     approach is ever rolled back.
   - Self-host via npm + frontend-maven-plugin — viable offline fallback;
     recipe in `static/{scalar,swagger-ui}/index.html` comments.

## Cross-references

- [PRD 031 — API namespace redesign](031-api-namespace-redesign.md) — the four
  SpringDoc groups, `SpringDocGroupsConfig`, `ApiPrefixArchitectureTest`.
- [PRD 035 — External integration API + MCP](035-rapla-mcp-server.md) —
  GraphQL becoming the external API (narrowing the REST surface); the
  OpenAPI-vs-GraphQL "two transports" analysis that spun this PRD out.
- [PRD 009 — Server bulk-storage REST API](009-server-bulk-storage-rest-api.md)
  — RemoteStorage, the long-lived Swing-facing REST surface this keeps
  documented.
- AGENTS.md — the Jackson 3 note; this PRD removes the Jackson-2 island from
  the runtime.
