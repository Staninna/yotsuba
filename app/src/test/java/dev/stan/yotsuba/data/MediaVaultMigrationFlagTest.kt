package dev.stan.yotsuba.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.core.backup.StorageAccessCheck
import dev.stan.yotsuba.core.database.YotsubaDatabase
import dev.stan.yotsuba.core.media.GalleryExporter
import dev.stan.yotsuba.core.media.MediaByteSource
import dev.stan.yotsuba.data.repository.LocalThreadImporter
import dev.stan.yotsuba.data.repository.MediaVaultRepositoryImpl
import dev.stan.yotsuba.data.repository.VaultStore
import dev.stan.yotsuba.data.repository.VaultTrash
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "legacy vault migrated" flag is only earned by a migration that ran to the end. One
 * that threw halfway must run again next launch, or the files it did not move are never
 * indexed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaVaultMigrationFlagTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: YotsubaDatabase
    private lateinit var preferences: DataStore<Preferences>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val migrated = booleanPreferencesKey("vault_legacy_migrated_v1")

    private val threads = object : ThreadRepository {
        override suspend fun thread(board: String, no: Long, forceRefresh: Boolean): DataResult<ThreadDetails> =
            DataResult.Failure(NetworkError.NotFound)
    }
    private val settings = object : SettingsRepository {
        override val settings: Flow<Settings> = MutableStateFlow(Settings())
        override suspend fun update(transform: (Settings) -> Settings) = Unit
    }

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, YotsubaDatabase::class.java).allowMainThreadQueries().build()
        preferences = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("t.preferences_pb") }
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
    }

    private fun repo(migration: suspend () -> Unit): MediaVaultRepositoryImpl {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = VaultStore(tmp.root)
        return MediaVaultRepositoryImpl(
            savedMediaDao = db.savedMediaDao(),
            store = store,
            vaultTrash = VaultTrash(store, db.savedMediaDao()),
            localImporter = LocalThreadImporter(context, store, db.savedMediaDao()),
            galleryExporter = GalleryExporter(context),
            byteSource = MediaByteSource(context, OkHttpClient()),
            threadRepository = threads,
            preferences = preferences,
            settings = settings,
            storageCheck = StorageAccessCheck { true },
            runMigration = migration,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test fun `a migration that throws leaves the flag unset and runs again next time`() = runTest {
        var runs = 0
        val repo = repo {
            runs++
            if (runs == 1) throw IOException("disk went away")
        }

        repo.migrateLegacyIfNeeded()
        assertEquals(1, runs)
        assertNull("a failed migration is not done", preferences.data.first()[migrated])

        repo.migrateLegacyIfNeeded()
        assertEquals(2, runs)
        assertTrue(preferences.data.first()[migrated] == true)

        repo.migrateLegacyIfNeeded()
        assertEquals("once flagged it never runs again", 2, runs)
    }

    @Test fun `a migration that completes is flagged and not repeated`() = runTest {
        var runs = 0
        val repo = repo { runs++ }

        repo.migrateLegacyIfNeeded()
        repo.migrateLegacyIfNeeded()

        assertEquals(1, runs)
        assertTrue(preferences.data.first()[migrated] == true)
    }
}
