package dev.stan.yotsuba.fake

import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateGroup
import dev.stan.yotsuba.domain.repository.VaultDedupRepository

/** Knows nothing and records nothing: for tests that only exercise the save path. */
object NoDedup : VaultDedupRepository {
    override suspend fun findByMd5(md5: String): String? = null
    override suspend fun recordMd5(url: String, md5: String) {}
    override suspend fun missingHashCount(): Int = 0
    override suspend fun backfillHashes(onProgress: (Int, Int) -> Unit) {}
    override suspend fun findDuplicates(mode: DedupMode, maxDistance: Int): List<DuplicateGroup> = emptyList()
}
