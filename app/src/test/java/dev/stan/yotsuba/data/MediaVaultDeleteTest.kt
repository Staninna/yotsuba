package dev.stan.yotsuba.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.core.backup.StorageAccessCheck
import dev.stan.yotsuba.core.database.YotsubaDatabase
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.core.media.GalleryExporter
import dev.stan.yotsuba.core.media.MediaByteSource
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultMetaCodec
import dev.stan.yotsuba.core.vault.VideoStills
import dev.stan.yotsuba.data.repository.LocalThreadImporter
import dev.stan.yotsuba.data.repository.MediaVaultRepositoryImpl
import dev.stan.yotsuba.data.repository.VaultStore
import dev.stan.yotsuba.data.repository.VaultTrash
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultPaths
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** Deleting a saved video takes its captured still with it, not only the file and its row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaVaultDeleteTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: YotsubaDatabase
    private lateinit var repo: MediaVaultRepositoryImpl
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        val store = VaultStore(tmp.root)
        repo = MediaVaultRepositoryImpl(
            savedMediaDao = db.savedMediaDao(),
            store = store,
            vaultTrash = VaultTrash(store, db.savedMediaDao()),
            localImporter = LocalThreadImporter(context, store, db.savedMediaDao()),
            galleryExporter = GalleryExporter(context),
            byteSource = MediaByteSource(context, OkHttpClient()),
            threadRepository = threads,
            preferences = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("t.preferences_pb") },
            settings = settings,
            storageCheck = StorageAccessCheck { false },
            runMigration = {},
        )
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
    }

    private fun saved(dir: File, name: String, url: String): File {
        val file = File(dir, name).apply { writeText("bytes") }
        VaultStore(tmp.root).updateMeta(dir) { it.copy(threadNo = 1).upsert(VaultFileMeta(fileName = name, url = url)) }
        return file
    }

    private fun row(file: File, url: String) = SavedMediaEntity(
        url = url, board = "g", threadNo = 1, postNo = 2, subject = null, displayName = file.name,
        absolutePath = file.absolutePath, ext = VaultPaths.extensionOf(file.name), sizeBytes = 5, width = null,
        height = null, thumbnailUrl = null, savedAt = 0,
    )

    @Test fun `the trash survives a new instance and gives the file back`() = runTest {
        val dir = File(File(tmp.root, "g"), "1 - Cats").apply { mkdirs() }
        val pic = saved(dir, "2_pic.jpg", "https://i.4cdn.org/g/2.jpg")
        db.savedMediaDao().insert(row(pic, "https://i.4cdn.org/g/2.jpg"))

        assertNull(repo.trash("https://i.4cdn.org/g/2.jpg"))
        assertFalse(pic.exists())
        assertNull(db.savedMediaDao().byUrl("https://i.4cdn.org/g/2.jpg"))
        assertTrue(File(File(tmp.root, VaultPaths.TRASH_DIR_NAME), VaultTrash.INDEX_FILE_NAME).isFile)

        // A fresh instance, as after a process death, reads the index from disk.
        val again = VaultTrash(VaultStore(tmp.root), db.savedMediaDao())
        again.warm()
        assertEquals(listOf("https://i.4cdn.org/g/2.jpg"), again.entries.value.map { it.url })
        assertNull(again.restore("https://i.4cdn.org/g/2.jpg"))

        assertTrue(pic.isFile)
        assertNotNull(db.savedMediaDao().byUrl("https://i.4cdn.org/g/2.jpg"))
        assertTrue(again.entries.value.isEmpty())
        val meta = VaultMetaCodec.decode(File(dir, VaultPaths.META_FILE_NAME).readText())!!
        assertTrue(meta.files.any { it.fileName == "2_pic.jpg" })
    }

    @Test fun `purging drops what is older than a week and keeps the rest`() = runTest {
        val dir = File(File(tmp.root, "g"), "1 - Cats").apply { mkdirs() }
        val old = saved(dir, "2_old.jpg", "https://i.4cdn.org/g/2.jpg")
        val young = saved(dir, "3_young.jpg", "https://i.4cdn.org/g/3.jpg")
        val trash = VaultTrash(VaultStore(tmp.root), db.savedMediaDao())
        val now = 10_000_000_000L
        assertNull(trash.trash(row(old, "https://i.4cdn.org/g/2.jpg"), now = now - VaultTrash.RETENTION_MS))
        assertNull(trash.trash(row(young, "https://i.4cdn.org/g/3.jpg"), now = now - 1))

        trash.purgeExpired(now)

        assertEquals(listOf("https://i.4cdn.org/g/3.jpg"), trash.entries.value.map { it.url })
        val trashDir = File(tmp.root, VaultPaths.TRASH_DIR_NAME)
        assertEquals(1, trashDir.listFiles { f -> f.name.endsWith(".jpg") }!!.size)
        assertEquals(VaultError.NotFound, trash.restore("https://i.4cdn.org/g/2.jpg"))
        assertNull(trash.restore("https://i.4cdn.org/g/3.jpg"))
        assertTrue(young.isFile)

        trash.empty()
        assertTrue(trash.entries.value.isEmpty())
        assertFalse(trashDir.exists())
    }

    @Test fun `delete removes the video's still along with the file`() = runTest {
        val dir = File(File(tmp.root, "g"), "1 - Cats").apply { mkdirs() }
        val video = saved(dir, "2_clip.webm", "https://i.4cdn.org/g/2.webm")
        val other = saved(dir, "3_pic.jpg", "https://i.4cdn.org/g/3.jpg")
        val still = VideoStills.stillFor(video).apply { parentFile!!.mkdirs(); writeText("jpeg") }
        db.savedMediaDao().insert(row(video, "https://i.4cdn.org/g/2.webm"))
        db.savedMediaDao().insert(row(other, "https://i.4cdn.org/g/3.jpg"))

        assertNull(repo.delete("https://i.4cdn.org/g/2.webm"))

        assertFalse(video.exists())
        assertFalse(still.exists())
        assertTrue("the other file keeps the thread dir alive", other.isFile)
        assertNull(db.savedMediaDao().byUrl("https://i.4cdn.org/g/2.webm"))
        val meta = VaultMetaCodec.decode(File(dir, VaultPaths.META_FILE_NAME).readText())!!
        assertTrue(meta.files.none { it.fileName == "2_clip.webm" })
    }
}
