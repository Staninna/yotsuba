package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.ChipRow
import dev.stan.yotsuba.core.designsystem.component.SectionHeader
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.SeekStep
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.feature.settings.labelRes

@Composable
fun MediaSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    val spacing = LocalSpacing.current

    SwitchRow(
        title = stringResource(R.string.settings_data_saver),
        summary = stringResource(R.string.settings_data_saver_summary),
        checked = settings.dataSaver,
        onToggle = { v -> update { it.copy(dataSaver = v) } },
    )

    SectionHeader(stringResource(R.string.settings_video))
    ChipRow(
        label = stringResource(R.string.settings_media_autoplay),
        options = MediaAutoplay.entries,
        selected = settings.mediaAutoplay,
        onSelect = { v -> update { it.copy(mediaAutoplay = v) } },
        labelOf = { stringResource(it.labelRes) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_keep_screen_on),
        summary = stringResource(R.string.settings_keep_screen_on_summary),
        checked = settings.keepScreenOnWhileWatching,
        onToggle = { v -> update { it.copy(keepScreenOnWhileWatching = v) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_double_tap_seek),
        summary = stringResource(R.string.settings_double_tap_seek_summary),
        checked = settings.doubleTapSeekEnabled,
        onToggle = { v -> update { it.copy(doubleTapSeekEnabled = v) } },
    )
    ChipRow(
        label = stringResource(R.string.settings_seek_step),
        options = SeekStep.entries,
        selected = settings.seekStep,
        onSelect = { v -> update { it.copy(seekStep = v) } },
        enabled = settings.doubleTapSeekEnabled,
        labelOf = { stringResource(R.string.settings_seek_step_seconds, it.seconds) },
    )
    Text(
        stringResource(R.string.settings_seek_step_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
    )

    SectionHeader(stringResource(R.string.settings_saving))
    SwitchRow(
        title = stringResource(R.string.settings_hold_to_save),
        summary = stringResource(R.string.settings_hold_to_save_summary),
        checked = settings.holdToSave,
        onToggle = { v -> update { it.copy(holdToSave = v) } },
    )
    Text(
        stringResource(R.string.settings_hold_to_save_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
    )
    SwitchRow(
        title = stringResource(R.string.settings_save_replies),
        summary = stringResource(R.string.settings_save_replies_summary),
        checked = settings.saveRepliesWithMedia,
        onToggle = { v -> update { it.copy(saveRepliesWithMedia = v) } },
    )
}
