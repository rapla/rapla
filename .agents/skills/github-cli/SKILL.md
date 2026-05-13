---
name: github-cli
description: Use when the agent needs to interact with the rapla/rapla GitHub repository — viewing PRs and CI checks, creating PRs, browsing/triaging issues, looking at releases or workflow runs, or surfacing project metadata that `git` alone cannot show (stars, forks, labels, descriptions, topics, license). Skip when local-only `git` operations are enough (commit, branch, log, diff). The Claude Code system prompt already routes all GitHub work through `gh` via the Bash tool — this skill captures the rapla-specific patterns and known state of the upstream repo so an agent doesn't re-derive them every session.
---

# GitHub CLI (`gh`) — rapla-specific patterns

`gh` is installed and authenticated per `docs/development.md` "GitHub CLI". The Claude Code system prompt explicitly says to use `gh` via the Bash tool for all GitHub-related tasks — no GitHub MCP server needed for this project. See AGENTS.md §6 for the safety protocol around commits/pushes; this skill covers the gh usage on top.

## Repo facts (snapshot 2026-05-13)

| | |
|---|---|
| Repo | `rapla/rapla` (default branch `master`) |
| Stars / Forks | 70 / 44 |
| License | **Dual: AGPL-3.0 OR Apache-2.0** (README + `LICENSE_GPL3` + `LICENSE_APACHE2`). GitHub's auto-detector only reports Apache-2.0 — its metadata is incomplete; AGENTS.md's "AGPL/Apache2" is right. |
| Open issues | **141** (51 enhancement, 13 bug, 2 review, 1 each: help wanted / Rapla-1.7.8 / other) |
| Open PRs | 1 (Dependabot, no human PRs in flight) |
| Latest release | `2.0` (2026-01-04) — RC chain through 2025 |
| CI workflows | **Only `Dependabot Updates` is active.** No test/build workflow — the PRD 017 pyramid is purely local. Last run: 2026-01-22 (a Dependabot bump). |
| Top issue reporters | `GoogleCodeExporter` (126 — old SourceForge import), `kohlhaas` (22), `mgeuer` (6), then long tail |

Re-probe with `gh repo view rapla/rapla --json description,stargazerCount,forkCount,licenseInfo,latestRelease,issues,pullRequests` if the snapshot looks stale.

## When to use gh vs git

- **`git`** — anything local: branches, commits, diffs, log, stash, worktrees. Doesn't need network or auth.
- **`gh`** — anything that lives on github.com: PRs, issues, releases, CI runs, reviewers, labels, repo metadata. Same REST API the GitHub MCP server wraps; we use `gh` instead because Claude Code's system prompt is built around it and the MCP server only pays off for line-anchored review-comment loops we don't run.

## Common workflows

### Review the open PR set

```bash
gh pr list                           # short table
gh pr list --json number,title,author,createdAt,isDraft --jq '.[] | "#\(.number) \(.author.login)  \(.title)"'
gh pr view 601                       # full PR detail (title, body, comments, checks summary)
gh pr view 601 --web                 # open in browser (WSL: opens Windows browser)
gh pr diff 601                       # the diff itself
gh pr checks 601                     # CI status — for #601 today: "no checks reported"
```

### Create a PR (after pushing the branch)

```bash
gh pr create --base master --head <branch> --title "..." --body "..."
gh pr create --draft --base master --head <branch> --title "WIP: ..." --body "..."
```

The Claude Code system prompt has the canonical create-a-PR procedure (build status → diff → log → propose title/body → confirm → push → `gh pr create` with HEREDOC body). Don't shortcut it.

### Triage issues

```bash
gh issue list --limit 30                                # short table
gh issue list --label bug                               # 13 open bugs as of snapshot
gh issue list --label enhancement --limit 5             # browse the 51-enhancement backlog
gh issue list --search "swing in:title"                 # full-text search
gh issue view 604                                       # full issue detail incl. comments
gh issue view 604 --json title,body,comments --jq '.comments | length'
```

### Watch CI runs

```bash
gh run list --limit 10                  # only Dependabot runs today; will populate once a real workflow lands
gh run watch <run-id>                   # tail a running workflow
gh run view <run-id> --log-failed       # log of failed steps only
```

### Releases

```bash
gh release list --limit 10              # 2.0, 2.0-RC11, 2.0-RC9, 2.0-RC8, …
gh release view 2.0 --json publishedAt,tagName,body
```

### Repo metadata edits (gated — confirm with user before running)

These mutate the upstream repo. Always confirm before invoking:

```bash
gh repo edit rapla/rapla --description "Resource scheduling and event planning"   # fix "planing" typo
gh repo edit rapla/rapla --add-topic java,scheduling,event-management,spring-boot,angular,swing,oauth2
```

The current repo has **no topics set** and a description with the typo `planing` (should be `planning`) — both are one-line fixes worth queuing for the next session that does outward-facing housekeeping.

## Auth

Auth lives in `~/.config/gh/hosts.yml` after one-time `gh auth login`. Token scopes: `gist, read:org, repo, workflow` — covers everything in this skill. If a call fails with a 401/403:

```bash
gh auth status              # check token validity + scopes
gh auth refresh -s repo     # add missing scopes interactively
```

Setup details in `docs/development.md` (the bootstrap section's step 7).

## Pagination & rate limits

`gh` paginates automatically when you use `--paginate`, otherwise default page size is 30. For broad scans (e.g. all 141 open issues), use `gh api` directly:

```bash
gh api "/repos/rapla/rapla/issues?state=open&per_page=100" --paginate --jq 'length'
gh api rate_limit --jq '.rate'    # current API budget
```

Authenticated calls get 5000 req/h — plenty for any rapla workflow.

## Things gh exposes that git can't

A non-exhaustive list, in case you're tempted to scrape the website:

- Stars, forks, watchers, license metadata, repo description, topics, homepage URL
- All issues and PRs (open and closed) with labels, milestones, comments, reviews
- CI workflow definitions and run history
- Releases, tags-as-releases, release assets
- Branch protection rules (`gh api /repos/rapla/rapla/branches/master/protection`)
- Contributors, assignable users, mentionable users
- GitHub Actions secrets and variables (read scope only)
- Code search and issue search across the repo

If you find yourself parsing HTML from github.com, stop and use `gh api` instead.

## Footguns

- **`gh pr create` opens an editor by default** if you don't pass `--title` and `--body`. In an agent context that hangs the Bash call. Always pass both.
- **`gh repo clone` writes to CWD.** In an agent session, prefer `git clone` so the path is explicit.
- **`gh auth login` needs a TTY.** If running unattended, set `GITHUB_TOKEN` instead.
- **`gh pr merge`** mutates the upstream — never run without explicit user direction (AGENTS.md "actions visible to others" rule).
- **The `gh repo edit` mutations** above are safe but visible; same rule.
