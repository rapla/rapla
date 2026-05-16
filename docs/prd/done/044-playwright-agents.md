# PRD 044: Playwright Agents (Planner / Generator / Healer)

**Status:** done (2026-05-16)

## Goal

Set up the Playwright **Agents** layer (Planner / Generator / Healer, Playwright
1.56+) for `rapla-angular`, so tier-7 browser e2e specs (AGENTS.md §10) can be
authored and maintained agentically. This is distinct from the Playwright **MCP**
server (PRD 033, done) which drives a browser interactively — Agents author and
heal `*.spec.ts` files.

## Scope

**In scope:**

- Add `@playwright/test` as a `rapla-angular` devDependency.
- `rapla-angular/playwright.config.ts` — `testDir: tests/`, system Chrome via
  `channel: 'chrome'` (no bundled Chromium — none exists for Ubuntu 26.04, see
  PRD 033 / `docs/development.md`).
- `npx playwright init-agents --loop claude` — scaffold the Claude-Code agent
  definitions + seed.
- A first smoke spec under `rapla-angular/tests/`.
- Document the setup + the corrected `init-agents` usage in
  `docs/development.md`; fix the stale "Playwright Agents" section of the
  `angular-frontend` skill.

**Out of scope (deferred):**

- Wiring Playwright e2e into CI — PRD 034 Phase 4.
- A broad e2e suite. AGENTS.md §10 caps tier 7 at ~5–15 critical-path tests;
  this PRD lands the harness + one smoke test, not the suite.
- `webServer` auto-start in the config — the rapla dev server start is the
  `mvn spring-boot:run` flow (AGENTS.md §8); specs assume it is already up.

## Plan

1. ✅ PRD (this file).
2. ✅ `npm install -D @playwright/test` in `rapla-angular`.
3. ✅ `rapla-angular/playwright.config.ts` — `channel: 'chrome'`,
   `baseURL` `http://localhost:8051` (`RAPLA_BASE_URL` override), `testDir: './tests'`.
4. ✅ `npx playwright init-agents --loop claude` — scaffolded
   `.claude/agents/playwright-test-{planner,generator,healer}.md`, `.mcp.json`
   (`playwright-test` MCP server), `tests/seed.spec.ts`, `specs/`.
5. ✅ `tests/smoke.spec.ts` — loads `/app/`, asserts `app-root` mounts.
6. ✅ `e2e` + `e2e:ui` npm scripts; `.gitignore` for `test-results/` etc.
7. ✅ Documented in `docs/development.md` § "Playwright Agents"; corrected the
   `angular-frontend` skill's Playwright Agents section.

## Tests

- ✅ `npm run e2e` with the dev server up — `seed.spec.ts` + `smoke.spec.ts`
  both pass (system Chrome via `channel: 'chrome'`).
- ✅ `init-agents` output present; agent definitions + `.mcp.json` resolve.

## Open Questions — resolved

1. `baseURL` → **`http://localhost:8051`** (Spring Boot full stack, tier-7
   intent), overridable via `RAPLA_BASE_URL`.
2. Generated files placement → **kept under `rapla-angular/`** (`.claude/agents/`,
   `.mcp.json`, etc.). The agents write to relative `tests/`/`specs/` paths, so
   they must run with the session rooted at `rapla-angular/`; moving them to the
   repo root would break those paths.

## Follow-ups

- Wire `npm run e2e` into CI — PRD 034 Phase 4.
- Grow the suite to the ~5–15 critical-path tests AGENTS.md §10 allows for
  tier 7 (this PRD landed only the harness + smoke test).
