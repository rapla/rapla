# 033 — Playwright MCP for browser-driven SPA testing

**Status:** done (2026-05-13). MCP server installed and connected (`claude mcp list` shows `playwright: ✓ Connected`). Install steps moved to `docs/development.md` ("Playwright MCP — install"). Usage patterns folded into the `angular-frontend` skill ("Browser-driven debug + prototype — Playwright MCP"). AGENTS.md §14 carries the 2-line pointer. No dedicated `playwright` skill — usage lives with the SPA skill since that's where the agent already is.

## Goal

Add `@playwright/mcp` as a Claude Code MCP server so an AI agent
can drive a real browser against the rapla SPA, end-to-end:

- Navigate to `http://localhost:4200/app/` (dev) or
  `http://localhost:8051/app/` (prod-parity / fat-JAR).
- Walk the OAuth2 Authorization Code + PKCE flow against
  Spring Authorization Server.
- Inspect DOM, network requests, console messages,
  storage / cookies.
- Take screenshots for visual confirmation.

Replaces the current "agent describes flow → user opens browser
→ pastes errors back" loop with a single tool the agent can call.

## Why

PRD 026 + 031 landed the SPA, OAuth2 flow, REST namespace, and
field-based OpenAPI schema. Three rounds of debugging in that
work would have collapsed to one if the agent could see what the
browser sees:

| Round | What we did | What Playwright MCP would have done |
|---|---|---|
| "CORS error on `/.well-known/openid-configuration`" | User pasted error → agent re-architected proxy config → user reloaded → repeat | Agent would have observed the failed fetch and the missing `Access-Control-Allow-Origin` header in one round |
| "Resources are missing — bundle is `{}`" | Agent added `console.log` → user refreshed → user pasted output → agent diagnosed Blob-vs-JSON | Agent would have seen the response Content-Type and parsed it itself |
| "OAuth callback page 401s mid-flow" | User pasted Network tab snippet → agent guessed at `Location` header rewrite | Agent would have inspected the 302 chain directly |

The pattern: SPA-side bugs need browser observation. `curl` can
prove the wire shape but can't see what the browser does with it
(JSON parser type detection, JS-side state, OAuth library
internals, cookies, sessionStorage). Playwright MCP closes that gap.

## Scope

**In scope:**

- Add `@playwright/mcp` to the project's MCP config.
- Document the install + usage path for both AI agents and humans.
- Add a `.agents/skills/playwright/` skill so future agents know
  when to reach for it (vs. `curl` / `mvn test`).
- Smoke-test the OAuth → reservation-list flow once, end-to-end,
  via the MCP server. Pin the working sequence as a reference.

**Out of scope:**

- Replacing the existing tier-3 MockMvc tests (per PRD 017's
  pyramid — Playwright tests are tier-4 by definition; expensive
  and slow). Playwright is an *additional* tool for things
  MockMvc can't reach (JS-side state, browser-side OAuth flow,
  CORS), not a replacement.
- A regression test suite. That's a follow-up; this PRD just
  enables the tool.
- Cypress / Selenium evaluation. Playwright is the canonical
  modern choice and ships with first-party MCP support; no need
  to comparison-shop.

## Setup

### Once-off install

```bash
# 1. System libs Playwright's bundled Chromium needs (libnss3,
#    libatk-bridge2.0-0, libdrm2, libgbm1, etc.). On WSL2 Ubuntu
#    these are NOT installed by default.
npx playwright install-deps   # apt-installs via sudo

# 2. Browser binaries (~150 MB Chromium + ~80 MB Firefox + ~80 MB WebKit).
npx playwright install        # downloads into ~/.cache/ms-playwright

# 3. Register the MCP server with Claude Code, scoped to this repo.
cd /home/chris/git/rapla
claude mcp add playwright npx '@playwright/mcp@latest'

# 4. Restart Claude Code (or use /mcp in-session) so the new tools
#    surface as `mcp__playwright__browser_*`.
```

