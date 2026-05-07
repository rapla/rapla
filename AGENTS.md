# AGENTS.md - Opencode Rules

## Project Overview

**Rapla** is a Java-based resource scheduling and event planning application (v2.1-SNAPSHOT, AGPL/Apache2). It uses Maven, targets Java 17, runs on Java 21. Key technologies: Spring Boot 3.2 (Tomcat 10), Swing, JAX-RS / Spring MVC, RxJava3, iCal4j, Exchange Web Services.

The codebase is a **5-module Maven reactor** (PRD 005, 2026-05-07):

| Module | Role |
|---|---|
| `rapla-bom` | BOM + parent POM (versions, plugin config). Was `parent/`. |
| `rapla-core` | Shared layer: entities, facade, framework, scheduler, storage interfaces, REST DTOs/endpoint interfaces, components/{util,layout,restproxy,i18n}, logger, inject. NO Spring Boot, NO Swing. |
| `rapla-client` | Swing client + presenters: `org.rapla.client.*`, components/{calendar,calendarview,iolayer,tablesorter,treetable}, plugin `*/client/*` and `*/swing/*`. Uses `spring-context` only — explicit `AnnotationConfigApplicationContext`, NOT `@SpringBootApplication`. Depends on rapla-core. |
| `rapla-server` | Server: `org.rapla.server.*` (excl. the `RaplaSpringBootApplication` entry point), JDBC storage, REST controllers, Spring autoconfig (`META-INF/spring/AutoConfiguration.imports`), plugin `*/server/*`. Depends on rapla-core + rapla-client (known compromise — see PRD 005 D3 for the abstractcalendar/RaplaBuilder coupling). |
| `rapla-app` | Runnable Spring Boot application: `RaplaSpringBootApplication`, `application.yml`, `src/assembly/`, `src/main/distribution/`, signing profiles, JNLP webclient/ staging. Produces `rapla-2.1-SNAPSHOT.jar` (Spring Boot fat JAR). |

The repo-root `pom.xml` is the reactor aggregator (artifactId `rapla-aggregator`, packaging=pom, lists all five modules).
`custom/` is intentionally NOT in the reactor (its WAR-overlay shape is being rethought; future PRD).

**Build & Test:**
- Reactor compile: `mvn compile`
- Reactor test: `mvn test`
- Per-module compile: `mvn -pl rapla-server -am compile`  *(use `-am` to also build module deps in-tree; otherwise Maven looks in `~/.m2/repository`)*
- Per-module test: `mvn -pl rapla-server -am test`
- Targeted test: `mvn -pl rapla-app -am test -Dtest=RaplaSpringBootApplicationTest`
- Run the app: `java -jar rapla-app/target/rapla-2.1-SNAPSHOT.jar`
- Requires SDKMAN (Java 21 + Maven) on WSL2 Ubuntu

The reactor aggregator (`pom.xml` at the repo root, packaging=pom, artifactId=`rapla-aggregator`) lists the 5 module siblings. Running `mvn` from the repo root walks the whole reactor.

## Rules

### 1. Test-First Approach
- Always write a failing test before implementing a feature or bug fix.
- Run `mvn test` to verify the test fails, then implement, then verify it passes.
- Tests go in `src/test/java/` mirroring the source package structure.

### 2. PRD-Driven Development
- Before implementing **anything**, check `docs/prd/` for an existing PRD that matches the feature or bug.
- If a matching PRD exists, read it, update it if needed, and plan implementation there.
- If no PRD exists, create one in `docs/prd/` before writing any code.

### 3. PRD Format
- File naming: `docs/prd/NNN-short-name.md` (e.g., `docs/prd/001-spring-boot-migration.md`).
- Each PRD must contain:
  - **Title** and **Status** (draft / in-progress / done)
  - **Goal** — what and why
  - **Scope** — what files/packages are affected
  - **Plan** — ordered implementation steps
  - **Tests** — what tests to write and when
  - **Open Questions** — unresolved decisions
- Keep PRDs concise. Update the status as work progresses.

### 4. Code Style
- No comments unless explicitly requested.
- Follow existing code conventions in the codebase.
- **Use constructor injection, never field injection.** All `@Inject` / `@Autowired` should be on a constructor parameter list, not on a field. New code (including Spring `@Bean` factory methods, `@Component`/`@Service` classes, and ported legacy classes) must use constructor injection. Existing field-injected code may be left alone until it's touched, but any class you edit should be migrated to constructor injection in the same change. Rationale: constructor injection makes dependencies explicit, supports `final` fields, and lets the class be instantiated for tests without a DI container.

### 5. Build Discipline
- Use `mvn compile` for the routine compile-check after edits. It's fast and catches type errors. The repo-root `pom.xml` is the reactor aggregator (post-PRD-005).
- **Do not run the full `mvn test` after every small step or after every PRD step.** Full tests are slow (~60–120 s reactor-wide with Spring context startup overhead) and accumulate cost across many incremental edits.
- **During an active session, only run targeted tests.** Use `mvn -pl rapla-app -am test -Dtest=ClassName` for a single test class. Pick the tests that exercise the code you just touched — for example, after editing an XML reader/writer, run the XML round-trip tests, not the whole suite.
- **Run the full reactor `mvn test` only at session end** (when you're about to hand off, or when the user explicitly asks for a green-build sign-off). Don't run it as a "checkpoint" between iterations of the same task — `mvn compile` already catches type errors, and full-suite runs in a tight loop just burn time without catching anything `mvn compile` + targeted tests wouldn't.
- If a targeted test fails in a way that suggests a wider regression, *then* expand to the full suite — but only as an investigation step, not as routine.
- **Targeted tests across the multi-module reactor:** when a test class lives in (say) `rapla-app` but the simplest `mvn -pl rapla-app -am test -Dtest=Foo` invocation also test-compiles upstream modules, *and* one of those upstream modules has broken test sources, you'll get a spurious red. Two fixes: (a) add `-Dsurefire.failIfNoSpecifiedTests=false` so per-module surefire skips modules where no test matches the `-Dtest` pattern; (b) explicitly list the modules whose tests should run, e.g. `mvn -pl rapla-bom,rapla-core,rapla-server,rapla-app test -Dtest=Foo -Dsurefire.failIfNoSpecifiedTests=false` — bypasses `rapla-client`'s test-compile entirely. Use (a) by default; reach for (b) when there's a known-broken sibling module.
- **Don't run `mvn clean` routinely.** `mvn clean` deletes `target/` and forces full recompilation, which is slow. Maven's incremental compile is reliable in normal use. **Reach for `mvn clean` only after renaming, moving, or deleting classes** — that's the case incremental compile can't cover, because stale `.class` files for the old name linger in `target/` and the JVM happily loads them. For everything else (type errors, regressions, "the build feels weird"), plain `mvn compile` / `mvn test -Dtest=...` is correct — investigate what's actually wrong rather than reaching for clean. Even a final hand-off build doesn't need clean unless you've moved files in this session. The goal is to keep the inner loop fast.

### 6. Git
- Never commit unless explicitly asked.
- Never push unless explicitly asked.

### 7. Parallel Work — Use a Git Worktree

If you may run in parallel with another agent, or you need a long-running dev server alongside an existing one, **work in your own git worktree** instead of the canonical checkout. The full protocol (creation, port allocation, sharing model, hard rules) lives in the **`git-worktrees`** skill at `.agents/skills/git-worktrees/SKILL.md` — load it before doing anything that touches a worktree. On a quiet `master`/main with no other agent active, you can ignore this rule.
