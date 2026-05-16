# PRD 041: OpenAPI — build-time generation + runtime plugin filtering

**Status:** draft
**Date:** 2026-05-15

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

1. **Wire build-time generation.** Produce the four group specs as build
   artifacts. Regression-check: diff against today's runtime-generated specs —
   must be identical.
2. **Tag plugin endpoints** with plugin id.
3. **Runtime filter component.** Load static spec → JSON-node-remove
   disabled-plugin paths → cache at startup → serve. Optional orphan-schema
   prune.
4. **Move SpringDoc + Jackson 2 to build/test scope.** Drop from runtime;
   verify via `mvn dependency:tree` that Jackson 2 is gone from `rapla-app`'s
   runtime classpath.
5. **Static explorer** (Scalar), dev/non-prod gated.
6. **Retarget SPA `gen:api`** to the static spec.

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

1. **Generation mechanism** — `springdoc-openapi-maven-plugin` (starts a real
   app context during the build) vs capture-in-an-integration-test (reuses the
   cached `@SpringBootTest` context, PRD 007). Lean: capture-in-test.
2. **Orphan `components/schemas`** after filtering — leave (harmless, just
   unused) or reachability-prune. Lean: leave for v1.
3. **Plugin enablement timing** — startup config only, or runtime-toggleable?
   If startup-only, cache the filtered spec once at startup. If toggleable,
   recompute on toggle. Assume startup-only; verify.
4. **Static spec location** — bundled in the jar
   (`src/main/resources/...`) vs generated into `target/` and served. Lean:
   bundled.
5. **`SwaggerJacksonConfig`** — rework vs delete. It bridges Jackson 2 for
   SpringDoc; at build-time-only it may still be needed for the generation
   run, scoped accordingly.

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
