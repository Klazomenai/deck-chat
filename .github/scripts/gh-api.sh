#!/usr/bin/env bash
# Shared helper for "ask GitHub about a release/tag/asset state, get back the
# HTTP status + response body, and let the caller dispatch on it".
#
# Designed for release-workflow scripts where 200/404 mean different things at
# different call sites (e.g. `build-and-attach-apk.yml :: create-or-update`
# treats 404 as "create the Release"; the integrity assertion right after it
# treats 404 as a hard failure). The case-statement therefore stays in the
# caller — this helper only owns the gh-invocation + HTTP-status parsing +
# body extraction, never the per-site decision.
#
# Usage:
#   source "$GITHUB_WORKSPACE/.github/scripts/gh-api.sh"
#   gh_api_with_status "repos/${REPO}/releases/tags/${TAG}" || true
#   case "$GH_API_HTTP_STATUS" in
#     200) release_json="$GH_API_BODY"; ... ;;
#     404) ...                                ;;
#     *)   echo "::error::Unexpected HTTP $GH_API_HTTP_STATUS"
#          printf '%s\n' "$GH_API_RESPONSE"
#          exit 1                              ;;
#   esac
#
# Sets (in caller scope, intentionally — these are the helper's output):
#   GH_API_RESPONSE     — full response from `gh api --include`, headers + body
#   GH_API_BODY         — response body with HTTP headers stripped
#   GH_API_HTTP_STATUS  — parsed status code ("200", "404", ...) or "unknown"
#                         when no `HTTP/x.y NNN` header could be parsed.
#
# Returns:
#   0 — status header parsed (regardless of which status). Caller dispatches.
#   1 — gh failed AND no status header was parseable. GH_API_HTTP_STATUS is
#       set to "unknown" so callers that wrap with `|| true` and dispatch
#       on a `*)` case still get a sane fall-through value.
#
# Notes:
#   - On HTTP redirects, the FIRST `HTTP/x.y` header wins. `gh api` typically
#     follows redirects internally and emits only the final response, so this
#     matches the documented gh behaviour for our endpoints (releases/tags).
#   - The body separator is the first blank line, CR-tolerant for HTTP/1.x
#     responses that still carry CRLF terminators.

set -euo pipefail

gh_api_with_status() {
  # `|| true`-style capture: a non-zero exit from gh on a 4xx/5xx surface is
  # the caller's `case` to dispatch, not ours. We only fail-close if the
  # status header itself cannot be parsed below.
  GH_API_RESPONSE=""
  GH_API_BODY=""
  GH_API_HTTP_STATUS=""

  GH_API_RESPONSE="$(gh api --include "$@" 2>&1)" || true

  GH_API_HTTP_STATUS="$(printf '%s\n' "$GH_API_RESPONSE" \
    | awk '/^HTTP\/[0-9.]+ [0-9]+/ { print $2; exit }')"

  if [ -z "$GH_API_HTTP_STATUS" ]; then
    GH_API_HTTP_STATUS="unknown"
    return 1
  fi

  # shellcheck disable=SC2034
  # GH_API_BODY is part of the helper's output contract — consumed by callers.
  GH_API_BODY="$(printf '%s\n' "$GH_API_RESPONSE" \
    | awk 'in_body { print; next } /^\r?$/ { in_body = 1 }')"

  return 0
}
