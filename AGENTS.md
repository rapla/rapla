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
| `rapla-angular/` | Angular SPA — separate tree, **not in the Maven reactor**. Built with `npm`, served at `/app/` in dev (via `ng serve` proxy on :4200) and prod (via Spring Boot static handler). Talks to the rapla-app REST API at `/api/*`. Has its own `package.json`, Vitest tests; talks to the server via `HttpClient` (auth + `/api/graphql`), no generated client. See AGENTS.md §14 + the `angular-frontend` skill. |

The repo-root `pom.xml` is the reactor aggregator (artifactId `rapla-aggregator`, packaging=pom, lists the five Maven modules); running `mvn` from the repo root walks the whole reactor.
`rapla-angular/` lives outside the reactor entirely — it's an Angular project, not a Maven module; build/test commands are `npm`, not `mvn`.

**Build & Test:**
- Reactor compile: `mvn compile`
- Reactor test: `mvn test`
- Per-module compile: `mvn -pl rapla-server -am compile`  *(use `-am` to also build module deps in-tree; otherwise Maven looks in `~/.m2/repository`)*
- Per-module test: `mvn -pl rapla-server -am test`
- Targeted test: `mvn -pl rapla-app -am test -Dtest=RaplaSpringBootApplicationTest`
- Run the dev server: `mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false -Dspring-boot.run.profiles=local` *(must use `-am` from repo root — see §5 hard rules; do not `cd rapla-app`, do not `mvn install`)*
- Full server lifecycle (background, PID/logs, graceful stop): see §8 below
- Test the deployable fat JAR + signed JNLP webclient: load the **`test-deployment`** skill
- Probe the running server's REST API directly (login, getResources, queryAppointments, etc.) — load the **`api-testing`** skill
- Requires SDKMAN (Java 21 + Maven) on WSL2 Ubuntu

## Skills

