# Yotsuba — UX/UI Improvement Report

High-value, low-risk, high-impact changes, based on a full read of the Compose UI layer
(`app/app/src/main/java/dev/stan/yotsuba`). Ordered by value-per-risk. Every item has
file:line anchors.

Status: in-progress

Update 2026-08-20: Tier 1 complete except item 1 (Stan reports it doesn't reproduce —
skipped). Item 2 extended per Stan's request: thumbnail previews now also show while
swiping between pages and under videos until the first frame renders. Items 2-11 built,
tests pass, deployed to the phone. Remaining: Tier 2 (items 12-19) and Tier 3 (20-25).

Update 2026-08-20 (round 2): item 12 (bookmarks unread badge — new `newReplies` column,
DB v3 migration, counted by refreshOne, cleared on thread visit, "+N" pill on rows) and
item 23 (predictive back — `enableOnBackInvokedCallback` in manifest) done. Swipe-to-delete
retuned per Stan: commit threshold raised to 75% of width and deletion now fires only on
release (M3 `onDismiss`), never mid-drag. Remaining: 13-19, 20-22, 24-25.

---

## Tier 1 — Bugs & broken UX (fix first, all small diffs)

### 1. Media viewer renders a blank screen while loading — and forever on empty/failed threads
- `MediaScreen.kt:153` — `if (!state.loaded) return`: while the thread fetch is in flight the
  viewer draws literally nothing (not even a black background). `loaded` only flips when
  `items.isNotEmpty()` (`MediaViewModel.kt:102`), so a thread with no media, or a failed fetch
  (`MediaViewModel.kt:78-83` has no error branch), is a permanently blank screen.
- Fix: always draw the black background + a spinner while unloaded; add an error/empty branch
  with a close affordance in the ViewModel state.

### 2. Full-size images have no placeholder, no progress, no error state
- `MediaScreen.kt:324-331` — `ZoomableAsyncImage` gets only `model` + `contentDescription`.
  A slow or failed load is an indefinite black screen.
- `MediaItem.thumbnailUrl` exists (`domain/model/MediaItem.kt:10`) but is never used in the
  viewer. Use it as the placeholder (`placeholderMemoryCacheKey` / placeholder request) so the
  already-cached thumbnail shows instantly while the full file loads; add an error state with
  retry. Also add `.crossfade()` to the app-wide ImageLoader (`YotsubaApplication.kt:24-36` —
  currently every image in the app pops in with no transition).

### 3. History: "Clear all" is one tap, irreversible, no confirmation
- `HistoryScreen.kt:60` → `HistoryViewModel.kt:67`. No dialog, no snackbar, no undo. The
  strings `history_cleared` and `history_entry_removed` exist (`strings.xml:113-114`) but are
  unused. Reuse the confirm-dialog pattern Settings already has.

### 4. History swipe-to-delete is glitchy and silent
- `HistoryScreen.kt:90-98`: `SwipeToDismissBox(backgroundContent = {})` — swiping reveals an
  empty void (no red background / delete icon); the delete fires during composition on
  `currentValue != Settled` in **either** direction, so a partial fling can delete; no undo
  snackbar (Bookmarks has one — `BookmarksScreen.kt:107-108` — copy that pattern); the
  `dismissState` is remembered without a key so it can leak across recycled rows.

### 5. History loading = blank white screen
- `HistoryScreen.kt:68` — `!state.loaded -> {}`. Every other screen has a skeleton; this one
  renders nothing.

### 6. Settings confirm dialogs have wrong button labels
- `SettingsScreen.kt:214-217`: destructive "Are you sure?" dialog has no body text saying what
  will be destroyed and its confirm button literally says **"Open"** (`R.string.action_open`,
  copy-pasted from the link dialog). The trusted-domains and hidden-threads dialogs use
  **"Cancel"** as their close/confirm button (`:245`, `:267`). Pure string fixes.
- Same dialogs: the lists inside are non-lazy, non-scrollable `Column`s
  (`SettingsScreen.kt:233-242`, `:255-264`) — 50 trusted domains = clipped unreachable list.
  Swap for a height-capped `LazyColumn`.

### 7. Catalog shows the wrong empty state during search
- `CatalogScreen.kt:152-155` — "This board has no threads right now" appears when a search
  query merely has zero matches. Branch on `searchQuery.isNotBlank()` → "No threads match
  '<query>'". Boards has the inverse bug: no empty state at all for zero search matches
  (`BoardsScreen.kt:74` excludes the query case).

