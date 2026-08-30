# Yotsuba

A 4chan browser for Android, with a media vault that outlives the app.

Four tabs. Home shows the boards you starred, each as a swipeable catalog tab.
Boards is the full list, with favourites and hidden boards. Threads is your
bookmarks and history in one place. Vault is everything you saved, on shared
storage, browsable from a file manager as well as from the app. Settings hangs
off a gear icon on every tab.

```text
Home / Boards ──▶ Catalog ──▶ Thread ──▶ Media viewer
                                │            │
                                │            └──▶ Vault  /sdcard/Yotsuba/<board>/<thread>/
                                └──▶ Threads (bookmarks, history)
```

## The vault

Saved media does not live in the app's sandbox. It goes to
`/sdcard/Yotsuba/<board>/<threadNo> - <subject>/`, one directory per thread, each
with a `meta.json` sidecar describing what is in it.

Uninstalling the app does not delete your media, and a fresh install rebuilds
its whole index from disk. The rescan button in the Vault tab walks the sidecars
and repopulates the database. The backup file (bookmarks, hidden threads,
settings) is also written into the vault folder after every change, so those
come back too if you import it. History does not survive an uninstall.

Vault sync can, every few hours, copy the text of each bookmarked thread into
the vault so it stays readable after 4chan drops it. Once a thread dies you can
have it pruned down to the OP plus the replies around each saved file.

## What it does

Threads have quote previews, spoilers that stay hidden until tapped, in-thread
search, and a read position that returns you to where you left off. When 4chan
no longer has a thread, the app falls through to desuarchive, arch.b4k.co or
warosu for the boards they cover.

Bookmarked threads are polled in the background and can notify you of new
replies. A home screen widget lists them, unread first.

Filters hide or stub posts by regex or plain text, on subject, comment, name,
tripcode, flag, poster ID or filename, per board or everywhere.

The media viewer is shared by threads and the vault: zoom, video with audio
where the board allows it, picture-in-picture, shuffle across a whole scope,
and reverse image search through Google Lens, SauceNAO, IQDB, TinEye or
Yandex.

The vault has a duplicate finder (exact and perceptual hash), a stats page,
multi-select, trash, and a switch to keep the folder out of Gallery and Photos.

App lock asks for your phone's unlock (fingerprint, face, PIN or pattern) when
you open the app, after a delay you choose.

In-app updates come from GitHub releases, see below.

## Who receives your data

The app talks to 4chan for everything ordinary. Beyond that:

- GitHub (`api.github.com`), when you check for updates. An unauthenticated GET
  for the latest release; no account, no token.
- desuarchive.org, arch.b4k.co and warosu.org, when a thread is gone from 4chan
  and one of them archives that board.
- TinEye, Yandex, SauceNAO, IQDB and Google Lens, only when you pick one from
  the reverse search menu. For images that are still online the engine gets the
  4chan URL. For a file that only exists on your phone (an imported thread, a
  video frame) TinEye and Yandex take a direct upload; the other engines need a
  public URL, so the app first uploads the file to litterbox.catbox.moe (kept
  one hour) or, if that fails, 0x0.st, and asks you before doing so.

`MANAGE_EXTERNAL_STORAGE` is what lets the vault live on shared storage instead
of in the sandbox. Nothing else reads outside `/sdcard/Yotsuba`.

## Building

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

Requirements: JDK 17+, Android SDK with compileSdk 37. Runs on Android 8.0
(minSdk 26) and up.

Debug builds install as `dev.stan.yotsuba.dev` and are labelled "Yotsuba dev",
so a development build sits beside the release one instead of replacing it, with
its own data.

The instrumented suite needs a device:

```sh
./gradlew :app:connectedDebugAndroidTest
```

`bin/` holds the scripts for driving a phone over USB or Tailscale:
`phone-find` resolves an adb transport and says which one answered,
`phone-arm` puts adb on TCP from a cable, `phone-status` shows what is
installed, and `dev-push yotsuba` builds, installs, launches and fails if the
app dies.

Two checks exist only for the minified build. `check-serializers.sh` compares
the serializers the compiler generated with the ones R8 kept, so a broken keep
rule cannot silently make saved threads unreadable; CI runs it on every push
against a debug-signed release build. `smoke.sh <apk>` installs an APK,
launches it and fails if it dies within ten seconds; the release workflow runs
it on an emulator against the APK it is about to publish.

## Cutting a release

`./bump.sh` does it: bumps `versionCode` and `versionName` together, runs the
tests, lint and the instrumented-test compile, writes the changelog, commits,
pushes and tags. By hand it is the same steps ending in

```sh
git tag -a v1.2.3 -m "Yotsuba 1.2.3" && git push origin v1.2.3
```

The release workflow runs the tests and lint, builds an APK signed with the
release keystore, verifies it is not debug-signed, checks the serializers,
smoke-launches it on an emulator, and attaches it to a GitHub release.

`versionName` must match the tag. The workflow fails the build otherwise,
because the in-app updater compares the two and a stale `versionName` makes the
app offer an update it already is.

## Updating

Settings, Updates, "Check for updates" asks GitHub for the newest release and,
when it beats the running build, offers to download and install it.

On Android 12+ the install is silent where the OS permits it. Some vendor
builds, HyperOS among them, refuse that and show the system installer dialog
instead; the app falls back to it.

## Architecture

`core/` (database, network, media, text, vault, backup, dedup, lock, widget,
work, update, design system), `domain/` (models and repository interfaces),
`data/` (repository implementations), and `feature/` (one package per screen,
each a ViewModel plus composables). Hilt wires it; Room and DataStore persist
it; a single OkHttp client is shared with Coil and Media3 so there is one
connection pool and one set of interceptors.

Repository interfaces live in `domain` and know nothing about Room or Retrofit,
which is why the ViewModel tests run on the JVM against fakes. See
[CONTEXT-MAP.md](CONTEXT-MAP.md) and [docs/adr/](docs/adr/).

## Disclaimer

Yotsuba is an independent hobby project, not affiliated with, endorsed by, or
sponsored by 4chan. It uses the public 4chan API. 4chan is a registered
trademark of 4chan LLC.

## License

MIT. See [LICENSE](LICENSE).
