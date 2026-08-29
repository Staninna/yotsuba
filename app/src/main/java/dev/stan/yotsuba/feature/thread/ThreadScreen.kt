package dev.stan.yotsuba.feature.thread

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.feature.media.saveToVault
import dev.stan.yotsuba.core.designsystem.component.SearchField
import dev.stan.yotsuba.core.designsystem.component.UiStateContent
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.feature.thread.components.BodyTap
import dev.stan.yotsuba.feature.thread.components.ExternalLinkDialog
import dev.stan.yotsuba.feature.thread.components.PostActionSheet
import dev.stan.yotsuba.feature.thread.components.PostCard
import dev.stan.yotsuba.feature.thread.components.PostCardActions
import dev.stan.yotsuba.feature.thread.components.QuotePreviewOverlay
import dev.stan.yotsuba.feature.thread.components.ThreadGallerySheet
import dev.stan.yotsuba.feature.thread.components.ThreadTopBar
import kotlinx.coroutines.launch

@Composable
fun ThreadScreen(
    board: String,
    threadNo: Long,
    scrollToPostNo: Long?,
    onBack: () -> Unit,
    onOpenMedia: (Long) -> Unit,
    onOpenInternal: (Urls.InternalLink) -> Unit,
    viewModel: ThreadViewModel = hiltViewModel<ThreadViewModel, ThreadViewModel.Factory>(
        creationCallback = { it.create(board, threadNo, scrollToPostNo) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollTarget by viewModel.scrollTarget.collectAsStateWithLifecycle()
    val mediaToOpen by viewModel.mediaToOpen.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var searchOpen by remember { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.thread_post_number_copied)
    val grantAccessMessage = stringResource(R.string.media_grant_storage)
    val saveAllMessage = stringResource(R.string.thread_gallery_save_all_queued)
    val textCopiedMessage = stringResource(R.string.thread_text_copied)
    val imageUrlCopiedMessage = stringResource(R.string.thread_image_url_copied)
    val haptics = LocalHapticFeedback.current
    val treeIndent = spacing.lg

    fun closeSearch() {
        searchOpen = false
        viewModel.onSearchChange(null) // drops the query and every highlight with it
    }

    fun openExternal(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    fun handleLink(url: String) {
        when (val action = viewModel.onLinkTap(url)) {
            is LinkAction.Internal -> onOpenInternal(action.link)
            is LinkAction.External -> openExternal(action.url)
            LinkAction.Confirm -> {} // the dialog shows from state
        }
    }

    // Polling follows the lifecycle: backgrounding the app or leaving the screen stops it.
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenVisibilityChanged(true)
        onPauseOrDispose { viewModel.onScreenVisibilityChanged(false) }
    }

    // The VM resolves where to scroll (restore priority, search steps); the screen obeys.
    LaunchedEffect(scrollTarget, state) {
        val target = scrollTarget ?: return@LaunchedEffect
        val rows = (state as? UiState.Success)?.data?.rows ?: return@LaunchedEffect
        val index = rows.indexOfFirst { (it as? ThreadRow.Post)?.post?.no == target.postNo }
        if (index >= 0) {
            if (target.animate) listState.animateScrollToItem(index) else listState.scrollToItem(index)
        }
        viewModel.onScrollTargetConsumed()
    }

    // The VM decided a thumbnail tap opens the viewer (rather than revealing a spoiler).
    LaunchedEffect(mediaToOpen) {
        val postNo = mediaToOpen ?: return@LaunchedEffect
        viewModel.onMediaOpened()
        onOpenMedia(postNo)
    }

    // A failed refresh leaves the thread up and says so once.
    val refreshError = (state as? UiState.Success)?.data?.refreshError
    val refreshErrorMessage = refreshError?.let { refreshErrorMessage(it) }
    LaunchedEffect(refreshError) {
        if (refreshError == null || refreshErrorMessage == null) return@LaunchedEffect
        viewModel.onRefreshErrorShown()
        snackbar.showSnackbar(refreshErrorMessage)
    }

    // Report the visible index range; the VM owns read position and unread counts.
    LaunchedEffect(Unit) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }.collect { (first, last) -> viewModel.onVisiblePostsChanged(first, last) }
    }

    // Jump buttons show once the list has moved, like the catalog's scroll-to-top.
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val rows = (state as? UiState.Success)?.data?.rows.orEmpty()
    val firstNewIndex = rows.indexOfFirst { it is ThreadRow.NewPostsDivider }.takeIf { it >= 0 }?.plus(1)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (scrolled || firstNewIndex != null) {
                JumpButtons(
                    onTop = { scope.launch { listState.animateScrollToItem(0) } },
                    onFirstNew = firstNewIndex?.let { index -> { scope.launch { listState.animateScrollToItem(index) } } },
                    onBottom = { scope.launch { listState.animateScrollToItem(rows.lastIndex.coerceAtLeast(0)) } },
                )
            }
        },
        topBar = {
            val s = (state as? UiState.Success)?.data
            ThreadTopBar(
                board = board,
                threadNo = threadNo,
                title = s?.details?.posts?.firstOrNull()?.subject ?: "/$board/$threadNo",
                bookmarked = s?.bookmarked == true,
                autoRefreshEnabled = s?.autoRefreshEnabled == true,
                repliesToMe = s?.repliesToMe ?: 0,
                filterPosterId = s?.filterPosterId,
                onClearFilter = { viewModel.onFilterPosterId(null) },
                onBack = onBack,
                onToggleBookmark = viewModel::onToggleBookmark,
                onRefresh = { viewModel.load(forceRefresh = true) },
                onOpenSearch = { searchOpen = true },
                onOpenGallery = viewModel::onOpenGallery,
                treeView = s?.treeView == true,
                onToggleTreeView = viewModel::onToggleTreeView,
                onToggleAutoRefresh = viewModel::onToggleAutoRefresh,
                onOpenExternal = ::openExternal,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            UiStateContent(state, onRetry = { viewModel.load() }) { s ->
                fun actionsFor(post: ThreadPost) = PostCardActions(
                    onBodyTap = { tap ->
                        when (tap) {
                            is BodyTap.Spoiler -> viewModel.onRevealSpoiler(post.no, tap.id)
                            is BodyTap.SameThreadQuote -> viewModel.onJumpToPost(tap.postNo)
                            is BodyTap.CrossThreadQuote -> onOpenInternal(
                                Urls.InternalLink.Thread(tap.board, tap.threadNo, tap.postNo)
                            )
                            is BodyTap.Link -> handleLink(tap.url)
                        }
                    },
                    onBodyLongPress = { tap ->
                        if (tap is BodyTap.SameThreadQuote) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onOpenPreview(tap.postNo)
                        }
                    },
                    onThumbnailTap = { viewModel.onThumbnailTap(post) },
                    onThumbnailLongPress = {
                        if (viewModel.onThumbnailLongPress(post)) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            saveToVault(
                                context = context,
                                hasAccess = viewModel.hasStorageAccess(),
                                onAccessNeeded = {
                                    scope.launch { snackbar.showSnackbar(grantAccessMessage) }
                                },
                                save = { viewModel.onSaveMedia(post) },
                            )
                        }
                    },
                    onBacklinkTap = viewModel::onJumpToPost,
                    onPosterIdTap = { viewModel.onFilterPosterId(post.posterId) },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.onOpenPostSheet(post.no)
                    },
                    onCopyPostNo = {
                        clipboard.setText(AnnotatedString(post.no.toString()))
                        scope.launch { snackbar.showSnackbar(copiedMessage) }
                    },
                )

                val opLabel = stringResource(R.string.thread_quote_label_op)
                val youLabel = stringResource(R.string.thread_quote_label_you)
                val quoteLabels = remember(s.quoteLabels, opLabel, youLabel) {
                    s.quoteLabels.mapValues { (_, label) ->
                        when (label) {
                            QuoteLabel.OP -> opLabel
                            QuoteLabel.YOU -> youLabel
                        }
                    }
                }
                val postCard: @Composable (ThreadPost, Boolean) -> Unit = { post, inPreview ->
                    val actions = actionsFor(post)
                    PostCard(
                        post = post,
                        board = s.board,
                        ui = s.postStates[post.no] ?: PostUiState.Default,
                        revealAll = s.revealAllSpoilers,
                        darkTheme = dark,
                        actions = if (inPreview) actions.forPreview() else actions,
                        highlight = if (inPreview) null else s.searchQuery,
                        quoteLabels = quoteLabels,
                    )
                }

                Column {
                    if (s.archivedNotice) {
                        Text(
                            stringResource(R.string.thread_archived),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(spacing.sm),
                        )
                    }
                    if (searchOpen) {
                        SearchBar(s, viewModel, onClose = ::closeSearch)
                    }
                    PullToRefreshBox(
                        isRefreshing = s.refreshing,
                        onRefresh = { viewModel.load(forceRefresh = true) },
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(spacing.md),
                            verticalArrangement = Arrangement.spacedBy(spacing.md),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                count = s.rows.size,
                                key = { i ->
                                    when (val row = s.rows[i]) {
                                        is ThreadRow.Post -> row.post.no
                                        is ThreadRow.NewPostsDivider -> "new-posts"
                                        is ThreadRow.MoreReplies -> "more-${'$'}{row.parentNo}"
                                    }
                                },
                            ) { i ->
                                when (val row = s.rows[i]) {
                                    is ThreadRow.Post -> Box(Modifier.padding(start = treeIndent * row.depth)) {
                                        postCard(row.post, false)
                                    }
                                    is ThreadRow.NewPostsDivider -> NewPostsDivider(
                                        count = row.count,
                                        onTap = viewModel::onDismissNewPostsDivider,
                                    )
                                    is ThreadRow.MoreReplies -> MoreRepliesRow(
                                        count = row.count,
                                        modifier = Modifier.padding(start = treeIndent * ThreadViewModel.MAX_TREE_DEPTH),
                                        onTap = { viewModel.onExpandTail(row.parentNo) },
                                    )
                                }
                            }
                        }
                    }
                }

                // System back pops one preview instead of leaving the thread.
                BackHandler(enabled = s.previewStack.isNotEmpty()) {
                    viewModel.onClosePreview()
                }
                // ...and closes the search bar before leaving the thread.
                BackHandler(enabled = searchOpen && s.previewStack.isEmpty()) {
                    closeSearch()
                }
                if (s.previewStack.isNotEmpty()) {
                    QuotePreviewOverlay(
                        group = s.previewStack.last(),
                        onDismiss = viewModel::onClosePreview,
                        onGoTo = viewModel::onJumpToPost,
                        postCard = { post -> postCard(post, true) },
                    )
                }

                s.postSheet?.let { post ->
                    PostActionSheet(
                        post = post,
                        claimed = post.no in s.claimedPostNos,
                        showFilterById = s.board?.userIds == true,
                        onCopyText = {
                            viewModel.onClosePostSheet()
                            clipboard.setText(AnnotatedString(post.body.plainText))
                            scope.launch { snackbar.showSnackbar(textCopiedMessage) }
                        },
                        onShareLink = {
                            viewModel.onClosePostSheet()
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${'$'}{Urls.threadWebUrl(board, threadNo)}#p${'$'}{post.no}")
                            }
                            runCatching { context.startActivity(Intent.createChooser(send, null)) }
                        },
                        onCopyImageUrl = {
                            viewModel.onClosePostSheet()
                            post.presentMedia?.let { clipboard.setText(AnnotatedString(it.fullUrl)) }
                            scope.launch { snackbar.showSnackbar(imageUrlCopiedMessage) }
                        },
                        onToggleClaimed = {
                            viewModel.onClosePostSheet()
                            viewModel.onToggleClaimed(post.no)
                        },
                        onFilterById = {
                            viewModel.onClosePostSheet()
                            viewModel.onFilterPosterId(post.posterId)
                        },
                        onDismiss = viewModel::onClosePostSheet,
                    )
                }

                if (s.galleryOpen) {
                    ThreadGallerySheet(
                        posts = s.mediaPosts,
                        revealAll = s.revealAllSpoilers,
                        onOpen = viewModel::onOpenMediaFromGallery,
                        onSaveAll = {
                            saveToVault(
                                context = context,
                                hasAccess = viewModel.hasStorageAccess(),
                                onAccessNeeded = { scope.launch { snackbar.showSnackbar(grantAccessMessage) } },
                                save = {
                                    viewModel.onSaveAllMedia()
                                    scope.launch { snackbar.showSnackbar(saveAllMessage) }
                                },
                            )
                        },
                        onDismiss = viewModel::onCloseGallery,
                    )
                }

                s.pendingExternalUrl?.let { url ->
                    ExternalLinkDialog(
                        url = url,
                        onOpen = {
                            viewModel.onDismissLinkDialog()
                            openExternal(url)
                        },
                        onTrustDomain = {
                            viewModel.onTrustDomain(url)
                            openExternal(url)
                        },
                        onDismiss = viewModel::onDismissLinkDialog,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(s: ThreadContent, viewModel: ThreadViewModel, onClose: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = spacing.md),
    ) {
        SearchField(
            value = s.searchQuery.orEmpty(),
            onValueChange = { viewModel.onSearchChange(it) },
            hintRes = R.string.thread_search_in_thread,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (s.searchMatches.isEmpty()) "0/0"
            else "${s.searchIndex + 1}/${s.searchMatches.size}",
            style = MaterialTheme.typography.labelMedium,
        )
        IconButton(onClick = { viewModel.onSearchStep(-1) }) {
            Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.thread_search_prev))
        }
        IconButton(onClick = { viewModel.onSearchStep(1) }) {
            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.thread_search_next))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, stringResource(R.string.thread_search_close))
        }
    }
}

