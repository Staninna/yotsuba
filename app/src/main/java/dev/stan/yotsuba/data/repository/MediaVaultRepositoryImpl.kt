package dev.stan.yotsuba.data.repository

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.media.MediaByteSource
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.core.vault.VaultMetaCodec
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val VAULT_MIGRATED = booleanPreferencesKey("vault_legacy_migrated_v1")

@Singleton
class MediaVaultRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedMediaDao: SavedMediaDao,
    private val store: VaultStore,
    private val migration: VaultLegacyMigration,
    private val byteSource: MediaByteSource,
    private val preferences: DataStore<Preferences>,
) : MediaVaultRepository {

    override fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    override fun entries(): Flow<List<VaultEntry>> = savedMediaDao.all().map { rows ->
        rows.filter { it.absolutePath.isNotEmpty() }.map { it.toVaultEntry() }
    }

    override fun savedUrls(): Flow<Set<String>> =
        savedMediaDao.urls().map { it.toSet() }

    override fun savedPaths(): Flow<Map<String, String>> = savedMediaDao.all().map { rows ->
        rows.filter { it.absolutePath.isNotEmpty() }.associate { it.url to it.absolutePath }
    }

    override suspend fun save(item: MediaItem, saveContext: VaultSaveContext): VaultError? =
        withContext(Dispatchers.IO) {
            if (!hasStorageAccess()) return@withContext VaultError.NoAccess
            attempt {
                store.ensureRoot()
                val dir = File(
                    File(store.root, VaultPaths.sanitizeSegment(saveContext.board)),
                    VaultPaths.threadDirName(
                        saveContext.threadNo, saveContext.threadSubject, saveContext.opExcerpt,
                    ),
                ).apply { mkdirs() }

                val target = store.uniqueFile(
                    dir, VaultPaths.fileName(item.postNo, item.filename, item.ext),
                )
                streamTo(item.fullUrl, target)

                val savedAt = System.currentTimeMillis()
                store.lock.withLock {
                    store.updateMeta(dir) { meta ->
                        meta.copy(
                            board = saveContext.board,
                            threadNo = saveContext.threadNo,
                            subject = saveContext.threadSubject,
                            threadUrl = Urls.threadWebUrl(saveContext.board, saveContext.threadNo),
                        ).upsert(store.fileMetaOf(target.name, item, saveContext.post, savedAt))
                    }
                }
                savedMediaDao.insert(
                    savedMediaEntity(
                        item, saveContext.board, saveContext.threadNo,
                        saveContext.threadSubject, target, savedAt,
                    ),
                )
            }
        }

    override suspend fun delete(url: String): VaultError? = withContext(Dispatchers.IO) {
        val entity = savedMediaDao.byUrl(url) ?: return@withContext VaultError.NotFound
        attempt {
            if (entity.absolutePath.isNotEmpty()) {
                val file = File(entity.absolutePath)
                val dir = file.parentFile
                file.delete()
                if (dir != null) {
                    store.lock.withLock {
                        store.updateMeta(dir) { it.remove(file.name) }
                        store.pruneIfEmpty(dir)
                    }
                }
            }
            savedMediaDao.delete(url)
        }
    }

    override suspend fun rescan() = withContext(Dispatchers.IO) {
        if (!hasStorageAccess() || !store.root.isDirectory) return@withContext
        val rebuilt = mutableListOf<dev.stan.yotsuba.core.database.entity.SavedMediaEntity>()
        store.lock.withLock {
            store.root.walkTopDown()
                .filter { it.isFile && it.name == VaultPaths.META_FILE_NAME }
                .forEach { metaFile ->
                    val meta = VaultMetaCodec.decode(metaFile.readText()) ?: return@forEach
                    meta.files.forEach { f ->
                        val file = File(metaFile.parentFile, f.fileName)
                        if (!file.isFile) return@forEach
                        rebuilt += savedMediaEntity(meta, f, file)
                    }
                }
        }
        savedMediaDao.clearAll()
        savedMediaDao.insertAll(rebuilt)
    }

    override suspend fun migrateLegacyIfNeeded() = withContext(Dispatchers.IO) {
        if (preferences.data.first()[VAULT_MIGRATED] == true) return@withContext
        if (!hasStorageAccess()) return@withContext

        runCatching { migration.run() }
        preferences.edit { it[VAULT_MIGRATED] = true }
    }

    /** Runs [block], mapping any failure to a typed [VaultError]; cancellation passes through. */
    private inline fun attempt(block: () -> Unit): VaultError? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        VaultError.Io(e.message)
    }

    /** Coil-disk-cache-first streaming into `<target>.part`, atomically renamed on success. */
    private fun streamTo(url: String, target: File) {
        val part = File(target.parentFile, target.name + ".part")
        try {
            part.outputStream().use { out -> byteSource.copyTo(url, out) }
            if (!part.renameTo(target)) {
                part.delete()
                throw java.io.IOException("Couldn't rename ${part.name}")
            }
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }
}
