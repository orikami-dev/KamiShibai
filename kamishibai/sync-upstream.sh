#!/usr/bin/env bash
# Brings the latest SmartTube (bug fixes, YouTube API updates, new features) into Kamishibai.
#
# Why this is safe: Kamishibai lives in its own files (smarttubetv/src/stmobile, smarttubetv/kamishibai.gradle,
# kamishibai/) and touches upstream files in a handful of lines (see kamishibai/UPSTREAM-TOUCHPOINTS.md).
# So the merge only conflicts if upstream rewrites one of those lines, and the check below tells you right away.
#
# Usage: kamishibai/sync-upstream.sh [--no-build]
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/yuliskov/SmartTube.git}"
UPSTREAM_BRANCH="${UPSTREAM_BRANCH:-master}"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"

git remote get-url upstream >/dev/null 2>&1 || git remote add upstream "$UPSTREAM_URL"

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "Commit or stash your changes first." >&2
  exit 1
fi

git fetch upstream "$UPSTREAM_BRANCH"

BEHIND="$(git rev-list --count "HEAD..upstream/$UPSTREAM_BRANCH")"
if [ "$BEHIND" = "0" ]; then
  echo "Already up to date with SmartTube."
  exit 0
fi

echo "SmartTube is $BEHIND commit(s) ahead. Merging into $BRANCH..."
if ! git merge --no-edit "upstream/$UPSTREAM_BRANCH"; then
  echo >&2
  echo "Conflict in:" >&2
  git diff --name-only --diff-filter=U >&2
  echo "See kamishibai/UPSTREAM-TOUCHPOINTS.md for how to resolve each touchpoint, then 'git commit'." >&2
  exit 2
fi

# MediaServiceCore (YouTube API logic) and SharedModules are git submodules: follow the commit upstream points to
git submodule sync --recursive
git submodule update --init --recursive

# Interfaces implemented by the touch UI must still match: the compile is the real test
if [ "${1:-}" != "--no-build" ]; then
  ./gradlew --no-daemon :smarttubetv:assembleStmobileDebug
fi

echo "Done. SmartTube changes are merged and Kamishibai still builds."
