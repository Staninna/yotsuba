package dev.stan.yotsuba.feature.thread

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.UiStateContent
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.feature.thread.components.BodyTap
import dev.stan.yotsuba.feature.thread.components.PostCard
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    board: String,
    threadNo: Long,
    scrollToPostNo: Long?,
    onBack: () -> Unit,
    onOpenMedia: (Long) -> Unit,
    onOpenInternal: (Urls.InternalLink) -> Unit,
    viewModel: ThreadViewModel = hiltViewModel<ThreadViewModel, ThreadViewModel.Factory>(
        creationCallback = { it.create(board, threadNo) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var scrolledToTarget by remember { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.thread_post_number_copied)

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

    androidx.compose.runtime.DisposableEffect(Unit) {
        viewModel.onScreenVisibilityChanged(true)
        onDispose { viewModel.onScreenVisibilityChanged(false) }
    }

    // Scroll restore, once content arrives. Priority: explicit target (quote/history tap) >
    // media last viewed in the full-screen viewer > reading position saved on scroll.
    LaunchedEffect(state, scrollToPostNo) {
        val s = (state as? UiState.Success)?.data ?: return@LaunchedEffect
        val mediaTarget = viewModel.consumeLastViewedMedia()
        if (scrolledToTarget && mediaTarget == null) return@LaunchedEffect
        val target = when {
            !scrolledToTarget && scrollToPostNo != null -> scrollToPostNo
            mediaTarget != null -> mediaTarget
            !scrolledToTarget -> viewModel.savedScrollPosition()
            else -> null
        } ?: run { scrolledToTarget = true; return@LaunchedEffect }
        val index = s.details.posts.indexOfFirst { it.no == target }
        if (index >= 0) listState.scrollToItem(index)
        scrolledToTarget = true
    }

    // Persist the reading position (first visible post) as the user scrolls.
    LaunchedEffect(state) {
        val s = (state as? UiState.Success)?.data ?: return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }
            .map { s.details.posts.getOrNull(it)?.no }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { postNo ->
                kotlinx.coroutines.delay(500)
                viewModel.onScrolledTo(postNo)
            }
    }

    // Separately track the BOTTOM-most visible post: the true "read up to" mark that the
    // bookmarks unread count is based on (the scroll anchor above is a top-of-screen value).
    LaunchedEffect(state) {
        val s = (state as? UiState.Success)?.data ?: return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }
            .map { it?.let { i -> s.details.posts.getOrNull(i)?.no } }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { postNo ->
                viewModel.onReadUpTo(postNo, s.details.posts.count { it.no > postNo })
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    val s = (state as? UiState.Success)?.data
                    Text(
                        s?.details?.posts?.firstOrNull()?.subject ?: "/$board/$threadNo",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val s = (state as? UiState.Success)?.data
                    IconButton(onClick = viewModel::onToggleBookmark) {
                        Icon(
                            if (s?.bookmarked == true) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            stringResource(
                                if (s?.bookmarked == true) R.string.thread_remove_bookmark else R.string.thread_bookmark
                            ),
                        )
                    }
                    IconButton(onClick = { viewModel.load(forceRefresh = true) }) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        val webUrl = Urls.threadWebUrl(board, threadNo)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.thread_share)) },
                            onClick = {
                                menuOpen = false
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, webUrl)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.thread_copy_link)) },
                            onClick = {
                                menuOpen = false
                                clipboard.setText(AnnotatedString(webUrl))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.thread_search_in_thread)) },
                            onClick = { menuOpen = false; searchOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.thread_open_in_browser)) },
                            onClick = { menuOpen = false; openExternal(webUrl) },
                        )
                        DropdownMenuItem(
                            text = {
                                val enabled = (state as? UiState.Success)?.data?.autoRefreshEnabled == true
                                Text(stringResource(R.string.thread_auto_refresh) + if (enabled) " ✓" else "")
                            },
                            onClick = { menuOpen = false; viewModel.onToggleAutoRefresh() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            UiStateContent(state, onRetry = { viewModel.load() }) { s ->
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = spacing.md),
                        ) {
                            OutlinedTextField(
                                value = s.searchQuery.orEmpty(),
                                onValueChange = { viewModel.onSearchChange(it) },
                                placeholder = { Text(stringResource(R.string.thread_search_in_thread)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (s.searchMatches.isEmpty()) "0/0"
                                else "${s.searchIndex + 1}/${s.searchMatches.size}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            IconButton(onClick = {
                                viewModel.onSearchStep(-1)
                                scrollToMatch(s, -1, listState, scope)
                            }) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.thread_search_prev)) }
                            IconButton(onClick = {
                                viewModel.onSearchStep(1)
                                scrollToMatch(s, 1, listState, scope)
                            }) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.thread_search_next)) }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.md),
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
                            PostCard(
                                post = post,
                                board = s.board,
                                backlinkCount = s.details.backlinks[post.no]?.size ?: 0,
                                saveStatus = post.media?.fullUrl?.let { s.mediaSaveStatuses[it] },
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
                                onThumbnailTap = {
                                    val media = post.media ?: return@PostCard
                                    if (media.spoiler && !s.revealAllSpoilers && post.no !in s.revealedImageSpoilers) {
                                        viewModel.onRevealImageSpoiler(post.no)
                                    } else {
                                        onOpenMedia(post.no)
                                    }
                                },
                                onBacklinksTap = { viewModel.onOpenBacklinks(post.no) },
                                onCopyPostNo = {
                                    clipboard.setText(AnnotatedString(post.no.toString()))
                                    scope.launch { snackbar.showSnackbar(copiedMessage) }
                                },
                                highlight = s.searchQuery,
                            )
                        }
                    }
                }

                // Quotelink preview card stack (D11) — overlay, level3 elevation.
                // System back pops one preview instead of leaving the thread.
                androidx.activity.compose.BackHandler(enabled = s.previewStack.isNotEmpty()) {
                    viewModel.onClosePreview()
                }
                if (s.previewStack.isNotEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { viewModel.onClosePreview() },
                        contentAlignment = Alignment.Center,
                    ) {
                        val group = s.previewStack.last()
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            modifier = Modifier
                                .padding(spacing.xl)
                                .heightIn(max = 480.dp)
                                .clickable(enabled = false) {},
                        ) {
                            Column(
                                Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                group.forEach { post ->
                                    PostCard(
                                        post = post,
                                        board = s.board,
                                        backlinkCount = 0,
                                        saveStatus = post.media?.fullUrl?.let { s.mediaSaveStatuses[it] },
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
                                        onThumbnailTap = { onOpenMedia(post.no) },
                                        onBacklinksTap = {},
                                        onCopyPostNo = {},
                                    )
                                }
                            }
                        }
                    }
                }

                // External-link confirmation (D26).
                s.pendingExternalUrl?.let { url ->
                    AlertDialog(
                        onDismissRequest = viewModel::onDismissLinkDialog,
                        title = { Text(stringResource(R.string.link_dialog_title)) },
                        text = { Text(url) },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.onDismissLinkDialog()
                                openExternal(url)
                            }) { Text(stringResource(R.string.action_open)) }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = {
                                    viewModel.onTrustDomain(url)
                                    openExternal(url)
                                }) {
                                    Text(
                                        stringResource(
                                            R.string.link_always_trust,
                                            Urls.domainOf(url) ?: "",
                                        )
                                    )
                                }
                                TextButton(onClick = viewModel::onDismissLinkDialog) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun scrollToMatch(
    s: ThreadContent,
    delta: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (s.searchMatches.isEmpty()) return
    val next = (s.searchIndex + delta).mod(s.searchMatches.size)
    val target = s.searchMatches[next]
    val index = s.details.posts.indexOfFirst { it.no == target }
    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
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
