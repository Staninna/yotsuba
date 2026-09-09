package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.ChipRow
import dev.stan.yotsuba.core.designsystem.component.SectionHeader
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.core.designsystem.component.TextRow
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.BoardProfile
import dev.stan.yotsuba.domain.model.FontSize
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.LineSpacing
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.threadKey
import dev.stan.yotsuba.feature.settings.ManagedListDialog
import dev.stan.yotsuba.feature.settings.labelRes

/** A 4chan board code: short, lowercase, alphanumeric. */
private val BoardCode = Regex("[a-z0-9]{1,5}")

@Composable
fun BoardsSection(
    settings: Settings,
    update: ((Settings) -> Settings) -> Unit,
    hiddenThreads: List<HiddenThread>,
    onHideNsfwBoards: () -> Unit,
    onUnhideThread: (HiddenThread) -> Unit,
    confirmThen: (Int, () -> Unit) -> Unit,
) {
    var showHidden by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var newCode by remember { mutableStateOf("") }
    val spacing = LocalSpacing.current

    TextRow(
        title = stringResource(R.string.settings_hide_nsfw),
        summary = stringResource(R.string.settings_hide_nsfw_summary),
        onClick = { confirmThen(R.string.settings_confirm_hide_nsfw_body, onHideNsfwBoards) },
    )
    TextRow(stringResource(R.string.settings_hidden_threads, hiddenThreads.size)) {
        showHidden = true
    }
    SwitchRow(
        title = stringResource(R.string.boards_blur_thumbnails),
        summary = stringResource(R.string.boards_blur_thumbnails_summary),
        checked = settings.blurThumbnails,
        onToggle = { on -> update { it.copy(blurThumbnails = on) } },
    )

    SectionHeader(stringResource(R.string.boards_profiles))
    // Favourites are the boards worth a profile; the text field below covers the rest.
    (settings.favouriteBoards + settings.boardProfiles.keys).sorted().forEach { board ->
        val overrides = settings.boardProfiles[board]?.overrides ?: 0
        TextRow(
            title = "/$board/",
            summary = if (overrides == 0) stringResource(R.string.boards_profile_global)
            else pluralStringResource(R.plurals.boards_profile_overrides, overrides, overrides),
            onClick = { editing = board },
        )
    }
    OutlinedTextField(
        value = newCode,
        onValueChange = { newCode = it.trim().lowercase() },
        label = { Text(stringResource(R.string.boards_profile_add)) },
        supportingText = { Text(stringResource(R.string.boards_profiles_summary)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            if (BoardCode.matches(newCode)) {
                editing = newCode
                newCode = ""
            }
        }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm),
    )

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
    editing?.let { board ->
        BoardProfileDialog(
            board = board,
            profile = settings.boardProfiles[board] ?: BoardProfile(),
            onChange = { p ->
                update {
                    it.copy(boardProfiles = if (p.overrides == 0) it.boardProfiles - board else it.boardProfiles + (board to p))
                }
            },
            onDismiss = { editing = null },
        )
    }
}

/** One chip row per overridable field; the first chip on each is "Use global". */
@Composable
private fun BoardProfileDialog(
    board: String,
    profile: BoardProfile,
    onChange: (BoardProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val useGlobal = stringResource(R.string.boards_profile_use_global)
    val onOff: @Composable (Boolean?) -> String = {
        when (it) {
            null -> useGlobal
            true -> stringResource(R.string.boards_profile_on)
            false -> stringResource(R.string.boards_profile_off)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("/$board/") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChipRow(
                    label = stringResource(R.string.settings_font_size),
                    options = listOf<FontSize?>(null) + FontSize.entries,
                    selected = profile.fontSize,
                    onSelect = { onChange(profile.copy(fontSize = it)) },
                    labelOf = { it?.let { v -> stringResource(v.labelRes) } ?: useGlobal },
                )
                ChipRow(
                    label = stringResource(R.string.settings_line_spacing),
                    options = listOf<LineSpacing?>(null) + LineSpacing.entries,
                    selected = profile.lineSpacing,
                    onSelect = { onChange(profile.copy(lineSpacing = it)) },
                    labelOf = { it?.let { v -> stringResource(v.labelRes) } ?: useGlobal },
                )
                ChipRow(
                    label = stringResource(R.string.settings_media_autoplay),
                    options = listOf<MediaAutoplay?>(null) + MediaAutoplay.entries,
                    selected = profile.mediaAutoplay,
                    onSelect = { onChange(profile.copy(mediaAutoplay = it)) },
                    labelOf = { it?.let { v -> stringResource(v.labelRes) } ?: useGlobal },
                )
                ChipRow(
                    label = stringResource(R.string.settings_reveal_spoilers),
                    options = listOf(null, true, false),
                    selected = profile.revealAllSpoilers,
                    onSelect = { onChange(profile.copy(revealAllSpoilers = it)) },
                    labelOf = onOff,
                )
                ChipRow(
                    label = stringResource(R.string.settings_inline_images),
                    options = listOf(null, true, false),
                    selected = profile.inlineImageExpansion,
                    onSelect = { onChange(profile.copy(inlineImageExpansion = it)) },
                    labelOf = onOff,
                )
                ChipRow(
                    label = stringResource(R.string.boards_blur_thumbnails),
                    options = listOf(null, true, false),
                    selected = profile.blurThumbnails,
                    onSelect = { onChange(profile.copy(blurThumbnails = it)) },
                    labelOf = onOff,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = { onChange(BoardProfile()); onDismiss() }) {
                Text(stringResource(R.string.boards_profile_remove))
            }
        },
    )
}
