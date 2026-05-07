---
name: git-worktrees
description: Use when the user wants to work on a branch in parallel with another agent, run two long-lived dev servers at once, or otherwise needs a sibling checkout that doesn't fight the main one for `target/` or ports. Skip on the canonical `master`/main branch when no other agent is active.
---

# Parallel work in git worktrees

When multiple agents (or developers in multiple panes) work on this repo in parallel, **each agent must run in its own git worktree.** Do NOT run concurrent `mvn` invocations against the same checkout — Maven holds no project-level lock and will silently corrupt `target/classes/` and `target/*.jar` when two builds race. A worktree gives each agent its own working tree and its own `target/`, so concurrent builds never touch the same files.

## Decide first: do you actually need a worktree?

You need one if any of these are true:
- another agent is already running in `/home/chris/git/rapla/`
- you've been told to work in parallel
- you want a long-running dev server (Spring Boot, Angular) at the same time another instance is up
- you're going to make commits on a feature branch that shouldn't disturb whatever the main checkout has open

You don't need one for: read-only inspection, single-shot compile checks, anything that finishes before another agent could start.

## Protocol — what to actually do

**Before starting work, check where you are:**

```bash
pwd                          # expect /home/chris/git/rapla*  or similar
git rev-parse --show-toplevel
git worktree list            # shows all existing worktrees and their branches
```

**If you need a worktree, create one from the canonical checkout:**

```bash
cd /home/chris/git/rapla
git worktree add ../rapla-<task-slug> -b <task-slug>
cd ../rapla-<task-slug>
```

Naming convention for `<task-slug>`: short, hyphenated, descriptive (e.g. `spring-boot-phase4`, `worktree-docs`, `prd-005-multimodule`). Avoid bare `agent-A` / `agent-B` — names should describe the work, not the actor.

**While working in the worktree:**
- All `mvn` commands run inside the worktree directory — they use the worktree's own `target/`.
- Commits land on the worktree's own branch; they don't affect any other worktree.
- `~/.m2/repository` is shared with every other worktree, so a `mvn install` here is visible to a build elsewhere. If you must `mvn install` a `SNAPSHOT`, qualify the version (`2.1-<task-slug>-SNAPSHOT`) so you don't overwrite another worktree's snapshot.
- Never start a second `mvn` while one is already running in the same worktree.

**When the task is done:**

```bash
cd /home/chris/git/rapla
git worktree remove ../rapla-<task-slug>      # deletes the directory + metadata
# the branch itself is preserved; delete it separately if you don't need it:
git branch -d <task-slug>                     # only if merged
```

## Hard rules
- Never run two `mvn` invocations against the same worktree at the same time — Maven has no project lock and will silently corrupt `target/`.
- Never check out the same branch in two worktrees — git refuses, and trying to force it via `git switch -C` corrupts the index.
- Never edit files in `/home/chris/git/rapla/` while another agent is also working there. If in doubt, make a worktree.
- Never delete a worktree directory with `rm -rf`. Use `git worktree remove` so the metadata under `rapla/.git/worktrees/` stays consistent. If you already did `rm -rf`, run `git worktree prune` to clean up.

## Port allocation for long-running servers

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

## Folder layout (sibling worktrees)

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

## How git stores worktrees

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

## Setup and teardown reference

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

## Operational notes

- **Disk cost:** each worktree is a full source checkout, but git objects are shared via the canonical `.git/`. Cheaper than `git clone`-ing the repo N times.
- **`mvn install` from a worktree** writes to the shared `~/.m2/repository`. If two worktrees install the same `groupId:artifactId:SNAPSHOT`, they overwrite each other. Use a worktree-specific qualifier (`-agent-A-SNAPSHOT`) if that becomes a problem.
- **dhbwrapla constraint:** `dhbwrapla-container/pom.xml` references `../../rapla` as a relative path. Keep `/home/chris/git/rapla/` as the canonical checkout. Agents working in `rapla-agent-A/` who also need to build dhbwrapla against their changes should `mvn install` from the worktree into `~/.m2/repository` and let dhbwrapla resolve from there.
- **IDE imports:** each worktree imports as a separate project. IntelliJ and VS Code handle this fine.
- **Never** run two `mvn` invocations against the same worktree at the same time. Sequential `mvn compile` then `mvn test` is fine; overlapping is not.
