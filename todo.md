# Yotsuba TODO

Open work first; everything finished lives under `# Done` at the bottom.
## 0. Testing

- [ ] 100% unit test coverage. Not met; last measured 2026-08-29 at 22.8% line / 20.1% instruction / 21.8% branch (JaCoCo via `./gradlew :app:createDebugUnitTestCoverageReport`). 391 JVM tests pass as of 2026-08-30 (up from 269); the percentage has not been re-measured since the overhaul and is due. One unit-test run failed once and passed on three reruns without a code change, so there is a flaky test somewhere in the suite. Seen again 2026-08-30 in `LoadableFlowTest` and `ApiResultTest` (leaked `Dispatchers.Main`, "uncaught exceptions before the test started"); both pass alone, so some earlier test leaks its dispatcher. Near-100% where JVM-testable: domain/model 99.4%, core/text 98.5%, core/vault 97.6%, core/util 97.1%, network DTOs 96.1%. `feature/thread` 55.9%, `data/repository` 43.9%. The remainder is mostly Compose UI (designsystem, screens, navigation) which needs instrumented coverage, plus Robolectric-hosted tests (Catalog/Settings VMs, MediaByteSource, SettingsDataStore) whose sandbox classloader bypasses JaCoCo, so they record 0% despite passing. `core/datastore` reads 0% for that reason alone. `feature/media` is 4.7%: the viewer VMs depend on Context/ExoPlayer, though `ViewerBehaviour` and `PostGraph` are now pure and fully covered
- [ ] 100% e2e test coverage. 8 instrumented Compose tests in `app/src/androidTest/` cover every screen's primary flow (boards → catalog → thread → media viewer, bookmark add/remove, history, vault, settings toggle) with Hilt `@TestInstallIn` fake repositories, no network. They compile (`:app:compileDebugAndroidTestKotlin`) but need a device: `./gradlew :app:connectedDebugAndroidTest`. Attempted on hardware 2026-08-29 and did not run: the test APK failed to link against the installed app APK (`NoSuchMethodError: kotlinx.coroutines.BuildersKt.runBlockingK`), a stale-artifact mismatch rather than a test failure. Unresolved. Secondary flows (search, spoilers, PiP, downloads, hold-to-save, edge-seek) are uncovered, and the gesture work in particular cannot be trusted without a device

- [ ] Small test smells. `NetworkLayerTest` uses `runBlocking` and `File.createTempFile` instead of `TemporaryFolder`, and the rate-limiter test asserts wall-clock >= 400 ms. `HomeViewModelTest.kt:89` has a `delay(100)` inside a fake
- [ ] The numbers above are stale: there are 19 `@Test`s in 15 instrumented files (not 8) and roughly 580 JVM `@Test`s (not 391). Re-measure coverage and rewrite the first two items

## 1. Release safety

- [ ] Third-party actions (`softprops/action-gh-release@v3`, `reactivecircus/android-emulator-runner@v2`) are pinned to major tags with `contents: write`. Dependabot covers github-actions, so SHA pinning is a judgement call; note it either way
- [ ] `biometric = 1.1.0` is the 2021 stable with known quirks on newer OEM ROMs; check it against HyperOS

## 2. Bugs


## 3. Privacy and third parties

- [ ] Both direct routes use private browser XHR endpoints, not APIs (`ReverseSearchUpload.kt:43-47`: `tineye.com/api/v1/result_json/`, `yandex.com/images-apphost/image-download`). TinEye's ToS forbids automated access outside the paid API, and either breaks silently on a field rename, surfacing only as "Could not upload to X"

## 4. Code quality

- [ ] `ThreadViewModel` has about 60 public `on*` methods and a `Session` with about 20 fields: previews, ghosts, search, gallery, spoilers, claims, links, save, bookmarks, history. Internals are factored (`ThreadPoller`, `GhostResolver`, `threadRows`); the surface is the god-class symptom
- [ ] `VaultViewModel.selectedEntries`, `openBoard`, `openThread`, `scopeEntries` (286-305) are linear-scan getters read from composables
- [ ] `VaultStore.writeAtomically` (214-221) does not fsync before rename, so atomic against a crash, not a power loss. Fine for sidecars; say so in a comment

## 6. Feature ideas (vs Readchan)

### Archival (the killer read-only feature)

