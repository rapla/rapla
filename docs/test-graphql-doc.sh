#!/usr/bin/env bash
# test-graphql-doc.sh — execute every fenced GraphQL block in graphql.md
# against a running rapla server, report which queries succeed / fail.
#
# Picks up only ```graphql blocks (executable queries / mutations).
# ```graphqls blocks are SDL definitions and are intentionally skipped.
#
# Usage:
#   RAPLA_USER=... RAPLA_PASS=... ./test-graphql-doc.sh [path/to/graphql.md]
#
# Defaults to ~/git/rapla/docs/graphql.md if no arg given. For the dhbw
# deployment-specific tour run against ~/git/dhbwrapla/docs/graphql.md.
#
# Env vars:
#   RAPLA_URL       — base URL (default http://localhost:8051)
#   RAPLA_USER      — username for OAuth password grant (required)
#   RAPLA_PASS      — password (required)
#   RAPLA_CLIENT    — OAuth client id (default rapla-client)
#   QUIET           — if set, only print failing query summaries
#
# Exit codes:
#   0 — every executable block returned data (no GraphQL errors)
#   1 — one or more blocks errored
#   2 — auth / network failure (couldn't obtain token, can't reach server)
#
# CI hook intent: catch doc drift. When the schema changes (DT key rename,
# attribute removed, enum value renamed, type-shape refactor), queries in
# the docs go stale silently — readers copy-paste broken queries. This
# script makes drift visible against a real running server.
#
# NOT a CI gate for the build itself. The dhbw-specific dataset isn't
# present in CI; run this script locally against a live dhbw deploy when
# editing dhbwrapla/docs/graphql.md, and against any rapla deployment
# when editing the generic doc.

set -euo pipefail

DOC=${1:-${HOME}/git/rapla/docs/graphql.md}
RAPLA_URL=${RAPLA_URL:-http://localhost:8051}
RAPLA_CLIENT=${RAPLA_CLIENT:-rapla-client}
QUIET=${QUIET:-}

if [[ ! -f "$DOC" ]]; then
  echo "ERROR: doc not found: $DOC" >&2
  exit 2
fi
if [[ -z "${RAPLA_USER:-}" || -z "${RAPLA_PASS:-}" ]]; then
  echo "ERROR: set RAPLA_USER and RAPLA_PASS env vars" >&2
  exit 2
fi

# Obtain a JWT via the OAuth password grant.
TOK=$(curl -s -X POST "${RAPLA_URL}/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "client_id=${RAPLA_CLIENT}" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${RAPLA_USER}" \
  --data-urlencode "password=${RAPLA_PASS}" \
  --data-urlencode "scope=read write" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token') or '', end='')")
if [[ -z "$TOK" ]]; then
  echo "ERROR: token request failed against ${RAPLA_URL}/oauth2/token" >&2
  exit 2
fi

# Iterate executable blocks (```graphql, NOT ```graphqls), POST each, classify
# the response.
python3 - "$DOC" "$TOK" "$RAPLA_URL" "$QUIET" <<'PY'
import json, re, subprocess, sys

doc_path, tok, base, quiet = sys.argv[1:5]

with open(doc_path) as f:
    text = f.read()

# Match ```graphql followed by newline (NOT ```graphqls). Negative lookahead
# on `s` prevents ```graphqls blocks from being picked up.
blocks = list(re.finditer(r'```graphql(?!s)\n(.*?)\n```', text, re.DOTALL))

n_ok = n_err = n_expected_err = 0
errors = []

VAR_RE = re.compile(r'^\s*#\s*@variables:\s*(\{.*\})\s*$', re.MULTILINE)
EXPECT_RE = re.compile(r'^\s*#\s*@expected:\s*(\S.*?)\s*$', re.MULTILINE)

for i, m in enumerate(blocks, 1):
    q = m.group(1)
    line_no = text[:m.start()].count('\n') + 2
    # @variables: { ... } leading-line annotation — sent in HTTP body as
    # GraphQL `variables`. Lets parameterized queries execute in CI.
    payload = {"query": q}
    vm = VAR_RE.search(q)
    if vm:
        try:
            payload["variables"] = json.loads(vm.group(1))
        except Exception as e:
            print(f"Q{i} (line {line_no}): bad @variables JSON: {e}", file=sys.stderr)
    # @expected: validation-error / §5d-pending / etc. — marks an error
    # as documented rather than a test failure.
    em = EXPECT_RE.search(q)
    expected_label = em.group(1) if em else None
    body = json.dumps(payload)
    try:
        r = subprocess.run(
            ['curl', '-s', '-X', 'POST',
             f'{base}/api/graphql',
             '-H', f'Authorization: Bearer {tok}',
             '-H', 'Content-Type: application/json',
             '-d', body],
            capture_output=True, text=True, timeout=60
        )
        resp = json.loads(r.stdout) if r.stdout else {}
    except Exception as e:
        resp = {"errors": [{"message": f"client exception: {e}"}]}

    errs = resp.get('errors') or []
    if errs and expected_label:
        # Documented error — counted but doesn't fail the run.
        n_expected_err += 1
        if not quiet:
            print(f"Q{i} EXP {expected_label}  (line {line_no}): {q[:60].strip()!r}")
    elif errs:
        n_err += 1
        msgs = [e.get('message', '')[:200] for e in errs[:3]]
        errors.append((i, line_no, q[:80].strip(), msgs))
    else:
        n_ok += 1
        if not quiet:
            print(f"Q{i} OK  (line {line_no}): {q[:60].strip()!r}")

for i, line_no, snip, msgs in errors:
    print(f"\nQ{i} ERR (line {line_no}): {snip!r}")
    for msg in msgs:
        print(f"  ! {msg}")

print(f"\n=== {n_ok} OK, {n_expected_err} EXP (documented errors), {n_err} ERR (unexpected, of {len(blocks)} executable blocks in {doc_path}) ===")
sys.exit(0 if n_err == 0 else 1)
PY
