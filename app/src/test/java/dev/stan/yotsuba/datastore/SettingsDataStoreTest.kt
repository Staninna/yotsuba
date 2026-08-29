package dev.stan.yotsuba.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import dev.stan.yotsuba.core.datastore.SettingsDataStore
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.SeekStep
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class SettingsDataStoreTest {

    @Test fun `defaults, write-read round trip, flow emission on change`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val file = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val store = SettingsDataStore(dataStore)

        store.settings.test {
            assertEquals(Settings(), awaitItem()) // defaults
            store.update { it.copy(themeMode = ThemeMode.DARK, catalogLayout = CatalogLayout.LIST) }
            val next = awaitItem()
            assertEquals(ThemeMode.DARK, next.themeMode)
            assertEquals(CatalogLayout.LIST, next.catalogLayout)
            store.update { it.copy(trustedDomains = setOf("example.com")) }
            assertEquals(setOf("example.com"), awaitItem().trustedDomains)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `viewer settings round trip, including the non-default booleans`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val file = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        val store = SettingsDataStore(PreferenceDataStoreFactory.create(scope = scope) { file })

        val defaults = Settings()
        assertEquals(SeekStep.TEN, defaults.seekStep)
        assertEquals(true, defaults.keepScreenOnWhileWatching)
        assertEquals(true, defaults.doubleTapSeekEnabled)
        assertEquals(true, defaults.holdToSave)
        assertEquals(true, defaults.saveRepliesWithMedia)

        store.settings.test {
            assertEquals(defaults, awaitItem())
            // Flipping every boolean away from its default catches a key written but never read.
            store.update {
                it.copy(
                    keepScreenOnWhileWatching = false,
                    doubleTapSeekEnabled = false,
                    seekStep = SeekStep.THIRTY,
                    holdToSave = false,
                    saveRepliesWithMedia = false,
                    mediaAutoplay = MediaAutoplay.NEVER,
                )
            }
            val next = awaitItem()
            assertEquals(false, next.keepScreenOnWhileWatching)
            assertEquals(false, next.doubleTapSeekEnabled)
            assertEquals(SeekStep.THIRTY, next.seekStep)
            assertEquals(false, next.holdToSave)
            assertEquals(false, next.saveRepliesWithMedia)
            assertEquals(MediaAutoplay.NEVER, next.mediaAutoplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `an unknown persisted enum name falls back to the default`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val file = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        dataStore.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("seekStep")] = "TWELVE" }

        assertEquals(SeekStep.TEN, SettingsDataStore(dataStore).settings.first().seekStep)
    }
}