@Composable
private fun refreshErrorMessage(error: NetworkError): String = stringResource(
    R.string.thread_refresh_failed,
    when (error) {
        NetworkError.Offline -> stringResource(R.string.error_offline)
        NetworkError.Timeout -> stringResource(R.string.error_timeout)
        NetworkError.RateLimited -> stringResource(R.string.error_rate_limited)
        NetworkError.NotFound -> stringResource(R.string.error_not_found)
        is NetworkError.Server -> stringResource(R.string.error_server, error.code)
        is NetworkError.Unknown -> stringResource(R.string.error_unknown)
    },
)

/** Jump to top / first new post / bottom. */
@Composable
private fun JumpButtons(onTop: () -> Unit, onFirstNew: (() -> Unit)?, onBottom: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm), horizontalAlignment = Alignment.End) {
        SmallFloatingActionButton(onClick = onTop) {
            Icon(Icons.Filled.VerticalAlignTop, stringResource(R.string.thread_jump_top))
        }
        if (onFirstNew != null) {
            SmallFloatingActionButton(
                onClick = onFirstNew,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Filled.FiberNew, stringResource(R.string.thread_jump_first_new))
            }
        }
        SmallFloatingActionButton(onClick = onBottom) {
            Icon(Icons.Filled.VerticalAlignBottom, stringResource(R.string.thread_jump_bottom))
        }
    }
}

/** Tree view: the folded tail under a depth-capped post. */
@Composable
private fun MoreRepliesRow(count: Int, modifier: Modifier, onTap: () -> Unit) {
    val spacing = LocalSpacing.current
    Text(
        pluralStringResource(R.plurals.thread_more_replies, count, count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().clickable(onClick = onTap).padding(spacing.sm),
    )
}

@Composable
private fun NewPostsDivider(count: Int, onTap: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap).padding(vertical = spacing.xs),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Text(
            pluralStringResource(R.plurals.thread_new_posts, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = spacing.md),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}
