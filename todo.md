# Yotsuba TODO

## 0. Testing

- [ ] 100% unit test coverage — not met; last measured 2026-08-29 at 22.8% line / 20.1% instruction / 21.8% branch (JaCoCo via `./gradlew :app:createDebugUnitTestCoverageReport`). 391 JVM tests pass as of 2026-08-30 (up from 269); the percentage has not been re-measured since the overhaul and is due. One unit-test run failed once and passed on three reruns without a code change, so there is a flaky test somewhere in the suite; not yet identified. Near-100% where JVM-testable: domain/model 99.4%, core/text 98.5%, core/vault 97.6%, core/util 97.1%, network DTOs 96.1%. `feature/thread` 55.9%, `data/repository` 43.9%. The remainder is mostly Compose UI (designsystem, screens, navigation) which needs instrumented coverage, plus Robolectric-hosted tests (Catalog/Settings VMs, MediaByteSource, SettingsDataStore) whose sandbox classloader bypasses JaCoCo, so they record 0% despite passing — `core/datastore` reads 0% for that reason alone. `feature/media` is 4.7%: the viewer VMs depend on Context/ExoPlayer, though `ViewerBehaviour` and `PostGraph` are now pure and fully covered
- [ ] 100% e2e test coverage — 8 instrumented Compose tests in `app/src/androidTest/` cover every screen's primary flow (boards → catalog → thread → media viewer, bookmark add/remove, history, vault, settings toggle) with Hilt `@TestInstallIn` fake repositories, no network. They compile (`:app:compileDebugAndroidTestKotlin`) but need a device: `./gradlew :app:connectedDebugAndroidTest`. Attempted on hardware 2026-08-29 and did not run: the test APK failed to link against the installed app APK (`NoSuchMethodError: kotlinx.coroutines.BuildersKt.runBlockingK`), a stale-artifact mismatch rather than a test failure — unresolved. Secondary flows (search, spoilers, PiP, downloads, hold-to-save, edge-seek) are uncovered, and the gesture work in particular cannot be trusted without a device

## 1. Release safety

- [ ] Nothing launches the release APK. CI builds it, verifies it is release-signed, and
  publishes it, but never starts it, so 1.1.1 shipped a build that crashed before its first
  frame: R8 renamed an enum used as a navigation argument, and type-safe routes look
  argument types up by name. Debug is not minified, so no amount of dev-build testing could
  have caught it. Until something smoke-launches the minified build, every release-only
  crash class is discovered by installing it
- [ ] The @Serializable sealed PostAnnotation hierarchy in the vault sidecar has the same
  shape of risk and has not been exercised from a minified build: serialNames are baked at
  compile time so the JSON is stable, but decoding a saved posts.json under R8 is untested

## 1. Code quality — blockers

- [x] Unify the two media viewers: extract shared `MediaFeedViewer` (pager + chrome + playback + PiP) and `PipController`; `VaultViewer` becomes a thin mapping. Kills ~350 duplicated lines, brings `MediaScreen.kt` (717) and `VaultScreen.kt` (545) under the 500-line limit
- [x] Decompose `MediaScreen.kt` further: `DownloadAction.kt` (sealed `SaveStatus` instead of double-branching on `(downloaded, queueState)`), `SubThreadPanel.kt`, `MediaShare.kt`, `ViewerStack.kt`
- [x] Stop swallowing `CancellationException`: shared `apiResult { }` helper in all four repositories (Board/Catalog/Thread/Bookmark), rethrow cancellation, drop redundant `Dispatchers.IO` wrapping; fixes stale `dao.updateRefresh` write in cancelled scope
- [x] Commit to the domain boundary: `VaultEntry` domain model + `VaultLocation` sum type (kill `"_unsorted"`/`0L` sentinels), vault reads via `MediaVaultRepository`, hidden threads behind a repository; remove `SavedMediaDao`/`HiddenThreadDao`/entities from ViewModels and composables
- [x] Fix `ThreadViewModel.uiState` 14-flow array-combine with 8 unchecked casts: typed hierarchical sub-states (`SearchState`, `SpoilerState`, ...); extract `ThreadPoller` (poll loop + backoff)

## 2. Code quality — judo moves

