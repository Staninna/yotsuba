package dev.stan.yotsuba.feature.thread

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.animatedListItem
import dev.stan.yotsuba.core.designsystem.component.SearchField
import dev.stan.yotsuba.core.designsystem.component.UiStateContent
import dev.stan.yotsuba.core.designsystem.component.errorMessage
import dev.stan.yotsuba.core.designsystem.motionEnter
import dev.stan.yotsuba.core.designsystem.motionExit
import dev.stan.yotsuba.core.designsystem.rememberHaptics
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.feature.media.saveToVault
import dev.stan.yotsuba.feature.media.shareText
import dev.stan.yotsuba.feature.thread.components.BacklinksUi
import dev.stan.yotsuba.feature.thread.components.BodyTap
import dev.stan.yotsuba.feature.thread.components.ExternalLinkDialog
import dev.stan.yotsuba.feature.thread.components.PostActionSheet
import dev.stan.yotsuba.feature.thread.components.PostCard
import dev.stan.yotsuba.feature.thread.components.PostCardActions
import dev.stan.yotsuba.feature.thread.components.QuotePreviewSheet
import dev.stan.yotsuba.feature.thread.components.ThreadGallerySheet
import dev.stan.yotsuba.feature.thread.components.ThreadTopBar
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlinx.coroutines.flow.first
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
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.thread_post_number_copied)
    val grantAccessMessage = stringResource(R.string.media_grant_storage)
    val saveAllMessage = stringResource(R.string.thread_gallery_save_all_queued)
    val textCopiedMessage = stringResource(R.string.thread_text_copied)
    val imageUrlCopiedMessage = stringResource(R.string.thread_image_url_copied)
    val haptics = rememberHaptics()
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

    // Inside the sheet a quotelink refocuses the sheet and a hold jumps; in the list both
    // follow the quote-tap setting. One instance per context, remembered, so every card gets
    // an equal `actions` and can skip recomposition.
    fun actionsFor(inPreview: Boolean) = PostCardActions(
        onBodyTap = { post, tap ->
            when (tap) {
                is BodyTap.Spoiler -> {
                    haptics.tick()
                    viewModel.onRevealSpoiler(post.no, tap.id)
                }
                is BodyTap.SameThreadQuote ->
                    if (inPreview) viewModel.onOpenPreview(tap.postNo) else viewModel.onQuoteTap(tap.postNo)
                // Another thread's post shows as a ghost in the sheet; the jump setting, which
                // has nowhere to jump to here, opens the thread instead.
                is BodyTap.CrossThreadQuote ->
                    if (inPreview) viewModel.onOpenGhost(tap.board, tap.threadNo, tap.postNo)
                    else if (!viewModel.onCrossThreadQuoteTap(tap.board, tap.threadNo, tap.postNo)) {
                        onOpenInternal(Urls.InternalLink.Thread(tap.board, tap.threadNo, tap.postNo))
                    }
                is BodyTap.Deadlink -> viewModel.onDeadlinkTap(tap.postNo)
                is BodyTap.Link -> handleLink(tap.url)
            }
        },
        onBodyLongPress = { _, tap ->
            when (tap) {
                is BodyTap.SameThreadQuote -> {
                    haptics.longPress()
                    if (inPreview) viewModel.onJumpToPost(tap.postNo) else viewModel.onQuoteLongPress(tap.postNo)
                }
                // A held cross-thread quote always leaves for that thread.
                is BodyTap.CrossThreadQuote -> {
                    haptics.longPress()
                    onOpenInternal(Urls.InternalLink.Thread(tap.board, tap.threadNo, tap.postNo))
                }
                else -> {}
            }
        },
        onThumbnailTap = viewModel::onThumbnailTap,
        onThumbnailLongPress = { post ->
            if (viewModel.onThumbnailLongPress(post)) {
                haptics.longPress()
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
        backlinks = BacklinksUi.Quotes(
            onTap = viewModel::onQuoteTap,
            onLongPress = {
                haptics.longPress()
                viewModel.onQuoteLongPress(it)
            },
        ),
        onPosterIdTap = { viewModel.onFilterPosterId(it.posterId) },
        onLongPress = { post ->
            haptics.longPress()
            viewModel.onOpenPostSheet(post.no)
        },
        onCopyPostNo = { post ->
            clipboard.setText(AnnotatedString(post.no.toString()))
            scope.launch { snackbar.showSnackbar(copiedMessage) }
        },
    )
    val listActions = remember(viewModel, onOpenInternal, haptics, context, scope, clipboard, snackbar, grantAccessMessage, copiedMessage) {
        actionsFor(inPreview = false)
    }
    val previewActions = remember(listActions) { actionsFor(inPreview = true).forPreview() }

    // Polling follows the lifecycle: backgrounding the app or leaving the screen stops it.
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenVisibilityChanged(true)
        onPauseOrDispose { viewModel.onScreenVisibilityChanged(false) }
    }

    // The VM resolves where to scroll (restore priority, search steps); the screen obeys.
    // Keyed on the target alone: a poll, a keystroke or the highlight clearing must not
    // cancel a scroll in flight. The rows are read live, so the first load is waited for.
    val currentRows by rememberUpdatedState((state as? UiState.Success)?.data?.rows)
    LaunchedEffect(scrollTarget) {
        val target = scrollTarget ?: return@LaunchedEffect
        val rows = snapshotFlow { currentRows }.first { it != null }!!
        val index = rows.indexOfFirst { (it as? ThreadRow.Post)?.post?.no == target.postNo }
        if (index >= 0) {
            if (target.animate) {
                listState.animateScrollToItem(index)
                haptics.tick()
            } else {
                listState.scrollToItem(index)
            }
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
        // Clearing the error re-keys this effect; the snackbar outlives it on the screen's scope.
        scope.launch { snackbar.showSnackbar(refreshErrorMessage) }
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
            AnimatedVisibility(
                visible = scrolled || firstNewIndex != null,
                enter = motionEnter(),
                exit = motionExit(),
            ) {
                JumpButtons(
                    onTop = { scope.launch { listState.jumpTo(0) } },
                    onFirstNew = firstNewIndex?.let { index -> { scope.launch { listState.jumpTo(index) } } },
                    onBottom = { scope.launch { listState.jumpTo(rows.lastIndex.coerceAtLeast(0)) } },
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
                onRepliesToMeTap = s?.latestReplyToMe?.let { no -> { viewModel.onQuoteTap(no) } },
                onRepliesToMeLongPress = s?.latestReplyToMe?.let { no ->
                    {
                        haptics.longPress()
                        viewModel.onQuoteLongPress(no)
                    }
                },
                filteredCount = s?.filteredCount ?: 0,
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
                archiveUrl = s?.archiveUrl,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            UiStateContent(state, onRetry = viewModel::retry) { s ->
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
                    PostCard(
                        post = post,
                        board = s.board,
                        ui = s.postStates[post.no] ?: PostUiState.Default,
                        revealAll = s.revealAllSpoilers,
                        darkTheme = dark,
                        actions = if (inPreview) previewActions else listActions,
                        sharesMediaWithViewer = !inPreview,
                        highlight = if (inPreview) null else s.searchQuery,
                        quoteLabels = quoteLabels,
                    )
                }

                Column {
                    val notice = if (s.details.offlineCopy) {
                        val date = remember(s.offlineCopyAt) {
                            s.offlineCopyAt?.let { DateFormat.getDateInstance().format(Date(it)) }
                        }
                        if (date != null) stringResource(R.string.thread_offline_copy_from, date)
                        else stringResource(R.string.thread_offline_copy)
                    } else if (s.archivedNotice) {
                        s.details.archive?.let { stringResource(R.string.thread_archived_from, it.label) }
                            ?: stringResource(R.string.thread_archived)
                    } else null
                    notice?.let { ThreadNotice(it) }
                    if (searchOpen) {
                        SearchBar(s, viewModel, onClose = ::closeSearch)
                    }
                    PullToRefreshBox(
                        isRefreshing = s.refreshing,
                        onRefresh = { haptics.tick(); viewModel.load(forceRefresh = true) },
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
                                        is ThreadRow.MoreReplies -> "more-${row.parentNo}"
                                        is ThreadRow.Filtered -> "filtered-${row.postNo}"
                                    }
                                },
                                // Four different subtrees; only a slot of the same kind is worth reusing.
                                contentType = { i ->
                                    when (s.rows[i]) {
                                        is ThreadRow.Post -> "post"
                                        is ThreadRow.NewPostsDivider -> "divider"
                                        is ThreadRow.MoreReplies -> "more"
                                        is ThreadRow.Filtered -> "filtered"
                                    }
                                },
                            ) { i ->
                                Box(animatedListItem()) {
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
                                        modifier = Modifier.padding(start = treeIndent * MAX_TREE_DEPTH),
                                        onTap = { viewModel.onExpandTail(row.parentNo) },
                                    )
                                    is ThreadRow.Filtered -> FilteredRow(
                                        pattern = row.pattern,
                                        modifier = Modifier.padding(start = treeIndent * row.depth),
                                        onTap = { viewModel.onExpandFiltered(row.postNo) },
                                    )
                                }
                                }
                            }
                        }
                    }
                }

                // System back steps the preview sheet back one post instead of leaving the thread.
                BackHandler(enabled = s.preview != null) {
                    viewModel.onClosePreview()
                }
                // ...and closes the search bar before leaving the thread.
                BackHandler(enabled = searchOpen && s.preview == null) {
                    closeSearch()
                }
                s.preview?.let { preview ->
                    QuotePreviewSheet(
                        preview = preview,
                        onDismiss = viewModel::onDismissPreview,
                        onBack = viewModel::onClosePreview,
                        onGoTo = viewModel::onJumpToPost,
                        onOpenThread = { board, threadNo, postNo ->
                            onOpenInternal(Urls.InternalLink.Thread(board, threadNo, postNo))
                        },
                        onFocus = viewModel::onOpenPreview,
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
                            shareText(context, "${Urls.threadWebUrl(board, threadNo)}#p${post.no}")
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
                        boardAllowsAudio = s.board?.webmAudio == true,
                        onOpen = viewModel::onOpenMediaFromGallery,
                        onSaveAll = { shown ->
                            saveToVault(
                                context = context,
                                hasAccess = viewModel.hasStorageAccess(),
                                onAccessNeeded = { scope.launch { snackbar.showSnackbar(grantAccessMessage) } },
                                save = {
                                    viewModel.onSaveAllMedia(shown)
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

/**
 * A long jump teleports to just short of [target] and animates the last stretch: the user
 * sees the same settle, and the hundreds of cards in between are never composed.
 */
private suspend fun LazyListState.jumpTo(target: Int) {
    if (abs(target - firstVisibleItemIndex) > 25) scrollToItem((target - 5).coerceAtLeast(0))
    animateScrollToItem(target)
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
private fun refreshErrorMessage(error: NetworkError): String =
    stringResource(R.string.thread_refresh_failed, errorMessage(error))

/** The strip above the list saying this copy is offline or archived. */
@Composable
private fun ThreadNotice(text: String) {
    val spacing = LocalSpacing.current
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(spacing.sm),
    )
}

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

/** One-line stand-in for a post a STUB filter caught; tapping it opens the post. */
@Composable
private fun FilteredRow(pattern: String, modifier: Modifier, onTap: () -> Unit) {
    val spacing = LocalSpacing.current
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onTap)) {
        Text(
            stringResource(R.string.thread_filtered_stub, pattern),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(spacing.sm),
        )
    }
}

@Composable
private fun NewPostsDivider(count: Int, onTap: () -> Unit) {
    val spacing = LocalSpacing.current
    // Grows in when the row first lands; the list's item animation handles its removal.
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = shown, enter = motionEnter(), exit = motionExit()) {
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
}