After step 4, the agent has `browser_navigate`, `browser_click`,
`browser_fill`, `browser_snapshot`, `browser_screenshot`,
`browser_network_requests`, `browser_console_messages`,
`browser_evaluate`, etc.

### MCP server flags

`claude mcp add playwright npx @playwright/mcp@latest -- --headless --isolated`

| Flag | Effect |
|---|---|
| `--headless` | Force headless even with WSLg available — for unattended runs. |
| `--isolated` | In-memory profile, no disk persistence. Clean state per run. |
| `--browser chromium\|firefox\|webkit\|msedge` | Pick engine. Default chromium. |
| `--viewport-size 1280x720` | Set viewport — important for screenshot diffs. |
| `--user-data-dir ~/.playwright-mcp/profile` | Persistent profile (auth cookies survive restarts). Use when an interactive login produces cookies the agent should reuse. |

Default (no flags): headed Chromium with on-disk profile. Best
for debugging where you want to see the window. For automation
add `--headless --isolated`.

### WSL2 specifics

- **Headless mode works anywhere.** The default flow.
- **Headed mode needs WSLg** (Windows 11 default). If on Windows
  10, set up VcXsrv + `DISPLAY=:0` or run headless.
- **Networking:** WSL2's `localhost` is the host loopback for
  the Playwright subprocess. `http://localhost:4200` and
  `http://localhost:8051` resolve correctly without extra
  configuration.
- **The `libatomic.so.1` issue you hit during Node setup**
  applies here too. `playwright install-deps` handles it; the
  separate apt install you did for Node already covers Chromium.

## Plan

### Phase 1 — Enable the MCP server

1. Run the four install commands above.
2. Verify the agent can see the new tools: in a fresh session,
   ask "list available MCP tools" — `mcp__playwright__browser_*`
   should appear.
3. Quick smoke: ask the agent to `browser_navigate` to
   `http://localhost:8051/` (the chooser landing page, public)
   and screenshot. Confirms the toolchain works.

### Phase 2 — End-to-end OAuth smoke

Pin the working sequence as a reference by asking the agent to:

1. Navigate to `http://localhost:4200/app/` (assumes ng serve +
   Spring Boot per PRD 026 dev workflow).
2. Wait for the auto-redirect to `/login`.
3. Click "Sign in" → browser navigates to `:8051/oauth2/authorize`
   (cross-origin per the PRD 031 architecture: SPA at :4200
   talks to AS at :8051 directly, like Keycloak).
4. Spring login form: fill `admin` / empty password, submit.
5. Browser bounces back to `:4200/app/auth/callback?code=…`.
6. SPA exchanges code for tokens → navigates to `/reservations`.
7. Screenshot the populated table.

Snapshot the agent's tool sequence into the skill (below) so
future invocations don't re-derive it.

### Phase 3 — Skill seed

Add `.agents/skills/playwright/SKILL.md` covering:

- When to reach for Playwright MCP (SPA-side bugs, OAuth flow,
  CORS, browser-internal state) vs. when NOT to (REST contract
  tests — use MockMvc; wire-shape probing — use the existing
  `api-testing` skill with `curl`).
- The OAuth E2E sequence pinned in Phase 2.
- Common patterns: capturing a screenshot before reporting
  "looks good"; reading `browser_console_messages` before
  diagnosing JS errors; using `browser_network_requests` to
  validate request shape rather than guessing.
- WSL gotchas (headed vs. headless, when to use `--user-data-dir`).

## Tests

Browser-driven flows verified end-to-end on the agent's first
run, then captured in the skill as a reference sequence. No
ongoing test-suite commitment in this PRD — that's PRD-N+1
material once we know what's worth automating.

The OpenAPI smoke tests (PRD 031, `OpenApiSmokeTest`) and tier-3
MockMvc tests stay as they are. Playwright tests would sit at
PRD 017's tier-4 level; default `mvn test` would not run them
(tag-excluded under `e2e`).

