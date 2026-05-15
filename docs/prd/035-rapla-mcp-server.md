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
- **Three showcase recordings** per Phase 0 — OpenClaw + voice + multi-channel (lead), OpenCode + Ollama (dev/ops), Claude Desktop (polished consumer). Same demo script, three runtimes. Demonstrates rapla MCP is portable across agent runtimes and works fully local.

**Out of scope:**

- Replacing the REST surface. Swing and Angular keep using REST.
- Streaming tools (server-sent events for "watch for new conflicts"). Useful but Phase-2 material.
- Multi-tenant isolation (PRD 002). MCP scope follows the same single-tenant model as REST for v1.
- External-IdP integration (Keycloak / Azure AD). Inherits from the OAuth work in PRDs 029/031; "if REST auth works, MCP auth works."
- Rate limiting or per-tool quotas. Add when there's a real consumer with a real rate.
- **M365 Copilot deployment** — public-deployed MCP server + Entra integration + Copilot Studio agent. Defer to **PRD 036** (to be drafted) — it's the production end-user story but adds an order of magnitude more setup (public TLS, Entra app registration, paid Copilot Studio license) and is independently scoped.

## Plan

### Phase 0 — Showcase scope (drives implementation priority)

The purpose of this PRD lands as concrete *demos*, not just an MCP endpoint. Three showcase tracks ranked by strategic value. Each requires a specific subset of MCP tools, which dictates which tools land first in Phase 1+2+3.

| # | Track | Stack | Required MCP tools | Why this matters |
|---|---|---|---|---|
| 1 | **OpenClaw + Ollama + multi-channel + voice** (lead) | OpenClaw onboard daemon → Ollama (`qwen3.5:9b` or NemoClaw) → rapla MCP (stdio); channels: WhatsApp + macOS/iOS voice + Slack | `find_free_slots`, `book`, `query_reservations` | "Talk to your phone or your existing chat app, your meetings happen." Highest visceral impact. Fully local-first, fully self-hosted. Strongest pitch for institutional / data-sovereign customers (universities, public sector). Sidesteps Microsoft + Anthropic infrastructure entirely. |
| 2 | **OpenCode + Ollama (terminal)** | `opencode` CLI → Ollama → rapla MCP (stdio), config in `opencode.json` | Same | "Devs/ops can self-host the entire stack." Dev/ops audience. Cheap to record once Track 1 is wired (same MCP, different runtime). |
| 3 | **Claude Desktop + Anthropic API** | Claude Desktop → Anthropic API → rapla MCP via `claude_desktop_config.json` (stdio) | Same | "Polished consumer experience." Reference benchmark for visual polish. Useful for technical evaluator demos. |
| 4 | M365 Copilot in Outlook (deferred to PRD 036) | Public-deployed rapla MCP (Streamable HTTP) → Entra-federated OAuth → Copilot Studio agent | Same + write-side hardening | "Lives in the tools your enterprise already pays for." Production end-user story. Requires public deployment + Entra registration + Copilot Studio license — split into a follow-up PRD because it's an order of magnitude more setup. |

**Implication for Phase ordering:** Tracks 1–3 share the same MCP surface (`find_free_slots`, `book`, `query_reservations`). Phase 1 + Phase 3 deliver this minimum set. Phase 2's other read tools (`who_is_free`, `check_conflicts`) are nice-to-have for the demos but not blocking. Phase 4's documentation closes the loop.

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

### Phase 5 — Showcase recording (per Phase 0 tracks)

Once Phases 1+3 produce the minimum tool set (`find_free_slots`, `book`, `query_reservations`), record the three showcase tracks. Same demo script across all three; different runtimes. Each ~60–90 seconds.

**Shared demo script:**

```
1. "Find me a conference room for 10 people next Tuesday afternoon."
   → agent calls find_free_slots → presents 2 candidates
2. "Book Conference Room A for 14:00–15:30, title 'Team retro',
    invite Alice and Bob."
   → agent calls book → confirmation
3. "What's on my calendar next week?"
   → agent calls query_reservations → formatted list
```

**Recording deliverables:**

| Track | Recording target | Use |
|---|---|---|
| 1 (OpenClaw) | 60–90 s screencast of voice command on phone + WhatsApp DM with assistant. Plus a 30-second still-shot of Slack DM flow. | Lead pitch in PRD 035 + README + rapla.org. Conference / customer pitch lead. |
| 2 (OpenCode) | 60 s terminal-cast (asciinema or video) of `opencode` driving the same flow. | Dev-audience evidence; embedded in `docs/development.md`. |
| 3 (Claude Desktop) | 60 s screencast of Claude Desktop chat with explicit tool-approval cards visible. | Technical-evaluator reference; embedded in PRD 035. |

Pin all three under `docs/showcases/` (or equivalent). Embed the lead (Track 1) in `README.md`, PRD 035 status block, and any future pitch deck.

**Caveat to acknowledge in scripts:** local-LLM tool calling (Tracks 1 + 2) is solid for single-tool demos but rougher on multi-step orchestration. Stick to the script; don't ad-lib. Re-record cleanly if a take goes off-rails.

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