### 8. Bookmarks pull-to-refresh indicator never spins
- `BookmarksScreen.kt:89` — `PullToRefreshBox(isRefreshing = false, ...)`. The refresh works
  (progress shows in the app-bar subtitle) but the spinner is hardwired off. One-line fix.

### 9. History day buckets use UTC midnight, not local
- `HistoryViewModel.kt:47` — `now % 86_400_000`. "Today"/"Yesterday" grouping is wrong for
  most timezones most of the day. Use `LocalDate` / `ZoneId.systemDefault()`.

### 10. Quote-preview stack ignores the back button
- `ThreadScreen.kt:321-371` — the stacked quotelink preview overlay has no `BackHandler`;
  system back exits the whole thread instead of popping the preview. Tap-outside works
  (`:326`), back doesn't. Add `BackHandler(enabled = previewStack.isNotEmpty())`.

### 11. Downloads bypass the cache and buffer whole files in memory
- `MediaScreen.kt:704-707` — save/share uses `java.net.URL(...).openStream()` +
  full `ByteArray`, skipping the shared OkHttp client and Coil's 200 MB disk cache: saving an
  image you're literally looking at re-downloads it. No progress indicator either, and share
  failure is silent (`:733-734`). Route through OkHttp (cache hit → instant), stream to the
  MediaStore output, add an indeterminate progress state on the button + failure snackbar.

---

## Tier 2 — High-impact consistency & feature wins (small-to-medium diffs)

### 12. Bookmarks: unread-count badge (the data already exists)
- `Bookmark.lastSeenPostNo` is written on every thread visit (`ThreadViewModel.kt:230`) but
  **never read by any UI**. The refresh-all flow already fetches current reply counts. Showing
  "+N new" on bookmark rows is the single biggest feature-per-diff win in the app — it turns
  Bookmarks into the "what's new" home screen.

### 13. Thread screen: add pull-to-refresh + jump FABs
- Catalog and Bookmarks have `PullToRefreshBox`; Thread — the screen you refresh most — only
  has a toolbar icon (`ThreadScreen.kt:176`). Add the same box.
- No jump-to-top/bottom in lists that hit 300+ posts, and the "N new posts" divider
  (`ThreadScreen.kt:424-440`) has no companion "jump to new posts" affordance. Catalog already
  has the FAB pattern (`CatalogScreen.kt:124-130`) — reuse it.

### 14. Copy post text
- There is no way to select or copy a post body anywhere (no `SelectionContainer` in the
  codebase; `PostBody` uses deprecated `ClickableText`, `PostBody.kt:80`). Low-risk version:
  long-press on a post → context sheet with "Copy text" (plain text from the existing
  `PostText` model), "Share post link", "Copy image URL". Avoids touching the ClickableText
  rendering path. (Full `ClickableText` → `LinkAnnotation` migration is worthwhile but is a
  Tier 3 refactor.)

### 15. Media chrome auto-hide fights the seek bar
- `MediaScreen.kt:158-163` — chrome hides after a hard 3 s regardless of interaction, so the
  video seek bar vanishes mid-scrub. Reset the timer on any control interaction and pause it
  while scrubbing. Related: no keep-screen-on during video playback and no buffering spinner /
  `onPlayerError` handling (`:562-697`) — a failed webm is a silent black rectangle.

### 16. Haptics: zero in the entire app
- `performHapticFeedback` / `LocalHapticFeedback`: zero hits. Add `LongPress` haptic on
  catalog long-press-hide (`CatalogScreen.kt:179`), swipe-to-delete commits, spoiler reveal,
  and save-success. ~10 lines total, disproportionate feel improvement.

### 17. Accessibility batch (mechanical, one pass)
- Content-bearing thumbnails all pass `contentDescription = null`: `CatalogScreen.kt:211,230,243`,
  `HistoryScreen.kt:111`, `BookmarksScreen.kt:132`.
- Thread overflow menu button unlabelled (`ThreadScreen.kt:180`); boards category
  `TriStateCheckbox` unlabelled (`BoardsScreen.kt:117` — the string `boards_category_toggle`
  exists, unused).
- Two clickable no-op `AssistChip`s are focus traps: NSFW badge (`BoardsScreen.kt:199`) and
  Archived chip (`BookmarksScreen.kt:162`) — make them non-interactive badges.
- Spoilers are masked by color only (`PostBody.kt:68`) — TalkBack reads hidden spoiler text
  aloud. Swap the hidden state's text for a mask/semantics.
