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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.stan.yotsuba.feature.thread.components.PostCard
import dev.stan.yotsuba.feature.thread.components.QuotePreviewOverlay
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
    val haptics = LocalHapticFeedback.current

    fun closeSearch() {
        searchOpen = false
        viewModel.onSearchChange(null) // drops the query and every highlight with it
    }

    fun openExternal(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    fun handleLink(url: String) {
        val internal = Urls.parseInternal(url)
        if (internal != null) {
            onOpenInternal(internal)
        } else if (viewModel.onExternalLink(url)) {
            openExternal(url)
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
        val posts = (state as? UiState.Success)?.data?.details?.posts ?: return@LaunchedEffect
        val index = posts.indexOfFirst { it.no == target.postNo }
        if (index >= 0) {
            if (target.animate) listState.animateScrollToItem(index) else listState.scrollToItem(index)
        }
        viewModel.onScrollTargetConsumed()
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            val s = (state as? UiState.Success)?.data
            ThreadTopBar(
                board = board,
                threadNo = threadNo,
                title = s?.details?.posts?.firstOrNull()?.subject ?: "/$board/$threadNo",
                bookmarked = s?.bookmarked == true,
                autoRefreshEnabled = s?.autoRefreshEnabled == true,
                onBack = onBack,
                onToggleBookmark = viewModel::onToggleBookmark,
                onRefresh = { viewModel.load(forceRefresh = true) },
                onOpenSearch = { searchOpen = true },
                onToggleAutoRefresh = viewModel::onToggleAutoRefresh,
                onOpenExternal = ::openExternal,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            UiStateContent(state, onRetry = { viewModel.load() }) { s ->
                val postCard: @Composable (ThreadPost, Boolean) -> Unit = { post, inPreview ->
                    PostCard(
                        post = post,
                        board = s.board,
                        backlinkCount = if (inPreview) 0 else s.details.backlinks[post.no]?.size ?: 0,
                        saveStatus = post.presentMedia?.fullUrl?.let { s.mediaSaveStatuses[it] },
                        revealedSpoilerIds = s.revealedSpoilers
                            .filter { it.first == post.no }.map { it.second }.toSet(),
                        revealAll = s.revealAllSpoilers,
                        imageSpoilerRevealed = post.no in s.revealedImageSpoilers,
                        darkTheme = dark,
                        onBodyTap = { tap ->
                            when (tap) {
                                is BodyTap.Spoiler -> viewModel.onRevealSpoiler(post.no, tap.id)
                                is BodyTap.SameThreadQuote -> viewModel.onOpenPreview(tap.postNo)
                                is BodyTap.CrossThreadQuote -> onOpenInternal(
                                    Urls.InternalLink.Thread(tap.board, tap.threadNo, tap.postNo)
                                )
                                is BodyTap.Link -> handleLink(tap.url)
                            }
                        },
                        onThumbnailTap = onThumbnailTap@{
                            val media = post.presentMedia ?: return@onThumbnailTap
                            if (!inPreview && media.spoiler && !s.revealAllSpoilers &&
                                post.no !in s.revealedImageSpoilers
                            ) {
                                viewModel.onRevealImageSpoiler(post.no)
                            } else {
                                onOpenMedia(post.no)
                            }
                        },
                        onThumbnailLongPress = {
                            if (!inPreview && s.holdToSave && post.presentMedia != null) {
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
                        onBacklinksTap = { if (!inPreview) viewModel.onOpenBacklinks(post.no) },
                        onCopyPostNo = {
                            if (!inPreview) {
                                clipboard.setText(AnnotatedString(post.no.toString()))
                                scope.launch { snackbar.showSnackbar(copiedMessage) }
                            }
                        },
                        highlight = if (inPreview) null else s.searchQuery,
                    )
                }

                Column {
                    if (s.archivedNotice) {
                        Text(
                            stringResource(R.string.thread_archived),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(spacing.sm),
                        )
                    }
                    if (searchOpen) {
                        SearchBar(s, viewModel, onClose = ::closeSearch)
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(s.details.posts.size, key = { s.details.posts[it].no }) { i ->
                            val post = s.details.posts[i]
                            if (s.newPostsAfter != null && i > 0 &&
                                s.details.posts[i - 1].no == s.newPostsAfter
                            ) {
                                NewPostsDivider(
                                    count = s.newPostsCount,
                                    onTap = { viewModel.onDismissNewPostsDivider() },
                                )
                            }
                            postCard(post, false)
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
                        postCard = { post -> postCard(post, true) },
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
