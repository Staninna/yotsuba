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
import dev.stan.yotsuba.core.vault.VaultPostsCodec
import dev.stan.yotsuba.core.vault.VaultThreadMeta
import dev.stan.yotsuba.core.vault.VaultThreadPosts
import dev.stan.yotsuba.data.repository.LocalThreadImporter
import dev.stan.yotsuba.data.repository.MediaVaultRepositoryImpl
import dev.stan.yotsuba.data.repository.VaultStore
import dev.stan.yotsuba.data.repository.VaultTrash
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultPaths
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.vault.vaultPost
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * The vault's write path end to end on a temp folder: bytes land where the layout says,
 * the sidecars describe them, the index row follows, and a rescan can rebuild that row
 * from the sidecars alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaVaultWritePathTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: YotsubaDatabase
    private lateinit var store: VaultStore
    private lateinit var repo: MediaVaultRepositoryImpl
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var access = true

    private val threads = object : ThreadRepository {
        override suspend fun thread(board: String, no: Long, forceRefresh: Boolean): DataResult<ThreadDetails> =
            DataResult.Failure(NetworkError.NotFound)
    }
    private val settings = object : SettingsRepository {
        override val settings: Flow<Settings> = MutableStateFlow(Settings())
        override suspend fun update(transform: (Settings) -> Settings) = Unit
    }

    @Before fun setUp() {
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, YotsubaDatabase::class.java).allowMainThreadQueries().build()
        store = VaultStore(tmp.root)
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
            storageCheck = StorageAccessCheck { access },
            runMigration = {},
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
        server.shutdown()
    }

    private fun item(postNo: Long, name: String, body: String) = MediaItem(
        postNo = postNo,
        filename = name,
        ext = ".jpg",
        sizeBytes = body.length.toLong(),
        width = 800,
        height = 600,
        thumbnailUrl = server.url("/g/${postNo}s.jpg").toString(),
        fullUrl = server.url("/g/$postNo.jpg").toString(),
        spoiler = false,
    )

    private fun post(no: Long, item: MediaItem? = null, isOp: Boolean = false, quotes: List<Long> = emptyList()) =
        ThreadPost(
            board = "g", no = no, isOp = isOp, name = "Anonymous", tripcode = null, capcode = null,
            posterId = null, countryCode = null, countryName = null, timeSeconds = 1_700_000_000 + no,
            subject = if (isOp) "Cats" else null, body = PostText(listOf(PostSegment("post $no"))),
            media = item?.let { PostMedia.Present(it) }, quotedPostNos = quotes,
        )

    private fun context(post: ThreadPost, conversation: List<ThreadPost> = emptyList()) = VaultSaveContext(
        board = "g", threadNo = 1, threadSubject = "Cats", opExcerpt = null, post = post, conversation = conversation,
    )

    private fun meta(dir: File): VaultThreadMeta = VaultMetaCodec.decode(File(dir, VaultPaths.META_FILE_NAME).readText())!!
    private fun posts(dir: File): VaultThreadPosts? =
        File(dir, VaultPaths.POSTS_FILE_NAME).takeIf { it.isFile }?.let { VaultPostsCodec.decode(it.readText()) }

    private fun threadDir(board: String, name: String) = File(File(tmp.root, board), name).apply { mkdirs() }

    /** A file on disk plus its sidecar entry, as a rescan would find it. */
    private fun sidecarFile(dir: File, threadNo: Long, name: String, url: String, postNo: Long = 2, present: Boolean = true) {
        if (present) File(dir, name).writeText("bytes of $name")
        store.updateMeta(dir) {
            it.copy(threadNo = threadNo, subject = "Cats")
                .upsert(VaultFileMeta(fileName = name, url = url, postNo = postNo, ext = ".jpg", savedAtMillis = 5))
        }
    }

    @Test fun `save streams the file into the thread dir and records it everywhere`() = runTest {
        server.enqueue(MockResponse().setBody("jpeg bytes"))
        val pic = item(2, "cat", "jpeg bytes")
        val op = post(1, isOp = true)
        val poster = post(2, pic, quotes = listOf(1))

        assertNull(repo.save(pic, context(poster, conversation = listOf(op, poster))))

        val dir = File(File(tmp.root, "g"), "1 - Cats")
        val file = File(dir, "2_cat.jpg")
        assertEquals("jpeg bytes", file.readText())
        assertNull("no .part left behind", dir.listFiles()!!.firstOrNull { it.name.endsWith(".part") })

        val meta = meta(dir)
        assertEquals(1L, meta.threadNo)
        assertEquals("Cats", meta.subject)
        val entry = meta.files.single()
        assertEquals("2_cat.jpg", entry.fileName)
        assertEquals(pic.fullUrl, entry.url)
        assertEquals(2L, entry.postNo)
        assertEquals("post 2", entry.postText)

        assertEquals(listOf(1L, 2L), posts(dir)!!.posts.map { it.no })

        val row = db.savedMediaDao().byUrl(pic.fullUrl)!!
        assertEquals(file.absolutePath, row.absolutePath)
        assertEquals("g", row.board)
        assertEquals(1L, row.threadNo)
        assertEquals(2L, row.postNo)
    }

    @Test fun `saving the same name twice keeps both files`() = runTest {
        server.enqueue(MockResponse().setBody("first"))
        server.enqueue(MockResponse().setBody("second"))
        val first = item(2, "cat", "first")
        val second = item(3, "cat", "second").copy(postNo = 2)

        assertNull(repo.save(first, context(post(2, first))))
        assertNull(repo.save(second, context(post(2, second))))

        val dir = File(File(tmp.root, "g"), "1 - Cats")
        assertEquals("first", File(dir, "2_cat.jpg").readText())
        assertEquals("second", File(dir, "2_cat (1).jpg").readText())
        assertEquals(listOf("2_cat.jpg", "2_cat (1).jpg"), meta(dir).files.map { it.fileName })
        assertEquals(2, db.savedMediaDao().allOnce().size)
    }

    @Test fun `a failed download leaves no file, no sidecar and no row`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val pic = item(2, "cat", "")

        val error = repo.save(pic, context(post(2, pic)))

        assertTrue(error is VaultError.Io)
        val dir = File(File(tmp.root, "g"), "1 - Cats")
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        assertNull(db.savedMediaDao().byUrl(pic.fullUrl))
    }

    @Test fun `save without storage access touches nothing`() = runTest {
        access = false
        val pic = item(2, "cat", "")

        assertEquals(VaultError.NoAccess, repo.save(pic, context(post(2, pic))))

        assertEquals(0, server.requestCount)
        assertFalse(File(tmp.root, "g").exists())
    }

    @Test fun `rescan rebuilds the index from the sidecars and keeps the old hashes`() = runTest {
        val cats = threadDir("g", "1 - Cats")
        val dogs = threadDir("a", "7 - Dogs")
        sidecarFile(cats, 1, "2_cat.jpg", "https://i.4cdn.org/g/2.jpg", postNo = 2)
        sidecarFile(cats, 1, "3_gone.jpg", "https://i.4cdn.org/g/3.jpg", postNo = 3, present = false)
        sidecarFile(dogs, 7, "8_dog.jpg", "https://i.4cdn.org/a/8.jpg", postNo = 8)
        // A stale row for a file that is no longer on disk, and a hash only the old row knows.
        db.savedMediaDao().insertAll(
            listOf(
                row("https://i.4cdn.org/g/9.jpg", File(cats, "9_stale.jpg")),
                row("https://i.4cdn.org/g/2.jpg", File(cats, "2_cat.jpg")).copy(md5 = "abc", phash = 42L),
            ),
        )

        repo.rescan()

        val rows = db.savedMediaDao().allOnce().sortedBy { it.url }
        assertEquals(listOf("https://i.4cdn.org/a/8.jpg", "https://i.4cdn.org/g/2.jpg"), rows.map { it.url })
        val cat = rows.single { it.url.endsWith("/2.jpg") }
        assertEquals(File(cats, "2_cat.jpg").absolutePath, cat.absolutePath)
        assertEquals(1L, cat.threadNo)
        assertEquals("Cats", cat.subject)
        assertEquals("abc", cat.md5)
        assertEquals(42L, cat.phash)
        val dog = rows.single { it.url.endsWith("/8.jpg") }
        assertEquals("a", dog.board)
        assertEquals(7L, dog.threadNo)
    }

    @Test fun `rescan on a missing root leaves the index alone`() = runTest {
        val row = row("https://i.4cdn.org/g/2.jpg", File(tmp.root, "g/1/2_cat.jpg"))
        db.savedMediaDao().insert(row)
        tmp.root.deleteRecursively()

        repo.rescan()

        assertNotNull(db.savedMediaDao().byUrl(row.url))
    }

    @Test fun `renameThread moves an imported thread and re-points its rows`() = runTest {
        val local = VaultPaths.LOCAL_BOARD_NAME
        val dir = threadDir(local, "5 - Old name")
        sidecarFile(dir, 5, "1_a.jpg", "file:///a.jpg", postNo = 1)
        store.updatePosts(dir, local, 5, listOf(vaultPost(1, isOp = true)))
        repo.rescan()

        assertNull(repo.renameThread(local, 5, "  New name  "))

        val renamed = File(File(tmp.root, local), "5 - New name")
        assertTrue(renamed.isDirectory)
        assertFalse(dir.exists())
        assertEquals("New name", meta(renamed).subject)
        assertEquals("New name", posts(renamed)!!.posts.single().subject)
        assertEquals(File(renamed, "1_a.jpg").absolutePath, db.savedMediaDao().byUrl("file:///a.jpg")!!.absolutePath)
    }

    @Test fun `renameThread re-indexes only the renamed thread`() = runTest {
        val local = VaultPaths.LOCAL_BOARD_NAME
        val dir = threadDir(local, "5 - Old name")
        sidecarFile(dir, 5, "1_a.jpg", "file:///a.jpg", postNo = 1)
        val cats = threadDir("g", "1 - Cats")
        sidecarFile(cats, 1, "2_cat.jpg", "https://i.4cdn.org/g/2.jpg", postNo = 2)
        repo.rescan()
        // A row the sidecar cannot reproduce: a rescan would rebuild it from disk and lose
        // the marker, a targeted re-index must not go near it.
        val marker = db.savedMediaDao().byUrl("https://i.4cdn.org/g/2.jpg")!!
            .copy(md5 = "marker", phash = 7L, pixelSize = 9L, subject = "not what the sidecar says", savedAt = 123)
        db.savedMediaDao().insert(marker)
        val stale = db.savedMediaDao().byUrl("file:///a.jpg")!!.copy(md5 = "kept")
        db.savedMediaDao().insert(stale)

        assertNull(repo.renameThread(local, 5, "New name"))

        assertEquals(marker, db.savedMediaDao().byUrl("https://i.4cdn.org/g/2.jpg"))
        val moved = db.savedMediaDao().byUrl("file:///a.jpg")!!
        assertEquals(File(File(File(tmp.root, local), "5 - New name"), "1_a.jpg").absolutePath, moved.absolutePath)
        assertEquals("New name", moved.subject)
        assertEquals("kept", moved.md5)
        assertEquals(2, db.savedMediaDao().allOnce().size)
    }

    @Test fun `renameThread refuses live threads, blank names and unknown threads`() = runTest {
        val dir = threadDir("g", "1 - Cats")
        sidecarFile(dir, 1, "2_cat.jpg", "https://i.4cdn.org/g/2.jpg")

        assertTrue(repo.renameThread("g", 1, "Kittens") is VaultError.Io)
        assertTrue(dir.isDirectory)
        assertEquals(VaultError.NotFound, repo.renameThread(VaultPaths.LOCAL_BOARD_NAME, 99, "Nope"))

        val local = threadDir(VaultPaths.LOCAL_BOARD_NAME, "5 - Old")
        sidecarFile(local, 5, "1_a.jpg", "file:///a.jpg")
        assertTrue(repo.renameThread(VaultPaths.LOCAL_BOARD_NAME, 5, "   ") is VaultError.Io)
        assertTrue(local.isDirectory)
    }

    @Test fun `mergeThreads moves files and posts into the target and drops the source`() = runTest {
        val from = threadDir("g", "1 - Cats")
        val into = threadDir("g", "2 - More cats")
        sidecarFile(from, 1, "3_a.jpg", "https://i.4cdn.org/g/3.jpg", postNo = 3)
        sidecarFile(from, 1, "4_b.jpg", "https://i.4cdn.org/g/4.jpg", postNo = 4)
        sidecarFile(into, 2, "4_b.jpg", "https://i.4cdn.org/g/44.jpg", postNo = 4)
        store.updatePosts(from, "g", 1, listOf(vaultPost(1, isOp = true), vaultPost(3, listOf(1))))
        store.updatePosts(into, "g", 2, listOf(vaultPost(2, isOp = true)))
        repo.rescan()

        assertNull(repo.mergeThreads("g", 1, "g", 2))

        assertFalse(from.exists())
        assertEquals("bytes of 3_a.jpg", File(into, "3_a.jpg").readText())
        assertEquals("bytes of 4_b.jpg", File(into, "4_b.jpg").readText())
        assertEquals("bytes of 4_b.jpg", File(into, "4_b (1).jpg").readText())
        val files = meta(into).files.associateBy { it.url!! }
        assertEquals("4_b.jpg", files.getValue("https://i.4cdn.org/g/44.jpg").fileName)
        assertEquals("4_b (1).jpg", files.getValue("https://i.4cdn.org/g/4.jpg").fileName)
        assertEquals("3_a.jpg", files.getValue("https://i.4cdn.org/g/3.jpg").fileName)
        assertEquals(listOf(1L, 2L, 3L), posts(into)!!.posts.map { it.no })

        val rows = db.savedMediaDao().allOnce()
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.threadNo == 2L && File(it.absolutePath).parentFile == into })
        assertEquals(File(into, "4_b (1).jpg").absolutePath, rows.single { it.url.endsWith("/4.jpg") }.absolutePath)
    }

    @Test fun `mergeThreads keeps both conversations when imported threads share post numbers`() = runTest {
        val local = VaultPaths.LOCAL_BOARD_NAME
        val from = threadDir(local, "10 - Trip")
        val into = threadDir(local, "20 - Holiday")
        sidecarFile(from, 10, "1_a.jpg", "file:///trip/a.jpg", postNo = 1)
        sidecarFile(from, 10, "2_b.jpg", "file:///trip/b.jpg", postNo = 2)
        sidecarFile(into, 20, "1_c.jpg", "file:///holiday/c.jpg", postNo = 1)
        sidecarFile(into, 20, "2_d.jpg", "file:///holiday/d.jpg", postNo = 2)
        sidecarFile(into, 20, "3_e.jpg", "file:///holiday/e.jpg", postNo = 3)
        store.updatePosts(from, local, 10, listOf(vaultPost(1, isOp = true), vaultPost(2, listOf(1))))
        store.updatePosts(into, local, 20, listOf(vaultPost(1, isOp = true), vaultPost(2), vaultPost(3)))
        repo.rescan()

        assertNull(repo.mergeThreads(local, 10, local, 20))

        val merged = posts(into)!!.posts
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), merged.map { it.no })
        assertEquals(listOf(1L), merged.filter { it.isOp }.map { it.no })
        assertEquals("post 1", merged.single { it.no == 4L }.body.plainText)
        assertEquals(listOf(4L), merged.single { it.no == 5L }.quotedPostNos)
        val files = meta(into).files.associateBy { it.url!! }
        assertEquals(4L, files.getValue("file:///trip/a.jpg").postNo)
        assertEquals(5L, files.getValue("file:///trip/b.jpg").postNo)
        assertEquals(1L, files.getValue("file:///holiday/c.jpg").postNo)
        val rows = db.savedMediaDao().allOnce()
        assertEquals(5, rows.size)
        assertEquals(4L, rows.single { it.url == "file:///trip/a.jpg" }.postNo)
        assertTrue(rows.all { it.threadNo == 20L })
    }

    @Test fun `mergeThreads into itself or an unknown thread does nothing`() = runTest {
        val dir = threadDir("g", "1 - Cats")
        sidecarFile(dir, 1, "3_a.jpg", "https://i.4cdn.org/g/3.jpg")

        assertNull(repo.mergeThreads("g", 1, "g", 1))
        assertEquals(VaultError.NotFound, repo.mergeThreads("g", 1, "g", 2))
        assertEquals(VaultError.NotFound, repo.mergeThreads("g", 9, "g", 1))

        assertTrue(File(dir, "3_a.jpg").isFile)
    }

    private fun row(url: String, file: File) = SavedMediaEntity(
        url = url, board = "g", threadNo = 1, postNo = 2, subject = null, displayName = file.name,
        absolutePath = file.absolutePath, ext = ".jpg", sizeBytes = 5, width = null, height = null,
        thumbnailUrl = null, savedAt = 0,
    )
}