## Risks

| Risk | Mitigation |
|---|---|
| Agent defaults to `curl` / Bash instead of Playwright tools (Simon Willison's TIL notes this happens) | Mention "use Playwright MCP" explicitly in prompts; once the agent uses it once it tends to keep using it. The skill helps. |
| Headed browser windows pop up unexpectedly during agent runs | Add `--headless` to the MCP config for default-quiet behaviour; opt into headed via a per-task flag when debugging. |
| Browser binary downloads (~310 MB) on slow connections | Once-off, cached in `~/.cache/ms-playwright`. Document in the prerequisites. |
| `playwright install-deps` requires sudo + apt | Documented in the setup section. CI environments may need a different package-install path. |
| Test flakiness from headless timing differences | Standard Playwright wait patterns (`expect(locator).toBeVisible()`, `waitForResponse`). Skill covers the canonical patterns. |
| Cookies / OAuth state leak across runs | Use `--isolated` for automation; use `--user-data-dir` only when the test deliberately wants a persistent session. |

## Open questions

1. **MCP scope: project vs. user.** `claude mcp add` defaults to
   project scope (config in repo's `.mcp.json` or similar). User
   scope (`~/.claude.json`) avoids per-project setup but makes
   the dependency invisible to other contributors. Recommendation:
   project scope, so a fresh clone + `claude mcp` lists the
   server.

2. **Headed by default, or headless?** For interactive debugging
   sessions a headed browser is fastest (you can hand-log in once,
   the agent observes). For automated runs headless is mandatory.
   Recommendation: install MCP with `--headless`, override per-task
   via the agent re-launching with different flags when needed.

3. **`--isolated` always vs. `--user-data-dir`?** Isolated is
   cleaner per-run but means the agent re-logs-in every time.
   For OAuth flows that's a lot of round trips. A persistent
   profile lets the agent log in once per session and reuse the
   token. Recommendation: persistent profile by default; isolated
   only for tests that explicitly need clean state.

4. **CI integration.** This PRD targets local dev only.
   GitHub Actions can run Playwright too (Microsoft maintains an
   official action), but that's a separate concern. Defer.

5. **Which browsers to install?** Chromium covers 90% of cases
   (it's what the SPA targets). WebKit + Firefox are useful for
   cross-browser regression but multiply CI cost. Recommendation:
   Chromium-only by default; add the others if a real
   cross-browser bug surfaces.

## Cross-references

- [PRD 026 — Angular frontend](026-angular-frontend.md): the SPA
  this would test.
- [PRD 031 — API namespace redesign](031-api-namespace-redesign.md):
  the OAuth + URL layout the test sequence walks.
- [PRD 017 — Testing pyramid](017-testing-pyramid.md): Playwright
  tests sit at tier 4 (E2E). Most testing stays at tiers 1-3.
- AGENTS.md §14 — Angular frontend rules. Adds context for
  Playwright's role alongside `ng build` / `mvn test`.
- `.agents/skills/api-testing/SKILL.md`: the wire-shape probing
  skill. Playwright is its complement for browser-internal state.

## Sources

- [Playwright MCP — Microsoft official repo](https://github.com/microsoft/playwright-mcp)
- [Playwright MCP — official docs](https://playwright.dev/docs/getting-started-mcp)
- [Using Playwright MCP with Claude Code — Simon Willison](https://til.simonwillison.net/claude-code/playwright-mcp-claude-code)
- [Headed Playwright with WSL — Matt Kleinsmith](https://medium.com/@matthewkleinsmith/headful-playwright-with-wsl-4bf697a44ecf)
- [Playwright MCP & Claude Code — testomat.io](https://testomat.io/blog/playwright-mcp-claude-code/)
- [How to Use Playwright MCP Server with Claude Code — Builder.io](https://www.builder.io/blog/playwright-mcp-server-claude-code)
