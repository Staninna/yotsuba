#!/usr/bin/env bash
# Changelogs written by Claude Code, one file per release under changelog/.
#
# Sourced by bump.sh for the release being cut. Run on its own to fill in the
# past:
#
#   ./changelog.sh 2.2.0            write changelog/v2.2.0.md (tag..previous tag, or HEAD)
#   ./changelog.sh --backfill       write every changelog/<tag>.md that is missing
#   ./changelog.sh --publish        put each changelog/<tag>.md into its GitHub release body
#   ./changelog.sh --help           this
#
# The shape is fixed because the in-app updater parses it (core/update/ReleaseNotes.kt):
# `## Added / Changed / Fixed / Removed`, one bullet per line, an optional bold lead.
# Only this script and bump.sh call Claude; CI never does.

# Ask Claude Code for the changelog of one version.
#   write_changelog VERSION        range: previous tag .. vVERSION if that tag exists, else .. HEAD
write_changelog() {
  local version=$1 tag="v$1" out="changelog/v$1.md" prev head range prompt app
  command -v claude >/dev/null || { echo "changelog: claude is not installed" >&2; return 1; }
  app=$(sed -n 's/^rootProject.name = "\(.*\)"/\1/p' settings.gradle.kts)
  if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    head=$tag
    prev=$(git describe --tags --abbrev=0 "$tag^" 2>/dev/null || true)
  else
    head=HEAD
    prev=$(git describe --tags --abbrev=0 2>/dev/null || true)
  fi
  range=${prev:+$prev..}$head
  echo "==> changelog for $version (${prev:-first release}..$head)"
  mkdir -p changelog
  prompt="Write the changelog for $app $version from the commit log and diff below.

Output only Markdown in exactly this shape, nothing before or after it:

## Added
- **Short lead** — one sentence on what the user can now do
## Changed
- ...
## Fixed
- ...
## Removed
- ...

Rules: use only those four headings, in that order, and drop any heading with no
entries. One line per bullet, most important first, at most eight bullets per
section. Write for the person using the app, not the developer: name the screen
or setting, skip class names, refactors, test-only and CI-only work unless they
change what the user sees. No introduction, no version line, no sign-off.

## Commits
$(git log --no-merges --format='- %s' "$range")

## Diff stat
$(git diff --stat "$range" -- . ':!app/src/test' ':!app/src/androidTest')

## Diff
$(git diff "$range" -- . ':!app/src/test' ':!app/src/androidTest' ':!*.png' ':!*.webp' | head -c 600000)"
  printf '%s\n' "$prompt" | claude -p --output-format text > "$out.tmp" || { rm -f "$out.tmp"; return 1; }
  # Strip a stray code fence and anything before the first heading.
  sed -i -e '/^```/d' -e '0,/^## /{/^## /!d}' "$out.tmp"
  grep -q '^## ' "$out.tmp" || { rm -f "$out.tmp"; echo "changelog: came back without headings" >&2; return 1; }
  if grep -E '^## ' "$out.tmp" | grep -vqE '^## (Added|Changed|Fixed|Removed)$'; then
    rm -f "$out.tmp"; echo "changelog: used a heading outside Added/Changed/Fixed/Removed" >&2; return 1
  fi
  mv "$out.tmp" "$out"
  echo "==> wrote $out"
  sed 's/^/    /' "$out"
}

# Every changelog/<tag>.md that is missing, oldest tag first.
backfill_changelogs() {
  local tag
  for tag in $(git tag --list 'v*' | sort -V); do
    [ -f "changelog/$tag.md" ] && continue
    write_changelog "${tag#v}" || return 1
  done
}

# Each changelog/<tag>.md becomes the body of its GitHub release, when it differs.
publish_changelogs() {
  local f tag
  command -v gh >/dev/null || { echo "changelog: gh is not installed" >&2; return 1; }
  for f in changelog/v*.md; do
    tag=$(basename "$f" .md)
    if ! gh release view "$tag" >/dev/null 2>&1; then echo "==> $tag has no release, skipped"; continue; fi
    if [ "$(gh release view "$tag" --json body --jq .body)" = "$(cat "$f")" ]; then continue; fi
    gh release edit "$tag" --notes-file "$f" >/dev/null && echo "==> published $tag"
  done
}

# Sourced by bump.sh: stop here.
[ "${BASH_SOURCE[0]}" = "$0" ] || return 0

set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"
case "${1:-}" in
  -h|--help|"") sed -n '2,/^[^#]/ s/^# \?//p' "$0" ;;
  --backfill) backfill_changelogs ;;
  --publish) publish_changelogs ;;
  [0-9]*.[0-9]*.[0-9]*) write_changelog "$1" ;;
  *) echo "unknown argument: $1" >&2; exit 2 ;;
esac
