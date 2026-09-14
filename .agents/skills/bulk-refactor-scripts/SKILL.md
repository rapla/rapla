---
name: bulk-refactor-scripts
description: Use when planning a bulk-refactor script — cross-module rename, signature-regex sweep, diff-based recovery against master, or similar mechanical pass over many files. Encodes the scars from the 2026-05-09 Date → LocalDateTime migration where a few regex slips burned hours. Skip for single-file edits or small refactors that fit in a few Edit calls; this is for "I'm about to write a Python/sed script that touches 100+ files."
---

# Lessons from the Date → LocalDateTime migration (2026-05-09)

When you're about to write a script that mechanically rewrites Java sources
across the reactor, read this first. Each bullet is a scar — a specific
shape of bug that broke the migration and cost hours to recover from.

## Signature-regex pitfalls

- **Anchor `<rt>` to `\w` and require typed args (≥2 tokens per arg).**
  Otherwise call statements like `throwParseDateException(date);` get
  matched as method declarations and silently deleted.
- **Strip script must distinguish overloads by arg shape.** `DateTools.toDate`
  has four overloads; only `(LocalDateTime)` and `(LocalDate)` are
  conversions to strip. Skip if the arg has `.getTime()`,
  `MILLISECONDS_PER`, or a top-level comma.

## Diff-based recovery

- **Diff-based recovery against master is dangerous when the working tree
  has diverged.** `SequenceMatcher` `replace` opcodes interleave OLD-signature
  lines into the NEW body. Only restore inside `insert` opcodes, and only
  single call-statement lines.

## Type design constraints

- **No conversion wrappers in entity/facade/storage tier.** Once a
  `DateTools.toX(...)` is stripped, fix the cascade by flipping the
  surrounding type — never re-wrap to silence the error. Wrappers belong
  only at JDBC / Swing-widget / ical4j / wire-format boundaries.
- **No parallel-named methods** (`*AsLocalDateTime`, `set*LocalDateTime`,
  `ofLocalDateTime`). Flip the type at the master name; don't double the
  API surface.

## Iteration discipline

- **Compile after every script, not after a chain.** Time-box each fix to
  one error pattern; cascading three scripts blind leaves the tree
  unrecoverable by diff. If the first script compiles cleanly, ship that
  commit before running the next.

## When this applies

- Cross-cutting type migrations (`Date` → `LocalDateTime`, primitive →
  wrapper, raw collections → generic, etc.).
- Package renames touching imports across all modules.
- Annotation sweeps (e.g. "add `@Nullable` everywhere a method returns
  null in some branch").
- Anything that touches >50 files and is hard to undo by hand.

## When NOT to apply

- A single-file refactor. Use Edit / Read.
- A small refactor (~5-10 files). One review pass is enough; no script.
- Type-safe IDE refactorings (IntelliJ "Rename" / "Change Signature").
  Let the IDE do it — it handles overload disambiguation, call-site
  rewrites, and tests, all atomically. A script is the wrong tool when
  the IDE has the right one.
