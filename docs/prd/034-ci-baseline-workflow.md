# PRD 034: CI baseline workflow

**Status:** draft (2026-05-13)

## Goal

Add a minimal GitHub Actions workflow that runs on every PR to `master` and on every push to `spring-boot`, exercising the bottom of the testing pyramid (PRD 017 tiers 1–2 for Java, tier 5–6 for Angular). Today the only active workflow is `Dependabot Updates` — every commit on `spring-boot` (64 of them so far) has gone untested in CI. PRs from external contributors get no automatic feedback.

## Why now

1. **`spring-boot` is the working trunk** (PRDs 005, 011, 029, 031, 026 land here). Merge-back diff is +84 k / −55 k LOC; catching regressions by re-reading 64 commits is the wrong tool.
2. **External contributors exist** (`@stephenBDT`, `@floxdeveloper` merged PRs in 2025); they get no CI feedback today.
3. **The test pyramid is real** (PRD 017 Phases 1–4 done, ~150 tests; PRDs 023 + 030 added 84 more). CI as forcing function makes it load-bearing.

## Scope

**In scope:**

- One workflow file: `.github/workflows/ci.yml`.
- Triggers: `pull_request` to `master`, `push` to `master` and `spring-boot`.
- Jobs:
  1. **Java** — JDK 21 (Temurin), Maven cache, `mvn -B verify` (default surefire excludes `@Tag("db")`/`@Tag("e2e")` per AGENTS.md §10). ~3–5 min on GitHub-hosted runners.
  2. **Angular** — Node 24, npm cache, `cd rapla-angular && npm ci && npm run build` (= `lint && ng build`). ~2 min.
- PR status checks gating merge (after the first green run lands).

**Out of scope (deferred):**

- Tier-3 web-slice tests (`@SpringBootTest` + MockMvc). These boot a Spring context; expensive but parallelizable. Phase 2.
- Tier-4 full E2E (`@Tag("e2e")`). 7–15 s per test, want them in a separate slower job. Phase 3.
- Browser e2e (Playwright MCP, PRD 033). Headless Playwright on Linux runners is straightforward but adds 5–10 min and a browser binary cache. Phase 4.
- Coverage report upload to a service. The JaCoCo aggregate works locally per the `coverage-report` skill; sending it to Codecov/Coveralls is a Phase-5 polish.
- Cross-OS or cross-JDK matrix. rapla targets one JDK (21); cross-OS only matters for the Swing client, which CI can't exercise headless without WSLg/Xvfb.

## Plan

### Phase 1 — Tier 1+2 + Angular build

1. Add `.github/workflows/ci.yml` with the two jobs above.
2. Run once against the current `spring-boot` HEAD to confirm green.
3. Open a PR from `spring-boot` to `master` (or a synthetic branch) to confirm the PR-status-check shape.
4. Document the workflow in `docs/development.md` under a new "CI" section.

### Phase 2 — Tier 3 web slices

1. Add a third job: `mvn -B -pl rapla-app -am test -Dtest.excludedGroups=e2e,db` to include `@SpringBootTest` + MockMvc tests.
2. Expect ~2–4 min runtime. Cache the Spring context where possible (already done via `@SpringBootTest` cache key — should "just work" with the Maven cache).

### Phase 3 — Tier 4 e2e

1. Add a fourth job: `mvn -B test -Dtest.excludedGroups=db` (drop the `e2e` exclude). Runs full `@SpringBootTest(webEnvironment=RANDOM_PORT)`.
2. Gate this job behind a `if: contains(github.event.pull_request.labels.*.name, 'run-e2e')` so it only runs when needed — keeps PR feedback fast by default.

### Phase 4 — Playwright (optional)

If/when we adopt browser e2e as a CI gate. Out of scope for the initial PRD.

## Tests

- The workflow itself is testable: trigger via `gh workflow run ci.yml --ref spring-boot` and confirm green.
- Catch regressions in CI config before they ship: edit the workflow on a feature branch, push, observe the CI of the CI. Don't edit `master`'s workflow directly per AGENTS.md `autoMode` policy on `.github/workflows/`.

## Open questions

1. **Concurrency cap** — add `concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: true }` to cancel stale runs on rapid pushes. Recommendation: yes.
2. **`mvn -B` vs `mvn -ntp`** — pick `-B` (legacy batch mode) for consistency with rapla docs.
3. **JDK distribution** — local dev uses Semeru (`21.0.11-sem`); use Temurin in CI (better `setup-java` cache hit rate).
4. **Caching key** — `setup-java` built-in Maven cache + `actions/cache@v4` keyed on `pom.xml` hashes. Standard.
5. **`push` to `spring-boot` or only `pull_request`?** Both — catches drift earlier than waiting for merge-back PR.

## Risks

| Risk | Mitigation |
|---|---|
| First CI run reveals existing test failures on `spring-boot` HEAD | Address them as Phase 1.5; don't merge the workflow until it passes. |
| Workflow runs use minutes on the public-repo free tier | Concurrency cap + tag-gated tier-4 jobs cap the budget. |
| Dependabot PRs start failing CI noisily (they pass today only because there is no CI) | Likely fine — Dependabot bumps are small; if they fail it surfaces a real version conflict. |
| External contributors confused by required-status-check gating | Document the workflow in `docs/development.md` and link from `CONTRIBUTING.md` (which doesn't exist yet — would be a one-line follow-up). |

## Cross-references

- [PRD 017 — Test Coverage Strategy](017-test-coverage-strategy.md) — the pyramid this workflow exercises.
- [PRD 007 — Build & Test Performance](007-build-and-test-performance.md) — Phase 1 work that brought test runtime under control; CI inherits those gains.
- [AGENTS.md §5](../../AGENTS.md) — build discipline (reactor hard rules apply in CI too).
- [`.agents/hooks.md`](../../.agents/hooks.md) — the local `mvn install` ban; CI runs `mvn verify`, also doesn't `install`.
- `~/.claude/settings.json` `autoMode.soft_deny` — editing `.github/workflows/` requires explicit user direction; this PRD is that direction.
