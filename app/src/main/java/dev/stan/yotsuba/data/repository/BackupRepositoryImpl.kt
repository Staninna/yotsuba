package dev.stan.yotsuba.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.stan.yotsuba.core.backup.BackupCodec
import dev.stan.yotsuba.core.backup.BackupFile
import dev.stan.yotsuba.core.backup.StorageAccessCheck
import dev.stan.yotsuba.core.datastore.SettingsDataStore
import dev.stan.yotsuba.di.ApplicationScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class BackupRepositoryImpl(
    private val store: VaultStore,
    private val bookmarks: BookmarkRepository,
    private val hiddenThreads: HiddenThreadsRepository,
    private val settings: SettingsRepository,
    private val preferences: DataStore<Preferences>,
    private val storageAccess: StorageAccessCheck,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) : BackupRepository {

    @Inject constructor(
        store: VaultStore,
        bookmarks: BookmarkRepository,
        hiddenThreads: HiddenThreadsRepository,
        settings: SettingsRepository,
        preferences: DataStore<Preferences>,
        storageAccess: StorageAccessCheck,
        @ApplicationScope scope: CoroutineScope,
    ) : this(store, bookmarks, hiddenThreads, settings, preferences, storageAccess, scope, Dispatchers.IO)

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

    private val file: File get() = File(store.root, BackupFile.FILE_NAME)
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
                store.writeAtomically(file, BackupCodec.encode(snapshot))
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
            // The backup carries no post lists, so a restored row would read 0 unread until
            // the next pass. Fetch them now, off the import so the result is not held up.
            if (merged.isNotEmpty()) scope.launch { bookmarks.refreshAll() }
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
        bookmarks.bookmarks.first().isEmpty() && preferences.data.first()[SettingsDataStore.BLOB_KEY] == null

    private companion object {
        const val AUTO_EXPORT_DEBOUNCE_MS = 5_000L
    }
}
