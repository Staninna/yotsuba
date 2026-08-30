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
# log and diff since the previous tag (see changelog.sh, which also backfills).
# The release workflow uses that file as the release body when it is there and
# falls back to GitHub's generated notes when it is not; CI never calls Claude.
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

# shellcheck source=changelog.sh
. "$(dirname "$SELF")/changelog.sh"

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
