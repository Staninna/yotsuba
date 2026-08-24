package dev.stan.yotsuba.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import dev.stan.yotsuba.core.datastore.SettingsDataStore
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
}
