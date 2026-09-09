package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import kotlin.math.roundToInt

private const val PRECACHE_MIN = 1
private const val PRECACHE_MAX = 10

@Composable
fun MediaSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    val spacing = LocalSpacing.current

    SwitchRow(
        title = stringResource(R.string.settings_data_saver),
        summary = stringResource(R.string.settings_data_saver_summary),
        checked = settings.dataSaver,
        onToggle = { v -> update { it.copy(dataSaver = v) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_inline_images),
        summary = stringResource(R.string.settings_inline_images_summary),
        checked = settings.inlineImageExpansion,
        onToggle = { v -> update { it.copy(inlineImageExpansion = v) } },
    )
    // Data saver switches precaching off altogether, so its rows go dead with it.
    PrecacheRow(
        count = settings.precacheCount,
        enabled = !settings.dataSaver,
        onChange = { n -> update { it.copy(precacheCount = n) } },
    )
    SwitchRow(
        title = stringResource(R.string.media_precache_unmetered),
        summary = stringResource(R.string.media_precache_unmetered_summary),
        checked = settings.precacheUnmeteredOnly,
        enabled = !settings.dataSaver,
        onToggle = { v -> update { it.copy(precacheUnmeteredOnly = v) } },
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

/** "Precache ahead: 3 pages" over a stepped slider; the setting is written once, on release. */
@Composable
private fun PrecacheRow(count: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val spacing = LocalSpacing.current
    var dragging by remember { mutableStateOf<Int?>(null) }
    val shown = dragging ?: count
    Column(Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs)) {
        Text(
            stringResource(R.string.media_precache_count),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
        Text(
            pluralStringResource(R.plurals.media_precache_pages, shown, shown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
        )
        Slider(
            value = shown.toFloat(),
            onValueChange = { dragging = it.roundToInt() },
            onValueChangeFinished = {
                dragging?.takeIf { it != count }?.let(onChange)
                dragging = null
            },
            valueRange = PRECACHE_MIN.toFloat()..PRECACHE_MAX.toFloat(),
            steps = PRECACHE_MAX - PRECACHE_MIN - 1,
            enabled = enabled,
        )
    }
}
