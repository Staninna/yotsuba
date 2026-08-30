package dev.stan.yotsuba.feature.thread

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.SearchField
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.feature.media.shareText
import dev.stan.yotsuba.feature.thread.components.ExternalLinkDialog
import dev.stan.yotsuba.feature.thread.components.PostActionSheet
import dev.stan.yotsuba.feature.thread.components.QuotePreviewSheet
import dev.stan.yotsuba.feature.thread.components.ThreadGallerySheet
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Everything the sheets and dialogs over the thread can ask of the screen. One instance,
 * remembered by the screen, so the overlays' inputs stay equal across recompositions.
 */
data class ThreadOverlayActions(
    val onClosePreview: () -> Unit,
    val onDismissPreview: () -> Unit,
    val onJumpToPost: (Long) -> Unit,
    val onFocusPreview: (Long) -> Unit,
    val onOpenThread: (board: String, threadNo: Long, postNo: Long?) -> Unit,
    val onClosePostSheet: () -> Unit,
    val onToggleClaimed: (Long) -> Unit,
    val onFilterPosterId: (String?) -> Unit,
    val onOpenMediaFromGallery: (ThreadPost) -> Unit,
    /** Save-all from the gallery, over the posts it is showing. */
    val onSaveAll: (List<ThreadPost>) -> Unit,
    val onCloseGallery: () -> Unit,
    val onDismissLinkDialog: () -> Unit,
    val onOpenExternal: (String) -> Unit,
    val onTrustDomain: (String) -> Unit,
    val onCloseSearch: () -> Unit,
)

/**
 * The layers over the list: the quote preview sheet, the post action sheet, the gallery
 * sheet and the external-link dialog, each shown from [s], plus the back handling that
 * steps them (and the search bar) back before system back leaves the thread.
 */
@Composable
fun ThreadOverlays(
    s: ThreadContent,
    board: String,
    threadNo: Long,
    searchOpen: Boolean,
    actions: ThreadOverlayActions,
    snackbar: SnackbarHostState,
    postCard: @Composable (ThreadPost, inPreview: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val textCopiedMessage = stringResource(R.string.thread_text_copied)
    val imageUrlCopiedMessage = stringResource(R.string.thread_image_url_copied)

    // System back steps the preview sheet back one post instead of leaving the thread.
    BackHandler(enabled = s.preview != null) {
        actions.onClosePreview()
    }
    // ...and closes the search bar before leaving the thread.
    BackHandler(enabled = searchOpen && s.preview == null) {
        actions.onCloseSearch()
    }
    s.preview?.let { preview ->
        QuotePreviewSheet(
            preview = preview,
            onDismiss = actions.onDismissPreview,
            onBack = actions.onClosePreview,
            onGoTo = actions.onJumpToPost,
            onOpenThread = actions.onOpenThread,
            onFocus = actions.onFocusPreview,
            postCard = { post -> postCard(post, true) },
        )
    }

    s.postSheet?.let { post ->
        PostActionSheet(
            post = post,
            claimed = post.no in s.claimedPostNos,
            showFilterById = s.board?.userIds == true,
            onCopyText = {
                actions.onClosePostSheet()
                clipboard.setText(AnnotatedString(post.body.plainText))
                scope.launch { snackbar.showSnackbar(textCopiedMessage) }
            },
            onShareLink = {
                actions.onClosePostSheet()
                shareText(context, "${Urls.threadWebUrl(board, threadNo)}#p${post.no}")
            },
            onCopyImageUrl = {
                actions.onClosePostSheet()
                post.presentMedia?.let { clipboard.setText(AnnotatedString(it.fullUrl)) }
                scope.launch { snackbar.showSnackbar(imageUrlCopiedMessage) }
            },
            onToggleClaimed = {
                actions.onClosePostSheet()
                actions.onToggleClaimed(post.no)
            },
            onFilterById = {
                actions.onClosePostSheet()
                actions.onFilterPosterId(post.posterId)
            },
            onDismiss = actions.onClosePostSheet,
        )
    }

    if (s.galleryOpen) {
        ThreadGallerySheet(
            posts = s.mediaPosts,
            revealAll = s.revealAllSpoilers,
            boardAllowsAudio = s.board?.webmAudio == true,
            onOpen = actions.onOpenMediaFromGallery,
            onSaveAll = actions.onSaveAll,
            onDismiss = actions.onCloseGallery,
        )
    }

    s.pendingExternalUrl?.let { url ->
        ExternalLinkDialog(
            url = url,
            onOpen = {
                actions.onDismissLinkDialog()
                actions.onOpenExternal(url)
            },
            onTrustDomain = {
                actions.onTrustDomain(url)
                actions.onOpenExternal(url)
            },
            onDismiss = actions.onDismissLinkDialog,
        )
    }
}

/** The in-thread search row above the list: query, hit counter, previous/next, close. */
@Composable
fun SearchBar(
    query: String?,
    matchCount: Int,
    matchIndex: Int,
    onQueryChange: (String?) -> Unit,
    onStep: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = spacing.md),
    ) {
        SearchField(
            value = query.orEmpty(),
            onValueChange = { onQueryChange(it) },
            hintRes = R.string.thread_search_in_thread,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (matchCount == 0) "0/0" else "${matchIndex + 1}/$matchCount",
            style = MaterialTheme.typography.labelMedium,
        )
        IconButton(onClick = { onStep(-1) }) {
            Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.thread_search_prev))
        }
        IconButton(onClick = { onStep(1) }) {
            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.thread_search_next))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, stringResource(R.string.thread_search_close))
        }
    }
}

/** The strip above the list saying this copy is offline or archived; nothing for a live thread. */
@Composable
fun ThreadNotice(s: ThreadContent) {
    val notice = if (s.details.offlineCopy) {
        val date = remember(s.offlineCopyAt) {
            s.offlineCopyAt?.let { DateFormat.getDateInstance().format(Date(it)) }
        }
        if (date != null) stringResource(R.string.thread_offline_copy_from, date)
        else stringResource(R.string.thread_offline_copy)
    } else if (s.archivedNotice) {
        s.details.archive?.let { stringResource(R.string.thread_archived_from, it.label) }
            ?: stringResource(R.string.thread_archived)
    } else {
        return
    }
    val spacing = LocalSpacing.current
    Text(
        notice,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(spacing.sm),
    )
}
