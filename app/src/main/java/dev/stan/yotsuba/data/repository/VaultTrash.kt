package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.core.vault.SidecarJson
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VideoStills
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultPaths
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * Files a grid delete moved aside. They sit in `.trash/` under the vault root with an
 * `index.json` beside them recording where each came from, so the trash survives the
 * process and a file can be put back a day later from the Trash sheet. Anything older
 * than [RETENTION_MS] goes for good on the next [purgeExpired], which runs at launch.
 */
@Singleton
class VaultTrash @Inject constructor(
    private val store: VaultStore,
    private val savedMediaDao: SavedMediaDao,
) {
    /** One trashed file: its old row and sidecar entry, and where it sits now. */
    @Serializable
    data class Item(
        val entity: SavedMediaEntity,
        val fileMeta: VaultFileMeta?,
        /** The thread directory the file came from. */
        val dir: String,
        val trashFile: String,
        val trashedAt: Long,
    )

    @Serializable
    private data class Index(val items: List<Item> = emptyList())

    private val mutex = Mutex()
    private var loaded = false
    private val items = LinkedHashMap<String, Item>()
    private val _entries = MutableStateFlow<List<SavedMediaEntity>>(emptyList())

    /** What the trash holds, newest first. Empty until the first operation reads the index. */
    val entries: StateFlow<List<SavedMediaEntity>> = _entries

    private val trashDir: File get() = File(store.root, VaultPaths.TRASH_DIR_NAME)
    private val indexFile: File get() = File(trashDir, INDEX_FILE_NAME)

    /** Reads the index once; a missing or unreadable one reads as empty. Call under [mutex]. */
    private fun load() {
        if (loaded) return
        loaded = true
        val index = indexFile.takeIf { it.isFile }?.let { SidecarJson.decode<Index>(it.readText()) }
        index?.items?.forEach { items[it.entity.url] = it }
        publish()
    }

    /** Writes the index and the flow. Call under [mutex]. */
    private fun persist() {
        if (items.isEmpty()) {
            indexFile.delete()
        } else {
            trashDir.mkdirs()
            indexFile.writeText(SidecarJson.encode(Index(items.values.toList())))
        }
        publish()
    }

    private fun publish() {
        _entries.value = items.values.sortedByDescending { it.trashedAt }.map { it.entity }
    }

    /** Reads the index so [entries] reflects the disk before anything is trashed or restored. */
    suspend fun warm() = mutex.withLock { load() }

    /** Moves [entity]'s file (it must have one) to the trash dir and drops its row and sidecar entry. */
    suspend fun trash(entity: SavedMediaEntity, now: Long = System.currentTimeMillis()): VaultError? = attempt {
        mutex.withLock {
            load()
            val file = File(entity.absolutePath)
            val dir = file.parentFile ?: throw IOException("no parent for ${file.name}")
            trashDir.mkdirs()
            val target = store.uniqueFile(trashDir, "${System.nanoTime()}_${file.name}")
            if (!store.moveFile(file, target)) throw IOException("Couldn't move ${file.name} to trash")
            var removed: VaultFileMeta? = null
            store.lock.withLock {
                store.updateMeta(dir) { meta ->
                    removed = meta.files.firstOrNull { it.fileName == file.name }
                    meta.remove(file.name)
                }
            }
            // The thread dir is deliberately not pruned here: a restore needs its sidecars
            // intact. Emptied directories go when their files do, in [purgeExpired] or [empty].
            items[entity.url] = Item(entity, removed, dir.absolutePath, target.absolutePath, now)
            savedMediaDao.delete(entity.url)
            persist()
        }
    }

    suspend fun restore(url: String): VaultError? = mutex.withLock {
        load()
        val item = items[url] ?: return@withLock VaultError.NotFound
        attempt {
            val dir = File(item.dir).apply { mkdirs() }
            val back = File(item.entity.absolutePath)
            if (!store.moveFile(File(item.trashFile), back)) throw IOException("Couldn't restore ${back.name}")
            store.lock.withLock {
                store.updateMeta(dir) { meta -> item.fileMeta?.let { meta.upsert(it) } ?: meta }
            }
            savedMediaDao.insert(item.entity)
            items.remove(url)
            persist()
        }
    }

    /** Deletes everything in the trash for good. */
    suspend fun empty() = mutex.withLock {
        load()
        discard(items.values.toList())
        items.clear()
        trashDir.deleteRecursively()
        persist()
    }

    /** Deletes what has sat in the trash longer than [RETENTION_MS]. */
    suspend fun purgeExpired(now: Long = System.currentTimeMillis()) = mutex.withLock {
        load()
        val expired = items.values.filter { now - it.trashedAt >= RETENTION_MS }
        if (expired.isEmpty()) return@withLock
        discard(expired)
        expired.forEach { items.remove(it.entity.url) }
        persist()
    }

    /** Removes the trashed files and the stills that stayed at their original spot; prunes emptied dirs. */
    private suspend fun discard(gone: List<Item>) {
        gone.forEach {
            File(it.trashFile).delete()
            VideoStills.stillFor(File(it.entity.absolutePath)).delete()
        }
        val dirs = gone.map { File(it.dir) }.toSet()
        store.lock.withLock { dirs.forEach { if (it.isDirectory) store.pruneIfEmpty(it) } }
    }

    companion object {
        const val INDEX_FILE_NAME = "index.json"
        const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
