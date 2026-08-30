# Thermo-nuclear review report

Repo: yotsuba, reviewed at `20a0ce6` (197 unpushed commits on main), fixed on main through the merges that follow it.
Rules: the thermo-nuclear code quality SKILL.md, plus `ui/ux`. Behaviour was frozen except for bugs and UI/UX findings, listed below.

## Counts

| severity | found | confirmed | fixed | deferred |
|---|---|---|---|---|
| critical | 13 | 12 | 12 | 1 |
| high | 64 | 61 | 61 | 3 |
| medium | 141 | 121 | 121 | 20 |
| low | 68 | 51 | 51 | 17 |
| total | 286 | 245 | 245 | 41 |

286 findings entered the ledger from 26 finders (14 directory slices, 3 cross-cutting sweeps, 4 unpushed-commit reviewers, crash, scroll, 3 UI/UX walkers). 245 survived verification. 245 are fixed; 41 are deferred (all rejected in verification). The 6 confirmed-but-deferred findings were fixed after the v2.0.0 tag, in the post/* merges.

## How it was verified

- `./gradlew testDebugUnitTest`, `lintDebug` and `compileDebugAndroidTestKotlin` ran green on main after every branch merge (19 merges).
- The stats-screen crash (F012) is pinned by `VaultStatsKeyTest`, which calls Compose's real `canBeSavedToBundle` predicate on the old key (rejects) and the new key (accepts). The debug build from merged main installs and launches clean on the attached phone (empty crash buffer). Opening the sheet by hand on the device is left to Stan; no agent drives the phone.
- Scroll findings were measured from the Compose compiler report (`app/build/compose_compiler/*.txt`: `PostCard` restartable-skippable but its `actions` parameter never equal) and the state reads in composition cited per finding; phone-side profiling is off the table by standing instruction, so the fix is verified by code and tests (read-mark test under ID filter, rows cached from the combine block), not by frame timing.
- UI/UX findings were verified against the source, with the user-visible change named per finding; they were not exercised on the device.
- Instrumented tests compile but did not run (no agent-driven device runs).

## Behaviour changed

- F001 `CatalogFlowTest.kt` (bug): openCatalog() opens the Boards tab first.
- F002 `HiddenThreadFlowTest.kt` (bug): openBoardsTab() before the board-title wait.
- F003 `AndroidManifest.xml` (bug): android:allowBackup=false; manifest matches the docs.
- F004 `BookmarkDao.kt` (bug): isBookmarked distinctUntilChanged (second half of the fix).
- F005 `TabScaffoldSlots.kt` (bug): Claim moved into the DisposableEffect body; SideEffect removed.
- F006 `PostHtmlParser.kt` (bug): codePoint() guards both numeric-entity branches with Character.isValidCodePoint; test added.
- F007 `MediaVaultRepositoryImpl.kt` (bug): rescan() carries md5/phash/pixelSize across the rebuild; test added.
- F009 `ThreadScreen.kt` (bug): Real templates for MoreReplies/Filtered keys.
- F010 `ThreadScreen.kt` (bug): Post share link is a real URL again.
- F012 `VaultStatsSheet.kt` (bug): Stats thread rows keyed on board/threadNo via lazyKey(); VaultStatsKeyTest pins the Bundle predicate.
- F016 `SettingsDataStore.kt` (bug): Legacy fold keyed off Settings serializer element names; removes exactly those keys instead of clear().
- F017 `SettingsRows.kt` (ui/ux): SwitchRow toggleable with Role.Switch; TextRow/NavigationRow carry Role.Button.
- F019 `Updater.kt` (bug): userActionShown flag gates the retry after a user cancel.
- F020 `UiState.kt` (7 orchestration/atomicity): LoadableFlow cancels the in-flight job before launching; test added.
- F023 `BookmarkRefreshScheduler.kt` (bug): Scheduler reads bookmarkRefreshMinutes and re-enqueues with UPDATE.
- F024 `BookmarkRefreshWorker.kt` (bug): Worker honours bookmarkNotifications before notifying.
- F037 `BookmarksList.kt` (bug): snapshotResult is a held StateFlow, cleared once shown; test added.
- F038 `BookmarksList.kt` (ui/ux): Loading skeleton before the empty state.
- F040 `HomeScreen.kt` (bug): Same bug as F142; fixed once.
- F043 `MediaScreen.kt` (bug): Second LongPress haptic removed; one buzz per long-press.
- F046 `VideoPage.kt` (bug): player added as key to the three config effects.
- F047 `ViewerChrome.kt` (ui/ux): Overflow menu holds auto-advance, PiP, delete and open-thread.
- F049 `FiltersSection.kt` (ui/ux): Dialog state keyed by filter id in rememberSaveable; fields survive rotation.
- F052 `PostCard.kt` (ui/ux): Custom accessibility actions on card, thumbnail and backlinks.
- F053 `PostCard.kt` (ui/ux): posterIdTextColor guarantees 4.5:1; test over all hues.
- F055 `ThreadScreen.kt` (bug): Scroll effect keyed on scrollTarget alone.
- F056 `ThreadScreen.kt` (bug): Refresh-error snackbar launched on the screen scope.
- F061 `ThreadViewModel.kt` (bug): Read mark frozen under ID filter and tree view; test added.
- F062 `VaultDedupSheet.kt` (ui/ux): Per-group Keep selected raises the same confirm dialog as Apply all.
- F063 `VaultDedupViewModel.kt` (bug): Apply all honours the kept map; dialog count matches; tests added.
- F079 `BoardsFavouriteFlowTest.kt` (bug): Restoration asserted straight after clearance.
- F094 `SettingsRows.kt` (ui/ux): Disabled rows dim text at 0.38 alpha.
- F101 `Urls.kt` (5 type/boundary): Fragment must start with s= and be non-blank; test added.
- F106 `VaultSyncWorker.kt` (7 orchestration/atomicity): Data-saver refusal returns Result.success().
- F107 `VaultSyncWorker.kt` (7 orchestration/atomicity): VaultSyncSummary.touched drives the worker's skip set.
- F112 `MediaVaultRepositoryImpl.kt` (bug): delete()/purgeTrash() remove video stills; test added.
- F129 `BoardsScreen.kt` (ui/ux): Unfavourite from Boards shows an order-preserving undo snackbar.
- F131 `BoardsViewModel.kt` (ui/ux): Search results ordered by best match; test added.
- F134 `CatalogPane.kt` (ui/ux): Catalog search field requests focus on reveal.
- F135 `CatalogScreen.kt` (ui/ux): Per-state contentDescription on the layout cycler.
- F141 `HistoryViewModel.kt` (bug): Board match guarded on non-empty trimmed query; test added.
- F142 `HomeScreen.kt` (bug): Saved page restored via scrollToPage once pageCount > 0.
- F144 `HomeScreen.kt` (ui/ux): Loading skeleton while boards are null.
- F146 `HomeViewModel.kt` (bug): Undo awaits the removal's Deferred; test added.
- F148 `ReorderableTabRow.kt` (bug): Drag ends when the tab set changes; widths read with getOrNull.
- F149 `ReorderableTabRow.kt` (ui/ux): BoardTab has Role.Tab and selected semantics.
- F150 `DownloadAction.kt` (4 magic/wrappers): saveSuccess/saveError theme colours replace hex literals.
- F151 `MediaFeedViewer.kt` (ui/ux): MediaFeedViewer no longer buzzes before delegating.
- F153 `MediaScreen.kt` (ui/ux): Live viewer gets a badge-counted replies button.
- F155 `MediaShare.kt` (ui/ux): shareMediaFile returns success; MediaScreen shows a failure snackbar.
- F162 `ViewerChrome.kt` (6 canonical layer): Viewer chrome fades use rememberMotionSpec.
- F165 `StorageSection.kt` (ui/ux): Clears report a one-shot result; backup rows disabled while busy; tests added.
- F168 `UpdatesSection.kt` (ui/ux): OnResumeEffect clears needsPermission on return from system settings.
- F169 `ExternalLinkDialog.kt` (ui/ux): Trust is a checkbox in the link dialog.
- F176 `ThreadTopBar.kt` (ui/ux): Subtitle is a FlowRow of chips.
- F178 `ThreadScreen.kt` (ui/ux): searchOpen is rememberSaveable.
- F179 `ThreadScreen.kt` (ui/ux): jumpTo teleports then animates the last stretch.
- F183 `ThreadsScreen.kt` (ui/ux): searching/confirmClear are rememberSaveable; search field survives tab switches and rotation.
- F188 `VaultExplorer.kt` (ui/ux): Seed state is VaultBody.Loading; explorer shows a skeleton first.
- F189 `VaultExplorer.kt` (6 canonical layer): No-access and empty states use EmptyState.
- F192 `VaultExplorer.kt` (ui/ux): Modifier.selectable with Checkbox role while selecting.
- F197 `VaultScreen.kt` (ui/ux): Statistics and Duplicates moved to an overflow menu.
- F198 `VaultScreen.kt` (ui/ux): stats is null until read; sheet shows a spinner.
- F204 `AppNavHost.kt` (bug): One NavController.push(route) with launchSingleTop.
- F238 `VaultMeta.kt` (3 design cleanliness): KDoc corrected; test renamed.
- F242 `WatchedThreadsWidget.kt` (ui/ux): Widget refresh target grown to ~40dp.
- F259 `MediaScreen.kt` (ui/ux): Share button labelled with content and state descriptions.
- F262 `FiltersSection.kt` (3 design cleanliness): Explicit if (filter.error != null).
- F265 `ThreadGallerySheet.kt` (ui/ux): Media grid pairs posts with present media; empty state shown.
- F266 `ThreadScreen.kt` (6 canonical layer): ThreadViewModel.retry() shows the loading shell.
- F271 `ThreadViewModel.kt` (ui/ux): Tree depth from retained-ancestor chain; test added.
- F272 `VaultExplorer.kt` (ui/ux): MediaGrid renders emptyText when empty.
- F277 `VaultScreen.kt` (ui/ux): Import default names resolved from resources.

- F130 `BoardsViewModel.kt` (ui/ux): hiddenBoards is the only visibility set; a category toggle hides or shows every board in it; a board added to a hidden category later is shown until hidden. Legacy hiddenCategories folded in on first board load.

## Deferred

All six confirmed-but-deferred findings (F075, F087, F088, F090, F104, F130) were fixed after v2.0.0: Room v11 migration (md5 index, dead bookmark columns), VideoFormats bound as a query parameter, injectable queue scope, domain as the leaf layer with `DomainLayerTest` guarding the direction, and hidden boards as the one visibility set.

Plus 41 findings rejected in verification (listed in the ledger with the verifier's reason).

## Root causes worth a structural fix

- **core <-> domain import cycle** (F104, fixed post-2.0.0). `core/util/DataResult`, `core/text/PostText` and `core/vault/VaultPaths` are imported by domain while domain models are imported by core. Splitting those three into a leaf package would let the layers be enforced.
- **Room schema debt** (F087, F090, fixed post-2.0.0: v11). `bookmarks` still carries `newReplies`/`unreadCount`/`lastSeenPostNo` columns nothing reads, and `saved_media.md5` has no index for the dedup scan. Both need a v11 migration; the domain model already dropped the fields.
- **Hidden boards vs hidden categories** (F130, fixed post-2.0.0). Two overlapping sets in Settings with a special case in `BoardVisibility.toggleBoard`; collapsing them needs a settings migration and a decision on boards added to a hidden category later.
- **Unqualified dispatcher injection.** Two branches independently added `CoroutineDispatcher` constructor parameters with Kotlin defaults, which Hilt ignores. `@ComputeDispatcher`/`@IoDispatcher` in `core/di/DispatchersModule.kt` are now the only way to inject one; keep it that way.
- **Interface defaults as fakes.** `MediaVaultRepository` and friends carried no-op default bodies so test fakes compiled; every member is abstract now and `fake/FakeMediaVault` is the inert base. New members must be added to the fake, which is the point.
- **Feature top bars owned by the shell.** `ThreadsScreen` built History's app bar inline while Bookmarks and Catalog export theirs; F184 moves it. The pattern to keep: a feature exports its `*Menu`/`*TopBar` composable, the shell only places it.

## UI/UX findings by screen

### Boards

- F129 `BoardsScreen.kt:115` [medium] Fixed
- F131 `BoardsViewModel.kt:130` [medium] Fixed

### Bookmarks (Threads > Watched)

- F038 `BookmarksList.kt:115` [high] Fixed
- F257 `BookmarksList.kt:190` [low] Fixed

### Catalog / Home: layout cycler

- F135 `CatalogScreen.kt:92` [medium] Fixed

### Catalog / Home: search

- F134 `CatalogPane.kt:158` [medium] Fixed

### Home

- F144 `HomeScreen.kt:116` [medium] Fixed

### Home: tab row

- F149 `ReorderableTabRow.kt:238` [medium] Fixed

### Media viewer (live thread and vault)

- F151 `MediaFeedViewer.kt:243` [medium] Fixed

### Media viewer (live thread)

- F153 `MediaScreen.kt:145` [medium] Fixed

### Media viewer (share)

- F259 `MediaScreen.kt:184` [low] Fixed

### Media viewer / Vault (share)

- F155 `MediaShare.kt:44` [medium] Fixed

### Media viewer chrome / Vault viewer

- F047 `ViewerChrome.kt:111` [high] Fixed

### Settings

- F168 `UpdatesSection.kt:38` [medium] Fixed

### Settings (all sections)

- F017 `SettingsRows.kt:42` [high] Fixed

### Settings > Filters

- F049 `FiltersSection.kt:180` [high] Fixed

### Settings > Media, Settings (all switch rows)

- F094 `SettingsRows.kt:107` [medium] Fixed

### Settings > Storage

- F165 `StorageSection.kt:45` [medium] Fixed

### Shell

- F093 `SettingsRows.kt:42` [medium] Deferred (rejected in verification: Same defect, same lines, same fix as F017 (which additionally covers TextRow/NavigationRow roles). Duplicate, not an independent finding.)

### Thread

- F177 `ThreadTopBar.kt:96` [medium] Deferred (rejected in verification: Duplicate of F176, same lines, same defect, same remedy; the only extra content is commit archaeology. Keep F176, which additionally catches the sub-48dp untyped tap target.)
- F179 `ThreadScreen.kt:191` [medium] Fixed

### Thread (tree view + poster-ID filter)

- F271 `ThreadViewModel.kt:599` [low] Fixed

### Thread view: PostCard

- F052 `PostCard.kt:117` [high] Fixed

### Thread view: PostCard poster-ID pill

- F053 `PostCard.kt:152` [high] Fixed

### Thread view: external link dialog

- F169 `ExternalLinkDialog.kt:27` [medium] Fixed

### Thread view: gallery sheet

- F265 `ThreadGallerySheet.kt:62` [low] Fixed

### Thread view: in-thread search

- F178 `ThreadScreen.kt:106` [medium] Fixed

### Thread view: top bar

- F176 `ThreadTopBar.kt:79` [medium] Fixed

### Threads

- F183 `ThreadsScreen.kt:73` [medium] Fixed

### Vault

- F197 `VaultScreen.kt:297` [medium] Fixed

### Vault > Statistics sheet

- F198 `VaultScreen.kt:406` [medium] Fixed

### Vault dedup sheet

- F062 `VaultDedupSheet.kt:265` [high] Fixed

### Vault explorer

- F188 `VaultExplorer.kt:117` [medium] Fixed

### Vault explorer (media grid)

- F192 `VaultExplorer.kt:503` [medium] Fixed

### Vault → board → thread grid

- F272 `VaultExplorer.kt:203` [low] Fixed

### Vault → import files

- F277 `VaultScreen.kt:735` [low] Fixed

### app widget

- F242 `WatchedThreadsWidget.kt:174` [low] Fixed

## All findings

| id | file | rule | what changed |
|---|---|---|---|
| F001 | CatalogFlowTest.kt:40 | bug | Fixed (c47bd1a) |
| F002 | HiddenThreadFlowTest.kt:31 | bug | Fixed (c47bd1a) |
| F003 | AndroidManifest.xml:23 | bug | Fixed (7da9ba3) |
| F004 | BookmarkDao.kt:33 | bug | Fixed (c4bab94) |
| F005 | TabScaffoldSlots.kt:50 | bug | Fixed (0ceb5ba) |
| F006 | PostHtmlParser.kt:150 | bug | Fixed (85a5aee) |
| F007 | MediaVaultRepositoryImpl.kt:564 | bug | Fixed (aa7b8d7) |
| F008 | ThreadScreen.kt:295 | 2 spaghetti growth | Fixed (55289ed) |
| F009 | ThreadScreen.kt:354 | bug | Fixed (84335cd) |
| F010 | ThreadScreen.kt:418 | bug | Fixed (84335cd) |
| F011 | ThreadViewModel.kt:520 | 6 canonical layer | Fixed (308b61e) |
| F012 | VaultStatsSheet.kt:69 | bug | Fixed (0e6d101) |
| F013 | RoomTest.kt:28 | bug | Deferred (rejected in verification: Not a defect at line 28, that line just builds an in-memory DB for DAO tests, which is exactly what it should do; the claim is a test-coverage gap, and the thermo-nuclear rules cover structure/abstraction/spaghetti, not missing tests. The proposed fix is also wrong as written: app/schemas/dev.stan.yotsuba.core.database.YotsubaDatabase contains only 6.json to 10.json, so MigrationTestHelper.createDatabase(TEST_DB, n) for n in 1..5 (and the end-to-end 1→10) cannot work, no exported schema exists for those versions.) |
| F014 | BoardsFavouriteFlowTest.kt:39 | 5 type/boundary | Fixed (3ee073d) |
| F015 | FlowTestHelpers.kt:10 | 2 spaghetti growth | Fixed (1fc9129) |
| F016 | SettingsDataStore.kt:57 | bug | Fixed (5cdc689) |
| F017 | SettingsRows.kt:42 | ui/ux | Fixed (f566119) |
| F018 | ReducedMotion.kt:29 | 6 canonical layer | Fixed (663fc0a) |
| F019 | Updater.kt:205 | bug | Fixed (f7da10f) |
| F020 | UiState.kt:32 | 7 orchestration/atomicity | Fixed (dd39c05) |
| F021 | WidgetDeepLink.kt:13 | 0 structural simplification | Fixed (38f6e23) |
| F022 | BookmarkRefreshScheduler.kt:24 | 0 structural simplification | Fixed (96ea5fd) |
| F023 | BookmarkRefreshScheduler.kt:33 | bug | Fixed (96ea5fd) |
| F024 | BookmarkRefreshWorker.kt:44 | bug | Fixed (e4f6c47) |
| F025 | MediaVaultRepositoryImpl.kt:58 | 0 structural simplification | Fixed (a7712db) |
| F026 | MediaVaultRepositoryImpl.kt:100 | 6 canonical layer | Fixed (32e517a) |
| F027 | MediaVaultRepositoryImpl.kt:531 | 4 magic/wrappers | Fixed (32e517a) |
| F028 | MediaVaultRepositoryImpl.kt:541 | 7 orchestration/atomicity | Fixed (15cd4c6) |
| F029 | Bookmark.kt:25 | 0 structural simplification | Fixed (b99c7d3) |
| F030 | BoardRepository.kt:3 | 3 design cleanliness | Fixed (8090153) |
| F031 | BookmarkRepository.kt:48 | 5 type/boundary | Fixed (8785181) |
| F032 | BookmarkRepository.kt:52 | 5 type/boundary | Fixed (8785181) |
| F033 | MediaVaultRepository.kt:14 | 5 type/boundary | Fixed (2678792) |
| F034 | MediaVaultRepository.kt:88 | 5 type/boundary | Fixed (2678792) |
| F035 | BoardsViewModel.kt:88 | 5 type/boundary | Fixed (d6099df) |
| F036 | BookmarksList.kt:78 | 6 canonical layer | Fixed (8494f81) |
| F037 | BookmarksList.kt:96 | bug | Fixed (971c953) |
| F038 | BookmarksList.kt:115 | ui/ux | Fixed (1c59105) |
| F039 | BookmarksViewModel.kt:137 | 5 type/boundary | Fixed (14ced7c) |
| F040 | HomeScreen.kt:83 | bug | Fixed (837087d) |
| F041 | HomeScreen.kt:95 | 6 canonical layer | Fixed (b0b3f8b) |
| F042 | ReorderableTabRow.kt:73 | 4 magic/wrappers | Fixed (cebd0e1) |
| F043 | MediaScreen.kt:133 | bug | Fixed (941050a) |
| F044 | MediaScreen.kt:212 | 6 canonical layer | Fixed (bbdbc59) |
| F045 | VideoPage.kt:85 | 0 structural simplification | Fixed (bc2b9da) |
| F046 | VideoPage.kt:141 | bug | Fixed (108b3cc) |
| F047 | ViewerChrome.kt:111 | ui/ux | Fixed (41c62de) |
| F048 | FiltersSection.kt:78 | 6 canonical layer | Fixed (f97f2a4) |
| F049 | FiltersSection.kt:180 | ui/ux | Fixed (635f67e) |
| F050 | PostBody.kt:62 | 0 structural simplification | Fixed (bffa699) |
| F051 | PostBody.kt:90 | 6 canonical layer | Deferred (rejected in verification: Misreads the code: the build at PostBody.kt:90 is inside remember keyed on body/revealedSpoilerIds/revealAll/highlight/quoteLabels/colors/scheme, so it does not rerun on recomposition with unchanged inputs, the claim that it 'repeats on every recomposition triggered by a new ThreadContent instance' is wrong. What remains is one AnnotatedString build per item realization, which is ordinary Compose; the proposed LruCache or VM-side span pipeline adds a cache-key surface and pushes styling into the ViewModel for an unmeasured gain.) |
| F052 | PostCard.kt:117 | ui/ux | Fixed (c658605) |
| F053 | PostCard.kt:152 | ui/ux | Fixed (c658605) |
| F054 | PostCard.kt:207 | 4 magic/wrappers | Deferred (rejected in verification: Same underlying defect as F172 (shared-element ownership inferred from actions.onThumbnailLongPress at PostCard.kt:207), reported twice. F054's remedy is the worse of the two: it bolts a PostCardSurface enum onto PostCardActions, a new field on a data class that is otherwise pure handlers, and demands rebuilding the action sets in ThreadScreen, touching SubThreadPanel.kt:139 too, for a distinction only one line inside PostCard actually needs. That is adding a mode flag, not deleting one. The claim that forPreview()'s KDoc 'decides which' surface is also a misread: that KDoc is about which handlers are inert, not about transitions. Keeping F172 as the canonical finding.) |
| F055 | ThreadScreen.kt:139 | bug | Fixed (bbcc8a1) |
| F056 | ThreadScreen.kt:164 | bug | Fixed (bbcc8a1) |
| F057 | ThreadScreen.kt:233 | 3 design cleanliness | Fixed (55289ed) |
| F058 | ThreadViewModel.kt:169 | 6 canonical layer | Fixed (3b8b911) |
| F059 | ThreadViewModel.kt:464 | 6 canonical layer | Fixed (4c81572) |
| F060 | ThreadViewModel.kt:518 | 2 spaghetti growth | Fixed (308b61e) |
| F061 | ThreadViewModel.kt:524 | bug | Fixed (308b61e) |
| F062 | VaultDedupSheet.kt:265 | ui/ux | Fixed (a34245b) |
| F063 | VaultDedupViewModel.kt:107 | bug | Fixed (a34245b) |
| F064 | VaultExplorer.kt:218 | 5 type/boundary | Deferred (rejected in verification: Duplicate of F187, which reports the same `view: Any` / Triple issue at its origin (VaultExplorer.kt:114) with the fuller fix. Real issue, but reported three times; keeping one.) |
| F065 | VaultScreen.kt:174 | bug | Fixed (609eefb) |
| F066 | VaultScreen.kt:204 | 1 file size | Fixed (9f4849e) |
| F067 | VaultStats.kt:43 | 6 canonical layer | Fixed (22bde01) |
| F068 | VaultViewModel.kt:424 | 5 type/boundary | Fixed (c6420be) |
| F069 | VaultViewModel.kt:435 | 0 structural simplification | Fixed (9389352) |
| F070 | VaultViewModel.kt:541 | 6 canonical layer | Fixed (c6420be) |
| F071 | VaultViewModel.kt:593 | 0 structural simplification | Fixed (c6420be) |
| F072 | AppNavHost.kt:105 | 6 canonical layer | Fixed (38f6e23) |
| F073 | AppNavHost.kt:137 | 2 spaghetti growth | Fixed (111aa10) |
| F074 | AppNavHost.kt:165 | 6 canonical layer | Fixed (111aa10) |
| F075 | MediaDownloadQueueTest.kt:33 | 5 type/boundary | Fixed (b166ca9) |
| F076 | MediaDownloadQueueTest.kt:43 | 6 canonical layer | Fixed (021843c) |
| F077 | ThreadRefreshFailureTest.kt:56 | 6 canonical layer | Fixed (032af93) |
| F078 | 6.json:1 | 5 type/boundary | Deferred (rejected in verification: Facts check out (schemas 6-10 only, migrations 1_2..9_10, no MigrationTestHelper anywhere), but this is a test-coverage/process gap, not something the thermo-nuclear rules flag: no structural regression, no spaghetti, no type/boundary problem in code. Rule 5 is about types and contracts in source, not about exported Room schema JSON. The cited artefact is a generated file and 'line 1' of it anchors nothing. The alternative fix (dropping exportSchema/schemaLocation) would delete the only artefacts a future migration test could use and would change the build's outputs, making the situation worse, not cleaner.) |
| F079 | BoardsFavouriteFlowTest.kt:63 | bug | Fixed (028969c) |
| F080 | TestRepositoryModule.kt:1 | 0 structural simplification | Deferred (rejected in verification: The file is 401 lines, nowhere near the 1k-line threshold the rules flag, and its three parts (seed data, fakes, bindings) are one cohesive concern: the instrumented-test double graph. The proposed fix deletes no complexity, it just spreads the same code across three files, which rule 3 explicitly deprecates. The import-order remark is a cosmetic nit.) |
| F081 | TestRepositoryModule.kt:324 | 6 canonical layer | Deferred (rejected in verification: Line 324 is indeed FakeSettingsRepository, and it does overlap with app/src/test/.../fake/FakeSettings.kt, but the duplicate is 7 trivial lines (a MutableStateFlow plus an update lambda), and the two live in source sets that cannot see each other by design. The proposed remedy is to add a whole new sharedTest source set to app/build.gradle.kts and rewire the Hilt @Binds at line 388 into a @Provides, i.e. build-system churn out of proportion to the payoff, and the classes aren't even identical (FakeSettings takes an `initial` param and has no @Inject/@Singleton, which Hilt needs here). The 'eighth copy' framing also overstates it: the KDoc's remark about ViewModel tests concerns the unit-test source set, not this file. Low-value nit under the rules' own 'don't flood the review with nits' bar.) |
| F082 | TestRepositoryModule.kt:370 | 4 magic/wrappers | Fixed (148f7fa) |
| F083 | FlowTestHelpers.kt:26 | 4 magic/wrappers | Fixed (87b5096) |
| F084 | HistoryClearFlowTest.kt:30 | 2 spaghetti growth | Fixed (87b5096) |
| F085 | VaultFlowTest.kt:33 | 5 type/boundary | Fixed (028969c) |
| F086 | BookmarkDao.kt:60 | 3 design cleanliness | Deferred (rejected in verification: The three UPDATEs are real and overlapping, but the proposed collapse does not delete complexity: it replaces three explicit, self-documenting statements with one statement carrying three nullable params plus a `touchActivity` guard flag, and the finding itself admits the naive version would break behaviour (updateState must leave lastActivityAt untouched; the COALESCE form as written would clobber it, and passing the row's existing value from the caller is a read-modify-write the current single statement avoids). Taste-level restructuring with a behaviour hazard, not a structural regression the rules flag.) |
| F087 | SavedMediaDao.kt:41 | 3 design cleanliness | Fixed (82f1597) |
| F088 | SavedMediaDao.kt:47 | 6 canonical layer | Fixed (93392e3) |
| F089 | SavedMediaDao.kt:49 | 0 structural simplification | Fixed (5f8c89a) |
| F090 | Entities.kt:20 | 0 structural simplification | Fixed (82f1597) |
| F091 | Migrations.kt:6 | 3 design cleanliness | Deferred (rejected in verification: The facts check out (no MigrationTestHelper anywhere, only 6..10.json exported, no destructive fallback at Modules.kt:126-128), but this is a missing-test-coverage gap, not one of the structural/abstraction problems this review's rules flag, and part of the fix is impossible: 1.json..5.json are not recoverable, `git log --all` for those paths returns nothing, so they were never committed.) |
| F092 | SettingsDataStore.kt:35 | 6 canonical layer | Fixed (90ea126) |
| F093 | SettingsRows.kt:42 | ui/ux | Deferred (rejected in verification: Same defect, same lines, same fix as F017 (which additionally covers TextRow/NavigationRow roles). Duplicate, not an independent finding.) |
| F094 | SettingsRows.kt:107 | ui/ux | Fixed (f566119) |
| F095 | SharedMedia.kt:42 | 4 magic/wrappers | Fixed (9e952d2) |
| F096 | ThreadSummaryRow.kt:37 | 5 type/boundary | Fixed (9e952d2) |
| F097 | ReducedMotion.kt:24 | 4 magic/wrappers | Fixed (663fc0a) |
| F098 | Elevation.kt:9 | 3 design cleanliness | Fixed (6533b2a) |
| F099 | ArchiveHosts.kt:33 | 5 type/boundary | Fixed (7f67d73) |
| F100 | Updater.kt:92 | 2 spaghetti growth | Fixed (f7da10f) |
| F101 | Urls.kt:33 | 5 type/boundary | Fixed (8c2a209) |
| F102 | Urls.kt:43 | 6 canonical layer | Fixed (8c2a209) |
| F103 | VaultMeta.kt:67 | 0 structural simplification | Fixed (4edf093) |
| F104 | VaultPosts.kt:66 | 6 canonical layer | Fixed (e27698a) |
| F105 | VideoStills.kt:33 | 4 magic/wrappers | Fixed (4edf093) |
| F106 | VaultSyncWorker.kt:55 | 7 orchestration/atomicity | Fixed (e4f6c47) |
| F107 | VaultSyncWorker.kt:64 | 7 orchestration/atomicity | Fixed (b558d62) |
| F108 | BackupRepositoryImpl.kt:77 | 4 magic/wrappers | Fixed (52b4e3a) |
| F109 | BookmarkRepositoryImpl.kt:117 | 4 magic/wrappers | Fixed (c4bab94) |
| F110 | HistoryRepositoryImpl.kt:64 | 4 magic/wrappers | Fixed (6ea85ee) |
| F111 | Mappers.kt:164 | 6 canonical layer | Fixed (4251c5b) |
| F112 | MediaVaultRepositoryImpl.kt:164 | bug | Fixed (aa7b8d7) |
| F113 | MediaVaultRepositoryImpl.kt:233 | 6 canonical layer | Fixed (a7712db) |
| F114 | MediaVaultRepositoryImpl.kt:237 | 6 canonical layer | Fixed (15cd4c6) |
| F115 | MediaVaultRepositoryImpl.kt:238 | 6 canonical layer | Fixed (5bce03f) |
| F116 | MediaVaultRepositoryImpl.kt:238 | 6 canonical layer | Deferred (rejected in verification: Duplicate: it restates F115 (mime table copied from MediaShare.mimeOf) and F114 (isVideo predicate in four places) as one finding at an adjacent line (239 vs the actual when at 238), adding no distinct defect. Keep F114 and F115.) |
| F117 | MediaVaultRepositoryImpl.kt:490 | 0 structural simplification | Fixed (15cd4c6) |
| F118 | SavedMediaMappers.kt:98 | 5 type/boundary | Fixed (15cd4c6) |
| F119 | VaultDedupRepositoryImpl.kt:34 | 4 magic/wrappers | Fixed (5f8c89a) |
| F120 | Bookmark.kt:27 | 3 design cleanliness | Fixed (b99c7d3) |
| F121 | Bookmark.kt:37 | 6 canonical layer | Fixed (394dc6b) |
| F122 | Filter.kt:28 | 4 magic/wrappers | Fixed (8b75bc3) |
| F123 | VaultEntry.kt:48 | 6 canonical layer | Fixed (4515803) |
| F124 | BookmarkRepository.kt:3 | 3 design cleanliness | Deferred (rejected in verification: Duplicate of F030 at the same file and line, and less accurate (it counts MaintenanceRepository at 14 unused imports; the file has 15 imports and uses none). Its only extra content, the DAO files, is folded into the F030 fix note.) |
| F125 | BookmarkRepository.kt:51 | 3 design cleanliness | Fixed (8785181) |
| F126 | MediaVaultRepository.kt:23 | 5 type/boundary | Deferred (rejected in verification: The issue is real but it is a strict subset of F033, which flags the same two defaults at the same lines with the same remedy and covers the other nine besides. Reporting it separately splits one change across two findings.) |
| F127 | MediaVaultRepository.kt:47 | 5 type/boundary | Deferred (rejected in verification: Duplicate of F033: identical member list, identical evidence, identical remedy (abstract members plus a shared FakeMediaVaultRepository base), plus F034's overload collapse. Nothing here is not already covered.) |
| F128 | VaultDedupRepository.kt:31 | 4 magic/wrappers | Fixed (3e2ea0b) |
| F129 | BoardsScreen.kt:115 | ui/ux | Fixed (23a531a) |
| F130 | BoardsViewModel.kt:92 | 2 spaghetti growth | Fixed (0fad2b0) |
| F131 | BoardsViewModel.kt:130 | ui/ux | Fixed (2ab05cf) |
| F132 | BookmarksList.kt:166 | 2 spaghetti growth | Fixed (14ced7c) |
| F133 | BookmarksViewModel.kt:132 | 0 structural simplification | Fixed (843881d) |
| F134 | CatalogPane.kt:158 | ui/ux | Fixed (88f7d64) |
| F135 | CatalogScreen.kt:92 | ui/ux | Fixed (88f7d64) |
| F136 | CatalogViewModel.kt:53 | 6 canonical layer | Fixed (10a0007) |
| F137 | CatalogViewModel.kt:83 | 0 structural simplification | Fixed (10a0007) |
| F138 | CatalogViewModel.kt:86 | 6 canonical layer | Fixed (10a0007) |
| F139 | CatalogViewModel.kt:97 | 6 canonical layer | Fixed (10a0007) |
| F140 | HistoryList.kt:140 | 6 canonical layer | Fixed (12032b2) |
| F141 | HistoryViewModel.kt:62 | bug | Fixed (2de6c1e) |
| F142 | HomeScreen.kt:84 | bug | Fixed (837087d) |
| F143 | HomeScreen.kt:98 | 4 magic/wrappers | Fixed (b0b3f8b) |
| F144 | HomeScreen.kt:116 | ui/ux | Fixed (e771e2b) |
| F145 | HomeScreen.kt:136 | 2 spaghetti growth | Fixed (c5a1276) |
| F146 | HomeViewModel.kt:45 | bug | Fixed (b148013) |
| F147 | HomeViewModel.kt:45 | 7 orchestration/atomicity | Fixed (b148013) |
| F148 | ReorderableTabRow.kt:135 | bug | Fixed (86f4203) |
| F149 | ReorderableTabRow.kt:238 | ui/ux | Fixed (768017e) |
| F150 | DownloadAction.kt:110 | 4 magic/wrappers | Fixed (d298bde) |
| F151 | MediaFeedViewer.kt:243 | ui/ux | Fixed (941050a) |
| F152 | MediaScreen.kt:115 | 2 spaghetti growth | Fixed (986ba63) |
| F153 | MediaScreen.kt:145 | ui/ux | Fixed (41c62de) |
| F154 | MediaShare.kt:26 | 6 canonical layer | Fixed (5bce03f) |
| F155 | MediaShare.kt:44 | ui/ux | Fixed (5a8e258) |
| F156 | MediaViewModel.kt:79 | 4 magic/wrappers | Fixed (78bc6ed) |
| F157 | MediaViewModel.kt:111 | 5 type/boundary | Fixed (6e96192) |
| F158 | MediaViewModel.kt:128 | 7 orchestration/atomicity | Fixed (1104126) |
| F159 | MediaViewModel.kt:173 | 6 canonical layer | Deferred (rejected in verification: Misreads the code: VaultViewModel's omission of holdToSave is deliberate and documented ('Saving does not apply here', VaultViewModel.kt:249), not silent drift. What remains is a four-line data-class construction in two places; a companion factory with a defaulted flag is a thin wrapper, not a canonical-layer violation.) |
| F160 | MediaViewModel.kt:210 | 6 canonical layer | Fixed (6e96192) |
| F161 | SoundTrack.kt:45 | 6 canonical layer | Fixed (bc2b9da) |
| F162 | ViewerChrome.kt:69 | 6 canonical layer | Fixed (9f6beff) |
| F163 | FiltersSection.kt:314 | 6 canonical layer | Fixed (9e83bdb) |
| F164 | StorageSection.kt:36 | 5 type/boundary | Fixed (ea5eae1) |
| F165 | StorageSection.kt:45 | ui/ux | Fixed (a95a3cd) |
| F166 | SettingsSectionScreen.kt:53 | 7 orchestration/atomicity | Fixed (212fb3b) |
| F167 | SettingsViewModel.kt:45 | 4 magic/wrappers | Fixed (ea5eae1) |
| F168 | UpdatesSection.kt:38 | ui/ux | Fixed (0016c08) |
| F169 | ExternalLinkDialog.kt:27 | ui/ux | Fixed (f91a422) |
| F170 | PostCard.kt:75 | 5 type/boundary | Fixed (55289ed) |
| F171 | PostCard.kt:86 | 2 spaghetti growth | Fixed (55289ed) |
| F172 | PostCard.kt:207 | 4 magic/wrappers | Fixed (55289ed) |
| F173 | PostCard.kt:208 | 4 magic/wrappers | Deferred (rejected in verification: The stacking is real (208-211 applied at 223-224), but the finding is speculation dressed as a defect: it asserts Compose 'is specified for one shared-content state per node' without evidence, and names no concrete misbehaviour, the two keys can never match at once, since the catalog is not composed during a thread->viewer transition and the viewer never publishes the thumbnail key. The proposed fix also fails the behaviour bar: `cameFromCatalog` does not exist anywhere in the tree, and having the catalog publish media.fullUrl means widening the catalog's data path (CatalogPane.kt:303 has only thumbnailUrl), which changes what animates on both screens.) |
| F174 | PostCard.kt:329 | 6 canonical layer | Deferred (rejected in verification: Misreads the code: the two mappings are not the same. PostCard tints Queued Color.White while DownloadAction uses White.copy(alpha = 0.7f); PostCard's Failed icon is ErrorOutline, DownloadAction's is Download; DownloadAction also has a null branch PostCard's non-null parameter lacks. A shared composable with the proposed signature would change the badge's Queued tint and Failed glyph, and parameterising those differences away leaves only two shared colour constants, not enough to earn the extra component.) |
| F175 | QuotePreviewSheet.kt:71 | 2 spaghetti growth | Fixed (d91e0d4) |
| F176 | ThreadTopBar.kt:79 | ui/ux | Fixed (f91a422) |
| F177 | ThreadTopBar.kt:96 | ui/ux | Deferred (rejected in verification: Duplicate of F176, same lines, same defect, same remedy; the only extra content is commit archaeology. Keep F176, which additionally catches the sub-48dp untyped tap target.) |
| F178 | ThreadScreen.kt:106 | ui/ux | Fixed (bbcc8a1) |
| F179 | ThreadScreen.kt:191 | ui/ux | Fixed (b20afd7) |
| F180 | ThreadScreen.kt:348 | 3 design cleanliness | Fixed (b20afd7) |
| F181 | ThreadViewModel.kt:187 | 7 orchestration/atomicity | Deferred (rejected in verification: The code is at line 187 as cited, but the proposed fix breaks an existing test: ThreadViewModelTest.kt:516-523 drives onVisiblePostsChanged and asserts env.history.readMark/env.bookmarks.seen after only dispatcher.scheduler.runCurrent(), which never advances virtual time, so a delay(500) inside collectLatest would leave the mark unwritten. Beyond that it is a micro-optimization of write volume with identical final state, rule 7 explicitly says not to over-index on those, and it introduces a real behavioural edge (leaving the thread within 500ms of the last scroll now drops the read mark), so it is not the behaviour-preserving cleanup it claims.) |
| F182 | ThreadViewModel.kt:543 | 6 canonical layer | Fixed (e3b9a73) |
| F183 | ThreadsScreen.kt:73 | ui/ux | Fixed (1997938) |
| F184 | ThreadsScreen.kt:99 | 6 canonical layer | Fixed (a464b88) |
| F185 | VaultDedupSheet.kt:311 | 4 magic/wrappers | Fixed (a34245b) |
| F186 | VaultEntrySheet.kt:76 | 6 canonical layer | Fixed (34ddfaa) |
| F187 | VaultExplorer.kt:114 | 5 type/boundary | Fixed (9ebe79d) |
| F188 | VaultExplorer.kt:117 | ui/ux | Fixed (9389352) |
| F189 | VaultExplorer.kt:131 | 6 canonical layer | Fixed (9389352) |
| F190 | VaultExplorer.kt:340 | 6 canonical layer | Fixed (34ddfaa) |
| F191 | VaultExplorer.kt:483 | 5 type/boundary | Deferred (rejected in verification: Duplicate of F187 (same `view: Any` parameters, same fix, cited at MediaGrid rather than the origin).) |
| F192 | VaultExplorer.kt:503 | ui/ux | Fixed (9ebe79d) |
| F193 | VaultExplorer.kt:604 | 6 canonical layer | Fixed (34ddfaa) |
| F194 | VaultScreen.kt:113 | 6 canonical layer | Deferred (rejected in verification: The claimed symptom does not hold. The activity declares android:configChanges="orientation\|screenSize\|screenLayout\|…" (AndroidManifest.xml:32), so rotation does not recreate it, and the nav bar↔rail switch is an `if` block that is a *sibling* of the Scaffold inside one Row (AppNavHost.kt:112-127), the NavHost/VaultScreen subtree keeps its composition identity, so the `remember`ed flags survive. With the stated user-visible bug gone, what remains is a preference for hoisting two transient sheet booleans into the ViewModel, which adds a sealed VaultSheet type, a state flow and a UiState field for no behaviour gain.) |
| F195 | VaultScreen.kt:193 | 2 spaghetti growth | Fixed (9f4849e) |
| F196 | VaultScreen.kt:242 | 1 file size | Deferred (rejected in verification: Duplicate of F066: same file, same VaultScreen 95-461 span, same topBar/actions block, same proposed extraction into VaultTopBar with ImportMenu/SyncMenu. It cites line 242 (`actions = {`) instead of 204 (`topBar = {`), but that is a nested part of the very block F066 already covers; reporting it twice adds nothing actionable.) |
| F197 | VaultScreen.kt:297 | ui/ux | Fixed (9f4849e) |
| F198 | VaultScreen.kt:406 | ui/ux | Fixed (22bde01) |
| F199 | VaultStatsSheet.kt:49 | 4 magic/wrappers | Fixed (9ebe79d) |
| F200 | VaultStatsSheet.kt:217 | 6 canonical layer | Fixed (34ddfaa) |
| F201 | VaultViewModel.kt:425 | 5 type/boundary | Deferred (rejected in verification: Duplicate of F068 (same 6-arity array combine and unchecked casts at VaultViewModel.kt:424-433); F068 carries the simpler fold that keeps the existing Editing/Activity structure.) |
| F202 | VaultViewModel.kt:471 | 7 orchestration/atomicity | Fixed (22bde01) |
| F203 | VaultViewModel.kt:643 | 0 structural simplification | Fixed (7223e4e) |
| F204 | AppNavHost.kt:111 | bug | Fixed (57126f4) |
| F205 | AppNavHost.kt:164 | 6 canonical layer | Deferred (rejected in verification: Duplicate of F074: same file, same four lambdas (164-180), same rule and same proposed move into MotionSpecs.kt. Reporting it twice adds nothing.) |
| F206 | AppNavHost.kt:305 | 4 magic/wrappers | Fixed (57126f4) |
| F207 | Route.kt:43 | 6 canonical layer | Fixed (f5b30b6) |
| F208 | strings.xml:6 | 3 design cleanliness | Fixed (e7a3c4f) |
| F209 | strings.xml:122 | 6 canonical layer | Fixed (e7a3c4f) |
| F210 | BackupRepositoryImplTest.kt:97 | 7 orchestration/atomicity | Fixed (2e631f6) |
| F211 | BoardsViewModelTest.kt:64 | 4 magic/wrappers | Fixed (f70f12d) |
| F212 | CatalogViewModelTest.kt:49 | 6 canonical layer | Fixed (b570ebd) |
| F213 | HistoryViewModelTest.kt:95 | 6 canonical layer | Fixed (2de6c1e) |
| F214 | MediaViewModelTest.kt:92 | 6 canonical layer | Fixed (eb96c05) |
| F215 | ThreadViewModelTest.kt:526 | 2 spaghetti growth | Fixed (032af93) |
| F216 | VaultViewModelTest.kt:168 | 0 structural simplification | Fixed (ee9c14d) |
| F217 | NetworkErrorMappingTest.kt:16 | 6 canonical layer | Fixed (46640ba) |
| F218 | VaultPathsTest.kt:58 | 6 canonical layer | Fixed (46640ba) |
| F219 | ci.yml:56 | 7 orchestration/atomicity | Deferred (rejected in verification: Lines 56/59/65/68 are as cited, but this isn't a structural defect the rules flag. Named per-check steps are the direct, boring form: each failure is attributed to a step in the GitHub UI, and collapsing them into one `--continue` invocation trades that away for a modest daemon-warm-up saving. Rule 7 targets orchestration that makes an implementation brittle or leaves state half-applied; sequential CI checks on one runner do neither, and Gradle would still run these tasks in dependency order inside a single invocation anyway. Taste nit at best, a signal regression at worst.) |
| F220 | .gitignore:1 | 4 magic/wrappers | Deferred (rejected in verification: Out of scope for this review: the skill flags structural code-quality problems (abstractions, spaghetti branching, file size, layer boundaries), not repo hygiene in .gitignore. It is also not a 'magic/wrappers' issue, and the cited line 1 ('.gradle/') has nothing to do with the claim. The factual core is thin too: .idea/ is deliberately partially tracked via its own .idea/.gitignore, so the untracked modules.xml/*.iml is a housekeeping preference, not a defect, and no code behaviour or maintainability of the app is affected.) |
| F221 | SettingsFlowTest.kt:39 | 3 design cleanliness | Fixed (028969c) |
| F222 | BackupModule.kt:14 | 6 canonical layer | Fixed (58fc08d) |
| F223 | DownloadedMediaDao.kt:3 | 4 magic/wrappers | Fixed (5f8c89a) |
| F224 | DownloadedMediaDao.kt:17 | 3 design cleanliness | Deferred (rejected in verification: The cited line (DownloadedMediaDao.kt:17) is the one place the entity IS used; the only actionable part, unused `DownloadedMediaEntity` imports, lives at line 9 of four other files, and those files also carry three other unused entity imports each (a shared copy-pasted import block), so the finding both mislocates and under-describes it. Unused imports are a lint-level style nit, and the rest of the finding is 'schedule for deletion later', not a present defect.) |
| F225 | DHash.kt:64 | 4 magic/wrappers | Fixed (14b4114) |
| F226 | MediaThumbnail.kt:5 | 3 design cleanliness | Fixed (9e952d2) |
| F227 | StateViews.kt:57 | 5 type/boundary | Fixed (9e952d2) |
| F228 | StateViews.kt:113 | 5 type/boundary | Fixed (9e952d2) |
| F229 | MotionSpecs.kt:47 | 4 magic/wrappers | Deferred (rejected in verification: The duplication is real but trivial (three tween lines against two different scope receivers), and the proposed fix makes the code less legible, not more: a nullable Triple<FiniteAnimationSpec<Float>, FiniteAnimationSpec<IntOffset>, FiniteAnimationSpec<Float>> drops the named parameters (fadeInSpec/placementSpec/fadeOutSpec) that make the call sites readable and encodes the reduced-motion decision as a null. That trades boring, direct code for indirection, which rule 4 argues against. Taste nit at low severity.) |
| F230 | Color.kt:21 | 5 type/boundary | Fixed (6533b2a) |
| F231 | Theme.kt:104 | 3 design cleanliness | Fixed (663fc0a) |
| F232 | DispatchersModule.kt:14 | 5 type/boundary | Fixed (58fc08d) |
| F233 | SoundPost.kt:37 | 4 magic/wrappers | Fixed (7383bde) |
| F234 | ArchiveHosts.kt:49 | 2 spaghetti growth | Deferred (rejected in verification: ArchiveHosts.kt is 51 lines long, so line 232 does not exist and the nearby real code (the WAROSU arm at line 49) does not support the claim. The file is a single small lookup table with one two-arm `when`, explicitly documented as the extension hook for a Fuuka scraper; that is not ad-hoc spaghetti growth the rules would flag. WAROSU is also live in `threadUrl` (ThreadViewModel.kt:148 builds the browser link from it), and the proposed split into a `webOnly` map would rewrite ArchiveHostsTest assertions and change `sourceFor`'s contract for 13 boards for no maintainability gain. Taste nit at a wrong line.) |
| F235 | NetworkMonitor.kt:49 | 4 magic/wrappers | Fixed (7f67d73) |
| F236 | GithubReleases.kt:70 | 5 type/boundary | Fixed (45606b6) |
| F237 | Urls.kt:23 | 6 canonical layer | Deferred (rejected in verification: Duplicate of F102, same duplication, same four sites, same proposed fix, just cited at Urls.kt:23 instead of :43 and with a weaker write-up. Reporting the same extraction twice is exactly the nit-flooding the rules warn against; F102 carries the finding.) |
| F238 | VaultMeta.kt:32 | 3 design cleanliness | Fixed (4edf093) |
| F239 | VaultPosts.kt:124 | 3 design cleanliness | Fixed (4edf093) |
| F240 | VideoStills.kt:35 | bug | Fixed (4edf093) |
| F241 | WatchedThreadsWidget.kt:125 | 6 canonical layer | Fixed (5d71650) |
| F242 | WatchedThreadsWidget.kt:174 | ui/ux | Fixed (5d71650) |
| F243 | BookmarkRefreshWorker.kt:73 | 2 spaghetti growth | Fixed (e4f6c47) |
| F244 | VaultSyncWorker.kt:83 | 3 design cleanliness | Fixed (96ea5fd) |
| F245 | BookmarkRepositoryImpl.kt:29 | 5 type/boundary | Fixed (52b4e3a) |
| F246 | Mappers.kt:71 | 4 magic/wrappers | Fixed (90dad30) |
| F247 | Mappers.kt:168 | 4 magic/wrappers | Fixed (4251c5b) |
| F248 | Mappers.kt:240 | 5 type/boundary | Fixed (6ea85ee) |
| F249 | MediaVaultRepositoryImpl.kt:375 | 4 magic/wrappers | Deferred (rejected in verification: The observation is right, MediaVaultRepository.kt:88-91 gives the 2-arg form a default body that silently drops `skip`, but the proposed fix does not compile as claimed. Roughly ten fakes override only the 1-arg form (VaultViewModelTest.kt:113, MediaViewModelTest.kt:121, ThreadViewModelTest.kt:155, BookmarksViewModelTest.kt:97, ThreadQuoteTapTest.kt:71, ThreadRefreshFailureTest.kt:69, DedupQueueTest.kt:41, VaultDedupViewModelTest.kt:68, MediaDownloadQueueTest.kt:61, androidTest TestRepositoryModule.kt:313); collapsing to a single 2-param method makes every one of those a non-override and breaks the test and androidTest source sets. The evidence's own claim that existing call sites compile unchanged is false.) |
| F250 | SavedMediaMappers.kt:99 | 5 type/boundary | Deferred (rejected in verification: Duplicate of F118 at the same expression (SavedMediaMappers.kt:98-99) with the same fix; on its own, dropping two !! is a taste-level nit that the rules would not flag separately.) |
| F251 | MediaItem.kt:29 | 3 design cleanliness | Fixed (f5f2056) |
| F252 | PostGraph.kt:15 | 4 magic/wrappers | Fixed (f5f2056) |
| F253 | BookmarkRepository.kt:41 | 5 type/boundary | Fixed (86c5402) |
| F254 | MediaVaultRepository.kt:114 | 5 type/boundary | Deferred (rejected in verification: Real but duplicative: renameThread/mergeThreads are two of the eleven defaults already itemised at the same lines by F033, whose fix (abstract members plus a typed VaultError.Unsupported) is this finding's fix. The finding also concedes the English string never reaches a user, so nothing survives that F033 does not already carry.) |
| F255 | BookmarksList.kt:101 | 6 canonical layer | Fixed (01626af) |
| F256 | BookmarksList.kt:111 | 3 design cleanliness | Deferred (rejected in verification: Misreads the code. OnResumeEffect wraps LifecycleResumeEffect, whose block runs when the effect enters composition while the owner is already RESUMED (the lifecycle observer is replayed to the current state), not only on a foreground return. The Crossfade in ThreadsScreen.kt:137-150 disposes/recreates BookmarksList when the segment changes, so switching back to WATCHED does re-fire onScreenVisible, and the 60 s throttle guards exactly that path. Both comments are accurate.) |
| F257 | BookmarksList.kt:190 | ui/ux | Fixed (d95fc39) |
| F258 | BookmarksViewModel.kt:33 | 5 type/boundary | Fixed (843881d) |
| F259 | MediaScreen.kt:184 | ui/ux | Fixed (45b1030) |
| F260 | VideoPage.kt:112 | 6 canonical layer | Deferred (rejected in verification: The lifecycle-observer half is the same defect as F161 (already confirmed); what is left is a six-line ExoPlayer.Builder block, and the proposed rememberLoopingPlayer(uri: String?) returns a nullable player that VideoPage, which needs a non-null player for PlayerSurface, repeatMode mutation and prepare() on retry, would have to force-unwrap, making the type boundary worse rather than better.) |
| F261 | FiltersSection.kt:80 | 3 design cleanliness | Fixed (9e83bdb) |
| F262 | FiltersSection.kt:158 | 3 design cleanliness | Fixed (9e83bdb) |
| F263 | UpdatesSection.kt:30 | 3 design cleanliness | Fixed (db2474a) |
| F264 | UpdatesSection.kt:33 | 3 design cleanliness | Fixed (db2474a) |
| F265 | ThreadGallerySheet.kt:62 | ui/ux | Fixed (f91a422) |
| F266 | ThreadScreen.kt:230 | 6 canonical layer | Fixed (66629fc) |
| F267 | ThreadScreen.kt:309 | 3 design cleanliness | Fixed (66629fc) |
| F268 | ThreadScreen.kt:415 | 6 canonical layer | Fixed (66629fc) |
| F269 | ThreadViewModel.kt:89 | 5 type/boundary | Fixed (e3b9a73) |
| F270 | ThreadViewModel.kt:290 | 6 canonical layer | Deferred (rejected in verification: Thin-abstraction nit, not a rule-6 violation. The ViewModel repetition is three field assignments at two sites in one file, constructing two different domain types; a ThreadSummary value object would add indirection without deleting a concept (rule 4 explicitly warns against this). The three displayTitle getters are one-liners with genuinely different inputs and fallbacks (CatalogThread reads PostText.plainText and falls back to "#$no", the other two to "/$board/$threadNo"), and writeSnapshot's take(60) is unrelated code in the data layer.) |
| F271 | ThreadViewModel.kt:599 | ui/ux | Fixed (30ab9e7) |
| F272 | VaultExplorer.kt:203 | ui/ux | Fixed (9389352) |
| F273 | VaultExplorer.kt:233 | 6 canonical layer | Fixed (9ebe79d) |
| F274 | VaultExplorer.kt:247 | 2 spaghetti growth | Fixed (9ebe79d) |
| F275 | VaultExplorer.kt:612 | 6 canonical layer | Deferred (rejected in verification: Misreads the code. The five call sites are not "the same job": DateFormat.SHORT (VaultDedupSheet:311), the default style (ThreadScreen:311) and getDateTimeInstance(MEDIUM, SHORT) (StorageSection:146) render deliberately different strings, so folding them into one savedDate/dateTime pair changes visible text, the finding claims behaviourChange:false. Likewise formatMs (VideoPage:413) intentionally never rolls over to hours ("75:03"), while formatDuration emits "1:15:03"; swapping them changes the scrubber label on long videos. The cited line 612 is itself the canonical helper (internal savedDate, already reused by VaultEntrySheet:90), not a defect; the only real duplicate is the private dateFormat in a different file, VaultStatsSheet.kt:217.) |
| F276 | VaultScreen.kt:412 | 7 orchestration/atomicity | Fixed (c6420be) |
| F277 | VaultScreen.kt:735 | ui/ux | Fixed (245d6ae) |
| F278 | VaultStatsSheet.kt:67 | 3 design cleanliness | Deferred (rejected in verification: Taste nit resting on a misread. perBoard is sorted bytes-descending (VaultStats.kt:47), so first().bytes is the max and the call is a trivial O(1) list head read per row, not a meaningful recomputation. The `largest > 0` guard is not dead code by construction: the finding itself names the case that reaches it (every row's sizeBytes null so every board totals 0 bytes), so removing it without a new invariant risks a divide-by-zero NaN fraction. Rules 3/4 do not flag hoisting a one-token expression out of a lambda.) |
| F279 | VaultViewModel.kt:475 | 4 magic/wrappers | Fixed (22bde01) |
| F280 | AppNavHost.kt:116 | 3 design cleanliness | Deferred (rejected in verification: The duplication is real at L114-124 and L136-146, but it is ~7 lines of declarative Compose slot wiring across two Material3 composables (NavigationRailItem/NavigationBarItem) that share no supertype or common API. The proposed fix is a higher-order composable taking a `(selected, onClick, icon, label) -> Unit` item factory, exactly the thin, indirection-adding wrapper rule 4 tells reviewers to be sceptical of, and it would not delete a concept, only relocate it. This is a low-value nit the review is told not to flood the output with.) |
| F281 | ExternalLinks.kt:21 | 6 canonical layer | Fixed (38f6e23) |
| F282 | ic_launcher_monochrome.xml:1 | 4 magic/wrappers | Fixed (2409b5c) |
| F283 | NetworkLayerTest.kt:7 | 3 design cleanliness | Deferred (rejected in verification: The unused import at line 7 is real (grep finds `Urls` only on that line), but it is a cosmetic lint nit, not something this review's rules flag: no structural regression, no branching or abstraction problem, no file-size or layering issue. The skill explicitly says not to flood the review with low-value nits and not to settle for cosmetic notes.) |
| F284 | VaultSnapshotTest.kt:23 | 6 canonical layer | Fixed (cde4ab0) |
| F285 | libs.versions.toml:27 | 4 magic/wrappers | Fixed (7266fb2) |
| F286 | libs.versions.toml:60 | 4 magic/wrappers | Deferred (rejected in verification: Duplicate of F285, same okhttp-logging, kotlin-android and inline-test-version observations with no additional content; keeping both would double-report one issue.) |
