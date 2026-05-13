# PRD 035: rapla as an MCP server — expose scheduling primitives to AI agents

**Status:** draft (2026-05-13)
**Date:** 2026-05-13

## Goal

Make rapla itself callable by AI agents via the Model Context Protocol (MCP). Add a Spring Boot 4 + Spring AI MCP server alongside the existing REST surface, exposing a curated subset of rapla's scheduling primitives as MCP tools:

- `find_free_slots(start, end, durationMinutes, resources?, attendees?)` → list of candidate windows
- `book(reservation)` → confirms a tentative reservation (requires auth scope)
- `query_reservations(filter)` → reservations matching a structured filter
- `query_resources(type?, attribute?)` → resources matching attribute predicates
- `check_conflicts(reservation)` → conflict report against a proposed reservation
- `who_is_free(group, window)` → availability matrix for a group of users

The MCP server is *additive* — the REST API stays as the primary surface for the Swing and Angular clients. MCP is a *third* client tier (after Swing, Angular), aimed at AI assistants embedded in chat tools (Claude Desktop, Claude Code, ChatGPT with MCP, etc.).

## Why now

Three independent signals converging in 2026:

1. **MCP adoption.** 97 M monthly SDK downloads as of March 2026; every major AI vendor (Anthropic, OpenAI, Google, Microsoft, AWS) supports it. Q2 2026 brings OAuth 2.1 + PKCE for browser-based MCP agents — which rapla already has (PRD 029). The auth story matches.
2. **Spring AI lands the MCP layer for Java.** `@McpTool` annotations + Spring's existing DI graph make wiring a new MCP server in a Spring Boot 4 reactor a ~one-day job; we don't write tool dispatch by hand. Per the 2026 literature, MCP-server code in Spring is "just as concise as Python" once `@McpTool` is in play.
3. **rapla's user-facing value is exactly the kind of thing an LLM agent wants to call.** "Find me a free 90-minute slot next Tuesday for these three people in any of the meeting rooms on floor 2" is a one-prompt MCP tool call, but a five-step REST sequence the agent has to compose by hand today.

## Scope

**In scope:**

- A new module `rapla-mcp` in the reactor, depending on `rapla-server` + `rapla-core` + `spring-ai-mcp-server`.
- A `RaplaMcpController` (or `@McpTool` annotated `@Service` beans, the Spring AI idiom) exposing the six tools above.
- Re-use the existing `AuthorizationServerConfig` — MCP clients authenticate via the same OAuth 2.0 Authorization Code + PKCE flow as the Angular SPA. No new auth path.
- Re-use the existing `PermissionController` so MCP tool calls respect the same "filter to user-readable scope" invariant as REST (AGENTS.md §12).
- A new section in `docs/development.md` documenting how to add the rapla MCP server to a local Claude Code session (`claude mcp add rapla http://localhost:8051/mcp …`).
- Smoke test: from a fresh Claude Code session, the agent can `find_free_slots` and `book` against a dev rapla instance.

**Out of scope:**

- Replacing the REST surface. Swing and Angular keep using REST.
- Streaming tools (server-sent events for "watch for new conflicts"). Useful but Phase-2 material.
- Multi-tenant isolation (PRD 002). MCP scope follows the same single-tenant model as REST for v1.
- External-IdP integration (Keycloak / Azure AD). Inherits from the OAuth work in PRDs 029/031; "if REST auth works, MCP auth works."
- Rate limiting or per-tool quotas. Add when there's a real consumer with a real rate.

## Plan

### Phase 1 — Skeleton + one tool end-to-end

1. Add `rapla-mcp` module to the reactor (`rapla-bom`/`pom.xml` reactor list + new module `pom.xml` depending on `rapla-server`, `rapla-core`, `org.springframework.ai:spring-ai-starter-mcp-server`).
2. Wire `@McpTool` on a single tool — start with `query_resources(type, attribute)`. Read-only, the lowest-permission surface.
3. Verify the OAuth gate: unauthenticated MCP calls return 401 (mirrors REST behaviour).
4. Smoke-test from a Claude Code session: `claude mcp add rapla …` → `mcp__rapla__query_resources` shows up → call it → see results.

### Phase 2 — Read-side tools

Add the other read-only tools: `query_reservations`, `who_is_free`, `find_free_slots`, `check_conflicts`. Each gets a tier-3 MockMvc-equivalent test (Spring AI's MCP test harness) covering the happy path + the permission-filter invariant (no leaks of resources the user can't read — AGENTS.md §12 applies).

### Phase 3 — Write-side tools

Add `book(reservation)`. Triggers conflict detection, runs through the same validation as the REST `/dispatch` path. **Requires explicit user confirmation in the MCP client** — the tool definition must include the right `cautious` / `dangerous` marker so Claude Desktop / Claude Code present a permission prompt before invoking. Per AGENTS.md §"Executing actions with care", this is "actions visible to others or that affect shared state."

### Phase 4 — Documentation + skill