Detailed how-tos live as **Agent Skills** under `.agents/skills/<name>/SKILL.md`, auto-discovered by `name` + `description` and loaded on demand — the rules below name the relevant skill where it applies (*"load the X skill"*). Claude Code sees them as `rapla:<name>` only when started with `claude --plugin-dir .`; other engines and the setup details: [`docs/development.md`](docs/development.md#agent-skills--how-engines-find-them). Restructuring this file or its skills: load the **`agents-cleanup`** skill.

## Rules

### 0. Session discipline — context budget + risky-change branching

**Hard rule first: the canonical checkout `~/git/rapla` stays on its branch.** NEVER `git checkout <branch>`, `checkout -b`, or `git switch` here — several sessions share this tree, and a switch drags all of them onto your branch (scar 2026-09-13: a subagent did `checkout -b` with ~60 foreign uncommitted changes). Branch work = a worktree (§7, `git-worktrees` skill; for the Agent tool: `isolation: "worktree"`). Uncommitted edits on the current branch are fine; a branch label without a worktree is not.

- **Don't let context exceed ~60% of the window** — auto-compact is lossy. Run `/compact <hint>` naming what to keep before it fires; after any compact, restate goal + acceptance criteria in one line. Verification fan-outs batch ~10 items per subagent — never one agent per item; name the expected agent count before launching.
- **Use `/branch` (or `/fork`) before a risky mechanical sweep** (Date→LocalDateTime, package renames, the kind the `bulk-refactor-scripts` skill records scars from) — a session snapshot you can abandon at no rollback cost.

### 0a0. Before writing anything — the ladder

From [DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail) ("the best code is the code you never wrote"), verbatim. Walk it top to bottom **before** writing a helper, a utility, or any implementation of a general pattern:

```
1. Does this need to exist?   → no: skip it (YAGNI)
2. Already in this codebase?  → reuse it, don't rewrite
3. Stdlib does it?            → use it
4. Native platform feature?   → use it
5. Installed dependency?      → use it
6. One line?                  → one line
7. Only then: the minimum that works
```

Step 2 is the one that gets skipped, and it costs one `grep`. **If the concept already exists twice, extract it once — never add the third copy** — extracted *into rapla*, never imported from dhbwrapla (the dependency runs one way). Scar 2026-08-05: a third hand-written block-dispatch loop, extracted as `org.rapla.storage.impl.server.BlockedDispatch` only after the user asked. The ladder fires at *design time*, not review time: a design proposal names the rung it stops at, and material handed over "as context" is context, not requirements — scope stays what was explicitly asked.

### 0a. Working principles — Karpathy's four rules

From [multica-ai/andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills). Bias toward caution over speed — for trivial tasks, use judgment.

**1. Think Before Coding** — *Don't assume. Don't hide confusion. Surface tradeoffs.*
- State your assumptions explicitly. If multiple interpretations exist, present them — don't pick silently. If something is unclear, stop, name it, ask.
- If a simpler approach exists, say so. Push back when warranted.
- **A spec may be deviated from — but never casually.** A concrete user spec (most often **"like Swing"**) has a reason even when none is given — for "like Swing" it is almost always parity across ALL call paths. Before deviating: name why the spec exists, test the alternative against those same paths, and ask if it isn't clearly better. **Label convenience as convenience** — a post-hoc rationale for the shorter path is the real failure (scar 2026-08-12, PRD 105: a warn pane instead of the Swing dialog missed that drag and resize have no sheet).
- → rapla: read the relevant `docs/`+PRD first (§2a); `design-dialog` skill for open design space.

**2. Simplicity First** — *Minimum code that solves the problem. Nothing speculative.*
- No features beyond what was asked; no abstractions for single-use code; no unrequested "flexibility"/configurability; no error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

**3. Surgical Changes** — *Touch only what you must. Clean up only your own mess.*
- Don't "improve" adjacent code, comments, or formatting; match existing style.
- Mention unrelated dead code, don't delete it; remove only what YOUR change made unused.
- The test: every changed line should trace directly to the user's request.
- → rapla: never delete code to pass a compile (§11); never touch another session's files (§7).

**4. Goal-Driven Execution** — *Define success criteria. Loop until verified.* See §1; for multi-step tasks, state a brief `step → verify` plan.

### 1. Test-First Approach

The order is **(a) understand → (b) write failing test → (c) fix → (d) verify test passes**, then commit. Skipping the test step for "obvious" fixes is the most common form of slippage in this codebase — don't.

- **Write the failing test before any production code change** for: bug fixes (every one — including 1-line ones), new features, behaviour changes, refactors that should preserve observable behaviour. Run `mvn test` to confirm it fails for the right reason, then fix, then re-run to confirm it passes. Tests live in `src/test/java/` mirroring the source package.
- **Diagnostics-first is fine when** you're still locating the root cause — curl probes, log inspection, exploratory println/diagnostic dumps in a test, integration-test bisects, reading code. Once you can name the broken function/field/method, switch to test-first for the fix.
- **One hypothesis, one variable at a time.** Write the suspected root cause down before changing anything, change exactly one thing, verify before the next. Trace a wrong value *backward* to where it's born — don't dedupe/clamp/guard it where it's observed (that hides the bug, it doesn't fix it). After 3+ failed fix attempts, stop and question the architecture instead of piling on patch #4.
- **No exception for "trivial" fixes.** A removed `final` keyword, a missing null-check, a typo'd config key — all get a regression test, to lock the fix in so the next refactor doesn't reopen it (the Jackson-3 `final`-field bug reopened five times across fields, PRD 011 follow-up).
- **Verifying the test would catch the bug:** after the fix goes green, briefly revert the fix and re-run to confirm it goes red for the right reason; re-apply.
- **After applying a fix, verify it yourself before asking the user to retest** — repeat the probe that surfaced the symptom (curl, test, diff).
- **Audit for sibling occurrences before declaring done.** Once you've named the root cause, grep for the same antipattern elsewhere — bugs cluster. **List the siblings and ASK whether to extend the fix**; don't silently fan out — similar-looking sites can need different handling (PRD 054: one of five dbsql `setTimestamp` sites was broken, the other four were consistent for different reasons).

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

- **Links between docs/PRDs are portable markdown** — relative path + GitHub heading anchor (`[PRD 102 § Decisions locked](102-browser-credential-hardening.md#decisions-locked)`), **never `[[wikilinks]]`** or other Obsidian-only syntax; cite "PRD NNN"/"§ Section" in prose as such a link. Anchor slug rules: `doc-coauthoring` skill.
- **Before diagnosing or planning in an unfamiliar area, check `docs/architecture/`, `docs/authentication.md`, `docs/graphql.md` first** — the invariant may already be documented.
- **When you learn something non-obvious about how Rapla works, propose writing it down** in the right `docs/` file. Never store stable domain knowledge only in MEMORY.md — entries are 150-char pointers.

