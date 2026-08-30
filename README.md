# Yotsuba

**A 4chan browser for Android, with a media vault that outlives the app.**

Boards, catalogs and threads in Compose, with the things a phone client
needs: quote previews without losing your place, spoilers that stay hidden until
you ask, a reading position that survives leaving a thread, and saved media
filed into a directory structure you can still make sense of from a file
manager.

```text
Boards ──▶ Catalog ──▶ Thread ──▶ Media viewer
                          │            │
                          │            └──▶ Vault  /sdcard/Yotsuba/<board>/<thread>/
                          └──▶ Bookmarks, History, Hidden threads
```

## The vault

Saved media does not live in the app's sandbox. It goes to
`/sdcard/Yotsuba/<board>/<threadNo> - <subject>/`, one directory per thread, each
with a `meta.json` sidecar describing what's in it.

That has a specific consequence worth knowing: **uninstalling the app does not
delete your media, and a fresh install can rebuild its whole index from disk**. The
rescan button in the Vault tab walks the sidecars and repopulates the
database. Bookmarks, history and settings do not survive an uninstall; the vault
does.

## Features

- **Boards** with favourites, hidden boards, and a bulk "hide NSFW" action
- **Catalog** in three densities, with per-thread hiding
- **Threads** with quote previews, spoiler handling, in-thread search, and a
  read position that returns you where you left off
- **Media viewer** shared by thread and vault: zoom, video with audio where the
  board allows it, picture-in-picture, and shuffle across a whole scope
- **Bookmarks** that track new replies since you last looked
- **History** with configurable retention
- **In-app updates** from GitHub releases, see below

## Building

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

Requirements: JDK 17+, Android SDK with compileSdk 37. Runs on Android 8.0
(minSdk 26) and up.

Debug builds install as `dev.stan.yotsuba.dev` and are labelled **Yotsuba dev**,
so a development build sits beside the release one instead of replacing it, with
its own data.

The instrumented suite needs a device:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Two checks exist only for the minified build, and the release workflow runs
both before it publishes anything. `check-serializers.sh` compares the
serializers the compiler generated with the ones R8 kept, so a broken keep rule
cannot silently make saved threads unreadable. `smoke.sh <apk>` installs an
APK on the connected device, launches it and fails if it dies within ten
seconds; CI runs it on an emulator against the APK it is about to publish.

## Cutting a release

Bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then
push a matching `v*` tag:

```sh
git tag -a v1.2.3 -m "Yotsuba 1.2.3" && git push origin v1.2.3
```

CI runs the tests, builds an APK signed with the release keystore, verifies it
is not debug-signed, and attaches it to a generated GitHub release.

`versionName` **must** match the tag. The release workflow fails the build
otherwise, because the in-app updater compares the two and a stale `versionName`
makes the app offer an update it already is.

## Updating

Settings → Updates → **Check for updates** asks GitHub for the newest release
and, when it beats the running build, offers to download and install it. No
account, no token, no telemetry: the repo is public and the check is an
unauthenticated GET.

On Android 12+ the install is silent where the OS permits it. Some vendor
builds, HyperOS among them, refuse that and show the system installer dialog
instead; the app falls back to it automatically.

## Architecture

`core/` (database, network, media, text, vault, design system), `domain/`
(models and repository interfaces), `data/` (repository implementations), and
`feature/` (one package per screen, each a ViewModel plus composables). Hilt
wires it; Room and DataStore persist it; a single OkHttp client is shared with
Coil and Media3 so there is one connection pool and one set of interceptors.

Repository interfaces live in `domain` and know nothing about Room or Retrofit,
which is why the ViewModel tests run on the JVM against fakes.

## Disclaimer

Yotsuba is an independent hobby project, not affiliated with, endorsed by, or
sponsored by 4chan. It uses the public 4chan API. 4chan is a registered
trademark of 4chan LLC.

## License

MIT. See [LICENSE](LICENSE).