1. `docs/development.md` — new section "rapla MCP server" with install (`claude mcp add ...`) and a smoke-test prompt.
2. New skill `.agents/skills/rapla-mcp/SKILL.md` — when to use rapla's MCP tools vs. the REST API; the auth flow; common patterns.
3. `README.md` mention — this is a user-facing feature.

## Tests

- **Tier-2 / tier-3** (per PRD 017): each `@McpTool` method gets a unit test that invokes the bean directly with a real `RaplaFacade` (via `FacadeTestSupport` or the Spring AI test harness) and asserts on the result + the permission filter.
- **Tier-4 e2e**: one `@SpringBootTest(webEnvironment=RANDOM_PORT)` that spins up the MCP server, registers an auth token, calls a tool over the wire, asserts the JSON-RPC response shape. Tagged `@Tag("e2e")` — excluded from default `mvn test`, runs in PRD 034 CI's e2e job (when that lands).

## Open questions

1. **Transport: stdio or Streamable HTTP?** Spring AI MCP server supports both. **Streamable HTTP** is the right answer for rapla (network-accessible, multi-client) per the MCP docs. Stdio only makes sense for local Claude Desktop hosts that spawn a child process — rapla already runs as a long-lived Spring Boot app.
2. **Tool granularity.** Should `find_free_slots` and `who_is_free` be separate tools, or one parametric tool? Separate is more discoverable; parametric is fewer surfaces to maintain. Recommendation: separate — LLM agents pick by tool name better than by parameter shape.
3. **Resource-attribute predicate language.** `query_resources(type, attribute)` needs a small predicate DSL ("attribute.capacity >= 10 AND attribute.floor = 2"). Rapla already has internal `Classification` filter logic — reuse it via a `RaplaFilter` JSON schema vs invent a new query string. Recommendation: lift the existing filter DTO into a typed MCP schema.
4. **Idempotency for `book`.** If an LLM agent retries a `book` call after a transient error, do we get a duplicate reservation? Standard MCP pattern: tool annotation `idempotency_key` parameter, server-side dedup window. Phase 3 material; spec out before implementing.
5. **Scope for permission inheritance.** OAuth scopes today are coarse (`access`). For `book`, do we add a `write` scope? Recommendation: yes, but inherit from existing rapla permission rules — the OAuth scope gates the *tool*, the rapla user permission gates the *action*. Two layers, both enforced.

## Risks

| Risk | Mitigation |
|---|---|
| MCP spec churn through 2026 | Spring AI tracks the spec; semver-major version bumps will be visible. The tools we expose are simple read/write; unlikely to break across spec revs. |
| Permission-leak bugs (LLM agent surfaces resources/reservations the user can't see) | AGENTS.md §12 applies. Every tool gets the permission-leak test. Tier-3 tests where a non-admin user invokes the tool and verifies the response contains no hidden ids. |
| `book` tool used autonomously without user confirmation | Mark the tool `cautious` in the MCP definition; the client UI (Claude Desktop / Claude Code) is responsible for prompting. We can't enforce this server-side; trust the client + log all `book` calls server-side for after-the-fact audit. |
| LLM hallucinates a reservation, agent calls `book`, real meeting gets bumped | Same as above. Plus: server-side store a `created_via=mcp` flag on reservations created through this path so the user can find/revert them quickly. |
| Spring AI MCP starter pulls in Jackson 2 (we run Jackson 3) | Spring AI 1.0+ targets Spring Framework 7 / Jackson 3. Verify before adoption — if it pulls Jackson 2, Phase 1 is "wait for upstream" instead. |

## Cross-references

- [PRD 029 — Swing OAuth login](029-swing-oauth-login.md) — the OAuth surface MCP authenticates against.
- [PRD 031 — Token refresh & API keys](031-token-refresh-and-api-keys.md) — refresh-token mechanics MCP clients inherit.
- [PRD 031 — API namespace redesign](031-api-namespace-redesign.md) — the `/api/` namespace; MCP will likely live at `/mcp/` per Spring AI convention.
- [PRD 017 — Test coverage strategy](017-test-coverage-strategy.md) — pyramid tiers MCP tests slot into.
- [PRD 034 — CI baseline workflow](034-ci-baseline-workflow.md) — MCP tier-4 tests would run in the e2e job once it exists.
- AGENTS.md §12 — permission-leak invariant; every MCP tool must comply.
- AGENTS.md §13 — mock policy; MCP tests use real `RaplaFacade`, not mocks.

## Sources

- [Build an MCP server — Model Context Protocol](https://modelcontextprotocol.io/docs/develop/build-server)
- [Building an MCP Server with Spring AI](https://senoritadeveloper.medium.com/building-an-mcp-server-with-spring-ai-and-testing-with-claude-desktop-e815b5bbd908)
- [Java MCP — Spring AI Alibaba](https://www.alibabacloud.com/blog/java-development-with-mcp-from-claude-automation-to-spring-ai-alibaba-ecosystem-integration_602189)
- [The Complete Guide to MCP in 2026](https://www.essamamdani.com/blog/complete-guide-model-context-protocol-mcp-2026)
- [MCP Hits 97 M Installs — DDR Innova](https://ddrinnova.com/blog/mcp-ai-standard-97-million-installs-2026/)
