# PRD 041: OpenAPI — build-time generation + runtime plugin filtering

**Status:** in-progress (Phase 1 done 2026-05-16; runtime plugin filter deferred). Spun off PRD 031's refresh consolidation + [PRD 043](043-api-keys-jwt-pat.md) (API keys) when testing Scalar/Swagger UI surfaced cracks in the existing /oauth2/token refresh path.
**Date:** 2026-05-15

> **2026-05-16 — adjacent work landed in this session.** Verifying OAuth on the served Scalar / Swagger UI explorers exposed that **embedded Spring AS doesn't issue refresh tokens to public clients by default**. To make refresh work end-to-end (and not paper over the issue), the rest of the session consolidated rapla's refresh-token mechanism onto Spring AS's `/oauth2/*` endpoints, sharing storage with the existing `/api/auth/refresh` path.
>
> Lands as PRD 031 follow-up:
> - Extracted `RefreshSessionService` (single-source-of-truth: JWT format with `typ=refresh`, full token in user prefs, never-rotate, multi-tab share).
> - `/oauth2/token` now handles `authorization_code` + `refresh_token` + `password` grants, all through `RefreshSessionService`. Custom Spring AS providers (`PublicClientRefreshTokenAuthenticationConverter` + 3 custom `AuthenticationProvider`s) wire it together.
> - `/oauth2/revoke` clears the user-prefs SESSION entry on revoke (single-token-per-user revocation).
> - Swing client migrated to `/oauth2/token` (form-encoded refresh) + `/oauth2/revoke` (logout).
> - Discovery's `refreshUrl` re-pointed to `/oauth2/token`. SPA needs no client-side change (`angular-oauth2-oidc` already uses `tokenEndpoint` for refresh).
> - `AuthController` **deleted** — same session migrated the ~12 test `loginAs` helpers to the new `OAuthTestSupport.loginAs(MockMvc, …)` helper that posts `/oauth2/token grant_type=password`. Swing logout migrated to `/oauth2/revoke`. `refreshUrl` field dropped from discovery + SPA + Swing.
> - **Client-side follow-up (2026-05-16):** the Swing *direct-password* path (fallback login dialog + `MyCustomConnector` password-reauth) still called the removed `POST /api/auth/login` via `RemoteAuthentificationService` `@HttpExchange` proxy. Fixed by swapping that bean for `ClientProxyConfig.OAuth2RemoteAuthentificationService`, which posts `/oauth2/token grant_type=password`. Dead `RemoteAuthentificationServiceImpl` removed. See `docs/authentication.md`.
>
> See PRD 031 (now mostly shipped) for the design discussion + [PRD 043](043-api-keys-jwt-pat.md) for the spun-off API-key work.
>
> ## OAuth-refresh consolidation — final architecture (added 2026-05-16)
>
> ### Single source of truth: `RefreshSessionService`
>
> Lives in `rapla-server/.../spring/RefreshSessionService.java` (autoconfig picks it up via `@Service` + explicit `@Import` in `RaplaServerAutoConfiguration` since component-scan only covers `org.rapla.server.spring.web`). Owns the entire refresh-token lifecycle — both Spring AS's `/oauth2/token` and any future caller go through it.
>
> | Method | Purpose |
> |---|---|
> | `issueAndPersist(User)` | At login (any grant). If user has a stored valid refresh JWT, return THAT; else mint + persist. Multi-tab share. |
> | `issueAndPersistRefreshToken(User)` | Hook for Spring AS's custom token generator on the authorization_code grant. |
> | `issueAccessToken(User)` | Short access token mint during refresh. |
> | `validate(presentedRefreshToken)` | JWT signature + `typ=refresh` + exact match against `SESSION` prefs entry. Throws on any failure. |
> | `clearSession(User)` | Wipes the SESSION entry → all refresh tokens for user become invalid. Called by `/oauth2/revoke` hook. |
> | `persistSession(User, refreshToken)` | Public for test fixtures + Spring AS issuance hooks. |
>
> Storage: full JWT in user prefs under `TypedComponentRole<String> SESSION = "org.rapla.auth.session"`. Single slot. Defense-in-depth argument for storing only a hash was moot because the persistent RSA signing key is *also* in the data file — backup leak = game-over regardless. Storing the full JWT lets `issueAndPersist` return the existing valid token on subsequent logins (multi-tab/multi-device share).
>
> ### Custom Spring AS providers (`AuthorizationServerConfig`)
>
> Six bits of custom wiring:
>
> 1. **`tokenGenerator()` @Bean** — `DelegatingOAuth2TokenGenerator(JwtGenerator, OAuth2AccessTokenGenerator, JwtRefreshTokenGenerator)`. Replaces the stock chain so refresh tokens go through `JwtRefreshTokenGenerator` (JWT format with `typ=refresh`, persisted by `RefreshSessionService`).
> 2. **`JwtRefreshTokenGenerator`** (private inner class) — generates the refresh JWT via `RefreshSessionService.issueAndPersistRefreshToken`. Spring AS's default `OAuth2RefreshTokenGenerator` suppresses for public clients (PKCE / `auth_method=NONE`); this one issues for everyone.
> 3. **`PublicClientRefreshTokenAuthenticationConverter`** (client-auth) — recognises requests with `client_id` only (no `client_secret`, no `code_verifier`) and produces an unauthenticated `OAuth2ClientAuthenticationToken` with method NONE. Covers refresh, password, revoke, introspect. Required because Spring AS's stock `PublicClientAuthenticationConverter` only matches PKCE token requests (i.e. `authorization_code` with code_verifier).
> 4. **`PublicClientRefreshTokenAuthenticationProvider`** (client-auth) — validates the converter's output: client_id resolves to a registered client, client's `client_authentication_methods` includes NONE.
> 5. **`RaplaRefreshTokenAuthenticationProvider`** (token-endpoint) — replaces the stock `OAuth2RefreshTokenAuthenticationProvider`. Validates via `RefreshSessionService.validate` (stateless, restart-safe) instead of looking up in the in-memory `OAuth2AuthorizationService`. Never rotates: returns same refresh token + fresh access token.
> 6. **`PasswordGrantAuthenticationConverter` + `PasswordGrantAuthenticationProvider`** (token-endpoint) — implements OAuth 2.0 Resource Owner Password Credentials grant (RFC 6749 §4.3). Spring AS removed it from defaults (OAuth 2.1 BCP deprecates); rapla re-enables it to replace the deleted `/api/auth/login`. Provider delegates credential check to `RaplaAuthentificationService`, then issues tokens via `RefreshSessionService`.
> 7. **`RaplaTokenRevocationAuthenticationProvider`** (revoke-endpoint) — decodes the token JWT, resolves user from `sub`, calls `RefreshSessionService.clearSession`. RFC 7009 compliant (always returns 200, even for unknown tokens).
>
> Wired via `OAuth2AuthorizationServerConfigurer`'s `.tokenGenerator()`, `.clientAuthentication()`, `.tokenEndpoint()`, `.tokenRevocationEndpoint()`.
>
> ### Rotation policy: never rotate
>
> Earlier design tried "rotate when within 7 d of expiry" (PRD 031 original). Rejected during this session in favor of never-rotate: multi-tab works trivially (tab A doesn't kick tab B off when it refreshes), all sessions re-Authorize together at refresh-token expiry (21 d). Theft detection lost, but `/oauth2/revoke` + the single-token-per-user store give clean explicit revocation. For per-session rotation + theft detection, deploy against Keycloak (PRD 031: IdP swap is env-var only).
>
> ### `application.yml` knob
>
> Only one new property under `spring.security.oauth2.authorizationserver.client.rapla-client.token`:
>
> - `access-token-time-to-live: 1h` — bumped from Spring AS's 5 min default so OAuth-aware explorers (Scalar, Swagger UI) don't re-Authorize every 5 minutes for dev workflows. Refresh-token TTL is hard-coded (`REFRESH_TOKEN_TTL_SECONDS = 21 d` in `RefreshSessionService`).
>
> Plus `password` added to `rapla-client.authorization-grant-types` so the grant is allowed for that client.
>
> ### Migration table (for future readers)
>
> | Deleted | Replaced by |
> |---|---|
> | `POST /api/auth/login` JSON `{username, password}` → `{accessToken, refreshToken}` (camelCase) | `POST /oauth2/token` form-encoded `grant_type=password&username=…&password=…&client_id=rapla-client` → `{access_token, refresh_token, expires_in, token_type}` (snake_case) |
> | `POST /api/auth/refresh` JSON `{refreshToken}` | `POST /oauth2/token` form-encoded `grant_type=refresh_token&refresh_token=…&client_id=rapla-client` |
> | `POST /api/auth/logout` Bearer | `POST /oauth2/revoke` form-encoded `token=…&token_type_hint=refresh_token&client_id=rapla-client` |
> | `OAuthConfig.refreshUrl` discovery field | use `tokenUrl` (same endpoint serves both code-exchange and refresh per OAuth 2.0) |
>
> Survivors under `/api/auth/`:
>
> - `/api/auth/oauth/config` — discovery
> - `/api/auth/oauth/exchange/{providerId}` — BFF for external IdPs ([PRD 036](036-external-idp-oauth-login.md))
> - `/api/auth/api-keys/*` — personal-access-token CRUD ([PRD 043](043-api-keys-jwt-pat.md))

## Goal

Take OpenAPI spec *generation* out of the runtime. Today SpringDoc generates the OpenAPI document at runtime by reflecting over `@RestController`s + DTOs — which drags **Jackson 2 into the production classpath** (`SwaggerJacksonConfig` is the Jackson-2 bridge island; the app itself is Jackson 3) and adds reflective-generation overhead and fragility.

Instead: generate the spec **once at build time** as a static artifact; at runtime serve a *filtered copy* with disabled-plugin endpoints removed. SpringDoc and its Jackson 2 dependency become **build/test-scope only** — they leave the production classpath.

Separable REST-infrastructure cleanup, spun out of [PRD 035](done/035-graphql-foundations.md) design discussion.

## Why now

- The Jackson-2 island (`SwaggerJacksonConfig`) and reflective generation are standing overhead — see AGENTS.md's Jackson 3 note.
- [PRD 035](done/035-graphql-foundations.md) makes **GraphQL the external API**, which narrows the REST surface over time — runtime SpringDoc machinery is increasingly disproportionate.
- But REST is **not** going away: `/api/storage/*` (RemoteStorage, the Swing client's interface — [PRD 009](009-server-bulk-storage-rest-api.md)) is long-lived, plus `/api/auth/*` and export feeds. They still need accurate docs + the SPA's codegen. The answer is not "drop OpenAPI" — it is "move generation to build time."
- The REST OpenAPI is **product-level** (controllers/DTOs are fixed at build, not per-deployment), so a build-time spec is byte-identical to runtime-generated — *except* for which plugins are enabled. That single per-deployment variable is handled by a cheap runtime filter.

## Scope

**In scope:**

- **Build-time generation.** Produce the superset OpenAPI spec(s) — the four PRD 031 groups (`auth`, `client`, `rest`, `exports`) — as static build artifacts (`springdoc-openapi-maven-plugin`, or capture in an integration test — see Open Questions).
- **Plugin endpoint tagging.** Each plugin-contributed `@RestController` carries an OpenAPI **tag = plugin id**, so the runtime filter can identify its paths.
- **Runtime filter.** A small component loads the static spec, removes the `paths` JSON nodes tagged with plugins not enabled in this deployment (plain Jackson-3 `JsonNode` / `ObjectNode.remove()` tree editing — *not* SpringDoc, *not* Jackson 2), caches the result at startup, serves at `/api/v3/api-docs` (+ grouped variants). A few dozen lines, one cached string, zero reflection.
- **Dependency move.** `springdoc-openapi-starter-*` + the transitive `com.fasterxml.jackson:jackson-databind` 2.x move to build/test scope; removed from runtime/compile scope of `rapla-app`.
- **Explorer.** Serve a static OpenAPI renderer — **Scalar** recommended, or static Swagger UI — **dev / non-prod only**.
- **SPA codegen.** `rapla-angular` `gen:api` retargets to the static spec file — no live server needed for codegen.

**Out of scope:**

- The GraphQL schema / transport — [PRD 035](done/035-graphql-foundations.md).
- Retiring OpenAPI entirely — REST stays (auth, exports, RemoteStorage).
- Hand-maintaining the spec — explicitly rejected: RemoteStorage alone is ~25+ operations ([PRD 009](009-server-bulk-storage-rest-api.md)); auto-generation is the point.

## Plan

### Phase 1 — done 2026-05-16

1. ✅ **Build-time generation.** `OpenApiSpecCaptureTest` (rapla-app/src/test/...) captures the four group specs from a live `@SpringBootTest` context (springdoc on test classpath) and writes them to `rapla-app/src/main/resources/openapi/`. Default mode asserts byte-equality against the committed file (catches drift from controller / DTO changes); `-Dopenapi.update=true` overwrites.
4. ✅ **SpringDoc + Jackson 2 to test scope.** `springdoc-openapi-starter-webmvc-ui` moved from compile to test scope in `rapla-app/pom.xml`. `swagger-annotations-jakarta` stays at provided scope (RaplaSpringBootApplication's `@OpenAPIDefinition` etc. need to compile). `mvn dependency:tree` confirms `com.fasterxml.jackson.core:jackson-databind:2.x` is gone from `rapla-app`'s runtime/compile scope. `SpringDocGroupsConfig` + `SwaggerJacksonConfig` + `OpenApiFieldDiscoveryFixture` moved to `rapla-app/src/test/java/...` so they're only loaded during the capture run. `StaticOpenApiController` (rapla-app/src/main/...) serves the captured specs at runtime — `@ConditionalOnMissingClass("org.springdoc.webmvc.api.OpenApiWebMvcResource")` so it's active in production (springdoc gone) and dormant in tests (springdoc on classpath, owns the URLs for the capture run).
5. ✅ **Static explorers.** Both Scalar and Swagger UI served from `rapla-app/src/main/resources/static/{scalar,swagger-ui}/index.html`, CDN-loaded from jsdelivr — zero bytes added to the fat JAR. Scalar drops the modern UI; Swagger UI is the battle-tested fallback. See OQ5 for explorer alternatives evaluated.
6. ✅ **Retarget SPA `gen:api`.** `rapla-angular/package.json` `gen:api` now reads `../rapla-app/src/main/resources/openapi/client.json` directly — no live server needed for codegen, deterministic across machines.

### Phase 2 — deferred

2. ⏸ **Tag plugin endpoints** with plugin id. Today's plugin-contributed `@RestController`s (Mail, ICal, Exchange, JNDI, Archiver, UrlEncryption, EventTimeCalculator, ExternalEventImport, etc.) live in `org.rapla.server.spring.web.*` rather than under `org.rapla.plugin.*.server.*`, so a tag-by-plugin pass needs a manual mapping step. Not blocking — served spec includes paths for all plugins regardless of deployment-level enablement, which matches today's runtime behavior (plugin enablement is checked inside the request handler, not at controller registration).
3. ⏸ **Runtime filter component.** Per (2), no plugin enablement metadata exists at controller level today. When (2) lands, add a small Jackson-3 `JsonNode` filter that prunes `paths` for disabled plugins at startup.

## Tests

- **Regression** — build-time-generated spec is byte-identical to today's runtime-generated spec (capture current, diff).
- **Runtime filter** — deployment with plugin X disabled → served spec has no X paths; X enabled → has them.
- **Classpath** — assertion / `dependency:tree` check that `com.fasterxml.jackson:jackson-databind` (Jackson 2) is not in `rapla-app`'s runtime/compile scope.
- `ApiPrefixArchitectureTest` still passes (the group machinery runs during build-time generation).

## Open Questions

1. ✅ **Generation mechanism** — RESOLVED: capture-in-test (`OpenApiSpecCaptureTest`). Reuses existing `@SpringBootTest` context. No new Maven plugin, no extra app boot.
2. ⏸ **Orphan `components/schemas`** — left as-is for v1 (no filter yet). Revisit when Phase 2 lands.
3. ⏸ **Plugin enablement timing** — N/A, plugin filter not implemented (Phase 2).
4. ✅ **Static spec location** — RESOLVED: bundled in jar at `rapla-app/src/main/resources/openapi/{auth,client,rest,exports}.json`. Committed; CI test catches drift via byte-equality assertion.
5. ✅ **`SwaggerJacksonConfig`** — RESOLVED: kept and moved to test sources alongside `SpringDocGroupsConfig`. Required during build-time capture to mirror rapla's field-based Jackson 3 visibility onto swagger-core's Jackson 2 ModelResolver. Inert at runtime — gone with the rest of springdoc test-scope deps.

### Resolved during implementation

6. ✅ **Runtime API-doc explorer** — both Scalar and Swagger UI, CDN-loaded from jsdelivr. Considered + rejected:
   - `com.scalar.maven:scalar-webmvc` — drags Jackson 2 via scalar-core.
   - `org.webjars.npm:scalar__api-reference` — unresolvable nanoid version conflicts in the npm dep tree.
   - `org.webjars:swagger-ui` — viable clean webjar (1.2 MiB), zero Jackson 2, but bundle-cost vs CDN's zero. Use this if CDN approach is ever rolled back.
   - Self-host via npm + frontend-maven-plugin — viable offline fallback; recipe in `static/{scalar,swagger-ui}/index.html` comments.

## Cross-references

- [PRD 031 — API namespace redesign](031-api-namespace-redesign.md) — the four SpringDoc groups, `SpringDocGroupsConfig`, `ApiPrefixArchitectureTest`.
- [PRD 035 (done) — GraphQL foundations](done/035-graphql-foundations.md) — GraphQL becoming the external API; the OpenAPI-vs-GraphQL "two transports" analysis that spun this PRD out.
- [PRD 009 — Server bulk-storage REST API](009-server-bulk-storage-rest-api.md) — RemoteStorage, the long-lived Swing-facing REST surface this keeps documented.
- AGENTS.md — the Jackson 3 note; this PRD removes the Jackson-2 island from the runtime.