- [~] Full offline thread snapshots, **mostly done.** Saving media writes `posts.json` beside it with the saved post's transitive parents and replies, as parsed segments so greentext, quotelinks and spoilers survive. The vault's Sync button then walks every saved thread, fetches the live one and merges its **whole** comment section in. While the thread still exists, that is the only chance to take it. `MediaVaultRepository.savedThread()` rebuilds a `ThreadDetails` from the sidecar, and the media viewer falls back to it when the live fetch fails, so a 404'd thread stays readable. Sync is rate-limited to ~1 thread/second by `RateLimitInterceptor` and reports a `done / total` counter; a `RateLimited` response stops the pass rather than hammering. Bookmarked threads now snapshot without a save (`snapshotThread`, a row action, and `VaultSyncWorker` every 6 h with `Settings.snapshotWatchedThreads`), and a dead thread's sidecar can be pruned to the OP plus the conversations around saved files (`Settings.pruneDeadSidecars`, off by default; snapshot-only threads are never pruned). Still missing: thumbnails for unsaved posts, and the explorer does not list snapshot-only threads (no media rows), so they are reachable only through the thread screen's offline fallback. The thread screen itself opens the sidecar copy when live and archive both fail ("Offline copy from <date>")

### Filtering & comfort

- [~] Favourite boards are the Home tab (swipeable catalog pages, first tab, start destination). Re-ordering favourites and per-board accents are not done

## 7. Docs and repo hygiene

- [ ] The todo.md same-commit rule is honoured in outcome, not practice: 11 of the last 60 commits touch this file, and `7b71e9d` / `ec2a874` exist only to move finished items. Section numbering in the open half is 0, 5, 6, then unnumbered
- [ ] Scripts are GNU-only (`readlink -f`, `sed -i`, `sed '0,/re/'` in `changelog.sh:98`, `grep -P` in `changelog.sh:112` and `release.yml:27`). Fine for this machine and ubuntu runners; will not survive macOS
- [ ] `changelog.sh --check` greps for shape and dash characters only, so the writing rules are a prompt, not a gate; and `bump.sh` depends on a `claude` binary unless `--no-changelog`

## Todo

- [ ] Posting. Reply and new thread, the 4chan slider captcha (WebView or the image endpoints), 4chan Pass login. Posts land in `claimed_posts` so they read (You) without a manual claim
- [ ] Tablet and foldable two-pane layout. Catalog beside thread, thread beside viewer, on the one outer Scaffold from the 2026-08-30 shell

## Integrations

- [ ] The vault as the sync medium. Write the bookmarks/settings/filters/history backup into the vault root on every change and restore it on first launch when one is present, so a synced vault folder (Syncthing, Nextcloud) carries the whole app state between devices with no server, and bookmarks stop being the thing that dies on uninstall

## Maybe

- [ ] Full-text search across the vault: index every sidecar's `posts.json` in a Room FTS table, search text/name/ID/filename offline. List snapshot-only threads in the explorer at the same time
- [ ] Re-orderable favourites and per-board accents on Home
- [ ] Playback speed, loop toggle and frame stepping for webm
- [ ] Per-bookmark auto-save of all media in a watched thread

# Done

Finished work, kept for the record. Sections mirror the ones above.

### 8. Review wave of 2026-08-31

Two waves of four parallel worktrees off the 2026-08-31 audit; items shipped as written unless noted.

#### 0. Testing

- [x] Test the vault write path. Of 20 public methods on `MediaVaultRepositoryImpl`, only `delete` and `trash` are exercised (`MediaVaultDeleteTest`). `save`, `rescan` (the uninstall-survival promise in CLAUDE.md), `syncSavedThreads`, `snapshotThread(s)`, `renameThread`, `mergeThreads`, `importLocalThread`, `savedThread`, `migrateLegacyIfNeeded` have zero test references. The 97.6% `core/vault` figure is the codecs and `VaultStore` helpers; `VaultViewModelTest` drives a `FakeVault` whose `rescan` is a counter. Blocked partly by the hardcoded `Dispatchers.IO` (see Code quality)
- [x] Flaky-test cause. `ThreadEnv.kt` defaulted `compute` and `queueScope` to `Dispatchers.Unconfined`, never cancelled; `MediaDownloadQueue` launches a worker on it in `init`, and `stateIn(WhileSubscribed)` ran its five-second stop timer on wall-clock time, firing after `resetMain` into the next test. A fix swapping in `UnconfinedTestDispatcher` is in the working tree, uncommitted, unverified. 42 `ThreadEnv(...)` call sites leave the default

#### 1. Release safety