- [x] Generic `UiState<T>` + `LoadableFlow` + `UiStateContent` composable; delete the three per-feature Loading/Error/Success sealed interfaces, `load()` copies, and screen `when`-blocks (null-means-loading made explicit once)
- [x] `SettingsDataStore`: dedupe the twice-written 15-field mapping (`dataStore.data.map(::snapshot)`); consider serialized `DataStore<Settings>`
- [x] `PostHtmlParser` → stateless `object`; delete DI singleton, hand-made second instance in `Mappers.kt`, and the threaded parameter through both repositories
- [x] Extract `SwipeToDeleteRow` + `showUndo` snackbar helper (Bookmarks/History copy-paste, Catalog third consumer)
- [x] Extract `ThreadSummaryRow` component + domain `displayTitle` (title-fallback expression exists 3×)
- [x] `core/media/MediaByteSource.kt`: one "Coil cache else network" helper; fix the `java.net.URL.openStream()` OkHttp bypass
- [x] Extract `VaultLegacyMigration` out of `MediaVaultRepositoryImpl` (repo loses 4 sideways deps, lands ~220 lines)

## 3. Bugs

- [x] Thread search dual source of truth: VM exposes `currentMatchPostNo`; delete `ThreadScreen.scrollToMatch`
- [x] `PostHtmlParser.flush`: link inside spoiler drops the Spoiler reveal annotation — verify against `PostBody` reveal keying; fix segment model if it leaks
- [x] History retention runs only when History tab opens — move into `HistoryRepositoryImpl`
- [x] Vault error contract: replace `Boolean`/`runCatching().getOrNull()` with typed failure reasons so FAILED badges can say why; remove double `runCatching` in `MediaDownloadQueue`
- [x] `MediaItem` deleted-file sentinel (`fullUrl = ""`, zeros) → sealed/nullable `PostMedia`
- [x] `BookmarkRepository.isBookmarked`: drop `suspend` on Flow-returning fun; remove `flowOf(Unit).flatMapLatest` ceremony in ThreadViewModel
- [x] VM methods stop reading their own UI state (`uiState.value as? Success ?: return`) — read source flows
- [x] `SettingsViewModel`: move cache clearing behind a `MaintenanceRepository`; DAO access behind repositories; no `HiddenThreadEntity` in UI state

## 4. Batch cleanups

- [x] Read-position/scroll-restore logic out of `ThreadScreen` composable into the VM (`scrollTarget` flow); extract `QuotePreviewOverlay.kt`, `ExternalLinkDialog.kt`; dedupe the double `PostCard` invocation
- [x] Settings enum-label when-ladders → `labelRes` per enum; extract `ManagedListDialog` (trusted domains / hidden threads clones)
- [x] Settings index + subscreens: one flat 360-line scroll → an 8-row index (`SettingsScreen.kt`) plus `SettingsSectionScreen` and seven section files, none over 75 lines; `SwitchRow`/`TextRow`/`ChipRow`/`NavigationRow` lifted into `core/designsystem`. `ChipRow` takes a composable label producer so a chip can format its own value
- [x] Delete `Settings.thumbnailSize` and `Settings.density`: persisted, round-tripped, and read by nothing. A setting nobody can reach is a lie in the data model
- [x] `backlinksOf` + `PostGraph` into `domain/model`: the transitive reply walk lived in `MediaUiState`, a UI class, and the backlink build was inline in `Mappers`. Both are now pure and JVM-tested, and the vault reader needs the same walk
- [x] `rememberPipController(feed, lastIndex)`: both viewers wired the same three transport callbacks by hand
- [ ] ~~Collapse `MediaItem.toViewerPage` and `VaultEntry.toViewerPage` into one~~ — **rejected on inspection 2026-08-29.** They share a shape, not logic: one resolves remote-vs-local sources and counts pending saves, the other is always local with nullable dimensions and a thread subject. Merging needs exactly the optional flags that make control flow worse. The shared part was already extracted as `MediaFeedViewer`/`PipController`
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

## 5. Overhaul of 2026-08-30

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

Known gaps from that pass, none verified on a device yet:
- Imported-thread merge collides on synthetic post numbers (both sides number 1..n), so the fake
  conversation of the source overwrites the target's; files and metadata merge correctly
