package dev.stan.yotsuba.core.backup

import dev.stan.yotsuba.data.repository.VaultStore
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Writes and reads the one thing an uninstall would otherwise destroy:
 * bookmarks, history, hidden threads and settings.
 *
 * The file lands next to the media vault on shared storage, because that is
 * the only place on the device that outlives the app. Reinstalling with a
 * different signing key — a debug build replaced by a signed release — wipes
 * /data/data and leaves this untouched.
 */
@Singleton
class BackupManager @Inject constructor(
    private val settings: SettingsRepository,
    private val bookmarks: BookmarkRepository,
    private val history: HistoryRepository,
    private val hidden: HiddenThreadsRepository,
    private val vault: MediaVaultRepository,
    private val store: VaultStore,
) {

    data class Counts(val bookmarks: Int, val history: Int, val hiddenThreads: Int)

    sealed interface Result {
        data class Exported(val path: String, val counts: Counts) : Result
        data class Imported(val counts: Counts) : Result
        data class Failed(val message: String) : Result
    }

    val file: File get() = File(store.root, BackupFile.FILE_NAME)

    suspend fun export(appVersion: String): Result = withContext(Dispatchers.IO) {
        if (!vault.hasStorageAccess()) return@withContext Result.Failed(NO_STORAGE)
        try {
            val backup = BackupFile(
                exportedAtMs = System.currentTimeMillis(),
                appVersion = appVersion,
                settings = settings.settings.first().toBackup(),
                bookmarks = bookmarks.bookmarks.first().map { it.toBackup() },
                history = history.history.first().map { it.toBackup() },
                hiddenThreads = hidden.all.first().map { it.toBackup() },
            )
            store.ensureRoot()
            // Write beside the target and rename, so an interrupted export
            // can't leave a truncated file where a good one used to be.
            val tmp = File(store.root, "${BackupFile.FILE_NAME}.tmp")
            tmp.writeText(BackupFile.json.encodeToString(backup))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            Result.Exported(file.absolutePath, backup.counts())
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Export failed.")
        }
    }

    /**
     * Merges the file into whatever is here now: nothing is deleted, and a
     * thread already bookmarked simply keeps the imported values. Running it
     * twice does the same thing as running it once.
     */
    suspend fun import(): Result = withContext(Dispatchers.IO) {
        if (!vault.hasStorageAccess()) return@withContext Result.Failed(NO_STORAGE)
        if (!file.isFile) return@withContext Result.Failed("No backup at ${file.absolutePath}")
        val backup = try {
            BackupFile.json.decodeFromString<BackupFile>(file.readText())
        } catch (e: Exception) {
            return@withContext Result.Failed("That file isn't a Yotsuba backup.")
        }
        if (backup.version > BackupFile.CURRENT_VERSION) {
            return@withContext Result.Failed(
                "That backup was written by a newer Yotsuba (format ${backup.version}).",
            )
        }
        try {
            settings.update { backup.settings.applyTo(it) }
            backup.bookmarks.forEach { bookmarks.add(it.toDomain()) }
            backup.history.forEach { history.record(it.toDomain()) }
            backup.hiddenThreads.forEach { hidden.hide(it.board, it.threadNo) }
            Result.Imported(backup.counts())
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Import failed.")
        }
    }

    private fun BackupFile.counts() = Counts(bookmarks.size, history.size, hiddenThreads.size)

    private companion object {
        const val NO_STORAGE = "Grant storage access first — the backup lives beside the media vault."
    }
}
