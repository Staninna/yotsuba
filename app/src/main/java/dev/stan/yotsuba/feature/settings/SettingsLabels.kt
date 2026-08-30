package dev.stan.yotsuba.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.ui.graphics.vector.ImageVector
import dev.stan.yotsuba.R
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.FilterAction
import dev.stan.yotsuba.domain.model.FilterField
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.QuoteTapAction
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.navigation.SettingsSectionId

/** String resources for the settings enums, shared by the index summaries and the chip rows. */

internal val SettingsSectionId.titleRes: Int
    get() = when (this) {
        SettingsSectionId.APPEARANCE -> R.string.settings_appearance
        SettingsSectionId.READING -> R.string.settings_reading
        SettingsSectionId.MEDIA -> R.string.settings_media
        SettingsSectionId.BOARDS -> R.string.settings_boards
        SettingsSectionId.LINKS -> R.string.settings_links
        SettingsSectionId.PRIVACY -> R.string.settings_privacy
        SettingsSectionId.FILTERS -> R.string.settings_filters
        SettingsSectionId.STORAGE -> R.string.settings_storage
        SettingsSectionId.UPDATES -> R.string.settings_updates
        SettingsSectionId.ABOUT -> R.string.settings_about
    }

internal val SettingsSectionId.icon: ImageVector
    get() = when (this) {
        SettingsSectionId.APPEARANCE -> Icons.Outlined.Palette
        SettingsSectionId.READING -> Icons.Outlined.MenuBook
        SettingsSectionId.MEDIA -> Icons.Outlined.PlayCircleOutline
        SettingsSectionId.BOARDS -> Icons.Outlined.GridView
        SettingsSectionId.LINKS -> Icons.Outlined.Shield
        SettingsSectionId.PRIVACY -> Icons.Outlined.Lock
        SettingsSectionId.FILTERS -> Icons.Outlined.FilterAlt
        SettingsSectionId.STORAGE -> Icons.Outlined.Storage
        SettingsSectionId.UPDATES -> Icons.Outlined.SystemUpdateAlt
        SettingsSectionId.ABOUT -> Icons.Outlined.Info
    }

internal val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

internal val CatalogLayout.labelRes: Int
    get() = when (this) {
        CatalogLayout.COMFORTABLE -> R.string.settings_layout_comfortable
        CatalogLayout.COMPACT -> R.string.settings_layout_compact
        CatalogLayout.LIST -> R.string.settings_layout_list
    }

internal val MediaAutoplay.labelRes: Int
    get() = when (this) {
        MediaAutoplay.ALWAYS -> R.string.settings_autoplay_always
        MediaAutoplay.UNMETERED_ONLY -> R.string.settings_autoplay_unmetered
        MediaAutoplay.NEVER -> R.string.settings_autoplay_never
    }

internal val HistoryRetention.labelRes: Int
    get() = when (this) {
        HistoryRetention.FOREVER -> R.string.settings_retention_forever
        HistoryRetention.DAYS_30 -> R.string.settings_retention_30d
        HistoryRetention.DAYS_7 -> R.string.settings_retention_7d
    }

internal val QuoteTapAction.labelRes: Int
    get() = when (this) {
        QuoteTapAction.POPOVER -> R.string.settings_quote_tap_popover
        QuoteTapAction.JUMP -> R.string.settings_quote_tap_jump
    }

/** Label for a "lock again after" choice; 0 is "right away". */
@Composable
internal fun appLockDelayLabel(seconds: Int): String = when {
    seconds == 0 -> stringResource(R.string.settings_app_lock_delay_now)
    seconds % 60 == 0 -> stringResource(R.string.settings_app_lock_delay_minutes, seconds / 60)
    else -> stringResource(R.string.settings_app_lock_delay_seconds, seconds)
}

internal val FilterField.labelRes: Int
    get() = when (this) {
        FilterField.SUBJECT -> R.string.filters_field_subject
        FilterField.COMMENT -> R.string.filters_field_comment
        FilterField.NAME -> R.string.filters_field_name
        FilterField.TRIPCODE -> R.string.filters_field_tripcode
        FilterField.FLAG -> R.string.filters_field_flag
        FilterField.POSTER_ID -> R.string.filters_field_poster_id
        FilterField.FILENAME -> R.string.filters_field_filename
    }

internal val FilterAction.labelRes: Int
    get() = when (this) {
        FilterAction.HIDE -> R.string.filters_action_hide
        FilterAction.STUB -> R.string.filters_action_stub
    }