- [x] Release-only gates run after the point of no return. `ci.yml` builds debug only; `assembleRelease`, `check-serializers.sh` and the emulator smoke live in `release.yml`, which runs after `bump.sh` has already pushed the bump commit and the tag. A red release leaves `main` at a version with no release, the tag exists, and `bump.sh` refuses both `--just-push` (tag exists, line 78) and a rerun; recovery is manual tag deletion. Add a debug-key-signed `assembleRelease` plus `check-serializers.sh` to CI on every push
- [x] `release.yml` runs unit tests but not lint or `compileDebugAndroidTestKotlin`. Those only run in `bump.sh:96-100` on the dev machine; a hand-pushed tag skips them. CLAUDE.md calls lint errors real crashes
- [x] No `timeout-minutes` on either workflow job. The emulator step is the kind that hangs; default is six hours of billed runner
- [x] `check-serializers.sh:18` hardcodes `app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes`, an AGP 9 internal path. The next Dependabot AGP bump moves it and the script dies on the release job, loudly (the sentinel at 28-30 is right) but late
- [x] `bump.sh --watch` (line 120) sleeps 5 s then takes `gh run list --limit 1`, which can attach to the previous run
- [x] `release.yml:39` decodes the keystore via inline `${{ secrets.RELEASE_KEYSTORE_B64 }}` in a `run:`; pass it through `env:` like the rest of the file
- [x] Dependabot has no `groups:`, so every library bump is its own PR and CI run

#### 2. Bugs

- [x] `MediaVaultRepositoryImpl.migrateLegacyIfNeeded` (line 429-430): `runCatching { migration.run() }` then unconditionally writes `VAULT_MIGRATED = true`. A migration that throws halfway (permission blip, one unreadable file, disk full) is marked done forever and whatever was not moved is invisible to `rescan()`. Set the flag only on success, or record progress
- [x] `raiseReadMark` fires on every new bottom row with no debounce (`ThreadViewModel.kt:204-206, 636-644`): `readUpTo` + `updateReadUpTo` + `markSeen`, three DB writes per row scrolled past. The top-visible collector at line 199 already debounces 500 ms; do the same
- [x] `VaultViewModel.uiState` combine (lines 559-618) reruns `arrangeEntries` (full sort) and `groupByBoard` over the whole vault whenever any of its five inputs changes, so one multi-select tick or one typed character resorts every entry. Split the arranged/grouped snapshot into its own `stateIn` keyed on `(entries, view)` and combine selection on top
- [x] `Updater.kt:61-71, 98-106, 110-114` catch `Exception`, which includes `CancellationException`, so a cancelled check reports "Couldn't reach GitHub". Rethrow like `apiResult` does
- [x] `Updater.kt:242-246`: below API 33 the install-status receiver is registered without `RECEIVER_NOT_EXPORTED`; another app can broadcast a fake `STATUS_SUCCESS`. Line 231: a `STATUS_PENDING_USER_ACTION` the user never completes leaves the receiver registered for the process lifetime
- [x] `ThreadViewModel.currentRows()` (620-624) recomputes `threadRows` on the caller's thread (main, from `onVisiblePostsChanged`) before the first emission has cached `lastRows`

#### 3. Privacy and third parties

- [x] Reverse search uploads to TinEye and Yandex on one tap with no prompt. `ReverseSearchViewModel.kt:44-52`: `DIRECT_UPLOAD` is the default (`Settings.kt:129`) and `ConfirmHost` only guards the litterbox route. A user's local file, possibly a video frame of 4chan content, goes to a Russian ad-tech host by default. Either prompt on the direct route too or default to the confirmed one
- [x] The retention promise is wrong for the fallback. `strings_media.xml:17` and `strings.xml:186` say "for the next hour, then it is deleted" (litterbox). The 0x0.st fallback at `ReverseSearchUpload.kt:158` sets `X-Expires: 24` and 0x0.st does not guarantee deletion. Name the host and the real window in the dialog
- [x] The uploader inherits the 4chan client's stack. `ReverseSearchUpload.kt:83` does `client.newBuilder()`, keeping `InMemoryCookieJar`, `StaleIfOfflineInterceptor` and `CachePolicyInterceptor` (`Modules.kt:92-95`). ADR-0003 excluded the updater for exactly this reason. Verify the cookie jar is host-scoped, or build a bare client
- [x] No User-Agent on `a.4cdn.org` calls; everything goes out as `okhttp/x`. The only UA in the app is on the 0x0.st request. Not required by the API rules, but it is what 4chan asks for when they need to reach a client author
- [x] No privacy notice anywhere (`grep -ri privacy README.md docs CLAUDE.md` is empty). The app now sends data to GitHub (updates), desuarchive and b4k (archive), TinEye, Yandex, litterbox and 0x0.st (reverse search), and holds `MANAGE_EXTERNAL_STORAGE`. A README paragraph listing who receives what