- Trash entries live in memory; a process death empties the trash on next launch
- Restored bookmarks show 0 unread until the next refresh (no `postNos` until then)
- Sort order in the Threads tab is not persisted
- `tab_bookmarks`, `tab_history`, `settings_summary_boards`, `settings_summary_storage`,
  `vault_rescan`, `vault_open_external` in `strings.xml` are unused now
- Instrumented flows were updated for the new shell (Threads tab, Boards tab, gear) and compile,
  but have not run on hardware since the 2026-08-29 link failure

## 6. Feature ideas (vs Readchan)

### Reading loop
- [x] Tree/reply-chain view — menu toggle, `PostGraph.tree()`, depth capped at 4 with an expandable "N more replies" row
- [x] "(You)" without posting — `claimed_posts` table (DB 9), long-press "Mark as mine", quotelinks read (You), "N replies to you" under the title
- [ ] Cross-thread ghost quotes — resolve `>>>/board/no` and dead backlinks against history/archive cache (the archive client now exists in `core/network/ArchiveHosts.kt`; wiring is the remaining half)
- [~] Thread watcher — background refresh (WorkManager, per-board catalog fetch, one `readUpTo` mark) and new-reply notifications shipped 2026-08-30; the "collapse everything above the read mark" view is still open

### Media
- [x] Hold to save — long-press a thumbnail in a thread or an open image/video in the viewer. Uses telephoto's `onLongClick`, no gesture overlay. The catalogue is deliberately excluded: long-press there already hides a thread
- [x] Double-tap the edges of a video to skip, with a configurable step. Implemented as a `DoubleClickToZoomListener`, so the middle third keeps zoom and the pager's drag is untouched. The jump is capped at a quarter of the running time, so a 10 s step does not overshoot a 2 s webm
- [x] Keep the screen on while a video plays — one owner in `MediaFeedViewer` via `View.keepScreenOn`; the window flag is not refcounted and several `VideoPage`s are composed at once
- [x] Import local files or a folder as an offline thread, filed under a reserved `_local` board. Files are copied, never referenced: a SAF grant can be revoked. `rescan()` needed no change — imports key on `file://<path>` like unsorted migration leftovers already did
- [ ] Vault dedup by MD5 (API provides it); already-saved media is marked in threads through `MediaSaveQueue.statuses`, dedup on save is not done
- [x] Thread gallery grid with "Save all" (no prefetch; each save goes through the normal queue)
- [x] Sound-post support — `core/media/SoundPost.kt` parses the filename tag, a second ExoPlayer follows the visual; images with sound have no mute button of their own and follow the feed's shared mute

### Archival (the killer read-only feature)
- [~] Full offline thread snapshots — **mostly done.** Saving media writes `posts.json` beside it with the saved post's transitive parents and replies, as parsed segments so greentext, quotelinks and spoilers survive. The vault's Sync button then walks every saved thread, fetches the live one and merges its **whole** comment section in — while the thread still exists, that is the only chance to take it. `MediaVaultRepository.savedThread()` rebuilds a `ThreadDetails` from the sidecar, and the media viewer falls back to it when the live fetch fails, so a 404'd thread stays readable. Sync is rate-limited to ~1 thread/second by `RateLimitInterceptor` and reports a `done / total` counter; a `RateLimited` response stops the pass rather than hammering. Still missing: bookmarked threads snapshotting without a save, thumbnails for unsaved posts, background/periodic sync rather than a manual tap (the button is now split into Rescan and Fetch new replies), and any pruning story for a sidecar that only grows. The thread screen itself opens the sidecar copy when live and archive both fail ("Offline copy from <date>")
- [~] Archive fallthrough — desuarchive and arch.b4k.co through the FoolFuuka JSON API, order live → vault snapshot → archive. Warosu has no JSON API and is a documented hook only. The media viewer still does live → vault, no archive

### Filtering & comfort
- [x] Regex/keyword/name/flag/ID/filename filters, per-board scoping, Hide or Stub, Settings > Filters with live regex validation and a test field; applied to catalog and thread
- [~] Favourite boards are the Home tab (swipeable catalog pages, first tab, start destination). Re-ordering favourites and per-board accents are not done
- [x] Data-saver mode — `Settings.dataSaver` + `NetworkMonitor.metered`; videos stop autoplaying and full images wait behind a "Load (N MB)" pill. Catalog/thread thumbnails still load
