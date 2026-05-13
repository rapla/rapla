---
name: coverage-report
description: Use when generating a JaCoCo coverage report — per-module HTML or the full-stack reactor aggregate. Covers the `-Pcoverage` profile, the rapla-app exclusion and merge step, and where the HTML lands. Don't reach for this during routine iteration — coverage is a release / audit concern, off by default because the JaCoCo agent costs ~10 s of forked-JVM overhead per module.
---

# JaCoCo coverage reports

Default `mvn test` skips coverage because the JaCoCo agent needs a forked
JVM and adds ~10 s per module. The `-Pcoverage` profile flips
`forkCount=0 → forkCount=1` and wires the agent.

## Commands

```bash
# Per-module reports only — fast, one HTML per module.
mvn -Pcoverage test

# Per-module + aggregate — recommended. Aggregate rolls up @SpringBootTest
# contributions back to rapla-core / rapla-server bytecode. Pass
# -Dtest.excludedGroups= so the e2e+db tests that actually exercise
# Spring + storage paths are included.
mvn -Pcoverage verify -Dtest.excludedGroups=
```

## Where the HTML lands

| Report | Path |
|---|---|
| Per-module | `<module>/target/site/jacoco/index.html` |
| Aggregate | `target/site/jacoco-aggregate/index.html` |

## What the aggregate covers and excludes

- **Excludes `rapla-app/target/classes`** from class-scanning. The JNLP
  `webclient/*.jar` set inside rapla-app crashes JaCoCo's bundle analyzer.
- **Folds in `rapla-app/target/jacoco.exec`** via a `merge` step. So the
  rapla-app `@SpringBootTest` runs DO attribute back to rapla-server and
  rapla-core bytecode in the aggregate — you get full-stack coverage of
  the controller → impl → entity chain without rapla-app showing up as
  a separate column.

## When to use

- Release prep — confirm the suite covers the surface that ships.
- Auditing a specific package's coverage after a refactor.
- Spot-checking which paths a new `@Tag("e2e")` test exercises.

## When NOT to use

- Routine iteration. Coverage doesn't tell you what to write next.
- Per-commit CI. The fork cost adds up; gate coverage behind an
  explicit `mvn -Pcoverage verify` job, not the default test lane.