#### 4. Code quality

- [x] `ReverseSearchUploader` is transport code in `feature/media` (`ReverseSearchUpload.kt:21-28, 78-84`): injects `OkHttpClient`, builds multipart bodies to four hosts, hardcodes `Dispatchers.IO` (92, 106). The only place a feature bypasses the repository boundary. Move it behind a domain interface in `data/`
- [x] `Dispatchers.IO` is hardcoded 17 times across `MediaVaultRepositoryImpl` and `VaultDedupRepositoryImpl` while `BackupRepositoryImpl` and every ViewModel inject theirs. This is why `data/repository` sits at 43.9% and why the vault write path is untested
- [x] `VaultStore.lock` (line 34) is a public `Mutex`; correctness is the phrase "under the store lock" as a doc comment on five functions, and `recordSavedFile` documents that the caller must hold it. Move the lock inside and expose `suspend fun <T> withStore(block)`
- [x] `VaultStore.attempt` (260-266) collapses every exception into `VaultError.Io(e.message)`, and there are zero `Log.*` calls in the app. A vault bug reaches the user as "Io(null)" and reaches you as nothing. Keep the cancellation rethrow; distinguish NPE from IOException and log at the boundary
- [x] `ThreadScreen` composable is about 460 lines (97-560) wiring six overlays plus swipe, scroll restore and snackbar plumbing; extract `ThreadOverlays`. `ThreadTopBar` takes about 25 parameters and the caller reads `s?.x` eleven times to feed it; pass a `TopBarState` plus an actions object like `PostCardActions`. `SearchBar` (563) takes the ViewModel where every other child takes lambdas
- [x] `VaultViewModel` loose ends: `autoAdvance` is `mutableStateOf` in a VM that otherwise exposes `StateFlow` (372); the six-way `combine<Any, View>` with casts (458-467) should nest two typed combines; `selectedEntries`, `openBoard`, `openThread`, `scopeEntries` (286-305) are linear-scan getters read from composables
- [x] `saveToVault(context, hasAccess, onAccessNeeded, save)` is called five times with the same three-line snackbar lambda (`ThreadScreen.kt:184, 300, 458` and two more). A `rememberSaveToVault(snackbar)`
- [x] `delay(500)` magic number at `ThreadViewModel.kt:199`; `HIGHLIGHT_MS` shows the pattern
- [x] `ThreadScreen.kt:236` `snapshotFlow { currentRows }.first { it != null }!!` reads as `filterNotNull().first()`

#### 7. Docs and repo hygiene

- [x] README describes the app three releases ago: tabs listed as Boards, Catalog, Thread, Media, Bookmarks, History; actual tabs are Home, Boards, Threads, Vault (`TopLevelDestination.kt:22-25`), and there is no History route. Zero mentions of app lock, widget, filters, archive fallthrough, offline snapshots, dedup, backup, vault sync, reverse search. `bin/` scripts are not named anywhere
- [x] CONTEXT-MAP.md is stale the same way: says `feature/{bookmarks,history}`, screen is `feature/threads/ThreadsScreen.kt`; no context covers `core/{lock,widget,work,backup,dedup}` or archives. CLAUDE.md lists six `core/` subpackages; there are fifteen. ADR-0002 says "38 test files"; there are 86
- [x] `.idea/` is tracked (10 files, including `caches/deviceStreaming.xml` with a Sony device selection). `.scratch/ux-report/ux-ui-improvements.md` is tracked despite `.gitignore` ignoring `.scratch/`, references `app/app/src/main/...` and has an em dash in a heading. `review/REPORT.md` and `findings.md` sit at the repo root as a one-day artefact of the 2026-08-30 pass. `.kotlin/` and `.claude/worktrees/` are not ignored
- [x] The em-dash ban is broken in `release.yml:30`, `bump.sh:5`, `bump.sh:55`

### 9. Review wave of 2026-08-31, second pass

#### 0. Testing