- Swipe-only actions (history/bookmark delete, media reply-panel swipe) need
  `customAccessibilityActions`.
- Auto-refresh menu item state is a literal `" ✓"` string concat (`ThreadScreen.kt:213`) —
  non-localizable and invisible to screen readers; use `trailingIcon`/checked semantics.

### 18. Boards screen catch-up
- No pull-to-refresh (refresh only reachable from the error state, `BoardsScreen.kt:72`), and
  `load()` nulls the list first (`BoardsViewModel.kt:32`) so any refresh blanks to skeleton.
  Same blank-to-skeleton issue on Catalog first-load (`CatalogViewModel.kt:55`).
- Favourite/hide gives zero feedback and rows snap between sections — `animateItem()` +
  a small snackbar fixes both.

### 19. Motion pass: cheap, wide-reaching polish
- The only animations in the app are inside `MediaScreen.kt`. All defaults elsewhere:
  - Catalog FAB pops in/out with a raw `if` (`CatalogScreen.kt:125`) → `AnimatedVisibility`.
  - No `animateItem()` on any list/grid (hide/remove snaps).
  - `LoadingSkeleton` is static grey boxes (`StateViews.kt:130-145`) → add a pulse/shimmer.
  - No NavHost transitions (`AppNavHost.kt:55-59`).
  - The `Motion` and `Elevation` token systems exist (`Theme.kt:100-105`) and are consumed by
    **nothing** — use them for exactly this pass instead of magic numbers.

---

## Tier 3 — Worth planning, slightly larger

### 20. Immersive media viewer
- `enableEdgeToEdge()` only; status/nav bars stay visible over the black viewer at all times.
  Tie system-bar visibility to the existing chrome-visibility state
  (`WindowInsetsControllerCompat`, transient-bars-by-swipe). Also: no `imePadding()` on the
  thread/catalog search fields, and `AppNavHost.kt:150` drops the Scaffold's top/side insets.

### 21. Thumbnail → viewer continuity
- No shared-element transition and no memory-cache handoff between the post thumbnail and the
  viewer. Even without `SharedTransitionLayout`, setting the viewer's placeholder to the
  thumbnail's memory-cache key removes the black flash (pairs with item 2).

### 22. Deep links
- No `intent-filter` for `boards.4chan.org` URLs (`AndroidManifest.xml:21-24`) even though
  `Urls.parseInternal` already parses them for in-app taps. Tapping a 4chan link anywhere on
  the phone could open the thread in-app.

### 23. Predictive back
- `android:enableOnBackInvokedCallback` not set; no predictive-back animations. Targets SDK 37,
  so this is increasingly visible. The media viewer's stack (`MediaScreen.kt:197`) and the
  preview stack (item 10) are the two custom back handlers to verify.

### 24. Dead settings: `thumbnailSize` and `density`
- Persisted in DataStore (`SettingsDataStore.kt:51,73,94`), exposed nowhere, read by nothing;
  `PostCard.kt` hardcodes 140/100 dp thumbnails. Either wire them into `PostCard`/catalog or
  delete them.

### 25. Performance nits in ThreadScreen (fix while touching it)
- `LaunchedEffect(state)` re-keys on every poll tick/keystroke (`ThreadScreen.kt:122`, `:138`),
  restarting the scroll-persist collectors constantly.
- Per-post `revealedSpoilers.filter{}.map{}.toSet()` inside the `items` lambda
  (`:286-287`, `:348-349`) — O(posts × spoilers) per recomposition; hoist to one
  `remember(state.revealedSpoilers)` map.
- Media viewer position poll is an unconditional 250 ms `while(true)` loop
  (`MediaScreen.kt:607-614`) — gate on isPlaying/chrome-visible.

---

## Explicitly not recommended right now
- Toolchain/dependency upgrades (pinned deliberately — CLAUDE.md).
- Rewriting `PostBody` to `BasicText` + `LinkAnnotation` as a first step — do the long-press
  copy menu (item 14) first; migrate later with tests.
- Landscape/tablet list-detail layouts — real work, low value for a one-phone deployment.

## Suggested order of attack
1. One "paper-cuts" PR: items 3–10 (all tiny, all bug-level).
2. Media loading states PR: items 1, 2, 15 (+ 21's cache-key trick).
3. Bookmarks unread badge: item 12.
4. Thread QoL: items 13, 14.
5. Haptics + a11y + motion pass: items 16, 17, 19.
