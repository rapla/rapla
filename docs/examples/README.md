# Examples — not wired into the build

Files kept for reference. Nothing in this directory is executed by the repo's build,
tests, or hosting; copy and adapt.

| File | What it is | Status |
|---|---|---|
| [`plugin-hello/`](plugin-hello/README.md) | The smallest drop-in rapla plugin: one `@AutoConfiguration` + one `@RestController` + the autoconfig marker. Not in the reactor — copy, rename the package, build, drop the jar into `./plugins/`. | Maintained example (PRD 045 plugin model). |
| [`gitlab-ci.yml`](gitlab-ci.yml) | A GitLab CI pipeline from a 2025 student project: Maven build → Docker image into the GitLab registry → ssh deploy of the container to a host. | **Rapla 2 shape, not maintained.** Uses a Java 11 Maven image, the old `target/distribution/*.tar.gz` artefact and the volume path `/app/data`. Rapla 3 builds a Spring Boot fat JAR (`rapla-app/target/rapla-*.jar`, Java 21) and the Docker image keeps its data under `/opt/rapla/data` — see [`docs/deployment.md`](../deployment.md) § Docker. The repo is hosted on GitHub, where this file is inert; the planned CI is GitHub Actions per [PRD 034](../prd/034-ci-baseline-workflow.md). |

Moved here from the repo root on 2026-09-14 so the file cannot be mistaken for an active pipeline.