- [x] Test Room migrations. `MigrationChainTest` (JVM, Robolectric) builds v6 from `schemas/6.json`, seeds every table, runs the real migrations to the current version with no destructive fallback and checks the identity hash. 1 through 5 stay untestable forever: their schemas were never exported
- [x] One `MainDispatcherRule`. `fake/MainDispatcherRule.kt` exists as "the one home for setMain/resetMain"; one file uses it, 16 hand-roll the pair
- [x] Share the fakes. `FakeBoardRepository` x4, `FakeHistoryRepository` x4, `FakeSettingsRepository` x4, `FakeVault` x4, `FakeBookmarkRepository` x3, `FakeThreadRepository` x3 across `src/test` and `androidTest/di/TestRepositoryModule.kt`; the `fake/` package holds six. The androidTest `FakeMediaVaultRepository` (`TestRepositoryModule.kt:281-335`) returns null/Unit for everything, so `MediaSaveFlowTest` proves a button exists, not that saving works
- [x] Robolectric details. `robolectric.properties` pins SDK 34, `RoomTest.kt:24` pins 35, so two sandbox boots per run. `testOptions.unitTests.isReturnDefaultValues = true` makes every un-mocked Android call silently return 0/null/false; Robolectric is already on the classpath, drop the flag

#### 2. Bugs

- [x] `renameThread` (`MediaVaultRepositoryImpl.kt:348-372`) and `mergeThreads` (374-386) end with a full `rescan()`, which walks the tree, rewrites the Room table and probes every video lacking a still. A one-folder rename should not re-index the vault
- [x] `VaultPaths.fileName` (line 76) appends `ext` unsanitised. For local imports `ext` comes from `extensionOf(displayName)` of whatever a content provider reports (`LocalThreadImporter.kt:42`); a hostile provider can get a `/` in. It cannot escape the thread directory, but sanitise it
- [x] `AppNavHost.kt:97, 166`: `threadSwipe` is a `mutableStateOf<Boolean?>` set from a click callback and cleared by `LaunchedEffect(entry)` so the transition lambdas can peek at it. A race between swap and clear slides the wrong way
- [x] `themes.xml` uses `android:Theme.Material.NoActionBar` (dark) as the window theme, so light-theme users see a dark frame for one frame at launch
- [x] `VideoPage.kt` `Slider.onValueChange` calls `seekTo` on every drag frame; throttle to `onValueChangeFinished` with a local preview value. `formatMs` uses `String.format` without a `Locale`
- [x] `ThreadScreen.kt:131` and two other `openExternal` sites `runCatching { startActivity }` and swallow `ActivityNotFoundException` with no feedback

#### 4. Code quality

- [x] `core/di/Modules.kt:37-48` imports every `data.repository.*Impl`, so `core` depends on `data`, inverting the documented arrow. A root `di/` package fixes it for free
- [x] `board + "/" + threadNo` as a lazy key is hand-rolled in `HistoryList.kt:93`, `VaultStatsSheet.kt:43`, `BookmarksList.kt:143`, `BoardsSection.kt:36`; `VaultStatsSheet.lazyKey` already exists
- [x] Raw SDK ints replaced with `VERSION_CODES` in `Updater.kt` and `GalleryExporter.kt`; the `>= 30` in `MediaVaultRepositoryImpl.kt` is still a bare number
- [x] No Compose compiler stability config, so stability of domain types is on trust (`VaultArrangement` doc: "stable by construction"). A `compose_compiler_config.conf` and `reportsDestination` would let you stop guessing
- [x] 11 bare `runCatching { }.getOrNull()` sites swallow failures to null. Mostly vault IO and probably intended; audit each once logging exists

#### 5. Known gaps from the 2026-08-30 overhaul

- [x] Imported-thread merge collides on synthetic post numbers (both sides number 1..n), so the fake conversation of the source overwrites the target's; files and metadata merge correctly. Fixed 2026-08-31: merged posts are renumbered past the target's highest number and their quotes remapped

#### 6. Feature ideas (vs Readchan)

- [x] Archive fallthrough. Desuarchive and arch.b4k.co through the FoolFuuka JSON API, order live → vault snapshot → archive, in the thread screen and, since 2026-08-31, the media viewer too. Warosu has no JSON API and is a documented hook only

#### 7. Docs and repo hygiene

- [x] ADR-0001 admits a fresh install "looks exactly like data loss" until Rescan is pressed; neither doc says the app could prompt. Product gap dressed as a documentation fact

### 5. Known gaps from the 2026-08-30 overhaul

- [x] Trash entries lived in memory. The trash now has an `index.json` beside the moved files under `.trash/`, keeps each file seven days, and a Trash entry in the vault's More menu restores or empties it
- [x] Restored bookmarks showed 0 unread until the next refresh. Import starts a refresh pass in the background
- [x] Sort order in the Threads tab was not persisted. It is `Settings.bookmarkSortOrder` now

