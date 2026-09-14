# PRD 034: CI baseline workflow

**Status:** Phases 1–3 implemented 2026-09-14 directly on `master` (user ruling: no branch test run) — `.github/workflows/ci.yml` with `java`, `slow-tests`, `angular`, `docker` jobs (a `publish` job existed until the move below) (nightly + on-demand, build despite red tests, self-signed rolling `nightly` release, image build without push; since 2026-09-14 `master` pushes `ghcr.io/rapla/rapla:nightly`, see [OQ 5](#open-questions)). First live run pending. **Publishing moved to rapla/rapla-nightly 2026-09-14** (user ruling: separate owners-only repo; env/PAT approach rejected — a branch workflow's own GITHUB_TOKEN could publish). `ci.yml` in rapla/rapla only builds and tests; the JAR asset and `ghcr.io/rapla/rapla:nightly` come from `rapla/rapla-nightly/.github/workflows/nightly.yml`.

## Goal

A GitHub Actions workflow (`.github/workflows/ci.yml`) that **reports test results and builds the deployable artefact** for `master`, triggered **on demand** (`workflow_dispatch`) and **once per night** (`schedule`). Red tests are reported loudly (red run, test summary, downloadable reports) but **never block the build**: the fat JAR is produced, self-signed, and published to one fixed download link either way.

The 2026-05-13 draft ran on every push/PR and gated merges. That is replaced (see [Decisions](#decisions)).

## Context since the first draft

- **`master` is the trunk again** — `spring-boot` squash-merged as `051e41fbb` (2026-09-14). The draft's `spring-boot` triggers are obsolete.
- **Java 21**, **Angular 22**. Node **24.15.0** everywhere: `docs/development.md`, `rapla-app/pom.xml`'s `frontend-maven-plugin`, `rapla-angular/.nvmrc` and the `angular` job's `setup-node` (OQ 4, closed 2026-09-14).
- **The SPA is built inside `mvn package`**: `frontend-maven-plugin` runs `npm ci` + `ng build` at `prepare-package` and copies the dist into `target/classes/static/app/`. `-Dskip.npm` only works when a dist already exists — CI must let the plugin build the SPA (or build it first).
- **Docker support is in the repo** (`1ba81b92a`): `Dockerfile` copies the host-built `rapla-app/target/rapla-*.jar`; no in-image Maven build.
- **`.gitlab-ci.yml` moved to `docs/examples/gitlab-ci.yml`** — inert, Rapla 2 era; not a template for this.
- **No `.github/` directory in the repo** — the only GitHub-side automation is Dependabot configured in the repo settings.
- **Default surefire lane** excludes `@Tag("db")`, `@Tag("e2e")`, `@Tag("perf")` (`rapla-bom/pom.xml` `test.excludedGroups`). Untagged `@SpringBootTest` + MockMvc (tier 3) tests are therefore **in** the default lane, not a separate phase as the first draft assumed.

## Decisions

### D1 — Triggers: `workflow_dispatch` + nightly `schedule`, no push/PR

```yaml
on:
  workflow_dispatch:
  schedule:
    - cron: '17 1 * * *'   # 01:17 UTC ≈ 03:17 CEST — off the full hour (GitHub delays :00 jobs under load)
```

**Why not on every push/PR:**

1. **Noise from WIP commits.** Several Claude sessions and the maintainer commit to `master` directly, often intermediate states; a red mail per push trains everyone to ignore red. One nightly verdict on the day's end state is the signal that gets read.
2. **Minutes.** A full run (reactor tests incl. tier 3 + SPA build + package) is ~10–15 min. At 5–20 pushes on busy days that is hours of runner time for results nobody waits for; nightly is ≤ 1 run/day plus manual runs.
3. **No merge-gating need today.** External PRs are rare; the maintainer can `gh workflow run ci.yml --ref <branch>` for a PR branch on demand.

Scheduled workflows only run on the default branch (`master`) and GitHub disables them after 60 days without repository activity — acceptable, rapla is active. **`workflow_dispatch` (UI button and `gh workflow run`) also only works once the workflow file exists on `master`** — a workflow that lives only on a feature branch can't be dispatched.

GitHub side (checked 2026-09-14): `rapla/rapla` is public, Actions enabled with all actions allowed. Public repos get unlimited minutes on standard runners and free artefact storage — no plan, no billing, no secrets needed.

`concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: false }` — a manual run during the nightly run queues rather than kills it.

### D2 — Red tests don't block the build

> **Superseded 2026-09-14 for publishing:** the nightly is built and published by `rapla/rapla-nightly` with `-DskipTests` and without test counts, so the statement below that a red nightly is published with its test results no longer applies. The rule itself still holds for this workflow: red tests don't block the CI build.

Surefire runs with `-Dmaven.test.failure.ignore=true`, so the reactor continues through `package` even when tests fail. The Java job then:

1. uploads the fat JAR as a run artefact (always),
2. uploads `**/target/surefire-reports/` (always, `if: always()`),
3. writes a test summary (counts + failing test names) to `$GITHUB_STEP_SUMMARY`,
4. **fails the job as its last step** if the surefire reports contain failures/errors.

Result: the run is red, the summary says which tests broke, the JAR is still downloadable — and **a red nightly is published to the `nightly` release too** (user ruling 2026-09-14, [D4](#d4--nightly-release-artefacts-and-retention)); its release notes carry the test result. A **compile error** still fails early and produces no JAR — that is intended; "build despite red" means test failures, not broken code. Since 2026-09-14 the release is published by `rapla/rapla-nightly`, not by this workflow.

Summary generation: first rung is a few lines of shell over the surefire XML (`grep -c '<failure\|<error'` + testcase names) — no third-party reporter action. A marketplace action (e.g. a JUnit report action) only if the shell summary proves unreadable ([OQ 5](#open-questions)).

### D3 — Job structure

| Job | Needs | Runs | Fails the run when | Artefacts |
|---|---|---|---|---|
| **java** — test + package | — | Temurin 21, Maven cache (`setup-java` `cache: maven`). No `setup-node`: the SPA is built by `frontend-maven-plugin` with its own Node (version from `rapla-app/pom.xml`, see [OQ 4](#open-questions)). `mvn -B clean package -Psign-jks -Dmaven.test.failure.ignore=true` from repo root (reactor; no `install`, AGENTS.md §5). Includes tiers 1–3 (default lane), the SPA production build via `frontend-maven-plugin`, and self-signing of the JNLP webclient jars. | any surefire failure/error (checked after upload), or compile/package error | `rapla-jar` (`rapla-app/target/rapla.jar`), `surefire-reports` |
| **angular** — lint + unit tests | — (parallel to java) | Node, `npm ci`, `npm run lint`, `npx ng test --watch=false` (Vitest via `@angular/build:unit-test`) | lint or vitest failure | vitest report if the builder emits one (optional) |
| **docker** — image build, no push | `java` (artefact) | downloads `rapla-jar` into `rapla-app/target/`, `docker build .` on every branch; no environment, `permissions: contents: read`. Validates `Dockerfile` + JAR. | image build failure | none |

- `java` and `angular` run **independently** (`angular` does not `needs: java`), so a lint failure never suppresses the JAR and vice versa.
- The SPA is built twice (once in `java` via Maven for the JAR, once implicitly by `ng test`) — accepted; deduplicating means passing a dist artefact between jobs and `-Dskip.npm`, which adds coupling for ~1–2 min.
- `docker` uses `if: always() && needs.java.result != 'cancelled'` guarded by the artefact actually existing, so a red-test run still validates the image; it is skipped if the JAR was never produced (compile error).
- `docker` needs the JAR and runs whenever it exists, regardless of test results (`if: always() && needs.java.outputs.jar == 'true'`). Publishing no longer happens here (see [OQ 5](#open-questions)).
- **Signing: `-Psign-jks`** (user ruling 2026-09-14). The self-signed `raplaselfsigned.ks` and its password are already public in the repo (`rapla-bom` `keystore.*` defaults; certificate RSA 2048 / SHA256, valid until 2036-05) — no secret. It signs the JNLP webclient jars inside the fat JAR so the webclient launches (with a trust prompt). Because the key is public, the signature proves nothing about origin; maintainer production builds stay YubiKey-signed (`-Psign-pkcs11`, local) — [`docs/signing.md`](../signing.md).

### D4 — Nightly release, artefacts and retention

> **Superseded 2026-09-14:** the nightly release and image are published by `rapla/rapla-nightly` (see [OQ 5](#open-questions)); the bullets below record the former in-repo `publish` job.

**Download = one rolling prerelease `nightly`** (user ruling 2026-09-14), not run artefacts:

- Fixed URL, no login, no ZIP: `https://github.com/rapla/rapla-nightly/releases/download/nightly/rapla.jar`.
- Exactly one nightly exists at any time: each publish overwrites the asset (`--clobber`) and moves the tag; nothing accumulates, no cleanup job.
- Marked **prerelease**, so it never becomes "Latest"; release watchers are notified once on creation, not on each nightly update.
- **Published even when tests are red.** Release notes are rewritten each time: commit SHA, build date, test result (e.g. "1432 tests, 3 failed" + link to the run), and the disclaimer *"Nightly test build, self-signed with the public dev certificate — not for production."*
- Only runs from `master` publish (scheduled, or a manual dispatch on `master`); a dispatch on another branch builds and tests but doesn't touch the release.

Run artefacts stay for diagnosis only:

- `surefire-reports`: `actions/upload-artifact`, **retention 3 days**.
- `rapla-jar`: retention **1 day** — only the hand-off to `docker`; manual branch runs download it from the run page.
- Container image: only the moving `ghcr.io/rapla/rapla:nightly` tag; each push leaves the previous image untagged, the `docker` job deletes untagged versions older than 10 days. No coverage upload.

### D5 — Out of scope (unchanged or deferred)

- Versioned release images (`:3.0`, `:latest`) — separate release process; the nightly pipeline only maintains `:nightly`.
- Playwright browser e2e ([PRD 033](done/033-playwright-mcp-browser-testing.md)).
- Push/PR triggers and required status checks ([OQ 3](#open-questions)).
- Cross-OS / cross-JDK matrix; Swing client can't run headless meaningfully.
- YubiKey-signed release builds, JNLP launch verification (`test-deployment` skill stays local).
- Notifications beyond GitHub's own failure mail (e.g. Telegram) — not wired in the public repo.

## Plan

### Phase 1 — Workflow, java + angular + publish jobs

1. Add `.github/workflows/ci.yml` with D1 triggers and the `java`, `angular`, `publish` jobs from D3, D2 failure handling, D4 release + artefacts.
2. **Branch test run:** dispatch doesn't work before the file is on `master` (D1), so the branch copy temporarily adds `push: branches: [<branch>]`. Verify: signed JAR artefact present (`jarsigner -verify` on a webclient jar), surefire artefact present, job summary lists results, `publish` skipped (not `master`).
3. **Verify the red path deliberately:** on the branch, add a temporary failing test, push → run red, summary names the test, JAR still uploaded. Remove the test and the temporary `push` trigger.
4. Merge to `master`; dispatch once manually → `nightly` prerelease created, fixed URL downloads the JAR without login, notes show commit + test result. Dispatch a second time → still exactly one asset, tag moved.
5. Wait for the first scheduled run.
6. **Done 2026-09-14:** short "CI" section in `docs/development.md` (triggers, nightly download link, where reports are, how to run manually).

### Phase 2 — Docker job

1. `docker` job: downloads the `rapla-jar` artefact into `rapla-app/target/`, runs `docker build`, on any branch whenever the JAR exists. **Done 2026-09-14.** Extended 2026-09-14 (registry ruling, [OQ 5](#open-questions)): on `master` push `ghcr.io/rapla/rapla:nightly` and delete untagged versions older than 10 days.

### Phase 3 — Slow lanes

1. `slow-tests` job, parallel to `java`, on both triggers: `mvn -B clean test -Dgroups=db,e2e,perf -Dtest.excludedGroups= -Dmaven.test.failure.ignore=true`, same report-don't-block pattern (reports artefact `surefire-reports-slow`, 3 days; summary; red job on failures). Doesn't feed `publish`. All `@Tag("db")` tests use embedded HSQLDB — no service container. **Done 2026-09-14.**
2. The summary shell lives once in `.github/scripts/test-summary.sh`, shared by `java` and `slow-tests`.

## Tests

- The workflow is verified by running it (Phase 1 steps 2–4), including the intentional red run — the D2 property "red tests → red run + JAR uploaded" and the D4 property "exactly one nightly asset after repeated runs" must be observed, not assumed.
- Workflow edits happen on a branch with a temporary `push` trigger before landing on `master` (dispatch needs the file on `master`).

## Open questions

1. **Nightly time** — implemented as 01:17 UTC (03:17 CEST / 02:17 CET, cron is UTC and doesn't follow DST). Later (e.g. 04:17 UTC) if late-evening commits should be included?
2. ~~Retention~~ — resolved 2026-09-14: one rolling `nightly` release (overwritten), run artefacts 1 day (JAR) / 3 days (reports) — D4.
3. **PR trigger later?** — add `pull_request` (tests only, no package) once external PRs pick up again, or keep manual `gh workflow run` for PR branches?
4. ~~**Node version**~~ — **Closed 2026-09-14:** one pin, 24.15.0 (npm 11.12.1 = `packageManager`, bundled with that Node, so no separate `npmVersion`) in `rapla-app/pom.xml`, `rapla-angular/.nvmrc`, `docs/development.md` and the `angular` job.
5. ~~Docker job~~ — **Closed 2026-09-14 (registry ruling, replaces the earlier "registry only for real releases"):** nightly `master` runs push `ghcr.io/rapla/rapla:nightly` (one moving tag, no per-SHA tags); untagged versions older than 10 days are deleted in the same job; release images stay a separate process. The package is created private on first push — an org owner switches it to public once by hand. No `docker save` tarball on the release. **Hardened 2026-09-14 (user ruling "Teil B"):** push and release run in the environment `nightly` (deployment branch `master` only) with the maintainer's environment secret `NIGHTLY_TOKEN` instead of `GITHUB_TOKEN`; the `docker` job is split into build-only `docker` (every branch) and `docker-push` (master, environment); `GITHUB_TOKEN` is read-only in all jobs, the repository's package access is Read, the `nightly` tag is protected by a ruleset. The `if: github.ref == 'refs/heads/master'` guards stay as a second layer. **Publishing moved to rapla/rapla-nightly 2026-09-14** (user ruling: separate owners-only repo; env/PAT approach rejected — a branch workflow's own GITHUB_TOKEN could publish). `ci.yml` in rapla/rapla only builds and tests; the JAR asset and `ghcr.io/rapla/rapla:nightly` come from `rapla/rapla-nightly/.github/workflows/nightly.yml`.

## Risks

| Risk | Mitigation |
|---|---|
| First run on `master` HEAD is red (known local-only reds, e.g. gitignored mock controllers, won't exist in CI; unknown reds may) | Intended outcome of D2 — the run reports them, the JAR still builds; fixing them is separate work. |
| `-Dmaven.test.failure.ignore=true` hides failures if the final check step is wrong | Phase 1 step 3 verifies the red path explicitly. |
| Tier-3 `@SpringBootTest` tests are slow or flaky on 2-vCPU runners | Measure on first run; move offenders to `@Tag("e2e")` only with a named reason. |
| Scheduled run silently disabled after 60 days of inactivity | Acceptable; manual trigger still works. |
| Users take the public nightly for a release (self-signed with a public key, possibly red tests) | Prerelease flag, fixed disclaimer + test result in the notes, `-nightly` asset name. |
| Force-moving the `nightly` tag confuses clones that fetched it | Only a moving pointer by design; the tag now lives in `rapla/rapla-nightly` (no tag in rapla/rapla is moved). |
| Frontend plugin downloads Node every run | `frontend-maven-plugin` install dir is `rapla-app/target` (cleaned); cache `~/.m2` covers the Node archive the plugin stores there. |

## Cross-references

- [PRD 017 — Test Coverage Strategy](017-test-coverage-strategy.md) — the pyramid.
- [PRD 007 — Build & Test Performance](007-build-and-test-performance.md) — default lane runtime.
- [AGENTS.md §5](../../AGENTS.md#5-build-discipline) — reactor hard rules (no `install`, `clean` before `package`) apply in CI too.
- [`docs/development.md`](../development.md) — Node/Java toolchain pins.
- `Dockerfile` — consumes the host-built fat JAR.
