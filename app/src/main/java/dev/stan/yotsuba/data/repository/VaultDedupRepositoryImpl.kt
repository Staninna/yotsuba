package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateGroup
import dev.stan.yotsuba.domain.repository.VaultDedupRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    override suspend fun backfillHashes(onProgress: (Int, Int) -> Unit) {}

    override suspend fun findDuplicates(mode: DedupMode, maxDistance: Int): List<DuplicateGroup> = emptyList()
}