### 3. Code Style
- No comments unless explicitly requested.
- Follow existing code conventions in the codebase; nothing speculative (§0a #2).
- **Use constructor injection, never field injection.** All `@Inject` / `@Autowired` should be on a constructor parameter list, not on a field. New code (including Spring `@Bean` factory methods, `@Component`/`@Service` classes, and ported legacy classes) must use constructor injection. Any class you edit gets migrated to constructor injection in the same change.
- **Spring DI wiring patterns differ between client and server in this codebase.**
  - **Client (rapla-client)**: `SwingClientConfig` has `@ComponentScan(basePackages = {"org.rapla.client", "org.rapla.plugin"}, ...)`. `@Service`/`@Component` annotations on classes in those packages are picked up automatically. Add `@Service` (with optional `("id")` for `Map<String, T>` consumers) to `@DefaultImplementation` / `@Extension` classes when wiring them.
  - **Server (rapla-server / rapla-app)**: `RaplaSpringBootApplication` only scans `org.rapla.server.spring`. Server-internal classes (`org.rapla.server.internal.*`, `org.rapla.plugin.*.server.*`) are wired via explicit `@Bean` factory methods in `ServerCoreConfig` / `ServerServiceConfig` — `new XImpl(...)` + `beanFactory.autowireBean(impl)` for request-scoped, `new XImpl(deps...)` for singletons. `@Service` on a server-internal class is dead code; extending the scan creates duplicate-bean conflicts. **Keep the explicit-`@Bean`-factory pattern on the server until a coordinated refactor flips it in one sweep.**
- **No `System.err`/`System.out` in `src/main/java/`** — use SLF4J (`LoggerFactory.getLogger(...)`) throughout. Acceptable in tests when no logger is reachable.
- **On the server, never wrap `Promise` in a `CountDownLatch` to make it sync** — cast to `SyncStorageOperator` and call the `*Sync` method directly (`getConflictsSync`, `queryAppointmentsSync`, …). Promise→latch wrappers add boilerplate, lose stack traces, and mask bugs as timeouts.
- **Server-side code depends on `StorageOperator` / `CachableStorageOperator`, not `RaplaFacade`.** The facade is the Swing-client API; on the server it's a process-singleton with a mutable per-thread-leaky `workingUserId` field. Controllers, services, provisioners, auth stores — anything in `rapla-server` / `rapla-app` / plugin `*/server/*` — take the operator directly.

### 5. Build Discipline

**Reactor hard rules** (referenced from §8 and §9):
- **NEVER `mvn install`** and **never run rapla JARs from `~/.m2/repository/`**. Both shadow in-reactor `target/classes` for sibling modules with stale code — an hours-of-debugging trap. The reactor's in-tree classpath handles all sibling resolution.
- **The `-am` ("also-make") flag is mandatory** for any cross-module compile/test/run. Without it, Maven resolves siblings from `~/.m2/repository`, which is stale or missing.
- Run from the repo root with `-pl <module> -am` — never `cd <module>`.

**Routine workflow:**
- Use `mvn compile` for the post-edit type-check. The repo-root `pom.xml` is the reactor aggregator.
- **Targeted tests only during a session.** Use `-pl <module-where-test-lives> -am` — not always `rapla-app` (a `rapla-server` test runs faster as `mvn -pl rapla-server -am test -Dtest=ClassName`). Use `-pl rapla-app -am` only for tests that genuinely live in `rapla-app`.
- **Full `mvn test` only at session end** or when the user asks for a green-build sign-off — or as investigation when a targeted failure suggests a wider regression.
- **Cross-module test gotcha:** if `mvn -pl rapla-app -am test -Dtest=Foo` test-compiles an upstream module with broken test sources, add `-Dsurefire.failIfNoSpecifiedTests=false`. **Never replace `-am` with an explicit module list** — a missed module silently resolves from `~/.m2/repository`.
- **Don't `mvn clean` routinely** — incremental compile is reliable.
- **ALWAYS `mvn clean compile` after deleting, renaming, or moving a class.** Stale `.class` files for the old name linger in `target/` and make broken references resolve at compile time but blow up at runtime. No exceptions; fires even for a single-class delete.
- **ALWAYS `mvn clean` before `mvn package` / packaging.** Stale `target/` artefacts shadow assembly inputs — you ship a broken artifact. Recipe: `mvn -pl rapla-app -am clean package -DskipTests [-Psign-pkcs11|-Psign-jks]`.

### 6. Git
- Never commit unless explicitly asked.
- Never push unless explicitly asked.
- **Never discard uncommitted changes to tracked files without explicit user approval** — `git checkout … -- <file>`, `git restore`, `git reset --hard`, `git clean`, `git stash` (any form except `list`/`show`). The rule covers INTENT, not a verb list: any command that rewrites a tracked working file to another version is a discard, **even if recoverable** ("it's only stashed", "I made a backup" are rationalizations — scar 2026-07-08: a `git stash -- <file>` to lint HEAD reverted two sessions' work). If the user genuinely wants one, they run it via the `!` prefix. To compare against HEAD, use `git show HEAD:<path> > /tmp/…` / `git diff` — never swap the working file.
  - **Approval must name the action AND the file** — e.g. "yes, restore schema.graphqls". Agreement that a file *is* broken, a vague "fix it" / "repariere das", or any other ambiguous assent is **NOT** approval. Without it, **ASK — as a question, never a narrated step** ("I'll restore X" is not asking).
  - **A change made by a command YOU ran is someone else's work, not yours.** A tracked file rewritten by a server run, codegen, formatter, or script needs approval to discard. The ONLY self-revert exception is an edit you made via Edit/Write **in this same turn** (scar 2026-06-21: a `spring-boot:run`-regenerated `schema.graphqls` got `git checkout`'d on an ambiguous remark).
- **Before ending a session, update outdated PRDs** whose Plan, Open Questions, or Status no longer match the codebase — close resolved OQs, mark phases, note direction changes; stale PRDs make the next session re-litigate. When the user asks to wrap up, load the **`wrap-up`** skill (full session-end checklist).
- **MEMORY.md entries that reference a PRD status, test status, or code location get staleness tags:** `[since: YYYY-MM-DD] [watch: <path>]`. At session start, `git log --oneline --since=YYYY-MM-DD -- <watch-path>`; if commits exist, re-read and update or remove the entry before acting on it. Feedback rules and reference pointers need no tags.

### 6a. Bulk-refactor scripts — see the `bulk-refactor-scripts` skill

Before writing a script that mechanically rewrites Java sources across the reactor (cross-module rename, signature-regex sweep, diff-based recovery), load the **`bulk-refactor-scripts`** skill — anchor rules for signature regexes, the SequenceMatcher diff-recovery trap, the no-wrapper rule for entity/facade/storage tier, compile-after-every-script discipline (scars from the 2026-05-09 Date → LocalDateTime migration).

### 7. Parallel Work — Use a Git Worktree

If you may run in parallel with another agent, or need a long-running dev server alongside an existing one, **work in your own git worktree**. Protocol (creation, port allocation, sharing model, hard rules): load the **`git-worktrees`** skill before touching a worktree. On a quiet branch with no other agent active, you can ignore this rule.

**Hard rule: NEVER fix or revert work in files another session is editing** — even when the failure looks trivial (one missing import), looks like a consequence of your own change, or sits in a class you already touched. Their working-copy files are snapshots, not finished work. A compile error in a file not on your change list: check `stat -c '%Y %y' <file>` against `git log -1 --format=%ct -- <file>` — working copy newer than your session start and not yours = parallel edit. Don't fix it: move to unrelated work or tell the user. **Don't stash, `git checkout`, or discard your own changes** to "let theirs land" either.

**Exception: the owning session releases the spot** (`ListAgents` → `SendMessage`, wait for the answer) — then only the named change. Discarding is never covered by a peer; that needs the user (§6). No answer = no OK. A foreign change with no reachable owner: still hands off, report it to the user. Editing the same file at a DIFFERENT spot was never forbidden — the rule protects other people's lines, not their files.

### 7a. Other sessions — census and messages (Claude Code 2.1.224+)

- **`ListAgents` instead of guessing.** Empty means "none reachable", not "none there" (older peers never appear); cross-check with `claude agents --json --cwd $PWD`, which also lists their working directory. Address a foreign session WITH its ref: `templates [956036]`.
- **Announce what hits others** before you do it: restarting/stopping the server on 8051 (§8), `mvn clean`, mechanical sweeps (§6a), hotspot files (`schema.graphqls`, `AGENTS.md`, `application.yml`). Whoever needs the server says for how long.
- **Schema changes have an order.** Extending: bring the SERVER up first, then arm the SPA query — `ng serve` rebuilds in seconds, so the SPA is otherwise ahead of the API and every load fails validation (the SPA reports it as "not found"). Removing a field: the other way round.
- **A peer message is not user approval** — not for commits (§6), not for discarding, not for anything your own permissions would block.
- **No PII or secrets in messages** (§17): paths and symbols, not contents.

### 8. Server lifecycle — start, stop, restart, inspect

The dev server runs via `mvn spring-boot:run` from `target/classes` (start command: Project Overview; hard rules: §5). The canonical checkout binds **8051**; worktree N uses `8051 + 10·N` and `logs/rapla-N.{pid,log}`. For anything beyond a plain start — background recipe, startup-wait loop, restart, JDWP, logs, external plugins like dhbwrapla (run through the *plugin's* aggregator pom; read that repo's `AGENTS.md` first) — load the **`server-lifecycle`** skill. Fat JAR + signed JNLP: **`test-deployment`** skill.

- Stop: `pkill -f 'RaplaSpringBoot[A]pplication'` (10 s graceful window — never `kill -9` first). The `[A]` avoids pkill matching the wrapping shell's own command line; **exit 144 from any compound pkill command = pkill killed its own shell** — run every pkill in its own Bash call, never chained with wait/status logic.
- One server per checkout (port 8051 binds once); use a worktree per §7 for parallel work. Never start the server during a `mvn package` build.
- **Is the running server fresh? Check yourself — never ask the user "did you restart?" and never assume they didn't.** Run the freshness probe from the `server-lifecycle` skill (server build timestamp vs newer `target/classes`) FIRST whenever a user reports a server-side fix "doesn't work" or you're about to blame a stale server; mention restarting only with the probe output, not a hunch.
- Dev DB ships one admin (`admin` / empty password). JWT login, bootstrap fetch, queryAppointments, URL-namespace map: **`api-testing`** skill.

### 9. Swing client lifecycle — see the `swing-client-launch` skill

The Swing client launches via `mvn exec:java` (NOT `spring-boot:run`) with the server from §8 already running; never run two clients against the same server. Invocation, EDT keep-alive timeout, JWT auto-login args, the two-log convention: load the **`swing-client-launch`** skill.

### 10. Testing conventions — pyramid + nevers

PRD 017's pyramid. Pick the cheapest tier that exercises your code path. **Java: default to tier 1 or 2; reach for tier 3/4 only when you actually need a Spring context. Angular: default to tier 5; only mount a component (tier 6) when behaviour depends on template/DOM. Browser e2e (tier 7) is the most expensive — keep the suite to ~5–15 critical user paths.**

| Tier | Where | Engine | Cost / first test | Use for |
|---|---|---|---:|---|
| 1. Pure unit | `rapla-core/src/test/...` | plain JUnit, **no Spring** | < 100 ms | Entities, util, date math, parsing, repeating-rule logic, permission rules, JSON wire-format |
| 2. Facade / storage unit | `rapla-server/src/test/...` extending `FacadeTestSupport` | plain JUnit, **no Spring** | ~150 ms | `RaplaFacade`-level behaviour, XML round-trip, conflict detection, anything that needs real `LocalCache` over real `FileOperator` |
| 3. Web slice | `rapla-app/src/test/...` with `@SpringBootTest` + `@AutoConfigureMockMvc` | Spring context (cached) | ~3–5 s amortised | Controllers, error-mapping, JWT gate, JSON DTO contracts |
| 4. Full Java E2E | `rapla-app/src/test/...` with `@SpringBootTest(webEnvironment=RANDOM_PORT)` | Spring + Tomcat | 7–15 s | Server↔REST-client round-trips, login → query → mutate. Keep small. |
| 5. Angular unit (TS) | `rapla-angular/src/**/*.spec.ts` | Vitest, **no `TestBed`** | < 50 ms | Pure-TS services, validators, RxJS pipelines, formatters, route guards — anything you can construct with `new` |
| 6. Angular component | `rapla-angular/src/**/*.spec.ts` with `TestBed.createComponent(...)` | Vitest + Angular TestBed + jsdom | ~200–500 ms first | Template bindings, `@Input`/`@Output` wiring, directives, rendering, Material a11y — not logic a tier-5 test could cover |
| 7. Browser e2e | `rapla-angular/tests/**/*.spec.ts` (Playwright) | Real Chromium + live server + storage | 5–30 s | Only what a real browser sees: OAuth/PKCE redirects, CORS, JS-side state, Material rendering, wire-format consumer mismatches. Authoring: `angular-frontend` skill. |

#### Nevers (apply to every test you write)

- **Never add a constructor argument to `FacadeImpl` or `FileOperator` without updating `FacadeTestSupport` in the same change** — the rapla-app `@SpringBootTest` ring is the only CI signal that catches drift.
- **Never tag inconsistently** — JDBC-hitting tests get `@Tag("db")`, `@SpringBootTest` acceptance tests get `@Tag("e2e")`. Both are excluded from the default `mvn test`; without the tag they break the fast lane.
- **Never use `@SpringBootTest(webEnvironment=RANDOM_PORT)` when MockMvc would do.** MockMvc is ~5–10× faster.

`FacadeTestSupport` usage, the `@Tag` table, full-lane `mvn test -Dtest.excludedGroups=` recipes: load the **`testing-conventions`** skill. JaCoCo coverage (release-prep only): **`coverage-report`** skill.

### 11. Deletions need a plan or a go

Don't delete code to make a compile pass — unless the removal is part of the plan (§0a #3 *surgical changes*). Tearing down features built earlier in the session (e.g. in a redesign) waits for an explicit go — propose the delta, ask. A removal includes its residue: routes, menus, nav entries.

- **Removal = behaviour inventory:** before removing, name which agreed behaviour the code carries — each one is kept, ported, or dropped with the user's OK; never deleted along with "dead code" (otherwise requirements die with the implementation).
- **Regression reported ("we had that already") ⇒ rebuild the agreed form with forward edits** — no git revert (§6), no copying old blocks back, no silent substitute design; if the form is technically blocked, name the options and ask (§0a #1).

### 12. Never leak server-side data past the user's read scope

Any REST endpoint that returns entities, ids, names, or **the existence** of entities must only ever surface what the current user is already permitted to see in the Swing client. Five rules that fire on every controller change — load the **`data-leak-prevention`** skill for the implementation patterns (Java code), the reference impl (`AccessTargetFilter`, rapla-app graphql), and the mandatory tier-3 MockMvc leak-test recipe:

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

The SPA lives in `rapla-angular/` (served at `/app/`) and talks to the server only through `HttpClient` (auth + `/api/graphql`) — no generated OpenAPI client. Build/lint, code style, Vitest/TestBed patterns, the wire-probe workflow, and the `playwright` MCP (`mcp__playwright__browser_*`) for browser debugging: load the **`angular-frontend`** skill. Layout + URL space: `rapla-angular/README.md` and PRD 026.

- **Never start, stop, restart, or `kill` the `ng serve` dev server.** The user runs it in their own terminal (`npm run start:ai`). If a change isn't visible, ask the user to check their terminal — don't relaunch.

### 15. REST endpoints live under `/api/` — literal prefix on the `@HttpExchange` interface

Every `@RestController` routes under `/api/`. The `@HttpExchange` interface is the single source of truth for path + verb + parameter bindings; the controller `implements` it (PRD 049). JAX-RS (`jakarta.ws.rs.*`) is gone — `ApiPrefixArchitectureTest.noJakartaWsRsImportsAnywhereInReactor` fails CI on any reintroduction.

**Whenever you create, modify, or delete an endpoint** (any class with `@RestController`, any `@HttpExchange` interface, any `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`/`@RequestMapping`, or any wire DTO), **load the `rest-endpoint-creation` skill** — the interface-implements pattern, the PII-in-query-params guardrail, the explicit-`@RequestParam("name")` rule, SpringDoc grouping, spec capture, and the documented class-level `@RequestMapping` exceptions.

**Only a short allow-list of controllers may live outside `/api/`** — landing/login/status pages, JNLP, the OpenAPI meta endpoint, and the 🔒 iCal/calendar URLs external subscribers depend on (never move those). `ApiPrefixArchitectureTest` enforces it; the table with each exemption's reason lives in the `rest-endpoint-creation` skill. A genuine new exception = a row in that table + the FQCN in `ApiPrefixArchitectureTest.ALLOWED_NON_API`, same commit.

**Authentication** (form login, OAuth2 + PKCE, password/refresh grants, revoke, API keys, Swing login split, JWT signing, the removed `/api/auth/login`): read [`docs/authentication.md`](docs/authentication.md) before touching `/oauth2/*`, `/api/auth/*`, `SecurityConfig`, `AuthorizationServerConfig`, or the Swing login path (`RaplaClientServiceImpl`, `RemoteOperator`, `MyCustomConnector`).

### 16. Read APIs don't mutate — getters / finders / resolvers stay side-effect-free

Anything shaped as a read — `get*`, `find*`, `resolve*`, `lookup*`, `is*`, `has*`, query methods, GET handlers, auth-filter identity resolution — must be side-effect-free. **No storage writes, no firing events, no mutating cached entities other callers can see, no I/O whose failure would surface as a read failure.** Provisioning, sync, identity-mirror updates, lazy persistence belong on a *write* path: lifecycle events (login / token-issuance / refresh), controller `POST`/`PUT`/`PATCH`, a scheduled job.

**Exception:** opaque internal caching that doesn't change observable state (memoized pure values, soft-ref caches, lazy-init transforms). Test: "would a concurrent caller see different observable state because of this call?" — if yes, it's a write. (Scar: `ExternalUserResolver.resolve()` stored on every request → concurrent `RaplaNewVersionException` → 401 in the SPA; the right seam was once-per-token at OAuth exchange/refresh.)

### 17. No real personal information or secrets in tests, docs, or PRDs

Never put real names, real email addresses, real phone numbers, real user ids that map to real people, or any other identifying real-person data into tests, docs, PRDs, fixtures, schema examples, log snippets, or commit messages. Even when a screenshot or live probe surfaces a real name (e.g. a lecturer's name from a dhbw query), **strip it before it lands in checked-in artefacts**.

- **Secrets too:** never write real passwords, tokens, API keys, or other credentials into checked-in artefacts — **including audit notes and TODOs**. Reference a found secret by location ("password in `docs/graphql.md` line N"), never by value; quoting it during an audit *is* re-leaking it.
- **Security findings are leaks too (user ruling 2026-09-14).** Audit results, pentest reports, review findings with exploit paths, leak-check/security-check reports and session coordination logs are never tracked — they live under `docs/security/` or `docs/coordination/` (both gitignored) or carry a marker in the file name (`*security-audit*`, `*security-review*`, `*security-findings*`, `*pentest*`, `*-findings.md`, `*leak-check*`, `*security-check*`, all gitignored anywhere). A PRD or architecture doc may describe a CLOSED issue and its fix, never an open hole with its path. Same scheme in dhbwrapla.
- **Deployment-specific production data** (dhbw user counts, visibility profiles, building/course structure) belongs in the private dhbwrapla docs even when anonymized; `rapla/docs/` keeps only the generic pattern plus a reference.

**Allowed:** obvious dummy data — generic placeholders (`<user-id>`, `<lecturer-id>`, `Prof X`, `lecturer-1`), long-standing fixture personas (`homer` / `monty` / `Simpson Homer` / `Burns Monty` from `testdefault.xml`, `John Doe`, `Alice` / `Bob`), and self-identifying maintainer accounts where the maintainer chose to put their own name in the doc.

**Forbidden:** real names captured from production-shaped data, even in a worked example — replace with `<lecturer-id>` / `Prof X` before saving. Scan for: capitalised names, non-`*@example.*` emails, phone numbers, id+name pairings.