### 6. Feature ideas, reading loop

- [x] Thread watcher. Background refresh (WorkManager, per-board catalog fetch, one `readUpTo` mark) and new-reply notifications shipped 2026-08-30; the "collapse everything above the read mark" view followed the same day

### 1. Release safety

- [x] Nothing launched the release APK, so 1.1.1 shipped a build that crashed before its
  first frame (R8 renamed a navigation argument enum). `smoke.sh` now installs the APK,
  launches it and fails if the process is gone or logcat shows a fatal within 10 s; the
  release workflow runs it on an API 34 emulator against the exact APK it is about to
  publish, and a failure stops the release. Verified 2026-08-30 on a local AVD, including
  the negative case (`am crash` mid-window fails the script)
- [x] posts.json decoding under R8. Not exercised at runtime: instrumented tests cannot run
  against the minified build without keeping the whole kotlin stdlib (the test APK borrows
  it from the app), and moving `testBuildType` to release would kill the Hilt flow suite.
  Instead `check-serializers.sh` compares every `$$serializer` class the compiler generated
  with what R8 kept, per `mapping.txt`, and the release workflow runs it after the build.
  All 30 survive today; a broken keep rule fails the release rather than a user's vault

### 1. Code quality, blockers

- [x] Unify the two media viewers: extract shared `MediaFeedViewer` (pager + chrome + playback + PiP) and `PipController`; `VaultViewer` becomes a thin mapping. Kills ~350 duplicated lines, brings `MediaScreen.kt` (717) and `VaultScreen.kt` (545) under the 500-line limit
- [x] Decompose `MediaScreen.kt` further: `DownloadAction.kt` (sealed `SaveStatus` instead of double-branching on `(downloaded, queueState)`), `SubThreadPanel.kt`, `MediaShare.kt`, `ViewerStack.kt`
- [x] Stop swallowing `CancellationException`: shared `apiResult { }` helper in all four repositories (Board/Catalog/Thread/Bookmark), rethrow cancellation, drop redundant `Dispatchers.IO` wrapping; fixes stale `dao.updateRefresh` write in cancelled scope
- [x] Commit to the domain boundary: `VaultEntry` domain model + `VaultLocation` sum type (kill `"_unsorted"`/`0L` sentinels), vault reads via `MediaVaultRepository`, hidden threads behind a repository; remove `SavedMediaDao`/`HiddenThreadDao`/entities from ViewModels and composables
- [x] Fix `ThreadViewModel.uiState` 14-flow array-combine with 8 unchecked casts: typed hierarchical sub-states (`SearchState`, `SpoilerState`, ...); extract `ThreadPoller` (poll loop + backoff)

### 2. Code quality, judo moves

- [x] Generic `UiState<T>` + `LoadableFlow` + `UiStateContent` composable; delete the three per-feature Loading/Error/Success sealed interfaces, `load()` copies, and screen `when`-blocks (null-means-loading made explicit once)
- [x] `SettingsDataStore`: dedupe the twice-written 15-field mapping (`dataStore.data.map(::snapshot)`); consider serialized `DataStore<Settings>`
- [x] `PostHtmlParser` → stateless `object`; delete DI singleton, hand-made second instance in `Mappers.kt`, and the threaded parameter through both repositories
- [x] Extract `SwipeToDeleteRow` + `showUndo` snackbar helper (Bookmarks/History copy-paste, Catalog third consumer)
- [x] Extract `ThreadSummaryRow` component + domain `displayTitle` (title-fallback expression exists 3×)
- [x] `core/media/MediaByteSource.kt`: one "Coil cache else network" helper; fix the `java.net.URL.openStream()` OkHttp bypass
- [x] Extract `VaultLegacyMigration` out of `MediaVaultRepositoryImpl` (repo loses 4 sideways deps, lands ~220 lines)

### 3. Bugs

