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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.stan.yotsuba.domain.model.HistoryRetention
import org.junit.Assert.assertNull
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
                    confirmVaultDelete = false,
                    bookmarkRefreshMinutes = 180,
                    bookmarkNotifications = false,
                    dataSaver = true,
                )
            }
            val next = awaitItem()
            assertEquals(false, next.confirmVaultDelete)
            assertEquals(180, next.bookmarkRefreshMinutes)
            assertEquals(false, next.bookmarkNotifications)
            assertEquals(true, next.dataSaver)
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
        dataStore.edit { it[stringPreferencesKey("settings")] = """{"seekStep":"TWELVE","themeMode":"DARK"}""" }

        val loaded = SettingsDataStore(dataStore).settings.first()
        assertEquals(SeekStep.TEN, loaded.seekStep)
        assertEquals(ThemeMode.DARK, loaded.themeMode)
    }

    @Test fun `a blob from a newer build with unknown keys still reads`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val file = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        dataStore.edit { it[stringPreferencesKey("settings")] = """{"fromTheFuture":1,"dynamicColor":false}""" }

        assertEquals(false, SettingsDataStore(dataStore).settings.first().dynamicColor)
    }

    @Test fun `a corrupt blob falls back to defaults rather than crashing`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val file = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        dataStore.edit { it[stringPreferencesKey("settings")] = "not json" }

        assertEquals(Settings(), SettingsDataStore(dataStore).settings.first())
    }

    @Test fun `one-preference-per-field settings migrate into the blob with values intact`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val file = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        dataStore.edit {
            it[stringPreferencesKey("themeMode")] = "LIGHT"
            it[booleanPreferencesKey("dynamicColor")] = false
            it[stringPreferencesKey("catalogLayout")] = "COMPACT"
            it[booleanPreferencesKey("autoRefreshEnabled")] = true
            it[stringSetPreferencesKey("trustedDomains")] = setOf("a.example", "b.example")
            it[stringPreferencesKey("mediaAutoplay")] = "NEVER"
            it[stringPreferencesKey("seekStep")] = "TWELVE" // unknown legacy enum name: same fallback as the blob
            it[booleanPreferencesKey("holdToSave")] = false
            it[stringPreferencesKey("historyRetention")] = "DAYS_7"
            it[stringSetPreferencesKey("hiddenBoards")] = setOf("b")
            it[stringPreferencesKey("bogus")] = "ignored" // a key this class doesn't own is left alone
        }

        val expected = Settings(
            themeMode = ThemeMode.LIGHT,
            dynamicColor = false,
            catalogLayout = CatalogLayout.COMPACT,
            autoRefreshEnabled = true,
            trustedDomains = setOf("a.example", "b.example"),
            mediaAutoplay = MediaAutoplay.NEVER,
            seekStep = SeekStep.TEN,
            holdToSave = false,
            historyRetention = HistoryRetention.DAYS_7,
            hiddenBoards = setOf("b"),
        )
        assertEquals(expected, SettingsDataStore(dataStore).settings.first())

        // The migration ran once: the legacy keys are gone, the blob carries the values, and
        // a key belonging to someone else survives untouched.
        val prefs = dataStore.data.first()
        assertNull(prefs[stringPreferencesKey("themeMode")])
        assertEquals(setOf("settings", "bogus"), prefs.asMap().keys.map { it.name }.toSet())
        assertEquals("ignored", prefs[stringPreferencesKey("bogus")])
        assertEquals(expected, SettingsDataStore(dataStore).settings.first())
    }

    @Test fun `a foreign key alone does not trigger the migration or get wiped`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val file = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val vaultFlag = booleanPreferencesKey("vault_legacy_migrated_v1")
        dataStore.edit { it[vaultFlag] = true }

        assertEquals(Settings(), SettingsDataStore(dataStore).settings.first())
        val prefs = dataStore.data.first()
        assertEquals(true, prefs[vaultFlag])
        assertNull(prefs[stringPreferencesKey("settings")])
    }
}
