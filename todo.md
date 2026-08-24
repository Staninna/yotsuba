# Yotsuba TODO

## 0. Testing

- [ ] 100% unit test coverage (production code, tests excluded from the 500-line file rule)
- [ ] 100% e2e test coverage (instrumented/Compose UI tests covering every screen and flow)

## 1. Code quality — blockers

- [ ] Unify the two media viewers: extract shared `MediaFeedViewer` (pager + chrome + playback + PiP) and `PipController`; `VaultViewer` becomes a thin mapping. Kills ~350 duplicated lines, brings `MediaScreen.kt` (717) and `VaultScreen.kt` (545) under the 500-line limit
- [ ] Decompose `MediaScreen.kt` further: `DownloadAction.kt` (sealed `SaveStatus` instead of double-branching on `(downloaded, queueState)`), `SubThreadPanel.kt`, `MediaShare.kt`, `ViewerStack.kt`
- [x] Stop swallowing `CancellationException`: shared `apiResult { }` helper in all four repositories (Board/Catalog/Thread/Bookmark), rethrow cancellation, drop redundant `Dispatchers.IO` wrapping; fixes stale `dao.updateRefresh` write in cancelled scope
- [ ] Commit to the domain boundary: `VaultEntry` domain model + `VaultLocation` sum type (kill `"_unsorted"`/`0L` sentinels), vault reads via `MediaVaultRepository`, hidden threads behind a repository; remove `SavedMediaDao`/`HiddenThreadDao`/entities from ViewModels and composables
- [ ] Fix `ThreadViewModel.uiState` 14-flow array-combine with 8 unchecked casts: typed hierarchical sub-states (`SearchState`, `SpoilerState`, ...); extract `ThreadPoller` (poll loop + backoff)

## 2. Code quality — judo moves

- [x] Generic `UiState<T>` + `LoadableFlow` + `UiStateContent` composable; delete the three per-feature Loading/Error/Success sealed interfaces, `load()` copies, and screen `when`-blocks (null-means-loading made explicit once)
- [x] `SettingsDataStore`: dedupe the twice-written 15-field mapping (`dataStore.data.map(::snapshot)`); consider serialized `DataStore<Settings>`
- [x] `PostHtmlParser` → stateless `object`; delete DI singleton, hand-made second instance in `Mappers.kt`, and the threaded parameter through both repositories
- [ ] Extract `SwipeToDeleteRow` + `showUndo` snackbar helper (Bookmarks/History copy-paste, Catalog third consumer)
- [ ] Extract `ThreadSummaryRow` component + domain `displayTitle` (title-fallback expression exists 3×)
- [ ] `core/media/MediaByteSource.kt`: one "Coil cache else network" helper; fix the `java.net.URL.openStream()` OkHttp bypass
- [ ] Extract `VaultLegacyMigration` out of `MediaVaultRepositoryImpl` (repo loses 4 sideways deps, lands ~220 lines)

## 3. Bugs

- [ ] Thread search dual source of truth: VM exposes `currentMatchPostNo`; delete `ThreadScreen.scrollToMatch`
- [ ] `PostHtmlParser.flush`: link inside spoiler drops the Spoiler reveal annotation — verify against `PostBody` reveal keying; fix segment model if it leaks
- [x] History retention runs only when History tab opens — move into `HistoryRepositoryImpl`
- [ ] Vault error contract: replace `Boolean`/`runCatching().getOrNull()` with typed failure reasons so FAILED badges can say why; remove double `runCatching` in `MediaDownloadQueue`
- [ ] `MediaItem` deleted-file sentinel (`fullUrl = ""`, zeros) → sealed/nullable `PostMedia`
- [x] `BookmarkRepository.isBookmarked`: drop `suspend` on Flow-returning fun; remove `flowOf(Unit).flatMapLatest` ceremony in ThreadViewModel
- [ ] VM methods stop reading their own UI state (`uiState.value as? Success ?: return`) — read source flows
- [ ] `SettingsViewModel`: move cache clearing behind a `MaintenanceRepository`; DAO access behind repositories; no `HiddenThreadEntity` in UI state

## 4. Batch cleanups

- [ ] Read-position/scroll-restore logic out of `ThreadScreen` composable into the VM (`scrollTarget` flow); extract `QuotePreviewOverlay.kt`, `ExternalLinkDialog.kt`; dedupe the double `PostCard` invocation
- [ ] Settings enum-label when-ladders → `labelRes` per enum; extract `ManagedListDialog` (trusted domains / hidden threads clones)
- [ ] `AppNavHost` nav-item loop written twice → one shared items builder
- [ ] Move `SectionHeader` from `feature.boards` to `core/designsystem`
- [x] Entity mappers into `Mappers.kt` with named args (positional 7-arg `HistoryEntity` mapping is a silent-corruption risk)
- [ ] Drop dead `DownloadedMediaEntity`/`Dao` (remove `insert` now, table-drop migration later)
- [x] Room `Migration` objects out of `Modules.kt` → `core/database/Migrations.kt`
- [x] Rename `core/util/Result.kt` → `DataResult.kt`
- [x] `HistoryBucket` enum instead of `R.string` ids in `HistoryUiState`
- [x] `HiddenThreadDao.forBoard(board)` query instead of in-memory filter in CatalogViewModel
- [ ] `OnResumeEffect` helper (Bookmarks hand-rolled observer, ThreadScreen's DisposableEffect variant)
- [ ] Unify `hasStorageAccess` polarity (MediaViewModel vs VaultViewModel); delete duplicate `findActivity`; `SavedMediaEntity` factory functions; `VaultSaveContext` → `domain/model/`; `Urls.parseInternal` slash contortion; `BoardRepositoryImpl` board lookup via `Map`; VaultScreen O(n²) `keys.sorted()` in `items()`

## 5. Feature ideas (vs Readchan)

### Reading loop
- [ ] Tree/reply-chain view (linear ↔ threaded toggle; `SubThreadPanel` BFS is 80% of the model)
- [ ] "(You)" without posting — claim posts, highlight/count replies to claimed posts
- [ ] Cross-thread ghost quotes — resolve `>>>/board/no` and dead backlinks against history/archive cache
- [ ] Thread watcher diff view — collapse above `maxReadPostNo`, background refresh + new-reply notifications

### Media
- [ ] Vault dedup by MD5 (API provides it); mark already-saved media in threads
- [ ] Gallery prefetch + one-tap "save whole thread's media" batch
- [ ] Sound-post support (`[sound=...]`)

### Archival (the killer read-only feature)
- [ ] Full offline thread snapshots — bookmarked threads persist posts + thumbnails (optionally full media); 404'd threads stay readable
- [ ] Archive fallthrough — desuarchive/warosu/b4k per board when a thread 404s

### Filtering & comfort
- [ ] Regex/keyword/name/flag filters, per-board scoping, "filtered, tap to reveal" stub
- [ ] Board favorites + catalog re-ordering; per-board theme accents
- [ ] Data-saver mode (thumbnails only on metered, via `NetworkMonitor`)
