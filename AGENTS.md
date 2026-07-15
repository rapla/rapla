# AGENTS.md - Opencode Rules
# AGENTS.md - Opencode Rules

## Project Overview

**Rapla** is a Java-based resource scheduling and event planning application (v2.1-SNAPSHOT, AGPL/Apache2). It uses Maven, targets Java 17, runs on Java 21. Key technologies: Spring Boot 4.0 (Tomcat 11), Jackson 3, Swing, JAX-RS / Spring MVC, RxJava3, iCal4j 4.2, Exchange Web Services.

**Jackson 3** (PRD 011, done): the runtime is Jackson 3 — databind/core live in the `tools.jackson.*` package, **not** `com.fasterxml.jackson.*`. Annotations (`@JsonIgnore`, `@JsonProperty`, …) stay in `com.fasterxml.jackson.annotation.*` (the version-shared package) — those imports are correct. Only `tools.jackson.databind.ObjectMapper` is the rapla mapper. Rapla uses only Jackson 3; every use of Jackson 2 (`com.fasterxml.jackson.databind`/`.core`) is forbidden except the build-time OpenAPI spec generation (springdoc, test scope only — never in the runtime classpath).

**Deployment topology:** the server is multi-pod capable — multiple instances run against one shared store, coordinating via the update history (JSON change records, polled ~every 10 s) plus store-level locking; don't assume a single instance or a process-local shared cache (§8's "one server per checkout" is a dev convention only). Lock layers (process / resource / global) and the multi-pod concurrency model: [`docs/architecture/locking.md`](docs/architecture/locking.md).

The codebase is a **5-module Maven reactor** (PRD 005, 2026-05-07) plus a **separate Angular SPA tree** (PRD 026, 2026-05-12):

| Module / tree | Role |
|---|---|
| `rapla-bom` | BOM + parent POM (versions, plugin config). Was `parent/`. |
| `rapla-core` | Shared layer: entities, facade, framework, scheduler, storage interfaces, REST DTOs/endpoint interfaces, components/{util,layout,restproxy,i18n}, logger, inject. NO Spring Boot, NO Swing. |
| `rapla-client` | Swing client + presenters: `org.rapla.client.*`, components/{calendar,calendarview,iolayer,tablesorter,treetable}, plugin `*/client/*` and `*/swing/*`. Uses `spring-context` only — explicit `AnnotationConfigApplicationContext`, NOT `@SpringBootApplication`. Depends on rapla-core. |
| `rapla-server` | Server: `org.rapla.server.*` (excl. the `RaplaSpringBootApplication` entry point), JDBC storage, REST controllers, Spring autoconfig (`META-INF/spring/AutoConfiguration.imports`), plugin `*/server/*`. Depends on rapla-core only (verified 2026-06-10 — the PRD 005 D3 rapla-client compromise is resolved; `RaplaBuilder`/abstractcalendar live in rapla-core). |
| `rapla-app` | Runnable Spring Boot application: `RaplaSpringBootApplication`, `application.yml`, `src/assembly/`, `src/main/distribution/`, signing profiles, JNLP webclient/ staging. Produces `rapla-2.1-SNAPSHOT.jar` (Spring Boot fat JAR). |
| `rapla-angular/` | Angular 21 SPA — separate tree, **not in the Maven reactor**. Built with `npm`, served at `/app/` in dev (via `ng serve` proxy on :4200) and prod (via Spring Boot static handler). Talks to the rapla-app REST API at `/api/*`. Has its own `package.json`, Vitest tests; talks to the server via `HttpClient` (auth + `/api/graphql`), no generated client. See AGENTS.md §14 + the `angular-frontend` skill. |

The repo-root `pom.xml` is the reactor aggregator (artifactId `rapla-aggregator`, packaging=pom, lists the five Maven modules); running `mvn` from the repo root walks the whole reactor.
`custom/` is intentionally NOT in the reactor (its WAR-overlay shape is being rethought; future PRD).
`rapla-angular/` lives outside the reactor entirely — it's an Angular project, not a Maven module; build/test commands are `npm`, not `mvn`.

**Build & Test:**
- Reactor compile: `mvn compile`
- Reactor test: `mvn test`
- Per-module compile: `mvn -pl rapla-server -am compile`  *(use `-am` to also build module deps in-tree; otherwise Maven looks in `~/.m2/repository`)*
- Per-module test: `mvn -pl rapla-server -am test`
- Targeted test: `mvn -pl rapla-app -am test -Dtest=RaplaSpringBootApplicationTest`
- Run the dev server: `mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false -Dspring-boot.run.profiles=local` *(must use `-am` from repo root — see §8 hard rules; do not `cd rapla-app`, do not `mvn install`)*
- Full server lifecycle (background, PID/logs, graceful stop): see §8 below
- Test the deployable fat JAR + signed JNLP webclient: load the **`test-deployment`** skill
- Probe the running server's REST API directly (login, getResources, queryAppointments, etc.) — load the **`api-testing`** skill
- Requires SDKMAN (Java 21 + Maven) on WSL2 Ubuntu

## Skills

