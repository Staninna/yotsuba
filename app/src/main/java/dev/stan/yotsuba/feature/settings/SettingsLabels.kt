package dev.stan.yotsuba.feature.settings

import dev.stan.yotsuba.R
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.ThemeMode

/** String resources for the settings enums, shared by the index summaries and the chip rows. */

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
