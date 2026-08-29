package dev.stan.yotsuba.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.stan.yotsuba.core.backup.ApplicationScope
import dev.stan.yotsuba.core.backup.BackupCodec
import dev.stan.yotsuba.core.backup.BackupFile
import dev.stan.yotsuba.core.backup.StorageAccessCheck
import dev.stan.yotsuba.domain.repository.BackupInfo
import dev.stan.yotsuba.domain.repository.BackupRepository
import dev.stan.yotsuba.domain.repository.BackupResult
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val store: VaultStore,
    private val bookmarks: BookmarkRepository,
    private val hiddenThreads: HiddenThreadsRepository,
    private val settings: SettingsRepository,
    private val preferences: DataStore<Preferences>,
    private val storageAccess: StorageAccessCheck,
    @ApplicationScope scope: CoroutineScope,
) : BackupRepository {

    /**
     * Any change to what the backup holds re-exports it, once the burst settles. The first
     * emission is the current state, not a change, so it is skipped.
     */
    @OptIn(FlowPreview::class)
    private val autoExport = combine(
        bookmarks.bookmarks, hiddenThreads.all, settings.settings,
    ) { b, h, s -> Triple(b, h, s) }
        .drop(1)
        .debounce(AUTO_EXPORT_DEBOUNCE_MS)
        .onEach { export() }
        .launchIn(scope)

    /** Test seam: the impl reads and writes under [store]'s root by default. */
    internal var rootOverride: File? = null
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val file: File get() = File(rootOverride ?: store.root, BackupFile.FILE_NAME)
    private val lock = Mutex()

    override suspend fun export(): BackupResult = withContext(ioDispatcher) {
        lock.withLock {
            if (!storageAccess.granted()) return@withLock BackupResult.NoAccess
            try {
                val now = System.currentTimeMillis()
                val snapshot = BackupCodec.build(
                    exportedAt = now,
                    settings = settings.settings.first(),
                    bookmarks = bookmarks.bookmarks.first(),
                    hidden = hiddenThreads.all.first(),
                )
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(BackupCodec.encode(snapshot))
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
                BackupResult.Exported(now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    override suspend fun import(): BackupResult = withContext(ioDispatcher) {
        lock.withLock {
            if (!storageAccess.granted()) return@withLock BackupResult.NoAccess
            val backup = try {
                if (!file.isFile) return@withLock BackupResult.NoBackup
                BackupCodec.decode(file.readText())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock BackupResult.Failed(e.message ?: e.javaClass.simpleName)
            }
            val merged = BackupCodec.mergeBookmarks(bookmarks.bookmarks.first(), backup.bookmarks)
            merged.forEach { bookmarks.add(it) }
            val hidden = BackupCodec.newHiddenThreads(hiddenThreads.all.first(), backup.hiddenThreads)
            hidden.forEach { hiddenThreads.hide(it.board, it.threadNo) }
            settings.update { backup.settings }
            BackupResult.Imported(bookmarks = merged.size, hiddenThreads = hidden.size)
        }
    }

    override suspend fun available(): BackupInfo? = withContext(ioDispatcher) {
        if (!storageAccess.granted()) return@withContext null
        runCatching {
            if (!file.isFile) null else BackupInfo(BackupCodec.decode(file.readText()).exportedAt)
        }.getOrNull()
    }

    override suspend fun isFreshInstall(): Boolean =
        bookmarks.bookmarks.first().isEmpty() && preferences.data.first()[SETTINGS_BLOB] == null

    private companion object {
        const val AUTO_EXPORT_DEBOUNCE_MS = 5_000L

        /** Same key [dev.stan.yotsuba.core.datastore.SettingsDataStore] writes its blob under. */
        val SETTINGS_BLOB = stringPreferencesKey("settings")
    }
}
