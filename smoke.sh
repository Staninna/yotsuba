#!/usr/bin/env bash
# Install an APK on the connected device, launch it, and fail if it dies.
#
#   ./smoke.sh path/to/app.apk [package]
#
# This is the release job's answer to 1.1.1, which crashed before its first frame
# because R8 renamed a class the navigation graph looks up by name. Debug is not
# minified, so nothing short of launching the release build can catch that class of
# crash. The bar is deliberately low: the process that `am start` created is still
# the process running SMOKE_WAIT seconds later, and logcat holds no fatal for it.
#
# Works against an emulator in CI or a phone over adb; there is no CI-only logic here.
set -euo pipefail

APK=${1:?usage: smoke.sh <apk> [package]}
PKG=${2:-dev.stan.yotsuba}
ACTIVITY=${SMOKE_ACTIVITY:-dev.stan.yotsuba.MainActivity}
WAIT=${SMOKE_WAIT:-10}

die() { echo "smoke: $*" >&2; exit 1; }

adb wait-for-device
# shellcheck disable=SC2016  # runs on the device, so the expansion must not happen here
adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/$ACTIVITY" | tee "${TMPDIR:-/tmp}/smoke-start.txt"
grep -q '^Status: ok' "${TMPDIR:-/tmp}/smoke-start.txt" || die "am start did not report ok"

pid=$(adb shell pidof "$PKG" | tr -d '\r') || true
[ -n "$pid" ] || die "$PKG has no process right after launch"
echo "smoke: $PKG is pid $pid, watching for ${WAIT}s"
sleep "$WAIT"

later=$(adb shell pidof "$PKG" | tr -d '\r') || true
fatal=$(adb logcat -d | grep -E "FATAL EXCEPTION|Process $PKG .*has died|Force finishing activity $PKG" || true)

if [ "$later" != "$pid" ] || [ -n "$fatal" ]; then
  echo "smoke: $PKG crashed (pid before: $pid, after: '${later:-none}')" >&2
  echo "--- logcat, errors and above ---" >&2
  adb logcat -d '*:E' | tail -200 >&2
  exit 1
fi
echo "smoke: $PKG survived ${WAIT}s"
