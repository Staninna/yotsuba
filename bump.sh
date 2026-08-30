#!/usr/bin/env bash
# Cut a release: bump both version fields, verify, commit, tag, push.
#
# The two version fields and the tag are one fact written in three places, and the
# release job fails the build if they disagree — so nothing here lets them drift.
#
#   ./bump.sh              next patch (1.1.2 -> 1.1.3)
#   ./bump.sh minor        next minor (1.1.2 -> 1.2.0)
#   ./bump.sh major        next major (1.1.2 -> 2.0.0)
#   ./bump.sh 2.0.0        that exact version
#   ./bump.sh --no-push    stop after the commit; you push and tag by hand
#   ./bump.sh --just-push  the bump is already committed; only push and tag
#   ./bump.sh --watch      follow the release run to the end
#   ./bump.sh --no-changelog  skip the Claude-written changelog
#
# The bump commit carries changelog/vX.Y.Z.md, written by Claude Code from the
# log and diff since the previous tag. The release workflow uses that file as
# the release body when it is there and falls back to GitHub's generated notes
# when it is not; CI itself never calls Claude.
#   ./bump.sh --help       this
set -euo pipefail

# The comment block above is the help text; there is only one copy of it.
SELF=$(readlink -f "$0")
usage() { sed -n '2,/^[^#]/ s/^# \?//p' "$SELF"; }

cd "$(dirname "$SELF")"

GRADLE=app/build.gradle.kts
APP=$(sed -n 's/^rootProject.name = "\(.*\)"/\1/p' settings.gradle.kts)
BRANCH=$(git rev-parse --abbrev-ref HEAD)
PUSH=yes
WATCH=no
BUMP=patch
CHANGELOG=yes

for arg in "$@"; do
  case "$arg" in
    -h|--help) usage; exit 0 ;;
    --no-push) PUSH=no ;;
    --just-push) BUMP=none ;;
    --watch) WATCH=yes ;;
    --no-changelog) CHANGELOG=no ;;
    patch|minor|major) BUMP=$arg ;;
    [0-9]*.[0-9]*.[0-9]*) BUMP=exact; VERSION=$arg ;;
    *) { echo "unknown argument: $arg"; echo; usage; } >&2; exit 2 ;;
  esac
done

die() { echo "bump: $*" >&2; exit 1; }

# Ask Claude Code for the changelog since the previous tag. The shape is fixed
# because the in-app updater parses it (core/update/ReleaseNotes.kt).
write_changelog() {
  local version=$1 out="changelog/v$1.md" prev prompt
  command -v claude >/dev/null || die "claude is not installed; use --no-changelog"
  prev=$(git describe --tags --abbrev=0 2>/dev/null || true)
  local range=${prev:+$prev..HEAD}
  echo "==> changelog for $version (${prev:-first release}..HEAD)"
  mkdir -p changelog
  prompt="Write the changelog for $APP $version from the commit log and diff below.

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
$(git log --no-merges --format='- %s' $range)

## Diff stat
$(git diff --stat $range -- . ':!app/src/test' ':!app/src/androidTest')

## Diff
$(git diff $range -- . ':!app/src/test' ':!app/src/androidTest' ':!*.png' ':!*.webp' | head -c 600000)"
  printf '%s\n' "$prompt" | claude -p --output-format text > "$out.tmp" || { rm -f "$out.tmp"; return 1; }
  # Strip a stray code fence and anything before the first heading.
  sed -i -e '/^```/d' -e '0,/^## /{/^## /!d}' "$out.tmp"
  grep -q '^## ' "$out.tmp" || { rm -f "$out.tmp"; echo "bump: changelog came back without headings" >&2; return 1; }
  if grep -E '^## ' "$out.tmp" | grep -vqE '^## (Added|Changed|Fixed|Removed)$'; then
    rm -f "$out.tmp"; echo "bump: changelog used a heading outside Added/Changed/Fixed/Removed" >&2; return 1
  fi
  mv "$out.tmp" "$out"
  echo "==> wrote $out"
  sed 's/^/    /' "$out"
}

[ -f "$GRADLE" ] || die "no $GRADLE — run this from the repo root"
if [ "$BUMP" = none ] && [ "$PUSH" = no ]; then die "--just-push and --no-push cancel out"; fi
if [ -n "$(git status --porcelain)" ]; then die "working tree is dirty; commit or stash first"; fi
[ "$BRANCH" = main ] || die "on '$BRANCH', not main"

CODE=$(sed -n 's/^ *versionCode = \([0-9]*\)/\1/p' "$GRADLE")
NAME=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' "$GRADLE")
[ -n "$CODE" ] && [ -n "$NAME" ] || die "could not read versionCode/versionName from $GRADLE"

IFS=. read -r MAJOR MINOR PATCH <<<"$NAME"
case "$BUMP" in
  patch) VERSION="$MAJOR.$MINOR.$((PATCH + 1))" ;;
  minor) VERSION="$MAJOR.$((MINOR + 1)).0" ;;
  major) VERSION="$((MAJOR + 1)).0.0" ;;
  none)  VERSION="$NAME" ;;
esac
TAG="v$VERSION"
NEXT_CODE=$((CODE + 1))

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then die "$TAG already exists"; fi
if git ls-remote --exit-code --tags origin "$TAG" >/dev/null 2>&1; then
  die "$TAG is already on the remote"
fi

if [ "$BUMP" = none ]; then
  # The bump is already a commit; this is the half that did not happen.
  echo "==> $APP $VERSION (code $CODE) already committed, pushing and tagging only"
else
  echo "==> $APP $NAME (code $CODE) -> $VERSION (code $NEXT_CODE)"

  # Both fields, one edit, so they can never move apart.
  sed -i "s/^\( *versionCode = \)$CODE\$/\1$NEXT_CODE/; s/^\( *versionName = \)\"$NAME\"\$/\1\"$VERSION\"/" "$GRADLE"
  grep -q "versionCode = $NEXT_CODE" "$GRADLE" || die "versionCode rewrite failed"
  grep -q "versionName = \"$VERSION\"" "$GRADLE" || die "versionName rewrite failed"

  # Three invocations on purpose: lint's Kotlin analysis crashes when it shares
  # a Gradle run with the androidTest compile.
  echo "==> tests, lint, androidTest compile"
  for task in testDebugUnitTest lintDebug compileDebugAndroidTestKotlin; do
    if ! ./gradlew "$task" -q; then
      git checkout -- "$GRADLE"
      die "$task failed; version left untouched"
    fi
  done

  if [ "$CHANGELOG" = yes ]; then
    write_changelog "$VERSION" || { git checkout -- "$GRADLE"; die "changelog failed; version left untouched"; }
    git add "changelog/$TAG.md"
  fi

  git commit -qam "Bump to $VERSION"
  echo "==> committed $(git rev-parse --short HEAD)"
fi

if [ "$PUSH" = no ]; then
  echo "==> not pushing. When you are ready:"
  echo "    git push origin main && git tag -a $TAG -m \"$APP $VERSION\" && git push origin $TAG"
  exit 0
fi

git push origin main
git tag -a "$TAG" -m "$APP $VERSION"
git push origin "$TAG"
echo "==> pushed $TAG"

if [ "$WATCH" = yes ] && command -v gh >/dev/null; then
  sleep 5
  gh run watch "$(gh run list --workflow=release.yml --limit 1 --json databaseId --jq '.[0].databaseId')" --exit-status
fi