Detailed how-tos live as **Agent Skills** (the cross-engine `SKILL.md` standard) under `.agents/skills/<name>/SKILL.md`, kept out of this always-on file so it stays rule-dense. Every engine that reads this repo — Claude Code, opencode, Copilot/VS Code, Codex, Gemini CLI — **auto-discovers them by `name` + `description` and loads the body on demand** (progressive disclosure); there is no manual index to maintain, and you never need to `cat` a SKILL.md to "enable" it. The numbered rules below name the relevant skill at the point it applies (*"load the X skill"*) — that contextual pointer **is** the reference. If your engine doesn't auto-surface skills (e.g. Cursor), they're plain Markdown at the path above. Restructuring this file or its skills: load the **`agents-cleanup`** skill.

## Rules

### 0. Session discipline — context budget + risky-change branching

Two practices that pay off on a codebase this size (rapla sessions tend to be long):

- **Don't let context exceed ~60% of the window.** Quality starts degrading at 20–40% of 200 k tokens; auto-compact (~83% threshold) is lossy and retains only 20–30% of detail. When approaching the limit, run `/compact <hint>` — e.g. `/compact focus on PRD 029 phase 2 verification, drop the bootstrap chatter` — so the summary keeps the load-bearing context and drops the rest. Don't wait for auto-compact.
- **Use `/branch` (or `/fork`) before a risky mechanical sweep.** Date→LocalDateTime, package renames, Jackson 3 migration, the kind of change the `bulk-refactor-scripts` skill records scars from. A branch is a session snapshot — try the experiment; if it works, keep the branch; if it doesn't, return to the original conversation with no rollback cost.

### 0a. Working principles — Karpathy's four rules

From [multica-ai/andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills) (Andrej Karpathy's LLM-coding observations), verbatim. Cross-cutting behavioural defaults. Bias toward caution over speed — for trivial tasks, use judgment. The rapla `→` pointer on each rule names where its detailed application lives.

**1. Think Before Coding** — *Don't assume. Don't hide confusion. Surface tradeoffs.*
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.
- → rapla: read the relevant `docs/`+PRD first (§2a); `design-dialog` skill for open design space.

**2. Simplicity First** — *Minimum code that solves the problem. Nothing speculative.*
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

**3. Surgical Changes** — *Touch only what you must. Clean up only your own mess.*
- Don't "improve" adjacent code, comments, or formatting; don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.
- Remove imports/variables/functions that YOUR changes made unused; don't remove pre-existing dead code unless asked.
- The test: every changed line should trace directly to the user's request.
- → rapla: match conventions (§3); never delete code to pass a compile (§11); never touch another session's files (§7).

**4. Goal-Driven Execution** — *Define success criteria. Loop until verified.*
- "Fix the bug" → write a test that reproduces it, then make it pass. "Refactor X" → tests pass before and after.
- For multi-step tasks, state a brief `step → verify` plan.
- → rapla: §1 made universal — the failing-test-first discipline.

### 1. Test-First Approach

The order is **(a) understand → (b) write failing test → (c) fix → (d) verify test passes**, then commit. Skipping the test step for "obvious" fixes is the most common form of slippage in this codebase — don't.

- **Write the failing test before any production code change** for: bug fixes (every one — including 1-line ones), new features, behaviour changes, refactors that should preserve observable behaviour. Run `mvn test` to confirm it fails for the right reason, then fix, then re-run to confirm it passes. Tests live in `src/test/java/` mirroring the source package.
- **Diagnostics-first is fine when** you're still locating the root cause — curl probes, log inspection, exploratory println/diagnostic dumps in a test, integration-test bisects, reading code. Once you can name the broken function/field/method, switch to test-first for the fix.
- **One hypothesis, one variable at a time.** Write the suspected root cause down before changing anything, change exactly one thing, verify before the next. Trace a wrong value *backward* to where it's born — don't dedupe/clamp/guard it where it's observed (that hides the bug, it doesn't fix it). After 3+ failed fix attempts, stop and question the architecture instead of piling on patch #4.
- **No exception for "trivial" fixes.** A removed `final` keyword, a missing null-check, a typo'd config key — all bug fixes get a regression test. The point isn't to verify the fix works; it's to lock the fix in so the next refactor doesn't reopen the bug. The Jackson-3 `final`-field bugs (PRD 011 follow-up, 2026-05-09) reopened the same pattern five times across different fields — a per-bug regression test would have caught the second one immediately.
- **Verifying the test would catch the bug:** After writing the fix and seeing the test go green, briefly revert the fix and re-run the test to confirm it goes red for the right reason. Re-apply the fix. This costs one extra test run and prevents tests that pass for the wrong reason (e.g. asserting on a field that gets initialized in setUp regardless of the bug).
- **After applying a fix, verify it yourself before asking the user to retest.** Repeat the probe that surfaced the original symptom — curl the endpoint, run the affected test, diff the output. "Please retest" comes *after* your own verification, not instead of it.
- **Audit for sibling occurrences before declaring done.** Once you've named the root cause (a wrong API shape, a missing guard, a buggy `setTimestamp` binding, a `final`-field Jackson trap), grep the codebase for the same antipattern elsewhere — bugs of a kind cluster, and "one site is broken" usually means 2–5 sites are broken. **List the siblings for the user and ASK whether to extend the fix.** Don't silently fan out — superficially-similar sites can need different handling, or the user may prefer the targeted fix today and the sweep on its own PR. Worked example: PRD 054 (2026-05-25) — the cache-drift bug lived in *one* of five dbsql `setTimestamp(...)` sites; the other four were self-consistent for different reasons, so a silent sweep would have been wrong.

