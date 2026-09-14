#!/usr/bin/env bash
title="$1"
shopt -s globstar nullglob
files=(**/target/surefire-reports/TEST-*.xml)
read -r tests failures errors skipped < <(cat "${files[@]}" /dev/null | grep -o '<testsuite [^>]*>' | awk '
  { for (i = 1; i <= NF; i++) if ($i ~ /^(tests|failures|errors|skipped)="[0-9]+"/) { split($i, kv, "\""); s[substr($i, 1, index($i, "=") - 1)] += kv[2] } }
  END { printf "%d %d %d %d\n", s["tests"], s["failures"], s["errors"], s["skipped"] }')
bad=$((failures + errors))
echo "result=$tests tests, $bad failed, $skipped skipped" >> "$GITHUB_OUTPUT"
echo "bad=$bad" >> "$GITHUB_OUTPUT"
{
  echo "### $title: $tests run, $failures failures, $errors errors, $skipped skipped"
  if [ "$bad" -gt 0 ]; then
    echo
    grep -l -E '<(failure|error)[ >]' "${files[@]}" | sed -E 's#.*/TEST-(.*)\.xml#- \1#'
  fi
} >> "$GITHUB_STEP_SUMMARY"
