#!/usr/bin/env bash
# Changelogs written by Claude Code, one file per release under changelog/.
#
# Sourced by bump.sh for the release being cut. Run on its own to fill in the
# past:
#
#   ./changelog.sh 2.2.0            write changelog/v2.2.0.md (tag..previous tag, or HEAD)
#   ./changelog.sh --backfill       write every changelog/<tag>.md that is missing
#   ./changelog.sh --publish        put each changelog/<tag>.md into its GitHub release body
#   ./changelog.sh --check          lint every changelog/<tag>.md against the shape and the unslop rules
#   ./changelog.sh --help           this
#
# The shape is fixed because the in-app updater parses it (core/update/ReleaseNotes.kt):
# `## Added / Changed / Fixed / Removed`, one bullet per line, an optional bold lead
# ending in a period. Every changelog is written under .claude/skills/unslop/SKILL.md,
# an exact copy of the unslop skill kept in the repo so the result does not depend on
# whatever skills the person cutting the release has installed; the prompt carries it in
# full, and --check enforces the parts a grep can. The model is claude-fable-5 unless
# CHANGELOG_MODEL says otherwise. Only this script and bump.sh call Claude; CI never does.

# Ask Claude Code for the changelog of one version.
#   write_changelog VERSION        range: previous tag .. vVERSION if that tag exists, else .. HEAD
write_changelog() {
  local version=$1 tag="v$1" out="changelog/v$1.md" prev head prompt app attempt
  local skill=.claude/skills/unslop/SKILL.md model=${CHANGELOG_MODEL:-claude-fable-5}
  command -v claude >/dev/null || { echo "changelog: claude is not installed" >&2; return 1; }
  [ -f "$skill" ] || { echo "changelog: $skill is missing; every changelog is written under it" >&2; return 1; }
  app=$(sed -n 's/^rootProject.name = "\(.*\)"/\1/p' settings.gradle.kts)
  if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    head=$tag
    prev=$(git describe --tags --abbrev=0 "$tag^" 2>/dev/null || true)
  else
    head=HEAD
    prev=$(git describe --tags --abbrev=0 2>/dev/null || true)
  fi
  # No previous tag: the log is everything up to the tag, the diff is against the
  # empty tree (a bare `git diff <tag>` would compare with the working tree).
  local log_range=${prev:+$prev..}$head
  local diff_range="${prev:-4b825dc642cb6eb9a060e54bf8d69288fbee4904} $head"
  echo "==> changelog for $version (${prev:-first release}..$head)"
  mkdir -p changelog
  prompt="Write the changelog for $app $version from the commit log and diff below.

Output only Markdown in exactly this shape, nothing before or after it:

## Added
- **Short lead.** One sentence on what the user can now do.
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

Apply the writing rules below to every bullet. In particular: no em dashes or en
dashes anywhere, no curly quotes, and the bold lead ends with a period. The lead
is a short noun phrase; nothing else separates it from the sentence. Say what the
user can now do or what stopped going wrong, in the words the app uses on screen.
A release with nothing user-visible says so in one bullet instead of dressing up
a version bump.

## Writing rules
$(cat "$skill")

## Commits
$(git log --no-merges --format='- %s' "$log_range")

## Diff stat
$(git diff --stat $diff_range -- . ':!app/src/test' ':!app/src/androidTest')

## Diff
$(git diff $diff_range -- . ':!app/src/test' ':!app/src/androidTest' ':!*.png' ':!*.webp' | head -c 600000 || true)"
  # Three tries: the model sometimes ignores a rule, and the checks below are cheap.
  for attempt in 1 2 3; do
    printf '%s\n' "$prompt" | claude -p --model "$model" --output-format text > "$out.tmp" || { rm -f "$out.tmp"; return 1; }
    # Strip a stray code fence and anything before the first heading.
    sed -i -e '/^```/d' -e '0,/^## /{/^## /!d}' "$out.tmp"
    if changelog_ok "$out.tmp"; then break; fi
    [ "$attempt" = 3 ] && { rm -f "$out.tmp"; return 1; }
    echo "==> attempt $attempt rejected, asking again"
  done
  mv "$out.tmp" "$out"
  echo "==> wrote $out"
  sed 's/^/    /' "$out"
}

# The shape the updater parses, plus the unslop rules a grep can enforce.
#   changelog_ok FILE
changelog_ok() {
  local f=$1
  grep -q '^## ' "$f" || { echo "changelog: came back without headings" >&2; return 1; }
  if grep -E '^## ' "$f" | grep -vqE '^## (Added|Changed|Fixed|Removed)$'; then
    echo "changelog: used a heading outside Added/Changed/Fixed/Removed" >&2; return 1
  fi
  if grep -nP '[\x{2014}\x{2013}\x{201C}\x{201D}\x{2018}\x{2019}]' "$f"; then
    echo "changelog: em dash, en dash or curly quote (see .claude/skills/unslop/SKILL.md)" >&2; return 1
  fi
  if grep -nE '^- \*\*[^*]*[^.*]\*\*' "$f"; then
    echo "changelog: a bold lead must end with a period" >&2; return 1
  fi
  return 0
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
  --check) rc=0; for f in changelog/v*.md; do changelog_ok "$f" || { echo "    in $f" >&2; rc=1; }; done; exit $rc ;;
  [0-9]*.[0-9]*.[0-9]*) write_changelog "$1" ;;
  *) echo "unknown argument: $1" >&2; exit 2 ;;
esac
