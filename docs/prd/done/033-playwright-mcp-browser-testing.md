# 033 — Playwright MCP for browser-driven SPA testing

**Status:** done (2026-05-13). MCP server installed and connected (`claude mcp list` shows `playwright: ✓ Connected`). Install steps moved to `docs/development.md` ("Playwright MCP — install"). Usage patterns folded into the `angular-frontend` skill ("Browser-driven debug + prototype — Playwright MCP"). AGENTS.md §14 carries the 2-line pointer. No dedicated `playwright` skill — usage lives with the SPA skill since that's where the agent already is.

## Goal

Add `@playwright/mcp` as a Claude Code MCP server so an agent can drive a real browser against the rapla SPA (dev `:4200/app/` or prod `:8051/app/`), walk the OAuth2 + PKCE flow, inspect DOM/network/console/storage, screenshot. Replaces the "agent describes flow → user pastes errors" loop with one callable tool.

## Why

PRD [026](../026-angular-frontend.md) + 031's three SPA debug rounds would have collapsed to one with browser observation:

| Round | Was | Would be |
|---|---|---|
| CORS on `/.well-known/openid-configuration` | User paste → agent re-architects → repeat | Agent sees missing `Access-Control-Allow-Origin` directly |
| Bundle `{}` | `console.log` → user paste → agent diagnoses Blob-vs-JSON | Agent reads response Content-Type directly |
| OAuth callback 401 mid-flow | User Network-tab paste → agent guesses | Agent inspects 302 chain directly |

`curl` proves wire shape but can't see browser behaviour (JSON parser type detection, JS-side state, OAuth internals, cookies, sessionStorage).

## Scope

**In scope:** add `@playwright/mcp` to project MCP config; document install + usage for agents and humans; add `.agents/skills/playwright/` skill; smoke-test OAuth → reservation-list, pin the sequence.

**Out of scope:** replacing tier-3 MockMvc (Playwright is tier-4 — additional, not replacement); a regression suite (follow-up); Cypress/Selenium evaluation (Playwright is canonical with first-party MCP).

## Setup

### Once-off install

```bash
# 1. System libs for bundled Chromium (libnss3, libatk-bridge2.0-0, libdrm2, libgbm1).
#    WSL2 Ubuntu: not installed by default.
npx playwright install-deps   # apt via sudo

# 2. Browser binaries (~150 MB Chromium + ~80 MB Firefox + ~80 MB WebKit).
npx playwright install        # → ~/.cache/ms-playwright

# 3. Register MCP server, scoped to this repo.
cd /home/chris/git/rapla
claude mcp add playwright npx '@playwright/mcp@latest'

# 4. Restart Claude Code (or /mcp) — tools surface as `mcp__playwright__browser_*`.
```

Provides `browser_navigate`, `_click`, `_fill`, `_snapshot`, `_screenshot`, `_network_requests`, `_console_messages`, `_evaluate`, etc.

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

- Headless: works anywhere (default).
- Headed: needs WSLg (Win 11 default); on Win 10, VcXsrv + `DISPLAY=:0` or stay headless.
- `localhost` is the WSL2 host loopback; `:4200`/`:8051` resolve correctly.
- `libatomic.so.1` handled by `playwright install-deps`.

## Plan

### Phase 1 — Enable MCP

Run the four install commands; verify `mcp__playwright__browser_*` tools appear; quick smoke: `browser_navigate` to `http://localhost:8051/` + screenshot.

### Phase 2 — End-to-end OAuth smoke

Pin sequence: navigate to `http://localhost:4200/app/` (assumes ng serve + Spring Boot per [PRD 026](../026-angular-frontend.md)) → wait redirect to `/login` → click "Sign in" → browser hits `:8051/oauth2/authorize` cross-origin → Spring form: `admin` / empty password → bounce to `:4200/app/auth/callback?code=…` → SPA exchanges → navigates to `/reservations` → screenshot.

### Phase 3 — Skill seed

`.agents/skills/playwright/SKILL.md` covers: when to use (SPA bugs, OAuth, CORS, browser-internal state) vs not (REST contract → MockMvc; wire-shape → `api-testing`+curl); the OAuth E2E sequence; common patterns (screenshot before "looks good"; `browser_console_messages` before diagnosing JS; `browser_network_requests` instead of guessing); WSL gotchas.

## Tests

Browser flows verified on first run, captured in the skill. No ongoing suite commitment — follow-up PRD once we know what's worth automating. OpenAPI smoke + tier-3 MockMvc tests stay; Playwright sits at tier-4 (tag-excluded `e2e` from default `mvn test`).

## Risks

| Risk | Mitigation |
|---|---|
| Agent defaults to `curl`/Bash over Playwright tools | Prompt "use Playwright MCP" explicitly; skill helps |
| Surprise headed browser windows | `--headless` in MCP config by default |
| Browser downloads ~310 MB on slow links | Cached in `~/.cache/ms-playwright` |
| `playwright install-deps` needs sudo + apt | Documented; CI may need different path |
| Headless timing flake | Standard `expect().toBeVisible()` / `waitForResponse` patterns |
| OAuth state leaks across runs | `--isolated` for automation; `--user-data-dir` only when persistent session wanted |

## Open questions

1. **MCP scope** — project scope (so fresh clone lists the server).
2. **Headed vs headless** — install `--headless`, override per-task when debugging.
3. **`--isolated` vs `--user-data-dir`** — persistent profile by default; isolated for tests needing clean state.
4. **CI integration** — defer; this PRD is local dev only.
5. **Browsers** — Chromium-only by default; add others if cross-browser bug surfaces.

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
