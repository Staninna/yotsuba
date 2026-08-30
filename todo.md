# Yotsuba TODO

Open work first; everything finished lives under `# Done` at the bottom.
## 0. Testing

- [ ] 100% unit test coverage. Not met; last measured 2026-08-29 at 22.8% line / 20.1% instruction / 21.8% branch (JaCoCo via `./gradlew :app:createDebugUnitTestCoverageReport`). 391 JVM tests pass as of 2026-08-30 (up from 269); the percentage has not been re-measured since the overhaul and is due. One unit-test run failed once and passed on three reruns without a code change, so there is a flaky test somewhere in the suite. Seen again 2026-08-30 in `LoadableFlowTest` and `ApiResultTest` (leaked `Dispatchers.Main`, "uncaught exceptions before the test started"); both pass alone, so some earlier test leaks its dispatcher. Near-100% where JVM-testable: domain/model 99.4%, core/text 98.5%, core/vault 97.6%, core/util 97.1%, network DTOs 96.1%. `feature/thread` 55.9%, `data/repository` 43.9%. The remainder is mostly Compose UI (designsystem, screens, navigation) which needs instrumented coverage, plus Robolectric-hosted tests (Catalog/Settings VMs, MediaByteSource, SettingsDataStore) whose sandbox classloader bypasses JaCoCo, so they record 0% despite passing. `core/datastore` reads 0% for that reason alone. `feature/media` is 4.7%: the viewer VMs depend on Context/ExoPlayer, though `ViewerBehaviour` and `PostGraph` are now pure and fully covered
- [ ] 100% e2e test coverage. 8 instrumented Compose tests in `app/src/androidTest/` cover every screen's primary flow (boards → catalog → thread → media viewer, bookmark add/remove, history, vault, settings toggle) with Hilt `@TestInstallIn` fake repositories, no network. They compile (`:app:compileDebugAndroidTestKotlin`) but need a device: `./gradlew :app:connectedDebugAndroidTest`. Attempted on hardware 2026-08-29 and did not run: the test APK failed to link against the installed app APK (`NoSuchMethodError: kotlinx.coroutines.BuildersKt.runBlockingK`), a stale-artifact mismatch rather than a test failure. Unresolved. Secondary flows (search, spoilers, PiP, downloads, hold-to-save, edge-seek) are uncovered, and the gesture work in particular cannot be trusted without a device

## 5. Known gaps from the 2026-08-30 overhaul

- Imported-thread merge collides on synthetic post numbers (both sides number 1..n), so the fake
  conversation of the source overwrites the target's; files and metadata merge correctly

## 6. Feature ideas (vs Readchan)

### Archival (the killer read-only feature)

- [~] Full offline thread snapshots, **mostly done.** Saving media writes `posts.json` beside it with the saved post's transitive parents and replies, as parsed segments so greentext, quotelinks and spoilers survive. The vault's Sync button then walks every saved thread, fetches the live one and merges its **whole** comment section in. While the thread still exists, that is the only chance to take it. `MediaVaultRepository.savedThread()` rebuilds a `ThreadDetails` from the sidecar, and the media viewer falls back to it when the live fetch fails, so a 404'd thread stays readable. Sync is rate-limited to ~1 thread/second by `RateLimitInterceptor` and reports a `done / total` counter; a `RateLimited` response stops the pass rather than hammering. Bookmarked threads now snapshot without a save (`snapshotThread`, a row action, and `VaultSyncWorker` every 6 h with `Settings.snapshotWatchedThreads`), and a dead thread's sidecar can be pruned to the OP plus the conversations around saved files (`Settings.pruneDeadSidecars`, off by default; snapshot-only threads are never pruned). Still missing: thumbnails for unsaved posts, and the explorer does not list snapshot-only threads (no media rows), so they are reachable only through the thread screen's offline fallback. The thread screen itself opens the sidecar copy when live and archive both fail ("Offline copy from <date>")
- [~] Archive fallthrough. Desuarchive and arch.b4k.co through the FoolFuuka JSON API, order live → vault snapshot → archive. Warosu has no JSON API and is a documented hook only. The media viewer still does live → vault, no archive

### Filtering & comfort

- [~] Favourite boards are the Home tab (swipeable catalog pages, first tab, start destination). Re-ordering favourites and per-board accents are not done

## Todo

- [ ] Posting. Reply and new thread, the 4chan slider captcha (WebView or the image endpoints), 4chan Pass login. Posts land in `claimed_posts` so they read (You) without a manual claim
- [ ] Tablet and foldable two-pane layout. Catalog beside thread, thread beside viewer, on the one outer Scaffold from the 2026-08-30 shell

## Integrations

- [ ] The vault as the sync medium. Write the bookmarks/settings/filters/history backup into the vault root on every change and restore it on first launch when one is present, so a synced vault folder (Syncthing, Nextcloud) carries the whole app state between devices with no server, and bookmarks stop being the thing that dies on uninstall

## Maybe

- [ ] Full-text search across the vault: index every sidecar's `posts.json` in a Room FTS table, search text/name/ID/filename offline. List snapshot-only threads in the explorer at the same time
- [ ] Archive fallthrough in the media viewer (still live → vault only)
- [ ] Re-orderable favourites and per-board accents on Home
- [ ] Playback speed, loop toggle and frame stepping for webm
- [ ] Per-bookmark auto-save of all media in a watched thread

# Done

Finished work, kept for the record. Sections mirror the ones above.

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
