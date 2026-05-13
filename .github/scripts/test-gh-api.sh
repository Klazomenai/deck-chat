#!/usr/bin/env bash
# Shell-level unit test for gh-api.sh — exercises the three documented
# return paths (200, 404, unparseable) against a stubbed `gh` function.
#
# Run locally:
#   bash .github/scripts/test-gh-api.sh
# Exits non-zero on any assertion failure so CI gates on it.
#
# Why this exists: the helper's HTTP-header parsing and body extraction
# are easy to break with a sed/awk regression. The smoke test runs in
# under 100 ms, has zero external dependencies (no network, no `gh`
# binary), and pins the three documented return paths so a future edit
# to gh-api.sh can't silently degrade them.

set -euo pipefail
THIS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Stub `gh` — selected via the endpoint argument passed by the helper's
# `gh api --include "$@"` invocation. The helper passes args verbatim, so
# inside the stub: $1=api, $2=--include, $3=<endpoint>.
gh() {
  case "${3:-}" in
    *tags/v200*)
      printf 'HTTP/2.0 200 OK\r\nContent-Type: application/json\r\n\r\n{"id":12345,"name":"v0.1.0-alpha.7"}\n'
      return 0
      ;;
    *tags/v404*)
      printf 'HTTP/2.0 404 Not Found\r\nContent-Type: application/json\r\n\r\n{"message":"Not Found"}\n'
      return 1
      ;;
    *broken*)
      # Simulate a transport failure: `gh` exits non-zero AND no HTTP/x.y
      # header is parseable. This is the helper's fail-closed path.
      printf 'connection refused\n' >&2
      return 1
      ;;
    *)
      printf 'unexpected test endpoint: %s\n' "${3:-}" >&2
      return 2
      ;;
  esac
}
export -f gh

# shellcheck source=.github/scripts/gh-api.sh
source "${THIS_DIR}/gh-api.sh"

failures=0
assert_eq() {
  local actual="$1" expected="$2" name="$3"
  if [ "$actual" = "$expected" ]; then
    printf 'ok: %s\n' "$name"
  else
    printf 'FAIL: %s: expected %q, got %q\n' "$name" "$expected" "$actual" >&2
    failures=$((failures + 1))
  fi
}

# --- Test 1: 200 OK -----------------------------------------------------------
# Status parsed; body extracted; helper rc=0.
rc=0
gh_api_with_status "repos/x/releases/tags/v200" || rc=$?
assert_eq "$GH_API_HTTP_STATUS" "200" "200: status parsed"
assert_eq "$GH_API_BODY" '{"id":12345,"name":"v0.1.0-alpha.7"}' "200: body extracted"
assert_eq "$rc" "0" "200: helper rc=0"

# --- Test 2: 404 Not Found ----------------------------------------------------
# Status parsed; body extracted; helper rc=0 (parseable status, even though
# `gh` itself exited 1 — that's the case-dispatch's job, not the helper's).
rc=0
gh_api_with_status "repos/x/releases/tags/v404" || rc=$?
assert_eq "$GH_API_HTTP_STATUS" "404" "404: status parsed"
assert_eq "$GH_API_BODY" '{"message":"Not Found"}' "404: body extracted"
assert_eq "$rc" "0" "404: helper rc=0 despite gh exit 1"

# --- Test 3: unparseable (no HTTP headers) -----------------------------------
# Fail-closed: status=unknown, helper rc=1, body left empty.
rc=0
gh_api_with_status "broken" || rc=$?
assert_eq "$GH_API_HTTP_STATUS" "unknown" "unparseable: status=unknown"
assert_eq "$rc" "1" "unparseable: helper rc=1"

# --- Summary -----------------------------------------------------------------
if [ "$failures" -gt 0 ]; then
  printf 'FAILED: %d assertion(s)\n' "$failures" >&2
  exit 1
fi
printf 'PASSED: all gh-api.sh assertions\n'
