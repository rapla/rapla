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
- Run `mvn compile` after changes to verify compilation.

### 5. Git
- Never commit unless explicitly asked.
- Never push unless explicitly asked.
