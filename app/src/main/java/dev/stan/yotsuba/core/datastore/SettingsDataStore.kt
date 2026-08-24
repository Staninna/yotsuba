package dev.stan.yotsuba.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.Density
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.domain.model.ThumbnailSize
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val themeMode = stringPreferencesKey("themeMode")
        val dynamicColor = booleanPreferencesKey("dynamicColor")
        val catalogLayout = stringPreferencesKey("catalogLayout")
        val thumbnailSize = stringPreferencesKey("thumbnailSize")
        val density = stringPreferencesKey("density")
        val revealAllSpoilers = booleanPreferencesKey("revealAllSpoilers")
        val autoRefreshEnabled = booleanPreferencesKey("autoRefreshEnabled")
        val confirmBeforeOpeningLinks = booleanPreferencesKey("confirmBeforeOpeningLinks")
        val trustedDomains = stringSetPreferencesKey("trustedDomains")
        val mediaAutoplay = stringPreferencesKey("mediaAutoplay")
        val recordHistory = booleanPreferencesKey("recordHistory")
        val historyRetention = stringPreferencesKey("historyRetention")
        val favouriteBoards = stringSetPreferencesKey("favouriteBoards")
        val hiddenBoards = stringSetPreferencesKey("hiddenBoards")
        val hiddenCategories = stringSetPreferencesKey("hiddenCategories")
    }

    override val settings: Flow<Settings> = dataStore.data.map(::snapshot)

    override suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { p ->
            val current = snapshot(p)
            val next = transform(current)
            p[Keys.themeMode] = next.themeMode.name
            p[Keys.dynamicColor] = next.dynamicColor
            p[Keys.catalogLayout] = next.catalogLayout.name
            p[Keys.thumbnailSize] = next.thumbnailSize.name
            p[Keys.density] = next.density.name
            p[Keys.revealAllSpoilers] = next.revealAllSpoilers
            p[Keys.autoRefreshEnabled] = next.autoRefreshEnabled
            p[Keys.confirmBeforeOpeningLinks] = next.confirmBeforeOpeningLinks
            p[Keys.trustedDomains] = next.trustedDomains
            p[Keys.mediaAutoplay] = next.mediaAutoplay.name
            p[Keys.recordHistory] = next.recordHistory
            p[Keys.historyRetention] = next.historyRetention.name
            p[Keys.favouriteBoards] = next.favouriteBoards
            p[Keys.hiddenBoards] = next.hiddenBoards
            p[Keys.hiddenCategories] = next.hiddenCategories
        }
    }

    private fun snapshot(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            themeMode = p[Keys.themeMode]?.let { enumOr(it, d.themeMode) } ?: d.themeMode,
            dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
            catalogLayout = p[Keys.catalogLayout]?.let { enumOr(it, d.catalogLayout) } ?: d.catalogLayout,
            thumbnailSize = p[Keys.thumbnailSize]?.let { enumOr(it, d.thumbnailSize) } ?: d.thumbnailSize,
            density = p[Keys.density]?.let { enumOr(it, d.density) } ?: d.density,
            revealAllSpoilers = p[Keys.revealAllSpoilers] ?: d.revealAllSpoilers,
            autoRefreshEnabled = p[Keys.autoRefreshEnabled] ?: d.autoRefreshEnabled,
            confirmBeforeOpeningLinks = p[Keys.confirmBeforeOpeningLinks] ?: d.confirmBeforeOpeningLinks,
            trustedDomains = p[Keys.trustedDomains] ?: d.trustedDomains,
            mediaAutoplay = p[Keys.mediaAutoplay]?.let { enumOr(it, d.mediaAutoplay) } ?: d.mediaAutoplay,
            recordHistory = p[Keys.recordHistory] ?: d.recordHistory,
            historyRetention = p[Keys.historyRetention]?.let { enumOr(it, d.historyRetention) } ?: d.historyRetention,
            favouriteBoards = p[Keys.favouriteBoards] ?: d.favouriteBoards,
            hiddenBoards = p[Keys.hiddenBoards] ?: d.hiddenBoards,
            hiddenCategories = p[Keys.hiddenCategories] ?: d.hiddenCategories,
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
}
