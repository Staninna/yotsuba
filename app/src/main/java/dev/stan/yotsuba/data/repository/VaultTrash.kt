package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.core.vault.VideoStills
import dev.stan.yotsuba.domain.model.VaultError
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.withLock

/**
 * Files a grid delete moved aside until its undo window closes. What was set aside is kept
 * in memory only: a restart empties the trash anyway.
 */
@Singleton
class VaultTrash @Inject constructor(
    private val store: VaultStore,
    private val savedMediaDao: SavedMediaDao,
) {
    private class Trashed(
        val entity: SavedMediaEntity,
        val fileMeta: VaultFileMeta?,
        val dir: File,
        val trashFile: File,
    )

    private val trashed = ConcurrentHashMap<String, Trashed>()

    /** Moves [entity]'s file (it must have one) to the trash dir and drops its row and sidecar entry. */
    suspend fun trash(entity: SavedMediaEntity): VaultError? = attempt {
        val file = File(entity.absolutePath)
        val dir = file.parentFile ?: throw IOException("no parent for ${file.name}")
        val trashDir = File(store.root, VaultPaths.TRASH_DIR_NAME).apply { mkdirs() }
        val target = store.uniqueFile(trashDir, "${System.nanoTime()}_${file.name}")
        if (!store.moveFile(file, target)) throw IOException("Couldn't move ${file.name} to trash")
        var removed: VaultFileMeta? = null
        store.lock.withLock {
            store.updateMeta(dir) { meta ->
                removed = meta.files.firstOrNull { it.fileName == file.name }
                meta.remove(file.name)
            }
        }
        // The thread dir is deliberately not pruned here: an undo needs its sidecars
        // intact. Emptied directories go with the trash in [purge].
        trashed[entity.url] = Trashed(entity, removed, dir, target)
        savedMediaDao.delete(entity.url)
    }

    suspend fun restore(url: String): VaultError? {
        val item = trashed[url] ?: return VaultError.NotFound
        return attempt {
            item.dir.mkdirs()
            val back = File(item.entity.absolutePath)
            if (!store.moveFile(item.trashFile, back)) throw IOException("Couldn't restore ${back.name}")
            store.lock.withLock {
                store.updateMeta(item.dir) { meta ->
                    item.fileMeta?.let { meta.upsert(it) } ?: meta
                }
            }
            savedMediaDao.insert(item.entity)
            trashed.remove(url)
        }
    }

    suspend fun purge() {
        val items = trashed.values.toList()
        trashed.clear()
        File(store.root, VaultPaths.TRASH_DIR_NAME).deleteRecursively()
        // Stills stay at the file's original spot while an undo is possible; this is where they go.
        items.forEach { VideoStills.stillFor(File(it.entity.absolutePath)).delete() }
        val dirs = items.map { it.dir }.toSet()
        store.lock.withLock { dirs.forEach { if (it.isDirectory) store.pruneIfEmpty(it) } }
    }
}
