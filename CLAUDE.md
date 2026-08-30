# Yotsuba

A 4chan browser for Android. Single module, Compose, Hilt, Room.

## House rules

This repo follows the shared Android app pattern. **Load the
`android-app-repo` skill** before touching signing, CI, releases, or the
in-app updater. It holds the invariants, the templates, and the gotchas
that already cost a day of debugging.

The parts that bite hardest if you don't know them:

- **`versionName` must equal the release tag minus the `v`.** The release
  workflow fails the build if they disagree, because the in-app updater
  compares them. Bump `versionCode` and `versionName` together, in the commit
  before the tag.
- **Release signing comes from environment variables.** The keystore lives at
  `~/.secrets/yotsuba/release.p12` and never in the repo. A local release build
  without those variables falls back to the debug key on purpose: only CI ships
  installable builds.
- **Debug builds install as `dev.stan.yotsuba.dev`**, labelled "Yotsuba dev",
  with their own storage. They sit beside the release build; they never replace
  it.
- **Never compile a credential into the app.** Anything in an APK is readable
  by anyone holding the APK. The update check is unauthenticated because the
  repo is public.

## Layout

```
core/     database, network, media, text, vault, design system
domain/   models and repository interfaces. Pure Kotlin; imports nothing from core
data/     repository implementations
feature/  one package per screen: ViewModel + composables
```

The `domain` boundary is deliberate: it is what lets the ViewModel tests run on
the JVM against fakes rather than needing a device.

## The vault

Saved media lives at `/sdcard/Yotsuba/<board>/<threadNo> - <subject>/` with a
`meta.json` sidecar per thread, on shared storage, outside the sandbox.

It survives uninstalling the app, and `MediaVaultRepository.rescan()` rebuilds
the entire index from those sidecars. Bookmarks, history and settings do **not**
survive an uninstall. Do not promise a user their data is safe across a
reinstall without checking which side of that line it falls on.

## Testing

```sh
./gradlew testDebugUnitTest          # JVM, no device
./gradlew lintDebug                  # lint errors are real crashes, not pedantry
./gradlew connectedDebugAndroidTest  # needs a device
```

Coverage is honestly recorded in `todo.md`. Read it before claiming a number.
