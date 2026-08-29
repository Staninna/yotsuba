package dev.stan.yotsuba.feature.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.feature.thread.components.BodyTap
import dev.stan.yotsuba.feature.thread.components.PostCard

/**
 * A post and everything that replies to it (transitively), as a flat sub-thread.
 * Tapping a reply's text drills into that reply's own sub-thread; tapping a
 * thumbnail jumps the viewer to that media.
 */
@Composable
internal fun SubThreadPanel(
    rootPostNo: Long,
    depth: Int,
    state: MediaUiState,
    onOpenSubThread: (Long) -> Unit,
    onJumpToMedia: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val darkTheme = isSystemInDarkTheme()
    val root = state.posts[rootPostNo]
    val replies = state.graph.descendantsOf(rootPostNo)
    var revealedSpoilers by remember { mutableStateOf(setOf<Int>()) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.media_back))
                }
                Text(
                    ">>$rootPostNo",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (depth > 1) {
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        "·".repeat(depth - 1),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.media_replies, replies.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = spacing.md),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = spacing.md, end = spacing.md, top = spacing.xs, bottom = spacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                if (root != null) {
                    item(key = "root") {
                        SubThreadPost(
                            post = root,
                            state = state,
                            darkTheme = darkTheme,
                            revealedSpoilers = revealedSpoilers,
                            onOpenSubThread = onOpenSubThread,
                            onJumpToMedia = onJumpToMedia,
                            clickableBody = false,
                            onRevealSpoiler = { revealedSpoilers = revealedSpoilers + it },
                        )
                    }
                }
                items(replies.size, key = { replies[it].no }) { i ->
                    SubThreadPost(
                        post = replies[i],
                        state = state,
                        darkTheme = darkTheme,
                        revealedSpoilers = revealedSpoilers,
                        onOpenSubThread = onOpenSubThread,
                        onJumpToMedia = onJumpToMedia,
                        clickableBody = true,
                        onRevealSpoiler = { revealedSpoilers = revealedSpoilers + it },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubThreadPost(
    post: ThreadPost,
    state: MediaUiState,
    darkTheme: Boolean,
    revealedSpoilers: Set<Int>,
    onOpenSubThread: (Long) -> Unit,
    onJumpToMedia: (Long) -> Unit,
    clickableBody: Boolean,
    onRevealSpoiler: (Int) -> Unit,
) {
    PostCard(
        post = post,
        board = state.board,
        backlinkCount = state.backlinks[post.no].orEmpty().size,
        revealedSpoilerIds = revealedSpoilers,
        revealAll = false,
        imageSpoilerRevealed = true,
        darkTheme = darkTheme,
        onBodyTap = { tap ->
            when (tap) {
                is BodyTap.Spoiler -> onRevealSpoiler(tap.id)
                is BodyTap.SameThreadQuote -> onOpenSubThread(tap.postNo)
                else -> if (clickableBody) onOpenSubThread(post.no)
            }
        },
        onThumbnailTap = { post.media?.let { onJumpToMedia(post.no) } },
        onBacklinksTap = { onOpenSubThread(post.no) },
        onCopyPostNo = { if (clickableBody) onOpenSubThread(post.no) },
        modifier = if (clickableBody) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onOpenSubThread(post.no) }
        } else Modifier,
    )
}