- [x] Thread search dual source of truth: VM exposes `currentMatchPostNo`; delete `ThreadScreen.scrollToMatch`
- [x] `PostHtmlParser.flush`: link inside spoiler drops the Spoiler reveal annotation. Verify against `PostBody` reveal keying; fix segment model if it leaks
- [x] History retention runs only when History tab opens. Move into `HistoryRepositoryImpl`
- [x] Vault error contract: replace `Boolean`/`runCatching().getOrNull()` with typed failure reasons so FAILED badges can say why; remove double `runCatching` in `MediaDownloadQueue`
- [x] `MediaItem` deleted-file sentinel (`fullUrl = ""`, zeros) → sealed/nullable `PostMedia`
- [x] `BookmarkRepository.isBookmarked`: drop `suspend` on Flow-returning fun; remove `flowOf(Unit).flatMapLatest` ceremony in ThreadViewModel
- [x] VM methods stop reading their own UI state (`uiState.value as? Success ?: return`); read source flows instead
- [x] `SettingsViewModel`: move cache clearing behind a `MaintenanceRepository`; DAO access behind repositories; no `HiddenThreadEntity` in UI state

### 4. Batch cleanups

- [x] Read-position/scroll-restore logic out of `ThreadScreen` composable into the VM (`scrollTarget` flow); extract `QuotePreviewOverlay.kt`, `ExternalLinkDialog.kt`; dedupe the double `PostCard` invocation
- [x] Settings enum-label when-ladders → `labelRes` per enum; extract `ManagedListDialog` (trusted domains / hidden threads clones)
- [x] Settings index + subscreens: one flat 360-line scroll → an 8-row index (`SettingsScreen.kt`) plus `SettingsSectionScreen` and seven section files, none over 75 lines; `SwitchRow`/`TextRow`/`ChipRow`/`NavigationRow` lifted into `core/designsystem`. `ChipRow` takes a composable label producer so a chip can format its own value
- [x] Delete `Settings.thumbnailSize` and `Settings.density`: persisted, round-tripped, and read by nothing. A setting nobody can reach is a lie in the data model
- [x] `backlinksOf` + `PostGraph` into `domain/model`: the transitive reply walk lived in `MediaUiState`, a UI class, and the backlink build was inline in `Mappers`. Both are now pure and JVM-tested, and the vault reader needs the same walk
- [x] `rememberPipController(feed, lastIndex)`: both viewers wired the same three transport callbacks by hand
- [ ] ~~Collapse `MediaItem.toViewerPage` and `VaultEntry.toViewerPage` into one~~. **Rejected on inspection 2026-08-29.** They share a shape, not logic: one resolves remote-vs-local sources and counts pending saves, the other is always local with nullable dimensions and a thread subject. Merging needs exactly the optional flags that make control flow worse. The shared part was already extracted as `MediaFeedViewer`/`PipController`
- [x] `AppNavHost` nav-item loop written twice → one shared items builder
- [x] Move `SectionHeader` from `feature.boards` to `core/designsystem`
- [x] Entity mappers into `Mappers.kt` with named args (positional 7-arg `HistoryEntity` mapping is a silent-corruption risk)
- [x] Drop dead `DownloadedMediaEntity`/`Dao` (remove `insert` now, table-drop migration later)
- [x] Room `Migration` objects out of `Modules.kt` → `core/database/Migrations.kt`
- [x] Rename `core/util/Result.kt` → `DataResult.kt`
- [x] `HistoryBucket` enum instead of `R.string` ids in `HistoryUiState`
- [x] `HiddenThreadDao.forBoard(board)` query instead of in-memory filter in CatalogViewModel
- [x] `OnResumeEffect` helper (Bookmarks hand-rolled observer, ThreadScreen's DisposableEffect variant)
- [x] Unify `hasStorageAccess` polarity (MediaViewModel vs VaultViewModel); delete duplicate `findActivity`; `SavedMediaEntity` factory functions; `VaultSaveContext` → `domain/model/`; VaultScreen O(n²) `keys.sorted()` in `items()`; `Urls.parseInternal` slash contortion; `BoardRepositoryImpl` board lookup via `Map`

### 5. Overhaul of 2026-08-30

Plan and review notes in `.scratch/next-level/plan.md`. Shipped in 148 commits: bookmarks became a
thread watcher; Saved became a saved-media tab (trash with undo, multi-select, open thread, totals,
sort/filter, local video stills, Recent feed, search, rename/merge, statistics); thread reader
(jump-to-quote, (OP)/(You), quoted-by row, ID filter, gallery, tree view, closed/sticky, long-press
sheet, jump FABs); shell (Home + Boards + Threads + Saved tabs, Settings behind a gear, one outer
Scaffold, bounded back stack, transitions, deep links, share target, widget); backup/restore of
bookmarks + settings + hidden threads into the vault root; filters; archive fallthrough; sound posts;
data saver; widget. Debt burned: `Repositories.kt`/`Daos.kt` split per declaration, settings as one
serialized blob, `SettingsSectionId` carries its own title/icon, catalog combine typed, `ViewerPage`
sealed, thread session state folded into one flow, `MediaSaveQueue` domain interface, deprecated
vault members deleted.

### 6. Feature ideas (vs Readchan)

#### Reading loop

- [x] Tree/reply-chain view. Menu toggle, `PostGraph.tree()`, depth capped at 4 with an expandable "N more replies" row
- [x] "(You)" without posting. `claimed_posts` table (DB 9), long-press "Mark as mine", quotelinks read (You), "N replies to you" under the title
- [x] Shared-element thumbnail → viewer transitions, haptics, and a motion pass shipped 2026-08-30 (`core/designsystem/SharedMedia.kt`, `Haptics.kt`, `MotionSpecs.kt`, all collapsing under animator scale 0)
- [x] Cross-thread ghost quotes. `>>>/board/no` quotes and numbered deadlinks open in the preview sheet through `GhostResolver` (held copy → vault sidecar → live → archive), with a "From /b/123 · Saved copy" line and an Open thread button; long-press still navigates. Ghost posts carry no (OP) tag or ID counts

#### Media

- [x] Hold to save. Long-press a thumbnail in a thread or an open image/video in the viewer. Uses telephoto's `onLongClick`, no gesture overlay. The catalogue is deliberately excluded: long-press there already hides a thread
- [x] Double-tap the edges of a video to skip, with a configurable step. Implemented as a `DoubleClickToZoomListener`, so the middle third keeps zoom and the pager's drag is untouched. The jump is capped at a quarter of the running time, so a 10 s step does not overshoot a 2 s webm
- [x] Keep the screen on while a video plays. One owner in `MediaFeedViewer` via `View.keepScreenOn`; the window flag is not refcounted and several `VideoPage`s are composed at once
- [x] Import local files or a folder as an offline thread, filed under a reserved `_local` board. Files are copied, never referenced: a SAF grant can be revoked. `rescan()` needed no change, since imports key on `file://<path>` like unsorted migration leftovers already did
- [x] Vault dedup. MD5 checked before download (`MediaSaveStatus.AlreadySaved`), and an on-demand "Find duplicates" sheet in Saved: backfills MD5 + a 64-bit dHash, Exact or Similar (Hamming distance, default 6) grouping with a suggested keeper, per-group or apply-all deletes. DB 10. `md5` is not written into the sidecar yet, so a rescan drops hashes until the next backfill
- [x] Thread gallery grid with "Save all" (no prefetch; each save goes through the normal queue)
- [x] Sound-post support. `core/media/SoundPost.kt` parses the filename tag, a second ExoPlayer follows the visual; images with sound have no mute button of their own and follow the feed's shared mute

#### Filtering & comfort

- [x] Regex/keyword/name/flag/ID/filename filters, per-board scoping, Hide or Stub, Settings > Filters with live regex validation and a test field; applied to catalog and thread
- [x] Data-saver mode. `Settings.dataSaver` + `NetworkMonitor.metered`; videos stop autoplaying and full images wait behind a "Load (N MB)" pill. Catalog/thread thumbnails still load

### 7. Shipped 2026-08-30, second pass

- [x] Text size and line spacing (Settings > Reading), applied to post bodies and catalog excerpts through `LocalPostTypography`; chrome follows the system
- [x] Inline image expansion behind Settings > Media (default off): still images expand in the card, videos/gifs/sound posts keep the viewer, data saver shows the Load pill
- [x] Reverse image search from both viewers: Lens/SauceNAO/IQDB/TinEye/Yandex by URL, "Share to another app" for local-only files, and a frame picker for videos (remote ones fetched to the share cache first)
- [x] Swipe left/right in a thread to the next/previous thread in the catalog order it was opened from (`ThreadSiblingsStore`). Not recorded from the Threads tab or History yet
- [x] `bump.sh` writes `changelog/vX.Y.Z.md` through `claude -p` and the release workflow uses it as the body; the updater parses it (`core/update/ReleaseNotes.kt`). CI never calls Claude

### Maybe

- [x] Reverse image search for local-only files, all engines. TinEye (multipart to `api/v1/result_json`, results keyed by `query_hash`) and Yandex (raw bytes to `images-apphost/image-download`, results by `cbir_id`) upload to the engine itself (`ReverseSearchUploader`); SauceNAO and IQDB, whose forms return no shareable URL, go through litterbox (1 hour) with 0x0.st as backup, and a privacy setting picks the default route. The host route always shows a confirm row first; a failed direct upload offers the host as a retry
