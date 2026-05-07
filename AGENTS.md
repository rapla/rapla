# AGENTS.md - Opencode Rules

## Project Overview

**Rapla** is a Java-based resource scheduling and event planning application (v2.1-SNAPSHOT, AGPL/Apache2). It uses Maven, targets Java 8+ (runs on Java 21), and features a Swing client, a web server (Jetty + RESTEasy), and a GWT web frontend. The codebase is a single Maven module under `org.rapla` with packages for client, server, storage, entities, plugins, facade, and framework. Key technologies: JAX-RS, RxJava3, Jetty, iCal4j, Exchange Web Services.

**Build & Test:**
- Compile: `mvn compile`
- Test: `mvn test`
- Requires SDKMAN (Java + Maven) on WSL2 Ubuntu

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
- Use `mvn compile` for the routine compile-check after edits. It's fast and catches type errors.
- **Do not run the full `mvn test` after every small step or after every PRD step.** Full tests are slow (~30–60 s + Spring context startup overhead) and accumulate cost across many incremental edits.
- **During an active session, only run targeted tests.** Use `mvn test -Dtest=ClassName` for a single test class, or `mvn test -Dtest='Foo*Test,Bar*Test'` for a small set. Pick the tests that exercise the code you just touched — for example, after editing an XML reader/writer, run the XML round-trip tests, not the whole suite.
- **Run the full `mvn test` only at session end** (when you're about to hand off, or when the user explicitly asks for a green-build sign-off). Don't run it as a "checkpoint" between iterations of the same task — `mvn compile` already catches type errors, and full-suite runs in a tight loop just burn time without catching anything `mvn compile` + targeted tests wouldn't.
- If a targeted test fails in a way that suggests a wider regression, *then* expand to the full suite — but only as an investigation step, not as routine.
- **Don't run `mvn clean` routinely.** `mvn clean` deletes `target/` and forces full recompilation, which is slow. Maven's incremental compile is reliable in normal use. Reach for `mvn clean` only when:
  - you hit a specific class-loading problem (e.g. `NoClassDefFoundError`, `MalformedInputException` in resource filtering, or a stale `BeanCreationException` referencing a class that compiles fine in source)
  - you're doing a final full-suite build before handing off and want a clean-room sign-off
  - you've moved/renamed/deleted classes and want to make sure no stale `.class` lingers
  Otherwise prefer plain `mvn compile` / `mvn test -Dtest=...`. The goal is to keep the inner loop fast.

### 6. Git
- Never commit unless explicitly asked.
- Never push unless explicitly asked.

### 7. Parallel Work — Use a Git Worktree

When starting a task that may run in parallel with other agents, **work in your own git worktree**, not the canonical checkout. The full layout, sharing model, and rationale are in the [Parallel Agent Workflow](#parallel-agent-workflow-git-worktrees) section below — read it once. The protocol below is what to actually do.

**Before starting work, check where you are:**

```bash
# 1. Confirm you're in a rapla checkout (canonical or worktree)
pwd                          # expect /home/chris/git/rapla*  or similar
git rev-parse --show-toplevel
git worktree list            # shows all existing worktrees and their branches
```

**If another agent is already running in `/home/chris/git/rapla/`, or you've been asked to work in parallel, create a worktree:**

```bash
# 2. From the canonical checkout, create a sibling worktree on a new branch
cd /home/chris/git/rapla
git worktree add ../rapla-<task-slug> -b <task-slug>

# 3. Move into it for the rest of the task
cd ../rapla-<task-slug>
```

Naming convention for `<task-slug>`: short, hyphenated, descriptive (e.g. `spring-boot-phase4`, `worktree-docs`, `prd-005-multimodule`). Avoid bare `agent-A` / `agent-B` — names should describe the work, not the actor.

**While working in the worktree:**
- All `mvn` commands run inside the worktree directory — they use the worktree's own `target/`.
- Commits land on the worktree's own branch; they don't affect any other worktree.
- `~/.m2/repository` is shared with every other worktree, so a `mvn install` here is visible to a build elsewhere. If you must `mvn install` a `SNAPSHOT`, qualify the version (`2.1-<task-slug>-SNAPSHOT`) so you don't overwrite another worktree's snapshot.
- Never start a second `mvn` while one is already running in the same worktree.

#### Port allocation for long-running servers

Tests (`mvn test`) bind no ports beyond what the test framework manages — Spring Boot tests should use `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` and the OS assigns a free port per JVM. Cross-worktree tests don't collide.

**Long-running dev servers do collide.** Two worktrees running `mvn spring-boot:run` (or `ng serve`) simultaneously will fight for the same port and the second one fails with `BindException`. **Convention: each worktree that actually starts a long-running server picks an offset `N`.**

| Worktree | Backend (`server.port`) | Angular (`ng serve --port`) |
|---|---|---|
| `rapla/` (canonical, N=0) | 8051 | 4200 |
| worktree N=1 | 8061 | 4201 |
| worktree N=2 | 8071 | 4202 |
| worktree N=3 | 8081 | 4203 |

Formula: backend = `8051 + 10·N`, Angular = `4200 + N`. Pick the next free `N` when you start a server — only allocate to worktrees that actually need one, not every worktree on principle.

**To apply the offset, drop a gitignored `application-local.yml` at the worktree root:**

```yaml
# application-local.yml — per-worktree override, never committed
server:
  port: 8061
rapla:
  file-datasources:
    raplafile: ${user.dir}/data/data.xml   # already per-worktree via ${user.dir}
```

Spring Boot loads `application-local.yml` after `application.yml` automatically when `spring.profiles.active=local` is set, or you can pass it directly:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8061
# or, with the profile:
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**For Angular**, drop a gitignored `proxy.conf.local.json` next to `proxy.conf.json` and run:

```bash
ng serve --port 4201 --proxy-config proxy.conf.local.json
```

The proxy file points API calls at *this worktree's* backend port (`http://localhost:8061`), so the Angular dev server in worktree N=1 talks to the backend in worktree N=1, not the canonical one.

**Required `.gitignore` entries** (add once, in the canonical checkout):

```
application-local.yml
src/main/resources/application-local.yml
proxy.conf.local.json
```

**Hardcoded ports in tests are a bug.** If `mvn test` ever fails with `BindException`, find the offending test (grep for `8051`, `8080`, `setPort(`, `port="`) and switch it to `RANDOM_PORT` / `@LocalServerPort`. Don't work around it by stopping other worktrees' servers.

**When the task is done:**

```bash
# 4. From outside the worktree (e.g. cd back to the canonical checkout)
cd /home/chris/git/rapla
git worktree remove ../rapla-<task-slug>      # deletes the directory + metadata
# the branch itself is preserved; delete it separately if you don't need it:
git branch -d <task-slug>                     # only if merged
```

**Hard rules:**
- Never run two `mvn` invocations against the same worktree at the same time — Maven has no project lock and will silently corrupt `target/`.
- Never check out the same branch in two worktrees — git refuses, and trying to force it via `git switch -C` corrupts the index.
- Never edit files in `/home/chris/git/rapla/` while another agent is also working there. If in doubt, make a worktree.
- Never delete a worktree directory with `rm -rf`. Use `git worktree remove` so the metadata under `rapla/.git/worktrees/` stays consistent. If you already did `rm -rf`, run `git worktree prune` to clean up.

## Parallel Agent Workflow (Git Worktrees)

When multiple agents (or developers in multiple panes) work on this repo in parallel, **each agent must run in its own git worktree.** Do NOT run concurrent `mvn` invocations against the same checkout — Maven holds no project-level lock and will silently corrupt `target/classes/` and `target/*.jar` when two builds race. A worktree gives each agent its own working tree and its own `target/`, so concurrent builds never touch the same files.

### Folder layout (sibling worktrees)

```
/home/chris/git/
├── rapla/                       ← canonical checkout. dhbwrapla-container/pom.xml
│   ├── .git/                    ←   resolves ../../rapla against THIS path, so it must
│   ├── pom.xml                  ←   keep existing. Default branch lives here (master
│   ├── src/                     ←   or whatever the active feature branch is).
│   └── target/                  ← only this worktree's build output
│
├── rapla-agent-A/               ← worktree, on its own branch
│   ├── .git                     ← FILE (not dir), points to rapla/.git/worktrees/agent-A/
│   ├── pom.xml                  ← own copy of the source tree
│   ├── src/
│   └── target/                  ← isolated; concurrent mvn here can't race rapla/target/
│
├── rapla-agent-B/               ← second parallel worktree
│   └── …
│
└── dhbwrapla/                   ← sibling repo, unchanged
                                    Its build always sees /home/chris/git/rapla/
                                    (the canonical checkout), never agent worktrees.
```

### How git stores worktrees

A worktree is a second working tree backed by the **same** git object database. There is exactly one `.git/` directory — in the canonical checkout (`/home/chris/git/rapla/.git/`). Every other worktree has a `.git` **file** (a single line: `gitdir: /home/chris/git/rapla/.git/worktrees/agent-A`) that points back to per-worktree metadata stored under the canonical `.git/worktrees/<name>/`.

What this means concretely:

| Resource | Location | Shared between worktrees? |
|---|---|---|
| Object database (commits, trees, blobs) | `rapla/.git/objects/` | **Yes** — one copy. A `git fetch` in any worktree benefits all. |
| Refs (branches, tags) | `rapla/.git/refs/` | **Yes** — one shared namespace. |
| Per-worktree HEAD, index, reflog | `rapla/.git/worktrees/<name>/` | **No** — each worktree has its own checkout state. |
| Working tree (the actual files) | `rapla-agent-A/...` | **No** — own copy, own edits. |
| `target/` (Maven output) | `rapla-agent-A/target/` | **No** — isolated. This is the whole point. |
| `~/.m2/repository` (Maven local repo) | `~/.m2/` | **Yes** — one per-user copy, with proper per-artifact locks. |

**Branch uniqueness rule:** a branch can be checked out in **only one worktree at a time.** Two agents cannot both check out `master`. Each agent works on its own branch (`agent-A/feature-foo`, `agent-B/bugfix-bar`).

### Setup and teardown

```bash
# create a worktree on a new branch
cd /home/chris/git/rapla
git worktree add ../rapla-agent-A -b agent-A/feature-foo

# create a worktree on an existing branch
git worktree add ../rapla-agent-B existing-branch-name

# list all worktrees
git worktree list

# remove a worktree when done (deletes the directory and metadata)
git worktree remove ../rapla-agent-A

# if a worktree directory was deleted manually, clean up the metadata
git worktree prune
```

### Operational notes

- **Disk cost:** each worktree is a full source checkout, but git objects are shared via the canonical `.git/`. Cheaper than `git clone`-ing the repo N times.
- **`mvn install` from a worktree** writes to the shared `~/.m2/repository`. If two worktrees install the same `groupId:artifactId:SNAPSHOT`, they overwrite each other. Use a worktree-specific qualifier (`-agent-A-SNAPSHOT`) if that becomes a problem.
- **dhbwrapla constraint:** `dhbwrapla-container/pom.xml` references `../../rapla` as a relative path. Keep `/home/chris/git/rapla/` as the canonical checkout. Agents working in `rapla-agent-A/` who also need to build dhbwrapla against their changes should `mvn install` from the worktree into `~/.m2/repository` and let dhbwrapla resolve from there.
- **IDE imports:** each worktree imports as a separate project. IntelliJ and VS Code handle this fine.
- **Never** run two `mvn` invocations against the same worktree at the same time. Sequential `mvn compile` then `mvn test` is fine; overlapping is not.
