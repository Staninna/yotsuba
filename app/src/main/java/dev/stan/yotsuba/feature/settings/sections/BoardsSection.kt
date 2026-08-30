package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.TextRow
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.threadKey
import dev.stan.yotsuba.feature.settings.ManagedListDialog

@Composable
fun BoardsSection(
    hiddenThreads: List<HiddenThread>,
    onHideNsfwBoards: () -> Unit,
    onUnhideThread: (HiddenThread) -> Unit,
    confirmThen: (Int, () -> Unit) -> Unit,
) {
    var showHidden by remember { mutableStateOf(false) }

    TextRow(
        title = stringResource(R.string.settings_hide_nsfw),
        summary = stringResource(R.string.settings_hide_nsfw_summary),
        onClick = { confirmThen(R.string.settings_confirm_hide_nsfw_body, onHideNsfwBoards) },
    )
    TextRow(stringResource(R.string.settings_hidden_threads, hiddenThreads.size)) {
        showHidden = true
    }

    if (showHidden) {
        ManagedListDialog(
            title = stringResource(R.string.settings_hidden_threads, hiddenThreads.size),
            items = hiddenThreads,
            key = { threadKey(it.board, it.threadNo) },
            itemLabel = { "/${it.board}/${it.threadNo}" },
            removeLabel = stringResource(R.string.settings_unhide),
            onRemove = onUnhideThread,
            onDismiss = { showHidden = false },
        )
    }
}