### 2. PRD-Driven Development — load the `prd-management` skill

Before implementing anything, check **`docs/prd/` AND `docs/prd/done/`** for an existing PRD — `done/` holds completed PRDs with decisions and alternatives already considered. For when to create, reopen, or close one: load the **`prd-management`** skill.

### 2a. Where knowledge lives

| What you learned | Where it goes |
|---|---|
| A decision being made (rationale + scope + plan) | `docs/prd/NNN-*.md` (§2) |
| Current project state that will change ("test X still fails", "PRD Y pending") | `memory/project_*.md` + MEMORY.md entry with `[since:]`/`[watch:]` tags |
| How Claude should behave in this project | **`AGENTS.md`** — new bullet under the relevant section |
| Rapla domain knowledge — concepts, entity relationships, module responsibilities | `docs/architecture/<topic>.md` (create if no existing file fits) |
| Auth / login flow specifics | `docs/authentication.md` |
| GraphQL schema / resolver specifics | `docs/graphql.md` |

**Obsidian vaults.** Two markdown vaults are in play; both are plain Obsidian vaults (`.obsidian/` config dirs):

| Vault | Path | Scope |
|---|---|---|
| **Rapla vault** | `docs/` (i.e. `~/git/rapla/docs`) | This project's checked-in knowledge — PRDs, `architecture/`, `authentication.md`, `graphql.md`. Shared via git. |
| **Private vault** | `~/vault` | Christopher's personal notes — NOT part of this repo, never committed here, never read into rapla artefacts (§17 PII rules still apply). |

The rapla vault (`docs/`) is the source of truth per the table above; the private vault is separate and personal. Don't conflate them — never write personal-vault content into `docs/`, and never copy `docs/` PRDs into `~/vault` without intent.

**Cross-reference links — portable markdown only (works on GitHub *and* Obsidian).** The `docs/` vault is hosted on GitHub, so every reference between docs/PRDs must be a standard markdown link with a **relative path + heading anchor**: `[PRD 102 § Decisions locked](102-browser-credential-hardening.md#decisions-locked)` (same dir) or `[glossary](../architecture/glossary.md)` (cross dir). **Never `[[wikilinks]]`** — GitHub renders them as literal text (they only work in the separate GitHub *Wiki* feature, which `docs/` is not). Anchors are the GitHub heading slug (lowercase, spaces→hyphens, punctuation dropped; ` — `/`: ` leave a double hyphen). Prefer linking a specific `#section` over the bare file. Don't rely on Obsidian-only features that break on GitHub — block IDs (`^id`) render as visible cruft; YAML frontmatter renders as a metadata table. When you cite another PRD/doc by "PRD NNN" or "§ Section" in prose, make it such a link.

**Before diagnosing a problem or planning work in an unfamiliar area, check `docs/architecture/`, `docs/authentication.md`, `docs/graphql.md` first** — they may already document the invariant or design decision you're trying to reverse-engineer from code.

**When in a session you learn something non-obvious about how Rapla works** — entity relationships, invariants, why a design decision was made — **propose to the user that it gets written down** in the appropriate `docs/` file. Don't leave domain knowledge only in session context where it disappears.

**Never store stable domain knowledge only in MEMORY.md** — entries are 150-char pointers, not knowledge holders.

