package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.ChipRow
import dev.stan.yotsuba.core.designsystem.component.SectionHeader
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.core.designsystem.theme.LocalYotsubaColors
import dev.stan.yotsuba.core.designsystem.theme.postTypography
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.FontSize
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.LineSpacing
import dev.stan.yotsuba.domain.model.QuoteTapAction
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.feature.settings.labelRes

@Composable
fun ReadingSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    ChipRow(
        label = stringResource(R.string.settings_font_size),
        options = FontSize.entries,
        selected = settings.fontSize,
        onSelect = { v -> update { it.copy(fontSize = v) } },
        labelOf = { stringResource(it.labelRes) },
    )
    ChipRow(
        label = stringResource(R.string.settings_line_spacing),
        options = LineSpacing.entries,
        selected = settings.lineSpacing,
        onSelect = { v -> update { it.copy(lineSpacing = v) } },
        labelOf = { stringResource(it.labelRes) },
    )
    TextPreview()
    SwitchRow(
        title = stringResource(R.string.settings_auto_refresh),
        summary = stringResource(R.string.settings_auto_refresh_summary),
        checked = settings.autoRefreshEnabled,
        onToggle = { v -> update { it.copy(autoRefreshEnabled = v) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_collapse_read),
        summary = stringResource(R.string.settings_collapse_read_summary),
        checked = settings.collapseReadPosts,
        onToggle = { v -> update { it.copy(collapseReadPosts = v) } },
    )
    ChipRow(
        label = stringResource(R.string.settings_quote_tap),
        options = QuoteTapAction.entries,
        selected = settings.quoteTap,
        onSelect = { v -> update { it.copy(quoteTap = v) } },
        labelOf = { stringResource(it.labelRes) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_record_history),
        summary = null,
        checked = settings.recordHistory,
        onToggle = { v -> update { it.copy(recordHistory = v) } },
    )
    ChipRow(
        label = stringResource(R.string.settings_history_retention),
        options = HistoryRetention.entries,
        selected = settings.historyRetention,
        onSelect = { v -> update { it.copy(historyRetention = v) } },
        labelOf = { stringResource(it.labelRes) },
    )

    SectionHeader(stringResource(R.string.settings_bookmarks))
    ChipRow(
        label = stringResource(R.string.settings_bookmark_refresh),
        options = (BookmarkRefreshOptions + settings.bookmarkRefreshMinutes).distinct().sorted(),
        selected = settings.bookmarkRefreshMinutes,
        onSelect = { v -> update { it.copy(bookmarkRefreshMinutes = v) } },
        labelOf = { stringResource(R.string.settings_bookmark_refresh_minutes, it) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_bookmark_notifications),
        summary = stringResource(R.string.settings_bookmark_notifications_summary),
        checked = settings.bookmarkNotifications,
        onToggle = { v -> update { it.copy(bookmarkNotifications = v) } },
    )
}

/** The current value is included even if it is not one of these, so the chip row always has a selection. */
private val BookmarkRefreshOptions = listOf(15, 30, 60, 180)

/**
 * A two-line fake post in the post typography. The theme already carries the saved
 * values, so this tracks the chips above with no plumbing of its own.
 */
@Composable
private fun TextPreview() {
    val spacing = LocalSpacing.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
    ) {
        Column(Modifier.padding(spacing.md)) {
            Text(
                stringResource(R.string.settings_text_preview_greentext),
                style = postTypography.bodyMedium,
                color = LocalYotsubaColors.current.greentext,
            )
            Text(
                stringResource(R.string.settings_text_preview_line),
                style = postTypography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
