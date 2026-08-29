package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.core.dedup.DHash
import dev.stan.yotsuba.core.dedup.Grouping
import dev.stan.yotsuba.core.dedup.Keeper
import dev.stan.yotsuba.core.dedup.Md5
import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateEntry
import dev.stan.yotsuba.domain.model.DuplicateGroup
import dev.stan.yotsuba.domain.repository.VaultDedupRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@Singleton
class VaultDedupRepositoryImpl @Inject constructor(
    private val dao: SavedMediaDao,
) : VaultDedupRepository {

    override suspend fun findByMd5(md5: String): String? = withContext(Dispatchers.IO) {
        dao.byMd5(md5)?.absolutePath?.takeIf { File(it).exists() }
    }

    override suspend fun recordMd5(url: String, md5: String) = withContext(Dispatchers.IO) {
        dao.updateMd5(url, md5)
    }

    override suspend fun missingHashCount(): Int = withContext(Dispatchers.IO) { dao.missingHashes().size }

    override suspend fun backfillHashes(onProgress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val todo = dao.missingHashes()
        val total = todo.size
        onProgress(0, total)
        todo.forEachIndexed { i, row ->
            coroutineContext.ensureActive()
            val file = File(row.absolutePath)
            if (file.isFile) {
                runCatching {
                    val md5 = row.md5 ?: Md5.of(file)
                    if (isVideo(row)) {
                        dao.updateMd5(row.url, md5)
                    } else {
                        val image = DHash.of(file)
                        dao.updateHashes(
                            row.url, md5, image?.dhash,
                            image?.let { it.width.toLong() * it.height.toLong() },
                        )
                    }
                }
            }
            onProgress(i + 1, total)
        }
    }

    override suspend fun findDuplicates(mode: DedupMode, maxDistance: Int): List<DuplicateGroup> =
        withContext(Dispatchers.IO) {
            val rows = dao.allOnce().filter { it.absolutePath.isNotEmpty() }
            val groups = when (mode) {
                DedupMode.EXACT -> Grouping.exact(rows) { it.md5 }
                DedupMode.SIMILAR -> Grouping.near(rows.filterNot(::isVideo), maxDistance) { it.phash }
            }
            groups.map { rows ->
                val entries = rows.map { it.toDuplicateEntry() }.sortedWith(Keeper.order)
                DuplicateGroup(entries, Keeper.suggest(entries).url)
            }.sortedByDescending { it.redundantBytes }
        }

    private fun isVideo(row: SavedMediaEntity) = row.ext == ".webm" || row.ext == ".mp4"

    private fun SavedMediaEntity.toDuplicateEntry() = DuplicateEntry(
        url = url,
        absolutePath = absolutePath,
        displayName = displayName,
        sizeBytes = sizeBytes ?: File(absolutePath).length(),
        width = width,
        height = height,
        savedAt = savedAt,
        subject = subject,
        isVideo = isVideo(this),
        thumbnailPath = thumbnailPath,
    )
}
