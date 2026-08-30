#!/usr/bin/env bash
# Every @Serializable class must still have its serializer after R8.
#
#   ./check-serializers.sh            # after ./gradlew :app:assembleRelease
#   ./check-serializers.sh <mapping.txt> <classes dir>
#
# The vault sidecars (meta.json, posts.json) are decoded by kotlinx.serialization, which
# finds each class's serializer by a name baked in at compile time. Debug never shrinks, so
# a serializer R8 drops is only ever missed on a release install, reading a user's saved
# thread as "absent". proguard-rules.pro promises the serializers survive; this compares
# that promise with what R8 actually kept, and needs no device.
# shellcheck disable=SC2016  # the $$ in class names is literal
set -euo pipefail

MAPPING=${1:-app/build/outputs/mapping/release/mapping.txt}
CLASSES=${2:-app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes}

die() { echo "check-serializers: $*" >&2; exit 1; }
[ -f "$MAPPING" ] || die "no $MAPPING; build release first"
[ -d "$CLASSES" ] || die "no $CLASSES; build release first"

# What the compiler generated, before R8.
compiled=$(find "$CLASSES" -name '*$$serializer.class' | sed 's|.*/classes/||; s|\.class$||; s|/|\.|g' | sort)
[ -n "$compiled" ] || die "found no serializer classes under $CLASSES; wrong directory?"
# A class that must be in the list, so a wrong directory cannot pass vacuously.
grep -qx 'dev.stan.yotsuba.core.vault.VaultThreadPosts$$serializer' <<<"$compiled" ||
  die "VaultThreadPosts serializer not among the compiled classes; wrong directory?"

# What R8 kept. Every class in the output is in the mapping, renamed or not.
kept=$(grep -o '^[^ ]*\$\$serializer' "$MAPPING" | sort)

missing=$(comm -23 <(echo "$compiled") <(echo "$kept"))
if [ -n "$missing" ]; then
  echo "check-serializers: R8 removed these serializers; a keep rule in proguard-rules.pro is broken:" >&2
  echo "$missing" >&2
  exit 1
fi
echo "check-serializers: all $(wc -l <<<"$compiled") serializers survived R8"