### 3. Code Style
- No comments unless explicitly requested.
- Follow existing code conventions in the codebase.
- **Simplicity first (§0a #2).** Nothing speculative — e.g. don't add `@ComponentScan`/`@Service` wiring where the explicit `@Bean` factory is the established server pattern (see below); don't build extension points no PRD asked for.
- **Use constructor injection, never field injection.** All `@Inject` / `@Autowired` should be on a constructor parameter list, not on a field. New code (including Spring `@Bean` factory methods, `@Component`/`@Service` classes, and ported legacy classes) must use constructor injection. Existing field-injected code may be left alone until it's touched, but any class you edit should be migrated to constructor injection in the same change. Rationale: constructor injection makes dependencies explicit, supports `final` fields, and lets the class be instantiated for tests without a DI container.
- **Spring DI wiring patterns differ between client and server in this codebase.**
  - **Client (rapla-client)**: `SwingClientConfig` has `@ComponentScan(basePackages = {"org.rapla.client", "org.rapla.plugin"}, ...)`. `@Service`/`@Component` annotations on classes in those packages are picked up automatically. Add `@Service` (with optional `("id")` for `Map<String, T>` consumers) to `@DefaultImplementation` / `@Extension` classes when wiring them. This is the dominant pattern for the Swing tier.
  - **Server (rapla-server / rapla-app)**: `RaplaSpringBootApplication` uses the default `@SpringBootApplication` scan, which only covers its own package (`org.rapla.server.spring`). Server-internal classes (`org.rapla.server.internal.*`, `org.rapla.plugin.*.server.*`) are wired via explicit `@Bean` factory methods in `ServerCoreConfig` / `ServerServiceConfig` — most use `new XImpl(...)` + `beanFactory.autowireBean(impl)` for request-scoped or `new XImpl(deps...)` for singletons. Adding `@Service` to a server-internal class without extending the `@ComponentScan` is dead code (Spring won't see it). Extending the scan creates duplicate-bean conflicts with the existing `@Bean` factories. **Until a coordinated refactor flips server wiring to `@ComponentScan` + `@Service` (and removes the matching `@Bean` factories in one sweep), continue the explicit-`@Bean`-factory pattern on the server side.**
- **No `System.err`/`System.out` in `src/main/java/`** — use SLF4J (`LoggerFactory.getLogger(...)`) throughout. Acceptable in tests when no logger is reachable.
- **On the server, never wrap `Promise` in a `CountDownLatch` to make it sync** — cast to `SyncStorageOperator` and call the `*Sync` method directly (`getConflictsSync`, `queryAppointmentsSync`, …). Promise→latch wrappers add boilerplate, lose stack traces, and mask bugs as timeouts.
- **Server-side code depends on `StorageOperator` / `CachableStorageOperator`, not `RaplaFacade`.** The facade is the Swing-client API; on the server it's a process-singleton with a mutable per-thread-leaky `workingUserId` field. Controllers, services, provisioners, auth stores — anything in `rapla-server` / `rapla-app` / plugin `*/server/*` — take the operator directly.

### 5. Build Discipline

**Reactor hard rules** (referenced from §8 and §9):
- **NEVER `mvn install`** and **never run rapla JARs from `~/.m2/repository/`**. Both shadow in-reactor `target/classes` for sibling modules with stale code — an hours-of-debugging trap. The reactor's in-tree classpath handles all sibling resolution. Enforced by a `PreToolUse: Bash` hook in `.agents/settings.json` that exit-2's `mvn install` calls — see `.agents/hooks.md` for the wired hooks and how to add more.
- **The `-am` ("also-make") flag is mandatory** for any cross-module compile/test/run. Without it, Maven resolves siblings from `~/.m2/repository`, which is stale or missing.
- Run from the repo root with `-pl <module> -am` — never `cd <module>`.

**Routine workflow:**
- Use `mvn compile` for the post-edit type-check. The repo-root `pom.xml` is the reactor aggregator.
- **Targeted tests only during a session.** Use `-pl <module-where-test-lives> -am` — not always `rapla-app`. A test in `rapla-server` runs faster as `mvn -pl rapla-server -am test -Dtest=ClassName` because `rapla-app` test-sources are never compiled. Use `-pl rapla-app -am` only for tests that genuinely live in `rapla-app` (Spring Boot slice / e2e). Pick tests that exercise the code you just touched.
- **Full `mvn test` only at session end** or when the user asks for a green-build sign-off. Don't checkpoint between iterations — `mvn compile` + targeted tests already cover it. Full reactor takes ~60–120 s with Spring context overhead.
- If a targeted test fails in a way that suggests a wider regression, *then* expand to the full suite — as investigation, not routine.
- **Cross-module test gotcha:** if `mvn -pl rapla-app -am test -Dtest=Foo` test-compiles an upstream module with broken test sources, you get a spurious red. Add `-Dsurefire.failIfNoSpecifiedTests=false`: `mvn -pl rapla-app -am test -Dtest=Foo -Dsurefire.failIfNoSpecifiedTests=false`. **Never replace `-am` with an explicit module list** — explicit lists require knowing the full in-reactor dep graph by heart; missing one module silently resolves it from `~/.m2/repository` (stale or absent).
- **Don't `mvn clean` routinely** — incremental compile is reliable.
- **ALWAYS `mvn clean compile` after deleting, renaming, or moving a class.** Stale `.class` files for the old name linger in `target/` and make broken references resolve at compile time but blow up at runtime. No exceptions; fires even for a single-class delete.
- **ALWAYS `mvn clean` before `mvn package` / packaging.** Stale `target/` artefacts shadow assembly inputs — you ship a broken artifact. Recipe: `mvn -pl rapla-app -am clean package -DskipTests [-Psign-pkcs11|-Psign-jks]`.

### 6. Git
- Never commit unless explicitly asked.
- Never push unless explicitly asked.
- **Never `git checkout … -- <file>`, `git restore`, `git reset --hard`, `git clean`,
  `git stash` (any mutating form), or otherwise discard uncommitted changes to tracked
  files without explicit user approval.** Discarding silently destroys session work —
  yours and the user's. The rule covers INTENT, not a verb list: any command that
  rewrites a tracked working file to another version is a discard, **even if recoverable**
  ("it's only stashed", "I made a backup first" are rationalizations, not approval —
  scar 2026-07-08: a `git stash -- <file>` run to lint the HEAD version reverted a file
  carrying two sessions' uncommitted work; recovered only because of a manual backup).
  `git restore` / `git reset --hard` / `git clean` / `git stash` (except `list`/`show`)
  are **hard-blocked by a PreToolUse hook** (`.agents/settings.json`, §5 pattern) — they
  fire even under bypassPermissions where `ask`/`deny` rules don't; if the user genuinely
  wants one, they run it themselves via the `!` prefix. To compare against HEAD, use the
  read-only forms: `git show HEAD:<path> > /tmp/…` / `git diff` — never swap the working
  file. `git checkout … -- <file>` can't be hook-guarded (same verb as the everyday
  `git checkout <branch>`/`-b`), so it rides on this rule — which is the part
  that actually failed once, so read it literally:
  - **Approval must name the action AND the file** — e.g. "yes, restore schema.graphqls".
    Agreement that a file *is* broken, a vague "fix it" / "repariere das", or any other
    ambiguous assent is **NOT** approval. Without an explicit go for *that exact file*,
    **ASK — as a question, never a narrated step** ("I'll restore X" is not asking).
  - **A change made by a command YOU ran is someone else's work, not yours.** A tracked
    file rewritten by a server run, codegen, formatter, or script is "arrived via a
    script" → discarding it needs approval. The ONLY self-revert exception is an edit you
    made via Edit/Write **in this same turn** — never a side-effect of a process you
    launched. (Scar 2026-06-21: a `spring-boot:run` regenerated a tracked `schema.graphqls`
    with invalid escaping; it got `git checkout`'d on an ambiguous "musste gefixt sein"
    instead of asking — a §6 violation. The hook now covers restore/reset/clean; checkout
    rides on this paragraph.)
- **Before ending a session, update outdated PRDs.** Any PRD whose Plan, Open Questions, or Status no longer matches what's actually in the codebase (because of work landed during the session) gets a brief edit reflecting the new reality — close the resolved OQs, mark phases done/in-progress, note any direction changes. PRDs are the long-term context for future sessions; if they're stale, the next session re-litigates decisions you already made. When the user asks to wrap up / end the session, load the **`wrap-up`** skill — the full session-end checklist (change inventory, green gate, PRD + docs + memory passes, commit proposal, residue list).
- **When writing a MEMORY.md entry that references a PRD status, test status, or specific code location, add staleness tags:** `[since: YYYY-MM-DD] [watch: docs/prd/NNN-name.md]` (or a source file path). At session start, verify any watched paths that have commits newer than their `since` date: `git log --oneline --since=YYYY-MM-DD -- <watch-path>`. If commits exist, re-read the entry and update or remove it before acting on it. Entries without `watch` tags (feedback rules, reference pointers) don't need this check — only entries that describe current project state.

### 6a. Bulk-refactor scripts — see the `bulk-refactor-scripts` skill

Before writing a script that mechanically rewrites Java sources across the
reactor (cross-module rename, signature-regex sweep, diff-based recovery),
load the **`bulk-refactor-scripts`** skill. It encodes the scars from the
2026-05-09 Date → LocalDateTime migration — anchor rules for signature
regexes, the SequenceMatcher diff-recovery trap, the no-wrapper rule for
entity/facade/storage tier, compile-after-every-script discipline.

### 7. Parallel Work — Use a Git Worktree

If you may run in parallel with another agent, or you need a long-running dev server alongside an existing one, **work in your own git worktree** instead of the canonical checkout. The full protocol (creation, port allocation, sharing model, hard rules) lives in the **`git-worktrees`** skill at `.agents/skills/git-worktrees/SKILL.md` — load it before doing anything that touches a worktree. On a quiet `master`/main with no other agent active, you can ignore this rule.

**Compile errors in files you didn't edit:** If `mvn compile` surfaces an error in a file that's not on your change list, before reaching for a fix, check whether another agent (or the user via a linter) modified it concurrently. `stat -c '%Y %y' <file>` against `git log -1 --format=%ct -- <file>` shows the working-copy mtime vs the last-commit time — if the working-copy is newer than your session start *and* you didn't touch it, it's a parallel edit. **Don't fix it.** Surface it to the user and ask whether to wait for the parallel work to land or coordinate. Parallel-edit fixes risk reverting in-flight refactors and step on the other agent's work.

**Hard rule: NEVER fix or revert work in files another session is editing.** Even when the failure looks trivial (one missing import), looks like a transitive consequence of your own change, or the fix is in a config class you've already touched. Other sessions' working-copy files are snapshots, not finished work — touching them produces merge conflicts or reverts in-progress work.

If a parallel-session change broke your build: confirm via the mtime check above, then either move to unrelated work or stop and tell the user. **Don't stash, `git checkout`, or otherwise discard your own changes** to "let theirs land cleanly" — that loses your work or creates merge conflicts later. Stay out of their files; keep your own.

### 8. Server lifecycle — start, stop, restart, inspect

The dev server is a Spring Boot application started via `mvn spring-boot:run` (no package step needed — runs from `target/classes`). Per §7 port convention, the canonical checkout binds **8051**; worktree N uses `8051 + 10·N`. PID/log file paths below assume the canonical checkout — substitute `logs/rapla-N.{pid,log}` in worktrees so multiple servers don't fight for the same files.

**Hard rules (§5):** never `mvn install`, never run from `~/.m2/repository/`, always `-pl rapla-app -am` from the repo root — never `cd rapla-app`.

> **Testing the deployable fat JAR (`mvn package` + signed JNLP webclient/) is a separate concern** — see the **`test-deployment`** skill at `.agents/skills/test-deployment/SKILL.md`. AGENTS.md only covers the dev server.

**Start recipe + startup-wait loop + JDWP + log truncation:** load the **`server-lifecycle`** skill — it carries the full `run_in_background=true` recipe with all gotchas (absolute paths, separate stop/start, `Started Rapla` marker). Load it for any server lifecycle work beyond a plain start.

Quick essentials that stay inline:
- Stop: `pkill -f 'RaplaSpringBoot[A]pplication'` (10 s graceful window — never `kill -9` first). The `[A]` avoids pkill matching the wrapping shell's own command line; **exit 144 from a compound stop command = pkill killed its own shell** — run pkill in its own Bash call, not chained with wait/status logic.
- One server per checkout (port 8051 binds once); use a worktree per §7 for parallel work.
- **Is the running server fresh (does it have your latest code)? Check yourself — never ask the user "did you restart?" and never assume they didn't.** One self-contained probe (empty output = server is fresh; listed files = it predates them):
  `find rapla-app/target/classes -name '*.class' -newermt "$(curl -s localhost:8051/server | grep -o '[0-9-]\{10\} [0-9:]\{5\} GMT' | head -1 | sed 's/ GMT/:00Z/; s/ /T/')" | head`
  *(the ISO-8601 conversion is required — `find` is `bfs` on this machine and rejects the raw `… GMT` string)*
  Run it FIRST whenever (a) a user reports a server-side fix "doesn't work", or (b) you're about to attribute any symptom to a stale/restarted-or-not server. Only if it proves staleness may you mention restarting — with the probe output, not a hunch.
- Never start the server during a `mvn package` build (`spring-boot:repackage` rewrites the same JAR).
- **Testing an external plugin (e.g. dhbwrapla):** run `spring-boot:run` through the *plugin's* aggregator pom with `workingDirectory` pinned to the plugin checkout root — otherwise the plugin's relative-path dataset (`./data`, `./local`) resolves against rapla-app and boot fails on a missing plugin-seeded resource. Full recipe + the `-P<plugin-id>` runtime-dep wiring: `server-lifecycle` skill. Working inside a plugin checkout, read **that repo's `AGENTS.md` first** — it has plugin-specific knobs rapla's doesn't.

#### Default credentials + REST probing — see the `api-testing` skill

Dev DB ships one admin (`admin` / empty password). For the JWT login recipe + bootstrap fetch + queryAppointments + the full URL-namespace map, load the `api-testing` skill.

### 9. Swing client lifecycle — see the `swing-client-launch` skill

The Swing desktop client launches via `mvn exec:java` (NOT
`spring-boot:run` — it uses plain `AnnotationConfigApplicationContext`),
with `-Dexec.daemonThreadJoinTimeout=86400000` to keep the EDT alive
past `main()` return. For the full start/stop/restart procedure, the
two-log convention (`logs/rapla.log` + `logs/rapla-client.log`),
`-Dexec.args="$RAPLA_DEV_TOKEN"` auto-login (PRD 029 Phase 5: CLI takes
an API JWT, not `username password`), and the "don't run two clients
against the same server" rule, load the **`swing-client-launch`**
skill. Server must be running per §8 first.

### 10. Testing conventions — pyramid + nevers

PRD 017's pyramid. Pick the cheapest tier that exercises your code path. **Java: default to tier 1 or 2; reach for tier 3/4 only when you actually need a Spring context. Angular: default to tier 5; only mount a component (tier 6) when behaviour depends on template/DOM. Browser e2e (tier 7) is the widest tier and the most expensive — keep the suite to ~5–15 tests covering critical user paths only.**

| Tier | Where | Engine | Cost / first test | Use for |
|---|---|---|---:|---|
| 1. Pure unit | `rapla-core/src/test/...` | plain JUnit, **no Spring** | < 100 ms | Entities, util, date math, parsing, repeating-rule logic, permission rules, JSON wire-format |
| 2. Facade / storage unit | `rapla-server/src/test/...` extending `FacadeTestSupport` | plain JUnit, **no Spring** | ~150 ms | `RaplaFacade`-level behaviour, XML round-trip, conflict detection, anything that needs real `LocalCache` over real `FileOperator` |
| 3. Web slice | `rapla-app/src/test/...` with `@SpringBootTest` + `@AutoConfigureMockMvc` | Spring context (cached) | ~3–5 s amortised | Controllers, error-mapping, JWT gate, JSON DTO contracts |
| 4. Full Java E2E | `rapla-app/src/test/...` with `@SpringBootTest(webEnvironment=RANDOM_PORT)` | Spring + Tomcat | 7–15 s | Server↔REST-client round-trips, login → query → mutate. Keep small. |
| 5. Angular unit (TS) | `rapla-angular/src/**/*.spec.ts` | Vitest, **no `TestBed`** | < 50 ms | Pure-TS services, validators, RxJS pipelines, formatters, route guards — anything you can construct with `new` |
| 6. Angular component | `rapla-angular/src/**/*.spec.ts` with `TestBed.createComponent(...)` | Vitest + Angular TestBed + jsdom | ~200–500 ms first, ~50 ms subsequent in same `describe` | Template bindings, `@Input`/`@Output` wiring, directives, `*ngIf`/`*ngFor` rendering, Material-driven a11y. Don't reach for it to test logic a tier-5 test could cover. |
| 7. Browser e2e | `rapla-angular/tests/**/*.spec.ts` (Playwright) | Real Chromium + live Spring Boot + live storage | 5–30 s | The full stack (browser → SPA → REST → facade → storage) for things only a real browser sees: OAuth + PKCE redirects, CORS, JS-side state (`sessionStorage`, JS caches), Material rendering, wire-format consumer mismatches. **Don't** use it for anything tiers 1–6 can prove — Playwright is the slowest, flakiest tier; reach for it only when its uniquely-broad coverage is what's required. Authored via Playwright Agents (Planner/Generator/Healer, 1.56+) — see `angular-frontend` skill. Not yet wired into CI (PRD 034 Phase 4). |

#### Nevers (apply to every test you write)

- **Never add a constructor argument to `FacadeImpl` or `FileOperator` without updating `FacadeTestSupport` in the same change** — the rapla-app `@SpringBootTest` ring is the only CI signal that catches drift.
- **Never tag inconsistently** — JDBC-hitting tests get `@Tag("db")`, `@SpringBootTest` acceptance tests get `@Tag("e2e")`. Both are excluded from the default `mvn test`; without the tag they break the fast lane.
- **Never use `@SpringBootTest(webEnvironment=RANDOM_PORT)` when MockMvc would do.** MockMvc is ~5–10× faster.

#### Howtos — see the `testing-conventions` skill

For the `FacadeTestSupport` usage pattern, the "when NOT to use it" bullets, the `@Tag` table with currently-tagged classes, and the `mvn test -Dtest.excludedGroups=` recipes for running the full lane, load the `testing-conventions` skill.

For JaCoCo coverage reports, load the `coverage-report` skill (release-prep only — off by default).

### 11. Never delete code to fix compile errors

Don't delete code to make a compile pass — unless the removal is part of the plan. (The sharp-edged case of §0a #3 *surgical changes*.)

### 12. Never leak server-side data past the user's read scope

Any REST endpoint that returns entities, ids, names, or **the existence** of entities must only ever surface what the current user is already permitted to see in the Swing client. Five rules that fire on every controller change — load the **`data-leak-prevention`** skill for the implementation patterns (Java code), the reference impl (`CalendarViewController.resolveResourceFilter`), and the mandatory tier-3 MockMvc leak-test recipe:

- **Never return entities without filtering by `PermissionController.canRead(entity, user)` at the output boundary.** Don't trust upstream `facade.getX()` to have done it.
- **Never let existence leak.** Id-list endpoints (`?allocatables=a1,a7,a99`) must respond identically for "id doesn't exist" and "id exists but you can't see it" — silently drop both.
- **Never check permissions per-collection only.** A reservation the user can read may reference an allocatable they can't. Re-check contained entities before exposing their ids/names.
- **Never return server-derived data unless every input is readable.** A `RenderedBlock` mixing a reservation + allocatable colours is only safe when the user can read both; otherwise drop the whole block, don't strip fields.
- **Never merge a new id-list / filter endpoint without a tier-3 MockMvc leak test.** Non-admin user, mixed visible/hidden ids in request, assert response is byte-identical to the visible-only subset (and to the all-non-existent-ids case). Status code, headers, body, latency-bucket.

Mental model: behave as if the JSON response were a CSV dump emailed to the user. If anything in it — id, name, count, column header, error text, latency — is something they couldn't have got via the Swing client today, the endpoint is broken.

### 13. Mock-framework policy — nevers

**Default: no mocks of internal rapla types.** Use the real thing — `FacadeTestSupport` at tier 2, real Spring context + MockMvc at tier 3. Mocks save < 100 ms per test and silently bypass the bugs rapla actually ships (Jackson final-field round-trip, operator stub no-ops, permission leaks, MONTHLY semantic, constructor drift). Full rationale: PRD 027.

- **Never `mock(...)` any rapla facade, operator, cache, permission, entity, or storage type** (`RaplaFacade`, `LocalCache`, `PermissionController`, etc.). Construct the real one via `FacadeTestSupport`.
- **Never `@MockBean` / `@SpyBean` in `@SpringBootTest`** — they bust the Spring context cache (fights PRD 007 Phase 2.7). Use a `@TestConfiguration` static class with hand-rolled stub beans (pattern: `PreferencesAdminControllerIntegrationTest.StubPanelsConfig`).
- **Never mock pure-Java models from PRDs 023 / 024** (`AllocationConflictModel`, `RepeatingRuleValidator`, `RaplaBuilder`, layout strategies). No I/O — just construct them.

For the Allowed list (Servlet-API mocks, hand-rolled doubles for `MailInterface`/`PreferencesPanel`) and the audit-grep, load the `testing-conventions` skill.

### 14. Angular frontend (`rapla-angular/`) — see the `angular-frontend` skill

The SPA lives in `rapla-angular/` (Angular 21, served at `/app/`). For build/lint commands, code-style, Vitest/TestBed test patterns, and the wire-probe workflow, load the `angular-frontend` skill. Full layout + URL space is in `rapla-angular/README.md` and PRD 026.

For browser-driven SPA debug/prototype the `playwright` MCP server is wired (tools surface as `mcp__playwright__browser_*`) — usage patterns are in the `angular-frontend` skill, install steps in `docs/development.md`.

One rule that fires without the skill (a footgun that catches agents who skim Angular files without loading the skill):

- **Never start, stop, restart, or `kill` the `ng serve` dev server.** The user runs it in their own terminal (`npm run start:ai` selects a no-live-reload config). If a change isn't visible, ask the user to check their terminal — don't relaunch.

The SPA talks to the server entirely through `HttpClient` (auth + the `/api/graphql` view transport) — there is **no** generated OpenAPI client tree (`src/app/api/`) and **no** `npm run gen:api` step; that client was removed once the SPA went GraphQL-only for data and cookie-`HttpClient` for auth.

### 15. REST endpoints live under `/api/` — literal prefix on the `@HttpExchange` interface

Every `@RestController` in the rapla server routes under `/api/`. PRD 049 made the `@HttpExchange` interface the single source of truth for path + verb + parameter bindings; the controller `implements` it. JAX-RS (`jakarta.ws.rs.*`) is gone from the reactor — `ApiPrefixArchitectureTest.noJakartaWsRsImportsAnywhereInReactor` fails CI on any reintroduction.

**Whenever you create, modify, or delete an endpoint** (any class with `@RestController`, any `@HttpExchange` interface, any `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`/`@RequestMapping`, or any wire DTO), **load the `rest-endpoint-creation` skill** — it carries the interface-implements pattern, the PII-in-query-params guardrail, the explicit-`@RequestParam("name")` rule, the SpringDocGroupsConfig grouping, the OpenAPI spec-capture command, and the four documented exception categories where class-level `@RequestMapping` is still acceptable.

**Allow-list — the only controllers permitted outside `/api/`** (and the reason each is excluded):

| FQCN | URL | Why exempt |
|---|---|---|
| `IndexPageController` | `/`, `/index` | Chooser landing page |
| `LoginPageController` | `/login` | Spring form-login convention |
| `CalendarPageController` | `/rapla/calendar(.csv)?`, `/rapla/internal_calendar(.csv)?` | 🔒 External iCal subscribers depend on these literal URLs |
| `Export2iCalController` | `/rapla/ical`, `/rapla/internal_ical` | 🔒 Outlook/Google/Apple subscribe here — must not move |
| `RaplaJNLPController` | `/raplaclient.jnlp`, `/webclient/**` | Java Web Start launcher manifest |
| `StatusPageController` | `/server` | Server-status HTML page |

`rapla-app/src/test/java/.../ApiPrefixArchitectureTest` enforces this mechanically: any new `@RestController` whose path doesn't start with `/api/` and isn't in the allow-list fails CI. To add a genuine new exception, document the reason in this table and add the FQCN to `ApiPrefixArchitectureTest.ALLOWED_NON_API` — same commit.

**Authentication** — every login flow (browser form `POST /login`, OAuth2 authorization_code + PKCE, the `grant_type=password` direct grant, `grant_type=refresh_token`, `/oauth2/revoke`, API keys), the Swing client's default-OAuth-vs-fallback-password split, the persistent JWT signing model, and the **removed** `/api/auth/login` endpoint — is documented in [`docs/authentication.md`](docs/authentication.md). Read it before touching anything under `/oauth2/*`, `/api/auth/*`, `SecurityConfig`, `AuthorizationServerConfig`, or the Swing login path (`RaplaClientServiceImpl`, `RemoteOperator`, `MyCustomConnector`).

### 16. Read APIs don't mutate — getters / finders / resolvers stay side-effect-free

Anything shaped as a read — `get*`, `find*`, `resolve*`, `lookup*`, `is*`, `has*`, query methods, GET handlers, auth-filter identity resolution — must be side-effect-free. **No storage writes, no firing events, no mutating cached entities other callers can see, no I/O whose failure would surface as a read failure.** Provisioning, sync, identity-mirror updates, lazy persistence belong on a *write* path: lifecycle events (login / token-issuance / refresh), controller `POST`/`PUT`/`PATCH`, a scheduled job — never the read path.

**Exception:** opaque internal caching that doesn't change observable state — memoize a pure derived value, populate a soft-ref cache, lazy-init a transform. Test: "would a concurrent caller see different observable state because of this call?" — if yes, it's a write, and it belongs somewhere else.

**Worked example:** `ExternalUserResolver.resolve()` called `facade.store(...)` on every authenticated request → concurrent `RaplaNewVersionException` → 401 modal in SPA. The right seam is once-per-token at OAuth exchange/refresh, not per-request on the read path.

### 17. No real personal information or secrets in tests, docs, or PRDs

Never put real names, real email addresses, real phone numbers, real user ids that map to real people, or any other identifying real-person data into tests, docs, PRDs, fixtures, schema examples, log snippets, or commit messages. Even when a screenshot or live probe surfaces a real name (e.g. a lecturer's name from a dhbw query), **strip it before it lands in checked-in artefacts**.

- **Secrets too:** never write real passwords, tokens, API keys, or other credentials into checked-in artefacts — **including audit notes and TODOs**. Reference a found secret by location ("password in `docs/graphql.md` line N"), never by value; quoting the finding during a security audit *is* re-leaking it (scar 2026-06-18: plaintext passwords copied into a fresh committable SECURITY-TODO.md while auditing the leak).
- **Deployment-specific production data** (dhbw user counts, visibility profiles, building/course structure) belongs in the private dhbwrapla docs even when anonymized; `rapla/docs/` keeps only the generic pattern plus a reference. Anonymization alone doesn't make it placeable here.

**Allowed:** obvious dummy data that no real person would mistake for themselves —
- Generic placeholders: `<user-id>`, `<lecturer-id>`, `Prof X`, `Dr. A`, `lecturer-1`
- Long-standing fixture personas: `homer` / `monty` / `Simpson Homer` / `Burns Monty` (Springfield characters in `testdefault.xml`), `John Doe`, `Alice` / `Bob`
- Self-identifying maintainer accounts only when the maintainer chose to put their own name in the doc (e.g. the maintainer's own admin credentials in `authtest.md`)

**Forbidden:** real names captured from production-shaped data, even in a worked example — replace with `<lecturer-id>` / `Prof X` before saving. Scan for: capitalised names, non-`*@example.*` emails, phone numbers, id+name pairings.
